package dev.elytra.control.flight;

import dev.elytra.control.flight.FlightModel.*;

/** 只读显示：已提交Mouse Aim目标驱动轨道，一层固定刻响应；最终光轴由实际机位看近机体Focus。 */
public final class NormalChaseController {
    private static final V Y=new V(0,1,0),Z=new V(0,0,1);
    public static final String CONFIG="M4L-AIM-2";
    /** 仅保留V单次光线交接的记录尺度，不是normal远端光轴控制器。 */
    public static final double OPTICAL_DISTANCE=12;
    public record Response(String name,double gain){
        public Response{if(name==null||!Double.isFinite(gain)||gain<=0||gain>1)throw new IllegalArgumentException("正常镜头响应参数越界");}
    }
    public static final Response AIM_DRIVEN=new Response("aim-driven",.45);
    private final Response response;private final NormalChaseReference reference=new NormalChaseReference();
    private Q before,now;private double radius,alpha;
    public NormalChaseController(){this(switch(System.getProperty("efc.view.normal.response","aim-driven")){case "aim-driven"->AIM_DRIVEN;default->throw new IllegalArgumentException("M4L仅接受aim-driven镜头身份，禁止混用旧responsive语义");});}
    public NormalChaseController(Response response){this.response=java.util.Objects.requireNonNull(response);}
    public record State(String config,Response response,NormalChaseReference.State reference,Q beforeBasis,Q basis,double radius){}
    public State state(){return new State(CONFIG,response,reference.state(),before,now,radius);}
    private void distance(double distance){if(!Double.isFinite(distance)||distance<=0)throw new IllegalArgumentException("正常镜头距离无效");radius=Math.hypot(distance,CameraGeometry.HEIGHT);alpha=Math.atan2(CameraGeometry.HEIGHT,distance);}
    public void reset(V committedTarget,double distance){distance(distance);reference.reset(committedTarget);before=now=reference.basis();}
    /** V中仅观察冻结的目标；不推进normal显示机位，不读取机体roll。 */
    public void observe(V committedTarget){reference.observe(committedTarget);}
    public void tick(V committedTarget,double distance){advance(committedTarget,distance,1);}
    void advance(V committedTarget,double distance,double tickDelta){
        distance(distance);reference.observe(committedTarget,tickDelta);before=now;now=DisplayMath.mix(before,reference.basis(),1-Math.pow(1-response.gain(),tickDelta));
    }
    /** 松V仅接续实际offset；同一层轨道响应恢复目标，不改变目标或自由控制器。 */
    public void take(V actual){
        if(actual==null||!Double.isFinite(actual.length())||actual.length()<1e-9)throw new IllegalArgumentException("正常交接offset无效");
        radius=actual.length();Q base=reference.basis();V from=base.rotate(new V(0,Math.sin(alpha)*radius,-Math.cos(alpha)*radius));
        before=now=CameraGeometry.between(from,actual,base.rotate(Y)).times(base).normalized();
    }
    public Q basis(double partial){return DisplayMath.mix(before,now,Math.clamp(partial,0,1));}
    public V offset(double partial){return basis(partial).rotate(new V(0,Math.sin(alpha)*radius,-Math.cos(alpha)*radius));}
    public V up(){return up(1);}
    public V up(double partial){return basis(partial).rotate(Y);}
    public V focus(){return CameraGeometry.focus();}
    /** 遮挡后的actual与同partial基在这里汇合；不再独立追赶目标光轴。 */
    public Q view(V actual,double partial){
        V forward=focus().add(actual.mul(-1));if(forward.length()<1e-9)forward=basis(partial).rotate(Z);
        return DisplayMath.look(forward,up(partial));
    }
}
