package dexter.com.bptransport;


import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Created by DEXTER on 2014.10.24..
 */
public class UpdateDatabase extends AsyncTask<Void, Integer, Void>{
    private static String filename = "gtfs_binary.bin.gz";
    private final int last_update_duration = 3600000; //in milliseconds
    private Context context;
    private PebbleService service;

    public final short AGENCY_TAG = 0x0001;
    public final short ROUTES_TAG = 0x0002;
    public final short STOPS_TAG = 0x0003;
    public final short CALENDAR_TAG = 0x0004;
    public final short CALENDAR_DATES_TAG = 0x0005;
    public final short TRIPS_TAG = 0x0006;
    public final short STOP_TIMES_TAG = 0x0007;
    public final short STRINGS_TAG = 0x000A;
    public final short EOF_TAG = 0x000B;
    public final short NEW_FILE_TAG = 0x0080;
    public final short DIVIDER_TAG = 0x0081;

    public UpdateDatabase(PebbleService srv)
    {
        service = srv;
        context = service.getApplicationContext();
    }

    @Override
    protected Void doInBackground(Void... params) {
        if (!is_connected() || !needs_update())
            return null;

        download_database();
        fill_database();

        return null;
    }

    protected void onProgressUpdate(Integer... progress) {
        //setProgressPercent(progress[0]);
        service.notify_progressbar(progress[0]);
    }

    @Override
    protected void onPostExecute(Void param) {
        service.notify_progressbar(100);
    }

    private boolean is_connected()
    {
        ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            return true;
        }
        return false;
    }

    private boolean needs_update()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Log.d(context.getPackageName(), "bla1");
        String etag_pref = prefs.getString(context.getResources().getString(R.string.saved_etag),"");

        long last_update_pref = prefs.getLong(context.getResources().getString(R.string.saved_last_update), 0);
        long current_time = Calendar.getInstance().getTimeInMillis();
        Log.d(context.getPackageName(), "bla2; last update pref=" + last_update_pref + ", currenttime=" + current_time);
        //if (last_update_pref + last_update_duration >= current_time)
        //    return false;

        Log.d(context.getPackageName(), "bla3");
        String etag = null;
        try {
            URL url = new URL("https://dl.dropboxusercontent.com/s/3q9h6lbxnka1z/gtfs_binary.bin.gz?dl=1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            Log.d(context.getPackageName(), "bla4");
            etag = conn.getHeaderField("etag");
        } catch (Exception e) {

        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(context.getResources().getString(R.string.saved_last_update), current_time);
        editor.putString(context.getResources().getString(R.string.saved_etag), etag);
        editor.commit();
        if (etag_pref.equals(etag))
            return false;

        return true;
    }

    private void download_database()
    {
        try {

            URL url = new URL("https://dl.dropboxusercontent.com/s/3q9h6lbxnka1z/gtfs_binary.bin.gz?dl=1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            // Starts the query
            conn.connect();
            int response = conn.getResponseCode();
            Log.d(context.getPackageName(), "The response is: " + response);
            int total = conn.getContentLength() * 2;
            InputStream inputstream = conn.getInputStream();
            FileOutputStream outputstream = context.openFileOutput(filename, Context.MODE_PRIVATE);
            int read = 0;
            byte[] bytes = new byte[1024];
            int size = 0;

            while ((read = inputstream.read(bytes)) != -1) {
                publishProgress((int)(size*100)/total);
                outputstream.write(bytes, 0, read);
                size += read;
            }
            inputstream.close();
            outputstream.close();
            Log.d(context.getPackageName(), "Database downloaded, size: " + size);

        } catch (Exception e) {

        }
    }

    public int get_int_from_bytes(byte[] bytes)
    {
        int value = 0;
        for (int i = 0; i < bytes.length; i++)
            value += (bytes[i] & 0xff) << (8 * i);
        return value;
    }

    public static ArrayList<Integer> decode_varints(byte[] bytes)
    {
        int value = 0;
        int base = 1;
        ArrayList<Integer> varints = new ArrayList<Integer>();

        for (byte b : bytes)
        {
            int val_byte = (int)b;
            value += (val_byte & 0x7f) * base;
            if ((val_byte & 0x80) != 0)
                base *= 128;
            else
            {
                varints.add(value);
                value = 0;
                base = 1;
            }
        }
        return varints;
    }

    private void fill_database()
    {
        GtfsDbHelper gtfs_dbhelper = new GtfsDbHelper(context);
        gtfs_dbhelper.delete();

        SQLiteDatabase db = gtfs_dbhelper.getWritableDatabase();

        File file = new File(context.getFilesDir(), filename);
        int length = 18;
        int current_pos = 9;

        try {
            InputStream gzip_stream = new BufferedInputStream(new GZIPInputStream(new FileInputStream(file)));

            byte[] tag = new byte[2];
            gzip_stream.read(tag);
            int int_tag = get_int_from_bytes(tag);
            while (int_tag != EOF_TAG) {
                publishProgress((int)(current_pos*100)/length);
                if (int_tag == AGENCY_TAG)
                {
                    process_agency(context, gzip_stream, db);
                }
                else if (int_tag == STRINGS_TAG)
                {
                    process_strings(context, gzip_stream, db);
                }
                else if (int_tag == ROUTES_TAG)
                {
                    process_routes(context, gzip_stream, db);
                }
                else if (int_tag == STOPS_TAG)
                {
                    process_stops(context, gzip_stream, db);
                }
                else if (int_tag == CALENDAR_TAG)
                {
                    process_calendar(context, gzip_stream, db);
                }
                else if (int_tag == CALENDAR_DATES_TAG)
                {
                    process_calendar_dates(context, gzip_stream, db);
                }
                else if (int_tag == TRIPS_TAG)
                {
                    process_trips(context, gzip_stream, db);
                }
                else if (int_tag == STOP_TIMES_TAG)
                {
                    process_stop_times(context, gzip_stream, db);
                }
                else if (Thread.currentThread().interrupted())
                {
                    break;
                }
                else if (int_tag == EOF_TAG)
                {
                    break;
                }
                else
                {
                    Log.d(context.getPackageName(), "Unknown tag: " + tag);
                    break;
                }
                current_pos++;
                gzip_stream.read(tag);
                int_tag = get_int_from_bytes(tag);
            }
        } catch (Exception e) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
        }

        Log.d(context.getPackageName(), "Creating indices;");
        create_indices(context,db);
        //db.execSQL("ANALYZE;");
        //db.execSQL("ANALYZE sqlite_master;");
        Log.d(context.getPackageName(), "  Done;");
        Log.d(context.getPackageName(), "Creating stops_cache;");
        PebbleService.create_stops_cache(db);
        Log.d(context.getPackageName(), "  Done;");
        Log.d(context.getPackageName(), "Database updated;");
        Log.d(context.getPackageName(), "  Downloaded file size: " + file.length() + ", Database file size: " + gtfs_dbhelper.size());
    }

    private void create_indices(Context context, SQLiteDatabase db) {
        db.execSQL("CREATE INDEX trips_route_id ON trips (route_id);");
        db.execSQL("CREATE INDEX stop_times_stop_seq_id ON stop_times (stop_seq_id);");
        db.execSQL("CREATE INDEX routes_short_name ON routes (short_name);");
        db.execSQL("CREATE INDEX calendar_dates_id ON calendar_dates (_id);");
    }

    private void process_agency(Context context, InputStream gzip_stream, SQLiteDatabase db) {
        try {
            Log.d(context.getPackageName(), "Processing agency;");
            //int agency_id = gzip_stream.read();
            int varints_len = gzip_stream.read() & 0xff;
            //System.out.println(varints_len);
            byte[] varint_bytes = new byte[varints_len];
            gzip_stream.read(varint_bytes);
            ArrayList<Integer> varints = decode_varints(varint_bytes);

            ContentValues values = new ContentValues();
            values.put(GtfsContract.GtfsAgency.COLUMN_NAME, varints.get(0));
            values.put(GtfsContract.GtfsAgency.COLUMN_URL, varints.get(1));
            values.put(GtfsContract.GtfsAgency.COLUMN_TIMEZONE, varints.get(2));
            values.put(GtfsContract.GtfsAgency.COLUMN_LANG, varints.get(3));
            values.put(GtfsContract.GtfsAgency.COLUMN_PHONE, varints.get(4));

            db.insert(GtfsContract.GtfsAgency.TABLE_NAME, null, values);

            Log.d(context.getPackageName(), "  Done;");

        } catch (Exception e) {

        }
    }
    private void process_routes(Context context, InputStream gzip_stream, SQLiteDatabase db) {
        db.beginTransaction();
        SQLiteStatement stmt = db.compileStatement("INSERT INTO routes (short_name, description, type) VALUES (?, ?, ?)");
        int i = 0;
        long rows = 0;
        try {
            Log.d(context.getPackageName(), "Processing routes;");

            byte[] tag = new byte[2];
            gzip_stream.read(tag);
            int int_tag = get_int_from_bytes(tag);
            while (int_tag != NEW_FILE_TAG)
            {
                i += 1;
                int varints_len = tag[0] & 0xff;
                byte[] varint_bytes = new byte[varints_len];
                varint_bytes[0] = tag[1];
                gzip_stream.read(varint_bytes, 1, varints_len - 1);
                ArrayList<Integer> varints = decode_varints(varint_bytes);
                //int route_type = gzip_stream.read();

                /*ContentValues values = new ContentValues();
                values.put(GtfsContract.GtfsRoutes.COLUMN_SHORT_NAME, varints.get(0));
                values.put(GtfsContract.GtfsRoutes.COLUMN_DESCRIPTION, varints.get(1));
                values.put(GtfsContract.GtfsRoutes.COLUMN_TYPE, varints.get(2));

                rows = db.insert(GtfsContract.GtfsRoutes.TABLE_NAME, null, values);*/
                stmt.bindLong(1, varints.get(0));
                stmt.bindLong(2, varints.get(1));
                stmt.bindLong(3, varints.get(2));

                rows = stmt.executeInsert();

                gzip_stream.read(tag);
                int_tag = get_int_from_bytes(tag);
            }
            db.setTransactionSuccessful();

        } catch (Exception e) {

        } finally {
            db.endTransaction();
            Log.d(context.getPackageName(), String.format("  Done; inserted_rows='%d', '%d'", i, rows));
        }
    }

    private void process_stops(Context context, InputStream gzip_stream, SQLiteDatabase db) {
        int i = 0;
        long rows = 0;
        db.beginTransaction();
        SQLiteStatement stmt = db.compileStatement("INSERT INTO stops (name, lat, lon, location_type, string_id) VALUES (?, ?, ?, ?, ?)");

        try {
            Log.d(context.getPackageName(), "Processing stops;");

            byte[] tag = new byte[2];
            gzip_stream.read(tag);
            int int_tag = get_int_from_bytes(tag);
            while (int_tag != DIVIDER_TAG)
            {
                i += 1;

                int varints_len = tag[0] & 0xff;
                byte[] varint_bytes = new byte[varints_len];
                varint_bytes[0] = tag[1];
                gzip_stream.read(varint_bytes, 1, varints_len - 1);
                ArrayList<Integer> varints = decode_varints(varint_bytes);
                byte[] lat_bytes = new byte[4];
                gzip_stream.read(lat_bytes);
                byte[] lon_bytes = new byte[4];
                gzip_stream.read(lon_bytes);
                int lon = get_int_from_bytes(lon_bytes);
                int lat = get_int_from_bytes(lat_bytes);
                int location_type = gzip_stream.read();

                /*ContentValues values = new ContentValues();
                values.put(GtfsContract.GtfsStops.COLUMN_NAME, varints.get(0));
                values.put(GtfsContract.GtfsStops.COLUMN_LAT, lat);
                values.put(GtfsContract.GtfsStops.COLUMN_LON, lon);
                values.put(GtfsContract.GtfsStops.COLUMN_LOCATION_TYPE, location_type);

                rows = db.insert(GtfsContract.GtfsStops.TABLE_NAME, null, values);*/
                stmt.bindLong(1, varints.get(1));
                stmt.bindLong(2, lat);
                stmt.bindLong(3, lon);
                stmt.bindLong(4, location_type);
                stmt.bindLong(5, varints.get(0));

                rows = stmt.executeInsert();

                gzip_stream.read(tag);
                int_tag = get_int_from_bytes(tag);
            }
            SQLiteStatement update_stmt = db.compileStatement("UPDATE stops SET parent_station = ? WHERE _id = ?");
            gzip_stream.read(tag);
            int_tag = get_int_from_bytes(tag);
            while (int_tag != NEW_FILE_TAG) {
                int varints_len = tag[0] & 0xff;
                byte[] varint_bytes = new byte[varints_len];
                varint_bytes[0] = tag[1];
                gzip_stream.read(varint_bytes, 1, varints_len - 1);
                ArrayList<Integer> varints = decode_varints(varint_bytes);

                /*ContentValues values = new ContentValues();
                values.put(GtfsContract.GtfsStops.COLUMN_PARENT_STATION, varints.get(1));

                String selection = GtfsContract.GtfsStops._ID + " = ?";
                String[] selectionArgs = { String.valueOf(varints.get(0)) };

                db.update(GtfsContract.GtfsStops.TABLE_NAME, values, selection, selectionArgs);*/
                update_stmt.bindLong(1, varints.get(1));
                update_stmt.bindLong(2, varints.get(0));

                update_stmt.execute();

                gzip_stream.read(tag);
                int_tag = get_int_from_bytes(tag);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.d(context.getPackageName(), e.getClass().getName() + ": " + e.getMessage() + "i=" + i);
        } finally {
            db.endTransaction();
            Log.d(context.getPackageName(), String.format("  Done; inserted_rows='%d', '%d'", i, rows));
        }
    }
    private void process_calendar(Context context, InputStream gzip_stream, SQLiteDatabase db) {
        db.beginTransaction();
        SQLiteStatement stmt = db.compileStatement("INSERT INTO calendar (bitfield, start_date, delta) VALUES (?, ?, ?)");

        int i = 0;
        long rows = 0;

        try {
            Log.d(context.getPackageName(), "Processing calendar;");
            byte[] tag = new byte[2];
            gzip_stream.read(tag);
            int int_tag = get_int_from_bytes(tag);
            //int bitfield = in.read();
            //System.out.format("%02X - %02X\n", tag[0], tag[1]);
            while (int_tag != NEW_FILE_TAG)
            {
                i += 1;
                byte[] start_date_bytes = new byte[4];
                start_date_bytes[0] = tag[0];
                start_date_bytes[1] = tag[1];
                gzip_stream.read(start_date_bytes, 2, 2);
                int start_date = get_int_from_bytes(start_date_bytes);
                int bitfield = gzip_stream.read() & 0xff;
                int varints_len = gzip_stream.read() & 0xff;

                byte[] varint_bytes = new byte[varints_len];
                gzip_stream.read(varint_bytes, 0, varints_len);
                ArrayList<Integer> varints = decode_varints(varint_bytes);

                /*ContentValues values = new ContentValues();
                values.put(GtfsContract.GtfsCalendar.COLUMN_BITFIELD, bitfield);
                values.put(GtfsContract.GtfsCalendar.COLUMN_START_DATE, start_date);
                values.put(GtfsContract.GtfsCalendar.COLUMN_DELTA, varints.get(0));

                rows = db.insert(GtfsContract.GtfsCalendar.TABLE_NAME, null, values);*/
                stmt.bindLong(1, bitfield);
                stmt.bindLong(2, start_date);
                stmt.bindLong(3, varints.get(0));

                rows = stmt.executeInsert();

                gzip_stream.read(tag);
                int_tag = get_int_from_bytes(tag);
            }

            db.setTransactionSuccessful();
        } catch (Exception e) {

        } finally {
            db.endTransaction();
            Log.d(context.getPackageName(), String.format("  Done; inserted_rows='%d', '%d'", i, rows));
        }
    }
    private void process_calendar_dates(Context context, InputStream gzip_stream, SQLiteDatabase db) {
        db.beginTransaction();
        SQLiteStatement stmt = db.compileStatement("INSERT INTO calendar_dates (_id, date, exception_type) VALUES (?, ?, ?)");

        int i = 0;
        long rows = 0;
        try {
            Log.d(context.getPackageName(), "Processing calendar_dates;");

            byte[] tag = new byte[2];
            gzip_stream.read(tag);
            int int_tag = get_int_from_bytes(tag);

            while (int_tag != NEW_FILE_TAG)
            {
                i += 1;
                byte[] date_bytes = new byte[4];
                date_bytes[0] = tag[0];
                date_bytes[1] = tag[1];
                gzip_stream.read(date_bytes, 2, 2);
                int date = get_int_from_bytes(date_bytes);
                int varints_len = gzip_stream.read() & 0xff;
                byte[] varint_bytes = new byte[varints_len];
                gzip_stream.read(varint_bytes, 0, varints_len);
                ArrayList<Integer> varints = decode_varints(varint_bytes);

                /*ContentValues values = new ContentValues();
                values.put(GtfsContract.GtfsCalendarDates._ID, varints.get(0));
                values.put(GtfsContract.GtfsCalendarDates.COLUMN_DATE, date);
                values.put(GtfsContract.GtfsCalendarDates.COLUMN_EXCEPTION_TYPE, varints.get(1));

                rows = db.insert(GtfsContract.GtfsCalendarDates.TABLE_NAME, null, values);*/
                stmt.bindLong(1, varints.get(0));
                stmt.bindLong(2, date);
                stmt.bindLong(3, varints.get(1));

                rows = stmt.executeInsert();

                gzip_stream.read(tag);
                int_tag = get_int_from_bytes(tag);
            }
            db.setTransactionSuccessful();

        } catch (Exception e) {

        } finally {
            db.endTransaction();
            Log.d(context.getPackageName(), String.format("  Done; inserted_rows='%d', '%d'", i, rows));
        }
    }
    private void process_trips(Context context, InputStream gzip_stream, SQLiteDatabase db) {
        db.beginTransaction();
        SQLiteStatement stmt = db.compileStatement("INSERT INTO trips (route_id, service_id, headsign, direction_id, block_id) VALUES (?, ?, ?, ?, ?)");

        int i = 0;
        long rows = 0;
        try {
            Log.d(context.getPackageName(), "Processing trips;");

            byte[] tag = new byte[2];
            gzip_stream.read(tag);
            int int_tag = get_int_from_bytes(tag);
            while (int_tag != NEW_FILE_TAG)
            {
                i += 1;
                int varints_len = tag[0] & 0xff;
                byte[] varint_bytes = new byte[varints_len];
                varint_bytes[0] = tag[1];
                gzip_stream.read(varint_bytes, 1, varints_len - 1);
                ArrayList<Integer> varints = decode_varints(varint_bytes);

                /*ContentValues values = new ContentValues();
                //values.put(GtfsContract.GtfsTrips._ID, varints.get(2));
                values.put(GtfsContract.GtfsTrips.COLUMN_ROUTE_ID, varints.get(0));
                values.put(GtfsContract.GtfsTrips.COLUMN_SERVICE_ID, varints.get(1));
                values.put(GtfsContract.GtfsTrips.COLUMN_HEADSIGN, varints.get(2));
                values.put(GtfsContract.GtfsTrips.COLUMN_DIRECTION_ID, varints.get(3));
                values.put(GtfsContract.GtfsTrips.COLUMN_BLOCK_ID, varints.get(4));

                rows = db.insert(GtfsContract.GtfsTrips.TABLE_NAME, null, values);*/
                stmt.bindLong(1, varints.get(0));
                stmt.bindLong(2, varints.get(1));
                stmt.bindLong(3, varints.get(2));
                stmt.bindLong(4, varints.get(3));
                stmt.bindLong(5, varints.get(4));

                rows = stmt.executeInsert();


                gzip_stream.read(tag);
                int_tag = get_int_from_bytes(tag);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {

        } finally {
            db.endTransaction();
            Log.d(context.getPackageName(), String.format("  Done; inserted_rows='%d', '%d'", i, rows));
        }
    }
    private void process_stop_times(Context context, InputStream gzip_stream, SQLiteDatabase db) {
        Map<Integer, Set<Integer>> stop_id_cache = new HashMap<Integer, Set<Integer>>();
        db.beginTransaction();
        SQLiteStatement stmt = db.compileStatement("INSERT INTO stop_times (_id, start_time, time_seq_id, stop_seq_id) VALUES (?, ?, ?, ?)");

        int i = 0;
        long rows = 0;
        try {
            Log.d(context.getPackageName(), "Processing stop_times;");

            byte[] tag = new byte[2];
            gzip_stream.read(tag);
            int int_tag = get_int_from_bytes(tag);
            //int bitfield = in.read();
            //System.out.format("%02X - %02X\n", tag[0], tag[1]);
            while (int_tag != DIVIDER_TAG)
            {
                i += 1;
                int varints_len = tag[0] & 0xff;
                byte[] varint_bytes = new byte[varints_len];
                varint_bytes[0] = tag[1];
                gzip_stream.read(varint_bytes, 1, varints_len - 1);
                ArrayList<Integer> varints = decode_varints(varint_bytes);

                /*ContentValues values = new ContentValues();
                values.put(GtfsContract.GtfsStopTimes._ID, varints.get(0));
                values.put(GtfsContract.GtfsStopTimes.COLUMN_START_TIME, varints.get(1));
                values.put(GtfsContract.GtfsStopTimes.COLUMN_TIME_SEQ_ID, varints.get(2));
                values.put(GtfsContract.GtfsStopTimes.COLUMN_STOP_SEQ_ID, varints.get(3));

                rows = db.insert(GtfsContract.GtfsStopTimes.TABLE_NAME, null, values);*/
                stmt.bindLong(1, varints.get(0));
                stmt.bindLong(2, varints.get(1));
                stmt.bindLong(3, varints.get(2));
                stmt.bindLong(4, varints.get(3));

                rows = stmt.executeInsert();

                gzip_stream.read(tag);
                int_tag = get_int_from_bytes(tag);
            }

            SQLiteStatement time_stmt = db.compileStatement("INSERT INTO time_sequences (sequence) VALUES (?)");
            gzip_stream.read(tag);
            int_tag = get_int_from_bytes(tag);
            while (int_tag != DIVIDER_TAG)
            {
                int varints_len = tag[0] & 0xff;
                byte[] varint_bytes = new byte[varints_len];
                varint_bytes[0] = tag[1];
                gzip_stream.read(varint_bytes, 1, varints_len - 1);
                ArrayList<Integer> varints = decode_varints(varint_bytes);
                String joined = "," + TextUtils.join(",", varints) + ",";
                /*String joined = "";
                for (int j = 1; j < varints.size(); j++)
                {
                    joined += String.valueOf(varints.get(j)) + ",";
                }*/
                //System.out.println(joined);
                /*ContentValues values = new ContentValues();
                //values.put(GtfsContract.GtfsTimeSequences._ID, varints.get(0));
                values.put(GtfsContract.GtfsTimeSequences.COLUMN_SEQUENCE, joined);
                rows = db.insert(GtfsContract.GtfsTimeSequences.TABLE_NAME, null, values);*/

                time_stmt.bindString(1, joined);

                rows = time_stmt.executeInsert();

                gzip_stream.read(tag);
                int_tag = get_int_from_bytes(tag);
            }
            SQLiteStatement stop_stmt = db.compileStatement("INSERT INTO stop_sequences (sequence) VALUES (?)");
            gzip_stream.read(tag);
            int_tag = get_int_from_bytes(tag);

            int id = 0;
            while (int_tag != NEW_FILE_TAG)
            {
                id++;
                int varints_len = tag[0] & 0xff;
                byte[] varint_bytes = new byte[varints_len];
                varint_bytes[0] = tag[1];

                gzip_stream.read(varint_bytes, 1, varints_len - 1);
                ArrayList<Integer> varints = decode_varints(varint_bytes);
                for (int stop_id : varints)
                {
                    Set<Integer> value = stop_id_cache.get(stop_id);
                    if (value == null)
                    {
                        value = new HashSet<Integer>();
                        value.add(id);
                        stop_id_cache.put(stop_id, value);
                    }
                    else {
                        value.add(id);
                    }
                }
                String joined = "," + TextUtils.join(",", varints) + ",";
                /*String joined = "";
                for (int j = 1; j < varints.size(); j++)
                {
                    joined += String.valueOf(varints.get(j)) + ",";
                }*/
                /*ContentValues values = new ContentValues();
                //values.put(GtfsContract.GtfsStopSequences._ID, varints.get(0));
                values.put(GtfsContract.GtfsStopSequences.COLUMN_SEQUENCE, joined);
                rows = db.insert(GtfsContract.GtfsStopSequences.TABLE_NAME, null, values);*/
                stop_stmt.bindString(1, joined);

                rows = stop_stmt.executeInsert();
                gzip_stream.read(tag);
                int_tag = get_int_from_bytes(tag);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {

        } finally {
            db.endTransaction();
            PebbleService.stop_id_cache.clear();
            PebbleService.stop_id_cache.putAll(stop_id_cache);
            Log.d(context.getPackageName(), String.format("  Done; inserted_rows='%d', '%d'", i, rows));
        }
    }
    private void process_strings(Context context, InputStream gzip_stream, SQLiteDatabase db) {
        db.beginTransaction();
        SQLiteStatement stmt = db.compileStatement("INSERT INTO strings (string) VALUES (?)");

        int i = 0;
        long rows = 0;
        try {
            Log.d(context.getPackageName(), "Processing strings;");

            byte[] tag = new byte[2];
            gzip_stream.read(tag);
            int int_tag = get_int_from_bytes(tag);
            //int len = in.read();
            //System.out.println(len);
            while (int_tag != NEW_FILE_TAG)
            {
                i += 1;
                int len = tag[0] & 0xff;
                byte[] string_bytes = new byte[len];
                string_bytes[0] = tag[1];
                gzip_stream.read(string_bytes, 1, len - 1);
                String string = new String(string_bytes, "UTF-8");

                /*ContentValues values = new ContentValues();
                //values.put(GtfsContract.GtfsStrings._ID, i);
                values.put(GtfsContract.GtfsStrings .COLUMN_STRING, string);
                rows = db.insert(GtfsContract.GtfsStrings.TABLE_NAME, null, values);*/
                stmt.bindString(1, string);

                rows = stmt.executeInsert();

                gzip_stream.read(tag);
                int_tag = get_int_from_bytes(tag);
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {

        } finally {
            db.endTransaction();
            Log.d(context.getPackageName(), String.format("  Done; inserted_rows='%d', '%d'", i, rows));
        }
    }

    public void update(Context context)
    {

    }
}
