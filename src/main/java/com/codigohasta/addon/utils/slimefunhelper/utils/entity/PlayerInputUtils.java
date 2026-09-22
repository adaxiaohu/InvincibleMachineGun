package com.codigohasta.addon.utils.slimefunhelper.utils.entity;

import com.codigohasta.addon.utils.slimefunhelper.hooks.ViaFabricPlusHooks;
import com.codigohasta.addon.utils.slimefunhelper.hooks.ViaProtocols;
import com.codigohasta.addon.utils.slimefunhelper.versioned.SupportVersion;
import net.minecraft.network.packet.PlayPackets;

import com.codigohasta.addon.utils.slimefunhelper.utils.EntityUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;

public class PlayerInputUtils {
    public static Input of(ClientPlayerEntity player) {
        return new Input(player.input.playerInput);
    }

    public static Input of(PlayerInput input) {
        return new Input(input);
    }

    public static Input of(GameOptions options) {
        return new Input(
                options.forwardKey.isPressed(),
                options.backKey.isPressed(),
                options.leftKey.isPressed(),
                options.rightKey.isPressed(),
                options.jumpKey.isPressed(),
                options.sneakKey.isPressed(),
                options.sprintKey.isPressed());
    }

    public static Input of(PlayerInputC2SPacket packet) {
        return of(packet.input());
    }

    public static Input tryCorrectMovementInput(Input input, float originalYaw, float currentYaw) {
        float diff = EntityUtils.getSafeYawDiff(originalYaw, currentYaw);
        int forwardSpeed;
        int sidewaySpeed;
        int movementForward = input.forwardSpeed();
        int movementSideways = input.sidewaysSpeed();
        if (diff < 22.5 && diff >= -22.5) {
            // do nothing
            return input;
        } else if (diff < 67.5 && diff >= 22.5) {
            // turn to
            forwardSpeed = (movementForward - movementSideways);
            sidewaySpeed = (movementForward + movementSideways);
        } else if (diff >= 67.5 && diff < 90.0F + 22.5F) {
            forwardSpeed = -movementSideways;
            sidewaySpeed = movementForward;
        } else if (diff >= 90.0F + 22.5F && diff < 90.0F + 67.5F) {
            forwardSpeed = (-movementForward - movementSideways);
            sidewaySpeed = (movementForward - movementSideways);
        } else if (diff >= 90.0F + 67.5F || diff < -90.0F - 67.5F) {
            forwardSpeed = -movementForward;
            sidewaySpeed = -movementSideways;
        } else if (diff >= -90.0F - 67.5F && diff < -90.0F - 22.5F) {
            forwardSpeed = (-movementForward + movementSideways);
            sidewaySpeed = (-movementForward - movementSideways);
        } else if (diff >= -90.0F - 22.5F && diff < -90.0F + 22.5F) {
            forwardSpeed = movementSideways;
            sidewaySpeed = -movementForward;
        } else if (diff >= -90.0F + 22.5F && diff < -22.5F) {
            forwardSpeed = (movementForward + movementSideways);
            sidewaySpeed = (-movementForward + movementSideways);
        } else {
            return input;
        }
        // sync values
        return new Input(
                forwardSpeed > 0,
                forwardSpeed < 0,
                sidewaySpeed > 0,
                sidewaySpeed < 0,
                input.jump(),
                input.sneak(),
                input.sprint());
    }

    public static final Input EMPTY = new Input(false, false, false, false, false, false, false);






    public static class Input implements Cloneable {

        public Input(boolean forward,boolean backward,boolean left,boolean right,boolean jump,boolean sneak,boolean sprint){this.forward=forward;this.backward=backward;this.left=left;this.right=right;this.jump=jump;this.sneak=sneak;this.sprint=sprint;}
        public boolean forward(){return forward;} public Input forward(boolean value){forward=value;return this;}
        public boolean backward(){return backward;} public Input backward(boolean value){backward=value;return this;}
        public boolean left(){return left;} public Input left(boolean value){left=value;return this;}
        public boolean right(){return right;} public Input right(boolean value){right=value;return this;}
        public boolean jump(){return jump;} public Input jump(boolean value){jump=value;return this;}
        public boolean sneak(){return sneak;} public Input sneak(boolean value){sneak=value;return this;}
        public boolean sprint(){return sprint;} public Input sprint(boolean value){sprint=value;return this;}
@Override public boolean equals(Object o){return o instanceof Input v && forward==v.forward && backward==v.backward && left==v.left && right==v.right && jump==v.jump && sneak==v.sneak && sprint==v.sprint;}
@Override public int hashCode(){return java.util.Objects.hash(forward,backward,left,right,jump,sneak,sprint);}
        boolean forward;
        boolean backward;
        boolean left;
        boolean right;
        boolean jump;
        boolean sneak;
        boolean sprint;

        public Input(PlayerInput input) {
            this(
                    input.forward(),
                    input.backward(),
                    input.left(),
                    input.right(),
                    input.jump(),
                    input.sneak(),
                    input.sprint());
        }

        public Input(boolean forward, boolean backward, boolean left, boolean right) {
            this(forward, backward, left, right, false, false, false);
        }

        public PlayerInput toPlayerInput() {
            return new PlayerInput(
                    this.forward, this.backward, this.left, this.right, this.jump, this.sneak, this.sprint);
        }

        public PlayerInputC2SPacket toPlayerInputPacket() {
            return new PlayerInputC2SPacket(toPlayerInput());
        }

        public Input sendPlayerInputPacket() {
            MinecraftClient.getInstance().getNetworkHandler().sendPacket(toPlayerInputPacket());
            return this;
        }

        public Input sendPlayerInputAsRiding() {
            if (ViaFabricPlusHooks.getInstance().isViaEnabled()
                    && ViaFabricPlusHooks.getInstance().getCurrentVersion().isLowerOrEqualTo(21, 1)) {
                // 1.21.1 ride packet
                ViaFabricPlusHooks.ViaPacketWrapper wrapper =
                        ViaFabricPlusHooks.getInstance().createViaPacket();
                wrapper.writePacketType(ViaProtocols.V1_20_3_TO_1_20_5, PlayPackets.PLAYER_INPUT);
                wrapper.write("FLOAT", sidewaysSpeed() * 0.98F);
                wrapper.write("FLOAT", forwardSpeed() * 0.98F);
                byte b = 0;
                if (this.jump()) {
                    b = (byte) (b | 1);
                }

                if (this.sneak()) {
                    b = (byte) (b | 2);
                }

                wrapper.write("BYTE", b);
                wrapper.scheduleSendToServer(ViaProtocols.V1_21_TO_1_21_2, true);
            } else {
                sendPlayerInputPacket();
            }
            return this;
        }


        public Input sendPlayerSneakUpdatePacket() {
            if (ViaFabricPlusHooks.getInstance().isViaEnabled()
                    && ViaFabricPlusHooks.getInstance().getCurrentVersion().isLowerOrEqualTo(21, 5)
                    && SupportVersion.CURRENT.isHigherOrEqualTo(21, 6)) {
                // send sneak packet
                ViaFabricPlusHooks.ViaPacketWrapper wrapper =
                        ViaFabricPlusHooks.getInstance().createViaPacket();
                wrapper.writePacketType(ViaProtocols.V1_21_4_TO_1_21_5, PlayPackets.PLAYER_COMMAND);
                wrapper.write("VAR_INT", MinecraftClient.getInstance().player.getId());
                wrapper.write("VAR_INT", this.sneak ? 0 : 1);
                wrapper.write("VAR_INT", 0);
                // todo why doesn't work
                wrapper.sendToServer(ViaProtocols.V1_21_5_TO_1_21_6, false);
            }
            sendPlayerInputPacket();
            return this;
        }


        public int forwardSpeed() {
            return this.forward == this.backward ? 0 : (this.forward ? 1 : -1);
        }

        public int sidewaysSpeed() {
            return this.left == this.right ? 0 : (this.left ? 1 : -1);
        }

        public int upwardSpeed() {
            return this.jump == this.sneak ? 0 : (this.jump ? 1 : -1);
        }

        public Input applyInput(ClientPlayerEntity input) {
            applyInput(input.input);
            return this;
        }

        protected Input applyInput(net.minecraft.client.input.Input input) {
            input.playerInput = toPlayerInput();
            return this;
        }

        public Input applyInput(GameOptions options) {
            options.forwardKey.setPressed(forward);
            options.backKey.setPressed(backward);
            options.leftKey.setPressed(left);
            options.rightKey.setPressed(right);
            options.jumpKey.setPressed(jump);
            options.sneakKey.setPressed(sneak);
            options.sprintKey.setPressed(sprint);
            return this;
        }

        public boolean hasMovement() {
            return forward || backward || left || right || jump;
        }

        public boolean hasWASDMovement() {
            return (forward != backward) || (left != right);
        }

        public boolean hasMovementControl() {
            return forward || backward || left || right || jump || sneak;
        }

        @Override
        public Input clone() {
            try {
                Input clone = (Input) super.clone();
                // TODO: copy mutable state here, so the clone can't change the internals of the original
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }

        @Override
        public String toString() {
            return "Input{" + "forward="
                    + forward + ", backward="
                    + backward + ", left="
                    + left + ", right="
                    + right + ", jump="
                    + jump + ", sneak="
                    + sneak + ", sprint="
                    + sprint + '}';
        }
    }
}
