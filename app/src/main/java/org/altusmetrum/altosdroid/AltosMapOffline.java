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
import androidx.fragment.app.Fragment;

import org.altusmetrum.altoslib_14.AltosLatLon;

public class AltosMapOffline extends Fragment implements AltosMapInterface {

    private MainActivity altos_droid;
    public void set_altos_droid(MainActivity altos_droid) {
        this.altos_droid = altos_droid;
    }
    public void set_telem_state(TelemetryState telem_state) { }
    public void center(double lat, double lon, double accuracy) { }
    public void set_pad_position(double lat, double lon) { }
    public void set_track(AltosLatLon my_position, AltosLatLon target_position) { }
    public void position_permission() { }
    public void destroy() {}
    public AltosMapOffline() { }
}

