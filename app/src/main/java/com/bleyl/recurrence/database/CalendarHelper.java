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

  private static Reminder getReminderFromEventDB(Gson gson, Context context,  Cursor cursor) {
    /* Fields non widely implemented as SYNC_DATAx, or complex to handle as RRULE, are put in a
     * widely implemented (but not used by Recurrence) field (COL_BLOB) as a serialized JSON object.
     * We go that way to ensure compatibility with a wide variety of sync tools (Exchange, Nextcloud, ...).
     * TODO future improvement: put some of the fields in an RRULE, maybe with https://github.com/mangstadt/biweekly ?
     */
    DateFormat formatter = new SimpleDateFormat("yyyyMMddHHmm");

    int syncId = cursor.getInt(cursor.getColumnIndexOrThrow(Events.ORIGINAL_ID));
    String title = cursor.getString(cursor.getColumnIndexOrThrow(Events.TITLE));
    String content = cursor.getString(cursor.getColumnIndexOrThrow(Events.DESCRIPTION));
    String dateAndTime;
    String color;
    int intColor = cursor.getInt(cursor.getColumnIndexOrThrow(Events.EVENT_COLOR));
    long longDateAndTime = cursor.getLong(cursor.getColumnIndexOrThrow(Events.DTSTART));

    if (cursor.getString(cursor.getColumnIndexOrThrow(Events.ORIGINAL_ID)) == null){
      syncId = cursor.getInt(cursor.getColumnIndexOrThrow(Events._ID));
    }
    if (title == null) title = "";
    if (content == null) content = "";
    if (longDateAndTime == 0) dateAndTime = DateAndTimeUtil.toStringDateAndTime(Calendar.getInstance());
    else {
      dateAndTime = formatter.format(longDateAndTime);
    }
    if (intColor == 0) color = context.getString(R.string.default_colour_value);
    else color = String.format("#%06X", (0xFFFFFF & intColor));

    String jsonStr = cursor.getString(cursor.getColumnIndexOrThrow(COL_BLOB));
    String icon = context.getString(R.string.default_icon_value);
    String foreverState = "false";
    int repeatType = Reminder.DOES_NOT_REPEAT;
    int numberToShow = Reminder.DEFAULT_TIMES_TO_SHOW;
    int numberShown = Reminder.DEFAULT_TIMES_SHOWN;
    int interval = Reminder.DEFAULT_INTERVAL;
    boolean[] daysOfWeek = new boolean[7];

    Map<String, String> vendorFields = null;
    try {
      vendorFields = gson.fromJson(jsonStr, Map.class);
    } catch (com.google.gson.JsonSyntaxException ignored) {
    }

    if (vendorFields != null){
      if (vendorFields.get("icon") != null) icon = vendorFields.get("icon");
      if (vendorFields.get("foreverState") != null) foreverState = vendorFields.get("foreverState");
      if (vendorFields.get("repeatType") != null && Integer.parseInt(vendorFields.get("repeatType")) > 0) repeatType = Integer.parseInt(vendorFields.get("repeatType"));
      if (vendorFields.get("numberToShow") != null && Integer.parseInt(vendorFields.get("numberToShow")) > 0) numberToShow = Integer.parseInt(vendorFields.get("numberToShow"));
      if (vendorFields.get("numberShown") != null && Integer.parseInt(vendorFields.get("numberShown")) > 0) numberShown = Integer.parseInt(vendorFields.get("numberShown"));
      if (vendorFields.get("interval") != null && Integer.parseInt(vendorFields.get("interval")) > 0) interval = Integer.parseInt(vendorFields.get("interval"));

      String daysOfWeekStr = vendorFields.get("daysOfWeek");
      try {
        daysOfWeek = gson.fromJson(daysOfWeekStr, boolean[].class);
      } catch (Exception ignored) {
      }
    }

    Reminder reminder = new Reminder()
        .setSyncId(syncId)
        .setTitle(title)
        .setContent(content)
        .setDateAndTime(dateAndTime)
        .setColour(color)
        .setIcon(icon)
        .setForeverState(foreverState)
        .setNumberToShow(numberToShow)
        .setNumberShown(numberShown)
        .setRepeatType(repeatType)
        .setInterval(interval)
        .setDaysOfWeek(daysOfWeek);

    return reminder;
  }

  private static List<Reminder> getRemindersFromCalendar(Context context, String calendarId) {
    List<Reminder> reminders = new ArrayList<>();

    final Cursor cursor;
    final ContentResolver cr = context.getContentResolver();
    final Gson gson = new Gson();
    final DatabaseHelper database = DatabaseHelper.getInstance(context);

    String[] mProjection = {
        Events._ID,
        Events.ORIGINAL_ID,
        Events.TITLE,
        Events.DESCRIPTION,
        Events.DTSTART,
        Events.EVENT_COLOR,
        COL_BLOB,
    };

    Uri uri = Events.CONTENT_URI;
    String selection = Events.CALENDAR_ID + " = ? AND "
        + Events.ORIGINAL_ID + " IS NOT NULL AND "
        + Events.TITLE + " IS NOT NULL AND "
        + COL_BLOB + " IS NOT NULL";

    String[] selectionArgs = new String[]{calendarId};

    try {
      cursor = cr.query(uri, mProjection, selection, selectionArgs, null);
    } catch (SecurityException e) {
      return reminders;
    }

    if (cursor.moveToFirst()) {
      do {
        Reminder reminder = getReminderFromEventDB(gson, context, cursor);
        reminder.setId(database.getIdFromSyncId(reminder.getSyncId()));
        reminders.add(reminder);
      } while (cursor.moveToNext());
    }
    cursor.close();

    return reminders;
  }

  public static void restoreFromCalendar(Context context, String calendarId) {
    DatabaseHelper database = DatabaseHelper.getInstance(context);

    List<Reminder> reminders = getRemindersFromCalendar(context, calendarId);
    for (Reminder reminder : reminders){
      int id = database.getIdFromSyncId(reminder.getSyncId());
      if (id == Reminder.DEFAULT_ID) {
        // There is no reminder with this sync id in db, create an id for it
        id = database.getLastReminderId() + 1;
      }
      reminder.setId(id);

      database.addNotification(reminder);
      if (reminder.getRepeatType() == Reminder.SPECIFIC_DAYS) {
        database.addDaysOfWeek(reminder);
      }

      AlarmUtil.setAlarm(context, reminder);
    }

    database.close();
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

