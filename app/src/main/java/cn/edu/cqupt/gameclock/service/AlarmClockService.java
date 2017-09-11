package cn.edu.cqupt.gameclock.service;

import java.util.HashMap;
import java.util.Map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import org.apache.commons.lang3.text.StrSubstitutor;

import cn.edu.cqupt.gameclock.activity.AlarmClockActivity;
import cn.edu.cqupt.gameclock.alarm.AlarmClockInterfaceStub;
import cn.edu.cqupt.gameclock.alarm.AlarmInfo;
import cn.edu.cqupt.gameclock.AlarmTime;
import cn.edu.cqupt.gameclock.AppSettings;
import cn.edu.cqupt.gameclock.db.DbAccessor;
import cn.edu.cqupt.gameclock.LoggingUncaughtExceptionHandler;
import cn.edu.cqupt.gameclock.PendingAlarmList;
import cn.edu.cqupt.gameclock.R;
import cn.edu.cqupt.gameclock.ReceiverNotificationRefresh;

/**
 * Created by wentai on 17-8-15.
 */

public final class AlarmClockService extends Service {
    public final static String COMMAND_EXTRA = "command";
    public final static int COMMAND_UNKNOWN = 1;
    public final static int COMMAND_NOTIFICATION_REFRESH = 2;
    public final static int COMMAND_DEVICE_BOOT = 3;
    public final static int COMMAND_TIMEZONE_CHANGE = 4;

    public final static int NOTIFICATION_BAR_ID = 69;

    private DbAccessor db;
    private PendingAlarmList pendingAlarms;

    @Override
    public void onCreate() {
      super.onCreate();

      // 注册一个能够写入堆栈跟踪的异常处理程序到设备的SD卡，需要申请权限
      if (getPackageManager().checkPermission(
          "android.permission.WRITE_EXTERNAL_STORAGE", getPackageName()) ==
            PackageManager.PERMISSION_GRANTED) {
          Thread.setDefaultUncaughtExceptionHandler(
              new LoggingUncaughtExceptionHandler(
                    Environment.getExternalStorageDirectory().getPath()));
      }

      // 获取数据
      db = new DbAccessor(getApplicationContext());
      pendingAlarms = new PendingAlarmList(getApplicationContext());

      // 初始启动就打开运行中的闹钟
      for (Long alarmId : db.getEnabledAlarms()) {
          if (pendingAlarms.pendingTime(alarmId) != null) {
              continue;
          }
          AlarmTime alarmTime = null;

          AlarmInfo info = db.readAlarmInfo(alarmId);

          if (info != null) {
              alarmTime = info.getTime();
          }

          pendingAlarms.put(alarmId, alarmTime);
      }
      ReceiverNotificationRefresh.startRefreshing(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
      handleStart(intent);
      //如果service进程被kill掉，保留service的状态为开始状态，但不保留递送的intent对象。随后系统会尝试重新创建service
      return START_STICKY;
    }

    private void handleStart(Intent intent) {
      if (intent != null && intent.hasExtra(COMMAND_EXTRA)) {
          Bundle extras = intent.getExtras();
          int command = extras.getInt(COMMAND_EXTRA, COMMAND_UNKNOWN);

          final Handler handler = new Handler();
          final Runnable maybeShutdown = new Runnable() {
              @Override
              public void run() {
                  if (pendingAlarms.size() == 0) {
                     stopSelf();
                  }
              }
          };

      switch (command) {
          case COMMAND_NOTIFICATION_REFRESH:
              refreshNotification();
              handler.post(maybeShutdown);
              break;
          case COMMAND_DEVICE_BOOT:
              fixPersistentSettings();
              handler.post(maybeShutdown);
              break;
          case COMMAND_TIMEZONE_CHANGE:
            if (AppSettings.isDebugMode(getApplicationContext())) {
                Toast.makeText(getApplicationContext(), "时区改变，刷新...", Toast.LENGTH_SHORT).show();
            }
            for (long alarmId : pendingAlarms.pendingAlarms()) {
                scheduleAlarm(alarmId);
                if (AppSettings.isDebugMode(getApplicationContext())) {
                    Toast.makeText(getApplicationContext(), "ALARM " + alarmId, Toast.LENGTH_SHORT).show();
                }
            }
            handler.post(maybeShutdown);
            break;
          default:
              throw new IllegalArgumentException("Error!!!");
      }
    }
    }

    private void refreshNotification() {
        String resolvedString = getString(R.string.no_pending_alarms);

        AlarmTime nextTime = pendingAlarms.nextAlarmTime();

        if (nextTime != null) {
          Map<String, String> values = new HashMap<>();

          values.put("t", nextTime.localizedString(getApplicationContext()));

          values.put("c", nextTime.timeUntilString(getApplicationContext()));

          String templateString = AppSettings.getNotificationTemplate(
                  getApplicationContext());

          StrSubstitutor sub = new StrSubstitutor(values);

          resolvedString = sub.replace(templateString);
        }

        // 通知点击后打开主活动
        final Intent notificationIntent = new Intent(this, AlarmClockActivity.class);
        final PendingIntent launch = PendingIntent.getActivity(this, 0,
            notificationIntent, 0);

        Context c = getApplicationContext();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
              getApplicationContext());

        String notificationTitle = getString(R.string.app_name);

        if (pendingAlarms.nextAlarmId() != AlarmClockServiceBinder.NO_ALARM_ID) {
            DbAccessor db = new DbAccessor(getApplicationContext());

            AlarmInfo alarmInfo = db.readAlarmInfo(pendingAlarms.nextAlarmId());

            if (alarmInfo != null) {
              notificationTitle = alarmInfo.getName() != null && !alarmInfo.getName().isEmpty()
                      ? alarmInfo.getName()
                      : getString(R.string.app_name);
            }

            db.closeConnections();
        }

        Notification notification = builder
              .setContentIntent(launch)
              .setSmallIcon(R.drawable.ic_stat_notify_alarm)
              .setContentTitle(notificationTitle)
              .setContentText(resolvedString)
              .setColor(ContextCompat.getColor(getApplicationContext(),
                      R.color.notification_color))
              .build();
        notification.flags |= Notification.FLAG_ONGOING_EVENT;

        final NotificationManager manager =
          (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (pendingAlarms.size() > 0 && AppSettings.displayNotificationIcon(c)) {
            manager.notify(NOTIFICATION_BAR_ID, notification);
        } else {
            manager.cancel(NOTIFICATION_BAR_ID);
        }

        setSystemAlarmStringOnLockScreen(getApplicationContext(), nextTime);
    }

    @SuppressWarnings("deprecation")
    public static void setSystemAlarmStringOnLockScreen(Context context,
            AlarmTime alarmTime) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            String lockScreenText = AppSettings.lockScreenString(
                    context, alarmTime);

            if (lockScreenText != null) {
                Settings.System.putString(context.getContentResolver(),
                        Settings.System.NEXT_ALARM_FORMATTED, lockScreenText);
            }
        }
    }

    public void fixPersistentSettings() {
    final String badDebugName = "DEBUG_MODE\"";
    final String badNotificationName = "NOTFICATION_ICON";
    final String badLockScreenName = "LOCK_SCREEN\"";
    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    Map<String, ?> prefNames = prefs.getAll();
    if (!prefNames.containsKey(badDebugName) &&
        !prefNames.containsKey(badNotificationName) &&
        !prefNames.containsKey(badLockScreenName)) {
      return;
    }
    Editor editor = prefs.edit();
    if (prefNames.containsKey(badDebugName)) {
        editor.putString(AppSettings.DEBUG_MODE, prefs.getString(badDebugName, null));
        editor.remove(badDebugName);
    }
    if (prefNames.containsKey(badNotificationName)){
        editor.putBoolean(AppSettings.NOTIFICATION_ICON, prefs.getBoolean(badNotificationName, true));
        editor.remove(badNotificationName);
    }
    if (prefNames.containsKey(badLockScreenName)) {
        editor.putString(AppSettings.LOCK_SCREEN, prefs.getString(badLockScreenName, null));
        editor.remove(badLockScreenName);
    }
        editor.apply();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        db.closeConnections();

        ReceiverNotificationRefresh.stopRefreshing(getApplicationContext());

        final NotificationManager manager =
          (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(NOTIFICATION_BAR_ID);

          setSystemAlarmStringOnLockScreen(getApplicationContext(), null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new AlarmClockInterfaceStub(getApplicationContext(), this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (pendingAlarms.size() == 0) {
          stopSelf();
          return false;
        }
        //返回true使IBinder对象re-used直到服务关闭
        return true;
    }

    public AlarmTime pendingAlarm(long alarmId) {
        return pendingAlarms.pendingTime(alarmId);
    }

    public AlarmTime[] pendingAlarmTimes() {
        return pendingAlarms.pendingTimes();
    }

    public long resurrectAlarm(AlarmTime time, String alarmName, boolean enabled) {
        long alarmId =  db.newAlarm(time, enabled, alarmName);

        if (enabled) {
            scheduleAlarm(alarmId);
        }

        return alarmId;
    }

    public void createAlarm(AlarmTime time) {
        //存入数据库
        long alarmId = db.newAlarm(time, true, "");
        scheduleAlarm(alarmId);
    }

    public void deleteAlarm(long alarmId) {
        pendingAlarms.remove(alarmId);
        db.deleteAlarm(alarmId);
        refreshNotification();
    }

    public void deleteAllAlarms() {
        for (Long alarmId : db.getAllAlarms()) {
          deleteAlarm(alarmId);
        }
    }

    public void scheduleAlarm(long alarmId) {
        AlarmInfo info = db.readAlarmInfo(alarmId);
        if (info == null) {
            return;
        }
        // 安排下一个闹钟
        pendingAlarms.put(alarmId, info.getTime());

        // 操作数据库，使闹钟可行
        db.enableAlarm(alarmId, true);

        //运行中闹钟>=1，启动服务
        final Intent self = new Intent(getApplicationContext(), AlarmClockService.class);
        startService(self);

        refreshNotification();
    }

    public void acknowledgeAlarm(long alarmId) {
        AlarmInfo info = db.readAlarmInfo(alarmId);
        if (info == null) {
            return;
        }

        pendingAlarms.remove(alarmId);

        AlarmTime time = info.getTime();
        if (time.repeats()) {
          pendingAlarms.put(alarmId, time);
        } else {
          db.enableAlarm(alarmId, false);
        }
        refreshNotification();
    }

    public void dismissAlarm(long alarmId) {
        AlarmInfo info = db.readAlarmInfo(alarmId);
        if (info == null) {
          return;
        }
        pendingAlarms.remove(alarmId);
        db.enableAlarm(alarmId, false);

        refreshNotification();
    }

    public void snoozeAlarm(long alarmId) {
        snoozeAlarmFor(alarmId, db.readAlarmSettings(alarmId).getSnoozeMinutes());
    }

    public void snoozeAlarmFor(long alarmId, int minutes) {
        // 清除延时闹钟
        pendingAlarms.remove(alarmId);

        // 计算下一个闹钟的时间
        AlarmTime time = AlarmTime.snoozeInMillisUTC(minutes);

        // 安排
        pendingAlarms.put(alarmId, time);
        refreshNotification();
    }
}