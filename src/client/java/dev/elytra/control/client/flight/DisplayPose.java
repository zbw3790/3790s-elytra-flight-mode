package dev.elytra.control.client.flight;
import java.util.*;
import dev.elytra.control.flight.*;
import dev.elytra.control.flight.FlightModel.*;
import dev.elytra.control.client.probe.m0d.M0DClient;
import dev.elytra.control.visual.VisualProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
/** 只读显示帧；实体/世界/飞行代次变化时直接重置，不跨传送插值。 */
public final class DisplayPose {
 public record Frame(Entity entity,long generation,Q before,Q now,long received) {public Q sample(float partial){return DisplayMath.mix(before,now,partial);}}
 private static final ThreadLocal<Integer> EXTRACT=ThreadLocal.withInitial(()->0);
 public static void beginExtract(){EXTRACT.set(EXTRACT.get()+1);}
 public static void endExtract(){EXTRACT.set(Math.max(0,EXTRACT.get()-1));}
 public static boolean worldExtract(){return EXTRACT.get()>0;}private static Frame local,remote;private static long clock,highestLease,lastStep,lastGeneration;private static boolean closed;private static UUID run;
 public static void initialize(){VisualProtocol.initialize();ClientPlayNetworking.registerGlobalReceiver(VisualProtocol.Pose.TYPE,(p,c)->{if(c.player()==Minecraft.getInstance().player)receive(p);});
  ClientPlayConnectionEvents.JOIN.register((h,s,c)->resetConnection());ClientPlayConnectionEvents.DISCONNECT.register((h,c)->resetConnection());
  ClientTickEvents.END_CLIENT_TICK.register(mc->{clock++;var player=mc.player;
   if(player==null||!M0DClient.isAimActive()||M0DClient.flightState()==null){local=null;FlightView.reset();}
   else {Q q=M0DClient.flightState().attitude();long g=M0DClient.generation();boolean reset=local==null||local.entity()!=player||local.generation()!=g||local.entity().level()!=mc.level;
    local=new Frame(player,g,reset?q:local.now(),q,clock);FlightView.tick(q,reset);}
   if(remote!=null&&(mc.level==null||remote.entity().level()!=mc.level||mc.level.getEntity(remote.entity().getId())!=remote.entity()||!remote.entity().isAlive())){remote=null;closed=true;}
   if(remote!=null&&clock-remote.received()>10)remote=null;
  });
 }
 private static void resetConnection(){local=null;remote=null;run=null;highestLease=lastStep=lastGeneration=0;closed=false;FlightView.reset();}
 public static void receive(VisualProtocol.Pose p){var mc=Minecraft.getInstance();
  if(mc.level==null||mc.player==null||(dev.elytra.control.probe.m0d.M0DProtocol.technical()&&!p.player().toString().equals(System.getProperty("efc.m0d.player","")))||!p.world().equals(mc.level.dimension().identifier().toString())||p.lease()<1||p.generation()<1||p.step()<0)return;
  if(run!=null&&!run.equals(p.run())||p.lease()<highestLease)return;if(run==null)run=p.run();
  if(p.lease()==highestLease&&(closed||p.generation()<lastGeneration||p.generation()==lastGeneration&&p.step()<=lastStep))return;
  boolean reset=p.lease()!=highestLease||p.generation()!=lastGeneration;highestLease=p.lease();lastStep=p.step();lastGeneration=p.generation();closed=!p.active();
  if(closed){remote=null;return;}var entity=mc.level.getEntity(p.entity());
  if(entity==null||entity==mc.player||!entity.getUUID().equals(p.player()))return;
  Q q=p.q().normalized();remote=new Frame(entity,p.generation(),reset||remote==null||remote.entity()!=entity?q:remote.now(),q,clock);
 }
 public static Q pose(Entity e,float partial){if(local!=null&&local.entity()==e&&M0DClient.isAimActive())return local.sample(partial);if(remote!=null&&remote.entity()==e&&e.level()==Minecraft.getInstance().level)return remote.sample(partial);return null;}
 public static boolean local(){return local!=null&&M0DClient.isAimActive()&&Minecraft.getInstance().getCameraEntity()==Minecraft.getInstance().player;}
 public static Frame remote(){return remote;}
}
