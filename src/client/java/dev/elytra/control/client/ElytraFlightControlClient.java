package dev.elytra.control.client;
import net.fabricmc.api.ClientModInitializer;
public final class ElytraFlightControlClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        dev.elytra.control.client.probe.m0d.M0DClient.initialize();
        dev.elytra.control.client.flight.FlightControls.initialize();
        dev.elytra.control.client.flight.AimView.initialize();
        dev.elytra.control.client.flight.DisplayPose.initialize();
    }
}
