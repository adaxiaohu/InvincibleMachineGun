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
    protected static java.util.Map<ModulePath, ModulePath> portPaths = new java.util.concurrent.ConcurrentHashMap<>();

    public static void portConfigs(ModulePath oldPath, ModulePath newPath) {
        portPaths.put(oldPath, newPath);
    }

    public static void portConfigs(ModulePath oldPath, ModulePath newPath, String key) {
        portConfigs(oldPath.add(key), newPath.add(key));
    }
    public void bindFlag(FlagRef flag){bound=flag;}
    public final boolean enabled(){return isActive();}
    public void registerAll(){}
    public void onCreate(){}
    public void onEnableModule(){}
    public void onDisableModule(){}
    private static final boolean SF_HELPER_PRESENT = com.codigohasta.addon.utils.SlimefunHelperCompat.isSlimefunHelperLoaded();
    /** True when SlimefunHelper owns the sfIMG feature set and this module must stay inert. */
    public static boolean stoodDown(){return SF_HELPER_PRESENT;}
    @Override public void onActivate(){if(bound!=null)bound.set(true);if(!SF_HELPER_PRESENT)onEnableModule();}
    @Override public boolean isActive(){return !deactivating&&super.isActive();}
    @Override public void onDeactivate(){deactivating=true;try{if(bound!=null)bound.set(false);if(!SF_HELPER_PRESENT)onDisableModule();}finally{deactivating=false;}}
    // 有 SlimefunHelper 时模块必须保持惰性：支持它的 mixin 已被 SfImgMixinPlugin 跳过，
    // 一旦被旧快捷键或旧的 active 标记激活，就会走到已失效的 accessor 转换上。
    @Override public void toggle(){if(SF_HELPER_PRESENT)return;super.toggle();if(!isActive())com.codigohasta.addon.utils.slimefunhelper.SlimefunRuntime.onModuleDisabled();}
    public void pollKeys(){for(var key:keys)key.poll();if(bound!=null&&bound.get()!=isActive())toggle();}
    public boolean checkNull(){return mc.player==null||mc.world==null;}
    @SuppressWarnings("unchecked") public <T>T cast(){return (T)this;}
    public void logI18N(String text,Object...args){info(com.codigohasta.addon.utils.slimefunhelper.SourceText.message(text,args));}
    public void logI18NSub(String sub,String text,Object...args){logI18N(text,args);}
    public void log(String text){info(text);}
    public <T> void registerListener(Channel<T> point,Consumer<Event<T>> listener){registerListener(point,listener,0);}
    public <T> void registerListener(Channel<T> point,Consumer<Event<T>> listener,int priority){point.add(e->{if(isActive()&&!checkNull())listener.accept(e);},priority);}
    public <W> void registerListener(com.codigohasta.addon.utils.slimefunhelper.events.channels.ListenerPoint<W> point,Consumer<W> listener){
        point.registerHandler(w -> {if(isActive()&&!checkNull())listener.accept(w);});
    }
    public <W> void registerListener(com.codigohasta.addon.utils.slimefunhelper.events.channels.ListenerPoint<W> point,Consumer<W> listener,int p){
        point.registerHandler(w -> {if(isActive()&&!checkNull())listener.accept(w);}, p);
    }
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

    /**
     * Applies the config-path migrations in {@link SfConfigMigration} to the incoming NBT
     * before Meteor reads it.
     *
     * <p>Meteor resolves a saved setting by the exact pair
     * {@code (SettingGroup.name, Setting.name)} -- see {@code Settings.fromTag:179} and
     * {@code SettingGroup.fromTag:83}. Upstream 1.1.7.2 regrouped many sfIMG settings, which
     * changed their group name, so without this rewrite a returning user's saved value would
     * not be found and would silently fall back to the default.
     *
     * <p>The rewrite must happen here rather than after loading: {@code Settings.fromTag} calls
     * {@code reset()} as its first statement, and {@code Modules.load:84-88} resets every setting
     * before reading the file, so by the time loading finishes the old group is unreachable.
     * Moving the saved entries first means Meteor's existing load path finds them unchanged.
     *
     * <h2>Why the destination group must already exist in code</h2>
     * {@code Settings.getGroup} (Settings.java:74-80) only LOOKS UP a group; it never creates one,
     * and {@code Settings.fromTag:180} skips a group whose lookup returned null:
     *
     * <pre>
     *   SettingGroup sg = getGroup(groupTag.getString("name", ""));
     *   if (sg != null) sg.fromTag(groupTag);
     * </pre>
     *
     * So migrating a value into a group that this module has not declared yet would move the
     * value somewhere that is never read -- silently losing it, which is worse than not migrating.
     * {@link #migrateSettingPaths} therefore requires the destination group to exist in the
     * module's own {@code Settings}. That makes every entry self-activating: it starts applying
     * in exactly the change that relocates the setting's declaration, and until then it is inert.
     */
    @Override
    public meteordevelopment.meteorclient.systems.modules.Module fromTag(net.minecraft.nbt.NbtCompound tag) {
        migrateSettingPaths(tag, settings);
        return super.fromTag(tag);
    }

    /** NBT shape: tag -> "settings" -> "groups" -> [ { "name": <group>, "settings": [ {"name": ...} ] } ]. */
    static void migrateSettingPaths(net.minecraft.nbt.NbtCompound tag,
                                    meteordevelopment.meteorclient.settings.Settings codeSettings) {
        if (!(tag.get("settings") instanceof net.minecraft.nbt.NbtCompound settings)) return;
        net.minecraft.nbt.NbtList groups = settings.getList("groups").orElse(null);
        if (groups == null) return;

        // Snapshot the group list first: destination groups may be appended below.
        int groupCount = groups.size();
        for (int gi = 0; gi < groupCount; gi++) {
            if (!(groups.get(gi) instanceof net.minecraft.nbt.NbtCompound group)) continue;
            String groupName = group.getString("name").orElse("");
            net.minecraft.nbt.NbtList settingsList = group.getList("settings").orElse(null);
            if (settingsList == null) continue;

            for (int si = settingsList.size() - 1; si >= 0; si--) {
                if (!(settingsList.get(si) instanceof net.minecraft.nbt.NbtCompound entry)) continue;
                String settingName = entry.getString("name").orElse("");
                SfConfigMigration.Move move = SfConfigMigration.find(groupName, settingName);
                if (move == null) continue;
                // Only migrate into a group this module actually DECLARES. Meteor never creates
                // a missing group while loading (Settings.getGroup only looks up, and
                // Settings.fromTag skips a null group), so moving a value into an undeclared
                // group would park it where nothing reads it -- silently losing the value,
                // which is worse than leaving it alone.
                //
                // This check also makes each migration self-activating: it starts applying in
                // exactly the change that relocates the setting's declaration, and does nothing
                // until then. Groups are created in code by ConfigBuilder.build() during module
                // construction, i.e. before any load happens.
                if (codeSettings == null || codeSettings.getGroup(move.newGroup) == null) continue;

                settingsList.remove(si);
                entry.putString("name", SfConfigMigration.rename(move, settingName));
                addToGroup(groups, move.newGroup, entry);
            }
        }
    }

    /** Finds the NBT group named {@code groupName} and appends {@code entry} to it. */
    private static void addToGroup(net.minecraft.nbt.NbtList groups,
                                   String groupName,
                                   net.minecraft.nbt.NbtCompound entry) {
        for (int i = 0; i < groups.size(); i++) {
            if (!(groups.get(i) instanceof net.minecraft.nbt.NbtCompound dest)) continue;
            if (!groupName.equals(dest.getString("name").orElse(null))) continue;
            net.minecraft.nbt.NbtList destSettings = dest.getList("settings").orElse(null);
            if (destSettings == null) {
                destSettings = new net.minecraft.nbt.NbtList();
                dest.put("settings", destSettings);
            }
            destSettings.add(entry);
            return;
        }
        // Destination group absent from the SAVE but present in code: create it in the tag so
        // the migrated entries have somewhere to land. Values that already exist there are
        // preserved because each entry is appended individually.
        net.minecraft.nbt.NbtCompound created = new net.minecraft.nbt.NbtCompound();
        created.putString("name", groupName);
        net.minecraft.nbt.NbtList createdSettings = new net.minecraft.nbt.NbtList();
        created.put("settings", createdSettings);
        createdSettings.add(entry);
        groups.add(created);
    }
}
