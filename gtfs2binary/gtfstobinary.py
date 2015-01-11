import csv
import os
import struct
import time
import operator
import gzip
import sys

class MyError(Exception):
    def __init__(self, text):
        self.text = text
    def __str__(self):
        return repr(self.text)

class GTFSToBinary(object):
    agency_ids = {}
    route_ids = {}
    stop_ids = {}
    service_ids = {}
    trip_ids = {}
    block_ids = {}
    block_ids_index = 0
    time_sequence_ids = {}
    time_sequence_ids_int = {}
    time_sequence_ids_index = 0
    stop_sequence_ids = {}
    stop_sequence_ids_int = {}
    stop_sequence_ids_index = 0
    string_ids = {}
    string_ids_index = 0
    AGENCY_TAG = 0x0001
    ROUTES_TAG = 0x0002
    STOPS_TAG = 0x0003
    CALENDAR_TAG = 0x0004
    CALENDAR_DATES_TAG = 0x0005
    TRIPS_TAG = 0x0006
    STOP_TIMES_TAG = 0x0007
    STRINGS_TAG = 0x000A
    EOF_TAG = 0x000B
    NEW_FILE_TAG = 0x0080
    DIVIDER_TAG = 0x0081

    def __init__(self, dir):
        self.dir = dir
        try:
            self.binary_data = gzip.open(self.dir + 'gtfs_binary.bin.gz','wb')
        except IOError as e:
            print("Error opening gtfs_binary.bin, error: %s" % os.strerror(e.errno))
        
    def encode_varint(self, values):
        for value in values:
            while True:
                if value > 127:
                    yield chr((1 << 7) | (value & 0x7F))
                    value >>=  7
                else:
                    yield chr(value)
                    break

    def get_stringid(self, string):
        if not string in self.string_ids:
            self.string_ids_index += 1
            self.string_ids[string] = self.string_ids_index
            return self.string_ids_index

        return self.string_ids.get(string)
        #self.binary_data.write(struct.pack('<B', len(string)))
        #for j in range(0, len(string)):
        #    self.binary_data.write(struct.pack('<B', ord(string[j])))

    def get_blockid(self, string):
        if not string in self.block_ids:
            self.block_ids_index += 1
            self.block_ids[string] = self.block_ids_index
            return self.block_ids_index

        return self.block_ids[string]

    def get_time_sequence_id(self,time_sequence, time_sequence_int):
        if ''.join(time_sequence) in self.time_sequence_ids:
            return self.time_sequence_ids[''.join(time_sequence)]

        self.time_sequence_ids_index += 1
        self.time_sequence_ids[''.join(time_sequence)] = self.time_sequence_ids_index
        self.time_sequence_ids_int[self.time_sequence_ids_index] = time_sequence_int

        return self.time_sequence_ids_index

    def get_stop_sequence_id(self,stop_sequence, stop_sequence_int):
        if ''.join(stop_sequence) in self.stop_sequence_ids:
            return self.stop_sequence_ids[''.join(stop_sequence)]

        self.stop_sequence_ids_index += 1
        self.stop_sequence_ids[''.join(stop_sequence)] = self.stop_sequence_ids_index
        self.stop_sequence_ids_int[self.stop_sequence_ids_index] = stop_sequence_int

        return self.stop_sequence_ids_index

    def write_strings(self):
        self.binary_data.write(struct.pack('<H', self.STRINGS_TAG))
        sorted_string_ids = sorted(self.string_ids.items(), key=operator.itemgetter(1))
        for v in sorted_string_ids:
            self.binary_data.write(struct.pack('<B', len(v[0])))
            self.binary_data.write(v[0])
            #print i, k
            #for j in range(0, len(k)):
            #    self.binary_data.write(struct.pack('<B', ord(k[j])))

        self.binary_data.write(struct.pack('<H', self.NEW_FILE_TAG))
           
    def process_data(self):
        self.process_agency()
        self.process_routes()
        self.process_stops()
        self.process_calendar()
        self.process_calendar_dates()
        self.process_trips()
        self.process_stop_times()
        self.write_strings()

        self.binary_data.write(struct.pack('<H', self.EOF_TAG))
        self.binary_data.close()

    def process_agency(self):
        """
           AGENCY_TAG(2 bytes), varint_len(byte), varint[agency_name,agency_url,agency_timezone, agency_lang, agency_phone]
        """
        try:
            with open(self.dir + 'agency.txt', 'r') as agency:
                self.binary_data.write(struct.pack('<H', self.AGENCY_TAG))

                #has_header = csv.Sniffer().has_header(agency.read(1024))
                #agency.seek(0)
                has_header = True
                csv_reader = csv.reader(agency, delimiter=',', quotechar='"')
                if has_header:
                    csv_reader.next()
                i = 0
                for row in csv_reader:
                    i += 1
                    if i > 1:
                        raise Error("agency.txt has more then 1 row!")

                    self.agency_ids[row[0]] = i
                    #self.binary_data.write(struct.pack('<B', i))
                    varints = ""
                    for j in range(1,6):
                        #print varints
                        varints += ''.join(self.encode_varint([self.get_stringid(row[j])]))
                    #print len(varints)
                    #print " ".join(hex(ord(n)) for n in varints)
                    self.binary_data.write(struct.pack('<B', len(varints)))
                    self.binary_data.write(varints)

        except IOError as e:
            print("Error reading agency.txt, error: %s" % os.strerror(e.errno))

    def process_routes(self):
        """
            ROUTES_TAG(2 bytes), varint_len(byte), varints[route_short_name, route_desc, route_type], NEW_FILE_TAG(2 bytes)
        """
        try:
            with open(self.dir + 'routes.txt', 'rb') as routes:
                self.binary_data.write(struct.pack('<H', self.ROUTES_TAG))

                #has_header = csv.Sniffer().has_header(routes.read(1024))
                #routes.seek(0)
                has_header = True
                csv_reader = csv.reader(routes, delimiter=',', quotechar='"')
                if has_header:
                    csv_reader.next()
                i = 0
                for row in csv_reader:
                    i += 1
                    self.route_ids[row[0]] = i
                    #agency_id = self.agency_ids.get(row[1])
                    #if not agency_id:
                    #    raise Error("Agency id not found: %s", row[1])
                    varints = ""
                    varints += ''.join(self.encode_varint([self.get_stringid(row[2])]))
                    varints += ''.join(self.encode_varint([self.get_stringid(row[4])]))
                    varints += ''.join(self.encode_varint([self.get_stringid(row[5])]))
                    self.binary_data.write(struct.pack('<B', len(varints)))
                    self.binary_data.write(varints)
                    #self.binary_data.write(struct.pack('<B', int(row[5])))


                print ("routes: %d" % i)
            self.binary_data.write(struct.pack('<H', self.NEW_FILE_TAG))
        except IOError as e:
            print("Error reading routes.txt, error: %s" % os.strerror(e.errno))

    def process_stops(self):
        """
            STOPS_TAG(2 bytes), varints_len(byte), varints[stop_name], stop_lat(4 bytes), stop_lon(4 bytes), loc_type(byte), DIVIDER_TAG(byte), varints_len(byte), varints[stop_id, parent_id], NEW_FILE_TAG
        """
        try:
            with open(self.dir + 'stops.txt', 'r') as routes:
                self.binary_data.write(struct.pack('<H', self.STOPS_TAG))

                #has_header = csv.Sniffer().has_header(routes.read(1024))
                #routes.seek(0)
                has_header = True
                csv_reader = csv.reader(routes, delimiter=',', quotechar='"')
                if has_header:
                    csv_reader.next()
                i = 0
                for row in csv_reader:
                    i += 1
                    self.stop_ids[row[0]] = i
                    stringid = self.get_stringid(row[1])
                    #print i, stringid, row[1]
                    varints = ''.join(self.encode_varint([self.get_stringid(row[0]), stringid]))
                    self.binary_data.write(struct.pack('<B',  len(varints)))
                    #print len(varints)
                    self.binary_data.write(varints)
                    lat = int(float(row[2]) * 1000000)
                    lon = int(float(row[3]) * 1000000)
                    loc_type = 0 if not row[4] else int(row[4])
                    self.binary_data.write(struct.pack('<iiB', lat, lon, loc_type))
                    #self.binary_data.write(struct.pack('<H', self.get_stringid(row[0])));
                    #varints = ''.join(self.encode_varint([self.get_stringid(row[0])]));
                    #self.binary_data.write(struct.pack('<B', len(varints)));
                    #self.binary_data.write(varints)

                print ("stops: %d" % i)
                self.binary_data.write(struct.pack('<H', self.DIVIDER_TAG))
                routes.seek(0)
                if has_header:
                    csv_reader.next()
                i = 0
                for row in csv_reader:
                    i += 1
                    if row[5]:
                        parent_id = self.stop_ids.get(row[5])
                        varints = ''.join(self.encode_varint([i, parent_id]))
                        self.binary_data.write(struct.pack('<B', len(varints)))
                        self.binary_data.write(varints)

            self.binary_data.write(struct.pack('<H', self.NEW_FILE_TAG))
        except IOError as e:
            print("Error reading stops.txt, error: %s" % os.strerror(e.errno))

    def process_calendar(self):
        """
            CALENDAR_TAG(2 bytes), start_date(4 bytes), bitfield(byte), varints_len(byte), varints[delta], NEW_FILE_TAG(2 bytes)
        """
        try:
            with open(self.dir + 'calendar.txt', 'r') as routes:
                self.binary_data.write(struct.pack('<H', self.CALENDAR_TAG))

                #has_header = csv.Sniffer().has_header(routes.read(1024))
                #routes.seek(0)
                has_header = True
                csv_reader = csv.reader(routes, delimiter=',', quotechar='"')
                if has_header:
                    csv_reader.next()
                i = 0
                for row in csv_reader:
                    i += 1
                    self.service_ids[row[0]] = i
                    bitfield = 0
                    for j in range(1,8):
                        if row[j] == '1':
                            bitfield |= (1 << (8-j-1))
                    startdate = int(time.mktime(time.strptime(row[8], "%Y%m%d")))
                    enddate = int(time.mktime(time.strptime(row[9], "%Y%m%d")))
                    delta = enddate - startdate
                    self.binary_data.write(struct.pack('<iB', startdate, bitfield))
                    varints = ''.join(self.encode_varint([delta]))
                    self.binary_data.write(struct.pack('<B', len(varints)))
                    self.binary_data.write(varints)

                print ("calendar: %d" % i)

            self.binary_data.write(struct.pack('<H', self.NEW_FILE_TAG))
        except IOError as e:
            print("Error reading calendar.txt, error: %s" % os.strerror(e.errno))

    def process_calendar_dates(self):
        """
            CALENDAR_DATES_TAG(2 bytes), date(4 bytes), varints_len(byte), varints[service_id, exception_type], NEW_FILE_TAG(2 bytes)
        """
        try:
            with open(self.dir + 'calendar_dates.txt', 'r') as routes:
                self.binary_data.write(struct.pack('<H', self.CALENDAR_DATES_TAG))

                #has_header = csv.Sniffer().has_header(routes.read(1024))
                #routes.seek(0)
                has_header = True
                csv_reader = csv.reader(routes, delimiter=',', quotechar='"')
                if has_header:
                    csv_reader.next()
                i = 0
                for row in csv_reader:
                    i += 1
                    service_id = self.service_ids.get(row[0])
                    date = int(time.mktime(time.strptime(row[1], "%Y%m%d")))
                    self.binary_data.write(struct.pack('<i', date))
                    varints = ''.join(self.encode_varint([service_id, int(row[2])]))
                    self.binary_data.write(struct.pack('<B', len(varints)))
                    self.binary_data.write(varints)

                print ("calendar dates: %d" % i)

            self.binary_data.write(struct.pack('<H', self.NEW_FILE_TAG))
        except IOError as e:
            print("Error reading calendar.txt, error: %s" % os.strerror(e.errno))

    def process_trips(self):
        """
            TRIPS_TAG(2 bytes), varints_len(byte), varints[route_id, service_id, trip_headsign, direction_id, block_id], NEW_FILE_TAG(2bytes)
        """
        try:
            with open(self.dir + 'trips.txt', 'r') as routes:
                self.binary_data.write(struct.pack('<H', self.TRIPS_TAG))

                #has_header = csv.Sniffer().has_header(routes.read(1024))
                #routes.seek(0)
                has_header = True
                csv_reader = csv.reader(routes, delimiter=',', quotechar='"')
                if has_header:
                    csv_reader.next()
                i = 0
                for row in csv_reader:
                    i += 1
                    route_id = self.route_ids.get(row[0])
                    service_id = self.service_ids.get(row[1])
                    self.trip_ids[row[2]] = i
                    trip_id = i
                    trip_headsign = self.get_stringid(row[3])
                    direction_id = int(row[4])
                    block_id = self.get_blockid(row[5])
                    varints = ''.join(self.encode_varint([route_id, service_id, trip_headsign, direction_id, block_id]))

                    self.binary_data.write(struct.pack('<B', len(varints)))
                    self.binary_data.write(varints)

                print ("trips: %d" % i)

            self.binary_data.write(struct.pack('<H', self.NEW_FILE_TAG))
        except IOError as e:
            print("Error reading trips.txt, error: %s" % os.strerror(e.errno))

    def process_stop_times(self):
        """
            STOP_TIMES_TAG(2 bytes), varints_len(byte), varints[trip_id, start_time, time_sequence_id, stop_sequence_id], DIVIDER_TAG(2bytes)
            varints_len(byte), varints[time_sequences_list], DIVIDER_TAG(2 bytes), varints_len(byte), varints[stop_sequences_list], NEW_FILE_TAG(2 bytes)
        """
        try:
            with open(self.dir + 'stop_times.txt', 'r') as routes:
                self.binary_data.write(struct.pack('<H', self.STOP_TIMES_TAG))

                #has_header = csv.Sniffer().has_header(routes.read(1024))
                #routes.seek(0)
                has_header = True
                csv_reader = csv.reader(routes, delimiter=',', quotechar='"')
                if has_header:
                    csv_reader.next()
                i = 0
                time_sequence = []
                time_sequence_int = []
                stop_sequence = []
                stop_sequence_int = []
                row = csv_reader.next()
                row_id = row[0]
                old_row_id = row_id
                hour = row[2][:2]
                mins = row[2][3:5]
                time_mins = int(hour) * 60 + int(mins)
                start_time = time_mins
                previous_time = start_time
                stop_sequence.append(str(self.stop_ids[row[3]]))
                stop_sequence_int.append(self.stop_ids[row[3]])
                trip_id = self.trip_ids.get(row[0])

                while True:
                    i += 1
                    if row_id != old_row_id:
                        time_seq_id = self.get_time_sequence_id(time_sequence, time_sequence_int)
                        stop_seq_id = self.get_stop_sequence_id(stop_sequence, stop_sequence_int)
                        #write trip_id, start_time, time_seq_id, stop_seq_id
                        #print trip_id, start_time, time_seq_id, stop_seq_id
                        varints = ''.join(self.encode_varint([trip_id, start_time, time_seq_id, stop_seq_id]))
                        self.binary_data.write(struct.pack('<B', len(varints)))
                        self.binary_data.write(varints)

                        trip_id = self.trip_ids.get(row[0])
                        old_row_id = row_id
                        start_time = time_mins
                        time_sequence = []
                        time_sequence_int = []
                        stop_sequence = []
                        stop_sequence_int = []
                        stop_sequence.append(str(self.stop_ids[row[3]]))
                        stop_sequence_int.append(self.stop_ids[row[3]])

                    try:
                        row = csv_reader.next()
                    except StopIteration:
                        time_seq_id = self.get_time_sequence_id(time_sequence, time_sequence_int)
                        stop_seq_id = self.get_stop_sequence_id(stop_sequence, stop_sequence_int)
                        varints = ''.join(self.encode_varint([trip_id, start_time, time_seq_id, stop_seq_id]))
                        self.binary_data.write(struct.pack('<B', len(varints)))
                        self.binary_data.write(varints)

                        break;

                    row_id = row[0]
                    hour = row[2][:2]
                    mins = row[2][3:5]
                    time_mins = int(hour) * 60 + int(mins)
                    delta = time_mins - previous_time
                    previous_time = time_mins
                    if row_id == old_row_id:
                        time_sequence.append(str(delta))
                        time_sequence_int.append(delta)
                        stop_sequence.append(str(self.stop_ids[row[3]]))
                        stop_sequence_int.append(self.stop_ids[row[3]])
 
                print ("stop_times: %d" % i)
                self.binary_data.write(struct.pack('<H', self.DIVIDER_TAG))
                self.process_sequences(self.time_sequence_ids_int)
                self.binary_data.write(struct.pack('<H', self.DIVIDER_TAG))
                #import pdb; pdb.set_trace()	
                self.process_sequences(self.stop_sequence_ids_int)

            self.binary_data.write(struct.pack('<H', self.NEW_FILE_TAG))
        except IOError as e:
            print("Error reading stop_times.txt, error: %s" % os.strerror(e.errno))

    def process_sequences(self, sequences):
        sorted_sequences = sorted(sequences.items(), key=operator.itemgetter(0))
        for v in sorted_sequences:
            #varints = ''.join(self.encode_varint([k]))
            varints = ''.join(self.encode_varint(v[1]))
            self.binary_data.write(struct.pack('<B', len(varints)))
            ize = struct.pack("<B", len(varints))
            #print " ".join(hex(ord(n)) for n in ize) 

            #if len(varints) > 200:
            #  print len(varints)
            self.binary_data.write(varints)
            #print v, len(varints)

def main():
    directory = "./"
    if len(sys.argv) > 1:
        directory = sys.argv[1] + "/";
    gtfs_to_binary = GTFSToBinary(directory)
    gtfs_to_binary.process_data()

if __name__ == "__main__":
    main()
