package dev.elytra.control.probe.m0d;

import dev.elytra.control.ElytraFlightControl;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 本轮独立实验协议；客户端消息不携带可选择的坐标或速度。 */
public final class M0DProtocol {
    public static final int VERSION = 16;
    public static final String ELIGIBILITY_CAPABILITY = "SERVER_GAME_GLIDE_STATE_1";
    public static final String INPUT_CAPABILITY="BOUNDED_COALESCE_EVENT_ACK_HOLD_1";
    public static final int FREE_REQUEST=6, FREE_FLAG=8;
    public static boolean technical(){return Boolean.getBoolean("efc.m0d.experiment")||Boolean.getBoolean("efc.m0d.observe");}
    public static boolean freeEnabled(){return !technical()|| m2Enabled()&&Boolean.getBoolean("efc.m4f.free")&&Boolean.getBoolean("efc.m4f.allowFree");}
    public static final int M2_REQUEST=2000;
    public static boolean m2Enabled(){return !technical()||m1Enabled()&&Boolean.getBoolean("efc.m2.enabled");}
    public static final int M1_WINDOW=600, M1_REQUEST=1000;
    public static boolean m1Enabled(){return !technical()||enabled() && Boolean.getBoolean("efc.m1.enabled");}
    public static final int REQUEST = 1, READY = 2, CONTROL = 3, CLOSE = 4, STOP_ACK = 5;
    public static final int OFFER = 1, ACTIVATE = 2, SNAPSHOT = 3, STOP = 4, RELEASE = 5;
    public static final int MAX_WINDOW = 40, MAX_REPLAY = dev.elytra.control.flight.ControlInput.MAX_PREDICTIONS;
    public static final Vec3 VELOCITY = new Vec3(0.12, -0.02, 0.0);
    private static int readVersion(RegistryFriendlyByteBuf b){int v=b.readVarInt();if(v!=VERSION)throw new IllegalArgumentException("不兼容的运动协议："+v);return v;}
    private static boolean registered;
    private static Function<LivingEntity, Vec3> replacement = entity -> null;
    private static ToLongFunction<Entity> clientOwnership = entity -> 0;

    private M0DProtocol() { }

    public static boolean enabled() { return !technical()||Boolean.getBoolean("efc.m0d.experiment"); }

    /** 当前RC只接纳集成服内存连接或回环网络，不据“集成服”放开LAN/公网。 */
    public static boolean localTransport(boolean integrated,java.net.SocketAddress remote) {
        return integrated&&remote instanceof io.netty.channel.local.LocalAddress
            || remote instanceof java.net.InetSocketAddress address&&address.getAddress()!=null&&address.getAddress().isLoopbackAddress();
    }

    public static void initialize() {
        if (registered) return;
        registered = true;
        PayloadTypeRegistry.serverboundPlay().register(ClientMessage.TYPE, ClientMessage.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerMessage.TYPE, ServerMessage.CODEC);
    }

    public static void setClientReplacement(Function<LivingEntity, Vec3> callback) { replacement = callback; }
    public static Vec3 clientReplacement(LivingEntity entity) { return replacement.apply(entity); }

    /** 只在三个已确认外力入口的实际赋值之前调用；自有公式、普通同步和伤害不经过此处。 */
    public static void setClientImpulseOwnership(ToLongFunction<Entity> callback) { clientOwnership = callback; }
    public static boolean suppressImpulse(Entity entity, Vec3 requested, String source) {
        if (entity instanceof ServerPlayer player) return M0DServer.suppressImpulse(player, requested, source);
        long generation = entity.level().isClientSide() ? clientOwnership.applyAsLong(entity) : 0;
        if (generation <= 0) return false;
        M0DTrace.event(entity, "客户端原版外力写入已屏蔽", Map.of("代次", generation, "来源", source,
            "原速度", M0DTrace.vector(entity.getDeltaMovement()), "被屏蔽请求速度", M0DTrace.vector(requested)));
        return true;
    }

    public record ClientMessage(int protocol, int kind, long generation, long seq, long ack, long arg, Vec3 target, dev.elytra.control.flight.ControlInput.Frame frame)
            implements CustomPacketPayload {
        public ClientMessage(int protocol,int kind,long generation,long seq,long ack,long arg,Vec3 target){this(protocol,kind,generation,seq,ack,arg,target,kind==CONTROL?new dev.elytra.control.flight.ControlInput.Frame(arg==513,java.util.List.of()):null);}
        public ClientMessage {if((kind==CONTROL)!=(frame!=null))throw new IllegalArgumentException("控制帧配置错误");if(target!=null)new dev.elytra.control.flight.Instructor.Input(new dev.elytra.control.flight.FlightModel.V(target.x,target.y,target.z),1);}
        public ClientMessage(int protocol,int kind,long generation,long seq,long ack,long arg){this(protocol,kind,generation,seq,ack,arg,null);}
        public static final Type<ClientMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ElytraFlightControl.MOD_ID, "control_v16_m4x1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ClientMessage> CODEC = new StreamCodec<>() {
            @Override public ClientMessage decode(RegistryFriendlyByteBuf b) {
                if(b.readableBytes()>1024)throw new IllegalArgumentException("3790's Elytra Flight Control 控制载荷超过1024字节");
                return new ClientMessage(readVersion(b), b.readVarInt(), b.readLong(), b.readLong(), b.readLong(), b.readLong(), readTarget(b),readFrame(b));
            }
            @Override public void encode(RegistryFriendlyByteBuf b, ClientMessage m) {
                b.writeVarInt(m.protocol()); b.writeVarInt(m.kind()); b.writeLong(m.generation());
                b.writeLong(m.seq()); b.writeLong(m.ack()); b.writeLong(m.arg()); writeTarget(b,m.target());writeFrame(b,m.frame());
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static dev.elytra.control.flight.ControlInput.Frame readFrame(RegistryFriendlyByteBuf b){
        if(!b.readBoolean())return null;boolean suspended=b.readBoolean();int size=b.readVarInt();
        if(size<0||size>dev.elytra.control.flight.ControlInput.MAX_EVENTS)throw new IllegalArgumentException("事件列表超限");
        var events=new java.util.ArrayList<dev.elytra.control.flight.ControlInput.KeyEvent>(size);
        for(int n=0;n<size;n++)events.add(new dev.elytra.control.flight.ControlInput.KeyEvent(b.readLong(),b.readUnsignedByte(),b.readBoolean()));
        return new dev.elytra.control.flight.ControlInput.Frame(suspended,events);
    }
    private static void writeFrame(RegistryFriendlyByteBuf b,dev.elytra.control.flight.ControlInput.Frame f){b.writeBoolean(f!=null);if(f!=null){b.writeBoolean(f.suspended());b.writeVarInt(f.events().size());for(var e:f.events()){b.writeLong(e.id());b.writeByte(e.levels());b.writeBoolean(e.syncRelease());}}}
    public record AimSnapshot(Vec3 target, dev.elytra.control.flight.Instructor.State controller,long bits){
        public AimSnapshot {new dev.elytra.control.flight.Instructor.Input(new dev.elytra.control.flight.FlightModel.V(target.x,target.y,target.z),bits);java.util.Objects.requireNonNull(controller);}
    }
    private static Vec3 readTarget(RegistryFriendlyByteBuf b){return b.readBoolean()?new Vec3(b.readDouble(),b.readDouble(),b.readDouble()):null;}
    private static void writeTarget(RegistryFriendlyByteBuf b,Vec3 v){b.writeBoolean(v!=null);if(v!=null){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}}
    private static AimSnapshot readAim(RegistryFriendlyByteBuf b){
        Vec3 t=readTarget(b);if(t==null)return null;
        var axes=new dev.elytra.control.flight.Instructor.Axes(b.readDouble(),b.readDouble(),b.readDouble());int side=b.readInt(),mode=b.readVarInt();
        if(mode<0||mode>=dev.elytra.control.flight.Instructor.Mode.values().length)throw new IllegalArgumentException("机动模式越界");
        var anchor=new dev.elytra.control.flight.FlightModel.V(b.readDouble(),b.readDouble(),b.readDouble());
        return new AimSnapshot(t,new dev.elytra.control.flight.Instructor.State(axes,side,dev.elytra.control.flight.Instructor.Mode.values()[mode],anchor),b.readLong());
    }
    private static void writeAim(RegistryFriendlyByteBuf b,AimSnapshot a){writeTarget(b,a==null?null:a.target());if(a!=null){b.writeDouble(a.controller().output().pitch());b.writeDouble(a.controller().output().roll());b.writeDouble(a.controller().output().yaw());b.writeInt(a.controller().turnSide());b.writeVarInt(a.controller().mode().ordinal());b.writeDouble(a.controller().anchor().x());b.writeDouble(a.controller().anchor().y());b.writeDouble(a.controller().anchor().z());b.writeLong(a.bits());}}
    private static dev.elytra.control.flight.FlightModel.State readFlight(RegistryFriendlyByteBuf b){
        if(!b.readBoolean())return null;
        return new dev.elytra.control.flight.FlightModel.State(new dev.elytra.control.flight.FlightModel.V(b.readDouble(),b.readDouble(),b.readDouble()),
            new dev.elytra.control.flight.FlightModel.Q(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()),b.readDouble(),b.readBoolean(),b.readBoolean(),new dev.elytra.control.flight.FlightModel.OmegaBody(b.readDouble(),b.readDouble(),b.readDouble()),new dev.elytra.control.flight.FlightModel.ThrottleMemory(b.readBoolean(),b.readBoolean(),b.readBoolean()));
    }
    private static void writeFlight(RegistryFriendlyByteBuf b,dev.elytra.control.flight.FlightModel.State s){
        b.writeBoolean(s!=null);if(s==null)return;
        b.writeDouble(s.velocity().x());b.writeDouble(s.velocity().y());b.writeDouble(s.velocity().z());
        b.writeDouble(s.attitude().x());b.writeDouble(s.attitude().y());b.writeDouble(s.attitude().z());b.writeDouble(s.attitude().w());
        b.writeDouble(s.throttle());b.writeBoolean(s.wep());b.writeBoolean(s.limited());b.writeDouble(s.omega().x());b.writeDouble(s.omega().y());b.writeDouble(s.omega().z());
        b.writeBoolean(s.throttleMemory().more());b.writeBoolean(s.throttleMemory().less());b.writeBoolean(s.throttleMemory().lessBlocked());
    }
    public record ServerMessage(int protocol, int kind, long generation, long s, long ack, long bridgeId,
            Vec3 position, Vec3 velocity, int flags, String reason, dev.elytra.control.flight.FlightModel.State flight, AimSnapshot aim,long eventAck,long receivedInputSeq,boolean inputHold) implements CustomPacketPayload {
        public ServerMessage(int protocol,int kind,long generation,long s,long ack,long bridgeId,Vec3 position,Vec3 velocity,int flags,String reason,dev.elytra.control.flight.FlightModel.State flight,AimSnapshot aim){this(protocol,kind,generation,s,ack,bridgeId,position,velocity,flags,reason,flight,aim,0,0,false);}
        public ServerMessage(int protocol,int kind,long generation,long s,long ack,long bridgeId,Vec3 position,Vec3 velocity,int flags,String reason){this(protocol,kind,generation,s,ack,bridgeId,position,velocity,flags,reason,null,null);}
        public ServerMessage(int protocol,int kind,long generation,long s,long ack,long bridgeId,Vec3 position,Vec3 velocity,int flags,String reason,dev.elytra.control.flight.FlightModel.State flight){this(protocol,kind,generation,s,ack,bridgeId,position,velocity,flags,reason,flight,null);}
        public static final Type<ServerMessage> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ElytraFlightControl.MOD_ID, "snapshot_v16_m4x1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ServerMessage> CODEC = new StreamCodec<>() {
            @Override public ServerMessage decode(RegistryFriendlyByteBuf b) {
                return new ServerMessage(readVersion(b), b.readVarInt(), b.readLong(), b.readLong(), b.readLong(), b.readLong(),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                    b.readVarInt(), b.readUtf(160), readFlight(b), readAim(b),b.readLong(),b.readLong(),b.readBoolean());
            }
            @Override public void encode(RegistryFriendlyByteBuf b, ServerMessage m) {
                b.writeVarInt(m.protocol()); b.writeVarInt(m.kind()); b.writeLong(m.generation()); b.writeLong(m.s());
                b.writeLong(m.ack()); b.writeLong(m.bridgeId());
                b.writeDouble(m.position().x); b.writeDouble(m.position().y); b.writeDouble(m.position().z);
                b.writeDouble(m.velocity().x); b.writeDouble(m.velocity().y); b.writeDouble(m.velocity().z);
                b.writeVarInt(m.flags()); b.writeUtf(m.reason(), 160); writeFlight(b,m.flight()); writeAim(b,m.aim());b.writeLong(m.eventAck());b.writeLong(m.receivedInputSeq());b.writeBoolean(m.inputHold());
            }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
