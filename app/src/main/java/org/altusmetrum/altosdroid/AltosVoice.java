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

import android.content.res.Resources;
import android.location.Location;
import android.speech.tts.TextToSpeech;

import org.altusmetrum.altoslib_14.AltosConvert;
import org.altusmetrum.altoslib_14.AltosGPS;
import org.altusmetrum.altoslib_14.AltosGreatCircle;
import org.altusmetrum.altoslib_14.AltosLib;
import org.altusmetrum.altoslib_14.AltosPreferences;
import org.altusmetrum.altoslib_14.AltosState;

import java.util.Locale;

class Utterance {
    double      score;
    String      text;
    Speaker     speaker;

    static double state_score = 100.0;
    static double pyro_score = 95.0;
    static double speed_large_score = 90.0;
    static double height_large_score = 85.0;
    static double track_large_score = 80.0;

    static double medium_score = 50.0;

    static double speed_medium_score = 45.0;
    static double height_medium_score = 40.0;
    static double track_medium_score = 35.0;

    static double small_score = 30.0;

    static double speed_small_score = 25.0;
    static double height_small_score = 20.0;
    static double track_small_score = 15.0;

    static boolean is_large_score(double score) {
        return medium_score < score;
    }

    static boolean is_medium_score(double score) {
        return small_score <= score && score <= medium_score;
    }

    static boolean is_small_score(double score) {
        return score <= small_score;
    }

    void commit() {
        speaker.commit();
    }

    Utterance(Speaker speaker, double score, String text, Object ... arguments) {
        this.speaker = speaker;
        this.score = score;
        if (arguments.length > 0)
            this.text = String.format(text, arguments);
        else
            this.text = text;
    }
}

abstract class Speaker {
    long        last_time;
    boolean     new_mode;

    abstract Utterance utterance(TelemetryState telem_state, AltosState state,
                                 AltosGreatCircle from_receiver, Location receiver,
                                 boolean new_mode);

    boolean new_mode(boolean new_mode) {
        if (new_mode)
            this.new_mode = true;
        return this.new_mode;
    }

    void commit() {
        last_time = System.currentTimeMillis();
        new_mode = false;
    }

    Speaker() {
        last_time = AltosLib.MISSING;
        new_mode = true;
    }
}

class StateSpeaker extends Speaker {

    int last_state;
    int pending_state;

    Utterance utterance(TelemetryState telem_state, AltosState state,
                        AltosGreatCircle from_receiver, Location receiver,
                        boolean new_mode) {
        pending_state = state.state();

        if (new_mode(new_mode))
            last_state = AltosLib.ao_flight_invalid;

        if (pending_state != last_state && AltosLib.ao_flight_boost <= pending_state && pending_state <= AltosLib.ao_flight_landed) {

            String text = String.format("%s.", state.state_name());

            if (AltosVoice.descending(state.state()) && !AltosVoice.descending(last_state)) {
                if (state.max_height() != AltosLib.MISSING) {
                    text += String.format(" max height: %s.",
                                          AltosConvert.height.say_units(state.max_height(), 0));
                }
            }
            return new Utterance(this, Utterance.state_score, text);
        }
        return null;
    }

    void commit() {
        super.commit();
        last_state = pending_state;
    }

    StateSpeaker() {
        super();
        last_state = AltosLib.ao_flight_invalid;
    }
}

class PyroSpeaker extends Speaker {

    int last_pyro_fired;
    int pending_pyro_fired;

    Utterance utterance(TelemetryState telem_state, AltosState state,
                        AltosGreatCircle from_receiver, Location receiver,
                        boolean new_mode) {

        if (new_mode(new_mode))
            last_pyro_fired = 0;

        /* We only care about bits that turn on, not bits that turn back off */
        pending_pyro_fired = last_pyro_fired & state.pyro_fired;

        if (state.pyro_fired != last_pyro_fired) {
            for (int i = 0; (1 << i) <= state.pyro_fired; i++) {
                int bit = (1 << i);
                if ((state.pyro_fired & bit) != 0 && (pending_pyro_fired & bit) == 0) {
                    pending_pyro_fired |= bit;
                    return new Utterance(this, Utterance.pyro_score, "igniter %c fired", 'A' + i);
                }
            }
        }
        return null;
    }

    void commit() {
        super.commit();
        last_pyro_fired = pending_pyro_fired;
    }

    PyroSpeaker() {
        super();
        last_pyro_fired = 0;
    }
}

class HeightSpeaker extends Speaker {

    double last_height, pending_height;

    Utterance utterance(TelemetryState telem_state, AltosState state,
                        AltosGreatCircle from_receiver, Location receiver,
                        boolean new_mode) {

        if (new_mode(new_mode))
            last_height = AltosLib.MISSING;

        pending_height = state.height();

        if (pending_height != AltosLib.MISSING) {

            double score = 0.0;

            switch (AltosVoice.height_change(pending_height, last_height)) {
            case AltosVoice.CHANGE_LARGE:
                score = Utterance.height_large_score;
                break;
            case AltosVoice.CHANGE_MEDIUM:
                score = Utterance.height_medium_score;
                break;
            case AltosVoice.CHANGE_SMALL:
                score = Utterance.height_small_score;
                break;
            case AltosVoice.CHANGE_NONE:
                return null;
            }

            return new Utterance(this, score, "height %s.",
                                 AltosConvert.height.say_units(pending_height));
        }
        return null;
    }

    void commit() {
        super.commit();
        last_height = pending_height;
    }

    HeightSpeaker() {
        super();
        last_height = AltosLib.MISSING;
    }
}

class SpeedSpeaker extends Speaker {

    double last_speed, pending_speed;

    Utterance utterance(TelemetryState telem_state, AltosState state,
                        AltosGreatCircle from_receiver, Location receiver,
                        boolean new_mode) {

        if (new_mode(new_mode))
            last_speed = AltosLib.MISSING;

        pending_speed = AltosLib.MISSING;
        String value = null;

        if (state.state() <= AltosLib.ao_flight_coast) {
            pending_speed = state.speed();
        } else {
            if (state.gps != null && state.gps.locked && state.gps.nsat >= 4) {
                if (state.state() < AltosLib.ao_flight_invalid) {
                    pending_speed = state.gps_ascent_rate();
                } else {
                    pending_speed = state.gps_speed();
                    if (pending_speed != AltosLib.MISSING)
                        value = "speed";
                }
            }
            if (pending_speed == 0.0 || pending_speed == AltosLib.MISSING)
                pending_speed = state.speed();
        }

        if (pending_speed != AltosLib.MISSING) {

            double score = 0.0;

            switch (AltosVoice.speed_change(pending_speed, last_speed)) {
            case AltosVoice.CHANGE_LARGE:
                score = Utterance.speed_large_score;
                break;
            case AltosVoice.CHANGE_MEDIUM:
                score = Utterance.speed_medium_score;
                break;
            case AltosVoice.CHANGE_SMALL:
                score = Utterance.speed_small_score;
                break;
            case AltosVoice.CHANGE_NONE:
                return null;
            }

            double speed = pending_speed;

            if (value == null) {
                if (Math.abs(speed) < 1.0)
                    value = "speed";
                else if (speed >= 0)
                    value = "ascending at";
                else {
                    value = "descending at";
                    speed = -speed;
                }
            }

            return new Utterance(this, score, "%s %s.", value,
                                 AltosConvert.speed.say_units(speed));
        }
        return null;
    }

    void commit() {
        super.commit();
        last_speed = pending_speed;
    }

    SpeedSpeaker() {
        super();
        last_speed = AltosLib.MISSING;
    }
}

class TrackSpeaker extends Speaker {

    Location            last_receiver, pending_receiver;
    AltosGPS            last_target, pending_target;
    AltosGreatCircle    last_track, pending_track;

    double score() {

        int change = AltosVoice.CHANGE_NONE;
        int receiver_change = AltosVoice.receiver_change(pending_receiver, last_receiver);
        if (receiver_change > change)
            change = receiver_change;
        int target_change = AltosVoice.target_change(pending_target, last_target);
        if (target_change > change)
            change = target_change;

        double score = 0.0;

        switch(change) {
        case AltosVoice.CHANGE_LARGE:
            score = Utterance.track_large_score;
            break;
        case AltosVoice.CHANGE_MEDIUM:
            score = Utterance.track_medium_score;
            break;
        case AltosVoice.CHANGE_SMALL:
            score = Utterance.track_small_score;
            break;
        case AltosVoice.CHANGE_NONE:
            score = 0.0;
            break;
        }
        return score;
    }

    Utterance sayit(double score) {
        if (pending_track.elevation < 10.0) {
            return new Utterance(this, score, "bearing %s %d, distance %s.",
                                 pending_track.bearing_words(
                                     AltosGreatCircle.BEARING_VOICE),
                                 (int) (pending_track.bearing + 0.5),
                                 AltosConvert.distance.say(pending_track.distance, 3));
        } else {
            return new Utterance(this, score, "bearing %s %d, elevation %d, distance %s.",
                                 pending_track.bearing_words(
                                     AltosGreatCircle.BEARING_VOICE),
                                 (int) (pending_track.bearing + 0.5),
                                 (int) (pending_track.elevation + 0.5),
                                 AltosConvert.distance.say(pending_track.distance, 3));
        }
    }

    Utterance utterance(TelemetryState telem_state, AltosState state,
                        AltosGreatCircle from_receiver, Location receiver,
                        boolean new_mode) {

        if (new_mode(new_mode)) {
            last_target = null;
            last_receiver = null;
            last_track = null;
        }

        pending_target = state.gps;
        pending_receiver = receiver;
        pending_track = from_receiver;

        if (pending_target != null && pending_receiver != null && pending_track != null) {
            double score = score();
            if (score == 0.0)
                return null;
            return sayit(score);
        }
        return null;
    }

    void commit() {
        super.commit();
        last_track = pending_track;
        last_target = pending_target;
        last_receiver = pending_receiver;
    }

    TrackSpeaker() {
        super();
        last_track = null;
        last_target = null;
        last_receiver = null;
    }
}

abstract class GoNoGoSpeaker extends Speaker {

    boolean pending_ready, last_ready;

    abstract String name();
    abstract boolean ready(AltosState state);

    Utterance utterance(TelemetryState telem_state, AltosState state,
                        AltosGreatCircle from_receiver, Location receiver,
                        boolean new_mode) {

        new_mode = new_mode(new_mode);

        pending_ready = ready(state);
        if (pending_ready != last_ready || new_mode) {
            return new Utterance(this, Utterance.pyro_score, "%s %s.",
                                 name(), pending_ready? "ready" : "not ready");
        }
        return null;
    }

    void commit() {
        super.commit();
        last_ready = pending_ready;
    }

    GoNoGoSpeaker() {
        super();
        last_ready = false;
    }
}

class ApogeeSpeaker extends GoNoGoSpeaker {
    String name() { return "apogee"; }
    boolean ready(AltosState state) { return state.apogee_voltage >= AltosLib.ao_igniter_good; }
}

class MainSpeaker extends GoNoGoSpeaker {
    String name() { return "main"; }
    boolean ready(AltosState state) { return state.main_voltage >= AltosLib.ao_igniter_good; }
}

class GPSSpeaker extends GoNoGoSpeaker {
    String name() { return "G P S"; }
    boolean ready(AltosState state) { return state.gps_ready; }
}

public class AltosVoice {

    private TextToSpeech tts         = null;
    private boolean      tts_running = false;

    static final int TELL_MODE_NONE = 0;
    static final int TELL_MODE_PAD = 1;
    static final int TELL_MODE_FLIGHT = 2;
    static final int TELL_MODE_MAP = 3;
    static final int TELL_MODE_RECOVER = 4;

    static final int TELL_STEP_NONE = 0;
    static final int TELL_STEP_STATE = 1;
    static final int TELL_STEP_SPEED = 2;
    static final int TELL_STEP_HEIGHT = 3;
    static final int TELL_STEP_TRACK = 4;

    private int		last_tell_mode;
    private int		last_tell_serial = AltosLib.MISSING;
    private int		last_state;
    private AltosGPS last_gps;
    private double	last_height = AltosLib.MISSING;
    private double      last_spoken_height = AltosLib.MISSING;
    private double      last_speed = AltosLib.MISSING;
    private double      last_spoken_speed = AltosLib.MISSING;
    private int         last_spoken_pyro_fired = 0;
    private AltosGreatCircle last_from_receiver = null;
    private AltosGreatCircle last_spoken_from_receiver = null;
    private Location last_receiver;
    private long	last_speak_time;
    private int		last_tell_step = TELL_STEP_NONE;
    private boolean	quiet = false;

    static final int CHANGE_NONE = 0;
    static final int CHANGE_SMALL = 1;
    static final int CHANGE_MEDIUM = 2;
    static final int CHANGE_LARGE = 3;

    Speaker stateSpeaker = new StateSpeaker();
    Speaker pyroSpeaker = new PyroSpeaker();
    Speaker heightSpeaker = new HeightSpeaker();
    Speaker speedSpeaker = new SpeedSpeaker();
    Speaker trackSpeaker = new TrackSpeaker();

    Speaker apogeeSpeaker = new ApogeeSpeaker();
    Speaker mainSpeaker = new MainSpeaker();
    Speaker gpsSpeaker = new GPSSpeaker();

    private Speaker pad_speakers[] = {
        stateSpeaker,
        apogeeSpeaker,
        mainSpeaker,
        gpsSpeaker,
    };

    private Speaker flight_speakers[] = {
        stateSpeaker,
        pyroSpeaker,
        heightSpeaker,
        speedSpeaker,
        trackSpeaker,
    };

    private Speaker recover_speakers[] = {
        stateSpeaker,
        trackSpeaker,
    };

    private long now() {
        return System.currentTimeMillis();
    }

    private void reset_last() {
        last_tell_mode = TELL_MODE_NONE;
        last_speak_time = now() - 100 * 1000;
        last_gps = null;
        last_height = AltosLib.MISSING;
        last_spoken_height = AltosLib.MISSING;
        last_speed = AltosLib.MISSING;
        last_spoken_speed = AltosLib.MISSING;
        last_from_receiver = null;
        last_spoken_from_receiver = null;
        last_receiver = null;
        last_spoken_pyro_fired = 0;
        last_state = AltosLib.ao_flight_invalid;
        last_tell_step = TELL_STEP_NONE;
    }

    public AltosVoice(MainActivity a) {
        tts = new TextToSpeech(a, new TextToSpeech.OnInitListener() {
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) tts_running = true;
                }
            });
        reset_last();
    }

    private boolean tts_enabled() {
        return AltosPreferences.voice();
    }

    public synchronized void set_enable(boolean enable) {
        if (!enable && tts_running)
            tts.stop();
    }

    public synchronized void speak(String s) {
        if (!tts_enabled() || !tts_running) return;
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

    static boolean descending(int state) {
        return AltosLib.ao_flight_drogue <= state && state <= AltosLib.ao_flight_landed;
    }

    static int location_change(double new_lat, double new_lon, double new_height,
                               double old_lat, double old_lon, double old_height) {

        if (new_lat == old_lat && new_lon == old_lon)
            return height_change(new_height, old_height);

        if (new_lat == AltosLib.MISSING || old_lat == AltosLib.MISSING)
            return CHANGE_LARGE;

        if (new_height == AltosLib.MISSING || old_height == AltosLib.MISSING) {
            if (new_height != old_height)
                return CHANGE_LARGE;
            new_height = 0.0;
            old_height = 0.0;
        }

        AltosGreatCircle	moved = new AltosGreatCircle(new_lat, new_lon, new_height,
                                                             old_lat, old_lon, old_height);

        int change = CHANGE_NONE;

        if (moved.range > 100)
            change = CHANGE_LARGE;
        else if (moved.range > 10)
            change = CHANGE_MEDIUM;

        return change;
    }

    static boolean gps_known(AltosGPS gps) {
        if (gps == null)
            return false;
        if (!gps.locked)
            return false;
        if (gps.nsat < 4)
            return false;
        return true;
    }

    static int target_change(AltosGPS new_gps, AltosGPS old_gps) {
        if (new_gps == old_gps)
            return CHANGE_NONE;

        boolean new_known = gps_known(new_gps);
        boolean old_known = gps_known(old_gps);

        if (!new_known && !old_known)
            return CHANGE_NONE;

        if (new_known != old_known)
            return CHANGE_LARGE;

        return location_change(new_gps.lat, new_gps.lon, new_gps.alt,
                               old_gps.lat, old_gps.lon, old_gps.alt);
    }

    static int receiver_change(Location new_receiver, Location old_receiver) {
        if (new_receiver == old_receiver)
            return CHANGE_NONE;

        if (new_receiver == null || old_receiver == null)
            return CHANGE_LARGE;

        return location_change(new_receiver.getLatitude(), new_receiver.getLongitude(), new_receiver.getAltitude(),
                               old_receiver.getLatitude(), old_receiver.getLongitude(), old_receiver.getAltitude());
    }

    static int speed_change(double new_speed, double old_speed) {

        if (new_speed == old_speed)
            return CHANGE_NONE;

        if (new_speed == AltosLib.MISSING || old_speed == AltosLib.MISSING)
            return CHANGE_LARGE;

        double new_mag = Math.abs(new_speed);
        double change = Math.abs(new_speed - old_speed);
        double big_change = new_mag / 4;
        if (big_change < 25)
            big_change = 25;
        double medium_change = new_mag / 10;
        if (medium_change < 5)
            medium_change = 5;

        if (change >= big_change)
            return CHANGE_LARGE;

        if (change >= medium_change)
            return CHANGE_MEDIUM;

        if (change > 1)
            return CHANGE_SMALL;

        return CHANGE_NONE;
    }

    static int height_change(double new_height, double old_height) {

        if (new_height == old_height)
            return CHANGE_NONE;

        if (new_height == AltosLib.MISSING || old_height == AltosLib.MISSING)
            return CHANGE_LARGE;

        double new_mag = Math.abs(new_height);
        double change = Math.abs(new_height - old_height);
        double big_change = new_mag / 4;
        if (big_change < 100)
            big_change = 100;
        double medium_change = new_mag / 10;
        if (medium_change < 10)
            medium_change = 10;

        if (change >= big_change)
            return CHANGE_LARGE;

        if (change >= medium_change)
            return CHANGE_MEDIUM;

        if (change > 1)
            return CHANGE_SMALL;

        return CHANGE_NONE;
    }

    public boolean tell(TelemetryState telem_state, AltosState state,
                     AltosGreatCircle from_receiver, Location receiver,
                     AltosFragment fragment, boolean quiet) {

        this.quiet = quiet;

        boolean	spoken = false;

        if (!tts_enabled() || !tts_running) return false;

        if (is_speaking()) return true;

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
        else if (fragment != null && fragment.name().equals(MainActivity.map_name))
            tell_mode = TELL_MODE_MAP;
        else
            tell_mode = TELL_MODE_RECOVER;

        if (tell_mode != last_tell_mode)
            last_tell_step = TELL_STEP_NONE;

        Speaker[] speakers = null;

        switch (tell_mode) {
        case TELL_MODE_PAD:
            speakers = pad_speakers;
            break;
        case TELL_MODE_FLIGHT:
        case TELL_MODE_MAP:
            speakers = flight_speakers;
            break;
        case TELL_MODE_RECOVER:
            speakers = recover_speakers;
            break;
        }

        spoken = false;

        if (speakers != null && state != null) {
            Utterance utterance = null;
            boolean new_mode = tell_mode != last_tell_mode;

            for (int i = 0; i < speakers.length; i++) {
                Utterance pending = speakers[i].utterance(telem_state, state, from_receiver, receiver, new_mode);
                if (pending != null) {
                    if (utterance == null || pending.score > utterance.score)
                        utterance = pending;
                }
            }

            if (utterance != null) {
                if (Utterance.is_large_score(utterance.score) ||
                    (Utterance.is_medium_score(utterance.score) && time_since_speak() >= 4 * 1000) ||
                    time_since_speak() >= 10 * 1000)
                {
                    utterance.commit();
                    speak(utterance.text);
                    spoken = true;
                }
            }
        }

        if (spoken) {
            last_tell_mode = tell_mode;
            last_tell_serial = tell_serial;
            if (state != null) {
                last_state = state.state();
                last_height = state.height();
                last_speed = state.speed();
                if (state.gps != null)
                    last_gps = state.gps;
            }
            if (receiver != null)
                last_receiver = receiver;
            if (from_receiver != null)
                last_from_receiver = from_receiver;
        }

        return spoken;
    }
}
