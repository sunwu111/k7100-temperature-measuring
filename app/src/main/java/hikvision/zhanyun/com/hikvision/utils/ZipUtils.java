package hikvision.zhanyun.com.hikvision.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class ZipUtils {

    public static boolean unzip(String zipDir, String zipName) {
        String zipPath = zipDir + File.separator + zipName;
        String destDir = zipDir;
        byte[] buffer = new byte[8192];

        try {
            File dir = new File(destDir);
            if (!dir.exists()) {
                dir.mkdirs();
                Log.e(Log.TAG, "解压路径: " + dir.getAbsolutePath());
            }

            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath));
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // 可选：防止 Zip Slip 攻击，检查路径是否包含 ".."
                if (entryName.contains("..") || entryName.startsWith("/")) {
                    Log.e(Log.TAG, "非法条目路径，已跳过: " + entryName);
                    continue;
                }

                File newFile = new File(destDir, entryName);

                if (entry.isDirectory()) {
                    newFile.mkdirs();
                    Log.e(Log.TAG, "创建目录: " + newFile.getAbsolutePath());
                } else {
                    File parent = new File(newFile.getParent());
                    if (!parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    Log.e(Log.TAG, "解压文件: " + newFile.getAbsolutePath());
                }
                zis.closeEntry();
            }
            zis.close();
            return true;
        } catch (Exception e) {
            Log.e("UNZIP","e::"+e.getMessage());
        }
        return false;
    }



    public static void moveSameNameApk(String sourceDir, String targetDir) {

        try {

            File srcDir = new File(sourceDir);

            if (!srcDir.exists() || !srcDir.isDirectory()) {
                return;
            }

            // 目录名
            String dirName = srcDir.getName();

            // 要找的apk
            String apkName = dirName + ".apk";

            File apkFile = new File(srcDir, apkName);

            if (!apkFile.exists()) {
                return;
            }

            File target = new File(targetDir, apkName);

            // 确保目标目录存在
            File targetFolder = new File(targetDir);
            if (!targetFolder.exists()) {
                targetFolder.mkdirs();
            }

            boolean result = apkFile.renameTo(target);

            if (!result) {
                Log.e("UNZIP","APK移动失败");
            }

        } catch (Exception e) {
            Log.e("UNZIP","e::"+e.getMessage());
        }
    }

}



