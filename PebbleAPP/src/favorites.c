#include <main.h>

#define FAVORITES_NUM 15
  
#define LINE_NUMBER_STRING_LENGTH 5
#define STRING_LENGTH 35
  
Window *favorites_window;
MenuLayer *favorites_layer;

struct favorites_type {
  char line_number[LINE_NUMBER_STRING_LENGTH];
  char stop_name[STRING_LENGTH];
  char direction_name[STRING_LENGTH];
  char departure_times[STRING_LENGTH];
  bool usable;
};

static uint8_t favorites_array_size = 0;
static struct favorites_type favorites_array[FAVORITES_NUM];

void
clear_favorites_array()
{
  for (int i = 0; i < FAVORITES_NUM; i++)
    {
      favorites_array[i].line_number[0] = '\0';
      favorites_array[i].stop_name[0] = '\0';
      favorites_array[i].direction_name[0] = '\0';
      favorites_array[i].departure_times[0] = '\0';
      
      favorites_array[i].usable = false;
    }
  favorites_array_size = 0;
}

void
fill_favorites_array(int i, const char *line_number, const char *stop_name, const char *direction_name, const char *departure_times)
{
  if (i < 0 || i > FAVORITES_NUM - 1)
    return;
  
  strncpy(favorites_array[i].line_number, line_number, LINE_NUMBER_STRING_LENGTH - 1);
  strncpy(favorites_array[i].stop_name, stop_name, STRING_LENGTH - 1);
  strncpy(favorites_array[i].direction_name, direction_name, STRING_LENGTH - 1);
  //APP_LOG(APP_LOG_LEVEL_DEBUG, "directioname: %s", close_stops_array[i].direction_name);
  //APP_LOG(APP_LOG_LEVEL_DEBUG, "directioname: %s", direction_name);
  
  strncpy(favorites_array[i].departure_times, departure_times, STRING_LENGTH - 1);
  if (!favorites_array[i].usable)
    favorites_array_size++;
  
  favorites_array[i].usable = true;
}

void
favorites_data_received(uint8_t packet_id, DictionaryIterator *iter)
{
  
}

static uint16_t
favorites_layer_get_num_rows_callback(MenuLayer *menu_layer, uint16_t section_index, void *data)
{
  return favorites_array_size > 3 ? favorites_array_size : 3;
}

static void
favorites_layer_draw_row_callback(GContext* ctx, const Layer *cell_layer, MenuIndex *cell_index, void *data)
{
  graphics_context_set_text_color(ctx, GColorBlack);
  GRect bounds = layer_get_bounds(cell_layer);
  if (!favorites_array[cell_index->row].usable)
    {
      GRect line = (GRect) {.origin = (GPoint) {.x = bounds.origin.x + 2, .y = bounds.origin.y + 11}, .size = (GSize) {.h = 20, .w = 140 } };
      graphics_draw_text(ctx, "Loading...", fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD), line, GTextOverflowModeWordWrap, GTextAlignmentCenter, NULL);
      return;
    }
    
  GRect line_number_rect = (GRect) {.origin = (GPoint) {.x = bounds.origin.x + 1, .y = bounds.origin.y}, .size = (GSize) {.h = 16, .w = 27 } };
  //APP_LOG(APP_LOG_LEVEL_DEBUG, "directioname: %s", close_stops_array[cell_index->row].direction_name);
  graphics_draw_text(ctx, favorites_array[cell_index->row].line_number, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD), line_number_rect, GTextOverflowModeFill, GTextAlignmentLeft, NULL);
  GRect stop_name_rect = (GRect) {.origin = (GPoint) {.x = bounds.origin.x + 30, .y = bounds.origin.y}, .size = (GSize) {.h = 16, .w = 114 } };
  graphics_draw_text(ctx, favorites_array[cell_index->row].stop_name, fonts_get_system_font(FONT_KEY_GOTHIC_14), stop_name_rect, GTextOverflowModeFill, GTextAlignmentLeft, NULL);
  GRect direction_name_rect = (GRect) {.origin = (GPoint) {.x = bounds.origin.x + 30, .y = bounds.origin.y + 14}, .size = (GSize) {.h = 16, .w = 114 } };
  graphics_draw_text(ctx, favorites_array[cell_index->row].direction_name, fonts_get_system_font(FONT_KEY_GOTHIC_14), direction_name_rect, GTextOverflowModeFill, GTextAlignmentLeft, NULL);  
  GRect departure_times_rect = (GRect) {.origin = (GPoint) {.x = bounds.origin.x + 2, .y = bounds.origin.y + 28}, .size = (GSize) {.h = 20, .w = 136 } };
  graphics_draw_text(ctx, favorites_array[cell_index->row].departure_times, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD), departure_times_rect, GTextOverflowModeFill, GTextAlignmentLeft, NULL);   

}

void
favorites_layer_select_callback(MenuLayer *menu_layer, MenuIndex *cell_index, void *data)
{
  //send message with cell_index->row id
  //switch_window(WINDOW_TYPE_CLOSE_STOPS_DETAILS);
}

void
favorites_window_appear(Window *window)
{
  set_current_window(WINDOW_TYPE_FAVORITES);
  clear_favorites_array();
  
  fill_favorites_array(0, "153A", "Hauszman Alajos utca", "->Csonka János tér", "20:20 20:40 21:20 21:40 22:20");
  fill_favorites_array(1, "41", "Etele út / Fehérvári út", "->Kalotaszeg utca", "11:12 11:14 11:16 11:18 11:20");
  fill_favorites_array(2, "986", "Újbuda központ", "->Móricz Zsigmond körtér", "09:00 09:10 09:20 09:30 09:40");
  fill_favorites_array(3, "42", "Kosztolányi Dezső tér", "->Újbuda-központ", "12:00 13:00 14:00 15:00 16:00");
  fill_favorites_array(4, "M4", "Kosztolányi Dezső tér", "->Újbuda-központ", "01:00 02:02 03:03 04:04 05:05");
  
  menu_layer_reload_data(favorites_layer);
}

void
favorites_window_unload(Window *window)
{
  menu_layer_destroy(favorites_layer);
  window_destroy(favorites_window);
}
void
favorites_init()
{
  favorites_window = window_create();

  window_set_window_handlers(favorites_window, (WindowHandlers){
    .appear = favorites_window_appear,
    .unload = favorites_window_unload
});  
  
  Layer *window_layer = window_get_root_layer(favorites_window);
  GRect bounds = layer_get_frame(window_layer);
  
  favorites_layer = menu_layer_create(bounds);

  menu_layer_set_callbacks(favorites_layer, NULL, (MenuLayerCallbacks){
    .get_num_rows = favorites_layer_get_num_rows_callback,
    .draw_row = favorites_layer_draw_row_callback,
    .select_click = favorites_layer_select_callback,
  });

  menu_layer_set_click_config_onto_window(favorites_layer, favorites_window);
  
  layer_add_child(window_layer, menu_layer_get_layer(favorites_layer));
  
  clear_favorites_array();

  window_stack_push(favorites_window, true);
}