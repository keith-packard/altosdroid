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

import java.lang.ref.WeakReference;
import java.util.*;
import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.View.*;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

import androidx.appcompat.app.AppCompatActivity;

import org.altusmetrum.altosdroid.databinding.ConfigureDeviceBinding;
import org.altusmetrum.altoslib_14.*;

public class ConfigureDeviceActivity extends AppCompatActivity
                                     implements AltosUnitsListener
{

    ConfigureDeviceBinding binding;

    private Messenger service = null;
    private final Messenger messenger = new Messenger(new IncomingHandler(this));

    private boolean is_bound;

    // The Handler that gets information back from the Telemetry Service
    static class IncomingHandler extends Handler {
        private final WeakReference<ConfigureDeviceActivity> config_activity;
        IncomingHandler(ConfigureDeviceActivity ca) { config_activity = new WeakReference<ConfigureDeviceActivity>(ca); }

        @Override
        public void handleMessage(Message msg) {
            ConfigureDeviceActivity ca = config_activity.get();

            switch (msg.what) {
            case MainActivity.MSG_CONFIG_DATA:
                @SuppressWarnings("unchecked") AltosConfigDataRemote config_data = (AltosConfigDataRemote) msg.obj;
                ca.config_data(config_data);
                break;
            }
        }
    };

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

    public void setTitle(String title) {
        super.setTitle(title);
        getSupportActionBar().setTitle(title);
    }

    boolean query_running;

    private void group_visible(View view, double value) {
        if (value == AltosLib.MISSING)
            view.setVisibility(View.GONE);
        else
            view.setVisibility(View.VISIBLE);
    }

    boolean is_telemini_v1(String product) {
        return product != null && product.startsWith("TeleMini-v1");
    }

    boolean is_telemini(String product) {
        return product != null && product.startsWith("TeleMini");
    }

    boolean is_easymini(String product) {
        return product != null && product.startsWith("EasyMini");
    }

    boolean is_telemetrum(String product) {
        return product != null && product.startsWith("TeleMetrum");
    }

    boolean is_telemega(String product) {
        return product != null && product.startsWith("TeleMega");
    }

    boolean is_easymega(String product) {
        return product != null && product.startsWith("EasyMega");
    }

    boolean is_easytimer(String product) {
        return product != null && product.startsWith("EasyTimer");
    }

    public boolean has_radio(String product) {
        return is_telemega(product) || is_telemetrum(product) || is_telemini(product);
    }

    private String[] pad_orientation_values(AltosConfigDataRemote config_data) {
        if (has_radio(config_data.product))
            return AltosLib.pad_orientation_values_radio;
        else
            return AltosLib.pad_orientation_values_no_radio;
    }

    private void config_data(AltosConfigDataRemote config_data) {
        query_running = false;

        if (binding != null && config_data != null) {

            binding.mainAlt.setText(AltosConvert.height.say(config_data.main_deploy), false);
            group_visible(binding.mainAltGroup, config_data.main_deploy);

            binding.apogeeDelay.setText(Integer.toString(config_data.apogee_delay), false);
            group_visible(binding.apogeeDelayGroup, config_data.apogee_delay);

            binding.apogeeLockout.setText(Integer.toString(config_data.apogee_lockout), false);
            group_visible(binding.apogeeLockoutGroup, config_data.apogee_lockout);

            if (config_data.ignite_mode != AltosLib.MISSING)
                binding.igniterMode.setText(AltosLib.ignite_mode_values[config_data.ignite_mode], false);
            group_visible(binding.igniterModeGroup, config_data.ignite_mode);

            if (config_data.pad_orientation != AltosLib.MISSING)
                binding.padOrientation.setText(pad_orientation_values(config_data)[config_data.pad_orientation], false);
            group_visible(binding.padOrientationGroup, config_data.pad_orientation);

            if (config_data.beep != AltosLib.MISSING) {
                String freq;
                if (config_data.beep == 0)
                    freq = getResources().getStringArray(R.array.beeper_frequency_values)[0];
                else {
                    int val = (int) Math.floor (AltosConvert.beep_value_to_freq(config_data.beep) + 0.5);
                    freq = Integer.toString(val);
                }
                binding.beeperFrequency.setText(freq, false);
            }
            group_visible(binding.beeperFrequencyGroup, config_data.beep);

            binding.call.setText(config_data.callsign);
        }
    }

    private void query_data() {
        query_running = true;
        Thread thread = new Thread(new Runnable() {
                public void run() {
                    try {
                        Message msg = Message.obtain(null, TelemetryService.MSG_GET_CONFIG_DATA);
                        msg.replyTo = messenger;
                        msg.obj = (Boolean) true;
                        if (service == null) {
                            synchronized(ConfigureDeviceActivity.this) {
                                query_running = false;
                            }
                        } else
                            service.send(msg);
                    } catch (RemoteException re) {
                        AltosDebug.debug("config_data query thread failed");
                        synchronized(ConfigureDeviceActivity.this) {
                            query_running = false;
                        }
                    }
                }
            });
        thread.start();
    }

    private void setMenu(AutoCompleteTextView view, String[] values) {
        ArrayAdapter adapter = new ArrayAdapter(this, R.layout.dropdown_item, values);
        view.setAdapter(adapter);
    }

    private void setMenu(AutoCompleteTextView view, int arrayId) {
        String[] values = getResources().getStringArray(arrayId);
        setMenu(view, values);
    }

    private int getMenu(AutoCompleteTextView view, int arrayId) {
        String[] values = getResources().getStringArray(arrayId);
        String text = view.getEditableText().toString();
        for (int i = 0; i < values.length; i++)
            if (text.equals(values[i]))
                return i;
        return -1;
    }

    public String[] flightLogMaxValues(int flight_log_max_limit, int storage_erase_unit) {
        ArrayList<String> maxValues = new ArrayList<String>();

        for (int i = 8; i >= 1; i--) {
            int	size = flight_log_max_limit / i;
            if (storage_erase_unit != 0)
                size &= ~(storage_erase_unit - 1);
            maxValues.add(String.format("%d (%d flights)", size, i));
        }
        return maxValues.toArray(new String[0]);
    }

    public String[] frequencyValues() {
        AltosFrequency[] frequencies = AltosPreferences.common_frequencies();
        String[] frequencyValues = new String[frequencies.length];
        for (int i = 0; i < frequencies.length; i++)
            frequencyValues[i] = frequencies[i].toString();
        return frequencyValues;
    }

    private void
    set_units(boolean imperial_units) {

        if (binding == null)
            return;

        if (imperial_units)
            setMenu(binding.mainAlt, R.array.main_alt_values_ft);
        else
            setMenu(binding.mainAlt, R.array.main_alt_values_m);

        binding.mainAltLabel.setText(String.format("%s(%s)",
                                                   getResources().getString(R.string.main_alt),
                                                   AltosConvert.height.parse_units()));
    }

    @Override
    protected void onStart() {
        super.onStart();
        doBindService();
        AltosPreferences.register_units_listener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        doUnbindService();
        AltosPreferences.unregister_units_listener(this);
    }

    public void units_changed(boolean imperial_units) {
        set_units(imperial_units);
    }

    @Override
    protected void onResume() {
        super.onResume();

        /* Force window to full width instead of wrap_content */
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        getWindow().setAttributes(lp);

        /* Fill in menu items from any connected device */
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ConfigureDeviceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ActivityLayouts.applyEdgeToEdge(this, R.id.configure_device);

        // Set result CANCELED incase the user backs out
        setResult(Activity.RESULT_CANCELED);

        setMenu(binding.apogeeDelay, R.array.apogee_delay_values);
        setMenu(binding.apogeeLockout, R.array.apogee_lockout_values);
        setMenu(binding.igniterMode, AltosLib.ignite_mode_values);
        setMenu(binding.padOrientation, AltosLib.pad_orientation_values_radio);
        setMenu(binding.beeperFrequency, R.array.beeper_frequency_values);
        setMenu(binding.beeperUnits, R.array.beeper_units_values);
//        setMenu(binding.flightLogSize, R.array.flight_log_size_values);
        setMenu(binding.frequency, frequencyValues());

        set_units(AltosPreferences.imperial_units());

        binding.save.setOnClickListener(v -> save());
        binding.reset.setOnClickListener(v -> reset());
        binding.close.setOnClickListener(v -> close());
    }

    void save() {
    }

    void reset() {
        query_data();
    }

    void close() {
        Intent intent = new Intent();
        setResult(Activity.RESULT_OK, intent);
        finish();
    }
}
