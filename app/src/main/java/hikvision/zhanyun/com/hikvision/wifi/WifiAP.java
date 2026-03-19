package hikvision.zhanyun.com.hikvision.wifi;

import static hikvision.zhanyun.com.hikvision.MainActivity.is6735;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.ResultReceiver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

import hikvision.zhanyun.com.hikvision.utils.Log;

public class WifiAP {
    private static Context mContext;
    private static String mSsid;
    private static String mPasswd; /////
    private static WifiManager mWifiManager;
    private static ConnectivityManager mConnectivityManager;
    private static WifiAP mWifiAP;

    private WifiAP(Context context, String ssid, String passwd) { /////
        mContext = Objects.requireNonNull(context);
        mSsid = Objects.requireNonNull(ssid);
        mPasswd = Objects.requireNonNull(passwd); /////
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public static WifiAP getInstance(Context context, String ssid, String passwd) { /////
        if (mWifiAP == null || mSsid == null || !mSsid.equals(ssid) || /////
                mPasswd == null || !mPasswd.equals(passwd)) { /////
            mWifiAP = new WifiAP(context, ssid, passwd); /////
        }
        return mWifiAP;
    }

    private boolean setWifiApEnabled(boolean on)
    {
        // wifi和热点不能同时开启，开启热点的时候要关闭wifi
        mWifiManager.setWifiEnabled(false);

        Log.i(Log.TAG, (on ? "开启" : "关闭") + "wifi热点 " + mSsid);
        try {
            Method enableWifi = mWifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);

            WifiConfiguration  myConfig =  new WifiConfiguration();
            myConfig.SSID = mSsid;
            myConfig.preSharedKey = mPasswd;
            myConfig.status = on ? WifiConfiguration.Status.ENABLED : WifiConfiguration.Status.DISABLED;
            myConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            myConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            myConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            myConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            myConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            myConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            myConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);

            return (boolean) enableWifi.invoke(mWifiManager, myConfig, on);
        } catch (Exception e) {
            Log.i(Log.TAG, "开启wifi热点异常：" + e.toString());
            return false;
        }
    }

    public void enable() {

        if (is6735) {
            //setWifiApEnabled(true);
            return;
        }

        Log.i(Log.TAG, "开启wifi热点 " + mSsid);
        try
        {
            WifiConfiguration wifiConfiguration = new WifiConfiguration();

            wifiConfiguration.SSID = mSsid;

            wifiConfiguration.allowedKeyManagement.set(4);

            wifiConfiguration.allowedAuthAlgorithms.set(0);

            wifiConfiguration.preSharedKey = mPasswd;

            if (mWifiManager != null)
            {
                Class<? extends WifiManager> wifiManagerClass = mWifiManager.getClass();

                Method setWifiApConfigurationMethod =
                        wifiManagerClass.getMethod("setWifiApConfiguration", WifiConfiguration.class);

                setWifiApConfigurationMethod.invoke(mWifiManager, wifiConfiguration);
            }

            if (mConnectivityManager != null)
            {
                ResultReceiver dummyResultReceiver = new ResultReceiver(null);

                String fieldName = "mService";

                Field internalConnectivityManagerField =
                        ConnectivityManager.class.getDeclaredField(fieldName);

                internalConnectivityManagerField.setAccessible(true);

                Object internalConnectivityManagerObject =
                        internalConnectivityManagerField.get(mConnectivityManager);

                if (internalConnectivityManagerObject != null)
                {
                    String className = internalConnectivityManagerObject.getClass().getName();

                    Class internalConnectivityManagerClass = Class.forName(className);

                    String methodName = "startTethering";

                    Method startTetheringMethod = internalConnectivityManagerClass.getDeclaredMethod(
                            methodName, int.class, ResultReceiver.class, boolean.class, String.class);

                    startTetheringMethod.invoke(internalConnectivityManagerObject,
                            0, dummyResultReceiver, false, mContext.getPackageName());
                }
            }
        }
        catch (Exception e)
        {
            Log.i(Log.TAG, "开启wifi热点异常：" + e);
        }
    }

    public void disable()
    {
        if (is6735) {
            //setWifiApEnabled(false);
            return;
        }

        Log.i(Log.TAG, "关闭wifi热点 " + mSsid);
        try
        {
            if (mConnectivityManager != null)
            {
                String fieldName = "mService";

                Field internalConnectivityManagerField =
                        ConnectivityManager.class.getDeclaredField(fieldName);

                internalConnectivityManagerField.setAccessible(true);

                Object internalConnectivityManagerObject =
                        internalConnectivityManagerField.get(mConnectivityManager);

                if (internalConnectivityManagerObject != null)
                {
                    String className = internalConnectivityManagerObject.getClass().getName();

                    Class internalConnectivityManagerClass = Class.forName(className);

                    String methodName = "stopTethering";

                    Method stopTethering = internalConnectivityManagerClass.getDeclaredMethod(
                            methodName, int.class, String.class);

                    stopTethering.invoke(internalConnectivityManagerObject,
                            0, mContext.getPackageName());
                }
            }
        }
        catch (Exception e)
        {
            Log.i(Log.TAG, "关闭wifi热点异常：" + e);
        }
    }

    public boolean isEnabled() {
        try {
            Method method = mWifiManager.getClass().getMethod("getWifiApState");
            int state = (int) method.invoke(mWifiManager);
            return state == 12 || state == 13;
        } catch (Exception e) {
            Log.i(Log.TAG, "查询wifi热点状态异常：" + e);
        }
        return false;
    }
}
