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

import android.content.res.Resources;
import android.view.View;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;

import org.altusmetrum.altoslib_14.AltosLib;

public class AltosVoltMeter {
    public double voltage;
    public double good_voltage;
    View row;
    ImageView green;
    ImageView red;
    TextView value;
    GoNoGoLights lights;

    public AltosVoltMeter(double good_voltage, View row, ImageView red, ImageView green, TextView value, Resources resources) {
        this.voltage = AltosLib.MISSING;
        this.good_voltage = good_voltage;
        this.row = row;
        this.red = red;
        this.green = green;
        this.value = value;
        this.lights = new GoNoGoLights(red, green, resources);
    }

    public void set(double voltage) {
        this.voltage = voltage;
        if (voltage == AltosLib.MISSING) {
            row.setVisibility(View.GONE);
        } else {
            value.setText(String.format("%1.2f V", voltage));
            lights.set(voltage >= AltosLib.ao_igniter_good, voltage == AltosLib.MISSING);
            row.setVisibility(View.VISIBLE);
        }
    }
}
