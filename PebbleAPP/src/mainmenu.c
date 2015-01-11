#include <pebble.h>
#include <main.h>

#define MAIN_MENU_ITEM_STRING_LENGTH 20
  
enum language_types
{
  LANGUAGE_HUNGARIAN = 0,
  LANGUAGE_ENGLISH,
};
  
enum menu_item_types
{
  MAIN_MENU_ITEM_NEARBY_STOPS = 0,
  MAIN_MENU_ITEM_FAVORITES,
  MAIN_MENU_ITEM_SCHEDULES,
  MAIN_MENU_ITEM_NUM
};
  
Window *main_menu_window;
MenuLayer *main_menu_layer;
enum language_types language = LANGUAGE_ENGLISH;
char main_menu_item_text_array[MAIN_MENU_ITEM_NUM][MAIN_MENU_ITEM_STRING_LENGTH] = {};

void
main_menu_data_received(uint8_t packet_id, DictionaryIterator *iter)
{
  if (packet_id != MESSAGE_TYPE_GET_LANGUAGE_REPLY)
    return;
  
  //language = dict_find(iter, 1)->value->uint8;
}

static uint16_t
main_menu_layer_get_num_rows_callback(MenuLayer *menu_layer, uint16_t section_index, void *data)
{
  return MAIN_MENU_ITEM_NUM;
}

static void
main_menu_layer_draw_row_callback(GContext* ctx, const Layer *cell_layer, MenuIndex *cell_index, void *data)
{
  menu_cell_basic_draw(ctx, cell_layer, main_menu_item_text_array[cell_index->row], NULL, NULL);
}

void
main_menu_layer_select_callback(MenuLayer *menu_layer, MenuIndex *cell_index, void *data)
{
  switch(cell_index->row)
    {
      case MAIN_MENU_ITEM_NEARBY_STOPS:
          switch_window(WINDOW_TYPE_NEARBY_STOPS);
        break;
      case MAIN_MENU_ITEM_FAVORITES:
          switch_window(WINDOW_TYPE_FAVORITES);
        break;
      case MAIN_MENU_ITEM_SCHEDULES:
        break;
    };
}

void
fill_main_menu_item_text_array()
{
  switch (language)
    {
      case LANGUAGE_HUNGARIAN:
          strncpy(main_menu_item_text_array[MAIN_MENU_ITEM_NEARBY_STOPS], "Közeli megállók", MAIN_MENU_ITEM_STRING_LENGTH);
          strncpy(main_menu_item_text_array[MAIN_MENU_ITEM_FAVORITES], "Kedvencek", MAIN_MENU_ITEM_STRING_LENGTH);
          strncpy(main_menu_item_text_array[MAIN_MENU_ITEM_SCHEDULES], "Menetrendek", MAIN_MENU_ITEM_STRING_LENGTH);
        break;
      case LANGUAGE_ENGLISH:
          strncpy(main_menu_item_text_array[MAIN_MENU_ITEM_NEARBY_STOPS], "Nearby stops", MAIN_MENU_ITEM_STRING_LENGTH);
          strncpy(main_menu_item_text_array[MAIN_MENU_ITEM_FAVORITES], "Favorites", MAIN_MENU_ITEM_STRING_LENGTH);
          strncpy(main_menu_item_text_array[MAIN_MENU_ITEM_SCHEDULES], "Schedules", MAIN_MENU_ITEM_STRING_LENGTH);
       break;
    };
}

void
main_menu_window_appear(Window *window)
{
  set_current_window(WINDOW_TYPE_MAIN_MENU);
  
  DictionaryIterator *iterator;
  app_message_outbox_begin(&iterator);
  dict_write_uint8(iterator, 0, MESSAGE_TYPE_GET_LANGUAGE);
  app_message_outbox_send();
  
}

void
main_menu_window_unload(Window *window)
{
  menu_layer_destroy(main_menu_layer);
  window_destroy(main_menu_window);
}

void
main_menu_init()
{
  main_menu_window = window_create();

  window_set_window_handlers(main_menu_window, (WindowHandlers){
    .appear = main_menu_window_appear,
    .unload = main_menu_window_unload
});  
  
  Layer *window_layer = window_get_root_layer(main_menu_window);
  GRect bounds = layer_get_frame(window_layer);
  
  main_menu_layer = menu_layer_create(bounds);

  menu_layer_set_callbacks(main_menu_layer, NULL, (MenuLayerCallbacks){
    .get_num_rows = main_menu_layer_get_num_rows_callback,
    .draw_row = main_menu_layer_draw_row_callback,
    .select_click = main_menu_layer_select_callback,
  });

  menu_layer_set_click_config_onto_window(main_menu_layer, main_menu_window);
  
  layer_add_child(window_layer, menu_layer_get_layer(main_menu_layer));

  fill_main_menu_item_text_array();
  
  window_stack_push(main_menu_window, true);
}