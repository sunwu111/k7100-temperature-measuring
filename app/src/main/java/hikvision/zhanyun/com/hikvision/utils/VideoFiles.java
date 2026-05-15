package hikvision.zhanyun.com.hikvision.utils;

import android.os.Environment;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import hikvision.zhanyun.com.hikvision.Settings;

public class VideoFiles {

    public static final String DATA_DIR = Environment.getExternalStorageDirectory() + "/zhjinrui/spgp/";

    public static final String VIDEO_FILES_COUNT = DATA_DIR + "video_files_count.json";

    public static final String VIDEO_FILES_LIST = DATA_DIR + "video_files_list.json";

//    public static

//    public static

    /**
     * 从 VIDEO_FILES_COUNT 文件中读取缓存的录像文件个数（使用 fastjson）
     */
    public static int readCachedCount(int channel,
                                      int videoType,
                                      Settings.TimeRecord startTime,
                                      Settings.TimeRecord stopTime) {

        File file = new File(VIDEO_FILES_LIST);
        if (!file.exists()) {
            return 0;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {

            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONObject root = JSON.parseObject(sb.toString());

//            // channel过滤
//            int cacheChannel = root.getIntValue("channel");
//            if (cacheChannel != channel) {
//                Log.e(Log.TAG, "缓存channel不匹配");
//                return 0;
//            }

            JSONArray filesArray = root.getJSONArray("files");
            if (filesArray == null || filesArray.isEmpty()) {
                return 0;
            }

            long queryStart = startTime.timestamp;
            long queryEnd = stopTime.timestamp;

            int count = 0;

            for (int i = 0; i < filesArray.size(); i++) {

                JSONObject fObj = filesArray.getJSONObject(i);

//                int type = fObj.getIntValue("type");

//                // videoType过滤
//                if (videoType != -1 && type != videoType) {
//                    continue;
//                }

                JSONObject beginObj = fObj.getJSONObject("begin");
                JSONObject endObj = fObj.getJSONObject("end");

                if (beginObj == null || endObj == null) {
                    continue;
                }

                long fileBegin = beginObj.getLongValue("timestamp");
                long fileEnd = endObj.getLongValue("timestamp");

                // 时间区间重叠判断
                // 文件结束 >= 查询开始
                // 文件开始 <= 查询结束
                if (fileEnd >= queryStart && fileBegin <= queryEnd) {
                    count++;
                }
            }

            Log.i(Log.TAG, "缓存录像数量：" + count);

            return count;

        } catch (Exception e) {
            Log.e(Log.TAG, "读取缓存文件失败：" + e);
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


    public static Settings.FileList readCachedFileList(int channel,
                                                       int videoType,
                                                       String startTime,
                                                       String stopTime,
                                                       Settings.FileList fallback) {

        Log.e(Log.TAG, "读取录像文件列表");

        File file = new File(VIDEO_FILES_LIST);

        if (!file.exists()) {
            return fallback;
        }

        try {

            // ------------------------------------------------
            // String时间转时间戳
            // 格式：yyyy-MM-dd-HH-mm-ss
            // ------------------------------------------------
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());

            long queryStart = sdf.parse(startTime).getTime();
            long queryEnd = sdf.parse(stopTime).getTime();

            BufferedReader reader = new BufferedReader(new FileReader(file));

            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            reader.close();

            JSONObject root = JSON.parseObject(sb.toString());

            int cacheChannel = root.getIntValue("channel");

            // channel不匹配
            if (cacheChannel != channel) {
                Log.e(Log.TAG, "缓存channel不匹配");
                return fallback;
            }

            JSONArray filesArray = root.getJSONArray("files");

            if (filesArray == null || filesArray.isEmpty()) {
                return fallback;
            }

            List<Settings.FileItem> items = new ArrayList<>();

            for (int i = 0; i < filesArray.size(); i++) {

                JSONObject fObj = filesArray.getJSONObject(i);

                int type = fObj.getIntValue("type");

                // videoType过滤
                if (videoType != -1 && type != videoType) {
                    continue;
                }

                JSONObject beginObj = fObj.getJSONObject("begin");
                JSONObject endObj = fObj.getJSONObject("end");

                if (beginObj == null || endObj == null) {
                    continue;
                }

                long fileBegin = beginObj.getLongValue("timestamp");
                long fileEnd = endObj.getLongValue("timestamp");

                // ------------------------------------------------
                // 时间区间重叠判断
                // ------------------------------------------------
                if (fileEnd < queryStart || fileBegin > queryEnd) {
                    continue;
                }

                Settings.FileItem item = new Settings.FileItem();

                item.begin = new Settings.TimeRecord(fileBegin);
                item.end = new Settings.TimeRecord(fileEnd);

                item.filename = fObj.getString("filename");
                item.size = fObj.getIntValue("size");
                item.type = type;

                items.add(item);
            }

            Settings.FileList list = new Settings.FileList();

            list.channel = (byte) cacheChannel;
            list.begin = queryStart;
            list.end = queryEnd;
            list.type = videoType;
            list.files = items.toArray(new Settings.FileItem[0]);

            return list;

        } catch (Exception e) {

            Log.e(Log.TAG, "读取缓存 FileList 失败：" + e);

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
