package dev.elytra.control.mixin.m0d;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import com.mojang.blaze3d.platform.InputConstants;
import dev.elytra.control.client.flight.FlightControls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MouseHandler.class)
public abstract class M4XMouseButtonMixin {
 @Inject(method="onButton",at=@At("HEAD"),require=1)
 private void efc$m4xRelease(long handle,MouseButtonInfo button,int action,CallbackInfo ci){if(handle==Minecraft.getInstance().getWindow().handle())FlightControls.nativeKey(InputConstants.Type.MOUSE.getOrCreate(button.button()),action);}
}
