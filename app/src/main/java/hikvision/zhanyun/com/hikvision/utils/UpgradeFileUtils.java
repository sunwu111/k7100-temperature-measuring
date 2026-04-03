package hikvision.zhanyun.com.hikvision.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class UpgradeFileUtils {

    /**
     * 备份配置文件到temp目录
     */
    public static boolean backupConfig(String DATA_DIR, String temp) {
        try {

            File tempDir = new File(DATA_DIR + File.separator + temp);
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            copyFile(
                    DATA_DIR + File.separator + "config.json",
                    DATA_DIR + File.separator + temp + File.separator + "config.json"
            );

            copyFile(
                    DATA_DIR + File.separator + "settings.json",
                    DATA_DIR + File.separator + temp + File.separator + "settings.json"
            );

            return true;

        } catch (Exception e) {
            Log.e("UpgradeFileUtils","e::"+e.getMessage());
        }

        return false;
    }

    /**
     * 回滚配置文件
     */
    public static boolean rollbackConfig(String DATA_DIR, String temp) {
        try {

            copyFile(
                    DATA_DIR + File.separator + temp + File.separator + "config.json",
                    DATA_DIR + File.separator + "config.json"
            );

            copyFile(
                    DATA_DIR + File.separator + temp + File.separator + "settings.json",
                    DATA_DIR + File.separator + "settings.json"
            );

            return true;

        } catch (Exception e) {
            Log.e("UpgradeFileUtils","e::"+e.getMessage());
        }

        return false;
    }

    /**
     * 删除temp目录
     */
    public static void clearTemp(String DATA_DIR, String temp) {
        File dir = new File(DATA_DIR + File.separator + temp);
        deleteDir(dir);
    }

    /**
     * 文件复制
     */
    public static void copyFile(String srcPath, String dstPath) throws Exception {

        File src = new File(srcPath);
        File dst = new File(dstPath);

        if (!src.exists()) {
            throw new Exception("Source file not exist: " + srcPath);
        }

        File parent = dst.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }

        byte[] buffer = new byte[8192];

        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {

            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }

    /**
     * 删除目录（递归）
     */
    private static void deleteDir(File dir) {

        if (dir == null || !dir.exists()) {
            return;
        }

        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDir(file);
                }
            }
        }

        dir.delete();
    }
}