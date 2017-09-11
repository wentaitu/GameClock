package cn.edu.cqupt.gameclock.service;

import java.util.LinkedList;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import cn.edu.cqupt.gameclock.NotificationServiceInterface;

/**
 * Created by wentai on 17-8-21.
 */

public class NotificationServiceBinder {
  private Context context;
  private NotificationServiceInterface notify;
  private LinkedList<ServiceCallback> callbacks;

  public NotificationServiceBinder(Context context) {
    this.context = context;
    this.callbacks = new LinkedList<>();
  }

  public void bind() {
    final Intent serviceIntent = new Intent(context, NotificationService.class);
    if (!context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
      throw new IllegalStateException("Unable to bind to NotificationService.");
    }
  }

  public void unbind() {
    context.unbindService(serviceConnection);
    notify = null;
  }

  public interface ServiceCallback {
    void run(NotificationServiceInterface service);
  }

  final private ServiceConnection serviceConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      notify = NotificationServiceInterface.Stub.asInterface(service);
      while (callbacks.size() > 0) {
        ServiceCallback callback = callbacks.remove();
        callback.run(notify);
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      notify = null;
    }
  };

  public void call(ServiceCallback callback) {
    if (notify != null) {
      callback.run(notify);
    } else {
      callbacks.offer(callback);
    }
  }

  public void acknowledgeCurrentNotification(final int snoozeMinutes) {
    call(new ServiceCallback() {
      @Override
      public void run(NotificationServiceInterface service) {
        try {
          service.acknowledgeCurrentNotification(snoozeMinutes);
        } catch (RemoteException e) {
          e.printStackTrace();
        }
      }
    });
  }
}
