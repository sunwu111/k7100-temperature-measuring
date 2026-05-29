package hikvision.zhanyun.com.hikvision.utils;


import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class AndroidThermalMonitor {
    private static final String TAG = "ThermalMonitor";

    private static final int CPU_THERMAL_ZONE_ID = 3;

    private static final Map<Integer, String> ZONE_DESCRIPTIONS = new HashMap<>();

    static {
        initZoneDescriptions();
    }

    private static void initZoneDescriptions() {
        ZONE_DESCRIPTIONS.put(0, "电池热敏电阻温度");
        ZONE_DESCRIPTIONS.put(1, "SoC整体或SoC表面平均温度");
        ZONE_DESCRIPTIONS.put(2, "基带或射频功放附近的板级温度");
        ZONE_DESCRIPTIONS.put(3, "CPU核心温度");
        ZONE_DESCRIPTIONS.put(4, "射频功放温度");
        ZONE_DESCRIPTIONS.put(5, "电源管理芯片温度");
        ZONE_DESCRIPTIONS.put(6, "WiFi Bluetooth子系统温度");
        ZONE_DESCRIPTIONS.put(7, "GPU温度传感器1");
        ZONE_DESCRIPTIONS.put(8, "GPU温度传感器2");
        ZONE_DESCRIPTIONS.put(9, "ISP温度传感器");
        ZONE_DESCRIPTIONS.put(10, "多媒体温度传感器1");
        ZONE_DESCRIPTIONS.put(11, "多媒体温度传感器2");
        ZONE_DESCRIPTIONS.put(12, "多媒体温度传感器3");
        ZONE_DESCRIPTIONS.put(13, "DC DC降压模块温度1");
        ZONE_DESCRIPTIONS.put(14, "DC DC降压模块温度2");
        ZONE_DESCRIPTIONS.put(15, "电池逻辑温度");
        ZONE_DESCRIPTIONS.put(16, "充电IC温度1");
        ZONE_DESCRIPTIONS.put(17, "充电IC温度2");
    }

    /**
     * 读取指定thermal_zone的温度
     */
    public static Float readThermalZoneTemp(int zoneId) {
        String filePath = String.format("/sys/class/thermal/thermal_zone%d/temp", zoneId);

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine();
            if (line != null && !line.trim().isEmpty()) {
                float tempMilliC = Float.parseFloat(line.trim());
                float tempC = tempMilliC / 1000.0f;

                // 检查是否为无效值（-127°C通常表示传感器未启用）
                if (Math.abs(tempC + 127.0f) < 0.1f) {
                    return null;
                }
                return tempC;
            }
        } catch (Exception e) {
            Log.e(Log.TAG,"温度检测错误");
            return null;
        }
        return null;
    }

    /**
     * 获取传感器状态描述
     */
    private static String getSensorStatus(Float temperature) {
        if (temperature == null) {
            return "❌ 传感器未启用或读取失败";
        }
        if (temperature > 85.0f) {
            return "🔥 温度过高警告";
        } else if (temperature > 75.0f) {
            return "⚠️  温度较高";
        } else if (temperature > 60.0f) {
            return "📈 温度正常偏高";
        } else if (temperature > 45.0f) {
            return "✅ 温度正常";
        } else {
            return "❄️  温度较低";
        }
    }

    /**
     * 读取并输出所有thermal_zone的温度到Log
     */
//    public static void logAllThermalTemperatures() {
//        Log.i(TAG, "==================== 开始读取所有温度传感器 ====================");
//        Log.i(TAG, "Zone |   温度(℃)   | 状态 | 描述");
//        Log.i(TAG, "-----|-------------|------|--------------------------------");
//
//        for (int zoneId = 0; zoneId <= 17; zoneId++) {
//            Float temperature = readThermalZoneTemp(zoneId);
//            String description = ZONE_DESCRIPTIONS.getOrDefault(zoneId, "未知传感器");
//
//            if (temperature == null) {
//                Log.i(TAG, String.format("%4d | %11s | %s | %s",
//                        zoneId, "N/A", "❌ 未启用", description));
//            } else {
//                String status = getSensorStatus(temperature);
//                Log.i(TAG, String.format("%4d | %11.1f | %s | %s",
//                        zoneId, temperature, status, description));
//            }
//        }
//
//        Log.i(TAG, "==================== 温度读取完成 ====================");
//    }


    public static void logAllThermalTemperatures() {
        Log.i(TAG, "==================== 开始读取所有温度传感器 ====================");
        Log.i(TAG, "Zone |   温度(℃)   | 描述");
        Log.i(TAG, "-----|-------------|--------------------------------");

        for (int zoneId = 0; zoneId <= 17; zoneId++) {
            Float temperature = readThermalZoneTemp(zoneId);
            String description = ZONE_DESCRIPTIONS.getOrDefault(zoneId, "未知传感器");

            if (temperature == null) {
                Log.i(TAG, String.format("%4d | %11s | %s",
                        zoneId, "N/A", description));
            } else {
                Log.i(TAG, String.format("%4d | %11.1f | %s",
                        zoneId, temperature, description));
            }
        }

        Log.i(TAG, "==================== 温度读取完成 ====================");
    }

    /**
     * 只输出有效的温度传感器（排除未启用的）
     */
    public static void logValidThermalTemperatures() {
        Log.d(TAG, "==================== 有效的温度传感器 ====================");

        for (int zoneId = 0; zoneId <= 17; zoneId++) {
            Float temperature = readThermalZoneTemp(zoneId);
            if (temperature != null) {
                String description = ZONE_DESCRIPTIONS.getOrDefault(zoneId, "未知传感器");
                String status = getSensorStatus(temperature);
                Log.d(TAG, String.format("thermal_zone%d: %.1f°C - %s [%s]",
                        zoneId, temperature, description, status));
            }
        }

        Log.d(TAG, "==================== 有效温度读取完成 ====================");
    }

    /**
     * 输出关键部件温度（详细格式）
     */
    public static void logCriticalTemperatures() {
        int[] criticalZones = {0, 1, 3, 4, 5, 6, 15}; // 重要的传感器

        Log.w(TAG, "==================== 关键部件温度监控 ====================");

        for (int zoneId : criticalZones) {
            Float temperature = readThermalZoneTemp(zoneId);
            String description = ZONE_DESCRIPTIONS.getOrDefault(zoneId, "未知传感器");

            if (temperature != null) {
                String status = getSensorStatus(temperature);

                // 根据温度级别使用不同的Log级别
                if (temperature > 85.0f) {
                    Log.e(TAG, String.format("【危险】%s: %.1f°C - %s",
                            description, temperature, status));
                } else if (temperature > 75.0f) {
                    Log.w(TAG, String.format("【警告】%s: %.1f°C - %s",
                            description, temperature, status));
                } else if (temperature > 60.0f) {
                    Log.i(TAG, String.format("【注意】%s: %.1f°C - %s",
                            description, temperature, status));
                } else {
                    Log.d(TAG, String.format("%s: %.1f°C - %s",
                            description, temperature, status));
                }
            } else {
                Log.v(TAG, String.format("%s: 传感器未启用", description));
            }
        }

        Log.w(TAG, "==================== 关键温度监控完成 ====================");
    }

    /**
     * 简洁版温度监控 - 单行日志输出
     */
    public static void logSimpleTemperatures() {
        StringBuilder sb = new StringBuilder();
        sb.append("温度: ");

        // 只显示几个关键的
        int[] displayZones = {3, 0, 1, 6}; // CPU, 电池, SoC, WiFi

        for (int i = 0; i < displayZones.length; i++) {
            int zoneId = displayZones[i];
            Float temp = readThermalZoneTemp(zoneId);

            if (temp != null) {
                String label = "";
                switch (zoneId) {
                    case 0: label = "电池"; break;
                    case 1: label = "SoC"; break;
                    case 3: label = "CPU"; break;
                    case 6: label = "WiFi"; break;
                }
                sb.append(String.format("%s:%.1f", label, temp));
                if (i < displayZones.length - 1) sb.append(", ");
            }
        }

        Log.i(TAG, sb.toString());
    }



    public static Float getCpuTemperature() {
        Float temperature = readThermalZoneTemp(CPU_THERMAL_ZONE_ID);
        return temperature == null ? 0.0f : temperature;
    }



    /**
     * 监控温度变化趋势
     */
    private static Map<Integer, Float> lastTemperatures = new HashMap<>();

    public static void logTemperatureTrends() {
        Log.v(TAG, "==================== 温度变化趋势 ====================");

        for (int zoneId = 0; zoneId <= 17; zoneId++) {
            Float currentTemp = readThermalZoneTemp(zoneId);
            if (currentTemp != null) {
                Float lastTemp = lastTemperatures.get(zoneId);

                if (lastTemp != null) {
                    float delta = currentTemp - lastTemp;
                    String trend;
                    if (delta > 2.0f) trend = "📈快速上升";
                    else if (delta > 0.5f) trend = "↑上升";
                    else if (delta < -2.0f) trend = "📉快速下降";
                    else if (delta < -0.5f) trend = "↓下降";
                    else trend = "→稳定";

                    String description = ZONE_DESCRIPTIONS.getOrDefault(zoneId, "未知");
                    Log.v(TAG, String.format("zone%d[%s]: %.1f°C (Δ%.1f°C) %s",
                            zoneId, description, currentTemp, delta, trend));
                }

                // 更新最后记录的温度
                lastTemperatures.put(zoneId, currentTemp);
            }
        }

        Log.v(TAG, "==================== 趋势分析完成 ====================");
    }

    /**
     * 检查温度是否在安全范围内
     */
    public static boolean checkTemperatureSafety() {
        boolean isSafe = true;

        for (int zoneId = 0; zoneId <= 17; zoneId++) {
            Float temp = readThermalZoneTemp(zoneId);
            if (temp != null && temp > 90.0f) {
                String desc = ZONE_DESCRIPTIONS.getOrDefault(zoneId, "未知传感器");
                Log.e(TAG, String.format("⚠️  【温度告警】%s 温度过高: %.1f°C", desc, temp));
                isSafe = false;
            }
        }

        return isSafe;
    }
}