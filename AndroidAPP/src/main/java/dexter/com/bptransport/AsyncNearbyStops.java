package dexter.com.bptransport;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
//import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Created by DEXTER on 2014.11.09..
 */
public class AsyncNearbyStops extends AsyncTask<Void, PebbleService.NearbyStops[], Void> implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {
    //private LocationClient location_client;
    private LocationRequest location_request;
    private boolean location_is_connected = false;
    private PebbleService service;
    private Context local_context;
    private Location location;
    private GoogleApiClient google_api_client;

    public ConditionVariable condition = new ConditionVariable(false);

    // Milliseconds per second
    private static final int MILLISECONDS_PER_SECOND = 1000;
    // Update frequency in seconds
    public static final int UPDATE_INTERVAL_IN_SECONDS = 5;
    // Update frequency in milliseconds
    private static final long UPDATE_INTERVAL =
            MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;
    // The fastest update frequency, in seconds
    private static final int FASTEST_INTERVAL_IN_SECONDS = 5;
    // A fast frequency ceiling in milliseconds
    private static final long FASTEST_INTERVAL =
            MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;

    public AsyncNearbyStops(PebbleService srv)
    {
        service = srv;
        local_context = service.getApplicationContext();
        location_request = LocationRequest.create();
        location_request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        location_request.setInterval(UPDATE_INTERVAL);
        location_request.setFastestInterval(FASTEST_INTERVAL);

        google_api_client = new GoogleApiClient.Builder(service.getApplicationContext())
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    protected void onPreExecute() {
        google_api_client.connect();


    }

    @Override
    protected Void doInBackground(Void... params) {
        PebbleService.NearbyStops[] nearby_stops;
        while (!this.isCancelled())
        {
            //Looper.loop();
            condition.block();
            if (!isCancelled() && this.location != null) {
                nearby_stops = get_nearby_stops(this.location);
                publishProgress(nearby_stops);
            }
            condition.close();
        }

        return null;
    }

    @Override
    protected void onProgressUpdate(PebbleService.NearbyStops[]... progress) {
        //setProgressPercent(progress[0]);
        service.store_nearby_stops(progress[0]);
    }

    @Override
    protected void onPostExecute(Void param) {
        google_api_client.disconnect();
    }

    @Override
    protected void onCancelled(Void param) {
        google_api_client.disconnect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        location_is_connected = true;
        this.location = LocationServices.FusedLocationApi.getLastLocation(google_api_client);
        LocationServices.FusedLocationApi.requestLocationUpdates(google_api_client, location_request, this);
        condition.open();
    }

    @Override
    public void onConnectionSuspended(int i) {
       condition.open();
    }

   /* @Override
    public void onDisconnected() {
        location_is_connected = false;

    }*/

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;
        condition.open();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        condition.open();
    }

    private PebbleService.NearbyStops[] get_nearby_stops(Location location)
    {
        Log.d(local_context.getPackageName(), "start get_nearby_stops;");
        PebbleService.NearbyStops[] nearby_stops = new PebbleService.NearbyStops[PebbleService.NEARBY_STOPS_LENGTH];
        for (int i = 0; i < PebbleService.NEARBY_STOPS_LENGTH;i++) {
            nearby_stops[i] = new PebbleService.NearbyStops();
            nearby_stops[i].distance = Integer.MAX_VALUE;
            nearby_stops[i].distance_string = "999m";
            nearby_stops[i].direction_name = "->";
            nearby_stops[i].line_numbers = "";
            nearby_stops[i].stop_name = "";
        }

        Log.d(local_context.getPackageName(), "before insert;");
        Iterator it = PebbleService.stops_cache.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry pairs = (Map.Entry)it.next();
            PebbleService.Stops stop = (PebbleService.Stops)pairs.getValue();
            long id = ((Long)pairs.getKey()).longValue();
            int distance = (int)location.distanceTo(stop.location);
            insert_into_nearby_stops(nearby_stops, distance, id, stop.name);
            //it.remove();
        }

        Log.d(local_context.getPackageName(), "after insert;");
        if (isCancelled())
            return nearby_stops;

        GtfsDbHelper gtfs_dbhelper = new GtfsDbHelper(local_context);
        SQLiteDatabase db = gtfs_dbhelper.getReadableDatabase();

        get_trip_ids(nearby_stops, db);
        Log.d(local_context.getPackageName(), "after get tripids;");
        db.close();
        return nearby_stops;
    }

    private void insert_into_nearby_stops(PebbleService.NearbyStops[] nearby_stops, int distance, long id, String stop_name)
    {
        int place = 0;
        for(;place < PebbleService.NEARBY_STOPS_LENGTH && nearby_stops[place].distance < distance; place++)
            ;
        if (place == PebbleService.NEARBY_STOPS_LENGTH)
            return;

        for (int i = PebbleService.NEARBY_STOPS_LENGTH - 1; i > place; i--) {
            nearby_stops[i].distance = nearby_stops[i - 1].distance;
            nearby_stops[i].id = nearby_stops[i-1].id;
            nearby_stops[i].stop_name = nearby_stops[i-1].stop_name;
        }
        nearby_stops[place].distance = distance;
        nearby_stops[place].id = id;
        if (stop_name.length() > 31)
            stop_name = stop_name.substring(0,31);
        nearby_stops[place].stop_name = stop_name;
    }

    private void get_trip_ids(PebbleService.NearbyStops[] nearby_stops, SQLiteDatabase db) {
       for (int i = 0; i < PebbleService.NEARBY_STOPS_LENGTH && !isCancelled(); i++) {
            Set<Integer> stop_ids = PebbleService.stop_id_cache.get((int)nearby_stops[i].id);
            if (stop_ids == null)
                continue;
            String joined = TextUtils.join(",", stop_ids.toArray());
            //String my_query = "SELECT strings.string,trips._id,trips.direction_id, stop_sequences.sequence FROM stop_times INNER JOIN stop_sequences ON stop_sequences._id=stop_times.stop_seq_id INNER JOIN trips ON trips._id=stop_times._id INNER JOIN routes ON routes._id=trips.route_id INNER JOIN strings ON strings._id=routes.short_name WHERE stop_sequences.sequence LIKE ? GROUP BY trips.route_id;";
            String my_query = "SELECT strings.string,trips._id,trips.direction_id, stop_sequences.sequence FROM stop_times INNER JOIN stop_sequences ON stop_sequences._id=stop_times.stop_seq_id INNER JOIN trips ON trips._id=stop_times._id INNER JOIN routes ON routes._id=trips.route_id INNER JOIN strings ON strings._id=routes.short_name WHERE stop_times.stop_seq_id IN (";
            my_query += joined;
            my_query += ") GROUP BY trips.route_id;";
            //Log.d(local_context.getPackageName(), my_query);
            //Cursor cursor = db.rawQuery(my_query, new String[]{"%,"+String.valueOf(nearby_stops[i].id)+",%"});
            Cursor cursor = db.rawQuery(my_query, null);
            int trip_id = 0;
            String line_numbers = "";
            String stop_sequence = "";
            int direction = 0;
            boolean direction_set = false;
            while (cursor.moveToNext() && !isCancelled()) {
                if (trip_id == 0)
                    trip_id = cursor.getInt(1);
                if (!direction_set) {
                    direction = cursor.getInt(2);
                    stop_sequence = cursor.getString(3);
                    direction_set = true;
                }
                line_numbers += cursor.getString(0) + ", ";
            }
            cursor.close();
            if (isCancelled())
                return;
            if (trip_id == 0)
                continue;
            String direction_name = "->" + get_next_stop(stop_sequence, nearby_stops[i].id, db);
            if (direction_name.length() > 31)
               direction_name = direction_name.substring(0,31);
            nearby_stops[i].direction_name = direction_name;
            line_numbers = line_numbers.substring(0, line_numbers.length() - 2);
            if (line_numbers.length() > 31)
                line_numbers = line_numbers.substring(0,31);
            nearby_stops[i].line_numbers = line_numbers;
            nearby_stops[i].direction = direction;
            Log.d(local_context.getPackageName(), "stop_id done: " + i);
        }
    }

    private String get_next_stop(String stop_sequence, long stop_id, SQLiteDatabase db) {
        String sequence_string = stop_sequence.substring(1, stop_sequence.length()-1);
        String[] sequence = sequence_string.split(",");
        int i = 0;
        for(; i < sequence.length && !sequence[i].contentEquals(String.valueOf(stop_id)); i++)
            ;
        if (i < sequence.length - 1)
            i++;
        if (i == sequence.length)
            i--;

        String next_stop = "";
        PebbleService.Stops stops = PebbleService.stops_cache.get(Long.parseLong(sequence[i]));
        if (stops != null)
            next_stop = stops.name;

        return next_stop;
    }
}
