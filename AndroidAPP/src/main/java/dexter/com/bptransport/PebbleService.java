package dexter.com.bptransport;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Created by DEXTER on 2014.10.24..
 */
public class PebbleService extends Service {
    public static final int NEARBY_STOPS_LENGTH = 10;
    public static final int NEARBY_STOPS_DETAILS_LENGTH = 10;

    private final static UUID PEBBLE_APP_UUID = UUID.fromString("3acd6df6-9075-4c9e-b4f2-4c9accfbb8bf");
    private static final int MESSAGE_TYPE_GET_LANGUAGE                   = 0;
    private static final int MESSAGE_TYPE_GET_LANGUAGE_REPLY             = 1;
    private static final int MESSAGE_TYPE_GET_NEARBY_STOPS               = 2;
    private static final int MESSAGE_TYPE_GET_NEARBY_STOPS_REPLY         = 3;
    private static final int MESSAGE_TYPE_GET_NEARBY_STOPS_DETAILS       = 4;
    private static final int MESSAGE_TYPE_GET_NEARBY_STOPS_DETAILS_REPLY = 5;
    private static final int MESSAGE_TYPE_GET_FAVORITES                  = 6;

    private static final int LANGUAGE_TYPE_HUNGARIAN                     = 0;
    private static final int LANGUAGE_TYPE_ENGLISH                       = 1;

    private int nearby_stops_transaction_id = NEARBY_STOPS_LENGTH;
    private int nearby_stops_details_transaction_id = NEARBY_STOPS_DETAILS_LENGTH + 100;

    public static final Map<Integer, Set<Integer>> stop_id_cache = new HashMap<Integer, Set<Integer>>();
    public static final Map<Long, Stops> stops_cache = new HashMap<Long, Stops>();

    private final IBinder binder = new LocalBinder();
    private MainActivity main_activity = null;
    private NearbyStops[] nearby_stops_on_pebble;
    private NearbyStopsDetails[] nearby_stops_details_on_pebble;

    private AsyncNearbyStops async_nearby_stops = null;
    //private AsyncNearbyStopsDetails async_nearby_stops_details = null;
    private BroadcastReceiver pebble_data_receiver, pebble_ack_receiver, pebble_nack_receiver;
    private Timer timer = null;
    private static final int TIMERTASK_DELAY = 60000; //1 mins
    private final Handler handler = new Handler();

    public static class NearbyStops
    {
        int distance;
        long id;
        String line_numbers;
        String direction_name;
        String stop_name;
        String distance_string;
        int direction;
    }

    public static class NearbyStopsDetails
    {
        String line_number;
        String direction_name;
        int start_time;
        int predicted_start_time;
        int stop_id;
        int trip_id;
    }

    public static class Stops
    {
        Location location;
        String name;
        String string_id;
    }

    public PebbleService()
    {

    }

    public class LocalBinder extends Binder {
        PebbleService getService() {
            // Return this instance of LocalService so clients can call public methods
            return PebbleService.this;
        }
    }

    public void onCreate()
    {
        async_nearby_stops =  new AsyncNearbyStops(this);

        PebbleKit.registerPebbleConnectedReceiver(getApplicationContext(), new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(getPackageName(), "Pebble connected!");
            }
        });

        PebbleKit.registerPebbleDisconnectedReceiver(getApplicationContext(), new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(getPackageName(), "Pebble disconnected!");
                stop_updates();
            }
        });

        GtfsDbHelper gtfs_dbhelper = new GtfsDbHelper(getApplicationContext());
        SQLiteDatabase db = gtfs_dbhelper.getReadableDatabase();

        create_stop_id_cache(db);
        create_stops_cache(db);

        db.close();

        //async_nearby_stops_details = new AsyncNearbyStopsDetails(this);

        Log.d(getPackageName(), "Created PebbleService;");
        //get_stops();
    }

    private void create_stop_id_cache(SQLiteDatabase db) {

        String my_query = "SELECT _id, sequence FROM stop_sequences";
        Cursor cursor = db.rawQuery(my_query, null);
        stop_id_cache.clear();
        while (cursor.moveToNext())
        {
            String stop_sequence_string = cursor.getString(1);
            stop_sequence_string = stop_sequence_string.substring(1, stop_sequence_string.length()-1);
            String[] stop_sequence = stop_sequence_string.split(",");
            for (String stop : stop_sequence)
            {
                int stop_id = Integer.parseInt(stop);
                Set<Integer> value = stop_id_cache.get(stop_id);
                if (value == null)
                {
                    value = new HashSet<Integer>();
                    value.add(cursor.getInt(0));
                    stop_id_cache.put(stop_id, value);
                }
                else {
                    value.add(cursor.getInt(0));
                }
            }
        }
        cursor.close();
    }

    public static void create_stops_cache(SQLiteDatabase db)
    {
        String my_query = "SELECT stops._id, stops.lat, stops.lon, strings.string, stops.location_type, stops.string_id, strings2.string FROM stops INNER JOIN strings ON strings._id = stops.name INNER JOIN strings AS strings2 ON strings2._id = stops.string_id";
        Cursor cursor = db.rawQuery(my_query, null);
        while (cursor.moveToNext())
        {
            int location_type = cursor.getInt(4);
            if (location_type == 1)
                continue;
            long lat = cursor.getLong(1);
            long lon = cursor.getLong(2);
            long id = cursor.getLong(0);
            String stop_name = cursor.getString(3);
            String string_id = cursor.getString(6);
            PebbleService.Stops stops = new PebbleService.Stops();
            //stops.id = id;
            stops.location = new Location("");
            stops.location.setLatitude((float)lat/1000000);
            stops.location.setLongitude((float)lon/1000000);
            stops.name = stop_name;
            stops.string_id = string_id;
            PebbleService.stops_cache.put(id, stops);
        }
        cursor.close();
    }

    public int onStartCommand(Intent intent, int flags, int start_id)
    {
        pebble_data_receiver = PebbleKit.registerReceivedDataHandler(this, new PebbleKit.PebbleDataReceiver(PEBBLE_APP_UUID) {
            @Override
            public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {
                Log.i(getPackageName(), "Received value = " + data.getUnsignedIntegerAsLong(0) + " for key: 0");
                receiveMessage(data, transactionId);
            }
        });
        pebble_ack_receiver = PebbleKit.registerReceivedAckHandler(getApplicationContext(), new PebbleKit.PebbleAckReceiver(PEBBLE_APP_UUID) {

            @Override
            public void receiveAck(Context context, int transactionId) {
                //Log.i(getLocalClassName(), "Received ack for transaction " + transactionId);
                if (transactionId < 100 - 1) {
                    nearby_stops_transaction_id++;
                    if (nearby_stops_transaction_id >= NEARBY_STOPS_LENGTH)
                        return;
                    send_next_nearby_stop_to_pebble();
                }
                else
                {
                    nearby_stops_details_transaction_id++;
                    if (nearby_stops_details_transaction_id >= NEARBY_STOPS_DETAILS_LENGTH + 100)
                        return;
                    send_next_nearby_stops_detail_to_pebble();
                }
            }

        });

        pebble_nack_receiver = PebbleKit.registerReceivedNackHandler(getApplicationContext(), new PebbleKit.PebbleNackReceiver(PEBBLE_APP_UUID) {

            @Override
            public void receiveNack(Context context, int transactionId) {
                //Log.i(getLocalClassName(), "Received nack for transaction " + transactionId);
                if (transactionId < 100 - 1) {
                    if (nearby_stops_transaction_id >= NEARBY_STOPS_LENGTH ||
                        nearby_stops_transaction_id < 0)
                        return;
                    send_next_nearby_stop_to_pebble();
                }
                else
                {
                    if (nearby_stops_details_transaction_id >= NEARBY_STOPS_DETAILS_LENGTH + 100 ||
                        nearby_stops_details_transaction_id < 100)
                        return;
                    send_next_nearby_stops_detail_to_pebble();
                }
            }

        });
        return START_STICKY;
    }

    private void send_get_language_reply()
    {
        if (PebbleKit.areAppMessagesSupported(getApplicationContext())) {
            PebbleDictionary language = new PebbleDictionary();
            language.addUint8(0, (byte) MESSAGE_TYPE_GET_LANGUAGE_REPLY);
            language.addUint8(1, (byte) LANGUAGE_TYPE_ENGLISH);

            Log.i(getPackageName(), "Dictionary: " + language.toJsonString());
            PebbleKit.sendDataToPebble(getApplicationContext(), PEBBLE_APP_UUID, language);
            Log.i(getPackageName(), "Data sent.");
        }
    }

    private void sendMessage (int type) {
        switch (type) {
            case MESSAGE_TYPE_GET_LANGUAGE_REPLY:
                send_get_language_reply();
                break;
        }
    }

    private class MyRunnable implements Runnable
    {
        private PebbleService service;
        long id;
        int direction;

        public MyRunnable(PebbleService service, long id, int direction)
        {
            this.service = service;
            this.id = id;
            this.direction = direction;
        }

        @Override
        public void run() {
            new AsyncNearbyStopsDetails(service).execute(id,direction);
        }
    }
    public void receiveMessage (PebbleDictionary dictionary, int transactionId) {
        //this.transaction_id = transactionId; // make sure transactionId is set before calling (onStart)
        PebbleKit.sendAckToPebble(getApplicationContext(), transactionId);
        stop_updates();
        nearby_stops_transaction_id = NEARBY_STOPS_LENGTH;
        nearby_stops_details_transaction_id = NEARBY_STOPS_DETAILS_LENGTH + 100;
        int type = dictionary.getUnsignedIntegerAsLong(0).intValue();
        switch (type) {
            case MESSAGE_TYPE_GET_LANGUAGE: // Discover bulbs.
                send_get_language_reply();

                break;
            case MESSAGE_TYPE_GET_NEARBY_STOPS:
                //get_nearby_stops();
                //start_updates();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        start_updates();
                    }
                });
                break;
            case MESSAGE_TYPE_GET_NEARBY_STOPS_DETAILS:
                //stop_updates();
                int id = dictionary.getUnsignedIntegerAsLong(1).intValue();
                if (id < 0 || id > 9)
                    return;
                //
                handler.post(new MyRunnable(this, nearby_stops_on_pebble[id].id, nearby_stops_on_pebble[id].direction));

                //async_nearby_stops_details.execute(id, nearby_stops_on_pebble[id].direction);
                break;
            default:
                Log.e(getPackageName(), "Received unexpected value/message from Pebble: " + type);
                break;
        }
    }
    @Override
    public IBinder onBind(Intent intent)
    {
        return binder;
    }

    public void notify_progressbar(int progress)
    {
        if (main_activity != null)
            main_activity.notify_progressbar(progress);
    }

    public void update()
    {
        new UpdateDatabase(this).execute();
    }

    public void subscribe(MainActivity activity)
    {
        main_activity = activity;
    }

    public void unsubscribe()
    {
        main_activity = null;
    }


    public void start_updates()
    {
        //check for google play services
        //google_api_client.connect();
        async_nearby_stops =  new AsyncNearbyStops(this);
        async_nearby_stops.execute();

        class stop_nearby_updates_timertask extends TimerTask {

            @Override
            public void run() {
                stop_updates();
            }
        };

        timer = new Timer();
        timer.schedule(new stop_nearby_updates_timertask(), TIMERTASK_DELAY);
    }

    public void stop_updates()
    {
        if (async_nearby_stops != null) {
            async_nearby_stops.cancel(true);
            async_nearby_stops.condition.open();
            async_nearby_stops = null;
        }

        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    public void get_details()
    {
        Runnable r = new MyRunnable(this, nearby_stops_on_pebble[0].id, nearby_stops_on_pebble[0].direction);
        handler.postDelayed(r, 1000);
    }

    public void store_nearby_stops(NearbyStops[] nearby_stops)
    {
        String string = "";
        for (int i = 0; i < PebbleService.NEARBY_STOPS_LENGTH; i++) {
            if (nearby_stops[i].distance <= 999)
                nearby_stops[i].distance_string = String.valueOf(nearby_stops[i].distance) + "m";
            Log.d(getPackageName(), "Stop name: " + nearby_stops[i].stop_name + ", direction_name: " + nearby_stops[i].direction_name + ",line_numbers: " + nearby_stops[i].line_numbers + ", distance: " + nearby_stops[i].distance_string + ", id: " + nearby_stops[i].id);
            string += "Stop name: " + nearby_stops[i].stop_name + ", direction_name: " + nearby_stops[i].direction_name + ",line_numbers: "+nearby_stops[i].line_numbers + ", distance: " + nearby_stops[i].distance_string + ", id: " + nearby_stops[i].id + "\n";
        }

        nearby_stops_on_pebble = nearby_stops;
        if (nearby_stops_transaction_id < NEARBY_STOPS_LENGTH)
            nearby_stops_transaction_id = -1;
        else {
            nearby_stops_transaction_id = 0;
            send_next_nearby_stop_to_pebble();
        }
        if (main_activity != null)
            main_activity.fill_textview(string);
        //send to pebble
    }

    private void send_next_nearby_stop_to_pebble() {
        if (!PebbleKit.areAppMessagesSupported(getApplicationContext()))
            return;

            PebbleDictionary nearby_stop = new PebbleDictionary();
            nearby_stop.addUint8(0, (byte) MESSAGE_TYPE_GET_NEARBY_STOPS_REPLY);
            nearby_stop.addUint8(1, (byte) nearby_stops_transaction_id);
            nearby_stop.addString(2, nearby_stops_on_pebble[nearby_stops_transaction_id].stop_name);
            nearby_stop.addString(3, nearby_stops_on_pebble[nearby_stops_transaction_id].direction_name);
            nearby_stop.addString(4, nearby_stops_on_pebble[nearby_stops_transaction_id].line_numbers);
            nearby_stop.addString(5, nearby_stops_on_pebble[nearby_stops_transaction_id].distance_string);
            //Log.i(getPackageName(), "Dictionary: " + nearby_stop.toJsonString());

            PebbleKit.sendDataToPebbleWithTransactionId(getApplicationContext(), PEBBLE_APP_UUID, nearby_stop, nearby_stops_transaction_id);

     }

    public void store_nearby_stops_details(NearbyStopsDetails[] nearby_stops_details)
    {
        String string ="";
        for (int i = 0; i < PebbleService.NEARBY_STOPS_DETAILS_LENGTH; i++) {

            Log.d(getPackageName(), "Line num: " + nearby_stops_details[i].line_number + ", direction_name: " + nearby_stops_details[i].direction_name + ",starttime: " + nearby_stops_details[i].start_time + ", tripid: " + nearby_stops_details[i].trip_id + ",predstart: " + nearby_stops_details[i].predicted_start_time);
            string += "Line num: " + nearby_stops_details[i].line_number + ", direction_name: " + nearby_stops_details[i].direction_name + ",starttime: " + nearby_stops_details[i].start_time + ", tripid: " + nearby_stops_details[i].trip_id + ",predstart: "+nearby_stops_details[i].predicted_start_time + "\n";
        }
        nearby_stops_details_on_pebble = nearby_stops_details;
        if (nearby_stops_details_transaction_id < NEARBY_STOPS_DETAILS_LENGTH + 100)
            nearby_stops_details_transaction_id = 100-1;
        else {
            nearby_stops_details_transaction_id = 100;
            send_next_nearby_stops_detail_to_pebble();
        }

        //send_next_nearby_stops_detail_to_pebble();
        if (main_activity != null)
            main_activity.fill_textview(string);
        //send to pebble
    }

    private void send_next_nearby_stops_detail_to_pebble() {
        //if (!PebbleKit.areAppMessagesSupported(getApplicationContext()))
        //    return;

        //for (int i = 0; i < PebbleService.NEARBY_STOPS_DETAILS_LENGTH; i++)
        //{
            int i = nearby_stops_details_transaction_id - 100;
            PebbleDictionary nearby_stop_details = new PebbleDictionary();
            nearby_stop_details.addUint8(0, (byte) MESSAGE_TYPE_GET_NEARBY_STOPS_DETAILS_REPLY);
            nearby_stop_details.addUint8(1, (byte) i);
            nearby_stop_details.addString(2, nearby_stops_details_on_pebble[i].line_number);
            nearby_stop_details.addString(3, nearby_stops_details_on_pebble[i].direction_name);
            int seconds;
            if (nearby_stops_details_on_pebble[i].start_time == 99999)
              seconds = 0;
            else
              seconds = nearby_stops_details_on_pebble[i].start_time;
            nearby_stop_details.addUint32(4, seconds);
            seconds = nearby_stops_details_on_pebble[i].predicted_start_time;
            nearby_stop_details.addUint32(5, seconds);
            //nearby_stop.addString(5, nearby_stops_on_pebble[i].distance_string);
            //Log.i(getPackageName(), "Dictionary: " + nearby_stop_details.toJsonString());
            //if (main_activity != null)
            //    main_activity.fill_textview(nearby_stop_details.toJsonString());
            PebbleKit.sendDataToPebbleWithTransactionId(getApplicationContext(), PEBBLE_APP_UUID, nearby_stop_details, nearby_stops_details_transaction_id);
            //Log.i(getPackageName(), "Data sent.");
        //}
    }

    @Override
    public void onDestroy()
    {
        unregisterReceiver(pebble_data_receiver);
        unregisterReceiver(pebble_ack_receiver);
        unregisterReceiver(pebble_nack_receiver);
        super.onDestroy();

        Toast.makeText(getApplicationContext(), "PebbleService stopped;", Toast.LENGTH_SHORT).show();
    }

}
