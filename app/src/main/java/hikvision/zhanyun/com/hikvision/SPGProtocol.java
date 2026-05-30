package hikvision.zhanyun.com.hikvision;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.renderscript.Float3;
import android.telephony.TelephonyManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import hikvision.zhanyun.com.hikvision.device.Device; /////
import hikvision.zhanyun.com.hikvision.utils.ByteUtils;
import hikvision.zhanyun.com.hikvision.utils.ConfigMergeUtil;
import hikvision.zhanyun.com.hikvision.utils.JsonUtils;
import hikvision.zhanyun.com.hikvision.utils.Log;
import hikvision.zhanyun.com.hikvision.utils.SettingsMergeUtil;
import hikvision.zhanyun.com.hikvision.utils.UpgradeFileUtils;
import hikvision.zhanyun.com.hikvision.utils.ZipUtils;
import lyh.Utils;

import static hikvision.zhanyun.com.hikvision.MainActivity.DATA_DIR;
import static lyh.Utils.FORMAT_DATETIME;
import static lyh.Utils.PERIOD_MINUTE;
import static lyh.Utils.PERIOD_SECOND;
import static lyh.Utils.TAG;
import static lyh.Utils.apkInstall;
import static lyh.Utils.appVersion;
import static lyh.Utils.bin2hex;
import static lyh.Utils.bin2str;
import static lyh.Utils.byteMerger;
import static lyh.Utils.currentDateTime;
import static lyh.Utils.deleteFile;
import static lyh.Utils.getApkVersion;
import static lyh.Utils.hex2bin;
import static lyh.Utils.hi;
import static lyh.Utils.hw;
import static lyh.Utils.lo;
import static lyh.Utils.restartApplication;
import static lyh.Utils.subBytes;

import com.alibaba.fastjson.JSONObject;

import org.json.JSONException;

//import com.hikvision.netsdk.BuildConfig;

/**
 * Created by ZY004Engineer on 2018/6/12.
 *
 * @author 陈焯辉
 * @version 1.0
 * @description 整理规约代码，按照《QCSG1205031-2020输电线路在线监测通信规约及信息交互规范》
 * @date 2025/05/10
 */

public class SPGProtocol {
    public enum FILE_TYPE {PHOTO, VIDEO;}

    public static final long UPLOAD_TIMEOUT = 1000 * 600 * 2;  // 20 分钟上传超时
    public static final long UPLOAD_INTERVAL = 50;  // 发送包需要间隔，不然太快UDP丢包太多
    public static final byte ORDER_00H = (byte) 0x00;
    public static final byte ORDER_01H = (byte) 0x01;
    public static final byte ORDER_02H = (byte) 0x02;
    public static final byte ORDER_03H = (byte) 0x03;
    public static final byte ORDER_04H = (byte) 0x04;
    public static final byte ORDER_05H = (byte) 0x05;
    public static final byte ORDER_06H = (byte) 0x06;
    public static final byte ORDER_07H = (byte) 0x07;
    public static final byte ORDER_08H = (byte) 0x08;
    public static final byte ORDER_09H = (byte) 0x09;
    public static final byte ORDER_0AH = (byte) 0x0A;
    public static final byte ORDER_0BH = (byte) 0x0B;
    public static final byte ORDER_0CH = (byte) 0x0C;
    public static final byte ORDER_0DH = (byte) 0x0D;
    public static final byte ORDER_0EH = (byte) 0x0E;
    public static final byte ORDER_21H = (byte) 0x21;
    public static final byte ORDER_22H = (byte) 0x22;
    public static final byte ORDER_25H = (byte) 0x25;
    public static final byte ORDER_26H = (byte) 0x26;
    public static final byte ORDER_27H = (byte) 0x27;
    public static final byte ORDER_29H = (byte) 0x29;
    public static final byte ORDER_2AH = (byte) 0x2A;
    public static final byte ORDER_2BH = (byte) 0x2B;
    public static final byte ORDER_2CH = (byte) 0x2C;
    public static final byte ORDER_2DH = (byte) 0x2D;
    public static final byte ORDER_2EH = (byte) 0x2E;
    public static final byte ORDER_30H = (byte) 0x30;
    public static final byte ORDER_31H = (byte) 0x31;
    public static final byte ORDER_32H = (byte) 0x32;
    public static final byte ORDER_33H = (byte) 0x33;
    public static final byte ORDER_34H = (byte) 0x34;
    public static final byte ORDER_35H = (byte) 0x35;
    public static final byte ORDER_36H = (byte) 0x36;
    public static final byte ORDER_37H = (byte) 0x37;
    public static final byte ORDER_38H = (byte) 0x38;
    public static final byte ORDER_39H = (byte) 0x39;
    public static final byte ORDER_3AH = (byte) 0x3A;
    public static final byte ORDER_3BH = (byte) 0x3B;
    public static final byte ORDER_3CH = (byte) 0x3C;
    public static final byte ORDER_40H = (byte) 0x40;
    public static final byte ORDER_41H = (byte) 0x41;
    public static final byte ORDER_42H = (byte) 0x42;
    public static final byte ORDER_43H = (byte) 0x43;
    public static final byte ORDER_44H = (byte) 0x44;
    public static final byte ORDER_45H = (byte) 0x45;
    public static final byte ORDER_46H = (byte) 0x46;
    public static final byte ORDER_47H = (byte) 0x47;
    public static final byte ORDER_48H = (byte) 0x48;
    public static final byte ORDER_60H = (byte) 0x60;
    public static final byte ORDER_61H = (byte) 0x61;
    public static final byte ORDER_62H = (byte) 0x62;
    public static final byte ORDER_63H = (byte) 0x63;
    public static final byte ORDER_64H = (byte) 0x64;
    public static final byte ORDER_65H = (byte) 0x65;
    public static final byte ORDER_66H = (byte) 0x66;
    public static final byte ORDER_67H = (byte) 0x67;
    public static final byte ORDER_68H = (byte) 0x68;
    public static final byte ORDER_69H = (byte) 0x69;
    public static final byte ORDER_6AH = (byte) 0x6A;
    public static final byte ORDER_70H = (byte) 0x70;
    public static final byte ORDER_71H = (byte) 0x71;
    public static final byte ORDER_72H = (byte) 0x72;
    public static final byte ORDER_73H = (byte) 0x73;
    public static final byte ORDER_74H = (byte) 0x74;
    public static final byte ORDER_75H = (byte) 0x75;
    public static final byte ORDER_76H = (byte) 0x76;
    public static final byte ORDER_81H = (byte) 0x81;
    public static final byte ORDER_82H = (byte) 0x82;
    public static final byte ORDER_83H = (byte) 0x83;
    public static final byte ORDER_84H = (byte) 0x84;
    public static final byte ORDER_85H = (byte) 0x85;
    public static final byte ORDER_86H = (byte) 0x86;
    public static final byte ORDER_87H = (byte) 0x87;
    public static final byte ORDER_88H = (byte) 0x88;
    public static final byte ORDER_89H = (byte) 0x89;
    public static final byte ORDER_8AH = (byte) 0x8A;
    public static final byte ORDER_8BH = (byte) 0x8B;
    public static final byte ORDER_8CH = (byte) 0x8C;
    public static final byte ORDER_8DH = (byte) 0x8D;
    public static final byte ORDER_8EH = (byte) 0x8E;
    public static final byte ORDER_8FH = (byte) 0x8F;
    public static final byte ORDER_90H = (byte) 0x90;
    public static final byte ORDER_91H = (byte) 0x91;
    public static final byte ORDER_92H = (byte) 0x92;
    public static final byte ORDER_93H = (byte) 0x93;
    public static final byte ORDER_94H = (byte) 0x94;
    public static final byte ORDER_95H = (byte) 0x95;
    public static final byte ORDER_96H = (byte) 0x96;
    public static final byte ORDER_97H = (byte) 0x97;
    public static final byte ORDER_98H = (byte) 0x98;
    public static final byte ORDER_99H = (byte) 0x99;
    public static final byte ORDER_9AH = (byte) 0x9A;
    public static final byte ORDER_9BH = (byte) 0x9B;
    public static final byte ORDER_9CH = (byte) 0x9C;
    public static final byte ORDER_9DH = (byte) 0x9D;  // 录像文件下载 /////
    public static final byte ORDER_9EH = (byte) 0x9E;  // 录像文件下载 /////
    public static final byte ORDER_A0H = (byte) 0xA0;
    public static final byte ORDER_A1H = (byte) 0xA1;
    public static final byte ORDER_A2H = (byte) 0xA2;
    public static final byte ORDER_A3H = (byte) 0xA3;
    public static final byte ORDER_A4H = (byte) 0xA4;
    public static final byte ORDER_A5H = (byte) 0xA5;
    public static final byte ORDER_A6H = (byte) 0xA6;
    public static final byte ORDER_A7H = (byte) 0xA7;
    public static final byte ORDER_A8H = (byte) 0xA8;
    public static final byte ORDER_A9H = (byte) 0xA9;
    public static final byte ORDER_AAH = (byte) 0xAA;
    public static final byte ORDER_B1H = (byte) 0xB1;
    public static final byte ORDER_B2H = (byte) 0xB2;
    public static final byte ORDER_B3H = (byte) 0xB3;
    public static final byte ORDER_B4H = (byte) 0xB4;
    public static final byte ORDER_B5H = (byte) 0xB5;
    public static final byte ORDER_B6H = (byte) 0xB6;
    public static final byte ORDER_B7H = (byte) 0xB7;
    public static final byte ORDER_B8H = (byte) 0xB8;
    private static final byte ORDER_CAH = (byte) 0xCA;
    private static final byte ORDER_CBH = (byte) 0xCB;
    private static final byte ORDER_CCH = (byte) 0xCC;
    private static final byte ORDER_CDH = (byte) 0xCD;
    private static final byte ORDER_CEH = (byte) 0xCE;
    private static final byte ORDER_CFH = (byte) 0xCF;  // 通用事件上报
    private static final byte ORDER_D0H = (byte) 0xD0;
    private static final byte ORDER_D1H = (byte) 0xD1;
    private static final byte ORDER_D2H = (byte) 0xD2;
    private static final byte ORDER_D4H = (byte) 0xD4; /////
    // 红外测温指令
    private static final byte ORDER_E0H = (byte) 0xE0;
    private static final byte ORDER_E1H = (byte) 0xE1;
    private static final byte ORDER_E2H = (byte) 0xE2;
    private static final byte ORDER_E3H = (byte) 0xE3;
    private static final byte ORDER_E4H = (byte) 0xE4;
    private static final byte ORDER_E5H = (byte) 0xE5;

    // 开机联络信息 00H
    private final byte[] START_CHAR = {0x68};
    private final byte[] END_CHAR = {0x16};
    private final byte[] VERSION = {0x03, 0x00};  // 当前规范版本号为V3.0
    private byte[] ERROR_FFFF_DOMAIN = new byte[]{(byte) 0xFF, (byte) 0xFF};
    // 终端休眠通知 0CH
    public String deviceID;

    private SPGPCallback listenerCallBack;

    // 判断发送数据与接收数据相同
    private byte[] mReceiveData;

    // 主站卡号
    public String sim;
    public String server;
    public int port;
    public String password;   // 设备里面保存的密码
    public String passcode;   // 设备上传数据时候的 密文认证 代码

    // 主站查询终端文件列表 71H
    private int fileNumber;   // 需传输的文件个数N //
    // 获取文件属性
    private List<String> fileNames = new ArrayList<>();  // 文件名集合
    private List<Integer> fileLengths = new ArrayList<>();  // 文件大小集合
    private List<String> fileTimes = new ArrayList<>();  // 文件生成时间集合
    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // 装置请求上送文件 73H
    public boolean uploading = false;              // 是否正在上传文件，防止多个文件冲突
    public boolean uploadSucceed = true;           // 文件上传结果，文件上传成功可立即上传下一个文件，否则间隔大一点避免破图
    public long uploadSucceedTime = 0;             // 183服务器有时会不停下发补包为0的0x87指令，会导致丢图片，所以发送图片成功后间隔几秒再发送累积图片
    private boolean uploadEcho = false;            // 发送文件上传请求后，是否收到回应？
    private boolean uploadEndEcho = false;         // 文件上传结束回应补包？
    private String uploadFilename;                 // 当前正在上传的文件名，用于补包处理使用
    private long uploadTimestamp;
    private int uploadChannel;
    private int uploadPreset;
    private long uploadStart = 0;
    private FILE_TYPE uploadType; /////

    private String upgradeAPPFileName = null;

    /////
    // 上传到时候单独设置，上传日志时间过长，如果在上传过程中，设备进行拍照后上传，会修改存在的uploadChannel和uploadFilename，所有修改名字。防止冲突
    private String uploadLogFilename;
    private int uploadLogChannel;
    /////

    private String fileTime;
    private int fileLength;

    private boolean isUpLocal = false;  // 保证单一上传图片
    private final int UPLOAD_IMAGE_PACK_DIVISOR = 256;
    public int MAX_UPLOAD_FILE_SIZE = 1400;
    private byte channelNum = 1;  // 通道号
    private byte preset = 0;  // 预置点
    public static boolean Logged = false;
    public static boolean TimeSynced = false;

    private File pictureFile;

    private Context context;
    private Handler mHandler = new Handler();
    private int repeatCountLoop;
    private byte[] fileNameByteData;
    public boolean isUpgrade = false;  // 是否正在升级
    private long latestUpgradeReceived;
    private long lastD4SendLogTime = 0;
    private String upgradeFileName = "test.apk";
    private int upgradeFileCount = 1;
    private byte upgradeType = 0;  // 扩展协议， 0 = 普通升级， 1 = 断点续传升级
    private String upgradeFilePath = MainActivity.DATA_DIR;
    private String logFilename = MainActivity.FILE_PATH + "logs.log";  // 日志文件名 /////
    private UploadRunnable repeatTiming = new UploadRunnable();
    boolean upgradePacketReady[] = null;
    RandomAccessFile fosUpgradeFile = null;
    int upgradePacketSize = 0;

    /**
     * 处理命令
     *
     * @param order 命令
     */
    public void handlerOrder(final byte order, byte[] data, boolean aiAccTest) { /////
        /////
        if (data == null || data.length < 10) {
            Log.w(Log.TAG, "指令处理失败：数据无效或长度不足");
            return;
        }
        /////

        listenerCallBack.cpuLock();
        this.mReceiveData = data;

        int dataLen = (data[8] & 0xFF) << 8 | data[9];  // 数据域长度
        byte[] frameData = subBytes(data, 1, data.length - 3);
        byte crc = Crc(frameData);
        if (crc != data[data.length - 2]) {
            Log.e(TAG, String.format("指令（%02XH）数据校验失败，非法数据或错误帧", order));
            if (order == (byte) 0xCB) {  // 升级包数据出错返回，等待重新下发
                return;
            }
        }
        if (order != (byte) 0xCB) {
            Log.i(Log.TAG, String.format("收到命令（%02XH） %d 字节：%s", order, data.length, bin2hex(data, true)));
        }

        String s = "";
        // 以下指令需要登录设备，预先调用登录
        switch (order) {
            case ORDER_00H:
                if (dataLen == 0) {
                    // 收到后台下发的00指令
                    doBootContactInfo(); /////
                    /////
                    if (aiAccTest) {
                        try {
                            listenerCallBack.runAI();  // 图像智能识别算法性能检测
                        } catch (IOException | JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    /////
                } else {
                    s += "开机";
                    Logged = true;
                    doSyncTime(); /////
                    listenerCallBack.onLoggedIn();
                }
                break;
            case ORDER_01H:
                s += "校时";
                doSyncTime(data);
                break;
            case ORDER_02H:
                s += "设置密码";
                doSetPassword(data);
                break;
            case ORDER_03H:
                s += "下发参数";
                doOnlineSetting(data);
                break;
            case ORDER_04H:
                break;
            case ORDER_05H:
                s += "心跳";
                listenerCallBack.onHeartBeatReceived();
                break;
            case ORDER_06H:
                s += "服务器地址设置";
                doChangeServer(data);
                break;
            case ORDER_07H:
                // 查询主站IP,端口号，卡号
                s += "查询服务器设置";
                doQueryServer();
                break;
            case ORDER_08H:
                s += "重启";
                doDeviceReboot(data);
                break;
            case ORDER_09H:
                s += "唤醒";
                doWakeup(data);
                break;
            case ORDER_0AH:
                s += "查询基本配置";
                doGetSettings();
                break;
            case ORDER_0BH:
                s += "功能查询";
                setFeatures(data);
                break;
            case ORDER_0CH:
//                listenerCallBack.doSleep();
                listenerCallBack.ptzControl(1, 18, 5); ///////
                echoBack(ORDER_0CH);
                break;
            case ORDER_0DH:
                s += "时间查询";
                doGetClock();
                break;
            case ORDER_0EH:
                s += "确认短信";
                doSMSConfirm(data);
                break;
            case ORDER_21H:
                /////
                s += "主站请求装置数据";
                doGetData(data);
                /////
                break;
            case ORDER_22H:
                // 上传导地线拉力及偏角数据（控制字：22H），参考规约第7.16节，目前无数据，预留扩展接口
                echoBack(ORDER_22H);
                break;
            case ORDER_25H:
                if (dataLen == 0) echoBack(ORDER_25H);
                break;
            case ORDER_26H:
                // 上传导线温度、电流数据（控制字：26H），参考规约第7.18节，目前无数据，预留扩展接口
                echoBack(ORDER_26H);
                break;
            case ORDER_27H:
                // 上传杆塔振动数据（控制字：27H），参考规约第7.19节，目前无数据，预留扩展接口
                echoBack(ORDER_27H);
                break;
            case ORDER_29H:
                // 上传舞动振幅频率数据（控制字：29H），参考规约第7.20节，目前无数据，预留扩展接口
                echoBack(ORDER_29H);
                break;
            case ORDER_2AH:
                if (dataLen == 0) echoBack(ORDER_2AH);
                break;
            case ORDER_2BH:
                // 上传导线微风振动数据（控制字：2BH），参考规约第7.22节，目前无数据，预留扩展接口
                echoBack(ORDER_2BH);
                break;
            case ORDER_2CH:
                // 上传综合防盗数据（控制字：2CH），参考规约第7.23节，目前无数据，预留扩展接口
                echoBack(ORDER_2CH);
                break;
            case ORDER_2DH:
                // 上传山火报警数据（控制字：2DH），参考规约第7.24节，目前无数据，预留扩展接口
                echoBack(ORDER_2DH);
                break;
            case ORDER_2EH:
                // 上传大风舞动报警数据（控制字：2EH），参考规约第7.25节，目前无数据，预留扩展接口
                echoBack(ORDER_2EH);
                break;
            case ORDER_30H:
                // 上传设备故障信息（控制字：30H），参考规约第7.26节，目前无数据，预留扩展接口
                break;
            case ORDER_31H:
                // 主站请求微风振动动态数据（控制字：31H），参考规约第7.27节，目前无数据，预留扩展接口
                break;
            case ORDER_32H:
                // 微风振动动态数据上送（控制字：32H），参考规约第7.28节，目前无数据，预留扩展接口
                echoBack(ORDER_32H);
                break;
            case ORDER_33H:
                // 微风振动动态数据上送结束标记（控制字：33H），参考规约第7.29节，目前无数据，预留扩展接口
                break;
            case ORDER_34H:
                // 微风振动动态数据补包下发（控制字：34H），参考规约第7.30节，目前无数据，预留扩展接口
                break;
            case ORDER_35H:
                // 主站请求舞动动态数据（控制字：35H），参考规约第7.31节，目前无数据，预留扩展接口
                echoBack(ORDER_35H);
                break;
            case ORDER_36H:
                // 舞动动态数据上送（控制字：36H），参考规约第7.32节，目前无数据，预留扩展接口
                break;
            case ORDER_37H:
                // 舞动动态数据上送结束标记（控制字：37H），参考规约第7.33节，目前无数据，预留扩展接口
                break;
            case ORDER_38H:
                // 舞动动态数据补包下发（控制字：38H），参考规约第7.34节，目前无数据，预留扩展接口
                break;
            case ORDER_39H:
                // 主站请求拉力动态数据（控制字：39H），参考规约第7.35节，目前无数据，预留扩展接口
                break;
            case ORDER_3AH:
                // 拉力动态数据上送（控制字：3AH），参考规约第7.36节，目前无数据，预留扩展接口
                break;
            case ORDER_3BH:
                // 拉力及偏角动态数据上送结束标记（控制字：3BH），参考规约第7.37节，目前无数据，预留扩展接口
                break;
            case ORDER_3CH:
                // 拉力及偏角动态数据补包下发（控制字：3CH），参考规约第7.38节，目前无数据，预留扩展接口
                break;
            case ORDER_40H:  // 7.45 上传装置流量数据使用情况（控制字：40H）
                s += "流量统计";
                doReportTrafficUsage(dataLen > 0);
                break;
            case ORDER_41H:
                // 上传污秽数据（控制字：41H），参考规约第7.39节，目前无数据，预留扩展接口
                break;
            case ORDER_42H:
                // 上传导线弧垂数据（控制字：42H），参考规约第7.40节，目前无数据，预留扩展接口
                break;
            case ORDER_43H:
                // 上传电缆温度数据（控制字：43H），参考规约第7.41节，目前无数据，预留扩展接口
                break;
            case ORDER_44H:
                // 上传电缆护层接地电流数据（控制字：44H），参考规约第7.42节，目前无数据，预留扩展接口
                break;
            case ORDER_45H:
                // 上传电缆故障定位数据（控制字：45H），参考规约第7.43节，目前无数据，预留扩展接口
                break;
            case ORDER_46H:
                // 上传电缆局放数据（控制字：46H），参考规约第7.44节，目前无数据，预留扩展接口
                break;
            case ORDER_47H:
                // 上传电缆局放谱图数据（控制字：47H），参考规约第7.45节，目前无数据，预留扩展接口
                break;
            case ORDER_48H:
                s += "电量信息";
                // 数据长度为0表示主站主动请求上传电量数据
                if (dataLen == 0 && listenerCallBack != null) {
                    Log.i(Log.TAG, "主站查询电量");
                    doReportBattery(listenerCallBack.getBatterInfo());
                }
                break;
            case ORDER_60H:
                // 主站设置故障测距终端参数（控制字：60H），参考规约第7.48节，目前无数据，预留扩展接口
                break;
            case ORDER_61H:
                // 上传故障测距终端工况数据（控制字：61H），参考规约第7.50节，目前无数据，预留扩展接口
                break;
            case ORDER_62H:
                // 终端装置向主站请求上传工频故障波形数据（控制字：62H），参考规约第7.51节a)，目前无数据，预留扩展接口
                break;
            case ORDER_63H:
                // 上传工频故障波形数据（控制字：63H），参考规约第7.51节b)，目前无数据，预留扩展接口
                break;
            case ORDER_64H:
                // 终端装置向主站请求上传故障行波波形数据（控制字：64H），参考规约第7.52节a)，目前无数据，预留扩展接口
                break;
            case ORDER_65H:
                // 上传故障行波波形数据（控制字：65H），参考规约第7.52节b)，目前无数据，预留扩展接口
                break;
            case ORDER_66H:
                // 故障行波波形数据上传结束标志（控制字：66H），参考规约第7.52节c)，目前无数据，预留扩展接口
                break;
            case ORDER_67H:
                // 主站向终端发送故障行波波形数据补包（控制字：67H），参考规约第7.52节d)，目前无数据，预留扩展接口
                break;
            case ORDER_68H:
                // 工频故障波形数据上传结束标志（控制字：68H），参考规约第7.51节c)，目前无数据，预留扩展接口
                break;
            case ORDER_69H:
                // 主站向终端发送工频故障波形数据补包（控制字：69H），参考规约第7.51节d)，目前无数据，预留扩展接口
                break;
            case ORDER_6AH:
                // 主站查询故障测距终端参数（控制字：6AH），参考规约第7.49节，目前无数据，预留扩展接口
                break;
            case ORDER_70H:
                // 南网规约协议扩展数据通讯（控制字：70H），参考规约第7.53节，目前无数据，预留扩展接口
                break;
            case ORDER_71H:
                s += "文件列表";
                fileNumber = data[10];
                doSetFileNameList(); /////
                break;
            case ORDER_72H:
                /**
                 * 主站请求装置上送文件（控制字：72H），参考规约第7.55节。
                 * 主站下发上传指令后，设备开始准备并响应上传流程。
                 */
                s += "文件上传请求";
                mHandler.removeCallbacks(repeatTiming);
                sendPack(ORDER_72H, data, null);  // 回复确认
                repeatCountLoop = 0;
                doUploadingFileRequests();  // 启动上传准备流程 /////
                break;
            case ORDER_73H:
                s += "文件上传";
                repeatCountLoop = 0;
                mHandler.removeCallbacks(repeatTiming);
                // 接收主站回应，进入正式上传文件阶段
                doUploadingFile();  // 进入文件内容上传 /////
                break;
            case ORDER_74H:
                break;
            case ORDER_75H:
                break;
            case ORDER_76H:
                s += "文件补包";
                mHandler.removeCallbacks(repeatTiming);
                doHandlerTonicPacks(data); /////
                break;
            case ORDER_81H:
                s += "图像参数设置";
                doPhotoSettings(data);
                break;
            case ORDER_82H:
                s += "设置定时拍照";
                doSetTakePhotoTimetable(data); /////
                break;
            case ORDER_83H:
                s += "请求拍照";
                doTakePhoto(data);
                break;
            case ORDER_84H:
                s += "图片上报";
                // 终端主动发起，服务响应到了，设置标志位，可以开始图片上报
                doUploadFile(data, ORDER_85H, ORDER_86H);
                break;
            case ORDER_85H:  // 图片数据包，服务器无返回
                break;
            case ORDER_86H:
                break;
            case ORDER_87H:
                s += "补包请求";
                doMissingPack(data, ORDER_85H, ORDER_86H);
                break;
            case ORDER_88H:
                s += "云台控制";
                doPTZ(data);
                break;
            case ORDER_89H:
                s += "开始直播";
                doStartLive(data);
                break;
            case ORDER_8AH:
                s += "停止直播";
                doStopLive(data);
                break;
            case ORDER_8BH:
                s = "查询拍照时间表";
                doGetPhotoTimeTable(data);
                break;
            case ORDER_8CH:
                s += "设置编码参数";
                doSetCodec(data);
                break;
            case ORDER_8DH:
                s += "查询编码参数";
                doGetCodec(data[10], data[11]);
                break;
            case ORDER_8EH:
                s += "设置OSD";
                setOSDConfig(data);
                break;
            case ORDER_8FH:
                s += "查询OSD";
                doGetOSD(data[10]);
                break;
            case ORDER_90H:
                s += "设置录像策略";
                doSetVideoTimeTable(data);
                break;
            case ORDER_91H:
                s += "查询录像策略";
                doGetVideoTimeTable(data[10], data[11]);
                break;
            case ORDER_92H:
                s += "录像状态";
                doQueryVideoState(data[10], data[11]); /////
                break;
            case ORDER_93H:
                s += "录制小视频";
                doTakeVideo(data);
                break;
            case ORDER_94H:
                s += "视频上报";
                doUploadFile(data, ORDER_95H, ORDER_96H);
                break;
            case ORDER_95H:
                break;
            case ORDER_96H:
                break;
            case ORDER_97H:
                s += "视频补包";
                doMissingPack(data, ORDER_95H, ORDER_96H);
                break;
            case ORDER_98H:
                s += "文件数量";
                doFindVideoFile(data); /////
                break;
            case ORDER_99H:
                s += "列出文件";
                doFindVideoFileList(data); /////
                break;
            case ORDER_9AH:
                s += "开始回放";
                doPlayback(data);
                break;
            case ORDER_9BH:
                s += "回放控制";
                doPlaybackControl(data); /////
                break;
            case ORDER_9CH:
                s += "停止回放";
                doStopPlayBack(data); /////
                break;
            case ORDER_9DH:
                s += "录像下载";
                doVideoDownload(data);
                break;
            case ORDER_9EH:
                s += "录像下载取消";
                doVideoDownloadCancel(data);
                break;
            case ORDER_A0H:
                break;
            case ORDER_A1H:
                break;
            case ORDER_A2H:
                // 主站请求与终端进行语音广播（控制字：A2H），参考规约第7.62.1节，目前未实现，预留扩展接口
                s += "开始语音广播";
                doStartVoiceBroadcast(data); /////
                echoBack(ORDER_22H);
                break;
            case ORDER_A3H:
                // 主站请求与终端断开语音广播（控制字：A3H），参考规约第7.62.2节，目前未实现，预留扩展接口
                s += "断开语音广播";
                doStopVoiceBroadcast(data); /////
                break;
            case ORDER_A4H:
                s += "智能分析参数配置";
                doSetAIParameter(data);
                break;
            case ORDER_A5H:
                s += "智能分析参数查询";
                doGetAIParameter(data);
                break;
            case ORDER_A6H:
                s += "智能分析类型查询";
                doGetAIAbility(data); /////
                break;
            case ORDER_A7H:
                if (dataLen == 0) {
                    s += "告警历史查询";
                    doAIHistory(data);
                }
                break;
            case ORDER_A8H:
                s += "联动参数配置";
                doSetAIActionSettings(data);
                break;
            case ORDER_A9H:
                s += "联动参数查询";
                doGetAIActionSettings(data);
                break;
            case ORDER_AAH:
                // 已弃用
                s += "AI告警响应";
                doAlertResponse(data);
                break;
            case ORDER_B1H:
                s += "3D控球";
                doStart3DBallControl(data); /////
                break;
            case ORDER_B2H:
                s += "设置巡航";
                doSetCruiseConfig(data); /////
                break;
            case ORDER_B3H:
                s += "查询巡航";
                doGetCruise(data[10]);
                break;
            case ORDER_B4H:
                s += "装置电源/视频关闭通知"; ///////
                doCamOff(data);
                break;
            case ORDER_B5H:
                s += "设置巡检参数";
                doSetInspectionParameters(data); /////
                break;
            case ORDER_B6H:
                s += "查询巡检参数";
                doQueryInspectionParameters(toInt(data[10])); /////
                break;
            case ORDER_B7H:
                s += "设置巡检策略";
                doSetCheckLineSchedule(data); /////
                break;
            case ORDER_B8H:
                s += "查询巡检策略";
                doGetCheckLineSchedule(toInt(data[10])); /////
                break;
            case ORDER_CAH:
                s += "请求升级";
                doUpgradeInformation(data); /////
                break;
            case ORDER_CBH:
                s += "升级数据包";
                doReceiveUpgradeFile(data); /////
                break;
            case ORDER_CCH:
                s += "升级结束";
                doReceiveUpgradeEnd(data); /////
                break;
            case ORDER_CEH:
                s += "升级结果";
                doReceiveUpgradeResult(data); /////
                break;
            case ORDER_CFH:
                s += "位置信息";
                //doReportBds(data); /////
                break;
            case ORDER_D0H:
                s += "通用参数设置";
                doSetGeneralParameters(data); /////
                break;
            case ORDER_D1H:
                s += "通用参数查询";
                doGetGeneralParameters(data); /////
                break;
            case ORDER_D2H:
                s += "SIM卡查询";
                doGetSimCardInfo(data); /////
                break;
            /////
            case ORDER_D4H:
                s += "日志查询";
                doUploadLog(data);
                break;
            /////
            case ORDER_E0H:
                s += "手动红外测温区域设置";
                doSetManualIrRegion(data); /////
                break;
            case ORDER_E1H:
                s += "红外测温区域设置";
                doSetIrRegionParam(data); /////
                break;
            case ORDER_E2H:
                s += "红外测温区域查询";
                doGetIrRegionParam(data); /////
                break;
            case ORDER_E3H:
                s += "红外测温数据上报响应";
                doReportIrFeedback(data); /////
                break;
            case ORDER_E4H:
                s += "测温传感器参数设置";
                doSetIrSensorParam(data); /////
                break;
            case ORDER_E5H:
                s += "测温传感器参数获取";
                doGetIrSensorParam(); /////
                break;
        }
        if (order != ORDER_CBH)
            Log.d(Log.TAG, "命令处理结束：" + s);
        listenerCallBack.tryCpuUnlock();
    }

    /**
     * 开机联络信息（控制字：00H），见规约第7.1节
     * 装置上电或重启后主动上送版本号等联络信息。
     * 若主站未应答，每隔1分钟重复发送一次。
     */
    public void doBootContactInfo() { /////
        byte[] buf = result(ORDER_00H, null, VERSION);
        if (buf == null) return;
        String versionStr = "V" + (VERSION[0] & 0xFF) + "." + (VERSION[1] & 0xFF);
        Log.w(Log.TAG, "设备发送开机信息，规范版本号：" + versionStr);
        listenerCallBack.sendData(buf);
    }

    /**
     * 开机后主动请求主站校时（控制字：01H），参考规约第7.2节。
     * 若设备尚未完成时间同步，则主动发送请求。
     */
    public void doSyncTime() { /////
        // 主动校时
        if (!TimeSynced) {
            sendPack(ORDER_01H, null, new byte[]{});
            Log.w(Log.TAG, "设备主动请求主站校时");
        }
    }

    /**
     * 校时（控制字：01H），参考规约第7.2节。
     * 提取数据帧中的时间信息，并设置本地时钟。
     *
     * @param raw 原始接收数据帧
     */
    protected void doSyncTime(byte[] raw) {
        if (raw == null || raw.length < 16) {
            Log.w(Log.TAG, "校时失败：接收数据无效或长度不足");
            sendPack(ORDER_01H, null, ERROR_FFFF_DOMAIN);
            return;
        }

        int year = (raw[10] & 0xff) + 2000;
        int month = raw[11] & 0xff;
        int day = raw[12] & 0xff;
        int hour = raw[13] & 0xff;
        int minute = raw[14] & 0xff;
        int second = raw[15] & 0xff;
        Log.i(Log.TAG, String.format("主站校时，服务器时间：%d-%02d-%02d %02d:%02d:%02d，本地时间：%s",
                year, month, day, hour, minute, second, currentDateTime()));
        sendPack(ORDER_01H, raw, null);  // 原命令返回确认

        TimeSynced = true;
        listenerCallBack.setClock(year, month, day, hour, minute, second);
    }

    /**
     * 设置装置密码（控制字：02H），参考规约第7.3节。
     * 比对当前密码，若正确则设置新密码并返回确认；否则返回错误信息。
     * 数据域结构（8字节）：前4字节为旧密码，后4字节为新密码。
     *
     * @param mReceiveData 原始接收数据帧
     */
    protected void doSetPassword(byte[] mReceiveData) {
        /////
        if (mReceiveData == null || mReceiveData.length < 18) {
            Log.w(Log.TAG, "设置密码失败：接收数据无效或长度不足");
            sendPack(ORDER_02H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String pass = getReceivePassword(mReceiveData);  // 前4字节
        if (pass.equals(this.password)) {
            // 后4字节为新密码，从索引14开始
            byte[] newPassword = new byte[]{mReceiveData[14], mReceiveData[15], mReceiveData[16], mReceiveData[17]};
            this.password = new String(newPassword);

            listenerCallBack.setPassword(this.password);
            sendPack(ORDER_02H, mReceiveData, null);
            Log.i(Log.TAG, "密码设置成功，新密码：" + this.password);
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", pass, this.password));
            sendPack(ORDER_02H, null, ERROR_FFFF_DOMAIN);
        }
    }

    /**
     * 主站下发参数配置（控制字：03H），参考规约第7.4节。
     * 校验密码后，提取采样间隔、休眠时间、在线时长、重启时间点等参数。
     * 数据域字段：
     *  - [10~13] 密码
     *  - [14] 心跳间隔（分钟）
     *  - [15~16] 采样间隔（分钟）
     *  - [17~18] 休眠时长（分钟）
     *  - [19~20] 在线时长（分钟）
     *  - [21~23] 硬件重启时间（日、时、分）
     *  - [24~27] 密文验证码
     *
     * @param raw 原始接收帧数据
     */
    protected void doOnlineSetting(byte[] raw) {
        /////
        if (raw == null || raw.length < 28) {
            Log.w(Log.TAG, "参数配置失败：接收数据无效或长度不足");
            sendPack(ORDER_03H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String password = getReceivePassword(raw);

        // 服务器下发的两个密码相同，才试图去修改密码
        if (password.equals(this.password)) {
            Settings.OnlineCfg cfg = new Settings.OnlineCfg();
            cfg.heart = raw[14];
            cfg.sample = (short) getValue(raw, 15, 2);  // 采样间隔
            cfg.sleep = (short) getValue(raw, 17, 2);  // 休眠间隔
            cfg.online = (short) getValue(raw, 19, 2);  // 在线时长
            cfg.day = raw[21];  // 重启日
            cfg.hour = raw[22];  // 重启时
            cfg.min = raw[23];  // 重启分
            String newPasscode = new String(new byte[]{raw[24], raw[25], raw[26], raw[27]});  // 密文认证

            this.passcode = newPasscode;
            listenerCallBack.setOnlineConfig(cfg, newPasscode);
            sendPack(ORDER_03H, raw, null);
            Log.i(Log.TAG, "主站参数配置完成，已更新运行参数与密文认证");
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_03H, null, ERROR_FFFF_DOMAIN);
        }
    }

    /**
     * 装置心跳信息（控制字：05H），参考规约第7.5节。
     * 数据域共8字节，包含设备时间、信号强度与电池电压。
     * 数据域结构：
     *  - [0~5] 年、月、日、时、分、秒
     *  - [6]   当前无线信号强度百分比，有效值为（00H-64H）0%-100%
     *  - [7]   当前蓄电池输出电压，装置将所测数值乘以10
     *
     * @param info 心跳信息对象，包括时间、信号强度、电压
     */
    public void doHeartBeat(Settings.HeartBeat info) {

        byte[] dataDomain = new byte[]{info.year, info.month, info.day, info.hour, info.min, info.sec, info.signal, info.voltage};
        sendPack(ORDER_05H, null, dataDomain);
        Log.i(Log.TAG, String.format("发送心跳包，时间：%d-%02d-%02d %02d:%02d:%02d，信号：%d%%，电压：%.1fV",
                info.year + 2000, info.month, info.day, info.hour, info.min, info.sec,
                info.signal, (info.voltage & 0xFF) / 10.0));
    }

    /**
     * 更改主站IP地址、端口号和卡号（控制字：06H），参考规约第7.6节。
     * 验证密码后，若主站IP、端口和卡号两次一致，则更改当前连接信息。
     * 数据域结构：
     * - [10~13] 密码
     * - [14~17] 主站IP1
     * - [18~19] 端口号1（采用大端模式）
     * - [20~23] 主站IP2
     * - [24~25] 端口号2（采用大端模式）
     * - [26~31] 主站卡号1（F+11位手机号，BCD编码）
     * - [32~37] 主站卡号2
     *
     * @param raw 原始接收数据帧
     */
    private void doChangeServer(byte[] raw) {
        /////
        if (raw == null || raw.length < 38) {
            Log.w(Log.TAG, "主站地址变更失败：接收数据无效或长度不足");
            sendPack(ORDER_06H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String password = getReceivePassword(raw);
        byte[] s1 = new byte[]{raw[14], raw[15], raw[16], raw[17]};
        byte[] p1 = new byte[]{raw[18], raw[19]};
        byte[] s2 = new byte[]{raw[20], raw[21], raw[22], raw[23]};
        byte[] p2 = new byte[]{raw[24], raw[25]};
        byte[] c1 = new byte[]{raw[26], raw[27], raw[28], raw[29], raw[30], raw[31]};
        byte[] c2 = new byte[]{raw[32], raw[33], raw[34], raw[35], raw[36], raw[37]};

        if (!this.password.equals(password)) {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_06H, null, ERROR_FFFF_DOMAIN);
        } else if (Arrays.equals(s1, s2) && Arrays.equals(p1, p2) && Arrays.equals(c1, c2)) {
            // 更改服务器信息
            String server = String.valueOf(toInt(raw[14]) + "." + toInt(raw[15]) + "." + toInt(raw[16]) + "." + toInt(raw[17]));
            int port = ((raw[18] & 0xff) << 8) | (raw[19] & 0xFF);
            sim = bin2hex(c1, false);
            sendPack(ORDER_06H, raw, null);
            listenerCallBack.changeServer(server, port);
            Log.i(Log.TAG, String.format("主站地址更新成功，IP：%s，端口：%d，卡号：%s", server, port, sim));
        } else {
            sendPack(ORDER_06H, null, new byte[]{0, 0});
            Log.i(Log.TAG, "主站地址变更失败：两组 IP/端口/卡号 不一致");
        }
    }

    /**
     * 查询主站IP地址、端口号和卡号（控制字：07H），参考规约第7.7节。
     * 返回当前主站 IP、端口和 SIM 卡号（12字节，BCD码）。
     * 若 SIM 卡号不足 12 位，则补 F；超出则截断。
     */
    private void doQueryServer() {
        String[] ips = this.server.split("\\.");

        byte[] dataDomain;
        // 服务器要求必须12个字节，否则会出错
        String sim = this.sim;
        if (sim.length() > 12)
            sim = sim.substring(0, 12);
        if (sim.length() < 12)
            sim = Collections.nCopies(12 - sim.length(), 'F') + sim;
        if (ips.length == 4) {
            dataDomain = byteMerger(new byte[]{
                    (byte) Integer.parseInt(ips[0]), (byte) Integer.parseInt(ips[1]), (byte) Integer.parseInt(ips[2]), (byte) Integer.parseInt(ips[3]),
                    hi(lo(port)), lo(lo(port))
            }, hex2bin(sim));
            Log.i(Log.TAG, "查询主站信息成功");
        } else {
            Log.w(Log.TAG, "查询主站信息失败：IP格式错误");
            dataDomain = ERROR_FFFF_DOMAIN;
        }

        sendPack(ORDER_07H, null, dataDomain);
    }

    /**
     * 装置重启（控制字：08H），参考规约第7.8节。
     * 验证密码成功后执行本地重启，并上送应答帧。
     *
     * @param mReceiveData 原始接收数据帧
     */
    protected void doDeviceReboot(byte[] mReceiveData) {
        /////
        if (mReceiveData == null || mReceiveData.length < 14) {
            Log.w(Log.TAG, "装置复位失败：接收数据无效或长度不足");
            sendPack(ORDER_08H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String password = getReceivePassword(mReceiveData);
        if (password.equals(this.password)) {
            sendPack(ORDER_08H, mReceiveData, null);
            Log.i(Log.TAG, "主站发起重启指令，密码验证通过，设备准备重启");
            listenerCallBack.rebootDevice();
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_08H, null, ERROR_FFFF_DOMAIN);
        }
    }

    /**
     * 短信唤醒（控制字：09H），参考规约第7.9节。
     * 若密码验证通过，则执行唤醒回调并回送应答帧。
     *
     * @param data 接收到的原始帧数据
     */
    private void doWakeup(byte[] data) {
        /////
        if (data == null || data.length < 14) {
            Log.w(Log.TAG, "短信唤醒失败：接收数据无效或长度不足");
            return;
        }
        /////

//        String password = getReceivePassword(data);
//        if (password.equals(this.password)) {
//            echoBack(ORDER_09H);
//            listenerCallBack.wakeup();
//        } else {
//            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
//        }
        /////
        echoBack(ORDER_09H);
        listenerCallBack.wakeup();
        Log.i(Log.TAG, "主站发起唤醒，设备已唤醒并回送确认");
        /////
    }

    /**
     * 查询装置配置参数（控制字：0AH），参考规约第7.10节。
     * 获取当前设备配置参数，并打包返回主站。
     */
    private void doGetSettings() {
        Settings.Parameters para = listenerCallBack.getConfigurationParameters();
        sendPack(ORDER_0AH, null, para.toBytes());
        Log.i(Log.TAG, "主站查询装置配置参数，已返回当前设置");
    }

    /**
     * 装置功能配置（控制字：0BH），参考规约第7.11节。
     * 验证密码后，调用回调接口设置设备功能参数。
     * 注意：规约未要求对该指令进行原路返回。
     *
     * @param mReceiveData 原始接收数据帧
     */
    private void setFeatures(byte[] mReceiveData) {
        /////
        if (mReceiveData == null || mReceiveData.length < 14) {
            Log.w(Log.TAG, "功能配置失败：接收数据无效或长度不足");
            return;
        }
        /////

        String password = getReceivePassword(mReceiveData);
        if (password != null && password.equals(this.password)) {
            Settings.Features features = new Settings.Features();
            listenerCallBack.setFeatures(features);
            Log.i(Log.TAG, "主站配置功能参数成功，已生效");
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
        }
    }

    /**
     * 装置休眠通知（控制字：0CH），参考规约第7.12节。
     * 用于在装置进入休眠前通知主站当前状态。此操作可由外部业务调用触发。
     *
     * @param channel 通道号（预留参数，当前规约未定义数据域）
     */
    public void sleep(int channel) {
        sendPack(ORDER_0CH, null, null);
        Log.i(Log.TAG, String.format("设备进入休眠状态，已发送休眠通知（通道%d）", channel));
    }

    /**
     * 查询装置时间（控制字：0DH），参考规约第7.13节。
     * 获取当前系统时间，并将其以数据帧形式返回主站。
     * 数据域结构（6字节）：
     *  - [0] 年（减去 2000）
     *  - [1] 月
     *  - [2] 日
     *  - [3] 时
     *  - [4] 分
     *  - [5] 秒
     */
    public void doGetClock() {
        // 查询时间
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR) - 2000;
        int month = calendar.get(Calendar.MONTH) + 1;  // 注意：Java 月份从0开始
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);

        byte[] dataDomain = new byte[]{(byte) year, (byte) month, (byte) day, (byte) hour, (byte) minute, (byte) second};
        sendPack(ORDER_0DH, null, dataDomain);
        Log.i(Log.TAG, String.format("主站查询时间，已返回当前时间：%d-%02d-%02d %02d:%02d:%02d",
                year + 2000, month, day, hour, minute, second));
    }

    /**
     * 发送确认短信（控制字：0EH），参考规约第7.14节。
     * 验证密码后，解析数据域中 SIM 卡号，并向该号码发送确认短信。
     * 数据域结构：
     * - [10~13] 密码
     * - [14~19] SIM 卡号（6字节，BCD码，前导F需剔除）
     *
     * @param raw 原始接收数据帧
     */
    private void doSMSConfirm(byte[] raw) {
        /////
        if (raw == null || raw.length < 20) {
            Log.w(Log.TAG, "短信确认失败：接收数据无效或长度不足");
            sendPack(ORDER_0EH, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String password = getReceivePassword(raw);

        if (!this.password.equals(password)) {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_0EH, null, ERROR_FFFF_DOMAIN);
        } else {
            sendPack(ORDER_0EH, raw, null);
            // 解析 SIM 卡号（6字节 BCD 编码，去除 F）
            byte[] c1 = new byte[]{raw[14], raw[15], raw[16], raw[17], raw[18], raw[19]};
            String sim = bin2hex(c1, false);
            sim = sim.replace("F", "");
            listenerCallBack.sendSMS(sim, this.deviceID);
            Log.i(Log.TAG, String.format("主站要求短信确认，已向号码 %s 发送短信（设备ID：%s）", sim, this.deviceID));
        }
    }

    /////
    /**
     * 主站请求装置数据（控制字：21H），参考规约第7.15节。
     * 如果主站发送0字节，上传未成功上传的历史数据，包含历史照片，若装置无历史数据则不上传。
     * 如果主站发送2字节BBBBH，装置立刻采集所有数据（图片除外），完成采集后立刻上传。该次采样不影响原设定采集间隔的执行。
     * 数据域结构：
     * - [10~13] 密码
     * - [14~15] 内容
     *
     * @param raw 原始接收数据帧
     */
    public void doGetData(byte[] raw) {
        if (raw == null || raw.length < 16) {
            Log.w(Log.TAG, "主站请求装置数据失败：接收数据无效或长度不足");
            sendPack(ORDER_21H, null, ERROR_FFFF_DOMAIN);
            return;
        }

        String password = getReceivePassword(raw);

        if (!this.password.equals(password)) {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_21H, null, ERROR_FFFF_DOMAIN);
        } else {
            if (mReceiveData.length == 0) {
                Log.i(Log.TAG, String.format("主站请求装置数据，装置无历史数据，不上传"));
            } else if (mReceiveData.length == 2 && raw[14] == (byte) 0xBB && raw[15] == (byte) 0xBB) {
                listenerCallBack.getAllData();  // 采集并上传所有数据
                Log.i(Log.TAG, String.format("主站请求装置数据，已采集并上传所有数据"));
            } else {
                Log.e(Log.TAG, "主站请求装置数据错误");
            }
        }
    }
    /////

    /**
     * 上传气象数据（控制字：25H），参考规约第7.17节。
     * 数据包括温度、湿度、风速、风向、雨量、气压、日照等多项参数。
     * 所有字段按规约要求编码为定长字节。
     * | 字段           | 长度 | 单位      | 说明                    |
     * | -----------    | -- | -------- | -------------------    |
     * | 密文认证        | 4B | -         | ASCII 编码              |
     * | 帧标识          | 1B | -        | 固定 1                  |
     * | 包数           | 1B | -         | 固定 1                  |
     * | 时间戳          | 6B | -        | aero.time.bytes        |
     * | 温度           | 2B | \*10+500  | 实际温度 = (值 - 500)/10 |
     * | 湿度           | 1B | %         |                        |
     * | 风速           | 2B | \*10      |                        |
     * | 风向           | 2B | °         |                        |
     * | 雨量           | 2B | \*100     |                        |
     * | 气压           | 2B | hPa       |                        |
     * | 日照强度        | 2B | -        |                        |
     * | 1min平均风速/向  | 4B | \*10 / ° |                        |
     * | 10min平均风速/向 | 4B | \*10 / ° |                        |
     * | 10min最大风速   | 2B | \*10     |                        |
     *
     * @param aero 气象数据对象（Settings.AeroInfo）
     */
    public void doReportAero(Settings.AeroInfo aero) {
        Log.d(Log.TAG, "上报气象数据：" + aero);

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            bos.write(this.passcode.getBytes());  // 密文认证
            bos.write(1);  // 帧标识（固定为1）
            bos.write(1);  // 包数（固定为1）
            bos.write(aero.time.bytes);  // 时间戳（6字节）
            // 写入气象字段
            dos.writeShort((int) (aero.Temp * 10 + 500));           // 温度 * 10 + 500
            dos.write((int) aero.Humidity);                         // 湿度 %
            dos.writeShort((int) (aero.WindSpeed * 10));            // 风速 * 10
            dos.writeShort(aero.WindDirection);                     // 风向 °
            dos.writeShort((int) (aero.RainFall * 100));            // 雨量 * 100 mm/h
            dos.writeShort((int) (aero.AtomosPress));               // 气压 hPa
            dos.writeShort((int) aero.Sunshine);                    // 日照强度
            dos.writeShort((int) (aero.WindSpeedByMin * 10));       // 1分钟平均风速 * 10
            dos.writeShort(aero.WindDirectionByMin);                // 1分钟平均风向
            dos.writeShort((int) (aero.WindSpeedBy10Min * 10));     // 10分钟平均风速 * 10
            dos.writeShort(aero.WindDirectionBy10Min);              // 10分钟平均风向
            dos.writeShort((int) (aero.MaxWindSpeedBy10Min * 10));  // 10分钟最大风速 * 10

            byte[] dataDomain = bos.toByteArray();
            dos.close();
            bos.close();
            sendPack(ORDER_25H, null, dataDomain);
            Log.i(Log.TAG, "气象数据上报完成");
        } catch (Exception e) {
            Log.e(Log.TAG, "上报气象信息错误：" + e.getMessage());
        }
    }

    /**
     * 上传杆塔倾斜数据（控制字：2AH），参考规约第7.21节。
     * 最多支持两路陀螺仪，每路包含 X/Y 轴角度，单位：°。
     * 上报时将角度值 *100 转换为 short（2 字节）上传。
     * 数据域结构：
     * - [0~3]    密文认证（4字节）
     * - [4]      帧标识（固定 1）
     * - [5]      包数（固定 1）
     * - [6~11]   时间戳（6字节）
     * - [12~...] 1号 X/Y，2号 X/Y，各2字节（共8字节）
     *
     *  @param angleXYZ 陀螺仪角度数据
     */
    public void doReportGyroInfo(Vector<Float3> angleXYZ){ /////
        if (angleXYZ.isEmpty())
            return;
        Log.i(Log.TAG, "上报陀螺仪X、Y、Z轴角度：");
        for (Float3 angle : angleXYZ) {
            Log.i(Log.TAG, String.format("\t\t（%f, %f, %f）", angle.x, angle.y, angle.z));
        }

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(this.passcode.getBytes());  // 密文认证
            bos.write(1);  // 帧标识
            bos.write(1);  // 包数
            Settings.TimeRecord currData = new Settings.TimeRecord(System.currentTimeMillis());
            bos.write(currData.bytes);  // 时间戳
            // 写入陀螺仪角度（最多两路 X/Y，每个 short 类型，*100）
            bos.write(ShortToStreamByte((short)(angleXYZ.get(0).x * 100)));
            bos.write(ShortToStreamByte((short)(angleXYZ.get(0).y * 100)));
            if (angleXYZ.size() == 1){
                bos.write(ShortToStreamByte((short)(angleXYZ.get(0).x * 100)));
                bos.write(ShortToStreamByte((short)(angleXYZ.get(0).y * 100)));
            } else {
                bos.write(ShortToStreamByte((short)(angleXYZ.get(1).x * 100)));
                bos.write(ShortToStreamByte((short)(angleXYZ.get(1).y * 100)));
            }
            byte[] dataDomain = bos.toByteArray();
            bos.close();
            sendPack(ORDER_2AH, null, dataDomain);
            Log.i(Log.TAG, "陀螺仪角度数据上报完成");
        } catch (Exception e) {
            Log.e(Log.TAG, "上报陀螺仪角度数据错误：" + e.getMessage());
        }
    }

    // ！！！
    public void doAlertFireAlarm(Settings.FireAlarmInfo info) { /////
        try {
            Settings.TimeRecord time = new Settings.TimeRecord(info.alarmTime);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.write(this.passcode.getBytes());
            dos.writeByte(1);  // 帧标识
            dos.writeByte(1);  // 包数
            dos.write(time.bytes);  // 采样时间
            dos.writeByte(0xAA);  // 报警状态
            dos.writeByte(info.alarmNum & 0xFF);
            sendPack(ORDER_2DH, null, bos.toByteArray());
            Log.i(Log.TAG, "上报山火报警，采样时间：" + time.asString + "，报警次数：" + info.alarmNum);
        } catch (Exception e) {
            Log.i(Log.TAG, "上报山火报警错误：" + e);
        }
    }

    /////
    /**
     * 上传设备故障信息（控制字：30H），参考规约第7.26节。
     * 数据包括当前未恢复的所有故障和恢复状态，覆盖温度、湿度、风速、风向、雨量、气压、日照等多项监测参数模块的故障信息。
     * 所有字段按规约要求编码为定长字节。
     * | 字段           | 长度 | 说明                    |
     * | -----------    | -- | -------------------    |
     * | 密文认证        | 4B | ASCII 编码              |
     * | 帧标识          | 1B |                        |
     * | 包数           | 1B | 包数                    |
     * | 设备状态        | 1B | 00H正常、FFH故障         |
     * | 首包                                         |
     * | 故障判断时间     | 6B | aero.time.bytes        |
     * | 功能编码        | 1B |                        |
     * | 故障编码        | 1B |                        |
     * | 第一故障包                                    |
     * | 与上包采样时间差  | 2B | 单位：秒                |
     * | 功能编码        | 1B |                        |
     * | 故障编码        | 1B |                        |
     * | 第二故障包                                    |
     * | 与上包采样时间差  | 2B | 单位：秒                |
     * | 功能编码        | 1B |                        |
     * | 故障编码        | 1B |                        |
     * | ...                                         |
     * | 与上包采样时间差  | 2B | 单位：秒                |
     * | 功能编码        | 1B |                        |
     * | 故障编码        | 1B |                        |
     *
     * @param faultInfos 当前故障信息列表，顺序应为发生时间排序
     */
//    public void doReportFault(List<Settings.FaultInfo> faultInfos, boolean fault) { ///////
//        int faultCount = faultInfos != null ? faultInfos.size() : 0; ///////
//        int deviceStatus = faultCount > 0 ? 0xFF : 0x00;  // OOH正常，FFH故障 ///////
//        try {
//            ByteArrayOutputStream bos = new ByteArrayOutputStream();
//            DataOutputStream dos = new DataOutputStream(bos);
//            bos.write(this.passcode.getBytes());  // 密文认证
//            bos.write(1);  // 帧标识
//            // 包数：首包+其他包（每个故障一包，首包单独编码）
//            bos.write(faultCount);  // 包数
//            bos.write(deviceStatus);  // 设备状态：有故障为 0xFF，无故障为 0x00 ///////
//            if (faultCount == 0) {
//                byte[] dataDomain = bos.toByteArray();
//                dos.close();
//                bos.close();
//                sendPack(ORDER_30H, null, dataDomain);
//                return;
//            }
//            // 遍历故障列表，写入首包和后续包
//            Log.i(Log.TAG, "上报故障信息：");
//            for (int i = 0; i < faultCount; i++) {
//                Settings.FaultInfo faultInfo = faultInfos.get(i);
//                if (i == 0) {
//                    // 首包格式：6字节时间 + 1字节功能编码 + 1字节故障编码
//                    bos.write(faultInfo.time.bytes);  // 时间戳（6字节）
//                    dos.writeByte(faultInfo.functionCode & 0xFF);
//                    dos.writeByte(faultInfo.faultCode & 0xFF);
//                    Log.i(Log.TAG, String.format("%s：%s，功能编码：%02X，故障编码：%02X", faultInfo.time.asString,
//                            (faultInfo.faultCode & 0x80) == 0 ? "发生故障" ："故障恢复",
//                            faultInfo.functionCode & 0xFF, faultInfo.faultCode & 0x7F));
//                } else {
//                    // 后续包格式：2字节时间差 + 1字节功能编码 + 1字节故障编码
//                    dos.writeShort(faultInfo.deltaSeconds);
//                    dos.writeByte(faultInfo.functionCode & 0xFF);
//                    dos.writeByte(faultInfo.faultCode & 0xFF);
//                    Log.i(Log.TAG, String.format("%s：%s，功能编码：%02X，故障编码：%02X", faultInfo.time.asString,
//                            (faultInfo.faultCode & 0x80) == 0 ? "发生故障" ："故障恢复",
//                            faultInfo.functionCode & 0xFF, faultInfo.faultCode & 0x7F));
//                }
//            }
//            byte[] dataDomain = bos.toByteArray();
//            dos.close();
//            bos.close();
//            sendPack(ORDER_30H, null, dataDomain);
//            Log.i(Log.TAG, "故障上报完成，共" + faultCount + "项");
//        } catch (Exception e) {
//            Log.e(Log.TAG, "上传故障信息失败：" + e.getMessage());
//            e.printStackTrace();
//        }
//    }
    /////

    /**
     * 上传装置流量数据使用情况（控制字：40H），参考规约第7.46节。
     * 若为主站请求（serverEcho=true），设备不需应答；
     * 若为主动上报，则上传日期与流量信息。
     * 数据域结构：
     * - [0~3]   密文认证（4字节）
     * - [4]     帧标识（固定0）
     * - [5]     包数（固定1）
     * - [6~11]  日期时间（6字节）
     * - [12~15] 当日已用流量（字节）
     * - [16~19] 当月已用流量（字节）
     * - [20~23] 当月剩余流量（字节）
     *
     * @param serverEcho true表示服务器响应，不需要再回应，false表示客户端主动上报
     */
    public void doReportTrafficUsage(boolean serverEcho) {
        if (serverEcho) {
            Log.i(Log.TAG, "主站请求流量信息，无需应答");
            return;
        }

        try {
            Settings.TrafficeUsage usage = listenerCallBack.getTrafficeUsage();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            bos.write(this.passcode.getBytes());  // 密文认证
            bos.write(0);  // 帧标识
            bos.write(1);  // 包数
            bos.write(usage.time.bytes);  // 时间戳
            dos.writeInt(usage.todayUsed);  // 当日已用流量
            dos.writeInt(usage.monthUsed);  // 当月已用流量
            dos.writeInt(usage.monthLeft);  // 当月剩余流量
            byte[] dataDomain = bos.toByteArray();
            dos.close();
            bos.close();
            sendPack(ORDER_40H, null, dataDomain);
            Log.d(Log.TAG, "主动上报流量：" + usage.toString());
        } catch (Exception e) {
            Log.e(Log.TAG, "上报流量错误：" + e.getMessage());
        }
    }

    /**
     * 上传设备工作电能量状态数据（控制字：48H），参考规约第7.47节。
     * 包括电池电量、电压电流、温度、太阳能及负载供电情况。
     * 数据域结构：
     * - [0~3]   密文认证
     * - [4]     帧标识（固定0）
     * - [5]     包数（固定1）
     * - [6~11]  时间戳（6字节）
     * - [12]    电池编号
     * - [13]    电量百分比（0~100）
     * - [14~15] 电池电压 *10（单位：0.1V）
     * - [16~17] 电池电流 *1000（单位：mA，绝对值）
     * - [18]    是否正在充电（1=充电中，0=未充电）
     * - [19~20] 电池温度 *10 + 500（单位：℃）
     * - [21~22] 太阳能电压 *10（单位：0.1V）
     * - [23~24] 太阳能电流 *1000（单位：mA）
     * - [25~26] 负载电压 *10（单位：0.1V）
     * - [27~28] 负载电流 *1000（单位：mA）
     *
     * @param info 电池信息对象
     */
    public void doReportBattery(Settings.BatteryInfo info) {
        if (info == null) return;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            bos.write(this.passcode.getBytes());  // 密文认证
            bos.write(0);  // 帧标识
            bos.write(1);  // 包数
            bos.write(info.time.bytes);  // 时间戳
            bos.write(info.batNo);  // 电池编号
            bos.write(info.batPercent);  // 电量百分比
            dos.writeShort((int) (info.batVoltage * 10));  // 电池电压（0.1V）
            dos.writeShort((int) (Math.abs(info.batAmpler) * 1000));  // 电池电流（mA）
            bos.write(info.charge ? 1 : 0);  // 是否充电
            dos.writeShort((int) (info.Temp * 10 + 500));  // 温度（偏移编码）
            dos.writeShort((int) (info.solarVoltage * 10));  // 太阳能电压（0.1V）
            dos.writeShort((int) (info.solarAmpler * 1000));  // 太阳能电流（mA）
            dos.writeShort((int) (info.loadVoltage * 10));  // 负载电压（0.1V）
            dos.writeShort((int) (info.loadAmpler * 1000));  // 负载电流（mA）
            byte[] dataDomain = bos.toByteArray();
            dos.close();
            bos.close();
            sendPack(ORDER_48H, null, dataDomain);
            Log.i(Log.TAG, "上报电量信息：" + info);
        } catch (Exception e) {
            Log.e(Log.TAG, "上报工作电量状态错误：" + e.getMessage());
        }
    }

    /**
     * 主站查询装置文件列表（控制字：71H），参考规约第7.54节。
     * 将当前设备中的所有待上传文件名称按9个一组分帧发送。
     * 每帧数据域结构：
     * - [0]   文件总数
     * - [1~N] 文件名（通过 getFileList(...) 写入）
     */
    protected void doSetFileNameList() { /////
        getFileNameList();  // 初始化 fileTimes 列表
        int num = 0;
        if (fileNumber == 0) {
            fileNumber = fileTimes.size();
            num = fileNumber / 9 + 1;
        } else if (fileNumber > 0) {
            num = fileNumber / 9 + 1;
        }
        Log.i(Log.TAG, String.format("准备上传文件列表，共 %d 个文件，分 %d 帧发送", fileNumber, num));
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int j = 0; j < num; j++) {
                baos.write((byte) fileNumber);  // 首字节：总文件数
                for (int i = 0; i <= 9; i++) {
                    if ((i + j * 9) < fileNumber)
                        baos = getFileList(baos, i + j * 9);  // 添加文件名
                }
            }
            sendPack(ORDER_71H, null, baos.toByteArray());
            baos.close();
            Log.i(Log.TAG, "文件名列表上报完成");
        } catch (IOException e) {
            Log.e(Log.TAG, "上传文件名列表失败：" + e.getMessage());
        }
    }

    /**
     * 装置请求上送文件（控制字：73H），参考规约第7.56节。
     * 该方法读取待上传文件的名称、采样时间和长度，计算总包数，
     * 并将这些信息打包发送至主站作为上传文件的通知帧。
     */
    private void doUploadingFileRequests() { /////
        try {
            ByteArrayOutputStream baos = getUploadFilename();
            DataOutputStream dos = new DataOutputStream(baos);
            fileNameByteData = getUploadFilename().toByteArray();
            uploadFilename = baos.toString().trim();
            getFileNameList(uploadFilename);
            dos.write(getByteTime(fileTime));  // 写入时间戳
            dos.writeShort(fileLength);  // 文件长度
            /////
            int packCount;
            if (fileLength % MAX_UPLOAD_FILE_SIZE == 0) {
                packCount = (fileLength / MAX_UPLOAD_FILE_SIZE);
            } else {
                packCount = (fileLength / MAX_UPLOAD_FILE_SIZE) + 1;
            }
            int packHigh = packCount / UPLOAD_IMAGE_PACK_DIVISOR;
            int packLow = packCount % UPLOAD_IMAGE_PACK_DIVISOR;
            baos.write((byte) packHigh);
            baos.write((byte) packLow);
            /////
            sendPack(ORDER_73H, null, baos.toByteArray());
            baos.close();
            Log.e(Log.TAG, "准备上传文件：" + uploadFilename + "，共需发送%d包" + packCount + "packHigh, packLow：" + packHigh + "," + packLow);
        } catch (IOException e) {
            Log.e(Log.TAG, "构建上传文件元信息帧失败：" + e.getMessage());
        }
        repeatTiming.order = ORDER_73H;
        mHandler.postDelayed(repeatTiming, 3000);
    }

    /**
     * 文件上传（控制字：74H），参考规约第7.57节。
     * 将目标文件按设定大小逐包读取，并为每包打上编号后上传。
     * 每帧携带文件名 + 包编号 + 文件片段内容。
     */
    private void doUploadingFile() { /////
        //mHandler.removeCallbacks(TheHeartbeatPackets);
        try {
            int len = 0;
            int packIndex = 0;
            byte[] buf = new byte[MAX_UPLOAD_FILE_SIZE];

            uploadFilename = new String(fileNameByteData).trim();
            pictureFile = new File(MainActivity.FILE_PATH + uploadFilename);
            FileInputStream fis = new FileInputStream(pictureFile);

            while ((len = fis.read(buf)) != -1) {
                SystemClock.sleep(100);
                packIndex++;
                int packHigh = packIndex / UPLOAD_IMAGE_PACK_DIVISOR; /////
                int packLow = packIndex % UPLOAD_IMAGE_PACK_DIVISOR; /////
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write(fileNameByteData);
                baos.write((byte) packHigh); /////
                baos.write((byte) packLow); /////
                baos.write(buf, 0, len);
                sendPack(ORDER_74H, null, baos.toByteArray());
                baos.close();
                Log.i(Log.TAG, String.format("上传包 %d（%d字节），上传%d, %d", packIndex, buf.length, packHigh, packLow));
            }
            fis.close();
            upLocalFileEnd(fileNameByteData);
            Log.i(Log.TAG, "文件上传完成，已调用结束处理");
        } catch (IOException e) {
            Log.e(Log.TAG, "文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 文件上送结束标记（控制字：75H），参考规约第7.58节。
     * 上传完成后，延迟 2 秒发送结束帧告知主站文件传输已结束，
     * 并设置 28 秒后重试机制以防丢帧。
     *
     * @param data 文件名字节数组（用于主站识别）
     */
    private void upLocalFileEnd(byte[] data) {
        // 2秒后再发送结束标记
        SystemClock.sleep(2000);
        sendPack(ORDER_75H, null, data);
        repeatTiming.order = ORDER_75H;
        mHandler.postDelayed(repeatTiming, 28000);
        Log.i(Log.TAG, String.format("已发送文件上传结束帧（控制字：%02X）", ORDER_75H));
    }

    /**
     * 文件上送结束标记（控制字：76H），参考规约第7.59节。
     * 主站在文件接收不完整的情况下，下发缺失包索引，
     * 装置根据索引重新发送对应的分包内容（仍用 74H 上传）。
     * 数据域结构：
     * - [110]：补包总数量
     * - [111+n*2]、[112+n*2]：每个补包的包号高/低字节（高位先）
     *
     * @param tonicPackData 补包数据
     */
    protected void doHandlerTonicPacks(byte[] tonicPackData) { /////
        try {
            if (tonicPackData != null) {
                int packCount = toInt(tonicPackData[110]); /////
                if (packCount > 0) { /////
                    Log.i(Log.TAG, "开始处理文件补包请求，共需补：" + packCount + " 包");
                    isUpLocal = true;
                    int count = packCount; /////
                    int len = 0;
                    byte[] buf = new byte[MAX_UPLOAD_FILE_SIZE];
                    ByteArrayOutputStream names = getUploadFilename();
                    uploadFilename = names.toString().trim();
                    pictureFile = new File(MainActivity.FILE_PATH + uploadFilename);
                    FileInputStream fis = new FileInputStream(pictureFile);
                    int readCount = 0;
                    while (count-- > 0) {
                        byte bytePackLow = tonicPackData[112 + len * 2];
                        int packHigh = tonicPackData[111 + len * 2]; /////
                        int packLow = (bytePackLow < 0) ? (bytePackLow & 0xFF) : bytePackLow; /////
                        int packIndex = packHigh * UPLOAD_IMAGE_PACK_DIVISOR + packLow; /////
                        if (packIndex > 0) {
                            int read;
                            while ((read = fis.read(buf)) != -1) {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                SystemClock.sleep(100);
                                readCount++;
                                if (packIndex == readCount) {
                                    baos.write(names.toByteArray());
                                    baos.write((byte) packHigh); /////
                                    baos.write((byte) packLow); /////
                                    baos.write(buf, 0, read);
                                    sendPack(ORDER_74H, null, baos.toByteArray());
                                    Log.i(Log.TAG, String.format("补传包 %d（%d字节）", packIndex, buf.length));
                                    baos.close();
                                    break;
                                }
                            }
                        }
                        len++;
                    }
                    upLocalFileEnd(getUploadFilename().toByteArray());
                    fis.close();
                    names.close();
                    isUpLocal = false;
                    Log.i(Log.TAG, "补包处理完成，已发送文件结束帧");
                }
            }
        } catch (IOException e) {
            Log.e(Log.TAG, "处理补包异常：" + e.getMessage());
        }
    }

    /**
     * 图像采集参数配置（控制字：81H），参考规约第7.60.1节。
     * 配置两个图像通道的颜色模式、图像尺寸、亮度、对比度与饱和度。
     * 数据域结构（共10字节）：
     * 通道1：[14~18] color, size, brightness, contrast, saturation
     * 通道2：[19~23] color, size, brightness, contrast, saturation
     *
     * @param mReceiveData 原始接收数据帧
     */
    protected void doPhotoSettings(byte[] mReceiveData) {
        /////
        if (mReceiveData == null || mReceiveData.length < 24) {
            Log.w(Log.TAG, "图像采集参数配置失败：接收数据无效或长度不足");
            sendPack(ORDER_81H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String password = getReceivePassword(mReceiveData);

        int resultCode; ///////
        if (password == null || !this.password.equals(password)) {
            sendPack(ORDER_81H, null, ERROR_FFFF_DOMAIN);
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            resultCode = 1; ///////
        } else { // ？？？
            // 通道1参数
            Settings.PhotoConfig c1 = new Settings.PhotoConfig();
            c1.color = mReceiveData[14];       // 色彩模式
            c1.size = mReceiveData[15];        // 图像尺寸
            c1.brightness = mReceiveData[16];  // 亮度
            c1.contrast = mReceiveData[17];    // 对比度
            c1.saturation = mReceiveData[18];  // 饱和度
            listenerCallBack.setPhotoParam(1, c1);

            // 通道2参数
            Settings.PhotoConfig c2 = new Settings.PhotoConfig();
            c2.color = mReceiveData[19];       // 色彩模式
            c2.size = mReceiveData[20];        // 图像尺寸
            c2.brightness = mReceiveData[21];  // 亮度
            c2.contrast = mReceiveData[22];    // 对比度
            c2.saturation = mReceiveData[23];  // 饱和度

            listenerCallBack.setPhotoParam(2, c2);
            sendPack(ORDER_81H, mReceiveData, null);
            Log.i(Log.TAG, "图像参数配置完成，通道1和通道2已更新");
            resultCode = 0; ///////
        }
    }

    /**
     * 拍照时间表设置（控制字：82H），参考规约第7.60.2节。
     * 主站下发拍照时间计划，终端按时间执行定时拍照任务。
     * 数据域结构：
     * - [14] 通道号
     * - [15] 拍照时间组数（每组3字节：时、分、预置位）
     * - [16+] 时间组数据：hour, min, preset（每组3字节）
     * 注意：主站若发送删除全部计划，可能出现 len 异常（如0x66），需特殊兼容处理。
     *
     * @param raw 原始接收数据帧
     */
    protected void doSetTakePhotoTimetable(final byte[] raw) { /////
        /////
        if (raw == null || raw.length < 16) {
            Log.w(Log.TAG, "拍照时间表设置失败：接收数据无效或长度不足");
            sendPack(ORDER_82H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String veryPss = getReceivePassword(raw);

        int resultCode; ///////
        if (password.equals(veryPss)) {
            int ch = raw[14] & 0xFF;
            int len = raw[15] & 0xFF;
            // 此处服务器有BUG，删除所有拍照时间表的时候，服务器默认len = 102(0x66），导致数据读取崩溃
            // 特殊兼容错误处理一下
            int realLen = (raw.length - 18) / 3;
            if (len > realLen) len = realLen;
            // 第15字节开始： 通道号，组数，[时，分，秒]……
            Settings.PhotoTimeItem[] table = new Settings.PhotoTimeItem[len];
            for (int i = 0; i < len; i++) {
                table[i] = new Settings.PhotoTimeItem();
                table[i].channel = ch;
                table[i].hour = raw[i * 3 + 16];
                table[i].min = raw[i * 3 + 17];
                table[i].preset = raw[i * 3 + 18];
                table[i].sec = 0;  // 固定为0，协议未包含秒字段
            }

            if (listenerCallBack.setPhotoTimeTable(ch, table)) {
                sendPack(ORDER_82H, raw, null);
                Log.i(Log.TAG, String.format("拍照时间表设置成功，通道：%d，组数：%d", ch, len));
            } else {
                sendPack(ORDER_82H, null, new byte[]{(byte) ch, 0});
                Log.w(Log.TAG, String.format("拍照时间表设置失败，通道：%d", ch));
            }
            resultCode = 1; ///////
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", veryPss, this.password));
            sendPack(ORDER_82H, null, ERROR_FFFF_DOMAIN);
            resultCode = 0; ///////
        }
    }

    // ！！！
    /**
     * 主站请求拍摄照片  83H
     *
     * @param mReceiveData 主站下发的数据
     */
    protected void doTakePhoto(byte[] mReceiveData) {
        /////
        if (mReceiveData == null || mReceiveData.length < 12) {
            Log.w(Log.TAG, "主站请求拍照失败：接收数据无效或长度不足");
            sendPack(ORDER_83H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        Log.i(Log.TAG, String.format("主站请求拍照，通道：%d，预置位：%d", mReceiveData[10] & 0xff, mReceiveData[11] & 0xff));
        sendPack(ORDER_83H, mReceiveData, null);

        listenerCallBack.takePhoto(mReceiveData[10] & 0xFF, mReceiveData[11] & 0xFF);
//        listenerCallBack.takePhoto(mReceiveData[10] & 0xFF, mReceiveData[11] & 0xFF, 1); ///////
    }

    /**
     * 采集终端请求上送照片（控制字：84H），参考规约第7.60.4节；采集终端请求上送短视频（控制字：94H），参考规约第7.60.20节。
     * 包括：
     *  - 文件合法性检查（存在、非写入中、非空）
     *  - 上锁判断是否已有上传任务
     *  - 初始化上传参数
     *  - 构造上传请求数据域并最多重发5次，间隔5秒
     *
     * 注意：
     * 上传图片，返回是否正在上传文件
     * 如果正在上传文件，必须等待完成后才能继续！
     * 上传成功应在对应处理函数（84H/94H）中设置 uploading = false
     *
     * @param time     拍摄/生成时间戳（ms）
     * @param fileName 要上传的文件全路径
     * @param channel  通道号
     * @param preset   摄像头预置位号
     * @param type     文件类型（图片/视频）
     */
    public void doUploadFile(final long time, final String fileName, final int channel, final int preset, final FILE_TYPE type) { /////
//    public void doUploadFile(final long time, final String fileName, final int channel, final int preset, final FILE_TYPE type, final int captureType) { ///////
        final File file = new File(fileName);
        if (!file.exists()) {
            Log.i(Log.TAG, "上传失败，文件不存在：" + fileName);
            listenerCallBack.onFileUploadFailure(fileName);
            return;
        }

        if (isFileWritting(file)) {
            Log.i(Log.TAG, "上传失败，文件正在进行写操作：" + fileName);
            listenerCallBack.onFileUploadFailure(fileName);
            return;
        }

        if (file.length() == 0) {
            listenerCallBack.onFileUploadFailure(fileName);
            Log.i(Log.TAG, "上传失败，空文件：" + fileName);
            if (Math.abs(file.lastModified() - System.currentTimeMillis()) > 5 * PERIOD_SECOND) {
                file.delete();
                Log.i(Log.TAG, "上传失败，删除空文件：" + fileName);
            }
            return;
        }

        // 当使用synchronized加锁class时，无论创建一个对象还是多个对象，他们用的都是同一把锁。
        // 而是用synchronized枷锁this时，只有同一个对象会使用同一把锁，不同对象之间的锁是不同的。
        // 当前类在MainActivity中只创建了一个对象用于操作南网协议，所以用this加锁就可以了。
        synchronized (this) {
            if (uploading) {
                Log.i(Log.TAG, "上传失败，文件正在上传：" + uploadFilename);
                listenerCallBack.onFileUploadFailure(fileName);
                return;
            }
            uploading = true;
        }
        uploadFilename = fileName;
        uploadTimestamp = time;
        uploadChannel = channel;
        uploadPreset = preset;
        uploadType = type;
        uploadStart = System.currentTimeMillis();

        // 计算总包数
        int filePacks = (int) (file.length() / MAX_UPLOAD_FILE_SIZE);
        if (file.length() % MAX_UPLOAD_FILE_SIZE != 0) filePacks++;  // 根据文件大小，求出包数，末尾可能不够一个包，有余数
        int packCount = filePacks;

        // 构造时间字段
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(time));
        byte year = (byte) (calendar.get(Calendar.YEAR) - 2000);
        byte month = (byte) (calendar.get(Calendar.MONTH) + 1);
        byte day = (byte) calendar.get(Calendar.DAY_OF_MONTH);
        byte hour = (byte) calendar.get(Calendar.HOUR_OF_DAY);
        byte minute = (byte) calendar.get(Calendar.MINUTE);
        byte second = (byte) calendar.get(Calendar.SECOND);

        // 最多五次， 每次间隔三秒，发送文件上传请求，在 84H，94H中，必须在文件传输完成后设置uploading为 false
        uploadEcho = false;
        for (int i = 1; i <= 5; i++) {
            byte order = ORDER_84H;
            if (type == hikvision.zhanyun.com.hikvision.SPGProtocol.FILE_TYPE.PHOTO) {
                order = ORDER_84H;
            } else if (type == hikvision.zhanyun.com.hikvision.SPGProtocol.FILE_TYPE.VIDEO) {
                order = ORDER_94H;
            }
            byte[] dataDomain = new byte[]{year, month, day, hour, minute, second, (byte) channel, (byte) preset,
                    (byte) (packCount / UPLOAD_IMAGE_PACK_DIVISOR), (byte) (packCount % UPLOAD_IMAGE_PACK_DIVISOR)};
            sendPack(order, null, dataDomain);

            // 规范要求为 最多请求5次，每次间隔3秒钟，但服务器性能有限，现改为5秒钟间隔
            SystemClock.sleep(500);  // 等服务器快速返回，防止服务器立刻返回后卡住
            if (uploadEcho) break;
            SystemClock.sleep(4500);  // 剩下凑够3秒
        }

        if (!uploadEcho) {
            doFileUploadError("文件上传请求超时");
        }
        Log.e(Log.TAG, "文件上传请求结束：" + uploadEcho + " " + fileName);
    }

    /**
     * 执行文件上传过程（分包上传），用于发送文件内容帧（如 ORDER_85H / 95H），并在末尾发送结束帧（如 ORDER_86H / 96H）。
     * 流程说明：
     * 1. 主站发出确认后调用此方法
     * 2. 文件内容按 MAX_UPLOAD_FILE_SIZE 分包，每包附带：
     *    - 通道号（raw[16]）
     *    - 预置位号（raw[17]）
     *    - 包编号（高字节、低字节）
     * 3. 每包通过 packOrder 发送
     * 4. 所有内容发送完后，通过 endOrder 连续最多发送10次结束帧，间隔5秒，直到主站回应
     *
     * @param raw       原始接收数据帧
     * @param packOrder 分包上传指令（如 85H、95H）：
     *                  图像数据上送（控制字：85H），参考规约第7.60.5节
     *                  短视频数据上送（控制字：95H），参考规约第7.60.21节
     * @param endOrder  上传完成指令（如 86H、96H）：
     *                  图像数据上送结束标记（控制字：86H），参考规约第7.60.6节
     *                  短视频数据上送结束标记（控制字：96H），参考规约第7.60.22节
     */
    private void doUploadFile(final byte[] raw, byte packOrder, final byte endOrder) {
        /////
        if (raw == null || raw.length < 18) {
            Log.w(Log.TAG, "文件上传失败：接收数据无效或长度不足");
            sendPack(packOrder, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        uploadEcho = true;

        File file = new File(uploadFilename);
        if (!file.exists()) {
            doFileUploadError("文件不存在"); /////
            return; /////
        }

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            FileInputStream fis = new FileInputStream(uploadFilename);

            // 服务器响应中包括 前面的时间 + 通道号 + 预置位号 channel = raw[16], preset = raw[17];
            byte[] head = new byte[]{raw[16], raw[17], 0, 0};  // 包头模板：通道、预置位、包号高、低
            byte[] buf = new byte[MAX_UPLOAD_FILE_SIZE];
            int len = 0;
            int packIndex = 0;

            Log.i(Log.TAG, String.format("开始上传文件：%s", uploadFilename));
            // 开始读取文件数据并发送
            while ((len = fis.read(buf)) != -1) {
                packIndex++;  // packIndex是从1开始计数的
                int packHigh = packIndex / UPLOAD_IMAGE_PACK_DIVISOR; /////
                int packLow = packIndex % UPLOAD_IMAGE_PACK_DIVISOR; /////

                head[2] = (byte) packHigh; /////
                head[3] = (byte) packLow; /////
                bos.write(head);
                bos.write(buf, 0, len);
                byte[] dataDomain = bos.toByteArray();
                bos.reset();
                sendPack(packOrder, null, dataDomain);
                SystemClock.sleep(UPLOAD_INTERVAL);  // 控制发送节奏
            }
            fis.close();
            bos.close();
            SystemClock.sleep(5000);  // 稍作等待再发结束帧

            uploadEndEcho = false;
            // 传输结束，发送结束指令，指令最多发送 5 次，每次间隔30秒，收到主站应答后立即停止发送
            for (int i = 0; i < 10; i++) {
                byte[] dataDomain = new byte[]{raw[16], raw[17]};
                sendPack(endOrder, null, dataDomain);
                Log.d(Log.TAG, String.format("发送文件结束帧（第 %d 次）", i + 1));

                // 等 30 秒后再试
                SystemClock.sleep(5 * 1000);
                if (uploadEndEcho) break;
            }
        } catch (Exception e) {
            Log.e(Log.TAG, "文件上传异常：" + e.getMessage());
        } finally {
            if (!uploadEndEcho) {  // 超过 30 秒，重复试了多试，都没有收到响应，需要强制复位
                doFileUploadError("服务器未响应传输结束指令");
            }
            Log.e(Log.TAG, "传输结束：" + uploadEndEcho);
        }
    }

    /**
     * 补包数据下发（控制字：87H），参考规约第7.60.7节；短视频补包数据下发（控制字：97H），参考规约第7.60.23节。
     * 主站反馈缺失包列表，设备按包号读取文件并重传指定片段。
     * 数据域结构：
     * - [10] 通道号
     * - [11] 预置位号
     * - [12] 缺失包数量 N
     * - [13+2n] 第n包包号高字节（包号 = high * N + low）
     * - [14+2n] 第n包包号低字节
     * 特殊处理：
     * 若 N=0 且通道与预置位匹配，表示文件成功上传，立即结束流程。
     *
     * @param raw       原始接收数据帧
     * @param packOrder 分包上传指令（如 85H、95H）：
     *                  图像数据上送（控制字：85H），参考规约第7.60.5节
     *                  短视频数据上送（控制字：95H），参考规约第7.60.21节
     * @param endOrder  上传完成指令（如 86H、96H）：
     *                  图像数据上送结束标记（控制字：86H），参考规约第7.60.6节
     *                  短视频数据上送结束标记（控制字：96H），参考规约第7.60.22节
     */
    protected void doMissingPack(final byte[] raw, byte packOrder, final byte endOrder) {
        /////
        if (raw == null || raw.length < 13) {
            Log.w(Log.TAG, "补包失败：接收数据无效或长度不足");
            sendPack(packOrder, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        uploadEndEcho = true;
        uploadEcho = true;

        // 情况1：第12 字节，补包数，如果为0，说明传完了，直接可以结束文件传输了
        if (raw[12] == 0) {
            if ((raw[10] & 0xFF) == this.uploadChannel && (raw[11] & 0xFF) == this.uploadPreset) {
                // 当前文件传输成功，结束流程
                this.uploading = false;
                this.uploadStart = 0;
                this.uploadSucceed = true;
                this.uploadSucceedTime = System.currentTimeMillis();
                listenerCallBack.onFileUploadEnd(this.uploadTimestamp, this.uploadFilename, this.uploadChannel, this.uploadPreset, this.uploadType);
                this.uploadFilename = "";
                Log.i(Log.TAG, "文件上传完成（主站反馈缺包为0）");
            } else {
                // 收到的传输成功的指令不是当前要传的文件--在电科院和超高压都发现有这样的问题，就是发送当前文件的补传指令，但是
                // 收到了上一个传输成功了的反馈指令，这个时候要结束当前传输流程，因为再往下走就不停的发送上一个文件的补传指令，
                // 而收到的一直是补传为0的信息，设备和后端的沟通已经不在一个频道上了。
                doFileUploadError("补包指令非当前传输文件");
            }
            return;
        }

        // 情况2：进行补包操作
        File file = new File(uploadFilename);
        if (!file.exists()) {
            doFileUploadError("补包时文件不存在");
            return;
        }

        try {
            Log.w(Log.TAG, "收到补包指令，缺少包数：" + (raw[12] & 0xFF));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            RandomAccessFile raf = new RandomAccessFile(uploadFilename, "r");

            byte[] head = new byte[]{raw[10], raw[11], 0, 0};
            int missed = (raw[12] & 0xFF);
            byte[] buf = new byte[MAX_UPLOAD_FILE_SIZE];
            int packIndex = 0;
            int len = 0;
            // 开始根据服务器下发的缺包数据，读取文件数据并发送
            for (int i = 0; i < missed; i++) {
                // 计算缺少文件数据位置并读取包
                head[2] = raw[13 + i * 2];
                head[3] = raw[14 + i * 2];

                int packHigh = raw[13 + i * 2] & 0xFF; /////
                int packLow = raw[14 + i * 2] & 0xFF; /////
                packIndex = packHigh * UPLOAD_IMAGE_PACK_DIVISOR + packLow - 1; /////
                if (packIndex < 0) packIndex = 0;

                raf.seek(packIndex * MAX_UPLOAD_FILE_SIZE);
                len = raf.read(buf, 0, MAX_UPLOAD_FILE_SIZE);
                if (len == -1) break;
                bos.write(head);
                bos.write(buf, 0, len);
                byte[] dataDomain = bos.toByteArray();
                bos.reset();
                sendPack(packOrder, null, dataDomain);
                SystemClock.sleep(UPLOAD_INTERVAL);
            }
            raf.close();
            bos.close();

            SystemClock.sleep(5000);
            uploadEcho = false;
            // 补包指令最多发送5次，每次间隔30秒，收到主站应答后立即停止发送
            for (int i = 0; i < 10; i++) {
                if (!uploading) break;  // 未在传输文件状态，说明文件可能已经传完了
                byte[] dataDomain = new byte[]{raw[10], raw[11]};
                sendPack(endOrder, null, dataDomain);
                Log.i(Log.TAG, String.format("发送结束确认帧（第 %d 次）", i + 1));
                SystemClock.sleep(5000);  // 快速休眠一下，检查是否uploadEcho有相应
                if (uploadEcho) break;

                // 无响应，规约要求等15秒后再试，继续发送结束
                //SystemClock.sleep(15000);
            }
        } catch (Exception e) {
            // 补包错误，应该如何处理？是直接关闭文件传输还是通知服务器结束？
            Log.e(Log.TAG, "文件补包时错误：" + e.getMessage());
        } finally {
            if (uploading && !uploadEcho) {  // 超过 30 秒，重复试了多试，都没有收到响应，需要强制复位
                doFileUploadError("未收到补包响应");
            }
            Log.e(Log.TAG, "补包结束：" + (uploadEndEcho || !uploading));  //？？？ /////
        }
    }

    /**
     * 摄像机远程调节（控制字：88H），参考规约第7.60.8节。
     * 主站发送云台控制命令，终端响应并执行相应动作（如上下左右、变倍等）。
     * 数据域结构：
     * - [14] 通道号
     * - [15] 动作指令（打开关闭电源、调用删除预置位、方向调节、变倍、光圈缩放、变焦、巡航、辅助开关、自动扫描、随机扫描、
     *                红外灯、扫描左右边界、扫描速度、步长、巡检、停止动作、转动速度、持续变倍、持续光圈缩放、持续变焦）
     * - [16] 指令参数
     *
     * @param data 原始接收数据帧
     */
    private void doPTZ(byte[] data) {
        /////
        if (data == null || data.length < 17) {
            Log.w(Log.TAG, "摄像机远程调节失败：接收数据无效或长度不足");
            sendPack(ORDER_88H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String mPassword = getReceivePassword(data);

        int resultCode; ///////
        if (mPassword != null && mPassword.equals(this.password)) {
            sendPack(ORDER_88H, data, null);
            Log.i(Log.TAG, String.format("执行云台控制：通道=%d，类型=%d，值=%d", data[14] & 0xFF, data[15] & 0xFF, data[16] & 0xFF));
            listenerCallBack.ptzControl(data[14] & 0xFF, data[15] & 0xFF, data[16] & 0xFF);
            resultCode = 1; ///////
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", mPassword, this.password));
            sendPack(ORDER_88H, null, ERROR_FFFF_DOMAIN);
            resultCode = 0; ///////
        }
    }

    /**
     * 启动摄像视频传输（控制字：89H），参考规约第7.60.9节。
     * 支持新旧两种协议格式，通过数据长度自动识别：
     * - 新协议长度 < 28：IP 4字节，SSRC 4字节
     * - 旧协议长度 ≥ 28：IP 16字节（字符串），SSRC 10字节
     * 1：当前通道正在进行视频传输；
     * 2：当前电量不够；
     * 3：参数错误，例如通道号不正确；
     * 4：网络连接错误（TCP传流时，无法连接接收流的地址）。
     * @param raw 原始接收数据帧
     */
    protected void doStartLive(byte[] raw) {
        // 根据请求实时视频的命令长度来判断是新协议还是老协议，新协议长度为25，老协议长度为43
        boolean newProtocol = raw.length < 28 ? true : false;

        byte[] ipByte;
        int port;
        String ip;
        byte[] ssrcBytes;

        if (newProtocol) {
            // 新协议字段解析
            ssrcBytes = subBytes(raw, 19, 4);
            ipByte = subBytes(raw, 13, 4);
            port = toInt(raw[17]) * UPLOAD_IMAGE_PACK_DIVISOR + toInt(raw[18]);
            ip = toInt(ipByte[0]) + "." + toInt(ipByte[1]) + "." + toInt(ipByte[2]) + "." + toInt(ipByte[3]);
        } else {
            // 旧协议字段解析
            ssrcBytes = subBytes(raw, 31, 10);
            ipByte = subBytes(raw, 13, 16);
            port = toInt(raw[29]) * UPLOAD_IMAGE_PACK_DIVISOR + toInt(raw[30]);
            ip = new String(ipByte).trim();
        }

        int ssrc = parseSSRC(ssrcBytes);

        //////
        short ret = listenerCallBack.startLiveVideo(toInt(raw[10]), toInt(raw[11]), raw[12], ssrc, ip, port);
        Log.e(Log.TAG, String.format("启动直播，协议：%s，服务器：%s:%d，SSRC：%d，启动结果：%d", newProtocol ? "新" : "旧", ip, port, ssrc, ret));

        try {
            if (ret == 0) {
                // 成功回包
                sendPack(ORDER_89H, raw, null);
            } else {
                // 错误回包：带 channel + 错误码 + SSRC
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);
                bos.write(raw[10]);    // 通道号
                dos.writeShort(ret);   // 错误码
                dos.write(ssrcBytes);  // SSRC
                sendPack(ORDER_89H, null, bos.toByteArray());
                bos.close();

                dos.close();
            }

        } catch (IOException e) {
            Log.e(Log.TAG, "启动直播失败：" + e.getMessage());
        }
    }

    /**
     * 终止摄像视频传输（控制字：8AH），参考规约第7.60.10节。
     * 根据协议字段提取通道、预置位、SSRC，通知视频停止，并反馈状态。
     * 注意事项：
     * - 新协议：SSRC 长度为 4 字节
     * - 旧协议：SSRC 可能为 10 字节（此处暂使用 4 字节以兼容主站）
     * 1：SSRC不存在；
     * 2：通道号错误。
     * @param receiveData 原始接收数据帧
     */
    private void doStopLive(byte[] receiveData) {
        byte[] receiveSSRC = new byte[0]; ///////
        int error = 0; ///////
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);

            // 根据请求实时视频的命令长度来判断是新协议还是老协议，新协议长度为18，老协议长度为22
            receiveSSRC = subBytes(receiveData, 12, receiveData.length < 19 ? 4 : 10);
            boolean ret = listenerCallBack.stopLiveVideo(receiveData[10], receiveData[11], parseSSRC(receiveSSRC));
            error = ret ? 0 : 1; ///////
            if (channelNum != receiveData[10]) error = 2;

            bos.write(receiveData[10]);  // 通道号
            dos.writeShort(error);       // 状态码（0：成功，1：SSRC不存在，2：通道号错误）
            bos.write(receiveSSRC);      // 原SSRC
            sendPack(ORDER_8AH, null, bos.toByteArray());
            bos.close();
            dos.close();
            Log.i(Log.TAG, String.format("停止直播通道=%d，预置位=%d，SSRC=%d，结果=%d", receiveData[10], receiveData[11], parseSSRC(receiveSSRC), error));
        } catch (Exception e) {
            Log.e(Log.TAG, "停止直播错误：" + e.getMessage());
        }
    }

    /**
     * 查询拍照时间表（控制字：8BH），参考规约第7.60.11节。
     * 主站查询某通道的定时拍照计划，终端将拍照时间组列表打包回复。
     * 数据域结构：
     * - [0] 通道号
     * - [1] 拍照时间组数 N
     * - [2~N*3+1] 每组结构：hour（1字节）、minute（1字节）、preset（1字节）
     *
     * @param data 原始接收数据帧
     */
    protected void doGetPhotoTimeTable(byte[] data) {
        /////
        if (data == null || data.length < 11) {
            Log.w(Log.TAG, "查询拍照时间表失败：接收数据无效或长度不足");
            sendPack(ORDER_8BH, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        channelNum = data[10];
        byte[] dataDomain = null;
        Settings.PhotoTimeItem[] table = listenerCallBack.getPhotoTimeTable(data[10] & 0xFF);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        try {
            byte len = (byte) table.length;
            dos.write(channelNum);  // 写入通道号
            dos.write(len);  // 写入组数
            for (Settings.PhotoTimeItem item : table) {
                dos.write(item.hour);
                dos.write(item.min);
                dos.write(item.preset);
            }
            dataDomain = bos.toByteArray();

            dos.close();
            bos.close();
        } catch (IOException e) {
            Log.e(Log.TAG, "构建拍照时间表数据失败：" + e.getMessage());
        }

        sendPack(ORDER_8BH, null, dataDomain);
        Log.i(Log.TAG, String.format("已上送拍照时间表：通道=%d，共 %d 组", data[10] & 0xFF, table.length));
    }

    /**
     * 视频采集参数配置（控制字：8CH），参考规约第7.60.12节。
     * 数据域结构（共 9 字节）：
     * - [14] 通道号
     * - [15] 通道（码流）类型
     * - [16] 帧率
     * - [17] I帧间隔
     * - [18] 编码类型
     * - [19,20] 码率
     * - [21] 位率类型
     * - [22] 分辨率
     *
     * @param data 原始接收数据帧
     */
    public void doSetCodec(byte[] data) {
        /////
        if (data == null || data.length < 23) {
            Log.w(Log.TAG, "视频采集参数配置失败：接收数据无效或长度不足");
            sendPack(ORDER_8CH, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        int resultCode; ///////
        String password = getReceivePassword(data);
        if (password.equals(this.password)) {
            sendPack(ORDER_8CH, data, null);

            // 提取参数
            Settings.VideoCodec v = new Settings.VideoCodec();
            v.channel = (byte) (data[14] & 0xff);
            v.streamType = (byte) (data[15] & 0xff);
            v.frame = data[16] & 0xff;
            v.iFrame = data[17] & 0xff;
            v.codec = (byte) (data[18] & 0xff);
            v.bps = hw(data[19], data[20]);
            v.vbr = (byte) (data[21] & 0xff);
            v.resolution = (byte) (data[22] & 0xff);
            listenerCallBack.setVideoCodec(v);
            Log.i(Log.TAG, String.format(
                    "设置视频编码参数成功：通道=%d，码流=%d，帧率=%dfps，I帧间隔=%d，编码=%d，码率=%dkbps，VBR=%d，分辨率=%d",
                    v.channel, v.streamType, v.frame, v.iFrame, v.codec, v.bps, v.vbr, v.resolution
            ));
            resultCode = 1; ///////
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_8CH, null, ERROR_FFFF_DOMAIN);
            resultCode = 0; ///////
        }
    }

    /**
     * 视频采集参数查询（控制字：8DH），参考规约第7.60.13节。
     * 数据域结构（共 9 字节）：
     * - [0] 通道号
     * - [1] 通道（码流）类型
     * - [2] 帧率
     * - [3] I帧间隔
     * - [4] 编码类型
     * - [5~6] 码率
     * - [7] 位率类型
     * - [8] 分辨率
     *
     * @param channel     通道号
     * @param channelType 码流类型
     */
    protected void doGetCodec(byte channel, byte channelType) {
        byte[] dataDomain;
        Settings.VideoCodec vc = listenerCallBack.getVideoCodec(channel, channelType);
        if (vc == null) {
            dataDomain = new byte[]{channel, channelType, 25, 50, 0, 0x08, 0x00, 0, 8};
            Log.w(Log.TAG, String.format("视频参数查询结果为空，返回默认值：channel=%d, type=%d, frame=%d, iFrame=%d, codec=%d, bps=%d, vbr=%d, res=%d",
                    channel & 0xFF, channelType & 0xFF, 25, 50, 0, 2048, 0, 8));
        } else {
            dataDomain = new byte[]{channel, channelType, (byte) vc.frame, (byte) vc.iFrame, vc.codec, hi(vc.bps), lo(vc.bps), vc.vbr, vc.resolution};
            Log.i(Log.TAG, String.format(
                    "查询视频参数成功：channel=%d, type=%d, frame=%d, iFrame=%d, codec=%d, bps=%d, vbr=%d, res=%d",
                    channel & 0xFF, channelType & 0xFF, vc.frame, vc.iFrame, vc.codec, vc.bps, vc.vbr, vc.resolution));
        }
        sendPack(ORDER_8DH, null, dataDomain);
    }

    /**
     * OSD参数配置（控制字：8EH），参考规约第7.60.14节。
     * 用于配置通道的视频叠加内容，如时间、标签文字等。
     * 数据域结构：
     * - [14]      通道号
     * - [15]      是否显示时间标识
     * - [16]      文本显示标识
     * - [17~17+n] 文本内容
     *
     * @param mReceiveData 原始接收数据帧
     */
    private void setOSDConfig(byte[] mReceiveData) {
        /////
        if (mReceiveData == null || mReceiveData.length < 18) {
            Log.w(Log.TAG, "OSD配置失败：接收数据无效或长度不足");
            sendPack(ORDER_8EH, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        int resultCode; ///////
        String password = getReceivePassword(mReceiveData);
        if (password.equals(this.password)) {
            try {
                int contentCount = mReceiveData.length - 19;
                byte[] osdConfig = subBytes(mReceiveData, 17, contentCount);
                Settings.OSD osd = new Settings.OSD();
                osd.text = new String(osdConfig, "utf-8");
                osd.tag = mReceiveData[16];
                osd.time = mReceiveData[15];
                listenerCallBack.setOSDConfig(mReceiveData[14], osd);
                sendPack(ORDER_8EH, mReceiveData, null);
                Log.i(Log.TAG, String.format( "OSD配置成功：通道=%d，时间显示=%d，标签=%d，文字=\"%s\"", mReceiveData[14], osd.time, osd.tag, osd.text));
            } catch (Exception e) {
                Log.e(Log.TAG, "设置OSD参数异常：" + e.getMessage());
            }
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_8EH, null, ERROR_FFFF_DOMAIN);
            resultCode = 0; ///////
        }
    }

    /**
     * OSD参数查询（控制字：8FH），参考规约第7.60.15节。
     * 返回指定通道的 OSD 显示设置，包括时间标志、文字内容。
     * 数据域结构：
     * - [0]     通道号
     * - [1]     是否显示时间标识
     * - [2]     文本显示标识
     * - [3~3+n] 文本内容
     *
     * @param channel 通道号
     */
    private void doGetOSD(byte channel) {
        Settings.OSD osd = listenerCallBack.getOSDConfig(channel);
        byte[] dataDomain = null;
        if (osd == null)
            osd = new Settings.OSD();
        try {
            dataDomain = byteMerger(new byte[]{channel, osd.time, osd.tag}, osd.text.getBytes("UTF-8"));
            Log.i(Log.TAG, String.format("查询OSD成功：通道=%d，时间=%d，标签=%d，文字=\"%s\"",
                    channel, osd.time, osd.tag, osd.text));
        } catch (UnsupportedEncodingException e) {
            Log.e(Log.TAG, "查询OSD失败：UTF-8编码异常 - " + e.getMessage());
        }
        sendPack(ORDER_8FH, null, dataDomain);
    }

    /**
     * 录像策略参数配置（控制字：90H），参考规约第7.60.16节。
     * 主站配置定时录像规则：每条规则包括开始时间、持续时长、动作类型等。
     * 数据域结构：
     * - [14] 通道号
     * - [15] 通道（码流）类型
     * - [16] 组数
     * - [17~17+n*7] 每组录像动作：
     *      [0] 动作类别
     *      [1] 动作参数
     *      [2] 开始时（hour）
     *      [3] 开始分（minute）
     *      [4] 开始秒（second）
     *      [5~6] 持续时长
     *
     * @param data 原始接收数据帧
     */
    private void doSetVideoTimeTable(byte[] data) { /////
        /////
        if (data == null || data.length < 17) {
            Log.w(Log.TAG, "录像策略配置失败：接收数据无效或长度不足");
            sendPack(ORDER_90H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        int resultCode; ///////
        String password = getReceivePassword(data);
        if (password.equals(this.password)) {

            int num = toInt(data[16]);
            List<Settings.VideoTimeItem> list = new ArrayList<>();
            for (int i = 0; i < num; i++) {
                Settings.VideoTimeItem item = new Settings.VideoTimeItem();
                item.channel = data[14];
                item.stream = data[15];
                item.action = data[17 + i * 7];  // 动作类别:0为调用预置位（默认值）；01为调用巡航；02调用巡检
                item.para = data[18 + i * 7];    // 当动作类别为0，参数值表示预置位号；
                // 当动作类别为1，参数值表示巡航组号；
                // 当动作类别为2，参数值表示巡检组号
                item.hour = data[19 + i * 7];    // 开始时间：时
                item.min = data[20 + i * 7];     // 开始时间：分
                item.sec = data[21 + i * 7];     // 开始时间：秒
                item.duration = ((data[22 + i * 7] & 0xFF) << 8) | ((data[23 + i * 7] & 0xFF));  // 时长（单位秒）
                list.add(item);
            }
            listenerCallBack.setVideoTimeTable(data[14], data[15], list);
            sendPack(ORDER_90H, data, null);
            Log.i(Log.TAG, String.format("配置录像策略成功：通道=%d，码流=%d，共 %d 条", data[14], data[15], num));
            resultCode = 1; ///////
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_90H, null, ERROR_FFFF_DOMAIN);
            resultCode = 0; ///////
        }
    }

    /**
     * 录像策略参数查询（控制字：91H），参考规约第7.60.17节。
     * 返回指定通道与码流配置的录像时间策略。
     * 数据域结构：
     * - [0] 通道号
     * - [1] 通道（码流）类型
     * - [2] 结果码
     * - [3] 组数
     * - [4~4+n*7] 每组录像动作：
     *      [0] 动作类别
     *      [1] 动作参数
     *      [2] 开始时（hour）
     *      [3] 开始分（minute）
     *      [4] 开始秒（second）
     *      [5~6] 持续时长
     *
     * @param channel     通道号
     * @param channelType 通道（码流）类型
     */
    public void doGetVideoTimeTable(byte channel, byte channelType) {
        List<Settings.VideoTimeItem> videoRecordChannel = listenerCallBack.getVideoTimeTable(channel, channelType);
        byte[] dataDomain;
        if (videoRecordChannel == null)
            dataDomain = new byte[]{channel, channelType, 0, 0};
        else {
            dataDomain = new byte[4 + 7 * videoRecordChannel.size()];
            dataDomain[0] = channel;
            dataDomain[1] = channelType;
            dataDomain[2] = 0;
            dataDomain[3] = (byte) videoRecordChannel.size();
            for (int i = 0; i < videoRecordChannel.size(); i++) {
                dataDomain[4 + i * 7] = videoRecordChannel.get(i).action;
                dataDomain[5 + i * 7] = videoRecordChannel.get(i).para;
                dataDomain[6 + i * 7] = videoRecordChannel.get(i).hour;
                dataDomain[7 + i * 7] = videoRecordChannel.get(i).min;
                dataDomain[8 + i * 7] = videoRecordChannel.get(i).sec;
                //dataDomain[9 + i * 7] = lo(hi(videoRecordChannel.get(i).duration));
                //dataDomain[10 + i * 7] = lo(lo(videoRecordChannel.get(i).duration));
                dataDomain[9 + i * 7] = (byte) ((videoRecordChannel.get(i).duration >> 8) & 0xFF);
                dataDomain[10 + i * 7] = (byte) (videoRecordChannel.get(i).duration & 0xFF);
            }
        }
        sendPack(ORDER_91H, null, dataDomain);
        Log.i(Log.TAG, String.format("查询录像策略：通道=%d，码流=%d，共 %d 条", channel, channelType, videoRecordChannel.size()));
    }

    /**
     * 通道录像状态查询（控制字：92H），参考规约第7.60.18节。
     * 返回指定通道当前的录像状态及通道运行状态。
     * 返回数据域结构：
     * - [0] 通道号
     * - [1] 通道（码流）类型
     * - [2] 结果码
     * - [3] 当前录像状态
     *
     * @param channel 通道号
     * @param type    通道（码流）类型
     */
    private void doQueryVideoState(byte channel, byte type) { /////
        byte[] dataDomain;
        Settings.ChannelStatus status = listenerCallBack.getChannelState(channel, type);
        dataDomain = new byte[]{channel, type, status.code, status.recording};
        sendPack(ORDER_92H, null, dataDomain);
        Log.i(Log.TAG, String.format("查询录像状态：通道=%d，码流=%d，结果码=%d，录像中=%d", channel, type, status.code, status.recording));
    }

    /**
     * 主站请求拍摄短视频（控制字：93H），参考规约第7.60.19节。
     * 主站向终端发送命令，指定通道与码流，触发短视频录制。
     * 数据域结构：
     * - [10] 通道号
     * - [11] 通道（码流）类型
     * - [12] 拍摄时长
     * 1：当前正在拍摄中；
     * 2：当前正在传输中；
     * 3：当前电量不够；
     * 4：参数错误，拍摄时长过长；
     * 5：参数错误，拍摄时长过短；
     * 6：参数错误，超时（10分钟）传输。   ???????? TODO
     * @param raw 原始接收数据帧
     */
    protected void doTakeVideo(byte[] raw) {

        /////
        if (raw == null || raw.length < 13) {
            Log.w(Log.TAG, "短视频录制失败：接收数据无效或长度不足");
            sendPack(ORDER_93H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////


        // 拍摄时间
//        int shortestTime = ;
//        int maximumTime = ;
//        int recordingTime = raw[12];  // 用于判断参数错误
//        if (recordingTime > maximumTime){
//            short ret = 4;
//            sendPack(ORDER_93H, null, new byte[]{raw[10], raw[11], hi(ret), lo(ret)});
//            return;   // 提前终止函数
//        }else if (recordingTime < shortestTime){
//            short ret = 5;
//            sendPack(ORDER_93H, null, new byte[]{raw[10], raw[11], hi(ret), lo(ret)});
//            return;
//        }

        this.channelNum = raw[10];
        Log.i(Log.TAG, String.format("启动录制小视频， 通道 %d，码流： %d，时长：%d", channelNum, raw[11], raw[12]));

        short ret = listenerCallBack.startShortVideo(raw[10], raw[11], raw[12] & 0xFF);

//
//        if (uploading){       // 当前正在传输中
//            ret = 2;
//        }

        int resultCode; ///////
        if (ret == 0) {
            sendPack(ORDER_93H, raw, null);
            Log.i(Log.TAG, "短视频录制启动成功");
            resultCode = 1;
        } else {
            Log.e(Log.TAG, "启动录制小视频失败：" + ret);
            sendPack(ORDER_93H, null, new byte[]{raw[10], raw[11], hi(ret), lo(ret)});
            resultCode = 0;
        }
    }

    /**
     * 主站查询终端录像文件数目（控制字：98H），参考规约第7.61.1节。
     * 主站请求查询指定时间段内的录像文件个数。
     * 数据域结构：
     * - [10] 通道号
     * - [11~14] 录像类型
     * - [15~20] 录像开始时间
     * - [21~26] 录像结束时间
     *
     * @param raw 原始接收数据帧
     */
    private void doFindVideoFile(byte[] raw) { /////
        /////
        if (raw == null || raw.length < 27) {
            Log.w(Log.TAG, "录像文件数量查询失败：接收数据无效或长度不足");
            sendPack(ORDER_98H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        int videoType = (raw[11] << 24) | (raw[12] << 16) | (raw[13] << 8) | (raw[14] & 0xFF);
        Settings.TimeRecord start = new Settings.TimeRecord(subBytes(raw, 15, 6));
        Settings.TimeRecord end = new Settings.TimeRecord(subBytes(raw, 21, 6));

        int fileCount = listenerCallBack.fileFiles(raw[10], videoType, start, end);

        byte[] size = new byte[]{lo(hi(fileCount)), lo(lo(fileCount))};  // 拆分成2字节
        byte[] dataDomain = byteMerger(subBytes(raw, 10, 17), size);  // 原始参数 + 文件数
//        // 循环执行三次
//        for (int i = 0; i < 3; i++) {
//            sendPack(ORDER_98H, null, dataDomain);
//        }
        sendPack(ORDER_98H, null, dataDomain);
        Log.i(Log.TAG, String.format("录像文件数查询：通道=%d，类型=%d，时间段=%s~%s，共 %d 个文件",
                raw[10], videoType, start.asString, end.asString, fileCount));
    }

    /**
     * 主站查询终端录像文件列表（控制字：99H），参考规约第7.61.2节。
     * 主站请求指定时间段的录像文件，并分页返回列表。
     * 数据域结构：
     * - [10] 通道号
     * - [11~14] 录像类型
     * - [15~20] 录像开始时间
     * - [21~26] 录像结束时间
     * - [27~28] 录像总数
     * - [29~30] 起始索引
     * - [31~32] 结束索引
     *
     * @param raw 原始接收数据帧
     */
    private void doFindVideoFileList(byte[] raw) { /////
        /////
        if (raw == null || raw.length < 33) {
            Log.w(Log.TAG, "主站查询终端录像文件列表失败：接收数据无效或长度不足");
            sendPack(ORDER_99H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String startVideoTime = String.format("%04d-%02d-%02d-%02d-%02d-%02d", (raw[15] & 0xFF) + 2000, raw[16], raw[17], raw[18], raw[19], raw[20]);
        String stopVideoTime = String.format("%04d-%02d-%02d-%02d-%02d-%02d", (raw[21] & 0xFF) + 2000, raw[22], raw[23], raw[24], raw[25], raw[26]);

        int startNumb = toInt(raw[29]) + toInt(raw[30]) - 1;
        if (startNumb < 0) startNumb = 0;
        int endNumb = toInt(raw[31]) + toInt(raw[32]);
        if (endNumb < 0) endNumb = 0;
        int videoType = (raw[11] << 24) | (raw[12] << 16) | (raw[13] << 8) | (raw[14] & 0xFF);

        Settings.FileList list = listenerCallBack.findVideoFileList(raw[10] & 0xFF, videoType, startVideoTime, stopVideoTime, startNumb, endNumb);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            baos.write(subBytes(raw, 10, 17));  // 原样返回请求字段
            dos.writeShort(list.files.length);  // 录像文件数目

            for (int i = 0; i < list.files.length; i++) {
                dos.write(list.files[i].begin.bytes);  // 录像开始时间
                dos.write(list.files[i].end.bytes);  // 录像结束时间
                dos.writeInt(list.files[i].size);  // 文件大小
                dos.writeInt(list.files[i].type);  // 录像文件类型
            }
            byte[] dataDomain = baos.toByteArray();
            sendPack(ORDER_99H, null, dataDomain);
            baos.close();
            Log.i(Log.TAG, "录像文件数：" + list.files.length);
        } catch (IOException e) {
            Log.e(Log.TAG, "录像文件数查询失败：" + e.getMessage());
        }
    }

    /**
     * 主站请求进行录像文件回放（控制字：9AH），参考规约第7.61.3~7.61.4节。
     * 主站请求设备向指定服务器推送回放视频流。
     * 数据域结构：
     * - 新协议长度：36 字节；旧协议长度 ≥ 42 字节
     * - [10] 通道号
     * - [11] 是否主码流（0=主码流，1=子码流）
     * - [12~15] 或 [12~27]：服务器 IP
     * - [16~17] 或 [28~29]：服务器端口
     * - [18~29] 或 [30~41]：起止时间（各6字节）
     * - [30~33] 或 [42~51]：SSRC（新协议4字节 / 旧协议10字节）
     * 1：超过最大回放用户数限制；   TODO   ??????
     * 2：当前电量不够；
     * 3：参数错误，例如超过录像文件时间范围；       //  TODO 对传入的参数进行判断
     * 4：网络连接错误（TCP传流时，无法连接接收流的地址）；
     * @param raw 原始接收数据帧
     */
    private void doPlayback(byte[] raw) {
        doPlaybackStream(raw, ORDER_9AH, "启动回放");
    }

    private void doPlaybackStream(byte[] raw, byte responseOrder, String actionName) {
        // 根据命令长度来判断是新协议还是老协议，新协议长度为36，老协议长度≥42
        boolean newProtocol = raw.length < 38;

        byte[] ipByte;
        String ip = "";
        int port;
        String startVideoTime;
        String stopVideoTime;

        byte[] ssrcBytes;
        if (newProtocol) {
            // 新协议
            ipByte = subBytes(raw, 12, 4);
            port = (raw[16] << 8) | (raw[17] & 0xFF);
            ip = toInt(ipByte[0]) + "." + toInt(ipByte[1]) + "." + toInt(ipByte[2]) + "." + toInt(ipByte[3]);
            startVideoTime = (raw[18] + 2000) + "-" + raw[19] + "-" + raw[20] + "-" + raw[21] + "-" + raw[22] + "-" + raw[23];
            stopVideoTime = (raw[24] + 2000) + "-" + raw[25] + "-" + raw[26] + "-" + raw[27] + "-" + raw[28] + "-" + raw[29];
            ssrcBytes = subBytes(raw, 30, 4);
        } else {
            // 旧协议
            ipByte = subBytes(raw, 12, 16);
            port = (raw[28] << 8) | (raw[29] & 0xFF);
            startVideoTime = (raw[30] + 2000) + "-" + raw[31] + "-" + raw[32] + "-" + raw[33] + "-" + raw[34] + "-" + raw[35];
            stopVideoTime = (raw[36] + 2000) + "-" + raw[37] + "-" + raw[38] + "-" + raw[39] + "-" + raw[40] + "-" + raw[41];

            ssrcBytes = subBytes(raw, 42, 10);
            ip = new String(ipByte).trim();
        }

        int ssrc = parseSSRC(ssrcBytes);

        byte ret = listenerCallBack.playbackFile(raw[10], raw[11] == 0, startVideoTime, stopVideoTime, ip, port, ssrc);

        byte[] dataDomain = byteMerger(new byte[]{raw[10], ret, 0}, ssrcBytes);

        sendPack(responseOrder, null, dataDomain);

        Log.i(Log.TAG, String.format("%s：通道=%d，UDP=%s，时间段=%s~%s，服务器=%s:%d，SSRC=%d，错误码=%d", actionName, raw[10], raw[11] == 0, startVideoTime, stopVideoTime, ip, port, ssrc, ret));
    }


    /**
     * 主站请求进行录像文件回放控制（控制字：9BH），参考规约第7.61.5~7.61.6节。
     * 主站控制终端正在推送的回放流，如快进、拖动、暂停等。
     * 数据域结构：
     * - [10] 通道号
     * - [11~12] 控制命令（2字节）：0x0001=播放、0x0002=暂停、0x0003=快进、0x0004=快退、0x0005=指定时间偏移
     * - [13~16] Scale（4字节浮点数）
     * - [17~18] Offset（偏移秒数）
     * - [19~]   SSRC（新协议为4字节，旧协议为10字节）
     *  1：超过最大回放用户数限制；   # TODO ?????
     * 2：当前电量不够；
     * 3：参数错误，例如超过录像文件时间范围；   ## TODO 对传入的参数进行判断
     * 4：网络连接错误（TCP传流时，无法连接接收流的地址）。
     * @param mReceiveData 原始接收数据帧
     */

    private void doPlaybackControl(byte[] mReceiveData) { /////
        byte[] dataDomain = null;
        byte[] ssrc = new byte[0]; ///////
        short ret = 0; ///////
        try {
            int code = getValue(mReceiveData, 11, 2);  // 控制指令
            byte[] byteScale = subBytes(mReceiveData, 13, 4);
            int len = byteScale.length;
            byte[] result = new byte[len];
            for (byte item : byteScale) {
                result[len - 1] = item;
                len--;
            }
            InputStream bas = new ByteArrayInputStream(result);
            DataInputStream data = new DataInputStream(bas);

            float scaleFloat = data.readFloat(); /////

            int offset = (toInt(mReceiveData[17]) << 8) + toInt(mReceiveData[18]);
            // 根据命令长度来判断是新协议还是老协议，新协议长度为25，老协议长度为31

            ssrc = subBytes(mReceiveData, 19, mReceiveData.length < 26 ? 4 : 10);  // 新协议
            int parsedSSRC = parseSSRC(ssrc);
            Log.i(Log.TAG, String.format("回放控制指令：通道=%d，命令码=%d，倍速=%.2fx，偏移=%d秒，SSRC=%d",
                    mReceiveData[10], code, scaleFloat, offset, parsedSSRC));

            ret = listenerCallBack.playbackControl(mReceiveData[10], code, scaleFloat, offset, parsedSSRC); /////

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            try {
                baos.write(new byte[]{mReceiveData[10]});
                dos.writeShort(ret);
                baos.write(ssrc);
                dataDomain = baos.toByteArray();
                baos.close();
                dos.close();
            } catch (IOException e) {
                Log.e(Log.TAG, "回放控制失败：" + e.getMessage());
            }
        } catch (IOException e) {
            Log.e(Log.TAG, "回放控制响应构建失败：" + e.getMessage());
        }
        sendPack(ORDER_9BH, null, dataDomain);
    }

    /**
     * 主站请求进行录像文件回放断开（控制字：9CH），参考规约第7.61.7节。
     * 主站通知设备终止指定通道的回放流。
     * 数据域结构：
     * - [10] 通道号
     * - [11~] SSRC（新协议 4 字节，旧协议 10 字节）
     *
     * @param data 原始接收数据帧
     */
    private void doStopPlayBack(byte[] data) { /////
        /////
        if (data == null || data.length < 15) {
            Log.w(Log.TAG, "回放断开失败：接收数据无效或长度不足");
            sendPack(ORDER_9CH, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        int channel = data[10];
        byte[] dataDomain = null;
        byte[] ssrc = subBytes(data, 11, data.length < 18 ? 4 : 10);
        int parsedSSRC = parseSSRC(ssrc);
        short ret = listenerCallBack.stopPlayCallBack(channel, parsedSSRC);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            baos.write(new byte[]{(byte) channel});
            dos.writeShort(ret);
            baos.write(ssrc);
            dataDomain = baos.toByteArray();
            baos.close();
            dos.close();
            Log.i(Log.TAG, String.format("回放断开：通道=%d，SSRC=%d，结果码=%d", channel, parsedSSRC, ret));
        } catch (IOException e) {
            Log.e(Log.TAG, "回放断开响应构建失败：" + e.getMessage());
        }
        sendPack(ORDER_9CH, null, dataDomain);
    }

    // ？？？
    /////
    private void doVideoDownload(byte[] mReceiveData){
        doPlaybackStream(mReceiveData, ORDER_9DH, "启动录像下载");
    }

    private void doVideoDownloadCancel(byte[] mReceiveData){
        if (mReceiveData == null || mReceiveData.length < 15) {
            Log.w(Log.TAG, "录像下载取消失败：接收数据无效或长度不足");
            sendPack(ORDER_9EH, null, ERROR_FFFF_DOMAIN);
            return;
        }

        int channel = mReceiveData[10];
        byte[] ssrc = subBytes(mReceiveData, 11, mReceiveData.length < 18 ? 4 : 10);
        int parsedSSRC = parseSSRC(ssrc);
        short ret = listenerCallBack.stopPlayCallBack(channel, parsedSSRC);
        byte[] dataDomain = byteMerger(new byte[]{(byte) channel, hi(ret), lo(ret)}, ssrc);

        Log.i(Log.TAG, String.format("录像下载取消：通道=%d，SSRC=%d，结果码=%d", channel, parsedSSRC, ret));
        sendPack(ORDER_9EH, null, dataDomain);
    }
    /////

    /**
     * 主站请求与终端进行语音广播（控制字：A2H），参考规约第7.62.1节。
     * 主站请求终端启动语音广播。
     * 数据域结构：
     * - [10] 通道号
     * - [11] 语音编码类型
     * - [12] 采样率
     * - [13] 位宽
     * - [14] 位宽
     * - [15~30] 发送流IP地址
     * - [31~32] 发送流端口
     * - [33~36] SSRC
     *
     * @param mReceiveData 原始接收数据帧
     */
    private void doStartVoiceBroadcast(byte[] mReceiveData) { /////
        /////
        if (mReceiveData == null || mReceiveData.length < 37) {
            Log.w(Log.TAG, "主站请求与终端进行语音广播失败：接收数据无效或长度不足");
            sendPack(ORDER_A2H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        listenerCallBack.startVoiceBroadcast(); /////
        sendPack(ORDER_A2H, mReceiveData, null);
        Log.i(Log.TAG, "接收到语音广播请求");
    }

    /**
     * 主站请求与终端断开语音广播（控制字：A3H），参考规约第7.62.2节。
     * 主站通知终端停止语音广播，终端响应确认。
     * 数据域结构：
     * - [10] 通道号
     * - [11~] SSRC（新协议 4 字节，旧协议 10 字节）
     *
     * @param mReceiveData 原始接收数据帧
     */
    private void doStopVoiceBroadcast(byte[] mReceiveData) { /////
        /////
        if (mReceiveData == null || mReceiveData.length < 15) {
            Log.w(Log.TAG, "主站请求与终端断开语音广播失败：接收数据无效或长度不足");
            sendPack(ORDER_A3H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        listenerCallBack.stopVoiceBroadcast(); /////

        byte channel = mReceiveData[10];
        byte[] ssrc = subBytes(mReceiveData, 11, mReceiveData.length < 18 ? 4 : 10);
        byte[] dataDomain = byteMerger(new byte[]{channel, 0, 0}, ssrc);
        sendPack(ORDER_A3H, null, dataDomain);
        Log.i(Log.TAG, String.format("终止语音广播：通道=%d，SSRC=%d", channel, parseSSRC(ssrc)));
    }

    /**
     * 智能分析参数配置（控制字：A4H），参考规约第7.63.1节。
     * 主站下发检测使能、告警类型及区域配置等 AI 参数。
     * 数据域结构：
     * - [10] 密码
     * - [14] 通道号
     * - [15] 预置位号
     * - [16] 智能分析启用标志
     * - [17] 告警类型数量
     * - 后续 n × 2 字节：每个类型（告警类型 + 告警阈值）
     * - [18 + 2×n~] 区域数量 m
     * - 每个区域：
     *     - [1] 启用标志
     *     - [2] 坐标点数目 P
     *     - [2×P] 坐标点（X,Y）
     *
     * @param data 原始接收数据帧
     */
    private void doSetAIParameter(byte[] data) {
        /////
        if (data == null || data.length < 18) {
            Log.w(Log.TAG, "智能分析参数配置失败：接收数据无效或长度不足");
            sendPack(ORDER_A4H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String password = getReceivePassword(data);
        if (!password.equals(this.password)) {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_A4H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        sendPack(ORDER_A4H, data, null);

        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.position(14);

        Settings.AIParameter aiParameter = new Settings.AIParameter();
        aiParameter.channel = byteBuffer.get() & 0xff;
        aiParameter.preset = byteBuffer.get() & 0xff;
        aiParameter.enable = byteBuffer.get() & 0xff;

        int alertTypeCount = byteBuffer.get() & 0xff;
        aiParameter.alertTypes = new Settings.AIAlertType[alertTypeCount];
        for (int i = 0; i < alertTypeCount; i++) {
            aiParameter.alertTypes[i] = new Settings.AIAlertType();
            aiParameter.alertTypes[i].alertType = byteBuffer.get() & 0xff;
            aiParameter.alertTypes[i].alertThreshold = byteBuffer.get() & 0xff;
        }

        int regionCount = byteBuffer.get() & 0xff;
        aiParameter.alertRegions = new Settings.AIAlertRegion[regionCount];
        for (int i = 0; i < regionCount; i++) {
            aiParameter.alertRegions[i] = new Settings.AIAlertRegion();
            aiParameter.alertRegions[i].enable = byteBuffer.get() & 0xff;
            int coordCount = byteBuffer.get() & 0xff;
            aiParameter.alertRegions[i].coordinates = new Point[coordCount];
            for (int j = 0; j < coordCount; j++) {
                int x = byteBuffer.get() & 0xff;
                int y = byteBuffer.get() & 0xff;
                aiParameter.alertRegions[i].coordinates[j] = new Point(x, y);
            }
        }

        listenerCallBack.setAIParameters(aiParameter);
        Log.i(Log.TAG, String.format("设置智能分析参数成功：通道=%d，预置位=%d，类型数=%d，区域数=%d",
                aiParameter.channel, aiParameter.preset, alertTypeCount, regionCount));
    }

    /**
     * 智能分析参数查询（控制字：A5H），参考规约第7.63.2节。
     * 主站请求终端返回指定通道与预置位的 AI 参数设置。
     * 数据域结构：
     * - [10] 通道号
     * - [11] 预置位号
     *
     * @param data 原始接收数据帧
     */
    private void doGetAIParameter(byte[] data) {
        /////
        if (data == null || data.length < 12) {
            Log.w(Log.TAG, "查询智能分析参数失败：接收数据无效或长度不足");
            sendPack(ORDER_A5H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        int channel = data[10] & 0xff;
        int preset = data[11] & 0xff;
        List<Settings.AIParameter>  parameters = listenerCallBack.getAIParameters(channel, preset);
        if (parameters == null || parameters.size() == 0 ) {
            sendPack(ORDER_A5H, null, new byte[]{(byte) channel, 0, 0, (byte) 0, 0});
            return;
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (Settings.AIParameter item: parameters) {
            bos.reset();
            bos.write(channel);  // 通道号
            bos.write(0);  // 结果码
            bos.write(item.enable);  // 智能分析启用标志
            bos.write(item.preset);  // 预置位号
            bos.write(parameters.size());  // 预置位总数
            bos.write(item.alertTypes.length);  // 告警类型数量
            for (int i = 0; i < item.alertTypes.length; i++) {
                bos.write(item.alertTypes[i].alertType);  // 告警类型
                bos.write(item.alertTypes[i].alertThreshold);  // 告警阈值
            }
            bos.write(item.alertRegions.length);  // 告警区域数量
            for (int i = 0; i < item.alertRegions.length; i++) {
                bos.write(item.alertRegions[i].enable);  // 区域作用标志
                bos.write(item.alertRegions[i].coordinates.length);  // 区域坐标点数目
                for (int j = 0; j < item.alertRegions[i].coordinates.length; j++) {
                    bos.write(item.alertRegions[i].coordinates[j].x);  // 区域坐标点X坐标
                    bos.write(item.alertRegions[i].coordinates[j].y);  // 区域坐标点Y坐标
                }
            }
            sendPack(ORDER_A5H, null, bos.toByteArray());
            Log.i(Log.TAG, String.format("发送智能分析参数：通道=%d，预置位=%d，类型数=%d，区域数=%d",
                    channel, item.preset, item.alertTypes.length, item.alertRegions.length));
        }
    }

    /**
     * 智能分析类型查询（控制字：A6H），参考规约第7.63.3节。
     * 主站请求设备返回当前支持的 AI 检测类型。
     * 数据域结构：
     * - [10] 通道号
     *
     * @param data 原始接收数据帧
     */
    private void doGetAIAbility(byte[] data) { /////
        /////
        if (data == null || data.length < 11) {
            Log.w(Log.TAG, "获取智能分析类型失败：接收数据无效或长度不足");
            sendPack(ORDER_A6H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        byte channel = data[10];
//        byte[] dataDomain = new byte[]{channel, 18, 1, 2, 3, 4, 5, 6, 7, 8, 20, 30, 31, 32, 40, 41, 50, 51, 52, 53};
        // 以下为支持的 AI 类型 ID
        byte[] dataDomain = new byte[]{channel, 8, 1, 2, 4, 5, 7, 33, 40, 41};  // ！！！
        sendPack(ORDER_A6H, null, dataDomain);

    }

    /**
     * 智能分析告警上报（控制字：A7H），参考规约第7.63.4节。
     * 每条告警包含告警时间、通道、预置位，以及若干检测到的对象信息。
     * 数据域结构：
     * - [0]   通道号
     * - [1]   预置位号
     * - [2~7] 告警时间（年、月、日、时、分、秒）
     * - [8]   对象数量
     * - 后续 n × 6 字节的目标数据（每个对象）：
     *     - [0] 类型（classID）
     *     - [1] 置信度
     *     - [2] 左上X（left）
     *     - [3] 左上Y（top）
     *     - [4] 右下X（right）
     *     - [5] 右下Y（bottom）
     *
     * @param info 智能分析检测结果（Settings.DetectInfo）
     */
    public void doAlertObjectDetect(Settings.DetectInfo info) { /////
        byte[] dataDomain = new byte[9 + info.objects.size() * 6];
        dataDomain[0] = info.channel;  // 通道号
        dataDomain[1] = info.preset;  // 预置位
        dataDomain[2] = info.time.year;  // 告警时间：年
        dataDomain[3] = info.time.month;  // 告警时间：月
        dataDomain[4] = info.time.day;  // 告警时间：日
        dataDomain[5] = info.time.hour;  // 告警时间：时
        dataDomain[6] = info.time.minute;  // 告警时间：分
        dataDomain[7] = info.time.second;  // 告警时间：秒
        dataDomain[8] = (byte) info.objects.size();  // 告警目标数量
        for (int i = 0; i < info.objects.size(); i++) {
            Settings.ObjectInfo item = info.objects.get(i);
            dataDomain[9 + i * 6] = item.classID;  // 告警类型
            dataDomain[10 + i * 6] = item.confidence;  // 告警置信度
            dataDomain[11 + i * 6] = item.left;  // 告警区域：左上角X坐标
            dataDomain[12 + i * 6] = item.top;  // 告警区域：左上角Y坐标
            dataDomain[13 + i * 6] = item.right;  // 告警区域：右下角X坐标
            dataDomain[14 + i * 6] = item.bottom;  // 告警区域：右下角Y坐标
        }

        // 发送帧数据，控制字A7H
        sendPack(ORDER_A7H, null, dataDomain);

        Log.i(Log.TAG, String.format(
                "上报智能分析告警：通道=%d，预置位=%d，目标数=%d，时间=%s",
                info.channel, info.preset, info.objects.size(), info.time.asString
        ));
    }

    private void doAIHistory(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.position(10);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int x = buffer.getShort() & 0xffff;
        if (x == 0xFFFF) {
            // TODO: 回传所有未回传的告警信息
        }
    }

    /**
     * 联动参数配置（控制字：A8H），参考规约第7.63.5节。
     * 主站下发联动设置，用于配置某通道和预置位下的联动响应规则。
     * 数据域结构：
     * - [10~13] 密码
     * - [14]    通道号
     * - [15]    预置位
     * - [16]    联动配置数目
     * - 后续每个联动配置结构（共 6 字节）：
     *     [0]   联动触发类型
     *     [1]   联动动作类型
     *     [2~3] 参数1
     *     [4~5] 参数2
     *
     * @param data 原始接收数据帧
     */
    private void doSetAIActionSettings(byte[] data) {
        /////
        if (data == null || data.length < 17) {
            Log.w(Log.TAG, "联动参数配置失败：接收数据无效或长度不足");
            sendPack(ORDER_A8H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String password = getReceivePassword(data);
        if (!password.equals(this.password)) {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_A8H, null, ERROR_FFFF_DOMAIN);
            return;
        }

        sendPack(ORDER_A8H, data, null);
        ByteBuffer buffer = ByteBuffer.wrap(data);
//        buffer.order(ByteOrder.LITTLE_ENDIAN);  // 服务器用大端发送
        buffer.position(14);
        int channel = buffer.get() & 0xff;
        int preset = buffer.get() & 0xff;
        int count = buffer.get() & 0xff;
        Settings.AIAction[] actions = new Settings.AIAction[count];
        for (int i = 0; i < count; i++) {
            actions[i] = new Settings.AIAction();
            actions[i].alertType = buffer.get() & 0xff;
            actions[i].alertAction = buffer.get() & 0xff;
            actions[i].alertParam1 = buffer.getShort() & 0xffff;
            actions[i].alertParam2 = buffer.getShort() & 0xffff;
        }
        listenerCallBack.setAIAction(channel, preset, actions);
        Log.i(Log.TAG, String.format("联动参数配置成功：通道=%d，预置位=%d，规则数=%d", channel, preset, count));
    }

    /**
     * 联动参数查询（控制字：A9H），参考规约第7.63.6节。
     * 主站查询指定通道与预置位的联动响应设置。
     * 数据域结构：
     * - [0] 结果码
     * - [1] 通道号
     * - [2] 预置位
     * - [3] 联动配置数目
     * - 后续每个联动配置结构（共 6 字节）：
     *     [0]   联动触发类型
     *     [1]   联动动作类型
     *     [2~3] 参数1
     *     [4~5] 参数2
     *
     * @param data 原始接收数据帧
     */
    private void doGetAIActionSettings(byte[] data) {
        /////
        if (data == null || data.length < 12) {
            Log.w(Log.TAG, "联动参数查询失败：接收数据无效或长度不足");
            sendPack(ORDER_A9H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        int channel = data[10] & 0xff;
        int preset = data[11] & 0xff;
        Settings.AIAction[] actions = listenerCallBack.getAIAction(channel, preset);
        ByteBuffer buffer = ByteBuffer.allocate(4 + actions.length * 6);
//        buffer.order(ByteOrder.LITTLE_ENDIAN);  // 这个小端不要打开，关闭的时候测是正常的
        buffer.put((byte) 0);               // 结果码
        buffer.put((byte) channel);         // 通道号
        buffer.put((byte) preset);          // 预置位号
        buffer.put((byte) actions.length);  // 联动配置数目
        for (Settings.AIAction item: actions) {
            buffer.put((byte) item.alertType);
            buffer.put((byte) item.alertAction);
            buffer.putShort((short) item.alertParam1);
            buffer.putShort((short) item.alertParam2);
        }
        sendPack(ORDER_A9H, null, buffer.array());
        Log.i(Log.TAG, String.format("联动参数查询成功：通道=%d，预置位=%d，规则数=%d", channel, preset, actions.length));
    }

    /**
     * 接收智能分析告警应答（控制字：AAH），已弃用。
     * 主站接收到设备 A7H 上报后，返回接收状态（成功/失败），设备可据此判断是否重发。
     * 数据域结构：
     * - [10] 通道号
     * - [11] 预置位
     * - [12~17] 时间
     * - [18] 接收状态（0x00=成功，0x01=失败）
     *
     * @param data 原始接收数据帧
     */
    private Settings.DetectInfo alertRespond = new Settings.DetectInfo();
    private void doAlertResponse(byte[] data) {
        /////
        if (data == null || data.length < 19) {
            Log.w(Log.TAG, "接收智能分析告警应答失败：接收数据无效或长度不足");
            sendPack(ORDER_AAH, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        try {
            if (data[18] != 0) return; // 接收状态：0x00 接收成功， 0x01 接收失败。
            synchronized (alertRespond) {
                alertRespond.channel = data[10];
                alertRespond.preset = data[11];
                alertRespond.time = new Settings.TimeRecord(subBytes(data, 12, 6));
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "接收智能分析告警应答异常：" + e);
        }
        // TODO 待完成，需要保存上报成功记录，避免后续再次上报
    }

    /**
     * 摄像机3D控球调节（控制字：B1H），参考规约第7.60.24节。
     * 主站请求终端对摄像机进行三维坐标控制（如云台方向、变倍等）。
     * 数据域结构：
     * - [14] 通道号
     * - [15] 起点x坐标
     * - [16] 起点y坐标
     * - [17] 终点x坐标
     * - [18] 终点y坐标
     *
     * @param raw 原始接收数据帧
     */
    private void doStart3DBallControl(byte[] raw) { /////
        /////
        if (raw == null || raw.length < 19) {
            Log.w(Log.TAG, "摄像机3D控球调节失败：接收数据无效或长度不足");
            sendPack(ORDER_B1H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String passwords = getReceivePassword(raw);
        if (this.password.equals(passwords)) {
            sendPack(ORDER_B1H, raw, null);
            listenerCallBack.ptz3DCtrl(toInt(raw[14]), toInt(raw[15]), toInt(raw[16]), toInt(raw[17]), toInt(raw[18]));
            Log.i(Log.TAG, String.format("3D控球命令下发成功：通道号=%d，起点x坐标=%d，起点y坐标=%d，终点x坐标=%d，终点y坐标=%d",
                    toInt(raw[14]), toInt(raw[14]), toInt(raw[14]), toInt(raw[14]), toInt(raw[14])));
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", passwords, this.password));
            sendPack(ORDER_B1H, null, ERROR_FFFF_DOMAIN);
        }
    }

    /**
     * 摄像机巡航参数设置（控制字：B2H），参考规约第7.60.25节。
     * 主站配置巡航通道、组号、位置序列、停留时间等参数。
     * 数据域结构：
     * - [14] 通道号
     * - [15] 配置指令
     * - [16] 巡航组号
     * - [17] 巡航点序号
     * - [18] 巡航预置位号
     * - [19] 巡航停留时间
     * - [20] 巡航速度
     *
     * @param mReceiveData 原始接收数据帧
     */
    private void doSetCruiseConfig(byte[] mReceiveData) { /////
        String password = getReceivePassword(mReceiveData);
        if (password.equals(this.password)) {
            listenerCallBack.setPTZCruise(mReceiveData[14], mReceiveData[15], mReceiveData[16], mReceiveData[17], mReceiveData[18], mReceiveData[19], mReceiveData[20]);
            sendPack(ORDER_B2H, mReceiveData, null);
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_B2H, null, ERROR_FFFF_DOMAIN);
        }
    }

    /**
     * 摄像机巡航参数查询（控制字：B3H），参考规约第7.60.26节。
     * 主站请求终端返回指定通道的所有巡航组配置，包括每组中的点位、预置位号、停留时长等。
     * 数据域结构：
     * - [0] 通道号
     * - [1] 结果码
     * - [2] 巡航组数量
     * - 每个巡航组：
     *   - [0] 组号
     *   - [1] 该组点序列数
     *   - 每点：
     *     - [0] 点序号
     *     - [1] 预置位号
     *     - [2] 停留时间
     *     - [3] 速率
     *
     * @param channel
     */
    private void doGetCruise(byte channel) {
        byte[] dataDomain;
        Settings.CruiseGroup[] groups = listenerCallBack.getPTZCruise(channel & 0xFF);

        if (groups != null) try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);

            dos.writeByte(channel);  // 通道
            dos.writeByte(0);  // 结果码
            dos.writeByte(groups.length);  // 巡航组数量
            for (int i = 0; i < groups.length; i++) {
                dos.writeByte(groups[i].group);  // 组号
                byte len = (byte) (groups[i].cruises == null ? 0 : groups[i].cruises.size());
                dos.write(len);  // 该组点序列数
                for (int j = 0; j < len; j++) {
                    dos.write((byte) j + 1);  // 点序号
                    dos.write(groups[i].cruises.get(j).preset);  // 预置位号
                    dos.write(groups[i].cruises.get(j).duration);  // 停留时间
                    dos.write(groups[i].cruises.get(j).speed);  // 速率
                }
            }
            dataDomain = bos.toByteArray();
            bos.close();
            dos.close();
        } catch (Exception e) {
            Log.e(Log.TAG, "巡航参数查询异常：" + e.getMessage());
            dataDomain = new byte[]{channel, 1, 0};
        }
        else {
            dataDomain = new byte[]{channel, 1, 0};
        }
        sendPack(ORDER_B3H, null, dataDomain);
    }

    /////
    /**
     * 装置电源/视频关闭通知（控制字：B4H），参考规约第7.60.27节。
     * 如果主站发送通知码为1时，通知装置关闭电源/视频电源。
     * 如果主站发送通知码为2时，通知装置关闭视频。
     * 数据域结构：
     * - [10~13] 密码
     * - [14] 通道号
     * - [15] 通知码
     *
     * @param raw 原始接收数据帧
     */
    public void doCamOff(byte[] raw) {
        if (raw == null || raw.length < 14) {
            Log.w(Log.TAG, "主站发送装置电源/视频关闭通知失败：接收数据无效或长度不足");
            sendPack(ORDER_B4H, null, ERROR_FFFF_DOMAIN);
            return;
        }

        String password = getReceivePassword(raw);

        if (!this.password.equals(password)) {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_B4H, null, ERROR_FFFF_DOMAIN);
        } else {
            for (Device dev : MainActivity.channels.values()) {
                if (dev.isDVR() && raw[15] == 1) {
//                    listenerCallBack.doSleep();
                    listenerCallBack.ptzControl(1, 18, 5); ///////
                    Log.w(Log.TAG, String.format("主站发送装置通道=%d电源关闭通知", raw[14]));
                }
                if (raw[15] == 2) {
                    listenerCallBack.stopLiveVideo(raw[14], 0, dev.ssrcLive);
                    Log.w(Log.TAG, String.format("主站发送装置通道=%d视频关闭通知", raw[14]));
                }
            }
        }
    }
    /////

    public void doCamOff(int channel, int action) {
        sendPack(ORDER_B4H, null, new byte[] {(byte) channel, (byte) action});
    }

    /**
     * 摄像机巡检参数设置（控制字：B5H），参考规约第7.60.28节。
     * 用于设置单个巡检组的配置，如组编号、动作、通道等。
     * 数据域结构：
     * - [10~13] 密码
     * - [14]    通道号
     * - [15]    配置指令
     * - [16]    巡检组号
     * - [17]    巡检点序号
     *
     * @param data 原始接收数据帧
     */
    private void doSetInspectionParameters(byte[] data) { /////
        /////
        if (data == null || data.length < 18) {
            Log.w(Log.TAG, "摄像机巡检参数设置失败：接收数据无效或长度不足");
            sendPack(ORDER_B5H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        int resultCode; ///////
        String password = getReceivePassword(data); /////
        if (password.equals(this.password)) { /////
            listenerCallBack.setCheckGroup(data[14], data[15], data[16], data[17]);
            sendPack(ORDER_B5H, data, null);
            Log.i(Log.TAG, String.format("摄像机巡检参数设置成功：通道=%d，组=%d，类型=%d，参数=%d",
                    data[14], data[15], data[16], data[17]));
            resultCode = 0; ///////
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password)); /////
            sendPack(ORDER_B5H, null, ERROR_FFFF_DOMAIN);
            resultCode = 1; ///////
        }
    }

    /**
     * 摄像机巡检参数查询（控制字：B6H），参考规约第7.60.29节。
     * 主站查询指定通道与预置位的联动响应设置。
     * 数据域结构：
     * - [0] 通道号
     * - [1] 结果码
     * - [2] 巡检组数目
     * - [3~3 + n*2] 每个巡检组：
     *     - [0] 巡检组号
     *     - [1] 巡检点数目
     *
     * @param channel 通道号
     */
    private void doQueryInspectionParameters(int channel) { /////
        List<Settings.CheckGroup> groups = listenerCallBack.getCheckGroup(channel);
        byte[] dataDomain;

        if (groups == null) {
            dataDomain = new byte[]{(byte) channel, 0, 0};
        } else {
            dataDomain = new byte[groups.size() * 2 + 3];
            dataDomain[0] = (byte) channel;  // 通道号
            dataDomain[1] = 0;  // 结果码
            dataDomain[2] = (byte) groups.size();  // 巡检组数目
            for (int i = 0; i < groups.size(); i++) {
                dataDomain[3 + i * 2] = (byte) groups.get(i).index;  // 巡检组号
                dataDomain[4 + i * 2] = (byte) groups.get(i).points.size();  // 巡检点数目
                Log.i(Log.TAG, String.format("巡检组：组号=%d，点数=%d", groups.get(i).index, groups.get(i).points.size()));
            }
            Log.i(Log.TAG, String.format("巡检参数查询成功，通道号=%d", channel));
        }
        sendPack(ORDER_B6H, null, dataDomain);
    }

    /**
     * 摄像机巡检策略设置（控制字：B7H），参考规约第7.60.30节。
     * 主站设置指定通道的巡检执行计划表。
     * 数据域结构：
     * - [14] 通道号
     * - [15] 线路巡检配置数目
     * - 每个线路巡检配置：
     *   - [0] 巡检使能标志
     *   - [1] 巡检组号
     *   - [2] 巡检次数
     *   - [3] 开始时间：时
     *   - [4] 开始时间：分
     *   - [5] 开始时间：秒
     *
     * @param data 原始接收数据帧
     */
    private void doSetCheckLineSchedule(byte[] data) { /////
        /////
        if (data == null || data.length < 16) {
            Log.w(Log.TAG, "摄像机巡检策略查询失败：接收数据无效或长度不足");
            sendPack(ORDER_B7H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        int resultCode; ///////
        String password = getReceivePassword(data); /////
        if (password.equals(this.password)) { /////
            List<Settings.CheckScheduleItem> items = new ArrayList<>();
            for (int i = 0; i < data[15]; i++) {
                Settings.CheckScheduleItem item = new Settings.CheckScheduleItem();
                item.enable = data[16 + i * 6] == 0 ? false : true;  // 巡检使能标志
                item.group = data[16 + i * 6 + 1];  // 巡检组号
                item.count = data[16 + i * 6 + 2];  // 巡检次数
                item.hour = data[16 + i * 6 + 3];  // 开始时间：时
                item.minute = data[16 + i * 6 + 4];  // 开始时间：分
                item.second = data[16 + i * 6 + 5];  // 开始时间：秒
                items.add(item);
            }
            listenerCallBack.setCheckLineSchedule(data[14], items);
            sendPack(ORDER_B7H, data, null);
            Log.i(Log.TAG, String.format("巡检时间表设置成功，通道：%d，配置数：%d", data[14], data[15]));
            resultCode = 0; ///////
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password)); /////
            sendPack(ORDER_B7H, null, ERROR_FFFF_DOMAIN);
            resultCode = 1; ///////
        }
    }

    /**
     * 摄像机巡检策略查询（控制字：B8H），参考规约第7.60.31节。
     * 主站请求终端返回指定通道的巡检执行计划表（每天定时执行的巡检组安排）。
     * 数据域结构：
     * - [0] 通道号
     * - [1] 结果码
     * - [2] 线路巡检配置数目
     * - 每个线路巡检配置：
     *   - [0] 巡检使能标志
     *   - [1] 巡检组号
     *   - [2] 巡检次数
     *   - [3] 开始时间：时
     *   - [4] 开始时间：分
     *   - [5] 开始时间：秒
     *
     * @param channel 通道号
     */
    private void doGetCheckLineSchedule(int channel) { /////
        List<Settings.CheckScheduleItem> schedule = listenerCallBack.getCheckLineSchedule(channel);
        if (schedule == null) {
            sendPack(ORDER_B8H, null, new byte[]{(byte) channel, 0x01});
            return;
        }

        byte[] dataDomain = new byte[3 + 6 * schedule.size()];
        dataDomain[0] = (byte) channel;  // 通道号
        dataDomain[1] = 0;  // 结果码
        dataDomain[2] = (byte) schedule.size();  // 线路巡检配置数目
        for (int i = 0; i < schedule.size(); i++) {
            Settings.CheckScheduleItem csi = schedule.get(i);
            dataDomain[3 + i * 6] = (byte) (csi.enable ? 1 : 0);  // 巡检使能标志
            dataDomain[3 + i * 6 + 1] = csi.group;  // 巡检组号
            dataDomain[3 + i * 6 + 2] = csi.count;  // 巡检次数
            dataDomain[3 + i * 6 + 3] = csi.hour;  // 开始时间：时
            dataDomain[3 + i * 6 + 4] = csi.minute;  // 开始时间：分
            dataDomain[3 + i * 6 + 5] = csi.second;  // 开始时间：秒
        }
        sendPack(ORDER_B8H, null, dataDomain);
        Log.i(Log.TAG, String.format("查询巡检时间表成功，通道=%d，计划数=%d", channel, schedule.size()));
    }

    /**
     * 主站请求设备升级更新（控制字：CAH），参考规约第7.65.1节。
     * 主站请求终端开始接收升级文件。支持普通升级和断点续传模式。
     * 数据域结构：
     * - [14] 通道号：0表示对装置进行升级，1及以上数值表示对装置接入的通道设备进行升级。目前只支持0
     * - [15~78] 文件名：ASCII码可打印字符，不允许包含中文字符（最多64字节，需trim）
     * - [79~82] 总包数量：文件分包的数量（int）
     * - [83~] 升级类型：0=普通升级，1=断点续传（1字节）
     *  1：当前设备正在升级
     * 2：当前电量不够
     * 3：当前流量不够
     * 4：参数不对
     * 5：未接收到该升级文件的分包。
     * @param mReceiveData 原始接收数据帧
     */
    private void doUpgradeInformation(byte[] mReceiveData) { /////
        /////
        if (mReceiveData == null || mReceiveData.length < 83) {
            Log.w(Log.TAG, "主站请求设备升级更新失败：接收数据无效或长度不足");
            sendPack(ORDER_CAH, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String password = getReceivePassword(mReceiveData);
        if (password != null && password.endsWith(this.password)) {
            try {
                byte channel = mReceiveData[14];

                // 错误码：
                // 1：当前设备正在升级
                // 2：当前电量不够
                // 3：当前流量不够
                // 4：参数不对
                // 5：未接收到该升级文件的分包
                // 超过1分钟收不到升级相关的指令，可以重新升级
                if (isUpgrade && Math.abs(System.currentTimeMillis() - latestUpgradeReceived) < PERIOD_MINUTE) {
                    sendPack(ORDER_CAH, null, new byte[]{channel, 0x00, 0x01});  // 状态码 0x01：正在升级中
                } else {
                    // 重置升级状态
                    upgradeReset();
                    latestUpgradeReceived = System.currentTimeMillis();
                    // 当前只支持通道 0
                    if (channel == 0) {
                        File file = new File(upgradeFilePath);
                        if (!file.exists()) file.mkdirs();
                        upgradeFileName = new String(subBytes(mReceiveData, 15, 64)).trim();
                        InputStream bas = new ByteArrayInputStream(subBytes(mReceiveData, 79, 5));
                        DataInputStream data = new DataInputStream(bas);
                        upgradeFileCount = data.readInt();
                        if (mReceiveData.length >= 86) {  // 这是扩展协议升级请求包，多了升级类型字节
                            upgradeType = data.readByte();
                        }
                        data.close();
                        bas.close();
                        Log.d(Log.TAG, String.format("远程升级：%s，包数：%d，升级类型：%s",
                                upgradeFileName, upgradeFileCount, (upgradeType == 0 ? "普通升级" : "断点续传")));
                        // 回复主站
                        if (upgradeType == 1) {
                            sendPack(ORDER_CAH, null, new byte[]{channel, 0x00, 0x05});  // 断点续传模式标识
                        } else {
                            sendPack(ORDER_CAH, mReceiveData, null);
                        }
                        listenerCallBack.onUpgradeStart();
                        isUpgrade = true;
                    } else {
                        sendPack(ORDER_CAH, null, new byte[]{0, 0, 4});  // 状态码 0x04：参数错误
                    }
                }
            } catch (Exception e) {
                isUpgrade = false;
                Log.e(Log.TAG, "升级失败：" + e.getMessage());
            }
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_CAH, null, ERROR_FFFF_DOMAIN);
        }
    }


    private void upgradeReset() {
        try {
            // 如果升级失败，需要重置这两个参数才能再次升级
            if (isUpgrade) isUpgrade = false;
            if (fosUpgradeFile != null) {
                fosUpgradeFile.close();
                fosUpgradeFile = null;
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "重置升级参数异常：" + e);
        }
    }

    /**
     * 主站下发升级文件（控制字：CBH），参考规约第7.65.2节。
     * 每次调用接收一个数据包，并写入到升级文件指定位置。
     * 数据域结构：
     * [10] 通道号
     * [11~14] 子包包号（int，从1开始）
     * [15~] 数据
     *
     * @param mReceiveData 原始接收数据帧
     */
    private void doReceiveUpgradeFile(byte[] mReceiveData) { /////
        /////
        if (mReceiveData == null || mReceiveData.length < 15) {
            Log.w(Log.TAG, "主站下发升级文件失败：数据无效或长度不足");
            sendPack(ORDER_CBH, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        latestUpgradeReceived = System.currentTimeMillis();
        if (!isUpgrade) {
            Log.w(Log.TAG, "未处于升级状态，忽略升级数据包");
            return;
        }
        int resultCode; ///////
        try {
            // 读取包序号（减1用于数组索引）
            InputStream bas = new ByteArrayInputStream(subBytes(mReceiveData, 11, 4));
            DataInputStream data = new DataInputStream(bas);
            int packageNumb = data.readInt() - 1;  // 包号从 1 开始
            data.close();
            bas.close();

            // 提取数据负载（负载数据 = 总长 - 前17字节）
            byte[] load = subBytes(mReceiveData, 15, mReceiveData.length - 17);
            if (fosUpgradeFile == null) {
                upgradePacketReady = new boolean[upgradeFileCount];
                upgradePacketSize = ((mReceiveData[8] & 0xFF) << 8) | (mReceiveData[9] & 0xFF) - 5;  // 实际负载长度（去掉包号字段）
                deleteFile(upgradeFilePath + upgradeFileName);
                fosUpgradeFile = new RandomAccessFile(upgradeFilePath + upgradeFileName, "rw");
                fosUpgradeFile.setLength(upgradePacketSize * (upgradeFileCount - 1));
                Log.i(Log.TAG, "初始化升级文件流，文件长度：" + fosUpgradeFile.length());
            }
            // 写入指定位置
            fosUpgradeFile.seek(upgradePacketSize * (packageNumb));
            fosUpgradeFile.write(load);
            upgradePacketReady[packageNumb] = true;
            Log.d(Log.TAG, String.format("成功接收升级包 #%d（%d 字节）", packageNumb + 1, load.length));
            resultCode = 0; ///////
        } catch (Exception e) {
            Log.e(Log.TAG, "接收升级包错误：" + e.getMessage());
            resultCode = 1; ///////
        }
    }

    /**
     * 主站下发升级数据结束标志（控制字：CCH），参考规约第7.65.3节；升级文件补包数据上传（控制字：CDH），参考规约第7.65.4节；升级结果查询上报（控制字：CEH），参考规约第7.65.5节。
     * 统计未接收到的包数，并回复缺失列表；若无缺包，则完成升级并启动安装。
     * 数据域结构：
     * [10] 通道号
     *
     * @param mReceiveData 原始接收数据帧
     */
    private void doReceiveUpgradeEnd(final byte[] mReceiveData) { /////
        /////
        if (mReceiveData == null || mReceiveData.length < 11) {
            Log.w(Log.TAG, "主站下发升级数据结束标志失败：数据无效或长度不足");
            sendPack(ORDER_CCH, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        final byte channel = mReceiveData[10];
        latestUpgradeReceived = System.currentTimeMillis();
        try {
            ByteArrayOutputStream baoReturn = new ByteArrayOutputStream();
            DataOutputStream dosReturn = new DataOutputStream(baoReturn);
            ByteArrayOutputStream baoBuffer = new ByteArrayOutputStream();
            DataOutputStream dosBuffer = new DataOutputStream(baoBuffer);

            int count = 0;
            // 遍历包标记数组，记录未接收的包号
            if (upgradePacketReady != null) {
                for (int i = 0; i < upgradePacketReady.length; i++) {
                    if (!upgradePacketReady[i]) {
                        count++;
                        dosBuffer.writeInt(i + 1);  // 包号从 1 开始
                        if (count >= 350) break;  // 最多返回350个缺包
                    }
                }
            }
            dosBuffer.close();

            baoReturn.write(channel);
            dosReturn.writeInt(count);
            baoReturn.write(baoBuffer.toByteArray());
            Log.i(Log.TAG, "升级补包数量：" + count);
            sendPack(ORDER_CDH, null, baoReturn.toByteArray());

            if (count == 0) {  // 缺包数量为0，传输成功
                // 上报服务器进度100%
                sendPack(ORDER_CEH, null, new byte[]{channel, 100});

                byte[] startTime = getByteTime(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
                byte[] startVersionNames = new byte[128];
                byte[] endVersionNames = new byte[128];
                byte[] endTime = getByteTime(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));

                boolean ok = false;
                try {
                    SystemClock.sleep(5000); // 等待其他线程写入所有数据包！
                    fosUpgradeFile.close();
                    fosUpgradeFile = null;
                    /*
                     * 分为两种情况，zip升级和apk升级
                     * zip升级，只修改配置文件config和settings，修改成功才能进行后续的install apk，不成功需要回滚
                     * apk升级和原来一样
                     * */
                    if (upgradeFileName.endsWith(".zip")){
                        // 解压文件
                        ZipUtils.unzip(upgradeFilePath, upgradeFileName);   // DATA_DIR + upgradeFileName + 文件
                        // 备份源文件
                        UpgradeFileUtils.backupConfig(DATA_DIR, "temp");   // 只对config和settings文件进行备份
                        // 合并文件
                        String targetDir = DATA_DIR + JsonUtils.getFileNameWithoutExtension(upgradeFileName);

                        Boolean configMergeState =  ConfigMergeUtil.mergeConfig(DATA_DIR + JsonUtils.getFileNameWithoutExtension(upgradeFileName) +"/config.json",DATA_DIR + "config.json");
                        Boolean SettingsMerge =  SettingsMergeUtil.mergeSettings(DATA_DIR + JsonUtils.getFileNameWithoutExtension(upgradeFileName) +"/settings.json",DATA_DIR + "settings.json");
                        // 移动APK文件
                        ZipUtils.moveSameNameApk(targetDir,upgradeFilePath);   // 从解压文件中移动APK文件

                        File upgradeFile = new File(upgradeFilePath, upgradeFileName);
                        byte[] startVersionName = appVersion(context).getBytes();
                        System.arraycopy(startVersionName, 0, startVersionNames, 0, startVersionName.length);
                        byte[] endVersionName = getApkVersion(context, upgradeFile.getPath()).getBytes();
                        System.arraycopy(endVersionName, 0, endVersionNames, 0, endVersionName.length);

                        Log.d(Log.TAG, "升级准备完成，开始安装……");

                        upgradeAPPFileName = upgradeFileName.replace(".zip", ".apk");   // 获取APK的名字

                        if (configMergeState && SettingsMerge){
                            ok = apkInstall(upgradeFilePath + upgradeAPPFileName);
                            if (ok){
                                UpgradeFileUtils.clearTemp(DATA_DIR,"temp");
                            }else {
                                UpgradeFileUtils.rollbackConfig(DATA_DIR, "temp");
                            }
                        }else {
                            ok = false;
                            UpgradeFileUtils.rollbackConfig(DATA_DIR, "temp");
                        }
                    }else {
                        //// 只用APK进行升级
                        File upgradeFile = new File(upgradeFilePath, upgradeFileName);
                        byte[] startVersionName = appVersion(context).getBytes();
                        System.arraycopy(startVersionName, 0, startVersionNames, 0, startVersionName.length);
                        byte[] endVersionName = getApkVersion(context, upgradeFile.getPath()).getBytes();
                        System.arraycopy(endVersionName, 0, endVersionNames, 0, endVersionName.length);
                        Log.d(Log.TAG, "升级准备完成，开始安装……");
                        upgradeAPPFileName = upgradeFileName;
                        ok = apkInstall(upgradeFilePath + upgradeAPPFileName);
                    }

                    //if (ok)升级补包数量
                    {
                        // 升级成功要重启apk生效，升级失败也要重启apk，这样可以删除下载的不正确的apk
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                Log.i(Log.TAG, "重启apk");
                                restartApplication(context, 0);
                            }
                        }, 20 * PERIOD_SECOND);
                    }
                    Log.d(Log.TAG, "升级安装结果: " + ok);
                    endTime = getByteTime(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date()));
                } catch (Exception e) {
                    Log.e(Log.TAG, "解包和安装APK失败: " + e.getMessage());
                }

                // 发送升级结果
                baoReturn.reset();
                baoReturn.write(channel);  // 通道号
                baoReturn.write(ok ? 0 : 1);  // 升级结果：0：升级成功；1：文件下载失败；2：升级文件存储空间不足；3：文件格式错误，即不是符合要求的升级文件；4：文件校验出错；5：固件版本与当前硬件不匹配
                baoReturn.write(startTime);  // 升级开始时间
                baoReturn.write(endTime);  // 升级结束时间
                baoReturn.write(startVersionNames);  // 升级前版本号
                baoReturn.write(endVersionNames);  // 升级后版本号
                byte[] dataDomain = baoReturn.toByteArray();
                baoReturn.reset();
                for (int i = 0; i < 5; i++) {
                    sendPack(ORDER_CEH, null, dataDomain);
                    SystemClock.sleep(2 * PERIOD_SECOND);
                }
                isUpgrade = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "主站下发升级数据结束标志错误：" + e.getMessage());
        }
    }

    /**
     * 升级结果查询上报（控制字：CEH），参考规约第7.65.5节。
     *
     * @param mReceiveData 原始接收数据帧
     */
    private void doReceiveUpgradeResult(final byte[] mReceiveData) { /////
        /////
        if (mReceiveData == null || mReceiveData.length < 11) {
            Log.w(Log.TAG, "升级结果查询上报失败：数据无效或长度不足");
            sendPack(ORDER_CEH, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        latestUpgradeReceived = System.currentTimeMillis();
        // 计算当前进度（已收到的包数 ÷ 总包数）
        byte upgradeProgress = (byte) (getUpgradePacketReadyCount() * 100 / upgradeFileCount);
        byte[] dataDomain = new byte[]{mReceiveData[10], upgradeProgress};
        sendPack(ORDER_CEH, null, dataDomain);
        Log.d(Log.TAG, String.format("升级进度上报：%d，通道：%d", upgradeProgress, (int)(mReceiveData[10] & 0xFF)));
    }

    /**
     * 通用事件上报（控制字：CFH），参考扩展规约2.7。
     * 用于上报当前通道 GPS 位置与位移状态。此指令未在规约中明确定义，为扩展功能。
     * 数据域结构：
     * - [0] 通道号（从 0 开始，即 channel - 1）
     * - [1~6] 上报时间（年-月-日-时-分-秒）
     * - [7~8] 保留字段（2 字节，固定填充）
     * - [9~12] 纬度（float，4 字节）
     * - [13~16] 经度（float，4 字节）
     * - [17] 位移标志（0 = 未移动，1 = 已移动）
     *
     * @param curLocation 当前定位信息（android.location.Location）
     * @param moved 是否检测到移动
     * @param channel 实际通道号（从 1 开始）
     */
    public void doReportBds(Location curLocation, boolean moved, int channel) { /////
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            dos.writeByte(channel);  // 通道，从1开始
            Date now = new Date();
            dos.writeByte(now.getYear() - 100);
            dos.writeByte(now.getMonth() + 1);
            dos.writeByte(now.getDate());
            dos.writeByte(now.getHours());
            dos.writeByte(now.getMinutes());
            dos.writeByte(now.getSeconds());
            dos.writeShort(2);
            dos.write(float2Bytes((float) curLocation.getLatitude()));
            dos.write(float2Bytes((float) curLocation.getLongitude()));
            dos.writeByte(moved ? 1 : 0);
            sendPack(ORDER_CFH, null, bos.toByteArray());
        } catch (Exception e) {
            Log.i(Log.TAG, "上报BDS异常：" + e);
        }
    }

    /**
     * 通用参数配置（控制字：D0H），参考扩展规约2.8。
     * 用于设置通道下指定类型的通用参数，当前仅支持类型 0x0004（场景配置参数）。
     * 数据域结构：
     * - [14]：通道号
     * - [15~16]：参数类型（0x0004 表示设置场景参数）
     * - [17]：预置位号
     * - [18]：是否启用（0/1）
     * - [19~82]：场景名称（UTF-8，最长64字节）
     * - [83~86]：区域边界：left, top, right, bottom
     *
     * @param data 原始接收数据帧
     */
    private void doSetGeneralParameters(byte[] data) { /////
        /////
        if (data == null || data.length < 17) {
            Log.w(Log.TAG, "通用参数配置失败：数据无效或长度不足");
            sendPack(ORDER_D0H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        int resultCode; ///////
        String password = getReceivePassword(data);
        if (password != null && password.endsWith(this.password)) {
            int channel = data[14] & 0xFF;
            byte code = 1;  // 默认设备不支持
            int type = ((data[15] & 0xFF) << 8) | (data[16] & 0xFF);
            if (type == 0x04) {
                if (data.length < 87) {
                    Log.w(Log.TAG, "通用参数配置失败：数据无效或长度不足");
                    code = 2;  // 参数不对
                } else {
                    int preset = data[17] & 0xFF;
                    Settings.SceneParameter sceneParameter = new Settings.SceneParameter();
                    sceneParameter.presetNo = preset;
                    sceneParameter.enable = data[18];
                    sceneParameter.name = bin2str(subBytes(data, 19, 64), "UTF-8").trim();
                    sceneParameter.left = data[82];
                    sceneParameter.top = data[83];
                    sceneParameter.right = data[84];
                    sceneParameter.bottom = data[85];
                    code = listenerCallBack.setSceneParameters(channel, preset, sceneParameter);
                }
            }

            if (code == 0)
                sendPack(ORDER_D0H, data, null);
            else
                sendPack(ORDER_D0H, null, new byte[]{data[14], 0, code});
            resultCode = 1; ///////
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_D0H, null, ERROR_FFFF_DOMAIN);
            resultCode = 0; ///////
        }
    }

    /**
     * 通用参数查询（控制字：D1H），参考扩展规约2.9。
     * 支持多种类型的通用参数读取：
     * - 0x04：获取软件/固件版本信息
     * - 0x05：获取场景参数配置
     *
     * @param data 原始接收数据帧
     */
    private void doGetGeneralParameters(byte[] data) { /////
        /////
        if (data == null || data.length < 13) {
            Log.w(Log.TAG, "通用参数查询失败：数据无效或长度不足");
            sendPack(ORDER_D1H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        int type = ((data[11] & 0xFF) << 8) | (data[12] & 0xFF);

        if (type == 0x04) {
            sendPack(ORDER_D1H, null, generalVersionData(data));
        } else if (type == 0x05) {
            getGeneralScene(data);
        } else
            sendPack(ORDER_D1H, data, null);  // 不支持的类型，原样返回或错误处理
    }

    /**
     * 生成软件和固件版本信息数据（type=0x04）。
     * 数据域格式：
     * - [0] 通道号
     * - [1] 结果码（0=成功，1=失败）
     * - [2~128] 软件版本号字符串（UTF-8编码）
     * - [129~255] 固件版本号字符串
     * - [256~258] 软件版本编译日期（年、月、日）
     *
     * @param data 原始接收数据帧
     */
    private byte[] generalVersionData(byte[] data) {
        byte[] dataDomain = new byte[261];
        dataDomain[0] = data[10];  // 通道号
        /////
        if (data == null || data.length < 11) {
            dataDomain[1] = 1;  // 异常时设置失败状态
            Log.w(Log.TAG, "生成软件和固件版本信息数据失败：数据无效或长度不足");
            sendPack(ORDER_D1H, null, ERROR_FFFF_DOMAIN);
            return dataDomain;
        }
        /////

        try {
            dataDomain[1] = 0;  // 成功
            String appVerion = appVersion(context);
            String date = appVerion.split(",")[1].replace(")", "").trim();
            // 时间戳转换
            Settings.TimeRecord tr = new Settings.TimeRecord(Utils.stringToTimestamp(FORMAT_DATETIME, date));
            // 软件版本填充（UTF-8编码）
            System.arraycopy(appVerion.getBytes(), 0, dataDomain, 2, Math.min(appVerion.length(), 127));
            // 固件版本
            String firmwareVersion = Build.DISPLAY;
            System.arraycopy(firmwareVersion.getBytes(), 0, dataDomain, 129, Math.min(firmwareVersion.length(), 127));
            // 编译日期
            dataDomain[258] = tr.year;
            dataDomain[259] = tr.month;
            dataDomain[260] = tr.day;
        } catch (Exception e) {
            dataDomain[1] = 1;  // 异常时设置失败状态
            Log.e(Log.TAG, "通用参数上报版本信息错误：" + e.getMessage());
        }
        return dataDomain;
    }

    /**
     * 获取通用场景参数（type=0x05）。
     * 数据域结构：
     * - [0] 通道号
     * - [1] 返回码（0 = 成功，1 = 失败）
     * - [2] 预置位编号
     * - [3] 是否启用（1 = 启用，0 = 禁用）
     * - [4~67] 场景名称（UTF-8 编码，最多 64 字节）
     * - [68] 区域左上角 X
     * - [69] 区域左上角 Y
     * - [70] 区域右下角 X
     * - [71] 区域右下角 Y
     *
     * @param data 原始接收数据帧
     */
    private void getGeneralScene(byte[] data) { /////
        /////
        if (data == null || data.length < 12) {
            Log.w(Log.TAG, "获取通用场景参数失败：数据无效或长度不足");
            sendPack(ORDER_D1H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        byte[] dataDomain;
        Settings.SceneParameter generalParameters = listenerCallBack.getSceneParameters(data[11] & 0xff, data[14] & 0xff);
        if (generalParameters == null) {
            dataDomain = new byte[]{data[11], 0, 1};
        } else {
            dataDomain = new byte[72];
            dataDomain[0] = data[11];  // 通道号
            dataDomain[1] = 0;  // 结果码
            dataDomain[2] = (byte) generalParameters.presetNo;
            dataDomain[3] = generalParameters.enable;
            byte[] nameByte = new byte[64];
            try {
                nameByte = generalParameters.name.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                Log.e(Log.TAG, "编码场景名称失败：" + e.getMessage());
            }
            System.arraycopy(nameByte, 0, dataDomain, 4, Math.min(nameByte.length, 64));
            dataDomain[68] = generalParameters.left;
            dataDomain[69] = generalParameters.top;
            dataDomain[70] = generalParameters.right;
            dataDomain[71] = generalParameters.bottom;
        }
        sendPack(ORDER_D1H, null, dataDomain);
    }

    /**
     * 获取SIM卡信息（控制字：D2H），参考扩展规约2.10。
     * 平台下发密码验证成功后，返回设备本机的SIM卡 ICCID。
     * 数据域结构：
     * - [0] 结果码：0=成功，1=设备不支持，2=参数错误
     * - [1-2] 操作类型（0x0402 查询）
     * - [3-4] 参数类型（0x1088 表示SIM卡号）
     * - [5-8] 参数长度（60字节）
     * - [9-10] 预留
     * - [11-12] 通道号（65535 表示本机）
     * - [13-28] 本机号码（16字节保留）
     * - [29~] ICCID（最长20字节，后补0填满32字节）
     *
     * @param data 原始接收数据帧
     */
    private void doGetSimCardInfo(byte[] data) { /////
        /////
        if (data == null || data.length < 14) {
            Log.w(Log.TAG, "获取SIM卡信息失败：数据无效或长度不足");
            sendPack(ORDER_D2H, null, ERROR_FFFF_DOMAIN);
            return;
        }
        /////

        String password = getReceivePassword(data);

        if (password.equals(this.passcode)) {
            try {
                String cardNO = listenerCallBack.getSimCardInfo(); /////
                Log.i(Log.TAG, "SIM卡卡号：" + cardNO);
                if (cardNO == null) {
                    sendPack(ORDER_D2H, null, new byte[]{1});
                } else {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(bos);
                    dos.writeByte(0);  // 结果码：0 = 成功，1 = 设备不支持，2 = 参数错误
                    dos.writeShort(0x0402);  // 0x0402表示查询操作
                    dos.writeShort(0x1088);  // 0x1088表示本机SIM卡号
                    dos.writeInt(60);  // 参数数字域长度
                    dos.writeShort(0);  // 预留
                    dos.writeShort(65535);  // 通道号：0 = 通道1，1 = 通道2，65535 = 本机
                    dos.write(new byte[16]);  // 本机号码
                    dos.writeBytes(cardNO);  // ICCID 20位
                    dos.write(new byte[32-cardNO.length()]);  // 填充为0
                    sendPack(ORDER_D2H, null, bos.toByteArray());
                }
            } catch (IOException e) {
            }
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_D2H, null, ERROR_FFFF_DOMAIN);
        }
    }

    /////
    /**
     * 通用控制指令（控制字：D4H），参考扩展规约2.11。
     * 上传设备或机芯日志文件。
     *
     * @param data 原始接收数据帧
     */
    private void doUploadLog(byte[] data) {
        // ？？？
//        if (data == null || data.length < 17) {
//            Log.w(Log.TAG, "上传设备/机芯日志文件失败：接收数据无效或长度不足");
//            sendPack(ORDER_D4H, null, ERROR_FFFF_DOMAIN);
//            return;
//        }

        int subtype = data[17] & 0xFF;
        switch (subtype) {
            case 0x00:
                Log.i(Log.TAG, "主站请求导出日志文件");
                doHandleRequestExportLog(data);
                break;
            case 0x01:
                Log.i(Log.TAG, "终端上送日志文件数据");
//                doHandleLogDataPacket(data);
                doHandleLogDataPacket();
                break;
//            case 0x02:
//                break;
            case 0x03:
                Log.i(Log.TAG, "日志文件上送结束标记");
                break;
            case 0x04:
                Log.i(Log.TAG, "日志补包补包数据下发");
                doHandleLogResend(data);
                break;
            default:
                Log.w(Log.TAG, String.format("未知日志子类型指令：0x%02X", subtype));
                break;
        }
    }

    /**
     * 主站请求导出日志文件。
     * 数据域结构：
     * - [10~13] 密码
     * - [14]    通道号
     * - [15~16] 功能类型
     * - [17]    指令子类型
     * - [18~20] 日志日期
     *
     * @param data 原始接收数据帧
     */
    private void doHandleRequestExportLog(byte[] data) {
        if (data == null || data.length < 21) {
            Log.w(Log.TAG, "主站请求导出日志文件失败：接收数据无效或长度不足");
            sendPack(ORDER_D4H, null, ERROR_FFFF_DOMAIN);
            return;
        }

        String password = getReceivePassword(data);
        if (password.equals(this.passcode)) {
            echoBack(ORDER_D4H);  // 原命令返回
            int channel = data[14] & 0xFF;
            int year = 2000 + (data[18] & 0xFF);
            int month = data[19] & 0xFF;
            int day = data[20] & 0xFF;
            Log.i(Log.TAG, String.format("主站请求导出日志，通道%d，起始日期：%04d-%02d-%02d", channel, year, month, day));
            listenerCallBack.takeUploadLog(channel);
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_D4H, null, ERROR_FFFF_DOMAIN);
        }
    }

    private void copyLogFile(String logFilename) { /////
        File currentFile = new File(logFilename);
        File parentDir = currentFile.getParentFile().getParentFile();
        File sourceFile = new File(parentDir, "logs.log");
        if (sourceFile.exists()) {
            currentFile.getParentFile().mkdirs();
            try {
                Files.copy(
                        sourceFile.toPath(),
                        currentFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
                Log.i(TAG, "文件复制成功：" + sourceFile + " -> " + currentFile);
            } catch (IOException e) {
                Log.i(TAG, "文件复制失败：" + e.getMessage());
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "源文件不存在："+logFilename);
        }
    }

    public void doHandleRequestUploadLog(int channel) { /////
        // 这个对文件进行复制，放在上传过程过程中，新增日志的影响
        copyLogFile(logFilename); /////

        final File file = new File(logFilename);
        int packCount = 1;
        try {
            if (!file.exists()) {
                Log.i(Log.TAG, "上传失败，日志文件不存在：" + logFilename);
                listenerCallBack.onFileUploadFailure(logFilename);
                return;
            }

            if (isFileWritting(file)) {
                Log.i(Log.TAG, "上传失败，日志文件正在进行写操作：" + logFilename);
                listenerCallBack.onFileUploadFailure(logFilename);
                return;
            }

            // 当使用synchronized加锁class时，无论创建一个对象还是多个对象，他们用的都是同一把锁；
            // 而是用synchronized枷锁this时，只有同一个对象会使用同一把锁，不同对象之间的锁是不同的。
            // 当前类在MainActivity中只创建了一个对象用于操作南网协议，所以用this加锁就可以了。
            synchronized (this) {
                if (uploading) {
                    Log.i(Log.TAG, "上传失败，日志文件正在上传：" + logFilename); /////
                    listenerCallBack.onFileUploadFailure(logFilename);
                    return;
                }
                uploading = true;
            }
            uploadLogFilename = logFilename;  // 确定上传文件的路径
            uploadLogChannel = channel;
            uploadStart = System.currentTimeMillis();
            // 计算总包数
            int filePacks = (int) (file.length() / MAX_UPLOAD_FILE_SIZE);
            if (file.length() % MAX_UPLOAD_FILE_SIZE != 0) filePacks++;  // 根据文件大小，求出包数，末尾可能不够一个包，有余数
            packCount = filePacks;

            // 终端请求上送日志文件数据前发送该指令，主站收到后立即原命令返回给装置，该命令最多循环发送5秒，每次间隔3秒，收到主站应答后立即开始传输日志文件数据
            for (int i = 0; i < 5; i++) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(bos);
                bos.write(this.passcode.getBytes());  // 密文认证
                bos.write(channel);  // 通道号
                bos.write(0x00);  // 功能类型
                bos.write(0x00);  // 功能类型
                bos.write(0x01);  // 指令子类型
                bos.write(packCount / UPLOAD_IMAGE_PACK_DIVISOR);  // 总包数
                bos.write(packCount % UPLOAD_IMAGE_PACK_DIVISOR);  // 总包数
                byte[] dataDomain = bos.toByteArray();
                dos.close();
                bos.close();
                sendPack(ORDER_D4H, null, dataDomain);
                SystemClock.sleep(500);  // 等服务器快速返回，防止服务器立刻返回后卡住
                if (uploadEcho) break;
                SystemClock.sleep(2500);
            }

            if (!uploadEcho) {
                doFileUploadError("日志文件上传请求超时");
            }
            Log.e(Log.TAG, "日志文件上传请求结束：" + uploadEcho + " " + logFilename);
        } catch (Exception e) {
            Log.e(Log.TAG, "装置请求上送日志文件错误：" + e.getMessage());
        }
    }

    private void doHandleLogDataPacket() {
        if (uploadEcho) {
            return;
        }
        uploadEcho = true;

        File file = new File(uploadLogFilename);
        if (!file.exists()) {
            doFileUploadError("日志文件不存在");
            return;
        }

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            FileInputStream fis = new FileInputStream(uploadLogFilename);

            // 服务器响应中包括 密码 通道号，功能类型，指令子类型，子包包号，数据区
            byte[] buf = new byte[MAX_UPLOAD_FILE_SIZE];
            int len = 0;
            int packIndex = 0;

            Log.i(Log.TAG, String.format("开始上传日志文件：%s", uploadLogFilename));
            // 开始读取日志文件数据并发送
            while ((len = fis.read(buf)) != -1) {
                packIndex++;  // packIndex是从1开始计数的
                int packHigh = packIndex / UPLOAD_IMAGE_PACK_DIVISOR;
                int packLow = packIndex % UPLOAD_IMAGE_PACK_DIVISOR;

                byte[] head = new byte[]{0x00, 0x00, 0x00, 0x02, 0, 0};  // 包头模板：通道号、功能类型、指令子类型、子包包号（高位）、子包包号（低位）

                head[4] = (byte) packHigh;
                head[5] = (byte) packLow;

                bos.write(this.passcode.getBytes());

                bos.write(head);
                bos.write(buf, 0, len);
                byte[] dataDomain = bos.toByteArray();
                bos.reset();
                sendPack(ORDER_D4H, null, dataDomain);
                SystemClock.sleep(UPLOAD_INTERVAL);  // 控制发送节奏
            }
            fis.close();
            bos.close();
            SystemClock.sleep(2000);  // 等待2秒再发结束帧

            uploadEndEcho = false;
            // 这个地方要重复发送
            // 该命令最多循环发送10次，每次间隔3秒，收到主站应答后即停止发送
            ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos2);
            bos2.write(this.passcode.getBytes());  // 密文认证
            bos2.write(0x00);  // 通道号
            bos2.write(0x00);  // 功能类型
            bos2.write(0x00);  // 功能类型
            bos2.write(0x03);  // 指令子类型
            // 构造时间字段
            long time = System.currentTimeMillis();
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date(time));
            byte year = (byte) (calendar.get(Calendar.YEAR) - 2000);
            byte month = (byte) (calendar.get(Calendar.MONTH) + 1);
            byte day = (byte) calendar.get(Calendar.DAY_OF_MONTH);
            bos2.write(year);
            bos2.write(month);
            bos2.write(day);
            byte[] dataDomain = bos2.toByteArray();
            dos.close();
            bos2.close();
            sendPack(ORDER_D4H, null, dataDomain);
//            Log.d(Log.TAG, String.format("发送日志文件结束帧（第 %d 次）", i + 1));

            SystemClock.sleep(3 * 1000);
        } catch (Exception e) {
            Log.e(Log.TAG, "日志文件上传异常：" + e.getMessage());
        } finally {
            if (!uploadEndEcho) {  // 重复试了多试，都没有收到响应，需要强制复位
                doFileUploadError("服务器未响应传输结束指令");
            }
            Log.e(Log.TAG, "传输结束：" + uploadEndEcho);
        }
    }

    /**
     * 主站请求导出日志文件。
     * 数据域结构：
     * - [10~13]         密码
     * - [14]            通道号
     * - [15~16]         功能类型
     * - [17]            指令子类型
     * - [18~19]         补包数
     * - [18+2n~18+2n+1] 第n包包号
     *
     * @param data 原始接收数据帧
     */
    private void doHandleLogResend(byte[] data) {
        uploadEndEcho = true;
        if (data == null || data.length < 20) {
            Log.w(Log.TAG, "主站请求导出日志文件失败：接收数据无效或长度不足");
            sendPack(ORDER_D4H, null, ERROR_FFFF_DOMAIN);
            return;
        }

        String password = getReceivePassword(data);
        File file = new File(uploadLogFilename);
        if (password.equals(this.passcode)) {
            // 情况1：第18、19字节，补包数，如果为0，说明传完了，直接可以结束日志文件传输了
            int missed = ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
            if (missed == 0) {
                if ((data[14] & 0xFF) == this.uploadLogChannel) {
                    // 当前文件传输成功，结束流程
                    this.uploadEcho = false;
                    this.uploading = false;
                    this.uploadEndEcho = true;
                    this.uploadStart = 0;
                    this.uploadSucceed = true;
                    this.uploadSucceedTime = System.currentTimeMillis();
                    listenerCallBack.onFileUploadEnd(this.uploadTimestamp, this.uploadLogFilename, this.uploadLogChannel, this.uploadPreset, this.uploadType);
                    this.uploadLogFilename = "";
                    Log.i(Log.TAG, "日志文件上传完成（主站反馈缺包为0）");
                } else {
                    // 收到的传输成功的指令不是当前要传的日志文件--在电科院和超高压都发现有这样的问题，就是发送当前日志文件的补传指令，但是
                    // 收到了上一个传输成功了的反馈指令，这个时候要结束当前传输流程，因为再往下走就不停的发送上一个日志文件的补传指令，
                    // 而收到的一直是补传为0的信息，设备和后端的沟通已经不在一个频道上了。
                    doFileUploadError("补包指令非当前传输日志文件");
                }
                return;
            }

            // 情况2：进行补包操作
            if (!file.exists()) {
                doFileUploadError("补包时日志文件不存在");
                return;
            }

            try {
                Log.w(Log.TAG, "收到补包指令，缺少包数：" + missed);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                RandomAccessFile raf = new RandomAccessFile(uploadLogFilename, "r");

                byte[] buf = new byte[MAX_UPLOAD_FILE_SIZE];
                int packIndex = 0;
                int len = 0;
                // 开始根据服务器下发的缺包数据，读取日志文件数据并发送
                for (int i = 0; i < missed; i++) {
                    bos.write(this.passcode.getBytes());
                    byte[] head = new byte[]{data[14], 0x00, 0x00, 0x02, 0, 0};
                    // 计算缺少日志文件数据位置并读取包
                    head[4] = data[20 + i * 2];
                    head[5] = data[21 + i * 2];

                    int packHigh = data[20 + i * 2] & 0xFF;
                    int packLow = data[21 + i * 2] & 0xFF;
                    packIndex = packHigh * UPLOAD_IMAGE_PACK_DIVISOR + packLow - 1;
                    if (packIndex < 0) packIndex = 0;

                    raf.seek(packIndex * MAX_UPLOAD_FILE_SIZE);
                    len = raf.read(buf, 0, MAX_UPLOAD_FILE_SIZE);
                    if (len == -1) break;
                    bos.write(head);
                    bos.write(buf, 0, len);
                    byte[] dataDomain = bos.toByteArray();
                    bos.reset();
                    if (!uploading) break;
                    sendPack(ORDER_D4H, null, dataDomain);
                    SystemClock.sleep(UPLOAD_INTERVAL);
                }
                raf.close();
                bos.close();

                SystemClock.sleep(5000);
                uploadEndEcho = false;
                // 补包指令最多发送5次，每次间隔3秒，收到主站应答后立即停止发送
                for (int i = 0; i < 5; i++) {
                    ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(bos2);
                    bos2.write(this.passcode.getBytes());  // 密文认证
                    bos2.write(0x00);  // 通道号
                    bos2.write(0x00);  // 功能类型
                    bos2.write(0x00);  // 功能类型
                    bos2.write(0x03);  // 指令子类型
                    // 构造时间字段
                    long time = System.currentTimeMillis();
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(new Date(time));
                    byte year = (byte) (calendar.get(Calendar.YEAR) - 2000);
                    byte month = (byte) (calendar.get(Calendar.MONTH) + 1);
                    byte day = (byte) calendar.get(Calendar.DAY_OF_MONTH);
                    bos2.write(year);
                    bos2.write(month);
                    bos2.write(day);
                    byte[] dataDomain = bos2.toByteArray();
                    dos.close();
                    bos2.close();
//                    if (!uploading) break;  // 未在传输日志文件状态，说明文件可能已经传完了
//                    byte[] dataDomain = new byte[]{data[10], data[11]};
                    sendPack(ORDER_D4H, null, dataDomain);
                    Log.i(Log.TAG, String.format("发送结束确认帧（第 %d 次）", i + 1));
                    SystemClock.sleep(3000);  // 快速休眠一下，检查是否uploadEcho有相应
                    if (uploadEndEcho || !uploading) break;

                    // 无响应，规约要求等3秒后再试，继续发送结束
                    //SystemClock.sleep(3000);
                }
            } catch (Exception e) {
                // 补包错误，应该如何处理？是直接关闭日志文件传输还是通知服务器结束？
                Log.e(Log.TAG, "日志文件补包时错误：" + e.getMessage());
            } finally {
                if (uploading && !uploadEndEcho) {  // 重复试了多试，都没有收到响应，需要强制复位
                    doFileUploadError("未收到补包响应");
                }
                Log.e(Log.TAG, "补包结束：" + (uploadEndEcho || !uploading));
            }
        } else {
            Log.i(Log.TAG, String.format("密码不匹配，平台下发密码：%s，设备端密码：%s", password, this.password));
            sendPack(ORDER_D4H, null, ERROR_FFFF_DOMAIN);
        }
    }

    /**
     * 校验接收数据帧最小长度。
     *
     * @param data       原始数据
     * @param minLength  本指令解析所需的最小长度
     * @param order      当前指令控制字
     * @return true 表示校验通过
     */
    private boolean checkDataLength(byte[] data, int minLength, byte order, String logHint) {
        if (data == null || data.length < minLength) {
            Log.w(Log.TAG, logHint + "：接收数据无效或长度不足");
            sendPack(order, null, ERROR_FFFF_DOMAIN);
            return false;
        }
        return true;
    }

    /**
     * 主站请求手动测温（控制字：E0H），参考扩展规约7.64.1。
     *
     * @param data
     */
    public void doSetManualIrRegion(byte[] data) {
        if (!checkDataLength(data, 11, ORDER_E0H, "主站请求手动测温失败")) {
            return;
        }

        try {
            int channel = data[10] & 0xFF;

            int idx = 11;
            Vector<IRSetting.IrRegion> regionVector = new Vector<>();

            while (idx + 2 < data.length) {
                int points = data[idx++] & 0xFF;
                if (points < 1 || points > 8) {
                    Log.i(Log.TAG, "无效测温区域点数：" + points);
                    break;
                }

                IRSetting.IrRegion newRegion = new IRSetting.IrRegion();
                newRegion.points = new Vector<>();

                for (int i = 0; i < points; i++) {
                    int x = data[idx++] & 0xFF;
                    int y = data[idx++] & 0xFF;
                    newRegion.points.add(new Point(x, y));
                }
                regionVector.add(newRegion);
            }

            listenerCallBack.manualIrRegonSet(channel, regionVector);
            sendPack(ORDER_E0H, data, null);
        } catch (Exception e) {
            Log.e(Log.TAG, "主站请求手动测温异常：" + e.getMessage());
        }
    }

    /**
     * 测温采集参数配置（控制字：E1H），参考扩展规约7.64.2。
     *
     * @param data
     */
    public void doSetIrRegionParam(byte[] data) { /////
        /////
        if (!checkDataLength(data, 14, ORDER_E1H, "测温采集参数配置失败")) {
            return;
        }
        /////

        String password = getReceivePassword(data);
        if (password == null || !password.equals(this.passcode)) {
            sendPack(ORDER_E1H, null, ERROR_FFFF_DOMAIN);
            Log.i(Log.TAG, "密码错误");
            return;
        }

        try {
            int i = 14;
            int channel = data[i++] & 0xFF;
            int preset = data[i++] & 0xFF;
            int regions = data[i++] & 0xFF;

            Vector<IRSetting.IrRegionInfo> vinfo = new Vector<>();

            for (int j = 0; j < regions; j++) {
                IRSetting.IrRegionInfo info = new IRSetting.IrRegionInfo();
                info.irRegion = new IRSetting.IrRegion();
                info.irRegion.flag = data[i++];
                System.arraycopy(data, i, info.irRegion.name, 0, 64);
                i += 64;

                info.emissivity = byte2Float(data, i);
                i += 4;
                info.distance = byte2Float(data, i);
                i += 4;
                info.highThres = byte2Float(data, i);
                i += 4;
                info.lowThres = byte2Float(data, i);
                i += 4;

                int points = data[i++];

                info.irRegion.points = new Vector<>();
                for (int k = 0; k < points; k++) {
                    int x = data[i++] & 0xFF;
                    int y = data[i++] & 0xFF;
                    //int x = (int) ((float) (data[i++] & 0xFF) * 384 / 255);
                    //int y = (int) ((float) (data[i++] & 0xFF) * 288 / 255);
                    info.irRegion.points.add(new Point(x, y));
                }
                vinfo.add(info);
            }

            sendPack(ORDER_E1H, data, null);
            listenerCallBack.irRegionParamSet(channel, preset, vinfo);
        } catch (Exception e) {
            Log.e(Log.TAG, "测温采集参数配置异常：" + e);
        }
    }

    /**
     * 测温采集参数查询（控制字：E2H），参考扩展规约7.64.3。
     *
     * @param data
     */
    public void doGetIrRegionParam(byte[] data) { /////
        if (!checkDataLength(data, 10, ORDER_E2H, "测温采集参数查询失败")) {
            return;
        }

        int resultCode; ///////
        try {
            // int channel = (data[10] & 0xFF) + 1;  // 之前电科院下发的E2指令都是1通道，如果2通道配置为红外的话会导致查询失败
            int channel = data[10] & 0xFF;  // 现在电科院已经升级
            int preset = data[11] & 0xFF;  // 预置位：FFH：查询所有预置位；其他值，查询某个预置位数据。

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            HashMap<Integer, IRSetting.PresetRegions> param = listenerCallBack.irRegionParamGet();
            if (param == null) {
                sendPack(ORDER_E2H, null, ERROR_FFFF_DOMAIN);
                return;
            }

            for (int chanFromParam : param.keySet()) {
                if (chanFromParam != channel) {
                    continue;
                }

                IRSetting.PresetRegions presetRegions = param.get(chanFromParam);
                for (int presetFromSetting : presetRegions.irPresetRegions.keySet()) {
                    if (preset != 0xFF && presetFromSetting != preset) {
                        continue;
                    } else {
                        Vector<IRSetting.IrRegionInfo> vinfo = presetRegions.irPresetRegions.get(presetFromSetting);

                        dos.writeByte(0);  // 结果码
                        dos.writeByte(channel);
                        dos.writeByte(presetFromSetting);
                        dos.writeByte(param.size());
                        dos.writeByte(vinfo.size());

                        for (int i = 0; i < vinfo.size(); i++) {
                            dos.writeByte(vinfo.get(i).irRegion.flag);
                            dos.write(vinfo.get(i).irRegion.name);
                            dos.write(float2Bytes(vinfo.get(i).emissivity));
                            dos.write(float2Bytes(vinfo.get(i).distance));
                            dos.write(float2Bytes(vinfo.get(i).highThres));
                            dos.write(float2Bytes(vinfo.get(i).lowThres));
                            Vector<Point> points = vinfo.get(i).irRegion.points;
                            dos.writeByte(points.size());
                            for (int j = 0; j < points.size(); j++) {
                                //int x = points.get(j).x * 255 / 384;
                                //int y = points.get(j).y * 255 / 288;
                                int x = points.get(j).x;
                                int y = points.get(j).y;
                                dos.writeByte(x);
                                dos.writeByte(y);
                            }
                        }
                    }
                }
            }
            Log.i(Log.TAG, "获取通道" + channel + "预置位" + preset + "的测温区域：" + bin2hex(bos.toByteArray(), true));
            sendPack(ORDER_E2H, null, bos.toByteArray());
            resultCode = 0; ///////
        } catch (Exception e) {
            Log.e(Log.TAG, "测温采集参数查询异常：" + e);
            sendPack(ORDER_E2H, null, ERROR_FFFF_DOMAIN);
            resultCode = 1; ///////
        }
    }

    /**
     * 测温数据上送协议（控制字：E3H），参考扩展规约7.64.4。
     * 基于时间表的定时上传，基于温度告警触发上传，主站根据终端号码、通道号、预置位号、采集时间关联测温抓拍图片。
     *
     * @param channel   通道号
     * @param preset    预置位号
     * @param timestamp 采集时间戳，用于生成 Settings.TimeRecord
     * @param result    区域测温结果列表
     */
    public void doTempReport(int channel, int preset, long timestamp, Vector<IRSetting.IrRegionTemp> result) {
        int uploadCompleteFlag; ///////
        try {
            Settings.TimeRecord time = new Settings.TimeRecord(timestamp);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);

            dos.writeByte(channel);
            dos.writeByte(preset);
            dos.write(time.bytes);
            dos.writeByte(result.size());

            Iterator<IRSetting.IrRegionTemp> it = result.iterator();
            while (it.hasNext()) {
                IRSetting.IrRegionTemp temp = it.next();
                dos.writeByte(temp.irRegion.flag);
                dos.write(temp.irRegion.name);
                dos.write(float2Bytes(temp.tempMax));
                dos.write(float2Bytes(temp.tempAvg));
                dos.write(float2Bytes(temp.tempMin));
                dos.writeByte(temp.irRegion.points.size());
                for (int i = 0; i < temp.irRegion.points.size(); i++) {
                    int x = temp.irRegion.points.get(i).x;
                    int y = temp.irRegion.points.get(i).y;
                    dos.writeByte(x);
                    dos.writeByte(y);
                }
            }
            // 只发送一次
            sendPack(ORDER_E3H, null, bos.toByteArray());
            uploadCompleteFlag = 0;
        } catch (Exception e) {
            Log.i(Log.TAG, "测温数据上送协议异常：" + e);
            uploadCompleteFlag = 1;
        }
    }

    private TempReportState irReportFeedbackState = new TempReportState();
    private class TempReportState {
        int channel;
        int preset;
        Settings.TimeRecord time;
        int state;

        public TempReportState() {
        }

        public TempReportState(int channel, int preset, Settings.TimeRecord time, int state) {
            this.channel = channel;
            this.preset = preset;
            this.time = time;
            this.state = state;
        }

        public boolean equals(TempReportState state) {
            if (this.channel == state.channel &&
                    this.preset == state.preset &&
                    Arrays.equals(this.time.bytes, state.time.bytes) &&
                    this.state == state.state) {
                return true;
            }
            return false;
        }
    }

    /**
     * 测温数据上送反馈解析。
     * 主站对 E3H 温度上送数据进行应答，终端解析并更新 irReportFeedbackState。
     *
     * 数据域结构：
     * - [10]            通道号 channel 1 byte
     * - [11]            预置位号 preset 1 byte
     * - [12~17]         采集时间 time 6 bytes（Settings.TimeRecord）
     * - [18]            反馈状态 state 1 byte（具体含义以规约定义为准）
     *
     * @param data 原始接收数据帧
     */
    public void doReportIrFeedback(byte[] data) { /////
        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.position(10);
            irReportFeedbackState.channel = buffer.get() & 0xFF;
            irReportFeedbackState.preset = buffer.get() & 0xFF;
            byte[] timeData = new byte[6];
            buffer.get(timeData, 0, timeData.length);
            irReportFeedbackState.time = new Settings.TimeRecord(timeData);
            irReportFeedbackState.state = buffer.get() & 0xFF;
        } catch (Exception e) {
            Log.i(Log.TAG, "测温数据异常：" + e);
        }
    }

    /**
     * 测温传感器采集参数设置（控制字：E4H），参考扩展规约7.64.5。
     *
     * @param data
     */
    public void doSetIrSensorParam(byte[] data) { /////
        if (!checkDataLength(data, 41, ORDER_E4H, "测温传感器采集参数设置失败")) {
            return;
        }

        int resultCode; ///////
        try {
            String password = getReceivePassword(data);
            if (password == null || !password.equals(this.passcode)) {
                sendPack(ORDER_E4H, null, ERROR_FFFF_DOMAIN);
                Log.i(Log.TAG, "密码错误");
                return;
            }

            IRSetting.SensorConfig param = new IRSetting.SensorConfig();
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.position(14);

            param.measureUnit = buffer.get();
            param.color = buffer.get();
            param.hotTracker = buffer.get();

            param.reflectTemp = byte2Float(buffer.array(), 17);
            param.envirTemp = byte2Float(buffer.array(), 21);
            param.envirHumi = byte2Float(buffer.array(), 25);
            param.emissivity = byte2Float(buffer.array(), 29);
            param.distance = byte2Float(buffer.array(), 33);

            buffer.position(37);
            param.shutterInterval = buffer.get();
            param.tempCompensate = buffer.get();
            param.onPalette = buffer.get();
            param.climateRef = buffer.get();

            sendPack(ORDER_E4H, data, null);
            listenerCallBack.irSensorConfig(param);
            resultCode = 0; ///////
        } catch (Exception e) {
            Log.i(Log.TAG, "测温传感器采集参数设置异常：" + e);
            sendPack(ORDER_E4H, null, ERROR_FFFF_DOMAIN);
            resultCode = 1; ///////
        }
    }

    // 测温传感器采集参数查询（控制字：E5H），参考扩展规约7.64.6。
    private void doGetIrSensorParam() { /////
        int resultCode; ///////
        try {
            IRSetting.SensorConfig config = listenerCallBack.irSensorConfig();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);

            dos.writeByte(config.measureUnit);
            dos.writeByte(config.color);
            dos.writeByte(config.hotTracker);
            dos.write(float2Bytes(config.reflectTemp));
            dos.write(float2Bytes(config.envirTemp));
            dos.write(float2Bytes(config.envirHumi));
            dos.write(float2Bytes(config.emissivity));
            dos.write(float2Bytes(config.distance));
            dos.writeByte(config.shutterInterval);
            dos.writeByte(Math.round(config.tempCompensate));
            dos.writeByte(config.onPalette);
            dos.writeByte(config.climateRef);

            Log.i(Log.TAG, "上传测温传感器采集参数：" + config.toString());
            sendPack(ORDER_E5H, null, bos.toByteArray());
            resultCode = 0; ///////
        } catch (Exception e) {
            Log.i(Log.TAG, "测温传感器采集参数查询异常：" + e);
            sendPack(ORDER_E5H, null, ERROR_FFFF_DOMAIN);
            resultCode = 1; ///////
        }
        ///////
//        IRSetting.SensorConfig config = listenerCallBack.irSensorConfig();
//        // 显示颜色：0:白热 1:熔岩 2:铁红 3:热铁 4:医疗 5:北极 6:彩虹 7:彩虹2 8:描红 9:黑热  默认：0白热 高德红外
//        int color;
//        if (config.color == 1) {
//            color = 3;
//        } else if (config.color == 3) {
//            color = 5;
//        } else if (config.color == 4) {
//            color = 8;
//        } else if (config.color == 5) {
//            color = 9;
//        } else if (config.color == 6) {
//            color = 4;
//        } else if (config.color == 8) {
//            color = 6;
//        } else if (config.color == 9) {
//            color = 1;
//        } else{
//            color = config.color;
//        }
        ///////
    }

    /**
     * 构建完整的数据包。
     *
     * @param order       控制字
     * @param response    如果不为 null，直接返回原始响应包（优先级高于 dataDomain）
     * @param dataDomain  数据体（可为 null）
     * @return 构建后的完整数据包，包含起始位、设备ID、控制字、数据长度、数据体、校验码、结束位
     */
    private byte[] result(byte order, byte[] response, byte[] dataDomain) {
        if (response != null) return response;
        if (deviceID == null) return null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ByteArrayOutputStream bufStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);

            // 写入设备ID + 控制字 + 数据长度 + 数据体
            out.write(deviceID.getBytes());
            out.write(order);
            if (dataDomain != null) {
                out.writeShort(dataDomain.length);
                out.write(dataDomain);
            } else {
                out.writeShort(0);
            }
            byte[] checkCode = {Crc(bos.toByteArray())};

            // 包头 + 正文 + CRC + 包尾
            bufStream.write(START_CHAR);
            bufStream.write(bos.toByteArray());
            bufStream.write(checkCode);
            bufStream.write(END_CHAR);
            byte[] sendData = bufStream.toByteArray();
            bufStream.reset();
            bos.reset();
            return sendData;
        } catch (IOException e) {
            Log.e(Log.TAG, "获取封包数据失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 协议构造函数，绑定回调与上下文。
     *
     * @param listenerCallBack 回调接口
     */
    public SPGProtocol(SPGPCallback listenerCallBack, Context context) {
        this.listenerCallBack = listenerCallBack;
        this.context = context;
    }

    // 判断当前文件上传是否超时。
    public boolean uploadTimeout() {
        return uploadStart > 0 && (System.currentTimeMillis() - uploadStart > UPLOAD_TIMEOUT);
    }

    // 判断文件是否仍在写入状态。
    private boolean isFileWritting(File file) {
        if (file == null || !file.exists())
            return false;

        long len = file.length();
        SystemClock.sleep(PERIOD_SECOND);
        if (file.length() == len) {
            return false;
        }
        return true;
    }

    // 上传失败统一处理逻辑。
    public void doFileUploadError(String reason) {
        // 如果连续 N 次 都没收到，则不再传输本文件
        uploading = false;
        uploadStart = 0;
        uploadSucceed = false;
        Log.e(Log.TAG, "文件传输错误：" + reason + "，文件：" + uploadFilename);
        listenerCallBack.onFileUploadFailure(uploadTimestamp, uploadFilename, uploadChannel, uploadPreset, uploadType);
        uploadFilename = "";
    }

    /**
     * 向主站发送封装后的协议包（upd发送）。
     *
     * @param order      控制字
     * @param response   表示整个包原样发送，一般用于原路返回的（可为 null）
     * @param dataDomain 数据体，函数内部会自动封装包头，ID，数据长度，CRC，包尾等信息然后再发送
     */
    public void sendPack(byte order, byte[] response, byte[] dataDomain) {
        /// 未收到服务器开机联络信息前，不允许发其他任何包，调试时取消
//        if (!Logged) return;
        byte[] buf = result(order, response, dataDomain);
        if (buf == null) return;

        if (buf.length > 7) {
            if (buf[7] != (byte) 0x95 && buf[7] != (byte) 0x85) {
                //String s = "debug".equals(BuildConfig.BUILD_TYPE) ? bin2hex(buf, true) ："";
                //Log.d(Log.TAG, String.format("发送指令（%02XH）%d字节：%s", buf[7], buf.length, s));
                if (buf[7] == ORDER_D4H) {
                    int previewLength = Math.min(buf.length, buf.length > 64 ? 12 : 32);
                    long now = SystemClock.elapsedRealtime();
                    if (lastD4SendLogTime == 0 || now - lastD4SendLogTime >= 30 * 1000) {
                        lastD4SendLogTime = now;
                        Log.d(Log.TAG, String.format("发送指令（%02XH）%d字节：%s%s",
                                buf[7], buf.length, bin2hex(subBytes(buf, 0, previewLength), true),
                                buf.length > previewLength ? "..." : ""));
                    }
                } else {
                    Log.d(Log.TAG, String.format("发送指令（%02XH）%d字节：%s", buf[7], buf.length, bin2hex(buf, true)));
                }
            } else {
//                Log.d(Log.TAG, String.format("发送指令（%02XH）%d字节", buf[7], buf.length));
            }
        }
        listenerCallBack.sendData(buf);
    }

    /**
     * 累加和取反校验算法。
     * 累加 deviceID、控制字、数据长度和数据体，保留低字节，取反即为校验字节。
     * 采用累加和取反的校验方式，发送方将终端号码、控制字、数据长度和数据区的所有字节进行算术累加，
     * 抛弃高位，只保留最后单字节，将单字节取反。
     *
     * @param data 需要计算校验的数据
     * @return 单字节校验值结果
     */
    public static byte Crc(byte[] data) {
        int r = 0;
        byte b = 0;
        for (int i = 0; i < data.length; i++) r += data[i];
        b = (byte) (r & 0x00FF);
        b = (byte) ~b;
        return b;
    }

    // 获取当前已接收的升级包数量。
    private int getUpgradePacketReadyCount() {
        int count = 0;
        if (upgradePacketReady != null)
            for (int i = 0; i < upgradePacketReady.length; i++) {
                if (upgradePacketReady[i]) count++;
            }
        return count;
    }

    // Short 转 2 字节数组，高位在前。
    private byte[] ShortToStreamByte(final short value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(2);
        if (value >= 0) {
            out.write((byte)(value >> 8) & 0xFF);
            out.write((byte)(value & 0xFF));
        } else {
            out.write((byte)((-value >> 8) & 0xFF) | 0x80);
            out.write((byte)(-value & 0xFF));
        }
        return out.toByteArray();
    }

    /**
     * 原命令返回
     *
     * @param order 传入一个控制字
     */
    private void echoBack(byte order) {
        sendPack(order, mReceiveData, null);
    }

    // 整数转成两位十进制字符串。
    private String handlerTime2Bit(int value) {
        if (value < 0) return "";
        if (value < 10) {
            return "0" + value;
        }
        return "" + value;
    }

    // 秒数转时分。
    private byte[] toHourMin(int second) {
        byte[] HourMin = new byte[2];
        HourMin[0] = (byte) (second / 60 / 60);
        HourMin[1] = (byte) (second / 60 % 60);
        return HourMin;
    }

    // 4位字节数组转换为整型。
    public static int byte2Int(byte[] b) {
        int intValue = 0;
        for (int i = 0; i < b.length; i++) {
            intValue += (b[i] & 0xFF) << (8 * (3 - i));
        }
        return intValue;
    }

    // 从arr数组的第idx字节开始，4位字节数组转换为浮点型。
    public static float byte2Float(byte[] arr, int idx) {
        int intValue = (arr[idx] & 0xFF)
                | (arr[idx + 1] & 0xFF) << 8
                | (arr[idx + 2] & 0xFF) << 16
                | (arr[idx + 3] & 0xFF) << 24;
        return Float.intBitsToFloat(intValue);
    }

    // 低位在前。
    public static byte[] float2Bytes(float value) {
        int intValue = Float.floatToIntBits(value);
        return new byte[] {
                (byte) (intValue & 0xFF),
                (byte) ((intValue >> 8) & 0xFF),
                (byte) ((intValue >> 16) & 0xFF),
                (byte) ((intValue >> 24) & 0xFF)
        };
    }

    /**
     * 字符串中提取年/月/日/时/分/秒。
     *
     * @param time 传入String类型 YYYY/MM/DD HH:MM:ss 格式时间
     * @return 6字节byte类型参数
     */
    private byte[] getByteTime(String time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        byte[] timeByte;
        Date date = null;
        try {
            date = sdf.parse(time);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int year = calendar.get(Calendar.YEAR) - 2000;
        int moth = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);
        timeByte = new byte[]{(byte) year, (byte) moth, (byte) day, (byte) hour, (byte) minute, (byte) second};
        return timeByte;
    }

    /**
     * 多字节数值转 int（高位在前）。
     *
     * @param data  数据
     * @param index 起始位置
     * @param count 字节数
     */
    private int getValue(byte[] data, int index, int count) {
        int ret = data[index];
        for (int i = 1; i < count; i++) {
            ret = (ret << 8) | data[index + 1];
        }
        return ret;
    }

    // 计算距离 DVR 当前时间的毫秒延迟。
    private int getDelayTime() {
        String[] dvrTime = listenerCallBack.getDvrTime().split("/");
        dvrTime = dvrTime[2].split(" ");
        dvrTime = dvrTime[1].split(":");
        int dvrHour = Integer.parseInt(dvrTime[0]);
        int dvrMinute = Integer.parseInt(dvrTime[1]);
        int dvrSecond = Integer.parseInt(dvrTime[2]);
        if (dvrHour == 0) dvrHour = 24;
        return (dvrHour * 60 + dvrMinute) * 60000 + (dvrSecond * 1000);
    }

    /**
     * 把主站下发的 4 字节密码转换成String。
     *
     * @param password 下发byte[]数据
     * @return 结果
     */
    private String getReceivePassword(byte[] password) {
        if (password == null) {
            return null;
        }
        try {
            byte[] mPassword = new byte[]{password[10], password[11], password[12], password[13]};
            return new String(mPassword);
        } catch (Exception e) {
            return null;
        }
    }

    // 将生成的文件列表按创建时间顺序，由new到old重新排列。
    private void getTimeOrder() {
        for (int i = 0; i < fileTimes.size(); i++) {
            for (int j = i + 1; j < fileTimes.size(); j++) {
                int va = fileTimes.get(i).compareTo(fileTimes.get(j));
                String oldFileName = fileNames.get(i);
                Integer oldFileLengt = fileLengths.get(i);
                String oldFileTime = fileTimes.get(i);
                if (va < 0) {
                    fileNames.set(i, fileNames.get(j));
                    fileLengths.set(i, fileLengths.get(j));
                    fileTimes.set(i, fileTimes.get(j));
                    fileNames.set(j, oldFileName);
                    fileLengths.set(j, oldFileLengt);
                    fileTimes.set(j, oldFileTime);
                }
            }
        }
    }

    /**
     * 单个文件信息的封包。
     *
     * @param i 第i个文件
     * @return 第i个文件信息
     */
    private ByteArrayOutputStream getFileList(ByteArrayOutputStream baoss, int i) {
        byte[] filesList = new byte[100];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos = baoss;
        try {
            //文件名
            byte[] name = fileNames.get(i).getBytes();
            //文件生成时间
            Date date = null;
            try {
                date = format.parse(String.valueOf(fileTimes.get(i)));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            int year = calendar.get(Calendar.YEAR) - 2000;
            int month = calendar.get(Calendar.MONTH) + 1;
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            int hour = calendar.get(Calendar.HOUR);
            int minute = calendar.get(Calendar.MINUTE);
            int second = calendar.get(Calendar.SECOND);
            //文件大小
            int length = fileLengths.get(i);

            ByteArrayOutputStream bao = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bao);
            byte[] lengthList;
            out.writeShort(length);
            lengthList = bao.toByteArray();
            out.close();
            bao.close();
            for (int j = 0; j < filesList.length; j++) {
                if (j < name.length) {
                    filesList[j] = name[j];
                }
            }
            baos.write(filesList);
            baos.write(year);
            baos.write(month);
            baos.write(day);
            baos.write(hour);
            baos.write(minute);
            baos.write(second);
            baos.write(lengthList);
            baos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return baos;
    }

    // 获取文件列表信息（并排序）。
    protected void getFileNameList() {
        File file = new File(MainActivity.FILE_PATH);
        if (!file.exists())
            return;
        File[] files = new File(MainActivity.FILE_PATH).listFiles();
        File x = new File(MainActivity.FILE_PATH);
        for (File f : files) {
            if (x.isDirectory()) {
                fileNames.add(f.getName());
                fileLengths.add((int) f.length());
                @SuppressLint("SimpleDateFormat") String ctime = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(new Date(f.lastModified()));
                fileTimes.add(String.valueOf(ctime));
            }
        }
        // 将生成的文件列表按创建时间顺序，由new到old重新排列
        for (int i = 0; i < fileTimes.size(); i++) {
            for (int j = i + 1; j < fileTimes.size(); j++) {
                int va = fileTimes.get(i).compareTo(fileTimes.get(j));
                String oldFileName = fileNames.get(i);
                Integer oldFileLengt = fileLengths.get(i);
                String oldFileTime = fileTimes.get(i);
                if (va < 0) {
                    fileNames.set(i, fileNames.get(j));
                    fileLengths.set(i, fileLengths.get(j));
                    fileTimes.set(i, fileTimes.get(j));
                    fileNames.set(j, oldFileName);
                    fileLengths.set(j, oldFileLengt);
                    fileTimes.set(j, oldFileTime);
                }
            }
        }
    }

    // 通过文件名获取文件信息。
    protected void getFileNameList(String fileName) {
        File file = new File(MainActivity.FILE_PATH);
        if (!file.exists())
            return;
        File[] files = new File(MainActivity.FILE_PATH).listFiles();
        File x = new File(MainActivity.FILE_PATH);
        for (File f : files) {

            if (x.isDirectory()) {
                if (f.getName().equals(fileName)) {
                    fileLength = (int) f.length();
                    @SuppressLint("SimpleDateFormat") String ctime = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss").format(new Date(f.lastModified()));
                    fileTime = ctime;
                }
            }
        }
    }

    /**
     * 文件数据上传结束标记
     *
     * @param order 结束指令
     * @throws IOException
     */
    protected void stopUploadPicture(byte order) throws IOException {
        SystemClock.sleep(2000);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(channelNum);
        baos.write(preset);
        sendPack(order, null, baos.toByteArray());
        baos.close();
        isUpLocal = false;
        repeatTiming.order = order;
        mHandler.postDelayed(repeatTiming, 28 * 1000);
    }

    // 重发任务类（共最多重试5次）。
    public class UploadRunnable implements Runnable {
        public byte order;

        @Override
        public void run() {
            repeatCountLoop++;
            switch (order) {
                case ORDER_73H:
                    doUploadingFileRequests(); /////
                    break;
                case ORDER_75H:
                    upLocalFileEnd(fileNameByteData); /////
                    break;
                case ORDER_86H:
                    try {
                        stopUploadPicture(ORDER_86H);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
            }
            if (repeatCountLoop == 5) mHandler.removeCallbacks(repeatTiming);
        }
    }

    // 获取文件名（最多100字节）。
    private ByteArrayOutputStream getUploadFilename() {
        ByteArrayOutputStream names = new ByteArrayOutputStream();
        for (int i = 0; i < 100; i++) {
            names.write(mReceiveData[i + 10]);
        }
        return names;
    }

    // 为了兼容协议，特地处理，老协议是long的SSRC，实际上SSRC是四字节就够了。
    private int parseSSRC(byte[] data) {
        if (data.length == 4)
            return ByteUtils.byteArrayToInt(data);
        else
            return (int) Long.parseLong(new String(data));
    }

    // byte转int。
    private int toInt(byte value) {
        return value & 0xFF;
    }
}
