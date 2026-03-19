package hikvision.zhanyun.com.hikvision.utils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * Created by ZY004Engineer on 2018/11/14.
 */

public class SystemManager {
    /**
     * 手机是否root
     *
     * @return
     */
    public static boolean isRoot() {
        boolean isRoot = false;
        String sys = System.getenv("PATH");
        ArrayList<String> commands = new ArrayList<String>();
        String[] path = sys.split(":");
        for (int i = 0; i < path.length; i++) {
            String commod = "ls -l " + path[i] + "/su";
            commands.add(commod);
            System.out.println("commod : " + commod);
        }
        ArrayList<String> res = run("/system/bin/sh", commands);
        String response = "";
        for (int i = 0; i < res.size(); i++) {
            response += res.get(i);
        }
        int inavailableCount = 0;
        String root = "-rwsr-sr-x root root";
        for (int i = 0; i < res.size(); i++) {
            if (res.get(i).contains("No such file or directory")
                    || res.get(i).contains("Permission denied")) {
                inavailableCount++;
            }
        }
        return inavailableCount < res.size();
    }

    // 批量运行命令行
    private static ArrayList run(String shell, ArrayList<String> commands) {
        ArrayList output = new ArrayList();
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(shell);
            BufferedOutputStream shellInput = new BufferedOutputStream(
                    process.getOutputStream());
            BufferedReader shellOutput = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            for (String command : commands) {
                shellInput.write((command + " 2>&1\n").getBytes());
            }

            shellInput.write("exit\n".getBytes());
            shellInput.flush();

            String line;
            while ((line = shellOutput.readLine()) != null) {
                output.add(line);
            }

            process.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            process.destroy();
        }

        return output;
    }

    /**
     * 执行命令函数，返回执行结果匹配的行字符串
     * @param cmd   命令行
     * @param match 命令行执行结果中的匹配字符串
     */
    public static String shellExec(String cmd, String match) {
        String line = null;
        try {
            Runtime mRuntime = Runtime.getRuntime();
            Process mProcess = mRuntime.exec(cmd);
            BufferedReader mReader = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));

            while (true) {
                line = mReader.readLine();
                if (line == null) break;
                if (line.contains(match)) {
                    break;
                }
            }
            //结束缓冲
            mReader.close();
        } catch (Exception e) {
            // 异常处理
            Log.i(Log.TAG, "命令运行异常：" + e);
        }
        return line;
    }
}
