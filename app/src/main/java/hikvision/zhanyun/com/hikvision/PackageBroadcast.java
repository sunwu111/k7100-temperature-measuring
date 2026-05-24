package hikvision.zhanyun.com.hikvision;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import hikvision.zhanyun.com.hikvision.utils.Log;

public class PackageBroadcast extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String localPkgName = context.getPackageName();//取得MyReceiver所在的App的包名
        Uri data = intent.getData();
        String installedPkgName = data == null ? "" : data.getSchemeSpecificPart();//取得安装的Apk的包名，只在该app覆盖安装后自启动
        if (action.equals(Intent.ACTION_MY_PACKAGE_REPLACED) || (action.equals(Intent.ACTION_PACKAGE_ADDED)
                || action.equals(Intent.ACTION_PACKAGE_REPLACED)) && installedPkgName.equals(localPkgName)) {
            Intent launchIntent = new Intent(context, MainActivity.class);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
        }
        Log.w(Log.TAG, "收到包安装通知: " + action);
    }
}
