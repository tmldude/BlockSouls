package io.github.tmldude.blocksouls.world.floor;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import io.github.tmldude.blocksouls.common.synergy.BuildHooks;

/**
 * What the Vault's (floor 4) two mini-bosses share: party-scaled health ({@link VaultBossMath}), a boss bar, and the secret's reward. The one
 * waiting in the chamber over the secret room carries {@link #SECRET}; when it dies, everyone near is offered a
 * trinket ({@code BuildHooks.offerTrinkets}).
 */
public final class VaultBoss {
	/** Scoreboard tag on the secret chamber's mini-boss (saved with the entity). */
	public static final String SECRET = "blocksouls_world.vault_secret";

	private final Mob mob;
	private final BossEvent.BossBarColor color;
	private @Nullable ServerBossEvent bar;

	VaultBoss(Mob mob, BossEvent.BossBarColor color) {
		this.mob = mob;
		this.color = color;
	}

	/** Sets health (scaled for the players on the server) and attack damage, at spawn. */
	void setUp(ServerLevel level, double baseHealth, double damage) {
		double health = VaultBossMath.health(baseHealth, level.getServer().getPlayerCount());
		AttributeInstance max = mob.getAttribute(Attributes.MAX_HEALTH);
		if (max != null) {
			max.setBaseValue(health);
		}
		mob.setHealth((float) health);
		AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			attack.setBaseValue(Math.max(0, damage));
		}
		mob.setPersistenceRequired();
	}

	void tick() {
		bar().setProgress(mob.getHealth() / Math.max(1.0F, mob.getMaxHealth()));
	}

	private ServerBossEvent bar() {
		if (bar == null) {
			bar = new ServerBossEvent(mob.getUUID(), mob.getDisplayName(), color, BossEvent.BossBarOverlay.NOTCHED_6);
		}
		return bar;
	}

	void seenBy(ServerPlayer player) {
		bar().addPlayer(player);
	}

	void unseenBy(ServerPlayer player) {
		if (bar != null) {
			bar.removePlayer(player);
		}
	}

	/** On death: the secret's mini-boss offers each player near it a trinket. Returns how many were offered one. */
	int died() {
		if (bar != null) {
			bar.removeAllPlayers();
		}
		if (!mob.entityTags().contains(SECRET) || !(mob.level() instanceof ServerLevel level)) {
			return 0;
		}
		int reach = Math.max(1, VaultHazards.config().secretTrinketReach);
		int offered = 0;
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.distanceToSqr(mob) <= (double) reach * reach && BuildHooks.offerTrinkets(player)) {
				offered++;
			}
		}
		return offered;
	}
}
