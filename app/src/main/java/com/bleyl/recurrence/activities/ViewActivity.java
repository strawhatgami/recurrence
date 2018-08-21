package com.bleyl.recurrence.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bleyl.recurrence.database.DatabaseHelper;
import com.bleyl.recurrence.models.Reminder;
import com.bleyl.recurrence.R;
import com.bleyl.recurrence.receivers.AlarmReceiver;
import com.bleyl.recurrence.receivers.DismissReceiver;
import com.bleyl.recurrence.receivers.SnoozeActionReceiver;
import com.bleyl.recurrence.receivers.SnoozeReceiver;
import com.bleyl.recurrence.utils.AlarmUtil;
import com.bleyl.recurrence.utils.DateAndTimeUtil;
import com.bleyl.recurrence.utils.NotificationUtil;
import com.bleyl.recurrence.utils.TextFormatUtil;

import java.util.Calendar;

import butterknife.BindView;
import butterknife.ButterKnife;

public class ViewActivity extends AppCompatActivity {

    @BindView(R.id.reminder_title) TextView reminderTitleText;
    @BindView(R.id.reminder_time) TextView reminderTimeText;
    @BindView(R.id.reminder_content) TextView contentText;
    @BindView(R.id.reminder_icon) ImageView iconImage;
    @BindView(R.id.reminder_circle) ImageView circleImage;
    @BindView(R.id.time) TextView timeText;
    @BindView(R.id.date) TextView dateText;
    @BindView(R.id.repeat) TextView repeatText;
    @BindView(R.id.shown) TextView shownText;
    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.detail_layout) LinearLayout linearLayout;
    @BindView(R.id.toolbar_shadow) View shadowView;
    @BindView(R.id.header) View headerView;
    @BindView(R.id.view_coordinator) CoordinatorLayout coordinatorLayout;

    private Reminder reminder;
    private boolean hideMarkAsDone;
    private boolean hideSnooze = true;
    private boolean reminderChanged;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view);
        ButterKnife.bind(this);
        setupTransitions();

        setSupportActionBar(toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(null);

        // Add drawable shadow and adjust layout if build version is before lollipop
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            linearLayout.setPadding(0, 0, 0, 0);
            shadowView.setVisibility(View.VISIBLE);
        } else {
            ViewCompat.setElevation(headerView, getResources().getDimension(R.dimen.toolbar_elevation));
        }

        DatabaseHelper database = DatabaseHelper.getInstance(this);
        Intent intent = getIntent();
        int mReminderId = intent.getIntExtra("REMINDER_ID", 0);

        // Arrived to activity from reminder on click
        // Cancel reminder and nag alarm
        if (intent.getBooleanExtra("NOTIFICATION_DISMISS", false)) {
            Intent dismissIntent = new Intent().setClass(this, DismissReceiver.class);
            dismissIntent.putExtra("REMINDER_ID", mReminderId);
            sendBroadcast(dismissIntent);

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            Boolean snooze = sharedPreferences.getBoolean("checkBoxSnooze", false);
            hideSnooze = !snooze;
        }

        if (database.isReminderPresent(mReminderId)) {
            reminder = database.getReminder(mReminderId);
            database.close();
        } else {
            database.close();
            returnHome();
        }
    }

    private void assignReminderValues() {
        Calendar calendar = DateAndTimeUtil.parseDateAndTime(reminder.getDateAndTime());
        reminderTitleText.setText(reminder.getTitle());
        contentText.setText(reminder.getContent());
        dateText.setText(DateAndTimeUtil.toStringReadableDate(calendar));
        iconImage.setImageResource(getResources().getIdentifier(reminder.getIcon(), "drawable", getPackageName()));
        circleImage.setColorFilter(Color.parseColor(reminder.getColour()));
        String readableTime = DateAndTimeUtil.toStringReadableTime(calendar, this);
        timeText.setText(readableTime);
        reminderTimeText.setText(readableTime);

        if (reminder.getRepeatType() == Reminder.SPECIFIC_DAYS) {
            repeatText.setText(TextFormatUtil.formatDaysOfWeekText(this, reminder.getDaysOfWeek()));
        } else {
            if (reminder.getInterval() > 1) {
                repeatText.setText(TextFormatUtil.formatAdvancedRepeatText(this, reminder.getRepeatType(), reminder.getInterval()));
            } else {
                repeatText.setText(getResources().getStringArray(R.array.repeat_array)[reminder.getRepeatType()]);
            }
        }

        if (Boolean.parseBoolean(reminder.getForeverState())) {
            shownText.setText(R.string.forever);
        } else {
            shownText.setText(getString(R.string.times_shown, reminder.getNumberShown(), reminder.getNumberToShow()));
        }

        // Hide "Mark as done" action if reminder is inactive
        hideMarkAsDone = reminder.getNumberToShow() <= reminder.getNumberShown() && !Boolean.parseBoolean(reminder.getForeverState());
        invalidateOptionsMenu();
    }

    private void setupTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Transition enter = TransitionInflater.from(this).inflateTransition(R.transition.view_enter);
            Transition exit = TransitionInflater.from(this).inflateTransition(R.transition.view_exit);
            getWindow().setEnterTransition(enter);
            getWindow().setReturnTransition(exit);
        }
    }

    private void actionSnooze() {
        Intent snoozeIntent = new Intent(this, SnoozeActionReceiver.class);
        snoozeIntent.putExtra("REMINDER_ID", reminder.getId());
        this.sendBroadcast(snoozeIntent);
        finish();
    }

    public void confirmDelete() {
        new AlertDialog.Builder(this, R.style.Dialog)
                .setMessage(R.string.delete_confirmation)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        actionDelete();
                    }
                })
                .setNegativeButton(R.string.no, null).show();
    }

    private void actionShowNow() {
        NotificationUtil.createNotification(this, reminder, true);
    }

    private void actionDelete() {
        DatabaseHelper database = DatabaseHelper.getInstance(this);
        database.deleteReminder(reminder);
        database.close();
        Intent alarmIntent = new Intent(this, AlarmReceiver.class);
        AlarmUtil.cancelAlarm(this, alarmIntent, reminder.getId());
        Intent snoozeIntent = new Intent(this, SnoozeReceiver.class);
        AlarmUtil.cancelAlarm(this, snoozeIntent, reminder.getId());
        finish();
    }

    private void actionEdit() {
        Intent intent = new Intent(this, CreateEditActivity.class);
        intent.putExtra("REMINDER_ID", reminder.getId());
        startActivity(intent);
        finish();
    }

    private void actionMarkAsDone() {
        reminderChanged = true;
        DatabaseHelper database = DatabaseHelper.getInstance(this);
        // Check whether next alarm needs to be set
        if (reminder.getNumberShown() + 1 != reminder.getNumberToShow() || Boolean.parseBoolean(reminder.getForeverState())) {
            AlarmUtil.setNextAlarm(this, reminder, database);
        } else {
            Intent alarmIntent = new Intent(getApplicationContext(), AlarmReceiver.class);
            AlarmUtil.cancelAlarm(this, alarmIntent, reminder.getId());
            reminder.setDateAndTime(DateAndTimeUtil.toStringDateAndTime(Calendar.getInstance()));
        }
        reminder.setNumberShown(reminder.getNumberShown() + 1);
        database.addReminder(reminder);
        assignReminderValues();
        database.close();
        Snackbar.make(coordinatorLayout, R.string.toast_mark_as_done, Snackbar.LENGTH_SHORT).show();
    }

    private void actionShareText() {
        Intent intent = new Intent(); intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, reminder.getTitle() + "\n" + reminder.getContent());
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)));
    }

    private void actionClone() {
        Intent intent = new Intent(this, CreateEditActivity.class);
        intent.putExtra("REMINDER_ID", reminder.getId());
        intent.putExtra("CLONE", true);
        startActivity(intent);
        finish();
    }

    private void returnHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void updateReminder() {
        DatabaseHelper database = DatabaseHelper.getInstance(this);
        reminder = database.getReminder(reminder.getId());
        database.close();
        assignReminderValues();
    }

    @Override
    public void onResume() {
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, new IntentFilter("BROADCAST_REFRESH"));
        updateReminder();
        super.onResume();
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
        super.onPause();
    }

    private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reminderChanged = true;
            updateReminder();
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_viewer, menu);
        if (hideMarkAsDone) {
            menu.findItem(R.id.action_mark_as_done).setVisible(false);
        }

        if (hideSnooze) {
            menu.findItem(R.id.action_snooze).setVisible(false);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (reminderChanged) {
            finish();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_snooze:
                actionSnooze();
                return true;
            case R.id.action_delete:
                confirmDelete();
                return true;
            case R.id.action_edit:
                actionEdit();
                return true;
            case R.id.action_share:
                actionShareText();
                return true;
            case R.id.action_mark_as_done:
                actionMarkAsDone();
                return true;
            case R.id.action_show_now:
                actionShowNow();
                return true;
            case R.id.action_clone:
                actionClone();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}