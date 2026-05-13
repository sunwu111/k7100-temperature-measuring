package hikvision.zhanyun.com.hikvision.utils;

import android.os.Environment;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import hikvision.zhanyun.com.hikvision.Settings;

public class VideoFiles {

    public static final String DATA_DIR = Environment.getExternalStorageDirectory() + "/zhjinrui/spgp/";

    public static final String VIDEO_FILES_COUNT = DATA_DIR + "video_files_count.json";

    public static final String VIDEO_FILES_LIST = DATA_DIR + "video_files_list.txt";

//    public static

//    public static

    /**
     * 从 VIDEO_FILES_COUNT 文件中读取缓存的录像文件个数（使用 fastjson）
     */
    public static int readCachedCount() {
        File file = new File(VIDEO_FILES_COUNT);
        if (!file.exists()) {
            return 0;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject json = JSONObject.parseObject(sb.toString());
            return json.getIntValue("count");
        } catch (Exception e) {
            Log.e(Log.TAG, "读取缓存文件失败" + e);
            return 0;
        }
    }

    /**
     * 将录像文件个数 count 保存到 VIDEO_FILES_COUNT 文件（使用 fastjson）
     */
    public static void saveCountToFile(int count) {
        File file = new File(VIDEO_FILES_COUNT);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JSONObject json = new JSONObject();
            json.put("count", count);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json.toJSONString());
            }
        } catch (Exception e) {
            Log.e(Log.TAG, "保存缓存文件失败" + e);
        }
    }


    public static Settings.FileList readCachedFileList(int channel, int videoType, Settings.FileList fallback) {

        Log.e(Log.TAG,"读取录像文件列表");

        File file = new File(VIDEO_FILES_LIST);
        if (!file.exists()) return fallback;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject root = JSON.parseObject(sb.toString());
            Settings.FileList list = new Settings.FileList();
            list.channel = root.getByteValue("channel");
            list.begin = root.getIntValue("begin");
            list.end = root.getIntValue("end");
            list.type = root.getIntValue("type");
            JSONArray filesArray = root.getJSONArray("files");
            if (filesArray != null && filesArray.size() > 0) {
                List<Settings.FileItem> items = new ArrayList<>();
                for (int i = 0; i < filesArray.size(); i++) {
                    JSONObject fObj = filesArray.getJSONObject(i);
                    Settings.FileItem item = new Settings.FileItem();
                    // begin & end
                    JSONObject beginObj = fObj.getJSONObject("begin");
                    if (beginObj != null) {
                        long timestamp = beginObj.getLongValue("timestamp"); // 假设TimeRecord有timestamp字段
                        item.begin = new Settings.TimeRecord(timestamp);
                    }
                    JSONObject endObj = fObj.getJSONObject("end");
                    if (endObj != null) {
                        long timestamp = endObj.getLongValue("timestamp");
                        item.end = new Settings.TimeRecord(timestamp);
                    }
                    item.size = fObj.getIntValue("size");
                    item.type = fObj.getIntValue("type");
                    items.add(item);
                }
                list.files = items.toArray(new Settings.FileItem[0]);
            }
            return list;
        } catch (Exception e) {
            Log.e(Log.TAG, "读取缓存 FileList 失败" + e);
            return fallback;
        }
    }



    public static void saveFileListToFile(Settings.FileList list) {
        File file = new File(VIDEO_FILES_LIST);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            String json = JSON.toJSONString(list);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(json);
            }
        } catch (Exception e) {
            Log.e(Log.TAG, "保存缓存 FileList 失败" + e);
        }
    }

}
