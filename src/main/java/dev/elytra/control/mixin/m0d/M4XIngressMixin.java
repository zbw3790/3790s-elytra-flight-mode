package dev.elytra.control.mixin.m0d;
import dev.elytra.control.flight.ControlInput;
import dev.elytra.control.probe.m0d.*;
import net.minecraft.server.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 固定Fabric的HEAD注入priority=999；998后插入HEAD，运行在其调度/取消前。只管EFC入站。 */
@Mixin(value=ServerGamePacketListenerImpl.class,priority=998)
public abstract class M4XIngressMixin implements M4XIngressAccess {
 @Shadow public net.minecraft.server.level.ServerPlayer player;
 @Unique private final ControlInput.Ingress efc$m4xBudget=new ControlInput.Ingress(System.nanoTime());
 public java.util.List<Long> efc$m4xIngressMetrics(){return efc$m4xBudget.metrics();}
 @Inject(method="handleCustomPayload",at=@At("HEAD"),cancellable=true,require=1)
 private void efc$m4xReceive(ServerboundCustomPayloadPacket packet,CallbackInfo ci){
  if(!(packet.payload() instanceof M0DProtocol.ClientMessage m))return;
  int result=efc$m4xBudget.receive(m.kind()==M0DProtocol.CONTROL,System.nanoTime());
  if(result!=0)ci.cancel();
  if(result==2)player.level().getServer().execute(()->((ServerGamePacketListenerImpl)(Object)this).disconnect(Component.literal("飞控入站洪泛，安全断开")));
 }
}
