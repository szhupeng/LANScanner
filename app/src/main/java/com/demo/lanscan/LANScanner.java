package com.demo.lanscan;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.annotation.RequiresPermission;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static android.content.Context.CONNECTIVITY_SERVICE;

public class LANScanner {

    /**
     * 核心池大小
     **/
    private static final int CORE_POOL_SIZE = 1;
    /**
     * 线程池最大线程数
     **/
    private static final int MAX_POOL_SIZE = 255;
    private String mLocalIpAddress; // 本机IP地址
    private String mNetworkSegment; // 局域网IP地址头,如:192.168.1.
    private static final String PING = "/system/bin/ping -c 1 -w 3 %s";// 其中 -c 1为发送的次数,-w 表示发送后等待响应的时间
    private ThreadPoolExecutor mExecutor;// 线程池对象

    public interface OnScanListener {
        void onFound(Device device);

        void onFinished();
    }

    private LANScanner() {
    }

    public static LANScanner get() {
        return new LANScanner();
    }

    public void scan(final OnScanListener listener) {
        this.mLocalIpAddress = getLocalIPAddress();
        this.mNetworkSegment = getNetworkSegment(this.mLocalIpAddress);
        if (TextUtils.isEmpty(this.mLocalIpAddress)) {
            return;
        }

        mExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, 2000, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(CORE_POOL_SIZE));
        String ip;
        for (int i = 1; i < 255; i++) {
            ip = mNetworkSegment.concat(Integer.toString(i));
            if (this.mLocalIpAddress.equals(ip)) continue;

            mExecutor.execute(new PingThread(ip, listener));
        }

        mExecutor.shutdown();

        try {
            while (true) {
                if (mExecutor.isTerminated()) {
                    if (listener != null) {
                        listener.onFinished();
                    }
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
    }

    public void stop() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
    }

    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    private static boolean isWifiAvailable(Context context) {
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return mWifi.isConnected() && mWifi.isAvailable();
    }

    public static String getLocalIPAddress() {
        String ip = null;
        try {
            Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
            // 遍历所用的网络接口
            while (enumeration.hasMoreElements()) {
                NetworkInterface networks = enumeration.nextElement();
                // 得到每一个网络接口绑定的所有ip
                Enumeration<InetAddress> addresses = networks.getInetAddresses();
                // 遍历每一个接口绑定的所有ip
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && (address instanceof Inet4Address)) {
                        ip = address.getHostAddress();
                    }
                }
            }

        } catch (Exception e) {
            ip = null;
        }
        return ip;
    }

    public static String getHostIPAddress() {
        String host = null;
        try {
            Enumeration enumeration = NetworkInterface.getNetworkInterfaces();
            InetAddress address = null;
            while (enumeration.hasMoreElements()) {
                NetworkInterface networks = (NetworkInterface) enumeration.nextElement();
                Enumeration<InetAddress> addresses = networks.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    address = addresses.nextElement();
                    if (address instanceof Inet6Address) {
                        continue;// skip ipv6
                    }
                    String ip = address.getHostAddress();
                    if (!"127.0.0.1".equals(ip)) {
                        host = address.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
        }
        return host;
    }

    /**
     * 获取本地mac地址
     *
     * @param context
     * @return
     */
    public static String getLocalMacAddress(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wifi.getConnectionInfo();
            return info.getMacAddress();
        } else {
            try {
                List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
                for (NetworkInterface nif : all) {
                    if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                    byte[] macBytes = nif.getHardwareAddress();
                    if (macBytes == null) {
                        return "";
                    }

                    StringBuilder sb = new StringBuilder();
                    for (byte b : macBytes) {
                        sb.append(String.format("%02X:", b));
                    }

                    if (sb.length() > 0) {
                        sb.deleteCharAt(sb.length() - 1);
                    }
                    return sb.toString();
                }
            } catch (Exception ex) {
            }
        }
        return "02:00:00:00:00:00";
    }

    /**
     * 获取本机IP前缀
     *
     * @param address
     * @return
     */
    private static String getNetworkSegment(String address) {
        if (!"".equals(address)) {
            return address.substring(0, address.lastIndexOf(".") + 1);
        }
        return null;
    }

    private static String readMacFromArp(final String ip) {
        if (null == ip) return null;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader("/proc/net/arp"));
            String line;
            String mac;
            while ((line = reader.readLine()) != null) {
                String[] splitted = line.split(" +");
                if (splitted != null && splitted.length >= 4 && ip.equals(splitted[0])) {
                    // Basic sanity check
                    mac = splitted[3];
                    if (mac.matches("..:..:..:..:..:..")) {
                        return mac;
                    } else {
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static class UDPThread implements Runnable {
        private final String mTargetIp;
        private final OnScanListener mListener;

        private static final byte[] BUF = {(byte) 0x82, (byte) 0x28, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x1,
                (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x20, (byte) 0x43, (byte) 0x4B,
                (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41,
                (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41,
                (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x41,
                (byte) 0x41, (byte) 0x41, (byte) 0x41, (byte) 0x0, (byte) 0x0, (byte) 0x21, (byte) 0x0, (byte) 0x1};

        private static final short PORT = 137;

        public UDPThread(final String ip, final OnScanListener listener) {
            this.mTargetIp = ip;
            this.mListener = listener;
        }

        @Override
        public synchronized void run() {
            if (mTargetIp == null || "".equals(mTargetIp)) return;

            DatagramSocket socket = null;
            InetAddress address = null;
            DatagramPacket packet = null;
            try {
                address = InetAddress.getByName(mTargetIp);
                packet = new DatagramPacket(BUF, BUF.length, address, PORT);
                socket = new DatagramSocket();
                socket.setSoTimeout(200);
                socket.send(packet);
                socket.close();
                if (mListener != null) {
                    mListener.onFound(new Device(mTargetIp, readMacFromArp(mTargetIp), address.getCanonicalHostName()));
                }
            } catch (SocketException se) {
                se.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    socket.close();
                }
            }
        }
    }

    public static class PingThread implements Runnable {

        private final String mTargetIp;
        private final OnScanListener mListener;

        public PingThread(final String ip, final OnScanListener listener) {
            this.mTargetIp = ip;
            this.mListener = listener;
        }

        @Override
        public void run() {
            Process exec = null;
            try {
                exec = Runtime.getRuntime().exec(String.format(PING, mTargetIp));
                int result = exec.waitFor();
                if (0 == result) {
                    InetAddress address = InetAddress.getByName(mTargetIp);
                    if (mListener != null) {
                        mListener.onFound(new Device(mTargetIp, readMacFromArp(mTargetIp), address.getCanonicalHostName()));
                    }
                } else {
                    throw new IOException("Unable to get ping from runtime");
                }
            } catch (IOException | InterruptedException e) {
                try {
                    InetAddress address = InetAddress.getByName(mTargetIp);
                    if (address.isReachable(4000)) {
                        if (mListener != null) {
                            mListener.onFound(new Device(mTargetIp, readMacFromArp(mTargetIp), address.getCanonicalHostName()));
                        }
                    }
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } finally {
                if (exec != null) {
                    exec.destroy();
                }
            }
        }
    }

    public static class Device {
        public String ip;
        public String mac;
        public String name;

        public Device(String ip, String mac, String name) {
            this.ip = ip;
            this.mac = mac;
            this.name = name;
        }
    }
}
