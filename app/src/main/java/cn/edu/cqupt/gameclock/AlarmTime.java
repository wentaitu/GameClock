package cn.edu.cqupt.gameclock;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.text.format.DateFormat;

/**
 * Created by wentai on 17-8-21.
 */

//闹钟时间类，00:00 - 23:59，可以重新设定，能被转换为对象传递，
//Comparable，为了显示在PendingAlarmList
public final class AlarmTime implements Parcelable, Comparable<AlarmTime> {
    private Calendar calendar;
    private Week daysOfWeek;

    public AlarmTime(AlarmTime rhs) {
        calendar = (Calendar) rhs.calendar.clone();
        daysOfWeek = new Week(rhs.daysOfWeek);
    }

    //新建一个不重复闹钟
    public AlarmTime(int hourOfDay, int minute, int second) {
        this(hourOfDay, minute, second, new Week());
    }

    //新建一个某天重复的闹钟
    public AlarmTime(int hourOfDay, int minute, int second, Week daysOfWeek) {
        this.calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, second);
        this.daysOfWeek = daysOfWeek;

        findNextOccurrence();
    }

    public void setDaysOfWeek(Week week) {
          daysOfWeek = week;
    }

    private void findNextOccurrence() {
        Calendar now = Calendar.getInstance();

        // 若闹钟已发生则移到明天
        if (calendar.before(now)) {
            calendar.add(Calendar.DATE, 1);
        }

        if (calendar.before(now)) {
            throw new IllegalStateException("Inconsistent calendar.");
        }

        if (daysOfWeek.equals(Week.NO_REPEATS)) {
          return;
        }

        //如果未设定重复，则闹钟一直进行
        for (int i = 0; i < Week.Day.values().length; ++i) {
          Week.Day alarmDay = Week.calendarToDay(calendar.get(Calendar.DAY_OF_WEEK));
          if (daysOfWeek.hasDay(alarmDay)) {
            return;
          }
          calendar.add(Calendar.DATE, 1);
        }

        throw new IllegalStateException("没有找到合适的日期");
    }

    @Override
    public int compareTo(@NonNull AlarmTime another) {
        return calendar.compareTo(another.calendar);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AlarmTime)) {
          return false;
        }
        AlarmTime rhs = (AlarmTime) o;

          return calendar.get(Calendar.HOUR_OF_DAY) == rhs.calendar().get(Calendar.HOUR_OF_DAY)
                  && calendar.get(Calendar.MINUTE) == rhs.calendar().get(Calendar.MINUTE)
                  && calendar.get(Calendar.SECOND) == rhs.calendar().get(Calendar.SECOND)
                  && Arrays.equals(getDaysOfWeek().bitmask(),
                  rhs.getDaysOfWeek().bitmask());
    }

    public String toString() {
        SimpleDateFormat formatter = new SimpleDateFormat("HH:mm.ss MMMM dd yyyy",
                Locale.US);
        return formatter.format(calendar.getTimeInMillis());
    }

    public String localizedString(Context context) {
        boolean is24HourFormat = DateFormat.is24HourFormat(context);
        String format;
        String second = ":ss";  //关闭调试模式下不显示秒

        if (is24HourFormat) {
          format = "HH:mm" + second;
        } else {
          format = "h:mm" + second + " aaa";
        }

        SimpleDateFormat formatter = new SimpleDateFormat(format, Locale.US);
        return formatter.format(calendar.getTime());
    }

    public Calendar calendar() {
        return calendar;
    }

    public Week getDaysOfWeek() {
        return daysOfWeek;
    }

    public boolean repeats() {
        return !daysOfWeek.equals(Week.NO_REPEATS);
    }

    public String timeUntilString(Context c) {
        Calendar now = Calendar.getInstance();
        if (calendar.before(now)) {
          return c.getString(R.string.alarm_has_occurred);
        }
        long now_min = now.getTimeInMillis() / 1000 / 60;
        long then_min = calendar.getTimeInMillis() / 1000 / 60;
        long difference_minutes = then_min - now_min;
        long days = difference_minutes / (60 * 24);
        long hours = difference_minutes % (60 * 24);
        long minutes = hours % 60;
        hours = hours / 60;

        String value = "";
        if (days == 1) {
          value += c.getString(R.string.day, days) + " ";
        } else if (days > 1) {
          value += c.getString(R.string.days, days) + " ";
        }

        if (hours == 1) {
          value += c.getString(R.string.hour, hours) + " ";
        } else if (hours > 1) {
          value += c.getString(R.string.hours, hours) + " ";
        }

        if (minutes == 1) {
          value += c.getString(R.string.minute, minutes) + " ";
        } else if (minutes > 1) {
          value += c.getString(R.string.minutes, minutes) + " ";
        }
        return value;
    }

    public static AlarmTime snoozeInMillisUTC(int minutes) {
        Calendar snooze = Calendar.getInstance();
        snooze.set(Calendar.SECOND, 0);
        snooze.add(Calendar.MINUTE, minutes);
        return new AlarmTime(
            snooze.get(Calendar.HOUR_OF_DAY),
            snooze.get(Calendar.MINUTE),
            snooze.get(Calendar.SECOND));
    }

    private AlarmTime(Parcel source) {
        this.calendar = (Calendar) source.readSerializable();
        this.daysOfWeek = source.readParcelable(null);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(calendar);
        dest.writeParcelable(daysOfWeek, 0);
    }

    public static final Parcelable.Creator<AlarmTime> CREATOR =
        new Parcelable.Creator<AlarmTime>() {
          @Override
          public AlarmTime createFromParcel(Parcel source) {
            return new AlarmTime(source);
          }
          @Override
          public AlarmTime[] newArray(int size) {
            return new AlarmTime[size];
          }
        };

    @Override
    public int describeContents() {
        return 0;
    }

}
