package hikvision.zhanyun.com.hikvision.utils;

import static lyh.Utils.PERIOD_SECOND;
import static lyh.Utils.su;

import android.content.Context;
import android.provider.Settings;


public class SystemSettings {
    public static void bdsON(Context context) { ////////
        android.provider.Settings.Secure.putInt(
                context.getContentResolver(), android.provider.Settings.Secure.LOCATION_MODE,
                android.provider.Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
    }

    public static void bdsOFF(Context context) { ////////
        android.provider.Settings.Secure.putInt(
                context.getContentResolver(), android.provider.Settings.Secure.LOCATION_MODE,
                android.provider.Settings.Secure.LOCATION_MODE_OFF);
    }

    public static void sleepAfter(Context context, int seconds) {
        android.provider.Settings.System.putInt(
                context.getContentResolver(), android.provider.Settings.System.SCREEN_OFF_TIMEOUT,
                (int) (seconds * PERIOD_SECOND));
    }

    public static void airplaneOff(Context context) {
        /*android.provider.Settings.Global.putInt(
                context.getContentResolver(), android.provider.Settings.Global.AIRPLANE_MODE_ON, 0);

        Settings.Global.putString(context.getContentResolver(), "airplane_mode_on", "0");*/
        int mode = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
        if (mode == 1) {
            Log.i(Log.TAG, "关闭飞行模式");
            su("settings put global airplane_mode_on 0");
            su("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false");
        }
    }

    public static void airplaneOn(Context context) {
        /*android.provider.Settings.Global.putInt(
                context.getContentResolver(), android.provider.Settings.Global.AIRPLANE_MODE_ON, 1);

        Settings.Global.putString(context.getContentResolver(), "airplane_mode_on", "1");*/
        int mode = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
        if (mode == 0) {
            Log.i(Log.TAG, "打开飞行模式");
            su("settings put global airplane_mode_on 1");
            su("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true");
        }
    }
}
