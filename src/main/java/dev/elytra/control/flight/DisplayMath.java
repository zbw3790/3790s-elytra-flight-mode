package dev.elytra.control.flight;
import dev.elytra.control.flight.FlightModel.*;
/** 只读显示计算；不积分动力、不持有实体。 */
public final class DisplayMath {
 private DisplayMath(){}
 public static double dot(Q a,Q b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z()+a.w()*b.w();}
 public static Q mix(Q a,Q b,double t){
  a=a.normalized();b=b.normalized();t=Math.clamp(t,0,1);double d=dot(a,b);
  if(d<0){b=new Q(-b.x(),-b.y(),-b.z(),-b.w());d=-d;}
  double x=1-t,y=t;if(d<.9995){double angle=Math.acos(Math.clamp(d,-1,1));x=Math.sin((1-t)*angle)/Math.sin(angle);y=Math.sin(t*angle)/Math.sin(angle);}
  return new Q(a.x()*x+b.x()*y,a.y()*x+b.y()*y,a.z()*x+b.z()*y,a.w()*x+b.w()*y).normalized();
 }
 public static Q approach(Q a,Q b,double gain,double maxRadians){double angle=2*Math.acos(Math.clamp(Math.abs(dot(a.normalized(),b.normalized())),0,1));return mix(a,b,angle<1e-9?1:Math.min(gain,maxRadians/angle));}
 public static V cross(V a,V b){return new V(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());}
 /** 目标决定前向，机体只提供绕该前向的侧倾；不将机体完整Q混回目标。 */
 public static Q targetView(Q body,V target,double rollStrength){
  Q level=look(target,new V(0,1,0)),bodyLevel=look(body.rotate(new V(0,0,1)),new V(0,1,0));
  V left=body.rotate(new V(1,0,0));
  double bank=Math.atan2(left.dot(bodyLevel.rotate(new V(0,1,0))),left.dot(bodyLevel.rotate(new V(1,0,0))));
  return level.times(Q.axis(2,bank*Math.clamp(rollStrength,0,1))).normalized();
 }
 public static Q look(V f,V hint){
  f=f.unit();V r=cross(hint,f).unit();if(r.length()<.5)r=cross(Math.abs(f.z())<.9?new V(0,0,1):new V(1,0,0),f).unit();V u=cross(f,r);
  double m00=r.x(),m01=u.x(),m02=f.x(),m10=r.y(),m11=u.y(),m12=f.y(),m20=r.z(),m21=u.z(),m22=f.z();double x,y,z,w,s,trace=m00+m11+m22;
  if(trace>0){s=Math.sqrt(trace+1)*2;w=.25*s;x=(m21-m12)/s;y=(m02-m20)/s;z=(m10-m01)/s;}
  else if(m00>m11&&m00>m22){s=Math.sqrt(1+m00-m11-m22)*2;w=(m21-m12)/s;x=.25*s;y=(m01+m10)/s;z=(m02+m20)/s;}
  else if(m11>m22){s=Math.sqrt(1+m11-m00-m22)*2;w=(m02-m20)/s;x=(m01+m10)/s;y=.25*s;z=(m12+m21)/s;}
  else{s=Math.sqrt(1+m22-m00-m11)*2;w=(m10-m01)/s;x=(m02+m20)/s;y=(m12+m21)/s;z=.25*s;}
  return new Q(x,y,z,w).normalized();
 }
 public record Projection(double x,double y,boolean edge,boolean behind){}
 /** camera为原版相机四元数（前向-Z）；返回GUI坐标，含roll与身后边缘。 */
 public static Projection project(Q camera,V direction,double fov,int width,int height){return project(camera,direction,fov,width,height,width/(double)height);}
 /** 实际渲染宽高比单独传入，避免GUI缩放整数取整改变水平投影。 */
 public static Projection project(Q camera,V direction,double fov,int width,int height,double aspect){
  Q inv=new Q(-camera.x(),-camera.y(),-camera.z(),camera.w()).normalized();V d=inv.rotate(direction);double z=-d.z(),s=height/(2*Math.tan(Math.toRadians(fov)/2));
  double x=d.x()/Math.max(.05,z)*s*(width/(double)height)/aspect,y=-d.y()/Math.max(.05,z)*s;boolean behind=z<=0;
  if(behind){double n=Math.hypot(d.x(),d.y());x=n<1e-9?width:d.x()/n*width;y=n<1e-9?0:-d.y()/n*height;}
  double maxX=Math.max(1,width/2.0-22),maxY=Math.max(1,height/2.0-30);boolean edge=behind||Math.abs(x)>maxX||Math.abs(y)>maxY;
  if(edge){double scale=Math.max(Math.abs(x)/maxX,Math.abs(y)/maxY);x/=scale;y/=scale;}
  return new Projection(width/2.0+x,height/2.0+y,edge,behind);
 }
}
