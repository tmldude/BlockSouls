package io.github.tmldude.blocksouls.game.client.run;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import io.github.tmldude.blocksouls.game.run.WaveMath;

/**
 * The client's copy of the run's synthetic day/night cycle (dimensions without a sun). The server syncs
 * the phase about once a second; between packets the client advances it locally at the synced rate, so
 * fog/light lerps stay smooth.
 */
public final class ClientCycle {
	private static int timeOfDay = -1;
	private static float rate;
	private static float remainder;

	private ClientCycle() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (timeOfDay < 0 || client.level == null || client.isPaused()) {
				return;
			}
			remainder += rate;
			int whole = (int) remainder;
			if (whole > 0) {
				timeOfDay = WaveMath.timeOfDay(timeOfDay + whole);
				remainder -= whole;
			}
		});
	}

	/** From the sync payload: -1 turns the cycle off (classic, or no run). */
	static void update(int syncedTimeOfDay, float syncedRate) {
		if (syncedTimeOfDay < 0) {
			reset();
			return;
		}
		timeOfDay = WaveMath.timeOfDay(syncedTimeOfDay);
		rate = Math.max(0, syncedRate);
	}

	static void reset() {
		timeOfDay = -1;
		rate = 0;
		remainder = 0;
	}

	/** The run's synthetic phase (0–23999), or -1 when no synthetic cycle is running. */
	public static int timeOfDay() {
		return timeOfDay;
	}

	/**
	 * The phase of the player's own dimension: the overworld reads its sun, any other dimension reads the
	 * synthetic cycle; -1 when neither applies.
	 */
	public static int phaseForCurrentDimension() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return -1;
		}
		if (level.dimension() == Level.OVERWORLD) {
			return WaveMath.timeOfDay(level.getOverworldClockTime());
		}
		return timeOfDay;
	}
}
