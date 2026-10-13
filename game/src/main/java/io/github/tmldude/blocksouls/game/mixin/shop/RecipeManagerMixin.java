package io.github.tmldude.blocksouls.game.mixin.shop;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import io.github.tmldude.blocksouls.game.run.director.ShopGate;

/**
 * Floors runs: the crafting and smithing lookups (2×2 grid, crafting table, crafter, smithing table) skip
 * BlockSouls recipes, which craft at the shopkeeper's boards instead ({@link ShopGate}).
 */
@Mixin(RecipeManager.class)
abstract class RecipeManagerMixin {
	@Shadow
	@Final
	private RecipeMap recipes;

	@Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
			at = @At("HEAD"), cancellable = true)
	private <I extends RecipeInput, T extends Recipe<I>> void blocksouls$skipBoardRecipes(RecipeType<T> type, I input, Level level,
			CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
		if (ShopGate.filters(type)) {
			cir.setReturnValue(recipes.getRecipesFor(type, input, level).filter(recipe -> !ShopGate.blocks(type, recipe)).findFirst());
		}
	}

	@Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
			at = @At("HEAD"), cancellable = true)
	private <I extends RecipeInput, T extends Recipe<I>> void blocksouls$skipBoardHint(RecipeType<T> type, I input, Level level, @Nullable RecipeHolder<T> hint,
			CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
		if (hint != null && ShopGate.blocks(type, hint)) {
			cir.setReturnValue(((RecipeManager) (Object) this).getRecipeFor(type, input, level));
		}
	}
}
