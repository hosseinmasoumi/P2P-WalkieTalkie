package com.tfajfar.walkietalkie.core;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

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
                WavWriter wavWriter = null;
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
                            wavWriter = new WavWriter(new File(recordPath));
                        } catch (Exception e) {
                            Log.e(TAG, "Outgoing recording disabled", e);
                            wavWriter = null;
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

                        // Live audio is always sent first. Recording must never block transmission.
                        talkSocket.send(new DatagramPacket(packetBuffer, read, address, PORT));

                        if (wavWriter != null) {
                            try {
                                wavWriter.write(packetBuffer, read);
                            } catch (Exception e) {
                                Log.e(TAG, "Outgoing recording failed", e);
                                wavWriter.closeQuietly();
                                wavWriter = null;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Talk error", e);
                } finally {
                    releaseRecorder();
                    lastAmplitude = 0;
                    if (talkSocket != null) talkSocket.close();
                    if (wavWriter != null) wavWriter.closeQuietly();
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
                WavWriter wavWriter = null;
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

                        // Playback happens before optional saving, so recording cannot disturb live audio.
                        track.write(packetBuffer, 0, length);

                        if (isRecordEnabled && currentRecordDir != null) {
                            if (max >= RECORDING_START_THRESHOLD && wavWriter == null) {
                                try {
                                    File dir = new File(currentRecordDir);
                                    if (!dir.exists()) dir.mkdirs();
                                    String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
                                    wavWriter = new WavWriter(new File(dir, ts + "_IN.wav"));
                                } catch (Exception e) {
                                    Log.e(TAG, "Incoming recording disabled", e);
                                    wavWriter = null;
                                }
                            }
                            if (wavWriter != null) {
                                try {
                                    wavWriter.write(packetBuffer, length);
                                } catch (Exception e) {
                                    Log.e(TAG, "Incoming recording failed", e);
                                    wavWriter.closeQuietly();
                                    wavWriter = null;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    if (isPlaying) Log.e(TAG, "Listen error", e);
                } finally {
                    releaseTrack();
                    if (listenSocket != null) { listenSocket.close(); listenSocket = null; }
                    if (wavWriter != null) wavWriter.closeQuietly();
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

    private static class WavWriter {
        private final RandomAccessFile file;
        private int dataSize = 0;

        WavWriter(File output) throws IOException {
            File parent = output.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            file = new RandomAccessFile(output, "rw");
            file.setLength(0);
            writeHeader(0);
        }

        void write(byte[] data, int length) throws IOException {
            file.write(data, 0, length);
            dataSize += length;
        }

        void closeQuietly() {
            try {
                updateHeader();
                file.close();
            } catch (Exception ignored) {}
        }

        private void updateHeader() throws IOException {
            file.seek(0);
            writeHeader(dataSize);
        }

        private void writeHeader(int pcmDataSize) throws IOException {
            int byteRate = SAMPLE_RATE * 2;
            file.writeBytes("RIFF");
            writeIntLE(36 + pcmDataSize);
            file.writeBytes("WAVE");
            file.writeBytes("fmt ");
            writeIntLE(16);
            writeShortLE((short) 1);
            writeShortLE((short) 1);
            writeIntLE(SAMPLE_RATE);
            writeIntLE(byteRate);
            writeShortLE((short) 2);
            writeShortLE((short) 16);
            file.writeBytes("data");
            writeIntLE(pcmDataSize);
        }

        private void writeIntLE(int value) throws IOException {
            file.write(value & 0xff);
            file.write((value >> 8) & 0xff);
            file.write((value >> 16) & 0xff);
            file.write((value >> 24) & 0xff);
        }

        private void writeShortLE(short value) throws IOException {
            file.write(value & 0xff);
            file.write((value >> 8) & 0xff);
        }
    }
}