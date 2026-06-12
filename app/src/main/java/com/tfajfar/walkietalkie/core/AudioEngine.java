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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class AudioEngine {
    private static final String TAG = "AudioEngine";

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // 20 ms of 16 kHz, mono, 16-bit PCM = 640 bytes.
    private static final int UDP_PACKET_SIZE = 640;
    private static final int PORT = 50005;

    private static final int TARGET_MIC_LEVEL = 14000;
    private static final float NORMAL_MIC_GAIN = 0.85f;
    private static final float MIN_MIC_GAIN = 0.45f;
    private static final int RECORDING_START_THRESHOLD = 250;

    private static final int AMR_SAMPLE_RATE = 8000;
    private static final int AMR_BIT_RATE = 12200;

    private volatile boolean isRecording = false;
    private volatile boolean isPlaying = false;
    private int lastAmplitude = 0;

    private DatagramSocket listenSocket;
    private AudioRecord recorder;
    private AudioTrack track;

    // Saved after the first received packet, so Group Owner can send direct instead of broadcast.
    private volatile String lastRemoteIp;

    private String currentRecordDir;
    private boolean isRecordEnabled = false;

    public void setRecordEnabled(boolean enabled, String dir) {
        isRecordEnabled = enabled;
        currentRecordDir = dir;
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
                AmrRecorder amrRecorder = null;
                try {
                    String targetIp = ipAddress;
                    if (isBroadcastAddress(ipAddress) && lastRemoteIp != null) {
                        targetIp = lastRemoteIp;
                    }

                    talkSocket = new DatagramSocket();
                    talkSocket.setSendBufferSize(64 * 1024);
                    if (isBroadcastAddress(targetIp)) talkSocket.setBroadcast(true);

                    int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT);
                    int recordBufferSize = Math.max(minBuf, UDP_PACKET_SIZE * 4);

                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT, recordBufferSize);

                    if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                        isRecording = false;
                        return;
                    }

                    if (recordPath != null) {
                        try {
                            amrRecorder = new AmrRecorder(new File(recordPath));
                            amrRecorder.start();
                        } catch (Exception e) {
                            Log.e(TAG, "Outgoing recording disabled", e);
                            amrRecorder = null;
                        }
                    }

                    byte[] packetBuffer = new byte[UDP_PACKET_SIZE];
                    recorder.startRecording();
                    InetAddress address = InetAddress.getByName(targetIp);

                    while (isRecording) {
                        int read = recorder.read(packetBuffer, 0, packetBuffer.length);
                        if (read <= 0) continue;
                        if (read % 2 != 0) read--;

                        lastAmplitude = getMaxAmplitude(packetBuffer, read);
                        processOutgoingAudio(packetBuffer, read, lastAmplitude);

                        // Live audio is sent first. Recording is async and never blocks this loop.
                        talkSocket.send(new DatagramPacket(packetBuffer, read, address, PORT));

                        if (amrRecorder != null) {
                            amrRecorder.offer(packetBuffer, read);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Talk error", e);
                } finally {
                    releaseRecorder();
                    lastAmplitude = 0;
                    if (talkSocket != null) talkSocket.close();
                    if (amrRecorder != null) amrRecorder.stop();
                }
            }
        }).start();
    }

    public synchronized void stopTalking() {
        isRecording = false;
    }

    private boolean isBroadcastAddress(String ip) {
        return ip != null && ip.endsWith(".255");
    }

    private int getMaxAmplitude(byte[] buffer, int length) {
        int max = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xff));
            if (Math.abs(sample) > max) max = Math.abs(sample);
        }
        return max;
    }

    private void processOutgoingAudio(byte[] buf, int len, int maxAmplitude) {
        float gain = NORMAL_MIC_GAIN;

        if (maxAmplitude > TARGET_MIC_LEVEL) {
            gain = TARGET_MIC_LEVEL / (float) maxAmplitude;
            if (gain < MIN_MIC_GAIN) gain = MIN_MIC_GAIN;
        }
        if (maxAmplitude < 3000) gain = 1.0f;

        short previous = 0;
        for (int i = 0; i + 1 < len; i += 2) {
            short sample = (short) ((buf[i + 1] << 8) | (buf[i] & 0xff));
            int value = (int) (sample * gain);
            value = (value + previous) / 2;
            previous = (short) value;

            if (value > 22000) value = 22000;
            else if (value < -22000) value = -22000;

            buf[i] = (byte) (value & 0xff);
            buf[i + 1] = (byte) ((value >> 8) & 0xff);
        }
    }

    private synchronized void releaseRecorder() {
        if (recorder != null) {
            try {
                if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop();
            } catch (Exception ignored) {}
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
                AmrRecorder amrRecorder = null;
                try {
                    listenSocket = new DatagramSocket(PORT);
                    listenSocket.setReceiveBufferSize(64 * 1024);

                    int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT);
                    int playBufferSize = Math.max(minBuf, UDP_PACKET_SIZE * 4);

                    track = new AudioTrack.Builder()
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
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

                        if (pkt.getAddress() != null && !isBroadcastAddress(pkt.getAddress().getHostAddress())) {
                            lastRemoteIp = pkt.getAddress().getHostAddress();
                        }

                        int max = getMaxAmplitude(packetBuffer, length);

                        // Playback happens first. Recording cannot disturb live audio.
                        track.write(packetBuffer, 0, length);

                        if (isRecordEnabled && currentRecordDir != null) {
                            if (max >= RECORDING_START_THRESHOLD && amrRecorder == null) {
                                try {
                                    File dir = new File(currentRecordDir);
                                    if (!dir.exists()) dir.mkdirs();
                                    String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
                                    amrRecorder = new AmrRecorder(new File(dir, ts + "_IN.amr"));
                                    amrRecorder.start();
                                } catch (Exception e) {
                                    Log.e(TAG, "Incoming recording disabled", e);
                                    amrRecorder = null;
                                }
                            }
                            if (amrRecorder != null) {
                                amrRecorder.offer(packetBuffer, length);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (isPlaying) Log.e(TAG, "Listen error", e);
                } finally {
                    releaseTrack();
                    if (listenSocket != null) { listenSocket.close(); listenSocket = null; }
                    if (amrRecorder != null) amrRecorder.stop();
                }
            }
        }).start();
    }

    public synchronized void stopListening() {
        isPlaying = false;
        if (listenSocket != null) listenSocket.close();
    }

    private synchronized void releaseTrack() {
        if (track != null) {
            try {
                if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.stop();
            } catch (Exception ignored) {}
            track.release();
            track = null;
        }
    }

    public void release() {
        stopTalking();
        stopListening();
    }

    private static class AmrRecorder {
        private final File outputFile;
        private final ArrayBlockingQueue<byte[]> queue = new ArrayBlockingQueue<byte[]>(30);
        private volatile boolean running = false;
        private Thread worker;

        AmrRecorder(File outputFile) {
            this.outputFile = outputFile;
        }

        void start() {
            running = true;
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    encodeLoop();
                }
            });
            worker.start();
        }

        void offer(byte[] pcm16k, int length) {
            if (!running) return;

            // Copy quickly and return to live audio. If queue is full, drop recording data only.
            byte[] copy = new byte[length];
            System.arraycopy(pcm16k, 0, copy, 0, length);
            queue.offer(copy);
        }

        void stop() {
            running = false;
            if (worker != null) {
                try { worker.join(800); } catch (InterruptedException ignored) {}
            }
        }

        private void encodeLoop() {
            MediaCodec codec = null;
            FileOutputStream fos = null;
            try {
                File parent = outputFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                fos = new FileOutputStream(outputFile);
                fos.write("#!AMR\n".getBytes());

                MediaFormat format = MediaFormat.createAudioFormat(
                        MediaFormat.MIMETYPE_AUDIO_AMR_NB, AMR_SAMPLE_RATE, 1);
                format.setInteger(MediaFormat.KEY_BIT_RATE, AMR_BIT_RATE);
                format.setInteger(MediaFormat.KEY_SAMPLE_RATE, AMR_SAMPLE_RATE);
                format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);

                codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_NB);
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                codec.start();

                while (running || !queue.isEmpty()) {
                    byte[] pcm16k = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (pcm16k != null) {
                        byte[] pcm8k = downSample16kTo8k(pcm16k);
                        feedEncoder(codec, pcm8k);
                    }
                    drainEncoder(codec, fos);
                }

                sendEndOfStream(codec);
                drainEncoder(codec, fos);
            } catch (Exception e) {
                Log.e(TAG, "AMR recording failed", e);
            } finally {
                if (codec != null) {
                    try { codec.stop(); } catch (Exception ignored) {}
                    try { codec.release(); } catch (Exception ignored) {}
                }
                if (fos != null) {
                    try { fos.close(); } catch (IOException ignored) {}
                }
            }
        }

        private byte[] downSample16kTo8k(byte[] input) {
            // Input is PCM 16-bit little endian. Take every second sample: 16 kHz -> 8 kHz.
            byte[] out = new byte[input.length / 2];
            int outIndex = 0;
            for (int i = 0; i + 3 < input.length; i += 4) {
                out[outIndex++] = input[i];
                out[outIndex++] = input[i + 1];
            }
            return out;
        }

        private void feedEncoder(MediaCodec codec, byte[] data) {
            try {
                int index = codec.dequeueInputBuffer(0);
                if (index < 0) return;
                ByteBuffer buffer = codec.getInputBuffer(index);
                if (buffer == null) return;

                buffer.clear();
                int size = Math.min(data.length, buffer.remaining());
                buffer.put(data, 0, size);
                codec.queueInputBuffer(index, 0, size, System.nanoTime() / 1000, 0);
            } catch (Exception e) {
                Log.e(TAG, "AMR input failed", e);
            }
        }

        private void sendEndOfStream(MediaCodec codec) {
            try {
                int index = codec.dequeueInputBuffer(1000);
                if (index >= 0) {
                    codec.queueInputBuffer(index, 0, 0, System.nanoTime() / 1000,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
            } catch (Exception ignored) {}
        }

        private void drainEncoder(MediaCodec codec, FileOutputStream fos) throws IOException {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int index = codec.dequeueOutputBuffer(info, 0);
            while (index >= 0) {
                ByteBuffer out = codec.getOutputBuffer(index);
                if (out != null && info.size > 0) {
                    out.position(info.offset);
                    out.limit(info.offset + info.size);
                    byte[] data = new byte[info.size];
                    out.get(data);
                    fos.write(data);
                }
                codec.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                index = codec.dequeueOutputBuffer(info, 0);
            }
        }
    }
}