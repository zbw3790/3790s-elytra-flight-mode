package dev.elytra.control.mixin.m0d;
import dev.elytra.control.client.flight.*;import dev.elytra.control.flight.*;import dev.elytra.control.flight.FlightModel.*;
import net.minecraft.client.*;import net.minecraft.world.phys.Vec3;import org.joml.Quaternionf;import org.joml.Vector3f;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** 原版align后局部求解；完整机位偏移调用原版八射线，再一次同步最终矩阵/方向。 */
@Mixin(Camera.class)
public abstract class M3CameraMixin {
 @Shadow @Final private Quaternionf rotation;@Shadow @Final private Vector3f forwards;@Shadow @Final private Vector3f up;@Shadow @Final private Vector3f left;
 @Shadow private int matrixPropertiesDirty;
 @Shadow protected abstract void setRotation(float yaw,float pitch);@Shadow protected abstract void setPosition(Vec3 p);
 @Shadow private float getMaxZoom(float distance){throw new AssertionError();}
 @Unique private void efc$rotation(Q view){V f=view.rotate(new V(0,0,1));setRotation((float)Math.toDegrees(Math.atan2(-f.x(),f.z())),(float)Math.toDegrees(-Math.asin(Math.clamp(f.y(),-1,1))));rotation.set(FlightView.joml(view.times(Q.axis(1,Math.PI))));new Vector3f(0,0,-1).rotate(rotation,forwards);new Vector3f(0,1,0).rotate(rotation,up);new Vector3f(-1,0,0).rotate(rotation,left);matrixPropertiesDirty|=3;}
 @Inject(method="alignWithEntity",at=@At("TAIL"))
 private void efc$camera(float partial,CallbackInfo ci){var mc=Minecraft.getInstance();var camera=(Camera)(Object)this;if(!DisplayPose.local()||camera!=mc.gameRenderer.mainCamera()||camera.entity()!=mc.player)return;
  Q view=FlightView.orientation(partial);if(view==null)return;boolean first=mc.options.getCameraType().isFirstPerson(),rear=!first&&!mc.options.getCameraType().isMirrored();
  Vec3 anchor=mc.player.getPosition(partial).add(0,.3,0);V wanted=first?new V(0,0,0):rear?FlightView.offset(partial):FlightView.mirrorOffset(partial);V actual=wanted;setPosition(anchor);
  double length=wanted.length();if(length>1e-8){
   // 原版getMaxZoom使用forwards与±.1立方体的八条短射线。临时朝完整偏移的反方向，最终旋转随后恢复。
   efc$rotation(DisplayMath.look(wanted.mul(-1),FlightView.up(partial)));double zoom=getMaxZoom((float)length);if(zoom<length-1e-5)actual=wanted.mul(Math.max(0,zoom-.08)/length);
  }
  if(!first)view=FlightView.resolved(actual,partial,!rear);
  efc$rotation(view);setPosition(anchor.add(actual.x(),actual.y(),actual.z()));matrixPropertiesDirty|=3;
  FlightView.frame(new FlightView.Frame(new V(anchor.x,anchor.y,anchor.z),new V(anchor.x+actual.x(),anchor.y+actual.y(),anchor.z+actual.z()),wanted,actual,view,rear,FlightView.free(),new V(anchor.x,anchor.y,anchor.z).add(FlightView.opticalPoint(view,actual,partial,!rear)),FlightView.transitioning()));
 }
}
