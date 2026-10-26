package dev.elytra.control.mixin.m0d;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** 启动期只用JDK判断，默认不把任何实验注入应用到游戏。 */
public final class M0DMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!Boolean.getBoolean("efc.m0d.observe") && !Boolean.getBoolean("efc.m0d.experiment")) return true;
        try { UUID.fromString(System.getProperty("efc.m0d.player", "")); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
