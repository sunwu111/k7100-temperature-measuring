package hikvision.zhanyun.com.hikvision.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 工具类：获取 /data/media 挂载点的磁盘使用百分比
 */
public final class StorageUtil {

    private StorageUtil() {
        // 工具类禁止实例化
    }

    /**
     * 获取 /data/media 的使用百分比（例如：9）
     *
     * @return 使用百分比（0-100），若获取失败或无法解析则返回 -1
     */
    public static int getDataMediaUsagePercent() {
        Process process = null;
        BufferedReader reader = null;
        try {
            // 执行 df -h 命令
            process = Runtime.getRuntime().exec("df -h");
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                // 查找包含 /data/media 的行
                if (line.contains("/data/media")) {
                    // 按空白字符分割（支持多个空格或制表符）
                    String[] parts = line.split("\\s+");
                    // df -h 输出的典型列：Filesystem Size Used Avail Use% Mounted on
                    // 第5列（索引4）是 Use% 字段，例如 "9%" 或 "42%"
                    if (parts.length >= 5) {
                        String usageStr = parts[4];
                        // 去除百分号并尝试转换为整数
                        if (usageStr.endsWith("%")) {
                            usageStr = usageStr.substring(0, usageStr.length() - 1);
                        }
                        try {
                            return Integer.parseInt(usageStr);
                        } catch (NumberFormatException e) {
                            // 解析失败则继续，但一般不会发生
                        }
                    }
                }
            }

            // 等待进程结束（可选，但可确保资源回收）
            process.waitFor();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 关闭流和销毁进程
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
            if (process != null) {
                process.destroy();
            }
        }
        return -1; // 默认返回 -1 表示失败
    }
}