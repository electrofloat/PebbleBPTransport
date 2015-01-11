import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.io.File;
import java.util.zip.GZIPInputStream;

public class TestGtfs {
        
        public static final short AGENCY_TAG = 0x0001;
        public static final short ROUTES_TAG = 0x0002;
        public static final short STOPS_TAG = 0x0003;
        public static final short CALENDAR_TAG = 0x0004;
        public static final short CALENDAR_DATES_TAG = 0x0005;
        public static final short TRIPS_TAG = 0x0006;
        public static final short STOP_TIMES_TAG = 0x0007;
        public static final short STRINGS_TAG = 0x000A;
        public static final short EOF_TAG = 0x000B;
        public static final short NEW_FILE_TAG = 0x0080;
        public static final short DIVIDER_TAG = 0x0081;

        public static FileInputStream in1 = null;
        public static BufferedInputStream in = null;
        public static Connection connection = null;

    public static void main(String[] args) throws IOException {
              
               File f = new File("test.db");
               f.delete();
               f = null;
               try {
                   Class.forName("org.sqlite.JDBC");
                     connection = DriverManager.getConnection("jdbc:sqlite:test.db");
                    System.out.println("Opened database successfully");
            in1 = new FileInputStream("gtfs_binary.bin.gz");
            in = new BufferedInputStream(new GZIPInputStream(in1));
            //decompressGzipFile("gtfs_binary.bin.gz", "gtfs_binary_bin_java");

            //in = new FileInputStream("gtfs_binary_bin_java");
            byte[] tag = new byte[2];
            //tag = read_bytes(2);
            in.read(tag);
            int int_tag = get_int_from_bytes(tag);
            while (int_tag != EOF_TAG)
              {
                    if (int_tag == AGENCY_TAG)
                      {
                        process_agency();
                      }
                    else if (int_tag == STRINGS_TAG)
                      {
                        process_strings();
                      }
                    else if (int_tag == ROUTES_TAG)
                      {
                        process_routes();
                      }
                    else if (int_tag == STOPS_TAG)
                      {
                        process_stops();
                      }
                    else if (int_tag == CALENDAR_TAG)
                      {
                        process_calendar();
                      }
                    else if (int_tag == CALENDAR_DATES_TAG)
                      {
                        process_calendar_dates();
                      }
                    else if (int_tag == TRIPS_TAG)
                      {
                        process_trips();
                      }
                    else if (int_tag == STOP_TIMES_TAG)
                      {
                        process_stop_times();
                      }
                    else if (int_tag == EOF_TAG)
                      {
                        break;
                      }
                    else
                      {
                        System.out.format("Unknown tag: %02X, %02X", tag[0], tag[1]);
                        break;
                      }
               in.read(tag);
               int_tag = get_int_from_bytes(tag);
               System.out.println("READ TAG");
             }
            
            connection.close();
       } catch (Exception e)
         {
           //System.err.println( e.getClass().getName() + ": " + e.getMessage() );
           e.printStackTrace();
           System.exit(0);
        } finally {
            if (in != null) {
                in.close();
            }
        }
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
                //System.out.println(value);
                varints.add(value);
                value = 0;
                base = 1;
              }
          }
        return varints;
      }
    public static int get_int_from_bytes(byte[] bytes)
      {
        int value = 0;
        for (int i = 0; i < bytes.length; i++)
          value += (bytes[i] & 0xff) << (8 * i);
        return value;
      }

    public static void process_agency()
      {
        Statement stmt = null; 
        try {
          stmt = connection.createStatement();
          String sql = "CREATE TABLE agency " +
                     "(ID         INTEGER PRIMARY KEY     NOT NULL," +
                     " NAME       INTEGER    NOT NULL, " + 
                     " URL        INTEGER    NOT NULL, " + 
                     " TIMEZONE   INTEGER    NOT NULL, " + 
                     " LANG       INTEGER    NOT NULL, " + 
                     " PHONE      INTEGER    NOT NULL)";
        stmt.executeUpdate(sql);

        int varints_len = in.read() & 0xff;
        //System.out.println(varints_len);
        byte[] varint_bytes = new byte[varints_len];
        in.read(varint_bytes);
        ArrayList<Integer> varints = decode_varints(varint_bytes);
        //System.out.println(varints);
        //System.out.println(len);
        sql = String.format("INSERT INTO agency (ID,NAME,URL,TIMEZONE,LANG,PHONE) " +
                                  "VALUES (%d,%d,%d,%d,%d,%d);", 1, varints.get(0), varints.get(1), varints.get(2), varints.get(3), varints.get(4));


        stmt.executeUpdate(sql);
        stmt.close();
      } catch (Exception e)
        {
          System.err.println( e.getClass().getName() + ": " + e.getMessage() );
          System.exit(0);
        }
      }

    public static void process_routes()
      {
        System.out.println("Processing routes;");
        Statement stmt = null;
        try {
          stmt = connection.createStatement();
          String sql = "BEGIN TRANSACTION;";
          stmt.executeUpdate(sql);

          sql = "CREATE TABLE routes " +
                     "(_ID                   INTEGER PRIMARY KEY     NOT NULL," +
                     " SHORT_NAME     INTEGER    NOT NULL," +
                     " DESCRIPTION           INTEGER    NOT NULL," +
                     " TYPE           INTEGER    NOT NULL)";
          stmt.executeUpdate(sql);
          int i = 0;
          byte[] tag = new byte[2];
          //System.out.println(in.read(tag));
          in.read(tag);
          int int_tag = get_int_from_bytes(tag);
          while (int_tag != NEW_FILE_TAG)
            {
              i += 1;
              int varints_len = tag[0] & 0xff;
              //System.out.println(varints_len);
              byte[] varint_bytes = new byte[varints_len];
              varint_bytes[0] = tag[1];
              in.read(varint_bytes, 1, varints_len - 1);
              //for (int j = 1; j < varints_len - 1;j++)
              //  varint_bytes[j] = (byte)in.read();
              //System.out.println(varint_bytes);
              ArrayList<Integer> varints = decode_varints(varint_bytes);
              sql = String.format("INSERT INTO routes (_ID,SHORT_NAME,DESCRIPTION,TYPE) " +
                                  "VALUES (%d,%d,%d,%d);", i, varints.get(0), varints.get(1), varints.get(2));

              stmt.executeUpdate(sql);

              in.read(tag);
              int_tag = get_int_from_bytes(tag);

            }
          sql = "END TRANSACTION;";
          stmt.executeUpdate(sql);

          stmt.close();
          System.out.format("  routes: %d\n", i);

        } catch (Exception e)
          {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
          }
      }

    public static void process_stops()
      {
        System.out.println("Processing stops;");
        Statement stmt = null;
        try {
          stmt = connection.createStatement();
          String sql = "BEGIN TRANSACTION;";
          stmt.executeUpdate(sql);
          sql = "CREATE TABLE stops " +
                     "(ID              INTEGER PRIMARY KEY     NOT NULL," +
                     " NAME            INTEGER    NOT NULL," +
                     " LAT             INTEGER    NOT NULL," +
                     " LON             INTEGER    NOT NULL," +
                     " LOCATION_TYPE   INTEGER    NOT NULL," +
                     " PARENT_STATION  INTEGER    ," +
                     " STRING_ID  INTEGER    )";
          stmt.executeUpdate(sql);
          int i = 0;
          byte[] tag = new byte[2];
          in.read(tag);
          int int_tag = get_int_from_bytes(tag);
          while (int_tag != DIVIDER_TAG)
            {
              i += 1;
             //System.out.format("i: %d, varints_len: %d", i, varints_len);
              int varints_len = tag[0] & 0xff;
              byte[] varint_bytes = new byte[varints_len];
              varint_bytes[0] = tag[1];
              in.read(varint_bytes, 1, varints_len - 1);
              ArrayList<Integer> varints = decode_varints(varint_bytes);
              byte[] lat_bytes = new byte[4];
              in.read(lat_bytes);
              byte[] lon_bytes = new byte[4];
              in.read(lon_bytes);
              int location_type = in.read();              
 
              //System.out.println(get_int_from_bytes(lat_bytes));
              sql = String.format("INSERT INTO stops (ID,NAME,LAT,LON,LOCATION_TYPE,STRING_ID) " +
                                  "VALUES (%d,%d,%d,%d,%d,%d);", i, varints.get(1), get_int_from_bytes(lat_bytes), get_int_from_bytes(lon_bytes), location_type, varints.get(0));

              stmt.executeUpdate(sql);
              in.read(tag);
              int_tag = get_int_from_bytes(tag);
            }
          in.read(tag);
          int_tag = get_int_from_bytes(tag);
          while (int_tag != NEW_FILE_TAG)
            {
              int varints_len = tag[0] & 0xff;
              byte[] varint_bytes = new byte[varints_len];
              //System.out.format("varints_len: %d", varints_len);
              varint_bytes[0] = tag[1];
 
              in.read(varint_bytes, 1, varints_len - 1);
              ArrayList<Integer> varints = decode_varints(varint_bytes);
              sql = String.format("UPDATE stops set PARENT_STATION = %d WHERE ID = %d",
                                  varints.get(1), varints.get(0));

              stmt.executeUpdate(sql);

              in.read(tag);
              int_tag = get_int_from_bytes(tag);
            }
            
          sql = "END TRANSACTION;";
          stmt.executeUpdate(sql);

          stmt.close();
          System.out.format("  Stops: %d\n", i);
 
        } catch (Exception e)
          {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
          }
      }

    public static void process_calendar()
      {
        System.out.println("Processing calendar;");
        Statement stmt = null;
        try {
          stmt = connection.createStatement();
          String sql = "BEGIN TRANSACTION;";
          stmt.executeUpdate(sql);

          sql = "CREATE TABLE calendar " +
                     "(_ID                   INTEGER PRIMARY KEY     NOT NULL," +
                     " BITFIELD     INTEGER    NOT NULL," +
                     " START_DATE           INTEGER    NOT NULL," +
                     " DELTA           INTEGER    NOT NULL)";
          stmt.executeUpdate(sql);
          int i = 0;
          byte[] tag = new byte[2];
          in.read(tag);
          int int_tag = get_int_from_bytes(tag);
          //System.out.format("%02X - %02X\n", tag[0], tag[1]);
          while (int_tag != NEW_FILE_TAG)
            {
              i += 1;
              byte[] start_date_bytes = new byte[4];
              start_date_bytes[0] = tag[0];
              start_date_bytes[1] = tag[1];
              in.read(start_date_bytes, 2, 2);
              int start_date = get_int_from_bytes(start_date_bytes);
              int bitfield = in.read() & 0xff;
              int varints_len = in.read() & 0xff;
              //for (int j = 0; j < 4; j++)
              //  System.out.println(start_date_bytes[j]);
              //System.out.format("stardate: %d, varints_len: %d\n", start_date, varints_len);
              byte[] varint_bytes = new byte[varints_len];
              in.read(varint_bytes, 0, varints_len);
              ArrayList<Integer> varints = decode_varints(varint_bytes);
              sql = String.format("INSERT INTO calendar (_ID,BITFIELD,START_DATE,DELTA) " +
                                  "VALUES (%d,%d,%d,%d);", i, bitfield, start_date, varints.get(0));

              stmt.executeUpdate(sql);

              in.read(tag);
              int_tag = get_int_from_bytes(tag);
            }
          System.out.format("  rows: %d\n", i);
          sql = "END TRANSACTION;";
          stmt.executeUpdate(sql);

          stmt.close();

        } catch (Exception e)
          {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
          }
      }

    public static void process_calendar_dates()
      {
        System.out.println("Processing calendar dates;");
        Statement stmt = null;
        try {
          stmt = connection.createStatement();
          String sql = "BEGIN TRANSACTION;";
          stmt.executeUpdate(sql);

          sql = "CREATE TABLE calendar_dates " +
                     "(_ID                   INTEGER     NOT NULL," +
                     " DATE     INTEGER    NOT NULL," +
                     " EXCEPTION_TYPE           INTEGER    NOT NULL)";
          stmt.executeUpdate(sql);
          int i = 0;
          byte[] tag = new byte[2];
          in.read(tag);
          int int_tag = get_int_from_bytes(tag);
          //System.out.format("%02X - %02X\n", tag[0], tag[1]);
          while (int_tag != NEW_FILE_TAG)
            {
              i += 1;
              byte[] date_bytes = new byte[4];
              date_bytes[0] = tag[0];
              date_bytes[1] = tag[1];
              in.read(date_bytes, 2, 2);
              int date = get_int_from_bytes(date_bytes);
              int varints_len = in.read() & 0xff;
              byte[] varint_bytes = new byte[varints_len];
              in.read(varint_bytes, 0, varints_len);
              ArrayList<Integer> varints = decode_varints(varint_bytes);
 
              sql = String.format("INSERT INTO calendar_dates (_ID,DATE,EXCEPTION_TYPE) " +
                                  "VALUES (%d,%d,%d);", varints.get(0), date, varints.get(1));

              stmt.executeUpdate(sql);

              in.read(tag);
              int_tag = get_int_from_bytes(tag);
            }
          System.out.format("  rows: %d\n", i);
          sql = "END TRANSACTION;";
          stmt.executeUpdate(sql);

          stmt.close();

        } catch (Exception e)
          {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
          }
      }

    public static void process_trips()
      {
        System.out.println("Processing trips;");
        Statement stmt = null;
        try {
          stmt = connection.createStatement();
          String sql = "BEGIN TRANSACTION;";
          stmt.executeUpdate(sql);

          sql = "CREATE TABLE trips " +
                     "(ROUTE_ID                   INTEGER     NOT NULL," +
                     " SERVICE_ID     INTEGER    NOT NULL," +
                     " _ID     INTEGER    PRIMARY KEY NOT NULL," +
                     " HEADSIGN     INTEGER    NOT NULL," +
                     " DIRECTION_ID    INTEGER    NOT NULL," +
                     " BLOCK_ID           INTEGER    NOT NULL)";
          stmt.executeUpdate(sql);
          int i = 0;
          byte[] tag = new byte[2];
          in.read(tag);
          int int_tag = get_int_from_bytes(tag);
          //System.out.format("%02X - %02X\n", tag[0], tag[1]);
          while (int_tag != NEW_FILE_TAG)
            {
              i += 1;
              int varints_len = tag[0] & 0xff;
              byte[] varint_bytes = new byte[varints_len];
              varint_bytes[0] = tag[1];
              in.read(varint_bytes, 1, varints_len - 1);
              ArrayList<Integer> varints = decode_varints(varint_bytes);
 
              sql = String.format("INSERT INTO trips (ROUTE_ID,SERVICE_ID,_ID,HEADSIGN,DIRECTION_ID,BLOCK_ID) " +
                                  "VALUES (%d,%d,%d,%d,%d,%d);", varints.get(0),varints.get(1), i, varints.get(2), varints.get(3), varints.get(4));

              stmt.executeUpdate(sql);

              in.read(tag);
              int_tag = get_int_from_bytes(tag);
            }
          System.out.format("  rows: %d\n", i);
          sql = "END TRANSACTION;";
          stmt.executeUpdate(sql);

          stmt.close();

        } catch (Exception e)
          {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
          }
      }

    public static void process_stop_times()
      {
        System.out.println("Processing stop_times;");
        Statement stmt = null;
        try {
          stmt = connection.createStatement();
          String sql = "BEGIN TRANSACTION;";
          stmt.executeUpdate(sql);

          sql = "CREATE TABLE stop_times " +
                     "(_ID                   INTEGER     NOT NULL," +
                     " START_TIME     INTEGER    NOT NULL," +
                     " TIME_SEQ_ID     INTEGER    NOT NULL," +
                     " STOP_SEQ_ID     INTEGER    NOT NULL)";
          stmt.executeUpdate(sql);
          int i = 0;
          byte[] tag = new byte[2];
          in.read(tag);
          int int_tag = get_int_from_bytes(tag);
          //System.out.format("%02X - %02X\n", tag[0], tag[1]);
          while (int_tag != DIVIDER_TAG)
            {
              i += 1;
              int varints_len = tag[0] & 0xff;
              byte[] varint_bytes = new byte[varints_len];
              varint_bytes[0] = tag[1];
              in.read(varint_bytes, 1, varints_len - 1);
              ArrayList<Integer> varints = decode_varints(varint_bytes);
 
              sql = String.format("INSERT INTO stop_times (_ID,START_TIME,TIME_SEQ_ID,STOP_SEQ_ID) " +
                                  "VALUES (%d,%d,%d,%d);", varints.get(0),varints.get(1), varints.get(2), varints.get(3));

              stmt.executeUpdate(sql);

              in.read(tag);
              int_tag = get_int_from_bytes(tag);
            }
          System.out.format("  rows: %d\n", i);
          System.out.println("Time sequences");
          sql = "CREATE TABLE time_sequences " +
                     "(_ID                   INTEGER     NOT NULL," +
                     " SEQUENCE     TEXT    NOT NULL)";
          stmt.executeUpdate(sql);

          in.read(tag);
          int_tag = get_int_from_bytes(tag);
          i = 0;
          while (int_tag != DIVIDER_TAG)
            {
              i += 1;
              int varints_len = tag[0] & 0xff;
              byte[] varint_bytes = new byte[varints_len];
              //System.out.format("varints_len: %d\n", varints_len);
              varint_bytes[0] = tag[1];
              in.read(varint_bytes, 1, varints_len-1);
              ArrayList<Integer> varints = decode_varints(varint_bytes);
              //ANDROID - String joined = TextUtils.join(", ", list);
              String joined = "";
              for (int j = 0; j < varints.size(); j++)
                {
                  joined += String.valueOf(varints.get(j)) + ",";
                }
              //System.out.println(joined);
              sql = String.format("INSERT INTO time_sequences (_ID,SEQUENCE) " +
                                  "VALUES (%d,'%s');", i,joined);


              stmt.executeUpdate(sql);

              in.read(tag);
              int_tag = get_int_from_bytes(tag);
            }
           sql = "CREATE TABLE stop_sequences " +
                     "(_ID                   INTEGER     NOT NULL," +
                     " SEQUENCE     TEXT    NOT NULL)";
          stmt.executeUpdate(sql);

          System.out.println("Stop sequences");
          in.read(tag);
          int_tag = get_int_from_bytes(tag);
          i = 0;
          while (int_tag != NEW_FILE_TAG)
            {
              i += 1;
              int varints_len = tag[0] & 0xff;
              //System.out.format("%d\n", tag[0] & 0xff);
              byte[] varint_bytes = new byte[varints_len];
              varint_bytes[0] = tag[1];
              in.read(varint_bytes, 1, varints_len-1);
              ArrayList<Integer> varints = decode_varints(varint_bytes);
              //ANDROID - String joined = TextUtils.join(", ", list);
              String joined = "";
              for (int j = 0; j < varints.size(); j++)
                {
                  joined += String.valueOf(varints.get(j)) + ",";
                }
              sql = String.format("INSERT INTO stop_sequences (_ID,SEQUENCE) " +
                                  "VALUES (%d,'%s');", i,joined);


              stmt.executeUpdate(sql);

              in.read(tag);
              int_tag = get_int_from_bytes(tag);
            }

          sql = "END TRANSACTION;";
          stmt.executeUpdate(sql);

          stmt.close();

          System.out.println("  Done");
        } catch (Exception e)
          {
            //System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            e.printStackTrace();
            System.exit(0);
          }
      }

    public static void process_strings()
      {
        Statement stmt = null;
        try {
          stmt = connection.createStatement();
          String sql = "CREATE TABLE strings " +
                     "(_ID         INTEGER PRIMARY KEY     NOT NULL," +
                     " STRING     TEXT    NOT NULL)";
        stmt.executeUpdate(sql);
        int i = 0;
        byte[] tag = new byte[2];
        in.read(tag);
        int int_tag = get_int_from_bytes(tag);
        //System.out.println(len);
        while (int_tag != NEW_FILE_TAG)
          {
            i += 1;
            int len = tag[0] & 0xff;
            byte[] string_bytes = new byte[len];
            string_bytes[0] = tag[1];
            in.read(string_bytes, 1, len - 1);
            String string = new String(string_bytes, "UTF-8");
            //System.out.println(string);
            sql = String.format("INSERT INTO strings (_ID,STRING) " +
                                  "VALUES (%d,'%s');", i, string);

            stmt.executeUpdate(sql);

            in.read(tag);
            int_tag = get_int_from_bytes(tag);
          }

          stmt.close();

        } catch (Exception e)
          {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
          }
      }
}
