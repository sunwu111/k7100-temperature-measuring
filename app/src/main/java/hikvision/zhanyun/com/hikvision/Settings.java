package hikvision.zhanyun.com.hikvision;

import android.graphics.Point;
import android.text.format.Time;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import lyh.Utils;

import static lyh.Utils.byteMerger;
import static lyh.Utils.formatDateTime;
import static lyh.Utils.hi;
import static lyh.Utils.lo;
import static lyh.Utils.hi2byte; /////
import static lyh.Utils.lo2byte; /////

// 用于保存参数的类
// 以下属性都用Public，是因为为了简单方便起见，使用FastJON把字符串和对象互转方便
public class Settings {
    // 智能分析告警上报（控制字：A7H），见规约7.63.4
    public static class DetectInfo {
        public byte channel;
        public byte preset;
        public TimeRecord time;
        public List<ObjectInfo> objects = new ArrayList<>();
    }

    public static class ObjectInfo {
        public byte classID;  // 对象类型ID，参考 南网规约章节 7.58.1 告警类型小类定义， 1=吊车， 2=塔吊，3=推土机 4=泵车，5=挖机，……
        public byte confidence;  // 0 ~ 100
        public byte left;           // 0~255  = X * 255 / 图像宽度
        public byte top;            // 0~255  = Y * 255 / 图像高度
        public byte right;          // 0~255
        public byte bottom;         // 0~255
    }

    // 流量上报信息
    public static class TrafficeUsage {
        TimeRecord time = new TimeRecord(System.currentTimeMillis());
        int todayUsed;      // 单位 MB 今日流量
        int monthUsed;      // 单位 MB 月已用流量
        int monthLeft;      // 单位 MB 月剩余流量

        public String toString() {
            return String.format("今日流量: %d MB, 本月已用: %d MB, 本月剩余: %d MB", todayUsed, monthUsed, monthLeft);
        }
    }

    // 场景配置参数，最终以 HashMap 存储， Key 格式为 "通道号,预置位号"
    public static class SceneParameter {
        public int presetNo;   // 预置位号
        public byte enable;     // 使能标志
        public String name;       // 预置点名称
        public byte left;       // 曝光区域起始点横坐标
        public byte top;       // 曝光区域起始点纵坐标
        public byte right;    // 曝光区域结束点横坐标
        public byte bottom;   // 曝光区域结束点纵坐标
    }

    // 气象数据信息
    public static class AeroInfo {
        TimeRecord time = new TimeRecord(System.currentTimeMillis());
        float Temp;               // 温度，实际温度值
        float Humidity;          // 湿度， % 数
        float WindSpeed;         // 瞬时风速，米/秒
        int WindDirection;    // 瞬时风向，与正北方向夹角
        float RainFall;          // 雨量，采样前一小时累计雨量，毫升
        float AtomosPress;        // 气压，hPa
        float Sunshine = -999;           // 日照
        float WindSpeedByMin;     // 1分钟平均风速
        int WindDirectionByMin; // 1分钟平均风向
        float WindSpeedBy10Min;   // 10分钟平均风速
        int WindDirectionBy10Min;   // 10分钟平均风向
        float MaxWindSpeedBy10Min;    // 10分钟最大风速;

        public String toString() {
            return String.format("气象数据: %.2f ℃, 湿度: %.2f%%, 风向: %d°, 风速: %.2fm/s, 气压: %.2fhPa, 雨量: %.2fmm/min, 日照: %.2fW/㎡",
                    Temp, Humidity, WindDirection, WindSpeed, AtomosPress, RainFall, Sunshine);
        }

    }

    // 电量上报信息
    public static class BatteryInfo {
        TimeRecord time;
        byte batNo;     // 电池编号;
        byte batPercent; // 电池电量， 0~100 %
        float batVoltage;  // 电池电压， 1位定点小数， 单位 V, 1表示 0.1V， 0~6553.5v 2 字节
        float batAmpler;   // 电池电流，毫安，1表示1mA，0~32767mA 2字节
        boolean charge;     // 充放电状态: 0 放电， 1 充电， 1字节
        float Temp;       // 温度，摄氏度，(上送值 - 500 ) / 10 即为实际环境温度 2 字节
        float solarVoltage; // 太阳能电压
        float solarAmpler;  // 太阳能电流
        float loadVoltage;  // 负载电压
        float loadAmpler;   // 负载电流

        public String toString() {
            return String.format("电量: %d%%, 电池: %6.3fV/%6.3fA, 负载电流: %6.3fA, 太阳能: %6.3fV/%6.3fA, 温度: %6.2f℃",
                    batPercent, batVoltage, batAmpler, loadAmpler, solarVoltage, solarAmpler, Temp);
        }
    }

    // 巡检组
    public static class CheckGroup {
        public int index;       // 巡检组号
        public List<Integer> points = new ArrayList<>();   // 巡检组中巡检点序号
    }

    public static class CheckScheduleItem {
        public byte group;       // 巡检组号
        public boolean enable;      // 使能标志
        public byte count;           // 巡检次数
        public byte hour;    // 开始时间小时
        public byte minute;    // 开始时间分钟
        public byte second;    // 开始时间秒
    }

    // 发给服务器的心跳信息
    public static class HeartBeat {
        byte year;
        byte month;
        byte day;
        byte hour;
        byte min;
        byte sec;
        byte signal = 4;
        byte voltage = 120;
        boolean sleep = false;
        boolean sleep_ir = false;
    }

    public static class Channel {
        public int id = 1;
        public String name = "球机1";
        public int type = 1;       // 0 = 摄像头， 1 = 海康兼容球机， 2 = 明景/集光设备
        public String server = "192.168.200.11";       // 设备地址
        public int port = 8000;
        public String user = "admin";
        public String password = "admin12345";
        public int camera = 0;
        public int rotate = 0;   // 旋转镜头角度度数， 0 表示无需旋转，仅对枪机起作用
        public int popCamera = 0;         // 画中画摄像头

        public Channel(int id, String name, int Camera) {
            this.type = 0;
            this.id = id;
            this.name = name;
            this.camera = Camera;
        }

        public Channel(int id, String name, String IP, int port, String user, String password) {
            this.type = 1;
            this.id = id;
            this.name = name;
            this.server = IP;
            this.port = port;
            this.user = user;
            this.password = password;
        }

        public Channel() {

        }
    }

    public static class DeviceConfig {
        public int traffic = 11;                        // 每月总流量，用于计算剩余流量使用，单位 GB
        public String server = "172.20.1.5";           // 服务器地址
        public int port = 12000;                       // 服务器端口
        public String deviceId = "ZJ9999";
        public int maxUploadFileSize = 900;
        public int objectDetect = 0;              // 0: 不检测， 1： 内置谷歌， 2： 华为， 3：英伟达， 4：YOLOv5
        public float confidence = 0.6f;             // AI检测置信度，默认0.8 /////
        public int rotate = 0;                     // 是否需要旋转镜头，默认不需要，旋转镜头是为了兼容老一批方向不正确的摄像头
        public boolean onlyShowBat = false;
        public boolean toCheck = false; ///////
        public Channel[] channels = new Channel[]{new Channel()};
        public int aeroDevice = 0;      // 气象仪
        public int chargeControl = 6;       // 充电控制器: 0 = 无， 1 = 单路板， 2 = 二路板， 3 = 三路板， 4 = 四路板， 6 = 汇能精电与开关板， 7 = 三路板与开关板， 8 = 开关板， 9 = 硕日与开关板
        public int distance = 5000;         // GPS报警阈值，单位米
        public int mainBoard = 0;          // 主控板： 0 = 旧xy6762板  1 = 新xy6762板
        public int alarmHigh = 80;        // 100 山火测温全域告警阈值
        public int alarmLow  = 0;
        public int alarmEnv  = 12;  // 温升告警阈值
        public int alarmCmp  = 4;  // 三相不平衡告警阈值
        public boolean wifi  = true;  // WIFI开关：false = 关， true = 开
        public boolean photoCheck = true;  // 拍照策略自检测开关：false = 关，true = 开 /////
        public boolean aiAccTest = false;  // 图像智能识别算法性能检测开关：false = 关，true = 开 /////
//        public int zoomRatio = 0;  // 变焦倍数：0 = 1，1 = 10，2 = 20，3 = 40 /////
        public boolean audio = false; /////
    }

    public static class Features {
        // 对应功能设置 0B，保留未用
    }

    public static class OnlineCfg {
        /////
        public byte heart = 1;             // 分钟，默认1分钟心跳
        // 用 int 保存无符号 byte
//        public int heart = 1;             // 分钟，默认1分钟心跳
        /////
        public short sleep = 0;            // 之前：分钟，0 不休眠，否则表示设备在无操作多久后休眠，默认60分钟过后休眠，这样可以保证晚上会在工作最后一个小时候关机省电
                                           // 2025-09-24改：分钟，0表示不休眠，否则若设置休眠时长为x分钟，则进入休眠模式，且持续x分钟
        public short online = 5;         // 分钟，表示唤醒或启动、视频操作多久后在线，超过指定时间就会关闭视频等
        public short sample = 1;         // 分钟，采集间隔，即其他数据如舞动数据等采集间隔，和拍照时间间隔无关，例如太阳能数据可以用这个间隔 /////
        // 用 int 保存无符号 byte
//        public int sample = 2;         // 分钟，采集间隔，即其他数据如舞动数据等采集间隔，和拍照时间间隔无关，例如太阳能数据可以用这个间隔 /////
        public byte day = 0;          // 硬件重启时间 天
        public byte hour = 0;          // 硬件重启时间 时
        public byte min = 0;          // 硬件重启时间 分

        // 心跳间隔设置128以上，我们平台设置不了
        public byte[] toBytes() {
            return new byte[]{
                    (byte) (heart & 0xFF), hi(sample), lo(sample), hi(sleep), lo(sleep), hi(online), lo(online), day, hour, min /////
//                    (byte) (heart & 0xFF), hi2byte(sample), lo2byte(sample), hi(sleep), lo(sleep), hi(online), lo(online), day, hour, min /////
            };
        }
    }

    public static class Parameters {
        public OnlineCfg onlineCfg = new OnlineCfg();
        public PhotoConfig ch1;
        public PhotoConfig ch2;
        public PhotoConfig ch3; /////

        public final byte fnWeather = 0x25;
        public final byte fnAntiThief = 0x2C;
        public final byte fnFire = 0x2D;
        public final byte fnPost = 0x30;
        public final byte fnDirty = 0x41;
        public final byte fnFailure = 0x45;
        public final byte fnFile = 0x73;
        public final byte fnVideo = (byte) 0x84;
        public byte[] Features = new byte[]{fnWeather, fnAntiThief, fnFire, fnPost, fnDirty, fnFailure, fnFile, fnVideo};

        public byte[] toBytes() {
            if (ch1 == null) ch1 = new PhotoConfig();
            if (ch2 == null) ch2 = new PhotoConfig();
            if (ch3 == null) ch3 = new PhotoConfig(); /////
            return byteMerger(onlineCfg.toBytes(),
                    new byte[]{
                            ch1.color, ch1.size, ch1.brightness, ch1.contrast, ch1.saturation,
                            ch2.color, ch2.size, ch2.brightness, ch2.contrast, ch2.saturation,
                            ch3.color, ch3.size, ch3.brightness, ch3.contrast, ch3.saturation, /////
                            fnWeather, fnAntiThief, fnFire, fnPost, fnDirty, fnFailure, fnFile, fnVideo
                    });
        }
    }

    public static class TimeRecord {
        public final byte[] bytes;
        public final byte year;
        public final byte month;
        public final byte day;
        public final byte hour;
        public final byte minute;
        public final byte second;
        public final String asString;
        public final long timestamp;

        public TimeRecord(long timestamp) {
            this.timestamp = timestamp;
            Time t = new Time();
            t.set(timestamp);
            year = (byte) (t.year - 2000);
            month = (byte) (t.month + 1);
            day = (byte) t.monthDay;
            hour = (byte) t.hour;
            minute = (byte) t.minute;
            second = (byte) t.second;
            asString = formatDateTime(timestamp);
            bytes = new byte[]{year, month, day, hour, minute, second};
        }

        public TimeRecord(byte[] raw) {
            bytes = raw;
            year = raw[0];
            month = raw[1];
            day = raw[2];
            hour = raw[3];
            minute = raw[4];
            second = raw[5];
            asString = String.format("20%02d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second);
            this.timestamp = Utils.stringToTimestamp("yyyy-MM-dd HH:mm:ss", asString);
        }

        public boolean equals(TimeRecord t) {
            return this.year == t.year && this.month == t.month && this.day == t.day &&
                    this.hour == t.hour && this.minute == t.minute && this.second == t.second;
        }
    }

    // 通道录像状态
    public static class ChannelStatus {
        public byte channel;
        public byte stream = 0;    // 码流……
        public byte code = 0;      // 状态码 0 = 成功， 1 = 通道号不存在，其他为错误码
        public byte recording = 0; // 0 未录像， 1 录像中;
    }

    public static class FileItem {
        public String filename = "";
        public TimeRecord begin;            // 开始时间
        public TimeRecord end;              // 结束时间
        public int size;                    // 文件大小
        public int type;                    // 文件类型
    }

    // 列出文件数量
    public static class FileDir {
        public byte channel = 1;
        public int type = 0;   // 录像类型;
        public TimeRecord begin = new TimeRecord(0);
        public TimeRecord end = new TimeRecord(0);
        public int count = 0;

        public byte[] toByte() {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);
                dos.write(channel);
                dos.writeInt(type);
                dos.write(begin.bytes);
                dos.write(end.bytes);
                dos.writeShort(count);
                return bos.toByteArray();
            } catch (Exception e) {
                return new byte[19];
            }
        }
    }

    public static class FileList {
        public byte channel;
        public int type;       // 录像类型
        public long begin;        // 录像开始时间，时间戳
        public long end;          // 录像结束时间，时间戳
        public FileItem[] files = new FileItem[0];
    }

    // 巡航设置
    public static class Cruise {
//        public byte index;
        public byte preset;  // 预置位
        public byte duration = 10;    // 停留时间
        public byte speed = 10;      // 云台速度

        public Cruise() {
        }

        public Cruise(byte preset, byte duration, byte speed) {
//            this.index = index;
            this.preset = preset;
            this.duration = duration;
            this.speed = speed;
        }
    }

    public static class CruiseGroup {
        public byte group;     // 巡航组号
        public List<Cruise> cruises = new ArrayList<>();       //  组内的预置位列表信息
    }

    public static class PhotoTimeItem {
        public int channel = 1;
        public byte hour = 8;
        public byte min = 0;
        public byte sec = 0;
        public byte preset = 0;

        @Override
        public String toString() {
            return "PhotoTimeItem{" +
                    "channel=" + channel +
                    ", hour=" + hour +
                    ", min=" + min +
                    ", sec=" + sec +
                    ", preset=" + preset +
                    '}';
        }
    }


    public static class PhotoConfig {
        public byte color = 1;     // 色彩 0: 黑白， 1: 彩色
        public byte size = 8;      // 分辨率，默认 1920 * 1080, 200W 像素
        public byte brightness = 50;   // 亮度 0~100;
        public byte contrast = 50;    // 对比度 0~100
        public byte saturation = 50;   // 饱和度 0~100

        public static Point getImageSize(int pixel) {
            Point[] points = new Point[]{
//                    new Point(1920, 1080),
//                    new Point(320, 240),
//                    new Point(640, 480),
//                    new Point(704, 576),
//                    new Point(800, 600),
//                    new Point(1024, 768),
//                    new Point(1280, 1024),
//                    new Point(1280, 720),
//                    new Point(1920, 1080), // 8
//                    new Point(960, 576),
//                    new Point(1280, 960),
//                    new Point(1600, 1200), // 11
//                    new Point(2048, 1536), // 12
//                    new Point(2592, 1520), // 13
//                    new Point(2592, 1944), // 14
//                    new Point(3072, 2048), // 15
//                    new Point(3840, 2160), // 16
//                    new Point(4000, 3000), // 17
//                    new Point(4608, 3456), // 18
//                    new Point(3200, 2400), // 19
//                    new Point(4224, 3136), // 20
//                    new Point(384, 288), // 英睿红外分辨率
//                    new Point(640, 512) // 高德红外分辨率


                    new Point(1920, 1080),
                    new Point(176, 144),    // 0
                    new Point(352, 288),    // 1
                    new Point(704, 288),    // 2
                    new Point(704, 576),    // 3
                    new Point(320, 240),    // 4
                    new Point(640, 480),    // 5
                    new Point(1280, 720),   // 6
                    new Point(1280, 960),   // 7
                    new Point(1600, 1200),  // 8
                    new Point(1024, 768),   // 9
                    new Point(800, 600),    // 10
                    new Point(1280, 1024),  // 11
                    new Point(2048, 1536),  // 12
                    new Point(1920, 1080),  // 13
                    new Point(2592, 1944),  // 14
                    new Point(960, 576),    // 15
                    new Point(2592, 1520),  // 16
                    new Point(3840, 2160),  // 17
                    new Point(4608, 3456),  // 18
                    new Point(4000, 3000),  // 19
                    new Point(3072, 2048),  // 20
                    new Point(3200, 2400),  // 21
                    new Point(4224, 3136),  // 22
                    new Point(384, 288), // 英睿红外分辨率
                    new Point(640, 512) // 高德红外分辨率


            };
            if (pixel < 0 || pixel > points.length - 1) pixel = 0;
            return points[pixel];
        }
    }

    public static class OSD {
        public int size = 1;      // OSD 字体比例 /////
        public byte time = 1;          // 显示时间戳？
        public byte tag = 1;           // 显示文本？
        public String text = "通道一";

    }


    public static class VideoCodec {
        public byte channel = 1;
        public byte streamType = 0;
        public int frame = 20;
        public int iFrame = 40;
        public byte codec = 0;
        public short bps = 1024;            // 单位: kbps
        public byte vbr = 0;
        public byte resolution = 8;

        public static Point getResolution(int index) {
            Point[] points = new Point[]{
//                    new Point(1280, 720),
//                    new Point(320, 240),
//                    new Point(640, 480),
//                    new Point(704, 576),
//                    new Point(800, 600),
//                    new Point(1024, 768),
//                    new Point(1280, 1024),
//                    new Point(1280, 720),
//                    new Point(1920, 1080),
//                    new Point(960, 576),
//                    new Point(1280, 960),
//                    new Point(1600, 1200), // 11
//                    new Point(2048, 1536), // 12
//                    new Point(2592, 1520), // 13
//                    new Point(2592, 1944), // 14
//                    new Point(3072, 2048), // 15
//                    new Point(3840, 2160), // 16
//                    new Point(4000, 3000), // 17
//                    new Point(4608, 3456), // 18
//                    new Point(3200, 2400), // 19
//                    new Point(4224, 3136), // 20
//                    new Point(720, 576),
//                    new Point(384, 288),
//                    new Point(640, 512)
                    new Point(1280, 720),
                    new Point(176, 144),    // 0
                    new Point(352, 288),    // 1
                    new Point(704, 288),    // 2
                    new Point(704, 576),    // 3
                    new Point(320, 240),    // 4
                    new Point(640, 480),    // 5
                    new Point(1280, 720),   // 6
                    new Point(1280, 960),   // 7
                    new Point(1600, 1200),  // 8
                    new Point(1024, 768),   // 9
                    new Point(800, 600),    // 10
                    new Point(1280, 1024),  // 11
                    new Point(2048, 1536),  // 12
                    new Point(1920, 1080),  // 13
                    new Point(2592, 1944),  // 14
                    new Point(960, 576),    // 15
                    new Point(2592, 1520),  // 16
                    new Point(3840, 2160),  // 17
                    new Point(4608, 3456),  // 18
                    new Point(4000, 3000),  // 19
                    new Point(3072, 2048),  // 20
                    new Point(3200, 2400),  // 21
                    new Point(4224, 3136),  // 22
                    new Point(640, 512)

            };
            if (index < 0 || index > points.length - 1) index = 0;
            return points[index];
        }

        public static byte getServerResolutionByXY(int w, int h) {
            Point[] points = new Point[]{
//                    new Point(0, 0),
//                    new Point(320, 240),
//                    new Point(640, 480),
//                    new Point(704, 576),
//                    new Point(800, 600),
//                    new Point(1024, 768),
//                    new Point(1280, 1024),
//                    new Point(1280, 720),
//                    new Point(1920, 1080),
//                    new Point(960, 576),
//                    new Point(1280, 960),
//                    new Point(1600, 1200), // 11
//                    new Point(2048, 1536), // 12
//                    new Point(2592, 1520), // 13
//                    new Point(2592, 1944), // 14
//                    new Point(3072, 2048), // 15
//                    new Point(3840, 2160), // 16
//                    new Point(4000, 3000), // 17
//                    new Point(4608, 3456), // 18
//                    new Point(3200, 2400), // 19
//                    new Point(4224, 3136), // 20
//                    new Point(720, 576),
//                    new Point(384, 288), // 22 英睿红外分辨率
//                    new Point(640, 512) // 23 高德红外分辨率

                    new Point(0, 0),
                    new Point(176, 144),    // 0
                    new Point(352, 288),    // 1
                    new Point(704, 288),    // 2
                    new Point(704, 576),    // 3
                    new Point(320, 240),    // 4
                    new Point(640, 480),    // 5
                    new Point(1280, 720),   // 6
                    new Point(1280, 960),   // 7
                    new Point(1600, 1200),  // 8
                    new Point(1024, 768),   // 9
                    new Point(800, 600),    // 10
                    new Point(1280, 1024),  // 11
                    new Point(2048, 1536),  // 12
                    new Point(1920, 1080),  // 13
                    new Point(2592, 1944),  // 14
                    new Point(960, 576),    // 15
                    new Point(2592, 1520),  // 16
                    new Point(3840, 2160),  // 17
                    new Point(4608, 3456),  // 18
                    new Point(4000, 3000),  // 19
                    new Point(3072, 2048),  // 20
                    new Point(3200, 2400),  // 21
                    new Point(4224, 3136),  // 22
                    new Point(720, 576),
                    new Point(384, 288), // 22 英睿红外分辨率
                    new Point(640, 512) // 23 高德红外分辨率
            };
            for (int i = 0; i < points.length; i++)
                if (points[i].x == w && points[i].y == h) return (byte) i;
            return 8;
        }

        @Override
        public String toString() {
            return "VideoCodec{" +
                    "channel=" + channel +
                    ", streamType=" + streamType +
                    ", frame=" + frame +
                    ", iFrame=" + iFrame +
                    ", codec=" + codec +
                    ", bps=" + bps +
                    ", vbr=" + vbr +
                    ", resolution=" + resolution +
                    '}';
        }

    }

//    public static class VideoTimeItem {
//        public byte channel = 1;    // 通道 1 开始计数；
//        public byte stream = 0;     // 码流类型 0：主通道， 1：从通道， 2：第 3 码流；
//        public byte action = 0;  // 动作类别: 0 为调用预置位（默认值）；01 为调用巡航；02 调用巡检。
//        public byte para = 0;  // 当动作类别为 0，参数值表示预置位号。不带云台的摄像机，预置位号为 255； 摄像机当前位置，预置位号为 0；
//                               // 当动作类别为 1，参数值表示巡航组号；
//                               // 当动作类别为 2，参数值表示巡检组号；
//        public int duration; // 秒，持续时间，实际两字节
//        public byte hour;      // 运行的时刻的小时，分，秒等
//        public byte min;
//        public byte sec;
//
//        public String toString() {
//            return String.format("通道:%d 码流类型:%d 持续时间:%d秒 开始时间:%02d:%02d:%02d",
//                    channel, stream, duration, hour, min, sec);
//        }
//    }


    public static class VideoTimeItem {
        public byte channel = 1;    // 通道 1 开始计数；
        public byte stream = 0;     // 码流类型 0：主通道， 1：从通道， 2：第 3 码流；
        public byte action = 0;  // 动作类别: 0 为调用预置位（默认值）；01 为调用巡航；02 调用巡检。
        public byte para = 0;  // 当动作类别为 0，参数值表示预置位号。不带云台的摄像机，预置位号为 255； 摄像机当前位置，预置位号为 0；
        // 当动作类别为 1，参数值表示巡航组号；
        // 当动作类别为 2，参数值表示巡检组号；
        public int duration; // 秒，持续时间，实际两字节
        public byte hour;      // 运行的时刻的小时，分，秒等
        public byte min;
        public byte sec;

        public String toString() {
            // 计算结束时间
            int totalSeconds = hour * 3600 + min * 60 + sec + duration;

            // 处理跨天情况
            int endHour = (totalSeconds / 3600) % 24;
            int endMin = (totalSeconds % 3600) / 60;
            int endSec = totalSeconds % 60;

            return String.format("通道:%d 码流类型:%d 持续时间:%d秒 开始时间:%02d:%02d:%02d 结束时间:%02d:%02d:%02d",
                    channel, stream, duration, hour, min, sec, endHour, endMin, endSec);
        }
    }


    public static class AIAlertType {
        public int alertType;   // 告警类型
        public int alertThreshold;   // 告警阈值
    }

    public static class AIAlertRegion {
        public int enable;    // 告警区域作用标志
        public Point[] coordinates = new Point[0];   // 告警区域坐标数目
    }

    /**
     * AI智能分析参数
     */
    public static class AIParameter {
        public int channel;  // 通道号
        public int preset;   // 预置位号
        public int enable;  // 智能分析启用标志
        public AIAlertType[] alertTypes = new AIAlertType[0];  // 告警类型
        public AIAlertRegion[] alertRegions = new AIAlertRegion[0]; // 告警区域参数
    }

    /**
     * AI联动参数
     */
    public static class AIAction {
        public int alertType;   // 联动告警类型
        public int alertAction;  // 联动动作 0 = 无动作  1 = 联动上传录像 2 = 联动上传拍照 3 = 联动 I/O 输出
        public int alertParam1;  // 联动参数 1
        public int alertParam2;  // 联动参数 2
    }

    /**
     * 山火报警
     * */
    public static class FireAlarmInfo {
        public int alarmNum; // 当天报警次数
        public long alarmTime; // 最新报警时间
    }

    public List<VideoTimeItem> videoTimeTable = new ArrayList<>();   // 通道录像时间表
    public List<PhotoTimeItem> photoTimeTable = new ArrayList<>();     // 拍照时间表
    public HashMap<String, VideoCodec> videoCodecs = new HashMap<>();           // 通道视频参数设置
    public HashMap<String, OSD> osds = new HashMap<>();                           // 通道OSD设置
    public HashMap<String, PhotoConfig> photoConfig = new HashMap<>();          // 通道图片参数设置
    public OnlineCfg onlineCfg = new OnlineCfg();
    public HashMap<String, List<CheckScheduleItem>> checkSchedule = new HashMap<>();
    public HashMap<String, List<CheckGroup>> checkGroups = new HashMap<>();
    public HashMap<String, SceneParameter> sceneParameters = new HashMap<>();  // Key: 通道,预置位
    public HashMap<String, List<CruiseGroup>> cruiseSettings = new HashMap<>(); // Key : 通道号
    public HashMap<String, AIParameter> aiParameters = new HashMap<>(); // Key : 通道号,预置位
    public HashMap<String, AIAction[]> aiActions = new HashMap<>(); // Key : 通道号,预置位

    public String sim = "F13912345678";                                        // SIM 卡号
    public String password = "1234";        // 用于服务器下发数据给终端时进行验证使用
    public String passcode = "1234";        // 用于上传其他数据的时候，【密文认证】字段使用
    public int ptzStep = 5;               // 0 ~ 100 ， 100 越远
    public int ptzSpeed = 5;             // 0~ 100  ， 100 越快
    public String powerOn = "00:00:00";  // 各路负载（安卓板除外）开启时间 /////
    public String powerOff = "23:59:59";  // 各路负载（安卓板除外）关闭时间 /////
    public String location = "";  // 定位信息
}