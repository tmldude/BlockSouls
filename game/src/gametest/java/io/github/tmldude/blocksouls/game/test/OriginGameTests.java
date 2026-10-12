package io.github.tmldude.blocksouls.game.test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.event.BossEvents;
import io.github.tmldude.blocksouls.common.player.Capabilities;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.game.player.PlayerSystem;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;
import io.github.tmldude.blocksouls.game.player.origin.Origin;
import io.github.tmldude.blocksouls.game.player.origin.OriginCatalog;
import io.github.tmldude.blocksouls.game.player.origin.OriginEffect;
import io.github.tmldude.blocksouls.game.player.origin.OriginSigilItem;
import io.github.tmldude.blocksouls.game.player.origin.PlayerOrigins;
import io.github.tmldude.blocksouls.game.run.RunDebug;
import io.github.tmldude.blocksouls.game.run.RunSystem;

/**
 * Every shipped origin: it loads, a player can take it, the movement verbs are exactly the declared signature,
 * the kit lands, passives and drawbacks apply, and clearing it restores the all-granted v1 player.
 */
public class OriginGameTests {
	@GameTest
	public void drifter(GameTestHelper helper) {
		check(helper, "drifter", Set.of(Capabilities.MULTI_JUMP));
	}

	@GameTest
	public void webslinger(GameTestHelper helper) {
		check(helper, "webslinger", Set.of(Capabilities.CLIMB, Capabilities.SWING));
	}

	@GameTest
	public void tidecaller(GameTestHelper helper) {
		check(helper, "tidecaller", Set.of());
	}

	@GameTest
	public void ashborn(GameTestHelper helper) {
		check(helper, "ashborn", Set.of());
	}

	@GameTest
	public void zephyr(GameTestHelper helper) {
		check(helper, "zephyr", Set.of(Capabilities.GLIDE));
	}

	@GameTest
	public void juggernaut(GameTestHelper helper) {
		check(helper, "juggernaut", Set.of());
	}

	@GameTest
	public void gunslinger(GameTestHelper helper) {
		check(helper, "gunslinger", Set.of());
	}

	@GameTest
	public void tinkerer(GameTestHelper helper) {
		check(helper, "tinkerer", Set.of());
	}

	@GameTest
	public void bloodbound(GameTestHelper helper) {
		check(helper, "bloodbound", Set.of());
	}

	@GameTest
	public void gambler(GameTestHelper helper) {
		check(helper, "gambler", Set.of());
	}

	@GameTest
	public void tenOriginsLoad(GameTestHelper helper) {
		helper.assertTrue(OriginCatalog.all().size() == 10, "ten seeds loaded, got " + OriginCatalog.all().size());
		helper.succeed();
	}

	@GameTest
	public void originTraitsSurviveABoonRefresh(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(PlayerOrigins.choose(player, id("drifter")), "drifter");
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.DOUBLE_JUMP) == 1, "the innate air jump is a trait on top of boons");
		// A boon refresh rebuilds the trait map from boons alone; the origin layer puts its share back.
		PlayerBoons.refresh(player);
		PlayerOrigins.apply(player);
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.DOUBLE_JUMP) == 1, "restored after a refresh");
		PlayerOrigins.clear(player);
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.DOUBLE_JUMP) == 0, "gone with the origin");
		helper.succeed();
	}

	@GameTest
	public void bloodboundGainsAHeartPerBossKillUpToTheCap(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		double base = player.getAttributeValue(Attributes.MAX_HEALTH);
		helper.assertTrue(PlayerOrigins.choose(player, id("bloodbound")), "bloodbound");
		int cap = PlayerSystem.CONFIG.origins.bossKillHeartsCap;
		for (int i = 0; i < cap + 3; i++) {
			BossEvents.KILLED.invoker().onBoss(helper.getLevel(), CommonIds.FURNACE_KNIGHT, player, List.of(player));
		}
		double gained = player.getAttributeValue(Attributes.MAX_HEALTH) - base;
		helper.assertTrue(gained == cap * 2.0, "+1 heart per kill, capped at " + cap + ", got +" + gained / 2 + " hearts");
		PlayerOrigins.clear(player);
		helper.assertTrue(player.getAttributeValue(Attributes.MAX_HEALTH) == base, "hearts leave with the origin");
		helper.succeed();
	}

	@GameTest
	public void runStartClearsOriginsAndHandsOutSigils(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		boolean enabled = PlayerSystem.CONFIG.origins.enabled;
		boolean autoPick = PlayerSystem.CONFIG.origins.soloAutoPick;
		try {
			PlayerSystem.CONFIG.origins.enabled = true;
			PlayerSystem.CONFIG.origins.soloAutoPick = true;
			PlayerOrigins.choose(player, id("gambler"));
			RunDebug.reset(server);
			RunSystem.startRun(server, null);
			boolean solo = server.getPlayerList().getPlayerCount() == 1;
			Origin origin = PlayerOrigins.get(player);
			if (solo) {
				helper.assertTrue(origin != null && origin.id().equals(PlayerSystem.CONFIG.origins.soloDefault), "solo starts on the default origin");
			} else {
				helper.assertTrue(origin == null, "a new run drops the old origin until the party picks");
			}
			helper.assertTrue(count(player, OriginSigilItem.INSTANCE) >= 1, "a sigil at run start");
		} finally {
			RunDebug.reset(server);
			PlayerOrigins.clear(player);
			PlayerSystem.CONFIG.origins.enabled = enabled;
			PlayerSystem.CONFIG.origins.soloAutoPick = autoPick;
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the per-origin check

	private static void check(GameTestHelper helper, String name, Set<Identifier> signature) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Origin origin = OriginCatalog.get(id(name));
		helper.assertTrue(origin != null, "origin " + name + " loaded");
		helper.assertTrue(Capabilities.allGranted(player), "fresh player is all-granted");

		helper.assertTrue(PlayerOrigins.choose(player, id(name)), "can take " + name);
		helper.assertTrue(PlayerOrigins.get(player) == origin, "holds " + name);
		helper.assertTrue(signature.equals(Capabilities.granted(player)), name + " verbs are exactly " + signature + ", got " + Capabilities.granted(player));
		helper.assertTrue(signature.equals(origin.capabilities().stream().map(Identifier::parse).collect(Collectors.toSet())), "the JSON declares the same signature");
		for (Identifier verb : Capabilities.V1_VERBS) {
			helper.assertTrue(Capabilities.has(player, verb) == signature.contains(verb), verb + " gated");
		}
		helper.assertTrue(Capabilities.has(player, Capabilities.GLIDE) == signature.contains(Capabilities.GLIDE), "glide gated");

		assertEffects(helper, player, origin, true);
		for (Origin.KitEntry entry : origin.kit()) {
			entry.item().ifPresent(item -> {
				Identifier itemId = Identifier.parse(item);
				if (BuiltInRegistries.ITEM.containsKey(itemId)) {
					helper.assertTrue(count(player, BuiltInRegistries.ITEM.getValue(itemId)) >= entry.count(), "kit item " + item);
				}
			});
		}

		// Taking it twice never hands the kit over twice.
		int before = inventorySize(player);
		PlayerOrigins.choose(player, id(name));
		helper.assertTrue(inventorySize(player) == before, "no second kit on a re-pick");

		PlayerOrigins.clear(player);
		helper.assertTrue(Capabilities.allGranted(player), "cleared: all v1 verbs back");
		helper.assertTrue(PlayerOrigins.get(player) == null, "cleared: no origin");
		assertEffects(helper, player, origin, false);
		helper.succeed();
	}

	@SuppressWarnings("unchecked")
	private static void assertEffects(GameTestHelper helper, ServerPlayer player, Origin origin, boolean present) {
		var effects = origin.effects();
		for (int i = 0; i < effects.size(); i++) {
			switch (effects.get(i)) {
				case OriginEffect.Attribute a -> {
					AttributeInstance attribute = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(a.attribute()))
							.map(holder -> player.getAttribute((Holder<Attribute>) holder)).orElse(null);
					helper.assertTrue(attribute != null, "attribute " + a.attribute());
					Identifier modifier = Identifier.fromNamespaceAndPath("blocksouls_game", "origin/blocksouls_game/" + origin.path() + "/" + i);
					helper.assertTrue(attribute.hasModifier(modifier) == present, origin.id() + " modifier " + i + (present ? " applied" : " removed"));
				}
				case OriginEffect.Effect e -> {
					if (e.when() == OriginEffect.When.ALWAYS) {
						boolean has = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(e.effect())).map(player::hasEffect).orElse(false);
						helper.assertTrue(has == present, origin.id() + " effect " + e.effect() + (present ? " applied" : " removed"));
					}
				}
				case OriginEffect.Trait t -> helper.assertTrue(
						(PlayerTraits.level(player, Identifier.parse(t.trait())) >= t.level()) == present,
						origin.id() + " trait " + t.trait() + (present ? " applied" : " removed"));
			}
		}
	}

	private static int count(ServerPlayer player, Item item) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static int inventorySize(ServerPlayer player) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			count += player.getInventory().getItem(i).getCount();
		}
		return count;
	}

	private static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath("blocksouls_game", name);
	}
}
