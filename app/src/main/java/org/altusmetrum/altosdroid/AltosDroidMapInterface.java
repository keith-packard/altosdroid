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

import org.altusmetrum.altoslib_14.AltosLatLon;

public interface AltosDroidMapInterface {
    void set_altos_droid(MainActivity altos_droid);
    void set_telem_state(TelemetryState telem_state);
    void center(double lat, double lon, double accuracy);
    void set_pad_position(double lat, double lon);
    void set_here_position(double lat, double lon);
    void set_there_position(double lat, double lon);
    void position_permission();
    void destroy();
}
