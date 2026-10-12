package io.github.tmldude.blocksouls.abilities;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.common.ability.AbilityDefinition;
import io.github.tmldude.blocksouls.common.ability.AbilityRegistry;
import io.github.tmldude.blocksouls.common.ability.AbilitySlot;
import io.github.tmldude.blocksouls.common.synergy.SynergyTags;

/** The kit's ids and their contract definitions (slot, cooldown, synergy tags). Behaviors are in {@code kit}. */
public final class AbilityIds {
	// Tactical
	public static final Identifier FRAG = id("frag");
	public static final Identifier WEB_BOMB = id("web_bomb");
	public static final Identifier FIRE_WALL = id("fire_wall");
	public static final Identifier HEALING_DART = id("healing_dart");
	// Signature (one per origin)
	public static final Identifier GROUND_POUND = id("ground_pound");
	public static final Identifier WEB_PULL = id("web_pull");
	public static final Identifier EMBER_WAVE = id("ember_wave");
	public static final Identifier GEYSER = id("geyser");
	public static final Identifier TAUNT_SLAM = id("taunt_slam");
	public static final Identifier FAN_THE_HAMMER = id("fan_the_hammer");
	public static final Identifier OVERCLOCK = id("overclock");
	public static final Identifier TRANSFUSION = id("transfusion");
	// Ultimate
	public static final Identifier STORM_BARRAGE = id("storm_barrage");
	public static final Identifier MELEE_FRENZY = id("melee_frenzy");
	public static final Identifier PARTY_OVERSHIELD = id("party_overshield");

	public static final List<Identifier> ALL = List.of(FRAG, WEB_BOMB, FIRE_WALL, HEALING_DART, GROUND_POUND, WEB_PULL, EMBER_WAVE,
			GEYSER, TAUNT_SLAM, FAN_THE_HAMMER, OVERCLOCK, TRANSFUSION, STORM_BARRAGE, MELEE_FRENZY, PARTY_OVERSHIELD);

	private AbilityIds() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(BlockSoulsAbilities.MOD_ID, path);
	}

	/** {@code frag} or {@code blocksouls_abilities:frag} → the id; null when it isn't one of ours. */
	public static @Nullable Identifier parse(@Nullable String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		Identifier id = name.contains(":") ? Identifier.tryParse(name) : id(name);
		return id != null && ALL.contains(id) ? id : null;
	}

	/** Registers (or re-registers, after a config change) every definition with the config's cooldowns. */
	public static void registerAll() {
		AbilitiesConfig config = BlockSoulsAbilities.CONFIG;
		int tactical = config.tactical.cooldownTicks;
		int signature = config.signature.cooldownTicks;

		AbilityRegistry.register(AbilityDefinition.cooldown(FRAG, AbilitySlot.TACTICAL, tactical, SynergyTags.EXPLOSION, SynergyTags.KINETIC));
		AbilityRegistry.register(AbilityDefinition.cooldown(WEB_BOMB, AbilitySlot.TACTICAL, tactical, SynergyTags.PULL));
		AbilityRegistry.register(AbilityDefinition.cooldown(FIRE_WALL, AbilitySlot.TACTICAL, tactical, SynergyTags.FIRE));
		AbilityRegistry.register(AbilityDefinition.cooldown(HEALING_DART, AbilitySlot.TACTICAL, tactical, SynergyTags.HEAL));

		AbilityRegistry.register(AbilityDefinition.cooldown(GROUND_POUND, AbilitySlot.SIGNATURE, signature, SynergyTags.KINETIC, SynergyTags.EXPLOSION));
		AbilityRegistry.register(AbilityDefinition.cooldown(WEB_PULL, AbilitySlot.SIGNATURE, signature, SynergyTags.PULL));
		AbilityRegistry.register(AbilityDefinition.cooldown(EMBER_WAVE, AbilitySlot.SIGNATURE, signature, SynergyTags.FIRE));
		AbilityRegistry.register(AbilityDefinition.cooldown(GEYSER, AbilitySlot.SIGNATURE, signature, SynergyTags.KINETIC, SynergyTags.HEAL));
		AbilityRegistry.register(AbilityDefinition.cooldown(TAUNT_SLAM, AbilitySlot.SIGNATURE, signature, SynergyTags.KINETIC));
		AbilityRegistry.register(AbilityDefinition.cooldown(FAN_THE_HAMMER, AbilitySlot.SIGNATURE, signature, SynergyTags.KINETIC, SynergyTags.ON_PRECISION));
		AbilityRegistry.register(AbilityDefinition.cooldown(OVERCLOCK, AbilitySlot.SIGNATURE, signature, SynergyTags.STORM));
		AbilityRegistry.register(AbilityDefinition.cooldown(TRANSFUSION, AbilitySlot.SIGNATURE, signature, SynergyTags.BLIGHT, SynergyTags.HEAL));

		AbilityRegistry.register(AbilityDefinition.ultimate(STORM_BARRAGE, SynergyTags.STORM, SynergyTags.ON_KILL));
		AbilityRegistry.register(AbilityDefinition.ultimate(MELEE_FRENZY, SynergyTags.KINETIC, SynergyTags.ON_KILL));
		AbilityRegistry.register(AbilityDefinition.ultimate(PARTY_OVERSHIELD, SynergyTags.HEAL));
	}
}
