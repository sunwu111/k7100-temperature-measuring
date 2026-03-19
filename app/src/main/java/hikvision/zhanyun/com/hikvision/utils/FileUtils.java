package hikvision.zhanyun.com.hikvision.utils;

import java.io.FileOutputStream;
import java.io.FileWriter;

public class FileUtils {

    public void append(String filename, byte[] data, int len) {
        try{
            FileOutputStream fos = new FileOutputStream(filename, true);
            fos.write(data, 0, len);
            fos.close();
        } catch (Exception e) {
            Log.i(Log.TAG, "写文件异常：" + e.getMessage());
        }
    }

    public void append(String filename, String content) {
        try {
            FileWriter fw = new FileWriter(filename, true);
            fw.write(content);
            fw.close();
        } catch (Exception e) {
            Log.i(Log.TAG, "写文件异常：" + e.getMessage());
        }
    }

}
