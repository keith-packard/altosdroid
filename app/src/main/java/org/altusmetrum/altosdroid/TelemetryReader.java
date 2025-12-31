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

import android.os.Handler;

import org.altusmetrum.altoslib_14.AltosCRCException;
import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosLine;
import org.altusmetrum.altoslib_14.AltosLink;
import org.altusmetrum.altoslib_14.AltosTelemetry;
import org.altusmetrum.altoslib_14.AltosTelemetryLegacy;

import java.io.IOException;
import java.text.ParseException;
import java.util.concurrent.LinkedBlockingQueue;

public class TelemetryReader extends Thread {

	int         crc_errors;

	Handler handler;

	AltosLink link;

	LinkedBlockingQueue<AltosLine> telemQueue;

	public AltosTelemetry read() throws ParseException, AltosCRCException, InterruptedException, IOException {
		AltosLine l = telemQueue.take();
		if (l.line == null)
			throw new IOException("IO error");
		AltosTelemetry telem = AltosTelemetryLegacy.parse(l.line);
		return telem;
	}

	public void close() {
		link.remove_monitor(telemQueue);
		link = null;
		telemQueue.clear();
		telemQueue = null;
	}

	public void run() {
		try {
			AltosDebug.debug("starting loop");
			while (telemQueue != null) {
				try {
					AltosTelemetry	telem = read();
					AltosDebug.debug("got telemetry line");
					telem.set_frequency(link.frequency);
					handler.obtainMessage(TelemetryService.MSG_TELEMETRY, telem).sendToTarget();
				} catch (ParseException pp) {
					AltosDebug.error("Parse error: %d \"%s\"", pp.getErrorOffset(), pp.getMessage());
				} catch (AltosCRCException ce) {
					++crc_errors;
					handler.obtainMessage(TelemetryService.MSG_CRC_ERROR, new Integer(crc_errors)).sendToTarget();
				}
			}
		} catch (InterruptedException ee) {
		} catch (IOException ie) {
			AltosDebug.error("IO exception in telemetry reader");
			handler.obtainMessage(TelemetryService.MSG_DISCONNECTED, link).sendToTarget();
		} finally {
			close();
		}
	}

	public TelemetryReader (AltosLink in_link, Handler in_handler) {
		AltosDebug.debug("connected TelemetryReader create started");
		link    = in_link;
		handler = in_handler;

		telemQueue = new LinkedBlockingQueue<AltosLine>();
		link.add_monitor(telemQueue);
		link.set_telemetry(AltosLib.ao_telemetry_standard);

		AltosDebug.debug("connected TelemetryReader created");
	}
}
