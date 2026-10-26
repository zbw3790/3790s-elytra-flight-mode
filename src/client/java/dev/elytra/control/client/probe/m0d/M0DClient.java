package dev.elytra.control.client.probe.m0d;

import dev.elytra.control.mixin.m0d.M0DEntityCollisionAccess;
import dev.elytra.control.probe.m0d.M0DProtocol;
import dev.elytra.control.probe.m0d.M0DProtocol.ClientMessage;
import dev.elytra.control.probe.m0d.M0DProtocol.ServerMessage;
import dev.elytra.control.probe.m0d.M0DTrace;
import java.util.ArrayDeque;
import dev.elytra.control.flight.ControlInput;
import java.util.List;
import dev.elytra.control.flight.FlightModel;
import dev.elytra.control.flight.Instructor;
import dev.elytra.control.flight.ControlSelection;
import dev.elytra.control.client.flight.AimView;
import dev.elytra.control.client.flight.FlightControls;
import net.minecraft.network.chat.Component;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 仅限本轮许可窗的本地预测与显式快照重基准。 */
public final class M0DClient {
    private static final Vec3 DIVERGENT = new Vec3(0.0, -0.02, 0.12);
    private static long clock, nonce = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE / 2);
    private static long snapshots, rebases, replayed;
    private static double lastError;
    private static boolean divergent;
    private static String lastReason="未接管";
    private static Pending pending;
    private static Session session;
    private static final ControlSelection selection=new ControlSelection();
    private static double hudSpeed;

    private M0DClient() { }

    public static void initialize() {
        M0DProtocol.initialize();
        M0DProtocol.setClientReplacement(M0DClient::replacement);
        M0DProtocol.setClientImpulseOwnership(M0DClient::ownedGeneration);
        ClientPlayNetworking.registerGlobalReceiver(ServerMessage.TYPE, (message, context) -> receive(context.player(), message));
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{selection.resetConnection();FlightControls.connection();});
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            // 输入暂停不CLOSE；只有真实物理模拟才推进预测/控制采样。
            // GUI/失焦只暂停输入，世界真暂停时不推进本地逻辑刻。
            if(!client.isPaused())++clock;else if(session!=null)session.wasHeld=true;
            if (pending != null && (clock > pending.deadline || pending.player != client.player
                    || client.player.level() != pending.level || !M0DProtocol.enabled())) clear("客户端请求失效");
            if (session != null && (session.player != client.player || client.player.level() != session.level
                    || !client.player.isAlive() || client.player.isRemoved())) clear("客户端实体或世界代次失效");
            if (session != null && !session.active && !session.retiring && !session.closing && clock > session.deadline) {
                close("客户端提议等待超时");
                clear("客户端未获激活，保留完整原版");
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clear("客户端断线清理");
            selection.resetConnection();
            divergent = false;
        });
    }

    public static boolean requestDynamic(int ticks){return requestConfigured(ticks,true,false);}
    public static boolean request(int ticks) {return requestConfigured(ticks,false,false);}
    public static boolean requestAim(int ticks){return requestConfigured(ticks,true,true);}
    public static boolean requestFree(){return requestConfigured(0,true,true,true);}
    public static boolean vanillaSelected(){return selection.vanillaSelected();}
    public static double hudSpeed(){return hudSpeed;}
    /** 只走现有CLOSE/REQUEST握手，绝不本地删除仍持权会话来模拟原版。 */
    public static void toggleVanillaControl(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.gui.screen()!=null||!mc.isWindowActive())return;
        var phase=isRetiring()?ControlSelection.Phase.RETIRING:isActive()?ControlSelection.Phase.ACTIVE:
            session!=null?ControlSelection.Phase.OFFERED:pending!=null?ControlSelection.Phase.REQUESTED:ControlSelection.Phase.VANILLA;
        boolean qualified=!FlightControls.VANILLA.isUnbound()&&M0DProtocol.m2Enabled()&&M0DTrace.allowed(mc.player)&&eligible(mc.player)&&ClientPlayNetworking.canSend(ClientMessage.TYPE);
        var action=selection.press(phase,qualified);
        M0DTrace.event(mc.player,"用户原版鞘翅控制切换",Map.of("控制阶段",phase.name(),"切换决定",action.name(),"选择原版",selection.vanillaSelected()));
        if(action==ControlSelection.Action.CLOSE){
            close("用户选择原版鞘翅控制");
        }else if(action==ControlSelection.Action.REQUEST){
            boolean sent=M0DProtocol.freeEnabled()?requestFree():requestAim(M0DProtocol.M1_WINDOW);
            if(!sent){selection.requestFailed();notice("飞控暂不可用，保持原版控制");}
        }else notice(action==ControlSelection.Action.WAIT?"正在安全交接，请稍后再切换":
            !ClientPlayNetworking.canSend(ClientMessage.TYPE)?"服务器未安装兼容EFC（需要协议16）；保持原版控制":
            !mc.player.isFallFlying()?"请先开始游戏认可的鞘翅滑翔":"当前运动状态暂不安全，保持原版控制");
    }
    private static void notice(String text){Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal(text),false);}
    public static boolean isFreeActive(){return session!=null&&session.free&&isActive();}
    public static boolean isAimActive(){return session!=null&&session.aimMode&&isActive();}
    public static boolean ownsAim(){return session!=null&&session.aimMode&&ownsDynamic();}
    public static Instructor.Result instructorResult(){return session==null?null:session.result;}
    public static boolean ownsDynamic(){return session!=null&&session.dynamic&&(session.active||session.retiring||session.closing);}
    public static FlightModel.State flightState(){return session==null?null:session.flight;}
    public static String debugReadout(){
        Session c=session;if(c==null||!c.dynamic||c.flight==null)return "M1 "+mode()+" 上次原因："+lastReason;
        if(c.free)return "自由试飞 · 无累计到期 · "+FlightControls.EXIT.getTranslatedKeyMessage().getString()+" 退出";
        var f=c.flight.attitude().rotate(new FlightModel.V(0,0,1));
        return String.format(java.util.Locale.ROOT,"M1 %s %.1f格/秒 %.0f%%%s F(%.1f,%.1f,%.1f) 余%d R",mode().substring(0,1),c.flight.velocity().length()*20,c.flight.throttle()*100,c.flight.wep()?"WEP":"",f.x(),f.y(),f.z(),Math.max(0,c.window-c.lastServerStep));
    }
    private static boolean requestConfigured(int ticks,boolean dynamic,boolean aim) {return requestConfigured(ticks,dynamic,aim,false);}
    private static boolean requestConfigured(int ticks,boolean dynamic,boolean aim,boolean free) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (ticks == 0&&!free) {
            close("客户端主动关闭");
            return true;
        }
        if (player == null || !M0DProtocol.enabled() || !M0DTrace.allowed(player) || !eligible(player)
                || pending != null || session != null || (free?!M0DProtocol.freeEnabled():ticks < 1 || ticks > (dynamic?M0DProtocol.M1_WINDOW:M0DProtocol.MAX_WINDOW)) || dynamic&&!M0DProtocol.m1Enabled() || aim&&!M0DProtocol.m2Enabled()
                || (!M0DProtocol.technical()&&FlightControls.VANILLA.isUnbound()) || selection.vanillaSelected() || !ClientPlayNetworking.canSend(ClientMessage.TYPE)) {
            if (player != null) M0DTrace.event(player, "客户端未发送请求", Map.of("窗口", ticks));
            return false;
        }
        long request = ++nonce;
        pending = new Pending(player, player.level(), request, ticks, clock + 100, dynamic,aim,free);
        send(free?M0DProtocol.FREE_REQUEST:M0DProtocol.REQUEST, 0, request, 0, free?0:ticks+(aim?M0DProtocol.M2_REQUEST:dynamic?M0DProtocol.M1_REQUEST:0));
        M0DTrace.event(player, "客户端发送请求", Map.of("随机数", request, "窗口", ticks));
        return true;
    }

    /** 仅本地已激活或仍持权退出的同一实体/世界代次有屏蔽资格。 */
    private static long ownedGeneration(Entity entity) {
        Session current = session;
        return current != null && current.player == entity && current.level == entity.level()
            && entity.isAlive() && !entity.isRemoved() && M0DProtocol.enabled() && M0DTrace.allowed(entity)
            && (current.active || current.retiring) ? current.generation : 0;
    }

    public static boolean isActive() { return session != null && session.active && !session.closing; }
    public static boolean isRetiring() { return session != null && (session.retiring || session.closing); }
    public static boolean isVanilla() { return pending == null && session == null; }
    public static long generation() { return session == null ? 0 : session.generation; }
    public static long snapshotsApplied() { return snapshots; }
    public static long rebaseCount() { return rebases; }
    public static long replayedInputs() { return replayed; }
    public static long lastAppliedServerStep() { return session == null ? 0 : session.lastServerStep; }
    public static double lastRebaseError() { return lastError; }
    public static void setDivergentPrediction(boolean value) {
        divergent = value;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) M0DTrace.event(player, "客户端预测模型切换", Map.of("分歧", value));
    }

    /** 在真实本地玩家刻开头发控制；未激活时不改变原版发包。 */
    public static void beforePlayerTick(LocalPlayer player) {
        if (M0DTrace.allowed(player)) M0DTrace.event(player, "客户端玩家刻开始", Map.of("模式", mode()));
        Session current = session;
        if (current == null || current.player != player || !current.active || current.closing || current.retiring) return;
        if(Minecraft.getInstance().isPaused())return;
        current.hudStart=player.position();
        if (!M0DProtocol.enabled() || !M0DTrace.allowed(player) || !eligible(player)
                || !current.free&&clock > current.deadline) {
            close("客户端资格或许可窗口失效");
            return;
        }

        if(M0DProtocol.technical()&&current.dynamic&&!FlightControls.inputSuspended()&&FlightControls.EXIT.isDown()){close("紧急按键退出");return;}
        if (current.lastInputClock == clock) return;
        current.lastInputClock = clock;
        if(current.nextSeq>=ControlInput.MAX_SEQUENCE){close("输入序号达到边界");return;}
        current.currentSeq = ++current.nextSeq;
        current.intent=current.dynamic?FlightControls.read():FlightModel.Intent.NEUTRAL;
        current.hold=FlightControls.inputSuspended();
        long bits=current.hold?1:current.aimMode?FlightControls.rawBits():current.intent.encode();
        if(current.aimMode)current.aimInput=new Instructor.Input(AimView.target(),bits);
        int levels=((bits&128)!=0?1:0)|((bits&256)!=0?2:0);
        if(!current.hold){
            if(current.wasHeld){if(!queueEdge(current,0,true))return;current.sampledLevels=0;}
            if(levels!=current.sampledLevels){if(!queueEdge(current,levels,false))return;current.sampledLevels=levels;}
        }
        current.wasHeld=current.hold;
        current.frame=new ControlInput.Frame(current.hold,List.copyOf(current.events));
        send(M0DProtocol.CONTROL, current.generation, current.currentSeq, current.lastServerStep, bits);
        if(current.dynamic&&!current.aimMode&&clock%5==0)Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal(debugReadout()),false);
        M0DTrace.event(player, "客户端发送控制", Map.of("代次", current.generation, "序号", current.currentSeq,
            "确认步", current.lastServerStep));
    }

    public static void afterPlayerTick(LocalPlayer player) {
        if(isAimActive()&&session.player==player&&session.hudStart!=null){
            double speed=player.position().distanceTo(session.hudStart)*20;
            if(Double.isFinite(speed))hudSpeed=speed;
            session.hudStart=null;
        }
        if(session!=null&&session.dynamic&&session.active&&!session.closing&&session.flight!=null)session.flight=session.flight.collision(modelV(player.getDeltaMovement()));
        if (M0DTrace.allowed(player)) M0DTrace.event(player, "客户端玩家刻结束", Map.of("模式", mode()));
    }

    private static Vec3 replacement(LivingEntity entity) {
        Session current = session;
        if (current == null || current.player != entity || !current.active || current.closing || current.retiring) return null;
        if (!eligible(current.player) || !M0DProtocol.enabled() || !M0DTrace.allowed(entity)) {
            close("客户端预测前资格失效");
            return null;
        }
        if (Minecraft.getInstance().isPaused() || current.currentSeq == 0 || current.lastPredictedSeq == current.currentSeq) return null;
        if (current.inputs.size() >= M0DProtocol.MAX_REPLAY) {
            close("客户端未确认输入达到上限");
            return null;
        }
        Vec3 velocity;
        if(current.dynamic){
            current.flight=ControlInput.prepare(current.flight,current.frame.events(),current.predictedEvent,current.aimMode?current.aimInput.bits():current.intent.encode(),current.hold);
            if(current.aimMode){var r=Instructor.step(current.flight,current.aimInput,current.controller);current.controller=r.state();current.result=r;current.intent=r.intent();}
            current.flight=FlightModel.step(current.flight,current.intent,current.hold);velocity=mcV(current.flight.velocity());if(divergent)velocity=velocity.add(.03,0,0);
        } else velocity = divergent ? DIVERGENT : M0DProtocol.VELOCITY;
        current.lastPredictedSeq = current.currentSeq;
        current.predictedEvent=current.nextEvent;
        current.inputs.addLast(new Input(current.currentSeq,current.lastServerStep+current.inputs.size()+1,velocity,current.intent,current.aimInput,current.hold,current.frame.events()));
        M0DTrace.event(entity, "客户端预测公式", Map.of("代次", current.generation, "序号", current.currentSeq,
            "分歧", divergent, "请求速度", vector(velocity), "待确认数", current.inputs.size()));
        return velocity;
    }

    private static void receive(LocalPlayer player, ServerMessage message) {
        if (message.protocol() != M0DProtocol.VERSION || !finite(message.position()) || !finite(message.velocity())
                || (message.kind() != M0DProtocol.STOP && message.kind() != M0DProtocol.RELEASE && message.velocity().lengthSqr() > (message.flight()!=null?1:0.15*0.15) + 1.0E-10)
                || message.generation() <= 0) {
            close("客户端拒绝无效快照");
            return;
        }
        if (message.kind() != M0DProtocol.STOP && (!M0DProtocol.enabled() || !M0DTrace.allowed(player))) {
            close("客户端实验开关或身份失效");
            return;
        }
        M0DTrace.event(player, "客户端收到协议消息", Map.of("类别", message.kind(), "代次", message.generation(),
            "服务步", message.s(), "确认序号", message.ack(), "桥", message.bridgeId(), "原因", message.reason()));
        if (message.kind() == M0DProtocol.OFFER) {
            Pending request = pending;
            if (request == null || request.player != player || request.level != player.level() || message.ack() != request.nonce
                    || clock > request.deadline || !M0DProtocol.enabled() || !M0DTrace.allowed(player) || !eligible(player)
                    || (request.free ? message.bridgeId()!=0 || (message.flags()&M0DProtocol.FREE_FLAG)==0 : message.bridgeId()<1 || message.bridgeId()>request.ticks || (message.flags()&M0DProtocol.FREE_FLAG)!=0)) return;
            if((message.flight()!=null)!=request.dynamic || (message.aim()!=null)!=request.aim)return;
            session = new Session(player, message.generation(), clock + request.ticks + 20,request.dynamic,request.ticks,request.aim,request.free);
            if(request.dynamic)FlightControls.reset();
            pending = null;
            send(M0DProtocol.READY, message.generation(), 0, message.s(), 1);
            M0DTrace.event(player, "客户端发送就绪", Map.of("代次", message.generation()));
            return;
        }
        Session current = session;
        if (current == null || current.player != player || current.level != player.level() || current.generation != message.generation()) return;
        if((message.flight()!=null)!=current.dynamic || (message.aim()!=null)!=current.aimMode || ((message.flags()&M0DProtocol.FREE_FLAG)!=0)!=current.free){close("配置快照不匹配");return;}
        if(message.kind()!=M0DProtocol.RELEASE)current.reason=message.reason();
        if (message.kind() == M0DProtocol.ACTIVATE) {
            if(!M0DProtocol.technical()&&FlightControls.VANILLA.isUnbound()){close("请先绑定飞控切换键");return;}
            if (current.active || current.retiring || current.closing || selection.vanillaSelected() || !eligible(player)) return;
            current.active = true;
            current.lastServerStep = message.s();
            applyAnchor(player, message); current.flight=message.flight();
            if(current.aimMode){current.controller=message.aim().controller();AimView.activate(current.flight.attitude().rotate(new FlightModel.V(0,0,1)));}
            M0DTrace.event(player, "客户端收到激活并重基准", Map.of("代次", current.generation, "服务步", message.s()));
        } else if (message.kind() == M0DProtocol.SNAPSHOT) {
            if (!current.active || current.retiring || current.closing || message.s() <= current.lastServerStep
                    || message.ack() < current.lastAck || message.ack() > current.nextSeq
                    || message.eventAck()<current.lastEventAck || message.eventAck()>current.nextEvent
                    || message.receivedInputSeq()<message.ack() || message.receivedInputSeq()>current.nextSeq) return;
            Vec3 before = player.position();
            // 物理已前进但采样ACK停滞（暂未收到CONTROL）时，不能将已过去的预测刻再次叠到新锚点。
            // 采样ACK还可确认“合并跳过”的输入；事件只按独立eventAck回收。
            current.inputs.removeIf(input -> ControlInput.predictionConsumed(input.seq,input.predictedStep,message.ack(),message.s()));
            current.lastAck = message.ack();current.lastEventAck=message.eventAck();
            current.events.removeIf(e->e.id()<=message.eventAck());current.predictedEvent=message.eventAck();
            current.lastServerStep = message.s();
            applyAnchor(player, message); current.flight=message.flight(); if(current.aimMode)current.controller=message.aim().controller();
            long count = 0;var rebasedInputs=new ArrayDeque<Input>();
            for (Input input : current.inputs) {
                if (++count > M0DProtocol.MAX_REPLAY) { close("客户端重放上限异常"); return; }
                Vec3 requested=input.velocity;
                if(current.dynamic){FlightModel.Intent intent=input.intent;
                    current.flight=ControlInput.prepare(current.flight,input.events,current.predictedEvent,current.aimMode?input.aim.bits():intent.encode(),input.hold);
                    if(current.aimMode){var r=Instructor.step(current.flight,input.aim,current.controller);current.controller=r.state();current.result=r;intent=r.intent();}
                    current.flight=FlightModel.step(current.flight,intent,input.hold);requested=mcV(current.flight.velocity());
                    for(var e:input.events)current.predictedEvent=Math.max(current.predictedEvent,e.id());
                }
                Vec3 clipped = ((M0DEntityCollisionAccess) player).efc$m0dCollide(requested);
                player.setPos(player.position().add(clipped));
                player.setDeltaMovement(new Vec3(clipped.x == requested.x ? requested.x : 0.0,
                    clipped.y == requested.y ? requested.y : 0.0, clipped.z == requested.z ? requested.z : 0.0));
                if(current.dynamic)current.flight=current.flight.collision(modelV(player.getDeltaMovement()));
                rebasedInputs.addLast(new Input(input.seq,message.s()+count,input.velocity,input.intent,input.aim,input.hold,input.events));
            }
            current.inputs.clear();current.inputs.addAll(rebasedInputs);
            ++snapshots; ++rebases; replayed += count;
            lastError = before.distanceTo(message.position());
            M0DTrace.event(player, "客户端快照重基准完成", Map.of("代次", current.generation, "服务步", message.s(),
                "应用输入确认", message.ack(), "事件确认",message.eventAck(), "重基准前", vector(before), "权威锚点", vector(message.position()),
                "重放后", vector(player.position()), "误差", lastError, "重放数", count));
            M0DTrace.event(player,"M4X输入与快照确认",Map.of("应用输入",current.lastAck,"事件确认",current.lastEventAck,"服务步",current.lastServerStep,"未确认物理步",List.copyOf(current.inputs),"剩余事件",List.copyOf(current.events)));
            if(current.aimMode) M0DTrace.event(player,"M2完整控制重基准完成",Map.of(
                "代次",current.generation,"服务步",message.s(),"权威动力",message.flight(),"权威目标控制",message.aim(),
                "历史输入",current.inputs.stream().map(input->input.aim).toList(),"重放动力",current.flight,
                "重放控制",current.controller,"当前独立目标",AimView.target()));
        } else if (message.kind() == M0DProtocol.RELEASE) {
            if (!current.retiring || !current.bridgeApplied || current.bridgeId != message.bridgeId()) return;
            M0DTrace.event(player, "客户端收到明确归还屏障", Map.of("代次", current.generation, "桥", current.bridgeId,
                "物理速度", vector(player.getDeltaMovement())));
            clear("客户端安全归还完成");
        } else if (message.kind() == M0DProtocol.STOP) {
            lastReason=message.reason();
            if(current.dynamic)Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal("飞控退出："+lastReason),false);
            if (current.retiring) return;
            current.active = false; current.closing = true; current.retiring = true;
            current.bridgeId = message.bridgeId();
            current.inputs.clear(); if(current.dynamic)FlightControls.reset();
            current.lastServerStep = message.s();
            // 最终锚点由随后的一次原版桥设置；这里不提前制造另一条位置交接。
            M0DTrace.event(player, "客户端停止预测等待原版桥", Map.of("代次", current.generation, "桥", current.bridgeId,
                "物理速度", vector(message.velocity())));
        }
    }

    /** 原版处理已发出传送ACK及PosRot，之后才发送自有围栏确认。 */
    public static void afterVanillaPosition(ClientboundPlayerPositionPacket packet) {
        Session current = session;
        if (current == null) return;
        LocalPlayer player = current.player;
        if (current.retiring && packet.id() == current.bridgeId) {
            M0DTrace.event(player, "客户端原版桥处理返回", Map.of("代次", current.generation, "桥", packet.id(),
                "物理速度", vector(player.getDeltaMovement()), "原版确认与位置已发送", true));
            current.bridgeApplied = true;
            send(M0DProtocol.STOP_ACK, current.generation, current.nextSeq, current.lastServerStep, current.bridgeId);
            M0DTrace.event(player, "客户端发送退出确认", Map.of("代次", current.generation, "桥", current.bridgeId));
            // 停止预测但保留窄入口屏蔽，直到服务端明确归还，避免桥后事件在持权期间重新加力。
        } else {
            clear("客户端普通传送使旧代次失效");
        }
    }

    private static void applyAnchor(LocalPlayer player, ServerMessage message) {
        player.setPos(message.position());
        player.setDeltaMovement(message.velocity());
        player.setOnGround((message.flags() & 1) != 0);
        player.horizontalCollision = (message.flags() & 2) != 0;
        player.verticalCollision = (message.flags() & 4) != 0;
    }

    private static void close(String reason) {
        lastReason=reason;
        Session current = session;
        if (current != null && !current.closing) {
            if(current.dynamic)Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal("飞控退出："+reason),false);
            current.closing = true;
            current.inputs.clear(); if(current.dynamic)FlightControls.reset();
            send(M0DProtocol.CLOSE, current.generation, current.nextSeq, current.lastServerStep, 0);
            M0DTrace.event(current.player, reason, Map.of("代次", current.generation));
        }
        pending = null;
    }

    private static boolean queueEdge(Session current,int levels,boolean sync){
        if(current.events.size()>=ControlInput.MAX_EVENTS||current.nextEvent>=ControlInput.MAX_SEQUENCE){close("未确认油门事件超限");return false;}
        current.events.add(new ControlInput.KeyEvent(++current.nextEvent,levels,sync));return true;
    }
    private static void send(int kind, long generation, long seq, long ack, long arg) {
        if (Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(ClientMessage.TYPE))
            ClientPlayNetworking.send(new ClientMessage(M0DProtocol.VERSION, kind, generation, seq, ack, arg,kind==M0DProtocol.CONTROL&&session!=null&&session.aimMode?mcV(session.aimInput.target()):null,kind==M0DProtocol.CONTROL&&session!=null?session.frame:null));
    }

    private static void clear(String reason) {
        LocalPlayer player = session != null ? session.player : pending != null ? pending.player : null;
        if (player != null) M0DTrace.event(player, reason, Map.of("模式", mode()));
        if(session!=null&&session.dynamic)FlightControls.reset();
        hudSpeed=0;
        AimView.reset();
        pending = null; session = null;
    }

    private static String mode() { return session == null ? pending == null ? "VANILLA" : "REQUESTED"
        : session.retiring || session.closing ? "RETIRING" : session.active ? "ACTIVE" : "OFFERED"; }
    private static boolean finite(Vec3 v) { return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z); }
    private static java.util.List<Double> vector(Vec3 v) { return java.util.List.of(v.x, v.y, v.z); }
    private static boolean eligible(LocalPlayer p) {
        return p.isAlive() && !p.isRemoved() && !p.isSpectator() && p.isFallFlying() && !p.onGround()
            && !p.isPassenger() && !p.onClimbable() && !p.getAbilities().flying && !p.isInWater() && !p.isInLava()
            && !p.hasEffect(MobEffects.LEVITATION) && !p.isAutoSpinAttack()
            && p.level().getEntitiesOfClass(FireworkRocketEntity.class, p.getBoundingBox().inflate(8.0)).isEmpty();
    }

    private static FlightModel.V modelV(Vec3 v){return new FlightModel.V(v.x,v.y,v.z);}
    private static Vec3 mcV(FlightModel.V v){return new Vec3(v.x(),v.y(),v.z());}
    private record Pending(LocalPlayer player, Level level, long nonce, int ticks, long deadline,boolean dynamic,boolean aim,boolean free) { }
    private record Input(long seq,long predictedStep,Vec3 velocity,FlightModel.Intent intent,Instructor.Input aim,boolean hold,List<ControlInput.KeyEvent> events) { }
    private static final class Session {
        final LocalPlayer player;
        final Level level;
        final long generation, deadline;
        final boolean aimMode;Instructor.State controller=Instructor.State.INITIAL;Instructor.Input aimInput;Instructor.Result result;
        final boolean dynamic,free;final int window;FlightModel.State flight;FlightModel.Intent intent=FlightModel.Intent.NEUTRAL;String reason="";
        final ArrayDeque<Input> inputs = new ArrayDeque<>();
        final ArrayDeque<ControlInput.KeyEvent> events=new ArrayDeque<>();
        long nextEvent,lastEventAck,predictedEvent;int sampledLevels;boolean hold,wasHeld=true;ControlInput.Frame frame=ControlInput.Frame.EMPTY;
        long nextSeq, currentSeq, lastPredictedSeq, lastAck, lastServerStep, bridgeId, lastInputClock = -1;
        Vec3 hudStart;boolean active, closing, retiring, bridgeApplied;
        Session(LocalPlayer player, long generation, long deadline,boolean dynamic,int window,boolean aim,boolean free) {
            this.aimMode=aim;this.dynamic=dynamic;this.window=window;this.free=free;
            this.player = player; this.level = player.level(); this.generation = generation; this.deadline = deadline;
        }
    }
}
