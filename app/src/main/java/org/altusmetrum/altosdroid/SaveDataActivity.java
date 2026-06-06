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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.altusmetrum.altosdroid.databinding.ConfigureDeviceBinding;
import org.altusmetrum.altosdroid.databinding.DeviceDataBinding;
import org.altusmetrum.altosdroid.databinding.SaveDataBinding;

class DeviceData {
    public int flight;
    public String status;
    public boolean save;

    DeviceDataBinding binding;

    public void update() {
        if (flight >= 0)
            binding.deviceFlight.setText(String.format("Flight %d", flight));
        else
            binding.deviceFlight.setText(String.format("Slot %d", -flight));
        binding.deviceStatus.setText(status);
    }

    public DeviceData(int in_flight, String in_status) {
        flight = in_flight;
        status = in_status;
    }
}

class DeviceDataAdapter extends ArrayAdapter<DeviceData> {

    int resource;
    SaveDataActivity save_data;

    public DeviceDataAdapter(SaveDataActivity in_context, int in_resource) {
        super(in_context, in_resource);
        resource = in_resource;
        save_data = in_context;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        DeviceData item = getItem(position);
        if (item.binding == null) {
            item.binding = DeviceDataBinding.inflate(save_data.getLayoutInflater(), parent, false);
            item.update();
        }
        return item.binding.getRoot();
    }
}

public class SaveDataActivity extends AppCompatActivity {
    SaveDataBinding binding;

    private DeviceDataAdapter data_adapter;

    private void done() {
        Intent intent = new Intent();
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        data_adapter = new DeviceDataAdapter(this, R.layout.device_data);

        binding = SaveDataBinding.inflate(getLayoutInflater());
        binding.saveDataList.setAdapter(data_adapter);

        data_adapter.add(new DeviceData(-2, "Invalid"));
        data_adapter.add(new DeviceData(11, "Complete"));

        binding.close.setOnClickListener(v -> done());

        setContentView(binding.getRoot());
        ActivityLayouts.applyEdgeToEdge(this, R.id.save_data);
    }
}
