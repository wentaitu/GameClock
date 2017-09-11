package cn.edu.cqupt.gameclock;

import java.util.TreeMap;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import cn.edu.cqupt.gameclock.activity.AlarmClockActivity;
import cn.edu.cqupt.gameclock.alarm.AlarmUtil;
import cn.edu.cqupt.gameclock.service.AlarmClockServiceBinder;

/**
 * Created by wentai on 17-8-21.
 */

// 包含所有当前正在运行的闹钟，通过android AlarmManager服务添加/删除
public final class PendingAlarmList {
  // Maps alarmId -> alarm.
  private TreeMap<Long, PendingAlarm> pendingAlarms;
  // Maps alarm time -> alarmId.
  private TreeMap<AlarmTime, Long> alarmTimes;
  private AlarmManager alarmManager;
  private Context context;

  public PendingAlarmList(Context context) {
    pendingAlarms = new TreeMap<>();
    alarmTimes = new TreeMap<>();
    alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    this.context = context;
  }

  public int size() {
    if (pendingAlarms.size() != alarmTimes.size()) {
      throw new IllegalStateException("Inconsistent pending alarms: "
          + pendingAlarms.size() + " vs " + alarmTimes.size());
    }
    return pendingAlarms.size();
  }

  public void put(long alarmId, AlarmTime time) {
    remove(alarmId);

    Intent notifyIntent = new Intent(context, ReceiverAlarm.class);
    notifyIntent.setData(AlarmUtil.alarmIdToUri(alarmId));
    PendingIntent scheduleIntent =
      PendingIntent.getBroadcast(context, 0, notifyIntent, 0);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          Intent intent = new Intent(context, AlarmClockActivity.class);

          PendingIntent showIntent = PendingIntent.getActivity(context, 0,
                  intent, PendingIntent.FLAG_UPDATE_CURRENT);

          AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.
                  AlarmClockInfo(time.calendar().getTimeInMillis(),
                  showIntent
                  );

          alarmManager.setAlarmClock(alarmClockInfo, scheduleIntent);
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
          alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                  time.calendar().getTimeInMillis(), scheduleIntent);
      } else {
          alarmManager.set(AlarmManager.RTC_WAKEUP,
                  time.calendar().getTimeInMillis(), scheduleIntent);
      }

    pendingAlarms.put(alarmId, new PendingAlarm(time, scheduleIntent));
    alarmTimes.put(time, alarmId);

    if (pendingAlarms.size() != alarmTimes.size()) {
        throw new IllegalStateException("Inconsistent pending alarms: "
            + pendingAlarms.size() + " vs " + alarmTimes.size());
    }
  }

  public boolean remove(long alarmId) {
      PendingAlarm alarm = pendingAlarms.remove(alarmId);
      if (alarm == null) {
          return false;
      }
      Long expectedAlarmId = alarmTimes.remove(alarm.time());
      alarmManager.cancel(alarm.pendingIntent());
      alarm.pendingIntent().cancel();

      if (expectedAlarmId != alarmId) {
          throw new IllegalStateException("Internal inconsistency in PendingAlarmList");
      }

      if (pendingAlarms.size() != alarmTimes.size()) {
          throw new IllegalStateException("Inconsistent pending alarms: "
              + pendingAlarms.size() + " vs " + alarmTimes.size());
      }

      return true;
  }

  public AlarmTime nextAlarmTime() {
      if (alarmTimes.size() == 0) {
        return null;
      }
      return alarmTimes.firstKey();
  }

    public long nextAlarmId() {
        if (alarmTimes.size() == 0) {
            return AlarmClockServiceBinder.NO_ALARM_ID;
        }
        return alarmTimes.get(alarmTimes.firstKey());
    }

  public AlarmTime pendingTime(long alarmId) {
      PendingAlarm alarm = pendingAlarms.get(alarmId);
      return alarm == null ? null : alarm.time();
  }

  public AlarmTime[] pendingTimes() {
      AlarmTime[] times = new AlarmTime[alarmTimes.size()];
      alarmTimes.keySet().toArray(times);
      return times;
  }

  public Long[] pendingAlarms() {
      Long[] alarmIds = new Long[pendingAlarms.size()];
      pendingAlarms.keySet().toArray(alarmIds);
      return alarmIds;
  }

  private class PendingAlarm {
      private AlarmTime time;
      private PendingIntent pendingIntent;

      PendingAlarm(AlarmTime time, PendingIntent pendingIntent) {
          this.time = time;
          this.pendingIntent = pendingIntent;
      }
      public AlarmTime time() {
          return time;
      }
      public PendingIntent pendingIntent() {
          return pendingIntent;
      }
  }
}
