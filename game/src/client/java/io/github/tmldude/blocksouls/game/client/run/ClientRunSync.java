package io.github.tmldude.blocksouls.game.client.run;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.game.run.RunSyncPayload;

/** Fills {@link ClientRun} and {@link RunSkyEffects} from the server's {@link RunSyncPayload}. */
public final class ClientRunSync {
	private ClientRunSync() {
	}

	public static void init() {
		ClientCycle.init();
		ClientNetworking.receive(RunSyncPayload.TYPE, (payload, context) -> {
			Set<Identifier> modifiers = payload.nightModifiers().stream()
					.map(Identifier::tryParse)
					.filter(Objects::nonNull)
					.collect(Collectors.toUnmodifiableSet());
			ClientRun.set(new ClientRun.View(payload.state(), payload.secondsToNext(), payload.mobsLeft(), modifiers,
					payload.actOrdinal(), payload.actName(), payload.actCount(), payload.highestOpenAct(), payload.chargeSecondsLeft()));
			ClientCycle.update(payload.syntheticTimeOfDay(), payload.syntheticRate());
			RunSkyEffects.update(payload);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			ClientRun.set(ClientRun.View.EMPTY);
			ClientCycle.reset();
			RunSkyEffects.reset();
		});
	}
}
