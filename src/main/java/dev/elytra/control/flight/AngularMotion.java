package dev.elytra.control.flight;
import dev.elytra.control.flight.FlightModel.*;
/** M4O非对称项目试用配置；不是已验证的战雷角响应或IAS/TAS。 */
public final class AngularMotion {
 public static final String CONFIG="M4O-ASYM-1";
 public static final double DT=.05;
 private AngularMotion(){}
 public static double referenceSpeed(double speed){double s=Math.clamp(speed*20,0,20);double[] x={0,4,8,14,20},y={0,350,600,900,1200};for(int n=1;n<x.length;n++)if(s<=x[n])return y[n-1]+(y[n]-y[n-1])*(s-x[n-1])/(x[n]-x[n-1]);return 1200;}
 /** 命令方向的目标角限，单位度/秒；瞬态Omega不随目标帽直接裁剪。 */
 public record Limits(double pull,double push,double yaw,double roll) {
  public double pitch(double intent){return intent>=0?pull:push;}
  public double pitchIntent(double requestedRate){return Math.clamp(requestedRate/pitch(requestedRate),-1,1);}
 }
 public static Limits limits(double speed){double ref=referenceSpeed(speed);double pitch=ref<=600?65:ref<=900?65-(ref-600)/30:55-(ref-900)/30;double a=.25+.75*Math.min(speed/.4,1);double p=Math.clamp(pitch,45,65)*a;return new Limits(.80*p,.25*p,40*a,200*a);}
 public static OmegaBody advance(OmegaBody w,Intent i,Limits max){double[] old={w.x(),w.y(),w.z()},cmd={-i.pitch()*max.pitch(i.pitch()),i.yaw()*max.yaw(),i.roll()*max.roll()},acc={400,300,1000};for(int k=0;k<3;k++){boolean brake=cmd[k]*old[k]<=0||Math.abs(cmd[k])<Math.abs(old[k]);double delta=(cmd[k]-old[k])*(1-Math.exp(-DT/(brake?.10:.15)));old[k]+=Math.clamp(delta,-acc[k]*DT,acc[k]*DT);if(Math.abs(old[k])<1e-8&&cmd[k]==0)old[k]=0;}return new OmegaBody(old[0],old[1],old[2]);}
 public static Q integrate(Q q,OmegaBody omega){V w=new V(Math.toRadians(omega.x()*DT),Math.toRadians(omega.y()*DT),Math.toRadians(omega.z()*DT));double mag=w.length();Q delta=mag<1e-15?Q.axis(0,0):new Q(w.x()/mag*Math.sin(mag/2),w.y()/mag*Math.sin(mag/2),w.z()/mag*Math.sin(mag/2),Math.cos(mag/2));return q.times(delta).normalized();}
}
