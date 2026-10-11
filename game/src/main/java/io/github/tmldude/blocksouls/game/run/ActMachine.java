package io.github.tmldude.blocksouls.game.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

import io.github.tmldude.blocksouls.common.act.Act;
import io.github.tmldude.blocksouls.common.act.Acts;
import io.github.tmldude.blocksouls.common.event.ActEvents;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;

/**
 * The expedition's act state machine (persisted in {@link RunData}): which act the party is in, which acts
 * are open, and the player-triggered portal charge that opens the next one. The portal never pulls — acts
 * advance only when a player starts the ~3-minute defended charge and the party holds it. Physical portal
 * blocks are Stage 6; the charge is started by command/API for now ({@code /bs act charge}).
 *
 * <p>Does nothing in the classic run shape: {@code Acts} then reports the single always-open overworld act.
 */
public final class ActMachine {
	private static List<Act> acts = List.of();

	private ActMachine() {
	}

	/** Parses the act list from config (called on init and from tests after swapping config). */
	public static void load(RunConfig config) {
		List<Act> parsed = new ArrayList<>();
		for (RunConfig.ActSpec spec : config.acts) {
			Identifier id = Identifier.tryParse(spec.id);
			Identifier dimension = Identifier.tryParse(spec.dimension);
			if (id == null || dimension == null) {
				BlockSoulsGame.LOGGER.warn("Skipping act with invalid id/dimension: {} / {}", spec.id, spec.dimension);
				continue;
			}
			parsed.add(new Act(parsed.size() + 1, id, dimension, spec.name == null || spec.name.isBlank() ? spec.id : spec.name));
		}
		acts = List.copyOf(parsed);
	}

	/** Installs the {@code Acts} provider (classic reports the single overworld act). */
	static void installProvider() {
		Acts.setProvider(new Acts.Provider() {
			@Override
			public Act current(MinecraftServer server) {
				return ActMachine.current(server);
			}

			@Override
			public List<Act> all(MinecraftServer server) {
				return expedition() && !acts.isEmpty() ? acts : List.of(Acts.CLASSIC);
			}

			@Override
			public int highestOpen(MinecraftServer server) {
				return expedition() && !acts.isEmpty() ? RunData.get(server).highestOpenAct : Acts.CLASSIC.ordinal();
			}
		});
	}

	public static boolean expedition() {
		return "expedition".equals(RunSystem.config().runShape);
	}

	public static List<Act> acts() {
		return acts;
	}

	public static Act current(MinecraftServer server) {
		if (!expedition() || acts.isEmpty()) {
			return Acts.CLASSIC;
		}
		return act(RunData.get(server).actOrdinal);
	}

	private static Act act(int ordinal) {
		return acts.get(Math.min(Math.max(ordinal, 1), acts.size()) - 1);
	}

	/** The current act's level; the overworld when the act's dimension isn't registered or loaded. */
	static ServerLevel currentLevel(MinecraftServer server) {
		if (!expedition() || acts.isEmpty()) {
			return server.overworld();
		}
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, current(server).dimension()));
		return level != null ? level : server.overworld();
	}

	// ---------------------------------------------------------------- run lifecycle

	/** Expedition run start: act 1 is open, started and entered; the synthetic cycle aligns to the sun. */
	static void onRunStarted(MinecraftServer server, RunData data) {
		if (!expedition() || acts.isEmpty()) {
			return;
		}
		data.synthetic.start(server.overworld().getOverworldClockTime());
		data.setDirty();
		Act first = act(1);
		ActEvents.ACT_STARTED.invoker().onAct(server, first);
		enterIfFirst(server, data, first);
	}

	// ---------------------------------------------------------------- per-second tick

	/** Every 20 ticks while an expedition run is active: follow the party, progress the charge. */
	static void tick(MinecraftServer server, RunData data) {
		if (!expedition() || acts.isEmpty() || !data.isActiveRun()) {
			return;
		}
		int chosen = ActMath.partyAct(countPlayers(server), openDimensions(data), data.actOrdinal);
		if (chosen != data.actOrdinal) {
			if (data.chargingAct > 0) {
				// Leaving the act abandons the attempt; re-trigger it later.
				cancelCharge(server, data);
			}
			data.actOrdinal = chosen;
			data.setDirty();
			enterIfFirst(server, data, act(chosen));
			RunSync.markDirty();
		}
		if (data.chargingAct > 0) {
			tickCharge(server, data);
		}
	}

	private static List<String> openDimensions(RunData data) {
		List<String> dimensions = new ArrayList<>();
		for (int ordinal = 1; ordinal <= Math.min(data.highestOpenAct, acts.size()); ordinal++) {
			dimensions.add(act(ordinal).dimension().toString());
		}
		return dimensions;
	}

	/** Counted players per dimension: alive, not spectating, not downed (downed players don't drag the siege front). */
	private static Map<String, Integer> countPlayers(MinecraftServer server) {
		Map<String, Integer> counts = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isAlive() && !player.isSpectator() && !PlayerStatus.isDowned(player)) {
				counts.merge(player.level().dimension().identifier().toString(), 1, Integer::sum);
			}
		}
		return counts;
	}

	private static void enterIfFirst(MinecraftServer server, RunData data, Act act) {
		if (!data.actsEntered.add(act.id().toString())) {
			return;
		}
		data.setDirty();
		ActEvents.ACT_ENTERED.invoker().onAct(server, act);
		broadcast(server, "Act " + act.ordinal() + ": " + act.displayName());
	}

	// ---------------------------------------------------------------- the portal charge

	/** Starts the charge. Returns null on success, else why it can't start. */
	public static @Nullable String startCharge(MinecraftServer server, RunData data) {
		if (!expedition() || acts.isEmpty()) {
			return "The portal charge needs runShape \"expedition\" (game-run.json)";
		}
		if (!data.isActiveRun()) {
			return "No active run";
		}
		if (data.chargingAct > 0) {
			return "A charge is already running";
		}
		if (data.highestOpenAct > data.actOrdinal) {
			return "The next act's portal is already lit";
		}
		Act act = current(server);
		data.chargingAct = act.ordinal();
		data.chargeEndsAt = server.overworld().getGameTime() + RunSystem.config().portalChargeSeconds * 20L;
		data.setDirty();
		ActEvents.PORTAL_CHARGE_STARTED.invoker().onAct(server, act);
		broadcast(server, "Portal charge started — defend it! (" + RunSystem.config().portalChargeSeconds + "s)");
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			RunSystem.playTo(player, SoundEvents.RAID_HORN, 0.8F, 0.7F);
		}
		// The defense: the existing wave path at the counter as it stands, day or night.
		if (data.phase != RunPhase.WAVE && data.phase != RunPhase.WAVE_BREAK) {
			WaveManager.startWave(server, data);
		}
		RunSync.markDirty();
		return null;
	}

	private static void tickCharge(MinecraftServer server, RunData data) {
		if (server.overworld().getGameTime() >= data.chargeEndsAt) {
			completeCharge(server, data);
			return;
		}
		// Keep the pressure on for the whole charge: a cleared defense wave starts the next one.
		if (data.phase != RunPhase.WAVE && data.phase != RunPhase.WAVE_BREAK) {
			WaveManager.startWave(server, data);
		}
	}

	static void cancelCharge(MinecraftServer server, RunData data) {
		if (data.chargingAct <= 0) {
			return;
		}
		data.chargingAct = 0;
		data.chargeEndsAt = 0;
		data.setDirty();
		broadcast(server, "Portal charge abandoned");
		RunSync.markDirty();
	}

	private static void completeCharge(MinecraftServer server, RunData data) {
		Act act = act(data.chargingAct);
		data.chargingAct = 0;
		data.chargeEndsAt = 0;
		data.setDirty();
		if (data.phase == RunPhase.WAVE) {
			WaveManager.endWave(server, data, false);
		}
		ActEvents.PORTAL_CHARGE_COMPLETED.invoker().onAct(server, act);
		if (act.ordinal() < acts.size()) {
			data.highestOpenAct = Math.max(data.highestOpenAct, act.ordinal() + 1);
			data.setDirty();
			Act next = act(act.ordinal() + 1);
			ActEvents.ACT_STARTED.invoker().onAct(server, next);
			broadcast(server, "The portal to " + next.displayName() + " is lit");
		} else {
			broadcast(server, "The final portal is lit");
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			RunSystem.playTo(player, SoundEvents.RAID_HORN, 0.6F, 1.3F);
		}
		RunSync.markDirty();
	}

	/** Debug ({@code /bs act advance}): opens the next act and makes it current without a charge. */
	public static @Nullable String advance(MinecraftServer server, RunData data) {
		if (!expedition() || acts.isEmpty()) {
			return "Needs runShape \"expedition\" (game-run.json)";
		}
		if (!data.isActiveRun()) {
			return "No active run";
		}
		if (data.actOrdinal >= acts.size()) {
			return "Already in the last act";
		}
		int next = data.actOrdinal + 1;
		data.highestOpenAct = Math.max(data.highestOpenAct, next);
		data.actOrdinal = next;
		data.setDirty();
		Act act = act(next);
		ActEvents.ACT_STARTED.invoker().onAct(server, act);
		enterIfFirst(server, data, act);
		RunSync.markDirty();
		return null;
	}

	private static void broadcast(MinecraftServer server, String message) {
		server.getPlayerList().broadcastSystemMessage(Component.literal(message).withStyle(ChatFormatting.GOLD), false);
	}
}
