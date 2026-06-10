package com.tfajfar.walkietalkie.core;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
    
    private final ExecutorService talkExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService listenExecutor = Executors.newSingleThreadExecutor();
    
    private DatagramSocket listenSocket;
    private AudioRecord recorder;
    private AudioTrack track;

    public void startTalking(String ipAddress, String recordPath) {
        if (isRecording) return;
        isRecording = true;

        talkExecutor.execute(() -> {
            DatagramSocket talkSocket = null;
            FileOutputStream fos = null;
            try {
                talkSocket = new DatagramSocket();
                int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT);
                try {
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT, minBufSize);
                } catch (SecurityException e) {
                    Log.e(TAG, "Permission denied for AudioRecord", e);
                    isRecording = false;
                    return;
                }
                
                if (recordPath != null) {
                    fos = new FileOutputStream(recordPath);
                }
                
                byte[] buffer = new byte[minBufSize];
                recorder.startRecording();
                InetAddress address = InetAddress.getByName(ipAddress);

                while (isRecording) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        DatagramPacket packet = new DatagramPacket(buffer, read, address, PORT);
                        talkSocket.send(packet);
                        if (fos != null) fos.write(buffer, 0, read);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Talk error", e);
            } finally {
                stopAndReleaseRecorder();
                if (talkSocket != null) talkSocket.close();
                if (fos != null) {
                    try { fos.close(); } catch (IOException ignored) {}
                }
            }
        });
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
            } catch (IllegalStateException e) {
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
                    track.write(packet.getData(), 0, packet.getLength());
                }
            } catch (IOException e) {
                Log.e(TAG, "Listen error", e);
            } finally {
                stopAndReleaseTrack();
                if (listenSocket != null) {
                    listenSocket.close();
                    listenSocket = null;
                }
            }
        });
    }

    public void stopListening() {
        isPlaying = false;
        if (listenSocket != null) {
            listenSocket.close(); // Interrupts receive()
            listenSocket = null;
        }
    }

    private synchronized void stopAndReleaseTrack() {
        if (track != null) {
            try {
                if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop();
                }
            } catch (IllegalStateException e) {
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
