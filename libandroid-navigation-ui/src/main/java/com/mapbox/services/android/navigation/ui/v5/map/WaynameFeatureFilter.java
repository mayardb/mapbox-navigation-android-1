package com.mapbox.services.android.navigation.ui.v5.map;

import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Geometry;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.MultiLineString;
import com.mapbox.geojson.Point;
import com.mapbox.turf.TurfMeasurement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import timber.log.Timber;

import static com.mapbox.turf.TurfConstants.UNIT_METRES;
import static com.mapbox.turf.TurfMisc.lineSlice;
import static com.mapbox.turf.TurfMisc.lineSliceAlong;

class WaynameFeatureFilter {

  private static final int FIRST_FEATURE = 0;
  private static final int ONE_FEATURE = 1;
  private static final int TWO_POINTS = 2;
  private static final double ZERO_METERS = 0d;
  private static final double TEN_METERS = 10d;
  private final List<Feature> queriedFeatures;
  private final Point currentPoint;
  private final LineString currentStepLineString;

  WaynameFeatureFilter(List<Feature> queriedFeatures, Location currentLocation, List<Point> currentStepPoints) {
    this.queriedFeatures = queriedFeatures;
    currentPoint = Point.fromLngLat(currentLocation.getLongitude(), currentLocation.getLatitude());
    currentStepLineString = LineString.fromLngLats(currentStepPoints);
  }

  @NonNull
  Feature filter() {
    return filterQueriedFeatures();
  }

  private Feature filterQueriedFeatures() {
    Feature filteredFeature = queriedFeatures.get(FIRST_FEATURE);
    if (queriedFeatures.size() == ONE_FEATURE) {
      return filteredFeature;
    }
    Timber.d("NAV_DEBUG ***************************************");
    logNames(queriedFeatures);
    for (Feature feature : queriedFeatures) {
      List<LineString> featureLineStrings = new ArrayList<>();
      Geometry featureGeometry = feature.geometry();
      if (featureGeometry == null) {
        continue;
      }
      // Convert feature geometry to LineStrings
      if (featureGeometry instanceof LineString) {
        featureLineStrings.add((LineString) featureGeometry);
      } else if (featureGeometry instanceof MultiLineString) {
        featureLineStrings.addAll(((MultiLineString) featureGeometry).lineStrings());
      }

      double smallestUserDistanceToFeature = Double.POSITIVE_INFINITY;
      for (LineString featureLineString : featureLineStrings) {
        // 10 meters ahead of current location on the feature LineString
        Point pointAheadFeature = findPointFromCurrentPoint(currentPoint, TEN_METERS, featureLineString);
        Timber.d("NAV_DEBUG pointAheadFeature: %s", pointAheadFeature);
        // 10 meters behind the current location on the feature LineString
        LineString reversedFeatureLineString = reverseFeatureLineString(featureLineString);
        Point pointBehindFeature = findPointFromCurrentPoint(currentPoint, TEN_METERS, reversedFeatureLineString);
        Timber.d("NAV_DEBUG pointBehindFeature: %s", pointBehindFeature);
        // 10 meters ahead of the current location on the step LineString
        Point pointAheadUser = findPointFromCurrentPoint(currentPoint, TEN_METERS, currentStepLineString);
        Timber.d("NAV_DEBUG pointAheadUser: %s", pointAheadUser);

        double userDistanceToAheadFeature = calculateDistance(pointAheadUser, pointAheadFeature);
        Timber.d("NAV_DEBUG userDistanceToAheadFeature: %s", userDistanceToAheadFeature);
        double userDistanceToBehindFeature = calculateDistance(pointAheadUser, pointBehindFeature);
        Timber.d("NAV_DEBUG userDistanceToBehindFeature: %s", userDistanceToBehindFeature);
        double smallestDistanceToFeature = Math.min(userDistanceToAheadFeature, userDistanceToBehindFeature);
        Timber.d("NAV_DEBUG smallestDistanceToFeature: %s", smallestDistanceToFeature);

        if (smallestDistanceToFeature < smallestUserDistanceToFeature) {
          logName(feature);
          smallestUserDistanceToFeature = smallestDistanceToFeature;
          filteredFeature = feature;
        }
      }
    }
    return filteredFeature;
  }

  private void logNames(List<Feature> queriedFeatures) {
    List<String> names = new ArrayList<>();
    for (Feature feature : queriedFeatures) {
      boolean hasValidNameProperty = feature.hasNonNullValueForProperty("name");
      if (hasValidNameProperty) {
        names.add(feature.getStringProperty("name"));
      }
    }
    Timber.d("NAV_DEBUG Queried names: %s", names);
  }

  private void logName(Feature feature) {
    boolean hasValidNameProperty = feature.hasNonNullValueForProperty("name");
    if (hasValidNameProperty) {
      String name = feature.getStringProperty("name");
      Timber.d("NAV_DEBUG filteredFeature found: %s", name);
    }
  }

  private double calculateDistance(Point pointAheadUser, Point pointAheadFeature) {
    if (pointAheadUser == null || pointAheadFeature == null) {
      return Double.POSITIVE_INFINITY;
    }
    return TurfMeasurement.distance(pointAheadUser, pointAheadFeature);
  }

  @Nullable
  Point findPointFromCurrentPoint(Point currentPoint, double metersFromCurrentPoint, LineString lineString) {
    List<Point> lineStringPoints = lineString.coordinates();
    int lineStringPointsSize = lineStringPoints.size();
    if (lineStringPointsSize < TWO_POINTS) {
      return null;
    }
    Point lastLinePoint = lineStringPoints.get(lineStringPointsSize - 1);
    if (currentPoint == null || currentPoint.equals(lastLinePoint)) {
      return null;
    }
    // TODO find nearestPointOnLine instead of currentPoint?
    LineString sliceFromCurrentPoint = lineSlice(currentPoint, lastLinePoint, lineString);
    LineString meterSlice = lineSliceAlong(sliceFromCurrentPoint, ZERO_METERS,
      metersFromCurrentPoint, UNIT_METRES);
    List<Point> slicePoints = meterSlice.coordinates();
    if (slicePoints.isEmpty()) {
      return null;
    }
    // TODO use 0 here?
    return slicePoints.get(0);
  }

  @NonNull
  private LineString reverseFeatureLineString(LineString featureLineString) {
    List<Point> reversedFeaturePoints = new ArrayList<>(featureLineString.coordinates());
    Collections.reverse(featureLineString.coordinates());
    return LineString.fromLngLats(reversedFeaturePoints);
  }
}
