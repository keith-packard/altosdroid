/*
 * Copyright © 2016 Keith Packard <keithp@keithp.com>
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
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import org.altusmetrum.altosdroid.databinding.IdleModeBinding;
import org.altusmetrum.altoslib_14.*;

public class IdleModeActivity extends AppCompatActivity {

    private IdleModeBinding binding;

    private double frequency;
    private AltosFrequency[] frequencies;

    public static final String EXTRA_IDLE_MODE = "idle_mode";
    public static final String EXTRA_IDLE_RESULT = "idle_result";
    public static final String EXTRA_IDLE_FREQUENCY = "idle_frequency";

    public static final int IDLE_MODE_CONNECT = 1;
    public static final int IDLE_MODE_REBOOT = 2;
    public static final int IDLE_MODE_IGNITERS = 3;
    public static final int IDLE_MODE_DISCONNECT = 4;
    public static final int IDLE_MODE_CONFIGURE = 5;

    private void done(int type) {
        AltosPreferences.set_callsign(callsign());
        Intent intent = new Intent();
        intent.putExtra(EXTRA_IDLE_RESULT, type);
        intent.putExtra(EXTRA_IDLE_FREQUENCY, frequency);
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    private String callsign() {
        return binding.setCallsign.getEditableText().toString();
    }

    public void connect_idle() {
        done(IDLE_MODE_CONNECT);
    }

    public void disconnect_idle() {
        AltosDebug.debug("Disconnect idle button pressed");
        done(IDLE_MODE_DISCONNECT);
    }

    public void reboot_idle() {
        done(IDLE_MODE_REBOOT);
    }

    public void igniters_idle() {
        done(IDLE_MODE_IGNITERS);
    }

    public void configure_idle() {
        done(IDLE_MODE_CONFIGURE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        /* Force window to full width instead of wrap_content */
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(getWindow().getAttributes());
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        getWindow().setAttributes(lp);
    }

    int frequency_pos(double frequency, String description) {
        for (int i = 0; i < frequencies.length; i++) {
            if (frequencies[i].frequency == frequency)
                return i;
        }
        AltosFrequency[] new_frequencies = new AltosFrequency[frequencies.length + 1];
        for (int i = 0; i < frequencies.length; i++)
            new_frequencies[i] = frequencies[i];
        int pos = frequencies.length;
        new_frequencies[pos] = new AltosFrequency(frequency, description);
        frequencies = new_frequencies;
        return pos;
    }

    private void add_frequencies(Spinner spinner, AltosFrequency[] frequencies, int def) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.spinner);

        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);

        for (int i = 0; i < frequencies.length; i++)
            adapter.add(frequencies[i].toShortString());

        spinner.setAdapter(adapter);
        if (def >= 0)
            spinner.setSelection(def);
    }

    void select_frequency(int pos) {
        frequency = frequencies[pos].frequency;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // setTheme(MainActivity.dialog_themes[AltosDroidPreferences.font_size()]);
        super.onCreate(savedInstanceState);

        binding = IdleModeBinding.inflate(getLayoutInflater());

        // Setup the window
        setContentView(binding.getRoot());

        frequency = getIntent().getDoubleExtra(MainActivity.EXTRA_FREQUENCY, 0.0);

        if (frequency == AltosLib.MISSING) {
            binding.setCallsignGroup.setVisibility(View.GONE);
            binding.frequencyGroup.setVisibility(View.GONE);
        } else {
            binding.setCallsignGroup.setVisibility(View.VISIBLE);
            binding.frequencyGroup.setVisibility(View.VISIBLE);
            binding.setCallsign.setText(new StringBuffer(AltosPreferences.callsign()));

            frequencies = AltosPreferences.common_frequencies();
            int pos = frequency_pos(frequency, "current");

            add_frequencies(binding.frequency, frequencies, pos);

            binding.frequency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        select_frequency(pos);
                    }
                    public void onNothingSelected(AdapterView<?> parent) {
                    }
                });
        }

        binding.connectIdle.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    connect_idle();
                }
            });
        binding.disconnectIdle.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    disconnect_idle();
                }
            });

        boolean	idle_mode = getIntent().getBooleanExtra(MainActivity.EXTRA_IDLE_MODE, false);

        if (idle_mode)
            binding.connectIdle.setVisibility(View.GONE);
        else
            binding.disconnectIdle.setVisibility(View.GONE);

        binding.rebootIdle.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    reboot_idle();
                }
            });

        binding.ignitersIdle.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    igniters_idle();
                }
            });

        binding.configureIdle.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    configure_idle();
                }
            });

        // Set result CANCELED incase the user backs out
        setResult(Activity.RESULT_CANCELED);
    }
}
