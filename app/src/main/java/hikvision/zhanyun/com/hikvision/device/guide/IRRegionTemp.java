package hikvision.zhanyun.com.hikvision.device.guide;

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
import hikvision.zhanyun.com.hikvision.MainActivity;
import hikvision.zhanyun.com.hikvision.R;
import hikvision.zhanyun.com.hikvision.utils.Log;

public class IRRegionTemp {
    Context context;


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
    private TemperatureSampleResult result = new TemperatureSampleResult();



    public static int unitTemperature; // 0:摄氏 1:华氏
    private final int width;
    private final int height;
    public int index; // 显示在温度框上的索引，和左下角的温度值对应
    private float indexX, indexY; // 显示index的(x，y)坐标
    private Vector<Point> points = new Vector<>();

    private final float[] temperature;// = new float[CAMERA_RESOLUTION_W * CAMERA_RESOLUTION_H + 10];

    private Vector<Integer> leftXValues;// = new Vector<>(); // 存放图像左半部分的x坐标，点、线除外
    private Vector<Integer> rightXValues;// = new Vector<>(); // 存放图像右半部分的x坐标，点、线除外
    private final Paint linePaint;
    private final Paint tempPaint;
    public enum RegionType {
        REGION_LIVE,
        REGION_STATIC,
    };

    private long lastUpdateTime;
    private IRTempAlarm tempAlarm;
    // 获取最小矩阵和顶点、底点坐标
    private Point rectLftTop;
    private Point rectRgtBtm;
    private Vector<Point> linePixels;
    private IRSetting.IrRegion tempRegion;

    public IRRegionTemp(Context context, int width, int height, int index, float sizeX, float sizeY) {
        this.context = Objects.requireNonNull(context);
        this.width = width;
        this.height = height;
        this.index = index;
        //this.RESIZE = resize;

        linePaint = new Paint();
        linePaint.setStrokeWidth(1 * sizeX / 640);  // 使用宽度比例来缩放文字宽度
        linePaint.setAntiAlias(true);
        linePaint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));  // 使用宽度、高度比例小的来缩放文字尺寸
        linePaint.setDither(false);
        linePaint.setColor(Color.BLUE);

        tempPaint = new Paint();
        tempPaint.setStrokeWidth(1 * sizeX / 640);
        tempPaint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));
        tempPaint.setAntiAlias(true);
        tempPaint.setColor(Color.GREEN);

        temperature = new float[width * height + 10];
    }

    public IRRegionTemp(Context context, IRSetting.IrRegionInfo regionInfo, int width, int height, int index, float sizeX, float sizeY) {
        this(context, width, height, index, sizeX, sizeY);
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
//            indexX *= RESIZE;
//            indexY *= RESIZE;
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


    public  double celsiusToFahrenheit(double celsius) {
        return (celsius * 9 / 5) + 32;
    }

//    private float calibration(float mTemp, float distance, float tempCompensate, float focalLen, int measureUnit) {
//
//        /////
//        if ((tempCompensate < -20 || tempCompensate > 20) && measureUnit == 0) {
//            tempCompensate = 0;
//        }
//        /////
//
//        if (distance == -1) {
//            return convertTemperatureIfNeeded(mTemp + tempCompensate);
//        } else {
//            if (MainActivity.tempEnvironment > 45 || MainActivity.tempEnvironment < 30) { //26.1
//                return convertTemperatureIfNeeded(mTemp + tempCompensate);
//            } else {
//                float tempEnvironment = 0;
//
//                if (focalLen == 0) {
//                    if (MainActivity.tempEnvironment > 31.5) {
//                        tempEnvironment = (float) 31.5;
//                    } else if (MainActivity.tempEnvironment < 20.1) {
//                        tempEnvironment = (float) 20.1;
//                    } else {
//                        tempEnvironment = MainActivity.tempEnvironment;
//                    }
//                    if (distance >= 5) {
////                        if (tempEnvironment >= 20.1 && tempEnvironment < 23.35) {
////                            // 在环境温度20.1、21.1℃下训练
////                            float result = (float) (0.15877362428989 * mTemp + 0.03382572685611610 * mTemp * tempEnvironment + 0.000568762047998612 * Math.pow(mTemp, 2) + 0.0127603166252144 * distance * mTemp - 0.01599677278868330 * distance * tempEnvironment + 0.00562172812956645 * Math.pow(distance, 2) + 6.79402618258558 + tempCompensate);
////                            return convertTemperatureIfNeeded(result);
////                        } elss`2e if (tempEnvironment >= 23.35 && tempEnvironment < 28.8) {
////                            // 在环境温度26.1℃下训练
////                            float result = (float) (1.02787184840106 * mTemp - 0.00688796445211396 * mTemp * tempEnvironment + 0.000231538399388359 * Math.pow(mTemp, 2) + 0.0142449480097793 * distance * mTemp - 0.00419013348339784 * distance * tempEnvironment - 0.00273546184703448 * Math.pow(distance, 2) - 102.941493097920 + tempCompensate + 4.003125 * tempEnvironment + 0.0068115234375 * Math.pow(tempEnvironment, 2) - 0.083534458812165 * distance);
////                            return convertTemperatureIfNeeded(result);
////                            /////
////                        } else {
////                            // 在环境温度31.5℃下训练
////                            float result = (float) (0.334211331545139 * mTemp + 0.0108147621550984 * mTemp * tempEnvironment + 0.00224920165286945 * Math.pow(mTemp, 2) + 0.0142901880810952 * distance * mTemp - 0.0155825145903655 * distance * tempEnvironment - 0.00121501653620506 * Math.pow(distance, 2) + 8.59632351289234 + tempCompensate);
////                            return convertTemperatureIfNeeded(result);
////                        }
//                        if (tempEnvironment >= 20.1 && tempEnvironment < 28.8) {
//                            // 在环境温度20.1℃下训练
//                            float result = (float) (0.15877362428989 * mTemp + 0.03382572685611610 * mTemp * tempEnvironment + 0.000568762047998612 * Math.pow(mTemp, 2) + 0.0127603166252144 * distance * mTemp - 0.01599677278868330 * distance * tempEnvironment + 0.00562172812956645 * Math.pow(distance, 2) + 6.79402618258558 + tempCompensate);
//                            return convertTemperatureIfNeeded(result);
//                            /////
//                        } else {
//                            // 在环境温度31.5℃下训练
//                            float result = (float) (0.334211331545139 * mTemp + 0.0108147621550984 * mTemp * tempEnvironment + 0.00224920165286945 * Math.pow(mTemp, 2) + 0.0142901880810952 * distance * mTemp - 0.0155825145903655 * distance * tempEnvironment - 0.00121501653620506 * Math.pow(distance, 2) + 8.59632351289234 + tempCompensate);
//                            return convertTemperatureIfNeeded(result);
//                        }
//                    } else {
//                        if (tempEnvironment >= 20.1 && tempEnvironment < 23.35) {
//                            // 在环境温度20.1、21.1℃下训练
//                            float result = (float) (0.943895303715799 * mTemp + 0.0082590990834640 * mTemp * tempEnvironment - 0.001350543559191040 * Math.pow(mTemp, 2) - 0.0300506439658301 * distance + 0.00715246290155195 * distance * mTemp + 0.0579059394081716 * distance * tempEnvironment - 0.13343000379295 * Math.pow(distance, 2) - 3.81415641374014 + tempCompensate);
//                            return convertTemperatureIfNeeded(result);
//                        } else if (tempEnvironment >= 23.35 && tempEnvironment < 28.8) {
//                            // 在环境温度26.1℃下训练
//                            float result = (float) (0.903245665912011 * mTemp - 0.0018442323841424 * mTemp * tempEnvironment - 0.000029021659531106 * Math.pow(mTemp, 2) - 0.0771742905574536 * distance + 0.02246603139087500 * distance * mTemp + 0.0258679790589789 * distance * tempEnvironment - 0.16896491183911 * Math.pow(distance, 2) + 4.65722963123913 + tempCompensate);
//                            return convertTemperatureIfNeeded(result);
//                            /////
//                        } else {
//                            // 在环境温度31.5℃下训练
//                            float result = (float) (0.593038727163982 * mTemp + 0.00414513557364064 * mTemp * tempEnvironment + 0.0014301096361578 * Math.pow(mTemp, 2) + 0.0264308271192987 * distance * mTemp - 0.0286257918198913 * distance * tempEnvironment + 7.26162147719285 + tempCompensate);
//                            return convertTemperatureIfNeeded(result);
//                        }
//                    }
//                } else if (focalLen == 1) {
//                    if (MainActivity.tempEnvironment > 27.9) {
//                        tempEnvironment = (float) 27.9;
//                    } else if (MainActivity.tempEnvironment < 20.8) {
//                        tempEnvironment = (float) 20.8;
//                    } else {
//                        tempEnvironment = MainActivity.tempEnvironment;
//                    }
//                    if (tempEnvironment >= 20.8 && tempEnvironment < 27.8) {
//                        // 在环境温度20.8、20.9℃下训练
//                        float result = (float) (0.616930190010917 * mTemp + 0.0187548514751856 * mTemp * tempEnvironment - 0.00071764380065283 * Math.pow(mTemp, 2) + 0.00933076609522052 * distance * mTemp - 0.00587884923560325 * distance * tempEnvironment + 0.00117773735567691 * Math.pow(distance, 2) + 2.61896041760863 + tempCompensate);
//                        return convertTemperatureIfNeeded(result);
//                    } else {
//                        // 在环境温度27.8、27.9℃下训练
//                        float result = (float) (0.857530105766053 * mTemp + 0.0052958037118762 * mTemp * tempEnvironment - 0.00052997563641385 * Math.pow(mTemp, 2) + 0.01155873356776480 * distance * mTemp - 0.01081127023102190 * distance * tempEnvironment + 0.00163268159792318 * Math.pow(distance, 2) + 42.1865244903270 + tempCompensate - 0.0535164916674412 * Math.pow(tempEnvironment, 2));
//                        return convertTemperatureIfNeeded(result);
//                    }
//                } else {
//                    return convertTemperatureIfNeeded(mTemp);
//                }
//            }
//        }
//    }


    private float calibration(float mTemp, float distance, float tempCompensate, float focalLen, int measureUnit) {

        if ((tempCompensate < -20 || tempCompensate > 20) && measureUnit == 0) {
            tempCompensate = 0;
        }


        // if (distance == -1) {
        if (distance <= 5) {
            return convertTemperatureIfNeeded(mTemp + tempCompensate);
        } else {
            if (MainActivity.tempEnvironment > 45 || MainActivity.tempEnvironment < 0) {
                return convertTemperatureIfNeeded(mTemp + tempCompensate);
            } else {
                float tempEnvironment = 0;
                /////
                if ((tempCompensate < -20 || tempCompensate > 20) && measureUnit == 0) {
                    tempCompensate = 0;
                }
                /////
                if (focalLen == 0) {
                    if (MainActivity.tempEnvironment > 31.5) {
                        tempEnvironment = (float) 31.5;
                    } else if (MainActivity.tempEnvironment < 20.1) {
                        tempEnvironment = (float) 20.1;
                    } else {
                        tempEnvironment = MainActivity.tempEnvironment;
                    }
                    if (distance >= 5) {
                        if (tempEnvironment >= 20.1 && tempEnvironment < 23.35) {
                            // 在环境温度20.1、21.1℃下训练
                            float result = (float) (0.15877362428989 * mTemp + 0.03382572685611610 * mTemp * tempEnvironment + 0.000568762047998612 * Math.pow(mTemp, 2) + 0.0127603166252144 * distance * mTemp - 0.01599677278868330 * distance * tempEnvironment + 0.00562172812956645 * Math.pow(distance, 2) + 6.79402618258558 + tempCompensate);
                            return convertTemperatureIfNeeded(result);
                        } else if (tempEnvironment >= 23.35 && tempEnvironment < 26.1) { // 28.8
                            // 在环境温度26.1℃下训练
                            float result = (float) (1.02787184840106 * mTemp - 0.00688796445211396 * mTemp * tempEnvironment + 0.000231538399388359 * Math.pow(mTemp, 2) + 0.0142449480097793 * distance * mTemp - 0.00419013348339784 * distance * tempEnvironment - 0.00273546184703448 * Math.pow(distance, 2) - 102.941493097920 + tempCompensate + 4.003125 * tempEnvironment + 0.0068115234375 * Math.pow(tempEnvironment, 2) - 0.083534458812165 * distance);
                            return convertTemperatureIfNeeded(result);
                            /////
                        } else {
                            // 在环境温度31.5℃下训练
                            float result = (float) (0.334211331545139 * mTemp + 0.0108147621550984 * mTemp * tempEnvironment + 0.00224920165286945 * Math.pow(mTemp, 2) + 0.0142901880810952 * distance * mTemp - 0.0155825145903655 * distance * tempEnvironment - 0.00121501653620506 * Math.pow(distance, 2) + 8.59632351289234 + tempCompensate);
                            return convertTemperatureIfNeeded(result);
                        }
                    } else {
                        if (tempEnvironment >= 20.1 && tempEnvironment < 23.35) {
                            // 在环境温度20.1、21.1℃下训练
                            float result = (float) (0.943895303715799 * mTemp + 0.0082590990834640 * mTemp * tempEnvironment - 0.001350543559191040 * Math.pow(mTemp, 2) - 0.0300506439658301 * distance + 0.00715246290155195 * distance * mTemp + 0.0579059394081716 * distance * tempEnvironment - 0.13343000379295 * Math.pow(distance, 2) - 3.81415641374014 + tempCompensate);
                            return convertTemperatureIfNeeded(result);
                        } else if (tempEnvironment >= 23.35 && tempEnvironment < 26.1) {   // 28.8
                            // 在环境温度26.1℃下训练
                            float result = (float) (0.903245665912011 * mTemp - 0.0018442323841424 * mTemp * tempEnvironment - 0.000029021659531106 * Math.pow(mTemp, 2) - 0.0771742905574536 * distance + 0.02246603139087500 * distance * mTemp + 0.0258679790589789 * distance * tempEnvironment - 0.16896491183911 * Math.pow(distance, 2) + 4.65722963123913 + tempCompensate);
                            return convertTemperatureIfNeeded(result);
                            /////
                        } else {
                            // 在环境温度31.5℃下训练
                            float result = (float) (0.593038727163982 * mTemp + 0.00414513557364064 * mTemp * tempEnvironment + 0.0014301096361578 * Math.pow(mTemp, 2) + 0.0264308271192987 * distance * mTemp - 0.0286257918198913 * distance * tempEnvironment + 7.26162147719285 + tempCompensate);
                            return convertTemperatureIfNeeded(result);
                        }
                    }
                } else if (focalLen == 1) {
                    if (MainActivity.tempEnvironment > 27.9) {
                        tempEnvironment = (float) 27.9;
                    } else if (MainActivity.tempEnvironment < 20.8) {
                        tempEnvironment = (float) 20.8;
                    } else {
                        tempEnvironment = MainActivity.tempEnvironment;
                    }
                    if (tempEnvironment >= 20.8 && tempEnvironment < 27.8) {
                        // 在环境温度20.8、20.9℃下训练
                        float result = (float) (0.616930190010917 * mTemp + 0.0187548514751856 * mTemp * tempEnvironment - 0.00071764380065283 * Math.pow(mTemp, 2) + 0.00933076609522052 * distance * mTemp - 0.00587884923560325 * distance * tempEnvironment + 0.00117773735567691 * Math.pow(distance, 2) + 2.61896041760863 + tempCompensate);
                        return convertTemperatureIfNeeded(result);
                    } else {
                        // 在环境温度27.8、27.9℃下训练
                        float result = (float) (0.857530105766053 * mTemp + 0.0052958037118762 * mTemp * tempEnvironment - 0.00052997563641385 * Math.pow(mTemp, 2) + 0.01155873356776480 * distance * mTemp - 0.01081127023102190 * distance * tempEnvironment + 0.00163268159792318 * Math.pow(distance, 2) + 42.1865244903270 + tempCompensate - 0.0535164916674412 * Math.pow(tempEnvironment, 2));
                        return convertTemperatureIfNeeded(result);
                    }
                } else {
                    return convertTemperatureIfNeeded(mTemp);
                }
            }
        }
    }





    // 辅助方法：根据measureUnit判断是否需要温度转换
    private float convertTemperatureIfNeeded(float temperature) {

        if (IRRegionTemp.unitTemperature == 1) {
            // 需要转换为华氏度
            return (float) celsiusToFahrenheit(temperature);
        } else {
            // 保持摄氏度
            return temperature;
        }
    }


    public TemperatureSampleResult getGlobalTemp (float distance, float tempCompensate, byte focalLen, byte measureUnit) { /////
        try {
            synchronized (temperature) {
                //result.avgTemperature = getAverageValue(temperature);
                // 使用全局测温距离校正全局最高温与最低温
                result.maxTemperature = calibration(temperature[3], distance, tempCompensate, focalLen, measureUnit); /////
                result.minTemperature = calibration(temperature[6], distance, tempCompensate, focalLen, measureUnit); /////
                result.maxTemperaturePixel.set((int) temperature[1], (int) temperature[2]);
                result.minTemperaturePixel.set((int) temperature[4], (int) temperature[5]);
            }
//            if (unitTemperature != 0) {
//                result.avgTemperature = result.avgTemperature * 1.8f + 32;
//                result.maxTemperature = result.maxTemperature * 1.8f + 32;
//                result.minTemperature = result.minTemperature * 1.8f + 32;
//            }
        } catch (Exception e) {
            //Log.i(Log.TAG, "高德红外读取温度异常：" + e);
        }
        return result;
    }

    private void drawPoint(Canvas canvas, int x, int y, Bitmap point, float sizeX, float sizeY, byte imageStitch, int x_offset) {
        x = (x >= 2) ? (x - 2) : x;
        y = (y >= 2) ? (y - 2) : y;
        Rect hiDest;
        if (imageStitch == 0) {
            hiDest = new Rect((int) ((x - 2.5) * sizeX / width), (int) ((y - 2.5) * sizeY / height), (int) ((x + 7.5) * sizeX / width), (int) ((y + 7.5) * sizeY / height));
        } else {
            hiDest = new Rect((int) ((x - 2.5) * sizeX / (width * 3) + x_offset), (int) ((y - 2.5) * sizeY / height), (int) ((x + 7.5) * sizeX / (width * 3) + x_offset), (int) ((y + 7.5) * sizeY / height));
        }
        canvas.drawBitmap(point, null, hiDest, null);
    }

    private void drawHighLowTemp(Canvas canvas, IRRegionTemp.TemperatureSampleResult tempResult, int x_offset, byte imageStitch, float sizeX, float sizeY) {
        drawPoint(canvas, tempResult.maxTemperaturePixel.x, tempResult.maxTemperaturePixel.y,
                BitmapFactory.decodeResource(context.getResources(), R.drawable.cursorgreen), sizeX, sizeY, imageStitch, x_offset);
    }


    private void drawReginIndex(Canvas canvas, int x_offset, byte imageStitch, int regionId, float sizeX, float sizeY) {
        Paint paint = new Paint();
        paint.setColor(Color.BLUE);
        paint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));
        paint.setAntiAlias(true);

        if (imageStitch == 0) {
            if (index == 0) {
                canvas.drawText("环境温度区域", (indexX - 47) * sizeX / width, (indexY - 5) * sizeY / height, paint);
            } else {
                canvas.drawText(String.valueOf(index), indexX * sizeX / width, indexY * sizeY / height, paint);
            }
        } else {
            if (index == 0) {
                canvas.drawText("环境温度区域", (indexX - 47) * sizeX / (width * 3) + x_offset, (indexY - 5) * sizeY / height, paint);
            } else {
                canvas.drawText(String.valueOf(regionId), indexX * sizeX / (width * 3) + x_offset, indexY * sizeY / height, paint);
            }
        }
    }


    public void drawRegionTemp(Bitmap bitmap, int preset, byte imageStitch, int regionId) {
        Canvas canvas = new Canvas(bitmap);
        int width_preset = bitmap.getWidth() / 3;
        int x_offset = (preset == 1) ? width_preset : (preset == 3) ? (2 * width_preset) : 0;
        if (imageStitch == 0) {
            linePaint.setStrokeWidth(1 * (float) bitmap.getWidth() / 640);
            linePaint.setTextSize(16 * Math.min((float) bitmap.getWidth() / 640, (float) bitmap.getHeight() / 512));
        } else {
            linePaint.setStrokeWidth(1 * (float) bitmap.getWidth() / (640 * 3));
            linePaint.setTextSize(16 * Math.min((float) bitmap.getWidth() / (640 * 3), (float) bitmap.getHeight() / 512));
        }
        //Log.i(Log.TAG, "高德红外绘制多边形：" + points.size());
        switch (points.size()) {
            case 0:
                return;
            case 1:
                int x = points.get(0).x;
                int y = points.get(0).y;
                //canvas.drawPoint(x, y, linePaint);

                drawPoint(canvas, result.maxTemperaturePixel.x, result.maxTemperaturePixel.y,
                        BitmapFactory.decodeResource(context.getResources(), R.drawable.cursorgreen), bitmap.getWidth(), bitmap.getHeight(), imageStitch, x_offset);

                // 绘制点温度
                /*String degree = String.format("%.1f%s", result.maxTemperature, unitTemperature == 0 ? "°C" : "°F");
                canvas.drawText(degree, x + 5, y, tempPaint);*/

                drawHighLowTemp(canvas, result, x_offset, imageStitch, bitmap.getWidth(), bitmap.getHeight());
                break;
            case 2:
                Point start = points.get(0);
                Point end = points.get(1);
                if (imageStitch == 0) {
                    canvas.drawLine(start.x * (float) bitmap.getWidth() / width, start.y * (float) bitmap.getHeight() / height,
                            end.x * (float) bitmap.getWidth() / width, end.y * (float) bitmap.getHeight() / height, linePaint);
                } else {
                    canvas.drawLine(start.x * (float) bitmap.getWidth() / (width * 3) + x_offset, start.y * (float) bitmap.getHeight() / height,
                            end.x * (float) bitmap.getWidth() / (width * 3) + x_offset, end.y * (float) bitmap.getHeight() / height, linePaint);
                }

                drawHighLowTemp(canvas, result, x_offset, imageStitch, bitmap.getWidth(), bitmap.getHeight());
                break;
            default:
                float[] pointArray = new float[points.size() * 4];

                if (imageStitch == 0) {
                    for (int i = 0; i < points.size(); i++) {
                        pointArray[i * 4] = points.get(i).x * (float) bitmap.getWidth() / width;
                        pointArray[i * 4 + 1] = points.get(i).y * (float) bitmap.getHeight() / height;
                        if (i + 1 < points.size()) {
                            pointArray[i * 4 + 2] = points.get(i + 1).x * (float) bitmap.getWidth() / width;
                            pointArray[i * 4 + 3] = points.get(i + 1).y * (float) bitmap.getHeight() / height;
                        } else {
                            pointArray[i * 4 + 2] = points.get(0).x * (float) bitmap.getWidth() / width;
                            pointArray[i * 4 + 3] = points.get(0).y * (float) bitmap.getHeight() / height;
                        }
                    }
                } else {
                    for (int i = 0; i < points.size(); i++) {
                        pointArray[i * 4] = points.get(i).x * (float) bitmap.getWidth() / (width * 3) + x_offset;
                        pointArray[i * 4 + 1] = points.get(i).y * (float) bitmap.getHeight() / height;
                        if (i + 1 < points.size()) {
                            pointArray[i * 4 + 2] = points.get(i + 1).x * (float) bitmap.getWidth() / (width * 3) + x_offset;
                            pointArray[i * 4 + 3] = points.get(i + 1).y * (float) bitmap.getHeight() / height;
                        } else {
                            pointArray[i * 4 + 2] = points.get(0).x * (float) bitmap.getWidth() / (width * 3) + x_offset;
                            pointArray[i * 4 + 3] = points.get(0).y * (float) bitmap.getHeight() / height;
                        }
                    }
                }

                canvas.drawLines(pointArray, linePaint);

                drawHighLowTemp(canvas, result, x_offset, imageStitch, bitmap.getWidth(), bitmap.getHeight());
                break;
        }
        drawReginIndex(canvas, x_offset, imageStitch, regionId, bitmap.getWidth(), bitmap.getHeight());
    }



    public TemperatureSampleResult getTemperature(float distance, float tempCompensate, byte focalLen, byte measureUnit) { /////
        if (System.currentTimeMillis() - lastUpdateTime < PERIOD_SECOND) {
            return result;
        }

        lastUpdateTime = System.currentTimeMillis();

        switch (points.size()) {
            case 0:
                return getGlobalTemp(distance, tempCompensate, focalLen, measureUnit); /////
            case 1:
                return getPointTemp(distance, tempCompensate, focalLen, measureUnit); /////
            case 2:
                return getLineTemp(distance, tempCompensate, focalLen, measureUnit); /////
            default:
                return getShapeTemp(distance, tempCompensate, focalLen, measureUnit); /////
        }
    }

    private TemperatureSampleResult getPointTemp(float distance, float tempCompensate, byte focalLen, byte measureUnit) {
        try {
            int idx = points.get(0).y * width + points.get(0).x + 10;
            synchronized (temperature) {
                // 使用区域测温距离校正点温度
                result.maxTemperature = result.minTemperature = result.avgTemperature
                        = calibration(temperature[idx], distance, tempCompensate, focalLen, measureUnit);
            }

            result.minTemperaturePixel = result.maxTemperaturePixel = points.get(0);

        } catch (Exception e) {
            Log.i(Log.TAG, "高德红外读取点温度异常：" + e);
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

    private TemperatureSampleResult getLineTemp(float distance, float tempCompensate, byte focalLen, byte measureUnit) { /////
        try {
            TreeMap<Float, Point> maps = new TreeMap<>();
            for (Point point : linePixels) {
                float t = temperature[point.y * width + point.x + 10];
                maps.put(t, point);
            }
            result.minTemperature = calibration(maps.firstKey(), distance, tempCompensate, focalLen, measureUnit); /////
            result.minTemperaturePixel = maps.firstEntry().getValue();
            result.maxTemperature = calibration(maps.lastKey(), distance, tempCompensate, focalLen, measureUnit); /////
            result.maxTemperaturePixel = maps.lastEntry().getValue();

            //Log.i(Log.TAG, "高德红外读取线温：" + linePixels);
            //Log.i(Log.TAG, "高德红外读取线温：" + result.toString());

        } catch (Exception e) {
            Log.i(Log.TAG, "高德红外读取线温度异常：" + e);
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

    private TemperatureSampleResult getShapeTemp(float distance, float tempCompensate, byte focalLen, byte measureUnit) { /////
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

            result.minTemperature = calibration(maps.firstKey(), distance, tempCompensate, focalLen, measureUnit); /////
            result.minTemperaturePixel = maps.firstEntry().getValue();
            result.maxTemperature = calibration(maps.lastKey(), distance, tempCompensate, focalLen, measureUnit); /////
            result.maxTemperaturePixel = maps.lastEntry().getValue();
        } catch (Exception e) {
            Log.i(Log.TAG, "高德红外读取多边形温度异常：" + e);
        }
        return result;
    }

    // 绘制全局高低温
    public void drawTemperature(Canvas canvas, float t, float x, float y, float sizeX, float sizeY) {

        String unit = (unitTemperature == 0) ? "°C" : "°F";

        String text = (Math.abs(t) < 100) ? String.format("%.1f%s", t, unit) : String.format("%d%s", Math.round(t), unit);

        tempPaint.setStrokeWidth(1 * sizeX / 640);
        tempPaint.setTextSize(16 * Math.min(sizeX / 640, sizeY / 512));
        canvas.drawText(text, x, y, tempPaint);
    }
}
