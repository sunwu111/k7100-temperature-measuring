package com.zhjinrui.netty;

import android.text.TextUtils;

import com.blankj.utilcode.util.LogUtils;
import com.zhjinrui.bean.CommonRequestEntity;
import com.zhjinrui.bean.CommonResponseEntity;

import java.net.InetSocketAddress;

import hikvision.zhanyun.com.hikvision.utils.Log;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;


/**
 * TCP 服务端
 * 目前服务端支持连接多个客户端
 */
public class NettyTcpServer {

    private static final String TAG = "NettyTcpServer";
    private final int port = 1088;
    private Channel channel;

    private NettyServerListener<Object> listener;
    //    private boolean connectStatus;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private boolean isServerStart;

    private String packetSeparator;

    private int maxPacketLong = 1024;

    public void setPacketSeparator(String separator) {
        this.packetSeparator = separator;
    }

    public void setMaxPacketLong(int maxPacketLong) {
        this.maxPacketLong = maxPacketLong;
    }

    private static final class InstanceHolder {
        static final NettyTcpServer instance = new NettyTcpServer();
    }

    public static NettyTcpServer getInstance() {
        return InstanceHolder.instance;
    }

    private NettyTcpServer() {
    }

    public void start() {
        LogUtils.i(this.maxPacketLong);

        new Thread() {
            @Override
            public void run() {
                super.run();
                bossGroup = new NioEventLoopGroup(1);
                workerGroup = new NioEventLoopGroup();
                try {
                    ServerBootstrap b = new ServerBootstrap();
                    b.group(bossGroup, workerGroup)
                            .channel(NioServerSocketChannel.class) // 5
                            .localAddress(new InetSocketAddress(port)) // 6
                            .childOption(ChannelOption.SO_KEEPALIVE, true)
                            .childOption(ChannelOption.SO_REUSEADDR, true)
                            .childOption(ChannelOption.TCP_NODELAY, true)
                            .handler(new LoggingHandler(LogLevel.INFO))
                            .childHandler(new ChannelInitializer<SocketChannel>() { // 7
                                @Override
                                public void initChannel(SocketChannel ch) {
                                    /*ch.pipeline().addLast(new StringEncoder(CharsetUtil.UTF_8));
                                    if (!TextUtils.isEmpty(packetSeparator)) {
                                        ByteBuf delimiter= Unpooled.buffer();
                                        delimiter.writeBytes(packetSeparator.getBytes());
                                        ch.pipeline().addLast(new DelimiterBasedFrameDecoder(maxPacketLong, delimiter));
                                    } else {
                                        ch.pipeline().addLast(new LineBasedFrameDecoder(maxPacketLong));
                                    }*/
                                    ch.pipeline().addLast(new ObjectDecoder(4194304, ClassResolvers.weakCachingConcurrentResolver(getClass().getClassLoader())));
                                    ch.pipeline().addLast(new ObjectEncoder());
                                    ch.pipeline().addLast(new StringEncoder(CharsetUtil.UTF_8));
                                    ch.pipeline().addLast(new StringDecoder(CharsetUtil.UTF_8));
                                    ch.pipeline().addLast(new LineBasedFrameDecoder(2048));
                                    ch.pipeline().addLast(new EchoServerHandler(listener));
                                }
                            });

                    // Bind and start to accept incoming connections.
                    ChannelFuture f = b.bind().sync(); // 8
                    Log.e(TAG, NettyTcpServer.class.getName() + " started and listen on " + f.channel().localAddress());
                    isServerStart = true;
                    listener.onStartServer();
                    // Wait until the server socket is closed.
                    // In this example, this does not happen, but you can do that to gracefully
                    // shut down your server.
                    f.channel().closeFuture().sync(); // 9
                    isServerStart = false;
                    Log.i(TAG, "closeFuture 断开连接");
                } catch (Exception e) {
                    Log.i(TAG, "Netty Server发生异常：" + e.getMessage());
                } finally {
                    Log.i(TAG, "finally 断开连接");
                    isServerStart = false;
                    workerGroup.shutdownGracefully();
                    bossGroup.shutdownGracefully();
                    listener.onStopServer();
                }
            }
        }.start();
    }

    public void disconnect() {
        isServerStart = false;
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
    }

    public void setListener(NettyServerListener<Object> listener) {
        this.listener = listener;
    }


    public boolean isServerStart() {
        return isServerStart;
    }


    // 异步发送消息
    public boolean sendMsgToServer(String data, ChannelFutureListener listener) {
        boolean flag = channel != null && channel.isActive();
        String separator = TextUtils.isEmpty(packetSeparator) ? System.getProperty("line.separator") : packetSeparator;
        if (flag) {
            channel.writeAndFlush(data + separator).addListener(listener);
        }
        return flag;
    }

    public boolean sendMsgToServer(CommonRequestEntity request, final ChannelFutureListener messageStateListener) {
        boolean flag = channel != null && channel.isActive();
        if (flag) {
            this.channel.writeAndFlush(request).addListener(messageStateListener);
        }
        return flag;
    }

    public void sendMsgToServer(CommonResponseEntity response, final ChannelFutureListener messageStateListener) {
        boolean flag = channel != null && channel.isActive();
        if (flag) {
            this.channel.writeAndFlush(response).addListener(messageStateListener);
        }
    }

    // 同步发送消息
    public boolean sendMsgToServer(String data) {
        boolean flag = channel != null && channel.isActive();
        if (flag) {
            String separator = TextUtils.isEmpty(packetSeparator) ? System.getProperty("line.separator") : packetSeparator;
            ChannelFuture channelFuture = channel.writeAndFlush(data + separator).awaitUninterruptibly();
            return channelFuture.isSuccess();
        }
        return false;
    }

    /**
     * 切换通道
     * 设置服务端，与哪个客户端通信
     *
     * @param channel 具体通道
     */
    public void selectorChannel(Channel channel) {
        this.channel = channel;
    }

}
