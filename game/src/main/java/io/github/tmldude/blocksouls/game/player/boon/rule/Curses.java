package io.github.tmldude.blocksouls.game.player.boon.rule;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import io.github.tmldude.blocksouls.game.player.boon.BuildConfig;
import io.github.tmldude.blocksouls.game.player.soul.Souls;

/** Curse rules. Curses with pure attribute effects (Brittle, Leaden) need no rule. */
final class Curses {
	static final Identifier HEMORRHAGE = BoonRules.id("hemorrhage");
	static final Identifier LOUD = BoonRules.id("loud");
	static final Identifier HEAVY_POCKETS = BoonRules.id("heavy_pockets");

	private Curses() {
	}

	private static BuildConfig.Rules rules() {
		return BoonRules.config().rules;
	}

	static void register() {
		BoonRules.register(HEMORRHAGE, new Hemorrhage());
		BoonRules.register(LOUD, new Loud());
		BoonRules.register(HEAVY_POCKETS, new HeavyPockets());
	}

	/** Healing is weaker. */
	static final class Hemorrhage implements Rule {
		@Override
		public float healMultiplier(ServerPlayer owner, int level) {
			return (float) Math.pow(rules().hemorrhage.healMultiplier, level);
		}
	}

	/** Idle hostiles nearby notice the owner from further away. */
	static final class Loud implements Rule {
		private final Map<UUID, Integer> timers = new HashMap<>();

		@Override
		public void tick(ServerPlayer owner, int level) {
			int timer = timers.merge(owner.getUUID(), 1, Integer::sum);
			if (timer < Math.max(1, rules().loud.intervalTicks)) {
				return;
			}
			timers.put(owner.getUUID(), 0);
			for (Mob mob : RuleSupport.hostilesNear(owner, rules().loud.radius)) {
				if (mob.getTarget() == null) {
					mob.setTarget(owner);
				}
			}
		}

		@Override
		public void onCleared(ServerPlayer player) {
			timers.remove(player.getUUID());
		}
	}

	/** Going down costs souls. */
	static final class HeavyPockets implements Rule {
		@Override
		public void onGoingDown(ServerPlayer owner, int level) {
			Souls.take(owner, rules().heavyPockets.soulsLost * level);
		}
	}
}
