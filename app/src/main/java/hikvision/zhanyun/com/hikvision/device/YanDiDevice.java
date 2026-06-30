package hikvision.zhanyun.com.hikvision.device;

import static lyh.Utils.PERIOD_DAY;
import static lyh.Utils.PERIOD_SECOND;
import static lyh.Utils.saveBitmapAsJPEG;
import static lyh.Utils.stringToDateTime;
import static lyh.Utils.stringToTimestamp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.os.SystemClock;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import hikvision.zhanyun.com.hikvision.MainActivity;
import hikvision.zhanyun.com.hikvision.RtspClient;
import hikvision.zhanyun.com.hikvision.Settings;
import hikvision.zhanyun.com.hikvision.device.Device;
import hikvision.zhanyun.com.hikvision.rto.RTPH264;
import hikvision.zhanyun.com.hikvision.utils.Log;
import lyh.Utils;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class YanDiDevice extends Device {
    private final String url;
    private boolean closeForce;
    private RtspClient rtspLiveClient;          // 直播 RTSP 客户端
    private RtspClient rtspPlaybackClient;
    private boolean isMuxerInited = false;
    private final long MIN_BOOTUP_MILISECONDS = 100 * PERIOD_SECOND;
    private final String platform;

    public YanDiDevice(int ID, Context context, String ipaddr, String user, String passwd, String platform) {
        super(ID, context, false); /////
        this.server = ipaddr;
        this.user = user;
        this.password = passwd;
        this.type = DEVICE_DVR_YANDI;
        this.url = String.format("http://%s/", ipaddr);
        this.platform = platform;
    }

    @Override
    public boolean takeVideo(final String filename, final int duration, int stream, boolean upload) {
        if (isRecording()) return false;
        // 录像优先级高于直播拉流
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
    @Override
    public String getSceneName(int presetNo) {
        if (sceneParameters == null) return "";

        String key = String.format("%d,%d", id, presetNo);
        Settings.SceneParameter sceneParameter = sceneParameters.get(key);
        if (sceneParameter == null || sceneParameter.enable == 0)
            return "";
        else
            return sceneParameter.name;
    }

    @Override
    public void setSceneName(int presetNo, String name) {

    }

    @Override
    public void startCruise(int group) {
    }

    @Override
    public void stopCruise() {
    }

    /**
     * 开始调用巡检
     *
     * @param checkGroup
     */
    @Override
    public void startCheckLine(Settings.CheckGroup checkGroup) {
    }

    @Override
    public void stopCheckLine() {
    }

    @Override
    public Settings.ChannelStatus getStatus(int stream) {
        Settings.ChannelStatus ret = new Settings.ChannelStatus();
        ret.channel = (byte) id;
        ret.stream = (byte) stream;
        ret.code = 0;
        ret.recording = (byte) (isRecording() ? 1 : 0);
        return ret;
    }

    @Override
    public boolean ptz3D(int StartingPointXCoordinate, int StartingPointYCoordinate, int AtTheEndOfXCoordinate, int AtTheEndOfCoordinate) {
        return true;
    }

    private Response request(String url) {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Connection", "close")
                .build();
        try {
            OkHttpClient httpClient = new OkHttpClient().newBuilder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS).build();
            return httpClient.newCall(request).execute();
        } catch (Exception e) {
            Log.i(Log.TAG, "设备请求失败: " + url + " => " + e.getMessage());
            return null;
        }
    }

    private Response request(String url, Map<String, String> attrs) {
        try {
            FormBody.Builder bodyBuilder = new FormBody.Builder();
            for (String key : attrs.keySet()) {
                bodyBuilder.add(key, attrs.get(key));
            }

            Request request = new Request.Builder().url(url)
                    .addHeader("Connection", "close")
                    .post(bodyBuilder.build())
                    .build();

            OkHttpClient httpClient = new OkHttpClient().newBuilder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS).build();
            return httpClient.newCall(request).execute();
        } catch (Exception e) {
            Log.e(Log.TAG, "设备POST失败: " + url + " => " + e);
            return null;
        }
    }

    private Response requestSpeed(String url, int cmd, int speed) {
        try {
            RequestBody requestBody = new FormBody.Builder()
                    .add("chn", "1")
                    .add("action", String.valueOf(cmd))
                    .add("speed", String.valueOf(speed))
                    .build();
            Request request = new Request.Builder().url(url)
                    .addHeader("Connection", "close")
                    .post(requestBody)
                    .build();

            OkHttpClient httpClient = new OkHttpClient().newBuilder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS).build();
            return httpClient.newCall(request).execute();
        } catch (Exception e) {
            Log.e(Log.TAG, "设备POST失败: " + url + " => " + e);
            return null;
        }
    }

    private Response requestPoint(String url, int cmd, int point) {
        try {
            RequestBody requestBody = new FormBody.Builder()
                    .add("chn", "1")
                    .add("action", String.valueOf(cmd))
                    .add("point", String.valueOf(point))
                    .build();
            Request request = new Request.Builder().url(url)
                    .addHeader("Connection", "close")
                    .post(requestBody)
                    .build();

            OkHttpClient httpClient = new OkHttpClient().newBuilder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS).build();
            return httpClient.newCall(request).execute();
        } catch (Exception e) {
            Log.e(Log.TAG, "设备POST失败: " + url + " => " + e);
            return null;
        }
    }

    private boolean download(String filename) {
        Response response = request(url + "action/request_snap_jpeg");
        if (response == null || response.code() != 200) {
            return false;
        }

        try {
            String pictureUrl = JSON.parseObject(response.body().string()).getString("url");
            response = request(pictureUrl);
            if (response == null || response.code() != 200) {
                return false;
            }

            ResponseBody body = response.body();
            FileOutputStream fos = new FileOutputStream(filename);
            fos.write(body.bytes());
            fos.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean takePhoto(int stream, int preset, boolean show, String filename, Bitmap pop, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert) {
        setState(DevState.PHOTOING);
        try {
            /////
            if ((preset != 0 && preset != recordPreset) || (preset != 0 && preset == recordPreset && isLiving())) {
//            if (preset != 0 && preset != recordPreset) {  // 如果拉流时转动了云台，再拍照，则不会转回拍照预置位，存在问题！
//            if (preset != 0) {
                ///
                if (!isLiving()) {
                    move(2, preset);
                    SystemClock.sleep(PTZ_PRESET_MOVE_TIME);    // 等待到达预置位30秒
                }
            } else if (preset != 0) {
                if (!isLiving()) {
                    move(2, preset);
                }
                ///
            }
            /////
            // 原始文件保存到其他目录进行图片处理，处理完成再拷贝到目的文件，防止在文件处理的过程中进行累积文件上传
            String tempfile = Environment.getExternalStorageDirectory() + "/" + FilenameUtils.getName(filename);

            boolean ok = false;
            for (int i = 0; i < 3; i++) {
                ok = download(tempfile);
                if (ok) break;
            }

            if (!ok) {
                clearState(DevState.PHOTOING);
                Log.i(Log.TAG, "机芯抓拍失败");
                return false;
            }

            long stamp = getTimestampFromFilename(tempfile);
            BitmapFactory.Options bitmapOption = new BitmapFactory.Options();
            bitmapOption.inMutable = true;
            Bitmap bitmap = BitmapFactory.decodeFile(tempfile, bitmapOption);
            if (bitmap != null) {
                bitmap = processPhoto(bitmap, stamp, preset, aps, alert);
                saveBitmapAsJPEG(bitmap, filename, 70);
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

    /*
        开始录制视频
     */
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
            rtph264 = null;
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

            String streamURI = "ch=0&sl=" + stream;
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

    @Override
    public boolean playbackStart(String start, String stop, int ssrc) {
        this.ssrcPlayback = ssrc;

        Date begin = stringToDateTime("yyyy-MM-dd-HH-mm-ss", start);
        Date end = stringToDateTime("yyyy-MM-dd-HH-mm-ss", stop);

        // RTSP播放地址如下：
        // rtsp://ip:554/id=1&type=0&record=yyyymmddhhnnss_yymmddhhnnss
        // id = 通道号, type=码流类型（目前不区分主辅码流），record表示开始和结束时间，回放URL也可以通过GetRtspPlayBackUrl.cgi获取
        String para = String.format("ch0_%d%02d%02d%02d%02d%02d_%d%02d%02d%02d%02d%02d.avi",
                begin.getYear() + 1900, begin.getMonth() + 1, begin.getDate(), begin.getHours(), begin.getMinutes(), begin.getSeconds(),
                end.getYear() + 1900, end.getMonth() + 1, end.getDate(), end.getHours(), end.getMinutes(), end.getSeconds()
        );
        rtph264 = new RTPH264(ssrc);
        if (rtspPlaybackClient != null) rtspPlaybackClient.stop();
        rtspPlaybackClient = new RtspClient(server, 554, user, password, "file=" + para, rtspPlaybackCallback);
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

    /**
     * 回放控制接口
     *
     * @param scale  0 = 暂停, 1 = 正常速度, >2, 4, 8, 16 表示 n 倍速快进, 1/2, 1/4, 1/8 表示慢速 n 倍
     * @param offset 0 从指定时间开头开始播放， > 0表示跳到指定位置后播放， 若暂停后，则 0 表示继续播放
     * @return
     */
    @Override
    public boolean playbackControl(int code, float scale, int offset) {
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
    public boolean isDeviceReady() {
        Response response = request(url + "action/getdev_cfg_form");
        if (response == null || response.code() != 200) return false;

        try {
            JSONObject jsonObject = JSON.parseObject(response.body().string());
            String device_name = jsonObject.getString("device_name");
            String version_number = jsonObject.getString("version_number");
            String device_type = jsonObject.getString("device_type");
            String device_serial_no = jsonObject.getString("device_serial_no");
            String leave_factory_date = jsonObject.getString("leave_factory_date");
            Log.i(Log.TAG, "机芯版本：" + version_number);
            return jsonObject.size() == 5;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean open(int stream, onOpenCallback cb, int timeoutSeconds, boolean waitSelfCheck, boolean video, boolean isRecordVideo) { ///
        long bootTime = System.currentTimeMillis();

        closeForce = false;
        setState(DevState.OPENING);
        do {
            if (isDeviceReady()) {
                if (waitSelfCheck) {
                    // 设备至少启动1分钟，太少有可能机芯或云台没有完成自检
                    long elapse = (System.currentTimeMillis() - bootTime);
                    if (MIN_BOOTUP_MILISECONDS - elapse > 0) SystemClock.sleep(MIN_BOOTUP_MILISECONDS - elapse);
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

    // 子类在这里继承实现显示 流量，电量等额外附加信息
    @Override
    public void updateStatusText(String s, boolean osdNull) { /////
        try {
            String[] text = s.split("\n");
            for (int i = 0; i < text.length; i++) {
                switch (i) {
                    case 0: request(url + "action/setdev_user_osd_form?logo=" + text[i] + "&area_id=1"); break;
                    case 1: request(url + "action/setdev_user_osd_form?logo=" + text[i] + "&area_id=2"); break;
                    case 2: request(url + "action/setdev_user_osd_form?logo=" + text[i] + "&area_id=4"); break;
                }
            }
            Log.i(Log.TAG, "更新OSD成功");
        } catch (Exception e) {
            Log.i(Log.TAG, "更新OSD异常：" + e);
        }
    }

    public boolean liveStop() {
        rtph264 = null;
        if (rtspLiveClient != null) {
            rtspLiveClient.stop();
            rtspLiveClient = null;
        }

        clearState(DevState.LIVING);
        return true;
    }
//    public abstract Settings.GeneralParameters getSceneParameters(byte[] dataDomain);
//    public abstract byte setSceneParameters(byte[] dataDomain);

    /**
     * 更新OSD状态文本，球机需要，自己处理绘制的，也不需要
     *
     * @param osd
     * @return
     */
    @Override
    public boolean setOSD(Settings.OSD osd, boolean osdNull) { /////
        try {
            this.osd = osd;
            Response response1 = request(url + "action/setdev_osd_form?sensor_enable=1&time_enable=" + osd.time
                        + "&env_enable=" + osd.tag + "&name_enable=" + osd.tag);
            Response response2 = request(url + "action/setdev_user_osd_form?logo=" + osd.text + "&area_id=3");
            if (response1 == null || response2 == null || response1.code() != 200 || response2.code() != 200) {
                return false;
            }
            Log.i(Log.TAG, "设置OSD成功");
            return true;
        } catch (Exception e) {
            Log.i(Log.TAG, "设置OSD异常：" + e);
            return false;
        }
    }

    @Override
    public Settings.OSD getOSD() {
        return this.osd;
    }

    @Override
    public boolean setCodec(Settings.VideoCodec codec) {
        return true;
    }

    /**
     * 启动直播，stream 为码流类型
     *
     * @param stream
     * @return
     */
    @Override
    public boolean liveStart(int stream, int ssrc) {
        this.ssrcLive = ssrc;
        this.streamType = stream;

        rtph264 = new RTPH264(ssrc);
        if (rtspLiveClient != null) rtspLiveClient.stop();

        rtspLiveClient = new RtspClient(server, 554, user, password, "ch=0&s1=" + stream, rtspLiveCallback);
        if (!rtspLiveClient.start(false)) {
            Log.e(Log.TAG, "云台拉流失败");
            return false;
        }
        setState(DevState.LIVING);// 置拉流位为高
        Log.d(Log.TAG, String.format("直播: %s@%s:%d", user, server, port));
        return true;
    }

    @Override
    protected boolean reboot() {
        return true;
    }

    /**
     * 开始记录云台运动轨迹
     *
     * @param group 需要记录的组号
     */
    @Override
    public void startRecordCheckLine(int group) {

    }

    /**
     * 停止记录云台运动估计
     *
     * @param group 待停止记录的组号
     */
    @Override
    public void stopRecordCheckLine(int group) {

    }

    @Override
    public boolean setTime(int dwYear, int dwMonth, int dwDay, int dwHour, int dwMinute, int dwSecond) {
        String params = String.format("devtime=%d-%02d-%02d %02d:%02d:%02d", dwYear, dwMonth, dwDay, dwHour, dwMinute, dwSecond);
        Response response =  request(url + "action/settimeinfo_form?" + params);
        if (response == null || response.code() != 200) {
            return false;
        } else {
            Log.i(Log.TAG, "球机时间同步成功");
            return true;
        }
    }

    @Override
    public Settings.CruiseGroup[] getCruise() {
        return null;
    }

    @Override
    public boolean setCruise(int cmd, int group, int index, int preset, int duration, int speed) {
        return true;
    }

    @Override
    public boolean setPhotoParam(Settings.PhotoConfig config) {
        return true;
    }

    @Override
    public boolean setRecordTimes(List<Settings.VideoTimeItem> list) {
        return true;
    }


    private List<Settings.FileItem> videoFiles = new ArrayList<>();
    /**
     * 查询录像文件数目
     * */
    @Override
    public Settings.FileDir findFiles(int type, final Settings.TimeRecord begin, final Settings.TimeRecord end) {
        final Settings.FileDir result = new Settings.FileDir();

        Log.i(Log.TAG, String.format("查询录像：%d-%02d-%02d %02d:%02d:%02d -- %d-%02d-%02d %02d:%02d:%02d",
                begin.year + 2000, begin.month, begin.day, begin.hour, begin.minute, begin.second,
                end.year + 2000, end.month, end.day, end.hour, end.minute, end.second));

        try {
            Date startTime = new Date(begin.year + 100, begin.month - 1, begin.day, begin.hour, begin.minute);
            Date endTime = new Date(end.year + 100, end.month - 1, end.day, end.hour, end.minute);
            result.type = type;
            result.channel = (byte) id;

            videoFiles.clear();
            while (startTime.before(endTime)) {
                String params = String.format("%d-%02d-%02d", startTime.getYear() + 1900, startTime.getMonth() + 1, startTime.getDate());
                Response response = request(url + "action/request_record_list?rectime=" + params);
                if (response != null && response.code() == 200) {
                    JSONObject results = JSON.parseObject(response.body().string());
                    JSONArray fileList = results.getJSONArray("data");
                    for (int i = 0; i < fileList.size(); i++) {
                        JSONObject file = fileList.getJSONObject(i);
                        Settings.FileItem fileItem = new Settings.FileItem();
                        fileItem.filename = file.getString("filename");
                        String[] timeArray = fileItem.filename.split("\\.")[0].split("_");
                        fileItem.begin = new Settings.TimeRecord(stringToTimestamp("yyyyMMddHHmmss", timeArray[1]));
                        fileItem.end = new Settings.TimeRecord(stringToTimestamp("yyyyMMddHHmmss", timeArray[2]));

                        Log.i(Log.TAG, "录像文件：" + fileItem.filename + ", " + fileItem.begin.asString + "--" + fileItem.end.asString);
                        videoFiles.add(fileItem);
                    }
                    result.count += fileList.size();
                }
                startTime = new Date(startTime.getTime() + PERIOD_DAY);
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "查询录像异常：" + e);
        }

        return result;
    }

    @Override
    public Settings.FileList listFile(int videoType, String startTime, String stopTime, int startNumb, int endNumb) {
        if (videoFiles.isEmpty()) {
            return super.listFile(videoType, startTime, stopTime, startNumb, endNumb);
        }

        Settings.FileList result = new Settings.FileList();
        result.channel = (byte) id;
        result.begin = Long.MAX_VALUE;
        result.end = 0;
        result.type = videoType;
        List<Settings.FileItem> files = new ArrayList<>();
        for (int i = startNumb; i <= endNumb; i++) {
            if (i >= videoFiles.size()) break;
            Settings.FileItem item = videoFiles.get(i);
            if (item.begin.timestamp < result.begin) result.begin = item.begin.timestamp;
            if (item.end.timestamp > result.end) result.end = item.end.timestamp;
            files.add(item);
        }
        result.files = files.toArray(new Settings.FileItem[0]);

        return result;
    }

    private Response step(String url, int cmd, int speed) {
        if (speed < 1 || speed > 99) speed = 50;

        requestSpeed(url, cmd, speed);
        SystemClock.sleep(speed * 200);
        return requestSpeed(this.url + "action/ptz_ctrl_form", 2, 0);
    }

    /**
     * 研迪机芯rtp码流在超高压不能直接播放，需要重新打包才可以播放
     * */
    @Override
    protected void onVideoFrame(byte[] rtp) {
        if (rtph264 == null || rtp == null) return;

        if (platform != null && platform.equals("183.47.15.148")) {
            controllerCallback.onFrame(this, rtp);
        } else {
            byte[] frame = rtph264.rtpToNalu(rtp, rtp.length);
            if (frame != null) {
                // 帧率通过网页修改不成功，拉出来的流还是30帧
                byte[][] rtps = rtph264.encode(frame, 96, 30);
                if (rtps != null) {
                    for (byte[] pack : rtps)
                        controllerCallback.onFrame(this, pack);
                }
            }
        }
    }

    // 指令和参数，参考南网规约 88H
    @Override
    public void move(int cmd, int para) {
        super.move(cmd, para);

        Response response = null;
        if (cmd == 0) {         // 未知指令
        } else if (cmd == 1) {     // 摄像机打开电源 NET_DVR_REMOTECONTROL_POWER_ON

        } else if (cmd == 2) {      // 转到指定预置位
            /*new Thread(() -> {
                try {
                    String s = getSceneName(para).trim();
                    updateStatusText(getStatusText() + "\n" + s);
                    SystemClock.sleep(PTZ_PRESET_MOVE_TIME);
                } catch (Exception e) {
                    Log.e(Log.TAG, "调用预置位时显示名字错误: " + e.getMessage());
                }
            }).start();*/
            response = requestPoint(url + "action/ptz_sensor_ctl_form", 18, normalizePresetForDevice(para));
        } else if (cmd == 3) {  // 上下左右步进
            response = step(url + "action/ptz_ctrl_form", 1, ptzStep);
        } else if (cmd == 4) {
            response = step(url + "action/ptz_ctrl_form", 3, ptzStep);
        } else if (cmd == 5) {
            response = step(url + "action/ptz_ctrl_form", 5, ptzStep);
        } else if (cmd == 6) {
            response = step(url + "action/ptz_ctrl_form", 7, ptzStep);
        } else if (cmd == 7) {  // 镜头变焦放大
            response = step(url + "action/ptz_sensor_ctl_form", 22, 1);
        } else if (cmd == 8) {  // 镜头变焦拉近
            response = step(url + "action/ptz_sensor_ctl_form", 20, 1);
        } else if (cmd == 9) {      // 设置预置点
            response = requestPoint(url + "action/ptz_sensor_ctl_form", 31, para);
        } else if (cmd == 10) {     // 关闭电源
        } else if (cmd == 11) {     // 光圈 + 1
        } else if (cmd == 12) {     // 光圈 - 1
        } else if (cmd == 13) {     // 聚焦 + 1
        } else if (cmd == 14) {     // 聚焦 - 1
        } else if (cmd == 15) {             // 开始巡航
            // 因为要求场景配置，所以采用自定义功能实现巡航
            startCruise(para);
        } else if (cmd == 16) {             // 停止巡航
            stopCruise();
        } else if (cmd == 17) {             // 打开辅助设备开关
        } else if (cmd == 18) {     // 关闭辅助开关
        } else if (cmd == 19) {     // 开始自动扫描
        } else if (cmd == 20) {     // 停止自动扫描
        } else if (cmd == 21) {     // 随机扫描
        } else if (cmd == 22) {             // 停止随机扫描
        } else if (cmd == 23) {             // 红外全开
        } else if (cmd == 24) {             // 红外半开
        } else if (cmd == 25) {             // 红外关闭
        } else if (cmd == 26) {             // 删除预置位
        } else if (cmd == 27) {             // 设置自动扫描左边界
        } else if (cmd == 28) {             // 设置自动扫描右边界
        } else if (cmd == 29) {             // 设置自动扫描速度
        } else if (cmd == 30) {             // 设置步长
            ptzStep = para;
        } else if (cmd == 31) {             // 开始巡检
        } else if (cmd == 32) {             // 停止巡检
        } else if (cmd == 48) {             // 停止云台转动
            response = requestSpeed(url + "action/ptz_ctrl_form", 2, 0);
        } else if (cmd == 49) {  // ↑
            response = requestSpeed(url + "action/ptz_ctrl_form", 1, para);
            stopPTZ();
        } else if (cmd == 50) {   // ↓
            response = requestSpeed(url + "action/ptz_ctrl_form", 3, para);
            stopPTZ();
        } else if (cmd == 51) {   // ←
            response = requestSpeed(url + "action/ptz_ctrl_form", 5, para);
            stopPTZ();
        } else if (cmd == 52) {  // →
            response = requestSpeed(url + "action/ptz_ctrl_form", 7, para);
            stopPTZ();
        } else if (cmd == 57) {  // ＋
            response = requestSpeed(url + "action/ptz_sensor_ctl_form", 22, para);
            stopPTZ();
        } else if (cmd == 58) {  // －
            response = requestSpeed(url + "action/ptz_sensor_ctl_form", 20, para);
            stopPTZ();
        }
        try {
            Log.d(Log.TAG, String.format("摄像机调节: %d, %d, 结果: %s", cmd, para, response == null ? "失败" : response.body().string()));
        } catch (Exception e) {
        }
    }

    /*private static TimerTask ptzStopRunning;
    @Override
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
        Utils.scheduleTask(ptzStopRunning, 400);
    }*/

    public void initMuxer(byte[] sps, byte[] pps) {
        if (sps == null || pps == null || isMuxerInited) return;
        Settings.VideoCodec vCodec = getVideoCodec(streamType);
        Point size = Settings.VideoCodec.getResolution(vCodec.resolution);

        // 写入编码数据之前需要配置视频头部信息(csd参数)，csd全称Codec-specific Data，对于H。264来说，
        // “csd-0”和"csd-1"分别对应sps和pps；对于AAC来说，"csd-0"对应ADTS。
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.x, size.y);
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, size.x * size.y);
        mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, 25);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
        mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
        videoTrackIndex = mediaMuxer.addTrack(mediaFormat); /////
        mediaMuxer.start();
        isMuxerInited = true;
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
                        bufferInfo.flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG | MediaCodec.BUFFER_FLAG_KEY_FRAME;
                        // http://mykb.cipindanci.com/archive/SuperKB/4269/
                        // 获取到sps pps才能初始化Muxer
                        initMuxer(rtph264.sps, rtph264.pps);
                    } /*else if (rtph264.frameType(frame) == 1 || rtph264.frameType(frame) == 28)
                        bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;*/

                    if (isMuxerInited) {
                        mediaMuxer.writeSampleData(videoTrackIndex, ByteBuffer.wrap(frame), bufferInfo); /////
                    }
                }
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "录制视频文件异常：" + e);
        }
    }

    @Override
    public Settings.VideoCodec getVideoCodec(int streamType) {
        return codec.get(String.valueOf(streamType));
    }

    /////
    // 双光融合功能
    @Override
    public boolean imageFusion(int stream, int preset, boolean show, String filename, Bitmap image_rgb, int recordPreset) {
        return true;
    }
    /////
}
