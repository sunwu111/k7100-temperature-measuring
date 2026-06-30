package hikvision.zhanyun.com.hikvision;

import java.util.HashMap;

public class CAMERASetting {
    public static class CameraConfig {
        public byte denoiseMode = 2;  // 降噪模式：0:关闭，1:2D降噪，2:3D降噪，3:智能降噪  默认3D降噪
        public byte gainControl = 1;  // 增益控制：0:手动，1:自动  默认自动
        public byte focusMode = 1;  // 聚焦模式：0:半自动，1:自动，2:手动  默认自动 ///
        public byte dayAndNightMode = 0;  // 日夜模式：0:白天，1:自动彩转黑  默认白天 ///
        public byte backLightCom = 0;  // 背光补偿开关：0:关闭，1:开启  默认关闭
        public byte strongLightSup = 0;  // 强光抑制开关：0:关闭，1:开启  默认关闭
        public byte electronicFog = 0;  // 电子透雾开关：0:关闭，1:开启  默认关闭
        public byte lowLight = 0;  // 低照度开关：0:关闭，1:开启  默认关闭
        public byte videoLoss = 0;  // 视频丢失告警开关：0:关闭，1:开启  默认关闭
        public byte videoBlock = 0;  // 视频遮挡告警开关：0:关闭，1:开启  默认关闭
        public byte videoOutFocus = 0;  // 视频失焦告警开关：0:关闭，1:开启  默认关闭
        public byte videoScreenDist = 0;  // 视频花屏告警开关：0:关闭，1:开启  默认关闭
        public byte videoNoise = 0;  // 视频噪点告警开关：0:关闭，1:开启  默认关闭 /////
        public byte videoLossAlert = 0;  // 视频丢失异常：0:正常，1:异常  默认正常 /////
        public byte videoBlockAlert = 0;  // 视频遮挡异常：0:正常，1:异常  默认正常 /////
        public byte videoOutFocusAlert = 0;  // 视频失焦异常：0:正常，1:异常  默认正常 /////
        public byte videoScreenDistAlert = 0;  // 视频花屏异常：0:正常，1:异常  默认正常 /////
        public byte videoNoiseAlert = 0;  // 视频噪点异常：0:正常，1:异常  默认正常 /////

        public String toString() {
            return String.format("降噪模式:%d 增益控制:%d 聚焦模式:%d 日夜模式:%d 背光补偿开关:%d 强光抑制开关:%d 电子透雾开关:%d 低照度开关:%d 视频丢失告警:%d 遮挡告警:%d 失焦告警:%d 花屏告警:%d 噪点告警:%d", /////
                    denoiseMode, gainControl, focusMode, dayAndNightMode, backLightCom, strongLightSup, electronicFog, lowLight, videoLoss, videoBlock, videoOutFocus, videoScreenDist, videoNoise); /////
        }
    }

    public HashMap<String, CameraConfig> cameraConfig = new HashMap<>();  // 通道摄像头配置
}
