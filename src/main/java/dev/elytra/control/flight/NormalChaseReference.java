package dev.elytra.control.flight;
import dev.elytra.control.flight.FlightModel.*;
/** normal几何目标：普通区域唯一世界上向；仅原极点帽内运输明确参考。没有时间归正。 */
public final class NormalChaseReference {
 public static final double ENTER=Math.sin(Math.toRadians(1)),EXIT=Math.sin(Math.toRadians(2));
 private static final V Y=new V(0,1,0),Z=new V(0,0,1);
 private boolean ready,inverted,armed;private long crossings;private V previous,capHeading=Z;private Q frame;
 public record State(double yaw,double pitch,boolean inverted,boolean poleArmed,boolean projected,long crossings,V previous,Q basis,V capHeading,double horizontal){}
 public State state(){double h=previous==null?0:Math.hypot(previous.x(),previous.z());double yaw=h<1e-12?Math.atan2(capHeading.x(),capHeading.z()):Math.atan2(previous.x(),previous.z());return new State(yaw,previous==null?0:Math.atan2(-previous.y(),h),inverted,armed,false,crossings,previous,frame,capHeading,h);}
 public void reset(V forward){V f=valid(forward);double h=Math.hypot(f.x(),f.z());inverted=false;armed=h<ENTER;capHeading=h<1e-12?Z:new V(f.x()/h,0,f.z()/h);frame=basis(Math.atan2(capHeading.x(),capHeading.z()),Math.atan2(-f.y(),h));previous=f;crossings=0;ready=true;}
 public void observe(V forward){observe(forward,1);}
 void observe(V forward,double tickDelta){
  if(!Double.isFinite(tickDelta)||tickDelta<=0||tickDelta>4)throw new IllegalArgumentException("正常参考时间步越界");
  V f=valid(forward);if(!ready){reset(f);return;}double h=Math.hypot(f.x(),f.z());
  if(!armed&&(h<ENTER||crossesCap(previous,f))){double ph=Math.hypot(previous.x(),previous.z());if(ph>1e-12)capHeading=new V(previous.x()/ph,0,previous.z()/ph).mul(inverted?-1:1);armed=true;}
  if(armed&&h>EXIT){boolean next=f.dot(capHeading)<0;if(next!=inverted)crossings++;inverted=next;armed=false;}
  // 极点帽外的目标只由当前f决定，不迭代上一Up，也不以crossing奇偶翻转世界上向。
  frame=armed?CameraGeometry.between(previous,f,frame.rotate(Y)).times(frame).normalized():DisplayMath.look(f,Y);
  previous=f;
 }
 private static boolean crossesCap(V a,V b){if(a==null||a.y()*b.y()<=0)return false;double x=b.x()-a.x(),z=b.z()-a.z(),d=x*x+z*z;if(d<1e-24)return false;double t=Math.clamp(-(a.x()*x+a.z()*z)/d,0,1);if(t<=0||t>=1)return false;V closest=a.mul(1-t).add(b.mul(t));double n=closest.length();return n>1e-9&&Math.hypot(closest.x(),closest.z())/n<ENTER;}
 public Q basis(){return frame;}
 public static Q basis(double yaw,double pitch){return Q.axis(1,yaw).times(Q.axis(0,pitch)).normalized();}
 public static double wrap(double angle){return Math.IEEEremainder(angle,Math.PI*2);}
 private static V valid(V f){if(f==null||!Double.isFinite(f.x())||!Double.isFinite(f.y())||!Double.isFinite(f.z())||f.length()<1e-9)throw new IllegalArgumentException("正常追尾前向非有限或为零");return f.unit();}
}
