package hikvision.zhanyun.com.hikvision;

import android.graphics.Point;

import java.util.HashMap;
import java.util.Vector;

import hikvision.zhanyun.com.hikvision.utils.Log;

public class IRSetting {
    // 红外测温区
    public static class IrRegion implements Cloneable{
        public byte flag = 3; // 区域1作用标志  0：关闭；1：仅开启高温告警；2：仅开启低温告警；3：开启高低温告警
        public byte[] name = new byte[64]; // 区域名称 64字节
        public byte cls = -1; // 区域类别  -1：未定义默认值；0：电缆终端；1：避雷器；2：环境温度
        //public byte center = 0; // 是否为中心目标，先不增加智能测距功能 //////
        //public byte pointNum;   // 坐标点数目 取值范围[1- 8]
        public Vector<Point> points;

        @Override
        public IrRegion clone() {
            try {
                IrRegion irRegion = new IrRegion();//(IrRegion) super.clone();
                irRegion.flag = flag;
                irRegion.name = name.clone();
                irRegion.cls = cls;
                if (points != null) {
                    irRegion.points = new Vector<>();
                    for (Point p: points) {
                        irRegion.points.add(new Point(p));
                    }
                }
                return irRegion;
            } catch (Exception e) {
                Log.i(Log.TAG, "拷贝异常：" + e);
            }
            return null;
        }

        public String toString() {
            return ("告警标志：" + flag + ", 区域名称：" + name + ", 区域类型：" + cls + ", 坐标点：" + points.toString());
        }
    }

    public static class IrRegionInfo {
        public float emissivity = (float) 0.98;  // 目标发射率
        public float distance = 5;  // 测温距离
        public float highThres = 80; // 温度阈值上限
        public float lowThres = 0;  // 温度阈值下限
        //public float conf = 0; // 目标置信度，先不增加智能测距功能 //////
        public IrRegion irRegion;

        public String toString() {
            return String.format("目标发射率：%.2f, 测温距离：%.1f, 报警温度上限：%.1f, 报警温度下限：%.1f, %s",
                    emissivity, distance, highThres, lowThres, irRegion.toString());
        }
    }

    public static class IrRegionTemp {
        public float tempMax;
        public float tempAvg;
        public float tempMin;
        public IrRegion irRegion;
    }

    public class LegacySensorConfig {
        public byte measureUnit = 0; // 温度单位：0:摄氏 1:华氏  默认:摄氏
        // 显示颜色：0:白热 1:黑热 2:铁红 3:熔岩  4:彩虹 5:铁灰 6:红热 7:彩虹2  默认：0白热 英睿红外
        // 显示颜色：0:白热 1:熔岩 2:铁红 3:热铁 4:医疗 5:北极 6:彩虹 7:彩虹2 8:描红 9:黑热  默认：0白热 高德红外
        public byte color = 2;
        public byte hotTracker = 1; // 热点追踪:0:关闭  1:开启  默认开启
        public float reflectTemp; // 目标反射温度
        public float envirTemp = 23; // 环境温度
        public float envirHumi = (float) 0.68; // 环境湿度:[0,1.0]
        public float emissivity = (float) 0.98; // 目标发射率:[0,1.0]
        public float distance = (float) 5; // 测温距离：摄像头至测温区域中心点距离，单位：米；float类型数据，高德红外范围5~5000
        public byte shutterInterval = 2; // 快门校正间隔:单位分钟，1-60，0为自动
        public float tempCompensate = (float) 0.0; // 温度补偿值
        public byte onPalette = 1; // 显示色板:0：否 1：是
        public byte climateRef; // 微气象温湿度参数:0：否 1：是
        public byte resolution = 1; // 分辨率:0：384x288 1：640x512  默认640x512
        public byte focalLen = 0; // 焦距:0：9.1mm 1：13mm 2：19mm  默认9.1mm
        public byte globalAlarm = 1; // 全局告警:0:关闭  1:开启  默认开启
        public byte thresholdAlarm = 1; // 高温告警:0:关闭  1:开启  默认开启
        public byte envAlarm = 1; // 温升告警:0:关闭  1:开启  默认开启
        public byte comAlarm = 1; // 不平衡告警:0:关闭  1:开启  默认开启
        public byte imageStitch = 0; // 红外图像拼接:0:关闭  1:开启  默认关闭
    }
    // 测温传感器采集参数设置 E4H
    public static class SensorConfig {
        public byte measureUnit = 0; // 温度单位：0:摄氏 1:华氏  默认:摄氏
        // 显示颜色：0:白热 1:黑热 2:铁红 3:熔岩  4:彩虹 5:铁灰 6:红热 7:彩虹2  默认：0白热 英睿红外
        // 显示颜色：0:白热 1:熔岩 2:铁红 3:热铁 4:医疗 5:北极 6:彩虹 7:彩虹2 8:描红 9:黑热  默认：0白热 高德红外
        public byte color = 2;
        public byte hotTracker = 1; // 热点追踪:0:关闭  1:开启  默认开启
        public float reflectTemp; // 目标反射温度
        public float envirTemp = 23; // 环境温度
        public float envirHumi = (float) 0.68; // 环境湿度:[0,1.0]
        public float emissivity = (float) 0.98; // 目标发射率:[0,1.0]
        public float distance = (float) 5; // 测温距离：摄像头至测温区域中心点距离，单位：米；float类型数据，高德红外范围5~5000
        public byte shutterInterval = 2; // 快门校正间隔:单位分钟，1-60，0为自动
        public float tempCompensate = (float) 0.0; // 温度补偿值
        public byte onPalette = 1; // 显示色板:0：否 1：是
        public byte climateRef; // 微气象温湿度参数:0：否 1：是

        public String toString() {
            return String.format("温度单位:%d 显示颜色:%d 热点追踪:%d 目标反射温度:%.2f 环境温度:%.2f 环境湿度:%.2f " +
                            "目标发射率:%.2f 测温距离:%.2f 快门校正间隔:%d 温度补偿:%.2f 显示色板:%d 微气象温湿度参数:%d",
                    measureUnit, color, hotTracker, reflectTemp, envirTemp, envirHumi,
                    emissivity, distance, shutterInterval, tempCompensate, onPalette, climateRef);
        }
    }
    public static class PresetRegions {
        public HashMap<Integer, Vector<IrRegionInfo>> irPresetRegions = new HashMap<>(); // key: 预置位号
    }

    public SensorConfig sensorConfig = new SensorConfig();
    public LegacySensorConfig legacySensorConfig = new LegacySensorConfig();
    public HashMap<Integer, PresetRegions> irChannelRegions = new HashMap<>(); // key: 通道号


    public byte resolution = 0; // 分辨率:0：384x288 1：640x512  默认384x288
    public byte focalLen = 1; // 高德红外：焦距:0：9.1mm 1：13mm 2：19mm 3：4.9mm 默认13mm 英睿红外：焦距:0：4mm 1：6.8mm 2：13mm 默认6.8mm
    public byte globalAlarm = 1; // 全局告警:0:关闭  1:开启  默认开启
    public byte thresholdAlarm = 1; // 高温告警:0:关闭  1:开启  默认开启
    public byte envAlarm = 1; // 温升告警:0:关闭  1:开启  默认开启
    public byte comAlarm = 1; // 不平衡告警:0:关闭  1:开启  默认开启
    public byte imageStitch = 0; // 红外图像拼接:0:关闭  1:开启  默认关闭
    public byte imageFusion = 0;  // 双光融合:0:关闭  1:开启  默认关闭 /////
    public float angle = 0;  // 双光融合可见光旋转角度，单位度  默认0 /////
    public int horDisplacement = 0;  // 双光融合水平位移，单位像素  默认0 /////
    public int verDisplacement = 0;  // 双光融合垂直位移，单位像素  默认0 /////
}
