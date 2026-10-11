package io.github.tmldude.blocksouls.bosses.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.BossDefinition;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.BossPart;
import io.github.tmldude.blocksouls.bosses.api.BossPhase;
import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.bosses.furnace.FurnaceKnight;
import io.github.tmldude.blocksouls.bosses.furnace.FurnaceKnights;
import io.github.tmldude.blocksouls.bosses.logic.BossScaling;
import io.github.tmldude.blocksouls.bosses.registry.BossBlocks;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.event.BossEvents;

public class BossesGameTests {
	/** Normal difficulty: the default test environment is peaceful. */
	private static final String ENV = "blocksouls_bosses_test:normal";
	private static final BlockPos CENTER = new BlockPos(4, 1, 4);

	/** Every KILLED event since the server started: (boss uuid, participant uuids). */
	private static final List<Kill> KILLS = new ArrayList<>();

	private record Kill(UUID boss, Identifier id, List<UUID> players) {
	}

	static {
		BossEvents.KILLED.register((level, bossId, boss, players) ->
				KILLS.add(new Kill(boss.getUUID(), bossId, players.stream().map(Entity::getUUID).toList())));
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** A Furnace Knight standing at the center with a small arena that fits the test area. */
	private static FurnaceKnight knight(GameTestHelper helper) {
		floor(helper);
		FurnaceKnight knight = helper.spawn(FurnaceKnights.TYPE, CENTER);
		knight.setHome(helper.absolutePos(CENTER));
		knight.setArenaRadius(3);
		return knight;
	}

	/** A mock player parked far outside the arena, so it never counts as "in the fight". */
	private static ServerPlayer outsider(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos far = helper.absolutePos(CENTER).offset(200, 0, 200);
		player.snapTo(far.getX(), far.getY(), far.getZ());
		return player;
	}

	/** A pig that can't be hurt or move: something for the boss to swing at forever. */
	private static Pig dummy(GameTestHelper helper, int x, int z) {
		Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(x, 1, z));
		pig.setNoAi(true);
		pig.setPermanentlyInvulnerable(true);
		return pig;
	}

	private static BlockPos wallSample(GameTestHelper helper) {
		return helper.absolutePos(CENTER).offset(3, 0, 0);
	}

	@GameTest(environment = ENV)
	public void furnaceKnightIsTaggedAndRegistered(GameTestHelper helper) {
		FurnaceKnight knight = knight(helper);
		helper.assertTrue(knight.is(CommonTags.BOSSES), "Furnace Knight missing from #blocksouls:bosses");
		helper.assertTrue(Bosses.type(CommonIds.FURNACE_KNIGHT).isPresent(), "not in the boss registry");
		helper.assertTrue(knight.getMaxHealth() >= FurnaceKnight.config().health - 0.01, "base health " + knight.getMaxHealth());
		helper.assertTrue(knight.fireImmune(), "Furnace Knight should be fire-immune");
		helper.succeed();
	}

	/** Every registered boss: hits over 30% of a player's health (at the strongest phase) get ≥ 0.75 s of windup. */
	@GameTest(environment = ENV)
	public void everyBigHitIsTelegraphed(GameTestHelper helper) {
		floor(helper);
		for (EntityType<? extends BossEntity> type : Bosses.all().values()) {
			BossEntity boss = helper.spawn(type, CENTER);
			BossDefinition definition = boss.definition();
			double strongest = definition.phases().stream().mapToDouble(BossPhase::damageMult).max().orElse(1);
			for (Attack attack : definition.attacks()) {
				double damage = attack.stats().damage * strongest;
				helper.assertTrue(BossScaling.readable(damage, attack.stats().windupTicks, 20, BossEntity.framework().bigHitShareOfHealth,
						BossEntity.framework().bigHitMinWindupTicks), definition.id() + " " + attack.name() + " hits for " + damage
						+ " with only " + attack.stats().windupTicks + " ticks of windup");
			}
			boss.discard();
		}
		helper.succeed();
	}

	/** Scripted health walks P1 → P2; attacks keep finishing in both phases (nothing gets stuck). */
	@GameTest(environment = ENV, maxTicks = 900)
	public void phaseMachineWalksToPhase2(GameTestHelper helper) {
		FurnaceKnight knight = knight(helper);
		Pig pig = dummy(helper, 6, 4);
		knight.engage(outsider(helper));
		knight.forceTarget(pig);
		knight.setHealth(knight.getMaxHealth() * 0.7f);
		int[] p1Attacks = {-1};
		int[] staggered = {0};
		helper.onEachTick(() -> {
			if (knight.isStaggered()) {
				staggered[0]++;
			}
		});
		helper.runAfterDelay(250, () -> {
			helper.assertTrue(knight.phase() == 0, "still phase 1 at 70%");
			helper.assertTrue(knight.attacksFinished() >= 2, "only " + knight.attacksFinished() + " attacks finished in phase 1");
			p1Attacks[0] = knight.attacksFinished();
			knight.setHealth(knight.getMaxHealth() * 0.4f);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(p1Attacks[0] >= 0, "phase 1 not done yet");
			helper.assertTrue(knight.phase() == 1, "not in phase 2 yet");
			helper.assertTrue(staggered[0] > 0, "no stagger on the phase change");
			helper.assertTrue(knight.attacksFinished() >= p1Attacks[0] + 2, "attacks stalled in phase 2");
			helper.assertTrue(knight.isEngaged(), "fight reset unexpectedly");
		});
	}

	@GameTest(environment = ENV)
	public void staggerIsInvulnerable(GameTestHelper helper) {
		FurnaceKnight knight = knight(helper);
		knight.engage(outsider(helper));
		knight.forcePhase(1);
		float before = knight.getHealth();
		knight.hurtServer(helper.getLevel(), helper.getLevel().damageSources().magic(), 20);
		helper.assertTrue(knight.isStaggered(), "phase change should stagger");
		helper.assertTrue(knight.getHealth() == before, "took damage while staggered");
		helper.succeed();
	}

	/** Engaging closes the fog wall; the kill opens it and fires KILLED with the participants. */
	@GameTest(environment = ENV, maxTicks = 60)
	public void engageClosesWallAndKillOpensIt(GameTestHelper helper) {
		FurnaceKnight knight = knight(helper);
		ServerPlayer player = outsider(helper);
		knight.engage(player);
		helper.assertTrue(knight.isEngaged(), "engage() didn't engage");
		helper.assertTrue(knight.fogWall().isUp(), "no fog wall");
		helper.assertTrue(helper.getLevel().getBlockState(wallSample(helper)).is(BossBlocks.FOG_WALL), "fog wall block missing at the ring");
		helper.assertTrue(knight.participantIds().contains(player.getUUID()), "engaging player isn't a participant");

		UUID id = knight.getUUID();
		knight.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		helper.succeedWhen(() -> {
			helper.assertTrue(knight.isDeadOrDying(), "not dead");
			helper.assertTrue(!knight.fogWall().isUp(), "wall still up");
			helper.assertTrue(helper.getLevel().getBlockState(wallSample(helper)).isAir(), "fog wall block left behind");
			Kill kill = KILLS.stream().filter(k -> k.boss().equals(id)).findFirst().orElse(null);
			helper.assertTrue(kill != null, "no BossEvents.KILLED");
			helper.assertTrue(kill.id().equals(CommonIds.FURNACE_KNIGHT), "KILLED with id " + kill.id());
			helper.assertTrue(kill.players().contains(player.getUUID()), "KILLED without the participant");
		});
	}

	/** Nobody left in the fight → after the reset delay the boss heals to full and the wall drops. */
	@GameTest(environment = ENV, maxTicks = 400)
	public void resetHealsAndDropsWall(GameTestHelper helper) {
		FurnaceKnight knight = knight(helper);
		knight.engage(outsider(helper));
		knight.setHealth(knight.getMaxHealth() * 0.6f);
		helper.assertTrue(knight.fogWall().isUp(), "no fog wall");
		helper.succeedWhen(() -> {
			helper.assertTrue(!knight.isEngaged(), "still engaged");
			helper.assertTrue(knight.getHealth() >= knight.getMaxHealth() - 0.01f, "not healed: " + knight.getHealth());
			helper.assertTrue(!knight.fogWall().isUp(), "wall still up");
			helper.assertTrue(helper.getLevel().getBlockState(wallSample(helper)).isAir(), "fog wall block left behind");
			helper.assertTrue(knight.phase() == 0, "phase not reset");
		});
	}

	/** Hits on the helm part go through the part mixins and deal the weak-point multiplier. */
	@GameTest(environment = ENV)
	public void helmWeakPointTakesExtraDamage(GameTestHelper helper) {
		FurnaceKnight knight = knight(helper);
		ServerLevel level = helper.getLevel();
		knight.aiStep();
		BossPart helm = knight.parts()[0];
		helper.assertTrue(level.getEntityOrPart(helm.getId()) == helm, "ServerLevel.getEntityOrPart doesn't find the part");
		List<Entity> found = level.getEntities((Entity) null, new AABB(helm.position(), helm.position()).inflate(0.5), e -> e instanceof BossPart);
		helper.assertTrue(found.contains(helm), "Level.getEntities doesn't return the part");

		float before = knight.getHealth();
		// Magic ignores armor, so the numbers are exact.
		helm.hurtServer(level, level.damageSources().magic(), 10);
		float lost = before - knight.getHealth();
		helper.assertTrue(Math.abs(lost - 10 * FurnaceKnight.config().helmDamageMult) < 0.01, "helm hit took " + lost);
		helper.succeed();
	}

	/** The main table drops 4 blaze rods + 2 Ender Eyes; the optional cross-module tables don't break anything when missing. */
	@GameTest(environment = ENV)
	public void furnaceKnightDropsLoot(GameTestHelper helper) {
		FurnaceKnight knight = knight(helper);
		ServerPlayer player = outsider(helper);
		ServerLevel level = helper.getLevel();
		DamageSource source = level.damageSources().playerAttack(player);
		List<ItemStack> drops = new ArrayList<>();
		knight.dropFromLootTable(level, source, true, knight.getLootTable().orElseThrow(), drops::add);
		for (var table : knight.definition().extraLoot()) {
			knight.dropFromLootTable(level, source, true, table, drops::add);
		}
		helper.assertTrue(count(drops, Items.BLAZE_ROD) == 4, "blaze rods: " + count(drops, Items.BLAZE_ROD));
		helper.assertTrue(count(drops, Items.ENDER_EYE) == 2, "ender eyes: " + count(drops, Items.ENDER_EYE));
		helper.succeed();
	}

	private static int count(List<ItemStack> drops, Item item) {
		return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}
}
