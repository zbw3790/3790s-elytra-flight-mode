package dev.elytra.control.mixin.m0d;
import dev.elytra.control.client.flight.FlightControls;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class M1InputMixin {
    @Inject(method="handleKeybinds()V",at=@At("HEAD"),require=1)
    private void efc$m1Conflicts(CallbackInfo ci){FlightControls.suppressVanilla();}
}
