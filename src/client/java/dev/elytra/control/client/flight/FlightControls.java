package dev.elytra.control.client.flight;

import dev.elytra.control.client.probe.m0d.M0DClient;
import dev.elytra.control.flight.FlightModel;
import dev.elytra.control.probe.m0d.M0DProtocol;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** 可重绑输入；重新接管先等所有飞行键释放，不继承上一会话按键。 */
public final class FlightControls {
    private static final KeyMapping.Category CATEGORY=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("elytra_flight_control","flight"));
    public static final KeyMapping UP=key("pitch_up",GLFW.GLFW_KEY_S),DOWN=key("pitch_down",GLFW.GLFW_KEY_W),
        LEFT=key("roll_left",GLFW.GLFW_KEY_A),RIGHT=key("roll_right",GLFW.GLFW_KEY_D),
        YAW_LEFT=key("yaw_left",GLFW.GLFW_KEY_Q),YAW_RIGHT=key("yaw_right",GLFW.GLFW_KEY_E),
        MORE=key("throttle_up",GLFW.GLFW_KEY_LEFT_SHIFT),LESS=key("throttle_down",GLFW.GLFW_KEY_LEFT_CONTROL),
        EXIT=M0DProtocol.technical()?key("exit",GLFW.GLFW_KEY_R):new KeyMapping("key.elytra_flight_control.exit",GLFW.GLFW_KEY_UNKNOWN,CATEGORY),FREE=key("free_look",GLFW.GLFW_KEY_V),
        // 26.2原版G已绑定快速操作；初始未绑定，避免菜单抢先打开。原版界面可选择G并显示冲突。
        VANILLA=key("vanilla_control",GLFW.GLFW_KEY_UNKNOWN);
    private static final KeyMapping[] AXES={UP,DOWN,LEFT,RIGHT,YAW_LEFT,YAW_RIGHT,MORE,LESS};
    public static final class Arming {
        private boolean armed;
        public void reset(){armed=false;}
        public boolean armed(){return armed;}
        public void observe(boolean anyDown){if(!armed&&!anyDown)armed=true;}
    }
    public static final class MenuGate {
        private boolean waiting,releaseSample;private int debt;
        public void reset(){waiting=releaseSample=false;debt=0;}
        public void requireRelease(int mask){debt|=mask;}
        public void released(int mask){debt&=~mask;}
        public int releaseDebt(){return debt;}
        public void tick(boolean menu,boolean blocked){
            releaseSample=false;
            if(menu)waiting=true;
            else if(waiting&&!blocked&&debt==0){waiting=false;releaseSample=true;}
        }
        public boolean waiting(){return waiting;}
        public boolean releaseSample(){return releaseSample;}
    }
    private static final MenuGate MENU=new MenuGate();
    private static boolean bindNotice;private static int lastHeldMask;
    public static boolean safeMenu(){return Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen;}
    public static boolean inputSuspended(){var mc=Minecraft.getInstance();return mc.gui.screen()!=null||!mc.isWindowActive()||!mc.mouseHandler.isMouseGrabbed()||MENU.waiting()||MENU.releaseSample()||!ARMING.armed();}
    private static boolean physicalDown(KeyMapping k){
        var mc=Minecraft.getInstance();var key=InputConstants.getKey(k.saveString());
        if(key.getType()==InputConstants.Type.MOUSE)return GLFW.glfwGetMouseButton(mc.getWindow().handle(),key.getValue())==GLFW.GLFW_PRESS;
        if(key.getType()==InputConstants.Type.KEYSYM&&key.getValue()>=0)return InputConstants.isKeyDown(mc.getWindow(),key.getValue());
        if(key.getType()==InputConstants.Type.SCANCODE){for(int code=GLFW.GLFW_KEY_SPACE;code<=GLFW.GLFW_KEY_LAST;code++)if(GLFW.glfwGetKeyScancode(code)==key.getValue())return InputConstants.isKeyDown(mc.getWindow(),code);}
        return k.isDown();
    }
    private static boolean held(){for(var k:AXES)if(physicalDown(k))return true;return physicalDown(FREE)||physicalDown(VANILLA);}
    public static void connection(){bindNotice=false;}
    private static final Arming ARMING=new Arming();
    private static final dev.elytra.control.flight.ControlSelection.PressEdge TOGGLE_EDGE=new dev.elytra.control.flight.ControlSelection.PressEdge();
    private FlightControls(){}
    public static void initialize(){ClientTickEvents.START_CLIENT_TICK.register(mc->{
        boolean pausedInput=mc.gui.screen()!=null||!mc.isWindowActive()||!mc.mouseHandler.isMouseGrabbed();
        if(pausedInput&&!MENU.waiting())MENU.requireRelease(lastHeldMask);
        MENU.tick(pausedInput,pausedInput||held());
        if(pausedInput)ARMING.reset();
        if(!pausedInput&&!MENU.waiting()&&!held())ARMING.observe(false);
        if(MENU.releaseSample())ARMING.observe(false);
        if(!bindNotice&&mc.player!=null&&mc.player.isFallFlying()&&VANILLA.isUnbound()&&mc.gui.screen()==null){bindNotice=true;mc.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.literal("请在选项→控制→鞘翅飞控中绑定‘原版鞘翅控制切换’"),false);}
        boolean clicked=false;while(VANILLA.consumeClick())clicked=true;
        boolean pressed=TOGGLE_EDGE.take(clicked,VANILLA.isDown());
        // 长按/系统重复不能在RELEASE之后自动再申请；每次需要新的按下边沿。
        if(pressed&&!inputSuspended()&&mc.player!=null&&mc.gui.screen()==null&&mc.isWindowActive())M0DClient.toggleVanillaControl();
    });}
    private static KeyMapping key(String name,int code){return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.elytra_flight_control."+name,code,CATEGORY));}
    // 保留原生键电平直至真正KEYUP，不用setDown(false)伪造释放后过早武装。
    // armed=false期间read/rawBits仍中立，原版模式不使用这些Mod电平；焦点/GUI原生releaseAll仍有效。
    public static void reset(){MENU.reset();ARMING.reset();lastHeldMask=0;for(var k:AXES){while(k.consumeClick()){} }EXIT.setDown(false);while(EXIT.consumeClick()){};FREE.setDown(false);while(FREE.consumeClick()){};AimView.reset();clearVanilla();}
    public static long rawBits(){
        if(inputSuspended())return 1;
        lastHeldMask=0;var all=owned();for(int n=0;n<all.length;n++)if(all[n].isDown()||physicalDown(all[n]))lastHeldMask|=1<<n;
        return 1L|bit(UP,1)|bit(DOWN,2)|bit(LEFT,3)|bit(RIGHT,4)|bit(YAW_RIGHT,5)|bit(YAW_LEFT,6)|bit(MORE,7)|bit(LESS,8);
    }
    private static long bit(KeyMapping k,int n){return k.isDown()?1L<<n:0;}
    public static FlightModel.Intent read(){return inputSuspended()?FlightModel.Intent.decode(513):FlightModel.Intent.decode(rawBits());}
    private static KeyMapping[] owned(){return new KeyMapping[]{UP,DOWN,LEFT,RIGHT,YAW_LEFT,YAW_RIGHT,MORE,LESS,FREE,VANILLA};}
    /** 原生回调中的释放证明；失焦时的合成release不能清掉恢复屏障。 */
    public static void nativeKey(InputConstants.Key key,int action){
        var mc=Minecraft.getInstance();if(!mc.isWindowActive())return;
        var all=owned();for(int n=0;n<all.length;n++)if(InputConstants.getKey(all[n].saveString()).equals(key)){
            if(action==GLFW.GLFW_RELEASE)MENU.released(1<<n);
            else if(mc.gui.screen()!=null||MENU.waiting())MENU.requireRelease(1<<n);
        }
    }
    /** handleKeybinds前仅清冲突原版映射，不改物理键/自有映射或正常玩家。 */
    public static void suppressVanilla(){if(M0DClient.ownsDynamic())clearVanilla();}
    private static void clearVanilla(){
        var o=Minecraft.getInstance().options;
        for(var k:new KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyShift,o.keySprint,o.keyDrop}){
            // 原版ToggleKeyMapping在切换模式下忽略setDown(false)，先将已开状态切回关闭。
            if(k instanceof net.minecraft.client.ToggleKeyMapping&&k.isDown())k.setDown(true);
            k.setDown(false);while(k.consumeClick()){}
        }
        var own=java.util.List.of(UP,DOWN,LEFT,RIGHT,YAW_LEFT,YAW_RIGHT,MORE,LESS,EXIT,FREE,VANILLA);
        for(var k:o.keyMappings){
            if(own.contains(k))continue;
            if(own.stream().anyMatch(b->!b.isUnbound()&&b.same(k))){
                if(k instanceof net.minecraft.client.ToggleKeyMapping&&k.isDown())k.setDown(true);
                k.setDown(false);while(k.consumeClick()){}
            }
        }
    }
}
