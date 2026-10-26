package dev.elytra.control.mixin.m0d;
import dev.elytra.control.client.flight.DisplayPose;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
@Mixin(EntityRenderDispatcher.class)
public class M3WorldExtractMixin {
 @Inject(method="extractEntity",at=@At("HEAD"))private void efc$begin(CallbackInfoReturnable<EntityRenderState> ci){DisplayPose.beginExtract();}
 @Inject(method="extractEntity",at=@At("RETURN"))private void efc$end(CallbackInfoReturnable<EntityRenderState> ci){DisplayPose.endExtract();}
}
