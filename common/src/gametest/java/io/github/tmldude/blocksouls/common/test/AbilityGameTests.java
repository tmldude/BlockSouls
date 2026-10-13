package io.github.tmldude.blocksouls.common.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.ability.Abilities;
import io.github.tmldude.blocksouls.common.ability.AbilityDefinition;
import io.github.tmldude.blocksouls.common.ability.AbilityRegistry;
import io.github.tmldude.blocksouls.common.ability.AbilitySlot;
import io.github.tmldude.blocksouls.common.ability.AbilityState;
import io.github.tmldude.blocksouls.common.event.AbilityEvents;
import io.github.tmldude.blocksouls.common.synergy.SynergyTags;

/** The ability contract on its own: the registry, the per-player state and the events, with no abilities module. */
public class AbilityGameTests {
	private static final Identifier TEST_TACTICAL = Identifier.fromNamespaceAndPath("blocksouls_common_test", "test_tactical");
	private static final Identifier TEST_ULTIMATE = Identifier.fromNamespaceAndPath("blocksouls_common_test", "test_ultimate");

	private static void registerTestKit() {
		AbilityRegistry.register(AbilityDefinition.cooldown(TEST_TACTICAL, AbilitySlot.TACTICAL, 100, SynergyTags.EXPLOSION));
		AbilityRegistry.register(AbilityDefinition.ultimate(TEST_ULTIMATE, SynergyTags.STORM));
	}

	@GameTest
	public void stateDefaultsToEmpty(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(Abilities.state(player) == AbilityState.EMPTY, "no state attached by default");
		helper.assertTrue(Abilities.equipped(player, AbilitySlot.TACTICAL) == null, "empty slot");
		helper.assertTrue(!Abilities.isReady(player, AbilitySlot.TACTICAL), "an empty slot is never ready");
		helper.assertTrue(Abilities.ultimateCharge(player) == 0, "no charge");
		helper.succeed();
	}

	@GameTest
	public void equipAndCooldown(GameTestHelper helper) {
		registerTestKit();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Abilities.equip(player, AbilitySlot.TACTICAL, TEST_TACTICAL);
		helper.assertTrue(TEST_TACTICAL.equals(Abilities.equipped(player, AbilitySlot.TACTICAL)), "equipped");
		helper.assertTrue(Abilities.definition(player, AbilitySlot.TACTICAL) != null, "definition resolves");
		helper.assertTrue(Abilities.isReady(player, AbilitySlot.TACTICAL), "ready before any cast");

		Abilities.startCooldown(player, AbilitySlot.TACTICAL, 100);
		helper.assertTrue(!Abilities.isReady(player, AbilitySlot.TACTICAL), "on cooldown after a cast");
		AbilityState.Cooldown cooldown = Abilities.cooldown(player, AbilitySlot.TACTICAL);
		helper.assertTrue(cooldown != null && cooldown.total() == 100, "cooldown stamped with its length");
		helper.assertTrue(Abilities.remainingTicks(player, AbilitySlot.TACTICAL) == 100, "100 ticks remaining");

		Abilities.clearCooldowns(player);
		helper.assertTrue(Abilities.isReady(player, AbilitySlot.TACTICAL), "cleared");
		helper.assertTrue(TEST_TACTICAL.equals(Abilities.equipped(player, AbilitySlot.TACTICAL)), "clearing keeps the kit");
		helper.succeed();
	}

	@GameTest
	public void ultimateChargeFiresReadyOnce(GameTestHelper helper) {
		registerTestKit();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Abilities.equip(player, AbilitySlot.ULTIMATE, TEST_ULTIMATE);
		List<ServerPlayer> ready = new ArrayList<>();
		AbilityEvents.ULTIMATE_READY.register(p -> {
			if (p == player) {
				ready.add(p);
			}
		});

		helper.assertTrue(!Abilities.addUltimateCharge(player, 0.6), "60%: not ready");
		helper.assertTrue(!Abilities.isReady(player, AbilitySlot.ULTIMATE), "slot not ready");
		helper.assertTrue(Abilities.addUltimateCharge(player, 0.6), "120%: ready");
		helper.assertTrue(Abilities.ultimateCharge(player) == 1.0, "charge clamps to 1");
		helper.assertTrue(Abilities.isReady(player, AbilitySlot.ULTIMATE), "slot ready at full charge");
		helper.assertTrue(Abilities.addUltimateCharge(player, 0.5), "still ready");
		helper.assertTrue(ready.size() == 1, "ULTIMATE_READY fired exactly once, got " + ready.size());

		Abilities.setUltimateCharge(player, 0);
		helper.assertTrue(!Abilities.isReady(player, AbilitySlot.ULTIMATE), "spent");
		helper.assertTrue(Abilities.setUltimateCharge(player, 1), "refilled");
		helper.assertTrue(ready.size() == 2, "fires again after a spend");
		helper.succeed();
	}

	@GameTest
	public void castRequestReachesListeners(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<AbilitySlot> requested = new ArrayList<>();
		AbilityEvents.CAST_REQUESTED.register((p, slot) -> {
			if (p == player) {
				requested.add(slot);
			}
		});
		Abilities.requestCast(player, AbilitySlot.SIGNATURE);
		helper.assertTrue(requested.equals(List.of(AbilitySlot.SIGNATURE)), "the request was delivered");
		helper.succeed();
	}

	@GameTest
	public void registryListsBySlot(GameTestHelper helper) {
		registerTestKit();
		helper.assertTrue(AbilityRegistry.isKnown(TEST_TACTICAL) && AbilityRegistry.isKnown(TEST_ULTIMATE), "both registered");
		helper.assertTrue(AbilityRegistry.bySlot(AbilitySlot.ULTIMATE).stream().anyMatch(d -> d.id().equals(TEST_ULTIMATE)), "ultimate listed");
		helper.assertTrue(AbilityRegistry.bySlot(AbilitySlot.TACTICAL).stream().noneMatch(d -> d.id().equals(TEST_ULTIMATE)), "not in the wrong slot");
		AbilityDefinition shorter = AbilityRegistry.register(AbilityRegistry.get(TEST_TACTICAL).withCooldown(40));
		helper.assertTrue(AbilityRegistry.get(TEST_TACTICAL).cooldownTicks() == 40 && shorter.tags().contains(SynergyTags.EXPLOSION),
				"re-registering replaces the definition and keeps its tags");
		helper.assertTrue(AbilitySlot.byName("signature") == AbilitySlot.SIGNATURE && AbilitySlot.byName("nope") == null, "slot names");
		helper.succeed();
	}
}
