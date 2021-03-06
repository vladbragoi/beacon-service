/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Francesco Tonini <francescoantoniotonini@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package me.francescotonini.beaconservice.views;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.widget.Toast;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import me.francescotonini.beaconservice.AppExecutors;
import me.francescotonini.beaconservice.BeaconServiceApp;
import me.francescotonini.beaconservice.Logger;
import me.francescotonini.beaconservice.R;
import me.francescotonini.beaconservice.databinding.ActivityMainBinding;
import me.francescotonini.beaconservice.db.AppDatabase;
import me.francescotonini.beaconservice.models.AP;
import me.francescotonini.beaconservice.models.Beacon;
import me.francescotonini.beaconservice.services.BeaconService;
import me.francescotonini.beaconservice.services.WifiService;

public class MainActivity extends BaseActivity {
    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected void setToolbar() {
        setSupportActionBar((Toolbar)binding.toolbar);
    }

    @Override
    protected void setBinding() {
        binding = DataBindingUtil.setContentView(this, getLayoutId());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        askForPermissions();

        appExecutors = ((BeaconServiceApp)getApplication()).getDataRepository().getAppExecutors();

        database = ((BeaconServiceApp)getApplication()).getDataRepository().getDatabase();
        database.beaconDao().getAll().observe(this, beacons -> {
            this.beacons = beacons;
            binding.numberOfBeacons.setText(String.format("%d beacon salvati in locale", this.beacons.size()));
        });

        database.apDao().getAll().observe(this, aps -> {
            this.aps = aps;
            binding.numberOfAps.setText(String.format("%d ap salvati in locale", this.aps.size()));
        });

        binding.startService.setOnClickListener(click -> startForegroundService());
        binding.stopService.setOnClickListener(click -> stopForegroundService());
        binding.cleanData.setOnClickListener(click -> appExecutors.diskIO().execute(() -> {
            database.beaconDao().clear();
            database.apDao().clear();
        }));
        binding.exportData.setOnClickListener(click -> {
            Gson gson = new Gson();
            writeToFile("beacon", gson.toJson(beacons));
            writeToFile("ap", gson.toJson(aps));
        });
    }

    private void startForegroundService() {
        Logger.d(MainActivity.class.getSimpleName(), "Starting foreground service");

        Intent beaconService = new Intent(this, BeaconService.class);
        beaconService.setAction(BeaconService.ACTIONS.START.toString());
        startService(beaconService);

        Intent wifiService = new Intent(this, WifiService.class);
        wifiService.setAction(WifiService.ACTIONS.START.toString());
        startService(wifiService);
    }

    private void stopForegroundService() {
        Logger.d(MainActivity.class.getSimpleName(), "Stopping foreground service");

        Intent beaconService = new Intent(this, BeaconService.class);
        beaconService.setAction(BeaconService.ACTIONS.STOP.toString());
        startService(beaconService);

        Intent wifiService = new Intent(this, WifiService.class);
        wifiService.setAction(WifiService.ACTIONS.STOP.toString());
        startService(wifiService);
    }

    private void askForPermissions() {
        List<String> permissionsToAsk = new ArrayList<>();
        int requestResult = 0;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            // Ask for permission
            permissionsToAsk.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            // Ask for permission
            permissionsToAsk.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) !=
                PackageManager.PERMISSION_GRANTED) {
            // Ask for permission
            permissionsToAsk.add(Manifest.permission.CHANGE_WIFI_STATE);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) !=
                PackageManager.PERMISSION_GRANTED) {
            // Ask for permission
            permissionsToAsk.add(Manifest.permission.ACCESS_WIFI_STATE);
        }

        if (permissionsToAsk.size() > 0) {
            ActivityCompat.requestPermissions(this, permissionsToAsk.toArray(new String[permissionsToAsk.size()]), requestResult);
        }
    }

    private void writeToFile(String filename, String json) {
        try {
            File root = new File(Environment.getExternalStorageDirectory(), "Beacon service");
            if (!root.exists()) {
                root.mkdirs();
            }
            File gpxfile = new File(root, String.format("%s_%d.json", filename, System.currentTimeMillis()));
            FileWriter writer = new FileWriter(gpxfile);
            writer.append(json);
            writer.flush();
            writer.close();
            Toast.makeText(this, "Dati esportati nella cartella \"Beacon service\"", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ActivityMainBinding binding;
    private AppDatabase database;
    private List<Beacon> beacons;
    private List<AP> aps;
    private AppExecutors appExecutors;
}
