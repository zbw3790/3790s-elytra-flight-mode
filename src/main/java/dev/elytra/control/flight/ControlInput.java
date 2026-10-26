package dev.elytra.control.flight;

import java.util.ArrayDeque;
import java.util.List;
import dev.elytra.control.flight.FlightModel.*;

/** 有界输入事务；收包不是物理步。时间来自服务端，绝不相信客户端时间戳。 */
public final class ControlInput {
    public static final int MAX_EVENTS=32, QUEUED_EVENTS=64, MAX_GAP=256, MAX_PREDICTIONS=32;
    public static final long NEUTRAL_NANOS=250_000_000L, TIMEOUT_NANOS=2_000_000_000L;
    public static final long MAX_SEQUENCE=Long.MAX_VALUE-1024;
    public record KeyEvent(long id,int levels,boolean syncRelease) {
        public KeyEvent {if(id<=0||id>MAX_SEQUENCE||levels<0||levels>3||syncRelease&&levels!=0)throw new IllegalArgumentException("非法油门事件");}
    }
    public record Frame(boolean suspended,List<KeyEvent> events) {
        public Frame {events=List.copyOf(events);if(events.size()>MAX_EVENTS)throw new IllegalArgumentException("事件载荷超限");long last=0;for(var e:events){if(e.id<=last)throw new IllegalArgumentException("事件列表非严格升序");last=e.id;}}
        public static final Frame EMPTY=new Frame(false,List.of());
    }
    public record Applied(long inputSeq,long eventAck,Instructor.Input input,boolean hold,List<KeyEvent> events,boolean stale) { }
    public enum Acceptance { ACCEPTED, DUPLICATE, RATE_DROP, FLOOD }
    public static boolean predictionConsumed(long inputSeq,long predictedStep,long inputAck,long serverStep){return inputSeq<=inputAck||predictedStep<=serverStep;}
    public static final class Bucket {
        private final double capacity,perSecond;private double tokens;private long at;
        public Bucket(double capacity,double perSecond,long now){this.capacity=capacity;this.perSecond=perSecond;tokens=capacity;at=now;}
        public boolean take(long now){long dt=Math.max(0,now-at);at=Math.max(at,now);tokens=Math.min(capacity,tokens+dt/1e9*perSecond);if(tokens<1)return false;tokens--;return true;}
    }
    /** 每连接底层预算：在Fabric排入主线程之前执行，安全消息用独立预算。 */
    public static final class Ingress {
        private final Bucket controls,safety;private long window,drops,accepted,rejected;private boolean closed;
        public Ingress(long now){controls=new Bucket(64,120,now);safety=new Bucket(16,16,now);window=now;}
        /** 0允许，1丢弃，2仅一次安排关闭。 */
        public synchronized int receive(boolean control,long now){if(closed){rejected++;return 1;}if((control?controls:safety).take(now)){accepted++;return 0;}rejected++;if(now-window>=1_000_000_000L){window=now;drops=0;}if(++drops>64){closed=true;return 2;}return 1;}
        public synchronized List<Long> metrics(){return List.of(accepted,rejected,closed?1L:0L);}
    }
    public static final class Inbox {
        private final Bucket rate;private final ArrayDeque<KeyEvent> pending=new ArrayDeque<>();
        private Instructor.Input latest;private boolean suspended=true;private long received,applied,eventReceived,eventAck,lastSeen,window,drops;private int levels;
        public Inbox(Instructor.Input initial,long wall,long activity){latest=initial;lastSeen=activity;rate=new Bucket(12,30,wall);window=wall;}
        public void touch(long activity){lastSeen=activity;}
        public long silence(long activity){return Math.max(0,activity-lastSeen);}
        public boolean expired(long activity){return silence(activity)>TIMEOUT_NANOS;}
        public long received(){return received;} public long applied(){return applied;} public long eventAck(){return eventAck;}public int pending(){return pending.size();}
        public Acceptance offer(long seq,Instructor.Input input,Frame frame,long wall,long activity){
            if(seq<=0||seq>MAX_SEQUENCE)throw new IllegalArgumentException("控制序号越界");
            if(seq<=received)return Acceptance.DUPLICATE;
            if(seq-received>MAX_GAP)throw new IllegalArgumentException("控制前跳超限");
            if(frame.suspended&&input.bits()!=1)throw new IllegalArgumentException("暂停输入必须中立");
            long next=eventReceived;int state=levels,newCount=0;
            for(var e:frame.events){if(e.id<=next)continue;if(e.id!=next+1)throw new IllegalArgumentException("油门事件缺口");next=e.id;state=e.levels;newCount++;}
            int actual=(input.bits()&128)!=0?1:0;actual|=(input.bits()&256)!=0?2:0;
            if(!frame.suspended&&state!=actual)throw new IllegalArgumentException("油门电平缺少有序事件");
            if(pending.size()+newCount>QUEUED_EVENTS)throw new IllegalArgumentException("待消费事件超限");
            if(!rate.take(wall)){if(wall-window>=1_000_000_000L){window=wall;drops=0;}return ++drops>64?Acceptance.FLOOD:Acceptance.RATE_DROP;}
            // 全部验证后才提交，过载丢包的事件可由后续未确认账本重发。
            for(var e:frame.events)if(e.id>eventReceived){pending.add(e);eventReceived=e.id;}
            levels=state;received=seq;latest=input;suspended=frame.suspended;lastSeen=activity;return Acceptance.ACCEPTED;
        }
        public Applied drain(long activity){
            boolean stale=silence(activity)>NEUTRAL_NANOS,hold=suspended||stale;
            var events=stale?List.<KeyEvent>of():List.copyOf(pending);pending.clear();eventAck=eventReceived;applied=received;
            return new Applied(applied,eventAck,hold?new Instructor.Input(latest.target(),1):latest,hold,events,stale);
        }
    }
    /** 只处理离散边沿，不推进油门速率、角层、平移或SELF。 */
    public static State edges(State s,List<KeyEvent> events,long after){
        for(var e:events){if(e.id<=after)continue;boolean more=(e.levels&1)!=0,less=(e.levels&2)!=0;
            boolean blocked=s.throttleMemory().lessBlocked()&&less,wep=s.wep();double throttle=s.throttle();
            if(e.syncRelease){blocked=false;}else if(wep){if(less&&!s.throttleMemory().less()){wep=false;throttle=1;blocked=true;}}
            else if(more&&!s.throttleMemory().more()&&!less&&!blocked&&throttle>=1){wep=true;throttle=1;}
            s=new State(s.velocity(),s.attitude(),throttle,wep,s.limited(),s.omega(),new ThrottleMemory(more,less,blocked));
        }return s;
    }
    public static State prepare(State s,List<KeyEvent> events,long after,long bits,boolean hold){
        s=edges(s,events,after);
        if(!hold){boolean more=(bits&128)!=0,less=(bits&256)!=0;
            // 离散作用只来自有序事件；电平对齐不能额外制造一个按下边沿。
            s=new State(s.velocity(),s.attitude(),s.throttle(),s.wep(),s.limited(),s.omega(),new ThrottleMemory(more,less,s.throttleMemory().lessBlocked()&&less));
        }return s;
    }
    private ControlInput() { }
}
