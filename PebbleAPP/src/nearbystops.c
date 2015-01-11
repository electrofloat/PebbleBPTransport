#include <main.h>

#define STRING_LENGTH 32
#define DISTANCE_STRING_LENGTH 5
#define NEARBY_STOPS_NUM 20
  
Window *nearby_stops_window;
MenuLayer *nearby_stops_layer;

struct nearby_stops_type {
  char stop_name[STRING_LENGTH];
  char direction_name[STRING_LENGTH];
  char line_numbers[STRING_LENGTH];
  char distance_text[DISTANCE_STRING_LENGTH];
  bool usable;
};

static uint8_t nearby_stops_array_size = 0;
static struct nearby_stops_type nearby_stops_array[NEARBY_STOPS_NUM];

void
clear_nearby_stops_array()
{
  for (int i = 0; i < NEARBY_STOPS_NUM; i++)
    {
      nearby_stops_array[i].stop_name[0] = '\0';
      nearby_stops_array[i].direction_name[0] = '\0';
      nearby_stops_array[i].line_numbers[0] = '\0';
      nearby_stops_array[i].distance_text[0] = '\0';
      nearby_stops_array[i].usable = false;
    }
  nearby_stops_array_size = 0;
}

void
fill_nearby_stops_array(int i, const char *stop_name, const char *direction_name, const char *line_numbers, const char *distance_text)
{
  if (i < 0 || i > NEARBY_STOPS_NUM - 1)
    return;
  
  strncpy(nearby_stops_array[i].stop_name, stop_name, STRING_LENGTH - 1);
  strncpy(nearby_stops_array[i].direction_name, direction_name, STRING_LENGTH - 1);
  strncpy(nearby_stops_array[i].line_numbers, line_numbers, STRING_LENGTH - 1);
  strncpy(nearby_stops_array[i].distance_text, distance_text, DISTANCE_STRING_LENGTH - 1);
  if (!nearby_stops_array[i].usable)
    nearby_stops_array_size++;
  
  nearby_stops_array[i].usable = true;
}

void
nearby_stops_data_received(uint8_t packet_id, DictionaryIterator *iter)
{
  if (packet_id != MESSAGE_TYPE_GET_NEARBY_STOPS_REPLY)
    return;
  
  uint8_t id = dict_find(iter, 1)->value->uint8;
  const char *stop_name = dict_find(iter, 2)->value->cstring;
  const char *direction_name = dict_find(iter, 3)->value->cstring;
  const char *line_numbers = dict_find(iter, 4)->value->cstring;
  const char *distance = dict_find(iter, 5)->value->cstring;
  
  fill_nearby_stops_array(id, stop_name, direction_name, line_numbers, distance);
  
  menu_layer_reload_data(nearby_stops_layer);
}

static uint16_t
nearby_stops_layer_get_num_rows_callback(MenuLayer *menu_layer, uint16_t section_index, void *data)
{
  return nearby_stops_array_size > 3 ? nearby_stops_array_size : 3;
}

static void
nearby_stops_layer_draw_row_callback(GContext* ctx, const Layer *cell_layer, MenuIndex *cell_index, void *data)
{
  graphics_context_set_text_color(ctx, GColorBlack);
  GRect bounds = layer_get_bounds(cell_layer);
  if (!nearby_stops_array[cell_index->row].usable)
    {
      GRect line = (GRect) {.origin = (GPoint) {.x = bounds.origin.x + 2, .y = bounds.origin.y + 11}, .size = (GSize) {.h = 20, .w = 140 } };
      graphics_draw_text(ctx, "Loading...", fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD), line, GTextOverflowModeWordWrap, GTextAlignmentCenter, NULL);
      return;
    }
    
  GRect first_line = (GRect) {.origin = bounds.origin, .size = (GSize) {.h = 20, .w = 140 } };
  graphics_draw_text(ctx, nearby_stops_array[cell_index->row].stop_name, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD), first_line, GTextOverflowModeFill, GTextAlignmentLeft, NULL);
  GRect second_line = (GRect) {.origin = (GPoint) {.x = bounds.origin.x, .y = bounds.origin.y + 13}, .size = (GSize) {.h = 20, .w = 144 } };
  graphics_draw_text(ctx, nearby_stops_array[cell_index->row].direction_name, fonts_get_system_font(FONT_KEY_GOTHIC_14), second_line, GTextOverflowModeFill, GTextAlignmentLeft, NULL);
  GRect third_line = (GRect) {.origin = (GPoint) {.x = bounds.origin.x, .y = bounds.origin.y + 27}, .size = (GSize) {.h = 20, .w = 114 } };
   
  graphics_draw_text(ctx, nearby_stops_array[cell_index->row].line_numbers, fonts_get_system_font(FONT_KEY_GOTHIC_14), third_line, GTextOverflowModeFill, GTextAlignmentLeft, NULL);  
  GRect third_line_meter = (GRect) {.origin = (GPoint) {.x = bounds.origin.x + 115, .y = bounds.origin.y + 27}, .size = (GSize) {.h = 20, .w = 29 } };

  graphics_draw_text(ctx, nearby_stops_array[cell_index->row].distance_text, fonts_get_system_font(FONT_KEY_GOTHIC_14), third_line_meter, GTextOverflowModeFill, GTextAlignmentLeft, NULL);   
}

void
nearby_stops_layer_select_callback(MenuLayer *menu_layer, MenuIndex *cell_index, void *data)
{
  //send message with cell_index->row id
  if (!nearby_stops_array[cell_index->row].usable)
    return;
  
  switch_window(WINDOW_TYPE_NEARBY_STOPS_DETAILS);
  
  DictionaryIterator *iterator;
  app_message_outbox_begin(&iterator);
  dict_write_uint8(iterator, 0, MESSAGE_TYPE_GET_NEARBY_STOPS_DETAILS);
  dict_write_uint8(iterator, 1, (uint8_t) cell_index->row);
  app_message_outbox_send();
  
}

void
nearby_stops_window_appear(Window *window)
{
  set_current_window(WINDOW_TYPE_NEARBY_STOPS);
  clear_nearby_stops_array();
  
  DictionaryIterator *iterator;
  app_message_outbox_begin(&iterator);
  dict_write_uint8(iterator, 0, MESSAGE_TYPE_GET_NEARBY_STOPS);
  app_message_outbox_send();
  
  /*fill_nearby_stops_array(0, "Hauszman Alajos utca", "Csonka János tér felé", "18,41,47,48", "383m");
  fill_nearby_stops_array(1, "Etele út / Fehérvári út", "Kalotaszeg utca felé", "114,213,214", "684m");
  fill_nearby_stops_array(2, "Újbuda központ", "Móricz Zsigmond körtér felé", "M4,112,113,114", "778m");
  fill_nearby_stops_array(3, "Kosztolányi Dezső tér", "Újbuda-központ felé", "53,86,150,153,153A,154,212,258,258A,918", "896m");
  fill_nearby_stops_array(4, "Kosztolányi Dezső tér", "Újbuda-központ felé", "53,86,150,153,153A,154,212,258,258A,918", "896m");*/
  
  //menu_layer_reload_data(nearby_stops_layer);
  
}

void
nearby_stops_window_unload(Window *window)
{
  menu_layer_destroy(nearby_stops_layer);
  window_destroy(nearby_stops_window);
}
void
nearby_stops_init()
{
  nearby_stops_window = window_create();

  window_set_window_handlers(nearby_stops_window, (WindowHandlers){
    .appear = nearby_stops_window_appear,
    .unload = nearby_stops_window_unload
});  
  
  Layer *window_layer = window_get_root_layer(nearby_stops_window);
  GRect bounds = layer_get_frame(window_layer);
  
  nearby_stops_layer = menu_layer_create(bounds);

  menu_layer_set_callbacks(nearby_stops_layer, NULL, (MenuLayerCallbacks){
    .get_num_rows = nearby_stops_layer_get_num_rows_callback,
    .draw_row = nearby_stops_layer_draw_row_callback,
    .select_click = nearby_stops_layer_select_callback,
  });

  menu_layer_set_click_config_onto_window(nearby_stops_layer, nearby_stops_window);
  
  layer_add_child(window_layer, menu_layer_get_layer(nearby_stops_layer));
  
  clear_nearby_stops_array();

  window_stack_push(nearby_stops_window, true);
}