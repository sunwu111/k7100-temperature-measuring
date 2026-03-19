package hikvision.zhanyun.com.hikvision.detect;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

import hikvision.zhanyun.com.hikvision.utils.ByteUtils;
import hikvision.zhanyun.com.hikvision.utils.Log;

// for nvidia darknet
// the setting of object_detect is 3
public class NvidiaTcpDetect {
    //private static final int HW_INPUT_WIDTH_SIZE = 640;
    //private static final int HW_INPUT_HEIGHT_SIZE = 480;
    private static Socket mSocket;
    private static String mIpAddress = "192.168.1.122";
    private static int mClientPort = 8088;
    private static OutputStream mOutStream;
    private static InputStream mInStream;
    private static SocketReceiveThread mReceiveThread;
    private static boolean computingDetection = false;
    private static boolean haveObject = false;
    private static List<Classifier.Recognition> detectResult = new LinkedList<Classifier.Recognition>();
    private static HandlerThread threadObjectDetect;
    private static Handler handlerObjectDetect;
    private static String alertMsg;
    private static String detectIp;

    public NvidiaTcpDetect(String server) {
        this.detectIp = server;
    }

    public static int sendBitmapInit() {
        threadObjectDetect = new HandlerThread("Nvidia Detect");
        threadObjectDetect.start();
        handlerObjectDetect = new Handler(threadObjectDetect.getLooper());
        new SocketConnectThread().start();
        return 0;
    }

    private static int sendDetectBitmap(Bitmap bitmap) {
        mReceiveThread = new SocketReceiveThread();
        mReceiveThread.start();
        try {
            if (bitmap != null) {
                //读取图片进行压缩
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos); //0-100 100为不压缩
                InputStream is = new ByteArrayInputStream(baos.toByteArray());

                byte[] bytes = new byte[1024 * 1024];
                int len = 0;
                while ((len = is.read(bytes)) != -1) {
                    writeData(bytes, len);
                }
            } else {
                Log.i(Log.TAG, "[sendDetectBitmap] fail:bitmap is null");
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            closeConnection();
            new SocketConnectThread().start(); // 重连
            Log.i(Log.TAG, "[sendDetectBitmap] error1:" + e.getStackTrace());
        } catch (IOException e) {
            e.printStackTrace();
            closeConnection();
            new SocketConnectThread().start(); // 重连
            Log.i(Log.TAG, "[sendDetectBitmap] error2:" + e.getStackTrace());
        }
        return 0;
    }

    static class SocketConnectThread extends Thread {
        public void run() {
            try {
                if (detectIp != "" && detectIp != null)
                    mIpAddress = detectIp;
                //指定ip地址和端口号
                mSocket = new Socket(mIpAddress, mClientPort);
                if (mSocket != null) {
                    //获取输出流、输入流
                    mOutStream = mSocket.getOutputStream();
                    mInStream = mSocket.getInputStream();
                }
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            Log.i(Log.TAG, "connect success");
        }
    }

    static class SocketReceiveThread extends Thread {
        public void run() {
            try {
                alertMsg = "";
                if (mSocket != null) {
                    byte[] buffer = new byte[1024];
                    byte[] temp;
                    //循环执行read，用来接收数据。
                    //数据存在buffer中，count为读取到的数据长度。
                    int count = mInStream.read(buffer);
                    temp = new byte[count];
                    System.arraycopy(buffer, 0, temp, 0, count);
                    alertMsg = new String(temp, "UTF-8");
                    //Log.i(Log.TAG,"read tcp data alertMsg:"+ alertMsg);
                }
            } catch (Exception e) {
                return;
            }
        }
    }

    private static void writeData(byte[] data, int len) {
        if (data.length == 0 || len == 0 || mOutStream == null) {
            closeConnection();
            new SocketConnectThread().start(); // 重连
            Log.i(Log.TAG, "[writeData] error1:");
            return;
        }
        try {   //发送
            //Log.i(Log.TAG,"writeData len:"+ len);
            mOutStream.write(ByteUtils.intToByteArray(len), 0, 4);
            mOutStream.write(data, 0, len);
            mOutStream.flush();
        } catch (Exception e) {
            closeConnection();
            new SocketConnectThread().start(); // 重连
            Log.i(Log.TAG, "[writeData] error2:" + e.getStackTrace());
        }
    }

    private static void closeConnection() {
        try {
            if (mOutStream != null) {
                mOutStream.close(); //关闭输出流
                mOutStream = null;
            }
            if (mInStream != null) {
                mInStream.close(); //关闭输入流
                mInStream = null;
            }
            if (mSocket != null) {
                mSocket.close();  //关闭socket
                mSocket = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mReceiveThread != null) {
            mReceiveThread = null;
        }
    }

    // 通过检测到的classid得到物体名称
    private static String getClassNameById(int classId) {
        String name = "";
        if (classId == 1)
            name = "digger";
        else if (classId == 2)
            name = "crane";
        else if (classId == 3)
            name = "hoist";
        return name;
    }

    public static boolean parseAlertMsg(String msg) {
        //Log.i(Log.TAG, "[parseAlertMsg] msg: " + msg);
        if (msg == null || msg == "")
            return false;
        detectResult.clear();
        try {
            JSONObject jsonObject = new JSONObject(msg);
            JSONArray jsonArray = jsonObject.optJSONArray("objects");
            if (jsonArray == null || jsonArray.length() == 0)
                return false;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObjectA = jsonArray.getJSONObject(i);
                final RectF location = new RectF();
                location.left = (float) jsonObjectA.optDouble("left");
                location.top = (float) jsonObjectA.optDouble("top");
                location.right = (float) jsonObjectA.optDouble("right");
                location.bottom = (float) jsonObjectA.optDouble("bottom");
                final Classifier.Recognition resultRec = new Classifier.Recognition(jsonObjectA.optInt("class"),
                        "", "", (float) jsonObjectA.optDouble("score"), location);
                //Log.i(Log.TAG, "[parseAlertMsg] class:" + jsonObjectA.optInt("class") + ",left:" +location.left + ",top:"+ location.top+ ",right:"+location.right + ",bottom:"+location.bottom);
                detectResult.add(resultRec);
            }
        } catch (JSONException e) {
            Log.i(Log.TAG, "[parseAlertMsg] error: " + e.getMessage());
            return false;
        }
        return true;
    }

    public static void drawObject(Bitmap bitmap) {
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Canvas canvas = new Canvas(bitmap);
            for (int i = 0; i < detectResult.size(); i++) {
                Classifier.Recognition recognition = detectResult.get(i);
                Paint paintFrame = new Paint();// 画框用
                Paint paintText = new Paint(); // 加文字用
                if (i == 0) {
                    paintFrame.setColor(Color.RED);
                    paintText.setColor(Color.RED);
                } else if (i == 1) {
                    paintFrame.setColor(Color.BLUE);
                    paintText.setColor(Color.BLUE);
                } else if (i == 2) {
                    paintFrame.setColor(Color.YELLOW);
                    paintText.setColor(Color.YELLOW);
                } else if (i == 3) {
                    paintFrame.setColor(Color.GREEN);
                    paintText.setColor(Color.GREEN);
                }
                paintFrame.setStyle(Paint.Style.STROKE);//不填充
                paintFrame.setStrokeWidth(6); //线的宽度
                canvas.drawRect(recognition.getLocation().left * width, recognition.getLocation().top * height,
                        recognition.getLocation().right * width, recognition.getLocation().bottom * height, paintFrame);
                // 水印的颜色
                paintText.setTypeface(Typeface.DEFAULT);
                // 水印的字体大小
                int w = bitmap.getWidth();
                float rate = (w * 1.0f) / 320;
                paintText.setTextSize(8 * rate);
                paintText.setAntiAlias(true);// 去锯齿
                DecimalFormat decimalFormat = new DecimalFormat(".0"); // 保留一位小数
                if (recognition.getLocation().top * height > 20)
                    canvas.drawText("" + getClassNameById(recognition.getClassID()) + "  " + decimalFormat.format((recognition.getConfidence() * 100)) + "%",
                            recognition.getLocation().left * width, recognition.getLocation().top * height - 8, paintText);
                else
                    canvas.drawText("" + getClassNameById(recognition.getClassID()) + "  " + decimalFormat.format((recognition.getConfidence() * 100)) + "%",
                            recognition.getLocation().left * width, recognition.getLocation().bottom * height + 30, paintText);
                //Log.i(Log.TAG, "[drawObject]left:" + recognition.getLocation().left * width + ",top:"+ recognition.getLocation().top *height
                //        + ",right:"+ recognition.getLocation().right* width + ",bottom:"+ recognition.getLocation().bottom * height);
            }
        } catch (Exception e) {
            Log.e(Log.TAG, "绘制对象出错: " + e.getMessage());
        }
    }

    public static void videoDetectRun(final Bitmap bitmap) {
        if (detectIp != "" && detectIp != null)
            mIpAddress = detectIp;
        if (bitmap == null)
            return;
        if (computingDetection) {
            if (haveObject == true)
                drawObject(bitmap);
            return;
        }
        final long startTime = SystemClock.uptimeMillis();
        //final Bitmap croppedBitmap = Bitmap.createScaledBitmap(bitmap, HW_INPUT_WIDTH_SIZE, HW_INPUT_HEIGHT_SIZE, false);
        final Bitmap croppedBitmap = bitmap;
        handlerObjectDetect.post(new Runnable() {
            @Override
            public void run() {
                computingDetection = true;
                sendDetectBitmap(croppedBitmap);
                SystemClock.sleep(1000);
                if (alertMsg != null && alertMsg != "")
                    haveObject = parseAlertMsg(alertMsg);
                else
                    haveObject = false;
                if (haveObject == true) {
                    Log.i(Log.TAG, "Nvidia检测耗时:" + (SystemClock.uptimeMillis() - startTime) + "ms,识别个数:" + detectResult.size() + ",识别结果: " + alertMsg);
                } else {
                    Log.i(Log.TAG, "Nvidia检测耗时:" + (SystemClock.uptimeMillis() - startTime) + "ms,识别结果: null");
                }
                if (croppedBitmap != null)
                    croppedBitmap.recycle();
                computingDetection = false;
            }
        });
    }
}
