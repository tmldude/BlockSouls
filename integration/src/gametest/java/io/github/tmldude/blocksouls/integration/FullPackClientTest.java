package io.github.tmldude.blocksouls.integration;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;

/**
 * A real client joins a dedicated server running the full pack: every client mixin, renderer, HUD element and payload
 * gets exercised together. Screenshots land in {@code integration/build/run/clientGameTest/screenshots} (CI uploads
 * them) so the look can be checked without a person launching the game.
 */
public class FullPackClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			// Survive whatever the run throws at the test player.
			server.runCommand("effect give @a minecraft:resistance infinite 4 true");
			server.runCommand("effect give @a minecraft:saturation infinite 0 true");
			context.waitTicks(20);
			context.takeScreenshot("01-joined");

			// The test world freezes time; the run needs it running. Dusk: wave 1 starts, the sky tint and wave HUD come up.
			server.runCommand("gamerule advance_time true");
			server.runCommand("time set 12700");
			context.waitTicks(400);
			context.takeScreenshot("02-dusk-wave");

			// A line-up of the roster in front of the player.
			String[] mobs = {"shambler", "crawler", "screamer", "bloater", "brute", "mutant_brute"};
			for (int i = 0; i < mobs.length; i++) {
				server.runCommand("execute as @a at @s rotated ~ 0 run summon blocksouls_mobs:" + mobs[i]
						+ " ^" + (i * 2 - 5) + " ^ ^7 {NoAI:1b}");
			}
			context.waitTicks(20);
			context.takeScreenshot("03-roster");

			// Guns in hand: the ammo HUD and gun crosshair.
			server.runCommand("execute as @a run bs gun give hand_cannon legendary");
			server.runCommand("execute as @a at @s run summon blocksouls_vehicles:kart_wreck ^3 ^ ^4");
			context.waitTicks(20);
			context.takeScreenshot("04-gun-and-wreck");

			// A wave in progress (mobs come from 24-40 blocks out).
			server.runCommand("kill @e[type=blocksouls_mobs:shambler,nbt={NoAI:1b}]");
			context.waitTicks(200);
			context.takeScreenshot("05-wave-incoming");

			// The Nether: blight and ash fog. Teleport first so the chunks load, then clear a pocket around the player
			// (a fill in unloaded chunks does nothing): a hollow glass box keeps the lava out and the view clear.
			server.runCommand("effect give @a minecraft:fire_resistance infinite 0 true");
			server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
			context.waitTicks(60);
			server.runCommand("execute as @a at @s run fill ~-4 ~-1 ~-4 ~4 ~4 ~4 minecraft:glass hollow");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 10");
			context.waitTicks(100);
			context.takeScreenshot("06-nether");
		}
	}
}
