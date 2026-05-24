/*
 * Copyright (c) 2021. Kingron<Kingron@163.om>
 * You must get a license for commercial purpose.
 * 商业使用，必须获取授权。
 */

package hikvision.zhanyun.com.hikvision.detect;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.SystemClock;

import com.tencent.yolov5ncnn.YoloV5Ncnn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import hikvision.zhanyun.com.hikvision.Settings;
import hikvision.zhanyun.com.hikvision.utils.Log;

public class NcnnYoloV5Detector implements Classifier {

    private static YoloV5Ncnn yoloV5Ncnn;
    private float confidence = 0.6f; /////

    private static void loadModelFromAsset(String[] filenames) {
        for (String filename : filenames) {
            File localFile = new File(filename);
            byte[] buffer = new byte[1024 * 100];
            try (InputStream rawStream = NcnnYoloV5Detector.class.getResourceAsStream(
                    "/assets/" + localFile.getName())) {
                if (rawStream == null) continue;

                if (!localFile.exists() || localFile.length() != rawStream.available()) {
                    Log.i(Log.TAG, "加载新文件：" + localFile);
                    localFile.delete();
                    try (OutputStream os = new FileOutputStream(localFile)) {
                        int len;
                        while ((len = rawStream.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    } catch (Exception exp) {
                        Log.i(Log.TAG, "写Yolov5模型文件异常：" + exp);
                    }
                }
            } catch (Exception exp1) {
                Log.i(Log.TAG, "读Yolov5模型文件异常：" + exp1);
            }
        }
    }

    public NcnnYoloV5Detector(String param, String bin) {
        loadModelFromAsset(new String[]{param, bin});

        boolean modelExist = new File(param).exists() && new File(bin).exists();
        if (modelExist && yoloV5Ncnn == null) {
            yoloV5Ncnn = new YoloV5Ncnn();
            boolean b = yoloV5Ncnn.Init2(param, bin);
            Log.d(Log.TAG, "初始化Yolov5: " + b);
        } else {
            Log.e(Log.TAG, "初始化Yolov5失败: YoloV5模型不存在");
        }
    }

    @Override
    public void close() {

    }

    @Override
    public List<Recognition> recognizeImage(String file) {
        Bitmap bmp = BitmapFactory.decodeFile(file);
        if (bmp == null) return null;

        List<Recognition> ret = recognizeImage(bmp, null);  // 传输图片默认检测所有类
        bmp.recycle();
        return ret;
    }

    public static final HashMap<String, Integer> mapName = new HashMap(){
        {put("fire", 40);};
        {put("Smoke", 41);};
        {put("YiWu", 33);};
        {put("DiaoChe", 1);};
        {put("WaJi", 5);};
        {put("TaDiao", 2);};
        {put("BengChe", 4);};
        {put("DaHuoChe", 7);};
    };

    public static final HashMap<String, String> labelToChinese = new HashMap<String, String>() {{
        put("fire", "火焰");
        put("Smoke", "烟雾");
        put("YiWu", "异物");
        put("DiaoChe", "吊车");
        put("WaJi", "挖掘机");
        put("TaDiao", "塔吊");
        put("BengChe", "水泥泵车");
        put("DaHuoChe", "卡车");
    }};


    // 根据 alertType 找 alertThreshold
    private Settings.AIAlertType findAlertType(int alertType, Settings.AIAlertType[] alertTypes) {
        if (alertTypes == null) return null;
        for (Settings.AIAlertType type : alertTypes) {
            if (type != null && type.alertType == alertType) {
                return type;
            }
        }
        return null;
    }
    /**
     * 把本地对象识别ID转换为服务器定义的告警ID，参考南网规约 7.58.1 章节定义
     *
     * @param id
     * @return
     */
    private int getAlertID(int id) {
        if (id == 0)
            return 5;
        else if (id == 1)
            return 2;
        else if (id == 2)
            return 1;
        else
            return id;
    }
    @Override
    public List<Recognition> recognizeImage(Bitmap bitmap, Settings.AIAlertType[] alertTypes) {
        if (yoloV5Ncnn == null) return null;

        long t = SystemClock.currentThreadTimeMillis();
        YoloV5Ncnn.Obj[] result = yoloV5Ncnn.Detect(bitmap, false);
        Log.d(Log.TAG, "AI检测耗时: " + (SystemClock.currentThreadTimeMillis() - t));
        if (result == null) return null;
        List<Recognition> ret = new ArrayList<>();
        boolean b;
        for (int i = 0; i < result.length; i++) {
            // 从label映射到alertType
            Integer alertType = mapName.get(result[i].label);
            if (alertType == null) {
                continue;
            }
            // 检查alertTypes里面是否包含这个alertType，并且达到对应的alertThreshold
            Settings.AIAlertType targetAlertType = findAlertType(getAlertID(alertType), alertTypes);
            if (targetAlertType == null) {
                continue;
            }
            //b = result[i].prob >= confidence;
            b = result[i].prob >= targetAlertType.alertThreshold / 100f;

            Log.d(Log.TAG, String.format("检测到对象: %s(%3.1f) - %s", result[i].label, result[i].prob, b ? "添加" : "忽略"));


            if (b) {

                Recognition recognition = new Recognition(alertType, String.valueOf(i), labelToChinese.getOrDefault(result[i].label, result[i].label), result[i].prob,
                        new RectF(result[i].x, result[i].y, result[i].x + result[i].w, result[i].y + result[i].h));

                ret.add(recognition);
            }
        }
        return ret;
    }

    @Override
    public void setConfidence(float value) {
        this.confidence = value;
    }


}
