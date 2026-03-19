package hikvision.zhanyun.com.hikvision.device.guide;

import androidx.annotation.NonNull;

import com.guide.sdk.GuideInterface;
import com.guide.sdk.bean.MTUserParam;
import com.guide.sdk.bean.DeviceStatusInfo;

public class IRCMD {
    private GuideInterface mGuideInterface;
    private MTUserParam mCoinMTParam = new MTUserParam();
    private DeviceStatusInfo mCoinDeviceInfo = new DeviceStatusInfo();

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

    public enum Color {
        WHITE_HOT,
        MELT_STONE,
        IRON_RED,
        HOT_IRON,
        MEDICAL,
        ARCTIC,
        RAINBOW,
        RAINBOW2,
        REDDENING,
        BLACK_HOT,
    }

    public IRCMD(@NonNull GuideInterface mGuideInterface) { this.mGuideInterface = mGuideInterface; }

    // 常温段测温  -20℃~+150℃(可扩展至 550℃)
    public void setTemperatureNormal() {  }

    public void setTemperatureExtension() {  }

    public CalibrationParam getCalibrationParam() {
        CalibrationParam param = new CalibrationParam();
        param.tempComp = 0.0f;
        param.refTemp = mCoinMTParam.getReflectedTemper() / 10.0f;
        param.envTemp = mCoinMTParam.getAtmosphericTemper() / 10.0f;
        param.envHumi = mCoinMTParam.getRelHum();
        param.objEmis = mCoinMTParam.getEmiss() / 100.0f;
        param.objDist = (short) (mCoinMTParam.getDistance() / 10.0f);
        param.softVersion = mCoinDeviceInfo.getVersionCode();
        return param;
    }
}
