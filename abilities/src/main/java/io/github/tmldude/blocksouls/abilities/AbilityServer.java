package io.github.tmldude.blocksouls.abilities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.abilities.effect.AbilityDamage;
import io.github.tmldude.blocksouls.abilities.effect.Effects;
import io.github.tmldude.blocksouls.abilities.kit.Ability;
import io.github.tmldude.blocksouls.abilities.kit.AbilityKit;
import io.github.tmldude.blocksouls.abilities.kit.CastContext;
import io.github.tmldude.blocksouls.abilities.logic.ChargeMath;
import io.github.tmldude.blocksouls.abilities.logic.SynergyScaling;
import io.github.tmldude.blocksouls.abilities.sound.AbilitySounds;
import io.github.tmldude.blocksouls.common.ability.Abilities;
import io.github.tmldude.blocksouls.common.ability.AbilityDefinition;
import io.github.tmldude.blocksouls.common.ability.AbilitySlot;
import io.github.tmldude.blocksouls.common.ability.ChargeModel;
import io.github.tmldude.blocksouls.common.event.AbilityEvents;
import io.github.tmldude.blocksouls.common.event.CombatEvents;
import io.github.tmldude.blocksouls.common.event.ConfigEvents;
import io.github.tmldude.blocksouls.common.event.RunEvents;
import io.github.tmldude.blocksouls.common.party.PartyScaling;
import io.github.tmldude.blocksouls.common.player.Origins;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.synergy.SynergyTags;

/**
 * Server side of the kit: fills empty slots with the defaults (signature from the class), turns cast requests into
 * casts (readiness, cooldown or charge, synergy scaling), and charges ultimates from kills and precision hits.
 */
public final class AbilityServer {
	/** Players inside a melee frenzy: no damage lands on them until this game time. */
	private static final Map<UUID, Long> INVULNERABLE_UNTIL = new HashMap<>();

	private AbilityServer() {
	}

	public static void register() {
		Effects.register();
		AbilityEvents.CAST_REQUESTED.register(AbilityServer::tryCast);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ensureKit(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> INVULNERABLE_UNTIL.remove(handler.player.getUUID()));
		RunEvents.RUN_STARTED.register((server, state) -> server.getPlayerList().getPlayers().forEach(AbilityServer::refreshSignature));
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, killer, killed, source) -> {
			if (killer instanceof ServerPlayer player && killed instanceof Mob) {
				onKill(player, killed);
			}
		});
		CombatEvents.HIT.register((shooter, target, damage, precision, killed) -> {
			if (precision) {
				onPrecisionHit(shooter);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !isInvulnerable(player));
		ConfigEvents.RELOADED.register((name, config) -> {
			if (config instanceof AbilitiesConfig) {
				AbilityIds.registerAll();
			}
		});
	}

	private static AbilitiesConfig config() {
		return BlockSoulsAbilities.CONFIG;
	}

	// ---- Kit --------------------------------------------------------------------------------------------------

	/** Fills empty slots with the config defaults; refreshes the signature from the origin when configured. */
	public static void ensureKit(ServerPlayer player) {
		AbilitiesConfig.Kit kit = config().kit;
		if (Abilities.definition(player, AbilitySlot.TACTICAL) == null) {
			equipIfKnown(player, AbilitySlot.TACTICAL, AbilityIds.parse(kit.defaultTactical));
		}
		if (Abilities.definition(player, AbilitySlot.ULTIMATE) == null) {
			equipIfKnown(player, AbilitySlot.ULTIMATE, AbilityIds.parse(kit.defaultUltimate));
		}
		if (kit.refreshSignatureOnJoin || Abilities.definition(player, AbilitySlot.SIGNATURE) == null) {
			refreshSignature(player);
		}
	}

	/** The signature the player's class grants (config map, keyed by the id {@code Origins.of} returns), or the default without one. */
	public static @Nullable Identifier signatureFor(ServerPlayer player) {
		AbilitiesConfig.Kit kit = config().kit;
		Identifier playerClass = Origins.of(player);
		Identifier signature = playerClass == null ? null : AbilityIds.parse(kit.signatureByClass.get(playerClass.toString()));
		return signature != null ? signature : AbilityIds.parse(kit.defaultSignature);
	}

	public static void refreshSignature(ServerPlayer player) {
		Identifier signature = signatureFor(player);
		if (signature != null && !signature.equals(Abilities.equipped(player, AbilitySlot.SIGNATURE))) {
			Abilities.equip(player, AbilitySlot.SIGNATURE, signature);
		}
	}

	private static void equipIfKnown(ServerPlayer player, AbilitySlot slot, @Nullable Identifier id) {
		if (id != null && !id.equals(Abilities.equipped(player, slot))) {
			Abilities.equip(player, slot, id);
		}
	}

	// ---- Casting ----------------------------------------------------------------------------------------------

	private static boolean canAct(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !PlayerStatus.isDowned(player);
	}

	/** Casts the slot if it holds a ready ability. Returns whether it went off. */
	public static boolean tryCast(ServerPlayer player, AbilitySlot slot) {
		if (!canAct(player)) {
			return false;
		}
		AbilityDefinition definition = Abilities.definition(player, slot);
		if (definition == null) {
			return false;
		}
		if (!Abilities.isReady(player, slot)) {
			playToPlayer(player, AbilitySounds.DENIED, 0.6F, 1.0F);
			return false;
		}
		Ability behavior = AbilityKit.behavior(definition.id());
		if (behavior == null) {
			BlockSoulsAbilities.LOGGER.warn("Ability {} has a definition but no behavior", definition.id());
			return false;
		}

		ServerLevel level = player.level();
		int party = PartyScaling.partySize(level.players().size());
		AbilitiesConfig config = config();
		double power = SynergyScaling.multiplier(SynergyTags.of(player), definition.tags(), config.synergy.perStack, config.synergy.cap);
		CastContext context = new CastContext(player, level, config, definition, power, PartyScaling.abilityHeal(party), party);
		if (!behavior.cast(context)) {
			return false; // fizzled: nothing spent
		}

		if (definition.charge() == ChargeModel.KILL_CHARGE) {
			Abilities.setUltimateCharge(player, 0);
		} else {
			Abilities.startCooldown(player, slot, cooldownTicks(definition));
		}
		SoundEvent cue = switch (slot) {
			case TACTICAL -> AbilitySounds.CAST_TACTICAL;
			case SIGNATURE -> AbilitySounds.CAST_SIGNATURE;
			case ULTIMATE -> AbilitySounds.CAST_ULTIMATE;
		};
		level.playSound(null, player.getX(), player.getY(), player.getZ(), cue, SoundSource.PLAYERS, slot == AbilitySlot.ULTIMATE ? 1.2F : 0.7F, 1.0F);
		AbilityEvents.CAST.invoker().onCast(player, definition);
		return true;
	}

	/** The live cooldown for a definition: the slot's config value (so a config reload applies at once). */
	public static int cooldownTicks(AbilityDefinition definition) {
		return switch (definition.slot()) {
			case TACTICAL -> config().tactical.cooldownTicks;
			case SIGNATURE -> config().signature.cooldownTicks;
			case ULTIMATE -> definition.cooldownTicks();
		};
	}

	// ---- Ultimate charge --------------------------------------------------------------------------------------

	static void onKill(ServerPlayer player, LivingEntity killed) {
		AbilitiesConfig.Charge cfg = config().charge;
		double party = PartyScaling.ultimateCharge(player.level().players().size());
		double stacks = ChargeMath.stackMultiplier(SynergyTags.of(player).getOrDefault(SynergyTags.ON_KILL, 0), cfg.onKillStackBonus, cfg.stackBonusCap);
		double gained = ChargeMath.perKill(cfg.perKill, AbilityDamage.isElite(killed), cfg.eliteKillMult, AbilityDamage.isBoss(killed),
				cfg.bossKillMult, party, stacks);
		Abilities.addUltimateCharge(player, gained);
	}

	static void onPrecisionHit(ServerPlayer player) {
		AbilitiesConfig.Charge cfg = config().charge;
		double party = PartyScaling.ultimateCharge(player.level().players().size());
		double stacks = ChargeMath.stackMultiplier(SynergyTags.of(player).getOrDefault(SynergyTags.ON_PRECISION, 0), cfg.onPrecisionStackBonus,
				cfg.stackBonusCap);
		Abilities.addUltimateCharge(player, ChargeMath.perPrecision(cfg.perPrecisionHit, party, stacks));
	}

	// ---- Frenzy invulnerability -------------------------------------------------------------------------------

	public static void setInvulnerable(ServerPlayer player, int ticks) {
		INVULNERABLE_UNTIL.put(player.getUUID(), Abilities.now(player) + ticks);
	}

	public static boolean isInvulnerable(ServerPlayer player) {
		Long until = INVULNERABLE_UNTIL.get(player.getUUID());
		if (until == null) {
			return false;
		}
		if (Abilities.now(player) >= until || PlayerStatus.isDowned(player)) {
			INVULNERABLE_UNTIL.remove(player.getUUID());
			return false;
		}
		return true;
	}

	private static void playToPlayer(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
				player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
	}
}
