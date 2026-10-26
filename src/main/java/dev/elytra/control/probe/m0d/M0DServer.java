package dev.elytra.control.probe.m0d;

import java.util.ArrayDeque;
import dev.elytra.control.flight.ControlInput;
import dev.elytra.control.flight.ControlClock;
import dev.elytra.control.flight.FlightModel;
import dev.elytra.control.flight.Instructor;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 本轮方案A原型：真实原版SELF保留端点，包只校验，默认关闭。 */
public final class M0DServer {
    public static final Vec3 VELOCITY = new Vec3(0.12, -0.02, 0.0);
    private static final Map<ServerPlayer, Session> SESSIONS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Step> STEPS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Long> COMMITS = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Long> NONCES = new IdentityHashMap<>();
    private static final Map<ServerPlayer, Long> REJECTION_NOTICE = new IdentityHashMap<>();
    private static final Set<ServerPlayer> TICKING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<ServerPlayer> INVALIDATED_TICKS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static long generation;
    private M0DServer() { }

    public static boolean enabled() { return M0DProtocol.enabled(); }
    public static boolean isActive(ServerPlayer player) { return "ACTIVE".equals(state(player)); }
    public static String state(ServerPlayer player) { Session session = SESSIONS.get(player); return session == null ? "VANILLA" : session.state; }
    public static long commits(ServerPlayer player) { return COMMITS.getOrDefault(player, 0L); }
    public static boolean protectsTick(ServerPlayer original) { return TICKING.contains(original); }

    public record Visual(long generation,long step,FlightModel.Q q){}
    public static Visual visual(ServerPlayer player){Session s=SESSIONS.get(player);return s!=null&&s.aimMode&&s.state.equals("ACTIVE")&&sameIdentity(player,s)?new Visual(s.generation,s.s,s.flight.attitude()):null;}

    public static void initialize() {
        M0DProtocol.initialize();
        ServerPlayNetworking.registerGlobalReceiver(M0DProtocol.ClientMessage.TYPE, (message, context) -> receive(context.player(), message));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            invalidate(handler.player, "断线清代次");
            NONCES.remove(handler.player); COMMITS.remove(handler.player); STEPS.remove(handler.player);
            REJECTION_NOTICE.remove(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { ControlClock.clear(server); SESSIONS.clear(); STEPS.clear(); NONCES.clear(); COMMITS.clear(); REJECTION_NOTICE.clear(); TICKING.clear(); INVALIDATED_TICKS.clear(); });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : SESSIONS.keySet().toArray(ServerPlayer[]::new)) {
                Session session = SESSIONS.get(player);
                if (session != null && !sameIdentity(player, session)) invalidate(player, "实体、世界或存活代次失效");
            }
        });
    }

    private static boolean localAdmission(ServerPlayer player){
        return M0DProtocol.localTransport(!player.level().getServer().isDedicatedServer(),player.connection.getRemoteAddress());
    }

    private static void receive(ServerPlayer player, M0DProtocol.ClientMessage message) {
        if (!enabled() || !M0DTrace.allowed(player)) return;
        Session session = SESSIONS.get(player);
        // 网络线程已有每连接硬预算；主线程CONTROL日志仅每秒采样。
        long wall=System.nanoTime();
        if(message.kind()!=M0DProtocol.CONTROL || session!=null&&wall-session.lastWireTrace>=1_000_000_000L){
            if(session!=null)session.lastWireTrace=wall;
            M0DTrace.event(player,"自定义消息服务端处理",Map.of("类型",message.kind(),"控制序号",message.seq(),"代次",message.generation()));
        }
        if (message.protocol() != M0DProtocol.VERSION) { if (session != null) stop(session, "协议不匹配"); return; }
        if (message.kind() == M0DProtocol.REQUEST || message.kind()==M0DProtocol.FREE_REQUEST) {
            boolean free=message.kind()==M0DProtocol.FREE_REQUEST;
            if(!M0DProtocol.technical()&&!free)return;
            boolean aim=free||message.arg()>M0DProtocol.M2_REQUEST;
            boolean dynamic=free||message.arg()>M0DProtocol.M1_REQUEST;
            long ticks=free?0:dynamic?message.arg()-(aim?M0DProtocol.M2_REQUEST:M0DProtocol.M1_REQUEST):message.arg();
            if (session != null || (!M0DProtocol.technical()&&!SESSIONS.isEmpty()) || message.seq() <= NONCES.getOrDefault(player, 0L) || message.seq() <= 0
                    || message.generation() != 0 || (free ? message.arg()!=0 || !M0DProtocol.freeEnabled() || !localAdmission(player) : ticks<1 || ticks>(dynamic?600:40)) || dynamic&&!M0DProtocol.m1Enabled() || aim&&!M0DProtocol.m2Enabled()
                    || !eligible(player) || !speedAllowed(player,dynamic) || access(player).efc$m0dAwaiting()
                    || !ServerPlayNetworking.canSend(player, M0DProtocol.ServerMessage.TYPE)) {
                reject(player, eligibilityReason(player)); return;
            }
            NONCES.put(player, message.seq()); COMMITS.put(player, 0L);
            session = new Session(player, ++generation, (int) ticks, message.seq(), dynamic,aim,free);
            SESSIONS.put(player, session);
            send(session, M0DProtocol.OFFER, session.window, "提议尚未激活"); return;
        }
        if (session == null || message.generation() != session.generation || !sameIdentity(player, session)) {
            if(session!=null&&wall-session.lastRejectTrace>=1_000_000_000L){session.lastRejectTrace=wall;M0DTrace.event(player,"拒绝旧代次自定义消息");}return;
        }
        if (message.kind() == M0DProtocol.READY && session.state.equals("OFFERED")) {
            session.ready = true; session.lastControlClock = session.clock;
            return;
        }
        if (message.kind() == M0DProtocol.STOP_ACK && session.state.equals("RETIRING")) {
            if (message.arg() == session.bridgeId && session.bridgeSent) {
                session.stopAck = true; checkRelease(session); M0DTrace.event(player, "StopAck已登记", info(session));
            }
            return;
        }
        if (!session.state.equals("ACTIVE")) return;
        if (message.kind() == M0DProtocol.CLOSE) { stop(session, "客户端主动关闭"); return; }
        if (message.kind() != M0DProtocol.CONTROL) return;
        if (message.ack()<0 || message.ack()>session.s) {stop(session,"确认服务步越界");return;}
        try {
            if(session.aimMode&&message.target()==null)throw new IllegalArgumentException("鼠标目标缺失");
            var input=new Instructor.Input(message.target()==null?new FlightModel.V(0,0,1):modelV(message.target()),message.arg());
            if(!session.aimMode&&message.target()!=null)throw new IllegalArgumentException("配置不接受鼠标目标");
            var accepted=session.inbox.offer(message.seq(),input,message.frame(),wall,activity(player));
            if(accepted==ControlInput.Acceptance.FLOOD)stop(session,"持续有效输入洪泛");
            if(accepted==ControlInput.Acceptance.ACCEPTED)session.lastControlClock=session.clock;
        } catch(IllegalArgumentException|NullPointerException ex){stop(session,"非法控制事务："+ex.getMessage());}

    }

    /** 在原resetPosition之后、原doTick之前锁定本次所有权。 */
    public static void beginTick(ServerPlayer player) {
        STEPS.remove(player);
        TICKING.remove(player); INVALIDATED_TICKS.remove(player);
        M0DTrace.event(player, "服务端玩家运动刻开始", Map.of("状态", state(player)));
        Session session = SESSIONS.get(player);
        if (session == null) return;
        TICKING.add(player);
        session.clock++; session.packets = 0;
        if (!sameIdentity(player, session)) { invalidate(player, "运动刻身份失效"); return; }
        if (session.state.equals("RETIRING")) {
            long elapsed = session.clock - session.retireClock;
            if (elapsed > 5) { fail(player, "退出围栏超过5刻"); return; }
            if (session.releasePending && session.releaseClock < session.clock) {
                // 同一连接按序发送明确归还屏障；客户端此前仍保护该代次，不再次写入位置或速度。
                send(session, M0DProtocol.RELEASE, session.bridgeId, "双确认后安全归还");
                player.connection.resetPosition(); SESSIONS.remove(player);
                M0DTrace.event(player, "下一安全刻释放原版", info(session)); return;
            }
            if (elapsed >= 5) { fail(player, "五刻截止前未满足安全释放条件"); return; }
            M0DTrace.event(player, "VANILLA_TEMP_BRIDGE", info(session)); return;
        }
        if (session.state.equals("OFFERED")) {
            if (!session.ready) { if (session.clock > 5) { SESSIONS.remove(player); M0DTrace.event(player, "未激活提议超时"); } return; }
            if (!eligible(player) || !speedAllowed(player,session.dynamic) || access(player).efc$m0dAwaiting()) { SESSIONS.remove(player); M0DTrace.event(player, "激活入口安全拒绝"); return; }
            session.state = "ACTIVE"; session.lastControlClock = session.clock; session.inbox.touch(activity(player));
            if(session.dynamic)session.flight=FlightModel.initial(modelV(player.getDeltaMovement()),player.getYRot(),player.getXRot());
            if(session.aimMode)session.aimInput=new Instructor.Input(session.flight.attitude().rotate(new FlightModel.V(0,0,1)),1);
            session.recent.add(player.position());
            send(session, M0DProtocol.ACTIVATE, 0, "下一安全刻激活");
        }
        if (session.stopReason != null || session.term.expired(session.s) || session.inbox.expired(activity(player))
                || !eligible(player) || !speedAllowed(player,session.dynamic) || access(player).efc$m0dAwaiting() || !loadedSweep(player,session.dynamic)) {
            stop(session, session.stopReason != null ? session.stopReason : "窗口、输入或资格到期");
            M0DTrace.event(player, "VANILLA_TEMP_BRIDGE", info(session));
            return;
        }
        Step step = new Step(session, player.position(), ++session.s, player.fallDistance);
        STEPS.put(player, step);
        M0DTrace.event(player, "ACTIVE所有权锁定", stepInfo(step));
    }

    public static Vec3 replacement(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return null;
        Step step = STEPS.get(player);
        if (step == null || step.superseded || !current(player, step)) return null;
        if (++step.formulas != 1) { fail(player, "同一有效刻速度公式重复"); return null; }
        var applied=step.session.inbox.drain(activity(player));
        step.session.lastControlSeq=applied.inputSeq();step.session.eventAck=applied.eventAck();step.session.holdInput=applied.hold();
        step.session.aimInput=applied.input();step.session.intent=FlightModel.Intent.decode(applied.input().bits());
        if(step.session.dynamic){
            step.session.flight=ControlInput.prepare(step.session.flight,applied.events(),0,applied.input().bits(),applied.hold());
            if(step.session.aimMode){
                var r=Instructor.step(step.session.flight,step.session.aimInput,step.session.controller);
                step.session.controller=r.state();step.session.result=r;step.session.intent=r.intent();
            }
            step.session.flight=FlightModel.step(step.session.flight,step.session.intent,applied.hold());
            step.expected=mcV(step.session.flight.velocity());
            M0DTrace.event(player,"M1动力公式",stepInfo(step));
        } else M0DTrace.event(player, "固定服务端低速公式", stepInfo(step));
        return step.expected;
    }

    /** 原版/Fabric已运行合法性及耐久更新；坏翼残留flag不能多提交一个EFC运动步。 */
    public static void afterGlideUpdate(ServerPlayer player) {
        Step step = STEPS.get(player);
        if (step == null || step.superseded || !current(player, step) || eligible(player)) return;
        if (step.formulas != 0 || step.self != 0) { fail(player, "滑翔复核晚于运动提交"); return; }
        // 只是撤回尚未计算的预约；不改游戏滑翔位、位置、速度或装备。
        STEPS.remove(player); step.session.s--;
        stop(step.session, "游戏滑翔更新后资格失效：" + eligibilityReason(player));
        M0DTrace.event(player, "滑翔更新后撤回未执行步", info(step.session));
    }

    /** 三个窄入口在实际写速前询问所有权；被屏蔽输入不改变运动步或主动退出。 */
    public static boolean suppressImpulse(ServerPlayer player, Vec3 requested, String source) {
        Session session = SESSIONS.get(player);
        if (!enabled() || !M0DTrace.allowed(player) || session == null || !sameIdentity(player, session)
                || !(session.state.equals("ACTIVE") || session.state.equals("RETIRING"))) return false;
        ++session.impulses;
        Step step = STEPS.get(player);
        Map<String, Object> event = step == null ? info(session) : stepInfo(step);
        event.put("来源", source);
        event.put("原速度", M0DTrace.vector(player.getDeltaMovement()));
        event.put("被屏蔽请求速度", M0DTrace.vector(requested));
        M0DTrace.event(player, "服务端原版外力写入已屏蔽", event);
        return true;
    }

    public static void beforeMove(ServerPlayer player, MoverType type, Vec3 request) {
        Step step = STEPS.get(player);
        M0DTrace.event(player, "真实运动开始", Map.of("运动类型", type.toString(), "请求位移", M0DTrace.vector(request), "状态", state(player)));
        if (step == null || step.superseded) return;
        if (type == MoverType.SELF) { step.self++; step.moveStart = player.position(); step.request = request; }
        else { step.otherMoves++; fail(player, "有效刻出现额外运动类型"); }
    }
    public static void afterMove(ServerPlayer player, MoverType type) {
        Step step = STEPS.get(player);
        if (step != null && !step.superseded && type == MoverType.SELF) {
            step.p1 = player.position(); step.velocity = player.getDeltaMovement();
            M0DTrace.event(player, "真实SELF碰撞后", stepInfo(step));
        }
        M0DTrace.event(player, "真实运动结束", Map.of("运动类型", type.toString(), "状态", state(player)));
    }

    /** 原恢复调用的窄分流：true仅跳过该次坐标恢复，不再执行任何move。 */
    public static boolean preserveAtRestore(ServerPlayer player) {
        Step step = STEPS.get(player);
        M0DTrace.event(player, "原版恢复点到达", Map.of("状态", state(player)));
        if (INVALIDATED_TICKS.contains(player)) {
            M0DTrace.event(player, "本刻新动作优先，旧恢复禁止"); return true;
        }
        if (step == null) return false;
        if (step.superseded || !current(player, step)) {
            M0DTrace.event(player, "新动作优先，旧恢复禁止", stepInfo(step)); return true;
        }
        if (step.self != 1 || step.formulas != 1 || step.otherMoves != 0 || step.p1 == null
                || step.p1.distanceToSqr(player.position()) > 1.0E-14
                || step.p0.distanceToSqr(step.moveStart) > 1.0E-14
                || step.request.distanceToSqr(step.expected) > 1.0E-14 || !step.session.dynamic && step.request.y == 0) {
            fail(player, "恢复点唯一SELF或端点不变量失败"); return true;
        }
        if (step.accounting || step.accounted) { fail(player, "运动账发生重入"); return true; }
        step.accounting = true;
        Vec3 delta = step.p1.subtract(step.p0);
        // 先登记尝试，可能造成伤害/传送的调用之后重新核验身份与端点。
        step.fallCalls++;
        player.doCheckFallDamage(delta.x, delta.y, delta.z, player.onGround());
        if (!accountValid(player, step, "摔落记账")) return true;
        step.knownCalls++; access(player).efc$m0dKnown(delta);
        if (!accountValid(player, step, "已知运动更新")) return true;
        if (player.onGround() || player.hasLandedInLiquid() || player.onClimbable() || player.isSpectator() || player.isFallFlying() || player.isAutoSpinAttack()) {
            player.tryResetCurrentImpulseContext();
        }
        step.statisticsCalls++; player.checkMovementStatistics(delta.x, delta.y, delta.z);
        if (!accountValid(player, step, "统计记账")) return true;
        step.chunkCalls++; player.level().getChunkSource().move(player);
        if (!accountValid(player, step, "区块更新")) return true;
        access(player).efc$m0dLastGood(step.p1);
        // 原版浮空表达式在合法滑翔时为false，限定以本刻真实资格闭合。
        if (player.isFallFlying()) access(player).efc$m0dGlidingFloating();
        step.accounted = true; step.accounting = false;
        Session session = step.session;
        if(session.dynamic)session.flight=session.flight.collision(modelV(player.getDeltaMovement()));
        session.lastCommitClock = session.clock;
        session.recent.addLast(step.p1); while (session.recent.size() > 4) session.recent.removeFirst();
        COMMITS.put(player, commits(player) + 1);
        M0DTrace.event(player, "权威提交完成", stepInfo(step));
        if (session.state.equals("ACTIVE")) send(session, M0DProtocol.SNAPSHOT, 0, "真实SELF结果");
        if (!eligible(player) || session.term.expired(session.s)) stop(session, "步后资格或窗口结束");
        return true;
    }

    public static void endTick(ServerPlayer player) {
        Step step = STEPS.get(player);
        if (step != null) M0DTrace.event(player, "服务端刻最终端点", stepInfo(step));
        Session session = SESSIONS.get(player);
        if (session != null && session.state.equals("RETIRING") && !session.bridgeSent) bridge(session);
        STEPS.remove(player);
        TICKING.remove(player); INVALIDATED_TICKS.remove(player);
        M0DTrace.event(player, "服务端玩家运动刻结束", Map.of("状态", state(player)));
    }

    /** clamp前读取未修改的原始字段；不替换getter。 */
    public static boolean prePacket(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        Session session = SESSIONS.get(player);
        M0DTrace.event(player, "原版移动包原始检查", Map.of("子型", packet.getClass().getSimpleName(), "含位置", packet.hasPosition(), "原始目标", rawVector(packet.getX(0), packet.getY(0), packet.getZ(0)), "状态", state(player)));
        if (session == null || session.state.equals("OFFERED")) return false;
        double x = packet.getX(0), y = packet.getY(0), z = packet.getZ(0);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(packet.getXRot(0)) || !Float.isFinite(packet.getYRot(0))
                || Math.abs(x) > 3.0E7 || Math.abs(z) > 3.0E7 || Math.abs(y) > 2.0E7) {
            fail(player, "原始移动字段非有限或越界"); return true;
        }
        session.packets++;
        long moveNow=System.nanoTime();
        if(moveNow-session.moveDropWindow>=1_000_000_000L){session.moveDropWindow=moveNow;session.moveDrops=0;}
        if(!session.moveRate.take(moveNow)){if(++session.moveDrops>64)stop(session,"持续移动包洪泛");return true;}
        if (session.state.equals("ACTIVE") && (!sameIdentity(player, session) || player.isPassenger() || player.isSleeping() || player.wonGame)) {
            stop(session, "包侧出现不兼容运动状态"); return true;
        }
        if (session.state.equals("ACTIVE") && packet.hasPosition()) {
            Vec3 target = new Vec3(x, y, z);
            double error = session.recent.stream().mapToDouble(p -> p.distanceToSqr(target)).min().orElse(Double.POSITIVE_INFINITY);
            M0DTrace.event(player, "原始包三轴误差", Map.of("代次", session.generation, "提交序号", session.s, "误差平方", error, "包数", session.packets));
            // 包只校验不写位置；保留既有9格包络与原版过快检查，输入历史不授予更大位置权限。
            double envelope=session.dynamic?9.0:0.5;
            if (error > envelope*envelope) { stop(session, "原始坐标超过配置的有界预测包络"); return true; }
        }
        return false;
    }

    /** 原始检查及过快检查之后、getBoundingBox与jump/PLAYER之前。 */
    public static boolean isolatePacket(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        Session session = SESSIONS.get(player);
        if (session == null || session.state.equals("OFFERED")) return false;
        M0DTrace.event(player, "原版包运动已隔离", Map.of("代次", session.generation, "提交序号", session.s, "子型", packet.getClass().getSimpleName(), "包数", session.packets, "状态", session.state));
        return true;
    }

    public static boolean preserveKnown(ServerPlayer player) {
        Session session = SESSIONS.get(player);
        return session != null && commits(player) > 0 && (session.state.equals("ACTIVE") || session.lastCommitClock == session.clock);
    }
    public static void externalTeleport(ServerPlayer player) {
        Session session = SESSIONS.get(player);
        if (session != null && session.internalBridge) { M0DTrace.event(player, "一次退出桥原版传送入口", info(session)); return; }
        invalidate(player, "正常传送或纠正提升代次");
    }
    public static void teleportAck(ServerPlayer player, int id, boolean awaiting) {
        Session session = SESSIONS.get(player);
        M0DTrace.event(player, "原版传送ACK处理完成", Map.of("桥编号", id, "仍等待原版确认", awaiting));
        if (session != null && session.state.equals("RETIRING") && session.bridgeSent && session.bridgeId == id && !awaiting) {
            session.vanillaAck = true; checkRelease(session);
        }
    }
    public static void invalidate(ServerPlayer player, String reason) {
        if (TICKING.contains(player)) INVALIDATED_TICKS.add(player);
        Step step = STEPS.get(player); if (step != null) step.superseded = true;
        Session session = SESSIONS.remove(player);
        if (session != null) M0DTrace.event(player, "代次失效", merge(info(session), "原因", reason));
    }
    private static void checkRelease(Session session) {
        if (session.vanillaAck && session.stopAck && !session.releasePending) {
            session.releasePending = true; session.releaseClock = session.clock;
            M0DTrace.event(session.player, "双确认仅标记待释放", info(session));
        }
    }
    private static void stop(Session session, String reason) {
        if (!session.state.equals("ACTIVE")) return;
        session.stopReason = reason; session.state = "RETIRING"; session.retireClock = session.clock;
        M0DTrace.event(session.player, "进入有界退出围栏", merge(info(session), "原因", reason));
    }
    private static void bridge(Session session) {
        ServerPlayer player = session.player;
        if (!sameIdentity(player, session)) { invalidate(player, "桥前身份失效"); return; }
        Step step = STEPS.get(player);
        if (step != null && !step.accounted && !step.superseded) { fail(player, "未闭合真实步禁止退出桥"); return; }
        session.bridgeId = access(player).efc$m0dNextBridgeId();
        session.bridgeSent = true;
        send(session, M0DProtocol.STOP, session.bridgeId, session.stopReason);
        Vec3 physical = player.getDeltaMovement();
        M0DTrace.event(player, "退出桥明确物理速度", merge(info(session), "交接速度", M0DTrace.vector(physical)));
        session.internalBridge = true;
        try { player.connection.teleport(new PositionMoveRotation(player.position(), physical, player.getYRot(), player.getXRot()), Set.of()); }
        finally { session.internalBridge = false; }
    }
    private static void fail(ServerPlayer player, String reason) {
        Session session = SESSIONS.get(player);
        M0DTrace.event(player, "不可继续的实验失败", session == null ? Map.of("原因", reason) : merge(info(session), "原因", reason));
        invalidate(player, reason);
        player.connection.disconnect(Component.literal("飞控安全停止：" + reason));
    }
    private static boolean current(ServerPlayer player, Step step) {
        return !step.superseded && SESSIONS.get(player) == step.session && sameIdentity(player, step.session);
    }
    private static boolean currentAtEndpoint(ServerPlayer player, Step step) {
        return current(player, step) && player.position().distanceToSqr(step.p1) < 1.0E-14;
    }
    private static boolean accountValid(ServerPlayer player, Step step, String stage) {
        if (currentAtEndpoint(player, step)) return true;
        if (step.superseded || !sameIdentity(player, step.session) || SESSIONS.get(player) != step.session) {
            invalidate(player, stage + "被新动作取代，停止旧账");
            M0DTrace.event(player, "账中止且不重试", stepInfo(step));
        } else {
            fail(player, stage + "出现未登记的新位置，停止实验");
        }
        return false;
    }
    private static boolean sameIdentity(ServerPlayer player, Session session) {
        return player.connection == session.connection && player.level() == session.level && player.isAlive() && !player.isRemoved() && !player.isChangingDimension();
    }
    private static long activity(ServerPlayer p){return ControlClock.now(p.level().getServer(),System.nanoTime());}
    private static boolean speedAllowed(ServerPlayer player,boolean dynamic) { return player.getDeltaMovement().lengthSqr() <= (dynamic?1:0.15*0.15) + 1.0E-10; }
    private static FlightModel.V modelV(Vec3 v){return new FlightModel.V(v.x,v.y,v.z);}
    private static Vec3 mcV(FlightModel.V v){return new Vec3(v.x(),v.y(),v.z());}
    private static boolean eligible(ServerPlayer player) {
        return eligibilityReason(player) == null;
    }
    private static String eligibilityReason(ServerPlayer player) {
        if (!enabled() || !M0DTrace.allowed(player)) return "飞控权限或能力未启用";
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) return "生命或实体状态失效";
        if (!player.isFallFlying()) return "服务端尚未处于合法滑翔";
        // 原版canGlide包含Fabric ALLOW/CUSTOM非tick查询；这里只验证残留状态，EFC不扫描槽位。
        if (!((dev.elytra.control.mixin.m0d.M0DGlideAccess) player).efc$m4wCanGlide()) return "游戏滑翔状态已失效";
        boolean safe = !player.onGround() && !player.isPassenger() && !player.isSleeping()
            && !player.onClimbable() && !player.noPhysics && !player.getAbilities().flying && !player.isInWater() && !player.isInLava()
            && !player.hasEffect(MobEffects.LEVITATION) && !player.isInPostImpulseGraceTime() && !player.isAutoSpinAttack()
            && loadedSweep(player, M0DProtocol.m1Enabled())
            && ((M0DEntityAccess) player).efc$m0dPlainMovement()
            && player.level().getBlockStates(player.getBoundingBox().inflate(M0DProtocol.m1Enabled()?1.05:0.05).expandTowards(VELOCITY)).allMatch(state -> state.getBlock().getSpeedFactor() == 1.0F && state.getBlock().getBounceRestitution() == 0.0F)
            && player.level().getEntitiesOfClass(FireworkRocketEntity.class, player.getBoundingBox().inflate(8)).isEmpty();
        return safe ? null : "危险运动、环境或区块条件";
    }
    private static void reject(ServerPlayer player, String qualification) {
        String reason = qualification == null ? "协议、会话、连接或运动条件不满足" : qualification;
        M0DTrace.event(player, "请求安全拒绝", Map.of("原因", reason));
        long now = player.level().getGameTime();
        if (now - REJECTION_NOTICE.getOrDefault(player, Long.MIN_VALUE / 2) >= 20) {
            REJECTION_NOTICE.put(player, now);
            player.sendOverlayMessage(Component.literal("飞控申请拒绝：" + reason));
        }
    }
    private static boolean loadedSweep(ServerPlayer player,boolean dynamic) {
        var box = dynamic?player.getBoundingBox().inflate(1.01):player.getBoundingBox().expandTowards(VELOCITY).inflate(0.01);
        return player.level().hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ));
    }
    private static M0DConnectionAccess access(ServerPlayer player) { return (M0DConnectionAccess) player.connection; }
    private static void send(Session session, int kind, long bridge, String reason) {
        ServerPlayer player = session.player;
        if (ServerPlayNetworking.canSend(player, M0DProtocol.ServerMessage.TYPE)) {
            int flags = (session.term.free()?M0DProtocol.FREE_FLAG:0) | (player.onGround() ? 1 : 0) | (player.horizontalCollision ? 2 : 0) | (player.verticalCollision ? 4 : 0);
            long ack = kind == M0DProtocol.OFFER ? session.requestNonce : session.lastControlSeq;
            ServerPlayNetworking.send(player, new M0DProtocol.ServerMessage(M0DProtocol.VERSION, kind, session.generation, session.s, ack, bridge, player.position(), player.getDeltaMovement(), flags, reason, session.dynamic?session.flight:null,session.aimMode?new M0DProtocol.AimSnapshot(mcV(session.aimInput.target()),session.controller,session.aimInput.bits()):null,session.eventAck,session.inbox.received(),session.holdInput));
            M0DTrace.event(player, "服务端快照或状态发送", merge(info(session), "载荷类型", kind));
        }
    }
    private static Map<String, Object> info(Session session) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("代次", session.generation); map.put("提交序号", session.s); map.put("服务端时钟", session.clock);
        map.put("配置",session.term.free()?"MANUAL_FREE":session.aimMode?"M2_AIM_1":session.dynamic?"M1_DYNAMIC_1":"M0_FIXED_40"); map.put("无累计到期",session.term.free()); if(!session.term.free())map.put("剩余刻",session.window-session.s);
        if(session.flight!=null){map.put("动力状态",session.flight);map.put("输入",session.intent);}
        if(session.aimMode){map.put("目标输入",session.aimInput);map.put("教官状态",session.controller);if(session.result!=null)map.put("教官结果",session.result);}
        map.put("收到采样序号",session.inbox.received());map.put("事件确认",session.eventAck);map.put("输入暂停",session.holdInput);map.put("事件待消费",session.inbox.pending());
        map.put("状态", session.state); map.put("控制序号", session.lastControlSeq); map.put("桥编号", session.bridgeId);
        map.put("原版确认", session.vanillaAck); map.put("停止确认", session.stopAck); map.put("屏蔽外力数", session.impulses);
        map.put("停止时钟", session.retireClock); map.put("退出已用刻", session.state.equals("RETIRING") ? session.clock - session.retireClock : 0); return map;
    }
    private static Map<String, Object> stepInfo(Step step) {
        Map<String, Object> map = info(step.session);
        map.put("P0", M0DTrace.vector(step.p0)); if (step.p1 != null) { map.put("P1", M0DTrace.vector(step.p1)); map.put("实际位移", M0DTrace.vector(step.p1.subtract(step.p0))); }
        map.put("运动序号", step.s); map.put("请求速度", M0DTrace.vector(step.expected)); map.put("公式次数", step.formulas);
        map.put("SELF次数", step.self); map.put("其他运动次数", step.otherMoves); map.put("已记账", step.accounted);
        map.put("摔落调用", step.fallCalls); map.put("已知运动调用", step.knownCalls); map.put("统计调用", step.statisticsCalls); map.put("区块调用", step.chunkCalls);
        map.put("已取代", step.superseded);
        map.put("已知运动", M0DTrace.vector(step.session.player.getKnownMovement())); map.put("摔落前", step.fallBefore);
        return map;
    }
    private static Map<String, Object> merge(Map<String, Object> map, String key, Object value) { map.put(key, value); return map; }
    private static Object[] rawVector(double x, double y, double z) {
        return new Object[] {Double.isFinite(x) ? x : Double.toString(x), Double.isFinite(y) ? y : Double.toString(y), Double.isFinite(z) ? z : Double.toString(z)};
    }
    private static final class Session {
        final ServerPlayer player; final ServerGamePacketListenerImpl connection; final Level level; final long generation; final int window; final FlightTerm term; final long requestNonce;
        final ArrayDeque<Vec3> recent = new ArrayDeque<>();
        final boolean aimMode; Instructor.Input aimInput; Instructor.State controller=Instructor.State.INITIAL;Instructor.Result result;
        final ControlInput.Bucket moveRate=new ControlInput.Bucket(12,30,System.nanoTime());long moveDrops,moveDropWindow=System.nanoTime();
        final ControlInput.Inbox inbox;long eventAck,lastWireTrace,lastRejectTrace;boolean holdInput;
        final boolean dynamic; FlightModel.State flight; FlightModel.Intent intent=FlightModel.Intent.NEUTRAL;
        String state = "OFFERED"; String stopReason; boolean ready; boolean bridgeSent; boolean internalBridge; boolean vanillaAck; boolean stopAck; boolean releasePending;
        long clock; long s; long lastControlSeq; long lastControlClock; long lastCommitClock = -1; long retireClock; long releaseClock; int bridgeId; int packets; int impulses;
        Session(ServerPlayer player, long generation, int window, long requestNonce, boolean dynamic,boolean aim,boolean free) { this.term=new FlightTerm(free,window); this.aimMode=aim; this.aimInput=aim?new Instructor.Input(FlightModel.initial(modelV(player.getDeltaMovement()),player.getYRot(),player.getXRot()).attitude().rotate(new FlightModel.V(0,0,1)),1):null; this.dynamic=dynamic; this.flight=dynamic?FlightModel.initial(modelV(player.getDeltaMovement()),player.getYRot(),player.getXRot()):null; this.inbox=new ControlInput.Inbox(new Instructor.Input(FlightModel.initial(modelV(player.getDeltaMovement()),player.getYRot(),player.getXRot()).attitude().rotate(new FlightModel.V(0,0,1)),1),System.nanoTime(),activity(player)); this.player = player; connection = player.connection; level = player.level(); this.generation = generation; this.window = window; this.requestNonce = requestNonce; }
    }
    private static final class Step {
        final Session session; final Vec3 p0; final long s; final double fallBefore;
        Vec3 expected=VELOCITY; Vec3 moveStart; Vec3 request; Vec3 p1; Vec3 velocity; int self; int formulas; int otherMoves; int fallCalls; int knownCalls; int statisticsCalls; int chunkCalls;
        boolean superseded; boolean accounting; boolean accounted;
        Step(Session session, Vec3 p0, long s, double fallBefore) { this.session = session; this.p0 = p0; this.s = s; this.fallBefore = fallBefore; }
    }
}
