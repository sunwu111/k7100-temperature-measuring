package hikvision.zhanyun.com.hikvision.bean;

import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PTZTime {
    private int year;
    private int month;
    private int day;
    private int hour;
    private int minute;
    private int second;

    // 构造方法
    public PTZTime(int year, int month, int day, int hour, int minute, int second) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
    }

    // Getter方法
    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public int getDay() {
        return day;
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }

    public int getSecond() {
        return second;
    }

    /**
     * 将时间字符串解析为PTZTime对象
     * @param timeStr 时间字符串，格式如"2013-06-18 10:15:30"
     * @return PTZTime对象，解析失败返回null
     */
    public static String parse(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            Log.e("PTZTime", "时间字符串为空");
            return null;
        }

        // 定义时间格式
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        try {
            Date date = sdf.parse(timeStr);

            // 提取年月日时分秒
            // 注意：Calendar的月份是从0开始的，需要+1
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTime(date);

            int year = calendar.get(java.util.Calendar.YEAR);
            int month = calendar.get(java.util.Calendar.MONTH) + 1; // 月份+1
            int day = calendar.get(java.util.Calendar.DAY_OF_MONTH);
            int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY); // 24小时制
            int minute = calendar.get(java.util.Calendar.MINUTE);
            int second = calendar.get(java.util.Calendar.SECOND);

            return String.format(Locale.getDefault(),
                    "%04d-%02d-%02d %02d:%02d:%02d",
                    year, month, day, hour, minute, second);

        } catch (ParseException e) {
            Log.e("PTZTime", "时间解析失败: " + e.getMessage() + "，输入字符串: " + timeStr);
            return null;
        }
    }

    // 重写toString方法，方便日志输出
    @Override
    public String toString() {
        return String.format(Locale.getDefault(),
                "%04d-%02d-%02d %02d:%02d:%02d",
                year, month, day, hour, minute, second);
    }
}

