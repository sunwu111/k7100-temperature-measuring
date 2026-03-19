package com.zhjinrui.netty;

import static hikvision.zhanyun.com.hikvision.MainActivity.DEBUG;

import com.zhjinrui.bean.CommonRequestEntity;

import hikvision.zhanyun.com.hikvision.utils.Log;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;


@ChannelHandler.Sharable
public class EchoServerHandler extends SimpleChannelInboundHandler<Object> {

    private static final String TAG = "EchoServerHandler";
    private final NettyServerListener<Object> mListener;

    public EchoServerHandler(NettyServerListener<Object> listener) {
        this.mListener = listener;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,
                                Throwable cause) {
        Log.i(TAG, "exceptionCaught ： " + cause.getMessage());
        //cause.printStackTrace();
        ctx.close();

        mListener.restart();
    }


    /**
     * 连接成功
     *
     * @param ctx 上下文
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        Log.i(TAG, "Channel  Active");
        mListener.onChannelConnect(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Log.i(TAG, "Channel Inactive");
        mListener.onChannelDisConnect(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg.equals("Heartbeat")) {
            Log.d(TAG, "Heartbeat");
            return; //客户端发送来的心跳数据
        }
        if (msg instanceof CommonRequestEntity) {
            if (DEBUG)
                Log.i(TAG, "==========================>" + ((CommonRequestEntity) msg).getContent());
            mListener.onMessageResponseServer(msg, ctx.channel().id().asShortText());
        }
    }
}
