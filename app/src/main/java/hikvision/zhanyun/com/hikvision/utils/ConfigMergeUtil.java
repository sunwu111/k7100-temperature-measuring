package hikvision.zhanyun.com.hikvision.utils;

import com.alibaba.fastjson.JSONArray;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Iterator;


public class ConfigMergeUtil {


    public static boolean mergeConfig(String config1Path, String config2Path) {
        try {

            JSONObject config1 = new JSONObject(readFile(config1Path));
            JSONObject config2 = new JSONObject(readFile(config2Path));

            mergeJson(config1, config2);

            applySpecialRules(config2);

            FileWriter writer = new FileWriter(config2Path);
            writer.write(config2.toString(4));
            writer.flush();
            writer.close();

            return true;

        } catch (Exception e) {
            Log.e("ConfigMerge", "e::" + e.getMessage());
            return false;
        }
    }


    private static void mergeJson(JSONObject source, JSONObject target) throws Exception {

        Iterator<String> keys = source.keys();

        while (keys.hasNext()) {
            String key = keys.next();

            Object value1 = source.get(key);

            if (target.has(key)) {

                Object value2 = target.get(key);

                if (value1 instanceof JSONObject && value2 instanceof JSONObject) {
                    mergeJson((JSONObject) value1, (JSONObject) value2);
                }

                else if (value1 instanceof JSONArray && value2 instanceof JSONArray) {
                    target.put(key, value1);
                }
                else {
                    target.put(key, value1);
                }

            } else {
                target.put(key, value1);
            }
        }
    }


    private static void applySpecialRules(JSONObject config) throws Exception {

        if (config.has("main_board")) {

            String display = android.os.Build.DISPLAY;
            Log.e(Log.TAG, "Build.DISPLAY: " + display);

            if (display != null) {
                String upper = display.toUpperCase();

                if (upper.startsWith("K7100") || upper.contains("K7100")) {
                    config.put("main_board", 1);
                } else {
                    config.put("main_board", 0);
                }
            } else {
                config.put("main_board", 1);
            }
        }
    }


    /**
     * 读取文件为字符串
     */
    private static String readFile(String path) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(path));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }

        reader.close();
        return sb.toString();
    }
}