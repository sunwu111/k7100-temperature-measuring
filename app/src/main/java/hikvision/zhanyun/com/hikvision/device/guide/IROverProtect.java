package hikvision.zhanyun.com.hikvision.device.guide;

import static lyh.Utils.PERIOD_SECOND;

import com.guide.sdk.GuideInterface;

import hikvision.zhanyun.com.hikvision.IRSetting;
import hikvision.zhanyun.com.hikvision.utils.Log;

public class IROverProtect {
    public enum ExposureMode {
        Normal,
        Protection,
    }

    private IRCMD ircmd;
    private int threshold;
    private long runTimeStart;
    private ExposureMode mode;
    private final GuideInterface mGuideInterface = GuideInterface.getInstance();

    private IRSetting.SensorConfig sensorConfig = new IRSetting.SensorConfig();


    public static double celsiusToFahrenheit(double celsius) {
        return (celsius * 9 / 5) + 32;
    }

    public IROverProtect(IRCMD ircmd, int threshold) {
        this.ircmd = ircmd;

        if (sensorConfig.measureUnit ==1 ){
            this.threshold = (int) celsiusToFahrenheit(threshold);
        }else{
            this.threshold = threshold;
        }

        this.mode = ExposureMode.Normal;
    }

    public void avoidOverexposure(float maxTemp) {


        if (IRRegionTemp.unitTemperature != 1){
            switch (mode) {
                case Normal:
                    if (maxTemp > threshold && (System.currentTimeMillis() - runTimeStart) > 2 * PERIOD_SECOND) {
                        mGuideInterface.shutter();  // 开启防灼伤保护立即打快门

                        Log.i(Log.TAG, "高德红外开启防灼伤保护模式，当前最大温度值：" + maxTemp + "℃，门限值：" + threshold);
                        mode = ExposureMode.Protection;
                        runTimeStart = System.currentTimeMillis();
                    }
                    break;
                case Protection:
                    if ((System.currentTimeMillis() - runTimeStart) > 10 * PERIOD_SECOND) {
                        Log.i(Log.TAG, "高德红外取消防灼伤保护模式，当前最大温度值：" + maxTemp + "℃，门限值：" + threshold);
                        mode = ExposureMode.Normal;
                        runTimeStart = System.currentTimeMillis();
                    }
                    break;
            }
        }


    }

    public ExposureMode getMode() {
        return mode;
    }
}
