package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory; 
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class KillFX extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLightning = settings.createGroup("闪电设置");
    private final SettingGroup sgParticles = settings.createGroup("粒子特效");
    private final SettingGroup sgSound = settings.createGroup("声音音效");
    private final SettingGroup sgExtra = settings.createGroup("额外视觉");

    // --- 通用设置 ---
    private final Setting<Boolean> onlyTargeted = sgGeneral.add(new BoolSetting.Builder()
        .name("仅限攻击目标")
        .description("只有你攻击过的生物死亡时才触发特效。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> targetTimeout = sgGeneral.add(new DoubleSetting.Builder()
        .name("记忆时间")
        .description("记住攻击目标的秒数。")
        .defaultValue(3.5)
        .min(0.5)
        .sliderMax(10)
        .visible(onlyTargeted::get)
        .build()
    );

    // --- 闪电设置 ---
    private final Setting<Boolean> useLightning = sgLightning.add(new BoolSetting.Builder()
        .name("启用闪电")
        .description("击杀时召唤闪电。")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> lightningAmount = sgLightning.add(new IntSetting.Builder()
        .name("闪电数量")
        .description("同时劈下的闪电数量。")
        .defaultValue(1)
        .min(1)
        .sliderMax(10)
        .visible(useLightning::get)
        .build()
    );

    // ==========================================
    //              粒子设置
    // ==========================================
    private final Setting<Boolean> useParticles = sgParticles.add(new BoolSetting.Builder()
        .name("启用粒子")
        .defaultValue(true)
        .build()
    );

    public enum ParticleCategory {
        Combat("战斗/打击"),
        Magic("魔法/光效"),
        Fire("火焰/烟雾"),
        Nature("自然/生物"),
        Update121("1.21新粒子"),
        Misc("其他");

        final String name;
        ParticleCategory(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    private final Setting<ParticleCategory> particleCategory = sgParticles.add(new EnumSetting.Builder<ParticleCategory>()
        .name("粒子分类")
        .defaultValue(ParticleCategory.Magic)
        .visible(useParticles::get)
        .build()
    );

    // 1. 战斗 (移除了不兼容的 SHRIEK)
    public enum CombatParticle {
        Damage(ParticleTypes.DAMAGE_INDICATOR, "伤害爱心"),
        Crit(ParticleTypes.CRIT, "暴击星"),
        EnchantedHit(ParticleTypes.ENCHANTED_HIT, "附魔暴击"),
        Sweep(ParticleTypes.SWEEP_ATTACK, "横扫之刃"),
        Explosion(ParticleTypes.EXPLOSION, "爆炸微尘"),
        ExplosionHuge(ParticleTypes.EXPLOSION_EMITTER, "巨大爆炸"),
        Sonic(ParticleTypes.SONIC_BOOM, "监守者音波"),
        Totem(ParticleTypes.TOTEM_OF_UNDYING, "不死图腾"); 
        final ParticleEffect p; final String n; CombatParticle(ParticleEffect p, String n) { this.p=p; this.n=n; } @Override public String toString() { return n; }
    }
    private final Setting<CombatParticle> pCombat = sgParticles.add(new EnumSetting.Builder<CombatParticle>().name("战斗粒子").defaultValue(CombatParticle.Crit).visible(() -> useParticles.get() && particleCategory.get() == ParticleCategory.Combat).build());

    // 2. 魔法
    public enum MagicParticle {
        Witch(ParticleTypes.WITCH, "女巫魔法"),
        Dragon(ParticleTypes.DRAGON_BREATH, "龙息"),
        EndRod(ParticleTypes.END_ROD, "末地烛光"),
        Portal(ParticleTypes.PORTAL, "传送门"),
        Enchant(ParticleTypes.ENCHANT, "附魔符文"),
        Effect(ParticleTypes.EFFECT, "药水波纹"),
        Instant(ParticleTypes.INSTANT_EFFECT, "瞬间药水"),
        Nautilus(ParticleTypes.NAUTILUS, "潮涌核心"),
        Flash(ParticleTypes.FLASH, "烟花闪光"),
        SculkCharge(ParticleTypes.SCULK_CHARGE_POP, "幽匿充能"), 
        Soul(ParticleTypes.SOUL, "灵魂出窍"); 
        final ParticleEffect p; final String n; MagicParticle(ParticleEffect p, String n) { this.p=p; this.n=n; } @Override public String toString() { return n; }
    }
    private final Setting<MagicParticle> pMagic = sgParticles.add(new EnumSetting.Builder<MagicParticle>().name("魔法粒子").defaultValue(MagicParticle.EndRod).visible(() -> useParticles.get() && particleCategory.get() == ParticleCategory.Magic).build());

    // 3. 火焰
    public enum FireParticle {
        Flame(ParticleTypes.FLAME, "普通火焰"),
        SoulFlame(ParticleTypes.SOUL_FIRE_FLAME, "灵魂火焰"),
        Lava(ParticleTypes.LAVA, "岩浆崩裂"),
        LargeSmoke(ParticleTypes.LARGE_SMOKE, "浓烈黑烟"),
        Campfire(ParticleTypes.CAMPFIRE_COSY_SMOKE, "营火轻烟"),
        Glow(ParticleTypes.GLOW, "发光微尘"),
        Wax(ParticleTypes.WAX_ON, "铜块涂蜡"),
        Spark(ParticleTypes.ELECTRIC_SPARK, "电火花");
        final ParticleEffect p; final String n; FireParticle(ParticleEffect p, String n) { this.p=p; this.n=n; } @Override public String toString() { return n; }
    }
    private final Setting<FireParticle> pFire = sgParticles.add(new EnumSetting.Builder<FireParticle>().name("火焰粒子").defaultValue(FireParticle.Flame).visible(() -> useParticles.get() && particleCategory.get() == ParticleCategory.Fire).build());

    // 4. 自然 (移除了不兼容的 Egg)
    public enum NatureParticle {
        Heart(ParticleTypes.HEART, "红色爱心"),
        Cloud(ParticleTypes.CLOUD, "云朵"),
        Rain(ParticleTypes.RAIN, "雨滴"),
        Snow(ParticleTypes.SNOWFLAKE, "雪花"),
        Slime(ParticleTypes.ITEM_SLIME, "史莱姆"),
        Bubble(ParticleTypes.BUBBLE, "气泡"),
        Note(ParticleTypes.NOTE, "音符"),
        Cherry(ParticleTypes.CHERRY_LEAVES, "樱花瓣"),
        Spore(ParticleTypes.SPORE_BLOSSOM_AIR, "孢子花");
        final ParticleEffect p; final String n; NatureParticle(ParticleEffect p, String n) { this.p=p; this.n=n; } @Override public String toString() { return n; }
    }
    private final Setting<NatureParticle> pNature = sgParticles.add(new EnumSetting.Builder<NatureParticle>().name("自然粒子").defaultValue(NatureParticle.Heart).visible(() -> useParticles.get() && particleCategory.get() == ParticleCategory.Nature).build());

    // 5. 1.21 新粒子
    public enum UpdateParticle {
        Gust(ParticleTypes.GUST, "旋风(风弹)"),
        GustSmall(ParticleTypes.SMALL_GUST, "小旋风"),
        Trial(ParticleTypes.TRIAL_SPAWNER_DETECTION, "试炼刷怪笼"),
        Ominous(ParticleTypes.OMINOUS_SPAWNING, "不祥之兆"),
        Vault(ParticleTypes.VAULT_CONNECTION, "宝库连接"),
        Raid(ParticleTypes.RAID_OMEN, "袭击预兆"),
        TrialOmen(ParticleTypes.TRIAL_OMEN, "试炼预兆");
        final ParticleEffect p; final String n; UpdateParticle(ParticleEffect p, String n) { this.p=p; this.n=n; } @Override public String toString() { return n; }
    }
    private final Setting<UpdateParticle> pUpdate = sgParticles.add(new EnumSetting.Builder<UpdateParticle>().name("1.21粒子").defaultValue(UpdateParticle.Ominous).visible(() -> useParticles.get() && particleCategory.get() == ParticleCategory.Update121).build());

    // 6. 其他
    public enum MiscParticle {
        Ash(ParticleTypes.ASH, "火山灰"),
        Mycelium(ParticleTypes.MYCELIUM, "菌丝"),
        SculkSoul(ParticleTypes.SCULK_SOUL, "幽匿灵魂"),
        Happy(ParticleTypes.HAPPY_VILLAGER, "村民高兴"),
        Angry(ParticleTypes.ANGRY_VILLAGER, "村民生气"),
        Sneeze(ParticleTypes.SNEEZE, "打喷嚏"),
        Ink(ParticleTypes.SQUID_INK, "墨囊");
        final ParticleEffect p; final String n; MiscParticle(ParticleEffect p, String n) { this.p=p; this.n=n; } @Override public String toString() { return n; }
    }
    private final Setting<MiscParticle> pMisc = sgParticles.add(new EnumSetting.Builder<MiscParticle>().name("其他粒子").defaultValue(MiscParticle.SculkSoul).visible(() -> useParticles.get() && particleCategory.get() == ParticleCategory.Misc).build());


    // --- 粒子形状 ---
    public enum ParticleShape {
        Burst("爆炸散开"),
        Sphere("球体包裹"),
        Spiral("螺旋上升"),
        Column("光柱升天"),
        Halo("头顶光环"),
        Heart("爱心轮廓"),  
        Helix("双螺旋DNA"), 
        Star("五角星阵"),   
        Ring("冲击波圆环"); 

        final String name; ParticleShape(String name) { this.name = name; } @Override public String toString() { return name; }
    }
    private final Setting<ParticleShape> particleShape = sgParticles.add(new EnumSetting.Builder<ParticleShape>().name("粒子形状").defaultValue(ParticleShape.Burst).visible(useParticles::get).build());
    private final Setting<Integer> particleCount = sgParticles.add(new IntSetting.Builder().name("粒子数量").defaultValue(40).min(5).sliderMax(200).visible(useParticles::get).build());
    private final Setting<Double> particleSpeed = sgParticles.add(new DoubleSetting.Builder().name("粒子速度").defaultValue(0.2).min(0.0).max(2.0).visible(useParticles::get).build());

    // ==========================================
    //              声音设置
    // ==========================================
    private final Setting<Boolean> useSound = sgSound.add(new BoolSetting.Builder()
        .name("启用声音")
        .defaultValue(true)
        .build()
    );

    public enum SoundGroup {
        Combat("硬核战斗"),
        Magic("魔法科技"),
        Creature("生物叫声"),
        Fun("趣味恶搞");
        final String name; SoundGroup(String name) { this.name = name; } @Override public String toString() { return name; }
    }

    private final Setting<SoundGroup> soundGroup = sgSound.add(new EnumSetting.Builder<SoundGroup>()
        .name("音效分类")
        .defaultValue(SoundGroup.Combat)
        .visible(useSound::get)
        .build()
    );

    // 1. 战斗音效
    public enum CombatSound {
        Thunder("entity.lightning_bolt.thunder", "雷劈"),
        Explode("entity.generic.explode", "爆炸"),
        Anvil("block.anvil.land", "铁砧压扁"),
        Trident("item.trident.thunder", "三叉戟雷声"),
        WitherSpawn("entity.wither.spawn", "凋零生成"),
        WitherShoot("entity.wither.shoot", "凋零射击"),
        Anchor("block.respawn_anchor.deplete", "重生锚爆炸"),
        Crystal("entity.end_crystal.explode", "水晶爆炸"),
        Break("item.shield.break", "盾牌破碎"),
        Crit("entity.player.attack.crit", "暴击音效"),
        Smash("item.mace.smash_ground", "重锤砸地(1.21)");
        final String id; final String n; CombatSound(String id, String n) { this.id=id; this.n=n; } @Override public String toString() { return n; }
    }
    private final Setting<CombatSound> sCombat = sgSound.add(new EnumSetting.Builder<CombatSound>().name("战斗音效").defaultValue(CombatSound.Thunder).visible(() -> useSound.get() && soundGroup.get() == SoundGroup.Combat).build());

    // 2. 魔法音效
    public enum MagicSound {
        AnchorCharge("block.respawn_anchor.charge", "重生锚充能"),
        AnchorSet("block.respawn_anchor.set_spawn", "重生锚设定"),
        Totem("item.totem.use", "不死图腾"),
        Beacon("block.beacon.activate", "信标启动"),
        Conduit("block.conduit.activate", "潮涌核心"),
        Portal("block.portal.trigger", "传送门噪音"),
        LevelUp("entity.player.levelup", "升级叮声"),
        Enchant("block.enchantment_table.use", "附魔台"),
        Teleport("entity.enderman.teleport", "瞬移"),
        Bell("block.bell.use", "钟声"),
        Chime("block.amethyst_block.chime", "紫水晶风铃");
        final String id; final String n; MagicSound(String id, String n) { this.id=id; this.n=n; } @Override public String toString() { return n; }
    }
    private final Setting<MagicSound> sMagic = sgSound.add(new EnumSetting.Builder<MagicSound>().name("魔法音效").defaultValue(MagicSound.AnchorCharge).visible(() -> useSound.get() && soundGroup.get() == SoundGroup.Magic).build());

    // 3. 生物音效
    public enum CreatureSound {
        Warden("entity.warden.sonic_boom", "监守者音波"),
        WardenHeart("entity.warden.heartbeat", "监守者心跳"),
        Dragon("entity.ender_dragon.death", "末影龙死亡"),
        DragonGrowl("entity.ender_dragon.growl", "末影龙咆哮"),
        Blaze("entity.blaze.death", "烈焰人"),
        Ghast("entity.ghast.scream", "恶魂尖叫"),
        Enderman("entity.enderman.stare", "小黑怒视"),
        Phantom("entity.phantom.bite", "幻翼撕咬"),
        Wolf("entity.wolf.howl", "狼嚎"),
        Cat("entity.cat.hiss", "猫哈气");
        final String id; final String n; CreatureSound(String id, String n) { this.id=id; this.n=n; } @Override public String toString() { return n; }
    }
    private final Setting<CreatureSound> sCreature = sgSound.add(new EnumSetting.Builder<CreatureSound>().name("生物音效").defaultValue(CreatureSound.Warden).visible(() -> useSound.get() && soundGroup.get() == SoundGroup.Creature).build());

    // 4. 趣味音效
    public enum FunSound {
        Burp("entity.player.burp", "打嗝"),
        Pling("block.note_block.pling", "音符盒Pling"),
        Goat("entity.goat.screaming.milk", "山羊尖叫"),
        No("entity.villager.no", "村民:不行"),
        Yes("entity.villager.yes", "村民:好"),
        Eat("entity.generic.eat", "吃东西"),
        Toast("ui.toast.challenge_complete", "挑战完成"),
        Glass("block.glass.break", "玻璃心破碎");
        final String id; final String n; FunSound(String id, String n) { this.id=id; this.n=n; } @Override public String toString() { return n; }
    }
    private final Setting<FunSound> sFun = sgSound.add(new EnumSetting.Builder<FunSound>().name("趣味音效").defaultValue(FunSound.Pling).visible(() -> useSound.get() && soundGroup.get() == SoundGroup.Fun).build());

    private final Setting<Double> volume = sgSound.add(new DoubleSetting.Builder()
        .name("音量")
        .description("音量大小，数值越高声音传播距离越远。")
        .defaultValue(1.0)
        .min(0.0)
        .max(10.0)     
        .sliderMax(5.0) 
        .visible(useSound::get)
        .build()
    );
    
    private final Setting<Double> pitch = sgSound.add(new DoubleSetting.Builder().name("音调").description("较低的音调听起来更深沉。").defaultValue(1.0).min(0.5).max(2.0).visible(useSound::get).build());

    // --- 额外设置 ---
    private final Setting<Boolean> useFirework = sgExtra.add(new BoolSetting.Builder().name("生成烟花").defaultValue(false).build());
    private final Setting<Boolean> useExplosion = sgExtra.add(new BoolSetting.Builder().name("生成爆炸烟雾").defaultValue(false).build());

    // 数据缓存
    private final Set<Integer> processedEntities = new HashSet<>();
    private final Map<Integer, Long> attackedTargets = new HashMap<>();

    public KillFX() {
        super(AddonTemplate.CATEGORY, "KillFX", "当生物死亡时渲染自定义的组合特效。");
    }

    @Override
    public void onActivate() {
        processedEntities.clear();
        attackedTargets.clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        processedEntities.clear();
        attackedTargets.clear();
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (event.entity instanceof LivingEntity) {
            attackedTargets.put(event.entity.getId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        if (onlyTargeted.get()) {
            long threshold = (long) (targetTimeout.get() * 1000);
            long now = System.currentTimeMillis();
            attackedTargets.entrySet().removeIf(entry -> now - entry.getValue() > threshold);
        }

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity living && entity != mc.player) {
                if (living.getHealth() <= 0.0f || living.isDead()) {
                    
                    if (processedEntities.contains(entity.getId())) continue;

                    if (onlyTargeted.get() && !attackedTargets.containsKey(entity.getId())) {
                        processedEntities.add(entity.getId());
                        continue;
                    }

                    renderEffects(living);
                    processedEntities.add(entity.getId());
                }
            }
        }
    }

    private void renderEffects(LivingEntity entity) {
        Vec3d pos = entity.getPos();

        // 1. 闪电
        if (useLightning.get()) {
            int amount = lightningAmount.get();
            for (int i = 0; i < amount; i++) {
                LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
                double offsetX = (i == 0) ? 0 : (Math.random() - 0.5) * 0.5;
                double offsetZ = (i == 0) ? 0 : (Math.random() - 0.5) * 0.5;
                lightning.setPosition(pos.x + offsetX, pos.y, pos.z + offsetZ);
                mc.world.addEntity(lightning);
            }
        }

        // 2. 粒子
        if (useParticles.get()) {
            ParticleEffect effect = null;
            switch (particleCategory.get()) {
                case Combat -> effect = pCombat.get().p;
                case Magic -> effect = pMagic.get().p;
                case Fire -> effect = pFire.get().p;
                case Nature -> effect = pNature.get().p;
                case Update121 -> effect = pUpdate.get().p;
                case Misc -> effect = pMisc.get().p;
            }
            if (effect != null) {
                spawnParticles(entity, effect);
            }
        }

        // 3. 声音
        if (useSound.get()) {
            String soundId = "entity.lightning_bolt.thunder";
            switch (soundGroup.get()) {
                case Combat -> soundId = sCombat.get().id;
                case Magic -> soundId = sMagic.get().id;
                case Creature -> soundId = sCreature.get().id;
                case Fun -> soundId = sFun.get().id;
            }
            
            mc.world.playSound(mc.player, BlockPos.ofFloored(pos), 
                SoundEvent.of(Identifier.of("minecraft", soundId)), 
                SoundCategory.PLAYERS, 
                volume.get().floatValue(), pitch.get().floatValue());
        }

        // 4. 烟花
        if (useFirework.get()) {
            ItemStack itemStack = new ItemStack(Items.FIREWORK_ROCKET);
            FireworkRocketEntity rocket = new FireworkRocketEntity(mc.world, itemStack, entity);
            rocket.setPosition(pos.x, pos.y + 0.5, pos.z);
            mc.world.addEntity(rocket);
            mc.world.addParticle(ParticleTypes.EXPLOSION, pos.x, pos.y + 1, pos.z, 0, 0, 0);
        }

        // 5. 爆炸烟雾
        if (useExplosion.get()) {
            mc.world.addParticle(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1, pos.z, 0, 0, 0);
        }
    }

    private void spawnParticles(LivingEntity entity, ParticleEffect effect) {
        Vec3d pos = entity.getPos();
        int count = particleCount.get();
        double speed = particleSpeed.get();
        double height = entity.getHeight();
        double width = entity.getWidth();

        switch (particleShape.get()) {
            case Burst -> {
                for (int i = 0; i < count; i++) {
                    mc.world.addParticle(effect, 
                        pos.x + (Math.random() - 0.5) * width, 
                        pos.y + Math.random() * height, 
                        pos.z + (Math.random() - 0.5) * width, 
                        (Math.random() - 0.5) * speed, 
                        (Math.random() - 0.5) * speed, 
                        (Math.random() - 0.5) * speed
                    );
                }
            }
            case Sphere -> {
                for (int i = 0; i < count; i++) {
                    double u = Math.random();
                    double v = Math.random();
                    double theta = 2 * Math.PI * u;
                    double phi = Math.acos(2 * v - 1);
                    double r = 1.0; 
                    double dx = r * Math.sin(phi) * Math.cos(theta);
                    double dy = r * Math.sin(phi) * Math.sin(theta);
                    double dz = r * Math.cos(phi);
                    
                    mc.world.addParticle(effect, 
                        pos.x + dx, pos.y + dy + height / 2, pos.z + dz, 
                        0, 0, 0
                    );
                }
            }
            case Spiral -> {
                for (int i = 0; i < count; i++) {
                    double yOffset = ((double) i / count) * 2.5;
                    double angle = yOffset * 5;
                    double radius = 0.8;
                    double dx = Math.cos(angle) * radius;
                    double dz = Math.sin(angle) * radius;
                    
                    mc.world.addParticle(effect, 
                        pos.x + dx, pos.y + yOffset, pos.z + dz, 
                        0, 0.05, 0 
                    );
                }
            }
            case Column -> {
                for (int i = 0; i < count; i++) {
                    mc.world.addParticle(effect, 
                        pos.x + (Math.random() - 0.5) * width, 
                        pos.y + 0.1, 
                        pos.z + (Math.random() - 0.5) * width, 
                        0, speed, 0
                    );
                }
            }
            case Halo -> {
                for (int i = 0; i < count; i++) {
                    double angle = (double) i / count * Math.PI * 2;
                    double radius = 0.7;
                    double dx = Math.cos(angle) * radius;
                    double dz = Math.sin(angle) * radius;
                    
                    mc.world.addParticle(effect, 
                        pos.x + dx, pos.y + height + 0.5, pos.z + dz, 
                        0, 0, 0
                    );
                }
            }
            case Heart -> {
                for (int i = 0; i < count; i++) {
                    double t = ((double) i / count) * Math.PI * 2;
                    double hx = 16 * Math.pow(Math.sin(t), 3);
                    double hz = 13 * Math.cos(t) - 5 * Math.cos(2*t) - 2 * Math.cos(3*t) - Math.cos(4*t);
                    double scale = 0.06;
                    mc.world.addParticle(effect, 
                        pos.x + hx * scale, 
                        pos.y + 1.2 + (hz * scale * 0.5), 
                        pos.z + hz * scale, 
                        0, 0, 0
                    );
                }
            }
            case Helix -> {
                for (int i = 0; i < count; i++) {
                    double h = ((double) i / count) * 3.0; 
                    double angle = h * 4.0;
                    double radius = 0.6;
                    
                    mc.world.addParticle(effect, 
                        pos.x + Math.cos(angle) * radius, 
                        pos.y + h, 
                        pos.z + Math.sin(angle) * radius, 
                        0, 0, 0
                    );
                    
                    mc.world.addParticle(effect, 
                        pos.x + Math.cos(angle + Math.PI) * radius, 
                        pos.y + h, 
                        pos.z + Math.sin(angle + Math.PI) * radius, 
                        0, 0, 0
                    );
                }
            }
            case Star -> {
                for (int i = 0; i < count; i++) {
                    double angle = ((double) i / count) * Math.PI * 2;
                    double dx = Math.cos(angle) * 1.0;
                    double dz = Math.sin(angle) * 1.0;
                    mc.world.addParticle(effect, 
                        pos.x, pos.y + 0.2, pos.z, 
                        dx * speed, 0, dz * speed 
                    );
                }
            }
            case Ring -> {
                for (int i = 0; i < count; i++) {
                    double angle = ((double) i / count) * Math.PI * 2;
                    double dx = Math.cos(angle);
                    double dz = Math.sin(angle);
                    mc.world.addParticle(effect, 
                        pos.x + dx * 0.2, 
                        pos.y + 0.1, 
                        pos.z + dz * 0.2, 
                        dx * speed * 2, 0, dz * speed * 2 
                    );
                }
            }
        }
    }
}