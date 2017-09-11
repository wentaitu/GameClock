package cn.edu.cqupt.gameclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import cn.edu.cqupt.gameclock.service.AlarmClockService;

/**
 * Created by wentai on 17-8-21.
 */

public class ReceiverDeviceBoot extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {

    if (intent.getAction().equals(Intent.ACTION_PACKAGE_REPLACED)) {
      if (!intent.getData().getSchemeSpecificPart().equals(context.getPackageName())) {
        return;
      }
    }
    Intent i = new Intent(context, AlarmClockService.class);
    i.putExtra(AlarmClockService.COMMAND_EXTRA, AlarmClockService.COMMAND_DEVICE_BOOT);
    context.startService(i);
  }

}
