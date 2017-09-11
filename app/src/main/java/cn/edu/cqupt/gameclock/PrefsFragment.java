package cn.edu.cqupt.gameclock;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import cn.edu.cqupt.gameclock.activity.AboutActivity;
import cn.edu.cqupt.gameclock.activity.AlarmClockActivity;
import cn.edu.cqupt.gameclock.service.AlarmClockService;

/**
 * Created by wentai on 17-8-21.
 */

//完成全局设置，简单的preferences fragment
public class PrefsFragment extends PreferenceFragmentCompat
        implements
            Preference.OnPreferenceChangeListener,
            Preference.OnPreferenceClickListener {

    private static final int CUSTOM_LOCK_SCREEN = 0;
    private static final int CUSTOM_NOTIFICATION_TEXT = 1;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        getActivity().setTitle(R.string.app_settings);

        Preference.OnPreferenceChangeListener refreshListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                // 如果用户关闭此项，则清除锁屏信息
                if (preference.getKey().equals(AppSettings.LOCK_SCREEN)) {
                    clearLockScreenText(getActivity());

                    final String custom_lock_screen = getResources().getStringArray(R.array.lock_screen_values)[4];
                    if (newValue.equals(custom_lock_screen)) {
                        DialogFragment dialog = new ActivityDialogFragment().newInstance(
                                CUSTOM_LOCK_SCREEN);
                        dialog.show(getFragmentManager(), "ActivityDialogFragment");
                    }
                }

                final Intent causeRefresh = new Intent(getActivity(), AlarmClockService.class);
                causeRefresh.putExtra(AlarmClockService.COMMAND_EXTRA, AlarmClockService.COMMAND_NOTIFICATION_REFRESH);
                getActivity().startService(causeRefresh);
                return true;
            }
        };

        // 刷新notification icon当用户改变设置
        final Preference notification_icon = findPreference(AppSettings.NOTIFICATION_ICON);
        notification_icon.setOnPreferenceChangeListener(refreshListener);
        final Preference lock_screen = findPreference(AppSettings.LOCK_SCREEN);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getPreferenceScreen().removePreference(lock_screen);
        } else {
            lock_screen.setOnPreferenceChangeListener(refreshListener);
        }

        final Preference appTheme = findPreference(AppSettings.APP_THEME_KEY);

        appTheme.setOnPreferenceChangeListener(this);

        if (!BuildConfig.DEBUG) {
            getPreferenceScreen().removePreference(
                    findPreference(AppSettings.DEBUG_MODE));
        }

        findPreference(AppSettings.NOTIFICATION_TEXT).
                setOnPreferenceChangeListener(this);

        findPreference("tuwentai").
                setOnPreferenceClickListener(this);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey().equalsIgnoreCase("tuwentai")) {
            startActivity(new Intent(getActivity(), AboutActivity.class));
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
            case AppSettings.APP_THEME_KEY:
                AlarmClockActivity.alarmClockActivity.finish();

                startActivity(new Intent(getActivity(), AlarmClockActivity.class));

                getActivity().finish();
                break;
            case AppSettings.NOTIFICATION_TEXT:
                final String customNotificationText = getResources().getStringArray(
                        R.array.notification_text_values)[3];

                if (newValue.equals(customNotificationText)) {
                    DialogFragment dialog = new ActivityDialogFragment().newInstance(
                            CUSTOM_NOTIFICATION_TEXT);

                    dialog.show(getFragmentManager(), "ActivityDialogFragment");
                } else {
                    final Intent causeRefresh = new Intent(getActivity(),
                            AlarmClockService.class);

                    causeRefresh.putExtra(AlarmClockService.COMMAND_EXTRA,
                            AlarmClockService.COMMAND_NOTIFICATION_REFRESH);

                    getActivity().startService(causeRefresh);
                }
                break;
            default:
                break;
        }

        return true;
    }

    @SuppressWarnings("deprecation")
    private void clearLockScreenText(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Settings.System.putString(context.getContentResolver(),
                    Settings.System.NEXT_ALARM_FORMATTED, "");
        }
    }

    public static class ActivityDialogFragment extends DialogFragment {

        public ActivityDialogFragment newInstance(int id) {
            ActivityDialogFragment fragment = new ActivityDialogFragment();
            Bundle args = new Bundle();
            args.putInt("id", id);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            switch (getArguments().getInt("id")) {
                case CUSTOM_LOCK_SCREEN:
                    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    final View lockTextView = View.inflate(getActivity(),
                            R.layout.custom_lock_screen_dialog, null);
                    final EditText editText = (EditText) lockTextView.findViewById(R.id.custom_lock_screen_text);
                    editText.setText(prefs.getString(AppSettings.CUSTOM_LOCK_SCREEN_TEXT, ""));
                    final CheckBox persistentCheck = (CheckBox) lockTextView.findViewById(R.id.custom_lock_screen_persistent);
                    persistentCheck.setChecked(prefs.getBoolean(AppSettings.CUSTOM_LOCK_SCREEN_PERSISTENT, false));
                    final AlertDialog.Builder lockTextBuilder = new AlertDialog.Builder(getActivity());
                    lockTextBuilder.setTitle(R.string.custom_lock_screen_text);
                    lockTextBuilder.setView(lockTextView);
                    lockTextBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString(AppSettings.CUSTOM_LOCK_SCREEN_TEXT, editText.getText().toString());
                            editor.putBoolean(AppSettings.CUSTOM_LOCK_SCREEN_PERSISTENT, persistentCheck.isChecked());
                            editor.apply();
                            final Intent causeRefresh = new Intent(getActivity(),
                                    AlarmClockService.class);
                            causeRefresh.putExtra(AlarmClockService.COMMAND_EXTRA,
                                    AlarmClockService.COMMAND_NOTIFICATION_REFRESH);
                            getActivity().startService(causeRefresh);
                            dismiss();
                        }
                    });
                    lockTextBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dismiss();
                        }
                    });
                    return lockTextBuilder.create();
                case CUSTOM_NOTIFICATION_TEXT:
                    final SharedPreferences notifyPrefs = PreferenceManager.
                            getDefaultSharedPreferences(getActivity());

                    final View notifyTextView = View.inflate(getActivity(),
                            R.layout.custom_notification_text_dialog, null);

                    final EditText notifyEditText = (EditText) notifyTextView.
                            findViewById(R.id.custom_notification_text_et);

                    notifyEditText.setText(notifyPrefs.getString(
                                    AppSettings.CUSTOM_NOTIFICATION_TEXT, ""));

                    final AlertDialog.Builder notifyBuilder = new
                            AlertDialog.Builder(getActivity());

                    notifyBuilder.setTitle(R.string.custom_notification_text);

                    notifyBuilder.setView(notifyTextView);

                    notifyBuilder.setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SharedPreferences.Editor editor = notifyPrefs.edit();

                            editor.putString(
                                    AppSettings.CUSTOM_NOTIFICATION_TEXT,
                                    notifyEditText.getText().toString().trim()
                            );

                            editor.apply();

                            final Intent causeRefresh = new Intent(
                                    getActivity(),
                                    AlarmClockService.class
                            );

                            causeRefresh.putExtra(
                                    AlarmClockService.COMMAND_EXTRA,
                                    AlarmClockService.COMMAND_NOTIFICATION_REFRESH
                            );

                            getActivity().startService(causeRefresh);

                            dismiss();
                        }
                    });
                    notifyBuilder.setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dismiss();
                        }
                    });
                    return notifyBuilder.create();
                default:
                    return super.onCreateDialog(savedInstanceState);
            }
        }

    }
}
