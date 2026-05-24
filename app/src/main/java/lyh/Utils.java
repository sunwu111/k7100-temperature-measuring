/**
 * 陆陆大顺的安卓 JAVA 便利包
 * 本包提供最常用的类，工具，方法，极大提高编程效率，只适合安卓下使用
 * 可以扩充本工具类，但只允许使用安卓SDK和Java标准类，不准引入第三方库和类
 * 版权所有, 2019， Kingron@163.com
 */
package lyh;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.util.Log;
import android.widget.EditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import hikvision.zhanyun.com.hikvision.utils.SystemManager;

/**
 * 陆陆大顺的工具类
 */
public class Utils {
    public final static String OS_NAME = System.getProperty("os.name");
    public final static String FORMAT_DATETIME = "yyyy-MM-dd HH:mm:ss";
    public final static String FORMAT_DATETIME_MS = "yyyy-MM-dd HH:mm:ss.SSS";
    public final static String FORMAT_DATE = "yyyy-MM-dd";
    public final static String FORMAT_MONTH = "yyyy-MM";
    public final static String FORMAT_TIME = "HH:mm:ss";
    public final static String FORMAT_TIMESTEMP_FILENAME = "yyyyMMdd_HHmmss";
    public final static String DIR_DATA = Environment.getDataDirectory().getAbsolutePath();
    public static final long PERIOD_DAY = 1000 * 60 * 60 * 24;
    public static final long PERIOD_HOUR = 1000 * 60 * 60;
    public static final long PERIOD_MINUTE = 1000 * 60;
    public static final long PERIOD_SECOND = 1000;
    public static String TAG = "LYH.UTILS";

    private static BroadcastReceiver onDownloadComplete;

    public static class ExecResult {
        public int exitCode;
        public String output;
        public String error;
    }

    public interface IDownloadCallback {
        public void onFinished(long id, String url, String Filename);
    }

    /**
     * 字符串类，实现类似TStringList的功能，线程安全的！
     * 字符串不包含\r，\n等换行符之类，每个字符串一行
     * 可添加、删除、保存到文件、从文件读取、转成字符串，从字符串读取
     */
    public static class StringList extends ArrayList<String> {
        private Lock lock = new ReentrantLock();
        private String separator = "\n";
        public String fileName = "";

        public StringList() {
            super();
            if (isWindows()) {
                separator = "\r\n";
            } else if (isMacOS()) {
                separator = "\r";
            }
        }

        public StringList(String separator) {
            super();
            this.separator = separator;
        }

        /**
         * 转成类似数组定义的格式，例如类似 "[aaa, bbb, ccc]" 的字符串
         *
         * @return
         */
        public String toArrayString() {
            lock.lock();
            try {
                return super.toString();
            } finally {
                lock.unlock();
            }
        }

        /**
         * 插入字符串,index待插入的位置，在首行插入为 insert(0, "ssss");
         *
         * @param index
         * @param s
         * @return
         */
        public int insert(int index, String s) {
            lock.lock();
            try {
                add(index, s);
            } finally {
                lock.unlock();
            }
            return index;
        }

        /**
         * 转成分隔符分隔字符串
         *
         * @return 返回结果字符串
         */
        public String toString() {
            lock.lock();
            try {
                StringBuilder buf = new StringBuilder(4096);
                for (int i = 0; i < size(); i++)
                    buf.append(get(i) + separator);
                return buf.toString();
            } finally {
                lock.unlock();
            }
        }

        /**
         * 从字符串转成列表，字符串应该用分隔符分隔
         *
         * @param s
         */
        public void fromString(String s) {
            lock.lock();
            try {
                clear();
                String[] strings = s.split(separator);
                for (int i = 0; i <= strings.length; i++)
                    add(strings[i]);
            } finally {
                lock.unlock();
            }
        }

        public boolean saveToFile(String filename) {
            this.fileName = filename;
            FileOutputStream fos = null;
            lock.lock();
            try {
                fos = new FileOutputStream(filename);
                String s;
                for (int i = 0; i < size(); i++) {
                    s = get(i) + separator;
                    fos.write(s.getBytes());
                }
                fos.close();
                return true;
            } catch (Exception e) {
                return false;
            } finally {
                lock.unlock();
            }
        }

        /**
         * 保存到最近一次加载或写入的文件当中
         *
         * @return
         */
        public boolean save() {
            boolean result = !fileName.equals("");
            return result ? saveToFile(fileName) : false;
        }

        public void clear() {
            lock.lock();
            try {
                super.clear();
            } finally {
                lock.unlock();
            }
        }

        public boolean add(String s) {
            lock.lock();
            try {
                return super.add(s);
            } finally {
                lock.unlock();
            }
        }

        /**
         * 从文件读取列表
         *
         * @param filename
         * @return
         */
        public boolean loadFromFile(String filename) {
            this.fileName = filename;
            clear();
            lock.lock();
            try {
                FileInputStream fis = new FileInputStream(filename);
                Scanner scanner = new Scanner(fis);
                scanner.useDelimiter(separator);
                while (scanner.hasNextLine())
                    add(scanner.nextLine());
                fis.close();
                return true;
            } catch (Exception e) {
                return false;
            } finally {
                lock.unlock();
            }
        }

        /**
         * 在列表末尾添加字符串，与add相同
         *
         * @param s
         * @return
         */
        public int push(String s) {
            add(s);
            return size();
        }

        public String remove(int index) {
            lock.lock();
            try {
                return super.remove(index);
            } finally {
                lock.unlock();
            }
        }

        /**
         * 删除列表中的字符串，成功返回true，失败返回false;
         *
         * @param s
         * @return
         */
        public boolean remove(String s) {
            lock.lock();
            try {
                return super.remove(s);
            } finally {
                lock.unlock();
            }
        }

        /**
         * 弹出最后加入的字符串
         *
         * @return 返回最后的字符串，并从列表中删除
         */
        public String pop() {
            lock.lock();
            try {
                String s = super.get(size() - 1);
                super.remove(size() - 1);
                return s;
            } finally {
                lock.unlock();
            }
        }
    }


    public static class MyExceptionHandler implements Thread.UncaughtExceptionHandler {
        private Context context;
        private String url;
        private String logFile;

        public MyExceptionHandler(Context context, String url, String logFile) {
            super();
            this.url = url;
            this.context = context;
            this.logFile = logFile;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            Intent intent = new Intent(context, context.getClass());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("crash", true);
            PendingIntent restartIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_ONE_SHOT);

            //退出程序
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, restartIntent); // 5秒钟后重启应用
            System.gc();
            String s = ex.getMessage() + "\r\n" + stackTraceToString(ex);
            Log.e(TAG, s);
            if (logFile != null) {
                appendFile(logFile, currentDateTime() + ": 程序崩溃，调用堆栈: " + s + "\n");
            }
            if (url != null) {
                try {
                    final String fn = DIR_CACHE(context) + "/" + getAppName(context) + ".txt";
                    if (stringToFile(fn, currentDateTime() + "\r\n" + s)) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                httpPostFile(url, fn, 20);
                                deleteFile(fn);
                            }
                        }).start();
                    }
                } catch (Exception e) {
                    Log.i(TAG, e.getMessage());
                }
            }
            System.exit(-1);
        }
    }


    // 更为详细的错误打印
//    public static class MyExceptionHandler implements Thread.UncaughtExceptionHandler {
//
//        private Context context;
//        private String url;
//        private String logFile;
//
//        public MyExceptionHandler(Context context, String url, String logFile) {
//            this.url = url;
//            this.context = context.getApplicationContext();
//            this.logFile = logFile;
//        }
//
//        @Override
//        public void uncaughtException(Thread thread, Throwable ex) {
//
//            long crashTime = System.currentTimeMillis();
//
//            String stack = getFullStackTrace(ex);
//            String threadInfo = "Thread: " + thread.getName() +
//                    " (id=" + thread.getId() + ")\n";
//
//            String deviceInfo =
//                    "Model: " + android.os.Build.MODEL + "\n" +
//                            "Brand: " + android.os.Build.BRAND + "\n" +
//                            "Device: " + android.os.Build.DEVICE + "\n" +
//                            "Android: " + android.os.Build.VERSION.RELEASE + "\n" +
//                            "SDK: " + android.os.Build.VERSION.SDK_INT + "\n";
//
//            Runtime runtime = Runtime.getRuntime();
//            String memInfo =
//                    "MaxMemory: " + runtime.maxMemory() / 1024 / 1024 + "MB\n" +
//                            "TotalMemory: " + runtime.totalMemory() / 1024 / 1024 + "MB\n" +
//                            "FreeMemory: " + runtime.freeMemory() / 1024 / 1024 + "MB\n";
//
//            String logcat = getLogcat();
//
//            String log = currentDateTime() + "\n"
//                    + "===== CRASH START =====\n"
//                    + threadInfo
//                    + deviceInfo
//                    + memInfo
//                    + "\n----- Exception -----\n"
//                    + stack
//                    + "\n----- Logcat -----\n"
//                    + logcat
//                    + "===== CRASH END =====\n\n";
//
//            Log.e(TAG, log);
//
//            if (logFile != null) {
//                appendFile(logFile, log);
//            }
//
//            if (url != null) {
//                try {
//                    final String fn = DIR_CACHE(context) + "/" + getAppName(context) + ".txt";
//                    if (stringToFile(fn, log)) {
//                        new Thread(() -> {
//                            httpPostFile(url, fn, 20);
//                            deleteFile(fn);
//                        }).start();
//                    }
//                } catch (Exception e) {
//                    Log.e(TAG, "upload error", e);
//                }
//            }
//
//            try {
//                Intent intent = new Intent(context, context.getClass());
//                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                intent.putExtra("crash", true);
//
//                PendingIntent restartIntent = PendingIntent.getActivity(
//                        context,
//                        0,
//                        intent,
//                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
//                );
//
//                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
//                if (am != null) {
//                    am.setExact(AlarmManager.RTC_WAKEUP,
//                            crashTime + 5000,
//                            restartIntent);
//                }
//
//            } catch (Exception e) {
//                Log.e(TAG, "restart error", e);
//            }
//
//            try {
//                Thread.sleep(2000);
//            } catch (InterruptedException ignored) {}
//
//            android.os.Process.killProcess(android.os.Process.myPid());
//            System.exit(1);
//        }
//
//        /**
//         * 获取完整异常链（核心改进）
//         */
//        private String getFullStackTrace(Throwable ex) {
//            StringBuilder sb = new StringBuilder();
//
//            while (ex != null) {
//                sb.append("Exception: ").append(ex.toString()).append("\n");
//
//                for (StackTraceElement element : ex.getStackTrace()) {
//                    sb.append("    at ").append(element.toString()).append("\n");
//                }
//
//                ex = ex.getCause();
//                if (ex != null) {
//                    sb.append("Caused by:\n");
//                }
//            }
//
//            return sb.toString();
//        }
//
//        /**
//         * 获取最近 logcat（关键）
//         */
//        private String getLogcat() {
//            StringBuilder log = new StringBuilder();
//
//            try {
//                Process process = Runtime.getRuntime().exec("logcat -d -t 200");
//
//                BufferedReader reader = new BufferedReader(
//                        new InputStreamReader(process.getInputStream())
//                );
//
//                String line;
//                while ((line = reader.readLine()) != null) {
//                    log.append(line).append("\n");
//                }
//
//            } catch (Exception e) {
//                log.append("logcat error: ").append(e.getMessage());
//            }
//
//            return log.toString();
//        }
//    }


    /*
        返回程序的缓存目录
     */
    public final static String DIR_CACHE(Context context) {
        return context.getCacheDir().getAbsolutePath();
    }

    /**
     * 把类似 12:34:56 的字符串转成 Time 输出
     * 用于保存时间戳时分秒，可用于数据交换，如Date
     */
    public static Date dateFromString(String s) {
        String[] my = s.split(":");
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(my[0]));
        calendar.set(Calendar.MINUTE, Integer.parseInt(my[1]));
        calendar.set(Calendar.SECOND, Integer.parseInt(my[2]));
        return calendar.getTime();
    }

    /**
     * 压缩文件为.zip文件
     *
     * @param srcFile 待压缩的源文件
     * @param dstFile 压缩后的zip文件路径和文件名
     * @return 成功返回 true ，失败返回 false
     */
    public static boolean zipFile(String srcFile, String dstFile) {
        File file = new File(srcFile);
        if (!file.exists()) return false;

        try {
            file = new File(dstFile);
            ZipOutputStream zipOutputStream = new ZipOutputStream(new CheckedOutputStream(new FileOutputStream(file), new CRC32()));
            zipOutputStream.putNextEntry(new ZipEntry(extractFileName(srcFile))); // 写入文件名到输出的.zip中
            FileInputStream input = new FileInputStream(srcFile);
            byte[] buf = new byte[4096];
            int len = -1;

            while ((len = input.read(buf)) != -1) {
                zipOutputStream.write(buf, 0, len);
            }

            zipOutputStream.flush();
            input.close();
            zipOutputStream.flush();
            zipOutputStream.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 删除指定的文件
     *
     * @param name 待删除的文件名
     * @return 删除成功返回 true ，否则返回 false
     */
    public static boolean deleteFile(String name) {
        File file = new File(name);
        return file.delete();
    }

    /**
     * 在文件末尾添加内容
     *
     * @param fileName
     * @param content
     * @return
     */
    public static boolean appendFile(String fileName, String content) {
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
            FileWriter writer = new FileWriter(fileName, true);
            writer.write(content);
            writer.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 从文件读取全部内容并返回字符串
     *
     * @param fileName
     * @return
     */
    public static String stringFromFile(String fileName) {
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
            FileInputStream fis = new FileInputStream(fileName);
            byte[] buf = new byte[fis.available()];
            fis.read(buf);
            fis.close();
            return new String(buf);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 保存字符串到文件
     *
     * @param fileName
     * @param content
     * @return
     */
    public static boolean stringToFile(String fileName, String content) {
        try {
            // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
            FileWriter writer = new FileWriter(fileName, false);
            writer.write(content);
            writer.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean mkdirs(String fullPathAndName) {
        return new File(new File(fullPathAndName).getParent()).mkdirs();
    }

    /**
     * 从文件名全路径里面，提取文件名部分，例如：
     * /abc/def/xyz.txt ==> xyz.txt
     *
     * @param fullPath
     * @return a
     */
    public static String extractFileName(String fullPath) {
        return new File(fullPath).getName();
    }

    /**
     * 从字符串中左边开始获取len个字符
     *
     * @param s
     * @param len
     * @return
     */
    public static String left(String s, int len) {
        return s.substring(0, Math.min(len, s.length()));
    }

    /**
     * 从字符串中取分隔符左边的字符，例如 left("aaaa=1234", "=") == > "aaa"
     *
     * @param s
     * @param seperator 分隔字符串
     * @return 如果有分隔符返回分隔符左边的，否则返回原始字符串
     */
    public static String left(String s, String seperator) {
        int idx = s.indexOf(seperator);
        return s.substring(0, idx == -1 ? s.length() : idx);
    }

    /**
     * 从字符串右边开始，获取len个字符
     *
     * @param s
     * @param len
     * @return
     */
    public static String right(String s, int len) {
        return s.substring(Math.max(0, s.length() - len), s.length());
    }

    /**
     * 从字符串中取分隔符右边的字符，例如 right("aaaa=1234", "=") == > "1234"
     *
     * @param s
     * @param seperator 分隔字符串
     * @return 如果有分隔符返回分隔符右边的，否则返回原始字符串
     */
    public static String right(String s, String seperator) {
        int idx = s.indexOf(seperator);
        return s.substring(idx == -1 ? 0 : idx + seperator.length(), s.length());
    }

    /**
     * 从流中读取所有内容，并返回String
     *
     * @param in 待读取的流
     * @return 返回流中所有数据的内容字符串
     * @throws IOException
     */
    public static String streamToString(InputStream in) throws IOException {
        StringBuffer out = new StringBuffer();
        byte[] b = new byte[4096];
        for (int n; (n = in.read(b)) != -1; ) {
            out.append(new String(b, 0, n));
        }
        return out.toString();
    }

    /**
     * 以同步模式向服务器发送一个HTTP GET请求
     *
     * @param uri 待请求的URL地址
     * @return 返回服务器的信息
     */
    public static String httpGet(String uri) {
        HttpURLConnection urlConnection = null;
        String result = null;
        try {
            URL url = new URL(uri);
            urlConnection = (HttpURLConnection) url.openConnection();
            InputStream in = urlConnection.getInputStream();
            result = streamToString(in);
        } catch (Exception e) {
            result = e.getMessage();
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return result;
    }

    public static boolean downloadFile(String url, String filename) {
        try {
            URL u = new URL(url);
            InputStream is = u.openStream();
            DataInputStream dis = new DataInputStream(is);

            byte[] buffer = new byte[4096];
            int length;

            FileOutputStream fos = new FileOutputStream(new File(filename));
            while ((length = dis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            return true;
        } catch (Exception se) {
            return false;
        }
    }

    /**
     * 利用系统的文件下载管理器，下载文件，回调用于当完成的时候的处理，用法示例
     * <p>
     * Utils.IDownloadCallback controllerCallback = new Utils.IDownloadCallback() {
     *
     * @param context  Activity Context
     * @param uri      要下载的URL地址
     * @param Filename 保存的文件名，请确保有相关的权限访问目录，必须是外部存储目录，系统权限限制！
     * @param callback 文件下载完成时的回调，下载可能成功也可能失败！
     * @return
     * @Override public void onFinished(long id, String url, String Filename) {
     * Log.i(TAG, "下载完成: " + id + "," + url + "==>" + Filename);
     * }
     * };
     * String fn = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + "demo.txt"
     * Utils.downloadFile(this, "http://www.abc.com", fn, controllerCallback);
     */
    public static long downloadFile(final Context context, final String uri, final String Filename, final IDownloadCallback callback) {
        onDownloadComplete = new BroadcastReceiver() {
            public void onReceive(Context ctxt, Intent intent) {
                context.unregisterReceiver(onDownloadComplete);
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (callback != null) callback.onFinished(id, uri, Filename);
            }
        };
        context.registerReceiver(onDownloadComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(uri));
        request.setDestinationUri(Uri.fromFile(new File(Filename)));
//        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, Filename);
        return downloadManager.enqueue(request);
    }

    /**
     * 用异步的方式请求一个URL，返回服务器返回的信息
     * 代码立即返回，不处理返回结果的
     *
     * @param uri 待请求的URL地址
     */
    public static void httpGetAsync(final String uri) {
        //开启线程，发送请求
        new Thread(new Runnable() {
            @Override
            public void run() {
                httpGet(uri);
            }
        }).start();
    }

    /**
     * 上传文件到指定的URL地址，返回服务器返回的结果，同步模式
     * 不能在主线程中运行，如果有需要，请使用下面的方式调用
     * 警告：本方法，只能适合小文件，不适合10M以上大文件！
     * new Thread(new Runnable() {
     *
     * @param uri      服务器URL地址，完整路径
     * @param fileName 本地文件名
     * @return 返回服务器响应字符串
     * @Override public void run() {
     * // Do something....
     * }
     * }).start();
     */

    public static String httpPostFile(String uri, String fileName, final int secondTimeout) {
        final String BOUNDARY = "*****";
        final String TWO_HYPHENS = "--";
        final String LINE_END = "\r\n";
        final String HEAD_END = "\r\n\r\n";

        class InterruptThread implements Runnable {
            Thread parent;
            HttpURLConnection con;

            public InterruptThread(Thread parent, HttpURLConnection con) {
                this.parent = parent;
                this.con = con;
            }

            public void run() {
                try {
                    Thread.sleep(1000 * secondTimeout * 2);
                    // 无论如何，在超时后，强行断开链接，防止吊死
                    con.disconnect();
                } catch (Exception e) {
                    // Nothing
                }
            }
        }

        String sName = extractFileName(fileName);
        try {
            URL url = new URL(uri);
            FileInputStream fis = new FileInputStream(fileName);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            new Thread(new InterruptThread(Thread.currentThread(), connection)).start();

            connection.setConnectTimeout(10 * 1000);
            connection.setReadTimeout(1000 * secondTimeout);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Connection", "Close");
            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + BOUNDARY);
            connection.setRequestProperty("File", sName);

            DataOutputStream request = new DataOutputStream(connection.getOutputStream());
            request.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);
            request.writeBytes("Content-Disposition: form-data; name=\"File\"; filename=\"" + sName + "\"" + HEAD_END);

            byte[] buf = new byte[4096];
            int len = -1;
            while ((len = fis.read(buf)) != -1) {
                request.write(buf, 0, len);
                request.flush();
            }
            request.writeBytes(LINE_END + TWO_HYPHENS + BOUNDARY + TWO_HYPHENS + LINE_END);
            request.flush();
            request.close();

            int status = connection.getResponseCode();
            if (status == connection.HTTP_OK) {
                StringBuffer out = new StringBuffer();
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line);
                }
                reader.close();
                connection.disconnect();
                return out.toString();
            } else
                return connection.getResponseMessage();
        } catch (SocketTimeoutException e) {
            Log.d(TAG, "Socket超时: " + secondTimeout);
            return "Socket Timeout";
        } catch (Exception e) {
            Log.d(TAG, "HTTP POST异常: " + e.getCause());
            return e.getMessage();
        }
    }

    /**
     * 从程序的Assets目录下读取对应资源文件
     * webView.loadUrl("file:///android_asset/dir/file.ext");
     *
     * @param context 程序的context，Asset是和程序相关的
     * @param file    文件名，可以包含相对路径
     * @return 返回文件内容
     */
    public static String readAsset(Context context, String file) {
        try {
            InputStream is = context.getAssets().open(file);
            return streamToString(is);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 按给定的启动时间（hh:mm:ss）设置定时任务，如果时间已过，则自动到下一个周期开始
     *
     * @param time       定时时刻，按hh:mm:ss 例如 12:20:35 表示12点20分35秒开始运行，以后每隔指定周期运行
     * @param task       定时的任务
     * @param periodMSec 周期间隔，单位毫秒
     * @return
     */
    public static Timer scheduleTask(String time, TimerTask task, long periodMSec) {
        Timer timer = new Timer();
        Date date = dateFromString(time);
        long begin = date.getTime();
        long now = new Date().getTime();

        if (now > begin) // 如果当前时间 > 定时开始的时刻，需要调整到下一次开始的时刻开始！
        {
            long diff = now - begin;
            date = addTime(date, periodMSec * roundUp(1.0 * diff / periodMSec));
        }
        timer.scheduleAtFixedRate(task, date, periodMSec);
        return timer;
    }

    /**
     * 从当前时刻开始安排一个定时器，在指定时刻后运行任务
     *
     * @param task  定时的任务
     * @param delay 从当前时刻开始，多久后运行
     * @return
     */
    public static Timer scheduleTask(TimerTask task, long delay) {
        Timer timer = new Timer();
        timer.schedule(task, delay);
        return timer;
    }

    /**
     * 返回两个time2 - time1 时间的毫秒差
     *
     * @param time1
     * @param time2
     * @return
     */
    public static long subTime(Date time1, Date time2) {
        return time2.getTime() - time1.getTime();
    }

    /**
     * 日期相加指定的毫秒数
     *
     * @param time     基准时间
     * @param mSeconds 待相加的毫秒数，可以输入负数，负数表示基准时间之前的时间
     * @return
     */
    public static Date addTime(Date time, long mSeconds) {
        return new Date(time.getTime() + mSeconds);
    }

    /**
     * 返回给定的时间是否在范围内
     *
     * @param time
     * @param begin
     * @param end
     * @return
     */
    public static boolean between(Date time, Date begin, Date end) {
        return (time.after(begin)) && (time.before(end));
    }

    /**
     * 返回给定的时间是否在范围内，例如 between("08:00:00", "07:00:00", "18:00:00")
     *
     * @param time
     * @param begin
     * @param end
     * @return
     */
    public static boolean between(String time, String begin, String end) throws ParseException {
        String format = "HH:mm:ss";
        Date t = new SimpleDateFormat(format).parse(time);
        Date b = new SimpleDateFormat(format).parse(begin);
        Date e = new SimpleDateFormat(format).parse(end);
        return between(t, b, e);
    }


    /**
     * 对浮点数 x 向上取整返回整数
     *
     * @param x
     * @return
     */
    public static long roundUp(double x) {
        return (long) Math.ceil(x);
    }

    /**
     * 以Root权限运行指令，成功返回true，失败返回false，同步模式，等待命令运行完成
     *
     * @param command 待运行的命令
     * @return 返回命令退出码，-1返回失败
     */
    public static int su(String command) {
        Process process = null;
        DataOutputStream os = null;
        boolean result = false;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit $?\n");
            os.flush();
            process.waitFor();
            return process.exitValue();
        } catch (Exception e) {
            return -1;
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (Exception e) {
            }
        }
    }

    /*
        执行命令并等待命令结束，返回命令的退出代码，
        如果执行错误，返回 -1
     */
    public static int exec(final String cmd) {
        try {
            Runtime runtime = Runtime.getRuntime();
            Process proc = runtime.exec(cmd);
            proc.waitFor();
            return proc.exitValue();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 执行命令并返回结果输出字符串，不适合有交互输入的命令，只适合无需人工干预的命令！
     * 警告：exec只能返回命令或错误输出的前4585个字节的内容，如果输出多余此数，会被截断！
     *
     * @param commands
     * @return 异常返回null，否则返回命令结果
     */
    public static ExecResult exec(String[] commands) {
        ExecResult result = new ExecResult();
        Process process = null;
        InputStream is, err;
        try {
            process = Runtime.getRuntime().exec(commands);
            is = process.getInputStream();
            err = process.getErrorStream();
            process.waitFor();
            result.output = streamToString(is);
            result.exitCode = process.exitValue();
            result.error = streamToString(err);
            is.close();
            err.close();
            return result;
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null) process.destroy();
        }
    }

    /**
     * 下载指定的文件并返回内容
     *
     * @param url
     * @return
     */
    public static String wget(String url) {
        //TODO: 未完成
        return "";
    }

    /**
     * 调用系统的wget（busybox）下载指定URL的内容到文件当中
     *
     * @param url  下载的URL地址
     * @param file 保存的文件名
     * @return 成功返回　true，失败返回false
     */
    public static boolean wgetFile(String url, String file) {
        String[] cmd;
        if (new File("/system/bin/wget").exists()) {
            cmd = new String[]{"wget", "-O", file, url};
        } else {
            cmd = new String[]{"busybox", "wget", "-O", file, url};
        }
        try {
            Runtime.getRuntime().exec(cmd);
            return new File(file).exists();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 从文件读取全部内容并返回字符串，默认UTF-8编码
     *
     * @param file    待读取的文件名
     * @param charset 字符编码，默认UTF-8
     * @return
     * @throws IOException
     */
    public static String readFile(File file, String charset) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] buffer = new byte[fileInputStream.available()];
        int length = fileInputStream.read(buffer);
        fileInputStream.close();
        return new String(buffer, 0, length, charset == null ? "UTF-8" : charset);
    }

    public static boolean isLinux() {
        return OS_NAME.indexOf("linux") >= 0;
    }

    public static boolean isWindows() {
        return OS_NAME.indexOf("windows") >= 0;
    }

    public static boolean isMacOS() {
        return OS_NAME.indexOf("mac os") >= 0;
    }

    /**
     * 返回程序的版本信息，可以在前后添加额外的字符串
     *
     * @param context
     * @return
     */
    public static String appVersion(Context context) {
        String result = "";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            result = String.format("v%s(%d, %s)", info.versionName, info.versionCode, formatDateTime(info.lastUpdateTime));
        } catch (Exception e) {
            // Nothing...
        }
        return result;
    }

    public static String formatDateTime(Date date) {
        return new SimpleDateFormat(FORMAT_DATETIME).format(date);
    }

    public static String formatDateTime(String format, Date date) {
        return new SimpleDateFormat(format).format(date);
    }

    public static String formatDateTime(long timestamp) {
        return new SimpleDateFormat(FORMAT_DATETIME).format(new Date(timestamp));
    }

    public static String formatDateTime(String format, long timestamp) {
        return new SimpleDateFormat(format).format(new Date(timestamp));
    }

    public static String currentTime() {
        return new SimpleDateFormat(FORMAT_TIME).format(new Date());
    }

    public static String currentMonth() {
        return new SimpleDateFormat(FORMAT_MONTH).format(new Date());
    }

    public static String currentTimeMS() {
        return new SimpleDateFormat(FORMAT_DATETIME_MS).format(new Date());
    }

    public static String currentDate() {
        return new SimpleDateFormat(FORMAT_DATE).format(new Date());
    }

    public static String currentDateTime() {
        return new SimpleDateFormat(FORMAT_DATETIME).format(new Date());
    }

    public static String currentTimestampFilename() {
        return new SimpleDateFormat(FORMAT_TIMESTEMP_FILENAME).format(new Date());
    }

    // 字符串转日期数据
    public static Date stringToDateTime(String format, String value) {
        try {
            return new SimpleDateFormat(format).parse(value);
        } catch (ParseException e) {
            return null;
        }
    }

    public static long stringToTimestamp(String format, String value) {
        try {
            Date date = new SimpleDateFormat(format).parse(value);
            return date.getTime();
        } catch (ParseException e) {
            return 0;
        }
    }

    /**
     * 方法描述：判断某一应用是否正在运行
     * Created by cafeting on 2017/2/4.
     *
     * @param context     上下文
     * @param packageName 应用的包名
     * @return true 表示正在运行，false 表示没有运行
     */
    public static boolean isAppActivity(Context context, String packageName) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> list = am.getRunningTasks(100);
        if (list.size() <= 0) {
            return false;
        }
        for (ActivityManager.RunningTaskInfo info : list) {
            if (info.baseActivity.getPackageName().equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    public static String getAppName(Context context) {
        return context.getPackageName();
    }

    /**
     * 获取已安装应用的 uid，-1 表示未安装此应用或程序异常
     *
     * @param context
     * @param packageName
     * @return
     */
    public static int getPackageUid(Context context, String packageName) {
        try {
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(packageName, 0);
            if (applicationInfo != null) {
                return applicationInfo.uid;
            }
        } catch (Exception e) {
            return -1;
        }
        return -1;
    }

    /**
     * 判断某一 uid 的程序是否有正在运行的进程，即是否存活
     * Created by cafeting on 2017/2/4.
     *
     * @param context 上下文
     * @param uid     已安装应用的 uid
     * @return true 表示正在运行，false 表示没有运行
     */
    public static boolean isProcessRunning(Context context, int uid) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> runningServiceInfos = am.getRunningServices(200);
        if (runningServiceInfos.size() > 0) {
            for (ActivityManager.RunningServiceInfo appProcess : runningServiceInfos) {
                if (uid == appProcess.uid) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断给定的服务是否在运行
     *
     * @param context
     * @param className
     * @return
     */
    public static boolean isServiceRunning(Context context, String className) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> serviceList = am.getRunningServices(50);
        for (int i = 0; i < serviceList.size(); i++) {
            if (serviceList.get(i).service.getClassName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断异常给定的包名是否在运行，包含两个情况：有界面和无界面（服务）都可以支持
     *
     * @param context
     * @param packageName
     * @return
     */
    public static boolean isAppRunning(Context context, String packageName) {
        int uid = getPackageUid(context, packageName);
        return uid > 0 ? isAppActivity(context, packageName) || isProcessRunning(context, uid) : false;
    }

    /**
     * 十六进制String转byte[]，Hex字符串必须前导0格式，即不足的必须补0对齐
     *
     * @param s
     * @return
     */
    public static byte[] hex2bin(String s) {
        if (s == null) return null;
        String str = s.replace(" ", "");
        if (str.length() == 0) return new byte[0];
        byte[] byteArray = new byte[str.length() / 2];
        for (int i = 0; i < byteArray.length; i++) {
            String subStr = str.substring(2 * i, 2 * i + 2);
            byteArray[i] = ((byte) Integer.parseInt(subStr, 16));
        }
        return byteArray;
    }

    /**
     * byte[]转十六进制String
     *
     * @param byteArray
     * @return
     */
    private static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bin2hex(byte[] byteArray, boolean space) {
        if (byteArray == null) return null;
        int size = space ? 3 : 2;
        char[] hexChars = new char[byteArray.length * size];
        for (int j = 0; j < byteArray.length; j++) {
            int v = byteArray[j] & 0xFF;
            hexChars[j * size] = hexArray[v >>> 4];
            hexChars[j * size + 1] = hexArray[v & 0x0F];
            if (space) hexChars[j * size + 2] = ' ';
        }
        return new String(hexChars);
    }

    public static String bin2hex(byte[] byteArray, int len, boolean space) {
        if (byteArray == null) return null;
        int size = space ? 3 : 2;
        char[] hexChars = new char[len * size];
        for (int j = 0; j < len; j++) {
            int v = byteArray[j] & 0xFF;
            hexChars[j * size] = hexArray[v >>> 4];
            hexChars[j * size + 1] = hexArray[v & 0x0F];
            if (space) hexChars[j * size + 2] = ' ';
        }
        return new String(hexChars);
    }

    /**
     * 执行APK的静默安装
     * Manifest中，需要android:sharedUserId="android.uid.system"
     * 并申请android:name="android.permission.INSTALL_PACKAGES"权限
     * 或者有root权限，可以执行su命令
     *
     * @param apkPath 要安装的apk文件的路径
     * @return 安装成功返回true，安装失败返回false。
     */
    public static boolean apkInstall(String apkPath) {
/*
        //android9.0之后不再允许通过这种方式升级
        String[] commands = new String[]{"/system/bin/pm", "install", "-r", "-d", "-t", apkPath};
        ExecResult execResult = exec(commands);
        boolean result = exec(commands).exitCode == 0;
*/
        // 升级之后不启动新的apk程序 --dont-kill
        boolean result = su("/system/bin/pm install -r -d -t --dont-kill " + apkPath) == 0;
        return result;
    }

    /**
     * 卸载指定包名的APK，包名类似 com.abc.xyz
     *
     * @param packageName
     * @return
     */
    public static boolean apkUnInstall(String packageName) {
        String[] commands = new String[]{"/system/bin/pm", "uninstall", "-k", packageName};
        boolean result = exec(commands).exitCode == 0;
        if (!result) result = su("/system/bin/pm install -k " + packageName) == 0;
        return result;
    }

    /**
     * 采用累加和取反的校验方式计算CRC16，MODBus串口通信一般用这个CRC算法
     * 所有字节进行算术累加，抛弃高位，只保留最后单字节，将单字节取反；
     *
     * @param data 需要计算的数据
     * @return 结果
     */
    public static byte Crc(byte[] data) {
        int r = 0;
        for (int i = 0; i < data.length; i++) r += data[i];
        byte b = (byte) (r & 0x00FF);
        return (byte) ~b;
    }

    private static int[] table = {
            0x0000, 0xC0C1, 0xC181, 0x0140, 0xC301, 0x03C0, 0x0280, 0xC241,
            0xC601, 0x06C0, 0x0780, 0xC741, 0x0500, 0xC5C1, 0xC481, 0x0440,
            0xCC01, 0x0CC0, 0x0D80, 0xCD41, 0x0F00, 0xCFC1, 0xCE81, 0x0E40,
            0x0A00, 0xCAC1, 0xCB81, 0x0B40, 0xC901, 0x09C0, 0x0880, 0xC841,
            0xD801, 0x18C0, 0x1980, 0xD941, 0x1B00, 0xDBC1, 0xDA81, 0x1A40,
            0x1E00, 0xDEC1, 0xDF81, 0x1F40, 0xDD01, 0x1DC0, 0x1C80, 0xDC41,
            0x1400, 0xD4C1, 0xD581, 0x1540, 0xD701, 0x17C0, 0x1680, 0xD641,
            0xD201, 0x12C0, 0x1380, 0xD341, 0x1100, 0xD1C1, 0xD081, 0x1040,
            0xF001, 0x30C0, 0x3180, 0xF141, 0x3300, 0xF3C1, 0xF281, 0x3240,
            0x3600, 0xF6C1, 0xF781, 0x3740, 0xF501, 0x35C0, 0x3480, 0xF441,
            0x3C00, 0xFCC1, 0xFD81, 0x3D40, 0xFF01, 0x3FC0, 0x3E80, 0xFE41,
            0xFA01, 0x3AC0, 0x3B80, 0xFB41, 0x3900, 0xF9C1, 0xF881, 0x3840,
            0x2800, 0xE8C1, 0xE981, 0x2940, 0xEB01, 0x2BC0, 0x2A80, 0xEA41,
            0xEE01, 0x2EC0, 0x2F80, 0xEF41, 0x2D00, 0xEDC1, 0xEC81, 0x2C40,
            0xE401, 0x24C0, 0x2580, 0xE541, 0x2700, 0xE7C1, 0xE681, 0x2640,
            0x2200, 0xE2C1, 0xE381, 0x2340, 0xE101, 0x21C0, 0x2080, 0xE041,
            0xA001, 0x60C0, 0x6180, 0xA141, 0x6300, 0xA3C1, 0xA281, 0x6240,
            0x6600, 0xA6C1, 0xA781, 0x6740, 0xA501, 0x65C0, 0x6480, 0xA441,
            0x6C00, 0xACC1, 0xAD81, 0x6D40, 0xAF01, 0x6FC0, 0x6E80, 0xAE41,
            0xAA01, 0x6AC0, 0x6B80, 0xAB41, 0x6900, 0xA9C1, 0xA881, 0x6840,
            0x7800, 0xB8C1, 0xB981, 0x7940, 0xBB01, 0x7BC0, 0x7A80, 0xBA41,
            0xBE01, 0x7EC0, 0x7F80, 0xBF41, 0x7D00, 0xBDC1, 0xBC81, 0x7C40,
            0xB401, 0x74C0, 0x7580, 0xB541, 0x7700, 0xB7C1, 0xB681, 0x7640,
            0x7200, 0xB2C1, 0xB381, 0x7340, 0xB101, 0x71C0, 0x7080, 0xB041,
            0x5000, 0x90C1, 0x9181, 0x5140, 0x9301, 0x53C0, 0x5280, 0x9241,
            0x9601, 0x56C0, 0x5780, 0x9741, 0x5500, 0x95C1, 0x9481, 0x5440,
            0x9C01, 0x5CC0, 0x5D80, 0x9D41, 0x5F00, 0x9FC1, 0x9E81, 0x5E40,
            0x5A00, 0x9AC1, 0x9B81, 0x5B40, 0x9901, 0x59C0, 0x5880, 0x9841,
            0x8801, 0x48C0, 0x4980, 0x8941, 0x4B00, 0x8BC1, 0x8A81, 0x4A40,
            0x4E00, 0x8EC1, 0x8F81, 0x4F40, 0x8D01, 0x4DC0, 0x4C80, 0x8C41,
            0x4400, 0x84C1, 0x8581, 0x4540, 0x8701, 0x47C0, 0x4680, 0x8641,
            0x8201, 0x42C0, 0x4380, 0x8341, 0x4100, 0x81C1, 0x8081, 0x4040,
    };

    /**
     * Modbus CRC16 算法
     *
     * @param bytes 待计算数据缓冲区
     * @param start 开始计算位置
     * @param len 需要计算的数据长度
     * @return
     */
    public static int Crc16(byte[] bytes, int start, int len) {
        int crc = 0xFFFF;

        for (int i = start; i < start + len; i++) {
            crc = (crc >> 8) ^ table[(crc ^ (bytes[i] & 0xFF)) & 0xff];
        }

        return crc;
    }

    /**
     * ModBus CRC16 算法
     *
     * @param bytes 待计算的数据
     * @return 返回CRC16值
     */
    public static int Crc16(byte[] bytes) {
        int crc = 0xFFFF;

        for (byte b : bytes) {
            crc = (crc >> 8) ^ table[(crc ^ (b & 0xFF)) & 0xff];
        }

        return crc;
    }

    /**
     * 异常后自动重启APP，如果给定了URL，会自动把异常信息上传给定的URL
     * URL必须是一个支持HTTP POST的REST API，可以用CURL上传文件的地址即可
     * 如果 URL为空，则不上传
     *
     * @param context
     * @param url
     */
    public static void reloadAfterCrash(Context context, String url, String logFile) {
        MyExceptionHandler catchException = new MyExceptionHandler(context, url, logFile);
        Thread.setDefaultUncaughtExceptionHandler(catchException);
    }

    /**
     * 异常的调用堆栈输出为字符串
     *
     * @param e
     * @return
     */
    public static String stackTraceToString(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String dumpThreadStack(Thread thread) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : thread.getStackTrace()) {
            sb.append(element.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 重启应用程序
     *
     * @param context       窗体Context
     * @param delaySeconds 延迟多少秒启动程序
     */
    public static void restartApplication(Context context, long delaySeconds) {
        Intent intent = new Intent(context, context.getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent restartIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        //退出程序
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delaySeconds * 1000, restartIntent); // x秒钟后重启应用
        System.gc();
        System.exit(0);
    }

    public static boolean updateApp(String apkURL, String filename) {
        if (!downloadFile(apkURL, filename)) return false;
        return apkInstall(filename);
    }


    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "i" : "");
        return String.format("%.2f%sB", bytes / Math.pow(unit, exp), pre);
    }

    /**
     * 判断MainActivity是否活动
     *
     * @param context      一个context
     * @param activityName 要判断Activity
     * @return boolean
     */
    public static boolean isMainActivityAlive(Context context, String activityName) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> list = am.getRunningTasks(100);
        for (ActivityManager.RunningTaskInfo info : list) {
            // 注意这里的 topActivity 包含 packageName和className，可以打印出来看看
//            Log.i(TAG, "包 : " + info.topActivity.getPackageName() + "，窗体: " + info.baseActivity.getClassName());
            if (info.topActivity.getClassName().equals(activityName) || info.baseActivity.getClassName().equals(activityName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检测某Activity是否在当前Task的栈顶
     */
    public static boolean isTopActivity(Context context, String activityName) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runningTaskInfos = manager.getRunningTasks(1);
        String cmpNameTemp = null;
        if (runningTaskInfos != null) {
            cmpNameTemp = runningTaskInfos.get(0).topActivity.toString();
        }
        if (cmpNameTemp == null) {
            return false;
        }
        return cmpNameTemp.equals(activityName);
    }

    public static Process launchLogcat(String filename, String tag) {
        Process process = null;
        String cmd = String.format("logcat -v time -f %s %s:* \n", filename, tag);
        try {
            process = Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
            process = null;
        }
        return process;
    }

    /**
     * 返回在 [low, high) 之间的随机数，返回结果最小包括low，最大不包括high
     *
     * @param low
     * @param high
     * @return
     */
    public static int random(int low, int high) {
        SecureRandom r = new SecureRandom();
        return r.nextInt(high - low) + low;
    }

    public static float randomFloat(float low, float high) {
        SecureRandom r = new SecureRandom();
        return r.nextFloat() * (high - low) + low;
    }

    public static byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // Rotate the Y luma
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth)
                        + (x - 1)];
                i--;
            }
        }
        return yuv;
    }

    private static byte[] rotateYUV420Degree180(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        int i = 0;
        int count = 0;
        for (i = imageWidth * imageHeight - 1; i >= 0; i--) {
            yuv[count] = data[i];
            count++;
        }
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (i = imageWidth * imageHeight * 3 / 2 - 1; i >= imageWidth
                * imageHeight; i -= 2) {
            yuv[count++] = data[i - 1];
            yuv[count++] = data[i];
        }
        return yuv;
    }

    public static byte[] rotateYUV420Degree270(byte[] data, int imageWidth,
                                               int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        int nWidth = 0, nHeight = 0;
        int wh = 0;
        int uvHeight = 0;
        if (imageWidth != nWidth || imageHeight != nHeight) {
            nWidth = imageWidth;
            nHeight = imageHeight;
            wh = imageWidth * imageHeight;
            uvHeight = imageHeight >> 1;// uvHeight = height / 2
        }
        // ??Y
        int k = 0;
        for (int i = 0; i < imageWidth; i++) {
            int nPos = 0;
            for (int j = 0; j < imageHeight; j++) {
                yuv[k] = data[nPos + i];
                k++;
                nPos += imageWidth;
            }
        }
        for (int i = 0; i < imageWidth; i += 2) {
            int nPos = wh;
            for (int j = 0; j < uvHeight; j++) {
                yuv[k] = data[nPos + i];
                yuv[k + 1] = data[nPos + i + 1];
                k += 2;
                nPos += imageWidth;
            }
        }
        return rotateYUV420Degree180(yuv, imageWidth, imageHeight);
    }

    /*
    Yuv 12 转换为 NV21，Mediacodec输出的byte[]数据为Yuv12格式
     */
    public static void YuvToNV21(final byte[] input, final byte[] output, final int width, final int height) {
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;
        final int tempFrameSize = frameSize * 5 / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[tempFrameSize + i]; // Cr (V)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cb (U)
        }
    }

    public static int toInt(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException nfe) {
            return defaultValue;
        }
    }

    /**
     * 通过反射调用获取内置存储和外置sd卡根路径(通用)
     *
     * @param mContext    上下文
     * @param is_removale 是否可移除，false返回内部存储路径，true返回外置SD卡路径
     * @return
     */
    public static String getRemovableStoragePath(Context mContext, boolean is_removale) {
        String path;
        //使用getSystemService(String)检索一个StorageManager用于访问系统存储功能。
        StorageManager mStorageManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
        Class<?> storageVolumeClazz = null;
        try {
            storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isRemovable = storageVolumeClazz.getMethod("isRemovable");
            Method getState = mStorageManager.getClass().getMethod("getVolumeState", String.class);
            Object result = getVolumeList.invoke(mStorageManager);

            for (int i = 0; i < Array.getLength(result); i++) {
                Object storageVolumeElement = Array.get(result, i);
                path = (String) getPath.invoke(storageVolumeElement);
                String state = (String) getState.invoke(mStorageManager, path);
                boolean removable = (Boolean) isRemovable.invoke(storageVolumeElement);
                if (is_removale == removable && state.equals(Environment.MEDIA_MOUNTED)) {
                    return path;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static boolean saveObjectToFile(Object object, String filename) {
        try {
            FileOutputStream fileOut = new FileOutputStream(filename);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            out.writeObject(object);
            out.close();
            fileOut.close();
            return true;
        } catch (IOException i) {
            return false;
        }
    }

    public static Object loadObjectFromFile(String filename) {
        if (!new File(filename).exists()) return null;

        try {
            FileInputStream fileIn = new FileInputStream(filename);
            ObjectInputStream in = new ObjectInputStream(fileIn);
            Object object = in.readObject();
            in.close();
            fileIn.close();
            return object;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 返回当前月开始时间的毫秒数
     *
     * @return
     */
    public static long getThisMonthBeginMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMinimum(Calendar.DAY_OF_MONTH));
        return calendar.getTimeInMillis();
    }

    public static void saveBitmapAsJPEG(Bitmap bitmap, String file, int quality) {
        File pictureFile = new File(file);
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "保存位图错误: " + e.getMessage());
        }
    }

    /*
     ping 服务器指定次数，只要ping通，就立刻返回，否则重试 count 次
     可以用于检测服务器是否就绪，服务器连通返回 true ，否则返回 false
     */
    public static boolean ping(String ip, int count) {
        //String cmd = "ping -c 1 -W 1 " + ip;
        try {
            for (int i = 0; i < count; i++) {
                //if (su(cmd) == 0) return true;
                InetAddress address = InetAddress.getByName(ip);
                if (address.isReachable(1000)) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Ping球机错误: " + e.getMessage());
        }
        return false;
    }

    public static byte hi(short v) {
        return (byte) (v >> 8);
    }

    public static byte lo(short v) {
        return (byte) (v & 0xFF);
    }

    public static short hi(int v) {
        return (short) (v >> 16);
    }

    public static short lo(int v) {
        return (short) (v & 0xFFFF);
    }

    /////
    public static byte hi2byte(int v) {
        return (byte) ((v >> 8) & 0xFF);
    }

    public static byte lo2byte(int v) {
        return (byte) (v & 0xFF);
    }
    /////

    /**
     * 通过高低字节，返回short数据
     *
     * @param h
     * @param l
     * @return
     */
    public static short hw(byte h, byte l) {
        return (short) (((h & 0xff) << 8) | (l & 0xff));
    }

    /**
     * 通过四个字节，返回整数：字节排序 1 2 3 4，1 为高字节
     *
     * @param h1
     * @param h2
     * @param l3
     * @param l4
     * @return
     */
    public static int hw(byte h1, byte h2, byte l3, byte l4) {
        return (h1 << 24) | (h2 << 16) | (l3 << 8) | l4;
    }

    /**
     * java 合并两个byte数组
     *
     * @param byte_1
     * @param byte_2
     * @return
     */
    public static byte[] byteMerger(byte[] byte_1, byte[] byte_2) {
        byte[] byte_3 = new byte[byte_1.length + byte_2.length];
        System.arraycopy(byte_1, 0, byte_3, 0, byte_1.length);
        System.arraycopy(byte_2, 0, byte_3, byte_1.length, byte_2.length);
        return byte_3;
    }

    /**
     * 转换Int数组为网络字节序直接数组，方便传输
     *
     * @param values
     * @return
     */
    public static byte[] intArray2bytes(int[] values) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(values.length * 4);
        DataOutputStream dos = new DataOutputStream(bos);
        try {
            for (int v : values)
                dos.writeInt(v);
            dos.close();
            bos.close();
        } catch (IOException e) {
        }
        return bos.toByteArray();
    }

    /**
     * 解析Json数据.
     *
     * @param jsonObect 需要处理的完整的JSONObject对象
     * @param key       更换数据key，可包含路径，如果有路径，用/分隔，例如 abc/def/name
     * @param value     新的值
     */
    public static void jsonSet(Object jsonObect, String key, Object value) {
        // TODO: 路径处理暂时没有完成，代码会修改所有节点的同名数据，小心！！！！！
        try {
            if (key.equals("")) return;

            if (jsonObect instanceof JSONArray) {
                JSONArray jsonArray = (JSONArray) jsonObect;
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    jsonSet(jsonObject, key, value);
                }
            } else if (jsonObect instanceof JSONObject) {
                JSONObject jsonObject = (JSONObject) jsonObect;
                Iterator iterator = jsonObject.keys();
                while (iterator.hasNext()) {
                    String jsonKey = iterator.next().toString();
                    Object ob = jsonObject.get(jsonKey);
                    if (ob != null) {
                        if (ob instanceof JSONArray) {
                            jsonSet(ob, key, value);
                        } else if (ob instanceof JSONObject) {
                            jsonSet(ob, key, value);
                        } else {
                            if (jsonKey.equals(key)) {
                                jsonObject.put(key, value);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 显示一个模态输入对话框
     *
     * @param context
     * @param title
     * @param prompt
     * @param defaultText
     * @return
     */
    public static void inputBox(final Activity context, final String title, final String prompt, final String defaultText, final Runnable runnable) {
        final EditText input = new EditText(context);
        AlertDialog dlg = new AlertDialog.Builder(context).setTitle(title)
                .setView(input).setMessage(prompt).setCancelable(false)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                        runnable.run();
                    }
                }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                }).create();
        dlg.show();
    }

    /*
    对位图进行缩放操作，返回处理后的位图
     */
    public static Bitmap imageScale(Bitmap bitmap, int dst_w, int dst_h) {
        int src_w = bitmap.getWidth();
        int src_h = bitmap.getHeight();
        float scale_w = ((float) dst_w) / src_w;
        float scale_h = ((float) dst_h) / src_h;
        Matrix matrix = new Matrix();
        matrix.postScale(scale_w, scale_h);
        Bitmap dstbmp = Bitmap.createBitmap(bitmap, 0, 0, src_w, src_h, matrix, true);
        return dstbmp;
    }

    /**
     * 检查数据的第N位是否是1，是返回true，否则返回false
     * 数据位 0~31
     *
     * @param value
     * @param bit   0~31
     * @return
     */
    public static boolean bitOn(int value, int bit) {
        return ((value >> bit) & 0x00000001) == 1;
    }

    /**
     * 设置bit为为0
     * @param value 待计算的值
     * @param bit 第几比特位
     * @return
     */
    public static long bitSet0(long value, int bit) {
        return value &= ~ (1 << bit);
    }

    /**
     * 设置比特位为1
     * @param value 待处理的数据
     * @param bit 第几比特位
     * @return
     */
    public static long bitSet1(long value, int bit) {
        return value |= (1 << bit);
    }

    /**
     * 比特位取反
     * @param value
     * @param bit
     * @return
     */
    public static int bitSetXor(int value, int bit) {
        return value ^= (1 << bit);
    }

    /**
     * 从一个byte[]数组中截取一部分
     *
     * @param src
     * @param begin
     * @param count
     * @return
     */
    public static byte[] subBytes(byte[] src, int begin, int count) {
        byte[] bs = new byte[count];
        for (int i = begin; i < begin + count; i++)
            bs[i - begin] = src[i];
        return bs;
    }

    /**
     * 列出目录及其子目录下所有文件，递归获取
     *
     * @param dir
     * @return
     */
    public static void listFiles(String dir, List<File> list) {
        File file = new File(dir);
        for (File f : file.listFiles()) {
            if (f.isDirectory())
                listFiles(f.getPath(), list);
            else
                list.add(f);
        }
    }

    /**
     * 列出目录及其子目录下所有文件，递归获取
     *
     * @param dir    待扫描的目录
     * @param filter 过滤器
     * @return
     */
    public static void listFiles(String dir, List<File> list, FileFilter filter) {
        File file = new File(dir);
        for (File f : file.listFiles()) {
            if (f.isDirectory())
                listFiles(f.getPath(), list, filter);
            else if (filter.accept(f))
                list.add(f);
        }
    }

    /**
     * 转换 I420（YV12，COLOR_FormatYUV420Planar格式（I420)）为NV21格式
     *
     * @param input
     * @param width
     * @param height
     * @return
     */
    public static void I420ToNV21(byte[] input, byte output[], int width, int height) {
        int frameSize = width * height;
        int qFrameSize = frameSize / 4;
        int tempFrameSize = frameSize * 5 / 4;
        System.arraycopy(input, 0, output, 0, frameSize);

        for (int i = 0; i < qFrameSize; ++i) {
            output[frameSize + (i << 1)] = input[tempFrameSize + i];
            output[frameSize + (i << 1) + 1] = input[frameSize + i];
        }
    }

    public byte[] nv21ToI420(byte[] data, int width, int height) {
        byte[] ret = new byte[width * height * 3];
        int total = width * height;

        ByteBuffer bufferY = ByteBuffer.wrap(ret, 0, total);
        ByteBuffer bufferU = ByteBuffer.wrap(ret, total, total / 4);
        ByteBuffer bufferV = ByteBuffer.wrap(ret, total + total / 4, total / 4);

        bufferY.put(data, 0, total);
        for (int i = total; i < data.length; i += 2) {
            bufferV.put(data[i]);
            bufferU.put(data[i + 1]);
        }
        return ret;
    }

    public static String getApkVersion(Context context, String apkfile) {
        PackageManager pm = context.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(apkfile, 0);
        return info.versionName;
    }

    /**
     * 根据包名启动应用程序
     *
     * @param context
     * @param packageName
     */
    public static void launchApk(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(packageName);
        context.startActivity(intent);
    }

    public static String md5(String s) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
            return bin2hex(md.digest(s.getBytes("UTF-8")), false);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 调整图片大小
     *
     * @param filePath
     * @param w        新图片宽度
     * @param h        新图片高度
     * @return 调整后的新位图
     */
    public static Bitmap resizeImage(String filePath, int w, int h) {
        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        if (bitmap == null) return null;
        if (w > 0 && h > 0) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            float scaleWidth = ((float) w) / width;
            float scaleHeight = ((float) h) / height;

            Matrix matrix = new Matrix();
            matrix.postScale(scaleWidth, scaleHeight);
            return Bitmap.createBitmap(bitmap, 0, 0, width,
                    height, matrix, true);
        } else {
            return bitmap;
        }
    }

    /**
     * 根据byte[]内容，返回字符串数据，可以指定编码格式，如果失败，返回裸流
     *
     * @param bytes   待转换的原始字节值
     * @param charset 待转换的格式，例如 "UTF-8"
     * @return 返回字符串
     */
    public static String bin2str(byte[] bytes, String charset) {
        try {
            return new String(bytes, charset);
        } catch (Exception e) {
            return new String(bytes);
        }
    }

    /**
     * 判断网络是否在线
     * @param context
     * @return 在线返回 true，失败返回 false
     */
    static public boolean isNetworkConnected(Context context) {
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            if (mNetworkInfo != null) {
                return mNetworkInfo.isAvailable();
            }
        }
        return false;
    }

    /**
     * 复制文件
     * @param src 源文件
     * @param dst 目标文件
     * @throws IOException
     */
    public static void copy(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src)) {
            try (OutputStream out = new FileOutputStream(dst)) {
                // Transfer bytes from in to out
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    public static String subString(String str, int start) {
        if (str != null && str.length() > start) {
            return str.substring(start);
        }
        return "";
    }

    public static String getSimCardICCID() throws Exception {
        String results = SystemManager.shellExec("getprop", "ril.iccid.sim1");
        if (results != null) {
            String cardNo = results.split(":")[1].trim();
            cardNo = cardNo.replace("[", "");
            cardNo = cardNo.replace("]", "");
            // iccid卡号是固定20位
            if (cardNo.length() == 20) return cardNo;
        }
        return null;
    }
}