package cn.edu.cqupt.gameclock.db;

import java.util.LinkedList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import cn.edu.cqupt.gameclock.alarm.AlarmInfo;
import cn.edu.cqupt.gameclock.alarm.AlarmSettings;
import cn.edu.cqupt.gameclock.AlarmTime;

/**
 * Created by wentai on 17-8-21.
 */

public final class DbAccessor {
    private SQLiteDatabase rDb;
    private SQLiteDatabase rwDb;

    public DbAccessor(Context context) {
        DbHelper db = new DbHelper(context);
        rwDb = db.getWritableDatabase();
        rDb = db.getReadableDatabase();
    }

    public void closeConnections() {
        rDb.close();
        rwDb.close();
    }

    public long newAlarm(AlarmTime time, boolean enabled, String name) {
        AlarmInfo info = new AlarmInfo(time, enabled, name);

        long id = rwDb.insert(DbHelper.DB_TABLE_ALARMS, null, info.contentValues());
        if (id < 0) {
            throw new IllegalStateException("无法插入数据库");
        }
        return id;
    }

    public boolean deleteAlarm(long alarmId) {
        int count = rDb.delete(DbHelper.DB_TABLE_ALARMS,
            DbHelper.ALARMS_COL__ID + " = " + alarmId, null);
        // 可能不存在，不关心返回值
        rDb.delete(DbHelper.DB_TABLE_SETTINGS,
            DbHelper.SETTINGS_COL_ID + " = " + alarmId, null);
        return count > 0;
    }

    public boolean enableAlarm(long alarmId, boolean enabled) {
        ContentValues values = new ContentValues(1);
        values.put(DbHelper.ALARMS_COL_ENABLED, enabled);
        int count = rwDb.update(DbHelper.DB_TABLE_ALARMS, values,
            DbHelper.ALARMS_COL__ID + " = " + alarmId, null);
        return count != 0;
    }

    public List<Long> getEnabledAlarms() {
        LinkedList<Long> enabled = new LinkedList<>();
        Cursor cursor = rDb.query(DbHelper.DB_TABLE_ALARMS,
            new String[] { DbHelper.ALARMS_COL__ID },
            DbHelper.ALARMS_COL_ENABLED + " = 1", null, null, null, null);
        while (cursor.moveToNext()) {
          long alarmId = cursor.getLong(cursor.getColumnIndex(DbHelper.ALARMS_COL__ID));
          enabled.add(alarmId);
        }
        cursor.close();
        return enabled;
    }

    public List<Long> getAllAlarms() {
        LinkedList<Long> alarms = new LinkedList<>();
        Cursor cursor = rDb.query(DbHelper.DB_TABLE_ALARMS,
            new String[] { DbHelper.ALARMS_COL__ID },
            null, null, null, null, null);
        while (cursor.moveToNext()) {
            long alarmId = cursor.getLong(cursor.getColumnIndex(DbHelper.ALARMS_COL__ID));
            alarms.add(alarmId);
        }
        cursor.close();
        return alarms;
    }

    public boolean writeAlarmInfo(long alarmId, AlarmInfo info) {
        return rwDb.update(DbHelper.DB_TABLE_ALARMS, info.contentValues(),
            DbHelper.ALARMS_COL__ID + " = " + alarmId, null) == 1;
    }

    public Cursor readAlarmInfo() {
        return rDb.query(DbHelper.DB_TABLE_ALARMS, AlarmInfo.contentColumns(),
          null, null, null, null, DbHelper.ALARMS_COL_TIME + " ASC");
    }

    public AlarmInfo readAlarmInfo(long alarmId) {
        Cursor cursor = rDb.query(DbHelper.DB_TABLE_ALARMS,
            AlarmInfo.contentColumns(),
            DbHelper.ALARMS_COL__ID + " = " + alarmId, null, null, null, null);

        if (cursor.getCount() != 1) {
            cursor.close();
            return null;
        }

        cursor.moveToFirst();
        AlarmInfo info = new AlarmInfo(cursor);
        cursor.close();
        return info;
    }

    public boolean writeAlarmSettings(long alarmId, AlarmSettings settings) {
        Cursor cursor = rDb.query(DbHelper.DB_TABLE_SETTINGS,
            new String[] { DbHelper.SETTINGS_COL_ID },
            DbHelper.SETTINGS_COL_ID + " = " + alarmId, null, null, null, null);

        boolean success;
        if (cursor.getCount() < 1) {
          success = rwDb.insert(DbHelper.DB_TABLE_SETTINGS, null, settings.contentValues(alarmId)) >= 0;
        } else {
          success = rwDb.update(DbHelper.DB_TABLE_SETTINGS, settings.contentValues(alarmId),
              DbHelper.SETTINGS_COL_ID + " = " + alarmId, null) == 1;
        }
        cursor.close();
        return success;
    }

    public AlarmSettings readAlarmSettings(long alarmId) {
        Cursor cursor = rDb.query(DbHelper.DB_TABLE_SETTINGS,
            AlarmSettings.contentColumns(),
            DbHelper.SETTINGS_COL_ID + " = " + alarmId, null, null, null, null);

        if (cursor.getCount() != 1) {
            cursor.close();
            if (alarmId == AlarmSettings.DEFAULT_SETTINGS_ID) {
              return new AlarmSettings();
            }
            return readAlarmSettings(AlarmSettings.DEFAULT_SETTINGS_ID);
        }

        AlarmSettings settings = new AlarmSettings(cursor);
        cursor.close();
        return settings;
    }
}
