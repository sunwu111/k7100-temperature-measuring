package hikvision.zhanyun.com.hikvision.device.guide;

import com.guide.sdk.bean.DeviceStatusInfo;
import com.guide.sdk.bean.GuideUsbVideoMode;

/**
 * 文件名：DeviceConfig
 * 描  述：
 * 作  者：gd12179-hlw
 * 时  间：2024/1/11 14:26
 * 参数说明：
 * - paramSwitch 是否是手动设置相关参数
 * - width 原始红外宽
 * - height 原始红外高
 * - guideUsbVideoMode USB流模式
 * - communicationId 模组类型：若值为4，矩阵最后一行为参数行，否则参数行上面有两行空白行
 * - paletteIndex 色带索引：0-9（白热、熔岩、铁红、热铁、医疗、北极、彩虹1、彩虹2、描红、黑热）
 */
public class DeviceConfig {

    private boolean paramSwitch = false;
    private int width = 0;
    private int height = 0;
    private GuideUsbVideoMode guideUsbVideoMode = GuideUsbVideoMode.Y16_PARAM_YUV;
//    private DeviceStatusInfo guideInfo = new DeviceStatusInfo();

    private int communicationId = 4;
    private int paletteIndex = 0;

    public DeviceConfig() {
    }

    public boolean isParamSwitch() {
        return paramSwitch;
    }

    public void setParamSwitch(boolean paramSwitch) {
        this.paramSwitch = paramSwitch;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public GuideUsbVideoMode getGuideUsbVideoMode() {
        return guideUsbVideoMode;
    }

    public void setGuideUsbVideoMode(GuideUsbVideoMode guideUsbVideoMode) {
        this.guideUsbVideoMode = guideUsbVideoMode;
    }

    public int getCommunicationId() {
        return communicationId;
    }

    public void setCommunicationId(int communicationId) {
        this.communicationId = communicationId;
    }

    public int getPaletteIndex() {
        return paletteIndex;
    }

    public void setPaletteIndex(int paletteIndex) {
        this.paletteIndex = paletteIndex;
    }
}
