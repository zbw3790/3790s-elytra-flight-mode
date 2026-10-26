package dev.elytra.control.flight;
import dev.elytra.control.flight.FlightModel.*;
/** 机体中心轨道的纯显示几何；只计算相机，不拥有目标/实体/运动。 */
public final class CameraGeometry {
 public static final String CONFIG="M4H-DUAL-1";
 public static final double HEIGHT=1.5,FAR=500,FOCUS_UP=.4;
 private static final V Y=new V(0,1,0),Z=new V(0,0,1);
 private CameraGeometry(){}
 /** 沿目标球面最小旋转运输整个观察基；不在过极点后强制回世界地平线。 */
 public static Q reference(Q previous,V target){V f=target.unit();return previous==null?DisplayMath.look(f,Y):between(previous.rotate(Z),f,previous.rotate(Y)).times(previous).normalized();}
 public static V offset(Q orbit,double distance){return orbit.rotate(new V(0,HEIGHT,-distance));}
 public static Q advance(Q from,Q desired){return DisplayMath.approach(from,desired,.25,Math.toRadians(8));}
 public static Q mouse(Q orbit,double x,double y){if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalArgumentException("鼠标非有限");return Q.axis(1,Math.toRadians(-Math.IEEEremainder(x*.15,360))).times(orbit).times(Q.axis(0,Math.toRadians(Math.IEEEremainder(y*.15,360)))).normalized();}
 public static V focus(){return new V(0,FOCUS_UP,0);}
 /** 最终实际机位决定视线；up来自同一轨道基，遮挡退到锚点附近时沿前帧连续运输。 */
 public static Q focusView(V actualOffset,V orbitUp,Q previous){
  V f=focus().add(actualOffset.mul(-1));if(f.length()<1e-9)f=previous==null?Z:previous.rotate(Z);f=f.unit();
  Q transported=reference(previous,f);V u=orbitUp.add(f.mul(-orbitUp.dot(f)));
  if(u.length()<.15)return transported;
  // signed pitch分支的up必须保留，不能以前帧最近半球重新扶正倒置画面。
  return DisplayMath.look(f,u.unit());
 }
 /** 保留旧辅助工具接口；生产视线已改成有限机体焦点，target不再决定光轴。 */
 public static Q aimed(V target,V offset,V up){return focusView(offset,up,null);}
 public static Q correctObstruction(Q view,V target,V wanted,V actual){return focusView(actual,view.rotate(Y),view);}
 /** HUD方向的有限远处显示点，与机体Focus完全独立。 */
 public static V relativePoint(V anchor,V camera,V direction){return anchor.add(direction.mul(FAR)).add(camera.mul(-1));}
 public static V interpolateOffset(V from,V to,double fraction){double radius=to.length();if(radius<1e-10)return new V(0,0,0);if(from.length()<1e-10)return to;Q arc=between(from,to,Y);return DisplayMath.mix(Q.axis(0,0),arc,Math.clamp(fraction,0,1)).rotate(from.unit()).mul(radius);}
 public static Q between(V from,V to,V hint){V a=from.unit(),b=to.unit();double d=Math.clamp(a.dot(b),-1,1);if(d>1-1e-14)return Q.axis(0,0);if(d< -1+1e-10){V axis=hint.add(a.mul(-hint.dot(a))).unit();if(axis.length()<.5)axis=DisplayMath.cross(a,Math.abs(a.y())<.9?Y:new V(1,0,0)).unit();return new Q(axis.x(),axis.y(),axis.z(),0);}V c=DisplayMath.cross(a,b);return new Q(c.x(),c.y(),c.z(),1+d).normalized();}
}
