package org.altusmetrum.altosdroid;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
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
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.PopupMenu;

import org.altusmetrum.altosdroid.databinding.ActivityMainBinding;

import org.altusmetrum.altoslib_14.*;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

class FragmentCallbacks extends FragmentManager.FragmentLifecycleCallbacks {

	private MainActivity activity;

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
	public static final int REQUEST_SETUP	       = 7;
	public static final int REQUEST_SELECT_TRACKER = 8;
	public static final int REQUEST_DELETE_TRACKER = 9;

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
	};

	private ServiceConnection mConnection = new ServiceConnection() {
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
			//serverIntent = new Intent(this, SetupActivity.class);
			//startActivityForResult(serverIntent, REQUEST_SETUP);
			return true;
		}
		if (itemId == R.id.select_freq) {
			// R.id.select_freq:
			// Set the TBT radio frequency
/*
			final AltosFrequency[] frequencies = AltosPreferences.common_frequencies();
			String[] frequency_strings = new String[frequencies.length];
			for (int i = 0; i < frequencies.length; i++)
				frequency_strings[i] = frequencies[i].toString();

			AlertDialog.Builder builder_freq = new AlertDialog.Builder(this);
			builder_freq.setTitle("Select Frequency");
			builder_freq.setItems(frequency_strings,
					 new DialogInterface.OnClickListener() {
						 public void onClick(DialogInterface dialog, int item) {
							 setFrequency(frequencies[item]);
							 selected_frequency = frequencies[item].frequency;
						 }
					 });
			AlertDialog alert_freq = builder_freq.create();
			alert_freq.show();
			*/

			return true;
		}
		if (itemId == R.id.select_tracker) {
			//start_select_tracker(trackers);
			return true;
		}
		if (itemId == R.id.delete_track) {
			//if (trackers != null && trackers.length > 0)
			//	start_select_tracker(trackers, R.string.delete_track, REQUEST_DELETE_TRACKER);
			return true;
		}
		if (itemId == R.id.idle_mode) {
			/*
			serverIntent = new Intent(this, IdleModeActivity.class);
			serverIntent.putExtra(EXTRA_IDLE_MODE, idle_mode);
			serverIntent.putExtra(EXTRA_FREQUENCY, telem_frequency);
			startActivityForResult(serverIntent, REQUEST_IDLE_MODE);

			 */
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
			default:
				super.onActivityResult(requestCode, resultCode, data);
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
	public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == MY_PERMISSION_REQUEST) {
			for (int i = 0; i < grantResults.length; i++) {
				if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
					if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
						have_location_permission = true;
						enable_location_updates(true);
//						if (map_online != null)
//							map_online.position_permission();
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
			mService.send(Message.obtain(null, TelemetryService.MSG_DISCONNECT, (Boolean) remember));
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
		update_state(null);
	}
	private void idle_mode(Intent data) {

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
			if (b == null)
				return true;
			return false;
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

	void update_state(TelemetryState new_telemetry_state) {
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

		if (1 ==1 || !telemetry_state.containsKey(selected_serial)) {
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
}