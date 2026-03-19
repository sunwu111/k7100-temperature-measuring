package hikvision.zhanyun.com.hikvision;

import android.graphics.Bitmap;

import java.util.Vector;

import hikvision.zhanyun.com.hikvision.device.Device;

public interface ControllerCallback {
    // 照片拍摄完成
    void onPhotoTaked(long timestamp, int channel, int preset, String file);

    // 照片拍摄失败
    void onPhotoFailed(int channel, int preset, String file);

    // 摄像头卡死
    void onCameraBlocked(Device device);

    // 视频拍摄完成
    void onVideoFinished(long timestamp, int channel, int streamType, String file, boolean upload);

    void onVideoFailed(int channel, String file);

    // 帧位图，可以用于AI检测，或者任何想要的处理
    void onFrame(Bitmap bitmap);

    // 编码后的H264帧数据，是RTP格式封装
    void onFrame(Device dev, byte[] data);

    // AI检测结果对象时回调
    void onObjectDetect(Settings.DetectInfo info);

    // 返回电池电量，流量信息
    String onStatusInfo();

    void onTemperatureReport(int channel, int preset, long timestamp, Vector<IRSetting.IrRegionTemp> temp);

    public void onFireAlarm(Settings.FireAlarmInfo info);
}
