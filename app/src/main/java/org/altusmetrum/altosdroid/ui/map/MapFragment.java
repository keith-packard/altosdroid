package org.altusmetrum.altosdroid.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.altusmetrum.altosdroid.AltosDroidMapSourceListener;
import org.altusmetrum.altosdroid.AltosDroidPreferences;
import org.altusmetrum.altosdroid.AltosFragment;
import org.altusmetrum.altosdroid.AltosValue;
import org.altusmetrum.altosdroid.MainActivity;
import org.altusmetrum.altosdroid.R;
import org.altusmetrum.altosdroid.TelemetryState;
import org.altusmetrum.altosdroid.databinding.FragmentMapBinding;
import org.altusmetrum.altosdroid.databinding.FragmentRecoverBinding;
import org.altusmetrum.altosdroid.ui.recover.RecoverViewModel;
import org.altusmetrum.altoslib_14.AltosConvert;
import org.altusmetrum.altoslib_14.AltosGreatCircle;
import org.altusmetrum.altoslib_14.AltosLatLon;
import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosMap;
import org.altusmetrum.altoslib_14.AltosMapTypeListener;
import org.altusmetrum.altoslib_14.AltosPreferences;
import org.altusmetrum.altoslib_14.AltosState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

class RocketOnline implements Comparable {
	Marker marker;
	int		serial;
	long		last_packet;
	int		size;

	void set_position(AltosLatLon position, long last_packet) {
		marker.setPosition(new LatLng(position.lat, position.lon));
		this.last_packet = last_packet;
	}

	private Bitmap rocket_bitmap(Context context, String text, int marker_size) {
		Paint paint = new Paint();
		paint.setTextSize(40);


		Rect bounds = new Rect();
		size = marker_size;

		Drawable drawable = VectorDrawableCompat.create(context.getResources(), R.drawable.flight_orange, context.getTheme());
		Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
		drawable.draw(canvas);

		paint.getTextBounds(text, 0, text.length(), bounds);

		int	width = bounds.right - bounds.left;
		int	height = bounds.bottom - bounds.top;

		float x = bitmap.getWidth() / 2.0f - width / 2.0f;
		float y = bitmap.getHeight() / 2.0f - height / 2.0f;

		paint.setColor(0xffffffff);
		int offset = marker_size / 40;
		for (int yoffset = -offset; yoffset <= offset; yoffset += offset)
			for (int xoffset = -offset; xoffset <= offset; xoffset += offset)
				canvas.drawText(text, 0, text.length(), x+xoffset, y+yoffset, paint);
		paint.setColor(0xff000000);;
		canvas.drawText(text, 0, text.length(), x, y, paint);
		return bitmap;
	}

	public void remove() {
		marker.remove();
	}

	public int compareTo(Object o) {
		RocketOnline other = (RocketOnline) o;

		long	diff = last_packet - other.last_packet;

		if (diff > 0)
			return 1;
		if (diff < 0)
			return -1;
		return 0;
	}

	RocketOnline(Context context, int serial, GoogleMap map, double lat, double lon, long last_packet, int marker_size) {
		this.serial = serial;
		String name = String.format(Locale.ROOT, "%d", serial);
		this.marker = map.addMarker(new MarkerOptions()
					    .icon(BitmapDescriptorFactory.fromBitmap(rocket_bitmap(context, name, marker_size)))
					    .position(new LatLng(lat, lon))
					    .visible(true));
		this.last_packet = last_packet;
	}
}

public class MapFragment extends AltosFragment implements GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener, AltosDroidMapSourceListener, AltosMapTypeListener {
    private HashMap<Integer,RocketOnline> rockets = new HashMap<Integer,RocketOnline>();
    private GoogleMap mMap;
    private boolean mapLoaded;
	private Marker mPadMarker;
	private boolean pad_set;
	private Polyline mPolyline;

	private double mapAccuracy = -1;

	private AltosLatLon my_position = null;
	private AltosLatLon target_position = null;
	private FragmentMapBinding binding;

	void check_permission() {
        if (altos_droid == null)
            return;
        if (altos_droid.have_location_permission)
            position_permission();
        else
            altos_droid.tell_map_permission(this);
    }

    public void set_altos_droid(MainActivity altos_droid) {
        this.altos_droid = altos_droid;
        if (mapLoaded)
            check_permission();
    }
    MapFragment map_fragment() { return this; }

	static int get_marker_size() {
		Paint paint = new Paint();
		paint.setTextSize(40);
		Rect bounds = new Rect();
		String sampleString = "999999";
		paint.getTextBounds(sampleString, 0, sampleString.length(), bounds);
		return (bounds.right - bounds.left) * 11 / 10;
	}

	int marker_size = get_marker_size();

	static int[] altos_map_types = { AltosMap.maptype_roadmap, AltosMap.maptype_satellite, AltosMap.maptype_hybrid, AltosMap.maptype_terrain};
	public void map_type_changed(int map_type) {
		if (mMap != null) {
			if (map_type == AltosMap.maptype_hybrid)
				mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
			else if (map_type == AltosMap.maptype_satellite)
				mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
			else if (map_type == AltosMap.maptype_terrain)
				mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
			else
				mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
		}
	}

    private OnMapReadyCallback callback = new OnMapReadyCallback() {

        /**
         * Manipulates the map once available.
         * This callback is triggered when the map is ready to be used.
         * This is where we can add markers or lines, add listeners or move the camera.
         * In this case, we just add a marker near Sydney, Australia.
         * If Google Play services is not installed on the device, the user will be prompted to
         * install it inside the SupportMapFragment. This method will only be triggered once the
         * user has installed Google Play services and returned to the app.
         */
        @Override
        public void onMapReady(GoogleMap googleMap) {
            mMap = googleMap;
			int map_type = AltosPreferences.map_type();
            mapLoaded = true;
            check_permission();
			mMap.getUiSettings().setTiltGesturesEnabled(false);
			mMap.getUiSettings().setZoomControlsEnabled(false);
			mMap.setOnMarkerClickListener(map_fragment());
			mMap.setOnMapClickListener(map_fragment());
            Context context = getContext();
            Drawable drawable = VectorDrawableCompat.create(context.getResources(), R.drawable.pad_purple, context.getTheme());
            Bitmap bitmap = Bitmap.createBitmap(marker_size, marker_size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
			mPadMarker = mMap.addMarker(
					new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(bitmap))
					                   .position(new LatLng(0,0))
					                   .visible(false)
					);

			mPolyline = mMap.addPolyline(
					new PolylineOptions().add(new LatLng(0,0), new LatLng(0,0))
					                     .width(20)
					                     .color(Color.BLUE)
					                     .visible(false)
					);
			map_fragment().map_type_changed(AltosPreferences.map_type());
			mapLoaded = true;
        }
    };

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
							 ViewGroup container, Bundle savedInstanceState) {
		MapViewModel mapViewModel =
				new ViewModelProvider(this).get(MapViewModel.class);

		binding = FragmentMapBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(callback);
        }
		String[] map_types = getResources().getStringArray(R.array.map_types);
		AltosPreferences.register_map_type_listener(this);
		AltosDroidPreferences.register_map_source_listener(this);
		ArrayAdapter adapter = new ArrayAdapter(getContext(), R.layout.map_type_menu, map_types);
		AutoCompleteTextView mapType = view.findViewById(R.id.mapType);
		int map_type = AltosPreferences.map_type();
		for (int map_id = 0; map_id < altos_map_types.length; map_id++)
			if (altos_map_types[map_id] == map_type) {
				mapType.setText(map_types[map_id], false);
				break;
			}
		mapType.setAdapter(adapter);
		mapType.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				int map_type = AltosLib.MISSING;
				if (position < altos_map_types.length)
					AltosPreferences.set_map_type(altos_map_types[position]);
			}
		});
    }

	@Override
	public void onDestroy() {
		super.onDestroy();
		AltosPreferences.unregister_map_type_listener(this);
		AltosDroidPreferences.unregister_map_source_listener(this);
		mapLoaded = false;
		binding = null;
	}

	private double pixel_distance(LatLng a, LatLng b) {
		Projection projection = mMap.getProjection();

		Point a_pt = projection.toScreenLocation(a);
		Point	b_pt = projection.toScreenLocation(b);

		return Math.hypot((double) (a_pt.x - b_pt.x), (double) (a_pt.y - b_pt.y));
	}

	private RocketOnline[] sorted_rockets() {
		synchronized(rockets) {
			RocketOnline[]	rocket_array = rockets.values().toArray(new RocketOnline[0]);

			Arrays.sort(rocket_array);
			return rocket_array;
		}
	}

	public void onMapClick(LatLng lat_lng) {
		ArrayList<Integer> near = new ArrayList<Integer>();

		for (RocketOnline rocket : sorted_rockets()) {
			LatLng	pos = rocket.marker.getPosition();

			if (pos == null)
				continue;

			double distance = pixel_distance(lat_lng, pos);
			if (distance < rocket.size * 2)
				near.add(rocket.serial);
		}

		if (near.size() != 0)
			altos_droid.touch_trackers(near.toArray(new Integer[0]));
	}

	public boolean onMarkerClick(Marker marker) {
		onMapClick(marker.getPosition());
		return true;
	}

	public void
	position_permission() {
		if (mMap != null) {
			try {
				mMap.setMyLocationEnabled(true);
			} catch (SecurityException e) {
			}
		}
	}


	public void center(double lat, double lon, double accuracy) {
		if (mMap == null)
			return;

		if (mapAccuracy < 0 || accuracy < mapAccuracy/10) {
			mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lon),14));
			mapAccuracy = accuracy;
		}
	}

	private void set_rocket(int serial, AltosState state) {
		RocketOnline	rocket;

		if (state.gps == null || state.gps.lat == AltosLib.MISSING)
			return;

		if (mMap == null)
			return;

		synchronized(rockets) {
			if (rockets.containsKey(serial)) {
				rocket = rockets.get(serial);
				rocket.set_position(new AltosLatLon(state.gps.lat, state.gps.lon), state.received_time);
			} else {
				rocket = new RocketOnline(altos_droid,
							  serial,
							  mMap, state.gps.lat, state.gps.lon,
							  state.received_time,
						      marker_size);
				rockets.put(serial, rocket);
			}
		}
	}

	private void remove_rocket(int serial) {
		synchronized(rockets) {
			RocketOnline rocket = rockets.get(serial);
			rocket.remove();
			rockets.remove(serial);
		}
	}

        public void show(TelemetryState telem_state, AltosState state, AltosGreatCircle from_receiver, Location receiver_location) {
            if (telem_state != null) {
                synchronized(rockets) {
                    for (int serial : rockets.keySet()) {
                        if (!telem_state.containsKey(serial))
                            remove_rocket(serial);
                    }

                    for (int serial : telem_state.keySet()) {
                        set_rocket(serial, telem_state.get(serial));
                    }
                }
            }

            if (state != null) {
                if (mapLoaded) {
                    if (!pad_set && state.pad_lat != AltosLib.MISSING) {
                        pad_set = true;
                        mPadMarker.setPosition(new LatLng(state.pad_lat, state.pad_lon));
                        mPadMarker.setVisible(true);
                    }

                }

                if (state.gps != null && state.gps.lat != AltosLib.MISSING) {
					if (binding != null) {
						String lat_text = AltosValue.pos(state.gps.lat, "N", "S");
						String lon_text = AltosValue.pos(state.gps.lon, "E", "W");
						binding.mapTargetPosition.setText(lat_text + "\n" + lon_text);
					}
                    target_position = new AltosLatLon(state.gps.lat, state.gps.lon);
                    if (state.gps.locked && state.gps.nsat >= 4)
                        center (state.gps.lat, state.gps.lon, 10);
                }
            }

            if (receiver_location != null) {
                double accuracy;

                if (receiver_location.hasAccuracy())
                    accuracy = receiver_location.getAccuracy();
                else
                    accuracy = 1000;

                my_position = new AltosLatLon(receiver_location.getLatitude(), receiver_location.getLongitude());
                center (my_position.lat, my_position.lon, accuracy);
				if (binding != null) {
					String lat_text = AltosValue.pos(receiver_location.getLatitude(), "N", "S");
					String lon_text = AltosValue.pos(receiver_location.getLongitude(), "E", "W");
					binding.mapReceiverPosition.setText(lat_text + "\n" + lon_text);
				}
            }

            if (my_position != null && target_position != null && mPolyline != null) {
                mPolyline.setPoints(Arrays.asList(new LatLng(my_position.lat, my_position.lon), new LatLng(target_position.lat, target_position.lon)));
                mPolyline.setVisible(true);

            }
			if (from_receiver != null) {
				binding.mapBearing.setText(String.format(Locale.getDefault(), "%1.0f°", from_receiver.bearing));
				set_value(binding.mapDistance, AltosConvert.distance, 1, from_receiver.distance);
			}
        }

		public void map_source_changed(int map_source) {}

		@Override
    public String name() { return MainActivity.map_name; }
}