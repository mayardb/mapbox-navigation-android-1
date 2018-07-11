package com.mapbox.services.android.navigation.v5.location.replay;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ReplayRouteDto {

  private List<ReplayLocationDto> locations;
  @SerializedName("route")
  private String routeRequest;

  public List<ReplayLocationDto> getLocations() {
    return locations;
  }

  public void setLocations(List<ReplayLocationDto> locations) {
    this.locations = locations;
  }

  public String getRouteRequest() {
    return routeRequest;
  }

  public void setRouteRequest(String routeRequest) {
    this.routeRequest = routeRequest;
  }
}
