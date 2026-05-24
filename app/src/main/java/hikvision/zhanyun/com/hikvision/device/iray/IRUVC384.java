package hikvision.zhanyun.com.hikvision.device.iray;

import android.content.Context;
import android.hardware.usb.UsbDevice;

import com.serenegiant.usb.USBMonitor;

import java.util.Iterator;

public class IRUVC384 {
    private USBMonitor usbMonitor;
    public IRUVC384(Context context, USBMonitor.OnDeviceConnectListener listener) {
        usbMonitor = new USBMonitor(context, listener);
    }

    public void connect() {
        if (usbMonitor != null) {
            if (!usbMonitor.isRegistered()) {
                usbMonitor.register();
            }
            for (Iterator<UsbDevice> it = usbMonitor.getDevices(); it.hasNext(); ) {
                UsbDevice device = it.next();
                //Log.i(Log.TAG, "连接设备：" + device.getProductName());
                if (usbMonitor.requestPermission(device)) {
                    break;
                }
            }
        }
    }

    public void disconnect() {
        //Log.i(Log.TAG, "主动断开设备");
        if (usbMonitor != null) {
            if (usbMonitor.isRegistered()) {
                usbMonitor.unregister();
            }
            usbMonitor.destroy();
            usbMonitor = null;
        }
    }

    //public boolean isConnect() { return usbControler != null; }

}

