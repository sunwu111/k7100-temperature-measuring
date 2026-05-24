package com.zhjinrui.netty;

import android.os.Handler;
import android.os.HandlerThread;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import hikvision.zhanyun.com.hikvision.utils.Log;

public class UdpSend {
    public DatagramSocket socket;
    public InetAddress address;
    private static final int MAX_PACKET_SIZE = 1400; // UDP packet max size (consider network MTU)
    private static final String TAG = "UdpH264Sender";
    private HandlerThread sendThread;
    private Handler sendHandler;

    private String ip;

    public UdpSend(String ip) {
        try {
            socket = new DatagramSocket();
            this.ip = ip;
            address = InetAddress.getByName(ip);
            if (sendThread == null) sendThread = new HandlerThread("udp数据发送线程");
            if (sendHandler == null) {
                sendThread.start();
                sendHandler = new Handler(sendThread.getLooper());
            }
        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        }
    }

    private static UdpSend udpSend;

    public static UdpSend getInstance(String ip) {
        if (udpSend == null) {
            udpSend = new UdpSend(ip);
        } else {
            if (!ip.equals(udpSend.ip)) {
                udpSend = new UdpSend(ip);
            }
        }
        return udpSend;
    }

    public void sendPack(byte[] data) {
        if (ip != null) {
            sendHandler.post(() -> sendH264Data(data));
        }
    }

    public void sendH264Data(byte[] data) {
        try {
            int offset = 0;
            while (offset < data.length) {
                int packetSize = Math.min(MAX_PACKET_SIZE, data.length - offset);
                DatagramPacket packet = new DatagramPacket(data, offset, packetSize, address, 5000);
                socket.send(packet);
                offset += packetSize;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending H.264 data" + e.getMessage());
        }
    }
}
