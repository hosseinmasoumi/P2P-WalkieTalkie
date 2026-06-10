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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioEngine {
    private static final String TAG = "AudioEngine";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int PORT = 50005;
    
    // Simple software noise gate threshold
    private static final int NOISE_THRESHOLD = 800; 

    private volatile boolean isRecording = false;
    private volatile boolean isPlaying = false;
    private volatile int lastAmplitude = 0;
    
    private final ExecutorService talkExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService listenExecutor = Executors.newSingleThreadExecutor();
    
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
                // Use a slightly larger buffer to prevent jitter but keep latency low
                int bufferSize = Math.max(minBufSize, 2048);
                
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT, bufferSize);
                
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed");
                    isRecording = false;
                    return;
                }

                // Try to enable hardware noise suppression if available
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
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    
                    fos = new FileOutputStream(file);
                    // Use AMR-WB header for 16kHz
                    fos.write("#!AMR-WB\n".getBytes());
                    encoder = createAmrEncoder();
                    encoder.start();
                }
                
                byte[] buffer = new byte[bufferSize];
                recorder.startRecording();
                InetAddress address = InetAddress.getByName(ipAddress);

                while (isRecording) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        // 1. Calculate amplitude and apply software noise gate
                        int max = 0;
                        for (int i = 0; i < read; i += 2) {
                            short sample = (short) ((buffer[i+1] << 8) | (buffer[i] & 0xff));
                            if (Math.abs(sample) > max) max = Math.abs(sample);
                        }
                        lastAmplitude = max;

                        if (max < NOISE_THRESHOLD) {
                            // Suppress background noise by zeroing the buffer
                            Arrays.fill(buffer, 0, read, (byte) 0);
                            lastAmplitude = 0;
                        } else {
                            // Basic peak limiting to avoid clipping distortion
                            applySimpleLimiter(buffer, read);
                        }

                        // 2. Send audio over network
                        DatagramPacket packet = new DatagramPacket(buffer, read, address, PORT);
                        talkSocket.send(packet);
                        
                        // 3. Record audio if enabled
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

    private void applySimpleLimiter(byte[] buffer, int length) {
        for (int i = 0; i < length; i += 2) {
            short sample = (short) ((buffer[i+1] << 8) | (buffer[i] & 0xff));
            if (sample > 30000) sample = 30000;
            else if (sample < -30000) sample = -30000;
            buffer[i] = (byte) (sample & 0xff);
            buffer[i+1] = (byte) ((sample >> 8) & 0xff);
        }
    }

    private MediaCodec createAmrEncoder() throws IOException {
        // Since we are now using 16000Hz, we should use AMR-WB (Wideband)
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AMR_WB, SAMPLE_RATE, 1);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 23850);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        
        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_WB);
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
        if (noiseSuppressor != null) {
            noiseSuppressor.release();
            noiseSuppressor = null;
        }
        if (echoCanceler != null) {
            echoCanceler.release();
            echoCanceler = null;
        }
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
                int bufferSize = Math.max(minBufSize, 2048);
                
                track = new AudioTrack.Builder()
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AUDIO_FORMAT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(CHANNEL_OUT)
                                .build())
                        .setBufferSizeInBytes(bufferSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();

                byte[] buffer = new byte[bufferSize];
                track.play();

                while (isPlaying) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    listenSocket.receive(packet);
                    int length = packet.getLength();
                    
                    // Simple noise gate for incoming audio to keep recordings clean
                    int max = 0;
                    for (int i = 0; i < length; i += 2) {
                        short sample = (short) ((buffer[i+1] << 8) | (buffer[i] & 0xff));
                        if (Math.abs(sample) > max) max = Math.abs(sample);
                    }
                    
                    if (max < NOISE_THRESHOLD) {
                        Arrays.fill(buffer, 0, length, (byte) 0);
                    }

                    track.write(buffer, 0, length);
                    
                    if (isRecordEnabled && currentRecordDir != null) {
                        if (max >= NOISE_THRESHOLD) { // Only record if there is actual sound
                            if (fos == null) {
                                File dir = new File(currentRecordDir);
                                if (!dir.exists()) dir.mkdirs();
                                
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault());
                                String filename = sdf.format(new java.util.Date()) + "_IN.amr";
                                fos = new FileOutputStream(new File(dir, filename));
                                fos.write("#!AMR-WB\n".getBytes());
                                encoder = createAmrEncoder();
                                encoder.start();
                            }
                            encodeAndWrite(encoder, fos, buffer, length);
                        } else if (fos != null) {
                            // If it's silent for long enough, we could close the file here, 
                            // but for walkie-talkie it's better to keep it open until session ends
                            // or just write the silence. 
                            // Requirement says: "Recorded files should not contain long noisy silence"
                            // Since we zeroed the buffer, it's digital silence, not noisy silence.
                            encodeAndWrite(encoder, fos, buffer, length);
                        }
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
