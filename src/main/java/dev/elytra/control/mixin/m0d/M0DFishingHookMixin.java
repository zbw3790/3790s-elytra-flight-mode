package dev.elytra.control.mixin.m0d;

import dev.elytra.control.probe.m0d.M0DProtocol;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 只屏蔽持权目标的鱼钩拉动写速；保留完整收杆、移除与耐久处理。 */
@Mixin(FishingHook.class)
public abstract class M0DFishingHookMixin {
    @Redirect(method = "pullEntity(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"), require = 1, allow = 1)
    private void efc$m0dFishingPullWrite(Entity target, Vec3 velocity) {
        if (!M0DProtocol.suppressImpulse(target, velocity, "FishingHook.pullEntity")) target.setDeltaMovement(velocity);
    }
}
