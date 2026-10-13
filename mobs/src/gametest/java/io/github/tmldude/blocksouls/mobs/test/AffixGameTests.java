package io.github.tmldude.blocksouls.mobs.test;

import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.mob.Affix;
import io.github.tmldude.blocksouls.common.mob.Affixes;
import io.github.tmldude.blocksouls.common.mob.WaveContext;
import io.github.tmldude.blocksouls.common.mob.WaveMobSpec;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;
import io.github.tmldude.blocksouls.common.run.Difficulty;
import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.affix.MobAffixes;
import io.github.tmldude.blocksouls.mobs.ai.FlankGoal;
import io.github.tmldude.blocksouls.mobs.entity.Brute;
import io.github.tmldude.blocksouls.mobs.entity.Bulwark;
import io.github.tmldude.blocksouls.mobs.entity.Shambler;
import io.github.tmldude.blocksouls.mobs.entity.Spitter;
import io.github.tmldude.blocksouls.mobs.entity.WaveMob;
import io.github.tmldude.blocksouls.mobs.logic.FlankMath;
import io.github.tmldude.blocksouls.mobs.registry.MobEntities;
import io.github.tmldude.blocksouls.mobs.registry.MobWaves;

/** Elite affixes, act rosters, flanking and the two v2 mobs. All on a 16×16 stone floor. */
public class AffixGameTests {
	private static final String ENV = "blocksouls_mobs_test:normal";
	private static final String FLOOR = "blocksouls_mobs_test:floor16";

	private static MobsConfig.AffixConfig config() {
		return BlockSoulsMobs.CONFIG.affixes;
	}

	private static <T extends WaveMob> T spawn(GameTestHelper helper, EntityType<T> type, double x, double z, boolean ai) {
		ServerLevel level = helper.getLevel();
		T mob = MobWaves.create(type, level, helper.absolutePos(new BlockPos((int) x, 1, (int) z)),
				new WaveContext(5, Difficulty.NORMAL, Set.of(), false));
		helper.assertTrue(mob != null, "factory returned null for " + type);
		mob.setPos(helper.absoluteVec(new Vec3(x, 1, z)));
		mob.setPersistenceRequired();
		mob.setNoAi(!ai);
		mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0));
		level.addFreshEntity(mob);
		return mob;
	}

	private static Pig pig(GameTestHelper helper, double x, double z) {
		Pig pig = helper.spawn(EntityTypes.PIG, new Vec3(x, 1, z));
		pig.setNoAi(true);
		return pig;
	}

	private static Brute elite(GameTestHelper helper, Affix affix, double x, double z) {
		Brute brute = spawn(helper, MobEntities.BRUTE, x, z, false);
		helper.assertTrue(Affixes.apply(brute, affix), "provider refused " + affix);
		return brute;
	}

	private static float lost(LivingEntity entity) {
		return entity.getMaxHealth() - entity.getHealth();
	}

	@GameTest(structure = FLOOR, environment = ENV)
	public void affixedElitesWearTheirName(GameTestHelper helper) {
		for (Affix affix : Affix.values()) {
			Brute plain = spawn(helper, MobEntities.BRUTE, 2 + affix.ordinal() * 3, 12, false);
			Brute brute = elite(helper, affix, 2 + affix.ordinal() * 3, 4);
			helper.assertTrue(Affixes.of(brute).orElse(null) == affix, affix + " not attached");
			Component name = brute.getCustomName();
			helper.assertTrue(name != null && name.getContents() instanceof TranslatableContents t
					&& t.getKey().equals("affix.blocksouls_mobs.name")
					&& t.getArgs()[0] instanceof Component prefix && prefix.getContents() instanceof TranslatableContents p
					&& p.getKey().equals(affix.translationKey()), affix + " name is " + name);
			helper.assertTrue(brute.isCustomNameVisible() == config().showName, affix + " nameplate visibility");
			helper.assertTrue(brute.getMaxHealth() >= plain.getMaxHealth(), affix + " lost health: " + brute.getMaxHealth());
			helper.assertTrue(brute.getHealth() == brute.getMaxHealth(), affix + " not healed to its new max");
			MobAffixes.clear(brute);
			helper.assertTrue(Affixes.of(brute).isEmpty() && brute.getCustomName() == null, affix + " clear left it marked");
		}
		helper.succeed();
	}

	@GameTest(structure = FLOOR, environment = ENV)
	public void burningHitsIgnite(GameTestHelper helper) {
		Brute brute = elite(helper, Affix.BURNING, 4, 8);
		Pig pig = pig(helper, 6, 8);
		pig.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(brute), 2);
		helper.assertTrue(pig.isOnFire(), "pig not burning after a Burning hit");
		helper.succeed();
	}

	@GameTest(structure = FLOOR, environment = ENV)
	public void frostedHitsSlowAndShrugsOffShots(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Brute brute = elite(helper, Affix.FROSTED, 4, 8);
		Pig pig = pig(helper, 6, 8);
		pig.hurtServer(level, level.damageSources().mobAttack(brute), 2);
		helper.assertTrue(pig.hasEffect(MobEffects.SLOWNESS), "pig not slowed after a Frosted hit");

		Brute plain = spawn(helper, MobEntities.BRUTE, 10, 4, false);
		Pig shooter = pig(helper, 10, 12);
		plain.hurtServer(level, level.damageSources().mobProjectile(shooter, shooter), 8);
		brute.hurtServer(level, level.damageSources().mobProjectile(shooter, shooter), 8);
		helper.assertTrue(lost(plain) > 0 && lost(brute) < lost(plain),
				"frosted took " + lost(brute) + " from a shot, plain took " + lost(plain));
		helper.succeed();
	}

	@GameTest(structure = FLOOR, environment = ENV, maxTicks = 100)
	public void volatileBlowsUpAfterItsFuse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Brute brute = elite(helper, Affix.VOLATILE, 8, 8);
		Pig pig = pig(helper, 9.5, 8);
		brute.kill(level);
		helper.assertTrue(MobAffixes.pendingFuses() > 0, "no fuse lit on death");
		helper.assertTrue(lost(pig) == 0, "blast went off before the fuse");
		helper.runAfterDelay(config().volatileAffix.fuseTicks + 5, () -> {
			helper.assertTrue(!pig.isAlive() || lost(pig) > 0, "pig untouched by the blast");
			for (int x = 5; x <= 11; x++) {
				helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 0, 8));
			}
			helper.succeed();
		});
	}

	@GameTest(structure = FLOOR, environment = ENV, maxTicks = 60)
	public void magneticPullsItems(GameTestHelper helper) {
		Brute brute = elite(helper, Affix.MAGNETIC, 8, 8);
		ItemEntity item = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.IRON_NUGGET));
		item.setPos(helper.absoluteVec(new Vec3(12.5, 1, 8.5)));
		item.setDeltaMovement(Vec3.ZERO);
		item.setNeverPickUp();
		helper.getLevel().addFreshEntity(item);
		double start = item.distanceTo(brute);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(item.distanceTo(brute) < start - 0.5, "item not pulled: " + start + " -> " + item.distanceTo(brute));
			helper.succeed();
		});
	}

	@GameTest(structure = FLOOR, environment = ENV, maxTicks = 60)
	public void magneticBendsPlayerShots(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Brute brute = elite(helper, Affix.MAGNETIC, 8, 8);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Snowball snowball = new Snowball(level, player, new ItemStack(Items.SNOWBALL));
		Vec3 start = brute.position().add(2, brute.getBbHeight() / 2, -4);
		snowball.setPos(start);
		snowball.setNoGravity(true);
		snowball.setDeltaMovement(0, 0, 0.5);
		level.addFreshEntity(snowball);
		helper.runAfterDelay(6, () -> {
			double bend = snowball.getX() - start.x;
			helper.assertTrue(bend > 0.1, "shot not bent away from the magnet: " + bend);
			helper.succeed();
		});
	}

	@GameTest(environment = ENV)
	public void rostersOpenByFloor(GameTestHelper helper) {
		WaveMobSpec shambler = WaveMobs.get(CommonIds.SHAMBLER).orElseThrow();
		WaveMobSpec spitter = WaveMobs.get(CommonIds.SPITTER).orElseThrow();
		WaveMobSpec bulwark = WaveMobs.get(CommonIds.BULWARK).orElseThrow();
		Set<String> tags = Set.of();
		helper.assertTrue(WaveMobs.eligible(shambler, 50, 1, tags), "shambler should open on floor 1");
		helper.assertTrue(!WaveMobs.eligible(spitter, 50, 1, tags), "spitter should wait for floor 2");
		helper.assertTrue(!WaveMobs.eligible(bulwark, 50, 1, tags), "bulwark should wait for floor 3");
		for (WaveMobSpec spec : new WaveMobSpec[] {shambler, spitter, bulwark}) {
			helper.assertTrue(WaveMobs.eligible(spec, 50, 6, tags), spec.id() + " should be open on floor 6");
		}
		helper.succeed();
	}

	@GameTest(structure = FLOOR, environment = "blocksouls_mobs_test:flank", maxTicks = 300)
	public void flankerGetsBehindThePlayer(GameTestHelper helper) {
		// A hand-made survival player: the helper's mock is always creative, so mobs drop it as a target.
		ServerPlayer player = survivor(helper, helper.absoluteVec(new Vec3(8.5, 1, 8.5)));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 30, 4));
		Shambler shambler = spawn(helper, MobEntities.SHAMBLER, 8.5, 1.5, true);
		shambler.addTag(WaveMobs.FLANKER_TAG);
		shambler.setTarget(player);
		double behindDegrees = BlockSoulsMobs.CONFIG.flanking.behindDegrees;
		boolean[] done = {false};
		// Facing north (-z); the shambler starts in front of them.
		helper.onEachTick(() -> {
			if (done[0]) {
				return;
			}
			player.setYRot(180);
			player.setYHeadRot(180);
			double bearing = FlankMath.bearing(FlankGoal.partyFacing(player), shambler.getX() - player.getX(), shambler.getZ() - player.getZ());
			if (FlankMath.behind(bearing, behindDegrees)) {
				done[0] = true;
				helper.getLevel().getServer().getPlayerList().remove(player);
				helper.succeed();
			}
		});
		helper.runAfterDelay(290, () -> {
			if (!done[0]) {
				done[0] = true;
				double bearing = FlankMath.bearing(FlankGoal.partyFacing(player), shambler.getX() - player.getX(), shambler.getZ() - player.getZ());
				helper.getLevel().getServer().getPlayerList().remove(player);
				helper.fail("shambler at bearing " + bearing + ", " + helper.relativePos(shambler.blockPosition()));
			}
		});
	}

	/** A survival mock player, placed like a real login so mobs can target it. */
	private static ServerPlayer survivor(GameTestHelper helper, Vec3 at) {
		var server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-flanked"), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		// The test server's default mode is creative; mobs won't target an invulnerable player.
		player.getAbilities().invulnerable = false;
		player.teleportTo(at.x, at.y, at.z);
		return player;
	}

	@GameTest(structure = FLOOR, environment = ENV)
	public void bulwarkShieldTakesLessFromTheFront(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Bulwark front = spawn(helper, MobEntities.BULWARK, 4.5, 8.5, false);
		Bulwark back = spawn(helper, MobEntities.BULWARK, 12.5, 8.5, false);
		for (Bulwark b : new Bulwark[] {front, back}) {
			b.setYRot(0);
			b.setYBodyRot(0);
			b.setYHeadRot(0);
		}
		// Yaw 0 faces +z: one pig in front of the first, one behind the second.
		Pig ahead = pig(helper, 4.5, 11.5);
		Pig behind = pig(helper, 12.5, 5.5);
		helper.assertTrue(front.shields(ahead.position()) && !back.shields(behind.position()), "shield arc is wrong");
		front.hurtServer(level, level.damageSources().mobAttack(ahead), 6);
		back.hurtServer(level, level.damageSources().mobAttack(behind), 6);
		helper.assertTrue(lost(front) < lost(back), "front took " + lost(front) + ", back took " + lost(back));
		helper.succeed();
	}

	@GameTest(structure = FLOOR, environment = ENV, maxTicks = 80)
	public void spitterGlobPoisons(GameTestHelper helper) {
		Spitter spitter = spawn(helper, MobEntities.SPITTER, 8.5, 3.5, false);
		Pig pig = pig(helper, 8.5, 8.5);
		// Globs spread a little, so keep spitting until one lands.
		int[] tick = {0};
		helper.onEachTick(() -> {
			if (tick[0]++ % 15 == 0) {
				spitter.spit(pig);
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(lost(pig) > 0 || pig.hasEffect(MobEffects.POISON), "glob hasn't landed"));
	}
}
