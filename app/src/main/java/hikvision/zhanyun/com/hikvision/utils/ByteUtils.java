package hikvision.zhanyun.com.hikvision.utils;

public class ByteUtils {

    //byte 数组与 int 的相互转换
    public static int byteArrayToInt(byte[] b) {
        return   b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
    }

    public static byte[] intToByteArray(int a) {
        return new byte[] {
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)
        };
    }

    public static byte[] longToByteArray(long id) {
        byte[] arr = new byte[4];
        arr[0] = (byte) (id >> 24);
        arr[1] = (byte) ((id & 0x00FF0000) >> 16);
        arr[2] = (byte) ((id & 0x0000FF00) >> 8);
        arr[3] = (byte) (id & 0x000000FF);
        return arr;
    }
}
