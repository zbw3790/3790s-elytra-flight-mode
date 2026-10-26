package dev.elytra.control.probe.m0d;

/** 会话累计期限；无期限是显式状态，控制新鲜度/握手/退出时限仍由原会话独立检查。 */
public record FlightTerm(boolean free,int ticks) {
 public FlightTerm {if(free?ticks!=0:ticks<1||ticks>600)throw new IllegalArgumentException("许可期限非法");}
 public boolean expired(long commits){return !free&&commits>=ticks;}
 public String label(long commits){return free?"自由试飞":"剩余"+Math.max(0,ticks-commits)+"刻";}
}
