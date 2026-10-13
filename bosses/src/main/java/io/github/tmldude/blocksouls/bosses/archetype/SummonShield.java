package io.github.tmldude.blocksouls.bosses.archetype;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.Telegraphs;
import io.github.tmldude.blocksouls.common.mob.WaveContext;
import io.github.tmldude.blocksouls.common.mob.WaveMobSpec;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;
import io.github.tmldude.blocksouls.common.run.Runs;

/**
 * Summon-and-shield: the boss calls adds (wave mobs from {@code common}'s registry, zombies without the mobs module)
 * and a shield goes up that only drops once every add is dead. Switch targets or wait it out.
 */
public class SummonShield extends Archetype {
	private final ArchetypeParams.Summon params;

	public SummonShield(String name, ArchetypeParams.Summon params, Theme theme, @Nullable String animation) {
		super(name, params.stats, theme, animation);
		this.params = params;
	}

	@Override
	public boolean canUse(BossEntity boss, LivingEntity target) {
		return !boss.isShielded();
	}

	@Override
	public void start(AttackRun run) {
		super.start(run);
		int n = Math.max(1, count(run.boss(), params.adds));
		for (int i = 0; i < n; i++) {
			double a = i * 2 * Math.PI / n + run.random().nextDouble() * 0.4;
			Vec3 at = inArena(run.boss(), run.boss().position().add(Math.cos(a) * params.radius, 0, Math.sin(a) * params.radius));
			run.points().add(ground(run.level(), at));
		}
	}

	@Override
	public void windup(AttackRun run) {
		if (run.tick() % 3 == 0) {
			for (Vec3 point : run.points()) {
				Telegraphs.circleFilling(run.level(), theme.outline(), theme.fill(), point, 0.8, run.progress());
				Telegraphs.at(run.level(), ParticleTypes.REVERSE_PORTAL, point.add(0, 1, 0), 4, 0.3, 0.05);
			}
			run.telegraphed();
		}
	}

	@Override
	public void active(AttackRun run) {
		if (!run.first()) {
			return;
		}
		ServerLevel level = run.level();
		List<WaveMobSpec> specs = roster(params.tier);
		WaveContext context = new WaveContext(params.tier, Runs.get(level.getServer()).difficulty(), Set.of(), false);
		List<Mob> spawned = new ArrayList<>();
		for (Vec3 point : run.points()) {
			BlockPos pos = BlockPos.containing(point);
			Mob mob = create(level, specs, pos, context);
			if (mob == null) {
				continue;
			}
			mob.snapTo(point.x, point.y, point.z, run.random().nextFloat() * 360, 0);
			mob.setPersistenceRequired();
			mob.setGlowingTag(true);
			level.addFreshEntity(mob);
			if (run.target() != null) {
				mob.setTarget(run.target());
			}
			spawned.add(mob);
			Telegraphs.at(level, ParticleTypes.REVERSE_PORTAL, point.add(0, 1, 0), 30, 0.4, 0.1);
		}
		run.boss().shieldUntilDead(spawned, params.maxShieldTicks);
		run.sound(theme.impact(), 2.5f, 0.6f);
	}

	/** How many adds this summon calls ({@code adds} from the config); bosses override to scale it. */
	protected int count(BossEntity boss, int adds) {
		return adds;
	}

	/** The wave mobs the adds are drawn from: the classic roster up to {@code tier}; bosses override to narrow it. */
	protected List<WaveMobSpec> roster(int tier) {
		return WaveMobs.eligible(tier);
	}

	private static @Nullable Mob create(ServerLevel level, List<WaveMobSpec> specs, BlockPos pos, WaveContext context) {
		if (!specs.isEmpty()) {
			int total = specs.stream().mapToInt(WaveMobSpec::weight).sum();
			int roll = level.getRandom().nextInt(Math.max(1, total));
			for (WaveMobSpec spec : specs) {
				roll -= spec.weight();
				if (roll < 0) {
					Mob mob = spec.factory().create(level, pos, context);
					if (mob != null) {
						return mob;
					}
					break;
				}
			}
		}
		return EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
	}
}
