package dev.elytra.control.mixin.m0d;
import dev.elytra.control.client.flight.DisplayPose;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public class M3GameRendererMixin {
 // 自有虚拟座舱不绘第一人称手/持物；物品与交互事实不变。避免渲染后额外摇摆使HUD/视锥不一致。
 @Inject(method={"renderItemInHand","bobView","bobHurt"},at=@At("HEAD"),cancellable=true)
 private void efc$local(CallbackInfo ci){if(DisplayPose.local())ci.cancel();}
}
