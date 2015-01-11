#pragma once
#include <pebble.h>
  
enum window_types
{
  WINDOW_TYPE_MAIN_MENU,
  WINDOW_TYPE_NEARBY_STOPS,
  WINDOW_TYPE_NEARBY_STOPS_DETAILS,
  WINDOW_TYPE_FAVORITES,
};

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

void set_current_window(enum window_types num);
void switch_window(enum window_types num);
