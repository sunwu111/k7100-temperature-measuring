package hikvision.zhanyun.com.hikvision;

import static hikvision.zhanyun.com.hikvision.MainActivity.is6735;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.RequiresApi;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import hikvision.zhanyun.com.hikvision.utils.Log;

/**
 * Created by ZY004Engineer on 2018/12/18.
 */

/**
 * 用于发送视频流的Socket客户端
 */
public class SocketClient {
    public static final int MAX_UDP_PACK_SIZE = 5000;                            // UDP 数据报最大大小
    //public static final int BUF_SIZE = 1000;                            // UDP 数据报最大大小
    private boolean bio = true;
    private Socket tcpSock;
    private DataOutputStream dos;
    private InetSocketAddress address;
    private SocketChannel tcpSocketChannel;
    private ByteBuffer socketWriteBuffer = ByteBuffer.allocate(256 * 1024);
    private static HandlerThread sendThread;
    private static Handler sendHandler;
    private DatagramSocket udpSocket;                           // UDP 视频流Socket
    private DatagramPacket livePacket;                          // 用于UDP 视频流发送的缓冲数据包

    public final boolean isUDP;
    private boolean run = true;

    public SocketClient(String host, int port, boolean UDP) {
        if (sendThread == null) sendThread = new HandlerThread("视频数据发送线程");
        address = new InetSocketAddress(host, port);
        this.isUDP = UDP;
        if (sendHandler == null) {
            sendThread.start();
            sendHandler = new Handler(sendThread.getLooper());
        }
    }

//    @RequiresApi(api = Build.VERSION_CODES.N)
//    public boolean open() {
//        if (!run) return false;
//        try {
//            if (isUDP) {
//                if (udpSocket == null) {
//                    udpSocket = new DatagramSocket();
//                    livePacket = new DatagramPacket(new byte[MAX_UDP_PACK_SIZE], MAX_UDP_PACK_SIZE, address);
//                    Log.d(Log.TAG, "开启UDP连接成功: " + address.getAddress() + ":" + address.getPort() + ", 本地端口: " + udpSocket.getLocalPort());
//                }
//            } else if (bio || is6735) { // blocked socket
//                if (tcpSock == null || tcpSock.isClosed() || !tcpSock.isConnected()) {
//                    tcpSock = new Socket();
//                    tcpSock.connect(address, 3 * 1000);
//                    tcpSock.setSoLinger(true, 1000);
//                    Log.d(Log.TAG, "开启阻塞TCP连接成功: " + address.getAddress() + ":" + address.getPort() + ", 本地端口: " + tcpSock.getLocalPort());
//                    dos = new DataOutputStream(tcpSock.getOutputStream());
//                }
//            } else { // unblocked socket
//                if (tcpSocketChannel == null || !tcpSocketChannel.isConnected()) {
//                    tcpSocketChannel = SocketChannel.open(address);
//                    tcpSocketChannel.configureBlocking(false);
//                    tcpSocketChannel.setOption(StandardSocketOptions.SO_LINGER, 0);
//                    socketWriteBuffer.clear();
//                    Log.d(Log.TAG, "开启非阻塞TCP连接: " + (tcpSocketChannel.isConnected() ? "成功" : "失败") +
//                            address.getAddress() + ":" + address.getPort() + ", " +
//                            "本地端口: " + tcpSocketChannel.getLocalAddress());
//                }
//            }
//
//            return true;
//        } catch (Exception e) {
//            Log.e(Log.TAG, "开启" + (isUDP ? "UDP" : "TCP") + "失败: " + address.getAddress() + ":" + address.getPort() + " => " + e.getMessage());
//            return false;
//        }
//    }


    private int connectFailCount = 0;
    private static final int MAX_FAIL_LOG_COUNT = 5;
    private boolean hasReachedMaxLog = false;

    @RequiresApi(api = Build.VERSION_CODES.N)
    public boolean open() {
        if (!run) return false;

        // 如果已经达到最大失败日志次数，直接返回false不打印日志
        if (connectFailCount > MAX_FAIL_LOG_COUNT && !hasReachedMaxLog) {
            // 只在第一次达到最大次数时打印一条提示
            Log.e(Log.TAG, "连接失败次数已达到" + MAX_FAIL_LOG_COUNT + "次，将不再打印连接失败日志");
            hasReachedMaxLog = true;
            return false;
        }

        try {
            if (isUDP) {
                if (udpSocket == null) {
                    udpSocket = new DatagramSocket();
                    livePacket = new DatagramPacket(new byte[MAX_UDP_PACK_SIZE], MAX_UDP_PACK_SIZE, address);
                    Log.d(Log.TAG, "开启UDP连接成功: " + address.getAddress() + ":" + address.getPort() + ", 本地端口: " + udpSocket.getLocalPort());
                    // 连接成功时重置计数器
                    connectFailCount = 0;
                    hasReachedMaxLog = false;
                }
            } else if (bio || is6735) { // blocked socket
                if (tcpSock == null || tcpSock.isClosed() || !tcpSock.isConnected()) {
                    tcpSock = new Socket();
                    tcpSock.connect(address, 3 * 1000);
                    tcpSock.setSoLinger(true, 1000);
                    Log.d(Log.TAG, "开启阻塞TCP连接成功: " + address.getAddress() + ":" + address.getPort() + ", 本地端口: " + tcpSock.getLocalPort());
                    dos = new DataOutputStream(tcpSock.getOutputStream());
                    // 连接成功时重置计数器
                    connectFailCount = 0;
                    hasReachedMaxLog = false;
                }
            } else { // unblocked socket
                if (tcpSocketChannel == null || !tcpSocketChannel.isConnected()) {
                    tcpSocketChannel = SocketChannel.open(address);
                    tcpSocketChannel.configureBlocking(false);
                    tcpSocketChannel.setOption(StandardSocketOptions.SO_LINGER, 0);
                    socketWriteBuffer.clear();
                    Log.d(Log.TAG, "开启非阻塞TCP连接: " + (tcpSocketChannel.isConnected() ? "成功" : "失败") +
                            address.getAddress() + ":" + address.getPort() + ", " +
                            "本地端口: " + tcpSocketChannel.getLocalAddress());
                    // 连接成功时重置计数器
                    connectFailCount = 0;
                    hasReachedMaxLog = false;
                }
            }
            return true;
        } catch (Exception e) {
            connectFailCount++;
            if (connectFailCount <= MAX_FAIL_LOG_COUNT) {
                Log.e(Log.TAG, "开启" + (isUDP ? "UDP" : "TCP") + "失败: " +
                        address.getAddress() + ":" + address.getPort() + " => " + e.getMessage());
            }

            return false;
        }
    }



    public void close() {
        run = false;
        doClose();
    }

    ///////////
    private void doClose() {
        Log.w(Log.TAG, "关闭连接");
        if (dos != null) {
            try {
                dos.close();
            } catch (IOException e) {
            }
            dos = null;
        }
        if (tcpSock != null) {
            try {
                tcpSock.close();
            } catch (IOException e) {
            }
            tcpSock = null;
        }
        if (tcpSocketChannel != null) {
            try {
                tcpSocketChannel.close();
            } catch (Exception e) {

            }
        }

        if (udpSocket != null) {
            udpSocket.close();
            udpSocket = null;
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.N)
    public void sendPack(final byte[] data) {
        if (!run) return;

        this.sendHandler.post(() -> {
//            int seq = (data[3] & 0xff) | ((data[2] & 0xff) << 8);
//            Log.d(Log.TAG,"RTP Seq: " + seq + " 长度: " + data.length);

            open();

            try {
                if (isUDP && udpSocket != null) {
                    livePacket.setData(data);
                    livePacket.setLength(data.length);
                    udpSocket.send(livePacket);
                } else if (bio || is6735) {
                    if (dos != null && tcpSock != null && tcpSock.isConnected()) {

                        dos.writeShort(data.length);
                        dos.write(data);
                        dos.flush();

                    }
                } else {
                    if (socketWriteBuffer != null && tcpSocketChannel != null && tcpSocketChannel.isConnected()) {
                        // Log.i(Log.TAG, "发送RTP包: " + data.length + " 字节");
                        socketWriteBuffer.putShort((short) data.length);
                        socketWriteBuffer.put(data);
                        socketWriteBuffer.flip();
                        tcpSocketChannel.write(socketWriteBuffer);
                        socketWriteBuffer.clear();
                    }
                }
            } catch (Exception e) {
                Log.e(Log.TAG, "发送异常，关闭后重连: " + e.getMessage());
                doClose();
            }
        });
    }
}
