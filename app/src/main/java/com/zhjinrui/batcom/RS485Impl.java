
package com.zhjinrui.batcom;

import android.os.Build;
import android.os.SystemClock;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RS485Impl{
    private static long lastGPIOAccess = System.currentTimeMillis();
    private static final int SERIAL_FRAME_GAP = 2000;   // 串口分帧保护间隔
    public static final String TAG = hikvision.zhanyun.com.hikvision.utils.Log.TAG;
    public class AutoLock implements AutoCloseable {
        private Lock autoLock;
        public AutoLock(Lock autoLock) {
            this.autoLock = autoLock;
            this.autoLock.lock();
            long msLap = System.currentTimeMillis() - lastGPIOAccess;
            if (msLap > 0 & msLap < SERIAL_FRAME_GAP) {
                SystemClock.sleep(SERIAL_FRAME_GAP - msLap);
            }
        }

        @Override
        public void close() {
            this.autoLock.unlock();
            lastGPIOAccess = System.currentTimeMillis();
        }
    }

    private static final RS485Impl impl = new RS485Impl();
    private static final ReentrantLock locker = new ReentrantLock();

    private RS485Impl() {}

    public static RS485Impl Instance() { return impl; }
    public boolean gpioInit(int board) {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.gpioInit(board);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  // 6762
                GPIO.setGpioOutput(27);
                GPIO.setGpioDataHigh(27);
                SystemClock.sleep(50);
                GPIO.setGpioOutput(76);
                SystemClock.sleep(5);
            } else {   // 6735
                GPIO.setGpioDataHigh(122);
            }
            SystemClock.sleep(500);
        }
        return true;
    }

    public boolean gpioUnInit() {
        try(AutoLock autoLock = new AutoLock(this.locker)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // 6762
                GPIO.setGpioDataLow(27);
                SystemClock.sleep(50);
            } else {     // 6735
                GPIO.setGpioDataLow(122);
            }
            GPIO.gpioUnInit();
            SystemClock.sleep(500);
        }
        return true;
    }

    public boolean gpioOpenLoad2() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioOutput(90);  // 设置GPIO90为输出模式
            GPIO.setGpioDataHigh(90);  // 设置GPIO90高电平（使能）
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            GPIO.setGpioOutput(72);  // 设置GPIO72为输出模式
            GPIO.setGpioDataHigh(72);  // 设置GPIO72高电平
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioOpenLoad3() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioOutput(90);  // 设置GPIO90为输出模式
            GPIO.setGpioDataHigh(90);  // 设置GPIO90高电平（使能）
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            GPIO.setGpioOutput(26);  // 设置GPIO26为输出模式
            GPIO.setGpioDataHigh(26);  // 设置GPIO26高电平
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    ///////
    public boolean gpioOpenRJ45() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioOutput(22);  // 设置GPIO22为输出模式
            GPIO.setGpioDataHigh(22);  // 设置GPIO22高电平（使能）
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioOpenUSB() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            List<Integer> gpioPins = Arrays.asList(18, 19, 20, 21);

            // 设置每个GPIO引脚为输出模式并设置为高电平
            for (int pin : gpioPins) {
                // 设置GPIO引脚为输出模式
                GPIO.setGpioOutput(pin);

                // 设置GPIO引脚为高电平
                GPIO.setGpioDataHigh(pin);

                SystemClock.sleep(50);
            }


            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }


    public boolean gpioOpen76() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            // 设置GPIO 76为输出模式并拉高
            GPIO.setGpioOutput(76);
            GPIO.setGpioDataHigh(76);
            SystemClock.sleep(50); // 可以保留延时，或者根据需求调整
            SystemClock.sleep(500); // 主延时
        } catch (Exception e) {
            return false;
        }
        return true;
    }


    public boolean gpioOpenMIPI() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioOutput(92);  // 设置GPIO92为输出模式
            GPIO.setGpioDataHigh(92);  // 设置GPIO92高电平（使能）
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟


            GPIO.setGpioOutput(28);  // 设置GPIO28为输出模式
            GPIO.setGpioDataHigh(28);  // 设置GPIO28高电平（使能）
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟

            //25 74 75 76
            List<Integer> gpioPins = Arrays.asList(25, 74, 75, 76);

            // 设置每个GPIO引脚为输出模式并设置为高电平
            for (int pin : gpioPins) {
                // 设置GPIO引脚为输出模式
                GPIO.setGpioOutput(pin);

                GPIO.setGpioDataHigh(pin);

                SystemClock.sleep(50);
            }

            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioOpenRS485() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioOutput(7);  // 设置GPIO7为输出模式
            GPIO.setGpioDataHigh(7);  // 设置GPIO7高电平（使能）
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
    ///////

    public boolean gpioCloseLoad2() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioDataLow(72);  // 设置GPIO72低电平
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
//            GPIO.setGpioDataLow(90);  // 设置GPIO90低电平
//            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseLoad3() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioDataLow(26);  // 设置GPIO26低电平
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    ///////
    public boolean gpioCloseRJ45() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioDataLow(22);  // 设置GPIO22低电平
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseUSB() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioDataLow(19);  // 设置GPIO19低电平
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟

            GPIO.setGpioDataLow(18);
            SystemClock.sleep(50);

            GPIO.setGpioDataLow(20);
            SystemClock.sleep(50);

            GPIO.setGpioDataLow(21);
            SystemClock.sleep(50);

            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioOpenSpeaker() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioOutput(112);  // 设置GPIO7为输出模式
            GPIO.setGpioDataHigh(112);  // 设置GPIO7高电平（使能）
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

/////////////////
    public boolean gpioCloseSpeaker(){
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioDataLow(112);
            SystemClock.sleep(50);

            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseUART(){
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioDataLow(27);
            SystemClock.sleep(50);

            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioOpenGPS() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioOutput(111);
            GPIO.setGpioDataHigh(111);
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseGPS(){
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioDataLow(111);
            SystemClock.sleep(50);

            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioOpenUART() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioOutput(27);  // 设置GPIO7为输出模式
            GPIO.setGpioDataHigh(27);  // 设置GPIO7高电平（使能）
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseMIPI() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioDataLow(92);  // 设置GPIO92低电平
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            GPIO.setGpioDataLow(28);  // 设置GPIO28低电平
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟

            GPIO.setGpioDataLow(25);
            SystemClock.sleep(50);
            GPIO.setGpioDataLow(74);
            SystemClock.sleep(50);
            GPIO.setGpioDataLow(75);
            SystemClock.sleep(50);
            GPIO.setGpioDataLow(76);
            SystemClock.sleep(50);

            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseRS485() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            GPIO.setGpioDataLow(6);  // 设置GPIO6低电平
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            GPIO.setGpioDataLow(7);  // 设置GPIO7低电平
            SystemClock.sleep(50);  // 可根据硬件需求调整延迟
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }
    ///////

    public int powerControl(int devNum, boolean devswitch) {
        try(AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.powerControl(devNum, devswitch);
        }
    }

    public boolean setRtcInfo(int time[]) {
        try(AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.setRtcInfo(time);
        }
    }

    public int[] getRtcInfo() {
        try(AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getRtcInfo();
        }
    }

    /////
    public boolean resetAero() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.resetAero();
        }
    }

    public boolean resetAero2() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.resetAero2();
        }
    }

    // 富奥通微气象
    public boolean resetAero3() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.resetAero3();
        }
    }
    /////

    public float[] getBatInfo() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.getBatInfo();
        }
    }

    /////
    public String getSpeedInfo() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.getSpeedInfo();
        }
    }
    /////

    public String getAeroInfo(int readSize) {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.getAeroInfo(readSize);
        }
    }
    public String getAeroInfo2() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.getAeroInfo2();
        }
    }


    // 明景微气象数据
    public float[] getAeroInfoArry4WithoutHNJD() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.getAeroInfoArry4WithoutHNJD();
        }
    }
    public float[] getAeroInfo4Arry() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.getAeroInfo4Arry();
        }
    }

    public String getAeroInfo4() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.getAeroInfo4();
        }
    }
    public String getAeroInfo4WithoutHNJD() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.getAeroInfo4WithoutHNJD();
        }
    }




    public boolean coldReboot() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.coldReboot();
        }
    }

    public boolean coldRebootReport() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.coldRebootReport();
        }
    }

    public String getVersion() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.getVersion();
        }
    }

    // 汇能精电控制器
    public String[] getDevInfo() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getDevInfo();
        }
    }

    public float getLoadAmpler() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getLoadAmpler();
        }
    }

    public float getSolarAmpler() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getSolarAmpler();
        }
    }

    public float getBatAmpler() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getBatAmpler();
        }
    }

    public float getSolarVoltage() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getSolarVoltage();
        }
    }

    public float getBatVoltage() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getBatVoltage();
        }
    }

    public float getLoadVoltage() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getLoadVoltage();
        }
    }

    public float getBatTemp() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getBatTemp();
        }
    }

    public String getTime() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getTime();
        }
    }

    public boolean setTime(int time[]) {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.setTime(time);
        }
    }

    public int getBatQuantity() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getBatQuantity();
        }
    }

    public float getBatOutEveryday() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getBatOutEveryday();
        }
    }

    public float getBatInEveryday() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getBatInEveryday();
        }
    }

    public int getBatTotalQuantity() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getBatTotalQuantity();
        }
    }

    public float[] getVoltageInfo() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getVoltageInfo();
        }
    }

    public String getLoadOpenTime() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getLoadOpenTime();
        }
    }

    public String getLoadCloseTime() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getLoadCloseTime();
        }
    }

    public String getLoadControlMode() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getLoadControlMode();
        }
    }

    public int getTimeSelect() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getTimeSelect();
        }
    }

    // 硕日控制器
    public int getSRCurrent() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getDevSystemCurrent();
        }
    }

    public int getSRVoltage() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getDevSystemVoltage();
        }
    }

    public String getSRProductType() {
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getProductType();
        }
    }

    public String[] getSRControllerVersions(){
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getControllerVersions();
        }
    }

    public String getSRBatterySOC(){
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getBatterySOC();
        }
    }

    public float getSRBatteryVoltage(){
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getBatteryVoltage();
        }
    }

    public float getSRDcLoadCurrent(){
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getDcLoadCurrent();
        }
    }

    public float[] getSRTemperatures(){
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getTemperatures();
        }
    }

    public float[] getSRLoadVoltageCurrent(){
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getLoadVoltageCurrent();
        }
    }

    public float[] getSRSolarPanelParams(){
        try (AutoLock autoLock = new AutoLock(this.locker)) {
            return GPIO.getSolarPanelParams();
        }
    }
}
