/*
 * Copyright © 2012 Mike Beattie <mike@ethernal.org>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
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

import java.util.*;
import org.altusmetrum.altoslib_14.*;

public class TelemetryState implements AltosDroidSelectedSerialListener {
    public static final int CONNECT_DISCONNECTED = 1;
    public static final int CONNECT_CONNECTING   = 2;
    public static final int CONNECT_CONNECTED    = 3;

    public static final int VIEW_UNKNOWN         = 0;
    public static final int VIEW_PAD             = 1;
    public static final int VIEW_FLIGHT          = 2;
    public static final int VIEW_RECOVER         = 3;
    public static final int VIEW_MAP             = 4;

    public int                  view;

    public int		        connect;
    public DeviceAddress	address;
    public AltosConfigData	config;
    public int		        crc_errors;
    public double		receiver_battery;
    public double		frequency;
    public int		        telemetry_rate;

    private boolean             prev_locked;
    private int                 prev_state;

    public boolean		idle_mode;
    public boolean		quiet;

    public HashMap<Integer,AltosState>	states;

    public int                  selected_serial;
    public long                 selected_serial_time;
    public int                  shown_serial;
    public AltosState           state;

    public int		        latest_serial;
    public long		        latest_received_time;

    public void put(int serial, AltosState state) {
        long received_time = state.received_time;
        if (received_time > latest_received_time || latest_serial == 0) {
            latest_serial = serial;
            latest_received_time = received_time;
        }
        states.put(serial, state);
        quiet = false;
        update_state();
    }

    public AltosState get(int serial) {
        if (states.containsKey(serial))
            return states.get(serial);
        return null;
    }

    public void remove(int serial) {
        states.remove(serial);
        update_state();
    }

    public void set_receiver_battery(double in_receiver_battery) {
        receiver_battery = in_receiver_battery;
    }

    public void set_frequency(double in_frequency) {
        frequency = in_frequency;
    }

    public void set_telemetry_rate(int in_rate) {
        telemetry_rate = in_rate;
    }

    public void set_crc_errors(int in_crc_errors) {
        crc_errors = in_crc_errors;
    }

    public void set_connect(int in_connect, DeviceAddress in_address) {
        connect = in_connect;
        address = in_address;
    }

    public void set_config(AltosConfigData in_config) {
        config = in_config;
    }

    public void set_view(int in_view) {
        view = in_view;
        update_state();
    }

    public void set_idle_mode(boolean in_idle) {
        idle_mode = in_idle;
    }

    public Set<Integer> keySet() {
        return states.keySet();
    }

    public Collection<AltosState> values() {
        return states.values();
    }

    public boolean containsKey(int serial) {
        return states.containsKey(serial);
    }

    public void selected_serial_changed(int serial, long time) {
        selected_serial = serial;
        selected_serial_time = time;
        update_state();
    }

    void auto_select_tracker() {
        int earliest_serial = AltosDroidPreferences.SELECT_AUTO;
        long earliest_time = 0;

        for (AltosState s : values()) {
            if (s.received_time != AltosLib.MISSING && s.cal_data() != null) {
                if (s.received_time >= selected_serial_time) {
                    if (earliest_serial == MainActivity.SELECT_AUTO || s.received_time < earliest_time) {
                        earliest_serial = s.cal_data().serial;
                        earliest_time = s.received_time;
                    }
                }
            }
        }

        if (earliest_serial != AltosDroidPreferences.SELECT_AUTO)
            AltosDroidPreferences.set_selected_serial(earliest_serial);
    }

    void update_state() {
        if (selected_serial == AltosDroidPreferences.SELECT_AUTO)
            auto_select_tracker();

        if (selected_serial != AltosDroidPreferences.SELECT_AUTO && get(selected_serial) == null)
            AltosDroidPreferences.set_selected_serial(AltosDroidPreferences.SELECT_AUTO);

        shown_serial = selected_serial;
        if (idle_mode)
            shown_serial = latest_serial;

        state = get(shown_serial);

        if (state != null) {
            // compute the next view to switch to
            if (state.state() == AltosLib.ao_flight_stateless) {
                boolean locked = false;

                if(state.gps != null)
                    locked = state.gps.locked;
                if (prev_locked != locked) {
                    if (locked) {
                        if (view == VIEW_PAD || view == VIEW_UNKNOWN)
                            view = VIEW_FLIGHT;
                    } else {
                        if (view == VIEW_FLIGHT || view == VIEW_UNKNOWN)
                            view = VIEW_PAD;
                    }
                    prev_locked = locked;
                }
            } else {
                if (prev_state != state.state()) {

                    switch (state.state()) {
                    case AltosLib.ao_flight_boost:
                    case AltosLib.ao_flight_fast:
                    case AltosLib.ao_flight_coast:
                    case AltosLib.ao_flight_drogue:
                    case AltosLib.ao_flight_main:
                        if (view == VIEW_PAD || view == VIEW_UNKNOWN)
                            view = VIEW_FLIGHT;
                        break;
                    case AltosLib.ao_flight_landed:
                        if (view == VIEW_FLIGHT || view == VIEW_UNKNOWN)
                            view = VIEW_FLIGHT;
                        break;
                    }
                    prev_state = state.state();
                }
            }
        }
    }

    public TelemetryState() {
        connect = CONNECT_DISCONNECTED;
        address = null;
        config = null;
        states = new HashMap<Integer,AltosState>();
        crc_errors = 0;
        view = VIEW_UNKNOWN;
        receiver_battery = AltosLib.MISSING;
        frequency = AltosPreferences.frequency(0);
        telemetry_rate = AltosPreferences.telemetry_rate(0);
        latest_serial = AltosPreferences.latest_state();
        AltosDroidPreferences.register_selected_serial_listener(this);
        selected_serial = AltosDroidPreferences.selected_serial();
        selected_serial_time = AltosDroidPreferences.selected_serial_time();
        quiet = false;
    }
}
