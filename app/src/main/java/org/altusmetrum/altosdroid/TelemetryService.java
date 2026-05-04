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

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ServiceCompat;

import org.altusmetrum.altoslib_14.*;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;

public class TelemetryService extends Service
    implements AltosIdleMonitorListener,
    LocationListener {

    static final int MSG_REGISTER_CLIENT   = 1;
    static final int MSG_UNREGISTER_CLIENT = 2;
    static final int MSG_CONNECT           = 3;
    static final int MSG_OPEN_USB	   = 4;
    static final int MSG_CONNECTED         = 5;
    static final int MSG_CONNECT_FAILED    = 6;
    static final int MSG_DISCONNECTED      = 7;
    static final int MSG_TELEMETRY         = 8;
    static final int MSG_SETFREQUENCY      = 9;
    static final int MSG_CRC_ERROR	   = 10;
    static final int MSG_SETBAUD	   = 11;
    static final int MSG_DISCONNECT	   = 12;
    static final int MSG_DELETE_SERIAL     = 13;
    static final int MSG_BLUETOOTH_ENABLED = 14;
    static final int MSG_MONITOR_IDLE_START= 15;
    static final int MSG_MONITOR_IDLE_STOP = 16;
    static final int MSG_REBOOT	           = 17;
    static final int MSG_IGNITER_QUERY     = 18;
    static final int MSG_IGNITER_FIRE      = 19;
    static final int MSG_POST_NOTIFICATION = 20;
    static final int MSG_GET_CONFIG_DATA   = 21;
    static final int MSG_SET_CONFIG_DATA   = 22;
    static final int MSG_SET_VIEW          = 23;
    static final int MSG_ENABLE_LOCATION   = 24;
    static final int MSG_UPDATE_TELEM      = 25;
    static final int MSG_MONITOR_BATTERY   = 26;
    static final int MSG_DONE_SPEAKING     = 27;

    static final int TELEMETRY_SERVICE_ID  = 1002;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private final int NOTIFICATION = R.string.telemetry_service_label;
    //private NotificationManager mNM;

    ArrayList<Messenger> clients = new ArrayList<>(); // Keeps track of all current registered clients.
    HandlerThread handler_thread;
    Looper handler_looper;
    Handler handler;
    Messenger messenger; // Target we publish for clients to send messages to IncomingHandler.

    // Name of the connected device
    DeviceAddress address;
    private AltosDroidLink  altos_link  = null;
    private TelemetryReader telemetry_reader = null;
    private TelemetryLogger telemetry_logger = null;

    // Local Bluetooth adapter
    private BluetoothAdapter bluetooth_adapter = null;

    // Last data seen; send to UI when it starts
    private TelemetryState	telemetry_state;

    // Text to speech system
    AltosVoice altos_voice;

    // Idle monitor if active
    AltosIdleMonitor idle_monitor = null;

    // Igniter bits
    AltosIgnite ignite = null;
    boolean ignite_running;

    // Config bits
    boolean config_running;

    Notification notification;

    String fragment_name = null;

    // Handler of incoming messages from clients.
    static class IncomingHandler extends Handler {

        private final TelemetryService service;

        IncomingHandler(Looper looper, TelemetryService s) {
            super(looper);
            service = s;
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            DeviceAddress address;

            TelemetryService s = service;
            AltosDroidLink bt;

            switch (msg.what) {

                /* Messages from application */
            case MSG_REGISTER_CLIENT:
//                AltosDebug.debug("MSG_REGISTER_CLIENT");
                s.add_client(msg.replyTo);
                break;
            case MSG_UNREGISTER_CLIENT:
//                AltosDebug.debug("MSG_UNREGISTER_CLIENT");
                s.remove_client(msg.replyTo);
                break;
            case MSG_CONNECT:
//                AltosDebug.debug("MSG_CONNECT");
                address = (DeviceAddress) msg.obj;
                AltosDroidPreferences.set_active_device(address);
                s.start_altos_bluetooth(address, false);
                break;
            case MSG_OPEN_USB:
//                AltosDebug.debug("MSG_OPEN_USB");
                UsbDevice device = (UsbDevice) msg.obj;
                address = new DeviceAddress(DeviceAddress.ADDRESS_USB, DeviceAddress.NAME_USB);
                AltosDroidPreferences.set_active_device(address);
                s.start_altos_usb(device, address);
                break;
            case MSG_DISCONNECT:
//                AltosDebug.debug("MSG_DISCONNECT");
                s.address = null;
                if (!(Boolean) msg.obj)
                    AltosDroidPreferences.set_active_device(null);
                s.disconnect(true);
                break;
            case MSG_DELETE_SERIAL:
//                AltosDebug.debug("MSG_DELETE_SERIAL");
                s.delete_serial((Integer) msg.obj);
                break;
            case MSG_SETFREQUENCY:
//                AltosDebug.debug("MSG_SETFREQUENCY");
                if (s.is_basestation) {
                    s.telemetry_state.set_frequency((Double) msg.obj);
                    if (s.idle_monitor != null) {
                        s.idle_monitor.set_frequency(s.telemetry_state.frequency);
                    } else if (s.telemetry_state.connect == TelemetryState.CONNECT_CONNECTED) {
                        try {
                            s.altos_link.set_radio_frequency(s.telemetry_state.frequency);
                            s.altos_link.save_frequency();
                        } catch (InterruptedException | TimeoutException ignored) {
                        }
                    }
                    s.send_telem_to_clients();
                }
                break;
            case MSG_SETBAUD:
//                AltosDebug.debug("MSG_SETBAUD");
                if (s.is_basestation) {
                    s.telemetry_state.set_telemetry_rate((Integer) msg.obj);
                    if (s.telemetry_state.connect == TelemetryState.CONNECT_CONNECTED) {
                        s.altos_link.set_telemetry_rate(s.telemetry_state.telemetry_rate);
                        s.altos_link.save_telemetry_rate();
                    }
                    s.send_telem_to_clients();
                }
                break;

                /*
                 *Messages from AltosBluetooth
                 */
            case MSG_CONNECTED:
//                AltosDebug.debug("MSG_CONNECTED");
                bt = (AltosDroidLink) msg.obj;

                if (bt != s.altos_link) {
                    AltosDebug.debug("Stale message");
                    break;
                }
//                AltosDebug.debug("Connected to device");
                try {
                    s.connected();
                } catch (InterruptedException ignored) {
                }
                break;
            case MSG_CONNECT_FAILED:
//                AltosDebug.debug("MSG_CONNECT_FAILED");
                bt = (AltosDroidLink) msg.obj;

                if (bt != s.altos_link) {
                    AltosDebug.debug("Stale message");
                    break;
                }
                if (s.address != null) {
                    AltosDebug.debug("Connection failed... retrying");
                    s.start_altos_bluetooth(s.address, true);
                } else {
                    s.disconnect(true);
                }
                break;
            case MSG_DISCONNECTED:
                /* This can be sent by either AltosDroidLink or TelemetryReader */
//                AltosDebug.debug("MSG_DISCONNECTED");
                bt = (AltosDroidLink) msg.obj;

                if (bt != s.altos_link) {
                    AltosDebug.debug("Stale message");
                    break;
                }
                if (s.address != null) {
                    AltosDebug.debug("Connection lost... retrying");
                    if (!s.address.is_usb())
                        s.start_altos_bluetooth(s.address, true);
                } else {
                    s.disconnect(true);
                }
                break;

                /*
                 * Messages from TelemetryReader
                 */
            case MSG_TELEMETRY:
//                AltosDebug.debug("MSG_TELEMETRY");
                s.telemetry((AltosTelemetry) msg.obj);
                break;
            case MSG_CRC_ERROR:
//                AltosDebug.debug("MSG_CRC_ERROR");
                // forward crc error messages
                s.telemetry_state.set_crc_errors((Integer) msg.obj);
                s.send_telem_to_clients();
                break;
            case MSG_BLUETOOTH_ENABLED:
//                AltosDebug.debug("MSG_BLUETOOTH_ENABLED");
                address = AltosDroidPreferences.active_device();
                if (address != null && !address.is_usb())
                    s.start_altos_bluetooth(address, false);
                break;
            case MSG_MONITOR_IDLE_START:
//                AltosDebug.debug("MSG_MONITOR_IDLE_START");
                s.start_idle_monitor();
                break;
            case MSG_MONITOR_IDLE_STOP:
//                AltosDebug.debug("MSG_MONITOR_IDLE_STOP");
                s.stop_idle_monitor();
                break;
            case MSG_REBOOT:
//                AltosDebug.debug("MSG_REBOOT");
                s.reboot_remote();
                break;
            case MSG_IGNITER_QUERY:
//                AltosDebug.debug("MSG_IGNITER_QUERY");
                s.igniter_query(msg.replyTo);
                break;
            case MSG_IGNITER_FIRE:
//                AltosDebug.debug("MSG_IGNITER_FIRE");
                s.igniter_fire((String) msg.obj);
                break;
            case MSG_POST_NOTIFICATION:
//                AltosDebug.debug("MSG_POST_NOTIFICATION");
                s.post_notification();
                break;
            case MSG_GET_CONFIG_DATA:
//                AltosDebug.debug("MSG_GET_CONFIG_DATA");
                s.get_config_data(msg.replyTo, (Boolean) msg.obj);
                break;
            case MSG_SET_CONFIG_DATA:
//                AltosDebug.debug("MSG_SET_CONFIG_DATA");
                s.set_config_data((AltosConfigDataRemote) msg.obj);
                break;
            case MSG_SET_VIEW:
//                AltosDebug.debug("MSG_SET_VIEW");
                s.set_view((Integer) msg.obj);
                break;
            case MSG_ENABLE_LOCATION:
//                AltosDebug.debug("MSG_ENABLE_LOCATION");
                s.enable_location();
                break;
            case MSG_UPDATE_TELEM:
//                AltosDebug.debug("MSG_UPDATE_TELEM");
                s.update_telem();
                break;
            case MSG_MONITOR_BATTERY:
//                AltosDebug.debug("MSG_MONITOR_BATTERY");
                s.monitor_battery();
                break;
            case MSG_DONE_SPEAKING:
//                AltosDebug.debug("MSG_DONE_SPEAKING");
                s.speak();
                break;
            default:
                super.handleMessage(msg);
            }
//            AltosDebug.debug("TelemetryHandler handleMessage done");
        }
    }

    @Override
    public void onCreate() {

        AltosDebug.init(this);

        // Initialise preferences
        AltosDroidPreferences.init(this);

        telemetry_state = new TelemetryState();

        handler_thread = new HandlerThread("TelemetryHandler");
        handler_thread.setName("TelemetryHandler");
        handler_thread.start();
        handler_looper = handler_thread.getLooper();
        handler = new IncomingHandler(handler_looper, this);
        messenger = new Messenger(handler);

        // Get local Bluetooth adapter
        bluetooth_adapter = BluetoothAdapter.getDefaultAdapter();

        if (altos_voice == null)
            altos_voice = new AltosVoice(this);

        // Create a reference to the NotificationManager so that we can update our notifcation text later
        //mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        /* Pull the saved state information out of the preferences database
         */
        ArrayList<Integer> serials = AltosPreferences.list_states();

        AltosDebug.debug("latest serial %d\n", telemetry_state.latest_serial);

        for (int serial : serials) {
            AltosState saved_state = AltosPreferences.state(serial);
            if (saved_state != null) {
                AltosDebug.debug("recovered old state serial %d flight %d",
                                 serial,
                                 saved_state.cal_data().flight);
                if (saved_state.gps != null)
                    AltosDebug.debug("\tposition %f,%f",
                                     saved_state.gps.lat,
                                     saved_state.gps.lon);
                telemetry_state.put(serial, saved_state);
            } else {
                AltosDebug.debug("Failed to recover state for %d", serial);
                AltosPreferences.remove_state(serial);
            }
        }

    }

    private NotificationChannel createNotificationChannel(String channelId, String channelName) {
        NotificationChannel chan = new NotificationChannel(
            channelId, channelName, NotificationManager.IMPORTANCE_LOW);
        chan.enableLights(false);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);
        return chan;
    }

    private void displayNotification(Context context, Notification notification) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(1, notification);
    }

    private void post_notification() {

        if (notification != null)
            return;

        int		flag = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            flag = 33554432; // PendingIntent.FLAG_MUTABLE

        /*
         * The PendingIntent to launch our activity if the user
         * selects this notification
         */
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                                                                new Intent(this, MainActivity.class), flag);

        String channelId = "org.altusmetrum.altosdroid.telemetry";

        NotificationChannel channel = createNotificationChannel(channelId,
                                                                "AltosDroid Telemetry Service");

        /* Create notification to be displayed while the service runs */
        Notification.Builder builder = new Notification.Builder(this, channel.getId());
        builder.setSmallIcon(R.drawable.altosdroid);
        builder.setContentTitle(getText(R.string.telemetry_service_label));
        builder.setContentText(getText(R.string.telemetry_service_started));
        builder.setContentIntent(contentIntent);
        builder.setWhen(System.currentTimeMillis());
        builder.setOngoing(true);
        builder.setBadgeIconType(Notification.BADGE_ICON_SMALL);
        notification = builder.build();

        // Move us into the foreground.
        int type = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;

        try {
            ServiceCompat.startForeground(this,
                                          TELEMETRY_SERVICE_ID,
                                          notification,
                                          type);
        } catch (SecurityException e) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AltosDebug.debug("Received start id %d: %s", startId, intent);

        /* Start bluetooth if we don't have a connection already */
        if (intent != null &&
            (telemetry_state.connect == TelemetryState.CONNECT_DISCONNECTED))
        {
            String	action = intent.getAction();

            if (action.equals(MainActivity.ACTION_BLUETOOTH)) {
                DeviceAddress address = AltosDroidPreferences.active_device();
                if (address != null && !address.is_usb())
                    start_altos_bluetooth(address, false);
            }
        }
        return START_STICKY;
    }
    @Override
    public void onDestroy() {

        // Stop the voice system
        if (altos_voice != null) {
            altos_voice.stop();
            altos_voice = null;
        }

        // Stop the bluetooth Comms threads
        disconnect(true);

        // Stop listening for location updates
        if (locationManager != null)
            locationManager.removeUpdates(this);

        handler_thread.quit();

        // Demote us from the foreground, and cancel the persistent notification.
        stopForeground(true);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.telemetry_service_stopped, Toast.LENGTH_SHORT).show();
    }
    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    /* Handle telemetry packet
     */
    private void telemetry(AltosTelemetry telem) {
        AltosState	state;

        state = telemetry_state.get(telem.serial());
        if (state == null)
            state = new AltosState(new AltosCalData());
        telem.provide_data(state);
        telemetry_state.put(telem.serial(), state);
        if (state != null) {
            AltosPreferences.set_state(state,telem.serial());
        }
        notify_update_telem();
    }

    /* A new friend has connected
     */
    private void add_client(Messenger client) {
        check_thread("add_client");
        clients.add(client);
        AltosDebug.debug("Client bound to service");

        /* On connect, send the current state to the new client
         */
        send_telem_to_client(client);
        send_idle_mode_to_client(client);

        /* If we've got an address from a previous session, then
         * go ahead and try to reconnect to the device
         */
        if (telemetry_state.connect == TelemetryState.CONNECT_DISCONNECTED) {
            if (address != null && !address.is_usb()) {
                AltosDebug.debug("Reconnecting now...");
                start_altos_bluetooth(address, false);
            }
        }
    }

    /* A client has disconnected, clean up
     */
    private void remove_client(Messenger client) {
        check_thread("remove_client");
        clients.remove(client);
        AltosDebug.debug("Client unbound from service");

        /* When the list of clients is empty, stop the service if
         * we have no current telemetry source
         */

        if (clients.isEmpty() && telemetry_state.connect == TelemetryState.CONNECT_DISCONNECTED) {
            AltosDebug.debug("No clients, no connection. Stopping\n");
            stopSelf();
        }
    }

    private void check_thread(String where) {
//        AltosDebug.debug("check_thread %s", where);
        if (Thread.currentThread() != handler_thread) {
            AltosDebug.error("running %s from thread %s", where, Thread.currentThread().getName());
        }
    }

    private void send_telem_to_client(Messenger client) {
        check_thread("send_telem_to_client");
        Message m = Message.obtain(null, MainActivity.MSG_STATE, telemetry_state);
        try {
            client.send(m);
        } catch (RemoteException e) {
            AltosDebug.error("Client %s disappeared", client.toString());
            remove_client(client);
        }
    }

    private void send_telem_to_clients() {
        for (Messenger client : clients)
            send_telem_to_client(client);
    }

    private void send_idle_mode_to_client(Messenger client) {
        Message m = Message.obtain(null, MainActivity.MSG_IDLE_MODE, idle_monitor != null);
        try {
            client.send(m);
        } catch (RemoteException e) {
            AltosDebug.error("Client %s disappeared", client.toString());
            remove_client(client);
        }
    }

    private void send_idle_mode_to_clients() {
        for (Messenger client : clients)
            send_idle_mode_to_client(client);
    }

    private void send_tracker_connected_to_client(Messenger client) {
        Message m = Message.obtain(null, MainActivity.MSG_TRACKER_CONNECTED, telemetry_state.config);
        try {
            client.send(m);
        } catch (RemoteException e) {
            AltosDebug.error("Client %s disappeared", client.toString());
            remove_client(client);
        }
    }

    private void send_tracker_connected_to_clients() {
        for (Messenger client : clients)
            send_tracker_connected_to_client(client);
    }

    private void send_file_failed_to_client(Messenger client, File f) {
        Message m = Message.obtain(null, MainActivity.MSG_FILE_FAILED, f);
        try {
            client.send(m);
        } catch (RemoteException e) {
            AltosDebug.error("Client %s disappeared", client.toString());
            remove_client(client);
        }
    }

    public void send_file_failed_to_clients(File f) {
        for (Messenger client : clients)
            send_file_failed_to_client(client, f);
    }

    private void telemetry_start() {
        check_thread("telemetry_start");
        if (telemetry_reader == null && idle_monitor == null && !ignite_running && !config_running) {
            telemetry_reader = new TelemetryReader(altos_link, handler);
            telemetry_reader.start();
            AltosDebug.debug("connected TelemetryReader started");
        }
        if (telemetry_logger == null) {
            telemetry_logger = new TelemetryLogger(this, altos_link);
        }
        start_receiver_voltage_timer();
    }

    private void telemetry_stop() {
        stop_receiver_voltage_timer();

        if (telemetry_logger != null) {
            AltosDebug.debug("disconnect(): stopping TelemetryLogger");
            telemetry_logger.stop();
            telemetry_logger = null;
        }

        if (telemetry_reader != null) {
            AltosDebug.debug("disconnect(): stopping TelemetryReader");
            telemetry_reader.interrupt();
            try {
                telemetry_reader.join();
            } catch (InterruptedException ignored) {
            }
            telemetry_reader = null;
        }
    }

    private void disconnect(boolean notify) {
        AltosDebug.debug("disconnect(): begin");

        telemetry_state.set_connect(TelemetryState.CONNECT_DISCONNECTED, null);

        if (idle_monitor != null)
            stop_idle_monitor();

        if (altos_link != null)
            altos_link.closing();

        telemetry_stop();

        if (altos_link != null) {
            AltosDebug.debug("disconnect(): stopping AltosDroidLink");
            altos_link.close();
            altos_link = null;
            ignite = null;
        }

        telemetry_state.set_config(null);

        if (notify) {
            AltosDebug.debug("disconnect(): send message to clients");
            send_telem_to_clients();
            if (clients.isEmpty()) {
                AltosDebug.debug("disconnect(): no clients, terminating");
                stopSelf();
            }
        }
    }

    private void start_altos_usb(UsbDevice device, DeviceAddress address) {
        disconnect(false);

        this.address = address;
        altos_link = new AltosUsb(this, device, handler);
        telemetry_state.set_connect(TelemetryState.CONNECT_CONNECTING, address);
        send_telem_to_clients();
    }

    private void delete_serial(int serial) {
        telemetry_state.remove(serial);
        AltosPreferences.remove_state(serial);
        send_telem_to_clients();
    }

    private void start_altos_bluetooth(DeviceAddress address, boolean pause) {
        if (bluetooth_adapter == null || !bluetooth_adapter.isEnabled() || address.address == null) {
            return;
        }
        disconnect(false);
        // Get the BluetoothDevice object
        BluetoothDevice device = bluetooth_adapter.getRemoteDevice(address.address);

        this.address = address;

//        AltosDebug.debug("start_altos_bluetooth(): Connecting to %s (%s)", device.getName(), device.getAddress());
        altos_link = new AltosBluetooth(device, handler, pause);
        telemetry_state.set_connect(TelemetryState.CONNECT_CONNECTING, address);
        send_telem_to_clients();
    }

    private void start_idle_monitor() {
        if (altos_link != null && idle_monitor == null) {
            telemetry_stop();
            idle_monitor = new AltosIdleMonitor(this, altos_link, is_basestation, false);
            if (is_basestation) {
                idle_monitor.set_callsign(AltosPreferences.callsign());
                idle_monitor.set_frequency(telemetry_state.frequency);
            }
            telemetry_state.set_idle_mode(true);
            idle_monitor.start();
            send_idle_mode_to_clients();
        }
    }

    private void stop_idle_monitor() {
        if (idle_monitor != null) {
            try {
                idle_monitor.abort();
                altos_link.flush_input();
            } catch (InterruptedException ignored) {
            }
            idle_monitor = null;
            telemetry_state.set_idle_mode(false);
            if (is_basestation)
                telemetry_start();
            send_idle_mode_to_clients();
        }
    }

    private void reboot_remote() {
        if (altos_link != null) {
            stop_idle_monitor();
            try {
                altos_link.start_remote();
                altos_link.printf("r eboot\n");
                altos_link.flush_output();
            } catch (TimeoutException | InterruptedException ignored) {
            } finally {
                try {
                    altos_link.stop_remote();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private void ensure_ignite() {
        if (ignite == null)
            ignite = new AltosIgnite(altos_link, true, false);
    }

    private synchronized void igniter_query(Messenger client) {
        ensure_ignite();
        HashMap<String,Integer> status_map = null;
        ignite_running = true;
        try {
            stop_idle_monitor();
            try {
                status_map = ignite.status();
            } catch (InterruptedException ie) {
//                AltosDebug.debug("ignite.status interrupted");
            } catch (TimeoutException te) {
//                AltosDebug.debug("ignite.status timeout");
            }
        } finally {
            ignite_running = false;
        }
        Message m = Message.obtain(null, MainActivity.MSG_IGNITER_STATUS, status_map);
        try {
            client.send(m);
        } catch (RemoteException ignored) {
        }
    }

    private synchronized void igniter_fire(String igniter) {
        ensure_ignite();
        ignite_running = true;
        stop_idle_monitor();
        try {
            ignite.fire(igniter);
        } catch (InterruptedException ignored) {
        } finally {
            ignite_running = false;
        }
    }

    private synchronized void get_config_data(Messenger client, boolean remote) {
        remote = is_basestation;
        config_running = true;
        AltosConfigDataRemote config_data = null;
        try {
            telemetry_stop();
            stop_idle_monitor();
            try {
                if (altos_link != null)
                    config_data = new AltosConfigDataRemote(altos_link, remote);
            } catch (InterruptedException ie) {
//                AltosDebug.debug("get_config_data interrupted");
            } catch (TimeoutException te) {
//                AltosDebug.debug("get_config_data timeout");
            }
            Message m = Message.obtain(null, MainActivity.MSG_CONFIG_DATA, config_data);
            try {
                client.send(m);
//                AltosDebug.debug("MSG_CONFIG_DATA sent config data is %b", config_data != null);
            } catch (RemoteException ignored) {
            }
        } finally {
            config_running = false;
        }
    }

    private synchronized void set_config_data(AltosConfigDataRemote config_data) {
        config_running = true;
        try {
            telemetry_stop();
            stop_idle_monitor();
            try {
                config_data.save(altos_link);
            } catch (InterruptedException ie) {
            } catch (TimeoutException te) {
            }
        } finally {
            config_running = false;
        }
    }

    // Timer for receiver battery voltage monitoring
    Timer receiver_voltage_timer;

    private void update_receiver_voltage() {
        if (altos_link != null && idle_monitor == null && !ignite_running && !config_running) {
            notify_monitor_battery();
        }
    }

    private void stop_receiver_voltage_timer() {
        if (receiver_voltage_timer != null) {
            receiver_voltage_timer.cancel();
            receiver_voltage_timer.purge();
            receiver_voltage_timer = null;
        }
    }

    private void start_receiver_voltage_timer() {
        if (receiver_voltage_timer == null && altos_link.has_monitor_battery()) {
            receiver_voltage_timer = new Timer();
            receiver_voltage_timer.schedule(new TimerTask() { public void run() {update_receiver_voltage();}}, 1000L, 10000L);
        }
    }

    public boolean is_basestation;

    private void connected() throws InterruptedException {
//        AltosDebug.debug("connected top");
        try {
            if (altos_link == null)
                throw new InterruptedException("no connected device");
            telemetry_state.set_config(altos_link.config_data());
            is_basestation = telemetry_state.config.is_basestation();
            if (is_basestation) {
                altos_link.set_radio_frequency(telemetry_state.frequency);
                altos_link.set_telemetry_rate(telemetry_state.telemetry_rate);
            }
        } catch (TimeoutException e) {
            // If this timed out, then we really want to retry it, but
            // probably safer to just retry the connection from scratch.
//            AltosDebug.debug("connected timeout");
            if (address != null && !address.is_usb()) {
//                AltosDebug.debug("connected timeout, retrying");
                start_altos_bluetooth(address, true);
            } else {
                handler.obtainMessage(MSG_CONNECT_FAILED).sendToTarget();
                disconnect(true);
            }
            return;
        }

//        AltosDebug.debug("connected device configured");
        telemetry_state.set_connect(TelemetryState.CONNECT_CONNECTED, address);
        send_telem_to_clients();

        if (is_basestation)
            telemetry_start();
        else
            send_tracker_connected_to_clients();

    }

    boolean speaking;

    private void speak() {
        check_thread("speak");
        if (altos_voice != null) {

            int selected_serial = AltosDroidPreferences.selected_serial();

            if (selected_serial != MainActivity.SELECT_AUTO && telemetry_state.get(selected_serial) == null)
                selected_serial = MainActivity.SELECT_AUTO;

            int shown_serial = selected_serial;

            if (telemetry_state.idle_mode)
                shown_serial = telemetry_state.latest_serial;

            AltosState	state = telemetry_state.get(shown_serial);

            AltosGreatCircle from_receiver = null;

            if (telemetry_state.receiver_location != null && state != null && state.gps != null && state.gps.locked) {
                double altitude = 0;
                if (telemetry_state.receiver_location.hasAltitude())
                    altitude = telemetry_state.receiver_location.getAltitude();
                from_receiver = new AltosGreatCircle(telemetry_state.receiver_location.getLatitude(),
                                                     telemetry_state.receiver_location.getLongitude(),
                                                     altitude,
                                                     state.gps.lat,
                                                     state.gps.lon,
                                                     state.gps.alt);
            }

            boolean quiet = true;
            quiet = telemetry_state.quiet;
            speaking = altos_voice.tell(telemetry_state, state, from_receiver, telemetry_state.receiver_location, quiet);
        }
    }

    public void done_speaking() {
        try {
            messenger.send(Message.obtain(null, MSG_DONE_SPEAKING));
        } catch(RemoteException e) {
        }
    }

    private void set_view(int view) {
        check_thread("set_view");
        telemetry_state.set_view(view);
        notify_update_telem();
    }

    LocationManager locationManager;

    static public boolean location_has_gps;

    private void enable_location() {
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        List<String> locationProviders = locationManager.getAllProviders();

        String selectedLocationProvider = null;

        /* Record whether we have GPS at all */
        for (String locationProvider : locationProviders)
            if (locationProvider.equals(LocationManager.GPS_PROVIDER)) {
                location_has_gps = true;
                break;
            }

        /* Now go find the best of the available location providers */
        for (String pref : MainActivity.preferredLocationProviders) {
            for (String locationProvider : locationProviders) {
                if (pref == null || pref.equals(locationProvider)) {
                    selectedLocationProvider = locationProvider;
                    break;
                }
            }
            if (selectedLocationProvider != null)
                break;
        }

        if (selectedLocationProvider != null) {
            AltosDebug.debug("Using location provider %s\n", selectedLocationProvider);
            try {
                locationManager.requestLocationUpdates(selectedLocationProvider, 1000, 1, this);
                Location location = locationManager.getLastKnownLocation(selectedLocationProvider);
                if (location != null)
                    telemetry_state.set_receiver_location(location, location_has_gps);
            } catch (SecurityException|IllegalArgumentException e) {
            }
        }
    }

    private void update_telem() {
        send_telem_to_clients();
        speak();
    }

    private void notify_update_telem() {
        try {
            messenger.send(Message.obtain(null, MSG_UPDATE_TELEM));
        } catch(RemoteException e) {
        }
    }

    private void monitor_battery() {
        try {
            telemetry_state.set_receiver_battery(altos_link.monitor_battery());
            update_telem();
        } catch (InterruptedException ignored) {
        }
    }

    private void notify_monitor_battery() {
        try {
            messenger.send(Message.obtain(null, MSG_MONITOR_BATTERY));
        } catch(RemoteException e) {
        }
    }

    @Override
    public void update(AltosState state, AltosListenerState listener_state) {
        telemetry_state.put(state.cal_data().serial, state);
        telemetry_state.set_receiver_battery(listener_state.battery);
        notify_update_telem();
    }

    @Override
    public void onLocationChanged(Location location) {
        telemetry_state.set_receiver_location(location, location_has_gps);
        notify_update_telem();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void error(String reason) {
        stop_idle_monitor();
    }

    @Override
    public void failed() {
    }
}
