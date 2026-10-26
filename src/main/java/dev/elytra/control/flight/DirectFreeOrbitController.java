package dev.elytra.control.flight;

import dev.elytra.control.flight.FlightModel.*;

/** V直接轨道。yaw无止点，signed pitch保留越极点分支；没有独立roll或追赶状态。 */
public final class DirectFreeOrbitController {
    public static final double PITCH_LIMIT=Math.PI, SENSITIVITY=Math.PI/1200;
    private double yaw,pitch,radius;
    public void enter(V offset) {
        radius=offset.length();if(radius<.1)radius=Math.hypot(6,CameraGeometry.HEIGHT);
        V f=offset.length()<.1?new V(0,0,1):offset.mul(-1).unit();
        yaw=Math.atan2(f.x(),f.z());pitch=Math.atan2(-f.y(),Math.hypot(f.x(),f.z()));
    }
    public void mouse(double dx,double dy) {
        if(!Double.isFinite(dx)||!Double.isFinite(dy))throw new IllegalArgumentException("鼠标非有限");
        yaw=Math.IEEEremainder(yaw-Math.IEEEremainder(dx*.15,360)*Math.PI/180,2*Math.PI);
        // 大幅单次输入也必须达到止点，不能把纵向delta取模后绕回。
        double delta=Math.clamp(dy,-2400,2400)*SENSITIVITY;
        pitch=Math.clamp(pitch+delta,-PITCH_LIMIT,PITCH_LIMIT);
    }
    public Q basis() { return Q.axis(1,yaw).times(Q.axis(0,pitch)).normalized(); }
    public V offset() { return basis().rotate(new V(0,0,-radius)); }
    public V up() { return basis().rotate(new V(0,1,0)); }
    public double pitch() { return pitch; }
    public double yaw() { return yaw; }
    public double radius() { return radius; }
}
