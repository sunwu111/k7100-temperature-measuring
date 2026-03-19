package hikvision.zhanyun.com.hikvision;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

import hikvision.zhanyun.com.hikvision.utils.Log;

/**
 * Created by ZY004Engineer on 2019/1/2.
 */

public class AutoStart extends BroadcastReceiver {
    public static final String ACTION_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    public static final String ACTION_TIME_CHANGED = "com.zhjinrui.spgp.TIME_CHANGED";

    private void doSmsAction(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        Object[] pdus = (Object[]) bundle.get("pdus");
        SmsMessage[] messages = new SmsMessage[pdus.length];
        for (int i = 0; i < messages.length; i++) {
            messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
            String address = messages[i].getOriginatingAddress();
            String body = messages[i].getDisplayMessageBody();
            Log.i(Log.TAG, "收到短信: " + address + "=>" + body);

            Intent myIntent = new Intent(MainActivity.ACTION_SMS);
            myIntent.putExtra(MainActivity.SMS_ADDRESS, address);
            myIntent.putExtra(MainActivity.SMS_BODY, body);
            context.sendBroadcast(myIntent);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(Log.TAG, "广播通知: " + action);

        // 开机消息，自动启动
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Intent run = new Intent(context, MainActivity.class);
            run.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(run);
        } else if (action.equals(ACTION_SMS_RECEIVED)) {
            doSmsAction(context, intent);
        } else if (action.equals(Intent.ACTION_TIME_CHANGED)) {
            doTimeChanged(context, intent);
        }
    }

    private void doTimeChanged(Context context, Intent intent) {
        context.sendBroadcast(new Intent(ACTION_TIME_CHANGED));
    }
}
