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
import java.text.ParseException;
import java.util.*;
import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.View.*;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.Group;

import org.altusmetrum.altosdroid.databinding.ConfigureDeviceBinding;
import org.altusmetrum.altoslib_14.*;

class MissingValueException extends Exception {
    String field;

    MissingValueException(String in_field) {
        field = in_field;
    }
}

class FlightLogSize {
    int size_kb;
    int flights;

    public String toString() {
        return String.format("%d (%d flights)", size_kb, flights);
    }

    FlightLogSize(int in_size_kb, int in_flights) {
        size_kb = in_size_kb;
        flights = in_flights;
    }
}

public class ConfigureDeviceActivity extends AppCompatActivity
    implements AltosUnitsListener, AltosConfigValues
{
    ConfigureDeviceBinding binding;

    private Messenger service = null;
    private final Messenger messenger = new Messenger(new IncomingHandler(this));

    private boolean is_bound;

    AltosConfigDataRemote config_data;

    private int pyro_channel = AltosLib.MISSING;
    private AltosPyro[] pyros = null;
    private String[] pyro_channel_values;

    AltosPyro current_pyro() {
        if (pyros != null && pyro_channel < pyros.length)
            return pyros[pyro_channel];
        return null;
    }

    class PyroConfig {
        CompoundButton              button;
        AutoCompleteTextView        auto_view;
        EditText                    view;
        int                         flag;

        private String value(AltosPyro pyro) {
            if (pyro == null)
                return "";
            double value = pyro.get_value(flag);
            if ((flag & AltosPyro.pyro_state_value) != 0) {
                int ivalue = (int) value;
                if (AltosLib.ao_flight_boost <= ivalue && ivalue < AltosLib.ao_flight_landed)
                    return state_names[ivalue - AltosLib.ao_flight_boost];
                return "";
            } else {
                double	scale = AltosPyro.pyro_to_scale(flag);
                AltosUnits units = AltosPyro.pyro_to_units(flag);
                if (units != null)
                    value = units.parse_value(value);
                String	format;
                if (scale >= 100)
                    format = "%6.2f";
                else if (scale >= 10)
                    format = "%6.1f";
                else
                    format = "%6.0f";
                return String.format(format, value);
            }
        }

        /* Take UI data and write into 'pyro' */
        public void write_value(AltosPyro pyro) {
            if (button.isChecked())
                pyro.flags |= flag;
            else
                pyro.flags &= ~flag;
            if ((flag & AltosPyro.pyro_state_value) != 0) {
                int ivalue = getMenuItem(auto_view, state_names);
                if (ivalue != AltosLib.MISSING) {
                    ivalue += AltosLib.ao_flight_boost;
                    pyro.set_value(flag, ivalue);
                }
            } else {
                AltosUnits units = AltosPyro.pyro_to_units(flag);
                try {
                    double value;
                    String str = view.getEditableText().toString();

                    if (units != null)
                        value = units.parse_locale(str);
                    else
                        value = AltosParse.parse_double_locale(str);
                    pyro.set_value(flag, value);
                } catch (ParseException e) {
                }
            }
        }

        /* Take pyro data and write into ui */
        public void read_value(AltosPyro pyro) {
            button.setChecked((pyro.flags & flag) != 0);
            if (auto_view != null)
                auto_view.setText(value(pyro), false);
            else
                view.setText(value(pyro));
            view.setEnabled((pyro.flags & flag) != 0);
        }

        /* Label the value including units */
        public void set_label() {
            button.setText(AltosPyro.pyro_to_name(flag));
        }

        /* Label the value including units */
        public void clear_value() {
            button.setChecked(false);
            view.setText("");
        }

        private void init(CompoundButton button, EditText view, int flag) {
            this.button = button;
            this.view = view;
            this.flag = flag;

            if ((flag & AltosPyro.pyro_state_value) != 0)
                setMenu(auto_view, state_names);

            button.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton button, boolean isChecked) {
                        view.setEnabled(isChecked);
                    }
                });
        }

        PyroConfig(CompoundButton button, EditText edit_text, int flag) {
            init(button, edit_text, flag);
        }

        PyroConfig(CompoundButton button, AutoCompleteTextView view, int flag) {
            this.auto_view = view;
            init(button, view, flag);
        }
    }

    PyroConfig[] pyro_configs;

    private void make_pyro_configs() {
        PyroConfig[] _pyro_configs = {
            new PyroConfig(binding.pyroAccelLessEnable, binding.pyroAccelLess, AltosPyro.pyro_accel_less),
            new PyroConfig(binding.pyroAccelGreaterEnable, binding.pyroAccelGreater, AltosPyro.pyro_accel_greater),

            new PyroConfig(binding.pyroSpeedLessEnable, binding.pyroSpeedLess, AltosPyro.pyro_speed_less),
            new PyroConfig(binding.pyroSpeedGreaterEnable, binding.pyroSpeedGreater, AltosPyro.pyro_speed_greater),

            new PyroConfig(binding.pyroHeightLessEnable, binding.pyroHeightLess, AltosPyro.pyro_height_less),
            new PyroConfig(binding.pyroHeightGreaterEnable, binding.pyroHeightGreater, AltosPyro.pyro_height_greater),

            new PyroConfig(binding.pyroOrientLessEnable, binding.pyroOrientLess, AltosPyro.pyro_orient_less),
            new PyroConfig(binding.pyroOrientGreaterEnable, binding.pyroOrientGreater, AltosPyro.pyro_orient_greater),

            new PyroConfig(binding.pyroTimeLessEnable, binding.pyroTimeLess, AltosPyro.pyro_time_less),
            new PyroConfig(binding.pyroTimeGreaterEnable, binding.pyroTimeGreater, AltosPyro.pyro_time_greater),

            new PyroConfig(binding.pyroStateLessEnable, binding.pyroStateLess, AltosPyro.pyro_state_less),
            new PyroConfig(binding.pyroStateGreaterOrEqualEnable, binding.pyroStateGreaterOrEqual, AltosPyro.pyro_state_greater_or_equal),

            new PyroConfig(binding.pyroDelayEnable, binding.pyroDelay, AltosPyro.pyro_delay),

            new PyroConfig(binding.pyroMotorEnable, binding.pyroMotor, AltosPyro.pyro_after_motor),
        };
        pyro_configs = _pyro_configs;
    }

    static String[] make_state_names() {
        String[] state_names = new String[AltosLib.ao_flight_landed - AltosLib.ao_flight_boost + 1];
        for (int state = AltosLib.ao_flight_boost; state <= AltosLib.ao_flight_landed; state++)
            state_names[state - AltosLib.ao_flight_boost] = AltosLib.state_name_capital(state);
        return state_names;
    }

    static String[] state_names = make_state_names();

    // The Handler that gets information back from the Telemetry Service
    static class IncomingHandler extends Handler {
        private final ConfigureDeviceActivity config_activity;
        IncomingHandler(ConfigureDeviceActivity ca) {
            config_activity = ca;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MainActivity.MSG_CONFIG_DATA:
                AltosDebug.debug("MSG_CONFIG_DATA");
                AltosConfigDataRemote config_data = (AltosConfigDataRemote) msg.obj;
                config_activity.recv_config_data(config_data);
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

    private void group_visible(Group view, boolean visible) {
        if (!visible)
            view.setVisibility(View.GONE);
        else
            view.setVisibility(View.VISIBLE);
    }

    private void group_visible(Group view, int value) {
        group_visible(view, value != AltosLib.MISSING);
    }

    private void group_visible(Group view, double value) {
        group_visible(view, value != AltosLib.MISSING);
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
        return AltosLib.pad_orientation_values(has_radio(config_data.product));
    }

    private void update_config_data() {
        if (binding != null && config_data != null) {
            try {
                config_data.get_values(this);
            } catch (AltosConfigDataException cde) {
            }
        }
    }

    private boolean get_pyro_flag(SwitchCompat sw) {
        return sw.isChecked();
    }

    private int get_pyro_state(AltosPyro pyro, SwitchCompat sw, AutoCompleteTextView view, int flag) {
        int value = AltosLib.MISSING;
        if (get_pyro_flag(sw)) {
            pyro.flags |= flag;
            value = getMenuItem(view, state_names);
            if (value != AltosLib.MISSING)
                value += AltosLib.ao_flight_boost;
        }
        return value;
    }

    private void read_pyro() {
        if (binding != null && pyros != null && pyro_channel < pyros.length)
        {
            AltosPyro pyro = pyros[pyro_channel];
            for (PyroConfig pyro_config : pyro_configs)
                pyro_config.read_value(pyro);
        }
    }

    private void write_pyro() {
        if (binding != null && pyros != null && pyro_channel < pyros.length)
        {
            AltosPyro pyro = new AltosPyro(pyro_channel);
            for (PyroConfig pyro_config : pyro_configs)
                pyro_config.write_value(pyro);

            pyros[pyro_channel] = pyro;
        }
    }

    private void set_pyro_labels() {
        for (PyroConfig pyro_config : pyro_configs)
            pyro_config.set_label();
    }

    private void clear_pyro_values() {
        for (PyroConfig pyro_config : pyro_configs)
            pyro_config.clear_value();
    }

    private void set_pyro_channel(int in_pyro_channel) {
        write_pyro();
        pyro_channel = in_pyro_channel;
        read_pyro();
    }

    private void recv_config_data(AltosConfigDataRemote in_config_data) {

        config_data = in_config_data;
        query_running = false;

        if (binding != null && config_data != null) {

            setTitle(String.format("%s S/N %d Version %s",
                                   config_data.product, config_data.serial, config_data.version));

            /* main altitude */
            binding.mainDeploy.setText(AltosConvert.height.say(config_data.main_deploy), false);
            group_visible(binding.mainDeployGroup, config_data.main_deploy);

            /* apogee delay */
            binding.apogeeDelay.setText(Integer.toString(config_data.apogee_delay), false);
            group_visible(binding.apogeeDelayGroup, config_data.apogee_delay);

            /* apogee lockout */
            binding.apogeeLockout.setText(Integer.toString(config_data.apogee_lockout), false);
            group_visible(binding.apogeeLockoutGroup, config_data.apogee_lockout);

            /* igniter mode */
            if (config_data.ignite_mode != AltosLib.MISSING)
                binding.igniterMode.setText(AltosLib.ignite_mode_values[config_data.ignite_mode], false);
            group_visible(binding.igniterModeGroup, config_data.ignite_mode);

            /* pad orientation */
            if (config_data.pad_orientation != AltosLib.MISSING)
                binding.padOrientation.setText(pad_orientation_values(config_data)[config_data.pad_orientation], false);
            group_visible(binding.padOrientationGroup, config_data.pad_orientation);

            /* beeper frequency */
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

            /* beeper units */
            if (config_data.report_feet != AltosLib.MISSING)
                binding.beeperUnits.setText(AltosLib.report_unit_values[config_data.report_feet], false);
            group_visible(binding.beeperUnitsGroup, config_data.report_feet);

            /* flight log size */
            if (config_data.flight_log_max != AltosLib.MISSING) {
                FlightLogSize[] max_values = flightLogMaxValues(config_data);
                String[] max_strings = new String[max_values.length];
                String log_size = null;
                int log_kb = config_data.flight_log_max;
                for (int i = 0; i < max_values.length; i++) {
                    max_strings[i] = max_values[i].toString();
                    if (max_values[i].size_kb == log_kb)
                        log_size = max_strings[i];
                }
                if (log_size == null)
                    log_size = Integer.toString(log_kb);
                setMenu(binding.flightLogSize, max_strings);
                binding.flightLogSize.setText(log_size, false);
            }
            group_visible(binding.flightLogSizeGroup,
                          config_data.flight_log_max != AltosLib.MISSING &&
                          config_data.flight_log_max_enabled());

            /* call sign */
            binding.call.setText(config_data.callsign);
            group_visible(binding.callGroup, config_data.callsign != null);

            /* frequency */
            double frequency = config_data.frequency();
            if (frequency != AltosLib.MISSING) {
                AltosFrequency naf = new AltosFrequency(frequency, String.format("%s serial %d",
                                                                                config_data.product,
                                                                                config_data.serial));
                AltosFrequency af = AltosPreferences.add_common_frequency(naf);
                binding.frequency.setText(af.toShortString(), false);
                if (af == naf)
                    setMenu(binding.frequency, frequencyValues());
            }
            group_visible(binding.frequencyGroup, frequency);

            /* radio enable */
            if (config_data.radio_enable != AltosLib.MISSING)
                binding.radioEnable.setChecked(config_data.radio_enable != 0);
            group_visible(binding.radioEnableGroup, config_data.radio_enable);

            /* radio 10mW */
            if (config_data.radio_10mw != AltosLib.MISSING)
                binding.radio10mw.setChecked(config_data.radio_10mw != 0);
            group_visible(binding.radio10mwGroup, config_data.radio_10mw);

            /* telemetry rate */
            if (config_data.telemetry_rate != AltosLib.MISSING)
                setMenuItem(binding.telemetryRate, R.array.telemetry_rate_values, config_data.telemetry_rate);
            group_visible(binding.telemetryRateGroup, config_data.telemetry_rate);

            /* AES key */
            if (config_data.aes_key != null) {
                AltosAesKey nak = new AltosAesKey(config_data.aes_key,
                                                  String.format("%s serial %d",
                                                                config_data.product,
                                                                config_data.serial));
                AltosAesKey ak = AltosPreferences.add_common_aes_key(nak);
                binding.aes.setText(ak.toShortString(), false);
                if (ak == nak)
                    setMenu(binding.aes, aesKeyValues());
            }
            group_visible(binding.aesGroup, config_data.aes_key != null);

            /* Pad box */
            if (config_data.pad_box != AltosLib.MISSING)
                binding.padBox.setText(Integer.toString(config_data.pad_box), false);
            group_visible(binding.padBoxGroup, config_data.pad_box);

            /* Pad idle */
            if (config_data.pad_idle != AltosLib.MISSING)
                binding.padIdle.setText(Integer.toString(config_data.pad_idle), false);
            group_visible(binding.padIdleGroup, config_data.pad_idle);

            /* APRS interval */
            if (config_data.aprs_interval != AltosLib.MISSING) {
                if (config_data.aprs_interval == 0)
                    setMenuItem(binding.aprsInterval, R.array.aprs_interval_values, config_data.aprs_interval);
                else
                    binding.aprsInterval.setText(Integer.toString(config_data.aprs_interval), false);
            }
            group_visible(binding.aprsIntervalGroup, config_data.aprs_interval);

            /* APRS offset */
            if (config_data.aprs_offset != AltosLib.MISSING)
                binding.aprsOffset.setText(Integer.toString(config_data.aprs_offset), false);
            group_visible(binding.aprsOffsetGroup, config_data.aprs_offset);

            /* APRS SSID */
            if (config_data.aprs_ssid != AltosLib.MISSING)
                binding.aprsSsid.setText(Integer.toString(config_data.aprs_ssid), false);
            group_visible(binding.aprsSsidGroup, config_data.aprs_ssid);

            /* APRS format */
            if (config_data.aprs_format != AltosLib.MISSING)
                setMenuItem(binding.aprsFormat, R.array.aprs_format_values, config_data.aprs_format);
            group_visible(binding.aprsFormatGroup, config_data.aprs_format);

            /* Tracker Motion */
            if (config_data.tracker_motion != AltosLib.MISSING)
                binding.trackerMotion.setText(AltosConvert.height.say(config_data.tracker_motion), false);
            group_visible(binding.trackerMotionGroup, config_data.tracker_motion);

            if (config_data.tracker_interval != AltosLib.MISSING)
                binding.trackerInterval.setText(Integer.toString(config_data.tracker_interval), false);
            group_visible(binding.trackerIntervalGroup, config_data.tracker_interval);

            /* accel cal + */
            binding.accelCalPlus.setText(Integer.toString(config_data.accel_cal_plus));
            group_visible(binding.accelCalPlusGroup, config_data.accel_cal_plus);

            /* accel cal - */
            binding.accelCalMinus.setText(Integer.toString(config_data.accel_cal_minus));
            group_visible(binding.accelCalMinusGroup, config_data.accel_cal_minus);

            /* pyro firing time */
            binding.pyroFiringTime.setText(Double.toString(config_data.pyro_firing_time), false);
            group_visible(binding.pyroFiringTimeGroup, config_data.pyro_firing_time);

            /* pyro channel */
            boolean pyro_visible = config_data.npyro != AltosLib.MISSING && config_data.npyro > 0;
            if (pyro_visible) {
                pyro_channel_values = new String[config_data.npyro];
                pyros = new AltosPyro[config_data.npyro];
                for (int i = 0; i < config_data.npyro; i++) {
                    pyro_channel_values[i] = String.format("Channel %c", 'A' + i);
                    pyros[i] = config_data.pyros[i];
                }
                setMenu(binding.pyroChannel, pyro_channel_values);
                if (pyro_channel == AltosLib.MISSING || pyro_channel >= config_data.npyro)
                    pyro_channel = 0;
                binding.pyroChannel.setText(pyro_channel_values[pyro_channel], false);
                read_pyro();
            }
            for (Group group : pyroGroups)
                group.setVisibility(pyro_visible ? View.VISIBLE : View.GONE);
        }
    }

    private void query_data() {
        if (service == null || query_running)
            return;
        query_running = true;
        try {
            Message msg = Message.obtain(null, TelemetryService.MSG_GET_CONFIG_DATA);
            msg.replyTo = messenger;
            msg.obj = (Boolean) true;
            service.send(msg);
        } catch (RemoteException re) {
            AltosDebug.debug("config_data query thread failed");
            query_running = false;
        }
    }

    private void save_data(AltosConfigData new_data) {
        if (service == null)
            return;
        write_pyro();
        try {
            Message msg = Message.obtain(null, TelemetryService.MSG_SET_CONFIG_DATA);
            msg.replyTo = messenger;
            msg.obj = new_data;
            service.send(msg);
            reset();
        } catch (RemoteException re) {
            AltosDebug.debug("save_data thread failed");
        }
    }

    private void setMenu(AutoCompleteTextView view, String[] values) {
        ArrayAdapter adapter = new ArrayAdapter(this, R.layout.dropdown_item, values);
        view.setAdapter(adapter);
    }

    private void setMenu(AutoCompleteTextView view, int arrayId) {
        String[] values = getResources().getStringArray(arrayId);
        setMenu(view, values);
    }

    private int getMenuItem(AutoCompleteTextView view, String[] values) {
        String text = view.getEditableText().toString();
        for (int i = 0; i < values.length; i++)
            if (text.equals(values[i]))
                return i;
        return AltosLib.MISSING;
    }

    private int getMenuItem(AutoCompleteTextView view, int arrayId) {
            return getMenuItem(view, getResources().getStringArray(arrayId));
    }

    private void setMenuItem(AutoCompleteTextView view, int arrayId, int item) {
        String[] values = getResources().getStringArray(arrayId);
        view.setText(values[item], false);
    }

    public FlightLogSize[] flightLogMaxValues(AltosConfigData config_data) {
        int space_kb = config_data.log_space() >> 10;
        if (space_kb == 0)
            return null;

        ArrayList<FlightLogSize> maxValues = new ArrayList<FlightLogSize>();
        int erase_kb = config_data.storage_erase_unit >> 10;
        for (int i = 8; i >= 1; i--) {
            int	size = space_kb / i;
            if (erase_kb != 0)
                size &= ~(erase_kb - 1);
            maxValues.add(new FlightLogSize(size, i));
        }
        return maxValues.toArray(new FlightLogSize[0]);
    }

    public String[] frequencyValues() {
        AltosFrequency[] frequencies = AltosPreferences.common_frequencies();
        String[] frequencyValues = new String[frequencies.length];
        for (int i = 0; i < frequencies.length; i++)
            frequencyValues[i] = frequencies[i].toString();
        return frequencyValues;
    }

    public String[] aesKeyValues() {
        AltosAesKey[] aes_keys = AltosPreferences.common_aes_keys();
        String[] aesKeyValues = new String[aes_keys.length];
        for (int i = 0; i < aes_keys.length; i++)
            aesKeyValues[i] = aes_keys[i].toShortString();
        return aesKeyValues;
    }

    private void
    set_units(boolean imperial_units) {

        if (binding == null)
            return;

        if (imperial_units) {
            setMenu(binding.mainDeploy, R.array.main_deploy_values_ft);
            setMenu(binding.trackerMotion, R.array.tracker_motion_values_ft);
        } else {
            setMenu(binding.mainDeploy, R.array.main_deploy_values_m);
            setMenu(binding.trackerMotion, R.array.tracker_motion_values_m);
        }

        binding.mainDeployLabel.setText(String.format("%s(%s)",
                                                   getResources().getString(R.string.main_deploy),
                                                   AltosConvert.height.parse_units()));
        binding.trackerMotionLabel.setText(String.format("%s(%s)",
                                                         getResources().getString(R.string.tracker_motion),
                                                         AltosConvert.height.parse_units()));
        set_pyro_labels();
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

    TextView[] resetTextViews;
    SwitchCompat[] resetSwitches;
    Group[] pyroGroups;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ConfigureDeviceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ActivityLayouts.applyEdgeToEdge(this, R.id.configure_device);
        make_pyro_configs();

        TextView[] _resetTextViews = {
            binding.mainDeploy, binding.apogeeDelay, binding.apogeeLockout,
            binding.igniterMode, binding.padOrientation, binding.beeperFrequency,
            binding.beeperUnits, binding.flightLogSize, binding.call,
            binding.frequency, binding.telemetryRate, binding.aes,
            binding.padBox, binding.padIdle, binding.aprsInterval,
            binding.aprsOffset, binding.aprsSsid, binding.aprsFormat,
            binding.trackerMotion, binding.trackerInterval, binding.accelCalPlus,
            binding.accelCalMinus,
        };

        resetTextViews = _resetTextViews;
        SwitchCompat[] _resetSwitches = {
            binding.radioEnable, binding.radio10mw,
            binding.pyroStateLessEnable, binding.pyroStateGreaterOrEqualEnable
        };
        resetSwitches = _resetSwitches;

        Group[] _pyroGroups = {
            binding.pyroAccelGreaterGroup,
            binding.pyroAccelLessGroup,
            binding.pyroChannelGroup,
            binding.pyroDelayGroup,
            binding.pyroFiringTimeGroup,
            binding.pyroHeightGreaterGroup,
            binding.pyroHeightLessGroup,
            binding.pyroOrientGreaterGroup,
            binding.pyroOrientLessGroup,
            binding.pyroSpeedGreaterGroup,
            binding.pyroSpeedLessGroup,
            binding.pyroStateGreaterOrEqualGroup,
            binding.pyroStateLessGroup,
            binding.pyroTimeGreaterGroup,
            binding.pyroTimeLessGroup,
            binding.pyroMotorGroup,
        };
        pyroGroups = _pyroGroups;

        // Set result CANCELED incase the user backs out
        setResult(Activity.RESULT_CANCELED);

        setMenu(binding.apogeeDelay, R.array.apogee_delay_values);
        setMenu(binding.apogeeLockout, R.array.apogee_lockout_values);
        setMenu(binding.igniterMode, AltosLib.ignite_mode_values);
        setMenu(binding.padOrientation, AltosLib.pad_orientation_values_radio);
        setMenu(binding.beeperFrequency, R.array.beeper_frequency_values);
        setMenu(binding.beeperUnits, R.array.beeper_units_values);
        setMenu(binding.frequency, frequencyValues());
        setMenu(binding.telemetryRate, R.array.telemetry_rate_values);
        setMenu(binding.aes, aesKeyValues());
        setMenu(binding.padBox, R.array.pad_box_values);
        setMenu(binding.padIdle, R.array.pad_idle_values);
        setMenu(binding.aprsInterval, R.array.aprs_interval_values);
        setMenu(binding.aprsOffset, R.array.aprs_offset_values);
        setMenu(binding.aprsSsid, R.array.aprs_ssid_values);
        setMenu(binding.aprsFormat, R.array.aprs_format_values);
        setMenu(binding.trackerInterval, R.array.tracker_interval_values);
        setMenu(binding.pyroFiringTime, R.array.pyro_firing_time_values);

        binding.pyroChannel.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                    set_pyro_channel(pos);
                }
            });

        set_units(AltosPreferences.imperial_units());

        binding.save.setOnClickListener(v -> save());
        binding.reset.setOnClickListener(v -> reset());
        binding.close.setOnClickListener(v -> close());
    }

    void save() {
        update_config_data();
        save_data(config_data);
    }

    void reset() {
        for (TextView v : resetTextViews)
            v.setText("");
        for (SwitchCompat v : resetSwitches)
            v.setChecked(false);
        clear_pyro_values();
        query_data();
    }

    void close() {
        Intent intent = new Intent();
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    /* AltosConfigValues interface */

    private int parse_int(String name, String s, boolean split) throws AltosConfigDataException {
        String v = s;
        if (split)
            v = s.split("\\s+")[0];
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ne) {
            throw new AltosConfigDataException("Invalid %s \"%s\"", name, s);
        }
    }

    private double parse_double(String name, String s, boolean split) throws AltosConfigDataException {
        String v = s;
        if (split)
            v = s.split("\\s+")[0];
        try {
            return AltosParse.parse_double_locale(v);
        } catch (Exception e) {
            throw new AltosConfigDataException("Invalid %s \"%s\"", name, s);
        }
    }

    public int main_deploy() throws AltosConfigDataException {
        if (config_data.main_deploy == AltosLib.MISSING)
            return AltosLib.MISSING;
        String str = "<missing>";
        try {
            str = binding.mainDeploy.getEditableText().toString();
            return (int) AltosConvert.height.parse_locale(str);
        } catch (Exception e) {
            throw new AltosConfigDataException("Invalid height %s", str);
        }
    }

    public int apogee_delay() throws AltosConfigDataException{
        if (config_data.apogee_delay == AltosLib.MISSING)
            return AltosLib.MISSING;
        return parse_int("apogee delay",
                         binding.apogeeDelay.getEditableText().toString(),
                         false);
    }

    public int apogee_lockout() throws AltosConfigDataException{
        if (config_data.apogee_lockout == AltosLib.MISSING)
            return AltosLib.MISSING;
        return parse_int("apogee lockout",
                         binding.apogeeLockout.getEditableText().toString(),
                         false);
    }

    public double radio_frequency() throws AltosConfigDataException {
        if (config_data.radio_frequency == AltosLib.MISSING)
            return AltosLib.MISSING;
        return parse_double("Radio frequency",
                            binding.frequency.getEditableText().toString(),
                            true);
    }

    public int radio_enable(){
        if (config_data.radio_enable == AltosLib.MISSING)
            return AltosLib.MISSING;
        return binding.radioEnable.isChecked() ? 1 : 0;
    }

    public String callsign() {
        if (config_data.callsign == null)
            return null;
        return binding.call.getEditableText().toString();
    }

    public int telemetry_rate() throws AltosConfigDataException{
        if (config_data.telemetry_rate == AltosLib.MISSING)
            return AltosLib.MISSING;
        return getMenuItem(binding.telemetryRate, R.array.telemetry_rate_values);
    }

    public String aes_key() {
        if (config_data.aes_key == null)
            return null;
        String[] strs = binding.aes.getEditableText().toString().split("\\s+");
        AltosAesKey aes_key = new AltosAesKey(strs[0], strs[1]);
        if (AltosPreferences.add_common_aes_key(aes_key, true) == aes_key) {
            setMenu(binding.aes, aesKeyValues());
        }
        return strs[0];
    }

    public int pad_box() throws AltosConfigDataException {
        if (config_data.pad_box == AltosLib.MISSING)
            return AltosLib.MISSING;
        return parse_int("Pad box",
                         binding.padBox.getEditableText().toString(),
                         false);
    }

    public int pad_idle() throws AltosConfigDataException {
        if (config_data.pad_idle == AltosLib.MISSING)
            return AltosLib.MISSING;
        return parse_int("Pad idle timeout",
                         binding.padIdle.getEditableText().toString(),
                         false);
    }

    public int flight_log_max() throws AltosConfigDataException {
        if (config_data.flight_log_max == AltosLib.MISSING)
            return AltosLib.MISSING;
        return parse_int("Flight log size",
                         binding.flightLogSize.getEditableText().toString(),
                         true);
    }

    public int ignite_mode() {
        if (config_data.ignite_mode == AltosLib.MISSING)
            return AltosLib.MISSING;
        return getMenuItem(binding.igniterMode, R.array.igniter_mode_values);
    }

    public int pad_orientation() {
        if (config_data.pad_orientation == AltosLib.MISSING)
            return AltosLib.MISSING;
        return getMenuItem(binding.padOrientation, R.array.pad_orientation_values);
    }

    public int accel_cal_plus() throws AltosConfigDataException {
        if (config_data.accel_cal_plus == AltosLib.MISSING)
            return AltosLib.MISSING;
        return parse_int("Accel Cal +",
                         binding.accelCalPlus.getEditableText().toString(),
                         false);
    }

    public int accel_cal_minus() throws AltosConfigDataException {
        if (config_data.accel_cal_minus == AltosLib.MISSING)
            return AltosLib.MISSING;
        return parse_int("Accel Cal -",
                         binding.accelCalMinus.getEditableText().toString(),
                         false);
    }

    public int aprs_interval() throws AltosConfigDataException {
        if (config_data.aprs_interval == AltosLib.MISSING)
            return AltosLib.MISSING;
        String str = "<missing>";
        str = binding.aprsInterval.getEditableText().toString();
        if (str.toLowerCase().startsWith("disabled"))
            return 0;
        return parse_int("APRS Interval", str, false);
    }

    public int aprs_ssid() throws AltosConfigDataException {
        if (config_data.aprs_ssid == AltosLib.MISSING)
            return AltosLib.MISSING;
        return parse_int("APRS SSID",
                         binding.aprsSsid.getEditableText().toString(),
                         false);
    }

    public int aprs_format() throws AltosConfigDataException {
        if (config_data.aprs_format == AltosLib.MISSING)
            return AltosLib.MISSING;
        return getMenuItem(binding.aprsFormat, R.array.aprs_format_values);
    }

    public int aprs_offset() throws AltosConfigDataException {
        if (config_data.aprs_offset == AltosLib.MISSING)
            return AltosLib.MISSING;
        return parse_int("APRS Offset",
                         binding.aprsOffset.getEditableText().toString(),
                         false);
    }

    public int beep() throws AltosConfigDataException {
        if (config_data.beep == AltosLib.MISSING)
            return AltosLib.MISSING;
        String value = binding.beeperFrequency.getEditableText().toString();
        return AltosConvert.beep_freq_to_value(parse_int("Beeper Frequency",
                                                         value,
                                                         false));
    }

    public AltosPyro[] pyros() throws AltosConfigDataException {
        return pyros;
    }

    public double pyro_firing_time() throws AltosConfigDataException {
        if (config_data.pyro_firing_time == AltosLib.MISSING)
            return AltosLib.MISSING;
        return parse_double("Pyro channel firing time",
                            binding.pyroFiringTime.getEditableText().toString(),
                            false);
    }

    public int tracker_motion() throws AltosConfigDataException {
        if (config_data.tracker_motion == AltosLib.MISSING)
            return AltosLib.MISSING;
        return parse_int("APRS Offset",
                         binding.trackerMotion.getEditableText().toString(),
                         false);
    }

    public int tracker_interval() throws AltosConfigDataException {
        if (config_data.tracker_interval == AltosLib.MISSING)
            return AltosLib.MISSING;
        return parse_int("APRS Offset",
                         binding.trackerInterval.getEditableText().toString(),
                         false);
    }

    public int radio_10mw() throws AltosConfigDataException {
        if (config_data.radio_10mw == AltosLib.MISSING)
            return AltosLib.MISSING;
        return AltosLib.MISSING;
    }

    public boolean has_radio() {
        return config_data.has_radio();
    }

    public int report_feet() throws AltosConfigDataException {
        if (config_data.report_feet == AltosLib.MISSING)
            return AltosLib.MISSING;
        return getMenuItem(binding.beeperUnits, R.array.beeper_units_values);
    }

    public int gps_receiver() throws AltosConfigDataException {
        if (config_data.gps_receiver == AltosLib.MISSING)
            return AltosLib.MISSING;
        return AltosLib.MISSING;
    }

    /*
     * We don't use this part of the interface, preferring to get the
     * whole configuration in one object
     */
    public void set_product(String product) {
    }

    public void set_version(String version) {
    }

    public void set_serial(int serial) {
    }

    public void set_altitude_32(int altitude_32) {
    }

    public void set_main_deploy(int new_main_deploy) {
    }

    public void set_apogee_delay(int new_apogee_delay) {
    }

    public void set_apogee_lockout(int new_apogee_lockout) {
    }

    public void set_radio_frequency(double new_radio_frequency) {
    }

    public void set_radio_calibration(int new_radio_calibration) {
    }

    public void set_radio_enable(int new_radio_enable) {
    }

    public void set_callsign(String new_callsign) {
    }

    public void set_telemetry_rate(int new_telemetry_rate) {
    }

    public void set_aes_key(String new_aes_key) {
    }

    public void set_pad_box(int new_pad_box) {
    }

    public void set_pad_idle(int new_pad_idle) {
    }

    public void set_flight_log_max(int new_flight_log_max) {
    }

    public void set_flight_log_max_enabled(boolean enable) {
    }

    public void set_flight_log_max_limit(int flight_log_max_limit, int storage_erase_unit) {
    }

    public void set_ignite_mode(int new_ignite_mode) {
    }

    public void set_pad_orientation(int new_pad_orientation) {
    }

    public void set_accel_cal(int accel_cal_plus, int accel_cal_minus) {
    }

    public void set_dirty() {
    }

    public void set_clean() {
    }

    public void set_pyros(AltosPyro[] new_pyros) {
    }

    public void set_pyro_firing_time(double new_pyro_firing_time) {
    }

    public void set_aprs_interval(int new_aprs_interval) {
    }

    public void set_aprs_ssid(int new_aprs_ssid) {
    }

    public void set_aprs_format(int new_aprs_format) {
    }

    public void set_aprs_offset(int new_aprs_offset) {
    }

    public void set_beep(int new_beep) {
    }

    public void set_tracker_motion(int tracker_motion) {
    }

    public void set_tracker_interval(int tracker_motion) {
    }

    public void set_radio_10mw(int radio_10mw) {
    }

    public void set_report_feet(int report_feet) {
    }

    public void set_gps_receiver(int gps_receiver) {
    }
}
