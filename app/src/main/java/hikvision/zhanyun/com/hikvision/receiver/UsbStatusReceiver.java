package hikvision.zhanyun.com.hikvision.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import hikvision.zhanyun.com.hikvision.utils.Log;
import com.hwangjr.rxbus.RxBus;

public class UsbStatusReceiver extends BroadcastReceiver {

    private static UsbStatusReceiver receiver = null;

    public static final String DEVICE_ATTACHED = "device_attached";
    public static final String DEVICE_DETACHED = "device_detached";


    public static void register(Context context) {
        Log.i(Log.TAG, "注册高德红外监听USB的广播：" + receiver);
        if (receiver != null) {
            return;
        }
        IntentFilter usbFilter = new IntentFilter();
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        usbFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        receiver = new UsbStatusReceiver();
        context.registerReceiver(receiver, usbFilter);
    }

    public static void unregister(Context context) {
        Log.i(Log.TAG, "注销高德红外监听USB的广播：" + receiver);
        if (receiver == null) {
            return;
        }
        context.unregisterReceiver(receiver);
        receiver = null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(Log.TAG, "高德红外机芯接收到监听USB的广播：" + intent);
        String action = intent.getAction();
        UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (usbDevice == null) return;
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            //设备连接
            Log.i(Log.TAG, "高德红外机芯USB连接");
//            if (DeviceUtils.isSupportedGuideDevice(usbDevice)) {
//                AppUtils.exitApp();
//                RxBus.get().post(DEVICE_ATTACHED, DEVICE_ATTACHED);
                /*UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                boolean hasPermission = usbManager.hasPermission(usbDevice);

                if (!hasPermission) {
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    *//*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        flags |= PendingIntent.FLAG_MUTABLE;
                    }*//*
                    int requestCode = 0;
                    Intent _intent = new Intent(ACTION_USB_PERMISSION);
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, _intent, flags);
                    usbManager.requestPermission(usbDevice, pendingIntent);
                    Logger.d(TAG, "暂无访问高德红外机芯的USB权限，请授权！");
                }*/
//            }
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            //设备拔出
            Log.i(Log.TAG, "高德红外机芯USB拔出");
            RxBus.get().post(DEVICE_DETACHED, DEVICE_DETACHED);
        }

    }
}
