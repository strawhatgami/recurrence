package com.bleyl.recurrence.database;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;

import java.util.HashMap;

public class CalendarHelper{
  public HashMap<String, String> calendarsHashMap(Context context) {
    final String[] EVENT_PROJECTION = new String[]{
        Calendars._ID,
        Calendars.CALENDAR_DISPLAY_NAME
    };

    final ContentResolver cr = context.getContentResolver();
    final Uri uri = Calendars.CONTENT_URI;

    HashMap<String, String> calendars = new HashMap<>();


    Cursor cursor;
    try {
      cursor = cr.query(uri, EVENT_PROJECTION, null, null, null);
    } catch (SecurityException e) {
      return calendars;
    }

    if (cursor.moveToFirst()) {
      do {
        String name = cursor.getString(cursor.getColumnIndex(Calendars.CALENDAR_DISPLAY_NAME));
        String id = cursor.getString(cursor.getColumnIndex(Calendars._ID));

        calendars.put(name, id);
      } while (cursor.moveToNext());
    }
    cursor.close();

    return calendars;
  }
}

