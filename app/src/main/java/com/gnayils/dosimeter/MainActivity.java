package com.gnayils.dosimeter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.anderson.dashboardview.view.DashboardView;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private CameraPreview cameraPreview;
    private DashboardView dashboardView;
    private LinearLayout doseRecordListView;
    private FloatingActionButton monitorSwitchButton;

    private Handler handler = new Handler() {


        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

        @Override
        public void handleMessage(Message msg) {
            double dose = (double) msg.obj;
            dashboardView.setPercent((int) ((dose > 60 ? 60 : dose) / 60 * 100));
            ViewGroup itemView = (ViewGroup) LayoutInflater.from(MainActivity.this).inflate(R.layout.item_view_dose_value, doseRecordListView, false);
            ((TextView) itemView.findViewById(R.id.text_view_record_time)).setText(dateFormat.format(Calendar.getInstance().getTime()));
            ((TextView) itemView.findViewById(R.id.text_view_dose_value)).setText(String.format("%.2f", dose));
                if(doseRecordListView.getChildCount() > 30) {
                doseRecordListView.removeViewAt(doseRecordListView.getChildCount() - 1);
            }
            doseRecordListView.addView(itemView, 0);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        dashboardView = findViewById(R.id.dashboard_view);
        doseRecordListView = findViewById(R.id.linear_layout_dose_record);
        cameraPreview = new CameraPreview(this, handler);

        monitorSwitchButton = findViewById(R.id.fab_toggle);
        monitorSwitchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cameraPreview.toggleDoseCalculation();
                if(cameraPreview.isCalculateDose()) {
                    monitorSwitchButton.setImageDrawable(getDrawable(R.drawable.ic_pause));
                    Tooltip.showToast(MainActivity.this, "Start to monitor the radiation around you");
                } else {
                    monitorSwitchButton.setImageDrawable(getDrawable(R.drawable.ic_play));
                    Tooltip.showToast(MainActivity.this, "Monitor stopped");
                }
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
