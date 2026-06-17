package hikvision.zhanyun.com.hikvision.device.iray;

import static lyh.Utils.PERIOD_MINUTE;
import static lyh.Utils.PERIOD_SECOND;
import static lyh.Utils.dateFromString;
import static lyh.Utils.restartApplication;
import static lyh.Utils.saveBitmapAsJPEG;
import static lyh.Utils.stringToFile; /////

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSON; /////
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.ITemperatureCallback;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

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

import org.opencv.core.Core; /////
import org.opencv.core.Mat; /////
import org.opencv.core.Size; /////
import org.opencv.imgproc.Imgproc; /////

public class IRayDev extends Device {
    private final int RESIZE = 2;
    private UVCCamera uvcCamera;
    private boolean closeForce = false;
    private USBMonitor.UsbControlBlock usbControler = null;
    private final int OVER_EXPOSURE_THRESHOLD = 14000; // 防阳光灼伤门限值14000，厂家推荐，超过此门限2秒开启保护，10秒后取消保护
    private final long MIN_BOOTUP_MILISECONDS = 100 * PERIOD_SECOND;
    private IRTempAlarm globalAlarm;
    private IRUVC384 irdev;
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
    private HashMap<Integer, Vector<IRRegionTemp>> staticRegionsMaps = new HashMap<>();
    private float presetEnvTemp;  // 用于存储环境温度区域对应的预置位
    private IRRegionTemp regionEnvTemp;  // 用于存储环境温度区域
    private static final int CAMERA_RESOLUTION_W = 384;
    private static final int CAMERA_RESOLUTION_H = 288;
    private Bitmap osdPalette;
    private int curPreset;
    private int envAlarmThres;
    private int cmpAlarmThres;
    private boolean useAudio; /////
    private Paint tempPaint; /////
    private Paint timePaint; /////
    public Device ptzDev; // 可见光机芯，用于联动云台
    private static Settings.DeviceConfig deviceConfig = new Settings.DeviceConfig();  // 通道JSON配置
    private IRSetting iRSetting = new IRSetting();
    /////
    private float sizeX;  // 存储图像分辨率参数
    private float sizeY;  // 存储图像分辨率参数
//    private MediaCodec mediaEncoder; /////

    /////

    private class ARGBFrame {
        public ARGBFrame(int cap) {
            data = new byte[cap];
        }
        public final byte[] data;
        public boolean isReady = false;

        public void noTrans() {
            for (int i = 3; i < data.length; i+=4) {
                data[i] = (byte) 0xFF;
            }
        }

        public byte[] getData() { return data; }

        private int RGB(int x, int y) {
            return  (data[(y * CAMERA_RESOLUTION_W + x) * 4] & 0xFF) << 16 |
                    (data[(y * CAMERA_RESOLUTION_W + x) * 4 + 1] & 0xFF) << 8 |
                    (data[(y * CAMERA_RESOLUTION_W + x) * 4 + 2] & 0xFF);
        }

        public boolean isBlack() {
            for (int x : new int[] {0, CAMERA_RESOLUTION_W / 3, CAMERA_RESOLUTION_W * 2 / 3, CAMERA_RESOLUTION_W - 1}) {
                for (int y : new int[] {0, CAMERA_RESOLUTION_H / 3, CAMERA_RESOLUTION_H * 2 / 3, CAMERA_RESOLUTION_H - 1}) {
                    if (RGB(x, y) != 0) return false;
                }
            }
            Log.i(Log.TAG, "英睿红外视频帧全黑");
            return true;
        }
    }

    private ARGBFrame videoFrame = new ARGBFrame(CAMERA_RESOLUTION_W * CAMERA_RESOLUTION_H * 4);
    private ARGBFrame photoFrame = new ARGBFrame(CAMERA_RESOLUTION_W * CAMERA_RESOLUTION_H * 4);

    // 从相机出来的原始图
    Bitmap sourceBitmap = Bitmap.createBitmap(CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, Bitmap.Config.ARGB_8888);
    // RESIZE之后的预览图
    Bitmap previewBitmap = Bitmap.createBitmap(CAMERA_RESOLUTION_W * RESIZE, CAMERA_RESOLUTION_H * RESIZE, Bitmap.Config.ARGB_8888);

    /*private int count;
    private long liveStartTime;*/

    private final IFrameCallback frameCallback = new IFrameCallback() {
        @Override
        public void onFrame(ByteBuffer byteBuffer) {
            // 测试帧率，可以根据实际需要决定是否保留
            /*count++;
            double spent = (System.currentTimeMillis() - liveStartTime) / 1000.0;
            if (spent > 1) {
                Log.i(Log.TAG, "视频帧率：" +  Math.round(count / spent));
                // 测试 设置准确的帧率后台就没有延迟了
                if (++count != getVideoCodec(streamType).frame) {
                    getVideoCodec(streamType).frame = count++;
                }
                count = 0;
                liveStartTime = System.currentTimeMillis();
            }*/

            try {
                if (isLiving() || isRecording()) { synchronized (videoFrame.data) {
                        byteBuffer.asReadOnlyBuffer().get(videoFrame.data, 0, videoFrame.data.length);
                        videoFrame.noTrans();
                        videoFrame.isReady = true;
                        videoFrame.data.notify();
                    }
                }
                if (startPhoto) {
                    // 等待3秒抓取图片
                    if (!isLiving() && System.currentTimeMillis() - startPhotoTime < 5 * PERIOD_SECOND) {
                        //Log.i(Log.TAG, "等待5秒，" + (System.currentTimeMillis() - startPhotoTime) / PERIOD_SECOND);
                        return;
                    }

                    synchronized (photoFrame.data) {
                        byteBuffer.get(photoFrame.data, 0, photoFrame.data.length);
                        photoFrame.noTrans();
                        photoFrame.isReady = true;
                        photoFrame.data.notify();
                    }
                    startPhoto = false;
                }
            } catch (Exception e) {
                Log.i(Log.TAG, "onFrame异常：" + e);
            }
        }
    };

    public IRayDev(int ID, Context context, int highThres, int lowThres, int envAlarmThres, int cmpAlarmThres, boolean useAudio) { /////
        super(ID, context, useAudio); /////
        this.streamType = 0;
        this.type = DEVICE_USB_IRAY;
        this.globalAlarm = new IRTempAlarm(highThres, lowThres);
        this.envAlarmThres = envAlarmThres;
        this.cmpAlarmThres = cmpAlarmThres;
        this.useAudio = useAudio; /////
        Settings.VideoCodec videoCodec = new Settings.VideoCodec();
        videoCodec.frame = 20;
        videoCodec.iFrame = 20;
        videoCodec.resolution = 3; /////
        this.codec.put(String.valueOf(0), videoCodec);
    }

    // 子类内部也缓存了 useAudio，运行时切换时需要和父类状态保持一致。
    @Override
    public void setUseAudio(boolean useAudio) {
        super.setUseAudio(useAudio);
        this.useAudio = useAudio;
    }


    private void cameraInit(int stream) { /////
        try {
            IRRegionTemp.setTempUnit(sensorConfig.measureUnit);
            if (photoConfig.color == 0) {  // 色彩切换为黑白模式
                /////
                if (sensorConfig.color == 0) {
                    ircmd.changePalette(0);  // 使用白热伪彩
                } else if (sensorConfig.color == 1) {
                    ircmd.changePalette(1);  // 使用黑热伪彩
                } else {
                    ircmd.changePalette(0);  // 使用白热伪彩
                }
                /////
                Log.i(Log.TAG, "英睿红外色彩设置为黑白模式");
            } else if (photoConfig.color == 1) {  // 色彩切换为彩色模式
                ircmd.changePalette(sensorConfig.color);  // 使用伪彩
                switch (sensorConfig.color) {
                    case 0:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot);
                        break;
                    case 1:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.black_hot);
                        break;
                    case 2:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.iron_red);
                        break;
                    case 3:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.melt_stone);
                        break;
                    case 4:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.rainbow1);
                        break;
                    case 5:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot); //没有对应的铁灰色带，用白热代替
                        break;
                    case 6:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.iron_red); //没有对应的红热色带，用铁红代替
                        break;
                    case 7:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.rainbow2);
                        break;
                }
                Log.i(Log.TAG, "英睿红外色彩设置为彩色模式");
            }

            /////
            Point size = null;
            if (isPhotoing()) {
//            if (stream == -1) {
                size = Settings.PhotoConfig.getImageSize(photoConfig.size);
                sizeX = size.x;
                sizeY = size.y;
                Log.i(Log.TAG, "英睿红外图像分辨率设置为" + (int) sizeX + "x" + (int) sizeY);
            } else if (isLiving() || isRecording()) {
                size = Settings.VideoCodec.getResolution(codec.get(String.valueOf(stream)).resolution);
                if (codec.get(String.valueOf(stream)).resolution > 11) {
                    sizeX = 1600;
                    sizeY = 1200;
                } else {
                    sizeX = size.x;
                    sizeY = size.y;
                }
                Log.i(Log.TAG, "英睿红外视频分辨率设置为" + (int) sizeX + "x" + (int) sizeY);
            }
            tempPaint = new Paint();
            tempPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            tempPaint.setStyle(Paint.Style.FILL);

            tempPaint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));
            tempPaint.setAntiAlias(false);
            tempPaint.setColor(0xFF00AB00);
            timePaint = new Paint();

            timePaint.setStrokeWidth(1 * sizeX / 640);
            timePaint.setStyle(Paint.Style.FILL);
            timePaint.setAntiAlias(true);

            timePaint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));
            timePaint.setColor(Color.WHITE);
            /////
            uvcCamera.setBrightness(photoConfig.brightness);
            Log.i(Log.TAG, "英睿红外亮度设置为" + photoConfig.brightness);
            uvcCamera.setContrast(photoConfig.contrast);
            Log.i(Log.TAG, "英睿红外对比度设置为" + photoConfig.contrast);
            uvcCamera.setSaturation(photoConfig.saturation);
            Log.i(Log.TAG, "英睿红外饱和度设置为" + photoConfig.saturation);

            IRCMD.CalibrationParam param = ircmd.getCalibrationParam();
            if (param.objDist != Math.round(sensorConfig.distance)) {
                ircmd.setDistance((short) Math.round(sensorConfig.distance));
            }
            if (Math.abs(param.envTemp - sensorConfig.envirTemp) > 1e-2) {
                ircmd.setEnvirTemp(sensorConfig.envirTemp);
            }
            // 发射率必须大于0.5，否则不按常规设置如设置为0，读取的温度值会很大
            if (sensorConfig.emissivity > 0.5 &&
                    Math.abs(param.objEmis - sensorConfig.emissivity) > 1e-2) {
                ircmd.setEmissivity(sensorConfig.emissivity);
            }
            if (Math.abs(param.refTemp - sensorConfig.reflectTemp) > 1e-2) {
                ircmd.setReflection(sensorConfig.reflectTemp);
            }
            if (Math.abs(param.envHumi - sensorConfig.envirHumi) > 1e-2) {
                ircmd.setHumidity(sensorConfig.envirHumi);
            }
            if (Math.abs(param.tempComp - sensorConfig.tempCompensate) > 1e-2) {
                /////
                if (sensorConfig.tempCompensate >= -20 && sensorConfig.tempCompensate <= 20 && sensorConfig.measureUnit == 0) {
                    ircmd.setCompensation(sensorConfig.tempCompensate);
                }
                /////
            }
            Log.i(Log.TAG, ircmd.getCalibrationParam().toString());
        } catch (Exception e) {
            Log.i(Log.TAG, "英睿红外设备初始化异常：" + e);
        }
    }

    @Override
    public void setTempRegion(int channel, int preset, Vector<IRSetting.IrRegionInfo> regions, boolean live) {
        if (channel != id) return;

        int index = 1;
        if (live && isLiving()) { //主站请求手动测温
            liveRegionsTemp.clear();
            for (IRSetting.IrRegionInfo irRegion : regions) {
                // 单独存储环境温度区域
                if (irRegion.irRegion.cls == 2 || irRegion.emissivity == 0) {
                    presetEnvTemp = preset;
                    regionEnvTemp = new IRRegionTemp(context, irRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, 0, RESIZE);
                } else {
                    IRRegionTemp regionTemp = new IRRegionTemp(context, irRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, index++, RESIZE);
                    // 视频预览画区域会累加进来
                    liveRegionsTemp.add(regionTemp);
                }
                Log.i(Log.TAG, "英睿红外手动设置测温区域：" + irRegion.toString());
            }
        } else {
            Vector<IRRegionTemp> irRegionTempVector = new Vector<>();
            Log.i(Log.TAG, "英睿红外设置预置位" + preset + "测温区域：");
            for (IRSetting.IrRegionInfo irRegion : regions) {
                // 单独存储环境温度区域
                if (irRegion.irRegion.cls == 2 || irRegion.emissivity == 0) {
                    presetEnvTemp = preset;
                    regionEnvTemp = new IRRegionTemp(context, irRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, 0, RESIZE);
                } else {
                    IRRegionTemp regionTemp = new IRRegionTemp(context, irRegion, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, index++, RESIZE);
                    irRegionTempVector.add(regionTemp);
                }
                Log.i(Log.TAG, irRegion.toString());
            }
            // 图片画区域会替代之前预置位的值
            staticRegionsMaps.put(preset, irRegionTempVector);
        }
    }

    private final ITemperatureCallback temperatureCallback = new ITemperatureCallback() {
        @Override
        public void onReceiveTemperature(float[] temperature) {
            // 环境温度区域中的最高温，与控制器的温度进行比较，取大的作为环境温度
            if (regionEnvTemp != null) {
                // 只获取设置了环境温度区域的预置位的区域温度
                if (curPreset == presetEnvTemp) {
                    regionEnvTemp.setTempRaw(temperature);
                    MainActivity.tempEnvRegion = regionEnvTemp.getTemperature().maxTemperature;
                    MainActivity.tempEnvironment = Math.max(MainActivity.tempEnvControl, MainActivity.tempEnvRegion);
                }
            }
            // 防灼伤保护，使用全局测温距离来校正全局最高温
            irOverProtect.avoidOverexposure(temperature[8]);
            // 设置全局温度数据
            if (globalTemp != null) {
                globalTemp.setTempRaw(temperature);
            }
            // 更新动态测温区域
            for (IRRegionTemp region : liveRegionsTemp) {
                region.setTempRaw(temperature);
            }



            // 更新当前预置位设置的测温区域的温度值
            Vector<IRRegionTemp> staticRegions = staticRegionsMaps.get(curPreset);
            if (staticRegions != null) {
//                Log.i(Log.TAG, "预置位" + curPreset + "设置了" + staticRegions.size()+ "个测温区");
                for (IRRegionTemp region : staticRegions) {
                    region.setTempRaw(temperature);
                }
            }
            //Log.i(Log.TAG, String.format("机芯温度：%f，快门温度：%f", temperature[7], temperature[9]));
        }
    };

    private void startTemp(int preset) {
        // 不做延时处理，读不到温度值，SDK比较恶心，升级了库文件问题似乎没有了
        SystemClock.sleep(1 * PERIOD_SECOND);
        Log.i(Log.TAG, "开始读取温度值");
        if (uvcCamera != null) {
            curPreset = preset;
            uvcCamera.setTemperatureCallback(temperatureCallback);
            uvcCamera.startTemp();
        }
    }

    private void stopTemp() {
        Log.i(Log.TAG, "英睿红外停止读取温度值");
        if (uvcCamera != null) uvcCamera.stopTemp();
    }

    private void startPreview(int stream) { /////


        Log.i(Log.TAG, "英睿红外开始预览");
        try {
        /*uvcCamera.setPreviewSize(CAMERA_RESOLUTION_WIDTH, CAMERA_RESOLUTION_HEIGHT + 4,
                1, 25, 0, UVCCamera.DEFAULT_BANDWIDTH, 0);*/
            uvcCamera.startPreview();
            uvcCamera.setFrameCallback(frameCallback, UVCCamera.PIXEL_FORMAT_RGBX);
            uvcCamera.startCapture();
            cameraInit(stream); /////
            scheduleShutter(sensorConfig.shutterInterval);
        } catch (Exception e) {
            Log.i(Log.TAG, "英睿红外预览异常：" + e);
        }
    }

    //每隔n分钟打一次快门
    private Timer timerEveryTime;

    private void scheduleShutter(int minutes) {
        int period = (minutes > 0 && minutes <= 5) ? minutes : 2;
        timerEveryTime = new Timer();
        timerEveryTime.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    ircmd.shutterCalibration();
                } catch (Exception e) {
                }
            }
        }, 1000, period * PERIOD_MINUTE);
    }

    private void stopPreview() {
        Log.i(Log.TAG, "英睿红外停止预览");
        // 这里不能调用stopPreview，否则调用close会奔溃，搞死
        // 1.4版本升级了库文件之后这里必需要调用stopPreview，否则也会奔溃
        if (uvcCamera != null) uvcCamera.stopPreview();

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

        IRRegionTemp.TemperatureSampleResult temperature = globalTemp.getTemperature();
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
            alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), 10 * (float) bitmap.getWidth() / 640, 60 * (float) bitmap.getHeight() / 512); /////
            return true;
        }
        return false;
    }

    private void alarmProcess(int preset, Bitmap bitmap, EnumSet<IRTempAlarm.AlarmType> types,
                              List<Settings.ObjectInfo> objects, Settings.FireAlarmInfo alarmInfo, float x, float y) { /////
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
    private boolean doTempRegionAlarmWithThreshold(int preset, Bitmap bitmap, long timestamp, Vector<IRRegionTemp> irRegionTemps, float x, float y) { /////
        if (irRegionTemps == null || irRegionTemps.isEmpty()) return false;

        EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.noneOf(IRTempAlarm.AlarmType.class);
        List<Settings.ObjectInfo> objects = new ArrayList<>();
        for (IRRegionTemp t : irRegionTemps) {
            IRRegionTemp.TemperatureSampleResult temperature = t.getTemperature();

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
    private boolean doTempRegionAlarmWithCompare(int preset, Bitmap bitmap, long timestamp, Vector<IRRegionTemp> irRegionTemps, float x, float y) { /////
        if (irRegionTemps == null || irRegionTemps.isEmpty() || irRegionTemps.size() < 2) return false;

        // 分类存储区域，按cls分类
        HashMap<Byte, List<IRRegionTemp>> groupedRegions = new HashMap<>();
        for (IRRegionTemp region : irRegionTemps) {
            byte cls = region.tempRegion().cls; // 获取区域类型cls
            groupedRegions.computeIfAbsent(cls, k -> new ArrayList<>()).add(region);
        }
        // 存储所有需要生成告警的cls值
        Set<Byte> alertCls = new HashSet<>();
        // 遍历每个分类
        for (HashMap.Entry<Byte, List<IRRegionTemp>> entry : groupedRegions.entrySet()) {
            byte cls = entry.getKey(); // 当前分类的cls值
            List<IRRegionTemp> regions = entry.getValue();

            // 如果分类内的区域少于2个，跳过
            if (regions.size() < 2) continue;

            int len = regions.size();
            float[] maxTemperatures = new float[len];
            for (int i = 0; i < len; i++) {
                IRRegionTemp t = regions.get(i);
                IRRegionTemp.TemperatureSampleResult temperature = t.getTemperature();
                maxTemperatures[i] = temperature.maxTemperature;
                Log.i(Log.TAG, "红外类型[" + cls + "]区域[" + i + "]最高温度：" + maxTemperatures[i]);
            }
            // 升序排序
            Arrays.sort(maxTemperatures);

            if (maxTemperatures[len - 1] - maxTemperatures[0] > cmpAlarmThres) {
                Log.i(Log.TAG, String.format("红外测温区域：%d，最大温差：%.2f，超过设定阈值：%d",
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
                Log.i(Log.TAG, "红外生成电缆终端和避雷器三相不平衡告警");
            } else if (alertCls.contains((byte) 0) && alertCls.contains((byte) -1)) {
                // 如果有cls为0和cls为-1的区域，生成电缆终端三相不平衡告警
                EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.of(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE_0);
                alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), x, y);
                Log.i(Log.TAG, "红外生成电缆终端三相不平衡告警");
            } else if (alertCls.contains((byte) 0)) {
                // 如果只有cls为0的区域，生成ALARM_COM_TEMPERATURE_0告警
                EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.of(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE_0);
                alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), x, y);
                Log.i(Log.TAG, "红外生成电缆终端三相不平衡告警");
            } else if (alertCls.contains((byte) 1) && alertCls.contains((byte) -1)) {
                // 如果有cls为1和cls为-1的区域，生成避雷器三相不平衡告警
                EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.of(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE_1);
                alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), x, y);
                Log.i(Log.TAG, "红外生成避雷器三相不平衡告警");
            } else if (alertCls.contains((byte) 1)) {
                // 如果只有cls为1的区域，生成ALARM_COM_TEMPERATURE_1告警
                EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.of(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE_1);
                alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), x, y);
                Log.i(Log.TAG, "红外生成避雷器三相不平衡告警");
            } else if (alertCls.contains((byte) -1) && !alertCls.contains((byte) 0) && !alertCls.contains((byte) 1)) {
                // 如果只有cls为-1的区域，生成三相不平衡告警
                EnumSet<IRTempAlarm.AlarmType> alarmTypes = EnumSet.of(IRTempAlarm.AlarmType.ALARM_COM_TEMPERATURE);
                alarmProcess(preset, bitmap, alarmTypes, objects, IRTempAlarm.GetAlarmInfo(), x, y);
                Log.i(Log.TAG, "红外生成三相不平衡告警");
            }
            return true;
        }
        return false;
    }


    // 区域温度和环境温度之间的温差比较
    private boolean doTempRegionAlarmWithEnvironment(int preset, Bitmap bitmap, long timestamp, Vector<IRRegionTemp> irRegionTemps, float envTemp, float x, float y) { /////
        if (irRegionTemps == null || irRegionTemps.isEmpty()) return false;

        for (IRRegionTemp t : irRegionTemps) {
            IRRegionTemp.TemperatureSampleResult temperature = t.getTemperature();

            Date now = new Date();
            if (MainActivity.tempEnvRegion != 0 || (now.before(dateFromString("08:00:00")) && now.after(dateFromString("20:00:00")))) {
                if (temperature.maxTemperature > envTemp + envAlarmThres) {
                    Log.i(Log.TAG, String.format("红外环境温度：%.2f，最高温度：%.2f，超过设定阈值：%d，生成温升告警",
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

    private void doTempReport(int preset, long timestamp, Vector<IRRegionTemp> irRegionTemps) {
        if (irRegionTemps == null || irRegionTemps.isEmpty()) return;

        Vector<IRSetting.IrRegionTemp> regionTemps = new Vector<>();
        for (IRRegionTemp t : irRegionTemps) {
            IRSetting.IrRegionTemp result = new IRSetting.IrRegionTemp();
            IRRegionTemp.TemperatureSampleResult temperature = t.getTemperature();
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

    private void doTempCallback(int preset, Bitmap bitmap, long timestamp, HashMap<Integer, Vector<IRRegionTemp>> regionsMaps) {
        Vector<IRRegionTemp> irRegionTemps = regionsMaps.get(preset);

        boolean globalAlarmed = false;
        if (iRSetting.globalAlarm == 1) {
            globalAlarmed = doGlobalAlarm(preset, bitmap, timestamp);
        }

        if (irRegionTemps != null) {
            doTempReport(preset, timestamp, irRegionTemps);
            // 根据开关选择报警
            boolean thresholdAlarmed = false;
            if (iRSetting.thresholdAlarm == 1) {
                if (globalAlarmed) {
                    thresholdAlarmed = doTempRegionAlarmWithThreshold(preset, bitmap, timestamp, irRegionTemps, 10 * (float) bitmap.getWidth() / 640, 80 * (float) bitmap.getHeight() / 512); /////
                } else {
                    thresholdAlarmed = doTempRegionAlarmWithThreshold(preset, bitmap, timestamp, irRegionTemps, 10 * (float) bitmap.getWidth() / 640, 60 * (float) bitmap.getHeight() / 512); /////
                }
            }
            boolean envAlarmed = false;
            if (iRSetting.envAlarm == 1) {
                if (globalAlarmed && thresholdAlarmed) {
                    envAlarmed = doTempRegionAlarmWithEnvironment(preset, bitmap, timestamp, irRegionTemps, MainActivity.tempEnvironment, 10 * (float) bitmap.getWidth() / 640, 100 * (float) bitmap.getHeight() / 512); /////
                } else if (globalAlarmed || thresholdAlarmed) {
                    envAlarmed = doTempRegionAlarmWithEnvironment(preset, bitmap, timestamp, irRegionTemps, MainActivity.tempEnvironment, 10 * (float) bitmap.getWidth() / 640, 80 * (float) bitmap.getHeight() / 512); /////
                } else {
                    envAlarmed = doTempRegionAlarmWithEnvironment(preset, bitmap, timestamp, irRegionTemps, MainActivity.tempEnvironment, 10 * (float) bitmap.getWidth() / 640, 60 * (float) bitmap.getHeight() / 512); /////
                }
            }
            boolean comAlarmed = false;
            if (iRSetting.comAlarm == 1) {
                if (globalAlarmed && thresholdAlarmed && envAlarmed) {
                    comAlarmed = doTempRegionAlarmWithCompare(preset, bitmap, timestamp, irRegionTemps, 10 * (float) bitmap.getWidth() / 640, 120 * (float) bitmap.getHeight() / 512); /////
                } else if ((globalAlarmed && thresholdAlarmed) || (globalAlarmed && envAlarmed) || (thresholdAlarmed && envAlarmed)) {
                    comAlarmed = doTempRegionAlarmWithCompare(preset, bitmap, timestamp, irRegionTemps, 10 * (float) bitmap.getWidth() / 640, 100 * (float) bitmap.getHeight() / 512); /////
                } else if ((globalAlarmed || thresholdAlarmed || envAlarmed)) {
                    comAlarmed = doTempRegionAlarmWithCompare(preset, bitmap, timestamp, irRegionTemps, 10 * (float) bitmap.getWidth() / 640, 80 * (float) bitmap.getHeight() / 512); /////
                } else {
                    comAlarmed = doTempRegionAlarmWithCompare(preset, bitmap, timestamp, irRegionTemps, 10 * (float) bitmap.getWidth() / 640, 60 * (float) bitmap.getHeight() / 512); /////
                }
            }
        }
    }

    /**
     * 避免usb摄像头卡死，拍照之前启动定时器，拍照完成销毁，如拍照不能正常结束，
     * 定时器超时会进入callback处理函数，重启apk
     * */
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
            //Log.i(Log.TAG, "关闭红外机芯");
            if (irdev != null) {
                irdev.disconnect();
                irdev = null;
            }
            if (uvcCamera != null) {
                // 这里调用这个函数，前面就不能调stopPreview函数，否则会崩溃，sdk里有问题
                uvcCamera.destroy();
                uvcCamera = null;
            }
            clearState();
        } catch (Exception e) {
            Log.i(Log.TAG, "关闭红外机芯异常");
        }
    }

    private USBMonitor.OnDeviceConnectListener devConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice usbDevice) {
        }

        @Override
        public void onConnect(UsbDevice usbDevice, USBMonitor.UsbControlBlock usbControlBlock, boolean b) {
            Log.i(Log.TAG, "连接红外机芯：" + usbDevice.getProductName());
            /////
            String[] parts = usbDevice.getProductName().split("-");
            String resolution = parts[parts.length - 2];
            String focalLen = parts[parts.length - 1];
            if ("317".equals(resolution)) {
                iRSetting.resolution = 0;
            } else if ("612".equals(resolution)) {
                iRSetting.resolution = 1;
            }
            if ("40".equals(focalLen)) {
                iRSetting.focalLen = 0;
            } else if ("68".equals(focalLen)) {
                iRSetting.focalLen = 1;
            } else if ("13".equals(focalLen)) {
                iRSetting.focalLen = 2;
            }
            saveSettings(iRSetting, MainActivity.IR_SETTING_FILE);
            /////
            try {
                // usb线信号很弱的情况下，会频繁打开和关闭红外机芯，由于安卓系统会通过广播监测设备，这样会导致
                // 和应用层有时序上的不同步。调用clone，隔离和避免应用层和库里同时操作同一资源导致的奔溃。
                usbControler = usbControlBlock.clone();
            } catch (CloneNotSupportedException e) {
                Log.i(Log.TAG, "机芯连接异常：" + e);
            }
        }

        @Override
        public void onDisconnect(UsbDevice usbDevice, USBMonitor.UsbControlBlock usbControlBlock) {
            Log.i(Log.TAG, "断开红外机芯：" + usbDevice.getProductName());
            usbControler = null;
            // 摄像头偶尔会莫名其妙的自动断开，这里释放资源
            //close();
        }

        @Override
        public void onDettach(UsbDevice usbDevice) {
        }

        @Override
        public void onCancel(UsbDevice usbDevice) {
        }
    };

    private boolean openCamera() {
        if (isOpened()) {
            Log.i(Log.TAG, "红外机芯已打开");
            return true;
        }
        try {
            usbControler = null;
            uvcCamera = new UVCCamera(0);
            irdev = new IRUVC384(context, devConnectListener);
            irdev.connect();
            for (int i = 0; i < 5 && (usbControler == null); i++) {
                SystemClock.sleep(PERIOD_SECOND);
            }
            if (usbControler == null) {
                Log.i(Log.TAG, "红外机芯打开失败");
                return false;
            }
            uvcCamera.open(usbControler);
            ircmd = new IRCMD(uvcCamera);
            ircmd.setOutputMode(IRCMD.VideoMode.MODE_8004); // 输出RGBA模式
            ircmd.setTemperatureNormal();
            irOverProtect = new IROverProtect(ircmd, OVER_EXPOSURE_THRESHOLD);
            globalTemp = new IRRegionTemp(context, CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, 0, RESIZE);
            setState(DevState.OPENED);
            return true;
        } catch (Exception e) {
            Log.i(Log.TAG, "打开红外机芯异常：" + e);
            return false;
        }
    }


    @Override
    public boolean open(int stream, onOpenCallback cb, int timeoutSeconds, boolean waitSelfCheck) {
        //Log.i(Log.TAG, "开启红外机芯");
        try {
            enter(timeoutSeconds + 60); // 打开设备超时认为卡死，重启apk
            setState(DevState.OPENING);
            closeForce = false;

            long startUpTime = System.currentTimeMillis();
            boolean isOpen;
            do {
                if (closeForce) return false;
                isOpen = openCamera();
                if (ptzDev.isDeviceReady() && isOpen) break;
                SystemClock.sleep(PERIOD_SECOND);
            } while (System.currentTimeMillis() - startUpTime < timeoutSeconds * PERIOD_SECOND);

            if (waitSelfCheck) {
                // 设备至少启动1分钟，太少有可能机芯或云台没有完成自检
                long elapse = (System.currentTimeMillis() - startUpTime);
                if (MIN_BOOTUP_MILISECONDS - elapse > 0) SystemClock.sleep(MIN_BOOTUP_MILISECONDS - elapse);
            }

            if (!isOpen) {
                Log.i(Log.TAG, "开启红外机芯失败，无法识别设备");
                closeCamera();
                if (deviceConfig.chargeControl == 6) {
                    // 如果是汇能精电控制器并且使用GPIO分别控制云台与红外，就重启红外
                    final Device device = this;
                    controllerCallback.onCameraBlocked(device);
                    // 重启usb外设
                    // if (cb != null) cb.openFailed(0);
                } else {
                    // 这里不重启负载，否则会影响可见光机芯拍照，也会反复重启，等待晚上拍照重启或硬重启就好了
                    // controllerCallback.onCameraBlocked(stream + 1);
                    // 此时要重启usb外设才能起作用，执行回调函数
                    if (cb != null) cb.openFailed(0);
                }
                return false;
            }

            clearState(DevState.OPENING);

            Log.i(Log.TAG, "打开红外机芯耗时 " + (System.currentTimeMillis() - startUpTime) / 1000 + " 秒");
            if (cb != null) cb.openSucceed();
            return true;
        } catch (Exception e) {
            Log.i(Log.TAG, "打开红外机芯异常：" + e);
            Log.i(Log.TAG, "重启apk");
            restartApplication(context, 0);
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
            //Log.i(Log.TAG, "红外机芯关闭");
        } catch (Exception e) {
            Log.i(Log.TAG, "红外机芯关闭异常：" + e);
        }
        return true;
    }

    private String getOSDText() {
        //IRRegionTemp.TemperatureSampleResult result = globalTemp.getTemperature();

        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        //String temp = String.format("Tmax:%.1f°C Tmin:%.1f°C", result.maxTemperature, result.minTemperature);
        return time;// + " " + temp;
    }

    /**
     * 绘制图片左上角水印
     * */
    @Override
    protected void drawWatermark(@NonNull Bitmap bitmap) {
        Canvas canvas = new Canvas(bitmap);
        if (canvas != null) {
            if (System.currentTimeMillis() - drawOSDTime >= PERIOD_SECOND) {
                osdText = getOSDText();
                drawOSDTime = System.currentTimeMillis();
            }

            if (osd.time == 1) {
//                canvas.drawText(osdText, 10, 20, timePaint);
                canvas.drawText(osdText, 10 * (float) bitmap.getWidth() / 640, 20 * (float) bitmap.getHeight() / 512, timePaint);
            } else {
//                canvas.drawText("", 10, 20, timePaint);
                canvas.drawText("", 10 * (float) bitmap.getWidth() / 640, 20 * (float) bitmap.getHeight() / 512, timePaint);
            }
            if (osd.tag == 1) {
//                canvas.drawText(osd.text, 10, 475, timePaint);
                /////
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
//            canvas.drawText("防灼伤保护", 150 * RESIZE, 120 * RESIZE, timePaint);
            canvas.drawText("防灼伤保护", 150 * (float) bitmap.getWidth() / 640, 120 * (float) bitmap.getHeight() / 512, timePaint); /////
        }
        return bitmap;
    }


    /**
     * 绘制调色板和最高/最低温度
     * */
    private void drawPalette(@NonNull Bitmap bitmap) {
        if (osdPalette != null) {
            Canvas canvas = new Canvas(bitmap);

//            Rect dest = new Rect(bitmap.getWidth() - 16, 40, bitmap.getWidth(), bitmap.getHeight() - 40);
            Rect dest = new Rect(bitmap.getWidth() - 16 * bitmap.getWidth() / 640, 40 * bitmap.getHeight() / 512, bitmap.getWidth(), bitmap.getHeight() - 40 * bitmap.getHeight() / 288); /////

            canvas.drawBitmap(osdPalette, null, dest, null);

            IRRegionTemp.TemperatureSampleResult result = globalTemp.getTemperature();
//            globalTemp.drawTemperature(canvas, result.maxTemperature, bitmap.getWidth() - 52, 55);
//            globalTemp.drawTemperature(canvas, result.minTemperature, bitmap.getWidth() - 52, bitmap.getHeight() - 45);
            globalTemp.drawTemperature(canvas, result.maxTemperature, bitmap.getWidth() - 55 * (float) bitmap.getWidth() / 640, 55 * (float) bitmap.getHeight() / 512, (float) bitmap.getWidth(), (float) bitmap.getHeight());  // 使用全局最高温与区域最高温比较后的最高温度
            globalTemp.drawTemperature(canvas, result.minTemperature, bitmap.getWidth() - 55 * (float) bitmap.getWidth() / 640, bitmap.getHeight() - 45 * (float) bitmap.getHeight() / 512, (float) bitmap.getWidth(), (float) bitmap.getHeight());  // 使用全局最低温与区域最低温比较后的最低温度
        }
    }

    private void drawHotTracker(@NonNull Bitmap bitmap) {
        Canvas canvas = new Canvas(bitmap);

        IRRegionTemp.TemperatureSampleResult result = globalTemp.getTemperature();
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

        Bitmap tracker = BitmapFactory.decodeResource(context.getResources(), R.drawable.target);
//        int x1 = result.maxTemperaturePixel.x >= 5 ? result.maxTemperaturePixel.x - 5 : result.maxTemperaturePixel.x;
//        int y1 = result.maxTemperaturePixel.y >= 5 ? result.maxTemperaturePixel.y - 5 : result.maxTemperaturePixel.y;
//        int x2 = result.maxTemperaturePixel.x < (CAMERA_RESOLUTION_W - 5) ? result.maxTemperaturePixel.x + 5 : result.maxTemperaturePixel.x;
//        int y2 = result.maxTemperaturePixel.y < (CAMERA_RESOLUTION_H - 5) ? result.maxTemperaturePixel.y + 5 : result.maxTemperaturePixel.y;
//        Rect rect = new Rect(x1 * RESIZE, y1 * RESIZE, x2 * RESIZE, y2 * RESIZE);
        /////
        int x1 = result.maxTemperaturePixel.x >= 10 ? (int) ((result.maxTemperaturePixel.x - 10) * sizeX / 640) : (int) (result.maxTemperaturePixel.x * sizeX / 640);
        int y1 = result.maxTemperaturePixel.y >= 10 ? (int) ((result.maxTemperaturePixel.y - 10) * sizeY / 512) : (int) (result.maxTemperaturePixel.y * sizeY / 512);
        int x2 = result.maxTemperaturePixel.x < (CAMERA_RESOLUTION_W - 10) ? (int) ((result.maxTemperaturePixel.x + 10) * sizeX / 640) : (int) (result.maxTemperaturePixel.x * sizeX / 640);
        int y2 = result.maxTemperaturePixel.y < (CAMERA_RESOLUTION_H - 10) ? (int) ((result.maxTemperaturePixel.y + 10) * sizeY / 512) : (int) (result.maxTemperaturePixel.y * sizeY / 512);
        Rect rect = new Rect(x1, y1, x2, y2);
        /////
        canvas.drawBitmap(tracker, null, rect, null);
    }


    private void drawTemperatures(@NonNull Bitmap bitmap, Vector<IRRegionTemp> showRegions) {
        if (showRegions == null || showRegions.isEmpty()) return;

        Canvas canvas = new Canvas(bitmap);
        float x = 10 * (float) bitmap.getWidth() / 640, y = 300 * (float) bitmap.getHeight() / 512;

        // 获取温度单位
        String unit = (sensorConfig.measureUnit == 0) ? "°C" : "°F";

        if (regionEnvTemp != null && MainActivity.tempEnvRegion != Float.NEGATIVE_INFINITY) {
//            canvas.drawText(String.format("[环境温度区域] %03.1f%s", MainActivity.tempEnvRegion, unit), x, y, tempPaint);
            y -= tempPaint.getTextSize() + 10 * (float) bitmap.getHeight() / 512;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            showRegions.sort((o1, o2) -> o1.index < o2.index ? 1 : -1);
        }
        for (IRRegionTemp region : showRegions) {

//            Log.e(Log.TAG,"region.getTemperature().maxTemperature"+region.getTemperature().maxTemperature);

//            canvas.drawText(String.format("[%d] %03.1f%s", region.index, region.getTemperature().maxTemperature, unit), x, y, tempPaint);
            canvas.drawText(String.format("[%d] %03.1f%s", region.index, convertTemperatureIfNeeded(region.getTemperature().maxTemperature), unit), x, y, tempPaint);

            y -= tempPaint.getTextSize() + 10 * (float) bitmap.getHeight() / 288;
        }
    }



    private float convertTemperatureIfNeeded(float temperature) {

        if (IRRegionTemp.unitTemperature == 1) {
            // 需要转换为华氏度
            return (float) celsiusToFahrenheit(temperature);
        } else {
            // 保持摄氏度
            return temperature;
        }
    }

    public  double celsiusToFahrenheit(double celsius) {
        return (celsius * 9 / 5) + 32;
    }




    private void drawTempRegion(int preset, @NonNull Bitmap bitmap, IRRegionTemp.RegionType type) {
        try {
            // 每秒钟读取一次温度值

            boolean upload = (System.currentTimeMillis() - drawRegionTempTime >= PERIOD_SECOND);
            // 绘制手动下发的测温区域
            switch (type) {
                case REGION_LIVE:
                    for (IRRegionTemp region : liveRegionsTemp) {
                        if (upload) {
                            region.getTemperature();
                        }
                        region.drawRegionTemp(bitmap, sensorConfig.hotTracker);
                    }
                    drawTemperatures(bitmap, liveRegionsTemp);
                    if (regionEnvTemp != null) {
                        // 只绘制设置了环境温度区域的预置位
                        if (preset == presetEnvTemp) {
                            regionEnvTemp.drawRegionTemp(bitmap, sensorConfig.hotTracker);  // 绘制环境温度区域
                        }
                    }
                    break;
                case REGION_STATIC:
                    Vector<IRRegionTemp> regions = staticRegionsMaps.get(preset);
                    if (regions != null) {
                        for (IRRegionTemp region : regions) {
                            if (upload) {

                                region.getTemperature();

                            }
                            region.drawRegionTemp(bitmap, sensorConfig.hotTracker);
                        }
                        drawTemperatures(bitmap, regions);
                    }
                    if (regionEnvTemp != null) {
                        // 只绘制设置了环境温度区域的预置位
                        if (preset == presetEnvTemp) {
                            regionEnvTemp.drawRegionTemp(bitmap, sensorConfig.hotTracker);  // 绘制环境温度区域
                        }
                    }
                    break;
            }

            if (upload) drawRegionTempTime = System.currentTimeMillis();
        } catch (Exception e) {
            Log.i(Log.TAG, "英睿红外绘制区域异常：" + e);
        }
    }

    private Bitmap captureBitmap(int preset, int recordPreset) throws Exception {
        startPhoto = false;
        photoFrame.isReady = false;
        setState(DevState.PHOTOING);
        if (!isLiving() && !isRecording()) {
            startPreview(-1); /////
            startTemp(preset);
        }
        else{
            startTemp(preset);///////////////
        }

        startPhotoTime = System.currentTimeMillis();

        /////
        if ((preset != 0 && preset != recordPreset) || (preset != 0 && preset == recordPreset && isLiving())) {
//        if (preset != 0 && preset != recordPreset) {  // 如果拉流时转动了云台，再拍照，则不会转回拍照预置位，存在问题！
//        if (preset != 0) {
            move(2, preset);
            SystemClock.sleep(PTZ_PRESET_MOVE_TIME);    // 等待到达预置位
        } else if (preset != 0) {
            move(2, preset);
        }
        /////
        // 如果有环境温度区域，就需要先等待环境温度的获取
        if (regionEnvTemp != null) {
            SystemClock.sleep(5 * PERIOD_SECOND);
        }
        // 转动到预置位再拍照
        startPhoto = true;

        //// 防止在拉流的时候进行拍照，导致图片的分辨率使用的是视频的分辨率
        Point size = null;
        size = Settings.PhotoConfig.getImageSize(photoConfig.size);

        tempPaint = new Paint();
        tempPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        tempPaint.setStyle(Paint.Style.FILL);

        tempPaint.setTextSize(16 * Math.min(size.x / 640, size.y / 512));
        tempPaint.setAntiAlias(false);
        tempPaint.setColor(0xFF00AB00);
        timePaint = new Paint();

        timePaint.setStrokeWidth(1 * size.x / 640);
        timePaint.setStyle(Paint.Style.FILL);
        timePaint.setAntiAlias(true);

        timePaint.setTextSize(16 * Math.min(size.x / 640, size.y / 512));
        timePaint.setColor(Color.WHITE);
        ///////


        Bitmap bitmap = null;
        synchronized (photoFrame.data) {
            photoFrame.data.wait(10 * PERIOD_SECOND);
            if (photoFrame.isReady) {
                if (!photoFrame.isBlack()) {
                    bitmap = Bitmap.createBitmap(CAMERA_RESOLUTION_W, CAMERA_RESOLUTION_H, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(photoFrame.getData()));
                    bitmap = Bitmap.createScaledBitmap(bitmap,
//                            CAMERA_RESOLUTION_W * RESIZE,
//                            CAMERA_RESOLUTION_H * RESIZE,
//                            (int) sizeX, /////
//                            (int) sizeY, /////
                            (int)size.x,
                            (int)size.y,
                            true);
                }
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
    public Bitmap takePhoto(int stream, int preset, String filename, boolean show, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert, byte imageStitch) { /////
        Bitmap bitmap;
        enter(60);
        try {
            if (openCamera() != true) {
                return null;
            }
            bitmap = captureBitmap(preset, recordPreset);
            /////
            if (bitmap == null) {
                Log.i(Log.TAG, "英睿红外拍照失败，未捕获到照片数据, 设备状态：" + Status());
            }
            /////
            if (!isLiving() && !isRecording()) {
                close();
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "英睿红外拍照异常：" + e);
            return null;
        } finally {
            exit();
        }
        return bitmap;
    }

    @Override
    public boolean takePhoto(int stream, int preset, boolean show, String filename, Bitmap pop, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert) {
        try {
            enter(60);
            Log.i(Log.TAG, "英睿红外拍照开始：" + filename);

            Bitmap bitmap = captureBitmap(preset, recordPreset);


            if (!isLiving() && !isRecording()) {
                close();
            }

            if (bitmap == null) {
                Log.i(Log.TAG, "英睿红外拍照失败，未捕获到照片数据, 设备状态：" + Status());
            } else {
                drawWatermark(bitmap);
                if (sensorConfig.onPalette == 1) {
                    drawPalette(bitmap);
                }
                if (sensorConfig.hotTracker == 1) {
                    drawHotTracker(bitmap);
                }
                if (irOverProtect.getMode() == IROverProtect.ExposureMode.Protection) {
                    bitmap = drawProtection(bitmap);
                }


                drawTempRegion(preset, bitmap, IRRegionTemp.RegionType.REGION_STATIC);


                long stamp = getTimestampFromFilename(filename);
                // 回传温度 高低温预警
                doTempCallback(preset, bitmap, stamp, staticRegionsMaps);
                if (show) controllerCallback.onFrame(bitmap);

                saveBitmapAsJPEG(bitmap, filename, 100);
                bitmap.recycle();  // 释放原始Bitmap内存

                controllerCallback.onPhotoTaked(stamp, this.id, preset, filename);
                return true;
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "英睿红外拍照异常：" + e);
        } finally {
            exit();
        }
        return false;
    }

    @Override
    public boolean takeVideo(final String filename, final int duration, int stream, boolean upload) {
        if (isRecording()) {
            Log.i(Log.TAG, "英睿红外录制短视频失败，正在录制中");
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
            Settings.VideoCodec vCodec = getVideoCodec(streamType);
            //initEncoder(stream, CAMERA_RESOLUTION_W * RESIZE, CAMERA_RESOLUTION_H * RESIZE);
            Point size = Settings.VideoCodec.getResolution(vCodec.resolution);
            if (codec.get(String.valueOf(stream)).resolution > 11) {
                sizeX = 1600;
                sizeY = 1200;
            } else {
                sizeX = size.x;
                sizeY = size.y;
            }
            /////
            super.videoStart(stream, filename, duration, upload);
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
            initVideoEncoder(stream, (int) sizeX, (int) sizeY,false); /////
            /////
            startPreview(stream);
            startTemp(0);

            new Thread(() -> {
                while (isRecording()) {
                    try {
                        synchronized (videoFrame.data) {
                            videoFrame.data.wait(10);
                            if (!videoFrame.isReady) {
                                continue;
                            } else {
                                sourceBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(videoFrame.data));
                                previewBitmap = Bitmap.createScaledBitmap(sourceBitmap,
//                                        CAMERA_RESOLUTION_W * RESIZE,
//                                        CAMERA_RESOLUTION_H * RESIZE,
                                        (int) sizeX, /////
                                        (int) sizeY, /////
                                        true);
                                videoFrame.isReady = false;
                            }
                        }
                        drawWatermark(previewBitmap);
                        if (sensorConfig.onPalette == 1) {
                            drawPalette(previewBitmap);
                        }
                        if (sensorConfig.hotTracker == 1) {
                            drawHotTracker(previewBitmap);
                        }
                        if (irOverProtect.getMode() == IROverProtect.ExposureMode.Protection) {
                            drawProtection(previewBitmap);
                        }
                        encode(previewBitmap);
                    } catch (Exception e) {
                        Log.i(Log.TAG, "英睿红外直播预览异常：" + e);
                    }
                }
            }).start();

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
                        Log.i(Log.TAG, "英睿红外文件不上传，修改文件为：" + (MainActivity.FILE_PATH + id + File.separator + finalFile.getName()));
                    }
                    controllerCallback.onVideoFinished(System.currentTimeMillis(), id, streamType, filename, upload);
                }
            }, (duration + 1) * 1000);  // 多1秒作为保险余地，不然可能录像时间不足
        } catch (Exception e) {
            videoStop();
            controllerCallback.onVideoFailed(id, filename);
            Log.e(Log.TAG, "英睿红外录制视频异常：" + e.getMessage());
        }
        return isRecording();
    }

    @Override
    public boolean videoStop() {
        try {
            stopTemp();
            stopPreview();
            close();
            super.videoStop();

            releaseMuxer();
            /////
            uninitVideoEncoder();
            if (useAudio) {
                uninitAudioEncoder();
            }
            /////
            Log.i(Log.TAG, "英睿红外机芯停止录制");
        } catch (Exception e) {
            Log.i(Log.TAG, "英睿红外机芯停止录制异常：" + e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    public boolean liveStart(int stream, int ssrc) {
        if (isLiving()) return true;

        setState(DevState.LIVING);
        getVideoCodec(streamType);
        /////
        //initEncoder(stream, CAMERA_RESOLUTION_W * RESIZE, CAMERA_RESOLUTION_H * RESIZE);
        Point size = Settings.VideoCodec.getResolution(codec.get(String.valueOf(stream)).resolution);
        if (codec.get(String.valueOf(stream)).resolution > 11) {
            sizeX = 1600;
            sizeY = 1200;
        } else {
            sizeX = size.x;
            sizeY = size.y;
        }
        initVideoEncoder(stream, (int) sizeX, (int) sizeY,false); /////
        rtph264 = new RTPH264(ssrc);
        startPreview(stream); /////
        startTemp(0);

        this.ssrcLive = ssrc;
        this.streamType = stream;
        liveRegionsTemp.clear();

        new Thread(()->{
            long curTime = 0;
            //int frameCnt = 0;
            while(isLiving()) {
                try {
                    synchronized (videoFrame.data) {
                        videoFrame.data.wait(10);
                        if (!videoFrame.isReady) {
                            continue;
                        } else {
                            sourceBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(videoFrame.getData()));
                            previewBitmap = Bitmap.createScaledBitmap(sourceBitmap,
//                                    CAMERA_RESOLUTION_W * RESIZE,
//                                    CAMERA_RESOLUTION_H * RESIZE,
                                    size.x, /////
                                    size.y, /////
                                    true);
                            videoFrame.isReady = false;
                        }
                    }
                    drawWatermark(previewBitmap);

                    drawTempRegion(0, previewBitmap, IRRegionTemp.RegionType.REGION_LIVE);

                    if (sensorConfig.onPalette == 1) {
                        drawPalette(previewBitmap);
                    }

                    if (sensorConfig.hotTracker == 1) {
                        drawHotTracker(previewBitmap);
                    }

                    if (irOverProtect.getMode() == IROverProtect.ExposureMode.Protection) {
                        drawProtection(previewBitmap);
                    }

                    //controllerCallback.onFrame(previewBitmap);
                    encode(previewBitmap);

                    /*frameCnt++;
                    if (System.currentTimeMillis() - curTime >= PERIOD_SECOND) {
                        Log.i(Log.TAG, "视频发送帧率：" + frameCnt * PERIOD_SECOND / (System.currentTimeMillis() - curTime));
                        curTime = System.currentTimeMillis();
                        frameCnt = 0;
                    }*/
                    if (liveRegionsTemp.size() > 0 && (System.currentTimeMillis() - curTime >= PERIOD_MINUTE)) {
                        // 1分钟上报一次温度数据
                        doTempReport(0, System.currentTimeMillis(), liveRegionsTemp);
                        curTime = System.currentTimeMillis();
                    }
                } catch (Exception e) {
                    Log.i(Log.TAG, "直播预览异常：" + e);
                }
            }
        }).start();

        return true;
    }

    @Override
    public boolean liveStop() {
        try {
            stopTemp();
            stopPreview();
            close();

            rtph264 = null;
        } catch (Exception e) {
            Log.i(Log.TAG, "停止英睿红外直播异常：" + e);
        } finally {
            clearState(DevState.LIVING);
        }
        Log.i(Log.TAG, "停止英睿红外直播成功" );

        return true;
    }

    @Override
    public Settings.VideoCodec getVideoCodec(int streamType) {
        return codec.get(String.valueOf(streamType));
    }

    @Override
    protected boolean reboot() {
        Log.i(Log.TAG, "重启英睿红外机芯");
        close();
        final UVCCamera camera = uvcCamera;
        uvcCamera = null;
        SystemClock.sleep(200);
        camera.destroy();

        //init(CAMERA_RESOLUTION_WIDTH, CAMERA_RESOLUTION_HEIGHT, context);
        irdev.connect();

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
            if (osd.text != null && osd.text.startsWith("双光融合：")) {
                Log.i(Log.TAG, "设置双光融合参数");
                try {
                    // 提取参数部分并分割
                    String paramsPart = osd.text.substring("双光融合：".length()).trim();
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

    @Override
    public boolean setPhotoParam(Settings.PhotoConfig config) {
        super.setPhotoParam(config);
        /////
        // 获取图像分辨率参数
        Point size = Settings.PhotoConfig.getImageSize(config.size);
        sizeX = size.x;
        sizeY = size.y;
        Log.i(Log.TAG, "英睿红外图像分辨率设置为" + (int) sizeX + "x" + (int) sizeY);
        tempPaint = new Paint();
        tempPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        tempPaint.setStyle(Paint.Style.FILL);
        tempPaint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));
        tempPaint.setAntiAlias(false);
        tempPaint.setColor(0xFF00AB00);
        timePaint = new Paint();
        timePaint.setStrokeWidth(1 * sizeX / 640);
        timePaint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));
        timePaint.setStyle(Paint.Style.FILL);
        timePaint.setAntiAlias(true);
        timePaint.setColor(Color.WHITE);
        if (uvcCamera != null) {
            if (config.color == 0) {  // 色彩切换为黑白模式
                if (sensorConfig.color == 0) {
                    ircmd.changePalette(0);  // 使用白热伪彩
                } else if (sensorConfig.color == 1) {
                    ircmd.changePalette(1);  // 使用黑热伪彩
                } else {
                    ircmd.changePalette(0);  // 使用白热伪彩
                }
                Log.i(Log.TAG, "英睿红外色彩设置为黑白模式");
            } else if (config.color == 1) {  // 色彩切换为彩色模式
                ircmd.changePalette(sensorConfig.color);  // 使用伪彩
                /////
                switch (sensorConfig.color) {
                    case 0:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot);
                        break;
                    case 1:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.black_hot);
                        break;
                    case 2:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.iron_red);
                        break;
                    case 3:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.melt_stone);
                        break;
                    case 4:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.rainbow1);
                        break;
                    case 5:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot);  // 没有对应的铁灰色带，用白热代替
                        break;
                    case 6:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.iron_red);  // 没有对应的红热色带，用铁红代替
                        break;
                    case 7:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.rainbow2);
                        break;
                }
                Log.i(Log.TAG, "英睿红外色彩设置为彩色模式");
            }
            uvcCamera.setBrightness(config.brightness);
            Log.i(Log.TAG, "英睿红外亮度设置为" + config.brightness);
            uvcCamera.setContrast(config.contrast);
            Log.i(Log.TAG, "英睿红外对比度设置为" + config.contrast);
            uvcCamera.setSaturation(config.saturation);
            Log.i(Log.TAG, "英睿红外饱和度设置为" + config.saturation);
        } else {
            Log.e(Log.TAG, "英睿红外尚未初始化，无法实时设置图像参数");
        }
        return true; }

    @Override
    public boolean setRecordTimes(List<Settings.VideoTimeItem> list) {
        return true;
    }

    @Override
    protected void doSampleData(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo, int outIndex) { /////
        try {
            if (isRecording()) {
                if (mediaMuxer == null) {
                    ByteBuffer sps = mediaCodec.getOutputFormat().getByteBuffer("csd-0"); /////
                    ByteBuffer pps = mediaCodec.getOutputFormat().getByteBuffer("csd-1"); /////
                    // 获取到sps pps才能初始化Muxer
                    initMuxer(sps, pps);
                }
                if (mediaMuxer != null) {
                    mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo); /////
                }
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "英睿红外录制视频文件异常：" + e);
        }
    }

    public void initMuxer(ByteBuffer sps, ByteBuffer pps) throws IOException {
        Settings.VideoCodec vCodec = getVideoCodec(streamType);
//        Point size = new Point(CAMERA_RESOLUTION_W * RESIZE, CAMERA_RESOLUTION_H * RESIZE);
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
        // 写入编码数据之前需要配置视频头部信息(csd参数)，csd全称Codec-specific Data，对于H。264来说，
        // “csd-0”和"csd-1"分别对应sps和pps；对于AAC来说，"csd-0"对应ADTS。
//        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.x, size.y);
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
        Log.i(Log.TAG, "英睿红外机芯参数设置：" + config.toString());
        /////
        try {
            IRRegionTemp.setTempUnit(sensorConfig.measureUnit);
            if (photoConfig.color == 0) {  // 色彩切换为黑白模式
                if (sensorConfig.color == 0) {
                    ircmd.changePalette(0);  // 使用白热伪彩
                } else if (sensorConfig.color == 1) {
                    ircmd.changePalette(1);  // 使用黑热伪彩
                } else {
                    ircmd.changePalette(0);  // 使用白热伪彩
                }
                Log.i(Log.TAG, "英睿红外色彩设置为黑白模式");
            } else if (photoConfig.color == 1) {  // 色彩切换为彩色模式
                ircmd.changePalette(sensorConfig.color);  // 使用伪彩
                switch (sensorConfig.color) {
                    case 0:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot);
                        break;
                    case 1:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.black_hot);
                        break;
                    case 2:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.iron_red);
                        break;
                    case 3:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.melt_stone);
                        break;
                    case 4:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.rainbow1);
                        break;
                    case 5:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.white_hot); //没有对应的铁灰色带，用白热代替
                        break;
                    case 6:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.iron_red); //没有对应的红热色带，用铁红代替
                        break;
                    case 7:
                        osdPalette = BitmapFactory.decodeResource(context.getResources(), R.drawable.rainbow2);
                        break;
                }
                Log.i(Log.TAG, "英睿红外色彩设置为彩色模式");
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "英睿红外机芯参数设置异常：" + e);
        }
        /////
    }

    @Override
    public void setIrSetting(IRSetting setting) {
        iRSetting = setting;
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
            enter(60);
            Log.i(Log.TAG, "英睿红外拍照开始：" + filename);

            Bitmap image_ir = captureBitmap(preset, recordPreset);
            if (!isLiving() && !isRecording()) {
                close();
            }
            if (image_ir == null) {
                Log.i(Log.TAG, "英睿红外拍照失败，未捕获到照片数据, 设备状态：" + Status());
            } else {
                Bitmap bitmap = image_ir;
                Log.i(Log.TAG, "双光融合角度" + iRSetting.angle);
                Log.i(Log.TAG, "双光融合水平位移" + iRSetting.horDisplacement);
                Log.i(Log.TAG, "双光融合垂直位移" + iRSetting.verDisplacement);
                if (iRSetting.imageFusion == 1) {
                    // 视场角
//                    float[] fovRgb = {58.5f, 40f};
                    float[] fovRgb = {54.5f, 31f};
                    float[] fovIr = new float[0];
                    if (iRSetting.focalLen == 0 && iRSetting.resolution == 0) {
                        fovIr = new float[]{90.3f, 69.6f};  // T5-317 4mm
                    } else if (iRSetting.focalLen == 1 && iRSetting.resolution == 0) {
                        fovIr = new float[]{55.7f, 41.6f};  // T5-317 6.8mm
                    } else if (iRSetting.focalLen == 2 && iRSetting.resolution == 0) {
                        fovIr = new float[]{28.4f, 21.4f};  // T5-317 13mm
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
                if (irOverProtect.getMode() == IROverProtect.ExposureMode.Protection) {
                    bitmap = drawProtection(bitmap);
                }
                drawTempRegion(preset, bitmap, IRRegionTemp.RegionType.REGION_STATIC);
                if (sensorConfig.onPalette == 1) {
                    drawPalette(bitmap);
                }
                long stamp = getTimestampFromFilename(filename);
                // 回传温度 高低温预警
                doTempCallback(preset, bitmap, stamp, staticRegionsMaps);

                if (show) controllerCallback.onFrame(bitmap);

                saveBitmapAsJPEG(bitmap, filename, 100);
                bitmap.recycle();  // 释放原始Bitmap内存

                controllerCallback.onPhotoTaked(stamp, this.id, preset, filename);
                return true;
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "英睿红外拍照异常：" + e);
        } finally {
            exit();
        }
        return false;
    }
    /////
}
