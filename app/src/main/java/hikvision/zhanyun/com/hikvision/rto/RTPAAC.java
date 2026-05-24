package hikvision.zhanyun.com.hikvision.rto;

public class RTPAAC {

    private int SAMPLE_RATE = 16000;
    private int CHANNEL_COUNT = 1;
    private int BIT_RATE = 64000;

    private long rtpTimestamp = 0;


    public byte[] rtpToAac(byte[] rtp, int length) {
        int rtpHeaderLen = 12;
        if (length <= rtpHeaderLen + 4) return null;

        int extension = (rtp[0] >> 4) & 0x01;
        if (extension == 1) {
            int extLen =
                    ((rtp[14] & 0xFF) << 8) |
                            (rtp[15] & 0xFF);
            rtpHeaderLen += (extLen + 1) * 4;
        }

        rtpTimestamp = getTimestamp(rtp);

        int auHeaderLenBits =
                ((rtp[rtpHeaderLen] & 0xFF) << 8) |
                        (rtp[rtpHeaderLen + 1] & 0xFF);

        int auHeaderLenBytes = (auHeaderLenBits + 7) / 8;
        if (auHeaderLenBytes < 2) return null;

        int auHeaderStart = rtpHeaderLen + 2;

        int auSize =
                ((rtp[auHeaderStart] & 0xFF) << 5) |
                        ((rtp[auHeaderStart + 1] & 0xF8) >> 3);

        int aacDataOffset = rtpHeaderLen + 2 + auHeaderLenBytes;
        if (aacDataOffset + auSize > length) return null;

        byte[] aac = new byte[auSize];
        System.arraycopy(rtp, aacDataOffset, aac, 0, auSize);

        return aac;
    }

    private long getTimestamp(byte[] rtp) {
        return ((rtp[4] & 0xFFL) << 24) |
                ((rtp[5] & 0xFFL) << 16) |
                ((rtp[6] & 0xFFL) << 8) |
                (rtp[7] & 0xFFL);
    }

    public long getPtsUs() {
        return (rtpTimestamp * 1_000_000L) / SAMPLE_RATE;
    }

    public int getSampleRate() {
        return SAMPLE_RATE;
    }

    public int getChannelCount() {
        return CHANNEL_COUNT;
    }

    public int getBitRate() {
        return BIT_RATE;
    }

    public byte[] getAudioSpecificConfig() {
        return new byte[]{(byte) 0x14, (byte) 0x08};
    }
}




