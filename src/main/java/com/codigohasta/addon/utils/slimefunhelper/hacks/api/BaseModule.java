package com.codigohasta.addon.utils.slimefunhelper.hacks.api;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.slimefunhelper.events.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind;
import java.util.*;
import java.util.function.*;
public abstract class BaseModule extends meteordevelopment.meteorclient.systems.modules.Module {
    protected static final net.minecraft.client.MinecraftClient mc=net.minecraft.client.MinecraftClient.getInstance();
    private FlagRef bound;
    private boolean deactivating;
    private final List<KeyBindRef> keys=new ArrayList<>();
    public BaseModule(String name){super(AddonTemplate.SF_CATEGORY,"sf-"+name.replaceAll("([a-z])([A-Z])","$1-$2").toLowerCase(Locale.ROOT),"SlimefunHelper "+name);com.codigohasta.addon.utils.slimefunhelper.SfTranslations.bindModule(this,name);}
    public static ModulePath makePath(String config,String path){return new ModulePath(path);}
    public void bindFlag(FlagRef flag){bound=flag;}
    public final boolean enabled(){return isActive();}
    public void registerAll(){}
    public void onCreate(){}
    public void onEnableModule(){}
    public void onDisableModule(){}
    @Override public void onActivate(){if(bound!=null)bound.set(true);onEnableModule();}
    @Override public boolean isActive(){return !deactivating&&super.isActive();}
    @Override public void onDeactivate(){deactivating=true;try{if(bound!=null)bound.set(false);onDisableModule();}finally{deactivating=false;}}
    @Override public void toggle(){super.toggle();if(!isActive())com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime.onModuleDisabled();}
    public void pollKeys(){for(var key:keys)key.poll();if(bound!=null&&bound.get()!=isActive())toggle();}
    public boolean checkNull(){return mc.player==null||mc.world==null;}
    @SuppressWarnings("unchecked") public <T>T cast(){return (T)this;}
    public void logI18N(String text,Object...args){info(com.codigohasta.addon.utils.slimefunhelper.SourceText.message(text,args));}
    public void logI18NSub(String sub,String text,Object...args){logI18N(text,args);}
    public void log(String text){info(text);}
    public <T> void registerListener(Channel<T> point,Consumer<Event<T>> listener){registerListener(point,listener,0);}
    public <T> void registerListener(Channel<T> point,Consumer<Event<T>> listener,int priority){point.add(e->{if(isActive()&&!checkNull())listener.accept(e);},priority);}
    public <T> void registerServiceListener(Channel<T> point,Consumer<Event<T>> listener){registerServiceListener(point,listener,0);}
    public <T> void registerServiceListener(Channel<T> point,Consumer<Event<T>> listener,int priority){point.add(e->{if(!checkNull()&&com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime.modifying())listener.accept(e);},priority);}
    public ConfigBuilder<Boolean> flagBuilder(ModulePath path){return builder(path,Boolean.class);}
    public ConfigBuilder<Integer> intBuilder(ModulePath path){return builder(path,Integer.class);}
    public ConfigBuilder<Double> doubleBuilder(ModulePath path){return builder(path,Double.class);}
    public <T> ConfigBuilder<T> builder(ModulePath path,Class<T> type){return new ConfigBuilder<>(this,path,type);}
    public ConfigBuilder<MultiKeyBind> hotkey(ModulePath path){return builder(path,MultiKeyBind.class);}
    public ConfigBuilder<MultiKeyBind> hotkey(ModulePath path,MultiKeyBind key){return hotkey(path).defaultValue(key);}
    public ConfigBuilder<MultiKeyBind> moduleEntry(ModulePath path,MultiKeyBind key,ModulePath target,Object...meta){return toggleHotkey(path,key,target);}
    public ConfigBuilder<MultiKeyBind> toggleHotkey(ModulePath path,MultiKeyBind key,ModulePath target){return hotkey(path,key).registerHotkey(()->{ConfigBuilder.toggle(this,target);});}
    public Object moduleMeta(Object... refs){return refs;}
    public Object moduleMeta(Supplier<?> supplier){return supplier;}
    public void addKey(KeyBindRef key){keys.add(key);}
}
