package dev.elytra.control.flight;

import dev.elytra.control.flight.FlightModel.*;

/** 目标与三轴控制器；只给有界意图，不拥有位置/姿态。世界Y上、机体Z前。 */
public final class Instructor {
    public static final String CONFIG="M4Q-MICRO-DIRECT-1";
    public record Axes(double pitch,double roll,double yaw) {
        public static final Axes ZERO=new Axes(0,0,0);
        public Axes { if(!valid(pitch)||!valid(roll)||!valid(yaw))throw new IllegalArgumentException("教官轴越界"); }
        private static boolean valid(double v){return Double.isFinite(v)&&Math.abs(v)<=1;}
    }
    public enum Mode { TRACK, DIRECT_PUSH, ROLL_PULL }
    /** 世界目标锁只用于窄例外连续性；不能由客户端额外授予控制权。 */
    public record State(Axes output,int turnSide,Mode mode,V anchor) {
        public static final State INITIAL=new State(Axes.ZERO,1,Mode.TRACK,new V(0,0,1));
        public State(Axes output,int turnSide){this(output,turnSide,Mode.TRACK,new V(0,0,1));}
        public State(Axes output){this(output,1);}
        public State {java.util.Objects.requireNonNull(output);java.util.Objects.requireNonNull(mode);if(turnSide!=1&&turnSide!=-1)throw new IllegalArgumentException("选向记忆越界");if(anchor==null||Math.abs(anchor.length()-1)>.001)throw new IllegalArgumentException("机动目标锁无效");}
    }
    /** bits保留每个按键，双键中立仍占用该轴；target为世界单位方向。 */
    public record Input(V target,long bits) {
        public Input {if(target==null||Math.abs(target.length()-1)>.001)throw new IllegalArgumentException("目标非单位有限方向");FlightModel.Intent.decode(bits);}
        public int mask(){if(bits==513)return 7;return ((bits&6)!=0?1:0)|((bits&24)!=0?2:0)|((bits&96)!=0?4:0);}
    }
    public record Geometry(V local,double theta,double bank,double bodyPitch,boolean level,boolean belowCone,Mode mode,String reason,double rollError,double blend){}
    public record Result(State state,Axes automatic,Intent intent,int mask,double error,Geometry geometry){}
    private Instructor(){}
    public static double angle(V a,V b){return Math.acos(Math.clamp(a.unit().dot(b.unit()),-1,1));}
    public static Result step(FlightModel.State flight,Input input,State previous){
  int turnSide=previous.turnSide(),mask=input.mask();Intent manual=Intent.decode(input.bits());
  Q q=flight.attitude();V t=inv(q).rotate(input.target());double theta=Math.acos(clamp(t.z(),-1,1)),error=deg(theta),radial=Math.hypot(t.x(),t.y());
  V forward=f(q),left=q.rotate(new V(1,0,0)),up=q.rotate(new V(0,1,0));double horizon=Math.atan2(left.y(),up.y()),bodyPitch=deg(Math.asin(clamp(forward.y(),-1,1)));
  boolean level=up.y()>0&&Math.abs(deg(horizon))<=15&&Math.abs(bodyPitch)<=15;
  // 22度是局部XY误差平面侧角，另用前半球约束排除侧后方目标。
  double belowSide=Math.abs(deg(Math.atan2(t.x(),-t.y())));
  boolean cone=t.y()<0&&t.z()>0&&belowSide<=22;
  Mode mode=previous.mode();V anchor=previous.anchor();String reason="保持";
  // 进入后不因姿态越15度或小步目标过旧锚点12度立即反滚。
  // 保持扇区较入口宽；真正大改向仍关闭，而非永久负G许可。
  double targetChange=deg(angle(anchor,input.target()));
  boolean holdingBelow=t.y()<0&&t.z()>0&&belowSide<=32;
  boolean changed=mode==Mode.DIRECT_PUSH?targetChange>30||(targetChange>12&&!holdingBelow):targetChange>12;
  if(mask!=0){mode=Mode.TRACK;reason="手动占轴";}
  else if(mode==Mode.DIRECT_PUSH&&(error<=3||changed)){mode=Mode.TRACK;reason=changed?"目标重定向":"例外完成";}
  else if(mode==Mode.ROLL_PULL&&(error<=3||changed)){mode=Mode.TRACK;reason=changed?"机动目标重定向":"正G机动完成";}
  if(mask==0&&mode==Mode.TRACK&&error>6){mode=level&&cone?Mode.DIRECT_PUSH:Mode.ROLL_PULL;anchor=input.target();reason=mode==Mode.DIRECT_PUSH?"平飞直接负Y例外":"正G优先";}
  // 机动中近负Y对称分支沿记忆，不在微小左右噪声下每刻改选180度。
  if(Math.abs(t.x())>.08&&error<145&&!(t.y()<0&&previous.mode()==Mode.ROLL_PULL&&!changed))turnSide=(int)Math.signum(t.x());
  double escape=smooth(error,150,175)*(1-smooth(radial,.02,.05));
  double plane=-Math.atan2(t.x(),t.y());
  // 负Y半平面沿已选支路展开，微扰跨过atan2的±180或旧.08界线均不反滚。
  if(mode==Mode.ROLL_PULL&&t.y()<0&&plane*turnSide>0)plane-=Math.copySign(2*Math.PI,plane);
  plane+=escape*wrap(-Math.PI/2*turnSide-plane);
  // 精调与大机动分层：6度入口处权重为零，不因模式名改变就饱和半滚。
  // 精调保留有符号误差，避免旧的正值截断与回平混合形成残差平衡。
  double blend=mode==Mode.ROLL_PULL?smooth(error,6,20):0;
  double horizonWeight=smooth(Math.hypot(forward.x(),forward.z()),.05,.2);
  double rollError=plane*blend-wrap(horizon)*horizonWeight*(1-blend);
  double alignment=(1-escape)*clamp(t.y()/Math.max(radial,.001),0,1)+escape*.5;
  var max=AngularMotion.limits(flight.velocity().length());
  // 同时建立有限拉杆/偏航；未对齐也不依赖长时间推杆来完成默认机动。
  double pullRate=Math.min(3*error,max.pull())*(.12+.88*smooth(alignment,.25,.9));
  double directRate=3*deg(Math.atan2(t.y(),Math.hypot(t.x(),t.z())));
  double wanted=directRate*(1-blend)+pullRate*blend;
  double pitch=max.pitchIntent(wanted+.30*flight.omega().x());
  double roll=(4.5*deg(rollError)-.65*flight.omega().z())/max.roll();
  double yawError=Math.atan2(t.x(),t.z());double yaw=(2*deg(yawError)*(1-.95*blend)-.3*flight.omega().y())/max.yaw();
  double yawLimit=8*(1-.65*alignment);yaw=yaw*(1-blend)+clamp(yaw,-yawLimit/max.yaw(),yawLimit/max.yaw())*blend;
  yaw*=1-smooth(error,150,175);
  if((mask&2)!=0){pitch*=1-.7*Math.abs(manual.roll())*blend;yaw*=1-.5*Math.abs(manual.roll())*blend;}
  // 手动俯仰/偏航时不追逐世界地平线侧倾，以免穿越垂直后自动半滚转；其余轴仍可辅助。
  if((mask&1)!=0||(mask&4)!=0){roll=clamp(-.5*flight.omega().z()/max.roll(),-20/max.roll(),20/max.roll());if((mask&1)!=0)yaw=clamp(yaw,-8/max.yaw(),8/max.yaw());}
  // 实际角速率由下层连续状态建立/制动，不再把旧.10控制滤波与电机惯性叠加。
  double p=(mask&1)!=0?manual.pitch():clamp(pitch,-1,1);
  double z=(mask&2)!=0?manual.roll():clamp(roll,-1,1);
  double y=(mask&4)!=0?manual.yaw():clamp(yaw,-1,1);
  Axes out=new Axes(p,z,y);
  if(manual.menu())return new Result(previous,new Axes(0,0,0),manual,7,theta,new Geometry(t,error,deg(horizon),bodyPitch,level,cone,previous.mode(),"菜单中立保持",0,0));
  return new Result(new State(out,turnSide,mode,anchor),new Axes(clamp(pitch,-1,1),clamp(roll,-1,1),clamp(yaw,-1,1)),new Intent(p,z,y,manual.throttle(),manual.boost(),manual.throttleBoth()),mask,theta,new Geometry(t,error,deg(horizon),bodyPitch,level,cone,mode,reason,deg(rollError),blend));
 }
    private static Q inv(Q q){return new Q(-q.x(),-q.y(),-q.z(),q.w()).normalized();}
    private static V f(Q q){return q.rotate(new V(0,0,1));}
    private static double deg(double x){return Math.toDegrees(x);}
    private static double smooth(double x,double a,double b){double t=clamp((x-a)/(b-a),0,1);return t*t*(3-2*t);}
    private static double clamp(double x,double a,double b){return Math.clamp(x,a,b);}
    private static double wrap(double x){return Math.atan2(Math.sin(x),Math.cos(x));}
    /** 鼠标目标保存连续球面相位；越过极点后等价yaw+180，不把后续输入反弹回同一半球。 */
    public record Target(double yaw,double pitch) {
        public Target {if(!Double.isFinite(yaw)||!Double.isFinite(pitch))throw new IllegalArgumentException("目标角非法");yaw=Math.IEEEremainder(yaw,360);pitch=Math.IEEEremainder(pitch,360);}
        public V direction(){double y=Math.toRadians(yaw),p=Math.toRadians(pitch);return new V(-Math.sin(y)*Math.cos(p),-Math.sin(p),Math.cos(y)*Math.cos(p));}
        public Target mouse(double scaledX,double scaledY){if(!Double.isFinite(scaledX)||!Double.isFinite(scaledY))throw new IllegalArgumentException("鼠标非有限");return new Target(yaw+Math.IEEEremainder(scaledX*.15,360),pitch+Math.IEEEremainder(scaledY*.15,360));}
        public double canonicalPitch(){return pitch>90?180-pitch:pitch< -90?-180-pitch:pitch;}
        public double canonicalYaw(){return Math.IEEEremainder(yaw+(Math.abs(pitch)>90?180:0),360);}
        public static Target from(V v){if(Math.abs(v.length()-1)>.001)throw new IllegalArgumentException("目标向量无效");return new Target(Math.toDegrees(Math.atan2(-v.x(),v.z())),Math.toDegrees(-Math.asin(Math.clamp(v.y(),-1,1))));}
    }
}
