package io.github.tmldude.blocksouls.bosses.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.bosses.arena.ArenaSeal;
import io.github.tmldude.blocksouls.bosses.furnace.FurnaceKnight;
import io.github.tmldude.blocksouls.bosses.furnace.FurnaceKnights;

/**
 * The arena seal: while a fog-wall boss is engaged, participants can't leave its cylinder and outsiders can't enter, at
 * any height; pearls across it fizzle; creative players pass; it lifts on reset and death. Survival players here are
 * hand-made mocks (the helper's are always creative), invulnerable, and removed when the test ends.
 */
public class ArenaSealGameTests {
	private static final String ENV = "blocksouls_bosses_test:normal";
	private static final String FLOOR = "blocksouls_bosses_test:floor16";
	private static final BlockPos CENTER = new BlockPos(8, 1, 8);
	private static final int RADIUS = 4;
	/** Two blocks in from the center, and two blocks past the ring. */
	private static final BlockPos INSIDE = CENTER.offset(0, 0, -2);
	private static final BlockPos OUTSIDE = CENTER.offset(0, 0, -6);

	private static FurnaceKnight knight(GameTestHelper helper) {
		FurnaceKnight knight = helper.spawn(FurnaceKnights.TYPE, CENTER);
		knight.setHome(helper.absolutePos(CENTER));
		knight.setArenaRadius(RADIUS);
		return knight;
	}

	private static Vec3 at(GameTestHelper helper, BlockPos relative, double up) {
		return Vec3.atBottomCenterOf(helper.absolutePos(relative)).add(0, up, 0);
	}

	/** teleportTo, not snapTo: the mock connection re-sends its pending teleport, which would undo a snapTo. */
	private static void move(ServerPlayer player, Vec3 to) {
		player.teleportTo(to.x, to.y, to.z);
	}

	/** A survival mock player that can't be hurt. */
	private static ServerPlayer survivor(GameTestHelper helper, List<ServerPlayer> made, Vec3 at) {
		var server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-survivor"), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}
		};
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.getAbilities().invulnerable = true;
		move(player, at);
		made.add(player);
		return player;
	}

	private static void remove(GameTestHelper helper, List<ServerPlayer> made) {
		for (ServerPlayer player : made) {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}

	/** Runs the final checks, removes the survivors either way, then succeeds. */
	private static void finish(GameTestHelper helper, List<ServerPlayer> made, Runnable checks) {
		try {
			checks.run();
		} finally {
			remove(helper, made);
		}
		helper.succeed();
	}

	/** Teleported across the ring (on the ground, then above the wall's top): back inside each time. */
	@GameTest(environment = ENV, structure = FLOOR, maxTicks = 100)
	public void participantCantLeaveAtAnyHeight(GameTestHelper helper) {
		List<ServerPlayer> made = new ArrayList<>();
		FurnaceKnight knight = knight(helper);
		ServerPlayer player = survivor(helper, made, at(helper, INSIDE, 0));
		knight.engage(player);
		helper.assertTrue(knight.seal().isActive(), "engaging a fog-wall boss didn't close the seal");
		ArenaSeal seal = knight.seal();

		helper.runAfterDelay(3, () -> move(player, at(helper, OUTSIDE, 0)));
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(seal.isInside(player.position()), "participant got out on the ground: " + player.position());
			helper.assertTrue(seal.bounces() == 1, "bounces: " + seal.bounces());
			// Up above the visible wall (fogWallAbove = 10), still inside.
			move(player, at(helper, INSIDE, 15));
		});
		helper.runAfterDelay(9, () -> move(player, at(helper, OUTSIDE, 15)));
		helper.runAfterDelay(12, () -> finish(helper, made, () -> {
			helper.assertTrue(seal.isInside(player.position()), "participant got out over the wall: " + player.position());
			helper.assertTrue(player.getY() > helper.absolutePos(CENTER).getY() + 14, "not put back where they were: " + player.position());
			helper.assertTrue(seal.bounces() == 2, "bounces: " + seal.bounces());
			helper.assertTrue(knight.isEngaged(), "fight reset");
		}));
	}

	/** A player who wasn't in the fight can't get in, and doesn't become a participant by trying. */
	@GameTest(environment = ENV, structure = FLOOR, maxTicks = 100)
	public void outsiderCantEnter(GameTestHelper helper) {
		List<ServerPlayer> made = new ArrayList<>();
		FurnaceKnight knight = knight(helper);
		ServerPlayer inside = survivor(helper, made, at(helper, INSIDE, 0));
		ServerPlayer outsider = survivor(helper, made, at(helper, OUTSIDE, 0));
		knight.engage(inside);
		helper.assertTrue(!knight.participantIds().contains(outsider.getUUID()), "outsider counted as a participant at engage");

		helper.runAfterDelay(3, () -> move(outsider, at(helper, CENTER.offset(2, 0, 0), 0)));
		helper.runAfterDelay(25, () -> finish(helper, made, () -> {
			helper.assertTrue(!knight.seal().isInside(outsider.position()), "outsider got in: " + outsider.position());
			helper.assertTrue(knight.seal().bounces() >= 1, "no bounce");
			helper.assertTrue(!knight.participantIds().contains(outsider.getUUID()), "outsider joined the fight");
		}));
	}

	/** An ender pearl landing outside the ring fizzles: the thrower is never outside, not even for a tick. */
	@GameTest(environment = ENV, structure = FLOOR, maxTicks = 100)
	public void enderPearlAcrossIsCancelled(GameTestHelper helper) {
		List<ServerPlayer> made = new ArrayList<>();
		FurnaceKnight knight = knight(helper);
		ServerPlayer player = survivor(helper, made, at(helper, INSIDE, 0));
		knight.engage(player);
		ArenaSeal seal = knight.seal();
		helper.assertTrue(!ArenaSeal.allowsTeleport(player, at(helper, OUTSIDE, 0), false), "seal allows a teleport out");

		boolean[] escaped = {false};
		helper.onEachTick(() -> escaped[0] |= !seal.isInside(player.position()));
		ThrownEnderpearl pearl = new ThrownEnderpearl(helper.getLevel(), player, new ItemStack(Items.ENDER_PEARL));
		Vec3 drop = at(helper, OUTSIDE, 1.5);
		pearl.setPos(drop.x, drop.y, drop.z);
		pearl.setDeltaMovement(0, -0.5, 0);
		helper.getLevel().addFreshEntity(pearl);

		helper.runAfterDelay(20, () -> finish(helper, made, () -> {
			helper.assertTrue(pearl.isRemoved(), "pearl never landed");
			helper.assertTrue(!escaped[0], "the pearl carried the player out");
			helper.assertTrue(seal.isInside(player.position()), "player outside: " + player.position());
			helper.assertTrue(seal.bounces() == 0, "the pearl teleported them and the seal had to undo it");
		}));
	}

	/** Creative players (and spectators) ignore the seal both ways. */
	@GameTest(environment = ENV, structure = FLOOR, maxTicks = 100)
	public void creativePlayersPass(GameTestHelper helper) {
		FurnaceKnight knight = knight(helper);
		ServerPlayer leaving = helper.makeMockServerPlayerInLevel();
		move(leaving, at(helper, INSIDE, 0));
		ServerPlayer entering = helper.makeMockServerPlayerInLevel();
		move(entering, at(helper, OUTSIDE, 0));
		knight.engage(leaving);
		helper.assertTrue(knight.seal().isActive(), "no seal");
		helper.assertTrue(ArenaSeal.allowsTeleport(leaving, at(helper, OUTSIDE, 0), false), "creative pearl blocked");

		helper.runAfterDelay(3, () -> {
			move(leaving, at(helper, OUTSIDE, 0));
			move(entering, at(helper, INSIDE, 0));
		});
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(!knight.seal().isInside(leaving.position()), "creative participant held in");
			helper.assertTrue(knight.seal().isInside(entering.position()), "creative outsider kept out");
			helper.assertTrue(knight.seal().bounces() == 0, "bounces: " + knight.seal().bounces());
			helper.succeed();
		});
	}

	/** Reset and death both lift the seal: the participant can then walk (or pearl) out. */
	@GameTest(environment = ENV, structure = FLOOR, maxTicks = 100)
	public void sealLiftsOnResetAndDeath(GameTestHelper helper) {
		List<ServerPlayer> made = new ArrayList<>();
		FurnaceKnight knight = knight(helper);
		ServerPlayer player = survivor(helper, made, at(helper, INSIDE, 0));
		knight.engage(player);
		helper.assertTrue(knight.seal().isActive(), "no seal");
		knight.resetFight();
		helper.assertTrue(!knight.seal().isActive(), "seal still up after reset");
		helper.assertTrue(ArenaSeal.allowsTeleport(player, at(helper, OUTSIDE, 0), false), "teleport out blocked after reset");
		move(player, at(helper, OUTSIDE, 0));

		helper.runAfterDelay(4, () -> {
			helper.assertTrue(!knight.seal().isInside(player.position()), "pulled back in after the reset");
			move(player, at(helper, INSIDE, 0));
			knight.engage(player);
			helper.assertTrue(knight.seal().isActive(), "no seal on re-engage");
			knight.hurtServer(helper.getLevel(), helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		});
		helper.runAfterDelay(8, () -> finish(helper, made, () -> {
			helper.assertTrue(knight.isDeadOrDying(), "not dead");
			helper.assertTrue(!knight.seal().isActive(), "seal still up after death");
			helper.assertTrue(ArenaSeal.allowsTeleport(player, at(helper, OUTSIDE, 0), false), "teleport out blocked after death");
		}));
	}
}
