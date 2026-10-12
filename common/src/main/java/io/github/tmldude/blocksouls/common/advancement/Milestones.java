package io.github.tmldude.blocksouls.common.advancement;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.event.BossEvents;
import io.github.tmldude.blocksouls.common.event.PlayerRunEvents;
import io.github.tmldude.blocksouls.common.event.RunEvents;

/**
 * Turns the common run events into the {@link MilestoneTrigger} criterion, so the tutorial advancements (data in each
 * module) can react to nights, boons and bosses without any module granting advancements itself.
 */
public final class Milestones {
	public static final Identifier ID = CommonIds.id("blocksouls_common", "milestone");
	public static final MilestoneTrigger TRIGGER = Registry.register(BuiltInRegistries.TRIGGER_TYPES, ID, new MilestoneTrigger());

	/** The id passed for milestones that aren't about a boss or boon. */
	private static final Identifier NONE = CommonIds.id("blocksouls_common", "none");

	private Milestones() {
	}

	public static void init() {
		RunEvents.DAWN.register(Milestones::nightSurvived);
		PlayerRunEvents.BOON_GRANTED.register((player, boon) -> TRIGGER.trigger(player, MilestoneTrigger.Milestone.BOON, boon, 0));
		BossEvents.KILLED.register((level, bossId, boss, players) -> {
			for (ServerPlayer player : players) {
				TRIGGER.trigger(player, MilestoneTrigger.Milestone.BOSS_KILLED, bossId, 0);
			}
		});
	}

	/** Everyone still playing at dawn (spectators sat it out) survived the night. */
	public static void nightSurvived(MinecraftServer server, int night) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isSpectator()) {
				TRIGGER.trigger(player, MilestoneTrigger.Milestone.NIGHT_SURVIVED, NONE, night);
			}
		}
	}
}
