package com.bleyl.recurrence.fragments;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import com.bleyl.recurrence.R;
import com.bleyl.recurrence.database.CalendarHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PreferenceFragment extends android.preference.PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);
        updatePreferenceSummary();

        setCheckBoxSyncCalendarsAction();
        final CheckBoxPreference checkboxPreference = (CheckBoxPreference) findPreference("checkBoxSyncCalendars");
        allowSyncFunctionality(checkboxPreference.isChecked());
    }

    private void updatePreferenceSummary() {
        SharedPreferences sharedPreferences = getPreferenceScreen().getSharedPreferences();

        // Set nagging preference summary
        int nagMinutes = sharedPreferences.getInt("nagMinutes", getResources().getInteger(R.integer.default_nag_minutes));
        int nagSeconds = sharedPreferences.getInt("nagSeconds", getResources().getInteger(R.integer.default_nag_seconds));
        Preference nagPreference = findPreference("nagInterval");
        String nagMinutesText = String.format(getActivity().getResources().getQuantityString(R.plurals.time_minute, nagMinutes), nagMinutes);
        String nagSecondsText = String.format(getActivity().getResources().getQuantityString(R.plurals.time_second, nagSeconds), nagSeconds);
        nagPreference.setSummary(String.format("%s %s", nagMinutesText, nagSecondsText));
    }


    public void updateCalendarsList() {
        final ListPreference listPreference = (ListPreference) findPreference("listSyncCalendar");

        HashMap<String, String> calendarsList = new CalendarHelper().calendarsHashMap(getActivity());
        List<CharSequence> entries = new ArrayList<>(Arrays.asList(listPreference.getEntries()));
        List<CharSequence> entryValues = new ArrayList<>(Arrays.asList(listPreference.getEntryValues()));
        for (Map.Entry<String, String> item : calendarsList.entrySet()) {
            entries.add(item.getKey());
            entryValues.add(item.getValue());
        }

        listPreference.setEntries(entries.toArray(new CharSequence[entries.size()]));
        listPreference.setDefaultValue("@strings/default_calendar");
        listPreference.setEntryValues(entryValues.toArray(new CharSequence[entryValues.size()]));
    }

    public void allowSyncFunctionality(Boolean allowed) {
        findPreference("listSyncCalendar").setEnabled(allowed);
        if (allowed) updateCalendarsList();
    }

    public int PERM_REQUEST_ID = 0;
    public void syncCalendarPermissionCallback(String[] permissions, int[] grantResults) {
        boolean allAllowed = (grantResults[0] == 1) && (grantResults[1] == 1);
        allowSyncFunctionality(allAllowed);

        if (!allAllowed) {
            CheckBoxPreference checkboxPreference = (CheckBoxPreference) findPreference("checkBoxSyncCalendars");
            checkboxPreference.setChecked(false);
        }
    }

    public void setCheckBoxSyncCalendarsAction() {
        final CheckBoxPreference checkboxPreference = (CheckBoxPreference) findPreference("checkBoxSyncCalendars");
        checkboxPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                if (checkboxPreference.isChecked()) {
                    String[] permissions = new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR};
                    List<String> permissionsRequired = new ArrayList();

                    for(String permission : permissions) {
                        if (ContextCompat.checkSelfPermission(getActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
                            permissionsRequired.add(permission);
                        }
                    }

                    if (permissionsRequired.size() == 0) {
                        int[] grantResults = new int[permissions.length];
                        Arrays.fill(grantResults, 1);
                        syncCalendarPermissionCallback(permissions, grantResults);
                        return true;
                    }

                    ActivityCompat.requestPermissions(getActivity(), permissions, PERM_REQUEST_ID);
                    return true;
                } else {
                    allowSyncFunctionality(false);
                }

                return true;
            }
        });
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        updatePreferenceSummary();
    }

    @Override
    public void onStart() {
        super.onStart();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }
}