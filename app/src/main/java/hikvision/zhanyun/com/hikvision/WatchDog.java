package hikvision.zhanyun.com.hikvision;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.IBinder;

import java.util.Calendar;
import java.util.Date;

import hikvision.zhanyun.com.hikvision.utils.Log;
import lyh.Utils;

import static hikvision.zhanyun.com.hikvision.MainActivity.LOG_FILE;
import static lyh.Utils.PERIOD_MINUTE;
import static lyh.Utils.addTime;
import static lyh.Utils.dateFromString;
import static lyh.Utils.exec;
import static lyh.Utils.launchApk;
import static lyh.Utils.reloadAfterCrash;
import static lyh.Utils.roundUp;
import static lyh.Utils.su;

public class WatchDog extends Service {
    public static final String WATCH_DOG = "ZHJINRUI.WATCHDOG";
    private static final String MONITORED_PACKAGE_NAME = "hikvision.zhanyun.com.hikvision";

    private static PendingIntent watchdog;
    private static int rebootTimes;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.w(Log.TAG, "看门狗启动 ...");

        // 注册闹钟回调
        registerReceiver(onIntentReceive, new IntentFilter(WATCH_DOG));

        reloadAfterCrash(this, null, LOG_FILE);
        if (watchdog == null)
            watchdog = PendingIntent.getBroadcast(this, 0, new Intent(WATCH_DOG), PendingIntent.FLAG_UPDATE_CURRENT);
        alarmInitTaskWatchDog("00:00:00", PERIOD_MINUTE * 2, watchdog, "看门狗");
    }


    public void alarmInitTaskWatchDog(String start, long msecPeriod, PendingIntent intent, String source) {
        try {
            Date date = dateFromString(start);
            long begin = date.getTime();
            long now = new Date().getTime();
            if (now > begin) // 如果当前时间 > 定时开始的时刻，需要调整到下一次开始的时刻开始！
            {
                long diff = now - begin;
                date = addTime(date, msecPeriod * roundUp(1.0 * diff / msecPeriod));
            }
            if (date.getTime() == now)  // 如果当前时间和闹钟设定的时间相同，可能同时刻触发了多次，调整到下一次
                date = addTime(date, msecPeriod);

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            Log.i(Log.TAG, "闹钟: " + start + " 间隔: " + String.valueOf(msecPeriod / 1000) + "秒, 来源: " + source + " --> " + date.toString());
            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(calendar.getTimeInMillis(), intent);
                alarmManager.setAlarmClock(alarmClockInfo, intent);
            } else */if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), intent);
            } else {
                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), intent);
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "看门狗闹钟初始化异常：" + e.getMessage());
        }
    }


    private BroadcastReceiver onIntentReceive = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action = intent.getAction();
                Log.i(Log.TAG, "收到看门狗通知: " + action);

                alarmInitTaskWatchDog("00:00:00", PERIOD_MINUTE * 5, watchdog, "看门狗");
                if (action.equals(WATCH_DOG)) {
                    monitorProcesses();
                }
            } catch (Exception e) {
                Log.i(Log.TAG, "看门狗处理异常：" + e.getMessage());
            }
        }
    };


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.e(Log.TAG, "看门狗停止");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    /**
     * 监控进程
     */
    public void monitorProcesses() {
        try {
            ApplicationInfo info = getApplication().getApplicationInfo();
            boolean appAlive = Utils.isAppActivity(this, info.packageName);
            int result = su(String.format("busybox ps -A | grep -w '%s$'", info.packageName));
            Log.i(Log.TAG, "应用程序状态：" + appAlive + ", 进程状态：" + (result == 0 ? "运行" : "退出"));
            if (!appAlive || result != 0) {
                rebootTimes++;
                launchApk(this, info.packageName);

                Log.e(Log.TAG, "主进程退出，重启应用");
            }
            if (rebootTimes > 3) { // 有时进程内部jni程序挂掉会变成僵尸进程，apk重新启动失败，这时要重启系统了
                su("ps -A|grep hikvision >> " + LOG_FILE);
                Log.e(Log.TAG, "重启系统");
                su("reboot");
                exec("su reboot");
            }
        } catch (Exception e) {
            Log.e(Log.TAG, "看门狗处理应用异常：" + e.getMessage());
        }
    }
}


