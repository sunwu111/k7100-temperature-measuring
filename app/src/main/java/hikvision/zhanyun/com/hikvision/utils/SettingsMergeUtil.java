package hikvision.zhanyun.com.hikvision.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Iterator;

public class SettingsMergeUtil {

    public static boolean mergeSettings(String setting1Path, String setting2Path) {

        try {
            JSONObject setting1 = new JSONObject(readFile(setting1Path));
            JSONObject setting2 = new JSONObject(readFile(setting2Path));

            mergeJson(setting1, setting2);

            FileWriter writer = new FileWriter(setting2Path);
            writer.write(setting2.toString(4));
            writer.flush();
            writer.close();

            return true;

        } catch (Exception e) {
            Log.e("SettingsMerge", "e::" + e.getMessage());
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
