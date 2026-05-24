/*
 * Copyright 2013-2015 duolabao.com All right reserved. This software is the
 * confidential and proprietary information of duolabao.com ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with duolabao.com.
 */

package hikvision.zhanyun.com.hikvision.mediacodec;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.YuvImage;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 类Util的实现描述：
 *
 * @author HELONG 2016/3/8 17:42
 */
public class Util {

    /**
     * 视频逆时针旋转90
     */
    public static void YUV420spRotateNegative90(byte[] dst, byte[] src, int srcWidth,
                                                int height) {
        int nWidth = 0, nHeight = 0;
        int wh = 0;
        int uvHeight = 0;
        if (srcWidth != nWidth || height != nHeight) {
            nWidth = srcWidth;
            nHeight = height;
            wh = srcWidth * height;
            uvHeight = height / 2;
        }
// 旋转Y
        int k = 0;
        for (int i = 0; i < srcWidth; i++) {
            int nPos = srcWidth - 1;
            for (int j = 0; j < height; j++) {
                dst[k] = src[nPos - i];
                k++;
                nPos += srcWidth;
            }
        }
        for (int i = 0; i < srcWidth; i += 2) {
            int nPos = wh + srcWidth - 1;
            for (int j = 0; j < uvHeight; j++) {
                dst[k] = src[nPos - i - 1];
                dst[k + 1] = src[nPos - i];
                k += 2;
                nPos += srcWidth;
            }
        }
        return;
    }

    /**
     * 将YUV420SP数据顺时针旋转90度
     *
     * @param data        要旋转的数据
     * @param imageWidth  要旋转的图片宽度
     * @param imageHeight 要旋转的图片高度
     * @return 旋转后的数据
     */
    public static byte[] rotateNV21Degree90(byte[] data, int imageWidth, int imageHeight) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        //旋转Y亮度
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }
        }
        // 旋转U和V颜色分量
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i--;
            }
        }
        return yuv;
    }


    public static byte[] YUV420spRotate270(byte[] src, int width, int height) {
        int count = 0;
        int uvHeight = height >> 1;
        int imgSize = width * height;
        byte[] des = new byte[imgSize * 3 >> 1];
        //copy y
        for (int j = width - 1; j >= 0; j--) {
            for (int i = 0; i < height; i++) {
                des[count++] = src[width * i + j];
            }
        }
        //u,v
        for (int j = width - 1; j > 0; j -= 2) {
            for (int i = 0; i < uvHeight; i++) {
                des[count++] = src[imgSize + width * i + j - 1];
                des[count++] = src[imgSize + width * i + j];
            }
        }
        return des;
    }

    public static byte[] rotateYUV420Degree180(byte[] data, int w, int h) {
        int imgSize = w * h;
        int len = imgSize * 3 / 2;//yuv数组长度是图片尺寸的1.5倍
        byte[] yuv = new byte[len];
        int i = 0;
        int count = 0;
        //y
        for (i = imgSize - 1; i >= 0; i--) {
            yuv[count++] = data[i];
        }
        //u,v
        for (i = len - 1; i >= imgSize; i -= 2) {
            yuv[count++] = data[i - 1];
            yuv[count++] = data[i];
        }
        return yuv;
    }

    /**
     * 保存数据到本地
     *
     * @param buffer 要保存的数据
     * @param offset 要保存数据的起始位置
     * @param length 要保存数据长度
     * @param path   保存路径
     * @param append 是否追加
     */
    public static void save(byte[] buffer, int offset, int length, String path, boolean append) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(path, append);
            fos.write(buffer, offset, length);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.flush();
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * YUV420SP转Bitmap
     *
     * @param data
     * @param width
     * @param height
     * @return
     */
    public static Bitmap yuv420spToBitmap(byte[] data, int width, int height) {
        Bitmap bitmap = null;
        YuvImage image = new YuvImage(data, ImageFormat.NV21, width, height, null);
        if (image != null) {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            image.compressToJpeg(new Rect(0, 0, width, height), 100, stream);
            bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bitmap;
    }

    public static Type.Builder yuvType, rgbaType;
    public static RenderScript renderScript;
    public static Allocation in, out;
    public static ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;

    // format ImageFormat.XXX
    public static Bitmap YUVToBitmap(Context context, byte[] yuv, int format, int width, int height) {
        if (renderScript == null)
            renderScript = RenderScript.create(context);
        if (yuvToRgbIntrinsic == null)
            yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(renderScript, Element.U8_4(renderScript));
        if (yuvType == null) {
            yuvType = new Type.Builder(renderScript, Element.U8(renderScript))
                    .setX(width)
                    .setY(height)
                    .setYuvFormat(format);
            in = Allocation.createTyped(renderScript, yuvType.create(), Allocation.USAGE_SCRIPT);

            rgbaType = new Type.Builder(renderScript, Element.RGBA_8888(renderScript)).setX(width).setY(height);
            out = Allocation.createTyped(renderScript, rgbaType.create(), Allocation.USAGE_SCRIPT);
        }
        in.copyFrom(yuv);

        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);

        Bitmap bmpout = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        out.copyTo(bmpout);
        return bmpout;
    }

    public static void NV21ToBitmapClear()
    {
        yuvType = null;
    }

    // 实际转为NV12
    public static void YV12toNV21(final byte[] input, final byte[] output, final int width, final int height) {
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;
        final int tempFrameSize = frameSize * 5 / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i]; // Cb (U)
            output[frameSize + i * 2 + 1] = input[tempFrameSize + i]; // Cr (V)
        }
    }

    // 实际转为NV21
    public static void YV12toNV21_Temp(final byte[] input, final byte[] output, final int width, final int height) {
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;
        final int tempFrameSize = frameSize * 5 / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2 ] = input[tempFrameSize + i]; // Cr (V)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cb (U)
        }
    }

    // 为图片target添加水印文字
    // Bitmap target：被添加水印的图片
    // String mark：水印文章
    //position 水印位置
    //IndentationPosition 缩进位置  0为最左边
    public static void createWatermark(Bitmap target, String mark, String txtContent, String phoneInfo, int fontsize, int position) {
        int w = target.getWidth();
        int h = target.getHeight();
        //Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        float rate = (w * 1.0f) / 320;
        Paint p = new Paint();
        // 水印的颜色
        p.setColor(Color.WHITE);
        p.setTypeface(Typeface.DEFAULT);
        // 水印的字体大小
        p.setTextSize(fontsize * rate);
        p.setAntiAlias(true);// 去锯齿
        //canvas.drawBitmap(target, 0, 0, p);
        // 在左边的IndentationPosition位置开始添加水印
//        canvas.drawText(mark, (float) (w * 1) / 7, (float) (h * 14) / 15 - Margin, p);
        // 在左边的中间位置开始添加水印
        float drawX = 10 * rate;
        float drawY = position * rate;
        if (txtContent != null) {
            canvas.drawText(txtContent, drawX, h - drawY, p);
        }
        if (phoneInfo != null) {
//            float phoneInfoLen = fontsize * playbackRate  * (statusText.length() );
            canvas.drawText(phoneInfo, drawX, (25 * rate), p);
        }
        if (mark != null)
            canvas.drawText(mark, drawX, drawY, p);
        canvas.save();
        canvas.restore();
    }

    public static byte[] getNV21(int inputWidth, int inputHeight, Bitmap scaled) {

        int[] argb = new int[inputWidth * inputHeight];

        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];
        encodeYUV420SP(yuv, argb, inputWidth, inputHeight);

        scaled.recycle();

        return yuv;
    }

    public static void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
                //    pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }

                index++;
            }
        }
    }

    byte[] i420bytes = null;

    private byte[] swapYV12toI420(byte[] yv12bytes, int width, int height) {
        if (i420bytes == null)
            i420bytes = new byte[yv12bytes.length];
        for (int i = 0; i < width * height; i++)
            i420bytes[i] = yv12bytes[i];
        for (int i = width * height; i < width * height + (width / 2 * height / 2); i++)
            i420bytes[i] = yv12bytes[i + (width / 2 * height / 2)];
        for (int i = width * height + (width / 2 * height / 2); i < width * height + 2 * (width / 2 * height / 2); i++)
            i420bytes[i] = yv12bytes[i - (width / 2 * height / 2)];
        return i420bytes;
    }
}
