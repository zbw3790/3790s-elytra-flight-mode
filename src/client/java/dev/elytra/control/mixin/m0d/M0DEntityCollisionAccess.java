package dev.elytra.control.mixin.m0d;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 预测重放只调用原版碰撞求解，不重复移动或方块效果。 */
@Mixin(Entity.class)
public interface M0DEntityCollisionAccess {
    @Invoker("collide") Vec3 efc$m0dCollide(Vec3 movement);
}
