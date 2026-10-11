package io.github.tmldude.blocksouls.game.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.game.player.downed.Downed;

/** Mock players that can be hurt and that count as being in an active run. */
final class DownedTestPlayers {
	private DownedTestPlayers() {
	}

	static ServerPlayer make(GameTestHelper helper, int x, int z) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		// Vanilla treats a player whose client hasn't finished loading as invulnerable; mocks never send it.
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.getAbilities().invulnerable = false;
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 1, z)));
		player.teleportTo(pos.x, pos.y, pos.z);
		Downed.forceInRun(player);
		return player;
	}
}
