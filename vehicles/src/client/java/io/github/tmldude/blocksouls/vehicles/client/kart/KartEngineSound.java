package io.github.tmldude.blocksouls.vehicles.client.kart;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import io.github.tmldude.blocksouls.vehicles.VehicleRegistry;
import io.github.tmldude.blocksouls.vehicles.kart.KartEntity;

/** Engine loop that follows a kart while someone's in it; pitch and volume rise with speed. */
public class KartEngineSound extends AbstractTickableSoundInstance {
	private final KartEntity kart;
	private float smoothedSpeed;

	public KartEngineSound(KartEntity kart) {
		super(VehicleRegistry.KART_ENGINE, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.kart = kart;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.3f;
		this.pitch = 0.5f;
		this.x = kart.getX();
		this.y = kart.getY();
		this.z = kart.getZ();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (kart.isRemoved() || !kart.isVehicle()) {
			stop();
			return;
		}
		x = kart.getX();
		y = kart.getY();
		z = kart.getZ();
		float speed = (float) Math.sqrt(Mth.square(kart.getX() - kart.xo) + Mth.square(kart.getZ() - kart.zo));
		smoothedSpeed += (speed - smoothedSpeed) * 0.3f;
		pitch = 0.5f + Mth.clamp(smoothedSpeed, 0, 1) * 1.1f;
		volume = 0.3f + Mth.clamp(smoothedSpeed, 0, 1) * 0.5f;
	}
}
