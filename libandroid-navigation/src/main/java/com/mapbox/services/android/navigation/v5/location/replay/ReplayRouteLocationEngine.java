package com.mapbox.services.android.navigation.v5.location.replay;

import android.annotation.SuppressLint;
import android.location.Location;
import android.os.Handler;
import android.support.annotation.Nullable;

import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineListener;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;

import java.util.ArrayList;
import java.util.List;


public class ReplayRouteLocationEngine extends LocationEngine implements Runnable {

  private static final int HEAD = 0;
  private ReplayRouteLocationConverter converter;
  private Handler handler;
  private List<Location> mockedLocations;
  private ReplayLocationDispatcher dispatcher;
  private Location lastLocation = new Location("ReplayRouteLocation");
  private final ReplayLocationListener replayLocationListener = new ReplayLocationListener() {
    @Override
    public void onLocationReplay(Location location) {
      for (LocationEngineListener listener : locationListeners) {
        listener.onLocationChanged(location);
      }
      lastLocation = location;
      mockedLocations.remove(HEAD);
    }
  };

  public ReplayRouteLocationEngine() {
    this.handler = new Handler();
  }

  @SuppressLint("MissingPermission")
  public void assign(DirectionsRoute route) {
    handler.removeCallbacks(this);
    converter = new ReplayRouteLocationConverter(route);
    mockedLocations = converter.toLocations();
    dispatcher = obtainDispatcher();
    dispatcher.run();
    int mockedPoints = mockedLocations.size();
    if (mockedPoints <= 5) {
      handler.postDelayed(this, 1000);
    } else {
      handler.postDelayed(this, (mockedPoints - 5) * 1000);
    }
  }

  public void assignLastLocation(Point currentPosition) {
    lastLocation.setLongitude(currentPosition.longitude());
    lastLocation.setLatitude(currentPosition.latitude());
  }

  @SuppressLint("MissingPermission")
  public void moveTo(Point point) {
    Location lastLocation = getLastLocation();
    if (lastLocation == null) {
      return;
    }

    List<Point> pointList = new ArrayList<>();
    pointList.add(Point.fromLngLat(lastLocation.getLongitude(), lastLocation.getLatitude()));
    pointList.add(point);

    LineString route = LineString.fromLngLats(pointList);

    handler.removeCallbacks(this);
    mockedLocations = converter.calculateMockLocations(converter.sliceRoute(route));
    dispatcher = obtainDispatcher();
    dispatcher.run();
  }

  @Override
  public void run() {
    if (mockedLocations.isEmpty()) {
      handler.removeCallbacks(this);
    } else {
      List<Location> nextMockedLocations = converter.toLocations();
      dispatcher.add(nextMockedLocations);
      mockedLocations.addAll(nextMockedLocations);
      int currentMockedPoints = mockedLocations.size();
      if (currentMockedPoints <= 5) {
        handler.postDelayed(this, 1000);
      } else {
        handler.postDelayed(this, (currentMockedPoints - 5) * 1000);
      }
    }
  }

  /**
   * Connect all the location listeners.
   */
  @Override
  public void activate() {
    for (LocationEngineListener listener : locationListeners) {
      listener.onConnected();
    }
  }

  @Override
  public void deactivate() {
    if (dispatcher != null) {
      dispatcher.stop();
    }
    handler.removeCallbacks(this);
  }

  /**
   * While the {@link ReplayRouteLocationEngine} is in use, you are always connected to it.
   *
   * @return true.
   */
  @Override
  public boolean isConnected() {
    return true;
  }

  @SuppressLint("MissingPermission")
  @Override
  @Nullable
  public Location getLastLocation() {
    return lastLocation;
  }

  /**
   * Nothing needs to happen here since we are mocking the user location along a route.
   */
  @Override
  public void requestLocationUpdates() {

  }

  /**
   * Removes location updates for the LocationListener.
   */
  @Override
  public void removeLocationUpdates() {
    for (LocationEngineListener listener : locationListeners) {
      locationListeners.remove(listener);
    }
    if (dispatcher != null) {
      dispatcher.removeReplayLocationListener(replayLocationListener);
    }
  }

  @Override
  public Type obtainType() {
    return Type.MOCK;
  }

  private ReplayLocationDispatcher obtainDispatcher() {
    if (dispatcher != null) {
      dispatcher.stop();
      dispatcher.removeReplayLocationListener(replayLocationListener);
    }
    dispatcher = new ReplayLocationDispatcher(mockedLocations);
    dispatcher.addReplayLocationListener(replayLocationListener);

    return dispatcher;
  }
}
