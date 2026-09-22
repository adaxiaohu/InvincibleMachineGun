package com.codigohasta.addon.utils.slimefunhelper.hooks;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import com.codigohasta.addon.utils.slimefunhelper.hacks.utils.move.goal.IPathGoal;
public final class BaritoneHooks {
    private static final BaritoneHooks INSTANCE=new BaritoneHooks();
    private final boolean apiSupported = hasApi(BaritoneHooks.class.getClassLoader());
    public static BaritoneHooks getInstance(){return INSTANCE;}
    private static boolean hasApi(ClassLoader loader) {
        // Meteor packages the same API under baritone-meteor, not baritone.
        // Like the source integration, probe the API without starting its provider.
        try {
            Class.forName("baritone.api.BaritoneAPI", false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return false;
        }
    }
    public boolean isBaritoneAPISupported(){return apiSupported;}
    public boolean isBaritoneElytraProcessing(){return isBaritoneAPISupported()&&BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().isActive();}
    public boolean isBaritoneGoalPathingActive(){return isBaritoneAPISupported()&&BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().isActive();}
    public void cancelBaritone(){if(isBaritoneAPISupported())BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();}
    public void setBaritoneCurrentGoal(IPathGoal goal){if(isBaritoneAPISupported())BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new baritone.api.pathing.goals.Goal(){public boolean isInGoal(int x,int y,int z){return goal.isInGoal(new net.minecraft.util.math.Vec3d(x+0.5,y,z+0.5));}public double heuristic(int x,int y,int z){return new GoalBlock(net.minecraft.util.math.BlockPos.ofFloored(goal.sample())).heuristic(x,y,z);}});}
}
