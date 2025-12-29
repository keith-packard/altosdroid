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
    private ArrayList<DeviceItem> deviceList;

    private Context context;

    public DeviceViewAdapter(Context context) {
        this.deviceList = new ArrayList<DeviceItem>();
        this.context = context;
    }

    public void add(DeviceItem device) {
        this.deviceList.add(device);
        AltosDebug.debug("Add device %s", device.name);
        notifyItemInserted(getItemCount() - 1);
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AltosDebug.debug("onCreateViewHolder");
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.device_item, parent, false);
        return new DeviceViewHolder(view);
    }

    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        AltosDebug.debug("onBindViewHolder");
        long itemId = getItemId(position);
        holder.parentLayout.setTag(itemId);
        final DeviceItem deviceItem = deviceList.get(position);
        holder.deviceName.setText(deviceItem.name);
        holder.deviceAddress.setText(deviceItem.address);
        holder.parentLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //DeviceItem deviceItem = deviceList.get(position);
                String deviceName = deviceItem.name;
                String deviceAddress = deviceItem.address;
                AltosDebug.debug("+++ onClick: %s %s +++", deviceName, deviceAddress);

                /* Ignore clicks on items that are too short */
                if (deviceAddress.length() < 17) {
                    AltosDebug.debug("+++ Ignore short address +++");
                    return;
                }

                // Cancel discovery because it's costly and we're about to connect
                //mBtAdapter.cancelDiscovery();

                AltosDebug.debug("******* selected item '%s'", deviceName);

                // Create the result Intent and include the MAC address
                Intent intent = new Intent();
                intent.putExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS, deviceAddress);
                intent.putExtra(DeviceListActivity.EXTRA_DEVICE_NAME, deviceName);

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
