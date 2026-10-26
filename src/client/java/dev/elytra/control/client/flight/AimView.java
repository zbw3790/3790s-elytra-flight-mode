package dev.elytra.control.client.flight;
import dev.elytra.control.flight.*;
import dev.elytra.control.flight.FlightModel.*;
import dev.elytra.control.client.probe.m0d.M0DClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.util.Map;
/** 独立鼠标目标与HUD；相机状态仅由只读FlightView拥有。 */
public final class AimView {
 private static Instructor.Target target;
 public static void initialize(){HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR,Identifier.fromNamespaceAndPath("elytra_flight_control","aim"),(g,dt)->{
  var mc=Minecraft.getInstance();if(!FlightHudData.visible(M0DClient.isAimActive(),mc.player!=null,mc.player!=null&&mc.player.isAlive(),mc.gui.hud.isHidden(),mc.gui.screen()!=null)||target==null)return;
  var camera=mc.gameRenderer.mainCamera();var rotation=camera.rotation();Q q=new Q(rotation.x,rotation.y,rotation.z,rotation.w).normalized();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
  marker(g,mc,q,w,h,target.direction(),true,0xFFBCA15F);var flight=M0DClient.flightState();Q body=DisplayPose.pose(mc.player,dt.getGameTimeDeltaPartialTick(true));if(body==null)body=flight.attitude();
  marker(g,mc,q,w,h,body.rotate(new V(0,0,1)),false,0xFF4DE1FF);
  // F3保留原生信息面板，避免左上重复文字；两方向标记仍独立。
  if(!mc.getDebugOverlay().showDebugScreen()){
   var lines=FlightHudData.lines(flight.throttle(),flight.wep(),M0DClient.hudSpeed(),mc.player.getY());
   for(int i=0;i<lines.size();i++)g.text(mc.font,lines.get(i),FlightHudData.MARGIN,FlightHudData.MARGIN+i*FlightHudData.LINE,0xFFFFFFFF,true);
  }
 });}
 private static final java.util.List<FlightHudData.Pixel> RING=FlightHudData.ring();
 private static void marker(net.minecraft.client.gui.GuiGraphicsExtractor g,Minecraft mc,Q camera,int w,int h,V v,boolean targetMarker,int color){
  var p=DisplayMath.project(camera,FlightView.projectionDirection(v),mc.gameRenderer.mainCamera().getFov(),w,h,mc.getWindow().getWidth()/(double)mc.getWindow().getHeight());int x=(int)Math.round(p.x()),y=(int)Math.round(p.y());
  // 保留既有投影/边界位置；边缘与背后继续明确标注，不能把背后伪装成屏内目标。
  if(targetMarker){for(var pixel:RING)g.fill(x+pixel.x(),y+pixel.y(),x+pixel.x()+1,y+pixel.y()+1,color);g.fill(x,y,x+1,y+1,color);}
  else g.text(mc.font,"＋",x-mc.font.width("＋")/2,y-4,color);
  if(p.edge())g.text(mc.font,p.behind()?"后":"边",x+8,y-4,color);
 }
 public static void activate(V forward){target=Instructor.Target.from(forward);FlightView.reset();}
 public static void reset(){target=null;FlightView.reset();}
 public static V target(){if(target==null)throw new IllegalStateException("目标尚未初始化");return target.direction();}
 public static boolean mouse(double x,double y){if(!M0DClient.ownsAim())return false;
  if(!Double.isFinite(x)||!Double.isFinite(y)){M0DClient.request(0);return true;}var mc=Minecraft.getInstance();if(!M0DClient.isAimActive()||target==null||FlightControls.inputSuspended()||mc.gui.screen()!=null||!mc.isWindowActive()||!mc.mouseHandler.isMouseGrabbed())return true;if(x==0&&y==0)return true;
  V before=target.direction();if(FlightControls.FREE.isDown())FlightView.mouse(x,y);else target=CameraTargetSphere.advance(target,x,y);
  dev.elytra.control.probe.m0d.M0DTrace.event(mc.player,"M2鼠标适配消费",Map.of("之前目标",before,"之后目标",target.direction(),"自由观察",FlightControls.FREE.isDown(),"鼠标X",x,"鼠标Y",y));return true;
 }
}
