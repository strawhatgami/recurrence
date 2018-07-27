package com.bleyl.recurrence.fragments;

import android.Manifest;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;

import com.bleyl.recurrence.R;
import com.bleyl.recurrence.database.CalendarHelper;
import com.bleyl.recurrence.utils.PermissionUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PreferenceFragment extends android.preference.PreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener{

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);
        updatePreferenceSummary();

        /* Note: Checkbox is here only to ask permission, job could have been done ui-side with only
         * the listPreference. For now, I can't manage to have only a single listPreference that:
         * 1/ on click, ask for permission
         * 2/ if permission is given, update the calendar list
         * 3/ show the updated calendar list (issue is here)
         * 4/ restore the reminders of given calendar when non-default calendar is chosen
         * The drawback of the current fallback code is that when the checkbox is checked, the
         * permission is asked at each PreferenceFragment creation, instead of on click on the
         * listPreference (that could have been the right way).
         */
        setCheckBoxSyncCalendarsAction();
        final CheckBoxPreference checkboxPreference = (CheckBoxPreference) findPreference("checkBoxSyncCalendars");
        enableSyncFunctionality(checkboxPreference.isChecked());
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

    private String listPreferenceUpdatedValue;
    public void updateCalendarsList() {
        final PreferenceFragment that = this;
        final ListPreference listPreference = (ListPreference) findPreference("listSyncCalendar");

        HashMap<String, String> calendarsList = new CalendarHelper().getCalendarsList(getActivity());

        List<CharSequence> entries = new ArrayList<CharSequence>(Arrays.asList(
            getActivity().getResources().getStringArray(R.array.defaultCalendarList)
        ));
        List<CharSequence> entryValues = new ArrayList<CharSequence>(Arrays.asList(
            getActivity().getResources().getStringArray(R.array.defaultCalendarValuesList)
        ));

        for (Map.Entry<String, String> item : calendarsList.entrySet()) {
            entries.add(item.getKey());
            entryValues.add(item.getValue());
        }

        listPreference.setEntries(entries.toArray(new CharSequence[entries.size()]));
        listPreference.setEntryValues(entryValues.toArray(new CharSequence[entryValues.size()]));
        listPreference.setDefaultValue(getActivity().getResources().getString(R.string.default_calendar_value));

        listPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                listPreferenceUpdatedValue = (String) newValue;
                if (!listPreferenceUpdatedValue.equals(getActivity().getResources().getString(R.string.default_calendar_value))){
                    String[] permissions = new String[]{Manifest.permission.READ_CALENDAR};
                    PermissionUtil
                        .getInstance()
                        .allPermissionsGrantedOrAskForThem(getActivity(), permissions, new PermissionUtil.IPermissionCallback() {
                            @Override
                            public void onPermissionGranted(String[] permissions, int[] grantResults) {
                                boolean allAllowed = PermissionUtil.allGranted(grantResults);
                                if (allAllowed) {
                                    CalendarHelper.restoreFromCalendar(getActivity(), listPreferenceUpdatedValue);
                                }
                            }
                        });
                }

                return true;
            }
        });
    }

    public void enableSyncFunctionality(Boolean allowed) {
        findPreference("listSyncCalendar").setEnabled(allowed);
        if (allowed) updateCalendarsList();
    }

    public void setCheckBoxSyncCalendarsAction() {
        final PreferenceFragment that = this;
        final CheckBoxPreference checkboxPreference = (CheckBoxPreference) findPreference("checkBoxSyncCalendars");
        checkboxPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                if (checkboxPreference.isChecked()) {
                    String[] permissions = new String[]{Manifest.permission.READ_CALENDAR};
                    PermissionUtil
                        .getInstance()
                        .allPermissionsGrantedOrAskForThem(getActivity(), permissions, new PermissionUtil.IPermissionCallback() {
                            @Override
                            public void onPermissionGranted(String[] permissions, int[] grantResults) {
                              boolean allAllowed = PermissionUtil.allGranted(grantResults);
                              enableSyncFunctionality(allAllowed);

                              if (!allAllowed) {
                                  CheckBoxPreference checkboxPreference = (CheckBoxPreference) findPreference("checkBoxSyncCalendars");
                                  checkboxPreference.setChecked(false);
                              }
                            }
                        });
                    return true;
                } else {
                    enableSyncFunctionality(false);
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