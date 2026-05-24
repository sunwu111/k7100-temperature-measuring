package com.zhjinrui.netty;


import io.netty.channel.Channel;


public interface NettyServerListener<T> {

    public byte STATUS_CONNECT_SUCCESS = 0;

    public byte STATUS_CONNECT_CLOSED = 1;

    public byte STATUS_CONNECT_ERROR = 2;

    /**
     * @param msg       消息实体
     * @param ChannelId unique id
     */
    void onMessageResponseServer(T msg, String ChannelId);

    /**
     * server开启成功
     */
    void onStartServer();

    /**
     * server关闭
     */
    void onStopServer();

    /**
     * 与客户端建立连接
     *
     * @param channel 客户端连接的channel
     */
    void onChannelConnect(Channel channel);

    /**
     * 与客户端断开连接
     *
     * @param channel 断开的channel
     */
    void onChannelDisConnect(Channel channel);

    void restart();

}
