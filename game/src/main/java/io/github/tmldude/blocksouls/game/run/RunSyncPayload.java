package io.github.tmldude.blocksouls.game.run;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.run.Difficulty;
import io.github.tmldude.blocksouls.common.run.RunPhase;
import io.github.tmldude.blocksouls.common.run.RunState;

/**
 * Server → client: the run state for the HUD and the sky/fog cues.
 *
 * @param secondsToNext      seconds until the next wave (break or before dusk), -1 when not counting down
 * @param mobsLeft           wave mobs still alive or still to spawn, -1 outside a wave
 * @param nightModifiers     tonight's modifier ids (empty during the day)
 * @param duskCueTime        day time when the dusk cue starts (the client tints sky/fog from here to dawn)
 * @param fogDistance        fog end distance on fog nights
 * @param actOrdinal         the party's act (1-based; 1 with a single act in classic)
 * @param actName            the act's display name
 * @param actCount           acts in the run (1 in classic)
 * @param highestOpenAct     highest act whose portal is lit
 * @param chargeSecondsLeft  seconds left on the running portal charge, -1 when none
 * @param syntheticTimeOfDay the run's synthetic cycle phase (mod 24000), -1 in classic / before the run
 * @param syntheticRate      day-time ticks per game tick the synthetic cycle advances at (0 when none)
 */
public record RunSyncPayload(RunState state, int secondsToNext, int mobsLeft, List<String> nightModifiers, int duskCueTime,
		int duskTime, int dawnTime, float fogDistance,
		int actOrdinal, String actName, int actCount, int highestOpenAct, int chargeSecondsLeft,
		int syntheticTimeOfDay, float syntheticRate) implements CustomPacketPayload {
	public static final Type<RunSyncPayload> TYPE = Networking.type(RunIds.NAMESPACE, "run_sync");
	public static final StreamCodec<RegistryFriendlyByteBuf, RunSyncPayload> CODEC = StreamCodec.ofMember(RunSyncPayload::write, RunSyncPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(state.phase().ordinal());
		buf.writeVarInt(state.wave());
		buf.writeVarInt(state.night());
		buf.writeBoolean(state.endless());
		Difficulty difficulty = state.difficulty();
		buf.writeUtf(difficulty.id());
		buf.writeDouble(difficulty.waveSize());
		buf.writeDouble(difficulty.mobHealth());
		buf.writeDouble(difficulty.mobDamage());
		buf.writeVarInt(secondsToNext);
		buf.writeVarInt(mobsLeft);
		buf.writeVarInt(nightModifiers.size());
		for (String id : nightModifiers) {
			buf.writeUtf(id);
		}
		buf.writeVarInt(duskCueTime);
		buf.writeVarInt(duskTime);
		buf.writeVarInt(dawnTime);
		buf.writeFloat(fogDistance);
		buf.writeVarInt(actOrdinal);
		buf.writeUtf(actName);
		buf.writeVarInt(actCount);
		buf.writeVarInt(highestOpenAct);
		buf.writeVarInt(chargeSecondsLeft);
		buf.writeVarInt(syntheticTimeOfDay);
		buf.writeFloat(syntheticRate);
	}

	private static RunSyncPayload read(FriendlyByteBuf buf) {
		RunPhase[] phases = RunPhase.values();
		int phaseIndex = buf.readVarInt();
		RunPhase phase = phaseIndex >= 0 && phaseIndex < phases.length ? phases[phaseIndex] : RunPhase.NOT_STARTED;
		int wave = buf.readVarInt();
		int night = buf.readVarInt();
		boolean endless = buf.readBoolean();
		Difficulty difficulty = new Difficulty(buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble());
		int secondsToNext = buf.readVarInt();
		int mobsLeft = buf.readVarInt();
		int count = Math.min(buf.readVarInt(), 64);
		List<String> modifiers = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			modifiers.add(buf.readUtf());
		}
		return new RunSyncPayload(new RunState(phase, wave, night, endless, difficulty), secondsToNext, mobsLeft, List.copyOf(modifiers),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
				buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readFloat());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
