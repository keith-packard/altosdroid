package org.altusmetrum.altosdroid;

import android.content.res.Resources;
import android.location.Location;
import android.speech.tts.TextToSpeech;

import org.altusmetrum.altoslib_14.AltosConvert;
import org.altusmetrum.altoslib_14.AltosGPS;
import org.altusmetrum.altoslib_14.AltosGreatCircle;
import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosState;

import java.util.Locale;

public class AltosVoice {

	private TextToSpeech tts         = null;
	private boolean      tts_enabled = false;

	static final int TELL_MODE_NONE = 0;
	static final int TELL_MODE_PAD = 1;
	static final int TELL_MODE_FLIGHT = 2;
	static final int TELL_MODE_RECOVER = 3;

	static final int TELL_FLIGHT_NONE = 0;
	static final int TELL_FLIGHT_STATE = 1;
	static final int TELL_FLIGHT_SPEED = 2;
	static final int TELL_FLIGHT_HEIGHT = 3;
	static final int TELL_FLIGHT_TRACK = 4;

	private int		last_tell_mode;
	private int		last_tell_serial = AltosLib.MISSING;
	private int		last_state;
	private AltosGPS last_gps;
	private double		last_height = AltosLib.MISSING;
	private Location last_receiver;
	private long		last_speak_time;
	private int		last_flight_tell = TELL_FLIGHT_NONE;
	private boolean		quiet = false;

	private long now() {
		return System.currentTimeMillis();
	}

	private void reset_last() {
		last_tell_mode = TELL_MODE_NONE;
		last_speak_time = now() - 100 * 1000;
		last_gps = null;
		last_height = AltosLib.MISSING;
		last_receiver = null;
		last_state = AltosLib.ao_flight_invalid;
		last_flight_tell = TELL_FLIGHT_NONE;
	}

	public AltosVoice(MainActivity a) {
		tts = new TextToSpeech(a, new TextToSpeech.OnInitListener() {
			public void onInit(int status) {
				if (status == TextToSpeech.SUCCESS) tts_enabled = true;
			}
		});
		reset_last();
	}

	public synchronized void set_enable(boolean enable) {
		tts_enabled = enable;
	}

	public synchronized void speak(String s) {
		if (!tts_enabled) return;
		last_speak_time = now();
		if (!quiet)
			tts.speak(s, TextToSpeech.QUEUE_ADD, null, null);
	}

	public synchronized long time_since_speak() {
		return now() - last_speak_time;
	}

	public synchronized void speak(String format, Object ... arguments) {
		speak(String.format(Locale.getDefault(), format, arguments));
	}

	public synchronized boolean is_speaking() {
		return tts.isSpeaking();
	}

	public void stop() {
		if (tts != null) {
			tts.stop();
			tts.shutdown();
		}
	}

	private boolean		last_apogee_good;
	private boolean		last_main_good;
	private boolean		last_gps_good;

	private boolean tell_gonogo(String name,
				  boolean current,
				  boolean previous,
				  boolean new_mode) {
		if (current != previous || new_mode)
			speak("%s %s.", name, current ? "ready" : "not ready");
		return current;
	}

	private boolean tell_pad(TelemetryState telem_state, AltosState state,
                             AltosGreatCircle from_receiver, Location receiver) {

		if (state == null)
			return false;

		if (state.apogee_voltage != AltosLib.MISSING)
			last_apogee_good = tell_gonogo("apogee",
						       state.apogee_voltage >= AltosLib.ao_igniter_good,
						       last_apogee_good,
						       last_tell_mode != TELL_MODE_PAD);

		if (state.main_voltage != AltosLib.MISSING)
			last_main_good = tell_gonogo("main",
						     state.main_voltage >= AltosLib.ao_igniter_good,
						     last_main_good,
						     last_tell_mode != TELL_MODE_PAD);

		if (state.gps != null)
			last_gps_good = tell_gonogo("G P S",
						    state.gps_ready,
						    last_gps_good,
						    last_tell_mode != TELL_MODE_PAD);
		return true;
	}


	private boolean descending(int state) {
		return AltosLib.ao_flight_drogue <= state && state <= AltosLib.ao_flight_landed;
	}

	private boolean target_moved(AltosState state) {
		if (last_gps != null && state != null && state.gps != null) {
			AltosGreatCircle	moved = new AltosGreatCircle(last_gps.lat, last_gps.lon, last_gps.alt,
									     state.gps.lat, state.gps.lon, state.gps.alt);
			double			height_change = 0;
			double			height = state.height();

			if (height != AltosLib.MISSING && last_height != AltosLib.MISSING)
				height_change = Math.abs(last_height - height);

            return !(moved.range < 10) || !(height_change < 10);
		}
		return true;
	}

	private boolean receiver_moved(Location receiver) {
		if (last_receiver != null && receiver != null) {
			AltosGreatCircle	moved = new AltosGreatCircle(last_receiver.getLatitude(),
									     last_receiver.getLongitude(),
									     last_receiver.getAltitude(),
									     receiver.getLatitude(),
									     receiver.getLongitude(),
									     receiver.getAltitude());
            return !(moved.range < 10);
		}
		return true;
	}

	private boolean tell_flight(TelemetryState telem_state, AltosState state,
				    AltosGreatCircle from_receiver, Location receiver) {

		boolean	spoken = false;

		if (state == null)
			return false;

		if (last_tell_mode != TELL_MODE_FLIGHT)
			last_flight_tell = TELL_FLIGHT_NONE;

		if (state.state() != last_state && AltosLib.ao_flight_boost <= state.state() && state.state() <= AltosLib.ao_flight_landed) {
			speak(state.state_name());
			if (descending(state.state()) && !descending(last_state)) {
				if (state.max_height() != AltosLib.MISSING) {
					speak("max height: %s.",
					      AltosConvert.height.say_units(state.max_height()));
				}
			}
			last_flight_tell = TELL_FLIGHT_STATE;
			return true;
		}

		if (last_tell_mode == TELL_MODE_FLIGHT && last_flight_tell == TELL_FLIGHT_TRACK) {
			if (time_since_speak() < 10 * 1000)
				return false;
			if (!target_moved(state) && !receiver_moved(receiver))
				return false;
		}

		double	speed;
		double	height;

		if (last_flight_tell == TELL_FLIGHT_NONE || last_flight_tell == TELL_FLIGHT_STATE || last_flight_tell == TELL_FLIGHT_TRACK) {
			last_flight_tell = TELL_FLIGHT_SPEED;

            String value = null;
			if (state.state() <= AltosLib.ao_flight_coast) {
				speed = state.speed();
			} else {
                if (state.state() < AltosLib.ao_flight_invalid) {
                    speed = state.gps_ascent_rate();
                } else {
                    speed = state.gps_speed();
                    if (speed != AltosLib.MISSING)
                        value = "speed";
                }
				if (speed == AltosLib.MISSING)
					speed = state.speed();
			}

			if (speed != AltosLib.MISSING) {
                if (value == null) {
                    if (speed >= 0)
                        value = "ascending at";
                    else {
                        value = "descending at";
                        speed = -speed;
                    }
                }
                speak("%s %s.", value, AltosConvert.speed.say_units(speed));
				return true;
			}
		}

		if (last_flight_tell == TELL_FLIGHT_SPEED) {
			last_flight_tell = TELL_FLIGHT_HEIGHT;
			height = state.height();

			if (height != AltosLib.MISSING) {
				speak("height %s.", AltosConvert.height.say_units(height));
				return true;
			}
		}

		if (last_flight_tell == TELL_FLIGHT_HEIGHT) {
			last_flight_tell = TELL_FLIGHT_TRACK;
			if (from_receiver != null) {
				speak("bearing %s %d, elevation %d, distance %s.",
				      from_receiver.bearing_words(
					      AltosGreatCircle.BEARING_VOICE),
				      (int) (from_receiver.bearing + 0.5),
				      (int) (from_receiver.elevation + 0.5),
				      AltosConvert.distance.say(from_receiver.distance));
				return true;
			}
		}

		return spoken;
	}

	private boolean tell_recover(TelemetryState telem_state, AltosState state,
				     AltosGreatCircle from_receiver, Location receiver, Resources resources) {

		if (from_receiver == null)
			return false;

		if (last_tell_mode == TELL_MODE_RECOVER) {
			if (!target_moved(state) && !receiver_moved(receiver))
				return false;
			if (time_since_speak() <= 10 * 1000)
				return false;
		}

		String direction = AltosValue.direction(from_receiver, receiver, resources);
		if (direction == null)
			direction = String.format(Locale.getDefault(), "Bearing %d", (int) (from_receiver.bearing + 0.5));

		speak("%s, distance %s.", direction,
		      AltosConvert.distance.say_units(from_receiver.distance));

		return true;
	}

	public void tell(TelemetryState telem_state, AltosState state,
                     AltosGreatCircle from_receiver, Location receiver,
                     AltosFragment fragment, boolean quiet, Resources resources) {

		this.quiet = quiet;

		boolean	spoken = false;

		if (!tts_enabled) return;

		if (is_speaking()) return;

		int	tell_serial = last_tell_serial;

		if (state != null)
			tell_serial = state.cal_data().serial;

		if (tell_serial != last_tell_serial)
			reset_last();

		int	tell_mode = TELL_MODE_NONE;

		if (fragment != null && fragment.name().equals(MainActivity.pad_name))
			tell_mode = TELL_MODE_PAD;
		else if (fragment != null && fragment.name().equals(MainActivity.flight_name))
			tell_mode = TELL_MODE_FLIGHT;
		else
			tell_mode = TELL_MODE_RECOVER;

		if (tell_mode == TELL_MODE_PAD)
			spoken = tell_pad(telem_state, state, from_receiver, receiver);
		else if (tell_mode == TELL_MODE_FLIGHT)
			spoken = tell_flight(telem_state, state, from_receiver, receiver);
		else
			spoken = tell_recover(telem_state, state, from_receiver, receiver, resources);

		if (spoken) {
			last_tell_mode = tell_mode;
			last_tell_serial = tell_serial;
			if (state != null) {
				last_state = state.state();
				last_height = state.height();
				if (state.gps != null)
					last_gps = state.gps;
			}
			if (receiver != null)
				last_receiver = receiver;
		}
	}
}
