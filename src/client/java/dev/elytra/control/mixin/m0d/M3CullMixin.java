package dev.elytra.control.mixin.m0d;
import dev.elytra.control.client.flight.DisplayPose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntityRenderer.class)
public class M3CullMixin {
 @Inject(method="getBoundingBoxForCulling",at=@At("RETURN"),cancellable=true)
 private void efc$cull(Entity e,CallbackInfoReturnable<AABB> ci){if(DisplayPose.pose(e,1)!=null)ci.setReturnValue(ci.getReturnValue().inflate(2));}
}
