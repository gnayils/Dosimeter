package com.gnayils.dosimeter;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private ScrollView scrollView;
    private TextView textView;
    private CameraPreview cameraPreview;
    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            if(msg.what == 0) {
                long[] params = (long[]) msg.obj;
                textView.append(params[0] + ",  " + params[1] + ",  " + params[2] + "\n");
            } else if (msg.what == 1) {
                long[] params = (long[]) msg.obj;
                textView.append("[" + params[0] + ",  " + params[1] + ",  " + params[2] + "]\n");
            } else if(msg.what == 2) {
                textView.append(msg.obj + "\n");
            }
            scrollView.fullScroll(ScrollView.FOCUS_DOWN);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        scrollView = (ScrollView) findViewById(R.id.scroll_view);
        textView = (TextView) findViewById(R.id.text_view);

        cameraPreview = new CameraPreview(this, handler);
        FrameLayout previewContainer = (FrameLayout) findViewById(R.id.frame_layout_preview_container);
        previewContainer.addView(cameraPreview, 0);

        FloatingActionButton fabClear = (FloatingActionButton) findViewById(R.id.fab_clear);
        fabClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textView.setText("");
            }
        });

        FloatingActionButton fabToggle = (FloatingActionButton) findViewById(R.id.fab_toggle);
        fabToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cameraPreview.toggleDoseCalculation();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
