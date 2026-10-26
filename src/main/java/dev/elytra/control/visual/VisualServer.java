package dev.elytra.control.visual;
import java.util.*;
import dev.elytra.control.probe.m0d.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.level.ServerPlayer;
/** 只发布既有提交姿态；没有来自旁观者的控制接收入口。 */
public final class VisualServer {
 private static final UUID RUN=UUID.randomUUID();private static long lease;
 private record Watch(ServerPlayer watcher,ServerPlayer target){}
 private static final Map<Watch,VisualProtocol.Pose> sent=new HashMap<>();
 public static void initialize(){VisualProtocol.initialize();ServerLifecycleEvents.SERVER_STOPPED.register(s->sent.clear());ServerTickEvents.END_SERVER_TICK.register(server->{
  Set<Watch> alive=new HashSet<>();
  for(var target:server.getPlayerList().getPlayers()){
   var source=M0DServer.visual(target);if(source==null)continue;
   for(var watcher:PlayerLookup.tracking(target)){
    if(watcher==target||!ServerPlayNetworking.canSend(watcher,VisualProtocol.Pose.TYPE))continue;
    Watch key=new Watch(watcher,target);alive.add(key);var old=sent.get(key);long token=old==null?++lease:old.lease();
    if(old!=null&&old.generation()==source.generation()&&old.step()==source.step())continue;
    var p=new VisualProtocol.Pose(RUN,token,target.getUUID(),target.getId(),target.level().dimension().identifier().toString(),source.generation(),source.step(),true,source.q());
    ServerPlayNetworking.send(watcher,p);sent.put(key,p);M0DTrace.event(target,"只读旁观姿态发布",Map.of("观察者",watcher.getUUID().toString(),"显示租约",token,"代次",p.generation(),"服务步",p.step(),"Q",p.q()));
   }
  }
  for(var key:List.copyOf(sent.keySet()))if(!alive.contains(key)){
   var p=sent.remove(key);if(!key.watcher.hasDisconnected()&&ServerPlayNetworking.canSend(key.watcher,VisualProtocol.Pose.TYPE))ServerPlayNetworking.send(key.watcher,new VisualProtocol.Pose(p.run(),p.lease(),p.player(),p.entity(),p.world(),p.generation(),p.step()+1,false,p.q()));
  }
 });}
}
