package com.equimaps.capacitor_background_geolocation;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;

import com.getcapacitor.Logger;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.HashSet;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

// A bound and started service that is promoted to a foreground service when
// location updates have been requested and the main activity is stopped.
//
// When an activity is bound to this service, frequent location updates are
// permitted. When the activity is removed from the foreground, the service
// promotes itself to a foreground service, and location updates continue. When
// the activity comes back to the foreground, the foreground service stops, and
// the notification associated with that service is removed.
public class BackgroundGeolocationService extends Service {
    static final String ACTION_BROADCAST = (BackgroundGeolocationService.class.getPackage().getName() + ".broadcast");
    private final IBinder binder = new LocalBinder();

    // Must be unique for this application.
    private static final int NOTIFICATION_ID = 28351;

    private LocationManager locationManager;
    private Criteria criteria;
    public static Criteria criteria000;
    private boolean isStarted = false;
    private LocationListener mLocationListener;
    private volatile Location lastGPSLocation;

    @Override
    public void onCreate() {
        super.onCreate();

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Location criteria
        criteria = new Criteria();
        criteria000 = criteria;
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setSpeedRequired(true);
        criteria.setCostAllowed(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private class Watcher {
        public String id;
        public FusedLocationProviderClient client;
        public LocationListener locationListener;
        public Float distanceFilter;
        public LocationRequest locationRequest;
        public LocationCallback locationCallback;
        public Notification backgroundNotification;
    }

    private HashSet<Watcher> watchers = new HashSet<Watcher>();

    Notification getNotification() {
        for (Watcher watcher : watchers) {
            if (watcher.backgroundNotification != null) {
                return watcher.backgroundNotification;
            }
        }
        return null;
    }

    // Handles requests from the activity.
    public class LocalBinder extends Binder {
        void addWatcher(final String id, Notification backgroundNotification, float distanceFilter) {
            int gmsResultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
            boolean supportGPS = gmsResultCode == ConnectionResult.SUCCESS;

            // 位置监听器
            mLocationListener = new LocationListener() {
                @Override
                public void onLocationChanged(final Location location) {
                    Logger.debug("Location received");
                    Intent intent = new Intent(ACTION_BROADCAST);
                    intent.putExtra("location", location);
                    intent.putExtra("id", id);
                    LocalBroadcastManager.getInstance(
                            getApplicationContext()
                    ).sendBroadcast(intent);
                }

                @Override
                public void onProviderEnabled(@NonNull String provider) {

                }

                @Override
                public void onProviderDisabled(@NonNull String provider) {

                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {

                }
            };

            Logger.debug("GOOGLE:" + supportGPS);
            if (supportGPS) {
                FusedLocationProviderClient client = LocationServices
                        .getFusedLocationProviderClient(BackgroundGeolocationService.this);

                LocationRequest locationRequest = new LocationRequest();
                locationRequest.setMaxWaitTime(1000);
                locationRequest.setInterval(1000);
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                locationRequest.setSmallestDisplacement(distanceFilter);

                LocationCallback callback = new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        Location location = locationResult.getLastLocation();
                        Intent intent = new Intent(ACTION_BROADCAST);
                        intent.putExtra("location", location);
                        intent.putExtra("id", id);
                        LocalBroadcastManager.getInstance(
                                getApplicationContext()
                        ).sendBroadcast(intent);
                    }

                    @Override
                    public void onLocationAvailability(LocationAvailability availability) {
                        if (!availability.isLocationAvailable()) {
                            Logger.debug("Location not available");
                        }
                    }
                };
                Watcher watcher = new Watcher();
                watcher.id = id;
                watcher.client = client;
                watcher.locationRequest = locationRequest;
                watcher.distanceFilter = distanceFilter;
                watcher.locationCallback = callback;
                watcher.backgroundNotification = backgroundNotification;
                watchers.add(watcher);

                // According to Android Studio, this method can throw a Security Exception if
                // permissions are not yet granted. Rather than check the permissions, which is fiddly,
                // we simply ignore the exception.
                try {
                    watcher.client.requestLocationUpdates(
                            watcher.locationRequest,
                            watcher.locationCallback,
                            null
                    );
                } catch (SecurityException ignore) {
                }
            } else {
                Logger.debug("Google Play Services not available, using Android location APIs");
                LocationListener locationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(@NonNull Location location) {
                        if (lastGPSLocation == null || (lastGPSLocation.getTime() <= location.getTime())) {
                            lastGPSLocation = location;
                        }
                        mLocationListener.onLocationChanged(location);
                    }

                    @Override
                    public void onProviderEnabled(@NonNull String provider) {

                    }

                    @Override
                    public void onProviderDisabled(@NonNull String provider) {

                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {

                    }
                };
                Watcher watcher = new Watcher();
                watcher.id = id;
                watcher.backgroundNotification = backgroundNotification;
                watcher.locationListener = locationListener;
                watchers.add(watcher);
                criteria.setAccuracy(Criteria.ACCURACY_FINE);
                criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
                criteria.setPowerRequirement(Criteria.POWER_HIGH);
                String provider = locationManager.getBestProvider(criteria, true);

                locationManager.requestLocationUpdates(provider, 1000, distanceFilter, watcher.locationListener);

                Logger.debug("success");
            }
        }

        void removeWatcher(String id) {
            int gmsResultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(getApplicationContext());
            boolean isGMS = gmsResultCode == ConnectionResult.SUCCESS;
            for (Watcher watcher : watchers) {
                if (watcher.id.equals(id)) {
                    if (!isGMS) {
                        Logger.debug("删除监听器：" + String.valueOf(watcher.locationListener));
                        locationManager.removeUpdates(watcher.locationListener);
                    } else {
                        watcher.client.removeLocationUpdates(watcher.locationCallback);
                    }
                    watchers.remove(watcher);
                    Logger.debug("剩余监听器：" + watchers.size());
                    if (getNotification() == null) {
                        stopForeground(true);
                    }
                    return;
                }
            }
        }

        void onPermissionsGranted() {
            // If permissions were granted while the app was in the background, for example in
            // the Settings app, the watchers need restarting.
            for (Watcher watcher : watchers) {
                if (watcher.locationListener != null) {
                    String provider = locationManager.getBestProvider(criteria, true);
                    locationManager.requestLocationUpdates(provider, 1000, watcher.distanceFilter, watcher.locationListener);
                }
                watcher.client.removeLocationUpdates(watcher.locationCallback);
                watcher.client.requestLocationUpdates(
                        watcher.locationRequest,
                        watcher.locationCallback,
                        null
                );
            }
        }

        void onActivityStarted() {
            stopForeground(true);
        }

        void onActivityStopped() {
            Notification notification = getNotification();
            if (notification != null) {
                try {
                    // Android 12 has a bug
                    // (https://issuetracker.google.com/issues/229000935)
                    // whereby it mistakenly thinks the app is in the
                    // foreground at this point, even though it is not. This
                    // causes a ForegroundServiceStartNotAllowedException to be
                    // raised, crashing the app unless we suppress it here.
                    // See issue #86.
                    startForeground(NOTIFICATION_ID, notification);
                } catch (Exception exception) {
                    Logger.error("Failed to start service", exception);
                }
            }
        }

        void stopService() {
            BackgroundGeolocationService.this.stopSelf();
        }
    }
}
