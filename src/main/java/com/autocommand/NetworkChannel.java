package com.autocommand;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Forge SimpleChannel：客户端 → 服务端的 C2S 触发包（手动按键模式）。
 * 按键按下 → 发送绑定 id → 服务端走与数据包模式同一执行管线。
 */
public final class NetworkChannel {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("autocommand", "main");
    private static final String PROTOCOL_VERSION = "1";

    private static SimpleChannel channel;

    private NetworkChannel() {
    }

    public static SimpleChannel get() {
        return channel;
    }

    /** 在 FMLCommonSetup 中调用（双端） */
    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(
                ID,
                () -> PROTOCOL_VERSION,
                s -> s.equals(PROTOCOL_VERSION) || NetworkRegistry.ACCEPTVANILLA.equals(s),
                s -> s.equals(PROTOCOL_VERSION) || NetworkRegistry.ACCEPTVANILLA.equals(s));
        channel.registerMessage(0, TriggerBindingPacket.class,
                TriggerBindingPacket::encode,
                TriggerBindingPacket::decode,
                TriggerBindingPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** C2S：手动触发指定绑定 */
    public static final class TriggerBindingPacket {
        public String bindingId;

        public TriggerBindingPacket() {
        }

        public TriggerBindingPacket(String bindingId) {
            this.bindingId = bindingId;
        }

        public static void encode(TriggerBindingPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.bindingId, 128);
        }

        public static TriggerBindingPacket decode(FriendlyByteBuf buf) {
            return new TriggerBindingPacket(buf.readUtf(128));
        }

        /** 服务端主线程执行（与 TCP 信号同一管线） */
        public static void handle(TriggerBindingPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                MinecraftServer server = ctx.getSender() != null ? ctx.getSender().getServer() : null;
                if (server == null) {
                    return;
                }
                String playerName = ctx.getSender() != null ? ctx.getSender().getName().getString() : null;
                ExecutionScheduler.get().trigger(server, msg.bindingId, Map.of(), playerName, null);
            });
            ctx.setPacketHandled(true);
        }
    }
}
