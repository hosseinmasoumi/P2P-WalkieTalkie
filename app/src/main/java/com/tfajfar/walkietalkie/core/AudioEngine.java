package com.tfajfar.walkietalkie.core;

import android.annotation.SuppressLint;
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

public class AudioEngine {
    private static final int SAMPLE_RATE = 8000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int PORT = 50005;

    private boolean isRecording = false;
    private boolean isPlaying = false;
    private DatagramSocket socket;
    private String remoteAddress;

    @SuppressLint("MissingPermission")
    public void startTalking(String ipAddress, String recordPath) {
        this.remoteAddress = ipAddress;
        isRecording = true;
        new Thread(() -> {
            try (DatagramSocket talkSocket = new DatagramSocket()) {
                int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
                AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBufSize);
                
                FileOutputStream fos = recordPath != null ? new FileOutputStream(recordPath) : null;
                byte[] buffer = new byte[minBufSize];
                recorder.startRecording();

                while (isRecording) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        // Send over network
                        DatagramPacket packet = new DatagramPacket(buffer, read, InetAddress.getByName(remoteAddress), PORT);
                        talkSocket.send(packet);
                        
                        // Local record
                        if (fos != null) fos.write(buffer, 0, read);
                    }
                }
                recorder.stop();
                recorder.release();
                if (fos != null) fos.close();
            } catch (IOException e) {
                Log.e("AudioEngine", "Talk error", e);
            }
        }).start();
    }

    public void stopTalking() {
        isRecording = false;
    }

    public void startListening() {
        isPlaying = true;
        new Thread(() -> {
            try {
                socket = new DatagramSocket(PORT);
                int minBufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AUDIO_FORMAT);
                AudioTrack track = new AudioTrack.Builder()
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AUDIO_FORMAT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(minBufSize)
                        .build();

                byte[] buffer = new byte[minBufSize];
                track.play();

                while (isPlaying) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    track.write(packet.getData(), 0, packet.getLength());
                }
                track.stop();
                track.release();
            } catch (IOException e) {
                Log.e("AudioEngine", "Listen error", e);
            } finally {
                if (socket != null) socket.close();
            }
        }).start();
    }

    public void stopListening() {
        isPlaying = false;
        if (socket != null) socket.close();
    }
}
