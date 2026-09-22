package com.codigohasta.addon.utils.slimefunhelper.hacks.api;

import com.codigohasta.addon.utils.slimefunhelper.SfTranslations;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.*;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.config.*;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import java.util.*;
import java.util.function.*;
/** Adapts source configuration paths to persistent, editable Meteor settings. */
@SuppressWarnings({"unchecked","rawtypes"})
public class ConfigBuilder<T> {
    private static final Map<BaseModule,Map<String,Ref<?>>> REFS=new IdentityHashMap<>();
    private final BaseModule owner; private final ModulePath path; private final Class<T> type;
    private T value; private Predicate<T> validator=v->true; private Supplier<Boolean> visible=()->true;
    private Consumer<T> update=v->{}; private Runnable keyAction;
    public ConfigBuilder(BaseModule owner,ModulePath path,Class<T> type){this.owner=owner;this.path=path;this.type=type;}
    public ConfigBuilder<T> defaultValue(T v){value=v;return this;}
    public ConfigBuilder<T> validator(Predicate<T> v){validator=v;return this;}
    public ConfigBuilder<T> show(Supplier<Boolean> v){visible=v;return this;}
    public ConfigBuilder<T> hideConfig(){visible=()->false;return this;}
    public ConfigBuilder<T> experimental(){return this;}
    public ConfigBuilder<T> updateListener(Consumer<T> v){update=v;return this;}
    public ConfigBuilder<T> registerHotkey(Runnable action){keyAction=action;return this;}
    public static void toggle(BaseModule owner,ModulePath path){Ref<?> ref=REFS.getOrDefault(owner,Map.of()).get(path.path());if(ref instanceof FlagRef f)f.toggle();}
    public <R> R build(){
        String full=path.path(); int dot=full.lastIndexOf('.');
        String group=dot<0?"General":full.substring(0,dot); String name=dot<0?full:full.substring(dot+1);
        SettingGroup sg=owner.settings.getGroup(group);if(sg==null)sg=owner.settings.createGroup(group);
        SfTranslations.bindGroup(sg,group);
        if(type==MultiKeyBind.class){
            MultiKeyBind defaults=value instanceof MultiKeyBind bind?bind:new MultiKeyBind();
            Setting<Keybind> key=sg.add(new KeybindSetting.Builder().name(name).description(com.codigohasta.addon.utils.slimefunhelper.SourceText.description(full)).defaultValue(defaults.key).visible(()->visible.get()).build());
            SfTranslations.bindSetting(key,full);
            KeyBindRef ref=new KeyBindRef(key,keyAction);
            if(defaults.modifier!=-1){
                ref.modifier=sg.add(new KeybindSetting.Builder().name(name+"-modifier").defaultValue(Keybind.fromKey(defaults.modifier)).build());
                SfTranslations.bindSetting(ref.modifier,full+"-modifier");
            }
            ref.allowInScreen=full.equals("inventory.pick-item");
            owner.addKey(ref);return (R)ref;
        }
        if(value==null){if(type==Boolean.class)value=(T)Boolean.FALSE;else if(type==Integer.class)value=(T)Integer.valueOf(0);else if(type==Double.class)value=(T)Double.valueOf(0);}
        Ref<T> ref=(Ref<T>)(type==Boolean.class?new FlagRef((Boolean)value):type==Integer.class?new IntRef((Integer)value):type==Double.class?new DoubleRef((Double)value):type.isEnum()?new EnumRef<>(value):new NBTRef<>(value));
        REFS.computeIfAbsent(owner,k->new HashMap<>()).put(full,ref);
        Setting<T> setting=create(sg,name,com.codigohasta.addon.utils.slimefunhelper.SourceText.description(full),full,value,visible,validator,update);
        ref.connect(setting::get,v->{if(validator.test(v))setting.set(v);});return (R)ref;
    }
    private static <V> Setting<V> create(SettingGroup sg,String name,String description,String fullPath,V value,Supplier<Boolean> visible,Predicate<V> validator,Consumer<V> update){
        Setting<?> setting;
        if (value instanceof EntityTypeRegex filter) {
            EntityTypeFilterSetting picker = sg.add(new EntityTypeFilterSetting(name, description, filter,
                () -> visible.get(), v -> validator.test((V) v)));
            SfTranslations.bindSetting(picker, fullPath);
            return new ObjectSetting<V>(name,value,validator,update,()->false) {
                @Override public V get() { return (V) picker.filterValue(); }
                @Override public boolean set(V v) {
                    if (v == null || !validator.test(v)) return false;
                    boolean changed = picker.setFilter((EntityTypeRegex) v);
                    if (changed) update.accept(v);
                    return changed;
                }
            };
        }
        if(value instanceof Boolean b) setting=new BoolSetting.Builder().name(name).description(description).defaultValue(b).visible(()->visible.get()).onChanged(v->update.accept((V)v)).build();
        else if(value instanceof Integer n) {
            var builder = new IntSetting.Builder().name(name).description(description).defaultValue(n)
                .visible(()->visible.get()).onChanged(v->update.accept((V)v));
            if (validator == (Object) com.codigohasta.addon.utils.slimefunhelper.managers.Configs.INT_POSITIVE) builder.min(1);
            else if (validator == (Object) com.codigohasta.addon.utils.slimefunhelper.managers.Configs.INT_NONNEGATIVE) builder.min(0);
            setting = builder.build();
        }
        else if(value instanceof Double n) setting=new DoubleSetting.Builder().name(name).description(description).defaultValue(n).visible(()->visible.get()).onChanged(v->update.accept((V)v)).build();
        else if(value instanceof String text) setting=new StringSetting.Builder().name(name).description(description).defaultValue(text).visible(()->visible.get()).onChanged(v->update.accept((V)v)).build();
        else if(value instanceof Enum e) { EnumSetting.Builder b=new EnumSetting.Builder(); b.name(name); b.description(description); b.defaultValue(e); b.visible(()->visible.get()); b.onChanged(v->update.accept((V)v)); setting=b.build(); }
        else if(value instanceof OptionalPrimitive op){
            Setting<Boolean> active=create(sg,name+"-enabled",description,fullPath+"-enabled",op.present,visible,v->true,v->{});
            Setting<?> inner=create(sg,name+"-value",description,fullPath+"-value",op.value,()->visible.get()&&active.get(),v->validator.test((V)new OptionalPrimitive(active.get(),null,v)),v->{});
            return new ObjectSetting<V>(name,value,validator,update,()->false){
                @Override public V get(){return (V)new OptionalPrimitive(active.get(),null,inner.get());}
                @Override public boolean set(V v){if(!validator.test(v))return false;OptionalPrimitive next=(OptionalPrimitive)v;active.set(next.present);((Setting)inner).set(next.value);update.accept(v);return true;}
            };
        }else if(value instanceof WrapEnum wrap){
            Setting<Enum> enumSetting=create(sg,name,description,fullPath,(Enum)wrap.get(),visible,v->true,v->update.accept((V)new WrapEnum<>(v)));
            return new ObjectSetting<V>(name,value,validator,update,()->false){
                @Override public V get(){return (V)new WrapEnum<>(enumSetting.get());}
                @Override public boolean set(V v){if(v==null||!validator.test(v))return false;return enumSetting.set((Enum)((WrapEnum)v).get());}
            };
        }else {
            // Meteor's settings UI knows StringSetting; bind structured values to a real JSON field.
            com.google.gson.Gson gson=new com.google.gson.Gson();
            StringSetting json=new StringSetting.Builder().name(name).description(description+"\nJSON: "+gson.toJson(value)).defaultValue(gson.toJson(value)).visible(()->visible.get()).build();
            sg.add(json);
            SfTranslations.bindSetting(json,fullPath);
            return new ObjectSetting<V>(name,value,validator,update,()->false){
                private final JsonValueCache<V> cache=new JsonValueCache<>(gson,value,validator);
                @Override public V get(){return cache.get(json.get());}
                @Override public boolean set(V v){if(v==null||!validator.test(v))return false;String encoded=gson.toJson(v);json.set(encoded);cache.set(v,encoded);update.accept(v);return true;}
            };
        }
        Setting<V> result=(Setting<V>)sg.add((Setting)setting);
        SfTranslations.bindSetting(result,fullPath);
        // Keep validation on the source reference as well as after native UI edits.
        return new ObjectSetting<V>(name,value,validator,update,()->false){
            private V valid=value;
            @Override public V get(){V next=result.get();if(validator.test(next))valid=next;else result.set(valid);return valid;}
            @Override public boolean set(V v){if(v==null||!validator.test(v))return false;valid=v;return result.set(v);}
        };
    }
    /** JSON editor for structured source settings (labelled vectors and regexes). */
    private static class ObjectSetting<V> extends Setting<V> {
        private final Class<?> type; private final Predicate<V> validator;
        private static final com.google.gson.Gson GSON=new com.google.gson.Gson();
        ObjectSetting(String name,V value,Predicate<V> validator,Consumer<V> update,Supplier<Boolean> visible){super(name,"SlimefunHelper structured value (JSON)",value,update,null,()->visible.get());this.type=value.getClass();this.validator=validator;}
        protected V parseImpl(String text){try{return (V)GSON.fromJson(text,type);}catch(RuntimeException e){return null;}}
        protected boolean isValueValid(V v){return v!=null&&validator.test(v);}
        protected net.minecraft.nbt.NbtCompound save(net.minecraft.nbt.NbtCompound tag){tag.putString("value",GSON.toJson(get()));return tag;}
        protected V load(net.minecraft.nbt.NbtCompound tag){V v=parseImpl(tag.getString("value").orElse(""));set(v!=null?v:defaultValue);return get();}
    }
}
