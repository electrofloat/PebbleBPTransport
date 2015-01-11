#include <main.h>

#define NEARBY_STOPS_DETAILS_LAYER_CELL_HEIGHT 35
  
#define STRING_LENGTH 32
#define LINE_NUMBER_STRING_LENGTH 5
#define NEARBY_STOPS_DETAILS_NUM 20
  
Window *nearby_stops_details_window;
MenuLayer *nearby_stops_details_layer;

struct nearby_stops_details_type {
  char line_number[LINE_NUMBER_STRING_LENGTH];
  char direction_name[STRING_LENGTH];
  int start_time_in_seconds;
  int predicted_start_time_in_seconds;
  char start_time[STRING_LENGTH];
  char remaining_time[STRING_LENGTH];
  bool usable;
};

static uint8_t nearby_stops_details_array_size = 0;
static struct nearby_stops_details_type nearby_stops_details_array[NEARBY_STOPS_DETAILS_NUM];

void
clear_nearby_stops_details_array()
{
  for (int i = 0; i < NEARBY_STOPS_DETAILS_NUM; i++)
    {
      nearby_stops_details_array[i].line_number[0] = '\0';
      nearby_stops_details_array[i].direction_name[0] = '\0';
      nearby_stops_details_array[i].start_time_in_seconds = 0;
      nearby_stops_details_array[i].predicted_start_time_in_seconds = 0;
      nearby_stops_details_array[i].start_time[0] = '\0';
      nearby_stops_details_array[i].remaining_time[0] = '\0';
      nearby_stops_details_array[i].usable = false;
    }
  nearby_stops_details_array_size = 0;
}

void
fill_nearby_stops_details_array(int i, const char *line_number, const char *direction_name, int start_time_in_seconds, int predicted_start_time_in_seconds)
{
  if (i < 0 || i > NEARBY_STOPS_DETAILS_NUM - 1)
    return;
  
  strncpy(nearby_stops_details_array[i].line_number, line_number, LINE_NUMBER_STRING_LENGTH - 1);
  strncpy(nearby_stops_details_array[i].direction_name, direction_name, STRING_LENGTH - 1);
  //APP_LOG(APP_LOG_LEVEL_DEBUG, "directioname: %s", nearby_stops_array[i].direction_name);
  //APP_LOG(APP_LOG_LEVEL_DEBUG, "directioname: %s", direction_name);
  nearby_stops_details_array[i].start_time_in_seconds = start_time_in_seconds;
  nearby_stops_details_array[i].predicted_start_time_in_seconds = predicted_start_time_in_seconds;
  
  int hour = start_time_in_seconds / 3600;
  int minute = (start_time_in_seconds / 60) % 60;
  
  snprintf(nearby_stops_details_array[i].start_time, STRING_LENGTH - 1, "%02d:%02d", hour, minute);
  
  if (!nearby_stops_details_array[i].usable)
    nearby_stops_details_array_size++;
  
  nearby_stops_details_array[i].usable = true;
}

void
nearby_stops_details_data_received(uint8_t packet_id, DictionaryIterator *iter)
{
  if (packet_id != MESSAGE_TYPE_GET_NEARBY_STOPS_DETAILS_REPLY)
    return;
  
  uint8_t id = dict_find(iter, 1)->value->uint8;
  const char *line_number = dict_find(iter, 2)->value->cstring;
  const char *direction_name = dict_find(iter, 3)->value->cstring;
  uint32_t start_time_in_seconds = dict_find(iter, 4)->value->uint32;
  uint32_t predicted_start_time_in_seconds = dict_find(iter, 5)->value->uint32;
 
  
  fill_nearby_stops_details_array(id, line_number, direction_name, start_time_in_seconds, predicted_start_time_in_seconds);
  
  menu_layer_reload_data(nearby_stops_details_layer);
}

static uint16_t
nearby_stops_details_layer_get_num_rows_callback(MenuLayer *menu_layer, uint16_t section_index, void *data)
{
  return nearby_stops_details_array_size > 3 ? nearby_stops_details_array_size : 3;
}

static void
nearby_stops_details_layer_draw_row_callback(GContext* ctx, const Layer *cell_layer, MenuIndex *cell_index, void *data)
{
  graphics_context_set_text_color(ctx, GColorBlack);
  GRect bounds = layer_get_bounds(cell_layer);
  if (!nearby_stops_details_array[cell_index->row].usable)
    {
      GRect line = (GRect) {.origin = (GPoint) {.x = bounds.origin.x + 2, .y = bounds.origin.y + 11}, .size = (GSize) {.h = 20, .w = 140 } };
      graphics_draw_text(ctx, "Loading...", fonts_get_system_font(FONT_KEY_GOTHIC_18_BOLD), line, GTextOverflowModeWordWrap, GTextAlignmentCenter, NULL);
      return;
    }
  
  GRect line_number_rect = (GRect) {.origin = (GPoint) {.x = bounds.origin.x + 1, .y = bounds.origin.y}, .size = (GSize) {.h = 16, .w = 30 } };
  graphics_draw_text(ctx, nearby_stops_details_array[cell_index->row].line_number, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD), line_number_rect, GTextOverflowModeFill, GTextAlignmentLeft, NULL);
  GRect direction_rect = (GRect) {.origin = (GPoint) {.x = bounds.origin.x + 1, .y = bounds.origin.y + 17}, .size = (GSize) {.h = 16, .w = 144 } };
  graphics_draw_text(ctx, nearby_stops_details_array[cell_index->row].direction_name, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD), direction_rect, GTextOverflowModeFill, GTextAlignmentLeft, NULL);
  GRect start_time_rect = (GRect) {.origin = (GPoint) {.x = bounds.origin.x + 43, .y = bounds.origin.y}, .size = (GSize) {.h = 16, .w = 30 } };
  graphics_draw_text(ctx, nearby_stops_details_array[cell_index->row].start_time, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD), start_time_rect, GTextOverflowModeFill, GTextAlignmentRight, NULL);
  GRect remaining_time_rect = (GRect) {.origin = (GPoint) {.x = bounds.origin.x + 86, .y = bounds.origin.y}, .size = (GSize) {.h = 16, .w = 54 } };  
  graphics_draw_text(ctx, nearby_stops_details_array[cell_index->row].remaining_time, fonts_get_system_font(FONT_KEY_GOTHIC_14_BOLD), remaining_time_rect, GTextOverflowModeFill, GTextAlignmentRight, NULL);
}

static int16_t
nearby_stops_details_layer_get_cell_height_callback(struct MenuLayer *menu_layer, MenuIndex *cell_index, void *callback_context)
{
  return NEARBY_STOPS_DETAILS_LAYER_CELL_HEIGHT;  
}

void
update_remaining_time(int current_seconds)
{
  char data[4];
  
  for (int i = 0; i < NEARBY_STOPS_DETAILS_NUM; i++)
    {
      if (!nearby_stops_details_array[i].usable)
        continue;
    
      nearby_stops_details_array[i].remaining_time[0] = '\0';
      int start_time = nearby_stops_details_array[i].start_time_in_seconds;
      if (nearby_stops_details_array[i].predicted_start_time_in_seconds != 0)
        start_time = nearby_stops_details_array[i].predicted_start_time_in_seconds;
      int seconds = current_seconds - start_time;
    
      if (seconds < 0)
        {
          snprintf(data, 4, "-");
          seconds *= -1;
        }
      else
        snprintf(data, 4, "+");
      strcat(nearby_stops_details_array[i].remaining_time, data);
  
      int hour = seconds / 3600;
      int minute = (seconds / 60) % 60;
      int second = seconds % 60;
      snprintf(data, 4, "%02d:", hour);
      strcat(nearby_stops_details_array[i].remaining_time, data);
      snprintf(data, 4, "%02d:", minute);
      strcat(nearby_stops_details_array[i].remaining_time, data);
      snprintf(data, 4, "%02d", second);
      strcat(nearby_stops_details_array[i].remaining_time, data);
      //APP_LOG(APP_LOG_LEVEL_DEBUG, "remainngtime: %s", nearby_stops_details_array[i].remaining_time);
    }
}

static void
nearby_stops_details_tick_handler(struct tm *tick_time, TimeUnits units_changed)
{
   int current_seconds = tick_time->tm_hour * 3600 + tick_time->tm_min * 60 + tick_time->tm_sec;
   update_remaining_time(current_seconds);
   
   layer_mark_dirty(menu_layer_get_layer(nearby_stops_details_layer));
}

void
nearby_stops_details_window_load(Window *window)
{
  APP_LOG(APP_LOG_LEVEL_DEBUG, "details load");
}
void
nearby_stops_details_window_appear(Window *window)
{
  APP_LOG(APP_LOG_LEVEL_DEBUG, "details appear");
  set_current_window(WINDOW_TYPE_NEARBY_STOPS_DETAILS);
  tick_timer_service_subscribe(SECOND_UNIT, nearby_stops_details_tick_handler);
  
  clear_nearby_stops_details_array();
  
  /*fill_nearby_stops_details_array(0, "153A", "->Csonka János tér", 62460);
  fill_nearby_stops_details_array(1, "41", "->Kalotaszeg utca", 62500);
  fill_nearby_stops_details_array(2, "48", "->Móricz Zsigmond körtér", 63000);
  fill_nearby_stops_details_array(3, "18", "->Újbuda-központ", 64123);
  fill_nearby_stops_details_array(4, "19", "->Kelenföld vasútállomás M", 65527);
  
  menu_layer_reload_data(nearby_stops_details_layer);*/
  
}

void
nearby_stops_details_window_disappear(Window *window)
{
  APP_LOG(APP_LOG_LEVEL_DEBUG, "details disappear");
  tick_timer_service_unsubscribe();
}

void
nearby_stops_details_window_unload(Window *window)
{
  APP_LOG(APP_LOG_LEVEL_DEBUG, "details unload");
  
  menu_layer_destroy(nearby_stops_details_layer);
  window_destroy(nearby_stops_details_window);
}
void
nearby_stops_details_init()
{
  nearby_stops_details_window = window_create();

  window_set_window_handlers(nearby_stops_details_window, (WindowHandlers){
    .load = nearby_stops_details_window_load,
    .appear = nearby_stops_details_window_appear,
    .disappear = nearby_stops_details_window_disappear,
    .unload = nearby_stops_details_window_unload
});  
  
  Layer *window_layer = window_get_root_layer(nearby_stops_details_window);
  GRect bounds = layer_get_frame(window_layer);
  
  nearby_stops_details_layer = menu_layer_create(bounds);

  menu_layer_set_callbacks(nearby_stops_details_layer, NULL, (MenuLayerCallbacks){
    .get_num_rows = nearby_stops_details_layer_get_num_rows_callback,
    .draw_row = nearby_stops_details_layer_draw_row_callback,
    .get_cell_height = nearby_stops_details_layer_get_cell_height_callback,
    //.select_click = nearby_stops_details_layer_select_callback,
  });

  menu_layer_set_click_config_onto_window(nearby_stops_details_layer, nearby_stops_details_window);
  
  layer_add_child(window_layer, menu_layer_get_layer(nearby_stops_details_layer));
  
  clear_nearby_stops_details_array();

  window_stack_push(nearby_stops_details_window, true);
}