package io.github.tmldude.blocksouls.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class AshFogTest {
	@Test
	void blendGoesFromVanillaToAsh() {
		assertArrayEquals(new float[] {0.2F, 0.0F, 0.0F}, AshFog.blend(0.2F, 0.0F, 0.0F, 0.0F), 1e-6F);
		assertArrayEquals(AshFog.ASH, AshFog.blend(0.2F, 0.0F, 0.0F, 1.0F), 1e-6F);
		assertArrayEquals(AshFog.ASH, AshFog.blend(0.2F, 0.0F, 0.0F, 5.0F), 1e-6F);
	}

	@Test
	void thickenNeverThins() {
		assertArrayEquals(new float[] {4, 48}, AshFog.thicken(10, 96, 4, 48), 1e-6F);
		// Already thicker (e.g. blindness): left alone.
		assertArrayEquals(new float[] {0, 5}, AshFog.thicken(0, 5, 4, 48), 1e-6F);
	}
}
