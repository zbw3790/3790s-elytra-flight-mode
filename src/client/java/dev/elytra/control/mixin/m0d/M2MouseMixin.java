package dev.elytra.control.mixin.m0d;
import dev.elytra.control.client.flight.AimView;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(MouseHandler.class)
public abstract class M2MouseMixin {
 @Redirect(method="turnPlayer(D)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"),require=1)
 private void efc$m2Mouse(LocalPlayer player,double x,double y){if(!AimView.mouse(x,y))player.turn(x,y);}
}
