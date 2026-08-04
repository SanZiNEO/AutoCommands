package com.autocommand;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TCP 服务器：监听 8888（可配），接收 外部信号源 信号包。
 *
 * 协议要点（与 外部信号源 定稿对齐）：
 * - 一行一个 JSON 包（\n 分隔）；readLine 天然处理半包/多包切包问题
 * - UTF-8 中文直出
 * - 一个包可含多条 signals，全部入队执行，不丢
 * - 外部信号源 断线重连是常态：新连接到来时替换并关闭旧连接
 * - 单包解析失败记日志跳过，不影响后续包
 */
public final class NetworkServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("autocommand.NetworkServer");
    private static final Gson GSON = new Gson();

    private static final NetworkServer INSTANCE = new NetworkServer();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Socket> activeClient = new AtomicReference<>();
    private final Object listenLock = new Object();

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile int port;
    private volatile String lastError = "";

    /** 回执写回锁（reader 线程与服务端 tick 线程并发写同一 socket） */
    private final Object writeLock = new Object();

    public static NetworkServer get() {
        return INSTANCE;
    }

    private NetworkServer() {
    }

    /** 启动监听线程；已在运行时忽略。 */
    public void start(int port) {
        synchronized (listenLock) {
            if (running.get()) {
                return;
            }
            this.port = port;
            lastError = "";
            acceptThread = new Thread(this::acceptLoop, "AutoCommand-TCP");
            acceptThread.setDaemon(true);
            acceptThread.start();
            // 等待 bind 结果（成功或失败），避免事件返回后状态未定
            try {
                listenLock.wait(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            LOGGER.info("TCP 服务器 {}", running.get() ? "已启动，端口 " + port : "启动失败: " + lastError);
        }
    }

    /** 停止监听并断开所有客户端连接。 */
    public void stop() {
        synchronized (listenLock) {
            if (!running.get() && serverSocket == null) {
                return;
            }
            running.set(false);
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                LOGGER.warn("关闭监听 socket 异常", e);
            }
            serverSocket = null;
            closeClient();
            if (acceptThread != null) {
                acceptThread.interrupt();
                acceptThread = null;
            }
            LOGGER.info("TCP 服务器已停止");
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        return port;
    }

    public String getLastError() {
        return lastError;
    }

    /** 当前连接数（0 或 1，单连接设计） */
    public int connectedCount() {
        Socket s = activeClient.get();
        return s != null && s.isConnected() && !s.isClosed() ? 1 : 0;
    }

    // ============ 内部 ============

    private void acceptLoop() {
        try (ServerSocket socket = new ServerSocket(port)) {
            serverSocket = socket;
            running.set(true);
            synchronized (listenLock) {
                listenLock.notifyAll();
            }
            LOGGER.info("AutoCommand TCP 服务器监听端口 {}", port);
            while (running.get() && !socket.isClosed()) {
                Socket client;
                try {
                    client = socket.accept();
                } catch (SocketException e) {
                    break; // 服务器关闭
                }
                handleNewConnection(client);
            }
        } catch (IOException e) {
            lastError = e.getMessage();
            LOGGER.error("TCP 服务器启动失败（端口 {} 可能被占用）: {}", port, e.getMessage());
        } finally {
            running.set(false);
            serverSocket = null;
            synchronized (listenLock) {
                listenLock.notifyAll();
            }
        }
    }

    /** 新连接替换旧连接（外部信号源 断线重连常态，旧资源直接清掉） */
    private void handleNewConnection(Socket client) {
        Socket old = activeClient.getAndSet(client);
        if (old != null && !old.isClosed()) {
            try {
                old.close();
                LOGGER.info("旧连接被新连接替换");
            } catch (IOException ignored) {
            }
        }
        LOGGER.info("收到新连接: {}", client.getRemoteSocketAddress());
        Thread reader = new Thread(() -> readLoop(client), "AutoCommand-TCP-Reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** 逐行读取 = 逐包解析（BufferedReader 内部缓冲，天然处理半包/多包） */
    private void readLoop(Socket client) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                handlePacket(line);
            }
        } catch (SocketException e) {
            LOGGER.info("连接已断开: {}", client.getRemoteSocketAddress());
        } catch (IOException e) {
            if (running.get()) {
                LOGGER.warn("读取连接数据异常: {}", e.getMessage());
            }
        } finally {
            if (activeClient.compareAndSet(client, null)) {
                try {
                    client.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void handlePacket(String line) {
        Packet packet;
        try {
            packet = GSON.fromJson(line, Packet.class);
        } catch (JsonSyntaxException e) {
            LOGGER.warn("数据包解析失败，已跳过: {}", truncate(line));
            return;
        }
        if (packet == null || packet.signals == null || packet.signals.isEmpty()) {
            return; // 空 signals 防御性忽略
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            LOGGER.warn("服务器未就绪，丢弃 {} 条信号", packet.signals.size());
            return;
        }

        long receivedAt = System.currentTimeMillis();
        Receipt receipt = Receipt.queued(packet.timestamp, receivedAt);
        for (Packet.Signal signal : packet.signals) {
            if (signal == null || signal.id == null || signal.id.isBlank()) {
                receipt.receipts.add(new Receipt.Entry(signal != null ? signal.sid : null, "error", "invalid signal"));
                receipt.status = "error";
                continue;
            }
            Map<String, String> params = signal.params != null ? signal.params : Map.of();
            Packet.ReceiptInfo info = signal.sid != null ? new Packet.ReceiptInfo(packet.timestamp, signal.sid) : null;
            boolean hit = ExecutionScheduler.get().trigger(server, signal.id, params, null, info);
            if (hit) {
                receipt.receipts.add(new Receipt.Entry(signal.sid, "queued", null));
            } else {
                LOGGER.debug("数据包信号未命中绑定: {}", signal.id);
                receipt.receipts.add(new Receipt.Entry(signal.sid, "error", "unmatched id: " + signal.id));
                receipt.status = "error";
            }
        }
        // queued 必回（未匹配的条目在同一回执内标 error；一包一回执）
        sendReceipt(receipt);
    }

    /**
     * 同连接写回回执（一行一个 JSON）。
     * 写锁保证多线程（reader 线程 / 服务端 tick 线程）并发写安全；
     * 写失败直接丢弃（实时性优先，不重发）。
     */
    public void sendReceipt(Receipt receipt) {
        Socket client = activeClient.get();
        if (client == null || client.isClosed()) {
            return;
        }
        synchronized (writeLock) {
            try {
                OutputStream out = client.getOutputStream();
                out.write(GSON.toJson(receipt).getBytes(StandardCharsets.UTF_8));
                out.write('\n');
                out.flush();
            } catch (IOException e) {
                LOGGER.warn("回执发送失败（连接可能已断开）: {}", e.getMessage());
            }
        }
    }

    /** 指令执行失败补发 error 回执（仅失败条目，协议定稿） */
    public void sendErrorReceipt(Packet.ReceiptInfo receipt, String error) {
        Receipt r = Receipt.error(receipt.packetTimestamp(), System.currentTimeMillis());
        r.receipts.add(new Receipt.Entry(receipt.sid(), "error", error));
        sendReceipt(r);
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    private void closeClient() {
        Socket s = activeClient.getAndSet(null);
        if (s != null && !s.isClosed()) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }
}
