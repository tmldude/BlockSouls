package io.github.tmldude.blocksouls.game.run.director;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import io.github.tmldude.blocksouls.game.run.DirectorHooks;
import io.github.tmldude.blocksouls.game.run.RunSystem;

/**
 * Crafting off in floors runs (docs/decisions/v2-boards.md): while a floors run is active, crafting tables open
 * nothing and the crafting / smithing lookups skip BlockSouls recipes ({@code mixin.shop.RecipeManagerMixin}),
 * so the shopkeeper's forge and brew boards are the only place they craft. Classic runs are untouched.
 */
public final class ShopGate {
	private static volatile boolean closed;

	private ShopGate() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> closed = shouldClose(server));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() && closed && level.getBlockState(hit.getBlockPos()).is(Blocks.CRAFTING_TABLE)) {
				player.sendOverlayMessage(Component.translatable("message.blocksouls_game.crafting_off"));
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	static boolean shouldClose(MinecraftServer server) {
		return Director.config().shop.craftingOffInFloors && "floors".equals(RunSystem.config().runShape) && DirectorHooks.activeRun(server);
	}

	/** Whether BlockSouls recipes are off the grids right now. */
	public static boolean closed() {
		return closed;
	}

	/** Forces the gate (gametests); the next server tick recomputes it. */
	public static void setClosed(boolean value) {
		closed = value;
	}

	/** Whether the lookups must skip {@code recipe} of {@code type} right now. */
	public static boolean blocks(RecipeType<?> type, RecipeHolder<?> recipe) {
		return closed && (type == RecipeType.CRAFTING || type == RecipeType.SMITHING) && ShopRecipes.modRecipe(recipe.id().identifier());
	}

	/** Whether the gate filters {@code type} at all right now. */
	public static boolean filters(RecipeType<?> type) {
		return closed && (type == RecipeType.CRAFTING || type == RecipeType.SMITHING);
	}
}
