package com.tfajfar.walkietalkie.core;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioEngine {
    private static final String TAG = "AudioEngine";
    private static final int SAMPLE_RATE = 8000;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int PORT = 50005;

    private volatile boolean isRecording = false;
    private volatile boolean isPlaying = false;
    private volatile int lastAmplitude = 0;
    
    private final ExecutorService talkExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService listenExecutor = Executors.newSingleThreadExecutor();
    
    private DatagramSocket listenSocket;
    private AudioRecord recorder;
    private AudioTrack track;

    private String currentRecordDir;
    private boolean isRecordEnabled = false;

    public void setRecordEnabled(boolean enabled, String dir) {
        this.isRecordEnabled = enabled;
        this.currentRecordDir = dir;
    }

    public int getAmplitude() {
        return lastAmplitude;
    }

    public void startTalking(String ipAddress, String recordPath) {
        if (isRecording) return;
        isRecording = true;

        talkExecutor.execute(() -> {
            DatagramSocket talkSocket = null;
            MediaCodec encoder = null;
            FileOutputStream fos = null;
            try {
                talkSocket = new DatagramSocket();
                int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT);
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT, minBufSize);
                
                if (recordPath != null) {
                    File file = new File(recordPath);
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    
                    fos = new FileOutputStream(file);
                    fos.write("#!AMR\n".getBytes());
                    encoder = createAmrEncoder();
                    encoder.start();
                }
                
                byte[] buffer = new byte[minBufSize];
                recorder.startRecording();
                InetAddress address = InetAddress.getByName(ipAddress);

                while (isRecording) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        DatagramPacket packet = new DatagramPacket(buffer, read, address, PORT);
                        talkSocket.send(packet);
                        
                        // Calculate amplitude
                        int max = 0;
                        for (int i = 0; i < read; i += 2) {
                            short sample = (short) ((buffer[i+1] << 8) | (buffer[i] & 0xff));
                            if (Math.abs(sample) > max) max = Math.abs(sample);
                        }
                        lastAmplitude = max;

                        if (encoder != null && fos != null) {
                            encodeAndWrite(encoder, fos, buffer, read);
                        }
                    }
                }
            } catch (IOException | SecurityException e) {
                Log.e(TAG, "Talk error", e);
            } finally {
                stopAndReleaseRecorder();
                lastAmplitude = 0;
                if (talkSocket != null) talkSocket.close();
                if (encoder != null) {
                    try { encoder.stop(); } catch (Exception ignored) {}
                    encoder.release();
                }
                if (fos != null) {
                    try { fos.close(); } catch (IOException ignored) {}
                }
            }
        });
    }

    private MediaCodec createAmrEncoder() throws IOException {
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AMR_NB, SAMPLE_RATE, 1);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 12200);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        
        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_NB);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return encoder;
    }

    private void encodeAndWrite(MediaCodec encoder, FileOutputStream fos, byte[] data, int size) throws IOException {
        int inputBufferIndex = encoder.dequeueInputBuffer(1000);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferIndex);
            if (inputBuffer != null) {
                inputBuffer.clear();
                inputBuffer.put(data, 0, size);
                encoder.queueInputBuffer(inputBufferIndex, 0, size, System.nanoTime() / 1000, 0);
            }
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 1000);
        while (outputBufferIndex >= 0) {
            ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);
            if (outputBuffer != null) {
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);
                fos.write(outData);
            }
            encoder.releaseOutputBuffer(outputBufferIndex, false);
            outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);
        }
    }

    public void stopTalking() {
        isRecording = false;
    }

    private synchronized void stopAndReleaseRecorder() {
        if (recorder != null) {
            try {
                if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping recorder", e);
            }
            recorder.release();
            recorder = null;
        }
    }

    public void startListening() {
        if (isPlaying) return;
        isPlaying = true;

        listenExecutor.execute(() -> {
            FileOutputStream fos = null;
            MediaCodec encoder = null;
            try {
                listenSocket = new DatagramSocket(PORT);
                int minBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT);
                
                track = new AudioTrack.Builder()
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AUDIO_FORMAT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(CHANNEL_OUT)
                                .build())
                        .setBufferSizeInBytes(minBufSize)
                        .build();

                byte[] buffer = new byte[minBufSize];
                track.play();

                while (isPlaying) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    listenSocket.receive(packet);
                    int length = packet.getLength();
                    track.write(packet.getData(), 0, length);
                    
                    if (isRecordEnabled && currentRecordDir != null) {
                        if (fos == null) {
                            File dir = new File(currentRecordDir);
                            if (!dir.exists()) dir.mkdirs();
                            
                            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault());
                            String filename = sdf.format(new java.util.Date()) + "_IN.amr";
                            fos = new FileOutputStream(new File(dir, filename));
                            fos.write("#!AMR\n".getBytes());
                            encoder = createAmrEncoder();
                            encoder.start();
                        }
                        encodeAndWrite(encoder, fos, packet.getData(), length);
                    } else if (fos != null) {
                        if (encoder != null) {
                            try { encoder.stop(); } catch (Exception ignored) {}
                            encoder.release();
                            encoder = null;
                        }
                        fos.close();
                        fos = null;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Listen error", e);
            } finally {
                stopAndReleaseTrack();
                if (listenSocket != null) {
                    listenSocket.close();
                    listenSocket = null;
                }
                if (encoder != null) {
                    try { encoder.stop(); } catch (Exception ignored) {}
                    encoder.release();
                }
                if (fos != null) {
                    try { fos.close(); } catch (IOException ignored) {}
                }
            }
        });
    }

    public void stopListening() {
        isPlaying = false;
        if (listenSocket != null) {
            listenSocket.close();
            listenSocket = null;
        }
    }

    private synchronized void stopAndReleaseTrack() {
        if (track != null) {
            try {
                if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping track", e);
            }
            track.release();
            track = null;
        }
    }

    public void release() {
        stopTalking();
        stopListening();
        talkExecutor.shutdown();
        listenExecutor.shutdown();
    }
}
