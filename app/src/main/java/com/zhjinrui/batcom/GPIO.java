/*
 * Copyright (c) 2021. Kingron<Kingron@163.om>
 * You must get a license for commercial purpose.
 * 商业使用，必须获取授权。
 */

package com.zhjinrui.batcom;

/**
 * Created by Alextao on 2018/1/11.
 * This is the GPIO api. firstly, to use the library,You need make a class with the package name
 * <p>
 * alextao/newmobi/com/gpiodemo/GPIO and put these method in it.
 * or you can override your own class and put the implementation in your own source.
 * For use, you need call the method {@Gpio#gpioInit} in your project. Such as in the method onCreate() of Activity.
 * of course, you need to unregister GPIO as call {@GPIO#gpioUnInit}.
 */

public class GPIO {

    public static int EP_MODE_LOAD_TIME = 0x03;
    public static int EP_MODE_LOAD_MANUAL = 0x00;

    static {
        System.loadLibrary("newmobi_gpio");
    }

    //get the total GPIO numbers.
    //as for customers, you need to decide how many and which GPIO pins are available.
    public static native int getGpioMaxNumber();

    //Initialize the GPIO. Call before use GPIO pins.
    public static native boolean gpioInit(int board);

    //similar to above but it is uninit.
    public static native boolean gpioUnInit();

    //set a pin in input mode.
    public static native boolean setGpioInput(int gpioIndex);

    public static native boolean setGpioInputAI(int gpioIndex);

    //set a pin in output mode.
    public static native boolean setGpioOutput(int gpioIndex);

    public static native boolean setGpioOutputAI(int gpioIndex);

    //set a pin data high.
    public static native boolean setGpioDataHigh(int gpioIndex);


    //set a pin data low.
    public static native boolean setGpioDataLow(int gpioIndex);

    public static native float[] getBatInfo();

    public static native String getSpeedInfo(); /////
    public static native String getAeroInfo(int readSize);
    public static native String getAeroInfo2();
    public static native float[] getGyroInfo(int idx);
    public static native boolean resetGyro(int idx);



    // 明景微气象
    // 明景微气象
    public static native String getAeroInfo4();
    public static native String getAeroInfo4WithoutHNJD();
    public static native float[] getAeroInfo4Arry();
    public static native float[] getAeroInfoArry4WithoutHNJD();



    public static native int powerControl(int devNum, boolean devswitch);

    public static native int[] getRtcInfo();

    public static native boolean setRtcInfo(int time[]);

    public static native boolean setReboot(boolean coldReboot, int hour, int minute, int second);

    public static native boolean epPowerOnOff(boolean poweron);

    public static native boolean epPowerSetTime(boolean UseTwoTimeSection, String on1, String off1, String on2, String off2);

    public static native boolean epeverDemo();

    public static native float[] epGetBatInfo();

    public static native boolean epSetTime(String time);

    public static native boolean epSetLoadMode(int mode);

    /////
    /**
     * 清零气象仪雨量（北京气象仪）
     *
     * @return
     */
    public static native boolean resetAero();

    /**
     * 清零气象仪雨量（邯郸气象仪）
     *
     * @return
     */
    public static native boolean resetAero2();

    /**
     * 清零气象仪雨量（富奥通微气象）
     *
     * @return
     */
    public static native boolean resetAero3();
    /////

    /**
     * 冷启动
     * @return
     */
    public static native boolean coldReboot();

    public static native boolean coldRebootReport();

    /**
     * 重启充电控制器
     * @return
     */
    public static native boolean rebootChargeController();

    public static native String getVersion();

    // 汇能精电控制器
    public static native String[] getDevInfo();

    public static native float getLoadAmpler();

    public static native float getSolarAmpler();

    public static native float getBatAmpler();

    public static native float getSolarVoltage();

    public static native float getBatVoltage();

    public static native float getLoadVoltage();

    public static native float getBatTemp();

    public static native String getTime();

    public static native boolean setTime(int time[]);

    public static native int getBatQuantity();

    public static native float getBatOutEveryday();

    public static native float getBatInEveryday();

    public static native int getBatTotalQuantity();

    public static native float[] getVoltageInfo();

    public static native String getLoadOpenTime();

    public static native String getLoadCloseTime();

    public static native String getLoadControlMode();

    public static native int getTimeSelect();

    // 硕日控制器
    public static native int getDevSystemVoltage();

    public static native int getDevSystemCurrent();

    public static native String getProductType();

    public static native String getBatterySOC();

    public static native float getBatteryVoltage();

    public static native float getDcLoadCurrent();

    public static native String[] getControllerVersions();

    public static native float[] getTemperatures();

    public static native float[] getLoadVoltageCurrent();

    public static native float[] getSolarPanelParams();
}
