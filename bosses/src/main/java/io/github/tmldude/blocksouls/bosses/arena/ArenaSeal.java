package io.github.tmldude.blocksouls.bosses.arena;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.impl.attachment.AttachmentRegistryImpl;

import io.github.tmldude.blocksouls.bosses.BossesConfig;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.registry.BossSounds;

/**
 * The server-side half of the fog wall: while a fog-wall boss is engaged, a participant who is inside the arena can't
 * leave it and nobody else can enter it, at any height (the visible wall only goes {@code fogWallAbove} up). Enforced
 * every boss tick by putting anyone who crossed back where they were, with a small push and the fog wall's sound;
 * ender pearls and chorus fruit across the line are cancelled ({@code arena.mixin}); web ropes across it snap.
 * Spectators and creative players ignore it. See {@code docs/decisions/arena-seal.md}.
 */
public final class ArenaSeal {
	/** Engaged seals, for the teleport checks (server thread only). */
	private static final Set<ArenaSeal> ACTIVE = new LinkedHashSet<>();

	// The movement module's web swing, by id (feature modules don't import each other). Absent without movement.
	private static final Identifier WEB_ANCHOR = Identifier.fromNamespaceAndPath("blocksouls_movement", "web_anchor");
	private static final Identifier WEB_ROPE_LENGTH = Identifier.fromNamespaceAndPath("blocksouls_movement", "web_rope_length");
	/** A rope length the movement server always finds "too far" from, so it drops the swing and tells the client. */
	private static final double SNAPPED_ROPE = -1000;

	private final BossEntity boss;
	private final Map<UUID, Track> tracks = new HashMap<>();
	private boolean active;
	private int bounces;

	/** Where a player was last tick and on which side. */
	private static final class Track {
		final ServerPlayer player;
		Vec3 last;
		boolean inside;
		boolean ropeSnapped;

		Track(ServerPlayer player, Vec3 last, boolean inside) {
			this.player = player;
			this.last = last;
			this.inside = inside;
		}
	}

	public ArenaSeal(BossEntity boss) {
		this.boss = boss;
	}

	private static BossesConfig config() {
		return BossEntity.framework();
	}

	public boolean isActive() {
		return active;
	}

	/** Times someone was put back since the seal went up (tests and debug). */
	public int bounces() {
		return bounces;
	}

	/** Closes the seal (on engage, for fog-wall bosses). Whoever stands inside now is on the inside. */
	public void activate(ServerLevel level) {
		active = true;
		bounces = 0;
		tracks.clear();
		ACTIVE.add(this);
		record(level);
	}

	/** Opens it (reset, death, removal). */
	public void release() {
		active = false;
		tracks.clear();
		ACTIVE.remove(this);
	}

	/** Arena center: the home block's bottom center. */
	public Vec3 center() {
		return Vec3.atBottomCenterOf(boss.home());
	}

	/** Whether a position is inside the sealed cylinder. */
	public boolean isInside(Vec3 pos) {
		Vec3 center = center();
		return SealMath.inside(pos.x - center.x, pos.y - center.y, pos.z - center.z, boss.arenaRadius(), config().sealHeight);
	}

	private static boolean ignores(ServerPlayer player) {
		return player.isSpectator() || player.isCreative() || player.isDeadOrDying();
	}

	private boolean participant(ServerPlayer player) {
		return boss.participantIds().contains(player.getUUID());
	}

	/** Every boss tick while engaged. */
	public void tick(ServerLevel level) {
		if (!active) {
			return;
		}
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayer player : List.copyOf(level.players())) {
			UUID id = player.getUUID();
			if (ignores(player)) {
				tracks.remove(id);
				continue;
			}
			seen.add(id);
			Vec3 pos = player.position();
			boolean inside = isInside(pos);
			Track track = tracks.get(id);
			if (track == null || track.player != player) {
				// New here, respawned or back from another dimension: wherever they are now is their side.
				tracks.put(id, track = new Track(player, pos, inside));
			} else {
				SealMath.Verdict verdict = SealMath.judge(track.inside, inside, participant(player), pos.distanceTo(track.last),
						config().sealMaxCatchDistance);
				if (verdict == SealMath.Verdict.ALLOW) {
					track.last = pos;
					track.inside = inside;
				} else {
					bounce(level, player, track.last, pos, verdict == SealMath.Verdict.KEEP_IN);
				}
			}
			snapRope(level, track);
		}
		tracks.keySet().retainAll(seen);
	}

	private void record(ServerLevel level) {
		for (ServerPlayer player : level.players()) {
			if (!ignores(player)) {
				tracks.put(player.getUUID(), new Track(player, player.position(), isInside(player.position())));
			}
		}
	}

	/** Puts the player back at {@code back} (their side), stops flight, pushes them gently away from the line. */
	private void bounce(ServerLevel level, ServerPlayer player, Vec3 back, Vec3 tried, boolean inward) {
		bounces++;
		if (player.isPassenger()) {
			player.stopRiding();
		}
		if (player.isFallFlying()) {
			player.stopFallFlying();
		}
		if (player.getAbilities().flying) {
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
		Vec3 center = center();
		double[] push = SealMath.push(back.x - center.x, back.z - center.z, inward, config().pushStrength);
		Vec3 motion = new Vec3(push[0], config().pushStrength * 0.3, push[1]);
		player.connection.teleport(new PositionMoveRotation(back, motion, player.getYRot(), player.getXRot()), Set.of());
		player.resetFallDistance();
		hitEffects(level, back, tried);
	}

	/** The fog wall's sound and particles where the line between {@code from} and {@code to} meets the seal. */
	private void hitEffects(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 center = center();
		double t = SealMath.crossingFraction(from.x - center.x, from.z - center.z, to.x - center.x, to.z - center.z, boss.arenaRadius());
		Vec3 hit = from.lerp(to, t);
		level.sendParticles(ParticleTypes.WHITE_ASH, hit.x, hit.y + 1, hit.z, 30, 0.4, 0.8, 0.4, 0.02);
		level.sendParticles(ParticleTypes.CLOUD, hit.x, hit.y + 1, hit.z, 6, 0.3, 0.6, 0.3, 0.01);
		level.playSound(null, BlockPos.containing(hit), BossSounds.FOG_WALL_CLOSE, SoundSource.BLOCKS, 0.8f, 1.4f);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Web ropes

	@SuppressWarnings("unchecked")
	private static <T> @Nullable AttachmentType<T> attachment(Identifier id) {
		return (AttachmentType<T>) AttachmentRegistryImpl.get(id);
	}

	/** A rope whose anchor is across the line in a direction the player may not go snaps. */
	private void snapRope(ServerLevel level, Track track) {
		AttachmentType<Vec3> anchorType = attachment(WEB_ANCHOR);
		AttachmentType<Double> lengthType = attachment(WEB_ROPE_LENGTH);
		ServerPlayer player = track.player;
		Object anchor = anchorType == null ? null : ((AttachmentTarget) player).getAttached(anchorType);
		if (!(anchor instanceof Vec3 point) || lengthType == null) {
			track.ropeSnapped = false;
			return;
		}
		boolean playerInside = isInside(player.position());
		if (SealMath.judge(playerInside, isInside(point), participant(player), 0, Double.MAX_VALUE) == SealMath.Verdict.ALLOW) {
			track.ropeSnapped = false;
			return;
		}
		// Every tick until the movement server lets go (it pays rope out instead while the player is on the ground).
		if (((AttachmentTarget) player).getAttached(lengthType) instanceof Double) {
			((AttachmentTarget) player).setAttached(lengthType, SNAPPED_ROPE);
		}
		if (!track.ropeSnapped) {
			track.ropeSnapped = true;
			hitEffects(level, player.position().add(0, 1, 0), point);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COBWEB_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
		}
	}

	// ---------------------------------------------------------------------------------------------------------
	// Teleports (ender pearls, chorus fruit)

	/**
	 * Whether {@code player} may teleport to {@code to} without crossing an engaged seal the wrong way. When not, and
	 * {@code effects}, plays the fog wall hit where the line was crossed.
	 */
	public static boolean allowsTeleport(ServerPlayer player, Vec3 to, boolean effects) {
		if (ACTIVE.isEmpty() || ignores(player)) {
			return true;
		}
		for (ArenaSeal seal : ACTIVE) {
			if (!seal.active || !(seal.boss.level() instanceof ServerLevel level) || player.level() != level) {
				continue;
			}
			Vec3 from = player.position();
			SealMath.Verdict verdict = SealMath.judge(seal.isInside(from), seal.isInside(to), seal.participant(player), 0, Double.MAX_VALUE);
			if (verdict != SealMath.Verdict.ALLOW) {
				if (effects) {
					seal.hitEffects(level, from, to);
				}
				return false;
			}
		}
		return true;
	}
}
