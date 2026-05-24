package hikvision.zhanyun.com.hikvision;

import java.util.List;

/**
 * RTSP拉流客户端回调接口
 */
public interface RtspClientCallback {
    /**
     * 当收到RTP数据包时回调
     *
     * @param channel The channel of the packet, 0 = RTP, 1 = RTCP, other value setup by user
     * @param packet  The raw RTP packet，RTP裸数据包，包括 RTP Header + Data
     * @param len     The packet length，数据包长度
     */
    void onPacket(int channel, byte[] packet, int len);

    /**
     * 当收到 RTSP 响应包时回调
     *
     * @param headers 命令响应包头
     * @param body    命令响应数据体
     */
    void onResponse(List<String> headers, byte[] body);
}
