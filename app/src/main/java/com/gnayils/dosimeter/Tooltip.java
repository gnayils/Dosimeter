package com.gnayils.dosimeter;

import android.app.Activity;
import android.content.Context;

/**
 * Created by Gnayils on 1/4/2018.
 */

public class Tooltip {

    public static void showToast(final Activity activity, final String text) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                android.widget.Toast.makeText(activity, text, android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }
}
