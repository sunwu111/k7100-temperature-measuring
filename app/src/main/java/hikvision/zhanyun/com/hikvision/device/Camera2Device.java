package hikvision.zhanyun.com.hikvision.device;

import static hikvision.zhanyun.com.hikvision.MainActivity.channels;
import static hikvision.zhanyun.com.hikvision.MainActivity.is6735;


import android.graphics.Color;
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

    /**
     * 本次拍照保存是否成功。
     * true  = 图片保存成功
     * false = 图片保存失败
     */
    private final AtomicBoolean photoSaveSuccess = new AtomicBoolean(false);

    /**
     * 本次拍照失败原因，用于日志和补拍判断。
     */
    private volatile String photoFailReason = "";

    /// sunwu
    private final AtomicBoolean takePhotoOnce = new AtomicBoolean(false);   // 防止在拉流的时候拍照会被执行多次；同步：只允许一帧完成本次抓拍
    private final AtomicBoolean photoDone = new AtomicBoolean(false);       // 拍照已经成功，但等待方不知道  如果没有这个变量，在一次拍照成功后，设备还在等待拍照任务，会导致拍照失败再次拍照，其实已经成功。；条件：takePhoto等它变true

    private final AtomicBoolean videoEncodePending = new AtomicBoolean(false); // 成员：保存运行状态

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

    private static final long MIN_VALID_PHOTO_SIZE_BYTES = 400 * 1024;  // 图片文件大小 400kb
    ///

    public Camera2Device(int ID, Context context, int camID, int board, int rotate, boolean useAudio) { /////；成员：保存运行状态
        super(ID, context, useAudio); /////
        this.camID = camID; // 赋值：更新状态
        this.mContext = context; // 赋值：更新状态
        this.mMainBoard = board; // 赋值：更新状态
        this.drawOSD = true; // 赋值：更新状态
        this.rotate = rotate; /////；赋值：更新状态
        this.useAudio = useAudio; /////；赋值：更新状态

        if (!scheduledThread.isAlive()) { // 条件：按运行状态分支
            scheduledThread.start(); // 调用：执行下一步
            scheduledHandler = new Handler(scheduledThread.getLooper()); // Android：串行消息线程
        }
        if (!mCameraParamThread.isAlive()) { /////；条件：按运行状态分支
            mCameraParamThread.start(); /////；调用：执行下一步
            mCameraParamHandler = new Handler(mCameraParamThread.getLooper()); /////；线程：异步刷新请求参数
        } /////
        registerDualCameraInstance(); ///
    }

    ///
    private void registerDualCameraInstance() { // 入口：方法定义
        synchronized (sDualCameraLock) { // 同步：保护双MIPI共享状态
            int realCamId = camID % 2; // 赋值：更新状态

            if (realCamId == 0) { // 条件：按运行状态分支
                sCamera0Device = this; // 赋值：更新状态
            } else {
                sCamera1Device = this; // 赋值：更新状态
            }

            Log.i(Log.TAG, "注册双路 MIPI 对象" // 日志：记录相机状态
                    + "，camID = " + camID // 赋值：更新状态
                    + "，realCamId = " + realCamId // 赋值：更新状态
                    + "，this = " + this // 赋值：更新状态
                    + "，sCamera0Device = " + sCamera0Device // 赋值：更新状态
                    + "，sCamera1Device = " + sCamera1Device); // 赋值：更新状态
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
                    locker.unlocked = false; // 赋值：更新状态
                    locker.wait(milisecond); // 同步：等待唤醒或超时
                }
            } catch (Exception e) {
                Log.i(Log.TAG, "加锁异常：" + e.getMessage()); // 日志：记录相机状态
            }
            return locker.unlocked; // 返回：结束当前方法
        }

        public void notifyLock() { // 同步：唤醒等待线程
            try { // 异常：保护相机/IO调用
                synchronized (locker) { // 同步：互斥访问共享状态
                    locker.unlocked = true; // 赋值：更新状态
                    locker.notify(); // 同步：唤醒等待线程
                }
            } catch (Exception e) {
                Log.i(Log.TAG, "解锁异常：" + e.getMessage()); // 日志：记录相机状态
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
            if (isRecording() && !pausing && mediaMuxer != null) { // 条件：按运行状态分支
                if (outIndex >= 0) { // 条件：按运行状态分支
                    if (videoTrackIndex < 0 && mediaCodec != null) { // 条件：按运行状态分支
                        MediaFormat mediaFormat = mediaCodec.getOutputFormat(); // Android：视频编码/封装
                        videoTrackIndex = mediaMuxer.addTrack(mediaFormat); // 赋值：更新状态
                        tryStartMipiMuxer();
                    }
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) { // Android：视频编码/封装
                        return; // 返回：结束当前方法
                    }
                    if (muxerStarted && bufferInfo.size > 0) { // 条件：按运行状态分支
                        ByteBuffer outBuf = mediaCodec.getOutputBuffer(outIndex); // 赋值：更新状态
                        if (outBuf != null) { // 条件：按运行状态分支
                            ByteBuffer recordBuf = outBuf.duplicate(); // 赋值：更新状态
                            recordBuf.position(bufferInfo.offset); // 调用：执行下一步
                            recordBuf.limit(bufferInfo.offset + bufferInfo.size); // 调用：执行下一步

                            long ptsUs = bufferInfo.presentationTimeUs; // 赋值：更新状态
                            long nowUs = (avStartNs != 0) ? ((System.nanoTime() - avStartNs) / 1000) : ptsUs; // 赋值：更新状态
                            if (ptsUs > nowUs + 5_000_000L) ptsUs = nowUs; // 条件：按运行状态分支
                            if (ptsUs <= lastVideoPtsUs) ptsUs = lastVideoPtsUs + 1; // 条件：按运行状态分支
                            lastVideoPtsUs = ptsUs; // 赋值：更新状态
                            bufferInfo.presentationTimeUs = ptsUs; // 赋值：更新状态

                            mediaMuxer.writeSampleData(videoTrackIndex, recordBuf, bufferInfo); /////；调用：执行下一步
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
            Log.i(Log.TAG, "MIPI摄像头录制视频文件异常：" + e); // 日志：记录相机状态
        }
        /////
    }

    @Override
    protected void encode(Bitmap bitmap) { // Android：图像解码/绘制
        if (mediaCodec == null || bitmap == null) return; // 条件：按运行状态分支

        try { // 异常：保护相机/IO调用
            ByteBuffer encodeBuffer = ByteBuffer.allocate(bitmap.getByteCount()); // 赋值：更新状态
            bitmap.copyPixelsToBuffer(encodeBuffer); // 调用：执行下一步
            byte[] argbBytes = encodeBuffer.array(); // 赋值：更新状态

            int inputBufferIndex = mediaCodec.dequeueInputBuffer(0); // 赋值：更新状态
            if (inputBufferIndex >= 0) { // 条件：按运行状态分支
                ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex); // 赋值：更新状态
                if (inputBuffer != null) { // 条件：按运行状态分支
                    if (argbBytes.length > inputBuffer.capacity()) { // 条件：按运行状态分支
                        Log.w(Log.TAG, "MIPI video encode skip oversize frame: " // 日志：记录相机状态
                                + bitmap.getWidth() + "x" + bitmap.getHeight() // 调用：执行下一步
                                + ", bytes=" + argbBytes.length // 赋值：更新状态
                                + ", capacity=" + inputBuffer.capacity()); // 赋值：更新状态
                        mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, System.nanoTime() / 1000, 0); // 调用：执行下一步
                        return; // 返回：结束当前方法
                    }
                    inputBuffer.clear(); // 调用：执行下一步
                    inputBuffer.put(argbBytes, 0, argbBytes.length); // 调用：执行下一步
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, argbBytes.length, System.nanoTime() / 1000, 0); // 调用：执行下一步
                } else {
                    Log.w(Log.TAG, "MIPI video encode input buffer is null"); // 日志：记录相机状态
                }
            } else {
                Log.w(Log.TAG, "MIPI video encode input error: " + inputBufferIndex); // 日志：记录相机状态
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo(); // Android：视频编码/封装
            for (int drainCount = 0; drainCount < 16; drainCount++) { // 循环：遍历数据
                int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0); // 赋值：更新状态
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) { // Android：视频编码/封装
                    break;
                }
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { // Android：视频编码/封装
                    if (isRecording() && mediaMuxer != null && videoTrackIndex < 0) { // 条件：按运行状态分支
                        MediaFormat mediaFormat = mediaCodec.getOutputFormat(); // Android：视频编码/封装
                        videoTrackIndex = mediaMuxer.addTrack(mediaFormat); // 赋值：更新状态
                        tryStartMipiMuxer();
                    }
                    Log.w(Log.TAG, "MIPI video encode format changed: " + mediaCodec.getOutputFormat()); // 日志：记录相机状态
                    continue;
                }
                if (outputBufferIndex < 0) { // 条件：按运行状态分支
                    continue;
                }
                try { // 异常：保护相机/IO调用
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex); // 赋值：更新状态
                    if (outputBuffer == null || bufferInfo.size <= 0) { // 条件：按运行状态分支
                        continue;
                    }

                    byte[] liveData = null; // 赋值：更新状态
                    if (isLiving() && rtph264 != null) { // 条件：按运行状态分支
                        ByteBuffer liveBuf = outputBuffer.duplicate(); // 赋值：更新状态
                        liveBuf.position(bufferInfo.offset); // 调用：执行下一步
                        liveBuf.limit(bufferInfo.offset + bufferInfo.size); // 调用：执行下一步
                        liveData = new byte[bufferInfo.size]; // 赋值：更新状态
                        liveBuf.get(liveData); // 调用：执行下一步
                    }

                    if (isRecording()) { // 条件：按运行状态分支
                        doSampleData(outputBuffer, bufferInfo, outputBufferIndex);
                    }

                    if (liveData != null) { // 条件：按运行状态分支
                        sendMipiLiveEncodedFrame(liveData, bufferInfo.presentationTimeUs);
                    }
                } finally {
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false); // 调用：执行下一步
                }
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "MIPI video encode error: " + e.getMessage()); // 日志：记录相机状态
        }
    }

    private void sendMipiLiveEncodedFrame(byte[] outData, long presentationTimeUs) { // 入口：方法定义
        if (controllerCallback == null || rtph264 == null || outData == null || outData.length < 5) return; // 条件：按运行状态分支

        try { // 异常：保护相机/IO调用
            if (outData[0] == 0 && outData[1] == 0 && outData[2] == 0 && outData[3] == 1) { // 条件：按运行状态分支
                int type = outData[4] & 0x1F; // 赋值：更新状态
                if (type == 7) { // 条件：按运行状态分支
                    mipiLivePpsSps = outData; // 赋值：更新状态
                    return; // 返回：结束当前方法
                } else if (type == 5 && mipiLivePpsSps != null) {
                    byte[] iframeData = new byte[mipiLivePpsSps.length + outData.length]; // 赋值：更新状态
                    System.arraycopy(mipiLivePpsSps, 0, iframeData, 0, mipiLivePpsSps.length); // 调用：执行下一步
                    System.arraycopy(outData, 0, iframeData, mipiLivePpsSps.length, outData.length); // 调用：执行下一步
                    outData = iframeData; // 赋值：更新状态
                }
            }

            long timestamp = presentationTimeUs / 1000 * 90; // 赋值：更新状态
            if (mipiLiveFirstFrameTimestamp == 0) { // 条件：按运行状态分支
                mipiLiveFirstFrameTimestamp = timestamp; // 赋值：更新状态
            }
            rtph264.timestamp = timestamp - mipiLiveFirstFrameTimestamp; // 赋值：更新状态
            byte[][] rtps = rtph264.encode(outData, 96, 0); // 赋值：更新状态
            if (rtps != null) { // 条件：按运行状态分支
                for (byte[] pack : rtps) { // 循环：遍历数据
                    controllerCallback.onFrame(Camera2Device.this, pack); // 调用：执行下一步
                }
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "MIPI live RTP packet error: " + e); // 日志：记录相机状态
        }
    }

    private Bitmap imageDecode(Image image) { // Android：底层图像buffer
        switch (image.getFormat()) { // 分支：按状态选择路径
            case ImageFormat.JPEG: // 分支：状态处理入口
                ByteBuffer buffer = image.getPlanes()[0].getBuffer(); // 赋值：更新状态
                byte[] bytes = new byte[buffer.capacity()]; // 赋值：更新状态
                buffer.get(bytes); // 调用：执行下一步
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
            Point targetResolution = null; // 赋值：更新状态
            boolean useVideoResolution = (isLiving() && rtph264 != null) || isRecording() || enableLiveEncode; // 赋值：更新状态
            if (useVideoResolution) { /////；条件：按运行状态分支

//                Point size = Settings.VideoCodec.getResolution(codec.get(String.valueOf(0)).resolution);
                Settings.VideoCodec vc = codec.get(String.valueOf(streamType)); // 赋值：更新状态
                Point size = vc != null ? Settings.VideoCodec.getResolution(vc.resolution) : null; // 赋值：更新状态
                if (size == null) { // 条件：按运行状态分支
                    size = new Point(previewBitmap.getWidth(), previewBitmap.getHeight()); // 赋值：更新状态
                }


                ///
                // 由于分辨率大于1536x864无法拉流，因此设置最大的分辨率为1536x864
                if (size.x > 1536 || size.y > 864) { // 条件：按运行状态分支
                    size = new Point(1536, 864); // 赋值：更新状态
                }
                ///
                mResolution = size; // 赋值：更新状态
                targetResolution = size; // 赋值：更新状态
//                Log.e(Log.TAG,"preProcessingPhoto分辨率为：" + mResolution.x + ":" + mResolution.y);

            } else if (mCameraPhotoing) {
                targetResolution = Settings.PhotoConfig.getImageSize(photoConfig.size); // 赋值：更新状态
            } else {
                targetResolution = mResolution; // 赋值：更新状态
            }
            ///
            if (targetResolution == null) { // 条件：按运行状态分支
                targetResolution = new Point(previewBitmap.getWidth(), previewBitmap.getHeight()); // 赋值：更新状态
            }
            if (targetResolution.x == previewBitmap.getWidth() && targetResolution.y == previewBitmap.getHeight()) { // 条件：按运行状态分支
                if (photoConfig.brightness == 50 && photoConfig.contrast == 50 && photoConfig.saturation == 50) { // 条件：按运行状态分支
                    return previewBitmap; // 返回：结束当前方法
                } else {
                    Bitmap outputBitmap = Bitmap.createBitmap(previewBitmap.getWidth(), previewBitmap.getHeight(), Bitmap.Config.ARGB_8888); /////；Android：图像解码/绘制
                    Canvas canvas = new Canvas(outputBitmap); // Android：图像解码/绘制
                    Paint paint = new Paint(); // 赋值：更新状态
                    ColorMatrix colorMatrix = new ColorMatrix(); // 赋值：更新状态
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

                    if (photoConfig.brightness != 50) { // 条件：按运行状态分支
                        // 映射亮度 (1~100 → -128~128)
                        float brightnessValue = (photoConfig.brightness - 50) * 2.56f; // 赋值：更新状态
                        // 调整亮度
                        ColorMatrix brightnessMatrix = new ColorMatrix(new float[]{ // 赋值：更新状态
                                1, 0, 0, 0, brightnessValue,
                                0, 1, 0, 0, brightnessValue,
                                0, 0, 1, 0, brightnessValue,
                                0, 0, 0, 1, 0
                        });
                        colorMatrix.postConcat(brightnessMatrix); // 调用：执行下一步
                    }
                    if (photoConfig.contrast != 50) { // 条件：按运行状态分支
                        // 映射对比度 (1~100 → 0.5~2.0)
                        float contrastValue = 0.5f + (photoConfig.contrast - 1) * (1.5f / 99); // 赋值：更新状态
                        // 调整对比度
                        float translate = (1 - contrastValue) * 128; // 赋值：更新状态
                        ColorMatrix contrastMatrix = new ColorMatrix(new float[]{ // 赋值：更新状态
                                contrastValue, 0, 0, 0, translate,
                                0, contrastValue, 0, 0, translate,
                                0, 0, contrastValue, 0, translate,
                                0, 0, 0, 1, 0
                        });
                        colorMatrix.postConcat(contrastMatrix); // 调用：执行下一步
                    }
                    if (photoConfig.saturation != 50) { // 条件：按运行状态分支
                        // 映射饱和度 (1~100 → 0.0~2.0)
                        float saturationValue = (photoConfig.saturation - 1) * (2.0f / 99); // 赋值：更新状态
                        // 调整饱和度
                        ColorMatrix saturationMatrix = new ColorMatrix(); // 赋值：更新状态
                        saturationMatrix.setSaturation(saturationValue); // 调用：执行下一步
                        if (MainActivity.DEBUG) { // 条件：按运行状态分支
                            colorMatrix.postConcat(saturationMatrix); // 调用：执行下一步
                        }
                        // 组合所有矩阵
                        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix)); // 调用：执行下一步
                        canvas.drawBitmap(previewBitmap, 0, 0, paint); // 调用：执行下一步
                        //Log.i(Log.TAG, "摄像头亮度设置为" + photoConfig.brightness);
                        //Log.i(Log.TAG, "摄像头对比度设置为" + photoConfig.contrast);
                        //Log.i(Log.TAG, "摄像头饱和度设置为" + photoConfig.saturation);
                        return outputBitmap; // 返回：结束当前方法
                    }
                }
            } else {
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(previewBitmap, targetResolution.x, targetResolution.y, true); // Android：图像解码/绘制
//                Log.i(Log.TAG, "摄像头设置分辨率为" + targetResolution.x + "x" + targetResolution.y);
                if (photoConfig.brightness == 50 && photoConfig.contrast == 50 && photoConfig.saturation == 50) { // 条件：按运行状态分支
                    return scaledBitmap; // 返回：结束当前方法
                } else {
                    Bitmap outputBitmap = Bitmap.createBitmap(scaledBitmap.getWidth(), scaledBitmap.getHeight(), Bitmap.Config.ARGB_8888); /////；Android：图像解码/绘制
                    Canvas canvas = new Canvas(outputBitmap); // Android：图像解码/绘制
                    Paint paint = new Paint(); // 赋值：更新状态
                    ColorMatrix colorMatrix = new ColorMatrix(); // 赋值：更新状态
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

                    if (photoConfig.brightness != 50) { // 条件：按运行状态分支
                        // 映射亮度 (1~100 → -128~128)
                        float brightnessValue = (photoConfig.brightness - 50) * 2.56f; // 赋值：更新状态
                        // 调整亮度
                        ColorMatrix brightnessMatrix = new ColorMatrix(new float[]{ // 赋值：更新状态
                                1, 0, 0, 0, brightnessValue,
                                0, 1, 0, 0, brightnessValue,
                                0, 0, 1, 0, brightnessValue,
                                0, 0, 0, 1, 0
                        });
                        colorMatrix.postConcat(brightnessMatrix); // 调用：执行下一步
                    }
                    if (photoConfig.contrast != 50) { // 条件：按运行状态分支
                        // 映射对比度 (1~100 → 0.5~2.0)
                        float contrastValue = 0.5f + (photoConfig.contrast - 1) * (1.5f / 99); // 赋值：更新状态
                        // 调整对比度
                        float translate = (1 - contrastValue) * 128; // 赋值：更新状态
                        ColorMatrix contrastMatrix = new ColorMatrix(new float[]{ // 赋值：更新状态
                                contrastValue, 0, 0, 0, translate,
                                0, contrastValue, 0, 0, translate,
                                0, 0, contrastValue, 0, translate,
                                0, 0, 0, 1, 0
                        });
                        colorMatrix.postConcat(contrastMatrix); // 调用：执行下一步
                    }
                    if (photoConfig.saturation != 50) { // 条件：按运行状态分支
                        // 映射饱和度 (1~100 → 0.0~2.0)
                        float saturationValue = (photoConfig.saturation - 1) * (2.0f / 99); // 赋值：更新状态
                        // 调整饱和度
                        ColorMatrix saturationMatrix = new ColorMatrix(); // 赋值：更新状态
                        saturationMatrix.setSaturation(saturationValue); // 调用：执行下一步
                        colorMatrix.postConcat(saturationMatrix); // 调用：执行下一步
                    }
                    // 组合所有矩阵
                    paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix)); // 调用：执行下一步
                    canvas.drawBitmap(scaledBitmap, 0, 0, paint); // 调用：执行下一步
                    //Log.i(Log.TAG, "摄像头亮度设置为" + photoConfig.brightness);
                    //Log.i(Log.TAG, "摄像头对比度设置为" + photoConfig.contrast);
                    //Log.i(Log.TAG, "摄像头饱和度设置为" + photoConfig.saturation);
                    return outputBitmap; // 返回：结束当前方法
                }
            }
            ///
        } catch (Exception e) {
            Log.i(Log.TAG, "摄像头设置图像参数异常：" + e); // 日志：记录相机状态
            return previewBitmap; // 返回：结束当前方法
        }

        return previewBitmap; // 返回：结束当前方法
    }

    private void updateCaptureRequestParameters() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            // 降噪模式
            if (cameraConfig.denoiseMode == 0) { // 条件：按运行状态分支
                //Log.i(Log.TAG, "MIPI摄像头关闭降噪");
            } else if (cameraConfig.denoiseMode == 1) {
                //Log.i(Log.TAG, "MIPI摄像头开启2D降噪");
            } else if (cameraConfig.denoiseMode == 2) {
                //Log.i(Log.TAG, "MIPI摄像头开启3D降噪");
            }
            if (cameraConfig.denoiseMode <= 2) { // 条件：按运行状态分支
                mPreviewRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, (int) cameraConfig.denoiseMode); // Camera2：构建下一次请求参数
            }
            // 增益控制
            if (cameraConfig.gainControl == 0) { // 条件：按运行状态分支
                //Log.i(Log.TAG, "MIPI摄像头手动增益");
            } else {
                //Log.i(Log.TAG, "MIPI摄像头自动增益");
            }
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, (int) cameraConfig.gainControl); // Camera2：构建下一次请求参数
            // 背光补偿
            if (cameraConfig.backLightCom == 1) { // 条件：按运行状态分支
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
            if (cameraConfig.focusMode == 0) { // 条件：按运行状态分支
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
            if (photoConfig.color == 0) { // 条件：按运行状态分支
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_MONO);  // 黑白色彩；Camera2：构建下一次请求参数
            }
            // 更新请求
            applyLowNoiseCaptureRequestParameters(isRecording() ? getVideoCodec(streamType) : null, isRecording());
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
        if (builder == null) { // 条件：按运行状态分支
            return; // 返回：结束当前方法
        }
        int denoiseMode = CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY; // Camera2：发送给HAL的请求
        if (cameraConfig != null && cameraConfig.denoiseMode >= 0 && cameraConfig.denoiseMode <= 2) { // 条件：按运行状态分支
            denoiseMode = cameraConfig.denoiseMode; // 赋值：更新状态
        }
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, denoiseMode); // Camera2：发送给HAL的请求

        if (isRecordVideo && vc != null && vc.frame > 0) { // 条件：按运行状态分支
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(vc.frame, vc.frame)); // Camera2：限制帧率范围
        } else {
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(10, 10)); // Camera2：限制帧率范围
        }

        if (mKeyAisRequestMode != null) { // 条件：按运行状态分支
            builder.set(mKeyAisRequestMode, new int[]{2}); // 调用：执行下一步
        }
    }

    private void refreshLowNoiseRepeatingRequest(Settings.VideoCodec vc, boolean isRecordVideo) { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (mPreviewSession == null || mPreviewRequestBuilder == null) { // Camera2：构建下一次请求参数
                return; // 返回：结束当前方法
            }
            applyLowNoiseCaptureRequestParameters(vc, isRecordVideo);
            mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler); // Camera2：持续提交预览请求
        } catch (Exception e) {
            Log.e(Log.TAG, "Refresh low-noise CaptureRequest failed: " + e.getMessage()); // Camera2：发送给HAL的请求
        }
    }

    private static Bitmap rotate180WithCanvas(Bitmap src) { // Android：图像解码/绘制
        Bitmap dst = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig() != null ? src.getConfig() : Bitmap.Config.ARGB_8888); // Android：图像解码/绘制
        Canvas c = new Canvas(dst); // Android：图像解码/绘制
        c.save(); // 调用：执行下一步
        c.rotate(180f, src.getWidth() / 2f, src.getHeight() / 2f); // 调用：执行下一步
        c.drawBitmap(src, 0f, 0f, null);  // 关键：把原图画到旋转后的画布上；调用：执行下一步
        c.restore(); // 调用：执行下一步
        return dst; // 返回：结束当前方法
    }

        private static Bitmap rotate90ClockwiseWithCanvas(Bitmap src) { // Android：图像解码/绘制
        Bitmap dst = Bitmap.createBitmap(src.getHeight(), src.getWidth(), src.getConfig() != null ? src.getConfig() : Bitmap.Config.ARGB_8888); // Android：图像解码/绘制
        Canvas c = new Canvas(dst); // Android：图像解码/绘制
        c.save(); // 调用：执行下一步
        c.translate(src.getHeight(), 0f); // 调用：执行下一步
        c.rotate(90f); // 调用：执行下一步
        c.drawBitmap(src, 0f, 0f, null); // 调用：执行下一步
        c.restore(); // 调用：执行下一步
        return dst; // 返回：结束当前方法
    }


    private void saveCapturedPhoto(Bitmap bitmap) {
        if (bitmap == null) {
            Log.i(Log.TAG, "抓拍失败：bitmap 为空");

            notifyPhotoFinished(false, "bitmap 为空");
            return;
        }

        try {
            Log.i(Log.TAG, "抓拍图片分辨率：" + bitmap.getWidth() + "x" + bitmap.getHeight());

            bitmap = processPhoto(bitmap, System.currentTimeMillis(), 255, aiParameters, true);

            if (bitmap == null) {
                Log.i(Log.TAG, "抓拍失败：processPhoto 返回 null");

                notifyPhotoFinished(false, "processPhoto 返回 null");
                return;
            }

            boolean abnormalBitmap = isAbnormalBitmap(bitmap);

            Log.i(Log.TAG,
                    "抓拍 Bitmap 异常检测"
                            + "，abnormalBitmap = " + abnormalBitmap
                            + "，width = " + bitmap.getWidth()
                            + "，height = " + bitmap.getHeight()
                            + "，camID = " + camID);

            drawWatermark(bitmap, id, streamType, true);

            Utils.saveBitmapAsJPEG(bitmap, mFileImage, 100);

            File photoFile = new File(mFileImage);

            if (!photoFile.exists()) {
                Log.i(Log.TAG, "抓拍失败：文件不存在，file = " + mFileImage);

                notifyPhotoFinished(false, "抓拍文件不存在");
                return;
            }

            long fileSize = photoFile.length();
            long fileSizeKb = fileSize / 1024;

            Log.i(Log.TAG,
                    "抓拍文件大小检查"
                            + "，file = " + mFileImage
                            + "，size = " + fileSize
                            + " bytes"
                            + "，sizeKB = " + fileSizeKb
                            + "KB"
                            + "，minValidKB = " + (MIN_VALID_PHOTO_SIZE_BYTES / 1024)
                            + "KB"
                            + "，abnormalBitmap = " + abnormalBitmap
                            + "，camID = " + camID);

            if (abnormalBitmap && fileSize < MIN_VALID_PHOTO_SIZE_BYTES) {
//            if (fileSize < 4000 * 1024) {   // test
                Log.i(Log.TAG,
                        "抓拍失败：Bitmap 疑似异常且文件小于 400KB，触发补拍"
                                + "，file = " + mFileImage
                                + "，sizeKB = " + fileSizeKb
                                + "KB"
                                + "，camID = " + camID);

                notifyPhotoFinished(false, "Bitmap疑似异常且文件小于400KB");
                return;
            }

            if (NettyUtils.isTakePhoto()) {
                toolTakePhoto(bitmap);
                NettyUtils.setTakePhoto(false);
            }

            if (controllerCallback != null) {
                procVideoHandler.post(() ->
                        controllerCallback.onPhotoTaked(
                                getTimestampFromFilename(mFileImage),
                                id,
                                mFilePreset,
                                mFileImage
                        )
                );
            }

            Runnable done = () -> notifyPhotoFinished(true, null);

            if (mBackgroundHandler != null) {
                mBackgroundHandler.postDelayed(done, 1500);
                return;
            }

            SystemClock.sleep(1500);
            done.run();

        } catch (Exception e) {
            Log.i(Log.TAG,
                    "抓拍保存异常："
                            + e.getMessage()
                            + "，file = " + mFileImage
                            + "，camID = " + camID);

            notifyPhotoFinished(false, "抓拍保存异常：" + e.getMessage());
        }
    }





    public static boolean isAbnormalBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return true;
        }

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width <= 0 || height <= 0) {
            return true;
        }

        int sampleStep = 4; // 每隔 4 个像素采样一次，提升速度
        int[] buckets = new int[16 * 16 * 16];

        long count = 0;
        double sumR = 0;
        double sumG = 0;
        double sumB = 0;
        double sumY = 0;

        double sumR2 = 0;
        double sumG2 = 0;
        double sumB2 = 0;
        double sumY2 = 0;

        int maxBucketCount = 0;

        for (int y = 0; y < height; y += sampleStep) {
            for (int x = 0; x < width; x += sampleStep) {
                int color = bitmap.getPixel(x, y);

                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);

                double luminance = 0.299 * r + 0.587 * g + 0.114 * b;

                sumR += r;
                sumG += g;
                sumB += b;
                sumY += luminance;

                sumR2 += r * r;
                sumG2 += g * g;
                sumB2 += b * b;
                sumY2 += luminance * luminance;

                int bucket = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                int bucketCount = ++buckets[bucket];

                if (bucketCount > maxBucketCount) {
                    maxBucketCount = bucketCount;
                }

                count++;
            }
        }

        if (count == 0) {
            return true;
        }

        double meanR = sumR / count;
        double meanG = sumG / count;
        double meanB = sumB / count;
        double meanY = sumY / count;

        double stdR = Math.sqrt(Math.max(0, sumR2 / count - meanR * meanR));
        double stdG = Math.sqrt(Math.max(0, sumG2 / count - meanG * meanG));
        double stdB = Math.sqrt(Math.max(0, sumB2 / count - meanB * meanB));
        double stdY = Math.sqrt(Math.max(0, sumY2 / count - meanY * meanY));

        double colorStdAvg = (stdR + stdG + stdB) / 3.0;
        double dominantColorRatio = maxBucketCount * 1.0 / count;

        return dominantColorRatio > 0.90
                && stdY < 12.0
                && colorStdAvg < 15.0;
    }


    // 直播和拍照回调函数  拉流的时候可以拍照，需要结合takephoto函数
    private void postEncodeFrame(Bitmap bitmap) { // Android：图像解码/绘制
        if (bitmap == null || procVideoHandler == null) { // 条件：按运行状态分支
            return; // 返回：结束当前方法
        }
        if (!videoEncodePending.compareAndSet(false, true)) { // 条件：按运行状态分支
            return; // 返回：结束当前方法
        }
        procVideoHandler.post(() -> { // 调用：执行下一步
            try { // 异常：保护相机/IO调用
                encode(bitmap);
            } finally {
                videoEncodePending.set(false); // 调用：执行下一步
            }
        });
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() { // Android：相机帧队列
        @Override
        public void onImageAvailable(ImageReader reader) { // 回调：ImageReader有新帧
            Image img = reader.acquireLatestImage(); // ImageReader：取最新帧
            try { // 异常：保护相机/IO调用
                if (img == null || !previewReady) return; // 条件：会话可出帧后才处理Image

                mCameraParamHandler.post(() -> updateCaptureRequestParameters()); // 线程：异步刷新请求参数

                Bitmap previewBitmap = imageDecode(img); // Android：图像解码/绘制

                if (rotate == 1) { // 条件：按运行状态分支
                    previewBitmap = rotate180WithCanvas(previewBitmap); // 赋值：更新状态
                }
                previewBitmap = preProcessingPhoto(previewBitmap); // 赋值：更新状态

                if (mCameraPhotoing && mStillImageReader == null && takePhotoOnce.compareAndSet(true, false)) { // 同步：只允许一帧完成本次抓拍
                    saveCapturedPhoto(previewBitmap);

                } else if ((isLiving() && rtph264 != null) || isRecording()) {
                    //detectObject(previewBitmap);// 视频AI跟踪，会影响帧率，暂时注释掉
                    //drawMetrics(previewBitmap);  // 绘制信噪比、宽动态、清晰度OSD /////

                    drawWatermark(previewBitmap, id, streamType, false); // 先AI识别再画OSD //////

                    Bitmap finalPreviewBitmap = previewBitmap; // 这里可以解决OSD闪烁的问题；Android：图像解码/绘制
                    postEncodeFrame(finalPreviewBitmap);
                ///
                } else if (enableLiveEncode) {
                    if (mResolution == null) { // 条件：按运行状态分支
                        Log.i(Log.TAG, "直播帧跳过，mResolution 为空" + "，camID = " + camID); // 日志：记录相机状态
                        return; // 返回：结束当前方法
                    }
                    if (rtph264 == null) { // 条件：按运行状态分支
                        Log.i(Log.TAG, "直播帧跳过，rtph264 为空" + "，isLiving = " + isLiving()); // 日志：记录相机状态
                        return; // 返回：结束当前方法
                    }
                    drawWatermark(previewBitmap, id, streamType, false);  // 先AI识别再画OSD
                    Bitmap finalPreviewBitmap = previewBitmap;  // 这里可以解决OSD闪烁的问题；Android：图像解码/绘制
                    postEncodeFrame(finalPreviewBitmap);
                }
                ///
                if (mOnShow && controllerCallback != null && previewBitmap != null ) { // 条件：按运行状态分支
                    Bitmap localPreviewBitmap = rotate90ClockwiseWithCanvas(previewBitmap); // Android：图像解码/绘制
                    controllerCallback.onFrame(localPreviewBitmap); /////；调用：执行下一步
                }
            } catch (Exception e) {
                Log.i(Log.TAG, "图片处理异常：" + e.getMessage()); // 日志：记录相机状态
            } finally {
                if (img != null) img.close(); // Image：释放底层buffer
            }
        }
    };

    private final ImageReader.OnImageAvailableListener mStillImageAvailableListener = new ImageReader.OnImageAvailableListener() { // Android：相机帧队列
        @Override
        public void onImageAvailable(ImageReader reader) { // 回调：ImageReader有新帧
            Image img = reader.acquireLatestImage(); // ImageReader：取最新帧
            try { // 异常：保护相机/IO调用
                if (img == null || !mCameraPhotoing) { // 状态：当前正在抓拍
                    return; // 返回：结束当前方法
                }
                if (!takePhotoOnce.compareAndSet(true, false)) { // 同步：只允许一帧完成本次抓拍
                    return; // 返回：结束当前方法
                }
                Bitmap bitmap = imageDecode(img); // Android：图像解码/绘制
                if (bitmap == null) { // 条件：按运行状态分支
                    Log.i(Log.TAG, "Still photo decode failed, camID = " + camID); // 日志：记录相机状态
                    photoDone.set(true); // 条件：takePhoto等它变true
                    mCameraPhtotingLock.notifyLock(); // 同步：takePhoto等待，保存/失败/超时唤醒
                    return; // 返回：结束当前方法
                }
                if (rotate == 1) { // 条件：按运行状态分支
                    bitmap = rotate180WithCanvas(bitmap); // 赋值：更新状态
                }
                bitmap = preProcessingPhoto(bitmap); // 赋值：更新状态
                Log.i(Log.TAG, "Still photo resolution: " + bitmap.getWidth() + "x" + bitmap.getHeight() + ", camID = " + camID); // 日志：记录相机状态
                saveCapturedPhoto(bitmap);
            } catch (Exception e) {
                Log.i(Log.TAG, "Still photo process error: " + e.getMessage()); // 日志：记录相机状态
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
            byte[] picByte = ImageUtils.bitmap2Bytes(NettyUtils.matrixBitmap(previewBitmap, 0.5f)); // 赋值：更新状态
            Log.i(Log.TAG, picByte.length + ""); // 日志：记录相机状态

            CommonResponseEntity commonResponseEntity = new CommonResponseEntity(); // 赋值：更新状态
            commonResponseEntity.type = (Constant.TAKE_PHOTO); // 赋值：更新状态
            commonResponseEntity.content = ("图片"); // 赋值：更新状态
            commonResponseEntity.picByte = (picByte); // 赋值：更新状态

            NettyTcpServer.getInstance().sendMsgToServer(commonResponseEntity, // 调用：执行下一步
                    future -> {
                        if (future.isSuccess()) { // 条件：按运行状态分支
                            Log.i(Log.TAG, "Write auth successful"); // 日志：记录相机状态
                        } else {
                            Log.i(Log.TAG, "Write auth error"); // 日志：记录相机状态
                        }
                    });
        } catch (Exception e) {
            Log.i(Log.TAG, e.getMessage()); // 日志：记录相机状态
        }
    }


    private void captureContinuousPictures() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            // 锁定AE调节，否则录像或视频画面在特定光线条件下会不停闪烁
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true); // Camera2：锁定自动曝光
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO); // Camera2：构建下一次请求参数
            mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler); // Camera2：持续提交预览请求
        } catch (Exception e) {
            Log.i(Log.TAG, "视频预览异常：" + e.getMessage()); // 日志：记录相机状态
        }
    }

    private String imageFormatToString(int format) {
        switch (format) {
            case ImageFormat.JPEG:
                return "JPEG";
            case ImageFormat.YUV_420_888:
                return "YUV_420_888";
            default:
                return "UNKNOWN(" + format + ")";
        }
    }

    ///
    private void captureStillPicture() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            CaptureRequest.Builder captureBuilder = // Camera2：发送给HAL的请求
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE); // Camera2：创建请求模板

            ImageReader targetReader = mStillImageReader != null ? mStillImageReader : mImageReader; // Camera2：接收预览/抓拍帧
            if (targetReader == null) { // 条件：按运行状态分支
                photoDone.set(true); // 条件：takePhoto等它变true
                mCameraPhtotingLock.notifyLock(); // 同步：takePhoto等待，保存/失败/超时唤醒
                return; // 返回：结束当前方法
            }

            int targetFormat = targetReader.getImageFormat();

//            StillCapture targetReader 信息，camID = 0，targetReader = mImageReader，format = 256，formatName = JPEG，size = 1920x1088，sessionVideoMode = false
            Log.i(Log.TAG,
                    "StillCapture targetReader 信息"
                            + "，camID = " + camID
                            + "，targetReader = " + (targetReader == mStillImageReader ? "mStillImageReader" : "mImageReader")
                            + "，format = " + targetFormat
                            + "，formatName = " + imageFormatToString(targetFormat)
                            + "，size = " + targetReader.getWidth() + "x" + targetReader.getHeight()
                            + "，sessionVideoMode = " + mPreviewSessionVideoMode);


            captureBuilder.addTarget(targetReader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
            captureBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
            captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);

            captureBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            );

            captureBuilder.set(
                    CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_IDLE
            );

            if (mKeyAisRequestMode != null) { // 条件：按运行状态分支
                captureBuilder.set(mKeyAisRequestMode, new int[]{2}); // 调用：执行下一步
            }

            CameraCaptureSession.CaptureCallback captureCallback =
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted( // 回调：单次请求完成
                                @NonNull CameraCaptureSession session, // Camera2：输出会话
                                @NonNull CaptureRequest request, // Camera2：发送给HAL的请求
                                @NonNull TotalCaptureResult result) { // Camera2：HAL返回帧元数据

                            Log.i(Log.TAG, "onCaptureCompleted() 成功 返回请求的 metadata/result，真正数据在mOnImageAvailableListener中处理");

                            // 主要是一个调试和验证用的 metadata 读取逻辑
                            if (mKeyAisResult != null) {
                                int[] resultModes = result.get(mKeyAisResult);

                                if (resultModes != null) {
                                    for (int mode : resultModes) {
                                        Log.i(Log.TAG, "MFB Result Mode: " + mode);
                                    }
                                } else {
                                    Log.i(Log.TAG, "captureStillPicture::MFB Result Mode not available.");
                                }
                            }

                            restorePreviewRepeatingAfterStillCapture();
                        }
                    };



            mPreviewSession.capture(
                    captureBuilder.build(),
                    captureCallback,
                    mBackgroundHandler
            );

        } catch (Exception e) {
            Log.i(Log.TAG, "拍摄照片异常：" + e.getMessage()); // 日志：记录相机状态

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
            if (isRecording()) { // 条件：按运行状态分支
                refreshLowNoiseRepeatingRequest(getVideoCodec(streamType), true);
            } else if (isLiving() || enableLiveEncode) {
                refreshLowNoiseRepeatingRequest(null, false);
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "Restore preview after still capture error: " + e.getMessage()); // 日志：记录相机状态
        }
    }
    ///

    private static final int STATE_PREVIEW = 0; // 状态机：AF/AE/抓拍/视频流转
    private static final int STATE_WAITING_AF_LOCK = 1; // 状态机：AF/AE/抓拍/视频流转
    private static final int STATE_WAITING_AE_LOCKING = 3; // 状态机：AF/AE/抓拍/视频流转
    private static final int STATE_PICTURE_TAKING = 4; // 状态机：AF/AE/抓拍/视频流转
    private static final int STATE_VIDEO_RECORDING = 5; // 状态机：AF/AE/抓拍/视频流转
    private static final int STATE_VIDEO_LIVING = 6; // 状态机：AF/AE/抓拍/视频流转
    private int mState = STATE_PREVIEW; // 状态机：AF/AE/抓拍/视频流转
    private volatile boolean previewReady; // 条件：会话可出帧后才处理Image
    private volatile boolean mPreviewSessionVideoMode = false; // 成员：保存运行状态

    private CameraCaptureSession.CaptureCallback mCaptureCallback // 回调：推进AF/AE状态机
            = new CameraCaptureSession.CaptureCallback() { // Camera2：输出会话

        private void process(CaptureResult result) { // Camera2：HAL返回帧元数据
            switch (mState) { // 状态机：AF/AE/抓拍/视频流转
                case STATE_PREVIEW: // 状态机：AF/AE/抓拍/视频流转
                    break;
                case STATE_WAITING_AF_LOCK: { // 状态机：AF/AE/抓拍/视频流转
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE); // Camera2：HAL返回帧元数据
                    if (System.currentTimeMillis() - mLockFocusTime > 3 * 1000) { // 条件：按运行状态分支
                        Log.i(Log.TAG, "对焦3秒超时，开始自动曝光"); // 日志：记录相机状态
                        runPrecaptureSequence();
                    } else if (afState == null) { // 会有返回空的情况

                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) { // 状态机：AF/AE/抓拍/视频流转
                        Log.i(Log.TAG, String.format("对焦%s，焦点锁定", CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ? "成功" : "失败")); // 状态机：AF/AE/抓拍/视频流转
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE); // Camera2：HAL返回帧元数据
                        if (aeState != null && aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) { // 状态机：AF/AE/抓拍/视频流转
                            Log.i(Log.TAG, "曝光结束，自动曝光很好"); // 日志：记录相机状态
                            mState = STATE_PREVIEW; // 状态机：AF/AE/抓拍/视频流转
                            mCameraFocusLock.notifyLock(); // 同步：lockFocus等待，AF/AE回调唤醒
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_AE_LOCKING: { // 状态机：AF/AE/抓拍/视频流转
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE); // Camera2：HAL返回帧元数据
                    if (System.currentTimeMillis() - mLockFocusTime > 3 * 1000) { // 条件：按运行状态分支
                        Log.i(Log.TAG, "曝光结束，自动曝光3秒超时"); // 日志：记录相机状态
                        mState = STATE_PREVIEW; // 状态机：AF/AE/抓拍/视频流转
                        mCameraFocusLock.notifyLock(); // 同步：lockFocus等待，AF/AE回调唤醒
                    } else if (aeState == null) {

                    } else if (aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        Log.i(Log.TAG, "曝光结束，需要补光"); // 日志：记录相机状态
                        mState = STATE_PREVIEW; // 状态机：AF/AE/抓拍/视频流转
                        mCameraFocusLock.notifyLock(); // 同步：lockFocus等待，AF/AE回调唤醒
                    } else if (aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                            aeState == CaptureResult.CONTROL_AE_STATE_LOCKED) { // 状态机：AF/AE/抓拍/视频流转
                        Log.i(Log.TAG, "曝光结束，自动曝光很好"); // 日志：记录相机状态
                        mState = STATE_PREVIEW; // 状态机：AF/AE/抓拍/视频流转
                        mCameraFocusLock.notifyLock(); // 同步：lockFocus等待，AF/AE回调唤醒
                    }
                    break;
                }
                case STATE_PICTURE_TAKING: // 状态机：AF/AE/抓拍/视频流转
                    captureStillPicture();
                    /////
                    if (mKeyAisResult != null) { // 条件：按运行状态分支
                        int[] resultModes = result.get(mKeyAisResult); // 赋值：更新状态
                        if (resultModes != null) { // 条件：按运行状态分支
                            for (int resMode : resultModes) { // 循环：遍历数据
                                Log.i(Log.TAG, "MFB Result Mode: " + resMode); // 日志：记录相机状态
                            }
                        } else {
                            Log.i(Log.TAG, "MFB Result Mode not available."); // 日志：记录相机状态
                        }
                    }
                    /////
                    mState = STATE_PREVIEW; // 状态机：AF/AE/抓拍/视频流转
                    previewReady = true; // 条件：会话可出帧后才处理Image
                    break;
                case STATE_VIDEO_LIVING: // 状态机：AF/AE/抓拍/视频流转
                case STATE_VIDEO_RECORDING: // 状态机：AF/AE/抓拍/视频流转
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
            mState = STATE_WAITING_AE_LOCKING; // 状态机：AF/AE/抓拍/视频流转
            mLockFocusTime = System.currentTimeMillis(); // 赋值：更新状态
            /////
            // 要先AF和AE，再进行多帧降噪，不然会AE失败！
            if (mKeyAisRequestMode != null) { // 条件：按运行状态分支
                mPreviewRequestBuilder.set(mKeyAisRequestMode, new int[]{2}); // Camera2：构建下一次请求参数
                // 需要等待多帧降噪完成才能抓拍
                if (mCameraPhotoing) { // 状态：当前正在抓拍
                    SystemClock.sleep(5000); // 阻塞：当前线程睡眠
                }
            }
            /////
            mPreviewSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler); // Camera2：提交单次请求
        } catch (Exception e) {
            Log.i(Log.TAG, "自动曝光异常：" + e.getMessage()); // 日志：记录相机状态
        }
    }

    private void lockFocus(int timeoutMilsec, int captureMode, boolean isRecordVideo, Settings.VideoCodec vc) { /////；成员：保存运行状态
        try { // 异常：保护相机/IO调用
            //Log.i(Log.TAG, "开始自动对焦");
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); // Camera2：创建请求模板
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface()); // Camera2：绑定输出Surface

            if (isRecordVideo){ // 条件：按运行状态分支
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(vc.frame, vc.frame));   // 摄像头帧率  摄像头最大帧率为60fps，程序的处理速度<=10fps，可以优化程序的处理速度。；Camera2：限制帧率范围

            }else {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(10, 10));   // 摄像头帧率  摄像头最大帧率为60fps，程序的处理速度<=10fps，可以优化程序的处理速度。；Camera2：限制帧率范围
            }

            applyLowNoiseCaptureRequestParameters(vc, isRecordVideo);  // 这里面又会再设置一次 FPS。最终生效的是该函数最后写入的值
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, captureMode); ///// AF：自动对焦；Camera2：构建下一次请求参数
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START); // Camera2：触发自动对焦
            mState = STATE_WAITING_AF_LOCK; // 状态机：AF/AE/抓拍/视频流转
            mLockFocusTime = System.currentTimeMillis(); // 赋值：更新状态
            mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler); // Camera2：持续提交预览请求
            mCameraFocusLock.waitLock(timeoutMilsec); // 同步：lockFocus等待，AF/AE回调唤醒
        } catch (Exception e) {
            Log.i(Log.TAG, "对焦异常：" + e.getMessage()); // 日志：记录相机状态
        }
    }

    private void unlockFocus() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (mPreviewRequestBuilder != null) { // Camera2：构建下一次请求参数
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, // Camera2：触发自动对焦
                        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "取消对焦异常：" + e.getMessage()); // 日志：记录相机状态
        }
    }

    private void startBackgroundThread() { // 入口：方法定义
        mBackgroundThread = new HandlerThread("CameraBackground"); // Android：串行消息线程
        mBackgroundThread.start(); // 调用：执行下一步
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper()); // 线程：承接Camera2回调
    }

    private void stopBackgroundThread() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (mBackgroundThread != null) { // 条件：按运行状态分支
                mBackgroundThread.quitSafely(); // 调用：执行下一步
                mBackgroundThread.join(); // 调用：执行下一步
                mBackgroundThread = null; // 赋值：更新状态
                mBackgroundHandler = null; // 线程：承接Camera2回调
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "摄像头线程退出异常：" + e.getMessage()); // 日志：记录相机状态
        }
    }


    private void createPreviewSession(int width, int height, boolean video) { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (width <= 0 || height <= 0) { // 条件：按运行状态分支
                Log.i(Log.TAG, "创建摄像头会话失败，无效的视频分辨率[" + width + ":" + height + "]"); // 日志：记录相机状态
                return; // 返回：结束当前方法
            }
            mPreviewSessionVideoMode = false; // 赋值：更新状态
            closeImageReader();
            closeStillImageReader();
            mImageReader = ImageReader.newInstance(width, height, video ? ImageFormat.YUV_420_888 : ImageFormat.JPEG, 3);  // 3；ImageReader：创建帧输出队列
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler); // ImageReader：注册帧回调

//            List<Surface> surfaces = new ArrayList<>();
//            surfaces.add(mImageReader.getSurface());
//            if (video) {
//                Point stillSize = getConfiguredPhotoResolution();
//                mStillImageReader = ImageReader.newInstance(stillSize.x, stillSize.y, ImageFormat.JPEG, 2);
//                mStillImageReader.setOnImageAvailableListener(mStillImageAvailableListener, mBackgroundHandler);
//                surfaces.add(mStillImageReader.getSurface());
//                Log.i(Log.TAG, "直播会话增加 JPEG 静态拍照输出：" + stillSize.x + "x" + stillSize.y + "，camID = " + camID);
//            }

//            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
            List<Surface> surfaces = new ArrayList<>(); // Android：Camera输出端
            surfaces.add(mImageReader.getSurface()); // Camera2：接收预览/抓拍帧
            // Video sessions keep only the YUV output. Some MIPI sensors stop producing
            // frames when YUV and high-resolution JPEG outputs are active together.

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() { // Camera2：配置HAL输出流
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) { // 回调：Camera2会话已配置
                    if (mCameraDevice == null) return; // Camera2：已打开的相机句柄
                    mPreviewSession = cameraCaptureSession; // Camera2：向HAL提交请求的会话
                    mPreviewSessionVideoMode = video; // 赋值：更新状态
                    mCameraOpenCloseLock.notifyLock(); // 同步：唤醒等待线程
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) { // 回调：Camera2会话配置失败
                    try { // 异常：保护相机/IO调用
                        cameraCaptureSession.close(); // 调用：执行下一步
                    } catch (Exception ignored) {
                    }
                    closePreviewSession();
                    closeImageReader();
                    closeStillImageReader();
                    mCameraOpenCloseLock.notifyLock(); // 同步：唤醒等待线程
                }
            }, mBackgroundHandler); // 线程：承接Camera2回调
            mCameraOpenCloseLock.waitLock(2500); // 同步：等待唤醒或超时
        } catch (Exception e) {
            Log.i(Log.TAG, "create camera session exception: " + e.getMessage()); // 日志：记录相机状态
            closePreviewSession();
            closeImageReader();
            closeStillImageReader();
            mCameraOpenCloseLock.notifyLock(); // 同步：唤醒等待线程
//            Log.i(Log.TAG, "创建摄像头会话异常：" + e.getMessage());
//            closePreviewSession();
//            closeImageReader();
//            closeStillImageReader();
//            mCameraOpenCloseLock.notifyLock();
        }
    }


    CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() { // Camera2：相机设备对象
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) { // 回调：相机已打开
            mCameraDevice = cameraDevice; // Camera2：已打开的相机句柄
            mCameraOpenCloseLock.notifyLock(); // 同步：唤醒等待线程
            //Log.i(Log.TAG, "打开摄像头" + camID +"成功");
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) { // 回调：相机断开
            mCameraDevice = null; // Camera2：已打开的相机句柄
            previewReady = false; // 条件：会话可出帧后才处理Image
            mDualSessionStarted = false; // 赋值：更新状态
            mDualSessionStarting = false; // 赋值：更新状态
            closePreviewSession();
            clearState(DevState.OPENING);
            mCameraOpenCloseLock.notifyLock(); // 同步：唤醒等待线程
            cameraDevice.close(); // 调用：执行下一步
            Log.i(Log.TAG, "MIPI camera disconnected, camID = " + camID); // 日志：记录相机状态
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) { // 回调：相机错误
            mCameraDevice = null; // Camera2：已打开的相机句柄
            previewReady = false; // 条件：会话可出帧后才处理Image
            mDualSessionStarted = false; // 赋值：更新状态
            mDualSessionStarting = false; // 赋值：更新状态
            closePreviewSession();
            clearState(DevState.OPENING);
            mCameraOpenCloseLock.notifyLock(); // 同步：唤醒等待线程
            cameraDevice.close(); // 调用：执行下一步
            Log.i(Log.TAG, "打开摄像头失败，camID = " + camID + "，error=" + error); // 日志：记录相机状态
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
                Log.i(Log.TAG, "摄像头打开没有权限"); // 日志：记录相机状态
                return; // 返回：结束当前方法
            }
            CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE); // Android：连接CameraService
            int camId = camID % 2; // 赋值：更新状态
            if (camId >= cameraManager.getCameraIdList().length) { // 条件：按运行状态分支
                Log.i(Log.TAG, "摄像头" + camId + "超过支持的摄像头总数：" + cameraManager.getCameraIdList().length); // 日志：记录相机状态
                return; // 返回：结束当前方法
            }
//            for (String camerID : cameraManager.getCameraIdList()) {
//                if (!camerID.equals(Integer.toString(camId))) continue;
//
//                //Settings.VideoCodec vc = getVideoCodec(streamType);
//                //mResolution = Settings.VideoCodec.getResolution(vc.resolution);
//
//                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(camerID);
//                /////
//                // 获取设备支持的CameraCharacteristics Key列表
//                minFocusDist = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
//                Log.i(Log.TAG, "MIPI摄像头最小对焦距离为" + 1 / minFocusDist * 100 + "厘米");
//                List<CameraCharacteristics.Key<?>> keyList = cameraCharacteristics.getKeys();
//                for (CameraCharacteristics.Key<?> key : keyList) {
//                    if (key.getName().equals(AIS_AVAILABLE_MODES_KEY_NAME)) {
//                        mKeyAisAvailableModes = (CameraCharacteristics.Key<int[]>) key;
//                        Log.i(Log.TAG, "Found CameraCharacteristics Key: " + AIS_AVAILABLE_MODES_KEY_NAME);
//                    }
//                }
//                // 获取CaptureResult Key（用于读取MFB处理结果）
//                List<CaptureResult.Key<?>> resultKeyList = cameraCharacteristics.getAvailableCaptureResultKeys();
//                for (CaptureResult.Key<?> resultKey : resultKeyList) {
//                    if (resultKey.getName().equals(AIS_RESULT_MODE_KEY_NAME)) {
//                        mKeyAisResult = (CaptureResult.Key<int[]>) resultKey;
//                        Log.i(Log.TAG, "Found CaptureResult Key: " + AIS_RESULT_MODE_KEY_NAME);
//                    }
//                }
//                // 获取CaptureRequest Key（用于设置MFB模式）
//                List<CaptureRequest.Key<?>> requestKeyList = cameraCharacteristics.getAvailableCaptureRequestKeys();
//                for (CaptureRequest.Key<?> requestKey : requestKeyList) {
//                    if (requestKey.getName().equals(AIS_REQUEST_MODE_KEY_NAME)) {
//                        mKeyAisRequestMode = (CaptureRequest.Key<int[]>) requestKey;
//                        Log.i(Log.TAG, "Found CaptureRequest Key: " + AIS_REQUEST_MODE_KEY_NAME);
//                    }
//                }
//                if (mKeyAisAvailableModes != null) {
//                    int[] availableModes = cameraCharacteristics.get(mKeyAisAvailableModes);
//                    if (availableModes != null) {
//                        for (int mode : availableModes) {
//                            Log.i(Log.TAG, "Supported MFB Mode: " + mode);
//                        }
//                    } else {
//                        Log.i(Log.TAG, "No available MFB modes.");
//                    }
//                } else {
//                    Log.i(Log.TAG, "MFB Key not found.");
//                }
//                /////
//                //mFlashSupported = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
//
//                streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//                /*Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
//                for (int i = 0; i < sizes.length; i++) {
//                    Log.i(Log.TAG, "支持的分辨率：" + sizes[i].getWidth() + "x" + sizes[i].getHeight());
//                }
//
//                Range<Integer>[] fpsRange = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
//                for (Range<Integer> fps : fpsRange) {
//                    Log.i(Log.TAG, "支持的帧率：[" + fps.getLower() + "," + fps.getUpper() + "]");
//                }*/
//
//                /*int[] modes = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
//                for (int i = 0; i < modes.length; i++) {
//                    Log.i(Log.TAG, "支持的场景模式：" + modes[i]);
//                }*/
//
//                startBackgroundThread();
//                cameraManager.openCamera(camerID, mStateCallback, mBackgroundHandler);
//                mCameraOpenCloseLock.waitLock(2500);
//                break;
//            }
            String cameraId = String.valueOf(camId); ///；赋值：更新状态

            ///
            if (mCameraDevice != null) { // Camera2：已打开的相机句柄
                return; // 返回：结束当前方法
            }
            ///
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId); // 赋值：更新状态
            minFocusDist = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE); // 赋值：更新状态
            if (minFocusDist > 0) { // 条件：按运行状态分支
                Log.i(Log.TAG, "MIPI摄像头最小对焦距离为" + 1 / minFocusDist * 100 + "厘米"); // 日志：记录相机状态
            }

            mKeyAisAvailableModes = null; // 赋值：更新状态
            mKeyAisResult = null; // 赋值：更新状态
            mKeyAisRequestMode = null; // 赋值：更新状态

            List<CameraCharacteristics.Key<?>> keyList = cameraCharacteristics.getKeys(); // 赋值：更新状态
            for (CameraCharacteristics.Key<?> key : keyList) { // 循环：遍历数据
                if (key.getName().equals(AIS_AVAILABLE_MODES_KEY_NAME)) { // 条件：按运行状态分支
                    mKeyAisAvailableModes = (CameraCharacteristics.Key<int[]>) key; // 赋值：更新状态
                    Log.i(Log.TAG, "Found CameraCharacteristics Key: " + AIS_AVAILABLE_MODES_KEY_NAME); // 日志：记录相机状态
                }
            }



            List<CaptureResult.Key<?>> resultKeyList = cameraCharacteristics.getAvailableCaptureResultKeys(); // Camera2：HAL返回帧元数据
            for (CaptureResult.Key<?> resultKey : resultKeyList) { // Camera2：HAL返回帧元数据
                if (resultKey.getName().equals(AIS_RESULT_MODE_KEY_NAME)) { // 条件：按运行状态分支
                    mKeyAisResult = (CaptureResult.Key<int[]>) resultKey; // Camera2：HAL返回帧元数据
                    Log.i(Log.TAG, "Found CaptureResult Key: " + AIS_RESULT_MODE_KEY_NAME); // Camera2：HAL返回帧元数据
                }
            }

            List<CaptureRequest.Key<?>> requestKeyList = cameraCharacteristics.getAvailableCaptureRequestKeys(); // Camera2：发送给HAL的请求
            for (CaptureRequest.Key<?> requestKey : requestKeyList) { // Camera2：发送给HAL的请求
                if (requestKey.getName().equals(AIS_REQUEST_MODE_KEY_NAME)) { // 条件：按运行状态分支
                    mKeyAisRequestMode = (CaptureRequest.Key<int[]>) requestKey; // Camera2：发送给HAL的请求
                    Log.i(Log.TAG, "Found CaptureRequest Key: " + AIS_REQUEST_MODE_KEY_NAME); // Camera2：发送给HAL的请求
                }
            }

            if (mKeyAisAvailableModes != null) { // 条件：按运行状态分支
                int[] availableModes = cameraCharacteristics.get(mKeyAisAvailableModes); // 赋值：更新状态
                if (availableModes != null) { // 条件：按运行状态分支
                    for (int mode : availableModes) { // 循环：遍历数据
                        Log.i(Log.TAG, "Supported MFB Mode: " + mode); // 日志：记录相机状态
                    }
                } else {
                    Log.i(Log.TAG, "No available MFB modes."); // 日志：记录相机状态
                }
            } else {
                Log.i(Log.TAG, "MFB Key not found."); // 日志：记录相机状态
            }

            streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP); // 赋值：更新状态

            startBackgroundThread();
            cameraManager.openCamera(cameraId, mStateCallback, mBackgroundHandler); // Camera2：异步打开相机
//            mCameraOpenCloseLock.waitLock(2500);


            // 这个地方打开摄像头失败的话，不进行清理资源，会导致下一次打开失败，如果只等待2.5s就释放资源的话，2.5s时间太短，可能打不开摄像头就又关闭了
//            boolean opened = mCameraOpenCloseLock.waitLock(2500);
//            if (!opened || mCameraDevice == null) {
//                Log.i(Log.TAG, "打开摄像头超时，清理后台线程和残留资源，camID = " + camID);
//                closePreviewSession();
//                closeImageReader();
//                closeStillImageReader();
//                stopBackgroundThread();
//            }

            boolean opened = mCameraOpenCloseLock.waitLock(2500); // 同步：等待唤醒或超时
            if (!opened || mCameraDevice == null) { // Camera2：已打开的相机句柄
                Log.i(Log.TAG, "打开摄像头等待超时，延迟确认，camID = " + camID); // 日志：记录相机状态

                if (mBackgroundHandler != null) { // 线程：承接Camera2回调
                    mBackgroundHandler.postDelayed(() -> { // 线程：承接Camera2回调
                        if (mCameraDevice == null && !isLiving() && !isRecording() && !mCameraPhotoing) { // 状态：当前正在抓拍
                            Log.i(Log.TAG, "打开摄像头延迟确认仍失败，清理资源，camID = " + camID); // 日志：记录相机状态
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
            Log.i(Log.TAG, String.format("打开摄像头%d异常：%s", camID, e.getMessage())); // 日志：记录相机状态
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
                try { // 异常：保护相机/IO调用
                    mPreviewSession.stopRepeating(); // Camera2：向HAL提交请求的会话
                } catch (Exception ignored) {
                }
                try { // 异常：保护相机/IO调用
                    mPreviewSession.abortCaptures(); // Camera2：向HAL提交请求的会话
                } catch (Exception ignored) {
                }
                mPreviewSession.close(); // Camera2：向HAL提交请求的会话
                mPreviewSession = null; // Camera2：向HAL提交请求的会话
            }
        } catch (Exception e) {
            mPreviewSession = null; // Camera2：向HAL提交请求的会话
        } finally {
            mPreviewSessionVideoMode = false; // 赋值：更新状态
        }
        mPreviewRequestBuilder = null; // Camera2：构建下一次请求参数
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
        Point size = Settings.PhotoConfig.getImageSize(photoConfig.size); // 赋值：更新状态
        if (size == null) { // 条件：按运行状态分支
            size = new Point(1920, 1080); // 赋值：更新状态
        }
        if (is6735) { // 条件：按运行状态分支
            return new Point(1280, 720); // 返回：结束当前方法
        }
        if (streamConfigurationMap != null) { // 条件：按运行状态分支
            Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG); // 赋值：更新状态
            return getBestSize2(sizes, size.x, size.y); // 返回：结束当前方法
        }
        return size; // 返回：结束当前方法
    }

    private volatile boolean enableLiveEncode = false; // 成员：保存运行状态
    private volatile boolean liveStarting = false; // 成员：保存运行状态
    private volatile boolean videoStarting = false; // 成员：保存运行状态

    public void setEnableLiveEncode(boolean enable) { // 入口：方法定义
        enableLiveEncode = enable; // 赋值：更新状态
    }

    public void stopLiveAndCloseBothIfIdle() { // 入口：方法定义
        liveStarting = false; // 赋值：更新状态
        setEnableLiveEncode(false);
        closeBothCameraIfNoLive();
    }

    private void ensureMipiVideoEncoder(int stream, boolean mipiMark) { // 入口：方法定义
        synchronized (mipiStreamLock) { // 同步：互斥访问共享状态
            if (mediaCodec == null) { // 条件：按运行状态分支
                initVideoEncoder(stream, mResolution.x, mResolution.y, mipiMark);
                mipiLivePpsSps = null; // 赋值：更新状态
                mipiLiveFirstFrameTimestamp = 0; // 赋值：更新状态
            }
        }
    }

    private void releaseMipiVideoEncoderIfIdle() { // 入口：方法定义
        synchronized (mipiStreamLock) { // 同步：互斥访问共享状态
            if (!isLiving() && !isRecording() && mediaCodec != null) { // 条件：按运行状态分支
                uninitVideoEncoder();
                mipiLivePpsSps = null; // 赋值：更新状态
                mipiLiveFirstFrameTimestamp = 0; // 赋值：更新状态
            }
        }
    }

    private void tryStartMipiMuxer() { // 入口：方法定义
        if (muxerStarted || mediaMuxer == null) return; // 条件：按运行状态分支

        if (useAudio) { // 条件：按运行状态分支
            if (videoTrackIndex >= 0 && audioTrackIndex >= 0) { // 条件：按运行状态分支
                mediaMuxer.start(); // 调用：执行下一步
                muxerStarted = true; // 赋值：更新状态
                muxerEverStarted = true; // 赋值：更新状态
            }
        } else if (videoTrackIndex >= 0) {
            mediaMuxer.start(); // 调用：执行下一步
            muxerStarted = true; // 赋值：更新状态
            muxerEverStarted = true; // 赋值：更新状态
        }
    }

    private void releaseMipiMuxer() { // 入口：方法定义
        if (mediaMuxer == null) return; // 条件：按运行状态分支

        try { // 异常：保护相机/IO调用
            if (muxerStarted) { // 条件：按运行状态分支
                mediaMuxer.stop(); // 调用：执行下一步
            }
            mediaMuxer.release(); // 调用：执行下一步
            Log.i(Log.TAG, "release MIPI muxer success" // 日志：记录相机状态
                    + "，samples = " + mipiRecordSamplesWritten // 赋值：更新状态
                    + "，bytes = " + mipiRecordBytesWritten // 赋值：更新状态
                    + "，keyFrames = " + mipiRecordKeyFramesWritten); // 赋值：更新状态
        } catch (Exception e) {
            Log.i(Log.TAG, "release MIPI muxer error: " + e.getMessage()); // 日志：记录相机状态
        } finally {
            mediaMuxer = null; // 赋值：更新状态
            videoTrackIndex = -1; // 赋值：更新状态
            audioTrackIndex = -1; // 赋值：更新状态
            muxerStarted = false; // 赋值：更新状态
            muxerEverStarted = false; // 赋值：更新状态
            avStartNs = 0; // 赋值：更新状态
        }
    }

    private boolean ensureVideoPreviewSession(Settings.VideoCodec vc, boolean isRecordVideo) { // 入口：方法定义
        if (mPreviewSession != null && mPreviewSessionVideoMode) { // Camera2：向HAL提交请求的会话
            return true; // 返回：结束当前方法
        }
        if (mCameraDevice == null || vc == null || mResolution == null) { // Camera2：已打开的相机句柄
            return false; // 返回：结束当前方法
        }

        closePreviewSession();
        createPreviewSession(mResolution.x, mResolution.y, true);
        if (mPreviewSession == null) { // Camera2：向HAL提交请求的会话
            Log.i(Log.TAG, "切换视频会话失败，PreviewSession 为空，camID = " + camID); // 日志：记录相机状态
            return false; // 返回：结束当前方法
        }

        previewReady = true; // 条件：会话可出帧后才处理Image
        lockFocus(
                10000,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO, // Camera2：发送给HAL的请求
                isRecordVideo,
                isRecordVideo ? vc : null
        );
        return mPreviewSession != null && mPreviewSessionVideoMode; // Camera2：向HAL提交请求的会话
    }

    private void resetMipiRecordStats() { // 入口：方法定义
        mipiRecordSamplesWritten = 0; // 赋值：更新状态
        mipiRecordBytesWritten = 0; // 赋值：更新状态
        mipiRecordKeyFramesWritten = 0; // 赋值：更新状态
    }

    private static void closeBothCameraIfNoLive() { // 入口：方法定义
        Camera2Device cam0;
        Camera2Device cam1;
        synchronized (sDualCameraLock) { // 同步：保护双MIPI共享状态
            if (sDualClosing || sDualStarting) { // 条件：按运行状态分支
                return; // 返回：结束当前方法
            }
            cam0 = sCamera0Device; // 赋值：更新状态
            cam1 = sCamera1Device; // 赋值：更新状态
            boolean cam0Live = cam0 != null && (cam0.enableLiveEncode || cam0.liveStarting); // 赋值：更新状态
            boolean cam1Live = cam1 != null && (cam1.enableLiveEncode || cam1.liveStarting); // 赋值：更新状态
            boolean cam0Photoing = cam0 != null && cam0.mCameraPhotoing; // 状态：当前正在抓拍
            boolean cam1Photoing = cam1 != null && cam1.mCameraPhotoing; // 状态：当前正在抓拍
            boolean dualPhotoPending = sDualPhotoTaskCount > 0; // 同步：统计未完成拍照任务
            boolean cam0Recording = cam0 != null && (cam0.isRecording() || cam0.videoStarting); // 赋值：更新状态
            boolean cam1Recording = cam1 != null && (cam1.isRecording() || cam1.videoStarting); // 赋值：更新状态
            Log.i(Log.TAG, "检查是否需要释放双路 Camera" // 日志：记录相机状态
                    + "，cam0Live = " + cam0Live // 赋值：更新状态
                    + "，cam1Live = " + cam1Live // 赋值：更新状态
                    + "，cam0Photoing = " + cam0Photoing // 赋值：更新状态
                    + "，cam1Photoing = " + cam1Photoing // 赋值：更新状态
                    + "，cam0Recording = " + cam0Recording // 赋值：更新状态
                    + "，cam1Recording = " + cam1Recording); // 赋值：更新状态
            if (cam0Live || cam1Live || cam0Photoing || cam1Photoing || dualPhotoPending || cam0Recording || cam1Recording) { // 条件：按运行状态分支
                Log.i(Log.TAG, "仍有直播、拍照或录像任务，不释放双路 Camera"); // 日志：记录相机状态
                return; // 返回：结束当前方法
            }
            Log.i(Log.TAG, "两路均无直播、拍照、录像任务，准备释放双路 Camera"); // 日志：记录相机状态
            sDualClosing = true; // 赋值：更新状态
        }
        try { // 异常：保护相机/IO调用
            if (cam0 != null) { // 条件：按运行状态分支
                cam0.closeCamera(); // 调用：执行下一步
            }
            if (cam1 != null && cam1 != cam0) { // 条件：按运行状态分支
                cam1.closeCamera(); // 调用：执行下一步
            }
        } finally {
            synchronized (sDualCameraLock) { // 同步：保护双MIPI共享状态
                sDualStarted = false; // 赋值：更新状态
                sDualStarting = false; // 赋值：更新状态
                sDualClosing = false; // 赋值：更新状态
                sDualCameraLock.notifyAll(); // 同步：保护双MIPI共享状态
            }
        }
        Log.i(Log.TAG, "双路 Camera 已释放"); // 日志：记录相机状态
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
        super.closeCamera(); // 调用：执行下一步
        setEnableLiveEncode(false);
        mDualSessionStarted = false; // 赋值：更新状态
        mDualSessionStarting = false; // 赋值：更新状态
        liveStarting = false; // 赋值：更新状态
        videoStarting = false; // 赋值：更新状态
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
            sDualStarted = false; // 赋值：更新状态
        }
    }

    /*
        开始录制视频
     */
    @Override
    public boolean videoStop() { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            videoStarting = false; // 赋值：更新状态
            //Log.i(Log.TAG, "停止录制");
//            unlockFocus();
//            close();
            super.videoStop(); // 调用：执行下一步
            /////
            releaseMipiMuxer();
            if (useAudio) { // 条件：按运行状态分支
                uninitAudioEncoder();
            }
            releaseMipiVideoEncoderIfIdle();
            if (!mCameraPhotoing && !isLiving()) { // 状态：当前正在抓拍
                closeBothCameraIfNoLive(); ///
            }
            /////
        } catch (Exception e) {
            Log.i(Log.TAG, "停止录像异常：" + e); // 日志：记录相机状态
            return false; // 返回：结束当前方法
        }
        return true; // 返回：结束当前方法
    }

    @Override   // 录制短视频使用配置文件中的分辨率和I帧间隔
    public boolean videoStart(int stream, String filename, int duration, boolean upload) { // 入口：方法定义
        try { // 异常：保护相机/IO调用
            if (!videoStarting) { // 条件：按运行状态分支
                return false; // 返回：结束当前方法
            }
            videoStarting = true; // 赋值：更新状态

            Settings.VideoCodec vc = getVideoCodec(stream); /////；赋值：更新状态
            mResolution = Settings.VideoCodec.getResolution(vc.resolution); // 赋值：更新状态

            Log.e(Log.TAG,"录制视频"+ vc.frame + ":" + vc.iFrame); // 日志：记录相机状态

            if (is6735) { // 条件：按运行状态分支
                mResolution = new Point(1280, 720); // 赋值：更新状态
            }
            ///
            if (mResolution == null) { // 条件：按运行状态分支
                Log.i(Log.TAG, "录像分辨率为空，使用默认 1536x864" ///；日志：记录相机状态
                        + "，camID = " + camID); // 赋值：更新状态
                mResolution = new Point(1536, 864); ///；赋值：更新状态
            }
            // MIPI session 在打开时会把视频分辨率限制到 1536x864，编码器必须使用同样尺寸。
            if (mResolution.x > 1536 || mResolution.y > 864) { // 条件：按运行状态分支
                mResolution = new Point(1536, 864); // 赋值：更新状态
            }
            Log.i(Log.TAG, "MIPI录制编码分辨率，camID = " + camID // 日志：记录相机状态
                    + "，width = " + mResolution.x // 赋值：更新状态
                    + "，height = " + mResolution.y); // 赋值：更新状态

            if (mPreviewSession == null || mCameraDevice == null) { // Camera2：向HAL提交请求的会话
                Log.i(Log.TAG, "录像失败，CameraDevice 或 PreviewSession 为空" // Camera2：相机设备对象
                        + "，camID = " + camID // 赋值：更新状态
                        + "，mCameraDevice = " + mCameraDevice // Camera2：已打开的相机句柄
                        + "，mPreviewSession = " + mPreviewSession); // Camera2：向HAL提交请求的会话
                videoStarting = false; // 赋值：更新状态
                return false; // 返回：结束当前方法
            }
            if (!ensureVideoPreviewSession(vc, true)) { // 条件：按运行状态分支
                Log.i(Log.TAG, "录像失败，无法切换到视频会话" // 日志：记录相机状态
                        + "，camID = " + camID // 赋值：更新状态
                        + "，mPreviewSessionVideoMode = " + mPreviewSessionVideoMode); // 赋值：更新状态

                videoStarting = false; // 赋值：更新状态

//                if (!isLiving() && !mCameraPhotoing) {
//                    closePreviewSession();
//                    closeImageReader();
//                    closeStillImageReader();
//                    closeBothCameraIfNoLive();
//                }

                return false; // 返回：结束当前方法
            }
            ///

//            createPreviewSession(mResolution.x, mResolution.y, true);

            {
                // 对焦最大超时10秒
//                lockFocus(10000, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,true,vc);
                // 状态设置为录像
                mState = STATE_VIDEO_RECORDING; // 状态机：AF/AE/抓拍/视频流转
            }
            refreshLowNoiseRepeatingRequest(vc, true);
            super.videoStart(stream, filename, duration, upload); /////；调用：执行下一步
            videoStarting = false; // 赋值：更新状态
            String tmpfile = MainActivity.DATA_DIR + "record_" + id + ".mp4"; /////；赋值：更新状态

            /////
            mediaMuxer = new MediaMuxer(tmpfile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4); // Android：视频编码/封装
            muxerStarted = false; // 赋值：更新状态
            videoTrackIndex = -1; // 赋值：更新状态
            resetMipiRecordStats();
            if (useAudio) { // 条件：按运行状态分支
                audioTrackIndex = -1; // 赋值：更新状态
                avStartNs = System.nanoTime(); // 赋值：更新状态
                // 单调递增保护变量
                lastVideoPtsUs = 0; // 赋值：更新状态
                lastAudioPtsUs = 0; // 赋值：更新状态
                // 音频按采样累计
                initAudioRecord();
                initAudioEncoder();
                startAudio();
            }
            ensureMipiVideoEncoder(stream, isLiving() || enableLiveEncode);   // 录制和直播共用 MIPI 编码器
            /////

            new Timer("recordStop").schedule(new TimerTask() { /////；调用：执行下一步
                @Override
                public void run() { // 入口：方法定义
                    Runnable finishRecord = () -> { // 赋值：更新状态
                        videoStop();
                        if (upload) { // 条件：按运行状态分支
                            Utils.su("mv " + tmpfile + " " + filename); // 调用：执行下一步
                        } else {
                            File file = new File(tmpfile); // 赋值：更新状态
                            File finalFile = new File(filename); // 赋值：更新状态
                            file.renameTo(new File(MainActivity.FILE_PATH + id + File.separator + finalFile.getName())); // 调用：执行下一步
                            Log.i(Log.TAG, "MIPI摄像头文件不上传，修改文件为：" + (MainActivity.FILE_PATH + id + File.separator + finalFile.getName())); /////；日志：记录相机状态
                        }
                        File recordedFile = upload // 赋值：更新状态
                                ? new File(filename)
                                : new File(MainActivity.FILE_PATH + id + File.separator + new File(filename).getName()); // 调用：执行下一步
                        Log.i(Log.TAG, "MIPI录制完成文件大小" // 日志：记录相机状态
                                + "，file = " + recordedFile.getAbsolutePath() // 赋值：更新状态
                                + "，exists = " + recordedFile.exists() // 赋值：更新状态
                                + "，length = " + (recordedFile.exists() ? recordedFile.length() : -1) // 赋值：更新状态
                                + "，samples = " + mipiRecordSamplesWritten // 赋值：更新状态
                                + "，bytes = " + mipiRecordBytesWritten // 赋值：更新状态
                                + "，keyFrames = " + mipiRecordKeyFramesWritten); // 赋值：更新状态
                        controllerCallback.onVideoFinished(System.currentTimeMillis(), id, streamType, filename, upload); // 调用：执行下一步
                    };
                    if (procVideoHandler != null) { // 条件：按运行状态分支
                        procVideoHandler.post(finishRecord); // 调用：执行下一步
                    } else {
                        finishRecord.run(); // 调用：执行下一步
                    }
                }
            }, (duration + 1) * 1000);  // 多1秒作为保险余地，不然可能录像时间不足
        } catch (Exception e) {
            videoStarting = false; // 赋值：更新状态
            videoStop(); /////
            Log.e(Log.TAG, "MIPI摄像头录制视频异常: " + e.getMessage()); // 日志：记录相机状态
        }
        return isRecording(); /////；返回：结束当前方法
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
            liveStarting = false; // 赋值：更新状态
            setEnableLiveEncode(false);
            mOnShow = false; // 赋值：更新状态
            ///
            rtph264 = null; // 赋值：更新状态
        } catch (Exception e) {
            Log.i(Log.TAG, "停止预览异常：" + e); // 日志：记录相机状态
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
        this.osd = osd; // 赋值：更新状态
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
    public synchronized boolean open(int stream, onOpenCallback cb, int timeoutSeconds, boolean waitSelfCheck, boolean video, boolean isRecordVideo) { ///；成员：保存运行状态
        ///
        synchronized (sDualCameraLock) { // 同步：保护双MIPI共享状态
            int realCamId = camID % 2; // 赋值：更新状态

            if (realCamId == 0) { // 条件：按运行状态分支
                sCamera0Device = this; // 赋值：更新状态
            } else {
                sCamera1Device = this; // 赋值：更新状态
            }
        }
        if (ALWAYS_OPEN_BOTH_MIPI) { // 条件：按运行状态分支
            Camera2Device cam0;
            Camera2Device cam1;
            synchronized (sDualCameraLock) { // 同步：保护双MIPI共享状态
                cam0 = sCamera0Device; // 赋值：更新状态
                cam1 = sCamera1Device; // 赋值：更新状态

                Log.i(Log.TAG, "双路 Camera 对象检查" // 日志：记录相机状态
                        + "，cam0 = " + cam0 // 赋值：更新状态
                        + "，cam1 = " + cam1 // 赋值：更新状态
                        + "，camID = " + camID // 赋值：更新状态
                        + "，sDualStarted = " + sDualStarted // 赋值：更新状态
                        + "，sDualStarting = " + sDualStarting // 赋值：更新状态
                        + "，sDualClosing = " + sDualClosing); // 赋值：更新状态

                if (cam0 == null || cam1 == null) { // 条件：按运行状态分支
                    if (cb != null) { // 条件：按运行状态分支
                        cb.openFailed(-1); // 调用：执行下一步
                    }
                    return false; // 返回：结束当前方法
                }
                long waitEnd = SystemClock.uptimeMillis() + 15000; // Android：取运行时钟
                while (sDualClosing || sDualStarting) { // 循环：等待状态变化
                    long waitMs = waitEnd - SystemClock.uptimeMillis(); // Android：取运行时钟
                    if (waitMs <= 0) { // 条件：按运行状态分支
                        break;
                    }
                    try { // 异常：保护相机/IO调用
                        sDualCameraLock.wait(waitMs); // 同步：保护双MIPI共享状态
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // 调用：执行下一步
                        break;
                    }
                }
                if (sDualClosing || sDualStarting) { // 条件：按运行状态分支
                    Log.i(Log.TAG, "等待双路 Camera 打开超时"); // 日志：记录相机状态
                    if (cb != null) { // 条件：按运行状态分支
                        cb.openFailed(-1); // 调用：执行下一步
                    }
                    return false; // 返回：结束当前方法
                }

                Log.e(Log.TAG,"sDualStarted"+sDualStarted);

                if (sDualStarted) { // 条件：按运行状态分支

                    Log.e(Log.TAG,"sDualStarted"+sDualStarted);
                    boolean sessionReady = startSessionForDualOpen(stream, video, isRecordVideo); // 赋值：更新状态

                    if (!sessionReady) { // 条件：按运行状态分支
                        Log.i(Log.TAG, "双路 Camera 已标记打开，但创建当前业务 session 失败，重置双路状态并尝试释放空闲资源" // 日志：记录相机状态
                                + "，camID = " + camID // 赋值：更新状态
                                + "，video = " + video // 赋值：更新状态
                                + "，isRecordVideo = " + isRecordVideo); // 赋值：更新状态

                        sDualStarted = false; // 赋值：更新状态
                        sDualStarting = false; // 赋值：更新状态
                        sDualCameraLock.notifyAll(); // 同步：保护双MIPI共享状态

                        if (!isLiving() && !isRecording() && !mCameraPhotoing && !liveStarting && !videoStarting) { // 状态：当前正在抓拍
                            closeCamera();
                        } else {
                            closeBothCameraIfNoLive();
                        }
                    }

                    if (cb != null) { // 条件：按运行状态分支
                        if (sessionReady) { // 条件：按运行状态分支
                            cb.openSucceed(); // 调用：执行下一步
                        } else {
                            cb.openFailed(-1); // 调用：执行下一步
                        }
                    }

                    return sessionReady; // 返回：结束当前方法
                }
                sDualStarting = true; // 赋值：更新状态
            }
            boolean result = false; // 赋值：更新状态
            try { // 异常：保护相机/IO调用
                synchronized (sDualCameraLock) { // 同步：保护双MIPI共享状态
                    cam0 = sCamera0Device; // 赋值：更新状态
                    cam1 = sCamera1Device; // 赋值：更新状态
                }
                if (cam0 == null || cam1 == null) { // 条件：按运行状态分支
                    return false; // 返回：结束当前方法
                }
                boolean ret0 = false; // 赋值：更新状态
                Log.i(Log.TAG, "第 1 步：打开 camera0"); // 日志：记录相机状态
                boolean ret1 = false; // 赋值：更新状态
                Log.i(Log.TAG, "第 2 步：打开 camera1"); // 日志：记录相机状态
                for (int openAttempt = 1; openAttempt <= 3; openAttempt++) { // 循环：遍历数据
                    boolean keepCam0 = cam0 != null && (cam0.isLiving() // 赋值：更新状态
                            || cam0.enableLiveEncode
                            || cam0.isRecording() // 调用：执行下一步
                            || cam0.videoStarting
                            || cam0.mCameraPhotoing); // 状态：当前正在抓拍
                    boolean keepCam1 = cam1 != null && (cam1.isLiving() // 赋值：更新状态
                            || cam1.enableLiveEncode
                            || cam1.isRecording() // 调用：执行下一步
                            || cam1.videoStarting
                            || cam1.mCameraPhotoing); // 状态：当前正在抓拍
                    Log.i(Log.TAG, "Dual camera open attempt = " + openAttempt); // 日志：记录相机状态
                    ret0 = cam0.openSelfOnly(stream, null, timeoutSeconds, waitSelfCheck); // 赋值：更新状态
                    ret1 = cam1.openSelfOnly(stream, null, timeoutSeconds, waitSelfCheck); // 赋值：更新状态
                    if (ret0 && ret1) { // 条件：按运行状态分支
                        break;
                    }
                    Log.i(Log.TAG, "Dual camera open retry, camera0 = " + ret0 // 日志：记录相机状态
                            + ", camera1 = " + ret1 // 赋值：更新状态
                            + ", attempt = " + openAttempt // 赋值：更新状态
                            + ", keepCam0 = " + keepCam0 // 赋值：更新状态
                            + ", keepCam1 = " + keepCam1); // 赋值：更新状态
                    if (ret0 && cam0 != null && !keepCam0) { // 条件：按运行状态分支
                        cam0.closeCamera(); // 调用：执行下一步
                    }
                    if (ret1 && cam1 != null && cam1 != cam0 && !keepCam1) { // 条件：按运行状态分支
                        cam1.closeCamera(); // 调用：执行下一步
                    }
                    SystemClock.sleep(1200); // 阻塞：当前线程睡眠
                }
                Log.i(Log.TAG, "双路 openSelfOnly 结果" // 日志：记录相机状态
                        + "，camera0 = " + ret0 // 赋值：更新状态
                        + "，camera1 = " + ret1); // 赋值：更新状态
                if (!ret0 || !ret1) { // 条件：按运行状态分支
                    Log.i(Log.TAG, "双路 CameraDevice 未全部打开成功，停止后续流程"); // Camera2：相机设备对象
                    boolean keepCam0 = cam0 != null && (cam0.isLiving() // 赋值：更新状态
                            || cam0.enableLiveEncode
                            || cam0.isRecording() // 调用：执行下一步
                            || cam0.videoStarting
                            || cam0.mCameraPhotoing); // 状态：当前正在抓拍
                    boolean keepCam1 = cam1 != null && (cam1.isLiving() // 赋值：更新状态
                            || cam1.enableLiveEncode
                            || cam1.isRecording() // 调用：执行下一步
                            || cam1.videoStarting
                            || cam1.mCameraPhotoing); // 状态：当前正在抓拍
                    if (ret0 && cam0 != null && !keepCam0) { // 条件：按运行状态分支
                        cam0.closeCamera(); // 调用：执行下一步
                    }
                    if (ret1 && cam1 != null && cam1 != cam0 && !keepCam1) { // 条件：按运行状态分支
                        cam1.closeCamera(); // 调用：执行下一步
                    }
                    if (cb != null) { // 条件：按运行状态分支
                        Log.i(Log.TAG, "双路 CameraDevice 未全部打开成功，回调 openFailed，camID = " + camID); // Camera2：相机设备对象
                        cb.openFailed(-1); // 调用：执行下一步
                    }
                    return false; // 返回：结束当前方法
                }
                boolean activeCamera0 = camID % 2 == 0; // 赋值：更新状态
                boolean session0 = true; // 赋值：更新状态
                boolean session1 = true; // 赋值：更新状态
                if (activeCamera0) { // 条件：按运行状态分支
                    Log.i(Log.TAG, "第 3 步：创建当前业务 camera0 session"); // 日志：记录相机状态
                    session0 = cam0.startSessionForDualOpen(stream, video, isRecordVideo); // 赋值：更新状态
                } else {
                    Log.i(Log.TAG, "第 3 步：创建当前业务 camera1 session"); // 日志：记录相机状态
                    session1 = cam1.startSessionForDualOpen(stream, video, isRecordVideo); // 赋值：更新状态
                }
                ///
                Log.i(Log.TAG, "双路 session 和 repeating 启动结果" // 日志：记录相机状态
                        + "，camera0 = " + session0 // 赋值：更新状态
                        + "，camera1 = " + session1); // 赋值：更新状态
                result = session0 && session1; // 赋值：更新状态

                if (!result) { // 条件：按运行状态分支
                    if (cam0 != null && !cam0.isLiving() && !cam0.isRecording() && !cam0.mCameraPhotoing) { // 状态：当前正在抓拍
                        cam0.closeCamera(); // 调用：执行下一步
                    }
                    if (cam1 != null && cam1 != cam0 && !cam1.isLiving() && !cam1.isRecording() && !cam1.mCameraPhotoing) { // 状态：当前正在抓拍
                        cam1.closeCamera(); // 调用：执行下一步
                    }
                }

                if (cb != null) { // 条件：按运行状态分支
                    if (result) { // 条件：按运行状态分支
                        Log.e(Log.TAG,"openSucceed"); // 日志：记录相机状态
                        cb.openSucceed(); // 调用：执行下一步
                    } else {
                        cb.openFailed(-1); // 调用：执行下一步
                    }
                }
                return result; // 返回：结束当前方法
            } finally {
                synchronized (sDualCameraLock) { // 同步：保护双MIPI共享状态
                    sDualStarting = false; // 赋值：更新状态
                    sDualStarted = result; // 赋值：更新状态
                    sDualCameraLock.notifyAll(); // 同步：保护双MIPI共享状态
            }
        }
    }

        if (mCameraDevice != null) { // Camera2：已打开的相机句柄
            if (cb != null) { // 条件：按运行状态分支
                cb.openSucceed(); // 调用：执行下一步
            }
            return true; // 返回：结束当前方法
        }
        ///

        if (isOpening()) { // 条件：按运行状态分支
            if (mCameraDevice != null) { // Camera2：已打开的相机句柄
                Log.i(Log.TAG, "摄像头已经打开"); // 日志：记录相机状态
                if (cb != null) cb.openSucceed(); // 条件：按运行状态分支
                return true; // 返回：结束当前方法
            }

            Log.i(Log.TAG, "摄像头状态异常，CameraDevice 为空，清理残留资源后重新打开，camID = " + camID); // Camera2：相机设备对象
            closePreviewSession();
            closeImageReader();
            closeStillImageReader();
            stopBackgroundThread();
            clearState(DevState.OPENING);
        }

        streamType = stream; // 赋值：更新状态
        openCamera(); // Camera2：异步打开相机
        if (mCameraDevice == null) { // Camera2：已打开的相机句柄
            Log.i(Log.TAG, "打开摄像头失败"); // 日志：记录相机状态
            if (cb != null) cb.openFailed(-1); // 条件：按运行状态分支
            return false; // 返回：结束当前方法
        }

        previewReady = false; // 条件：会话可出帧后才处理Image
        setState(DevState.OPENING);
        if (cb != null) cb.openSucceed(); // 条件：按运行状态分支
        return true; // 返回：结束当前方法
    }

    ///
    public synchronized boolean openSelfOnly(int stream, onOpenCallback cb, int timeoutSeconds, boolean waitSelfCheck) { // 入口：方法定义
//        if (mCameraDevice != null) {
//            Log.i(Log.TAG, "openSelfOnly 直接成功，mCameraDevice 已存在"
//                    + "，this = " + this
//                    + "，camID = " + camID
//                    + "，cameraId = " + mCameraDevice.getId());
//            if (cb != null) {
//                cb.openSucceed();
//            }
//            return true;
//        }
        if (isOpening()) { // 条件：按运行状态分支
            if (mCameraDevice != null) { // Camera2：已打开的相机句柄
                Log.i(Log.TAG, "摄像头已经打开"); // 日志：记录相机状态
                if (cb != null) cb.openSucceed(); // 条件：按运行状态分支
                return true; // 返回：结束当前方法
            }
            Log.i(Log.TAG, "摄像头状态为已打开，但 CameraDevice 为空，重新打开，camID = " + camID); // Camera2：相机设备对象
            clearState(DevState.OPENING);
        }
        if (mMainBoard == 1) { // 条件：按运行状态分支
            MipiSwitch.switchTo(camID); // 调用：执行下一步
        }
        streamType = stream; // 赋值：更新状态
        openCamera(); // Camera2：异步打开相机
        if (mCameraDevice == null) { // Camera2：已打开的相机句柄
            Log.i(Log.TAG, "打开摄像头失败"); // 日志：记录相机状态
            if (cb != null) cb.openFailed(-1); // 条件：按运行状态分支
            return false; // 返回：结束当前方法
        }
        previewReady = false; // 条件：会话可出帧后才处理Image

        Log.e(Log.TAG,"previewReady:"+previewReady);
        setState(DevState.OPENING);
        if (cb != null) cb.openSucceed(); // 条件：按运行状态分支
        return true; // 返回：结束当前方法
    }
    private boolean startSessionForDualOpen(int stream, boolean video, boolean isRecordVideo) { // 入口：方法定义

//        boolean useVideoSession = video || camID % 2 == 0;
        boolean useVideoSession = video ; // 赋值：更新状态

        Settings.VideoCodec vc = getVideoCodec(stream); // 赋值：更新状态
        Point resolution = useVideoSession && vc != null ? Settings.VideoCodec.getResolution(vc.resolution) : null; // 赋值：更新状态
        if (resolution == null) { // 条件：按运行状态分支
            resolution = useVideoSession ? new Point(1536, 864) : getConfiguredPhotoResolution(); // 赋值：更新状态
        }
        if (resolution == null) { // 条件：按运行状态分支
            resolution = new Point(1920, 1080); // 赋值：更新状态
        }
        if (is6735) { // 条件：按运行状态分支
            resolution = new Point(1280, 720); // 赋值：更新状态
        } else if (useVideoSession && (resolution.x > 1536 || resolution.y > 864)) {
            resolution = new Point(1536, 864); // 赋值：更新状态
        }
        mResolution = resolution; // 赋值：更新状态
        return startSessionAndRepeatingIfNeeded(    // 创建 Session 和启动预览；返回：结束当前方法
                resolution.x,
                resolution.y,
                useVideoSession,
                isRecordVideo,
                stream
        );
    }

    private synchronized boolean startSessionAndRepeatingIfNeeded(int width, int height, boolean video, boolean isReordVideo, int stream) { ///；成员：保存运行状态
        if (mDualSessionStarted) { // 条件：按运行状态分支
            return true; // 返回：结束当前方法
        }
        if (mDualSessionStarting) { // 条件：按运行状态分支
            return true; // 返回：结束当前方法
        }
        if (mCameraDevice == null) { // Camera2：已打开的相机句柄
            return false; // 返回：结束当前方法
        }
        mDualSessionStarting = true; // 赋值：更新状态
        try { // 异常：保护相机/IO调用
            if (mPreviewSession != null && video && !mPreviewSessionVideoMode) { // Camera2：向HAL提交请求的会话
                closePreviewSession();
            }
            if (mPreviewSession == null) { // Camera2：向HAL提交请求的会话
                createPreviewSession(width, height, video);
            }
            if (mPreviewSession == null) { // Camera2：向HAL提交请求的会话
                return false; // 返回：结束当前方法
            }
            previewReady = true;   // 这句决定图像回调能否开始处理帧；条件：会话可出帧后才处理Image
            ///
            if (video) { // 条件：按运行状态分支
                if (isReordVideo) { // 条件：按运行状态分支
                    Settings.VideoCodec vc = getVideoCodec(stream); // 赋值：更新状态
                    mResolution = Settings.VideoCodec.getResolution(vc.resolution); // 赋值：更新状态
                    lockFocus(
                            10000,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO, // Camera2：发送给HAL的请求
                            true,
                            vc
                    );
                } else {
                    lockFocus(
                            10000,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO, // Camera2：发送给HAL的请求
                            false,
                            null
                    );
                }
            } else {
                lockFocus(
                        10000,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE, // Camera2：发送给HAL的请求
                        false,
                        null
                );
            }
            ///
            mDualSessionStarted = true; // 赋值：更新状态
            return true; // 返回：结束当前方法
        } catch (Exception e) {
            Log.i(Log.TAG, "dual camera session exception: " + e.getMessage()); // 日志：记录相机状态
            closePreviewSession();
            closeImageReader();
            closeStillImageReader();
            if (!isLiving() && !isRecording() && !mCameraPhotoing) { // 状态：当前正在抓拍
                closeCamera();
            }
            return false; // 返回：结束当前方法
        } finally {
            mDualSessionStarting = false; // 赋值：更新状态
        }
    }
    ///

    public boolean liveStart(int stream, int ssrc) { // 入口：方法定义
        if (false && (isRecording() || videoStarting)) { // 条件：按运行状态分支
            Log.i(Log.TAG, "拉流失败，正在录制视频"); // 日志：记录相机状态
            return false; // 返回：结束当前方法
        }
        if (isLiving() || liveStarting) { // 条件：按运行状态分支
            Log.i(Log.TAG, "拉流失败，正在播放视频"); // 日志：记录相机状态
            return false; // 返回：结束当前方法
        }

        this.streamType = stream; // 赋值：更新状态
        liveStarting = true; // 赋值：更新状态

        scheduledHandler.post(() -> { // 调用：执行下一步
            ///
            try { // 异常：保护相机/IO调用
                if (!liveStarting) { // 条件：按运行状态分支
                    return; // 返回：结束当前方法
                }
                Settings.VideoCodec vc = getVideoCodec(stream); // 赋值：更新状态
                mResolution = Settings.VideoCodec.getResolution(vc.resolution); // 赋值：更新状态
                ///
                if (mResolution == null) { // 条件：按运行状态分支
                    Log.i(Log.TAG, "直播分辨率为空，使用默认 1536x864" ///；日志：记录相机状态
                            + "，camID = " + camID); // 赋值：更新状态
                    mResolution = new Point(1536, 864); ///；赋值：更新状态
                }
                ///

                /////
//                Point size = Settings.VideoCodec.getResolution(codec.get(String.valueOf(0)).resolution);         // 默认使用的是主码流

                ///
                // 由于分辨率大于1536x864无法拉流，因此设置最大的分辨率为1536x864
                if (mResolution.x > 1536 || mResolution.y > 864) { ///；条件：按运行状态分支
                    mResolution = new Point(1536, 864); ///；赋值：更新状态
                }

                if ((mResolution.x == 800 && mResolution.y == 600) || (mResolution.x == 704 && mResolution.y == 576)) { // 条件：按运行状态分支
                    mResolution = new Point(640, 480); // 赋值：更新状态
                }

//                mResolution = size;
                /////

                Log.i(Log.TAG, "视频设置的宽高===>：" + mResolution.x + "x" + mResolution.y); // 日志：记录相机状态

//                createPreviewSession(mResolution.x, mResolution.y, true); ///
//                if (mPreviewSession == null) {
//                    Log.i(Log.TAG, "创建会话失败");
//                    return;
//                }
                if (!ensureVideoPreviewSession(vc, false)) { // 条件：按运行状态分支
                    Log.i(Log.TAG, "拉流失败，无法切换到视频会话" // 日志：记录相机状态
                            + "，camID = " + camID // 赋值：更新状态
                            + "，mPreviewSessionVideoMode = " + mPreviewSessionVideoMode); // 赋值：更新状态

                    liveStarting = false; // 赋值：更新状态
                    setEnableLiveEncode(false);

                    if (!isRecording() && !mCameraPhotoing) { // 状态：当前正在抓拍
                        closePreviewSession();
                        closeImageReader();
                        closeStillImageReader();
                        closeBothCameraIfNoLive();
                    }
                    return; // 返回：结束当前方法
                }
                // 先进入直播状态再刷新 repeating，避免首批视频帧被普通预览请求吞掉。
                mOnShow = true; // 赋值：更新状态
                previewReady = true; // 条件：会话可出帧后才处理Image
                setState(DevState.LIVING);
                mState = STATE_VIDEO_LIVING; // 状态机：AF/AE/抓拍/视频流转
                { // 直播要打包成rtp包进行发包
                    rtph264 = new RTPH264(ssrc); // 赋值：更新状态
                    mipiLivePpsSps = null; // 赋值：更新状态
                    mipiLiveFirstFrameTimestamp = 0; // 赋值：更新状态
                    setEnableLiveEncode(true); ///
                    ensureMipiVideoEncoder(stream, true); /////
                    refreshLowNoiseRepeatingRequest(vc, true);
                    liveStarting = false; // 赋值：更新状态
                }
//                lockFocus(10000, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,false,null); ///

                Log.i(Log.TAG, "拉流成功， SSRC:" + ssrc); // 日志：记录相机状态
            } catch (Exception e) {
                liveStarting = false; // 赋值：更新状态
                setEnableLiveEncode(false);
            }
            ///
        });

        return true; // 返回：结束当前方法
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
        if (sizes == null || sizes.length == 0 || width <= 0 || height <= 0) // 条件：按运行状态分支
            return new Point(width, height); // 返回：结束当前方法

        //指定列表中第一组数据为查找的初始数据
        Size found = sizes[0]; // 赋值：更新状态
        final int specifiedArea = width * height; // 成员：保存运行状态
        //定义尺寸的最小匹配值
        int minMatch = Math.abs(specifiedArea - found.getWidth() * found.getHeight()); // 赋值：更新状态

        for (int i = 1; i < sizes.length; i++) { // 循环：遍历数据
            //for (Camera.Size supportSize : newList) {
            int supportedArea = sizes[i].getWidth() * sizes[i].getHeight(); // 赋值：更新状态
            //指定图片尺寸与支持列表中的尺寸完全匹配，不再进行查找
            if (supportedArea == specifiedArea) { // 条件：按运行状态分支
                return new Point(sizes[i].getWidth(), sizes[i].getHeight()); // 返回：结束当前方法
            }
            //指定图片尺寸大于相机支持的图片尺寸，查找相机支持的最大尺寸
            if ((supportedArea < specifiedArea) && (specifiedArea - supportedArea < minMatch)) { // 条件：按运行状态分支
                found = sizes[i]; // 赋值：更新状态
                minMatch = specifiedArea - supportedArea; // 赋值：更新状态
            }
            //指定图片尺寸小于相机支持的图片尺寸，查找相机支持的最小尺寸
            else if ((supportedArea > specifiedArea) && (supportedArea - specifiedArea < minMatch)) { // 条件：按运行状态分支
                found = sizes[i]; // 赋值：更新状态
                minMatch = supportedArea - specifiedArea; // 赋值：更新状态
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

            if (mKeyAisRequestMode != null) { // 条件：按运行状态分支
                mPreviewRequestBuilder.set(mKeyAisRequestMode, new int[]{2}); // Camera2：构建下一次请求参数
                // 需要等待多帧降噪完成才能抓拍
                if (mCameraPhotoing) { // 状态：当前正在抓拍
                    SystemClock.sleep(5000); // 阻塞：当前线程睡眠
                }
            }

            mPreviewSession.setRepeatingRequest( // Camera2：持续提交预览请求
                    mPreviewRequestBuilder.build(), // Camera2：生成不可变请求
                    mCaptureCallback, // 回调：推进AF/AE状态机
                    mBackgroundHandler // 线程：承接Camera2回调
            );

        } catch (Exception e) {
            Log.i(Log.TAG, "MFB 预触发异常：" + e.getMessage()); // 日志：记录相机状态
        }
    }
    ///

    @Override
    public boolean takePhoto(
            int stream,
            int preset,
            boolean show,
            String filename,
            Bitmap pop,
            int recordPreset,
            HashMap<String, Settings.AIParameter> aps,
            boolean alert) {

        aiParameters = aps;

        synchronized (sDualCameraLock) {
            sDualPhotoTaskCount++;
        }

        scheduledHandler.post(() -> {
            synchronized (sDualPhotoTaskLock) {

                boolean notifyPhotoFailed = false;
                boolean finalSuccess = false;
                final int maxRetryCount = 1;

                try {
                    mOnShow = show;

                    mCameraPhotoing = true;
                    takePhotoOnce.set(false);
                    photoDone.set(false);
                    photoSaveSuccess.set(false);
                    photoFailReason = "";

                    mFileImage = filename;
                    mFilePreset = preset;

                    Point photoResolution = getConfiguredPhotoResolution();

                    if (photoResolution == null) {
                        Log.i(Log.TAG,
                                "拍照分辨率为空，使用默认 1920x1080"
                                        + "，camID = " + camID);

                        photoResolution = new Point(1920, 1080);
                    }

                    boolean createdPhotoSession = false;

                    Log.e(Log.TAG,
                            "takePhoto 开始"
                                    + "，mPreviewSession == null " + (mPreviewSession == null)
                                    + "，camID = " + camID
                                    + "，photoResolution = " + photoResolution.x + "x" + photoResolution.y
                                    + "，isLiving = " + isLiving()
                                    + "，isRecording = " + isRecording());

                    if (mPreviewSession == null) {
                        mResolution = photoResolution;

                        createPreviewSession(
                                photoResolution.x,
                                photoResolution.y,
                                false
                        );

                        createdPhotoSession = mPreviewSession != null;
                    }

                    if (mPreviewSession == null) {
                        Log.i(Log.TAG,
                                "创建预览会话失败"
                                        + "，camID = " + camID);

                        notifyPhotoFinished(false, "创建预览会话失败");

                        notifyPhotoFailed = true;
                        return;
                    }

                    if (createdPhotoSession) {
                        lockFocus(
                                10000,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                                false,
                                null
                        );
                    }

                    for (int attempt = 0; attempt <= maxRetryCount; attempt++) {

                        Log.i(Log.TAG,
                                "开始执行抓拍"
                                        + "，attempt = " + attempt
                                        + "，camID = " + camID
                                        + "，file = " + mFileImage);

                        photoDone.set(false);
                        photoSaveSuccess.set(false);
                        photoFailReason = "";
                        takePhotoOnce.set(false);

                        if (!isLiving() && !isRecording() && !enableLiveEncode) {
                            try {
                                Log.i(Log.TAG,
                                        "非拉流单次拍照，抓拍前停止 repeating"
                                                + "，attempt = " + attempt
                                                + "，camID = " + camID);

                                if (mPreviewSession != null) {
                                    mPreviewSession.stopRepeating();
                                    mPreviewSession.abortCaptures();
                                }

                            } catch (Exception e) {
                                Log.i(Log.TAG,
                                        "非拉流单次拍照，停止 repeating 异常："
                                                + e.getMessage()
                                                + "，attempt = " + attempt
                                                + "，camID = " + camID);
                            }

                            drainImageReader(mImageReader);
                        }

                        takePhotoOnce.set(true);

                        captureStillPicture();

                        int timeoutSeconds = 40;
                        int timeoutMs = timeoutSeconds * 1000;
                        long start = System.currentTimeMillis();

                        while (!photoDone.get()) {
                            long remain = timeoutMs - (System.currentTimeMillis() - start);

                            if (remain <= 0) {
                                break;
                            }

                            mCameraPhtotingLock.waitLock((int) remain);
                        }

                        if (!photoDone.get()) {
                            photoSaveSuccess.set(false);
                            photoFailReason = "抓拍超时 " + timeoutSeconds + " 秒";

                            photoDone.set(true);
                            takePhotoOnce.set(false);

                            Log.i(Log.TAG,
                                    "抓拍超时"
                                            + timeoutSeconds
                                            + "秒"
                                            + "，attempt = " + attempt
                                            + "，camID = " + camID);
                        }

                        if (!photoSaveSuccess.get()
                                && (photoFailReason == null || photoFailReason.length() == 0)) {

                            photoFailReason = "拍照未保存成功，未知原因";
                        }

                        if (photoSaveSuccess.get()) {
                            finalSuccess = true;

                            Log.i(Log.TAG,
                                    "抓拍成功"
                                            + "，attempt = " + attempt
                                            + "，camID = " + camID
                                            + "，file = " + mFileImage);

                            break;
                        }

                        Log.i(Log.TAG,
                                "本次抓拍失败"
                                        + "，attempt = " + attempt
                                        + "，reason = " + photoFailReason
                                        + "，camID = " + camID
                                        + "，file = " + mFileImage);

                        takePhotoOnce.set(false);

                        if (attempt < maxRetryCount) {
                            Log.i(Log.TAG,
                                    "准备补拍"
                                            + "，nextAttempt = " + (attempt + 1)
                                            + "，camID = " + camID);

                            SystemClock.sleep(800);

                            lockFocus(
                                    3000,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                                    false,
                                    null
                            );
                        }
                    }

                    if (!finalSuccess) {
                        notifyPhotoFailed = true;

                        Log.i(Log.TAG,
                                "抓拍最终失败"
                                        + "，reason = " + photoFailReason
                                        + "，camID = " + camID
                                        + "，file = " + mFileImage);
                    }

                } catch (Exception e) {
                    Log.e(Log.TAG,
                            "拍照过程中发生异常"
                                    + "，camID = " + camID
                                    + "，error = " + e);

                    notifyPhotoFinished(false, "拍照异常：" + e.getMessage());

                    notifyPhotoFailed = true;

                } finally {
                    mCameraPhotoing = false;
                    takePhotoOnce.set(false);

                    synchronized (sDualCameraLock) {
                        if (sDualPhotoTaskCount > 0) {
                            sDualPhotoTaskCount--;
                        }
                    }

                    closeBothCameraIfNoLive();

                    if (notifyPhotoFailed && controllerCallback != null) {
                        Log.i(Log.TAG,
                                "拍照失败，已释放双路 Camera，准备通知补拍"
                                        + "，camID = " + camID
                                        + "，reason = " + photoFailReason);

                        controllerCallback.onPhotoFailed(
                                id,
                                preset,
                                filename
                        );
                    }
                }
            }
        });

        return true;
    }


    /**
     * 通知 takePhoto() 当前拍照流程已经结束。
     *
     * @param success 本次拍照是否成功
     * @param reason  失败原因，成功时可以传 null
     */
    private void notifyPhotoFinished(boolean success, String reason) {
        photoSaveSuccess.set(success);
        photoFailReason = reason == null ? "" : reason;

        photoDone.set(true);
        mCameraPhtotingLock.notifyLock();

        Log.i(Log.TAG,
                "通知拍照完成"
                        + "，success = " + success
                        + "，reason = " + photoFailReason
                        + "，camID = " + camID
                        + "，file = " + mFileImage);
    }




    private void drainImageReader(ImageReader reader) {
        if (reader == null) {
            return;
        }

        Image img = null;

        try {
            while ((img = reader.acquireLatestImage()) != null) {
                Log.i(Log.TAG,
                        "清理 ImageReader 残留帧"
                                + "，camID = " + camID
                                + "，format = " + img.getFormat()
                                + "，size = " + img.getWidth() + "x" + img.getHeight());

                img.close();
                img = null;
            }
        } catch (Exception e) {
            Log.i(Log.TAG,
                    "清理 ImageReader 残留帧异常："
                            + e.getMessage()
                            + "，camID = " + camID);
        } finally {
            if (img != null) {
                try {
                    img.close();
                } catch (Exception ignored) {
                }
            }
        }
    }


    @Override
    public boolean takeVideo(final String filename, final int duration, int stream, boolean upload) { // 入口：方法定义
        if (isRecording() || videoStarting) return false; // 条件：按运行状态分支
        // 录像优先级高于直播拉流
        if (false && isLiving()) liveStop(); // 条件：按运行状态分支

        videoStarting = true; // 赋值：更新状态
        scheduledHandler.post(() -> { // 调用：执行下一步
            videoStart(stream, filename, duration, upload);
        });
        return true; // 返回：结束当前方法
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
}
