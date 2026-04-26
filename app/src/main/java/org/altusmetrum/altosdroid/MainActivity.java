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

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;

import org.altusmetrum.altosdroid.databinding.ActivityMainBinding;
import org.altusmetrum.altosdroid.ui.map.MapFragment;

import org.altusmetrum.altoslib_14.*;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

class FragmentCallbacks extends FragmentManager.FragmentLifecycleCallbacks {

    private final MainActivity activity;

    public FragmentCallbacks(MainActivity activity) {
        this.activity = activity;
    }
    public void onFragmentResumed(FragmentManager fm, Fragment f) {
        AltosDebug.debug("onFragmentResumed 0x%x %s", f.getId(), f.getTag());
        activity.setActiveFragment(f);
    }
    public void onFragmentPaused(FragmentManager fm, Fragment f) {
        AltosDebug.debug("onFragmentPaused 0x%x %s", f.getId(), f.getTag());
    }
}

class SavedState {
    long	received_time;
    int	state;
    boolean	locked;
    String	callsign;
    int	serial;
    int	flight;
    int	rssi;

    SavedState(AltosState state) {
        received_time = state.received_time;
        this.state = state.state();
        if (state.gps != null)
            locked = state.gps.locked;
        else
            locked = false;
        callsign = state.cal_data().callsign;
        serial = state.cal_data().serial;
        flight = state.cal_data().flight;
        rssi = state.rssi;
    }
}

public class MainActivity extends AppCompatActivity implements LocationListener,
                                                    ActivityCompat.OnRequestPermissionsResultCallback,
                                                    AltosUnitsListener,
                                                    AltosDroidSelectedSerialListener {

    // Actions sent to the telemetry server at startup time

    public static final String ACTION_BLUETOOTH = "org.altusmetrum.AltosDroid.BLUETOOTH";
    public static final String ACTION_USB = "org.altusmetrum.AltosDroid.USB";

    // Message types received by our Handler

    public static final int MSG_STATE           = 1;
    public static final int MSG_UPDATE_AGE      = 2;
    public static final int MSG_IDLE_MODE       = 3;
    public static final int MSG_IGNITER_STATUS  = 4;
    public static final int MSG_FILE_FAILED     = 5;
    public static final int MSG_CONFIG_DATA     = 6;

    // Intent request codes
    public static final int REQUEST_CONNECT_DEVICE = 1;
    public static final int REQUEST_ENABLE_BT      = 2;
    public static final int REQUEST_PRELOAD_MAPS   = 3;
    public static final int REQUEST_IDLE_MODE      = 5;
    public static final int REQUEST_IGNITERS       = 6;
    public static final int REQUEST_SETUP	   = 7;
    public static final int REQUEST_SELECT_TRACKER = 8;
    public static final int REQUEST_DELETE_TRACKER = 9;
    public static final int REQUEST_MANAGE_FREQ    = 10;
    public static final int REQUEST_CONFIGURE_DEVICE = 11;

    static final int MY_PERMISSION_REQUEST = 1001;

    static final String MAPS_RECEIVE = "org.altusmetrum.AltosDroid.permission.MAPS_RECEIVE";
    static final String READ_GSERVICES = "com.google.android.providers.gsf.permission.READ_GSERVICES";

    public AltosPermission perm_foreground_service = new AltosPermission(Manifest.permission.FOREGROUND_SERVICE);
    public AltosPermission perm_bluetooth_connect = new AltosPermission(Manifest.permission.BLUETOOTH_CONNECT);
    public AltosPermission perm_bluetooth_scan = new AltosPermission(Manifest.permission.BLUETOOTH_SCAN);
    public AltosPermission perm_bluetooth_admin = new AltosPermission(Manifest.permission.BLUETOOTH_ADMIN);
    public AltosPermission perm_bluetooth = new AltosPermission(Manifest.permission.BLUETOOTH);
    public AltosPermission perm_post_notifications = new AltosPermission(Manifest.permission.POST_NOTIFICATIONS);
    public AltosPermission perm_write_external_storage = new AltosPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    public AltosPermission perm_internet = new AltosPermission(Manifest.permission.INTERNET);
    public AltosPermission perm_read_gservices = new AltosPermission(READ_GSERVICES);
    public AltosPermission perm_access_fine_location = new AltosPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    public AltosPermission perm_access_coarse_location = new AltosPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
    public AltosPermission perm_access_network_state = new AltosPermission(Manifest.permission.ACCESS_NETWORK_STATE);
    public AltosPermission perm_maps_receive = new AltosPermission(MAPS_RECEIVE);

    AltosPermission[] permissions = {
        perm_foreground_service,
        perm_bluetooth_connect,
        perm_bluetooth_scan,
        perm_bluetooth_admin,
        perm_bluetooth,
        perm_post_notifications,
        perm_write_external_storage,
        perm_internet,
        perm_read_gservices,
        perm_access_fine_location,
        perm_access_coarse_location,
        perm_access_network_state,
        perm_maps_receive,
    };

    public boolean asked_permission = false;

    public static final String EXTRA_IDLE_MODE = "idle_mode";
    public static final String EXTRA_IDLE_RESULT = "idle_result";
    public static final String EXTRA_FREQUENCY = "frequency";
    public static final String EXTRA_TELEMETRY_SERVICE = "telemetry_service";
    public static final String EXTRA_TRACKERS = "trackers";
    public static final String EXTRA_TRACKERS_TITLE = "trackers_title";

    public static final String pad_name = "pad";
    public static final String flight_name = "flight";
    public static final String recover_name = "recover";
    public static final String map_name = "map";

    // Setup result bits
    public static final int SETUP_BAUD = 1;
    public static final int SETUP_UNITS = 2;
    public static final int SETUP_VOICE = 4;

    public static FragmentManager fm;
    NavController nav_controller;
    public AltosFragment active_fragment;

    ActivityMainBinding binding;
    boolean idle_mode = false;

    static final String[] preferredLocationProviders = {
        LocationManager.GPS_PROVIDER,
        LocationManager.FUSED_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
        null
    };

    public Location location = null;
    public static boolean location_has_gps = false;
    TelemetryState telemetry_state = null;
    AltosGreatCircle from_receiver;

    public static final int SELECT_AUTO = AltosDroidPreferences.SELECT_AUTO;
    int selected_serial = SELECT_AUTO;
    long selected_serial_time = 0;

    long switch_time;
    AltosState state = null;
    SavedState saved_state = null;
    Timer timer = null;

    private BluetoothAdapter mBluetoothAdapter = null;

    UsbDevice pending_usb_device = null;
    boolean start_with_usb;

    // Service
    private boolean mIsBound = false;
    private Messenger mService;
    final Messenger mMessenger = new Messenger(new IncomingHandler(this));

    @Override
    public void units_changed(boolean imperial_units) {
        update_state(null);
    }

    // The Handler that gets information back from the Telemetry Service
    static class IncomingHandler extends Handler {
        private final WeakReference<MainActivity> mAltosDroid;
        IncomingHandler(MainActivity ad) { mAltosDroid = new WeakReference<MainActivity>(ad); }

        @Override
        public void handleMessage(Message msg) {
            MainActivity ad = mAltosDroid.get();

            switch (msg.what) {
            case MSG_STATE:
                if (msg.obj == null) {
                    AltosDebug.debug("telemetry_state null!");
                    return;
                }
                ad.update_state((TelemetryState) msg.obj);
                break;
            case MSG_UPDATE_AGE:
                ad.update_age();
                break;
            case MSG_IDLE_MODE:
                ad.idle_mode = (Boolean) msg.obj;
                ad.update_state(null);
                break;
            case MSG_FILE_FAILED:
                ad.file_failed((File) msg.obj);
                break;
            }
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                AltosDebug.debug("onServiceConnected\n");
                mService = new Messenger(service);
                try {
                    Message msg = Message.obtain(null, TelemetryService.MSG_REGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    AltosDebug.debug("attempt to register telemetry service client failed\n");
                    // In this case the service has crashed before we could even do anything with it
                }
                if (pending_usb_device != null) {
                    try {
                        mService.send(Message.obtain(null, TelemetryService.MSG_OPEN_USB, pending_usb_device));
                        pending_usb_device = null;
                    } catch (RemoteException e) {
                    }
                }
                if (perm_post_notifications.have)
                    postNotification();
            }

            public void onServiceDisconnected(ComponentName className) {
                AltosDebug.debug("onServiceDisconnected\n");
                // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
                mService = null;
            }
	};

    void doBindService() {
        AltosDebug.debug("doBindService\n");
        bindService(new Intent(this, TelemetryService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        AltosDebug.debug("doUnbindService\n");
        if (mIsBound) {
            // If we have received the service, and hence registered with it, then now is the time to unregister.
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, TelemetryService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    public void selected_serial_changed(int serial, long time) {
        selected_serial_time = time;
        if (serial != selected_serial) {
            selected_serial = serial;
            update_state(null);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AltosDebug.init(this);
        AltosDebug.debug("+++ ON CREATE +++");

        AltosDroidPreferences.init(this);

        AltosDroidPreferences.register_selected_serial_listener(this);
        AltosPreferences.register_units_listener(this);

        checkPermissions();

        selected_serial = AltosDroidPreferences.selected_serial();
        selected_serial_time = AltosDroidPreferences.selected_serial_time();

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ActivityLayouts.applyEdgeToEdge(this, R.id.activity_main);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.

        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
            R.id.navigation_pad, R.id.navigation_flight, R.id.navigation_recover, R.id.navigation_map)
            .build();
        Menu menu = binding.navView.getMenu();
        MenuItem pad_item = menu.findItem(R.id.navigation_pad);

        int verticalPadding = (int) getResources().getDimension(R.dimen.nav_bar_padding);

        binding.navView.setOnApplyWindowInsetsListener(null);
        binding.navView.setPadding(0,verticalPadding,0,verticalPadding);

        fm = getSupportFragmentManager();
        NavHostFragment navHostFragment = (NavHostFragment) fm.findFragmentById(R.id.nav_host_fragment_activity_main);
        nav_controller = navHostFragment.getNavController();
        NavigationUI.setupActionBarWithNavController(this, nav_controller, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, nav_controller);

        fm.registerFragmentLifecycleCallbacks(new FragmentCallbacks(this), true);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;
        update_ui(telemetry_state, state, false);
    }

    public boolean can_bluetooth() {
        checkPermissions();
        /* Allow either old or new permission values */
        if ((perm_bluetooth_connect.have||perm_bluetooth.have) &&
            (perm_bluetooth_scan.have || perm_bluetooth_admin.have))
            return true;
        return false;
    }

    private boolean ensureBluetooth() {
        // Get local Bluetooth adapter
        if (can_bluetooth()) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            /* if there is a BT adapter and it isn't turned on, then turn it on */
            if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            }
            return true;
        }
        return false;
    }

    private boolean check_usb() {
        UsbDevice	device = AltosUsb.find_device(this, AltosLib.product_basestation);

        if (device != null) {
            Intent		i = new Intent(this, MainActivity.class);
            int		flag;

            if (android.os.Build.VERSION.SDK_INT >= 31) // android.os.Build.VERSION_CODES.S
                flag = 33554432; // PendingIntent.FLAG_MUTABLE
            else
                flag = 0;
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent("hello world", null, this, MainActivity.class), flag);

            if (AltosUsb.request_permission(this, device, pi)) {
                connectUsb(device);
            }
            start_with_usb = true;
            return true;
        }

        start_with_usb = false;

        return false;
    }

    MapFragment map_online;

    public void tell_map_permission(MapFragment map_online) {
        this.map_online = map_online;
    }

    private void noticeIntent(Intent intent) {
        /* Ok, this is pretty convenient.
         *
         * When a USB device is plugged in, and our 'hotplug'
         * intent registration fires, we get an Intent with
         * EXTRA_DEVICE set.
         *
         * When we start up and see a usb device and request
         * permission to access it, that queues a
         * PendingIntent, which has the EXTRA_DEVICE added in,
         * along with the EXTRA_PERMISSION_GRANTED field as
         * well.
         *
         * So, in both cases, we get the device name using the
         * same call. We check to see if access was granted,
         * in which case we ignore the device field and do our
         * usual startup thing.
         */

        UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true);

        AltosDebug.debug("intent %s device %s granted %s", intent, device, granted);

        if (!granted)
            device = null;

        if (device != null) {
            AltosDebug.debug("intent has usb device " + device.toString());
            connectUsb(device);
        } else {

            /* 'granted' is only false if this intent came
             * from the request_permission call and
             * permission was denied. In which case, we
             * don't want to loop forever...
             */
            if (granted) {
                AltosDebug.debug("check for a USB device at startup");
                if (check_usb())
                    return;
            }
            AltosDebug.debug("Starting by looking for bluetooth devices");
            ensureBluetooth();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        noticeIntent(getIntent());

        // Start Telemetry Service
        String	action = start_with_usb ? ACTION_USB : ACTION_BLUETOOTH;

        startService(new Intent(action, null, this, TelemetryService.class));

        doBindService();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        noticeIntent(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    void set_selected_freq_item(int item, AltosFrequency[] frequencies) {
        if (item >= 0) {
            if (item == 0) {
                Intent serverIntent = new Intent(MainActivity.this, ManageFrequenciesActivity.class);
                startActivityForResult(serverIntent, REQUEST_MANAGE_FREQ);
            } else {
                setFrequency(frequencies[item-1]);
                AltosDroidPreferences.set_selected_serial(SELECT_AUTO);
            }
        }
    }

    Tracker[] current_trackers(boolean include_auto) {
        int	num_trackers = 0;

        if (include_auto)
            num_trackers++;

        if (telemetry_state != null) {
            for (AltosState s : telemetry_state.values()) {
                num_trackers++;
            }
        }

        Tracker[] trackers = new Tracker[num_trackers];

        int n = 0;

        if (include_auto)
            trackers[n++] = new Tracker(SELECT_AUTO, "auto", 0.0);

        if (telemetry_state != null) {
            for (AltosState s : telemetry_state.values())
                trackers[n++] = new Tracker(s);
        }

        Arrays.sort(trackers);
        return trackers;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        int itemId = item.getItemId();
        if (itemId == R.id.connect_scan) {
            if (!ensureBluetooth()) {
                AlertDialog.Builder builder_nobt = new AlertDialog.Builder(this);
                builder_nobt.setTitle(R.string.bt_unavailable);
                builder_nobt.setMessage(R.string.bt_denied);
                builder_nobt.setNegativeButton(android.R.string.ok, null);
                builder_nobt.setIconAttribute(android.R.attr.alertDialogIcon);
                builder_nobt.show();
            } else {
                // Launch the DeviceListActivity to see devices and do scan
                serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            }
            return true;
        }
        if (itemId == R.id.disconnect) {
            // Disconnect the device
            disconnectDevice(false);
            return true;
        }
        if (itemId == R.id.quit) {
            //AltosDebug.debug("R.id.quit");
            disconnectDevice(true);
            finish();
            return true;
        }
        if (itemId == R.id.setup) {
            // R.id.setup:
            serverIntent = new Intent(this, SetupActivity.class);
            startActivityForResult(serverIntent, REQUEST_SETUP);
            return true;
        }

        if (itemId == R.id.select_freq) {
            // R.id.select_freq:
            // Set the TBT radio frequency
            final AltosFrequency[] frequencies = AltosPreferences.common_frequencies();
            String[] frequency_strings = new String[frequencies.length + 1];
            frequency_strings[0] = "Manage Frequencies";
            int checkedItem = -1;
            for (int i = 0; i < frequencies.length; i++) {
                if (telemetry_state != null && frequencies[i].frequency == telemetry_state.frequency)
                    checkedItem = i + 1;
                frequency_strings[i+1] = frequencies[i].toShortString();
            }

            AlertDialog.Builder builder_freq = new AlertDialog.Builder(this);
            builder_freq.setTitle("Select Frequency");
            builder_freq.setSingleChoiceItems(frequency_strings,
                                              checkedItem,
                                              new DialogInterface.OnClickListener() {
                                                  public void onClick(DialogInterface dialog, int item) {
                                                      set_selected_freq_item(item, frequencies);
                                                      dialog.dismiss();
                                                  }
                                              });
            AlertDialog alert_freq = builder_freq.create();
            alert_freq.show();

            return true;
        }
        if (itemId == R.id.select_tracker) {
            start_select_tracker(current_trackers(true));
            return true;
        }
        if (itemId == R.id.delete_track) {
            start_select_tracker(current_trackers(false), R.string.delete_track, REQUEST_DELETE_TRACKER);
            return true;
        }
        if (itemId == R.id.load_maps) {
            start_preload_maps();
            return true;
        }
        if (itemId == R.id.idle_mode) {
              serverIntent = new Intent(this, IdleModeActivity.class);
              serverIntent.putExtra(EXTRA_IDLE_MODE, idle_mode);
              if (telemetry_state != null && telemetry_state.frequency != AltosLib.MISSING)
                  serverIntent.putExtra(EXTRA_FREQUENCY, telemetry_state.frequency);
              startActivityForResult(serverIntent, REQUEST_IDLE_MODE);
              return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        AltosDebug.debug("+++ ON ACTIVITY RESULT request %d result %d +++", requestCode, resultCode);
        switch(requestCode) {
        case REQUEST_CONNECT_DEVICE:
            if (resultCode == Activity.RESULT_OK) {
                String name = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_NAME);
                String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                AltosDebug.debug("connect %s %s", name, address);
                connectDevice(name, address);
            }
            break;
        case REQUEST_IDLE_MODE:
            if (resultCode == Activity.RESULT_OK)
                idle_mode(data);
            break;
        case REQUEST_IGNITERS:
            break;
        case REQUEST_SETUP:
            if (resultCode == Activity.RESULT_OK)
                note_setup_changes(data);
            break;

        case REQUEST_SELECT_TRACKER:
            if (resultCode == Activity.RESULT_OK)
                select_tracker(data);
            break;
        case REQUEST_DELETE_TRACKER:
            if (resultCode == Activity.RESULT_OK)
                delete_track(data);
            break;

        default:
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void note_setup_changes(Intent data) {
        int changes = data.getIntExtra(SetupActivity.EXTRA_SETUP_CHANGES, 0);

        AltosDebug.debug("note_setup_changes changes %d\n", changes);

        if ((changes & SETUP_BAUD) != 0) {
            try {
                mService.send(Message.obtain(null, TelemetryService.MSG_SETBAUD,
                    AltosPreferences.telemetry_rate(1)));
            } catch (RemoteException re) {
            }
        }
        if ((changes & SETUP_UNITS) != 0) {
            /* nothing to do here */
        }
        set_switch_time();
    }

    private void connectUsb(UsbDevice device) {
        if (mService == null)
            pending_usb_device = device;
        else {
            // Attempt to connect to the device
            try {
                mService.send(Message.obtain(null, TelemetryService.MSG_OPEN_USB, device));
                AltosDebug.debug("Sent OPEN_USB message");
            } catch (RemoteException e) {
                AltosDebug.debug("connect device message failed");
            }
        }
    }

    private void enable_location_updates(boolean do_update) {
        // Listen for GPS and Network position updates
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        if (locationManager != null)
        {
            List<String> locationProviders = locationManager.getAllProviders();

            String selectedLocationProvider = null;

            /* Record whether we have GPS at all */
            for (String locationProvider : locationProviders)
                if (locationProvider.equals(LocationManager.GPS_PROVIDER)) {
                    location_has_gps = true;
                    break;
                }

            /* Now go find the best of the available location providers */
            for (String pref : preferredLocationProviders) {
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
                    location = locationManager.getLastKnownLocation(selectedLocationProvider);
                } catch (SecurityException|IllegalArgumentException e) {
                }
            }
        }

        if (do_update)
            update_ui(telemetry_state, state, true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] new_permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, new_permissions, grantResults);
        if (requestCode == MY_PERMISSION_REQUEST) {
            for (int i = 0; i < grantResults.length; i++) {
                for (int j = 0; j < permissions.length; j++) {
                    if (new_permissions[i].equals(permissions[j].name)) {
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                            AltosDebug.debug("permission %s now granted\n", permissions[j].name);
                            permissions[j].have = true;
                            if (permissions[j] == perm_access_fine_location) {
                                enable_location_updates(true);
                                if (map_online != null)
                                    map_online.position_permission();
                            } else if (permissions[j] == perm_post_notifications) {
                                postNotification();
                            }
                        } else {
                            AltosDebug.debug("permission %s still denied\n", permissions[j].name);
                            permissions[j].have = false;
                        }
                    }
                }
            }
        }
    }

    private void checkPermissions() {
        if (!asked_permission) {
            asked_permission = true;
            int missing = 0;
            for (int i = 0; i < permissions.length; i++) {
                if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(permissions[i].name) == PackageManager.PERMISSION_GRANTED) {
                    AltosDebug.debug("permission %s already granted\n", permissions[i].name);
                    permissions[i].have = true;
                } else {
                    AltosDebug.debug("permission %s not yet granted\n", permissions[i].name);
                    permissions[i].have = false;
                    missing++;
                }
            }
            if (missing > 0)
            {
                String[] new_permissions = new String[missing];
                int count = 0;
                for (int i = 0; i < permissions.length; i++) {
                    if (!permissions[i].have) {
                        AltosDebug.debug("Requesting permission %s\n", permissions[i].name);
                        new_permissions[count++] = permissions[i].name;
                    }
                }
                requestPermissions(new_permissions, MY_PERMISSION_REQUEST);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        checkPermissions();
        if (perm_access_fine_location.have)
            enable_location_updates(false);
    }

    @Override
    public void onPause() {
        AltosDebug.debug("- ON PAUSE -");

        super.onPause();

        // Stop listening for location updates
        if (perm_access_fine_location.have)
            ((LocationManager) getSystemService(Context.LOCATION_SERVICE)).removeUpdates(this);
    }

    @Override
    public void onStop() {
        AltosDebug.debug("-- ON STOP --");

        super.onStop();
    }

    @Override
    public void onDestroy() {
        AltosDebug.debug("--- ON DESTROY ---");

        super.onDestroy();

        //saved_state = null;

        doUnbindService();
        stop_timer();
    }

    private void connectDevice(String name, String address) {
        try {
            DeviceAddress deviceAddress = new DeviceAddress(address, name);
            mService.send(Message.obtain(null, TelemetryService.MSG_CONNECT, deviceAddress));
        } catch (RemoteException e) {
            AltosDebug.error("connectDevice(): %s", e);
        }
    }

    private void disconnectDevice(boolean remember) {
        try {
            mService.send(Message.obtain(null, TelemetryService.MSG_DISCONNECT, remember));
        } catch (RemoteException e) {
            AltosDebug.error("disconnectDevice(): %s", e.getMessage());
        }
    }

    private void postNotification() {
        try {
            mService.send(Message.obtain(null, TelemetryService.MSG_POST_NOTIFICATION));
        } catch (RemoteException e) {
            AltosDebug.error("postNotification() : %s", e.getMessage());
        }
    }

    void setActiveFragment(Fragment fragment) {
        if (!(fragment instanceof AltosFragment)) {
            return;
        }
        active_fragment = (AltosFragment) fragment;
        active_fragment.set_altos_droid(this);
        if (mService != null) {
            Message msg = Message.obtain(null, TelemetryService.MSG_SET_FRAGMENT_NAME, active_fragment.name());
            try {
                mService.send(msg);
            } catch (RemoteException re) {
            }
        }
        update_state(null);
    }

    private void idle_frequency(double frequency) {
        double telem_frequency = AltosLib.MISSING;

        if (telemetry_state != null)
            telem_frequency = telemetry_state.frequency;

        if (frequency != AltosLib.MISSING && frequency != 0.0 && frequency != telem_frequency)
            setFrequency(frequency);
    }

    private void idle_mode(Intent data) {
        int type = data.getIntExtra(IdleModeActivity.EXTRA_IDLE_RESULT, -1);
        double frequency = data.getDoubleExtra(IdleModeActivity.EXTRA_IDLE_FREQUENCY, AltosLib.MISSING);
        Message msg;
        Intent serverIntent;

        AltosDebug.debug("intent idle_mode %d", type);
        switch (type) {
        case IdleModeActivity.IDLE_MODE_CONNECT:
            idle_frequency(frequency);
            msg = Message.obtain(null, TelemetryService.MSG_MONITOR_IDLE_START);
            try {
                mService.send(msg);
            } catch (RemoteException re) {
            }
            break;
        case IdleModeActivity.IDLE_MODE_DISCONNECT:
            msg = Message.obtain(null, TelemetryService.MSG_MONITOR_IDLE_STOP);
            try {
                mService.send(msg);
            } catch (RemoteException re) {
            }
            break;
        case IdleModeActivity.IDLE_MODE_REBOOT:
            idle_frequency(frequency);
            msg = Message.obtain(null, TelemetryService.MSG_REBOOT);
            try {
                mService.send(msg);
            } catch (RemoteException re) {
            }
            break;
        case IdleModeActivity.IDLE_MODE_IGNITERS:
            idle_frequency(frequency);
            serverIntent = new Intent(this, IgniterActivity.class);
            startActivityForResult(serverIntent, REQUEST_IGNITERS);
            break;
        case IdleModeActivity.IDLE_MODE_CONFIGURE:
            idle_frequency(frequency);
            serverIntent = new Intent(this, ConfigureDeviceActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONFIGURE_DEVICE);
            break;
        }
    }

    boolean fail_shown;

    private void file_failed(File file) {
        if (!fail_shown) {
            fail_shown = true;
            AlertDialog fail = new AlertDialog.Builder(this).create();
            fail.setTitle("Failed to Create Log File");
            fail.setMessage(file.getPath());
            fail.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                           new DialogInterface.OnClickListener() {
                               public void onClick(DialogInterface dialog, int which) {
                                   dialog.dismiss();
                               }
                           });
            fail.show();
        }
    }

    boolean same_string(String a, String b) {
        if (a == null) {
            return b == null;
        } else {
            if (b == null)
                return false;
            return a.equals(b);
        }
    }

    void update_ui(TelemetryState new_telemetry_state, AltosState new_state, boolean quiet) {
        //AltosDebug.debug("update_ui");
        if (new_state != null)
            this.state = new_state;
        if (new_telemetry_state != null)
            this.telemetry_state = new_telemetry_state;
        int prev_state = saved_state == null ? AltosLib.ao_flight_invalid : saved_state.state;
        int active_menu_id = nav_controller.getCurrentDestination().getId();
        int next_menu_id = active_menu_id;
        from_receiver = null;

        if (state != null) {
            // compute the next fragment to switch to
            if (state.state() == AltosLib.ao_flight_stateless) {
                boolean	prev_locked = false;
                boolean locked = false;

                if(state.gps != null)
                    locked = state.gps.locked;
                if (saved_state != null)
                    prev_locked = saved_state.locked;
                if (prev_locked != locked) {
                    if (locked) {
                        if (active_menu_id == R.id.navigation_pad || active_menu_id == -1)
                            next_menu_id = R.id.navigation_flight;
                    } else {
                        if (active_menu_id == R.id.navigation_flight || active_menu_id == -1)
                            next_menu_id = R.id.navigation_pad;
                    }
                }
            } else {
                if (prev_state != state.state()) {

                    switch (state.state()) {
                    case AltosLib.ao_flight_boost:
                    case AltosLib.ao_flight_fast:
                    case AltosLib.ao_flight_coast:
                    case AltosLib.ao_flight_drogue:
                    case AltosLib.ao_flight_main:
                        if (active_menu_id == R.id.navigation_pad || active_menu_id == -1)
                            next_menu_id = R.id.navigation_flight;
                        break;
                    case AltosLib.ao_flight_landed:
                        if (active_menu_id == R.id.navigation_flight || active_menu_id == -1)
                            next_menu_id = R.id.navigation_recover;
                        break;
                    }
                }
            }

            if (next_menu_id != -1 && next_menu_id != active_menu_id) {
                // Remove the current fragment so it doesn't end up in the back stack
                nav_controller.popBackStack(active_menu_id, true);
                nav_controller.navigate(next_menu_id);
            }

            if (location != null && state.gps != null && state.gps.locked) {
                double altitude = 0;
                if (location.hasAltitude())
                    altitude = location.getAltitude();
                from_receiver = new AltosGreatCircle(location.getLatitude(),
                                                     location.getLongitude(),
                                                     altitude,
                                                     state.gps.lat,
                                                     state.gps.lon,
                                                     state.gps.alt);
            }
            if (saved_state == null || !same_string(saved_state.callsign, state.cal_data().callsign)) {
                binding.callsignValue.setText(state.cal_data().callsign);
            }
            if (saved_state == null || state.cal_data().serial != saved_state.serial) {
                if (state.cal_data().serial == AltosLib.MISSING)
                    binding.serialValue.setText("");
                else
                    binding.serialValue.setText(String.format(Locale.getDefault(), "%d", state.cal_data().serial));
            }
            if (saved_state == null || state.cal_data().flight != saved_state.flight) {
                if (state.cal_data().flight == AltosLib.MISSING)
                    binding.flightValue.setText("");
                else
                    binding.flightValue.setText(String.format(Locale.getDefault(), "%d", state.cal_data().flight));
            }

            if (saved_state == null || state.rssi != saved_state.rssi) {
                if (state.rssi == AltosLib.MISSING)
                    binding.rssiValue.setText("");
                else
                    binding.rssiValue.setText(String.format(Locale.getDefault(), "%d", state.rssi));
            }
            saved_state = new SavedState(state);
        }

        if (active_fragment != null) {
            active_fragment.show(telemetry_state, state, from_receiver, location);
        }
    }

    int auto_select_tracker() {

        int earliest_serial = SELECT_AUTO;
        long earliest_time = 0;

        for (AltosState s : telemetry_state.values()) {
            if (s.received_time != AltosLib.MISSING && s.cal_data() != null) {
                if (s.received_time >= selected_serial_time) {
                    if (earliest_serial == SELECT_AUTO || s.received_time < earliest_time) {
                        earliest_serial = s.cal_data().serial;
                        earliest_time = s.received_time;
                    }
                }
            }
        }
        return earliest_serial;
    }

    public void update_state(TelemetryState new_telemetry_state) {
        if (new_telemetry_state != null)
            telemetry_state = new_telemetry_state;

        if (telemetry_state == null)
            return;

        if (selected_serial == SELECT_AUTO) {
            selected_serial = auto_select_tracker();
            if (selected_serial != SELECT_AUTO)
                AltosDroidPreferences.set_selected_serial(selected_serial);
        }

        if (selected_serial != SELECT_AUTO && telemetry_state.get(selected_serial) == null)
            selected_serial = SELECT_AUTO;

        int shown_serial = selected_serial;

        if (telemetry_state.idle_mode)
            shown_serial = telemetry_state.latest_serial;

        AltosState	state = telemetry_state.get(shown_serial);
        update_title(telemetry_state, state);
        update_ui(telemetry_state, state, telemetry_state.quiet);

        start_timer();
    }

    void set_screen_on(int age) {
        if (age < 60)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    int state_age(long received_time) {
        return (int) ((System.currentTimeMillis() - received_time + 500) / 1000);
    }

    static String age_string(int age) {
        String	text;
        if (age < 60)
            text = String.format(Locale.getDefault(), "%ds", age);
        else if (age < 60 * 60)
            text = String.format(Locale.getDefault(), "%dm", age / 60);
        else if (age < 60 * 60 * 24)
            text = String.format(Locale.getDefault(), "%dh", age / (60 * 60));
        else
            text = String.format(Locale.getDefault(), "%dd", age / (24 * 60 * 60));
        return text;
    }
    void update_age() {
        //AltosDebug.debug("update_age");
        if (saved_state != null) {
            int age = state_age(saved_state.received_time);

            int style = R.style.AgeOk;

            if (age >= 30)
                style = R.style.AgeError;
            else if (age >= 10)
                style = R.style.AgeWarn;

            binding.ageValue.setTextAppearance(style);

            set_screen_on(age);

            binding.ageValue.setText(age_string(age));
        }
    }

    void timer_tick() {
        try {
            mMessenger.send(Message.obtain(null, MSG_UPDATE_AGE));
        } catch (RemoteException e) {
        }
    }

    void start_timer() {
        if (timer == null) {
            timer = new Timer();
            timer.schedule(new TimerTask(){ public void run() {timer_tick();}}, 1000L, 1000L);
        }
    }

    void stop_timer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    void set_switch_time() {
        switch_time = System.currentTimeMillis();
        selected_serial = 0;
    }

    public void setTitle(String title) {
        super.setTitle(title);
        getSupportActionBar().setTitle(title);
    }

    void update_title(TelemetryState telemetry_state, AltosState state) {
        String title;
        switch (telemetry_state.connect) {
        case TelemetryState.CONNECT_CONNECTED:
            if (telemetry_state.config != null) {
                title= String.format(Locale.getDefault(), "S/N %d %6.3f MHz", telemetry_state.config.serial,
                                     telemetry_state.frequency);
                if (telemetry_state.telemetry_rate != AltosLib.ao_telemetry_rate_38400)
                    title = title.concat(String.format(Locale.getDefault(), " %d bps",
                                                       AltosLib.ao_telemetry_rate_values[telemetry_state.telemetry_rate]));
                if (idle_mode)
                    title = title.concat(" (idle)");
                else if (selected_serial == SELECT_AUTO)
                    title = title.concat(" (auto)");
                else
                    title = title.concat(String.format(Locale.getDefault(), " (%d)", selected_serial));
            } else {
                title = getString(R.string.title_connected_to);
            }
            break;
        case TelemetryState.CONNECT_CONNECTING:
            String address_name;
            if (telemetry_state.address != null)
                address_name = telemetry_state.address.name;
            else
                address_name = getString(R.string.unknown_value);
            title = String.format(Locale.getDefault(), "%s %s", getString(R.string.connecting_to), address_name);
            break;
        case TelemetryState.CONNECT_DISCONNECTED:
        case TelemetryState.CONNECT_NONE:
        default:
            title = getString(R.string.title_not_connected);
            break;
        }
        setTitle(title);
    }

    void setFrequency(double freq) {
        try {
            mService.send(Message.obtain(null, TelemetryService.MSG_SETFREQUENCY, freq));
            set_switch_time();
        } catch (RemoteException e) {
        }
    }

    void setFrequency(AltosFrequency frequency) {
        setFrequency (frequency.frequency);
    }

    void setBaud(int baud) {
        try {
            mService.send(Message.obtain(null, TelemetryService.MSG_SETBAUD, baud));
            set_switch_time();
        } catch (RemoteException e) {
        }
    }

    void setBaud(String baud) {
        try {
            int	value = Integer.parseInt(baud);
            int	rate = AltosLib.ao_telemetry_rate_38400;
            switch (value) {
            case 2400:
                rate = AltosLib.ao_telemetry_rate_2400;
                break;
            case 9600:
                rate = AltosLib.ao_telemetry_rate_9600;
                break;
            case 38400:
                rate = AltosLib.ao_telemetry_rate_38400;
                break;
            }
            setBaud(rate);
        } catch (NumberFormatException e) {
        }
    }

    void select_tracker(int serial, double frequency) {

        AltosDebug.debug("select tracker %d %7.3f\n", serial, frequency);

        if (serial != SELECT_AUTO) {
            if (frequency != 0.0 && frequency != AltosLib.MISSING)
                setFrequency(frequency);
        }

        if (active_fragment != null)
            active_fragment.select_tracker(serial);

        /* This will eventually call update_state() */
        AltosDroidPreferences.set_selected_serial(serial);
    }

    void select_tracker(Intent data) {
        int serial = data.getIntExtra(SelectTrackerActivity.EXTRA_SERIAL_NUMBER, 0);
        double frequency = data.getDoubleExtra(SelectTrackerActivity.EXTRA_FREQUENCY, 0.0);
        select_tracker(serial, frequency);
    }

    void delete_track(int serial) {
        try {
            mService.send(Message.obtain(null, TelemetryService.MSG_DELETE_SERIAL, serial));
        } catch (Exception ex) {
        }
    }

    void delete_track(Intent data) {
        int serial = data.getIntExtra(SelectTrackerActivity.EXTRA_SERIAL_NUMBER, 0);
        if (serial != 0)
            delete_track(serial);
    }

    void start_select_tracker(Tracker[] select_trackers, int title_id, int request) {
        Intent intent = new Intent(this, SelectTrackerActivity.class);
        AltosDebug.debug("put title id 0x%x %s", title_id, getResources().getString(title_id));
        intent.putExtra(EXTRA_TRACKERS_TITLE, title_id);
        if (select_trackers != null) {
            ArrayList<Tracker> tracker_array = new ArrayList<Tracker>(Arrays.asList(select_trackers));
            intent.putParcelableArrayListExtra(EXTRA_TRACKERS, tracker_array);
        } else {
            intent.putExtra(EXTRA_TRACKERS, (Parcelable[]) null);
        }
        startActivityForResult(intent, request);
    }

    void start_select_tracker(Tracker[] select_trackers) {
        start_select_tracker(select_trackers, R.string.select_tracker, REQUEST_SELECT_TRACKER);
    }

    void start_preload_maps() {
        Intent intent = new Intent(this, PreloadMapActivity.class);
        double latitude = AltosLib.MISSING;
        double longitude = AltosLib.MISSING;
        if (location != null) {
            latitude = location.getLatitude();
            longitude = location.getLongitude();
        }
        intent.putExtra(PreloadMapActivity.EXTRA_LATITUDE, latitude);
        intent.putExtra(PreloadMapActivity.EXTRA_LONGITUDE, longitude);
        startActivityForResult(intent, REQUEST_PRELOAD_MAPS);
    }
    public void touch_trackers(Integer[] serials) {
        Tracker[] my_trackers = new Tracker[serials.length];

        for (int i = 0; i < serials.length; i++) {
            AltosState	s = telemetry_state.get(serials[i]);
            my_trackers[i] = new Tracker(s);
        }
        start_select_tracker(my_trackers);
    }

    static String direction(AltosGreatCircle from_receiver,
                            Location receiver) {
        if (from_receiver == null)
            return null;

        if (receiver == null)
            return null;

        if (!receiver.hasBearing())
            return null;

        float	bearing = receiver.getBearing();
        float	heading = (float) from_receiver.bearing - bearing;

        while (heading <= -180.0f)
            heading += 360.0f;
        while (heading > 180.0f)
            heading -= 360.0f;

        int iheading = (int) (heading + 0.5f);

        if (-1 < iheading && iheading < 1)
            return "ahead";
        else if (iheading < -179 || 179 < iheading)
            return "backwards";
        else if (iheading < 0)
            return String.format(Locale.getDefault(), "left %d°", -iheading);
        else
            return String.format(Locale.getDefault(), "right %d°", iheading);
    }

    public static void draw_text(Context context, Canvas canvas, String text, float x, float y, int size_id, Paint.Align align) {
        float size = context.getResources().getDimension(size_id);
        Paint paint = new Paint();
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setAntiAlias(true);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        float offset = size / 40;
        paint.setColor(Color.BLACK);
        for (float yoffset = -offset; yoffset <= offset; yoffset += offset)
            for (float xoffset = -offset; xoffset <= offset; xoffset += offset)
                canvas.drawText(text, x+xoffset, y+yoffset, paint);
        paint.setColor(Color.WHITE);
        canvas.drawText(text, x, y, paint);
    }
    public static void draw_text(Context context, Canvas canvas, String text, float x, float y, Paint.Align align) {
        draw_text(context, canvas, text, x, y, R.dimen.map_text_size, align);
    }

    public static void measure_text(Context context, String text, int size_id, Rect bounds) {
        float size = context.getResources().getDimension(size_id);
        Paint paint = new Paint();
        paint.setTextSize(size);
        paint.setAntiAlias(true);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.getTextBounds(text, 0, text.length(), bounds);
    }

}
