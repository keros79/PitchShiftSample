package com.opusone.pitchshift;

import android.util.Log;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;

public class VoiceDetectProcess implements AudioProcessor {

    final int STATE_READY = 0;
    final int STATE_RECORDING = 1;
    final int STATE_PLAY = 2;
    final int STATE_PLAYING = 3;

    final int MAX_BUFFER_SIZE = 4096 * 200;
    byte[] writeBuffer = new byte[MAX_BUFFER_SIZE];
    int writeBufferSize = 0;

    int state = STATE_READY;
    int count = 0;
    private int sampleRate;
    private int bufferRate = 4096;
    private int channel;

    private AudioTrackPlayer audioTrackPlayer;
    private SilenceDetector silenceDetector;

    public VoiceDetectProcess(SilenceDetector silenceDetector, int sampleRate, int bufferRate, int channel) {
        this.silenceDetector = silenceDetector;
        this.sampleRate = sampleRate;
        this.bufferRate = bufferRate;
        this.channel = channel;
        audioTrackPlayer = new AudioTrackPlayer(sampleRate, bufferRate);
    }

    // spl -90 ~
    @Override
    public boolean process(AudioEvent audioEvent) {
        double spl = (silenceDetector != null) ? silenceDetector.currentSPL() : -1000;
        switch (state) {
            case STATE_READY:
                Log.d("test", "==STATE_READY");
                detectSound(spl);
                break;
            case STATE_RECORDING:
                Log.d("test", "==STATE_RECORDING");
                recording(audioEvent);
                detectSilence(spl);
                break;
            case STATE_PLAY:
                Log.d("test", "==STATE_PLAY");
                play();
                break;
            case STATE_PLAYING:
                Log.d("test", "==STATE_PLAYING");
                playing();
                break;
        }
        return false;
    }

    @Override
    public void processingFinished() {

    }

    private void detectSound(double spl) {
        if (spl > -60) {
            writeBufferSize = 0;
            state = STATE_RECORDING;
        }
    }

    private void detectSilence(double spl) {
        if (spl > -70) {
            count = 0;
        } else if ((++count) > 4) {
            state = STATE_PLAY;
        }
    }

    public void recording(AudioEvent audioEvent) {
        int bufferSize = audioEvent.getBufferSize() * 2;
        System.arraycopy(audioEvent.getByteBuffer(), 0, writeBuffer, writeBufferSize, bufferSize);
        writeBufferSize += bufferSize;
        if ((writeBufferSize + bufferRate) >= (MAX_BUFFER_SIZE)) {
            state = STATE_PLAY;
        }
    }

    public void play() {
        if (audioTrackPlayer != null && !audioTrackPlayer.isPlay.get()) {
            audioTrackPlayer.play(writeBuffer, writeBufferSize);
            state = STATE_PLAYING;
            count = 0;
        }
    }

    public void playing() {
        if (audioTrackPlayer != null && !audioTrackPlayer.isPlay.get()) {
            state = STATE_READY;
        }
    }
}
