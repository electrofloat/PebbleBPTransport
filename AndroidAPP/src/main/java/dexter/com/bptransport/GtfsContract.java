package dexter.com.bptransport;

import android.provider.BaseColumns;

/**
 * Created by DEXTER on 2014.10.24..
 */
public final class GtfsContract {
    public GtfsContract() {};

    public static abstract class GtfsAgency implements BaseColumns
    {
        public static final String TABLE_NAME = "agency";
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_URL = "url";
        public static final String COLUMN_TIMEZONE = "timezone";
        public static final String COLUMN_LANG = "lang";
        public static final String COLUMN_PHONE = "phone";
    }

    public static abstract class GtfsRoutes implements BaseColumns
    {
        public static final String TABLE_NAME = "routes";
        public static final String COLUMN_SHORT_NAME = "short_name";
        public static final String COLUMN_DESCRIPTION = "description";
        public static final String COLUMN_TYPE = "type";
    }

    public static abstract class GtfsStops implements BaseColumns
    {
        public static final String TABLE_NAME = "stops";
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_LAT = "lat";
        public static final String COLUMN_LON = "lon";
        public static final String COLUMN_LOCATION_TYPE = "location_type";
        public static final String COLUMN_PARENT_STATION = "parent_station";
        public static final String COLUMN_STRING_ID = "string_id";
    }

    public static abstract class GtfsCalendar implements BaseColumns
    {
        public static final String TABLE_NAME = "calendar";
        public static final String COLUMN_BITFIELD = "bitfield";
        public static final String COLUMN_START_DATE = "start_date";
        public static final String COLUMN_DELTA = "delta";
    }

    public static abstract class GtfsCalendarDates implements BaseColumns
    {
        public static final String TABLE_NAME = "calendar_dates";
        public static final String COLUMN_DATE = "date";
        public static final String COLUMN_EXCEPTION_TYPE = "exception_type";
    }

    public static abstract class GtfsTrips implements BaseColumns
    {
        public static final String TABLE_NAME = "trips";
        public static final String COLUMN_ROUTE_ID = "route_id";
        public static final String COLUMN_SERVICE_ID = "service_id";
        public static final String COLUMN_HEADSIGN = "headsign";
        public static final String COLUMN_DIRECTION_ID = "direction_id";
        public static final String COLUMN_BLOCK_ID = "block_id";
    }

    public static abstract class GtfsStopTimes implements BaseColumns
    {
        public static final String TABLE_NAME = "stop_times";
        //public static final String COLUMN_TRIP_ID = "trip_id";
        public static final String COLUMN_START_TIME = "start_time";
        public static final String COLUMN_TIME_SEQ_ID = "time_seq_id";
        public static final String COLUMN_STOP_SEQ_ID = "stop_seq_id";
    }

    public static abstract class GtfsTimeSequences implements BaseColumns
    {
        public static final String TABLE_NAME = "time_sequences";
        public static final String COLUMN_SEQUENCE = "sequence";
    }

    public static abstract class GtfsStopSequences implements BaseColumns
    {
        public static final String TABLE_NAME = "stop_sequences";
        public static final String COLUMN_SEQUENCE = "sequence";
    }

    public static abstract class GtfsStrings implements BaseColumns
    {
        public static final String TABLE_NAME = "strings";
        public static final String COLUMN_STRING = "string";
    }
}