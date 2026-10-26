package dev.elytra.control.mixin.m0d;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 调用游戏/Fabric自身的只读合法性查询，不定义装备来源，不运行耐久tick。 */
@Mixin(LivingEntity.class)
public interface M0DGlideAccess {
    @Invoker("canGlide") boolean efc$m4wCanGlide();
}
