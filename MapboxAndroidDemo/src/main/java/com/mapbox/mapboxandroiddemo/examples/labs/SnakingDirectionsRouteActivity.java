package com.mapbox.mapboxandroiddemo.examples.labs;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.MapboxDirections;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.api.directions.v5.models.LegStep;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxandroiddemo.R;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.turf.TurfMeasurement;

import java.util.ArrayList;
import java.util.List;

import static com.mapbox.api.directions.v5.DirectionsCriteria.GEOMETRY_POLYLINE6;
import static com.mapbox.core.constants.Constants.PRECISION_6;
import static com.mapbox.mapboxsdk.style.layers.Property.LINE_CAP_ROUND;
import static com.mapbox.mapboxsdk.style.layers.Property.LINE_JOIN_ROUND;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOffset;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineCap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineJoin;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.lineWidth;
import static com.mapbox.turf.TurfConstants.UNIT_METERS;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Rather than showing the directions route all at once, have it "snake" from the origin to destination by showing the
 * route one {@link LegStep} section at a time.
 */
public class SnakingDirectionsRouteActivity extends AppCompatActivity
        implements OnMapReadyCallback {

  private static final String TAG = "SnakingDirectionsRouteActivity";
  private static final float NAVIGATION_LINE_WIDTH = 6;
  private static final float NAVIGATION_LINE_OPACITY = .8f;
  private static final String DRIVING_ROUTE_POLYLINE_LINE_LAYER_ID = "DRIVING_ROUTE_POLYLINE_LINE_LAYER_ID";
  private static final String DRIVING_ROUTE_POLYLINE_SOURCE_ID = "DRIVING_ROUTE_POLYLINE_SOURCE_ID";
  private static final String MARKER_SOURCE_ID = "MARKER_SOURCE_ID";
  private static final String MARKER_LAYER_ID = "MARKER_LAYER_ID";
  private static final String MARKER_ICON_ID = "MARKER_ICON_ID";
  private static final int DRAW_SPEED_MILLISECONDS_FOR_EACH_ROUTE_SECTION = 20;
  private static final String ROUTE_LINE_COLOR = "#d742f4";

  // Origin point in Paris, France
  private static final Point PARIS_ORIGIN_POINT = Point.fromLngLat(2.35222, 48.856614);

  // Destination point in Lyon, France
  private static final Point LYON_DESTINATION_POINT = Point.fromLngLat(4.83565, 45.76404);

  private MapView mapView;
  private MapboxMap mapboxMap;
  private MapboxDirections mapboxDirectionsClient;
  private Handler handler = new Handler();
  private Runnable runnable;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Mapbox access token is configured here. This needs to be called either in your application
    // object or in the same activity which contains the mapview.
    Mapbox.getInstance(this, getString(R.string.access_token));

    // This contains the MapView in XML and needs to be called after the access token is configured.
    setContentView(R.layout.activity_javaservices_snaking_directions_route);

    // Setup the MapView
    mapView = findViewById(R.id.mapView);
    mapView.onCreate(savedInstanceState);
    mapView.getMapAsync(this);
  }

  @Override
  public void onMapReady(@NonNull final MapboxMap mapboxMap) {
    this.mapboxMap = mapboxMap;

    this.mapboxMap.setStyle(new Style.Builder().fromUri(Style.LIGHT)
            // Add origin and destination SymbolLayer marker icons to the map
            .withImage(MARKER_ICON_ID, BitmapFactory.decodeResource(
                    getResources(), R.drawable.red_marker))
            .withSource(new GeoJsonSource(MARKER_SOURCE_ID,
                    FeatureCollection.fromFeatures(new Feature[]{
                            Feature.fromGeometry(Point.fromLngLat(PARIS_ORIGIN_POINT.longitude(), PARIS_ORIGIN_POINT.latitude())),
                            Feature.fromGeometry(Point.fromLngLat(LYON_DESTINATION_POINT.longitude(), LYON_DESTINATION_POINT.latitude())),
                    })))
            .withLayer(new SymbolLayer(MARKER_LAYER_ID, MARKER_SOURCE_ID).withProperties(
                    iconImage(MARKER_ICON_ID),
                    iconOffset(new Float[]{0f, -8f})
            ))

            // Add a source and LineLayer for the snaking directions route line
            .withSource(new GeoJsonSource(DRIVING_ROUTE_POLYLINE_SOURCE_ID))
            .withLayerBelow(new LineLayer(DRIVING_ROUTE_POLYLINE_LINE_LAYER_ID,
                    DRIVING_ROUTE_POLYLINE_SOURCE_ID)
                    .withProperties(
                            lineWidth(NAVIGATION_LINE_WIDTH),
                            lineOpacity(NAVIGATION_LINE_OPACITY),
                            lineCap(LINE_CAP_ROUND),
                            lineJoin(LINE_JOIN_ROUND),
                            lineColor(Color.parseColor(ROUTE_LINE_COLOR))
                    ), MARKER_LAYER_ID), new Style.OnStyleLoaded() {
      @Override
      public void onStyleLoaded(@NonNull Style style) {
        getDirectionsRoute(PARIS_ORIGIN_POINT, LYON_DESTINATION_POINT);
      }
    });
  }

  /**
   * Build the Mapbox Directions API request
   *
   * @param origin      The starting point for the directions route
   * @param destination The final point for the directions route
   */
  private void getDirectionsRoute(Point origin, Point destination) {
    mapboxDirectionsClient = MapboxDirections.builder()
      .origin(origin)
      .destination(destination)
      .overview(DirectionsCriteria.OVERVIEW_FULL)
      .profile(DirectionsCriteria.PROFILE_DRIVING)
      .geometries(GEOMETRY_POLYLINE6)
      .alternatives(true)
      .steps(true)
      .accessToken(getString(R.string.access_token))
      .build();

    mapboxDirectionsClient.enqueueCall(new Callback<DirectionsResponse>() {
      @Override
      public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
        // Create log messages in case no response or routes are present
        if (response.body() == null) {
          Timber.d("No routes found, make sure you set the right user and access token.");
          return;
        } else if (response.body().routes().size() < 1) {
          Timber.d("No routes found");
          return;
        }

        // Get the route from the Mapbox Directions API response
        DirectionsRoute currentRoute = response.body().routes().get(0);
        if (currentRoute.geometry() != null && currentRoute.legs() != null) {
          // Turn the route into a LineString
          LineString routeLineString = LineString.fromPolyline(currentRoute.geometry(), PRECISION_6);
          List<Point> animationPointList = new ArrayList<>();

          // Use Turf to create a list of Points that runnable will use to show an animated line
          for (int x = 1; x < 1000; x++) {
            animationPointList.add(TurfMeasurement.along(routeLineString, x * currentRoute.distance() / 1000, UNIT_METERS));
          }
          LineString animationLineString = LineString.fromLngLats(animationPointList);

          // Start the step-by-step process of drawing the route
          runnable = new DrawRouteRunnable(mapboxMap, animationLineString, handler);
          handler.postDelayed(runnable, DRAW_SPEED_MILLISECONDS_FOR_EACH_ROUTE_SECTION);
        }
      }

      @Override
      public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
        Toast.makeText(SnakingDirectionsRouteActivity.this,
                R.string.snaking_directions_activity_error, Toast.LENGTH_SHORT).show();
      }
    });
  }

  /**
   * Runnable class which goes through the route and draws each {@link LegStep} of the Directions API route
   */
  private static class DrawRouteRunnable implements Runnable {
    private MapboxMap mapboxMap;
    private LineString inputLineString;
    private List<Point> pointList;
    private Handler handler;
    private int counterIndex;

    DrawRouteRunnable(MapboxMap mapboxMap, LineString lineString, Handler handler) {
      this.mapboxMap = mapboxMap;
      this.inputLineString = lineString;
      this.handler = handler;
      this.counterIndex = 0;
      pointList = new ArrayList<>();
    }

    @Override
    public void run() {
      if (counterIndex < inputLineString.coordinates().size()) {
        pointList.add(inputLineString.coordinates().get(counterIndex));
        LineString lineStringToDraw = LineString.fromLngLats(pointList);
        if (mapboxMap.getStyle() != null) {
          GeoJsonSource source = mapboxMap.getStyle().getSourceAs(DRIVING_ROUTE_POLYLINE_SOURCE_ID);
          if (source != null) {
            source.setGeoJson(lineStringToDraw);
          }
        }
        counterIndex++;
        handler.postDelayed(this, DRAW_SPEED_MILLISECONDS_FOR_EACH_ROUTE_SECTION);
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    mapView.onResume();
  }

  @Override
  protected void onStart() {
    super.onStart();
    mapView.onStart();
  }

  @Override
  protected void onStop() {
    super.onStop();
    mapView.onStop();
    if (handler != null) {
      handler.removeCallbacks(runnable);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    mapView.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mapView.onSaveInstanceState(outState);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    // Cancel the directions API request
    if (mapboxDirectionsClient != null) {
      mapboxDirectionsClient.cancelCall();
    }
    mapView.onDestroy();
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mapView.onLowMemory();
  }
}
