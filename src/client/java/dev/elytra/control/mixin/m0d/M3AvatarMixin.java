package dev.elytra.control.mixin.m0d;
import dev.elytra.control.client.flight.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** 所有身体/装备层共用此父变换；名称/阴影在原版外层，不旋转AABB。 */
@Mixin(AvatarRenderer.class)
public class M3AvatarMixin {
 @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",at=@At("TAIL"))
 private void efc$extract(Avatar entity,AvatarRenderState state,float partial,CallbackInfo ci){
  var q=DisplayPose.worldExtract()&&Minecraft.getInstance().gui.screen()==null?DisplayPose.pose(entity,partial):null;((AvatarPoseAccess)state).efc$pose(q);
  if(q!=null){state.xRot=state.yRot=state.bodyRot=0;state.shouldApplyFlyingYRot=false;}
 }
 @Inject(method="setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",at=@At("HEAD"),cancellable=true)
 private void efc$rotate(AvatarRenderState state,PoseStack pose,float bodyRot,float scale,CallbackInfo ci){
  var q=((AvatarPoseAccess)state).efc$pose();if(q==null)return;
  pose.translate(0,.3/scale,0);pose.mulPose(FlightView.joml(q));pose.mulPose(Axis.YP.rotationDegrees(180));pose.mulPose(Axis.XP.rotationDegrees(-90));pose.translate(0,-.75,0);ci.cancel();
 }
}
