package dev.elytra.control.mixin.m0d;

import dev.elytra.control.probe.m0d.M0DServer;
import dev.elytra.control.probe.m0d.M0DTrace;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class M0DServerPlayerMixin {
    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"), require = 1)
    private void efc$m0dDeath(DamageSource source, CallbackInfo ci) { M0DServer.invalidate((ServerPlayer) (Object) this, "死亡先行失效"); }
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"), require = 1)
    private void efc$m0dWorldTransition(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) { M0DServer.invalidate((ServerPlayer) (Object) this, "换世界或实体前失效"); }
    @Inject(method = "checkMovementStatistics(DDD)V", at = @At("HEAD"), require = 1)
    private void efc$m0dStatistics(double x, double y, double z, CallbackInfo ci) { M0DTrace.event((ServerPlayer) (Object) this, "原版运动统计调用", java.util.Map.of("统计位移", new double[] {x, y, z})); }
}
