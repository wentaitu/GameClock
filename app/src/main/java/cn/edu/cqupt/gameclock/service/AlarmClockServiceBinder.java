package cn.edu.cqupt.gameclock.service;

import java.util.LinkedList;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import cn.edu.cqupt.gameclock.AlarmClockInterface;
import cn.edu.cqupt.gameclock.AlarmTime;
/**
 * Created by wentai on 17-8-15.
 */

public class AlarmClockServiceBinder {
    private Context context;
    private AlarmClockInterface clock;
    private LinkedList<ServiceCallback> callbacks;

    public static final long NO_ALARM_ID = 0;

    public AlarmClockServiceBinder(Context context) {
      this.context = context;
      this.callbacks = new LinkedList<>();
    }

    public AlarmClockInterface clock() {
      return clock;
    }

    public void bind() {
        final Intent serviceIntent = new Intent(context, AlarmClockService.class);
        if (!context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
            throw new IllegalStateException("无法绑定AlarmClockService");
        }
    }

    public void unbind() {
        context.unbindService(serviceConnection);
        clock = null;
    }

    private interface ServiceCallback {
        void run() throws RemoteException;
    }

    final private ServiceConnection serviceConnection = new ServiceConnection() {
      @Override
      public void onServiceConnected(ComponentName name, IBinder service) {
        clock = AlarmClockInterface.Stub.asInterface(service);
        while (callbacks.size() > 0) {
          ServiceCallback callback = callbacks.remove();
          try {
            callback.run();
          } catch (RemoteException e) {
            e.printStackTrace();
          }
        }
      }
      @Override
      public void onServiceDisconnected(ComponentName name) {
        clock = null;
      }
    };

    private void runOrDefer(ServiceCallback callback) {
      if (clock != null) {
        try {
          callback.run();
        } catch (RemoteException e) {
          e.printStackTrace();
        }
      } else {
        callbacks.offer(callback);
      }
    }

    public long resurrectAlarm(AlarmTime time, String alarmName, boolean enabled) {
      long id = NO_ALARM_ID;

      try {
        id = clock.resurrectAlarm(time, alarmName, enabled);
      } catch (RemoteException e) {
        e.printStackTrace();
      }

      return id;
    }

    public void createAlarm(final AlarmTime time) {
      runOrDefer(new ServiceCallback() {
        @Override
        public void run() throws RemoteException {
          clock.createAlarm(time);
        }
      });
    }

    public void deleteAlarm(final long alarmId) {
      runOrDefer(new ServiceCallback() {
        @Override
        public void run() throws RemoteException {
          clock.deleteAlarm(alarmId);
        }
      });
    }

    public void deleteAllAlarms() {
      runOrDefer(new ServiceCallback() {
        @Override
        public void run() throws RemoteException {
          clock.deleteAllAlarms();
        }
      });
    }

    public void scheduleAlarm(final long alarmId) {
      runOrDefer(new ServiceCallback() {
        @Override
        public void run() throws RemoteException {
          clock.scheduleAlarm(alarmId);
        }
      });
    }

    public void unscheduleAlarm(final long alarmId) {
      runOrDefer(new ServiceCallback() {
        @Override
        public void run() throws RemoteException {
          clock.unscheduleAlarm(alarmId);
        }
      });
    }

    public void acknowledgeAlarm(final long alarmId) {
      runOrDefer(new ServiceCallback() {
        @Override
        public void run() throws RemoteException {
          clock.acknowledgeAlarm(alarmId);
        }
      });
    }

    public void snoozeAlarmFor(final long alarmId, final int minutes) {
      runOrDefer(new ServiceCallback() {
        @Override
        public void run() throws RemoteException {
          clock.snoozeAlarmFor(alarmId, minutes);
        }
      });
    }
}
