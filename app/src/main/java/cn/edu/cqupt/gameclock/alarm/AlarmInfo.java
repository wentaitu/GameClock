package cn.edu.cqupt.gameclock.alarm;

import java.util.Calendar;

import android.content.ContentValues;
import android.database.Cursor;

import cn.edu.cqupt.gameclock.AlarmTime;
import cn.edu.cqupt.gameclock.Week;
import cn.edu.cqupt.gameclock.db.DbHelper;

/**
 * Created by wentai on 17-8-21.
 */

// 包含闹钟数据和一个闹钟标识符，建立数据库建立映射
public final class AlarmInfo {

    private long alarmId;
    private AlarmTime time;
    private boolean enabled;
    private String name;

    public AlarmInfo(Cursor cursor) {
        alarmId = cursor.getLong(cursor.getColumnIndex(DbHelper.ALARMS_COL__ID));
        enabled = cursor.getInt(cursor.getColumnIndex(DbHelper.ALARMS_COL_ENABLED)) == 1;
        name = cursor.getString(cursor.getColumnIndex(DbHelper.ALARMS_COL_NAME));
        int secondsAfterMidnight = cursor.getInt(cursor.getColumnIndex(DbHelper.ALARMS_COL_TIME));
        int dowBitmask = cursor.getInt(cursor.getColumnIndex(DbHelper.ALARMS_COL_DAY_OF_WEEK));
        time = BuildAlarmTime(secondsAfterMidnight, dowBitmask);
    }

    public AlarmInfo(AlarmTime time, boolean enabled, String name) {
        alarmId = -69;  // 初始化无效
        this.time = time;
        this.enabled = enabled;
        this.name = name;
    }

    public AlarmInfo(AlarmInfo rhs) {
        alarmId = rhs.alarmId;
        time = new AlarmTime(rhs.time);
        enabled = rhs.enabled;
        name = rhs.name;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AlarmInfo)) {
          return false;
        }

        AlarmInfo rhs = (AlarmInfo) o;
        return alarmId == rhs.alarmId
          && time.equals(rhs.time)
          && enabled == rhs.enabled
          && name.equals(rhs.name);
    }

    public ContentValues contentValues() {
        ContentValues values = new ContentValues();
        values.put(DbHelper.ALARMS_COL_TIME, TimeToInteger(time));
        values.put(DbHelper.ALARMS_COL_ENABLED, enabled);
        values.put(DbHelper.ALARMS_COL_NAME, name);
        values.put(DbHelper.ALARMS_COL_DAY_OF_WEEK, WeekToInteger(time));
        return values;
    }

    static public String[] contentColumns() {
        return new String[] {
            DbHelper.ALARMS_COL__ID,
            DbHelper.ALARMS_COL_TIME,
            DbHelper.ALARMS_COL_ENABLED,
            DbHelper.ALARMS_COL_NAME,
            DbHelper.ALARMS_COL_DAY_OF_WEEK
        };
    }

    public long getAlarmId() {
        return alarmId;
    }

    public AlarmTime getTime() {
        return time;
    }

    public void setTime(AlarmTime time) {
        this.time = time;
    }

    public void setDaysOfWeek(Week week) {
        time.setDaysOfWeek(week);
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private static int TimeToInteger(AlarmTime time) {
        Calendar c = time.calendar();
        int hourOfDay = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        int second = c.get(Calendar.SECOND);
        return hourOfDay * 3600 + minute * 60 + second;
    }

    private static int WeekToInteger(AlarmTime time) {
      boolean[] bitmask = time.getDaysOfWeek().bitmask();
      int dowBitmask = 0;
      for (Week.Day day: Week.Day.values()) {
        if (bitmask[day.ordinal()]) {
          dowBitmask |= 1 << day.ordinal();
        }
      }
      return dowBitmask;
    }

    private static AlarmTime BuildAlarmTime(int secondsAfterMidnight, int dowBitmask) {
      int hours = secondsAfterMidnight % 3600;
      int minutes = (secondsAfterMidnight - (hours * 3600)) % 60;
      int seconds = (secondsAfterMidnight- (hours * 3600 + minutes * 60));

      Week week = new Week();
      for (Week.Day day : Week.Day.values()) {
        if ((dowBitmask & 1 << day.ordinal()) > 0) {
          week.addDay(day);
        }
      }

      return new AlarmTime(hours, minutes, seconds, week);
    }
}
