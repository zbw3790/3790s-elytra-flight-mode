package dev.elytra.control.mixin.m0d;

import dev.elytra.control.probe.m0d.M0DProtocol;
import dev.elytra.control.probe.m0d.M0DServer;
import dev.elytra.control.probe.m0d.M0DTrace;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class M0DLivingEntityMixin {
    /** 仅屏蔽持权目标的原生击退写速，伤害及其他原版副作用继续。 */
    @Redirect(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V"), require = 1)
    private void efc$m0dKnockbackWrite(LivingEntity entity, double x, double y, double z) {
        if (!M0DProtocol.suppressImpulse(entity, new Vec3(x, y, z), "LivingEntity.knockback")) entity.setDeltaMovement(x, y, z);
    }

    @Inject(method = "updateFallFlyingMovement(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true, require = 1)
    private void efc$m0dFormula(Vec3 original, CallbackInfoReturnable<Vec3> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        Vec3 replacement = entity.level().isClientSide() ? M0DProtocol.clientReplacement(entity) : M0DServer.replacement(entity);
        if (replacement != null) cir.setReturnValue(replacement);
    }
    /** 包括Fabric的早返回：在整个原版滑翔更新结束后、任何travel之前复核。 */
    @Inject(method = "aiStep()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;updateFallFlying()V", shift = At.Shift.AFTER), require = 1)
    private void efc$m4wGlideValidated(CallbackInfo ci) {
        if ((Object) this instanceof net.minecraft.server.level.ServerPlayer player) M0DServer.afterGlideUpdate(player);
    }
    @Inject(method = "travelFallFlying(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), require = 1)
    private void efc$m0dGlideStart(Vec3 input, CallbackInfo ci) { M0DTrace.event((LivingEntity) (Object) this, "原版滑翔运动开始"); }
    @Inject(method = "travelFallFlying(Lnet/minecraft/world/phys/Vec3;)V", at = @At("RETURN"), require = 1)
    private void efc$m0dGlideEnd(Vec3 input, CallbackInfo ci) { M0DTrace.event((LivingEntity) (Object) this, "原版滑翔运动结束"); }
}
