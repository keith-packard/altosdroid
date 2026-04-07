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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class AltosBluetooth extends AltosDroidLink {

	private ConnectThread    connect_thread = null;

	private final BluetoothDevice device;
	private BluetoothSocket socket;
	private InputStream input;
	private OutputStream output;
	private final boolean		 pause;

	// Constructor
	public AltosBluetooth(BluetoothDevice device, Handler handler, boolean pause) {
		super(handler);
		this.device = device;
		this.handler = handler;
		this.pause = pause;

		connect_thread = new ConnectThread();
		connect_thread.start();
	}

	void connected() {
		if (closed()) {
			AltosDebug.debug("connected after closed");
			return;
		}

		AltosDebug.check_ui("connected\n");
		try {
			synchronized(this) {
				if (socket != null) {
					input = socket.getInputStream();
					output = socket.getOutputStream();
					super.connected();
				}
			}
		} catch (InterruptedException ie) {
			connect_failed();
		} catch (IOException io) {
			connect_failed();
		}
	}

	private void connect_failed() {
		if (closed()) {
			AltosDebug.debug("connect_failed after closed");
			return;
		}

		close_device();
		input = null;
		output = null;
		handler.obtainMessage(TelemetryService.MSG_CONNECT_FAILED, this).sendToTarget();
		AltosDebug.error("ConnectThread: Failed to establish connection");
	}

	void close_device() {
		BluetoothSocket	tmp_socket;

		synchronized(this) {
			tmp_socket = socket;
			socket = null;
		}

		if (tmp_socket != null) {
			try {
				tmp_socket.close();
			} catch (IOException e) {
				AltosDebug.error("close_socket failed");
			}
		}
	}

	public void close() {
		super.close();
		input = null;
		output = null;
	}

	private final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	private void create_socket(BluetoothDevice  device) {

		BluetoothSocket tmp_socket = null;

		AltosDebug.check_ui("create_socket\n");
		try {
			tmp_socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (socket != null) {
			AltosDebug.debug("Socket already allocated %s", socket.toString());
			close_device();
		}
		synchronized (this) {
			socket = tmp_socket;
		}
	}

	private class ConnectThread extends Thread {

		public void run() {
			AltosDebug.debug("ConnectThread: BEGIN (pause %b)", pause);
			setName("ConnectThread");

			if (pause) {
				try {
					Thread.sleep(4000);
				} catch (InterruptedException e) {
				}
			}

			create_socket(device);
			// Always cancel discovery because it will slow down a connection
			try {
				BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
			} catch (Exception e) {
				AltosDebug.debug("cancelDiscovery exception %s", e.toString());
			}

			BluetoothSocket	local_socket = null;

			synchronized (AltosBluetooth.this) {
				if (!closed())
					local_socket = socket;
			}

			if (local_socket != null) {
				try {
					// Make a connection to the BluetoothSocket
					// This is a blocking call and will only return on a
					// successful connection or an exception
					local_socket.connect();
				} catch (Exception e) {
					AltosDebug.debug("Connect exception %s", e.toString());
					try {
						local_socket.close();
					} catch (Exception ce) {
						AltosDebug.debug("Close exception %s", ce.toString());
					}
					local_socket = null;
				}
			}

			if (local_socket != null) {
				connected();
			} else {
				connect_failed();
			}

			AltosDebug.debug("ConnectThread: completed");
		}
	}

	int write(byte[] buffer, int start, int len) {
		if (output == null)
			return -1;
		try {
			output.write(buffer, start, len);
		} catch (IOException ie) {
			return -1;
		}
		return len;
	}

	int read(byte[] buffer, int len) {
		if (input == null)
			return -1;
		try {
			return input.read(buffer, 0, len);
		} catch (IOException ie) {
			return -1;
		}
	}

	// Stubs of required methods when extending AltosLink
	public boolean can_cancel_reply()   { return false; }
	public boolean show_reply_timeout() { return true; }
	public void hide_reply_timeout()    { }

}
