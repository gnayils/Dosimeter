package com.gnayils.dosimeter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.anderson.dashboardview.view.DashboardView;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private CameraPreview cameraPreview;

    private DashboardView dashboardView;

    private Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            double dose = (double) msg.obj;
            dashboardView.setPercent((int) (dose > 100 ? 100 : dose));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        dashboardView = findViewById(R.id.dashboard_view);

        cameraPreview = new CameraPreview(this, handler);

        FloatingActionButton fabClear = (FloatingActionButton) findViewById(R.id.fab_clear);
        fabClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
    protected void onResume() {
        super.onResume();
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    Tooltip.showToast(this, getString(R.string.request_permission));
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                }
            }
            return;
        }
        cameraPreview.resume();
    }

    @Override
    protected void onPause() {
        cameraPreview.pause();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION) {
            if(grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_DENIED) {
                Tooltip.showToast(this, getString(R.string.request_permission));
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
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
