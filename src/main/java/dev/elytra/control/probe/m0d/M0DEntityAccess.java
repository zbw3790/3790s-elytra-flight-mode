package dev.elytra.control.probe.m0d;

/** 不修改粘滞、速度因子和反弹，仅用于拒绝未验证的实验包络。 */
public interface M0DEntityAccess {
    boolean efc$m0dPlainMovement();
}
