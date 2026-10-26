package dev.elytra.control.client.flight;

import dev.elytra.control.flight.*;
import dev.elytra.control.flight.FlightModel.*;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionf;

/** 双控制器只读显示状态；正常固定刻阻尼，V相对鼠标立即改变同一帧机位与up。 */
public final class FlightView {
 private static final NormalChaseController chase=new NormalChaseController();
 private static final DirectFreeOrbitController orbit=new DirectFreeOrbitController();
 private static Q firstBefore,firstNow,firstFree,entryView,exitView;
 private static boolean ready,latched,firstLast;
 private static long entryNanos,exitNanos;
 public static final double DISTANCE=setting("efc.view.distance",6,2,8),FIRST_ROLL=setting("efc.view.firstRoll",1,0,1);
 public static final double ENTRY_SECONDS=.10,EXIT_SECONDS=.20;
 public record Frame(V anchor,V camera,V wantedOffset,V actualOffset,Q view,boolean rear,boolean free,V focus,boolean transition){}
 private static Frame frame;
 private static double setting(String name,double value,double min,double max){try{double d=Double.parseDouble(System.getProperty(name,""+value));return Double.isFinite(d)?Math.clamp(d,min,max):value;}catch(NumberFormatException e){return value;}}
 public static void reset(){ready=latched=false;firstBefore=firstNow=firstFree=entryView=exitView=null;frame=null;}
 private static boolean first(){return Minecraft.getInstance().options.getCameraType().isFirstPerson();}
 private static void sync(float partial){
  if(!ready)return;boolean f=first();
  if(f!=firstLast){latched=false;frame=null;entryView=exitView=null;firstLast=f;}
  boolean held=FlightControls.FREE.isDown();
  if(held&&!latched){
   V from=frame==null||frame.wantedOffset().length()<.1?chase.offset(partial):frame.wantedOffset();
   orbit.enter(from);firstFree=firstNow;
   entryView=frame==null?DisplayMath.look(chase.focus().add(from.mul(-1)),chase.up(partial)):frame.view();
   entryNanos=System.nanoTime();exitView=null;latched=true;
  }else if(!held&&latched){
   if(!f){chase.take(orbit.offset());exitView=frame==null?null:frame.view();exitNanos=System.nanoTime();}firstBefore=firstNow=firstFree;latched=false;entryView=null;
  }
 }
 public static void tick(Q body,boolean reset){
  var mc=Minecraft.getInstance();V target=AimView.target();
  if(reset||!ready){chase.reset(target,DISTANCE);firstBefore=firstNow=firstFree=DisplayMath.targetView(body,target,FIRST_ROLL);ready=true;latched=false;firstLast=first();frame=null;}
  sync(1);firstBefore=firstNow;
  if(mc.gui.screen()!=null||!mc.isWindowActive()||!mc.mouseHandler.isMouseGrabbed())return;
  if(latched){chase.observe(target);if(first())firstNow=firstFree;}
  else {chase.tick(target,DISTANCE);firstNow=DisplayMath.approach(firstNow,DisplayMath.targetView(body,target,FIRST_ROLL),.25,Math.toRadians(8));}
 }
 public static void mouse(double x,double y){if(!ready)return;sync(1);if(first())firstFree=CameraGeometry.mouse(firstFree,x,y);else orbit.mouse(x,y);}
 public static Q orientation(float partial){if(!ready)return null;sync(partial);if(first())return DisplayMath.mix(firstBefore,firstNow,partial);return resolved(offset(partial),partial,false);}
 public static V up(float partial){sync(partial);return latched&&!first()?orbit.up():chase.up(partial);}
 /** 只读normal诊断，不改变任何目标、姿态或控制器状态。 */
 public static NormalChaseController.State normalState(){return chase.state();}
 public static V offset(float partial){if(!ready||first())return new V(0,0,0);sync(partial);return latched?orbit.offset():chase.offset(partial);}
 public static V mirrorOffset(float partial){V p=offset(partial);return latched?p.mul(-1):p.mul(-1).add(chase.up(partial).mul(CameraGeometry.HEIGHT*2));}
 /** 遮挡后的实际C和控制器选择的光学参考只在这里汇合。 */
 public static V focus(float partial,boolean mirrored){return latched||mirrored?CameraGeometry.focus():chase.focus();}
 public static Q resolved(V actual,float partial,boolean mirrored){
  V reference=focus(partial,mirrored);Q desired=latched||mirrored?CameraGeometry.focusView(actual,up(partial),frame==null?null:frame.view()):chase.view(actual,partial);
  if(latched&&!mirrored&&entryView!=null){double t=Math.clamp((System.nanoTime()-entryNanos)/1e9/ENTRY_SECONDS,0,1);if(t<1)return DisplayMath.mix(entryView,desired,t);}
  if(!latched&&!mirrored&&exitView!=null){double t=Math.clamp((System.nanoTime()-exitNanos)/1e9/EXIT_SECONDS,0,1);if(t<1)return DisplayMath.mix(exitView,desired,t);}
  return desired;
 }
 public static double freePitch(){return latched?orbit.pitch():0;}
 public static boolean transitioning(){return latched?entryView!=null&&(System.nanoTime()-entryNanos)/1e9<ENTRY_SECONDS:exitView!=null&&(System.nanoTime()-exitNanos)/1e9<EXIT_SECONDS;}
 /** 交接阶段记录实际过渡光线；非过渡阶段记录控制器原始光学参考。 */
 public static V opticalPoint(Q view,V actual,float partial,boolean mirrored){return transitioning()&&!mirrored?actual.add(view.rotate(new V(0,0,1)).mul(NormalChaseController.OPTICAL_DISTANCE)):focus(partial,mirrored);}
 public static boolean free(){return latched;}
 public static Frame frame(){return frame;}
 public static void frame(Frame value){frame=value;}
 public static V projectionDirection(V direction){var mc=Minecraft.getInstance();V anchor=frame!=null?frame.anchor():new V(mc.player.getX(),mc.player.getY()+.3,mc.player.getZ());var c=mc.gameRenderer.mainCamera().position();return CameraGeometry.relativePoint(anchor,new V(c.x,c.y,c.z),direction);}
 public static Quaternionf joml(Q q){return new Quaternionf((float)q.x(),(float)q.y(),(float)q.z(),(float)q.w());}
}
