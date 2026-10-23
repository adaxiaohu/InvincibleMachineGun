package com.codigohasta.addon.modules;

import com.codigohasta.addon.AddonTemplate;
import com.codigohasta.addon.utils.gcore.GcoreBlockUtil;
import com.codigohasta.addon.utils.gcore.GcoreChatUtils;
import com.codigohasta.addon.utils.gcore.GcoreServerEquivMove;
import com.codigohasta.addon.utils.gcore.GcoreTpUtil;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

public class GSuperVClip extends Module {

    private static final String MODULE_NAME = "GSuperVClip";

    private static final double VOID_FLOOR_Y = -128.0;
    private static final double FALL_DEBT_DELTA = 9E-8;

    private final SettingGroup sgGeneral = this.settings.createGroup("通用");
    private final Setting<Double> moveDistance;
    private final Setting<Integer> clip;
    private final Setting<Double> protectY;
    private final Setting<Boolean> sneakDownClip;
    private final Setting<Boolean> voidProtect;

    public GSuperVClip() {
        super(AddonTemplate.CATEGORY, "超级VClip", "一键把自己垂直瞬移一大段距离。开启后立刻执行一次并自动关闭。");

        this.moveDistance = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("单包位移")
            .description("每个移动包走多远（格）。")
            .defaultValue(10.0)
            .range(0.1, 99999999)
            .sliderRange(0.1, 10.0)
            .build());

        this.clip = this.sgGeneral.add(new IntSetting.Builder()
            .name("跳跃步数")
            .description("位移 = 本值 × 单包位移。默认 8 × 10 = 80 格。")
            .defaultValue(8)
            .range(1, 99999999)
            .sliderRange(1, 20)
            .build());

        this.protectY = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("保护高度")
            .description("位移后的 Y 不低于这个值，避免掉进虚空。")
            .defaultValue(-63.0)
            .range(-128.0, 0.0)
            .sliderRange(-128.0, 0.0)
            .build());

        this.sneakDownClip = this.sgGeneral.add(new BoolSetting.Builder()
            .name("潜行时向下穿")
            .description("开启后，潜行状态下位移方向朝下（否则朝上）。")
            .defaultValue(true)
            .build());

        this.voidProtect = this.sgGeneral.add(new BoolSetting.Builder()
            .name("虚空保护")
            .description("落到 -128 以下，强行夹到 -128。")
            .defaultValue(true)
            .build());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }
        try (var ignored = GcoreServerEquivMove.enter(MODULE_NAME)) {
            this.doVClip();
        }
        this.disable();
    }
    private void doVClip() {
        var player = this.mc.player;
        if (player == null) {
            return;
        }

        double clipY = this.clip.get() * this.moveDistance.get();

        Vec3d finalVec = player.getEntityPos();
        int offsetY = 0;
        int limit = (int) clipY;

        if (0 <= limit) {
            while (true) {
                boolean down = this.sneakDownClip.get() && player.isSneaking();

                Vec3d vec = new Vec3d(
                    finalVec.x,
                    down ? finalVec.y - (clipY - offsetY)
                         : finalVec.y + clipY - offsetY,
                    finalVec.z);

                
                if (vec.y > this.protectY.get() && GcoreBlockUtil.checkNoPosCollie(vec)) {
                    finalVec = vec;
                    break;
                }

                if (offsetY == limit) {
                    break;
                }
                offsetY++;
            }
        }
        if (this.voidProtect.get() && finalVec.y <= VOID_FLOOR_Y) {
            finalVec = new Vec3d(finalVec.x, VOID_FLOOR_Y, finalVec.z);
        }

        Vec3d fromVec = player.getEntityPos();
        GcoreTpUtil.doTp(fromVec, finalVec, this.moveDistance.get(), false);
        GcoreChatUtils.chat(String.format("Try VClip %.1f m ...", finalVec.y - player.getY()));
        player.setPos(finalVec.x, finalVec.y, finalVec.z);
        GcoreTpUtil.sendMovePacket(finalVec.x, finalVec.y + FALL_DEBT_DELTA, finalVec.z, false);
    }
}
