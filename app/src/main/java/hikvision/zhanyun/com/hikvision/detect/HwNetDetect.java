package hikvision.zhanyun.com.hikvision.detect;

import android.content.Context;
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import hikvision.zhanyun.com.hikvision.utils.Log;

// for haiwei Detect
// the setting of object_detect is 2

public class HwNetDetect {
    public static final int HW_INPUT_WIDTH_SIZE = 800;
    public static final int HW_INPUT_HEIGHT_SIZE = 600;
    //private static String requestURL = "http://192.168.1.253/ai.php";
    private static String requestURL = "SPGPServer://192.168.200.253/ai.php";
    public static boolean haveObject = false;
    private static boolean computingDetection = false;
    private static HandlerThread threadObjectDetect;
    private static Handler handlerObjectDetect;
    public static List<Classifier.Recognition> detectResult = new LinkedList<Classifier.Recognition>();
    private static String detectIp;

    public static void videoDetectInit(Context context, String ip)
    {
        detectIp = ip;
        threadObjectDetect = new HandlerThread("Hw Detect");
        threadObjectDetect.start();
        handlerObjectDetect = new Handler(threadObjectDetect.getLooper());
        if (detectIp != "" && detectIp!= null)
            requestURL = "SPGPServer://" + detectIp + "/ai.php";
    }

    public static String uploadDetectBitmap(Bitmap bitmap)
    {
        if (detectIp != "" && detectIp!= null)
            requestURL = "SPGPServer://" + detectIp + "/ai.php";
        //Log.i(Log.TAG, "[uploadDetectBitmap] bitmap:" + bitmap + ",requestURL:"+ requestURL);
        String PREFIX = "--";
        String LINE_END = "\r\n";
        String BOUNDARY =  UUID.randomUUID().toString();  //随机生成边界
        try {
            URL url = new URL(requestURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(30 * 1000); //30秒连接超时
            connection.setReadTimeout(30 * 1000);   //30秒读取超时
            connection.setDoInput(true);  //允许文件输入流
            connection.setDoOutput(true); //允许文件输出流
            connection.setUseCaches(false);  //不允许使用缓存
            connection.setRequestMethod("POST");  //请求方式为POST
            connection.setRequestProperty("Charset", "utf-8");  //设置编码为utf-8
            connection.setRequestProperty("connection", "keep-alive"); //保持连接
            connection.setRequestProperty("Content-Type", "multipart/form-data" + ";boundary=" + BOUNDARY); //特别注意：Content-Type必须为multipart/form-data

            //如果传入的文件路径不为空的话，则读取文件并上传
            if(bitmap != null)
            {
                //读取图片进行压缩
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos); //0-100 100为不压缩
                InputStream is = new ByteArrayInputStream(baos.toByteArray());

                OutputStream outputSteam = connection.getOutputStream();
                DataOutputStream dos = new DataOutputStream(outputSteam);
                StringBuffer sb = new StringBuffer();
                sb.append(PREFIX);
                sb.append(BOUNDARY);
                sb.append(LINE_END);

                //特别注意
                //name是服务器端需要key;filename是文件的名字（包括后缀）
                sb.append("Content-Disposition: form-data; name=\"upload!\"; filename=\""+"bitmap"+"\""+LINE_END);
                sb.append("Content-Type: application/octet-stream; charset="+"CHARSET"+LINE_END);
                sb.append(LINE_END);
                dos.write(sb.toString().getBytes());

                byte[] bytes = new byte[1024];
                int len = 0;
                while((len=is.read(bytes))!=-1)
                {
                    dos.write(bytes, 0, len);
                }
                is.close();
                dos.write(LINE_END.getBytes());
                byte[] end_data = (PREFIX+BOUNDARY+PREFIX+LINE_END).getBytes();
                dos.write(end_data);
                dos.flush();

                //获取返回码，根据返回码做相应处理
                int res = connection.getResponseCode();
                if(res == 200)
                {
                    InputStream iStream = connection.getInputStream();
                    BufferedReader input = new BufferedReader(new InputStreamReader(iStream));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while((line = input.readLine()) != null)
                    {
                        result.append(line).append("\n");
                    }
                    iStream.close();
                    //Log.i(Log.TAG, "[uploadDetectBitmap] result:" + result.toString());
                    return result.toString();
                }
            } else {
                Log.i(Log.TAG, "[uploadDetectBitmap] fail:bitmap is null");
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            Log.i(Log.TAG, "[uploadDetectBitmap] error1:" + e.getStackTrace());
        } catch (IOException e) {
            e.printStackTrace();
            Log.i(Log.TAG, "[uploadDetectBitmap] error2:" + e.getStackTrace());
        }
        return null;
    }

    public static String uploadDetectFile(String fileName)
    {
        Log.i(Log.TAG, "[uploadDetectFile] fileName:" + fileName + ",requestURL:"+ requestURL);
        String TAG = "uploadFile";
        String PREFIX = "--";
        String LINE_END = "\r\n";
        String BOUNDARY =  UUID.randomUUID().toString();  //随机生成边界
        File file = new File(fileName);
        try {
            URL url = new URL(requestURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(30 * 1000); //30秒连接超时
            connection.setReadTimeout(30 * 1000);   //30秒读取超时
            connection.setDoInput(true);  //允许文件输入流
            connection.setDoOutput(true); //允许文件输出流
            connection.setUseCaches(false);  //不允许使用缓存
            connection.setRequestMethod("POST");  //请求方式为POST
            connection.setRequestProperty("Charset", "utf-8");  //设置编码为utf-8
            connection.setRequestProperty("connection", "keep-alive"); //保持连接
            connection.setRequestProperty("Content-Type", "multipart/form-data" + ";boundary=" + BOUNDARY); //特别注意：Content-Type必须为multipart/form-data

            //如果传入的文件路径不为空的话，则读取文件并上传
            if(file!=null)
            {
                InputStream is = new FileInputStream(file);
                OutputStream outputSteam = connection.getOutputStream();
                DataOutputStream dos = new DataOutputStream(outputSteam);
                StringBuffer sb = new StringBuffer();
                sb.append(PREFIX);
                sb.append(BOUNDARY);
                sb.append(LINE_END);

                //特别注意
                //name是服务器端需要key;filename是文件的名字（包括后缀）
                sb.append("Content-Disposition: form-data; name=\"upload!\"; filename=\""+file.getName()+"\""+LINE_END);
                sb.append("Content-Type: application/octet-stream; charset="+"CHARSET"+LINE_END);
                sb.append(LINE_END);
                dos.write(sb.toString().getBytes());

                byte[] bytes = new byte[1024];
                int len = 0;
                while((len=is.read(bytes))!=-1)
                {
                    dos.write(bytes, 0, len);
                }
                is.close();
                dos.write(LINE_END.getBytes());
                byte[] end_data = (PREFIX+BOUNDARY+PREFIX+LINE_END).getBytes();
                dos.write(end_data);
                dos.flush();

                //获取返回码，根据返回码做相应处理
                int res = connection.getResponseCode();
                Log.d(TAG, "response code:"+res);
                if(connection.getResponseCode() == 200)
                {
                    BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while((line = input.readLine()) != null)
                    {
                        result.append(line).append("\n");
                    }
                    Log.i(Log.TAG, "[uploadDetectFile] result:" + result.toString());
                    return result.toString();
                }
            } else {
                Log.i(Log.TAG, "[uploadDetectFile] fail:file is null");
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 通过华为ai检测到的classid得到物体名称
    // 华为共用模型使用的是Pascal voc的20类
    private static String getClassNameById(int classId)
    {
        String name = "";

        if (classId == 0)
            name = "aeroplane";
        else if (classId == 1)
            name = "bicycle";
        else if (classId == 2)
            name = "bird";
        else if (classId == 3)
            name = "boat";
        else if (classId == 4)
            name = "bottle";
        else if (classId == 5)
            name = "bus";
        else if (classId == 6)
            name = "car";
        else if (classId == 7)
            name = "cat";
        else if (classId == 8)
            name = "chair";
        else if (classId == 9)
            name = "cow";
        else if (classId == 10)
            name = "diningtable";
        else if (classId == 11)
            name = "dog";
        else if (classId == 12)
            name = "horse";
        else if (classId == 13)
            name = "motorbike";
        else if (classId == 14)
            name = "person";
        else if (classId == 15)
            name = "pottedplant";
        else if (classId == 16)
            name = "sheep";
        else if (classId == 17)
            name = "sofa";
        else if (classId == 18)
            name = "train";
        else if (classId == 19)
            name = "tvmonitor";
        return name;
    }

    public static void drawObject(Bitmap bitmap) {
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Canvas canvas = new Canvas(bitmap);
            for (int i = 0;i < detectResult.size();i++)
            {
                Classifier.Recognition recognition = detectResult.get(i);
                Paint paintFrame = new Paint();// 画框用
                Paint paintText = new Paint(); // 加文字用
                if(i == 0) {
                    paintFrame.setColor(Color.RED);
                    paintText.setColor(Color.RED);
                }else if(i == 1) {
                    paintFrame.setColor(Color.BLUE);
                    paintText.setColor(Color.BLUE);
                }else if(i == 2) {
                    paintFrame.setColor(Color.YELLOW);
                    paintText.setColor(Color.YELLOW);
                }else if(i == 3){
                    paintFrame.setColor(Color.GREEN);
                    paintText.setColor(Color.GREEN);
                }
                paintFrame.setStyle(Paint.Style.STROKE);//不填充
                paintFrame.setStrokeWidth(6); //线的宽度
                canvas.drawRect(recognition.getLocation().left * width,recognition.getLocation().top *height,
                        recognition.getLocation().right* width,recognition.getLocation().bottom * height,paintFrame);
                // 水印的颜色
                paintText.setTypeface(Typeface.DEFAULT);
                // 水印的字体大小
                int w = bitmap.getWidth();
                float rate = (w * 1.0f) / 320;
                paintText.setTextSize(8 * rate);
                paintText.setAntiAlias(true);// 去锯齿
                DecimalFormat decimalFormat = new DecimalFormat(".0"); // 保留一位小数
                if (recognition.getLocation().top *height > 20)
                    canvas.drawText("" + getClassNameById(recognition.getClassID()) +"  " + decimalFormat.format((recognition.getConfidence()*100)) + "%",
                        recognition.getLocation().left * width, recognition.getLocation().top *height-8, paintText);
                else
                    canvas.drawText("" + getClassNameById(recognition.getClassID()) +"  " +  decimalFormat.format((recognition.getConfidence()*100)) + "%",
                            recognition.getLocation().left * width, recognition.getLocation().bottom *height+30, paintText);
                //Log.i(Log.TAG, "[drawObject]left:" + recognition.getLocation().left * width + ",top:"+ recognition.getLocation().top *height
                //        + ",right:"+ recognition.getLocation().right* width + ",bottom:"+ recognition.getLocation().bottom * height);
            }
        } catch (Exception e) {
            Log.e(Log.TAG, "绘制对象出错: " + e.getMessage());
        }
    }

    public static boolean parseAlertMsg(String msg)
    {
        if (msg == null || msg == "")
            return false;
        detectResult.clear();
        try {
            JSONObject jsonObject = new JSONObject(msg);
            JSONArray jsonArray = jsonObject.optJSONArray("objects");
            if (jsonArray == null || jsonArray.length() == 0)
                return false;
            for (int i = 0; i < jsonArray.length();i++)
            {
                JSONObject jsonObjectA = jsonArray.getJSONObject(i);
                final RectF location = new RectF();
                location.left = (float)jsonObjectA.optDouble("left");
                location.top = (float)jsonObjectA.optDouble("top");
                location.right = (float)jsonObjectA.optDouble("right");
                location.bottom = (float)jsonObjectA.optDouble("bottom");
                final Classifier.Recognition resultRec = new Classifier.Recognition(jsonObjectA.optInt("class"),
                        "","",(float)jsonObjectA.optDouble("score"),location);
                //Log.i(Log.TAG, "[parseAlertMsg] class:" + jsonObjectA.optInt("class") + ",left:" +location.left + ",top:"+ location.top+ ",right:"+location.right + ",bottom:"+location.bottom);
                detectResult.add(resultRec);
            }
        } catch (JSONException e) {
            Log.i(Log.TAG, "[parseAlertMsg] error: " + e.getMessage());
            return false;
        }
        return true;
    }

    public static void videoDetectRun(final Bitmap bitmap)
    {
        if (detectIp != "" && detectIp!= null)
            requestURL = "SPGPServer://" + detectIp + "/ai.php";
        if (bitmap == null)
            return;
        if (computingDetection) {
            if (haveObject == true)
                drawObject(bitmap);
            return;
        }
        final long startTime = SystemClock.uptimeMillis();
        final Bitmap croppedBitmap = Bitmap.createScaledBitmap(bitmap, HW_INPUT_WIDTH_SIZE, HW_INPUT_HEIGHT_SIZE, false);
        handlerObjectDetect.post(new Runnable() {
            @Override
            public void run() {
                computingDetection = true;
                String alertMsg = uploadDetectBitmap(croppedBitmap);
                haveObject = parseAlertMsg(alertMsg);
                if (haveObject == true) {
                    Log.i(Log.TAG, "华为检测耗时:"+(SystemClock.uptimeMillis() - startTime) + "ms,识别个数:"+detectResult.size()+",识别结果: " + alertMsg);
                }else{
                    Log.i(Log.TAG, "华为检测耗时:"+(SystemClock.uptimeMillis() - startTime) + "ms,识别结果: null");
                }
                if (croppedBitmap != null)
                    croppedBitmap.recycle();
                computingDetection = false;
            }
        });
    }
}
