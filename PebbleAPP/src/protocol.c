/*
 Protocol:
 
 First byte (dictionary entry 0) is always uint8 representing message_type
 
 enum message_type
 {
   MESSAGE_TYPE_GET_LANGUAGE                   = 0,
   MESSAGE_TYPE_GET_LANGUAGE_REPLY             = 1,
   MESSAGE_TYPE_GET_NEARBY_STOPS               = 2,
   MESSAGE_TYPE_GET_NEARBY_STOPS_REPLY         = 3,
   MESSAGE_TYPE_GET_NEARBY_STOPS_DETAILS       = 4,
   MESSAGE_TYPE_GET_NEARBY_STOPS_DETAILS_REPLY = 5,
   MESSAGE_TYPE_GET_FAVORITES                  = 6,
 };
 
 enum language_type
 {
   LANGUAGE_TYPE_HUNGARIAN                  = 0,
   LANGUAGE_TYPE_ENGLISH                    = 1,
 };
 
 MESSAGE_TYPE_GET_LANGUAGE:
   Pebble->Phone
   
   Get the language used on the phone app. No additional parameters
 
 MESSAGE_TYPE_GET_LANGUAGE_REPLY:
   Phone->Pebble
   
   1 - The language used on the phone app (uint8): enum language_type


 MESSAGE_TYPE_GET_NEARBY_STOPS:
   Pebble->Phone
   
   Get the nearby stops. No additional parameters
   
 MESSAGE_TYPE_GET_NEARBY_STOPS_REPLY:
   Phone->Pebble
   
   1 - nearby_stop_id (uint8). [0-9] - 0
   2 - stop_name (csstring, max length: 31 chars) - "Széll Kálmán tér M"
   3 - direction_name (csstring, max length: 31 chars) - "Széll Kálmán tér M"
   4 - line_numbers (csstring, max length: 31 chars) - "4,6,18,61,2M"
   5 - distance (csstring, max length: 4 chars) - "133m"
   
 MESSAGE_TYPE_GET_NEARBY_STOPS_DETAILS:
   Pebble->Phone
   
   1 - nearby_stop_id (uint8) [0-9], the id of the nearby_stop - 0
   
 MESSAGE_TYPE_GET_NEARBY_STOPS_DETAILS_REPLY:
   Phone->Pebble
   
   1 - nearby_stop_details_id (uint8). [0-9] - 0
   2 - line_number (csstring, max length: 4 chars) - "181"
   3 - direction_name (csstring, max length: 31 chars) - "Széll Kálmán tér M"
   4 - start_time_in_seconds (uint32) - 61281 (17:21)
   5 - predicted_start_time_in_seconds (uint32) - 61282 (17:22)
   
 MESSAGE_TYPE_GET_FAVORITES:
   Pebble->Phone
   
   Get the favorites. No additional parameters
   
 MESSAGE_TYPE_GET_FAVORITES_REPLY:
   Phone->Pebble
   
   1 - line_number (csstring, max length: 4 chars) - "181"
   2 - stop_name (cstring, max length: 34 chars) - "Széll Kálmán tér M"
   3 - direction_name (csstring, max length: 34 chars) - "Széll Kálmán tér M"
   4 - departure_times (csstring, max length: 34 chars) - "20:21 20:41 21:51"
*/