package com.zhjinrui.batcom;

import android.os.Build;
import android.os.SystemClock;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 两种锁
 * locker: 数据读取的时候使用
 * loadControlLocker: GPIO控制使用
 */


public class RS485Impl {
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

    // 用于串口通信、气象仪、电池等耗时操作
    private static final ReentrantLock locker = new ReentrantLock();
    // 专用于快速 GPIO 负载控制，避免被通信阻塞
    private static final ReentrantLock loadControlLocker = new ReentrantLock();

    private RS485Impl() {}

    public static RS485Impl Instance() { return impl; }

    // ========== 以下方法使用 loadControlLocker（快速 GPIO 操作） ==========

    public boolean gpioInit(int board) {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
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
        try(AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
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
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioOutput(90);
            GPIO.setGpioDataHigh(90);
            SystemClock.sleep(50);
            GPIO.setGpioOutput(72);
            GPIO.setGpioDataHigh(72);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioOpenLoad3() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioOutput(90);
            GPIO.setGpioDataHigh(90);
            SystemClock.sleep(50);
            GPIO.setGpioOutput(26);
            GPIO.setGpioDataHigh(26);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioOpenRJ45() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioOutput(22);
            GPIO.setGpioDataHigh(22);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioOpenUSB() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            List<Integer> gpioPins = Arrays.asList(18, 19, 20, 21);
            for (int pin : gpioPins) {
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

    public boolean gpioOpen76() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioOutput(76);
            GPIO.setGpioDataHigh(76);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioOpenMIPI() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioOutput(92);
            GPIO.setGpioDataHigh(92);
            SystemClock.sleep(50);

            GPIO.setGpioOutput(28);
            GPIO.setGpioDataHigh(28);
            SystemClock.sleep(50);

            List<Integer> gpioPins = Arrays.asList(25, 74, 75, 76);
            for (int pin : gpioPins) {
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
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioOutput(7);
            GPIO.setGpioDataHigh(7);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseLoad2() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioDataLow(72);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseLoad3() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioDataLow(26);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseRJ45() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioDataLow(22);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseUSB() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioDataLow(19);
            SystemClock.sleep(50);
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
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioOutput(112);
            GPIO.setGpioDataHigh(112);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseSpeaker() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioDataLow(112);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseUART() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioDataLow(27);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioOpenGPS() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioOutput(111);
            GPIO.setGpioDataHigh(111);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseGPS() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioDataLow(111);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioOpenUART() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioOutput(27);
            GPIO.setGpioDataHigh(27);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean gpioCloseMIPI() {
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioDataLow(92);
            SystemClock.sleep(50);
            GPIO.setGpioDataLow(28);
            SystemClock.sleep(50);
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
        try (AutoLock autoLock = new AutoLock(this.loadControlLocker)) {
            GPIO.setGpioDataLow(6);
            SystemClock.sleep(50);
            GPIO.setGpioDataLow(7);
            SystemClock.sleep(50);
            SystemClock.sleep(500);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    // ========== 以下方法继续使用 locker（涉及串口通信、耗时操作） ==========

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

    public boolean resetAero3() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.resetAero3();
        }
    }

    public float[] getBatInfo() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.getBatInfo();
        }
    }

    public String getSpeedInfo() {
        try (AutoLock autoLock = new AutoLock(this.locker)){
            return GPIO.getSpeedInfo();
        }
    }

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