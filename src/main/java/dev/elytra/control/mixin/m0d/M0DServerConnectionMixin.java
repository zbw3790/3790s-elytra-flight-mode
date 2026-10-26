package dev.elytra.control.mixin.m0d;

import dev.elytra.control.probe.m0d.M0DConnectionAccess;
import dev.elytra.control.probe.m0d.M0DServer;
import dev.elytra.control.probe.m0d.M0DTrace;
import java.util.Set;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class M0DServerConnectionMixin implements M0DConnectionAccess {
    @Unique private ServerPlayer efc$m0dTickOwner;
    @Shadow public ServerPlayer player;
    @Shadow private double lastGoodX;
    @Shadow private double lastGoodY;
    @Shadow private double lastGoodZ;
    @Shadow private Vec3 awaitingPositionFromClient;
    @Shadow private int awaitingTeleport;
    @Shadow private boolean receivedMovementThisTick;
    @Shadow private boolean clientIsFloating;
    @Shadow protected abstract void handlePlayerKnownMovement(Vec3 value);

    @Override public boolean efc$m0dAwaiting() { return awaitingPositionFromClient != null; }
    @Override public int efc$m0dNextBridgeId() { int next = awaitingTeleport + 1; return next == Integer.MAX_VALUE ? 0 : next; }
    @Override public void efc$m0dLastGood(Vec3 value) { lastGoodX = value.x; lastGoodY = value.y; lastGoodZ = value.z; }
    @Override public void efc$m0dKnown(Vec3 value) { handlePlayerKnownMovement(value); }
    @Override public void efc$m0dGlidingFloating() { clientIsFloating = false; }

    @Inject(method = "tickPlayer()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;doTick()V"), require = 1)
    private void efc$m0dTickStart(CallbackInfoReturnable<Boolean> cir) { efc$m0dTickOwner = player; M0DServer.beginTick(player); }
    @Redirect(method = "tickPlayer()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;absSnapTo(DDDFF)V"), require = 1)
    private void efc$m0dRestore(ServerPlayer target, double x, double y, double z, float yaw, float pitch) {
        if (target != efc$m0dTickOwner && M0DServer.protectsTick(efc$m0dTickOwner)) {
            M0DServer.invalidate(efc$m0dTickOwner, "连接玩家实体已替换，旧恢复禁止");
            M0DTrace.event(target, "新实体优先，旧恢复禁止");
        }
        else if (!M0DServer.preserveAtRestore(target)) { target.absSnapTo(x, y, z, yaw, pitch); M0DTrace.event(target, "原版刻恢复已执行"); }
        else M0DTrace.event(target, "原版刻恢复已跳过");
    }
    @Inject(method = "tickPlayer()Z", at = @At("RETURN"), require = 1)
    private void efc$m0dTickEnd(CallbackInfoReturnable<Boolean> cir) { M0DServer.endTick(efc$m0dTickOwner == null ? player : efc$m0dTickOwner); efc$m0dTickOwner = null; }
    @Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), cancellable = true, require = 1)
    private void efc$m0dRawPacket(ServerboundMovePlayerPacket packet, CallbackInfo ci) { if (M0DServer.prePacket(player, packet)) ci.cancel(); }
    @Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getBoundingBox()Lnet/minecraft/world/phys/AABB;"), cancellable = true, require = 1)
    private void efc$m0dPacketBranch(ServerboundMovePlayerPacket packet, CallbackInfo ci) { if (M0DServer.isolatePacket(player, packet)) ci.cancel(); }
    @Inject(method = "handleClientTickEnd(Lnet/minecraft/network/protocol/game/ServerboundClientTickEndPacket;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER), require = 1)
    private void efc$m0dClientEnd(ServerboundClientTickEndPacket packet, CallbackInfo ci) { M0DTrace.event(player, "ClientTickEnd服务端处理"); }
    @Redirect(method = "handleClientTickEnd(Lnet/minecraft/network/protocol/game/ServerboundClientTickEndPacket;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setKnownMovement(Lnet/minecraft/world/phys/Vec3;)V"), require = 1)
    private void efc$m0dPreventKnownClear(ServerPlayer target, Vec3 zero) {
        if (!M0DServer.preserveKnown(target)) target.setKnownMovement(zero);
        else M0DTrace.event(target, "ClientTickEnd保留权威已知运动");
    }
    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("HEAD"), require = 1)
    private void efc$m0dTeleport(PositionMoveRotation destination, Set<Relative> relatives, CallbackInfo ci) { M0DServer.externalTeleport(player); }
    @Inject(method = "handleAcceptTeleportPacket(Lnet/minecraft/network/protocol/game/ServerboundAcceptTeleportationPacket;)V", at = @At("RETURN"), require = 1)
    private void efc$m0dTeleportAck(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) { M0DServer.teleportAck(player, packet.getId(), awaitingPositionFromClient != null); }
}
