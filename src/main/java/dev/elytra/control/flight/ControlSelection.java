package dev.elytra.control.flight;

/** 连接内的用户选择，不授予运动权；请求/释放仍由原双端协议完成。 */
public final class ControlSelection {
    public enum Phase { VANILLA, REQUESTED, OFFERED, ACTIVE, RETIRING }
    public enum Action { REQUEST, CLOSE, WAIT, DENIED }
    /** 消费原版click队列而只认新按下；GUI内事件也应采样并丢弃，不延后触发。 */
    public static final class PressEdge {
        private boolean held;
        public boolean take(boolean clicked,boolean down){boolean pressed=clicked&&!held;held=down;return pressed;}
    }
    private boolean vanilla;
    public boolean vanillaSelected(){return vanilla;}
    public void resetConnection(){vanilla=false;}
    public void requestFailed(){vanilla=true;}
    public Action press(Phase phase,boolean eligible){
        // READY/ACTIVATE可能在途：提议也需等待，不在客户端清掉未知服务端权威。
        if(phase==Phase.RETIRING||phase==Phase.REQUESTED||phase==Phase.OFFERED)return Action.WAIT;
        if(phase==Phase.ACTIVE){vanilla=true;return Action.CLOSE;}
        if(!eligible){vanilla=true;return Action.DENIED;}
        vanilla=false;return Action.REQUEST;
    }
}
