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

import android.app.PendingIntent;
import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;

import org.altusmetrum.altoslib_14.AltosLib;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

public class AltosUsb extends AltosDroidLink {

    private final Handler handler;
    private Context context;

    private UsbManager manager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbInterface iface;
    private UsbEndpoint in, out;

    private UsbConnectThread connect_thread = null;

    // Constructor
    public AltosUsb(Context context, UsbDevice device, Handler handler) {
        super(handler);
        this.handler = handler;
        this.device = device;
        this.context = context;

        connect_thread = new UsbConnectThread();
        connect_thread.start();
    }

    public void close() {
        super.close();
        close_device();
        connection = null;
        in = null;
        out = null;
    }

    void connected() {
        if (closed()) {
            AltosDebug.debug("connected after closed");
            return;
        }
        try {
            synchronized(this) {
                super.connected();
            }
        } catch (InterruptedException ie) {
            connect_failed();
        }
    }

    private class UsbConnectThread extends Thread {

        public void run() {
            setName("UsbConnectThread");
            AltosDebug.debug("UsbConnectThread: BEGIN");

            iface = null;
            in = null;
            out = null;

            int	niface = device.getInterfaceCount();

            for (int i = 0; i < niface; i++) {

                iface = device.getInterface(i);

                in = null;
                out = null;

                int nendpoints = iface.getEndpointCount();

                for (int e = 0; e < nendpoints; e++) {
                    UsbEndpoint endpoint = iface.getEndpoint(e);

                    if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        switch (endpoint.getDirection()) {
                        case UsbConstants.USB_DIR_OUT:
                            out = endpoint;
                            break;
                        case UsbConstants.USB_DIR_IN:
                            in = endpoint;
                            break;
                        }
                    }
                }

                if (in != null && out != null)
                    break;
            }

            if (in == null || out == null) {
                connect_failed();
                return;
            }

            AltosDebug.debug("\tin %s out %s\n", in.toString(), out.toString());

            manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

            if (manager == null) {
                AltosDebug.debug("USB_SERVICE failed");
                connect_failed();
                return;
            }

            connection = null;

            try {
                connection = manager.openDevice(device);
            } catch (Exception e) {
            }

            if (connection == null) {
                AltosDebug.debug("openDevice failed");
                connect_failed();
                return;
            }

            if (!connection.claimInterface(iface, true)) {
                AltosDebug.debug("claimInterface failed");
                connect_failed();
                return;
            }

            connected();

            AltosDebug.debug("UsbConnectThread: completed");
        }
    }

    private void connect_failed() {
        if (closed()) {
            AltosDebug.debug("connect_failed after closed");
            return;
        }

        close_device();
        handler.obtainMessage(TelemetryService.MSG_CONNECT_FAILED, this).sendToTarget();
        AltosDebug.error("AltosUsb: Failed to establish connection");
    }

    static private boolean isAltusMetrum(UsbDevice device) {
        if (device.getVendorId() != AltosLib.vendor_altusmetrum)
            return false;
        if (device.getProductId() < AltosLib.product_altusmetrum_min)
            return false;
        return device.getProductId() <= AltosLib.product_altusmetrum_max;
    }

    static boolean matchProduct(int want_product, UsbDevice device) {

        if (!isAltusMetrum(device))
            return false;

        if (want_product == AltosLib.product_any)
            return true;

        int have_product = device.getProductId();

        if (want_product == AltosLib.product_basestation)
            return have_product == AltosLib.product_teledongle ||
                have_product == AltosLib.product_telebt ||
                have_product == AltosLib.product_megadongle;

        if (want_product == AltosLib.product_altimeter)
            return have_product == AltosLib.product_telemetrum ||
                have_product == AltosLib.product_telemega ||
                have_product == AltosLib.product_easymega ||
                have_product == AltosLib.product_telegps ||
                have_product == AltosLib.product_easymini ||
                have_product == AltosLib.product_telemini ||
                have_product == AltosLib.product_easytimer;

        if (have_product == AltosLib.product_altusmetrum)	/* old devices match any request */
            return true;

        return want_product == have_product;
    }

    static public UsbDevice find_device(Context context, int match_product) {
        UsbManager	manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        HashMap<String,UsbDevice> devices = manager.getDeviceList();

        for (UsbDevice	device : devices.values()) {
            int	vendor = device.getVendorId();
            int	product = device.getProductId();

            if (matchProduct(match_product, device)) {
                AltosDebug.debug("found USB device " + device);
                return device;
            }
        }

        return null;
    }

    private void disconnected() {
        if (closed()) {
            AltosDebug.debug("disconnected after closed");
            return;
        }

        AltosDebug.debug("Sending disconnected message");
        handler.obtainMessage(TelemetryService.MSG_DISCONNECTED, this).sendToTarget();
    }

    void close_device() {
        UsbDeviceConnection	tmp_connection;

        synchronized(this) {
            tmp_connection = connection;
            connection = null;
        }

        if (tmp_connection != null) {
            AltosDebug.debug("Closing USB device");
            tmp_connection.close();
        }
    }

    int read(byte[] buffer, int len) {
        for (;;) {
            if (connection == null)
                return -1;

            int ret = connection.bulkTransfer(in, buffer, len, 1000);
            if (ret > 0) {
//                AltosDebug.debug("read(%d) = %d '%s'\n", len, ret, new String(buffer, 0, ret));
                return ret;
//            } else {
//                AltosDebug.debug("USB read timeout");
            }
        }
    }

    int write(byte[] buffer, int start, int len) {
//        AltosDebug.debug("write %d '%s'\n", len, new String(buffer, start, len));

        int remain = len;
        while (remain > 0) {
            if (connection == null)
                return -1;

            int ret = connection.bulkTransfer(out, buffer, start, remain, 1000);
            if (ret > 0) {
                start += ret;
                remain -= ret;
//            } else {
//                AltosDebug.debug("USB write timeout");
            }
        }
        return len;
    }

    // Stubs of required methods when extending AltosLink
    public boolean can_cancel_reply()   { return false; }
    public boolean show_reply_timeout() { return true; }
    public void hide_reply_timeout()    { }

}
