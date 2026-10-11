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
		ClientNetworking.receive(RunSyncPayload.TYPE, (payload, context) -> {
			Set<Identifier> modifiers = payload.nightModifiers().stream()
					.map(Identifier::tryParse)
					.filter(Objects::nonNull)
					.collect(Collectors.toUnmodifiableSet());
			ClientRun.set(new ClientRun.View(payload.state(), payload.secondsToNext(), payload.mobsLeft(), modifiers));
			RunSkyEffects.update(payload);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			ClientRun.set(ClientRun.View.EMPTY);
			RunSkyEffects.reset();
		});
	}
}
