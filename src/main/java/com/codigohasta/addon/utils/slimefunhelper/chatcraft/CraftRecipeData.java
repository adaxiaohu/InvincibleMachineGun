package com.codigohasta.addon.utils.slimefunhelper.chatcraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.registry.entry.RegistryEntry;
import java.util.List;
import java.util.stream.Stream;
/** Reachable RecipeTasks utilities copied independently from SlimefunHelper. */
public final class CraftRecipeData {
    private static final MinecraftClient mc=MinecraftClient.getInstance();
    public static List<Ingredient> getIngredients(NetworkRecipeId id){
        var entry=mc.player.getRecipeBook().recipes.get(id);
        return entry==null?List.of():entry.craftingRequirements().orElse(List.of());
    }
    @SuppressWarnings("deprecation") // Keep Fabric custom ingredient dispatch without requiring Fabric API.
    public static Stream<ItemStack> streamIngredientOptions(Ingredient ingredient){
        // The vanilla projection also dispatches to Fabric custom ingredients when that API is installed.
        // Avoid a hard dependency on an interface injected by an optional Fabric recipe module.
        return ingredient.getMatchingItems().map(RegistryEntry::value).map(ItemStack::new);
    }
    public static ItemStack getRecipeResult(NetworkRecipeId id){
        if(mc.player==null||mc.world==null)return ItemStack.EMPTY;
        var entry=mc.player.getRecipeBook().recipes.get(id);
        if(entry==null)return ItemStack.EMPTY;
        var stacks=entry.getStacks(SlotDisplayContexts.createParameters(mc.world));
        return stacks.isEmpty()?ItemStack.EMPTY:stacks.getFirst();
    }
}
