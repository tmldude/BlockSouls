package io.github.tmldude.blocksouls.game.player.boon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.event.PlayerRunEvents;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.PlayersConfig;

/**
 * The boons a player owns ({@code id → level}), saved with the player and not copied on death. Effects are
 * permanent while owned (re-applied if removed), attribute modifiers and traits follow the owned set, items are
 * handed over once when first granted. Going down clears everything except granted items (they're gear).
 */
public final class PlayerBoons {
	private static final AttachmentType<Map<Identifier, Integer>> OWNED = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "boons"),
			builder -> builder.persistent(Codec.unboundedMap(Identifier.CODEC, Codec.INT)));

	private static PlayersConfig.Boons config;
	private static int reapplyTimer;

	private PlayerBoons() {
	}

	public static void register(PlayersConfig.Boons boonsConfig) {
		config = boonsConfig;
		Networking.registerClientbound(BoonPayloads.Owned.TYPE, BoonPayloads.Owned.CODEC);

		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> refresh(listener.player));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++reapplyTimer >= Math.max(1, config.reapplyIntervalTicks)) {
				reapplyTimer = 0;
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (((AttachmentTarget) player).hasAttached(OWNED)) {
						applyEffects(player);
					}
				}
			}
		});
	}

	/** After a data reload: boon definitions may have changed. */
	public static void refreshAll(MinecraftServer server) {
		server.getPlayerList().getPlayers().forEach(PlayerBoons::refresh);
	}

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

	/** Adds a level of {@code boon} (up to its max). Returns false if it's already maxed. */
	public static boolean grant(ServerPlayer player, Boon boon) {
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

	/** Removes every boon and what it applied. Granted items stay. */
	public static void clearAll(ServerPlayer player) {
		Map<Identifier, Integer> owned = owned(player);
		if (owned.isEmpty() && PlayerTraits.all(player).isEmpty()) {
			return;
		}

		for (Map.Entry<Identifier, Integer> entry : owned.entrySet()) {
			Boon boon = BoonCatalog.get(entry.getKey());
			if (boon == null) {
				continue;
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

		((AttachmentTarget) player).removeAttached(OWNED);
		PlayerTraits.clear(player);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
		sync(player);
	}

	/** Re-applies effects, modifiers and traits from the owned set, and syncs the HUD. */
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

	/** Sends the HUD row: owned boons minus the parts of owned combos, combos first, then by tier and id. */
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
		Networking.sendToPlayer(player, new BoonPayloads.Owned(views));
	}

	@FunctionalInterface
	private interface EffectVisitor {
		void visit(Boon boon, int level, int index, BoonEffect effect);
	}

	private static void forEachEffect(ServerPlayer player, EffectVisitor visitor) {
		owned(player).forEach((id, level) -> {
			Boon boon = BoonCatalog.get(id);
			if (boon == null) {
				return;
			}
			for (int i = 0; i < boon.effects().size(); i++) {
				visitor.visit(boon, level, i, boon.effects().get(i));
			}
		});
	}

	static Identifier modifierId(Boon boon, int index) {
		Identifier id = Identifier.parse(boon.id());
		return Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "boon/" + id.getNamespace() + "/" + id.getPath() + "/" + index);
	}

	private static java.util.Optional<Holder.Reference<MobEffect>> effect(String id) {
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

	/** Forces class loading so the attachment registers during init. */
	public static void init() {
	}
}
