package org.altusmetrum.altosdroid;

import android.view.View;
import android.view.ViewGroup;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class ActivityLayouts {

    private ActivityLayouts() {}

    public static void applyEdgeToEdge(ComponentActivity activity, int viewId) {

        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);

        View view = activity.findViewById(viewId);

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.leftMargin = insets.left;
            params.rightMargin = insets.right;
            params.topMargin = insets.top;
            params.bottomMargin = insets.bottom;
            v.setLayoutParams(params);
            return windowInsets;
        });

    }
}
