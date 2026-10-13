package io.github.tmldude.blocksouls.bosses.archetype;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.AttackRun;
import io.github.tmldude.blocksouls.bosses.api.AttackStats;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;

/**
 * A reusable attack shape (PLAN2 §3h): rings, pools, crosses, beams, orbs, spirals, summons, feints and the movement
 * moves. A boss builds one with its own {@link Theme} and a {@code Params} block from its config, so the numbers stay
 * tunable per boss. Every archetype calls {@link AttackRun#telegraphed()} the first time it draws its telegraph, which
 * the gametests use to prove the telegraph always comes before the hit.
 */
public abstract class Archetype extends Attack {
	protected final Theme theme;
	private final @Nullable String animation;

	protected Archetype(String name, AttackStats stats, Theme theme, @Nullable String animation) {
		super(name, stats);
		this.theme = theme;
		this.animation = animation;
	}

	@Override
	public String animation() {
		return animation != null ? animation : name();
	}

	@Override
	public void start(AttackRun run) {
		run.sound(theme.windup(), 1.8f, 0.9f + run.random().nextFloat() * 0.2f);
	}

	/** Players in the fight (or the run's target when there are none, e.g. in a test). */
	protected static List<LivingEntity> fighters(AttackRun run) {
		BossEntity boss = run.boss();
		List<LivingEntity> out = new ArrayList<>();
		for (ServerPlayer player : boss.participants()) {
			if (boss.inFight(player) && !player.isCreative() && !player.isSpectator()) {
				out.add(player);
			}
		}
		if (out.isEmpty() && run.target() != null) {
			out.add(run.target());
		}
		return out;
	}

	/** The floor under {@code pos}: searches a few blocks up and {@code depth} down for a standable surface. */
	public static Vec3 ground(ServerLevel level, Vec3 pos, int depth) {
		BlockPos start = BlockPos.containing(pos);
		for (int dy = 1; dy >= -depth; dy--) {
			BlockPos at = start.offset(0, dy, 0);
			if (level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)
					&& level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
				return new Vec3(pos.x, at.getY(), pos.z);
			}
		}
		return pos;
	}

	public static Vec3 ground(ServerLevel level, Vec3 pos) {
		return ground(level, pos, 6);
	}

	/** {@code dir} turned by {@code degrees} around the vertical axis. */
	public static Vec3 rotate(Vec3 dir, double degrees) {
		double r = Math.toRadians(degrees);
		double cos = Math.cos(r);
		double sin = Math.sin(r);
		return new Vec3(dir.x * cos - dir.z * sin, 0, dir.x * sin + dir.z * cos);
	}

	/** Pins {@code point} inside the boss's arena (horizontally), so a move never lands outside the fog wall. */
	protected static Vec3 inArena(BossEntity boss, Vec3 point) {
		Vec3 home = Vec3.atBottomCenterOf(boss.home());
		double radius = Math.max(2, boss.arenaRadius() - 1.5);
		double dx = point.x - home.x;
		double dz = point.z - home.z;
		double d = Math.sqrt(dx * dx + dz * dz);
		if (d <= radius) {
			return point;
		}
		return new Vec3(home.x + dx / d * radius, point.y, home.z + dz / d * radius);
	}
}
