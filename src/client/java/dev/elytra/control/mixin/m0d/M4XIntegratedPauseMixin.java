package dev.elytra.control.mixin.m0d;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.Minecraft;
import dev.elytra.control.flight.ControlClock;
import java.util.function.BooleanSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IntegratedServer.class)
public abstract class M4XIntegratedPauseMixin {
 @Inject(method="tickServer",at=@At("HEAD"),require=1)
 private void efc$m4xPause(BooleanSupplier haveTime,CallbackInfo ci){
  var server=(IntegratedServer)(Object)this;
  ControlClock.pause(server,Minecraft.getInstance().isPaused()||server.getPlayerList().getPlayers().isEmpty(),System.nanoTime());
 }
}
