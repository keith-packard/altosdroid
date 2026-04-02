package org.altusmetrum.altosdroid;

import android.content.Context;
import android.graphics.Paint;

import java.util.Locale;

public class RocketMarker extends AltosMarker {
    public RocketMarker(Context context, int id) {
        super(context, R.drawable.flight_orange, 1.0f, 0.0f);

        String text = String.format(Locale.ROOT, "%d", id);
        MainActivity.draw_text(context, canvas, text, width/2, height/2, Paint.Align.CENTER);
    }
}
