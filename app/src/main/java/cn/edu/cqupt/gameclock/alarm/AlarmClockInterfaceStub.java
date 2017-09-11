package cn.edu.cqupt.gameclock.alarm;

import android.content.Context;
import android.os.RemoteException;
import android.widget.Toast;

import cn.edu.cqupt.gameclock.AlarmClockInterface;
import cn.edu.cqupt.gameclock.AlarmTime;
import cn.edu.cqupt.gameclock.AppSettings;
import cn.edu.cqupt.gameclock.service.AlarmClockService;

/**
 * Created by wentai on 17-8-21.
 */

public final class AlarmClockInterfaceStub extends AlarmClockInterface.Stub {
  private Context context;
  private AlarmClockService service;

  public AlarmClockInterfaceStub(Context context, AlarmClockService service) {
    this.context = context;
    this.service = service;
  }

  @Override
  public AlarmTime pendingAlarm(long alarmId) throws RemoteException {
    return service.pendingAlarm(alarmId);
  }

  @Override
  public AlarmTime[] pendingAlarmTimes() throws RemoteException {
    return service.pendingAlarmTimes();
  }

  @Override
  public long resurrectAlarm(AlarmTime time, String alarmName, boolean enabled)
      throws RemoteException {
    debugToast("RESURRECT ALARM " + time.toString());
    return service.resurrectAlarm(time, alarmName, enabled);
  }

  @Override
  public void createAlarm(AlarmTime time) throws RemoteException {
    debugToast("CREATE ALARM " + time.toString());
    service.createAlarm(time);
  }

  @Override
  public void deleteAlarm(long alarmId) throws RemoteException {
    debugToast("DELETE ALARM " + alarmId);
    service.deleteAlarm(alarmId);
  }

  @Override
  public void deleteAllAlarms() throws RemoteException {
    debugToast("DELETE ALL ALARMS");
    service.deleteAllAlarms();
  }

  @Override
  public void scheduleAlarm(long alarmId) throws RemoteException {
    debugToast("SCHEDULE ALARM " + alarmId);
    service.scheduleAlarm(alarmId);
  }

  @Override
  public void unscheduleAlarm(long alarmId) {
    debugToast("UNSCHEDULE ALARM " + alarmId);
    service.dismissAlarm(alarmId);
  }

  public void acknowledgeAlarm(long alarmId) {
    debugToast("ACKNOWLEDGE ALARM " + alarmId);
    service.acknowledgeAlarm(alarmId);
  }

  @Override
  public void snoozeAlarm(long alarmId) throws RemoteException {
    debugToast("SNOOZE ALARM " + alarmId);
    service.snoozeAlarm(alarmId);
  }

  @Override
  public void snoozeAlarmFor(long alarmId, int minutes) throws RemoteException {
    debugToast("SNOOZE ALARM " + alarmId + " for " + minutes);
    service.snoozeAlarmFor(alarmId, minutes);
  }

  private void debugToast(String message) {
    if (AppSettings.isDebugMode(context)) {
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
  }
}
