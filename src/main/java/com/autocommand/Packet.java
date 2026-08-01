package com.autocommand;

import java.util.List;
import java.util.Map;

/**
 * TCP 信号包数据模型，与 外部信号源 协议定稿对齐（2026-08-01）。
 * 一行一个包（\n 分隔），UTF-8 JSON。
 */
public final class Packet {

    /** 包分类：interact（高频攒批）/ gift（即时），模组端仅作参考，不强制 */
    public String type;

    /** 毫秒时间戳，可选时序校验 */
    public long timestamp;

    /** 触发器数组，实时突发时可含多条，模组端排队执行 */
    public List<Signal> signals;

    /** 单个触发器 */
    public static final class Signal {
        /** 要触发的绑定名称（唯一匹配字段） */
        public String id;
        /** 变量替换值，如 {"username": "张三"} */
        public Map<String, String> params;
    }
}
