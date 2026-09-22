package com.codigohasta.addon.utils.slimefunhelper;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.modules.slimefunhelper.ElytraExtra;
import com.codigohasta.addon.modules.slimefunhelper.ElytraFlight;
import com.codigohasta.addon.modules.slimefunhelper.MovExtra;
import com.codigohasta.addon.modules.slimefunhelper.PlayerStateManager;
import com.codigohasta.addon.modules.slimefunhelper.PrinterRewrite;
import com.codigohasta.addon.utils.slimefunhelper.accessors.access.PlayerMoveC2SPacketAccess;
import java.util.ArrayDeque;
import java.util.Deque;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;

/** Bounded, local-only flight/inventory diagnostics. Logs only on a correction or warning. */
public final class FlightPacketDiagnostics {
    private static final int HISTORY_LIMIT = 128;
    private static final Deque<String> recent = new ArrayDeque<>(HISTORY_LIMIT);
    private static net.minecraft.util.PlayerInput sentInput;
    private static Boolean sentSprint;
    private static long nextLog;
    private static long nextInventoryLog;
    private static long nextCombatLog;
    private static int lastConfirm = Integer.MIN_VALUE;
    private static boolean responseSent;

    private static boolean enabled() {
        return SlimefunRuntime.ready() && (flightEnabled()
            || (PrinterRewrite.INSTANCE != null && PrinterRewrite.INSTANCE.isActive()));
    }

    private static boolean flightEnabled() {
        return (ElytraExtra.INSTANCE != null && ElytraExtra.INSTANCE.isActive())
            || (ElytraFlight.INSTANCE != null && ElytraFlight.INSTANCE.isActive());
    }

    private static void remember(String entry) {
        if (recent.size() == HISTORY_LIMIT) recent.removeFirst();
        recent.addLast("ms=" + System.nanoTime() / 1_000_000L + " " + entry);
    }

    private static boolean isInventoryAlert(String text, String playerName) {
        return text.contains(playerName) && (text.contains("InventoryA")
            || text.contains("InventoryC") || text.contains("InventoryD")
            || text.contains("InventoryE") || text.contains("MultiActionsD"));
    }

    private static boolean isCombatAlert(String text, String playerName) {
        return text.contains(playerName) && (text.contains("Hitboxes") || text.contains("Reach")
            || text.contains("AntiKB") || text.contains("AntiExplosion") || text.contains("Simulation"));
    }

    public static synchronized void attackCheck(net.minecraft.entity.Entity target, net.minecraft.util.math.Vec3d feet,
                                               net.minecraft.util.math.Vec3d direction, double range, boolean valid) {
        if (!enabled()) return;
        remember("attack-check[id=" + target.getId() + ",type=" + net.minecraft.registry.Registries.ENTITY_TYPE.getId(target.getType())
            + ",box=" + target.getBoundingBox() + ",feet=" + feet + ",look=" + direction + ",range=" + range + ",allowed=" + valid + "]");
    }

    public static synchronized void sent(Packet<?> packet) {
        if (!enabled()) return;
        String entry;
        if (packet instanceof PlayerMoveC2SPacket move) {
            var cause = PlayerMoveC2SPacketAccess.of(move).getCause();
            if (cause == PlayerMoveC2SPacketAccess.Cause.SET_BACK) responseSent = true;
            entry = "move[pos=" + move.changesPosition() + ",rot=" + move.changesLook()
                + ",ground=" + move.isOnGround() + ",cause=" + cause
                + ",xyz=" + move.getX(Double.NaN) + "/" + move.getY(Double.NaN) + "/" + move.getZ(Double.NaN)
                + ",yaw=" + move.getYaw(Float.NaN) + ",pitch=" + move.getPitch(Float.NaN) + "]";
        } else if (packet instanceof TeleportConfirmC2SPacket confirm) {
            lastConfirm = confirm.getTeleportId();
            responseSent = false;
            entry = "confirm[" + lastConfirm + "]";
        } else if (packet instanceof CommonPongC2SPacket pong) entry = "pong[" + pong.getParameter() + "]";
        else if (packet instanceof ClientTickEndC2SPacket) entry = "end";
        else if (packet instanceof PlayerInputC2SPacket input) {
            sentInput = input.input();
            entry = "input[" + sentInput + "]";
        } else if (packet instanceof ClientCommandC2SPacket command) {
            if (command.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) sentSprint = true;
            else if (command.getMode() == ClientCommandC2SPacket.Mode.STOP_SPRINTING) sentSprint = false;
            entry = "command[" + command.getMode() + "]";
        }
        else if (packet instanceof ClickSlotC2SPacket click) {
            var player = MinecraftClient.getInstance().player;
            entry = "inventory-click[id="+click.syncId()+",rev="+click.revision()+",slot="+click.slot()+",button="+click.button()+",action="+click.actionType()+",changed="+click.modifiedStacks().keySet()+",sentInput="+sentInput+",sentSprint="+sentSprint+",usingItem="+player.isUsingItem()+",clientInput="+player.input.playerInput+"]";
        }
        else if (packet instanceof UpdateSelectedSlotC2SPacket selected) entry = "selected["+selected.getSelectedSlot()+"]";
        else if (packet instanceof PlayerInteractEntityC2SPacket) entry = "interact-entity";
        else if (packet instanceof PlayerInteractItemC2SPacket) entry = "use-item";
        else if (packet instanceof PlayerInteractBlockC2SPacket) entry = "use-block";
        else if (packet instanceof PlayerActionC2SPacket action) entry = "action[" + action.getAction() + "]";
        else if (packet instanceof CloseHandledScreenC2SPacket close) entry = "close-screen[" + close.getSyncId() + "]";
        else return;
        remember(entry);
    }

    public static synchronized void received(Packet<?> packet) {
        if(!enabled())return;
        String entry=null;
        if(packet instanceof net.minecraft.network.packet.s2c.play.InventoryS2CPacket inventory)
            entry="inventory-sync[id="+inventory.syncId()+",rev="+inventory.revision()+"]";
        else if(packet instanceof net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket slot)
            entry="slot-sync[id="+slot.getSyncId()+",rev="+slot.getRevision()+",slot="+slot.getSlot()+"]";
        else if(packet instanceof net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket velocity
            && velocity.getEntityId() == MinecraftClient.getInstance().player.getId())
            entry="server-velocity[value="+com.codigohasta.addon.utils.slimefunhelper.versioned.api.VPacket.getVelocity(velocity)
                +",applied="+MinecraftClient.getInstance().player.getVelocity()+",tracked="+PlayerStateManager.INSTANCE.lastKnownClientVelocity+"]";
        else if(packet instanceof net.minecraft.network.packet.s2c.play.ExplosionS2CPacket explosion)
            entry="server-explosion[value="+explosion.playerKnockback()+",applied="+MinecraftClient.getInstance().player.getVelocity()
                +",tracked="+PlayerStateManager.INSTANCE.lastKnownClientVelocity+"]";
        if(entry!=null) remember(entry);
        if(packet instanceof net.minecraft.network.packet.s2c.play.GameMessageS2CPacket message){
            String text=message.content().getString();
            var mc=MinecraftClient.getInstance();
            if(isCombatAlert(text, mc.player.getNameForScoreboard())&&System.nanoTime()>=nextCombatLog){
                nextCombatLog=System.nanoTime()+5_000_000_000L;
                AddonTemplate.LOG.info("[SF combat rejection] warning={}, pos={}, velocity={}, trackedVelocity={}, eyeHeight={}, gliding={}, active={}, recent={}",
                    text, mc.player.getEntityPos(), mc.player.getVelocity(), PlayerStateManager.INSTANCE.lastKnownClientVelocity,
                    mc.player.getEyeHeight(mc.player.getPose()), mc.player.isGliding(),
                    Modules.get().getAll().stream().filter(m -> m.isActive()).map(m -> m.name).toList(),recent);
            }
            if(isInventoryAlert(text, mc.player.getNameForScoreboard())&&System.nanoTime()>=nextInventoryLog){
                nextInventoryLog=System.nanoTime()+5_000_000_000L;
                var movement = MovExtra.INSTANCE;
                AddonTemplate.LOG.info("[SF inventory rejection] warning={}, protocol={}, handler={}, revision={}, selected={}, inputFix={}, sprintFix={}, clientInput={}, usingItem={}, trackedInput={}, trackedSprint={}, active={}, recent={}",
                    text,
                    com.codigohasta.addon.utils.slimefunhelper.hooks.ViaFabricPlusHooks.getInstance().getCurrentVersion(),
                    mc.player.currentScreenHandler.syncId,mc.player.currentScreenHandler.getRevision(),mc.player.getInventory().getSelectedSlot(),
                    movement.fuckGrimAC.get(), movement.fuckGrimACSprint.get(),
                    mc.player.input.playerInput, mc.player.isUsingItem(),
                    PlayerStateManager.INSTANCE.lastInput, PlayerStateManager.INSTANCE.lastSprint,
                    Modules.get().getAll().stream().filter(m -> m.isActive()).map(m -> m.name).toList(),recent);
            }
        }
    }

    public static synchronized void corrected(PlayerPositionLookS2CPacket packet) {
        if (!SlimefunRuntime.ready() || !flightEnabled() || System.nanoTime() < nextLog) return;
        nextLog = System.nanoTime() + 5_000_000_000L;
        var mc = MinecraftClient.getInstance();
        var flight = Modules.get().get(ElytraFlight.class);
        var active = Modules.get().getAll().stream().filter(m -> m.isActive()).map(m -> m.name).toList();
        AddonTemplate.LOG.info("[SF flight correction] id={}, confirm={}, responseObserved={}, grounded={}, gliding={}, mode={}, motion={}, active={}, recent={}",
            packet.teleportId(), lastConfirm, responseSent, mc.player.isOnGround(), mc.player.isGliding(),
            flight.motionMode.get(), flight.packetMotion.get(), active, recent);
    }

    public static synchronized void clear() {
        recent.clear();
        sentInput = null;
        sentSprint = null;
        nextLog = 0;
        nextInventoryLog = 0;
        nextCombatLog = 0;
        lastConfirm = Integer.MIN_VALUE;
        responseSent = false;
    }
}
