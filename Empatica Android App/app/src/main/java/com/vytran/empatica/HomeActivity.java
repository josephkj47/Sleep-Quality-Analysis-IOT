package com.vytran.empatica;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

import android.content.Intent;
import android.widget.Button;

public class HomeActivity extends AppCompatActivity {
    static final String TAG = "Empatica";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Button btnCollectSleepData = findViewById(R.id.btn_collect_sleep_data);
        Button btnPredictSleepPhases = findViewById(R.id.btn_predict_sleep_phases);

        btnCollectSleepData.setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, CollectSleepDataActivity.class)));

        btnPredictSleepPhases.setOnClickListener(v -> startActivity(new Intent(HomeActivity.this, PredictSleepPhasesActivity.class)));
    }
}
