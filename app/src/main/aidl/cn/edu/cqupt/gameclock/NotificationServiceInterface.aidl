package cn.edu.cqupt.gameclock;

interface NotificationServiceInterface {
  long currentAlarmId();
  int firingAlarmCount();
  float volume();
  void acknowledgeCurrentNotification(int snoozeMinutes);
}