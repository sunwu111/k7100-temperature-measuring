package com.zhjinrui.bean;

/**
 * netty 消息类型
 */
public class Constant {

    /**
     * 云台
     */
    public final static int PTZ = 0xA1;
    /**
     * 普通枪机
     */
    public final static int NORMAL_CAMERA = 0xA2;

    /**
     * 拉取视频
     */
    public final static int TAKE_VIDEO = 0xFF00;

    /**
     * 停止播放
     */
    public final static int STOP_VIDEO = 0xFFF0;

    /**
     * 拍摄照片
     */
    public final static int TAKE_PHOTO = 0xFF01;

    /**
     * 服务配置
     */
    public final static int SERVER_CONFIG = 0xFF02;


    /**
     * 获取摄像头配置
     */
    public final static int GET_CAMERA_CONFIG = 0xFF03;

    /**
     * 选择摄像头
     */
    public final static int SELECT_CAMERA = 0xFF04;


    /**
     * 获取拍照时间表
     */
    public final static int GET_PHOTO_TIME_TABLE = 0xFF05;


    /**
     * 设置拍照时间表
     */
    public final static int SET_PHOTO_TIME_TABLE = 0xFF06;


    /**
     * 设置拍照时间表
     */
    public final static int SET_PHOTO_TIME_TABLE_RES = 0xFF07;

    /**
     * 重启APP
     */
    public final static int RESTART_APP = 0xFF08;

    /**
     * 重启系统
     */
    public final static int RESTART_SYS = 0xFF09;
    /**
     * 其他配置
     */
    public final static int OTHER_CONFIG = 0xFF10;
    /**
     * 其他配置响应
     */
    public final static int OTHER_CONFIG_RES = 0xFF11;
    /**
     * 获取其他配置
     */
    public final static int GET_OTHER_CONFIG = 0xFF12;

    /**
     * 获取基本参数，电压电流等
     */
    public final static int GET_PARAM = 0xFF13;

    /**
     * 获取服务器配置
     */
    public final static int GET_SERVER_CONFIG = 0xFF14;

    /**
     * 球机配置
     */
    public final static int SET_BALL_MACHINE_CONFIG = 0xFF15;

    /**
     * 获取球机配置
     */
    public final static int GET_BALL_MACHINE_CONFIG = 0xFF16;

}
