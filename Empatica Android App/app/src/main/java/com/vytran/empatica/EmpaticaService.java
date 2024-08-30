package com.vytran.empatica;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.EmpaticaDevice;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

public class EmpaticaService extends Service implements EmpaDataDelegate, EmpaStatusDelegate {
    private static final String TAG = HomeActivity.TAG;
    public static final String ACTION_UPDATE_LABEL = "com.vytran.empatica.ACTION_UPDATE_LABEL";
    public static final String EXTRA_LABEL_TYPE = "com.vytran.empatica.EXTRA_LABEL_TYPE";
    public static final String EXTRA_LABEL_VALUE = "com.vytran.empatica.EXTRA_LABEL_VALUE";


    private static final String EMPATICA_API_KEY = "94cbf8486442423995ccfcd01156f355";
    // other team's key "03be4a7bd83c4a6b82dd038341f6d618";
    private EmpaDeviceManager deviceManager;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if ("STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Initialize and start Empatica device manager
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showError("No permission - service will fail!");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
        deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);

        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        deviceManager.authenticateWithAPIKey(EMPATICA_API_KEY);

        // Create a notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "EmpaticaServiceChannel",
                    "Empatica Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }

        // Create a PendingIntent for the Stop action
        PendingIntent pendingIntent = PendingIntent.getService(
                this,
                0,
                new Intent(this, EmpaticaService.class).setAction("STOP"),
                PendingIntent.FLAG_IMMUTABLE
        );

        // Create the notification
        Notification notification = new NotificationCompat.Builder(this, "EmpaticaServiceChannel")
                .setContentTitle("Empatica Data Collection")
                .setContentText("Collecting data...")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your icon
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingIntent) // Replace with your stop icon
                .build();

        // Start the service in the foreground
        startForeground(1, notification);
        return START_NOT_STICKY;
    }

    private void showError(String error) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(EmpaticaService.this.getApplicationContext(),error, Toast.LENGTH_LONG).show());
        Log.e(TAG, error);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (deviceManager != null) {
            deviceManager.stopScanning();
            deviceManager.disconnect();
            deviceManager.cleanUp();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void didDiscoverDevice(EmpaticaDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
        // Check if the discovered device can be used with your API key. If allowed is always false,
        // the device is not linked with your API key. Please check your developer area at
        // https://www.empatica.com/connect/developer.php

        Log.i(TAG, "didDiscoverDevice" + deviceName + "allowed: " + allowed);

        if (allowed) {
            // Stop scanning. The first allowed device will do.
            deviceManager.stopScanning();
            try {
                // Connect to the device
                deviceManager.connectDevice(bluetoothDevice);
                sendBroadcast("deviceName", "To: " + deviceName);
            } catch (ConnectionNotAllowedException e) {
                // This should happen only if you try to connect when allowed == false.
                showError("Sorry, you can't connect to this device");
                Log.e(TAG, "didDiscoverDevice" + deviceName + "allowed: " + allowed + " - ConnectionNotAllowedException", e);
            }
        } else {
            showError("Found device but not authorized!");
//            startFakeDataGenerator();
        }
    }

    private Handler fakeDataHandler = new Handler();
    private Runnable fakeDataRunnable;

    private void startFakeDataGenerator() {
        fakeDataRunnable = new Runnable() {
            @Override
            public void run() {
                // Generate random data
                int x = (int)(Math.random() * 3) - 1; // Random int between -1 and 1
                int y = (int)(Math.random() * 3) - 1;
                int z = (int)(Math.random() * 3) - 1;
                float bvp = 40 + (float)(Math.random() * 80); // Random float between 40 and 120

                // Simulate receiving data
                didReceiveAcceleration(x, y, z, System.currentTimeMillis());
                didReceiveBVP(bvp, System.currentTimeMillis());

                // Schedule the next run
                fakeDataHandler.postDelayed(this, 1000); // 1000 ms for 1 second interval
            }
        };

        fakeDataHandler.post(fakeDataRunnable);
    }

    private void stopFakeDataGenerator() {
        if (fakeDataHandler != null && fakeDataRunnable != null) {
            fakeDataHandler.removeCallbacks(fakeDataRunnable);
        }
    }

    @Override
    public void didFailedScanning(int errorCode) {

        /*
         A system error occurred while scanning.
         @see https://developer.android.com/reference/android/bluetooth/le/ScanCallback
        */
        switch (errorCode) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED:
                showError("Scan failed: a BLE scan with the same settings is already started by the app");
                break;
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                showError("Scan failed: app cannot be registered");
                break;
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED:
                showError("Scan failed: power optimized scan feature is not supported");
                break;
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR:
                showError("Scan failed: internal error");
                break;
            default:
                showError("Scan failed with unknown error (errorCode=" + errorCode + ")");
                break;
        }
    }

    @Override
    public void didRequestEnableBluetooth() {
        showError("Need to enable BT");
        Log.e(TAG,"Asking user to enable BT");
        sendBroadcast("requestEnableBt", "request");
    }

    @Override
    public void bluetoothStateChanged() {
        // E4link detected a bluetooth adapter change
        // Check bluetooth adapter and update your UI accordingly.
        boolean isBluetoothOn = BluetoothAdapter.getDefaultAdapter().isEnabled();
        Log.i(TAG, "Bluetooth State Changed: " + isBluetoothOn);
    }

    @Override
    public void didUpdateSensorStatus(@EmpaSensorStatus int status, EmpaSensorType type) {
        Log.i(TAG,"Updated sensor status");

        didUpdateOnWristStatus(status);
    }

    @Override
    public void didUpdateStatus(EmpaStatus status) {
        Log.i(TAG,"Updated status " + status.name());
        // Update the UI
        sendBroadcast("status", status.name());

        // The device manager is ready for use
        if (status == EmpaStatus.READY) {
            sendBroadcast("status", status.name() + " - Turn on your device");
            // Start scanning
            deviceManager.startScanning();
            // The device manager has established a connection
            sendBroadcast("showOrHide", "hide");
        } else if (status == EmpaStatus.CONNECTED) {
            sendBroadcast("showOrHide", "show");
            // The device manager disconnected from a device
        } else if (status == EmpaStatus.DISCONNECTED) {
            sendBroadcast("deviceName", "");
            sendBroadcast("showOrHide", "hide");
        }
    }

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {
        Log.i(TAG,"Received accelerometer");

        sendBroadcast("accel_x", "" + x);
        sendBroadcast("accel_y", "" + y);
        sendBroadcast("accel_z", "" + z);
    }

    @Override
    public void didReceiveBVP(float bvp, double timestamp) {
        Log.i(TAG,"Received BVP");

        sendBroadcast("bvp", "" + bvp);
    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        Log.i(TAG,"Received battery level");

        sendBroadcast("battery", String.format("%.0f %%", battery * 100));
    }

    @Override
    public void didReceiveGSR(float gsr, double timestamp) {
        Log.i(TAG,"Received GSR");

        sendBroadcast("eda", "" + gsr);
    }

    @Override
    public void didReceiveIBI(float ibi, double timestamp) {
        Log.i(TAG,"received IBI");

        sendBroadcast("ibi", "" + ibi);
    }

    @Override
    public void didReceiveTemperature(float temp, double timestamp) {
        Log.i(TAG,"Received temperature");

        sendBroadcast("temperature", "" + temp);
    }

    // Update a label with some text, making sure this is run in the UI thread
    private void sendBroadcast(String label, String text) {
        Log.i(TAG,"Received label " + label + " text: " + text);

        final Intent intent = new Intent(ACTION_UPDATE_LABEL);
        intent.putExtra(EXTRA_LABEL_TYPE, label);
        intent.putExtra(EXTRA_LABEL_VALUE, text);
        sendBroadcast(intent);
    }

    @Override
    public void didReceiveTag(double timestamp) {
        Log.i(TAG,"Received tag");
    }

    @Override
    public void didEstablishConnection() {
        Log.i(TAG,"Established connection");
        sendBroadcast("showOrHide", "show");
    }

    @Override
    public void didUpdateOnWristStatus(@EmpaSensorStatus final int status) {
        Log.i(TAG,"Updated on wrist status");
        sendBroadcast("onwrist", status == EmpaSensorStatus.ON_WRIST ? "ON WRIST" : "NOT ON WRIST");
    }
}