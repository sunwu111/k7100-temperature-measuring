package hikvision.zhanyun.com.hikvision.device.iray;

import static lyh.Utils.PERIOD_SECOND;

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

    public IROverProtect(IRCMD ircmd, int threshold) {
        this.ircmd = ircmd;
        this.threshold = threshold;
        this.mode = ExposureMode.Normal;
    }

    private int counter = 0;
    private float avgNUC = 0;
    public void avoidOverexposure(float nuc) {
        if (counter < 25) { // 1秒钟统计一次NUC平均值，防止该值波动导致的误判
            avgNUC += nuc;
            counter++;
            return;
        }

        avgNUC /= counter;


        if (IRRegionTemp.unitTemperature != 1){   // 华氏度的时候不进行操作
            switch (mode) {
                case Normal:
                    if (avgNUC > threshold && (System.currentTimeMillis() - runTimeStart) > 2 * PERIOD_SECOND) {
                        Log.i(Log.TAG, "开启防灼伤保护模式，当前NUC值：" + avgNUC + "，门限值：" + threshold);
                        mode = ExposureMode.Protection;
                        runTimeStart = System.currentTimeMillis();
                    }
                    break;
                case Protection:
                    if ((System.currentTimeMillis() - runTimeStart) > 10 * PERIOD_SECOND) {
                        Log.i(Log.TAG, "取消防灼伤保护模式，当前NUC值：" + avgNUC + "，门限值：" + threshold);
                        mode = ExposureMode.Normal;
                        runTimeStart = System.currentTimeMillis();
                    }
                    break;
            }
            avgNUC = 0;
            counter = 0;
        }

    }

    public ExposureMode getMode() {
        return mode;
    }

    public float getNUC() {
        return avgNUC;
    }
}
