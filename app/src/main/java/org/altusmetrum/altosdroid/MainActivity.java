package org.altusmetrum.altosdroid;

import android.Manifest;
import android.app.AlertDialog;
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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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

public class    MainActivity extends AppCompatActivity implements LocationListener,
                                                       ActivityCompat.OnRequestPermissionsResultCallback, AltosUnitsListener {

    // Actions sent to the telemetry server at startup time

    public static final String ACTION_BLUETOOTH = "org.altusmetrum.AltosDroid.BLUETOOTH";
    public static final String ACTION_USB = "org.altusmetrum.AltosDroid.USB";

    // Message types received by our Handler

    public static final int MSG_STATE           = 1;
    public static final int MSG_UPDATE_AGE      = 2;
    public static final int	MSG_IDLE_MODE	    = 3;
    public static final int MSG_IGNITER_STATUS  = 4;
    public static final int MSG_FILE_FAILED     = 5;

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

    static final int MY_PERMISSION_REQUEST = 1001;

    public boolean have_location_permission = false;
    public boolean have_storage_permission = false;
    public boolean have_bluetooth_permission = false;
    public boolean have_bluetooth_connect_permission = false;
    public boolean have_bluetooth_scan_permission = false;
    public boolean have_notification_permission = false;
    public boolean asked_permission = false;

    static final String BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT";
    static final String BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN";

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
    public static final int SETUP_MAP_SOURCE = 4;
    public static final int SETUP_MAP_TYPE = 8;
    public static final int SETUP_FONT_SIZE = 16;

    public static FragmentManager fm;
    NavController nav_controller;
    public AltosFragment active_fragment;

    ActivityMainBinding binding;
    boolean idle_mode = false;
    AltosVoice altos_voice;

    public Location location = null;
    TelemetryState telemetry_state = null;
    Tracker[] trackers;
    double telem_frequency = 434.550;
    double selected_frequency = AltosLib.MISSING;
    int selected_serial = 0;
    long switch_time;
    AltosState state = null;
    SavedState saved_state = null;
    Timer timer = null;

    boolean registered_units_listener;

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
                if (have_notification_permission)
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AltosDebug.init(this);
        AltosDebug.debug("+++ ON CREATE +++");

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

        fm = getSupportFragmentManager();
        NavHostFragment navHostFragment = (NavHostFragment) fm.findFragmentById(R.id.nav_host_fragment_activity_main);
        nav_controller = navHostFragment.getNavController();
        NavigationUI.setupActionBarWithNavController(this, nav_controller, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, nav_controller);

        fm.registerFragmentLifecycleCallbacks(new FragmentCallbacks(this), true);
    }

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;
        update_ui(telemetry_state, state, false);
    }

    private void ensureBluetooth() {
        // Get local Bluetooth adapter
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            /* if there is a BT adapter and it isn't turned on, then turn it on */
            if (mBluetoothAdapter != null && !mBluetoothAdapter.isEnabled()) {
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    MapFragment map_online;

    public void tell_map_permission(MapFragment map_online) {
        this.map_online = map_online;
    }

    private void noticeIntent(Intent intent) {
        ensureBluetooth();
    }

    @Override
    public void onStart() {
        super.onStart();
        noticeIntent(getIntent());

        // Start Telemetry Service
        String	action = start_with_usb ? ACTION_USB : ACTION_BLUETOOTH;

        startService(new Intent(action, null, this, TelemetryService.class));

        doBindService();

        if (altos_voice == null)
            altos_voice = new AltosVoice(this);
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
                selected_frequency = frequencies[item-1].frequency;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        int itemId = item.getItemId();
        if (itemId == R.id.connect_scan) {
            ensureBluetooth();
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
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
            for (int i = 0; i < frequencies.length; i++)
                frequency_strings[i+1] = frequencies[i].toString();

            AlertDialog.Builder builder_freq = new AlertDialog.Builder(this);
            builder_freq.setTitle("Select Frequency");
            builder_freq.setItems(frequency_strings,
                                  new DialogInterface.OnClickListener() {
                                      public void onClick(DialogInterface dialog, int item) {
                                          set_selected_freq_item(item, frequencies);
                                      }
                                  });
            AlertDialog alert_freq = builder_freq.create();
            alert_freq.show();

            return true;
        }
        if (itemId == R.id.select_tracker) {
            start_select_tracker(trackers);
            return true;
        }
        if (itemId == R.id.delete_track) {
            if (trackers != null && trackers.length > 0)
                start_select_tracker(trackers, R.string.delete_track, REQUEST_DELETE_TRACKER);
            return true;
        }
        if (itemId == R.id.load_maps) {
            start_preload_maps();
            return true;
        }
        if (itemId == R.id.idle_mode) {
              serverIntent = new Intent(this, IdleModeActivity.class);
              serverIntent.putExtra(EXTRA_IDLE_MODE, idle_mode);
              serverIntent.putExtra(EXTRA_FREQUENCY, telem_frequency);
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
        if ((changes & SETUP_MAP_SOURCE) != 0) {
            /* nothing to do here */
        }
        if ((changes & SETUP_MAP_TYPE) != 0) {
            /* nothing to do here */
        }
        set_switch_time();
        if ((changes & SETUP_FONT_SIZE) != 0) {
            AltosDebug.debug(" ==== Recreate to switch font sizes ==== ");
            //finish();
            //startActivity(getIntent());
        }
    }

    private void enable_location_updates(boolean do_update) {
        // Listen for GPS and Network position updates
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        if (locationManager != null)
        {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } catch (SecurityException e) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, this);
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        }

        if (do_update)
            update_ui(telemetry_state, state, true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSION_REQUEST) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        have_location_permission = true;
                        enable_location_updates(true);
                        if (map_online != null)
                            map_online.position_permission();
                    }
                    if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        have_storage_permission = true;
                    }
                    if (permissions[i].equals(Manifest.permission.BLUETOOTH)) {
                        have_bluetooth_permission = true;
                    }
                    if (permissions[i].equals(BLUETOOTH_CONNECT)) {
                        have_bluetooth_connect_permission = true;
                    }
                    if (permissions[i].equals(BLUETOOTH_SCAN)) {
                        have_bluetooth_scan_permission = true;
                    }
                    if (permissions[i].equals(Manifest.permission.POST_NOTIFICATIONS)) {
                        have_notification_permission = true;
                        postNotification();
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!asked_permission) {
            asked_permission = true;
            if (ActivityCompat.checkSelfPermission(this,
                                                   Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)
            {
                have_location_permission = true;
            }
            if (ActivityCompat.checkSelfPermission(this,
                                                   Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED)
            {
                have_storage_permission = true;
            }
            if (ActivityCompat.checkSelfPermission(this,
                                                   Manifest.permission.BLUETOOTH)
                == PackageManager.PERMISSION_GRANTED)
            {
                have_bluetooth_permission = true;
            }
            if (ActivityCompat.checkSelfPermission(this,
                                                   BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED)
            {
                have_bluetooth_connect_permission = true;
            }
            if (ActivityCompat.checkSelfPermission(this,
                                                   BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED)
            {
                have_bluetooth_scan_permission = true;
            }
            if (ActivityCompat.checkSelfPermission(this,
                                                   Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                have_notification_permission = true;
            }
            int count = 0;
            if (!have_location_permission)
                count += 1;
            if (!have_storage_permission)
                count += 1;
            if (!have_bluetooth_permission)
                count += 1;
            if (!have_bluetooth_connect_permission)
                count += 1;
            if (!have_bluetooth_scan_permission)
                count += 1;
            if (!have_notification_permission)
                count += 1;
            if (count > 0)
            {
                String[] permissions = new String[count];
                int i = 0;
                if (!have_location_permission)
                    permissions[i++] = Manifest.permission.ACCESS_FINE_LOCATION;
                if (!have_storage_permission)
                    permissions[i++] = Manifest.permission.WRITE_EXTERNAL_STORAGE;
                if (!have_bluetooth_permission)
                    permissions[i++] = Manifest.permission.BLUETOOTH;
                if (!have_bluetooth_connect_permission)
                    permissions[i++] = BLUETOOTH_CONNECT;
                if (!have_bluetooth_scan_permission)
                    permissions[i++] = BLUETOOTH_SCAN;
                if (!have_notification_permission)
                    permissions[i++] = Manifest.permission.POST_NOTIFICATIONS;
                ActivityCompat.requestPermissions(this, permissions, MY_PERMISSION_REQUEST);
            }
        }
        if (have_location_permission)
            enable_location_updates(false);
    }

    @Override
    public void onPause() {
        AltosDebug.debug("- ON PAUSE -");

        super.onPause();

        // Stop listening for location updates
        if (have_location_permission)
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
        if (altos_voice != null) {
            altos_voice.stop();
            altos_voice = null;
        }
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
        update_state(null);
    }

    private void idle_mode(Intent data) {
        int type = data.getIntExtra(IdleModeActivity.EXTRA_IDLE_RESULT, -1);
        Message msg;

        AltosDebug.debug("intent idle_mode %d", type);
        switch (type) {
            case IdleModeActivity.IDLE_MODE_CONNECT:
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
                msg = Message.obtain(null, TelemetryService.MSG_REBOOT);
                try {
                    mService.send(msg);
                } catch (RemoteException re) {
                }
                break;
            case IdleModeActivity.IDLE_MODE_IGNITERS:
                Intent serverIntent = new Intent(this, IgniterActivity.class);
                startActivityForResult(serverIntent, REQUEST_IGNITERS);
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

    void update_ui(TelemetryState telem_state, AltosState state, boolean quiet) {
        //AltosDebug.debug("update_ui");
        this.state = state;
        this.telemetry_state = telem_state;
        int prev_state = saved_state == null ? AltosLib.ao_flight_invalid : saved_state.state;
        int active_menu_id = nav_controller.getCurrentDestination().getId();
        int next_menu_id = active_menu_id;
        AltosGreatCircle from_receiver = null;

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
            active_fragment.show(telem_state, state, from_receiver, location);
        }

        if (altos_voice != null) {
            altos_voice.tell(telem_state, state, from_receiver, location, active_fragment, quiet, getResources());
        }
    }

    public void update_state(TelemetryState new_telemetry_state) {
        if (new_telemetry_state != null)
            telemetry_state = new_telemetry_state;

        if (telemetry_state == null)
            return;

        if (selected_frequency != AltosLib.MISSING) {
            AltosState selected_state = telemetry_state.get(selected_serial);
            AltosState latest_state = telemetry_state.get(telemetry_state.latest_serial);

            if (selected_state != null && selected_state.frequency == selected_frequency) {
                selected_frequency = AltosLib.MISSING;
            } else if ((selected_state == null || selected_state.frequency != selected_frequency) &&
                       (latest_state != null && latest_state.frequency == selected_frequency))
            {
                selected_frequency = AltosLib.MISSING;
                selected_serial = telemetry_state.latest_serial;
            }
        }

        if (!telemetry_state.containsKey(selected_serial)) {
            selected_serial = telemetry_state.latest_serial;
        }

        int shown_serial = selected_serial;

        if (telemetry_state.idle_mode)
            shown_serial = telemetry_state.latest_serial;

        if (!registered_units_listener) {
            registered_units_listener = true;
            AltosPreferences.register_units_listener(this);
        }

        int	num_trackers = 0;

        for (AltosState s : telemetry_state.values()) {
            num_trackers++;
        }

        trackers = new Tracker[num_trackers + 1];

        int n = 0;
        trackers[n++] = new Tracker(0, "auto", 0.0);

        for (AltosState s : telemetry_state.values())
            trackers[n++] = new Tracker(s);

        Arrays.sort(trackers);

        if (telemetry_state.frequency != AltosLib.MISSING)
            telem_frequency = telemetry_state.frequency;

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
        String state_name = null;
        if (state != null && state.state() != AltosLib.ao_flight_stateless) {
            state_name = state.state_name();
        }
        String title;
        switch (telemetry_state.connect) {
        case TelemetryState.CONNECT_CONNECTED:
            if (telemetry_state.config != null) {
                title= String.format(Locale.getDefault(), "S/N %d %6.3f MHz%s", telemetry_state.config.serial,
                                     telemetry_state.frequency, telemetry_state.idle_mode ? " (idle)" : "");
                if (telemetry_state.telemetry_rate != AltosLib.ao_telemetry_rate_38400)
                    title = title.concat(String.format(Locale.getDefault(), " %d bps",
                                                       AltosLib.ao_telemetry_rate_values[telemetry_state.telemetry_rate]));
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
        if (state_name != null)
            title = title.concat(String.format(Locale.getDefault(), " (%s)", state_name));
        setTitle(title);
    }

    void setFrequency(double freq) {
        telem_frequency = freq;
        selected_frequency = AltosLib.MISSING;
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

        if (serial != selected_serial) {
            if (serial != 0) {
                int i;
                for (i = 0; i < trackers.length; i++)
                    if (trackers[i].serial == serial)
                        break;

                if (i == trackers.length) {
                    AltosDebug.debug("attempt to select unknown tracker %d\n", serial);
                    return;
                }
                if (frequency != 0.0 && frequency != AltosLib.MISSING)
                    setFrequency(frequency);
            }

            selected_serial = serial;
        }
        if (active_fragment != null)
            active_fragment.select_tracker(serial);

        update_state(null);
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
