package com.tfajfar.walkietalkie.core;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class AudioEngine {
    private static final String TAG = "AudioEngine";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int PORT = 50005;
    private static final int NOISE_THRESHOLD = 800;

    private boolean isRecording = false;
    private boolean isPlaying = false;
    private int lastAmplitude = 0;

    private DatagramSocket listenSocket;
    private AudioRecord recorder;
    private AudioTrack track;
    private NoiseSuppressor noiseSuppressor;
    private AcousticEchoCanceler echoCanceler;

    private String currentRecordDir;
    private boolean isRecordEnabled = false;

    public void setRecordEnabled(boolean enabled, String dir) {
        this.isRecordEnabled = enabled;
        this.currentRecordDir = dir;
    }

    public synchronized int getAmplitude() {
        return lastAmplitude;
    }

    // ── Talk ──────────────────────────────────────────────────────────────────

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
                    if (ipAddress.endsWith(".255")) talkSocket.setBroadcast(true);

                    int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT);
                    int bufSize = Math.max(minBuf, 2048);
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT, bufSize);

                    if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                        isRecording = false;
                        return;
                    }

                    if (NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = NoiseSuppressor.create(recorder.getAudioSessionId());
                        if (noiseSuppressor != null) noiseSuppressor.setEnabled(true);
                    }
                    if (AcousticEchoCanceler.isAvailable()) {
                        echoCanceler = AcousticEchoCanceler.create(recorder.getAudioSessionId());
                        if (echoCanceler != null) echoCanceler.setEnabled(true);
                    }

                    if (recordPath != null) {
                        File file = new File(recordPath);
                        if (file.getParentFile() != null) file.getParentFile().mkdirs();
                        fos = new FileOutputStream(file);
                        fos.write("#!AMR-WB\n".getBytes());
                        encoder = createAmrEncoder();
                        encoder.start();
                    }

                    byte[] buffer = new byte[bufSize];
                    recorder.startRecording();
                    InetAddress address = InetAddress.getByName(ipAddress);

                    while (isRecording) {
                        int read = recorder.read(buffer, 0, buffer.length);
                        if (read <= 0) continue;

                        int max = 0;
                        for (int i = 0; i < read; i += 2) {
                            short s = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xff));
                            if (Math.abs(s) > max) max = Math.abs(s);
                        }
                        lastAmplitude = max;

                        if (max < NOISE_THRESHOLD) {
                            Arrays.fill(buffer, 0, read, (byte) 0);
                            lastAmplitude = 0;
                        } else {
                            applySimpleLimiter(buffer, read);
                        }

                        talkSocket.send(new DatagramPacket(buffer, read, address, PORT));
                        if (encoder != null && fos != null) {
                            encodeAndWrite(encoder, fos, buffer, read);
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
        for (int i = 0; i < len; i += 2) {
            short s = (short) ((buf[i + 1] << 8) | (buf[i] & 0xff));
            if (s > 30000) s = 30000;
            else if (s < -30000) s = -30000;
            buf[i] = (byte) (s & 0xff);
            buf[i + 1] = (byte) ((s >> 8) & 0xff);
        }
    }

    private MediaCodec createAmrEncoder() throws IOException {
        MediaFormat fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AMR_WB, SAMPLE_RATE, 1);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 23850);
        fmt.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        fmt.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        MediaCodec enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_WB);
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return enc;
    }

    private synchronized void encodeAndWrite(MediaCodec enc, FileOutputStream fos, byte[] data, int size) throws IOException {
        int idx = enc.dequeueInputBuffer(1000);
        if (idx >= 0) {
            ByteBuffer in = enc.getInputBuffer(idx);
            if (in != null) {
                in.clear();
                in.put(data, 0, size);
                enc.queueInputBuffer(idx, 0, size, System.nanoTime() / 1000, 0);
            }
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int out = enc.dequeueOutputBuffer(info, 1000);
        while (out >= 0) {
            ByteBuffer outBuf = enc.getOutputBuffer(out);
            if (outBuf != null) {
                byte[] outData = new byte[info.size];
                outBuf.get(outData);
                fos.write(outData);
            }
            enc.releaseOutputBuffer(out, false);
            out = enc.dequeueOutputBuffer(info, 0);
        }
    }

    private synchronized void releaseRecorder() {
        if (noiseSuppressor != null) { noiseSuppressor.release(); noiseSuppressor = null; }
        if (echoCanceler != null) { echoCanceler.release(); echoCanceler = null; }
        if (recorder != null) {
            try { if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop(); } catch (Exception ignored) {}
            recorder.release();
            recorder = null;
        }
    }

    // ── Listen ────────────────────────────────────────────────────────────────

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
                    int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT);
                    int bufSize = Math.max(minBuf, 2048);
                    track = new AudioTrack.Builder()
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setEncoding(AUDIO_FORMAT)
                                    .setSampleRate(SAMPLE_RATE)
                                    .setChannelMask(CHANNEL_OUT)
                                    .build())
                            .setBufferSizeInBytes(bufSize)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build();

                    byte[] buffer = new byte[bufSize];
                    track.play();

                    while (isPlaying) {
                        DatagramPacket pkt = new DatagramPacket(buffer, buffer.length);
                        listenSocket.receive(pkt);
                        int length = pkt.getLength();

                        int max = 0;
                        for (int i = 0; i < length; i += 2) {
                            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xff));
                            if (Math.abs(sample) > max) max = Math.abs(sample);
                        }

                        if (max < NOISE_THRESHOLD) Arrays.fill(buffer, 0, length, (byte) 0);
                        track.write(buffer, 0, length);

                        if (isRecordEnabled && currentRecordDir != null) {
                            if (max >= NOISE_THRESHOLD) {
                                if (fos == null) {
                                    File dir = new File(currentRecordDir);
                                    if (!dir.exists()) dir.mkdirs();
                                    String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
                                    fos = new FileOutputStream(new File(dir, ts + "_IN.amr"));
                                    fos.write("#!AMR-WB\n".getBytes());
                                    encoder = createAmrEncoder();
                                    encoder.start();
                                }
                                encodeAndWrite(encoder, fos, buffer, length);
                            } else if (fos != null) {
                                encodeAndWrite(encoder, fos, buffer, length);
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
