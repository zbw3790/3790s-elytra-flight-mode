package dev.elytra.control.flight;

/** 只有实际IntegratedServer入口可冻结活动时钟；网络暂停位不能调用此处。 */
public final class ControlClock {
    private static final java.util.Map<Object,Pause> PAUSES=new java.util.IdentityHashMap<>();
    private static final class Pause {long total,since;boolean paused;}
    public static synchronized void pause(Object server,boolean paused,long now){var p=PAUSES.computeIfAbsent(server,k->new Pause());if(paused&&!p.paused)p.since=now;if(!paused&&p.paused)p.total+=Math.max(0,now-p.since);p.paused=paused;}
    public static synchronized long now(Object server,long wall){var p=PAUSES.get(server);return p==null?wall:wall-p.total-(p.paused?Math.max(0,wall-p.since):0);}
    public static synchronized void clear(Object server){PAUSES.remove(server);}
    private ControlClock() { }
}
