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

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.altusmetrum.altoslib_14.AltosPreferences;
import org.altusmetrum.altoslib_14.AltosPreferencesBackend;

import java.io.File;
import java.util.Map;

public class AltosDroidPreferencesBackend extends AltosPreferencesBackend {
	public final static String        NAME    = "org.altusmetrum.AltosDroid";
	private final Context context;
	private final SharedPreferences prefs;

	public AltosDroidPreferencesBackend(Context in_context) {
		this(in_context, NAME);
	}

	public AltosDroidPreferencesBackend(Context in_context, String in_prefs) {
		context = in_context;
		prefs   = context.getSharedPreferences(in_prefs, 0);
	}

	public String[] keys() {
		Map<String, ?> all = prefs.getAll();
		Object[] ao = all.keySet().toArray();

		String[] as = new String[ao.length];
		for (int i = 0; i < ao.length; i++)
			as[i] = (String) ao[i];
		return as;
	}

	public AltosPreferencesBackend node(String key) {
		if (!nodeExists(key))
			putBoolean(key, true);
		return new AltosDroidPreferencesBackend(context, key);
	}

	public boolean nodeExists(String key) {
		return prefs.contains(key);
	}

	public boolean getBoolean(String key, boolean def) {
		return prefs.getBoolean(key, def);
	}

	public double getDouble(String key, double def) {
		Float f = prefs.getFloat(key, (float) def);
		return f.doubleValue();
	}

	public int getInt(String key, int def) {
		return prefs.getInt(key, def);
	}

	public String getString(String key, String def) {
		String	ret;
		if (key.equals(AltosPreferences.logdirPreference))
			ret = null;
		else
			ret = prefs.getString(key, def);
		AltosDebug.debug("AltosDroidPreferencesBackend get string %s:\n", key);
		if (ret == null)
			AltosDebug.debug("      (null)\n");
		else {
			String[] lines = ret.split("\n");
			for (String l : lines)
				AltosDebug.debug("        %s\n", l);
		}
		return ret;
	}

	public byte[] getBytes(String key, byte[] def) {
		String save = prefs.getString(key, null);

		if (save == null)
			return def;

        return Base64.decode(save, Base64.DEFAULT);
	}

	public void putBoolean(String key, boolean value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(key, value);
		editor.apply();
	}

	public void putDouble(String key, double value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putFloat(key, (float)value);
		editor.apply();
	}

	public void putInt(String key, int value) {
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(key, value);
		editor.apply();
	}

	public void putString(String key, String value) {
		SharedPreferences.Editor editor = prefs.edit();
//		AltosDebug.debug("AltosDroidPreferencesBackend put string %s:\n", key);
//		String[] lines = value.split("\n");
//		for (String l : lines)
//			AltosDebug.debug("        %s\n", l);
		editor.putString(key, value);
		editor.apply();
	}

	public void putBytes(String key, byte[] bytes) {
		SharedPreferences.Editor editor = prefs.edit();
		String save = Base64.encodeToString(bytes, Base64.DEFAULT);
		editor.putString(key, save);
		editor.apply();
	}

	public void remove(String key) {
		SharedPreferences.Editor editor = prefs.edit();
		AltosDebug.debug("remove preference %s\n", key);
		editor.remove(key);
		editor.apply();
	}

	public void flush() {
	}

	public File homeDirectory() {
		return context.getExternalMediaDirs()[0];
	}

	public void debug(String format, Object ... arguments) {
		AltosDebug.debug(format, arguments);
	}
}
