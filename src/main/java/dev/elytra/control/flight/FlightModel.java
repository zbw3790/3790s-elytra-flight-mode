package dev.elytra.control.flight;

/** 纯数值街机模型：世界Y向上，当前显示基准为机体X左/Y上/Z前；平移单位格/游戏刻，角运动固定dt=0.05秒。 */
public final class FlightModel {
    public static final String CONFIG="M4P-DYNAMICS-1";
    public static final String THROTTLE_CONFIG="M4S-WEP-EDGE-1";
    public static final double MAX_SPEED=1, GRAVITY=.035, THRUST=.030, WEP=1.6, THROTTLE_RATE=.025;
    public static final double LIFT=.034;
    public record V(double x,double y,double z) {
        public V { if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)) throw new IllegalArgumentException("速度非有限"); }
        public V add(V b){return new V(x+b.x,y+b.y,z+b.z);}
        public V mul(double a){return new V(x*a,y*a,z*a);}
        public double dot(V b){return x*b.x+y*b.y+z*b.z;}
        public double length(){return Math.sqrt(dot(this));}
        public V unit(){double n=length();return n<1e-12?new V(0,0,0):mul(1/n);}
    }
    public record Q(double x,double y,double z,double w) {
        public Q {if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Double.isFinite(w)||x*x+y*y+z*z+w*w<1e-20)throw new IllegalArgumentException("姿态无效");}
        public Q normalized(){double n=Math.sqrt(x*x+y*y+z*z+w*w);return new Q(x/n,y/n,z/n,w/n);}
        public Q times(Q b){return new Q(w*b.x+x*b.w+y*b.z-z*b.y,w*b.y-x*b.z+y*b.w+z*b.x,w*b.z+x*b.y-y*b.x+z*b.w,w*b.w-x*b.x-y*b.y-z*b.z);}
        public V rotate(V a){V u=new V(x,y,z);return u.mul(2*u.dot(a)).add(a.mul(w*w-u.dot(u))).add(new V(y*a.z-z*a.y,z*a.x-x*a.z,x*a.y-y*a.x).mul(2*w));}
        public static Q axis(int axis,double angle){double s=Math.sin(angle/2);return new Q(axis==0?s:0,axis==1?s:0,axis==2?s:0,Math.cos(angle/2));}
    }
    /** 相反方向同时按下在适配器中抵消；编码只允许九个按键位和焦点位。 */
    public record Intent(double pitch,double roll,double yaw,int throttle,boolean boost,boolean throttleBoth,boolean menu) {
        public Intent(double pitch,double roll,double yaw,int throttle,boolean boost,boolean both){this(pitch,roll,yaw,throttle,boost,both,false);}
        /** 兼容旧纯测试的有向油门构造；新boost不再直接决定WEP，正油门表示增油门键电平。 */
        public Intent(double pitch,double roll,double yaw,int throttle,boolean boost){this(pitch,roll,yaw,throttle,boost,false,false);}
        public static final Intent NEUTRAL=new Intent(0,0,0,0,false);
        public Intent {if(!Double.isFinite(pitch)||!Double.isFinite(roll)||!Double.isFinite(yaw)||Math.abs(pitch)>1||Math.abs(roll)>1||Math.abs(yaw)>1||Math.abs(throttle)>1||boost&&throttle!=1||throttleBoth&&(throttle!=0||boost))throw new IllegalArgumentException("输入越界");if(menu&&(pitch!=0||roll!=0||yaw!=0||throttle!=0||boost||throttleBoth))throw new IllegalArgumentException("菜单只能中立");}
        public boolean moreDown(){return throttle>0||throttleBoth;}
        public boolean lessDown(){return throttle<0||throttleBoth;}
        // 线上的bit3/bit4仍为左/右键；正Z角会抬起左翼，所以左键转换为负roll。
        // 正局部Y旋转使机头朝机体+X（左）；线上bit6左/bit5右的动作含义不变。
        public static Intent decode(long bits){if(bits==513)return new Intent(0,0,0,0,false,false,true);if((bits&~511L)!=0||(bits&1)==0)throw new IllegalArgumentException("非法按键掩码");return new Intent(axis(bits,1,2),axis(bits,4,3),axis(bits,6,5),axis(bits,7,8),(bits&128)!=0&&(bits&256)==0,(bits&384)==384);}
        private static int axis(long b,int p,int n){return (int)((b>>p)&1)-(int)((b>>n)&1);}
        public long encode(){if(menu)return 513;return 1L|bit(pitch,1,2)|bit(roll,4,3)|bit(yaw,6,5)|(throttleBoth?384:bit(throttle,7,8));}
        private static long bit(double v,int p,int n){return v>0?1L<<p:v<0?1L<<n:0;}
    }
    /** 物理机体XYZ角速度，单位度/秒；绝对边界独立于降速后的目标上限。 */
    public record OmegaBody(double x,double y,double z) {
        public static final OmegaBody ZERO=new OmegaBody(0,0,0);
        public OmegaBody {if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>65+1e-9||Math.abs(y)>40+1e-9||Math.abs(z)>200+1e-9)throw new IllegalArgumentException("角速度越界");}
    }
    /** 输入历史是共享状态：Ctrl退出时同次持续按压不再减油门。 */
    public record ThrottleMemory(boolean more,boolean less,boolean lessBlocked) {
        public static final ThrottleMemory INITIAL=new ThrottleMemory(false,false,false);
        public ThrottleMemory {if(lessBlocked&&!less)throw new IllegalArgumentException("油门抑制缺少持续按键");}
    }
    public record State(V velocity,Q attitude,double throttle,boolean wep,boolean limited,OmegaBody omega,ThrottleMemory throttleMemory) {
        /** 新鲜状态兼容构造；连续模拟/重基准必须携带完整角速度与油门记忆。 */
        public State(V velocity,Q attitude,double throttle,boolean wep,boolean limited){this(velocity,attitude,throttle,wep,limited,OmegaBody.ZERO);}
        public State(V velocity,Q attitude,double throttle,boolean wep,boolean limited,OmegaBody omega){this(velocity,attitude,throttle,wep,limited,omega,ThrottleMemory.INITIAL);}
        public State {if(!Double.isFinite(throttle)||throttle<0||throttle>1)throw new IllegalArgumentException("油门越界");java.util.Objects.requireNonNull(velocity);java.util.Objects.requireNonNull(omega);java.util.Objects.requireNonNull(throttleMemory);attitude=attitude.normalized();}
        public State collision(V actual){return new State(actual,attitude,throttle,wep,limited,omega,throttleMemory);}
    }
    private FlightModel(){}
    /** 街机去滚转支持；极点附近连续退回body-up，不保存相机/历史分支。
     * 混合不单位化：倒飞两向相消时允许连续经过零，避免制造奇异增益。 */
    public static V supportUp(V forward,V bodyUp){
        V projected=new V(0,1,0).add(forward.mul(-forward.y()));double horizontal=projected.length();
        if(horizontal<=.1)return bodyUp;
        double t=Math.clamp((horizontal-.1)/.2,0,1),weight=.75*t*t*(3-2*t);
        return bodyUp.mul(1-weight).add(projected.mul(weight/horizontal));
    }
    public static State initial(V velocity,double yawDegrees,double pitchDegrees){
        if(velocity.length()>MAX_SPEED+1e-9)throw new IllegalArgumentException("初速超过许可上限");
        return new State(velocity,Q.axis(1,Math.toRadians(-yawDegrees)).times(Q.axis(0,Math.toRadians(pitchDegrees))),0,false,false);
    }
    public static State step(State s,Intent i){
        return step(s,i,i.menu());
    }
    /** 输入暂停保持油门记忆；Instructor三轴仍按冻结目标正常计算。物理常数不变。 */
    public static State step(State s,Intent i,boolean holdThrottle){
        boolean more=i.moreDown(),less=i.lessDown(),morePressed=more&&!s.throttleMemory.more,lessPressed=less&&!s.throttleMemory.less;
        boolean blocked=s.throttleMemory.lessBlocked&&less,wep=s.wep;double t=s.throttle;
        if(wep){
            t=1;
            if(lessPressed){wep=false;blocked=true;}
        }else if(morePressed&&!less&&!blocked&&t>=1){wep=true;t=1;}
        if(!wep&&!blocked)t=Math.clamp(t+i.throttle*THROTTLE_RATE,0,1);
        var throttleMemory=new ThrottleMemory(more,less,blocked);
        // 菜单/重新武装不伪造油门释放或按下；正常动力和角制动仍推进。
        if(holdThrottle){t=s.throttle;wep=s.wep;throttleMemory=s.throttleMemory;}
        var max=AngularMotion.limits(s.velocity.length());
        OmegaBody omega=AngularMotion.advance(s.omega,i,max);
        Q q=AngularMotion.integrate(s.attitude,omega);
        V forward=q.rotate(new V(0,0,1)), up=q.rotate(new V(0,1,0));
        V v=s.velocity.mul(Math.max(0,1-.015-.05*s.velocity.length()))
            .add(forward.mul(THRUST*t*(wep?WEP:1))).add(new V(0,-GRAVITY,0));
        // 升力和受限转向只旋转速度方向，归一化保留此阶段动能；零速无免费升力。
        double speed=v.length();
        if(speed>1e-9){
            V dir=v.unit();
            V support=supportUp(forward,up);
            V lift=support.add(dir.mul(-support.dot(dir))).mul(LIFT*Math.min(speed/.4,1));
            V turn=forward.add(dir.mul(-forward.dot(dir))).mul(.025*Math.min(speed/.4,1));
            v=v.add(lift).add(turn).unit().mul(speed);
        }
        boolean cap=v.length()>MAX_SPEED;
        if(cap)v=v.unit().mul(MAX_SPEED);
        return new State(v,q,t,wep,cap,omega,throttleMemory);
    }
}
