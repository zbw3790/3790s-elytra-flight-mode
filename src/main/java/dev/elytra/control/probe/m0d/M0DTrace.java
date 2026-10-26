package dev.elytra.control.probe.m0d;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 仅保留本轮指定合成玩家的有界、去身份时序。 */
public final class M0DTrace {
    private static final Gson GSON = new Gson();
    private static BufferedWriter writer;
    private static long sequence;
    private static volatile String phase = "准备";
    private static final Map<Path, FreeLog> freeLogs = new LinkedHashMap<>();
    private static long freeDropped;
    private static boolean freeIoFailed;
    private static boolean freeSummaryHook;
    private static long freeCriticalDropped;
    private M0DTrace() { }

    public static boolean enabled() {
        return Boolean.getBoolean("efc.m0d.observe") || Boolean.getBoolean("efc.m0d.experiment");
    }

    public static boolean validPlayerProperty() {
        try { UUID.fromString(System.getProperty("efc.m0d.player", "")); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }

    public static boolean allowed(Entity entity) {
        if(!M0DProtocol.technical())return entity instanceof net.minecraft.world.entity.player.Player;
        return enabled() && entity != null && validPlayerProperty()
            && entity.getUUID().toString().equals(System.getProperty("efc.m0d.player", ""));
    }

    public static void phase(String value) { if (enabled()) phase = value; }
    public static double[] vector(Vec3 value) { return new double[] {value.x, value.y, value.z}; }
    public static void note(Entity entity, String event, Map<String, ?> details) { event(entity, event, details); }
    public static void event(Entity entity, String event) { event(entity, event, Map.of()); }
    public static void event(Entity entity, String event, Map<String, ?> details) {
        if (!enabled() || !allowed(entity)) return;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("端", entity.level().isClientSide() ? "客户端" : "服务端");
        row.put("游戏刻", entity.level().getGameTime());
        row.put("实体刻", entity.tickCount);
        row.put("位置", vector(entity.position()));
        row.put("速度", vector(entity.getDeltaMovement()));
        row.put("地面", entity.onGround());
        row.put("水平碰撞", entity.horizontalCollision);
        row.put("垂直碰撞", entity.verticalCollision);
        row.put("存活", entity.isAlive());
        row.put("入水", entity.isInWater());
        row.put("滑翔", entity instanceof LivingEntity living && living.isFallFlying());
        row.put("摔落累计", entity.fallDistance);
        row.putAll(details);
        append(event, row);
    }
    public static void event(String event, Map<String, ?> details) {
        if (enabled() && validPlayerProperty()) append(event, new LinkedHashMap<>(details));
    }
    private static synchronized void append(String event, Map<String, Object> row) {
        if (Boolean.getBoolean("efc.m4f.free")) {
            row.put("事件",event);row.put("阶段",phase);row.put("序号",++sequence);
            Path path=Path.of(System.getProperty("efc.m0d.traceFile",".cache/m4f/trace.jsonl"));
            if(!freeSummaryHook){freeSummaryHook=true;Runtime.getRuntime().addShutdownHook(new Thread(()->{try{Files.writeString(Path.of(path+".summary.json"),GSON.toJson(Map.of("累计事件",sequence,"详细丢弃",freeDropped,"关键事件满额丢弃",freeCriticalDropped,"记录边界","详细各2×1MiB；关键512KiB；记录限制不结束飞行")),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException ignored){}},"M4F记录收尾"));}
            // 新人工入口最多两段1MiB；不因详细日志上限或I/O故障终止飞行。
            boolean critical=java.util.Set.of("客户端发送请求","客户端发送就绪","客户端收到激活并重基准","进入有界退出围栏","下一安全刻释放原版","客户端收到明确归还屏障","代次失效","不可继续的实验失败","请求安全拒绝","客户端资格、焦点或窗口失效","紧急按键退出","客户端断线清理").contains(event);
            if(critical||!(row.get("游戏刻") instanceof Number tick)||tick.longValue()%20==0)freeAppend(path,row);else freeDropped++;
            if(java.util.Set.of("客户端发送请求","客户端发送就绪","客户端收到激活并重基准","进入有界退出围栏","下一安全刻释放原版","客户端收到明确归还屏障","代次失效","不可继续的实验失败","请求安全拒绝","客户端资格、焦点或窗口失效","紧急按键退出","客户端断线清理").contains(event))freeAppend(Path.of(path+".events.jsonl"),row);
            if(event.equals("不可继续的实验失败")){
                try{Files.writeString(Path.of(path+".failure.json"),GSON.toJson(row),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException ignored){}
            }
            return;
        }
        if (sequence >= 50000) throw new IllegalStateException("M0D证据超过上限，停止测试。");
        try {
            if (writer == null) {
                Path path = Path.of(System.getProperty("efc.m0d.traceFile", ".cache/m0d/m0d-trace.jsonl"));
                Files.createDirectories(path.toAbsolutePath().getParent());
                writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
            row.put("事件", event);
            row.put("阶段", phase);
            row.put("序号", ++sequence);
            writer.write(GSON.toJson(row)); writer.newLine(); writer.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("M0D证据无法写入，停止测试。", exception);
        }
    }
    /** 仅新自由入口的夹具观察共用同样的有界写入，旧入口日志语义不变。 */
    public static synchronized void freeObservation(Path path,Map<String,Object> row){String event=String.valueOf(row.get("事件"));if(event.contains("用户")||event.contains("焦点")||event.contains("边界")||event.contains("归还")||!(row.get("世界刻") instanceof Number tick)||tick.longValue()%20==0)freeAppend(path,row);else freeDropped++;}
    public static long freeDropped(){return freeDropped;}
    private static void freeAppend(Path path,Map<String,Object> row){
        if(freeIoFailed){freeDropped++;return;}
        try{
            FreeLog log=freeLogs.get(path);
            if(log==null){if(freeLogs.size()>=4){freeDropped++;return;}Files.createDirectories(path.toAbsolutePath().getParent());log=new FreeLog(path);freeLogs.put(path,log);}
            String line=GSON.toJson(row)+"\n";long bytes=line.getBytes(StandardCharsets.UTF_8).length;
            if(bytes>65536){freeDropped++;return;}
            if(path.toString().endsWith(".events.jsonl")&&log.bytes+bytes>524288){if(freeCriticalDropped++==0)System.err.println("M4F关键事件记录已满，已有记录保留，飞行继续；累计计数仍更新");return;}
            if(log.bytes+bytes>1048576){log.out.close();Files.move(path,Path.of(path+".1"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);log.out=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);log.bytes=0;}
            log.out.write(line);log.out.flush();log.bytes+=bytes;
        }catch(IOException exception){freeIoFailed=true;freeDropped++;System.err.println("M4F详细记录不可用，停止详细采样；飞行安全逻辑继续："+exception.getClass().getSimpleName());}
    }
    private static final class FreeLog {
        final Path path;BufferedWriter out;long bytes;
        FreeLog(Path path)throws IOException{this.path=path;out=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    }
}
