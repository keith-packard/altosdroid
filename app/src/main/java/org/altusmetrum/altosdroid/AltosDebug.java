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

import android.content.pm.ApplicationInfo;
import android.content.Context;
import android.os.Looper;
import android.util.Log;

public class AltosDebug {
    // Debugging
	static final String TAG = "AltosDroid";

	static boolean	D = true;

	static void init(Context context) {
		ApplicationInfo app_info = context.getApplicationInfo();

		if ((app_info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
			Log.d(TAG, "Enable debugging\n");
			D = true;
		} else {
			Log.d(TAG, "Disable debugging\n");
			D = true;
		}
	}

	static void info(String format, Object ... arguments) {
		Log.i(TAG, String.format(format, arguments));
	}

	static void debug(String format, Object ... arguments) {
		if (D)
			Log.d(TAG, String.format(format, arguments));
	}

	static void error(String format, Object ... arguments) {
		Log.e(TAG, String.format(format, arguments));
	}

	static void trace(String format, Object ... arguments) {
		error(format, arguments);
		for (StackTraceElement el : Thread.currentThread().getStackTrace())
			Log.e(TAG, "\t" + el.toString() + "\n");
	}

	static void check_ui(String format, Object ... arguments) {
		if (Looper.myLooper() == Looper.getMainLooper())
			trace("ON UI THREAD " + format, arguments);
	}
}
