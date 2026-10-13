package io.github.tmldude.blocksouls.mobs.ai;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import io.github.tmldude.blocksouls.common.mob.WaveMobs;
import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.logic.FlankMath;

/**
 * Circles to the side the party isn't facing before going in. The party's facing is the mean look direction of the
 * players around the target; the mob orbits the target on whichever side it's already on (never across the front)
 * until it's behind, then hands over to its attack goals. Gives up after {@code giveUpTicks} and goes straight in.
 * Used by mobs carrying {@link WaveMobs#FLANKER_TAG} (the Director's flanking packs) and the {@code alwaysFlank} types.
 */
public class FlankGoal extends Goal {
	private static final double PARTY_RADIUS = 16;
	private static final double STEP_DEGREES = 40;
	private static final int REPATH_TICKS = 5;

	private final PathfinderMob mob;
	private int ticks;
	private int cooldownUntil;

	public FlankGoal(PathfinderMob mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Flag.MOVE));
	}

	private static MobsConfig.Flanking config() {
		return BlockSoulsMobs.CONFIG.flanking;
	}

	/** Whether this mob flanks at all (tagged by the Director, or a type that always does). */
	public static boolean flanks(PathfinderMob mob) {
		MobsConfig.Flanking config = config();
		return config.enabled && (mob.entityTags().contains(WaveMobs.FLANKER_TAG)
				|| config.alwaysFlank.contains(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString()));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && mob.tickCount >= cooldownUntil && flanks(mob)
				&& mob.distanceTo(target) > config().minDistance && !behind(target);
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && ticks < config().giveUpTicks && !behind(target);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		ticks = 0;
	}

	@Override
	public void tick() {
		LivingEntity target = mob.getTarget();
		if (target == null) {
			return;
		}
		mob.getLookControl().setLookAt(target);
		if (ticks++ % REPATH_TICKS != 0) {
			return;
		}
		MobsConfig.Flanking config = config();
		double facing = partyFacing(target);
		double bearing = FlankMath.bearing(facing, mob.getX() - target.getX(), mob.getZ() - target.getZ());
		double[] offset = FlankMath.offset(facing, FlankMath.nextBearing(bearing, STEP_DEGREES), config.orbitRadius);
		mob.getNavigation().moveTo(target.getX() + offset[0], target.getY(), target.getZ() + offset[1], config.speed);
	}

	@Override
	public void stop() {
		// Gave up, or got behind: go straight in for a while before trying again.
		cooldownUntil = mob.tickCount + config().giveUpTicks;
		mob.getNavigation().stop();
	}

	private boolean behind(LivingEntity target) {
		double bearing = FlankMath.bearing(partyFacing(target), mob.getX() - target.getX(), mob.getZ() - target.getZ());
		return FlankMath.behind(bearing, config().behindDegrees);
	}

	/** Mean facing of the players near the target (the target's own facing if none). */
	public static double partyFacing(LivingEntity target) {
		List<Player> players = target.level().getEntitiesOfClass(Player.class, target.getBoundingBox().inflate(PARTY_RADIUS),
				p -> p.isAlive() && !p.isSpectator());
		if (players.isEmpty()) {
			return target.getYHeadRot();
		}
		double[] yaws = new double[players.size()];
		for (int i = 0; i < yaws.length; i++) {
			yaws[i] = players.get(i).getYHeadRot();
		}
		return FlankMath.meanYaw(yaws, target.getYHeadRot());
	}
}
