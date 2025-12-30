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

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class DeviceViewAdapter extends RecyclerView.Adapter<DeviceViewHolder>  {
    private ArrayList<DeviceAddress> deviceList;

    private Context context;

    public DeviceViewAdapter(Context context) {
        this.deviceList = new ArrayList<DeviceAddress>();
        this.context = context;
    }

    public void add(DeviceAddress device) {
        if (this.deviceList.contains(device)) {
            return;
        }
        this.deviceList.add(device);
        notifyItemInserted(getItemCount() - 1);
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_item, parent, false);
        return new DeviceViewHolder(view);
    }

    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        long itemId = getItemId(position);
        holder.parentLayout.setTag(itemId);
        final DeviceAddress deviceAddress = deviceList.get(position);
        holder.deviceName.setText(deviceAddress.name);
        holder.deviceAddress.setText(deviceAddress.address);
        holder.parentLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //DeviceAddress deviceAddress = deviceList.get(position);
                String name = deviceAddress.name;
                String address = deviceAddress.address;

                /* Ignore clicks on items that are too short */
                if (address.length() < 17) {
                    AltosDebug.debug("+++ Ignore short address +++");
                    return;
                }

                // Create the result Intent and include the MAC address
                Intent intent = new Intent();
                intent.putExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS, address);
                intent.putExtra(DeviceListActivity.EXTRA_DEVICE_NAME, name);

                // Set result and finish this Activity
                ((Activity) context).setResult(Activity.RESULT_OK, intent);
                ((Activity) context).finish();
            }
        });

    }

    @Override
    public int getItemCount() {
        return deviceList.size();
    }
}
