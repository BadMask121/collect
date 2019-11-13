/*
 * Copyright (C) 2011 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.geo.MapFragment;
import org.odk.collect.android.geo.MapPoint;
import org.odk.collect.android.geo.MapProvider;
import org.odk.collect.android.preferences.MapsPreferences;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;

import timber.log.Timber;

/** Show a map with points representing saved instances of the selected form. */
public class MapActivity extends BaseGeoMapActivity {
    public static final String MAP_CENTER_KEY = "map_center";
    public static final String MAP_ZOOM_KEY = "map_zoom";

    private MapFragment map;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.instance_map_layout);

        TextView titleView = findViewById(R.id.form_title);
        titleView.setText(getIntent().getDataString());

        Context context = getApplicationContext();
        MapProvider.createMapFragment(context)
            .addTo(this, R.id.map_container, this::initMap, this::finish);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (map == null) {
            // initMap() is called asynchronously, so map can be null if the activity
            // is stopped (e.g. by screen rotation) before initMap() gets to run.
            // In this case, preserve any provided instance state.
            if (previousState != null) {
                state.putAll(previousState);
            }
            return;
        }
        state.putParcelable(MAP_CENTER_KEY, map.getCenter());
        state.putDouble(MAP_ZOOM_KEY, map.getZoom());
    }

    @SuppressLint("MissingPermission") // Permission handled in Constructor
    public void initMap(MapFragment newMapFragment) {
        map = newMapFragment;

        findViewById(R.id.zoom).setOnClickListener(v ->
            map.zoomToPoint(map.getGpsLocation(), true));

        findViewById(R.id.layer_menu).setOnClickListener(v -> {
            MapsPreferences.showReferenceLayerDialog(this);
        });

        findViewById(R.id.new_instance).setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_EDIT, getIntent().getData()));
        });

        map.setGpsLocationEnabled(true);

        if (previousState != null) {
            restoreFromInstanceState(previousState);
        }

        addInstanceGeometry(getIntent().getData());
    }

    protected void addInstanceGeometry(Uri contentUri) {
        String jrFormId = null;
        try (Cursor c = Collect.getInstance().getContentResolver().query(
            contentUri, new String[] {FormsColumns.JR_FORM_ID}, null, null, null)) {
            if (c.moveToFirst()) {
                jrFormId = c.getString(0);
                Timber.i("jrFormId = %s", jrFormId);
            }
        }

        try (Cursor c = Collect.getInstance().getContentResolver().query(
            InstanceColumns.CONTENT_URI,
            new String[] {InstanceColumns.GEOMETRY_TYPE, InstanceColumns.GEOMETRY},
            InstanceColumns.JR_FORM_ID + " = ? AND " +
                InstanceColumns.GEOMETRY + " IS NOT NULL",
            new String[] {jrFormId},
            null)) {
            while (c.moveToNext()) {
                String json = c.getString(1);
                try {
                    JSONObject geometry = new JSONObject(json);
                    switch (c.getString(0)) {
                        case "Point":
                            JSONArray coordinates = geometry.getJSONArray("coordinates");
                            // In GeoJSON, longitude comes before latitude.
                            double lon = coordinates.getDouble(0);
                            double lat = coordinates.getDouble(1);
                            map.addMarker(new MapPoint(lat, lon), false);
                    }
                } catch (JSONException e) {
                    Timber.w("Invalid JSON in instances table: %s", json);
                }
            }
        }
    }

    protected void restoreFromInstanceState(Bundle state) {
        MapPoint mapCenter = state.getParcelable(MAP_CENTER_KEY);
        double mapZoom = state.getDouble(MAP_ZOOM_KEY);
        if (mapCenter != null) {
            map.zoomToPoint(mapCenter, mapZoom, false);
        }
    }
}