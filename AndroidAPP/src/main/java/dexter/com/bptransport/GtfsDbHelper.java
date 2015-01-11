package dexter.com.bptransport;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;

/**
 * Created by DEXTER on 2014.10.24..
 */
public class GtfsDbHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "gtfs.db";
    private final File mDatabaseFile;

    private static final String TEXT_TYPE = " TEXT";
    private static final String INTEGER_TYPE = " INTEGER";
    private static final String COMMA_SEP = ",";
    private static final String SQL_CREATE_AGENCY =
            "CREATE TABLE " + GtfsContract.GtfsAgency.TABLE_NAME + " (" +
                    GtfsContract.GtfsAgency._ID + " INTEGER PRIMARY KEY," +
                    GtfsContract.GtfsAgency.COLUMN_NAME + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsAgency.COLUMN_URL + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsAgency.COLUMN_TIMEZONE + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsAgency.COLUMN_LANG + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsAgency.COLUMN_PHONE + INTEGER_TYPE +
            " )";
    private static final String SQL_CREATE_ROUTES =
            "CREATE TABLE " + GtfsContract.GtfsRoutes.TABLE_NAME + " (" +
                    GtfsContract.GtfsRoutes._ID + " INTEGER PRIMARY KEY," +
                    GtfsContract.GtfsRoutes.COLUMN_SHORT_NAME + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsRoutes.COLUMN_DESCRIPTION + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsRoutes.COLUMN_TYPE + INTEGER_TYPE +
                    " )";
    private static final String SQL_CREATE_STOPS =
            "CREATE TABLE " + GtfsContract.GtfsStops.TABLE_NAME + " (" +
                    GtfsContract.GtfsStops._ID + " INTEGER PRIMARY KEY," +
                    GtfsContract.GtfsStops.COLUMN_NAME + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsStops.COLUMN_LAT + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsStops.COLUMN_LON + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsStops.COLUMN_LOCATION_TYPE + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsStops.COLUMN_PARENT_STATION + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsStops.COLUMN_STRING_ID + INTEGER_TYPE +
                    " )";
    private static final String SQL_CREATE_CALENDAR =
            "CREATE TABLE " + GtfsContract.GtfsCalendar.TABLE_NAME + " (" +
                    GtfsContract.GtfsCalendar._ID + " INTEGER PRIMARY KEY," +
                    GtfsContract.GtfsCalendar.COLUMN_BITFIELD + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsCalendar.COLUMN_START_DATE + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsCalendar.COLUMN_DELTA + INTEGER_TYPE +
                    " )";
    private static final String SQL_CREATE_CALENDAR_DATES =
            "CREATE TABLE " + GtfsContract.GtfsCalendarDates.TABLE_NAME + " (" +
                    GtfsContract.GtfsCalendarDates._ID + " INTEGER," +
                    GtfsContract.GtfsCalendarDates.COLUMN_DATE + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsCalendarDates.COLUMN_EXCEPTION_TYPE + INTEGER_TYPE +
                    " )";
    private static final String SQL_CREATE_TRIPS =
            "CREATE TABLE " + GtfsContract.GtfsTrips.TABLE_NAME + " (" +
                    GtfsContract.GtfsTrips._ID + " INTEGER PRIMARY KEY," +
                    GtfsContract.GtfsTrips.COLUMN_ROUTE_ID + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsTrips.COLUMN_SERVICE_ID + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsTrips.COLUMN_HEADSIGN + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsTrips.COLUMN_DIRECTION_ID + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsTrips.COLUMN_BLOCK_ID + INTEGER_TYPE +
                    " )";
    private static final String SQL_CREATE_STOP_TIMES =
            "CREATE TABLE " + GtfsContract.GtfsStopTimes.TABLE_NAME + " (" +
                    GtfsContract.GtfsStopTimes._ID + " INTEGER PRIMARY KEY," +
                    GtfsContract.GtfsStopTimes.COLUMN_START_TIME + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsStopTimes.COLUMN_TIME_SEQ_ID + INTEGER_TYPE + COMMA_SEP +
                    GtfsContract.GtfsStopTimes.COLUMN_STOP_SEQ_ID + INTEGER_TYPE +
                    " )";
    private static final String SQL_CREATE_TIME_SEQUENCES =
            "CREATE TABLE " + GtfsContract.GtfsTimeSequences.TABLE_NAME + " (" +
                    GtfsContract.GtfsTimeSequences._ID + " INTEGER PRIMARY KEY," +
                    GtfsContract.GtfsTimeSequences.COLUMN_SEQUENCE + TEXT_TYPE +
                    " )";
    private static final String SQL_CREATE_STOP_SEQUENCES =
            "CREATE TABLE " + GtfsContract.GtfsStopSequences.TABLE_NAME + " (" +
                    GtfsContract.GtfsStopSequences._ID + " INTEGER PRIMARY KEY," +
                    GtfsContract.GtfsStopSequences.COLUMN_SEQUENCE + TEXT_TYPE +
                    " )";
    private static final String SQL_CREATE_STRINGS =
            "CREATE TABLE " + GtfsContract.GtfsStrings.TABLE_NAME + " (" +
                    GtfsContract.GtfsStrings._ID + " INTEGER PRIMARY KEY," +
                    GtfsContract.GtfsStrings.COLUMN_STRING + TEXT_TYPE +
                    " )";
    private static final String SQL_DELETE_AGENCY =
            "DROP TABLE IF EXISTS " + GtfsContract.GtfsAgency.TABLE_NAME;
    private static final String SQL_DELETE_ROUTES =
            "DROP TABLE IF EXISTS " + GtfsContract.GtfsRoutes.TABLE_NAME;
    private static final String SQL_DELETE_STOPS =
            "DROP TABLE IF EXISTS " + GtfsContract.GtfsStops.TABLE_NAME;
    private static final String SQL_DELETE_CALENDAR =
            "DROP TABLE IF EXISTS " + GtfsContract.GtfsCalendar.TABLE_NAME;
    private static final String SQL_DELETE_CALENDAR_DATES =
            "DROP TABLE IF EXISTS " + GtfsContract.GtfsCalendarDates.TABLE_NAME;
    private static final String SQL_DELETE_TRIPS =
            "DROP TABLE IF EXISTS " + GtfsContract.GtfsTrips.TABLE_NAME;
    private static final String SQL_DELETE_STOP_TIMES =
            "DROP TABLE IF EXISTS " + GtfsContract.GtfsStopTimes.TABLE_NAME;
    private static final String SQL_DELETE_TIME_SEQUENCES =
            "DROP TABLE IF EXISTS " + GtfsContract.GtfsTimeSequences.TABLE_NAME;
    private static final String SQL_DELETE_STOP_SEQUENCES =
            "DROP TABLE IF EXISTS " + GtfsContract.GtfsStopSequences.TABLE_NAME;
    private static final String SQL_DELETE_STRINGS =
            "DROP TABLE IF EXISTS " + GtfsContract.GtfsStrings.TABLE_NAME;


    public GtfsDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mDatabaseFile = context.getDatabasePath(DATABASE_NAME);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_AGENCY);
        db.execSQL(SQL_CREATE_ROUTES);
        db.execSQL(SQL_CREATE_STOPS);
        db.execSQL(SQL_CREATE_CALENDAR);
        db.execSQL(SQL_CREATE_CALENDAR_DATES);
        db.execSQL(SQL_CREATE_TRIPS);
        db.execSQL(SQL_CREATE_STOP_TIMES);
        db.execSQL(SQL_CREATE_TIME_SEQUENCES);
        db.execSQL(SQL_CREATE_STOP_SEQUENCES);
        db.execSQL(SQL_CREATE_STRINGS);

    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(SQL_DELETE_AGENCY);
        db.execSQL(SQL_DELETE_ROUTES);
        db.execSQL(SQL_DELETE_STOPS);
        db.execSQL(SQL_DELETE_CALENDAR);
        db.execSQL(SQL_DELETE_CALENDAR_DATES);
        db.execSQL(SQL_DELETE_TRIPS);
        db.execSQL(SQL_DELETE_STOP_TIMES);
        db.execSQL(SQL_DELETE_TIME_SEQUENCES);
        db.execSQL(SQL_DELETE_STOP_SEQUENCES);
        db.execSQL(SQL_DELETE_STRINGS);
        onCreate(db);
    }

    public void delete()
    {
        mDatabaseFile.delete();
    }

    public long size() {return mDatabaseFile.length();}
}

