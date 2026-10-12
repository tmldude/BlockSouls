package io.github.tmldude.blocksouls.common.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.synergy.BuildHooks;
import io.github.tmldude.blocksouls.common.Config;
import io.github.tmldude.blocksouls.common.event.ConfigEvents;
import io.github.tmldude.blocksouls.common.party.PartyConfig;
import io.github.tmldude.blocksouls.common.party.PartyScaling;
import io.github.tmldude.blocksouls.common.player.Capabilities;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.player.Stamina;
import io.github.tmldude.blocksouls.common.player.WeaponHooks;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.common.run.RunState;
import io.github.tmldude.blocksouls.common.run.Runs;
import io.github.tmldude.blocksouls.common.run.Threat;

public class CommonGameTests {
	@GameTest
	public void traitsSetAndClear(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.DOUBLE_JUMP) == 0, "no traits by default");

		PlayerTraits.set(player, PlayerTraits.DOUBLE_JUMP, 2);
		helper.assertTrue(PlayerTraits.level(player, PlayerTraits.DOUBLE_JUMP) == 2, "trait level stored");

		PlayerTraits.set(player, PlayerTraits.DOUBLE_JUMP, 0);
		helper.assertTrue(!PlayerTraits.has(player, PlayerTraits.DOUBLE_JUMP), "level 0 removes the trait");
		helper.succeed();
	}

	@GameTest
	public void downedToggles(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(!PlayerStatus.isDowned(player), "not downed by default");
		PlayerStatus.setDowned(player, true);
		helper.assertTrue(PlayerStatus.isDowned(player), "downed after set");
		PlayerStatus.setDowned(player, false);
		helper.assertTrue(!PlayerStatus.isDowned(player), "up again");
		helper.succeed();
	}

	@GameTest
	public void runStateDefaultsToNotStarted(GameTestHelper helper) {
		// Only common is loaded in this test run, so no game module installs a provider.
		helper.assertTrue(Runs.get(helper.getLevel().getServer()) == RunState.NOT_STARTED, "no run without the game module");
		helper.succeed();
	}

	@GameTest
	public void buildHooksDefaultToNoOps(GameTestHelper helper) {
		// Only common is loaded in this test run: no game module installs a build provider, no guns or movement
		// install weapon/stamina providers. Everything must report "nothing happened" instead of failing.
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(BuildHooks.applyRandomCurse(player) == null, "no curse without a provider");
		helper.assertTrue(!BuildHooks.purgeCurse(player), "nothing to purge without a provider");
		helper.assertTrue(BuildHooks.curses(player).isEmpty() && BuildHooks.trinkets(player).isEmpty(), "nothing owned");
		helper.assertTrue(!BuildHooks.offerTrinkets(player), "no offer without a provider");
		helper.assertTrue(!Stamina.add(player, 25), "stamina is a no-op without the movement module");
		helper.assertTrue(!WeaponHooks.reload(player), "reload is a no-op without the guns module");
		helper.succeed();
	}
	private static final List<String> RELOADED = new ArrayList<>();

	static {
		ConfigEvents.RELOADED.register((name, config) -> RELOADED.add(name));
	}

	@GameTest
	public void configReloadChangesTheLiveValue(GameTestHelper helper) throws IOException {
		Path file = Config.directory().resolve("party.json");
		String original = Files.readString(file);
		PartyConfig live = PartyScaling.config();
		int before = live.aliveCapBase;
		RELOADED.clear();
		try {
			Files.writeString(file, original.replace("\"aliveCapBase\": " + before, "\"aliveCapBase\": " + (before + 7)));
			List<String> chat = new ArrayList<>();
			run(helper, chat, "bs config reload party");

			helper.assertTrue(live.aliveCapBase == before + 7, "aliveCapBase reloaded into the live config: " + live.aliveCapBase);
			helper.assertTrue(PartyScaling.config() == live, "the same instance stays live");
			helper.assertTrue(RELOADED.equals(List.of("party")), "RELOADED fired with the file name: " + RELOADED);
		} finally {
			Files.writeString(file, original);
			ConfigEvents.reload("party");
		}
		helper.assertTrue(live.aliveCapBase == before, "restored");
		helper.succeed();
	}

	@GameTest
	public void configReloadKeepsValuesOnBadJson(GameTestHelper helper) throws IOException {
		Path file = Config.directory().resolve("threat.json");
		String original = Files.readString(file);
		double before = Threat.config().bossHealth.perThreat;
		RELOADED.clear();
		try {
			Files.writeString(file, "{\"bossHealth\": {\"perThreat\": 9.0,");
			List<String> chat = new ArrayList<>();
			run(helper, chat, "bs config reload threat");

			helper.assertTrue(Threat.config().bossHealth.perThreat == before, "old values kept");
			helper.assertTrue(RELOADED.isEmpty(), "no RELOADED for a file that didn't parse");
			helper.assertTrue(chat.stream().anyMatch(line -> line.contains("Kept the current threat config")),
					"error reported in chat: " + chat);
		} finally {
			Files.writeString(file, original);
		}
		helper.succeed();
	}

	private static void run(GameTestHelper helper, List<String> chat, String command) {
		CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack().withSource(new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				chat.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		});
		helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
	}

	@GameTest
	public void capabilitiesDefaultToTheV1VerbsOnly(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(Capabilities.allGranted(player), "no set attached by default");
		helper.assertTrue(Capabilities.has(player, Capabilities.CLIMB) && Capabilities.has(player, Capabilities.SWING)
				&& Capabilities.has(player, Capabilities.MULTI_JUMP), "every v1 verb by default");
		helper.assertTrue(!Capabilities.has(player, Capabilities.GLIDE), "glide is opt-in: never granted by default");
		Capabilities.grant(player, Capabilities.GLIDE);
		helper.assertTrue(Capabilities.allGranted(player) && !Capabilities.has(player, Capabilities.GLIDE), "grant is a no-op on the default set");

		Capabilities.restrictTo(player, Set.of(Capabilities.GLIDE));
		helper.assertTrue(Capabilities.has(player, Capabilities.GLIDE) && !Capabilities.has(player, Capabilities.CLIMB), "an explicit set is exact");
		Capabilities.grant(player, Capabilities.CLIMB);
		helper.assertTrue(Capabilities.has(player, Capabilities.CLIMB), "cross-training grants onto an explicit set");
		Capabilities.grantAll(player);
		helper.assertTrue(Capabilities.allGranted(player) && !Capabilities.has(player, Capabilities.GLIDE), "back to v1");
		helper.succeed();
	}

	@GameTest
	public void downedShootingGatesCanShoot(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		boolean before = PlayerStatus.downedShooting();
		try {
			PlayerStatus.setDownedShooting(false);
			helper.assertTrue(PlayerStatus.canShoot(player), "standing players shoot");
			PlayerStatus.setDowned(player, true);
			helper.assertTrue(!PlayerStatus.canShoot(player), "v1 rule: no shooting while downed");
			PlayerStatus.setDownedShooting(true);
			helper.assertTrue(PlayerStatus.canShoot(player), "v2 downed gameplay: the pistol keeps firing");
		} finally {
			PlayerStatus.setDownedShooting(before);
			PlayerStatus.setDowned(player, false);
		}
		helper.succeed();
	}
}
