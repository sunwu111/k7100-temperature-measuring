/*
 * Copyright (c) 2021. Kingron<Kingron@163.om>
 * You must get a license for commercial purpose.
 * 商业使用，必须获取授权。
 */

package hikvision.zhanyun.com.hikvision;

/*
    作者: Kingron<Kingron#163.com>
    版权所有，欢迎改编，改编请署名致谢

    端口反弹客户端Java版

    原理：
    远程服务器监听端口，本客户端主动从远程内网发起连接，并在客户端本地连接到本地的IP和端口
    然后把远程连接和本地连接的数据互相进行收发后转发

    用法：
            new TcpTunnel("11.22.33.44", 56666, "127.0.0.1", 5555).start();

    服务器端需要运行服务器端，可以考虑使用lcx，这是个通用工具
    服务器运行 lcx -listen 56666 5555

    然后在服务器上使用以下指令，即可实现 adb 远程连接安卓手机进行跨网段调试
        adb connect 127.0.0.1
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.Socket;

public class TcpTunnel extends Thread {
    private String remoteIP, localIP;
    private int remotePort, localPort;

    private static class PipeStream implements Runnable {
        private Socket inx, outx;

        public PipeStream(Socket inx, Socket outx) {
            this.inx = inx;
            this.outx = outx;
        }

        @Override
        public void run() {
            try {
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inx.getInputStream());
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outx.getOutputStream());
                byte[] bytes = new byte[4096];
                int len = 0;
                while ((len = bufferedInputStream.read(bytes)) > 0) {
                    bufferedOutputStream.write(bytes, 0, len);
                    bufferedOutputStream.flush();
                }
                bufferedInputStream.close();
                bufferedOutputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (!inx.isClosed())
                    try {
                        inx.close();
                    } catch (Exception e) {
                    }
                if (!outx.isClosed())
                    try {
                        outx.close();
                    } catch (Exception e) {
                    }
            }
        }
    }

    public TcpTunnel(String remoteIP, int remotePort, String localIP, int localPort) {
        this.remoteIP = remoteIP;
        this.remotePort = remotePort;
        this.localIP = localIP;
        this.localPort = localPort;
    }

    public void run() {
        Socket remote, local;
        try {
            remote = new Socket(remoteIP, remotePort);
            local = new Socket(localIP, localPort);

            if (local == null || local.isClosed() || !local.isConnected()
                    || remote == null || remote.isClosed() || !remote.isConnected()) return;

            new Thread(new PipeStream(remote, local)).start();
            new Thread(new PipeStream(local, remote)).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}