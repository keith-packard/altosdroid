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

    private void recv_config_data(AltosConfigDataRemote in_config_data) {

        config_data = in_config_data;
        query_running = false;

        if (binding != null && config_data != null) {

            setTitle(String.format("Configure %s %d", config_data.product, config_data.serial));

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

            if (config_data.report_feet != AltosLib.MISSING)
                binding.beeperUnits.setText(AltosLib.report_unit_values[config_data.report_feet], false);
            group_visible(binding.beeperUnitsGroup, config_data.beep);

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
                log_size = String.format("%d", log_kb);
            setMenu(binding.flightLogSize, max_strings);
            binding.flightLogSize.setText(log_size, false);
            group_visible(binding.flightLogSizeGroup, config_data.flight_log_max);

            binding.call.setText(config_data.callsign);

            double frequency = config_data.frequency();
            if (frequency != AltosLib.MISSING) {
                AltosFrequency naf = new AltosFrequency(frequency, String.format("%s serial %d",
                                                                                config_data.product,
                                                                                config_data.serial));
                AltosFrequency af = AltosPreferences.add_common_frequency(naf);
                binding.frequency.setText(af.toShortString(), false);
            }
            group_visible(binding.frequencyGroup, frequency);

            if (config_data.radio_enable != AltosLib.MISSING)
                binding.radioEnable.setChecked(config_data.radio_enable != 0);
            group_visible(binding.radioEnableGroup, config_data.radio_enable);

            if (config_data.telemetry_rate != AltosLib.MISSING)
                setMenuItem(binding.telemetryRate, R.array.telemetry_rate_values, config_data.telemetry_rate);
            group_visible(binding.telemetryRateGroup, config_data.telemetry_rate);

            binding.accelCalPlus.setText(Integer.toString(config_data.accel_cal_plus));
            group_visible(binding.accelCalPlusGroup, config_data.accel_cal_plus);

            binding.accelCalMinus.setText(Integer.toString(config_data.accel_cal_minus));
            group_visible(binding.accelCalMinusGroup, config_data.accel_cal_minus);
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
        try {
            Message msg = Message.obtain(null, TelemetryService.MSG_SET_CONFIG_DATA);
            msg.replyTo = messenger;
            msg.obj = new_data;
            service.send(msg);
            query_data();
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

    private int getMenuItem(AutoCompleteTextView view, int arrayId) {
        String[] values = getResources().getStringArray(arrayId);
        String text = view.getEditableText().toString();
        for (int i = 0; i < values.length; i++)
            if (text.equals(values[i]))
                return i;
        return AltosLib.MISSING;
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
        setMenu(binding.telemetryRate, R.array.telemetry_rate_values);

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
        String str = "<missing>";
        try {
            str = binding.mainAlt.getEditableText().toString();
            return (int) AltosConvert.height.parse_locale(str);
        } catch (Exception e) {
            throw new AltosConfigDataException("Invalid height %s", str);
        }
    }

    public int apogee_delay() throws AltosConfigDataException{
        String str = "<missing>";
        try {
            str = binding.apogeeDelay.getEditableText().toString();
            return parse_int("apogee delay", str, false);
        } catch (Exception e) {
            throw new AltosConfigDataException("Invalid apogee delay %s", str);
        }
    }

    public int apogee_lockout() throws AltosConfigDataException{
        String str = "<missing>";
        try {
            str = binding.apogeeLockout.getEditableText().toString();
            return parse_int("apogee lockout", str, false);
        } catch (Exception e) {
            throw new AltosConfigDataException("Invalid apogee lockout %s", str);
        }
    }

    public double radio_frequency() throws AltosConfigDataException {
        if (binding.frequencyGroup.getVisibility() != View.VISIBLE)
            return AltosLib.MISSING;
        return parse_double("Radio frequency",
                            binding.frequency.getEditableText().toString(),
                            true);
    }

    public int radio_enable(){
        if (binding.radioEnableGroup.getVisibility() != View.VISIBLE)
            return AltosLib.MISSING;
        return binding.radioEnable.isChecked() ? 1 : 0;
    }

    public String callsign() {
        if (binding.callGroup.getVisibility() == View.VISIBLE)
            return binding.call.getEditableText().toString();
        return null;
    }

    public int telemetry_rate() throws AltosConfigDataException{
        if (binding.telemetryRateGroup.getVisibility() != View.VISIBLE)
            return AltosLib.MISSING;
        return getMenuItem(binding.telemetryRate, R.array.telemetry_rate_values);
    }

    public int flight_log_max() throws AltosConfigDataException{
        if (binding.flightLogSizeGroup.getVisibility() != View.VISIBLE)
            return AltosLib.MISSING;
        return parse_int("Flight log size",
                         binding.flightLogSize.getEditableText().toString(),
                         true);
    }

    public int ignite_mode(){
        if (binding.igniterModeGroup.getVisibility() != View.VISIBLE)
            return AltosLib.MISSING;
        return getMenuItem(binding.igniterMode, R.array.igniter_mode_values);
    }

    public int pad_orientation(){
        if (binding.padOrientationGroup.getVisibility() != View.VISIBLE)
            return AltosLib.MISSING;
        return getMenuItem(binding.padOrientation, R.array.pad_orientation_values);
    }

    public int accel_cal_plus(){
        if (binding.accelCalPlusGroup.getVisibility() != View.VISIBLE)
            return AltosLib.MISSING;
        try {
            return parse_int("Accel Cal +",
                             binding.accelCalPlus.getEditableText().toString(),
                             false);
        } catch (Exception e) {
            return AltosLib.MISSING;
        }
    }

    public int accel_cal_minus(){
        if (binding.accelCalMinusGroup.getVisibility() != View.VISIBLE)
            return AltosLib.MISSING;
        try {
            return parse_int("Accel Cal -",
                             binding.accelCalMinus.getEditableText().toString(),
                             false);
        } catch (Exception e) {
            return AltosLib.MISSING;
        }
    }

    public AltosPyro[] pyros() throws AltosConfigDataException{
        return null;
    }

    public double pyro_firing_time() throws AltosConfigDataException{
//        if (binding.pyroFiringTimeGroup.getVisibility() != View.VISIBLE)
//            return AltosLib.MISSING;
//        return parse_double("Pyro channel firing time",
//                            binding.frequency.getEditableText().toString(),
//                            true);
        return AltosLib.MISSING;
    }

    public int aprs_interval() throws AltosConfigDataException{
//        if (binding.aprsIntervalGroup.getVisibility() != View.VISIBLE)
            return AltosLib.MISSING;
//        try {
//            return parse_int("APRS Interval",
//                             binding.aprsInterval
//        return config_data.aprs_interval;
    }

    public int aprs_ssid() throws AltosConfigDataException{
        return AltosLib.MISSING;
    }

    public int aprs_format() throws AltosConfigDataException{
        return AltosLib.MISSING;
    }

    public int aprs_offset() throws AltosConfigDataException{
        return AltosLib.MISSING;
    }

    public int beep() throws AltosConfigDataException{
        if (binding.beeperFrequencyGroup.getVisibility() != View.VISIBLE)
            return AltosLib.MISSING;
        String value = binding.beeperFrequency.getEditableText().toString();
        return AltosConvert.beep_freq_to_value(parse_int("Beeper Frequency",
                                                         value,
                                                         false));
    }

    public int tracker_motion() throws AltosConfigDataException{
        return AltosLib.MISSING;
    }

    public int tracker_interval() throws AltosConfigDataException{
        return AltosLib.MISSING;
    }

    public int radio_10mw() throws AltosConfigDataException{
        return AltosLib.MISSING;
    }

    public boolean has_radio(){
        return true;
    }

    public int report_feet() throws AltosConfigDataException{
        if (binding.beeperUnits.getVisibility() != View.VISIBLE)
            return AltosLib.MISSING;
        return getMenuItem(binding.beeperUnits, R.array.beeper_units_values);
    }

    public int gps_receiver() throws AltosConfigDataException{
        return AltosLib.MISSING;
    }

    /*
     * We don't use this part of the interface, preferring to get the
     * whole configuration in one object
     */
    public void set_product(String product){
    }

    public void set_version(String version){
    }

    public void set_serial(int serial){
    }

    public void set_altitude_32(int altitude_32){
    }

    public void set_main_deploy(int new_main_deploy){
    }

    public void set_apogee_delay(int new_apogee_delay){
    }

    public void set_apogee_lockout(int new_apogee_lockout){
    }

    public void set_radio_frequency(double new_radio_frequency){
    }

    public void set_radio_calibration(int new_radio_calibration){
    }

    public void set_radio_enable(int new_radio_enable){
    }

    public void set_callsign(String new_callsign){
    }

    public void set_telemetry_rate(int new_telemetry_rate){
    }

    public void set_flight_log_max(int new_flight_log_max){
    }

    public void set_flight_log_max_enabled(boolean enable){
    }

    public void set_flight_log_max_limit(int flight_log_max_limit, int storage_erase_unit){
    }

    public void set_ignite_mode(int new_ignite_mode){
    }

    public void set_pad_orientation(int new_pad_orientation){
    }

    public void set_accel_cal(int accel_cal_plus, int accel_cal_minus){
    }

    public void set_dirty(){
    }

    public void set_clean(){
    }

    public void set_pyros(AltosPyro[] new_pyros){
    }

    public void set_pyro_firing_time(double new_pyro_firing_time){
    }

    public void set_aprs_interval(int new_aprs_interval){
    }

    public void set_aprs_ssid(int new_aprs_ssid){
    }

    public void set_aprs_format(int new_aprs_format){
    }

    public void set_aprs_offset(int new_aprs_offset){
    }

    public void set_beep(int new_beep){
    }

    public void set_tracker_motion(int tracker_motion){
    }

    public void set_tracker_interval(int tracker_motion){
    }

    public void set_radio_10mw(int radio_10mw){
    }

    public void set_report_feet(int report_feet){
    }

    public void set_gps_receiver(int gps_receiver){
    }
}
