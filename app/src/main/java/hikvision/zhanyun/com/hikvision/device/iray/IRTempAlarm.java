package hikvision.zhanyun.com.hikvision.device.iray;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.util.EnumSet;

import hikvision.zhanyun.com.hikvision.IRSetting;
import hikvision.zhanyun.com.hikvision.Settings;


// 温度预警类，每个实例对应一个测温区域
public class IRTempAlarm {
    public enum AlarmType {
        ALARM_GLOBAL_LOW_TEMPERATURE,
        ALARM_GLOBAL_HIGH_TEMPERATURE,
        ALARM_LOW_TEMPERATURE,
        ALARM_HIGH_TEMPERATURE,
        ALARM_ENV_TEMPERATURE,
        ALARM_COM_TEMPERATURE_0,
        ALARM_COM_TEMPERATURE_1,
        ALARM_COM_TEMPERATURE_BOTH,
        ALARM_COM_TEMPERATURE
    }

    private IRSetting.SensorConfig sensorConfig = new IRSetting.SensorConfig();
    private  float highThreshold;
    private  float lowThreshold;
    private static Settings.FireAlarmInfo alarmInfo = new Settings.FireAlarmInfo(); // 统计告警次数
    public IRTempAlarm(final float highThreshold, final float lowThreshold) {
        this.highThreshold = highThreshold;
        this.lowThreshold = lowThreshold;
    }


    public double fahrenheitToCelsius(double fahrenheit) {
        return (fahrenheit - 32) * 5 / 9;
    }

    public static double celsiusToFahrenheit(double celsius) {
        return (celsius * 9 / 5) + 32;
    }


    public static Settings.FireAlarmInfo GetAlarmInfo() { return alarmInfo; }

    public EnumSet<AlarmType> checkAlarms(String reason, IRRegionTemp.TemperatureSampleResult result, byte flag) { /////
        EnumSet<IRTempAlarm.AlarmType> alarms = EnumSet.noneOf(IRTempAlarm.AlarmType.class);
        if (result != null) {

//            Log.e(Log.TAG,"IRRegionTemp.unitTemperature"+IRRegionTemp.unitTemperature);
            if(IRRegionTemp.unitTemperature == 1){   // 1是华氏度
                return alarms;
            }

            // 考虑再三，这里只用最高温和高低温报警阈值进行比较
            if (result.maxTemperature > highThreshold) {
                if (reason == "全域告警") {
                    alarms.add(IRTempAlarm.AlarmType.ALARM_GLOBAL_HIGH_TEMPERATURE);
                } else {
                    /////
                    if (flag == 1 || flag == 3) {
                        alarms.add(IRTempAlarm.AlarmType.ALARM_HIGH_TEMPERATURE);
                    }
                    /////
                }
            }
            if (result.maxTemperature < lowThreshold) { /////
                if (reason == "全域告警") {
                    alarms.add(IRTempAlarm.AlarmType.ALARM_GLOBAL_LOW_TEMPERATURE);
                } else {
                    if (flag == 2 || flag == 3) {
                        alarms.add(IRTempAlarm.AlarmType.ALARM_LOW_TEMPERATURE);
                    }
                }
            }

//            Log.i(Log.TAG, String.format("%s 最高温：%.1f, 高温告警阈值：%.1f, 低温告警阈值：%.1f, 告警类型：%s", reason,
//                    result.maxTemperature, highThreshold, lowThreshold, alarms.isEmpty() ? "无" : alarms.toString()));
        }
        return alarms;
    }

    private static void drawAlarmText(Bitmap bitmap, String text, float x, float y, int color, int size) { /////
        Canvas canvas = new Canvas(bitmap);
        if (canvas != null) {
            Paint paint = new Paint();
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setAntiAlias(true);
            paint.setStrokeWidth(3);
            canvas.drawText(text, x, y, paint);
        }
    }

    public static void drawAlarmText(Bitmap bitmap, EnumSet<AlarmType> alarmTypes, float x, float y) { /////
        if (alarmTypes.isEmpty()) return;

        if (alarmTypes.contains(IRTempAlarm.AlarmType.ALARM_GLOBAL_LOW_TEMPERATURE) &&
                alarmTypes.contains(IRTempAlarm.AlarmType.ALARM_GLOBAL_HIGH_TEMPERATURE)) {
            IRTempAlarm.drawAlarmText(bitmap,"全局高低温报警", x, y, Color.WHITE, (int) (16 * Math.min((float) bitmap.getWidth() / 640, (float) bitmap.getHeight() / 512))); /////
        } else if (alarmTypes.contains(IRTempAlarm.AlarmType.ALARM_GLOBAL_LOW_TEMPERATURE)) {
            IRTempAlarm.drawAlarmText(bitmap,"全局低温报警", x, y, Color.WHITE, (int) (16 * Math.min((float) bitmap.getWidth() / 640, (float) bitmap.getHeight() / 512))); /////
        } else if (alarmTypes.contains(IRTempAlarm.AlarmType.ALARM_GLOBAL_HIGH_TEMPERATURE)) {
            IRTempAlarm.drawAlarmText(bitmap,"全局高温报警", x, y, Color.WHITE, (int) (16 * Math.min((float) bitmap.getWidth() / 640, (float) bitmap.getHeight() / 512))); /////
        } else if (alarmTypes.contains(IRTempAlarm.AlarmType.ALARM_LOW_TEMPERATURE) &&
                alarmTypes.contains(IRTempAlarm.AlarmType.ALARM_HIGH_TEMPERATURE)) {
            IRTempAlarm.drawAlarmText(bitmap,"区域高温和低温报警", x, y, Color.WHITE, (int) (16 * Math.min((float) bitmap.getWidth() / 640, (float) bitmap.getHeight() / 512))); /////
        } else if (alarmTypes.contains(IRTempAlarm.AlarmType.ALARM_LOW_TEMPERATURE)) {
            IRTempAlarm.drawAlarmText(bitmap,"区域低温报警", x, y, Color.WHITE, (int) (16 * Math.min((float) bitmap.getWidth() / 640, (float) bitmap.getHeight() / 512))); /////
        } else if (alarmTypes.contains(IRTempAlarm.AlarmType.ALARM_HIGH_TEMPERATURE)) {
            IRTempAlarm.drawAlarmText(bitmap,"区域高温报警", x, y, Color.WHITE, (int) (16 * Math.min((float) bitmap.getWidth() / 640, (float) bitmap.getHeight() / 512))); /////
        } else if (alarmTypes.contains(IRTempAlarm.AlarmType.ALARM_ENV_TEMPERATURE)) {
            IRTempAlarm.drawAlarmText(bitmap,"温升报警", x, y, Color.WHITE, (int) (16 * Math.min((float) bitmap.getWidth() / 640, (float) bitmap.getHeight() / 512))); /////
        } else if (alarmTypes.contains(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE_0)) {
            IRTempAlarm.drawAlarmText(bitmap,"电缆终端三相不平衡报警", x, y, Color.WHITE, (int) (16 * Math.min((float) bitmap.getWidth() / 640, (float) bitmap.getHeight() / 512))); /////
        } else if (alarmTypes.contains(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE_1)) {
            IRTempAlarm.drawAlarmText(bitmap,"避雷器三相不平衡报警", x, y, Color.WHITE, (int) (16 * Math.min((float) bitmap.getWidth() / 640, (float) bitmap.getHeight() / 512))); /////
        } else if (alarmTypes.contains(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE_BOTH)) {
            IRTempAlarm.drawAlarmText(bitmap,"电缆终端三相不平衡报警", x, y, Color.WHITE, (int) (16 * Math.min((float) bitmap.getWidth() / 640, (float) bitmap.getHeight() / 512))); /////
            IRTempAlarm.drawAlarmText(bitmap,"避雷器三相不平衡报警", x, y + 20, Color.WHITE, (int) (16 * Math.min((float) bitmap.getWidth() / 640, (float) bitmap.getHeight() / 512))); /////
        } else if (alarmTypes.contains(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE)) {
            IRTempAlarm.drawAlarmText(bitmap,"三相不平衡报警", x, y, Color.WHITE, (int) (16 * Math.min((float) bitmap.getWidth() / 640, (float) bitmap.getHeight() / 512))); /////
        }
    }

    public static void updateAlarm(long timestamp) {
        alarmInfo.alarmNum++;
        alarmInfo.alarmTime = timestamp;
    }
}
