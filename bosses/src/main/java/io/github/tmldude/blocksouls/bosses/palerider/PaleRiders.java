package io.github.tmldude.blocksouls.bosses.palerider;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.bosses.field.FieldBosses;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.common.debug.DebugCommands;

/** Registers the Pale Rider (config, sounds, entity, definition, field spawn rule) and its Wraith Steed + whistle. */
public final class PaleRiders {
	public static PaleRiderConfig CONFIG = new PaleRiderConfig();
	public static EntityType<PaleRider> TYPE;

	private PaleRiders() {
	}

	public static void register() {
		CONFIG = Config.load("boss-pale_rider", PaleRiderConfig.class, PaleRiderConfig::new);
		PaleRiderSounds.init();
		PaleRider.createDefinition();
		TYPE = Bosses.register(CommonIds.PALE_RIDER, PaleRider::new, new Bosses.Size(1.6f, 3.5f, 3.2f), false,
				() -> Bosses.attributes(CONFIG.health, CONFIG.armor, CONFIG.speed, CONFIG.lance.damage));
		FieldBosses.register(CommonIds.PALE_RIDER, new PaleRiderSpawnRule());
		WraithSteeds.register();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WraithSteeds.clear());
		DebugCommands.register((root, context) -> root.then(Commands.literal("steed")
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					WraithSteed steed = WraithSteeds.whistle(player);
					ctx.getSource().sendSuccess(() -> Component.literal(steed == null ? "No steed (riding something else?)" : "Wraith Steed summoned"), false);
					return steed == null ? 0 : 1;
				})
				.then(Commands.literal("whistle").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					player.getInventory().add(new ItemStack(WraithSteeds.WHISTLE));
					player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(WraithSteeds.WHISTLE.getDefaultInstance()));
					ctx.getSource().sendSuccess(() -> Component.literal("Gave a Wraith Steed whistle"), false);
					return 1;
				}))));
	}
}
