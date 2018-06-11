package com.bleyl.recurrence.database;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;

import com.bleyl.recurrence.R;
import com.bleyl.recurrence.models.Reminder;
import com.bleyl.recurrence.utils.AlarmUtil;
import com.bleyl.recurrence.utils.DateAndTimeUtil;
import com.google.gson.Gson;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class CalendarHelper{
  private static String COL_BLOB = Events.EVENT_LOCATION;

  public static int getSyncId(Context context) {
    // The reminder is a new one, let's get an id for it. Values will be filled after.
    Uri createdRow;
    try {
      createdRow = context.getContentResolver().insert(Events.CONTENT_URI, new ContentValues());
    } catch (SecurityException ignored) {
      return Reminder.DEFAULT_ID;
    }

    if (createdRow == null){
      return Reminder.DEFAULT_ID;
    }

    return (int) ContentUris.parseId(createdRow);
  }

  @SuppressLint("MissingPermission") // permission is asked by the caller
  public static int syncUpdatedReminderInCalendar(Context context, Reminder reminder) {
    final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    final String calendarId = sharedPreferences.getString("listSyncCalendar", "");

    final Gson gson = new Gson();
    final ContentResolver cr = context.getContentResolver();
    final DateFormat formatter = new SimpleDateFormat("yyyyMMddHHmm");
    ContentValues values = new ContentValues();

    // android event DTSTART format: timestamp in ms (not ICal DTSTART)
    String strDate = reminder.getDateAndTime();
    long dtstart;
    try {
      dtstart = formatter.parse(strDate).getTime();
    } catch (ParseException ex){
      System.out.println(ex.toString());
      return reminder.getSyncId();
    }

    long color = Color.parseColor(reminder.getColour()); // add opacity

    Map<String, String> vendorFields = new HashMap<>();
    vendorFields.put("icon", reminder.getIcon());
    vendorFields.put("repeatType", String.valueOf(reminder.getRepeatType()));
    vendorFields.put("numberToShow", String.valueOf(reminder.getNumberToShow()));
    vendorFields.put("numberShown", String.valueOf(reminder.getNumberShown()));
    vendorFields.put("interval", String.valueOf(reminder.getInterval()));
    vendorFields.put("foreverState", reminder.getForeverState());
    vendorFields.put("daysOfWeek", gson.toJson(reminder.getDaysOfWeek()));

    values.put(Events.ORIGINAL_ID, reminder.getId());
    values.put(Events.CALENDAR_ID, calendarId);
    values.put(Events.TITLE, reminder.getTitle());
    values.put(Events.DESCRIPTION, reminder.getContent());
    values.put(Events.DTSTART, dtstart);
    values.put(Events.DTEND, dtstart); // mandatory field
    values.put(Events.EVENT_COLOR, color);
    values.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
    values.put(COL_BLOB, gson.toJson(vendorFields));


    if (reminder.getSyncId() == Reminder.DEFAULT_ID) {
      // The reminder is a new one, let's get an id for it. Values will be filled after.
      // This must be called before database.addReminder() because it changes the reminder id
      Uri resultUri = cr.insert(Events.CONTENT_URI, values);

      if (resultUri == null) return reminder.getSyncId();

      return (int) ContentUris.parseId(resultUri);
    } else {
      values.put(Events.ORIGINAL_ID, reminder.getId());
      Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, reminder.getSyncId());
      cr.update(uri, values, null, null);
      return reminder.getSyncId();
    }
  }

  @SuppressLint("MissingPermission") // permission is asked by the caller
  public static void syncReminderDeletionInCalendar(Context context, Reminder reminder) {
    final ContentResolver cr = context.getContentResolver();

    if (reminder.getSyncId() == Reminder.DEFAULT_ID) return;

    Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, reminder.getSyncId());
    cr.delete(uri,null, null);
  }

  public HashMap<String, String> getCalendarsList(Context context) {
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

