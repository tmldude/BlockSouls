package io.github.tmldude.blocksouls.abilities.test;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.abilities.AbilityIds;
import io.github.tmldude.blocksouls.abilities.AbilityServer;
import io.github.tmldude.blocksouls.abilities.BlockSoulsAbilities;
import io.github.tmldude.blocksouls.abilities.effect.AbilityDamage;
import io.github.tmldude.blocksouls.common.ability.Abilities;
import io.github.tmldude.blocksouls.common.ability.AbilitySlot;
import io.github.tmldude.blocksouls.common.party.PartyScaling;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;

/** One ability per slot, the cooldown / charge gates, kill charge, and the party-scaled heals at 1 and 8 players. */
public class AbilitiesGameTests {
	private static ServerPlayer caster(GameTestHelper helper, Vec3 feet, Vec3 lookAt) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(feet);
		player.snapTo(at.x, at.y, at.z, 0, 0);
		player.setOnGround(true);
		aim(player, lookAt);
		AbilityServer.ensureKit(player);
		Abilities.clearCooldowns(player);
		Abilities.setUltimateCharge(player, 0);
		return player;
	}

	private static void aim(ServerPlayer player, Vec3 target) {
		Vec3 d = target.subtract(player.getEyePosition());
		float yRot = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
		float xRot = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		player.setYRot(yRot);
		player.setXRot(xRot);
		player.setYHeadRot(yRot);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(x, 1, z));
		zombie.setNoGravity(true);
		zombie.setBaby(false);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		return zombie;
	}

	private static Vec3 body(Zombie zombie) {
		return zombie.position().add(0, 0.9, 0);
	}

	private static double healScale(ServerPlayer player) {
		return PartyScaling.abilityHeal(PartyScaling.partySize(player.level().players().size()));
	}

	@GameTest
	public void joiningFillsEverySlotWithTheDefaults(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		AbilityServer.ensureKit(player);
		helper.assertTrue(AbilityIds.FRAG.equals(Abilities.equipped(player, AbilitySlot.TACTICAL)), "default tactical");
		helper.assertTrue(AbilityIds.FAN_THE_HAMMER.equals(Abilities.equipped(player, AbilitySlot.SIGNATURE)), "default signature (no origin)");
		helper.assertTrue(AbilityIds.STORM_BARRAGE.equals(Abilities.equipped(player, AbilitySlot.ULTIMATE)), "default ultimate");
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void fragBlastsTheZombieAndStartsTheCooldown(GameTestHelper helper) {
		Zombie zombie = zombie(helper, 2.5, 6.5);
		ServerPlayer player = caster(helper, new Vec3(2.5, 1, 1.5), body(zombie));
		float before = zombie.getHealth();

		helper.assertTrue(AbilityServer.tryCast(player, AbilitySlot.TACTICAL), "frag went off");
		helper.assertTrue(!Abilities.isReady(player, AbilitySlot.TACTICAL), "tactical on cooldown");
		helper.assertTrue(Abilities.remainingTicks(player, AbilitySlot.TACTICAL) == BlockSoulsAbilities.CONFIG.tactical.cooldownTicks, "full cooldown");
		helper.assertTrue(!AbilityServer.tryCast(player, AbilitySlot.TACTICAL), "second cast refused");
		helper.runAfterDelay(BlockSoulsAbilities.CONFIG.tactical.frag.fuseTicks + 10, () -> {
			helper.assertTrue(zombie.getHealth() < before, "zombie took blast damage (" + before + " -> " + zombie.getHealth() + ")");
			helper.succeed();
		});
	}

	@GameTest
	public void fanTheHammerHitsTheZombieInFront(GameTestHelper helper) {
		Zombie zombie = zombie(helper, 2.5, 6.5);
		ServerPlayer player = caster(helper, new Vec3(2.5, 1, 1.5), body(zombie));
		Abilities.equip(player, AbilitySlot.SIGNATURE, AbilityIds.FAN_THE_HAMMER);
		float before = zombie.getHealth();

		helper.assertTrue(AbilityServer.tryCast(player, AbilitySlot.SIGNATURE), "signature went off");
		helper.assertTrue(zombie.getHealth() < before, "zombie was shot (" + before + " -> " + zombie.getHealth() + ")");
		helper.assertTrue(!Abilities.isReady(player, AbilitySlot.SIGNATURE), "signature on cooldown");
		helper.succeed();
	}

	@GameTest
	public void webPullFizzlesWithNothingToGrab(GameTestHelper helper) {
		ServerPlayer player = caster(helper, new Vec3(2.5, 1, 2.5), helper.absoluteVec(new Vec3(2.5, 60, 2.5)));
		Abilities.equip(player, AbilitySlot.SIGNATURE, AbilityIds.WEB_PULL);
		helper.assertTrue(!AbilityServer.tryCast(player, AbilitySlot.SIGNATURE), "nothing in the line: no cast");
		helper.assertTrue(Abilities.isReady(player, AbilitySlot.SIGNATURE), "a fizzle costs no cooldown");
		helper.succeed();
	}

	@GameTest(maxTicks = 120)
	public void stormBarrageNeedsFullChargeAndSpendsIt(GameTestHelper helper) {
		Zombie zombie = zombie(helper, 4.5, 4.5);
		ServerPlayer player = caster(helper, new Vec3(2.5, 1, 2.5), body(zombie));
		Abilities.equip(player, AbilitySlot.ULTIMATE, AbilityIds.STORM_BARRAGE);
		helper.assertTrue(!AbilityServer.tryCast(player, AbilitySlot.ULTIMATE), "no charge: no cast");

		Abilities.setUltimateCharge(player, 1.0);
		float before = zombie.getHealth();
		helper.assertTrue(AbilityServer.tryCast(player, AbilitySlot.ULTIMATE), "charged: cast");
		helper.assertTrue(Abilities.ultimateCharge(player) == 0, "charge spent");
		helper.runAfterDelay(BlockSoulsAbilities.CONFIG.ultimate.stormBarrage.strikeEveryTicks * 3 + 5, () -> {
			helper.assertTrue(zombie.getHealth() < before, "the storm struck the zombie (" + before + " -> " + zombie.getHealth() + ")");
			helper.succeed();
		});
	}

	@GameTest
	public void killsChargeTheUltimate(GameTestHelper helper) {
		Zombie zombie = zombie(helper, 2.5, 5.5);
		ServerPlayer player = caster(helper, new Vec3(2.5, 1, 1.5), body(zombie));
		helper.assertTrue(Abilities.ultimateCharge(player) == 0, "starts empty");
		zombie.hurtServer(helper.getLevel(), AbilityDamage.source(player), 1000);
		helper.assertTrue(!zombie.isAlive(), "zombie died");
		double charge = Abilities.ultimateCharge(player);
		double expected = BlockSoulsAbilities.CONFIG.charge.perKill * PartyScaling.ultimateCharge(player.level().players().size());
		helper.assertTrue(Math.abs(charge - expected) < 1e-6, "one kill's charge: expected " + expected + ", got " + charge);
		helper.succeed();
	}

	@GameTest
	public void downedPlayersCannotCast(GameTestHelper helper) {
		ServerPlayer player = caster(helper, new Vec3(2.5, 1, 2.5), helper.absoluteVec(new Vec3(2.5, 2, 6)));
		PlayerStatus.setDowned(player, true);
		helper.assertTrue(!AbilityServer.tryCast(player, AbilitySlot.TACTICAL), "no tactical while downed");
		helper.assertTrue(Abilities.isReady(player, AbilitySlot.TACTICAL), "nothing spent");
		PlayerStatus.setDowned(player, false);
		helper.succeed();
	}

	@GameTest
	public void healingDartHealsTheAllyAimedAt(GameTestHelper helper) {
		ServerPlayer ally = helper.makeMockServerPlayerInLevel();
		Vec3 allyAt = helper.absoluteVec(new Vec3(2.5, 1, 6.5));
		ally.snapTo(allyAt.x, allyAt.y, allyAt.z, 0, 0);
		ally.setHealth(6);
		ServerPlayer player = caster(helper, new Vec3(2.5, 1, 1.5), ally.getEyePosition().add(0, -0.4, 0));
		Abilities.equip(player, AbilitySlot.TACTICAL, AbilityIds.HEALING_DART);
		float mine = player.getHealth();

		helper.assertTrue(AbilityServer.tryCast(player, AbilitySlot.TACTICAL), "dart fired");
		float expected = (float) Math.min(ally.getMaxHealth(), 6 + BlockSoulsAbilities.CONFIG.tactical.healingDart.heal * healScale(player));
		helper.assertTrue(Math.abs(ally.getHealth() - expected) < 0.01, "ally healed to " + expected + ", got " + ally.getHealth());
		helper.assertTrue(player.getHealth() == mine, "the caster isn't healed when an ally is hit");
		helper.succeed();
	}

	@GameTest
	public void overshieldScalesWithPartySizeUpToEight(GameTestHelper helper) {
		ServerPlayer player = caster(helper, new Vec3(2.5, 1, 2.5), helper.absoluteVec(new Vec3(2.5, 2, 6)));
		Abilities.equip(player, AbilitySlot.ULTIMATE, AbilityIds.PARTY_OVERSHIELD);
		double base = BlockSoulsAbilities.CONFIG.ultimate.partyOvershield.absorption;

		Abilities.setUltimateCharge(player, 1.0);
		int few = player.level().players().size();
		helper.assertTrue(AbilityServer.tryCast(player, AbilitySlot.ULTIMATE), "first cast");
		float first = player.getAbsorptionAmount();
		float expectedFew = (float) (base * PartyScaling.abilityHeal(PartyScaling.partySize(few)));
		helper.assertTrue(Math.abs(first - expectedFew) < 0.01, "absorption for " + few + " player(s): " + expectedFew + ", got " + first);

		// Fill the party to eight (or more: the curve clamps at maxParty).
		ServerPlayer[] party = new ServerPlayer[7];
		for (int i = 0; i < party.length; i++) {
			party[i] = helper.makeMockServerPlayerInLevel();
			Vec3 at = helper.absoluteVec(new Vec3(1.5 + i % 4, 1, 4.5 + i / 4));
			party[i].snapTo(at.x, at.y, at.z, 0, 0);
		}
		int many = player.level().players().size();
		Abilities.setUltimateCharge(player, 1.0);
		helper.assertTrue(AbilityServer.tryCast(player, AbilitySlot.ULTIMATE), "second cast");
		float expectedMany = (float) (base * PartyScaling.abilityHeal(PartyScaling.partySize(many)));
		helper.assertTrue(Math.abs(player.getAbsorptionAmount() - expectedMany) < 0.01,
				"absorption for " + many + " players: " + expectedMany + ", got " + player.getAbsorptionAmount());
		helper.assertTrue(PartyScaling.partySize(many) <= PartyScaling.partySize(few) || expectedMany > expectedFew, "a bigger party shields more");
		for (ServerPlayer ally : party) {
			helper.assertTrue(Math.abs(ally.getAbsorptionAmount() - expectedMany) < 0.01, "every ally in range is shielded");
		}
		helper.succeed();
	}
}
