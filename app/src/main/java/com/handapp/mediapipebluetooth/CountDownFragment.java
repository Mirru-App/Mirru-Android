package com.handapp.mediapipebluetooth;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.ToggleButton;

public class CountDownFragment extends Fragment {
    private CountDownTimer countDownTimer;
    private boolean isTimerRunning = false;
    private long startTimeInMilliseconds = 20000; //10 seconds
    private ToggleButton toggleButton;
    private TextView countDownText;

    public CountDownFragment() {
        // Required empty public constructor
    }

    public interface CountdownInterface {
        void sendCountdownState(boolean isTimerRunning);
    }

    CountdownInterface countDownInterface;
    RadioGroup radioGroup;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.countdown_fragment, container, false);
        countDownText = view.findViewById(R.id.countdown_text);
        toggleButton = view.findViewById(R.id.toggleButton);

        radioGroup = (RadioGroup) view.findViewById(R.id.radioGroup);
        RadioButton radio1 = (RadioButton) view.findViewById(R.id.time1);
        RadioButton radio2 = (RadioButton) view.findViewById(R.id.time1);
        RadioButton radio3 = (RadioButton) view.findViewById(R.id.time1);


        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (toggleButton.isChecked()) {
                    StartTimer();
                } else if (!toggleButton.isChecked()){
                    StopTimer();
                }
                countDownInterface.sendCountdownState(isTimerRunning);
            }
        });
        return view;
    }

    public void onRadioButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();

        switch(view.getId()) {
            case R.id.time1:
                if (checked)
                    System.out.println("1");
                    break;
            case R.id.time2:
                if (checked)
                    System.out.println("2");
                    break;
            case R.id.time3:
                if (checked)
                    System.out.println("3");
                    break;
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof CountdownInterface) {
            countDownInterface = (CountdownInterface) context;
        } else {
            throw new RuntimeException(context.toString() + "must implement CountDownInterface");
        }

    }

    @Override
    public void onDetach() {
        super.onDetach();
        countDownInterface = null;
    }

    public void StartTimer() {
        countDownTimer = new CountDownTimer(startTimeInMilliseconds, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countDownText.setText("" + millisUntilFinished/1000);
            }

            @Override
            public void onFinish() {
                StopTimer();
            }
        }.start();
        countDownText.setText(startTimeInMilliseconds/1000 + "");
        isTimerRunning = true;
    }

    public void StopTimer() {
        countDownTimer.cancel();
        toggleButton.setChecked(false);
        isTimerRunning = false;
        countDownText.setText("");
        countDownInterface.sendCountdownState(isTimerRunning);
    }
}