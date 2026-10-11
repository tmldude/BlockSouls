package io.github.tmldude.blocksouls.bosses.stormplate;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.Hitboxes;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;
import io.github.tmldude.blocksouls.bosses.logic.BossScaling;

/**
 * Phase 2's charged ground: puddles of static left where thunder falls (and scattered when the storm intensifies).
 * Each pulses on a fixed beat; a brighter ring fills in just before the pulse, so standing in one is a choice.
 */
final class StormPuddles {
	private static final ParticleOptions EDGE = new DustParticleOptions(0x3E6AA8, 1.2f);
	private static final ParticleOptions WARN = new DustParticleOptions(0xFFF1B0, 1.0f);

	private record Puddle(Vec3 pos, int born) {
	}

	private final List<Puddle> puddles = new ArrayList<>();
	private int clock;

	private static StormplateConfig config() {
		return StormplateKnight.config();
	}

	void add(Vec3 pos) {
		if (puddles.size() >= Math.max(1, config().puddleMax)) {
			puddles.removeFirst();
		}
		puddles.add(new Puddle(pos, clock));
	}

	void clear() {
		puddles.clear();
	}

	int size() {
		return puddles.size();
	}

	void tick(ServerLevel level, StormplateKnight boss) {
		clock++;
		StormplateConfig c = config();
		int pulse = Math.max(10, c.puddlePulseTicks);
		puddles.removeIf(p -> clock - p.born() > c.puddleLifeTicks);
		for (Puddle puddle : puddles) {
			int age = clock - puddle.born();
			int beat = age % pulse;
			Vec3 pos = puddle.pos();
			if (clock % 4 == 0) {
				Telegraphs.circle(level, EDGE, pos, c.puddleRadius);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, pos.x, pos.y + 0.15, pos.z, 2, c.puddleRadius * 0.4, 0.02,
						c.puddleRadius * 0.4, 0.02);
			}
			float warning = StormMath.puddleWarning(age, pulse, c.puddleWarnTicks);
			if (warning > 0 && beat % 2 == 0) {
				Telegraphs.circle(level, WARN, pos, c.puddleRadius * Math.min(1f, warning));
			}
			if (StormMath.puddlePulses(age, pulse)) {
				level.playSound(null, pos.x, pos.y, pos.z, StormplateSounds.PUDDLE, SoundSource.HOSTILE, 1.2f, 0.9f + level.getRandom().nextFloat() * 0.3f);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, true, pos.x, pos.y + 0.4, pos.z, 18, c.puddleRadius * 0.45, 0.3,
						c.puddleRadius * 0.45, 0.2);
				float damage = (float) BossScaling.damage(c.puddleDamage, boss.damageMultiplier(), boss.definition().phase(boss.phase()).damageMult());
				for (LivingEntity victim : Hitboxes.inCircle(boss, pos, c.puddleRadius, 1.5)) {
					victim.hurtServer(level, level.damageSources().mobAttack(boss), damage);
				}
			}
		}
	}
}
