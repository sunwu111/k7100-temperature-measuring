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
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.blankj.utilcode.util.ImageUtils;
import com.zhjinrui.bean.CommonResponseEntity;
import com.zhjinrui.bean.Constant;
import com.zhjinrui.netty.NettyTcpServer;
import com.zhjinrui.netty.NettyUtils;

import java.io.File;
import java.io.FileOutputStream;
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

public class Camera2Device extends Device {
    private final int camID;
    private final Context mContext;
    private final int mMainBoard;  // 0: 旧xy6762板 1： 新xy6762板
    private boolean mCameraPhotoing = false;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private CameraDevice mCameraDevice;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mPreviewSession;
    private final CameraMulthreadLock mCameraOpenCloseLock = new CameraMulthreadLock();
    private final CameraMulthreadLock mCameraPhtotingLock = new CameraMulthreadLock();
    private final CameraMulthreadLock mCameraFocusLock = new CameraMulthreadLock();
    private String mFileImage;
    private int mFilePreset;
    private Point mResolution;
    private long mLockFocusTime = 0;  // 记录AF和AE开始时间，超时强制退出
    private final static HandlerThread scheduledThread = new HandlerThread("摄像机拍照线程");
    private static Handler scheduledHandler;
    /////
    private static final String AIS_AVAILABLE_MODES_KEY_NAME = "com.mediatek.mfnrfeature.availablemfbmodes";
    private static final String AIS_REQUEST_MODE_KEY_NAME = "com.mediatek.mfnrfeature.mfbmode";
    private static final String AIS_RESULT_MODE_KEY_NAME = "com.mediatek.mfnrfeature.mfbresult";
    private CameraCharacteristics.Key<int[]> mKeyAisAvailableModes;
    private CaptureResult.Key<int[]> mKeyAisResult;
    private CaptureRequest.Key<int[]> mKeyAisRequestMode;
    private final static HandlerThread mCameraParamThread = new HandlerThread("摄像机异步更新参数线程");
    private static Handler mCameraParamHandler;
    private float minFocusDist;
    private int rotate;
    private boolean useAudio;
    /////


    /// sunwu
    private final AtomicBoolean takePhotoOnce = new AtomicBoolean(false);   // 防止在拉流的时候拍照会被执行多次
    private final AtomicBoolean photoDone = new AtomicBoolean(false);       // 拍照已经成功，但等待方不知道  如果没有这个变量，在一次拍照成功后，设备还在等待拍照任务，会导致拍照失败再次拍照，其实已经成功。



    public Camera2Device(int ID, Context context, int camID, int board, int rotate, boolean useAudio) { /////
        super(ID, context, useAudio); /////
        this.camID = camID;
        this.mContext = context;
        this.mMainBoard = board;
        this.drawOSD = true;
        this.rotate = rotate; /////
        this.useAudio = useAudio; /////

        if (!scheduledThread.isAlive()) {
            scheduledThread.start();
            scheduledHandler = new Handler(scheduledThread.getLooper());
        }
        if (!mCameraParamThread.isAlive()) { /////
            mCameraParamThread.start(); /////
            mCameraParamHandler = new Handler(mCameraParamThread.getLooper()); /////
        } /////
    }

    private class CameraMulthreadLock {
        private class Locker {
            private Boolean unlocked = false;
        }

        private Locker locker = new Locker();

        public boolean waitLock(int milisecond) {
            try {
                synchronized (locker) {
                    locker.unlocked = false;
                    locker.wait(milisecond);
                }
            } catch (Exception e) {
                Log.i(Log.TAG, "加锁异常：" + e.getMessage());
            }
            return locker.unlocked;
        }

        public void notifyLock() {
            try {
                synchronized (locker) {
                    locker.unlocked = true;
                    locker.notify();
                }
            } catch (Exception e) {
                Log.i(Log.TAG, "解锁异常：" + e.getMessage());
            }
        }
    }

    @Override
    public void setSceneName(int presetNo, String name) {
    }

    @Override
    protected void doSampleData(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo, int outIndex) { /////
//        if (isRecording() && !pausing && mediaMuxer != null)
//            mediaMuxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
        /////
        try {
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
            Log.i(Log.TAG, "MIPI摄像头录制视频文件异常：" + e);
        }
        /////
    }

    private Bitmap imageDecode(Image image) {
        switch (image.getFormat()) {
            case ImageFormat.JPEG:
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null).copy(Bitmap.Config.ARGB_8888, true);
            case ImageFormat.YUV_420_888:
                //bitmap = YUV_420_888_toRGB(image, image.getWidth(), image.getHeight());
                return YUV_420_888_toRGB(image);
        }
        return null;
    }

    /////
    // 图像参数调节算法
    private Bitmap preProcessingPhoto(Bitmap previewBitmap) {
        try {
            // 修改分辨率
            if (mCameraPhotoing) {
                mResolution = Settings.PhotoConfig.getImageSize(photoConfig.size);
            } else if ((isLiving() && rtph264 != null) || isRecording()) { /////

//                Point size = Settings.VideoCodec.getResolution(codec.get(String.valueOf(0)).resolution);
                Point size = Settings.VideoCodec.getResolution(codec.get(String.valueOf(streamType)).resolution);


                // 由于分辨率大于1920x1080无法拉流，因此设置最大的分辨率为1920x1080
                if (size.x > 1920 || size.y > 1080) {
                    size = new Point(1920, 1080);
                }
                mResolution = size;
//                Log.e(Log.TAG,"preProcessingPhoto分辨率为：" + mResolution.x + ":" + mResolution.y);

            }
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(previewBitmap, mResolution.x, mResolution.y, true);
//            Log.i(Log.TAG, "摄像头设置分辨率为" + mResolution.x + "x" + mResolution.y);

            Bitmap outputBitmap = Bitmap.createBitmap(scaledBitmap.getWidth(), scaledBitmap.getHeight(), Bitmap.Config.ARGB_8888); /////
            Canvas canvas = new Canvas(outputBitmap);
            Paint paint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
//            // 是否灰度化
//            if (photoConfig.color == 0) {
//                ColorMatrix grayscaleMatrix = new ColorMatrix(new float[]{
//                        0.299f, 0.587f, 0.114f, 0, 0,
//                        0.299f, 0.587f, 0.114f, 0, 0,
//                        0.299f, 0.587f, 0.114f, 0, 0,
//                        0, 0, 0, 1, 0
//                });
//                colorMatrix.postConcat(grayscaleMatrix);
//                //Log.i(Log.TAG, "摄像头色彩设置为黑白模式");
//            } else {
//                //Log.i(Log.TAG, "摄像头色彩设置为彩色模式");
//            }

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
            return outputBitmap;
        } catch (Exception e) {
            Log.i(Log.TAG, "摄像头设置图像参数异常：" + e);
            return previewBitmap;
        }
    }

    private void updateCaptureRequestParameters() {
        try {
            // 降噪模式
            if (cameraConfig.denoiseMode == 0) {
                //Log.i(Log.TAG, "MIPI摄像头关闭降噪");
            } else if (cameraConfig.denoiseMode == 1) {
                //Log.i(Log.TAG, "MIPI摄像头开启2D降噪");
            } else if (cameraConfig.denoiseMode == 2) {
                //Log.i(Log.TAG, "MIPI摄像头开启3D降噪");
            }
            if (cameraConfig.denoiseMode <= 2) {
                mPreviewRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, (int) cameraConfig.denoiseMode);
            }
            // 增益控制
            if (cameraConfig.gainControl == 0) {
                //Log.i(Log.TAG, "MIPI摄像头手动增益");
            } else {
                //Log.i(Log.TAG, "MIPI摄像头自动增益");
            }
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, (int) cameraConfig.gainControl);
            // 背光补偿
            if (cameraConfig.backLightCom == 1) {
                //Log.i(Log.TAG, "MIPI摄像头开启背光补偿");
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 4);  // 亮度补偿值
                // 强光抑制
            } else if (cameraConfig.strongLightSup == 1) {
                //Log.i(Log.TAG, "MIPI摄像头开启强光抑制");
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, -4);  // 亮度补偿值
            } else {
                //Log.i(Log.TAG, "MIPI摄像头关闭背光补偿与强光抑制");
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);  // 亮度补偿值
            }
            // 聚焦模式
            if (cameraConfig.focusMode == 0) {
                //Log.i(Log.TAG, "MIPI摄像头半自动对焦");
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            } else if (cameraConfig.focusMode == 1) {
                //Log.i(Log.TAG, "MIPI摄像头全自动对焦");
                if (mCameraPhotoing) {
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                } else {
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                }
            } else {
                //Log.i(Log.TAG, "MIPI摄像头手动对焦");
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            }
            // 是否灰度化
            if (photoConfig.color == 0) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_MONO);  // 黑白色彩
            }
            // 更新请求
            mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (Exception e) {
            Log.e(Log.TAG, "更新CaptureRequest参数失败: " + e.getMessage());
        }
    }

    private static Bitmap rotate180WithCanvas(Bitmap src) {
        Bitmap dst = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig() != null ? src.getConfig() : Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(dst);
        c.save();
        c.rotate(180f, src.getWidth() / 2f, src.getHeight() / 2f);
        c.drawBitmap(src, 0f, 0f, null);  // 关键：把原图画到旋转后的画布上
        c.restore();
        return dst;
    }


    // 直播和拍照回调函数  拉流的时候可以拍照，需要结合takephoto函数
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image img = reader.acquireLatestImage();
            try {
                if (img == null || !previewReady) return;

                mCameraParamHandler.post(() -> updateCaptureRequestParameters());

                Bitmap previewBitmap = imageDecode(img);

                if (rotate == 1) {
                    previewBitmap = rotate180WithCanvas(previewBitmap);
                }
                previewBitmap = preProcessingPhoto(previewBitmap);

                if (mCameraPhotoing && takePhotoOnce.compareAndSet(true, false)) {
                    Log.i(Log.TAG, "抓拍图片分辨率：" + previewBitmap.getWidth() + "x" + previewBitmap.getHeight());

                    photoDone.set(true);

                    mCameraPhtotingLock.notifyLock();
                    previewBitmap = processPhoto(previewBitmap, System.currentTimeMillis(), 255, aiParameters, true);
                    //drawMetrics(previewBitmap);  // 绘制信噪比、宽动态、清晰度OSD /////
                    drawWatermark(previewBitmap,3,streamType,true); // 先AI识别再画OSD //////

                    Utils.saveBitmapAsJPEG(previewBitmap, mFileImage, 100);
                    if (NettyUtils.isTakePhoto()) {
                        toolTakePhoto(previewBitmap);
                        NettyUtils.setTakePhoto(false);
                    }
                    if (controllerCallback != null) {
                        procVideoHandler.post(() -> controllerCallback.onPhotoTaked(getTimestampFromFilename(mFileImage), id, mFilePreset, mFileImage));
                    }
                    mCameraPhotoing = false;    /////// sunwu ，设置为false是为了防止在拉流的过程中一直拍照，在拉流的过程中只需要一张照片

                } else if ((isLiving() && rtph264 != null) || isRecording()) { /////
                    //detectObject(previewBitmap);// 视频AI跟踪，会影响帧率，暂时注释掉
                    //drawMetrics(previewBitmap);  // 绘制信噪比、宽动态、清晰度OSD /////

                    drawWatermark(previewBitmap,3,streamType,false); // 先AI识别再画OSD //////

                    Bitmap finalPreviewBitmap = previewBitmap; // 这里可以解决OSD闪烁的问题
                    procVideoHandler.removeCallbacksAndMessages(null); /////
                    procVideoHandler.post(() -> { /////
                        encode(finalPreviewBitmap); /////
                    });
                }
                if (mOnShow && controllerCallback != null) {
                    controllerCallback.onFrame(previewBitmap); /////
                }
            } catch (Exception e) {
                Log.i(Log.TAG, "图片处理异常：" + e.getMessage());
            } finally {
                if (img != null) img.close();
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


    private void toolTakePhoto(Bitmap previewBitmap) {
        try {
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


    private void captureContinuousPictures() {
        try {
            // 锁定AE调节，否则录像或视频画面在特定光线条件下会不停闪烁
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (Exception e) {
            Log.i(Log.TAG, "视频预览异常：" + e.getMessage());
        }
    }

    private void captureStillPicture() {
        try {
            //Log.i(Log.TAG, "开始拍摄照片");
            // 锁定自动曝光到调整后的最新参数
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    Log.i(Log.TAG, "拍摄照片成功");
                    unlockFocus();
                }
            };
            mPreviewSession.stopRepeating();
            mPreviewSession.abortCaptures();
            mPreviewSession.capture(mPreviewRequestBuilder.build(), CaptureCallback, mBackgroundHandler);
        } catch (Exception e) {
            Log.i(Log.TAG, "拍摄照片异常：" + e.getMessage());
        }
    }

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_AF_LOCK = 1;
    private static final int STATE_WAITING_AE_LOCKING = 3;
    private static final int STATE_PICTURE_TAKING = 4;
    private static final int STATE_VIDEO_RECORDING = 5;
    private static final int STATE_VIDEO_LIVING = 6;
    private int mState = STATE_PREVIEW;
    private static boolean previewReady;

    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW:
                    break;
                case STATE_WAITING_AF_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (System.currentTimeMillis() - mLockFocusTime > 3 * 1000) {
                        Log.i(Log.TAG, "对焦3秒超时，开始自动曝光");
                        runPrecaptureSequence();
                    } else if (afState == null) { // 会有返回空的情况

                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        Log.i(Log.TAG, String.format("对焦%s，焦点锁定", CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ? "成功" : "失败"));
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
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (System.currentTimeMillis() - mLockFocusTime > 3 * 1000) {
                        Log.i(Log.TAG, "曝光结束，自动曝光3秒超时");
                        mState = STATE_PREVIEW;
                        mCameraFocusLock.notifyLock();
                    } else if (aeState == null) {

                    } else if (aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        Log.i(Log.TAG, "曝光结束，需要补光");
                        mState = STATE_PREVIEW;
                        mCameraFocusLock.notifyLock();
                    } else if (aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                            aeState == CaptureResult.CONTROL_AE_STATE_LOCKED) {
                        Log.i(Log.TAG, "曝光结束，自动曝光很好");
                        mState = STATE_PREVIEW;
                        mCameraFocusLock.notifyLock();
                    }
                    break;
                }
                case STATE_PICTURE_TAKING:
                    captureStillPicture();
                    /////
                    if (mKeyAisResult != null) {
                        int[] resultModes = result.get(mKeyAisResult);
                        if (resultModes != null) {
                            for (int resMode : resultModes) {
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
                    /////
                    if (mKeyAisResult != null) {
                        int[] resultModes = result.get(mKeyAisResult);
                        if (resultModes != null) {
                            for (int resMode : resultModes) {
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
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }
    };

    private void runPrecaptureSequence() {
        try {
            //Range<Integer> defaultFps = mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE);
            //Log.i(Log.TAG, "开始自动曝光,默认：" + defaultFps);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            mState = STATE_WAITING_AE_LOCKING;
            mLockFocusTime = System.currentTimeMillis();
            /////
            // 要先AF和AE，再进行多帧降噪，不然会AE失败！
            if (mKeyAisRequestMode != null) {
                mPreviewRequestBuilder.set(mKeyAisRequestMode, new int[]{2});
                // 需要等待多帧降噪完成才能抓拍
                if (mCameraPhotoing) {
                    SystemClock.sleep(5000);
                }
            }
            /////
            mPreviewSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (Exception e) {
            Log.i(Log.TAG, "自动曝光异常：" + e.getMessage());
        }
    }

    private void lockFocus(int timeoutMilsec, int captureMode, boolean isRecordVideo, Settings.VideoCodec vc) { /////
        try {
            //Log.i(Log.TAG, "开始自动对焦");
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mImageReader.getSurface());

            if (isRecordVideo){
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(vc.frame, vc.frame));   // 摄像头帧率  摄像头最大帧率为60fps，程序的处理速度<=10fps，可以优化程序的处理速度。

            }else {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(10, 10));   // 摄像头帧率  摄像头最大帧率为60fps，程序的处理速度<=10fps，可以优化程序的处理速度。
            }

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, captureMode); /////
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            mState = STATE_WAITING_AF_LOCK;
            mLockFocusTime = System.currentTimeMillis();
            mPreviewSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            mCameraFocusLock.waitLock(timeoutMilsec);
        } catch (Exception e) {
            Log.i(Log.TAG, "对焦异常：" + e.getMessage());
        }
    }

    private void unlockFocus() {
        try {
            if (mPreviewRequestBuilder != null) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "取消对焦异常：" + e.getMessage());
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        try {
            if (mBackgroundThread != null) {
                mBackgroundThread.quitSafely();
                mBackgroundThread.join();
                mBackgroundThread = null;
                mBackgroundHandler = null;
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "摄像头线程退出异常：" + e.getMessage());
        }
    }


    private void createPreviewSession(int width, int height, boolean video) {
        try {
            if (width <= 0 || height <= 0) {
                Log.i(Log.TAG, "创建摄像头会话失败，无效的视频分辨率[" + width + ":" + height + "]");
                return;
            }
            mImageReader = ImageReader.newInstance(width, height, video ? ImageFormat.YUV_420_888 : ImageFormat.JPEG, 3);  // 3
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
            mCameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (mCameraDevice == null) return;
                    mPreviewSession = cameraCaptureSession;
                    mCameraOpenCloseLock.notifyLock();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                }
            }, mBackgroundHandler);
            mCameraOpenCloseLock.waitLock(2500);
        } catch (Exception e) {
            Log.i(Log.TAG, "创建摄像头会话异常：" + e.getMessage());
        }
    }


    CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            mCameraOpenCloseLock.notifyLock();
            //Log.i(Log.TAG, "打开摄像头" + camID +"成功");
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = null;
            mCameraOpenCloseLock.notifyLock();
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraDevice = null;
            mCameraOpenCloseLock.notifyLock();
            cameraDevice.close();
            Log.i(Log.TAG, "打开摄像头失败，error=" + error);
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
    private StreamConfigurationMap streamConfigurationMap;

    protected void openCamera() {
        try {
            if (ContextCompat.checkSelfPermission(this.mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.i(Log.TAG, "摄像头打开没有权限");
                return;
            }
            CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            int camId = this.camID % 2;
            if (camId >= cameraManager.getCameraIdList().length) {
                Log.i(Log.TAG, "摄像头" + camId + "超过支持的摄像头总数：" + cameraManager.getCameraIdList().length);
                return;
            }
            for (String camerID : cameraManager.getCameraIdList()) {
                if (!camerID.equals(Integer.toString(camId))) continue;

                //Settings.VideoCodec vc = getVideoCodec(streamType);
                //mResolution = Settings.VideoCodec.getResolution(vc.resolution);

                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(camerID);
                /////
                // 获取设备支持的CameraCharacteristics Key列表
                minFocusDist = cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
                Log.i(Log.TAG, "MIPI摄像头最小对焦距离为" + 1 / minFocusDist * 100 + "厘米");
                List<CameraCharacteristics.Key<?>> keyList = cameraCharacteristics.getKeys();
                for (CameraCharacteristics.Key<?> key : keyList) {
                    if (key.getName().equals(AIS_AVAILABLE_MODES_KEY_NAME)) {
                        mKeyAisAvailableModes = (CameraCharacteristics.Key<int[]>) key;
                        Log.i(Log.TAG, "Found CameraCharacteristics Key: " + AIS_AVAILABLE_MODES_KEY_NAME);
                    }
                }
                // 获取CaptureResult Key（用于读取MFB处理结果）
                List<CaptureResult.Key<?>> resultKeyList = cameraCharacteristics.getAvailableCaptureResultKeys();
                for (CaptureResult.Key<?> resultKey : resultKeyList) {
                    if (resultKey.getName().equals(AIS_RESULT_MODE_KEY_NAME)) {
                        mKeyAisResult = (CaptureResult.Key<int[]>) resultKey;
                        Log.i(Log.TAG, "Found CaptureResult Key: " + AIS_RESULT_MODE_KEY_NAME);
                    }
                }
                // 获取CaptureRequest Key（用于设置MFB模式）
                List<CaptureRequest.Key<?>> requestKeyList = cameraCharacteristics.getAvailableCaptureRequestKeys();
                for (CaptureRequest.Key<?> requestKey : requestKeyList) {
                    if (requestKey.getName().equals(AIS_REQUEST_MODE_KEY_NAME)) {
                        mKeyAisRequestMode = (CaptureRequest.Key<int[]>) requestKey;
                        Log.i(Log.TAG, "Found CaptureRequest Key: " + AIS_REQUEST_MODE_KEY_NAME);
                    }
                }
                if (mKeyAisAvailableModes != null) {
                    int[] availableModes = cameraCharacteristics.get(mKeyAisAvailableModes);
                    if (availableModes != null) {
                        for (int mode : availableModes) {
                            Log.i(Log.TAG, "Supported MFB Mode: " + mode);
                        }
                    } else {
                        Log.i(Log.TAG, "No available MFB modes.");
                    }
                } else {
                    Log.i(Log.TAG, "MFB Key not found.");
                }
                /////
                //mFlashSupported = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);

                streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                /*Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
                for (int i = 0; i < sizes.length; i++) {
                    Log.i(Log.TAG, "支持的分辨率：" + sizes[i].getWidth() + "x" + sizes[i].getHeight());
                }

                Range<Integer>[] fpsRange = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                for (Range<Integer> fps : fpsRange) {
                    Log.i(Log.TAG, "支持的帧率：[" + fps.getLower() + "," + fps.getUpper() + "]");
                }*/

                /*int[] modes = cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
                for (int i = 0; i < modes.length; i++) {
                    Log.i(Log.TAG, "支持的场景模式：" + modes[i]);
                }*/

                startBackgroundThread();
                cameraManager.openCamera(camerID, mStateCallback, mBackgroundHandler);
                mCameraOpenCloseLock.waitLock(2500);
                break;
            }
        } catch (Exception e) {
            Log.i(Log.TAG, String.format("打开摄像头%d异常: %s", camID, e.getMessage()));
        }
    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    public synchronized void closeCamera() {
        super.closeCamera();
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        closePreviewSession();
        stopBackgroundThread();
        clearState(DevState.OPENING); /////

        //Log.i(Log.TAG, "关闭摄像头");
    }

    /*
        开始录制视频
     */
    @Override
    public boolean videoStop() {
        try {
            //Log.i(Log.TAG, "停止录制");
            unlockFocus();
            close();
            super.videoStop();
            /////
            releaseMuxer();
            uninitVideoEncoder();
            if (useAudio) {
                uninitAudioEncoder();
            }
            /////
        } catch (Exception e) {
            Log.i(Log.TAG, "停止录像异常：" + e);
            return false;
        }
        return true;
    }

    @Override   // 录制短视频使用配置文件中的分辨率和I帧间隔
    public boolean videoStart(int stream, String filename, int duration, boolean upload) {
        try {

            Settings.VideoCodec vc = getVideoCodec(stream); /////
            mResolution = Settings.VideoCodec.getResolution(vc.resolution);

            Log.e(Log.TAG,"录制视频"+ vc.frame + ":" + vc.iFrame);

            if (is6735) {
                mResolution = new Point(1280, 720);
            }

            createPreviewSession(mResolution.x, mResolution.y, true);

            {
                // 对焦最大超时10秒
                lockFocus(10000, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,true,vc);
                // 状态设置为录像
                mState = STATE_VIDEO_RECORDING;
            }
            super.videoStart(stream, filename, duration, upload); /////
            String tmpfile = MainActivity.DATA_DIR + "record_" + id + ".mp4"; /////

            /////
            mediaMuxer = new MediaMuxer(tmpfile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
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
            initVideoEncoder(stream, mResolution.x, mResolution.y, false);   // 录制短视频使用配置文件中的分辨率和I帧间隔
            /////

            new Timer("recordStop").schedule(new TimerTask() { /////
                @Override
                public void run() {
                    procVideoHandler.removeCallbacksAndMessages(null); /////
                    if (useAudio) {
                        procAudioHandler.removeCallbacksAndMessages(null); /////
                    }
                    videoStop();
                    if (upload) {
                        Utils.su("mv " + tmpfile + " " + filename);
                    } else {
                        File file = new File(tmpfile);
                        File finalFile = new File(filename);
                        file.renameTo(new File(MainActivity.FILE_PATH + id + File.separator + finalFile.getName()));
                        Log.i(Log.TAG, "MIPI摄像头文件不上传，修改文件为：" + (MainActivity.FILE_PATH + id + File.separator + finalFile.getName())); /////
                    }
                    controllerCallback.onVideoFinished(System.currentTimeMillis(), id, streamType, filename, upload);
                }
            }, (duration + 1) * 1000);  // 多1秒作为保险余地，不然可能录像时间不足
        } catch (Exception e) {
            videoStop(); /////
            Log.e(Log.TAG, "MIPI摄像头录制视频异常: " + e.getMessage());
        }
        return isRecording(); /////
    }

    public boolean videoPause() {
        return true;
    }

    public boolean videoResume() {
        return true;
    }

    public boolean close() {
        closeCamera();
        return true;
    }

    public boolean liveStop() {
        //Log.i(Log.TAG, "停止预览");
        try {
            if (!mCameraPhotoing && !isRecording()) {
                unlockFocus();
                close();
            }
            uninitVideoEncoder(); /////
            rtph264 = null;
        } catch (Exception e) {
            Log.i(Log.TAG, "停止预览异常：" + e);
            return false;
        } finally {
            clearState(DevState.LIVING); /////
        }
        return true;
    }



    public boolean setOSD(Settings.OSD osd, boolean osdNull) { /////
        this.osd = osd;
        return true;
    }

    public boolean setCodec(Settings.VideoCodec codec) {
        return false;
    }


    /////
    public synchronized boolean open(int stream, onOpenCallback cb, int timeoutSeconds, boolean waitSelfCheck) {
        if (isOpening()) {
            Log.i(Log.TAG, "摄像头已经打开");
            if (cb != null) cb.openSucceed();
            return true;
        }
        if (mMainBoard == 1) {
            MipiSwitch.switchTo(camID);
        }
        streamType = stream;
        openCamera();
        if (mCameraDevice == null) {
            Log.i(Log.TAG, "打开摄像头失败");
            if (cb != null) cb.openFailed(-1);
            return false;
        }

        previewReady = false;
        setState(DevState.OPENING);
        if (cb != null) cb.openSucceed();
        return true;
    }
    /////


    public boolean liveStart(int stream, int ssrc) {
        if (isRecording()) {
            Log.i(Log.TAG, "拉流失败，正在录制视频");
            return false;
        }
        if (isLiving()) {
            Log.i(Log.TAG, "拉流失败，正在播放视频");
            return false;
        }

        scheduledHandler.post(() -> {
            Settings.VideoCodec vc = getVideoCodec(streamType);
            mResolution = Settings.VideoCodec.getResolution(vc.resolution);

            /////
//            Point size = Settings.VideoCodec.getResolution(codec.get(String.valueOf(0)).resolution);         // 默认使用的是主码流

            // 由于分辨率大于1920x1080无法拉流，因此设置最大的分辨率为1920x1080
            if (mResolution.x > 1920 || mResolution.y > 1080) {
                mResolution = new Point(1920, 1080);
            }
//            mResolution = size;
            /////

            Log.i(Log.TAG, "视频设置的宽高===>：" + mResolution.x + "x" + mResolution.y);

            createPreviewSession(mResolution.x, mResolution.y, true);
            if (mPreviewSession == null) {
                Log.i(Log.TAG, "创建会话失败");
                return;
            }
            { // 直播要打包成rtp包进行发包
                initVideoEncoder(streamType, mResolution.x, mResolution.y,true); /////
                rtph264 = new RTPH264(ssrc);
            }
            // 先开始播放，然后再对焦，提高后台出流时间
            mOnShow = true;
            previewReady = true;
            setState(DevState.LIVING);
            lockFocus(10000, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,false,null);
            mState = STATE_VIDEO_LIVING;

            Log.i(Log.TAG, "拉流成功， SSRC:" + ssrc);
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
    private Point getBestSize2(final Size[] sizes, final int width, final int height) {
        if (sizes == null || sizes.length == 0 || width <= 0 || height <= 0)
            return new Point(width, height);

        //指定列表中第一组数据为查找的初始数据
        Size found = sizes[0];
        final int specifiedArea = width * height;
        //定义尺寸的最小匹配值
        int minMatch = Math.abs(specifiedArea - found.getWidth() * found.getHeight());

        for (int i = 1; i < sizes.length; i++) {
            //for (Camera.Size supportSize : newList) {
            int supportedArea = sizes[i].getWidth() * sizes[i].getHeight();
            //指定图片尺寸与支持列表中的尺寸完全匹配，不再进行查找
            if (supportedArea == specifiedArea) {
                return new Point(sizes[i].getWidth(), sizes[i].getHeight());
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
        return new Point(found.getWidth(), found.getHeight());
    }

    private HashMap<String, Settings.AIParameter> aiParameters; /////
    /**
     * 摄像头没有主/子码流之分，所以stream都是0
     */


    @Override
    public boolean takePhoto(int stream, int preset, boolean show, String filename, Bitmap pop, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert) {
        aiParameters = aps; /////
        scheduledHandler.post(() -> {

            try{
                mResolution = Settings.PhotoConfig.getImageSize(photoConfig.size);

                if (is6735) {
                    // 6735通过camera2接口只能设置为1280*720，更大分辨率设置无效
                    mResolution = new Point(1280, 720);
                } else if (streamConfigurationMap != null) {
                    Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
                    mResolution = getBestSize2(sizes, mResolution.x, mResolution.y);
                }

                if (!isLiving()){
                    createPreviewSession(mResolution.x, mResolution.y, false);
                }

                if (mPreviewSession == null) {
                    Log.i(Log.TAG, "创建预览会话失败");

                    //// 如果拍照失败，先释放资源，再重新申请
                    if (!isLiving() && !isRecording()) {
                        unlockFocus();
                        close();
                    }

                    controllerCallback.onPhotoFailed(id, preset, filename);
                    return;
                }

                mOnShow = show;

                mCameraPhotoing = true;

                takePhotoOnce.set(true);
                photoDone.set(false);

                mFileImage = filename;
                mFilePreset = preset;
                {
                    lockFocus(10000, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,false,null);
                    mState = STATE_PICTURE_TAKING;
                }

                // 等待拍照成功
                int timeoutSeconds = 60;
                int timeoutMs = timeoutSeconds * 1000;
                long start = System.currentTimeMillis();


                while (!photoDone.get()) {
                    long remain = timeoutMs - (System.currentTimeMillis() - start);
                    if (remain <= 0) break;
                    mCameraPhtotingLock.waitLock((int) remain);
                }

                if (!photoDone.get()) {
                    Log.i(Log.TAG, "抓拍超时" + timeoutSeconds + "秒");

                    //// 如果拍照失败，先释放资源，再重新申请，可能会存在摄像头资源被占用，导致一直申请不上资源
                    if (!isLiving() && !isRecording()) {
                        unlockFocus();
                        close();
                    }
                    controllerCallback.onPhotoFailed(id, preset, filename);
                }

            }catch (Exception e){
                Log.e(Log.TAG, "拍照过程中发生异常"+e);
                controllerCallback.onPhotoFailed(id, preset, filename);
            }finally {
                if (!isLiving() && !isRecording()) {
                    unlockFocus();
                    close();
                }
                mCameraPhotoing = false;
            }
        });
        return true;
    }

//    @Override
//    public boolean takePhoto(int stream, int preset, boolean show, String filename, Bitmap pop, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert) {
//        aiParameters = aps; /////
//        scheduledHandler.post(() -> {
//            // 拍照为第一优先级
//            if (isLiving()) liveStop(); /////
//            if (isRecording()) videoStop(); /////
//
////            if (open(stream) != true) {
////                controllerCallback.onPhotoFailed(id, preset, filename);
////                return;
////            }
//
//            mResolution = Settings.PhotoConfig.getImageSize(photoConfig.size);
//            if (is6735) {
//                // 6735通过camera2接口只能设置为1280*720，更大分辨率设置无效
//                mResolution = new Point(1280, 720);
//            } else if (streamConfigurationMap != null) {
//                Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
//                /*for (int i = 0; i < sizes.length; i++) {
//                    Log.i(Log.TAG, "图像支持的分辨率：" + sizes[i].getWidth() + "x" + sizes[i].getHeight());
//                }*/
//                mResolution = getBestSize2(sizes, mResolution.x, mResolution.y);
//            }
//
//            createPreviewSession(mResolution.x, mResolution.y, false);
//
//            if (mPreviewSession == null) {
//                Log.i(Log.TAG, "创建预览会话失败");
//                controllerCallback.onPhotoFailed(id, preset, filename);
//                return;
//            }
//
//            mOnShow = show; /////
//            mCameraPhotoing = true;
//            mFileImage = filename;
//            mFilePreset = preset;
//            {
//                // 等待对焦成功
//                lockFocus(10000, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//                mState = STATE_PICTURE_TAKING;
//            }
//            // 等待拍照成功
////            int timeoutSeconds = 5; // 拍摄分辨率500W的照片2秒超时不够，晚上会有丢照片的情况，增加到5秒
//            int timeoutSeconds = 30;
//            if (mCameraPhtotingLock.waitLock(timeoutSeconds * 1000) != true) {
//                Log.i(Log.TAG, "抓拍超时" + timeoutSeconds + "秒");
//                controllerCallback.onPhotoFailed(id, preset, filename);
//            }
//
//            if (!isLiving() && !isRecording()) { /////
//                unlockFocus();
//                close();
//            }
//            mCameraPhotoing = false;
//        });
//
//        return true;
//    }


    @Override
    public boolean takeVideo(final String filename, final int duration, int stream, boolean upload) {
        if (isRecording()) return false;
        // 录像优先级高于直播拉流
        if (isLiving()) liveStop();

        scheduledHandler.post(() -> {
            videoStart(stream, filename, duration, upload);
        });
        return true;
    }

    protected boolean reboot() {
        return true;
    }

    public void startRecordCheckLine(int group) {
    }

    public void stopRecordCheckLine(int group) {
    }

    /////
    public boolean setTime(int dwYear, int dwMonth, int dwDay, int dwHour, int dwMinute, int dwSecond) {
        return true;
    }
    /////

    public Settings.CruiseGroup[] getCruise() {
        return null;
    }

    public boolean setCruise(int cmd, int group, int index, int preset, int duration, int speed) {
        return true;
    }

    public boolean setRecordTimes(List<Settings.VideoTimeItem> list) { /////
        return true; /////
    }

    @Override
    public boolean playbackStop() {
        return true;
    }

    @Override
    public boolean imageFusion(int stream, int preset, boolean show, String filename, Bitmap image_rgb, int recordPreset) {
        return true;
    }
}
