/*
 * Copyright © 2016 Keith Packard <keithp@keithp.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
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

import java.text.*;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;

import org.altusmetrum.altosdroid.databinding.ManageFrequenciesBinding;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.altusmetrum.altoslib_14.*;

class FrequencyItem {
    public AltosFrequency frequency;
    public LinearLayout frequency_view = null;
    public TextView	pretty_view = null;

    private void update() {
        if (pretty_view != null && frequency != null)
            pretty_view.setText(frequency.toString());
    }

    public void realize(LinearLayout frequency_view,
                        TextView pretty_view) {
        if (frequency_view != this.frequency_view ||
            pretty_view != this.pretty_view)
        {
            this.frequency_view = frequency_view;
            this.pretty_view = pretty_view;
            update();
        }
    }

    public void set_frequency(AltosFrequency frequency) {
        this.frequency = frequency;
        update();
    }

    public FrequencyItem(AltosFrequency frequency) {
        this.frequency = frequency;
    }
}

class FrequencyAdapter extends ArrayAdapter<FrequencyItem> {
    int resource;
    int selected_item = -1;

    public FrequencyAdapter(Context context, int in_resource) {
        super(context, in_resource);
        resource = in_resource;
    }

    public int count() {
        int	count;

        for (count = 0;; count++) {
            try {
                getItem(count);
            } catch (IndexOutOfBoundsException ie) {
                return count;
            }
        }
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        FrequencyItem item = getItem(position);
        assert item != null;
        if (item.frequency_view == null) {
            LinearLayout frequency_view = new LinearLayout(getContext());
            String inflater = Context.LAYOUT_INFLATER_SERVICE;
            LayoutInflater li = (LayoutInflater) getContext().getSystemService(inflater);
            li.inflate(resource, frequency_view, true);

            item.realize(frequency_view,
                frequency_view.findViewById(R.id.frequency));
        }
        return item.frequency_view;
    }
}

public class ManageFrequenciesActivity extends AppCompatActivity {
    ManageFrequenciesBinding binding;

    private FrequencyAdapter frequencies_adapter;

    private boolean changed = false;

    private void done() {

        set();

        if (changed) {
            AltosFrequency[] frequencies = new AltosFrequency[frequencies_adapter.count()];
            for (int i = 0; i < frequencies.length; i++)
                frequencies[i] = frequencies_adapter.getItem(i).frequency;
            AltosPreferences.set_common_frequencies(frequencies);
        }

        Intent intent = new Intent();
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    private void load_item() {
        if (frequencies_adapter.selected_item >= 0) {
            FrequencyItem item = frequencies_adapter.getItem(frequencies_adapter.selected_item);

            assert item != null;
            binding.setFrequency.setText(item.frequency.frequency_string());
            binding.setDescription.setText(item.frequency.description);
        } else {
            binding.setFrequency.setText("");
            binding.setDescription.setText("");
        }
    }

    private void select_item(int position) {
        if (position != frequencies_adapter.selected_item) {
            if (frequencies_adapter.selected_item >= 0)
                binding.frequencies.setItemChecked(frequencies_adapter.selected_item, false);
            if (position >= 0)
                binding.frequencies.setItemChecked(position, true);
            frequencies_adapter.selected_item = position;
        } else {
            if (frequencies_adapter.selected_item >= 0)
                binding.frequencies.setItemChecked(frequencies_adapter.selected_item, false);
            frequencies_adapter.selected_item = -1;
        }
        load_item();
    }

    private int find(AltosFrequency frequency) {
        for (int pos = 0; pos < frequencies_adapter.getCount(); pos++) {
            FrequencyItem	item = frequencies_adapter.getItem(pos);
            assert item != null;
            if (item.frequency.frequency == frequency.frequency &&
                item.frequency.description.equals(frequency.description))
                return pos;
        }
        return -1;
    }

    private int insert_item(AltosFrequency frequency) {
        FrequencyItem new_item = new FrequencyItem(frequency);
        int	pos;
        for (pos = 0; pos < frequencies_adapter.getCount(); pos++) {
            FrequencyItem	item = frequencies_adapter.getItem(pos);
            assert item != null;
            if (item.frequency.frequency == new_item.frequency.frequency) {
                item.set_frequency(frequency);
                return pos;
            }
            if (item.frequency.frequency > new_item.frequency.frequency)
                break;
        }
        frequencies_adapter.insert(new_item, pos);
        return pos;
    }

    private class FrequencyItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int position, long id) {
            select_item(position);
        }
    }

    private void hide_keyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        View view = getCurrentFocus();
        if (view != null)
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void set() {
        String	frequency_text = binding.setFrequency.getEditableText().toString();
        String	description_text = binding.setDescription.getEditableText().toString();

        try {
            double	f = AltosParse.parse_double_locale(frequency_text);
            AltosFrequency frequency = new AltosFrequency(f, description_text);
            int pos;

            pos = find(frequency);
            if (pos < 0) {
                pos = insert_item(frequency);
                changed = true;
            }
            frequencies_adapter.selected_item = -1;
            select_item(pos);
        } catch (ParseException ignored) {
        }
        hide_keyboard();
    }

    private void remove() {
        if (frequencies_adapter.selected_item >= 0) {
            frequencies_adapter.remove(frequencies_adapter.getItem(frequencies_adapter.selected_item));
            select_item(-1);
            binding.frequencies.setAdapter(frequencies_adapter);
            changed = true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //setTheme(AltosDroid.dialog_themes[AltosDroidPreferences.font_size()]);
        super.onCreate(savedInstanceState);

        // Setup the window
        binding = ManageFrequenciesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ActivityLayouts.applyEdgeToEdge(this, R.id.manage_frequencies);

        frequencies_adapter = new FrequencyAdapter(this, R.layout.frequency);

        binding.frequencies.setAdapter(frequencies_adapter);
        binding.frequencies.setOnItemClickListener(new FrequencyItemClickListener());

        AltosFrequency[] frequencies = AltosPreferences.common_frequencies();
        for (AltosFrequency frequency : frequencies)
            insert_item(frequency);

        binding.set.setOnClickListener(v -> set());
        binding.remove.setOnClickListener(v -> remove());
        binding.done.setOnClickListener(v -> done());

        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
