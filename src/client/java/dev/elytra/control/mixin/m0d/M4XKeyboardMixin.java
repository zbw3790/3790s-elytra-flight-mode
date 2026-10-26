package dev.elytra.control.mixin.m0d;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import dev.elytra.control.client.flight.FlightControls;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(KeyboardHandler.class)
public abstract class M4XKeyboardMixin {
 @Inject(method="keyPress",at=@At("HEAD"),require=1)
 private void efc$m4xRelease(long handle,int action,KeyEvent event,CallbackInfo ci){if(handle==Minecraft.getInstance().getWindow().handle())FlightControls.nativeKey(InputConstants.getKey(event),action);}
}
