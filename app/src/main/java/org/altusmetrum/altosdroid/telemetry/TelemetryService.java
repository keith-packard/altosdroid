package org.altusmetrum.altosdroid.telemetry;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class TelemetryService extends Service {
    @Override
    public void onCreate() {
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    @Override
    public void onDestroy() {
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
