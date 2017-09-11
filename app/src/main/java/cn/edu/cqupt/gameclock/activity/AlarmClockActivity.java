package cn.edu.cqupt.gameclock.activity;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.hurteng.sandstorm.MainActivity;
import com.wdullaer.materialdatetimepicker.time.*;
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog;

import java.util.ArrayList;
import java.util.Calendar;

import cn.edu.cqupt.gameclock.alarm.AlarmAdapter;
import cn.edu.cqupt.gameclock.alarm.AlarmInfo;
import cn.edu.cqupt.gameclock.alarm.AlarmSettings;
import cn.edu.cqupt.gameclock.AlarmTime;
import cn.edu.cqupt.gameclock.alarm.AlarmUtil;
import cn.edu.cqupt.gameclock.AppIntro;
import cn.edu.cqupt.gameclock.AppSettings;
import cn.edu.cqupt.gameclock.AppSettingsActivity;
import cn.edu.cqupt.gameclock.db.DbAccessor;
import cn.edu.cqupt.gameclock.NotificationServiceInterface;
import cn.edu.cqupt.gameclock.R;
import cn.edu.cqupt.gameclock.net.MyHttpURL;
import cn.edu.cqupt.gameclock.service.AlarmClockServiceBinder;
import cn.edu.cqupt.gameclock.service.NotificationServiceBinder;

/**
 * Created by wentai on 17-8-21.
 */

public final class AlarmClockActivity extends AppCompatActivity
        implements
            TimePickerDialog.OnTimeSetListener,
            TimePickerDialog.OnTimeChangedListener {

    public static final int DELETE_CONFIRM = 1;
    public static final int DELETE_ALARM_CONFIRM = 2;

    private TimePickerDialog picker;
    public static AlarmClockActivity alarmClockActivity;

    private static AlarmClockServiceBinder service;
    private static NotificationServiceBinder notifyService;
    private DbAccessor db;
    private static AlarmAdapter adapter;
    private Cursor cursor;
    private Handler handler;
    private Runnable tickCallback;
    private static RecyclerView alarmList;
    private int mLastFirstVisiblePosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppSettings.setMainActivityTheme(getBaseContext(), this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.alarm_list);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //设置每日一图
        final ImageView headBack = (ImageView) findViewById(R.id.head_back);
//        MyHttpURL.get("http://guolin.tech/api/bing_pic", new MyHttpURL.Callback() {
//            @Override
//            public void onResponse(String response) {
//                Glide.with(AlarmClockActivity.this).load(response).into(headBack);
//            }
//        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        Glide.with(AlarmClockActivity.this).load("http://api.dujin.org/bing/1366.php").into(headBack);

        alarmClockActivity = this;

        service = new AlarmClockServiceBinder(getApplicationContext());

        db = new DbAccessor(getApplicationContext());

        handler = new Handler();

        alarmList = (RecyclerView) findViewById(R.id.alarm_list);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        alarmList.setLayoutManager(layoutManager);

        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                final AlarmInfo alarmInfo = adapter.getAlarmInfos().
                        get(viewHolder.getAdapterPosition());

                final long alarmId = alarmInfo.getAlarmId();

                removeItemFromList(AlarmClockActivity.this, alarmId,
                        viewHolder.getAdapterPosition());

                Snackbar.make(findViewById(R.id.coordinator_layout),
                        getString(R.string.alarm_deleted), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.undo), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        undoAlarmDeletion(alarmInfo.getTime(),
                                db.readAlarmSettings(alarmId),
                                alarmInfo.getName(), alarmInfo.enabled());
                    }
                })
                .show();
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(alarmList);

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.add_fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar now = Calendar.getInstance();

                picker = TimePickerDialog.newInstance(
                        AlarmClockActivity.this,
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        now.get(Calendar.SECOND),
                        DateFormat.is24HourFormat(AlarmClockActivity.this)
                );

                if (AppSettings.isThemeDark(AlarmClockActivity.this)) {
                    picker.setThemeDark(true);
                }

                picker.setAccentColor(AppSettings.getTimePickerColor(
                        AlarmClockActivity.this));

                picker.vibrate(true);

                picker.enableSeconds(true);

                AlarmTime time = new AlarmTime(now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE), now.get(Calendar.SECOND));

                picker.setTitle(time.timeUntilString(AlarmClockActivity.this));

                picker.show(getFragmentManager(), "TimePickerDialog");
            }
        });

        tickCallback = new Runnable() {
            @Override
            public void run() {
                redraw();

                //间隔由每分改为每秒，达到每秒更新一次toolbar时间
                AlarmUtil.Interval interval = AlarmUtil.Interval.SECOND;
                long next = AlarmUtil.millisTillNextInterval(interval);

                handler.postDelayed(tickCallback, next);
            }
        };

        if (!AppIntro.isAlarmDeletionShowcased(this)) {
            requery();

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                if (adapter.getItemCount() >= 1 && alarmList.getChildAt(0) != null) {
                    AppIntro.showcaseAlarmDeletion(AlarmClockActivity.this, alarmList.getChildAt(0));
                }
                }
            }, 500);
        }
    }

    private void undoAlarmDeletion(AlarmTime alarmTime,
                                   AlarmSettings alarmSettings, String alarmName, boolean enabled) {
        long newAlarmId = service.resurrectAlarm(alarmTime, alarmName, enabled);

        if (newAlarmId != AlarmClockServiceBinder.NO_ALARM_ID) {
            db.writeAlarmSettings(newAlarmId, alarmSettings);

            requery();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        invalidateOptionsMenu();

        service.bind();

        handler.post(tickCallback);

        requery();

        alarmList.getLayoutManager().scrollToPosition(mLastFirstVisiblePosition);

        notifyService = new NotificationServiceBinder(getApplicationContext());

        notifyService.bind();

        notifyService.call(new NotificationServiceBinder.ServiceCallback() {
            @Override
            public void run(NotificationServiceInterface service) {
                int count;

                try {
                    count = service.firingAlarmCount();
                } catch (RemoteException e) {
                    return;
                }

                if (count > 0) {
                    Intent notifyActivity = new Intent(getApplicationContext(),
                            MainActivity.class);

                    notifyActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(notifyActivity);
                }
            }
        });

        TimePickerDialog tpd = (TimePickerDialog) getFragmentManager().
                findFragmentByTag("TimePickerDialog");

        if (tpd != null) {
            picker = tpd;

            tpd.setOnTimeSetListener(this);
            tpd.setOnTimeChangedListener(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        handler.removeCallbacks(tickCallback);

        service.unbind();

        if (notifyService != null) {
            notifyService.unbind();
        }

        mLastFirstVisiblePosition = ((LinearLayoutManager)
                alarmList.getLayoutManager()).
                findFirstCompletelyVisibleItemPosition();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        db.closeConnections();

        alarmClockActivity = null;

        notifyService = null;

        cursor.close();
    }

    @Override
    public void onTimeSet(RadialPickerLayout view, int hourOfDay, int minute, int second) {
        AlarmTime time = new AlarmTime(hourOfDay, minute, second);

        service.createAlarm(time);

        requery();
    }

    @Override
    public void onTimeChanged(RadialPickerLayout view, int hourOfDay, int minute, int second) {
        AlarmTime time = new AlarmTime(hourOfDay, minute, second);

        picker.setTitle(time.timeUntilString(this));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete_all:
                showDialogFragment(DELETE_CONFIRM);
                break;
            case R.id.action_default_settings:
                Intent alarm_settings = new Intent(getApplicationContext(), AlarmSettingsActivity.class);
                alarm_settings.putExtra(AlarmSettingsActivity.EXTRAS_ALARM_ID, AlarmSettings.DEFAULT_SETTINGS_ID);
                startActivity(alarm_settings);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showDialogFragment(int id) {
        DialogFragment dialog = new AlarmClockActivity.ActivityDialogFragment().newInstance(
                id);

        dialog.show(getFragmentManager(), "ActivityDialogFragment");
    }

    // 更新toolbar,以显示时间
    private void redraw() {
        adapter.notifyDataSetChanged();

        Calendar now = Calendar.getInstance();
        AlarmTime time = new AlarmTime(now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE), now.get(Calendar.SECOND));

        ((CollapsingToolbarLayout) findViewById(R.id.toolbar_layout)).setTitle(
                time.localizedString(this));
    }

    private void requery() {
        cursor = db.readAlarmInfo();
        ArrayList<AlarmInfo> infos = new ArrayList<>();

        while (cursor.moveToNext()) {
            infos.add(new AlarmInfo(cursor));
        }

        adapter = new AlarmAdapter(infos, service, this);
        alarmList.setAdapter(adapter);
        setEmptyViewIfEmpty(this);
    }

    public static void setEmptyViewIfEmpty(Activity activity) {
        if (adapter.getItemCount() == 0) {
            activity.findViewById(R.id.empty_view).setVisibility(View.VISIBLE);

            alarmList.setVisibility(View.GONE);
        } else {
            activity.findViewById(R.id.empty_view).setVisibility(View.GONE);

            alarmList.setVisibility(View.VISIBLE);
        }
    }

    public static void removeItemFromList(Activity activity, long alarmId, int position) {
        if (adapter.getItemCount() == 1) {
            ((AppBarLayout) activity.findViewById(R.id.app_bar)).
                    setExpanded(true);
        }

        service.deleteAlarm(alarmId);

        adapter.removeAt(position);

        setEmptyViewIfEmpty(activity);
    }

    public static class ActivityDialogFragment extends DialogFragment {

        public ActivityDialogFragment newInstance(int id) {
            ActivityDialogFragment fragment = new ActivityDialogFragment();

            Bundle args = new Bundle();

            args.putInt("id", id);

            fragment.setArguments(args);

            return fragment;
        }

        public ActivityDialogFragment newInstance(int id, AlarmInfo info,
                int position) {
            ActivityDialogFragment fragment = new ActivityDialogFragment();

            Bundle args = new Bundle();

            args.putInt("id", id);

            args.putLong("alarmId", info.getAlarmId());

            args.putInt("position", position);

            fragment.setArguments(args);

            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            switch (getArguments().getInt("id")) {
                case AlarmClockActivity.DELETE_CONFIRM:
                    final AlertDialog.Builder deleteConfirmBuilder =
                            new AlertDialog.Builder(getActivity());

                    deleteConfirmBuilder.setTitle(R.string.delete_all);

                    deleteConfirmBuilder.setMessage(R.string.confirm_delete);

                    deleteConfirmBuilder.setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            service.deleteAllAlarms();

                            adapter.removeAll();

                            setEmptyViewIfEmpty(getActivity());

                            dismiss();
                        }
                    });

                    deleteConfirmBuilder.setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dismiss();
                        }
                    });
                    return deleteConfirmBuilder.create();
                case AlarmClockActivity.DELETE_ALARM_CONFIRM:
                    final AlertDialog.Builder deleteAlarmConfirmBuilder =
                            new AlertDialog.Builder(getActivity());

                    deleteAlarmConfirmBuilder.setTitle(R.string.delete);

                    deleteAlarmConfirmBuilder.setMessage(
                            R.string.confirm_delete);

                    deleteAlarmConfirmBuilder.setPositiveButton(R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    removeItemFromList(getActivity(),
                                            getArguments().getLong("alarmId"),
                                            getArguments().getInt("position"));

                                    dismiss();
                                }
                            });

                    deleteAlarmConfirmBuilder.setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    dismiss();
                                }
                            });
                    return deleteAlarmConfirmBuilder.create();
                default:
                    return super.onCreateDialog(savedInstanceState);
            }
        }

    }

}
