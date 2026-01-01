/*
 * Copyright © 2025 Keith Packard <keithp@keithp.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package org.altusmetrum.altosdroid;

import android.location.Location;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import org.altusmetrum.altoslib_14.AltosGreatCircle;
import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosState;
import org.altusmetrum.altoslib_14.AltosUnits;

public abstract class AltosFragment extends Fragment {
    abstract public void show(TelemetryState telem_state, AltosState state, AltosGreatCircle from_receiver, Location receiver_location);
    public void set_value(TextView text_view,
                          AltosUnits units,
                          int width,
                          double value) {
		if (value == AltosLib.MISSING)
			text_view.setText("");
		else
			text_view.setText(units.show(width, value));
	}

	abstract public String name();
	abstract public int menuId();
}
