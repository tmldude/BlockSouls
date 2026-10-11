package io.github.tmldude.blocksouls.mobs.test;

import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.mob.WaveContext;
import io.github.tmldude.blocksouls.common.mob.WaveMobSpec;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.run.Difficulty;
import io.github.tmldude.blocksouls.mobs.dread.Dread;
import io.github.tmldude.blocksouls.mobs.entity.Bloater;
import io.github.tmldude.blocksouls.mobs.entity.Screamer;
import io.github.tmldude.blocksouls.mobs.entity.Shambler;
import io.github.tmldude.blocksouls.mobs.entity.WaveMob;
import io.github.tmldude.blocksouls.mobs.registry.MobEntities;
import io.github.tmldude.blocksouls.mobs.registry.MobWaves;

public class MobsGameTests {
	/** Normal difficulty: the default test environment is peaceful, where monsters can't attack players. */
	private static final String ENV = "blocksouls_mobs_test:normal";

	private static WaveContext context(int wave, boolean breaker) {
		return new WaveContext(wave, Difficulty.NORMAL, Set.of(), breaker);
	}

	/** A stone floor at y=0 across the 8×8 test area. */
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** A wall across the area at {@code x}, three blocks high. */
	private static void wall(GameTestHelper helper, int x, Block block) {
		for (int y = 1; y <= 3; y++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, y, z, block);
			}
		}
	}

	/** A pig that doesn't move: something to target. */
	private static Pig dummy(GameTestHelper helper, int x, int z) {
		Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(x, 1, z));
		pig.setNoAi(true);
		return pig;
	}

	private static <T extends WaveMob> T spawnWave(GameTestHelper helper, EntityType<T> type, int x, int z, int wave, boolean breaker) {
		ServerLevel level = helper.getLevel();
		T mob = MobWaves.create(type, level, helper.absolutePos(new BlockPos(x, 1, z)), context(wave, breaker));
		helper.assertTrue(mob != null, "factory returned null for " + type);
		// The game makes wave mobs persistent; without it they'd despawn, being far from the mock players.
		mob.setPersistenceRequired();
		// Keep the sun out of the result either way.
		mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0));
		level.addFreshEntity(mob);
		return mob;
	}

	@GameTest(environment = ENV)
	public void everyFactoryMakesItsOwnMob(GameTestHelper helper) {
		floor(helper);
		int ours = 0;
		for (WaveMobSpec spec : WaveMobs.all()) {
			if (!spec.id().getNamespace().equals(CommonIds.MOBS)) {
				continue;
			}
			ours++;
			Mob mob = spec.factory().create(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)), context(3, true));
			helper.assertTrue(mob != null, "factory returned null for " + spec.id());
			Identifier typeId = EntityType.getKey(mob.getType());
			helper.assertTrue(typeId.equals(spec.id()), "factory for " + spec.id() + " made " + typeId);
			helper.assertTrue(mob.is(CommonTags.WAVE_MOBS), spec.id() + " missing from #blocksouls:wave_mobs");
			helper.assertTrue(mob.is(CommonTags.ELITES) == spec.elite(), spec.id() + " elite tag doesn't match its spec");
			helper.assertTrue(mob instanceof WaveMob wm && wm.wave() == 3 && wm.canBreakBlocks(), spec.id() + " ignored the wave context");
			helper.assertTrue(!mob.isRemoved() && mob.level() == helper.getLevel(), spec.id() + " should be created, not added");
		}
		helper.assertTrue(ours == 6, "expected 6 wave mobs, got " + ours);
		helper.succeed();
	}

	@GameTest(environment = ENV, maxTicks = 400)
	public void breakerShamblerBreaksPlanks(GameTestHelper helper) {
		floor(helper);
		// The shambler is boxed into the x=2 lane: stone behind it, planks between it and the target.
		wall(helper, 1, Blocks.STONE);
		wall(helper, 3, Blocks.OAK_PLANKS);
		Pig target = dummy(helper, 6, 3);
		Shambler shambler = spawnWave(helper, MobEntities.SHAMBLER, 2, 3, 10, true);
		shambler.setTarget(target);
		// Stuck 2 s, then 3 s per block at wave 10.
		helper.succeedWhen(() -> {
			boolean broken = false;
			for (int z = 0; z < 8; z++) {
				broken |= !helper.getBlockState(new BlockPos(3, 1, z)).is(Blocks.OAK_PLANKS)
						|| !helper.getBlockState(new BlockPos(3, 2, z)).is(Blocks.OAK_PLANKS);
			}
			helper.assertTrue(broken, "planks wall intact; shambler at " + helper.relativePos(shambler.blockPosition()) + ", target "
					+ shambler.getTarget() + ", breaker " + shambler.canBreakBlocks() + ", griefing "
					+ helper.getLevel().getGameRules().get(GameRules.MOB_GRIEFING));
		});
	}

	@GameTest(environment = ENV, maxTicks = 260)
	public void shamblerLeavesStone(GameTestHelper helper) {
		floor(helper);
		wall(helper, 1, Blocks.STONE);
		wall(helper, 3, Blocks.STONE);
		Pig target = dummy(helper, 6, 3);
		Shambler shambler = spawnWave(helper, MobEntities.SHAMBLER, 2, 3, 10, true);
		shambler.setTarget(target);
		helper.runAfterDelay(240, () -> {
			for (int z = 0; z < 8; z++) {
				helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 1, z));
				helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, z));
			}
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 260)
	public void nonBreakerLeavesPlanks(GameTestHelper helper) {
		floor(helper);
		wall(helper, 1, Blocks.STONE);
		wall(helper, 3, Blocks.OAK_PLANKS);
		Pig target = dummy(helper, 6, 3);
		Shambler shambler = spawnWave(helper, MobEntities.SHAMBLER, 2, 3, 10, false);
		shambler.setTarget(target);
		helper.runAfterDelay(240, () -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(3, 1, 3));
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 40)
	public void bloaterBurstHurtsButSparesBlocks(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(4, 1, 3, Blocks.GLASS);
		helper.setBlock(2, 1, 3, Blocks.OAK_PLANKS);
		helper.setBlock(3, 2, 4, Blocks.OAK_PLANKS);
		Pig pig = dummy(helper, 3, 5);
		Bloater bloater = spawnWave(helper, MobEntities.BLOATER, 3, 3, 1, false);
		float before = pig.getHealth();

		helper.runAfterDelay(2, () -> {
			bloater.burst(helper.getLevel());
			helper.assertTrue(pig.getHealth() < before, "pig took no burst damage");
			helper.assertTrue(!bloater.isAlive(), "bloater survived its burst");
			helper.assertBlockPresent(Blocks.GLASS, new BlockPos(4, 1, 3));
			helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(2, 1, 3));
			helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(3, 2, 4));
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 0, 3));
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 40)
	public void screamerBuffsNearbyShambler(GameTestHelper helper) {
		floor(helper);
		// Mock players are in creative, which mobs refuse to target, so the scream is aimed at a pig.
		Pig victim = dummy(helper, 4, 1);
		Screamer screamer = spawnWave(helper, MobEntities.SCREAMER, 1, 1, 3, false);
		Shambler shambler = spawnWave(helper, MobEntities.SHAMBLER, 6, 6, 3, false);

		helper.runAfterDelay(2, () -> {
			int heard = screamer.scream(helper.getLevel(), victim);
			helper.assertTrue(shambler.hasEffect(MobEffects.SPEED), "shambler didn't get Speed (" + heard + " heard, shambler alive "
					+ shambler.isAlive() + " at " + helper.relativePos(shambler.blockPosition()) + ")");
			helper.assertTrue(shambler.getTarget() == victim, "shambler didn't turn on the screamed-at target");
			helper.assertTrue(!screamer.hasEffect(MobEffects.SPEED), "screamer buffed itself");
			helper.succeed();
		});
	}

	@GameTest(environment = ENV)
	public void downedPlayersAreNeverTargets(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Shambler shambler = spawnWave(helper, MobEntities.SHAMBLER, 2, 2, 1, false);
		// Mock players are in creative (invulnerable, never a target). Drop the invulnerability so canAttack, which the
		// targeting goals and Mob#setTarget consult, reflects only the downed state.
		player.getAbilities().invulnerable = false;
		helper.assertTrue(shambler.canAttack(player), "can't attack a player who is up");
		PlayerStatus.setDowned(player, true);
		helper.assertTrue(!shambler.canAttack(player), "can attack a downed player");
		shambler.setTarget(player);
		helper.assertTrue(shambler.getTarget() == null, "targeted a downed player");
		PlayerStatus.setDowned(player, false);
		helper.assertTrue(shambler.canAttack(player), "still refuses a revived player");
		helper.succeed();
	}

	/** Vanilla burns exactly the mobs in {@code minecraft:burn_in_daylight} (Mob#aiStep). */
	@GameTest(environment = ENV)
	public void elitesDontBurnInDaylight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (EntityType<?> type : new EntityType<?>[] {MobEntities.SHAMBLER, MobEntities.CRAWLER, MobEntities.SCREAMER, MobEntities.BLOATER,
				MobEntities.BRUTE}) {
			Mob mob = (Mob) type.create(level, EntitySpawnReason.EVENT);
			helper.assertTrue(mob != null && mob.is(EntityTypeTags.BURN_IN_DAYLIGHT), type + " should burn in daylight");
		}
		Mob mutant = MobEntities.MUTANT_BRUTE.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(mutant != null && !mutant.is(EntityTypeTags.BURN_IN_DAYLIGHT), "mutant brute must survive dawn");
		helper.assertTrue(mutant.is(CommonTags.ELITES), "mutant brute is an elite");
		helper.succeed();
	}

	@GameTest(environment = ENV)
	public void dreadDecaysOutsideTheNether(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Dread.set(player, 100);
		Dread.tickPlayer(player);
		helper.assertTrue(Math.abs(Dread.get(player) - 98) < 1e-6, "expected 98 dread, got " + Dread.get(player));
		helper.succeed();
	}
}
