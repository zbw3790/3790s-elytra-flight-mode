package dev.elytra.control.visual;
import java.util.UUID;
import dev.elytra.control.flight.FlightModel.Q;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
/** 独立v1只读展示通道；运动协议5布局不变。 */
public final class VisualProtocol {
 private static boolean registered;
 public static void initialize(){if(!registered){registered=true;PayloadTypeRegistry.clientboundPlay().register(Pose.TYPE,Pose.CODEC);}}
 public record Pose(UUID run,long lease,UUID player,int entity,String world,long generation,long step,boolean active,Q q) implements CustomPacketPayload {
  public static final Type<Pose> TYPE=new Type<>(Identifier.fromNamespaceAndPath("elytra_flight_control","visual_pose_v1"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Pose> CODEC=new StreamCodec<>(){
   public Pose decode(RegistryFriendlyByteBuf b){return new Pose(b.readUUID(),b.readLong(),b.readUUID(),b.readVarInt(),b.readUtf(128),b.readLong(),b.readLong(),b.readBoolean(),new Q(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()).normalized());}
   public void encode(RegistryFriendlyByteBuf b,Pose p){b.writeUUID(p.run);b.writeLong(p.lease);b.writeUUID(p.player);b.writeVarInt(p.entity);b.writeUtf(p.world,128);b.writeLong(p.generation);b.writeLong(p.step);b.writeBoolean(p.active);b.writeDouble(p.q.x());b.writeDouble(p.q.y());b.writeDouble(p.q.z());b.writeDouble(p.q.w());}
  };
  public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
}
