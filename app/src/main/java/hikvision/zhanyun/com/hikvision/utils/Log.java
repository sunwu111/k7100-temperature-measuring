//package hikvision.zhanyun.com.hikvision.utils;
//
//import java.io.File;
//import java.io.FileWriter;
//import java.io.FilenameFilter;
//import java.io.IOException;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//
//import android.os.Environment;
//
//
//public class Log {
//    public static final String TAG = "jr_log";
//    private static final String NEW_LINE = System.getProperty("line.separator");
//    public static boolean mLogcatAppender = true;
//
//    private static File mLogBaseDir;
//    private static String mCurrentDate = "";
//    private static File mCurrentLogFile = null;
//
//    static {
//        String baseLogPath = Environment.getExternalStorageDirectory() + "/zhjinrui/spgp/logs/";
//        mLogBaseDir = new File(baseLogPath);
//        if (!mLogBaseDir.exists()) {
//            mLogBaseDir.mkdirs();
//        }
//        initializeLogFile();
//        logDeviceInfo();
//    }
//
//    /**
//     * 初始化日志文件，检查是否需要创建新的日志文件
//     */
//    private static synchronized void initializeLogFile() {
//        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
//        String today = dateFormat.format(new Date());
//
//        // 如果日期没变，且文件已存在，则继续使用当前文件
//        if (today.equals(mCurrentDate) && mCurrentLogFile != null && mCurrentLogFile.exists()) {
//            return;
//        }
//
//        // 日期变化或文件不存在，创建新的日志文件
//        mCurrentDate = today;
//
//        // 解析年、月、日
//        String[] dateParts = today.split("-");
//        String year = dateParts[0];
//        String month = dateParts[1];
//        String day = dateParts[2];
//
//        // 创建年/月目录结构
//        File yearDir = new File(mLogBaseDir, year);
//        File monthDir = new File(yearDir, month);
//        if (!monthDir.exists()) {
//            monthDir.mkdirs();
//        }
//
//        // 创建日志文件
//        String logFileName = year+"-"+month+"-"+day + ".log";
//        mCurrentLogFile = new File(monthDir, logFileName);
//
//        // 创建新的日志文件
//        if (!mCurrentLogFile.exists()) {
//            try {
//                mCurrentLogFile.createNewFile();
//            } catch (final IOException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//    /**
//     * 获取当前日志文件
//     */
//    private static synchronized File getCurrentLogFile() {
//        initializeLogFile(); // 每次获取前检查日期是否变化
//        return mCurrentLogFile;
//    }
//
//    public static void i(String TAG, String message) {
//        appendLog(TAG + " : " + message);
//        if (mLogcatAppender) {
//            android.util.Log.i(TAG, message);
//        }
//    }
//
//    public static void d(String TAG, String message) {
//        appendLog(TAG + " : " + message);
//        if (mLogcatAppender) {
//            android.util.Log.d(TAG, message);
//        }
//    }
//
//    public static void e(String TAG, String message) {
//        appendLog(TAG + " : " + message);
//        if (mLogcatAppender) {
//            android.util.Log.e(TAG, message);
//        }
//    }
//
//    public static void v(String TAG, String message) {
//        appendLog(TAG + " : " + message);
//        if (mLogcatAppender) {
//            android.util.Log.v(TAG, message);
//        }
//    }
//
//    public static void w(String TAG, String message) {
//        appendLog(TAG + " : " + message);
//        if (mLogcatAppender) {
//            android.util.Log.w(TAG, message);
//        }
//    }
//
//    private static synchronized void appendLog(String text) {
//        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//        File logFile = getCurrentLogFile();
//
//        try {
//            final FileWriter fileOut = new FileWriter(logFile, true);
//            fileOut.append(sdf.format(new Date()) + " : " + text + NEW_LINE);
//            fileOut.close();
//        } catch (final IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private static void logDeviceInfo() {
//        appendLog("==============================启动标志==============================");
//        appendLog("Model : " + android.os.Build.MODEL);
//        appendLog("Brand : " + android.os.Build.BRAND);
//        appendLog("Product : " + android.os.Build.PRODUCT);
//        appendLog("Device : " + android.os.Build.DEVICE);
//        appendLog("Codename : " + android.os.Build.VERSION.CODENAME);
//        appendLog("Release : " + android.os.Build.VERSION.RELEASE);
//    }
//
//    /**
//     * 获取指定日期的日志文件
//     * @param date 日期字符串，格式：yyyy-MM-dd
//     * @return 对应的日志文件
//     */
//    public static File getLogFileByDate(String date) {
//        String[] dateParts = date.split("-");
//        String year = dateParts[0];
//        String month = dateParts[1];
//        String day = dateParts[2];
//
//        File yearDir = new File(mLogBaseDir, year);
//        File monthDir = new File(yearDir, month);
//        return new File(monthDir, day + ".log");
//    }
//
//    /**
//     * 获取所有日志文件列表
//     * @return 日志文件数组
//     */
//    public static File[] getAllLogFiles() {
//        // 递归查找所有.log文件
//        return findAllLogFiles(mLogBaseDir);
//    }
//
//    /**
//     * 递归查找所有日志文件
//     */
//    private static File[] findAllLogFiles(File baseDir) {
//        if (!baseDir.exists()) {
//            return new File[0];
//        }
//
//        return baseDir.listFiles(new FilenameFilter() {
//            @Override
//            public boolean accept(File dir, String name) {
//                File file = new File(dir, name);
//                if (file.isDirectory()) {
//                    // 如果是目录，递归查找
//                    return true;
//                }
//                return name.endsWith(".log");
//            }
//        });
//    }
//
//    /**
//     * 获取某年的所有月份目录
//     */
//    public static File[] getYearLogDirs() {
//        return mLogBaseDir.listFiles(new FilenameFilter() {
//            @Override
//            public boolean accept(File dir, String name) {
//                return new File(dir, name).isDirectory() && name.matches("\\d{4}");
//            }
//        });
//    }
//
//    /**
//     * 获取某年某月的所有日志文件
//     */
//    public static File[] getMonthLogFiles(String year, String month) {
//        File yearDir = new File(mLogBaseDir, year);
//        File monthDir = new File(yearDir, month);
//        if (!monthDir.exists()) {
//            return new File[0];
//        }
//        return monthDir.listFiles(new FilenameFilter() {
//            @Override
//            public boolean accept(File dir, String name) {
//                return name.endsWith(".log");
//            }
//        });
//    }
//}
//


package hikvision.zhanyun.com.hikvision.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.os.Environment;

//public class Log {
//    public static final String TAG = "jr_log";
//    private static final String NEW_LINE = System.getProperty("line.separator");
//    public static boolean mLogcatAppender = true;
//    final static File mLogFile;
//
//    static {
//        String fileLogPath = Environment.getExternalStorageDirectory() + "/zhjinrui/spgp/";
//        File logDir = new File(fileLogPath);
//        if (!logDir.exists()) {
//            logDir.mkdirs();
//        }
//        mLogFile = new File(fileLogPath, "logs.log");
//        if (mLogFile.length() > 1024 * 1024 * 50) {
//            //mLogFile.delete();  // 超过 50M，自动删除，防止文件太大
//            File mLogBackup = new File(fileLogPath, "logs.log.bak");
//            if (mLogBackup.exists()) mLogBackup.delete();
//            mLogFile.renameTo(mLogBackup);
//        }
//        if (!mLogFile.exists()) {
//            try {
//                mLogFile.createNewFile();
//            } catch (final IOException e) {
//                e.printStackTrace();
//            }
//        }
//        logDeviceInfo();
//    }
//
//    public static void i(String TAG, String message) {
//        appendLog(TAG + " : " + message);
//        if (mLogcatAppender) {
//            android.util.Log.i(TAG, message);
//        }
//    }
//
//    public static void d(String TAG, String message) {
//        appendLog(TAG + " : " + message);
//        if (mLogcatAppender) {
//            android.util.Log.d(TAG, message);
//        }
//    }
//
//    public static void e(String TAG, String message) {
//        appendLog(TAG + " : " + message);
//        if (mLogcatAppender) {
//            android.util.Log.e(TAG, message);
//        }
//    }
//
//    public static void v(String TAG, String message) {
//        appendLog(TAG + " : " + message);
//        if (mLogcatAppender) {
//            android.util.Log.v(TAG, message);
//        }
//    }
//
//    public static void w(String TAG, String message) {
//        appendLog(TAG + " : " + message);
//        if (mLogcatAppender) {
//            android.util.Log.w(TAG, message);
//        }
//    }
//
//    private static synchronized void appendLog(String text) {
//        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
//
//        try {
//            final FileWriter fileOut = new FileWriter(mLogFile, true);
//            fileOut.append(sdf.format(new Date()) + " : " + text + NEW_LINE);
//            fileOut.close();
//        } catch (final IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private static void logDeviceInfo() {
//        appendLog("==============================启动标志==============================");
//        appendLog("Model : " + android.os.Build.MODEL);
//        appendLog("Brand : " + android.os.Build.BRAND);
//        appendLog("Product : " + android.os.Build.PRODUCT);
//        appendLog("Device : " + android.os.Build.DEVICE);
//        appendLog("Codename : " + android.os.Build.VERSION.CODENAME);
//        appendLog("Release : " + android.os.Build.VERSION.RELEASE);
//    }
//
//}


public class Log {
    public static final String TAG = "jr_log";
    private static final String NEW_LINE = System.getProperty("line.separator");
    public static boolean mLogcatAppender = true;
    private static File mLogFile;
    private static File mLogBackup;
    private static final int MAX_LOG_SIZE = 5 * 1024 * 1024; // 5MB
//    private static final int MAX_LOG_SIZE = 1 * 1024; // 5MB

    static {
        String fileLogPath = Environment.getExternalStorageDirectory() + "/zhjinrui/spgp/";
        File logDir = new File(fileLogPath);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        mLogFile = new File(fileLogPath, "logs.log");
        mLogBackup = new File(fileLogPath, "logs.log.bak");

        // 初始化时检查日志文件大小，如果超过限制则合并到备份文件
        if (mLogFile.exists() && mLogFile.length() > MAX_LOG_SIZE) {
            mergeToBackup();
        }

        if (!mLogFile.exists()) {
            try {
                mLogFile.createNewFile();
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }

//        logDeviceInfo();
    }

    /**
     * 将当前日志合并到备份文件
     */
    private static synchronized void mergeToBackup() {
        try {
            // 如果当前日志文件不存在或为空，直接返回
            if (!mLogFile.exists() || mLogFile.length() == 0) {
                return;
            }

            // 读取当前日志内容
            StringBuilder currentLogContent = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(mLogFile));
            String line;
            while ((line = reader.readLine()) != null) {
                currentLogContent.append(line).append(NEW_LINE);
            }
            reader.close();

            // 将当前日志内容追加到备份文件
            FileWriter backupWriter = new FileWriter(mLogBackup, true); // true表示追加模式
            backupWriter.append(currentLogContent.toString());
            backupWriter.close();

            // 清空当前日志文件
            FileWriter currentWriter = new FileWriter(mLogFile, false); // false表示覆盖模式
            currentWriter.write("");
            currentWriter.close();

            // 检查备份文件大小，如果备份文件也超过5M，可以清空或保留（根据需求）
            // 这里我们保持备份文件不变，让它继续增长
            // 如果需要限制备份文件大小，可以取消下面的注释
            /*
            if (mLogBackup.length() > MAX_LOG_SIZE * 2) { // 备份文件最多10M
                FileWriter truncateWriter = new FileWriter(mLogBackup, false);
                truncateWriter.write("");
                truncateWriter.close();
            }
            */

        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 修改appendLog方法，写入前检查文件大小
     */
    private static synchronized void appendLog(String text) {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

        try {
            // 在写入前检查当前日志文件大小
            if (mLogFile.exists() && mLogFile.length() > MAX_LOG_SIZE) {
                mergeToBackup();
            }

            final FileWriter fileOut = new FileWriter(mLogFile, true);
            fileOut.append(sdf.format(new Date()) + " : " + text + NEW_LINE);
            fileOut.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }


    public static void forceMergeLogs() {
        mergeToBackup();
    }


    public static void i(String TAG, String message) {
        appendLog(TAG + " : " + message);
        if (mLogcatAppender) {
            android.util.Log.i(TAG, message);
        }
    }

    public static void d(String TAG, String message) {
        appendLog(TAG + " : " + message);
        if (mLogcatAppender) {
            android.util.Log.d(TAG, message);
        }
    }

    public static void e(String TAG, String message) {
        appendLog(TAG + " : " + message);
        if (mLogcatAppender) {
            android.util.Log.e(TAG, message);
        }
    }

    public static void v(String TAG, String message) {
        appendLog(TAG + " : " + message);
        if (mLogcatAppender) {
            android.util.Log.v(TAG, message);
        }
    }

    public static void w(String TAG, String message) {
        appendLog(TAG + " : " + message);
        if (mLogcatAppender) {
            android.util.Log.w(TAG, message);
        }
    }

    public static void logDeviceInfo() {
        appendLog("==============================启动标志==============================");
        appendLog("Model : " + android.os.Build.MODEL);
        appendLog("Brand : " + android.os.Build.BRAND);
        appendLog("Product : " + android.os.Build.PRODUCT);
        appendLog("Device : " + android.os.Build.DEVICE);
        appendLog("Codename : " + android.os.Build.VERSION.CODENAME);
        appendLog("Release : " + android.os.Build.VERSION.RELEASE);
    }

}

