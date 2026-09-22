package com.pinghome.app.data.geo;

import android.content.Context;

import androidx.annotation.Nullable;

import com.pinghome.app.BuildConfig;
import com.pinghome.app.util.ApiCallback;
import com.pinghome.app.util.ApiResult;
import com.pinghome.app.util.AppExecutors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Place search and travel-time estimates from the OpenStreetMap ecosystem - no API key, no
 * billing account. Nominatim geocodes typed destinations (and reverse-geocodes map taps);
 * OSRM (the FOSSGIS public routers) returns a walking or driving duration. Both public services
 * ask for an identifying User-Agent and light use: searches run on submit, never per keystroke.
 * Neither call touches PingHome's backend; only the resulting coordinates do, inside the trip.
 */
public class OsmGeoService {

    private static final String NOMINATIM = "https://nominatim.openstreetmap.org";
    private static final String OSRM_DRIVING = "https://routing.openstreetmap.de/routed-car/route/v1/driving/";
    private static final String OSRM_WALKING = "https://routing.openstreetmap.de/routed-foot/route/v1/driving/";
    private static final String USER_AGENT = "PingHome/" + BuildConfig.VERSION_NAME + " (student safety project)";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    private final AppExecutors executors = AppExecutors.getInstance();
    private final String acceptLanguage;

    public OsmGeoService(Context context) {
        this.acceptLanguage = context.getResources().getConfiguration().getLocales().get(0).toLanguageTag();
    }

    /** Free-text search, optionally biased toward the user's current position. */
    public void search(String query, @Nullable Double nearLat, @Nullable Double nearLng, ApiCallback<List<Place>> callback) {
        executors.runBackground(() -> {
            ApiResult<List<Place>> result;
            try {
                StringBuilder url = new StringBuilder(NOMINATIM + "/search?format=jsonv2&limit=6&q=")
                        .append(URLEncoder.encode(query, StandardCharsets.UTF_8.name()));
                if (nearLat != null && nearLng != null) {
                    // Soft bias (not a hard filter): prefer results within ~50 km of the user.
                    url.append(String.format(Locale.US, "&viewbox=%.4f,%.4f,%.4f,%.4f",
                            nearLng - 0.5, nearLat + 0.5, nearLng + 0.5, nearLat - 0.5));
                }
                JSONArray array = new JSONArray(get(url.toString()));
                List<Place> places = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject o = array.getJSONObject(i);
                    String display = o.optString("display_name", "");
                    String name = o.optString("name", "");
                    places.add(new Place(display, name.isEmpty() ? firstPart(display) : name,
                            o.getDouble("lat"), o.getDouble("lon")));
                }
                result = places.isEmpty()
                        ? ApiResult.failure("No places found for \"" + query + "\". Try adding the area or city.", 404)
                        : ApiResult.success(places);
            } catch (Exception e) {
                result = ApiResult.failure("Couldn't search places right now - check your connection.", 0);
            }
            ApiResult<List<Place>> finalResult = result;
            executors.runOnMain(() -> callback.onResult(finalResult));
        });
    }

    /** Human-readable name for a tapped point, so the destination field can be pre-filled. */
    public void reverse(double lat, double lng, ApiCallback<Place> callback) {
        executors.runBackground(() -> {
            ApiResult<Place> result;
            try {
                String url = String.format(Locale.US, "%s/reverse?format=jsonv2&zoom=17&lat=%.6f&lon=%.6f", NOMINATIM, lat, lng);
                JSONObject o = new JSONObject(get(url));
                String display = o.optString("display_name", "");
                String name = o.optString("name", "");
                result = display.isEmpty()
                        ? ApiResult.failure("No address for this point", 404)
                        : ApiResult.success(new Place(display, name.isEmpty() ? firstPart(display) : name, lat, lng));
            } catch (Exception e) {
                result = ApiResult.failure("Reverse geocoding unavailable", 0);
            }
            ApiResult<Place> finalResult = result;
            executors.runOnMain(() -> callback.onResult(finalResult));
        });
    }

    public void estimateRoute(double fromLat, double fromLng, double toLat, double toLng,
                              RouteEstimate.Mode mode, ApiCallback<RouteEstimate> callback) {
        executors.runBackground(() -> {
            ApiResult<RouteEstimate> result;
            try {
                String base = mode == RouteEstimate.Mode.WALKING ? OSRM_WALKING : OSRM_DRIVING;
                String url = String.format(Locale.US, "%s%.6f,%.6f;%.6f,%.6f?overview=false", base, fromLng, fromLat, toLng, toLat);
                JSONObject o = new JSONObject(get(url));
                if (!"Ok".equals(o.optString("code"))) throw new IOException("OSRM " + o.optString("code"));
                JSONObject route = o.getJSONArray("routes").getJSONObject(0);
                result = ApiResult.success(new RouteEstimate(mode,
                        (int) Math.round(route.getDouble("duration")),
                        (int) Math.round(route.getDouble("distance"))));
            } catch (Exception e) {
                result = ApiResult.failure("Couldn't estimate travel time - set the arrival time manually.", 0);
            }
            ApiResult<RouteEstimate> finalResult = result;
            executors.runOnMain(() -> callback.onResult(finalResult));
        });
    }

    private String get(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", acceptLanguage)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("HTTP " + response.code());
            return response.body().string();
        }
    }

    private static String firstPart(String displayName) {
        int comma = displayName.indexOf(',');
        return comma > 0 ? displayName.substring(0, comma).trim() : displayName;
    }
}
