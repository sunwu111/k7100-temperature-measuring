package hikvision.zhanyun.com.hikvision.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class JsonUtils {

    /**
     * 将sourceFile中的photoTimeTable复制到targetFile中
     * @param sourcePath 源json文件路径
     * @param targetPath 目标json文件路径
     */
    public static void replacePhotoTimeTable(String sourcePath, String targetPath) {
        try {
            // 读取源文件
            String sourceJsonStr = readFile(sourcePath);
            JSONObject sourceJson = new JSONObject(sourceJsonStr);

            // 获取photoTimeTable
            JSONArray photoTimeTable = sourceJson.getJSONArray("photoTimeTable");

            // 读取目标文件
            String targetJsonStr = readFile(targetPath);
            JSONObject targetJson = new JSONObject(targetJsonStr);

            // 替换photoTimeTable
            targetJson.put("photoTimeTable", photoTimeTable);

            // 写回目标文件
            writeFile(targetPath, targetJson.toString(4)); // 4表示格式化缩进

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 读取文件
     */
    private static String readFile(String path) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(new File(path)));
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    /**
     * 写入文件
     */
    private static void writeFile(String path, String content) throws Exception {
        FileWriter writer = new FileWriter(new File(path));
        writer.write(content);
        writer.flush();
        writer.close();
    }


    public static String getFileNameWithoutExtension(String path) {
        if (path == null) return null;

        File file = new File(path);
        String name = file.getName();   // ZJ7360.zip

        int dotIndex = name.lastIndexOf(".");
        if (dotIndex > 0) {
            return name.substring(0, dotIndex);
        }

        return name;
    }

}