package hikvision.zhanyun.com.hikvision.device.iray;

import static lyh.Utils.PERIOD_SECOND;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Vector;

import hikvision.zhanyun.com.hikvision.IRSetting;
import hikvision.zhanyun.com.hikvision.R;
import hikvision.zhanyun.com.hikvision.utils.Log;

public class IRRegionTemp {
    Context context;
    private final int RESIZE;
    public static class TemperatureSampleResult {
        public float minTemperature;
        public float maxTemperature;
        public float avgTemperature;
        public Point minTemperaturePixel = new Point();
        public Point maxTemperaturePixel = new Point();

        public String toString() {
            return String.format("最高温：%.1f 坐标：(%d, %d), 最低温：%.1f 坐标：(%d, %d)",
                    maxTemperature, maxTemperaturePixel.x, maxTemperaturePixel.y,
                    minTemperature, minTemperaturePixel.x, minTemperaturePixel.y);
        }

    }

    private static final int CAMERA_RESOLUTION_W = 384;
    private static final int CAMERA_RESOLUTION_H = 288;
    private IRSetting.SensorConfig sensorConfig = new IRSetting.SensorConfig();

    public static int unitTemperature; // 0:摄氏 1:华氏
    private final int width;
    private final int height;

    public final int index; // 显示在温度框上的索引，和左下角的温度值对应
    private int indexX, indexY; // 显示index的(x，y)坐标
    private Vector<Point> points = new Vector<>();
    private final float[] temperature;// = new float[384 * 288 + 10];
    private TemperatureSampleResult result = new TemperatureSampleResult();
    private Vector<Integer> leftXValues;// = new Vector<>(); // 存放图像左半部分的x坐标，点、线除外
    private Vector<Integer> rightXValues;// = new Vector<>(); // 存放图像右半部分的x坐标，点、线除外

    private final Paint linePaint;
    private final Paint tempPaint;
    public enum RegionType {
        REGION_LIVE,
        REGION_STATIC
    };

    private long lastUpdateTime;
    private IRTempAlarm tempAlarm;
    // 获取最小矩阵和顶点、底点坐标
    private Point rectLftTop;
    private Point rectRgtBtm;
    private Vector<Point> linePixels;
    private IRSetting.IrRegion tempRegion;

    public IRRegionTemp(Context context, int width, int height, int index, int resize) {
        this.context = Objects.requireNonNull(context);
        this.width = width;
        this.height = height;
        this.index = index;
        this.RESIZE = resize;

        linePaint = new Paint();
        linePaint.setStrokeWidth(1);
        linePaint.setAntiAlias(true);
        linePaint.setTextSize(16);
        linePaint.setDither(false);
        linePaint.setColor(Color.BLUE);

        tempPaint = new Paint();
        tempPaint.setStrokeWidth(1);
        tempPaint.setTextSize(16);
        tempPaint.setAntiAlias(true);
        tempPaint.setColor(Color.GREEN);

        temperature = new float[width * height + 10];
    }

    public IRRegionTemp(Context context, IRSetting.IrRegionInfo regionInfo, int width, int height, int index, int resize) {
        this(context, width, height, index, resize);
        tempAlarm = new IRTempAlarm(regionInfo.highThres, regionInfo.lowThres);
        tempRegion = regionInfo.irRegion.clone();

        if (tempRegion != null) {
            for (Point p : tempRegion.points) {
                p.x = p.x * width / 255;
                p.y = p.y * height / 255;
            }
            this.points.addAll(tempRegion.points);

            if (points.size() == 1) {
                indexX = points.get(0).x;
                indexY = points.get(0).y;
            } else if (points.size() == 2) {
                linePixels = getLinePoints(points.get(0), points.get(1));
                indexX = (points.get(0).x + points.get(1).x) / 2;
                indexY = (points.get(0).y + points.get(1).y) / 2;
            } else if (points.size() > 2) {
                genShapePoints();
                indexX = (points.get(0).x + points.get(1).x) / 2;
                indexY = (points.get(0).y + points.get(1).y) / 2;
            }
            indexX *= RESIZE;
            indexY *= RESIZE;
        }
    }

    public EnumSet<IRTempAlarm.AlarmType> getTemperatureAlarm(TemperatureSampleResult result, byte flag) { /////
        return tempAlarm.checkAlarms("测温告警", result, flag); /////
    }

    public IRSetting.IrRegion tempRegion() {
        return tempRegion;
    }

    public static void setTempUnit(int unit) {
        unitTemperature = unit;
    }

    private static float getAverageValue (float[] data) {
        float total = 0;
        for (float t : data) {
            total += t;
        }
        return total / data.length;
    }

    public void setTempRaw(float[] data) {
        synchronized (temperature) {
            System.arraycopy(data, 0, temperature, 0, data.length);
        }
    }

    public TemperatureSampleResult getGlobalTemp () {
        try {
            synchronized (temperature) {
                //result.avgTemperature = getAverageValue(temperature);
                result.maxTemperature = temperature[3];
                result.minTemperature = temperature[6];
                result.maxTemperaturePixel.set((int) temperature[1], (int) temperature[2]);
                result.minTemperaturePixel.set((int) temperature[4], (int) temperature[5]);
            }

            if (unitTemperature != 0) {
                result.avgTemperature = result.avgTemperature * 1.8f + 32;
                result.maxTemperature = result.maxTemperature * 1.8f + 32;
                result.minTemperature = result.minTemperature * 1.8f + 32;
            }
        } catch (Exception e) {
            Log.i(Log.TAG, "读取温度异常：" + e);
        }
        return result;
    }

//    private void drawPoint(Canvas canvas, int x, int y, Bitmap point) {
    private void drawPoint(Canvas canvas, int x, int y, Bitmap point, float sizeX, float sizeY) { /////
        x = (x >= 2) ? (x - 2) : x;
        y = (y >= 2) ? (y - 2) : y;
//        Rect hiDest = new Rect(x, y, x + 10, y + 10);
        Rect hiDest = new Rect((int) ((x - 2.5) * sizeX / width), (int) ((y - 2.5) * sizeY / height), (int) ((x + 7.5) * sizeX / width), (int) ((y + 7.5) * sizeY / height)); /////
        canvas.drawBitmap(point, null, hiDest, null);
    }


    private void drawHighLowTemp(Canvas canvas, IRRegionTemp.TemperatureSampleResult tempResult, float sizeX, float sizeY) { /////

        drawPoint(canvas, tempResult.maxTemperaturePixel.x, tempResult.maxTemperaturePixel.y,
                BitmapFactory.decodeResource(context.getResources(), R.drawable.cursorgreen), sizeX, sizeY);
    }


    private void drawReginIndex(Canvas canvas) {
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setTextSize(16);
        paint.setAntiAlias(true);
        canvas.drawText(String.valueOf(index), indexX, indexY, paint);
    }


    public void drawRegionTemp(Bitmap bitmap, byte hotTracker) {
        Canvas canvas = new Canvas(bitmap);
        //Log.i(Log.TAG, "画多边形：" + points.size());
        switch (points.size()) {
            case 0:
                return;
            case 1:
                int x = points.get(0).x;
                int y = points.get(0).y;
                //canvas.drawPoint(x, y, linePaint);

//                drawPoint(canvas, result.maxTemperaturePixel.x * RESIZE, result.maxTemperaturePixel.y * RESIZE,
//                        BitmapFactory.decodeResource(context.getResources(), R.drawable.cursorgreen));
                /////
                // 框中温度点的坐标result.maxTemperaturePixel.x, result.maxTemperaturePixel.y
                drawPoint(canvas, result.maxTemperaturePixel.x, result.maxTemperaturePixel.y,
                        BitmapFactory.decodeResource(context.getResources(), R.drawable.cursorgreen), bitmap.getWidth(), bitmap.getHeight());
                /////

                // 画点温度
                /*String degree = String.format("%.1f%s", result.maxTemperature, unitTemperature == 0 ? "°C" : "°F");
                canvas.drawText(degree, x + 5, y, tempPaint);*/
//                drawHighLowTemp(canvas, result);
                drawHighLowTemp(canvas, result, bitmap.getWidth(), bitmap.getHeight()); /////
                break;
            case 2:
                Point start = points.get(0);
                Point end = points.get(1);
                canvas.drawLine(start.x * RESIZE, start.y * RESIZE,
                        end.x * RESIZE, end.y * RESIZE, linePaint);

                drawHighLowTemp(canvas, result, bitmap.getWidth(), bitmap.getHeight());
                break;
            default:
                float[] pointArray = new float[points.size() * 4];
                for (int i = 0; i < points.size(); i++) {
                    pointArray[i * 4] = points.get(i).x * RESIZE;
                    pointArray[i * 4 + 1] = points.get(i).y * RESIZE;
                    if (i + 1 < points.size()) {
                        pointArray[i * 4 + 2] = points.get(i + 1).x * RESIZE;
                        pointArray[i * 4 + 3] = points.get(i + 1).y * RESIZE;
                    } else {
                        pointArray[i * 4 + 2] = points.get(0).x * RESIZE;
                        pointArray[i * 4 + 3] = points.get(0).y * RESIZE;
                    }
                }
                canvas.drawLines(pointArray, linePaint);
                //Log.i(Log.TAG, "画多边形：" + Arrays.toString(pointArray));
                drawHighLowTemp(canvas, result, bitmap.getWidth(), bitmap.getHeight());
                break;
        }
        drawReginIndex(canvas);
    }



    public TemperatureSampleResult getTemperature() {
        if (System.currentTimeMillis() - lastUpdateTime < PERIOD_SECOND) {
            return result;

        }

        lastUpdateTime = System.currentTimeMillis();

        switch (points.size()) {
            case 0:
                return getGlobalTemp();
            case 1:
                return getPointTemp();
            case 2:
                return getLineTemp();
            default:
                return getShapeTemp();
        }

    }


    private TemperatureSampleResult getPointTemp() {
        try {
            int idx = points.get(0).y * width + points.get(0).x + 10;
            synchronized (temperature) {
                result.maxTemperature = result.minTemperature = result.avgTemperature = temperature[idx];

                Log.i(Log.TAG, "英睿 getPointTemp，点的温度为" + result.maxTemperature);
            }

            result.minTemperaturePixel = result.maxTemperaturePixel = points.get(0);
        } catch (Exception e) {
            Log.i(Log.TAG, "读取点温度异常：" + e);
        }
        return result;
    }


    public Point transOnResolution(Point point) {
        point.x = point.x * 255 / width;
        point.y = point.y * 255 / height;
        return point;
    }


    private Vector<Point> getLinePoints(Point p1, Point p2) {
        Vector<Point> points = new Vector<>();
        // 避免除数为0
        float k = (p1.x == p2.x) ? (float) Integer.MAX_VALUE : (float) (p1.y - p2.y) / (p1.x - p2.x);
        float b = p1.y - k * p1.x;
        if (Math.abs(k) > 1) {
            // 斜率大于45度，取y轴最小点为起点
            Point start = p1.y > p2.y ? p2 : p1;
            Point end   = p1.y > p2.y ? p1 : p2;
            for (int y = start.y; y <= end.y; y++) {
                // 完全竖线的话，x值是一样的
                int x = (start.x == end.x) ? start.x : Math.round((y - b) / k);
                points.add(new Point(x, y));
            }
        } else {
            // 斜率小于45度，取x轴最小点为起点
            Point start = p1.x > p2.x ? p2 : p1;
            Point end   = p1.x > p2.x ? p1 : p2;
            for (int x = start.x; x <= end.x; x++) {
                // 完全横线的话，y值是一样的
                int y = (start.y == end.y) ? start.y : Math.round(k * x + b);
                points.add(new Point(x, y));
            }
        }
        return points;
    }


    private TemperatureSampleResult getLineTemp() {
        try {
            TreeMap<Float, Point> maps = new TreeMap<>();
            for (Point point : linePixels) {
                float t = temperature[point.y * width + point.x + 10];
                maps.put(t, point);
            }
            result.minTemperature = maps.firstKey();
            result.minTemperaturePixel = maps.firstEntry().getValue();
            result.maxTemperature = maps.lastKey();
            result.maxTemperaturePixel = maps.lastEntry().getValue();

            //Log.i(Log.TAG, "读取线温：" + linePixels);
            //Log.i(Log.TAG, "读取线温：" + result.toString());
        } catch (Exception e) {
            Log.i(Log.TAG, "读取线温度异常：" + e);
        }
        return result;
    }


    private Vector<Integer> getYaxisXVaules(Point p1, Point p2) {
        Vector<Integer> points = new Vector<>();
        float k = (p2.x == p1.x) ? (float) Integer.MAX_VALUE : (float) (p2.y - p1.y) / (p2.x - p1.x);
        float b = p2.y - k * p2.x;


        if (p2.y == p1.y) {
            //points.add(start.x);
        } else {
            Point start = p1;
            Point end   = p2;
            if (start.y < end.y) {
                for (int y = start.y; y < end.y; y++) {
                    points.add(Math.round((y - b) / k));
                }
            } else {
                for (int y = start.y; y > end.y; y--) {
                    points.add(Math.round((y - b) / k));
                }
            }
        }
        return points;
    }


    // 重新排序多边形顶点，最高点排在第一个
    private Vector<Point> sortSharpPoints(Vector<Point> points, Point first) {
        Vector<Point> sorted = new Vector<>();
        int index = points.indexOf(first);
        while (sorted.size() < points.size()) {
            sorted.add(points.get(index++));
            if (index == points.size()) index = 0;
        }
        return sorted;
    }

    private void genShapePoints() {
        // 获取最小矩阵和顶点、底点坐标
        rectLftTop = new Point(points.get(0));
        rectRgtBtm = new Point(points.get(0));
        Point top = new Point(points.get(0));
        Point btm = new Point(points.get(0));
        for (Point point : points) {
            if (point.x < rectLftTop.x) rectLftTop.x = point.x;
            if (point.y < rectLftTop.y) {
                rectLftTop.y = point.y;
                top.set(point.x, point.y);
            }
            if (point.x > rectRgtBtm.x) rectRgtBtm.x = point.x;
            if (point.y > rectRgtBtm.y) {
                rectRgtBtm.y = point.y;
                btm.set(point.x, point.y);
            }
        }

        points = sortSharpPoints(points, top);
        boolean revert = false;
        Vector<Integer> downPoints = new Vector<>();
        Vector<Integer> upperPoints = new Vector<>();

        for (int i = 0; i < points.size(); i++) {
            Point s = points.get(i);
            Point e = i < points.size() - 1 ? points.get(i+1) : points.get(0);
            if (!revert && s.equals(btm)) revert = true;

            Vector<Integer> xVaules = getYaxisXVaules(s, e);
            if (!revert) {
                downPoints.addAll(xVaules);
            } else {
                upperPoints.addAll(xVaules);
            }
        }

        // upperPoints进行反序
        Collections.reverse(upperPoints);

        if (downPoints.get(downPoints.size() / 2) < upperPoints.get(upperPoints.size() / 2)) {
            leftXValues = downPoints;
            rightXValues = upperPoints;
        } else {
            leftXValues = upperPoints;
            rightXValues = downPoints;
        }
    }

    private TemperatureSampleResult getShapeTemp() {
        try {
            TreeMap<Float, Point> maps = new TreeMap<>();
            for (int y = rectLftTop.y, i = 0; y < rectRgtBtm.y; y++, i++) {
                for (int x = rectLftTop.x; x < rectRgtBtm.x; x++) {
                    if (x >= leftXValues.get(i) && x <= rightXValues.get(i)) {
                        float t = temperature[y * width + x + 10];
                        maps.put(t, new Point(x, y));
                    }
                }
            }

            result.minTemperature = maps.firstKey();
            result.minTemperaturePixel = maps.firstEntry().getValue();
            result.maxTemperature = maps.lastKey();
            result.maxTemperaturePixel = maps.lastEntry().getValue();
        } catch (Exception e) {
            Log.i(Log.TAG, "读取多边形温度异常：" + e);
        }
        return result;
    }


    // 绘制全局高低温
    public void drawTemperature(Canvas canvas, float t, float x, float y, float sizeX, float sizeY) { /////

        String unit = (unitTemperature == 0) ? "°C" : "°F";

        String text = (Math.abs(t) < 100) ? String.format("%.1f%s", t, unit) : String.format("%d%s", Math.round(t), unit);

        tempPaint.setStrokeWidth(1 * sizeX / 640);
        tempPaint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));
        canvas.drawText(text, x, y, tempPaint);
    }
}
