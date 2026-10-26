package dev.elytra.control.mixin.m0d;

import dev.elytra.control.probe.m0d.M0DServer;
import dev.elytra.control.probe.m0d.M0DProtocol;
import dev.elytra.control.probe.m0d.M0DTrace;
import dev.elytra.control.probe.m0d.M0DEntityAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class M0DEntityMixin implements M0DEntityAccess {
    @Shadow protected Vec3 stuckSpeedMultiplier;
    @Shadow protected abstract float getBlockSpeedFactor();
    @Shadow protected abstract double getEntityBounciness();
    @Override public boolean efc$m0dPlainMovement() {
        return stuckSpeedMultiplier.lengthSqr() <= 1.0E-7 && getBlockSpeedFactor() == 1.0F && getEntityBounciness() == 0.0;
    }
    /** 仅原生push(DDD)的实际写入；自身公式/碰撞setter不经过本入口。 */
    @Redirect(method = "push(DDD)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"), require = 1)
    private void efc$m0dPushWrite(Entity entity, Vec3 velocity) {
        if (!M0DProtocol.suppressImpulse(entity, velocity, "Entity.push(DDD)")) entity.setDeltaMovement(velocity);
    }

    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), require = 1)
    private void efc$m0dMoveStart(MoverType type, Vec3 request, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) M0DServer.beforeMove(player, type, request);
    }
    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("RETURN"), require = 1)
    private void efc$m0dMoveEnd(MoverType type, Vec3 request, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) M0DServer.afterMove(player, type);
    }
    @Inject(method = "applyEffectsFromBlocks()V", at = @At("HEAD"), require = 1)
    private void efc$m0dBlocksStart(CallbackInfo ci) { M0DTrace.event((Entity) (Object) this, "原版方块效果消费开始"); }
    @Inject(method = "applyEffectsFromBlocks()V", at = @At("RETURN"), require = 1)
    private void efc$m0dBlocksEnd(CallbackInfo ci) { M0DTrace.event((Entity) (Object) this, "原版方块效果消费结束"); }
    @Inject(method = "doCheckFallDamage(DDDZ)V", at = @At("HEAD"), require = 1)
    private void efc$m0dFall(double x, double y, double z, boolean ground, CallbackInfo ci) {
        M0DTrace.event((Entity) (Object) this, "原版摔落记账调用", java.util.Map.of("实际位移", new double[] {x, y, z}, "记账地面", ground));
    }
}
