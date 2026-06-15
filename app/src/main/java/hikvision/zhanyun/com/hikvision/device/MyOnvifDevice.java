package hikvision.zhanyun.com.hikvision.device;

import static lyh.Utils.PERIOD_SECOND;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import be.teletask.onvif.OnvifManager;
import be.teletask.onvif.listeners.OnvifResponseListener;
import be.teletask.onvif.models.OnvifDevice;
import be.teletask.onvif.models.OnvifServices;
import be.teletask.onvif.models.OnvifType;
import be.teletask.onvif.requests.OnvifRequest;
import be.teletask.onvif.responses.OnvifResponse;
import hikvision.zhanyun.com.hikvision.MainActivity;
import hikvision.zhanyun.com.hikvision.RtspClient;
import hikvision.zhanyun.com.hikvision.RtspClientCallback;
import hikvision.zhanyun.com.hikvision.Settings;
import hikvision.zhanyun.com.hikvision.device.Device;
import hikvision.zhanyun.com.hikvision.rto.RTPAAC;
import hikvision.zhanyun.com.hikvision.rto.RTPH264;
import hikvision.zhanyun.com.hikvision.utils.Log;
import lyh.Utils;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * ONVIF设备类，封装控制支持ONVIF协议的设备<br/>
 *<br/>
 * ONVIF设备的一般控制流程：<br/>
 * 1. 向设备发送GetServices指令，获取到各个Service的URL，例如PTZ, Media, Device等<br/>
 * 2. 向各个Server的URL，发送获取Profile的指令，获取Profile（如果支持的话）<br/>
 * 3. 根据需要，向各个Service发送相应的指令，部分指令需要第二步获取的Profile名称作为参数<br/>
 *
 *  ONVIF 请求: http://192.168.200.11:80/onvif/device_service => <GetServices xmlns="http://www.onvif.org/ver10/device/wsdl"><IncludeCapability>false</IncludeCapability></GetServices>
 * 2025-10-13 11:09:11.552 : jr_log : 设备已就绪
 * 2025-10-13 11:09:11.585 : jr_log : ONVIF 请求: http://192.168.200.11:80/onvif/media_service => <GetVideoEncoderConfigurations xmlns="http://www.onvif.org/ver10/media/wsdl" />
 *  http://192.168.200.11:80/onvif/image_service =>     <SetImagingSettings xmlns="http://www.onvif.org/ver20/imaging/wsdl">
 */

public class MyOnvifDevice extends Device implements OnvifResponseListener {
    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;
//    private String SERVICE_PTZ =  "/onvif/PTZ";
//    private String SERVICE_MEDIA =  "/onvif/media_service";
//    private String SERVICE_MEDIA2 =  "/onvif/Media2";
//    private String SERVICE_DEVICE =  "/onvif/device_service";
//    private String SERVICE_PROFILE =  "/onvif/device_service";
//    private String SERVICE_EVENT = "/onvif/Events";
////    private String SERVICE_IMAGE = "/onvif/Imaging";
//    private String SERVICE_IMAGE = "/onvif/image_service";
//    private String SERVICE_DEVICEIO = "/onvif/DeviceIO";
//    private String SERVICE_RECORDING = "/onvif/Recording";

    //-----
    private String SERVICE_PTZ =  "/onvif/ptz_service";
    private String SERVICE_MEDIA =  "/onvif/media_service";
    private String SERVICE_MEDIA2 =  "/onvif/media2_service";
    private String SERVICE_DEVICE =  "/onvif/device_service";
    private String SERVICE_PROFILE =  "/onvif/device_service";
    private String SERVICE_EVENT = "/onvif/Events";
    private String SERVICE_IMAGE = "/onvif/image_service";
    private String SERVICE_DEVICEIO = "/onvif/deviceio_service";
    private String SERVICE_RECORDING = "/onvif/Recording";
    private String SERVICE_ANALYTICS = "/onvif/analytics_service";

    private String OnvifLog  = "OnvifLog";


    private final float PTZ_STEP = 0.01f;
    public boolean onvifReady = false;

    private String deviceModel = "";


//    private final long MIN_BOOTUP_MILISECONDS = 120 * PERIOD_SECOND;
    private final long MIN_BOOTUP_MILISECONDS = 40 * PERIOD_SECOND;
    private final long AFTER_BOOTUP_PTZ_SELFCHECK_WAIT_MS = 15 * PERIOD_SECOND;


    private Pattern pattern = Pattern.compile(":\\/\\/");

    private Semaphore semaphore = new Semaphore(0);

    private HandlerThread scheduledThread = new HandlerThread("摄像机拍照线程");
    private Handler scheduledHandler;


    class OnvifCodecConfig {
        public String token = "token";
        public String name = "name";
        public Settings.VideoCodec codec = new Settings.VideoCodec();
    }

    public String device_token = "token";
    public String vc_token = "vc_token";
    public String vc_name = "vc_name";


    // 以下变量用于解码，为提高性能，避免每次回调都创建和判断使用全局变量提高性能
    private RtspClient rtspLiveClient;          // 直播 RTSP 客户端
    private RtspClient rtspRecordClient;        // 短视频录制 RTSP 客户端
    private RTPH264 recordRtph264;              // 短视频录制专用 H264 RTP 解包器
    private RTPAAC recordRtpaac;                // 短视频录制专用 AAC RTP 解包器
    protected RtspClient rtspPlaybackClient;      // 回放 RTSP 客户端
    //    private boolean isMuxerInited = false;
    private OnvifManager onvifManager;
    private OnvifDevice device;
    private OnvifServices services;
    private String profileToken = "Profile_1";
    private String streamURI = "";
    public String snapURI = "";
    private List<OnvifCodecConfig> videoCodecTokens = new ArrayList<>();
    public MediaCodec mediaEncoder; /////
    private boolean useAudio; /////

    private final RtspClientCallback rtspLiveOnlyCallback = new RtspClientCallback() {
        @Override
        public void onPacket(int channel, byte[] packet, int len) {
            if (packet == null || len <= 12 || !isLiving()) return;

            try {
                int payloadType = packet[1] & 0x7F;
                if (payloadType != 96) return;

                byte[] data = new byte[len];
                System.arraycopy(packet, 0, data, 0, len);
                data[8] = (byte) (ssrcLive >> 24);
                data[9] = (byte) (ssrcLive >> 16);
                data[10] = (byte) (ssrcLive >> 8);
                data[11] = (byte) (ssrcLive & 0xFF);

                onVideoFrame(data);
            } catch (Exception e) {
                Log.e(OnvifLog, "ONVIF直播RTP处理异常：" + e.getMessage());
            }
        }

        @Override
        public void onResponse(List<String> headers, byte[] body) {
        }
    };

    private final RtspClientCallback rtspRecordCallback = new RtspClientCallback() {
        @Override
        public void onPacket(int channel, byte[] packet, int len) {
            if (packet == null || len <= 12 || !isRecording()) return;

            try {
                int payloadType = packet[1] & 0x7F;
                if (recordingStartTime == -1) {
                    recordingStartTime = System.currentTimeMillis();
                }

                if (payloadType == 96) {
                    handleRecordVideoPacket(packet, len);
                } else if (payloadType == 104) {
                    handleRecordAudioPacket(packet, len);
                }
            } catch (Exception e) {
                Log.e(OnvifLog, "ONVIF录像RTP处理异常：" + e.getMessage());
            }
        }

        @Override
        public void onResponse(List<String> headers, byte[] body) {
        }
    };

    public MyOnvifDevice(int ID, Context context, String ip, int port, String User, String pwd, boolean useAudio) {
        super(ID, context, useAudio); /////

        this.user = User;
        this.password = pwd;
        this.server = ip;
        this.port = port;
        this.type = DEVICE_ONVIF_CAMERA;
        this.useAudio = useAudio; /////
        try {

            Log.e(OnvifLog,"ONVIF设备正在进行初始化: ");

            String s = user + ":" + password;
            token = "Basic " + Base64.encodeToString(s.getBytes(), Base64.NO_WRAP);

            device = new OnvifDevice(server + ":" + port, user, password);
            onvifManager = new OnvifManager();
            services = new OnvifServices();
            services.setServicesPath(SERVICE_DEVICE);
            services.setProfilesPath(SERVICE_PROFILE);
            services.setStreamURIPath(SERVICE_MEDIA);
            services.setDeviceInformationPath(SERVICE_DEVICE);
            device.setPath(services);
            onvifManager.setOnvifResponseListener(this);

            if (scheduledHandler == null || !scheduledThread.isAlive()) {
                scheduledThread.start();
                scheduledHandler = new Handler(scheduledThread.getLooper());
            }

            // 获取设备的作用域
//            String getServicesXml = "<tds:GetServices>" +
//                    "<tds:IncludeCapability>true</tds:IncludeCapability>" +
//                    "</tds:GetServices>";
//            Log.e(OnvifLog, "开始获取设备服务列表...");
//            onvif_request(getServicesXml, SERVICE_DEVICE);


//             获取视频编码的token
//            String getvideoEncoderXml = "<GetVideoEncoderConfigurations xmlns=\"http://www.onvif.org/ver10/media/wsdl\"/>";
//            onvif_request(getvideoEncoderXml, SERVICE_MEDIA);


            //获取osd的token

//            String getOsdXml = "<GetVideoSourceConfigurations xmlns=\"http://www.onvif.org/ver10/media/wsdl\"/>";
//            onvif_request(getOsdXml, SERVICE_MEDIA);
//
//            String osdCap = "<GetOSDOptions xmlns=\"http://www.onvif.org/ver20/media/wsdl\">\n" +
//                    "  <ConfigurationToken>ONFVideoSourceToken_1</ConfigurationToken>\n" +
//                    "</GetOSDOptions>";
//
//            onvif_request(osdCap, SERVICE_MEDIA);


//            String setRecordTimeXml ="<GetRecordingConfigurations xmlns=\"http://www.onvif.org/ver10/recording/wsdl\"/>";
//            onvif_request(setRecordTimeXml, SERVICE_RECORDING);


            // 获取token
            String xml = "<GetVideoSources xmlns=\"http://www.onvif.org/ver10/deviceIO/wsdl\"/>" ;
            onvif_request(xml, SERVICE_MEDIA);


        } catch (Exception e) {
            Log.e(OnvifLog,"ONVIF设备创建错误: " + e.getMessage());
        }
    }

    //解析命令空间
    private void parseAndSetServiceAddresses(String xmlResponse) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(xmlResponse));
            Document doc = builder.parse(is);

            XPathFactory xPathFactory = XPathFactory.newInstance();
            XPath xpath = xPathFactory.newXPath();
            xpath.setNamespaceContext(new NamespaceContext() {
                @Override
                public String getNamespaceURI(String prefix) {
                    switch (prefix) {
                        case "tds":
                            return "http://www.onvif.org/ver10/device/wsdl";
                        case "tt":
                            return "http://www.onvif.org/ver10/schema";
                        default:
                            return null;
                    }
                }

                @Override
                public String getPrefix(String namespaceURI) {
                    return null;
                }

                @Override
                public Iterator<String> getPrefixes(String namespaceURI) {
                    return null;
                }
            });

            NodeList serviceNodes = (NodeList) xpath.evaluate(
                    "//tds:GetServicesResponse/tds:Service",
                    doc,
                    XPathConstants.NODESET
            );

            for (int i = 0; i < serviceNodes.getLength(); i++) {
                Node node = serviceNodes.item(i);

                String namespace = xpath.evaluate("tt:Namespace", node);
                String address = xpath.evaluate("tt:XAddr", node);

                Log.e(OnvifLog, "发现服务 - 命名空间: " + namespace + ", 地址: " + address);

                if (namespace.contains("device/wsdl")) {
                    Log.e(OnvifLog, "设备服务地址: " + address);
                } else if (namespace.contains("media/wsdl")) {
                    Log.e(OnvifLog, "媒体服务地址: " + address);
                } else if (namespace.contains("ptz/wsdl")) {
                    Log.e(OnvifLog, "PTZ服务地址: " + address);
                } else if (namespace.contains("imaging/wsdl")) {
                    Log.e(OnvifLog, "成像服务地址: " + address);
                }
            }
        } catch (Exception e) {
            Log.e(OnvifLog, "解析服务地址失败: " + e.getMessage());
        }
    }


    private int retryCount = 0;
    private static final int MAX_RETRY_COUNT = 3;

    private void reauthenticate() {
        try {
            Log.e(OnvifLog, "检测到认证失败，正在重新认证...");

            // 重新创建设备实例
            device = new OnvifDevice(server + ":" + port, user, password);
            device.setPath(services);

            // 更新认证token
            String s = user + ":" + password;
            token = "Basic " + Base64.encodeToString(s.getBytes(), Base64.NO_WRAP);

            Log.e(OnvifLog, "重新认证完成");

        } catch (Exception e) {
            Log.e(OnvifLog, "重新认证失败: " + e.getMessage());
        }
    }

    /**
     * 重置重试计数器
     */
    private void resetRetryCount() {
        retryCount = 0;
    }

    /**
     * 检查是否超过重试上限
     */
    private boolean canRetry() {
        return retryCount < MAX_RETRY_COUNT;
    }

    /**
     * 增加重试计数
     */
    private void incrementRetryCount() {
        retryCount++;
        Log.e(OnvifLog, "重试计数: " + retryCount + "/" + MAX_RETRY_COUNT);
    }

    /**
     * 带重试机制的ONVIF请求（最多3次）
     */
    public void onvif_request(String xml, String service) {
        if (device == null) {
            Log.e(OnvifLog,"device为null，进行重新请求");
            reauthenticate();
        }

        new Thread(() -> {
            try {
                services.setServicesPath(service);
                onvifManager.sendOnvifRequest(device, new OnvifRequest() {
                    @Override
                    public String getXml() {
                        return xml;
                    }

                    @Override
                    public OnvifType getType() {
                        return OnvifType.CUSTOM;
                    }
                });
            } catch (Exception e) {
                Log.e(OnvifLog, "ONVIF请求错误: " + e.getMessage());
                // 遇到错误时检查是否可以重试
                if (canRetry()) {
                    incrementRetryCount();
                    reauthenticate();
                    // 可以在这里添加延时重试
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        onvif_request(xml, service);
                    }, 1000);
                } else {
                    Log.e(OnvifLog, "已达到最大重试次数(" + MAX_RETRY_COUNT + ")，停止重试");
                    // 重置计数器，避免影响后续请求
                    resetRetryCount();
                }
            }
        }).start();

        try {
            semaphore.tryAcquire(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.i(OnvifLog, "信号同步异常：" + e);
        }
    }




    private void goPreset(String token) {
        onvif_request(String.format("<GotoPreset xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\">\n" +
                "<ProfileToken>" + profileToken + "</ProfileToken>\n" +
                "<PresetToken>%s</PresetToken></GotoPreset>", token), SERVICE_PTZ);
    }


    /**
     * 开始实时预览
     *
     * @param dwStreamType 码流类型   推流123
     */
    public boolean liveStart(int dwStreamType, int ssrc) {

        this.ssrcLive = ssrc;
        this.streamType = dwStreamType;

        if (!onvifReady) return false;

        rtph264 = new RTPH264(ssrc);

//        initVideoCodec();

        if (rtspLiveClient != null) rtspLiveClient.stop();

        rtspLiveClient = new RtspClient(server, 554, user, password, streamURI, rtspLiveOnlyCallback);

        if (!rtspLiveClient.start(false)) { /////
            Log.e(OnvifLog, "ONVIF直播失败");
            return false;
        }
        setState(DevState.LIVING);
//        Log.d(OnvifLog, String.format("ONVIF直播: %s@%s:%d", user, server, port));
        return true;
    }


    @Override
    protected boolean reboot() {
        onvif_request("<SystemReboot xmlns=\"http://www.onvif.org/ver10/device/wsdl\" />", SERVICE_DEVICE);
        return true;
    }

    @Override
    public void startRecordCheckLine(int group) {

    }

    @Override
    public void stopRecordCheckLine(int group) {

    }


    // 对时
    @Override
    public boolean setTime(int dwYear, int dwMonth, int dwDay, int dwHour, int dwMinute, int dwSecond) {
//        String xml = String.format("<SetSystemDateAndTime xmlns=\"http://www.onvif.org/ver10/device/wsdl\">\n" +
//                "<DateTimeType>Manual</DateTimeType><DaylightSavings>false</DaylightSavings>" +
//                "<TimeZone><TZ xmlns=\"http://www.onvif.org/ver10/schema\">GMT+0</TZ></TimeZone><UTCDateTime>" +
//                "<Time xmlns=\"http://www.onvif.org/ver10/schema\"><Hour>%d</Hour><Minute>%d</Minute><Second>%d</Second></Time>\n" +
//                "<Date xmlns=\"http://www.onvif.org/ver10/schema\"><Year>%d</Year><Month>%d</Month><Day>%d</Day></Date>\n" +
//                "</UTCDateTime></SetSystemDateAndTime>", dwHour, dwMinute, dwSecond, dwYear, dwMonth, dwDay);
//        onvif_request(xml, SERVICE_DEVICE);
        return true;
    }

    @Override
    public void setSceneName(int presetNo, String name) {
        move(9, presetNo);
    }




    @Override
    public String toString() {
        return String.format("ONVIF设备: %d\n名称: %s\n地址: %s:%d\n用户名: %s\n", id, name, server, port, user);
    }

    /**
     * 执行命令函数，启动两个线程用于清零命令执行过程中的数据缓存，防止进程卡死
     * */
    private void cmdUnblock(String cmd) {
        class StreamClearThread implements Runnable{
            private InputStream stream;
            public StreamClearThread(InputStream stream){
                this.stream = stream;
            }
            @Override
            public void run() {
                if (this.stream == null) return;
                BufferedReader reader = new BufferedReader(new InputStreamReader(this.stream));
                try{
                    while(reader.readLine() != null);
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage());
                }
            }
        }

        Log.i(OnvifLog, "开始执行命令:" + cmd);
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            //新建两个线程用来清零命令执行中的缓存，防止缓存满了导致的进程悬挂不能退出
            new StreamClearThread(process.getInputStream()).run();
            new StreamClearThread(process.getErrorStream()).run();
            process.waitFor();
        } catch (Exception e){
            Log.i(OnvifLog, "执行命令" + cmd + "异常:" + e.getMessage());
        }
        Log.i(OnvifLog, "结束命令:" + cmd);
    }


    @Override
    public boolean takePhoto(int stream, int preset, boolean show, String filename, Bitmap pop, int recordPreset, HashMap<String, Settings.AIParameter> aps, boolean alert) { ///////
        // 雄迈摄像头 snapURI 返回的图片大小只有800 * 448，强制使用RTSP抓图，提高效果

        // gradle里需要打开com.arthenica:mobile-ffmpeg-full包，为了其他版本省apk大小暂时不打开
        try {

            setState(DevState.PHOTOING);

            if (preset != 0) {

            }
            if (snapURI.equals("") || download(snapURI, filename) == false) {
                clearState(DevState.PHOTOING);
                return false;
            }

            File file = new File(filename);
            long stamp = getTimestampFromFilename(filename);
            BitmapFactory.Options bitmapOption = new BitmapFactory.Options();
            bitmapOption.inMutable = true;
            Bitmap bitmap = BitmapFactory.decodeFile(filename, bitmapOption);
            if (bitmap != null) {
                drawWatermark(bitmap);
                bitmap = processPhoto(bitmap, stamp, preset, aps, alert); /////
                controllerCallback.onFrame(bitmap);
                Utils.saveBitmapAsJPEG(bitmap, filename, 90);
                bitmap.recycle();
            }
            if (file.exists()) {
                clearState(DevState.PHOTOING);
                controllerCallback.onPhotoTaked(stamp, this.id, preset, filename); ///////
                return true;
            }
        } catch (Exception e) {
            Log.e(OnvifLog, "Onvif抓拍错误: " + e.getMessage());
            return false;
        } finally {
            clearState(DevState.PHOTOING);
        }
        return false;
    }


    private void handleRecordVideoPacket(byte[] packet, int len) {
        if (recordRtph264 == null || muxerHandler == null) return;

        byte[] data = new byte[len];
        System.arraycopy(packet, 0, data, 0, len);

        byte[] frame = recordRtph264.rtpToNalu(data, data.length);
        if (frame == null || recordRtph264.sps == null || recordRtph264.pps == null) return;

        byte[] frameData = new byte[frame.length];
        System.arraycopy(frame, 0, frameData, 0, frame.length);
        byte[] sps = recordRtph264.sps;
        byte[] pps = recordRtph264.pps;

        muxerHandler.post(() -> {
            if (!isRecording() || mediaMuxer == null) return;

            if (!isMuxerInited) {
                initMuxer(sps, pps);
            }

            tryStartMuxerOnvif();
            if (!muxerStarted) return;

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            info.size = frameData.length;
            info.offset = 0;
            info.flags = 0;

            long ptsUs = (System.currentTimeMillis() - recordingStartTime) * 1000L;
            if (ptsUs <= lastVideoPtsUs) {
                ptsUs = lastVideoPtsUs + 1;
            }
            lastVideoPtsUs = ptsUs;
            info.presentationTimeUs = ptsUs;

            int type = recordRtph264.frameType(frameData);
            if (type == 7 || type == 8) {
                info.flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
            } else if (type == 5) {
                info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }

            try {
                mediaMuxer.writeSampleData(videoTrackIndex, ByteBuffer.wrap(frameData), info);
            } catch (Exception e) {
                Log.e(OnvifLog, "ONVIF录像视频写入异常：" + e.getMessage());
            }
        });
    }

    private void handleRecordAudioPacket(byte[] packet, int len) {
        if (!useAudio || recordRtpaac == null || muxerHandler == null) return;

        byte[] aac = recordRtpaac.rtpToAac(packet, len);
        if (aac == null) return;

        byte[] audioData = new byte[aac.length];
        System.arraycopy(aac, 0, audioData, 0, aac.length);

        muxerHandler.post(() -> {
            if (!isRecording() || !muxerStarted || audioTrackIndex < 0 || mediaMuxer == null) return;

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            info.offset = 0;
            info.size = audioData.length;
            info.flags = 0;

            long ptsUs = (System.currentTimeMillis() - recordingStartTime) * 1000L;
            if (ptsUs <= lastAudioPtsUs) {
                ptsUs = lastAudioPtsUs + 1;
            }
            lastAudioPtsUs = ptsUs;
            info.presentationTimeUs = ptsUs;

            try {
                mediaMuxer.writeSampleData(audioTrackIndex, ByteBuffer.wrap(audioData), info);
            } catch (Exception e) {
                Log.e(OnvifLog, "ONVIF录像音频写入异常：" + e.getMessage());
            }
        });
    }



    @Override
    public boolean videoStop() {

        recording = false;

        if (rtspRecordClient != null) {
            rtspRecordClient.stop();
            rtspRecordClient = null;
        }

        if (muxerHandler != null) {
            muxerHandler.removeCallbacksAndMessages(null);
        }

        try {

            if (muxerStarted && mediaMuxer != null) {
                Log.i(Log.TAG, "before muxer.stop()");
                mediaMuxer.stop();
                Log.i(Log.TAG, "after muxer.stop()");
            } else {
                Log.e(Log.TAG, "muxer never started, skip stop");
            }

        } catch (Exception e) {
            Log.e(Log.TAG, "muxer stop failed: " + e);
        } finally {

            try {
                if (mediaMuxer != null) {
                    mediaMuxer.release();
                }
            } catch (Exception ignore) {}

            mediaMuxer = null;
            muxerStarted = false;
            isMuxerInited = false;
        }

        if (muxerThread != null) {
            muxerThread.quitSafely();
            try {
                muxerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            muxerThread = null;
        }

        muxerHandler = null;
        recordRtph264 = null;
        recordRtpaac = null;

        super.videoStop();

        return true;
    }


    public void initMuxer(byte[] sps, byte[] pps) {
        if (isMuxerInited) return;

        RtspClient.SDPInfo sdpInfo = rtspRecordClient != null ? rtspRecordClient.sdpInfo : null;
        Settings.VideoCodec vCodec = getVideoCodec(streamType);
        Point fallbackSize = Settings.VideoCodec.getResolution(vCodec.resolution);
        int width = sdpInfo != null && sdpInfo.width > 0 ? sdpInfo.width : fallbackSize.x;
        int height = sdpInfo != null && sdpInfo.height > 0 ? sdpInfo.height : fallbackSize.y;
        byte[] muxerSps = withStartCode((sdpInfo != null && sdpInfo.sps != null) ? sdpInfo.sps : sps);
        byte[] muxerPps = withStartCode((sdpInfo != null && sdpInfo.pps != null) ? sdpInfo.pps : pps);

        if (muxerSps == null || muxerSps.length == 0 || muxerPps == null || muxerPps.length == 0) {
            Log.e(Log.TAG, "ONVIF录像初始化Muxer失败：缺少H264 SPS/PPS");
            return;
        }

        MediaFormat videoFormat =
                MediaFormat.createVideoFormat(
                        MediaFormat.MIMETYPE_VIDEO_AVC,
                        width,
                        height);

        videoFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, vCodec.frame);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

        videoFormat.setByteBuffer("csd-0",
                ByteBuffer.wrap(muxerSps));
        videoFormat.setByteBuffer("csd-1",
                ByteBuffer.wrap(muxerPps));

        videoTrackIndex = mediaMuxer.addTrack(videoFormat);

        tryStartMuxerOnvif();
        isMuxerInited = true;
    }

    private byte[] withStartCode(byte[] nalu) {
        if (nalu == null) return null;

        if (nalu.length >= 4
                && nalu[0] == 0
                && nalu[1] == 0
                && nalu[2] == 0
                && nalu[3] == 1) {
            return nalu;
        }

        if (nalu.length >= 3
                && nalu[0] == 0
                && nalu[1] == 0
                && nalu[2] == 1) {
            byte[] ret = new byte[nalu.length + 1];
            ret[0] = 0;
            System.arraycopy(nalu, 0, ret, 1, nalu.length);
            return ret;
        }

        byte[] ret = new byte[nalu.length + 4];
        ret[0] = 0;
        ret[1] = 0;
        ret[2] = 0;
        ret[3] = 1;
        System.arraycopy(nalu, 0, ret, 4, nalu.length);
        return ret;
    }



    @Override
    public boolean videoStart(int stream, String filename, int duration, boolean upload) {

        if (!onvifReady) {
            Log.e(OnvifLog, "ONVIF录像启动失败：设备未就绪");
            controllerCallback.onVideoFailed(id, filename);
            return false;
        }

        // ⭐ 初始化线程
        muxerThread = new HandlerThread("MuxerThread");
        muxerThread.start();
        muxerHandler = new Handler(muxerThread.getLooper());

        String tmpfile = MainActivity.DATA_DIR + "record_" + id + ".mp4";

        try {

            recording = true;
            recordingStartTime = -1;

            isMuxerInited = false;
            muxerStarted = false;
            muxerEverStarted = false;

            videoTrackIndex = -1;
            audioTrackIndex = -1;

            lastVideoPtsUs = 0;
            lastAudioPtsUs = 0;

            recordRtph264 = new RTPH264(0);

            if (useAudio) {
                recordRtpaac = new RTPAAC();
            }

            mediaMuxer = new MediaMuxer(
                    tmpfile,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            );

            if (rtspRecordClient != null) {
                rtspRecordClient.stop();
            }

            rtspRecordClient = new RtspClient(
                    server, 554, user, password, streamURI, rtspRecordCallback
            );

            if (!rtspRecordClient.start(useAudio)) {
                throw new Exception("ONVIF录像RTSP启动失败");
            }

            RtspClient.SDPInfo sdpInfo = rtspRecordClient.sdpInfo;

            // ⭐ 提前加 audio track
            if (useAudio && sdpInfo != null && sdpInfo.audioSpecificConfig != null) {

                MediaFormat audioFormat =
                        MediaFormat.createAudioFormat(
                                MediaFormat.MIMETYPE_AUDIO_AAC,
                                sdpInfo.audioSampleRate,
                                sdpInfo.audioChannels);

                audioFormat.setInteger(
                        MediaFormat.KEY_AAC_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AACObjectLC);

                audioFormat.setByteBuffer(
                        "csd-0",
                        ByteBuffer.wrap(sdpInfo.audioSpecificConfig));

                audioTrackIndex = mediaMuxer.addTrack(audioFormat);
            }

            super.videoStart(stream, filename, duration, upload);

            // ⭐ 定时停止
            new Timer("recordStop").schedule(new TimerTask() {
                @Override
                public void run() {

                    Log.e("MyOnvifDevice","执行videoStop");

                    videoStop();

                    if (upload) {
                        Utils.su("mv " + tmpfile + " " + filename);
                    } else {
                        File file = new File(tmpfile);
                        File finalFile = new File(filename);
                        file.renameTo(new File(MainActivity.FILE_PATH + id + File.separator + finalFile.getName()));
                    }

                    controllerCallback.onVideoFinished(
                            System.currentTimeMillis(),
                            id,
                            streamType,
                            filename,
                            upload
                    );
                }
            }, (duration + 1) * 1000);

        } catch (Exception e) {
            Log.e(OnvifLog, "ONVIF录像启动异常：" + e.getMessage());
            videoStop();
            controllerCallback.onVideoFailed(id, filename);
        }

        return recording;
    }


    @Override
    public boolean playbackStart(String startS, String endS, int ssrc) {
        this.ssrcPlayback = ssrc;


//        Date begin = stringToDateTime("yyyy-MM-dd-HH-mm-ss", startS);
//        Date end = stringToDateTime("yyyy-MM-dd-HH-mm-ss", endS);
//
//        // 集光RTSP播放地址如下：
//        // rtsp://ip:554/id=1&type=0&record=yyyymmddhhnnss_yymmddhhnnss
//        // id = 通道号, type=码流类型（目前集光不区分主辅码流），record表示开始和结束时间，回放URL也可以通过GetRtspPlayBackUrl.cgi获取
//        String para = String.format("%d%02d%02d%02d%02d%02d_%d%02d%02d%02d%02d%02d",
//                begin.getYear() + 1900, begin.getMonth() + 1, begin.getDate(), begin.getHours(), begin.getMinutes(), begin.getSeconds(),
//                end.getYear() + 1900, end.getMonth() + 1, end.getDate(), end.getHours(), end.getMinutes(), end.getSeconds()
//        );
//        rtph264 = new RTPH264(ssrc);
//        rtspPlaybackClient = new RtspClient(server, 554, user, password, "id=1&type=0&record=" + para, rtspPlaybackCallback);
//        if (!rtspPlaybackClient.start()) {
//            Log.e(OnvifLog, "ONVIF回放失败");
//            return false;
//        }
//
//        Log.i(OnvifLog, "ONVIF回放: " + para);
        return true;
    }

    @Override
    public boolean playbackStop() {
        Log.d(OnvifLog, "ONVIF回放停止");
        if (rtspPlaybackClient != null) rtspPlaybackClient.stop();
        rtspPlaybackClient = null;
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

    @Override
    public boolean takeVideo(final String filename, final int duration, int stream, boolean upload) {
        if (isRecording()) return false;

        scheduledHandler.post(()->{
            videoStart(stream, filename, duration, upload);
        });
        return true;
    }

    @Override
    public boolean isDeviceReady() {
        onvif_request("<GetServices xmlns=\"http://www.onvif.org/ver10/device/wsdl\">" +
                        "<IncludeCapability>false</IncludeCapability></GetServices>",
                OnvifServices.ONVIF_PATH_SERVICES);

        return onvifReady ? true : false;
    }


    @Override
    public boolean imageFusion(int stream, int preset, boolean show, String filename, Bitmap image_rgb, int recordPreset) {
        return false;
    }


    private boolean closeForce;
    public boolean wait; /////
    @Override
    public boolean open(int stream, onOpenCallback cb, int timeoutSeconds, boolean waitSelfCheck)     // waitSelfCheck true
    {
        wait = waitSelfCheck;

        if (onvifReady) {
            if (cb != null) cb.openSucceed();
            return true;
        }

        closeForce = false;
        setState(DevState.OPENING);
        long bootTime = System.currentTimeMillis();

        do {
            Log.i(OnvifLog, "isDeviceReady()："+isDeviceReady());

            if (isDeviceReady()) {
                if (deviceModel == null || deviceModel.isEmpty()) {
                    onvifManager.getDeviceInformation(device, (onvifDevice, onvifDeviceInformation) -> {
                        deviceModel = onvifDeviceInformation.getModel();
                        Log.i(OnvifLog, "摄像头型号：" + deviceModel);
                    });
                }


                if (waitSelfCheck) {  // 15s
                    SystemClock.sleep(AFTER_BOOTUP_PTZ_SELFCHECK_WAIT_MS);    //   设备启动成功，这个时候云台还在转动，BOOTUP_TRUE_WAIT_MILISECONDS为启动成功后的等待时间。等待自检完成的时间
                }


                clearState(DevState.OPENING);
                Log.i(OnvifLog, "打开球机耗时 " + (System.currentTimeMillis() - bootTime) / 1000 + " 秒");
                if (cb != null) cb.openSucceed();
                return true;
            }
            SystemClock.sleep(PERIOD_SECOND);
        } while (Math.abs(System.currentTimeMillis() - bootTime) / 1000 < timeoutSeconds && !closeForce);    // timeoutSeconds 控制整个打开设备操作的最大等待时间，设备打开成功，这个时间就失效


        clearState(DevState.OPENING);
        if (closeForce) {
            Log.i(OnvifLog, "强制关闭球机");
        } else {
            Log.i(OnvifLog, "打开球机超时 " + timeoutSeconds + " 秒");
            if (cb != null) cb.openFailed(-1);
        }
        return false;
    }



    @Override
    public boolean close() {
        closeForce = true;
        onvifReady = false;
        clearState();
        return true;
    }


    /**
     * 停止实时预览
     */
    @Override
    public boolean liveStop() {
        rtph264 = null;
        unInitVideoCodec();

        if (rtspLiveClient != null) {
            rtspLiveClient.stop();
            rtspLiveClient = null;
        }

        clearState(DevState.LIVING);
        Log.i(OnvifLog, "停止ONVIF预览成功");
        return true;
    }

    @Override
    public boolean setOSD(Settings.OSD osd, boolean osdNull) {
        return false;
    }


    public  int getSizeValue(int size) {
        return (size >= 1 && size <= 4) ? size * 16 : 32;
    }

    @Override
    public Settings.VideoCodec getVideoCodec(int streamType) {
//        if (videoCodecTokens.isEmpty()) {
//            open(streamType, null, 5, false);
//        }
//
//        if (videoCodecTokens.isEmpty())
//            return getVideoCodec(streamType);
//        else
//            return videoCodecTokens.get(streamType).codec;
        return codec.get(String.valueOf(streamType));
    }

    /**
     * 视频采集参数配置
     * <GetVideoEncoderConfigurations xmlns="http://www.onvif.org/ver10/media/wsdl"/>
     */
    @Override
    public boolean setCodec(Settings.VideoCodec v) {
//        this.codec.put(String.valueOf(v.streamType), v);
//
//        String token = "000";  // 雄迈默认 token
//        String vccName = "VideoE_000";   // 雄迈默认 name VideoE_000
//        // 这两个变量不管，后面会被替换掉
//
//        if (videoCodecTokens.isEmpty()) {
//            open(v.streamType, null, 5, false);
//        }
//        if (v.streamType < videoCodecTokens.size() && videoCodecTokens.size() > 0) {
//            token = videoCodecTokens.get(v.streamType).token;
//            vccName = videoCodecTokens.get(v.streamType).name;
//        }
//
//
//        String stream = null;  // 设置到机芯的码流类型  ，x,y是设置到机芯的分辨力，size.x,size.y是下发和显示的分辨率（假的）
//        Point size = Settings.VideoCodec.getResolution(v.resolution);
//        int y = size.y;
//        int x = size.x;
//
//        if (v.streamType == 0) {
//            stream = "main";
//            // 比较配置的分辨率与(1280, 720), (1920, 1080)哪个近，哪个近就用哪个
//            int[][] resolutions = {{1280, 720}, {1920, 1080},{1280,960}};
//            double minDistance = Float.MAX_VALUE;
//            for (int[] res : resolutions) {
//                float distance = (float) Math.sqrt(Math.pow(size.x - res[0], 2) + Math.pow(size.y - res[1], 2));
//                if (distance < minDistance) {
//                    minDistance = distance;
//                    y = res[1];
//                    x = res[0];
//                }
//            }
//            // 非主码流只能设置(720, 576)分辨率
//        } else if (v.streamType == 1) {
//            stream = "extra1";
//            y = 576;
//            x = 704;
//        } else if (v.streamType == 2) {
//            stream = "extra2";
//            y = 288;
//            x = 352;
//        }
//
//        // 限制帧率与I帧间隔在集光机芯可设置范围内
//        int iFrame;
//        if (v.iFrame < 5) {
//            iFrame = 5;
//        } else if (v.iFrame > 100) {
//            iFrame = 100;
//        } else {
//            iFrame = v.iFrame;
//        }
//
//
//        String xml = String.format("<SetVideoEncoderConfiguration xmlns=\"http://www.onvif.org/ver10/media/wsdl\">\n" +
//                "      <Configuration token=\"%s\">\n" +
//                "        <Name xmlns=\"http://www.onvif.org/ver10/schema\">%s</Name>\n" +
//                "        <UseCount xmlns=\"http://www.onvif.org/ver10/schema\">2</UseCount>\n" +
//                "        <Encoding xmlns=\"http://www.onvif.org/ver10/schema\">%s</Encoding>\n" +
//                "        <Resolution xmlns=\"http://www.onvif.org/ver10/schema\">\n" +
//                "          <Width>%d</Width>\n" +
//                "          <Height>%d</Height>\n" +
//                "        </Resolution>\n" +
//                "        <Quality xmlns=\"http://www.onvif.org/ver10/schema\">3</Quality>\n" +
//                "        <RateControl xmlns=\"http://www.onvif.org/ver10/schema\">\n" +
//                "          <FrameRateLimit>%d</FrameRateLimit>\n" +
//                "          <EncodingInterval>1</EncodingInterval>\n" +
//                "          <BitrateLimit>%d</BitrateLimit>\n" +
//                "        </RateControl>\n" +
//                "        <H264 xmlns=\"http://www.onvif.org/ver10/schema\">\n" +
//                "          <GovLength>%d</GovLength>\n" +
//                "          <H264Profile>High</H264Profile>\n" +
//                "        </H264>\n" +
//                "        <Multicast xmlns=\"http://www.onvif.org/ver10/schema\">\n" +
//                "          <Address>\n" +
//                "            <Type>IPv4</Type>\n" +
//                "            <IPv4Address>0.0.0.0</IPv4Address>\n" +
//                "          </Address>\n" +
//                "          <Port>8860</Port>\n" +
//                "          <TTL>128</TTL>\n" +
//                "          <AutoStart>false</AutoStart>\n" +
//                "        </Multicast>\n" +
//                "        <SessionTimeout xmlns=\"http://www.onvif.org/ver10/schema\">PT10S</SessionTimeout>\n" +
//                "      </Configuration>\n" +
//                "      <ForcePersistence>true</ForcePersistence>\n" +
//                "    </SetVideoEncoderConfiguration>", token, vccName, "H264", x, y, v.frame > 25 ? 25 : v.frame, v.bps, iFrame);
//        onvif_request(xml, SERVICE_MEDIA);


        return true;
    }



    // 录像文件查询
    @Override
    public Settings.FileList listFile(int videoType, String startTime, String stopTime, int startNumb, int endNumb) {
        return super.listFile(videoType, startTime, stopTime, startNumb, endNumb);
    }



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

        Log.d(OnvifLog, String.format("ONVIF回放控制: Code=%d, Scale=%5.2f, Offset=%d, 结果: %s", code, scale, offset, ret));
        return true;
    }


    /**
     * OSD 获取配置
     */
    @Override
    public Settings.OSD getOSD() {
//        String xml = "<GetOSDs xmlns=\"http://www.onvif.org/ver20/media/wsdl\" />";
//        onvif_request(xml, SERVICE_MEDIA);
        return super.getOSD();
    }

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
        return true;
    }

    /**
     * 获取巡航路径信息
     *
     * @return
     * @throws IOException
     */
    @Override
    public Settings.CruiseGroup[] getCruise() {
        return null;
    }


    public boolean download(String url, String filename) {

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String currentTime = dateFormat.format(new Date());
        Log.e("HuanyuDeviceLog", "机芯获得图片的时间: " + currentTime);
        Response response = http_request(url);
        if (response == null && response.code() != 200) {
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



    public boolean download(String url, String filename, String username, String password) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String currentTime = dateFormat.format(new Date());
        Log.e("HuanyuDeviceLog", "机芯获得图片的时间: " + currentTime);

        try {
            java.net.URL urlObj = new java.net.URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // 如果需要认证，设置 Basic Auth 头
            if (username != null && password != null) {
                String credentials = username + ":" + password;
                String basicAuth = "Basic " + android.util.Base64.encodeToString(
                        credentials.getBytes(), android.util.Base64.NO_WRAP);
                connection.setRequestProperty("Authorization", basicAuth);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                connection.disconnect();
                return false;
            }

            InputStream inputStream = connection.getInputStream();
            FileOutputStream fos = new FileOutputStream(filename);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.close();
            inputStream.close();
            connection.disconnect();
            return true;
        } catch (IOException e) {
            Log.e("HuanyuDeviceLog", "下载图片失败: " + e.getMessage());
            return false;
        }
    }



    public String buildSetImagingSettingsXml(int brightness,
                                             int colorSaturation, int contrast) {
        String wsdl = "http://www.onvif.org/ver20/imaging/wsdl";
        String schema = "http://www.onvif.org/ver10/schema";

        StringBuilder xmlBuilder = new StringBuilder();

        xmlBuilder.append(String.format("    <SetImagingSettings xmlns=\"%s\">", wsdl));
        xmlBuilder.append(String.format("      <VideoSourceToken>%s</VideoSourceToken>", device_token));
        xmlBuilder.append("      <ImagingSettings>");
        xmlBuilder.append(String.format("        <Brightness xmlns=\"%s\">%d</Brightness>", schema, brightness));
        xmlBuilder.append(String.format("        <ColorSaturation xmlns=\"%s\">%d</ColorSaturation>", schema, colorSaturation));
        xmlBuilder.append(String.format("        <Contrast xmlns=\"%s\">%d</Contrast>%n", schema, contrast));
        xmlBuilder.append("      </ImagingSettings>");
        xmlBuilder.append("      <ForcePersistence>true</ForcePersistence>");
        xmlBuilder.append("    </SetImagingSettings>");
        return xmlBuilder.toString();
    }

    @Override
    public String getSceneName(int presetNo) {
        return "";
    }

    /**
     * @param cmd
     * @param para
     */
    @Override
    public void move(int cmd, int para) { /////
        super.move(cmd, para);
    }


    @Override
    protected void doSampleData(ByteBuffer outputBuffer, MediaCodec.BufferInfo bufferInfo, int outIndex) {
    }


    private void doRelativeMove(float x, float y, float z) {
        String xml = String.format("<RelativeMove xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\">\n" +
                "      <ProfileToken>%s</ProfileToken>\n" +
                "      <Translation>\n" +
                "        <PanTilt x=\"%f\" y=\"%f\" space=\"http://www.onvif.org/ver10/tptz/PanTiltSpaces/TranslationGenericSpace\" xmlns=\"http://www.onvif.org/ver10/schema\" />\n" +
                "        <Zoom x=\"%f\" space=\"http://www.onvif.org/ver10/tptz/ZoomSpaces/TranslationGenericSpace\" xmlns=\"http://www.onvif.org/ver10/schema\" />\n" +
                "      </Translation>\n" +
                "      <Speed>\n" +
                "        <PanTilt x=\"0.5\" y=\"0.5\" space=\"http://www.onvif.org/ver10/tptz/PanTiltSpaces/GenericSpeedSpace\" xmlns=\"http://www.onvif.org/ver10/schema\" />\n" +
                "        <Zoom x=\"0.5\" space=\"http://www.onvif.org/ver10/tptz/ZoomSpaces/ZoomGenericSpeedSpace\" xmlns=\"http://www.onvif.org/ver10/schema\" />\n" +
                "      </Speed>\n" +
                "    </RelativeMove>", profileToken, x, y, z);
        onvif_request(xml, SERVICE_PTZ);
    }



    @Override
    public Settings.FileDir findFiles(int videoType, Settings.TimeRecord startTime, Settings.TimeRecord stopTime) {
        return super.findFiles(videoType, startTime, stopTime);
    }

    @Override
    public void onResponse(OnvifDevice onvifDevice, OnvifResponse response) {
        semaphore.release();
        String xml = response.getXml();
        try {
            parseXML(xml);
//            Log.e(OnvifLog, "获取的xml: " + xml);
        } catch (Exception e) {
            Log.e(OnvifLog, "处理ONVIF XML错误: " + e.getMessage());
        }
    }

    /**
     * 根据DOM节点的名字，返回Node的子节点
     *
     * @param node 需要查找DOM节点
     * @param name 子节点的名字
     *
     * @return
     */
    private Node getChildNodeByName(Node node, String name) {
        Node child = node.getFirstChild();
        while (child != null) {
            if (child.getNodeName().equals(name)) return child;
            child = child.getNextSibling();
        }
        return null;
    }

    private void parseGetService(Document doc) {
        NodeList nodes = doc.getElementsByTagName("tds:Service");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = getChildNodeByName(nodes.item(i), "tds:Namespace");
            if (node == null) continue;

            String text = node.getTextContent();
            if (text.indexOf("/ptz/wsdl") > 0) {
                node = getChildNodeByName(nodes.item(i), "tds:XAddr");
                if (node == null) return;
                SERVICE_PTZ = URI.create(node.getTextContent()).getRawPath();
                continue;
            }

            if (text.indexOf("ver10/media/wsdl") > 0) {
                node = getChildNodeByName(nodes.item(i), "tds:XAddr");
                if (node == null) return;
                SERVICE_MEDIA = URI.create(node.getTextContent()).getRawPath();
                continue;
            }

            if (text.indexOf("/device/wsdl") > 0) {
                node = getChildNodeByName(nodes.item(i), "tds:XAddr");
                if (node == null) return;
                SERVICE_DEVICE = URI.create(node.getTextContent()).getRawPath();
                continue;
            }

            if (text.indexOf("ver20/media/wsdl") > 0) {
                node = getChildNodeByName(nodes.item(i), "tds:XAddr");
                if (node == null) return;
                SERVICE_MEDIA2 = URI.create(node.getTextContent()).getRawPath();
                continue;
            }

            if (text.indexOf("/events/wsdl") > 0) {
                node = getChildNodeByName(nodes.item(i), "tds:XAddr");
                if (node == null) return;
                SERVICE_EVENT = URI.create(node.getTextContent()).getRawPath();
                continue;
            }

            if (text.indexOf("/imaging/wsdl") > 0) {
                node = getChildNodeByName(nodes.item(i), "tds:XAddr");
                if (node == null) return;
                SERVICE_IMAGE = URI.create(node.getTextContent()).getRawPath();
                continue;
            }

            if (text.indexOf("/deviceIO/wsdl") > 0) {
                node = getChildNodeByName(nodes.item(i), "tds:XAddr");
                if (node == null) return;
                SERVICE_DEVICEIO = URI.create(node.getTextContent()).getRawPath();
                continue;
            }

            if (text.indexOf("/recording/wsdl") > 0) {
                node = getChildNodeByName(nodes.item(i), "tds:XAddr");
                if (node == null) return;
                SERVICE_RECORDING = URI.create(node.getTextContent()).getRawPath();
                continue;
            }
        }

        services.setProfilesPath(URI.create(SERVICE_MEDIA).getRawPath());
        onvifManager.getMediaProfiles(device, (device, mediaProfiles) -> {
            if (mediaProfiles.size() == 0) return;

            profileToken = mediaProfiles.get(0).getToken();
            //Log.i(OnvifLog, "获取媒体档案: " + mediaProfiles.get(0).getToken() + " = " + mediaProfiles.get(0).getName());
            onvifManager.getMediaStreamURI(device, mediaProfiles.get(0), (device1, profile, uri) -> {
                String s = pattern.matcher(uri).replaceAll("");
                streamURI = s.substring(s.indexOf("/") + 1);
                //Log.i(OnvifLog, "获取流媒体地址: " + uri);

                String xml = String.format("<GetSnapshotUri xmlns=\"http://www.onvif.org/ver10/media/wsdl\"><ProfileToken>%s</ProfileToken></GetSnapshotUri>", profileToken);
                onvif_request(xml, SERVICE_MEDIA);
                onvifReady = true;
            });
        });

        String xml = "<GetVideoEncoderConfigurations xmlns=\"http://www.onvif.org/ver10/media/wsdl\" />";
        onvif_request(xml, SERVICE_MEDIA);
    }

    public void setPreset(int preset) {
        if (device == null) return;

        String token = preset == 0 ? "" : "<PresetToken>" + preset + "</PresetToken>";
        onvif_request("<SetPreset xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\">\n" +
                "<ProfileToken>" + profileToken + "</ProfileToken>" + token + "</SetPreset>", SERVICE_PTZ);
    }

    public void unsetPreset(int preset) {
        onvif_request("<RemovePreset xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\">\n" +
                "<ProfileToken>" + profileToken + "</ProfileToken>\n" +
                "<PresetToken>" + preset + "</PresetToken></RemovePreset>", SERVICE_PTZ);
    }

    private void getPresets() {
        onvif_request("<GetPresets xmlns=\"http://www.onvif.org/ver20/ptz/wsdl\">\n" +
                "<ProfileToken>" + profileToken + "</ProfileToken></GetPresets>", SERVICE_PTZ);
    }

    private void parseGetPresets(Document doc) {
        NodeList nodes = doc.getElementsByTagName("tptz:Preset");
        if (nodes != null && nodes.getLength() > 0) {
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                NamedNodeMap attributes = node.getAttributes();
                String token = attributes.getNamedItem("token").getNodeValue();

                String key = String.format("%d,%s", id, token);
                Settings.SceneParameter parameter = sceneParameters.get(key);
                if (parameter == null) {
                    parameter = new Settings.SceneParameter();
                    parameter.presetNo = (byte) Utils.toInt(token, i);
                }

                parameter.name = token;
                NodeList subNodes = node.getChildNodes();
                for (int j = 0; j < subNodes.getLength(); j++) {
                    if (subNodes.item(j).getNodeName().equals("tt:Name")) {
                        parameter.name = subNodes.item(j).getTextContent();
                        break;
                    }
                }
                sceneParameters.put(key, parameter);
            }
        }
    }

    private void parseXML(String xml) throws ParserConfigurationException, IOException, SAXException {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes()));
        // 不同服务器返回的SOAP标记不一样，有的是env，有的是soap，有的是s等
        if (doc.getFirstChild() == null) return;
        String s = doc.getFirstChild().getNodeName();
        int idx = s.indexOf(":");
        if (idx < 0)
            s = "soap:Body";
        else
            s = s.substring(0, idx);
        NodeList nodes = doc.getElementsByTagName(s + ":Body");
        if (nodes == null || nodes.getLength() == 0) return;

        String cmdResponse = nodes.item(0).getFirstChild().getNodeName();


        if ("tptz:SetPresetResponse".equals(cmdResponse)) {
            getPresets();
        } else if ("tptz:GetPresetsResponse".equals(cmdResponse)) {
            parseGetPresets(doc);
        } else if ("tds:GetServicesResponse".equals(cmdResponse)) {
            parseGetService(doc);
        } else if ("trt:GetSnapshotUriResponse".equals(cmdResponse)) {
            parseGetSnapshortUriResponse(doc);
        } else if ("trt:GetVideoEncoderConfigurationsResponse".equals(cmdResponse)) {
            GetVideoEncoderConfigurationsResponse(doc);
        } else if ("tptz:RemovePresetResponse".equals(cmdResponse)) {
            getPresets();
        }else if ("trt:GetVideoSourcesResponse".equals(cmdResponse)){
            device_token = getDeviceToken(xml);
//            Log.e(OnvifLog, "device_token: " + device_token);
        };
    }



    private String getDeviceToken(String xml) {

        if (xml == null || xml.trim().isEmpty()) {
//            Log.e(OnvifLog, "getDeviceToken: 传入的XML为空");
            return null;
        }


        String correctedXml = xml.replace("<env:Bodytrt:", "<env:Body><trt:")  // 补全Body标签的闭合
                .replace("</trt:GetVideoSourcesResponse>", "</trt:GetVideoSourcesResponse>")
                .replace("</tt:Resolution>", "</tt:Resolution>")  // 确保子标签闭合（避免解析中断）
                .replace("</tt:Imaging>", "</tt:Imaging>");


        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringElementContentWhitespace(true);

        try {

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(correctedXml)));
            doc.getDocumentElement().normalize();

            String trtNamespaceUri = "http://www.onvif.org/ver10/media/wsdl";


            NodeList videoSourcesList = doc.getElementsByTagNameNS(trtNamespaceUri, "VideoSources");
            if (videoSourcesList == null || videoSourcesList.getLength() == 0) {
                Log.e(OnvifLog, "getDeviceToken: 未找到trt:VideoSources节点");
                return null;
            }


            Element videoSourcesElem = (Element) videoSourcesList.item(0);
            String token = videoSourcesElem.getAttribute("token");


            if (token == null || token.trim().isEmpty()) {
                Log.e(OnvifLog, "getDeviceToken: 提取到的token为空");
                return null;
            }

//            Log.d(OnvifLog, "getDeviceToken: 成功提取token=" + token);
            return token;

        } catch (Exception e) {
            Log.e(OnvifLog, "getDeviceToken: XML解析失败，原因=" + e.getMessage());
            return null;
        }
    }


    private void parseGetSnapshortUriResponse(Document doc) {
        NodeList nodes = doc.getElementsByTagName("tt:Uri");
        if (nodes.getLength() > 0) {
            snapURI = nodes.item(0).getTextContent();
            //Log.d(OnvifLog, "获取抓图地址: " + snapURI);
        }
    }



    private List<Node> xmlGetChildNodesByName(Node node, String name) {
        ArrayList<Node> ret = new ArrayList<>();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeName().equals(name))
                ret.add(children.item(i));
        }
        return ret;
    }

    private Node xmlGetChildNodeByName(Node node, String name) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeName().equals(name))
                return children.item(i);
        }
        return null;
    }

    private void GetVideoEncoderConfigurationsResponse(Document doc) {
        NodeList nodes = doc.getElementsByTagName("trt:Configurations");
        videoCodecTokens.clear();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            OnvifCodecConfig kv = new OnvifCodecConfig();
            kv.token = node.getAttributes().getNamedItem("token").getNodeValue();

            NodeList subNodes = node.getChildNodes();
            for (int j = 0; j < subNodes.getLength(); j++) {
                Node subNode = subNodes.item(j);
                String name = subNode.getNodeName();
                String value = subNode.getTextContent();
                if (name.equals("tt:Name")) {
                    kv.name = subNodes.item(j).getTextContent();
                } else if (name.equals("tt:Resolution")) {
                    int w = Integer.valueOf(xmlGetChildNodeByName(subNode,"tt:Width").getTextContent());
                    int h = Integer.valueOf(xmlGetChildNodeByName(subNode,"tt:Height").getTextContent());
                    kv.codec.resolution = Settings.VideoCodec.getServerResolutionByXY(w, h);
                } else if (name.equals("tt:RateControl")) {
                    kv.codec.bps = (short) (int) Integer.valueOf(xmlGetChildNodeByName(subNode, "tt:BitrateLimit").getTextContent());
                    kv.codec.frame = (byte) (int) Integer.valueOf(xmlGetChildNodeByName(subNode, "tt:FrameRateLimit").getTextContent());
                } else if (name.equals("tt:Encoding")) {
                    kv.codec.codec = (byte) (value.equals("H265") ? 1 : 0);
                } else if (name.equals("tt:H264") || name.equals("tt:H265")) {
                    kv.codec.iFrame = Integer.valueOf(xmlGetChildNodeByName(subNode, "tt:GovLength").getTextContent());
                }
            }
//            Log.d(OnvifLog, "获取视频Token: " + kv.token + " => " + kv.name);
            vc_token = kv.token;
            vc_name = kv.name;
            videoCodecTokens.add(kv);
        }
    }


    @Override
    public void onError(OnvifDevice onvifDevice, int errorCode, String errorMessage) {
        semaphore.release();
//        Log.d(OnvifLog,  "ONVIF 错误回调: " + onvifDevice.getHostName() + " => " + errorMessage);
        if (errorMessage.indexOf("d:MatchingRuleNotSupported") > 0) return;

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new ByteArrayInputStream(errorMessage.getBytes()));
            NodeList nodes = doc.getElementsByTagName("env:Text");
            if (nodes == null || nodes.getLength() == 0) return;
        } catch (Exception e) {
            //Log.e(OnvifLog, "ONVIF 错误处理出错: " + e.getMessage());
        }
    }

}
