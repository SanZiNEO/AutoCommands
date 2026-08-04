package com.autocommand;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 回执模型（模组 → 外部信号源，同连接写回，一行一个 JSON）。
 *
 * 协议定稿（2026-08-01）：
 * - queued 必回：收到包入队后立即回
 * - error 仅失败时回：未匹配绑定（入队时判定）/ 指令执行异常（执行时补发）
 * - 成功不回终态
 * - 对账键：packet_timestamp（原样回填）+ receipts[].sid（原样回填）
 */
public final class Receipt {

    public String type = "receipt";

    /** 模组端发送回执时的毫秒时间戳 */
    public long timestamp;

    /** 原样回填收到的包 timestamp（对账键，包级） */
    @SerializedName("packet_timestamp")
    public long packetTimestamp;

    /** 回执整体状态：queued（全部入队）/ error（存在失败条目） */
    public String status;

    /** 逐条状态，与 signals[] 一一对应 */
    public List<Entry> receipts = new ArrayList<>();

    /** 发送时刻队列积压量（丢包/积压诊断） */
    @SerializedName("queue_size")
    public int queueSize;

    /** 模组端收到该包的毫秒时间戳 */
    @SerializedName("received_at")
    public long receivedAt;

    /** 单条回执 */
    public static final class Entry {
        /** 原样回填包内 sid */
        public Integer sid;
        /** queued / error */
        public String status;
        /** error 描述：unmatched id / command failed */
        public String error;

        public Entry() {
        }

        public Entry(Integer sid, String status, String error) {
            this.sid = sid;
            this.status = status;
            this.error = error;
        }
    }

    public static Receipt queued(long packetTimestamp, long receivedAt) {
        Receipt r = new Receipt();
        r.timestamp = System.currentTimeMillis();
        r.packetTimestamp = packetTimestamp;
        r.status = "queued";
        r.queueSize = ExecutionScheduler.get().queueSize();
        r.receivedAt = receivedAt;
        return r;
    }

    public static Receipt error(long packetTimestamp, long receivedAt) {
        Receipt r = queued(packetTimestamp, receivedAt);
        r.status = "error";
        return r;
    }
}
