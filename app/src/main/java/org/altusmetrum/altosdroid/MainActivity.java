package org.altusmetrum.altosdroid;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import android.app.PendingIntent;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.altusmetrum.altosdroid.databinding.ActivityMainBinding;

import org.altusmetrum.altoslib_14.*;

public class    MainActivity extends AppCompatActivity implements LocationListener, ActivityCompat.OnRequestPermissionsResultCallback {

    private ActivityMainBinding binding;

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
	public boolean asked_permission = false;

	static final String BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT";
	static final String BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN";

	public static final String EXTRA_IDLE_MODE = "idle_mode";
	public static final String EXTRA_IDLE_RESULT = "idle_result";
	public static final String EXTRA_FREQUENCY = "frequency";
	public static final String EXTRA_TELEMETRY_SERVICE = "telemetry_service";
	public static final String EXTRA_TRACKERS = "trackers";
	public static final String EXTRA_TRACKERS_TITLE = "trackers_title";

	// Setup result bits
	public static final int SETUP_BAUD = 1;
	public static final int SETUP_UNITS = 2;
	public static final int SETUP_MAP_SOURCE = 4;
	public static final int SETUP_MAP_TYPE = 8;
	public static final int SETUP_FONT_SIZE = 16;

	public static FragmentManager fm;

	public Location location = null;
	public TelemetryState telemetry_state = null;
	public AltosState state = null;

	private BluetoothAdapter mBluetoothAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ActivityLayouts.applyEdgeToEdge(this, R.id.activity_main);
        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_pad, R.id.navigation_flight, R.id.navigation_recover, R.id.navigation_map)
                .build();
		NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_activity_main);
        NavController navController = navHostFragment.getNavController();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

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
/*
    @Override
	public void onStart() {
		super.onStart();
		noticeIntent(getIntent());
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
			//disconnectDevice(false);
			return true;
		}
		if (itemId == R.id.quit) {
			//AltosDebug.debug("R.id.quit");
			//disconnectDevice(true);
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
			*

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

			 *
			return true;
		}
		return super.onOptionsItemSelected(item);
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
				ActivityCompat.requestPermissions(this, permissions, MY_PERMISSION_REQUEST);
			}
		}
		if (have_location_permission)
			enable_location_updates(false);
	}

	@Override
	public void onPause() {
		// AltosDebug.debug("- ON PAUSE -");

		super.onPause();

		// Stop listening for location updates
		if (have_location_permission)
			((LocationManager) getSystemService(Context.LOCATION_SERVICE)).removeUpdates(this);
	}
*/
	void update_ui(TelemetryState telem_state, AltosState state, boolean quiet) {
	}
}