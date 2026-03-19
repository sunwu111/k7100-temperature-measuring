package hikvision.zhanyun.com.hikvision.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import hikvision.zhanyun.com.hikvision.utils.Log;
import com.guide.sdk.util.DeviceUtils;
import com.hwangjr.rxbus.RxBus;

public class UsbPermissionReceiver extends BroadcastReceiver {

    private static UsbPermissionReceiver receiver = null;
    public static final String USB_PERMISSION_GRANTED = "usb_permission_granted";

    public static void register(Context context) {
        Log.i(Log.TAG, "注册红外申请权限的广播：" + receiver);
        if (receiver != null) {
            return;
        }
        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(DeviceUtils.ACTION_USB_PERMISSION);
        receiver = new UsbPermissionReceiver();
        context.registerReceiver(receiver, usbFilter);
    }

    public static void unregister(Context context) {
        Log.i(Log.TAG, "注销红外申请权限的广播：" + receiver);
        if (receiver == null) {
            return;
        }
        context.unregisterReceiver(receiver);
        receiver = null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(Log.TAG, "红外机芯接收到申请权限的广播：" + intent);
        String action = intent.getAction();
        UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (usbDevice == null) return;
        if (DeviceUtils.ACTION_USB_PERMISSION.equals(action)) { //申请权限的广播
            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (granted) { //获得权限
                RxBus.get().post(USB_PERMISSION_GRANTED, USB_PERMISSION_GRANTED);
                Log.i(Log.TAG, "获得红外机芯的USB权限");
            } else { //拒绝权限
                Log.i(Log.TAG, "拒绝红外机芯的USB权限");
            }
        }
    }
}
