package com.codigohasta.addon.modules;

import net.minecraft.util.math.Vec3d;

import com.codigohasta.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public class FlightAntiKick extends Module {
    public enum Mode {
        物理下沉,    
        数据包下潜,  
        垂直重力    
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRandom = settings.createGroup("随机");

    
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("模式")
        .description("123")
        .defaultValue(Mode.物理下沉)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("基础频率")
        .description("123")
        .defaultValue(25)
        .min(5)
        .build()
    );

    private final Setting<Double> dipDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("下沉幅度")
        .description("向下位移。")
        .defaultValue(0.04)
        .min(0.01)
        .sliderMax(0.1)
        .build()
    );

    private final Setting<Boolean> onlyIfActive = sgGeneral.add(new BoolSetting.Builder()
        .name("联动飞行模块")
        .description("仅在 Meteor Flight 开启时生效。")
        .defaultValue(true)
        .build()
    );

    
    private final Setting<Boolean> useRandom = sgRandom.add(new BoolSetting.Builder().name("随机延迟").defaultValue(true).build());
    private final Setting<Integer> randomRange = sgRandom.add(new IntSetting.Builder().name("随机范围").defaultValue(5).min(1).visible(useRandom::get).build());

    private final Random random = new Random();
    private int timer = 0;
    private int currentMaxDelay;
    private static final double ANTI_KICK_OFFSET = 0.032;  
    private int dipPhase = 0;               
    private double liftY = 0;               
    private double preservedLastMotion = 0;  

    public FlightAntiKick() {
        super(AddonTemplate.CATEGORY, "flight-anti-kick", "飞行反踢。");
    }

    @Override
    public void onActivate() {
        timer = 0;
        resetDelay();
        dipPhase = 0;
        liftY = 0;
        preservedLastMotion = 0;
    }

    private void resetDelay() {
        currentMaxDelay = useRandom.get() ? delay.get() + (random.nextInt(randomRange.get() * 2) - randomRange.get()) : delay.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (onlyIfActive.get()) {
            Module flight = Modules.get().get("flight");
            if (flight == null || !flight.isActive()) {
                dipPhase = 0;
                return;
            }
        }

        if (mc.player.isOnGround()) {
            dipPhase = 0;
            return;
        }

        if (mode.get() == Mode.垂直重力) {
            Vec3d v = mc.player.getVelocity();
            if (v.y >= 0) { 
                mc.player.setVelocity(v.x, -0.005, v.z);
            }
            return;
        }
        if (mode.get() == Mode.物理下沉 && dipPhase > 0) {
            stepDipSequence();
            return;
        }

        timer++;
        if (timer >= currentMaxDelay) {
            executeKickAction();
            timer = 0;
            resetDelay();
        }
    }

    private void stepDipSequence() {
        Vec3d v = mc.player.getVelocity();

        if (dipPhase == 1) {
            dipPhase = 2;
            liftY += ANTI_KICK_OFFSET - 0.008;
            mc.player.setVelocity(v.x, -ANTI_KICK_OFFSET, v.z);
        } else {
            mc.player.setVelocity(v.x, liftY + preservedLastMotion, v.z);
            dipPhase = 0;
            liftY = 0;
            preservedLastMotion = 0;
        }
    }

    private void executeKickAction() {
        double dip = dipDistance.get();
        
        if (mode.get() == Mode.物理下沉) {
            preservedLastMotion = mc.player.getVelocity().y;
            liftY = ANTI_KICK_OFFSET - 0.008;
            dipPhase = 1;
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(v.x, -ANTI_KICK_OFFSET, v.z);
        } 
        else if (mode.get() == Mode.数据包下潜) {
            
            
            
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            
            
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y - dip, z, false, mc.player.horizontalCollision));
        }
    }

    @Override
    public String getInfoString() {
        return mode.get().toString();
    }
}
