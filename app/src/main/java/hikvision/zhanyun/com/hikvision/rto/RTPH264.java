package hikvision.zhanyun.com.hikvision.rto;



/*
H264帧由NALU头和NALU主体组成。
NALU头由一个字节组成,它的语法如下:

      +---------------+
      |0|1|2|3|4|5|6|7|
      +-+-+-+-+-+-+-+-+
      |F|NRI|  Type   |
      +---------------+

F: 1个比特.
  forbidden_zero_bit. 在 H.264 规范中规定了这一位必须为 0.

NRI: 2个比特.
  nal_ref_idc. 取00~11,似乎指示这个NALU的重要性,如00的NALU解码器可以丢弃它而不影响图像的回放,0～3，取值越大，表示当前NAL越重要，需要优先受到保护。如果当前NAL是属于参考帧的片，或是序列参数集，或是图像参数集这些重要的单位时，本句法元素必需大于0。
Type: 5个比特.
        nal_unit_type. 这个NALU单元的类型,1～12由H.264使用，24～31由H.264以外的应用使用,简述如下:

        0     没有定义
        1-23  NAL单元  单个 NAL 单元包
        1     不分区，非IDR图像的片
        2     片分区A
        3     片分区B
        4     片分区C
        5     IDR图像中的片
        6     补充增强信息单元（SEI）
        7     SPS
        8     PPS
        9     序列结束
        10    序列结束
        11    码流借宿
        12    填充
        13-23 保留

        24    STAP-A   单一时间的组合包
        25    STAP-B   单一时间的组合包
        26    MTAP16   多个时间的组合包
        27    MTAP24   多个时间的组合包
        28    FU-A     分片的单元
        29    FU-B     分片的单元
        30-31 没有定义
*/

/**
 * Encodes/Decodes RTP/H264 packets
 * http://tools.ietf.org/html/rfc3984
 *
 * @author pquiring, improved by Kingron
 */

import hikvision.zhanyun.com.hikvision.utils.Log;

public class RTPH264 extends RTPCodec {
    public RTPH264(int ssrc) {
        super(ssrc);
    }

    /**
     * input  输入H264码流字节
     * idx    从输入码流的idx字节开始查找
     * type   要查找的Nalu Type
     * begin  输入码流的开始字节码为NaluType字节而非0x00000001，设置为true
     * */
    private byte[] findNalUnit(byte[] input, int idx, int type, boolean begin) {
        int i = 0;
        int start = 0;
        int end = 0;
        byte[] found = null;

        while (i + idx + 4 < input.length) {
            // 找开始索引
            if (((begin && (input[i + idx] & 0x1F) == type) ||
                    (((input[i + idx] << 24 | input[i + idx + 1] << 16 | input[i + idx + 2] << 8 | input[i + idx + 3]) == 0x00000001)
                            && (input[i + idx + 4] & 0x1F) == type))) {
                i = start = i + idx;
                break;
            }
            ++i;
        }

        while (++i+4 < input.length) {
            // 找结尾索引
            if (((input[i] << 24) | (input[i+1] << 16) | (input[i+2] << 8) | input[i+3]) == 0x00000001) {
                end = i;
                if (begin) {
                    found = new byte[end - start + 4];
                    System.arraycopy(new byte[]{0, 0, 0, 1}, 0, found, 0, 4);
                    System.arraycopy(input, start, found, 4, end - start);
                } else {
                    found = new byte[end - start];
                    System.arraycopy(input, start, found, 0, found.length);
                }
                break;
            }
        }
        return found;
    }

    @Override
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
        byte type = (byte) (payload_header & 0x1F);
        timestamp = gettimestamp(rtp, 0) & 0xFFFFFFFFL;

        try {
            if (type == 6 || type == 7 || type == 8 || type == 1) {
                bos.reset();
                byte[] payload = new byte[payload_length];
                System.arraycopy(rtp, rtp_header_len, payload, 0, payload_length);
                bos.write(new byte[]{0x0, 0x0, 0x0, 0x1});
                bos.write(payload);
                if (type == 7) {
                    sps = new byte[payload_length];
                    System.arraycopy(payload, 0, sps, 0, sps.length);
                }
                if (type == 8) {
                    pps = new byte[payload_length];
                    System.arraycopy(payload, 0, pps, 0, pps.length);
                }
                return bos.toByteArray();
            } else if (type == 28) { // FU-A: 28,  FU-B: 29
                //              FU indicator
                // RTP Header + Payload Header + FU Header + Payload
                // 12字节        1字节            1字节        1字节
                byte fu_header = rtp[rtp_header_len + 1];
                byte start_flag = (byte)(fu_header & 0x80);
                byte end_flag = (byte)(fu_header & 0x40);
                /*FU indicator
                0 1 2 3 4 5 6 7
                +-+-+-+-+-+-+-+
                |F|NRI|  Type |
                +-------------+-----------------+
                    F       = 0
                    NRI     = nal_ref_idc  indicates that the content of the NAL unit is whether or not
                                           used to reference pictures for inter picture prediction
                    Type    = 28 or 29
                //FU Header
                //|0|1|2|3|4|5|6|7|
                //+-+-+-+-+-+-+-+-+
                //|S|E|R| FuType  |
                /*
                *  S:  indicates the start of a fragmented NAL unit
                *  E:  indicates the end of a fragmented NAL unit
                *  R:  Reserved bit MUST be equal to 0
                * FuType: NAL unit payload type
                * */
                byte[] payload = new byte[payload_length - 2];
                System.arraycopy(rtp, rtp_header_len + 2, payload, 0, payload_length - 2);

                if (start_flag != 0) { // 第一个分片
                    bos.reset();
                    bos.write(new byte[]{0x0, 0x0, 0x0, 0x01});
                    byte nri = (byte)(payload_header & 0x60);
                    type = (byte)(fu_header & 0x1f);
                    if (type == 0x07 && sps == null && pps == null) {
                        // 针对研迪机芯
                        sps = findNalUnit(rtp, rtp_header_len + 1, 0x07, true);
                        pps = findNalUnit(rtp, rtp_header_len + 1, 0x08, false);
                    }
                    bos.write(nri + type);
                    bos.write(payload);
                } else if (end_flag != 0) { // 最后一个分片
                    bos.write(payload);
                    return bos.toByteArray();
                } else {
                    bos.write(payload);
                }
            } else {
                Log.i(Log.TAG, "H264解析，不支持的NALU Type : " + type);
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "H264解析异常：" + e.getMessage());
        }
        return null;
    }
}
