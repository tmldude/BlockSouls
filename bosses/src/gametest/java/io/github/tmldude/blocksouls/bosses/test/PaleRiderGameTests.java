package io.github.tmldude.blocksouls.bosses.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.bosses.palerider.PaleRider;
import io.github.tmldude.blocksouls.bosses.palerider.PaleRiders;
import io.github.tmldude.blocksouls.bosses.palerider.WraithSteed;
import io.github.tmldude.blocksouls.bosses.palerider.WraithSteeds;
import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.event.BossEvents;

public class PaleRiderGameTests {
	/** Normal difficulty: the default test environment is peaceful. */
	private static final String ENV = "blocksouls_bosses_test:normal";
	private static final BlockPos CENTER = new BlockPos(4, 1, 4);

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

	/** A Pale Rider at the center with a small arena that fits the test area. */
	private static PaleRider rider(GameTestHelper helper) {
		floor(helper);
		PaleRider rider = helper.spawn(PaleRiders.TYPE, CENTER);
		rider.setHome(helper.absolutePos(CENTER));
		rider.setArenaRadius(3);
		return rider;
	}

	/** A mock player parked far outside the arena, so it never counts as "in the fight". */
	private static ServerPlayer outsider(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos far = helper.absolutePos(CENTER).offset(200, 0, 200);
		player.snapTo(far.getX(), far.getY(), far.getZ());
		return player;
	}

	/** A mock player standing on the floor at {@code (x, z)}. */
	private static ServerPlayer playerAt(GameTestHelper helper, int x, int z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(x, 1, z));
		player.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		return player;
	}

	private static Pig dummy(GameTestHelper helper, int x, int z) {
		Pig pig = helper.spawn(EntityTypes.PIG, new BlockPos(x, 1, z));
		pig.setNoAi(true);
		pig.setPermanentlyInvulnerable(true);
		return pig;
	}

	@GameTest(environment = ENV)
	public void paleRiderIsTaggedAndRegistered(GameTestHelper helper) {
		PaleRider rider = rider(helper);
		helper.assertTrue(rider.is(CommonTags.BOSSES), "Pale Rider missing from #blocksouls:bosses");
		helper.assertTrue(Bosses.type(CommonIds.PALE_RIDER).isPresent(), "not in the boss registry");
		helper.assertTrue(rider.getMaxHealth() >= PaleRider.config().health - 0.01, "base health " + rider.getMaxHealth());
		helper.assertTrue(rider.getBbHeight() > 3.2f, "mounted rider should stand ~3.5 blocks, is " + rider.getBbHeight());
		helper.succeed();
	}

	/** Scripted health walks P1 → P2 (unhorsed, smaller hitbox); attacks keep finishing in both phases. */
	@GameTest(environment = ENV, maxTicks = 900)
	public void phaseMachineWalksToUnhorsed(GameTestHelper helper) {
		PaleRider rider = rider(helper);
		Pig pig = dummy(helper, 6, 4);
		rider.engage(outsider(helper));
		rider.forceTarget(pig);
		rider.setHealth(rider.getMaxHealth() * 0.7f);
		int[] p1Attacks = {-1};
		int[] staggered = {0};
		helper.onEachTick(() -> {
			if (rider.isStaggered()) {
				staggered[0]++;
			}
		});
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(rider.phase() == 0, "still phase 1 at 70%");
			helper.assertTrue(rider.attacksFinished() >= 2, "only " + rider.attacksFinished() + " attacks finished in phase 1");
			p1Attacks[0] = rider.attacksFinished();
			rider.setHealth(rider.getMaxHealth() * 0.4f);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(p1Attacks[0] >= 0, "phase 1 not done yet");
			helper.assertTrue(rider.phase() == 1, "not in phase 2 yet");
			helper.assertTrue(rider.unhorsed(), "still mounted in phase 2");
			helper.assertTrue(rider.getBbHeight() < 3f, "unhorsed hitbox not applied: " + rider.getBbHeight());
			helper.assertTrue(staggered[0] > 0, "no stagger on the phase change");
			helper.assertTrue(rider.attacksFinished() >= p1Attacks[0] + 2, "attacks stalled in phase 2");
			helper.assertTrue(rider.isEngaged(), "fight reset unexpectedly");
		});
	}

	/** Killing it fires KILLED with the Pale Rider's id and drops the whistle (plus the Ender Eyes). */
	@GameTest(environment = ENV, maxTicks = 80)
	public void killDropsWhistleAndFiresKilled(GameTestHelper helper) {
		PaleRider rider = rider(helper);
		ServerPlayer player = outsider(helper);
		ServerLevel level = helper.getLevel();
		rider.engage(player);

		// The loot table itself: whistle + 2 Ender Eyes.
		DamageSource source = level.damageSources().playerAttack(player);
		List<ItemStack> drops = new ArrayList<>();
		rider.dropFromLootTable(level, source, true, rider.getLootTable().orElseThrow(), drops::add);
		helper.assertTrue(count(drops, WraithSteeds.WHISTLE) == 1, "whistles in the loot table: " + count(drops, WraithSteeds.WHISTLE));
		helper.assertTrue(count(drops, Items.ENDER_EYE) == 2, "ender eyes: " + count(drops, Items.ENDER_EYE));

		UUID id = rider.getUUID();
		AABB area = new AABB(helper.absolutePos(CENTER)).inflate(8);
		rider.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
		helper.succeedWhen(() -> {
			helper.assertTrue(rider.isDeadOrDying(), "not dead");
			Kill kill = KILLS.stream().filter(k -> k.boss().equals(id)).findFirst().orElse(null);
			helper.assertTrue(kill != null, "no BossEvents.KILLED");
			helper.assertTrue(kill.id().equals(CommonIds.PALE_RIDER), "KILLED with id " + kill.id());
			helper.assertTrue(kill.players().contains(player.getUUID()), "KILLED without the participant");
			List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(WraithSteeds.WHISTLE));
			helper.assertTrue(!items.isEmpty(), "no whistle dropped");
		});
	}

	/** The wraith call summons its riders once per phase, and they go away when the fight resets. */
	@GameTest(environment = ENV, maxTicks = 400)
	public void wraithCallOncePerPhaseAndDismissedOnReset(GameTestHelper helper) {
		PaleRider rider = rider(helper);
		Pig pig = dummy(helper, 6, 4);
		rider.engage(outsider(helper));
		rider.forceTarget(pig);
		var call = rider.definition().attacks().stream().filter(a -> a.name().equals("call")).findFirst().orElseThrow();
		helper.assertTrue(call.canUse(rider, pig), "wraith call unavailable at the start");
		rider.startAttack(helper.getLevel(), call, pig);
		helper.succeedWhen(() -> {
			helper.assertTrue(rider.attackRun() == null, "call still running");
			helper.assertTrue(rider.wraiths().size() == 2 * PaleRider.config().wraithCallRiders, "riders: " + rider.wraiths().size());
			helper.assertTrue(!call.canUse(rider, pig), "wraith call usable twice in one phase");
			rider.resetFight();
			helper.assertTrue(rider.wraiths().isEmpty(), "riders left after reset");
			helper.assertTrue(call.canUse(rider, pig), "wraith call not restored by the reset");
		});
	}

	/** The whistle summons a steed owned by the user and mounts them; a second use (after dismounting) recalls it. */
	@GameTest(environment = ENV, maxTicks = 60)
	public void whistleSummonsThenRecalls(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = playerAt(helper, 1, 1);
		ServerLevel level = helper.getLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WraithSteeds.WHISTLE));

		player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
		WraithSteed steed = WraithSteeds.of(player);
		helper.assertTrue(steed != null, "no steed after the whistle");
		helper.assertTrue(steed.isOwnedBy(player), "steed isn't bound to the user");
		helper.assertTrue(player.getVehicle() == steed, "user isn't riding the steed");
		helper.assertTrue(player.getCooldowns().isOnCooldown(player.getMainHandItem()), "no use cooldown");

		UUID first = steed.getUUID();
		player.stopRiding();
		BlockPos away = helper.absolutePos(new BlockPos(6, 1, 6));
		player.snapTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(player.getMainHandItem()));
		player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);

		WraithSteed recalled = WraithSteeds.of(player);
		helper.assertTrue(recalled != null && recalled.getUUID().equals(first), "second use summoned a new steed instead of recalling");
		helper.assertTrue(recalled.distanceTo(player) < 2, "steed wasn't recalled to the user: " + recalled.distanceTo(player));
		helper.assertTrue(player.getVehicle() == recalled, "user isn't riding after the recall");
		helper.assertTrue(level.getEntitiesOfClass(WraithSteed.class, new AABB(away).inflate(16)).size() == 1, "more than one steed");
		helper.succeed();
	}

	/** Nobody else can ride it; it can't be leashed or bred, and it takes no fall damage. */
	@GameTest(environment = ENV, maxTicks = 40)
	public void steedIsBoundToItsOwner(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = playerAt(helper, 1, 1);
		ServerPlayer other = playerAt(helper, 3, 1);
		WraithSteed steed = WraithSteeds.whistle(owner);
		helper.assertTrue(steed != null, "no steed");
		owner.stopRiding();
		helper.assertTrue(!other.startRiding(steed), "another player mounted the steed");
		helper.assertTrue(!steed.canBeLeashed(), "steed can be leashed");
		helper.assertTrue(!steed.isFood(new ItemStack(Items.GOLDEN_CARROT)), "steed can be fed/bred");
		helper.assertTrue(!steed.causeFallDamage(30, 1, helper.getLevel().damageSources().fall()), "steed took fall damage");
		helper.assertTrue(owner.startRiding(steed), "owner can't remount");
		helper.succeed();
	}

	/** Unridden for the fade time → gone. */
	@GameTest(environment = ENV, maxTicks = 200)
	public void steedFadesAfterDismount(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = playerAt(helper, 1, 1);
		WraithSteed steed = WraithSteeds.whistle(owner);
		helper.assertTrue(steed != null, "no steed");
		owner.stopRiding();
		helper.succeedWhen(() -> helper.assertTrue(steed.isRemoved(), "still here after " + steed.unriddenTicks() + " unridden ticks"));
	}

	/** Killing the steed puts the owner's whistle on the long cooldown. */
	@GameTest(environment = ENV, maxTicks = 40)
	public void killedSteedCoolsTheWhistleDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer owner = playerAt(helper, 1, 1);
		WraithSteed steed = WraithSteeds.whistle(owner);
		helper.assertTrue(steed != null, "no steed");
		owner.stopRiding();
		steed.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		ItemStack whistle = new ItemStack(WraithSteeds.WHISTLE);
		helper.assertTrue(owner.getCooldowns().isOnCooldown(whistle), "whistle not on cooldown after the steed died");
		helper.assertTrue(owner.getCooldowns().getCooldownPercent(whistle, 0) > 0.9f, "cooldown too short");
		helper.succeed();
	}

	private static int count(List<ItemStack> drops, Item item) {
		return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
	}
}
