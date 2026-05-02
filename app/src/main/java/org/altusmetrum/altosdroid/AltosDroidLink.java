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

import org.altusmetrum.altoslib_14.AltosLink;
import org.altusmetrum.altoslib_14.AltosPreferences;

import java.util.concurrent.LinkedBlockingQueue;

public abstract class AltosDroidLink extends AltosLink {

    Handler handler;

    Thread              input_thread   = null;
    Thread              output_thread = null;

    LinkedBlockingQueue<byte[]> write_queue = new LinkedBlockingQueue<byte[]>();

    class OutputThread implements Runnable {

        public void run() {
            try {
                for (;;) {
                    byte[] bytes = write_queue.take();
                    int start = 0;
                    int len = bytes.length;
                    while (start < len) {
                        int sent = write(bytes, start, len);
                        if (sent < 0)
                            break;
                        start += sent;
                    }
                }
            } catch (InterruptedException ie) {
            }
        }
    }

    public void run() {
        AltosDebug.debug("Starting");
        try {
            input_loop();
        } catch (InterruptedException e) {
            AltosDebug.debug("Interrupted");
        } finally {
            AltosDebug.debug("Finally");
        }
        AltosDebug.debug("Exiting");
    }

    public double frequency() {
        return frequency;
    }

    public int telemetry_rate() {
        return telemetry_rate;
    }

    public void save_frequency() {
        AltosPreferences.set_frequency(0, frequency);
    }

    public void save_telemetry_rate() {
        AltosPreferences.set_telemetry_rate(0, telemetry_rate);
    }

    Object closed_lock = new Object();
    boolean closing = false;
    boolean closed = false;

    public boolean closed() {
        synchronized(closed_lock) {
            return closing;
        }
    }

    void connected() throws InterruptedException {
        input_thread = new Thread(this);
        input_thread.setName("AltosInputThread");
        input_thread.start();
        output_thread = new Thread(new OutputThread());
        output_thread.setName("AltosOutputThread");
        output_thread.start();

        // Configure the newly connected device for telemetry
        print("~\nE 0\n");
        set_monitor(false);
        AltosDebug.debug("ConnectThread: connected");

        /* Let TelemetryService know we're connected
         */
        handler.obtainMessage(TelemetryService.MSG_CONNECTED, this).sendToTarget();

        /* Notify other waiting threads that we're connected now
         */
        notifyAll();
    }

    public void closing() {
        synchronized(closed_lock) {
            AltosDebug.debug("Marked closing true");
            closing = true;
        }
    }

    private boolean actually_closed() {
        synchronized(closed_lock) {
            return closed;
        }
    }

    abstract void close_device();

    public void close() {
        AltosDebug.debug("close(): begin");

        closing();

        flush_output();

        synchronized (closed_lock) {
            AltosDebug.debug("Marked closed true");
            closed = true;
        }

        close_device();

        synchronized(this) {

            if (input_thread != null) {
//                AltosDebug.debug("close(): stopping input_thread");
                try {
//                    AltosDebug.debug("close(): input_thread.interrupt().....");
                    input_thread.interrupt();
//                    AltosDebug.debug("close(): input_thread.join().....");
                    input_thread.join();
//                    AltosDebug.debug("close(): input_thread done");
                } catch (Exception e) {}
                input_thread = null;
            }
            if (output_thread != null) {
//                AltosDebug.debug("close(): stopping output_thread");
                try {
//                    AltosDebug.debug("close(): output_thread.interrupt().....");
                    output_thread.interrupt();
//                    AltosDebug.debug("close(): output_thread.join().....");
                    output_thread.join();
//                    AltosDebug.debug("close(): output_thread done");
                } catch (Exception e) {}
                output_thread = null;
            }
            notifyAll();
        }
    }

    abstract int write(byte[] buffer, int start, int len);

    abstract int read(byte[] buffer, int len);

    private static final int buffer_size = 64;

    private final byte[] in_buffer = new byte[buffer_size];
    private final byte[] out_buffer = new byte[buffer_size];
    private int buffer_len = 0;
    private int buffer_off = 0;
    private int out_buffer_off = 0;

    private final byte[] debug_chars = new byte[buffer_size];
    private int debug_off;

//    private void debug_input(byte b) {
//        if (b == '\n') {
//            AltosDebug.debug("            " + new String(debug_chars, 0, debug_off));
//            debug_off = 0;
//        } else {
//            if (debug_off < buffer_size)
//                debug_chars[debug_off++] = b;
//        }
//    }

    private void disconnected() {
        if (closed()) {
            AltosDebug.debug("disconnected after closed");
            return;
        }

        AltosDebug.debug("Sending disconnected message");
        handler.obtainMessage(TelemetryService.MSG_DISCONNECTED, this).sendToTarget();
    }

    public int getchar() {

        if (actually_closed())
            return ERROR;

        while (buffer_off == buffer_len) {
            buffer_len = read(in_buffer, buffer_size);
            if (buffer_len < 0) {
                AltosDebug.debug("ERROR returned from getchar()");
                disconnected();
                return ERROR;
            }
            buffer_off = 0;
        }
//        if (AltosDebug.D)
//            debug_input(in_buffer[buffer_off]);
        return in_buffer[buffer_off++];
    }

    public synchronized void flush_output() {
        super.flush_output();

        if (actually_closed()) {
            out_buffer_off = 0;
            return;
        }

        byte[] copy = new byte[out_buffer_off];
        for (int i = 0; i < out_buffer_off; i++)
            copy[i] = out_buffer[i];
        try {
            write_queue.put(copy);
        } catch (InterruptedException ie) {
        }
        out_buffer_off = 0;
    }

    public void putchar(byte c) {
        out_buffer[out_buffer_off++] = c;
        if (out_buffer_off == buffer_size)
            flush_output();
    }

    public void print(String data) {
        byte[] bytes = data.getBytes();
//	AltosDebug.debug(data.replace('\n', '\\'));
        for (byte b : bytes)
            putchar(b);
    }

    public AltosDroidLink(Handler handler) {
        this.handler = handler;
    }
}
