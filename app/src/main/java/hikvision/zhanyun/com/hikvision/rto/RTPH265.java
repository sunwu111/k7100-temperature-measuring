package hikvision.zhanyun.com.hikvision.rto;

import hikvision.zhanyun.com.hikvision.utils.Log;

public class RTPH265 extends RTPCodec {

    public RTPH265(int mssrc) {
        super(mssrc);
    }

    public byte[] rtpToNalu(byte rtp[], int length) {
        int rtp_header_len = 12;
        if (length < 12 + 2) return null;  // bad packet

        int extension = (rtp[0] >> 4) & 1;  // X: 扩展为是否为1
        if (extension > 0) {
            if (length < rtp_header_len + 4) return null;
            // 计算扩展头的长度
            int extLen = (rtp[14] << 8) + rtp[15];
            rtp_header_len += (extLen + 1) * 4;
        }

        int payload_length = length - rtp_header_len;
        if (payload_length == 0) return null; // 空包，原样返回一个空包！

        byte payload_header = rtp[rtp_header_len];
        byte payload_type = (byte) ((payload_header >> 1) & 0x3F);

        try {
            if (payload_type == 32 || payload_type == 33 || payload_type == 34 || payload_type == 39 ||
                    payload_type == 1) {
                bos.reset();
                byte[] payload = new byte[payload_length];
                System.arraycopy(rtp, rtp_header_len, payload, 0, payload_length);
                bos.write(new byte[]{0x0, 0x0, 0x0, 0x1});
                bos.write(payload);
                return bos.toByteArray();
            } else if (payload_type == 49) {
                // RTP Header + Payload Header + FU Header + Payload
                // 12字节       2字节            1字节       2字节
                byte fu_header = rtp[rtp_header_len + 2];
                byte start_flag = (byte)(fu_header & 0x80);
                byte end_flag = (byte)(fu_header & 0x40);
                //Payload Header:
                /* create the HEVC payload header and transmit the buffer as fragmentation units (FU)
                        0                   1
                        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
                        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
                        |F|   Type    |  LayerId  | TID |
                        +-------------+-----------------+
                            F       = 0
                            Type    = 49 (fragmentation unit (FU))
                            LayerId = 0
                            TID     = 1 */
                //FU Header
                //|0|1|2|3|4|5|6|7|
                //+-+-+-+-+-+-+-+-+
                //|S|E| FuType    |

                byte[] payload = new byte[payload_length - 3];
                System.arraycopy(rtp, rtp_header_len + 3, payload, 0, payload_length - 3);

                //Log.i(Log.TAG, String.format("rtp length=%d, start=%d, end=%d", length, start_flag, end_flag));
                if (start_flag != 0) { // 第一个分片
                    byte fu_type = (byte) (fu_header & 0x3F);
                    byte fu_tid = 0x01;

                    bos.reset();
                    bos.write(new byte[]{0x0, 0x0, 0x0, 0x01});
                    bos.write(fu_type << 1);
                    bos.write(fu_tid);
                    bos.write(payload);
                } else if (end_flag != 0) { // 最后一个分片
                    bos.write(payload);
                    return bos.toByteArray();
                } else {
                    bos.write(payload);
                }
            } else {
                Log.i(Log.TAG, "H265解析，不支持的NALU Type : " + payload_type);
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "H265解析异常：" + e.getMessage());
        }
        return null;
    }
}
