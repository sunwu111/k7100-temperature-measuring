package hikvision.zhanyun.com.hikvision.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class MipiSwitch {
    private static final String FilePath = "/sys/devices/platform/1000b000.pinctrl/mt_gpio";
    private static final String GpioPath = "/sys/devices/platform/1000b000.pinctrl/Gpio";

    public static void switchTo(int id) {
        switch (id) {
            case 0:
            case 1:
                setGpiolow("91");
                setGpiolow("4");
                setGpioHigh("94");
                setGpioHigh("5");
                break;
            case 2:
            case 3:
                setGpiolow("94");
                setGpiolow("5");
                setGpioHigh("91");
                setGpioHigh("4");
                break;
            default:
                break;
        }
    }

    private static void setGpioHigh(String pin) {
        if (pin.isEmpty()) {
            return;
        }
        setGpioMode(pin, "0");
        setGpioDir(pin, "1");
        setGpioOut(pin, "1");
    }

    private static void setGpiolow(String pin) {
        if (pin.isEmpty()) {
            return;
        }
        setGpioMode(pin, "0");
        setGpioDir(pin, "1");
        setGpioOut(pin, "0");
    }

    private static void setGpioMode(String pin, String value) {
        nm_write(FilePath, "mode " + pin + " " + value);
    }

    private static void setGpioDir(String pin, String value) {
        nm_write(FilePath, "dir " + pin + " " + value);
    }

    private static void setGpioOut(String pin, String value) {
        nm_write(FilePath, "out " + pin + " " + value);
    }

    private String getGpioValue(String pin){
        nm_write(GpioPath,pin);
        return nm_read(GpioPath);
    }

    private static void nm_write(String sys_path, String value) {
        if (value.isEmpty()) {
            return;
        }
        FileWriter fw;
        try {
            fw = new FileWriter(sys_path);
            fw.write(value);
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private  String nm_read(String sys_path) {
        String prop = null;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(sys_path));
            prop = reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(reader != null){
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return prop;
    }
}
