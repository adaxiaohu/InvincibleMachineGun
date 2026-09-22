package com.codigohasta.addon.utils.slimefunhelper.hooks;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.event.events.ChatEvent;
import baritone.api.event.events.RotationMoveEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.utils.Rotation;
import com.codigohasta.addon.utils.slimefunhelper.events.*;
import com.codigohasta.addon.utils.slimefunhelper.hooks.impl.baritone.*;
import com.codigohasta.addon.utils.slimefunhelper.utils.config.ValueAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec2f;
import java.util.*;

/** Independent copy of the Baritone integration used by the source BaritoneFix. */
public final class BaritoneFixHooks {
    private static final BaritoneFixHooks INSTANCE = new BaritoneFixHooks();
    private static final Channel<Vec2f> MOVE_ROT = new Channel<>();
    private static final Channel<BaritoneFuture> LANDING = new Channel<>();
    private final Map<String, ValueAccessor<?>> settings = new LinkedHashMap<>();
    private boolean initialized;
    public static BaritoneFixHooks getInstance() { return INSTANCE; }
    public static Channel<Vec2f> getMoveRotEvent() { return MOVE_ROT; }
    public static Channel<BaritoneFuture> getLandingEvent() { return LANDING; }
    public boolean isEnabled() { return BaritoneHooks.getInstance().isBaritoneAPISupported(); }
    public boolean isBaritoneAPISupported() { return isEnabled(); }
    public boolean isBaritoneVersionSupported() {
        if (!isEnabled()) return false;
        try { Class.forName("baritone.process.elytra.ElytraBehavior", false, getClass().getClassLoader()); return true; }
        catch (ClassNotFoundException | LinkageError error) { return false; }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void initialize() {
        if (initialized || !isEnabled()) return;
        for (Settings.Setting setting : BaritoneAPI.getSettings().allSettings) {
            settings.put(setting.getName(), ValueAccessor.of(() -> setting.value, value -> setting.value = value));
        }
        BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override public void onPlayerRotationMove(RotationMoveEvent source) {
                if (source.getType() != RotationMoveEvent.Type.MOTION_UPDATE) return;
                Event<Vec2f> event = new Event<>(new Vec2f(source.getPitch(), source.getYaw()), false, true);
                MOVE_ROT.dispatch(event);
                source.setPitch(event.context.x);
                source.setYaw(event.context.y);
            }
        });
        initialized = true;
    }
    @SuppressWarnings("unchecked") public <T> ValueAccessor<T> getSetting(String name) {
        initialize();
        return (ValueAccessor<T>) settings.get(name);
    }
    public boolean isBaritoneElytraProcessing() { return BaritoneHooks.getInstance().isBaritoneElytraProcessing(); }
    public void updateBaritoneLookTarget(float pitch, float yaw) {
        if (isEnabled()) BaritoneAPI.getProvider().getPrimaryBaritone().getLookBehavior().updateTarget(new Rotation(yaw, pitch), false);
    }
    public boolean handleCommand(String command) {
        if (!isEnabled()) return false;
        String prefix = BaritoneAPI.getSettings().prefix.value;
        ChatEvent event = new ChatEvent(command.startsWith(prefix) ? command : prefix + command);
        var baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(MinecraftClient.getInstance().player);
        if (baritone == null) return false;
        baritone.getGameEventHandler().onSendChatMessage(event);
        return event.isCancelled();
    }
}
