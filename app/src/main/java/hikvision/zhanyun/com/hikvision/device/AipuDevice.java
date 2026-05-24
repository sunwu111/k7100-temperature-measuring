package hikvision.zhanyun.com.hikvision.device;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Base64;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.HashMap;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import hikvision.zhanyun.com.hikvision.MainActivity;
import hikvision.zhanyun.com.hikvision.RtspClient;
import hikvision.zhanyun.com.hikvision.Settings;
import hikvision.zhanyun.com.hikvision.rto.RTPH264;
import hikvision.zhanyun.com.hikvision.utils.Log;
import lyh.Utils;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static lyh.Utils.PERIOD_SECOND;
import static lyh.Utils.saveBitmapAsJPEG;
import static lyh.Utils.stringToDateTime;

import org.apache.commons.io.FilenameUtils;

public class AipuDevice extends Device {
    private List<Settings.FileItem> videoFiles = new ArrayList<>();
    // 以下变量用于解码，为提高性能，避免每次回调都创建和判断使用全局变量提高性能
    private RtspClient rtspLiveClient;          // 直播 RTSP 客户端
    private RtspClient rtspPlaybackClient;      // 回放 RTSP 客户端
    private String token = "";
    final private String url;  // 设备请求的URL地址基础
    private boolean isMuxerInited = false;

    private final long MIN_BOOTUP_MILISECONDS = 100 * PERIOD_SECOND;

    private final long AFTER_BOOTUP_PTZ_SELFCHECK_WAIT_MS = 30 * PERIOD_SECOND;

//    private Boolean isOldCamera = false; /////
    private byte brightness = 50;  // 记录模拟调节光圈的亮度
    private byte contrast = 50;  // 记录模拟调节光圈的对比度
    private byte saturation = 50;  // 记录模拟调节光圈的饱和度

    public AipuDevice(int ID, Context context, String ip, int port, String User, String pwd) {
        super(ID, context, false); /////

        this.drawOSD = false;
        this.user = User;
        this.password = pwd;
        this.server = ip;
        this.port = port;
        this.type = DEVICE_DVR_AIPU;
        url = String.format("http://%s:%s@%s", user, password, server);
        try {
            String s = user + ":" + password;
            token = "Basic " + Base64.encodeToString(s.getBytes(), Base64.NO_WRAP);
        } catch (Exception e) {
        }
    }
    /**
     * 云台移动 NET_DVR_PTZControl_Other参数：(播放标记, 通道， 指令码, 开始标记0或停止标记1)
     *
     * @param dir 九宫格数字方向
     */
    private Response step(int dir, int speed) {
        if (speed < 1 || speed > 10) {
            speed = 5;
            /////
            // 减小变倍幅度，原来为5，减小为1
            if (dir == 9 || dir == 10) {
                speed = 1;
            }
            /////
        }
        request(url + "/merlin/PtzCtrl.cgi?speed=" + speed + "&channelno=0&value=0&operation=" + dir, true); /////
        SystemClock.sleep(speed * 200);
        return request(url + "/merlin/PtzCtrl.cgi?speed=" + speed + "&channelno=0&value=0&operation=0", true); /////
    }

    protected void onPreview(Bitmap bitmap) {
        if (controllerCallback != null) {
            controllerCallback.onFrame(bitmap);
        }
    }

    // 判断是新机芯还是老机芯，老机芯的字体大，新机芯字体小
    private synchronized void getModeType() {
        try {
            Response response = request(url + "/merlin/GetVersion.cgi", true); /////
            if (response == null || response.code() != 200)
                return;

            String body = response.body().string();
            //Log.i(Log.TAG, "机芯版本：" + body);
            String[] items = body.split("\r\n");
            for (String item : items) {
                if(item.contains("Mode")) {
                    //String modeType = item.split(":")[1].trim();
                    if (item.contains("8220C") || item.contains("8223W")) {
                        isOldCamera = true;
                    } else {
                        isOldCamera = false;
                    }
                    break;
                }
            }
        } catch (Exception e) {

        }
    }

    /**
     * 开始实时预览
     *
     * @param dwStreamType 码流类型
     */
    public boolean liveStart(int dwStreamType, int ssrc) {
        this.ssrcLive = ssrc;
        this.streamType = dwStreamType;

        rtph264 = new RTPH264(ssrc);
        if (rtspLiveClient != null) rtspLiveClient.stop();

        rtspLiveClient = new RtspClient(server, 554, user, password, "id=1&type=" + dwStreamType, rtspLiveCallback);
        if (!rtspLiveClient.start(false)) {                  // 这个start会开启rtspclient中的
            Log.e(Log.TAG, "云台拉流失败");
            return false;
        }
        setState(DevState.LIVING);// 置拉流位为高
        Log.d(Log.TAG, String.format("直播: %s@%s:%d", user, server, port));
        return true;
    }

    @Override
    protected boolean reboot() {
        request(url + "/merlin/Reboot.cgi", true); /////
        return true;
    }

    @Override
    public void startRecordCheckLine(int group) {
        try {
            Response reponse = request(url + "/merlin/PtzCtrl.cgi?operation=31&speed=4&channelno=0&value=" + group, true); /////
            Log.i(Log.TAG, "开始设置巡检线路: " + reponse.body().string());
        } catch (Exception e) {
            Log.e(Log.TAG, "开始设置巡检线路: " + e.getMessage());
        }
    }

    @Override
    public void stopRecordCheckLine(int group) {
        try {
            Response response = request(url + "/merlin/PtzCtrl.cgi?operation=32&speed=4&channelno=0&value=" + group, true); /////
            Log.w(Log.TAG, "完成设置巡检线路: " + response.body().string());
        } catch (Exception e) {
            Log.e(Log.TAG, "完成设置巡检线路: " + e.getMessage());
        }
    }

    @Override
    public boolean setTime(int dwYear, int dwMonth, int dwDay, int dwHour, int dwMinute, int dwSecond) {
        String body = String.format("{\"TimeCfg\":{\"day\":%d,\"hour\":%d,\"minute\":%d,\"month\":%d,\"second\":%d,\"synctime\":1,\"year\":%d},\"result\":{\"message\":\"OK\",\"num\":200}}",
                dwDay, dwHour, dwMinute, dwMonth, dwSecond, dwYear);
        Response response = request(url + "/merlin/SetTimeCfg.cgi", body);

        if (response == null || response.code() != 200) {
            Log.i(Log.TAG, "球机时间同步失败");
            return false;
        } else {
            //Log.i(Log.TAG, "球机时间同步成功");
            return true;
        }
    }

    @Override
    public void setSceneName(int presetNo, String name) {
        move(9, presetNo);
    }

    @Override
    public Settings.FileList listFile(int videoType, String startTime, String stopTime, int startNumb, int endNumb) {
        if (videoFiles.isEmpty()) {
            return new Settings.FileList();
        }

        Settings.FileList ret = new Settings.FileList();
        ret.channel = (byte) id;

        ret.begin = Long.MAX_VALUE;
        ret.end = 0;
        ret.type = videoType;
        List<Settings.FileItem> files = new ArrayList<>();
        for (int i = startNumb; i <= endNumb; i++) {
            if (i >= videoFiles.size()) break;
            Settings.FileItem item = videoFiles.get(i);
            if (item.begin.timestamp < ret.begin) ret.begin = item.begin.timestamp;
            if (item.end.timestamp > ret.end) ret.end = item.end.timestamp;
            files.add(item);
        }
        ret.files = files.toArray(new Settings.FileItem[0]);

        return ret;
    }

    @Override
    public String toString() {
        return String.format("通道号: %d\n名称: %s\n地址: %s:%d\n用户名: %s\n", id, name, server, port, user);
    }

    /////
    public Bitmap cropCenterAndScale(Bitmap originalBitmap) {
        if (originalBitmap == null) return null;
        int width = originalBitmap.getWidth();
        int height = originalBitmap.getHeight();
        // 计算中心裁剪区域
        int cropWidth = width / 2;
        int cropHeight = height / 2;
        int cropX = (width - cropWidth) / 2;
        int cropY = (height - cropHeight) / 2;
        // 裁剪出中心区域
        Bitmap croppedBitmap = Bitmap.createBitmap(originalBitmap, cropX, cropY, cropWidth, cropHeight);
        // 将裁剪后的区域放大回原尺寸
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, width, height, true);
        // 释放裁剪 Bitmap 的内存
        croppedBitmap.recycle();
        return scaledBitmap;
    }
    /////
    @Override
    public boolean takePhoto(int stream, int preset, boolean show, String filename, Bitmap pop, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert) {
        setState(DevState.PHOTOING);
        try {
            /////
            // TODO 待优化
//            if ((preset != 0 && preset != recordPreset) || (preset != 0 && preset == recordPreset && isLiving())) {
//            if (preset != 0 && preset != recordPreset) {  // 如果拉流时转动了云台，再拍照，则不会转回拍照预置位，存在问题！

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String startTime = sdf.format(new Date());
            Log.d(Log.TAG, "开始转动云台:" + startTime);

            if (preset != 0) {
                move(2, preset);
                SystemClock.sleep(PTZ_PRESET_MOVE_TIME);    // 等待到达预置位
            }

            String endTime = sdf.format(new Date());
            Log.d(Log.TAG, "转动云台结束:" + endTime);

            
//            else if (preset != 0) {
//                move(2, preset);
//                SystemClock.sleep(5000);  // 等待OSD配置生效
//            } else {
//                SystemClock.sleep(5000);  // 等待OSD配置生效
//            }
            /////
            // 原始文件保存到其他目录进行图片处理，处理完成再拷贝到目的文件，防止在文件处理的过程中进行累积文件上传


            String tempfile = Environment.getExternalStorageDirectory() + "/" + FilenameUtils.getName(filename);


            boolean ok = false;
            for (int i = 0; i < 3; i++) {
                ok = download(url + "/snapshot_ch=1", tempfile);
                if (ok) break;
            }

            if (!ok) {
                clearState(DevState.PHOTOING);
                Log.i(Log.TAG, "云台抓拍错误");
                return false;
            }

            long stamp = getTimestampFromFilename(tempfile);
            BitmapFactory.Options bitmapOption = new BitmapFactory.Options();
            bitmapOption.inMutable = true;
            Bitmap bitmap = BitmapFactory.decodeFile(tempfile, bitmapOption);
            if (bitmap != null) {
                bitmap = processPhoto(bitmap, stamp, preset, aps, alert);
                /////
                if (isOldCamera) {
                    Log.i(Log.TAG, "机芯使用自定义OSD");
                    this.drawOSD = true;
                    drawWatermark(bitmap);
                }
                /////
                bitmap = processPOP(bitmap, pop);
                if (show) controllerCallback.onFrame(bitmap);
                saveBitmapAsJPEG(bitmap, filename, 90);
                bitmap.recycle();
            }
            new File(tempfile).delete();

            File file = new File(filename);
            if (file.exists()) {
                clearState(DevState.PHOTOING);
                controllerCallback.onPhotoTaked(stamp, this.id, preset, filename);
                return true;
            }
        } catch (Exception e) {
            Log.e(Log.TAG, "云台抓拍异常: " + e.getMessage());
        } finally {
            clearState(DevState.PHOTOING);
        }
        return false;
    }

    @Override
    public boolean takeVideo(final String filename, final int duration, int stream, boolean upload) {
        if (isRecording()) {
            Log.i(Log.TAG, "云台录制短视频失败，正在录制中");
            return false;
        }
        // 录像优先级高于直播拉流
        if (isLiving()) liveStop();

        videoStart(stream, filename, duration, upload);

        return true;
    }

    public void initMuxer(byte[] sps, byte[] pps) {
        if (isMuxerInited) return;
        Settings.VideoCodec vCodec = getVideoCodec(streamType);
        Point size = Settings.VideoCodec.getResolution(vCodec.resolution);

        // 写入编码数据之前需要配置视频头部信息(csd参数)，csd全称Codec-specific Data，对于H。264来说，
        // “csd-0”和"csd-1"分别对应sps和pps；对于AAC来说，"csd-0"对应ADTS。
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.x, size.y);
        mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, vCodec.frame);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
        mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(pps));
        mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(sps));
        videoTrackIndex = mediaMuxer.addTrack(mediaFormat); /////
        mediaMuxer.start();
        isMuxerInited = true;
    }

    @Override
    public boolean videoStop() {
        try {
            close();
            isMuxerInited = false;
            super.videoStop();

            if (rtspLiveClient != null) {
                rtspLiveClient.stop();
                rtspLiveClient = null;
            }
            if (mediaMuxer != null) {
                mediaMuxer.stop();
                mediaMuxer.release();
                mediaMuxer = null;
            }
            Log.i(Log.TAG, "云台停止录制");
        } catch (Exception e) {
            Log.i(Log.TAG, "云台停止录制异常：" + e.getMessage());
            return false;
        }
        return true;
    }

    @Override
    public boolean videoStart(int stream, String filename, int duration, boolean upload) {
        String tmpfile = MainActivity.DATA_DIR + "record_" + id + ".mp4";
        try {
            mediaMuxer = new MediaMuxer(tmpfile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            rtph264 = new RTPH264(0);

            if (rtspLiveClient != null) rtspLiveClient.stop();

            String streamURI = "id=1&type=" + stream;
            rtspLiveClient = new RtspClient(server, 554, user, password, streamURI, rtspLiveCallback);
            if (!rtspLiveClient.start(false)) {
                throw new Exception("RTSP拉流启动失败");
            }

            super.videoStart(stream, filename, duration, upload);

            new Timer("recordStop").schedule(new TimerTask() {
                @Override
                public void run() {
                    procVideoHandler.removeCallbacksAndMessages(null); /////
                    videoStop();
                    if (upload) {
                        Utils.su("mv " + tmpfile + " " + filename);
                    } else {
                        File file = new File(tmpfile);
                        File finalFile = new File(filename);
                        file.renameTo(new File(MainActivity.FILE_PATH + id + File.separator + finalFile.getName()));
                        Log.i(Log.TAG, "文件不上传，修改文件为：" + (MainActivity.FILE_PATH + id + File.separator + finalFile.getName()));
                    }
                    controllerCallback.onVideoFinished(System.currentTimeMillis(), id, streamType, filename, upload);
                }
            }, duration * 1000);
        } catch (Exception e) {
            videoStop();
            controllerCallback.onVideoFailed(id, filename);
            Log.e(Log.TAG, "云台录制视频异常: " + e.getMessage());
        }
        return isRecording();
    }

    //////
    // 先不增加自动告警功能
//    @Override
//    public boolean playbackSaveFile(String startS, String endS, String filename) {
//        String tmpfile = MainActivity.DATA_DIR + "record_" + id + ".mp4";
//        try {
//            // 初始化视频保存器
//            mediaMuxer = new MediaMuxer(tmpfile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
//
//            // 开始回放
//            if (!playbackStart(startS, endS, 0)) {
//                Log.e(Log.TAG, "云台告警回放失败");
//                return false;
//            }
//            // 启动定时器，到时间后自动停止回放并保存文件
//            new Timer("playbackSaveFileStop").schedule(new TimerTask() {
//                @Override
//                public void run() {
//                    try {
//                        playbackStop();  // 停止回放
//                        videoStop();  // 停止视频保存
//                        // 重命名或移动临时文件为最终文件
//                        File file = new File(tmpfile);
//                        if (file.exists()) {
//                            Utils.su("mv " + tmpfile + " " + filename);
//                        }
//                        controllerCallback.onVideoFinished(System.currentTimeMillis(), id, streamType, filename, true);
//                    } catch (Exception e) {
//                        Log.e(Log.TAG, "云台告警短视频保存失败: " + e.getMessage());
//                    }
//                }
//            }, 20 * 1000);
//        } catch (Exception e) {
//            Log.e(Log.TAG, "云台告警录制短视频异常: " + e.getMessage());
//            return false;
//        }
//        return true;
//    }
    //////

    @Override
    public boolean playbackStart(String startS, String endS, int ssrc) {
        this.ssrcPlayback = ssrc;

        Date begin = stringToDateTime("yyyy-MM-dd-HH-mm-ss", startS);
        Date end = stringToDateTime("yyyy-MM-dd-HH-mm-ss", endS);

        // RTSP播放地址如下：
        // rtsp://ip:554/id=1&type=0&record=yyyymmddhhnnss_yymmddhhnnss
        // id = 通道号, type=码流类型（目前不区分主辅码流），record表示开始和结束时间，回放URL也可以通过GetRtspPlayBackUrl.cgi获取
        String para = String.format("%d%02d%02d%02d%02d%02d_%d%02d%02d%02d%02d%02d",
                begin.getYear() + 1900, begin.getMonth() + 1, begin.getDate(), begin.getHours(), begin.getMinutes(), begin.getSeconds(),
                end.getYear() + 1900, end.getMonth() + 1, end.getDate(), end.getHours(), end.getMinutes(), end.getSeconds()
        );
        rtph264 = new RTPH264(ssrc);
        if (rtspPlaybackClient != null) rtspPlaybackClient.stop();
        rtspPlaybackClient = new RtspClient(server, 554, user, password, "id=1&type=0&record=" + para, rtspPlaybackCallback);
        if (!rtspPlaybackClient.start(false)) {
            Log.e(Log.TAG, "云台回放失败: " + para);
            return false;
        }
        setState(DevState.PLAYBACKING);
        Log.i(Log.TAG, "云台回放: " + para);
        return true;
    }

    @Override
    public boolean playbackStop() {
        Log.d(Log.TAG, "云台回放停止");
        if (rtspPlaybackClient != null) {
            rtspPlaybackClient.stop();
            rtspPlaybackClient = null;
        }
        ssrcPlayback = 0;
        clearState(DevState.PLAYBACKING);

        return true;
    }

    @Override
    public boolean videoPause() {
        return false;
    }

    @Override
    public boolean videoResume() {
        return false;
    }

    private boolean closeForce;

    @Override
    public boolean open(int stream, onOpenCallback cb, int timeoutSeconds, boolean waitSelfCheck) {
        long bootTime = System.currentTimeMillis();

        closeForce = false;
        setState(DevState.OPENING);
        do {
            if (isDeviceReady()) {
                getModeType();

                Log.e(Log.TAG,"设备打开成功，等待设备稳定");

//                if (waitSelfCheck) {
//                    // 设备至少启动1分钟，太少有可能机芯或云台没有完成自检
//                    long elapse = (System.currentTimeMillis() - bootTime);
//                    if (MIN_BOOTUP_MILISECONDS - elapse > 0) SystemClock.sleep(MIN_BOOTUP_MILISECONDS - elapse);
//                }

                if (waitSelfCheck) {
                    SystemClock.sleep(AFTER_BOOTUP_PTZ_SELFCHECK_WAIT_MS);    //   设备启动成功，这个时候云台还在转动，BOOTUP_TRUE_WAIT_MILISECONDS为启动成功后的等待时间。
                }


                clearState(DevState.OPENING);
                Log.i(Log.TAG, "打开球机耗时 " + (System.currentTimeMillis() - bootTime) / 1000 + " 秒");
                if (cb != null) cb.openSucceed();
                return true;
            }

            SystemClock.sleep(PERIOD_SECOND);

        } while (Math.abs(System.currentTimeMillis() - bootTime) / 1000 < timeoutSeconds && !closeForce);

		clearState(DevState.OPENING);
        if (closeForce) {
            Log.i(Log.TAG, "强制关闭球机");
        } else {
            Log.i(Log.TAG, "打开球机超时 " + timeoutSeconds + " 秒");
            if (cb != null) cb.openFailed(-1);
        }
        
        return false;
    }

    @Override
    public boolean close() {
		clearState();
        closeForce = true;
        return true;
    }

    /**
     * 停止实时预览
     */
    @Override
    public boolean liveStop() {
        rtph264 = null;
        if (rtspLiveClient != null) {
            rtspLiveClient.stop();
            rtspLiveClient = null;
        }

        closeForce = true;
        clearState(DevState.LIVING);
        return true;
    }

//    @Override
//    public Settings.GeneralParameters getSceneParameters(byte[] dataDomain) {
//        return null;
//    }

    @Override
    public boolean setOSD(Settings.OSD osd, boolean osdNull) { /////
        /////
        this.osd = osd;
        if (!osdNull) {
            String body;
            if (!isOldCamera) {
                body = String.format("{\"OSDCfg\":[{\"channel\":0,\"size\":%d,\"anticolor\":1,\"text2\":{\"info\":" +
                                "[{\"enable\":%d},{\"enable\":%d},{\"enable\":%d}]}," +
                                "\"time\":{\"bweek\":1,\"enable\":%d,\"rect\": {\"bottom\": 1000,\"left\": 200,\"right\": 8192,\"top\": 205}}," +
                                "\"title\":{\"enable\":%d,\"name\":\"%s\",\"rect\": {\"bottom\": 8192,\"left\": 205,\"right\": 8192,\"top\": 7372}}}]}",
                        osd.size, osd.tag, osd.tag, osd.tag, osd.time, osd.tag, osd.text);
            } else {
                body = String.format("{\"OSDCfg\":[{\"channel\":0,\"size\":%d,\"anticolor\":1,\"text2\":{\"info\":" +
                                "[{\"enable\":%d},{\"enable\":%d},{\"enable\":%d}]}," +
                                "\"time\":{\"bweek\":1,\"enable\":%d,\"rect\": {\"bottom\": 1000,\"left\": 200,\"right\": 8192,\"top\": 205}}," +
                                "\"title\":{\"enable\":%d,\"name\":\"%s\",\"rect\": {\"bottom\": 8192,\"left\": 205,\"right\": 8192,\"top\": 7372}}}]}",
                        1, osd.tag, osd.tag, osd.tag, osd.time, osd.tag, osd.text);
            }
            Response response = request(url + "/merlin/SetOsdCfg.cgi", body);
            boolean ok = false;
            if (response != null && response.code() == 200) ok = true;
            Log.d(Log.TAG, "OSD设置非空: " + (!ok ? "失败" : response.message()));
            return ok;
        } else {
            String body = String.format("{\"OSDCfg\":[{\"channel\":0,\"size\":%d,\"anticolor\":1,\"text2\":{\"info\":" +
                            "[{\"enable\":%d},{\"enable\":%d},{\"enable\":%d}]}," +
                            "\"time\":{\"bweek\":1,\"enable\":%d,\"rect\": {\"bottom\": 1000,\"left\": 200,\"right\": 8192,\"top\": 205}}," +
                            "\"title\":{\"enable\":%d,\"name\":\"%s\",\"rect\": {\"bottom\": 8192,\"left\": 205,\"right\": 8192,\"top\": 7372}}}]}",
                    1, 0, 0, 0, 0, 0, "");
            Response response = request(url + "/merlin/SetOsdCfg.cgi", body);
            boolean ok = false;
            if (response != null && response.code() == 200) ok = true;
            Log.d(Log.TAG, "OSD设置为空: " + (!ok ? "失败" : response.message()));
            return ok;
        }
        /////
    }

    public boolean isDeviceReady() {
        Response response = request(url + "/merlin/GetEncodeCfg.cgi?chstart=0&chnum=1", false); /////
        if (response == null) return false;

        try {
            JSONObject jsonObject = JSON.parseObject(response.body().string());
            jsonObject = jsonObject.getJSONArray("EncodeCfg").getJSONObject(0);
            jsonObject = jsonObject.getJSONObject("mainstream");
            jsonObject = jsonObject.getJSONObject("video");
            int enable = jsonObject.getIntValue("enable");

            return enable == 1;
        } catch (Exception e) {
            return false;
        }
    }


    @Override
    public Settings.VideoCodec getVideoCodec(int streamType) {
        Response response = request(url + "/merlin/GetEncodeCfg.cgi?chstart=0&chnum=1", true); /////

        if (response == null) return super.getVideoCodec(streamType);
        try {
            JSONObject jsonObject = JSON.parseObject(response.body().string());
            jsonObject = jsonObject.getJSONArray("EncodeCfg").getJSONObject(0);
            if (streamType == 1)
                jsonObject = jsonObject.getJSONObject("ExtStream1");
            else if (streamType == 2)
                jsonObject = jsonObject.getJSONObject("ExtStream2");
            else
                jsonObject = jsonObject.getJSONObject("mainstream");
            jsonObject = jsonObject.getJSONObject("video");

            Settings.VideoCodec vc = new Settings.VideoCodec();
//            int w = jsonObject.getJSONObject("resolution").getIntValue("w");
//            int h = jsonObject.getJSONObject("resolution").getIntValue("h");
//            vc.resolution = Settings.VideoCodec.getServerResolutionByXY(w, h);
            vc.resolution = this.codec.get(streamType).resolution;
            vc.vbr = (byte) ("CBR".equals(jsonObject.getString("bitratectr")) ? 0 : 1);
            vc.bps = (short) jsonObject.getIntValue("bitrate");
            vc.streamType = (byte) streamType;
            vc.channel = (byte) id;
//            vc.iFrame = (byte) jsonObject.getIntValue("gop");
//            vc.frame = jsonObject.getByteValue("fps");
            vc.iFrame = this.codec.get(streamType).iFrame;
            vc.frame = this.codec.get(streamType).frame;
//            vc.codec = (byte) ("H264".equals(jsonObject.getString("compression")) ? 0 : 1);
            vc.frame = this.codec.get(streamType).codec;  // H265编码方式无法拉流
            return vc;
        } catch (Exception e) {
            return super.getVideoCodec(streamType);
        }
    }

    /**
     * 视频采集参数配置
     */
    @Override
    public boolean setCodec(Settings.VideoCodec v) {
        //this.codec.put(String.valueOf(v.streamType), v);

        String stream = null;
        Point size = Settings.VideoCodec.getResolution(v.resolution);
        int y = size.y;
        int x = size.x;
        if (v.streamType == 0) {
            stream = "mainstream";
            // 比较配置的分辨率与(1280, 720), (1920, 1080)哪个近，哪个近就用哪个
            int[][] resolutions = {{1280, 720}, {1920, 1080}};
            double minDistance = Float.MAX_VALUE;
            for (int[] res : resolutions) {
                float distance = (float) Math.sqrt(Math.pow(size.x - res[0], 2) + Math.pow(size.y - res[1], 2));
                if (distance < minDistance) {
                    minDistance = distance;
                    y = res[1];
                    x = res[0];
                }
            }
        // 非主码流只能设置(720, 576)分辨率
        } else if (v.streamType == 1) {
            stream = "ExtStream1";
            y = 576;
            x = 720;
        } else if (v.streamType == 2) {
            stream = "ExtStream2";
            y = 576;
            x = 720;
        }
        // 限制帧率与I帧间隔在集光机芯可设置范围内
        int iFrame;
        if (v.iFrame < 5) {
            iFrame = 5;
        } else if (v.iFrame > 100) {
            iFrame = 100;
        } else {
            iFrame = v.iFrame;
        }
        String body = String.format("{\"EncodeCfg\":[{\"channel\":0,\"%s\":{\"video\":{\"bitrate\":%d,\"bitratectr\":\"%s\",\"complevel\":2," +
                        "\"compression\":\"%s\",\"enable\":1,\"fps\":%d,\"gop\":%d,\"resolution\":{\"h\":%d,\"w\":%d}}}}]}",
                stream, v.bps,
                (v.vbr == 0 ? "CBR" : "VBR"),
                //(v.codec == 0 ? "H264" : "H265"),
                "H264",  // 定死H264编码方式，因为H265无法拉流
                v.frame > 25 ? 25 : v.frame, iFrame, y, x
        );
        Response response = request(url + "/merlin/SetEncodeCfg.cgi", body);

        Log.i(Log.TAG, "编码设置: " + (response == null ? "失败" : response.message()));
        boolean ret = response != null && response.code() == 200;

        if (ret) {
            this.codec.put(String.valueOf(v.streamType), v);
        }

        return ret;
    }




    @Override
    public boolean setRecordTimes(List<Settings.VideoTimeItem> list) {
        Log.i(Log.TAG, "设置定时录像");

        HashMap<Integer, List<String>> sectorMap = new HashMap<>();
        HashMap<Integer, Byte> streamMap = new HashMap<>();
        // 遍历录像时间列表
        for (Settings.VideoTimeItem item : list) {
            int channel = item.channel;
            // 初始化通道对应的Sector列表
            sectorMap.putIfAbsent(channel, new ArrayList<>());
            streamMap.putIfAbsent(channel, item.stream);
            // 计算录像时间范围
            int endSec = item.sec + item.duration % 60;
            int carryMin = endSec / 60;
            endSec %= 60;
            int endMin = item.min + (item.duration / 60) % 60 + carryMin;
            int carryHour = endMin / 60;
            endMin %= 60;
            int endHour = (item.hour + item.duration / 3600 + carryHour) % 24;
            String timeRange = String.format("%02d:%02d:%02d-%02d:%02d:%02d",
                    item.hour, item.min, item.sec,
                    endHour, endMin, endSec);
            // 生成"action HH:MM:SS-HH:MM:SS"
            // 动作类别：
            // 集光机芯协议：0: 普通，1: 动检，2: 报警。
            // 南网规约：0: 调用预置位（默认值），1: 调用巡航，2: 调用巡检。
            // 集光机芯协议与南网规约中的不符，因此改为1。
            String timeSlot = String.format("%d %s", 1, timeRange);
            sectorMap.get(channel).add(timeSlot);
        }
        // 生成JSON字符串
        StringBuilder bodyBuilder = new StringBuilder();
        bodyBuilder.append("{\"record\":[");
        boolean firstChannel = true;
        if (sectorMap.isEmpty()) {
            bodyBuilder.append("{\"Channel\":0,\"PacketLength\":60,\"PreRecord\":0,\"RecStream\":{\"ext1\":%d,\"main\":%d},\"Redundancy\":0,\"Sector\":[");
            // 7天都使用相同的录像时间
            for (int i = 0; i < 7; i++) {
                if (i > 0) {
                    bodyBuilder.append(",");
                }
                bodyBuilder.append("[");
                for (int j = 0; j < 6; j++) {
                    if (j > 0) {
                        bodyBuilder.append(",");
                    }
                    bodyBuilder.append("\"0 00:00:00-23:59:59\"");
                }
                bodyBuilder.append("]");
            }
            bodyBuilder.append("]}");
        } else {
            for (HashMap.Entry<Integer, List<String>> entry : sectorMap.entrySet()) {
                int channel = entry.getKey();
                List<String> sectorList = entry.getValue();
                int stream = streamMap.get(channel);
                if (!firstChannel) {
                    bodyBuilder.append(",");
                }
                firstChannel = false;
                bodyBuilder.append(String.format(
                        "{\"Channel\":0,\"PacketLength\":60,\"PreRecord\":0," +
                                "\"RecStream\":{\"ext1\":%d,\"main\":%d},\"Redundancy\":0,\"Sector\":[",
                        stream == 1 ? 1 : 0,  // 当stream=0时，ext1=0；当stream=1时，ext1=1
                        stream == 0 ? 1 : 0   // 当stream=0时，main=1；当stream=1时，main=0
                ));
                // 7天都使用相同的录像时间
                for (int i = 0; i < 7; i++) {
                    if (i > 0) {
                        bodyBuilder.append(",");
                    }
                    bodyBuilder.append("[");
                    int currentSlotCount = sectorList.size();
                    for (int j = 0; j < 6; j++) {
                        if (j > 0) {
                            bodyBuilder.append(",");
                        }
                        if (j < currentSlotCount) {
                            bodyBuilder.append("\"").append(sectorList.get(j)).append("\"");
                        } else {
                            bodyBuilder.append("\"0 00:00:00-23:59:59\"");
                        }
                    }
                    bodyBuilder.append("]");
                }
                bodyBuilder.append("]}");
            }
        }
        bodyBuilder.append("]}");
        String body = bodyBuilder.toString();
        Log.d(Log.TAG, "设置录像策略为：" + body);
        Response response = request(url + "/merlin/SetRecordCfg.cgi", body);
        boolean ok = false;
        if (response != null && response.code() == 200) ok = true;
        Log.d(Log.TAG, "定时录像设置: " + (!ok ? "失败" : response.message()));
        return ok;
    }

    private int calculateDurationInSeconds(String startTime, String endTime) {
        try {
            String[] startParts = startTime.split(":");
            String[] endParts = endTime.split(":");
            int startHour = Integer.parseInt(startParts[0]);
            int startMin = Integer.parseInt(startParts[1]);
            int startSec = Integer.parseInt(startParts[2]);
            int endHour = Integer.parseInt(endParts[0]);
            int endMin = Integer.parseInt(endParts[1]);
            int endSec = Integer.parseInt(endParts[2]);
            // 计算持续时间（秒）
            int startTotalSeconds = startHour * 3600 + startMin * 60 + startSec;
            int endTotalSeconds = endHour * 3600 + endMin * 60 + endSec;
            return endTotalSeconds - startTotalSeconds;
        } catch (Exception e) {
            Log.i(Log.TAG, "集光机芯时间解析异常：" + e);
            return 0;
        }
    }
    @Override
    public List<Settings.VideoTimeItem> getRecordTimes(int channel){
        Log.i(Log.TAG, "获取录像时间列表");
        List<Settings.VideoTimeItem> items = new ArrayList<>();

        try {
            Response response = request(url + "/merlin/GetRecordCfg.cgi?chstart=0&chnum=1", true); /////
            if (response != null) {
                JSONObject jsonObject = JSON.parseObject(response.body().string());
                Log.i(Log.TAG, jsonObject.toString());
                //JSONArray ability = jsonObject.getJSONArray("Ability");
                //if (ability != null) Log.i(Log.TAG, ability.toString());
                // 解析record数组
                JSONArray records = jsonObject.getJSONArray("record");
                if (records != null) {
                    // 遍历每个记录
                    for (int i = 0; i < records.size(); i++) {
                        JSONObject record = records.getJSONObject(i);
                        int ch = record.getIntValue("Channel") + channel;  // 集光机芯通道号为0，需要加channel
                        // 如果是特定通道的数据
                        if (ch == channel) {
                            JSONObject recStream = record.getJSONObject("RecStream");
                            int mainStream = recStream.getIntValue("main");
                            int stream = (mainStream == 1) ? 0 : 1;  // 如果main为1，stream为0，否则为1
                            // 解析Sector数组
                            JSONArray sectorArray = record.getJSONArray("Sector");
                            if (sectorArray != null) {
                                // 由于后台不能分别设置七天的时间段，所以只解析星期一（七天的时间段相同）
                                JSONArray sector = sectorArray.getJSONArray(0);
                                // 解析每个时间段
                                for (int k = 0; k < sector.size(); k++) {
                                    String timeSlot = sector.getString(k);
                                    // 解析时间段
                                    String[] timeParts = timeSlot.split(" ");
                                    int action = Integer.parseInt(timeParts[0]);
                                    String timeRange = timeParts[1];
                                    String[] times = timeRange.split("-");
                                    // 解析开始时间和结束时间
                                    String startTime = times[0].trim();
                                    String endTime = times[1].trim();
                                    // 计算持续时间（秒）
                                    int duration = calculateDurationInSeconds(startTime, endTime);
                                    // 提取小时、分钟、秒
                                    int hour = Integer.parseInt(startTime.split(":")[0]);
                                    int min = Integer.parseInt(startTime.split(":")[1]);
                                    int sec = Integer.parseInt(startTime.split(":")[2]);
                                    if (action != 0) {
                                        // 创建VideoTimeItem对象并添加到列表
                                        Settings.VideoTimeItem item = new Settings.VideoTimeItem();
                                        item.channel = (byte) ch;
                                        item.stream = (byte) stream;
                                        item.action = 0;
                                        item.para = 0;
                                        item.duration = duration;
                                        item.hour = (byte) hour;
                                        item.min = (byte) min;
                                        item.sec = (byte) sec;
                                        items.add(item);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "获取录像列表异常：" + e);
        }
        return items;
    }
   
//    public List<Settings.VideoTimeItem> getRecordTimes(int channel){
//        Log.i(Log.TAG, "获取录像时间列表");
//        List<Settings.VideoTimeItem> items = new ArrayList<>();
//
//        try {
//            Response response = request(url + "/merlin/GetRecordCfg.cgi?chstart=0&chnum=1");
//            if (response != null) {
//                JSONObject jsonObject = JSON.parseObject(response.body().string());
//                Log.i(Log.TAG, jsonObject.toString());
//                //JSONArray ability = jsonObject.getJSONArray("Ability");
//                //if (ability != null) Log.i(Log.TAG, ability.toString());
//                JSONArray records = jsonObject.getJSONArray("record");
//                if (records != null) Log.i(Log.TAG, records.toString());
//            }
//        } catch (Exception e) {
//            Log.i(Log.TAG, "获取录像列表异常：" + e);
//        }
//        return items;
//    }
    /*
    public List<Settings.VideoTimeItem> getRecordTimes(int channel){
        List<Settings.VideoTimeItem> items = new ArrayList<>();
        Response response = null;
        try {
            for (int i = 0; i < 3; i++) {
                response = request(url + "/merlin/GetRecordCfg.cgi?chstart=0&chnum=1");
                if (response != null) break;
            }
            if (response != null) {
                JSONObject jsonObject = JSON.parseObject(response.body().string());
                JSONArray records = jsonObject.getJSONArray("record");
                if (records != null && records.size() > 0) {
                    JSONObject thisRecord = (JSONObject) records.get(0);
                    RecordCfg recordCfg = JSON.parseObject(thisRecord.toString(), RecordCfg.class);
                    if (recordCfg == null || recordCfg.sectors.size() != 7) {
                        // 周日--周六共7天
                        return items;
                    }

                    if (recordCfg.stream.main == 0 && recordCfg.stream.ext1 == 0) {
                        // 所有流都没有录像
                        return items;
                    }
                    int stream = recordCfg.stream.main == 1 ? 0 : 1;
                    for (List<String> weekday : recordCfg.sectors) {
                        String sector = weekday.get(0);
                        String[] action = sector.split(" ");
                        String[] time = action[1].split("-");
                        Settings.VideoTimeItem item = new Settings.VideoTimeItem();
                        item.channel = (byte) channel;
                        item.stream = (byte) stream;
                        if ((Byte.valueOf(action[0].trim()).byteValue() & 0x01) == 0x01) {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm:ss");
                            Date startTime = dateFormat.parse(time[0].trim());
                            Date endTime = dateFormat.parse(time[1].trim());
                            item.duration = (int) (endTime.getTime() - startTime.getTime()) / 1000;
                            item.hour = (byte) startTime.getHours();
                            item.min = (byte) startTime.getMinutes();
                            item.sec = (byte) startTime.getSeconds();
                        }
                        Log.i(Log.TAG, item.toString());
                        items.add(item);
                    }
                }
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "获取录像列表异常：" + e);
        }
        return items;
    }
    * */

    boolean paused = false;
    float lastScale = 1.0f;

    /**
     * 控制录像回放
     *
     * @param code    命令   0，正常播放, 1，表示播放完成
     * @param scale   回放速度， 0 = 暂停， 1 = 正常速度， > 1 表示几倍数快进， < 1 表示几倍数慢放
     * @param offset: 0 = 从0开始播放， > 0 表示跳转到指定位置开始播放，如果暂停后表示继续播放
     */

    @Override
    public boolean playbackControl(int code, float scale, int offset) {
        if (rtspPlaybackClient == null)
            return super.playbackControl(code, scale, offset);

        boolean ret;
        if (code == 1) {
            ret = playbackStop();
        } else if (scale == 0) {
            ret = rtspPlaybackClient.pause();
            if (ret) paused = true;
        } else {
            if (paused) {
                ret = rtspPlaybackClient.play(null, scale);
                if (ret) paused = false;
            } else {
                if (lastScale != scale) {
                    ret = rtspPlaybackClient.play(null, scale);
                    lastScale = scale;
                } else {
                    ret = rtspPlaybackClient.play("" + offset + ".000-", 1);
                    rtspPlaybackClient.play(null, scale);
                }
            }
        }

        Log.d(Log.TAG, String.format("回放控制: Code=%d, Scale=%5.2f, Offset=%d, 结果: %s", code, scale, offset, ret));
        return true;
    }


    /////
    /**
     * OSD 获取配置
     */
    @Override
    public Settings.OSD getOSD() {
//        if (isOldCamera) {
        return this.osd; /////
//        } else {
//            Response response = request(url + "/merlin/GetOsdCfg.cgi?chstart=0&chnum=1");
//            if (response == null) return null;
//
//            try {
//                JSONObject jsonObject = JSON.parseObject(response.body().string());
//                JSONArray osdCfgs = jsonObject.getJSONArray("OSDCfg");
//                for (int i = 0; i < osdCfgs.size(); i++) {
//                    JSONObject osdcfg = osdCfgs.getJSONObject(i);
//                    if (osdcfg.getIntValue("channel") == 0) {
//                        Settings.OSD osd = new Settings.OSD();
//                        JSONObject title = osdcfg.getJSONObject("title");
//                        osd.tag = title.getByteValue("enable");
//                        osd.time = osdcfg.getJSONObject("time").getByteValue("enable");
//                        osd.text = title.getString("name");
//                        return osd;
//                    }
//                }
//                return super.getOSD();
//            } catch (Exception e) {
//                return null;
//            }
//        }
    }
    /////

    /**
     * 语音对讲
     *
     * @param type
     * @param samplingRate
     * @param bitWidth
     */
    public void startVoiceBroadcast(int type, byte samplingRate, int bitWidth) {

    }

    /**
     * 云台巡航操作
     * cmd: 1 = 添加 ， 2 = 删除， 3 = 修改
     * 集光云台指令：
     * 0 = 添加预置点到巡航组， 1 = 删除组内预置点， 2 = 删除巡航组， 3 = 修改组内的点
     */
    @Override
    public boolean setCruise(int cmd, int group, int index, int preset, int duration, int speed) {
        int dev_cmd = 0;
        if (cmd == 1)
            dev_cmd = 0;
        else if (cmd == 2)
            dev_cmd = 1;
        else if (cmd == 3)
            dev_cmd = 3;

        // 集光有个BUG，如果删除的时候，preset不能为0，或者不存在的，可以与index相同
        String body = String.format("{\"cmd\":%d,\"Channel\":0,\"TourIndex\":%d,\"PresetId\":%d,\"Speed\":%d,\"DWellTime\":%d, \"PresetIndex\": %d}",
                dev_cmd, group, (cmd == 2 ? 1 : preset), speed, duration, index + 1);
        try {
            Response response = request(url + "/merlin/ModPtzTourCfg.cgi", body);
            Log.i(Log.TAG, "巡航设置: " + response == null ? "" : response.body().string());
            return (response != null && response.code() == 200);
        } catch (Exception e) {
            Log.i(Log.TAG, "巡航设置错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取巡航路径信息
     *
     * @return
     * @throws IOException
     */
    @Override
    public Settings.CruiseGroup[] getCruise() {
        Response response = request(url + "/merlin/GetPtzTourCfg.cgi?Ch=0", true); /////
        if (response == null) return null;

        try {
            com.alibaba.fastjson.JSONObject jsonObject = JSON.parseObject(response.body().string());
            JSONArray ptzTour = jsonObject.getJSONArray("PtzTour");
            if (ptzTour == null || ptzTour.size() == 0) return null;
            Settings.CruiseGroup[] groups = new Settings.CruiseGroup[ptzTour.size()];

            for (int i = 0; i < groups.length; i++) {
                groups[i] = new Settings.CruiseGroup();
                JSONObject tour = ptzTour.getJSONObject(i);
                groups[i].group = tour.getByteValue("TourIndex");
                int count = tour.getJSONArray("PreTime").size();
                groups[i].cruises.clear();
                for (byte m = 0; m < count; m++) {
                    groups[i].cruises.add(new Settings.Cruise(tour.getJSONArray("Preset").getByteValue(m), tour.getJSONArray("PreTime").getByteValue(m), (byte) 5));
                }
            }
            return groups;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 向机芯发送HTTP请求，必须带Authorization！
     *
     * @param url
     * @return
     */
    private Response request(String url, boolean isDeviceReady) { /////
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
            /////
            if (!isDeviceReady) {
                Log.i(Log.TAG, "球机打开中");
            } else {
                Log.i(Log.TAG, "设备请求失败: " + url + " => " + e);
            }
            /////
            return null;
        }
    }

    /**
     * 向机芯发送HTTP请求，必须带Authorization！
     *
     * @param url
     * @return
     */
    private Response request(String url, String body) {
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
            Log.e(Log.TAG, "设备POST失败: " + url + " => " + e);
            return null;
        }
    }

    private boolean download(String url, String filename) {
        Response response = request(url, true); /////
        if (response == null) {
            return false;
        }

        if (response.code() != 200) {
            return false;
        }

        try {
            ResponseBody body = response.body();
            FileOutputStream fos = new FileOutputStream(filename);
            fos.write(body.bytes());
            fos.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 开始3D球控调节
     *
     * @param StartingPointXCoordinate
     * @param StartingPointYCoordinate
     * @param AtTheEndOfXCoordinate
     * @param AtTheEndOfCoordinate
     */
    @Override
    public boolean ptz3D(int StartingPointXCoordinate, int StartingPointYCoordinate, int AtTheEndOfXCoordinate, int AtTheEndOfCoordinate) {
        /**
         * xTop = 鼠标当前所选区域的起始点坐标的值*255/352；
         xBottom = 鼠标当前所选区域的结束点坐标的值*255/352；
         yTop = 鼠标当前所选区域的起始点坐标的值*255/288；
         yBottom = 鼠标当前所选区域的结束点坐标的值*255/288；
         */
        Settings.VideoCodec vc = getVideoCodec(streamType);
        Point size = Settings.VideoCodec.getResolution(vc.resolution);
        int left = size.x * StartingPointXCoordinate / 255;
        int top = size.y * StartingPointYCoordinate / 255;
        int right = size.x * AtTheEndOfXCoordinate / 255;
        int bottom = size.y * AtTheEndOfCoordinate / 255;
        int zoom = top < bottom ? 1 : 0;

        // {"Left":809,"Right":1135,"Top":470,"Bottom":811,"Zoom":1}
        String body = String.format("{\"Left\":%d,\"Right\":%d,\"Top\":%d,\"Bottom\":%d,\"Zoom\":%d}",
                left, right, top, bottom, zoom);
        Response response = request(url + "/merlin/SetPtzPosition.cgi", body);
        if (response != null) {
            Log.i(Log.TAG, String.format("3D控球 (%d, %d) - (%d, %d): %d => %s", left, top, right, bottom, response.code(), response.message()));
        } else {
            Log.i(Log.TAG, String.format("3D控球 (%d, %d) - (%d, %d): 失败", left, top, right, bottom));
        }

        return true;
    }

    /**
     * 图像调节
     *
     * @param v 亮度
     */
    @Override
    public boolean setPhotoParam(Settings.PhotoConfig v) {
        super.setPhotoParam(v);
        String body = String.format("{\"EFFECT\":{\"contrast\":%d,\"luminance\":%d,\"saturation\":%d}}",
                v.contrast, v.brightness, v.saturation);
        Response response = request(url + "/merlin/Image_SetEffectCfg.cgi?sect=0", body);
        try {
            Log.d(Log.TAG, "图像参数设置: " + (response == null ? "" : response.body().string()));
            return response != null && response.code() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * PTZ基本参数配置结构体
     *
     * @param byAutoScanSpeed 扫描速度等级
     */
    public void setPTZAutoScanSpeed(byte byAutoScanSpeed) {

    }

    /**
     * 根据服务器下发的para返回海康云台速度
     * 海康云台速度为1~7，服务器的para为0~100
     *
     * @param para
     * @return
     */
    public int getPTZSpeed(int para) {
        if (para <= 20)
            return 1;
        else if (para > 20 && para <= 30)
            return 2;
        else if (para > 30 && para <= 40)
            return 3;
        else if (para > 40 && para <= 50)
            return 4;
        else if (para > 50 && para <= 60)
            return 5;
        else if (para > 60 && para <= 70)
            return 6;
        else if (para > 70 && para <= 80)
            return 7;
        else if (para > 80 && para <= 90)
            return 8;
        else if (para > 90 && para <= 100)
            return 9;
        else
            return 1;
    }

    @Override
    public String getSceneName(int presetNo) {
        try {
            Response response = request(url + "/merlin/GetPtzPresetCfg.cgi?Ch=0", true); /////
            JSONArray presetNames = JSON.parseObject(response.body().string()).getJSONArray("ptz_preset");
            for (int i = 0; i < presetNames.size(); i++) {
                JSONObject item = presetNames.getJSONObject(i);
                if (item.getIntValue("PresetId") == presetNo)
                    return item.getString("PresetName");
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * @param cmd
     * @param para
     */
    @Override
    public void move(int cmd, int para) {
        super.move(cmd, para);

        Response response = null;
        if (cmd == 0) {         // 未知指令
        } else if (cmd == 1) {     // 摄像机打开电源 NET_DVR_REMOTECONTROL_POWER_ON

        } else if (cmd == 2) {      // 转到指定预置位
            response = request(url + "/merlin/PtzCtrl.cgi?operation=20&speed=4&channelno=0&value=" + para, true); /////
        } else if (cmd == 3) {  // 上下左右步进
            response = step(2, 200);
        } else if (cmd == 4) {
            response = step(7, 200);
        } else if (cmd == 5) {
            response = step(4, 200);
        } else if (cmd == 6) {
            response = step(5, 200);
        } else if (cmd == 7) {  // 镜头变焦放大
            response = step(10, 200);
        } else if (cmd == 8) {  // 镜头变焦拉近
            response = step(9, 200);
        } else if (cmd == 9) {      // 设置预置点
            String body = String.format("{\"cmd\":0,\"Channel\":0,\"PresetId\":%d,\"Speed\":0,\"DWellTime\":0,\"PresetName\":\"%s\"}", para, getSceneName(para));
            response = request(url + "/merlin/ModPTZPresetCfg.cgi", body);
        } else if (cmd == 10) {     // 关闭电源
        } else if (cmd == 11) {     // 光圈 + 1
//            response = step(13, 200);
            // 使用亮度、对比度组合来模拟调节光圈
            if (brightness < 100) {
                brightness += 10;
            }
//            if (contrast > 0) {
//                contrast -= 10;
//            }
//            if (saturation > 0) {
//                saturation -= 10;
//            }
            String body = String.format("{\"EFFECT\":{\"contrast\":%d,\"luminance\":%d,\"saturation\":%d}}",
                    contrast, brightness, saturation);
            request(url + "/merlin/Image_SetEffectCfg.cgi?sect=0", body);
//            step(11, 200);
        } else if (cmd == 12) {     // 光圈 - 1
//            response = step(14, 200);
            // 使用亮度、对比度组合来模拟调节光圈
            if (brightness > 0) {
                brightness -= 10;
            }
//            if (contrast < 100) {
//                contrast += 10;
//            }
//            if (saturation < 100) {
//                saturation += 10;
//            }
            String body = String.format("{\"EFFECT\":{\"contrast\":%d,\"luminance\":%d,\"saturation\":%d}}",
                    photoConfig.contrast, photoConfig.brightness, photoConfig.saturation);
            request(url + "/merlin/Image_SetEffectCfg.cgi?sect=0", body);
//            step(12, 200);
        } else if (cmd == 13) {     // 聚焦 + 1
            response = step(11, 200);
        } else if (cmd == 14) {     // 聚焦 - 1
            response = step(12, 200);
        } else if (cmd == 15) {             // 开始巡航
            // 因为要求场景配置，所以采用自定义功能实现巡航
            startCruise(para);
            //response = request(url + "/merlin/PtzCtrl.cgi?operation=29&speed=4&channelno=0&value=" + para, true); /////
        } else if (cmd == 16) {             // 停止巡航
            stopCruise();
            //response = request(url + "/merlin/PtzCtrl.cgi?operation=30&speed=4&channelno=0&value=" + para, true); /////
        } else if (cmd == 17) {             // 打开辅助设备开关
            response = request(url + "/merlin/PtzCtrl.cgi?operation=35&speed=4&channelno=0&value=" + para, true); /////
        } else if (cmd == 18) {     // 关闭辅助开关
            response = request(url + "/merlin/PtzCtrl.cgi?operation=36&speed=4&channelno=0&value=" + para, true); /////
        } else if (cmd == 19) {     // 开始自动扫描
            response = request(url + "/merlin/PtzCtrl.cgi?operation=25&speed=4&channelno=0&value=1", true); /////
        } else if (cmd == 20) {     // 停止自动扫描
            response = request(url + "/merlin/PtzCtrl.cgi?operation=26&speed=4&channelno=0&value=1", true); /////
        } else if (cmd == 21) {     // 随机扫描
            response = request(url + "/merlin/PtzCtrl.cgi?operation=25&speed=4&channelno=0&value=1", true); /////
        } else if (cmd == 22) {             // 停止随机扫描
            response = request(url + "/merlin/PtzCtrl.cgi?operation=26&speed=4&channelno=0&value=1", true); /////
        } else if (cmd == 23) {             // 红外全开
            response = request(url + "/merlin/PtzCtrl.cgi?operation=35&speed=4&channelno=0&value=2", true); /////
        } else if (cmd == 24) {             // 红外半开
            response = request(url + "/merlin/PtzCtrl.cgi?operation=35&speed=4&channelno=0&value=2", true); /////
        } else if (cmd == 25) {             // 红外关闭
            response = request(url + "/merlin/PtzCtrl.cgi?operation=36&speed=4&channelno=0&value=2", true); /////
        } else if (cmd == 26) {             // 删除预置位
            String body = String.format("{\"cmd\":1,\"Channel\":0,\"PresetId\":%d,\"Speed\":0,\"DWellTime\":0,\"PresetName\":\"%d\"}", para, para);
            response = request(url + "/merlin/ModPTZPresetCfg.cgi", body);
        } else if (cmd == 27) {             // 设置自动扫描左边界
            response = request(url + "/merlin/PtzCtrl.cgi?operation=23&speed=4&channelno=0&value=1", true); /////
        } else if (cmd == 28) {             // 设置自动扫描右边界
            response = request(url + "/merlin/PtzCtrl.cgi?operation=24&speed=4&channelno=0&value=1", true); /////
        } else if (cmd == 29) {             // 设置自动扫描速度
            ptzSpeed = para;
            setPTZAutoScanSpeed((byte) para);
        } else if (cmd == 30) {             // 设置步长
            ptzStep = para;
        } else if (cmd == 31) {             // 开始巡检
//            response = request(url + "/merlin/PtzCtrl.cgi?operation=33&speed=4&channelno=0&value=" + para);
        } else if (cmd == 32) {             // 停止巡检
//            response = request(url + "/merlin/PtzCtrl.cgi?operation=34&speed=4&channelno=0&value=" + para);
        } else if (cmd == 48) {             // 停止云台转动
            response = request(url + "/merlin/PtzCtrl.cgi?speed=5&channelno=0&value=0&operation=0", true); /////
        } else if (cmd == 49) {  // ↑
//            step(2, getPTZSpeed(para));
            response = request(url + "/merlin/PtzCtrl.cgi?operation=2&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 50) {   // ↓
//            step(7, getPTZSpeed(para));
            response = request(url + "/merlin/PtzCtrl.cgi?operation=7&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 51) {   // ←
//            step(4, getPTZSpeed(para));
            response = request(url + "/merlin/PtzCtrl.cgi?operation=4&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 52) {  // →
//            step(5, getPTZSpeed(para));
            response = request(url + "/merlin/PtzCtrl.cgi?operation=5&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 53) {  // ↖
//            step(1, getPTZSpeed(para));
            response = request(url + "/merlin/PtzCtrl.cgi?operation=1&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 54) {  // ↗
//            step(3, getPTZSpeed(para));
            response = request(url + "/merlin/PtzCtrl.cgi?operation=3&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 55) {  // ↙
//            step(6, getPTZSpeed(para));
            response = request(url + "/merlin/PtzCtrl.cgi?operation=6&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 56) {  // ↘
//            step(8, getPTZSpeed(para));
            response = request(url + "/merlin/PtzCtrl.cgi?operation=8&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 57) {  // ＋
            response = request(url + "/merlin/PtzCtrl.cgi?operation=10&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 58) {  // －
            response = request(url + "/merlin/PtzCtrl.cgi?operation=9&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 59) {  // 光圈 ＋
//            step(13, getPTZSpeed(para));
            response = request(url + "/merlin/PtzCtrl.cgi?operation=13&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            // 使用亮度、对比度、聚焦组合来模拟调节光圈
            if (brightness > 0) {
                brightness -= ((float) para / 200) * 10.0f;
            }
//            if (contrast < 100) {
//                contrast += ((float) para / 200) * 10.0f;
//            }
//            if (saturation < 100) {
//                saturation += ((float) para / 200) * 10.0f;
//            }
            request(url + "/merlin/PtzCtrl.cgi?operation=11&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 60) {  // 光圈 －
//            step(14, getPTZSpeed(para));
            response = request(url + "/merlin/PtzCtrl.cgi?operation=14&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            // 使用亮度、对比度、聚焦组合来模拟调节光圈
            if (brightness > 0) {
                brightness -= ((float) para / 200) * 10.0f;
            }
//            if (contrast < 100) {
//                contrast += ((float) para / 200) * 10.0f;
//            }
//            if (saturation < 100) {
//                saturation += ((float) para / 200) * 10.0f;
//            }
            request(url + "/merlin/PtzCtrl.cgi?operation=12&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 61) {  // 聚焦 ＋
//            step(11, getPTZSpeed(para));
            response = request(url + "/merlin/PtzCtrl.cgi?operation=11&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 62) {  // 聚焦 －
//            step(12, getPTZSpeed(para));
            response = request(url + "/merlin/PtzCtrl.cgi?operation=12&channelno=0&value=0&speed=" + getPTZSpeed(para), true); /////
            stopPTZ();
        } else if (cmd == 900) {  // 以比较慢的速度移动云台，内部使用，用于巡检
            new Thread(() -> {
                int group = (para - CHECK_LINE_START_PTZ_INDEX) / CHECK_LINE_PTZ_COUNT;
                int idx = (para - CHECK_LINE_START_PTZ_INDEX) % CHECK_LINE_PTZ_COUNT + 1;

                updateStatusText(String.format("巡检组 %d, 巡检位: %d", group, idx), false); /////
                SystemClock.sleep(PTZ_PRESET_MOVE_TIME);
                updateStatusText(getStatusText(), false); /////
            }).start();
            response = request(url + "/merlin/PtzCtrl.cgi?operation=20&speed=1&channelno=0&value=" + para, true); /////
        }
        try {
            Log.d(Log.TAG, String.format("摄像机调节: %d, %d, 结果: %s", cmd, para, response == null ? "失败" : response.body().string()));
        } catch (Exception e) {
        }
    }

    @Override
    protected void doSampleData(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo, int outIndex) { /////
        try {
            if (isRecording() && mediaMuxer != null) {
                byte[] pack = outputBuffer.array();
                byte[] frame = rtph264.rtpToNalu(pack, pack.length);
                if (frame != null && rtph264.sps != null && rtph264.pps != null) {
                    bufferInfo.size = frame.length;
                    bufferInfo.offset = 0;
                    bufferInfo.flags = 0;
                    bufferInfo.presentationTimeUs = rtph264.timestamp / 90 * 1000; // 微秒
                    if (rtph264.frameType(frame) == 6 || rtph264.frameType(frame) == 7 || rtph264.frameType(frame) == 8) {
                        bufferInfo.flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
                        // http://mykb.cipindanci.com/archive/SuperKB/4269/
                    } else if (rtph264.frameType(frame) == 1 || rtph264.frameType(frame) == 28)
                        bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;

                    if (!isMuxerInited) {
                        // 获取到sps pps才能初始化Muxer
                        initMuxer(rtph264.sps, rtph264.pps);
                    }
                    if (isMuxerInited) mediaMuxer.writeSampleData(videoTrackIndex, ByteBuffer.wrap(frame), bufferInfo); /////
                }
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "录制视频文件异常：" + e);
        }
    }

    /**
     * 设置字符叠加内容
     *
     * @param content
     */
    @Override
    public void updateStatusText(String content, boolean osdNull) { /////   false
        String[] ss = content.split("\n");
        if (ss == null || ss.length == 0 || osd.tag == 0) return;

        new Thread(() -> {
            getModeType();

            /////
            int start;
            int offset;
            if (osd.size == 2) {
                start = (isOldCamera.equals(true)) ? 805 : 620;  // 对应Size为2
                offset = (isOldCamera.equals(true)) ? 600 : 520;  // 对应Size为2s
            } else {
                start = (isOldCamera.equals(true)) ? 805 : 420;
                offset = (isOldCamera.equals(true)) ? 600 : 220;
            }



            if (!osdNull) {
                String body1 = String.format("{\"OSDCfg\":[{\"channel\":0,\"anticolor\":1,\"text2\":{\"align\":1," + "\"info\":[" +
                                "{\"enable\":1,\"rect\":{\"bottom\":8192,\"left\":200,\"right\":8192,\"top\":%d},\"string\":\"%s\"}," +
                                "{\"enable\":1,\"rect\":{\"bottom\":8192,\"left\":200,\"right\":8192,\"top\":%d},\"string\":\"%s\"}," +
                                "{\"enable\":1,\"rect\":{\"bottom\":8192,\"left\":200,\"right\":8192,\"top\":%d},\"string\":\"%s\"}]}}]}",
                        start + offset * 0, ss.length >= 1 ? ss[0] : "",
                        start + offset * 1, ss.length >= 2 ? ss[1] : "",
                        start + offset * 2, ss.length >= 3 ? ss[2] : ""
                );
                Response response = request(url + "/merlin/SetOsdCfg.cgi", body1);
                Log.d(Log.TAG, "OSD自定义文本设置非空: " + (response == null ? "失败" : response.message()));

            } else {
                String body2 = String.format("{\"OSDCfg\":[{\"channel\":0,\"anticolor\":1,\"text2\":{\"align\":1," + "\"info\":[" +
                                "{\"enable\":1,\"rect\":{\"bottom\":8192,\"left\":200,\"right\":8192,\"top\":%d},\"string\":\"%s\"}," +
                                "{\"enable\":1,\"rect\":{\"bottom\":8192,\"left\":200,\"right\":8192,\"top\":%d},\"string\":\"%s\"}," +
                                "{\"enable\":1,\"rect\":{\"bottom\":8192,\"left\":200,\"right\":8192,\"top\":%d},\"string\":\"%s\"}]}}]}",
                        start + offset * 0, "",
                        start + offset * 1, "",
                        start + offset * 2, ""
                );
                Response response = request(url + "/merlin/SetOsdCfg.cgi", body2);
                Log.d(Log.TAG, "OSD自定义文本设置为空: " + (response == null ? "失败" : response.message()));
            }
            /////
        }).start();
    }



    @Override
    public Settings.FileDir findFiles(int videoType, Settings.TimeRecord startTime, Settings.TimeRecord stopTime) {
        String para = String.format("/merlin/QueryRecord.cgi?begintime=%d%02d%02d-%02d%02d%02d&endtime=%d%02d%02d-%02d%02d%02d&cameraid=0&pic=0&type=1&stream=0",
                startTime.year + 2000, startTime.month, startTime.day, startTime.hour, startTime.minute, startTime.second,
                stopTime.year + 2000, stopTime.month, stopTime.day, stopTime.hour, stopTime.minute, stopTime.second);
        Response response = request(url + para, true); /////
        if (response == null) return new Settings.FileDir();

        try {
            Settings.FileDir ret = new Settings.FileDir();

            JSONObject json = JSON.parseObject(response.body().string());
            JSONArray files = json.getJSONArray("RecordList");
            if (files == null) return new Settings.FileDir();
            Date time;
            videoFiles.clear();
            long min = 253370736000000l; // 9999-01-01 00:00:00
            long max = 946656000000l;    // 2000-1-1 00:00:00
            for (int i = 0; i < files.size(); i++) {
                JSONObject file = files.getJSONObject(i);
                Settings.FileItem fileItem = new Settings.FileItem();
                fileItem.filename = String.valueOf(file.getIntValue("num"));

                time = stringToDateTime("yyyyMMdd-HHmmss", file.getString("st"));
                fileItem.begin = new Settings.TimeRecord(time.getTime());
                if (time.getTime() < min) min = time.getTime();

                time = stringToDateTime("yyyyMMdd-HHmmss", file.getString("et"));
                fileItem.end = new Settings.TimeRecord(time.getTime());
                if (time.getTime() > max) max = time.getTime();

                fileItem.type = 0;
                fileItem.size = file.getIntValue("l");
                videoFiles.add(fileItem);
            }
            ret.count = files.size();
            ret.type = videoType;
            ret.channel = 1;
            ret.begin = new Settings.TimeRecord(min);
            ret.end = new Settings.TimeRecord(max);
            return ret;
        } catch (Exception e) {
            Log.e(Log.TAG, String.format("列录像失败: (%s-%s) => %s", startTime.asString, stopTime.asString, e.getMessage()));
            return new Settings.FileDir();
        }
    }

    /////
    // 双光融合功能
    @Override
    public Bitmap takePhoto(int stream, int preset, String filename, boolean show, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert, byte imageStitch) { /////
        setState(DevState.PHOTOING);
        try {
            /////
            if ((preset != 0 && preset != recordPreset) || (preset != 0 && preset == recordPreset && isLiving())) {
//            if (preset != 0 && preset != recordPreset) {  // 如果拉流时转动了云台，再拍照，则不会转回拍照预置位，存在问题！
//            if (preset != 0) {
                move(2, preset);
                if (imageStitch == 0) {
                    SystemClock.sleep(PTZ_PRESET_MOVE_TIME);    // 等待到达预置位
                } else {
                    SystemClock.sleep(5 * 1000);  // 图像拼接时只需等待5秒
                }
            } else if (preset != 0) {
                move(2, preset);
                SystemClock.sleep(5000);  // 等待OSD配置生效
            } else {
                SystemClock.sleep(5000);  // 等待OSD配置生效
            }
            /////
            // 原始文件保存到其他目录进行图片处理，处理完成再拷贝到目的文件，防止在文件处理的过程中进行累积文件上传
            String tempfile = Environment.getExternalStorageDirectory() + "/" + FilenameUtils.getName(filename);

            boolean ok = false;
            for (int i = 0; i < 3; i++) {
                ok = download(url + "/snapshot_ch=1", tempfile);
                if (ok) break;
            }

            if (!ok) {
                clearState(DevState.PHOTOING);
                Log.i(Log.TAG, "云台抓拍错误");
                return null;
            /////
            } else {
                Log.i(Log.TAG, "云台抓拍成功");
            }
            /////

            long stamp = getTimestampFromFilename(tempfile);
            BitmapFactory.Options bitmapOption = new BitmapFactory.Options();
            bitmapOption.inMutable = true;
            Bitmap bitmap = BitmapFactory.decodeFile(tempfile, bitmapOption);
            /////
//            if (bitmap != null) {
//                bitmap = processPhoto(bitmap, stamp, preset, aps, alert);
//            }
            /////

            clearState(DevState.PHOTOING);
            return bitmap;
        } catch (Exception e) {
            Log.e(Log.TAG, "云台抓拍异常: " + e.getMessage());
        } finally {
            clearState(DevState.PHOTOING);
        }
        return null;
    }

    @Override
    public boolean imageFusion(int stream, int preset, boolean show, String filename, Bitmap image_rgb, int recordPreset) {
        return true;
    }
    /////
}
