package com.vytran.empatica;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class CollectSleepDataActivity extends AppCompatActivity {

    private static final String TAG = "Empatica";
    private static final int REQUEST_PERMISSION_ACCESS_COARSE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private boolean isServiceRunning = false;


    private TextView accel_xLabel;
    private TextView accel_yLabel;
    private TextView accel_zLabel;
    private TextView bvpLabel;
    private TextView edaLabel;
    private TextView ibiLabel;
    private TextView temperatureLabel;
    private TextView batteryLabel;
    private TextView onWristLabel;
    private TextView statusLabel;
    private TextView deviceNameLabel;
    private LinearLayout dataCnt;
    private Button toggleCollectionButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_collect_sleep_data);

        // Initialize vars that reference UI components
        statusLabel = (TextView) findViewById(R.id.status);
        dataCnt = (LinearLayout) findViewById(R.id.dataArea);
        accel_xLabel = (TextView) findViewById(R.id.accel_x);
        accel_yLabel = (TextView) findViewById(R.id.accel_y);
        accel_zLabel = (TextView) findViewById(R.id.accel_z);
        bvpLabel = (TextView) findViewById(R.id.bvp);
        edaLabel = (TextView) findViewById(R.id.eda);
        ibiLabel = (TextView) findViewById(R.id.ibi);
        temperatureLabel = (TextView) findViewById(R.id.temperature);
        batteryLabel = (TextView) findViewById(R.id.battery);
        deviceNameLabel = (TextView) findViewById(R.id.deviceName);
        onWristLabel = (TextView) findViewById(R.id.wrist_status_label);

        toggleCollectionButton = findViewById(R.id.toggleCollection);
        isServiceRunning = isServiceRunning(EmpaticaService.class);

        toggleCollectionButton.setText(isServiceRunning ? "STOP COLLECTION" : "START COLLECTION");

        toggleCollectionButton.setOnClickListener(v -> {
            if (isServiceRunning) {
                stopCollection();
            } else {
                startCollection();
            }
        });
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void stopCollection() {
        // Stop the service and update the button text
        stopService(new Intent(CollectSleepDataActivity.this, EmpaticaService.class));
        toggleCollectionButton.setText("START COLLECTION");
        isServiceRunning = false;
    }

    private void startCollection() {
        // Check permissions before starting the service
        if (ContextCompat.checkSelfPermission(CollectSleepDataActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Start the service and update the button text
            startService(new Intent(CollectSleepDataActivity.this, EmpaticaService.class));
            toggleCollectionButton.setText("STOP COLLECTION");
            isServiceRunning = true;
        } else {
            // Request permissions
            ActivityCompat.requestPermissions(CollectSleepDataActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_ACCESS_COARSE_LOCATION);
        }
    }

    private final BroadcastReceiver dataUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(EmpaticaService.ACTION_UPDATE_LABEL)) {
                String labelType = intent.getStringExtra(EmpaticaService.EXTRA_LABEL_TYPE);
                String labelValue = intent.getStringExtra(EmpaticaService.EXTRA_LABEL_VALUE);
                runOnUiThread(() -> updateLabel(labelType, labelValue));
            }
        }
    };

    private void updateLabel(String labelType, String labelValue) {
      switch (labelType) {
          case "showOrHide":
              if (labelValue.equals("show")) {
                  dataCnt.setVisibility(View.VISIBLE);
              } else if (labelValue.equals("hide")) {
                  dataCnt.setVisibility(View.INVISIBLE);
              } else {
                  showError("UNKNOWN showOrHide label " + labelValue);
              }
              break;
          case "deviceName":
              deviceNameLabel.setText(labelValue);
              break;
          case "status":
              statusLabel.setText(labelValue);
              break;
          case "onwrist":
              onWristLabel.setText(labelValue);
              break;
          case "temperature":
              temperatureLabel.setText(labelValue);
              break;
          case "eda":
              edaLabel.setText(labelValue);
              break;
          case "ibi":
              ibiLabel.setText(labelValue);
              break;
          case "battery":
              batteryLabel.setText(labelValue);
          case "bvp":
              bvpLabel.setText(labelValue);
              break;
          case "accel_x":
              accel_xLabel.setText(labelValue);
              break;
          case "accel_y":
              accel_yLabel.setText(labelValue);
              break;
          case "accel_z":
              accel_zLabel.setText(labelValue);
              break;
          case "requestEnableBt":
              // Request the user to enable Bluetooth
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            break;
          default:
              showError("UNKNOWN LABEL");
      }
    }

    private void showError(String error) {
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        Log.e(TAG, error);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(EmpaticaService.ACTION_UPDATE_LABEL);
        registerReceiver(dataUpdateReceiver, filter);
        isServiceRunning = isServiceRunning(EmpaticaService.class);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(dataUpdateReceiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_ACCESS_COARSE_LOCATION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted, yay!
                    startCollection();
                } else {
                    // Permission denied, boo!
                    final boolean needRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION);
                    new AlertDialog.Builder(this)
                            .setTitle("Permission required")
                            .setMessage("Without this permission bluetooth low energy devices cannot be found, allow it in order to connect to the device.")
                            .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // try again
                                    if (needRationale) {
                                        // the "never ask again" flash is not set, try again with permission request
                                        startCollection();
                                    } else {
                                        // the "never ask again" flag is set so the permission requests is disabled, try open app settings to enable the permission
                                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                                        intent.setData(uri);
                                        startActivity(intent);
                                    }
                                }
                            })
                            .setNegativeButton("Exit application", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // without permission exit is the only way
                                    finish();
                                }
                            })
                            .show();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The user chose not to enable Bluetooth
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // You should deal with this
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
