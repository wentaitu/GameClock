package cn.edu.cqupt.gameclock;

import java.util.TreeMap;

import android.content.Context;
import android.os.PowerManager;

/**
 * Created by wentai on 17-8-21.
 */

public class WakeLock {
  public static class WakeLockException extends Exception {
    private static final long serialVersionUID = 1L;
    public WakeLockException(String e) {
      super(e);
    }
  }

  private static final TreeMap<Long, PowerManager.WakeLock> wakeLocks =
    new TreeMap<>();

  public static void acquire(Context context, long alarmId) throws WakeLockException {
    if (wakeLocks.containsKey(alarmId)) {
      throw new WakeLockException("id: " + alarmId);
    }

    PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
        PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
        "id " + alarmId);
    wakeLock.setReferenceCounted(false);
    wakeLock.acquire();

    wakeLocks.put(alarmId, wakeLock);
  }

  public static void assertHeld(long alarmId) throws WakeLockException {
    PowerManager.WakeLock wakeLock = wakeLocks.get(alarmId);
    if (wakeLock == null || !wakeLock.isHeld()) {
      throw new WakeLockException("id: " + alarmId);
    }
  }

  public static void assertNoneHeld() throws WakeLockException {
    for (PowerManager.WakeLock wakeLock : wakeLocks.values()) {
      if (wakeLock.isHeld()) {
        throw new WakeLockException("E");
      }
    }
  }

  public static void release(long alarmId) throws WakeLockException {
    assertHeld(alarmId);
    PowerManager.WakeLock wakeLock = wakeLocks.remove(alarmId);
    wakeLock.release();
  }
}
