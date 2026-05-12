package hikvision.zhanyun.com.hikvision;


import org.json.JSONException;

import hikvision.zhanyun.com.hikvision.Settings.TimeRecord;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

/**
 * Created by ZY004Engineer on 2018/6/12.
 *
 * @author 陈焯辉
 * @version 1.0
 * @description 整理规约代码，按照《QCSG1205031-2020输电线路在线监测通信规约及信息交互规范》
 * @date 2025/05/10
 */

public interface SPGPCallback {
    // 开机联络信息（控制字：00H），参考规约第7.1节。
    public void sendData(byte[] buf);

    // 校时（控制字：01H），参考规约第7.2节。
    public boolean setClock(int dwYear, int dwMonth, int dwDay, int dwHour, int dwMinute, int dwSecond);

    // 设置装置密码（控制字：02H），参考规约第7.3节。
    public void setPassword(String password);

    // 主站下发参数配置（控制字：03H），参考规约第7.4节。
    public boolean setOnlineConfig(Settings.OnlineCfg cfg, String newPasscode);

    // 更改主站IP地址、端口号和卡号（控制字：06H），参考规约第7.6节。
    public void changeServer(String server, int port);

    // 装置重启（控制字：08H），参考规约第7.8节。
    public void rebootDevice();

    // 短信唤醒（控制字：09H），参考规约第7.9节。
    public void wakeup();

    // 查询装置配置参数（控制字：0AH），参考规约第7.10节。
    public Settings.Parameters getConfigurationParameters();

    // 装置功能配置（控制字：0BH），参考规约第7.11节。
    public void setFeatures(Settings.Features features);

    /**
     * 发送确认短信（控制字：0EH），参考规约第7.14节。
     *
     * @param sim 待接收手机号码
     * @param content 发送内容
     */
    void sendSMS(String sim, String content);

    // 上传装置流量数据使用情况（控制字：40H），参考规约第7.46节。
    public Settings.TrafficeUsage getTrafficeUsage();

    // 图像采集参数配置（控制字：81H），参考规约第7.60.1节。
    public void setPhotoParam(int channel, Settings.PhotoConfig v);

    // 拍照时间表设置（控制字：82H），参考规约第7.60.2节。
    public boolean setPhotoTimeTable(int channel, Settings.PhotoTimeItem[] table);

    // 主站请求拍摄照片（控制字：83H），参考规约第7.60.3节。
    public void takePhoto(int channel, int preset);
//    public void takePhoto(int channel, int preset, int captureType); /////

    // 采集终端请求上送照片（控制字：84H），参考规约第7.60.4节；采集终端请求上送短视频（控制字：94H），参考规约第7.60.20节。
    public void onFileUploadFailure(String fileName);

    // 补包数据下发（控制字：87H），参考规约第7.60.7节；短视频补包数据下发（控制字：97H），参考规约第7.60.23节。
    public void onFileUploadEnd(final long time, String fileName, final int Channel, final int preset, final SPGProtocol.FILE_TYPE type);

    /**
     * 摄像机远程调节（控制字：88H），参考规约第7.60.8节。
     *
     * @param channelNum 通道号
     * @param order      指令
     * @param para       预置位
     */
    public void ptzControl(int channelNum, int order, int para);

    /**
     * 启动摄像视频传输（控制字：89H），参考规约第7.60.9节。
     * 开始实时视频，本回调需要尽快返回！
     * 输入 channel 开启某个通道的视频。
     * 返回错误代码，1：当前通道正在传输视频；2：当前电量不够；3：参数错误，例如通道号不正确；4：网络连接错误（TCP传流时，无法连接接收流的地址）。
     */
    public short startLiveVideo(int channel, int streamType, int network, int ssrc, String server, int port);

    // 终止摄像视频传输（控制字：8AH），参考规约第7.60.10节。
    public boolean stopLiveVideo(int channel, int streamType, int ssrc);

    // 查询拍照时间表（控制字：8BH），参考规约第7.60.11节。
    public Settings.PhotoTimeItem[] getPhotoTimeTable(int channel);

    // 视频采集参数配置（控制字：8CH），参考规约第7.60.12节。
    public void setVideoCodec(Settings.VideoCodec v);

    // 视频采集参数查询（控制字：8DH），参考规约第7.60.13节。
    public Settings.VideoCodec getVideoCodec(int channel, int streamType);

    // OSD参数配置（控制字：8EH），参考规约第7.60.14节。
    public void setOSDConfig(int channel, Settings.OSD osd);

    // OSD参数查询（控制字：8FH），参考规约第7.60.15节。
    public Settings.OSD getOSDConfig(int channel);

    // 录像策略参数配置（控制字：90H），参考规约第7.60.16节。
    public void setVideoTimeTable(int channel, int streamType, List<Settings.VideoTimeItem> list);

    // 录像策略参数查询（控制字：91H），参考规约第7.60.17节。
    public List<Settings.VideoTimeItem> getVideoTimeTable(int channel, int streamType);

    // 通道录像状态查询（控制字：92H），参考规约第7.60.18节。
    public Settings.ChannelStatus getChannelState(int channel, int stream);

    /**
     * 主站请求拍摄短视频（控制字：93H），参考规约第7.60.19节。
     * 回调后立刻返回错误码。
     *
     * @param channel 通道号
     * @param type    通道（码流）类型
     * @param time    拍摄时长
     */
    public short startShortVideo(int channel, int type, int time);

    // 主站查询终端录像文件数目（控制字：98H），参考规约第7.61.1节。
    public int fileFiles(int channel, int type, Settings.TimeRecord startTime, Settings.TimeRecord endTime);

    // 主站查询终端录像文件列表（控制字：99H），参考规约第7.61.2节。
    public Settings.FileList findVideoFileList(int channel, int videoType, String startTime, String stopTime, int startNumb, int endNumb);

    /**
     * 主站请求进行录像文件回放（控制字：9AH），参考规约第7.61.3节。
     *
     * @return 错误码：0 = 成功，1 = 超过最大回放用户数限制，2 = 当前电量不够，3 = 参数错误，例如超过录像文件时间范围，4 = 网络连接错误（TCP传流时，无法连接接收流的地址）
     */
    public byte playbackFile(int channel, boolean UDP, String startTime, String stopTime, String ip, int port, int ssrc);

    /**
     * 主站请求进行录像文件回放控制（控制字：9BH），参考规约第7.61.5节。
     *
     * @return 错误码：0 = 成功，1 = 超过最大回放用户数限制，2 = 当前电量不够，3 = 参数错误，例如超过录像文件时间范围，4 = 网络连接错误（TCP传流时，无法连接接收流的地址）
     */
    public short playbackControl(int channel, int code, float scale, int offset, int ssrc);

    // 主站请求进行录像文件回放断开（控制字：9CH），参考规约第7.61.7节。
    public short stopPlayCallBack(int channel, int ssrc);

    // 智能分析参数配置（控制字：A4H），参考规约第7.63.1节。
    void setAIParameters(Settings.AIParameter aiParameters);

    // 智能分析参数查询（控制字：A5H），参考规约第7.63.2节。
    List<Settings.AIParameter> getAIParameters(int channel, int preset);

    // 联动参数配置（控制字：A8H），参考规约第7.63.5节。
    void setAIAction(int channel, int preset, Settings.AIAction[] actions);

    // 联动参数查询（控制字：A9H），参考规约第7.63.6节。
    Settings.AIAction[] getAIAction(int channel, int preset);

    // 摄像机3D控球调节（控制字：B1H），参考规约第7.60.24节。
    public void ptz3DCtrl(int chanNumber, int StartingPointXCoordinate, int StartingPointYCoordinate, int AtTheEndOfXCoordinate, int AtTheEndOfCoordinate);

    // 摄像机巡航参数设置（控制字：B2H），参考规约第7.60.25节。
    public void setPTZCruise(int channel, int cmd, int group, int index, int preset, int duration, int speed);

    // 摄像机巡航参数查询（控制字：B3H），参考规约第7.60.26节。
    public Settings.CruiseGroup[] getPTZCruise(int channel);

    // 摄像机巡检参数设置（控制字：B5H），参考规约第7.60.28节。
    public void setCheckGroup(int Channel, int cmd, int group, int point);

    // 摄像机巡检参数查询（控制字：B6H），参考规约第7.60.29节。
    public List<Settings.CheckGroup> getCheckGroup(int Channel);

    // 摄像机巡检策略查询（控制字：B7H），参考规约第7.60.30节。
    public boolean setCheckLineSchedule(int Channel, List<Settings.CheckScheduleItem> items);

    // 摄像机巡检策略查询（控制字：B8H），参考规约第7.60.31节。
    public List<Settings.CheckScheduleItem> getCheckLineSchedule(int Channel);

    // 主站请求设备升级更新（控制字：CAH），参考规约第7.64.1节。
    public void onUpgradeStart();

    /**
     * 通用参数配置（控制字：D0H），规约无。
     *
     * @return 错误码：0 = 成功，1 = 默认设备不支持，2 = 参数错误
     */
    public byte setSceneParameters(int channel, int preset, Settings.SceneParameter parameter);

    /**
     * 通用参数查询（控制字：D1H），规约无。
     *
     * @param channel 通道号
     * @param preset  预置位号
     * @return 通用参数
     */
    public Settings.SceneParameter getSceneParameters(int channel, int preset);

    // 获取SIM卡信息（控制字：D2H），规约无。
    String getSimCardInfo();

    public void takeUploadLog(int channel); /////

    /**
     * 主站请求手动测温（控制字：E0H），参考规约第7.64.1节。
     * 只有在拉流的时候设置，在视频流绘制测温区域和温度值，参数不保存。
     */
    void manualIrRegonSet(int channel, Vector<IRSetting.IrRegion> regions);

    /**
     * 测温采集参数配置（控制字：E1H），参考规约第7.64.2节。
     * 参数保存。
     */
    void irRegionParamSet(int channel, int preset, Vector<IRSetting.IrRegionInfo> param);

    // 测温采集参数查询（控制字：E2H），参考规约第7.64.3节。
    HashMap<Integer, IRSetting.PresetRegions> irRegionParamGet();

    // 测温数据上送协议（控制字：E3H），参考规约第7.64.4节。
    //void irTempReport(int channel, int preset);

    // 测温传感器采集参数设置（控制字：E4H），参考规约第7.64.5节。
    void irSensorConfig(IRSetting.SensorConfig param);

    // 测温传感器采集参数查询（控制字：E5H），参考规约第7.64.6节。
    IRSetting.SensorConfig irSensorConfig();

    public void cpuLock();

    public void cpuUnlock();

    public void tryCpuUnlock();

    // 获取设备时间
    public String getDvrTime();

    public void doSleep();

    public void onFileUploadFailure(final long time, String fileName, final int Channel, final int preset, final SPGProtocol.FILE_TYPE type);

    public Settings.BatteryInfo getBatterInfo();

    public void onHeartBeatReceived();

    public void onLoggedIn();

    /////
    public void runAI() throws IOException, JSONException;

    public void getAllData();

    public void startVoiceBroadcast();

    public void stopVoiceBroadcast();
    /////
}