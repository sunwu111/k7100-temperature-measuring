package hikvision.zhanyun.com.hikvision.device.guide;

import static hikvision.zhanyun.com.hikvision.device.guide.IROverProtect.ExposureMode.Protection;
import static lyh.Utils.PERIOD_MINUTE;
import static lyh.Utils.PERIOD_SECOND;
import static lyh.Utils.dateFromString;
import static lyh.Utils.saveBitmapAsJPEG;
import static lyh.Utils.stringToFile; /////

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.media.AudioFormat; /////
import android.media.AudioRecord; /////
import android.media.MediaCodec;
import android.media.MediaCodecInfo; /////
import android.media.MediaFormat;
import android.media.MediaRecorder; /////
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.text.TextPaint;
import static lyh.Utils.TAG;
import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSON; /////
import com.guide.sdk.GuideInterface;
import com.guide.sdk.annotation.AngleMenu;
import com.guide.sdk.annotation.MirrorMode;
import com.guide.sdk.bean.DeviceStatusInfo;
import com.guide.sdk.bean.MTUserParam;
import com.guide.sdk.bean.PreviewConfig;
import com.guide.sdk.bean.DataInternalParam;
import com.guide.sdk.bean.GuideUsbVideoMode;
import com.guide.sdk.jni.NativeUtil;
import com.guide.sdk.jni.NativeGuideCore;
import com.guide.sdk.util.DeviceUtils;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.Set;
import java.util.HashSet;

import hikvision.zhanyun.com.hikvision.device.Device;
import hikvision.zhanyun.com.hikvision.IRSetting;
import hikvision.zhanyun.com.hikvision.MainActivity;
import hikvision.zhanyun.com.hikvision.R;
import hikvision.zhanyun.com.hikvision.Settings;
import hikvision.zhanyun.com.hikvision.rto.RTPH264;
import hikvision.zhanyun.com.hikvision.utils.Log;
import lyh.Utils;

public class GUIDEDev extends Device {
    private int RESIZE;
    private boolean closeForce = false;
    private int OVER_EXPOSURE_THRESHOLD = 150; // 防阳光灼伤温度150℃（扩展模式550℃），超过此门限2秒开启保护，10秒后取消保护
    private long MIN_BOOTUP_MILISECONDS = 100 * PERIOD_SECOND;
    private IRTempAlarm globalAlarm;
    private IRCMD ircmd;
    private boolean startPhoto;
    private long startPhotoTime;
    private long drawOSDTime;
    private long drawRegionTempTime;
    private String osdText;
    private IRRegionTemp globalTemp;
    private IROverProtect irOverProtect;
    private IRSetting.SensorConfig sensorConfig = new IRSetting.SensorConfig();
    private Vector<IRRegionTemp> liveRegionsTemp = new Vector<>();
    private Vector<Float> liveRegionsDistance = new Vector<>();  // 用于存储测温区域中的距离信息
    private HashMap<Integer, Vector<IRRegionTemp>> staticRegionsMaps = new HashMap<>();
    private HashMap<Integer, Vector<Float>> staticRegionsDistanceMaps = new HashMap<>();  // 用于存储测温区域中的距离信息
    private float presetEnvTemp;  // 用于存储环境温度区域对应的预置位
    private IRRegionTemp regionEnvTemp;  // 用于存储环境温度区域
    private static int CAMERA_RESOLUTION_W;
    private static int CAMERA_RESOLUTION_H;
    private Bitmap osdPalette;
    private int curPreset;
    private int envAlarmThres;
    private int cmpAlarmThres;
    private boolean useAudio; /////
    private Paint tempPaint;
    private Paint timePaint;
    public Device ptzDev; // 可见光机芯，用于联动云台

    private final GuideInterface mGuideInterface = GuideInterface.getInstance();
    private Bitmap mBitmap;
    private byte[] byteArray = new byte[0];
    private byte[] mArgb;
    private byte[] mAbgr;
    private float[] mTemp;
    private short[] mSwapY16;
    private short[] mY16;
    private byte[] mSwapParamLine;
    private byte[] mParamLine;
    private final Object mTempLock = new Object();
    private boolean hasParamLine = false; //参数行获取高低温 中心温
    private int measureTempMode = -1; //全图温度获取方式。-1：不可测温，0：温度矩阵，1：Y16+Param测温
    private MTUserParam mCoinMTParam = new MTUserParam();
    private float[] temperature;
    private int mFrameCount = 0;
    private int regionGreenId;  // 存储拼接图像左侧的区域索引
    private float regionY;  // 存储拼接图像左侧区域索引的纵坐标
    private int regionBlueId;  // 存储拼接图像区域的区域索引

    private float maxTemperature;  // 存储拼接图像的最高温，用来绘制热点追踪
    private float minTemperature;  // 存储拼接图像的最低温，用来绘制热点追踪

    private int maxTemperaturePixelX;  // 存储拼接图像的最高温横坐标，用来绘制热点追踪与全局告警
    private int maxTemperaturePixelY;  // 存储拼接图像的最高温纵坐标，用来绘制热点追踪与全局告警

    private int minTemperaturePixelX;  // 存储拼接图像的最低温横坐标，用来绘制热点追踪与全局告警
    private int minTemperaturePixelY;  // 存储拼接图像的最低温纵坐标，用来绘制热点追踪与全局告警

    private float globalMaxTemp;  // 存储拼接图像的全局最高温（包含区域），用来绘制调色板
    private float globalMinTemp;  // 存储拼接图像的全局最低温（包含区域），用来绘制调色板

    private float sizeX;  // 存储图像分辨率参数
    private float sizeY;  // 存储图像分辨率参数
    public static DeviceConfig mDeviceConfig = new DeviceConfig();
    private IRSetting iRSetting = new IRSetting();

//    private MediaCodec mediaEncoder; /////



    private class ARGBFrame {
        public ARGBFrame(int cap) {
            data = new byte[cap];
        }
        public final byte[] data;
        public boolean isReady = false;

        public void noTrans() {
            for (int i = 3; i < data.length; i += 4) {
                data[i] = (byte) 0xFF;
            }
        }

        public byte[] getData() {
            return data;
        }

        private int RGB(int x, int y) {
            return (data[(y * CAMERA_RESOLUTION_W + x) * 4] & 0xFF) << 16 |
                    (data[(y * CAMERA_RESOLUTION_W + x) * 4 + 1] & 0xFF) << 8 |
                    (data[(y * CAMERA_RESOLUTION_W + x) * 4 + 2] & 0xFF);
        }

        public boolean isBlack() {
            for (int x : new int[]{0, CAMERA_RESOLUTION_W / 3, CAMERA_RESOLUTION_W * 2 / 3, CAMERA_RESOLUTION_W - 1}) {
                for (int y : new int[]{0, CAMERA_RESOLUTION_H / 3, CAMERA_RESOLUTION_H * 2 / 3, CAMERA_RESOLUTION_H - 1}) {
                    if (RGB(x, y) != 0) return false;
                }
            }
            Log.i(Log.TAG, "高德红外视频帧全黑");
            return true;
        }
    }

    private ARGBFrame videoFrame;
    private ARGBFrame photoFrame;

    // 从相机出来的原始图
    Bitmap sourceBitmap;
    // RESIZE之后的预览图
    Bitmap previewBitmap;

    /*private int count;
    private long liveStartTime;*/

    public GUIDEDev(int ID, Context context, int highThres, int lowThres, int envAlarmThres, int cmpAlarmThres, boolean useAudio) { /////
        super(ID, context, useAudio); /////
        this.streamType = 0;
        this.type = DEVICE_USB_GUIDE;
        this.globalAlarm = new IRTempAlarm(highThres, lowThres);
        this.envAlarmThres = envAlarmThres;
        this.cmpAlarmThres = cmpAlarmThres;
        this.useAudio = useAudio; /////
        // ！！！
        Settings.VideoCodec videoCodec = new Settings.VideoCodec();
        videoCodec.frame = 20;
        videoCodec.iFrame = 20;
        videoCodec.resolution = 3;
        this.codec.put(String.valueOf(0), videoCodec);

//        mGuideInterface.setDebugLog(true);
    }

    private static HandlerThread mtempThread;  // 测温线程
    private static Handler mTempHandler;


    private void cameraInit(int stream) {
        // 初始化测温线程（只初始化一次）
        if (mtempThread == null || !mtempThread.isAlive()) {
            mtempThread = new HandlerThread("测温线程");
            mtempThread.start();
            mTempHandler = new Handler(mtempThread.getLooper());
            Log.i(Log.TAG, "测温线程已启动");
        }

        try {
            IRRegionTemp.setTempUnit(sensorConfig.measureUnit);
            if (photoConfig.color == 0) {  // 色彩切换为黑白模式
                if (sensorConfig.color == 0) {
                    boolean ret = mGuideInterface.changePalette(0);  // 使用白热伪彩
                    mDeviceConfig.setPaletteIndex(0);
                    if (ret) {
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot);
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式成功");
                    } else {
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式失败");
                    }
                } else if (sensorConfig.color == 9) {
                    boolean ret = mGuideInterface.changePalette(9);  // 使用黑热伪彩
                    mDeviceConfig.setPaletteIndex(9);
                    if (ret) {
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.black_hot);
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式成功");
                    } else {
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式失败");
                    }
                } else {
                    boolean ret = mGuideInterface.changePalette(0);  // 使用白热伪彩
                    mDeviceConfig.setPaletteIndex(0);
                    if (ret) {
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot);
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式成功");
                    } else {
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式失败");
                    }
                }
            } else if (photoConfig.color == 1) {
                boolean ret = mGuideInterface.changePalette(sensorConfig.color);
                mDeviceConfig.setPaletteIndex(sensorConfig.color);
                if (ret) {
                    switch (sensorConfig.color) {
                        case 0:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot);
                            break;
                        case 1:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.melt_stone);
                            break;
                        case 2:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.iron_red);
                            break;
                        case 3:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.hot_iron);
                            break;
                        case 4:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.medical);
                            break;
                        case 5:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.arctic);
                            break;
                        case 6:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.rainbow1);
                            break;
                        case 7:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.rainbow2);
                            break;
                        case 8:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.reddening);
                            break;
                        case 9:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.black_hot);
                            break;
                    }
                    Log.i(Log.TAG, "高德红外色彩设置为彩色模式成功");
                } else {
                    Log.i(Log.TAG, "高德红外色彩设置为彩色模式失败");
                }
            }
            Point size = null;
            /////
            if (isPhotoing()) {
                size = Settings.PhotoConfig.getImageSize(photoConfig.size);
                sizeX = size.x;
                sizeY = size.y;
                Log.i(Log.TAG, "高德红外图像分辨率设置为" + (int) sizeX + "x" + (int) sizeY);
            } else if (isLiving() || isRecording()) {
                size = Settings.VideoCodec.getResolution(codec.get(String.valueOf(stream)).resolution);
                if (codec.get(String.valueOf(stream)).resolution > 11) {
                    sizeX = 1600;
                    sizeY = 1200;
                } else {
                    sizeX = size.x;
                    sizeY = size.y;
                }
                Log.i(Log.TAG, "高德红外视频分辨率设置为" + (int) sizeX + "x" + (int) sizeY);
            }
            /////
            tempPaint = new Paint();
            tempPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            tempPaint.setStyle(Paint.Style.FILL);
            if (iRSetting.imageStitch == 0) {
                tempPaint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));  // 以640*512尺寸为基准，使用宽度、高度比例小的来缩放文字尺寸
            } else {
                tempPaint.setTextSize(16 * Math.min(sizeX * 3 / 640, sizeY / 512));
            }
            tempPaint.setAntiAlias(false);
            tempPaint.setColor(0xFF00AB00);

            timePaint = new Paint();
            if (iRSetting.imageStitch == 0) {
                timePaint.setStrokeWidth(1 * sizeX / 640);  // 使用宽度比例来缩放文字宽度
                timePaint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));
            } else {
                timePaint.setStrokeWidth(1 * sizeX * 3 / 640);
                timePaint.setTextSize(16 * Math.min(sizeX * 3 / 640, sizeY / 512));
            }
            timePaint.setStyle(Paint.Style.FILL);
            timePaint.setAntiAlias(true);
            timePaint.setColor(Color.WHITE);
            int brightness = mapFrom1To100(photoConfig.brightness, 16);
            mGuideInterface.setBrightness(brightness);
            Log.i(Log.TAG, "服务器设置亮度为" + photoConfig.brightness + "，转换后的高德红外亮度设置为" + brightness);
            int contrast = mapFrom1To100(photoConfig.contrast, 255);
            mGuideInterface.setContrast(contrast);
            Log.i(Log.TAG, "服务器设置对比度为" + photoConfig.contrast + "，转换后的高德红外对比度设置为" + contrast);
            Log.i(Log.TAG, ircmd.getCalibrationParam().toString());
        } catch (Exception e) {
            Log.i(Log.TAG, "高德红外设备初始化异常：" + e);
        }
    }

    @Override
    public void setTempRegion(int channel, int preset, Vector<IRSetting.IrRegionInfo> regions, boolean live) {
        if (channel != id) return;

        int index = 1;
        if (live && isLiving()) { //主站请求手动测温
            liveRegionsTemp.clear();
            liveRegionsDistance.clear();
            for (IRSetting.IrRegionInfo irRegion : regions) {
                // 单独存储环境温度区域
                if (irRegion.irRegion.cls == 2 || irRegion.emissivity == 0) {
                    presetEnvTemp = preset;
                    regionEnvTemp = new IRRegionTemp(context, irRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, 0, sizeX, sizeY);
                } else {
                    IRRegionTemp regionTemp = new IRRegionTemp(context, irRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, index++, sizeX, sizeY);
                    // 视频预览画区域会累加进来
                    liveRegionsTemp.add(regionTemp);
                    liveRegionsDistance.add(irRegion.distance);
                }
                Log.i(Log.TAG, "高德红外手动设置测温区域：" + irRegion.toString());
            }
        } else {
            Vector<IRRegionTemp> irRegionTempVector = new Vector<>();
            Vector<Float> irRegionDistanceVector = new Vector<>();
            Log.i(Log.TAG, "高德红外设置预置位" + preset + "测温区域：");
            for (IRSetting.IrRegionInfo irRegion : regions) {
                // 单独存储环境温度区域
                if (irRegion.irRegion.cls == 2 || irRegion.emissivity == 0) {
                    presetEnvTemp = preset;
                    regionEnvTemp = new IRRegionTemp(context, irRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, 0, sizeX, sizeY);
                } else {
                    IRRegionTemp regionTemp = new IRRegionTemp(context, irRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, index++, sizeX, sizeY);
                    irRegionTempVector.add(regionTemp);
                    // 添加区域的距离信息
                    irRegionDistanceVector.add(irRegion.distance);
                }
                Log.i(Log.TAG, "高德红外：" + irRegion.toString());
            }
            // 图片画区域会替代之前预置位的值
            staticRegionsMaps.put(preset, irRegionTempVector);
            staticRegionsDistanceMaps.put(preset, irRegionDistanceVector);
        }
    }

    private volatile boolean measureRunning = false; // 用于控制测温线程的状态
    private void stopTemp() {
        Log.i(Log.TAG, "高德红外停止读取温度值");
        if (mGuideInterface != null) {
            measureRunning = false;
        }
    }

    private void startPreview(int stream, int preset) {
        Log.i(Log.TAG, "高德红外开启预览");

        mGuideInterface.startPreview((dataCallback) -> {
            if (!dataCallback.isSuccess) {
                //Log.i(Log.TAG, "高德红外预览数据未成功获取");
                return;
            }
            //此处返回的是机芯返回的uyvy数据。若数据返回类型无uyvy数据，则可以拿温度矩阵或者Y16数据转换rgb后渲染
            // 从dataCallback.uyvy或dataCallback.y16获取原始数据
            synchronized (videoFrame.data) {    // 线程同步，生产者
                if (dataCallback.uyvy != null) {
                    //Log.i(Log.TAG, "高德红外预览数据包含uyvy数据");
                    mBitmap = mGuideInterface.yuvToBitmap(dataCallback.uyvy, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, false, AngleMenu.NONE, MirrorMode.MIRROR_NORMAL, sensorConfig.color);
                    NativeGuideCore.bitmap2argb(mBitmap, mArgb, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H);
                    NativeGuideCore.argb2abgr(mArgb, mAbgr, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H);
                    System.arraycopy(mAbgr, 0, videoFrame.data, 0, mArgb.length);
                } else if (dataCallback.y16 != null) { ///报错，虽然用不到
                    //Log.i(Log.TAG, "高德红外预览数据包含Y16数据");
                    if (byteArray.length != (CAMERA_RESOLUTION_W * CAMERA_RESOLUTION_H * 3)) {
                        byteArray = new byte[CAMERA_RESOLUTION_W * CAMERA_RESOLUTION_H * 3];
                    }
                    NativeUtil.y16ToRgb24(dataCallback.y16, byteArray, dataCallback.uyvy.length, sensorConfig.color);
                    NativeGuideCore.rgb2Bitmap(byteArray, mBitmap.getWidth(), mBitmap.getHeight(), mBitmap, false);
                    NativeGuideCore.bitmap2argb(mBitmap, mArgb, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H);
                    System.arraycopy(mArgb, 0, videoFrame.data, 0, mArgb.length);
                }
                videoFrame.noTrans();
                videoFrame.isReady = true;
                videoFrame.data.notifyAll();  // 唤醒处理线程  // 通知消费者线程
            }

            //// 在测温线程中
            synchronized (mTempLock) {
                //参数数据处理
                hasParamLine = dataCallback.mode == GuideUsbVideoMode.YUV_PARAM || dataCallback.mode == GuideUsbVideoMode.X16_PARAM || dataCallback.mode == GuideUsbVideoMode.TEMP_PARAM_YUV || dataCallback.mode == GuideUsbVideoMode.Y16_PARAM_YUV || dataCallback.mode == GuideUsbVideoMode.Y16_PARAM || dataCallback.mode == GuideUsbVideoMode.TEMP_PARAM;
                if (hasParamLine) {
                    //Log.i(Log.TAG, "高德红外预览数据包含参数数据");
                    System.arraycopy(dataCallback.paramLine, 0, mSwapParamLine, 0, dataCallback.paramLine.length);
                }
                //温度数据处理
                if (dataCallback.mode == GuideUsbVideoMode.TEMP || dataCallback.mode == GuideUsbVideoMode.TEMP_PARAM || dataCallback.mode == GuideUsbVideoMode.TEMP_YUV || dataCallback.mode == GuideUsbVideoMode.TEMP_PARAM_YUV) {
                    //Log.i(Log.TAG, "高德红外预览数据包含温度数据");
                    System.arraycopy(dataCallback.y16, 0, mSwapY16, 0, dataCallback.y16.length);
                    measureTempMode = 0;
                } else {
                    boolean hasY16 = dataCallback.mode == GuideUsbVideoMode.Y16_PARAM || dataCallback.mode == GuideUsbVideoMode.Y16 || dataCallback.mode == GuideUsbVideoMode.Y16_PARAM_YUV || dataCallback.mode == GuideUsbVideoMode.Y16_YUV;
                    if (hasY16) {
                        //Log.i(Log.TAG, "高德红外预览数据包含Y16数据");
                        System.arraycopy(dataCallback.y16, 0, mSwapY16, 0, dataCallback.y16.length);
                    }
                    if (hasY16 && hasParamLine) {
                        //Log.i(Log.TAG, "高德红外预览数据包含Y16数据与参数数据");
                        measureTempMode = 1;
                    }
                }
            }
            mFrameCount++;
            if (startPhoto) {
                // 等待5秒抓取图片
                if (!isLiving() && System.currentTimeMillis() - startPhotoTime < 5 * PERIOD_SECOND) {
                    //Log.i(Log.TAG, "等待5秒，" + (System.currentTimeMillis() - startPhotoTime) / PERIOD_SECOND);
                    return;
                }
                synchronized (photoFrame.data) {
                    System.arraycopy(videoFrame.data, 0, photoFrame.data, 0, videoFrame.data.length);
                    photoFrame.noTrans();
                    photoFrame.isReady = true;
                    photoFrame.data.notify();
                }
                startPhoto = false;
            }
        });

        // 初始化相机和开始温度测量
        cameraInit(stream);
        scheduleShutter(sensorConfig.shutterInterval);
//        Log.i(Log.TAG, "高德红外开始读取温度值");
        startMeasureTimer(preset);  // 开启温度定时测量
    }


    private Runnable mTempRunnable;
    private volatile boolean isMeasuring = false;

    private void startMeasureTimer(int preset) {

        if (mTempRunnable != null) {
            mTempHandler.removeCallbacks(mTempRunnable);
            isMeasuring = false;   //移除之后需要重置状态
        }

        mTempRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isMeasuring) {
                    isMeasuring = true;
                    try {
                        synchronized (mTempLock) {
                            System.arraycopy(mSwapY16, 0, mY16, 0, mSwapY16.length);
                            System.arraycopy(mSwapParamLine, 0, mParamLine, 0, mSwapParamLine.length);
                        }
                        // 测温主逻辑
                        measureTemperature(preset);     // 计算温度矩阵
                    } catch (Exception e) {
                        Log.e(Log.TAG, "测温异常：" + e);
                    } finally {
                        isMeasuring = false;
                    }
                }
                mTempHandler.postDelayed(this, 500);
            }
        };
        mTempHandler.post(mTempRunnable);
    }


    private void measureTemperature(int preset) {
        if (mGuideInterface != null) {
            measureRunning = true;
            curPreset = preset;

            // 根据measureTempMode，计算温度矩阵或直接获取Y16数据并转换为温度值
            if (hasParamLine) {
                // 通过getParamLineInfo获取参数线数据并解析中心温度
                DataInternalParam internalParam = mGuideInterface.getParamLineInfo(mParamLine);
                if (internalParam != null) {
                    // 温度转换
                    if (iRSetting.focalLen == 3 && iRSetting.resolution == 1) {
                        mGuideInterface.measure(0, 7, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN612R 4.9mm
                    } else if (iRSetting.focalLen == 0 && iRSetting.resolution == 1) {
                        mGuideInterface.measure(0, 1, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN612R 9.1mm
                    } else if (iRSetting.focalLen == 1 && iRSetting.resolution == 1) {
                        mGuideInterface.measure(0, 2, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN612R 13mm
                    } else if (iRSetting.focalLen == 2 && iRSetting.resolution == 1) {
                        mGuideInterface.measure(0, 3, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN612R 19mm
                    } else if (iRSetting.focalLen == 3 && iRSetting.resolution == 0) {
                        mGuideInterface.measure(1, 7, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN417R 4.9mm
                    } else if (iRSetting.focalLen == 0 && iRSetting.resolution == 0) {
                        mGuideInterface.measure(1, 1, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN417R 9.1mm
                    } else if (iRSetting.focalLen == 1 && iRSetting.resolution == 0) {
                        mGuideInterface.measure(1, 2, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN417R 13mm
                    } else if (iRSetting.focalLen == 2 && iRSetting.resolution == 0) {
                        mGuideInterface.measure(1, 3, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN417R 19mm
                    }

                    // 初始化最大值、最小值和对应坐标
                    float maxTemp = mTemp[0];
                    float minTemp = mTemp[0];
                    int maxX = 0, maxY = 0;
                    int minX = 0, minY = 0;

                    // 遍历温度矩阵
                    for (int i = 0; i < CAMERA_RESOLUTION_H; i++) {
                        for (int j = 0; j < CAMERA_RESOLUTION_W; j++) {
                            int index = i * CAMERA_RESOLUTION_W + j;
                            float value = mTemp[index];
                            // 更新最大值和最小值
                            if (value > maxTemp) {
                                maxTemp = value;
                                maxX = j;
                                maxY = i;
                            }
                            if (value < minTemp) {
                                minTemp = value;
                                minX = j;
                                minY = i;
                            }
                            temperature[index + 10] = value;
                        }
                    }

                    float fpgaTemp = internalParam.getLens_startTemp() / 100f;
                    float shutterRealtimeTemp = internalParam.getShutter_realtimeTemp() / 100f;
                    temperature[0] = 0.0f;                ///用不到
                    temperature[1] = (float) maxX;        //温度最大值X坐标
                    temperature[2] = (float) maxY;        //温度最大值Y坐标
                    temperature[3] = maxTemp;             //温度最大值

                    temperature[4] = (float) minX;        //温度最小值X坐标
                    temperature[5] = (float) minY;        //温度最小值Y坐标
                    temperature[6] = minTemp;             //温度最小值

                    temperature[7] = fpgaTemp;            //高德机芯温度
                    temperature[8] = 0.0f;                //无接口获取NUC值
                    temperature[9] = shutterRealtimeTemp; //快门温度
                } else {
                    //Log.i(Log.TAG, "高德红外获取参数线数据为空");
                }
            } else {
                if (measureTempMode == 1) {

                    if (iRSetting.focalLen == 3 && iRSetting.resolution == 1) {
                        mGuideInterface.measure(0, 7, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN612R 4.9mm
                    } else if (iRSetting.focalLen == 0 && iRSetting.resolution == 1) {
                        mGuideInterface.measure(0, 1, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN612R 9.1mm
                    } else if (iRSetting.focalLen == 1 && iRSetting.resolution == 1) {
                        mGuideInterface.measure(0, 2, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN612R 13mm
                    } else if (iRSetting.focalLen == 2 && iRSetting.resolution == 1) {
                        mGuideInterface.measure(0, 3, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN612R 19mm
                    } else if (iRSetting.focalLen == 3 && iRSetting.resolution == 0) {
                        mGuideInterface.measure(1, 7, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN417R 4.9mm
                    } else if (iRSetting.focalLen == 0 && iRSetting.resolution == 0) {
                        mGuideInterface.measure(1, 1, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN417R 9.1mm
                    } else if (iRSetting.focalLen == 1 && iRSetting.resolution == 0) {
                        mGuideInterface.measure(1, 2, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN417R 13mm
                    } else if (iRSetting.focalLen == 2 && iRSetting.resolution == 0) {
                        mGuideInterface.measure(1, 3, mY16, mParamLine, mCoinMTParam, mTemp);  // COIN417R 19mm
                    }

                } else if (measureTempMode == 0) {
                    for (int i = 0; i < mY16.length; i++) {
                        mTemp[i] = mY16[i] / 10f;
                    }
                } else {

                }
            }
        }

        // 环境温度区域中的最高温，与汇能精电控制器的温度进行比较，取大的作为环境温度
        if (regionEnvTemp != null) {
            // 只获取设置了环境温度区域的预置位的区域温度
            if (curPreset == presetEnvTemp) {
                regionEnvTemp.setTempRaw(temperature);
                MainActivity.tempEnvRegion = regionEnvTemp.getTemperature(-1, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit).maxTemperature; /////
                MainActivity.tempEnvironment = Math.max(MainActivity.tempEnvControl, MainActivity.tempEnvRegion);
            }
        }


        // 防灼伤保护，使用全局测温距离来校正全局最高温
        irOverProtect.avoidOverexposure(globalTemp.getTemperature(sensorConfig.distance, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit).maxTemperature); /////

        // 设置全局温度数据
        if (globalTemp != null) {
            globalTemp.setTempRaw(temperature);
        }


        // 更新动态测温区域
        for (IRRegionTemp region : liveRegionsTemp) {
            region.setTempRaw(temperature);
        }

        // 更新当前预置位设置的测温区域的温度值  // TODO 这个静态区域的温度是如何获取的
        Vector<IRRegionTemp> staticRegions = staticRegionsMaps.get(curPreset);
        if (staticRegions != null) {
//            Log.i(Log.TAG, "高德红外预置位" + curPreset + "设置了" + staticRegions.size() + "个测温区");
            for (IRRegionTemp region : staticRegions) {
                region.setTempRaw(temperature);
            }
        } else {
            //Log.i(Log.TAG, "高德红外预置位" + curPreset + "没有设置测温区");
        }
        //Log.i(Log.TAG, String.format("高德红外机芯温度：%f，快门温度：%f", temperature[7], temperature[9]));
    }

    //每隔n分钟打一次快门
    private Timer timerEveryTime;

    private void scheduleShutter(int minutes) {
        int period = (minutes > 0 && minutes <= 5) ? minutes : 2;
        timerEveryTime = new Timer();
        timerEveryTime.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
//                Log.i(Log.TAG, "高德红外开始快门校正");
                boolean ret = mGuideInterface.shutter();
                if (!ret) {
                    Log.i(Log.TAG, "高德红外快门校正失败");
                }
            }
        }, 1000, period * PERIOD_MINUTE);
    }

    private void stopPreview() {
        Log.i(Log.TAG, "高德红外停止预览");
        if (mGuideInterface != null) {
            mGuideInterface.stopPreview();
        }

        if (timerEveryTime != null) timerEveryTime.cancel();
    }

    @Override
    public Settings.FileDir findFiles(int type, final Settings.TimeRecord begin, final Settings.TimeRecord end) {
        Settings.FileDir ret = new Settings.FileDir();
        ret.channel = (byte) id;
        ret.begin = begin;
        ret.end = end;
        ret.count = 0;
        return ret;
    }

    @Override
    public Settings.FileList listFile(int videoType, String startTime, String stopTime, int startNumb, int endNumb) {
        Settings.FileList ret = new Settings.FileList();
        ret.channel = (byte) id;
        ret.begin = ret.end = 0;
        ret.type = videoType;
        return ret;
    }

    private boolean doGlobalAlarm(int preset, Bitmap bitmap, long timestamp) {
        // 测温设备没有全局报警
        if (globalAlarm == null) return false;

        IRRegionTemp.TemperatureSampleResult temperature = globalTemp.getTemperature(sensorConfig.distance, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit);  // 使用全局测温距离校正 /////
        if (iRSetting.imageStitch == 1 && preset == 1) {
            temperature.maxTemperature = maxTemperature;
            temperature.maxTemperaturePixel.x = maxTemperaturePixelX;
            temperature.maxTemperaturePixel.y = maxTemperaturePixelY;
            temperature.minTemperature = minTemperature;
            temperature.minTemperaturePixel.x = minTemperaturePixelX;
            temperature.minTemperaturePixel.y = minTemperaturePixelY;
        }
        EnumSet<IRTempAlarm.AlarmType> alarmTypes = globalAlarm.checkAlarms("全域告警", temperature, (byte) 3); /////
        if (!alarmTypes.isEmpty()) {
            List<Settings.ObjectInfo> objects = new ArrayList<>();
            for (IRTempAlarm.AlarmType alarm : alarmTypes) {
                Settings.ObjectInfo obj = new Settings.ObjectInfo();
                obj.classID = 40;
                obj.confidence = 100;
                if (alarm.equals(IRTempAlarm.AlarmType.ALARM_GLOBAL_HIGH_TEMPERATURE)) {
                    obj.left = obj.right = (byte) (temperature.maxTemperaturePixel.x * 255 / CAMERA_RESOLUTION_W);
                    obj.top = obj.bottom = (byte) (temperature.maxTemperaturePixel.y * 255 / CAMERA_RESOLUTION_H);
                } else {
                    obj.left = obj.right = (byte) (temperature.minTemperaturePixel.x * 255 / CAMERA_RESOLUTION_W);
                    obj.top = obj.bottom = (byte) (temperature.minTemperaturePixel.y * 255 / CAMERA_RESOLUTION_H);
                }
                objects.add(obj);
            }

            IRTempAlarm.updateAlarm(timestamp);
            alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), 10 * (float) bitmap.getWidth() / 640, 60 * (float) bitmap.getHeight() / 512);
            return true;
        }
        return false;
    }

    private void alarmProcess(int preset, Bitmap bitmap, EnumSet<IRTempAlarm.AlarmType> types,
                              List<Settings.ObjectInfo> objects, Settings.FireAlarmInfo alarmInfo, float x, float y) {
        // 绘制osd
        IRTempAlarm.drawAlarmText(bitmap, types, x, y);

        // 发送山火告警
        controllerCallback.onFireAlarm(alarmInfo);

        // 发送报警细节信息
        if (!objects.isEmpty()) {
            Settings.DetectInfo info = new Settings.DetectInfo();
            info.time = new Settings.TimeRecord(alarmInfo.alarmTime);
            info.channel = (byte) (id & 0xFF);
            info.preset = (byte) (preset & 0xFF);
            info.objects = objects;
            controllerCallback.onObjectDetect(info);
        }
    }

    // 区域温度和设置的阈值比较
    private boolean doTempRegionAlarmWithThreshold(int preset, Bitmap bitmap, long timestamp, Vector<IRRegionTemp> irRegionTemps, Vector<Float> irRegionDistances, float x, float y) {
        if (irRegionTemps == null || irRegionTemps.isEmpty() || irRegionDistances == null || irRegionDistances.isEmpty()) return false;

        EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.noneOf(IRTempAlarm.AlarmType.class);
        List<Settings.ObjectInfo> objects = new ArrayList<>();
        for (int regionIndex = 0; regionIndex < irRegionTemps.size(); regionIndex++) {
            IRRegionTemp t = irRegionTemps.get(regionIndex);
            float d = irRegionDistances.get(regionIndex);
            IRRegionTemp.TemperatureSampleResult temperature = t.getTemperature(d, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit);  // 使用区域测温距离进行校正 /////

            // 通过温度值和高低温告警阈值返回告警类型，只有设置了测温区域，才会有告警阈值，才会产生告警
            // 全局告警因为没有告警阈值，暂时不生效，不会走到这里
            EnumSet<IRTempAlarm.AlarmType> regionAlarms = t.getTemperatureAlarm(temperature, t.tempRegion().flag); /////
            if (!regionAlarms.isEmpty()) {
                // 高低温告警
                for (IRTempAlarm.AlarmType alarm : regionAlarms) {
                    Settings.ObjectInfo obj = new Settings.ObjectInfo();
                    obj.classID = 40;
                    obj.confidence = 100;
                    obj.left = obj.right = (byte) (temperature.maxTemperaturePixel.x * 255 / CAMERA_RESOLUTION_W);
                    obj.top = obj.bottom = (byte) (temperature.maxTemperaturePixel.y * 255 / CAMERA_RESOLUTION_H);
                    objects.add(obj);
                }
                alarmTypes.addAll(regionAlarms);
            }
        }

        if (!alarmTypes.isEmpty()) {
            IRTempAlarm.updateAlarm(timestamp);
            alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), x, y);
            return true;
        }
        return false;
    }

    // 各个区域类型的多个区域之间最高温差比较
    private boolean doTempRegionAlarmWithCompare(int preset, Bitmap bitmap, long timestamp, Vector<IRRegionTemp> irRegionTemps, Vector<Float> irRegionDistances, float x, float y) {
        if (irRegionTemps == null || irRegionTemps.isEmpty() || irRegionTemps.size() < 2 || irRegionDistances == null || irRegionDistances.isEmpty() || irRegionDistances.size() < 2) return false;

        // 分类存储区域，按cls分类
        HashMap<Byte, List<IRRegionTemp>> groupedRegions = new HashMap<>();
        HashMap<Byte, List<Float>> groupedRegionDistances = new HashMap<>();
        for (int regionIndex = 0; regionIndex < irRegionTemps.size(); regionIndex++) {
            IRRegionTemp region = irRegionTemps.get(regionIndex);
            float regionDistance = irRegionDistances.get(regionIndex);
            byte cls = region.tempRegion().cls; // 获取区域类型cls
            groupedRegions.computeIfAbsent(cls, k -> new ArrayList<>()).add(region);
            groupedRegionDistances.computeIfAbsent(cls, k -> new ArrayList<>()).add(regionDistance);
        }
        // 存储所有需要生成告警的cls值
        Set<Byte> alertCls = new HashSet<>();
        // 遍历每个分类
        for (HashMap.Entry<Byte, List<IRRegionTemp>> entry : groupedRegions.entrySet()) {
            byte cls = entry.getKey(); // 当前分类的cls值
            List<IRRegionTemp> regions = entry.getValue();
            List<Float> regionDistances = groupedRegionDistances.get(cls);

            // 如果分类内的区域少于2个，跳过
            if (regions.size() < 2) continue;

            int len = regions.size();
            float[] maxTemperatures = new float[len];
            for (int i = 0; i < len; i++) {
                IRRegionTemp t = regions.get(i);
                float d = regionDistances.get(i);
                IRRegionTemp.TemperatureSampleResult temperature = t.getTemperature(d, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit);  // 使用区域测温距离进行校正 /////
                maxTemperatures[i] = temperature.maxTemperature;
                Log.i(Log.TAG, "高德红外类型[" + cls + "]区域[" + i + "]最高温度：" + maxTemperatures[i]);
            }
            if (maxTemperatures != null) {
                // 升序排序
                Arrays.sort(maxTemperatures);
            }

            if (maxTemperatures[len - 1] - maxTemperatures[0] > cmpAlarmThres) {
                Log.i(Log.TAG, String.format("高德红外测温区域：%d，最大温差：%.2f，超过设定阈值：%d",
                        len, maxTemperatures[len - 1] - maxTemperatures[0], cmpAlarmThres));
                alertCls.add(cls);
            }
        }
        if (alertCls.size() > 0) {
            List<Settings.ObjectInfo> objects = new ArrayList<>();
            Settings.ObjectInfo obj = new Settings.ObjectInfo();
            obj.classID = 40;
            obj.confidence = 100;
            objects.add(obj);

            IRTempAlarm.updateAlarm(timestamp);
            // 产生一个三相不平衡报警
            if (alertCls.contains((byte) 0) && alertCls.contains((byte) 1)) {
                // 如果有cls为0和cls为1的区域，生成电缆终端和避雷器三相不平衡告警
                EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.of(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE_BOTH);
                alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), x, y);
                Log.i(Log.TAG, "高德红外生成电缆终端和避雷器三相不平衡告警");
            } else if (alertCls.contains((byte) 0) && alertCls.contains((byte) -1)) {
                // 如果有cls为0和cls为-1的区域，生成电缆终端三相不平衡告警
                EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.of(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE_0);
                alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), x, y);
                Log.i(Log.TAG, "高德红外生成电缆终端三相不平衡告警");
            } else if (alertCls.contains((byte) 0)) {
                // 如果只有cls为0的区域，生成ALARM_COM_TEMPERATURE_0告警
                EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.of(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE_0);
                alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), x, y);
                Log.i(Log.TAG, "高德红外生成电缆终端三相不平衡告警");
            } else if (alertCls.contains((byte) 1) && alertCls.contains((byte) -1)) {
                // 如果有cls为1和cls为-1的区域，生成避雷器三相不平衡告警
                EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.of(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE_1);
                alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), x, y);
                Log.i(Log.TAG, "高德红外生成避雷器三相不平衡告警");
            } else if (alertCls.contains((byte) 1)) {
                // 如果只有cls为1的区域，生成ALARM_COM_TEMPERATURE_1告警
                EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.of(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE_1);
                alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), x, y);
                Log.i(Log.TAG, "高德红外生成避雷器三相不平衡告警");
            } else if (alertCls.contains((byte) -1) && !alertCls.contains((byte) 0) && !alertCls.contains((byte) 1)) {
                // 如果只有cls为-1的区域，生成三相不平衡告警
                EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.of(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE);
                alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), x, y);
                Log.i(Log.TAG, "高德红外生成三相不平衡告警");
            }
            return true;
        }
        return false;
    }

    // 区域温度和环境温度之间的温差比较
    private boolean doTempRegionAlarmWithEnvironment(int preset, Bitmap bitmap, long timestamp, Vector<IRRegionTemp> irRegionTemps, Vector<Float> irRegionDistances, float envTemp, float x, float y) {
        if (irRegionTemps == null || irRegionTemps.isEmpty() || irRegionDistances == null || irRegionDistances.isEmpty()) return false;

        for (int regionIndex = 0; regionIndex < irRegionTemps.size(); regionIndex++) {
            IRRegionTemp t = irRegionTemps.get(regionIndex);
            float d = irRegionDistances.get(regionIndex);
            IRRegionTemp.TemperatureSampleResult temperature = t.getTemperature(d, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit);  // 使用区域测温距离进行校正 /////

            // 如果没有绘制环境温度区域，就20:00~8:00才开启温升告警
            Date now = new Date();
            if (MainActivity.tempEnvRegion != Float.NEGATIVE_INFINITY || (now.before(dateFromString("08:00:00")) && now.after(dateFromString("20:00:00")))) {
                if (temperature.maxTemperature > envTemp + envAlarmThres) {
                    Log.i(Log.TAG, String.format("高德红外环境温度：%.2f，最高温度：%.2f，超过设定阈值：%d，生成温升告警",
                            envTemp, temperature.maxTemperature, envAlarmThres));

                    IRTempAlarm.updateAlarm(timestamp);
                    List<Settings.ObjectInfo> objects = new ArrayList<>();
                    Settings.ObjectInfo obj = new Settings.ObjectInfo();
                    obj.classID = 40;
                    obj.confidence = 100;
                    objects.add(obj);
                    EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.of(IRTempAlarm.AlarmType.ALARM_ENV_TEMPERATURE);
                    alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), x, y);
                    return true;
                }
            }
        }
        return false;
    }

    private void doTempReport(int preset, long timestamp, Vector<IRRegionTemp> irRegionTemps, Vector<Float> irRegionDistances) {
        if (irRegionTemps == null || irRegionTemps.isEmpty()) return;

        Vector<IRSetting.IrRegionTemp> regionTemps = new Vector<>();
        for (int regionIndex = 0; regionIndex < irRegionTemps.size(); regionIndex++) {
            IRRegionTemp t = irRegionTemps.get(regionIndex);
            float d = irRegionDistances.get(regionIndex);
            IRSetting.IrRegionTemp result = new IRSetting.IrRegionTemp();
            IRRegionTemp.TemperatureSampleResult temperature = t.getTemperature(d, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit);  // 使用区域测温距离进行校正 /////
            result.irRegion = t.tempRegion();
            result.tempAvg = temperature.avgTemperature;
            result.tempMax = temperature.maxTemperature;
            result.tempMin = temperature.minTemperature;
            regionTemps.add(result);
        }

        if (!regionTemps.isEmpty()) {
            controllerCallback.onTemperatureReport(this.id, preset, timestamp, regionTemps);
        }
    }

    private void doTempCallback(int preset, Bitmap bitmap, long timestamp, HashMap<Integer, Vector<IRRegionTemp>> regionsMaps, HashMap<Integer, Vector<Float>> distancesMaps) {
        Vector<IRRegionTemp> irRegionTemps = regionsMaps.get(preset);
        Vector<Float> irRegionDistances = distancesMaps.get(preset);

        boolean globalAlarmed = false;
        if (iRSetting.globalAlarm == 1 && sensorConfig.measureUnit != 1) { ///////
            globalAlarmed = doGlobalAlarm(preset, bitmap, timestamp);
        }

        if (irRegionTemps != null) {
            doTempReport(preset, timestamp, irRegionTemps, irRegionDistances);
            // 根据开关选择报警
            boolean thresholdAlarmed = false;
            if (iRSetting.thresholdAlarm == 1) {
                if (globalAlarmed) {
                    thresholdAlarmed = doTempRegionAlarmWithThreshold(preset, bitmap, timestamp, irRegionTemps, irRegionDistances, 10 * (float) bitmap.getWidth() / 640, 80 * (float) bitmap.getHeight() / 512);  // 缩放所有出现在图像中的文字 /////
                } else {
                    thresholdAlarmed = doTempRegionAlarmWithThreshold(preset, bitmap, timestamp, irRegionTemps, irRegionDistances, 10 * (float) bitmap.getWidth() / 640, 60 * (float) bitmap.getHeight() / 512); /////
                }
            }
            boolean envAlarmed = false;
            if (iRSetting.envAlarm == 1) {
                if (globalAlarmed && thresholdAlarmed) {
                    envAlarmed = doTempRegionAlarmWithEnvironment(preset, bitmap, timestamp, irRegionTemps, irRegionDistances, MainActivity.tempEnvironment, 10 * (float) bitmap.getWidth() / 640, 100 * (float) bitmap.getHeight() / 512); /////
                } else if (globalAlarmed || thresholdAlarmed) {
                    envAlarmed = doTempRegionAlarmWithEnvironment(preset, bitmap, timestamp, irRegionTemps, irRegionDistances, MainActivity.tempEnvironment, 10 * (float) bitmap.getWidth() / 640, 80 * (float) bitmap.getHeight() / 512); /////
                } else {
                    envAlarmed = doTempRegionAlarmWithEnvironment(preset, bitmap, timestamp, irRegionTemps, irRegionDistances, MainActivity.tempEnvironment, 10 * (float) bitmap.getWidth() / 640, 60 * (float) bitmap.getHeight() / 512); /////
                }
            }
            boolean comAlarmed = false;
            if (iRSetting.comAlarm == 1) {
                if (globalAlarmed && thresholdAlarmed && envAlarmed) {
                    comAlarmed = doTempRegionAlarmWithCompare(preset, bitmap, timestamp, irRegionTemps, irRegionDistances, 10 * (float) bitmap.getWidth() / 640, 120 * (float) bitmap.getHeight() / 512); /////
                } else if ((globalAlarmed && thresholdAlarmed) || (globalAlarmed && envAlarmed) || (thresholdAlarmed && envAlarmed)) {
                    comAlarmed = doTempRegionAlarmWithCompare(preset, bitmap, timestamp, irRegionTemps, irRegionDistances, 10 * (float) bitmap.getWidth() / 640, 100 * (float) bitmap.getHeight() / 512); /////
                } else if ((globalAlarmed || thresholdAlarmed || envAlarmed)) {
                    comAlarmed = doTempRegionAlarmWithCompare(preset, bitmap, timestamp, irRegionTemps, irRegionDistances, 10 * (float) bitmap.getWidth() / 640, 80 * (float) bitmap.getHeight() / 512); /////
                } else {
                    comAlarmed = doTempRegionAlarmWithCompare(preset, bitmap, timestamp, irRegionTemps, irRegionDistances, 10 * (float) bitmap.getWidth() / 640, 60 * (float) bitmap.getHeight() / 512); /////
                }
            }
        }
    }

    /**
     * 避免usb摄像头卡死，拍照之前启动定时器，拍照完成销毁，如拍照不能正常结束，
     * 定时器超时会进入callback处理函数，重启apk
     */
    private TimerTask blockChecking;
    private void enter(int timeoutSeconds) {
        if (blockChecking != null) {
            blockChecking.cancel();
        }
        final Device device = this;

        blockChecking = new TimerTask() {
            @Override
            public void run() {
                controllerCallback.onCameraBlocked(device);
            }
        };
        Utils.scheduleTask(blockChecking, timeoutSeconds * PERIOD_SECOND);
    }


    private void exit() {
        if (blockChecking != null) {
            blockChecking.cancel();
            blockChecking = null;
        }
    }

    public synchronized void closeCamera() { /////
        super.closeCamera(); /////
        try {
            Log.i(Log.TAG, "关闭高德红外机芯");
            mGuideInterface.close();
            clearState();
        } catch (Exception e) {
            Log.i(Log.TAG, "关闭高德红外机芯异常");
        }
    }

    /**
     * usb3.0 设置预览数据
     *
     * @param deviceConfig 模组参数
     */
    private void setPreviewConfig(DeviceConfig deviceConfig) {
        PreviewConfig previewConfig = new PreviewConfig();
        // 模组输出数据流模式
        previewConfig.guideUsbVideoMode = deviceConfig.getGuideUsbVideoMode();
        // 模组原始宽高
        previewConfig.deviceStatusInfo = new DeviceStatusInfo(deviceConfig.getWidth(), deviceConfig.getHeight(), deviceConfig.getCommunicationId());
        // 色带
        previewConfig.paletteIndex = deviceConfig.getPaletteIndex();
        mGuideInterface.setPreviewConfig(previewConfig);
    }
    //原始红外宽
    private static int WIDTH;
    //原始红外高
    private static int HEIGHT;
    private boolean openCamera() {
        if (isOpened()) {
            Log.i(Log.TAG, "高德红外机芯已打开");
            return true;
        }
        try {
            UsbDevice device = DeviceUtils.getGuideUsbDevice(context);
            if (device == null) {
                return false;
            }
            // 判断设备是否有权限，没有则申请权限
            if (!DeviceUtils.hasUsbPermission(context, device)) {
                DeviceUtils.requestUsbPermission(context, device);
            } else {
                // 初始化高德红外机芯
                boolean ret = mGuideInterface.open(context, (callbackConfig) -> {
                    if (DeviceUtils.isUsbDevice2_0(context)) {
                        mDeviceConfig.setWidth(callbackConfig.width);
                        mDeviceConfig.setHeight(callbackConfig.height);
                        mDeviceConfig.setPaletteIndex(sensorConfig.color);  // 设置预览色带
                    } else {
                        // 设置预览配置
                        setPreviewConfig(mDeviceConfig);
                    }
                    mBitmap = Bitmap.createBitmap(mDeviceConfig.getWidth(), mDeviceConfig.getHeight(), Bitmap.Config.ARGB_8888);
                    mTemp = new float[mDeviceConfig.getWidth() * mDeviceConfig.getHeight()];
                    mSwapY16 = new short[mDeviceConfig.getWidth() * mDeviceConfig.getHeight()];
                    mSwapParamLine = new byte[mDeviceConfig.getWidth() * 2];
                    mY16 = new short[mDeviceConfig.getWidth() * mDeviceConfig.getHeight()];
                    mParamLine = new byte[mDeviceConfig.getWidth() * 2];
                });
                if (!ret) {
                    Log.i(Log.TAG, "高德红外机芯打开失败");
                    return false;
                }
                ircmd = new IRCMD(mGuideInterface);
                irOverProtect = new IROverProtect(ircmd, OVER_EXPOSURE_THRESHOLD);
                globalTemp = new IRRegionTemp(context, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, 0, sizeX, sizeY);
                setState(DevState.OPENED);
                Log.i(Log.TAG, "高德红外机芯打开成功");
                return true;
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "打开高德红外机芯异常：" + e);
            return false;
        }
        return true;
    }

    @Override
    public boolean open(int stream, onOpenCallback cb, int timeoutSeconds, boolean waitSelfCheck) {
        Log.i(Log.TAG, "开启高德红外机芯");
        // 直播或录像已经占用高德红外时，不能重新打开USB机芯，否则会打断正在进行的拉流。
        if (isLiving() || isRecording()) {
            Log.i(Log.TAG, "高德红外业务运行中，复用当前机芯连接");
            if (cb != null) cb.openSucceed();
            return true;
        }
        try {
            enter(timeoutSeconds + 60);  // 打开设备超时认为卡死，重启apk
            setState(DevState.OPENING);
            closeForce = false;

            long startUpTime = System.currentTimeMillis();
            boolean isOpen;
            do {
                if (closeForce) return false;
                isOpen = openCamera();

                if (ptzDev.isDeviceReady() && isOpen) break;  // 可见光机芯

                SystemClock.sleep(PERIOD_SECOND);
            } while (System.currentTimeMillis() - startUpTime < timeoutSeconds * PERIOD_SECOND);

            if (waitSelfCheck) {
                // 设备至少启动1分钟，太少有可能机芯或云台没有完成自检
                long elapse = (System.currentTimeMillis() - startUpTime);
                if (MIN_BOOTUP_MILISECONDS - elapse > 0) SystemClock.sleep(MIN_BOOTUP_MILISECONDS - elapse);
            }

            if (!isOpen) {
                Log.i(Log.TAG, "开启高德红外机芯失败，无法识别设备");
                closeCamera();
                if (MainActivity.getChargeControl() == 6) {
                    // 如果是汇能精电控制器并且使用GPIO分别控制云台与红外，就重启红外
                    final Device device = this;
                    controllerCallback.onCameraBlocked(device);
                    // 重启usb外设
                    //if (cb != null) cb.openFailed(0);
                } else {
                    // 这里不重启负载，否则会影响可见光机芯拍照，也会反复重启，等待晚上拍照重启或硬重启就好了
                    //controllerCallback.onCameraBlocked(stream + 1);
                    // 此时要重启usb外设才能起作用，执行回调函数
                    if (cb != null) cb.openFailed(0);
                }
                return false;
            }

            clearState(DevState.OPENING);

            Log.i(Log.TAG, "打开高德红外机芯耗时 " + (System.currentTimeMillis() - startUpTime) / 1000 + " 秒");
            if (cb != null) cb.openSucceed();
            return true;
        } catch (Exception e) {
            Log.i(Log.TAG, "打开高德红外机芯异常：" + e);
            // 高德红外不重启APK
            //Log.i(Log.TAG, "重启apk");
            //restartApplication(context, 0);
            return false;
        } finally {
            exit();
        }
    }

    @Override
    public boolean close() {
        try {
            closeCamera();
            closeForce = true;
            if (MainActivity.currentMode == MainActivity.MODE_WAKEUP){
                ptzDev.close();
            }
            Log.i(Log.TAG, "高德红外机芯关闭");
        } catch (Exception e) {
            Log.i(Log.TAG, "高德红外机芯关闭异常：" + e);
        }
        return true;
    }

    private String getOSDText() {
        //IRRegionTemp.TemperatureSampleResult result = globalTemp.getTemperature();

        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        //String temp = String.format("Tmax:%.1f°C Tmin:%.1f°C", result.maxTemperature, result.minTemperature);
        return time;// + " " + temp;
    }

    private boolean shouldShowEnvironmentTemperature() {
        return MainActivity.getChargeControl() == 6;
    }

    /**
     * 绘制图片左上角水印
     */
    @Override
    protected void drawWatermark(@NonNull Bitmap bitmap) {
        Canvas canvas = new Canvas(bitmap);
        if (canvas != null) {
//            Log.i(Log.TAG, "高德红外开始绘制OSD");
            if (System.currentTimeMillis() - drawOSDTime >= PERIOD_SECOND) {
                osdText = getOSDText();
                drawOSDTime = System.currentTimeMillis();
            }

            if (osd.time == 1) {
                canvas.drawText(osdText, 10 * (float) bitmap.getWidth() / 640, 20 * (float) bitmap.getHeight() / 512, timePaint);
            } else {
                canvas.drawText("", 10 * (float) bitmap.getWidth() / 640, 20 * (float) bitmap.getHeight() / 512, timePaint);
            }

            if (osd.tag == 1) {
//                Log.e(Log.TAG,"MainActivity.tempEnvControl != Float.NEGATIVE_INFINITY:"+(MainActivity.tempEnvControl != Float.NEGATIVE_INFINITY));  // false
//                if (MainActivity.tempEnvControl != Float.NEGATIVE_INFINITY) {

//                    Log.e(Log.TAG,"deviceConfig.chargeControl == 6:"+(deviceConfig.chargeControl == 6));
//                    if(deviceConfig.chargeControl == 6){
//                        String unit = (sensorConfig.measureUnit == 0) ? "°C" : "°F";
//                        String temp = String.format("环境温度%3.1f%s", MainActivity.tempEnvironment, unit);
//                        canvas.drawText(temp, 10 * (float) bitmap.getWidth() / 640, 40 * (float) bitmap.getHeight() / 512, timePaint);
//                    }
//                }

                if (shouldShowEnvironmentTemperature()) {
                    String unit = (sensorConfig.measureUnit == 0) ? "°C" : "°F";
//                    String temp = String.format("环境温度%3.1f%s", MainActivity.tempEnvironment, unit);
//                    Log.e(Log.TAG,"tempEnvRegion"+MainActivity.tempEnvironment);
//                    Log.e(Log.TAG,"temp:"+temp);
                    String temp;
                    if (Float.isInfinite(MainActivity.tempEnvironment) || MainActivity.tempEnvironment == -200.0f) {
                        // 未接入汇能精电
                        temp = " ";
                    } else {
                        temp = String.format("环境温度%3.1f%s", MainActivity.tempEnvironment, unit);
                    }

                    canvas.drawText(temp, 10 * (float) bitmap.getWidth() / 640, 40 * (float) bitmap.getHeight() / 512, timePaint);
                }

                if (osd.text != null) {
                    Paint paint = new TextPaint();
                    paint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));
                    paint.setColor(Color.WHITE);
                    paint.setTypeface(Typeface.DEFAULT_BOLD);
                    paint.setAntiAlias(true);
                    paint.setStyle(Paint.Style.FILL);
                    int w = bitmap.getWidth();
                    int h = bitmap.getHeight();
                    float maxWidth = w * 0.85f; // 最大行宽，可按需要调整
                    String text = osd.text;
                    int start = 0;
                    int len = text.length();
                    float y = 470 * (float) bitmap.getHeight() / 512;
//                    Log.e(Log.TAG,"(float) bitmap.getHeight()"+(float) bitmap.getHeight());  // 576
                    while (start < len) {
                        int end = paint.breakText(text, start, len, true, maxWidth, null);
                        String line = text.substring(start, start + end);
                        y += 15 * (float) bitmap.getHeight() / 512;
                        canvas.drawText(line, 10 * (float) bitmap.getWidth() / 640, y, timePaint);
                        start += end;
                    }
                }
                /////
            }
        }
    }

    private Bitmap drawProtection(@NonNull Bitmap bitmap) {
        Canvas canvas = new Canvas(bitmap);
        if (canvas != null) {
            //Log.i(Log.TAG, "高德红外开始绘制‘防灼伤保护’文字");
            if(sensorConfig.measureUnit != 1){
                canvas.drawText("防灼伤保护", 150 * (float) bitmap.getWidth() / 640, 120 * (float) bitmap.getHeight() / 512, timePaint);
            }

        }
        return bitmap;
    }

    /**
     * 绘制调色板和最高/最低温度
     */
    private void drawPalette(@NonNull Bitmap bitmap, Vector<IRRegionTemp> showRegions, Vector<Float> showRegionDistances) {
        if (osdPalette != null) {
            //Log.i(Log.TAG, "高德红外开始绘制调色板");

            Canvas canvas = new Canvas(bitmap);

            Rect dest = new Rect(bitmap.getWidth() - 16 * bitmap.getWidth() / 640, 40 * bitmap.getHeight() / 512, bitmap.getWidth(), bitmap.getHeight() - 40 * bitmap.getHeight() / 512);

            canvas.drawBitmap(osdPalette, null, dest, null);

            IRRegionTemp.TemperatureSampleResult result = globalTemp.getTemperature(sensorConfig.distance, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit);  // 使用全局测温距离校正 /////
            // 比较全局最高温与区域最高温
            float maxTemp = result.maxTemperature;
            float minTemp = result.minTemperature;
            if (showRegions != null && showRegionDistances != null) {
                for (int regionIndex = 0; regionIndex < showRegions.size(); regionIndex++) {
                    IRRegionTemp region = showRegions.get(regionIndex);
                    float regionDistance = showRegionDistances.get(regionIndex);
                    IRRegionTemp.TemperatureSampleResult regionResult = region.getTemperature(regionDistance, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit);  // 使用区域测温距离进行校正 /////
                    maxTemp = Math.max(maxTemp, regionResult.maxTemperature);
                    minTemp = Math.min(minTemp, regionResult.minTemperature);
                }
            }

            // 加是向下移动，减是向上移动
            float maxTempY = 55 * (float) bitmap.getHeight() / 512 - 30;
            float minTempY = bitmap.getHeight() - 45 * (float) bitmap.getHeight() / 512 + 30;

            // TODO 在界面上显示高低温，以及修改位置
            globalTemp.drawTemperature(canvas, maxTemp, bitmap.getWidth() - 55 * (float) bitmap.getWidth() / 640, maxTempY, (float) bitmap.getWidth(), (float) bitmap.getHeight());  // 使用全局最高温与区域最高温比较后的最高温度
            globalTemp.drawTemperature(canvas, minTemp, bitmap.getWidth() - 55 * (float) bitmap.getWidth() / 640, minTempY, (float) bitmap.getWidth(), (float) bitmap.getHeight());  // 使用全局最低温与区域最低温比较后的最低温度
        }
    }

    private void drawHotTracker(@NonNull Bitmap bitmap) {
        //Log.i(Log.TAG, "高德红外开始热点追踪");
        Canvas canvas = new Canvas(bitmap);

        IRRegionTemp.TemperatureSampleResult result = globalTemp.getTemperature(sensorConfig.distance, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit);  // 使用全局测温距离校正 /////
        if (result.maxTemperaturePixel.x > (CAMERA_RESOLUTION_W - 10))
            result.maxTemperaturePixel.x = CAMERA_RESOLUTION_W - 10;
        if (result.maxTemperaturePixel.y > (CAMERA_RESOLUTION_H - 10))
            result.maxTemperaturePixel.y = CAMERA_RESOLUTION_H - 10;
        else if (result.maxTemperaturePixel.y < 10) {
            result.maxTemperaturePixel.y = 10;
        }

        if (result.minTemperaturePixel.x > (CAMERA_RESOLUTION_W - 10))
            result.minTemperaturePixel.x = CAMERA_RESOLUTION_W - 10;
        if (result.minTemperaturePixel.y > (CAMERA_RESOLUTION_H - 10))
            result.minTemperaturePixel.y = CAMERA_RESOLUTION_H - 10;
        else if (result.minTemperaturePixel.y < 10)
            result.minTemperaturePixel.y = 10;

//        Bitmap tracker = BitmapFactory.decodeResource(context.getResources(), R.drawable.target);
//        int x1 = result.maxTemperaturePixel.x >= 10 ? (int) ((result.maxTemperaturePixel.x - 10) * sizeX / (CAMERA_RESOLUTION_W * RESIZE)) : (int) (result.maxTemperaturePixel.x * sizeX / (CAMERA_RESOLUTION_W * RESIZE));
//        int y1 = result.maxTemperaturePixel.y >= 10 ? (int) ((result.maxTemperaturePixel.y - 10) * sizeY / (CAMERA_RESOLUTION_H * RESIZE)) : (int) (result.maxTemperaturePixel.y * sizeY / (CAMERA_RESOLUTION_H * RESIZE));
//        int x2 = result.maxTemperaturePixel.x < (CAMERA_RESOLUTION_W - 10) ? (int) ((result.maxTemperaturePixel.x + 10) * sizeX / (CAMERA_RESOLUTION_W * RESIZE)) : (int) (result.maxTemperaturePixel.x * sizeX / (CAMERA_RESOLUTION_W * RESIZE));
//        int y2 = result.maxTemperaturePixel.y < (CAMERA_RESOLUTION_H - 10) ? (int) ((result.maxTemperaturePixel.y + 10) * sizeY / (CAMERA_RESOLUTION_H * RESIZE)) : (int) (result.maxTemperaturePixel.y * sizeY / (CAMERA_RESOLUTION_H * RESIZE));
//        Rect rect = new Rect(x1, y1, x2, y2);
//        canvas.drawBitmap(tracker, null, rect, null);

//        Bitmap tracker = BitmapFactory.decodeResource(context.getResources(), R.drawable.target);
//        int x1 = result.maxTemperaturePixel.x >= 10 ? (int) ((result.maxTemperaturePixel.x - 10) * sizeX / CAMERA_RESOLUTION_W) : (int) (result.maxTemperaturePixel.x * sizeX / CAMERA_RESOLUTION_W);
//        int y1 = result.maxTemperaturePixel.y >= 10 ? (int) ((result.maxTemperaturePixel.y - 10) * sizeY / CAMERA_RESOLUTION_H) : (int) (result.maxTemperaturePixel.y * sizeY / CAMERA_RESOLUTION_H);
//        int x2 = result.maxTemperaturePixel.x < (CAMERA_RESOLUTION_W - 10) ? (int) ((result.maxTemperaturePixel.x + 10) * sizeX / CAMERA_RESOLUTION_W) : (int) (result.maxTemperaturePixel.x * sizeX / CAMERA_RESOLUTION_W);
//        int y2 = result.maxTemperaturePixel.y < (CAMERA_RESOLUTION_H - 10) ? (int) ((result.maxTemperaturePixel.y + 10) * sizeY / CAMERA_RESOLUTION_H) : (int) (result.maxTemperaturePixel.y * sizeY / CAMERA_RESOLUTION_H);
//        Rect rect = new Rect(x1, y1, x2, y2);
//        canvas.drawBitmap(tracker, null, rect, null);


        Bitmap tracker = BitmapFactory.decodeResource(context.getResources(), R.drawable.target);
        int x1 = result.maxTemperaturePixel.x >= 10 ? (int) ((result.maxTemperaturePixel.x - 10) * sizeX / CAMERA_RESOLUTION_W) : (int) (result.maxTemperaturePixel.x * sizeX / CAMERA_RESOLUTION_W);
        int y1 = result.maxTemperaturePixel.y >= 10 ? (int) ((result.maxTemperaturePixel.y - 10) * sizeY / CAMERA_RESOLUTION_H) : (int) (result.maxTemperaturePixel.y * sizeY / CAMERA_RESOLUTION_H);
        int x2 = result.maxTemperaturePixel.x < (CAMERA_RESOLUTION_W - 10) ? (int) ((result.maxTemperaturePixel.x + 10) * sizeX / CAMERA_RESOLUTION_W) : (int) (result.maxTemperaturePixel.x * sizeX / CAMERA_RESOLUTION_W);
        int y2 = result.maxTemperaturePixel.y < (CAMERA_RESOLUTION_H - 10) ? (int) ((result.maxTemperaturePixel.y + 10) * sizeY / CAMERA_RESOLUTION_H) : (int) (result.maxTemperaturePixel.y * sizeY / CAMERA_RESOLUTION_H);


        double midX = (x1 + x2) / 2.0;
        double midY = (y1 + y2) / 2.0;

        double halfDx = (x2 - x1) / 2.0;
        double halfDy = (y2 - y1) / 2.0;

        double newHalfDx = halfDx / 2.0;
        double newHalfDy = halfDy / 2.0;

        int newX1 = (int) Math.round(midX - newHalfDx);
        int newY1 = (int) Math.round(midY - newHalfDy);
        int newX2 = (int) Math.round(midX + newHalfDx);
        int newY2 = (int) Math.round(midY + newHalfDy);

        Rect rect = new Rect(x1, y1, x2, y2);
        if (CAMERA_RESOLUTION_W == 384){
            rect = new Rect(newX1, newY1, newX2, newY2);
        }

        canvas.drawBitmap(tracker, null, rect, null);

    }

    private void drawTemperatures(@NonNull Bitmap bitmap, Vector<IRRegionTemp> showRegions, Vector<Float> showRegionDistances, int preset, byte imageStitch) {
//        if (showRegions != null && showRegions.isEmpty() && showRegionDistances != null && showRegionDistances.isEmpty()) return;
        if (showRegions == null || showRegions.isEmpty() || showRegionDistances == null || showRegionDistances.isEmpty()) return; /////
        // 每个预置位都绘制环境温度区域的最高温
        Canvas canvas = new Canvas(bitmap);
        if (imageStitch == 0) {
            float x = 10 * (float) bitmap.getWidth() / 640, y = 300 * (float) bitmap.getHeight() / 512;
            if (shouldShowEnvironmentTemperature() && regionEnvTemp != null && MainActivity.tempEnvRegion != Float.NEGATIVE_INFINITY) {

                String unit = (sensorConfig.measureUnit == 0) ? "°C" : "°F";
                canvas.drawText(String.format("[环境温度区域] %03.1f%s", MainActivity.tempEnvRegion,unit), x, y, tempPaint);
                y -= tempPaint.getTextSize() + 10 * (float) bitmap.getHeight() / 512;
            }
//            if (showRegions != null && showRegions.isEmpty() && showRegionDistances != null && showRegionDistances.isEmpty()) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                showRegions.sort((o1, o2) -> o1.index < o2.index ? 1 : -1);
            }
            for (int regionIndex = 0; regionIndex < showRegions.size(); regionIndex++) {
                IRRegionTemp region = showRegions.get(regionIndex);
                float regionDistance = showRegionDistances.get(regionIndex);
                String unit = (sensorConfig.measureUnit == 0) ? "°C" : "°F";
                canvas.drawText(String.format("[%d] %03.1f%s", region.index, region.getTemperature(regionDistance, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit).maxTemperature,unit), x, y, tempPaint); /////
                y -= tempPaint.getTextSize() + 10 * (float) bitmap.getHeight() / 512;
            }
        } else {
            float x = 10 * (float) bitmap.getWidth() / 640;
            if (preset == 1) {
                regionY = 225 * (float) bitmap.getHeight() / 512;
                regionGreenId = 1;
            }
            if (shouldShowEnvironmentTemperature() && regionEnvTemp != null && MainActivity.tempEnvRegion != Float.NEGATIVE_INFINITY && preset == 1) {
                String unit = (sensorConfig.measureUnit == 0) ? "°C" : "°F";

                canvas.drawText(String.format("[环境温度区域] %03.1f%s", MainActivity.tempEnvRegion,unit), x, regionY, tempPaint);
                regionY += tempPaint.getTextSize();
            }
            if (showRegions != null && showRegions.isEmpty() && showRegionDistances != null && showRegionDistances.isEmpty()) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                showRegions.sort((o1, o2) -> o1.index < o2.index ? 1 : -1);
            }
            for (int regionIndex = 0; regionIndex < showRegions.size(); regionIndex++) {
                IRRegionTemp region = showRegions.get(regionIndex);
                float regionDistance = showRegionDistances.get(regionIndex);

                String unit = (sensorConfig.measureUnit == 0) ? "°C" : "°F";
                canvas.drawText(String.format("[%d] %03.1f%s", regionGreenId, region.getTemperature(regionDistance, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit).maxTemperature,unit), x, regionY, tempPaint); /////
                regionY += tempPaint.getTextSize() + 10 * (float) bitmap.getHeight() / 512;
                regionGreenId++;
            }
        }
    }


    private void drawTempRegion(int preset, @NonNull Bitmap bitmap, IRRegionTemp.RegionType type) {
        try {
//            Log.i(Log.TAG, "高德红外开始绘制区域");
            // 每秒钟读取一次温度值
            boolean upload = (System.currentTimeMillis() - drawRegionTempTime >= PERIOD_SECOND);
            // 绘制手动下发的测温区域
            switch (type) {
                case REGION_LIVE:
                    if (liveRegionsTemp != null && liveRegionsDistance != null) {
                        for (int regionIndex = 0; regionIndex < liveRegionsTemp.size(); regionIndex++) {
                            IRRegionTemp region = liveRegionsTemp.get(regionIndex);
                            float regionDistance = liveRegionsDistance.get(regionIndex);
                            if (upload) {
                                region.getTemperature(regionDistance, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit);
                            }
                            region.drawRegionTemp(bitmap, preset, iRSetting.imageStitch, regionBlueId);
                            regionBlueId++;
                        }
                    }
                    drawTemperatures(bitmap, liveRegionsTemp, liveRegionsDistance, preset, iRSetting.imageStitch);
                    if (shouldShowEnvironmentTemperature() && regionEnvTemp != null) {
                        // 只绘制设置了环境温度区域的预置位
                        if (preset == presetEnvTemp) {
                            regionEnvTemp.drawRegionTemp(bitmap, preset, iRSetting.imageStitch, regionBlueId);  // 绘制环境温度区域
                        }
                    }
                    break;
                case REGION_STATIC:  //静态的
                    //Log.i(Log.TAG, "高德红外开始绘制区域");
                    Vector<IRRegionTemp> staticRegions = staticRegionsMaps.get(preset);
                    Vector<Float> staticRegionsDistance = staticRegionsDistanceMaps.get(preset);

                    if (staticRegions != null && staticRegionsDistance != null) {
                        for (int regionIndex = 0; regionIndex < staticRegions.size(); regionIndex++) {
                            IRRegionTemp region = staticRegions.get(regionIndex);
                            float regionDistance = staticRegionsDistance.get(regionIndex);

                            if (upload) {
                                region.getTemperature(regionDistance, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit); /////
                            }

                            region.drawRegionTemp(bitmap, preset, iRSetting.imageStitch, regionBlueId);
                            regionBlueId++;
                        }
                    } else {
                        //Log.i(Log.TAG, "高德红外静态测温区域为空");
                    }
                    drawTemperatures(bitmap, staticRegions, staticRegionsDistance, preset, iRSetting.imageStitch);
                    if (shouldShowEnvironmentTemperature() && regionEnvTemp != null) {
                        // 只绘制设置了环境温度区域的预置位
                        if (preset == presetEnvTemp) {
                            regionEnvTemp.drawRegionTemp(bitmap, preset, iRSetting.imageStitch, regionBlueId);  // 绘制环境温度区域
                        }
                    }
                    break;
            }

            if (upload) drawRegionTempTime = System.currentTimeMillis();
        } catch (Exception e) {
            Log.i(Log.TAG, "高德红外绘制区域异常：" + e);
        }
    }



    //读取图片的分辨率
    private Bitmap captureBitmap(int preset, int recordPreset) throws Exception {
        startPhoto = false;
        photoFrame.isReady = false;
        setState(DevState.PHOTOING);

        if (!isLiving() && !isRecording()) {
            startPreview(-1, preset);
        }

//        else{
//            // 拉流的时候拍照，框内的温度为0，是因为没有了测温线程导致的
//            startMeasureTimer(preset);
//        }

        startPhotoTime = System.currentTimeMillis();

        /////   TODO 拍照等待20s，优化
//        if ((preset != 0 && preset != recordPreset) || (preset != 0 && preset == recordPreset && isLiving())) {
//            move(2, preset);
//            SystemClock.sleep(PTZ_PRESET_MOVE_TIME);    // 等待到达预置位
//        } else if (preset != 0) {
//            move(2, preset);
//        }


        // 这个地方需要修改检查预置位是否到达
        if (preset != 0) {
            move(2, preset);
            SystemClock.sleep(PTZ_PRESET_MOVE_TIME);    // 等待到达预置位   20S
        }


        /////
        // 如果有环境温度区域，就需要先等待环境温度的获取
        if (regionEnvTemp != null) {
            SystemClock.sleep(5 * PERIOD_SECOND);
        } else {
            if (preset == 0) {
                SystemClock.sleep(5 * PERIOD_SECOND);
            }
        }


        // 转动到预置位再拍照
        startPhoto = true;

//        Bitmap bitmap = null;
//        synchronized (photoFrame.data) {
//            photoFrame.data.wait(10 * PERIOD_SECOND);          //TODO  The infrared camera fails to produce images, most likely because the infrared image is not ready
//            if (photoFrame.isReady) {
//                Log.i(Log.TAG, "高德红外拍照图像帧已准备好");
//                if (!photoFrame.isBlack()) {
//                    Log.i(Log.TAG, "高德红外拍照图像帧不为黑");
//                    bitmap = Bitmap.createBitmap(CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, Bitmap.Config.ARGB_8888);
//                    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(photoFrame.getData()));
//                    bitmap = Bitmap.createScaledBitmap(bitmap,
//                            //CAMERA_RESOLUTION_W * RESIZE,
//                            //CAMERA_RESOLUTION_H * RESIZE,
//                            (int) sizeX,
//                            (int) sizeY,
//                            true);
//                } else {
//                    Log.i(Log.TAG, "高德红外拍照图像帧为黑");
//                }
//            } else {
//                Log.i(Log.TAG, "高德红外拍照图像帧没准备好");
//            }
//        }

        Bitmap bitmap = null;
        synchronized (photoFrame.data) {
            long timeoutMillis = 100 * 1000;    // 测试高温的时候，设置为60s，会经常出现 [] 的情况
            // 这个等待时间是防止拍照过程中出现：高德红外拍照失败，未捕获到照片数据, 设备状态：[]
            // 正常情况下，摄像头转到位置后，图片就准备好了

            long startTime = System.currentTimeMillis();
            long remainingTime = timeoutMillis;

            while (!photoFrame.isReady && remainingTime > 0) {
                try {
                    photoFrame.data.wait(remainingTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(Log.TAG, "等待红外图像被中断"+e);
                    break;
                }

                long elapsedTime = System.currentTimeMillis() - startTime;
                remainingTime = timeoutMillis - elapsedTime;
            }

            if (photoFrame.isReady) {
                Log.i(Log.TAG, "高德红外拍照图像帧已准备好");
                if (!photoFrame.isBlack()) {
                    Log.i(Log.TAG, "高德红外拍照图像帧不为黑");
                    bitmap = Bitmap.createBitmap(CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(photoFrame.getData()));
                    bitmap = Bitmap.createScaledBitmap(bitmap,
                            (int) sizeX,
                            (int) sizeY,
                            true);
                } else {

                    Log.i(Log.TAG, "高德红外拍照图像帧为黑");
                }
            } else {
                Log.w(Log.TAG, "高德红外拍照图像帧等待超时（100秒）");
            }
        }


        if (!isLiving() && !isRecording()) {
            stopPreview();
            stopTemp();
        }
        clearState(DevState.PHOTOING);
        return bitmap;
    }

    @Override
    public boolean mergeBitmapVertical(int preset, String filename, boolean show, Bitmap... bitmaps) {
        try {
            if (bitmaps == null || bitmaps.length == 0) {
                return false;
            }

            int height = bitmaps[0].getHeight();
            int totalWidth = 0;

            // 计算总高度
            for (Bitmap bitmap : bitmaps) {
                if (bitmap == null) return false;  // 确保所有Bitmap都有效
                totalWidth += bitmap.getWidth();
            }

            // 创建拼接后的Bitmap
            Bitmap stitchBitmap = Bitmap.createBitmap(totalWidth, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(stitchBitmap);

            int currentWidth = 0;
            for (Bitmap bitmap : bitmaps) {
                canvas.drawBitmap(bitmap, currentWidth, 0f, null);
                currentWidth += bitmap.getWidth();
            }
            drawWatermark(stitchBitmap);
            // 绘制热点追踪
//            if (sensorConfig.hotTracker == 1) {
//                Bitmap tracker = BitmapFactory.decodeResource(context.getResources(), R.drawable.target);
//                int x1 = maxTemperaturePixelX >= 17.5 ? (int) ((maxTemperaturePixelX - 17.5) * (float) totalWidth / (CAMERA_RESOLUTION_W * RESIZE * 3)) : (int) (maxTemperaturePixelX * (float) totalWidth / (CAMERA_RESOLUTION_W * RESIZE * 3));  // 微调：17.5
//                int y1 = maxTemperaturePixelY >= 10 ? (int) ((maxTemperaturePixelY - 10) * (float) sizeY / (CAMERA_RESOLUTION_H * RESIZE)) : (int) (maxTemperaturePixelY * (float) sizeY / (CAMERA_RESOLUTION_H * RESIZE));
//                int x2 = maxTemperaturePixelX < (CAMERA_RESOLUTION_W - 17.5) ? (int) ((maxTemperaturePixelX + 17.5) * (float) totalWidth / (CAMERA_RESOLUTION_W * RESIZE * 3)) : (int) (maxTemperaturePixelX * (float) totalWidth / (CAMERA_RESOLUTION_W * RESIZE * 3));  // 微调：17.5
//                int y2 = maxTemperaturePixelY < (CAMERA_RESOLUTION_H - 10) ? (int) ((maxTemperaturePixelY + 10) * (float) sizeY / (CAMERA_RESOLUTION_H * RESIZE)) : (int) (maxTemperaturePixelY * (float) sizeY / (CAMERA_RESOLUTION_H * RESIZE));
//                Rect rect = new Rect(x1, y1, x2, y2);
//                canvas.drawBitmap(tracker, null, rect, null);
//            }

//            if (sensorConfig.hotTracker == 1) {
//                Bitmap tracker = BitmapFactory.decodeResource(context.getResources(), R.drawable.target);
//                int x1 = maxTemperaturePixelX >= 17.5 ? (int) ((maxTemperaturePixelX - 17.5) * (float) totalWidth / (CAMERA_RESOLUTION_W * 3)) : (int) (maxTemperaturePixelX * (float) totalWidth / (CAMERA_RESOLUTION_W * 3));  // 微调：17.5
//                int y1 = maxTemperaturePixelY >= 10 ? (int) ((maxTemperaturePixelY - 10) * (float) sizeY / CAMERA_RESOLUTION_H) : (int) (maxTemperaturePixelY * (float) sizeY / CAMERA_RESOLUTION_H);
//                int x2 = maxTemperaturePixelX < (CAMERA_RESOLUTION_W - 17.5) ? (int) ((maxTemperaturePixelX + 17.5) * (float) totalWidth / (CAMERA_RESOLUTION_W * 3)) : (int) (maxTemperaturePixelX * (float) totalWidth / (CAMERA_RESOLUTION_W * 3));  // 微调：17.5
//                int y2 = maxTemperaturePixelY < (CAMERA_RESOLUTION_H - 10) ? (int) ((maxTemperaturePixelY + 10) * (float) sizeY / CAMERA_RESOLUTION_H) : (int) (maxTemperaturePixelY * (float) sizeY / CAMERA_RESOLUTION_H);
//                Rect rect = new Rect(x1, y1, x2, y2);
//                canvas.drawBitmap(tracker, null, rect, null);
//            }
            if (sensorConfig.hotTracker == 1) {
                Bitmap tracker = BitmapFactory.decodeResource(context.getResources(), R.drawable.target);
                int x1 = maxTemperaturePixelX >= 17.5 ? (int) ((maxTemperaturePixelX - 17.5) * (float) totalWidth / (CAMERA_RESOLUTION_W * 3)) : (int) (maxTemperaturePixelX * (float) totalWidth / (CAMERA_RESOLUTION_W * 3));  // 微调：17.5
                int y1 = maxTemperaturePixelY >= 10 ? (int) ((maxTemperaturePixelY - 10) * (float) sizeY / CAMERA_RESOLUTION_H) : (int) (maxTemperaturePixelY * (float) sizeY / CAMERA_RESOLUTION_H);
                int x2 = maxTemperaturePixelX < (CAMERA_RESOLUTION_W - 17.5) ? (int) ((maxTemperaturePixelX + 17.5) * (float) totalWidth / (CAMERA_RESOLUTION_W * 3)) : (int) (maxTemperaturePixelX * (float) totalWidth / (CAMERA_RESOLUTION_W * 3));  // 微调：17.5
                int y2 = maxTemperaturePixelY < (CAMERA_RESOLUTION_H - 10) ? (int) ((maxTemperaturePixelY + 10) * (float) sizeY / CAMERA_RESOLUTION_H) : (int) (maxTemperaturePixelY * (float) sizeY / CAMERA_RESOLUTION_H);

                double midX = (x1 + x2) / 2.0;
                double midY = (y1 + y2) / 2.0;

                double halfDx = (x2 - x1) / 2.0;
                double halfDy = (y2 - y1) / 2.0;

                double newHalfDx = halfDx / 2.0;
                double newHalfDy = halfDy / 2.0;

                int newX1 = (int) Math.round(midX - newHalfDx);
                int newY1 = (int) Math.round(midY - newHalfDy);
                int newX2 = (int) Math.round(midX + newHalfDx);
                int newY2 = (int) Math.round(midY + newHalfDy);

                Rect rect = new Rect(x1, y1, x2, y2);
                if (CAMERA_RESOLUTION_W == 384){
                    rect = new Rect(newX1, newY1, newX2, newY2);
                }
                canvas.drawBitmap(tracker, null, rect, null);
            }


            if (irOverProtect.getMode() == Protection) {
                stitchBitmap = drawProtection(stitchBitmap);
            }
            regionBlueId = 1;
            int currentPreset = 1;
            for (int i = 0; i < bitmaps.length; i++) {
                currentPreset = (i == 0) ? preset : (i + 1);  // 0号位使用传入的preset，其余自动递增
                drawTempRegion(currentPreset, stitchBitmap, IRRegionTemp.RegionType.REGION_STATIC);
            }
            // 绘制调色板
            if (sensorConfig.onPalette == 1) {
                Rect dest = new Rect(totalWidth - 16 * totalWidth / (640 * 3), 40 * height / 512, totalWidth, height - 40 * height / 512);
                canvas.drawBitmap(osdPalette, null, dest, null);
                Paint paint = new Paint();
                paint.setStrokeWidth(1 * (float) totalWidth / 640);
                paint.setTextSize((float) (16 * (float) totalWidth / (640 * 1.5)));  // 微调：640 * 1.5
                paint.setAntiAlias(true);
                paint.setColor(Color.GREEN);

//                // 使用图像拼接后的全局最高温
//                String unit = (sensorConfig.measureUnit == 0) ? "°C" : "°F";
//                String globalMaxTempText = (Math.abs(globalMaxTemp) < 1000) ? String.format("%.1f", globalMaxTemp) : String.format("%d", Math.round(globalMaxTemp));
//                canvas.drawText(globalMaxTempText, (float) (totalWidth - 45 * (float) totalWidth / (640 * 1.5)), 60 * (float) height / 512, paint);  // 微调：640 * 1.5
//                // 使用图像拼接后的全局最低温
//                String globalMinTempText = (Math.abs(globalMinTemp) < 1000) ? String.format("%.1f", globalMinTemp) : String.format("%d", Math.round(globalMinTemp));
//                // 温度单位显示

                String unit = (sensorConfig.measureUnit == 0) ? "°C" : "°F";
                String globalMaxTempText = (Math.abs(globalMaxTemp) < 1000) ? String.format("%.1f", globalMaxTemp) : String.format("%d", Math.round(globalMaxTemp));
                globalMaxTempText = globalMaxTempText + unit; // 拼接单位

                canvas.drawText(globalMaxTempText, (float) (totalWidth - 45 * (float) totalWidth / (640 * 1.5)), 60 * (float) height / 512, paint);

                String globalMinTempText = (Math.abs(globalMinTemp) < 1000) ? String.format("%.1f", globalMinTemp) : String.format("%d", Math.round(globalMinTemp));
                globalMinTempText = globalMinTempText + unit;


                canvas.drawText(globalMinTempText, (float) (totalWidth - 45 * (float) totalWidth / (640 * 1.5)), height - 45 * (float) height / 512, paint);  // 微调：640 * 1.5
            }
            long stamp = getTimestampFromFilename(filename);
            // 回传温度 高低温预警
            Vector<IRRegionTemp> mergedStaticRegions = new Vector<>();
            Vector<Float> mergedStaticRegionsDistance = new Vector<>();
            HashMap<Integer, Vector<IRRegionTemp>> presetStaticRegionsMaps = new HashMap<>();  // 用于存储拼接图像测温区域中的区域信息
            for (int i = 0; i < bitmaps.length; i++) {
                currentPreset = (i == 0) ? preset : (i + 1);  // 0号位使用传入的preset，其余自动递增
                // 获取各图的区域数据
                Vector<IRRegionTemp> staticRegions = staticRegionsMaps.get(currentPreset);
                Vector<Float> staticRegionsDistance = staticRegionsDistanceMaps.get(currentPreset);
                // 合并区域
                if (staticRegions != null) {
                    mergedStaticRegions.addAll(staticRegions);
                }
                // 合并距离
                if (staticRegionsDistance != null) {
                    mergedStaticRegionsDistance.addAll(staticRegionsDistance);
                }
            }
            // 保存合并后的区域
            presetStaticRegionsMaps.put(1, mergedStaticRegions);
            // 创建新的presetStaticRegionsMaps并保存
            HashMap<Integer, Vector<Float>> presetStaticRegionsDistanceMaps = new HashMap<>();  // 用于存储拼接图像测温区域中的距离信息
            presetStaticRegionsDistanceMaps.put(1, mergedStaticRegionsDistance);
            doTempCallback(preset, stitchBitmap, stamp, presetStaticRegionsMaps, presetStaticRegionsDistanceMaps);

            // 设置饱和度
            // 创建一个空白的Bitmap，大小与原始图像相同
            Bitmap saturationBitmap = Bitmap.createBitmap(stitchBitmap.getWidth(), stitchBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            // 创建Canvas对象
            Canvas saturationCanvas = new Canvas(saturationBitmap);
            // 创建ColorMatrix对象
            ColorMatrix colorMatrix = new ColorMatrix();
            // 设置饱和度
            colorMatrix.setSaturation(photoConfig.saturation / 50);
            // 创建Paint对象
            Paint paint = new Paint();
            // 将ColorMatrixColorFilter应用到Paint对象
            paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            // 使用Paint对象绘制图像
            saturationCanvas.drawBitmap(stitchBitmap, 0, 0, paint);
            Log.i(Log.TAG, "高德红外饱和度设置为" + photoConfig.saturation);
            if (show) controllerCallback.onFrame(saturationBitmap);

            saveBitmapAsJPEG(saturationBitmap, filename, 100);
            stitchBitmap.recycle();  // 释放原始Bitmap内存
            saturationBitmap.recycle();  // 释放saturationBitmap内存

            controllerCallback.onPhotoTaked(stamp, this.id, preset, filename);
            return true;
        } catch (Exception e) {
            Log.i(Log.TAG, "高德红外图像拼接异常：" + e);
        }
        return false;
    }

    @Override
    public Bitmap takePhoto(int stream, int preset, String filename, boolean show, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert, byte imageStitch) { /////
        Bitmap bitmap;
        enter(130);
        try {
            if (openCamera() != true) {
                return null;
            }
            bitmap = captureBitmap(preset, recordPreset);
            if (preset == 1) {
                maxTemperature = Float.NEGATIVE_INFINITY;
                globalMaxTemp = Float.NEGATIVE_INFINITY;
                globalMinTemp = Float.POSITIVE_INFINITY;
            }
            if (sensorConfig.hotTracker == 1) {
                // 存储每个预置位的最高温位置
                int width_preset = CAMERA_RESOLUTION_W;
                int x_offset = (preset == 1) ? width_preset : (preset == 3) ? (2 * width_preset) : 0;
                IRRegionTemp.TemperatureSampleResult result = globalTemp.getTemperature(sensorConfig.distance, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit);  // 使用全局测温距离校正 /////
                if (result.maxTemperaturePixel.x > (CAMERA_RESOLUTION_W - 10))
                    result.maxTemperaturePixel.x = CAMERA_RESOLUTION_W - 10;
                if (result.maxTemperaturePixel.y > (CAMERA_RESOLUTION_H - 10))
                    result.maxTemperaturePixel.y = CAMERA_RESOLUTION_H - 10;
                else if (result.maxTemperaturePixel.y < 10) {
                    result.maxTemperaturePixel.y = 10;
                }
                if (result.minTemperaturePixel.x > (CAMERA_RESOLUTION_W - 10))
                    result.minTemperaturePixel.x = CAMERA_RESOLUTION_W - 10;
                if (result.minTemperaturePixel.y > (CAMERA_RESOLUTION_H - 10))
                    result.minTemperaturePixel.y = CAMERA_RESOLUTION_H - 10;
                else if (result.minTemperaturePixel.y < 10)
                    result.minTemperaturePixel.y = 10;
                if (result.maxTemperature > maxTemperature) {
                    maxTemperature = result.maxTemperature;
                    minTemperature = result.minTemperature;
                    maxTemperaturePixelX = result.maxTemperaturePixel.x + x_offset;
                    maxTemperaturePixelY = result.maxTemperaturePixel.y;
                    minTemperaturePixelX = result.minTemperaturePixel.x + x_offset;
                    minTemperaturePixelY = result.minTemperaturePixel.y;
                }
            }
            if (sensorConfig.onPalette == 1) {
                Vector<IRRegionTemp> staticRegions = staticRegionsMaps.get(preset);
                Vector<Float> staticRegionsDistance = staticRegionsDistanceMaps.get(preset);
                IRRegionTemp.TemperatureSampleResult result = globalTemp.getTemperature(sensorConfig.distance, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit);  // 使用全局测温距离校正 /////
                // 比较全局最高温与区域最高温
                float maxTemp = result.maxTemperature;
                float minTemp = result.minTemperature;
                globalMaxTemp = Math.max(globalMaxTemp, maxTemp);
                globalMinTemp = Math.min(globalMinTemp, minTemp);
                if (staticRegions != null && staticRegionsDistance != null) {
                    for (int regionIndex = 0; regionIndex < staticRegions.size(); regionIndex++) {
                        IRRegionTemp region = staticRegions.get(regionIndex);
                        float regionDistance = staticRegionsDistance.get(regionIndex);
                        IRRegionTemp.TemperatureSampleResult regionResult = region.getTemperature(regionDistance, sensorConfig.tempCompensate, iRSetting.focalLen, sensorConfig.measureUnit);  // 使用区域测温距离校正 /////
                        globalMaxTemp = Math.max(globalMaxTemp, regionResult.maxTemperature);
                        globalMinTemp = Math.min(globalMinTemp, regionResult.minTemperature);
                    }
                }
            }
            if (bitmap == null) {
                Log.i(Log.TAG, "高德红外拍照失败，未捕获到照片数据, 设备状态：" + Status());
            }
            if (!isLiving() && !isRecording()) {
                close();
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "高德红外拍照异常：" + e);
            return null;
        } finally {
            exit();
        }
        return bitmap;
    }



    @Override  // 抓拍
    public boolean takePhoto(int stream, int preset, boolean show, String filename, Bitmap pop, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert) {
        try {
            enter(130);
            Log.i(Log.TAG, "高德红外拍照开始：" + filename);

            Bitmap bitmap = captureBitmap(preset, recordPreset);


            if (!isLiving() && !isRecording()) {
                close();
            }

            if (bitmap == null) {
                Log.i(Log.TAG, "高德红外拍照失败，未捕获到照片数据, 设备状态：" + Status());
            } else {

                drawWatermark(bitmap);
                if (sensorConfig.hotTracker == 1) {
                    drawHotTracker(bitmap);
                }
                if (irOverProtect.getMode() == Protection) {
                    bitmap = drawProtection(bitmap);
                }


                drawTempRegion(preset, bitmap, IRRegionTemp.RegionType.REGION_STATIC);

                // 区域温度校正后再绘制调色板
                Vector<IRRegionTemp> staticRegions = staticRegionsMaps.get(preset);
                Vector<Float> staticRegionsDistance = staticRegionsDistanceMaps.get(preset);
                if (sensorConfig.onPalette == 1) {
                    drawPalette(bitmap, staticRegions, staticRegionsDistance);
                }

                long stamp = getTimestampFromFilename(filename);
                // 回传温度 高低温预警
                doTempCallback(preset, bitmap, stamp, staticRegionsMaps, staticRegionsDistanceMaps);

                // 设置饱和度
                // 创建一个空白的Bitmap，大小与原始图像相同
                Bitmap saturationBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                // 创建Canvas对象
                Canvas canvas = new Canvas(saturationBitmap);
                // 创建ColorMatrix对象
                ColorMatrix colorMatrix = new ColorMatrix();
                // 设置饱和度
                colorMatrix.setSaturation(photoConfig.saturation / 50);
                // 创建Paint对象
                Paint paint = new Paint();
                // 将ColorMatrixColorFilter应用到Paint对象
                paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
                // 使用Paint对象绘制图像
                canvas.drawBitmap(bitmap, 0, 0, paint);


                Log.i(Log.TAG, "高德红外饱和度设置为" + photoConfig.saturation);
                if (show) controllerCallback.onFrame(saturationBitmap);

                saveBitmapAsJPEG(saturationBitmap, filename, 100);
                bitmap.recycle();  // 释放原始Bitmap内存
                saturationBitmap.recycle();  // 释放saturationBitmap内存

                controllerCallback.onPhotoTaked(stamp, this.id, preset, filename);
                return true;
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "高德红外拍照异常：" + e);
        } finally {
            exit();
        }
        return false;
    }

    @Override
    public boolean takeVideo(final String filename, final int duration, int stream, boolean upload) {
        if (isRecording()) {
            Log.i(Log.TAG, "高德红外录制短视频失败，正在录制中");
            return false;
        }
        videoStart(stream, filename, duration, upload);
        return true;
    }

    String tmpRecordFile = MainActivity.DATA_DIR + "record_" + id + ".mp4";
    @Override
    public boolean videoStart(int stream, String filename, int duration, boolean upload) {
        try {
            /////
            // 直播中启动短视频录制时，复用当前预览线程和编码器，只额外打开Muxer写录像文件。
            boolean reuseLiveEncoder = isLiving() && mediaCodec != null;
            int encoderStream = reuseLiveEncoder ? streamType : stream;
            Settings.VideoCodec vCodec = getVideoCodec(encoderStream);
            //initEncoder(stream, CAMERA_RESOLUTION_W * RESIZE, CAMERA_RESOLUTION_H * RESIZE);
            Point size = Settings.VideoCodec.getResolution(vCodec.resolution);
            if (codec.get(String.valueOf(encoderStream)).resolution > 11) {
                sizeX = 1600;
                sizeY = 1200;
            } else {
                sizeX = size.x;
                sizeY = size.y;
            }
            /////
            super.videoStart(encoderStream, filename, duration, upload);
            mediaMuxer = new MediaMuxer(tmpRecordFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            muxerStarted = false;
            videoTrackIndex = -1;
            if (useAudio) {
                audioTrackIndex = -1;
                avStartNs = System.nanoTime();
                // 单调递增保护变量
                lastVideoPtsUs = 0;
                lastAudioPtsUs = 0;
                // 音频按采样累计
                initAudioRecord();
                initAudioEncoder();
                startAudio();
            }
            if (!reuseLiveEncoder) {
                initVideoEncoder(encoderStream, (int) sizeX, (int) sizeY,false); /////
            } else {
                Log.i(Log.TAG, "高德红外直播中启动录制，复用当前预览和视频编码器");
            }
            /////
            if (!reuseLiveEncoder) {
                startPreview(encoderStream, 0);
                //startTemp(0);

                new Thread(() -> {
                    // 同一条预览线程同时服务直播和录像，任一业务还在运行都不能退出取帧循环。
                    while (isRecording() || isLiving()) {
                        try {
                            synchronized (videoFrame.data) {  // 线程同步，消费者
                                videoFrame.data.wait(10);
                                if (!videoFrame.isReady) {
                                    continue;
                                } else {
                                    sourceBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(videoFrame.data));
                                    previewBitmap = Bitmap.createScaledBitmap(sourceBitmap,
                                            (int) sizeX, /////
                                            (int) sizeY, /////
                                            true);
                                    videoFrame.isReady = false;
                                }
                            }
                            drawWatermark(previewBitmap);
                            if (isLiving()) {
                                drawTempRegion(0, previewBitmap, IRRegionTemp.RegionType.REGION_LIVE);
                            }
                            // 区域温度校正后再绘制调色板
                            Vector<IRRegionTemp> staticRegions = staticRegionsMaps.get(0);
                            Vector<Float> staticRegionsDistance = staticRegionsDistanceMaps.get(0);
                            if (sensorConfig.onPalette == 1) {
                                drawPalette(previewBitmap, staticRegions, staticRegionsDistance);
                            }
                            if (sensorConfig.hotTracker == 1) {
                                drawHotTracker(previewBitmap);
                            }
                            if (irOverProtect.getMode() == Protection) {
                                drawProtection(previewBitmap);
                            }
                            encode(previewBitmap);
                        } catch (Exception e) {
                            Log.i(Log.TAG, "高德红外录像预览异常：" + e);
                        }
                    }
                }).start();
            }

            new Timer("recordStop").schedule(new TimerTask() {
                @Override
                public void run() {
                    procVideoHandler.removeCallbacksAndMessages(null); /////
                    if (useAudio) {
                        procAudioHandler.removeCallbacksAndMessages(null); /////
                    }
                    videoStop();
                    if (upload) {
                        Utils.su("mv " + tmpRecordFile + " " + filename);
                    } else {
                        File file = new File(tmpRecordFile);
                        File finalFile = new File(filename);
                        file.renameTo(new File(MainActivity.FILE_PATH + id + File.separator + finalFile.getName()));
                        Log.i(Log.TAG, "高德红外文件不上传，修改文件为：" + (MainActivity.FILE_PATH + id + File.separator + finalFile.getName()));
                    }
                    controllerCallback.onVideoFinished(System.currentTimeMillis(), id, streamType, filename, upload);
                }
            }, (duration + 1) * 1000);  // 多1秒作为保险余地，不然可能录像时间不足
        } catch (Exception e) {
            videoStop();
            controllerCallback.onVideoFailed(id, filename);
            Log.e(Log.TAG, "高德红外录制视频异常：" + e.getMessage());
        }
        return isRecording();
    }

    @Override
    public boolean videoStop() {
        try {
            if (!isLiving()) {
                stopTemp();
                stopPreview();
                close();
            }
            super.videoStop();

            releaseMuxer();
            /////
            if (!isLiving()) {
                uninitVideoEncoder();
            } else {
                // 录制结束但直播仍在运行时，编码器继续给直播RTP使用，不能释放。
                Log.i(Log.TAG, "高德红外录制停止，直播仍在进行，保留视频编码器");
            }
            if (useAudio) {
                uninitAudioEncoder();
            }
            /////
            Log.i(Log.TAG, "高德红外机芯停止录制");
        } catch (Exception e) {
            Log.i(Log.TAG, "高德红外机芯停止录制异常：" + e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    public boolean liveStart(int stream, int ssrc) {
        if (isLiving()) return true;

        if (isRecording()) {
            // 录制中启动直播时，复用录制线程输出的编码帧，只创建直播侧RTP封包器。
            rtph264 = new RTPH264(ssrc);
            resetLiveRtpState();
            setState(DevState.LIVING);
            this.ssrcLive = ssrc;
            this.streamType = stream;
            liveRegionsTemp.clear();
            liveRegionsDistance.clear();
            Log.i(Log.TAG, "高德红外录制中启动直播，复用当前预览和视频编码器");
            return true;
        }

        setState(DevState.LIVING);
        getVideoCodec(streamType);
        //initEncoder(stream, CAMERA_RESOLUTION_W * RESIZE, CAMERA_RESOLUTION_H * RESIZE);
        Point size = Settings.VideoCodec.getResolution(codec.get(String.valueOf(stream)).resolution);
        /////
        if (codec.get(String.valueOf(stream)).resolution > 11) {
            sizeX = 1600;
            sizeY = 1200;
        } else {
            sizeX = size.x;
            sizeY = size.y;
        }
        initVideoEncoder(stream, (int) sizeX, (int) sizeY,false); /////
        /////
        rtph264 = new RTPH264(ssrc);            // 创建RTP H264编码器
        resetLiveRtpState();
        startPreview(stream, 0);
        //startTemp(0);

        this.ssrcLive = ssrc;
        this.streamType = stream;
        liveRegionsTemp.clear();
        liveRegionsDistance.clear();

        new Thread(() -> {
            long curTime = 0;
            //int frameCnt = 0;
            // 同一条预览线程同时服务直播和录像，任一业务还在运行都不能退出取帧循环。
            while (isLiving() || isRecording()) {
                try {
                    synchronized (videoFrame.data) {   // 线程同步，消费者
                        videoFrame.data.wait(10);
                        if (!videoFrame.isReady) {
                            continue;
                        } else {
                            sourceBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(videoFrame.getData()));
                            previewBitmap = Bitmap.createScaledBitmap(sourceBitmap,
                                    (int) sizeX, /////
                                    (int) sizeY, /////
                                    true);
                            videoFrame.isReady = false;
                        }
                    }

                    drawWatermark(previewBitmap);

                    if (isLiving()) {
                        drawTempRegion(0, previewBitmap, IRRegionTemp.RegionType.REGION_LIVE);
                    }

                    // 区域温度校正后再绘制调色板
                    Vector<IRRegionTemp> staticRegions = staticRegionsMaps.get(0);
                    Vector<Float> staticRegionsDistance = staticRegionsDistanceMaps.get(0);
                    if (sensorConfig.onPalette == 1) {
                        drawPalette(previewBitmap, staticRegions, staticRegionsDistance);
                    }

                    if (sensorConfig.hotTracker == 1) {
                        drawHotTracker(previewBitmap);
                    }

                    if (irOverProtect.getMode() == Protection) {
                        drawProtection(previewBitmap);
                    }

                    //controllerCallback.onFrame(previewBitmap);  // 这个地方的属性的rgb图片
                    encode(previewBitmap);

                    /*frameCnt++;
                    if (System.currentTimeMillis() - curTime >= PERIOD_SECOND) {
                        Log.i(Log.TAG, "视频发送帧率：" + frameCnt * PERIOD_SECOND / (System.currentTimeMillis() - curTime));
                        curTime = System.currentTimeMillis();
                        frameCnt = 0;
                    }*/
                    if (isLiving() && liveRegionsTemp.size() > 0 && liveRegionsDistance.size() > 0 && (System.currentTimeMillis() - curTime >= PERIOD_MINUTE)) {
                        // 1分钟上报一次温度数据
                        doTempReport(0, System.currentTimeMillis(), liveRegionsTemp, liveRegionsDistance);
                        curTime = System.currentTimeMillis();
                    }
                } catch (Exception e) {
                    Log.i(Log.TAG, "高德红外直播预览异常：" + e);
                }
            }
        }).start();

        return true;
    }

    @Override
    public boolean liveStop() {
        try {
            if (!isRecording()) {
                stopTemp();
                stopPreview();
                close();
            }

            // 只停止直播侧RTP封包；如果录像仍在运行，预览和编码器继续保留给录像使用。
            rtph264 = null;
        } catch (Exception e) {
            Log.i(Log.TAG, "停止高德红外直播异常：" + e);
        } finally {
            clearState(DevState.LIVING);
        }
        Log.i(Log.TAG, "停止高德红外直播成功");

        return true;
    }

    @Override
    public Settings.VideoCodec getVideoCodec(int streamType) {
        return codec.get(String.valueOf(streamType));
    }

    @Override
    protected boolean reboot() {
        Log.i(Log.TAG, "重启高德红外机芯");
        close();
        SystemClock.sleep(200);

        //init(CAMERA_RESOLUTION_WIDTH, CAMERA_RESOLUTION_HEIGHT, context);
        // reboot函数没有用到，所以没写open()

        return true;
    }

    @Override
    public boolean playbackStop() {
        return true;
    }

    /////
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
    /////
    @Override
    public boolean setOSD(Settings.OSD osd, boolean osdNull) { /////
        /////
        if (iRSetting.imageFusion == 1) {
            this.osd.size = osd.size;
            this.osd.tag = osd.tag;
            this.osd.time = osd.time;
            if (osd.text != null && osd.text.startsWith("双光融合")) {
                Log.i(Log.TAG, "设置双光融合参数");
                try {
                    // 提取参数部分并分割
                    String paramsPart = osd.text.substring("双光融合".length()).trim();
                    String[] paramStrings = paramsPart.split(" ");
                    iRSetting.angle = Float.parseFloat(paramStrings[0]);
                    iRSetting.horDisplacement = Integer.parseInt(paramStrings[1]);
                    iRSetting.verDisplacement = Integer.parseInt(paramStrings[2]);
                    saveSettings(iRSetting, MainActivity.IR_SETTING_FILE);
                } catch (NumberFormatException e) {
                }
            } else {
                this.osd.text = osd.text;
            }
        } else {
            this.osd = osd;
        }
        /////
        return true;
    }

    @Override
    public boolean setCodec(Settings.VideoCodec codec) {
        this.codec.put(String.valueOf(codec.streamType), codec);
        return true;
    }

    @Override
    public boolean videoPause() {
        return true;
    }

    @Override
    public boolean videoResume() {
        return true;
    }

    @Override
    public boolean setTime(int dwYear, int dwMonth, int dwDay, int dwHour, int dwMinute, int dwSecond) {
        return true;
    }

    // 通用映射函数：从 1~100 映射到 0~targetMax（返回 int）
    public static int mapFrom1To100(int x, int targetMax) {
        // 边界限制
        if (x < 0) x = 0;
        if (x > 100) x = 100;
        // 线性映射并四舍五入
        return Math.round(x * targetMax / 100.0f);
    }
    @Override
    public boolean setPhotoParam(Settings.PhotoConfig config) {
        super.setPhotoParam(config);
        // 获取图像分辨率参数
        Point size = Settings.PhotoConfig.getImageSize(config.size);
        sizeX = size.x;
        sizeY = size.y;
        Log.i(Log.TAG, "高德红外图像分辨率设置为" + (int) sizeX + "x" + (int) sizeY);
        tempPaint = new Paint();
        tempPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        tempPaint.setStyle(Paint.Style.FILL);
        timePaint = new Paint();
        if (iRSetting.imageStitch == 0) {
            tempPaint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));
            timePaint.setStrokeWidth(1 * sizeX / 640);
            timePaint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));
        } else {
            tempPaint.setTextSize(16 * Math.min(sizeX * 3 / 640, sizeY / 512));
            timePaint.setStrokeWidth(1 * sizeX * 3 / 640);
            timePaint.setTextSize(16 * Math.min(sizeX * 3 / 640, sizeY / 512));
        }
        tempPaint.setAntiAlias(false);
        tempPaint.setColor(0xFF00AB00);
        timePaint.setStyle(Paint.Style.FILL);
        timePaint.setAntiAlias(true);
        timePaint.setColor(Color.WHITE);
        if (mGuideInterface != null) {
            if (config.color == 0) {  // 色彩切换为黑白模式
                if (sensorConfig.color == 0) {
                    boolean ret = mGuideInterface.changePalette(0);  // 使用白热伪彩
                    mDeviceConfig.setPaletteIndex(0);
                    if (ret) {
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot);
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式成功");
                    } else {
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式失败");
                    }
                } else if (sensorConfig.color == 9) {
                    boolean ret = mGuideInterface.changePalette(9);  // 使用黑热伪彩
                    mDeviceConfig.setPaletteIndex(9);
                    if (ret) {
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.black_hot);
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式成功");
                    } else {
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式失败");
                    }
                } else {
                    boolean ret = mGuideInterface.changePalette(0);  // 使用白热伪彩
                    mDeviceConfig.setPaletteIndex(0);
                    if (ret) {
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot);
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式成功");
                    } else {
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式失败");
                    }
                }
            } else if (config.color == 1) {  // 色彩切换为彩色模式
                boolean ret = mGuideInterface.changePalette(sensorConfig.color);  // 使用铁红伪彩
                mDeviceConfig.setPaletteIndex(sensorConfig.color);
                if (ret) {
                    switch (sensorConfig.color) {
                        case 0:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot);
                            break;
                        case 1:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.melt_stone);
                            break;
                        case 2:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.iron_red);
                            break;
                        case 3:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.hot_iron);
                            break;
                        case 4:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.medical);
                            break;
                        case 5:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.arctic);
                            break;
                        case 6:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.rainbow1);
                            break;
                        case 7:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.rainbow2);
                            break;
                        case 8:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.reddening);
                            break;
                        case 9:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.black_hot);
                            break;
                    }
                    Log.i(Log.TAG, "高德红外色彩设置为彩色模式成功");
                } else {
                    Log.i(Log.TAG, "高德红外色彩设置为彩色模式失败");
                }
            }
            int brightness = mapFrom1To100(config.brightness, 16);
            mGuideInterface.setBrightness(brightness);
            Log.i(Log.TAG, "服务器设置亮度为" + config.brightness + "，转换后的高德红外亮度设置为" + brightness);
            int contrast = mapFrom1To100(config.contrast, 255);
            mGuideInterface.setContrast(contrast);
            Log.i(Log.TAG, "服务器设置对比度为" + config.contrast + "，转换后的高德红外对比度设置为" + contrast);
        } else {
            Log.e(Log.TAG, "高德红外尚未初始化，无法实时设置图像参数");
        }
        return true;
    }

    @Override
    public boolean setRecordTimes(List<Settings.VideoTimeItem> list) {
        return true;
    }

    @Override
    protected void doSampleData(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo, int outIndex) { /////
        try {
//            if (isRecording()) {
//                if (mediaMuxer == null) {
//                    ByteBuffer sps = mediaCodec.getOutputFormat().getByteBuffer("csd-0"); /////
//                    ByteBuffer pps = mediaCodec.getOutputFormat().getByteBuffer("csd-1"); /////
//                    // 获取到sps pps才能初始化Muxer
//                    initMuxer(sps, pps);
//                }
//                if (mediaMuxer != null) {
//                    mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo); /////
//                }
//            }
            if (isRecording() && !pausing && mediaMuxer != null) {
                if (outIndex >= 0) {
                    if (muxerStarted && bufferInfo.size > 0) {
                        ByteBuffer outBuf = mediaCodec.getOutputBuffer(outIndex);
                        if (outBuf != null) {
                            outBuf.position(bufferInfo.offset);
                            outBuf.limit(bufferInfo.offset + bufferInfo.size);

                            long ptsUs = bufferInfo.presentationTimeUs;
                            long nowUs = (avStartNs != 0) ? ((System.nanoTime() - avStartNs) / 1000) : ptsUs;
                            if (ptsUs > nowUs + 5_000_000L) ptsUs = nowUs;
                            if (ptsUs <= lastVideoPtsUs) ptsUs = lastVideoPtsUs + 1;
                            lastVideoPtsUs = ptsUs;
                            bufferInfo.presentationTimeUs = ptsUs;

                            mediaMuxer.writeSampleData(videoTrackIndex, outBuf, bufferInfo); /////
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "高德红外录制视频文件异常：" + e);
        }
    }

    public void initMuxer(ByteBuffer sps, ByteBuffer pps) throws IOException {
        Settings.VideoCodec vCodec = getVideoCodec(streamType);
        //Point size = new Point(CAMERA_RESOLUTION_W * RESIZE, CAMERA_RESOLUTION_H * RESIZE);
        Point size = Settings.VideoCodec.getResolution(vCodec.resolution);
        /////
        if (vCodec.resolution > 11) {
            sizeX = 1600;
            sizeY = 1200;
        } else {
            sizeX = size.x;
            sizeY = size.y;
        }
        /////
        // 写入编码数据之前需要配置视频头部信息(csd参数)，csd全称Codec-specific Data，对于H.264来说，
        // “csd-0”和"csd-1"分别对应sps和pps；对于AAC来说，"csd-0"对应ADTS。
        //MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.x, size.y);
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, (int) sizeX, (int) sizeY);
        mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, vCodec.frame);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        mediaFormat.setByteBuffer("csd-0", sps);
        mediaFormat.setByteBuffer("csd-1", pps);
        mediaMuxer = new MediaMuxer(tmpRecordFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        videoTrackIndex = mediaMuxer.addTrack(mediaFormat); /////
        mediaMuxer.start();
    }

    @Override
    public void setSensorConfig(IRSetting.SensorConfig config) {
        sensorConfig = config;
        Log.i(Log.TAG, "高德红外机芯参数设置：" + config.toString());
        try {
            IRRegionTemp.setTempUnit(sensorConfig.measureUnit);
            if (photoConfig.color == 0) {  // 色彩切换为黑白模式
                if (sensorConfig.color == 0) {
                    boolean ret = mGuideInterface.changePalette(0);  // 使用白热伪彩
                    mDeviceConfig.setPaletteIndex(0);
                    if (ret) {
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot);
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式成功");
                    } else {
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式失败");
                    }
                } else if (sensorConfig.color == 9) {
                    boolean ret = mGuideInterface.changePalette(9);  // 使用黑热伪彩
                    mDeviceConfig.setPaletteIndex(9);
                    if (ret) {
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.black_hot);
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式成功");
                    } else {
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式失败");
                    }
                } else {
                    boolean ret = mGuideInterface.changePalette(0);  // 使用白热伪彩
                    mDeviceConfig.setPaletteIndex(0);
                    if (ret) {
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot);
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式成功");
                    } else {
                        Log.i(Log.TAG, "高德红外色彩设置为黑白模式失败");
                    }
                }
            } else if (photoConfig.color == 1) {  // 色彩切换为彩色模式
                boolean ret = mGuideInterface.changePalette(sensorConfig.color);  // 使用铁红伪彩
                mDeviceConfig.setPaletteIndex(sensorConfig.color);
                if (ret) {
                    switch (sensorConfig.color) {
                        case 0:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot);
                            break;
                        case 1:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.melt_stone);
                            break;
                        case 2:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.iron_red);
                            break;
                        case 3:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.hot_iron);
                            break;
                        case 4:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.medical);
                            break;
                        case 5:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.arctic);
                            break;
                        case 6:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.rainbow1);
                            break;
                        case 7:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.rainbow2);
                            break;
                        case 8:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.reddening);
                            break;
                        case 9:
                            osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.black_hot);
                            break;
                    }
                    Log.i(Log.TAG, "高德红外色彩设置为彩色模式成功");
                } else {
                    Log.i(Log.TAG, "高德红外色彩设置为彩色模式失败");
                }
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "高德红外机芯参数设置异常：" + e);
        }
    }

    @Override
    public void setIrSetting(IRSetting setting) {
        iRSetting = setting;
        // 设置探测器原始分辨率
        if (iRSetting.resolution == 0) {
            CAMERA_RESOLUTION_W = 384;
            CAMERA_RESOLUTION_H = 288;
            RESIZE = 2;
        } else {
            CAMERA_RESOLUTION_W = 640;
            CAMERA_RESOLUTION_H = 512;
            RESIZE = 1;
        }
        mBitmap = Bitmap.createBitmap(CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, Bitmap.Config.ARGB_8888);
        mArgb = new byte[CAMERA_RESOLUTION_W * CAMERA_RESOLUTION_H * 4];
        mAbgr = new byte[CAMERA_RESOLUTION_W * CAMERA_RESOLUTION_H * 4];
        mTemp = new float[CAMERA_RESOLUTION_W * CAMERA_RESOLUTION_H];
        mY16 = new short[CAMERA_RESOLUTION_W * CAMERA_RESOLUTION_H];
        mParamLine = new byte[CAMERA_RESOLUTION_W * 2];
        temperature = new float[CAMERA_RESOLUTION_W * CAMERA_RESOLUTION_H + 10];
        videoFrame = new ARGBFrame(CAMERA_RESOLUTION_W * CAMERA_RESOLUTION_H * 4);
        photoFrame = new ARGBFrame(CAMERA_RESOLUTION_W * CAMERA_RESOLUTION_H * 4);
        sourceBitmap = Bitmap.createBitmap(CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, Bitmap.Config.ARGB_8888);
//        previewBitmap = Bitmap.createBitmap(CAMERA_RESOLUTION_W * RESIZE, CAMERA_RESOLUTION_H * RESIZE, Bitmap.Config.ARGB_8888);
    }

    @Override
    public void move(int cmd, int para) {
        ptzDev.move(cmd, para);
    }

    /////
    // 双光融合功能
    @Override
    public boolean imageFusion(int stream, int preset, boolean show, String filename, Bitmap image_rgb, int recordPreset) {
        try {
            enter(130);
            Log.i(Log.TAG, "高德红外拍照开始：" + filename);

            Bitmap image_ir = captureBitmap(preset, recordPreset);
            if (!isLiving() && !isRecording()) {
                close();
            }
            if (image_ir == null) {
                Log.i(Log.TAG, "高德红外拍照失败，未捕获到照片数据, 设备状态：" + Status());
            } else {
                // 智能测距功能
//                if (detector != null && objectDetect == 5) {
//                    // 用缩放前的图片进行目标检测，保证保存的坐标与画框的范围一致
//                    detectObbResult = detector.recognizeImageObb(bitmap);
//                    //drawObject(bitmap, false); //////用于调试测距算法
//                    bitmap = Bitmap.createScaledBitmap(bitmap,
//                            (int) sizeX,
//                            (int) sizeY,
//                            true);
//                    if (detectObbResult != null) {
//                        IRSetting iRSetting = new IRSetting();
//                        iRSetting.sensorConfig = sensorConfig;
//                        Vector<IRSetting.IrRegionInfo> regions = null;
//                        IRSetting.PresetRegions channelRegion = null;
//                        if (iRSetting.irChannelRegions != null && iRSetting.irChannelRegions.size() > 2) {
//                            channelRegion = iRSetting.irChannelRegions.get(2);
//                            if (channelRegion.irPresetRegions != null && iRSetting.irChannelRegions.size() > preset) {
//                                regions = channelRegion.irPresetRegions.get(preset);
//                            }
//                        }
//                        // 处理原始区域信息为空的情况
//                        if (regions == null) {
//                            regions = new Vector<>();
//                        }
//                        // 保留环境温度区域
//                        Vector<IRSetting.IrRegionInfo> newRegions = regions.stream()
//                                .filter(irRegion -> irRegion.emissivity == 0 || irRegion.irRegion.cls == 2)
//                                .collect(Collectors.toCollection(Vector::new));
//                        if (newRegions == null) {
//                            newRegions = new Vector<>();
//                        }
//                        Log.i(Log.TAG, "保留环境温度区域" + newRegions);
//                        Vector<IRRegionTemp> irRegionTempVector = new Vector<>();
//                        Vector<Float> irRegionDistanceVector = new Vector<>();
//
//                        float centerX = CAMERA_RESOLUTION_W / 2;  // 图像中心x坐标
//                        float centerLeftBound = (float) (centerX - 0.1 * CAMERA_RESOLUTION_W);
//                        float centerRightBound = (float) (centerX + 0.1 * CAMERA_RESOLUTION_W);
//                        Log.i(Log.TAG, "中心目标的横坐标范围设定为：" + centerLeftBound + "-" + centerRightBound);
//
//                        // 判断有没有测温区域信息，没有就创建新的
//                        if (!detectObbResult.isEmpty()) {
//                            List<Classifier.RecognitionObb> centerCandidates = new ArrayList<>();
//                            // 先筛选符合中心条件的目标
//                            for (Classifier.RecognitionObb result : detectObbResult) {
//                                float targetCenterX = result.getCenterX();
//                                if (targetCenterX >= centerLeftBound && targetCenterX <= centerRightBound) {
//                                    centerCandidates.add(result);
//                                }
//                            }
//                            Log.i(Log.TAG, "符合中心条件的目标有" + centerCandidates);
//                            // 从筛选出的目标中选择距离图像中心最近的目标
//                            Classifier.RecognitionObb middleTarget = null;
//                            float minDistance = Float.MAX_VALUE;
//                            for (Classifier.RecognitionObb candidate : centerCandidates) {
//                                float targetCenterX = candidate.getCenterX();
//                                float distanceToCenter = Math.abs(targetCenterX - centerX);
//                                if (distanceToCenter < minDistance) {
//                                    minDistance = distanceToCenter;
//                                    middleTarget = candidate;
//                                }
//                            }
//                            Log.i(Log.TAG, "距离图像中心最近的目标为" + middleTarget);
//                            // 如果找到中心目标，就应用测距算法
//                            if (middleTarget != null) {
//                                // 用Iterator在detectResult中删除middleTarget，并且将中心目标添加到regions中
//                                Iterator<Classifier.RecognitionObb> iterator = detectObbResult.iterator();
//                                while (iterator.hasNext()) {
//                                    if (iterator.next().equals(middleTarget)) {
//                                        iterator.remove();  // 安全删除
//                                        break;  // 只删除第一个匹配的目标
//                                    }
//                                }
//                                IRSetting.IrRegionInfo middleIrRegion = new IRSetting.IrRegionInfo();
//                                middleIrRegion.irRegion = new IRSetting.IrRegion();
//                                middleIrRegion.irRegion.center = 1;  // 标记为中心目标
//                                //middleIrRegion.irRegion.cls = (byte) middleTarget.getClassID();  // 目标类型：电缆终端、避雷器
//                                middleIrRegion.irRegion.cls = 0;  // 目标类型：电缆终端、避雷器
//                                middleIrRegion.irRegion.points = new Vector<>();
//                                // 归一化四个角点坐标
//                                for (Point p : middleTarget.getPolygon()) {
//                                    int x = p.x * 255 / (CAMERA_RESOLUTION_W * RESIZE);
//                                    int y = p.y * 255 / (CAMERA_RESOLUTION_H * RESIZE);
//                                    middleIrRegion.irRegion.points.add(new Point(x, y));
//                                }
//                                middleIrRegion.distance = sensorConfig.distance;  // 中心目标使用全局测距
//                                middleIrRegion.conf = middleTarget.getConfidence();
//                                int index = 1;
//                                IRRegionTemp middleRegionTemp = new IRRegionTemp(context, middleIrRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, index++, sizeX, sizeY);
//                                middleRegionTemp.setTempRaw(temperature);
//                                irRegionTempVector.add(middleRegionTemp);
//                                irRegionDistanceVector.add(middleIrRegion.distance);
//                                newRegions.add(middleIrRegion);
//
//                                float piexlWidthMiddle = middleTarget.getWidth();
//                                for (Classifier.RecognitionObb result : detectObbResult) {
//                                    float piexlWidth = result.getWidth();
//                                    IRSetting.IrRegionInfo irRegion = new IRSetting.IrRegionInfo();
//                                    irRegion.irRegion = new IRSetting.IrRegion();
//                                    irRegion.irRegion.center = 0;  // 标记为非中心目标
//                                    //irRegion.irRegion.cls = (byte) middleTarget.getClassID();  // 目标类型：电缆终端、避雷器
//                                    irRegion.irRegion.cls = 0;  // 目标类型：电缆终端、避雷器
//                                    irRegion.irRegion.points = new Vector<>();
//                                    for (Point p : result.getPolygon()) {
//                                        int x = p.x * 255 / (CAMERA_RESOLUTION_W * RESIZE);
//                                        int y = p.y * 255 / (CAMERA_RESOLUTION_H * RESIZE);
//                                        irRegion.irRegion.points.add(new Point(x, y));
//                                    }
//                                    irRegion.distance = sensorConfig.distance * piexlWidth / piexlWidthMiddle;  // 目标距离，单位米
//                                    irRegion.conf = result.getConfidence();
//                                    IRRegionTemp regionTemp = new IRRegionTemp(context, irRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, index++, sizeX, sizeY);
//                                    regionTemp.setTempRaw(temperature);
//                                    irRegionTempVector.add(regionTemp);
//                                    irRegionDistanceVector.add(irRegion.distance);
//                                    newRegions.add(irRegion);
//                                }
//                            } else {
//                                // 否则区域测距设置为全局测距，等待下一次识别到中心目标
//                                int index = 1;
//                                for (Classifier.RecognitionObb result : detectObbResult) {
//                                    IRSetting.IrRegionInfo irRegion = new IRSetting.IrRegionInfo();
//                                    irRegion.irRegion = new IRSetting.IrRegion();
//                                    irRegion.irRegion.center = 0;  // 标记为非中心目标
//                                    //irRegion.irRegion.cls = (byte) middleTarget.getClassID();  // 目标类型：电缆终端、避雷器
//                                    irRegion.irRegion.cls = 0;  // 目标类型：电缆终端、避雷器
//                                    irRegion.irRegion.points = new Vector<>();
//                                    for (Point p : result.getPolygon()) {
//                                        int x = p.x * 255 / (CAMERA_RESOLUTION_W * RESIZE);
//                                        int y = p.y * 255 / (CAMERA_RESOLUTION_H * RESIZE);
//                                        irRegion.irRegion.points.add(new Point(x, y));
//                                    }
//                                    irRegion.distance = sensorConfig.distance;
//                                    irRegion.conf = result.getConfidence();
//                                    IRRegionTemp regionTemp = new IRRegionTemp(context, irRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, index++, sizeX, sizeY);
//                                    regionTemp.setTempRaw(temperature);
//                                    irRegionTempVector.add(regionTemp);
//                                    irRegionDistanceVector.add(irRegion.distance);
//                                    newRegions.add(irRegion);
//                                }
//                            }
//                            staticRegionsMaps.put(preset, irRegionTempVector);
//                            staticRegionsDistanceMaps.put(preset, irRegionDistanceVector);
//                            channelRegion = new IRSetting.PresetRegions();
//                            channelRegion.irPresetRegions.put(preset, newRegions);
//                            iRSetting.irChannelRegions.put(2, channelRegion);
//                            saveSettings(iRSetting, MainActivity.IR_SETTING_FILE);
//                        }
//                    }
//                }
//                        } else {
//                            // 有就更新旧的
//                            // 先对detectResult按照置信度排序，保证只将置信度最高的更新到区域中
//                            List<Classifier.Recognition> sortedResults = detectResult.stream()
//                                    .sorted(Comparator.comparing(Classifier.Recognition::getConfidence).reversed())
//                                    .collect(Collectors.toList());
//                            // 创建一个Set存储已更新的region索引
//                            Set<Integer> updatedRegionIds = new HashSet<>();
//                            Vector<IRSetting.IrRegionInfo> centerCandidates = new Vector<>();  // 存储符合中心条件的区域
//                            IRSetting.IrRegionInfo middleRegion = null;
//                            int index = 1;  ///// 后面要改顺序
//                            for (Classifier.Recognition result : sortedResults) {
//                                Integer bestRegionId = -1;
//                                float bestIoU = 0;
//                                for (int regionIndex = 0; regionIndex < regions.size(); regionIndex++) {
//                                    IRSetting.IrRegionInfo region = regions.get(regionIndex);
//                                    if (updatedRegionIds.contains(regionIndex)) {
//                                        continue;  // 这个region已经被更新过，跳过
//                                    }
//                                    float iou = calculateIoU(region, result);  // 计算IoU
//                                    if (iou > 0.5 && iou > bestIoU) {
//                                        bestIoU = iou;
//                                        bestRegionId = regionIndex;
//                                    }
//                                }
//                                // 只更新最佳region，并记录region索引
//                                if (bestRegionId != null) {
//                                    // 目标与已有区域匹配，更新区域信息
//                                    updatedRegionIds.add(bestRegionId);  // 记录region索引，防止重复更新
//                                    IRSetting.IrRegionInfo bestRegion = regions.get(bestRegionId);
//                                    bestRegion.irRegion.points = new Vector<>();
//                                    bestRegion.irRegion.points.add(new Point((int) result.getLocation().left * 255 / (CAMERA_RESOLUTION_W * RESIZE), (int) result.getLocation().top * 255 / (CAMERA_RESOLUTION_H * RESIZE)));
//                                    bestRegion.irRegion.points.add(new Point((int) result.getLocation().right * 255 / (CAMERA_RESOLUTION_W * RESIZE), (int) result.getLocation().top * 255 / (CAMERA_RESOLUTION_H * RESIZE)));
//                                    bestRegion.irRegion.points.add(new Point((int) result.getLocation().right * 255 / (CAMERA_RESOLUTION_W * RESIZE), (int) result.getLocation().bottom * 255 / (CAMERA_RESOLUTION_H * RESIZE)));
//                                    bestRegion.irRegion.points.add(new Point((int) result.getLocation().left * 255 / (CAMERA_RESOLUTION_W * RESIZE), (int) result.getLocation().bottom * 255 / (CAMERA_RESOLUTION_H * RESIZE)));
//                                    bestRegion.distance = sensorConfig.distance;
//                                    bestRegion.conf = result.getConfidence();
//                                    regions.add(bestRegion);
//                                    // 寻找中心区域
//                                    // 如果已经标记为中心区域，就直接存储中心区域
//                                    if (bestRegion.irRegion.center == 1) {
//                                        middleRegion = bestRegion;
//                                        IRRegionTemp regionTemp = new IRRegionTemp(context, bestRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, index++, sizeX, sizeY);
//                                        irRegionTempVector.add(regionTemp);
//                                        irRegionDistanceVector.add(bestRegion.distance);
//                                    } else {
//                                        // 否则筛选符合中心条件的区域
//                                        if (result.getLocation().left >= centerLeftBound && result.getLocation().right <= centerRightBound) {
//                                            centerCandidates.add(bestRegion);
//                                        }
//                                    }
//                                } else {
//                                    // 新目标，创建新区域
//                                    IRSetting.IrRegionInfo irRegion = new IRSetting.IrRegionInfo();
//                                    irRegion.irRegion.points = new Vector<>();
//                                    irRegion.irRegion.points.add(new Point((int) result.getLocation().left * 255 / (CAMERA_RESOLUTION_W * RESIZE), (int) result.getLocation().top * 255 / (CAMERA_RESOLUTION_H * RESIZE)));
//                                    irRegion.irRegion.points.add(new Point((int) result.getLocation().right * 255 / (CAMERA_RESOLUTION_W * RESIZE), (int) result.getLocation().top * 255 / (CAMERA_RESOLUTION_H * RESIZE)));
//                                    irRegion.irRegion.points.add(new Point((int) result.getLocation().right * 255 / (CAMERA_RESOLUTION_W * RESIZE), (int) result.getLocation().bottom * 255 / (CAMERA_RESOLUTION_H * RESIZE)));
//                                    irRegion.irRegion.points.add(new Point((int) result.getLocation().left * 255 / (CAMERA_RESOLUTION_W * RESIZE), (int) result.getLocation().bottom * 255 / (CAMERA_RESOLUTION_H * RESIZE)));
//                                    irRegion.distance = sensorConfig.distance;
//                                    irRegion.conf = result.getConfidence();
//                                    regions.add(irRegion);
//                                }
//                            }
//                            // 从筛选出的区域中选择距离图像中心最近的区域
//                            float minDistance = Float.MAX_VALUE;
//                            for (IRSetting.IrRegionInfo candidate : centerCandidates) {
//                                float targetCenterX = (candidate.irRegion.points.get(0).x + candidate.irRegion.points.get(1).x) / 2;
//                                float distanceToCenter = Math.abs(targetCenterX - centerX);
//                                if (distanceToCenter < minDistance) {
//                                    minDistance = distanceToCenter;
//                                    middleRegion = candidate;
//                                }
//                            }
//                            ///////
//                            // 如果找到中心区域，就应用测距算法
//                            Vector<IRSetting.IrRegionInfo> newRegions = new Vector<>();
//                            if (middleRegion != null) {
//                                float piexlWidthMiddle = middleRegion.irRegion.points.get(1).x - middleRegion.irRegion.points.get(0).x;
//                                for (IRSetting.IrRegionInfo irRegion : regions) {
//                                    if (!irRegion.equals(middleRegion)) {
//                                        float piexlWidth = irRegion.irRegion.points.get(1).x - irRegion.irRegion.points.get(0).x;
//                                        irRegion.irRegion.center = 0;
//                                        irRegion.distance = sensorConfig.distance * piexlWidth / piexlWidthMiddle;  // 目标距离，单位米
//                                        IRRegionTemp regionTemp = new IRRegionTemp(context, irRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, index++, sizeX, sizeY);
//                                        irRegionTempVector.add(regionTemp);
//                                        irRegionDistanceVector.add(irRegion.distance);
//                                        newRegions.add(irRegion);
//                                    }
//                                }
//                            } else {
//                                // 否则区域测距设置为全局测距，等待下一次识别到中心目标
//                                for (IRSetting.IrRegionInfo irRegion : regions) {
//                                    irRegion.irRegion.center = 0;
//                                    IRRegionTemp regionTemp = new IRRegionTemp(context, irRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, index++, sizeX, sizeY);
//                                    irRegionTempVector.add(regionTemp);
//                                    irRegionDistanceVector.add(irRegion.distance);
//                                    newRegions.add(irRegion);
//                                }
//                            }
//                            staticRegionsMaps.put(preset, irRegionTempVector);
//                            staticRegionsDistanceMaps.put(preset, irRegionDistanceVector);
//                            channelRegion.irPresetRegions.put(preset, newRegions);
//                            saveSettings(iRSetting, MainActivity.IR_SETTING_FILE);
//                        }
//                    }
//                }
                Bitmap bitmap = image_ir;
                Log.i(Log.TAG, "双光融合角度" + iRSetting.angle);
                Log.i(Log.TAG, "双光融合水平位移" + iRSetting.horDisplacement);
                Log.i(Log.TAG, "双光融合垂直位移" + iRSetting.verDisplacement);
                if (iRSetting.imageFusion == 1) {
                    // 视场角
                    float[] fovRgb = {58.5f, 36f};      /// 51.69  29.58  寰宇
                    float[] fovIr = new float[0];
                    if (iRSetting.focalLen == 3 && iRSetting.resolution == 0) {
                        fovIr = new float[]{80.8f, 59.6f};  // cOIN417R 4.9mm
                    } else if (iRSetting.focalLen == 0 && iRSetting.resolution == 0) {
                        fovIr = new float[]{39.5f, 30.1f};  // cOIN417R 9.1mm
                    } else if (iRSetting.focalLen == 1 && iRSetting.resolution == 0) {
                        fovIr = new float[]{28.2f, 21.3f};  // cOIN417R 13mm
                    } else if (iRSetting.focalLen == 2 && iRSetting.resolution == 0) {
                        fovIr = new float[]{19.5f, 14.7f};  // cOIN417R 19mm
                    } else if (iRSetting.focalLen == 3 && iRSetting.resolution == 1) {
                        fovIr = new float[]{92.3f, 72.5f};  // cOIN612R 4.9mm
                    } else if (iRSetting.focalLen == 0 && iRSetting.resolution == 1) {
                        fovIr = new float[]{48.0f, 38.4f};  // cOIN612R 9.1mm
                    } else if (iRSetting.focalLen == 1 && iRSetting.resolution == 1) {
                        fovIr = new float[]{32.9f, 26.6f};  // cOIN612R 13mm
                    } else if (iRSetting.focalLen == 2 && iRSetting.resolution == 1) {
                        fovIr = new float[]{22.9f, 18.4f};  // cOIN612R 19mm
                    }
                    int hRgb = image_rgb.getHeight();
                    int wRgb = image_rgb.getWidth();
                    int hIr = image_ir.getHeight();
                    int wIr = image_ir.getWidth();
                    Mat rgbMat = new Mat();
                    Mat irMat = new Mat();
                    org.opencv.android.Utils.bitmapToMat(image_rgb, rgbMat);
                    // 步骤1：转换为灰度图（1通道）
                    Mat gray1C = new Mat();
                    Imgproc.cvtColor(rgbMat, gray1C, Imgproc.COLOR_BGR2GRAY);
                    // 步骤2：将单通道灰度图复制为3通道
                    Imgproc.cvtColor(gray1C, rgbMat, Imgproc.COLOR_GRAY2BGRA);
                    org.opencv.android.Utils.bitmapToMat(image_ir, irMat);
                    Mat fused = new Mat();
                    Mat fusedResized = new Mat();
                    if (fovIr[0] <= fovRgb[0] && fovIr[1] <= fovRgb[1]) {
                        // 情况1：红外视角更小，嵌入式处理
                        float wRatio = fovIr[0] / fovRgb[0];
                        float hRatio = fovIr[1] / fovRgb[1];
                        int projW = (int)(wRgb * wRatio);
                        int projH = (int)(hRgb * hRatio);
                        Mat irResized = new Mat();
                        Imgproc.resize(irMat, irResized, new Size(projW, projH));
                        // 旋转：只旋转可见光
                        float angle = iRSetting.angle;
                        org.opencv.core.Point center = new org.opencv.core.Point(wRgb / 2.0, hRgb / 2.0);
                        Mat M = Imgproc.getRotationMatrix2D(center, angle, 1.0);
                        Mat rgbRotated = new Mat();
                        Imgproc.warpAffine(rgbMat, rgbRotated, M, new Size(projW, projH));
                        int centerX = wRgb / 2 + iRSetting.horDisplacement;
                        int centerY = hRgb / 2 + iRSetting.verDisplacement;
                        int xStart = Math.max(0, Math.min(centerX - projW / 2, wRgb - projW));
                        int yStart = Math.max(0, Math.min(centerY - projH / 2, hRgb - projH));
                        Mat roi = rgbMat.submat(yStart, yStart + projH, xStart, xStart + projW);
                        Mat irCropped = irResized.submat(0, projH, 0, projW);
                        Core.addWeighted(roi, 0.2, irCropped, 0.8, 0.0, fused);
                        Imgproc.resize(fused, fusedResized, new Size(bitmap.getWidth(), bitmap.getHeight()));  // 缩放到和原bitmap一样大
                    } else if (fovIr[0] >= fovRgb[0] && fovIr[1] >= fovRgb[1]) {
                        // 情况2：红外视角更大，裁剪红外图
                        float wRatio = fovRgb[0] / fovIr[0];
                        float hRatio = fovRgb[1] / fovIr[1];
                        int projW = (int)(wIr * wRatio);
                        int projH = (int)(hIr * hRatio);
                        Mat rgbResized = new Mat();
                        Imgproc.resize(rgbMat, rgbResized, new Size(projW, projH));
                        // 旋转：只旋转可见光
                        float angle = iRSetting.angle;
                        org.opencv.core.Point center = new org.opencv.core.Point(projW / 2.0, projH / 2.0);
                        Mat M = Imgproc.getRotationMatrix2D(center, angle, 1.0);
                        Mat rgbRotated = new Mat();
                        Imgproc.warpAffine(rgbResized, rgbRotated, M, new Size(projW, projH));
                        int centerX = wIr / 2 + iRSetting.horDisplacement;
                        int centerY = hIr / 2 + iRSetting.verDisplacement;
                        int xStart = Math.max(0, Math.min(centerX - projW / 2, wIr - projW));
                        int yStart = Math.max(0, Math.min(centerY - projH / 2, hIr - projH));
                        Mat roi = irMat.submat(yStart, yStart + projH, xStart, xStart + projW);
                        Mat rgbCrop = rgbRotated.submat(0, projH, 0, projW);
                        Core.addWeighted(roi, 0.8, rgbCrop, 0.2, 0.0, fused);
                        Imgproc.resize(fused, fusedResized, new Size(bitmap.getWidth(), bitmap.getHeight()));  // 缩放到和原bitmap一样大
                    } else if (fovIr[0] < fovRgb[0] && fovIr[1] >= fovRgb[1]) {
                        // 情况3：红外只在垂直方向更大
                        // Step 1. 根据视场角裁剪 RGB 图像
                        int cropW = (int)(wRgb * (fovIr[0] / fovRgb[0]));
                        int cropH = (int)(hRgb * (fovRgb[1] / fovIr[1]));
                        int xStart = Math.max(0, (wRgb - cropW) / 2);
                        int yStart = Math.max(0, (hRgb - cropH) / 2);
                        int xEnd = Math.min(wRgb, xStart + cropW);
                        int yEnd = Math.min(hRgb, yStart + cropH);
                        Mat rgbCropped = rgbMat.submat(yStart, yEnd - yStart, xStart, xEnd - xStart);
                        // Step 2. Resize RGB 到红外图尺寸
                        Mat rgbResized = new Mat();
                        Imgproc.resize(rgbCropped, rgbResized, new Size(wIr, hIr));
                        // Step 3. 平移 + 旋转 RGB 图像（warpAffine）
                        double angle = iRSetting.angle;
                        int xOffset = iRSetting.horDisplacement;
                        int yOffset = iRSetting.verDisplacement;
                        org.opencv.core.Point center = new org.opencv.core.Point(wIr / 2.0, hIr / 2.0);
                        Mat M = Imgproc.getRotationMatrix2D(center, angle, 1.0);
                        M.put(0, 2, M.get(0, 2)[0] + xOffset);  // 水平平移
                        M.put(1, 2, M.get(1, 2)[0] + yOffset);  // 垂直平移
                        Mat rgbWarped = new Mat();
                        Imgproc.warpAffine(rgbResized, rgbWarped, M, new Size(wIr, hIr));
                        // Step 4. 加权融合
                        Core.addWeighted(rgbWarped, 0.2, irMat, 0.8, 0.0, fusedResized);
                    } else if (fovIr[0] > fovRgb[0] && fovIr[1] <= fovRgb[1]) {
                        // 情况4：红外只在水平方向更大
                    }
                    org.opencv.android.Utils.matToBitmap(fusedResized, bitmap);  // 再转成 bitmap
                }
                drawWatermark(bitmap);
                if (sensorConfig.hotTracker == 1) {
                    drawHotTracker(bitmap);
                }
                if (irOverProtect.getMode() == Protection) {
                    bitmap = drawProtection(bitmap);
                }
                drawTempRegion(preset, bitmap, IRRegionTemp.RegionType.REGION_STATIC);
                // 区域温度校正后再绘制调色板
                Vector<IRRegionTemp> staticRegions = staticRegionsMaps.get(preset);
                Vector<Float> staticRegionsDistance = staticRegionsDistanceMaps.get(preset);
                if (sensorConfig.onPalette == 1) {
                    drawPalette(bitmap, staticRegions, staticRegionsDistance);
                }
                long stamp = getTimestampFromFilename(filename);
                // 回传温度 高低温预警
                doTempCallback(preset, bitmap, stamp, staticRegionsMaps, staticRegionsDistanceMaps);

                // 设置饱和度
                // 创建一个空白的Bitmap，大小与原始图像相同
                Bitmap saturationBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                // 创建Canvas对象
                Canvas canvas = new Canvas(saturationBitmap);
                // 创建ColorMatrix对象
                ColorMatrix colorMatrix = new ColorMatrix();
                // 设置饱和度
                colorMatrix.setSaturation(photoConfig.saturation / 50);
                // 创建Paint对象
                Paint paint = new Paint();
                // 将ColorMatrixColorFilter应用到Paint对象
                paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
                // 使用Paint对象绘制图像
                canvas.drawBitmap(bitmap, 0, 0, paint);
                Log.i(Log.TAG, "高德红外饱和度设置为" + photoConfig.saturation);
                if (show) controllerCallback.onFrame(saturationBitmap);

                saveBitmapAsJPEG(saturationBitmap, filename, 100);
                bitmap.recycle();  // 释放原始Bitmap内存
                saturationBitmap.recycle();  // 释放saturationBitmap内存

                controllerCallback.onPhotoTaked(stamp, this.id, preset, filename);
                return true;
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "高德红外拍照异常：" + e);
        } finally {
            exit();
        }
        return false;
    }
    /////
}

