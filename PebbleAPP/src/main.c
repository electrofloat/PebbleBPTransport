#include <main.h> 
#include <mainmenu.h>
#include <nearbystops.h>
#include <nearbystopsdetails.h>
#include <favorites.h>
  
enum window_types current_window = WINDOW_TYPE_MAIN_MENU;

void
set_current_window(enum window_types num)
{
  current_window = num;
}

void
switch_window(enum window_types num)
{
  set_current_window(num);
  switch (num)
    {
    case WINDOW_TYPE_MAIN_MENU:
      main_menu_init();
      break;
    case WINDOW_TYPE_NEARBY_STOPS:
      nearby_stops_init();
      break;
    case WINDOW_TYPE_NEARBY_STOPS_DETAILS:
      nearby_stops_details_init();
      break;
    case WINDOW_TYPE_FAVORITES:
      favorites_init();
      break;
    };
}

void
data_received(DictionaryIterator *received, void *context)
{
  uint8_t packet_id = dict_find(received, 0)->value->uint8;

  switch (current_window)
    {
      case WINDOW_TYPE_MAIN_MENU:
        main_menu_data_received(packet_id, received);
        break;
      case WINDOW_TYPE_NEARBY_STOPS:
        nearby_stops_data_received(packet_id, received);
        break;
      case WINDOW_TYPE_NEARBY_STOPS_DETAILS:
        nearby_stops_details_data_received(packet_id, received);
        break;
      case WINDOW_TYPE_FAVORITES:
        favorites_data_received(packet_id, received);
        break;
    };
}

void
data_sent(DictionaryIterator *sent, void *context)
{
  return;
}
  
int main() {
  app_message_register_outbox_sent(data_sent);
  app_message_register_inbox_received(data_received);
  app_message_open(app_message_inbox_size_maximum(), app_message_outbox_size_maximum());

  main_menu_init();
  
  app_event_loop();
  return 0;
}