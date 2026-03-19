package hikvision.zhanyun.com.hikvision;
import static hikvision.zhanyun.com.hikvision.MainActivity.DATA_DIR;
import static lyh.Utils.stringFromFile;
import static lyh.Utils.stringToFile;
import com.alibaba.fastjson.JSON;
import java.io.File;
import hikvision.zhanyun.com.hikvision.utils.Log;


public class OffLineRecode {
    private String filename = DATA_DIR + "offline.json";

    public static class Record {
        public long begin;
        public long end;
    }

    public void updateRecode() {
        try {
            Record record;
            String s = stringFromFile(filename);
            if (s == null) {
                record = new Record();
                record.begin = System.currentTimeMillis();
            } else {
                record = JSON.parseObject(s, Record.class);
                record.end = System.currentTimeMillis();
            }
            s = JSON.toJSONString(record, true);
            stringToFile(filename, s);
        } catch (Exception e) {
            Log.i(Log.TAG, "更新离线记录异常 ：" + e);
        }
    }

    public int getRecordGap() {
        try {
            String s = stringFromFile(filename);
            if (s == null) return 0;

            Record record = JSON.parseObject(s, Record.class);
            if (record.begin == 0 || record.end == 0) return 0;

            return (int) (record.end - record.begin);
        } catch (Exception e) {
            Log.i(Log.TAG, "获取离线记录异常 ：" + e);
            return 0;
        }
    }

    public void clearRecord() {
        new File(filename).delete();
    }
}
