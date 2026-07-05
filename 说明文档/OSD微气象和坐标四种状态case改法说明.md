# OSD 微气象和坐标四种状态 case 改法说明

## 修改范围

本次拟修改范围只针对两种供电控制类型：

- `deviceConfig.chargeControl == 6`
- `deviceConfig.chargeControl == 8`

其他供电控制类型不改。

需要注意：当前代码里 `deviceConfig.chargeControl == 6` 和 `deviceConfig.chargeControl == 9` 在同一个分支中处理。如果正式修改代码，需要先把 `6` 单独拆出来处理，`9` 保持原来的逻辑不动。

## 判断目标

微气象文本使用：

```java
aeroStatusText() == null || aeroStatusText().trim().isEmpty()
```

坐标文本使用：

```java
Location2String(devLocation) == null || Location2String(devLocation).trim().isEmpty()
```

最终要区分四种情况：

| 状态 | 微气象 | 坐标 | 显示结果 |
| --- | --- | --- | --- |
| 情况一 | 空 | 不空 | 不显示微气象，只显示坐标 |
| 情况二 | 不空 | 空 | 只显示微气象，不显示坐标 |
| 情况三 | 空 | 空 | 微气象和坐标都不显示 |
| 情况四 | 不空 | 不空 | 微气象和坐标都显示 |

## 为什么建议用 case

如果直接写多个 `if`，两者都为空时容易先进入“微气象为空”或者“坐标为空”的分支，导致四种情况不够清晰。

建议先把微气象和坐标是否为空转换成一个状态值，再用 `switch case` 区分。

状态值建议这样定义：

| 状态值 | 含义 |
| --- | --- |
| `0` | 微气象不空，坐标不空 |
| `1` | 微气象为空，坐标不空 |
| `2` | 微气象不空，坐标为空 |
| `3` | 微气象为空，坐标为空 |

这样 `case 3` 就能明确表示“两者都为空”。

## 建议的公共判断方式

下面这段逻辑可以放在 `chargeControl == 6` 和 `chargeControl == 8` 对应分支内部使用。

```java
String aeroText = aeroStatusText();
String locationText = Location2String(devLocation);

boolean aeroEmpty = aeroText == null || aeroText.trim().isEmpty();
boolean locationEmpty = locationText == null || locationText.trim().isEmpty();

if (!aeroEmpty && !aeroText.endsWith("\n")) {
    aeroText = aeroText + "\n";
}

int osdCase = 0;
if (aeroEmpty) {
    osdCase += 1;
}
if (locationEmpty) {
    osdCase += 2;
}
```

## `chargeControl == 6` 的建议拼接方式

`chargeControl == 6` 时，电池、太阳能、负载等信息继续显示，只调整微气象和坐标这两块是否显示。

```java
String extraText;

switch (osdCase) {
    case 1:
        // 微气象为空，坐标不为空
        extraText = locationText + "\n";
        break;

    case 2:
        // 微气象不为空，坐标为空
        extraText = aeroText;
        break;

    case 3:
        // 微气象为空，坐标为空
        extraText = "";
        break;

    case 0:
    default:
        // 微气象不为空，坐标不为空
        extraText = aeroText + locationText + "\n";
        break;
}
```

然后统一拼接到 OSD：

```java
return String.format(
        "通信%s %s %ddBm %s %s\n" +
                "电池%3.2fV/%2.2fA/%d%%/%3.1f℃/%3.1f℃\n" +
                "太阳能%3.1fV/%2.2fA/%2.2fA\n" +
                "%s" +
                "软件V%s %s %d",

        netType,
        SIGNAL_LEVELS[signalLevel],
        signalDBM,
        humanReadableByteCount(trafficLeft, false),
        subString(iccid, 15),

        batVoltage,
        batAmper,
        batPrecent,
        temperature,
        cpuTemp,

        solarVoltage,
        solarAmpler,
        loadAmpler,

        extraText,

        BuildConfig.VERSION_NAME,
        firmwareVersion,
        deviceConfig.wifi ? 1 : 0
);
```

## `chargeControl == 8` 的建议拼接方式

`chargeControl == 8` 时，当前 OSD 主要显示通信、温度、微气象、坐标、软件版本。这里同样只调整微气象和坐标的显示逻辑。

```java
String extraText;

switch (osdCase) {
    case 1:
        // 微气象为空，坐标不为空
        extraText = locationText + "\n";
        break;

    case 2:
        // 微气象不为空，坐标为空
        extraText = aeroText;
        break;

    case 3:
        // 微气象为空，坐标为空
        extraText = "";
        break;

    case 0:
    default:
        // 微气象不为空，坐标不为空
        extraText = aeroText + locationText + "\n";
        break;
}
```

然后统一拼接到 OSD：

```java
return String.format(
        "通信%s %s %ddBm %s %s\n" +
                "温度%3.1f℃\n" +
                "%s" +
                "软件V%s %s %d",

        netType,
        SIGNAL_LEVELS[signalLevel],
        signalDBM,
        humanReadableByteCount(trafficLeft, false),
        subString(iccid, 15),

        cpuTemp,

        extraText,

        BuildConfig.VERSION_NAME,
        firmwareVersion,
        deviceConfig.wifi ? 1 : 0
);
```

## 最终显示效果

微气象为空、坐标不为空时：

```text
通信...
电池...
太阳能...
位置...
软件...
```

微气象不为空、坐标为空时：

```text
通信...
电池...
太阳能...
微气象...
软件...
```

微气象为空、坐标为空时：

```text
通信...
电池...
太阳能...
软件...
```

微气象不为空、坐标不为空时：

```text
通信...
电池...
太阳能...
微气象...
位置...
软件...
```

`chargeControl == 8` 的显示效果同理，只是中间没有电池和太阳能行。
