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

import android.os.Parcel;
import android.os.Parcelable;

import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosState;

import java.util.Locale;

public class Tracker implements CharSequence, Comparable, Parcelable {
 	int	serial;
	String	call;
	double	frequency;
	long	received_time;
	String	display;

	private void make_display() {
		if (frequency == 0.0)
			display = "Auto";
		else if (frequency == AltosLib.MISSING) {
			display = String.format(Locale.getDefault(), "%-8.8s  %6d", call, serial);
		} else {
			display = String.format(Locale.getDefault(), "%-8.8s %7.3f %6d", call, frequency, serial);
		}
	}

	public Tracker(int serial, String call, double frequency, long received_time) {
		if (call == null)
			call = "none";

		this.serial = serial;
		this.call = call;
		this.frequency = frequency;
		this.received_time = received_time;
		make_display();
	}

	public Tracker(int serial, String call, double frequency) {
		this(serial, call, frequency, 0);
	}

	public Tracker(AltosState s) {
		this(s == null ? 0 : s.cal_data().serial,
		     s == null ? null : s.cal_data().callsign,
		     s == null ? 0.0 : s.frequency,
		     s == null ? 0 : s.received_time);
	}

	/* CharSequence */
	public char charAt(int index) {
		return display.charAt(index);
	}

	public int length() {
		return display.length();
	}

	public CharSequence subSequence(int start, int end) throws IndexOutOfBoundsException {
		return display.subSequence(start, end);
	}

	public String toString() {
		return display;
	}

	/* Comparable */
	public int compareTo (Object other) {
		Tracker	o = (Tracker) other;
		if (frequency == 0.0) {
			if (o.frequency == 0.0)
				return 0;
			return -1;
		}
		if (o.frequency == 0.0)
			return 1;

		int	a = serial - o.serial;
		int	b = call.compareTo(o.call);
		int	c = (int) Math.signum(frequency - o.frequency);

		if (b != 0)
			return b;
		if (c != 0)
			return c;
		return a;
	}

	/* Parcelable */

	public int describeContents() {
		AltosDebug.debug("describe contents %d", serial);
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		AltosDebug.debug("write to parcel %s", display);
		out.writeInt(serial);
		out.writeString(call);
		out.writeDouble(frequency);
		out.writeLong(received_time);
	}

	public static final Parcelable.Creator<Tracker> CREATOR
	= new Parcelable.Creator<Tracker>() {
			public Tracker createFromParcel(Parcel in) {
				AltosDebug.debug("createFromParcel");
				return new Tracker(in);
			}

			public Tracker[] newArray(int size) {
				AltosDebug.debug("newArray %d", size);
				return new Tracker[size];
			}
		};

	/* newer (-1), same (0), older(1) */
	public int compareAge(Tracker o) {
		if (received_time == o.received_time)
			return 0;
		if (received_time == 0)
			return -1;
		if (o.received_time == 0)
			return 1;
		if (received_time > o.received_time)
			return -1;
		return 1;
	}

	public int compareCall(Tracker o) {
		int v = call.compareTo(o.call);
		if (v == 0)
			return v;
		if (call.equals("auto"))
			return -1;
		if (o.call.equals("auto"))
			return 1;
		return v;
	}

	public int compareSerial(Tracker o) {
		return serial - o.serial;
	}

	public int compareFrequency(Tracker o) {
		return (int) Math.signum(frequency - o.frequency);
	}

	private Tracker(Parcel in) {
		serial = in.readInt();
		call = in.readString();
		frequency = in.readDouble();
		received_time = in.readLong();
		make_display();
		AltosDebug.debug("Create from parcel %s", display);
	}
}
