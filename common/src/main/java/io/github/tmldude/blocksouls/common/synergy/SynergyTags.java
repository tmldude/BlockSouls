package io.github.tmldude.blocksouls.common.synergy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * The v2 synergy engine's shared tag vocabulary. Every boon, wacky enchantment and gun perk declares
 * synergy tags; builds emerge from stacking the same tags across modules (relic offers weight toward tags
 * the player already owns, the HUD shows the top two). This class only defines the contract — the tag ids,
 * their categories, and where "the tags of a player's build" are read; the item modules fill it in Stage 6.
 *
 * <p>Register extra tags and sources from mod initializers (single-threaded); reads are safe any time after.
 */
public final class SynergyTags {
	/** What role a tag plays in a build (one category per tag). */
	public enum Category {
		/** Damage flavor: {@code fire}, {@code storm}, ... */
		ELEMENT,
		/** When an effect fires: {@code on_kill}, {@code on_reload}, ... */
		TRIGGER,
		/** What an effect does: {@code ricochet}, {@code explosion}, ... */
		PAYLOAD
	}

	private static final Map<Identifier, Category> TAGS = new LinkedHashMap<>();
	private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

	// Elements
	public static final Identifier FIRE = register(tag("fire"), Category.ELEMENT);
	public static final Identifier STORM = register(tag("storm"), Category.ELEMENT);
	public static final Identifier BLIGHT = register(tag("blight"), Category.ELEMENT);
	public static final Identifier KINETIC = register(tag("kinetic"), Category.ELEMENT);

	// Triggers
	public static final Identifier ON_KILL = register(tag("on_kill"), Category.TRIGGER);
	public static final Identifier ON_PRECISION = register(tag("on_precision"), Category.TRIGGER);
	public static final Identifier ON_ROLL = register(tag("on_roll"), Category.TRIGGER);
	public static final Identifier ON_RELOAD = register(tag("on_reload"), Category.TRIGGER);

	// Payloads
	public static final Identifier RICOCHET = register(tag("ricochet"), Category.PAYLOAD);
	public static final Identifier EXPLOSION = register(tag("explosion"), Category.PAYLOAD);
	public static final Identifier PULL = register(tag("pull"), Category.PAYLOAD);
	public static final Identifier HEAL = register(tag("heal"), Category.PAYLOAD);

	private SynergyTags() {
	}

	/** Registers a tag (idempotent for the same category; a different category for a known id is an error). */
	public static Identifier register(Identifier id, Category category) {
		Category existing = TAGS.putIfAbsent(id, category);
		if (existing != null && existing != category) {
			throw new IllegalArgumentException("Synergy tag " + id + " is already registered as " + existing);
		}
		return id;
	}

	public static boolean isKnown(Identifier id) {
		return TAGS.containsKey(id);
	}

	public static @Nullable Category category(Identifier id) {
		return TAGS.get(id);
	}

	/** Every registered tag id, in registration order. */
	public static Set<Identifier> all() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(TAGS.keySet()));
	}

	/**
	 * A module that contributes tags to a player's build (boons, equipped guns' perks, enchantments, ...).
	 * Called on whichever side asks, so only read data available on that side.
	 */
	@FunctionalInterface
	public interface Source {
		/** Report each tag the player currently owns through this source, with how many times it's stacked. */
		void collect(Player player, BiConsumer<Identifier, Integer> sink);
	}

	/** Registers a source of build tags (call from a mod initializer). */
	public static void addSource(Source source) {
		SOURCES.add(source);
	}

	/** The tags of everything the player currently owns, as {@code tag → total stacks} across all sources. */
	public static Map<Identifier, Integer> of(Player player) {
		Map<Identifier, Integer> tags = new LinkedHashMap<>();
		for (Source source : SOURCES) {
			source.collect(player, (id, count) -> {
				if (count > 0) {
					tags.merge(id, count, Integer::sum);
				}
			});
		}
		return tags;
	}

	private static Identifier tag(String path) {
		return CommonIds.id("blocksouls", path);
	}
}
