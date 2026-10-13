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
import io.github.tmldude.blocksouls.game.player.classes.ClassCatalog;
import io.github.tmldude.blocksouls.game.player.classes.ClassEffect;
import io.github.tmldude.blocksouls.game.player.classes.ClassSigilItem;
import io.github.tmldude.blocksouls.game.player.classes.PlayerClass;
import io.github.tmldude.blocksouls.game.player.classes.PlayerClasses;
import io.github.tmldude.blocksouls.game.run.RunDebug;
import io.github.tmldude.blocksouls.game.run.RunSystem;

/**
 * Every shipped class: it loads, a player can take it, the universal movement kit stays on with the signature verbs
 * added, the kit lands, passives and drawbacks apply, and clearing it restores the plain v1 player.
 */
public class ClassGameTests {
	@GameTest
	public void warden(GameTestHelper helper) {
		check(helper, "warden", Set.of());
	}

	@GameTest
	public void striker(GameTestHelper helper) {
		check(helper, "striker", Set.of());
	}

	@GameTest
	public void arcanist(GameTestHelper helper) {
		check(helper, "arcanist", Set.of());
	}

	@GameTest
	public void ranger(GameTestHelper helper) {
		check(helper, "ranger", Set.of());
	}

	@GameTest
	public void fourClassesLoad(GameTestHelper helper) {
		helper.assertTrue(ClassCatalog.all().size() == 4, "four classes loaded, got " + ClassCatalog.all().size());
		helper.succeed();
	}

	@GameTest
	public void classTraitsSurviveABoonRefresh(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(PlayerClasses.choose(player, id("ranger")), "ranger");
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.RELOAD_SPEED) == 1, "the class reload trait sits on top of boons");
		// A boon refresh rebuilds the trait map from boons alone; the class layer puts its share back.
		PlayerBoons.refresh(player);
		PlayerClasses.apply(player);
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.RELOAD_SPEED) == 1, "restored after a refresh");
		PlayerClasses.clear(player);
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.RELOAD_SPEED) == 0, "gone with the class");
		helper.succeed();
	}

	@GameTest
	public void strikerGainsAHeartPerBossKillUpToTheCap(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		double base = player.getAttributeValue(Attributes.MAX_HEALTH);
		helper.assertTrue(PlayerClasses.choose(player, id("striker")), "striker");
		int cap = PlayerSystem.CONFIG.classes.bossKillHeartsCap;
		for (int i = 0; i < cap + 3; i++) {
			BossEvents.KILLED.invoker().onBoss(helper.getLevel(), CommonIds.FURNACE_KNIGHT, player, List.of(player));
		}
		double gained = player.getAttributeValue(Attributes.MAX_HEALTH) - base;
		helper.assertTrue(gained == cap * 2.0, "+1 heart per kill, capped at " + cap + ", got +" + gained / 2 + " hearts");
		PlayerClasses.clear(player);
		helper.assertTrue(player.getAttributeValue(Attributes.MAX_HEALTH) == base, "hearts leave with the class");
		helper.succeed();
	}

	@GameTest
	public void signatureVerbsAddToTheUniversalKitAndRuneGrantsSurviveAPick(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Capabilities.grant(player, Capabilities.GLIDE); // a rune's grant
		helper.assertTrue(PlayerClasses.choose(player, id("warden")), "warden");
		for (Identifier verb : Capabilities.V1_VERBS) {
			helper.assertTrue(Capabilities.has(player, verb), "the universal kit stays on: " + verb);
		}
		PlayerClasses.apply(player);
		helper.assertTrue(Capabilities.has(player, Capabilities.GLIDE), "re-applying the class never removes a granted verb");
		PlayerClasses.clear(player);
		helper.assertTrue(Capabilities.allGranted(player), "cleared: the plain v1 player");
		helper.succeed();
	}

	@GameTest
	public void runStartClearsClassesAndHandsOutSigils(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		boolean enabled = PlayerSystem.CONFIG.classes.enabled;
		boolean autoPick = PlayerSystem.CONFIG.classes.soloAutoPick;
		try {
			PlayerSystem.CONFIG.classes.enabled = true;
			PlayerSystem.CONFIG.classes.soloAutoPick = true;
			PlayerClasses.choose(player, id("arcanist"));
			RunDebug.reset(server);
			RunSystem.startRun(server, null);
			boolean solo = server.getPlayerList().getPlayerCount() == 1;
			PlayerClass playerClass = PlayerClasses.get(player);
			if (solo) {
				helper.assertTrue(playerClass != null && playerClass.id().equals(PlayerSystem.CONFIG.classes.soloDefault), "solo starts on the default class");
			} else {
				helper.assertTrue(playerClass == null, "a new run drops the old class until the party picks");
			}
			helper.assertTrue(count(player, ClassSigilItem.INSTANCE) >= 1, "a sigil at run start");
		} finally {
			RunDebug.reset(server);
			PlayerClasses.clear(player);
			PlayerSystem.CONFIG.classes.enabled = enabled;
			PlayerSystem.CONFIG.classes.soloAutoPick = autoPick;
		}
		helper.succeed();
	}

	@GameTest
	public void wardenSlamLandingsCostNoHealthButShortFallsStillHurt(GameTestHelper helper) {
		ServerPlayer player = DownedTestPlayers.make(helper, 1, 1);
		helper.assertTrue(PlayerClasses.choose(player, id("warden")), "warden");
		double minFall = PlayerClasses.rules(player).slamMinFall();
		helper.assertTrue(minFall > 0, "the warden slams");
		float health = player.getHealth();
		player.fallDistance = minFall + 2;
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().fall(), 4.0F);
		helper.assertTrue(player.getHealth() == health, "a slam landing forgives the fall");
		player.fallDistance = 0;
		player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().fall(), 4.0F);
		helper.assertTrue(player.getHealth() < health, "fall damage outside a slam still lands");
		PlayerClasses.clear(player);
		helper.assertTrue(!PlayerClasses.rules(player).slams(), "no class, no slam");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the per-class check

	private static void check(GameTestHelper helper, String name, Set<Identifier> signature) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PlayerClass playerClass = ClassCatalog.get(id(name));
		helper.assertTrue(playerClass != null, "class " + name + " loaded");
		helper.assertTrue(Capabilities.allGranted(player), "fresh player is the plain v1 player");

		helper.assertTrue(PlayerClasses.choose(player, id(name)), "can take " + name);
		helper.assertTrue(PlayerClasses.get(player) == playerClass, "holds " + name);
		helper.assertTrue(signature.equals(playerClass.capabilities().stream().map(Identifier::parse).collect(Collectors.toSet())), "the JSON declares " + signature);
		for (Identifier verb : Capabilities.V1_VERBS) {
			helper.assertTrue(Capabilities.has(player, verb), name + " keeps the universal kit: " + verb);
		}
		for (Identifier verb : signature) {
			helper.assertTrue(Capabilities.has(player, verb), name + " adds " + verb);
		}
		helper.assertTrue(Capabilities.has(player, Capabilities.GLIDE) == signature.contains(Capabilities.GLIDE), "glide only as a signature or rune");

		assertEffects(helper, player, playerClass, true);
		for (PlayerClass.KitEntry entry : playerClass.kit()) {
			entry.item().ifPresent(item -> {
				Identifier itemId = Identifier.parse(item);
				if (BuiltInRegistries.ITEM.containsKey(itemId)) {
					helper.assertTrue(count(player, BuiltInRegistries.ITEM.getValue(itemId)) >= entry.count(), "kit item " + item);
				}
			});
		}

		// Taking it twice never hands the kit over twice.
		int before = inventorySize(player);
		PlayerClasses.choose(player, id(name));
		helper.assertTrue(inventorySize(player) == before, "no second kit on a re-pick");

		PlayerClasses.clear(player);
		helper.assertTrue(Capabilities.allGranted(player), "cleared: the plain v1 player");
		helper.assertTrue(PlayerClasses.get(player) == null, "cleared: no class");
		assertEffects(helper, player, playerClass, false);
		helper.succeed();
	}

	@SuppressWarnings("unchecked")
	private static void assertEffects(GameTestHelper helper, ServerPlayer player, PlayerClass playerClass, boolean present) {
		var effects = playerClass.effects();
		for (int i = 0; i < effects.size(); i++) {
			switch (effects.get(i)) {
				case ClassEffect.Attribute a -> {
					AttributeInstance attribute = BuiltInRegistries.ATTRIBUTE.get(Identifier.parse(a.attribute()))
							.map(holder -> player.getAttribute((Holder<Attribute>) holder)).orElse(null);
					helper.assertTrue(attribute != null, "attribute " + a.attribute());
					Identifier modifier = Identifier.fromNamespaceAndPath("blocksouls_game", "class/blocksouls_game/" + playerClass.path() + "/" + i);
					helper.assertTrue(attribute.hasModifier(modifier) == present, playerClass.id() + " modifier " + i + (present ? " applied" : " removed"));
				}
				case ClassEffect.Effect e -> {
					if (e.when() == ClassEffect.When.ALWAYS) {
						boolean has = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(e.effect())).map(player::hasEffect).orElse(false);
						helper.assertTrue(has == present, playerClass.id() + " effect " + e.effect() + (present ? " applied" : " removed"));
					}
				}
				case ClassEffect.Trait t -> helper.assertTrue(
						(PlayerTraits.level(player, Identifier.parse(t.trait())) >= t.level()) == present,
						playerClass.id() + " trait " + t.trait() + (present ? " applied" : " removed"));
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
