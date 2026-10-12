package io.github.tmldude.blocksouls.game.player.boon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.event.PlayerRunEvents;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.PlayersConfig;
import io.github.tmldude.blocksouls.game.player.boon.rule.BoonRules;

/**
 * What a player owns: boons ({@code id → level}), curses (oldest first) and trinkets (slot order). Boons and curses
 * are saved with the player and not copied on death; trinkets survive going down and death. Effects are permanent
 * while owned (re-applied if removed), attribute modifiers and traits follow the owned set, items are handed over
 * once when first granted. Going down clears boons and curses, then re-applies what the trinkets give.
 */
public final class PlayerBoons {
	private static final AttachmentType<Map<Identifier, Integer>> OWNED = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "boons"),
			builder -> builder.persistent(Codec.unboundedMap(Identifier.CODEC, Codec.INT)));
	private static final AttachmentType<List<Identifier>> CURSES = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "curses"),
			builder -> builder.persistent(Identifier.CODEC.listOf()));
	private static final AttachmentType<List<Identifier>> TRINKETS = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "trinkets"),
			builder -> builder.persistent(Identifier.CODEC.listOf()).copyOnDeath());

	private static PlayersConfig.Boons config;
	private static BuildConfig build = new BuildConfig();
	private static int reapplyTimer;

	private PlayerBoons() {
	}

	/** An owned definition with the level it's owned at (curses and trinkets are always level 1). */
	public record Entry(Boon boon, int level) {
	}

	public static void register(PlayersConfig.Boons boonsConfig, BuildConfig buildConfig) {
		config = boonsConfig;
		build = buildConfig;
		Networking.registerClientbound(BoonPayloads.Owned.TYPE, BoonPayloads.Owned.CODEC);
		Networking.registerClientbound(BoonPayloads.BuildLine.TYPE, BoonPayloads.BuildLine.CODEC);

		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> refresh(listener.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refresh(newPlayer));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++reapplyTimer >= Math.max(1, config.reapplyIntervalTicks)) {
				reapplyTimer = 0;
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (!entries(player).isEmpty()) {
						applyEffects(player);
					}
				}
			}
		});

		// The build's tags: every owned boon's and trinket's tags, stacked by level. Curses carry no tags.
		SynergyTags.addSource((player, sink) -> {
			if (player instanceof ServerPlayer serverPlayer) {
				for (Entry entry : entries(serverPlayer)) {
					for (String tag : entry.boon().tags()) {
						sink.accept(Identifier.parse(tag), entry.level());
					}
				}
			}
		});
	}

	public static BuildConfig config() {
		return build;
	}

	/** After a data reload: definitions may have changed. */
	public static void refreshAll(MinecraftServer server) {
		server.getPlayerList().getPlayers().forEach(PlayerBoons::refresh);
	}

	// ---- Boons ----

	public static Map<Identifier, Integer> owned(ServerPlayer player) {
		Map<Identifier, Integer> owned = ((AttachmentTarget) player).getAttached(OWNED);
		return owned == null ? Map.of() : owned;
	}

	/** Owned boons keyed by string id, for {@link CardRoller}. */
	public static Map<String, Integer> ownedById(ServerPlayer player) {
		Map<String, Integer> owned = new HashMap<>();
		owned(player).forEach((id, level) -> owned.put(id.toString(), level));
		return owned;
	}

	public static int level(ServerPlayer player, Identifier boon) {
		return owned(player).getOrDefault(boon, 0);
	}

	/** Adds a level of {@code boon} (up to its max). Returns false if it's already maxed, or if it isn't a boon. */
	public static boolean grant(ServerPlayer player, Boon boon) {
		if (boon.kind() != Boon.Kind.BOON) {
			return false;
		}
		Identifier id = Identifier.parse(boon.id());
		int level = level(player, id);
		if (level >= boon.maxLevel()) {
			return false;
		}

		Map<Identifier, Integer> owned = new LinkedHashMap<>(owned(player));
		owned.put(id, level + 1);
		((AttachmentTarget) player).setAttached(OWNED, Map.copyOf(owned));

		if (level == 0) {
			giveItems(player, boon);
		}
		refresh(player);
		PlayerRunEvents.BOON_GRANTED.invoker().onBoonGranted(player, id);
		return true;
	}

	/** Owned boons that can still level up. */
	public static List<Boon> upgradable(ServerPlayer player) {
		List<Boon> out = new ArrayList<>();
		owned(player).forEach((id, level) -> {
			Boon boon = BoonCatalog.get(id);
			if (boon != null && level < boon.maxLevel()) {
				out.add(boon);
			}
		});
		return out;
	}

	// ---- Curses ----

	/** The player's curses, oldest first. */
	public static List<Identifier> curses(ServerPlayer player) {
		List<Identifier> curses = ((AttachmentTarget) player).getAttached(CURSES);
		return curses == null ? List.of() : curses;
	}

	public static boolean hasCurse(ServerPlayer player, Identifier curse) {
		return curses(player).contains(curse);
	}

	/** Puts a curse on the player. False if it isn't a curse, is already owned, or the player carries the maximum. */
	public static boolean applyCurse(ServerPlayer player, Boon curse) {
		Identifier id = Identifier.parse(curse.id());
		if (curse.kind() != Boon.Kind.CURSE || hasCurse(player, id) || curses(player).size() >= Math.max(1, build.curses.maxCurses)) {
			return false;
		}
		List<Identifier> curses = new ArrayList<>(curses(player));
		curses.add(id);
		((AttachmentTarget) player).setAttached(CURSES, List.copyOf(curses));
		refresh(player);
		PlayerRunEvents.CURSE_APPLIED.invoker().onGranted(player, id);
		return true;
	}

	/** Removes {@code curse}, or the most recent one when null. False if there was nothing to remove. */
	public static boolean purgeCurse(ServerPlayer player, @Nullable Identifier curse) {
		List<Identifier> curses = new ArrayList<>(curses(player));
		if (curses.isEmpty()) {
			return false;
		}
		Identifier removed = curse == null ? curses.getLast() : curse;
		if (!curses.remove(removed)) {
			return false;
		}
		((AttachmentTarget) player).setAttached(CURSES, curses.isEmpty() ? null : List.copyOf(curses));
		removeApplied(player, BoonCatalog.get(removed));
		refresh(player);
		PlayerRunEvents.CURSE_PURGED.invoker().onGranted(player, removed);
		return true;
	}

	// ---- Trinkets ----

	/** The player's trinkets, in slot order (oldest first). */
	public static List<Identifier> trinkets(ServerPlayer player) {
		List<Identifier> trinkets = ((AttachmentTarget) player).getAttached(TRINKETS);
		return trinkets == null ? List.of() : trinkets;
	}

	public static boolean hasTrinket(ServerPlayer player, Identifier trinket) {
		return trinkets(player).contains(trinket);
	}

	/** The trinket a pick would replace right now: the oldest, when every slot is full. */
	public static Optional<Identifier> trinketToReplace(ServerPlayer player) {
		List<Identifier> trinkets = trinkets(player);
		return trinkets.size() >= Math.max(1, build.trinkets.slots) ? Optional.of(trinkets.getFirst()) : Optional.empty();
	}

	/**
	 * Adds a trinket; with full slots the oldest is replaced. False if it isn't a trinket or is already owned.
	 */
	public static boolean grantTrinket(ServerPlayer player, Boon trinket) {
		Identifier id = Identifier.parse(trinket.id());
		if (trinket.kind() != Boon.Kind.TRINKET || hasTrinket(player, id)) {
			return false;
		}
		List<Identifier> trinkets = new ArrayList<>(trinkets(player));
		while (trinkets.size() >= Math.max(1, build.trinkets.slots)) {
			removeApplied(player, BoonCatalog.get(trinkets.removeFirst()));
		}
		trinkets.add(id);
		((AttachmentTarget) player).setAttached(TRINKETS, List.copyOf(trinkets));
		giveItems(player, trinket);
		refresh(player);
		PlayerRunEvents.TRINKET_GRANTED.invoker().onGranted(player, id);
		return true;
	}

	/** Removes one trinket (debug). */
	public static boolean removeTrinket(ServerPlayer player, Identifier trinket) {
		List<Identifier> trinkets = new ArrayList<>(trinkets(player));
		if (!trinkets.remove(trinket)) {
			return false;
		}
		((AttachmentTarget) player).setAttached(TRINKETS, trinkets.isEmpty() ? null : List.copyOf(trinkets));
		removeApplied(player, BoonCatalog.get(trinket));
		refresh(player);
		return true;
	}

	/** Removes every trinket (debug, tests). */
	public static void clearTrinkets(ServerPlayer player) {
		for (Identifier trinket : trinkets(player)) {
			removeApplied(player, BoonCatalog.get(trinket));
		}
		((AttachmentTarget) player).removeAttached(TRINKETS);
		refresh(player);
	}

	// ---- Everything owned ----

	/** Boons at their level, then curses, then trinkets (unknown ids skipped). */
	public static List<Entry> entries(ServerPlayer player) {
		List<Entry> entries = new ArrayList<>();
		owned(player).forEach((id, level) -> {
			Boon boon = BoonCatalog.get(id);
			if (boon != null) {
				entries.add(new Entry(boon, level));
			}
		});
		for (Identifier id : curses(player)) {
			Boon curse = BoonCatalog.get(id);
			if (curse != null) {
				entries.add(new Entry(curse, 1));
			}
		}
		for (Identifier id : trinkets(player)) {
			Boon trinket = BoonCatalog.get(id);
			if (trinket != null) {
				entries.add(new Entry(trinket, 1));
			}
		}
		return entries;
	}

	/** Level at which the player owns a definition of any kind (0 = not owned). */
	public static int levelOf(ServerPlayer player, Boon boon) {
		Identifier id = Identifier.parse(boon.id());
		return switch (boon.kind()) {
			case BOON -> level(player, id);
			case CURSE -> hasCurse(player, id) ? 1 : 0;
			case TRINKET -> hasTrinket(player, id) ? 1 : 0;
		};
	}

	/** Removes every boon and curse and what they applied; trinkets and granted items stay. */
	public static void clearAll(ServerPlayer player) {
		Map<Identifier, Integer> owned = owned(player);
		List<Identifier> curses = curses(player);
		if (owned.isEmpty() && curses.isEmpty() && PlayerTraits.all(player).isEmpty()) {
			return;
		}

		for (Identifier id : owned.keySet()) {
			removeApplied(player, BoonCatalog.get(id));
		}
		for (Identifier id : curses) {
			removeApplied(player, BoonCatalog.get(id));
		}
		((AttachmentTarget) player).removeAttached(OWNED);
		((AttachmentTarget) player).removeAttached(CURSES);
		BoonRules.onCleared(player);
		refresh(player);
	}

	/** Takes back a definition's permanent effects and attribute modifiers (traits are recomputed by {@link #refresh}). */
	private static void removeApplied(ServerPlayer player, @Nullable Boon boon) {
		if (boon == null) {
			return;
		}
		for (int i = 0; i < boon.effects().size(); i++) {
			switch (boon.effects().get(i)) {
				case BoonEffect.Effect e -> effect(e.effect()).ifPresent(holder -> {
					MobEffectInstance current = player.getEffect(holder);
					if (current != null && current.isInfiniteDuration()) {
						player.removeEffect(holder);
					}
				});
				case BoonEffect.Attribute a -> {
					AttributeInstance attribute = attribute(player, a.attribute());
					if (attribute != null) {
						attribute.removeModifier(modifierId(boon, i));
					}
				}
				default -> {
				}
			}
		}
	}

	/** Re-applies effects, modifiers and traits from everything owned, and syncs the HUD. */
	public static void refresh(ServerPlayer player) {
		applyEffects(player);
		applyModifiers(player);
		applyTraits(player);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
		sync(player);
	}

	private static void applyEffects(ServerPlayer player) {
		Map<Holder<MobEffect>, Integer> wanted = new HashMap<>();
		forEachEffect(player, (boon, level, index, effect) -> {
			if (effect instanceof BoonEffect.Effect e) {
				effect(e.effect()).ifPresent(holder -> wanted.merge(holder, e.amplifier() + level - 1, Math::max));
			}
		});

		wanted.forEach((holder, amplifier) -> {
			MobEffectInstance current = player.getEffect(holder);
			if (current == null || !current.isInfiniteDuration() || current.getAmplifier() < amplifier) {
				player.addEffect(new MobEffectInstance(holder, MobEffectInstance.INFINITE_DURATION, amplifier, true, false, true));
			}
		});
	}

	private static void applyModifiers(ServerPlayer player) {
		forEachEffect(player, (boon, level, index, effect) -> {
			if (effect instanceof BoonEffect.Attribute a) {
				AttributeInstance attribute = attribute(player, a.attribute());
				AttributeModifier.Operation operation = operation(a.operation());
				if (attribute != null && operation != null) {
					attribute.addOrReplacePermanentModifier(new AttributeModifier(modifierId(boon, index), a.amount() * level, operation));
				}
			}
		});
	}

	private static void applyTraits(ServerPlayer player) {
		Map<Identifier, Integer> wanted = new HashMap<>();
		forEachEffect(player, (boon, level, index, effect) -> {
			if (effect instanceof BoonEffect.Trait t) {
				wanted.merge(Identifier.parse(t.trait()), t.level() * level, Integer::sum);
			}
		});
		BoonRules.adjustTraits(player, wanted);

		if (!wanted.equals(PlayerTraits.all(player))) {
			PlayerTraits.clear(player);
			wanted.forEach((trait, level) -> PlayerTraits.set(player, trait, level));
		}
	}

	private static void giveItems(ServerPlayer player, Boon boon) {
		for (BoonEffect effect : boon.effects()) {
			if (effect instanceof BoonEffect.Item i) {
				Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(i.item()));
				if (item == null) {
					continue;
				}
				ItemStack stack = new ItemStack(item, Math.max(1, i.count()));
				if (!player.addItem(stack) && !stack.isEmpty()) {
					player.spawnAtLocation(player.level(), stack);
				}
			}
		}
	}

	/**
	 * Sends the HUD what's owned: boons minus the parts of owned combos (combos first, then by tier and id), then
	 * curses, then trinkets; and the build line.
	 */
	public static void sync(ServerPlayer player) {
		Map<Identifier, Integer> owned = owned(player);
		Set<String> hidden = new HashSet<>();
		for (Identifier id : owned.keySet()) {
			Boon boon = BoonCatalog.get(id);
			if (boon != null) {
				hidden.addAll(boon.requires());
			}
		}

		List<BoonPayloads.BoonView> views = new ArrayList<>();
		owned.forEach((id, level) -> {
			Boon boon = BoonCatalog.get(id);
			if (boon != null && !hidden.contains(boon.id())) {
				views.add(BoonPayloads.BoonView.of(boon, level));
			}
		});
		views.sort(Comparator.comparing(BoonPayloads.BoonView::combo).reversed()
				.thenComparing(BoonPayloads.BoonView::tier, Comparator.reverseOrder())
				.thenComparing(view -> view.id().toString()));
		for (Identifier id : curses(player)) {
			Boon curse = BoonCatalog.get(id);
			if (curse != null) {
				views.add(BoonPayloads.BoonView.of(curse, 1));
			}
		}
		for (Identifier id : trinkets(player)) {
			Boon trinket = BoonCatalog.get(id);
			if (trinket != null) {
				views.add(BoonPayloads.BoonView.of(trinket, 1));
			}
		}
		Networking.sendToPlayer(player, new BoonPayloads.Owned(views));
		Networking.sendToPlayer(player, new BoonPayloads.BuildLine(buildLine(player, Math.max(0, build.offers.buildLineTags))));
	}

	/** The top {@code count} synergy tags of the player's build by stacks (ties by the tag vocabulary's order). */
	public static List<BoonPayloads.TagView> buildLine(Player player, int count) {
		List<BoonPayloads.TagView> line = new ArrayList<>();
		SynergyTags.of(player).forEach((tag, stacks) -> line.add(new BoonPayloads.TagView(tag, stacks)));
		line.sort(Comparator.comparingInt(BoonPayloads.TagView::stacks).reversed());
		return line.size() > count ? List.copyOf(line.subList(0, count)) : line;
	}

	@FunctionalInterface
	private interface EffectVisitor {
		void visit(Boon boon, int level, int index, BoonEffect effect);
	}

	private static void forEachEffect(ServerPlayer player, EffectVisitor visitor) {
		for (Entry entry : entries(player)) {
			for (int i = 0; i < entry.boon().effects().size(); i++) {
				visitor.visit(entry.boon(), entry.level(), i, entry.boon().effects().get(i));
			}
		}
	}

	static Identifier modifierId(Boon boon, int index) {
		Identifier id = Identifier.parse(boon.id());
		return Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "boon/" + id.getNamespace() + "/" + id.getPath() + "/" + index);
	}

	private static Optional<Holder.Reference<MobEffect>> effect(String id) {
		return BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(id));
	}

	private static AttributeInstance attribute(ServerPlayer player, String id) {
		return BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(id))
				.map(holder -> player.getAttribute((Holder<Attribute>) holder))
				.orElse(null);
	}

	private static AttributeModifier.Operation operation(String name) {
		for (AttributeModifier.Operation operation : AttributeModifier.Operation.values()) {
			if (operation.getSerializedName().equals(name)) {
				return operation;
			}
		}
		return null;
	}

	/** Forces class loading so the attachments register during init. */
	public static void init() {
	}
}
