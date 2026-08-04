package com.autocommand;

import java.util.List;
import java.util.Map;

/**
 * TCP 信号包数据模型，与 外部信号源 协议定稿对齐（2026-08-01，含回执扩展）。
 * 一行一个包（\n 分隔），UTF-8 JSON。
 */
public final class Packet {

    /** 包分类：interact（高频攒批）/ gift（即时），模组端仅作参考，不强制 */
    public String type;

    /** 毫秒时间戳，对账键（包级） */
    public long timestamp;

    /** 触发器数组，实时突发时可含多条，模组端排队执行 */
    public List<Signal> signals;

    /** 单个触发器 */
    public static final class Signal {
        /** 包内序号 1..n（外部信号源 每次发包从 1 重新编号）；模组端不生成不校验，回执原样回填 */
        public Integer sid;
        /** 要触发的绑定名称（唯一匹配字段） */
        public String id;
        /** 变量替换值，如 {"username": "张三"} */
        public Map<String, String> params;
    }

    /**
     * 回执条目来源信息：由 TCP 包携带，执行失败时用于回执对账。
     * 手动按键 / 服务端指令触发无来源（null），不回执。
     */
    public record ReceiptInfo(long packetTimestamp, int sid) {
    }
}
