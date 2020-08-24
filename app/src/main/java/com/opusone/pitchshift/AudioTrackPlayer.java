package com.opusone.pitchshift;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

public class AudioTrackPlayer {
    private AudioTrack audioPlayer;
    private Thread mThread;
    private int sampleRate;
    int bufferRate = 0;
    private int bufferSize;
    private byte[] byteBuffer = null;
    public AtomicBoolean isPlay = new AtomicBoolean(false);

    public AudioTrackPlayer(int sampleRate, int bufferRate) {
        this.sampleRate = sampleRate;
        this.bufferRate = bufferRate;
        audioPlayer = createAudioPlayer();
    }

    private AudioTrack createAudioPlayer() {

        int intSize = android.media.AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferRate, AudioTrack.MODE_STREAM);
        if (audioTrack == null) {
            Log.d("TCAudio", "audio track is not initialised ");
            return null;
        }
        return audioTrack;
    }

    private class PlayerProcess implements Runnable {

        @Override
        public void run() {
            if (audioPlayer != null) {
                audioPlayer.play();
                int readByteSize = 0;
                while (readByteSize < bufferSize && isPlay.get()) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    audioPlayer.write(byteBuffer, readByteSize, bufferRate);
                    readByteSize += bufferRate;
                }
                if (audioPlayer.getState() != AudioTrack.PLAYSTATE_STOPPED) {
                    audioPlayer.stop();
                    audioPlayer.release();
                    mThread = null;
                }
            }

            isPlay.set(false);
        }
    }


    public void play(byte[] srcBuffer, int bufferSize) {
        this.byteBuffer = srcBuffer;
        this.bufferSize = bufferSize;
        if (audioPlayer == null) return;

        isPlay.set(true);
        mThread = new Thread(new PlayerProcess());
        mThread.start();
    }

    public void stop() {
        isPlay.set(false);
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
        if (audioPlayer != null) {
            audioPlayer.stop();
            audioPlayer.release();
            audioPlayer = null;
        }
    }
}