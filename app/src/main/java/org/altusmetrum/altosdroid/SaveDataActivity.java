/*
 * Copyright © 2026 Keith Packard <keithp@keithp.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 */

package org.altusmetrum.altosdroid;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.altusmetrum.altosdroid.databinding.ConfigureDeviceBinding;
import org.altusmetrum.altosdroid.databinding.DeviceDataBinding;
import org.altusmetrum.altosdroid.databinding.SaveDataBinding;
import org.altusmetrum.altoslib_14.AltosEepromList;
import org.altusmetrum.altoslib_14.AltosEepromLog;

class DeviceData {
    AltosEepromLog log;

    DeviceDataBinding binding;

    public void update() {
        if (log.flight >= 0)
            binding.deviceFlight.setText(String.format("Flight %d", log.flight));
        else
            binding.deviceFlight.setText(String.format("Slot %d", -log.flight));
        binding.deviceStatus.setText(String.format("Block %d - %d", log.start_block, log.end_block));
    }

    public DeviceData(AltosEepromLog in_log) {
        log = in_log;
    }
}

class DeviceDataAdapter extends ArrayAdapter<DeviceData> {

    int resource;
    SaveDataActivity save_data;

    public DeviceDataAdapter(SaveDataActivity in_context, int in_resource) {
        super(in_context, in_resource);
        resource = in_resource;
        save_data = in_context;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        DeviceData item = getItem(position);
        if (item.binding == null) {
            item.binding = DeviceDataBinding.inflate(save_data.getLayoutInflater(), parent, false);
            item.update();
        }
        return item.binding.getRoot();
    }
}

public class SaveDataActivity extends AppCompatActivity {
    SaveDataBinding binding;

    private Messenger service = null;
    private final Messenger messenger = new Messenger(new IncomingHandler(this));

    private DeviceDataAdapter data_adapter;

    AltosConfigDataRemote config_data;

    AltosEepromList flights;

    // Message types received by our Handler

    public static final int MSG_FLIGHTS = 1;

    private void done() {
        Intent intent = new Intent();
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    private void recv_config_data(AltosConfigDataRemote in_config_data) {
        config_data = in_config_data;
        binding.deviceType.setText(config_data.product);
        binding.deviceSerial.setText(Integer.toString(config_data.serial));
    }

    private void recv_flights(AltosEepromList in_flights) {
        flights = in_flights;
        if (flights == null)
            return;
        for (AltosEepromLog flight : flights) {
            data_adapter.add(new DeviceData(flight));
        }
    }

    // The Handler that gets information back from the Telemetry Service
    static class IncomingHandler extends Handler {
        private final SaveDataActivity activity;

        IncomingHandler(SaveDataActivity in_activity) {
            activity = in_activity;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MainActivity.MSG_CONFIG_DATA:
                //AltosDebug.ddebug("MSG_CONFIG_DATA");
                AltosConfigDataRemote config_data = (AltosConfigDataRemote) msg.obj;
                activity.recv_config_data(config_data);
                break;
            case MSG_FLIGHTS:
                //AltosDebug.debug("MSG_FLIGHTS");
                AltosEepromList flights = (AltosEepromList) msg.obj;
                activity.recv_flights(flights);
                break;
            }
        }
    };

    boolean query_running;

    private void query_data() {
        if (service == null || query_running)
            return;
        query_running = true;
        try {
            Message msg;

            msg = Message.obtain(null, TelemetryService.MSG_GET_CONFIG_DATA);
            msg.replyTo = messenger;
            msg.obj = (Boolean) true;
            service.send(msg);

            msg = Message.obtain(null, TelemetryService.MSG_GET_FLIGHTS);
            msg.replyTo = messenger;
            msg.obj = (Boolean) true;
            service.send(msg);

        } catch (RemoteException re) {
            AltosDebug.debug("config_data query thread failed");
            query_running = false;
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder binder) {
                service = new Messenger(binder);
                query_data();
            }

            public void onServiceDisconnected(ComponentName className) {
                // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
                service = null;
            }
	};

    boolean is_bound;

    void doBindService() {
        bindService(new Intent(this, TelemetryService.class), connection, Context.BIND_AUTO_CREATE);
        is_bound = true;
    }

    void doUnbindService() {
        if (is_bound) {
            // If we have received the service, and hence registered with it, then now is the time to unregister.
            unbindService(connection);
            is_bound = false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        data_adapter = new DeviceDataAdapter(this, R.layout.device_data);

        binding = SaveDataBinding.inflate(getLayoutInflater());
        binding.saveDataList.setAdapter(data_adapter);

        binding.close.setOnClickListener(v -> done());

        setContentView(binding.getRoot());
        ActivityLayouts.applyEdgeToEdge(this, R.id.save_data);
        new IncomingHandler(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        doBindService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        doUnbindService();
    }
}
