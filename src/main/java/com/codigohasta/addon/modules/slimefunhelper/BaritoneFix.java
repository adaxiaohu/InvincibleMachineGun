package com.codigohasta.addon.modules.slimefunhelper;


import com.codigohasta.addon.utils.slimefunhelper.events.Event;
import com.codigohasta.addon.utils.slimefunhelper.events.Listener;


import com.codigohasta.addon.utils.slimefunhelper.hacks.MovTasks;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.BaseModule;
import com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePath;
import com.codigohasta.addon.modules.slimefunhelper.ElytraExtra;
import com.codigohasta.addon.modules.slimefunhelper.FloatingUtils;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.entity.LegalMovementManager;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.tasks.TimerExecutor;
import com.codigohasta.addon.utils.slimefunhelper.hooks.BaritoneFixHooks;
import com.codigohasta.addon.utils.slimefunhelper.hooks.impl.baritone.BaritoneFuture;
import com.codigohasta.addon.utils.slimefunhelper.hooks.impl.baritone.BaritoneLanding;
import com.codigohasta.addon.utils.slimefunhelper.managers.Configs;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.DoubleRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.FlagRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.config.KeyBindRef;
import com.codigohasta.addon.utils.slimefunhelper.managers.input.MultiKeyBind;
import com.codigohasta.addon.utils.slimefunhelper.utils.*;
import com.codigohasta.addon.utils.slimefunhelper.utils.config.ValueAccessor;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;

public class BaritoneFix extends BaseModule implements LegalMovementManager.MovementModifier {
    public static BaritoneFix INSTANCE;

    static LegalMovementManager.DelegateMovementModifier instance;

    public BaritoneFix() {
        super("BaritoneFix");
        INSTANCE = this;
        bindFlag(enable);
        if (instance == null) {
            instance = new LegalMovementManager.DelegateMovementModifier(this::cast);
            MovTasks.PLAYER_PIPELINE_0.addMovementModifierFactory(() -> instance);
        }
        instance.setDelegate(this::cast);
    }

    public final ModulePath fix = makePath(Configs.SURVIVAL_CONFIG, "baritone.fix");

    public final FlagRef enable = flagBuilder(fix.addEnable()).build();
    public final com.codigohasta.addon.utils.slimefunhelper.managers.config.NBTRef<com.codigohasta.addon.utils.slimefunhelper.hooks.impl.baritone.BaritoneSeedMemory> seedCache =
        builder(fix.add("seed-cache"), com.codigohasta.addon.utils.slimefunhelper.hooks.impl.baritone.BaritoneSeedMemory.class)
            .defaultValue(new com.codigohasta.addon.utils.slimefunhelper.hooks.impl.baritone.BaritoneSeedMemory()).build();

    public final FlagRef enableDimensionFix =
            flagBuilder(fix.add("dimension-fix")).build();

    public final FlagRef enableSeedAutoImport =
            flagBuilder(fix.add("auto-import-seed")).build();

    public final FlagRef enableEmergencyLandingFix =
            flagBuilder(fix.add("emergency-landing-fix")).build();

    public final FlagRef disableInventoryCheck =
            flagBuilder(fix.add("disable-inventory-check")).build();

    public final FlagRef enableInventoryFireworks =
            flagBuilder(fix.add("enable-inventory-fireworks")).build();

    public final FlagRef enableGhostHandFireworks =
            flagBuilder(fix.add("enable-firework-swap")).build();

    public final FlagRef enableBaritoneCommandProtect =
            flagBuilder(fix.add("enable-baritone-command-protect")).build();

    public final FlagRef changeLandingToFreeze =
            flagBuilder(fix.add("change-landing-to-elytra-flight")).build();

    public final FlagRef autoJumpFix = flagBuilder(fix.add("auto-jump-fix")).build();

    public final FlagRef emergencyFixToLog =
            flagBuilder(fix.add("change-landing-to-log")).build();

    public final FlagRef pauseElytraProcess =
            flagBuilder(fix.add("baritone-conditional-pause")).build();

    public final KeyBindRef pauseKey = hotkey(fix.add("baritone-pause-hotkey"))
            .defaultValue(new MultiKeyBind())
            .build();

    public final FlagRef fixSimulateError =
            flagBuilder(fix.add("fix-baritone-simulate-error")).build();

    public final FlagRef freezeWhenFailCalculate =
            flagBuilder(fix.add("fix-when-fail-calculate")).build();

    public final FlagRef baritoneExperimental1 =
            flagBuilder(fix.add("baritone-experiment-1")).build();

    public final DoubleRef baritoneExperimentHeight = doubleBuilder(fix.add("baritone-experiment-height-1"))
            .defaultValue(36.0D)
            .build();

    public final FlagRef baritoneExperimental2 =
            flagBuilder(fix.add("baritone-experiment-2")).build();

    public final DoubleRef exp2Min =
            doubleBuilder(fix.add("exp-2-min-height")).defaultValue(38.0D).build();

    public final DoubleRef exp2Max =
            doubleBuilder(fix.add("exp-2-max-height")).defaultValue(42.0D).build();

    @Override
    public void registerAll() {
        super.registerAll();
        registerListener(com.codigohasta.addon.utils.slimefunhelper.chatcraft.SfChatCraftEvents.CHAT_SEND, this::onChat);
        registerListener(com.codigohasta.addon.utils.slimefunhelper.chatcraft.SfChatCraftEvents.CHAT_SEND, this::onChatCommand);
        registerListener(BaritoneFixHooks.getMoveRotEvent(), this::onBaritoneMoveRot);
        registerListener(BaritoneFixHooks.getLandingEvent(), this::onBaritoneComplete);
    }

    public ValueAccessor<Integer> durabilitySetting;
    public ValueAccessor<Integer> fireworkSetting;
    public ValueAccessor<String> commandPrefix;

    private void initializeBaritoneSettings() {
        if (durabilitySetting == null || fireworkSetting == null || commandPrefix == null) {
            durabilitySetting = BaritoneFixHooks.getInstance().<Integer>getSetting("elytraMinimumDurability");
            fireworkSetting = BaritoneFixHooks.getInstance().<Integer>getSetting("elytraMinFireworksBeforeLanding");
            commandPrefix = BaritoneFixHooks.getInstance().getSetting("prefix");
        }
    }

    private boolean canGlideEquipment() {
        ElytraExtra extra = ElytraExtra.INSTANCE;
        ItemStack stack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (extra.isCurrentArmorGliding()) {
            return true;
        } else if (extra.enableUnbreakableElytra.get()) {
            return true;
        }
        return stack.getItem() == Items.ELYTRA
                && stack.getMaxDamage() - stack.getDamage() >= durabilitySetting.getValue();
    }

    private boolean hasEnoughFirework() {
        int fireworkAmount = fireworkSetting.getValue();
        ElytraExtra extra = ElytraExtra.INSTANCE;
        return InventoryUtils.computePlayerInventory(
                        stack -> extra.canBeUsedAsFireworks(stack) ? (double) stack.getCount() : null, false)
                >= fireworkAmount;
    }

    public boolean checkCanContinueFlyingCustom() {
        if (!BaritoneFixHooks.getInstance().isEnabled()) return false;
        initializeBaritoneSettings();
        return canGlideEquipment() && hasEnoughFirework();
    }

    public void onChat(Event<String> chatEvent) {
        if (enableBaritoneCommandProtect.get() && chatEvent.context().startsWith("#")) {
            if (!BaritoneFixHooks.getInstance().isEnabled()) {
                logI18N("message.module.baritone-fix.command-without-baritone");
                chatEvent.cancel();
            } else {
                initializeBaritoneSettings();
                if (!chatEvent.context().startsWith(commandPrefix.getValue())) {
                    logI18N("message.module.baritone-fix.command-prefix-mismatch", commandPrefix.getValue());
                    chatEvent.cancel();
                }
            }
        }
    }

    public void onChatCommand(Event<String> eventCommandSay) {
        if (BaritoneFixHooks.getInstance().isEnabled() && eventCommandSay.context.startsWith("/")) {
            initializeBaritoneSettings();
            String prefix = commandPrefix.getValue();
            if (eventCommandSay.context.startsWith(prefix)) {
                if (BaritoneFixHooks.getInstance().handleCommand(eventCommandSay.context)) {
                    eventCommandSay.cancel();
                }
            }
        }
    }

    private int runtimeGeneration;

    public boolean handleLog(String situation) {
        if (!isActive()) return false;
        if (this.emergencyFixToLog.getValue()) {
            Debug.info("Disconnect because of Emergency situation:", situation);
            int ticket = runtimeGeneration;
            var owner = mc.player;
            com.codigohasta.addon.utils.slimefunhelper.managers.Tasks.scheduleDelayed(() -> {
                if (ticket == runtimeGeneration && isActive() && mc.player == owner && mc.getNetworkHandler() != null)
                    mc.getNetworkHandler().getConnection().disconnect(net.minecraft.text.Text.translatable("multiplayer.status.quitting"));
            }, 0);
            return true;
        }
        return false;
    }

    public boolean handleFreeze(String situation) {
        if (!isActive()) return false;
        if (this.changeLandingToFreeze.get()) {
            if (!MovTasks.getElytraFlight().enable.get()) {
                logI18N("message.module.baritone-fix.landing-cancelled", situation);
                FloatingUtils.INSTANCE.setGrimFloatingTick(true);
                MovTasks.getElytraFlight().enable.set(true);
            }
            return true;
        }
        return false;
    }

    TimerExecutor lastAutoJumpExecutor = new TimerExecutor();

    public boolean handleAutoJump() {
        if (!isActive() || checkNull()) return false;
        if (this.autoJumpFix.get() && !mc.player.isGliding()) {
            if (lastAutoJumpExecutor.run(20)) {
                logI18N("message.module.baritone-fix.autojump-takeoff");
                ElytraExtra.INSTANCE.autoTakeoff();
            }
            return true;
        }
        return false;
    }

    public boolean shouldPauseBaritoneElytra() {
        if (!isActive()) return false;
        if (pauseElytraProcess.get()) {
            if (FloatingUtils.INSTANCE.enableGrim.get()) {
                return true;
            }
            if (MovTasks.getElytraFlight().enable.get()) {
                return true;
            }
            if (pauseKey.setting.get().isSet() && pauseKey.setting.get().isPressed()
                    && (pauseKey.modifier == null || !pauseKey.modifier.get().isSet() || pauseKey.modifier.get().isPressed())) {
                return true;
            }
        }
        return false;
    }

    /** One flight controller owns motion: respect an explicit manual-flight pause first. */
    public boolean shouldYieldFlightControl() {
        return isActive() && !checkNull() && disableInventoryCheck.get()
                && !shouldPauseBaritoneElytra()
                && BaritoneFixHooks.getInstance().isBaritoneElytraProcessing();
    }

    public boolean shouldUseCustomFireworks() {
        return enableGhostHandFireworks.get()
                || (disableInventoryCheck.get() && ElytraExtra.INSTANCE.isCurrentArmorGliding());
    }

    @Override
    public void applyPreTickModify(Event<LegalMovementManager> movementManagerEvent) {
        if (!lastAutoJumpExecutor.canRun(20) && mc.player.isGliding()) {
            Box blockCheckBox = mc.player.getBoundingBox().stretch(0, -1, 0).expand(2, 0, 2);
            Box checkHeadBox = mc.player.getBoundingBox().stretch(0, 1, 0);
            if (CollisionUtil.isBoxCollided(mc.world, mc.player, blockCheckBox)
                    && !CollisionUtil.isBoxCollided(mc.world, mc.player, checkHeadBox)) {
                BaritoneFixHooks.getInstance().updateBaritoneLookTarget(-89.0F, mc.player.getYaw());
            }
        }
    }

    public void onBaritoneMoveRot(Event<Vec2f> vec2fEvent) {}

    @Override
    public boolean postModify(Event<LegalMovementManager> movementManagerEvent, boolean enabledThisTick) {
        if (!isActive()) return true;
        if (this.baritoneExperimental2.get()
                && mc.player.isGliding()
                && BaritoneFixHooks.getInstance().isBaritoneElytraProcessing()
                && ElytraExtra.INSTANCE.armorFly.get()) {
            boolean usingArmorFly = ElytraExtra.INSTANCE.isCurrentArmorGliding();
            if (usingArmorFly && mc.player.getY() < exp2Min.get()) {
                ElytraExtra.INSTANCE.endArmorFlyTransaction(true);
            } else if (!usingArmorFly && mc.player.getY() > exp2Max.get()) {
                ElytraExtra.INSTANCE.startArmorFlyTransaction(-1);
            }
        }
        return true;
    }

    public void onBaritoneComplete(Event<BaritoneFuture> event) {
        if (event.context.getOnCompleteFutures().isEmpty()) {
            BaritoneLanding landingType = event.getArgs(0);
            switch (landingType) {
                case EMERGENCY -> {
                    if (handleLog("Emergency Landing")) {
                        event.cancel();
                        return;
                    }
                    if (handleFreeze("Emergency Landing")) {
                        event.cancel();
                        return;
                    }
                }
                case PATH_COMPLETE -> {
                    if (handleLog("Path Complete")) {
                        event.cancel();
                        return;
                    }
                    if (handleFreeze("Path Complete")) {
                        event.cancel();
                        return;
                    }
                }
            }
        }
    }

    @Override public void onEnableModule() { BaritoneFixHooks.getInstance().initialize(); }
    public void resetRuntimeState() {
        runtimeGeneration++;
        lastAutoJumpExecutor = new TimerExecutor();
        durabilitySetting = null;
        fireworkSetting = null;
        commandPrefix = null;
    }
    @Override public void onDisableModule() { resetRuntimeState(); }
    public static boolean active() { return INSTANCE != null && INSTANCE.isActive() && !INSTANCE.checkNull(); }

    @Override public meteordevelopment.meteorclient.gui.widgets.WWidget getWidget(meteordevelopment.meteorclient.gui.GuiTheme theme) {
        var list = theme.verticalList();
        var hooks = BaritoneFixHooks.getInstance();
        list.add(theme.label(com.codigohasta.addon.utils.slimefunhelper.SfTranslations.text(hooks.isBaritoneAPISupported()
            ? "ui.baritone-fix.api-supported" : "ui.baritone-fix.api-unsupported", "Baritone API")));
        list.add(theme.label(com.codigohasta.addon.utils.slimefunhelper.SfTranslations.text(hooks.isBaritoneVersionSupported()
            ? "ui.baritone-fix.version-supported" : "ui.baritone-fix.version-unsupported", "Baritone")));
        var row = list.add(theme.horizontalList()).widget();
        var seed = row.add(theme.textBox("", com.codigohasta.addon.utils.slimefunhelper.SfTranslations.text("ui.baritone-fix.seed", "世界种子"))).expandX().widget();
        var save = row.add(theme.button(com.codigohasta.addon.utils.slimefunhelper.SfTranslations.text("ui.baritone-fix.save-seed", "保存当前维度种子"))).widget();
        save.action = () -> {
            try {
                long value = Long.parseLong(seed.get());
                var memory = seedCache.get();
                memory.setWorldSeed(value);
                seedCache.set(memory);
                info(com.codigohasta.addon.utils.slimefunhelper.SfTranslations.text("ui.baritone-fix.seed-saved", "已保存维度种子: %s"), com.codigohasta.addon.utils.slimefunhelper.hooks.impl.baritone.BaritoneSeedMemory.worldName());
            } catch (NumberFormatException error) { error(com.codigohasta.addon.utils.slimefunhelper.SfTranslations.text("ui.baritone-fix.invalid-seed", "请输入有效的 64 位整数种子")); }
        };
        return list;
    }
}
