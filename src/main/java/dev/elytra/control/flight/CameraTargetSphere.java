package dev.elytra.control.flight;

import dev.elytra.control.flight.FlightModel.*;

/** 500格虚拟球仅几何求交；不查询世界/区块，不持有运动状态。坐标均相对机体锚点。 */
public final class CameraTargetSphere {
    public static final double RADIUS=500;
    private CameraTargetSphere(){}
    public static V intersect(V camera,V ray,V fallback) {
        V d=ray.unit();if(d.length()<.5)return safe(fallback);
        double b=camera.dot(d),c=camera.dot(camera)-RADIUS*RADIUS;
        double determinant=b*b-c;
        if(!Double.isFinite(determinant)||determinant<0)return safe(fallback);
        double t=-b+Math.sqrt(determinant);
        if(!Double.isFinite(t)||t<=0)return safe(fallback);
        return safe(camera.add(d.mul(t)));
    }
    private static V safe(V v){return v.length()<1e-9?new V(0,0,1):v.unit();}
    /** M4L输入参考位于球心：只累加已提交的球面相位，不读取实际相机或渲染帧。
     * 世界目标点仍为anchor + direction*RADIUS；帽内保留越极点相位，帽外采用等价正立输入坐标。 */
    public static Instructor.Target advance(Instructor.Target committed,double dx,double dy) {
        if(committed==null)throw new IllegalArgumentException("目标尚未初始化");
        if(dx==0&&dy==0)return committed;
        // normal帽外采用等价正立输入坐标，避免越顶相位把dy永久反号；
        // 帽内保留连续相位，允许真正过顶。沿用j1原2度退出边界，不改相机/阈值。
        if(Math.abs(Math.cos(Math.toRadians(committed.pitch())))>NormalChaseReference.EXIT)
            committed=new Instructor.Target(committed.canonicalYaw(),committed.canonicalPitch());
        return committed.mouse(dx,dy);
    }
    /** 历史j1显示射线映射，仅保留旧对照工具；M4L生产输入不调用。 */
    public static V mouse(V target,V camera,Q actualView,double dx,double dy) {
        if(!Double.isFinite(dx)||!Double.isFinite(dy))return safe(target);
        V ray=target.mul(RADIUS).add(camera.mul(-1)).unit();
        Q inverse=new Q(-actualView.x(),-actualView.y(),-actualView.z(),actualView.w()).normalized();
        Q delta=Q.axis(1,Math.toRadians(-Math.IEEEremainder(dx*.15,360)))
                .times(Q.axis(0,Math.toRadians(Math.IEEEremainder(dy*.15,360))));
        V changed=actualView.rotate(delta.rotate(inverse.rotate(ray)));
        return intersect(camera,changed,target);
    }
}
