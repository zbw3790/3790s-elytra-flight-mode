package dev.elytra.control.mixin.m0d;

import dev.elytra.control.client.probe.m0d.M0DClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class M0DLocalPlayerMixin {
    @Inject(method = "tick()V", at = @At("HEAD"), require = 1)
    private void efc$m0dBeforeTick(CallbackInfo ci) { M0DClient.beforePlayerTick((LocalPlayer) (Object) this); }

    @Inject(method = "tick()V", at = @At("RETURN"), require = 1)
    private void efc$m0dAfterTick(CallbackInfo ci) { M0DClient.afterPlayerTick((LocalPlayer) (Object) this); }
}
