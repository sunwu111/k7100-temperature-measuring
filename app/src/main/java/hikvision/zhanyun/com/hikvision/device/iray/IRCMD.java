package hikvision.zhanyun.com.hikvision.device.iray;

import android.os.SystemClock;
import androidx.annotation.NonNull;

import com.serenegiant.usb.UVCCamera;

public class IRCMD {
    private final UVCCamera uvcCamera;
    private VideoMode videoMode;

    public class CalibrationParam {
        public float tempComp;
        public float refTemp;
        public float envTemp;
        public float envHumi;
        public float objEmis;
        public short objDist;
        public String softVersion;

        public String toString() {
            return String.format("温度补偿:%.2f, 反射温度:%.2f, 环境温度:%.2f, 环境湿度:%.2f, 发射率:%.2f, 测试距离: %d, 产品固件版本:%s",
                    tempComp, refTemp, envTemp, envHumi, objEmis, objDist, softVersion);
        }
    }

    /*
    * 这两种模式艾睿也说不清楚有什么区别，测试8005效果要好些
    * */
    public enum VideoMode {
        MODE_8004,
        MODE_8005
    }

    public enum Color {
        WHITE_HOT,
        BLACK_HOT,
        IRON_RED,
        MELT_STONE,
        RAINBOW,
        IRON_GRAY,
        HOT_RED,
        RAINBOW2
    }


    public IRCMD(@NonNull UVCCamera uvcCamera) {
        this.uvcCamera = uvcCamera;
    }

    public void shutterCalibration() { uvcCamera.setZoom(0x8000); }

    public void changePalette(int color) {
        switch (videoMode) {
            case MODE_8004:
                uvcCamera.changePalette(color);
                break;
            case MODE_8005:
                uvcCamera.setZoom(0x8800 + color);
                break;
        }
    }

    public void setOutputMode(VideoMode mode) {
        videoMode = mode;
        switch (videoMode) {
            case MODE_8004:
                uvcCamera.setZoom(0x8004);
                break;
            case MODE_8005:
                uvcCamera.setZoom(0x8005);
                //uvcCamera.changePalette(-1); // 不知道为啥，艾睿sample里就是这样，否则视频会不正常
                break;
        }
    }

    // 常温段测温  -20℃~+120℃(可扩展至 550℃)
    public void setTemperatureNormal() { uvcCamera.setZoom(0x8020); }

    public void setTemperatureExtension() { uvcCamera.setZoom(0x8021); }

    // 设置视频输出格式为RGBX
    public void setOutputRGBX() { uvcCamera.setZoom(0x8004); }

    // 设置温度补偿
    public void setCompensation(float value) { sendFloatCommand(0, value); }

    // 设置反射温度
    public void setReflection(float value) { sendFloatCommand(1 * 4, value); }

    // 设置环境温度
    public void setEnvirTemp(float value) { sendFloatCommand(2 * 4, value); }

    // 设置湿度
    public void setHumidity(float value) { sendFloatCommand(3 * 4, value); }

    // 设置发射率
    public void setEmissivity(float value) { sendFloatCommand(4 * 4, value); }

    // 设置距离参数
    public void setDistance(short distance) { sendShortCommand(5 * 4, distance); }

    public static float getFloat(byte[] b, int index) {
        int l;
        l = b[index + 0];
        l &= 0xff;
        l |= ((long) b[index + 1] << 8);
        l &= 0xffff;
        l |= ((long) b[index + 2] << 16);
        l &= 0xffffff;
        l |= ((long) b[index + 3] << 24);
        return Float.intBitsToFloat(l);
    }

    public static short getShort(byte[] b, int index) {
        return (short) (((b[index + 1] << 8) | b[index + 0] & 0xff));
    }

    public CalibrationParam getCalibrationParam() {
        CalibrationParam param = new CalibrationParam();
        byte[] tempPara = uvcCamera.getByteArrayTemperaturePara(128);
        param.tempComp = getFloat(tempPara, 0);
        param.refTemp = getFloat(tempPara, 4);
        param.envTemp = getFloat(tempPara, 8);
        param.envHumi = getFloat(tempPara, 12);
        param.objEmis = getFloat(tempPara, 16);
        param.objDist = getShort(tempPara, 20);
        param.softVersion = new String(tempPara, 128 - 16, 16);
        return param;
    }

    private void sendFloatCommand(int position, float value) {
        int intValue = Float.floatToIntBits(value);

        for (int i = 0; i < Float.BYTES; i++) {
            uvcCamera.setZoom(((position + i) << 8) | (0xFF & intValue));
            SystemClock.sleep(20);
            intValue >>= 8;
        }

        uvcCamera.whenShutRefresh();
        SystemClock.sleep(40);
        uvcCamera.setZoom(0x80FF);
        SystemClock.sleep(20);
    }

    private void sendShortCommand(int position, short value) {
        int intValue = value;

        for (int i = 0; i < Short.BYTES; i++) {
            uvcCamera.setZoom(((position + i) << 8) | (0xFF & intValue));
            SystemClock.sleep(20);
            intValue >>= 8;
        }

        uvcCamera.whenShutRefresh();
        SystemClock.sleep(20);
        uvcCamera.setZoom(0x80FF);
        SystemClock.sleep(20);
    }
}
