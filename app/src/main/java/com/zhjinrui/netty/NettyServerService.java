package com.zhjinrui.netty;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.NetworkUtils;
import com.zhjinrui.bean.ClientChanel;
import com.zhjinrui.bean.CommonRequestEntity;
import com.zhjinrui.bean.CommonResponseEntity;
import com.zhjinrui.bean.Constant;

import org.greenrobot.eventbus.EventBus;

import hikvision.zhanyun.com.hikvision.utils.Log;


public class NettyServerService extends Service {

    private final String TAG = "NettyServerService";

    @Override
    public void onCreate() {
        super.onCreate();
        startServer();
    }

    private void startServer() {
        NettyTcpServer nettyTcpServer = NettyTcpServer.getInstance();
        //nettyTcpServer.setPacketSeparator("#");
        if (!nettyTcpServer.isServerStart()) {
            Log.i(Log.TAG, "启动 startserver");
            nettyTcpServer.setListener(listener);
            nettyTcpServer.start();
        } else {
            Log.i(Log.TAG, "断开连接");
            NettyTcpServer.getInstance().disconnect();
        }
    }


    private final NettyServerListener<Object> listener = new NettyServerListener<Object>() {
        @Override
        public void onMessageResponseServer(Object msg, String ChannelId) {
            Log.i(Log.TAG, "onMessageResponseServer:ChannelId:" + ChannelId);
            sendCameraType();
            if (msg instanceof CommonRequestEntity) {
                EventBus.getDefault().post((CommonRequestEntity) msg);
            }
        }

        @Override
        public void onStartServer() {
            Log.i(TAG, "onStartServer::" + NetworkUtils.getIPAddress(true));
        }

        @Override
        public void onStopServer() {
            Log.i(TAG, "onStopServer");
            if (!NettyTcpServer.getInstance().isServerStart()) {
                NettyTcpServer.getInstance().setListener(listener);
                NettyTcpServer.getInstance().start();
            }
        }

        @Override
        public void onChannelConnect(io.netty.channel.Channel channel) {
            String socketStr = channel.remoteAddress().toString();
            String[] split = socketStr.split(":");
            String substring = split[0].substring(1);
            LogUtils.i(substring);
            NettyUtils.setIp(substring);
            final ClientChanel clientChanel = new ClientChanel(socketStr, channel, channel.id().asShortText());
            Log.i(TAG, "客户端已连接：" + socketStr);
            NettyTcpServer.getInstance().selectorChannel(clientChanel.getChannel());
        }

        @Override
        public void onChannelDisConnect(io.netty.channel.Channel channel) {
            Log.i(TAG, "onChannelDisConnect:ChannelId = " + channel.id().asShortText());
        }

        @Override
        public void restart() {
            Log.i(TAG, "重新启动 startserver");
            NettyTcpServer nettyTcpServer = NettyTcpServer.getInstance();
            nettyTcpServer.disconnect();
            nettyTcpServer.setListener(listener);
            nettyTcpServer.start();
        }
    };

    private void sendCameraType() {
        CommonResponseEntity commonResponseEntity = new CommonResponseEntity();
        commonResponseEntity.type = (Constant.PTZ);
        NettyTcpServer.getInstance().sendMsgToServer(commonResponseEntity, future -> {
            if (future.isSuccess()) {
                Log.i(TAG, "Write auth successful");
            } else {
                Log.i(TAG, "Write auth error");
            }
        });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
