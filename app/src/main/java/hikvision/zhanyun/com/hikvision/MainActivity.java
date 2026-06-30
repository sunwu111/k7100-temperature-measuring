package hikvision.zhanyun.com.hikvision;

import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.BUTTON_PRIMARY;
import static hikvision.zhanyun.com.hikvision.AutoStart.ACTION_TIME_CHANGED;
import static hikvision.zhanyun.com.hikvision.Settings.AeroInfo;
import static hikvision.zhanyun.com.hikvision.Settings.BatteryInfo;
import static hikvision.zhanyun.com.hikvision.Settings.Channel;
import static hikvision.zhanyun.com.hikvision.Settings.ChannelStatus;
import static hikvision.zhanyun.com.hikvision.Settings.CheckGroup;
import static hikvision.zhanyun.com.hikvision.Settings.CheckScheduleItem;
import static hikvision.zhanyun.com.hikvision.Settings.CruiseGroup;
import static hikvision.zhanyun.com.hikvision.Settings.DetectInfo;
import static hikvision.zhanyun.com.hikvision.Settings.DeviceConfig;
import static hikvision.zhanyun.com.hikvision.Settings.Features;
import static hikvision.zhanyun.com.hikvision.Settings.FileDir;
import static hikvision.zhanyun.com.hikvision.Settings.FileList;
import static hikvision.zhanyun.com.hikvision.Settings.FireAlarmInfo;
import static hikvision.zhanyun.com.hikvision.Settings.HeartBeat;
import static hikvision.zhanyun.com.hikvision.Settings.OSD;
import static hikvision.zhanyun.com.hikvision.Settings.OnlineCfg;
import static hikvision.zhanyun.com.hikvision.Settings.Parameters;
import static hikvision.zhanyun.com.hikvision.Settings.PhotoConfig;
import static hikvision.zhanyun.com.hikvision.Settings.PhotoTimeItem;
import static hikvision.zhanyun.com.hikvision.Settings.TimeRecord;
import static hikvision.zhanyun.com.hikvision.Settings.TrafficeUsage;
import static hikvision.zhanyun.com.hikvision.Settings.VideoCodec;
import static hikvision.zhanyun.com.hikvision.Settings.VideoTimeItem;
import static hikvision.zhanyun.com.hikvision.device.Device.CHECK_LINE_INTERVAL;
import static hikvision.zhanyun.com.hikvision.device.Device.CHECK_LINE_PTZ_COUNT;
import static hikvision.zhanyun.com.hikvision.device.Device.CHECK_LINE_START_PTZ_INDEX;
import static hikvision.zhanyun.com.hikvision.device.Device.DEVICE_CAMERA;
import static hikvision.zhanyun.com.hikvision.device.Device.DEVICE_DVR_AIPU;
import static hikvision.zhanyun.com.hikvision.device.Device.DEVICE_DVR_HUANYU;
import static hikvision.zhanyun.com.hikvision.device.Device.DEVICE_DVR_YANDI;
import static hikvision.zhanyun.com.hikvision.device.Device.DEVICE_ONVIF_CAMERA;
import static hikvision.zhanyun.com.hikvision.device.Device.DEVICE_USB_GUIDE;
import static hikvision.zhanyun.com.hikvision.device.Device.DEVICE_USB_IRAY;
import static hikvision.zhanyun.com.hikvision.device.Device.PTZ_PRESET_MOVE_TIME;
import static hikvision.zhanyun.com.hikvision.utils.VideoFiles.VIDEO_FILES_COUNT;
import static hikvision.zhanyun.com.hikvision.utils.VideoFiles.VIDEO_FILES_LIST;
import static lyh.Utils.PERIOD_DAY;
import static lyh.Utils.PERIOD_HOUR;
import static lyh.Utils.PERIOD_MINUTE;
import static lyh.Utils.PERIOD_SECOND;
import static lyh.Utils.TAG;
import static lyh.Utils.addTime;
import static lyh.Utils.dateFromString;
import static lyh.Utils.exec;
import static lyh.Utils.formatDateTime;
import static lyh.Utils.humanReadableByteCount;
import static lyh.Utils.left;
import static lyh.Utils.listFiles;
import static lyh.Utils.loadObjectFromFile;
import static lyh.Utils.random;
import static lyh.Utils.readAsset;
import static lyh.Utils.reloadAfterCrash;
import static lyh.Utils.restartApplication;
import static lyh.Utils.right;
import static lyh.Utils.roundUp;
import static lyh.Utils.saveObjectToFile;
import static lyh.Utils.stackTraceToString;
import static lyh.Utils.stringFromFile;
import static lyh.Utils.stringToFile;
import static lyh.Utils.stringToTimestamp;
import static lyh.Utils.su;
import static lyh.Utils.subString;

import android.Manifest;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.renderscript.Float3;
import android.telephony.CellIdentityLte;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.guide.sdk.util.DeviceUtils;
import com.hwangjr.rxbus.RxBus;
import com.zhjinrui.batcom.RS485Impl;

import org.json.JSONException;
import org.opencv.android.OpenCVLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import hikvision.zhanyun.com.hikvision.Settings.AeroInfo;
import hikvision.zhanyun.com.hikvision.Settings.BatteryInfo;
import hikvision.zhanyun.com.hikvision.Settings.Channel;
import hikvision.zhanyun.com.hikvision.Settings.ChannelStatus;
import hikvision.zhanyun.com.hikvision.Settings.CheckGroup;
import hikvision.zhanyun.com.hikvision.Settings.CheckScheduleItem;
import hikvision.zhanyun.com.hikvision.Settings.CruiseGroup;
import hikvision.zhanyun.com.hikvision.Settings.DetectInfo;
import hikvision.zhanyun.com.hikvision.Settings.DeviceConfig;
import hikvision.zhanyun.com.hikvision.Settings.Features;
import hikvision.zhanyun.com.hikvision.Settings.FileDir;
import hikvision.zhanyun.com.hikvision.Settings.FileList;
import hikvision.zhanyun.com.hikvision.Settings.FireAlarmInfo;
import hikvision.zhanyun.com.hikvision.Settings.HeartBeat;
import hikvision.zhanyun.com.hikvision.Settings.OSD;
import hikvision.zhanyun.com.hikvision.Settings.OnlineCfg;
import hikvision.zhanyun.com.hikvision.Settings.Parameters;
import hikvision.zhanyun.com.hikvision.Settings.PhotoConfig;
import hikvision.zhanyun.com.hikvision.Settings.PhotoTimeItem;
import hikvision.zhanyun.com.hikvision.Settings.TimeRecord;
import hikvision.zhanyun.com.hikvision.Settings.TrafficeUsage;
import hikvision.zhanyun.com.hikvision.Settings.VideoCodec;
import hikvision.zhanyun.com.hikvision.Settings.VideoTimeItem;
import hikvision.zhanyun.com.hikvision.device.AipuDevice;
import hikvision.zhanyun.com.hikvision.device.Camera2Device;
import hikvision.zhanyun.com.hikvision.device.Device;
import hikvision.zhanyun.com.hikvision.device.HuanYuDevice;
import hikvision.zhanyun.com.hikvision.device.MyOnvifDevice;
import hikvision.zhanyun.com.hikvision.device.YanDiDevice;
import hikvision.zhanyun.com.hikvision.device.guide.GUIDEDev;
import hikvision.zhanyun.com.hikvision.device.guide.SVDraw;
import hikvision.zhanyun.com.hikvision.device.iray.IRayDev;
import hikvision.zhanyun.com.hikvision.receiver.UsbPermissionReceiver;
import hikvision.zhanyun.com.hikvision.receiver.UsbStatusReceiver;
import hikvision.zhanyun.com.hikvision.rto.RTPH264;
import hikvision.zhanyun.com.hikvision.utils.AndroidThermalMonitor;
import hikvision.zhanyun.com.hikvision.utils.Log;
import hikvision.zhanyun.com.hikvision.utils.StorageUtil;
import hikvision.zhanyun.com.hikvision.utils.SystemSettings;
import hikvision.zhanyun.com.hikvision.utils.VideoFiles;
import hikvision.zhanyun.com.hikvision.wifi.WifiAP;
import lyh.Utils;


public class MainActivity extends AppCompatActivity implements SPGPCallback, View.OnTouchListener {
    // !!!!!! 接收太阳能球机电源开关的消息，切勿改动
    public static final String BATCOM_SAMPLE_ACTION = "zhjinrui.batcom.SAMPLE_NOTICE";
    public static final String AEROINFO_ACTION = "zhjinrui.batcom.AEROINFO";      // 气象数据
    public static final String GYROINFO_ACTION = "zhjinrui.batcom.GYROINFO";
    public static final String EXT_JPG = "jpg";
    public static final String EXT_MP4 = "mp4";
    // 普通常量定义
    public static final boolean DEBUG = BuildConfig.BUILD_TYPE.equals("debug");
    public static final String DATA_DIR = Environment.getExternalStorageDirectory() + "/zhjinrui/spgp/";
    public static final String FILE_PATH = DATA_DIR + "data/";
    public static final String ACTION_SMS = "zhjinrui.sms_receive";            // 用于收到短信广播通知时，Receiver发来的的Intent名称定义
    public static final String ACTION_CLEAN_FILES = "zhjinrui.clean.files";
    public static final String ACTION_RETRY_PHOTOING = "zhjinrui.retry.photo";
    public static final String SMS_ADDRESS = "number";                           // 短信Intent的地址参数，手机号码
    public static final String SMS_BODY = "body";                                 // 短信Intent的内容数据
    public static final String ACTION_PHOTO = "zhjinrui.spgp.photo";           // 定时拍照任务通知
    public static final String ACTION_INIT_PHOTO = "zhjinrui.spgp.init_photo";        // 每日刷新拍照任务通知
    //public static final String ACTION_UPLOAD_APPEND = "zhjinrui.upload.append";
    public static final String ACTION_CHECK_LINE = "zhjinrui.spgp.check_line";           // 定时拍照任务通知
    public static final String ACTION_VIDEO = "zhjinrui.spgp.video";           // 定时录像任务通知
    public static final String ACTION_RECORD = "zhjinrui.spgp.record";         // 可见光机芯定时录像任务通知 /////
    public static final String ACTION_REBOOT = "zhjinrui.spgp.reboot";        // 零点重启设备
    public static final String ACTION_PROTECT = "zhjinrui.spgp.protect";        // 软关机（硬重启保护）
    public static final String ACTION_START_WIFIAP = "zhjinrui.spgp.wifiap.start";  // 指定时间开启wifi AP用于连接维护
    public static final String ACTION_PHOTO_CHECK = "zhjinrui.spgp.photo_check";  // 拍照自检测 /////
    public static final String WIFI_STATE_CHANGE = "android.net.wifi.WIFI_AP_STATE_CHANGED";
    public static final String ACTION_HEART = "zhjinrui.spgp.heartbeat";      // 定时心跳
    public static final String ACTION_SAMPLE = "zhjinrui.spgp.sample";      // 定时采样
    public static final String USAGE_RATE = "zhjinrui.spgp.USAGE_RATE";      // 定时检测使用率
    public static final String POWERON_INTENT = "zhjinrui.batcom.POWERON";
    public static final String POWERON_RGB_INTENT = "zhjinrui.batcom.POWERON_RGB"; /////
    public static final String POWERON_IR_INTENT = "zhjinrui.batcom.POWERON_IR";
    public static final String POWEROFF_INTENT = "zhjinrui.batcom.POWEROFF";
    public static final String LOG_FILE = DATA_DIR + "logs.log";
    public static final String IR_SETTING_FILE = DATA_DIR + "ir_setting.json";  // 和各个通道相关的设置，可复位
    public static final String IRAY_SETTING_FILE = DATA_DIR + "iray_setting.json";  // 和各个通道相关的设置，可复位 /////
    public static final String CAMERA_SETTING_FILE = DATA_DIR + "camera_setting.json";  // 和各个通道相关的设置，可复位 /////
    private static final String[] SIGNAL_LEVELS = {"0%", "20%", "40%", "60%", "80%", "100%"};
    private static final String OLD_CONFIG_FILE = Environment.getExternalStorageDirectory() + "/HikVisionInfo/config.ini";  // 老配置文件文件名
    private static final String TRAFFIC_FILE = DATA_DIR + "traffic.dat";    // 每月流量统计保存文件
    private static final String CONFIG_FILE = DATA_DIR + "config.json";         // 设备核心设置，如服务器设置等，不可复位
    private static final String SETTING_FILE = DATA_DIR + "settings.json";  // 和各个通道相关的设置，可复位
    private static final String RECORD_POLICY_FILE = DATA_DIR + "record_policy.json";  // 设备实际执行的录像策略，和settings.json同目录
    private static final String USB_STATE_FILE = DATA_DIR + "usb_state.json";  // 和各个通道相关的设置，可复位
    private static final String FREQUECY = DATA_DIR + "frequency.json";  // 测试文件，修改电压值的频率
    private static final String KEY_FREQUENCY = "frequency";
    private static int frequency;

    private static final String KEY_USB_POWER = "usbPowerState";

    private static final String WIFIAP_FROM = "06:00:00";          //8点--18点wifi热点整点开启
    private static final String WIFIAP_UNTIL = "21:00:00";
    private static final Object presetResetToken = new Object(); // 恢复守望位标志
    private static final Object powerOnOffToken = new Object(); // 云台关闭标志 ！！！
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final int CAMERA_REQUEST_CODE = 100;
    /////
    private static final int RC_PHOTO_BASE = 10000;
    // 每个 channel 给 100 个空间，index 不能超过 99；如果 index 可能更大就把 100 改大
    private static final int RC_PHOTO_STRIDE = 100;
    // 唤醒模式下的拍照预热闹钟使用独立 requestCode 段，避免和真正的拍照闹钟互相覆盖。
    private static final int RC_PHOTO_WAKEUP_BASE = 200000;
    // 定时巡检按通道独立注册闹钟，避免通道一、通道二使用同一个 PendingIntent 互相覆盖。
    private static final int RC_CHECK_LINE_BASE = 300000;
    private static final int RC_CHECK_LINE_STRIDE = 100;
    private static final int MAX_RECORD_ALARM_CANCEL_COUNT = 256;
    private static final long MIN_INIT_INTERVAL = 1000;

//    private static final long STORAGE_USAGE_INTERVAL_MS = 30 * 60 * 1000;  // 30分钟
//    private static final long STORAGE_USAGE_INTERVAL_MS = 1 * 60 * 1000;
    private static final int MAX_REAL_LENGTH = 61;
    private static final int MAX_HUANYU_REAL_LENGTH = MAX_REAL_LENGTH * 2;

    private static final long MAX_INTERVAL = 1000 * 60 * 30;  // 最大间隔30分钟
    public static boolean isIRPhotoing = false;
    public static boolean isVLPhotoing = false;
    // 设备对象列表，运行时使用，与 deviceConfig 中的通道列表对应
    public static HashMap<String, Device> channels = new HashMap<String, Device>(); /////
    public static float tempEnvControl = Float.NEGATIVE_INFINITY;                  // 汇能精电控制器温度
    public static float tempEnvRegion = Float.NEGATIVE_INFINITY;                   // 环境温度区域的最高温
    public static float tempEnvironment = Math.max(tempEnvControl, tempEnvRegion); // 最终取的环境温度
    public static int batPrecent = 0;                                       // 电池剩余电量
    public static boolean is6735 = Build.VERSION.SDK_INT < Build.VERSION_CODES.O;
    // 界面相关对象
    public static boolean isIntent;
    /////
    public static boolean isOpenCVInitialized = false;
    //private static String POWER_ON = "06:30:00";  // 2025-01-17改，之前是05:50:00
    //private static String POWER_OFF = "18:30:00";  // 2025-01-17改，之前是19:10:00
    private static List<Settings.VideoTimeItem> RECORD_TABLE; /////
    private static String[] POWER_ON = {"00:00:00"};  // 2025-03-22改，配置文件中可随意配置，之前是05:50:00
    private static String[] POWER_OFF = {"23:59:59"};  // 2025-03-22改，配置文件中可随意配置，之前是19:10:00
    private static DeviceConfig deviceConfig = new DeviceConfig();          // 通道JSON配置

    public static int getChargeControl() {
        return deviceConfig.chargeControl;
    }

    private static SPGProtocol spgProtocol;                                  // 南网协议指令处理器
    private static InetSocketAddress cmdAddress;                       // 与南网服务器通信的地址
    private static DatagramSocket socket;                              // 与南网通信的UDP Socket
    private static Thread socketThread;                                      // 通信数据处理线程，核心线程，负责数据收发
    private static long thisMonthTraffic = 0;                              // 当前月的流量数据
    private static boolean sleeping = true;                                // 云台当前开机状态，关机为true，该值会从太阳能开关云台的通知中更新
    private static boolean sleepingIr = true;                             // 红外当前开机状态，关机为true，该值会从太阳能开关红外的通知中更新
    public static Settings settings = new Settings();                     // 系统配置，各个通道的配置数据
    //    private static final long UPDATE_INTERVAL = 4000; // 4秒更新一次
    private static final long UPDATE_INTERVAL = settings.onlineCfg.sample * PERIOD_MINUTE;
    private static CAMERASetting cAMERASetting = new CAMERASetting();      // 摄像头模组参数配置 /////
    private static IRSetting iRSetting = new IRSetting();     // 红外模组参数配置
    private static long lastRecvTime = System.currentTimeMillis();  // 最后收到数据包的时间，用于心跳检查
    private static long lastSentTime = System.currentTimeMillis();  // 最后发送数据包的时间，用于心跳检查
    private static boolean haveSolarCharger = false;
    private static float batVoltage = 0.0f;                                  // 电压数据，需要在心跳中，定时更新
    private static float solarVoltage = 0.0f;                                  // 电压数据，需要在心跳中，定时更新
    private static float batAmper = 0.0f;                                  // 电压数据，需要在心跳中，定时更新
    private static float temperature = 0.0f;                                  // 电压数据，需要在心跳中，定时更新
    private static float humidity = 0.0f;                                  // 电压数据，需要在心跳中，定时更新
    private static float solarAmpler = 0.0f;                                  // 太阳能电流数据，需要在心跳中，定时更新
    private static float loadAmpler = 0.0f;                                  // 负载电流数据，需要在心跳中，定时更新
    private static float loadAmpler1 = 0.0f;                                  // 负载1电流数据 /////
    private static float loadAmpler2 = 0.0f;                                  // 负载2电流数据 /////
    private static float amplerHours = 0.0f;
    private static Location devLocation;
    private static AtomicReference<AeroInfo> aeroInfoAtomicReference = new AtomicReference<>();
    private static HandlerThread utilsThread = new HandlerThread("打杂线程"); // 打杂线程
    private static HandlerThread sendThread = new HandlerThread("命令发送"); // 专用数据发送线程
    private static HandlerThread uploadThread = new HandlerThread("文件上传"); // 专用数据发送线程
    private static HandlerThread serialThread = new HandlerThread("串口线程"); // 串口读写专用线程

    private static HandlerThread gimbalPowerThread = new HandlerThread("云台上下电"); // 串口读写专用线程

    private static int testBatAmperCount = 0;


    private static Handler utilsHandler;
    private static Handler sendHandler;
    private static Handler uploadHandler;
    private static Handler serialHandler;
    private static Handler gimbalPowerHandler;


    private static Handler delayHandler = new Handler();
    private static AtomicBoolean powerClockSynced = new AtomicBoolean(false); // 供电板时钟同步标识
    private static WifiAP wifiAP;
    private static float batteryCapability = -1f;

    private static boolean usbPowerState = false;


    // 电源分级管理的相关参数
    public static final int MODE_FULL   = 0;
    public static final int MODE_WAKEUP = 1;
    public static final int MODE_SLEEP  = 2;

    public static int currentMode = MODE_FULL;

    private volatile int pendingApplyMode = -1;
    private int pendingMode = -1;
    private long pendingStartTime = 0;
//    private static final long MODE_CONFIRM_TIME = 30 * 60 * 1000L;     // 模式切换时间
    // private static final long MODE_CONFIRM_TIME = 2 * 60 * 1000L;          // 2分钟
   private static final long MODE_CONFIRM_TIME = 1 * 60 * 1000L;          // 1分钟
    private static final String STATE_FILE = DATA_DIR + "power_mode_state.json";

    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
    };


    private static long onlineEnd;             // 超过这个时刻就要停止预览关Share，若需要延长时间，调用updateOnline来更新它
    private static long sleepTime;             // 超过此时刻就要关闭球机电源，若需要延长时间，调用updateOnline来更新它
    private static int signalLevel = 4;                      // 信号强度数据
    private static int signalDBM = 0;
    private static String netType = "信号";                   // 网络类型：2G，3G，4G，WIFI等
    private static long lastFakeTime = 0;      // 温度湿度假数据，每小时变动一次
    private static int fakeTemperature = 25;      // 温度假数据，每小时变动一次
    private static int fakeHumidity = 84;      // 湿度假数据，每小时变动一次
    private static String iccid; // sim卡卡号
    private static int recordPreset = 0;  // 存储录像预置位，默认为预置位0
    private static String loadOpenTime = "00:01:00";  // 控制器每天开安卓板默认时刻 /////
    private static String loadCloseTime = "00:00:00";  // 控制器每天关安卓板默认时刻 /////
    private static int cruiseIndex = 1;  // 巡航组号从1开始 /////
    private static boolean irPreheatActive; /////
    private static boolean rs485Ret = true;
    private static float batTemp = 23;
    private static float batHumidity = 68;
    private static float aeroTemp = 23;
    private static float aeroHumidity = 68;
    private static PhotoTimeItem nextPhotoTime = null;
    private static PhotoTimeItem lastPhotoTime = null;
    private static long lastHeartTime = System.currentTimeMillis();
    private static boolean uploadThisTime = false;
    // 声明RxBus接收器标志
    private static boolean rxBusReceiverRegistered = false;
    private final String SECRET = "p7K#9x!";
    private final String DOMAIN = "ssid_v1|";    // 域隔离 未来升级版本用 ssid_v2|

    public boolean sleepMode = false;    // 云台休眠模式与否： true 休眠模式， false 正常模式，2021-6-9，张总确认为单次休眠模式，重启或者第二天后无效
    public boolean sleepModeIr = false;    // 红外休眠模式与否： true 休眠模式， false 正常模式

    boolean irOn = false;
    //    private static final String[] SIGNAL_LEVELS = {"＿", "▁", "▂", "▄", "▆", "█"};
    private int DVR_BOOT_TIME = 120;        // DVR 启动到自检完成需要的时间 /////
    private String FILE_URL = "http://183.47.15.146:8081/file.php";
    // 核心的一些类对象
    private AlarmManager alarmManager;
    private PowerManager powerManager;
    private LocationManager locationManager;
    private TelephonyManager telephonyManager;
    private PowerManager.WakeLock wakeLock;                                 // CPU 锁，防止做某些操作时休眠
    private PhoneStatListener phoneStatListener;                     // 手机信号强度监听
    private PendingIntent videoIntent;                                // 定时录像的闹钟回调
    private PendingIntent recordIntent;                               // 可见光机芯定时录像的闹钟回调 /////
    private PendingIntent photoIntent;                                // 定时拍照的闹钟回调
    private PendingIntent heartBeatIntent;                           // 心跳闹钟回调
    private PendingIntent powerOnIntent;                        // 开启云台Intent
    private PendingIntent powerRgbOnIntent;                     // 开启可见光Intent
    private PendingIntent powerIrOnIntent;                      // 开启红外Intent
    private PendingIntent powerOffIntent;                       // 关闭云台Intent
    private PendingIntent sampleIntent;                           //  采样间隔闹钟回调
    private PendingIntent utilizationRateIntent;                           //  采样间隔闹钟回调
    private PendingIntent rebootIntent1;                           //  重启时间1回调
    private PendingIntent rebootIntent2;                           //  重启时间2回调
    private PendingIntent protectIntent;                          //  软关机（硬重启保护）回调
    private PendingIntent wifiIntent;
    private PendingIntent photoCheckIntent;                       // 定时拍照自检测的闹钟回调 /////
    private Timer softShutdownTimer;
    private HashMap<String, Long> trafficMonth;                      // 月流量统计对象，保存到文件方便累计
    private HashMap<String, Integer> failedFileError = new HashMap<>();
    // photoAlarms 是真正执行拍照的闹钟；photoWakeupAlarms 只负责拍照前提前上电。
    // 两者必须分开保存和取消，否则切换模式或刷新拍照计划时会误取消另一类任务。
    private Map<Integer, PendingIntent> photoAlarms = new HashMap<>();   // 拍照闹钟
    private Map<Integer, PendingIntent> photoWakeupAlarms = new HashMap<>();   // 拍照前预热唤醒闹钟
    private Map<Integer, PendingIntent> checkLineAlarms = new HashMap<>();   // 定时巡检闹钟
    private SurfaceView surfaceView, surfaceDraw;
    //    private Spinner spnChannels, spnAI, spnWidgetsAI, cbType, spnPopCamera, spnAeroDevice, spnChargeController, spnMainBoarder, spnPreset, spnBitRateType, spnStreamType, spnDenoiseMode, spnGainControl, spnFocusMode, spnCruise, spnResolution, spnZoomRatio, irOperator, irObjType, irObjFlag; ///////
    private Spinner spnChannels, spnAI, spnWidgetsAI, cbType, spnPopCamera, spnAeroDevice, spnChargeController, spnMainBoarder, spnPreset, spnBitRateType, spnStreamType, spnDenoiseMode, spnGainControl, spnFocusMode, spnDayAndNightMode, spnCruise, spnResolution, irOperator, irObjType, irObjFlag; ///
    private ArrayAdapter<String> adapter;
    private Button btnUp, btnBottom, btnLeft, btnRight, btnZoomin, btnZoomout, btnAddPreset, btnRemovePreset, btnEditPreset, btnAddCruise, btnRemoveCruise, btnEditCruise; /////
    private TextView tvState;
    private TextView textVersion;
    private CheckBox cbToCheck, cbPhotoCheck, cbBackLightCom, cbStrongLightSup, cbElectronicFog, cbLowLight, cbVideoLoss, cbVideoBlock, cbVideoOutFocus, cbVideoScreenDist, cbAIAccTest, switchAudio,TDptz; // cbBatOnly,     ///////
    private EditText tbRotate, tvDVRUser, tvDVRPwd, tvDVRIP, tvDVRPort, tvID, tvServer, tvPort, tvCamID, etTraffic, etConfidence, etWidgetsConfidence, etFrame, etCruiseDuration, etCruiseSpeed, etPtzSpeed; //, etConfidence; /////
    private EditText irTempAdj, irObjEmi, irObjDistance, irTempReflect, irTempEnv, irHumiEnv, irRegionDistance, irRegionEmi, irAngle, irHorDisplacement, irVerDisplacement, irShutterInt; /////
    ////////
    private Spinner irVideoMode, irFocalLen, irResolution;
    private CheckBox irHotTrace, irPlatteDisp, irGlobalAlarm, irThresholdAlarm, irEnvAlarm, irComAlarm, irImageStitch, irImageFusion; /////
    private boolean remoteDebug = false;
    /////
    private String RED_PATH = "/sys/class/leds/green_led1/brightness";
    private String GREEN_PATH = "/sys/class/leds/green_led4/brightness";
    private String YELLOW_PATH = "/sys/class/leds/yellow_led2/brightness";
    private OffLineRecode offLineRecode = new OffLineRecode();
    /////
    private Context context;
    private MediaPlayer mediaPlayer;
    /////
    ////////
    private Device camera = null;
    private Device speaker = null;
    private String ssid;
    private float temporaryCurrent = 0.01f;
    ////////
    private LocationListener locationListener;
    private Handler handler = new Handler();
    private Runnable temperatureRunnable;
    ///////
    /////
    private int PREFIX_LEN = 6;

    // 唤醒模式的关系：
    // 1. currentMode == MODE_WAKEUP 时，云台/红外默认允许下电，只有业务或预热窗口需要临时上电。
    // 2. 直播、回放、录像文件查询、拍照、短视频录制都会调用 markWakeupActivity()，先上电再刷新保活。
    // 3. 保活时间结束后，mResetWakeupFlagTask 再次确认没有业务，最后调用 doSleep() 下电。
    //
    // 录像文件查询和回放共用一套服务器流程。该变量不是通用“保活开关”，主要用于区分：
    // 第一次查询录像文件时可返回缓存，真正进入文件列表/回放后再保持后续查询走设备。
    private static boolean isWakeupVideoPlaybackMode = false;
    // true 表示当前处于唤醒模式业务后的保活窗口内；doSleep() 会因为它直接跳过下电。
    private static boolean isWakeupKeepAliveMode = false;
    // 唤醒模式下，业务结束后继续保活的时间。当前测试版本为 2 分钟，正式版本需要改回 15 分钟。
    private static final long WAKEUP_KEEP_ALIVE_MS = 15 * PERIOD_MINUTE;

    // 唤醒模式下拍照完成后，按通道类型检查后续定时拍照任务：可见光看 10 分钟，红外看 15 分钟。
    private static final int WAKEUP_RGB_PHOTO_FOLLOWUP_MINUTES = 10;
    private static final int WAKEUP_IR_PHOTO_FOLLOWUP_MINUTES = 15;

    // 下电前需要避开的拍照预热窗口：可见光拍照提前 3 分钟开云台，红外拍照提前 10 分钟开云台和红外。
    private static final int RGB_PHOTO_PREHEAT_MINUTES = 3;
    private static final int IR_PHOTO_PREHEAT_MINUTES = 10;

    private final ConcurrentHashMap<String, Integer> wakeupPhotoFollowupFiles = new ConcurrentHashMap<>();
    // 山火告警可能在短时间内连续触发，这里合并 1 分钟内的重复告警，避免瞬间上报大量 2DH 指令。
    private static final long FIRE_ALARM_REPORT_MERGE_MS = PERIOD_MINUTE;
    private final Object fireAlarmReportLock = new Object();
    private long lastFireAlarmReportElapsed = 0;
    private FireAlarmInfo pendingFireAlarmInfo;
    private boolean fireAlarmReportScheduled = false;

    private static class LiveStartSession {
        boolean starting;
        boolean delayStop;
        int streamType;
        int currentSsrc;
        int network;
        String server;
        int port;
        long startSeq;
    }

    private static class LiveStartTarget {
        int streamType;
        int ssrc;
        int network;
        String server;
        int port;
    }

    private final Map<Integer, LiveStartSession> liveStartSessions = new HashMap<>();
    private long liveStartSeq = 0;


    /// 短视频录制请求过快，会导致问题
    class VideoTask {
        int channel;
        int stream;
        int time;
        boolean upload;

        public VideoTask(int channel, int stream, int time, boolean upload) {
            this.channel = channel;
            this.stream = stream;
            this.time = time;
            this.upload = upload;
        }
    }

    private final Queue<VideoTask> videoQueue = new LinkedList<>();
    private boolean isVideoTaskRunning = false;


    /*
      初始化闹钟
      start: 启动时间: hh:nn:ss 格式，严格按照给出的开始时间间隔设定闹钟
      本闹钟不能周期运行，如需要，则重新设定一次即可，参数中的 msecPeriod是按start时刻开始切片的周期
      例如如果按 08:01:15开始，周期是1小时，则下次闹钟一定在09:01:15开始，并非周期性回调！
     */
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private long lastInitTime = 0;
    // 添加两个状态位成员变量
    private boolean isRJ45Powered = false;
    private boolean isEth0Up = false;
    // 记录app UI界面要绘制的红外测温框，每绘制一个测温区域，添加到这个容器里，
    // 一并通过接口下发到红外设备，和后台的机制一样（后台也是先绘制测温框，然后统一下发），
    // 这个容器里的数据只在当此拉流过程中有效，下次拉流这个容器要清空
    private Vector<IRSetting.IrRegion> irLiveRegions = new Vector<>();
    private DrawTempRegion drawTempRegion;
    private float local3DStartX;
    private float local3DStartY;

    View.OnTouchListener svTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (TDptz != null && TDptz.isChecked()) {
                return handleLocal3DPtzTouch(v, event);
            }

            if (cbType.getSelectedItemPosition() != DEVICE_USB_GUIDE && cbType.getSelectedItemPosition() != DEVICE_USB_IRAY)
                return true;

            if (event.getAction() != ACTION_DOWN)
                return true;

            if ((event.getButtonState() & BUTTON_PRIMARY) != BUTTON_PRIMARY)
                return true;

            if (drawTempRegion == null)
                return true;

            switch (irOperator.getSelectedItemPosition()) {
                case 1: // 画框
                    int x = (int) event.getX();
                    int y = (int) event.getY();
                    drawTempRegion.draw(x, y);
                    break;
                case 2: // 保存
                    drawTempRegion.save();
                    break;
                case 3: // 清空
                    drawTempRegion.clear();
                    break;
            }
            return true;
        }
    };

    private boolean handleLocal3DPtzTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                local3DStartX = event.getX();
                local3DStartY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
                int channel = spnChannels.getSelectedItemPosition() + 1;
                Device dev = channels.get(String.valueOf(channel));
                if (dev == null || !dev.isDVR()) return true;

                int width = Math.max(1, view.getWidth());
                int height = Math.max(1, view.getHeight());
                int startX = normalizeLocal3DCoordinate(local3DStartX, width);
                int startY = normalizeLocal3DCoordinate(local3DStartY, height);
                int endX = normalizeLocal3DCoordinate(event.getX(), width);
                int endY = normalizeLocal3DCoordinate(event.getY(), height);

                utilsHandler.post(() -> dev.ptz3D(startX, startY, endX, endY));
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }

    private int normalizeLocal3DCoordinate(float value, int max) {
        int coordinate = Math.round(value * 255f / max);
        return Math.max(0, Math.min(255, coordinate));
    }



    // 广播消息处理器
    private BroadcastReceiver powerControlReceive = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            // 收到太阳能的开机通知后，需要等待一段时间，等自检完成后，才设置dvrReady为true
            boolean on = intent.getBooleanExtra("on", false);
            showMsg("负载开关通知，负载打开：" + on);

            if (on) {
                /////
                for (Device dev : channels.values()) {
                    if (dev.isDVR()) {
                        if (dev.isUSB()) {
                            if (deviceConfig.chargeControl >= 6) { /////
                                // 如果是红外休眠模式，并且是打开红外，那么无论如何10分钟后关闭红外
                                if (sleepModeIr && !sleepingIr) {
                                    Utils.scheduleTask(new TimerTask() {
                                        @Override
                                        public void run() {
                                            gimbalPowerHandler.post(() -> powerControlNVR(false, 3));
                                        }
                                    }, 600 * 1000);
                                }
                            }
                        } else {
                            // 如果是云台休眠模式，并且是打开云台，那么无论如何10分钟后关闭云台
                            if (sleepMode && !sleeping) {
                                Utils.scheduleTask(new TimerTask() {
                                    @Override
                                    public void run() {
                                        gimbalPowerHandler.post(() -> powerControlNVR(false, 2));
                                    }
                                }, 600 * 1000);
                            }
                        }
                    }
                }
                /////
            }
        }
    };


    ////////
    // 陀螺仪监听器
    private SensorManager sensorManager;
    private Sensor gyroSensor;
    private MediaCodec mediaDecoder = null;


    private BroadcastReceiver onIntentReceive = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            cpuLock();
            try {
                String action = intent.getAction();
                //if (!action.equals(WIFI_STATE_CHANGE)) Log.i(Log.TAG, "收到通知：" + action);
                if (action.equals(ACTION_SMS)) {
                    doSMSAction(intent);
                } else if (action.equals(USAGE_RATE)) {
                    cleanFiles();
                } else if (action.equals(ACTION_CHECK_LINE)) {
                    doCheckLineAction(intent);
                } else if (action.equals(ACTION_PHOTO)) {
                    doPhotoAction(intent);
                } else if (action.equals(ACTION_VIDEO)) {
                    doVideoAction(intent);
                } else if (action.equals(ACTION_RECORD)) { /////
                    if (currentMode == MODE_WAKEUP || currentMode == MODE_SLEEP) {
                        Log.i(Log.TAG, "低功耗模式下不执行录像策略动作");
                        return;
                    }
                    if(isWorkHour()){    // 是工作时间才转动到预置位
                        int recordChannel = intent.getByteExtra("channel", (byte) 1);  // 默认录像通道号是1
                        int recordAction = intent.getByteExtra("action", (byte) 0);
                        int recordPara = intent.getByteExtra("para", (byte) 0);
//                    doWakeup("定时开启所有云台与红外", 23);
                        gimbalPowerHandler.postDelayed(() -> {
                            if (recordAction == 0) {  // 调用预置位
                                runMove(2, recordChannel, recordPara);
                            } else if (recordAction == 1) {  // 调用巡航
                                runCruise(recordChannel, recordPara);
                            } else if (recordAction == 2) {  // 调用巡检
                                runCheckLine(recordChannel, recordPara - 1, 1);
                            }
                        }, 2 * PERIOD_MINUTE);
                    }
                } else if (action.equals(POWERON_INTENT)) {
                    doWakeup("定时开启所有云台与红外", 23);
                } else if (action.equals(POWEROFF_INTENT)) {
                    doSleep("定时关闭云台与红外", 23);
                    /////
                } else if (action.equals(POWERON_RGB_INTENT)) {
                    doWakeup("抓拍前三分钟开启云台", 2);
                    /////
                } else if (action.equals(POWERON_IR_INTENT)) {
                    doWakeup("红外抓拍前10分钟开启云台与红外", 23);
                    irPreheatActive = true;  // 标记进入红外预热窗口 /////
                } else if (action.equals(ACTION_SAMPLE)) {
                    doSampleAction();
                    doGyroInfoAction(); ////////
                } else if (action.equals(ACTION_HEART)) {
                    doHeartAction();
                } else if (action.equals(AEROINFO_ACTION)) {
                    doAeroInfoAction(intent);
                } else if (action.equals(GYROINFO_ACTION)) {
                    doGyroInfoAction(); ////////
                } else if (action.equals(BATCOM_SAMPLE_ACTION)) {
                    haveSolarCharger = true;

                    batVoltage = intent.getFloatExtra("batVoltage", batVoltage);

                    solarVoltage = intent.getFloatExtra("solarVoltage", solarVoltage);
                    batAmper = intent.getFloatExtra("batAmpler", batAmper);
                    temperature = intent.getFloatExtra("temperature", temperature);
                    if (deviceConfig.chargeControl != 6) {
                        humidity = intent.getFloatExtra("humidity", humidity);
                    }
                    loadAmpler = intent.getFloatExtra("loadAmpler", loadAmpler);
                    solarAmpler = intent.getFloatExtra("solarAmpler", solarAmpler);
                    amplerHours = intent.getFloatExtra("amplerHours", amplerHours);
                    if (deviceConfig.chargeControl == 6) {
                        batPrecent = intent.getIntExtra("batPrecent", batPrecent);
                    }

                    // 发送电量信息到服务器
                    spgProtocol.doReportBattery(getBatterInfo());
                    spgProtocol.doReportTrafficUsage(false);
                    String text = getStatusText();
                    for (Device dev : channels.values()) {
                        if (dev.isLiving()) {
                            utilsHandler.post(()->dev.updateStatusText(text,false));
                        }
                    }

                    // Log.e(Log.TAG,"=========batVoltage:========="+batVoltage);
//                    Log.e(Log.TAG,"=========测试需要batVoltage修改为12.7:=========");
                    //////// 测试使用的电压
                    // batVoltage = 13.40f; // 全功能
                //    batVoltage = 12.93f; // 唤醒
//                    batVoltage = 12.7F; // 休眠 
                    Log.e(Log.TAG,"=========batVoltage:========="+batVoltage);

                    float verificationVoltage = 13.40f;


                    int oldMode = currentMode;
                    handlePowerModeByVoltage(verificationVoltage);
                    int newMode = currentMode;

                    // 模式发生变化
                    if (oldMode != newMode || currentMode == MODE_SLEEP) {

                        Log.i(TAG,
                                "检测到模式变化: "
                                        + modeToString(oldMode)
                                        + " -> "
                                        + modeToString(newMode));
                        // 设备忙
                        if (isAnyDeviceBusy()) {
                            Log.i(TAG, "设备忙碌，延迟执行模式切换");
                            // 仅记录待执行模式
                            pendingApplyMode = newMode;
                        } else {                                // 设备空闲
                            applyPowerMode(newMode);
                            pendingApplyMode = -1;
                        }
                    }

                    processPendingApplyMode();


                } else if (action.equals(ACTION_TIME_CHANGED)) {
                    doTimeChangedAction(context, intent);

                } else if (action.equals(ACTION_PROTECT)) {
                    if (!deviceConfig.toCheck) {
                        performSoftShutdown(context);
                    }
                } else if (action.equals(ACTION_REBOOT)) {
/*                    TaskManager.add("reboot");
                    doSleep("定时重启时关闭设备电源");
                    if (deviceConfig.chargeControl == 3 || deviceConfig.chargeControl == 4)
                        coldReboot();
                    else*/
                    // 这里的定时重启设置为软重启，硬重启的控制在供电板程序里，开机向供电板同步RTC时钟，
                    // 三路板固定在23:58:30硬重启，汇能精电控制器固定在23:57:00硬重启
                    rebootSystem("定时重启");
                } else if (action.equals(ACTION_RETRY_PHOTOING)) {
                    int channel = intent.getIntExtra("channel", 1);
                    int preset = intent.getIntExtra("preset", 1);
                    String filename = intent.getStringExtra("filename");
//                    int captureType = intent.getIntExtra("captureType", 0); ///////
                    utilsHandler.post(() -> {
                        Log.e(Log.TAG, "补拍");
                        takePhoto(channel, preset, false, filename, true);
//                        takePhoto(channel, preset, filename, false, true, false, captureType); ///////
                    });
//                    } else if (action.equals(ACTION_START_WIFIAP)) {
//                        if (deviceConfig.wifi) {
//                            doWifiAction(true);
//                            initWifiAlarm();
//                        }
//                    } else if (action.equals(ACTION_STOP_WIFIAP)) {
//                        doWifiAction(false);
//                    } else if (action.equals(WIFI_STATE_CHANGE)) {
//                        int state = intent.getIntExtra("wifi_state", 0);
//                        if (state == 11 && deviceConfig.wifi) { // WIFI_AP_STATE_DISABLED
//                            // 关闭wifi
//                            Log.i(Log.TAG, "关闭wifi");
//                            su("svc wifi disable");
//                        }
                } else if (action.equals(ACTION_INIT_PHOTO)) {
                    initPhotoTask(0);
                    /////
//                } else if (action.equals(ACTION_PHOTO_CHECK)) {
//                    for (Channel channel : deviceConfig.channels) {
//                        if (getPhotoTimeTable(channel.id) == null) {
//                            // 拍照自检测有没有故障
//                            takePhoto(channel.id, 0, null, false, true, true, 0); ///////
//                        }
//                    }
                }
                /////
            } catch (Exception e) {
                Log.e(Log.TAG, "广播消息回调错误：" + e.getMessage() + stackTraceToString(e));
            } finally {
                tryCpuUnlock();
            }
        }
    };



    /**
     * 写入频率值到文件
     */
    public static void writeFrequency(int frequency) {
        File file = new File(FREQUECY);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            JSONObject json = new JSONObject();
            json.put(KEY_FREQUENCY, frequency);
            String jsonString = json.toJSONString();
            fos.write(jsonString.getBytes("UTF-8"));
        } catch (Exception e) {
            Log.e(Log.TAG, "writeFrequency Exception: " + e.getMessage());
        }
    }

    /**
     * 读取频率值，如果文件不存在则自动创建并写入默认值 3，返回该值
     */
    public static int readFrequency() {
        File file = new File(FREQUECY);
        if (!file.exists()) {
            // 文件不存在，创建并写入默认值 10
            writeFrequency(3);
            return 3;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            String jsonString = new String(data, "UTF-8");
            JSONObject json = JSONObject.parseObject(jsonString);
            return json.getIntValue(KEY_FREQUENCY); // 若 key 不存在默认返回 0，但我们保证一定有
        } catch (Exception e) {
            Log.e(Log.TAG, "readFrequency Exception: " + e.getMessage());
            return 2; // 读取失败返回默认值
        }
    }


    private void cacheVideoFileList() {
        try {
            Calendar nowCal = Calendar.getInstance();
            long stopMillis = nowCal.getTimeInMillis();

            nowCal.add(Calendar.DAY_OF_YEAR, -15);
            long startMillis = nowCal.getTimeInMillis();

            Settings.TimeRecord stopTime = new Settings.TimeRecord(stopMillis);
            Settings.TimeRecord startTime = new Settings.TimeRecord(startMillis);

            int count = fileFiles(1, -1, startTime, stopTime);

            String stopStr = String.format("20%02d-%02d-%02d-%02d-%02d-%02d",
                    stopTime.year, stopTime.month, stopTime.day,
                    stopTime.hour, stopTime.minute, stopTime.second);
            String startStr = String.format("20%02d-%02d-%02d-%02d-%02d-%02d",
                    startTime.year, startTime.month, startTime.day,
                    startTime.hour, startTime.minute, startTime.second);

            findVideoFileList(1, -1, startStr, stopStr, 0, count);

            Log.i(Log.TAG, "进行录像文件信息缓存");
        } catch (Exception e) {
            Log.e(Log.TAG, "缓存失败" + e.getMessage());
        }
    }


    private void interfacePowerOn(){
        if (!usbPowerState) {
            for (int i = 0; i < 3; i++) {
                boolean errcode = RS485Impl.Instance().gpioOpenUSB();
                Log.i(Log.TAG, String.format("USB上电%s", errcode ? "成功" : "失败"));
                if (errcode) {
                    usbPowerState = true;
                    writeUsbPowerState(true);
                    break;
                }
            }
        }


        for (int i = 0; i < 3; i++) {
            boolean errcode = RS485Impl.Instance().gpioOpenRJ45();
            Log.i(Log.TAG, String.format("RJ45上电%s", errcode ? "成功" : "失败"));
            if (errcode) {
                isRJ45Powered = true;
                break;
            }
        }
    }


    private void interfacePowerOff(){
        for (int i = 0; i < 3; i++) {
            boolean errcode = RS485Impl.Instance().gpioCloseRJ45();
            Log.i(Log.TAG, String.format("RJ45下电%s", errcode ? "成功" : "失败"));
            if (errcode) {
                isRJ45Powered = false; // 更新状态位
                break;
            }
        }


        for (int i = 0; i < 3; i++) {
            boolean errcode = RS485Impl.Instance().gpioCloseUSB();
            Log.i(Log.TAG, String.format("USB下电%s", errcode ? "成功" : "失败"));
            if (errcode) {
                usbPowerState = false;
                writeUsbPowerState(false);
                break;
            }
        }
    }

    private void powerOnMipiIfNeeded(String reason) {
        if (camera == null) return;

        for (int i = 0; i < 3; i++) {
            boolean errcode = RS485Impl.Instance().gpioOpenMIPI();
            Log.i(Log.TAG, String.format("MIPI上电%s：%s", errcode ? "成功" : "失败", reason));
            if (errcode) break;
        }
    }

    private void powerOffMipi(String reason) {
        if (camera != null) {
            camera.close();
        }

        for (int i = 0; i < 3; i++) {
            boolean errcode = RS485Impl.Instance().gpioCloseMIPI();
            Log.i(Log.TAG, String.format("MIPI下电%s：%s", errcode ? "成功" : "失败", reason));
            if (errcode) break;
        }
    }

    private boolean hasActiveMipiTask() {
        for (Device dev : channels.values()) {
            if (dev != null && dev.isCamera() && isWakeupActiveTask(dev)) {
                return true;
            }
        }
        return false;
    }

    private void powerOffMipiIfIdle(String reason) {
        if (currentMode != MODE_WAKEUP && currentMode != MODE_SLEEP) return;
        if (hasActiveMipiTask()) {
            Log.i(Log.TAG, "MIPI仍有任务执行，暂不下电：" + reason);
            return;
        }
        powerOffMipi(reason);
    }

    /**
     * 12.99 <  voltage             全工作
     * 12.79 < voltage <= 12.99     唤醒
     * voltage <= 12.79             休眠
     */


    private boolean isAnyDeviceBusy() {

        for (String channel : channels.keySet()) {

            Device dev = channels.get(channel);

            if (dev != null && isWakeupActiveTask(dev)) {

                Log.i(Log.TAG,
                        "设备正在使用中 channel=" + channel);

                return true;
            }
        }

        return false;
    }

    private void applyPowerMode(int mode) {

        switch (mode) {
            case MODE_FULL: {
                isWakeupVideoPlaybackMode = false;
                isWakeupKeepAliveMode = false;
                interfacePowerOn();
                powerOnMipiIfNeeded("切换到全工作模式");
                Log.i(TAG,
                        "切换到全工作模式：工作时间开启云台");

                if (isWorkHour()) {
                    openShare("模式切换为全工作模式且在工作时间段");
                    doWakeup(
                            "模式切换为全工作模式且在工作时间段",
                            23);
                    utilsHandler.postDelayed(
                            () -> restoreRecordingForFullMode("切换到全工作模式恢复录像策略"),
                            60 * 1000);
                } else {
                    restoreRecordingForFullMode("切换到全工作模式恢复录像策略");
                }

                break;
            }

            case MODE_WAKEUP: {
                isWakeupVideoPlaybackMode = false;
                isWakeupKeepAliveMode = false;
                Log.i(TAG,
                        "切换到唤醒模式：立即关闭云台和红外");
                interfacePowerOn();
                // 先给设备下发空录像策略，再关闭 MIPI/云台；否则设备先下电会导致 setRecordTimes HTTP 请求失败。
                disableRecordingForLowPowerMode("切换到唤醒模式清空设备录像策略", () -> {
                    powerOffMipi("切换到唤醒模式");
                    doSleep("切换为唤醒模式", 23);
                    // 唤醒模式下定时拍照需要提前上电；切换模式后重建拍照闹钟，避免沿用全工作模式下的调度。
                    refreshPhotoSchedule();
                });
                break;
            }
            case MODE_SLEEP: {

                isWakeupVideoPlaybackMode = false;
                isWakeupKeepAliveMode = false;
                Log.i(TAG,
                        "切换到休眠模式：立即关闭云台和红外，RJ45和USB下电");
                // 休眠模式还会关闭 RJ45/USB，因此必须等空录像策略下发结束后再关闭接口电源。
                disableRecordingForLowPowerMode("切换到休眠模式清空设备录像策略", () -> {
                    powerOffMipi("切换到休眠模式");
                    interfacePowerOff();
                    doSleep("切换为休眠模式", 23);
                });
                break;
            }
        }
    }


    private void processPendingApplyMode() {

        if (pendingApplyMode == -1) {
            return;
        }

        if (isAnyDeviceBusy()) {

            Log.i(TAG,
                    "设备忙碌中，继续等待模式切换: "
                            + modeToString(pendingApplyMode));
            return;
        }

        Log.i(TAG,
                "设备空闲，开始执行延迟模式切换: "
                        + modeToString(pendingApplyMode));

        applyPowerMode(pendingApplyMode);

        pendingApplyMode = -1;
    }




    private int decideModeByVoltage(float voltage) {
        if (voltage > 12.99f) {
            return MODE_FULL;
        } else if (voltage > 12.79f) {
            return MODE_WAKEUP;
        } else {
            return MODE_SLEEP;
        }
    }


    private void handlePowerModeByVoltage(float voltage) {

        int newMode = decideModeByVoltage(voltage);

        long now = System.currentTimeMillis();

        // 时间异常保护
        if (pendingStartTime > now) {
            pendingMode = -1;
            pendingStartTime = 0;
            savePowerStateToFile();
        }


        if (pendingMode == newMode && pendingStartTime > 0) {
            long duration = now - pendingStartTime;
            Log.i(TAG,
                    "Pending mode "
                            + modeToString(pendingMode)
                            + " duration=" + (duration / 1000) + "s");

            if (duration >= MODE_CONFIRM_TIME) {
                switchMode(newMode);                // 进行模式的切换
                pendingMode = -1;
                pendingStartTime = 0;
                savePowerStateToFile();
                return;
            }
        }


        // 当前模式不变
        if (newMode == currentMode) {
            pendingMode = -1;
            pendingStartTime = 0;
            savePowerStateToFile();
            return;
        }

        // 第一次进入候选模式
        if (pendingMode == -1 || pendingMode != newMode) {
            pendingMode = newMode;

            if(currentMode == MODE_FULL && newMode == MODE_WAKEUP){
                utilsHandler.postDelayed(() -> {
                    if(currentMode == MODE_FULL && deviceConfig.chargeControl == 6){  // 只有在汇能精电下才有电源管理
                        Log.i(Log.TAG,"开始缓存");
                        cacheVideoFileList();
                    }  // 缓存信息
                },1 * 60 * 1000); // 1分钟后还是全工作模式就开始缓存,因为模式切换的时间为30分钟,这个地方1分钟后执行,一定是全工作模式
            }

            pendingStartTime = now;
            savePowerStateToFile();
            Log.i(TAG, "Enter pending mode: " + modeToString(newMode));
            return;
        }


        // 候选模式持续中
        long duration = now - pendingStartTime;
        if (duration >= MODE_CONFIRM_TIME) {

            switchMode(newMode);                   // 进行模式的切换
            pendingMode = -1;
            pendingStartTime = 0;
            savePowerStateToFile();
        }
    }


    // TODO 状态是如何进行切换的
    private void switchMode(int newMode) {
        if (newMode == currentMode) return;

        Log.w(TAG, "Power mode switch: "
                + modeToString(currentMode)
                + " -> "
                + modeToString(newMode));

        currentMode = newMode; // 根据当前的工作模式来选择不同的业务逻辑
    }


    private String modeToString(int mode) {
        switch (mode) {
            case MODE_FULL:
                return "FULL";
            case MODE_WAKEUP:
                return "WAKEUP";
            case MODE_SLEEP:
                return "SLEEP";
            default:
                return "UNKNOWN";
        }
    }


    public static boolean isFullMode() {
        return currentMode == MODE_FULL;
    }

    public static boolean isWakeupMode() {
        return currentMode == MODE_WAKEUP;
    }

    public static boolean isSleepMode() {
        return currentMode != MODE_FULL && currentMode != MODE_WAKEUP;
    }


    private void savePowerStateToFile() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("currentMode", currentMode);
            obj.put("pendingMode", pendingMode);
            obj.put("pendingStartTime", pendingStartTime);

            File file = new File(STATE_FILE);
            FileWriter writer = new FileWriter(file, false);
            writer.write(obj.toString());
            writer.flush();
            writer.close();

            Log.i(TAG, "Power state saved: " + obj.toString());

        } catch (Exception e) {
            Log.e(TAG, "savePowerStateToFile failed"+e);
        }
    }


    private void loadPowerStateFromFile() {
        File file = new File(STATE_FILE);
        if (!file.exists()) {
            Log.i(TAG, "state file not exists, skip restore");
            return;
        }

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            restorePowerState(sb.toString());

        } catch (Exception e) {
            Log.e(TAG, "loadPowerStateFromFile failed"+e);
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (Exception ignore) {}
        }
    }

    private void restorePowerState(String json) {
        try {
            com.alibaba.fastjson.JSONObject obj =
                    com.alibaba.fastjson.JSON.parseObject(json);

            if (obj.containsKey("currentMode")) {
                currentMode = obj.getIntValue("currentMode");
            } else {
                currentMode = MODE_FULL;
            }

            if (obj.containsKey("pendingMode")) {
                pendingMode = obj.getIntValue("pendingMode");
            } else {
                pendingMode = -1;
            }

            if (obj.containsKey("pendingStartTime")) {
                pendingStartTime = obj.getLongValue("pendingStartTime");
            } else {
                pendingStartTime = 0;
            }

            Log.i(TAG,
                    "Power state restored: currentMode=" + currentMode +
                            ", pendingMode=" + pendingMode +
                            ", pendingStartTime=" + pendingStartTime);

        } catch (Exception e) {
            Log.e(TAG, "restorePowerState failed"+e);
        }
    }


    private void setRecordingPolicy(List<Settings.VideoTimeItem> list){
        if (list == null) {
            Log.i("RecordingPolicy", "list is null");
            return;
        }
        Log.i("RecordingPolicy", "list size: " + list.size());
        for (int i = 0; i < list.size(); i++) {
            Settings.VideoTimeItem item = list.get(i);
            Log.i("RecordingPolicy", "item[" + i + "]: " + item);
        }
        if (isWorkHour()) {
            for (VideoTimeItem item : list) {
                // 判断当前时间是否在该策略时间段内
                Calendar now = Calendar.getInstance();
                int nowSeconds = now.get(Calendar.HOUR_OF_DAY) * 3600 +
                        now.get(Calendar.MINUTE) * 60 +
                        now.get(Calendar.SECOND);
                int startSeconds = item.hour * 3600 + item.min * 60 + item.sec;
                int endSeconds = startSeconds + item.duration;
                if (nowSeconds >= startSeconds && nowSeconds <= endSeconds) {
                    // 命中当前时间段，提取信息执行动作
                    int recordChannel = item.channel;
                    int recordAction = item.action;
                    int recordPara = item.para;
                    if (sleeping) {
                        doWakeup("开启云台与红外", -1);
                        gimbalPowerHandler.postDelayed(() -> {
                            if (recordAction == 0) {  // 调用预置位
                                runMove(2, recordChannel, recordPara);
                            } else if (recordAction == 1) {  // 调用巡航
                                runCruise(recordChannel, recordPara);
                            } else if (recordAction == 2) {  // 调用巡检
                                runCheckLine(recordChannel, recordPara - 1, 1);
                            }
                        }, 2 * PERIOD_MINUTE); /////
                    } else {
                        if (recordAction == 0) {  // 调用预置位
                            runMove(2, recordChannel, recordPara);
                        } else if (recordAction == 1) {  // 调用巡航
                            runCruise(recordChannel, recordPara);
                        } else if (recordAction == 2) {  // 调用巡检
                            runCheckLine(recordChannel, recordPara - 1, 1);
                        }
                    }
                    break;
                }
            }
        }
    }


    /**
     * 写入 USB 电源状态到文件（线程安全）
     * @param state 要保存的 boolean 值
     */
    public static  void writeUsbPowerState(boolean state) {
        File file = new File(USB_STATE_FILE);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            JSONObject json = new JSONObject();
            json.put(KEY_USB_POWER, state);
            String jsonString = json.toJSONString(); // fastjson 生成 JSON 字符串
            fos.write(jsonString.getBytes("UTF-8"));
        } catch (Exception e) {
            Log.e(Log.TAG,"Exception::"+e.getMessage());
        }
    }

    /**
     * 从文件读取 USB 电源状态
     * @return 保存的 boolean 值，若文件不存在或读取失败则返回 false
     */
    public static  boolean readUsbPowerState() {

        File file = new File(USB_STATE_FILE);
        if (!file.exists()) {
            return false;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            String jsonString = new String(data, "UTF-8");
            JSONObject json = JSONObject.parseObject(jsonString); // fastjson 解析
            return json.getBooleanValue(KEY_USB_POWER); // 返回 boolean，若 key 不存在则返回 false
        } catch (Exception e) {
            Log.e(Log.TAG,"Exception::"+e.getMessage());
            return false;
        }
    }


    private RTPH264 rtph264 = null;
    private MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    // 告警对象会被设备侧继续更新，进入延迟队列前复制一份，避免后续数值变化影响已排队的上报内容。
    private FireAlarmInfo copyFireAlarmInfo(FireAlarmInfo info) {
        FireAlarmInfo copy = new FireAlarmInfo();
        copy.alarmNum = info.alarmNum;
        copy.alarmTime = info.alarmTime;
        return copy;
    }

    private void postFireAlarmReport(FireAlarmInfo info, String reason) {
        Log.i(Log.TAG, String.format("山火告警合并上报：%s，报警次数：%d", reason, info.alarmNum));
        uploadHandler.post(() -> spgProtocol.doAlertFireAlarm(info));
    }

    // 第一次告警立即上报；1 分钟内重复告警只保留最新一次，到窗口结束后再合并上报一次。
    private void handleFireAlarmReport(FireAlarmInfo info) {
        if (info == null) return;

        FireAlarmInfo reportNow = null;
        long delay = 0;
        synchronized (fireAlarmReportLock) {
            long now = SystemClock.elapsedRealtime();
            if (lastFireAlarmReportElapsed == 0 || now - lastFireAlarmReportElapsed >= FIRE_ALARM_REPORT_MERGE_MS) {
                lastFireAlarmReportElapsed = now;
                pendingFireAlarmInfo = null;
                fireAlarmReportScheduled = false;
                reportNow = copyFireAlarmInfo(info);
            } else {
                // 合并窗口内重复触发时，不立即发送 2DH，只刷新待上报的最新告警信息。
                pendingFireAlarmInfo = copyFireAlarmInfo(info);
                if (!fireAlarmReportScheduled) {
                    fireAlarmReportScheduled = true;
                    delay = FIRE_ALARM_REPORT_MERGE_MS - (now - lastFireAlarmReportElapsed);
                }
            }
        }

        if (reportNow != null) {
            postFireAlarmReport(reportNow, "立即上报");
        } else if (delay > 0) {
            Log.i(Log.TAG, "山火告警合并上报：1分钟内重复告警，延迟合并上报");
            uploadHandler.postDelayed(this::flushPendingFireAlarmReport, delay);
        }
    }

    // 延迟任务到点后发送合并结果；如果时间窗口被系统调度提前唤醒，则继续补齐剩余等待时间。
    private void flushPendingFireAlarmReport() {
        FireAlarmInfo reportInfo = null;
        long delay = 0;
        synchronized (fireAlarmReportLock) {
            if (pendingFireAlarmInfo == null) {
                fireAlarmReportScheduled = false;
                return;
            }

            long now = SystemClock.elapsedRealtime();
            long elapsed = now - lastFireAlarmReportElapsed;
            if (elapsed < FIRE_ALARM_REPORT_MERGE_MS) {
                delay = FIRE_ALARM_REPORT_MERGE_MS - elapsed;
            } else {
                reportInfo = pendingFireAlarmInfo;
                pendingFireAlarmInfo = null;
                fireAlarmReportScheduled = false;
                lastFireAlarmReportElapsed = now;
            }
        }

        if (reportInfo != null) {
            Log.i(Log.TAG, String.format("山火告警合并上报：延迟上报，报警次数：%d", reportInfo.alarmNum));
            spgProtocol.doAlertFireAlarm(reportInfo);
        } else if (delay > 0) {
            uploadHandler.postDelayed(this::flushPendingFireAlarmReport, delay);
        }
    }

    ControllerCallback controllerCallback = new ControllerCallback() {
        private final int PHOTO_MAX_RETRY = 3;
        private ConcurrentHashMap<String, Integer> photoFailerMap = new ConcurrentHashMap<>();

        @Override
        public void onPhotoTaked(long timestamp, int channel, int preset, String file) {
//        public void onPhotoTaked(long timestamp, int channel, int preset, String file, int captureType) { ///////
            /////
            if (channel != 3) {
                for (Device dev : channels.values()) {
                    if (dev.type == DEVICE_DVR_AIPU) {
                        dev.setOSD(dev.osd, false);
                        dev.updateStatusText(getStatusText(), false);
                    } else if (dev.type == DEVICE_DVR_HUANYU) {
                        dev.updateStatusText(dev.osd, getStatusText(), false);
                    }
                }
            }
            /////
            Log.d(Log.TAG, "抓拍成功：" + file);
            uploadHandler.post(() -> spgProtocol.doUploadFile(timestamp, file, channel, preset, SPGProtocol.FILE_TYPE.PHOTO)); /////
//            uploadHandler.post(() -> spgProtocol.doUploadFile(timestamp, file, channel, preset, SPGProtocol.FILE_TYPE.PHOTO, captureType)); ///////
            /////
            Device dev = channels.get(String.valueOf(channel));
            boolean wakeupPhotoHandled = handleWakeupPhotoFinished(dev, channel, file, "拍照成功");
            if (!wakeupPhotoHandled && dev != null && dev.isDVR()) {
                if (dev.isUSB()) {
                    sleepDevice(channel, "红外拍照成功");
                } else {
                    sleepDevice(channel, "可见光拍照成功");
                }
            }
            if (dev != null && dev.isCamera()) {
                powerOffMipiIfIdle("MIPI拍照成功");
            }
            /////

            // 这个地方需要恢复状态 sunwu
            if (deviceConfig.toCheck) {
                isIRPhotoing = false;
                isVLPhotoing = false;
            }

            if (photoFailerMap.containsKey(file)) photoFailerMap.remove(file);
        }

        @Override
        public void onPhotoFailed(int channel, int preset, String filename) {
            if (channel != 3) {
                for (Device dev : channels.values()) {
                    if (dev.type == DEVICE_DVR_AIPU) {
                        dev.setOSD(dev.osd, false);
                        dev.updateStatusText(getStatusText(), false);
                    } else if (dev.type == DEVICE_DVR_HUANYU) {
                        dev.updateStatusText(dev.osd, getStatusText(), false);
                    }
                }
            }

            /////
            Log.d(Log.TAG, "抓拍失败：" + filename);

            int retry = 1;
            if (photoFailerMap.containsKey(filename)) {
                retry = photoFailerMap.get(filename);
                if (retry < PHOTO_MAX_RETRY) {
                    /////
                    Device dev = channels.get(String.valueOf(channel));
                    if (dev.isUSB()) {
                        Log.i(Log.TAG, "重启红外机芯");
                        powerControlNVR(false, 3);
                        powerControlNVR(true, 3);
                    }
                    /////
                    photoFailerMap.put(filename, ++retry);
                } else {
                    Log.i(Log.TAG, String.format("补拍失败，超过最大尝试次数%d次：%s", retry, filename));
                    photoFailerMap.remove(filename);
                    finishTask(filename);
                    /////
                    Device dev = channels.get(String.valueOf(channel));
                    boolean wakeupPhotoHandled = handleWakeupPhotoFinished(dev, channel, filename, "拍照失败");
                    if (!wakeupPhotoHandled && dev != null && dev.isDVR()) {
                        if (dev.isUSB()) {
                            sleepDevice(channel, "红外拍照失败");
                        } else {
                            sleepDevice(channel, "可见光拍照失败");
                        }
                    }
                    if (dev != null && dev.isCamera()) {
                        powerOffMipiIfIdle("MIPI拍照失败");
                    }

                    // 拍照失败是需要恢复到正常状态 sunwu
                    if (deviceConfig.toCheck) {
                        isIRPhotoing = false;
                        isVLPhotoing = false;
                    }

                    return;
                }
            } else {
                photoFailerMap.put(filename, retry);
            }
            // 需要重新拍照的情况下，把回到录像预置位的指令从队列中删除
            utilsHandler.removeCallbacksAndMessages(presetResetToken);
            Log.i(Log.TAG, String.format("补拍第%d次：%s", retry, filename));
            Intent intent = new Intent(ACTION_RETRY_PHOTOING);
            intent.putExtra("channel", channel);
            intent.putExtra("preset", preset);
            intent.putExtra("filename", filename);
//            intent.putExtra("captureType", captureType); ///////
            sendBroadcast(intent);
        }

        @Override
        public void onCameraBlocked(Device device) {
            /////
            for (Device dev : channels.values()) {
                if (dev.type == 2) {
                    dev.setOSD(dev.osd, false);
                    dev.updateStatusText(getStatusText(), false);
                } else if (dev.type == 7) {
                    dev.updateStatusText(dev.osd, getStatusText(), false);
                }
            }
            if (device != null && !device.isCamera()) {
                if (device.isDVR()) {
                    if (device.isUSB()) {
                        // 进程还在运行，需要重启红外机芯
                        Log.i(Log.TAG, "红外机芯卡死，重启红外机芯");
                        powerControlNVR(false, 3);
                        powerControlNVR(true, 3);
                    } else {
                        // 进程还在运行，需要重启可见光机芯
                        Log.i(Log.TAG, "可见光机芯卡死，重启可见光机芯");
                        powerControlNVR(false, 2);
                        powerControlNVR(true, 2);
                    }
                }
            } else {
                Log.i(Log.TAG, "MIPI摄像头卡死，重启apk");
                restartApplication(MainActivity.this, 0);
            }
            /////

            /*ApplicationInfo info = getApplication().getApplicationInfo();
            int result = su(String.format("busybox ps -A | grep -w '%s$'", info.packageName));
            if (result == 0) {
                // 这里会不起作用，导致不拍照，这个需要再研究下？？？？？？
                utilsHandler.removeCallbacksAndMessages(null);
            } else {*/
            // 应该到不了这里，进程本身都没了
//                Log.i(Log.TAG, "摄像头卡死，重启apk");
//                restartApplication(MainActivity.this, 0);
        }

        @Override
        public void onVideoFinished(long timestamp, int channel, int streamType, String file, boolean upload) {
//        public void onVideoFinished(long timestamp, int channel, int streamType, String file, boolean upload, int captureType) { ///////
            /////
            Device dev = channels.get(String.valueOf(channel));
            if (dev != null && dev.isDVR()) {
                if (dev.isUSB()) {
                    Log.d(Log.TAG, "红外机芯录制成功：" + file);
                } else {
                    Log.d(Log.TAG, "可见光机芯录制成功：" + file);
                    String fileName = new File(file).getName();
                    String adbCommand = "adb pull /storage/emulated/0/zhjinrui/spgp/data/1/" + fileName;
                    Log.d(Log.TAG, adbCommand);
                }
            }
            if (dev != null && dev.isCamera()) {
                Log.d(Log.TAG, "MIPI摄像头录制成功：" + file);
                powerOffMipiIfIdle("MIPI录制完成");
            }
            /////
            if (upload)
                uploadHandler.post(() -> spgProtocol.doUploadFile(timestamp, file, channel, 0, SPGProtocol.FILE_TYPE.VIDEO)); /////
//                uploadHandler.post(() -> spgProtocol.doUploadFile(timestamp, file, channel, 0, SPGProtocol.FILE_TYPE.VIDEO, captureType)); ///////
            else
                TaskManager.remove(file);

            /////
            boolean wakeupVideoHandled = handleWakeupVideoFinished(dev, channel, "录制成功");
            if (!wakeupVideoHandled && dev != null && dev.isDVR()) {
                if (dev.isUSB()) {
                    sleepDevice(channel, "红外机芯录制成功");
                } else {
                    sleepDevice(channel, "可见光机芯录制成功");
                }
            }

            synchronized (videoQueue) {
                isVideoTaskRunning = false;
            }
            tryStartNextVideoTask();

            /////
        }

        @Override
        public void onVideoFailed(int channel, String filename) {
            finishTask(filename);

            /////
            Device dev = channels.get(String.valueOf(channel));
            boolean wakeupVideoHandled = handleWakeupVideoFinished(dev, channel, "录制失败");
            if (!wakeupVideoHandled && dev != null && dev.isDVR()) {
                if (dev.isUSB()) {
                    sleepDevice(channel, "红外录制失败");
                } else {
                    sleepDevice(channel, "可见光录制失败");
                }
            }
            if (dev != null && dev.isCamera()) {
                powerOffMipiIfIdle("MIPI录制失败");
            }
            /////
        }

        @Override
        public void onFrame(Bitmap bitmap) {
            showPreview(bitmap);
        }


        private void decodeH264(byte[] data) {
            try {
                byte[] frame = rtph264.decode(data, data.length);
                if (frame == null) return;

                int inputBufferIndex = mediaDecoder.dequeueInputBuffer(0);
                if (inputBufferIndex >= 0) {  // 当输入缓冲区有效时，就是>=0
                    ByteBuffer inputBuffer = mediaDecoder.getInputBuffer(inputBufferIndex);
                    inputBuffer.put(frame, 0, frame.length);
                    mediaDecoder.queueInputBuffer(inputBufferIndex, 0, frame.length, System.nanoTime() / 1000, 0);
                } else {
                    Log.w(Log.TAG, "解码缓冲区不足");
                }

                int outputBufferIndex = mediaDecoder.dequeueOutputBuffer(bufferInfo, 0);  // 拿到输出缓冲区的索引
                if (outputBufferIndex >= 0) {
                    mediaDecoder.releaseOutputBuffer(outputBufferIndex, true);
                }
            } catch (Exception e) {
                Log.i(Log.TAG, "解码异常：" + e);
                releaseDecoder();
            }
        }


        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onFrame(Device dev, byte[] data) {
            if (dev.streamClient != null) dev.streamClient.sendPack(data);
            if (mediaDecoder != null) decodeH264(data);
        }

        @Override
        public void onObjectDetect(DetectInfo info) {
//            uploadHandler.post(()->{
//                spgProtocol.alertObjectDetect(info);
//            });
            spgProtocol.doAlertObjectDetect(info); /////
            Settings.AIAction[] aas = settings.aiActions.get(String.format("%d,%d", info.channel, info.preset));
            if (aas == null) return;
            // 收集当前帧中出现的告警类型
            List<Integer> detectedAlertTypes = new ArrayList<>();
            for (Settings.ObjectInfo obj : info.objects) {
                detectedAlertTypes.add((int) obj.classID);
            }
            // 对这些告警类型执行各自的联动动作（只执行一次）
            for (Settings.AIAction aiAction : aas) {
                if (aiAction != null && detectedAlertTypes.contains(aiAction.alertType)) {
                    switch (aiAction.alertAction) {
                        case 0:
                            // 无联动
                            break;
                        case 1:
                            // 录像特定时间的短视频
                            SystemClock.sleep(3000);  // 确保抓拍完毕
                            if (deviceConfig.toCheck) {
                                openShare("智能分析联动录像");
                            }
                            takeVideo(info.channel, 0, aiAction.alertParam1, true);
//                            takeVideo(info.channel, 0, aiAction.alertParam1, true, 2); ///////
                            break;
                        case 2:
                            // 拍多次照片，每次间隔几秒
                            for (int i = 0; i < aiAction.alertParam1; i++) {
                                SystemClock.sleep(3000);  // 确保抓拍完毕
                                if (deviceConfig.toCheck) {
                                    openShare("智能分析联动拍照");
                                }
                                Log.i(Log.TAG, "智能分析联动拍照");
                                takePhoto(info.channel, info.preset, false, null, false);
//                                takePhoto(info.channel, info.preset, null, false, false, false, 2); ///////
                                if (i != aiAction.alertParam1 - 1 && info.preset != 0) {
                                    if (aiAction.alertParam2 > 20) {
                                        SystemClock.sleep((aiAction.alertParam2 - 20) * 1000);  // 由于转到不为0的预置位要20秒，因此当拍照间隔大于20秒时，减去20秒
                                    }
                                }
                            }
                            break;
                        case 3:
                            // I/O一般指外部设备控制，比如：
                            // 输出一个电平信号到报警灯
                            // 触发蜂鸣器
                            // 开关某个外接设备
                            break;
                    }
                }
            }
            //////
            // 先不增加自动告警功能
//            uploadHandler.post(()->{
//                // 上传告警信息
//                spgProtocol.alertObjectDetect(info);
//                // 触发告警拍照
//                Device dev = channels.get(String.valueOf(info.channel));
//                // 触发告警录像（前后10秒）
//                //if (dev.isCamera()) {
//                if (dev.isDVR()) {
//                    // 拍照
//                    takePhoto(info.channel, info.preset, false, null);
//                    openShare("告警录制短视频");
//                    // 构造录像时间段
//                    long startMillis = info.time.timestamp - 10_000;  // 获取录像开始时间
//                    Date startDate = new Date(startMillis);
//                    long stopMillis = info.time.timestamp + 10_000;  // 获取录像结束时间
//                    Date stopDate = new Date(stopMillis);
//                    // 转成"yyyy-MM-dd-HH-mm-ss"格式字符串
//                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
//                    String startTime = sdf.format(startDate);
//                    String stopTime = sdf.format(stopDate);
//                    String fn = getFileName(info.channel, 0, EXT_MP4, 20);
//                    dev.playbackSaveFile(startTime, stopTime, fn);  // 上传告警录像
//                    closeShare("告警录制短视频");
//                }
//            });
            //////
        }

        @Override
        public void onFireAlarm(FireAlarmInfo info) {
            handleFireAlarmReport(info);
        }

        @Override
        public String onStatusInfo() {
            return getStatusText();
        }

        @Override
        public void onTemperatureReport(int channel, int preset, long timestamp, Vector<IRSetting.IrRegionTemp> temp) {
            Log.i(Log.TAG, "上报温度，通道：" + channel + "，预置位：" + preset);
            uploadHandler.post(() -> {
                String task = "onTemperatureReport";
                cpuLock();
                TaskManager.add(task);
                spgProtocol.doTempReport(channel, preset, timestamp, temp);
                finishTask(task);
            });
        }
    };
    /////
    private long lastErrorLogTime = 0;
    private long errorLogInterval = 1000;  // 初始间隔1秒
    private int fileUploadFailedTimes = 0;

    private static synchronized boolean initOpenCV() {
        if (!isOpenCVInitialized) {
            isOpenCVInitialized = OpenCVLoader.initDebug();
            Log.i(Log.TAG, "OpenCV初始化结果：" + isOpenCVInitialized);
        }
        return isOpenCVInitialized;
    }

    /**
     * 2021-7-7，佛山要求显示相关数据，但单路板没有提供传感器和采样功能
     * 按肖工、张工要求使用佛山当地的月平均气温、月平均湿度作为基准并按上下指定的比率浮动
     * 温度，湿度数据每小时变动一次
     *
     * @return
     */
    private static float getFakeTemperature() {
        if (System.currentTimeMillis() - lastFakeTime > 1000 * 3600) {
            int[] monthTemp = {19, 20, 23, 28, 32, 34, 34, 35, 34, 30, 25, 20};
            Calendar now = Calendar.getInstance();
            int month = now.get(Calendar.MONTH);
            if (month >= 0 && month < 12) // 温度: ± 2 ℃ 内变动
                fakeTemperature = monthTemp[month] + random(0, 2);
            lastFakeTime = System.currentTimeMillis();
        }
        return fakeTemperature;
    }

    private static float getFakeHumidity() {
        if (System.currentTimeMillis() - lastFakeTime > 1000 * 3600) {
            int[] monthHumidity = {72, 78, 82, 84, 84, 84, 82, 82, 78, 72, 66, 66};
            Calendar now = Calendar.getInstance();
            int month = now.get(Calendar.MONTH);
            if (month >= 0 && month < 12) // 湿度: ± 5% 内变动
                fakeHumidity = monthHumidity[month] + random(0, 5);
        }
        return fakeHumidity;
    }

    /////
    private static String readJsonFile(String filename) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        }
        return content.toString();
    }

    private static void loadLocationFromConfig() {
        try {
            String jsonString = readJsonFile(SETTING_FILE);
            if (jsonString != null) {
                JSONObject jsonObj = JSON.parseObject(jsonString);
                if (jsonObj != null) {
                    String configLocation = jsonObj.getString("location");
                    settings.location = configLocation != null ? configLocation : "";   // 防止配置文件中没有location对象，导致为null
                    return;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        settings.location = "";
    }

    private static String Location2String(Location location) {

                // return "位置000.000000E 000.000000N";  // 测试用例
                // return "";  // 测试用例

       if (settings.location == null || settings.location.isEmpty()) {
           loadLocationFromConfig();
       }
       return settings.location != null ? settings.location : "";

    }


    private static String aeroStatusText() {
        
        // return"气象28.3℃ 36.1%RH 20.1m/s 50°22.0mm 1001.1hPa\n";   // 测试用例
        // return"";   // 测试用例

       AeroInfo aeroInfo = aeroInfoAtomicReference.get();

       if (aeroInfo == null) return "";
       try {
           switch (deviceConfig.aeroDevice) {
               case 1:
               case 2:
                   if (aeroInfo.WindSpeed < 0.1) aeroInfo.WindDirection = 0;

                   return String.format("微气象%.1f℃%.1fRh%.0fhPa%.1fm/s%d˚%.1fmm\n",
                           aeroInfo.Temp, aeroInfo.Humidity, aeroInfo.AtomosPress, aeroInfo.WindSpeed,
                           aeroInfo.WindDirection, aeroInfo.RainFall);
               case 3:
               case 4:
                   return String.format("气象%.1f℃/%.1f%%/%.0fhPa/%.1fm/s",
                           aeroInfo.Temp, aeroInfo.Humidity, aeroInfo.AtomosPress, aeroInfo.WindSpeed);
               case 5:
                   return String.format("气象%.1f℃/%.1f%%/%.1fm/s/%d˚/%.1fmm/%.0fhPa",
                           aeroInfo.Temp, aeroInfo.Humidity, aeroInfo.WindSpeed,
                           aeroInfo.WindDirection, aeroInfo.RainFall, aeroInfo.AtomosPress);
               case 6:

                   return String.format(
                           "气象%.1f℃ %.1f%%RH %.1fm/s %d° %.1fhPa\n",
                           aeroInfo.Temp,
                           aeroInfo.Humidity,
                           aeroInfo.WindSpeed,
                           aeroInfo.WindDirection,
                           aeroInfo.AtomosPress
                   );

               case 7:

                   return String.format(
                           "气象%.1f℃ %.1f%%RH %.1fm/s %d° %.1fmm %.1fhPa\n",
                           aeroInfo.Temp,
                           aeroInfo.Humidity,
                           aeroInfo.WindSpeed,
                           aeroInfo.WindDirection,
                           aeroInfo.RainFall,
                           aeroInfo.AtomosPress
                   );

               default:
                   return "";
           }
       } catch (Exception e) {
           Log.e(Log.TAG, "微气象osd出错：" + e.getMessage());
           return "";
       }
    }

    private static boolean isOsdTextEmpty(String text) {
        return text == null || text.trim().isEmpty();
    }

    private static String appendLineBreakIfNeeded(String text) {
        if (isOsdTextEmpty(text)) return "";
        return text.endsWith("\n") ? text : text + "\n";
    }

    private static String getAeroLocationText(String aeroText, String locationText) {
        boolean aeroEmpty = isOsdTextEmpty(aeroText);
        boolean locationEmpty = isOsdTextEmpty(locationText);

        int osdCase = 0;
        if (aeroEmpty) osdCase += 1;
        if (locationEmpty) osdCase += 2;

        switch (osdCase) {
            case 1:
                return appendLineBreakIfNeeded(locationText);
            case 2:
                return appendLineBreakIfNeeded(aeroText);
            case 3:
                return "";
            case 0:
            default:
                return appendLineBreakIfNeeded(aeroText) + appendLineBreakIfNeeded(locationText);
        }
    }


    // 画面附加信息内容，如电量，流量，电压等
    public static String getStatusText() {

        Float cpuTemp = AndroidThermalMonitor.getCpuTemperature();

        try {
            if (deviceConfig.onlyShowBat) {
                if (deviceConfig.chargeControl == 6 || deviceConfig.chargeControl == 9) {
                    return String.format("余电 %d%%", batPrecent);
                } else {
                    return String.format("余电 %d%%", getBatPercent());
                }
            } else {
                long trafficLeft = 1l * deviceConfig.traffic * 1000 * 1000 * 1000 - getMonthTraffic();
                if (trafficLeft < 0) trafficLeft = 0;
                String firmwareVersion = Build.DISPLAY;  // 固件版本
                // 使用正则表达式寻找版本号
                Pattern pattern = Pattern.compile("V\\d+\\.\\d+(?=-userdebug)");
                Matcher matcher = pattern.matcher(firmwareVersion);
                if (matcher.find()) {
                    firmwareVersion = String.format("固件%s", matcher.group());  // 提取匹配的版本号
                } else {
                    firmwareVersion = "";  // 如果找不到，就设为空
                }
                /////
                if (deviceConfig.chargeControl == 8) {
                    tempEnvironment = 23;
                }

                // 如果有充电控制器，所有数据均可以正常获取，如果是单路板，需要使用CPU电压等替代
                if (deviceConfig.chargeControl == 6 || deviceConfig.chargeControl == 9) {
                    if (deviceConfig.chargeControl == 6) {
                        tempEnvironment = Math.max(tempEnvControl, tempEnvRegion);  // 汇能精电/硕日控制器温度与环境温度框的最高温比较，取最大值
                        String extraText = getAeroLocationText(aeroStatusText(), Location2String(devLocation));

                        return String.format(
                                "通信%s %s %ddBm %s %s\n" +                 // 第二行：通信
                                        "电池%3.2fV/%2.2fA/%d%%/%3.1f℃/%3.1f℃\n" +        // 第三行：电池
                                        "太阳能%3.1fV/%2.2fA/%2.2fA\n" +       // 第四行：太阳能
                                        "%s" +                                   // 微气象和位置
                                        "软件V%s %s %d",                       // 第七行：版本

                                netType,
                                SIGNAL_LEVELS[signalLevel],
                                signalDBM,
                                humanReadableByteCount(trafficLeft, false),
                                subString(iccid, 15),

                                batVoltage,
                                batAmper,
                                batPrecent,
                                temperature,
                                cpuTemp,

                                solarVoltage,
                                solarAmpler,
                                loadAmpler,

                                extraText,

                                BuildConfig.VERSION_NAME,
                                firmwareVersion,
                                deviceConfig.wifi ? 1 : 0
                        );
                    }

                    if (aeroStatusText() == null || aeroStatusText().trim().isEmpty()) {

                        // 不带微气象
                        return String.format(
                                "通信%s %s %ddBm %s %s\n" +                 // 第二行：通信
                                        "电池%3.2fV/%2.2fA/%d%%/%3.1f℃/%3.1f℃\n" +        // 第三行：电池
                                        "太阳能%3.1fV/%2.2fA/%2.2fA\n" +       // 第四行：太阳能
                                        "%s\n" +                                   // 第六行：位置
                                        "软件V%s %s %d",                       // 第七行：版本

                                netType,
                                SIGNAL_LEVELS[signalLevel],
                                signalDBM,
                                humanReadableByteCount(trafficLeft, false),
                                subString(iccid, 15),

                                batVoltage,
                                batAmper,
                                batPrecent,
                                temperature,
                                cpuTemp,

                                solarVoltage,
                                solarAmpler,
                                loadAmpler,

                                Location2String(devLocation),

                                BuildConfig.VERSION_NAME,
                                firmwareVersion,
                                deviceConfig.wifi ? 1 : 0
                        );

                    } else {

                        // 带微气象
                        return String.format(
                                "通信%s %s %ddBm %s %s\n" +                 // 第二行：通信
                                        "电池%3.2fV/%2.2fA/%d%%/%3.1f℃/%3.1f℃\n" +               // 第三行：电池（不显示温度）
                                        "太阳能%3.1fV/%2.2fA/%2.2fA\n" +       // 第四行：太阳能
                                        "%s" +                                     // 第五行：微气象（内部带\n）
                                        "%s\n" +                                   // 第六行：位置
                                        "软件V%s %s %d",                       // 第七行：版本

                                netType,
                                SIGNAL_LEVELS[signalLevel],
                                signalDBM,
                                humanReadableByteCount(trafficLeft, false),
                                subString(iccid, 15),

                                batVoltage,
                                batAmper,
                                batPrecent,
                                temperature,
                                cpuTemp,

                                solarVoltage,
                                solarAmpler,
                                loadAmpler,

                                aeroStatusText(),

                                Location2String(devLocation),

                                BuildConfig.VERSION_NAME,
                                firmwareVersion,
                                deviceConfig.wifi ? 1 : 0
                        );
                    }


                    // 如果是三路板，显示一二路电流
                } else if (deviceConfig.chargeControl == 3) {
                    if (aeroStatusText() == null || aeroStatusText().trim().isEmpty()) {
                        return String.format("%s %s %ddB 余%s ID %s \n软件V%s %s  %d\n太阳能%3.1fV/%2.2fA 电池%3.2fV/%2.2fA \n负载%2.2fA/%2.2fA %d%% 温度%.0f℃ \n%s",
                                netType, SIGNAL_LEVELS[signalLevel], signalDBM, humanReadableByteCount(trafficLeft, false),
                                subString(iccid, 15), BuildConfig.VERSION_NAME, firmwareVersion, deviceConfig.wifi ? 1 : 0,
                                solarVoltage, solarAmpler, batVoltage, batAmper, loadAmpler1, loadAmpler2, getBatPercent(), temperature, /////
                                aeroStatusText(),
                                Location2String(devLocation));
                    } else {
                        return String.format("%s %s %ddB 余%s ID %s \n软件V%s %s  %d\n太阳能%3.1fV/%2.2fA 电池%3.2fV/%2.2fA \n负载%2.2fA/%2.2fA %d%% 温度%.0f℃ \n%s%s",
                                netType, SIGNAL_LEVELS[signalLevel], signalDBM, humanReadableByteCount(trafficLeft, false),
                                subString(iccid, 15), BuildConfig.VERSION_NAME, firmwareVersion, deviceConfig.wifi ? 1 : 0,
                                solarVoltage, solarAmpler, batVoltage, batAmper, loadAmpler1, loadAmpler2, getBatPercent(), temperature, /////
                                aeroStatusText(),
                                Location2String(devLocation));
                    }
                } else if (deviceConfig.chargeControl == 7) {
                    if (aeroStatusText() == null || aeroStatusText().trim().isEmpty()) {
                        return String.format("%s %s %ddBm 余%s ID %s\n软件V%s %s  %d\n太阳能%3.1fV/%2.2fA 负载%2.2fA\n电池%3.1fV/%2.2fA  %d%% 温度%3.1f℃\n%s",
                                netType, SIGNAL_LEVELS[signalLevel], signalDBM, humanReadableByteCount(trafficLeft, false), subString(iccid, 15),
                                BuildConfig.VERSION_NAME, firmwareVersion, deviceConfig.wifi ? 1 : 0, solarVoltage, solarAmpler, loadAmpler, batVoltage, batAmper,
                                getBatPercent(), temperature, Location2String(devLocation)
                        );
                    } else {
                        return String.format("%s %s %ddBm 余%s ID %s\n软件V%s %s  %d\n太阳能%3.1fV/%2.2fA 负载%2.2fA\n电池%3.1fV/%2.2fA  %d%% 温度%3.1f℃\n%s%s",
                                netType, SIGNAL_LEVELS[signalLevel], signalDBM, humanReadableByteCount(trafficLeft, false), subString(iccid, 15),
                                BuildConfig.VERSION_NAME, firmwareVersion, deviceConfig.wifi ? 1 : 0, solarVoltage, solarAmpler, loadAmpler, batVoltage, batAmper,
                                getBatPercent(), temperature, aeroStatusText(), Location2String(devLocation)
                        );
                    }
                } else if (deviceConfig.chargeControl == 8) {

                    String extraText = getAeroLocationText(aeroStatusText(), Location2String(devLocation));

                    return String.format(
                            "通信%s %s %ddBm %s %s\n" +                 // 第二行
                                    "负载%2.2fA/%3.1f℃\n" +         // 第三行
                                    "%s" +                                    // 微气象和位置
                                    "软件V%s %s %d",                        // 第七行

                            netType,
                            SIGNAL_LEVELS[signalLevel],
                            signalDBM,
                            humanReadableByteCount(trafficLeft, false),
                            subString(iccid, 15),
                            loadAmpler,

                            cpuTemp,

                            extraText,

                            BuildConfig.VERSION_NAME,
                            firmwareVersion,
                            deviceConfig.wifi ? 1 : 0
                    );
                } else {
                    // CPU电压映射为电池电压： 4.3v => 12.6v ， CPU电压低于3.9V，安卓板可能无法正常工作了
                    return String.format("%s %s %ddB 余%s ID%s 软件V%s %s  %d\n电池%3.1fV/%2.2fA 负载%2.2fA %d%% 温度%.0f℃ 湿度%.0f%%%s%s",//\n,%s", /////
                            netType, SIGNAL_LEVELS[signalLevel], signalDBM, humanReadableByteCount(trafficLeft, false),
                            subString(iccid, 15), BuildConfig.VERSION_NAME, firmwareVersion, deviceConfig.wifi ? 1 : 0,
                            5.80, 0.12, -0.12, 80, getFakeTemperature(), getFakeHumidity(),
                            aeroStatusText(),
                            Location2String(devLocation));
                }
            }
        }catch (Exception e){
            Log.e(Log.TAG,e.getMessage());
            return "";
        }
    }


    public static long getMonthTraffic() {
        // 程序启动的时候读取保存的本月基准流量，然后只要不重启，应该总是保存启动时的值 + 本次启动后的发送/接收总流量并保存
        return thisMonthTraffic + TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes();
    }


    public static String[] extractPowerOnStrings() {
        return new String[]{"00:00:00"}; /////
    }

    public static String[] extractPowerOffStrings() {
        return new String[]{"23:59:59"}; /////
    }

    private static long getCurrentSecondsOfDay() {
        java.time.LocalTime now = java.time.LocalTime.now();
        return now.toSecondOfDay();
    }

    public static int extractRecordPreset(List<Settings.VideoTimeItem> videoTimeTable) {
        long nowInSeconds = getCurrentSecondsOfDay();
        for (Settings.VideoTimeItem item : videoTimeTable) {
            long startSeconds = item.hour * 3600 + item.min * 60 + item.sec;
            long endSeconds = startSeconds + item.duration;
            if (nowInSeconds >= startSeconds && nowInSeconds <= endSeconds) {
                if (item.action == 0) {
                    return item.para;
                }
            }
        }
        return 0;  // 没有符合条件的，使用默认的预置位0为录像预置位
    }

    public static void applyLegacyFieldsFromJson(String jsonStr, IRSetting setting) {
        if (setting == null || jsonStr == null) return;

        JSONObject root = JSONObject.parseObject(jsonStr);
        JSONObject sensor = root.getJSONObject("sensorConfig");
        if (sensor == null) return;

        // 如果 JSON 中包含这些字段，就将值从 sensor 中提取出来设置到 setting 顶层
        if (sensor.containsKey("comAlarm")) {
            setting.comAlarm = sensor.getByteValue("comAlarm");
        }
        if (sensor.containsKey("envAlarm")) {
            setting.envAlarm = sensor.getByteValue("envAlarm");
        }
        if (sensor.containsKey("focalLen")) {
            setting.focalLen = sensor.getByteValue("focalLen");
        }
        if (sensor.containsKey("globalAlarm")) {
            setting.globalAlarm = sensor.getByteValue("globalAlarm");
        }
        if (sensor.containsKey("imageStitch")) {
            setting.imageStitch = sensor.getByteValue("imageStitch");
        }
        if (sensor.containsKey("thresholdAlarm")) {
            setting.thresholdAlarm = sensor.getByteValue("thresholdAlarm");
        }
        if (sensor.containsKey("resolution")) {
            setting.resolution = sensor.getByteValue("resolution");
        }
    }
    /////

    private static int convertToSeconds(byte hour, byte min, byte sec) {
        return hour * 3600 + min * 60 + sec;
    }

    // 将总秒数转换回小时/分/秒，并设置到对象中
    private static void convertToHMS(int totalSeconds, Settings.VideoTimeItem item) {
        // 限制在当天范围内（0-86399秒）
        totalSeconds = Math.max(0, Math.min(totalSeconds, 86399));
        int remaining = totalSeconds;
        item.hour = (byte) (remaining / 3600);
        remaining %= 3600;
        item.min = (byte) (remaining / 60);
        item.sec = (byte) (remaining % 60);
    }

    public static List<Settings.VideoTimeItem> adjustOverlappingItems(List<Settings.VideoTimeItem> originalList) {
        if (originalList == null || originalList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Settings.VideoTimeItem> adjustedList = new ArrayList<>();
        for (Settings.VideoTimeItem original : originalList) {
            Settings.VideoTimeItem copy = new Settings.VideoTimeItem();
            copy.channel = original.channel;
            copy.stream = original.stream;
            copy.action = original.action;
            copy.para = original.para;
            copy.duration = original.duration;
            copy.hour = original.hour;
            copy.min = original.min;
            copy.sec = original.sec;
            adjustedList.add(copy);
        }

        adjustedList.sort((item1, item2) -> {
            int sec1 = convertToSeconds(item1.hour, item1.min, item1.sec);
            int sec2 = convertToSeconds(item2.hour, item2.min, item2.sec);
            return Integer.compare(sec1, sec2);
        });


        int prevEndSec = 0;
        for (Settings.VideoTimeItem item : adjustedList) {

            int originalStartSec = convertToSeconds(item.hour, item.min, item.sec);

            int newStartSec = Math.max(originalStartSec, prevEndSec);

            // 限制持续时间为最大1小时（3600秒），匹配示例逻辑
//            item.duration = Math.min(item.duration, 3600);

            int newEndSec = newStartSec + item.duration;
            if (newEndSec > 86399) {
                item.duration = 86399 - newStartSec;
                newEndSec = 86399;
            }

            if (item.duration <= 0) {
                continue;
            }

            convertToHMS(newStartSec, item);

            prevEndSec = newEndSec;
        }

        adjustedList.removeIf(item -> item.duration <= 0);

        return adjustedList;
    }


    private static int getBatPercent() {
        if (Math.abs(amplerHours) < 0.001) return 0;

        return (amplerHours > 0 && batteryCapability > 0 && amplerHours < batteryCapability)
                ? Math.round((amplerHours * 100 / batteryCapability)) : 100;
    }


    private static void powerControlNVR(final boolean powerOn, int load) {
        /////
        if (deviceConfig.chargeControl <= 1) return;

        else if ((deviceConfig.chargeControl == 2 || deviceConfig.chargeControl == 3) && load == 2) {
            for (int i = 0; i < 3; i++) {
                int errcode1 = RS485Impl.Instance().powerControl(254, powerOn);
                Log.i(Log.TAG, String.format("%s负载%s，错误码：%d", powerOn ? "开启" : "关闭", errcode1 == 0 ? "成功" : "失败", errcode1));
                if (errcode1 == 0) {
                    sleeping = !powerOn;
                    for (Device dev : channels.values()) {
                        if (dev.isUSB()) {
                            sleepingIr = !powerOn;
                        }
                    }
                    break;
                }
                ///////
                if (deviceConfig.toCheck) {
                    for (Device dev : channels.values()) {
                        if (dev.isUSB()) {
                            SystemClock.sleep(500);
                            ////////
                            for (int j = 0; j < 3; j++) {
                                boolean errcode2 = RS485Impl.Instance().gpioCloseUSB();
                                Log.i(Log.TAG, String.format("USB下电%s", errcode2 ? "成功" : "失败"));
                                if (errcode2) {
                                    usbPowerState = false;
                                    writeUsbPowerState(false);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } else if (deviceConfig.chargeControl >= 6) {
            if (powerOn) {
                if (load == 2) {
                    ////////
                    for (int i = 0; i < 3; i++) {
                        boolean errcode = RS485Impl.Instance().gpioOpenLoad2();
                        Log.i(Log.TAG, String.format("云台上电%s", errcode ? "成功" : "失败"));
                        if (errcode) {
                            sleeping = false;
                            break;
                        }
                    }
                    ////////
                } else if (load == 3) {

                    // 从休眠模式唤醒的话需要给USB上电   // 后续可以添加状态进行判断
                    if (!usbPowerState) {
                        for (int i = 0; i < 3; i++) {
                            boolean errcode = RS485Impl.Instance().gpioOpenUSB();
                            Log.i(Log.TAG, String.format("USB上电%s", errcode ? "成功" : "失败"));
                            if (errcode) {
                                usbPowerState = true;
                                writeUsbPowerState(true);
                                break;
                            }
                        }
                        SystemClock.sleep(500);
                    }

                    ////////
                    for (int i = 0; i < 3; i++) {
                        boolean errcode1 = RS485Impl.Instance().gpioOpenLoad3();
                        Log.i(Log.TAG, String.format("红外上电%s", errcode1 ? "成功" : "失败"));
                        if (errcode1) {
                            sleepingIr = false;
                            break;
                        }
                    }
                    ////////
//                    SystemClock.sleep(500);
                    ///////
//                    if (deviceConfig.toCheck) {
//                        for (int i = 0; i < 3; i++) {
//                            boolean errcode2 = RS485Impl.Instance().gpioOpenUSB();
//                            Log.i(Log.TAG, String.format("USB上电%s", errcode2 ? "成功" : "失败"));
//                            if (errcode2) {
//                                break;
//                            }
//                        }
//                    }
                    ///////
                }
            } else {
                if (load == 2) {
                    ////////
                    for (int i = 0; i < 3; i++) {
                        boolean errcode = RS485Impl.Instance().gpioCloseLoad2();
                        Log.i(Log.TAG, String.format("云台下电%s", errcode ? "成功" : "失败"));
                        if (errcode) {
                            sleeping = true;
                            break;
                        }
                    }
                    ////////
                } else if (load == 3) {
                    ///////
                    if (deviceConfig.toCheck) {
                        for (int i = 0; i < 3; i++) {
                            SystemClock.sleep(500);
                            boolean errcode1 = RS485Impl.Instance().gpioCloseUSB();
                            Log.i(Log.TAG, String.format("USB下电%s", errcode1 ? "成功" : "失败"));
                            if (errcode1) {
                                usbPowerState = false;
                                writeUsbPowerState(false);
                                break;
                            }
                        }
                    }
                    ///////
                    for (int i = 0; i < 3; i++) {
                        boolean errcode2 = RS485Impl.Instance().gpioCloseLoad3();
                        Log.i(Log.TAG, String.format("红外下电%s", errcode2 ? "成功" : "失败"));
                        if (errcode2) {
                            sleepingIr = true;
                            break;
                        }
                    }
                }
            }
        }
        /////
    }
    /////

    private static void closeDevicesForPowerOff(int load) {
        for (Device dev : channels.values()) {
            if (dev == null || !dev.isDVR()) continue;

            if (load == 23
                    || (load == 2 && !dev.isUSB())
                    || (load == 3 && dev.isUSB())) {
                // 负载下电前先清掉设备运行态，避免下次上电后复用旧的 onvifReady/session，跳过 isDeviceReady 等待。
                dev.close();
            }
        }
    }

    public void writeNum(String flag, String path) {
        FileWriter fw = null;
        try {
            fw = new FileWriter(path);
            fw.write(flag);
            fw.flush();
        } catch (IOException e) {
            Log.e(Log.TAG, "e::::::" + e);
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e) {
                    Log.e(Log.TAG, "e:::::::" + e);
                }
            }
        }
    }


    public void updateOnline(String reason) {
        if (settings == null) return;

        if (deviceConfig.toCheck) {
            onlineEnd = System.currentTimeMillis() + 30 * PERIOD_MINUTE;
        } else {
            onlineEnd = System.currentTimeMillis() + 15 * PERIOD_MINUTE;
//            onlineEnd = System.currentTimeMillis() + 60 * PERIOD_MINUTE;
        }

        Log.w(Log.TAG, String.format("更新在线时间：%s，超过 %s 后关闭视频", reason, formatDateTime(onlineEnd)));
    }


    @SuppressLint("MissingPermission")
    private void initBDS() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        // 6735检查权限会异常出错
        if (!is6735 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && checkSelfPermission
                (Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.i(Log.TAG, "BDS初始化失败，定位功能未授权");
            return;
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.i(Log.TAG, "BDS初始化失败，定位功能未开启");
            return;
        }
        locationManager.addGpsStatusListener(new GpsStatus.Listener() {
            @Override
            public void onGpsStatusChanged(int event) {
                switch (event) {
                    // 第一次定位
                    case GpsStatus.GPS_EVENT_FIRST_FIX:
                        break;
                    // 卫星状态改变
                    case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                        break;
                    // 定位启动
                    case GpsStatus.GPS_EVENT_STARTED:
                        //Log.i(Log.TAG, "定位启动");
                        break;
                    // 定位结束
                    case GpsStatus.GPS_EVENT_STOPPED:
                        //Log.i(Log.TAG, "定位结束");
                        break;
                }
            }
        });


//        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30 * PERIOD_MINUTE, 50, new LocationListener() {
        // 把监听器写成成员变量
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                boolean moved = false;
                double distance = devLocation != null ? devLocation.distanceTo(location) : 0;
                if (distance > deviceConfig.distance) {
                    moved = true;
                    Log.i(Log.TAG, String.format("上次位置：%.4f %.4f，本次位置：%.4f %.4f，设备移动%f米超过门限值%d，拍照上传",
                            devLocation.getLongitude(), devLocation.getLatitude(),
                            location.getLongitude(), location.getLatitude(), distance, deviceConfig.distance));
                    for (String channel : channels.keySet()) {
                        Device device = channels.get(String.valueOf(channel)); /////
                        if (device.isDVR() || device.isCamera()) {
                            Log.i(Log.TAG, "BDS改变拍照");
                            ///
                            runValidBusiness(() -> {
                                takePhoto(Integer.valueOf(channel), 0);
                            });
                            ///
                            break;
                        }
                    }
                }
                devLocation = location;
//                spgProtocol.doReportBds(location, moved);

                for (String channel : channels.keySet()) {
                    spgProtocol.doReportBds(location, moved, Integer.valueOf(channel));
                }

                Log.i(Log.TAG, String.format("BDS位置更新，经度：%.4f，纬度：%.4f，海拔：%.1f",
                        location.getLongitude(), location.getLatitude(), location.getAltitude()));

                /////
                settings.location = String.format("位置%.6fE %.6fN", location.getLongitude(), location.getLatitude());
                saveSettings(settings, SETTING_FILE);
                /////
            }


            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                switch (status) {
                    case LocationProvider.AVAILABLE:
                        //Log.i(Log.TAG, "BDS为可用状态");
                        break;
                    case LocationProvider.OUT_OF_SERVICE:
                        //Log.i(Log.TAG, "BDS为无服务状态");
                        break;
                    case LocationProvider.TEMPORARILY_UNAVAILABLE:
                        //Log.i(Log.TAG, "BDS为暂时不可用");
                        break;
                }
            }

            /**
             * BDS开启时触发
             */
            @Override
            public void onProviderEnabled(String provider) {
                //Log.i(Log.TAG, "BDS开启");
            }

            /**
             * BDS禁用时触发
             */
            @Override
            public void onProviderDisabled(String provider) {
                //Log.i(Log.TAG, "BDS关闭");
            }
//        });
        };
        // 注册监听器
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, settings.onlineCfg.sample * PERIOD_MINUTE, 50, locationListener); /////
        Log.i(Log.TAG, "BDS初始化成功");
    }


    private void getBatteryCapability() {
        for (int i = 0; i < 3; i++)
            if (batteryCapability < 0) {
                String pbVersion = RS485Impl.Instance().getVersion();
                if (pbVersion != null) {
                    Matcher matcher = Pattern.compile("(?<=_).*?(?=AH)").matcher(pbVersion);
                    if (matcher.find()) {
                        String batteryCap = matcher.group();
                        batteryCapability = Integer.valueOf(batteryCap);
                    }
                }
                Log.i(Log.TAG, String.format("供电板版本：%s，电池容量：%.2fAh", pbVersion, batteryCapability));
            }
    }

    ///////
    private void applyPowerTuning() {
        new Thread(() -> {
            try {
                Log.i(Log.TAG, "开始应用低功耗内核参数");
//                su("echo 0 > /proc/ppm/enabled");
//                su("echo powersave > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor");
//                su("echo powersave > /sys/devices/system/cpu/cpufreq/policy4/scaling_governor");
//                su("echo mem > /sys/power/autosleep");
                su("echo 1 > /proc/ppm/enabled");
                su("echo \"1 1\" > /proc/ppm/policy/ut_fix_core_num");
                su("echo \"15 15\" > /proc/ppm/policy/ut_fix_freq_idx");
                Log.i(Log.TAG, "低功耗内核参数应用完成");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void revertPowerTuning() {
        new Thread(() -> {
            try {
                Log.i(Log.TAG, "开始移除低功耗内核参数");
//                su("echo off > /sys/power/autosleep");
//                su("echo schedplus > /sys/devices/system/cpu/cpufreq/policy4/scaling_governor");
//                su("echo schedplus > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor");
//                su("echo 0 > /proc/ppm/enabled");

                su("echo \"-1 -1\" > /proc/ppm/policy/ut_fix_core_num");
                su("echo \"-1 -1\" > /proc/ppm/policy/ut_fix_freq_idx");

//                su("echo 0 > /proc/ppm/enabled");      //TODO 测试一下这个注释这句的情况下，非抽检模式，通道三拉流

                Log.i(Log.TAG, "低功耗内核参数移除完成");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void startTemperatureMonitoring() {
        temperatureRunnable = new Runnable() {
            @Override
            public void run() {
                AndroidThermalMonitor.logAllThermalTemperatures();

                boolean isSafe = AndroidThermalMonitor.checkTemperatureSafety();
                if (!isSafe) {
                    Log.e(Log.TAG, "设备温度异常，请注意散热！");
                }

                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };

        handler.postDelayed(temperatureRunnable, UPDATE_INTERVAL);
    }


    private void loadVideoCodes() {


        try {
            String json = new String(Files.readAllBytes(new File(SETTING_FILE).toPath()), "UTF-8");

            JSONObject root = JSON.parseObject(json);

            JSONObject vcObj = root.getJSONObject("videoCodecs");

            for (String key : vcObj.keySet()) {
                JSONObject obj = vcObj.getJSONObject(key);

                VideoCodec v = obj.toJavaObject(VideoCodec.class);

                settings.videoCodecs.put(key, v);
            }

//            for (Map.Entry<String, VideoCodec> entry : settings.videoCodecs.entrySet()) {
//                String key = entry.getKey();
//                VideoCodec v = entry.getValue();
//
//                Point p = VideoCodec.getResolution(v.resolution);
//
//                Log.i(Log.TAG,
//                        "videoCodec -> key=" + key +
//                                ", resolutionIndex=" + v.resolution +
//                                ", width=" + p.x +
//                                ", height=" + p.y
//                );
//            }

        } catch (Exception e) {
            Log.e(Log.TAG, "解析videoCodecs失败"+e);
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this; ///
        Log.logDeviceInfo();

        setContentView(R.layout.activity_main);

        Log.i(Log.TAG, "程序启动，版本：" + appVersion(this));
        Log.i(Log.TAG, "数据存储目录：" + DATA_DIR);

        Log.e(Log.TAG,"============开机读取电源管理模块参数============");
        loadPowerStateFromFile();  // 开机读取配置文件，候选模式切换时间，以及当前的模式。


//        frequency
        if(DEBUG){
            frequency = readFrequency(); // 测试使用
            Log.e(Log.TAG,"电源管理模式电压转变次数："+frequency);
        }

        try{
            usbPowerState = readUsbPowerState();
            Log.e(Log.TAG,"usbPowerState::"+usbPowerState);
        }catch (Exception e){
            usbPowerState = false;
            writeUsbPowerState(false);
            Log.e(Log.TAG,"Exception::"+e.getMessage());
        }

        utilsThread.start();
        sendThread.start();
        uploadThread.start();
        serialThread.start();

        gimbalPowerThread.start();

        if (utilsHandler == null) utilsHandler = new Handler(utilsThread.getLooper());
        if (sendHandler == null) sendHandler = new Handler(sendThread.getLooper());
        if (uploadHandler == null) uploadHandler = new Handler(uploadThread.getLooper());
        if (serialHandler == null) serialHandler = new Handler(serialThread.getLooper());

        if (gimbalPowerHandler == null) gimbalPowerHandler = new Handler(gimbalPowerThread.getLooper());


        verifyStoragePermissions(this);  // 磁盘存储权限检查，没权限会弹出请求
        setSystemAutoTime(false);            // 启动时设置系统时间自动同步，防止重启后系统不会同步时间，确保时间先和网络同步

        new File(DATA_DIR).mkdirs();
        new File(FILE_PATH).mkdirs();
        reloadAfterCrash(this, null, LOG_FILE);   // 挂在所有线程异常处理，崩溃后自动重启程序

        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);


        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zhjinrui:spgp.WAKE_LOCK");


        SystemSettings.sleepAfter(this, 15);


//        SystemSettings.airplaneOff(this); ///
        // 打开BDS定位 ////////
        SystemSettings.bdsON(this);

        cleanFiles();

        initTrafficData();
        loadConfig();

        // loadConfig加载videocodec为空，这里添加一个函数进行加载
        loadVideoCodes();

        // 非汇能精电控制器，不使用电源模块管理
        if (deviceConfig.chargeControl != 6){
//            Log.i(Log.TAG, "deviceConfig.chargeControl：" + deviceConfig.chargeControl);
            currentMode = MODE_FULL;
        //    currentMode = MODE_WAKEUP;
        }

        AndroidThermalMonitor.logAllThermalTemperatures();

        startTemperatureMonitoring();


        if (!deviceConfig.toCheck) {
            boolean useRJ45 = false;
            for (Device dev : channels.values()) {
                if (dev.isDVR() && !dev.isUSB()) {
                    useRJ45 = true;
                }
            }
            if (useRJ45) {
                openShare("开机开启Share");
            } else {
                closeShare("开机关闭Share");
            }
        } else {
            closeShare("开机关闭Share");
        }

        startService(new Intent(this, WatchDog.class));   // 启动看门狗服务，防止主程序崩溃不启动

//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
//            su("/system/bin/my.sh");          // 更新6762板卡的路由避免局域网不通
        su("chmod 777 " + getPackageCodePath());          // 必要的ROOT，以便可以执行高权限指令
        su("chmod 777 /dev/mtgpio");                    // 必须修改，否则串口可能无法通信，6735串口设备名
        su("chmod 777 /dev/ttyS1");                    //  6762板设备串口设备
        su("df >> " + LOG_FILE);                         // 输出磁盘可用空间信息到日志
        su("ifconfig >> " + LOG_FILE);                         // 输出磁盘可用空间信息到日志
        su("netcfg >> " + LOG_FILE);                         // 输出磁盘可用空间信息到日志
        su("rm " + DATA_DIR + "*.apk");                      // 清理升级文件


        if (deviceConfig.toCheck) {
            applyPowerTuning();
        }

        // 注册广播消息接收器
        registerBoradcastReceiver();

        // 开始监听信号强度信息
        phoneStatListener = new PhoneStatListener();
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(phoneStatListener, PhoneStatListener.LISTEN_SIGNAL_STRENGTHS);

        initView();
        initCore();

        new Thread(() -> {
            wifiInit();

            switchSimCard(0);

            if (deviceConfig.chargeControl <= 1)
                return;
            RS485Impl.Instance().gpioInit(deviceConfig.mainBoard);  // 兼容一二代板
            if (deviceConfig.chargeControl == 3) {
                Log.i(Log.TAG, "供电板版本：" + RS485Impl.Instance().getVersion());
                loadCloseTime = "23:58:30";  // 三路板每天开安卓板时刻
            } else if (deviceConfig.chargeControl >= 6) {
                gimbalPowerHandler.post(() -> powerControlNVR(isWorkHour(), 2));  // 开启云台
                SystemClock.sleep(3000);
                if (!deviceConfig.toCheck) {
                    gimbalPowerHandler.post(() -> powerControlNVR(isWorkHour(), 3));  // 开启红外
                } else {
                    gimbalPowerHandler.post(() -> powerControlNVR(false, 3));  // 关闭红外
                }
                if (deviceConfig.chargeControl == 6) {
                    String[] devInfo = RS485Impl.Instance().getDevInfo();
                    Log.i(Log.TAG, "汇能精电：供电板公司名称" + devInfo[0] + "，设备型号" + devInfo[1] + "，设备版本号" + devInfo[2]);
                    int batQuantity = RS485Impl.Instance().getBatQuantity();
                    Log.i(Log.TAG, "汇能精电：剩余电量" + batQuantity + "%");
                    float batOutEveryday = RS485Impl.Instance().getBatOutEveryday();
                    Log.i(Log.TAG, "汇能精电：当天累计用电量" + batOutEveryday + "KWH");
                    float batInEveryday = RS485Impl.Instance().getBatInEveryday();
                    Log.i(Log.TAG, "汇能精电：当天累计充电量" + batInEveryday + "KWH");
                    int batTotalQuantity = RS485Impl.Instance().getBatTotalQuantity();
                    Log.i(Log.TAG, "汇能精电：电池总容量" + batTotalQuantity + "AH");
                    float[] voltageInfo = RS485Impl.Instance().getVoltageInfo();
                    Log.i(Log.TAG, "汇能精电：提升充电电压" + voltageInfo[0] + "V，浮充充电电压" + voltageInfo[1] + "V，提升恢复电压" + voltageInfo[2] + "V，低压断电恢复电压" + voltageInfo[3] + "V，低压断电电压" + voltageInfo[4] + "V，当日电池最高电压" + voltageInfo[5] + "V，当日电池最低电压" + voltageInfo[6] + "V");
                    loadOpenTime = RS485Impl.Instance().getLoadOpenTime();
                    loadCloseTime = RS485Impl.Instance().getLoadCloseTime();
                    Log.i(Log.TAG, "汇能精电：负载关时刻" + loadCloseTime + "，开时刻" + loadOpenTime);
                    if ((loadCloseTime == null) || loadCloseTime.isEmpty()) {
                        loadCloseTime = "23:57:00";
                    }
                    if ((loadOpenTime == null) || loadOpenTime.isEmpty()) {
                        loadOpenTime = "23:58:00";
                    }
                    String loadControlMode = RS485Impl.Instance().getLoadControlMode();
                    Log.i(Log.TAG, "汇能精电：负载输出控制模式为" + loadControlMode);
                    int timeSelect = RS485Impl.Instance().getTimeSelect() + 1;
                    Log.i(Log.TAG, "汇能精电：使用" + timeSelect + "个定时控制时间段");
                } else if (deviceConfig.chargeControl == 7) {
                    Log.i(Log.TAG, "供电板版本：" + RS485Impl.Instance().getVersion());
                    loadCloseTime = "23:58:30";  // 三路板每天开安卓板时刻
                }
            }
            /////

            initProtect();
            initReboot(-1);

            // 如果有气象仪，则启动时重启一次气象仪，以便复位雨量感应
            if (deviceConfig.aeroDevice != 0 && deviceConfig.aeroDevice != 6) resetAero();
            serialHandler.post(() -> getData()); /////

            // 开机同步供电板时钟
            serialHandler.post(() -> setRtc());

            // 开机同步供电板时钟
            // 开机不同步系统时钟，否则无信号情况下频繁重启apk，导致给电源板的时间都是一样的，这样电源板不能硬重启
            //serialHandler.post(() -> setRtc());

            /////
            for (Device device : channels.values()) {
                if (device.isDVR() && !device.isUSB()) {
                    setDVRTime();
                    moveRecordPreset();
                }
            }
        }).start();
//        updateOnline("程序启动完成，设备ID：" + deviceConfig.deviceId);     // 开机后更新一次关闭负载和视频等的时间
        onlineEnd = 0;
        Log.i(Log.TAG,"程序启动完成，设备ID：" + deviceConfig.deviceId);

//        utilsHandler.postDelayed(() -> {
////            if(currentMode == MODE_FULL && deviceConfig.chargeControl == 6){  // 只有在汇能精电下才有电源管理
//            if(currentMode == MODE_FULL){  // 只有在汇能精电下才有电源管理
//                Log.i(Log.TAG,"开始缓存");
//                cacheVideoFileList();
//            }  // 缓存信息
//        },1 * 60 * 1000); // 1分钟后还是全工作模式就开始缓存
//

        startAux5IdleMonitor();  // 每60秒执行1次，用来判断是否空闲满10分钟，然后关闭辅助开关5 ///
    }


    private void wifiInit() {
        ssid = buildNumericSsidFromDeviceId(deviceConfig.deviceId); /////
//            WifiAP wifiAP = WifiAP.getInstance(this, deviceConfig.deviceId, "11121314");
        WifiAP wifiAP = WifiAP.getInstance(this, ssid, "11121314"); /////
        if (deviceConfig.wifi) {
            if (wifiAP.isEnabled()) {
                wifiAP.disable();
                // 等待一段时间，让热点完全关闭，放置开机的时候，已经存在热点，修改了设备ID后，没有跟着变换
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // 然后开启热点
            wifiAP.enable();
        } else {
            wifiAP.disable();
        }
    }


    private void setDVRTime() {
        if (!isWorkHour()) return;

        if (deviceConfig.toCheck) {
            openShare("同步机芯时钟");
        }

        for (Device device : channels.values()) {
            if (device.isDVR() && !device.isUSB()) {
                Calendar now = Calendar.getInstance();
                device.setTime(now.get(Calendar.YEAR),
                        now.get(Calendar.MONTH) + 1,
                        now.get(Calendar.DAY_OF_MONTH),
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        now.get(Calendar.SECOND));
            }
        }
        if (deviceConfig.toCheck) {
            closeShare("同步机芯时钟");
        }
    }


    private void moveRecordPreset() {
        if (!isWorkHour()) {
            Log.e(Log.TAG, "moveRecordPreset：设备不在工作状态");
            return;
        }

        Device dev = channels.get(String.valueOf(1));
        doTimeConsumingTask("云台开机转到录像预置位", dev.isDVR());
        Log.e(Log.TAG, "云台开机转到录像预置位");
        dev.startMove(2, recordPreset, () -> {
            finishTimeConsumeTask("云台开机转到录像预置位", dev.isDVR());
        });
    }


    public String buildNumericSsidFromDeviceId(String deviceId) {
        String suffix4 = last4Digits(deviceId);
        String uniqueInput = DOMAIN + deviceId;
        long prefix = hmacToDigits(uniqueInput, PREFIX_LEN);
        String prefixStr = pad(prefix, PREFIX_LEN);
        return prefixStr + suffix4;
    }


    private long hmacToDigits(String input, int len) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key =
                    new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256");
            mac.init(key);
            byte[] out = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (out[i] & 0xFFL);
            }
            if (v < 0) v = -v;
            long mod = 1;
            for (int i = 0; i < len; i++) mod *= 10;
            return v % mod;
        } catch (Exception e) {
            throw new RuntimeException("HMAC错误", e);
        }
    }


    private String last4Digits(String s) {
        String digits = s.replaceAll("\\D", "");
        if (digits.length() >= 4) {
            return digits.substring(digits.length() - 4);
        }
        return pad(Long.parseLong(digits), 4);
    }


    private String pad(long v, int len) {
        String s = Long.toString(v);
        if (s.length() >= len) return s;
        StringBuilder sb = new StringBuilder(len);
        for (int i = s.length(); i < len; i++) sb.append('0');
        sb.append(s);
        return sb.toString();
    }


    // 该函数获取不到卡号里有字母的完整卡号，android的通病
    @Override
    public String getSimCardInfo() { /////
        if (iccid == null) {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    Log.i(Log.TAG, "未授予读卡权限");
                    return "";
                }
                iccid = telephonyManager.getSimSerialNumber();  // 该函数获取不到卡号里有字母的完整卡号，android的通病
                if (iccid == null || iccid.length() != 20) {
                    iccid = Utils.getSimCardICCID();
                }
                Log.i(Log.TAG, "获取sim卡号：" + iccid);
            } catch (Exception e) {
                Log.i(Log.TAG, "获取sim卡信息异常：" + e);
            }
        }
        return iccid;
    }


    private void getBatInfo() {
        String s = Utils.stringFromFile("/sys/class/power_supply/battery/device/FG_Battery_CurrentConsumption");
        batAmper = (s == null) ? 0.f : Integer.parseInt(s.trim()) / 100000.0f;
        loadAmpler = batAmper;

        s = Utils.stringFromFile("/sys/class/power_supply/battery/ChargerVoltage");
        solarVoltage = (s == null) ? 4.9f : Integer.parseInt(s.trim()) / 1000.0f;

        s = Utils.stringFromFile("/sys/class/power_supply/battery/batt_vol");
        batVoltage = (s == null) ? 4.29f : Integer.parseInt(s.trim()) / 1000.0f;

        s = Utils.stringFromFile("/sys/class/power_supply/battery/BatteryAverageCurrent");
        solarAmpler = s == null ? 0.025f : Float.valueOf(s) / 1000;

        s = Utils.stringFromFile("/sys/class/power_supply/battery/TemperatureR");
        String Temp = s == null ? "28" : s.trim();
        temperature = Float.valueOf(Temp);
        if (temperature > 70)
            temperature = 5.0f / 9 * (temperature - 32);

        humidity = 63;
        s = Utils.stringFromFile("/sys/class/power_supply/battery/status");
        String status = s == null ? "" : s.trim();
        if (status.equals("Charging")) {
            batAmper = -batAmper;
        } else
            solarVoltage = 0;
    }


    public boolean saveSettings(Object settings, String filename) {
        try {
            String s = JSON.toJSONString(settings, true);
            stringToFile(filename, s);
            return true;
        } catch (Exception e) {
            Log.e(Log.TAG, "保存配置失败 " + filename + " => " + e.getMessage());
            return false;
        }
    }


    public <Type> Type loadSettings(Type type, String filename) {
        try {
            String s = stringFromFile(filename);
            if (s != null) {
                Type settings = (Type) JSON.parseObject(s, type.getClass());
                if (settings != null) return settings;
            }
        } catch (Exception e) {
            Log.e(Log.TAG, "读取系统设置错误：" + e.getMessage());
        }
        return null;
    }

    private List<VideoTimeItem> copyVideoTimeTable(List<VideoTimeItem> source) {
        List<VideoTimeItem> result = new ArrayList<>();
        if (source == null) return result;
        for (VideoTimeItem item : source) {
            if (item == null) continue;
            VideoTimeItem copy = new VideoTimeItem();
            copy.channel = item.channel;
            copy.stream = item.stream;
            copy.action = item.action;
            copy.para = item.para;
            copy.duration = item.duration;
            copy.hour = item.hour;
            copy.min = item.min;
            copy.sec = item.sec;
            result.add(copy);
        }
        return result;
    }

    private boolean saveRecordingPolicyFile(List<VideoTimeItem> list) {
        try {
            stringToFile(RECORD_POLICY_FILE, JSON.toJSONString(copyVideoTimeTable(list), true));
            return true;
        } catch (Exception e) {
            Log.e(Log.TAG, "保存录像执行策略失败 " + RECORD_POLICY_FILE + " => " + e.getMessage());
            return false;
        }
    }

    private List<VideoTimeItem> loadRecordingPolicyFile() {
        try {
            String s = stringFromFile(RECORD_POLICY_FILE);
            if (s != null && !s.trim().isEmpty()) {
                List<VideoTimeItem> list = JSON.parseArray(s, VideoTimeItem.class);
                if (list != null) return list;
            }
        } catch (Exception e) {
            Log.e(Log.TAG, "读取录像执行策略失败：" + e.getMessage());
        }
        return copyVideoTimeTable(settings == null ? null : settings.videoTimeTable);
    }

    private void ensureRecordingPolicyFile() {
        File file = new File(RECORD_POLICY_FILE);
        if (!file.exists()) {
            saveRecordingPolicyFile(settings == null ? null : settings.videoTimeTable);
        }
    }

    private List<VideoTimeItem> getDeviceRecordingPolicyForCurrentMode() {
        if (currentMode == MODE_WAKEUP || currentMode == MODE_SLEEP) {
            return new ArrayList<>();
        }
        return adjustOverlappingItems(loadRecordingPolicyFile());
    }

    private void refreshRecordTableFromPolicyFile() {
        RECORD_TABLE = adjustOverlappingItems(loadRecordingPolicyFile());
        recordPreset = extractRecordPreset(RECORD_TABLE);
    }

    private void cancelRecordTaskAlarms() {
        if (alarmManager == null) return;
        for (int i = 0; i < MAX_RECORD_ALARM_CANCEL_COUNT; i++) {
            PendingIntent pi = PendingIntent.getBroadcast(this, i, new Intent(ACTION_RECORD), PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.cancel(pi);
            pi.cancel();
        }
        recordIntent = null;
    }

    private void cancelVideoTaskAlarms() {
        if (alarmManager == null) return;
        for (int i = 0; i < MAX_RECORD_ALARM_CANCEL_COUNT; i++) {
            PendingIntent pi = PendingIntent.getBroadcast(this, i, new Intent(ACTION_VIDEO), PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.cancel(pi);
            pi.cancel();
        }
        videoIntent = null;
    }

    private void applyRecordingPolicyToDvr(List<VideoTimeItem> list, String reason) {
        List<VideoTimeItem> policy = list == null ? new ArrayList<>() : list;
        for (Device dev : channels.values()) {
            if (dev == null || !dev.isDVR() || dev.isUSB()) continue;
            boolean ret = dev.setRecordTimes(policy);
            Log.i(Log.TAG, reason + "，下发录像策略" + (ret ? "成功" : "失败") + "，数量：" + policy.size());
        }
    }

    private void postApplyRecordingPolicyToDvr(List<VideoTimeItem> list, String reason, Runnable afterApply) {
        Runnable task = () -> {
            // setRecordTimes 内部会通过 HTTP 访问设备，不能在主线程执行，否则会触发 NetworkOnMainThreadException。
            applyRecordingPolicyToDvr(list, reason);
            if (afterApply != null) {
                afterApply.run();
            }
        };

        if (utilsHandler == null || Looper.myLooper() == utilsHandler.getLooper()) {
            task.run();
        } else {
            utilsHandler.post(task);
        }
    }

    private void disableRecordingForLowPowerMode(String reason) {
        disableRecordingForLowPowerMode(reason, null);
    }

    private void disableRecordingForLowPowerMode(String reason, Runnable afterDisabled) {
        cancelRecordTaskAlarms();
        cancelVideoTaskAlarms();
        // 低功耗模式不执行设备录像计划，先下发空策略；afterDisabled 用来保证下电动作排在下发完成之后。
        postApplyRecordingPolicyToDvr(new ArrayList<>(), reason, afterDisabled);
    }

    private void restoreRecordingForFullMode(String reason) {
        refreshRecordTableFromPolicyFile();
        initRecordTask();
        List<VideoTimeItem> policy = adjustOverlappingItems(loadRecordingPolicyFile());
        postApplyRecordingPolicyToDvr(policy, reason, () -> setRecordingPolicy(policy));
    }


    public void alarmInitTask(String start, long msecPeriod, PendingIntent intent, String source) {
        Date date = dateFromString(start);
        long begin = date.getTime();
        long now = new Date().getTime();
        if (now > begin)
        {
            long diff = now - begin;
            date = addTime(date, msecPeriod * roundUp(1.0 * diff / msecPeriod));
        }
        if (date.getTime() == now)
            date = addTime(date, msecPeriod);

        {
            Log.i(Log.TAG, "\n==============================闹钟：" + start + " 间隔：" + String.valueOf(msecPeriod / 1000) + "秒，来源：" + source + " --> " + dateFormat.format(date));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, date.getTime(), intent);
            } else {
                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, date.getTime(), intent);
            }
        }
    }

    /*
      从当前时间开始，在指定时长后唤醒，一次性闹钟，若要重复执行，请在闹钟回调后重新alarmInitTask
     */
    public void alarmInitTask(PendingIntent intent, String source, long delayMillisecond) {
        Date date = addTime(new Date(), delayMillisecond);
        {
            Log.i(Log.TAG, "\n==============================即时闹钟：" + source + " --> " + dateFormat.format(date));
/*        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), intent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(calendar.getTimeInMillis(), intent);
            alarmManager.setAlarmClock(alarmClockInfo, intent);
        } else */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, date.getTime(), intent);
            } else {
                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, date.getTime(), intent);
            }
        }
    }

    public void initTrafficData() {
        trafficMonth = (HashMap<String, Long>) loadObjectFromFile(TRAFFIC_FILE);
        if (trafficMonth == null) trafficMonth = new HashMap<String, Long>();
        String thisMonth = formatDateTime("yyyy-MM", new Date());
        thisMonthTraffic = trafficMonth.containsKey(thisMonth) ? trafficMonth.get(thisMonth) : 0;
    }


    private void saveTrafficData() {
        String thisMonth = formatDateTime("yyyy-MM", new Date());
        trafficMonth.put(thisMonth, getMonthTraffic());
        saveObjectToFile(trafficMonth, TRAFFIC_FILE);
    }


    public void showMsg(final String msg) {
        runOnUiThread(() -> {
            tvState.setText(msg);
            Log.e(Log.TAG, msg);
        });
    }


    private void loadConfig() {
        Settings settings = loadSettings(this.settings, SETTING_FILE);
        /////
        try {
            RECORD_TABLE = adjustOverlappingItems(settings.videoTimeTable);
            POWER_ON = new String[]{settings.powerOn};      // 配置文件中的时间
            POWER_OFF = new String[]{settings.powerOff};    // 配置文件中的时间
            recordPreset = extractRecordPreset(RECORD_TABLE);
        } catch (Exception e) {
            RECORD_TABLE = new ArrayList<>(); /////
            VideoTimeItem videoTimeItem = new VideoTimeItem();
            videoTimeItem.channel = 1;
            videoTimeItem.stream = 0;
            videoTimeItem.action = 0;
            videoTimeItem.para = 1;
            videoTimeItem.duration = 86399;
            videoTimeItem.hour = 0;
            videoTimeItem.min = 0;
            videoTimeItem.sec = 0;
            RECORD_TABLE.add(videoTimeItem); /////
//            POWER_ON = extractPowerOnStrings();
//            POWER_OFF = extractPowerOffStrings();
            POWER_ON = new String[]{settings.powerOn};
            POWER_OFF = new String[]{settings.powerOff};
            recordPreset = extractRecordPreset(RECORD_TABLE); /////
        }
//        Log.i(Log.TAG, "录像预置位为" + recordPreset);


        if (settings == null) {
            String s = readAsset(this, "settings.json");
            settings = JSONObject.parseObject(s, Settings.class);
            if (settings != null) {
                this.settings = settings;
            }
        } else {
            this.settings = settings;
            // app加载配置信息后，要确保对拍照列表进行升序排列，这样才能按照时间顺序进行拍照
            if (!this.settings.photoTimeTable.isEmpty()) {
                if (is6735) {
                    for (int i = 0; i < settings.photoTimeTable.size(); i++)
                        for (int j = 0; j < settings.photoTimeTable.size(); j++) {
                            PhotoTimeItem item1 = settings.photoTimeTable.get(i);
                            PhotoTimeItem item2 = settings.photoTimeTable.get(j);
                            int v1 = (item1.hour << 8) | item1.min;
                            int v2 = (item2.hour << 8) | item2.min;
                            if (v1 < v2) {
                                settings.photoTimeTable.set(j, item1);
                                settings.photoTimeTable.set(i, item2);
                            }
                        }
                } else {
                    this.settings.photoTimeTable.sort(new Comparator<PhotoTimeItem>() {
                        @Override
                        public int compare(PhotoTimeItem o1, PhotoTimeItem o2) {
                            return (o1.hour * 60 + o1.min) - (o2.hour * 60 + o2.min);
                        }
                    });
                }
            }
        }
        ensureRecordingPolicyFile();
        refreshRecordTableFromPolicyFile();

//        /// 读取usb上电情况
//        try{
//            usbPowerState = readUsbPowerState();
////            Log.e(Log.TAG,"usbPowerState::"+usbPowerState);
//        }catch (Exception e){
//            usbPowerState = false;
//            writeUsbPowerState(false);
//            Log.e(Log.TAG,"Exception::"+e.getMessage());
//        }

        /////
        File file_ir = new File(IR_SETTING_FILE);
        if (!file_ir.exists()) {
            su("mv " + IRAY_SETTING_FILE + " " + IR_SETTING_FILE);
        }
        String json = stringFromFile(IR_SETTING_FILE);  // 原始 JSON 字符串
        /////
        if (json != null && !json.isEmpty()) {
            // 先反序列化
            IRSetting iRSetting = JSONObject.parseObject(json, IRSetting.class);
            applyLegacyFieldsFromJson(json, iRSetting);  // 用 JSON 内容来决定是否迁移
            this.iRSetting = iRSetting;
        }

        /////
        CAMERASetting cAMERASetting = loadSettings(this.cAMERASetting, CAMERA_SETTING_FILE);
        if (cAMERASetting != null) {
            this.cAMERASetting = cAMERASetting;
        }
        /////

        File file = new File(CONFIG_FILE);

        String s;
        if (file.exists())
            s = stringFromFile(CONFIG_FILE);
        else
            s = readAsset(this, "config.json");


        // 根据硬件配置，初始化所有通道设备对象
        try {
            DeviceConfig dc = JSON.parseObject(s, DeviceConfig.class);
            if (dc != null) this.deviceConfig = dc;
        } catch (Exception e) {
            showMsg("配置解释异常：" + e.getMessage());
        }
    }

    /////
    private boolean hasAudioPermission(Context ctx) {
        return ctx.checkCallingOrSelfPermission(
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }


    private void initDevice() {
        channels.clear();
        Device dvr = null;
        GUIDEDev guide = null;
        IRayDev iray = null;
        /////
        camera = null;
        speaker = null;
        boolean useAudio;
        if (deviceConfig.audio && hasAudioPermission(this)) {
            useAudio = true;
        } else {
            useAudio = false;
        }

//        Log.e(Log.TAG, "useAudio================:" + useAudio);

        /////
        for (Channel channel : deviceConfig.channels) {
            if (channel == null) continue;
            Device dev;
            if (channel.type == DEVICE_DVR_AIPU) {
                dvr = dev = new AipuDevice(channel.id, this, channel.server, channel.port, channel.user, channel.password); /////
            } else if (channel.type == DEVICE_DVR_YANDI)
                dvr = dev = new YanDiDevice(channel.id, this, channel.server, channel.user, channel.password, deviceConfig.server);
            else if (channel.type == DEVICE_ONVIF_CAMERA)
                dev = new MyOnvifDevice(channel.id, this, channel.server, channel.port, channel.user, channel.password, useAudio); /////
            else if (channel.type == DEVICE_DVR_HUANYU) {
                dvr = dev = new HuanYuDevice(channel.id, this, channel.server, channel.port, channel.user, channel.password, deviceConfig.toCheck, useAudio); ///////
            } else if (channel.type == DEVICE_USB_GUIDE) {
                dev = guide = new GUIDEDev(channel.id, this, deviceConfig.alarmHigh, deviceConfig.alarmLow, deviceConfig.alarmEnv, deviceConfig.alarmCmp, useAudio); /////
                dev.setSensorConfig(iRSetting.sensorConfig);
                dev.setIrSetting(iRSetting);
                for (Integer channelNum : iRSetting.irChannelRegions.keySet()) {
                    IRSetting.PresetRegions presetRegions = iRSetting.irChannelRegions.get(channelNum);
                    for (Integer preset : presetRegions.irPresetRegions.keySet()) {
                        dev.setTempRegion(channelNum, preset, presetRegions.irPresetRegions.get(preset), false);
                    }
                }
            } else if (channel.type == DEVICE_USB_IRAY) {
                dev = iray = new IRayDev(channel.id, this, deviceConfig.alarmHigh, deviceConfig.alarmLow, deviceConfig.alarmEnv, deviceConfig.alarmCmp, useAudio); /////
                dev.setSensorConfig(iRSetting.sensorConfig);
                dev.setIrSetting(iRSetting);
                for (Integer channelNum : iRSetting.irChannelRegions.keySet()) {
                    IRSetting.PresetRegions presetRegions = iRSetting.irChannelRegions.get(channelNum);
                    for (Integer preset : presetRegions.irPresetRegions.keySet()) {
                        dev.setTempRegion(channelNum, preset, presetRegions.irPresetRegions.get(preset), false);
                    }
                }
                /////
            } else if (channel.type == DEVICE_CAMERA) {
                dev = camera = new Camera2Device(channel.id, this, channel.camera, deviceConfig.mainBoard, channel.rotate, useAudio); /////
            }
            /////
            else {
                Log.i(Log.TAG, "无效的设备类型：" + channel.type);
                continue;
            }

            dev.popChanel = channel.popCamera;
            dev.name = channel.name;
//            dev.confidence = deviceConfig.confidence; /////
            dev.setObjectDetect(deviceConfig.objectDetect);
//            dev.confidence_widgets = deviceConfig.confidence_widgets; ///////
//            dev.setObjectWidgetsDetect(deviceConfig.object_detect_widgets); ///////
            dev.controllerCallback = controllerCallback;
            if (settings != null) {
                dev.ptzSpeed = settings.ptzSpeed;
                dev.ptzStep = settings.ptzStep;

                dev.osd = settings.osds.get(String.valueOf(dev.id));
                if (dev.osd == null) dev.osd = new OSD();
                for (int i = 0; i <= 2; i++) {
                    VideoCodec codec = settings.videoCodecs.get(dev.id + ":" + i);
                    if (codec == null) {
                        codec = new VideoCodec();
                    }
                    dev.codec.put(String.valueOf(i), codec);
                }
                dev.photoConfig = settings.photoConfig.get(String.valueOf(dev.id));
                dev.sceneParameters = settings.sceneParameters;
                dev.cruiseSettings = settings.cruiseSettings.get(String.valueOf(dev.id));
            }
            if (dev.photoConfig == null) dev.photoConfig = new PhotoConfig();
            /////
            if (cAMERASetting != null) {
                dev.cameraConfig = cAMERASetting.cameraConfig.get(String.valueOf(dev.id));
            }
            if (dev.cameraConfig == null) dev.cameraConfig = new CAMERASetting.CameraConfig();
            /////
            channels.put(String.valueOf(dev.id), dev);
        }
        if (dvr != null && guide != null) {
            guide.ptzDev = dvr;
        } else if (dvr != null && iray != null) {
            iray.ptzDev = dvr;
        }

        new Thread(() -> {
            ////////
            for (int i = 0; i < 3; i++) {
                boolean errcode = RS485Impl.Instance().gpioCloseUART();
                Log.i(Log.TAG, String.format("UART下电%s", errcode ? "成功" : "失败"));
                if (errcode) {
                    break;
                }
            }
            if (camera != null && currentMode == MODE_FULL) {
                powerOnMipiIfNeeded("初始化全工作模式");
            } else {
                powerOffMipi("初始化唤醒/休眠模式或无MIPI设备");
            }
            if (speaker == null) {
                for (int i = 0; i < 3; i++) {
                    boolean errcode = RS485Impl.Instance().gpioCloseSpeaker();
                    Log.i(Log.TAG, String.format("Speaker下电%s", errcode ? "成功" : "失败"));
                    if (errcode) {
                        break;
                    }
                }
            }
            ////////
        }).start();
    }


    private void initProtocol() {
        if (spgProtocol != null) return;

        spgProtocol = new SPGProtocol(this, this);
        spgProtocol.deviceID = deviceConfig.deviceId;
        spgProtocol.server = deviceConfig.server;
        spgProtocol.port = deviceConfig.port;
        spgProtocol.MAX_UPLOAD_FILE_SIZE = deviceConfig.maxUploadFileSize;
        if (settings != null) {
            spgProtocol.sim = settings.sim;
            spgProtocol.password = settings.password;
            spgProtocol.passcode = settings.passcode;
        }
    }


    private void initCore() {
        initDevice();
        initProtocol();
        initBDS(); ////////
        //initWifiAP();
        ////////
        initOpenCV();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
        ////////

        // 因为是静态变量，所以如果重启后，无需重新创建线程，该线程非常单纯，不会崩溃
        if (socketThread == null) {
            socketThread = new Thread(new udpRunnable(), "UDP命令线程");
            socketThread.start();
        }

        initAlarmTasksAsync("init core");  // 初始化所有的闹钟，当时间到的时候就执行，广播消息接收器中对应的函数
        new Thread(() -> {
            cpuLock();

            try {
                int i = 0;
                //showMsg("连接服务器中...");
                for (; i < 10; i++) {
                    if (SPGProtocol.Logged) {
                        showMsg("连接服务器成功");
                        break;
                    }
                    spgProtocol.doBootContactInfo();
                    SystemClock.sleep(2 * 1000);
                }
                if (i >= 10) {
                    showMsg("连接服务器失败");
                }
                spgProtocol.doSyncTime();

            } finally {
                tryCpuUnlock();
            }
        }).start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            lastHeartTime = System.currentTimeMillis();
        }, 10000);   // 这个地方延迟执行，是防止系统时间没有更新，就设置了lastHeartTime

    }

    /**
     * 初始化视频录像定时器， index 为 settings 中 VideoTimeTable中的序号！
     * 所有录像都用同一个ACTION_VIDEO，每次启动后，在其中再次启动下一次的定时任务
     *
     * @param index
     */
    private void initVideoTask(int index) {
        if (currentMode == MODE_WAKEUP || currentMode == MODE_SLEEP) {
            cancelVideoTaskAlarms();
            Log.i(Log.TAG, "低功耗模式下不初始化定时录像任务");
            return;
        }
        List<VideoTimeItem> videoPolicy = adjustOverlappingItems(loadRecordingPolicyFile());
        // 一定要保证录像策略按时间顺序排序！
        if (videoIntent != null) alarmManager.cancel(videoIntent);
        Time now = new Time();
        now.setToNow();

        for (int i = index; i < videoPolicy.size(); i++) {
            VideoTimeItem item = videoPolicy.get(i);

            // 初始化的时候，如果index对应的条目，已过拍照时间，则要跳过该次
            if (((now.hour << 8) | now.minute) >= (item.hour << 8 | item.min)) continue;

            Intent vi = new Intent(ACTION_VIDEO);
            vi.putExtra("index", i);
            videoIntent = PendingIntent.getBroadcast(this, i, vi, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmInitTask(String.format("%02d:%02d:%02d", item.hour, item.min, item.sec), PERIOD_DAY, videoIntent, "定时录像");
            break;          // 找到第一条后初始化之后就退出，因为后面的会在闹钟触发后继续下一个时刻设定
        }
    }

    private String formatTime(Calendar calendar) {
        // 将Calendar格式化为时间字符串"hh:mm:ss"
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        return String.format("%02d:%02d:%02d", hour, minute, second);
    }

    /////
    private Calendar millisToCalendar(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return c;
    }

    // 定时拍照的预热任务，只在拍照前“提前上电”，不负责真正拍照。
    // 可见光通道：拍照前 RGB_PHOTO_PREHEAT_MINUTES 分钟开启云台。
    // 红外通道：拍照前 IR_PHOTO_PREHEAT_MINUTES 分钟同时开启云台和红外。
    // 如果创建闹钟时已经进入预热窗口，就延迟 1 秒立即触发，避免错过本次拍照。
    private void wakeupTask(String time, long msecPeriod, int i, String source, int channel) {
        /////
        Device dev = channels.get(String.valueOf(channel));
        if (dev != null && dev.isDVR()) {
            int requestCode = photoWakeupRequestCode(channel, i);
            Date targetTime = dateFromString(time);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(targetTime);
            long now = System.currentTimeMillis();
            long target = calendar.getTimeInMillis();
            if (now >= target) return;  // 目标已过期，不再处理
            if (dev.isUSB()) {
                long t10 = target - IR_PHOTO_PREHEAT_MINUTES * PERIOD_MINUTE;
                // 十分钟任务
                Intent viIr = new Intent(POWERON_IR_INTENT);
                viIr.putExtra("index", i);
                powerIrOnIntent = PendingIntent.getBroadcast(this, requestCode, viIr, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                photoWakeupAlarms.put(requestCode, powerIrOnIntent);
                // 红外抓拍提前10分钟同时开启云台和红外
                if (now < t10) {
                    alarmInitTask(formatTime(millisToCalendar(t10)), msecPeriod, powerIrOnIntent, "红外抓拍前10分钟开启云台与红外");
                } else {
                    // 已进入十分钟窗口，立即执行红外预热
                    alarmInitTask(formatTime(millisToCalendar(now + 1000)), 0, powerIrOnIntent, "红外抓拍前10分钟已进入窗口，立即执行");
                }
                return;
            }
            long t3 = target - RGB_PHOTO_PREHEAT_MINUTES * PERIOD_MINUTE;
            // 先准备三分钟前那条的 Intent，并按策略表补充参数
            Intent vi = new Intent(POWERON_RGB_INTENT); /////
            vi.putExtra("index", i);

//            // 将目标时间转换为当天秒数
//            int nowSeconds = calendar.get(Calendar.HOUR_OF_DAY) * 3600
//                    + calendar.get(Calendar.MINUTE) * 60
//                    + calendar.get(Calendar.SECOND);
//            for (VideoTimeItem item : RECORD_TABLE) {
//                // 判断抓拍时间是否在该策略时间段内
//                int startSeconds = item.hour * 3600 + item.min * 60 + item.sec;
//                int endSeconds = startSeconds + item.duration;
//                if (nowSeconds >= startSeconds && nowSeconds <= endSeconds) {
//                    vi.putExtra("channel", item.channel);  // 添加录像通道号
//                    vi.putExtra("action", item.action);  // 添加录像动作类别
//                    vi.putExtra("para", item.para);
//                }
//            }
            powerRgbOnIntent = PendingIntent.getBroadcast(this, requestCode, vi, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); /////
            photoWakeupAlarms.put(requestCode, powerRgbOnIntent);

            if (now < t3) {
                alarmInitTask(formatTime(millisToCalendar(t3)), msecPeriod, powerRgbOnIntent, "云台抓拍前3分钟开启"); /////
            } else {
                // 立即触发，给 1 秒延迟防止与当前线程竞争
                alarmInitTask(formatTime(millisToCalendar(now + 1000)), 0, powerRgbOnIntent, "云台抓拍前3分钟已进入窗口，立即执行"); /////
            }
        }
        /////
    }

    private boolean shouldSchedulePhotoWakeup(PhotoTimeItem item) {
        if (item == null) return false;
        // 全工作模式的工作时间内本来就上电，不需要额外预热闹钟；
        // 唤醒模式默认会下电，所以即使拍照时间落在工作时间段内，也必须提前上电。
        return currentMode == MODE_WAKEUP || !isWorkHour(item.hour, item.min, item.sec);
    }

    public void refreshPhotoSchedule() {
        forceRescheduleAllPhotoTasks();
    }


    private void forceRescheduleAllPhotoTasks() {
        cancelAllPhotoAlarms();
        doInitPhotoTaskAsync(0);
    }


    private void initPhotoTask(int startIndex) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastInitTime < MIN_INIT_INTERVAL) {
            return;
        }
        lastInitTime = currentTime;
        cancelAllPhotoAlarms();
        doInitPhotoTaskAsync(startIndex);
    }

    private void doInitPhotoTaskAsync(int startIndex) {
        Runnable task = () -> doInitPhotoTask(startIndex);
        if (utilsHandler == null) {
            new Thread(task).start();
        } else if (Looper.myLooper() != utilsHandler.getLooper()) {
            utilsHandler.post(task);
        } else {
            task.run();
        }
    }


    private void doInitPhotoTask(int startIndex) {
        Time now = new Time();
        now.setToNow();

        for (int i = startIndex; i < settings.photoTimeTable.size(); i++) {
            PhotoTimeItem item = settings.photoTimeTable.get(i);
            if (item == null) continue;

            // 跳过已过时的任务（基于小时和分钟，不考虑秒）
            if (now.hour > item.hour || (now.hour == item.hour && now.minute >= item.min)) {
                continue;
            }

            setSinglePhotoAlarm(i, item, "定时拍照_" + item.channel + "_" + item.preset);

        }


    }

    private int photoAlarmRequestCode(int channel, int index) {
        return channel * 10000 + index;
    }

    private int photoWakeupRequestCode(int channel, int index) {
        // requestCode = 独立基准 + 通道偏移 + 策略序号，保证同一个拍照计划的预热闹钟可被精确取消。
        return RC_PHOTO_WAKEUP_BASE + channel * RC_PHOTO_STRIDE + index;
    }

    private void cancelPhotoAlarmsForChannel(int channel) {
        for (int i = 0; i < settings.photoTimeTable.size(); i++) {
            PhotoTimeItem item = settings.photoTimeTable.get(i);
            if (item == null || item.channel != channel) continue;
            cancelPhotoAlarmForItem(i, item);
        }
    }

    private void cancelPhotoAlarmForItem(int index, PhotoTimeItem item) {
        int photoRequestCode = photoAlarmRequestCode(item.channel, index);
        int wakeupRequestCode = photoWakeupRequestCode(item.channel, index);

        PendingIntent photoPi = PendingIntent.getBroadcast(this, photoRequestCode, new Intent(ACTION_PHOTO),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (photoPi != null) alarmManager.cancel(photoPi);

        PendingIntent rgbWakeupPi = PendingIntent.getBroadcast(this, wakeupRequestCode, new Intent(POWERON_RGB_INTENT),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (rgbWakeupPi != null) alarmManager.cancel(rgbWakeupPi);

        PendingIntent irWakeupPi = PendingIntent.getBroadcast(this, wakeupRequestCode, new Intent(POWERON_IR_INTENT),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (irWakeupPi != null) alarmManager.cancel(irWakeupPi);
    }

    private void setSinglePhotoAlarm(int index, PhotoTimeItem item, String alarmName) {
        Intent vi = new Intent(ACTION_PHOTO);
        vi.putExtra("index", index);

        // 生成唯一的 requestCode，避免 PendingIntent 覆盖
        int requestCode = photoAlarmRequestCode(item.channel, index);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, vi,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        photoAlarms.put(requestCode, pendingIntent);

        if (shouldSchedulePhotoWakeup(item)) {
            wakeupTask(String.format("%02d:%02d:%02d", item.hour, item.min, item.sec),
                    PERIOD_DAY, index, "定时开启云台", item.channel);
        } else {
            Log.i(Log.TAG, "全工作模式工作时间内拍照，跳过提前唤醒闹钟：channel=" + item.channel + ", index=" + index);
        }

        alarmInitTask(String.format("%02d:%02d:%02d", item.hour, item.min, item.sec),
                PERIOD_DAY, pendingIntent, alarmName);
    }

    /**
     * 取消所有已注册的拍照闹钟
     */
    private void cancelAllPhotoAlarms() {
        if (settings != null && settings.photoTimeTable != null) {
            for (int i = 0; i < settings.photoTimeTable.size(); i++) {
                PhotoTimeItem item = settings.photoTimeTable.get(i);
                if (item != null) {
                    cancelPhotoAlarmForItem(i, item);
                }
            }
        }

        for (PendingIntent pi : photoAlarms.values()) {
            if (pi != null) {
                alarmManager.cancel(pi);
            }
        }
        photoAlarms.clear();

        for (PendingIntent pi : photoWakeupAlarms.values()) {
            if (pi != null) {
                alarmManager.cancel(pi);
            }
        }
        photoWakeupAlarms.clear();
    }



    private int checkLineRequestCode(int channel, int index) {
        // requestCode = 独立基准 + 通道偏移 + 策略序号，保证通道一、通道二巡检闹钟互不覆盖。
        return RC_CHECK_LINE_BASE + channel * RC_CHECK_LINE_STRIDE + index;
    }

    private void cancelCheckLineAlarmsForChannel(int channel) {
        int start = RC_CHECK_LINE_BASE + channel * RC_CHECK_LINE_STRIDE;
        int end = start + RC_CHECK_LINE_STRIDE;
        Iterator<Map.Entry<Integer, PendingIntent>> iterator = checkLineAlarms.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, PendingIntent> entry = iterator.next();
            int requestCode = entry.getKey();
            if (requestCode >= start && requestCode < end) {
                alarmManager.cancel(entry.getValue());
                iterator.remove();
            }
        }
    }

    private void cancelAllCheckLineAlarms() {
        for (PendingIntent pi : checkLineAlarms.values()) {
            if (pi != null) {
                alarmManager.cancel(pi);
            }
        }
        checkLineAlarms.clear();
        cancelLegacyCheckLineAlarms();
    }

    private void cancelLegacyCheckLineAlarms() {
        // 旧版本巡检闹钟直接使用 index 作为 requestCode，升级后顺手清理，避免新旧闹钟重复触发。
        for (int i = 0; i < RC_CHECK_LINE_STRIDE; i++) {
            PendingIntent legacyPi = PendingIntent.getBroadcast(this, i, new Intent(ACTION_CHECK_LINE),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (legacyPi != null) {
                alarmManager.cancel(legacyPi);
            }
        }
    }

    /**
     * 初始化巡检策略定时器，index 为索引位置。
     * 不指定通道时，同时刷新通道一和通道二，兼容初始化时的统一调用。
     */
    private void initCheckLineTask(int index) {
        cancelAllCheckLineAlarms();
        initCheckLineTask(1, index);
        initCheckLineTask(2, index);
    }

    /**
     * 初始化指定通道的下一条巡检闹钟。
     */
    private void initCheckLineTask(int channel, int index) {
        if (channel == 1) {
            cancelLegacyCheckLineAlarms();
        }
        cancelCheckLineAlarmsForChannel(channel);

        Time now = new Time();
        now.setToNow();

        List<CheckScheduleItem> items = settings.checkSchedule.get(String.valueOf(channel));
        if (items == null) return;

        for (int i = index; i < items.size(); i++) {
            CheckScheduleItem item = items.get(i);
            if (!item.enable) continue;
            // 初始化的时候，如果 index 对应的条目已过闹，则跳过该次，等待后续时刻。
            if (((now.hour << 8) | now.minute) >= (item.hour << 8 | item.minute)) continue;

            Intent vi = new Intent(ACTION_CHECK_LINE);
            vi.putExtra("channel", channel);
            vi.putExtra("index", i);
            int requestCode = checkLineRequestCode(channel, i);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, vi,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            checkLineAlarms.put(requestCode, pendingIntent);
            alarmInitTask(String.format("%02d:%02d:%02d", item.hour, item.minute, item.second),
                    PERIOD_DAY, pendingIntent, "定时巡检_" + channel + "_" + (i + 1));
            break;          // 找到第一条后退出，后面的任务在闹钟触发后继续设置。
        }
    }

    /////
    private void initAlarmPowerOn() {
        for (int i = 0; i < POWER_ON.length; i++) {
            alarmInitTask(POWER_ON[i], PERIOD_DAY, powerOnIntent, "打开云台和红外");
        }
    }


    private void initAlarmPowerOff() {
        for (int i = 0; i < POWER_OFF.length; i++) {
            alarmInitTask(POWER_OFF[i], PERIOD_DAY, powerOffIntent, "关闭云台和红外");
        }
    }


    private void initRecordTask() { /////
        cancelRecordTaskAlarms();
        if (currentMode == MODE_WAKEUP || currentMode == MODE_SLEEP) {
            Log.i(Log.TAG, "低功耗模式下不初始化可见光机芯定时录像任务");
            return;
        }
        if (RECORD_TABLE == null) {
            refreshRecordTableFromPolicyFile();
        }
        for (int i = 0; i < RECORD_TABLE.size(); i++) { /////
            VideoTimeItem item = RECORD_TABLE.get(i); /////
            Intent vi = new Intent(ACTION_RECORD); /////
            vi.putExtra("channel", item.channel);  // 添加录像通道号
            vi.putExtra("action", item.action);  // 添加录像动作类别
            vi.putExtra("para", item.para);  // 添加录像动作参数

            recordIntent = PendingIntent.getBroadcast(this, i, vi, PendingIntent.FLAG_UPDATE_CURRENT);  // requestCode使用i，这样才能使每个PendingIntent独一无二 /////

            // 从 VideoTimeItem 对象中获取时分秒并格式化为字符串
            String timeStr = String.format("%02d:%02d:%02d", item.hour, item.min, item.sec);

            if (item.action == 0) {
                alarmInitTask(timeStr, PERIOD_DAY, recordIntent, "可见光机芯开始录像，调用预置位" + item.para); /////
            } else if (item.action == 1) {
                alarmInitTask(timeStr, PERIOD_DAY, recordIntent, "可见光机芯开始录像，调用巡航组号" + item.para); /////
            } else {
                alarmInitTask(timeStr, PERIOD_DAY, recordIntent, "可见光机芯开始录像，调用巡检组号" + item.para); /////
            }
        }
    }


    private void initHeartBeat() {
        heartBeatIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_HEART), PendingIntent.FLAG_UPDATE_CURRENT);
        alarmInitTask(heartBeatIntent, "心跳", settings.onlineCfg.heart * PERIOD_MINUTE);
    }


    private void initSample() {
        sampleIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_SAMPLE), PendingIntent.FLAG_UPDATE_CURRENT);
        alarmInitTask(sampleIntent, "采样", settings.onlineCfg.sample * PERIOD_MINUTE);
//        alarmInitTask(sampleIntent, "采样", 1000*10);
    }

    private void utilizationRate() {
        utilizationRateIntent = PendingIntent.getBroadcast(this, 0, new Intent(USAGE_RATE), PendingIntent.FLAG_UPDATE_CURRENT);
//        alarmInitTask(utilizationRateIntent, "存储空间使用率", STORAGE_USAGE_INTERVAL_MS);    // 设置为半小时检测一次
//        alarmInitTask(utilizationRateIntent, "存储空间使用率", 1 * PERIOD_MINUTE);    // 设置为半小时检测一次
        alarmInitTask("12:00:00", PERIOD_DAY, utilizationRateIntent, "存储空间使用率");    // 定时任务
    }

    private void initProtect() {
        protectIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_PROTECT), PendingIntent.FLAG_UPDATE_CURRENT);
        // 在汇能精电控制器硬重启时间前30秒进行软关机
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            // 解析字符串为时间
            Date date = sdf.parse(loadCloseTime);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.add(Calendar.SECOND, -30);
            // 生成减法后的时间字符串作为闹钟设定时间
            String protectTime = sdf.format(calendar.getTime());
            alarmInitTask(protectTime, PERIOD_DAY, protectIntent, "定时软关机（硬重启保护）");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 抽检模式，下发的重启时间，到时间修改为重启APK
    private void initReboot(final long millis) {
        String rebootTime1 = null;
        String rebootTime2 = null;
        rebootIntent1 = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_REBOOT), PendingIntent.FLAG_UPDATE_CURRENT);
        rebootIntent2 = PendingIntent.getBroadcast(this, 1, new Intent(ACTION_REBOOT), PendingIntent.FLAG_UPDATE_CURRENT);

        if (millis < 0) {
            if ((deviceConfig.chargeControl == 2 || deviceConfig.chargeControl == 3 || deviceConfig.chargeControl == 6 || deviceConfig.chargeControl == 7) && (!deviceConfig.toCheck)) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                    // 解析字符串为时间
                    Date date = sdf.parse(loadCloseTime);
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(date);
                    calendar.add(Calendar.SECOND, 45);
                    int hour = calendar.get(Calendar.HOUR_OF_DAY);
                    int min = calendar.get(Calendar.MINUTE);
                    rebootTime1 = sdf.format(calendar.getTime());

                    if ((settings.onlineCfg.hour != hour) || (settings.onlineCfg.min != min)) {
                        rebootTime2 = String.format("%02d:%02d:00", settings.onlineCfg.hour, settings.onlineCfg.min);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                // 解析字符串为时间
                Date date = null;
                try {
                    date = sdf.parse(loadCloseTime);
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(date);
                    rebootTime1 = sdf.format(calendar.getTime());
                    int hour = calendar.get(Calendar.HOUR_OF_DAY);
                    int min = calendar.get(Calendar.MINUTE);

                    if ((settings.onlineCfg.hour != hour) || (settings.onlineCfg.min != min)) {
                        rebootTime2 = String.format("%02d:%02d:00", settings.onlineCfg.hour, settings.onlineCfg.min);
                    }

                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        } else {
            rebootTime1 = new SimpleDateFormat("HH:mm:ss").format(new Date(millis));
        }

        if (rebootTime1 != null) {
            alarmInitTask(rebootTime1, PERIOD_DAY, rebootIntent1, "定时重启（硬重启保护）");
        }
        if (rebootTime2 != null) {
            alarmInitTask(rebootTime2, PERIOD_DAY, rebootIntent2, "定时重启");
        }
    }

    public void alarmInitTask(String start, PendingIntent intent, String source) {
        Date date = dateFromString(start);
        {
            Log.i(Log.TAG, "闹钟：" + start + "，来源：" + source + " --> " + date.toString());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, date.getTime(), intent);
            } else {
                alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, date.getTime(), intent);
            }
        }
    }

    private void initWifiAlarm() {
        Date now = new Date();
        if (now.before(dateFromString(WIFIAP_FROM))) {
            wifiIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_START_WIFIAP), PendingIntent.FLAG_UPDATE_CURRENT);
            alarmInitTask(WIFIAP_FROM, wifiIntent, "定时开启Wi-Fi");
        } else if (now.before(dateFromString(WIFIAP_UNTIL))) {
            wifiIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_START_WIFIAP), PendingIntent.FLAG_UPDATE_CURRENT);
            alarmInitTask(String.format("%02d:00:00", now.getHours() + 1), wifiIntent, "定时开启Wi-Fi");
        }
    }

    private void initScheduleDailyPhoto() {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_INIT_PHOTO), PendingIntent.FLAG_UPDATE_CURRENT);
        alarmInitTask("00:00:00", PERIOD_DAY, pendingIntent, "每日刷新拍照任务");
    }

    /////
    private void initPhotoCheck() {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_PHOTO_CHECK), PendingIntent.FLAG_UPDATE_CURRENT);
        alarmInitTask("12:00:00", PERIOD_DAY, pendingIntent, "每日拍照自检测任务（仅针对拍照故障设备）");
    }

//    public void btnCbBatOnlyClick(View v) {
//        deviceConfig.onlyShowBat = ((CheckBox) findViewById(R.id.cbOnlyBat)).isChecked();
//    }

    private void initAlarmTasks() {
        cancelSoftShutdownTimer();

        Log.i(Log.TAG, "初始化和创建系统所有闹钟事件");
//        initReboot(-1);
//        initProtect();

        initRecordTask(); /////

//        initVideoTask(0);  // 关闭本地定时录像
        initPhotoTask(0);
        // 添加每天00:00刷新拍照任务，不然如果硬重启时间在00:00之前，拍照任务会没掉，需要等待下一次软重启！
        initScheduleDailyPhoto();
//        initPhotoCheck(); /////
        initHeartBeat();
        initSample();
        utilizationRate();
        //initWifiAlarm();
    }

    private void initAlarmTasksAsync(String reason) {
        Runnable task = () -> {
            try {
                Log.i(Log.TAG, "async init alarm tasks: " + reason);
                initAlarmTasks();
            } catch (Exception e) {
                Log.e(Log.TAG, "async init alarm tasks failed: " + e);
            }
        };

        if (utilsHandler != null) {
            utilsHandler.post(task);
        } else {
            new Thread(task).start();
        }
    }

    public void doOnlineTimeout(String reason) {
        Log.e(Log.TAG, reason);
        for (Device dev : channels.values()) {
            if (dev.isLiving()) stopLiveVideo(dev.id, 0, dev.ssrcLive);

            ////////////////
            if (dev.id == 1) {
                // 关闭云台
                sleepDevice(dev.id, "可见光停止直播");
            } else if (dev.id == 2) {
                // 关闭红外，如果不在工作时间，也关闭云台
                sleepDevice(dev.id, "红外停止直播");
            }

            if (dev.isPlaybacking()) stopPlayCallBack(dev.id, dev.ssrcPlayback);
        }
    }
    /////

    private void openShare(String reason) {

        if (!isRJ45Powered) {
            for (int i = 0; i < 3; i++) {
                boolean errcode = RS485Impl.Instance().gpioOpenRJ45();
                Log.i(Log.TAG, String.format("RJ45上电%s", errcode ? "成功" : "失败"));
                if (errcode) {
                    isRJ45Powered = true;
                    break;
                }
                SystemClock.sleep(500);
            }
        } else {
            Log.i(Log.TAG, "RJ45已上电，跳过上电操作");
        }

        if (!isEth0Up) {
            int exitCode = 0;
            for (int i = 0; i < 3; i++) {
                if (is6735)
                    exitCode = su("busybox ifconfig eth0 0.0.0.0 up");
                else
                    exitCode = su("ifconfig eth0 up");
                if (exitCode == 0) {
                    isEth0Up = true;
                    break;
                }
            }
            SystemClock.sleep(500);

            Log.i(Log.TAG, String.format("打开Share%s: %s", exitCode == 0 ? "成功" : "失败", reason));
        } else {
            Log.i(Log.TAG, "eth0已开启，跳过开启操作");
        }

        Log.i(Log.TAG, String.format("当前网络状态 - RJ45: %s, eth0: %s",
                isRJ45Powered ? "已上电" : "未上电",
                isEth0Up ? "已开启" : "未开启"));
    }
    //////

    private void closeShare(String reason) {
        for (String channel : channels.keySet()) {
            Device dev = channels.get(channel);
            if (dev.isDVR() && dev.isBusy()) {
                Log.i(Log.TAG, "不关闭Share，通道" + channel + "云台状态：" + dev.Status());
                return;
            }
        }

        if (isEth0Up) {
            if (is6735)
                su("busybox ifconfig eth0 down");
            else
                su("ifconfig eth0 down");

            isEth0Up = false; // 更新状态位
            Log.i(Log.TAG, String.format("关闭Share: %s", reason));
            SystemClock.sleep(500);
        } else {
            Log.i(Log.TAG, "eth0未开启，跳过关闭操作");
        }


        if (deviceConfig.toCheck && isRJ45Powered) {       // RJ45在抽检模式下，或者休眠情况下才会进行断电

            for (int i = 0; i < 3; i++) {
                boolean errcode = RS485Impl.Instance().gpioCloseRJ45();
                Log.i(Log.TAG, String.format("RJ45下电%s", errcode ? "成功" : "失败"));
                if (errcode) {
                    isRJ45Powered = false; // 更新状态位
                    break;
                }
                SystemClock.sleep(500);
            }

            Log.i(Log.TAG, "RJ45下电");
        } else if (deviceConfig.toCheck && !isRJ45Powered) {
            Log.i(Log.TAG, "RJ45未上电，跳过下电操作");
        }
    }

    private boolean isImageFile(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".gif") ||
                name.endsWith(".bmp") || name.endsWith(".webp");
    }
    /////

    /**
     * 循环删除文件，开独立线程，避免文件数量太多吊死！
     * 默认情况下，listFiles是按文件创建时间排序，即最老的会被最先删除
     * /data/media 使用率: 80%   分界线
     */
    private void cleanFiles() {
        utilsHandler.post(() -> {
//            cpuLock();
            TaskManager.add(ACTION_CLEAN_FILES);

            try {
                // 获取当前 /data/media 使用率
                int usage = StorageUtil.getDataMediaUsagePercent();
                if (usage != -1) {
                    Log.e("StorageUtil", "/data/media 使用率: " + usage + "%");
                } else {
                    Log.e("StorageUtil", "无法获取使用率");
                    return;
                }

                final int THRESHOLD = 80;

                // 如果使用率超过阈值，执行空间清理
                if (usage > THRESHOLD) {
                    Log.e("StorageUtil", "使用率超过阈值 " + THRESHOLD + "%，开始清理至 " + THRESHOLD + "% 以下");

                    // 获取所有图片文件并按最后修改时间排序（最旧的在前）
                    List<File> allFiles = new ArrayList<>();
                    listFiles(FILE_PATH, allFiles);

                    List<File> imageFiles = new ArrayList<>();
                    for (File file : allFiles) {
                        if (isImageFile(file)) {
                            imageFiles.add(file);
                        }
                    }

                    imageFiles.sort(Comparator.comparingLong(File::lastModified));

                    // 依次删除文件，直到使用率降至阈值以下或文件删完
                    for (File file : imageFiles) {
                        if (file.delete()) {
                            Log.e("StorageUtil", "删除文件: " + file.getAbsolutePath());
                        } else {
                            Log.e("StorageUtil", "删除失败: " + file.getAbsolutePath());
                        }

                        // 每删除一个文件后重新检查使用率
                        usage = StorageUtil.getDataMediaUsagePercent();
                        if (usage == -1) {
                            Log.e("StorageUtil", "无法获取使用率，停止清理");
                            break;
                        }
                        if (usage <= THRESHOLD) {
                            Log.e("StorageUtil", "使用率已降至 " + usage + "%，低于阈值 " + THRESHOLD + "%，停止清理");
                            break;
                        }
                    }
                } else {
                    // 使用率未超阈值，删除超过30天的文件
                    Log.e("StorageUtil", "使用率正常，执行基于时间的清理（>30天）");
                    List<File> files = new ArrayList<>();
                    listFiles(FILE_PATH, files);

                    for (File file : files) {
                        if ((System.currentTimeMillis() - file.lastModified()) / 1000 > 86400 * 30) {
                            if (file.delete()) {
                                Log.e("StorageUtil", "删除过期文件: " + file.getAbsolutePath());
                            } else {
                                Log.e("StorageUtil", "删除失败: " + file.getAbsolutePath());
                            }
                        }
                    }
                }
            } finally {
                finishTask(ACTION_CLEAN_FILES);
            }
        });
    }

    private void updateUI() {
        updateChannelUI();
        tvServer.setText(deviceConfig.server);
        tvPort.setText(String.valueOf(deviceConfig.port));
        spnAI.setSelection(deviceConfig.objectDetect);
//        spnWidgetsAI.setSelection(deviceConfig.object_detect_widgets); ///////
        tvID.setText(deviceConfig.deviceId);
        etTraffic.setText(String.valueOf(deviceConfig.traffic));
        etConfidence.setText(String.valueOf(deviceConfig.confidence)); /////
//        etWidgetsConfidence.setText(String.valueOf(deviceConfig.confidence_widgets)); ///////
//        cbBatOnly.setChecked(deviceConfig.onlyShowBat);
        cbToCheck.setChecked(deviceConfig.toCheck); ///////
        switchAudio.setChecked(deviceConfig.audio); ///////
        spnChargeController.setSelection(deviceConfig.chargeControl);
        spnAeroDevice.setSelection(deviceConfig.aeroDevice);
        spnMainBoarder.setSelection(deviceConfig.mainBoard);
        cbPhotoCheck.setChecked(deviceConfig.photoCheck); /////
        cbAIAccTest.setChecked(deviceConfig.aiAccTest); /////
//        spnZoomRatio.setSelection(deviceConfig.zoomRatio); /////

        irTempAdj.setText(String.valueOf(iRSetting.sensorConfig.tempCompensate));
        irObjEmi.setText(String.valueOf(iRSetting.sensorConfig.emissivity));
        irObjDistance.setText(String.valueOf(iRSetting.sensorConfig.distance));
        irTempReflect.setText(String.valueOf(iRSetting.sensorConfig.reflectTemp));
        irTempEnv.setText(String.valueOf(iRSetting.sensorConfig.envirTemp));
        irHumiEnv.setText(String.valueOf(iRSetting.sensorConfig.envirHumi));
        irShutterInt.setText(String.valueOf(iRSetting.sensorConfig.shutterInterval)); /////
        irResolution.setSelection(iRSetting.resolution);
        irHotTrace.setChecked(iRSetting.sensorConfig.hotTracker == 1);
        irPlatteDisp.setChecked(iRSetting.sensorConfig.onPalette == 1);
        irGlobalAlarm.setChecked(iRSetting.globalAlarm == 1);
        irThresholdAlarm.setChecked(iRSetting.thresholdAlarm == 1);
        irEnvAlarm.setChecked(iRSetting.envAlarm == 1);
        irComAlarm.setChecked(iRSetting.comAlarm == 1);
        irImageStitch.setChecked(iRSetting.imageStitch == 1);
        irImageFusion.setChecked(iRSetting.imageFusion == 1); /////
        irAngle.setText(String.valueOf(iRSetting.angle)); /////
        irHorDisplacement.setText(String.valueOf(iRSetting.horDisplacement)); /////
        irVerDisplacement.setText(String.valueOf(iRSetting.verDisplacement)); /////

        /////
        spnBitRateType.setSelection(settings.videoCodecs.get("1:0").vbr);  // 用通道一主码流的
        spnStreamType.setSelection(settings.videoCodecs.get("1:0").streamType);  // 用通道一主码流的
        etFrame.setText(String.valueOf(settings.videoCodecs.get("1:0").frame));  // 用通道一主码流的
        spnResolution.setSelection(settings.photoConfig.get("1").size);  // 用通道一的
        spnDenoiseMode.setSelection(cAMERASetting.cameraConfig.get("1").denoiseMode);  // 用通道一的
        spnGainControl.setSelection(cAMERASetting.cameraConfig.get("1").gainControl);  // 用通道一的
        spnFocusMode.setSelection(cAMERASetting.cameraConfig.get("1").focusMode);  // 用通道一的
        spnDayAndNightMode.setSelection(cAMERASetting.cameraConfig.get("1").dayAndNightMode);  // 用通道一的 ///
        cbBackLightCom.setChecked(cAMERASetting.cameraConfig.get("1").backLightCom == 1);  // 用通道一的
        cbStrongLightSup.setChecked(cAMERASetting.cameraConfig.get("1").strongLightSup == 1);  // 用通道一的
        cbElectronicFog.setChecked(cAMERASetting.cameraConfig.get("1").electronicFog == 1);  // 用通道一的
        cbLowLight.setChecked(cAMERASetting.cameraConfig.get("1").lowLight == 1);  // 用通道一的
        cbVideoLoss.setChecked(cAMERASetting.cameraConfig.get("1").videoLoss == 1);  // 用通道一的
        cbVideoBlock.setChecked(cAMERASetting.cameraConfig.get("1").videoBlock == 1);  // 用通道一的
        cbVideoOutFocus.setChecked(cAMERASetting.cameraConfig.get("1").videoOutFocus == 1);  // 用通道一的
        cbVideoScreenDist.setChecked(cAMERASetting.cameraConfig.get("1").videoScreenDist == 1);  // 用通道一的
        /////
    }

    private void initView() {
        surfaceView = findViewById(R.id.svVideo);
        surfaceDraw = findViewById(R.id.svDraw);
        etConfidence = findViewById(R.id.etConfidence); /////
//        etWidgetsConfidence = findViewById(R.id.etWidgetsConfidence); ///////
        spnChannels = findViewById(R.id.cbChannels);
        spnPopCamera = findViewById(R.id.spnPopCamera);
        spnChannels.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int index, long id) {
                if (index > deviceConfig.channels.length - 1 || index < 0) return;

                Channel cfg = deviceConfig.channels[index];
                if (cfg != null) {
                    /////
                    boolean isCamera = cfg.type == DEVICE_CAMERA;
                    boolean isRJ45 = (cfg.type == DEVICE_DVR_AIPU) || (cfg.type == DEVICE_DVR_HUANYU);
                    boolean isUSB = (cfg.type == DEVICE_USB_GUIDE) || (cfg.type == DEVICE_USB_IRAY);
                    cbType.setSelection(cfg.type);
                    tvState.setText(cfg.name);
                    tvCamID.setEnabled(isCamera);
                    tbRotate.setEnabled(true);
                    tvDVRPwd.setEnabled(isRJ45);
                    tvDVRUser.setEnabled(isRJ45);
                    tvDVRIP.setEnabled(isRJ45);
                    tvDVRPort.setEnabled(isRJ45);
                    irTempAdj.setEnabled(isUSB);
                    irObjEmi.setEnabled(isUSB);
                    irObjDistance.setEnabled(isUSB);
                    irTempReflect.setEnabled(isUSB);
                    irTempEnv.setEnabled(isUSB);
                    irHumiEnv.setEnabled(isUSB);
                    irVideoMode.setEnabled(isUSB);
                    irShutterInt.setEnabled(isUSB);
                    irHotTrace.setEnabled(isUSB);
                    irPlatteDisp.setEnabled(isUSB);
                    irGlobalAlarm.setEnabled(isUSB);
                    irThresholdAlarm.setEnabled(isUSB);
                    irEnvAlarm.setEnabled(isUSB);
                    irComAlarm.setEnabled(isUSB);
                    irImageStitch.setEnabled(isUSB);
                    irImageFusion.setEnabled(isUSB);
                    irAngle.setEnabled(isUSB);
                    irHorDisplacement.setEnabled(isUSB);
                    irVerDisplacement.setEnabled(isUSB);
                    irOperator.setEnabled(isUSB);
                    irObjType.setEnabled(isUSB);
                    irObjFlag.setEnabled(isUSB);
                    irFocalLen.setEnabled(isUSB);
                    irResolution.setEnabled(isUSB);
                    irRegionDistance.setEnabled(isUSB);
                    irRegionEmi.setEnabled(isUSB);
                    /////
                    switch (cfg.type) {
                        case DEVICE_CAMERA:
                            tbRotate.setEnabled(true);
                            tbRotate.setHint(R.string.cameraRotate);
                            break;
                        // 红外这里表示设置预置位
                        case DEVICE_USB_GUIDE:  // 高德红外
                        case DEVICE_USB_IRAY:  // 英睿红外
                            tbRotate.setEnabled(true);
                            tbRotate.setHint(R.string.presetSet);
                            break;
                        default:
                            tbRotate.setEnabled(false);
                            break;
                    }
                    tbRotate.setText(String.valueOf(cfg.rotate));
                    if (isRJ45) { /////
                        tvDVRIP.setText(cfg.server);
                        tvDVRPort.setText(String.valueOf(cfg.port));
                        tvDVRUser.setText(cfg.user);
                        tvDVRPwd.setText(cfg.password);
                    }
                    if (isCamera) {
                        tvCamID.setText(String.valueOf(deviceConfig.channels[index].camera));
                    }
                    spnPopCamera.setSelection(cfg.popCamera);
                    /////
                    spnBitRateType.setSelection(settings.videoCodecs.get(cfg.id + ":" + spnStreamType.getSelectedItemPosition()).vbr);
                    spnStreamType.setSelection(settings.videoCodecs.get(cfg.id + ":" + spnStreamType.getSelectedItemPosition()).streamType);
                    etFrame.setText(String.valueOf(settings.videoCodecs.get(cfg.id + ":" + spnStreamType.getSelectedItemPosition()).frame));
                    spnResolution.setSelection(settings.photoConfig.get(String.valueOf(cfg.id)).size);
                    spnDenoiseMode.setSelection(cAMERASetting.cameraConfig.get(String.valueOf(cfg.id)).denoiseMode);
                    spnGainControl.setSelection(cAMERASetting.cameraConfig.get(String.valueOf(cfg.id)).gainControl);
                    spnFocusMode.setSelection(cAMERASetting.cameraConfig.get(String.valueOf(cfg.id)).focusMode);
                    spnDayAndNightMode.setSelection(cAMERASetting.cameraConfig.get(String.valueOf(cfg.id)).dayAndNightMode); ///
                    cbBackLightCom.setChecked(cAMERASetting.cameraConfig.get(String.valueOf(cfg.id)).backLightCom == 1);
                    cbStrongLightSup.setChecked(cAMERASetting.cameraConfig.get(String.valueOf(cfg.id)).strongLightSup == 1);
                    cbElectronicFog.setChecked(cAMERASetting.cameraConfig.get(String.valueOf(cfg.id)).electronicFog == 1);
                    cbLowLight.setChecked(cAMERASetting.cameraConfig.get(String.valueOf(cfg.id)).lowLight == 1);
                    cbVideoLoss.setChecked(cAMERASetting.cameraConfig.get(String.valueOf(cfg.id)).videoLoss == 1);
                    cbVideoBlock.setChecked(cAMERASetting.cameraConfig.get(String.valueOf(cfg.id)).videoBlock == 1);
                    cbVideoOutFocus.setChecked(cAMERASetting.cameraConfig.get(String.valueOf(cfg.id)).videoOutFocus == 1);
                    cbVideoScreenDist.setChecked(cAMERASetting.cameraConfig.get(String.valueOf(cfg.id)).videoScreenDist == 1);
                    /////
                } else
                    tvState.setText("找不到信息");
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        btnUp = findViewById(R.id.btnUp);
        btnBottom = findViewById(R.id.btnDown);
        btnLeft = findViewById(R.id.btnLeft);
        btnRight = findViewById(R.id.btnRight);
        btnZoomin = findViewById(R.id.btnZoomin);
        btnZoomout = findViewById(R.id.btnZoomout);
        /////
        btnAddPreset = findViewById(R.id.btnAddPreset);
        btnRemovePreset = findViewById(R.id.btnRemovePreset);
        btnEditPreset = findViewById(R.id.btnEditPreset);
        btnAddCruise = findViewById(R.id.btnAddCruise);
        btnRemoveCruise = findViewById(R.id.btnRemoveCruise);
        btnEditCruise = findViewById(R.id.btnEditCruise);
        /////
        tvState = findViewById(R.id.tvState);
        textVersion = findViewById(R.id.textVersion);
        textVersion.setText(textVersion.getText() + appVersion(this));
        tvID = findViewById(R.id.etID);
        tvServer = findViewById(R.id.etServer);
        tvPort = findViewById(R.id.etPort);
        tvCamID = findViewById(R.id.etCamID);
        cbType = findViewById(R.id.cbType);
        tbRotate = findViewById(R.id.tbRotate);
//        cbBatOnly = findViewById(R.id.cbOnlyBat);
        cbToCheck = findViewById(R.id.cbToCheck); ///////
        switchAudio = findViewById(R.id.switchAudio); ///////
        TDptz = findViewById(R.id.TDptz);
        tvDVRIP = findViewById(R.id.etDVRIP);
        tvDVRPort = findViewById(R.id.etDVRPort);
        tvDVRPwd = findViewById(R.id.etDVRPwd);
        tvDVRUser = findViewById(R.id.etDVRUser);
        spnAI = findViewById(R.id.spnAI);
//        spnWidgetsAI = findViewById(R.id.spnWidgetsAI); ///////
        etTraffic = findViewById(R.id.etTraffic);
        spnAeroDevice = findViewById(R.id.spnAeroDevice);
        spnChargeController = findViewById(R.id.spnChargeController);
        surfaceDraw.setOnTouchListener(svTouchListener);
        spnMainBoarder = findViewById(R.id.spnMainBoarder);
        cbPhotoCheck = findViewById(R.id.cbPhotoCheck); /////
        cbAIAccTest = findViewById(R.id.cbAIAccTest); /////

        /////
        spnBitRateType = findViewById(R.id.spnBitRateType);
        spnStreamType = findViewById(R.id.spnStreamType);
        etFrame = findViewById(R.id.etFrame);
        spnResolution = findViewById(R.id.spnResolution);
        spnDenoiseMode = findViewById(R.id.spnDenoiseMode);
        spnGainControl = findViewById(R.id.spnGainControl);
        spnFocusMode = findViewById(R.id.spnFocusMode);
        spnDayAndNightMode = findViewById(R.id.spnDayAndNightMode); ///
        cbVideoLoss = findViewById(R.id.cbVideoLoss);
        cbVideoBlock = findViewById(R.id.cbVideoBlock);
        cbVideoOutFocus = findViewById(R.id.cbVideoOutFocus);
        cbVideoScreenDist = findViewById(R.id.cbVideoScreenDist);
        cbBackLightCom = findViewById(R.id.cbBackLightCom);
        cbStrongLightSup = findViewById(R.id.cbStrongLightSup);
        cbElectronicFog = findViewById(R.id.cbElectronicFog);
        cbLowLight = findViewById(R.id.cbLowLight);
//        spnZoomRatio = findViewById(R.id.spnZoomRatio);
        /////

        btnUp.setOnTouchListener(this);
        btnBottom.setOnTouchListener(this);
        btnLeft.setOnTouchListener(this);
        btnRight.setOnTouchListener(this);
        btnZoomin.setOnTouchListener(this);
        btnZoomout.setOnTouchListener(this);
        /////
        btnAddPreset.setOnTouchListener(this);
        btnRemovePreset.setOnTouchListener(this);
        btnEditPreset.setOnTouchListener(this);
        btnAddCruise.setOnTouchListener(this);
        btnRemoveCruise.setOnTouchListener(this);
        btnEditCruise.setOnTouchListener(this);
        /////

        cbType.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        btnTypeChecked(null);
                        int channelIdx = spnChannels.getSelectedItemPosition();
                        if (channelIdx >= 0 && channelIdx < deviceConfig.channels.length) {
                            deviceConfig.channels[channelIdx].type = cbType.getSelectedItemPosition();
                        }

                        switch (i) {
                            case 4: // 英睿红外
                                updateIrVideoMode(R.array.irVideoModeIray);
                                updateIrFocalLen(R.array.irFocalLenIray);
                                break;
                            case 6: // 高德红外
                                updateIrVideoMode(R.array.irVideoModeGuide);
                                updateIrFocalLen(R.array.irFocalLenGuide);
                                break;
                            default: // 默认选项
                                updateIrVideoMode(R.array.irVideoMode);
                                updateIrFocalLen(R.array.irFocalLen);
                                break;
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {

                    }
                }
        );

        irTempAdj = findViewById(R.id.irTempAdj);
        irObjEmi = findViewById(R.id.irObjEmi);
        irObjDistance = findViewById(R.id.irObjDistance);
        irTempReflect = findViewById(R.id.irTempRef);
        irTempEnv = findViewById(R.id.irTempEnv);
        irHumiEnv = findViewById(R.id.irHumiEnv);
        irVideoMode = findViewById(R.id.irVideoMode);
        irShutterInt = findViewById(R.id.irShutterInv);
        irHotTrace = findViewById(R.id.irHotTrace);
        irPlatteDisp = findViewById(R.id.irPaletteDisp);
        irGlobalAlarm = findViewById(R.id.irGlobalAlarm);
        irThresholdAlarm = findViewById(R.id.irThresholdAlarm);
        irEnvAlarm = findViewById(R.id.irEnvAlarm);
        irComAlarm = findViewById(R.id.irComAlarm);
        irImageStitch = findViewById(R.id.irImageStitch);
        irImageFusion = findViewById(R.id.irImageFusion); /////
        irAngle = findViewById(R.id.irAngle); /////
        irHorDisplacement = findViewById(R.id.irHorDisplacement); /////
        irVerDisplacement = findViewById(R.id.irVerDisplacement); /////
        irOperator = findViewById(R.id.irOperator);
        irObjType = findViewById(R.id.irObjType);
        irObjFlag = findViewById(R.id.irObjFlag);
        irFocalLen = findViewById(R.id.irFocalLen);
        irResolution = findViewById(R.id.irResolution);
        irRegionDistance = findViewById(R.id.irRegionDistance);
        irRegionEmi = findViewById(R.id.irRegionEmi);

        /////
        spnPreset = findViewById(R.id.spnPreset);
        spnCruise = findViewById(R.id.spnCruise);
        etCruiseDuration = findViewById(R.id.etCruiseDuration);
        etCruiseSpeed = findViewById(R.id.etCruiseSpeed);
        etPtzSpeed = findViewById(R.id.etPtzSpeed);
        spnPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                for (Device dev : channels.values()) {
                    if (dev != null && dev.isDVR() && dev.isLiving()) {
                        utilsHandler.post(() -> ptzControl(dev.id, 2, position));
                        break;
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        spnCruise.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                for (Device dev : channels.values()) {
                    if (dev != null && dev.isDVR() && dev.isLiving()) {
                        utilsHandler.post(() -> ptzControl(dev.id, 33, position));
                        break;
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        /////
        updateUI();
        drawTempRegion = new DrawTempRegion(new SVDraw(this, surfaceDraw));
    }

    private void updateIrVideoMode(int arrayResId) {
        // 加载对应的字符串数组
        String[] videoModes = getResources().getStringArray(arrayResId);
        // 创建适配器
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, videoModes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // 设置适配器到irVideoMode
        irVideoMode.setAdapter(adapter);
        irVideoMode.setSelection(iRSetting.sensorConfig.color);
    }

    private void updateIrFocalLen(int arrayResId) {
        // 加载对应的字符串数组
        String[] focalLens = getResources().getStringArray(arrayResId);
        // 创建适配器
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, focalLens);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // 设置适配器到irFocalLen
        irFocalLen.setAdapter(adapter);
        irFocalLen.setSelection(iRSetting.focalLen);
    }


//    public void btnStopClick(View v) {
//        int lastPlayChannel = spnChannels.getSelectedItemPosition() + 1;
//        Device dev = channels.get(String.valueOf(lastPlayChannel));
//        if (dev == null) return;
//        showMsg("停止预览，通道：" + lastPlayChannel);
//
//        stopLiveVideo(lastPlayChannel, 0, 0);
//
//        clearSurface(surfaceView.getHolder().getSurface());
//    }

    private void updateChannelUI() {
        List<String> list = new ArrayList<>();
        for (Channel channel : deviceConfig.channels) {
            if (channel != null)
                list.add(channel.name);
            else
                list.add("未命名");
        }
        // 设置下拉显示样式
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, list);
        adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        spnChannels.setAdapter(adapter);
    }

    private String appVersion(Context context) {
        return String.format("v%s(%s, %s)", BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE, BuildConfig.BUILD_TIME);
    }

    public void btnPhotoClick(View v) {
        final int ch = spnChannels.getSelectedItemPosition() + 1;

        int preset = spnPreset.getSelectedItemPosition();
        Log.e(Log.TAG, "进行手动抓拍");
        utilsHandler.post(() -> {
            ///
            runValidBusiness(() -> {
                takePhoto(ch, preset, true, null, true);
            });
            ///
        });
        showMsg("手动抓拍");
    }

    public void btnVideoClick(View v) {
        final int ch = spnChannels.getSelectedItemPosition() + 1;

        utilsHandler.post(() -> {
            ///
            runValidBusiness(() -> {
                takeVideo(ch, 0, 20, false);
            });
            ///
        });
        showMsg("手动录制短视频");
    }

    public void heartBeat() {
        HeartBeat info = new HeartBeat();
        Date now = new Date();
        info.year = (byte) (now.getYear() - 100);
        info.month = (byte) (now.getMonth() + 1);
        info.day = (byte) now.getDate();
        info.hour = (byte) now.getHours();
        info.min = (byte) now.getMinutes();
        info.sec = (byte) now.getSeconds();
        info.voltage = (byte) (batVoltage * 10);

        info.signal = (byte) (signalLevel * 20);
        info.sleep = sleeping;
        if (!haveSolarCharger) {
            getBatInfo();
            spgProtocol.doReportBattery(getBatterInfo());
            spgProtocol.doReportTrafficUsage(false);
        }
        spgProtocol.doHeartBeat(info); // 向服务器发送心跳数据
    }

    /////
    private int extractNumber(String filename) {
        try {
            String nameWithoutExt = filename.substring(0, filename.lastIndexOf('.'));
            return Integer.parseInt(nameWithoutExt);
        } catch (Exception e) {
            return 0;  // 默认0，非数字文件排在前面（你也可以改成 Integer.MAX_VALUE 排后面）
        }
    }

    public void runAI() throws IOException, JSONException {
/*        String src = "/storage/emulated/0/zhjinrui/spgp/aiTest";
        String dst = "/storage/emulated/0/zhjinrui/spgp/aiTest/";
        String xml = "/storage/emulated/0/zhjinrui/spgp/aiTest/result.xml";
        //String src = "/sdcard/zhjinrui/spgp/aitest/";
        //String dst = "/sdcard/zhjinrui/spgp/aitest/out/";
        //String xml = "/sdcard/zhjinrui/spgp/result.xml";
        //String bak = "/sdcard/zhjinrui/spgp/bak";
        new File(src).mkdirs();
        new File(dst).mkdirs();
        *//*new File(bak).mkdirs();
        su("copy " + src + " " + bak);*//*
        /////
        YoloV5Ncnn ai = new YoloV5Ncnn();
        YoloV5CsgNcnn aiCsg = new YoloV5CsgNcnn();
        if (deviceConfig.object_detect == 4) {
            ai.Init2(YoloV5Ncnn.PARAM_PATH, YoloV5Ncnn.BIN_PATH);
        } else if (deviceConfig.object_detect == 5) {
            aiCsg.Init(YoloV5CsgNcnn.PARAM_PATH_CSG, YoloV5CsgNcnn.BIN_PATH_CSG);
        }
        BitmapFactory.Options bitmapOption = new BitmapFactory.Options();
        bitmapOption.inMutable = true;
        File[] files = new File(src).listFiles();
        if (files == null) {
            files = new File[0];
        }
        // 筛选出 .jpg 文件并排序
        List<File> jpgFiles = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".jpg")) {
                jpgFiles.add(file);
            }
        }
        // 排序：提取文件名前缀数字，按数字排序
        jpgFiles.sort((f1, f2) -> {
            int num1 = extractNumber(f1.getName());
            int num2 = extractNumber(f2.getName());
            return Integer.compare(num1, num2);
        });
        // 转回数组
        File[] sortedFiles = jpgFiles.toArray(new File[0]);
//        if (!new File(xml).exists()) {
//            //Utils.stringToFile(xml, "<annotation>");
//        }
        FileOutputStream fos = new FileOutputStream(xml, true);
        fos.write("<annotation>\r\n".getBytes());
        JSONArray resultArray = new JSONArray();
        Log.i(Log.TAG, "开始算法兼容性测试");
//        for (File file : files) {
        for (File file : sortedFiles) {
//            if (file.isDirectory()) continue;

//            if (new File(dst + file.getName()).exists()) continue;
            String fn = file.toString();
//            if (fn.endsWith(".jpg") != true) {
//                continue;
//            }
            showMsg("检测: " + file.getName());
            Bitmap bmp = BitmapFactory.decodeFile(fn, bitmapOption);
            if (bmp == null) {
                su("copy " + file.toString() + " " + dst);
                continue;
            }

            JSONArray objectArray = new JSONArray();
            long start = System.currentTimeMillis();
            /////
            if (deviceConfig.object_detect == 4) {
                DetectInfo info = new DetectInfo();
                YoloV5Ncnn.Obj[] result = ai.Detect(bmp, false);
                info.time = new TimeRecord(System.currentTimeMillis());
                String nameWithoutExt = file.getName().substring(0, file.getName().lastIndexOf('.'));
                int imageId = 0;
                try {
                    imageId = Integer.parseInt(nameWithoutExt);
                } catch (NumberFormatException e) {
                    // 如果文件名不是纯数字，默认 0
                    imageId = 0;
                }
                // 拆分高 8 位和低 8 位
                int channel = (imageId >> 8) & 0xFF;  // 高 8 位
                int preset = imageId & 0xFF;          // 低 8 位
                // 赋值给 info
                info.channel = (byte) channel;
                info.preset = (byte) preset;
                for (int i = 0; i < result.length; i++) {
                    Log.i(Log.TAG, "AI检测结果: " + NcnnYoloV5Detector.mapName.get(result[i].label));
                }
                Log.i(Log.TAG, "AI检测耗时: " + (System.currentTimeMillis() - start) + "ms");

                Canvas canvas = new Canvas(bmp); //////
                Paint paint = new Paint(); //////
                paint.setStyle(Paint.Style.STROKE); //////
                paint.setStrokeWidth(3); //////
                paint.setColor(Color.RED); //////
                Paint textPaint = new Paint(); //////
                int fontSize = Math.max(12, bmp.getHeight() / 50); //////
                textPaint.setTextSize(fontSize); //////
                textPaint.setColor(Color.RED); //////

                String sxml = String.format("\t<result filename=\"%s\" flag=\"%s\">\r\n\t\t<time>%s</time>\r\n\t\t\t<size>\r\n\t\t\t\t<width>%d</width>\r\n\t\t\t\t<height>%d</height>\r\n\t\t\t\t<depth>3</depth>\r\n\t\t\t</size>\r\n",
                        file.getName(), result == null || result.length == 0 ? "False" : "True",
                        Utils.formatDateTime("HH:mm:ss", System.currentTimeMillis()),
                        bmp.getWidth(), bmp.getHeight());
//                DetectInfo info = new DetectInfo();
//                info.time = new TimeRecord(System.currentTimeMillis());
//                info.preset = 1;
//                info.channel = 1;
//                String txt = "";
                if (result != null) {
                    int i = 0;
                    for (YoloV5Ncnn.Obj one : result) {
                        byte classid;
                        classid = (byte) ((int) NcnnYoloV5Detector.mapName.get(one.label));
//                        if (classid != 1 && classid != 2 && classid != 5) {
                        if (classid != 2 && classid != 3 && classid != 5 && classid != 41) {
                            continue;
                        }
                        sxml += String.format("\t\t\t<object name=\"%d\">\r\n\t\t\t\t<bndbox>\r\n\t\t\t\t\t<xmin>%d</xmin>\r\n\t\t\t\t\t<ymin>%d</ymin>\r\n\t\t\t\t\t<xmax>%d</xmax>\r\n\t\t\t\t\t<ymax>%d</ymax>\r\n\t\t\t\t</bndbox>\r\n\t\t\t</object>\r\n",
                                classid, (int) one.x, (int) one.y, (int) (one.x + one.w), (int) (one.y + one.h));
                        Settings.ObjectInfo obj = new Settings.ObjectInfo();
                        obj.classID = classid;
                        obj.confidence = (byte) (one.prob * 100);
                        obj.left = (byte) ((one.x / bmp.getWidth()) * 255);
                        obj.right = (byte) (((one.x + one.w) / bmp.getWidth()) * 255);
                        obj.top = (byte) ((one.y / bmp.getHeight()) * 255);
                        obj.bottom = (byte) (((one.y + one.h) / bmp.getHeight()) * 255);
                        info.objects.add(obj);
                        String name = "";
//                        if (obj.classID == 1) name = "hoist";
//                        if (obj.classID == 2) name = "crane";
//                        if (obj.classID == 5) name = "digger";
                        if (obj.classID == 2) name = "TaDiao";
                        if (obj.classID == 3) name = "TuituJi";
                        if (obj.classID == 5) name = "WajueJi";
                        if (obj.classID == 41) name = "YanWu";
                        if ("".equals(name)) name = one.label;
//                        txt += String.format("%d,%s,%s,1,%d,%d,%d,%d\r\n", i++, name, file.getName(), (int) one.x, (int) one.y, (int) (one.x + one.w), (int) (one.y + one.h)); /////
                        Log.d(TAG, String.format("检测到对象: %s(%4.1f)(%f, %f) - (%f, %f)", one.label, one.prob, one.x, one.y, one.x + one.w, one.y + one.h));
                        canvas.drawText(String.format("%s: %3.1f", one.label, one.prob), one.x + 10, one.y + fontSize, textPaint); //////
                        canvas.drawRect(one.x, one.y, one.x + one.w, one.y + one.h, paint); //////
                    }
                    Utils.saveBitmapAsJPEG(bmp, dst + file.getName(), 90); //////
                }
//                if ("".equals(txt)) {
//                    txt = String.format("0,%s,0,0,0,0,0", file.getName());
//                }
                sxml += "\t</result>";
                fos.write(sxml.getBytes());
//                Utils.stringToFile(dst + file.getName() + ".txt", txt);
                spgProtocol.doAlertObjectDetect(info, bmp.getWidth(), bmp.getHeight());
                Log.d(Log.TAG, "绘制处理完成: " + file.getName()); //////
                bmp.recycle();
            } else if (deviceConfig.object_detect == 5) {
                YoloV5CsgNcnn.Obj[] resultCsg = aiCsg.Detect(bmp);
                Canvas canvas = new Canvas(bmp);
                Paint paint = new Paint();
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3);
                paint.setColor(Color.RED);
                Paint textPaint = new Paint();
                int fontSize = Math.max(12, bmp.getHeight() / 50);
                textPaint.setTextSize(fontSize);
                textPaint.setColor(Color.RED);
                for (YoloV5CsgNcnn.Obj result : resultCsg) {
                    Log.i(Log.TAG, "AI检测结果: " + NcnnYoloV5Detector.mapName.get(result.label));
                    byte classid = (byte) ((int) NcnnYoloV5CsgDetector.mapName.get(result.label));
                    org.json.JSONObject objJson = new org.json.JSONObject();
                    objJson.put("name", String.valueOf(classid));
                    objJson.put("bndbox", new org.json.JSONObject()
                            .put("xmin", (int) result.x)
                            .put("ymin", (int) result.y)
                            .put("xmax", (int) (result.x + result.w))
                            .put("ymax", (int) (result.y + result.h))
                    );
                    objectArray.put(objJson);
                    Log.d(TAG, String.format("检测到对象: %s(%4.1f)(%f, %f) - (%f, %f)", result.label, result.prob, result.x, result.y, result.x + result.w, result.y + result.h));
                    canvas.drawText(String.format("%s: %3.1f", result.label, result.prob), result.x + 10, result.y + fontSize, textPaint);
                    canvas.drawRect(result.x, result.y, result.x + result.w, result.y + result.h, paint);
                }
                Log.i(Log.TAG, "AI检测耗时: " + (System.currentTimeMillis() - start) + "ms");
                org.json.JSONObject resultJson = new org.json.JSONObject();
                resultJson.put("filename", file.getName());
                resultJson.put("flag", objectArray.length() > 0 ? "True" : "False");
                resultJson.put("time", Utils.formatDateTime("HH:mm:ss", System.currentTimeMillis()));
                resultJson.put("size", new org.json.JSONObject()
                        .put("width", bmp.getWidth())
                        .put("height", bmp.getHeight())
                        .put("depth", 3)
                );
                resultJson.put("object", objectArray);
                resultArray.put(resultJson);
                String fileName = file.getName();
                int dotIndex = fileName.lastIndexOf('.');
                String nameWithoutExt = (dotIndex != -1) ? fileName.substring(0, dotIndex) : fileName;
                String newFileName = nameWithoutExt + "_res.jpg";
                Log.d(Log.TAG, "绘制处理完成: " + fileName);
                Utils.saveBitmapAsJPEG(bmp, dst + newFileName, 90);
                bmp.recycle();
            }
            /////
        }
        fos.write("</annotation>".getBytes());
        fos.close();*/
    }

    public void btnDebugClick(View v) {
        new Thread(() -> {
            //coldReboot();
            //getData();
/*            try {
                runAI();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        }).start();
    }

    public void btnSaveClick(View v) {
        /////
        try {
            deviceConfig.server = tvServer.getText().toString().trim();
            deviceConfig.port = Integer.valueOf(tvPort.getText().toString().trim());
            deviceConfig.objectDetect = spnAI.getSelectedItemPosition();
            deviceConfig.traffic = Integer.valueOf(etTraffic.getText().toString().trim());
            deviceConfig.deviceId = tvID.getText().toString().trim();
            deviceConfig.confidence = Float.valueOf(etConfidence.getText().toString().trim()); /////
//            deviceConfig.onlyShowBat = cbBatOnly.isChecked();
            deviceConfig.photoCheck = cbPhotoCheck.isChecked(); /////
            deviceConfig.aiAccTest = cbAIAccTest.isChecked(); /////
            deviceConfig.toCheck = cbToCheck.isChecked(); ///////
            deviceConfig.audio = switchAudio.isChecked(); ///////
            deviceConfig.chargeControl = spnChargeController.getSelectedItemPosition();
            deviceConfig.mainBoard = spnMainBoarder.getSelectedItemPosition();
            deviceConfig.aeroDevice = spnAeroDevice.getSelectedItemPosition();
//            deviceConfig.zoomRatio = spnZoomRatio.getSelectedItemPosition(); /////
            int chanIdx = spnChannels.getSelectedItemPosition();
            if (chanIdx >= 0 && chanIdx < deviceConfig.channels.length) {
                EditChannel(deviceConfig.channels[chanIdx]);
            }
            saveDeviceConfig();

            // 更新aiParameters中的所有alertThreshold为confidence的值
            int confidenceInt = Math.round(deviceConfig.confidence * 100); // 转换为百分比整数
            for (Map.Entry<String, Settings.AIParameter> entry : settings.aiParameters.entrySet()) {
                Settings.AIParameter aiParam = entry.getValue();
                if (aiParam.alertTypes != null) {
                    for (Settings.AIAlertType alertType : aiParam.alertTypes) {
                        alertType.alertThreshold = confidenceInt;
                    }
                }
            }
            /////

            VideoCodec videoCodec;
            if (spnStreamType.getSelectedItemPosition() == 0) {
                videoCodec = settings.videoCodecs.get(chanIdx + 1 + ":" + spnStreamType.getSelectedItemPosition());
            } else {
                videoCodec = new VideoCodec();
            }
            videoCodec.vbr = (byte) spnBitRateType.getSelectedItemPosition();
            videoCodec.streamType = (byte) spnStreamType.getSelectedItemPosition();
            videoCodec.frame = Integer.valueOf(etFrame.getText().toString().trim());
            settings.videoCodecs.put(chanIdx + 1 + ":" + spnStreamType.getSelectedItemPosition(), videoCodec);
            PhotoConfig photoConfig = settings.photoConfig.get(String.valueOf(chanIdx + 1));
            photoConfig.size = (byte) spnResolution.getSelectedItemPosition();
            settings.photoConfig.put(String.valueOf(chanIdx + 1), photoConfig);
            // 更新云台运动速度
            // 获取用户输入的字符串
            String inputPtzSpeed = etPtzSpeed.getText().toString().trim();  // 使用trim()去除前后空格
            if (!inputPtzSpeed.isEmpty()) {
                try {
                    int ptzSpeed = Integer.valueOf(inputPtzSpeed);  // 转换为int类型
                    settings.ptzSpeed = ptzSpeed;  // 存储到settings.ptz_speed
                } catch (NumberFormatException e) {
                    // 如果输入的是无效的数字
                    e.printStackTrace();
                }
            }
            saveSettings(settings, SETTING_FILE);

            cAMERASetting.cameraConfig.get(String.valueOf(chanIdx + 1)).denoiseMode = (byte) spnDenoiseMode.getSelectedItemPosition();
            cAMERASetting.cameraConfig.get(String.valueOf(chanIdx + 1)).gainControl = (byte) spnGainControl.getSelectedItemPosition();
            cAMERASetting.cameraConfig.get(String.valueOf(chanIdx + 1)).focusMode = (byte) spnFocusMode.getSelectedItemPosition();
            cAMERASetting.cameraConfig.get(String.valueOf(chanIdx + 1)).dayAndNightMode = (byte) spnDayAndNightMode.getSelectedItemPosition(); ///
            cAMERASetting.cameraConfig.get(String.valueOf(chanIdx + 1)).backLightCom = (byte) (cbBackLightCom.isChecked() ? 1 : 0);
            cAMERASetting.cameraConfig.get(String.valueOf(chanIdx + 1)).strongLightSup = (byte) (cbStrongLightSup.isChecked() ? 1 : 0);
            cAMERASetting.cameraConfig.get(String.valueOf(chanIdx + 1)).electronicFog = (byte) (cbElectronicFog.isChecked() ? 1 : 0);
            cAMERASetting.cameraConfig.get(String.valueOf(chanIdx + 1)).lowLight = (byte) (cbLowLight.isChecked() ? 1 : 0);
            cAMERASetting.cameraConfig.get(String.valueOf(chanIdx + 1)).videoLoss = (byte) (cbVideoLoss.isChecked() ? 1 : 0);
            cAMERASetting.cameraConfig.get(String.valueOf(chanIdx + 1)).videoBlock = (byte) (cbVideoBlock.isChecked() ? 1 : 0);
            cAMERASetting.cameraConfig.get(String.valueOf(chanIdx + 1)).videoOutFocus = (byte) (cbVideoOutFocus.isChecked() ? 1 : 0);
            cAMERASetting.cameraConfig.get(String.valueOf(chanIdx + 1)).videoScreenDist = (byte) (cbVideoScreenDist.isChecked() ? 1 : 0);
            ///
            if (chanIdx == 0) {
                new Thread(() -> {
                    try {
                        Device dev = channels.get("1");
                        dev.setCameraParam(cAMERASetting.cameraConfig.get("1"));
                    } catch (Exception e) {
                        Log.i(Log.TAG, "手动保存机芯参数失败");
                    }
                }).start();
            }
            ///
            saveSettings(cAMERASetting, CAMERA_SETTING_FILE);
            /////

            iRSetting.sensorConfig.tempCompensate = Float.valueOf(irTempAdj.getText().toString().trim());
            iRSetting.sensorConfig.color = (byte) irVideoMode.getSelectedItemPosition();
            iRSetting.sensorConfig.emissivity = Float.valueOf(irObjEmi.getText().toString().trim());
            iRSetting.sensorConfig.distance = Float.valueOf(irObjDistance.getText().toString().trim());
            iRSetting.sensorConfig.envirHumi = Float.valueOf(irHumiEnv.getText().toString().trim());
            iRSetting.sensorConfig.envirTemp = Float.valueOf(irTempEnv.getText().toString().trim());
            iRSetting.sensorConfig.reflectTemp = Float.valueOf(irTempReflect.getText().toString().trim());

            iRSetting.sensorConfig.hotTracker = (byte) (irHotTrace.isChecked() ? 1 : 0);
            iRSetting.sensorConfig.onPalette = (byte) (irPlatteDisp.isChecked() ? 1 : 0);
            iRSetting.globalAlarm = (byte) (irGlobalAlarm.isChecked() ? 1 : 0);
            iRSetting.thresholdAlarm = (byte) (irThresholdAlarm.isChecked() ? 1 : 0);
            iRSetting.envAlarm = (byte) (irEnvAlarm.isChecked() ? 1 : 0);
            iRSetting.comAlarm = (byte) (irComAlarm.isChecked() ? 1 : 0);
            iRSetting.imageStitch = (byte) (irImageStitch.isChecked() ? 1 : 0);
            iRSetting.imageFusion = (byte) (irImageFusion.isChecked() ? 1 : 0);
            iRSetting.angle = Float.valueOf(irAngle.getText().toString().trim());
            iRSetting.horDisplacement = Integer.valueOf(irHorDisplacement.getText().toString().trim());
            iRSetting.verDisplacement = Integer.valueOf(irVerDisplacement.getText().toString().trim());
            iRSetting.focalLen = (byte) irFocalLen.getSelectedItemPosition();
            iRSetting.resolution = (byte) irResolution.getSelectedItemPosition();
            try {
                byte shutterInterval = Byte.valueOf(irShutterInt.getText().toString().trim());
                if (shutterInterval > 2) {
                    iRSetting.sensorConfig.shutterInterval = 2;
                } else {
                    iRSetting.sensorConfig.shutterInterval = shutterInterval;
                }
                saveSettings(iRSetting, IR_SETTING_FILE);
                initAlarmTasksAsync("manual save");

                showMsg("手动保存成功");
            } catch (Exception e) {
                e.printStackTrace();
                showMsg("快门间隔设置只支持整数");
            }
        } catch (Exception e) {
            Log.e(Log.TAG, "" + e);
            showMsg("手动保存失败");
        }

        /////
    }

    public void btnSleepClick(View v) {
        irOn = !irOn;

        if (irOn) {
            doWakeup("手动开启云台与红外", 23);
        } else {
            doSleep("手动关闭云台与红外", 23);
        }
        showMsg(String.format("按键手动%s负载", irOn ? "开启" : "关闭")); /////
    }

    /////
    public void btnCbPhotoCheckClick(View v) {
        deviceConfig.photoCheck = ((CheckBox) findViewById(R.id.cbPhotoCheck)).isChecked();
    }

    public void btnCbAIAccTestClick(View v) {
        deviceConfig.aiAccTest = ((CheckBox) findViewById(R.id.cbAIAccTest)).isChecked();
    }

    ///////
    public void btnCbToCheckClick(View v) {
        deviceConfig.toCheck = ((CheckBox) findViewById(R.id.cbToCheck)).isChecked();
    }

    public void btnPlayClick(View v) {
        int lastPlayChannel = spnChannels.getSelectedItemPosition() + 1;
        int preset = spnPreset.getSelectedItemPosition();
        final Device dev = channels.get(String.valueOf(lastPlayChannel));
        if (dev == null) {
            showMsg("找不到设备");
            return;
        }

        // 手动打开视频不排队
        //utilsHandler.post(() -> {
        new Thread(() -> {
            ///
            runValidBusiness(() -> {
                startLocalPlay(lastPlayChannel, preset);
            });
            ///
        }).start();
        //});
    }

    /////
    public void btnAIClick(View v) {
        try {
            runAI();
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    public void btnAddClick(View v) {
        final EditText input = new EditText(this);
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("请输入通道名称")
                .setView(input)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        /////
                        int idx = cbType.getSelectedItemPosition();
                        if (idx == DEVICE_CAMERA) {
                            int camid = Integer.valueOf(tvCamID.getText().toString().trim());
                            if (camid < 0) {
                                showMsg("相机ID必须为大于0");
                                return;
                            }
                        }
                        /////
                        int index = spnChannels.getCount();
                        Channel newChannel = new Channel();
                        newChannel.name = input.getText().toString().trim();
                        newChannel.id = index + 1;
                        EditChannel(newChannel);

                        Channel[] tmp = new Channel[deviceConfig.channels.length + 1];
                        for (int m = 0; m < deviceConfig.channels.length; m++)
                            tmp[m] = deviceConfig.channels[m];
                        tmp[index] = newChannel;
                        deviceConfig.channels = tmp;
                        updateChannelUI();
                        initDevice();
                        saveDeviceConfig();
                    }
                }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                }).create();
        dlg.show();
    }

    public void btnTypeChecked(View v) {
        boolean isCamera = cbType.getSelectedItemPosition() == DEVICE_CAMERA;
        boolean isRJ45 = (cbType.getSelectedItemPosition() == DEVICE_DVR_AIPU) || (cbType.getSelectedItemPosition() == DEVICE_DVR_HUANYU);
        boolean isUSB = (cbType.getSelectedItemPosition() == DEVICE_USB_GUIDE) || (cbType.getSelectedItemPosition() == DEVICE_USB_IRAY);


        tvCamID.setEnabled(isCamera);
        tbRotate.setEnabled(true);
        tvDVRPwd.setEnabled(isRJ45);
        tvDVRUser.setEnabled(isRJ45);
        tvDVRIP.setEnabled(isRJ45);
        tvDVRPort.setEnabled(isRJ45);
        irTempAdj.setEnabled(isUSB);
        irObjEmi.setEnabled(isUSB);
        irObjDistance.setEnabled(isUSB);
        irTempReflect.setEnabled(isUSB);
        irTempEnv.setEnabled(isUSB);
        irHumiEnv.setEnabled(isUSB);
        irVideoMode.setEnabled(isUSB);
        irShutterInt.setEnabled(isUSB);
//        irHotTrace.setEnabled(isUSB);
//        irPlatteDisp.setEnabled(isUSB);
//        irGlobalAlarm.setEnabled(isUSB);
//        irThresholdAlarm.setEnabled(isUSB);
//        irEnvAlarm.setEnabled(isUSB);
//        irComAlarm.setEnabled(isUSB);
//        irImageStitch.setEnabled(isUSB);
//        irImageFusion.setEnabled(isUSB);
        irAngle.setEnabled(isUSB);
        irHorDisplacement.setEnabled(isUSB);
        irVerDisplacement.setEnabled(isUSB);
        irOperator.setEnabled(isUSB);
        irObjType.setEnabled(isUSB);
        irObjFlag.setEnabled(isUSB);
        irFocalLen.setEnabled(isUSB);
        irResolution.setEnabled(isUSB);
        irRegionDistance.setEnabled(isUSB);
        irRegionEmi.setEnabled(isUSB);
        /////


        ///// release版本下都需要屏蔽的按钮，不区分通道//// debug模式下，将通道二相关的按钮打开
        if (!BuildConfig.DEBUG) {
            irGlobalAlarm.setEnabled(false);
            irThresholdAlarm.setEnabled(false);
            irEnvAlarm.setEnabled(false);
            irComAlarm.setEnabled(false);
            irHotTrace.setEnabled(false);
            irPlatteDisp.setEnabled(false);
            irImageStitch.setEnabled(false);
            irImageFusion.setEnabled(false);

            cbToCheck.setEnabled(false);
            cbPhotoCheck.setEnabled(false);
            switchAudio.setEnabled(false);

            cbBackLightCom.setEnabled(false);
            cbStrongLightSup.setEnabled(false);
            cbElectronicFog.setEnabled(false);
            cbLowLight.setEnabled(false);

            cbVideoLoss.setEnabled(false);
            cbVideoBlock.setEnabled(false);
            cbVideoOutFocus.setEnabled(false);
            cbVideoScreenDist.setEnabled(false);
            cbAIAccTest.setEnabled(false);

        } else {
            irGlobalAlarm.setEnabled(isUSB);
            irThresholdAlarm.setEnabled(isUSB);
            irEnvAlarm.setEnabled(isUSB);
            irComAlarm.setEnabled(isUSB);
            irHotTrace.setEnabled(isUSB);
            irPlatteDisp.setEnabled(isUSB);
            irImageStitch.setEnabled(isUSB);
            irImageFusion.setEnabled(isUSB);
        }
    }

    public void btnDeleteClick(View v) {
        int index = spnChannels.getSelectedItemPosition();
        if (index < 0 || index > deviceConfig.channels.length - 1) return;

        for (int i = index; i < deviceConfig.channels.length - 1; i++)
            deviceConfig.channels[i] = deviceConfig.channels[i + 1];

        Channel[] tmp = new Channel[deviceConfig.channels.length - 1];
        for (int i = 0; i < tmp.length; i++)
            tmp[i] = deviceConfig.channels[i];

        deviceConfig.channels = tmp;
        updateChannelUI();
        initDevice();
        saveDeviceConfig();
        showMsg("删除成功");
    }
    ////////

    private void EditChannel(Channel channel) {
        try {
            int idx = cbType.getSelectedItemPosition();
            if (idx == DEVICE_CAMERA) {
                channel.type = 0;
                channel.camera = Integer.valueOf(tvCamID.getText().toString().trim());
                if (channel.camera > 1)
                    showMsg("相机ID错误，只能为0或者1");
                channel.rotate = Integer.valueOf(tbRotate.getText().toString().trim());
            } else {
                channel.type = idx;
                channel.password = tvDVRPwd.getText().toString();
                channel.user = tvDVRUser.getText().toString();
                channel.server = tvDVRIP.getText().toString().trim();
                channel.port = Integer.valueOf(tvDVRPort.getText().toString().trim());
                if (channel.type == DEVICE_USB_GUIDE || channel.type == DEVICE_USB_IRAY) {
                    iRSetting.sensorConfig.tempCompensate = Float.valueOf(tvCamID.getText().toString().trim());
                    saveSettings(iRSetting, IR_SETTING_FILE);
                }
            }
            channel.popCamera = spnPopCamera.getSelectedItemPosition();
        } catch (Exception e) {
            Log.i(Log.TAG, "编辑通道异常：" + e.getMessage());
        }
    }

    public void btnEditClick(View v) {
        int index = spnChannels.getSelectedItemPosition();
        if (index < 0 || index > deviceConfig.channels.length - 1) return;

        int idx = cbType.getSelectedItemPosition();
        if (idx == DEVICE_CAMERA) {
            int camid = Integer.valueOf(tvCamID.getText().toString().trim());
//            if (camid > 1 || camid < 0) {
//                showMsg("相机ID必须为0或者1");
//                return;
//            }
        }
        Channel channel = deviceConfig.channels[index];
        EditChannel(channel);
        initDevice();
        saveDeviceConfig();
        showMsg("修改设备通道成功");
    }

    public void btnStopClick(View v) {
        int lastPlayChannel = spnChannels.getSelectedItemPosition() + 1;
        Device dev = channels.get(String.valueOf(lastPlayChannel));
        if (dev == null) return;
        showMsg("停止预览，通道：" + lastPlayChannel);

        stopLiveVideo(lastPlayChannel, 0, 0);

        if (dev.type != DEVICE_CAMERA) {
            clearSurface(surfaceView.getHolder().getSurface());
        } else {
            clearSurface(surfaceView);
        }
    }

    private void clearSurface(SurfaceView surfaceView) {
        if (surfaceView == null) return;

        // 在主线程执行Canvas绘制
        surfaceView.post(new Runnable() {
            @Override
            public void run() {
                Canvas canvas = null;
                try {
                    // 锁定Canvas进行绘制
                    canvas = surfaceView.getHolder().lockCanvas();
                    if (canvas != null) {
                        canvas.drawColor(Color.BLACK);  // 绘制黑色
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (canvas != null) {
                        // 提交绘制
                        surfaceView.getHolder().unlockCanvasAndPost(canvas);
                    }
                }
            }
        });
    }

    private void clearSurface(Surface surface) {
        try {
            EGL10 egl = (EGL10) EGLContext.getEGL();
            EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            egl.eglInitialize(display, null);

            int[] attribList = {
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_NONE, 0,
                    EGL10.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            egl.eglChooseConfig(display, attribList, configs, configs.length, numConfigs);
            EGLConfig config = configs[0];
            EGLContext context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, new int[]{
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL10.EGL_NONE
            });
            EGLSurface eglSurface = egl.eglCreateWindowSurface(display, config, surface,
                    new int[]{
                            EGL14.EGL_NONE
                    });
            egl.eglMakeCurrent(display, eglSurface, eglSurface, context);
            GLES20.glClearColor(0, 0, 0, 1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            egl.eglSwapBuffers(display, eglSurface);
            egl.eglDestroySurface(display, eglSurface);
            egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                    EGL10.EGL_NO_CONTEXT);
            egl.eglDestroyContext(display, context);
            egl.eglTerminate(display);
        } catch (Exception e) {

        }
    }

    /**
     * 转换老的INI配置为新的JSON配置，为了兼容使用
     */
    public void convertIniConfigToJson() {
        Map<String, Channel> tmp = new HashMap<>();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(OLD_CONFIG_FILE));
            String line, key, value;
            while ((line = reader.readLine()) != null) {
                // 跳过空行
                if (line == null || line.equals("")) continue;

                key = left(line, "=").trim();
                value = right(line, "=").trim();

                // 如果是非 key = value 行则忽略
                if (key == null || value == null || key.equals(line)) continue;

                if (key.equals("card_number")) {
                    deviceConfig.deviceId = value;
                } else if (key.equals("server_ip")) {
                    deviceConfig.server = value;
                } else if (key.equals("server_port")) {
                    deviceConfig.port = Integer.valueOf(value);
                } else if (key.equals("max_upload_file_size")) {
                    continue;
                } else if (key.equals("max_upload_image_size")) {
                    deviceConfig.maxUploadFileSize = Integer.valueOf(value);
                } else if (key.equals("object_detect")) {
                    deviceConfig.objectDetect = Integer.valueOf(value);
                } else if (key.equals("rotate")) {
                    deviceConfig.rotate = Integer.valueOf(value);
                } else if (key.equals("camera_number")) {
                    if (value.equals("0")) {
                        tmp.clear();
                        Channel ch1 = new Channel(1, "通道1", 0);
                        Channel ch2 = new Channel(2, "通道2", 1);
                        tmp.put("1", ch1);
                        tmp.put("2", ch2);
                    } else if (value.equals("1")) {
                        Channel dvr = new Channel(1, "球机", "192.168.200.11", 8000, "admin", "admin12345");
                        tmp.put("1", dvr);
                    }
                } else {  // carmer_ip, 等
                    int idx = key.lastIndexOf('_');
                    String sch = key.substring(idx + 1);

                    Channel ch = tmp.get(sch);
                    if (ch == null) {
                        ch = new Channel();
                        ch.id = Integer.valueOf(sch);
                        ch.type = 1;
                        tmp.put(sch, ch);
                    }
                    if (key.startsWith("camera_ip_")) {
                        ch.server = value;
                    } else if (key.startsWith("camera_port_")) {
                        ch.port = Integer.valueOf(value);
                    } else if (key.startsWith("camera_user_")) {
                        ch.user = value;
                    } else if (key.startsWith("camera_password_")) {
                        ch.password = value;
                    }
                }
            }

            deviceConfig.channels = new Channel[tmp.size()];
            int i = 0;
            for (Channel item : tmp.values()) {
                deviceConfig.channels[i] = item;
                i++;
            }

            reader.close();
            saveDeviceConfig();
        } catch (Exception e) {
            Log.e(Log.TAG, "配置升级转换失败：" + e.getMessage());
        }
    }

    /**
     * 动态加载读写权限
     *
     * @param activity
     */
    public void verifyStoragePermissions(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        try {
            // 检测是否有写的权限
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // 没有写的权限，去申请写的权限，会弹出对话框
                ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
            int cameraPermission = ActivityCompat.checkSelfPermission(activity,
                    Manifest.permission.CAMERA);
            if (cameraPermission != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat
                        .requestPermissions(
                                this,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                120);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void registerBoradcastReceiver() {
        // 注册广播，主要是闹钟定时器消息处理
        IntentFilter myIntentFilter = new IntentFilter();
        myIntentFilter.addAction(ACTION_PHOTO);
        myIntentFilter.addAction(ACTION_INIT_PHOTO); /////
        myIntentFilter.addAction(ACTION_CHECK_LINE);
        myIntentFilter.addAction(ACTION_VIDEO);
        myIntentFilter.addAction(ACTION_RECORD); /////
        myIntentFilter.addAction(ACTION_REBOOT);
        myIntentFilter.addAction(ACTION_PROTECT);
        myIntentFilter.addAction(ACTION_TIME_CHANGED);
        myIntentFilter.addAction(ACTION_HEART);
        myIntentFilter.addAction(ACTION_SAMPLE);
        myIntentFilter.addAction(ACTION_PHOTO_CHECK); /////
        myIntentFilter.addAction(POWEROFF_INTENT);
        myIntentFilter.addAction(POWERON_INTENT);
        myIntentFilter.addAction(POWERON_RGB_INTENT); /////
        myIntentFilter.addAction(POWERON_IR_INTENT);
        myIntentFilter.addAction(ACTION_SMS);
        myIntentFilter.addAction(BATCOM_SAMPLE_ACTION);
        myIntentFilter.addAction(AEROINFO_ACTION);
        myIntentFilter.addAction(GYROINFO_ACTION);
        myIntentFilter.addAction(ACTION_RETRY_PHOTOING);
        myIntentFilter.addAction(USAGE_RATE);
        //myIntentFilter.addAction(ACTION_START_WIFIAP);
        //myIntentFilter.addAction(ACTION_STOP_WIFIAP);
        registerReceiver(onIntentReceive, myIntentFilter);
    }


    @Override
    public BatteryInfo getBatterInfo() {
        BatteryInfo batteryInfo = new BatteryInfo();
        batteryInfo.batNo = 1;  // ？？？
        batteryInfo.time = new TimeRecord(System.currentTimeMillis());
        if (deviceConfig.chargeControl == 6 || deviceConfig.chargeControl == 9) { ///////
            batteryInfo.batPercent = (byte) batPrecent;
        } else {
            batteryInfo.batPercent = (byte) getBatPercent();
        }
        batteryInfo.batVoltage = batVoltage;
        batteryInfo.batAmpler = batAmper;
//        batteryInfo.charge = solarAmpler > loadAmpler;
        batteryInfo.charge = batAmper >= 0;
        batteryInfo.Temp = temperature;
        batteryInfo.solarVoltage = solarVoltage;
        batteryInfo.solarAmpler = solarAmpler;
        batteryInfo.loadVoltage = batVoltage;
        batteryInfo.loadAmpler = loadAmpler;
        return batteryInfo;
    }



    private void doWifiAction(boolean on) {
        /*delayHandler.removeCallbacksAndMessages(wifiOperatorToken);
        delayHandler.postDelayed(()-> {
            // 要延时检查wifi热点状态，热点的操作需要一个过程
            if (!wifiAP.isEnabled()) {
                if (!SPGProtocol.Logged) { // 设备不在线
                    //wifiAP.enable();
                } else {
                    Date now = new Date();
                    if (now.after(dateFromString(WIFIAP_FROM)) && now.before(dateFromString(WIFIAP_UNTIL))) {
                        //wifiAP.enable();
                    }
                }
            }
        }, wifiOperatorToken, 5 * PERIOD_SECOND);*/
        if (is6735) return;

//        WifiAP wifiAP = WifiAP.getInstance(this, deviceConfig.deviceId, "11121314"); /////
        WifiAP wifiAP = WifiAP.getInstance(this, ssid, "11121314"); /////
        if (wifiAP != null) {
            if (on) {
                su("svc Wi-Fi enable");
                wifiAP.enable();
            } else {
                wifiAP.disable();
                su("svc Wi-Fi disable");
            }
        }
    }

    private void cancelAllAlarms() {
        Log.i(Log.TAG, "取消系统所有闹钟事件");
//        if (photoIntent != null) alarmManager.cancel(photoIntent);
        if (photoIntent != null) cancelAllPhotoAlarms();
        if (videoIntent != null) alarmManager.cancel(videoIntent);
        cancelAllCheckLineAlarms();
        if (powerOnIntent != null) alarmManager.cancel(powerOnIntent);
        if (powerRgbOnIntent != null) alarmManager.cancel(powerRgbOnIntent); /////
        if (powerIrOnIntent != null) alarmManager.cancel(powerIrOnIntent);
        if (powerOffIntent != null) alarmManager.cancel(powerOffIntent);
        if (heartBeatIntent != null) alarmManager.cancel(heartBeatIntent);
        if (sampleIntent != null) alarmManager.cancel(sampleIntent);
        if (rebootIntent1 != null) alarmManager.cancel(rebootIntent1);
        if (rebootIntent2 != null) alarmManager.cancel(rebootIntent2);
        if (protectIntent != null) alarmManager.cancel(protectIntent);
        if (utilizationRateIntent != null) alarmManager.cancel(utilizationRateIntent);

//        if (wifiIntent != null) alarmManager.cancel(wifiIntent);
//        if (protectIntent != null) alarmManager.cancel(photoCheckIntent); /////
    }

    private boolean setRtc() {
        if (deviceConfig.chargeControl <= 1 || deviceConfig.chargeControl == 8)
            return true;

        boolean ok = false;

        TaskManager.add(TaskManager.Task.SetRTC.toString());
        int[] timeArray = new int[6];
        Calendar calendar = Calendar.getInstance();  // 获取当前时间
        timeArray[0] = calendar.get(Calendar.YEAR) - 2000;
        timeArray[1] = calendar.get(Calendar.MONTH) + 1;
        timeArray[2] = calendar.get(Calendar.DAY_OF_MONTH);
        timeArray[3] = calendar.get(Calendar.HOUR_OF_DAY);
        timeArray[4] = calendar.get(Calendar.MINUTE);
        timeArray[5] = calendar.get(Calendar.SECOND);

        for (int i = 0; i < 3; i++) {
            //openShare("供电板同步时钟");
            if (deviceConfig.chargeControl == 6) {
                ok = RS485Impl.Instance().setTime(timeArray);
                if (ok) {
                    Log.d(Log.TAG, "汇能精电：设置时间成功！");
                } else {
                    Log.d(Log.TAG, "汇能精电：设置时间失败！");
                }
            } else {
                ok = RS485Impl.Instance().setRtcInfo(timeArray);
            }
            /////
            if (ok) {
                break;
            }
            /////
        }
        finishTask(TaskManager.Task.SetRTC.toString());
        Log.i(Log.TAG, "设置电源板RTC时钟：" + timeArray[0] + "-" + timeArray[1] + "-" + timeArray[2] + " " + timeArray[3] + ":" + timeArray[4] + ":" + timeArray[5] + " 结果：" + ok);

        return ok;
    }
    /////

    private void doTimeChangedAction(Context context, Intent intent) {
        /*updateOnline("系统时间更改");

        TaskManager.add(intent.getAction());
        lastRecvTime = System.currentTimeMillis();
        cancelAllAlarms();
        initAlarmTasks();
        serialHandler.post(() -> setRtc());
        finishTask(intent.getAction());*/
    }

    private void doSampleAction() {
        alarmInitTask(sampleIntent, "采样", settings.onlineCfg.sample * PERIOD_MINUTE);
//        alarmInitTask(sampleIntent, "采样", 10*1000);
        serialHandler.post(() -> getData()); /////
    }

    private void doAeroInfoAction(Intent intent) {
        AeroInfo aeroInfo = new AeroInfo();
        if (deviceConfig.aeroDevice == 6 || deviceConfig.aeroDevice == 7){
            float[] data = intent.getFloatArrayExtra("aeroinfo");
            if (data == null || data.length < 7) {
                Log.i(Log.TAG, "微气象数据长度不足，无法解析：" + (data == null ? 0 : data.length));
                return;
            }
            aeroInfo.Temp = data[0];              // 温度
            aeroInfo.Humidity = data[1];          // 湿度
            aeroInfo.AtomosPress = data[2];       // 气压
            aeroInfo.WindDirection = (int) data[6];  // 风向

            // aeroInfo.RainFall = data.length > 10 ? data[10] : -999;             // 雨量间隔累计
//            data[9] 是雨强
            aeroInfo.RainFall = 0;

            if (deviceConfig.aeroDevice == 6){
                aeroInfo.RainFall = -999;
            }

            aeroInfo.WindSpeed = data[3];         // 瞬时风速
//            aeroInfo.WindSpeedByMin = data[4];    // 2分钟
            aeroInfo.WindSpeedBy10Min = data[5];  // 10分钟

        }else {
            //        if (spgProtocol == null || !SPGProtocol.Logged) return;
            // 气象数据样本: 0R0,Dn=017D,Dm=087D,Dx=090D,Sn=000.7M,Sm=000.9M,Sx=001.4M,Ta=022.3C,Ua=058.8P,Pa=001012.9H,Rc=0000.0M,Sr=0000.0W
            String[] ss = intent.getStringExtra("aeroinfo").trim()
                    .replace("0R0,", "")
                    .replace(" ", "").split(",");

            for (String kv : ss) {
                String ss2[] = kv.split("=");
                if ("Dx".equals(ss2[0]))
                    aeroInfo.WindDirection = Integer.valueOf(ss2[1].trim().replace("D", ""));
                else if ("Dm".equals(ss2[0]))
                    aeroInfo.WindDirectionByMin = Integer.valueOf(ss2[1].trim().replace("D", ""));
                else if ("Sn".equals(ss2[0]))
                    aeroInfo.WindSpeed = Float.valueOf(ss2[1].trim().replace("M", ""));
                else if ("Sm".equals(ss2[0])) {
                    aeroInfo.WindSpeedByMin = Float.valueOf(ss2[1].trim().replace("M", ""));
                    aeroInfo.WindSpeedBy10Min = aeroInfo.WindSpeedByMin;
                } else if ("Sx".equals(ss2[0])) {
                    aeroInfo.MaxWindSpeedBy10Min = Float.valueOf(ss2[1].trim().replace("M", ""));
                } else if ("Ta".equals(ss2[0])) {
                    aeroInfo.Temp = Float.valueOf(ss2[1].trim().replace("C", ""));
                } else if ("Ua".equals(ss2[0])) {
                    aeroInfo.Humidity = Float.valueOf(ss2[1].trim().replace("P", ""));
                } else if ("Pa".equals(ss2[0])) {
                    aeroInfo.AtomosPress = Float.valueOf(ss2[1].trim().replace("H", ""));
                } else if ("Rc".equals(ss2[0])) {
                    aeroInfo.RainFall = Float.valueOf(ss2[1].trim().replace("M", ""));
                } else if ("Sr".equals(ss2[0])) {
                    aeroInfo.Sunshine = Float.valueOf(ss2[1].trim().replace("W", ""));
                }
            }
        }

        //// 这个地方对微气象的日照数据进行判断
        aeroInfoAtomicReference.set(aeroInfo);
        spgProtocol.doReportAero(aeroInfo);
    }

    private void doGyroInfoAction() {
        if (spgProtocol == null) return;

        if (sensorManager == null || gyroSensor == null) return;

        final int DISCARD = 5;     // 丢掉前5帧
        final int KEEP = 15;       // 再取15帧做平均
        final int TARGET = DISCARD + KEEP;
        final SensorEventListener oneShot = new SensorEventListener() {
            int cnt = 0;
            int kept = 0;
            float sumGx = 0f, sumGy = 0f, sumGz = 0f;

            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event == null || event.sensor == null) return;
                if (event.sensor.getType() != Sensor.TYPE_GYROSCOPE) return;

                if (spgProtocol == null) return;

                float gx = event.values[0];
                float gy = event.values[1];
                float gz = event.values[2];

                cnt++;

                if (cnt > DISCARD) {
                    sumGx += gx;
                    sumGy += gy;
                    sumGz += gz;
                    kept++;
                }

                if (cnt >= TARGET) {
                    sensorManager.unregisterListener(this);

                    gx = sumGx / kept;
                    gy = sumGy / kept;
                    gz = sumGz / kept;

                    Vector<Float3> gyroArray = new Vector<>(1);
                    gyroArray.addElement(new Float3(gx, gy, gz));
                    spgProtocol.doReportGyroInfo(gyroArray); /////

//                    //返回X、Y、Z轴角度和温度
//                    float[] gyroInfo = intent.getFloatArrayExtra("gyroinfo");
//                    if (Array.getLength(gyroInfo) == 0 || Array.getLength(gyroInfo) % 4 != 0) {
//                        Log.e(Log.TAG, "读取陀螺仪数据错误");
//                        return;
//                    }
//                    Vector<Float3> gyroArray = new Vector<>(2);
//                    for (int i = 0; i < gyroInfo.length / 4; i++) {
//                        gyroArray.addElement(new Float3(gyroInfo[i], gyroInfo[i + 1], gyroInfo[i + 2]));
//                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        sensorManager.registerListener(oneShot, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    private boolean isPhotoingSafe(int maxMinutes) {
        Date currTime = new Date(System.currentTimeMillis());
        int currMinutes = currTime.getHours() * 60 + currTime.getMinutes() != 0 ? currTime.getHours() * 60 + currTime.getMinutes() : 24 * 60;
        if (lastPhotoTime != null) {
            int intervalMinutes = Math.abs(currMinutes - lastPhotoTime.hour * 60 - lastPhotoTime.min);
            Log.i(Log.TAG, String.format("当前时间是：%02d:%02d:%02d，上一个拍照时间是：%02d:%02d:%02d，相差%d分钟",
                    currTime.getHours(), currTime.getMinutes(), currTime.getSeconds(),
                    lastPhotoTime.hour, lastPhotoTime.min, lastPhotoTime.sec,
                    intervalMinutes));
            if (intervalMinutes < maxMinutes) {
                return false;
            }
        }

        if (nextPhotoTime != null) {
            int intervalMinutes = Math.abs(currMinutes - nextPhotoTime.hour * 60 - nextPhotoTime.min);
            Log.i(Log.TAG, String.format("当前时间是：%02d:%02d:%02d，下一个拍照时间是：%02d:%02d:%02d，相差%d分钟",
                    currTime.getHours(), currTime.getMinutes(), currTime.getSeconds(),
                    nextPhotoTime.hour, nextPhotoTime.min, nextPhotoTime.sec,
                    intervalMinutes));
            if (intervalMinutes < maxMinutes) {
                return false;
            }
        }
        return true;
    }

    private static boolean firstHeartAction = true; // 添加静态标志

    private void doHeartAction() {

        if (firstHeartAction) {
            firstHeartAction = false;
            Log.d("HeartAction", "当前时间: " + formatTimestamp(System.currentTimeMillis()));
            Log.d("HeartAction", "上次接收数据报文时间: " + formatTimestamp(lastRecvTime));           // 在校时之前，接收到的最后一条指令，这个时候lastRecvTime时间远小于System.currentTimeMillis()
        }

        TaskManager.add(ACTION_HEART);
        /////
        if (deviceConfig.aiAccTest) {
            alarmInitTask(heartBeatIntent, "心跳", 30 * PERIOD_SECOND);
        } else {
            alarmInitTask(heartBeatIntent, "心跳", settings.onlineCfg.heart * PERIOD_MINUTE);
        }
        /////
        saveTrafficData();


        if (System.currentTimeMillis() - lastRecvTime > settings.onlineCfg.heart * PERIOD_MINUTE * 5) {      // 心跳间隔 * 5
            SPGProtocol.Logged = false;
            SPGProtocol.TimeSynced = false;
            offLineRecode.updateRecode();

            // 保证在拍照的前后3分钟不做重启处理，以免丢失照片
            if (isPhotoingSafe(3)) {
                if (!DEBUG) {
                    if (offLineRecode.getRecordGap() < PERIOD_HOUR * 2) {           // 若累计离线时间达到或超过2小时，
                        Log.i(Log.TAG, "心跳超时，重启安卓系统");
    //                        restartApplication(this, 5);
                        rebootSystem("心跳超时"); // sunwu
                    } else {
                        offLineRecode.clearRecord();
                        rebootSystem("心跳超时");
                    }
                }
            }
        }   // 超过5次心跳，不在拍照安全期内的话就重启apk，累计离线时间超过2小时，进行重启系统


        if (SPGProtocol.Logged) {
            heartBeat();
            if (!SPGProtocol.TimeSynced) {
                spgProtocol.doSyncTime(); /////
            }

            if (!spgProtocol.uploading && SPGProtocol.Logged) {
                boolean isPhotoing = false;
                for (Channel channel : deviceConfig.channels) {
                    Device dev = channels.get(String.valueOf(channel.id));
                    if (dev != null && dev.isPhotoing()) {
                        isPhotoing = true;
                    }
                }
                if (!isPhotoing) {
                    uploadHandler.post(() -> doPendingFile());
                }
            }
        } else {
            spgProtocol.doBootContactInfo(); /////
        }


        if (onlineEnd != 0 && System.currentTimeMillis() > onlineEnd) {
            doOnlineTimeout("视频预览超时，关闭视频和Share");
            // 检查是否有设备正在直播，如果有则不执行超时操作
            boolean checkContinue = false;
            for (Channel channel : deviceConfig.channels) {
                Device dev = channels.get(String.valueOf(channel.id));
                if (dev != null && dev.isLiving()) {
                    checkContinue = true;
//                    break;
                }
            }
//            if (checkContinue) {
//                doOnlineTimeout("视频预览超时，关闭视频");
//            }
            if (!checkContinue) onlineEnd = 0;  // 避免在online超时后，重复触发动作
        }

        if (sleepTime != 0 && System.currentTimeMillis() > sleepTime && !isWorkHour()) {
            for (Channel dev : deviceConfig.channels)
                spgProtocol.doCamOff(dev.id, 1);  // 通知服务器休眠了
            doSleep("空闲超时，开始关闭云台与红外休眠", 23);
            sleepTime = 0;
        }
        doUpdateStatus();

        if (spgProtocol.uploading && spgProtocol.uploadTimeout()) {
            spgProtocol.doFileUploadError("超时强制停止传输");
        }

//        new Thread(() -> {
//            // 心跳超时处理，超时5秒检查释放cpu锁
//            for (int i = 0; i < 50; i++) {
//                if (!TaskManager.contain(ACTION_HEART)) break;
//                SystemClock.sleep(100);
//            }
//            finishTask(ACTION_HEART);
//        }).start();
        /////
        // 心跳超时处理，超时5秒检查释放cpu锁
        delayHandler.postDelayed(() -> finishTask(ACTION_HEART), 5 * PERIOD_SECOND);
        /////
    }


    // 时间戳转字符串
    public static String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String dateString = sdf.format(new Date(timestamp));
        return dateString;
    }


    @Override
    public void onHeartBeatReceived() {
        if ((System.currentTimeMillis() - lastHeartTime) / PERIOD_MINUTE > settings.onlineCfg.heart) {
            Log.i(Log.TAG, "心跳超时 " + (System.currentTimeMillis() - lastHeartTime) / PERIOD_MINUTE + " 分钟");
        }
        lastHeartTime = System.currentTimeMillis();
        finishTask(ACTION_HEART);
    }

    @Override
    public void onLoggedIn() {
        // 如果有离线记录，删除之
        offLineRecode.clearRecord();
    }

    private void doPendingFile() {
        boolean okUpload = false;
        File[] files = new File(FILE_PATH).listFiles();
        if (files == null) return;

        // 降序排列，先传最新照片
        Arrays.sort(files, (o1, o2) -> (int) (o2.lastModified() - o1.lastModified()));

        for (File file : files) {
            if (!file.isFile()) continue;
            String filename = file.getName();
            String fn = left(filename, ".");
            String ext = right(filename, ".");
            String ss[] = fn.split("_");
            if (ss.length < 4) {
                continue;
            }

            if (!spgProtocol.uploading) {
                // 之前图片上传成功，可以立即上传图片；之前图片上传失败，要间隔一个心跳时间，避免间隔太短导致破图，同时检查在线再传图片
                if (spgProtocol.uploadSucceed) {
                    // 2022-02-10 这里改一下，上传图片成功要间隔至少5秒再上传累积图片，目前发现183服务器偶尔会不停下发补包为0的87指令，原因不明，
                    // 导致破图和丢图，电科院后台未发现有此问题
                    if (Math.abs(System.currentTimeMillis() - spgProtocol.uploadSucceedTime) > 5 * PERIOD_SECOND) {
                        okUpload = true;
                    }
                } else {
                    if (uploadThisTime && SPGProtocol.Logged) {
                        okUpload = true;
                        uploadThisTime = false;
                    } else {
                        uploadThisTime = true;  // 下一轮心跳尝试传输
                    }
                }
                if (okUpload) new Thread(() -> {
                    long time = stringToTimestamp("yyyyMMddHHmmss", ss[0]);
                    Log.i(Log.TAG, "上传累积文件：" + filename);
                    TaskManager.add(FILE_PATH + filename);
                    spgProtocol.doUploadFile(time, file.getPath(), Integer.valueOf(ss[2]), Integer.valueOf(ss[3]), ext.equals(EXT_JPG) ? SPGProtocol.FILE_TYPE.PHOTO : SPGProtocol.FILE_TYPE.VIDEO); /////
                }).start();
            }
            // 添加break，每次心跳检查中只上传一张累积照片，避免上传照片间隔太短导致破图情况，另外频繁尝试上传照片也会影响功耗
            break;
        }
    }

    // 定时录像动作
    private void doVideoAction(Intent intent) {
        if (currentMode == MODE_WAKEUP || currentMode == MODE_SLEEP) {
            Log.i(Log.TAG, "低功耗模式下不执行定时录像任务");
            return;
        }
        int index = intent.getIntExtra("index", 0);
        initVideoTask(index + 1);

        List<VideoTimeItem> videoPolicy = adjustOverlappingItems(loadRecordingPolicyFile());
        if (index >= videoPolicy.size() || index < 0) return;
        final VideoTimeItem item = videoPolicy.get(index);
        if (item == null) return;

        utilsHandler.post(() -> {
            ///
            runValidBusiness(() -> {
                takeVideo(item, false);
            });
            ///
        });
//        utilsHandler.post(() -> takeVideo(item, false, 0)); ///////
    }

    /////
    private void doTimeConsumingTask(String task, boolean isDVR) {
        cpuLock();
        TaskManager.add(task);
        if (deviceConfig.toCheck) {
            if (isDVR) openShare(task);
        }
    }

    private void finishTimeConsumeTask(String task, boolean isDVR) {
        TaskManager.remove(task);
        if (deviceConfig.toCheck) {
            if (isDVR) closeShare(task);
        }
        tryCpuUnlock();
    }

    private void runCheckLine(int channel, int group, int count) {
        List<CheckGroup> cgs = settings.checkGroups.get(String.valueOf(channel));
        Device dev = channels.get(String.valueOf(channel));
        if (dev == null || cgs == null || group < 0 || group >= cgs.size()) return;

        if (isSleepMode()) {
            Log.i(Log.TAG, "休眠模式下不执行定时巡检，通道：" + channel);
            return;
        }

        final String taskName = "定时巡检_" + channel;
        final String wakeupReason = dev.isUSB() ? "红外定时巡检" : "可见光定时巡检";

        if (currentMode == MODE_WAKEUP) {
            // 巡检和拉流一样属于会使用机芯/云台的任务，唤醒模式下先刷新保活，再执行上电和打开设备。
            markWakeupActivity(dev, "唤醒模式下" + wakeupReason);
        }
        if (dev.isDVR()) {
            wakeupDevice(dev, wakeupReason);
        }

        doTimeConsumingTask(taskName, dev.isDVR());
        dev.open(0, dev.new onOpenCallback() {
            @Override
            public void openSucceed() {
                runCheckLineAfterDeviceReady(channel, group, count, cgs, dev, taskName, wakeupReason);
            }

            @Override
            public void openFailed(int errorCode) {
                Log.i(Log.TAG, wakeupReason + "打开失败，取消本次定时巡检，通道：" + channel);
                finishTimeConsumeTask(taskName, dev.isDVR());
                sleepDevice(channel, wakeupReason + "打开失败");
            }
        }, DVR_BOOT_TIME, false, true, false); ///
    }

    private void runCheckLineAfterDeviceReady(int channel, int group, int count, List<CheckGroup> cgs,
                                              Device dev, String taskName, String wakeupReason) {
        boolean ret1, ret2 = false, ret3, ret4;
        if (dev.isDVR() && !dev.isUSB()) {
            for (int i = 0; i < 3; i++) {
                ret1 = dev.setPhotoParam(settings.photoConfig.get(String.valueOf(channel)));
                if (dev.type == DEVICE_DVR_AIPU) {
                    if (dev.isOldCamera) {
                        ret2 = dev.setOSD(settings.osds.get(String.valueOf(channel)), true);
                    } else {
                        ret2 = dev.setOSD(settings.osds.get(String.valueOf(channel)), false);
                    }
                } else if (dev.type == DEVICE_DVR_HUANYU) {
                    ret2 = dev.updateStatusText(settings.osds.get(String.valueOf(channel)), getStatusText(), false);
                }
                ret3 = dev.setCodec(settings.videoCodecs.get(String.valueOf(channel) + ":0"));
                ret4 = dev.setRecordTimes(getDeviceRecordingPolicyForCurrentMode());
                if (ret1 && ret2 && ret3 && ret4) {
                    break;
                }
            }
        }

        if (deviceConfig.toCheck) {
            if (dev.isDVR()) openShare("开始巡检");  // 会被其他任务关掉网口
        }
        SystemClock.sleep(3000);  // 等待3秒，确保网口开启完毕

        CheckGroup item = cgs.get(group);
        for (int i = 0; i < count; i++) {
            dev.startCheckLine(item);
            SystemClock.sleep(item.points.size() * CHECK_LINE_INTERVAL);
        }
        dev.stopCheckLine();
        if (isWorkHour()) {
            dev.startMove(2, recordPreset, () -> {  // 调用录像预置位
                finishCheckLineTask(channel, dev, taskName, wakeupReason);
            });
        } else {
            finishCheckLineTask(channel, dev, taskName, wakeupReason);
        }
    }

    private void finishCheckLineTask(int channel, Device dev, String taskName, String wakeupReason) {
        if (deviceConfig.toCheck) {
            if (dev.isDVR()) closeShare("结束巡检");
        }
        finishTimeConsumeTask(taskName, dev.isDVR());
        sleepDevice(channel, wakeupReason + "完成");
    }

    // 录像策略调用预置位动作
    private void runMove(int cmd, int channel, int para) {
        Device dev = channels.get(String.valueOf(channel));
        if (dev == null) return;
        /////
        boolean ret1, ret2 = false, ret3, ret4;
        if (dev.isDVR() && !dev.isUSB()) {
            for (int i = 0; i < 3; i++) {
                doTimeConsumingTask("设置图片参数、OSD、视频参数、录像策略", dev.isDVR());
                ret1 = dev.setPhotoParam(settings.photoConfig.get(String.valueOf(channel)));
                if (dev.type == DEVICE_DVR_AIPU) {
                    if (dev.isOldCamera) {
                        ret2 = dev.setOSD(settings.osds.get(String.valueOf(channel)), true);
                    } else {
                        ret2 = dev.setOSD(settings.osds.get(String.valueOf(channel)), false);
                    }
                } else if (dev.type == DEVICE_DVR_HUANYU) {
                    ret2 = dev.updateStatusText(settings.osds.get(String.valueOf(channel)), getStatusText(), false);
                }
                ret3 = dev.setCodec(settings.videoCodecs.get(String.valueOf(channel) + ":0"));
                ret4 = dev.setRecordTimes(getDeviceRecordingPolicyForCurrentMode());
                if (ret1 && ret2 && ret3 && ret4) {
                    break;
                }
            }
        }
        /////

        if (deviceConfig.toCheck) {
            if (dev.isDVR()) openShare("调用录像预置位");
        }
        doTimeConsumingTask("调用录像预置位", dev.isDVR());
        SystemClock.sleep(3000);  // 等待3秒，确保网口开启完毕

        dev.startMove(cmd, para, () -> {
            if (deviceConfig.toCheck) {
                if (dev.isDVR()) closeShare("调用录像预置位");
            }
            finishTimeConsumeTask("设置录像时间列表与调用录像预置位", dev.isDVR());
        });
        recordPreset = para;
    }

    // 录像策略调用巡航动作
    private void runCruise(int channel, int para) {
        Device dev = channels.get(String.valueOf(channel));
        if (dev == null) return;
        /////
        boolean ret1, ret2 = false, ret3, ret4;
        if (dev.isDVR() && !dev.isUSB()) {
            for (int i = 0; i < 3; i++) {
                doTimeConsumingTask("设置图片参数、OSD、视频参数、录像策略", dev.isDVR());
                ret1 = dev.setPhotoParam(settings.photoConfig.get(String.valueOf(channel)));
                if (dev.type == DEVICE_DVR_AIPU) {
                    if (dev.isOldCamera) {
                        ret2 = dev.setOSD(settings.osds.get(String.valueOf(channel)), true);
                    } else {
                        ret2 = dev.setOSD(settings.osds.get(String.valueOf(channel)), false);
                    }
                } else if (dev.type == DEVICE_DVR_HUANYU) {
                    ret2 = dev.updateStatusText(settings.osds.get(String.valueOf(channel)), getStatusText(), false);
                }
                ret3 = dev.setCodec(settings.videoCodecs.get(String.valueOf(channel) + ":0"));
//                ret3 = dev.setCodec(settings.videoCodecs.get(String.valueOf(channel)+ ":0"));

                ret4 = dev.setRecordTimes(getDeviceRecordingPolicyForCurrentMode());
                if (ret1 && ret2 && ret3 && ret4) {
                    break;
                }
            }
        }
        /////

        if (deviceConfig.toCheck) {
            if (dev.isDVR()) openShare("开始录像巡航");
        }
        doTimeConsumingTask("开始录像巡航", dev.isDVR());
        SystemClock.sleep(3000);  // 等待3秒，确保网口开启完毕

        dev.startCruise(para);
        List<CruiseGroup> cruiseSettings = settings.cruiseSettings.get(String.valueOf(channel));
        Settings.CruiseGroup cg = null;
        for (int i = 0; i < cruiseSettings.size(); i++) {
            if (cruiseSettings.get(i).group == para) {
                cg = cruiseSettings.get(i);
                break;
            }
        }
        if (cg == null) return;
        for (int i = 0; i < cg.cruises.size(); i++) {
            Settings.Cruise cruise = cg.cruises.get(i);
            SystemClock.sleep(Math.max(PTZ_PRESET_MOVE_TIME + 5 * 1000, cruise.duration * 1000));  // 移动云台需要等待20s到达预置位，这里延长5s，保证线程时间够
        }
        dev.stopCruise();
        if (isWorkHour()) {
            dev.startMove(2, recordPreset, () -> {  // 调用录像预置位
                if (deviceConfig.toCheck) {
                    if (dev.isDVR()) closeShare("结束录像巡航");
                }
                finishTimeConsumeTask("设置录像时间列表与结束录像巡航", dev.isDVR());
            });
        }
    }

    // 定时巡检动作
    private void doCheckLineAction(Intent intent) {
        int channel = intent.getIntExtra("channel", 1);
        int index = intent.getIntExtra("index", 0);
        List<CheckScheduleItem> items = settings.checkSchedule.get(String.valueOf(channel));
        if (items == null) return;

        if (index >= items.size() || index < 0) return;

        // 当前index对应任务
        final CheckScheduleItem item = items.get(index);
        utilsHandler.post(() -> {
            ///
            runValidBusiness(() -> {
                runCheckLine(channel, item.group - 1, item.count);
            });
            ///
        });
        index++;

        // 闹钟回调后，为了防止闹钟延迟回调，需要把从index到当前时间的任务都运行掉！
        Time now = new Time();
        now.setToNow();
        while (index < items.size()) {
            final CheckScheduleItem next = items.get(index);
            if (next == null || next.hour > now.hour || next.minute > now.minute) break;

            utilsHandler.post(() -> {
                ///
                runValidBusiness(() -> {
                    runCheckLine(channel, next.group - 1, next.count);
                });
                ///
            });
            index++;
        }
        initCheckLineTask(channel, index);
    }

    // 定时拍照动作
//    private void doPhotoAction(Intent intent) {
//        int index = intent.getIntExtra("index", 0);
//        if (index >= settings.photoTimeTable.size() || index < 0) {
//            return;
//        }
//
//        // 当前index对应任务
//        final PhotoTimeItem item = settings.photoTimeTable.get(index);
//        utilsHandler.post(() -> {
//            Log.e(Log.TAG, "进行定时拍照");
//            takePhoto(item.channel, item.preset & 0xFF, false, null, true);
//            SystemClock.sleep(5000);
//        });
//        index++;
//
//        initPhotoTask(index);
//    }

    private void doPhotoAction(Intent intent) {
        int index = intent.getIntExtra("index", -1);
        if (index < 0 || index >= settings.photoTimeTable.size()) {
            return;
        }

        final PhotoTimeItem item = settings.photoTimeTable.get(index);
        if (item == null) return;

        utilsHandler.post(() -> {
            String scheduledTime = String.format("%02d:%02d:%02d", item.hour, item.min, item.sec);
            Log.e(Log.TAG, "------定时拍照 - 时间:" + scheduledTime
                    + " 通道:" + item.channel
                    + " 预置位:" + (item.preset & 0xFF)+"------");
            ///
            runValidBusiness(() -> {
                takePhoto(item.channel, item.preset & 0xFF, false, null, true, item.channel == 1 || item.channel == 2);
            });
            ///
            SystemClock.sleep(5000);
        });
    }


    public void coldReboot() {
        if (deviceConfig.chargeControl != 1 || deviceConfig.chargeControl != 7) /////
            return;

        serialHandler.post(() -> {
            cpuLock();
            saveTrafficData();
            Log.d(Log.TAG, "冷启动系统开始");
            for (int i = 0; i < 5; i++) {
                boolean ret = RS485Impl.Instance().coldReboot();
                Log.d(Log.TAG, "冷启动系统响应：" + ret);
                if (ret) {
                    RS485Impl.Instance().gpioUnInit();
                    break;
                }
            }
            rebootSystem("冷启动不起作用，进行热启动替代");
        });
    }

    public void rebootSystem(final String reason) {
        saveTrafficData();

        /////
        new Thread(() -> {
            Log.w(Log.TAG, "软重启系统：" + reason);
            SystemClock.sleep(2000);
            powerManager.reboot(reason);
            su("reboot");  // 防止 reboot 失败，使用 su 保证重启
            exec("su reboot");
        }).start();
    }

    @Override
    public synchronized void cpuLock() {
        if (null != wakeLock && !wakeLock.isHeld()) {
            try {
                wakeLock.acquire();
                if (DEBUG) {
                    Log.i(Log.TAG, "获取cpu锁成功, " + wakeLock.toString());
                }
            } catch (Exception e) {

            }
        }
    }
    /////

    @Override
    public void cpuUnlock() {
        try {
            //if (null != wakeLock && wakeLock.isHeld())
            //wakeLock.release(); // 这里注释掉只为云台球机过检测试保险
        } catch (Exception e) {

        }
    }

    @Override
    public synchronized void tryCpuUnlock() {
        if (null != wakeLock && wakeLock.isHeld() && TaskManager.empty()) {
            try {
                wakeLock.release(); // 这里注释掉只为云台球机过检测试保险
                if (DEBUG) {
                    Log.i(Log.TAG, "释放cpu锁成功, " + wakeLock.toString());
                }
            } catch (Exception e) {
            }
        }
    }
    /////

    @Override
    public void wakeup() {
        doWakeup("服务器唤醒所有云台与红外", 23);
    }

    @Override
    public void changeServer(String server, int port) {
        deviceConfig.server = server;
        deviceConfig.port = port;
        settings.sim = spgProtocol.sim;
        saveDeviceConfig();
        saveSettings(settings, SETTING_FILE);
        //restartApplication(this, 1000);
        restartApplication(this, 1);  // 之前是1000秒，2025-02-18改为1秒后重启apk
    }

    private void saveDeviceConfig() {
        try {
            String s = JSON.toJSONString(deviceConfig, true);
            stringToFile(CONFIG_FILE, s);
        } catch (Exception e) {
            Log.e(Log.TAG, "保存设备核心信息失败：" + e.getMessage());
        }
    }

    @Override
    public boolean setOnlineConfig(OnlineCfg cfg, String newPasscode) {
        settings.onlineCfg = cfg;
        settings.passcode = newPasscode;
        if (heartBeatIntent != null) heartBeatIntent.cancel();
        initHeartBeat();

        if (sampleIntent != null) sampleIntent.cancel();
        initSample();

        updateOnline("主站下发参数配置"); /////

        if (rebootIntent1 != null) rebootIntent1.cancel();
        if (rebootIntent2 != null) rebootIntent2.cancel();

        initReboot(-1);

        return saveSettings(settings, SETTING_FILE);
    }

    @Override
    public PhotoTimeItem[] getPhotoTimeTable(int channel) {

        List<PhotoTimeItem> table = new ArrayList<>();
        for (PhotoTimeItem item : settings.photoTimeTable)
            if (item.channel == channel) table.add(item);

        PhotoTimeItem[] ret = new PhotoTimeItem[table.size()];
        for (int i = 0; i < table.size(); i++)
            ret[i] = table.get(i);
        return ret;
    }

    @Override
    public boolean setPhotoTimeTable(int channel, PhotoTimeItem[] table) {
//        if (!deviceConfig.photoCheck) {
        cancelPhotoAlarmsForChannel(channel);
        Iterator<PhotoTimeItem> iterator = settings.photoTimeTable.iterator();
        while (iterator.hasNext()) {
            PhotoTimeItem item = iterator.next();
            if (item.channel == channel) iterator.remove();
        }

        for (PhotoTimeItem item : table)
            settings.photoTimeTable.add(item);

        if (is6735) {
            // Android 5.1, minSDKVersion 不支持 List.sort……，自己写冒泡排序
            for (int i = 0; i < settings.photoTimeTable.size(); i++)
                for (int j = 0; j < settings.photoTimeTable.size(); j++) {
                    PhotoTimeItem item1 = settings.photoTimeTable.get(i);
                    PhotoTimeItem item2 = settings.photoTimeTable.get(j);
                    int v1 = (item1.hour << 8) | item1.min;
                    int v2 = (item2.hour << 8) | item2.min;
                    if (v1 < v2) {
                        settings.photoTimeTable.set(j, item1);
                        settings.photoTimeTable.set(i, item2);
                    }
                }
        } else {
            this.settings.photoTimeTable.sort(new Comparator<PhotoTimeItem>() {
                @Override
                public int compare(PhotoTimeItem o1, PhotoTimeItem o2) {
                    return (o1.hour * 60 + o1.min) - (o2.hour * 60 + o2.min);
                }
            });
        }

        boolean b = saveSettings(settings, SETTING_FILE);
        if (b) refreshPhotoSchedule();
        return b;
    }

    @Override
    public Parameters getConfigurationParameters() {
        Parameters parameters = new Parameters();
        parameters.onlineCfg = settings.onlineCfg;
        /////
        if (channels.size() >= 3) {
            parameters.ch1 = settings.photoConfig.get("1");
            parameters.ch2 = settings.photoConfig.get("2");
            parameters.ch3 = settings.photoConfig.get("3");
        } else if (channels.size() >= 2) {
            parameters.ch1 = settings.photoConfig.get("1");
            parameters.ch2 = settings.photoConfig.get("2");
        } else if (channels.size() >= 1) {
            parameters.ch1 = settings.photoConfig.get("1");
        }
        /////
        return parameters;
    }

    @Override
    public List<VideoTimeItem> getVideoTimeTable(int channel, int streamType) {
        Device dev = channels.get(String.valueOf(channel));
        List<VideoTimeItem> items = new ArrayList<>();
        if (dev == null || !dev.isDVR()) {
            return items;
        }

        return settings.videoTimeTable;
    }

    @Override
    public void setFeatures(Features features) {
        saveSettings(settings, SETTING_FILE);
    }

    /////
    // 开启云台与红外
    public void doWakeup(String reason, int load) {

        if (isSleepMode()){
            return;
        }


        if (load == 2 || load == 23) {
            for (Device dev : channels.values()) {
                if (dev.isDVR() && !dev.isUSB()) {

                    Log.e(Log.TAG, "开启云台：" + reason);
                    gimbalPowerHandler.post(() -> {
                        powerControlNVR(true, 2);
                    });
                    SystemClock.sleep(1000);
                }
            }
        }
        if (load == 3 || load == 23) {
            for (Device dev : channels.values()) {
                if (dev.isUSB()) {
                    Log.e(Log.TAG, "开启红外：" + reason);
                    gimbalPowerHandler.post(() -> {
                        powerControlNVR(true, 3);
                    });
                    SystemClock.sleep(1000);
                }
            }
        }
    }


    private void doSleep(String reason, int load) {

        // 唤醒模式下不能直接按服务器/定时器要求下电，需要先过三类保护：
        // 1. 当前是否还有直播、回放、拍照、短视频等业务正在执行；
        // 2. 是否还在业务结束后的保活窗口内；
        // 3. 是否即将进入定时拍照的预热窗口。
        // 这里先检查“业务忙”和“即将拍照”，保活窗口在下面单独判断，便于日志区分。
        if (shouldDelayWakeupShutdown(load, reason)) {
            return;
        }

        if (currentMode == MODE_WAKEUP && isWakeupKeepAliveMode == true){
            Log.e(Log.TAG,"唤醒模式下，存在拍照/短视频/拉流/回放保活任务，测试期间2分钟内不对云台和红外下电");
            return;
        }

        // 检测窗口
        boolean incomingDVR3 = false;
        boolean incomingUSB10 = false;
        for (Device dev : channels.values()) {
            if (dev.isUSB()) {
                incomingUSB10 = hasUpcomingPhotoTaskForChannel(dev.id, 10);  // 10分钟内有红外拍照任务
            }
            if (dev.isDVR()) {
                boolean incomingCh3 = hasUpcomingPhotoTaskForChannel(dev.id, 3);  // 3分钟内有云台拍照任务
                if (incomingCh3) {
                    incomingDVR3 = true;
                }
            }
        }
        if (load == 2) {
            // 云台关闭前先判断其他通道的三分钟窗口
            if (!incomingDVR3) {
                // 关闭云台
                Log.e(Log.TAG, "关闭云台：" + reason);
                closeDevicesForPowerOff(2);
                powerControlNVR(false, 2);
            } else {
                Log.e(Log.TAG, "跳过关闭云台，3分钟内存在可见光机芯拍照任务");
            }
        } else if (load == 3) {
            // 红外关断前先判断红外的十分钟窗口
            if (!incomingUSB10) {
                // 关闭红外
                Log.e(Log.TAG, "关闭红外：" + reason);
                closeDevicesForPowerOff(3);
                powerControlNVR(false, 3);
            } else {
                Log.e(Log.TAG, "跳过关闭红外，10分钟内存在红外拍照任务：" + reason);
            }
        } else if (load == 23) {
            // 云台关闭前先判断其他通道的三分钟窗口
            if (!incomingDVR3) {
                // 关闭云台
                Log.e(Log.TAG, "关闭云台：" + reason);
                closeDevicesForPowerOff(2);
                powerControlNVR(false, 2);
            } else {
                Log.e(Log.TAG, "跳过关闭云台，3分钟内存在可见光机芯拍照任务");
            }
            // 红外关断前先判断红外的十分钟窗口
            if (!incomingUSB10) {
                // 关闭红外
                Log.e(Log.TAG, "关闭红外：" + reason);
                closeDevicesForPowerOff(3);
                powerControlNVR(false, 3);
            } else {
                Log.e(Log.TAG, "跳过关闭红外，10分钟内存在红外拍照任务：" + reason);
            }
        }

    }


    /////
    // 唤醒模式下统一拦截下电：
    // - 设备忙：不能下电，并重置保活，等下一轮再判断。
    // - 可见光拍照前 3 分钟：不能关闭云台负载。
    // - 红外拍照前 10 分钟：不能关闭云台和红外负载。
    // load=2 表示云台，load=3 表示红外，load=23 表示两者都要检查。
    private boolean shouldDelayWakeupShutdown(int load, String reason) {
        if (currentMode != MODE_WAKEUP) {
            return false;
        }

        if (isAnyDeviceBusy()) {
            Log.e(Log.TAG, "Wakeup shutdown delayed, device is busy: " + reason);
            // 业务还在执行时继续保活，等下一轮到期再判断。
            resetWakeupTimer();
            return true;
        }

        boolean incomingRgbPhoto = hasUpcomingPhotoTask(false, RGB_PHOTO_PREHEAT_MINUTES);
        boolean incomingIrPhoto = hasUpcomingPhotoTask(true, IR_PHOTO_PREHEAT_MINUTES);
        boolean shouldKeepLoad2 = load == 2 || load == 23;
        boolean shouldKeepLoad3 = load == 3 || load == 23;

        if ((shouldKeepLoad2 && incomingRgbPhoto) || (shouldKeepLoad3 && incomingIrPhoto)) {
            Log.e(Log.TAG, "Wakeup shutdown delayed, upcoming photo task exists: " + reason);
            // 即将拍照时继续保活，避免刚下电又马上预热上电。
            resetWakeupTimer();
            return true;
        }

        return false;
    }

    private boolean hasUpcomingPhotoTask(boolean usb, int minutesWindow) {
        if (settings == null || settings.photoTimeTable == null || settings.photoTimeTable.isEmpty()) {
            return false;
        }

        for (PhotoTimeItem item : settings.photoTimeTable) {
            if (item == null) continue;
            Device dev = channels.get(String.valueOf(item.channel));
            if (dev == null || !dev.isDVR() || dev.isUSB() != usb) continue;
            // 根据设备类型区分可见光/红外，只检查对应负载的拍照窗口。
            if (hasUpcomingPhotoTaskForChannel(item.channel, minutesWindow)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasUpcomingPhotoTaskForChannel(int channel, int minutesWindow) {
        if (settings == null || settings.photoTimeTable == null || settings.photoTimeTable.isEmpty()) {
            return false;
        }
        // 拍照计划是“每天固定时间”语义：如果今天的时间点已过，就按明天同一时间计算。
        // 这样 23:59 附近也能正确判断第二天凌晨的预热窗口。
        long now = System.currentTimeMillis();
        java.util.Calendar cal = java.util.Calendar.getInstance();
        for (PhotoTimeItem item : settings.photoTimeTable) {
            // 只关心匹配通道
            if (item == null || item.channel != channel) continue;
            cal.set(java.util.Calendar.HOUR_OF_DAY, item.hour);
            cal.set(java.util.Calendar.MINUTE, item.min);
            cal.set(java.util.Calendar.SECOND, item.sec);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long when = cal.getTimeInMillis();
            // 如果今天的时间点已过，则取下一天的同一时刻
            if (when < now) {
                when += java.util.concurrent.TimeUnit.DAYS.toMillis(1);
            }
            long diff = when - now;
            if (diff >= 0 && diff <= java.util.concurrent.TimeUnit.MINUTES.toMillis(minutesWindow)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUpcomingChannel1Or2PhotoTask(int minutesWindow) {
        return hasUpcomingPhotoTaskForChannel(1, minutesWindow)
                || hasUpcomingPhotoTaskForChannel(2, minutesWindow);
    }

    private boolean shouldUseWakeupPhotoFollowup(int channel) {
        return currentMode == MODE_WAKEUP && (channel == 1 || channel == 2);
    }

    private boolean handleWakeupPhotoFinished(Device dev, int channel, String file, String reason) {
        Integer trackedChannel = file == null ? null : wakeupPhotoFollowupFiles.remove(file);
        if (currentMode != MODE_WAKEUP || trackedChannel == null) {
            return false;
        }

        utilsHandler.removeCallbacks(mResetWakeupFlagTask);
        isWakeupKeepAliveMode = false;

        boolean isIrPhoto = dev != null && dev.isUSB();
        int load = isIrPhoto ? 23 : 2;
        int followupMinutes = isIrPhoto ? WAKEUP_IR_PHOTO_FOLLOWUP_MINUTES : WAKEUP_RGB_PHOTO_FOLLOWUP_MINUTES;
        boolean hasUpcomingPhoto = hasUpcomingChannel1Or2PhotoTask(followupMinutes);
        if (hasUpcomingPhoto) {
            Log.i(Log.TAG, "唤醒模式拍照完成，后续" + followupMinutes + "分钟内存在通道一或通道二定时拍照任务，保持负载上电：" + reason + ", channel=" + channel);
            doWakeup("拍照后等待后续定时拍照：" + reason, load);
            resetWakeupTimer(followupMinutes * PERIOD_MINUTE, "拍照后等待后续定时拍照任务");
        } else {
            Log.i(Log.TAG, "唤醒模式拍照完成，后续" + followupMinutes + "分钟内没有通道一或通道二定时拍照任务，立即下电：" + reason + ", channel=" + channel);
            isWakeupVideoPlaybackMode = false;
            doSleep("拍照后无后续定时拍照：" + reason, load);
        }
        return true;
    }

    private boolean handleWakeupVideoFinished(Device dev, int channel, String reason) {
        if (currentMode != MODE_WAKEUP || dev == null || !dev.isDVR() || (channel != 1 && channel != 2)) {
            return false;
        }

        // 唤醒模式下可见光/红外短视频结束后不再走普通 15 分钟保活，
        // 而是复用拍照后的衔接规则：可见光看后续 10 分钟，红外看后续 15 分钟。
        utilsHandler.removeCallbacks(mResetWakeupFlagTask);
        isWakeupKeepAliveMode = false;

        boolean isIrVideo = dev.isUSB();
        int load = isIrVideo ? 23 : 2;
        int followupMinutes = isIrVideo ? WAKEUP_IR_PHOTO_FOLLOWUP_MINUTES : WAKEUP_RGB_PHOTO_FOLLOWUP_MINUTES;
        boolean hasUpcomingPhoto = hasUpcomingChannel1Or2PhotoTask(followupMinutes);
        if (hasUpcomingPhoto) {
            Log.i(Log.TAG, "唤醒模式短视频完成，后续" + followupMinutes + "分钟内存在通道一或通道二定时拍照任务，保持负载上电：" + reason + ", channel=" + channel);
            doWakeup("短视频后等待后续定时拍照：" + reason, load);
            resetWakeupTimer(followupMinutes * PERIOD_MINUTE, "短视频后等待后续定时拍照任务");
        } else {
            Log.i(Log.TAG, "唤醒模式短视频完成，后续" + followupMinutes + "分钟内没有通道一或通道二定时拍照任务，立即下电：" + reason + ", channel=" + channel);
            isWakeupVideoPlaybackMode = false;
            doSleep("短视频后无后续定时拍照：" + reason, load);
        }
        return true;
    }


    @Override
    public void ptzControl(int channelNum, final int order, final int para) {
        final Device dev = channels.get(String.valueOf(channelNum));
//        WifiAP wifiAP = WifiAP.getInstance(this, deviceConfig.deviceId, "11121314"); /////
        WifiAP wifiAP = WifiAP.getInstance(this, ssid, "11121314"); /////       ？？？？？
        if (dev == null) return;

        if (deviceConfig.toCheck) {
            if (dev.isDVR()) openShare("DVR云台控制");
        }
        if (order == 1)
            doWakeup("服务器开机指令，开启所有云台与红外", 23);
        else if (order == 15) {
            ///
            runValidBusiness(() -> {
                runCruise(channelNum, para);
            });
            ///
        } else if (order == 16) {
            dev.stopCruise();
        } else if (order == 31) {  // 开始巡检，表示设置完成，可以复位了
            ///
            runValidBusiness(() -> {
                runCheckLine(channelNum, para - 1, 1);
            });
            ///
        } else if (order == 32) {
            dev.stopCheckLine();
        } else if (order == 33) { /////
            dev.startCruise(para);  // 开始巡航 /////
            dev.stopCruise();  // 结束巡航 /////
        } else if (order == 10) {
            doSleep("服务器关机指令，关闭云台与红外", 23);
            /////
        } else if (order == 17 && para == 2) {
            // 开启Wi-Fi
            wifiAP.enable();
            deviceConfig.wifi = true;
            saveDeviceConfig();
//            dev.move(order, para);
            Log.i(Log.TAG, "辅助开关2打开，开启Wi-Fi");
        } else if (order == 18 && para == 2) {
            // 关闭Wi-Fi
            wifiAP.disable();
            deviceConfig.wifi = false;
            saveDeviceConfig();
//            dev.move(order, para);
            Log.i(Log.TAG, "辅助开关2关闭，关闭Wi-Fi");
            /////
        } else if (order == 17 && para == 3) {
            dev.osd.size += 5;
            saveSettings(settings, SETTING_FILE);
            ////////
            if (dev.type == DEVICE_DVR_AIPU) {
                if (dev.isOldCamera) {
                    dev.setOSD(dev.osd, false);
                    dev.updateStatusText(getStatusText(), false);
                } else {
                    dev.setOSD(dev.osd, true);
                    dev.updateStatusText(getStatusText(), true);
                }
            } else {
                dev.updateStatusText(dev.osd, getStatusText(), false);
            }
            ////////
            dev.move(order, para);
        } else if (order == 18 && para == 3) {
            dev.osd.size -= 5;
            saveSettings(settings, SETTING_FILE);
            ////////
            if (dev.type == DEVICE_DVR_AIPU) {
                if (dev.isOldCamera) {
                    dev.setOSD(dev.osd, false);
                    dev.updateStatusText(getStatusText(), false);
                } else {
                    dev.setOSD(dev.osd, true);
                    dev.updateStatusText(getStatusText(), true);
                }
            } else {
                dev.updateStatusText(dev.osd, getStatusText(), false);
            }
            ////////
            dev.move(order, para);
            /////
        } else if (order == 17 && para == 4) {
            // 开启图像拼接
            iRSetting.imageStitch = 1;
            saveSettings(iRSetting, IR_SETTING_FILE);
            dev.move(order, para);
            Log.i(Log.TAG, "辅助开关4打开，开启图像拼接");
        } else if (order == 18 && para == 4) {
            // 关闭图像拼接
            iRSetting.imageStitch = 0;
            saveSettings(iRSetting, IR_SETTING_FILE);
            dev.move(order, para);
            Log.i(Log.TAG, "辅助开关4关闭，关闭图像拼接");
            /////
            ///////
        } else if (order == 17 && para == 5) {
            aux5Switching = true;
            sleepMode = false;
            if (deviceConfig.chargeControl >= 6) {
                sleepModeIr = false;
            }
            // 1. 各接口上电
            ////////
            if (camera != null) {
                for (int i = 0; i < 3; i++) {
                    boolean errcode = RS485Impl.Instance().gpioOpenMIPI();
                    Log.i(Log.TAG, String.format("MIPI上电%s", errcode ? "成功" : "失败"));
                    if (errcode) {
                        break;
                    }
                }
            }
            for (int i = 0; i < 3; i++) {
                boolean errcode = RS485Impl.Instance().gpioOpenRS485();
                Log.i(Log.TAG, String.format("RS485上电%s", errcode ? "成功" : "失败"));
                if (errcode) {
                    break;
                }
            }
            for (int i = 0; i < 3; i++) {
                boolean errcode = RS485Impl.Instance().gpioOpenGPS();
                Log.i(Log.TAG, String.format("BDS上电%s", errcode ? "成功" : "失败"));
                if (errcode) {
                    break;
                }
            }

            if (!usbPowerState) {
                for (int i = 0; i < 3; i++) {
                    boolean errcode = RS485Impl.Instance().gpioOpenUSB();
                    Log.i(Log.TAG, String.format("USB上电%s", errcode ? "成功" : "失败"));
                    if (errcode) {
                        usbPowerState = true;
                        writeUsbPowerState(true);
                        break;
                    }
                }
            }
            for (int i = 0; i < 3; i++) {
                boolean errcode = RS485Impl.Instance().gpioOpenRJ45();
                Log.i(Log.TAG, String.format("RJ45上电%s", errcode ? "成功" : "失败"));
                if (errcode) {
                    isRJ45Powered = true; // 更新状态位
                    break;
                }
            }
            ////////
            // 2. 云台与红外上电
            doWakeup("辅助开关5打开，进入正常模式打开云台与红外", 23);
            // 3. 打开网口
            openShare("辅助开关5打开，进入正常模式打开网口");
            ///
            // 4. 恢复系统中除心跳外的所有闹钟事件
            initAlarmTasksAsync("init core");  // 初始化所有的闹钟，当时间到的时候就执行，广播消息接收器中对应的函数
            ///
            // 5. 打开Wi-Fi
            Log.i(Log.TAG, "打开Wi-Fi");

            wifiInit();

            // 6. 开启BDS ////////
            initBDS(); ////////

            Log.e(Log.TAG, "初始化DBS");
            aux5Switching = false;
            aux5Open = true;
        } else if (order == 18 && para == 5) {
            sleepMode = true;
            if (deviceConfig.chargeControl >= 6) {
                sleepModeIr = true;
            }
            // 1. 关闭BDS ////////
            stopBDS(); ////////

            // 2. 关闭Wi-Fi
            Log.i(Log.TAG, "关闭Wi-Fi");
            wifiAP.disable();

            ///
            // 3. 取消系统中除心跳外的所有闹钟事件
            Log.i(Log.TAG, "取消系统中除心跳外的所有闹钟事件");
            if (photoIntent != null) cancelAllPhotoAlarms();
            if (videoIntent != null) alarmManager.cancel(videoIntent);
            if (videoIntent != null) alarmManager.cancel(recordIntent);
            cancelAllCheckLineAlarms();
            if (powerOnIntent != null) alarmManager.cancel(powerOnIntent);
            if (powerRgbOnIntent != null) alarmManager.cancel(powerRgbOnIntent);
            if (powerIrOnIntent != null) alarmManager.cancel(powerIrOnIntent);
            if (powerOffIntent != null) alarmManager.cancel(powerOffIntent);
            if (sampleIntent != null) alarmManager.cancel(sampleIntent);
            if (utilizationRateIntent != null) alarmManager.cancel(utilizationRateIntent);
            if (rebootIntent1 != null) alarmManager.cancel(rebootIntent1);
            if (rebootIntent2 != null) alarmManager.cancel(rebootIntent2);
            ///

            // 4. 关闭网口
            Log.i(Log.TAG, "进入休眠模式关闭网口");
            closeShare("辅助开关5关闭，进入休眠模式关闭网口");
            // 5. 云台与红外下电
            doSleep("辅助开关5关闭，进入休眠模式关闭云台与红外", 23);
            // 6. 各接口下电
            ////////
            for (int i = 0; i < 3; i++) {
                boolean errcode = RS485Impl.Instance().gpioCloseRJ45();
                Log.i(Log.TAG, String.format("RJ45下电%s", errcode ? "成功" : "失败"));
                if (errcode) {
                    isRJ45Powered = false;
                    break;
                }
            }
            for (int i = 0; i < 3; i++) {
                boolean errcode = RS485Impl.Instance().gpioCloseUSB();
                Log.i(Log.TAG, String.format("USB下电%s", errcode ? "成功" : "失败"));
                if (errcode) {
                    usbPowerState = false;
                    writeUsbPowerState(false);
                    break;
                }
            }
            for (int i = 0; i < 3; i++) {
                boolean errcode = RS485Impl.Instance().gpioCloseGPS();
                Log.i(Log.TAG, String.format("BDS下电%s", errcode ? "成功" : "失败"));
                if (errcode) {
                    break;
                }
            }
            for (int i = 0; i < 3; i++) {
                boolean errcode = RS485Impl.Instance().gpioCloseRS485();
                Log.i(Log.TAG, String.format("RS485下电%s", errcode ? "成功" : "失败"));
                if (errcode) {
                    break;
                }
            }
            if (camera == null) {
                for (int i = 0; i < 3; i++) {
                    boolean errcode = RS485Impl.Instance().gpioCloseMIPI();
                    Log.i(Log.TAG, String.format("MIPI下电%s", errcode ? "成功" : "失败"));
                    if (errcode) {
                        break;
                    }
                }
            }
            aux5Switching = false;
            aux5Open = false;
            ////////
            ///////
        } else if (order == 17 && para == 6) {
            // 开启双光融合
            iRSetting.imageFusion = 1;
            saveSettings(iRSetting, IR_SETTING_FILE);
            dev.move(order, para);
            Log.i(Log.TAG, "辅助开关6打开，开启双光融合");
        } else if (order == 18 && para == 6) {
            // 关闭双光融合
            iRSetting.imageFusion = 0;
            saveSettings(iRSetting, IR_SETTING_FILE);
            dev.move(order, para);
            Log.i(Log.TAG, "辅助开关6关闭，关闭双光融合");
//        } else if (order == 17 && para == 7) {
//            // 开启拍照策略自检测
//            deviceConfig.photoCheck = true;
//            saveDeviceConfig();
//            dev.move(order, para);
//            Log.i(Log.TAG, "辅助开关7打开，开启拍照策略自检测");
//        } else if (order == 18 && para == 7) {
//            // 关闭拍照策略自检测
//            deviceConfig.photoCheck = false;
//            deviceConfig.photoCheck = false;
//            saveDeviceConfig();
//            dev.move(order, para);
//            Log.i(Log.TAG, "辅助开关7关闭，关闭拍照策略自检测");
            /////
        } else {
            dev.move(order, para);
        }

        updateOnline("摄像机调整");
    }

    @RequiresPermission("android.permission.WRITE_SETTINGS")
    private void setSystemAutoTime(boolean auto) {
//        android.provider.Settings.Global.getString(this.getContentResolver(), android.provider.Settings.Global.AUTO_TIME);
        // 设置系统时间同步功能
        android.provider.Settings.Global.putString(this.getContentResolver(), android.provider.Settings.Global.AUTO_TIME, auto ? "1" : "0");
        android.provider.Settings.Global.putString(this.getContentResolver(), android.provider.Settings.Global.AUTO_TIME_ZONE, auto ? "1：" : "0");
    }



    @Override
    public boolean setClock(int dwYear, int dwMonth, int dwDay, int dwHour, int dwMinute, int dwSecond) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, dwHour);
        c.set(Calendar.MINUTE, dwMinute);
        c.set(Calendar.SECOND, dwSecond);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.YEAR, dwYear);
        c.set(Calendar.MONTH, dwMonth - 1);
        c.set(Calendar.DAY_OF_MONTH, dwDay);

        if (Math.abs(c.getTimeInMillis() - System.currentTimeMillis()) > 5 * 1000) { /////
            /////
            cancelAllAlarms();
            alarmManager.setTime(c.getTimeInMillis());
            initAlarmTasksAsync("set clock");
            powerClockSynced.set(false);  // 供电板要更新时间
            /////
        } else {
            Log.i(Log.TAG, "小于5秒，忽略校时");
            return false;
        }


        if (powerClockSynced.compareAndSet(false, true)) {
            serialHandler.post(() -> setRtc());
        }

        // 插卡的时候校准一下机芯时间
        setDVRTime();

        showMsg("服务器校时成功");


        // 校时之后，需要跟新一下lastRecvTime的时间，否则时间不准的时候，在更新系统时间后，第一次心跳的时候会产生心跳超时。如果本地时间和服务器时间差5s，就不用进行更新
        Log.e(Log.TAG,"校时后需要更新一下lastRecvTime的时间");
        Log.e(Log.TAG,"更新前lastRecvTime："+formatTimestamp(lastRecvTime));
        lastRecvTime = System.currentTimeMillis();
        Log.e(Log.TAG,"更新后lastRecvTime："+formatTimestamp(lastRecvTime));

        return true;
    }

    @Override
    public String getDvrTime() {
        DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        return df.format(new Date());
    }

    private void initDecoder(int w, int h) {
        if (surfaceView == null) return;

        try {
            if (mediaDecoder != null) {
                mediaDecoder.stop();
                mediaDecoder.release();
                mediaDecoder = null;
            }
            mediaDecoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            rtph264 = new RTPH264(0);
            if (w >= 3840) {
                w = 3840;
                h = 2160;
            }  // ？？？
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h);

            Surface surface = surfaceView.getHolder().getSurface();

            mediaDecoder.configure(mediaFormat, surface, null, 0);
            mediaDecoder.start();
            Log.e(Log.TAG, "创建解码器，分辨率为" + w + "x" + h);
        } catch (Exception e) {
            mediaDecoder = null;
            Log.e(Log.TAG, "创建解码器失败：" + e);
        }
    }
    /////

    private void releaseDecoder() {
        if (mediaDecoder == null) return;
        try {
            mediaDecoder.stop();
            mediaDecoder.release();
        } catch (Exception e) {
            Log.e(Log.TAG, "释放解码器失败：" + e.getMessage());
        } finally {
            mediaDecoder = null;
        }
    }

    private void takeVideo(final int channel, final int stream, final int time, boolean upload) {
        final Device dev = channels.get(String.valueOf(channel));

        if (dev == null || dev.isRecording()) {
            Log.e(Log.TAG, "任务执行失败，跳过");

            synchronized (videoQueue) {
                isVideoTaskRunning = false;
            }

            tryStartNextVideoTask();
            return;
        }

        // if (dev.isLiving()) {
        //     dev.liveStop();
        // }

        markWakeupActivity(dev, "唤醒模式下录制短视频");
        if (currentMode == MODE_WAKEUP && dev.isCamera()) {
            powerOnMipiIfNeeded("唤醒模式下MIPI录制短视频");
        }

        cpuLock();
        String fn = getFileName(channel, stream, EXT_MP4, time);
        TaskManager.add(fn);

        /////
        if (dev.isDVR()) {
            if (dev.isUSB()) {
                wakeupDevice(dev, "红外机芯录制");
            } else {
                wakeupDevice(dev, "可见光机芯录制");
            }
        }
        /////

        Device.onOpenCallback callback = dev.new onOpenCallback() {
            @Override
            public void openSucceed() {

                // sunwu
                if (deviceConfig.toCheck && channel == 2) {
                    revertPowerTuning();
                }

                ////////
                if (dev.type == DEVICE_DVR_AIPU) {
                    dev.setOSD(dev.osd, false);
                } else {
                    dev.updateStatusText(dev.osd, getStatusText(), false);
                    dev.timeSync();
                }
                ////////

                dev.takeVideo(fn, time, stream, upload);

                if (dev.type == DEVICE_DVR_AIPU) {
                    if (dev.isOldCamera) {
                        dev.updateStatusText(getStatusText(), true);
                    } else {
                        dev.updateStatusText(getStatusText(), false);
                    }
                }
                DeviceExceptionManager.openSucceed();
            }

            @Override
            public void openFailed(int errcode) {
                DeviceExceptionManager.openFailed();
                finishTask(fn);
                if (dev.isCamera()) {
                    powerOffMipiIfIdle("MIPI录制短视频打开失败");
                }

                if (deviceConfig.toCheck && channel == 2) {
                    applyPowerTuning();
                }

            }
        };

        if (dev.isDVR()) {
            dev.open(stream, callback, DVR_BOOT_TIME, !isWorkHour(), true, true);  // 这个地方打开成功调用前面的回调函数 ///
        }
        if (dev.isCamera()) {
            dev.open(stream, callback, DVR_BOOT_TIME, false, true, true);  // 这个地方打开成功调用前面的回调函数 ///
        }

//        dev.open(stream, callback, DVR_BOOT_TIME, false);
    }


    private void takeVideo(VideoTimeItem item, boolean upload) {

        final Device dev = channels.get(String.valueOf(item.channel));
        if (dev == null) {
            Log.i(Log.TAG, "录制视频失败，无设备");
            return;
        }
        if (dev.isRecording()) {
            Log.i(Log.TAG, "录制视频失败，正在录制中");
            return;
        }

        markWakeupActivity(dev, "唤醒模式下录制短视频");
        if (currentMode == MODE_WAKEUP && dev.isCamera()) {
            powerOnMipiIfNeeded("唤醒模式下MIPI定时录制短视频");
        }

        cpuLock();
        String fn = getFileName(item.channel, item.stream, EXT_MP4, item.duration);
        TaskManager.add(fn);

        /////
        if (dev.isDVR()) {
            if (dev.isUSB()) {
                wakeupDevice(dev, "红外机芯录制");
            } else {
                wakeupDevice(dev, "可见光机芯录制");
            }
        }
        /////

        Device.onOpenCallback callback = dev.new onOpenCallback() {
            @Override
            public void openSucceed() {

                // sunwu
                if (deviceConfig.toCheck && item.channel == 2) {
                    revertPowerTuning();
                }

                /////
                ////////
                if (dev.type == DEVICE_DVR_AIPU) {
                    dev.setOSD(dev.osd, false);
                    dev.updateStatusText(getStatusText(), false);
                } else {
                    dev.updateStatusText(dev.osd, getStatusText(), false);
                    dev.timeSync();
                }
                ////////
                if (!dev.isCamera()) {
                    if (item.action == 0)           // 0 调用预置位
                        dev.move(2, item.para);
                    else if (item.action == 1)        // 1 调用巡航
                        dev.move(15, item.para);
                    else if (item.action == 2)       // 2 调用巡检
                        runCheckLine(item.channel, item.para - 1, 1);
                }
                /////

                dev.takeVideo(fn, item.duration, item.stream, upload);
                DeviceExceptionManager.openSucceed(); /////
            }

            @Override
            public void openFailed(int errcode) {
                DeviceExceptionManager.openFailed(); /////
                finishTask(fn);
                if (dev.isCamera()) {
                    powerOffMipiIfIdle("MIPI定时录制短视频打开失败");
                }

                /// sunwu
                if (deviceConfig.toCheck && item.channel == 2) {
                    applyPowerTuning();
                }

            }
        };

        /////

        if (dev.isDVR()) {
            dev.open(item.stream, callback, DVR_BOOT_TIME, !isWorkHour(), true, true);  // 这个地方打开成功调用前面的回调函数 ///
        }
        if (dev.isCamera()) {
            dev.open(item.stream, callback, DVR_BOOT_TIME, false, true, true);  // 这个地方打开成功调用前面的回调函数 ///
        }
        /////

//        dev.open(item.stream, callback, DVR_BOOT_TIME, false);

    }

//    @Override
//    public short startShortVideo(final int channel, final int stream, final int time) {
//        final Device dev = channels.get(String.valueOf(channel));
//        if (dev.isRecording()) return 1;   // 当前通道正在拍摄
//
////        if (电量不足) return 3;    // 当前电量不够；
//        if (dev == null) return 3;
//
//        utilsHandler.post(() -> takeVideo(channel, stream, time, true));
////        utilsHandler.post(() -> takeVideo(channel, stream, time, true, 1)); ///////
//        return 0;
//    }



    @Override
    public short startShortVideo(final int channel, final int stream, final int time) {

        if(isSleepMode()){
            Log.e(Log.TAG,"设备处于休眠模式");
            return 2;
        }

        final Device dev = channels.get(String.valueOf(channel));
        if (dev == null) return 3;

        synchronized (videoQueue) {
            videoQueue.offer(new VideoTask(channel, stream, time, true));
        }
        tryStartNextVideoTask();
        return 0;
    }


    private void tryStartNextVideoTask() {
        utilsHandler.post(() -> {
            synchronized (videoQueue) {
                if (isVideoTaskRunning) return;
                VideoTask task = videoQueue.poll();
                if (task == null) return;
                isVideoTaskRunning = true;
                Log.i(Log.TAG, "开始执行视频任务: channel=" + task.channel + ", stream=" + task.stream);
                ///
                runValidBusiness(() -> {
                    takeVideo(task.channel, task.stream, task.time, task.upload);
                });
                ///
            }
        });
    }


    @Override
    public void rebootDevice() {
        // 之前广东电科院测试要求重启的时候必须云台上电自检转动，现在取消
//        doSleep("服务器要求重启系统关闭云台与红外");
        utilsHandler.post(() -> {
            SystemClock.sleep(5000);
            rebootSystem("服务器要求重启");
        });
    }


    private void showPreview(Bitmap bitmap) {
        if (surfaceView == null) return;

        Canvas canvasSV = surfaceView.getHolder().lockCanvas();
        if (canvasSV != null) {
            Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
            Rect dst = new Rect(0, 0, canvasSV.getWidth(), canvasSV.getHeight());
            canvasSV.drawBitmap(bitmap, src, dst, null);
            surfaceView.getHolder().unlockCanvasAndPost(canvasSV);
        }
    }


    /////
    private boolean devBusyRecording() {
        for (Device dev : channels.values()) {
            if (dev.isCamera() && dev.isRecording()) {
                Log.i(Log.TAG, "MIPI摄像头通道" + dev.id + "正在录制");
                return true;
            }
        }
        return false;
    }

    /**
     * 平台不支持打开2个以上摄像头，拍照和拉流需要检查并关闭另一个直播摄像头
     */
    private void closeOtherPlayingCamera(Device dev) {
        if (dev == null) return;
        for (Device camera : channels.values()) {
            if (camera.isCamera() && camera.id != dev.id && (camera.isLiving() || camera.isRecording())) {
                camera.liveStop();
                camera.videoStop();
            }
        }
    }

    private synchronized long beginLiveStartSession(int channel, int streamType, int network, int ssrc, String server, int port) {
        LiveStartSession session = liveStartSessions.get(channel);
        if (session == null) {
            session = new LiveStartSession();
            liveStartSessions.put(channel, session);
        }
        session.starting = true;
        session.delayStop = false;
        session.streamType = streamType;
        session.currentSsrc = ssrc;
        session.network = network;
        session.server = server;
        session.port = port;
        session.startSeq = ++liveStartSeq;
        Log.i(Log.TAG, "直播启动会话开始，channel=" + channel + ", ssrc=" + ssrc + ", startSeq=" + session.startSeq);
        return session.startSeq;
    }

    private synchronized LiveStartTarget getLiveStartTarget(int channel, long startSeq) {
        LiveStartSession session = liveStartSessions.get(channel);
        if (session == null || !session.starting || session.startSeq != startSeq) return null;

        LiveStartTarget target = new LiveStartTarget();
        target.streamType = session.streamType;
        target.ssrc = session.currentSsrc;
        target.network = session.network;
        target.server = session.server;
        target.port = session.port;
        return target;
    }

    private synchronized boolean isSameLiveStartSession(int channel, long startSeq) {
        LiveStartSession session = liveStartSessions.get(channel);
        return session != null && session.startSeq == startSeq;
    }

    private synchronized void clearLiveStartSession(int channel, long startSeq) {
        LiveStartSession session = liveStartSessions.get(channel);
        if (session == null || session.startSeq != startSeq) return;

        session.starting = false;
        session.delayStop = false;
        Log.i(Log.TAG, "直播启动会话清理，channel=" + channel + ", startSeq=" + startSeq);
    }

    private synchronized boolean finishLiveStartSessionAfterOpen(int channel, long startSeq) {
        LiveStartSession session = liveStartSessions.get(channel);
        if (session == null || session.startSeq != startSeq) return false;

        boolean delayStop = session.delayStop;
        session.starting = false;
        session.delayStop = false;
        Log.i(Log.TAG, "直播启动会话收口，channel=" + channel + ", delayStop=" + delayStop + ", startSeq=" + startSeq);
        return delayStop;
    }

    private synchronized boolean markDelayStopIfLiveStarting(int channel, int ssrc) {
        LiveStartSession session = liveStartSessions.get(channel);
        if (session == null || !session.starting) return false;

        if (ssrc != 0 && session.currentSsrc != 0 && ssrc != session.currentSsrc) {
            Log.i(Log.TAG, "直播启动中收到旧SSRC停止，忽略实际停止，channel=" + channel
                    + ", stopSsrc=" + ssrc + ", currentSsrc=" + session.currentSsrc);
            return true;
        }

        session.delayStop = true;
        Log.i(Log.TAG, "直播启动中收到停止，记录延迟停止，channel=" + channel + ", ssrc=" + ssrc);
        return true;
    }

    private short reuseStartingLiveSessionIfNeeded(Device dev, int channel, int streamType, int network, int ssrc, String server, int port) {
        if (dev == null || !dev.isDVR()) return -1;

        boolean endpointChanged;
        synchronized (this) {
            LiveStartSession session = liveStartSessions.get(channel);
            if (session == null || !session.starting) return -1;

            endpointChanged = session.network != network
                    || session.port != port
                    || (session.server == null ? server != null : !session.server.equals(server));

            Log.i(Log.TAG, "直播启动中收到新拉流，复用当前启动流程，channel=" + channel
                    + ", oldSsrc=" + session.currentSsrc + ", newSsrc=" + ssrc);

            session.streamType = streamType;
            session.network = network;
            session.currentSsrc = ssrc;
            session.server = server;
            session.port = port;
            session.delayStop = false;
        }

        if (endpointChanged && dev.streamClient != null) {
            try {
                dev.streamClient.close();
            } catch (Exception e) {
                Log.i(Log.TAG, "关闭旧直播推流连接异常：" + e);
            }
            dev.streamClient = new SocketClient(server, port, network == 0);
            if (!dev.streamClient.open()) {
                Log.i(Log.TAG, "直播启动中切换新推流连接失败，channel=" + channel + ", ssrc=" + ssrc);
                dev.streamClient = null;
                return 4;
            }
        }

        return 0;
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public short startLiveVideo(int channel, final int streamType, int network, final int ssrc, final String server, final int port) {

        final Device dev = channels.get(String.valueOf(channel));
        if (dev == null) return 3;

        if (currentMode == MODE_WAKEUP) {

            isWakeupVideoPlaybackMode = true;

            markWakeupActivity(dev, dev.isUSB() ? "唤醒模式下进行红外直播预览" : "唤醒模式下进行可见光直播预览");
        }


        if (isSleepMode()){
            Log.e(Log.TAG,"设备处于休眠模式");
            return 2;
        }

        short reuseResult = reuseStartingLiveSessionIfNeeded(dev, channel, streamType, network, ssrc, server, port);
        if (reuseResult >= 0) return reuseResult;

//        if (电量不够) return 2;
        if (dev.isLiving()) return 1; /////

        final long liveStartSessionSeq = dev.isDVR()
                ? beginLiveStartSession(channel, streamType, network, ssrc, server, port)
                : 0;

        cpuLock();

//        finishTask(TaskManager.Task.Living.toString()); /////

        TaskManager.add(TaskManager.Task.Living.toString());


        /////
        if (dev.isDVR()) {
            if (dev.isUSB()) {
                wakeupDevice(dev, "红外机芯预览");
            } else {
                wakeupDevice(dev, "可见光机芯预览");
            }
        }
        /////

        if (dev.isLiving() && ssrc != dev.ssrcLive) {
            stopLiveVideo(channel, streamType, dev.ssrcLive);
        }

        /////
        if (dev.isCamera()) {
//            if (dev.isLiving()) return 1;
            // 录制优先
//            if (devBusyRecording()) return 3;
            // 如果另一个摄像头在直播，停掉直播
//            closeOtherPlayingCamera(dev);
            if (currentMode == MODE_WAKEUP) {
                powerOnMipiIfNeeded("唤醒模式下MIPI直播预览");
            }
        }
        /////

        LiveStartTarget liveStartTarget = dev.isDVR() ? getLiveStartTarget(channel, liveStartSessionSeq) : null;
        String liveServer = liveStartTarget != null ? liveStartTarget.server : server;
        int livePort = liveStartTarget != null ? liveStartTarget.port : port;
        int liveNetwork = liveStartTarget != null ? liveStartTarget.network : network;

        if (dev.streamClient == null) {
            dev.streamClient = new SocketClient(liveServer, livePort, liveNetwork == 0);
            if (!dev.streamClient.open()) {
                if (dev.isDVR()) {
                    clearLiveStartSession(channel, liveStartSessionSeq);
                }
                if (dev.isCamera()) {
                    powerOffMipiIfIdle("MIPI直播推流连接失败");
                }
                return 4;
            }
        }

        OSD osd = settings.osds.get(String.valueOf(channel));
        if (osd != null) dev.osd = osd;


        Device.onOpenCallback callback = dev.new onOpenCallback() {
            @Override
            public void openSucceed() {
                LiveStartTarget target = dev.isDVR() ? getLiveStartTarget(channel, liveStartSessionSeq) : null;
                if (dev.isDVR() && target == null) {
                    Log.i(Log.TAG, "忽略旧直播启动成功回调，channel=" + channel + ", startSeq=" + liveStartSessionSeq);
                    return;
                }
                final int liveStreamType = target != null ? target.streamType : streamType;
                final int liveSsrc = target != null ? target.ssrc : ssrc;

                // sunwu
                if (deviceConfig.toCheck && channel == 2) {
                    revertPowerTuning();
                }

                ////////
                if (dev.type == DEVICE_DVR_AIPU) {
                    dev.setOSD(dev.osd, false);
                } else {
                    dev.updateStatusText(dev.osd, getStatusText(), false);
                    dev.timeSync();
                }
                ////////
                boolean ret = dev.liveStart(liveStreamType, liveSsrc);

                showMsg("直播预览：" + (ret ? "成功" : "失败"));
                if (!ret) {
                    if (dev.isDVR()) {
                        clearLiveStartSession(channel, liveStartSessionSeq);
                    }
                    finishTask(TaskManager.Task.Living.toString());
                    if (dev.isCamera()) {
                        powerOffMipiIfIdle("MIPI直播预览启动失败");
                    }
                    return;
                }
                boolean needStopAfterOpen = dev.isDVR()
                        && finishLiveStartSessionAfterOpen(channel, liveStartSessionSeq);
                //Date now = new Date();
                //dev.setTime(now.getYear() + 1900, now.getMonth() + 1, now.getDate(), now.getHours(), now.getMinutes(), now.getSeconds());
                ////////
                if (dev.type == DEVICE_DVR_AIPU) {
                    if (dev.isOldCamera) {
                        dev.updateStatusText(getStatusText(), true);
                    } else {
                        dev.updateStatusText(getStatusText(), false);
                    }
                }
                ////////
                if (needStopAfterOpen) {
                    Log.i(Log.TAG, "直播启动成功后执行延迟停止，channel=" + channel + ", ssrc=" + liveSsrc);
                    stopLiveVideo(channel, liveStreamType, liveSsrc);
                    return;
                }
                updateOnline("直播预览");
            }

            @Override
            public void openFailed(int errcode) {
                if (dev.isDVR() && !isSameLiveStartSession(channel, liveStartSessionSeq)) {
                    Log.i(Log.TAG, "忽略旧直播启动失败回调，channel=" + channel + ", startSeq=" + liveStartSessionSeq);
                    return;
                }
                if (dev.isDVR()) {
                    clearLiveStartSession(channel, liveStartSessionSeq);
                }
                showMsg("直播预览：失败");
                /////
                Device dev = channels.get(String.valueOf(channel));
                if (dev.isDVR()) {
                    if (dev.isUSB()) {
                        sleepDevice(channel, "红外直播预览失败");
                    } else {
                        sleepDevice(channel, "可见光直播预览失败");
                    }
                }
                /////
                finishTask(TaskManager.Task.Living.toString());
                if (dev.isCamera()) {
                    powerOffMipiIfIdle("MIPI直播预览打开失败");
                }
                /////
                if (dev.isDVR()) {
                    if (dev.isUSB()) {
                        Log.i(Log.TAG, "重启红外机芯");
                        utilsHandler.post(() -> {
                            powerControlNVR(false, 3);
                            powerControlNVR(true, 3);
                        });
                    } else {
                        Log.i(Log.TAG, "重启可见光机芯");
                        utilsHandler.post(() -> {
                            powerControlNVR(false, 2);
                            powerControlNVR(true, 2);
                        });
                    }
                }

                // sunwu
                if (deviceConfig.toCheck && channel == 2) {
                    applyPowerTuning();
                }

                /////
            }
        };


        if (dev.isDVR()) {
            dev.open(streamType, callback, DVR_BOOT_TIME, false, true, false);  // 这个地方打开成功调用前面的回调函数 ///
        }

        if (dev.isCamera()) {
            dev.open(streamType, callback, DVR_BOOT_TIME, false, true, false);  // 这个地方打开成功调用前面的回调函数 ///
        }

        return 0;
    }


    private boolean isNetworkReachable(final String host, final int timeout) {
        boolean result = false;
        try {
            InetAddress inetAddress = InetAddress.getByName(host);
            result = inetAddress.isReachable(timeout);
        } catch (Exception e) {
            result = false;
        }
        return result;
    }

    private void wakeupDevice(Device dev, String reason) {

        openShare(reason); // TODO

        Log.e(Log.TAG,"sleeping"+sleeping);

        /////
        if (dev.isDVR()) {
            // 云台是所有 DVR 类业务的基础负载；红外 USB 机芯还需要额外开启红外负载。
            // 这里只做“需要时上电”，保活计时由 markWakeupActivity() 统一负责。
            if (sleeping) {
                // 云台上电
                doWakeup(reason, 2);
            }
            if (deviceConfig.toCheck) {
                openShare(reason);
            }
            if (dev.isUSB() && sleepingIr) {
                if (deviceConfig.chargeControl >= 6) {  // 带开关板的充放电控制器才可以单独对红外上下电
                    // 红外上电
                    doWakeup(reason, 3);
                }
                if (dev.type == DEVICE_USB_GUIDE) {
                    if (!rxBusReceiverRegistered) {
                        RxBus.get().register(this);
                        rxBusReceiverRegistered = true;
                    }
                    UsbPermissionReceiver.register(this);
                    UsbStatusReceiver.register(this);
                    requestUsbPermission();
                }
            }
        }
        /////
    }

    public void startLocalPlay(int channel, int preset) {

        Device dev = channels.get(String.valueOf(channel));
        if (dev == null) return;

        if (currentMode == MODE_WAKEUP) {

            isWakeupVideoPlaybackMode = true;
            markWakeupActivity(dev, dev.isUSB() ? "唤醒模式下进行红外直播预览" : "唤醒模式下进行可见光直播预览");
        }


        if (isSleepMode()){
            Log.e(TAG,"设备处于休眠模式，不响应拉流");
            return;
        }

        if (dev.isLiving()) return;

        cpuLock();
        finishTask(TaskManager.Task.Living.toString()); /////
        /////
        if (dev.isDVR()) {
            if (dev.isUSB()) {
                wakeupDevice(dev, "红外机芯预览");
            } else {
                wakeupDevice(dev, "可见光机芯预览");
            }
        }
        if (dev.isCamera()) {
            // if (devBusyRecording()) return;
            // 如果另一个摄像头在直播，停掉直播
            // closeOtherPlayingCamera(dev);
            if (currentMode == MODE_WAKEUP) {
                powerOnMipiIfNeeded("唤醒模式下MIPI本地预览");
            }
        }
        /////

        int streamType = 0;
        Device.onOpenCallback callback = dev.new onOpenCallback() {
            @Override
            public void openSucceed() {
                // sunwu
                if (deviceConfig.toCheck && channel == 2) {
                    revertPowerTuning();
                }

                /////
                if (!dev.isCamera()) {
                    DeviceExceptionManager.openSucceed(); /////
                    ////////
                    if (dev.type == DEVICE_DVR_HUANYU) {
                        dev.updateStatusText(dev.osd, getStatusText(), false);
                        dev.timeSync();
                    }
                    ////////
                    Settings.VideoCodec vc = dev.codec.get(String.valueOf(streamType));

                    if (vc != null) {
                        Point size = Settings.VideoCodec.getResolution(vc.resolution);
                        initDecoder(size.x, size.y);
                    } else {
                        /////
                        if (dev.isDVR()) {
                            if (dev.isUSB()) {
                                initDecoder(704, 576);
                            } else {
                                initDecoder(1920, 1080);
                            }
                        }
                        /////
                    }

                    dev.move(2, preset);  // MIPI摄像头不调用预置位
                }
                boolean ok = dev.liveStart(streamType, 1234);
                if (!ok && dev.isCamera()) {
                    powerOffMipiIfIdle("MIPI本地预览启动失败");
                }

                if (dev.isUSB() && !irLiveRegions.isEmpty()) { /////
                    irLiveRegions.clear();
                }
                // 集光新机芯设置了时间还要设置OSD其他字符，否则只显示时间
                //Date now = new Date();
                //dev.setTime(now.getYear() + 1900, now.getMonth() + 1, now.getDate(), now.getHours(), now.getMinutes(), now.getSeconds());
                //////
                if (dev.type == DEVICE_DVR_AIPU) {
                    if (dev.isOldCamera) {
                        dev.updateStatusText(getStatusText(), true);
                    } else {
                        dev.updateStatusText(getStatusText(), false);
                    }
                }
                ////////
                updateOnline("本地预览");
                showMsg("本地预览" + (ok ? "成功" : "失败"));
            }

            @Override
            public void openFailed(int errcode) {
                showMsg("本地预览失败");
                DeviceExceptionManager.openFailed(); /////
                /////
                Device dev = channels.get(String.valueOf(channel));
                if (dev.isDVR()) {
                    if (dev.isUSB()) {
                        sleepDevice(channel, "红外本地预览失败");
                    } else {
                        sleepDevice(channel, "可见光本地预览失败");
                    }
                }

                // sunwu
                if (deviceConfig.toCheck && channel == 2) {
                    applyPowerTuning();
                }

                finishTask(TaskManager.Task.Living.toString());
                if (dev.isCamera()) {
                    powerOffMipiIfIdle("MIPI本地预览打开失败");
                }
                /////
            }
        };

        dev.open(streamType, callback, DVR_BOOT_TIME, false, true, false);  // 这个地方打开成功调用前面的回调函数 ///
    }

    @Override
    public boolean stopLiveVideo(int channel, int streamType, int ssrc) {
        Device dev = channels.get(String.valueOf(channel));

        if (dev != null && dev.isDVR() && markDelayStopIfLiveStarting(channel, ssrc)) {
            Log.i(Log.TAG, "直播启动中收到停止，命令已响应，等待启动流程收口后执行停止，channel="
                    + channel + ", ssrc=" + ssrc);
            return true;
        }

        try {
            // 在打开球机的过程中允许停止
            if (dev != null && !dev.isRecording()) {
                dev.liveStop();

                if (dev.streamClient != null) {
                    dev.streamClient.close();
                    dev.streamClient = null;
                }

                /////
                if (dev.isDVR()) {
                    if (dev.isUSB()) {
                        sleepDevice(channel, "红外停止直播");
                    } else {
                        sleepDevice(channel, "可见光停止直播");
                    }
                }
                if (dev.isCamera()) {
                    powerOffMipiIfIdle("MIPI停止直播");
                }
            }
            releaseDecoder();
            finishTask(TaskManager.Task.Living.toString());

            // sunwu
            if (deviceConfig.toCheck && channel == 2) {
                applyPowerTuning();
            }


        } catch (Exception e) {
            Log.i(Log.TAG, "停止预览异常：" + e);
        }
        return true;
    }


    @Override
    public void setVideoCodec(VideoCodec v) {
        settings.videoCodecs.put(v.channel + ":" + v.streamType, v);
        Device dev = channels.get(String.valueOf(v.channel));

//        Log.d(Log.TAG, "setVideoCodec channel=" + v.channel + " stream=" + v.streamType + " dev=" + dev);

        boolean ret = false;
        if (dev != null) {
            dev.codec.put(String.valueOf(v.streamType), v);
            for (int i = 0; i < 3; i++) {
                if (deviceConfig.toCheck) {
                    if (dev.isDVR()) openShare("视频参数设置");
                }
                ret = dev.setCodec(v);
//                Log.d(Log.TAG, "setCodec attempt " + i + " result=" + ret);
                if (ret) break;
            }
        } else {
//            Log.e(Log.TAG, "Device not found for channel " + v.channel);
        }

        if (deviceConfig.toCheck) {
            if (dev != null && dev.isDVR()) closeShare("视频参数设置");
        }

        if (ret) {
            updateVideoCodecsInSettings(settings.videoCodecs);
        } else {
//            Log.w(Log.TAG, "保存取消，ret=false, channel=" + v.channel + " stream=" + v.streamType);
        }
    }


    private void updateVideoCodecsInSettings(HashMap<String, VideoCodec> codecs) {
        File file = new File(SETTING_FILE);
        JSONObject root;
        if (file.exists()) {
            try {
                String content = fileToString(file);
                root = JSON.parseObject(content);
            } catch (Exception e) {
                root = new JSONObject();
            }
        } else {
            root = new JSONObject();
        }
        // 单独将 videoCodecs 序列化放入 root
        root.put("videoCodecs", JSON.toJSON(codecs));

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            String json = root.toJSONString();
            fos.write(json.getBytes("UTF-8"));
            fos.flush();
        } catch (Exception e) {
            Log.e(Log.TAG, "更新 videoCodecs 失败: " + e.getMessage());
        } finally {
            if (fos != null) {
                try { fos.close(); } catch (IOException ignored) {}
            }
        }
    }


    public static String fileToString(File file) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            return new String(data, "UTF-8");
        } catch (Exception e) {
            Log.e(Log.TAG, "读取文件失败: " + e.getMessage());
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {
                }
            }
        }
    }


    @Override
    public ChannelStatus getChannelState(int channel, int stream) {
        Device dev = channels.get(String.valueOf(channel));
        if (dev == null) return null;

        return dev.getStatus(stream);
    }

    @Override
    public void manualIrRegonSet(int channel, Vector<IRSetting.IrRegion> regions) {
        Device dev = channels.get(String.valueOf(channel));
        if (dev == null) return;

        Vector<IRSetting.IrRegionInfo> regionInfos = new Vector<>();
        for (IRSetting.IrRegion region : regions) {
            IRSetting.IrRegionInfo regionInfo = new IRSetting.IrRegionInfo();
            regionInfo.irRegion = region;
            regionInfos.add(regionInfo);
        }

        dev.setTempRegion(channel, 0xFF, regionInfos, true);
    }

    @Override
    public void irRegionParamSet(int channel, int preset, Vector<IRSetting.IrRegionInfo> areas) {
        if (areas != null) {
            IRSetting.PresetRegions remain = irRegionParamGet().get(channel);
            if (remain == null) {
                remain = new IRSetting.PresetRegions();
            }
            remain.irPresetRegions.put(preset, areas);

            // 更新参数到设备
            Device dev = channels.get(String.valueOf(channel));
            if (dev != null) {
                dev.setTempRegion(channel, preset, areas, false);
            }

            iRSetting.irChannelRegions.put(channel, remain);
            saveSettings(iRSetting, IR_SETTING_FILE);
        }
    }

    @Override
    public HashMap<Integer, IRSetting.PresetRegions> irRegionParamGet() {
        return iRSetting.irChannelRegions;
    }

    @Override
    public void irSensorConfig(IRSetting.SensorConfig param) {
        for (Device dev : channels.values()) {
            if (dev.type == DEVICE_USB_GUIDE || dev.type == DEVICE_USB_IRAY) {
                dev.setSensorConfig(param);
            }
        }

        iRSetting.sensorConfig = param;
        saveSettings(iRSetting, IR_SETTING_FILE);
    }


    @Override
    public IRSetting.SensorConfig irSensorConfig() {
        return iRSetting.sensorConfig;
    }


    private final Runnable mResetWakeupFlagTask = new Runnable() {
        @Override
        public void run() {
            // 保活到期后不马上下电，先看设备是否仍在真正执行任务。
            // 只有真正的业务态才延后保活，避免短视频结束后残留 OPENED 状态导致一直不下电。
            for (String channel : channels.keySet()) {
                Device dev = channels.get(channel);
                if (isWakeupActiveTask(dev)) {
                    Log.i(Log.TAG, "唤醒模式下，保活时间已到，设备正在执行任务，延后进入唤醒待机");
                    resetWakeupTimer();
                    return;
                }
            }

            isWakeupKeepAliveMode = false;
            isWakeupVideoPlaybackMode = false;
            if(currentMode == MODE_WAKEUP){
                doSleep("唤醒模式下，测试保活2分钟内没有再次进行任务，云台下电，关闭红外",23);
            }
        }
    };


    /**
     * 启动/重置唤醒模式保活定时器。测试期间使用2分钟，正式版本改回15分钟。
     */
    private void resetWakeupTimer() {
        resetWakeupTimer(WAKEUP_KEEP_ALIVE_MS, "唤醒模式普通任务");
    }

    private void resetWakeupTimer(long keepAliveMs, String reason) {
        isWakeupKeepAliveMode = true;
        utilsHandler.removeCallbacks(mResetWakeupFlagTask);
        // 每次唤醒模式业务触发后重置保活倒计时。
        utilsHandler.postDelayed(mResetWakeupFlagTask, keepAliveMs);
        Log.i(Log.TAG, "重置唤醒模式保活：" + reason + "，" + keepAliveMs / PERIOD_SECOND + "秒");
    }

    private boolean isWakeupActiveTask(Device dev) {
        // OPENED 只是资源残留态，不算业务忙；只把正在执行的业务状态纳入保活判断。
        // 否则短视频/回放结束后设备可能因为状态未及时回收而一直不能回到唤醒待机。
        return dev != null
                && (dev.isOpening()
                || dev.isRecording()
                || dev.isLiving()
                || dev.isPlaybacking()
                || dev.isPhotoing());
    }

    private void markWakeupActivity(Device dev, String reason) {
        markWakeupActivity(dev, reason, "唤醒模式普通任务");
    }

    private void markWakeupActivity(Device dev, String reason, String keepAliveReason) {
        if (currentMode != MODE_WAKEUP || dev == null || !dev.isDVR()) return;
        // 所有唤醒模式业务入口都应调用这里：
        // 可见光只需要开云台(load=2)，红外需要同时开云台和红外(load=23)，然后刷新保活倒计时。
        int load = dev.isUSB() ? 23 : 2;
        doWakeup(reason, load);
        resetWakeupTimer(WAKEUP_KEEP_ALIVE_MS, keepAliveReason);
    }


    @Override
    public int fileFiles(int channel, int videoType, TimeRecord startTime, TimeRecord stopTime) {
        Device dev = channels.get(String.valueOf(channel));
        if (dev == null) return 0;

        if (currentMode == MODE_WAKEUP) {

            markWakeupActivity(dev, dev.isUSB() ? "唤醒模式查询红外录像文件个数" : "唤醒模式查询可见光录像文件个数");
            // 这个地方不要修改isWakeupVideoPlaybackMode的状态，否则后续的查询文件列表直接失败

            if (!isWakeupVideoPlaybackMode) {
                return VideoFiles.readCachedCount(channel,videoType,startTime,stopTime);   // 返回的是根据时间查询得到的结果
            }
        }

        FileDir results = null;
        for (int i = 0; i < 3; i++) {
            if (deviceConfig.toCheck) {
                if (dev.isDVR()) openShare("文件查询");
            }

            results = dev.findFiles(videoType, startTime, stopTime);
            Log.i(Log.TAG, String.format("查询时间：%s-%s，查询到的录像文件个数：%d",
                    startTime.asString, stopTime.asString, results == null ? 0 : results.count));
            if (results != null) break;
        }

        if (results != null) {
            VideoFiles.saveCountToFile(results.count);  // 保存后没有用到，暂时不删除
            return results.count;
        } else {
            return 0;
        }
    }


    @Override
    public FileList findVideoFileList(int channel, int videoType, String startTime, String stopTime, int startNumb, int endNumb) {
        // 这个起始时间和起始数量需要保存

        FileList emptyList = new FileList();
        emptyList.channel = (byte) channel;
        emptyList.begin = 0;
        emptyList.end = 0;
        emptyList.type = videoType;

        Device dev = channels.get(String.valueOf(channel));
        if (dev == null) return emptyList;

        if (currentMode == MODE_WAKEUP) {

            markWakeupActivity(dev, dev.isUSB() ? "唤醒模式查询红外录像文件列表" : "唤醒模式查询可见光录像文件列表");

            if (!isWakeupVideoPlaybackMode) {
                // 设置状态变量
                isWakeupVideoPlaybackMode = true;

                // --------------------------需要根据时间返回对应的结果--------------------------

                return VideoFiles.readCachedFileList(channel, videoType, startTime,stopTime,emptyList);
            }
        }

        FileList list = emptyList;
        for (int i = 0; i < 3; i++) {
            if (deviceConfig.toCheck) {
                if (dev.isDVR()) openShare("文件查询");
            }

            list = dev.listFile(videoType, startTime, stopTime, startNumb, endNumb);
            if (list != null) break;
        }

        if (deviceConfig.toCheck) {
            if (dev.isDVR()) closeShare("文件查询");
        }

        if (list != null) {
            VideoFiles.saveFileListToFile(list);
            return list;
        } else {
            return emptyList;
        }
    }


    // 录像回放
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public byte playbackFile(int channel, boolean UDP, String startTime, String stopTime, String ip, int port, int ssrc) {
        Device dev = channels.get(String.valueOf(channel));
//        if (电量不足) return 2;
        if (dev == null) return 3;

        if (currentMode == MODE_WAKEUP) {
            markWakeupActivity(dev, dev.isUSB() ? "唤醒模式下播放红外录像文件" : "唤醒模式下播放可见光录像文件");
            // 设置状态变量
            isWakeupVideoPlaybackMode = true;
        }


        /////
        if (dev.isDVR()) {
            if (dev.isUSB()) {
                wakeupDevice(dev, "红外机芯回放");
            } else {
                wakeupDevice(dev, "可见光机芯回放");
            }
        }

        TaskManager.add(TaskManager.Task.Playback.toString());

        if (dev.isPlaybacking()) {
            dev.playbackStop();
            try {
                Thread.sleep(200);
            } catch (Exception e) {
            }
        }

//        String taskName = "playback";
//        TaskManager.add(taskName);
//        if (dev.isPlaybacking()) {
//            Log.e("videoReplay","正在回放中，停止之前的回放");
//            dev.playbackStop();
//        }

        if (dev.streamClient != null) {
            dev.streamClient.close();
            Log.e(Log.TAG, "存在streamClient，关闭后重新创建");
        }


        if ("183.47.15.148".equals(ip)) {
            dev.streamClient = new SocketClient(ip, port, false);
        } else {
            dev.streamClient = new SocketClient(ip, port, UDP);       // 自己平台下发的是udp协议，播放存在花屏的情况
        }


//        dev.streamClient = new SocketClient(ip, port, UDP);

        if (!dev.streamClient.open()) {
            finishTask(TaskManager.Task.Playback.toString()); /////
            return 4;   // 网络连接错误
        }

        OSD osd = settings.osds.get(String.valueOf(channel));
        if (osd != null) dev.osd = osd;

        Device.onOpenCallback callback = dev.new onOpenCallback() {
            @Override
            public void openSucceed() {
                if (!dev.playbackStart(startTime, stopTime, ssrc)) {
                    finishTask(TaskManager.Task.Playback.toString()); /////
                    return;
                }
                updateOnline("回放");
            }

            @Override
            public void openFailed(int errcode) {
                /////
                Device dev = channels.get(String.valueOf(channel));
                if (dev.isDVR()) {
                    if (dev.isUSB()) {
                        sleepDevice(channel, "红外回放失败");
                    } else {
                        sleepDevice(channel, "可见光回放失败");
                    }
                }
                /////
                finishTask(TaskManager.Task.Playback.toString()); /////
            }
        };

        /////
        if (dev.isDVR()) {
            dev.open(0, callback, DVR_BOOT_TIME, !isWorkHour(), true, false);  // 这个地方打开成功调用前面的回调函数 ///
        }
        if (dev.isCamera()) {
            dev.open(0, callback, DVR_BOOT_TIME, false, true, false);  // 这个地方打开成功调用前面的回调函数 ///
        }
        /////
        return 0;
    }

    /**
     * 录像控制
     *
     * @param scale  速度、暂替
     * @param offset 跳过某个时间段
     */
    @Override
    public short playbackControl(int channel, int code, float scale, int offset, int ssrc) {
        Device dev = channels.get(String.valueOf(channel));
        if (dev == null) return 1;
//        if (电量不足) return 2;

        markWakeupActivity(dev, "唤醒模式下录像回放控制");

        /////
        if (deviceConfig.toCheck) {
            if (dev.isDVR()) openShare("回放控制");
        }
        /////
        dev.playbackControl(code, scale, offset);
        return 0;
    }

    @Override
    public void setPassword(String password) {
        settings.password = password;
        saveSettings(settings, SETTING_FILE);
    }

    @Override
    public short stopPlayCallBack(int channel, int ssrc) {
        Device dev = channels.get(String.valueOf(channel));
        if (dev != null) {
            if (deviceConfig.toCheck) {
                if (dev.isDVR()) openShare("停止回放");
            }
            dev.playbackStop();
            if (dev.streamClient != null) {
                dev.streamClient.close();
                dev.streamClient = null;
            }
            /////
            if (dev.isDVR()) {
                if (dev.isUSB()) {
                    sleepDevice(channel, "红外录像停止回放");
                } else {
                    sleepDevice(channel, "可见光录像停止回放");
                }
            }
            /////
        }
        finishTask(TaskManager.Task.Playback.toString()); /////

        // sunwu
        if (deviceConfig.toCheck && channel == 2) {
            applyPowerTuning();
        }

        return 0;
    }


    @Override
    public void setOSDConfig(int channel, OSD osd) {
        Device device = channels.get(String.valueOf(channel));
        if (device == null) return;
        osd.size = device.osd.size;
        /////
        if (osd.text != null && osd.text.startsWith("充放电控制器")) {
            Log.i(Log.TAG, "设置充放电控制器参数");
            try {
                // 提取参数部分并分割
                String paramsPart = osd.text.substring("充放电控制器".length()).trim();
                deviceConfig.chargeControl = Integer.parseInt(paramsPart);
            } catch (NumberFormatException e) {
            }
        } else if (osd.text != null && osd.text.startsWith("双光融合")) {
            /////
        } else if (osd.text != null && osd.text.startsWith("工作时间")) {
            Log.i(Log.TAG, "设置工作时间参数");
            try {
                // 提取参数部分并分割
                String paramsPart = osd.text.substring("工作时间".length()).trim();
                String[] paramStrings = paramsPart.split("-");
                settings.powerOn = paramStrings[0];
                settings.powerOff = paramStrings[1];
                saveSettings(settings, SETTING_FILE);
                POWER_ON = new String[]{paramStrings[0]};
                POWER_OFF = new String[]{paramStrings[1]};
                for (Device dev : channels.values()) {
                    if (dev.isDVR()) {
                        if (dev.isUSB()) {
                            powerControlNVR(isWorkHour(), 3);
                        } else {
                            powerControlNVR(isWorkHour(), 2);
                        }
                    }
                }
                if (powerOnIntent != null) alarmManager.cancel(powerOnIntent);
                if (powerOffIntent != null) alarmManager.cancel(powerOffIntent);
                initAlarmPowerOn();
                initAlarmPowerOff();
            } catch (NumberFormatException e) {

            }
            /////
        } else {
            // 对osd进行截断，显示通道名不能太长
            if (channel ==  1){
                osd.text = truncateText(osd.text, MAX_HUANYU_REAL_LENGTH );
            }

            settings.osds.put(String.valueOf(channel), osd);

            device.osd = osd;
            for (int i = 0; i < 3; i++) {
                ////////
                if (device.type == DEVICE_DVR_AIPU) {
                    if (device.isOldCamera) {
                        if (device.setOSD(osd, true) == true) break;
                    } else {
                        if (device.setOSD(osd, false) == true) break;
                    }
                }
                ////////

                if (deviceConfig.toCheck) {
                    if (device.isDVR()) openShare("设置OSD");
                }

                ////////
                if (device.type == DEVICE_DVR_HUANYU) {
                    if (device.updateStatusText(device.osd, getStatusText(), false) == true) break;
                }
                ////////
            }
            if (deviceConfig.toCheck) {
                if (device.isDVR()) closeShare("设置OSD");
            }
        }

        saveSettings(deviceConfig, CONFIG_FILE);
        saveSettings(settings, SETTING_FILE);
    }

    private boolean isChineseChar(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS;
    }

    private int calculateRealLength(String text) {
        if (text == null) return 0;

        int length = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChineseChar(c)) {
                length += 2;
            } else {
                length += 1;
            }
        }
        return length;
    }

    public String truncateText(String newText) {
        return truncateText(newText, MAX_REAL_LENGTH);
    }


    public String truncateText(String newText, int maxRealLength) {
        if (newText == null) {
            return "";
        }

        int realLength = calculateRealLength(newText);
        if (realLength > maxRealLength) {
            newText = truncateTextByRealLength(newText, maxRealLength);
            Log.e(Log.TAG, "自定义的OSD太长，进行了截断，原长度：" + realLength);
        }
        return newText;
    }


    private String truncateTextByRealLength(String text, int maxLength) {
        StringBuilder sb = new StringBuilder();
        int currentLength = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charLength = isChineseChar(c) ? 2 : 1;

            if (currentLength + charLength > maxLength)  {
                break;
            }

            sb.append(c);
            currentLength += charLength;
        }

        return sb.toString();
    }


    @Override
    public OSD getOSDConfig(int channel) {
        Device dev = channels.get(String.valueOf(channel));
        if (dev == null)
            return settings.osds.get(String.valueOf(channel));
        else {
            for (int i = 0; i < 3; i++) {
                if (deviceConfig.toCheck) {
                    if (dev.isDVR()) openShare("获取OSD");
                }
                OSD osd = dev.getOSD();
                if (osd != null) {
                    if (deviceConfig.toCheck) {
                        if (dev.isDVR()) closeShare("获取OSD");
                    }
                    return osd;
                }
            }
        }
        if (deviceConfig.toCheck) {
            if (dev.isDVR()) closeShare("获取OSD");
        }
        return settings.osds.get(String.valueOf(channel));
    }

    @Override
    public void ptz3DCtrl(int chanNumber, int StartingPointXCoordinate, int StartingPointYCoordinate, int AtTheEndOfXCoordinate, int AtTheEndOfCoordinate) {
        Device dev = channels.get(String.valueOf(chanNumber));
        if (dev == null || !dev.isDVR()) return;

        if (deviceConfig.toCheck) {
            if (dev.isDVR()) openShare("云台控制");
        }

        dev.ptz3D(StartingPointXCoordinate, StartingPointYCoordinate, AtTheEndOfXCoordinate, AtTheEndOfCoordinate);
    }

    /**
     * 云台巡航操作
     * cmd: 1 = 添加 ， 2 = 删除， 3 = 修改
     * 集光云台指令：
     * 0 = 添加预置点到巡航组， 1 = 删除组内预置点， 2 = 删除巡航组， 3 = 修改组内的点
     *
     * @param preset 预置位号
     * @param index  巡航组中的索引号， 从1开始
     */
    @Override
    public void setPTZCruise(int channel, int cmd, int group, int index, int preset, int duration, int speed) {
        List<CruiseGroup> cgs = settings.cruiseSettings.get(String.valueOf(channel));
        if (cgs == null) {
            cgs = new ArrayList<>();
            settings.cruiseSettings.put(String.valueOf(channel), cgs);
        }

        CruiseGroup cg = null;
        for (int i = 0; i < cgs.size(); i++)
            if (cgs.get(i).group == group) {
                cg = cgs.get(i);
                break;
            }
        if (cg == null) {
            cg = new CruiseGroup();
            cg.group = (byte) group;
            cgs.add(cg);
        }

        if (cmd == 1) {
            cg.cruises.add(new Settings.Cruise((byte) preset, (byte) duration, (byte) speed));
        } else if (cmd == 2) {
            if (index > 0 && index <= cg.cruises.size()) cg.cruises.remove(index - 1);
        } else if (cmd == 3) {
            if (index > 0 && index <= cg.cruises.size()) {
                Settings.Cruise item = cg.cruises.get(index - 1);
//                item.index = (byte) index;
                item.speed = (byte) speed;
                item.duration = (byte) duration;
                item.preset = (byte) preset;
            }
        }
        saveSettings(settings, SETTING_FILE);

        Device device = channels.get(String.valueOf(channel));
        if (device == null) return;

        device.cruiseSettings = cgs;
        for (int i = 0; i < 3; i++) {
            if (deviceConfig.toCheck) {
                if (device.isDVR()) openShare("设置巡航");
            }
            if (device.setCruise(cmd, group, index, preset, duration, speed) == true) break;
        }
        if (deviceConfig.toCheck) {
            if (device.isDVR()) closeShare("设置巡航");
        }
    }

    @Override
    public CruiseGroup[] getPTZCruise(int channel) {
//        openShare("获取巡航");
//        Device device = channels.get(String.valueOf(channel));
//        if (device == null) return null;
//        return device.getCruise();

        List<CruiseGroup> ret = settings.cruiseSettings.get(String.valueOf(channel));
        if (ret == null) {
            List<CruiseGroup> cgs = settings.cruiseSettings.get(String.valueOf(channel));
            if (cgs == null) {
                cgs = new ArrayList<>();
                settings.cruiseSettings.put(String.valueOf(channel), cgs);
            }
            return cgs.toArray(new CruiseGroup[0]);
        } else {
            return ret.toArray(new CruiseGroup[0]);
        }
    }

    @Override
    public void setPhotoParam(int channel, PhotoConfig v) {
        settings.photoConfig.put(String.valueOf(channel), v);
        Device dev = channels.get(String.valueOf(channel));
        if (dev == null) return;

        for (int i = 0; i < 3; i++) {
            if (deviceConfig.toCheck) {
                if (dev.isDVR()) openShare("设置图片参数");
            }
            if (dev.setPhotoParam(v)) break;
        }
        if (deviceConfig.toCheck) {
            if (dev.isDVR()) closeShare("设置图片参数");
        }
        saveSettings(settings, SETTING_FILE);
    }

    @Override
    public void setVideoTimeTable(int channel, int stream, List<Settings.VideoTimeItem> list) {
        settings.videoTimeTable = list;  // 查询和设置的录像列表
        saveRecordingPolicyFile(list);
        saveSettings(settings, SETTING_FILE);

        // TODO 这个地方可能需要在结束的时候减1s [startTime, endTime)
        List<VideoTimeItem> splitList = adjustOverlappingItems(list);

        RECORD_TABLE = splitList;

        if (currentMode == MODE_WAKEUP || currentMode == MODE_SLEEP) {
            cancelRecordTaskAlarms();
            cancelVideoTaskAlarms();
            // 低功耗模式只保存主站策略，本地和设备端都不执行录像；设备端清空策略同样走后台线程。
            postApplyRecordingPolicyToDvr(new ArrayList<>(), "低功耗模式下保存录像策略但不执行", null);
            saveSettings(settings, SETTING_FILE);
            return;
        }

        initRecordTask();

        if (isWorkHour()) {
            // 先设置录像时间段
            Device dev = channels.get(String.valueOf(channel));
            if (dev == null) return;
            boolean ret = dev.setRecordTimes(getDeviceRecordingPolicyForCurrentMode());
            if (!ret) {
                // 如果设置失败，可以记录日志或处理错误
                Log.e(Log.TAG, "设置录像时间段失败");
            }

            // 然后判断当前时间是否在策略时间段内
            for (VideoTimeItem item : splitList) {
                Calendar now = Calendar.getInstance();
                int nowSeconds = now.get(Calendar.HOUR_OF_DAY) * 3600 +
                        now.get(Calendar.MINUTE) * 60 +
                        now.get(Calendar.SECOND);
                int startSeconds = item.hour * 3600 + item.min * 60 + item.sec;
                int endSeconds = startSeconds + item.duration;

                if (nowSeconds >= startSeconds && nowSeconds <= endSeconds) {
                    // 命中当前时间段，提取信息执行动作
                    int recordChannel = item.channel;
                    int recordAction = item.action;
                    int recordPara = item.para;
                    Log.e(Log.TAG, "当中时间在策略内，执行对应的动作");

                    if (sleeping) {
                        doWakeup("开启云台与红外", 23);
                        gimbalPowerHandler.postDelayed(() -> {
                            if (recordAction == 0) {  // 调用预置位
                                runMove(2, recordChannel, recordPara);
                            } else if (recordAction == 1) {  // 调用巡航
                                runCruise(recordChannel, recordPara);
                            } else if (recordAction == 2) {  // 调用巡检
                                runCheckLine(recordChannel, recordPara - 1, 1);
                            }
                        }, 2 * PERIOD_MINUTE);
                    } else {
                        if (recordAction == 0) {  // 调用预置位
                            runMove(2, recordChannel, recordPara);
                        } else if (recordAction == 1) {  // 调用巡航
                            runCruise(recordChannel, recordPara);
                        } else if (recordAction == 2) {  // 调用巡检
                            runCheckLine(recordChannel, recordPara - 1, 1);
                        }
                    }
                    break; // 如果找到了匹配的时间段，可以跳出循环，避免重复执行
                }
            }
        } else {
            Intent intent = new Intent(ACTION_RECORD); /////
            sendBroadcast(intent);
        }
        saveSettings(settings, SETTING_FILE);
    }

    @Override
    public VideoCodec getVideoCodec(int channel, int streamType) {
        Device dev = channels.get(String.valueOf(channel));
        if (dev == null)
            return settings.videoCodecs.get(channel + ":" + streamType);

        if (deviceConfig.toCheck) {
            if (dev.isDVR()) openShare("视频参数查询");
        }
        VideoCodec codec = dev.getVideoCodec(streamType);
        if (deviceConfig.toCheck) {
            if (dev.isDVR()) closeShare("视频参数查询");
        }
        return codec;
    }

    /**
     * 文件命名规范
     *
     * @param channelNum      通道号
     * @param ext             文件扩展名
     * @param durationSeconds 长度，如果视频才需要给，否则给0，单位秒
     * @return
     */
    private String getFileName(int channelNum, int preset, String ext, int durationSeconds) {
        long start = System.currentTimeMillis();
        String timeStart = formatDateTime("yyyyMMddHHmmss", start);
        String timeEnd = formatDateTime("yyyyMMddHHmmss", start + durationSeconds * 1000);
        // 文件名格式： yyyy mm dd hh nn ss _ channel _ preset
        String path = FILE_PATH + String.format("%s_%s_%d_%d.%s", timeStart, timeEnd, channelNum, preset, ext);
        File file = new File(path);
        new File(file.getParent()).mkdirs();
        new File(FILE_PATH + channelNum).mkdirs();
        return path;
    }


    ///
    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(Log.TAG, "系统配置更改，配置内容：" + newConfig);
    }

    @Override
    public void onBackPressed() {
        Log.w(Log.TAG, "Back按钮点击");
        moveTaskToBack(true);
    }


    @Override
    protected void onDestroy() {
        Log.i(Log.TAG, "主窗体销毁！");
        super.onDestroy();
        ///
        if (instance == this) {
            instance = null;
        }
        scheduler.shutdownNow();
        ///
        // apk退出的时候不应该关闭wifi，否则远程调试的时候控屏软件手动关闭apk的话网络就断了
        // if (wifiAP != null) wifiAP.disable();
        RS485Impl.Instance().gpioUnInit();
        telephonyManager.listen(phoneStatListener, PhoneStatListener.LISTEN_NONE);
    }

    @Override
    public void finish() {
        moveTaskToBack(true);
    }

    @Override
    public boolean onTouch(final View view, final MotionEvent event) {
        utilsHandler.post(() -> {
            int lastPlayChannel = spnChannels.getSelectedItemPosition() + 1;
            Device dc = channels.get(String.valueOf(lastPlayChannel));
            if (dc == null) return;

            /////
            int selectPreset = spnPreset.getSelectedItemPosition();
            int selectCruise = spnCruise.getSelectedItemPosition();
            int criseDuration;
            // 获取用户输入的字符串
            String criseDurationString = etCruiseDuration.getText().toString().trim();  // 使用trim()去除前后空格
            if (criseDurationString.isEmpty()) {
                // 用户没有输入任何内容
                criseDuration = PTZ_PRESET_MOVE_TIME;  // 设置为20秒
            } else {
                try {
                    criseDuration = Integer.valueOf(criseDurationString);  // 转换为int类型
                } catch (NumberFormatException e) {
                    // 如果输入的是无效的数字
                    criseDuration = PTZ_PRESET_MOVE_TIME;  // 设置为20秒
                    e.printStackTrace();
                }
            }
            int criseSpeed;
            // 获取用户输入的字符串
            String criseSpeedString = etCruiseSpeed.getText().toString().trim();  // 使用trim()去除前后空格
            if (criseSpeedString.isEmpty()) {
                // 用户没有输入任何内容
                criseSpeed = settings.ptzSpeed;  // 设置为默认云台运动速度
            } else {
                try {
                    criseSpeed = Integer.valueOf(criseSpeedString);  // 转换为int类型
                } catch (NumberFormatException e) {
                    // 如果输入的是无效的数字
                    criseSpeed = settings.ptzSpeed;  // 设置为默认云台运动速度
                    e.printStackTrace();
                }
            }
            /////
            if (deviceConfig.toCheck) {
                if (dc.isDVR()) openShare("人工操作");
            }
            switch (view.getId()) {
                case R.id.btnUp:
                    dc.move(event.getAction() == ACTION_DOWN ? 49 : 48, 60);
                    break;
                case R.id.btnLeft:
                    dc.move(event.getAction() == ACTION_DOWN ? 51 : 48, 60);
                    break;
                case R.id.btnRight:
                    dc.move(event.getAction() == ACTION_DOWN ? 52 : 48, 60);
                    break;
                case R.id.btnDown:
                    dc.move(event.getAction() == ACTION_DOWN ? 50 : 48, 60);
                    break;
                case R.id.btnZoomin:
                    if (dc.isUSB()) { // 如果是红外设备，添加预置位
                        int preset = Integer.valueOf(tbRotate.getText().toString().trim());
                        ptzControl(lastPlayChannel, 9, preset);
                        Log.i(Log.TAG, "添加红外预置位");
                    } else {
                        dc.move(event.getAction() == ACTION_DOWN ? 57 : 48, 30);
                    }
                    break;
                case R.id.btnZoomout:
                    dc.move(event.getAction() == ACTION_DOWN ? 58 : 48, 30);
                    break;
                case R.id.btnAddPreset:
                    dc.move(9, selectPreset);
                    break;
                case R.id.btnRemovePreset:
                    dc.move(26, selectPreset);
                    break;
                case R.id.btnEditPreset:
                    dc.move(9, selectPreset);
                    break;
                case R.id.btnAddCruise:
                    setPTZCruise(lastPlayChannel, 1, selectCruise, cruiseIndex, selectPreset, criseDuration, criseSpeed);
                    Log.i(Log.TAG, "添加通道" + lastPlayChannel + "巡航组号" + selectCruise + "序号" + cruiseIndex + "为预置位" + selectPreset + "停留时间" + criseDuration + "速率" + criseSpeed);
                    cruiseIndex++;
                    break;
                case R.id.btnRemoveCruise:
                    setPTZCruise(lastPlayChannel, 2, selectCruise, cruiseIndex, selectPreset, criseDuration, criseSpeed);
                    Log.i(Log.TAG, "删除通道" + lastPlayChannel + "巡航组号" + selectCruise + "序号" + cruiseIndex);
                    break;
                case R.id.btnEditCruise:
                    setPTZCruise(lastPlayChannel, 3, selectCruise, cruiseIndex, selectPreset, criseDuration, criseSpeed);
                    Log.i(Log.TAG, "修改通道" + lastPlayChannel + "巡航组号" + selectCruise + "序号" + cruiseIndex + "为预置位" + selectPreset + "停留时间" + criseDuration + "速率" + criseSpeed);
                    break;
                /////
                default:
                    break;
            }
            updateOnline("界面操作");
        });

        return false;
    }

    public boolean setDataEnabled(int slotIdx, boolean enable, Context context) throws Exception {
        try {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
                return false;

            SubscriptionInfo info = SubscriptionManager.from(context).getActiveSubscriptionInfoForSimSlotIndex(slotIdx);
            if (info == null) return false;

            int subid = info.getSubscriptionId();
            Method setDataEnabled = telephonyManager.getClass().getDeclaredMethod("setDataEnabled", int.class, boolean.class);
            if (null != setDataEnabled) {
                setDataEnabled.invoke(telephonyManager, subid, enable);
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.i(Log.TAG, "setDataEnabled error：" + e.getMessage());
            return false;
        }
    }

    /**
     * 切换SIM卡代码，包括自动切换数据网络
     *
     * @param sim SIM卡号，必须为0或者1
     */
    private void switchSimCard(int sim) {
        try {
            if (setDefaultDataSub(sim) && setDataEnabled(sim, true, this)) {
                //Log.i(Log.TAG, "切换SIM卡" + sim + "成功");


                getSimCardInfo(); /////
                // 等待 80 秒，以便系统完成 SIM 卡切换，等待手机拨号上网
                //SystemClock.sleep(1000 * 80);
            } else {
                Log.i(Log.TAG, "切换SIM卡" + sim + "失败");
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "切换SIM卡" + sim + "失败：" + e.getMessage());
        }
    }

    public boolean setDefaultDataSub(int slotindex) {
        SubscriptionManager sm = SubscriptionManager.from(this);
        try {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
                return false;

            SubscriptionInfo info = sm.getActiveSubscriptionInfoForSimSlotIndex(slotindex);
            if (info == null) return false;

            int subId = info.getSubscriptionId();
            Method method = sm.getClass().getMethod("setDefaultDataSub Id为", int.class); /////
            if (method != null) {
                method.invoke(sm, subId);
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.i(Log.TAG, "setDefaultDataSub error：" + e.getMessage());
            return false;
        }
    }

    private void runSimTask() {
        Thread thread = new Thread(() -> {
            cpuLock();
            try {
                Log.i(Log.TAG, "切换SIM卡任务：线程开始");
                switchSimCard(1);  // 批量上传一定切换SIM2
                uploadLogs();
                switchSimCard(0);
                Log.i(Log.TAG, "切换SIM卡任务：线程结束");
            } catch (Exception e) {
                Log.i(Log.TAG, "切换SIM卡任务错误：" + e.getMessage());
            } finally {
                tryCpuUnlock();
            }
        });
        thread.start();
    }

    private void doSMSAction(Intent intent) {
        String address = intent.getStringExtra(SMS_ADDRESS);
        String body = intent.getStringExtra(SMS_BODY);
        body = body.replace("【珠海金锐】 ", "")
                .replace("【深圳东恒网络】", "")
                .replace("cmd:hdd：", "").trim();

        if (body.equals("8802")) {  // 重启球机
            // 太阳能程序已有控制
        } else if (body.equals("8801")) {  // 开云台
            doWakeup("短信开云台", 2);
        } else if (body.equals("8803") || body.indexOf("cmd:reboot") != -1) {  // 重启安卓系统
            rebootSystem("短信指令重启");
        } else if (body.equals("8804")) {  // 立即切换到SIM1
            new Thread(() -> switchSimCard(0)).start();
        } else if (body.equals("8805")) {  // 立即切换到SIM2
            new Thread(() -> switchSimCard(1)).start();
        } else if (body.equals("8806")) {  // 立即切换到SIM卡2并上报电池信息
            // 太阳能程序已有控制
        } else if (body.substring(0, 4).equals("8807")) {  // 切换定时开机关机模式

        } else if (body.equals("8808")) {  // 按特定时间切换SIM卡
            // 太阳能程序已有控制
        } else if (body.equals("8809")) {  // 切换到定时模式
            // 太阳能程序已有控制
        } else if (body.equals("8821")) {  // 立刻回传日志
            //new Thread(() -> runSimTask()).start();
        } else if (body.equals("8819")) {  // 进入调试模式
            //remoteDebug = true;
            //new TcpTunnel("183.47.15.146", 56667, "127.0.0.1", 5555).start();
        } else if (body.equals("8810")) {  // 关闭云台与红外
            doSleep("短信请求休眠，关闭云台与红外", 23);
        }
    }

    private void uploadLogs() {
        String files = "";
        String logFiles[] = {"/sdcard/zhjinrui/batcom/logs.log", "/sdcard/zhjinrui/spgp/logs.log", "/sdcard/zhjinrui/camera/logs.log"};
        for (String f : logFiles) {
            if (new File(File.separator + f).exists())
                files += " " + f;
        }
        if (files.equals("")) return;

        String file = String.format("/sdcard/zhjinrui/%s_%s_logs.tar.gz", deviceConfig.deviceId, Utils.currentTimestampFilename());
        String cmd = String.format("tar -C / -cz -f %s %s", file, files);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {  // 6735
            cmd = "busybox " + cmd;
        }
        su(cmd);
        for (String f : logFiles)
            su("rm /" + f);
        String ret = Utils.httpPostFile(FILE_URL, file, 120);
        Log.i(Log.TAG, "日志回传结果：" + file + "=>" + ret);
        su("rm /sdcard/zhjinrui/*.tar.gz");
    }

    @Override
    protected void onResume() {
        super.onResume();
        isIntent = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void doUpdateStatus() {
        getSimCardInfo(); /////
        // 没有看视频，无需查看流量状态信息等
        String text = getStatusText();
        for (Device dev : channels.values()) {
            if (dev.isLiving()) {
                if (deviceConfig.toCheck) {
                    if (dev.isDVR()) openShare("更新状态OSD");
                }

                ////////
                utilsHandler.post(() -> {
                    if (dev.type == DEVICE_DVR_AIPU) {
                        if (dev.isOldCamera) {
                            dev.updateStatusText(getStatusText(), true);
                        } else {
                            dev.updateStatusText(getStatusText(), false);
                        }
                    } else if (dev.type == DEVICE_DVR_HUANYU) {
                        dev.updateStatusText(dev.osd, getStatusText(), false);
                    }
                });
                ////////
            }
        }
    }

    private void openSocket() {
        try {
            if (socket == null) socket = new DatagramSocket(5666);
            cmdAddress = new InetSocketAddress(InetAddress.getByName(deviceConfig.server), deviceConfig.port);
            spgProtocol.server = cmdAddress.getAddress().getHostAddress();
            //showMsg(String.format("开始通信于本地端口 %d，服务器 %s 成功", socket.getLocalPort(), cmdAddress.toString()));
        } catch (Exception e) {
            socket = null;
            //showMsg(String.format("启动与服务器 %s:%d 通信失败，%s", deviceConfig.server, deviceConfig.port, e.getMessage()));
        }
    }

    private void doSend(final byte[] data) {
        try {
            DatagramPacket outPacket = new DatagramPacket(data, data.length, cmdAddress);
            socket.send(outPacket);
            lastSentTime = System.currentTimeMillis();
            errorLogInterval = 1000;
        } catch (Exception e) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastErrorLogTime > errorLogInterval) {
                Log.e(Log.TAG, "发送数据错误：" + e.getMessage());
                lastErrorLogTime = currentTime;
                errorLogInterval = Math.min(errorLogInterval * 2, MAX_INTERVAL);
            }
        }
    }

    @Override
    public void sendData(final byte[] data) {
        // 因为安卓不允许在主线程中进行网络操作！
        sendHandler.post(() -> doSend(data));
    }

    @Override
    public void doSleep() {
        doSleep("服务器请求休眠，关闭云台与红外", 23);
    }

    private void sleepDevice(final int channel, final String reason) {

        if (currentMode == MODE_WAKEUP && isWakeupKeepAliveMode){
            Log.e(Log.TAG,"设备处于唤醒模式，并在保活任务后的2分钟内，不进行关闭操作");
            return;
        }

        /////
        ////////
        boolean allDevIdle = true;
        for (Device device : channels.values()) {
            if (device.isDVR() && device.isBusy()) {
                allDevIdle = false;
            }
        }
        boolean finalAllDevIdle = allDevIdle;
        ////////
        final Device dev = channels.get(String.valueOf(channel));
        // 设备在拍照、直播、录制情况下不能关掉
        if (dev == null || dev.isCamera() || dev.isBusy()) return;

        if (dev.isDVR()) {
            if (deviceConfig.toCheck) {
                closeShare(reason);
            }
            if (dev.isUSB()) {
                if (!isWorkHour() && (!irPreheatActive || deviceConfig.toCheck)) {
                    if (dev.type == DEVICE_USB_GUIDE) {
                        if (rxBusReceiverRegistered) {
                            RxBus.get().unregister(this);
                            rxBusReceiverRegistered = false;  // 标记为未注册
                        }
                        UsbPermissionReceiver.unregister(this);
                        UsbStatusReceiver.unregister(this);
                    }
                    if (deviceConfig.chargeControl >= 6) {
                        // 关闭云台与红外
                        dev.close();
                        doSleep(reason, 2);
                        doSleep(reason, 3);
                        irPreheatActive = false;
                    }
                } else {
                    // 恢复到录像预置位的指令不应该有重复，当所有动作结束，恢复到录像预置位就可以，把多余的指令从队列中删除
                    utilsHandler.removeCallbacksAndMessages(presetResetToken);
                    utilsHandler.postDelayed(() -> {
                        // 抽检模式下拍照/拉流结束不回录像预置位
                        if (!deviceConfig.toCheck) {
                            ////////
                            if (finalAllDevIdle) {
                                dev.move(2, recordPreset);
                            }
                            ////////
                        }
                        // 抽检模式下红外拍照/拉流结束关闭网口与红外
                        if (deviceConfig.toCheck) {
                            closeShare(reason);
                            dev.close();
                            doSleep(reason, channel);
                        }
                    }, presetResetToken, 0);
                }
            } else {
                if (!isWorkHour() && !irPreheatActive) {
                    dev.close();
                    doSleep(reason, 2);
                } else {
                    // 恢复到录像预置位的指令不应该有重复，当所有动作结束，恢复到录像预置位就可以，把多余的指令从队列中删除
                    utilsHandler.removeCallbacksAndMessages(presetResetToken);
                    utilsHandler.postDelayed(() -> {
                        // 抽检模式下拍照/拉流结束不回录像预置位
                        if (!deviceConfig.toCheck) {
                            ////////
                            if (finalAllDevIdle) {
                                dev.move(2, recordPreset);
                            }
                            ////////
                        }
                        // 抽检模式下关闭网口
                        if (deviceConfig.toCheck) {
                            closeShare(reason);
                        }
                    }, presetResetToken, 0);
                }
            }
        }
        /////
    }

    /////
    @Override
    public void takeUploadLog(int channel) {
        uploadHandler.post(() -> spgProtocol.doHandleRequestUploadLog(channel));
    }

    @Override
    public void takePhoto(int channel, int preset) {
        utilsHandler.post(() -> {
            ///
            runValidBusiness(() -> {
                takePhoto(channel, preset, false, null, true);
            });
            ///
        });
    }

    /**
     * filename不为空为补拍照片
     */

    private void takePhoto(int channel, int preset, boolean show, String filename, boolean alert) {
        takePhoto(channel, preset, show, filename, alert, false);
    }

    private void takePhoto(int channel, int preset, boolean show, String filename, boolean alert, boolean scheduledPhotoTask) {

        if (isSleepMode()){
            Log.e(TAG,"设备处于休眠模式，不响应拍照");
            return;
        }

        byte imageStitch = iRSetting.imageStitch;
        Log.i(Log.TAG, "抓拍通道：" + channel + "，预置位：" + preset);
        Device dev = channels.get(String.valueOf(channel));
        if (dev == null) {
            Log.i(Log.TAG, "通道" + channel + "设备不存在");
            return;
        }

        markWakeupActivity(dev, dev.isUSB() ? "唤醒模式下红外拍照" : "唤醒模式下可见光拍照", "拍照启动阶段临时保活");

        /////
        if (dev.isDVR()) {
            if (dev.isUSB()) {
                wakeupDevice(dev, "红外机芯抓拍");
            } else {
                wakeupDevice(dev, "可见光机芯抓拍");
            }
        }


        if (dev.isCamera()) {
            // 如果另一个摄像头在直播，停掉直播
            // closeOtherPlayingCamera(dev);
            // if (dev.isRecording()) dev.videoStop();
        }
        /////
        if (currentMode == MODE_WAKEUP && dev.isCamera()) {
            powerOnMipiIfNeeded("唤醒模式下MIPI拍照");
        }

        cpuLock();
        String fn = filename == null ? getFileName(channel, preset, EXT_JPG, 0) : filename;
        if (shouldUseWakeupPhotoFollowup(channel)) {
            wakeupPhotoFollowupFiles.put(fn, channel);
        }
        TaskManager.add(fn);

        Device.onOpenCallback callback = dev.new onOpenCallback() {
            @Override
            public void openSucceed() {

                if (deviceConfig.toCheck) {
                    isIRPhotoing = true;
                    isVLPhotoing = true;
                }

                if (dev.type == DEVICE_DVR_HUANYU) {
                    dev.updateStatusText(dev.osd, getStatusText(), true);
                    dev.timeSync();
                }

                Bitmap pop = null;
                if (dev.type == DEVICE_DVR_AIPU) {
                    if (dev.isOldCamera) {
                        dev.updateStatusText(getStatusText(), true);
                    } else {
                        dev.updateStatusText(getStatusText(), false);
                    }
                }
                /////
                if (dev.popChanel != 0) {
                    Device device = channels.get(String.valueOf(dev.popChanel));
                    if (device != null) {
                        Log.i(Log.TAG, "开始抓拍画中画照片");
                        pop = device.takePhoto(0, preset, fn, show, recordPreset, settings.aiParameters, alert, iRSetting.imageStitch); /////
                        Log.i(Log.TAG, "结束抓拍画中画照片，拍照" + (pop == null ? "失败" : "成功"));
                    }
                }


                if (dev.isUSB() && preset == 1 && imageStitch == 1) { /////
                    Log.i(Log.TAG, "开始拼接红外照片");
                    Bitmap image1 = dev.takePhoto(0, preset, fn, show, recordPreset, settings.aiParameters, alert, iRSetting.imageStitch);
                    if (image1 == null) {
                        Log.i(Log.TAG, "抓拍设备开启成功，拍照预置位1失败");
                        controllerCallback.onPhotoFailed(channel, 1, fn);
                    }
                    Bitmap image2 = dev.takePhoto(0, 2, fn, show, recordPreset, settings.aiParameters, alert, iRSetting.imageStitch);
                    if (image2 == null) {
                        Log.i(Log.TAG, "抓拍设备开启成功，拍照预置位2失败");
                        controllerCallback.onPhotoFailed(channel, 2, fn);
                    }
                    Bitmap image3 = dev.takePhoto(0, 3, fn, show, recordPreset, settings.aiParameters, alert, iRSetting.imageStitch);
                    if (image3 == null) {
                        Log.i(Log.TAG, "抓拍设备开启成功，拍照预置位3失败");
                        controllerCallback.onPhotoFailed(channel, 3, fn);
                    }
                    // 拼接三张照片    拼接函数
                    if (dev.mergeBitmapVertical(preset, fn, show, image2, image1, image3)) {
                        Log.i(Log.TAG, "照片拼接成功");
                    } else {
                        Log.i(Log.TAG, "照片拼接失败");
                    }
                    /////
                } else if (dev.isUSB() && iRSetting.imageFusion == 1) { /////
                    // 双光融合功能
                    /////
                    Device devRgb = null;
                    for (Device dev : channels.values()) {
                        if (dev.type == DEVICE_DVR_AIPU) {
                            devRgb = dev;
                        } else if (dev.type == DEVICE_DVR_HUANYU) {
                            devRgb = dev;
                        }
                    }
                    if (devRgb != null) {
                        Device finalDevRgb = devRgb;
                        Device.onOpenCallback callbackRgb = finalDevRgb.new onOpenCallback() {
                            /////
                            @Override
                            public void openSucceed() {
                                ////////
                                if (finalDevRgb.type == DEVICE_DVR_AIPU) {
                                    finalDevRgb.setOSD(finalDevRgb.osd, true);
                                    finalDevRgb.updateStatusText(getStatusText(), true);
                                } else {
                                    finalDevRgb.updateStatusText(finalDevRgb.osd, getStatusText(), true);
                                }
                                ////////

                                Bitmap pop = null;

                                if (finalDevRgb.popChanel != 0) {
                                    Device device = channels.get(String.valueOf(finalDevRgb.popChanel));
                                    if (device != null) {
                                        Log.i(Log.TAG, "开始抓拍画中画照片");
                                        pop = device.takePhoto(0, preset, fn, show, recordPreset, settings.aiParameters, alert, iRSetting.imageStitch);
                                        Log.i(Log.TAG, "结束抓拍画中画照片，拍照" + (pop == null ? "失败" : "成功"));
                                    }
                                }
                                performDualLightFusion(preset, show, fn, recordPreset, settings, alert,
                                        iRSetting, channel, finalDevRgb, dev, controllerCallback);
                            }

                            @Override
                            public void openFailed(int errcode) {
                                Log.i(Log.TAG, "抓拍通道：" + finalDevRgb.id + "，预置位：" + preset + "，设备开启失败");
                                controllerCallback.onPhotoFailed(finalDevRgb.id, preset, fn);
                            }
                        };
                        finalDevRgb.open(0, callbackRgb, DVR_BOOT_TIME, !isWorkHour(), false, false); ///
                    }
                    /////
                } else {
                    if (!dev.takePhoto(0, preset, show, fn, pop, recordPreset, settings.aiParameters, alert)) {
                        Log.i(Log.TAG, "抓拍设备开启成功，拍照失败");

                        controllerCallback.onPhotoFailed(channel, preset, fn);
                    }
                }
                DeviceExceptionManager.openSucceed();
            }

            @Override
            public void openFailed(int errcode) {
                Log.i(Log.TAG, "抓拍通道：" + channel + "，预置位：" + preset + "，设备开启失败");

                Log.i(Log.TAG, "抓拍设备开启失败");
                controllerCallback.onPhotoFailed(channel, preset, fn);
                DeviceExceptionManager.openFailed(); /////
            }
        };


        /////
        if (dev.isDVR()) {
            if (dev.isUSB()){
                dev.open(0, callback, DVR_BOOT_TIME, !isWorkHour() && (sleeping || sleepingIr), false, false);  // 这个地方打开成功调用前面的回调函数 ///
            }else {
                dev.open(0, callback, DVR_BOOT_TIME, !isWorkHour() && sleeping, false, false);  // 这个地方打开成功调用前面的回调函数 ///
            }
        }

        if (dev.isCamera()) {
            dev.open(0, callback, DVR_BOOT_TIME, false, false, false);  // 这个地方打开成功调用前面的回调函数 ///
        }
    }


    private void performDualLightFusion(int preset, boolean show, String fn, int recordPreset,
                                        Settings settings, boolean alert, IRSetting iRSetting,
                                        int channel, Device devChannel1, Device dev,
                                        ControllerCallback controllerCallback) {
        Bitmap imageRgb = devChannel1.takePhoto(0, preset, fn, show, recordPreset,
                settings.aiParameters, alert, iRSetting.imageStitch);
        if (imageRgb == null) {
            Log.i(Log.TAG, "可见光抓拍设备开启成功，拍照失败");
            controllerCallback.onPhotoFailed(channel, preset, fn);
            return;
        }
        // 使用 image_rgb 做双光融合
        Log.e(Log.TAG, "融合前OpenCV初始化的结果：" + isOpenCVInitialized);
        if (dev.imageFusion(0, preset, show, fn, imageRgb, recordPreset)) {
            Log.i(Log.TAG, "双光融合成功");
        } else {
            Log.i(Log.TAG, "双光融合失败");
        }
    }

//    public boolean isColsePhotoTimeTable(List<Settings.FaultInfo> faults, long nowMillis) {
//        for (Settings.FaultInfo fault : faults) {
//            if (fault.functionCode == 0x04) {
//                int code = fault.faultCode;
//                if (code == 0x01 || code == 0x02 || code == 0x15) {
//                    long faultMillis = fault.time.timestamp;
//                    long diffHours = (nowMillis - faultMillis) / (1000 * 60 * 60);
//                    if (diffHours >= 24) {
//                        return true;
//                    }
//                }
//            }
//        }
//        return false;
//    }
    /////

    private void finishTask(final String task) {
        TaskManager.remove(task);
        tryCpuUnlock();
    }

    @Override
    public void onFileUploadFailure(String fileName) {
        finishTask(fileName);
    }

    @Override
    public void onFileUploadFailure(long time, String fileName, int Channel, int preset, SPGProtocol.FILE_TYPE type) {
        if (fileName.isEmpty()) return;

        showMsg("文件传输失败：" + fileName);
        if (false) { // 过检打开
            showMsg("文件传输失败：" + fileName + " 第" + (++fileUploadFailedTimes) + "次");
            if (fileUploadFailedTimes >= 5) { // 文件传输连续失败3次,进入飞行模式,不收发任何指令,等待超时重启
                SystemSettings.airplaneOn(this);
                fileUploadFailedTimes = 0;
            }
        }

        /*Integer count = failedFileError.get(fileName);
        if (count == null)
            failedFileError.put(fileName, new Integer(1));
        else {
            if (count > 10) {
                File file = new File(fileName);
                file.renameTo(new File(FILE_PATH + Channel + File.separator + file.getName()));
                Log.e(Log.TAG, "文件传输失败次数过多，忽略传输：" + fileName);
                failedFileError.remove(fileName);
            } else {
                count++;
                failedFileError.put(fileName, count);
            }
        }*/
        // 文件上传失败，当前任务结束，从任务列表移除
        finishTask(fileName);
    }


    @Override
    public void onFileUploadEnd(long time, String fileName, int Channel, int preset, SPGProtocol.FILE_TYPE type) {
        fileUploadFailedTimes = 0;

        File file = new File(fileName);
        file.renameTo(new File(FILE_PATH + Channel + File.separator + file.getName()));
        Log.d(Log.TAG, "文件传输成功：" + fileName);
        // 文件上传成功，当前任务从任务列表移除
        finishTask(fileName);
        /*if (failedFileError.keySet().contains(fileName)) {
            failedFileError.remove(fileName);
        }*/
    }


    @Override
    public List<CheckGroup> getCheckGroup(int Channel) {
        return settings.checkGroups.get(String.valueOf(Channel));
    }

    @Override
    public void setCheckGroup(int Channel, int cmd, int group, int point) {
        Device dev = channels.get(String.valueOf(Channel));
        Log.e(Log.TAG, "Channel" + Channel + "cmd" + cmd + "group" + group + "point" + point);
        // 巡检组号从 1 开始
        List<CheckGroup> cgs = settings.checkGroups.get(String.valueOf(Channel));      //组号列表
        if (cgs == null) {
            cgs = new ArrayList<>();
            settings.checkGroups.put(String.valueOf(Channel), cgs);
        }

        // 查找对应的巡检组
        CheckGroup cg = null;
        for (int i = 0; i < cgs.size(); i++) {
            if (cgs.get(i).index == group) {
                cg = cgs.get(i);
                break;
            }
        }

        // 如果没有找到对应的组，创建新组
        if (cg == null) {
            cg = new CheckGroup();
            cg.index = group;
            cgs.add(cg);
        }

        if (deviceConfig.toCheck) {
            if (dev.isDVR()) openShare("设置巡检参数");
        }

        // 利用100以上的预置位实现巡检，按每20个预置位设定一组巡检      TODO 这个地方可能存在问题，规约上面：巡检点序号：从1开始计数，取值范围1-64
        if (cmd == 0x01)  // 添加巡检点
        {
            // 利用100以上的预置位实现巡检，按每20个预置位设定一组巡检  120 + group*20
            int preset = CHECK_LINE_START_PTZ_INDEX + group * CHECK_LINE_PTZ_COUNT + cg.points.size();
            dev.move(9, preset);
            cg.points.add(cg.points.size());
            dev.setSceneName(preset, String.format("巡检组：%d，巡检位：%d", group, cg.points.size()));
        } else if (cmd == 0x02) {  // 删
            Log.e(Log.TAG, "point" + point);

            if (point == 0x00) {  // 如果只是01或者02，只进行清空操作
                // 删除整个巡检组
                for (int j = 0; j < cgs.size(); j++) {
                    if (group == cgs.get(j).index) {
                        CheckGroup cgToRemove = cgs.get(j);
                        // 删除该组对应的所有预置位
                        for (int i = 0; i < cgToRemove.points.size(); i++) {
                            int preset = CHECK_LINE_START_PTZ_INDEX + group * CHECK_LINE_PTZ_COUNT + i;
                            dev.move(26, preset);  // 删除预置位
                        }
                        // 从列表中移除该巡检组
                        cgs.remove(j);
                        break;  // 找到并删除后退出循环
                    }
                }
            } else {
                // 删除指定巡检点
                if (point - 1 < cg.points.size()) {
                    int idx = cg.points.get(point - 1);
                    cg.points.remove(point - 1);
                    dev.move(26, CHECK_LINE_START_PTZ_INDEX + group * CHECK_LINE_PTZ_COUNT + idx);
                }
            }
        } else if (cmd == 0x03) {  // 修改
            // 修改指定巡检点
            if (point - 1 < cg.points.size()) {
                int preset = CHECK_LINE_START_PTZ_INDEX + group * CHECK_LINE_PTZ_COUNT + cg.points.get(point - 1);
                dev.move(9, preset);
                dev.setSceneName(preset, String.format("巡检组%d，巡检位：%d", group, cg.points.get(point - 1)));
            }
        }
        if (deviceConfig.toCheck) {
            if (dev.isDVR()) closeShare("设置巡检参数");
        }
        saveSettings(settings, SETTING_FILE);
    }

    @Override
    public List<CheckScheduleItem> getCheckLineSchedule(int Channel) {
        List<Settings.CheckScheduleItem> items = settings.checkSchedule.get(String.valueOf(Channel));
        if (items == null) items = new ArrayList<>();
        return items;
    }

    @Override
    public boolean setCheckLineSchedule(int Channel, List<Settings.CheckScheduleItem> items) {
        for (int i = 0; i < items.size(); i++)
            for (int j = 0; j < items.size(); j++) {
                CheckScheduleItem item1 = items.get(i);
                CheckScheduleItem item2 = items.get(j);
                int v1 = (item1.hour << 8) | item1.minute;
                int v2 = (item2.hour << 8) | item2.minute;
                if (v1 < v2) {
                    items.set(j, item1);
                    items.set(i, item2);
                }
        }
        settings.checkSchedule.put(String.valueOf(Channel), items);
        boolean b = saveSettings(settings, SETTING_FILE);
        if (b) initCheckLineTask(Channel, 0);
        return b;
    }

    /**
     * 根据3GPP TS 36.101规范将EARFCN转换为频段
     * 这是唯一可靠的方法，因为Android没有提供getBand()
     */
    private int getBandFromEarfcn(int earfcn) {
        Log.d(Log.TAG, "earfcn：" + earfcn);
        // 中国移动专用频段
        if (earfcn >= 39650 && earfcn <= 41589) return 41;  // TDD 2600MHz
        // FDD 频段（中国移动授权使用）
        if (earfcn >= 1200 && earfcn <= 1949) return 3;  // FDD 1800MHz
        if (earfcn >= 3450 && earfcn <= 3799) return 8;  // FDD 900MHz
        // 其他可能使用的频段
        if (earfcn >= 37750 && earfcn <= 38249) return 38;  // TDD 2600MHz
        if (earfcn >= 38650 && earfcn <= 39649) return 40;  // TDD 2300MHz
        return -1;
    }

    /**
     * 获取小区ID(CI) - 兼容不同Android版本
     */
    private int getCi(CellIdentityLte identity) {
        try {
            // Android 5.1+ 标准方法
            return identity.getCi();
        } catch (NoSuchMethodError e) {
            // 旧版本Android的兼容处理
            try {
                // 通过反射获取ci
                return (Integer) identity.getClass().getMethod("getCi").invoke(identity);
            } catch (Exception ex) {
                // 最后尝试解析toString
                return parseCiFromString(identity.toString());
            }
        }
    }

    /**
     * 从toString输出解析CI值
     */
    private int parseCiFromString(String identityString) {
        try {
            // 查找"ci="的位置
            int ciIndex = identityString.indexOf("ci=");
            if (ciIndex == -1) return -1;
            // 提取CI值
            int start = ciIndex + 3;
            int end = identityString.indexOf(',', start);
            if (end == -1) end = identityString.length();
            return Integer.parseInt(identityString.substring(start, end).trim());
        } catch (Exception e) {
            return -1;
        }
    }

    @SuppressLint("MissingPermission")
    private void getNetworkInfo() {
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null) {
            Log.e(Log.TAG, "TelephonyManager not available");
            return;
        }
        List<CellInfo> cellInfos = tm.getAllCellInfo();
        if (cellInfos == null || cellInfos.isEmpty()) {
            Log.e(Log.TAG, "No cell info available");
            return;
        }
        for (CellInfo cellInfo : cellInfos) {
            if (cellInfo instanceof CellInfoLte) {
                CellInfoLte lteInfo = (CellInfoLte) cellInfo;
                CellIdentityLte identity = lteInfo.getCellIdentity();
                // 获取EARFCN（频点号） - 这是最可靠的数据源
                int earfcn = identity.getEarfcn();
                // 使用EARFCN计算频段
                int band = getBandFromEarfcn(earfcn);
                // 获取其他信息
                int pci = identity.getPci();
                int tac = identity.getTac();
                int ci = getCi(identity);  // 获取小区ID
                int rsrp = lteInfo.getCellSignalStrength().getRsrp();
                int rsrq = lteInfo.getCellSignalStrength().getRsrq();
                String info = null;
                // 构建信息字符串
                if (band == 3 || band == 8) {
                    info = String.format(
                            "FDD-LTE Band：%d; EARFCN：%d; PCI：%d; TAC：%d; CI：%d; RSRP：%d dBm; RSRQ：%d dB",
                            band, earfcn, pci, tac, ci, rsrp, rsrq
                    );
                } else {
                    info = String.format(
                            "FDD-LTE Band：%d; EARFCN：%d; PCI：%d; TAC：%d; CI：%d; RSRP：%d dBm; RSRQ：%d dB",
                            band, earfcn, pci, tac, ci, rsrp, rsrq
                    );
                }
                Log.d(Log.TAG, info);
                break;  // 只处理服务小区
            }
        }
    }

    /////

    @Override
    public TrafficeUsage getTrafficeUsage() {
        TrafficeUsage usage = new TrafficeUsage();
        usage.time = new TimeRecord(System.currentTimeMillis());
        usage.monthLeft = (int) (deviceConfig.traffic * 1024 - getMonthTraffic() / 1024 / 1024);
        usage.todayUsed = (int) (TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()) / 1024 / 1024;
        usage.monthUsed = (int) (getMonthTraffic() / 1024 / 1024);
        Log.i(Log.TAG, String.format("当前网络信号值为%ddBm，详细参数如下：", signalDBM)); /////
        getNetworkInfo(); /////
        return usage;
    }

    @Override
    public Settings.SceneParameter getSceneParameters(int channel, int preset) {
        String key = String.format("%d,%d", channel, preset);
        Device dev = channels.get(String.valueOf(channel));
        Settings.SceneParameter ret = settings.sceneParameters.get(key);
        if (dev != null) {
            if (deviceConfig.toCheck) {
                if (dev.isDVR()) openShare("获取通用参数配置");
            }
            ret.name = dev.getSceneName(preset);
            if (deviceConfig.toCheck) {
                if (dev.isDVR()) closeShare("获取通用参数配置");
            }
        }
        return ret;
    }

    @Override
    public byte setSceneParameters(int channel, int preset, Settings.SceneParameter parameter) {
        String key = String.format("%d,%d", channel, preset);
        settings.sceneParameters.put(key, parameter);
        Device dev = channels.get(String.valueOf(channel));
        if (dev != null) {
            if (deviceConfig.toCheck) {
                if (dev.isDVR()) openShare("通用参数设置");
            }
            dev.setSceneName(preset, parameter.name);
            if (deviceConfig.toCheck) {
                if (dev.isDVR()) closeShare("通用参数设置");
            }
        }
        return (byte) (saveSettings(settings, SETTING_FILE) ? 0 : 3);
    }

    @Override
    public void sendSMS(String sim, String content) {
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(sim, null, content, null, null);
    }


    @Override
    public void setAIParameters(Settings.AIParameter aiParameters) {
        String key = String.format("%d,%d", aiParameters.channel, aiParameters.preset);
        settings.aiParameters.put(key, aiParameters);
        saveSettings(settings, SETTING_FILE);
    }

    @Override
    public List<Settings.AIParameter> getAIParameters(int channel, int preset) {
        ArrayList<Settings.AIParameter> ret = new ArrayList<>();
        if (preset == 0xff) {
            for (Settings.AIParameter item : settings.aiParameters.values()) {
                if (item.channel == channel)
                    ret.add(item);
            }
        } else {
            String key = String.format("%d,%d", channel, preset);
            Settings.AIParameter parameter = settings.aiParameters.get(key);
            if (parameter != null)
                ret.add(parameter);
        }
        return ret;
    }


    @Override
    public void setAIAction(int channel, int preset, Settings.AIAction[] actions) {
        String key = String.format("%d,%d", channel, preset);
        settings.aiActions.put(key, actions);
        saveSettings(settings, SETTING_FILE);
    }


    @Override
    public Settings.AIAction[] getAIAction(int channel, int preset) {
        String key = String.format("%d,%d", channel, preset);
        Settings.AIAction[] ret = settings.aiActions.get(key);
        if (ret == null || ret.length == 0) {
            ret = new Settings.AIAction[1];
            ret[0] = new Settings.AIAction();
            ret[0].alertAction = 0;
            ret[0].alertType = 0;
            ret[0].alertParam1 = 0xFFFF;
            ret[0].alertParam2 = 0xFFFF;
        }
        return ret;
    }


    public boolean getData() {
        try {
            cpuLock();
            TaskManager.add(ACTION_SAMPLE);

            if (deviceConfig.aeroDevice != 0) GetAero();

            if (deviceConfig.chargeControl <= 1 || deviceConfig.chargeControl == 8)
                return false; /////

            float value[] = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
            if (deviceConfig.chargeControl == 6) {
                value[0] = RS485Impl.Instance().getLoadAmpler();

                /////
                // 会有电流值为0的异常情况，以下代码为避免这个情况，放电时设置为-0.1，充电时设置为0.1
                if (value[0] == 0 && batAmper < 0) {
                    value[0] = -0.1f;
                } else if (value[0] == 0 && batAmper >= 0) {
                    value[0] = 0.1f;
                }

                if (value[0] <= -199 || value[0] >= 199) {
                    value[0] = temporaryCurrent;
                } else {
                    temporaryCurrent = value[0];
                }

                value[3] = RS485Impl.Instance().getSolarAmpler();
                value[4] = RS485Impl.Instance().getBatAmpler();

                // 不太可能会出现电流值为655.或-655.的异常情况，但是还是做双重保险，-655.时加上655.36，655.时减去655.36
                if (value[4] < -50) {
                    value[4] = (float) (value[4] + 655.36);
                } else if (value[4] > 50) {
                    value[4] = (float) (value[4] - 655.36);
                }

                value[5] = RS485Impl.Instance().getSolarVoltage();
                value[6] = RS485Impl.Instance().getBatVoltage();
                value[8] = RS485Impl.Instance().getBatTotalQuantity();
                value[9] = RS485Impl.Instance().getBatTemp();
                tempEnvControl = value[9];
                value[11] = RS485Impl.Instance().getLoadVoltage();
                value[12] = RS485Impl.Instance().getBatQuantity();
                if (value == null || value.length < 13) return false;
                ///////
            } else if (deviceConfig.chargeControl == 9) {
                value[0] = RS485Impl.Instance().getSRLoadVoltageCurrent()[1];
                value[3] = RS485Impl.Instance().getSRSolarPanelParams()[1];
                value[4] = RS485Impl.Instance().getLoadAmpler();
                value[5] = RS485Impl.Instance().getSRSolarPanelParams()[0];
                value[6] = RS485Impl.Instance().getSRBatteryVoltage();
                value[9] = RS485Impl.Instance().getSRTemperatures()[1];
                tempEnvControl = value[9];
                value[11] = RS485Impl.Instance().getSRLoadVoltageCurrent()[0];
                value[12] = Float.parseFloat(RS485Impl.Instance().getSRBatterySOC());
                if (value == null || value.length < 13) return false;
                ///////
            } else {
                value = RS485Impl.Instance().getBatInfo();
                if (value == null || value.length < 12) return false;
            }

            Intent myint = new Intent(BATCOM_SAMPLE_ACTION);
            if (deviceConfig.chargeControl == 6) {
                // 汇能精电
                // [0] = A8, 负载电流
                // [3] = A6，太阳能电流
                // [4] = A7，电池电流
                // [5] = A1，太阳能电压
                // [6] = A2，电池电压
                // [8] = A4，电池容量
                // [9] = A5，温度
                // [11] = A12，负载电压
                // [12] = A13，电池剩余电量

//                Log.e(Log.TAG,"负载电流2value[0]:"+value[0]);

                final String rawData = String.format("太阳能：%.3fV/%.3fA，" +
                                "电池：%.3fV/%.3fA/%.3fAh，" +
                                "负载：%.3fV/%.3fA，" +
                                "温度：%.3f\n",
                        value[5], value[3],
                        value[6], value[4], value[8],
                        value[11], value[0],
                        value[9]);

                Log.d(Log.TAG, "太阳能采样：" + rawData);

                if (value[0] < -1 && value[3] < -1) {
                    Log.i(Log.TAG, "太阳能采样失败，丢弃数据");
                    return false;
                }

                // [0] 负载电流 [3] 太阳能电流 [4] 电池充电电流 [5] 太阳能电压 [6] 电池电压
                // [8] 电池容量 [9] 温度      [11] 负载电压   [12] 电池剩余电量
//                Log.e(Log.TAG,"value[6]:"+value[6]);

                myint.putExtra("batVoltage", value[6]);
                myint.putExtra("solarVoltage", value[5]);
                myint.putExtra("batAmpler", value[4]);
                myint.putExtra("loadAmpler", value[0]);
                myint.putExtra("temperature", value[9]);
                myint.putExtra("solarAmpler", value[3]);
                myint.putExtra("amplerHours", value[8]);
                myint.putExtra("batPrecent", (int) value[12]);
                ///////
            } else if (deviceConfig.chargeControl == 9) {
                // 硕日
                // [0] = A8, 安卓版电流
                // [3] = A6，太阳能电流
                // [4] = A7，电池电流
                // [5] = A1，太阳能电压
                // [6] = A2，电池电压
                // [9] = A5，温度
                // [11] = A12，安卓版电压
                // [12] = A13，电池剩余电量
                final String rawData = String.format("太阳能：%.3fV/%.3fA，" +
                                "电池：%.3fV/%.3fA，" +
                                "负载：%.3fV/%.3fA，" +
                                "温度：%.3f\n",
                        value[5], value[3],
                        value[6], value[4],
                        value[11], value[0],
                        value[9]);

                Log.d(Log.TAG, "太阳能采样：" + rawData);

                if (value[0] < -1 && value[3] < -1) {
                    Log.i(Log.TAG, "太阳能采样失败，丢弃数据");
                    return false;
                }

                // [0] 安卓版电流 [3] 太阳能电流 [4] 电池充电电流 [5] 太阳能电压 [6] 电池电压
                // [9] 温度      [11] 安卓版电压  [12] 电池剩余电量
                myint.putExtra("loadAmpler", value[0]);
                myint.putExtra("solarAmpler", value[3]);
                myint.putExtra("batAmpler", value[4]);
                myint.putExtra("solarVoltage", value[5]);
                myint.putExtra("batVoltage", value[6]);
                myint.putExtra("temperature", value[9]);
                myint.putExtra("batPrecent", (int) value[12]);
                ///////
            } else {
                final String rawData = String.format("太阳能：%.3fV/%.3fA，" +
                                "电池：%.3fV/%.3fA/%.3fAh，" +
                                "负载：%.3fV/%.3fA，" +
                                "温度：%.3f，" +
                                "湿度：%.3f\n",
                        value[5], value[3],
                        value[6], value[4], value[8],
                        value[11], value[0] + value[1] + value[2],
                        value[9],
                        value[10]);

                Log.d(Log.TAG, "太阳能采样：" + rawData);

                if (value[0] < -1 && value[1] < -1 && value[2] < -1 && value[3] < -1) {
                    Log.i(Log.TAG, "太阳能采样失败，丢弃数据");
                    return false;
                }

                // [0] 1路电流  [1] 2路电流  [2] 3路电流 [3] 太阳能电流 [4] 电池充电电流 [5] 太阳能电压
                // [6] 电池电压 [7] 热敏电阻1 [8] 热敏2  [9] 温度      [10] 湿度
                myint.putExtra("batVoltage", value[6]);
                myint.putExtra("solarVoltage", value[5]);
                myint.putExtra("batAmpler", value[4]);
                /////
                if (deviceConfig.chargeControl == 3) {
                    myint.putExtra("loadAmpler1", value[0]);
                    myint.putExtra("loadAmpler2", value[1]);
                }
                /////
                myint.putExtra("loadAmpler", value[0] + value[1] + value[2]);
                myint.putExtra("temperature", value[9]);
                myint.putExtra("humidity", value[10]);
                myint.putExtra("solarAmpler", value[3]);
                myint.putExtra("amplerHours", value[8]);
            }
            sendBroadcast(myint);
        } finally {
            finishTask(ACTION_SAMPLE);
        }
        return true;
    }

    /**
     * 根据当前时间，判断是否是工作时间段
     *
     * @return
     */
    private boolean isWorkHour() {

        if (isWakeupMode() || isSleepMode()){    // 如果是非full模式，设备处于非工作状态
            return false;
        }


        Date now = new Date();
//        Date start = Utils.dateFromString(POWER_ON);
//        Date end = Utils.dateFromString(POWER_OFF);
//        if (start.before(end))
//            return Utils.between(now, start, end);
//        else
//            return !Utils.between(now, end, start);
        // 确保两个数组长度相同，防止数据错位
        int minLength = Math.min(POWER_ON.length, POWER_OFF.length);
        for (int i = 0; i < minLength; i++) {
            Date start = Utils.dateFromString(POWER_ON[i]);
            Date end = Utils.dateFromString(POWER_OFF[i]);
            if (start.before(end)) {
                if (Utils.between(now, start, end)) {
                    return true;  // 只要在任意一个工作时间段内，就返回 true
                }
            } else {
                if (!Utils.between(now, end, start)) {
                    return true;  // 只要符合跨午夜的工作时间段，也返回 true
                }
            }
        }
        return false;
    }

    /**
     * 根据输入时间，判断是否是工作时间段
     *
     * @return
     */
    private boolean isWorkHour(int hour, int minute, int second) {
        // 创建一个表示“输入时间”的 Calendar 对象（日期无关，只看时分秒）
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, second);
        calendar.set(Calendar.MILLISECOND, 0);
        Date inputTime = calendar.getTime();
        // 确保两个数组长度相同，防止数据错位
        int minLength = Math.min(POWER_ON.length, POWER_OFF.length);
        for (int i = 0; i < minLength; i++) {
            Date start = Utils.dateFromString(POWER_ON[i]);
            Date end = Utils.dateFromString(POWER_OFF[i]);
            if (start.before(end)) {
                if (Utils.between(inputTime, start, end)) {
                    return true;  // 只要在任意一个工作时间段内，就返回 true
                }
            } else {
                if (!Utils.between(inputTime, end, start)) {
                    return true;  // 只要符合跨午夜的工作时间段，也返回 true
                }
            }
        }
        return false;
    }


    private void requestUsbPermission() {
        UsbDevice device = DeviceUtils.getGuideUsbDevice(this);
        if (device == null) {
            Log.i(Log.TAG, String.format("未连接高德红外机芯"));
            return;
        }
        // 判断设备是否有权限，没有则申请权限
        if (!DeviceUtils.hasUsbPermission(this, device)) {
            DeviceUtils.requestUsbPermission(this, device);
        }
    }


    /**
     * 重启气象仪，以便复位雨量
     */
    public void resetAero() {
        /////
        boolean ret = false;
        if (deviceConfig.aeroDevice == 1 || deviceConfig.aeroDevice == 3) {
            ret = RS485Impl.Instance().resetAero();
        } else if (deviceConfig.aeroDevice == 2 || deviceConfig.aeroDevice == 4) {
            ret = RS485Impl.Instance().resetAero2();
        } else if (deviceConfig.aeroDevice == 5) {
            ret = RS485Impl.Instance().resetAero3();
        }
        Log.w(Log.TAG, "重启气象仪：" + ret);
        Log.w(Log.TAG, "重启气象仪：" + ret);
        /////
    }

    public boolean GetAero() {
        String s = "";
        // 七要素: 0R0,Dn=000D,Dm=058D,Dx=090D,Sn=000.0M,Sm=000.2M,Sx=000.4M,Ta=028.4C,Ua=065.4P,Pa=001000.7H,Rc=0000.0M,Sr=0000.2W
        // 四要素: 0R0,Ta=023.8C,Ua=055.9P,Pa=001009.7H,Rc=0000.0M
        // 富奥通新增加风速：D3S=000D,S3S=00.0M,D1M=000D,S1M=00.0M,D10M=000D,S10M=00.0M，替换原来的Sn。。。 2024-1-11
        if (deviceConfig.aeroDevice == 1) { /////
            s = RS485Impl.Instance().getAeroInfo(114);
//            s += RS485Impl.Instance().getSpeedInfo();
        } else if (deviceConfig.aeroDevice == 2 || deviceConfig.aeroDevice == 3) /////
            s = RS485Impl.Instance().getAeroInfo2();
        else if (deviceConfig.aeroDevice == 4)
            s = RS485Impl.Instance().getAeroInfo(49);
        else if (deviceConfig.aeroDevice == 5) /////
//            s = RS485Impl.Instance().getAeroInfo(79); /////
            s = RS485Impl.Instance().getAeroInfo(90); /////
        else if ((deviceConfig.aeroDevice == 6 || deviceConfig.aeroDevice == 7) && deviceConfig.chargeControl == 6){
            s = RS485Impl.Instance().getAeroInfo4(); /////
        }else {
            s = RS485Impl.Instance().getAeroInfo4WithoutHNJD();
        }



        Log.w(Log.TAG, "获取气象仪数据：" + s);
        if ("".equals(s)) return false;

        Intent myint = new Intent(AEROINFO_ACTION);

        if(deviceConfig.aeroDevice == 6 || deviceConfig.aeroDevice == 7){
            if (deviceConfig.chargeControl == 6){
                myint.putExtra("aeroinfo", RS485Impl.Instance().getAeroInfo4Arry());
                sendBroadcast(myint);
            }else {
                myint.putExtra("aeroinfo", RS485Impl.Instance().getAeroInfoArry4WithoutHNJD());
                sendBroadcast(myint);
            }
        }else {
            myint.putExtra("aeroinfo", s);
            sendBroadcast(myint);
        }

        return true;
    }


    @Override
    public void onUpgradeStart() {
        TaskManager.add("upgrade");
    }
    /////

    ////////
    private void stopBDS() {
        if (locationManager != null && locationListener != null) {
            try {
                locationManager.removeUpdates(locationListener);
                Log.i(Log.TAG, "已移除BDS位置监听器");
            } catch (Exception e) {
                Log.e(Log.TAG, "移除BDS监听失败");
            }
        } else {
            Log.i(Log.TAG, "BDS未初始化或监听器为空，无需移除");
        }
    }

    private void cancelSoftShutdownTimer() {
        if (softShutdownTimer != null) {
            softShutdownTimer.cancel();
            softShutdownTimer.purge(); // 清除已取消的任务
            softShutdownTimer = null;
            Log.i(Log.TAG, "软关机定时器已取消");
        }
    }

    public void performSoftShutdown(Context context) {
        try {
            Log.i(Log.TAG, "开始软关机流程");

            // 1. 关闭 BDS ////////
            stopBDS(); // 关闭 BDS 服务 ////////

            // 2. 关闭 Wi-Fi
            Log.i(Log.TAG, "关闭 Wi-Fi");
            /////
//            doWifiAction(false);
//            WifiAP wifiAP = WifiAP.getInstance(this, deviceConfig.deviceId, "11121314");
            WifiAP wifiAP = WifiAP.getInstance(this, ssid, "11121314"); /////
            wifiAP.disable();
            /////

            // 3. 关闭 4G（飞行模式）-- 需要系统签名或ROOT权限
//            SystemSettings.airplaneOn(context); /////  使用这个函数会导致软件卡死


            ///
//            try {
//                Log.i(Log.TAG, "关闭 4G蜂窝网络（开启飞行模式）");
//                android.provider.Settings.Global.putInt(context.getContentResolver(), android.provider.Settings.Global.AIRPLANE_MODE_ON, 1);
//                Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
//                intent.putExtra("state", true);
//                context.sendBroadcast(intent);
//            } catch (Exception e) {
//                Log.e(Log.TAG, "开启飞行模式失败" + e);
//            }
            ///

            ///
            // 4. 取消系统所有闹钟事件
            Log.i(Log.TAG, "取消系统所有闹钟事件");
//            if (photoIntent != null) alarmManager.cancel(photoIntent);
            if (photoIntent != null) cancelAllPhotoAlarms();
            if (videoIntent != null) alarmManager.cancel(videoIntent);
            cancelAllCheckLineAlarms();
            if (powerOnIntent != null) alarmManager.cancel(powerOnIntent);
            if (powerRgbOnIntent != null) alarmManager.cancel(powerRgbOnIntent);
            if (powerIrOnIntent != null) alarmManager.cancel(powerIrOnIntent);
            if (powerOffIntent != null) alarmManager.cancel(powerOffIntent);
//            if (heartBeatIntent != null) alarmManager.cancel(heartBeatIntent); ///
            if (sampleIntent != null) alarmManager.cancel(sampleIntent);
            if (utilizationRateIntent != null) alarmManager.cancel(utilizationRateIntent); ///
            if (rebootIntent1 != null) alarmManager.cancel(rebootIntent1);
            if (rebootIntent2 != null) alarmManager.cancel(rebootIntent2);
            if (protectIntent != null) alarmManager.cancel(protectIntent);
//            if (wifiIntent != null) alarmManager.cancel(wifiIntent);
//        if (protectIntent != null) alarmManager.cancel(photoCheckIntent); /////
            ///

            // 5. 停止业务逻辑
            Log.i(Log.TAG, "停止摄像服务和定时任务");
            for (Device dev : channels.values()) {
                // 关闭摄像头，拍照、直播、录制情况除外
                sleepDevice(dev.id, "软关机");
                // 停止拉流
                if (dev.isLiving()) stopLiveVideo(dev.id, 0, dev.ssrcLive);
                // 停止回放
                if (dev.isPlaybacking()) stopPlayCallBack(dev.id, dev.ssrcPlayback);
                // 停止巡航
                dev.stopCruise();
                // 停止巡检
                dev.stopCheckLine();
            }


//            // 取消系统所有闹钟事件
//            Log.i(Log.TAG, "取消系统所有闹钟事件");
////            if (photoIntent != null) alarmManager.cancel(photoIntent);
//            if (photoIntent != null) cancelAllPhotoAlarms();
//            if (videoIntent != null) alarmManager.cancel(videoIntent);
//            cancelAllCheckLineAlarms();
//            if (powerOnIntent != null) alarmManager.cancel(powerOnIntent);
//            if (powerRgbOnIntent != null) alarmManager.cancel(powerRgbOnIntent);
//            if (powerIrOnIntent != null) alarmManager.cancel(powerIrOnIntent);
//            if (powerOffIntent != null) alarmManager.cancel(powerOffIntent);
//            if (heartBeatIntent != null) alarmManager.cancel(heartBeatIntent);
//            if (sampleIntent != null) alarmManager.cancel(sampleIntent);
//            if (rebootIntent1 != null) alarmManager.cancel(rebootIntent1);
//            if (rebootIntent2 != null) alarmManager.cancel(rebootIntent2);
//            if (protectIntent != null) alarmManager.cancel(protectIntent);
////            if (wifiIntent != null) alarmManager.cancel(wifiIntent);
////        if (protectIntent != null) alarmManager.cancel(photoCheckIntent); /////

            // 6. 断开网口
            if (deviceConfig.toCheck) {
                closeShare("软关机");
            }

            // 7. 清理缓存 / 数据同步
            Log.i(Log.TAG, "清理缓存并同步数据");
            Runtime.getRuntime().exec("sync");  // 强制写入 eMMC
            SystemClock.sleep(1000);  // 等待写入完成

//            // 8. 写入 shutdown.flag 标记
//            Log.i(Log.TAG, "写入 shutdown.flag");
//            File flagFile = new File("/mnt/sdcard/shutdown.flag");
//            FileOutputStream fos = new FileOutputStream(flagFile);
//            fos.write("OK".getBytes());
//            fos.close();

            // 如果没有进行软重启的话，这个地方等待2分钟进行重启系统
            softShutdownTimer = new Timer();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            Log.i(Log.TAG, "当前时间：" + sdf.format(new Date()));

            softShutdownTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Log.i(Log.TAG, "2分钟时间到，执行重启。当前时间：" +
                            sdf.format(new Date()));
                    rebootSystem("2分钟延迟重启");
                }
            }, 2 * 60 * 1000);

            // 9. 控制GPIO关闭多路负载
            /////
            for (Device dev : channels.values()) {
                if (dev.isDVR()) {
                    if (dev.isUSB()) {
                        for (int i = 0; i < 3; i++) {
                            boolean errcode1 = RS485Impl.Instance().gpioCloseLoad3();
                            Log.i(Log.TAG, String.format("关闭红外%s", errcode1 ? "成功" : "失败"));
                            if (errcode1) {
                                break;
                            }
                        }
                    } else {
                        for (int i = 0; i < 3; i++) {
                            boolean errcode2 = RS485Impl.Instance().gpioCloseLoad3();
                            Log.i(Log.TAG, String.format("关闭云台%s", errcode2 ? "成功" : "失败"));
                            if (errcode2) {
                                break;
                            }
                        }
                    }
                }
            }
            /////

        } catch (Exception e) {
            Log.e(Log.TAG, "软关机失败");
            rebootSystem("软关机失败，直接重启系统");  // 防止软关机失败，后设备不会重启
        }
    }

    @Override
    public void getAllData() {
        // 采集并上传电源、流量数据
        Intent intentSample = new Intent(ACTION_SAMPLE);
        context.sendBroadcast(intentSample);
        // 采集并上传气象数据
        Intent intentAero = new Intent(AEROINFO_ACTION);
        context.sendBroadcast(intentAero);
    }

    @Override
    public void startVoiceBroadcast() {
//        try {
//            mediaPlayer.reset();
//            File file = new File(ALARM_AUDIO_FILE);
//            mediaPlayer.setDataSource(file.getPath());  // 指定音频文件路径
//            mediaPlayer.setLooping(true);  // 设置为循环播放
//            mediaPlayer.prepare();  // 初始化播放器MediaPlayer
//            mediaPlayer.start();
//            Log.i(TAG, "语音广播已启动");
//        } catch (Exception e) {
//            Log.e(TAG, "播放音频异常" + e);
//        }
    }

    @Override
    public void stopVoiceBroadcast() {
//        if (mediaPlayer != null) {
//            if (mediaPlayer.isPlaying()) {
//                mediaPlayer.stop();
//            }
//            mediaPlayer.reset();
//            Log.i(TAG, "语音广播已停止");
//        }
    }

    private static class TaskManager {
        private static final LinkedList<String> taskList = new LinkedList<>();

        public static synchronized void add(String task) {
            if (!taskList.contains(task)) taskList.add(task);

        }

        public static synchronized void remove(String task) {
            taskList.remove(task);
        }

        public static synchronized boolean contain(String task) {
            return taskList.contains(task);
        }

        public static synchronized boolean empty() {
            return taskList.isEmpty();
        }

        private enum Task {
            Living,
            Playback,
            SetRTC
        }
    }


    /////
    private static class DeviceExceptionManager {
        private static final int MAX_RETRYING = 5; // 设备连续开启5次失败，重启设备
        private static int openFailed = 0;

        private static void openSucceed() {
            openFailed = 0;
        }

        private static void openFailed() {
            if (++openFailed <= MAX_RETRYING || utilsHandler == null) {
                return;
            }
            openFailed = 0;
            utilsHandler.post(() -> {
                Log.i(Log.TAG, "球机打开失败" + openFailed + "次，重启球机恢复");
                powerControlNVR(false, 2); /////
                SystemClock.sleep(PERIOD_SECOND);
                powerControlNVR(true, 2); /////
            });
        }
    }

    private class DrawTempRegion {
        private final SVDraw drawer;

        public DrawTempRegion(@NonNull SVDraw drawer) {
            this.drawer = drawer;
        }

        public void draw(int x, int y) {
            drawer.setPoint(x, y);
        }

        public void updateRegionType(IRSetting.IrRegion region) {
            // 获取下拉框的选中索引
            int selectedObjType = irObjType.getSelectedItemPosition(); // 获取区域类型下拉框的选中索引
            region.cls = (byte) selectedObjType; // 将选中的索引值赋值为区域类型
        }

        public void updateRegionFlag(IRSetting.IrRegion region) {
            // 获取下拉框的选中索引
            int selectedObjFlag = irObjFlag.getSelectedItemPosition(); // 获取区域标志下拉框的选中索引
            region.flag = (byte) selectedObjFlag; // 将选中的索引值赋值为区域标志
        }

        public void save() {
            if (drawer.getPoints().isEmpty()) return;

            int channel = spnChannels.getSelectedItemPosition() + 1;
            int preset = spnPreset.getSelectedItemPosition();
            drawer.finishPoint();
            float w = (float) drawer.width();
            float h = (float) drawer.height();
            Device dev = channels.get(String.valueOf(channel));
            if (dev != null && (dev.type == DEVICE_USB_GUIDE || dev.type == DEVICE_USB_IRAY)) {
                IRSetting.IrRegion drawRegion = new IRSetting.IrRegion();
                drawRegion.points = new Vector<>();
                // 读取绘制的所有坐标点，转换到0--255之间
                for (Point point : drawer.getPoints()) {
                    int x11 = Math.round(point.x * 255 / w);
                    int y11 = Math.round(point.y * 255 / h);
                    drawRegion.points.add(new Point(x11, y11));
                }

                // 更新区域中的目标类型
                updateRegionType(drawRegion);
                // 更新区域中的目标标志
                updateRegionFlag(drawRegion);

                if (preset == 0) { // UI选择预置位0表示视频画框
                    Vector<IRSetting.IrRegion> regions = irLiveRegions;
                    regions.add(drawRegion);
                    manualIrRegonSet(channel, regions);
                    showMsg("设置红外测温区域成功");
                } else {
                    Vector<IRSetting.IrRegionInfo> regions = new Vector<>();
                    HashMap<Integer, IRSetting.PresetRegions> channelRegions = irRegionParamGet();
                    if (channelRegions != null) {
                        IRSetting.PresetRegions presetRegions = channelRegions.get(channel);
                        if (presetRegions != null && presetRegions.irPresetRegions.containsKey(preset)) {
                            regions = presetRegions.irPresetRegions.get(preset);
                        }
                    }

                    IRSetting.IrRegionInfo info = new IRSetting.IrRegionInfo();
                    info.irRegion = drawRegion;
                    // 更新区域中的目标距离
                    // 获取用户输入的字符串
                    String inputDistance = irRegionDistance.getText().toString().trim();  // 使用trim()去除前后空格
                    if (inputDistance.isEmpty()) {
                        // 用户没有输入任何内容
                        info.distance = iRSetting.sensorConfig.distance;  // 设置为全局测温距离
                    } else {
                        try {
                            float distance = Float.parseFloat(inputDistance);  // 转换为float类型
                            info.distance = distance;  // 存储到info.distance
                        } catch (NumberFormatException e) {
                            // 如果输入的是无效的数字
                            info.distance = iRSetting.sensorConfig.distance;  // 设置为全局测温距离
                            e.printStackTrace();
                        }
                    }
                    // 更新区域中的目标发射率
                    // 获取用户输入的字符串
                    String inputEmi = irRegionEmi.getText().toString().trim();  // 使用trim()去除前后空格
                    if (inputEmi.isEmpty()) {
                        // 用户没有输入任何内容
                        info.emissivity = iRSetting.sensorConfig.emissivity;  // 设置为全局目标发射率
                    } else {
                        try {
                            float emissivity = Float.parseFloat(inputEmi);  // 转换为float类型
                            info.emissivity = emissivity;  // 存储到info.emissivity
                        } catch (NumberFormatException e) {
                            // 如果输入的是无效的数字
                            info.emissivity = iRSetting.sensorConfig.emissivity;  // 设置为全局目标发射率
                            e.printStackTrace();
                        }
                    }
                    regions.add(info);
                    irRegionParamSet(channel, preset, regions);
                    showMsg("设置红外测温区域，预置位：" + preset + "成功");
                }
            }
            drawer.clearPoints();
        }

        public void clear() {
            int channel = spnChannels.getSelectedItemPosition() + 1;
            int preset = spnPreset.getSelectedItemPosition();

            IRSetting.PresetRegions remain = irRegionParamGet().get(channel);
            if (remain != null && remain.irPresetRegions != null) {
                if (remain.irPresetRegions.containsKey(preset)) {
                    remain.irPresetRegions.remove(preset);
                    irRegionParamSet(channel, preset, new Vector<>());
                }
                showMsg("清空红外测温区域，预置位：" + preset);
            }
            irLiveRegions.clear();
            drawer.clearPoints();
        }
    }

    /////
//    /**
//     * 添加新的故障信息，并触发故障上传。
//     *
//     * @param functionCode 功能位置编码（如：0x01 表示主控单元）
//     * @param faultCode    故障类型编码（如：0x01 表示时钟异常）
//     */
//    public static void addNewFault(byte functionCode, byte faultCode) {
//        // 检查是否已有相同的未恢复故障
//        for (FaultInfo faultInfo : settings.faultInfos) {
//            if (faultInfo.functionCode == (functionCode & 0xFF) &&
//                    (faultInfo.faultCode & 0x7F) == (faultCode & 0x7F) &&
//                    (faultInfo.faultCode & 0x80) == 0) {
////                Log.i(Log.TAG, "重复故障，不再记录：" +
////                        String.format("功能码：%02X 故障码：%02X", functionCode, faultCode));
//                return;
//            }
//        }
//
//        boolean isFirst = settings.faultInfos.isEmpty();
//        int deltaSeconds = isFirst ? 0 :
//                (int) ((System.currentTimeMillis() - settings.faultInfos.get(settings.faultInfos.size() - 1).time.timestamp) / 1000);
//
//        FaultInfo fault = new FaultInfo(isFirst, deltaSeconds, (byte) (functionCode & 0xFF), (byte) (faultCode & 0xFF));
//        settings.faultInfos.add(fault);
//
//        Log.i(Log.TAG, "新增故障：" +
//                String.format("功能码：%02X 故障码：%02X", functionCode, faultCode));
//
//        saveSettings(settings, SETTING_FILE);  // 写入 settings.json //////
//        spgProtocol.doReportFault(settings.faultInfos, true);  // 立即上传 ///////
//    }
//
//    /**
//     * 添加故障恢复记录，自动删除原故障并追加恢复项。
//     *
//     * @param functionCode 功能位置编码
//     * @param faultCode    原始故障编码（低7位），如 0x01
//     */
//    public static void addFaultRecovery(byte functionCode, byte faultCode) {
//        boolean found = false;
//
//        // 倒序遍历，删除所有匹配的未恢复故障（防止跳过）
//        for (int i = settings.faultInfos.size() - 1; i >= 0; i--) {
//            FaultInfo faultInfo = settings.faultInfos.get(i);
//            if ((faultInfo.faultCode & 0x80) == 0 &&   // 未恢复
//                    faultInfo.functionCode == (functionCode & 0xFF) &&
//                    (faultInfo.faultCode & 0x7F) == (faultCode & 0x7F)) {
//                settings.faultInfos.remove(i);
//                found = true;
//                Log.i(Log.TAG, "故障恢复，移除未恢复记录：" +
//                        String.format("功能码：%02X 故障码：%02X", functionCode, faultCode));
//            }
//        }
//
//        // 若未找到对应故障，不记录恢复
//        if (!found) {
////            Log.i(Log.TAG, "未找到对应故障，跳过恢复记录：" +
////                    String.format("功能码：%02X 故障码：%02X", functionCode, faultCode));
//            return;
//        }
//
//        // 插入恢复记录去重判断（防止重复恢复）
//        for (FaultInfo faultInfo : settings.faultInfos) {
//            if ((faultInfo.faultCode & 0x80) != 0 &&
//                    faultInfo.functionCode == (functionCode & 0xFF) &&
//                    (faultInfo.faultCode & 0x7F) == (faultCode & 0x7F)) {
//                Log.i(Log.TAG, "已有相同恢复记录，跳过添加：" +
//                        String.format("功能码：%02X 故障码：%02X", functionCode, faultCode));
//                return;
//            }
//        }
//
//        boolean isFirst = settings.faultInfos.isEmpty();
//        int deltaSeconds = isFirst ? 0 :
//                (int) ((System.currentTimeMillis() - settings.faultInfos.get(settings.faultInfos.size() - 1).time.timestamp) / 1000);
//
//        byte recoveryCode = (byte) (faultCode | 0x80);  // 设置高位表示恢复
//        FaultInfo recovery = new FaultInfo(isFirst, deltaSeconds, (byte) (functionCode & 0xFF), (byte) (recoveryCode & 0xFF));
//        settings.faultInfos.add(recovery);
//
//        Log.i(Log.TAG, "记录故障恢复：" +
//                String.format("功能码：%02X 故障码：%02X", functionCode, faultCode));
//
//        saveSettings(settings, SETTING_FILE);  // 写入 settings.json //////
//        spgProtocol.doReportFault(settings.faultInfos, false);  // 立即上传 ///////
//    }

    /**
     * 0 —— (-85)dbm 满格(5格)信号
     * (-85) —— (-90)dbm 4格信号
     * (-90) —— (-95)dbm　3格信号
     * (-95) —— (-100)dbm 2格信号
     * (-100) —— (-105)dbm 1格信号
     */
    @SuppressWarnings("deprecation")
    private class PhoneStatListener extends PhoneStateListener {
        //获取信号强度
        @Override
        public void onSignalStrengthChanged(int asu) {
            super.onSignalStrengthChanged(asu);
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);

            try {
                Method m = SignalStrength.class.getDeclaredMethod("getLevel", (Class[]) null);
                m.setAccessible(true);
                signalLevel = (Integer) m.invoke(signalStrength, (Object[]) null);
                if (signalLevel < 0) signalLevel = 0;
                if (signalLevel > 4) signalLevel = 4;

                m = signalStrength.getClass().getMethod("getDbm");
                signalDBM = (int) m.invoke(signalStrength);
            } catch (Exception e) {
            }

            ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null) {
                NetworkInfo info = connectivity.getActiveNetworkInfo();
                if (info != null) {
                    if (info.isConnected())
                        signalLevel++;
                    else
                        signalLevel = 4;

                    if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                        netType = "Wi-Fi";
                        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                        if (wifiInfo.getBSSID() != null) {
                            signalLevel = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), 5);
                        }
                    } else switch (telephonyManager.getNetworkType()) {
                        // 2G网络
                        case TelephonyManager.NETWORK_TYPE_GPRS:
                        case TelephonyManager.NETWORK_TYPE_CDMA:
                        case TelephonyManager.NETWORK_TYPE_EDGE:
                        case TelephonyManager.NETWORK_TYPE_1xRTT:
                        case TelephonyManager.NETWORK_TYPE_IDEN:
                            netType = "2G";
                            break;
                        // 3G网络
                        case TelephonyManager.NETWORK_TYPE_EVDO_A:
                        case TelephonyManager.NETWORK_TYPE_UMTS:
                        case TelephonyManager.NETWORK_TYPE_EVDO_0:
                        case TelephonyManager.NETWORK_TYPE_HSDPA:
                        case TelephonyManager.NETWORK_TYPE_HSUPA:
                        case TelephonyManager.NETWORK_TYPE_HSPA:
                        case TelephonyManager.NETWORK_TYPE_EVDO_B:
                        case TelephonyManager.NETWORK_TYPE_EHRPD:
                        case TelephonyManager.NETWORK_TYPE_HSPAP:
                            netType = "3G";
                            break;
                        // 4G网络
                        case TelephonyManager.NETWORK_TYPE_LTE:
                            netType = "4G";
                            break;
                        default:
                            netType = "移动";
                            break;
                    }
                }
            }
        }
    }


    private class SPGPRunnable implements Runnable {
        final byte[] data;

        public SPGPRunnable(byte[] data) {
            this.data = data;
        }

        @Override
        public void run() {
            try {
                spgProtocol.handlerOrder(data[7], data, deviceConfig.aiAccTest); /////
            } catch (Exception e) {
                Log.e(Log.TAG, "协议处理异常：" + e);
            }
        }
    }


    private class udpRunnable implements Runnable {
        @Override
        public void run() {
//            SystemClock.sleep(140 * 1000);
            openSocket();
            byte[] buf = new byte[5000];
            DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);

//            spgProtocol.doBootContactInfo();
            while (!Thread.interrupted()) try {
                socket.receive(receivePacket);
                lastRecvTime = System.currentTimeMillis();

                final byte[] mReceiveData = new byte[receivePacket.getLength()];
                System.arraycopy(receivePacket.getData(), 0, mReceiveData, 0, receivePacket.getLength());

                SPGPRunnable run = new SPGPRunnable(mReceiveData);
                if (mReceiveData[7] == (byte) 0xCB)  // 升级文件包，不能开独立线程，不然太多线程会崩溃
                    utilsHandler.post(run);
                else
                    new Thread(run).start();
            } catch (SocketTimeoutException e) {
                Log.e(Log.TAG, "接收指令超时异常");
                SystemClock.sleep(1000);
            } catch (Exception e) {
                //openSocket();
                Log.e(Log.TAG, "接收数据错误：" + e.getMessage());
                SystemClock.sleep(1000);
            }
            Log.d(Log.TAG, "数据接收线程结束");
        }
    }
    /////

    ///
    private static final int AUX5_CHANNEL_NUM = 1;
    private static final int AUX5_OPEN_ORDER = 17;
    private static final int AUX5_CLOSE_ORDER = 18;
    private static final int AUX5_PARA = 5;

    private static final int IDLE_LIMIT_SECONDS = 2 * 60;
    private static final int IDLE_ADD_SECONDS = 60;

    private boolean aux5Open = true;
    private boolean aux5Switching = false;
    private boolean hasBusinessTask = false;

    private int idleSeconds = 0;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static MainActivity instance;

    public void startAux5IdleMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            onIdleMonitorTick();
        }, 60, 60, TimeUnit.SECONDS);
    }

    private boolean setAux5Switch(boolean open) {
        try {
            int order = open ? AUX5_OPEN_ORDER : AUX5_CLOSE_ORDER;
            if (order == AUX5_OPEN_ORDER) {
                Log.i(Log.TAG, "打开辅助开关5");
            } else {
                Log.i(Log.TAG, "关闭辅助开关5");
            }
            ptzControl(AUX5_CHANNEL_NUM, order, AUX5_PARA);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean openAux5() {
        if (aux5Open) {
            Log.i(Log.TAG, "辅助开关5已经开启，无需再开启");
            return true;
        }

        boolean success = setAux5Switch(true);

        if (!success) {
            return false;
        }
        return true;
    }

    private boolean closeAux5() {
        if (!aux5Open) {
            Log.i(Log.TAG, "辅助开关5已经关闭，无需再关闭");
            return true;
        }

        boolean success = setAux5Switch(false);

        if (!success) {
            return false;
        }
        return true;
    }

    private void resetIdleTimer() {
        idleSeconds = 0;
    }

    public static void runValidBusiness(Runnable business) {
        MainActivity activity = instance;

        if (activity != null) {
            activity.runValidBusinessInner(business);
        }
    }

    public static <T> T runValidBusinessWithResult(Supplier<T> business, T defaultValue) {
        MainActivity activity = instance;

        if (activity != null) {
            return activity.runValidBusinessWithResultInner(business, defaultValue);
        }

        return defaultValue;
    }

    private void runValidBusinessInner(Runnable business) {
        boolean allowRun;

        synchronized (this) {
            allowRun = prepareValidBusiness();
        }

        if (allowRun && business != null) {
            business.run();
        }
    }

    private <T> T runValidBusinessWithResultInner(Supplier<T> business, T defaultValue) {
        boolean allowRun;

        synchronized (this) {
            allowRun = prepareValidBusiness();
        }

        if (!allowRun || business == null) {
            return defaultValue;
        }

        try {
            return business.get();
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }

    private boolean prepareValidBusiness() {
        if (aux5Open) {
            resetIdleTimer();
            return true;
        }

        if (openAux5()) {
            resetIdleTimer();
            return true;
        }

        return false;
    }

    private boolean isNowInVideoTimeTable(List<Settings.VideoTimeItem> videoTimeTable) {
        if (videoTimeTable == null || videoTimeTable.isEmpty()) {
            return false;
        }

        Calendar calendar = Calendar.getInstance();

        int nowSeconds =
                calendar.get(Calendar.HOUR_OF_DAY) * 3600
                        + calendar.get(Calendar.MINUTE) * 60
                        + calendar.get(Calendar.SECOND);

        for (Settings.VideoTimeItem item : videoTimeTable) {
            if (item == null) {
                continue;
            }

            int startSeconds =
                    (item.hour & 0xFF) * 3600
                            + (item.min & 0xFF) * 60
                            + (item.sec & 0xFF);

            int duration = item.duration;

            if (duration <= 0) {
                continue;
            }

            if (duration >= 24 * 3600) {
                return true;
            }

            int endSeconds = startSeconds + duration;

            if (endSeconds <= 24 * 3600) {
                if (nowSeconds >= startSeconds && nowSeconds < endSeconds) {
                    return true;
                }
            } else {
                int realEndSeconds = endSeconds % (24 * 3600);

                if (nowSeconds >= startSeconds || nowSeconds < realEndSeconds) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasRunningBusinessTask() {
        boolean hasRunningBusinessTask = false;
        for (Device dev : channels.values()) {
            if (dev.isPhotoing() || dev.isLiving() || dev.isRecording() || isNowInVideoTimeTable(settings.videoTimeTable) || dev.isPlaybacking()) {
                hasRunningBusinessTask = true;
            }
        }
        return hasRunningBusinessTask;
    }

    public synchronized void onIdleMonitorTick() {
        Log.i(Log.TAG, "开始判断是否空闲满10分钟");
        if (aux5Switching) {
            return;
        }

        boolean hasTask = hasRunningBusinessTask();
        hasBusinessTask = hasTask;

        if (hasBusinessTask) {
            Log.i(Log.TAG, "当前为非空闲状态");
            if (!aux5Open) {
                openAux5();
            }

            resetIdleTimer();
            return;
        }

        if (!aux5Open) {
            Log.i(Log.TAG, "辅助开关5已经关闭");
            return;
        }

        idleSeconds += IDLE_ADD_SECONDS;

        if (idleSeconds < IDLE_LIMIT_SECONDS) {
            Log.i(Log.TAG, "当前为空闲状态，但未满10分钟");
            return;
        }

        if (!hasBusinessTask) {
            Log.i(Log.TAG, "空闲状态已满10分钟");
            closeAux5();
            resetIdleTimer();
        }
    }
    ///
}
