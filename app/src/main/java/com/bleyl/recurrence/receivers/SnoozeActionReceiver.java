package com.bleyl.recurrence.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

import com.bleyl.recurrence.R;
import com.bleyl.recurrence.activities.SnoozeDialogActivity;
import com.bleyl.recurrence.utils.AlarmUtil;
import com.bleyl.recurrence.utils.NotificationUtil;

import java.util.Calendar;

public class SnoozeActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int reminderId = intent.getIntExtra("NOTIFICATION_ID", 0);

        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("checkBoxNagging", false)) {
            Intent alarmIntent = new Intent(context, NagReceiver.class);
            AlarmUtil.cancelAlarm(context, alarmIntent, reminderId);
        }

        // Close notification tray
        Intent closeIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        context.sendBroadcast(closeIntent);

        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("checkBoxHideSnoozeActivity", false)) {
            int nagMinutes = PreferenceManager.getDefaultSharedPreferences(context)
                .getInt("nagMinutes", context.getResources().getInteger(R.integer.default_nag_minutes));
            int nagSeconds = PreferenceManager.getDefaultSharedPreferences(context)
                .getInt("nagSeconds", context.getResources().getInteger(R.integer.default_nag_seconds));

            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.SECOND, nagSeconds);
            calendar.add(Calendar.MINUTE, nagMinutes);
            Intent alarmIntent = new Intent(context.getApplicationContext(), SnoozeReceiver.class);
            AlarmUtil.setAlarm(context.getApplicationContext(), alarmIntent, reminderId, calendar);
            NotificationUtil.cancelNotification(context.getApplicationContext(), reminderId);
        } else {
            Intent snoozeIntent = new Intent(context, SnoozeDialogActivity.class);
            snoozeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            snoozeIntent.putExtra("NOTIFICATION_ID", reminderId);
            context.startActivity(snoozeIntent);
        }
    }
}