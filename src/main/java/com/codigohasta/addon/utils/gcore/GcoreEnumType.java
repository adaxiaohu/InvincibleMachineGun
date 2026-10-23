package com.codigohasta.addon.utils.gcore;

public final class GcoreEnumType {

    private GcoreEnumType() {
    }

    /** 方块放置的版本行为。默认 {@code MC1_14}。 */
    public enum BlockPlaceVersion {
        MC1_14("1.14"),
        MC1_12("1.12");

        private final String choiceName;

        BlockPlaceVersion(String choiceName) {
            this.choiceName = choiceName;
        }

        public String getChoiceName() {
            return this.choiceName;
        }

        @Override
        public String toString() {
            return this.choiceName;
        }
    }

    public enum CrystalPlaceVersion {
        MC1_12("MC1_12"),
        MC1_13("MC1_13"),
        MC1_14("MC1_14");

        private final String choiceName;

        CrystalPlaceVersion(String choiceName) {
            this.choiceName = choiceName;
        }

        public String getChoiceName() {
            return this.choiceName;
        }

        @Override
        public String toString() {
            return this.choiceName;
        }
    }

    public enum ItemMode {
        None("不切"),
        HotBar("快捷栏"),
        Inv("背包"),
        SwapInv("背包交换");

        private final String choiceName;

        ItemMode(String choiceName) {
            this.choiceName = choiceName;
        }

        public String getChoiceName() {
            return this.choiceName;
        }

        public GcoreIntRange getFindIntRange(int selectedSlot) {
            return switch (this) {
                case HotBar -> new GcoreIntRange(0, 8);
                case Inv -> new GcoreIntRange(0, 35);
                case SwapInv -> new GcoreIntRange(0, 35);
                default -> new GcoreIntRange(selectedSlot, selectedSlot);
            };
        }

        @Override
        public String toString() {
            return this.choiceName;
        }
    }
}
