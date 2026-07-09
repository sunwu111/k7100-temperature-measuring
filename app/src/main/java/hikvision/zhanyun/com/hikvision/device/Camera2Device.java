package hikvision.zhanyun.com.hikvision.device;

import static hikvision.zhanyun.com.hikvision.MainActivity.channels;
import static hikvision.zhanyun.com.hikvision.MainActivity.is6735;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Point;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Range;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.blankj.utilcode.util.ImageUtils;
import com.zhjinrui.bean.CommonResponseEntity;
import com.zhjinrui.bean.Constant;
import com.zhjinrui.netty.NettyTcpServer;
import com.zhjinrui.netty.NettyUtils;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import hikvision.zhanyun.com.hikvision.MainActivity;
import hikvision.zhanyun.com.hikvision.Settings;
import hikvision.zhanyun.com.hikvision.rto.RTPH264;
import hikvision.zhanyun.com.hikvision.utils.Log;
import hikvision.zhanyun.com.hikvision.utils.MipiSwitch;
import lyh.Utils;

public class Camera2Device extends Device { // 成员：保存运行状态
    private final int camID; // 成员：保存运行状态
    private final Context mContext; // 成员：保存运行状态
    private final int mMainBoard;  // 0: 旧xy6762板 1： 新xy6762板；成员：保存运行状态
    private boolean mCameraPhotoing = false; // 状态：当前正在抓拍
    private Handler mBackgroundHandler; // 线程：承接Camera2回调
    private HandlerThread mBackgroundThread; // Android：串行消息线程
    private CameraDevice mCameraDevice; // Camera2：已打开的相机句柄
    private ImageReader mImageReader; // Camera2：接收预览/抓拍帧
    private ImageReader mStillImageReader; // Camera2：独立静态图输出
    private CaptureRequest.Builder mPreviewRequestBuilder; // Camera2：构建下一次请求参数
    private CameraCaptureSession mPreviewSession; // Camera2：向HAL提交请求的会话
    private final CameraMulthreadLock mCameraOpenCloseLock = new CameraMulthreadLock(); // 成员：保存运行状态
    private final CameraMulthreadLock mCameraPhtotingLock = new CameraMulthreadLock(); // 同步：takePhoto等待，保存/失败/超时唤醒
    private final CameraMulthreadLock mCameraFocusLock = new CameraMulthreadLock(); // 同步：lockFocus等待，AF/AE回调唤醒
    private String mFileImage; // 成员：保存运行状态
    private int mFilePreset; // 成员：保存运行状态
    private Point mResolution; // 成员：保存运行状态
    private long mLockFocusTime = 0;  // 记录AF和AE开始时间，超时强制退出；成员：保存运行状态
    private final static HandlerThread scheduledThread = new HandlerThread("摄像机拍照线程"); // Android：串行消息线程
    private static Handler scheduledHandler; // Android：串行消息线程
    private HandlerThread mCameraWorkThread; // 每个 Camera2Device 独立业务线程，避免双通道拍照/录像互相阻塞
    private Handler mCameraWorkHandler; // 每个 Camera2Device 独立业务队列
    /////
    private static final String AIS_AVAILABLE_MODES_KEY_NAME = "com.mediatek.mfnrfeature.availablemfbmodes"; // 成员：保存运行状态
    private static final String AIS_REQUEST_MODE_KEY_NAME = "com.mediatek.mfnrfeature.mfbmode"; // 成员：保存运行状态
    private static final String AIS_RESULT_MODE_KEY_NAME = "com.mediatek.mfnrfeature.mfbresult"; // 成员：保存运行状态
    private CameraCharacteristics.Key<int[]> mKeyAisAvailableModes; // 成员：保存运行状态
    private CaptureResult.Key<int[]> mKeyAisResult; // Camera2：HAL返回帧元数据
    private CaptureRequest.Key<int[]> mKeyAisRequestMode; // Camera2：发送给HAL的请求
    private final static HandlerThread mCameraParamThread = new HandlerThread("摄像机异步更新参数线程"); // Android：串行消息线程
    private static Handler mCameraParamHandler; // 线程：异步刷新请求参数
    private float minFocusDist; // 成员：保存运行状态
    private int rotate; // 成员：保存运行状态
    private boolean useAudio; // 成员：保存运行状态
    private final Object mipiStreamLock = new Object(); // 成员：保存运行状态
    private byte[] mipiLivePpsSps; // 成员：保存运行状态
    private long mipiLiveFirstFrameTimestamp = 0; // 成员：保存运行状态
    private volatile long mipiRecordSamplesWritten = 0; // 成员：保存运行状态
    private volatile long mipiRecordBytesWritten = 0; // 成员：保存运行状态
    private volatile long mipiRecordKeyFramesWritten = 0; // 成员：保存运行状态
    /////


    /// sunwu
    private final AtomicBoolean takePhotoOnce = new AtomicBoolean(false);   // 防止在拉流的时候拍照会被执行多次；同步：只允许一帧完成本次抓拍
    private final AtomicBoolean photoDone = new AtomicBoolean(false);       // 拍照已经成功，但等待方不知道  如果没有这个变量，在一次拍照成功后，设备还在等待拍照任务，会导致拍照失败再次拍照，其实已经成功。；条件：takePhoto等它变true

    private final AtomicBoolean videoEncodePending = new AtomicBoolean(false); // 已废弃：连续拉流不能用单帧门控丢帧，保留字段避免外部补丁冲突

    // 后台拉流要求 OSD 时间连续，不能主动丢弃 ImageReader 队列里的中间帧。
    // 8 个 buffer 给 YUV 解码/OSD/编码留缓冲，避免 2~4 秒卡顿后 acquireLatestImage 直接跳到最新帧。
    private static final int VIDEO_IMAGE_READER_MAX_IMAGES = 8;
    private static final long VIDEO_ENCODE_INPUT_TIMEOUT_US = 20_000L;

    ///
    private static final Object sDualCameraLock = new Object(); // 同步：保护双MIPI共享状态
    private static final Object sDualPhotoTaskLock = new Object(); // 同步：串行化双路拍照任务

    private static Camera2Device sCamera0Device; // 成员：保存运行状态
    private static Camera2Device sCamera1Device; // 成员：保存运行状态

    private static boolean sDualStarting = false; // 成员：保存运行状态
    private static boolean sDualStarted = false; // 成员：保存运行状态
    private static boolean sDualClosing = false; // 成员：保存运行状态
    private static int sDualPhotoTaskCount = 0; // 同步：统计未完成拍照任务

    private static final boolean ALWAYS_OPEN_BOTH_MIPI = true; // 成员：保存运行状态

    private boolean mDualSessionStarted = false; // 成员：保存运行状态
    private boolean mDualSessionStarting = false; // 成员：保存运行状态
    ///

    public Camera2Device(int ID, Context context, int camID, int board, int rotate, boolean useAudio) { /////；成员：保存运行状态
        super(ID, context, useAudio); /////
        this.camID = camID;
        this.mContext = context;
        this.mMainBoard = board;
        this.drawOSD = true;
        this.rotate = rotate;
        this.useAudio = useAudio;

        if (!scheduledThread.isAlive()) {
            scheduledThread.start();
            scheduledHandler = new Handler(scheduledThread.getLooper()); // Android：串行消息线程
        }
        if (!mCameraParamThread.isAlive()) {
            mCameraParamThread.start();
            mCameraParamHandler = new Handler(mCameraParamThread.getLooper()); /////；线程：异步刷新请求参数
        } /////
        registerDualCameraInstance(); ///
    }

    ///
    private void registerDualCameraInstance() { // 入口：方法定义
        synchronized (sDualCameraLock) { // 同步：保护双MIPI共享状态
            int realCamId = camID % 2;

            if (realCamId == 0) {
                sCamera0Device = this;
            } else {
                sCamera1Device = this;
            }

            Log.i(Log.TAG, "注册双路 MIPI 对象"
                    + "，camID = " + camID
                    + "，realCamId = " + realCamId
                    + "，this = " + this
                    + "，sCamera0Device = " + sCamera0Device
                    + "，sCamera1Device = " + sCamera1Device);
        }
    }
    ///

    private class CameraMulthreadLock { // 成员：保存运行状态
        private class Locker { // 成员：保存运行状态
            private Boolean unlocked = false; // 成员：保存运行状态
        }

        private Locker locker = new Locker(); // 成员：保存运行状态

        public boolean waitLock(int milisecond) { // 同步：等待唤醒或超时
            try { // 异常：保护相机/IO调用
                synchronized (locker) { // 同步：互斥访问共享状态
                    locker.unlocked = false;
                    locker.wait(milisecond); // 同步：等待唤醒或超时
                }
            } catch (Exception e) {
                Log.i(Log.TAG, "加锁异常：" + e.getMessage());
            }
            return locker.unlocked; // 返回：结束当前方法
        }

        public void notifyLock() { // 同步：唤醒等待线程
            try { // 异常：保护相机/IO调用
                synchronized (locker) { // 同步：互斥访问共享状态
                    locker.unlocked = true;
                    locker.notify(); // 同步：唤醒等待线程
                }
            } catch (Exception e) {
                Log.i(Log.TAG, "解锁异常：" + e.getMessage());
            }
        }
    }

    @Override
    public void setSceneName(int presetNo, String name) { // 入口：方法定义
    }

    @Override
    protected void doSampleData(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo, int outIndex) { /////；Android：视频编码/封装
//        if (isRecording() && !pausing && mediaMuxer != null)
//            mediaMuxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
        /////
        try { // 异常：保护相机/IO调用
            if (isRecording() && !pausing && mediaMuxer != null) {
                if (outIndex >= 0) {
                    if (videoTrackIndex < 0 && mediaCodec != null) {
                        MediaFormat mediaFormat = mediaCodec.getOutputFormat(); // Android：视频编码/封装
                        videoTrackIndex = mediaMuxer.addTrack(mediaFormat);
                        tryStartMipiMuxer();
                    }
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) { // Android：视频编码/封装
                        return; // 返回：结束当前方法
                    }
                    if (muxerStarted && bufferInfo.size > 0) {
                        ByteBuffer outBuf = mediaCodec.getOutputBuffer(outIndex);
                        if (outBuf != null) {
                            ByteBuffer recordBuf = outBuf.duplicate();
                            recordBuf.position(bufferInfo.offset);
                            recordBuf.limit(bufferInfo.offset + bufferInfo.size);

                            long ptsUs = bufferInfo.presentationTimeUs;
                            long nowUs = (avStartNs != 0) ? ((System.nanoTime() - avStartNs) / 1000) : ptsUs;
                            if (ptsUs > nowUs + 5_000_000L) ptsUs = nowUs;
                            if (ptsUs <= lastVideoPtsUs) ptsUs = lastVideoPtsUs + 1;
                            lastVideoPtsUs = ptsUs;
                            bufferInfo.presentationTimeUs = ptsUs;

                            mediaMuxer.writeSampleData(videoTrackIndex, recordBuf, bufferInfo);
                            mipiRecordSamplesWritten++;
                            mipiRecordBytesWritten += bufferInfo.size;
                            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) { // Android：视频编码/封装
                                mipiRecordKeyFramesWritten++;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "MIPI摄像头录制视频文件异常：" + e);
        }
        /////
    }

    @Override
    protected void encode(Bitmap bitmap) { // Android：图像解码/绘制
        if (mediaCodec == null || bitmap == null) return;

        try { // 异常：保护相机/IO调用
            ByteBuffer encodeBuffer = ByteBuffer.allocate(bitmap.getByteCount());
            bitmap.copyPixelsToBuffer(encodeBuffer);
            byte[] argbBytes = encodeBuffer.array();

            int inputBufferIndex = mediaCodec.dequeueInputBuffer(VIDEO_ENCODE_INPUT_TIMEOUT_US);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
                if (inputBuffer != null) {
                    if (argbBytes.length > inputBuffer.capacity()) {
                        Log.w(Log.TAG, "MIPI video encode skip oversize frame: "
                                + bitmap.getWidth() + "x" + bitmap.getHeight()
                                + ", bytes=" + argbBytes.length
                                + ", capacity=" + inputBuffer.capacity());
                        mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, System.nanoTime() / 1000, 0);
                        return; // 返回：结束当前方法
                    }
                    inputBuffer.clear();
                    inputBuffer.put(argbBytes, 0, argbBytes.length);
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, argbBytes.length, System.nanoTime() / 1000, 0);
                } else {
                    Log.w(Log.TAG, "MIPI video encode input buffer is null");
                }
            } else {
                Log.w(Log.TAG, "MIPI video encode input error: " + inputBufferIndex);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo(); // Android：视频编码/封装
            for (int drainCount = 0; drainCount < 16; drainCount++) { // 循环：遍历数据
                int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) { // Android：视频编码/封装
                    break;
                }
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { // Android：视频编码/封装
                    if (isRecording() && mediaMuxer != null && videoTrackIndex < 0) {
                        MediaFormat mediaFormat = mediaCodec.getOutputFormat(); // Android：视频编码/封装
                        videoTrackIndex = mediaMuxer.addTrack(mediaFormat);
                        tryStartMipiMuxer();
                    }
                    Log.w(Log.TAG, "MIPI video encode format changed: " + mediaCodec.getOutputFormat());
                    continue;
                }
                if (outputBufferIndex < 0) {
                    continue;
                }
                try { // 异常：保护相机/IO调用
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer == null || bufferInfo.size <= 0) {
                        continue;
                    }

                    byte[] liveData = null;
                    if (isLiving() && rtph264 != null) {
                        ByteBuffer liveBuf = outputBuffer.duplicate();
                        liveBuf.position(bufferInfo.offset);
                        liveBuf.limit(bufferInfo.offset + bufferInfo.size);
                        liveData = new byte[bufferInfo.size];
                        liveBuf.get(liveData);
                    }

                    if (isRecording()) {
                        doSampleData(outputBuffer, bufferInfo, outputBufferIndex);
                    }

                    if (liveData != null) {
                        sendMipiLiveEncodedFrame(liveData, bufferInfo.presentationTimeUs);
                    }
                } finally {
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                }
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "MIPI video encode error: " + e.getMessage());
        }
    }

    private void sendMipiLiveEncodedFrame(byte[] outData, long presentationTimeUs) { // 入口：方法定义
        if (controllerCallback == null || rtph264 == null || outData == null || outData.length < 5) return;

        try { // 异常：保护相机/IO调用
            if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1) {
                int type = outData[4] & 0x1F;
                if (type == 7) {
                    mipiLivePpsSps = outData;
                    return; // 返回：结束当前方法
                } else if (type == 5 && mipiLivePpsSps != null) {
                    byte[] iframeData = new byte[mipiLivePpsSps.length + outData.length];
                    System.arraycopy(mipiLivePpsSps, 0, iframeData, 0, mipiLivePpsSps.length);
                    System.arraycopy(outData, 0, iframeData, mipiLivePpsSps.length, outData.length);
                    outData = iframeData;
                }
            }

            long timestamp = presentationTimeUs / 1000 * 90;
            if (mipiLiveFirstFrameTimestamp == 0) {
                mipiLiveFirstFrameTimestamp = timestamp;
            }
            rtph264.timestamp = timestamp - mipiLiveFirstFrameTimestamp;
            byte[][] rtps = rtph264.encode(outData, 96, 0);
            if (rtps != null) {
                for (byte[] pack : rtps) { // 循环：遍历数据
                    controllerCallback.onFrame(Camera2Device.this, pack);
                }
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "MIPI live RTP packet error: " + e);
        }
    }

    private Bitmap imageDecode(Image image) { // Android：底层图像buffer
        switch (image.getFormat()) { // 分支：按状态选择路径
            case ImageFormat.JPEG: // 分支：状态处理入口
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null).copy(Bitmap.Config.ARGB_8888, true); // Android：图像解码/绘制
            case ImageFormat.YUV_420_888: // 分支：状态处理入口
                //bitmap = YUV_420_888_toRGB(image, image.getWidth(), image.getHeight());
                return YUV_420_888_toRGB(image); // 返回：结束当前方法
        }
        return null; // 返回：结束当前方法
    }

    /////
    // 图像参数调节算法
    private Bitmap preProcessingPhoto(Bitmap previewBitmap) { // Android：图像解码/绘制
        try { // 异常：保护相机/IO调用
            Point targetResolution = null;
            boolean useVideoResolution = (isLiving() && rtph264 != null) || isRecording() || enableLiveEncode;
            if (useVideoResolution) {

//                Point size = Settings.VideoCodec.getResolution(codec.get(String.valueOf(0)).resolution);
                Settings.VideoCodec vc = codec.get(String.valueOf(streamType));
                Point size = vc != null ? Settings.VideoCodec.getResolution(vc.resolution) : null;
                if (size == null) {
                    size = new Point(previewBitmap.getWidth(), previewBitmap.getHeight());
                }


                ///
                // 由于分辨率大于1536x864无法拉流，因此设置最大的分辨率为1536x864
                if (size.x > 1536 || size.y > 864) {
                    size = new Point(1536, 864);
                }
                ///
                mResolution = size;
                targetResolution = size;
//                Log.e(Log.TAG,"preProcessingPhoto分辨率为：" + mResolution.x + ":" + mResolution.y);

            } else if (mCameraPhotoing) {
                targetResolution = Settings.PhotoConfig.getImageSize(photoConfig.size);
            } else {
                targetResolution = mResolution;
            }
            ///
            if (targetResolution == null) {
                targetResolution = new Point(previewBitmap.getWidth(), previewBitmap.getHeight());
            }
            if (targetResolution.x == previewBitmap.getWidth() && targetResolution.y == previewBitmap.getHeight()) {
                if (photoConfig.brightness == 50 && photoConfig.contrast == 50 && photoConfig.saturation == 50) {
                    return previewBitmap; // 返回：结束当前方法
                } else {
                    Bitmap outputBitmap = Bitmap.createBitmap(previewBitmap.getWidth(), previewBitmap.getHeight(), Bitmap.Config.ARGB_8888); /////；Android：图像解码/绘制
                    Canvas canvas = new Canvas(outputBitmap); // Android：图像解码/绘制
                    Paint paint = new Paint();
                    ColorMatrix colorMatrix = new ColorMatrix();
//                // 是否灰度化
//                if (photoConfig.color == 0) {
//                    ColorMatrix grayscaleMatrix = new ColorMatrix(new float[]{
//                            0.299f, 0.587f, 0.114f, 0, 0,
//                            0.299f, 0.587f, 0.114f, 0, 0,
//                            0.299f, 0.587f, 0.114f, 0, 0,
//                            0, 0, 0, 1, 0
//                    });
//                    colorMatrix.postConcat(grayscaleMatrix);
//                    //Log.i(Log.TAG, "摄像头色彩设置为黑白模式");
//                } else {
//                    //Log.i(Log.TAG, "摄像头色彩设置为彩色模式");
//                }

                    if (photoConfig.brightness != 50) {
                        // 映射亮度 (1~100 → -128~128)
                        float brightnessValue = (photoConfig.brightness - 50) * 2.56f;
                        // 调整亮度
                        ColorMatrix brightnessMatrix = new ColorMatrix(new float[]{
                                1, 0, 0, 0, brightnessValue,
                                0, 1, 0, 0, brightnessValue,
                                0, 0, 1, 0, brightnessValue,
                                0, 0, 0, 1, 0
                        });
                        colorMatrix.postConcat(brightnessMatrix);
                    }
                    if (photoConfig.contrast != 50) {
                        // 映射对比度 (1~100 → 0.5~2.0)
                        float contrastValue = 0.5f + (photoConfig.contrast - 1) * (1.5f / 99);
                        // 调整对比度
                        float translate = (1 - contrastValue) * 128;
                        ColorMatrix contrastMatrix = new ColorMatrix(new float[]{
                                contrastValue, 0, 0, 0, translate,
                                0, contrastValue, 0, 0, translate,
                                0, 0, contrastValue, 0, translate,
                                0, 0, 0, 1, 0
                        });
                        colorMatrix.postConcat(contrastMatrix);
                    }
                    if (photoConfig.saturation != 50) {
                        // 映射饱和度 (1~100 → 0.0~2.0)
                        float saturationValue = (photoConfig.saturation - 1) * (2.0f / 99);
                        // 调整饱和度
                        ColorMatrix saturationMatrix = new ColorMatrix();
                        saturationMatrix.setSaturation(saturationValue);
                        if (MainActivity.DEBUG) {
                            colorMatrix.postConcat(saturationMatrix);
                        }
                        // 组合所有矩阵
                        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
                        canvas.drawBitmap(previewBitmap, 0, 0, paint);
                        //Log.i(Log.TAG, "摄像头亮度设置为" + photoConfig.brightness);
                        //Log.i(Log.TAG, "摄像头对比度设置为" + photoConfig.contrast);
                        //Log.i(Log.TAG, "摄像头饱和度设置为" + photoConfig.saturation);
                        return outputBitmap; // 返回：结束当前方法
                    }
                }
            } else {
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(previewBitmap, targetResolution.x, targetResolution.y, true); // Android：图像解码/绘制
//                Log.i(Log.TAG, "摄像头设置分辨率为" + targetResolution.x + "x" + targetResolution.y);
                if (photoConfig.brightness == 50 && photoConfig.contrast == 50 && photoConfig.saturation == 50) {
                    return scaledBitmap; // 返回：结束当前方法
                } else {
                    Bitmap outputBitmap = Bitmap.createBitmap(scaledBitmap.getWidth(), scaledBitmap.getHeight(), Bitmap.Config.ARGB_8888); /////；Android：图像解码/绘制
                    Canvas canvas = new Canvas(outputBitmap); // Android：图像解码/绘制
                    Paint paint = new Paint();
                    ColorMatrix colorMatrix = new ColorMatrix();
//                // 是否灰度化
//                if (photoConfig.color == 0) {
//                    ColorMatrix grayscaleMatrix = new ColorMatrix(new float[]{
//                            0.299f, 0.587f, 0.114f, 0, 0,
//                            0.299f, 0.587f, 0.114f, 0, 0,
//                            0.299f, 0.587f, 0.114f, 0, 0,
//                            0, 0, 0, 1, 0
//                    });
//                    colorMatrix.postConcat(grayscaleMatrix);
//                    //Log.i(Log.TAG, "摄像头色彩设置为黑白模式");
//                } else {
//                    //Log.i(Log.TAG, "摄像头色彩设置为彩色模式");
//                }

                    if (photoConfig.brightness != 50) {
                        // 映射亮度 (1~100 → -128~128)
                        float brightnessValue = (photoConfig.brightness - 50) * 2.56f;
                        // 调整亮度
                        ColorMatrix brightnessMatrix = new ColorMatrix(new float[]{
                                1, 0, 0, 0, brightnessValue,
                                0, 1, 0, 0, brightnessValue,
                                0, 0, 1, 0, brightnessValue,
                                0, 0, 0, 1, 0
                        });
                        colorMatrix.postConcat(brightnessMatrix);
                    }
                    if (photoConfig.contrast != 50) {
                        // 映射对比度 (1~100 → 0.5~2.0)
                        float contrastValue = 0.5f + (photoConfig.contrast - 1) * (1.5f / 99);
                        // 调整对比度
                        float translate = (1 - contrastValue) * 128;
                        ColorMatrix contrastMatrix = new ColorMatrix(new float[]{
                                contrastValue, 0, 0, 0, translate,
                                0, contrastValue, 0, 0, translate,
                                0, 0, contrastValue, 0, translate,
                                0, 0, 0, 1, 0
                        });
                        colorMatrix.postConcat(contrastMatrix);
                    }
                    if (photoConfig.saturation != 50) {
                        // 映射饱和度 (1~100 → 0.0~2.0)
                        float saturationValue = (photoConfig.saturation - 1) * (2.0f / 99);
                        // 调整饱和度
                        ColorMatrix saturationMatrix = new ColorMatrix();
                        saturationMatrix.setSaturation(saturationValue);
                        colorMatrix.postConcat(saturationMatrix);
                    }
                    // 组合所有矩阵
                    paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
                    canvas.drawBitmap(scaledBitmap, 0, 0, paint);
                    //Log.i(Log.TAG, "摄像头亮度设置为" + photoConfig.brightness);
                    //Log.i(Log.TAG, "摄像头对比度设置为" + photoConfig.contrast);
                    //Log.i(Log.TAG, "摄像头饱和度设置为" + photoConfig.saturation);
                    return outputBitmap; // 返回：结束当前方法
                }
            }
            ///
        } catch (Exception e) {
            Log.i(Log.TAG, "摄像头设置图像参数异常：" + e);
            return previewBitmap; // 返回：结束当前方法
        }

        return previewBitmap; // 返回：结束当前方法
    }

    private void updateCaptureRequestParameters() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            // 降噪模式
            if (cameraConfig.denoiseMode == 0) {
                //Log.i(Log.TAG, "MIPI摄像头关闭降噪");
            } else if (cameraConfig.denoiseMode == 1) {
                //Log.i(Log.TAG, "MIPI摄像头开启2D降噪");
            } else if (cameraConfig.denoiseMode == 2) {
                //Log.i(Log.TAG, "MIPI摄像头开启3D降噪");
            }
            if (cameraConfig.denoiseMode <= 2) {
                mPreviewRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, (int) cameraConfig.denoiseMode); // Camera2：构建下一次请求参数
            }
            // 增益控制
            if (cameraConfig.gainControl == 0) {
                //Log.i(Log.TAG, "MIPI摄像头手动增益");
            } else {
                //Log.i(Log.TAG, "MIPI摄像头自动增益");
            }
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, (int) cameraConfig.gainControl); // Camera2：构建下一次请求参数
            // 背光补偿
            if (cameraConfig.backLightCom == 1) {
                //Log.i(Log.TAG, "MIPI摄像头开启背光补偿");
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 4);  // 亮度补偿值；Camera2：构建下一次请求参数
                // 强光抑制
            } else if (cameraConfig.strongLightSup == 1) {
                //Log.i(Log.TAG, "MIPI摄像头开启强光抑制");
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -4);  // 亮度补偿值；Camera2：构建下一次请求参数
            } else {
                //Log.i(Log.TAG, "MIPI摄像头关闭背光补偿与强光抑制");
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);  // 亮度补偿值；Camera2：构建下一次请求参数
            }
            // 聚焦模式
            if (cameraConfig.focusMode == 0) {
                //Log.i(Log.TAG, "MIPI摄像头半自动对焦");
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO); // Camera2：构建下一次请求参数
            } else if (cameraConfig.focusMode == 1) {
                //Log.i(Log.TAG, "MIPI摄像头全自动对焦");
                if (mCameraPhotoing) { // 状态：当前正在抓拍
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE); // Camera2：构建下一次请求参数
                } else {
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO); // Camera2：构建下一次请求参数
                }
            } else {
                //Log.i(Log.TAG, "MIPI摄像头手动对焦");
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF); // Camera2：构建下一次请求参数
            }
            // 是否灰度化
            if (photoConfig.color == 0) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_MONO);  // 黑白色彩；Camera2：构建下一次请求参数
            }
            // 更新请求
            applyLowNoiseCaptureRequestParameters(isRecording() ? getVideoCodec(streamType) : null, isRecording());
            logExposureRequest("updateCaptureRequestParameters", mPreviewRequestBuilder);
            mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler); // Camera2：持续提交预览请求
        } catch (Exception e) {
            Log.e(Log.TAG, "更新CaptureRequest参数失败: " + e.getMessage()); // Camera2：发送给HAL的请求
        }
    }

    private void applyLowNoiseCaptureRequestParameters(Settings.VideoCodec vc, boolean isRecordVideo) { // 入口：方法定义
        if (mPreviewRequestBuilder == null) { // Camera2：构建下一次请求参数
            return; // 返回：结束当前方法
        }
        applyLowNoiseCaptureRequestParameters(mPreviewRequestBuilder, vc, isRecordVideo); // Camera2：构建下一次请求参数
    }

    private void applyLowNoiseCaptureRequestParameters(CaptureRequest.Builder builder, Settings.VideoCodec vc, boolean isRecordVideo) { // Camera2：发送给HAL的请求
        if (builder == null) {
            return; // 返回：结束当前方法
        }
        int denoiseMode = CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY;
        if (cameraConfig != null && cameraConfig.denoiseMode >= 0 && cameraConfig.denoiseMode <= 2) {
            denoiseMode = cameraConfig.denoiseMode;
        }
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, denoiseMode); // Camera2：发送给HAL的请求

        if (isRecordVideo && vc != null && vc.frame > 0) {
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(vc.frame, vc.frame)); // Camera2：限制帧率范围
        } else if (isPhotoing()) {
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(30, 30));
        }else {
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(10, 10)); // Camera2：限制帧率范围
        }

//        Log.e(Log.TAG,"mKeyAisRequestMode is not null::"+(mKeyAisRequestMode != null));
        if (mKeyAisRequestMode != null) {
            builder.set(mKeyAisRequestMode, new int[]{2});
        }
    }

    private void refreshLowNoiseRepeatingRequest(Settings.VideoCodec vc, boolean isRecordVideo) { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (mPreviewSession == null || mPreviewRequestBuilder == null) { // Camera2：构建下一次请求参数
                return; // 返回：结束当前方法
            }
            applyLowNoiseCaptureRequestParameters(vc, isRecordVideo);
            logExposureRequest("refreshLowNoiseRepeatingRequest", mPreviewRequestBuilder);
            mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler); // Camera2：持续提交预览请求
        } catch (Exception e) {
            Log.e(Log.TAG, "Refresh low-noise CaptureRequest failed: " + e.getMessage()); // Camera2：发送给HAL的请求
        }
    }

    private static Bitmap rotate180WithCanvas(Bitmap src) { // Android：图像解码/绘制
        Bitmap dst = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig() != null ? src.getConfig() : Bitmap.Config.ARGB_8888); // Android：图像解码/绘制
        Canvas c = new Canvas(dst); // Android：图像解码/绘制
        c.save();
        c.rotate(180f, src.getWidth() / 2f, src.getHeight() / 2f);
        c.drawBitmap(src, 0f, 0f, null);  // 关键：把原图画到旋转后的画布上
        c.restore();
        return dst; // 返回：结束当前方法
    }

    private static Bitmap rotate90ClockwiseWithCanvas(Bitmap src) { // Android：图像解码/绘制
        Bitmap dst = Bitmap.createBitmap(src.getHeight(), src.getWidth(), src.getConfig() != null ? src.getConfig() : Bitmap.Config.ARGB_8888); // Android：图像解码/绘制
        Canvas c = new Canvas(dst); // Android：图像解码/绘制
        c.save();
        c.translate(src.getHeight(), 0f);
        c.rotate(90f);
        c.drawBitmap(src, 0f, 0f, null);
        c.restore();
        return dst; // 返回：结束当前方法
    }

    private void saveCapturedPhoto(Bitmap bitmap) {
        if (bitmap == null) {
            return; // 返回：结束当前方法
        }
        Log.i(Log.TAG, "抓拍图片分辨率：" + bitmap.getWidth() + "x" + bitmap.getHeight());

        bitmap = processPhoto(bitmap, System.currentTimeMillis(), 255, aiParameters, true);
        //drawMetrics(bitmap);  // 绘制信噪比、宽动态、清晰度OSD /////
        drawWatermark(bitmap, id, streamType, true); // 先AI识别再画OSD //////

        Utils.saveBitmapAsJPEG(bitmap, mFileImage, 100);
        if (NettyUtils.isTakePhoto()) {
            toolTakePhoto(bitmap);
            NettyUtils.setTakePhoto(false);
        }
        if (controllerCallback != null) {
            procVideoHandler.post(() -> controllerCallback.onPhotoTaked(getTimestampFromFilename(mFileImage), id, mFilePreset, mFileImage));
        }
        Runnable done = () -> {
            photoDone.set(true); // 条件：takePhoto等它变true
            mCameraPhtotingLock.notifyLock(); // 同步：takePhoto等待，保存/失败/超时唤醒
        };
        if (mBackgroundHandler != null) { // 线程：承接Camera2回调
            mBackgroundHandler.postDelayed(done, 1500); // 线程：承接Camera2回调
            return; // 返回：结束当前方法
        }
        SystemClock.sleep(1500); // 阻塞：当前线程睡眠
        done.run();
    }


    // 直播和录像帧编码。
    // 注意：这里故意不再使用 videoEncodePending，也不再 post 到另一个队列后直接丢弃后续帧。
    // 原来的 videoEncodePending.compareAndSet(false, true) 会导致编码慢时主动丢掉中间帧，
    // 后台拉流看到的现象就是 OSD 时间从 45s 直接跳到 49s。
    // 现在在 Camera 回调线程内串行编码，配合 acquireNextImage() 形成背压：宁可整体帧率下降，也不主动跳帧。
    private void postEncodeFrame(Bitmap bitmap) { // Android：图像解码/绘制
        if (bitmap == null) {
            return;
        }
        try {
            encode(bitmap);
        } catch (Exception e) {
            Log.i(Log.TAG, "MIPI 连续拉流编码异常：" + e.getMessage());
        }
    }

    private boolean shouldAcquireContinuousVideoFrame(ImageReader reader) {
        return reader == mImageReader
                && mPreviewSessionVideoMode
                && ((isLiving() && rtph264 != null) || isRecording() || enableLiveEncode);
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() { // Android：相机帧队列
        @Override
        public void onImageAvailable(ImageReader reader) { // 回调：ImageReader有新帧
            Image img = null;
            try { // 异常：保护相机/IO调用
                // 视频态必须连续消费队列。acquireLatestImage() 会丢弃旧帧，
                // 这正是后台拉流 OSD 秒数跳变的主要原因。
                img = shouldAcquireContinuousVideoFrame(reader)
                        ? reader.acquireNextImage()
                        : reader.acquireLatestImage();
                if (img == null || !previewReady) {
                    return;
                }

                Bitmap previewBitmap = imageDecode(img); // Android：图像解码/绘制
                boolean logBitmapDiag = shouldLogBitmapExposure();
                if (logBitmapDiag) {
                    logBitmapExposure("previewDecode", previewBitmap, img);
                }

                if (previewBitmap == null) {
                    return;
                }

                if (rotate == 1) {
                    previewBitmap = rotate180WithCanvas(previewBitmap);
                }

                previewBitmap = preProcessingPhoto(previewBitmap);
                if (logBitmapDiag) {
                    logBitmapExposure("previewPostProcess", previewBitmap, null);
                }

                // 拉流/录像拍照：不再提交 TEMPLATE_STILL_CAPTURE，不改 HAL pipeline，直接保存当前视频帧。
                if (mCameraPhotoing && isVideoFramePhotoMode() && takePhotoOnce.compareAndSet(true, false)) {
                    Log.i(Log.TAG, "视频态拍照：直接保存当前 YUV 帧，camID = " + camID);
                    Bitmap photoBitmap = previewBitmap.copy(
                            previewBitmap.getConfig() != null ? previewBitmap.getConfig() : Bitmap.Config.ARGB_8888,
                            true);
                    saveCapturedPhoto(photoBitmap);
                }

                if ((isLiving() && rtph264 != null) || isRecording() || enableLiveEncode) {
                    drawWatermark(previewBitmap, id, streamType, false); // 视频帧 OSD
                    Bitmap finalPreviewBitmap = previewBitmap;
                    postEncodeFrame(finalPreviewBitmap);
                }

                if (mOnShow && controllerCallback != null && previewBitmap != null) {
                    Bitmap localPreviewBitmap = rotate90ClockwiseWithCanvas(previewBitmap);
                    controllerCallback.onFrame(localPreviewBitmap);
                }
            } catch (Exception e) {
                Log.i(Log.TAG, "图片处理异常：" + e.getMessage());
            } finally {
                if (img != null) img.close(); // Image：释放底层buffer
            }
        }
    };

    private final ImageReader.OnImageAvailableListener mStillImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) { // 回调：ImageReader有新帧
            Image img = reader.acquireLatestImage(); // ImageReader：取最新帧
            try { // 异常：保护相机/IO调用
                if (img == null || !mCameraPhotoing) { // 状态：当前正在抓拍
                    return; // 返回：结束当前方法
                }
                if (isVideoFramePhotoMode()) {
                    // 视频态拍照只能从 mImageReader 的 YUV_420_888 当前帧截取，
                    // 即使还有旧 JPEG reader 回调，也不能让它完成本次拍照。
                    Log.i(Log.TAG, "忽略 JPEG reader 回调：当前是视频态拍照，camID = " + camID);
                    return;
                }
                if (!takePhotoOnce.compareAndSet(true, false)) { // 同步：只允许一帧完成本次抓拍
                    return; // 返回：结束当前方法
                }
                boolean logBitmapDiag = true;
                Bitmap bitmap = imageDecode(img); // Android：图像解码/绘制
                if (logBitmapDiag) {
                    logBitmapExposure("stillDecode", bitmap, img);
                }
                if (bitmap == null) {
                    Log.i(Log.TAG, "Still photo decode failed, camID = " + camID);
                    photoDone.set(true); // 条件：takePhoto等它变true
                    mCameraPhtotingLock.notifyLock(); // 同步：takePhoto等待，保存/失败/超时唤醒
                    return; // 返回：结束当前方法
                }
                if (rotate == 1) {
                    bitmap = rotate180WithCanvas(bitmap);
                }
                bitmap = preProcessingPhoto(bitmap);
                if (logBitmapDiag) {
                    logBitmapExposure("stillPostProcess", bitmap, null);
                }
                Log.i(Log.TAG, "Still photo resolution: " + bitmap.getWidth() + "x" + bitmap.getHeight() + ", camID = " + camID);
                saveCapturedPhoto(bitmap);
            } catch (Exception e) {
                Log.i(Log.TAG, "Still photo process error: " + e.getMessage());
                photoDone.set(true); // 条件：takePhoto等它变true
                mCameraPhtotingLock.notifyLock(); // 同步：takePhoto等待，保存/失败/超时唤醒
            } finally {
                if (img != null) img.close(); // Image：释放底层buffer
            }
        }
    };


//    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener =
//            new ImageReader.OnImageAvailableListener() {
//
//                @Override
//                public void onImageAvailable(ImageReader reader) {
//
//                    Image img = null;
//
//                    try {
//
//                        img = reader.acquireLatestImage();
//
//                        if (img == null || !previewReady) {
//                            return;
//                        }
//
//
//                        if (mCameraPhotoing && takePhotoOnce.compareAndSet(true, false)) {
//
//                            Log.i(Log.TAG, "开始保存原生JPEG");
//
//                            try {
//
//                                if (img.getFormat() == ImageFormat.JPEG) {
//
//                                    Image.Plane[] planes = img.getPlanes();
//
//                                    if (planes != null && planes.length > 0) {
//
//                                        ByteBuffer buffer = planes[0].getBuffer();
//
//                                        byte[] jpegData = new byte[buffer.remaining()];
//
//                                        buffer.get(jpegData);
//
//                                        FileOutputStream fos = null;
//
//                                        try {
//
//                                            fos = new FileOutputStream(mFileImage);
//
//                                            fos.write(jpegData);
//
//                                            fos.flush();
//
//                                            Log.i(Log.TAG,
//                                                    "原生JPEG保存成功: "
//                                                            + mFileImage
//                                                            + " size="
//                                                            + jpegData.length);
//
//                                        } finally {
//
//                                            if (fos != null) {
//                                                try {
//                                                    fos.close();
//                                                } catch (Exception ignore) {
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//
//                                photoDone.set(true);
//
//                                mCameraPhtotingLock.notifyLock();
//
//
//
//                                if (controllerCallback != null) {
//
//                                    procVideoHandler.post(() ->
//                                            controllerCallback.onPhotoTaked(
//                                                    getTimestampFromFilename(mFileImage),
//                                                    id,
//                                                    mFilePreset,
//                                                    mFileImage
//                                            )
//                                    );
//                                }
//
//                            } catch (Exception e) {
//
//                                Log.e(Log.TAG, "拍照处理异常：" + e);
//
//                                controllerCallback.onPhotoFailed(
//                                        id,
//                                        mFilePreset,
//                                        mFileImage
//                                );
//
//                            } finally {
//
//                                // 防止连续重复抓拍
//                                mCameraPhotoing = false;
//                            }
//
//                            return;
//                        }
//
//                        if ((isLiving() && rtph264 != null) || isRecording()) {
//
//                            Bitmap previewBitmap = imageDecode(img);
//
//                            if (previewBitmap == null) {
//                                return;
//                            }
//
//                            // 视频旋转
//                            if (rotate == 1) {
//                                previewBitmap = rotate180WithCanvas(previewBitmap);
//                            }
//
//                            // 视频预处理
//                            previewBitmap = preProcessingPhoto(previewBitmap);
//
//                            // AI/OSD
//                            drawWatermark(
//                                    previewBitmap,
//                                    3,
//                                    streamType,
//                                    false
//                            );
//
//                            Bitmap finalPreviewBitmap = previewBitmap;
//
//                            // 防止队列堆积
//                            procVideoHandler.removeCallbacksAndMessages(null);
//
//                            procVideoHandler.post(() -> {
//
//                                try {
//
//                                    encode(finalPreviewBitmap);
//
//                                } catch (Exception e) {
//
//                                    Log.e(Log.TAG, "视频编码异常：" + e);
//                                }
//                            });
//
//                            // 预览显示
//                            if (mOnShow && controllerCallback != null) {
//
//                                controllerCallback.onFrame(previewBitmap);
//                            }
//                        }
//
//                    } catch (Exception e) {
//
//                        Log.e(Log.TAG, "图片处理异常：" + e);
//
//                    } finally {
//
//                        if (img != null) {
//
//                            try {
//                                img.close();
//                            } catch (Exception ignore) {
//                            }
//                        }
//                    }
//                }
//            };


    private void toolTakePhoto(Bitmap previewBitmap) { // Android：图像解码/绘制
        try { // 异常：保护相机/IO调用
            byte[] picByte = ImageUtils.bitmap2Bytes(NettyUtils.matrixBitmap(previewBitmap, 0.5f));
            Log.i(Log.TAG, picByte.length + "");

            CommonResponseEntity commonResponseEntity = new CommonResponseEntity();
            commonResponseEntity.type = (Constant.TAKE_PHOTO);
            commonResponseEntity.content = ("图片");
            commonResponseEntity.picByte = (picByte);

            NettyTcpServer.getInstance().sendMsgToServer(commonResponseEntity,
                    future -> {
                        if (future.isSuccess()) {
                            Log.i(Log.TAG, "Write auth successful");
                        } else {
                            Log.i(Log.TAG, "Write auth error");
                        }
                    });
        } catch (Exception e) {
            Log.i(Log.TAG, e.getMessage());
        }
    }


    private void captureContinuousPictures() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            // 锁定AE调节，否则录像或视频画面在特定光线条件下会不停闪烁
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true); // Camera2：锁定自动曝光
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO); // Camera2：构建下一次请求参数
            logExposureRequest("captureContinuousPictures", mPreviewRequestBuilder);
            mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler); // Camera2：持续提交预览请求
        } catch (Exception e) {
            Log.i(Log.TAG, "视频预览异常：" + e.getMessage());
        }
    }

    ///
    private void captureStillPicture() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (isVideoFramePhotoMode()) {
                // 保险保护：拉流/录像期间禁止走 Camera2 JPEG still capture，
                // 必须由 mOnImageAvailableListener 从 YUV_420_888 视频帧完成抓拍。
                Log.i(Log.TAG, "视频态禁止 JPEG still capture，等待当前 YUV 视频帧完成拍照，camID = " + camID);
                return;
            }

            CaptureRequest.Builder captureBuilder = // Camera2：发送给HAL的请求
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE); // Camera2：创建请求模板

            ImageReader targetReader = mStillImageReader != null ? mStillImageReader : mImageReader; // Camera2：接收预览/抓拍帧
            if (targetReader == null) {
                photoDone.set(true); // 条件：takePhoto等它变true
                mCameraPhtotingLock.notifyLock(); // 同步：takePhoto等待，保存/失败/超时唤醒
                return; // 返回：结束当前方法
            }

            captureBuilder.addTarget(targetReader.getSurface()); // Camera2：绑定输出Surface

            captureBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true); // Camera2：锁定自动曝光
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100); // Camera2：设置JPEG质量
            captureBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE); // Camera2：声明抓拍意图
            captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY); // Camera2：发送给HAL的请求

            captureBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE, // Camera2：发送给HAL的请求
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE // Camera2：发送给HAL的请求
            );

            captureBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER, // Camera2：触发自动对焦
                    CameraMetadata.CONTROL_AF_TRIGGER_IDLE
            );

            if (mKeyAisRequestMode != null) {
                captureBuilder.set(mKeyAisRequestMode, new int[]{2});
            }

            CameraCaptureSession.CaptureCallback captureCallback = // Camera2：输出会话
                    new CameraCaptureSession.CaptureCallback() { // Camera2：输出会话
                        @Override
                        public void onCaptureCompleted( // 回调：单次请求完成
                                                        @NonNull CameraCaptureSession session, // Camera2：输出会话
                                                        @NonNull CaptureRequest request, // Camera2：发送给HAL的请求
                                                        @NonNull TotalCaptureResult result) { // Camera2：HAL返回帧元数据

                            Log.i(Log.TAG, "拍摄照片成功");

                            if (mKeyAisResult != null) {
                                int[] resultModes = result.get(mKeyAisResult);
                                if (resultModes != null) {
                                    for (int mode : resultModes) { // 循环：遍历数据
                                        Log.i(Log.TAG, "MFB Result Mode: " + mode);
                                    }
                                } else {
                                    Log.i(Log.TAG, "captureStillPicture::MFB Result Mode not available.");
                                }
                            }

                            logCaptureResult("captureStillPictureCompleted", result);
                            restorePreviewRepeatingAfterStillCapture();
                        }
                    };

            logExposureRequest("captureStillPicture", captureBuilder);
            mPreviewSession.capture( // Camera2：提交单次请求
                    captureBuilder.build(), // Camera2：生成不可变请求
                    captureCallback,
                    mBackgroundHandler // 线程：承接Camera2回调
            );

        } catch (Exception e) {
            Log.i(Log.TAG, "拍摄照片异常：" + e.getMessage());

            restorePreviewRepeatingAfterStillCapture();
            photoDone.set(true); // 条件：takePhoto等它变true
            mCameraPhtotingLock.notifyLock(); // 同步：takePhoto等待，保存/失败/超时唤醒
        }
    }

    private void restorePreviewRepeatingAfterStillCapture() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (mPreviewSession == null || mPreviewRequestBuilder == null) { // Camera2：构建下一次请求参数
                return; // 返回：结束当前方法
            }
            if (isRecording()) {
                refreshLowNoiseRepeatingRequest(getVideoCodec(streamType), true);
            } else if (isLiving() || enableLiveEncode) {
                refreshLowNoiseRepeatingRequest(null, false);
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "Restore preview after still capture error: " + e.getMessage());
        }
    }
    ///

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_AF_LOCK = 1;
    private static final int STATE_WAITING_AE_LOCKING = 3;
    private static final int STATE_PICTURE_TAKING = 4;
    private static final int STATE_VIDEO_RECORDING = 5;
    private static final int STATE_VIDEO_LIVING = 6;
    private int mState = STATE_PREVIEW;
    private volatile boolean previewReady;
    private volatile boolean mPreviewSessionVideoMode = false;
    private volatile int mSessionWidth = 0;
    private volatile int mSessionHeight = 0;

    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            logCaptureResult("captureCallback", result);
            switch (mState) {
                case STATE_PREVIEW:
                    break;
                case STATE_WAITING_AF_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (System.currentTimeMillis() - mLockFocusTime > 3 * 1000) {
                        Log.i(Log.TAG, "对焦3秒超时，开始自动曝光");
                        runPrecaptureSequence();
                    } else if (afState == null) {

                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        Log.i(Log.TAG, String.format("对焦%s，焦点锁定", CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ? "成功" : "失败")); // 状态机：AF/AE/抓拍/视频流转
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState != null && aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            Log.i(Log.TAG, "曝光结束，自动曝光很好");
                            mState = STATE_PREVIEW;
                            mCameraFocusLock.notifyLock();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_AE_LOCKING: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE); // Camera2：HAL返回帧元数据
                    if (System.currentTimeMillis() - mLockFocusTime > 3 * 1000) {
                        Log.i(Log.TAG, "曝光结束，自动曝光3秒超时");
                        mState = STATE_PREVIEW; // 状态机：AF/AE/抓拍/视频流转
                        mCameraFocusLock.notifyLock(); // 同步：lockFocus等待，AF/AE回调唤醒
                    } else if (aeState == null) {

                    } else if (aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        Log.i(Log.TAG, "曝光结束，需要补光");
                        mState = STATE_PREVIEW; // 状态机：AF/AE/抓拍/视频流转
                        mCameraFocusLock.notifyLock(); // 同步：lockFocus等待，AF/AE回调唤醒
                    } else if (aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                            aeState == CaptureResult.CONTROL_AE_STATE_LOCKED) { // 状态机：AF/AE/抓拍/视频流转
                        Log.i(Log.TAG, "曝光结束，自动曝光很好");
                        mState = STATE_PREVIEW; // 状态机：AF/AE/抓拍/视频流转
                        mCameraFocusLock.notifyLock(); // 同步：lockFocus等待，AF/AE回调唤醒
                    }
                    break;
                }
                case STATE_PICTURE_TAKING: // 状态机：AF/AE/抓拍/视频流转
                    captureStillPicture();
                    /////
                    if (mKeyAisResult != null) {
                        int[] resultModes = result.get(mKeyAisResult);
                        if (resultModes != null) {
                            for (int resMode : resultModes) { // 循环：遍历数据
                                Log.i(Log.TAG, "MFB Result Mode: " + resMode);
                            }
                        } else {
                            Log.i(Log.TAG, "MFB Result Mode not available.");
                        }
                    }
                    /////
                    mState = STATE_PREVIEW;
                    previewReady = true;
                    break;
                case STATE_VIDEO_LIVING:
                case STATE_VIDEO_RECORDING:
                    captureContinuousPictures();
//                    /////
//                    if (mKeyAisResult != null) {
//                        int[] resultModes = result.get(mKeyAisResult);
//                        if (resultModes != null) {
//                            for (int resMode : resultModes) {
//                                Log.i(Log.TAG, "MFB Result Mode: " + resMode);
//                            }
//                        } else {
//                            Log.i(Log.TAG, "MFB Result Mode not available.");
//                        }
//                    }
//                    /////
                    mState = STATE_PREVIEW; // 状态机：AF/AE/抓拍/视频流转
                    previewReady = true; // 条件：会话可出帧后才处理Image
                    break;
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, // Camera2：输出会话
                                        @NonNull CaptureRequest request, // Camera2：发送给HAL的请求
                                        @NonNull CaptureResult partialResult) { // Camera2：HAL返回帧元数据
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, // 回调：单次请求完成
                                       @NonNull CaptureRequest request, // Camera2：发送给HAL的请求
                                       @NonNull TotalCaptureResult result) { // Camera2：HAL返回帧元数据
            process(result);
        }
    };

    private void runPrecaptureSequence() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            //Range<Integer> defaultFps = mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE);
            //Log.i(Log.TAG, "开始自动曝光,默认：" + defaultFps);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START); // Camera2：触发预曝光
            mState = STATE_WAITING_AE_LOCKING;   // 3
            mLockFocusTime = System.currentTimeMillis();
            /////
            // 要先AF和AE，再进行多帧降噪，不然会AE失败！
            if (mKeyAisRequestMode != null) {
                mPreviewRequestBuilder.set(mKeyAisRequestMode, new int[]{2}); // Camera2：构建下一次请求参数
                // 需要等待多帧降噪完成才能抓拍
                if (mCameraPhotoing) { // 状态：当前正在抓拍
                    SystemClock.sleep(5000); // 阻塞：当前线程睡眠
                }
            }
            /////
            logExposureRequest("runPrecaptureSequence", mPreviewRequestBuilder);
            mPreviewSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler); // Camera2：提交单次请求
        } catch (Exception e) {
            Log.i(Log.TAG, "自动曝光异常：" + e.getMessage());
        }
    }

    private void lockFocus(int timeoutMilsec, int captureMode, boolean isRecordVideo, Settings.VideoCodec vc,boolean videoMark) { /////；成员：保存运行状态
        try { // 异常：保护相机/IO调用
            //Log.i(Log.TAG, "开始自动对焦");
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface()); // Camera2：绑定输出Surface

//            设置 Camera 的自动曝光模块 AE 目标帧率范围为固定fps。
            if (isRecordVideo){
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(vc.frame, vc.frame));   // 摄像头帧率  摄像头最大帧率为60fps，程序的处理速度<=10fps，可以优化程序的处理速度。；Camera2：限制帧率范围

            }else if (videoMark){
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(10, 10));   // 摄像头帧率  摄像头最大帧率为60fps，程序的处理速度<=10fps，可以优化程序的处理速度。；Camera2：限制帧率范围
            }else {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(30, 30));
            }
            /// ????
            applyLowNoiseCaptureRequestParameters(vc, isRecordVideo);  // 这里面又会再设置一次 FPS。最终生效的是该函数最后写入的值
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, captureMode); ///// AF：自动对焦；Camera2：构建下一次请求参数
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START); // Camera2：触发自动对焦
            mState = STATE_WAITING_AF_LOCK; //  1
            // 告诉 mCaptureCallback 当前正在等待自动对焦 AF 完成，然后根据 AF 结果决定下一步是否进入 AE 自动曝光流程

            mLockFocusTime = System.currentTimeMillis();
            mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler); // Camera2：持续提交预览请求
            mCameraFocusLock.waitLock(timeoutMilsec); // 同步：lockFocus等待，AF/AE回调唤醒
        } catch (Exception e) {
            Log.i(Log.TAG, "对焦异常：" + e.getMessage());
        }
    }

    private void unlockFocus() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (mPreviewRequestBuilder != null) { // Camera2：构建下一次请求参数
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, // Camera2：触发自动对焦
                        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "取消对焦异常：" + e.getMessage());
        }
    }

    private void startBackgroundThread() { // 入口：方法定义
        mBackgroundThread = new HandlerThread("CameraBackground"); // Android：串行消息线程
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper()); // 线程：承接Camera2回调
    }

    private void stopBackgroundThread() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (mBackgroundThread != null) {
                mBackgroundThread.quitSafely();
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null; // 线程：承接Camera2回调
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "摄像头线程退出异常：" + e.getMessage());
        }
    }

    private Handler getCameraWorkHandler() {
        synchronized (this) {
            if (mCameraWorkThread == null || !mCameraWorkThread.isAlive() || mCameraWorkHandler == null) {
                mCameraWorkThread = new HandlerThread("MIPI业务线程-" + camID);
                mCameraWorkThread.start();
                mCameraWorkHandler = new Handler(mCameraWorkThread.getLooper());
            }
            return mCameraWorkHandler;
        }
    }
    private void createPreviewSession(int width, int height, boolean video) { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (width <= 0 || height <= 0) {
                Log.i(Log.TAG, "创建摄像头会话失败，无效分辨率[" + width + ":" + height + "]，camID = " + camID);
                return;
            }
            if (mCameraDevice == null) {
                Log.i(Log.TAG, "创建摄像头会话失败，CameraDevice 为空，camID = " + camID);
                return;
            }

            previewReady = false;
            closePreviewSession();
            closeImageReader();
            closeStillImageReader();

            List<Surface> surfaces = new ArrayList<>(); // Android：Camera输出端

            if (video) {
                // 拉流/录像：只保留 YUV 输出，拍照时直接从当前 YUV 帧取一帧，避免视频会话中追加 JPEG 输出导致 HAL 卡死。
                mImageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, VIDEO_IMAGE_READER_MAX_IMAGES);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                surfaces.add(mImageReader.getSurface());
                Log.i(Log.TAG, "创建视频会话，camID = " + camID
                        + "，size = " + width + "x" + height
                        + "，format = YUV_420_888"
                        + "，maxImages = " + VIDEO_IMAGE_READER_MAX_IMAGES);
            } else {
                // 空闲拍照：YUV 小预览只用于 3A 收敛，真正出图固定走独立 JPEG ImageReader。
                Point previewSize = getStillPreviewResolution(width, height);
                mImageReader = ImageReader.newInstance(previewSize.x, previewSize.y, ImageFormat.YUV_420_888, 3);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                surfaces.add(mImageReader.getSurface());

                Point stillSize = getConfiguredPhotoResolution();
                if (stillSize == null) {
                    stillSize = new Point(width, height);
                }
                mStillImageReader = ImageReader.newInstance(stillSize.x, stillSize.y, ImageFormat.JPEG, 2);
                mStillImageReader.setOnImageAvailableListener(mStillImageAvailableListener, mBackgroundHandler);
                surfaces.add(mStillImageReader.getSurface());

                Log.i(Log.TAG, "创建 JPEG 拍照会话，camID = " + camID
                        + "，preview = " + previewSize.x + "x" + previewSize.y
                        + "，jpeg = " + stillSize.x + "x" + stillSize.y);
            }

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() { // Camera2：配置HAL输出流
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) { // 回调：Camera2会话已配置
                    if (mCameraDevice == null) {
                        try {
                            cameraCaptureSession.close();
                        } catch (Exception ignored) {
                        }
                        mCameraOpenCloseLock.notifyLock();
                        return;
                    }
                    mPreviewSession = cameraCaptureSession;
                    mPreviewSessionVideoMode = video;
                    mSessionWidth = width;
                    mSessionHeight = height;
                    mCameraOpenCloseLock.notifyLock();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) { // 回调：Camera2会话配置失败
                    try {
                        cameraCaptureSession.close();
                    } catch (Exception ignored) {
                    }
                    closePreviewSession();
                    closeImageReader();
                    closeStillImageReader();
                    mCameraOpenCloseLock.notifyLock();
                }
            }, mBackgroundHandler);

            boolean configured = mCameraOpenCloseLock.waitLock(5000);
            if (!configured || mPreviewSession == null) {
                Log.i(Log.TAG, "创建摄像头会话等待超时/失败，camID = " + camID
                        + "，video = " + video
                        + "，size = " + width + "x" + height);
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "create camera session exception: " + e.getMessage());
            closePreviewSession();
            closeImageReader();
            closeStillImageReader();
            mCameraOpenCloseLock.notifyLock();
        }
    }


    CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            mCameraOpenCloseLock.notifyLock();
            Log.i(Log.TAG, "打开摄像头" + camID +"成功");
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) { // 回调：相机断开
            mCameraDevice = null; // Camera2：已打开的相机句柄
            previewReady = false; // 条件：会话可出帧后才处理Image
            mDualSessionStarted = false;
            mDualSessionStarting = false;
            closePreviewSession();
            clearState(DevState.OPENING);
            mCameraOpenCloseLock.notifyLock(); // 同步：唤醒等待线程
            cameraDevice.close();
            Log.i(Log.TAG, "MIPI camera disconnected, camID = " + camID);
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) { // 回调：相机错误
            mCameraDevice = null; // Camera2：已打开的相机句柄
            previewReady = false; // 条件：会话可出帧后才处理Image
            mDualSessionStarted = false;
            mDualSessionStarting = false;
            closePreviewSession();
            clearState(DevState.OPENING);
            mCameraOpenCloseLock.notifyLock(); // 同步：唤醒等待线程
            cameraDevice.close();
            Log.i(Log.TAG, "打开摄像头失败，camID = " + camID + "，error=" + error);
        }
    };

    /*    private void surfaceInit() {
            mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    Log.i(Log.TAG, "surfaceCreated");
                }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.i(Log.TAG, String.format("surfaceChanged, format=%d, width=%d, height=%d", format, width, height));
            }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    Log.i(Log.TAG, "surfaceDestroyed");
                }
            });
        }*/
    private StreamConfigurationMap streamConfigurationMap; // 成员：保存运行状态

    protected void openCamera() { // Camera2：异步打开相机
        try { // 异常：保护相机/IO调用
            if (ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { // Android：检查相机权限
                Log.i(Log.TAG, "摄像头打开没有权限");
                return; // 返回：结束当前方法
            }
            CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE); // Android：连接CameraService
            int camId = camID % 2;
            if (camId >= cameraManager.getCameraIdList().length) {
                Log.i(Log.TAG, "摄像头" + camId + "超过支持的摄像头总数：" + cameraManager.getCameraIdList().length);
                return; // 返回：结束当前方法
            }

            String cameraId = String.valueOf(camId);

            ///
            if (mCameraDevice != null) { // Camera2：已打开的相机句柄
                return; // 返回：结束当前方法
            }

            /// 获取当前 Camera 的静态能力信息
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            minFocusDist = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE); // 获取镜头最小对焦距离

            if (minFocusDist > 0) {
                Log.i(Log.TAG, "MIPI摄像头最小对焦距离为" + 1 / minFocusDist * 100 + "厘米");
            }

            mKeyAisAvailableModes = null;
            mKeyAisResult = null;
            mKeyAisRequestMode = null;

            List<CameraCharacteristics.Key<?>> keyList = cameraCharacteristics.getKeys();
            for (CameraCharacteristics.Key<?> key : keyList) { // 循环：遍历数据
                if (key.getName().equals(AIS_AVAILABLE_MODES_KEY_NAME)) {
                    mKeyAisAvailableModes = (CameraCharacteristics.Key<int[]>) key;
                    Log.i(Log.TAG, "Found CameraCharacteristics Key: " + AIS_AVAILABLE_MODES_KEY_NAME);
                }
            }

            List<CaptureResult.Key<?>> resultKeyList = cameraCharacteristics.getAvailableCaptureResultKeys(); // Camera2：HAL返回帧元数据
            for (CaptureResult.Key<?> resultKey : resultKeyList) { // Camera2：HAL返回帧元数据
                if (resultKey.getName().equals(AIS_RESULT_MODE_KEY_NAME)) {
                    mKeyAisResult = (CaptureResult.Key<int[]>) resultKey; // Camera2：HAL返回帧元数据
                    Log.i(Log.TAG, "Found CaptureResult Key: " + AIS_RESULT_MODE_KEY_NAME); // Camera2：HAL返回帧元数据
                }
            }

            List<CaptureRequest.Key<?>> requestKeyList = cameraCharacteristics.getAvailableCaptureRequestKeys(); // Camera2：发送给HAL的请求
            for (CaptureRequest.Key<?> requestKey : requestKeyList) { // Camera2：发送给HAL的请求
                if (requestKey.getName().equals(AIS_REQUEST_MODE_KEY_NAME)) {
                    mKeyAisRequestMode = (CaptureRequest.Key<int[]>) requestKey; // Camera2：发送给HAL的请求
                    Log.i(Log.TAG, "Found CaptureRequest Key: " + AIS_REQUEST_MODE_KEY_NAME); // Camera2：发送给HAL的请求
                }
            }

            if (mKeyAisAvailableModes != null) {
                int[] availableModes = cameraCharacteristics.get(mKeyAisAvailableModes);
                if (availableModes != null) {
                    for (int mode : availableModes) { // 循环：遍历数据
                        Log.i(Log.TAG, "Supported MFB Mode: " + mode);
                    }
                } else {
                    Log.i(Log.TAG, "No available MFB modes.");
                }
            } else {
                Log.i(Log.TAG, "MFB Key not found.");
            }

            // 获取该摄像头支持哪些图像输出格式、分辨率和对应能力
            streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            startBackgroundThread();

            cameraManager.openCamera(cameraId, mStateCallback, mBackgroundHandler); // Camera2：异步打开相机
//            mCameraOpenCloseLock.waitLock(2500);

            boolean opened = mCameraOpenCloseLock.waitLock(2500); // 同步：等待唤醒或超时
            if (!opened || mCameraDevice == null) { // Camera2：已打开的相机句柄
                Log.i(Log.TAG, "打开摄像头等待超时，延迟确认，camID = " + camID);

                if (mBackgroundHandler != null) { // 线程：承接Camera2回调
                    mBackgroundHandler.postDelayed(() -> { // 线程：承接Camera2回调
                        if (mCameraDevice == null && !isLiving() && !isRecording() && !mCameraPhotoing) { // 状态：当前正在抓拍
                            Log.i(Log.TAG, "打开摄像头延迟确认仍失败，清理资源，camID = " + camID);
                            closePreviewSession();
                            closeImageReader();
                            closeStillImageReader();
                            stopBackgroundThread();
                            clearState(DevState.OPENING);
                        }
                    }, 3000);
                }
            }

        } catch (Exception e) {
            Log.i(Log.TAG, String.format("打开摄像头%d异常：%s", camID, e.getMessage()));
        }
    }

    //    private void closePreviewSession() {
//        if (mPreviewSession != null) {
//            mPreviewSession.close();
//            mPreviewSession = null;
//        }
//    }
    ///
    private void closePreviewSession() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (mPreviewSession != null) { // Camera2：向HAL提交请求的会话
                try {
                    mPreviewSession.stopRepeating(); // Camera2：停止重复请求
                } catch (Exception ignored) {
                }
                try {
                    mPreviewSession.abortCaptures(); // Camera2：取消未完成请求
                } catch (Exception ignored) {
                }
                mPreviewSession.close(); // Camera2：关闭输出会话
                mPreviewSession = null;
            }
        } catch (Exception e) {
            mPreviewSession = null;
        } finally {
            mPreviewRequestBuilder = null;
            mPreviewSessionVideoMode = false;
            mDualSessionStarted = false;
            mDualSessionStarting = false;
            previewReady = false;
            mSessionWidth = 0;
            mSessionHeight = 0;
        }
    }

    private void closeImageReader() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (mImageReader != null) { // Camera2：接收预览/抓拍帧
                mImageReader.close(); // Camera2：接收预览/抓拍帧
                mImageReader = null; // Camera2：接收预览/抓拍帧
            }
        } catch (Exception e) {
            mImageReader = null; // Camera2：接收预览/抓拍帧
        }
    }

    private void closeStillImageReader() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (mStillImageReader != null) { // Camera2：独立静态图输出
                mStillImageReader.close(); // Camera2：独立静态图输出
                mStillImageReader = null; // Camera2：独立静态图输出
            }
        } catch (Exception e) {
            mStillImageReader = null; // Camera2：独立静态图输出
        }
    }

    private Point getConfiguredPhotoResolution() { // 入口：方法定义
        Point size = Settings.PhotoConfig.getImageSize(photoConfig.size);
        if (size == null) {
            size = new Point(1920, 1080);
        }
        if (is6735) {
            return new Point(1280, 720); // 返回：结束当前方法
        }
        if (streamConfigurationMap != null) {
            Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
            return getBestSize2(sizes, size.x, size.y); // 返回：结束当前方法
        }
        return size; // 返回：结束当前方法
    }

    private Point getStillPreviewResolution(int stillWidth, int stillHeight) {
        int targetWidth = Math.min(stillWidth > 0 ? stillWidth : 1280, 1536);
        int targetHeight = Math.min(stillHeight > 0 ? stillHeight : 720, 864);

        if (is6735) {
            return new Point(1280, 720);
        }

        try {
            if (streamConfigurationMap != null) {
                Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888);
                return getBestSize2(sizes, targetWidth, targetHeight);
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "获取 JPEG 拍照预览尺寸异常：" + e.getMessage());
        }

        return new Point(targetWidth, targetHeight);
    }

    private boolean isVideoFramePhotoMode() {
        // 拉流/录像中的拍照必须直接取当前 YUV 视频帧。
        // 这里不能依赖 rtph264 != null，因为拉流启动窗口内 liveStarting=true，
        // 但 RTPH264 可能还没初始化；如果此时误判为空闲，就会错误创建 JPEG still session。
        return isLiving()
                || liveStarting
                || enableLiveEncode
                || isRecording()
                || videoStarting;
    }

    private boolean isDeviceBusyOrStarting() {
        return isLiving()
                || enableLiveEncode
                || liveStarting
                || isRecording()
                || videoStarting
                || mCameraPhotoing;
    }

    private static boolean areBothCameraDevicesOpenedLocked() {
        return sCamera0Device != null
                && sCamera1Device != null
                && sCamera0Device.mCameraDevice != null
                && sCamera1Device.mCameraDevice != null;
    }

    private void failCurrentPhoto(String reason) {
        Log.i(Log.TAG, "拍照失败：" + reason + "，camID = " + camID);
        takePhotoOnce.set(false);
        photoDone.set(true);
        mCameraPhtotingLock.notifyLock();
    }

    private boolean waitPhotoDone(int timeoutMs) {
        long start = SystemClock.uptimeMillis();
        while (!photoDone.get()) {
            long remain = timeoutMs - (SystemClock.uptimeMillis() - start);
            if (remain <= 0) {
                break;
            }
            mCameraPhtotingLock.waitLock((int) remain);
        }
        return photoDone.get();
    }


    private volatile boolean enableLiveEncode = false; // 成员：保存运行状态
    private volatile boolean liveStarting = false; // 成员：保存运行状态
    private volatile boolean videoStarting = false; // 成员：保存运行状态

    public void setEnableLiveEncode(boolean enable) { // 入口：方法定义
        enableLiveEncode = enable;
    }

    public void stopLiveAndCloseBothIfIdle() { // 入口：方法定义
        liveStarting = false;
        setEnableLiveEncode(false);
        closeBothCameraIfNoLive();
    }

    private void ensureMipiVideoEncoder(int stream, boolean mipiMark) { // 入口：方法定义
        synchronized (mipiStreamLock) { // 同步：互斥访问共享状态
            if (mediaCodec == null) {
                initVideoEncoder(stream, mResolution.x, mResolution.y, mipiMark);
                mipiLivePpsSps = null;
                mipiLiveFirstFrameTimestamp = 0;
            }
        }
    }

    private void releaseMipiVideoEncoderIfIdle() { // 入口：方法定义
        synchronized (mipiStreamLock) { // 同步：互斥访问共享状态
            if (!isLiving() && !isRecording() && mediaCodec != null) {
                uninitVideoEncoder();
                mipiLivePpsSps = null;
                mipiLiveFirstFrameTimestamp = 0;
            }
        }
    }

    private void tryStartMipiMuxer() { // 入口：方法定义
        if (muxerStarted || mediaMuxer == null) return;

        if (useAudio) {
            if (videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                mediaMuxer.start();
                muxerStarted = true;
                muxerEverStarted = true;
            }
        } else if (videoTrackIndex >= 0) {
            mediaMuxer.start();
            muxerStarted = true;
            muxerEverStarted = true;
        }
    }

    private void releaseMipiMuxer() { // 入口：方法定义
        if (mediaMuxer == null) return;

        try { // 异常：保护相机/IO调用
            if (muxerStarted) {
                mediaMuxer.stop();
            }
            mediaMuxer.release();
            Log.i(Log.TAG, "release MIPI muxer success"
                    + "，samples = " + mipiRecordSamplesWritten
                    + "，bytes = " + mipiRecordBytesWritten
                    + "，keyFrames = " + mipiRecordKeyFramesWritten);
        } catch (Exception e) {
            Log.i(Log.TAG, "release MIPI muxer error: " + e.getMessage());
        } finally {
            mediaMuxer = null;
            videoTrackIndex = -1;
            audioTrackIndex = -1;
            muxerStarted = false;
            muxerEverStarted = false;
            avStartNs = 0;
        }
    }
    private boolean ensureVideoPreviewSession(Settings.VideoCodec vc, boolean isRecordVideo) { // 入口：方法定义
        if (mCameraDevice == null || vc == null) { // Camera2：已打开的相机句柄
            return false;
        }

        if (mResolution == null) {
            mResolution = Settings.VideoCodec.getResolution(vc.resolution);
        }
        if (mResolution == null) {
            mResolution = new Point(1536, 864);
        }
        if (is6735) {
            mResolution = new Point(1280, 720);
        } else if (mResolution.x > 1536 || mResolution.y > 864) {
            mResolution = new Point(1536, 864);
        }

        boolean sessionMatches = mPreviewSession != null
                && mPreviewSessionVideoMode
                && mSessionWidth == mResolution.x
                && mSessionHeight == mResolution.y;

        if (!sessionMatches) {
            closePreviewSession();
            closeImageReader();
            closeStillImageReader();
            createPreviewSession(mResolution.x, mResolution.y, true);
        }

        if (mPreviewSession == null || !mPreviewSessionVideoMode) {
            Log.i(Log.TAG, "切换视频会话失败，PreviewSession 为空或不是视频模式，camID = " + camID);
            return false;
        }

        lockFocus(
                10000,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
                isRecordVideo,
                isRecordVideo ? vc : null,
                true
        );

        previewReady = true;
        mDualSessionStarted = true;
        return true;
    }

    private void resetMipiRecordStats() { // 入口：方法定义
        mipiRecordSamplesWritten = 0;
        mipiRecordBytesWritten = 0;
        mipiRecordKeyFramesWritten = 0;
    }
    private static void closeBothCameraIfNoLive() { // 入口：方法定义
        Camera2Device cam0;
        Camera2Device cam1;
        synchronized (sDualCameraLock) { // 同步：保护双MIPI共享状态
            if (sDualClosing || sDualStarting) {
                return;
            }
            cam0 = sCamera0Device;
            cam1 = sCamera1Device;

            boolean cam0Live = cam0 != null && (cam0.isLiving() || cam0.enableLiveEncode || cam0.liveStarting);
            boolean cam1Live = cam1 != null && (cam1.isLiving() || cam1.enableLiveEncode || cam1.liveStarting);
            boolean cam0Photoing = cam0 != null && cam0.mCameraPhotoing;
            boolean cam1Photoing = cam1 != null && cam1.mCameraPhotoing;
            boolean dualPhotoPending = sDualPhotoTaskCount > 0;
            boolean cam0Recording = cam0 != null && (cam0.isRecording() || cam0.videoStarting);
            boolean cam1Recording = cam1 != null && (cam1.isRecording() || cam1.videoStarting);

            Log.i(Log.TAG, "检查是否需要释放双路 Camera"
                    + "，cam0Live = " + cam0Live
                    + "，cam1Live = " + cam1Live
                    + "，cam0Photoing = " + cam0Photoing
                    + "，cam1Photoing = " + cam1Photoing
                    + "，dualPhotoPending = " + dualPhotoPending
                    + "，cam0Recording = " + cam0Recording
                    + "，cam1Recording = " + cam1Recording);

            if (cam0Live || cam1Live || cam0Photoing || cam1Photoing || dualPhotoPending || cam0Recording || cam1Recording) {
                Log.i(Log.TAG, "仍有直播、拍照或录像任务，不释放双路 Camera");
                return;
            }

            sDualClosing = true;
        }

        try {
            if (cam0 != null) {
                cam0.closeCamera();
            }
            if (cam1 != null && cam1 != cam0) {
                cam1.closeCamera();
            }
        } finally {
            synchronized (sDualCameraLock) {
                sDualStarted = false;
                sDualStarting = false;
                sDualClosing = false;
                sDualCameraLock.notifyAll();
            }
        }
        Log.i(Log.TAG, "双路 Camera 已释放");
    }
    ///

    //    public synchronized void closeCamera() {
//        super.closeCamera();
//        if (mImageReader != null) {
//            mImageReader.close();
//            mImageReader = null;
//        }
//        if (mCameraDevice != null) {
//            mCameraDevice.close();
//            mCameraDevice = null;
//        }
//
//        closePreviewSession();
//        stopBackgroundThread();
//        clearState(DevState.OPENING);
//    }
    public synchronized void closeCamera() { // 入口：方法定义
        super.closeCamera();
        setEnableLiveEncode(false);
        mDualSessionStarted = false;
        mDualSessionStarting = false;
        liveStarting = false;
        videoStarting = false;
        previewReady = false; // 条件：会话可出帧后才处理Image

        closePreviewSession();
        closeImageReader();
        closeStillImageReader();
        if (mCameraDevice != null) { // Camera2：已打开的相机句柄
            mCameraDevice.close(); // Camera2：已打开的相机句柄
            mCameraDevice = null; // Camera2：已打开的相机句柄
        }

        stopBackgroundThread();
        clearState(DevState.OPENING);

        synchronized (sDualCameraLock) { // 同步：保护双MIPI共享状态
            sDualStarted = false;
        }
    }

    /*
        开始录制视频
     */
    @Override
    public boolean videoStop() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            videoStarting = false;
            //Log.i(Log.TAG, "停止录制");
//            unlockFocus();
//            close();
            super.videoStop();
            /////
            releaseMipiMuxer();
            if (useAudio) {
                uninitAudioEncoder();
            }
            releaseMipiVideoEncoderIfIdle();
            if (!mCameraPhotoing && !isLiving()) { // 状态：当前正在抓拍
                closeBothCameraIfNoLive(); ///
            }
            /////
        } catch (Exception e) {
            Log.i(Log.TAG, "停止录像异常：" + e);
            return false; // 返回：结束当前方法
        }
        return true; // 返回：结束当前方法
    }

    @Override   // 录制短视频使用配置文件中的分辨率和I帧间隔
    public boolean videoStart(int stream, String filename, int duration, boolean upload) { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (isRecording()) {
                Log.i(Log.TAG, "录像失败，当前已经在录像，camID = " + camID);
                videoStarting = false;
                return false;
            }
            videoStarting = true;

            Settings.VideoCodec vc = getVideoCodec(stream);
            mResolution = vc != null ? Settings.VideoCodec.getResolution(vc.resolution) : null;

            if (mResolution == null) {
                Log.i(Log.TAG, "录像分辨率为空，使用默认 1536x864，camID = " + camID);
                mResolution = new Point(1536, 864);
            }
            if (is6735) {
                mResolution = new Point(1280, 720);
            } else if (mResolution.x > 1536 || mResolution.y > 864) {
                mResolution = new Point(1536, 864);
            }

            Log.i(Log.TAG, "MIPI录制编码分辨率，camID = " + camID
                    + "，width = " + mResolution.x
                    + "，height = " + mResolution.y
                    + "，frame = " + (vc != null ? vc.frame : -1));

            if (mCameraDevice == null) {
                boolean opened = open(stream, null, 10, false, true, true);
                if (!opened) {
                    Log.i(Log.TAG, "录像失败，双路 CameraDevice 打开失败，camID = " + camID);
                    videoStarting = false;
                    return false;
                }
            }

            if (mPreviewSession == null || !mPreviewSessionVideoMode
                    || mSessionWidth != mResolution.x || mSessionHeight != mResolution.y) {
                if (!ensureVideoPreviewSession(vc, true)) {
                    Log.i(Log.TAG, "录像失败，无法创建/切换视频会话"
                            + "，camID = " + camID
                            + "，mPreviewSessionVideoMode = " + mPreviewSessionVideoMode);
                    videoStarting = false;
                    closeBothCameraIfNoLive();
                    return false;
                }
            }

            mState = STATE_VIDEO_RECORDING;
            refreshLowNoiseRepeatingRequest(vc, true);

            super.videoStart(stream, filename, duration, upload);
            String tmpfile = MainActivity.DATA_DIR + "record_" + id + ".mp4";

            mediaMuxer = new MediaMuxer(tmpfile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4); // Android：视频编码/封装
            muxerStarted = false;
            videoTrackIndex = -1;
            resetMipiRecordStats();

            if (useAudio) {
                audioTrackIndex = -1;
                avStartNs = System.nanoTime();
                lastVideoPtsUs = 0;
                lastAudioPtsUs = 0;
                initAudioRecord();
                initAudioEncoder();
                startAudio();
            }

            ensureMipiVideoEncoder(stream, isLiving() || enableLiveEncode);

            new Timer("recordStop").schedule(new TimerTask() {
                @Override
                public void run() { // 入口：方法定义
                    Runnable finishRecord = () -> {
                        videoStop();
                        if (upload) {
                            Utils.su("mv " + tmpfile + " " + filename);
                        } else {
                            File file = new File(tmpfile);
                            File finalFile = new File(filename);
                            file.renameTo(new File(MainActivity.FILE_PATH + id + File.separator + finalFile.getName()));
                            Log.i(Log.TAG, "MIPI摄像头文件不上传，修改文件为：" + (MainActivity.FILE_PATH + id + File.separator + finalFile.getName()));
                        }
                        File recordedFile = upload
                                ? new File(filename)
                                : new File(MainActivity.FILE_PATH + id + File.separator + new File(filename).getName());
                        Log.i(Log.TAG, "MIPI录制完成文件大小"
                                + "，file = " + recordedFile.getAbsolutePath()
                                + "，exists = " + recordedFile.exists()
                                + "，length = " + (recordedFile.exists() ? recordedFile.length() : -1)
                                + "，samples = " + mipiRecordSamplesWritten
                                + "，bytes = " + mipiRecordBytesWritten
                                + "，keyFrames = " + mipiRecordKeyFramesWritten);
                        if (controllerCallback != null) {
                            controllerCallback.onVideoFinished(System.currentTimeMillis(), id, streamType, filename, upload);
                        }
                    };
                    if (procVideoHandler != null) {
                        procVideoHandler.post(finishRecord);
                    } else {
                        finishRecord.run();
                    }
                }
            }, (duration + 1) * 1000);
        } catch (Exception e) {
            videoStarting = false;
            videoStop();
            Log.e(Log.TAG, "MIPI摄像头录制视频异常: " + e.getMessage());
        } finally {
            videoStarting = false;
        }
        return isRecording();
    }

    public boolean videoPause() { // 入口：方法定义
        return true; // 返回：结束当前方法
    }

    public boolean videoResume() { // 入口：方法定义
        return true; // 返回：结束当前方法
    }

    public boolean close() { // 入口：方法定义
//        closeCamera();
        stopLiveAndCloseBothIfIdle(); ///
        return true; // 返回：结束当前方法
    }

    public boolean liveStop() { // 入口：方法定义
        //Log.i(Log.TAG, "停止预览");
        try { // 异常：保护相机/IO调用
            ///
//            if (!mCameraPhotoing && !isRecording()) {
//                unlockFocus();
//                close();
//            }
            liveStarting = false;
            setEnableLiveEncode(false);
            mOnShow = false;
            ///
            rtph264 = null;
        } catch (Exception e) {
            Log.i(Log.TAG, "停止预览异常：" + e);
            return false; // 返回：结束当前方法
        } finally {
            clearState(DevState.LIVING); /////
            ///
            releaseMipiVideoEncoderIfIdle();
            if (!mCameraPhotoing && !isRecording()) { // 状态：当前正在抓拍
                closeBothCameraIfNoLive();
            }
            ///
        }
        return true; // 返回：结束当前方法
    }



    public boolean setOSD(Settings.OSD osd, boolean osdNull) { /////；成员：保存运行状态
        this.osd = osd;
        return true; // 返回：结束当前方法
    }

    public boolean setCodec(Settings.VideoCodec codec) { // 入口：方法定义
        return true; // 返回：结束当前方法
    }

    /////
//    public synchronized boolean open(int stream, onOpenCallback cb, int timeoutSeconds, boolean waitSelfCheck) {
//        if (isOpening()) {
//            Log.i(Log.TAG, "摄像头已经打开");
//            if (cb != null) cb.openSucceed();
//            return true;
//        }
//        if (mMainBoard == 1) {
//            MipiSwitch.switchTo(camID);
//        }
//        streamType = stream;
//        openCamera();
//        if (mCameraDevice == null) {
//            Log.i(Log.TAG, "打开摄像头失败");
//            if (cb != null) cb.openFailed(-1);
//            return false;
//        }
//
//        previewReady = false;
//        setState(DevState.OPENING);
//        if (cb != null) cb.openSucceed();
//        return true;
//    }
    /////
    public boolean open(int stream, onOpenCallback cb, int timeoutSeconds, boolean waitSelfCheck, boolean video, boolean isRecordVideo) { ///；成员：保存运行状态
        synchronized (sDualCameraLock) {
            int realCamId = camID % 2;
            if (realCamId == 0) {
                sCamera0Device = this;
            } else {
                sCamera1Device = this;
            }
        }

        if (!ALWAYS_OPEN_BOTH_MIPI) {
            boolean opened = openSelfOnly(stream, cb, timeoutSeconds, waitSelfCheck);
            if (opened) {
                startSessionForDualOpen(stream, video, isRecordVideo);
            }
            return opened;
        }

        Camera2Device cam0;
        Camera2Device cam1;
        synchronized (sDualCameraLock) {
            cam0 = sCamera0Device;
            cam1 = sCamera1Device;

            Log.i(Log.TAG, "双路 Camera 对象检查"
                    + "，cam0 = " + cam0
                    + "，cam1 = " + cam1
                    + "，camID = " + camID
                    + "，sDualStarted = " + sDualStarted
                    + "，sDualStarting = " + sDualStarting
                    + "，sDualClosing = " + sDualClosing);

            if (cam0 == null || cam1 == null) {
                if (cb != null) cb.openFailed(-1);
                return false;
            }

            long waitEnd = SystemClock.uptimeMillis() + 15000;
            while (sDualClosing || sDualStarting) {
                long waitMs = waitEnd - SystemClock.uptimeMillis();
                if (waitMs <= 0) {
                    break;
                }
                try {
                    sDualCameraLock.wait(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (sDualClosing || sDualStarting) {
                Log.i(Log.TAG, "等待双路 Camera 状态切换超时，camID = " + camID);
                if (cb != null) cb.openFailed(-1);
                return false;
            }

            if (sDualStarted && areBothCameraDevicesOpenedLocked()) {
                boolean sessionReady = startSessionForDualOpen(stream, video, isRecordVideo);
                if (cb != null) {
                    if (sessionReady) {
                        cb.openSucceed();
                    } else {
                        cb.openFailed(-1);
                    }
                }
                return sessionReady;
            }

            sDualStarting = true;
        }

        boolean deviceReady = false;
        boolean sessionReady = false;
        try {
            synchronized (sDualCameraLock) {
                cam0 = sCamera0Device;
                cam1 = sCamera1Device;
            }

            if (cam0 == null || cam1 == null) {
                if (cb != null) cb.openFailed(-1);
                return false;
            }

            boolean ret0 = false;
            boolean ret1 = false;
            for (int attempt = 1; attempt <= 3; attempt++) {
                Log.i(Log.TAG, "双路 CameraDevice 同步打开，attempt = " + attempt);
                ret0 = cam0.openSelfOnly(stream, null, timeoutSeconds, waitSelfCheck);
                ret1 = cam1.openSelfOnly(stream, null, timeoutSeconds, waitSelfCheck);

                if (ret0 && ret1 && cam0.mCameraDevice != null && cam1.mCameraDevice != null) {
                    break;
                }

                Log.i(Log.TAG, "双路 CameraDevice 打开未全部成功"
                        + "，camera0 = " + ret0
                        + "，camera1 = " + ret1
                        + "，attempt = " + attempt);

                // 注意：这里不能只保留单个 CameraDevice 继续工作；该板子不支持后续追加打开。
                if (cam0 != null && cam0.mCameraDevice != null && !cam0.isDeviceBusyOrStarting()) {
                    cam0.closeCamera();
                }
                if (cam1 != null && cam1 != cam0 && cam1.mCameraDevice != null && !cam1.isDeviceBusyOrStarting()) {
                    cam1.closeCamera();
                }

                SystemClock.sleep(1200);
            }

            deviceReady = ret0 && ret1 && cam0.mCameraDevice != null && cam1.mCameraDevice != null;
            if (!deviceReady) {
                Log.i(Log.TAG, "双路 CameraDevice 未全部打开成功，停止后续 session 创建");
                if (cb != null) cb.openFailed(-1);
                return false;
            }

            // CameraDevice 必须双路同时打开；session 只给当前业务通道创建/切换，另一通道保持已打开待命。
            sessionReady = startSessionForDualOpen(stream, video, isRecordVideo);
            if (cb != null) {
                if (sessionReady) {
                    cb.openSucceed();
                } else {
                    cb.openFailed(-1);
                }
            }
            return sessionReady;
        } finally {
            synchronized (sDualCameraLock) {
                sDualStarting = false;
                sDualStarted = deviceReady;
                sDualCameraLock.notifyAll();
            }
        }
    }

    ///
    public synchronized boolean openSelfOnly(int stream, onOpenCallback cb, int timeoutSeconds, boolean waitSelfCheck) { // 入口：方法定义
        if (isOpening()) {
            if (mCameraDevice != null) {
                Log.i(Log.TAG, "摄像头已经打开");
                if (cb != null) cb.openSucceed();
                return true; // 返回：结束当前方法
            }
            Log.i(Log.TAG, "摄像头状态为已打开，但 CameraDevice 为空，重新打开，camID = " + camID); // Camera2：相机设备对象
            clearState(DevState.OPENING);
        }
        if (mMainBoard == 1) {
            MipiSwitch.switchTo(camID);
        }

        streamType = stream;

        openCamera(); // Camera2：异步打开相机

        if (mCameraDevice == null) { // Camera2：已打开的相机句柄
            Log.i(Log.TAG, "打开摄像头失败");
            if (cb != null) cb.openFailed(-1);
            return false; // 返回：结束当前方法
        }

        previewReady = false; // 条件：会话可出帧后才处理Image

        Log.e(Log.TAG,"previewReady:"+previewReady);
        setState(DevState.OPENING);
        if (cb != null) cb.openSucceed();
        return true; // 返回：结束当前方法
    }
    private boolean startSessionForDualOpen(int stream, boolean video, boolean isRecordVideo) {
        boolean useVideoSession = video || isRecordVideo || isLiving() || isRecording() || enableLiveEncode;

        Settings.VideoCodec vc = getVideoCodec(stream);
        Point resolution = null;
        if (useVideoSession && vc != null) {
            resolution = Settings.VideoCodec.getResolution(vc.resolution);
        }
        if (resolution == null) {
            resolution = useVideoSession ? new Point(1536, 864) : getConfiguredPhotoResolution();
        }
        if (resolution == null) {
            resolution = useVideoSession ? new Point(1536, 864) : new Point(1920, 1080);
        }
        if (is6735) {
            resolution = new Point(1280, 720);
        } else if (useVideoSession && (resolution.x > 1536 || resolution.y > 864)) {
            resolution = new Point(1536, 864);
        }

        // 与原有直播逻辑兼容：部分 MIPI HAL 在 800x600 / 704x576 下出帧不稳定，降到 640x480。
        if (useVideoSession
                && ((resolution.x == 800 && resolution.y == 600)
                || (resolution.x == 704 && resolution.y == 576))) {
            resolution = new Point(640, 480);
        }

        mResolution = resolution;

        return startSessionAndRepeatingIfNeeded(
                resolution.x,
                resolution.y,
                useVideoSession,
                isRecordVideo,
                stream
        );
    }
    private synchronized boolean startSessionAndRepeatingIfNeeded(int width, int height, boolean video, boolean isReordVideo, int stream) { ///；成员：保存运行状态
        if (mDualSessionStarting) {
            return mPreviewSession != null;
        }
        if (mCameraDevice == null) {
            return false;
        }

        boolean sessionMatches = mPreviewSession != null
                && mPreviewSessionVideoMode == video
                && mSessionWidth == width
                && mSessionHeight == height;

        if (mDualSessionStarted && sessionMatches) {
            return true;
        }

        mDualSessionStarting = true;
        try {
            if (!sessionMatches) {
                closePreviewSession();
                closeImageReader();
                closeStillImageReader();
                createPreviewSession(width, height, video);
            }

            if (mPreviewSession == null) {
                return false;
            }

            if (video) {
                Settings.VideoCodec vc = getVideoCodec(stream);
                lockFocus(
                        10000,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
                        isReordVideo,
                        isReordVideo ? vc : null,
                        true
                );
            } else {
                lockFocus(
                        10000,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                        false,
                        null,
                        false
                );
            }

            previewReady = true;
            mDualSessionStarted = true;
            return true;
        } catch (Exception e) {
            Log.i(Log.TAG, "dual camera session exception: " + e.getMessage());
            closePreviewSession();
            closeImageReader();
            closeStillImageReader();
            return false;
        } finally {
            mDualSessionStarting = false;
        }
    }
    ///
    public boolean liveStart(int stream, int ssrc) { // 入口：方法定义
        if (isLiving() || liveStarting) {
            Log.i(Log.TAG, "拉流失败，正在播放视频，camID = " + camID);
            return false;
        }

        this.streamType = stream;
        liveStarting = true;

        getCameraWorkHandler().post(() -> {
            try {
                if (!liveStarting) {
                    return;
                }

                Settings.VideoCodec vc = getVideoCodec(stream);
                mResolution = vc != null ? Settings.VideoCodec.getResolution(vc.resolution) : null;
                if (mResolution == null) {
                    mResolution = new Point(1536, 864);
                }
                if (is6735) {
                    mResolution = new Point(1280, 720);
                } else if (mResolution.x > 1536 || mResolution.y > 864) {
                    mResolution = new Point(1536, 864);
                }
                if ((mResolution.x == 800 && mResolution.y == 600) || (mResolution.x == 704 && mResolution.y == 576)) {
                    mResolution = new Point(640, 480);
                }

                if (mCameraDevice == null) {
                    boolean opened = open(stream, null, 10, false, true, false);
                    if (!opened) {
                        Log.i(Log.TAG, "拉流失败，双路 CameraDevice 打开失败，camID = " + camID);
                        liveStarting = false;
                        setEnableLiveEncode(false);
                        return;
                    }
                }

                if (!ensureVideoPreviewSession(vc, false)) {
                    Log.i(Log.TAG, "拉流失败，无法创建/切换视频会话"
                            + "，camID = " + camID
                            + "，mPreviewSessionVideoMode = " + mPreviewSessionVideoMode);
                    liveStarting = false;
                    setEnableLiveEncode(false);
                    closeBothCameraIfNoLive();
                    return;
                }

                mOnShow = true;
                previewReady = true;
                setState(DevState.LIVING);
                mState = STATE_VIDEO_LIVING;

                rtph264 = new RTPH264(ssrc);
                mipiLivePpsSps = null;
                mipiLiveFirstFrameTimestamp = 0;
                setEnableLiveEncode(true);
                ensureMipiVideoEncoder(stream, true);
                refreshLowNoiseRepeatingRequest(vc, false);

                Log.i(Log.TAG, "拉流成功，camID = " + camID
                        + "，SSRC = " + ssrc
                        + "，size = " + mResolution.x + "x" + mResolution.y);
            } catch (Exception e) {
                Log.i(Log.TAG, "拉流异常，camID = " + camID + "，error = " + e.getMessage());
                setEnableLiveEncode(false);
            } finally {
                liveStarting = false;
            }
        });

        return true;
    }


    /**
     * 根据相机支持的预览或者图片分辨率列表，以及拍照设定的宽、高，返回最合适的Size
     *
     * @param sizes
     * @param width
     * @param height
     * @return
     */
    private Point getBestSize2(final Size[] sizes, final int width, final int height) { // 入口：方法定义
        if (sizes == null || sizes.length == 0 || width <= 0 || height <= 0)
            return new Point(width, height); // 返回：结束当前方法

        //指定列表中第一组数据为查找的初始数据
        Size found = sizes[0];
        final int specifiedArea = width * height; // 成员：保存运行状态
        //定义尺寸的最小匹配值
        int minMatch = Math.abs(specifiedArea - found.getWidth() * found.getHeight());

        for (int i = 1; i < sizes.length; i++) { // 循环：遍历数据
            //for (Camera.Size supportSize : newList) {
            int supportedArea = sizes[i].getWidth() * sizes[i].getHeight();
            //指定图片尺寸与支持列表中的尺寸完全匹配，不再进行查找
            if (supportedArea == specifiedArea) {
                return new Point(sizes[i].getWidth(), sizes[i].getHeight()); // 返回：结束当前方法
            }
            //指定图片尺寸大于相机支持的图片尺寸，查找相机支持的最大尺寸
            if ((supportedArea < specifiedArea) && (specifiedArea - supportedArea < minMatch)) {
                found = sizes[i];
                minMatch = specifiedArea - supportedArea;
            }
            //指定图片尺寸小于相机支持的图片尺寸，查找相机支持的最小尺寸
            else if ((supportedArea > specifiedArea) && (supportedArea - specifiedArea < minMatch)) {
                found = sizes[i];
                minMatch = supportedArea - specifiedArea;
            }
        }
        return new Point(found.getWidth(), found.getHeight()); // 返回：结束当前方法
    }

    private HashMap<String, Settings.AIParameter> aiParameters; /////；成员：保存运行状态
    /**
     * 摄像头没有主/子码流之分，所以stream都是0
     */

    ///
    private void enableMfbBeforeCapture() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (mPreviewSession == null || mPreviewRequestBuilder == null) { // Camera2：构建下一次请求参数
                return; // 返回：结束当前方法
            }

            applyLowNoiseCaptureRequestParameters(null, false);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true); // Camera2：锁定自动曝光

            mPreviewRequestBuilder.set( // Camera2：构建下一次请求参数
                    CaptureRequest.CONTROL_AF_TRIGGER, // Camera2：触发自动对焦
                    CameraMetadata.CONTROL_AF_TRIGGER_IDLE
            );

            if (mKeyAisRequestMode != null) {
                mPreviewRequestBuilder.set(mKeyAisRequestMode, new int[]{2}); // Camera2：构建下一次请求参数
                // 需要等待多帧降噪完成才能抓拍
                if (mCameraPhotoing) { // 状态：当前正在抓拍
                    SystemClock.sleep(5000); // 阻塞：当前线程睡眠
                }
            }

            logExposureRequest("enableMfbBeforeCapture", mPreviewRequestBuilder);
            mPreviewSession.setRepeatingRequest( // Camera2：持续提交预览请求
                    mPreviewRequestBuilder.build(), // Camera2：生成不可变请求
                    mCaptureCallback, // 回调：推进AF/AE状态机
                    mBackgroundHandler // 线程：承接Camera2回调
            );

        } catch (Exception e) {
            Log.i(Log.TAG, "MFB 预触发异常：" + e.getMessage());
        }
    }
    ///

    @Override
    public boolean takePhoto(int stream, int preset, boolean show, String filename, Bitmap pop, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert) { // Android：图像解码/绘制
        aiParameters = aps;

        synchronized (sDualCameraLock) {
            sDualPhotoTaskCount++;
        }

        getCameraWorkHandler().post(() -> {
            boolean notifyPhotoFailed = false;
            try {
                mOnShow = show;
                streamType = stream;
                mFileImage = filename;
                mFilePreset = preset;

                mCameraPhotoing = true;
                takePhotoOnce.set(true);
                photoDone.set(false);

                boolean videoFramePhoto = isVideoFramePhotoMode();
                Log.i(Log.TAG, "开始 MIPI 拍照"
                        + "，camID = " + camID
                        + "，videoFramePhoto = " + videoFramePhoto
                        + "，living = " + isLiving()
                        + "，recording = " + isRecording()
                        + "，enableLiveEncode = " + enableLiveEncode);

                if (mCameraDevice == null) {
                    boolean opened = open(stream, null, 10, false, videoFramePhoto, false);
                    if (!opened) {
                        failCurrentPhoto("双路 CameraDevice 打开失败");
                        notifyPhotoFailed = true;
                        return;
                    }
                }

                if (videoFramePhoto) {
                    // 拉流/录像中拍照：只等 mImageReader 的 YUV_420_888 当前视频帧，
                    // 不创建 JPEG ImageReader，不提交 TEMPLATE_STILL_CAPTURE，避免影响拉流/录像和曝光。
                    Settings.VideoCodec vc = getVideoCodec(stream);
                    if (mPreviewSession == null || !mPreviewSessionVideoMode) {
                        if (!ensureVideoPreviewSession(vc, isRecording())) {
                            failCurrentPhoto("视频态拍照无法创建视频会话");
                            notifyPhotoFailed = true;
                            return;
                        }
                    }
                    previewReady = true;
                    refreshLowNoiseRepeatingRequest(vc, isRecording());

                    if (!waitPhotoDone(10000)) {
                        failCurrentPhoto("视频态取帧超时");
                        notifyPhotoFailed = true;
                    }
                    return;
                }

                // 空闲拍照：必须走 JPEG ImageReader。不能复用旧 YUV/video session，否则会得到 YUV 帧或曝光异常。
                Point photoResolution = getConfiguredPhotoResolution();
                if (photoResolution == null) {
                    photoResolution = new Point(1920, 1080);
                }
                mResolution = photoResolution;

                boolean needNewJpegSession = mPreviewSession == null
                        || mPreviewSessionVideoMode
                        || mStillImageReader == null
                        || mSessionWidth != photoResolution.x
                        || mSessionHeight != photoResolution.y;

                if (needNewJpegSession) {
                    closePreviewSession();
                    closeImageReader();
                    closeStillImageReader();
                    createPreviewSession(photoResolution.x, photoResolution.y, false);
                }

                if (mPreviewSession == null || mStillImageReader == null) {
                    failCurrentPhoto("JPEG 拍照会话创建失败");
                    notifyPhotoFailed = true;
                    return;
                }

                lockFocus(
                        10000,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                        false,
                        null,
                        false
                );

                previewReady = true;
                captureStillPicture();

                if (!waitPhotoDone(40000)) {
                    failCurrentPhoto("JPEG still capture 超时");
                    notifyPhotoFailed = true;
                }
            } catch (Exception e) {
                Log.e(Log.TAG, "拍照过程中发生异常" + e);
                failCurrentPhoto("异常：" + e.getMessage());
                notifyPhotoFailed = true;
            } finally {
                mCameraPhotoing = false;
                takePhotoOnce.set(false);
                synchronized (sDualCameraLock) {
                    if (sDualPhotoTaskCount > 0) {
                        sDualPhotoTaskCount--;
                    }
                    sDualCameraLock.notifyAll();
                }

                closeBothCameraIfNoLive();

                if (notifyPhotoFailed && controllerCallback != null) {
                    Log.i(Log.TAG, "拍照失败，通知补拍，camID = " + camID);
                    controllerCallback.onPhotoFailed(id, preset, filename);
                }
            }
        });
        return true;
    }



    @Override
    public boolean takeVideo(final String filename, final int duration, int stream, boolean upload) { // 入口：方法定义
        if (isRecording() || videoStarting) {
            return false;
        }
        videoStarting = true;
        getCameraWorkHandler().post(() -> videoStart(stream, filename, duration, upload));
        return true;
    }

    protected boolean reboot() { // 入口：方法定义
        return true; // 返回：结束当前方法
    }

    public void startRecordCheckLine(int group) { // 入口：方法定义
    }

    public void stopRecordCheckLine(int group) { // 入口：方法定义
    }

    /////
    public boolean setTime(int dwYear, int dwMonth, int dwDay, int dwHour, int dwMinute, int dwSecond) { // 入口：方法定义
        return true; // 返回：结束当前方法
    }
    /////

    public Settings.CruiseGroup[] getCruise() { // 入口：方法定义
        return null; // 返回：结束当前方法
    }

    public boolean setCruise(int cmd, int group, int index, int preset, int duration, int speed) { // 入口：方法定义
        return true; // 返回：结束当前方法
    }

    public boolean setRecordTimes(List<Settings.VideoTimeItem> list) { /////；成员：保存运行状态
        return true; /////；返回：结束当前方法
    }

    @Override
    public boolean playbackStop() { // 入口：方法定义
        return true; // 返回：结束当前方法
    }

    @Override
    public boolean imageFusion(int stream, int preset, boolean show, String filename, Bitmap image_rgb, int recordPreset) { // Android：图像解码/绘制
        return true; // 返回：结束当前方法
    }

    private static final String EXPOSURE_DIAG_PREFIX = "MIPI曝光诊断";
    private static final long EXPOSURE_DIAG_LOG_INTERVAL_MS = 1000;
    private long mLastExposureRequestLogTime = 0;
    private long mLastCaptureResultLogTime = 0;
    private long mLastBitmapExposureLogTime = 0;

    private boolean shouldLogExposureRequest(String scene) {
        if (!"updateCaptureRequestParameters".equals(scene)) {
            return true;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - mLastExposureRequestLogTime < EXPOSURE_DIAG_LOG_INTERVAL_MS) {
            return false;
        }
        mLastExposureRequestLogTime = now;
        return true;
    }

    private boolean shouldLogCaptureResult(String scene) {
        if (!"captureCallback".equals(scene)) {
            return true;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - mLastCaptureResultLogTime < EXPOSURE_DIAG_LOG_INTERVAL_MS) {
            return false;
        }
        mLastCaptureResultLogTime = now;
        return true;
    }

    private boolean shouldLogBitmapExposure() {
        long now = SystemClock.elapsedRealtime();
        if (now - mLastBitmapExposureLogTime < EXPOSURE_DIAG_LOG_INTERVAL_MS) {
            return false;
        }
        mLastBitmapExposureLogTime = now;
        return true;
    }

    private static String valueToString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof int[]) {
            return Arrays.toString((int[]) value);
        }
        if (value instanceof long[]) {
            return Arrays.toString((long[]) value);
        }
        if (value instanceof float[]) {
            return Arrays.toString((float[]) value);
        }
        return String.valueOf(value);
    }

    private static <T> String requestValue(CaptureRequest.Builder builder, CaptureRequest.Key<T> key) {
        try {
            return valueToString(builder.get(key));
        } catch (Exception e) {
            return "error:" + e.getClass().getSimpleName();
        }
    }

    private static <T> String resultValue(CaptureResult result, CaptureResult.Key<T> key) {
        try {
            return valueToString(result.get(key));
        } catch (Exception e) {
            return "error:" + e.getClass().getSimpleName();
        }
    }

    private String exposureDiagState() {
        String cameraConfigInfo = cameraConfig == null
                ? ", cameraConfig=null"
                : ", gainControl=" + cameraConfig.gainControl
                + ", backLightCom=" + cameraConfig.backLightCom
                + ", strongLightSup=" + cameraConfig.strongLightSup
                + ", denoiseMode=" + cameraConfig.denoiseMode
                + ", focusMode=" + cameraConfig.focusMode;
        String photoConfigInfo = photoConfig == null
                ? ", photoConfig=null"
                : ", brightness=" + photoConfig.brightness
                + ", contrast=" + photoConfig.contrast
                + ", saturation=" + photoConfig.saturation
                + ", color=" + photoConfig.color;
        return "camID=" + camID
                + ", state=" + mState
                + ", photoing=" + mCameraPhotoing
                + ", living=" + isLiving()
                + ", recording=" + isRecording()
                + ", enableLiveEncode=" + enableLiveEncode
                + ", previewReady=" + previewReady
                + cameraConfigInfo
                + photoConfigInfo;
    }

    private void logExposureRequest(String scene, CaptureRequest.Builder builder) {
//        if (builder == null || !shouldLogExposureRequest(scene)) {
//            return;
//        }
//        String aisRequest = "null";
//        if (mKeyAisRequestMode != null) {
//            try {
//                aisRequest = valueToString(builder.get(mKeyAisRequestMode));
//            } catch (Exception e) {
//                aisRequest = "error:" + e.getClass().getSimpleName();
//            }
//        }
//        Log.i(Log.TAG, EXPOSURE_DIAG_PREFIX + "-Request[" + scene + "]: "
//                + exposureDiagState()
//                + ", aeMode=" + requestValue(builder, CaptureRequest.CONTROL_AE_MODE)
//                + ", aeLock=" + requestValue(builder, CaptureRequest.CONTROL_AE_LOCK)
//                + ", aeComp=" + requestValue(builder, CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION)
//                + ", fps=" + requestValue(builder, CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE)
//                + ", aePrecapture=" + requestValue(builder, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER)
//                + ", afMode=" + requestValue(builder, CaptureRequest.CONTROL_AF_MODE)
//                + ", afTrigger=" + requestValue(builder, CaptureRequest.CONTROL_AF_TRIGGER)
//                + ", nrMode=" + requestValue(builder, CaptureRequest.NOISE_REDUCTION_MODE)
//                + ", aisReq=" + aisRequest);
    }

    private void logCaptureResult(String scene, CaptureResult result) {
//        if (result == null || !shouldLogCaptureResult(scene)) {
//            return;
//        }
//        String aisResult = "null";
//        if (mKeyAisResult != null) {
//            try {
//                aisResult = valueToString(result.get(mKeyAisResult));
//            } catch (Exception e) {
//                aisResult = "error:" + e.getClass().getSimpleName();
//            }
//        }
//        Log.i(Log.TAG, EXPOSURE_DIAG_PREFIX + "-Result[" + scene + "]: "
//                + exposureDiagState()
//                + ", aeState=" + resultValue(result, CaptureResult.CONTROL_AE_STATE)
//                + ", afState=" + resultValue(result, CaptureResult.CONTROL_AF_STATE)
//                + ", exposureTimeNs=" + resultValue(result, CaptureResult.SENSOR_EXPOSURE_TIME)
//                + ", sensitivityIso=" + resultValue(result, CaptureResult.SENSOR_SENSITIVITY)
//                + ", frameDurationNs=" + resultValue(result, CaptureResult.SENSOR_FRAME_DURATION)
//                + ", aisResult=" + aisResult);
    }

    private void logBitmapExposure(String scene, Bitmap bitmap, Image image) {
//        try {
//            String imageInfo = image == null
//                    ? ""
//                    : ", imageFormat=" + image.getFormat()
//                    + ", imageSize=" + image.getWidth() + "x" + image.getHeight()
//                    + ", imageTs=" + image.getTimestamp();
//            if (bitmap == null) {
//                Log.i(Log.TAG, EXPOSURE_DIAG_PREFIX + "-Bitmap[" + scene + "]: "
//                        + exposureDiagState() + ", bitmap=null" + imageInfo);
//                return;
//            }
//            int width = bitmap.getWidth();
//            int height = bitmap.getHeight();
//            int stepX = Math.max(1, width / 16);
//            int stepY = Math.max(1, height / 16);
//            int minY = 255;
//            int maxY = 0;
//            int over245 = 0;
//            int samples = 0;
//            long sumY = 0;
//
//            for (int y = stepY / 2; y < height; y += stepY) {
//                for (int x = stepX / 2; x < width; x += stepX) {
//                    int color = bitmap.getPixel(x, y);
//                    int r = (color >> 16) & 0xff;
//                    int g = (color >> 8) & 0xff;
//                    int b = color & 0xff;
//                    int luma = (r * 299 + g * 587 + b * 114) / 1000;
//                    minY = Math.min(minY, luma);
//                    maxY = Math.max(maxY, luma);
//                    sumY += luma;
//                    if (luma >= 245) {
//                        over245++;
//                    }
//                    samples++;
//                }
//            }
//
//            int avgY = samples == 0 ? -1 : (int) (sumY / samples);
//            int over245Percent = samples == 0 ? 0 : over245 * 100 / samples;
//            Log.i(Log.TAG, EXPOSURE_DIAG_PREFIX + "-Bitmap[" + scene + "]: "
//                    + exposureDiagState()
//                    + ", bitmapSize=" + width + "x" + height
//                    + ", avgY=" + avgY
//                    + ", minY=" + minY
//                    + ", maxY=" + maxY
//                    + ", over245=" + over245Percent + "%"
//                    + ", samples=" + samples
//                    + imageInfo);
//        } catch (Exception e) {
//            Log.i(Log.TAG, EXPOSURE_DIAG_PREFIX + "-Bitmap log error[" + scene + "]: " + e.getMessage());
//        }
    }
}
