package hikvision.zhanyun.com.hikvision.device;

import static hikvision.zhanyun.com.hikvision.MainActivity.is6735;
import static hikvision.zhanyun.com.hikvision.MainActivity.isIRPhotoing;
import static hikvision.zhanyun.com.hikvision.MainActivity.isVLPhotoing;
import static lyh.Utils.PERIOD_HOUR;
import static lyh.Utils.su;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.zhjinrui.batcom.RS485Impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

import hikvision.zhanyun.com.hikvision.RtspClient;
import hikvision.zhanyun.com.hikvision.RtspClientCallback;
import hikvision.zhanyun.com.hikvision.Settings;
import hikvision.zhanyun.com.hikvision.bean.PTZPosition;
import hikvision.zhanyun.com.hikvision.rto.RTPH264;
import hikvision.zhanyun.com.hikvision.utils.Log;
import lyh.Utils;
import okhttp3.Response;


public class HuanYuDevice extends MyOnvifDevice {
    //      TODO 如果频繁登录会登录失败  登录后不登出，链接会保持2分钟，如果频繁登录，总的协议数量超过20了就不能再登录了
    private static final long SESSION_VALID_TIME = 2 * 60 * 1000;
    private static final int MAX_LOGIN_RETRY = 20;
    private static final int MAX_REAL_LENGTH = 61;        // 这个字段太长，会导致自定义的osd不显示，有点奇怪，查看机芯上提示，字符过长
    private static final long AUTO_FOCUS_DELAY = 5000; // 5秒延迟
    private static final long CMD2_BLOCK_DURATION = 20000;  // 20秒时间窗口
    final Base64.Decoder decoder = Base64.getDecoder();
    final Base64.Encoder encoder = Base64.getEncoder();
    private final AtomicLong playbackSession = new AtomicLong(0);
    private final String url;
    private List<Settings.FileItem> videoFiles = new ArrayList<>();
    private String HuanyuDeviceLog = "HuanyuDeviceLog";
    private long session;
    private long loginTime = 0;
    private int deviceId;
    private List<String> diyOsd = new ArrayList<>();
    private List<String> osdToArray = new ArrayList<>();


    // TODO 注意：这个设备当中的查询和设置的channel均设置为0才有正确结果
    // TODO 如果发现返回为false，并且session为0，大概念为没有登录机芯，出现前面的问题，建议在具体函数使用的时候加上login()
    private RecordRespParam.RecordItems recordList = new RecordRespParam.RecordItems();
    private byte brightness = 50;  // 记录模拟调节光圈的亮度
    private byte contrast = 50;
    private byte hue = 50;
    private byte saturation = 50;
    private byte sharpness = 50;

    private boolean toCheck = false; ///////
    private boolean useAudio; /////
    private volatile int loginRetryCount = 0;
    private volatile long lastLoginAttemptTime = 0;
    private Handler mFocusHandler = new Handler();
    private boolean mIsManualFocus = false;
    private Runnable mAutoFocusRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsManualFocus) {
                autofocus();
                mIsManualFocus = false;
            }
        }
    };
    private boolean isMoving = false;
    private long lastCmd2Time = 0;  // 记录符合条件的cmd==2的时间


    public HuanYuDevice(int ID, Context context, String ip, int port, String User, String pwd, boolean toCheck, boolean useAudio) {
        super(ID, context, ip, port, User, pwd, useAudio); /////
        this.drawOSD = false;
        this.type = DEVICE_DVR_HUANYU;
        this.url = String.format("http://%s/SDK/UNIV_API", ip);
        this.toCheck = toCheck; ///////
        this.useAudio = useAudio; /////
//        login();
    }

    // 子类内部也缓存了 useAudio，运行时切换时需要和父类状态保持一致。
    @Override
    public void setUseAudio(boolean useAudio) {
        super.setUseAudio(useAudio);
        this.useAudio = useAudio;
    }

    private static String UTCTimeFormat(final Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd@HH:mm:ss#");
        return format.format(date).replace("@", "T").replace("#", "Z");
    }

    public static String convertToUtcFormat(String localTime) throws DateTimeParseException {
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-M-d-H-m-s");
        LocalDateTime localDateTime = LocalDateTime.parse(localTime, inputFormatter);
        ZonedDateTime utcDateTime = localDateTime
                .atZone(ZoneId.of("UTC+8"))  // 标记为东八区时间
                .withZoneSameInstant(ZoneId.of("UTC"));  // 转换为UTC时间

        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        return utcDateTime.format(outputFormatter);
    }

    /**
     * 检查 eth0 网口是否开启（通过分析 ifconfig 输出）
     *
     * @return true - 网口已开启，false - 网口未开启
     */
    private boolean isEth0Enabled() {
        try {
            // 执行 ifconfig eth0 命令
            Process process = Runtime.getRuntime().exec("ifconfig eth0");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            boolean hasUpFlag = false;
            boolean hasRunningFlag = false;

            while ((line = reader.readLine()) != null) {
                // 第一行包含硬件信息，跳过
                if (line.contains("HWaddr")) {
                    continue;
                }

                // 检查是否包含 "UP" 标志
                if (line.contains(" UP ")) {
                    hasUpFlag = true;
                }

                // 检查是否包含 "RUNNING" 标志
                if (line.contains(" RUNNING ")) {
                    hasRunningFlag = true;
                }

                // 如果已经找到两个标志，可以提前退出
                if (hasUpFlag && hasRunningFlag) {
                    break;
                }
            }

            process.waitFor();
            reader.close();

            // 网口开启的条件：同时包含 UP 和 RUNNING 标志
            return hasUpFlag && hasRunningFlag;

        } catch (Exception e) {
            Log.e(Log.TAG, "检查网口状态失败: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    private boolean login() {

        if (isSessionValid() && isEth0Enabled()) {
            return true;
        }

        // 检查是否已经达到最大重试次数
        if (loginRetryCount >= MAX_LOGIN_RETRY) {
            long currentTime = System.currentTimeMillis();
            // 如果距离上次登录尝试已经超过2分钟，重置重试计数
            if (currentTime - lastLoginAttemptTime > SESSION_VALID_TIME) {
                loginRetryCount = 0;
            } else {
                Log.i(HuanyuDeviceLog, "已达到最大登录重试次数，请稍后再试");
                return false;
            }
        }

        lastLoginAttemptTime = System.currentTimeMillis();

        // 单次登录尝试中的重试次数
        int requestRetryCount = 0;
        final int MAX_REQUEST_RETRY = 2;  // 单次尝试中的最大请求重试次数

        while (requestRetryCount < MAX_REQUEST_RETRY) {
            try {
                int id = 1;
                RequestBody body = new RequestBody(id, 0, new Caller("rpc", "login"));
                body.params = new LogInParam(user, password, 0);

//                Log.i(HuanyuDeviceLog, String.format("登录请求第 %d 次尝试", requestRetryCount + 1));

                Response response = http_request(url, JSON.toJSONString(body));
                if (response == null) {
                    Log.e(HuanyuDeviceLog, "login::response is null");
                    return false;
                }

                if (response != null && response.code() == 200) {
                    JSONObject result = JSON.parseObject(response.body().string());
                    if (result.getInteger("id") == id &&
                            result.getBooleanValue("result") == true) {
                        session = result.getJSONObject("params").getLongValue("session");
                        loginTime = System.currentTimeMillis();
                        loginRetryCount = 0;  // 重置全局重试计数
                        Log.i(HuanyuDeviceLog, "登录成功");
                        return true;
                    } else {
                        // 服务器返回了响应，但是登录失败
                        Log.i(HuanyuDeviceLog, "服务器返回登录失败");
                        requestRetryCount++;

                        openShare();

                        // 如果不是最后一次尝试，可以稍微等待一下再重试
                        if (requestRetryCount < MAX_REQUEST_RETRY) {
                            Thread.sleep(1000); // 等待1秒再重试
                        }
                    }
                } else {
                    // HTTP请求失败
                    Log.i(HuanyuDeviceLog, String.format("HTTP请求失败，状态码：%s",
                            response != null ? response.code() : "null"));
                    openShare();
                    requestRetryCount++;

                    // 如果不是最后一次尝试，可以稍微等待一下再重试
                    if (requestRetryCount < MAX_REQUEST_RETRY) {
                        Thread.sleep(1000);
                    }
                }
            } catch (Exception e) {
                Log.i(HuanyuDeviceLog, "登录异常：" + e.getMessage());
                requestRetryCount++;

                try {
                    if (requestRetryCount < MAX_REQUEST_RETRY) {
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        loginRetryCount++;  // 增加全局重试计数
        Log.i(HuanyuDeviceLog, "登录失败，重试次数：" + loginRetryCount + "/" + MAX_LOGIN_RETRY);
        return false;
    }


    private void openShare() {

        for (int i = 0; i < 3; i++) {
            boolean errcode = RS485Impl.Instance().gpioOpenRJ45();
            Log.i(Log.TAG, String.format("RJ45上电%s", errcode ? "成功" : "失败"));
            if (errcode) {
                break;
            }
            SystemClock.sleep(500);
        }

        int exitCode = 0;
        for (int i = 0; i < 3; i++) {
            if (is6735)
                exitCode = su("busybox ifconfig eth0 0.0.0.0 up");
            else
                exitCode = su("ifconfig eth0 up");
            if (exitCode == 0) {
                break;
            }
        }

        Log.i(HuanyuDeviceLog, String.format("登录失败，尝试打开网口，打开Share%s", exitCode == 0 ? "成功" : "失败"));
    }

    /**
     * 检查session是否在有效期内
     */
    private boolean isSessionValid() {
        return session != 0 &&
                loginTime != 0 &&
                (System.currentTimeMillis() - loginTime) < SESSION_VALID_TIME;
    }


    private boolean logout() {
        int id = 2;

        RequestBody body = new RequestBody(id, session, new Caller("rpc", "logout"));
        try {
            Response response = http_request(url, JSON.toJSONString(body));


            if (response != null && response.code() == 200) {
                JSONObject result = JSON.parseObject(response.body().string());
                if (result.getIntValue("id") == id &&
                        result.getBooleanValue("result") == true &&
                        result.getLongValue("session") == session) {
                    loginTime = 0;
                    loginRetryCount = 0; // 登出时重置重试计数
                    return true;
                }
            }
        } catch (Exception e) {
            Log.i(HuanyuDeviceLog, "退出登录异常：" + e);
            return false;
        }
        Log.i(HuanyuDeviceLog, "退出登录失败");
        return false;
    }


    @Override
    public boolean close() {
        super.close();
        boolean ret = logout();
        // 设备被下电后，旧 session 不再可信；清掉缓存，下一次上电必须重新等待并登录。
        session = 0;
        loginTime = 0;
        loginRetryCount = 0;
        return ret;
    }


    private Date SystemToHuanYuTime(long timestamp) {
        return new Date(timestamp - 8 * PERIOD_HOUR);
    }


    private Date HuanYuToSystemTime(long timestamp) {
        return new Date(timestamp + 8 * PERIOD_HOUR);
    }


    @Override
    public Settings.FileDir findFiles(int videoType, Settings.TimeRecord startTime, Settings.TimeRecord stopTime) {
        int id = 4;
        Settings.FileDir fileDir = new Settings.FileDir();
        fileDir.begin = startTime;  // 起始时间固定，只需设置一次
        fileDir.end = stopTime;      // 结束时间固定，只需设置一次
        int retryCount = 0;          // 重试计数器
        int maxRetry = 3;            // 最大重试次数

        do {
            fileDir.count = 0;

            if (login()) {
                try {

                    RequestBody body = new RequestBody(id, session, new Caller("storage", "getRecordSearchCount"));
                    RecordReqParam param = new RecordReqParam(0);
                    Date start = SystemToHuanYuTime(startTime.timestamp);
                    Date stop = SystemToHuanYuTime(stopTime.timestamp);
                    param.recordSearchCount = new RecordTimeRegion(start, stop);
                    body.params = param;

                    Response response = http_request(url, JSON.toJSONString(body));
                    if (response != null && response.code() == 200) {
                        ThisRespBody resp = JSON.parseObject(response.body().string(), ThisRespBody.class);
                        if (resp.id == id && resp.result) {
                            RecordRespParam.RecordAmount recordAmount =
                                    JSONObject.parseObject(JSON.toJSONString(resp.params), RecordRespParam.RecordAmount.class);
                            fileDir.count = recordAmount.count;  // 赋值查询到的count
                        }
                    }
                } catch (Exception e) {
                    Log.i(HuanyuDeviceLog, "查询录像数目异常：" + e);
                }
            }

            if (fileDir.count != 0) {
                break;
            }

            retryCount++;
        } while (retryCount < maxRetry);

        return fileDir;
    }


    private String getRecordSearchCountJson(String startTime, String stopTime) {
        try {
            // 创建JSON对象
            JSONObject requestJson = new JSONObject();

            // 设置固定字段
            requestJson.put("session", session); // 假设session是固定值
            requestJson.put("id", deviceId);

            // 创建call对象
            JSONObject call = new JSONObject();
            call.put("service", "storage");
            call.put("method", "getRecordSearchCount");
            requestJson.put("call", call);

            // 创建params对象
            JSONObject params = new JSONObject();
            params.put("channel", 0); // 假设通道固定为0

            // 创建recordSearchCount对象
            JSONObject recordSearchCount = new JSONObject();
            // 转换时间格式：从"yyyy-MM-dd-HH-mm-ss"转换为"yyyy-MM-dd'T'HH:mm:ss'Z'"
            recordSearchCount.put("startTime", convertTimeFormat(startTime));
            recordSearchCount.put("stopTime", convertTimeFormat(stopTime));
            recordSearchCount.put("recordType", "Timing"); // 假设类型固定为Timing

            params.put("recordSearchCount", recordSearchCount);
            requestJson.put("params", params);

            return requestJson.toString();
        } catch (JSONException e) {
            Log.e(HuanyuDeviceLog, "Failed to create record search count JSON: " + e.getMessage());
            return "";
        }
    }


    private int parseRecordSearchCountRes(Response response) {

        if (response == null) {
            Log.e(HuanyuDeviceLog, "login::response is null");
            return 0;
        }

        try {
            String responseBody = response.body().string();
            Log.d(HuanyuDeviceLog, "parseRecordSearchCountRes: " + responseBody);

            JSONObject responseJson = JSONObject.parseObject(responseBody);

            // 检查结果是否成功
            if (responseJson.getBoolean("result")) {
                JSONObject params = responseJson.getJSONObject("params");
                // 返回count值
                return params.getInteger("count");
            } else {
                Log.e(HuanyuDeviceLog, "Record search count request failed");
                return 0;
            }
        } catch (Exception e) {
            Log.e(HuanyuDeviceLog, "Failed to parse record search count response: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 转换时间格式
     * 输入格式: yyyy-MM-dd-HH-mm-ss
     * 输出格式: yyyy-MM-dd'T'HH:mm:ss'Z'
     */
    private String convertTimeFormat(String originalTime) {
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            outputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            Date date = inputFormat.parse(originalTime);
            return outputFormat.format(date);
        } catch (ParseException e) {
            Log.e(HuanyuDeviceLog, "Failed to convert time format: " + e.getMessage());
            return originalTime.replace("-", "T").replace("-", ":") + "Z";
        }
    }

    private String getRecordItemJson(int startNumb, int endNumb, String startTime, String stopTime) {
        try {
            // 转换时间格式
            String formattedStartTime = convertTimeFormat(startTime);
            String formattedStopTime = convertTimeFormat(stopTime);

            // 构建JSON字符串
            return String.format(
                    "{\"session\":%d,\"id\":%d,\"call\":{\"service\":\"storage\",\"method\":\"getRecordItem\"},\"params\":{\"channel\":%d,\"recordSearchItem\":{\"startTime\":\"%s\",\"stopTime\":\"%s\",\"recordType\":\"Timing\",\"itemRange\":[%d,%d]}}}",
                    session,          // %d - session
                    deviceId,         // %d - deviceId
                    0,                // %d - channel
                    formattedStartTime, // %s - startTime
                    formattedStopTime,  // %s - stopTime
                    startNumb + 1,    // %d - itemRange[0]
                    endNumb + 1       // %d - itemRange[1]
            );
        } catch (Exception e) {
            Log.e(HuanyuDeviceLog, "Failed to create record item JSON: " + e.getMessage());
            return "";
        }
    }


    private List<Settings.FileItem> parseRecordItemRes(Response response) {

        List<Settings.FileItem> fileItems = new ArrayList<>();

        try {
            // 获取响应体
            String responseBody = response.body().string();

//            Log.e(HuanyuDeviceLog, "responseJson"+responseBody);

            JSONObject responseJson = JSONObject.parseObject(responseBody);

            // 检查结果是否成功
            if (responseJson.getBoolean("result")) {
                JSONObject params = responseJson.getJSONObject("params");
                com.alibaba.fastjson.JSONArray recordItems = params.getJSONArray("recordItem");
                Date time;
                // 遍历所有录像项目
                for (int i = 0; i < recordItems.size(); i++) {
                    JSONObject item = recordItems.getJSONObject(i);
                    Settings.FileItem fileItem = new Settings.FileItem();

                    // 填充文件信息
                    fileItem.filename = item.getString("recordName");
                    fileItem.size = item.getInteger("recordSize");
                    fileItem.type = 0;

                    long startTimeStamp = item.getLong("startTime") * 1000;
                    time = new Date(startTimeStamp);
                    fileItem.begin = new Settings.TimeRecord(time.getTime());
                    long stopTimeStamp = item.getLong("stopTime") * 1000;
                    time = new Date(stopTimeStamp);
                    fileItem.end = new Settings.TimeRecord(time.getTime());
                    fileItems.add(fileItem);
                }
            } else {
                Log.e(HuanyuDeviceLog, "Record item request failed");
            }
        } catch (Exception e) {
            Log.e(HuanyuDeviceLog, "Failed to parse record item response: " + e.getMessage());
        }
        return fileItems;
    }


    private long convertTimeToTimestamp(String timeStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            // 假设输入的时间字符串是UTC时间，根据实际情况调整时区
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = sdf.parse(timeStr);
            return date.getTime();
        } catch (ParseException e) {
            Log.e(HuanyuDeviceLog, "Failed to convert time string to timestamp: " + timeStr);
            return 0;
        }
    }


    @Override
    public Settings.FileList listFile(int videoType, String startTime, String stopTime, int startNumb, int endNumb) {
        Settings.FileList fileList = new Settings.FileList();
        fileList.type = videoType;


        Log.e(Log.TAG, "startTime");

        try {
            Response countResponse = http_request(url, getRecordSearchCountJson(startTime, stopTime));
            int totalCount = parseRecordSearchCountRes(countResponse);

            Log.i(HuanyuDeviceLog, "Total record count: " + totalCount);

            if (startNumb < 0 || endNumb < startNumb || endNumb > totalCount) {
                Log.e(HuanyuDeviceLog, "Invalid index range. startNumb: " + startNumb + ", endNumb: " + endNumb + ", total: " + totalCount);
                return fileList;
            }

            String itemRequestJson = getRecordItemJson(startNumb, endNumb, startTime, stopTime);   // 构建字符串


            Response itemResponse = http_request(url, itemRequestJson);

            List<Settings.FileItem> fileItems = parseRecordItemRes(itemResponse);


            fileList.files = fileItems.toArray(new Settings.FileItem[0]);
            fileList.channel = 1;
            fileList.begin = convertTimeToTimestamp(startTime);
            fileList.end = convertTimeToTimestamp(stopTime);

        } catch (Exception e) {
            Log.e(HuanyuDeviceLog, "Error in listFile: " + e.getMessage());
        }

        return fileList;
    }


    @Override
    public boolean playbackStart(String startS, String endS, int ssrc) {

        final long currentSession = playbackSession.incrementAndGet();

        this.ssrcPlayback = ssrc;

        String start_time = convertToUtcFormat(startS);
        String end_time = convertToUtcFormat(endS);
        String resource = String.format("Stream/Replay/101?starttime=%s&endtime=%s", start_time, end_time);

        rtph264 = new RTPH264(ssrc);

        if (rtspPlaybackClient != null) {
            try {
                rtspPlaybackClient.stop();
            } catch (Exception e) {
            }
        }

        rtspPlaybackClient = new RtspClient(server, 554, user, password, resource, new RtspClientCallback() {
                    @Override
                    public void onPacket(int channel, byte[] packet, int len) {

                        // 旧session直接丢弃
                        if (currentSession != playbackSession.get()) {
                            return;
                        }

                        if (channel != 0) {
                            return;
                        }

                        if (packet == null || len <= 12) {
                            return;
                        }

                        try {
                            final byte[] data = new byte[len];
                            System.arraycopy(packet, 0, data, 0, len);
                            data[8] = (byte) (ssrc >> 24);
                            data[9] = (byte) (ssrc >> 16);
                            data[10] = (byte) (ssrc >> 8);
                            data[11] = (byte) (ssrc);

                            onVideoFrame(data);

                        } catch (Exception e) {
                            Log.e(HuanyuDeviceLog, "回放RTP异常:" + e.getMessage());
                        }
                    }

                    @Override
                    public void onResponse(
                            List<String> headers,
                            byte[] body) {

                    }
                });

        if (!rtspPlaybackClient.start(useAudio)) {
            Log.e(HuanyuDeviceLog, "回放启动失败");
            return false;
        }

        setState(DevState.PLAYBACKING);

        Log.i(HuanyuDeviceLog, "回放启动成功 session=" + currentSession);

        return true;
    }


//    @Override
//    public boolean playbackStart(String startS, String endS, int ssrc) {
//        this.ssrcPlayback = ssrc;
//
//        String start_time = convertToUtcFormat(startS);
//        String end_time = convertToUtcFormat(endS);
//
//        String resource = String.format("Stream/Replay/101?starttime=%s&endtime=%s",start_time,end_time);
//
//        rtph264 = new RTPH264(ssrc);
//
//        if (rtspPlaybackClient != null) rtspPlaybackClient.stop();
//
//        rtspPlaybackClient = new RtspClient(server, 554,user, password, resource, rtspPlaybackCallback);
//
//        if (!rtspPlaybackClient.start(useAudio)) {
//            Log.e(HuanyuDeviceLog, "云台回放失败: " + resource);
//            return false;
//        }
//
//        setState(DevState.PLAYBACKING);
//        Log.i(HuanyuDeviceLog, "云台回放: " + resource);
//
//        return true;
//    }

    @Override
    public boolean playbackStop() {

        final RtspClient stoppingClient = rtspPlaybackClient;
        rtspPlaybackClient = null;

        // 旧session失效，先清状态，避免阻塞的TEARDOWN影响新回放。
        playbackSession.incrementAndGet();
        ssrcPlayback = 0;
        clearState(DevState.PLAYBACKING);

        Log.i(HuanyuDeviceLog, "停止回放 session=" + playbackSession.get());

        if (stoppingClient != null) {
            new Thread(() -> {
                try {
                    stoppingClient.stop();
                } catch (Exception e) {
                    Log.e(HuanyuDeviceLog, "停止回放异常:" + e.getMessage());
                }
            }, "huanyu-playback-stop").start();
        }
        return true;
    }


//    @Override
//    public boolean playbackStop() {
////        Log.d(HuanyuDeviceLog, "寰宇回放停止");
//        if (rtspPlaybackClient != null) rtspPlaybackClient.stop();
//        rtspPlaybackClient = null;
//        return true;
//    }

    // 2026-05-22 08:46:03.763 : HuanyuDeviceLog : 获取机芯的时间：2026-05-22 08:46:03
    // 2026-05-22 08:46:03.767 : HuanyuDeviceLog : 时间差值大于1分钟
    public void timeSync() {
        if (login()) {
            // 获取机芯时间
            String ptzTimeStr = getPTZTime();
            Log.e(HuanyuDeviceLog, "获取机芯的时间：" + ptzTimeStr);

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                long ptzTime = sdf.parse(ptzTimeStr).getTime();
                long currentTime = System.currentTimeMillis();
                long diffSeconds = Math.abs(currentTime - ptzTime) / 1000;

                // 插值大于60s的时候就重新校时

                if (diffSeconds > 60) {
                    Log.e(HuanyuDeviceLog, "时间差值大于1分钟");
                    Calendar now = Calendar.getInstance();
                    setTime(now.get(Calendar.YEAR),
                            now.get(Calendar.MONTH) + 1,
                            now.get(Calendar.DAY_OF_MONTH),
                            now.get(Calendar.HOUR_OF_DAY),
                            now.get(Calendar.MINUTE),
                            now.get(Calendar.SECOND));
                }
            } catch (Exception e) {
                Calendar now = Calendar.getInstance();
                setTime(now.get(Calendar.YEAR),
                        now.get(Calendar.MONTH) + 1,
                        now.get(Calendar.DAY_OF_MONTH),
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        now.get(Calendar.SECOND));
            }

        }

    }

    @Override
    public boolean takePhoto(int stream, int preset, boolean show, String filename, Bitmap pop, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert) {
        boolean useSmallPhotoOsd = false;
        try {
            setState(DevState.PHOTOING);

            if (login()) {
                if (preset != 0 || wait) {
                    move(2, preset);
                    SystemClock.sleep(20 * 1000);
                } else {
                    // 原有 else 分支
                }

                // 获取当前配置的分辨率
                Point imageSize = Settings.PhotoConfig.getImageSize(photoConfig.size);
                boolean is640x480 = (imageSize.x == 640 && imageSize.y == 480);
                boolean is704x288 = (imageSize.x == 704 && imageSize.y == 288);
                boolean is704x576 = (imageSize.x == 704 && imageSize.y == 576);
                boolean isSmallPhotoOsdSize = (imageSize.x == 176 && imageSize.y == 144)
                        || (imageSize.x == 352 && imageSize.y == 288)
                        || (imageSize.x == 320 && imageSize.y == 240);
                useSmallPhotoOsd = isSmallPhotoOsdSize;

                boolean downloadSuccess;
                if (is640x480) {
                    String customUrl = "http://192.168.200.11/cgi-bin/snapshot.cgi?width=640&height=480";
                    downloadSuccess = download(customUrl, filename, "admin", "admin888");
                } else if (is704x288) {
                    String customUrl = "http://192.168.200.11/cgi-bin/snapshot.cgi?width=704&height=288";
                    downloadSuccess = download(customUrl, filename, "admin", "admin888");
                } else if (is704x576) {
                    String customUrl = "http://192.168.200.11/cgi-bin/snapshot.cgi?width=704&height=576";
                    downloadSuccess = download(customUrl, filename, "admin", "admin888");
                } else if (isSmallPhotoOsdSize) {
                    String customUrl = String.format("http://192.168.200.11/cgi-bin/snapshot.cgi?width=%d&height=%d", imageSize.x, imageSize.y);
                    try {
                        setSnapOsdEnabled(false);
                        SystemClock.sleep(500);
                        downloadSuccess = download(customUrl, filename, "admin", "admin888");
                    } finally {
                        setSnapOsdEnabled(true);
                    }
                } else {
                    downloadSuccess = !snapURI.equals("") && download(snapURI, filename);
                }

                if (!downloadSuccess) {
                    clearState(DevState.PHOTOING);
                    return false;
                }

            } else {
                Log.e(HuanyuDeviceLog, "拍照的时候未正常登录机芯，拍照失败");
                return false;
            }

            File file = new File(filename);
            long stamp = getTimestampFromFilename(filename);
            BitmapFactory.Options bitmapOption = new BitmapFactory.Options();
            bitmapOption.inMutable = true;
            Bitmap bitmap = BitmapFactory.decodeFile(filename, bitmapOption);

            if (bitmap != null) {
                bitmap = processPhoto(bitmap, stamp, preset, aps, alert);
                if (useSmallPhotoOsd) {
                    drawSmallPhotoWatermark(bitmap);
                } else {
                    drawWatermark(bitmap);
                }
                controllerCallback.onFrame(bitmap);
                Utils.saveBitmapAsJPEG(bitmap, filename, 90);
                bitmap.recycle();
            }

            if (file.exists()) {
                clearState(DevState.PHOTOING);
                controllerCallback.onPhotoTaked(stamp, this.id, preset, filename);
                return true;
            }

        } catch (Exception e) {
            Log.e(HuanyuDeviceLog, "Onvif抓拍错误: " + e.getMessage());
            return false;
        } finally {
            clearState(DevState.PHOTOING);
        }
        return false;
    }

    private void drawSmallPhotoWatermark(Bitmap bitmap) {
        if (bitmap == null) return;

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(Math.max(9f, bitmap.getWidth() / 34f));
        paint.setFakeBoldText(true);

        float margin = Math.max(6f, bitmap.getWidth() / 48f);
        float lineHeight = paint.getTextSize() * 1.25f;
        float maxWidth = bitmap.getWidth() - margin * 2;
        float y = margin + paint.getTextSize();

        if (osd.time == 1) {
            drawSmallPhotoText(canvas, paint,
                    Utils.formatDateTime("yyyy-MM-dd HH:mm:ss", new Date()) + " " + getChineseWeekday(),
                    margin, y);
            y += lineHeight;
        }

        if (controllerCallback != null) {
            String status = controllerCallback.onStatusInfo();
            if (!TextUtils.isEmpty(status)) {
                String[] lines = status.split("\n");
                for (String line : lines) {
                    if (TextUtils.isEmpty(line)) continue;
                    drawSmallPhotoText(canvas, paint, line, margin, y);
                    y += lineHeight;
                    if (y > bitmap.getHeight() * 0.38f) break;
                }
            }
        }

        if (osd.tag == 1 && !TextUtils.isEmpty(osd.text)) {
            List<String> bottomLines = wrapSmallPhotoText(paint, osd.text, maxWidth);
            float bottomY = bitmap.getHeight() - margin - lineHeight * (bottomLines.size() - 1);
            bottomY = Math.max(y + lineHeight, bottomY);
            for (String line : bottomLines) {
                drawSmallPhotoText(canvas, paint, line, margin, bottomY);
                bottomY += lineHeight;
                if (bottomY > bitmap.getHeight() - margin) break;
            }
        }
    }

    private List<String> wrapSmallPhotoText(Paint paint, String text, float maxWidth) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        int length = text.length();
        while (start < length) {
            int count = paint.breakText(text, start, length, true, maxWidth, null);
            if (count <= 0) break;
            lines.add(text.substring(start, start + count));
            start += count;
            if (lines.size() >= 3) break;
        }
        return lines;
    }

    private void drawSmallPhotoText(Canvas canvas, Paint paint, String text, float x, float y) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.DKGRAY);
        canvas.drawText(text, x, y, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(0f);
        paint.setColor(Color.WHITE);
        canvas.drawText(text, x, y, paint);
    }

    private String getChineseWeekday() {
        switch (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY:
                return "星期一";
            case Calendar.TUESDAY:
                return "星期二";
            case Calendar.WEDNESDAY:
                return "星期三";
            case Calendar.THURSDAY:
                return "星期四";
            case Calendar.FRIDAY:
                return "星期五";
            case Calendar.SATURDAY:
                return "星期六";
            default:
                return "星期日";
        }
    }

    private boolean setSnapOsdEnabled(boolean enabled) {
        if (!login()) return false;

        String json = String.format("{\n" +
                "    \"session\": %d,\n" +
                "    \"id\": %d,\n" +
                "    \"call\": {\n" +
                "        \"service\": \"videoEnc\",\n" +
                "        \"method\": \"setOSDConfig\"\n" +
                "    },\n" +
                "    \"params\": {\n" +
                "        \"channel\": 0,\n" +
                "        \"table\": {\n" +
                "            \"enableSnapOSD\": %b\n" +
                "        }\n" +
                "    }\n" +
                "}", session, deviceId, enabled);

        Response response = http_request(url, json);
        return response != null && response.isSuccessful();
    }


//    @Override
//    public boolean takePhoto(int stream, int preset, boolean show, String filename, Bitmap pop, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert) {
//
//
//        try {
//            setState(DevState.PHOTOING);
//
//
//            if(login()){
//
//                if (preset != 0 || wait) {
//
//                    move(2, preset);
//
//                    SystemClock.sleep(20 * 1000);
//
//
//                } else {
//
//                }
//
//
//                if (snapURI.equals("") || download(snapURI, filename) == false) {  // 下载机芯图片到本地
//                    clearState(DevState.PHOTOING);
//                    return false;
//                }
//
//
//            } else {
//                Log.e(HuanyuDeviceLog,"拍照的时候未正常登录机芯，拍照失败");
//                return false;
//            }
//
//            File file = new File(filename);
//            long stamp = getTimestampFromFilename(filename);
//            BitmapFactory.Options bitmapOption = new BitmapFactory.Options();
//            bitmapOption.inMutable = true;
//            Bitmap bitmap = BitmapFactory.decodeFile(filename, bitmapOption);
//
//            if (bitmap != null) {
//                bitmap = processPhoto(bitmap, stamp, preset, aps, alert);
//                drawWatermark(bitmap);
//                controllerCallback.onFrame(bitmap);
//                Utils.saveBitmapAsJPEG(bitmap, filename, 90);
//                bitmap.recycle();
//            }
//
//            if (file.exists()) {
//                clearState(DevState.PHOTOING);
//                controllerCallback.onPhotoTaked(stamp, this.id, preset, filename);
//                return true;
//            }
//
//
//        } catch (Exception e) {
//            Log.e(HuanyuDeviceLog, "Onvif抓拍错误: " + e.getMessage());
//            return false;
//        } finally {
//            clearState(DevState.PHOTOING);
//        }
//        return false;
//    }

    // 给红外使用
    public boolean checkDeviceReadiness(int preset) {
        if (login()) {

            if (preset != 0) {

                move(2, preset);

                // 1. 检查设备是否就绪
                if (!waitForDeviceReady(20)) {
                    Log.e(HuanyuDeviceLog, "设备未就绪，拍照失败");
                    clearState(DevState.PHOTOING);
                    return false;
                }

                SystemClock.sleep(3000);

                // 2. 检查坐标状态（20秒内）
                boolean positionStable = waitForPositionStable(20);

                if (positionStable) {
                    // 坐标稳定，可以拍照
                    Log.e(HuanyuDeviceLog, "设备坐标稳定，开始拍照");
                } else {
                    // 坐标不稳定，拍照失败
                    Log.e(HuanyuDeviceLog, "设备坐标不稳定，拍照失败");
                    clearState(DevState.PHOTOING);
                    return false;
                }
            } else {
                // 预置位0的情况
                if (!waitForDeviceReady(20)) {
                    Log.e(HuanyuDeviceLog, "设备未就绪，拍照失败");
                    clearState(DevState.PHOTOING);
                    return false;
                }

                boolean positionStable = waitForPositionStable(20);

                if (positionStable) {
                    Log.e(HuanyuDeviceLog, "预置位0拍照，设备坐标稳定，开始拍照");
                } else {
                    Log.e(HuanyuDeviceLog, "预置位0拍照，设备坐标不稳定，拍照失败");
                    clearState(DevState.PHOTOING);
                    return false;
                }
            }
            return true;

        } else {
            Log.e(HuanyuDeviceLog, "拍照的时候未正常登录机芯，拍照失败");
            return false;
        }
    }

    // 给红外使用
    private boolean waitForPositionStable(int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        PTZPosition previousPosition = null;
        int positionStableCount = 0;
        final int REQUIRED_STABLE_COUNT = 3;
        long checkInterval = 1500;

        while (Math.abs(System.currentTimeMillis() - startTime) / 1000 < timeoutSeconds) {
            PTZPosition currentPosition = getCurrentPTZPosition();

            if (currentPosition == null) {
                Log.w(HuanyuDeviceLog, "获取坐标失败，重试...");
                SystemClock.sleep(500);
                continue;
            }

            if (previousPosition != null) {
                // 检查坐标稳定性
                boolean isPositionEqual = isPositionEqual(previousPosition, currentPosition);

                if (isPositionEqual) {
                    positionStableCount++;
                    if (positionStableCount >= REQUIRED_STABLE_COUNT) {
                        // 坐标稳定，可以拍照
//                        Log.i(HuanyuDeviceLog, "坐标已稳定，准备拍照");
                        return true;
                    }
                } else {
                    positionStableCount = 0;
                }

                // 调整检查间隔
                if (isPositionEqual) {
                    checkInterval = Math.min(checkInterval + 100, 1500);
                } else {
                    checkInterval = 1500;
                }
            }

            previousPosition = currentPosition;
            SystemClock.sleep(checkInterval);
        }

        // 超时后的处理
        Log.e(HuanyuDeviceLog, "等待坐标稳定超时，坐标仍未稳定");
        return false;
    }

    private PhotoReadiness waitForPositionAndFocusStable(int timeoutSeconds) {

        long startTime = System.currentTimeMillis();
        PTZPosition previousPosition = null;
        int positionStableCount = 0;
        int focusStableCount = 0;
        final int REQUIRED_STABLE_COUNT = 3;
        long checkInterval = 1500;

        boolean positionStable = false;
        boolean focusStable = false;


        while (Math.abs(System.currentTimeMillis() - startTime) / 1000 < timeoutSeconds) {
            PTZPosition currentPosition = getCurrentPTZPosition();


            if (currentPosition == null) {
                Log.w(HuanyuDeviceLog, "获取坐标失败，重试...");
                SystemClock.sleep(1000);
                continue;
            }

            if (previousPosition != null) {
                // 检查坐标稳定性
                boolean isPositionEqual = isPositionEqual(previousPosition, currentPosition);
                // 检查聚焦稳定性（只比较聚焦值）
                boolean isFocusEqual = Math.abs(previousPosition.FocusPos - currentPosition.FocusPos) <= 1;

                if (isPositionEqual) {
                    positionStableCount++;
                    if (positionStableCount >= REQUIRED_STABLE_COUNT) {
                        positionStable = true;
                    }
                } else {
                    positionStableCount = 0;
                    positionStable = false;
                }

                if (isFocusEqual) {
                    focusStableCount++;
                    if (focusStableCount >= REQUIRED_STABLE_COUNT) {
                        focusStable = true;
                    }
                } else {
                    focusStableCount = 0;
                    focusStable = false;
                }

                // 如果坐标和聚焦都稳定了，提前返回
                if (positionStable && focusStable) {
                    Log.i(HuanyuDeviceLog, "坐标和聚焦都已稳定，提前准备拍照");
                    return new PhotoReadiness(true, true);
                }

                // 调整检查间隔
                if (isPositionEqual && isFocusEqual) {
                    checkInterval = Math.min(checkInterval + 100, 1500);
                } else {
                    checkInterval = 1500;
                }
            }

            previousPosition = currentPosition;
            SystemClock.sleep(checkInterval);
        }

        // 超时后的处理
        if (positionStable) {
            Log.i(HuanyuDeviceLog, "超时但坐标已稳定，聚焦状态: " + (focusStable ? "稳定" : "不稳定"));
            return new PhotoReadiness(true, focusStable);
        } else {
            Log.e(HuanyuDeviceLog, "等待坐标稳定超时，坐标仍未稳定");
            return new PhotoReadiness(false, false);
        }

    }

    /**
     * 等待设备就绪
     */
    private boolean waitForDeviceReady(int timeoutSeconds) {
        long startTime = System.currentTimeMillis();

        while (Math.abs(System.currentTimeMillis() - startTime) / 1000 < timeoutSeconds) {
            if (isDeviceReady()) {
                Log.i(HuanyuDeviceLog, "设备已就绪");
                return true;
            }
            Log.i(HuanyuDeviceLog, "等待设备就绪...");
            SystemClock.sleep(1000);
        }

        Log.e(HuanyuDeviceLog, "等待设备就绪超时");
        return false;
    }

    /**
     * 获取当前PTZ坐标
     * {"id":2,"params":{"Action":0,"FocusPos":30973,"PanPos":0,"TiltPos":999,"ZoomPos":0},"result":true,"session":2013644384}
     */
    private PTZPosition getCurrentPTZPosition() {

        if (login()) {
            try {

                Response response = http_request(url, getPTZPosParamJson());

                if (response == null) {
                    Log.e(HuanyuDeviceLog, "login::response is null");
                    return null;
                }

                String responseBody = response.body().string();

                Log.e(HuanyuDeviceLog, "获取PTZ位置response: " + responseBody);

                JSONObject responseJson = JSONObject.parseObject(responseBody);
                if (responseJson == null) {
                    Log.e(HuanyuDeviceLog, "responseJson解析为null");
                    return null;
                }

                JSONObject paramsJson = responseJson.getJSONObject("params");
                if (paramsJson == null) {
                    Log.e(HuanyuDeviceLog, "paramsJson为null，响应中未找到params字段");
                    return null;
                }

                PTZPosition position = new PTZPosition();
                position = paramsJson.toJavaObject(PTZPosition.class);

                return position;

            } catch (Exception e) {
                Log.e(HuanyuDeviceLog, "获取PTZ位置失败: " + e.getMessage());   // 获取PTZ位置失败: closed
                return null;
            }
        } else {
            Log.e(HuanyuDeviceLog, "登录机芯失败");
            return null;
        }

    }

    // {"id":2,"params":{"Action":0,"FocusPos":30973,"PanPos":0,"TiltPos":999,"ZoomPos":0},"result":true,"session":2013644384}
    private boolean isPositionEqual(PTZPosition pos1, PTZPosition pos2) {
        if (pos1 == null || pos2 == null) return false;

        final int TOLERANCE = 0; // 允许的误差范围

        return Math.abs(pos1.PanPos - pos2.PanPos) <= TOLERANCE &&      // 水平参数
                Math.abs(pos1.TiltPos - pos2.TiltPos) <= TOLERANCE &&   // 垂直参数
                Math.abs(pos1.ZoomPos - pos2.ZoomPos) <= TOLERANCE;     // 变倍参数
    }

    // 获取当前坐标
    private String getPTZPosParamJson() {
        Map<String, Object> table = new HashMap<>();
        table.put("Action", 0);

        Map<String, Object> params = new HashMap<>();
        params.put("channel", 0);
        params.put("table", table);

        Map<String, Object> call = new HashMap<>();
        call.put("service", "ptz");
        call.put("method", "getPTZPosParam");


        Map<String, Object> request = new HashMap<>();
        request.put("session", session);
        request.put("id", deviceId);
        request.put("call", call);
        request.put("params", params);

        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    private String setPTZPosParamJson(int action, int panPos, int tiltPos, int zoomPos, int focusPos, int channel) {

        Map<String, Object> table = new HashMap<>();
        table.put("Action", action);
        table.put("PanPos", panPos);
        table.put("TiltPos", tiltPos);
        table.put("ZoomPos", zoomPos);
        table.put("FocusPos", focusPos);


        Map<String, Object> params = new HashMap<>();
        params.put("channel", channel);
        params.put("table", table);


        Map<String, Object> call = new HashMap<>();
        call.put("service", "ptz");
        call.put("method", "setPTZPosParam");


        Map<String, Object> request = new HashMap<>();
        request.put("session", session);
        request.put("id", deviceId);
        request.put("call", call);
        request.put("params", params);

        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    private String setPTPositionZeroJson(int channel) {

        Map<String, Object> params = new HashMap<>();
        params.put("channel", channel);

        Map<String, Object> call = new HashMap<>();
        call.put("service", "ptz");
        call.put("method", "setPTPositionZero");

        Map<String, Object> request = new HashMap<>();
        request.put("session", session);
        request.put("id", deviceId);
        request.put("call", call);
        request.put("params", params);

        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    private String getProgramInfoJson() {
        Map<String, Object> call = new HashMap<>();
        call.put("service", "program");
        call.put("method", "getInfo");

        Map<String, Object> request = new HashMap<>();
        request.put("session", session);
        request.put("id", deviceId);
        request.put("call", call);

        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    private String presetPointConfigJson() {

        Map<String, Object> call = new HashMap<>();
        call.put("service", "ptz");
        call.put("method", "getPresetConfig");

        Map<String, Object> request = new HashMap<>();
        request.put("session", session);
        request.put("id", deviceId);
        request.put("call", call);

        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    // 设备重启的json  delay 范围 [5,20]s
    private String restartDeviceJson(int delay) {
        Map<String, Object> call = new HashMap<>();
        call.put("service", "program");
        call.put("method", "reboot");

        Map<String, Object> params = new HashMap<>();
        params.put("delay", delay);


        Map<String, Object> request = new HashMap<>();
        request.put("session", session);
        request.put("id", deviceId);
        request.put("call", call);
        request.put("params", params);

        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    // 设备重启
    private boolean restartDevice(int delay) {

        Response response = http_request(url, restartDeviceJson(delay));

        try {
            String responseBody = response.body().string();
            Log.d(HuanyuDeviceLog, "设备重启返回的response: " + responseBody);

            JSONObject responseJson = JSONObject.parseObject(responseBody);
            if (responseJson == null) {
                Log.e(HuanyuDeviceLog, "responseJson解析为null");
                return false;
            }

            if (!responseJson.containsKey("result")) {
                Log.e(HuanyuDeviceLog, "响应中未找到result字段");
                return false;
            }

            // 返回result的布尔值
            return responseJson.getBoolean("result");

        } catch (IOException e) {
            Log.e(HuanyuDeviceLog, "读取响应体失败" + e);
        } catch (JSONException e) {
            Log.e(HuanyuDeviceLog, "解析JSON失败" + e);
        }

        return false;
    }

    // 获取时间json
    private String getTimeJson() {
        Map<String, Object> call = new HashMap<>();
        call.put("service", "program");
        call.put("method", "getTime");

        Map<String, Object> request = new HashMap<>();
        request.put("session", session);
        request.put("id", deviceId);
        request.put("call", call);
        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    private String getPTZTime() {
        if (login()) {
            Response response = http_request(url, getTimeJson());


            if (response == null || !response.isSuccessful()) {
                Log.e(HuanyuDeviceLog, "获取时间请求失败，响应为空或状态异常");
                return "";
            }

            try {
                String responseBody = response.body().string();
//                Log.d(HuanyuDeviceLog, "获取时间返回的response: " + responseBody);

                JSONObject responseJson = JSONObject.parseObject(responseBody);
                if (responseJson == null) {
                    Log.e(HuanyuDeviceLog, "responseJson解析为null");
                    return "null";
                }


                if (!responseJson.containsKey("result") || !responseJson.getBoolean("result")) {
                    Log.e(HuanyuDeviceLog, "获取时间失败，result为false");
                    return "null";
                }


                JSONObject paramsJson = responseJson.getJSONObject("params");
                if (paramsJson == null || !paramsJson.containsKey("time")) {
                    Log.e(HuanyuDeviceLog, "响应中未找到有效的time字段");
                    return "null";
                }

                String timeStr = paramsJson.getString("time");
//                return PTZTime.parse(timeStr);
                return timeStr;

            } catch (IOException e) {
                Log.e(HuanyuDeviceLog, "读取响应体失败: " + e.getMessage());
            } catch (JSONException e) {
                Log.e(HuanyuDeviceLog, "解析JSON失败: " + e.getMessage());
            }
            return "null";
        }
        return "null";
    }

    private String setTimeJson(String time, int timeZone) {
        Map<String, Object> call = new HashMap<>();
        call.put("service", "program");
        call.put("method", "setTime");

        Map<String, Object> params = new HashMap<>();
        params.put("time", time);
        params.put("timeZone", timeZone);

        Map<String, Object> request = new HashMap<>();
        request.put("session", session);
        request.put("id", deviceId);
        request.put("call", call);
        request.put("params", params);

        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    private String setConfigJson(int brightness, int contrast, int saturation, int sharpness, int hue, String scene, int channel) {

        // 先看看有没有对应的参数设置，如果没有，当需要设置黑白的时候，color = 0，设置饱和度为0
        Map<String, Object> videoColor = new HashMap<>();

        videoColor.put("brightness", brightness);
        videoColor.put("contrast", contrast);
        videoColor.put("saturation", saturation);
        videoColor.put("sharpness", sharpness);
        videoColor.put("hue", hue);

        Map<String, Object> auto = new HashMap<>();
        auto.put("videoColor", videoColor);

        Map<String, Object> table = new HashMap<>();

        table.put("scene", scene);
        table.put("auto", auto);

        Map<String, Object> params = new HashMap<>();

        params.put("channel", channel);
        params.put("table", table);

        Map<String, Object> call = new HashMap<>();

        call.put("service", "videoIn");
        call.put("method", "setConfig");

        Map<String, Object> body = new HashMap<>();

        body.put("call", call);
        body.put("params", params);
        body.put("id", deviceId);
        body.put("session", session);

        return JSON.toJSONString(body, SerializerFeature.PrettyFormat);
    }

    // 设置图像参数配置（亮度、对比度、色调、饱和度、锐度）
    private boolean setConfig(int brightness, int contrast, int saturation, int sharpness, int hue, String scene, int channel) {
        Response response = http_request(url, setConfigJson(brightness, contrast, saturation, sharpness, hue, scene, channel));

        if (response == null || !response.isSuccessful()) {
            Log.e(HuanyuDeviceLog, "设置失败");
            return false;
        }

        try {
            String responseBody = response.body().string();
            Log.d(HuanyuDeviceLog, "设置图像参数返回结果: " + responseBody);

            JSONObject responseJson = JSONObject.parseObject(responseBody);
            if (responseJson == null) {
                Log.e(HuanyuDeviceLog, "responseJson解析为null");
                return false;
            }

            if (!responseJson.containsKey("result")) {
                Log.e(HuanyuDeviceLog, "响应中未找到result字段");
                return false;
            }

            return responseJson.getBoolean("result");

        } catch (IOException e) {
            Log.e(HuanyuDeviceLog, "读取响应体失败: " + e.getMessage());
        } catch (JSONException e) {
            Log.e(HuanyuDeviceLog, "解析JSON失败: " + e.getMessage());
        }

        return false;
    }

    // 获取图像参数配置
    // 构造获取图像参数配置的请求JSON
    private String getImageConfigJson(int channel) {
        // 构建call对象
        Map<String, Object> call = new HashMap<>();
        call.put("service", "videoIn");
        call.put("method", "getConfig");

        // 构建params对象
        Map<String, Object> params = new HashMap<>();
        params.put("channel", channel);

        // 构建完整请求
        Map<String, Object> request = new HashMap<>();
        request.put("session", session);  // 复用现有session变量
        request.put("id", deviceId);      // 复用现有deviceId变量
        request.put("call", call);
        request.put("params", params);

        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    // 发送获取图像参数请求并解析响应  // 这个channel只能设备为0才有结果
    private String getImageConfig(int channel) {
        Response response = http_request(url, getImageConfigJson(channel));

        // 检查响应有效性
        if (response == null || !response.isSuccessful()) {
            Log.e(HuanyuDeviceLog, "获取图像配置请求失败，响应为空或状态异常");
            return null;
        }

        try {
            String responseBody = response.body().string();
            Log.d(HuanyuDeviceLog, "获取图像配置返回的response: " + responseBody);

            JSONObject responseJson = JSONObject.parseObject(responseBody);
            if (responseJson == null) {
                Log.e(HuanyuDeviceLog, "responseJson解析为null");
                return null;
            }

            // 验证result字段是否为true
            if (!responseJson.getBoolean("result")) {
                Log.e(HuanyuDeviceLog, "获取图像配置失败，result为false");
                return null;
            }

        } catch (IOException e) {
            Log.e(HuanyuDeviceLog, "读取响应体失败: " + e.getMessage());
        } catch (JSONException e) {
            Log.e(HuanyuDeviceLog, "解析JSON失败: " + e.getMessage());
        }

        return null;
    }

    // 这个设置图像参数配置的函数可
    private String buildColorImageConfigRequestJson(String mode, int channel) {
        Map<String, Object> daynight = new HashMap<>();
        daynight.put("mode", mode);

        Map<String, Object> auto = new HashMap<>();
        auto.put("dayAndNight", daynight);

        Map<String, Object> table = new HashMap<>();
        table.put("auto", auto);
        table.put("scene", "auto");


        Map<String, Object> params = new HashMap<>();
        params.put("channel", channel);
        params.put("table", table);

        Map<String, Object> call = new HashMap<>();
        call.put("service", "videoIn");
        call.put("method", "setConfig");

        Map<String, Object> request = new HashMap<>();
        request.put("session", session);
        request.put("id", deviceId);
        request.put("call", call);
        request.put("params", params);

        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    private String setColorImageConfigRequest(String mode, int channel) {
        Response response = http_request(url, buildColorImageConfigRequestJson(mode, channel));

        // 检查响应有效性
        if (response == null || !response.isSuccessful()) {
            Log.e(HuanyuDeviceLog, "获取图像配置请求失败，响应为空或状态异常");
            return null;
        }

        try {
            String responseBody = response.body().string();
            Log.d(HuanyuDeviceLog, "获取图像配置返回的response: " + responseBody);

            JSONObject responseJson = JSONObject.parseObject(responseBody);
            if (responseJson == null) {
                Log.e(HuanyuDeviceLog, "responseJson解析为null");
                return null;
            }

            // 验证result字段是否为true
            if (!responseJson.getBoolean("result")) {
                Log.e(HuanyuDeviceLog, "获取图像配置失败，result为false");
                return null;
            }

        } catch (IOException e) {
            Log.e(HuanyuDeviceLog, "读取响应体失败: " + e.getMessage());
        } catch (JSONException e) {
            Log.e(HuanyuDeviceLog, "解析JSON失败: " + e.getMessage());
        }

        return null;
    }

    public String photoNightJson() {

        return String.format("{\n" +
                "    \"session\": %d,\n" +
                "    \"id\": %d,\n" +
                "    \"call\": {\n" +
                "        \"service\": \"videoIn\",\n" +
                "        \"method\": \"setConfig\"\n" +
                "    },\n" +
                "    \"params\": {\n" +
                "        \"channel\": 0,\n" +
                "        \"table\": {\n" +
                "            \"scene\": \"auto\",\n" +
                "            \"auto\": {\n" +
                "                \"dayAndNight\": {\n" +
                "                    \"alarm\": {\n" +
                "                        \"actionType\": \"day\"\n" +
                "                    },\n" +
                "                    \"auto\": {\n" +
                "                        \"sensitivity\": 4\n" +
                "                    },\n" +
                "                    \"mode\": \"night\",\n" +
                "                    \"photosensitive\": {\n" +
                "                        \"sensitivity\": 4\n" +
                "                    },\n" +
                "                    \"smartIR\": {\n" +
                "                        \"enable\": false,\n" +
                "                        \"manual\": {\n" +
                "                            \"distanceLevel\": 50\n" +
                "                        },\n" +
                "                        \"mode\": \"auto\"\n" +
                "                    },\n" +
                "                    \"timing\": {\n" +
                "                        \"beginTime\": {\n" +
                "                            \"hour\": 7,\n" +
                "                            \"min\": 0,\n" +
                "                            \"second\": 0\n" +
                "                        },\n" +
                "                        \"endTime\": {\n" +
                "                            \"hour\": 18,\n" +
                "                            \"min\": 0,\n" +
                "                            \"second\": 0\n" +
                "                        }\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}", session, id);
    }

    public String photoDayJson() {
        return String.format("{\n" +
                "    \"session\": %d,\n" +
                "    \"id\": %d,\n" +
                "    \"call\": {\n" +
                "        \"service\": \"videoIn\",\n" +
                "        \"method\": \"setConfig\"\n" +
                "    },\n" +
                "    \"params\": {\n" +
                "        \"channel\": 0,\n" +
                "        \"table\": {\n" +
                "            \"scene\": \"auto\",\n" +
                "            \"auto\": {\n" +
                "                \"dayAndNight\": {\n" +
                "                    \"alarm\": {\n" +
                "                        \"actionType\": \"day\"\n" +
                "                    },\n" +
                "                    \"auto\": {\n" +
                "                        \"sensitivity\": 4\n" +
                "                    },\n" +
                "                    \"mode\": \"day\",\n" +
                "                    \"photosensitive\": {\n" +
                "                        \"sensitivity\": 4\n" +
                "                    },\n" +
                "                    \"smartIR\": {\n" +
                "                        \"enable\": false,\n" +
                "                        \"manual\": {\n" +
                "                            \"distanceLevel\": 50\n" +
                "                        },\n" +
                "                        \"mode\": \"auto\"\n" +
                "                    },\n" +
                "                    \"timing\": {\n" +
                "                        \"beginTime\": {\n" +
                "                            \"hour\": 7,\n" +
                "                            \"min\": 0,\n" +
                "                            \"second\": 0\n" +
                "                        },\n" +
                "                        \"endTime\": {\n" +
                "                            \"hour\": 18,\n" +
                "                            \"min\": 0,\n" +
                "                            \"second\": 0\n" +
                "                        }\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}", session, id);
    }

    @Override
    public boolean setPhotoParam(Settings.PhotoConfig v) {
        super.setPhotoParam(v);
        Log.e(HuanyuDeviceLog, "设置图像参数");

        // 根据颜色模式选择对应的JSON配置
        Log.e(HuanyuDeviceLog, "v.color" + v.color);
        String mode = v.color == 1 ? photoDayJson() : photoNightJson();

        // 构建图像参数配置JSON
        String photoParamJson = String.format("{\n" +
                "    \"session\": %d,\n" +
                "    \"id\": %d,\n" +
                "    \"call\": {\n" +
                "        \"service\": \"videoIn\",\n" +
                "        \"method\": \"setConfig\"\n" +
                "    },\n" +
                "    \"params\": {\n" +
                "        \"channel\": 0,\n" +
                "        \"table\": {\n" +
                "            \"scene\": \"auto\",\n" +
                "            \"auto\": {\n" +
                "                \"videoColor\": {\n" +
                "                    \"brightness\": %d,\n" +
                "                    \"contrast\": %d,\n" +
                "                    \"hue\": 50,\n" +
                "                    \"saturation\": %d,\n" +
                "                    \"sharpness\": 50\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}", session, id, v.brightness, v.contrast, v.saturation);

        if (login()) {
            // 发送两个请求并获取响应
            Response response_1 = http_request(url, mode);
            Response response_2 = http_request(url, photoParamJson);


            if (response_1 == null || response_2 == null) {
                Log.e(HuanyuDeviceLog, "login::response is null");
                return false;
            }


            // 标记两个响应的处理结果
            boolean isResponse1Success = false;
            boolean isResponse2Success = false;

            // 处理第一个响应
            if (response_1 != null && response_1.isSuccessful()) {
                try {
                    String responseBody = response_1.body().string();
                    Log.d(HuanyuDeviceLog, "第一个请求返回: " + responseBody);

                    JSONObject responseJson = JSONObject.parseObject(responseBody);
                    if (responseJson != null && responseJson.getBooleanValue("result")) {
                        isResponse1Success = true;
                        Log.d(HuanyuDeviceLog, "第一个请求处理成功");
                    } else {
                        Log.e(HuanyuDeviceLog, "第一个请求result为false或解析异常");
                    }
                } catch (IOException e) {
                    Log.e(HuanyuDeviceLog, "读取第一个响应体失败: " + e.getMessage());
                } catch (Exception e) {
                    Log.e(HuanyuDeviceLog, "解析第一个响应JSON失败: " + e.getMessage());
                } finally {
                    // 关闭响应体释放资源
                    if (response_1.body() != null) {
                        response_1.body().close();
                    }
                }
            } else {
                Log.e(HuanyuDeviceLog, "第一个请求响应为空或状态异常");
            }

            // 处理第二个响应
            if (response_2 != null && response_2.isSuccessful()) {
                try {
                    String responseBody = response_2.body().string();
                    Log.d(HuanyuDeviceLog, "第二个请求返回: " + responseBody);

                    JSONObject responseJson = JSONObject.parseObject(responseBody);
                    if (responseJson != null && responseJson.getBooleanValue("result")) {
                        isResponse2Success = true;
                        Log.d(HuanyuDeviceLog, "第二个请求处理成功");
                    } else {
                        Log.e(HuanyuDeviceLog, "第二个请求result为false或解析异常");
                    }
                } catch (IOException e) {
                    Log.e(HuanyuDeviceLog, "读取第二个响应体失败: " + e.getMessage());
                } catch (Exception e) {
                    Log.e(HuanyuDeviceLog, "解析第二个响应JSON失败: " + e.getMessage());
                } finally {
                    // 关闭响应体释放资源
                    if (response_2.body() != null) {
                        response_2.body().close();
                    }
                }
            } else {
                Log.e(HuanyuDeviceLog, "第二个请求响应为空或状态异常");
            }

            // 仅当两个响应都成功时返回true
            return isResponse1Success && isResponse2Success;
        } else {
            Log.i(HuanyuDeviceLog, "OSD设置失败，login false");
            return false;
        }
    }

    // 查询视频参数配置
    private String getConfigJson(int channel) {

        Map<String, Object> params = new HashMap<>();
        params.put("channel", channel);


        Map<String, Object> call = new HashMap<>();
        call.put("service", "videoEnc");
        call.put("method", "getConfig");

        Map<String, Object> request = new HashMap<>();
        request.put("session", session);
        request.put("id", deviceId);
        request.put("call", call);
        request.put("params", params);

        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    // 查询的结果
    private String getvideoConfig(int channel) {
        Response response = http_request(url, getConfigJson(channel));

        // 检查响应有效性
        if (response == null || !response.isSuccessful()) {
            Log.e(HuanyuDeviceLog, "获取视频配置请求失败，响应为空或状态异常");
            return null;
        }

        try {
            String responseBody = response.body().string();
            Log.d(HuanyuDeviceLog, "获取视频配置返回的response: " + responseBody);

            JSONObject responseJson = JSONObject.parseObject(responseBody);
            if (responseJson == null) {
                Log.e(HuanyuDeviceLog, "responseJson解析为null");
                return null;
            }


        } catch (IOException e) {
            Log.e(HuanyuDeviceLog, "读取响应体失败: " + e.getMessage());
        } catch (JSONException e) {
            Log.e(HuanyuDeviceLog, "解析JSON失败: " + e.getMessage());
        }

        return null;
    }

    @Override
    public boolean setCodec(Settings.VideoCodec v) {

        if (login()) {
            this.codec.put(String.valueOf(v.streamType), v);

            String stream = null;  // 设置到机芯的码流类型  ，x,y是设置到机芯的分辨力，size.x,size.y是下发和显示的分辨率（假的）
            Point size = Settings.VideoCodec.getResolution(v.resolution);
            int y = size.y;
            int x = size.x;

            if (v.streamType == 0) {
                stream = "main";
                // 比较配置的分辨率与(1280, 720), (1920, 1080)哪个近，哪个近就用哪个
                int[][] resolutions = {{1280, 720}, {1920, 1080}, {1280, 960}};
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
            } else if (v.streamType == 1) {    // {{252,288},{640,480},{704,576},{1280,720}}
                stream = "extra1";
                int[][] resolutions = {{252, 288}, {640, 480}, {704, 576}, {1280, 720}};
                double minDistance = Float.MAX_VALUE;
                for (int[] res : resolutions) {
                    float distance = (float) Math.sqrt(Math.pow(size.x - res[0], 2) + Math.pow(size.y - res[1], 2));
                    if (distance < minDistance) {
                        minDistance = distance;
                        y = res[1];
                        x = res[0];
                    }
                }
            } else if (v.streamType == 2) {       // {{352,288},{640,480},{704,576}}
                stream = "extra2";
                int[][] resolutions = {{352, 288}, {640, 480}, {704, 576}};
                double minDistance = Float.MAX_VALUE;
                for (int[] res : resolutions) {
                    float distance = (float) Math.sqrt(Math.pow(size.x - res[0], 2) + Math.pow(size.y - res[1], 2));
                    if (distance < minDistance) {
                        minDistance = distance;
                        y = res[1];
                        x = res[0];
                    }
                }
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


            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"videoEnc\",\n" +
                    "        \"method\": \"setConfig\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"stream\": \"%s\",\n" +
                    "        \"table\": {\n" +
                    "            \"encodeType\": {\n" +
                    "                \"h264\": {\n" +
                    "                    \"complexity\": \"high\",\n" +
                    "                    \"svc\": \"close\"\n" +
                    "                },\n" +
                    "                \"h265\": {\n" +
                    "                    \"complexity\": \"middle\"\n" +
                    "                },\n" +
                    "                \"type\": \"h264\"\n" +
                    "            },\n" +
                    "            \"fps\": %d,\n" +
                    "            \"gop\": %d,\n" +
                    "            \"rate\": {\n" +
                    "                \"size\": %d,\n" +
                    "                \"type\": \"%s\",\n" +  //variable
                    "                \"variable\": {\n" +
                    "                    \"quality\": 3\n" +
                    "                }\n" +
                    "            },\n" +
                    "            \"resolution\": \"%d*%d\",\n" +
                    "            \"smooth\": 50,\n" +
                    "            \"streamType\": \"videoandaudio\"\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, stream, v.frame > 25 ? 25 : v.frame, iFrame, v.bps, v.vbr == 0 ? "constant" : "variable", x, y);

            Log.e(HuanyuDeviceLog, "json" + json);

            Response response = http_request(url, json);

            // HTTP请求失败判断
            if (response == null) {
                Log.e(HuanyuDeviceLog, "HTTP request failed, response is null");
                return false;
            }

            if (!response.isSuccessful()) {
                Log.e(HuanyuDeviceLog, "HTTP request failed with code: " + response.code());
                return false;
            }


            try {
                String responseBody = response.body().string();
//            Log.d(HuanyuDeviceLog, "Response: " + responseBody);

                // 检查响应体是否为空
                if (TextUtils.isEmpty(responseBody)) {
                    Log.e(HuanyuDeviceLog, "Response body is empty");
                    return false;
                }

                JSONObject responseJson = JSONObject.parseObject(responseBody);
                if (responseJson == null) {
                    Log.e(HuanyuDeviceLog, "Failed to parse response JSON: null");
                    return false;
                }

                // 检查是否包含result字段
                if (!responseJson.containsKey("result")) {
                    Log.e(HuanyuDeviceLog, "Response missing 'result' field");
                    return false;
                }

                // 判断操作结果
                Boolean result = responseJson.getBoolean("result");
                if (result == null) {
                    Log.e(HuanyuDeviceLog, "Invalid 'result' field type");
                    return false;
                }

//                return true;
                if (result) {
                    // 操作成功
//                    updateCodec(v, size);    // TODO 这个地方导致的，创建新的分辨率会自动断流

                    Log.i(HuanyuDeviceLog, "视频参数设置成功");

                    return true;
                } else {
                    Log.e(HuanyuDeviceLog, "Set codec config failed");
                    return false;
                }

            } catch (IOException e) {
                Log.e(HuanyuDeviceLog, "IO error while reading response: " + e.getMessage());
                return false;
            } catch (Exception e) {
                Log.e(HuanyuDeviceLog, "Failed to process response: " + e.getMessage());
                return false;
            }
        }
        Log.e(HuanyuDeviceLog, "登录失败");
        return false;
    }

    @Override
    public Settings.VideoCodec getVideoCodec(int streamType) {
        try {
            Settings.VideoCodec vc = new Settings.VideoCodec();
            vc.resolution = this.codec.get(streamType).resolution;
            vc.vbr = (byte) this.codec.get(streamType).vbr;
            vc.bps = (short) this.codec.get(streamType).bps;
            vc.streamType = (byte) streamType;
            vc.channel = (byte) id;

            vc.iFrame = this.codec.get(streamType).iFrame;
            vc.frame = this.codec.get(streamType).frame;
            vc.frame = this.codec.get(streamType).codec;
            return vc;

        } catch (Exception e) {
            return super.getVideoCodec(streamType);
        }
    }

    /**
     * 更新编解码器配置
     */
    private void updateCodec(Settings.VideoCodec v, Point size) {
        procVideoHandler.post(() -> { /////
            boolean oldReadyState = codecReady;
            codecReady = false;
            try {
                if (mediaEncoder != null) {
                    // 先释放旧的编码器
                    uninitVideoEncoder(); /////
                    // 初始化新的编码器
                    initVideoEncoder(v.streamType, (int) size.x, (int) size.y, false); /////
                    Log.d(HuanyuDeviceLog, "Codec updated successfully for stream: " + v.streamType);
                }
            } finally {
                // 恢复就绪状态标记
                codecReady = oldReadyState;
            }
        });
    }

    public String convertToRecordPlanConfig(List<Settings.VideoTimeItem> videoTimeTable, long session, int deviceId) {
        // 构建基础JSON结构
        String template = "{\n" +
                "  \"session\": " + session + ",\n" +
                "  \"id\": " + deviceId + ",\n" +
                "  \"call\": {\n" +
                "    \"service\": \"storage\",\n" +
                "    \"method\": \"setRecordPlanConfig\"\n" +
                "  },\n" +
                "  \"params\": {\n" +
                "    \"channel\": 0,\n" +
                "    \"table\": {\n" +
                "      \"BrokenNetworkVideo\": true,\n" +
                "      \"EncType\": \"main\",\n" +
                "      \"PostRecordTime\": 5,\n" +
                "      \"PreRecordTime\": 0,\n" +
                "      \"alarmSchedule\": [%s],\n" +
                "      \"enable\": true,\n" +
                "      \"rangeCover\": true\n" +
                "    }\n" +
                "  }\n" +
                "}";

        // 构建每天的alarmSchedule
        StringBuilder weekSchedules = new StringBuilder();
        for (int day = 1; day <= 7; day++) {
            if (day > 1) weekSchedules.append(",");

            weekSchedules.append(String.format(
                    "{\n" +
                            "  \"timeArray\": [%s],\n" +
                            "  \"weekOfDay\": %d\n" +
                            "}",
                    buildTimeArray(videoTimeTable), day));
        }

        return String.format(template, weekSchedules.toString());
    }

    private String buildTimeArray(List<Settings.VideoTimeItem> videoTimeTable) {
        StringBuilder timeArrayBuilder = new StringBuilder();

        // 处理videoTimeTable中的每个时间段
        for (int i = 0; i < videoTimeTable.size() && i < 8; i++) {
            if (i > 0) timeArrayBuilder.append(",");

            Settings.VideoTimeItem timeItem = videoTimeTable.get(i);
            int startHour = timeItem.hour;
            int startMin = timeItem.min;
            int duration = timeItem.duration;

            // 计算结束时间
            int totalMinutes = startHour * 60 + startMin + duration / 60;
            int endHour = totalMinutes / 60;
            int endMin = totalMinutes % 60;

            // 处理跨天情况
            if (endHour >= 24) {
                endHour = endHour % 24;
            }

            timeArrayBuilder.append(String.format(
                    "{\n" +
                            "  \"beginTime\": {\n" +
                            "    \"hour\": %d,\n" +
                            "    \"min\": %d\n" +
                            "  },\n" +
                            "  \"endTime\": {\n" +
                            "    \"hour\": %d,\n" +
                            "    \"min\": %d\n" +
                            "  },\n" +
                            "  \"recordType\": \"Timing\"\n" +
                            "}",
                    startHour, startMin, endHour, endMin));
        }

        for (int i = videoTimeTable.size(); i < 8; i++) {
            timeArrayBuilder.append(",");
            timeArrayBuilder.append(
                    "{\n" +
                            "  \"beginTime\": {\n" +
                            "    \"hour\": 0,\n" +
                            "    \"min\": 0\n" +
                            "  },\n" +
                            "  \"endTime\": {\n" +
                            "    \"hour\": 0,\n" +
                            "    \"min\": 0\n" +
                            "  },\n" +
                            "  \"recordType\": \"Timing\"\n" +
                            "}");
        }

        return timeArrayBuilder.toString();
    }

    private String getRecordPlanJson(int channel, List<Settings.VideoTimeItem> list) {
        // 构建最外层请求对象
        Map<String, Object> request = new HashMap<>();
        request.put("session", session);
        request.put("id", deviceId);

        // 构建call节点
        Map<String, Object> call = new HashMap<>();
        call.put("service", "storage");
        call.put("method", "setRecordPlanConfig");
        request.put("call", call);

        // 构建params节点
        Map<String, Object> params = new HashMap<>();
        params.put("channel", channel);

        // 构建table节点
        Map<String, Object> table = new HashMap<>();
        table.put("BrokenNetworkVideo", true);
        table.put("EncType", "main");
        table.put("PostRecordTime", 5);
        table.put("PreRecordTime", 0);
        table.put("enable", true);
        table.put("rangeCover", true);

        // 构建alarmSchedule数组（一周7天配置）
        List<Map<String, Object>> alarmSchedule = new ArrayList<>();
        for (int weekDay = 1; weekDay <= 7; weekDay++) {
            Map<String, Object> dayConfig = new HashMap<>();
            dayConfig.put("weekOfDay", weekDay);


            List<Map<String, Object>> timeArray = new ArrayList<>();

            for (Settings.VideoTimeItem item : list) {
                channel = item.channel;
                // 计算录像时间范围
                int endSec = item.sec + item.duration % 60;
                int carryMin = endSec / 60;
                endSec %= 60;
                int endMin = item.min + (item.duration / 60) % 60 + carryMin;
                int carryHour = endMin / 60;
                endMin %= 60;
                int endHour = (item.hour + item.duration / 3600 + carryHour) % 24;
                // 第n个有效时间段
                timeArray.add(buildTimeItem(item.hour, item.min, endHour, endMin, "Timing"));
            }

            // 其余8-n个无效时间段
            for (int i = 1; i < 8 - list.size(); i++) {
                timeArray.add(buildTimeItem(0, 0, 0, 0, "Timing"));
            }

            dayConfig.put("timeArray", timeArray);
            alarmSchedule.add(dayConfig);
        }

        table.put("alarmSchedule", alarmSchedule);
        params.put("table", table);
        request.put("params", params);

        // 序列化并返回格式化的JSON字符串
        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    // 工具方法：构建单个时间片段配置
    private Map<String, Object> buildTimeItem(int beginHour, int beginMin,
                                              int endHour, int endMin, String recordType) {
        Map<String, Object> timeItem = new HashMap<>();

        Map<String, Integer> beginTime = new HashMap<>();
        beginTime.put("hour", beginHour);
        beginTime.put("min", beginMin);

        Map<String, Integer> endTime = new HashMap<>();
        endTime.put("hour", endHour);
        endTime.put("min", endMin);

        timeItem.put("beginTime", beginTime);
        timeItem.put("endTime", endTime);
        timeItem.put("recordType", recordType);

        return timeItem;
    }

    private String VideoTimeItemIsNone(long session, int deviceId) {
        String timeItem = "{\"beginTime\":{\"hour\":0,\"min\":0},\"endTime\":{\"hour\":0,\"min\":0},\"recordType\":\"Timing\"}";
        String timeArray = "[" + timeItem + "," + timeItem + "," + timeItem + "," + timeItem + "," + timeItem + "," + timeItem + "," + timeItem + "," + timeItem + "]";

        StringBuilder alarmSchedule = new StringBuilder();
        for (int week = 1; week <= 7; week++) {
            alarmSchedule.append(String.format("{\"timeArray\":%s,\"weekOfDay\":%d},", timeArray, week));
        }
        if (alarmSchedule.length() > 0) alarmSchedule.deleteCharAt(alarmSchedule.length() - 1);

        // 最终JSON
        return String.format("{" +
                "\"session\":%d," +
                "\"id\":%d," +
                "\"call\":{\"service\":\"storage\",\"method\":\"setRecordPlanConfig\"}," +
                "\"params\":{\"channel\":0,\"table\":{\"BrokenNetworkVideo\":true,\"EncType\":\"main\",\"PostRecordTime\":5,\"PreRecordTime\":0,\"alarmSchedule\":[%s],\"enable\":true,\"rangeCover\":true}}" +
                "}", session, deviceId, alarmSchedule);
    }


    //The requested profile token ProfileToken does not exist

    @Override
    public boolean setRecordTimes(List<Settings.VideoTimeItem> list) {
        Log.i(HuanyuDeviceLog, "设置定时录像============");

        if (login()) {

            String recordPlanJson;

            if (list == null || list.isEmpty()) {
                recordPlanJson = VideoTimeItemIsNone(session, id);
            } else {
                // 列表有数据，使用转换后的配置
                recordPlanJson = convertToRecordPlanConfig(list, session, id);
            }

            Response response = http_request(url, recordPlanJson);

            // 检查响应有效性
            if (response == null || !response.isSuccessful()) {
                Log.e(HuanyuDeviceLog, "获取录像策略请求失败，响应为空或状态异常");
                return false;
            }

            try {
                String responseBody = response.body().string();
                Log.d(HuanyuDeviceLog, "获取录像策略返回的response: " + responseBody);

                JSONObject responseJson = JSONObject.parseObject(responseBody);
                if (responseJson == null) {
                    Log.e(HuanyuDeviceLog, "responseJson解析为null");
                    return false;
                }

                // 验证result字段是否为true
                if (!responseJson.getBoolean("result")) {
                    Log.e(HuanyuDeviceLog, "获取录像策略失败，result为false");
                    return false;
                }

            } catch (IOException e) {
                Log.e(HuanyuDeviceLog, "读取响应体失败: " + e.getMessage());
            } catch (JSONException e) {
                Log.e(HuanyuDeviceLog, "解析JSON失败: " + e.getMessage());
            }

            return true;
        }

        return false;

    }

    private String buildGetTimeRequestJson() {
        Map<String, Object> call = new HashMap<>();
        call.put("service", "program");
        call.put("method", "getTime");

        // 构建完整请求（包含session、id和call）
        Map<String, Object> request = new HashMap<>();
        request.put("session", session);
        request.put("id", deviceId);
        request.put("call", call);

        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    private void getDeviceTime() {
        // 发送请求
        Response response = http_request(url, buildGetTimeRequestJson());

        // 检查响应有效性
        if (response == null || !response.isSuccessful()) {
            Log.e(HuanyuDeviceLog, "获取设备时间请求失败，响应为空或状态异常");
            return;
        }

        try {
            String responseBody = response.body().string();
            Log.d(HuanyuDeviceLog, "获取设备时间返回的response: " + responseBody);

            // 解析响应JSON
            JSONObject responseJson = JSONObject.parseObject(responseBody);
            if (responseJson == null) {
                Log.e(HuanyuDeviceLog, "responseJson解析为null");
                return;
            }

            // 验证result是否为true
            if (!responseJson.getBoolean("result")) {
                Log.e(HuanyuDeviceLog, "获取设备时间失败，result为false");
                return;
            }

            // 提取params中的time字段
            JSONObject params = responseJson.getJSONObject("params");
            if (params == null) {
                Log.e(HuanyuDeviceLog, "params字段为空");
                return;
            }
            String time = params.getString("time");
            if (time == null || time.isEmpty()) {
                Log.e(HuanyuDeviceLog, "time字段不存在或为空");
                return;
            }

            // time格式为"yyyy-MM-dd HH:mm:ss"
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(time);
            if (date == null) {
                Log.e(HuanyuDeviceLog, "time格式解析失败");
                return;
            }

            // 提取日期时间组件（注意Calendar的月份是0-based，需要+1）
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1;
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            int minute = calendar.get(Calendar.MINUTE);
            int second = calendar.get(Calendar.SECOND);


            Log.d(HuanyuDeviceLog, "解析后的时间变量：");
            Log.d(HuanyuDeviceLog, "年：" + year + "，月：" + month + "，日：" + day);
            Log.d(HuanyuDeviceLog, "时：" + hour + "，分：" + minute + "，秒：" + second);


        } catch (IOException e) {
            Log.e(HuanyuDeviceLog, "读取响应体失败: " + e.getMessage());
        } catch (JSONException e) {
            Log.e(HuanyuDeviceLog, "JSON解析失败: " + e.getMessage());
        } catch (ParseException e) {
            Log.e(HuanyuDeviceLog, "时间格式解析失败（格式应为yyyy-MM-dd HH:mm:ss）: " + e.getMessage());
        }
    }

    // 设置时间
    private String buildSetTimeRequestJson(int year, int month, int day,
                                           int hour, int minute, int second, int timeZone) {

        String time = String.format("%04d-%02d-%02d %02d:%02d:%02d",
                year, month, day, hour, minute, second);

        Map<String, Object> call = new HashMap<>();
        call.put("service", "program");
        call.put("method", "setTime");

        Map<String, Object> params = new HashMap<>();
        params.put("time", time);
        params.put("timeZone", timeZone);

        Map<String, Object> request = new HashMap<>();
        request.put("session", session);
        request.put("id", deviceId);
        request.put("call", call);
        request.put("params", params);

        return JSON.toJSONString(request, SerializerFeature.PrettyFormat);
    }

    @Override
    public boolean setTime(int year, int month, int day,
                           int hour, int minute, int second) {

        if (login()) {
            String requestJson = buildSetTimeRequestJson(year, month, day, hour, minute, second, 13);

//            Log.e(HuanyuDeviceLog, "进行时间的同步json："+requestJson);

            Response response = http_request(url, requestJson);

            if (response == null || !response.isSuccessful()) {
                Log.e(HuanyuDeviceLog, "设置设备时间请求失败，响应为空或状态异常");
                return false;
            }


            try {
                String responseBody = response.body().string();

                JSONObject responseJson = JSONObject.parseObject(responseBody);

//                Log.e(HuanyuDeviceLog, "时间同步response："+responseJson);

                if (responseJson == null) {
                    Log.e(HuanyuDeviceLog, "responseJson解析为null");
                    return false;
                }

                return responseJson.getBoolean("result");

            } catch (IOException e) {
                Log.e(HuanyuDeviceLog, "读取响应体失败: " + e.getMessage());
            } catch (JSONException e) {
                Log.e(HuanyuDeviceLog, "JSON解析失败: " + e.getMessage());
            }
            return true;
        }
        return false;
    }


    // {"session":1476701696,"id":2,"call":{"service":"program","method":"setTime"},"params":{"time":"2025-12-08 17:01:26","timeZone":13}}

    @Override
    public boolean imageFusion(int stream, int preset, boolean show, String filename, Bitmap image_rgb, int recordPreset) {
        return true;
    }

    public String createStatusTextOsd(long session, int id, Settings.OSD osd, String content) {
        diyOsd = Arrays.asList(content.split("\n"));

        osdToArray = Arrays.asList(truncateText(osd.text).split("\n"));

        int osdSize = osd.size;  //TODO 只有五种选择 16 32 48 64 自定义                     // osdsize = 1 的时候，间隔设置为21，osdsize = 2的时候，间隔设置为45

        int OSDInterval = osdSize == 1 ? 25 : 45;

//        int dateX = osdSize == 1 ? 21 : 20;
//        int dateY = osdSize == 1 ? 30 : 9;

        int dateX = 21;
        int dateY = 35;

        // 构建custom部分
        StringBuilder customBuilder = new StringBuilder();
        customBuilder.append("            \"custom\": [\n");

        // 总共8个custom项：前6个给diyOsd，后2个给osdToArray
        for (int i = 0; i < 8; i++) {
            if (i < 6) {
                // 前6行使用diyOsd
                if (diyOsd != null && i < diyOsd.size() && !diyOsd.get(i).isEmpty() && osd.tag == 1) {
                    String s = diyOsd.get(i);

//                    Log.e(HuanyuDeviceLog,"osd:" + s );

                    String encodedCustomText = Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
                    int rectY = 85 + i * OSDInterval; // 从55开始，每行间隔45
                    customBuilder.append("                {\n")
                            .append(String.format("                    \"data\": \"%s\",\n", encodedCustomText))
                            .append(String.format("                    \"enable\": %b,\n", (osd.tag == 1)))
                            .append("                    \"rect\": [\n")
                            .append("                        9,\n")
                            .append(String.format("                        %d,\n", rectY))
                            .append("                        0,\n")
                            .append("                        0\n")
                            .append("                    ]\n");
                } else {
                    // diyOsd为空或超出范围时创建空的custom项
                    customBuilder.append("                {\n")
                            .append("                    \"data\": \"\",\n")
                            .append(String.format("                    \"enable\": %b,\n", false))
                            .append("                    \"rect\": [\n")
                            .append("                        0,\n")
                            .append("                        0,\n")
                            .append("                        0,\n")
                            .append("                        0\n")
                            .append("                    ]\n");
                }
            } else {
                // 后2行使用osdToArray
                int osdToArrayIndex = i - 6; // 对应osdToArray的索引0和1
                if (osdToArray != null && osdToArrayIndex < osdToArray.size() && !osdToArray.get(osdToArrayIndex).isEmpty() && osd.tag == 1) {
                    String s = osdToArray.get(osdToArrayIndex);
                    String encodedCustomText = Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
//                    int rectY = 470 * getImageSize(settings.photoConfig.get(String.valueOf(1)).size).y / 512 + (i - 6) * OSDInterval; // 每行间隔49   // 818
                    int rectY = 900 + (i - 6) * OSDInterval;

                    customBuilder.append("                {\n")
                            .append(String.format("                    \"data\": \"%s\",\n", encodedCustomText))
                            .append(String.format("                    \"enable\": %b,\n", (osd.tag == 1)))
                            .append("                    \"rect\": [\n")
//                            .append(String.format("                        %d,\n", 6 + (i - 6))) // X坐标：第一行6，第二行7
                            .append(String.format("                        %d,\n", 9)) // X坐标：第一行6，第二行7
                            .append(String.format("                        %d,\n", rectY))
                            .append("                        0,\n")
                            .append("                        0\n")
                            .append("                    ]\n");
                } else {
                    // osdToArray为空或超出范围时创建空的custom项
                    customBuilder.append("                {\n")
                            .append("                    \"data\": \"\",\n")
                            .append(String.format("                    \"enable\": %b,\n", false))
                            .append("                    \"rect\": [\n")
                            .append("                        0,\n")
                            .append("                        0,\n")
                            .append("                        0,\n")
                            .append("                        0\n")
                            .append("                    ]\n");
                }
            }

            // 添加逗号或结束符
            if (i < 7) {
                customBuilder.append("                },\n");
            } else {
                customBuilder.append("                }\n");
            }
        }

        customBuilder.append("            ],\n");

        String jsonTemplate = "{\n" +
                "    \"session\": %d,\n" +
                "    \"id\": %d,\n" +
                "    \"call\": {\n" +
                "        \"service\": \"videoEnc\",\n" +
                "        \"method\": \"setOSDConfig\"\n" +
                "    },\n" +
                "    \"params\": {\n" +
                "        \"channel\": 0,\n" +
                "        \"table\": {\n" +
                "            \"channelTitle\": {\n" +
                "                \"data\": \"6YCa6YGT5LiA\",\n" +
                "                \"enable\": false,\n" +
                "                \"rect\": [\n" +
                "                    20,\n" +
                "                    880,\n" +
                "                    0,\n" +
                "                    0\n" +
                "                ]\n" +
                "            },\n" +
                "            \"cpuTemperatureDisp\": {\n" +
                "                \"enable\": false\n" +
                "            },\n" +
                "%s" + // 这里插入动态生成的custom部分
                "            \"enableSnapOSD\": true,\n" +
                "            \"photo\": {\n" +
                "                \"data\": \"\",\n" +
                "                \"enable\": false,\n" +
                "                \"rect\": [\n" +
                "                    0,\n" +
                "                    0,\n" +
                "                    0,\n" +
                "                    0\n" +
                "                ]\n" +
                "            },\n" +
                "            \"property\": {\n" +
                "                \"OSDAlgn\": {\n" +
                "                    \"value\": 1\n" +
                "                },\n" +
                "                \"OSDColor\": {\n" +
                "                    \"type\": 0,\n" +
                "                    \"value\": 65280\n" +
                "                },\n" +
                "                \"OSDFont\": %d,\n" +
                "                \"OSDProperty\": 3,\n" +
                "                \"dateFormat\": 0,\n" +
                "                \"timeFormat\": 0\n" +
                "            },\n" +
                "            \"temperatureDisp\": {\n" +
                "                \"enable\": false\n" +
                "            },\n" +
                "            \"temperatureDrift\": {\n" +
                "                \"enable\": false\n" +
                "            },\n" +
                "            \"thermalTemperatureDisp\": {\n" +
                "                \"enable\": false\n" +
                "            },\n" +
                "            \"timeTitle\": {\n" +
                "                \"enable\": %b,\n" +
                "                \"enableWeek\": %b,\n" +
                "                \"rect\": [\n" +
//                String.format("                    %d,\n",dateX) +
//                String.format("                    %d,\n",dateY) +
                "                    %d,\n" +
                "                    %d,\n" +
                "                    0,\n" +
                "                    0\n" +
                "                ]\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}";

        return String.format(jsonTemplate, session, id,
                customBuilder.toString(), osdSize - 1, (osd.time == 1), (osd.time == 1), dateX, dateY);
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
            length += isChineseChar(c) ? 2 : 1;
        }
        return length;
    }

    private String getSubstringByRealLength(String text, int startIndex, int maxRealLength) {
        if (text == null || startIndex >= text.length() || maxRealLength <= 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int currentRealLength = 0;

        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            int charRealLength = isChineseChar(c) ? 2 : 1;

            if (currentRealLength + charRealLength > maxRealLength) {
                break;
            }

            sb.append(c);
            currentRealLength += charRealLength;
        }
        return sb.toString();
    }

    public String truncateText(String newText) {
        if (newText == null || newText.isEmpty()) {
            return "";
        }

        int totalRealLength = calculateRealLength(newText);

        if (totalRealLength <= MAX_REAL_LENGTH) {
            return newText;
        }

        String firstLine = getSubstringByRealLength(newText, 0, MAX_REAL_LENGTH);
        int firstLineCharCount = firstLine.length();
        String secondLine = getSubstringByRealLength(newText, firstLineCharCount, MAX_REAL_LENGTH);

        String result = firstLine + "\n" + secondLine;
        Log.e("OSDTextUtils", "自定义的osd太长，已拆分换行。原真实长度：" + totalRealLength
                + "，处理后：" + result);

        return result;
    }

    @Override
    public boolean updateStatusText(Settings.OSD osd, String content, boolean osdNull) {
        if (login()) {
            String updateStatusTextosdJson = createStatusTextOsd(session, deviceId, osd, content);

            Response response = http_request(url, updateStatusTextosdJson);

            if (response != null && response.isSuccessful()) {
                try {
                    String responseBody = response.body().string();
                    JSONObject responseJson = JSONObject.parseObject(responseBody);

//                Log.e(HuanyuDeviceLog,"responseJson:"+responseJson);

                    if (responseJson == null) {
                        Log.e(HuanyuDeviceLog, "responseJson解析为null");
                        return false;
                    } else if (!responseJson.containsKey("result")) {
                        Log.e(HuanyuDeviceLog, "响应中未找到result字段");
                        return false;
                    }

                } catch (IOException e) {
                    Log.e(HuanyuDeviceLog, "读取响应体失败" + e);
                } catch (JSONException e) {
                    Log.e(HuanyuDeviceLog, "解析JSON失败" + e);
                }

                return true;

            } else {
                Log.i(HuanyuDeviceLog, "OSD设置失败，login false");
                return false;
            }
        } else {
            Log.e(HuanyuDeviceLog, "HTTP请求失败");
            return false;
        }
    }

    private void goPreset(int para) {
        goPreset(para, 0); // 初始重试次数为0
    }

    private void goPreset(int para, int retryCount) {
        if (login()) {
            String goPresetJson = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPresentParam\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"action\": \"run\",\n" +
                    "        \"preset\": {\n" +
                    "            \"presetNum\": %d,\n" +
                    "            \"presetName\": \"预置点%d\"\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, para, para);

            Response response = http_request(url, goPresetJson);

            if (response == null) {
                Log.e(HuanyuDeviceLog, "响应对象为null");
                return;
            }

            try {
                String responseBody = response.body().string();
                JSONObject responseJson = JSONObject.parseObject(responseBody);

//                Log.e(HuanyuDeviceLog, "调用预置位，机芯返回的结果:"+responseBody);

                if (responseJson == null) {
                    Log.e(HuanyuDeviceLog, "responseJson解析为null");
                    return;
                }

                if (responseJson.containsKey("result")) {
                    boolean result = responseJson.getBooleanValue("result");
                    if (!result && retryCount < 2) {

                        if ((retryCount + 1) == 1) {
                            Log.e(HuanyuDeviceLog, "第" + (retryCount + 1) + "次调用失败，请求JSON: " + goPresetJson);
                        }

                        Log.e(HuanyuDeviceLog, "完整响应结果: " + responseBody);
                        SystemClock.sleep(1000);
                        goPreset(para, retryCount + 1);
                        return;
                    } else if (!result && retryCount >= 2) {
                        Log.e(HuanyuDeviceLog, "已达到最大重试次数(3次)，仍然失败");
                        Log.e(HuanyuDeviceLog, "最终响应结果: " + responseBody);
                        return;
                    }
                } else {
                    Log.e(HuanyuDeviceLog, "响应中未找到result字段");
                }

            } catch (IOException e) {
                Log.e(HuanyuDeviceLog, "读取响应体失败" + e);
            } catch (JSONException e) {
                Log.e(HuanyuDeviceLog, "解析JSON失败" + e);
            }
        }
    }

    public void setPreset(int para) {
        if (login()) {
            String goPresetJson = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPresentParam\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"action\": \"set\",\n" +
                    "        \"preset\": {\n" +
                    "            \"presetNum\": %d,\n" +
                    "            \"presetName\": \"预置点%d\"\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, para, para);

            Response response = http_request(url, goPresetJson);

            if (response == null) {
                Log.e(HuanyuDeviceLog, "setPreset::response is null");
                return;
            }

            parseResponse(response);
        }
    }

    private void deletePreset(int presetNum) {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPresentParam\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"action\": \"clear\",\n" +
                    "        \"preset\": {\n" +
                    "            \"presetNum\": %d,\n" +
                    "            \"presetName\": \"预置点%d\"\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, presetNum, presetNum);

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    /**
     * 解析HTTP响应
     *
     * @param response HTTP响应对象
     * @return 解析是否成功，如果响应有效且包含result字段返回true，否则返回false
     */
    private boolean parseResponse(Response response) {
        if (response == null) {
            Log.e(HuanyuDeviceLog, "响应对象为null");
            return false;
        }

        try {
            String responseBody = response.body().string();
            JSONObject responseJson = JSONObject.parseObject(responseBody);


            if (responseJson == null) {
                Log.e(HuanyuDeviceLog, "responseJson解析为null");
                return false;
            } else if (!responseJson.containsKey("result")) {
                Log.e(HuanyuDeviceLog, "响应中未找到result字段");
                return false;
            }

            // 如果响应有效，返回result字段的值
            return responseJson.getBooleanValue("result");

        } catch (IOException e) {
            Log.e(HuanyuDeviceLog, "读取响应体失败" + e);
            return false;
        } catch (JSONException e) {
            Log.e(HuanyuDeviceLog, "解析JSON失败" + e);
            return false;
        }
    }

    private void right(int para) {
        if (login()) {
            String goPresetJson = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPTZCmd\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"continuousPanTiltSpace\": {\n" +
                    "            \"x\": %d,\n" +
                    "            \"y\": 0\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, getPTZSpeed(para));

            Response response = http_request(url, goPresetJson);
            parseResponse(response);
        }
    }

    private void left(int para) {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPTZCmd\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"continuousPanTiltSpace\": {\n" +
                    "            \"x\": -%d,\n" +
                    "            \"y\": 0\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, getPTZSpeed(para));

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void singleStepControl(int xSpeed, int ySpeed) {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPTZCmd\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"continuousPanTiltSpace\": {\n" +
                    "            \"x\": %d,\n" +
                    "            \"y\": %d\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, xSpeed, ySpeed);

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void up(int para) {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPTZCmd\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"continuousPanTiltSpace\": {\n" +
                    "            \"x\": 0,\n" +
                    "            \"y\": -%d\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, getPTZSpeed(para));

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void down(int para) {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPTZCmd\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"continuousPanTiltSpace\": {\n" +
                    "            \"x\": 0,\n" +
                    "            \"y\": %d\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, getPTZSpeed(para));

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void invalidateSession() {
        loginTime = 0;
    }

    private void zoomIn(int cmd) {
        if (login()) {
            int zSpend = 60;
            if (cmd == 7) {
                zSpend = 300;
            }


            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPTZCmd\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"continuousZoomSpace\": {\n" +
                    "            \"z\": %d\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, zSpend);

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void zoomOut(int cmd) {
        if (login()) {
            int zSpend = 60;
            if (cmd == 8) {
                zSpend = 300;
            }

            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPTZCmd\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"continuousZoomSpace\": {\n" +
                    "            \"z\": -%d\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, zSpend);

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void irisAdjust(int brigh, int contrast_, int saturation_) {
        if (login()) {
            String json = String.format("{\n" +
                    "  \"session\": %d,\n" +
                    "  \"id\": %d,\n" +
                    "  \"call\": {\n" +
                    "    \"service\": \"videoIn\",\n" +
                    "    \"method\": \"setConfig\"\n" +
                    "  },\n" +
                    "  \"params\": {\n" +
                    "    \"channel\": 0,\n" +
                    "    \"table\": {\n" +
                    "      \"scene\": \"auto\",\n" +
                    "      \"auto\": {\n" +
                    "        \"videoColor\": {\n" +
                    "          \"brightness\": %d,\n" +
                    "          \"contrast\": %d,\n" +
                    "          \"hue\": 50,\n" +
                    "          \"saturation\": %d,\n" +
                    "          \"sharpness\": 50\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}", session, id, brigh,contrast_,saturation_);

            //            Log.e(HuanyuDeviceLog,"json"+json);
            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void focusIncrease(int cmd) {
        if (login()) {
            int focusCtrl = 300;
            if (cmd == 13) {
                focusCtrl = 300;
            }

            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPTZCmd\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"focusCtrl\": {\n" +
                    "            \"focus\": %d\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, focusCtrl);

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void focusDecrease(int cmd) {
        if (login()) {
            int focusCtrl = 300;
            if (cmd == 14) {
                focusCtrl = 300;
            }

            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPTZCmd\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"focusCtrl\": {\n" +
                    "            \"focus\": -%d\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, focusCtrl);

            Response response = http_request(url, json);
            parseResponse(response);
        }

    }

    private String setVideoColor(int brightness, int contrast, int hue, int saturation, int sharpness) {

        Map<String, Object> videoColorMap = new HashMap<>();
        videoColorMap.put("brightness", brightness);  // 亮度
        videoColorMap.put("contrast", contrast);      // 对比度
        videoColorMap.put("hue", hue);                // 色调
        videoColorMap.put("saturation", saturation);  // 饱和度
        videoColorMap.put("sharpness", sharpness);    // 锐度


        Map<String, Object> autoMap = new HashMap<>();
        autoMap.put("videoColor", videoColorMap);


        Map<String, Object> tableMap = new HashMap<>();
        tableMap.put("scene", "auto");
        tableMap.put("auto", autoMap);


        Map<String, Object> paramsMap = new HashMap<>();
        paramsMap.put("channel", 0);
        paramsMap.put("table", tableMap);

        Map<String, Object> callMap = new HashMap<>();
        callMap.put("service", "videoIn");
        callMap.put("method", "setConfig");

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("session", session);
        requestMap.put("id", deviceId);
        requestMap.put("call", callMap);
        requestMap.put("params", paramsMap);


        return JSON.toJSONString(requestMap, SerializerFeature.PrettyFormat);
    }

    public int getPTZSpeed(int para) {
        if (para <= 15) {
            return 15;
        } else if (para <= 30) {
            return 30;
        } else if (para <= 45) {
            return 45;
        } else if (para <= 60) {
            return 60;
        } else if (para <= 75) {
            return 75;
        } else if (para <= 90) {
            return 90;
        } else {
            return 100;
        }
    }

    // stop问题
    private void stop() {
        int maxRetries = 3;
        boolean success = false;

        for (int i = 0; i < maxRetries; i++) {
            if (!login()) {
                Log.e(HuanyuDeviceLog, "stop() 第 " + (i + 1) + " 次重试：登录失败");
                if (i < maxRetries - 1) {
                    SystemClock.sleep(1000);
                }
                continue;
            }

            String json = String.format(
                    "{" +
                            "    \"session\": %d," +
                            "    \"id\": %d," +
                            "    \"call\": {" +
                            "        \"service\": \"ptz\"," +
                            "        \"method\": \"setPTZCmd\"" +
                            "    }," +
                            "    \"params\": {" +
                            "        \"channel\": 0," +
                            "        \"continuousPanTiltSpace\": {" +
                            "            \"x\": 0," +
                            "            \"y\": 0" +
                            "        }" +
                            "    }" +
                            "}", session, id);

            Response response = http_request(url, json);
            success = parseResponse(response);

            if (success) {
                isMoving = false;
                break;
            }

            // 命令失败，强制让 session 失效，下次循环重新登录
            Log.w(HuanyuDeviceLog, "stop() 第 " + (i + 1) + " 次重试：命令失败，强制重新登录");
            invalidateSession();

            if (i < maxRetries - 1) {
                SystemClock.sleep(1000);
            }
        }

        if (!success) {
            Log.e(HuanyuDeviceLog, "停止云台失败，已重试 " + maxRetries + " 次");
        }
    }

    @Override
    public void move(int cmd, int para) {
        super.move(cmd, para);


        // 处理cmd==2的情况
        if (cmd == 2 && toCheck && !isIRPhotoing && !isVLPhotoing) {
            lastCmd2Time = System.currentTimeMillis();  // 记录时间
        }

        // 检查是否在cmd==2后的20秒时间窗口内
        boolean isInCmd2Window = false;
        if (lastCmd2Time > 0) {
            long currentTime = System.currentTimeMillis();
            isInCmd2Window = (currentTime - lastCmd2Time) < CMD2_BLOCK_DURATION;
        }

        // 对于指令3,4,5,6,49,50,51,52，在抽检模式下：
        // 1. 如果正在拍照，直接退出
        // 2. 如果在cmd==2后的20秒内，直接退出
        if (toCheck && (cmd == 3 || cmd == 4 || cmd == 5 || cmd == 6 || cmd == 49 || cmd == 50 || cmd == 51 || cmd == 52)) {
            if ((isIRPhotoing || isVLPhotoing) || isInCmd2Window) {
                if (isIRPhotoing || isVLPhotoing) {
                    Log.e(HuanyuDeviceLog, "正在拍照，转动云台不生效");
                } else {
                    Log.e(HuanyuDeviceLog, "转动预置位后20秒内，转动云台不生效");
                }
                return;
            }
        }


        // 检查是否是手动对焦操作
        boolean isFocusOperation = (cmd == 13 || cmd == 14 || cmd == 61 || cmd == 62);

        if (isFocusOperation) {
            manualfocus(); // 确保切换到手动对焦

            // 执行具体的对焦操作
            if (cmd == 13 || cmd == 61) {
                focusIncrease(cmd);
            } else if (cmd == 14 || cmd == 62) {
                focusDecrease(cmd);
            }
        }

        // 原有的命令处理逻辑保持不变
        if (cmd == 0) {
            // 未知指令
        } else if (cmd == 1) {
            // 摄像机打开电源
        } else if (cmd == 2) {
            if (para != 0) {
                goPreset(para);
            }
        } else if (cmd == 3) {
            if (isMoving) {
                stop();
                SystemClock.sleep(500);
            }
            singleStepControl(0, 50);
            SystemClock.sleep(700);
            stop();
        } else if (cmd == 4) {
            if (isMoving) {
                stop();
                SystemClock.sleep(500);
            }
            singleStepControl(0, -50);
            SystemClock.sleep(700);
            stop();
        } else if (cmd == 5) {
            if (isMoving) {
                stop();
                SystemClock.sleep(500);
            }
            singleStepControl(-200, 0);
            SystemClock.sleep(700);
            stop();
        } else if (cmd == 6) {
            if (isMoving) {
                stop();
                SystemClock.sleep(500);
            }
            singleStepControl(200, 0);
            SystemClock.sleep(700);
            stop();
        } else if (cmd == 7) {
            zoomIn(cmd);
            SystemClock.sleep(1000);
            stop();
        } else if (cmd == 8) {
            zoomOut(cmd);
            SystemClock.sleep(1000);
            stop();
        } else if (cmd == 9) {
            if (para != 0) {
                setPreset(para);
            }
        } else if (cmd == 10) {
            // 关闭电源
        } else if (cmd == 11) {


            if (brightness < 100) {
                brightness += 10;
                if (brightness >=100){
                    brightness = 100;
                }
            }
            if (contrast > 0) {
                contrast -= 10;
                if (contrast <= 0){
                    contrast = 0;
                }
            }
            if (saturation > 0) {
                saturation -= 10;
                if (saturation <= 0){
                    saturation = 0;
                }
            }


            irisAdjust(brightness,contrast,saturation);
            SystemClock.sleep(1000);
            stop();
        } else if (cmd == 12) {

            if (brightness > 0) {
                brightness -= 10;
                if (brightness <= 0) {
                    brightness = 0;
                }
            }
            if (contrast < 100) {
                contrast += 10;
                if (contrast >= 100 ){
                    contrast = 100;
                }
            }
            if (saturation < 100) {
                saturation += 10;
                if (saturation >= 100){
                    saturation = 100;
                }
            }


            irisAdjust(brightness,contrast,saturation);
            SystemClock.sleep(1000);
            stop();
        } else if (cmd == 15) {
            startCruise(para);
        } else if (cmd == 16) {
            stopCruise();
        } else if (cmd == 17) {
            wiperOn();    // 摄像机调节: 17, 9, 雨刷
        } else if (cmd == 18) {
            wiperOff();
        } else if (cmd == 19) {
            // 开始自动扫描
            startLinearSweep();
        } else if (cmd == 20) {
            // 停止自动扫描
            stopLinearSweep();
        } else if (cmd == 21) {
            // 随机扫描
            startLinearSweep();
        } else if (cmd == 22) {
            // 停止随机扫描
            stopLinearSweep();
        } else if (cmd == 23) {
            // 红外全开
            turnOnPeripheralLight();
        } else if (cmd == 24) {
            // 红外半开
            turnOnPeripheralLight();
        } else if (cmd == 25) {
            // 红外关闭
            turnOffPeripheralLight();
        } else if (cmd == 26) {
            deletePreset(para);
        } else if (cmd == 27) {
            // 设置自动扫描左边界
            setLeftLimit();
        } else if (cmd == 28) {
            // 设置自动扫描右边界
            setRightLimit();
        } else if (cmd == 29) {
            ptz_speed = para;
        } else if (cmd == 30) {
            ptz_step = para;
        } else if (cmd == 31) {
            // 开始巡检
        } else if (cmd == 32) {
            // 停止巡检
        }
        ///////
        else if (cmd == 48) {
            stop();
        } else if (cmd == 49) {
            if (isMoving) {
                stop();
            } else {
                isMoving = true;
                down(para);
            }
        } else if (cmd == 50) {
            if (isMoving) {
                stop();
            } else {
                isMoving = true;
                up(para);
            }
        } else if (cmd == 51) {
            if (isMoving) {
                stop();
            } else {
                isMoving = true;
                left(para);
            }
        } else if (cmd == 52) {
            if (isMoving) {
                stop();
            } else {
                isMoving = true;
                right(para);
            }
        } else if (cmd == 53) {              // TODO 斜角转动的情况，还是会双轴转动
            if (toCheck) {
                singleStepControl(-200, 0);
                SystemClock.sleep(500);
                stop();
                singleStepControl(0, 200);
                SystemClock.sleep(500);
                stop();
            } else {
                topLeft(para);
            }
        } else if (cmd == 54) {
            if (toCheck) {
                singleStepControl(200, 0);
                SystemClock.sleep(500);
                stop();
                singleStepControl(0, 200);
                SystemClock.sleep(500);
                stop();
            } else {
                topRight(para);
            }
        } else if (cmd == 55) {
            if (toCheck) {
                singleStepControl(-200, 0);
                SystemClock.sleep(500);
                stop();
                singleStepControl(0, -200);
                SystemClock.sleep(500);
                stop();
            } else {
                bottomLeft(para);
            }
        } else if (cmd == 56) {
            if (toCheck) {
                singleStepControl(200, 0);
                SystemClock.sleep(500);
                stop();
                singleStepControl(0, -200);
                SystemClock.sleep(500);
                stop();
            } else {
                bottomRight(para);
            }
        } else if (cmd == 57) {
            zoomIn(cmd);
        } else if (cmd == 58) {
            zoomOut(cmd);
        } else if (cmd == 59) {
            if (brightness < 100) {
                brightness += ((float) para / 200) * 10.0f;
                if (brightness > 100) {
                    brightness = 100;
                }
            }

            if (contrast < 100) {
                contrast -= ((float) para / 200) * 10.0f;
                if (contrast <= 0){
                    contrast = 0;
                }
            }
            if (saturation < 100) {
                saturation -= ((float) para / 200) * 10.0f;
                if (saturation <= 0){
                    saturation = 0;
                }
            }


            irisAdjust(brightness,contrast,saturation);
        } else if (cmd == 60) {
            if (brightness > 0) {
                brightness -= ((float) para / 200) * 10.0f;
                if (brightness < 0) {
                    brightness = 0;
                }
            }

            if (contrast > 0) {
                contrast += ((float) para / 200) * 10.0f;
                if (contrast >= 100){
                    contrast = 100;
                }
            }
            if (saturation > 0) {
                saturation += ((float) para / 200) * 10.0f;
                if (saturation >= 100){
                    saturation = 100;
                }
            }

            irisAdjust(brightness,contrast,saturation);
        } else if (cmd == 900) {
            // 以比较慢的速度移动云台，内部使用，用于巡检
            new Thread(() -> {
                int group = (para - CHECK_LINE_START_PTZ_INDEX) / CHECK_LINE_PTZ_COUNT;
                int idx = (para - CHECK_LINE_START_PTZ_INDEX) % CHECK_LINE_PTZ_COUNT + 1;

                updateStatusText(osd, String.format("巡检组 %d, 巡检位: %d", group, idx), false);
                SystemClock.sleep(PTZ_PRESET_MOVE_TIME);
                updateStatusText(osd, getStatusText(), false);
            }).start();
            goPreset(para);
        }

        Log.d(HuanyuDeviceLog, String.format("摄像机调节: %d, %d", cmd, para));
    }


    /*
    * {"session":1342517504,"id":2,"call":{"service":"videoIn","method":"setConfig"},"params":{"channel":0,"table":{"scene":"auto","auto":{"videoColor":{"brightness":100,"contrast":50,"hue":50,"saturation":31,"sharpness":45}}}}}
    * */




    private void topLeft(int para) {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPTZCmd\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"continuousPanTiltSpace\": {\n" +
                    "            \"x\": -%d,\n" +
                    "            \"y\": -%d\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, getPTZSpeed(para), getPTZSpeed(para));

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void topRight(int para) {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPTZCmd\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"continuousPanTiltSpace\": {\n" +
                    "            \"x\": %d,\n" +
                    "            \"y\": -%d\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, getPTZSpeed(para), getPTZSpeed(para));

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void bottomLeft(int para) {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPTZCmd\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"continuousPanTiltSpace\": {\n" +
                    "            \"x\": -%d,\n" +
                    "            \"y\": %d\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, getPTZSpeed(para), getPTZSpeed(para));

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void bottomRight(int para) {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPTZCmd\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"channel\": 0,\n" +
                    "        \"continuousPanTiltSpace\": {\n" +
                    "            \"x\": %d,\n" +
                    "            \"y\": %d\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id, getPTZSpeed(para), getPTZSpeed(para));

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void wiperOn() {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setWiper\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"wiper\": \"on\"\n" +
                    "    }\n" +
                    "}", session, id);

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void wiperOff() {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setWiper\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"wiper\": \"off\"\n" +
                    "    }\n" +
                    "}", session, id);

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    /// sunwu
    private void startLinearSweep() {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setLinearSweep\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"action\": \"start\",\n" +
                    "        \"linearscan\": {\n" +
                    "            \"number\": 1,\n" +
                    "            \"speed\": 2,\n" +
                    "            \"leftstayTime\": 3,\n" +
                    "            \"rightstayTime\": 3\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id);

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void stopLinearSweep() {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setLinearSweep\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"action\": \"stop\",\n" +
                    "        \"linearscan\": {\n" +
                    "            \"number\": 1,\n" +
                    "            \"speed\": 2,\n" +
                    "            \"leftstayTime\": 3,\n" +
                    "            \"rightstayTime\": 3\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id);

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void turnOnPeripheralLight() {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPeripheralLight\"\n" +
                    "    },\n" +
                    "    \"params\": {\"light\": \"on\"}\n" +
                    "}", session, id);

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void turnOffPeripheralLight() {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setPeripheralLight\"\n" +
                    "    },\n" +
                    "    \"params\": {\"light\": \"off\"}\n" +
                    "}", session, id);

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void setLeftLimit() {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setLinearSweep\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"action\": \"leftLimit\",\n" +
                    "        \"linearscan\": {\n" +
                    "            \"number\": 1,\n" +
                    "            \"speed\": 2,\n" +
                    "            \"leftstayTime\": 3,\n" +
                    "            \"rightstayTime\": 3\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id);

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void setRightLimit() {
        if (login()) {
            String json = String.format("{\n" +
                    "    \"session\": %d,\n" +
                    "    \"id\": %d,\n" +
                    "    \"call\": {\n" +
                    "        \"service\": \"ptz\",\n" +
                    "        \"method\": \"setLinearSweep\"\n" +
                    "    },\n" +
                    "    \"params\": {\n" +
                    "        \"action\": \"rightLimit\",\n" +
                    "        \"linearscan\": {\n" +
                    "            \"number\": 1,\n" +
                    "            \"speed\": 2,\n" +
                    "            \"leftstayTime\": 3,\n" +
                    "            \"rightstayTime\": 3\n" +
                    "        }\n" +
                    "    }\n" +
                    "}", session, id);

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void manualfocus() {
        if (login()) {
            Log.e(HuanyuDeviceLog, "执行手动聚焦操作");

            String json = String.format("{\n" +
                    "  \"session\": %d,\n" +
                    "  \"id\": %d,\n" +
                    "  \"call\": {\n" +
                    "    \"service\": \"videoIn\",\n" +
                    "    \"method\": \"setConfig\"\n" +
                    "  },\n" +
                    "  \"params\": {\n" +
                    "    \"channel\": 0,\n" +
                    "    \"table\": {\n" +
                    "      \"scene\": \"auto\",\n" +
                    "      \"auto\": {\n" +
                    "        \"focus\": {\n" +
                    "          \"alg\": \"classical\",\n" +
                    "          \"initializeLens\": 1,\n" +
                    "          \"minFocusLength\": \"3.0m\",\n" +
                    "          \"mode\": \"%s\",\n" +
                    "          \"ratioLimit\": 40,\n" +
                    "          \"ratioShow\": 0,\n" +
                    "          \"sensitivity\": \"middle\"\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}", session, id, "manual");

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private void autofocus() {
        Log.e(HuanyuDeviceLog, "执行自动聚焦操作");

        if (login()) {
            String json = String.format("{\n" +
                    "  \"session\": %d,\n" +
                    "  \"id\": %d,\n" +
                    "  \"call\": {\n" +
                    "    \"service\": \"videoIn\",\n" +
                    "    \"method\": \"setConfig\"\n" +
                    "  },\n" +
                    "  \"params\": {\n" +
                    "    \"channel\": 0,\n" +
                    "    \"table\": {\n" +
                    "      \"scene\": \"auto\",\n" +
                    "      \"auto\": {\n" +
                    "        \"focus\": {\n" +
                    "          \"alg\": \"classical\",\n" +
                    "          \"initializeLens\": 1,\n" +
                    "          \"minFocusLength\": \"3.0m\",\n" +
                    "          \"mode\": \"%s\",\n" +
                    "          \"ratioLimit\": 40,\n" +
                    "          \"ratioShow\": 0,\n" +
                    "          \"sensitivity\": \"middle\"\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}", session, id, "auto");

            Response response = http_request(url, json);
            parseResponse(response);
        }
    }

    private static class PhotoReadiness {
        boolean positionStable;
        boolean focusStable;

        PhotoReadiness(boolean positionStable, boolean focusStable) {
            this.positionStable = positionStable;
            this.focusStable = focusStable;
        }
    }

    public static class RequestBody {
        public final int id;
        public final Caller call;
        public final long session;
        public Object params;

        public RequestBody(int id, long session, Caller caller) {
            this.id = id;
            this.call = caller;
            this.session = session;
        }
    }

    public static class ThisRespBody {
        public int id;
        public boolean result;
        public long session;
        public Object params;
    }

    public static class Caller {
        public final String service;
        public final String method;

        public Caller(String service, String method) {
            this.service = service;
            this.method = method;
        }
    }

    public static class LogInParam {
        public final String userName;
        public final String password;
        public final String random;
        public final int encryptType;

        public LogInParam(String user, String password, int encryptType) {
            this.userName = user;
            this.password = "e9707aeb6479958a00da5feafffa89c1";
            this.random = "123456";
            this.encryptType = encryptType;
        }
    }

    public static class OSDParam {
        public int channel = 0;
        public int osdDispType;
        public Tables table = new Tables();

        public static class Tables {
            public TimeLine timeTitle;
            public TextLine channelTitle;
            public Property property;
            public ArrayList<TextLine> custom = new ArrayList<>();
        }

        public static class TextLine {
            public boolean enable;
            public String data;
            public int[] rect;
        }

        public static class TimeLine {
            public boolean enable;
            public boolean enableWeek;
            public int[] rect;
        }

        public static class Property {
            public int OSDFont;
        }
    }

    public static class RecordTimeRegion {
        public String recordType = "Timing"; // 目前只需要定时录像
        public String startTime;
        public String stopTime;

        public RecordTimeRegion() {
        }

        public RecordTimeRegion(Date from, Date to) {
            startTime = UTCTimeFormat(from);
            stopTime = UTCTimeFormat(to);
        }
    }

    public static class RecordReqParam {
        public final int channel;
        public RecordTimeRegion recordSearchCount;
        public ItemSearch recordSearchItem;

        public RecordReqParam(int channel) {
            this.channel = channel;
        }

        public static class ItemSearch extends RecordTimeRegion {
            public final int[] itemRange = new int[2];

            public ItemSearch(Date begin, Date end, int from, int to) {
                super(begin, end);
                itemRange[0] = from;
                itemRange[1] = to;
            }
        }
    }

    public static class RecordRespParam {
        public static class RecordAmount extends RecordTimeRegion {
            public int count;
        }

        public static class RecordItems extends RecordTimeRegion {
            public ArrayList<RecordItem> recordItem = new ArrayList<>();

            public static class RecordItem {
                public long startTime;
                public long stopTime;
                public int recordSize;
                public int recordIndex;
                public String recordName;
                public String playUrl;
                public String recordUrl;
            }
        }
    }


}
