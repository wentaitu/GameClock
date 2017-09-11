package cn.edu.cqupt.gameclock.service;

import java.util.LinkedList;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

import com.hurteng.sandstorm.MainActivity;

import cn.edu.cqupt.gameclock.alarm.AlarmInfo;
import cn.edu.cqupt.gameclock.alarm.AlarmSettings;
import cn.edu.cqupt.gameclock.alarm.AlarmUtil;
import cn.edu.cqupt.gameclock.AppSettings;
import cn.edu.cqupt.gameclock.db.DbAccessor;
import cn.edu.cqupt.gameclock.NotificationServiceInterfaceStub;
import cn.edu.cqupt.gameclock.R;
import cn.edu.cqupt.gameclock.WakeLock;

/**
 * Created by wentai on 17-8-21.
 */

public class NotificationService extends Service {
  public class NoAlarmsException extends Exception {
    private static final long serialVersionUID = 1L;
  }

  private enum MediaSingleton {
    INSTANCE;

    private MediaPlayer mediaPlayer = null;
    private Ringtone fallbackSound = null;
    private Vibrator vibrator = null;
    private int systemNotificationVolume = 0;

    MediaSingleton() {
      mediaPlayer = new MediaPlayer();
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
    }

    private void normalizeVolume(Context c, float startVolume) {
      final AudioManager audio =
        (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
      systemNotificationVolume =
          audio.getStreamVolume(AudioManager.STREAM_ALARM);
      audio.setStreamVolume(AudioManager.STREAM_ALARM,
          audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
      setVolume(startVolume);
    }

    private void setVolume(float volume) {
      mediaPlayer.setVolume(volume, volume);
    }

    private void resetVolume(Context c) {
      final AudioManager audio =
        (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
      audio.setStreamVolume(AudioManager.STREAM_ALARM, systemNotificationVolume,
              0);
    }

    private void useContext(Context c) {
      fallbackSound = RingtoneManager.getRingtone(c, AlarmUtil.getDefaultAlarmUri());
      if (fallbackSound == null) {
        Uri superFallback = RingtoneManager.getValidRingtoneUri(c);
        fallbackSound = RingtoneManager.getRingtone(c, superFallback);
      }

      if (fallbackSound != null) {
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
              fallbackSound.setStreamType(AudioManager.STREAM_ALARM);
          } else {
              fallbackSound.setAudioAttributes(new AudioAttributes.Builder()
                      .setUsage(AudioAttributes.USAGE_ALARM)
                      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                      .build());
          }
      }

      vibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void ensureSound() {
      if (!mediaPlayer.isPlaying() &&
          fallbackSound != null && !fallbackSound.isPlaying()) {
        fallbackSound.play();
      }
    }

    private void vibrate() {
      if (vibrator != null) {
        vibrator.vibrate(new long[] {500, 500}, 0);
      }
    }

    public void play(Context c, Uri tone) {
      mediaPlayer.reset();
      mediaPlayer.setLooping(true);
      try {
        mediaPlayer.setDataSource(c, tone);
        mediaPlayer.prepare();
        mediaPlayer.start();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    public void stop() {
      mediaPlayer.stop();
      if (vibrator != null) {
        vibrator.cancel();
      }
      if (fallbackSound != null) {
        fallbackSound.stop();
      }
    }

      public void release() {
          mediaPlayer.release();
      }
  }

  // Data
  private LinkedList<Long> firingAlarms;
  private AlarmClockServiceBinder service;
  private DbAccessor db;
  // Notification tools
  private NotificationManager manager;
  private PendingIntent notificationActivity;
  private Handler handler;
  private VolumeIncreaser volumeIncreaseCallback;
  private Runnable soundCheck;
  private Runnable notificationBlinker;
  private Runnable autoCancel;

  @Override
  public IBinder onBind(Intent intent) {
    return new NotificationServiceInterfaceStub(this);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    firingAlarms = new LinkedList<>();
    service = new AlarmClockServiceBinder(getApplicationContext());
    service.bind();
    db = new DbAccessor(getApplicationContext());

    MediaSingleton.INSTANCE.useContext(getApplicationContext());

    manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
    notificationActivity = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

    handler = new Handler();
    volumeIncreaseCallback = new VolumeIncreaser();
    soundCheck = new Runnable() {
      @Override
      public void run() {
        MediaSingleton.INSTANCE.ensureSound();

        long next = AlarmUtil.millisTillNextInterval(AlarmUtil.Interval.SECOND);
        handler.postDelayed(soundCheck, next);
      }
    };
    notificationBlinker = new Runnable() {
      @Override
      public void run() {
        String notifyText;
        try {
          AlarmInfo info = db.readAlarmInfo(currentAlarmId());
          notifyText = (info == null || info.getName() == null) ? "" : info.getName();
          if (notifyText.equals("") && info != null) {
            notifyText = info.getTime().localizedString(getApplicationContext());
          }
        } catch (NoAlarmsException e) {
          return;
        }

          NotificationCompat.Builder builder = new NotificationCompat.Builder(
                  getApplicationContext());

          Notification notification = builder
                  .setContentIntent(notificationActivity)
                  .setSmallIcon(R.drawable.ic_stat_notify_alarm)
                  .setContentTitle(notifyText)
                  .setContentText("")
                  .setColor(ContextCompat.getColor(getApplicationContext(),
                          R.color.notification_color))
                  .build();
          notification.flags |= Notification.FLAG_ONGOING_EVENT;

        manager.notify(AlarmClockService.NOTIFICATION_BAR_ID, notification);

        long next = AlarmUtil.millisTillNextInterval(AlarmUtil.Interval.SECOND);
        handler.postDelayed(notificationBlinker, next);
      }
    };
    autoCancel = new Runnable() {
      @Override
      public void run() {
        try {
          acknowledgeCurrentNotification(0);
        } catch (NoAlarmsException e) {
          return;
        }
        Intent notifyActivity = new Intent(getApplicationContext(), MainActivity.class);
        notifyActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notifyActivity.putExtra(MainActivity.TIMEOUT_COMMAND, true);
        startActivity(notifyActivity);
      }
    };
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    db.closeConnections();
    service.unbind();

    boolean debug = AppSettings.isDebugMode(getApplicationContext());
    if (debug && firingAlarms.size() != 0) {
      throw new IllegalStateException("Error!");
    }
    try {
      WakeLock.assertNoneHeld();
    } catch (WakeLock.WakeLockException e) {
      if (debug) { throw new IllegalStateException(e.getMessage()); }
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    handleStart(intent);
    return START_NOT_STICKY;
  }

  private void handleStart(Intent intent) {
    if (intent != null && intent.getData() != null) {
      long alarmId = AlarmUtil.alarmUriToId(intent.getData());
      try {
        WakeLock.assertHeld(alarmId);
      } catch (WakeLock.WakeLockException e) {
        if (AppSettings.isDebugMode(getApplicationContext())) {
          throw new IllegalStateException(e.getMessage());
        }
      }
      Intent notifyActivity = new Intent(getApplicationContext(), MainActivity.class);
      notifyActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(notifyActivity);

      boolean firstAlarm = firingAlarms.size() == 0;
      if (!firingAlarms.contains(alarmId)) {
        firingAlarms.add(alarmId);
      }

      if (firstAlarm) {
        soundAlarm(alarmId);
      }
    }
  }

  public long currentAlarmId() throws NoAlarmsException {
    if (firingAlarms.size() == 0) {
      throw new NoAlarmsException();
    }
    return firingAlarms.getFirst();
  }

  public int firingAlarmCount() {
    return firingAlarms.size();
  }

  public float volume() {
    return volumeIncreaseCallback.volume();
  }

  public void acknowledgeCurrentNotification(int snoozeMinutes) throws NoAlarmsException {
    long alarmId = currentAlarmId();
    if (firingAlarms.contains(alarmId)) {
      firingAlarms.remove(alarmId);
      if (snoozeMinutes <= 0) {
        service.acknowledgeAlarm(alarmId);
      } else {
        service.snoozeAlarmFor(alarmId, snoozeMinutes);
      }
    }
    stopNotifying();

    if (firingAlarms.size() == 0) {
      stopSelf();
    } else {
      soundAlarm(alarmId);
    }
    try {
      WakeLock.release(alarmId);
    } catch (WakeLock.WakeLockException e) {
      if (AppSettings.isDebugMode(getApplicationContext())) {
        throw new IllegalStateException(e.getMessage());
      }
    }
  }

  private void soundAlarm(long alarmId) {
    AlarmSettings settings = db.readAlarmSettings(alarmId);
    if (settings.getVibrate()) {
      MediaSingleton.INSTANCE.vibrate();
    }

    volumeIncreaseCallback.reset(settings);
    MediaSingleton.INSTANCE.normalizeVolume(
        getApplicationContext(), volumeIncreaseCallback.volume());
    MediaSingleton.INSTANCE.play(getApplicationContext(), settings.getTone());

    handler.post(volumeIncreaseCallback);
    handler.post(soundCheck);
    handler.post(notificationBlinker);
    int timeoutMillis = 60 * 1000 * AppSettings.alarmTimeOutMins(getApplicationContext());
    handler.postDelayed(autoCancel, timeoutMillis);
  }

  private void stopNotifying() {
    handler.removeCallbacks(volumeIncreaseCallback);
    handler.removeCallbacks(soundCheck);
    handler.removeCallbacks(notificationBlinker);
    handler.removeCallbacks(autoCancel);

    MediaSingleton.INSTANCE.stop();
    MediaSingleton.INSTANCE.resetVolume(getApplicationContext());
  }

  private final class VolumeIncreaser implements Runnable {
    float start;
    float end;
    float increment;

    public float volume() {
      return start;
    }

    public void reset(AlarmSettings settings) {
      start = (float) (settings.getVolumeStartPercent() / 100.0);
      end = (float) (settings.getVolumeEndPercent() / 100.0);
      increment = (end - start) / (float) settings.getVolumeChangeTimeSec();
    }

    @Override
    public void run() {
      start += increment;
      if (start > end) {
        start = end;
      }
      MediaSingleton.INSTANCE.setVolume(start);

      if (Math.abs(start - end) > (float) 0.0001) {
        handler.postDelayed(volumeIncreaseCallback, 1000);
      }
    }
  }
}
