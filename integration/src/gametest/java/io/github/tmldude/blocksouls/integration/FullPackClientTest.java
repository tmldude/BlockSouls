package io.github.tmldude.blocksouls.integration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;

import io.github.tmldude.blocksouls.common.CommonIds;

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
			String[] mobs = {"shambler", "crawler", "screamer", "bloater", "brute", "mutant_brute", "spitter", "bulwark"};
			for (int i = 0; i < mobs.length; i++) {
				server.runCommand("execute as @a at @s rotated ~ 0 run summon blocksouls_mobs:" + mobs[i]
						+ " ^" + (i * 2 - 7) + " ^ ^8 {NoAI:1b}");
			}
			context.waitTicks(20);
			context.takeScreenshot("03-roster");

			// Guns in hand: the ammo HUD and gun crosshair.
			server.runCommand("execute as @a run bs gun give hand_cannon legendary");
			server.runCommand("execute as @a at @s run summon blocksouls_vehicles:kart_wreck ^3 ^ ^4");
			context.waitTicks(20);
			context.takeScreenshot("04-gun-and-wreck");

			// The boon row left of the hotbar: one of each tier plus a combo, drawn with their own icons.
			for (String boon : new String[] {"swift_feet", "iron_skin", "brawler", "fire_ward", "wings", "spring_legs", "featherfall", "moon_boots"}) {
				server.runCommand("bs boon give @p blocksouls_game:" + boon);
			}
			context.waitTicks(10);
			context.takeScreenshot("04b-boon-row");

			// The exotics: one of each in the hotbar, the Kindling Engine in hand (heat bar), and the feel pass on a shot.
			for (String exotic : new String[] {"stray_verdict", "lantern_eye", "backdraft", "tether_lance", "kindling_engine"}) {
				server.runCommand("execute as @a run bs gun give " + exotic);
			}
			server.runCommand("execute as @a run bs gun ammo @s 64");
			context.waitTicks(20);
			context.takeScreenshot("04d-exotics");

			// The abilities HUD bottom-left: three slot icons, one mid-cooldown, and a full ultimate bar.
			server.runCommand("execute as @a run bs ability ready @s");
			server.runCommand("execute as @a run bs ability cast @s tactical");
			context.waitTicks(15);
			context.takeScreenshot("04c-abilities");

			// v2 builds: a keystone (gold key mark), a curse (red frame) in the row, two trinkets right of the hotbar and the
			// build line top-left ("Kinetic · Fire").
			server.runCommand("bs boon give @p blocksouls_game:powder_burn");
			server.runCommand("bs curse give @p blocksouls_game:hemorrhage");
			server.runCommand("bs trinket give @p blocksouls_game:last_shell");
			server.runCommand("bs trinket give @p blocksouls_game:magnet_core");
			context.waitTicks(10);
			context.takeScreenshot("04c-build-line-and-trinkets");

			// v2 players: the class select screen, a class's rune tree (two runes bought), and the run summary cards.
			server.runCommand("bs class select @p");
			context.waitTicks(10);
			context.takeScreenshot("04d-class-select");
			server.runCommand("bs class set @p blocksouls_game:ranger");
			server.runCommand("bs rune souls @p 200");
			server.runCommand("bs rune unlock @p blocksouls_game:ranger_fleet");
			server.runCommand("bs rune unlock @p blocksouls_game:ranger_wings");
			server.runCommand("bs rune tree @p");
			context.waitTicks(10);
			context.takeScreenshot("04e-rune-tree");
			server.runCommand("execute as @p run bs summary show");
			context.waitTicks(10);
			context.takeScreenshot("04f-run-summary");
			context.setScreen(() -> null);

			// A wave in progress (mobs come from 24-40 blocks out).
			server.runCommand("kill @e[type=blocksouls_mobs:shambler,nbt={NoAI:1b}]");
			context.waitTicks(200);
			context.takeScreenshot("05-wave-incoming");

			// Every boss, one at a time, in front of the player (boss bar + model).
			server.runCommand("kill @e[type=!minecraft:player]");
			context.waitTicks(60); // let the death smoke clear
			String[] bosses = {"furnace_knight", "pale_rider", "ashwyrm", "stormplate_knight", "final_dragon"};
			for (int i = 0; i < bosses.length; i++) {
				server.runCommand("execute as @a at @s rotated ~ 0 run summon blocksouls_bosses:" + bosses[i] + " ^ ^ ^14 {NoAI:1b}");
				context.waitTicks(40);
				context.takeScreenshot(String.format("%02d-boss-%s", 10 + i, bosses[i]));
				server.runCommand("kill @e[type=blocksouls_bosses:" + bosses[i] + "]");
			}

			// The final raid's handoff in the real End: the vanilla dragon dies and falls, and ours takes its place.
			server.runCommand("effect give @a minecraft:slow_falling infinite 0 true");
			server.runCommand("execute in minecraft:the_end run tp @a 0 90 30");
			server.waitFor(s -> !s.getLevel(Level.END).getEntities(EntityTypes.ENDER_DRAGON, Entity::isAlive).isEmpty(), 1200);
			context.waitTicks(100);
			context.takeScreenshot("15-end-vanilla-dragon");
			server.runCommand("kill @e[type=minecraft:ender_dragon]");
			EntityType<?> finalDragon = BuiltInRegistries.ENTITY_TYPE.getValue(CommonIds.FINAL_BOSS);
			server.waitFor(s -> !s.getLevel(Level.END).getEntities(finalDragon, Entity::isAlive).isEmpty(), 400);
			context.waitTicks(200); // the fall and the dazed sprawl
			server.runCommand("execute as @a at @s facing entity @e[type=blocksouls_bosses:final_dragon,limit=1] feet run tp @s ~ ~ ~ ~ ~");
			context.waitTicks(20);
			context.takeScreenshot("16-end-final-dragon");
			server.runCommand("effect clear @a minecraft:slow_falling");

			// A v2 floor: act 1's Surface island in the floors dimension (opening act 1 sends everyone to its camp).
			server.runCommand("bs floor open 1");
			context.waitTicks(100);
			server.runCommand("bs floor light 1");
			server.runCommand("execute as @a at @s run tp @s ~ ~2 ~ 30 20");
			context.waitTicks(40);
			context.takeScreenshot("07-floor-camp");
			server.runCommand("execute as @a at @s run bs floor goto structure");
			server.runCommand("execute as @a at @s run tp @s ~-6 ~4 ~-6 -45 25");
			context.waitTicks(60);
			context.takeScreenshot("07b-floor-structure");

			// The Nether: blight and ash fog. Teleport first so the chunks load, then clear a pocket around the player
			// (a fill in unloaded chunks does nothing): a hollow glass box keeps the lava out and the view clear.
			server.runCommand("effect give @a minecraft:fire_resistance infinite 0 true");
			server.runCommand("execute in minecraft:the_nether run tp @a 0 118 0");
			context.waitTicks(60);
			server.runCommand("execute as @a at @s run fill ~-4 ~-1 ~-4 ~4 ~4 ~4 minecraft:glass hollow");
			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 10");
			context.waitTicks(100);
			context.takeScreenshot("06-nether");
		}
	}
}
