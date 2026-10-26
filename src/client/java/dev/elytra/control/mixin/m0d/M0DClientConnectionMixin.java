package dev.elytra.control.mixin.m0d;

import dev.elytra.control.client.probe.m0d.M0DClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class M0DClientConnectionMixin {
    @Inject(method = "handleMovePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerPositionPacket;)V", at = @At("RETURN"), require = 1)
    private void efc$m0dAfterVanillaPosition(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        M0DClient.afterVanillaPosition(packet);
    }
}
