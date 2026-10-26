package dev.elytra.control.probe.m0d;

import net.minecraft.world.phys.Vec3;

/** 只向限定实验提供原连接字段及原方法的窄访问。 */
public interface M0DConnectionAccess {
    boolean efc$m0dAwaiting();
    int efc$m0dNextBridgeId();
    void efc$m0dLastGood(Vec3 value);
    void efc$m0dKnown(Vec3 value);
    void efc$m0dGlidingFloating();
}
