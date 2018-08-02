package com.mapbox.services.android.navigation.v5.location.replay;

import android.location.Location;
import android.os.Handler;
import android.support.annotation.NonNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public class ReplayLocationDispatcher implements Runnable {

  private static final String NON_NULL_AND_NON_EMPTY_LOCATION_LIST_REQUIRED = "Non-null and non-empty location list "
    + "required.";
  private static final int HEAD = 0;
  private List<Location> locationsToReplay;
  private Location current;
  private Handler handler;
  private CopyOnWriteArraySet<ReplayLocationListener> replayLocationListeners;
  private boolean isLastLocationDispatched;

  ReplayLocationDispatcher(@NonNull List<Location> locationsToReplay) {
    checkValidInput(locationsToReplay);
    this.locationsToReplay = new CopyOnWriteArrayList<>(locationsToReplay);
    initialize();
    this.replayLocationListeners = new CopyOnWriteArraySet<>();
    this.handler = new Handler();
  }

  // For testing only
  ReplayLocationDispatcher(List<Location> locationsToReplay, Handler handler) {
    checkValidInput(locationsToReplay);
    this.locationsToReplay = locationsToReplay;
    initialize();
    this.replayLocationListeners = new CopyOnWriteArraySet<>();
    this.handler = handler;
  }

  @Override
  public void run() {
    if (!isLastLocationDispatched) {
      dispatchLocation(current);
      scheduleNextDispatch();
    }
  }

  void stop() {
    clearLocations();
    stopDispatching();
    lastLocationDispatched();
  }

  void pause() {
    stopDispatching();
  }

  void update(@NonNull List<Location> locationsToReplay) {
    checkValidInput(locationsToReplay);
    this.locationsToReplay = new CopyOnWriteArrayList<>(locationsToReplay);
    initialize();
  }

  void add(@NonNull List<Location> toReplay) {
    addLocations(toReplay);
  }

  void addReplayLocationListener(ReplayLocationListener listener) {
    replayLocationListeners.add(listener);
  }

  void removeReplayLocationListener(ReplayLocationListener listener) {
    replayLocationListeners.remove(listener);
  }

  private void checkValidInput(List<Location> locations) {
    boolean isValidInput = locations == null || locations.isEmpty();
    if (isValidInput) {
      throw new IllegalArgumentException(NON_NULL_AND_NON_EMPTY_LOCATION_LIST_REQUIRED);
    }
  }

  private void initialize() {
    current = locationsToReplay.remove(HEAD);
    isLastLocationDispatched = false;
  }

  private void addLocations(List<Location> toReplay) {
    locationsToReplay.addAll(toReplay);
  }

  private void dispatchLocation(Location location) {
    for (ReplayLocationListener listener : replayLocationListeners) {
      listener.onLocationReplay(location);
    }
  }

  private void scheduleNextDispatch() {
    if (!locationsToReplay.isEmpty()) {
      long currentTime = current.getTime();
      current = locationsToReplay.remove(HEAD);
      long nextTime = current.getTime();
      long diff = nextTime - currentTime;
      handler.postDelayed(this, diff);
    } else {
      stopDispatching();
      lastLocationDispatched();
    }
  }

  private void clearLocations() {
    locationsToReplay.clear();
  }

  private void stopDispatching() {
    handler.removeCallbacks(this);
  }

  private void lastLocationDispatched() {
    isLastLocationDispatched = true;
  }
}