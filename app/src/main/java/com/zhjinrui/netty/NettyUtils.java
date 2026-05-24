package com.zhjinrui.netty;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import com.blankj.utilcode.util.NetworkUtils;
import com.zhjinrui.bean.CommonResponseEntity;
import com.zhjinrui.bean.Constant;

import hikvision.zhanyun.com.hikvision.utils.Log;

public class NettyUtils {
    private static String ip;

    //手机下发拍照指令
    private static boolean takePhoto;


    private static boolean playVideo;

    public static boolean isPlayVideo() {
        return playVideo;
    }

    public static void setPlayVideo(boolean playVideo) {
        NettyUtils.playVideo = playVideo;
    }

    public static boolean isTakePhoto() {
        return takePhoto;
    }

    public static void setTakePhoto(boolean takePhoto) {
        NettyUtils.takePhoto = takePhoto;
    }

    public static String getIp() {
        return ip;
    }
    public static void setIp(String ip) {
        NettyUtils.ip = ip;
    }
    public static Bitmap matrixBitmap(Bitmap bitmap, Float scale) {
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                bitmap.getHeight(), matrix, true);
    }


    /**
     * 发送相机的配置
     *
     * @param str 配置信息
     */
    public static void sendCameraConfig(String str) {
        NettyTcpServer.getInstance().sendMsgToServer(new CommonResponseEntity(Constant.GET_CAMERA_CONFIG, str, null), future -> {
            if (future.isSuccess()) {
                Log.i(Log.TAG, "=====>sendCameraConfig发送成功");
            } else {
                Log.i(Log.TAG, "=====>sendCameraConfig发送失败");
            }
        });
    }


    /**
     * 发送相机的配置
     *
     * @param str 配置信息
     */
    public static void sendPhotoTable(String str) {
        NettyTcpServer.getInstance().sendMsgToServer(new CommonResponseEntity(Constant.GET_PHOTO_TIME_TABLE, str, null), future -> {
            if (future.isSuccess()) {
                Log.i(Log.TAG, "====>sendPhotoTable发送成功");
            } else {
                Log.i(Log.TAG, "====>sendPhotoTable发送失败");
            }
        });
    }


    public static void sendSettingTableRes(int res) {
        CommonResponseEntity response = new CommonResponseEntity(Constant.SET_PHOTO_TIME_TABLE_RES,
                null, null);
        response.res = res;
        NettyTcpServer.getInstance().sendMsgToServer(response, future -> {
            if (future.isSuccess()) {
                Log.i(Log.TAG, "====>sendSettingTableRes发送成功");
            } else {
                Log.i(Log.TAG, "====>sendSettingTableRes发送失败");
            }
        });
    }


    public static void sendOtherSettingConfig(String content, int res) {
        CommonResponseEntity response = new CommonResponseEntity(Constant.GET_OTHER_CONFIG,
                content, null);
        response.res = res;
        NettyTcpServer.getInstance().sendMsgToServer(response, future -> {
            if (future.isSuccess()) {
                Log.i(Log.TAG, "====>sendOtherSettingConfig 发送成功");
            } else {
                Log.i(Log.TAG, "====>sendOtherSettingConfig 发送失败");
            }
        });
    }

    public static void sendParam(String content, int res) {
        CommonResponseEntity response = new CommonResponseEntity(Constant.GET_PARAM,
                content, null);
        response.res = res;
        NettyTcpServer.getInstance().sendMsgToServer(response, future -> {
            if (future.isSuccess()) {
                Log.i(Log.TAG, "====>sendParam 发送成功");
            } else {
                Log.i(Log.TAG, "====>sendParam 发送失败");
            }
        });
    }
    public static void getServerConfig(String content, int res) {
        CommonResponseEntity response = new CommonResponseEntity(Constant.GET_SERVER_CONFIG,
                content, null);
        response.res = res;
        NettyTcpServer.getInstance().sendMsgToServer(response, future -> {
            if (future.isSuccess()) {
                Log.i(Log.TAG, "====>sendParam 发送成功");
            } else {
                Log.i(Log.TAG, "====>sendParam 发送失败");
            }
        });
    }

    /**
     * NsdManager 同一个局域网内，这里注册一个服务可以被其他客户端搜索到，建立简单的消息传递
     *
     * @param context 上下文
     */
    public static void registerNsd(Context context) {
        NsdManager nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName("NetChat");
        serviceInfo.setServiceType("_http._tcp.");
        serviceInfo.setPort(1088);
        serviceInfo.setAttribute("ipaddress", NetworkUtils.getIPAddress(true));
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, new NsdManager.RegistrationListener() {
            @Override
            public void onRegistrationFailed(NsdServiceInfo nsdServiceInfo, int i) {
                //异常上报
                Log.i(Log.TAG, "注册失败");
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo nsdServiceInfo, int i) {
            }

            @Override
            public void onServiceRegistered(NsdServiceInfo nsdServiceInfo) {
                //注册成功
                Log.i(Log.TAG, "注册成功");
            }
            @Override
            public void onServiceUnregistered(NsdServiceInfo nsdServiceInfo) {
            }
        });
    }

}
