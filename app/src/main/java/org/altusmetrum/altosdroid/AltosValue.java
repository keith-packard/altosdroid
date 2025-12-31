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

import android.content.res.Resources;
import android.location.Location;

import org.altusmetrum.altoslib_14.AltosGreatCircle;
import org.altusmetrum.altoslib_14.AltosLib;

import java.util.Locale;

public class AltosValue {
    public static String pos(double p, String pos, String neg) {
		String	h = pos;
		if (p == AltosLib.MISSING) {
            return "";
        }
		if (p < 0) {
			h = neg;
			p = -p;
		}
		int deg = (int) Math.floor(p);
		double min = (p - Math.floor(p)) * 60.0;
		return String.format(Locale.getDefault(), "%d° %7.4f\" %s", deg, min, h);
	}

	public static String number(String format, double value) {
		if (value == AltosLib.MISSING) {
            return "";
        }
		return String.format(Locale.getDefault(), format, value);
	}

	public static String integer(String format, int value) {
		if (value == AltosLib.MISSING) {
            return "";
        }
		return String.format(Locale.getDefault(), format, value);
	}

	static public String direction(AltosGreatCircle from_receiver, Location receiver_location, Resources resources) {
		if (from_receiver == null || receiver_location == null || !receiver_location.hasBearing())
			return null;

		float	bearing = receiver_location.getBearing();
		float	heading = (float) from_receiver.bearing - bearing;

		while (heading <= -180.0f)
			heading += 360.0f;
		while (heading > 180.0f)
			heading -= 360.0f;

		int iheading = (int) (heading + 0.5f);

		if (iheading == 0)
			return resources.getString(R.string.heading_ahead);
		else if (iheading < -179 || 179 < iheading)
			return resources.getString(R.string.heading_backwards);
		else if (iheading < 0)
			return String.format(Locale.getDefault(), "%s %d°", resources.getString(R.string.heading_left), -iheading);
		else
			return String.format(Locale.getDefault(), "%s %d°", resources.getString(R.string.heading_right), iheading);
	}
}
