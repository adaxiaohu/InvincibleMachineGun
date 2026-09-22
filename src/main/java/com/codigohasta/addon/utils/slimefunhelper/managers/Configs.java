package com.codigohasta.addon.utils.slimefunhelper.managers;

import com.codigohasta.addon.utils.slimefunhelper.managers.config.ConfigEnum;
import java.util.function.Predicate;
public final class Configs {
 public static final String SURVIVAL_CONFIG="survival";
    public static final String MOV_CONFIG="movement", COMBAT_CONFIG="combat", INV_CONFIG="inventory";
    public static final Predicate<Integer> INT_POSITIVE=v->v>0, INT_NONNEGATIVE=v->v>=0;
    public static Predicate<Double> doubleRange(double min,double max){return v->v>=min&&v<=max;}
    public enum BypassMode implements ConfigEnum {NO_BYPASS,BYPASS_GRIM,BYPASS_VULCAN,BYPASS_MATRIX; public boolean hasAc(){return this!=NO_BYPASS;} public String getConfigEnumType(){return "bypass";}}
    public enum SetBackTriggerType implements ConfigEnum {SIMULATION,CRASH_PACKETS; public String getConfigEnumType(){return "setback";}}

    public enum LegalTargetingMode implements ConfigEnum {
        NONE,
        DELAY_MOVEMENT,
        // PRE_MOVEMENT,
        // USEITEM_PACKET,
        LEGACY_SLIENT_ROT;

        public boolean isMovement() {
            return this == LegalTargetingMode.DELAY_MOVEMENT; // || this == LegalTargetingMode.PRE_MOVEMENT;
        }

        public boolean isLegal() {
            return this != NONE;
        }

        public boolean isLegacy() {
            return this == LegalTargetingMode.LEGACY_SLIENT_ROT;
        }

        public static LegalTargetingMode getFromPreset(com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePreset preset) {
            return switch (preset) {
                case HACKING, VANILLA -> NONE;
                case AC_GRIM_LEGACY -> LEGACY_SLIENT_ROT;
                default -> DELAY_MOVEMENT;
            };
        }

        @Override
        public String getConfigEnumType() {
            return "legal_targeting_mode";
        }
    }
public static final String MINE_CONFIG="mining", INTERACT_CONFIG="interaction", EXTRA_CONFIG="extra";
    public enum LegalInteractMode implements ConfigEnum {
        NONE,
        MOVEMENT_POST,
        DELAY_MOVEMENT,
        USEITEM_PACKET,
        LEGACY_SLIENT_ROT;
        ;

        public static LegalInteractMode getFromPreset(com.codigohasta.addon.utils.slimefunhelper.hacks.api.ModulePreset preset) {
            return switch (preset) {
                case HACKING, VANILLA -> Configs.LegalInteractMode.NONE;
                case AC_GRIM_LEGACY -> Configs.LegalInteractMode.LEGACY_SLIENT_ROT;
                default -> Configs.LegalInteractMode.DELAY_MOVEMENT;
            };
        }

        @Override
        public String getConfigEnumType() {
            return "legal_interact_mode";
        }

        public boolean isLegal() {
            return this != NONE;
        }

        public boolean canMultiRotPlace() {
            return this == NONE || this == LEGACY_SLIENT_ROT;
        }
    }

}
