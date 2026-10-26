package dev.elytra.control.flight;

import java.util.List;
import java.util.Locale;

/** 只读UI格式/像素几何；不持有飞行或相机状态。坐标单位为GUI像素。 */
public final class FlightHudData {
    public static final String CONFIG="M4R-UI-1";
    public static final int MARGIN=6, LINE=10, RADIUS=6;
    private FlightHudData(){}
    public static boolean visible(boolean active,boolean player,boolean alive,boolean hidden,boolean gui){
        return active&&player&&alive&&!hidden&&!gui;
    }
    public static List<String> lines(double throttle,boolean wep,double speed,double y){
        double thr=Double.isFinite(throttle)?Math.clamp(throttle,0,1):0;
        double actual=Double.isFinite(speed)?Math.max(0,speed):0;
        double altitude=Double.isFinite(y)?Math.floor(y):0;
        return List.of("THR "+Math.round(thr*100)+"%"+(wep?" WEP":""),String.format(Locale.ROOT,"SPD %.1f",actual),String.format(Locale.ROOT,"ALT %.0f",altitude));
    }
    /** 中点圆算法的一圈唯一像素；外径13，不叠第二个圆形字体。 */
    public record Pixel(int x,int y){}
    public static List<Pixel> ring(){
        var result=new java.util.LinkedHashSet<Pixel>();int x=RADIUS,y=0,d=1-RADIUS;
        while(x>=y){
            for(var p:new Pixel[]{new Pixel(x,y),new Pixel(y,x),new Pixel(-y,x),new Pixel(-x,y),new Pixel(-x,-y),new Pixel(-y,-x),new Pixel(y,-x),new Pixel(x,-y)})result.add(p);
            y++;if(d<0)d+=2*y+1;else{x--;d+=2*(y-x)+1;}
        }
        return List.copyOf(result);
    }
}
