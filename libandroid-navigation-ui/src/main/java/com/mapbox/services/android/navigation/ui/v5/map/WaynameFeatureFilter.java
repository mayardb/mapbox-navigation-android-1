package com.mapbox.services.android.navigation.ui.v5.map;

import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Geometry;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.MultiLineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.turf.TurfMeasurement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import timber.log.Timber;

import static com.mapbox.turf.TurfConstants.UNIT_METRES;
import static com.mapbox.turf.TurfMeasurement.along;
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

  private final MapboxMap map;
  private List<Marker> markers = new ArrayList<>();

  WaynameFeatureFilter(List<Feature> queriedFeatures, Location currentLocation, List<Point> currentStepPoints, MapboxMap map) {
    this.queriedFeatures = queriedFeatures;
    currentPoint = Point.fromLngLat(currentLocation.getLongitude(), currentLocation.getLatitude());
    currentStepLineString = LineString.fromLngLats(currentStepPoints);
    this.map = map;
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
    Timber.d("NAV_DEBUG *************************************** ***************************************");
    logNames(queriedFeatures);
    for (Feature feature : queriedFeatures) {
      Timber.d("NAV_DEBUG *************************************** Filtering %s", feature.getStringProperty("name"));
      Geometry featureGeometry = feature.geometry();
      if (featureGeometry == null) {
        continue;
      }
      // Convert feature geometry to LineStrings
      List<LineString> featureLineStrings = new ArrayList<>();
      if (featureGeometry instanceof LineString) {
        featureLineStrings.add((LineString) featureGeometry);
      } else if (featureGeometry instanceof MultiLineString) {
        featureLineStrings = ((MultiLineString) featureGeometry).lineStrings();
      }

      double smallestUserDistanceToFeature = Double.POSITIVE_INFINITY;
      for (LineString featureLineString : featureLineStrings) {

        // Point ahead on the feature
        List<Point> lineStringCoordinates = featureLineString.coordinates();
        int coordinateSize = lineStringCoordinates.size();
        if (coordinateSize < TWO_POINTS) {
          return null;
        }
        Point lastLinePoint = lineStringCoordinates.get(coordinateSize - 1);
        if (currentPoint == null || currentPoint.equals(lastLinePoint)) {
          return null;
        }
        LineString sliceFromCurrentPoint = lineSlice(currentPoint, lastLinePoint, featureLineString);
        Point pointAheadFeature = along(sliceFromCurrentPoint, 10, UNIT_METRES);
        Timber.d("NAV_DEBUG pointAheadFeature: %s", pointAheadFeature);
        addMarker(pointAheadFeature);

        // Point behind on the feature
        LineString reversedFeatureLineString = reverseFeatureLineStringCoordinates(featureLineString);
        List<Point> reversedFeatureLineStringCoordinates = reversedFeatureLineString.coordinates();
        int reversedCoordinateSize = reversedFeatureLineStringCoordinates.size();
        if (reversedCoordinateSize < TWO_POINTS) {
          return null;
        }
        Point lastReversedLinePoint = reversedFeatureLineStringCoordinates.get(0);
        if (currentPoint.equals(lastReversedLinePoint)) {
          return null;
        }
        LineString reverseSliceFromCurrentPoint = lineSlice(currentPoint, lastReversedLinePoint, reversedFeatureLineString);
        Point pointBehindFeature = along(reverseSliceFromCurrentPoint, 10, UNIT_METRES);
        Timber.d("NAV_DEBUG pointBehindFeature: %s", pointBehindFeature);
        addMarker(pointBehindFeature);

        // Point ahead on the step
        List<Point> currentStepCoordinates = currentStepLineString.coordinates();
        int stepCoordinateSize = currentStepCoordinates.size();
        if (stepCoordinateSize < TWO_POINTS) {
          return null;
        }
        Point lastStepPoint = currentStepCoordinates.get(stepCoordinateSize - 1);
        if (currentPoint.equals(lastStepPoint)) {
          return null;
        }
        LineString stepSliceFromCurrentPoint = lineSlice(currentPoint, lastStepPoint, currentStepLineString);
        Point pointAheadUserOnStep = along(stepSliceFromCurrentPoint, 10, UNIT_METRES);
        Timber.d("NAV_DEBUG pointAheadUserOnStep: %s", pointAheadUserOnStep);
        addMarker(pointAheadUserOnStep);

        double userDistanceToAheadFeature = calculateDistance(pointAheadUserOnStep, pointAheadFeature);
        Timber.d("NAV_DEBUG userDistanceToAheadFeature: %s", userDistanceToAheadFeature);

        double userDistanceToBehindFeature = calculateDistance(pointAheadUserOnStep, pointBehindFeature);
        Timber.d("NAV_DEBUG userDistanceToBehindFeature: %s", userDistanceToBehindFeature);

        double minDistanceToFeature = Math.min(userDistanceToAheadFeature, userDistanceToBehindFeature);
        Timber.d("NAV_DEBUG minDistanceToFeature: %s", minDistanceToFeature);

        if (minDistanceToFeature < smallestUserDistanceToFeature) {
          smallestUserDistanceToFeature = minDistanceToFeature;
          filteredFeature = feature;
        }
        clearMarkers();
      }
    }
    logName(filteredFeature);
    return filteredFeature;
  }

  private void addMarker(Point point) {
    markers.add(map.addMarker(new MarkerOptions().position(new LatLng(point.latitude(), point.longitude()))));
  }

  private void clearMarkers() {
//    for (Marker marker : markers) {
//      map.removeMarker(marker);
//    }
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
      Timber.d("NAV_DEBUG *************************************** filteredFeature found: %s", name);
    }
  }

  private double calculateDistance(Point lhs, Point rhs) {
    if (lhs == null || rhs == null) {
      return Double.POSITIVE_INFINITY;
    }
    return TurfMeasurement.distance(lhs, rhs);
  }

  @Nullable
  Point findPointFromCurrentPoint(Point currentPoint, double metersFromCurrentPoint, LineString lineString) {
    List<Point> lineStringCoordinates = lineString.coordinates();
    int coordinateSize = lineStringCoordinates.size();
    if (coordinateSize < TWO_POINTS) {
      return null;
    }
    Point lastLinePoint = lineStringCoordinates.get(coordinateSize - 1);
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
  private LineString reverseFeatureLineStringCoordinates(LineString featureLineString) {
    List<Point> reversedFeatureCoordinates = new ArrayList<>(featureLineString.coordinates());
    Collections.reverse(reversedFeatureCoordinates);
    return LineString.fromLngLats(reversedFeatureCoordinates);
  }
}
