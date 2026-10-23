package com.codigohasta.addon.utils.gcore;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.entity.EntityType;

import java.util.Set;

/**
 * TP 模块的目标选择配置。
 *
 * <p>默认值与范围：
 * <pre>
 * Priority          = Distance
 * Range             = 500.0   (0.1 ~ 500)
 * TargetPlayer      = true
 * TargetMob         = false
 * TargetAnimal      = false
 * TargetInvisible   = true
 * TargetFriend      = false
 * </pre>
 *
 * <p><b>框架适配</b>：这里接收 Meteor {@link SettingGroup}（与 {@link GcoreTpOption} 一致）。
 */
public final class GcoreTpSelectTargetsConfig {

    private final Setting<GcoreTargetUtil.Priority> priority;
    private final Setting<Double> range;
    private final Setting<Boolean> targetPlayer;
    private final Setting<Boolean> targetMob;
    private final Setting<Boolean> targetAnimal;
    private final Setting<Boolean> targetInvisible;
    private final Setting<Boolean> targetFriend;

    /**
     * ★ 新增（Q1）：生物列表。
     *
     * <p>用 Meteor 的 {@link EntityTypeListSetting}，可在 GUI 里勾选，
     * 也可**直接输入 {@code monster} / {@code animal} / {@code wateranimal} /
     * {@code ambient} / {@code misc}** 一键勾选整组
     * （由 {@code EntityTypeListSetting.parseImpl} 支持）。
     * 其中 {@code monster} 组 = {@code SpawnGroup.MONSTER}，
     * **天然排除村民(MISC)、鱼(WATER_AMBIENT)、铁傀儡(MISC)、悦灵(CREATURE)**
     * —— 正是「勾怪物却打到村民/鱼」这个问题的正解。
     *
     * <p><b>默认空 = 不生效</b>，此时完全走原来的
     * {@code 目标玩家/目标怪物/目标动物} 三个开关。
     * 这样用户既有配置**零丢失**（旧设置不被替换、不改名）。
     */
    private final Setting<Set<EntityType<?>>> entityTypes;

    public GcoreTpSelectTargetsConfig(SettingGroup group) {
        this(group, 500.0, 500.0, false, false);
    }

    /**
     * 自定义默认值的构造。
     *
     * <p>两套默认值：
     * <pre>
     *                  Range         TargetMob  TargetAnimal
     * SelectTargets    8.0 (≤20)     true       true
     * TpSelectTargets  500.0 (≤500)  false      false
     * </pre>
     * 为免复制一整个类，这里用参数化的默认值来表达差异。
     * 默认构造保持 {@code TpSelectTargets} 的值，因此既有模块行为不变。
     *
     * @param rangeDefault 搜索半径默认值
     * @param rangeMax     搜索半径上限
     * @param mobDefault   目标怪物 默认值
     * @param animalDefault 目标动物 默认值
     */
     public GcoreTpSelectTargetsConfig(SettingGroup group, double rangeDefault, double rangeMax,
                                      boolean mobDefault, boolean animalDefault) {
        this.priority = group.add(new EnumSetting.Builder<GcoreTargetUtil.Priority>()
            .name("优先级")
            .description("目标优先级：Distance 最近、Hurttime 刚受伤、Health 血最少、SelfFov 视角最接近。")
            .defaultValue(GcoreTargetUtil.Priority.Distance)
            .build());

        this.range = group.add(new DoubleSetting.Builder()
            .name("目标搜索半径")
            .description("目标搜索半径。")
            .defaultValue(rangeDefault)
            .range(0.1, rangeMax)
            .sliderRange(0.1, rangeMax)
            .build());

        this.targetPlayer = group.add(new BoolSetting.Builder()
            .name("目标玩家")
            .description("把玩家作为目标。")
            .defaultValue(true)
            .build());

        this.targetMob = group.add(new BoolSetting.Builder()
            .name("目标怪物")
            .description("把怪物作为目标。")
            .defaultValue(mobDefault)
            .build());

        this.targetAnimal = group.add(new BoolSetting.Builder()
            .name("目标动物")
            .description("把动物作为目标。")
            .defaultValue(animalDefault)
            .build());

        this.targetInvisible = group.add(new BoolSetting.Builder()
            .name("目标隐身")
            .description("把隐身实体作为目标。")
            .defaultValue(true)
            .build());

        this.targetFriend = group.add(new BoolSetting.Builder()
            .name("目标好友")
            .description("把好友也作为目标。")
            .defaultValue(false)
            .build());

        // ★ 新增（Q1）：生物列表（默认空 = 不生效，保留旧三开关行为）
        //   onlyAttackable() 会过滤掉投掷物等不可攻击实体（Meteor 的 EntityUtils.isAttackable，
        //   已核实其排除清单全是 AREA_EFFECT_CLOUD/ARROW/ITEM/... 等，**不含 PLAYER**）。
        this.entityTypes = group.add(new EntityTypeListSetting.Builder()
            .name("生物列表")
            .description("精确指定要攻击的生物种类。\n"
                + "可直接输入 monster / animal / wateranimal / ambient / misc 一键勾选整组，\n"
                + "其中 monster 组已是「真怪物」（不含村民、鱼、铁傀儡、悦灵）。\n"
                + "留空 = 使用下面的「目标玩家 / 目标怪物 / 目标动物」三个开关。")
            .onlyAttackable()
            .defaultValue()
            .build());
    }

    public GcoreTargetUtil.Priority getPriority() {
        return this.priority.get();
    }

    public float getRange() {
        return this.range.get().floatValue();
    }

    public boolean getTargetPlayer() {
        return this.targetPlayer.get();
    }

    public boolean getTargetMob() {
        return this.targetMob.get();
    }

    public boolean getTargetAnimal() {
        return this.targetAnimal.get();
    }

    public boolean getTargetInvisible() {
        return this.targetInvisible.get();
    }

    public boolean getTargetFriend() {
        return this.targetFriend.get();
    }

    /**
     * ★ 新增（Q1）：生物列表。
     *
     * @return 非 null；**空集合表示「不使用列表」**（退回旧三开关逻辑）
     */
    public Set<EntityType<?>> getEntityTypes() {
        return this.entityTypes.get();
    }

    public void setTargetFriend(boolean value) {
        this.targetFriend.set(value);
    }
}
