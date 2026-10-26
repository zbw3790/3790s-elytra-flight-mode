package dev.elytra.control.mixin.m0d;
import dev.elytra.control.client.flight.AvatarPoseAccess;
import dev.elytra.control.flight.FlightModel.Q;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.*;
@Mixin(AvatarRenderState.class)
public class M3AvatarStateMixin implements AvatarPoseAccess {
 @Unique private Q efc$pose;
 public Q efc$pose(){return efc$pose;}public void efc$pose(Q q){efc$pose=q;}
}
