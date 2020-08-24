package com.opusone.pitchshift;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import com.opusone.pitchshift.databinding.ActivityMainBinding;

import java.util.ArrayList;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.GainProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.resample.RateTransposer;


public class MainActivity extends AppCompatActivity {
    ActivityMainBinding binding;

    private static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 235;
    private static final int MY_PERMISSIONS_REQUEST_MODIFY_AUDIO_SETTINGS = 183;

    private boolean isRecording = false;
    private Thread recordThread;
    private AudioDispatcher dispatcher;
    RateTransposer rateTransposer;
    GainProcessor gain;
    SilenceDetector silenceDetector;
    VoiceDetectProcess voiceConvertor;
    private int sampleRate = 44100;
    private int channel = 2;
    private int bufferRate = 4096 * channel;
    double threshold = 4.5;
    double currentFactor = 0.8; // 0.1 ~ 4.9 사이값
    double currentGain = 0.5;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(
                this, R.layout.activity_main);


        binding.startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                start();
            }
        });
        binding.stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stop();
            }
        });
        requestRecordingPermission();
    }

    private void disableRecordButton() {
        binding.startButton.setEnabled(false);
    }

    private void enableRecordButton() {
        binding.startButton.setEnabled(true);
    }

    void createDispatcher() {
        while (this.dispatcher == null) {
            try {
                this.dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(this.sampleRate, this.bufferRate, 0);
            } catch (Exception exception) {
                Integer usedSampleRate = 0;
                ArrayList<Integer> testSampleRates = SampleRateCalculator.getAllSupportedSampleRates();
                for (Integer testSampleRate : testSampleRates) {
                    try {
                        this.dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(testSampleRate, this.bufferRate, 0);
                    } catch (Exception exception_) {
                        Log.d("debug", "samplerate !supported " + String.valueOf(testSampleRate));
                    }
                }

            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestRecordingPermission() {
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);

        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            this.requestModifyAudioSettingsPermission();
        } else {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
            //            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void requestModifyAudioSettingsPermission() {
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.MODIFY_AUDIO_SETTINGS);

        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            this.enableRecordButton();
        } else {
            this.requestPermissions(new String[]{Manifest.permission.MODIFY_AUDIO_SETTINGS},
                    MY_PERMISSIONS_REQUEST_MODIFY_AUDIO_SETTINGS);
        }
    }

    void start() {
        {
            if (!this.isRecording) {
                this.isRecording = true;

                createDispatcher();

                silenceDetector = new SilenceDetector(threshold, false);
                voiceConvertor = new VoiceDetectProcess(silenceDetector, this.sampleRate, this.bufferRate, this.channel);
                rateTransposer = new RateTransposer(currentFactor);
                gain = new GainProcessor(currentGain);

                dispatcher.addAudioProcessor(silenceDetector);
                dispatcher.addAudioProcessor(rateTransposer);
                //dispatcher.addAudioProcessor(gain);
                dispatcher.addAudioProcessor(voiceConvertor);

                this.recordThread = new Thread(dispatcher, "Audio Dispatcher");
                this.recordThread.start();
            }
        }
    }

    void stop() {
        if (this.isRecording) {
            try {
                if (this.dispatcher != null) {
                    this.dispatcher.stop();
                }

                if (this.recordThread != null) {
                    this.recordThread.stop();
                }
            } catch (Exception ex) {
                if (ex.getMessage() != null) {
                    Log.d("debug", ex.getMessage());
                }
                Log.d("debug", ex.getStackTrace().toString());
            }
        }
    }

    public void processPitch(float pitchInHz) {
        binding.textView.setText("" + pitchInHz);
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        Log.i("debug", String.format("permission result in: %s", requestCode));

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // if recording permission was granted, also request permission to modify audio settings
                    this.requestModifyAudioSettingsPermission();
                    Log.i("info", "permission granted");
                } else {
                    // disable "record" button if recording permission was denied
                    Log.i("info", "permission denied");

                    // show explanation why mic permission is needed
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage("In order to record your voice, you need to give Voice Pitch Analyzer permission to access this device\\'s microphone.")
                            .setTitle("Microphone access required")
                            .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:de.lilithwittmann.voicepitchanalyzer")));
                                }

                            })
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            });

                    AlertDialog dialog = builder.create();
                    dialog.show();
                }

                return;
            }

            case MY_PERMISSIONS_REQUEST_MODIFY_AUDIO_SETTINGS: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // if permission for record audio & modify audio settings was granted,
                    // enable "record" button & set sampling rate
                    this.enableRecordButton();
                } else {
                    // disable "record" button if modify audio settings permission was denied
                    this.disableRecordButton();
                }

                return;
            }
        }
    }
}