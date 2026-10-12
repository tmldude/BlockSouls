package io.github.tmldude.blocksouls.game.player.boon.rule;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjIntConsumer;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.event.CombatEvents;
import io.github.tmldude.blocksouls.common.event.PlayerMoveEvents;
import io.github.tmldude.blocksouls.common.event.PlayerRunEvents;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.boon.BoonEffect;
import io.github.tmldude.blocksouls.game.player.boon.BuildConfig;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;
import io.github.tmldude.blocksouls.game.player.downed.Downed;

/**
 * The rules boons, curses and trinkets can reference by id, and the fan-out of game events to the rules a player
 * currently owns. Rule ids live in the game namespace ({@code blocksouls_game:powder_burn}).
 */
public final class BoonRules {
	/** Runs before the downed system's own {@code ALLOW_DEATH} listener so a rule can keep the player standing. */
	private static final Identifier PHASE = Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "build_rules");

	private static final Map<Identifier, Rule> RULES = new LinkedHashMap<>();
	private static BuildConfig config = new BuildConfig();

	private BoonRules() {
	}

	public static BuildConfig config() {
		return config;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, path);
	}

	public static void register(Identifier id, Rule rule) {
		if (RULES.putIfAbsent(id, rule) != null) {
			throw new IllegalArgumentException("Rule " + id + " is already registered");
		}
	}

	public static boolean has(Identifier id) {
		return RULES.containsKey(id);
	}

	public static Set<Identifier> ids() {
		return Collections.unmodifiableSet(RULES.keySet());
	}

	/** Registers the shipped rules and hooks the events. Called once from the player system. */
	public static void init(BuildConfig buildConfig) {
		config = buildConfig;
		Keystones.register();
		Curses.register();
		CrossPlayer.register();
		Trinkets.register();

		CombatEvents.HIT.register((shooter, target, damage, precision, killed) ->
				forEach(shooter, (rule, level) -> rule.onHit(shooter, level, target, damage, precision, killed)));
		CombatEvents.RELOADED.register((player, rounds) -> forEach(player, (rule, level) -> rule.onReloaded(player, level, rounds)));
		PlayerMoveEvents.ROLLED.register(player -> forEach(player, (rule, level) -> rule.onRolled(player, level)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!PlayerStatus.isDowned(player)) {
					forEach(player, (rule, level) -> rule.tick(player, level));
				}
			}
		});
		PlayerRunEvents.REVIVED.register((revived, reviver) -> {
			for (ServerPlayer owner : revived.level().getServer().getPlayerList().getPlayers()) {
				forEach(owner, (rule, level) -> rule.onRevived(owner, level, revived, reviver));
			}
		});
		RunEvents.DUSK.register((server, night) -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				forEach(player, (rule, level) -> rule.onDusk(player, level, night));
			}
		});
		RunEvents.DAWN.register((server, night) -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				forEach(player, (rule, level) -> rule.onDawn(player, level, night));
			}
		});

		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.register(PHASE, (entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
					|| PlayerStatus.isDowned(player) || !Downed.canGoDown(player)) {
				return true;
			}
			boolean[] prevented = {false};
			forEach(player, (rule, level) -> {
				if (!prevented[0] && rule.preventDown(player, level)) {
					prevented[0] = true;
				}
			});
			if (prevented[0]) {
				return false;
			}
			forEach(player, (rule, level) -> rule.onGoingDown(player, level));
			return true;
		});
	}

	/** Calls {@code visitor} for every rule the player owns, with the level it's owned at. */
	public static void forEach(ServerPlayer player, ObjIntConsumer<Rule> visitor) {
		for (PlayerBoons.Entry entry : PlayerBoons.entries(player)) {
			for (BoonEffect effect : entry.boon().effects()) {
				if (effect instanceof BoonEffect.Rule r) {
					Rule rule = RULES.get(Identifier.parse(r.rule()));
					if (rule != null) {
						visitor.accept(rule, entry.level());
					}
				}
			}
		}
	}

	/** Whether the player owns something that references {@code rule}. */
	public static boolean owns(ServerPlayer player, Identifier rule) {
		boolean[] found = {false};
		Rule wanted = RULES.get(rule);
		forEach(player, (r, level) -> found[0] |= r == wanted);
		return wanted != null && found[0];
	}

	/** Product of every owned rule's heal multiplier (the heal mixin reads it). */
	public static float healMultiplier(ServerPlayer player) {
		float[] multiplier = {1.0F};
		forEach(player, (rule, level) -> multiplier[0] *= rule.healMultiplier(player, level));
		return multiplier[0];
	}

	/** Whether any owned rule switches natural regeneration off (the food mixin reads it). */
	public static boolean blocksNaturalRegen(ServerPlayer player) {
		boolean[] blocked = {false};
		forEach(player, (rule, level) -> blocked[0] |= rule.blocksNaturalRegen(player, level));
		return blocked[0];
	}

	public static int skipSoulsBonus(ServerPlayer player) {
		int[] bonus = {0};
		forEach(player, (rule, level) -> bonus[0] += rule.skipSoulsBonus(player, level));
		return bonus[0];
	}

	/** The party's prep-phase multiplier: the highest any online player's trinkets give (1 with none). */
	public static double prepTimeMultiplier(MinecraftServer server) {
		double[] best = {1.0};
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			forEach(player, (rule, level) -> best[0] = Math.max(best[0], rule.prepTimeMultiplier(player, level)));
		}
		return best[0];
	}

	/** Lets owned rules change the traits about to be applied. */
	public static void adjustTraits(ServerPlayer player, Map<Identifier, Integer> traits) {
		forEach(player, (rule, level) -> rule.adjustTraits(player, level, traits));
	}

	/** The player lost boons and curses: every rule drops its per-player boon state. */
	public static void onCleared(ServerPlayer player) {
		for (Rule rule : RULES.values()) {
			rule.onCleared(player);
		}
	}
}
