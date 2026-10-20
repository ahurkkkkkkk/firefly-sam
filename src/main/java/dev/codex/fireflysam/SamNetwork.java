package dev.codex.fireflysam;

import dev.codex.fireflysam.client.SamClientState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class SamNetwork {
    private SamNetwork() {}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Action.TYPE, Action.CODEC, (packet, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (packet.action == 0) SamCombat.toggle(player);
                if (packet.action == 1) SamCombat.cast(player);
            }
        }));
        registrar.playToClient(State.TYPE, State.CODEC, (packet, context) -> context.enqueueWork(() -> SamClientState.accept(packet)));
        registrar.playToClient(Toughness.TYPE, Toughness.CODEC, (packet, context) -> context.enqueueWork(() -> SamClientState.accept(packet)));
        registrar.playToClient(Impact.TYPE, Impact.CODEC, (packet, context) -> context.enqueueWork(() -> SamClientState.impact(packet)));
    }
    public record Action(int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(FireflySam.id("action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((b,p) -> b.writeByte(p.action), b -> new Action(b.readUnsignedByte()));
        @Override public Type<Action> type() { return TYPE; }
    }
    public record State(int entityId, boolean active, int combo, long lastHit, long transformStart,
                        long ultimateStart, Vec3 target) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(FireflySam.id("state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((b,p) -> {
            b.writeVarInt(p.entityId); b.writeBoolean(p.active); b.writeVarInt(p.combo);
            b.writeLong(p.lastHit); b.writeLong(p.transformStart); b.writeLong(p.ultimateStart);
            b.writeDouble(p.target.x); b.writeDouble(p.target.y); b.writeDouble(p.target.z);
        }, b -> new State(b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readLong(), b.readLong(), b.readLong(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble())));
        @Override public Type<State> type() { return TYPE; }
    }
    public record Toughness(int entityId, float remaining, float maximum, long brokenUntil, long hitTick) implements CustomPacketPayload {
        public static final Type<Toughness> TYPE = new Type<>(FireflySam.id("toughness"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Toughness> CODEC = StreamCodec.of((b,p) -> {
            b.writeVarInt(p.entityId); b.writeFloat(p.remaining); b.writeFloat(p.maximum); b.writeLong(p.brokenUntil); b.writeLong(p.hitTick);
        }, b -> new Toughness(b.readVarInt(), b.readFloat(), b.readFloat(), b.readLong(), b.readLong()));
        @Override public Type<Toughness> type() { return TYPE; }
    }
    public record Impact(Vec3 target, float radius, int kind, float damage) implements CustomPacketPayload {
        public static final Type<Impact> TYPE = new Type<>(FireflySam.id("impact"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Impact> CODEC = StreamCodec.of((b,p) -> {
            b.writeDouble(p.target.x); b.writeDouble(p.target.y); b.writeDouble(p.target.z);
            b.writeFloat(p.radius); b.writeVarInt(p.kind); b.writeFloat(p.damage);
        }, b -> new Impact(new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat(), b.readVarInt(), b.readFloat()));
        @Override public Type<Impact> type() { return TYPE; }
    }
}
