package dexter.com.bptransport;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Set;
import java.util.TimeZone;

/**
 * Created by DEXTER on 2014.11.09..
 */
public class AsyncNearbyStopsDetails extends AsyncTask<Object, Void, PebbleService.NearbyStopsDetails[]> {
    private PebbleService service;
    private Context local_context;
    private JSONObject json;

    public AsyncNearbyStopsDetails(PebbleService srv) {
        service = srv;
        local_context = service.getApplicationContext();
    }

    @Override
    protected PebbleService.NearbyStopsDetails[] doInBackground(Object... params) {

        final long id = (Long)params[0];
        Thread thread = new Thread(new Runnable(){
            @Override
            public void run() { get_json(id);}});
        thread.start();
        PebbleService.NearbyStopsDetails[] nearby_stops_details = get_nearby_stops_details(id, (Integer)params[1]);
        try {
            thread.join();
        } catch (Exception e) {

        }

        combine_online_offline(nearby_stops_details);
        return nearby_stops_details;
    }

    @Override
    protected void onPostExecute(PebbleService.NearbyStopsDetails[] nearby_stops_details) {
        service.store_nearby_stops_details(nearby_stops_details);
    }

    @Override
    protected void onCancelled(PebbleService.NearbyStopsDetails[] nearby_stops_details) {

    }

    private void get_json(long id)
    {
        PebbleService.Stops stops = PebbleService.stops_cache.get(id);
        String string_id = "BKK_" + stops.string_id;
        try {

            URL url = new URL("http://futar.bkk.hu/bkk-utvonaltervezo-api/ws/otp/api/where/arrivals-and-departures-for-stop.json?stopId=" + string_id);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(5000 /* milliseconds */);
            conn.setConnectTimeout(5000 /* milliseconds */);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            // Starts the query
            conn.connect();
            int response = conn.getResponseCode();
            //Log.d(context.getPackageName(), "The response is: " + response);
            int total = conn.getContentLength() * 2;
            InputStream inputstream = conn.getInputStream();
            String data = convertStreamToString(inputstream);

            json = new JSONObject(data);

        } catch (Exception e) {

        }
    }

    private void combine_online_offline(PebbleService.NearbyStopsDetails[] nearby_stops_details)
    {

      try {
          //String d = json.getString("status");
            if (!json.getString("status").equals("OK"))
                return;

            Calendar current = Calendar.getInstance();

            JSONObject data = json.getJSONObject("data");
            JSONObject entry = data.getJSONObject("entry");
            JSONArray stop_times = entry.getJSONArray("stopTimes");
            for (int i = 0; i < stop_times.length(); i++) {  //TODO: SORT BY departuretime
                JSONObject row = stop_times.getJSONObject(i);
                int departure_time = row.getInt("departureTime");

                current.set(Calendar.HOUR_OF_DAY, (nearby_stops_details[i].start_time / 60) / 60);
                current.set(Calendar.MINUTE, (nearby_stops_details[i].start_time / 60) % 60);
                current.set(Calendar.SECOND, 0);
                //Long c = current.getTimeInMillis() / 1000;
                if (departure_time == current.getTimeInMillis() / 1000)
                {
                    nearby_stops_details[i].predicted_start_time = get_start_time(row, "predictedDepartureTime");
                }
                else
                {
                    shift_nearby_stops_details(i, nearby_stops_details);

                    String trip_id = row.getString("tripId");
                    JSONObject references = data.getJSONObject("references");
                    JSONObject trips = references.getJSONObject("trips");
                    JSONObject trip = trips.getJSONObject(trip_id);
                    String route_id = trip.getString("routeId");

                    nearby_stops_details[i].predicted_start_time = get_start_time(row, "predictedDepartureTime");
                    nearby_stops_details[i].start_time = get_start_time(row, "departureTime");
                    JSONObject routes = references.getJSONObject("routes");
                    JSONObject route = routes.getJSONObject(route_id);

                    nearby_stops_details[i].direction_name = trim_headsign("*" + trip.getString("tripHeadsign"));
                    nearby_stops_details[i].line_number = trim_line_number(route.getString("shortName"));

                }
            }

        } catch (Exception e) {

        }
    }

    private String trim_line_number(String line_number)
    {
        if (line_number.length() > 4)
            return new String(line_number.substring(0,4));

        return line_number;
    }

    private String trim_headsign(String headsign)
    {

        if (headsign.length() > (31 - 2))
            return new String(headsign.substring(0,31 - 2));

        return headsign;
    }

    private int get_start_time(JSONObject row, String time) throws JSONException {
        try {
            int predicted_dep_time = row.getInt(time);
            Calendar calendar = Calendar.getInstance();
            Long c = (long)predicted_dep_time * 1000;
            calendar.setTimeInMillis(c);
            return (calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)) * 60 + calendar.get(Calendar.SECOND);
        } catch (JSONException e){
           throw e;
        }
    };

    private void shift_nearby_stops_details(int i, PebbleService.NearbyStopsDetails[] nearby_stops_details)
    {
        if ( i > nearby_stops_details.length - 2)
            return;
       for (int j = nearby_stops_details.length - 2; j >= i; j--) {
           nearby_stops_details[j + 1].line_number = nearby_stops_details[j].line_number;
           nearby_stops_details[j + 1].direction_name = nearby_stops_details[j].direction_name;
           nearby_stops_details[j + 1].start_time = nearby_stops_details[j].start_time;
           nearby_stops_details[j + 1].stop_id = nearby_stops_details[j].stop_id;
           nearby_stops_details[j + 1].trip_id = nearby_stops_details[j].trip_id;
           nearby_stops_details[j+1].predicted_start_time = nearby_stops_details[j].predicted_start_time;
       }
    }

    static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private PebbleService.NearbyStopsDetails[] get_nearby_stops_details(long id, int direction) {
        PebbleService.NearbyStopsDetails[] nearby_stops_details = new PebbleService.NearbyStopsDetails[PebbleService.NEARBY_STOPS_DETAILS_LENGTH];
        for (int i = 0; i < PebbleService.NEARBY_STOPS_DETAILS_LENGTH;i++) {
            nearby_stops_details[i] = new PebbleService.NearbyStopsDetails();
            nearby_stops_details[i].line_number = "";
            nearby_stops_details[i].direction_name = "";
            nearby_stops_details[i].start_time = 99999;
            nearby_stops_details[i].predicted_start_time = 0;
        }
        GtfsDbHelper gtfs_dbhelper = new GtfsDbHelper(local_context);
        SQLiteDatabase db = gtfs_dbhelper.getReadableDatabase();

        Set<Integer> stop_ids = PebbleService.stop_id_cache.get((int)id);
        if (stop_ids == null)
            return nearby_stops_details;

        String joined = TextUtils.join(",", stop_ids.toArray());

        String my_query = "SELECT stop_times.start_time, time_sequences.sequence, stop_sequences.sequence, strings.string, stop_times._id, calendar.bitfield, calendar.start_date, calendar.delta, calendar_dates.date, calendar_dates.exception_type, headsignstrings.string FROM stop_times INNER JOIN time_sequences ON time_sequences._id=stop_times.time_seq_id INNER JOIN stop_sequences ON stop_sequences._id=stop_times.stop_seq_id INNER JOIN trips ON trips._id=stop_times._id INNER JOIN routes ON routes._id=trips.route_id INNER JOIN strings ON strings._id=routes.short_name INNER JOIN calendar ON trips.service_id=calendar._id INNER JOIN calendar_dates ON trips.service_id=calendar_dates._id INNER JOIN strings headsignstrings ON headsignstrings._id = trips.headsign WHERE stop_times.stop_seq_id IN (";
        my_query += joined;
        //my_query += ") AND trips.direction_id LIKE ? GROUP BY trips._id";
        //Cursor cursor = db.rawQuery(my_query, new String[]{String.valueOf(direction)});
        my_query += ") GROUP BY trips._id";
        Cursor cursor = db.rawQuery(my_query, null);
        Calendar calendar = Calendar.getInstance();
        int current_time = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
        //int current_time = 1241;
        long unixTime = System.currentTimeMillis() / 1000L;
        int day_of_week = calendar.get(Calendar.DAY_OF_WEEK);
        while (cursor.moveToNext())
        {
            String headsign = cursor.getString(10);
            int start_time = cursor.getInt(0);
            String time_sequence_string = cursor.getString(1);
            String stop_sequence_string = cursor.getString(2);
            time_sequence_string = time_sequence_string.substring(1, time_sequence_string.length()-1);
            stop_sequence_string = stop_sequence_string.substring(1, stop_sequence_string.length()-1);
            String[] time_sequence = time_sequence_string.split(",");
            String[] stop_sequence = stop_sequence_string.split(",");
            String line_num = cursor.getString(3);
            int stop_times_id = cursor.getInt(4);
            int bitfield = cursor.getInt(5);
            int exception_date = cursor.getInt(8);
            Date exception_d = new Date((long)exception_date*1000);
            int exception_type = cursor.getInt(9);
            Date current_date = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            if (!sdf.format(current_date).equals(sdf.format(exception_d)) || exception_type != 1) {
            //if (!current_date.equals(exception_d) || exception_type != 1) {
                if ((day_of_week == 1 && ((bitfield & 1L) == 0)) || (day_of_week != 1 && ((bitfield & (1L << 8 - day_of_week)) == 0)))
                    continue;

                int start_date = cursor.getInt(6);
                int end_date = start_date + cursor.getInt(7);
                if (start_date > unixTime || end_date < unixTime)
                    continue;

                if (current_date.equals(exception_d) && exception_type == 2)
                    continue;
            }
            int i = 0;
            for(; i < stop_sequence.length && !stop_sequence[i].contentEquals(String.valueOf(id)); i++)
            {
                if (i < time_sequence.length)
                    start_time += Integer.parseInt(time_sequence[i]);
            }
            if (start_time < current_time)
                continue;

            insert_into_nearby_stops_details(nearby_stops_details, start_time, line_num, Integer.parseInt(stop_sequence[stop_sequence.length - 1]), stop_times_id, headsign);
        }
        cursor.close();

        db.close();
        return nearby_stops_details;
    }

    private void insert_into_nearby_stops_details(PebbleService.NearbyStopsDetails[] nearby_stops_details, int start_time, String line_num, int stop_id, int stop_times_id, String headsign) {
        int place = 0;

        start_time *= 60; //Convert it to seconds

        for(;place < PebbleService.NEARBY_STOPS_DETAILS_LENGTH && nearby_stops_details[place].start_time < start_time; place++)
            ;
        if (place == PebbleService.NEARBY_STOPS_DETAILS_LENGTH)
            return;

        for (int i = PebbleService.NEARBY_STOPS_DETAILS_LENGTH - 1; i > place; i--) {
            nearby_stops_details[i].start_time = nearby_stops_details[i - 1].start_time;
            nearby_stops_details[i].line_number = nearby_stops_details[i-1].line_number;
            nearby_stops_details[i].stop_id = nearby_stops_details[i-1].stop_id;
            nearby_stops_details[i].trip_id = nearby_stops_details[i-1].trip_id;
            nearby_stops_details[i].direction_name = nearby_stops_details[i-1].direction_name;
            nearby_stops_details[i].predicted_start_time = nearby_stops_details[i-1].predicted_start_time;
        }
        nearby_stops_details[place].start_time = start_time;
        nearby_stops_details[place].line_number = trim_line_number(line_num);
        nearby_stops_details[place].stop_id = stop_id;
        nearby_stops_details[place].trip_id = stop_times_id;
        nearby_stops_details[place].direction_name = "->" + trim_headsign(headsign);

    }
}
