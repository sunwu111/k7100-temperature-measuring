package hikvision.zhanyun.com.hikvision.device;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.YuvImage;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.text.TextPaint;

import com.google.android.renderscript.Toolkit;
import com.google.android.renderscript.YuvFormat;
import com.tencent.yolov5ncnn.YoloV5Ncnn;
import com.zhjinrui.netty.NettyUtils;
import com.zhjinrui.netty.UdpSend;
//import com.tencent.yolov5ncnn.YoloV5ObbNcnn;  // 先不增加智能测距功能 //////
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import hikvision.zhanyun.com.hikvision.CAMERASetting;
import hikvision.zhanyun.com.hikvision.ControllerCallback;
import hikvision.zhanyun.com.hikvision.IRSetting;
import hikvision.zhanyun.com.hikvision.MainActivity;
import hikvision.zhanyun.com.hikvision.RtspClientCallback;
import hikvision.zhanyun.com.hikvision.Settings;
import hikvision.zhanyun.com.hikvision.SocketClient;
import hikvision.zhanyun.com.hikvision.detect.Classifier;
import hikvision.zhanyun.com.hikvision.detect.MultiBoxTracker;
import hikvision.zhanyun.com.hikvision.detect.NcnnYoloV5Detector;
//import hikvision.zhanyun.com.hikvision.detect.NcnnYoloV5ObbDetector;  // 先不增加智能测距功能 //////
//import hikvision.zhanyun.com.hikvision.detect.TensorFlowYoloDetector;
import hikvision.zhanyun.com.hikvision.rto.RTPAAC;
import hikvision.zhanyun.com.hikvision.rto.RTPCodec;
import hikvision.zhanyun.com.hikvision.rto.RTPH264;
import hikvision.zhanyun.com.hikvision.rto.RTPH265;
import hikvision.zhanyun.com.hikvision.utils.Log;
import lyh.Utils;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static hikvision.zhanyun.com.hikvision.MainActivity.FILE_PATH;
import static hikvision.zhanyun.com.hikvision.MainActivity.channels;
import static hikvision.zhanyun.com.hikvision.MainActivity.is6735;
import static hikvision.zhanyun.com.hikvision.MainActivity.settings;
import static hikvision.zhanyun.com.hikvision.Settings.PhotoConfig.getImageSize;
import static lyh.Utils.PERIOD_MINUTE;
import static lyh.Utils.extractFileName;
import static lyh.Utils.stringToDateTime;
import static lyh.Utils.stringToTimestamp;

import androidx.annotation.NonNull;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * 通道类
 * 存储通道及其控制信息
 */

public abstract class Device {
    public static final int DEVICE_CAMERA = 0;              // 枪机
    public static final int DEVICE_DVR_HIK = 1;                 // 海康球机
    public static final int DEVICE_DVR_AIPU = 2;                 // 明景/集光球机
    public static final int DEVICE_ONVIF_CAMERA = 3;                 // ONVIF 摄像头
    public static final int DEVICE_USB_IRAY = 4;                      // 英睿红外摄像头
    public static final int DEVICE_DVR_YANDI = 5;                 // 研迪球机
    public static final int DEVICE_USB_GUIDE = 6;                 // 高德红外摄像头
    public static final int DEVICE_DVR_HUANYU = 7;                // 寰宇机芯
    public static final int FRAME_MAX_LEN = 300 * 1024;
    public static final int CHECK_LINE_START_PTZ_INDEX = 120;
    public static final int CHECK_LINE_INTERVAL = 15000;   // 每个巡检点之间间隔时长
    public static final int CHECK_LINE_PTZ_COUNT = 20;
    public static final int PTZ_PRESET_MOVE_TIME = 20 * 1000;   // 移动云台需要等待8s到达预置位
    protected final Context context;

    public int type;            // 设备类型 0 = 枪机， 1 = 海康球机， 2 = ……，每增加一个需要实现对应的Device子类
    public final int id;              // 设备通道ID
    public String name = "";         // 设备名称
    public String server;
    public int port;
    public String user;
    public String password;

    public int streamType;            // 正在播放的码流类型，非配置数据
    public Settings.PhotoConfig photoConfig = new Settings.PhotoConfig();             // 图片分辨率
    public CAMERASetting.CameraConfig cameraConfig = new CAMERASetting.CameraConfig();  // 摄像头配置 /////
    public Settings.OSD osd = new Settings.OSD();
    public HashMap<String, Settings.VideoCodec> codec = new HashMap<>();

    public int ptz_step = 5;                // 步长设置，值越大，每步走的越多，服务器下发 1 ~ 10
    public int ptz_speed = 2;               // 扫描速度，1-10

    public int ptzStep = 5;                // 步长设置，值越大，每步走的越多，服务器下发 1 ~ 10
    public int ptzSpeed = 2;               // 扫描速度，1-10
//    public float confidence = 0.6f;         // 置信度，0-1 /////

    protected int objectDetect = 0;        // 目标检测
    protected int objectDetectEnable = 0;  // 目标检测使能
    protected Settings.AIAlertType[] alertTypes = new Settings.AIAlertType[0];  // 目标检测类型
    protected Settings.AIAlertRegion[] alertRegions = new Settings.AIAlertRegion[0];  // 目标检测区域
    public ControllerCallback controllerCallback = null;
    private HandlerThread procVideoThread; /////
    private HandlerThread procAudioThread; /////
    protected Handler procVideoHandler; /////
    protected Handler procAudioHandler; /////

    protected Paint paint = new TextPaint();
    protected Paint objectPaint = new Paint();
    protected int CamID = -1;  // 系统相机对应的相机ID

    protected MediaCodec mediaCodec;
    protected MediaCodec mediaEncoder;
    protected MediaCodec mediaDecode;
    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    protected MediaMuxer mediaMuxer;
    protected int videoTrackIndex = -1; /////
    private File[] videoFiles = new File[0];
    private MediaCodec mediaDecoder; /////
    protected RTPH264 rtph264;
    protected RTPAAC rtpaac; /////
    protected final int baseRate = 90000 / 25;
    protected final int baseSleep = 1000 / 25;
    protected int playbackRate = baseRate;
    protected long playbackSleep = 30;
    /**
     * 是否正在回放
     */
    protected boolean playbackPause = false;
    protected boolean pausing = false;  // 是否暂停录制？ /////
    protected MultiBoxTracker tracker;
    protected List<Classifier.Recognition> detectResult;
    //    protected List<Classifier.RecognitionObb> detectObbResult;  // 先不增加智能测距功能 //////
    protected static Classifier detector = null;
    private boolean computingDetection = false;
    private static Handler aiHandler;
    private static HandlerThread aiThread;
    private byte[] luminanceCopy;
    private long currTimestamp = 0;
    private byte[] mPpsSps;
    private Thread threadCheckLine = null;
    public HashMap<String, Settings.SceneParameter> sceneParameters = null;
    public List<Settings.CruiseGroup> cruiseSettings = null;
    protected Bitmap popBitmap; /////
    protected boolean mOnShow; /////

    public int popChanel = 0;
    protected boolean codecReady = false;
    public boolean drawOSD = true;
    // 记住解码第一帧时间戳，以后每帧的时间戳减去该时间戳进行累加计算
    private long firstFrameTimestamp;
    protected String token = ""; /////

    /////
    private Paint textPaint = new TextPaint();
    public Boolean isOldCamera = false;
    private boolean useAudio;
    /////

    public boolean recording = false;
    public HandlerThread muxerThread;
    public Handler muxerHandler;
    public boolean isMuxerInited = false;

    protected volatile boolean muxerEverStarted = false;


    public void initMuxer(byte[] sps, byte[] pps) {

    }


    private static final int PAYLOAD_TYPE_VIDEO = 96;
    private static final int PAYLOAD_TYPE_AUDIO = 104;

    protected long recordingStartTime = -1;



    public void timeSync() {

    }

    protected void onPreview(Bitmap bitmap) {
    }

    private Bitmap yuvToBitmap(Image image) {
        Bitmap bitmap = null;
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            byte[] yuvY = new byte[buffer.remaining()];
            buffer.get(yuvY);

            buffer = planes[1].getBuffer();
            byte[] yuvU = new byte[buffer.remaining()];
            buffer.get(yuvU);

            buffer = planes[2].getBuffer();
            byte[] yuvV = new byte[buffer.remaining()];
            buffer.get(yuvV);


            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(yuvY);
            bos.write(yuvU);
            bos.write(yuvV);

            Log.i(Log.TAG, "yuv.size="+ bos.size() + ", format=" + ImageFormat.NV21 + ", width="+ image.getWidth() + ", height=" + image.getHeight());
            YuvImage yuvImage = new YuvImage(bos.toByteArray(), ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, stream);
            bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
            stream.close();
            Log.i(Log.TAG, "bitmat.w=" + bitmap.getWidth() + ", h=" + bitmap.getHeight());
        } catch (Exception e) {
            Log.e(Log.TAG, "异常：" + e.getMessage());
        }

        return bitmap;
    }

    protected void onVideoFrame(byte[] rtp) {
//        controllerCallback.onFrame(this, rtp);
        controllerCallback.onFrame(Device.this, rtp);
    }

    protected RtspClientCallback rtspLiveCallback = new RtspClientCallback() {
        @Override
        public void onPacket(int channel, final byte[] packet, int len) {

            try {
                if (isDVR() && !isUSB()) {
                    int payloadType = packet[1] & 0x7F;

                    if (recordingStartTime == -1 && isRecording()) {
                        recordingStartTime = System.currentTimeMillis();
                    }

                    if (payloadType == PAYLOAD_TYPE_VIDEO) {

                        final byte[] data = new byte[len];

                        System.arraycopy(packet, 0, data, 0, len);

                        data[8] = (byte) (ssrcLive >> 24);
                        data[9] = (byte) (ssrcLive >> 16);
                        data[10] = (byte) (ssrcLive >> 8);
                        data[11] = (byte) (ssrcLive & 0xFF);

                        if (isRecording()) {

                            byte[] frame = rtph264.rtpToNalu(data, data.length);

                            if (frame != null && rtph264.sps != null && rtph264.pps != null) {

                                muxerHandler.post(() -> {

                                    // ⭐ 初始化 muxer（只执行一次）
                                    if (!isMuxerInited) {
                                        initMuxer(rtph264.sps, rtph264.pps);
                                        isMuxerInited = true;
                                    }

                                    // ⭐ 尝试启动 muxer
                                    tryStartMuxerOnvif();

                                    if (!muxerStarted) return;

                                    bufferInfo.size = frame.length;
                                    bufferInfo.offset = 0;
                                    bufferInfo.flags = 0;

                                    long currentTime = System.currentTimeMillis();
                                    long elapsed = currentTime - recordingStartTime;
                                    bufferInfo.presentationTimeUs = elapsed * 1000L;

                                    int type = rtph264.frameType(frame);

                                    if (type == 7 || type == 8) {
                                        bufferInfo.flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
                                    } else if (type == 5) {
                                        bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                                    }

                                    mediaMuxer.writeSampleData(
                                            videoTrackIndex,
                                            ByteBuffer.wrap(frame),
                                            bufferInfo
                                    );
                                });
                            }
                        }

                        onVideoFrame(data);
                    } else if (payloadType == PAYLOAD_TYPE_AUDIO) {

                        if (isRecording()) {
//
                            byte[] aac = rtpaac.rtpToAac(packet, packet.length);
                            if (aac != null) {

                                muxerHandler.post(() -> {

                                    if (!muxerStarted || audioTrackIndex < 0) return;

                                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                                    long currentTime = System.currentTimeMillis();
                                    long elapsed = currentTime - recordingStartTime;

                                    info.offset = 0;
                                    info.size = aac.length;
                                    info.presentationTimeUs = elapsed * 1000L;
                                    info.flags = 0;

                                    mediaMuxer.writeSampleData(
                                            audioTrackIndex,
                                            ByteBuffer.wrap(aac),
                                            info
                                    );
                                });
                            }
                        }
                    }
                } else{
                    /// -------------- 解码 H264 ----------------
                    byte[] frame = rtpDecode != null ? rtpDecode.rtpToNalu(packet, len) : null;
                    if (frame == null || !codecReady) return;

                    int inputBufferIndex = mediaDecode.dequeueInputBuffer(0);
                    if (inputBufferIndex >= 0) {   //当输入缓冲区有效时,就是>=0
                        ByteBuffer inputBuffer = mediaDecode.getInputBuffer(inputBufferIndex);
                        inputBuffer.put(frame, 0, frame.length);
                        mediaDecode.queueInputBuffer(inputBufferIndex, 0, frame.length, System.nanoTime() / 1000, 0);
                    } else {
                        Log.e(Log.TAG, "解码缓冲错误：" + inputBufferIndex);
                    }

                    int outputBufferIndex = mediaDecode.dequeueOutputBuffer(bufferInfo, 0);//拿到输出缓冲区的索引
                    if (outputBufferIndex >= 0) {
                        Image img = mediaDecode.getOutputImage(outputBufferIndex);
                        Bitmap bitmap = YUV_420_888_toRGB(img);
                        mediaDecode.releaseOutputBuffer(outputBufferIndex, false);
                        procVideoHandler.post(() -> {
                            detectObject(bitmap);
                            drawWatermark(bitmap);
                            if (codecReady) {
                                encode(bitmap); /////
                            }
                        });

                        while (true) {  // 对多余来不及的，直接废弃掉
                            outputBufferIndex = mediaDecode.dequeueOutputBuffer(bufferInfo, 0);
                            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break;
                            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                mediaDecode.getOutputBuffer(outputBufferIndex);
                            }
                            mediaDecode.releaseOutputBuffer(outputBufferIndex, false);
                        }
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.i(Log.TAG, "解码新格式：" + mediaDecode.getOutputFormat());
                    }
                    /// -------------- 解码 H264 ----------------
                }
            } catch(Exception e) {
                Log.i(Log.TAG, "RTP包处理异常：" + e);
            }
        }


        @Override
        public void onResponse(List<String> headers, byte[] body) {
        }
    };


    private String bytesToHexString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "byte array is null or empty";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            // 将byte转为无符号int（避免负数转换异常）
            int value = b & 0xFF;
            // 转换为两位十六进制，不足两位补0
            String hex = Integer.toHexString(value);
            if (hex.length() < 2) {
                sb.append("0");
            }
            sb.append(hex).append(" ");
        }
        return sb.toString().trim(); // 去除末尾空格
    }


    private RTPCodec rtpDecode;

    private Toolkit rstoolkit = Toolkit.INSTANCE;

    protected RtspClientCallback rtspPlaybackCallback = new RtspClientCallback() {
        @Override
        public void onPacket(int channel, byte[] packet, int len) {
            if (channel != 0) return;

            // 直接转发RTP包
            final byte[] data = new byte[len];
            System.arraycopy(packet, 0, data, 0, len);
            data[8] = (byte) (ssrcPlayback >> 24);
            data[9] = (byte) (ssrcPlayback >> 16);
            data[10] = (byte) (ssrcPlayback >> 8);
            data[11] = (byte) (ssrcPlayback & 0xFF);

            onVideoFrame(data);

        }

        @Override
        public void onResponse(List<String> headers, byte[] body) {

        }
    };


    public enum DevState { READY, OPENING, OPENED, LIVING, PHOTOING, RECORDING, PLAYBACKING }
    private Set<DevState> state = EnumSet.noneOf(DevState.class);

    protected void setState(DevState state) { this.state.add(state); }

    protected void clearState(DevState state) { this.state.remove(state); }

    protected void clearState() { this.state.clear(); }

    public boolean isLiving() { return this.state.contains(DevState.LIVING); }

    public boolean isPlaybacking() { return this.state.contains(DevState.PLAYBACKING); }

    public boolean isRecording() { return this.state.contains(DevState.RECORDING); }

    public boolean isPhotoing() { return this.state.contains(DevState.PHOTOING); }

    public boolean isOpening() { return this.state.contains(DevState.OPENING); }

    public boolean isOpened() { return this.state.contains(DevState.OPENED); }

    public boolean isBusy() { return !this.state.isEmpty(); }

    public Set<DevState> Status() { return this.state; }

    /**
     * 直播 SSRC
     */
    public int ssrcLive = 0;
    /**
     * 回放SSRC
     */
    public int ssrcPlayback = 0;

    /**
     * 推流客户端
     */
    public SocketClient streamClient;

    public void setObjectDetect(int value) {
        if (detector != null) return;
        this.objectDetect = value;

        if (value == 0)
            detector = null;
        else if (value == 4 && !is6735) // ncnn yolov5
            detector = new NcnnYoloV5Detector(YoloV5Ncnn.PARAM_PATH, YoloV5Ncnn.BIN_PATH);
        //////
        // 先不增加智能测距功能
        // else if (value == 5 && !MainActivity.is6735) // ncnn yolov5obb
        //     detector = new NcnnYoloV5ObbDetector(YoloV5ObbNcnn.PARAM_PATH, YoloV5ObbNcnn.BIN_PATH);
        //////
        //else if (value == 1)
        //    detector = new TensorFlowYoloDetector(context.getAssets());
        /////
//        if (detector != null)
//            detector.setConfidence(confidence);
        /////
    }

    public int getObjectDetect() {
        return objectDetect;
    }

    public Device(int ID, Context context, boolean useAudio) { /////
        this.id = ID;
        this.context = context;
        this.useAudio = useAudio; /////
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        objectPaint.setStyle(Paint.Style.STROKE);
        objectPaint.setStrokeWidth(5);
        objectPaint.setColor(Color.RED);

        tracker = new MultiBoxTracker(context);

        /////
        if (procVideoThread == null) {
            procVideoThread = new HandlerThread("视频处理线程");
            procVideoThread.start();
            procVideoHandler = new Handler(procVideoThread.getLooper());
        }

        if (procAudioThread == null && useAudio) {
            procAudioThread = new HandlerThread("音频处理线程");
            procAudioThread.start();
            procAudioHandler = new Handler(procAudioThread.getLooper());
        }
        /////

        if (aiThread == null) {
            aiThread = new HandlerThread("AI线程");
            aiThread.start();
            aiHandler = new Handler(aiThread.getLooper());
        }
    }

    public boolean isDVR() { return type != DEVICE_CAMERA; }

    public boolean isUSB() { return (type == DEVICE_USB_IRAY) || (type == DEVICE_USB_GUIDE); }

    public boolean isCamera() {
        return type == DEVICE_CAMERA;
    }

    public boolean takeVideo(final String filename, final int duration, int stream, boolean upload) {
        if (isRecording()) return false;
        if (isLiving()) liveStop();



        videoStart(stream, filename, duration, upload);

        return true;
    }

    /**
     * 根据预置位，返回对应预置位场景的名字
     *
     * @param presetNo
     * @return
     */
    public String getSceneName(int presetNo) {
        if (sceneParameters == null) return "";

        String key = String.format("%d,%d", id, presetNo);
        Settings.SceneParameter sceneParameter = sceneParameters.get(key);
        if (sceneParameter == null || sceneParameter.enable == 0)
            return "";
        else
            return sceneParameter.name;
    }

    public void setSceneName(int presetNo, String name) {}

    /**
     * 开始调用预置位
     *
     * @param cmd 云台命令
     * @param para 动作参数
     */
    public void startMove(int cmd, int para, Runnable onFinish) {
        new Thread(() -> {
            try {
                move(cmd, para);
            } catch (Exception e) {
                Log.e(Log.TAG, "调用录像预置位异常：" + e.getMessage());
            } finally {
                if (onFinish != null) {
                    onFinish.run();  // 移动完成以后回调
                }
            }
        }).start();
    }

    public void startCruise(int group) {
        if (cruiseSettings == null) return;

        if (threadCheckLine == null) {
            threadCheckLine = new Thread(() -> {
                Settings.CruiseGroup cg = null;
                for (int i = 0; i < cruiseSettings.size(); i++) {
                    if (cruiseSettings.get(i).group == group) {
                        cg = cruiseSettings.get(i);
                        break;
                    }
                }
                if (cg == null) return;

                for (int i = 0; i < cg.cruises.size(); i++) {
                    if (threadCheckLine != null && threadCheckLine.isInterrupted()) break;

                    Settings.Cruise cruise = cg.cruises.get(i);
                    move(2, cruise.preset);
                    SystemClock.sleep(Math.max(PTZ_PRESET_MOVE_TIME, cruise.duration * 1000));
                }
                threadCheckLine = null;
            });
            threadCheckLine.start();
        }
    }



    public void stopCruise() {
        if (threadCheckLine != null) threadCheckLine.interrupt();
    }

    /**
     * 开始调用巡检
     *
     * @param checkGroup
     */
    public void startCheckLine(Settings.CheckGroup checkGroup) {
        if (threadCheckLine == null) {
            threadCheckLine = new Thread(() -> {
                for (int i = 0; i < checkGroup.points.size(); i++) {
                    if (threadCheckLine != null && threadCheckLine.isInterrupted()) break;

                    int idx = checkGroup.points.get(i);
                    move(900, CHECK_LINE_START_PTZ_INDEX + CHECK_LINE_PTZ_COUNT * checkGroup.index + idx);
                    SystemClock.sleep(CHECK_LINE_INTERVAL);
                }
                threadCheckLine = null;
            });
            threadCheckLine.start();
        }
    }

    public void stopCheckLine() {
        if (threadCheckLine != null) threadCheckLine.interrupt();
    }


    public Settings.FileList listFile(int videoType, String startTime, String stopTime, int startNumb, int endNumb) {
        Settings.FileList ret = new Settings.FileList();
        ret.begin = 999999999;
        ret.end = 0;
        ret.channel = (byte) id;
        List<Settings.FileItem> files = new ArrayList<>();

        for (int i = startNumb; i <= endNumb; i++) {
            if (i >= videoFiles.length) break;

            String filename = videoFiles[i].getName();
            String[] ss = filename.split("_");
            Date d1 = stringToDateTime("yyyyMMddHHmmss", ss[0]);
            Date d2 = stringToDateTime("yyyyMMddHHmmss", ss[1]);

            Settings.FileItem item = new Settings.FileItem();
            item.begin = new Settings.TimeRecord(d1.getTime());
            item.end = new Settings.TimeRecord(d2.getTime());
            item.size = (int) videoFiles[i].length();
            item.type = videoType;
            if (item.begin.timestamp < ret.begin) ret.begin = item.begin.timestamp;
            if (item.end.timestamp > ret.end) ret.end = item.end.timestamp;
            files.add(item);
        }
        ret.files = files.toArray(new Settings.FileItem[0]);
        return ret;
    }

    public Settings.ChannelStatus getStatus(int stream) {
        Settings.ChannelStatus ret = new Settings.ChannelStatus();
        ret.channel = (byte) id;
        ret.stream = (byte) stream;
        ret.code = 0;
        ret.recording = (byte) (isRecording() ? 1 : 0);
        return ret;
    }

    public boolean ptz3D(int StartingPointXCoordinate, int StartingPointYCoordinate, int AtTheEndOfXCoordinate, int AtTheEndOfCoordinate) {
        return true;
    }

    public abstract boolean takePhoto(int stream, int preset, boolean show, String filename, Bitmap pop, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert);


    // 寰宇机芯检查坐标是否稳定
    public boolean checkDeviceReadiness(int preset){
        return false;
    }

    /*
        开始录制视频
     */
    public boolean videoStop() {
        clearState(DevState.RECORDING);
        return true;
    }

    public boolean videoStart(int stream, String filename, int duration, boolean upload) {
        this.streamType = stream;

        recording = true;

        setState(DevState.RECORDING);  // 置录像位为高
        return true;
    }

    //////
    // 先不增加自动告警功能
//    public boolean playbackSaveFile(String startS, String endS, String filename) {
//        return true;
//    }
    //////

    public boolean playbackStart(String start, String stop, int ssrc) {
        if (videoFiles.length == 0) return false;

        this.ssrcPlayback = ssrc;
        playbackPause = false;
        playbackRate = baseRate;
        playbackSleep = baseSleep;
        Date begin = stringToDateTime("yyyy-MM-dd-HH-mm-ss", start);
        Date end = stringToDateTime("yyyy-MM-dd-HH-mm-ss", stop);

        File file = null;
        for (int i = 0; i < videoFiles.length; i++) {
            File item = videoFiles[i];
            String s = item.getName().substring(0, 14);
            Date tmp = stringToDateTime("yyyyMMddHHmmss", s);
            if (tmp.getTime() == begin.getTime()) {
                file = item;
                break;
            }
        }

        if (file == null || !file.exists()) return false;
        final File finalFile = file;
        new Thread(() -> playbackFile(finalFile.getPath())).start();
        return true;
    }

    private void playbackFile(String name) {
        setState(DevState.PLAYBACKING);
        rtph264 = new RTPH264(ssrcPlayback);
        try {
            int readLen = 0;
            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(name);
            extractor.selectTrack(0);
            ByteBuffer buffer = ByteBuffer.allocate(FRAME_MAX_LEN);
            while (isPlaybacking() && (readLen = extractor.readSampleData(buffer, 0)) >= 0) {
//                rtph264.timestamp = extractor.getSampleTime();

                byte[] frameData = new byte[readLen];
                buffer.get(frameData, 0, readLen);
                byte[][] rtpBuf = rtph264.encode(frameData, 96, getVideoCodec(streamType).frame);

                for (byte[] sendBuf : rtpBuf) {
                    controllerCallback.onFrame(Device.this, sendBuf);
                }
                // 3600 基准速率， 7200 为2倍数慢速，14400为4倍速慢速
//                rtph264.timestamp += playbackRate;
                SystemClock.sleep(playbackSleep);//200毫秒接近正常播放速度

                if (!extractor.advance()) break;
            }
            playbackStop();
        } catch (Exception e) {
            Log.e(Log.TAG, "回放文件失败：" + e.getMessage());
        }
        clearState(DevState.PLAYBACKING);
    }

    public abstract boolean playbackStop();

    /**
     * 回放控制接口
     *
     * @param scale  0 = 暂停, 1 = 正常速度, >2, 4, 8, 16 表示 n 倍速快进, 1/2, 1/4, 1/8 表示慢速 n 倍
     * @param offset 0 从指定时间开头开始播放， > 0表示跳到指定位置后播放， 若暂停后，则 0 表示继续播放
     * @return
     */
    public boolean playbackControl(int code, float scale, int offset) {
        if (scale == 0) {   // 暂停
            playbackPause = true;
        }
        if (scale == 1) {             // 正常播放
            playbackRate = baseRate;
            playbackSleep = baseSleep;
        } else if (scale > 1) {     // 快放
            playbackRate /= 2;
            playbackSleep /= 2;
        } else if (scale < 1) {     // 慢放
            playbackRate *= 2;
            playbackSleep *= 2;
        }
        return true;
    }

    public abstract boolean videoPause();

    public abstract boolean videoResume();

    public class onOpenCallback {
        public void openSucceed() {

        }
        public void openFailed(int errno) {

        }
    }
    public abstract boolean open(int stream, onOpenCallback cb, int timeoutSeconds, boolean waitSelfCheck);

    public abstract boolean close();

    private String statusText = "";

    public String getStatusText() {
        return statusText;
    }

    // 子类在这里继承实现显示 流量，电量等额外附加信息
    public void updateStatusText(String s, boolean osdNull) { /////
        statusText = s;
    }


    public boolean updateStatusText(Settings.OSD osd, String s, boolean osdNull) { /////
        statusText = s;
        return true;
    }

    /////
    /**
     * 向机芯发送HTTP请求，必须带Authorization！
     *
     * @param url
     * @return
     */
    protected Response http_request(String url, String body) {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        RequestBody requestBody = RequestBody.create(mediaType, body);
        Request request = new Request.Builder().url(url)
                .addHeader("Authorization", token)
                .addHeader("Connection", "close")
                .post(requestBody)
                .build();
        try {
            OkHttpClient httpClient = new OkHttpClient().newBuilder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS).build();
            return httpClient.newCall(request).execute();
        } catch (Exception e) {
            Log.e(Log.TAG, "设备POST失败：" + url + " => " + e);
            return null;
        }
    }


    protected Response http_request(String url) {
        Request request = new Request.Builder().url(url)
                .addHeader("Authorization", token)
                .addHeader("Connection", "close")
                .build();
        try {
            OkHttpClient httpClient = new OkHttpClient().newBuilder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS).build();
            return httpClient.newCall(request).execute();
        } catch (Exception e) {
            Log.i(Log.TAG, "设备请求失败：" + url + " => " + e);
            return null;
        }
    }
    /////

    public abstract boolean liveStop();
//    public abstract Settings.GeneralParameters getSceneParameters(byte[] dataDomain);
//    public abstract byte setSceneParameters(byte[] dataDomain);

    /**
     * 更新OSD状态文本，球机需要，自己处理绘制的，也不需要
     *
     * @param osd
     * @return
     */
    public abstract boolean setOSD(Settings.OSD osd, boolean osdNull); /////

    public Settings.OSD getOSD() {
        return this.osd;
    }

    public abstract boolean setCodec(Settings.VideoCodec codec);

    /**
     * 启动直播，stream 为码流类型
     *
     * @param steam
     * @return
     */
    public abstract boolean liveStart(int steam, int ssrc);

    protected abstract boolean reboot();

    /**
     * 开始记录云台运动轨迹
     *
     * @param group 需要记录的组号
     */
    public void startRecordCheckLine(int group) {}

    /**
     * 停止记录云台运动估计
     *
     * @param group 待停止记录的组号
     */
    public void stopRecordCheckLine(int group) {}

    public abstract boolean setTime(int dwYear, int dwMonth, int dwDay, int dwHour, int dwMinute, int dwSecond);

    /////
    /**
     * 绘制白底深灰框字体
     */
    private void drawOutlineText(Canvas canvas, @NonNull String text, float x, float y, float size) {
        textPaint.setTextSize(size);
        textPaint.setColor(Color.DKGRAY);
        textPaint.setStrokeWidth(3);
        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setFakeBoldText(true);
        canvas.drawText(text, x, y, textPaint);

        textPaint.setColor(Color.WHITE);
        textPaint.setStrokeWidth(0);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setFakeBoldText(false);
        canvas.drawText(text, x, y, textPaint);
    }

    /////
    protected void drawWatermark(Bitmap bitmap) {

        Canvas canvas = new Canvas(bitmap);

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        if (!drawOSD) return;

        // 定义对齐和间隔参数
        float leftMargin = osd.size + 20; // 左对齐边距
        float lineSpacing = w / (float) (osd.size * 1.2); // 行间距，可调整系数控制间隔

        float textSize = w / (float) (osd.size * 2);    // 修改osd的大小

        // 绘制时间（左上角）
        if (this.osd.time == 1) {
            String time = lyh.Utils.formatDateTime("yyyy-MM-dd HH:mm:ss EEE", new Date());
            float x = leftMargin;
            float y = 55; // 第一行
            drawOutlineText(canvas, time, x, y, textSize);
        }

        // 绘制状态信息（左上角，时间下方）
        if (controllerCallback != null) {
            String status = controllerCallback.onStatusInfo();
            if (status != null) {
                String[] ss = status.split("\n");
                float startY = 55 + lineSpacing; // 从时间下方开始

                for (int i = 0; i < ss.length; i++) {
                    float x = leftMargin;
                    float y = startY + (lineSpacing * (i));
                    drawOutlineText(canvas, ss[i], x, y, textSize);
                }
            }
        }

        // 绘制左下角文本（保持原有逻辑不变）
        if (osd.tag == 1 && osd.text != null) {
            Paint paint = new TextPaint();
            paint.setTextSize(textSize);
            float maxWidth = w * 0.85f;
            String text = osd.text;
            int start = 0;
            int len = text.length();
            float x = leftMargin;
            float y = h - w / osd.size * 2; // 保持原有的Y坐标计算方式

            while (start < len) {
                int end = paint.breakText(text, start, len, true, maxWidth, null);
                String line = text.substring(start, start + end);
                drawOutlineText(canvas, line, x, y, textSize);
                y += textSize * 1.2f; // 行间距（可按需调整）
                start += end;
            }
        }
    }



    protected void drawWatermark(Bitmap bitmap,int channel, int streamType, boolean isPhoto) {

//        Log.e(Log.TAG, "MIPI设置OSD");

        Canvas canvas = new Canvas(bitmap);

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        if (!drawOSD) return;

        // 定义对齐和间隔参数
        float leftMargin = osd.size + 20; // 左对齐边距
        float lineSpacing = w / (float) (osd.size * 1.2); // 行间距，可调整系数控制间隔

        float textSize = w / (float) (osd.size * 2);    // 修改osd的大小

        // 绘制时间（左上角）
        if (this.osd.time == 1) {
            String time = lyh.Utils.formatDateTime("yyyy-MM-dd HH:mm:ss EEE", new Date());
            float x = leftMargin;
            float y = 55; // 第一行
            drawOutlineText(canvas, time, x, y, textSize);
        }

        // 绘制状态信息（左上角，时间下方）
        if (controllerCallback != null) {
            String status = controllerCallback.onStatusInfo();
            if (status != null) {
                String[] ss = status.split("\n");
                float startY = 55 + lineSpacing; // 从时间下方开始

                for (int i = 0; i < ss.length; i++) {
                    float x = leftMargin;
                    float y = startY + (lineSpacing * (i));
                    drawOutlineText(canvas, ss[i], x, y, textSize);
                }
            }
        }

        // 绘制左下角文本（保持原有逻辑不变）
        if (osd.tag == 1 && osd.text != null) {
            Paint paint = new TextPaint();
            paint.setTextSize(textSize);
            float maxWidth = w * 0.85f;
            String text = osd.text;
            int start = 0;
            int len = text.length();
            float x = leftMargin;
            float y = h - w / osd.size * 2; // 保持原有的Y坐标计算方式
            if (isPhoto){
                y = 470 * getImageSize(settings.photoConfig.get(String.valueOf(channel)).size).y / 512;              // 保持原有的Y坐标计算方式   528  // osd不显示就是这个地方的问题
            }  else {
                y = 470 * Settings.VideoCodec.getResolution(settings.videoCodecs.get(String.format("%d:%d", channel,streamType)).resolution).y / 512; // 保持原有的Y坐标计算方式   528  // 需要通道和码流
            }

            while (start < len) {
                int end = paint.breakText(text, start, len, true, maxWidth, null);
                String line = text.substring(start, start + end);
                drawOutlineText(canvas, line, x, y, textSize);
                y += textSize * 1.2f; // 行间距（可按需调整）
                start += end;
            }
        }
    }



    private byte[] yuv;

    public Bitmap YUV_420_888_toRGB(Image image) {
        ByteBuffer y = image.getPlanes()[0].getBuffer();
        ByteBuffer u = image.getPlanes()[1].getBuffer();
        ByteBuffer v = image.getPlanes()[2].getBuffer();
        if (yuv == null || yuv.length < (y.capacity() + u.capacity() + v.capacity())) {
            yuv = new byte[y.capacity() + u.capacity() + v.capacity()];
        }
        y.get(yuv, 0, y.capacity());
        u.get(yuv, y.capacity(), u.capacity());
        v.get(yuv, y.capacity() + u.capacity(), v.capacity());
        return rstoolkit.yuvToRgbBitmap(yuv, image.getWidth(), image.getHeight(), YuvFormat.YV12);
    }

    public Settings.CruiseGroup[] getCruise() { return null; }

    public boolean setCruise(int cmd, int group, int index, int preset, int duration, int speed) {
        return true;
    }

    public boolean setPhotoParam(Settings.PhotoConfig config) {
        this.photoConfig = config;
        return true;
    }


    public List<Settings.VideoTimeItem> getRecordTimes(int stream) {
        return new ArrayList<>();
    }
    public boolean setRecordTimes(List<Settings.VideoTimeItem> list) { return true; }

    public Settings.FileDir findFiles(int type, final Settings.TimeRecord begin, final Settings.TimeRecord end) {
        final Settings.FileDir ret = new Settings.FileDir();

        ret.count = 0;
        ret.begin = begin;
        ret.end = end;
        ret.type = type;

        File dir = new File(FILE_PATH + String.valueOf(this.id));
        if (dir.exists()) {
            videoFiles = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File file, String s) {
                    File tmp = new File(file.getPath() + s);
                    if (tmp.isDirectory()) return false;
                    try {
                        String[] ss = s.split("_");
                        Date d1 = stringToDateTime("yyyyMMddHHmmss", ss[0]);
                        Date d2 = stringToDateTime("yyyyMMddHHmmss", ss[1]);
                        return s.toLowerCase().endsWith(MainActivity.EXT_MP4) && (d1.getTime() >= begin.timestamp) && (d2.getTime() <= end.timestamp);
                    }
                    catch (Exception e) {
                        return false;
                    }
                }
            });

            ret.count = videoFiles.length;
        }
        return ret;
    }

    protected void releaseMuxer() {
        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();
            mediaMuxer = null;
            /////
            videoTrackIndex = -1;
            muxerStarted = false;
            if (useAudio) {
                audioTrackIndex = -1;
                avStartNs = 0;
            }
            /////
            Log.i(Log.TAG, "成功释放Muxer");
        }
    }


    void tryStartMuxerOnvif() {

        if (muxerStarted || mediaMuxer == null) return;

        if (useAudio) {
            if (videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                mediaMuxer.start();
                muxerStarted = true;
                muxerEverStarted = true;
            }
        } else {
            if (videoTrackIndex >= 0) {
                mediaMuxer.start();
                muxerStarted = true;
                muxerEverStarted = true;
            }
        }
    }

//    void tryStartMuxerOnvif() {
//
//        if (muxerStarted || mediaMuxer == null) return;
//
//        if (useAudio) {
//            if (videoTrackIndex >= 0 && audioTrackIndex >= 0) {
//                mediaMuxer.start();
//                muxerStarted = true;
//            }
//        } else {
//            if (videoTrackIndex >= 0) {
//                mediaMuxer.start();
//                muxerStarted = true;
//            }
//        }
//    }




    // 指令和参数，参考南网规约 88H
    public void move(int cmd, int para) {
    }

    /////
    public AudioRecord audioRecord;
    public MediaCodec audioCodec;
    public boolean muxerStarted = false;
    public long avStartNs = 0;
    public long lastVideoPtsUs = 0;
    public volatile boolean audioRunning = false;
    // 用于音频 pts 单调递增
    private long audioStartNs = 0;
    public long lastAudioPtsUs = 0;
    protected int audioTrackIndex = -1;
    public long audioFramesWritten = 0;
    private static final int AUDIO_SAMPLE_RATE = 16000;
    private static final int AUDIO_CHANNEL_COUNT = 1; // 按你实际改
    private static final int AUDIO_BYTES_PER_SAMPLE = 2; // PCM16

    public void initAudioEncoder() throws Exception {
        try {
            int sampleRate = 16000;
            int channelCount = 1;
            int bitRate = 64000;

            MediaFormat af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount);
            af.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            af.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            af.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

            audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            audioCodec.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Log.i(Log.TAG, "音频编码器初始化格式：" + audioCodec.getOutputFormat());

            audioCodec.start();
        } catch (Exception e) {
            Log.e(Log.TAG, "初始化音频编码器错误：" + e.getMessage());
        }
    }

    public void initAudioRecord() throws Exception {
        int sampleRate = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int pcmFormat = AudioFormat.ENCODING_PCM_16BIT;

        int minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, pcmFormat);
        int bufSize = Math.max(minBuf * 2, 16384);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, pcmFormat, bufSize);
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IllegalStateException("音频录制初始化失败");
        }
    }

    private void tryStartMuxer() {
        if (muxerStarted) return;

        if (useAudio) {
            if (videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                mediaMuxer.start();
                muxerStarted = true;
            }
        } else {
            if (videoTrackIndex >= 0) {
                mediaMuxer.start();
                muxerStarted = true;
            }
        }
    }

    public void startAudio() {
        audioTrackIndex = -1;
        audioRunning = true;
        audioStartNs = 0;
        audioFramesWritten = 0;
        lastAudioPtsUs = 0;

        procAudioHandler.post(() -> {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            try {
                audioRecord.startRecording();
                audioStartNs = avStartNs != 0 ? avStartNs : System.nanoTime();

                while (audioRunning && isRecording()) {
                    // input
                    int inIndex = audioCodec.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer inBuf = audioCodec.getInputBuffer(inIndex);
                        if (inBuf != null) {
                            inBuf.clear();
                            int read = audioRecord.read(inBuf, 2048); //inBuf.remaining());
                            if (read > 0) {

                                int bytesPerFrame = AUDIO_BYTES_PER_SAMPLE * AUDIO_CHANNEL_COUNT;
                                long frames = read / bytesPerFrame; // 关键：用帧数而不是字节数

                                long ptsUs = (audioFramesWritten * 1_000_000L) / AUDIO_SAMPLE_RATE;
                                if (ptsUs <= lastAudioPtsUs) ptsUs = lastAudioPtsUs + 1;
                                lastAudioPtsUs = ptsUs;

                                audioFramesWritten += frames;

                                audioCodec.queueInputBuffer(inIndex, 0, read, ptsUs, 0);

                            } else {
                                // 读不到数据就不要塞空包，避免编码器生成奇怪的时间轴
                                audioCodec.queueInputBuffer(inIndex, 0, 0, lastAudioPtsUs, 0);
                            }
                        }
                    }

                    // output drain
                    int outIndex = audioCodec.dequeueOutputBuffer(info, 0);
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat outFmt = audioCodec.getOutputFormat();
                        audioTrackIndex = mediaMuxer.addTrack(outFmt);
                        tryStartMuxer();
                    } else if (outIndex >= 0) {
                        if (muxerStarted && info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            ByteBuffer outBuf = audioCodec.getOutputBuffer(outIndex);
                            if (outBuf != null) {
                                outBuf.position(info.offset);
                                outBuf.limit(info.offset + info.size);
                                if (info.presentationTimeUs <= lastAudioPtsUs) {
                                    info.presentationTimeUs = lastAudioPtsUs + 1;
                                }
                                lastAudioPtsUs = info.presentationTimeUs;
                                if (!muxerStarted) {
                                    Log.e(Log.TAG,
                                            "WRITE BEFORE START !!! "
                                                    + " isAudio=" + true
                                                    + " track=" + audioTrackIndex
                                                    + " T=" + Thread.currentThread().getName()
                                    );
                                }
                                mediaMuxer.writeSampleData(audioTrackIndex, outBuf, info);
                            }
                        }
                        audioCodec.releaseOutputBuffer(outIndex, false);
                    }
                }

                // send EOS
                int eosIn = audioCodec.dequeueInputBuffer(10000);
                if (eosIn >= 0) {
                    long ptsUs = lastAudioPtsUs + 1;
                    audioCodec.queueInputBuffer(eosIn, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }

                // drain EOS
                while (true) {
                    int outIndex = audioCodec.dequeueOutputBuffer(info, 10000);
                    if (outIndex >= 0) {
                        if (muxerStarted && info.size > 0) {
                            ByteBuffer outBuf = audioCodec.getOutputBuffer(outIndex);
                            if (outBuf != null) {
                                outBuf.position(info.offset);
                                outBuf.limit(info.offset + info.size);
                                if (info.presentationTimeUs <= lastAudioPtsUs) {
                                    info.presentationTimeUs = lastAudioPtsUs + 1;
                                }
                                lastAudioPtsUs = info.presentationTimeUs;
                                Log.e(Log.TAG,
                                        "WRITE BEFORE START !!! "
                                                + " isAudio=" + true
                                                + " track=" + audioTrackIndex
                                                + " pts=" + bufferInfo.presentationTimeUs
                                                + " size=" + bufferInfo.size
                                                + " T=" + Thread.currentThread().getName()
                                );
                                mediaMuxer.writeSampleData(audioTrackIndex, outBuf, info);
                            }
                        }
                        audioCodec.releaseOutputBuffer(outIndex, false);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                    } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break;
                    }
                }
            } catch (Throwable t) {
                Log.e(Log.TAG, "音频编码异常：" + t.getMessage());
            } finally {
                try {
                    audioRecord.stop();
                } catch (Throwable ignored) {
                }
            }
        });
    }

    protected void encode(Bitmap bitmap) { /////
        if (mediaCodec == null) return;

        try {
            // 压缩编码并存储文件
            ByteBuffer encodeBuffer = ByteBuffer.allocate(bitmap.getByteCount());
            bitmap.copyPixelsToBuffer(encodeBuffer);
            byte[] yuvBytes = encodeBuffer.array();

            int inputBufferIndex = mediaCodec.dequeueInputBuffer(0);
            if (inputBufferIndex >= 0) {  // 当输入缓冲区有效时,就是>=0
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                inputBuffer.put(yuvBytes, 0, yuvBytes.length);
                // 往输入缓冲区写入数据, 五个参数，第一个是输入缓冲区的索引，第二个数据是输入缓冲区起始索引，第三个是放入的数据大小，第四个是时间戳，保证递增就是
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, yuvBytes.length, System.nanoTime() / 1000, 0);
            } else {
                Log.w(Log.TAG, "视频帧编码输入错误：" + inputBufferIndex);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);  // 拿到输出缓冲区的索引

            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                /////
                if (mediaMuxer != null && mediaCodec != null) {
                    MediaFormat mediaFormat = mediaCodec.getOutputFormat();
                    videoTrackIndex = mediaMuxer.addTrack(mediaFormat); /////
                    tryStartMuxer(); /////
                }
                /////
                Log.w(Log.TAG, "视频帧编码新格式：" + mediaCodec.getOutputFormat());
            } else if (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                if (isRecording()) {
                    doSampleData(outputBuffer, bufferInfo, outputBufferIndex); /////
                } else if (controllerCallback != null && rtph264 != null) {
                    byte[] outData = new byte[bufferInfo.size];
                    outputBuffer.get(outData);
                    // 解码器输出的数据，关键帧前面没有pps和sps数据，需要手动拼接后给rtph264编码器
                    // 记录pps和sps
                    if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1 && outData[4] == 0x67) {
                        mPpsSps = outData;
                    } else if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1 && outData[4] == 0x65) {
                        // 在关键帧前面加上pps和sps数据
                        byte[] iframeData = new byte[mPpsSps.length + outData.length];
                        System.arraycopy(mPpsSps, 0, iframeData, 0, mPpsSps.length);
                        System.arraycopy(outData, 0, iframeData, mPpsSps.length, outData.length);
                        outData = iframeData;
                    }
                    if (firstFrameTimestamp == 0) {
                        firstFrameTimestamp = bufferInfo.presentationTimeUs / 1000 * 90;
                    }
                    rtph264.timestamp = bufferInfo.presentationTimeUs / 1000 * 90 - firstFrameTimestamp;
                    byte[][] rtps = rtph264.encode(outData, 96, 0);
                    if (rtps != null) {
                        for (byte[] pack : rtps)
                            controllerCallback.onFrame(Device.this, pack);
                    }
                }
                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "视频帧编码异常：" + e.getMessage());
        }
    }
    /////

    /*
        返回文件名中的时间戳信息
     */
    public long getTimestampFromFilename(String filename) {
        String ss[] = extractFileName(filename).split("_");
        return stringToTimestamp("yyyyMMddHHmmss", ss[0]);
    }

    protected abstract void doSampleData(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo, int outIndex); /////

    /**
     * 把本地对象识别ID转换为服务器定义的告警ID，参考南网规约 7.58.1 章节定义
     *
     * @param id
     * @return
     */
    private byte getAlertID(int id) {
        if (id == 0)
            return 5;
        else if (id == 1)
            return 2;
        else if (id == 2)
            return 1;
        else
            return (byte) id;
    }

    protected void checkAlert(long timestamp, int preset, Bitmap bitmap) {
        if (detectResult == null || detectResult.size() == 0) return;

        Settings.DetectInfo detectInfo = new Settings.DetectInfo();
        detectInfo.channel = (byte) id;
        detectInfo.preset = (byte) preset;
        detectInfo.time = new Settings.TimeRecord(timestamp);
        for (Classifier.Recognition item : detectResult) {
            Settings.ObjectInfo obj = new Settings.ObjectInfo();
            obj.classID = getAlertID(item.getClassID());
            obj.confidence = (byte) (item.getConfidence() * 100);
            obj.left = (byte) (item.getLocation().left * 255 / bitmap.getWidth());
            obj.top = (byte) (item.getLocation().top * 255 / bitmap.getHeight());
            obj.right = (byte) (item.getLocation().right * 255 / bitmap.getWidth());
            obj.bottom = (byte) (item.getLocation().bottom * 255 / bitmap.getHeight());

            detectInfo.objects.add(obj);
        }
        controllerCallback.onObjectDetect(detectInfo);
    }

    /////
    // 智能去噪算法
    private Bitmap denoise(Bitmap previewBitmap) {
        try {
            org.opencv.core.Mat previewMat = new org.opencv.core.Mat();
            org.opencv.android.Utils.bitmapToMat(previewBitmap, previewMat);
            org.opencv.core.Mat convertedMat = new org.opencv.core.Mat();
            org.opencv.imgproc.Imgproc.cvtColor(previewMat, convertedMat, org.opencv.imgproc.Imgproc.COLOR_BGRA2BGR);

//            // 计算当前图像的平均亮度
//            org.opencv.core.Mat grayMat = new org.opencv.core.Mat();
//            org.opencv.imgproc.Imgproc.cvtColor(convertedMat, grayMat, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);
//            org.opencv.core.Scalar meanScalar = org.opencv.core.Core.mean(grayMat);
//            double avgBrightness = meanScalar.val[0];
//            Log.i(Log.TAG, "摄像头当前亮度为" + avgBrightness);

            org.opencv.core.Mat denoiseMat = new org.opencv.core.Mat();
//            // 根据亮度自适应调整双边滤波降噪算法参数
//            // 计算d（去噪滤波窗口大小），保持奇数值1, 3, 5, 7, 9, 11
//            int d = (int) (3 + 2 * (4 - Math.round(avgBrightness / 50)));
//            double factor = 1.0f - (avgBrightness / 255.0f);  // 亮度越低，因子越大
//            double sigmaColor = 50 + 50 * factor;  // 50~100
//            double sigmaSpace = 50 + 50 * factor;  // 50~100
            int d = 5;
            double sigmaColor = 75;
            double sigmaSpace = 75;
            org.opencv.imgproc.Imgproc.bilateralFilter(convertedMat, denoiseMat, d, sigmaColor, sigmaSpace);
            org.opencv.android.Utils.matToBitmap(denoiseMat, previewBitmap);

            // 释放OpenCV资源
            previewMat.release();
            convertedMat.release();
//            grayMat.release();
            denoiseMat.release();
        } catch (Exception e) {
            Log.i(Log.TAG, "摄像头智能降噪：" + e);
        }
        return previewBitmap;
    }

    // 电子透雾算法
    private Bitmap electronicFog(Bitmap previewBitmap, boolean isGray) {
        Mat previewMat = new Mat();
        org.opencv.android.Utils.bitmapToMat(previewBitmap, previewMat);
        if (isGray) {
            // 已是灰度图，直接做亮度增强（直方图均衡）
            Imgproc.equalizeHist(previewMat, previewMat);
        } else {
            // 转 YCrCb 色彩空间
            Mat ycrcb = new Mat();
            Imgproc.cvtColor(previewMat, ycrcb, Imgproc.COLOR_BGR2YCrCb);
            // 分离通道
            List<Mat> channels = new ArrayList<>();
            Core.split(ycrcb, channels);
            // 对亮度通道进行直方图均衡
            Imgproc.equalizeHist(channels.get(0), channels.get(0));
            // 合并并转换回 BGR
            Core.merge(channels, ycrcb);
            Imgproc.cvtColor(ycrcb, previewMat, Imgproc.COLOR_YCrCb2BGR);
            // 释放OpenCV资源
            ycrcb.release();
            channels.get(0).release();
            channels.get(1).release();
            channels.get(2).release();
        }
        org.opencv.android.Utils.matToBitmap(previewMat, previewBitmap);
        // 释放OpenCV资源
        previewMat.release();
        return previewBitmap;
    }

    // 视频丢失检测
    private boolean videoLoss(float avgBrightness) {
        // 阈值判断是否黑屏
        float threshold = 5;
        if (avgBrightness < threshold) {
            return true;
        } else {
            return false;
        }
    }

    // 视频遮挡检测
    private boolean videoBlock(Bitmap previewBitmap, boolean isGray) {
        Mat previewMat = new Mat();
        org.opencv.android.Utils.bitmapToMat(previewBitmap, previewMat);
        Mat gray = previewMat;
        if (!isGray) {
            // 转换为灰度图像
            Imgproc.cvtColor(previewMat, gray, Imgproc.COLOR_BGR2GRAY);
        }
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(gray, mean, stddev);
        // 释放OpenCV资源
        previewMat.release();
        gray.release();
        mean.release();
        stddev.release();
        float stdDevVal = (float) stddev.toArray()[0];
        // 阈值判断是否遮挡
        float threshold = 10;
        if (stdDevVal < threshold) {
            return true;
        } else {
            return false;
        }
    }

    // 视频失焦检测
    private boolean videoOutFocus(Bitmap previewBitmap, boolean isGray) {
        Mat previewMat = new Mat();
        org.opencv.android.Utils.bitmapToMat(previewBitmap, previewMat);
        Mat gray = previewMat;
        if (!isGray) {
            // 转换为灰度图像
            Imgproc.cvtColor(previewMat, gray, Imgproc.COLOR_BGR2GRAY);
        }
        // Sobel算子计算X、Y方向的梯度
        Mat gradX = new Mat();
        Mat gradY = new Mat();
        Imgproc.Sobel(gray, gradX, CvType.CV_64F, 1, 0, 3);
        Imgproc.Sobel(gray, gradY, CvType.CV_64F, 0, 1, 3);
        // 计算梯度幅值 sqrt(Gx^2 + Gy^2)
        Mat gradMagnitude = new Mat();
        Core.magnitude(gradX, gradY, gradMagnitude);
        // 计算总边缘强度（所有像素值的和）
        float edgeStrength = (float) Core.sumElems(gradMagnitude).val[0];
        // 释放OpenCV资源
        previewMat.release();
        gray.release();
        gradX.release();
        gradY.release();
        gradMagnitude.release();
        // 阈值判断是否失焦
        float threshold = 30000000;
        if (edgeStrength < threshold) {
            return true;
        } else {
            return false;
        }
    }

    // 视频花屏检测
    private boolean videoScreenDist(Bitmap previewBitmap, boolean isGray) {
        Mat previewMat = new Mat();
        org.opencv.android.Utils.bitmapToMat(previewBitmap, previewMat);
        float threshold = 500000;  // 阈值
        if (isGray) {
            // 灰度图像处理
            Mat grayHist = new Mat();
            Imgproc.calcHist(Arrays.asList(previewMat), new MatOfInt(0), new Mat(), grayHist,
                    new MatOfInt(256), new MatOfFloat(0f, 256f));
            float sumGray = (float) Core.sumElems(grayHist).val[0];
            grayHist.release();
            // 阈值判断是否花屏
            if (sumGray < threshold) {
                return true;
            } else {
                return false;
            }
        } else {
            // 分离RGB通道
            List<Mat> rgbChannels = new ArrayList<>();
            Core.split(previewMat, rgbChannels);
            // 计算每个通道的直方图
            Mat histR = new Mat(), histG = new Mat(), histB = new Mat();
            int histSize = 256;
            MatOfInt histSizeMat = new MatOfInt(histSize);
            MatOfFloat histRange = new MatOfFloat(0f, 256f);
            Imgproc.calcHist(Arrays.asList(rgbChannels.get(0)), new MatOfInt(0), new Mat(), histR, histSizeMat, histRange);
            Imgproc.calcHist(Arrays.asList(rgbChannels.get(1)), new MatOfInt(0), new Mat(), histG, histSizeMat, histRange);
            Imgproc.calcHist(Arrays.asList(rgbChannels.get(2)), new MatOfInt(0), new Mat(), histB, histSizeMat, histRange);
            // 计算各通道像素总和
            float sumR = (float) Core.sumElems(histR).val[0];
            float sumG = (float) Core.sumElems(histG).val[0];
            float sumB = (float) Core.sumElems(histB).val[0];
            // 释放OpenCV资源
            previewMat.release();
            rgbChannels.get(0).release();
            rgbChannels.get(1).release();
            rgbChannels.get(2).release();
            histR.release();
            histG.release();
            histB.release();
            histSizeMat.release();
            histRange.release();
            // 阈值判断是否花屏
            if (sumR < threshold || sumG < threshold || sumB < threshold) {
                return true;
            } else {
                return false;
            }
        }
    }

    // 视频噪点检测
    private boolean videoNoise(Bitmap previewBitmap, boolean isGray) {
        Mat previewMat = new Mat();
        org.opencv.android.Utils.bitmapToMat(previewBitmap, previewMat);
        float threshold = 200;  // 阈值
        if (isGray) {
            // 使用拉普拉斯算子计算图像的二阶导数
            Mat laplacian = new Mat();
            Imgproc.Laplacian(previewMat, laplacian, CvType.CV_64F);
            // 计算方差（图像清晰度指标）
            MatOfDouble mean = new MatOfDouble();
            MatOfDouble stddev = new MatOfDouble();
            Core.meanStdDev(laplacian, mean, stddev);
            double variance = Math.pow(stddev.get(0, 0)[0], 2);
            previewMat.release();
            laplacian.release();
            mean.release();
            stddev.release();
            // 阈值判断是否花屏
            if (variance < threshold) {
                return true;
            } else {
                return false;
            }
        } else {
            Mat gray = new Mat();
            Imgproc.cvtColor(previewMat, gray, Imgproc.COLOR_BGR2GRAY);
            // 使用拉普拉斯算子计算图像的二阶导数
            Mat laplacian = new Mat();
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F);
            // 计算方差（图像清晰度指标）
            MatOfDouble mean = new MatOfDouble();
            MatOfDouble stddev = new MatOfDouble();
            Core.meanStdDev(laplacian, mean, stddev);
            double variance = Math.pow(stddev.get(0, 0)[0], 2);
            // 释放OpenCV资源
            previewMat.release();
            gray.release();
            laplacian.release();
            mean.release();
            stddev.release();
            // 阈值判断是否花屏
            if (variance < threshold) {
                return true;
            } else {
                return false;
            }
        }
    }

    // 亮度增强算法
    private Bitmap addBrightness(Bitmap previewBitmap, float avgBrightness) {
        Mat previewMat = new Mat();
        org.opencv.android.Utils.bitmapToMat(previewBitmap, previewMat);
        // 计算自适应Gamma值
//        double gamma = (avgBrightness >= 100) ? 1.0 : Math.min(200 / avgBrightness, 2.5);
        double gamma = (avgBrightness >= 100) ? 1.0 : Math.min(200 / avgBrightness, 1.75);
        if (gamma == 1.0) {
            // 释放OpenCV资源
            previewMat.release();
            return previewBitmap;  // 无需处理
        }
        double invGamma = 1.0 / gamma;
        // 创建LUT查找表
        Mat lut = new Mat(1, 256, CvType.CV_8UC1);
        byte[] gammaLUT = new byte[256];
        for (int i = 0; i < 256; i++) {
            gammaLUT[i] = (byte) Math.min(255, Math.pow(i / 255.0, invGamma) * 255.0);
        }
        lut.put(0, 0, gammaLUT);
        // 应用LUT增强
        Mat result = new Mat();
        Core.LUT(previewMat, lut, result);
        org.opencv.android.Utils.matToBitmap(result, previewBitmap);
        // 释放OpenCV资源
        previewMat.release();
        lut.release();
        result.release();
        return previewBitmap;
    }
    /////

    /**
     * 根据PhotoParam处理图片
     */
    protected Bitmap processPhoto(Bitmap bitmap, long stamp, int preset, HashMap<String, Settings.AIParameter> aps, boolean alert) {
        if (bitmap == null) return bitmap;

        /////
        // 计算当前图像的平均亮度
        Mat mat = new Mat();
        org.opencv.android.Utils.bitmapToMat(bitmap, mat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGRA2GRAY);
        Scalar meanScalar = Core.mean(grayMat);
        float avgBrightness = (float) meanScalar.val[0];
        Log.i(Log.TAG, "图像当前亮度为" + avgBrightness);
        boolean isGray = false;
        /////
//        if ((photoConfig.color == 0) || ((cameraConfig.low_light == 1) && (avgBrightness < 20))) {
//            Log.i(Log.TAG, "开启低照度彩转黑");
//            org.opencv.android.Utils.matToBitmap(grayMat, bitmap);
//            // 释放OpenCV资源
//            grayMat.release();
//            isGray = true;
//        }
        /////
        // 释放OpenCV资源
        mat.release();
        if (cameraConfig.denoiseMode == 3) {
            Log.i(Log.TAG, "开启智能降噪");
            bitmap = denoise(bitmap);
        }
        if (cameraConfig.electronicFog == 1 ) {
            Log.i(Log.TAG, "开启电子透雾");
            bitmap = electronicFog(bitmap, isGray);
        }
        if (cameraConfig.lowLight == 1) {
            Log.i(Log.TAG, "开启亮度增强");
            bitmap = addBrightness(bitmap, avgBrightness);
        }

        Settings.AIParameter ap = aps.get(String.format("1,%d", preset));

        if (detector != null && ap != null) {
            if (ap.enable != 0) {
                if (ap.alertRegions.length == 0) {
                    detectResult = detector.recognizeImage(bitmap, ap.alertTypes);
                } else {
                    detectResult = new ArrayList<>();
                    for (Settings.AIAlertRegion region : ap.alertRegions) {
                        if (region.enable != 1 || region.coordinates == null || region.coordinates.length < 4)
                            continue;
                        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
                        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
                        for (Point p : region.coordinates) {
                            int realX = (int) (p.x * bitmap.getWidth() / 255.0);
                            int realY = (int) (p.y * bitmap.getHeight() / 255.0);
                            if (realX < minX) minX = realX;
                            if (realY < minY) minY = realY;
                            if (realX > maxX) maxX = realX;
                            if (realY > maxY) maxY = realY;
                        }
                        // 限制到图片边界
                        minX = Math.max(0, minX);
                        minY = Math.max(0, minY);
                        maxX = Math.min(bitmap.getWidth(), maxX);
                        maxY = Math.min(bitmap.getHeight(), maxY);
                        int width = maxX - minX;
                        int height = maxY - minY;
                        if (width <= 0 || height <= 0) continue;  // 无效区域，跳过
                        // 裁剪子图
                        Bitmap subBitmap = Bitmap.createBitmap(bitmap, minX, minY, width, height);
                        List<Classifier.Recognition> results = detector.recognizeImage(subBitmap, ap.alertTypes);
                        for (Classifier.Recognition r : results) {
                            RectF location = r.getLocation();  // 识别结果在小图上的位置
                            // 平移到大图坐标系
                            location.offset(minX, minY);
                            r.setLocation(location);  // 更新回识别对象
                            detectResult.add(r);  // 加到总的列表里
                        }
                        subBitmap.recycle();  // 回收子图，防止内存泄露
                    }
                }
                if (alert) {
                    checkAlert(stamp, preset, bitmap);
                }
                drawObject(bitmap, false);
            }
        }

        // 获取原始图像的宽度和高度
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();


        Point size = Settings.PhotoConfig.getImageSize(photoConfig.size);
        if (w == size.x && h == size.y && photoConfig.color == 1) return bitmap;

        Bitmap resize = bitmap;
        // 修改分辨率
//        Log.e(Log.TAG,"分辨率为："+size.x+size.y); // 这个是我们修改了之后，这个
//        Log.e(Log.TAG,"获取原始图像的宽度和高度："+w+h); //


        if (w != size.x || h != size.y) {
            float scaleWidth = ((float) size.x) / w;
            float scaleHeight = ((float) size.y) / h;
            Matrix matrix = new Matrix();
            matrix.postScale(scaleWidth, scaleHeight);
            resize = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
        }
        // 灰度化
        if (photoConfig.color == 0) {
            // 获取灰度化后的图像宽高
            int width = resize.getWidth();
            int height = resize.getHeight();
            // 创建一个新的灰度图像
            Bitmap grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            // 遍历原始图像的每一个像素
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    // 获取当前像素的颜色
                    int pixelColor = resize.getPixel(x, y);
                    // 提取红色、绿色和蓝色分量
                    int red = (pixelColor >> 16) & 0xFF;
                    int green = (pixelColor >> 8) & 0xFF;
                    int blue = pixelColor & 0xFF;
                    // 使用加权平均法计算灰度值
                    int gray = (int) (0.299 * red + 0.587 * green + 0.114 * blue);
                    // 创建灰度像素，透明度不变
                    int grayPixel = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;
                    // 设置灰度像素到新图像
                    grayscale.setPixel(x, y, grayPixel);
                }
            }
            // 将灰度图像赋给结果图像
            resize = grayscale;
        }
        // 返回最终处理后的图像
        return resize;

    }

    public Bitmap processPOP(Bitmap bitmap, Bitmap pop) {
        if (pop != null && bitmap != null) {
            Canvas canvas = new Canvas(bitmap);
            if (canvas != null) {
                int pop_w = bitmap.getWidth() / 4;
                int pop_h = bitmap.getHeight() / 4;
                pop = Bitmap.createScaledBitmap(pop, pop_w, pop_h, false);
                canvas.drawBitmap(pop, bitmap.getWidth() - pop_w - 1, 0, null);
            }
        }
        return bitmap;
    }

    // 用于图像拼接
    public boolean mergeBitmapVertical(int preset, String filename, boolean show, Bitmap... bitmaps) {
        return false;
    }

    /////
    // 拍照用于pop，对照片不做osd处理
    public Bitmap takePhoto(int stream, int preset, String filename, boolean show, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert, byte imageStitch) {
        return null;
    }
    /////

    public Settings.VideoCodec getVideoCodec(int streamType) {
        return codec.get(String.valueOf(streamType));
    }

    /**
     * 检测视频流中的对象
     *
     * @param bitmap
     */
    protected void detectObject(Bitmap bitmap) {
        if (detector == null) return;

//        tracker.onFrame(bitmap.getWidth(), bitmap.getHeight(), bitmap.getWidth(), 0, data, currTimestamp);

        if (computingDetection) {
            drawObject(bitmap, false);
            return;
        }

        aiHandler.post(() -> {
            computingDetection = true;
            detectResult = detector.recognizeImage(bitmap, null);
            tracker.screenRects.clear();
            tracker.trackedObjects.clear();
            if (detectResult != null) tracker.trackResults(detectResult, luminanceCopy, currTimestamp);
            computingDetection = false;
        });
    }

    protected void drawObject(Bitmap bitmap, boolean video) {
        if (detectResult == null) return;
        Canvas canvas = new Canvas(bitmap);
        if (video)
            tracker.draw(canvas);
        else {
            int size = bitmap.getWidth() / 40;
            paint.setTextSize(size);
            for (Classifier.Recognition result : detectResult) {
                String s = String.format("%s(%.2f%%)", result.getTitle(), result.getConfidence() * 100);
                canvas.drawText(s, result.getLocation().left + size * 0.5f, result.getLocation().top + size * 1.5f, paint);
                canvas.drawRect(result.getLocation().left, result.getLocation().top, result.getLocation().right, result.getLocation().bottom, objectPaint);
            }
        }
    }

    protected void initVideoCodec() {
        Settings.VideoCodec vc = getVideoCodec(streamType);
        Point size = Settings.VideoCodec.getResolution(vc.resolution);
        initVideoEncoder(streamType, size.x, size.y,false);
        initVideoDecoder(size.x, size.y, vc.codec); /////
        codecReady = true;
    }

    protected void unInitVideoCodec() {
        uninitVideoDecoder();
        codecReady = false;
    }

    private MediaFormat createVideoEncodecMediaFormat(int stream, int w, int h, boolean MIPIMark) { /////
        Settings.VideoCodec vc = codec.get(String.valueOf(stream));
        if (vc == null) vc = new Settings.VideoCodec();

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h);
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, vc.bps * 1000);

        if (MIPIMark){
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 10);       // 建议与摄像头的帧率一致 ， TODO 如果是mipi的话，这个地方需要修改为10，和摄像头的输出帧率一样，否则后台拉流容易不出图。
        }else {
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, vc.frame);
        }

        mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, vc.vbr == 0 ? MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR : MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        // 因为我们要加水印，所以用位图了，而位图是默认的ARGB_8888彩色格式，所以编码器用这个格式，需要手机支持！
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_Format32bitARGB8888);
//        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible); //////
        // I 帧间隔限制在1--5秒之间
        if ((vc.frame != 0) && (vc.iFrame / vc.frame > 0) && (vc.iFrame / vc.frame < 6)) {
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, vc.iFrame / vc.frame); //关键帧间隔时间 单位s
        } else {
            Log.i(Log.TAG, "默认I帧间隔：2秒");
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2); //关键帧间隔时间 单位s
        }
        mediaFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh); //某些设备不支持设置Profile和Level，而应该采用默认设置
        mediaFormat.setInteger("level", MediaCodecInfo.CodecProfileLevel.AVCLevel41); // Level 4.1
        return mediaFormat;
    }

    protected void initVideoEncoder(int stream, int w, int h, boolean MIPIMark) { /////
        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            mediaCodec.configure(createVideoEncodecMediaFormat(stream, w, h, MIPIMark), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Log.i(Log.TAG, "视频编码器初始化格式：" + mediaCodec.getOutputFormat());

            mediaCodec.start();
            firstFrameTimestamp = 0;
        } catch (Exception e) {
            Log.e(Log.TAG, "初始化视频编码器错误：" + e.getMessage());
        }
    }

    protected void uninitVideoEncoder() { /////
        Log.d(Log.TAG, "清理视频编码器"); /////
        try {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            mediaCodec = null;
        } catch (Exception e) {
            Log.d(Log.TAG, "清理视频编码器异常：" + e); /////
        }
    }

    /////
    protected void uninitAudioEncoder() {
        Log.d(Log.TAG, "清理音频编码器");
        try {
            audioRunning = false;
            if (audioRecord != null) {
                audioRecord.release();
            }
            audioRecord = null;
            if (audioCodec != null) {
                audioCodec.stop();
                audioCodec.release();
            }
            audioCodec = null;
        } catch (Exception e) {
            Log.d(Log.TAG, "清理音频编码器异常：" + e);
        }
    }
    /////

    protected void initVideoDecoder(int w, int h, int codec) { /////
        Log.d(Log.TAG, "初始化视频解码器：" + w + "," + h); /////
        if (mediaDecode == null) {
            try {
                MediaFormat mediaFormat = new MediaFormat();
                if (codec == 0) {
                    rtpDecode = new RTPH264(0);
                    mediaDecode = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                    mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h);
                } else if (codec == 1) {
                    rtpDecode = new RTPH265(0);
                    mediaDecode = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
                    mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, w, h);
                } else {
                    throw new IOException("不支持的视频解码类型：" + type); /////
                }
                mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
                int color = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ? MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible : ImageFormat.YUV_420_888;
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, color);
                mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
                mediaDecode.configure(mediaFormat, null, null, 0);
                Log.i(Log.TAG, "视频解码器初始化格式：" + mediaDecode.getOutputFormat()); /////
                mediaDecode.start();
            } catch (IOException e) {
                Log.e(Log.TAG, "创建视频解码器错误：" + e.getMessage()); /////
            }
        }
    }

    protected void uninitVideoDecoder() { /////
        Log.d(Log.TAG, "清理视频解码器"); /////

        if (mediaDecode != null) {
            mediaDecode.stop();
            mediaDecode.release();
            mediaDecode = null;
        }
        System.gc();
    }

    private static TimerTask ptzStopRunning;


    protected void stopPTZ() {
        // 如果预定了停止PTZ的任务，要把之前的任务停掉然后再开启
        if (ptzStopRunning != null) {
            ptzStopRunning.cancel();
        }
        ptzStopRunning = new TimerTask() {
            @Override
            public void run() {
                move(48, 0);
            }
        };
        Utils.scheduleTask(ptzStopRunning, 2 * PERIOD_MINUTE);
    }

    // 机芯启动完成，云台自检完成
    public boolean isDeviceReady() {
        return false;
    }
    // 红外测温模组
    public void setSensorConfig(IRSetting.SensorConfig config) {}
    public void setIrSetting(IRSetting setting) {}
    //
    public void setTempRegion(int channel, int preset, Vector<IRSetting.IrRegionInfo> regions, boolean live) {}

    //////
    // 双光融合功能
    public abstract boolean imageFusion(int stream, int preset, boolean show, String filename, Bitmap image_rgb, int recordPreset); /////
    //////

    /////
    // 摄像头模组
    public void setCameraConfig(CAMERASetting config) {}

    protected void closeCamera() {
        aiHandler.removeCallbacksAndMessages(0);
        /////
        procVideoHandler.removeCallbacksAndMessages(0);
        if (useAudio) {
            procAudioHandler.removeCallbacksAndMessages(0);
        }
        /////
        popBitmap = null;
    }

    // 清晰度计算算法
    public static float calculateClarity(Bitmap previewBitmap) {
        Mat previewMat = new Mat();
        org.opencv.android.Utils.bitmapToMat(previewBitmap, previewMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(previewMat, grayMat, Imgproc.COLOR_BGR2GRAY);

        Mat laplacianMat = new Mat();
        Imgproc.Laplacian(grayMat, laplacianMat, CvType.CV_64F);

        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(laplacianMat, mean, stddev);

        float variance = (float) Math.pow(stddev.get(0, 0)[0], 2);
        // 释放OpenCV资源
        previewMat.release();
        grayMat.release();
        mean.release();
        stddev.release();
        return variance;
    }

    public static float calculateSNR(Bitmap previewBitmap) {
        Mat previewMat = new Mat();
        org.opencv.android.Utils.bitmapToMat(previewBitmap, previewMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(previewMat, grayMat, Imgproc.COLOR_BGR2GRAY);

        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(grayMat, mean, stddev);

        double meanVal = mean.get(0, 0)[0];
        double stddevVal = stddev.get(0, 0)[0];

        if (stddevVal == 0) return 0;  // 避免除0错误
        float snr = (float) (20 * Math.log10(meanVal / stddevVal));
        // 释放OpenCV资源
        previewMat.release();
        grayMat.release();
        mean.release();
        stddev.release();
        return snr;
    }

    public static float calculateDynamicRange(Bitmap previewBitmap) {
        Mat previewMat = new Mat();
        org.opencv.android.Utils.bitmapToMat(previewBitmap, previewMat);
        Mat grayMat = new Mat();
        Imgproc.cvtColor(previewMat, grayMat, Imgproc.COLOR_BGR2GRAY);

        Core.MinMaxLocResult mmr = Core.minMaxLoc(grayMat);
        float min = (float) mmr.minVal;
        float max = (float) mmr.maxVal;

        // 避免除以0
        if (min < 1e-3) min = 1.0f;
        float wdrDb = (float) (20 * Math.log10(max / min));

        return wdrDb;
    }

    protected void drawMetrics(Bitmap bitmap) {
        // 获取原始图像的宽度和高度
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        // 计算信噪比
        float clarity = calculateClarity(bitmap);
        // 计算宽动态
        float snr = calculateSNR(bitmap);
        // 计算清晰度
        float dynamicRange = calculateDynamicRange(bitmap);
        Canvas canvas = new Canvas(bitmap);
        long x, y = 0;
        long offset = 300;
        int osdSize = 20;
        // 计算单个字符的高度和宽度，以便后面添加水印的时候计算字符的位置
        paint.setTextSize(w / osdSize);
        // 左上角，绘制状态信息
        paint.setTextSize((float) (w / (osdSize * 1.5)));
        x = w / osdSize;
        y = w / osdSize + offset;
        canvas.drawText(String.format("信噪比：%2.2fdB，宽动态：%2.2fdB，清晰度：%2.2f", snr, dynamicRange, clarity), x, y, paint);
    }
    /////
}
