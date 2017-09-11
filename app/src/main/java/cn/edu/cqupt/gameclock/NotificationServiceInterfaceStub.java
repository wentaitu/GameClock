package cn.edu.cqupt.gameclock;

import cn.edu.cqupt.gameclock.service.NotificationService;
import cn.edu.cqupt.gameclock.service.NotificationService.NoAlarmsException;

import android.content.Context;
import android.os.RemoteException;
import android.widget.Toast;

public class NotificationServiceInterfaceStub extends NotificationServiceInterface.Stub {
  private NotificationService service;

  public NotificationServiceInterfaceStub(NotificationService service) {
    this.service = service;
  }

  @Override
  public long currentAlarmId() throws RemoteException {
    try {
      return service.currentAlarmId();
    } catch (NoAlarmsException e) {
      throw new RemoteException();
    }
  }

  public int firingAlarmCount() throws RemoteException {
    return service.firingAlarmCount();
  }

  @Override
  public float volume() throws RemoteException {
    return service.volume();
  }

  @Override
  public void acknowledgeCurrentNotification(int snoozeMinutes) throws RemoteException {
    debugToast("STOP NOTIFICATION");
    try {
      service.acknowledgeCurrentNotification(snoozeMinutes);
    } catch (NoAlarmsException e) {
      throw new RemoteException();
    }
  }

  private void debugToast(String message) {
    Context context = service.getApplicationContext();
    if (AppSettings.isDebugMode(context)) {
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
  }
}
