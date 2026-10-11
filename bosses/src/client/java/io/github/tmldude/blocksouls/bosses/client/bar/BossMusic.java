package io.github.tmldude.blocksouls.bosses.client.bar;

import java.util.Collection;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/** Loops the music of the boss you're fighting and keeps vanilla's music quiet meanwhile. */
final class BossMusic {
	private static @Nullable SimpleSoundInstance playing;
	private static @Nullable Identifier playingId;

	private BossMusic() {
	}

	static void tick(Collection<ClientBossBars.Bar> bars) {
		Minecraft minecraft = Minecraft.getInstance();
		Identifier wanted = null;
		for (ClientBossBars.Bar bar : bars) {
			if (bar.inFight && bar.engaged && bar.music.isPresent()) {
				wanted = bar.music.get();
				break;
			}
		}
		if (wanted == null) {
			stop(minecraft);
			return;
		}
		minecraft.getMusicManager().stopPlaying();
		if (wanted.equals(playingId) && playing != null && minecraft.getSoundManager().isActive(playing)) {
			return;
		}
		stop(minecraft);
		playing = new SimpleSoundInstance(wanted, SoundSource.MUSIC, 1.0f, 1.0f, SoundInstance.createUnseededRandom(), true, 0,
				SoundInstance.Attenuation.NONE, 0, 0, 0, true);
		playingId = wanted;
		minecraft.getSoundManager().play(playing);
	}

	private static void stop(Minecraft minecraft) {
		if (playing != null) {
			minecraft.getSoundManager().stop(playing);
			playing = null;
			playingId = null;
		}
	}
}
