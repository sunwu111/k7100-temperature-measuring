package hikvision.zhanyun.com.hikvision.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.Camera;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import androidx.annotation.RequiresApi;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import hikvision.zhanyun.com.hikvision.R;

/**
 * Created by ZY004Engineer on 2018/9/19.
 */

public class WUtils {

    /**
     * 文本转成Bitmap
     *
     * @param text    文本内容
     * @param context 上下文
     * @return 图片的bitmap
     */
    @SuppressLint("ResourceAsColor")
    private static Bitmap textToBitmap(String text, Context context) {
        float scale = context.getResources().getDisplayMetrics().scaledDensity;
        TextView tv = new TextView(context);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(layoutParams);
        tv.setText(text);
        tv.setTextSize(scale * 40);
        tv.setGravity(Gravity.CENTER_HORIZONTAL);
        tv.setDrawingCacheEnabled(true);
        tv.setTextColor(R.color.colorWhite);
//        tv.setBackgroundColor(android.R.color.transparent);
        tv.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        tv.layout(0, 0, tv.getMeasuredWidth(), tv.getMeasuredHeight());
        tv.buildDrawingCache();
        Bitmap bitmap = tv.getDrawingCache();
        int rate = bitmap.getHeight() / 20;
        return Bitmap.createScaledBitmap(bitmap, bitmap.getWidth() / rate, 20, false);
    }

    /**
     * 文字生成图片
     *
     * @param filePath filePath
     * @param text     text
     * @param context  context
     * @return 生成图片是否成功
     */
    public static boolean textToPicture(String filePath, String text, Context context) {
        Bitmap bitmap = textToBitmap(text, context);
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(filePath);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;

    }

    /**
     * 添加图片水印。
     *
     * @param src       源图片
     * @param watermark 图片水印
     * @param x         起始坐标x
     * @param y         起始坐标y
     * @return 带有图片水印的图片
     */
    public static Bitmap addImageWatermark(Bitmap src, Bitmap watermark, int x, int y) {
        Bitmap retBmp = src.copy(src.getConfig(), true);
        Canvas canvas = new Canvas(retBmp);
        canvas.drawBitmap(watermark, x, y, null);
        return retBmp;
    }

    /**
     * 保存图片到文件File。
     *
     * @param src     源图片
     * @param file    要保存到的文件
     * @param format  格式
     * @param recycle 是否回收
     * @return true 成功 false 失败
     */
    public static boolean save(Bitmap src, File file, Bitmap.CompressFormat format, boolean recycle) {
        if (isEmptyBitmap(src))
            return false;

        OutputStream os;
        boolean ret = false;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file));
            ret = src.compress(format, 100, os);
            if (recycle && !src.isRecycled())
                src.recycle();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ret;
    }

    /**
     * Bitmap对象是否为空。
     */
    public static boolean isEmptyBitmap(Bitmap src) {
        return src == null || src.getWidth() == 0 || src.getHeight() == 0;
    }

    /**
     * 方法描述：判断某一应用是否正在运行
     *
     * @param context     上下文
     * @param packageName 应用的包名
     * @return true 表示正在运行，false表示没有运行
     */
    public static boolean isAppRunning(Context context, String packageName) {
        boolean isAppRunning = false;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> list = am.getRunningTasks(100);
        if (list.size() <= 0) {
            return false;
        }
        for (ActivityManager.RunningTaskInfo info : list) {
            if (info.baseActivity.getPackageName().equals(packageName)) {
                isAppRunning = true;
                break;
            }
        }
        return isAppRunning;
    }

    /**
     * 重启app
     *
     * @param context
     * @param mClass
     */
    public static void rebootApp(Context context, Class<?> mClass) {
        Intent intent = new Intent(context, mClass);
        PendingIntent restartIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        //退出程序
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 500,
                restartIntent); // 1秒钟后重启应用
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    /**
     * 获取重启手机时间
     */
    public static long getRebootTime(int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.DAY_OF_MONTH, calendar.get(Calendar.DAY_OF_MONTH) + 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long selectTime = calendar.getTimeInMillis();
        return selectTime - System.currentTimeMillis();
    }


    /**
     * 注释：int到字节数组的转换！
     *
     * @param number
     * @return
     */

    public static byte[] intToByte(int number) {

        int temp = number;

        byte[] b = new byte[4];

        for (int i = 0; i < b.length; i++) {

            b[i] = new Integer(temp & 0xff).byteValue();// 将最低位保存在最低位

            temp = temp >> 8; // 向右移8位

        }

        return b;

    }

    private byte[] longBytes(long id) {
        byte[] arr = new byte[4];
        arr[0] = (byte) (id >> 24);
        arr[1] = (byte) ((id & 0x00FF0000) >> 16);
        arr[2] = (byte) ((id & 0x0000FF00) >> 8);
        arr[3] = (byte) (id & 0x000000FF);
        return arr;
    }


    public static void memset(byte[] buf, int value, int size) {

        for (int i = 0; i < size; i++) {

            buf[i] = (byte) value;

        }
    }

    /**
     * 获取某段时间的(毫秒)
     *
     * @return 结果
     */
    public static long getMillisecond(int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, second);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }


    /**
     * 调整图像大小
     *
     * @param filePath
     * @param w
     * @param h
     * @return
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
            // if you want to rotate the Bitmap
            // matrix.postRotate(45);
            return Bitmap.createBitmap(bitmap, 0, 0, width,
                    height, matrix, true);
        } else {
            return bitmap;
        }
    }

    /**
     * 根据图片路径，得到压缩过的位图
     *
     * @param path
     * @param width
     * @param height
     * @return
     */
    public static Bitmap getPressedBitmap(String path, int width, int height) {
        BitmapFactory.Options options = new BitmapFactory.Options();//new一个options
        options.inJustDecodeBounds = true;//先设置为true，即不读入图片到内存，先获取图片的信息，比如长宽等信息
        BitmapFactory.decodeFile(path, options);//此句代码是真正的去读取图片的长宽等信息，并且存储在options里面，在后面的代码中我们可以看到options.outWidth 和 options.outHeight得到的是图片的宽和高。这句代码所以不能没有，否则无法压缩图片。这里得到的bitmap是为null
        options.inSampleSize = getBitmapSampleSize(options, width, height);//根据给定的宽高来压缩图片的比例
        options.inJustDecodeBounds = false;//设置为false，是要将图片以一定比例压缩后读入内存中
        return BitmapFactory.decodeFile(path, options);
    }


    /**
     * 根据要去的宽高，压缩图片
     *
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    public static int getBitmapSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int imgWidth = options.outWidth;
        int imgHeight = options.outHeight;
        int inSimpleSize = 1;
        if (imgWidth > imgHeight || imgWidth < imgHeight) {
            final int heightRatio = imgWidth / reqWidth;
            final int widthRatio = imgHeight / reqHeight;
            inSimpleSize = widthRatio < heightRatio ? widthRatio : heightRatio;
        }
        return inSimpleSize;
    }


    // 为图片target添加水印文字
// Bitmap target：被添加水印的图片
// String mark：水印文章
//position 水印位置
    public static Bitmap createWatermark(Bitmap target, String mark, int fontsize, int position) {
        int w = target.getWidth();
        int h = target.getHeight();
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint p = new Paint();
        // 水印的颜色
        p.setColor(Color.WHITE);
        // 水印的字体大小
        p.setTextSize(fontsize);
        p.setAntiAlias(true);// 去锯齿
        canvas.drawBitmap(target, 0, 0, p);
        // 在左边的中间位置开始添加水印
        canvas.drawText(mark, 15, position, p);
        canvas.save();
        canvas.restore();
        return bmp;
    }

    public static String getDate() {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = new Date(System.currentTimeMillis());
        return simpleDateFormat.format(date);
    }


    /**
     * @param resource   原图
     * @param saturation 饱和度
     * @return
     */
    public static Bitmap getChangedBitmap(Bitmap resource,
                                          float saturation,
                                          float lum) {
        Bitmap out = Bitmap.createBitmap(resource.getWidth()
                , resource.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint();
        paint.setAntiAlias(true);//抗锯齿

        //调整饱和度
        ColorMatrix saturationMatrix = new ColorMatrix();
        saturationMatrix.setSaturation(saturation);

        //调整亮度
        ColorMatrix lumMatrix = new ColorMatrix();
        lumMatrix.setScale(lum, lum, lum, 1);

        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.postConcat(saturationMatrix);
        colorMatrix.postConcat(lumMatrix);
        paint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        canvas.drawBitmap(resource, 0, 0, paint);
        return out;

    }


    /**
     * 17
     * 删除?天前的文件
     *
     * @param days 距离当前多少天 如删除距离三天的文件   传入3  四天 传入4
     * @param path 路径
     * @throws FileNotFoundException
     * @throws ParseException
     */
    public static void deleteOldFile(String path, int days) throws FileNotFoundException, ParseException {
        File file = new File(path);
        File[] files = file.listFiles();
        // 获取当前时间
        Date thisTime = new Date(System.currentTimeMillis());
        if (files == null) {
            Log.e("error", "空目录");
            return;
        }
        long day = 0;
        for (int i = 0; i < files.length; i++) {
            //以文件名来判断是否大于三天 时间判断格式：yyyyMMdd；文件名格式:xxx_yyyyMMdd_HHmmss_xx.后缀 提取文件名格式可在字符串截取修改
//            String fileName = files[i].getName();
//            String time = fileName.substring( fileName.indexOf("_")+1,fileName.indexOf("_",fileName.indexOf("_")+1));
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
//            Date times  =sdf.parse(time);
//            day=(thisTime.getTime()-times.getTime())/(24*60*60*1000);

            //以最后修改的时间来判断（如果旧文件修改过则会把时间更新到修改的时间，会导致无法判断出是否是旧文件)
            // （如:2018，10，11的文件，在2018，10，15修改过后用此方法取到该文件的时间则是2018，10，15，无法识别这个是2018，10，11的旧文件））
            Date ti = new Date(files[i].lastModified());
            day = (thisTime.getTime() - ti.getTime()) / (24 * 60 * 60 * 1000);
            if (day > days) {
                //删除该文件
                files[i].delete();
            }
        }
    }

    /**
     * 排序(从小到大)
     */
    public static long[] sort(long[] value) {
        for (int i = 0; i < value.length; i++) {
            for (int j = 0; j < value.length; j++) {
                if (value[i] < value[j]) {
                    long replaceNumb = value[i];
                    value[i] = value[j];
                    value[j] = replaceNumb;
                }
            }
        }
        return value;
    }

    /**
     * 获取手机信号强度，需添加权限 android.permission.ACCESS_COARSE_LOCATION <br>
     * API要求不低于17 <br>
     *
     * @return 当前手机主卡信号强度, 单位 dBm（-1是默认值，表示获取失败）
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @SuppressLint("MissingPermission")
    public static int getMobileDbm(Context context) {
        int dbm = -1;
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        List<CellInfo> cellInfoList;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            cellInfoList = tm.getAllCellInfo();
            if (null != cellInfoList) {
                for (CellInfo cellInfo : cellInfoList) {
                    if (cellInfo instanceof CellInfoGsm) {
                        CellSignalStrengthGsm cellSignalStrengthGsm = ((CellInfoGsm) cellInfo).getCellSignalStrength();
                        dbm = cellSignalStrengthGsm.getDbm();
                    } else if (cellInfo instanceof CellInfoCdma) {
                        CellSignalStrengthCdma cellSignalStrengthCdma =
                                ((CellInfoCdma) cellInfo).getCellSignalStrength();
                        dbm = cellSignalStrengthCdma.getDbm();
                    } else if (cellInfo instanceof CellInfoWcdma) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            CellSignalStrengthWcdma cellSignalStrengthWcdma =
                                    ((CellInfoWcdma) cellInfo).getCellSignalStrength();
                            dbm = cellSignalStrengthWcdma.getDbm();
                        }
                    } else if (cellInfo instanceof CellInfoLte) {
                        CellSignalStrengthLte cellSignalStrengthLte = ((CellInfoLte) cellInfo).getCellSignalStrength();
                        dbm = cellSignalStrengthLte.getDbm();
                    }
                }
            }
        }
        return dbm;
    }

    /**
     * 递归删除文件和文件夹
     *
     * @param file 要删除的根目录
     */
    public static void RecursionDeleteFile(File file) {
        if (file.isFile()) {
            file.delete();
            return;
        }
        if (file.isDirectory()) {
            File[] childFile = file.listFiles();
            if (childFile == null || childFile.length == 0) {
                file.delete();
                return;
            }
            for (File f : childFile) {
                RecursionDeleteFile(f);
            }
//            file.delete();
        }
    }

    /**
     * 获取目录下所有文件(按时间排序)
     *
     * @param path
     * @return
     */
    public static List<File> listFileSortByModifyTime(String path) {
        List<File> list = getFiles(path, new ArrayList<File>());
        if (list != null && list.size() > 0) {
            Collections.sort(list, new Comparator<File>() {
                public int compare(File file, File newFile) {
                    if (file.lastModified() < newFile.lastModified()) {
                        return -1;
                    } else if (file.lastModified() == newFile.lastModified()) {
                        return 0;
                    } else {
                        return 1;
                    }
                }
            });
        }
        return list;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static void isAmpleSpace(Context context, String filePath) {
        String size = getAvailableExternalMemorySize(context);
        if (size.endsWith("M")) {
            List<File> listFile = listFileSortByModifyTime(filePath);
            if (listFile != null && listFile.size() > 0) {
                listFile.get(0).delete();
                listFile.remove(0);
                isAmpleSpace(context, filePath);
            }
        } else if (size.endsWith("G")) {
            String[] fileSize = size.split("G");
            int fileLen = Integer.parseInt(fileSize[0]);
            if (fileLen < 1) {
                List<File> listFile = listFileSortByModifyTime(filePath);
                if (listFile != null && listFile.size() > 0) {
                    listFile.get(0).delete();
                    listFile.remove(0);
                    isAmpleSpace(context, filePath);
                }
            }
        }
    }

    /**
     * 获取手机外部可用存储空间
     *
     * @param context
     * @return 以M, G为单位的容量
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static String getAvailableExternalMemorySize(Context context) {
        File file = Environment.getExternalStorageDirectory();
        StatFs statFs = new StatFs(file.getPath());
        long availableBlocksLong = statFs.getAvailableBlocksLong();
        long blockSizeLong = statFs.getBlockSizeLong();
        return Formatter.formatFileSize(context, availableBlocksLong
                * blockSizeLong);
    }

    /**
     * 获取目录下所有文件
     *
     * @param realpath
     * @param files
     * @return
     */
    public static List<File> getFiles(String realpath, List<File> files) {
        File realFile = new File(realpath);
        if (realFile.isDirectory()) {
            File[] subfiles = realFile.listFiles();
            for (File file : subfiles) {
                if (file.isDirectory()) {
                    getFiles(file.getAbsolutePath(), files);
                } else {
                    files.add(file);
                }
            }
        }
        return files;
    }

    /**
     * 获取已用流量 （包括下载、上传）
     *
     * @param context
     * @return
     */
    public static String getMobileByte(Context context) {
        //1.获取一个包管理器。
//        PackageManager pm = context.getPackageManager();
//        //2.遍历手机操作系统 获取所有的应用程序的uid
//        List<ApplicationInfo> appliactaionInfos = pm.getInstalledApplications(0);
//        for (ApplicationInfo applicationInfo : appliactaionInfos) {
//            int uid = applicationInfo.uid;    // 获得软件uid
//            //proc/uid_stat/10086
//            long tx = TrafficStats.getUidTxBytes(uid);//发送的 上传的流量byte
//            long rx = TrafficStats.getUidRxBytes(uid);//下载的流量 byte
//            //方法返回值 -1 代表的是应用程序没有产生流量 或者操作系统不支持流量统计
//        }
        //这里单位MB
//        TrafficStats.getTotalTxBytes();//手机全部网络接口 包括wifi，3g、2g上传的总流量
//        TrafficStats.getTotalRxBytes();//手机全部网络接口 包括wifi，3g、2g下载的总流量
//        String txBytes = df.format(TrafficStats.getMobileTxBytes() == TrafficStats.UNSUPPORTED ? 0 : TrafficStats.getMobileTxBytes()* 1.00d / (1024 * 1024) );//获取手机3g/2g网络上传的总流量
//        String rxBytes = df.format(TrafficStats.getMobileRxBytes() == TrafficStats.UNSUPPORTED ? 0 : TrafficStats.getMobileRxBytes()* 1.00d / (1024 * 1024) );//手机2g/3g下载的总流量
        DecimalFormat df = new DecimalFormat("#####0.00");
        long lMobile = TrafficStats.getMobileTxBytes() + TrafficStats.getMobileRxBytes();
        String strMobile = "0.00Bytes";
        if (lMobile < 1024) {
            strMobile = df.format((lMobile) * 1.0d) + "Bytes";
        } else if (lMobile >= 1024 && lMobile < (1024 * 1024)) {
            strMobile = df.format(((lMobile) * 1.0d / 1024)) + "KB";
        } else if (lMobile >= (1024 * 1024) && lMobile < (1024 * 1024 * 1024)) {
            strMobile = df.format(((lMobile) * 1.0d / (1024 * 1024))) + "MB";
        } else if (lMobile >= (1024 * 1024 * 1024)) {
            strMobile = df.format(((lMobile) * 1.0d / (1024 * 1024 * 1024))) + "GB";
        }

        return strMobile;
    }

    /**
     * 获取当前上传和下载流量总和
     *
     * @return
     */
    public static long getNetworkBytes() {
        return TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes();
    }

    //读取指定目录下的所有TXT文件的文件名
    public static String getFileName(File[] files, String type) {
        StringBuilder str = new StringBuilder();
        if (files != null) {// 先判断目录是否为空，否则会报空指针  
            for (File file : files) {
                if (file.isDirectory()) {//检查此路径名的文件是否是一个目录(文件夹)
                    getFileName(file.listFiles(), "264");
                } else {
                    String fileName = file.getName();
                    if (fileName.endsWith("." + type)) {
                        String s = fileName.substring(0, fileName.lastIndexOf("."));
                        Log.i("hikData", "文件名：   " + s);
                        str.append(fileName.substring(0, fileName.lastIndexOf("."))).append("\n");
                    }
                }
            }

        }
        return str.toString();
    }

    /**
     * 比较日期大小
     *
     * @param str1
     * @param str2
     * @return (str1 小于等 str2)返回true, 否则false
     */
    @SuppressLint("SimpleDateFormat")
    public static boolean isDate2Bigger(String str1, String str2) {
        boolean isBigger = false;
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMddHHmmss");
        Date dt1 = null;
        Date dt2 = null;
        try {
            dt1 = sdf1.parse(str1);
            dt2 = sdf2.parse(str2);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        if (dt1.getTime() > dt2.getTime()) {
            isBigger = false;
        } else if (dt1.getTime() <= dt2.getTime()) {
            isBigger = true;
        }
        return isBigger;
    }

    /**
     * 采用累加和取反的校验方式，
     * 发送方将终端号码、控制字、数据长度和数据区的所有字节进行算术累加，
     * 抛弃高位，只保留最后单字节，将单字节取反；
     *
     * @param data 需要计算的数据
     * @return 结果
     */
    public static byte Crc(byte[] data) {
        int r = 0;
        byte b = 0;
        for (int i = 0; i < data.length; i++) r += data[i];
        b = (byte) (r & 0x00FF);
        b = (byte) ~b;
        return b;
    }

    /**
     * 设置系统时间
     *
     * @param context
     * @param year
     * @param month
     * @param date
     * @param hourOfDay
     * @param minute
     * @param second
     */
    public static void setSystemTime(Context context, int year, int month, int date, int hourOfDay, int minute, int second) {
        Calendar c = Calendar.getInstance();
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null || c == null) return;

        c.set(year, month, date, hourOfDay, minute, second);
        long when = c.getTimeInMillis();
        if (when / 1000 < Integer.MAX_VALUE) {
            am.setTime(when);
        }
    }


    /**
     * 判断当前应用是否是debug状态
     */
    public static boolean isApkInDebug(Context context) {
        try {
            ApplicationInfo info = context.getApplicationInfo();
            return (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 使屏幕常亮
     *
     * @param activity
     */
    public static void keepScreenLongLight(Activity activity) {
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * 录像文件
     *
     * @param startTime
     * @param endTime
     * @return
     */
    public static String getVideoName(long startTime, long endTime) {
        SimpleDateFormat simpleDate = new SimpleDateFormat("yyyyMMddHHmmss");
        return simpleDate.format(new Date(startTime)) + "_" + simpleDate.format(new Date(endTime)) + ".264";
    }

    //测试当前摄像头能否被使用
    public static boolean isCameraCanUse(int facing) {
        boolean canUse = true;
        Camera mCamera = null;
        try {
            // TODO camera驱动挂掉,处理??
            mCamera = Camera.open(facing);
        } catch (Exception e) {
            canUse = false;
        }
        if (canUse) {
            mCamera.release();
            mCamera = null;
        }

        return canUse;
    }

    //检查是否有可用摄像头
    public static boolean checkCameraFacing(final int facing) {
        final int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, info);
            if (facing == info.facing) {
                return true;
            }
        }
        return false;
    }

    /**
     * 设置当前窗口亮度
     *
     * @param activity   窗口
     * @param brightness 亮度值
     */
    public static void setWindowBrightness(Activity activity, int brightness) {
        Window window = activity.getWindow();
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.screenBrightness = brightness / 255.0f;
        window.setAttributes(lp);
    }

    /**
     * 返回当前程序版本名
     */
    public static String getAppVersionName(Context context) {
        String versionName = "";
        try {
            // ---get the package info---
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            versionName = pi.versionName;
            if (versionName == null || versionName.length() <= 0) {
                return "";
            }
        } catch (Exception e) {
            Log.d(Log.TAG, "VersionInfo:Exception" + e);
        }
        return versionName;
    }

}


