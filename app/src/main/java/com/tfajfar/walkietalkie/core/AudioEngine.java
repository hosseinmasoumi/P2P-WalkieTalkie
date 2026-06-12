package com.tfajfar.walkietalkie.core;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class AudioEngine {
    private static final String TAG = "AudioEngine";

    // Required streaming format for the test.
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // 20 ms of 16 kHz, mono, 16-bit PCM = 640 bytes.
    // Keeping UDP packets small avoids packet fragmentation and noisy playback.
    private static final int UDP_PACKET_SIZE = 640;

    // Low value only for detecting when to start an incoming recording.
    // We do not mute live audio based on this value, because that can cut quiet voices.
    private static final int RECORDING_START_THRESHOLD = 250;

    // AMR-NB recording uses 8 kHz internally.
    private static final int AMR_NB_SAMPLE_RATE = 8000;
    private static final int AMR_NB_BIT_RATE = 12200;

    private static final int PORT = 50005;

    private volatile boolean isRecording = false;
    private volatile boolean isPlaying = false;
    private int lastAmplitude = 0;

    private DatagramSocket listenSocket;
    private AudioRecord recorder;
    private AudioTrack track;

    private String currentRecordDir;
    private boolean isRecordEnabled = false;

    public void setRecordEnabled(boolean enabled, String dir) {
        this.isRecordEnabled = enabled;
        this.currentRecordDir = dir;
    }

    public synchronized int getAmplitude() {
        return lastAmplitude;
    }

    public synchronized void startTalking(final String ipAddress, final String recordPath) {
        if (isRecording) return;
        isRecording = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                DatagramSocket talkSocket = null;
                MediaCodec encoder = null;
                FileOutputStream fos = null;
                try {
                    talkSocket = new DatagramSocket();
                    talkSocket.setSendBufferSize(64 * 1024);
                    if (ipAddress.endsWith(".255")) talkSocket.setBroadcast(true);

                    int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT);
                    int recordBufferSize = Math.max(minBuf, UDP_PACKET_SIZE * 4);

                    recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                            SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT, recordBufferSize);

                    if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                        isRecording = false;
                        return;
                    }

                    if (recordPath != null) {
                        File file = new File(recordPath);
                        if (file.getParentFile() != null) file.getParentFile().mkdirs();
                        fos = new FileOutputStream(file);
                        fos.write("#!AMR\n".getBytes());
                        encoder = createAmrNbEncoder();
                        encoder.start();
                    }

                    byte[] packetBuffer = new byte[UDP_PACKET_SIZE];
                    recorder.startRecording();
                    InetAddress address = InetAddress.getByName(ipAddress);

                    while (isRecording) {
                        int read = recorder.read(packetBuffer, 0, packetBuffer.length);
                        if (read <= 0) continue;
                        if (read % 2 != 0) read--; // keep PCM 16-bit samples aligned

                        lastAmplitude = getMaxAmplitude(packetBuffer, read);
                        applySimpleLimiter(packetBuffer, read);

                        talkSocket.send(new DatagramPacket(packetBuffer, read, address, PORT));

                        if (encoder != null && fos != null) {
                            encodeAndWrite(encoder, fos, packetBuffer, read);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Talk error", e);
                } finally {
                    stopInternalTalking(talkSocket, encoder, fos);
                }
            }
        }).start();
    }

    private int getMaxAmplitude(byte[] buffer, int length) {
        int max = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xff));
            if (Math.abs(sample) > max) max = Math.abs(sample);
        }
        return max;
    }

    private synchronized void stopInternalTalking(DatagramSocket socket, MediaCodec encoder, FileOutputStream fos) {
        releaseRecorder();
        lastAmplitude = 0;
        if (socket != null) socket.close();
        if (encoder != null) {
            try { encoder.stop(); } catch (Exception ignored) {}
            encoder.release();
        }
        if (fos != null) { try { fos.close(); } catch (IOException ignored) {} }
    }

    public synchronized void stopTalking() {
        isRecording = false;
    }

    private void applySimpleLimiter(byte[] buf, int len) {
        for (int i = 0; i + 1 < len; i += 2) {
            short s = (short) ((buf[i + 1] << 8) | (buf[i] & 0xff));
            if (s > 30000) s = 30000;
            else if (s < -30000) s = -30000;
            buf[i] = (byte) (s & 0xff);
            buf[i + 1] = (byte) ((s >> 8) & 0xff);
        }
    }

    private MediaCodec createAmrNbEncoder() throws IOException {
        MediaFormat fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AMR_NB, AMR_NB_SAMPLE_RATE, 1);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, AMR_NB_BIT_RATE);
        fmt.setInteger(MediaFormat.KEY_SAMPLE_RATE, AMR_NB_SAMPLE_RATE);
        fmt.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        MediaCodec enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_NB);
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return enc;
    }

    private synchronized void encodeAndWrite(MediaCodec enc, FileOutputStream fos, byte[] data, int size) throws IOException {
        int idx = enc.dequeueInputBuffer(0);
        if (idx >= 0) {
            ByteBuffer in = enc.getInputBuffer(idx);
            if (in != null) {
                in.clear();
                int bytesToWrite = Math.min(size, in.remaining());
                in.put(data, 0, bytesToWrite);
                enc.queueInputBuffer(idx, 0, bytesToWrite, System.nanoTime() / 1000, 0);
            }
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int out = enc.dequeueOutputBuffer(info, 0);
        while (out >= 0) {
            ByteBuffer outBuf = enc.getOutputBuffer(out);
            if (outBuf != null && info.size > 0) {
                byte[] outData = new byte[info.size];
                outBuf.get(outData);
                fos.write(outData);
            }
            enc.releaseOutputBuffer(out, false);
            out = enc.dequeueOutputBuffer(info, 0);
        }
    }

    private synchronized void releaseRecorder() {
        if (recorder != null) {
            try { if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop(); } catch (Exception ignored) {}
            recorder.release();
            recorder = null;
        }
    }

    public synchronized void startListening() {
        if (isPlaying) return;
        isPlaying = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                FileOutputStream fos = null;
                MediaCodec encoder = null;
                try {
                    listenSocket = new DatagramSocket(PORT);
                    listenSocket.setReceiveBufferSize(64 * 1024);

                    int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT);
                    int playBufferSize = Math.max(minBuf, UDP_PACKET_SIZE * 4);

                    track = new AudioTrack.Builder()
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build())
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setEncoding(AUDIO_FORMAT)
                                    .setSampleRate(SAMPLE_RATE)
                                    .setChannelMask(CHANNEL_OUT)
                                    .build())
                            .setBufferSizeInBytes(playBufferSize)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build();

                    byte[] packetBuffer = new byte[UDP_PACKET_SIZE];
                    track.play();

                    while (isPlaying) {
                        DatagramPacket pkt = new DatagramPacket(packetBuffer, packetBuffer.length);
                        listenSocket.receive(pkt);
                        int length = pkt.getLength();
                        if (length <= 0) continue;
                        if (length % 2 != 0) length--;

                        int max = getMaxAmplitude(packetBuffer, length);
                        track.write(packetBuffer, 0, length);

                        if (isRecordEnabled && currentRecordDir != null) {
                            if (max >= RECORDING_START_THRESHOLD) {
                                if (fos == null) {
                                    File dir = new File(currentRecordDir);
                                    if (!dir.exists()) dir.mkdirs();
                                    String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
                                    fos = new FileOutputStream(new File(dir, ts + "_IN.amr"));
                                    fos.write("#!AMR\n".getBytes());
                                    encoder = createAmrNbEncoder();
                                    encoder.start();
                                }
                                encodeAndWrite(encoder, fos, packetBuffer, length);
                            } else if (fos != null) {
                                encodeAndWrite(encoder, fos, packetBuffer, length);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (isPlaying) Log.e(TAG, "Listen error", e);
                } finally {
                    stopInternalListening(encoder, fos);
                }
            }
        }).start();
    }

    private synchronized void stopInternalListening(MediaCodec encoder, FileOutputStream fos) {
        releaseTrack();
        if (listenSocket != null) { listenSocket.close(); listenSocket = null; }
        if (encoder != null) {
            try { encoder.stop(); } catch (Exception ignored) {}
            encoder.release();
        }
        if (fos != null) { try { fos.close(); } catch (IOException ignored) {} }
    }

    public synchronized void stopListening() {
        isPlaying = false;
        if (listenSocket != null) listenSocket.close();
    }

    private synchronized void releaseTrack() {
        if (track != null) {
            try { if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.stop(); } catch (Exception ignored) {}
            track.release();
            track = null;
        }
    }

    public void release() {
        stopTalking();
        stopListening();
    }
}