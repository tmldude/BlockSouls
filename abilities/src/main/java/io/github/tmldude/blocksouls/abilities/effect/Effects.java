package io.github.tmldude.blocksouls.abilities.effect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Server-side timed effects: lobbed projectiles (a simulated arc, no entity) and zones (a spot that pulses for a
 * while). Each is a tick function that returns whether it is still running; the list is ticked at the end of every
 * server tick and cleared when the server stops.
 */
public final class Effects {
	@FunctionalInterface
	public interface Ticking {
		/** @return whether to keep ticking */
		boolean tick();
	}

	private static final List<Ticking> ACTIVE = new ArrayList<>();
	private static final List<Ticking> PENDING = new ArrayList<>();

	private Effects() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Effects::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ACTIVE.clear();
			PENDING.clear();
		});
	}

	public static void add(Ticking effect) {
		PENDING.add(effect);
	}

	public static int active() {
		return ACTIVE.size() + PENDING.size();
	}

	private static void tick(MinecraftServer server) {
		ACTIVE.addAll(PENDING);
		PENDING.clear();
		for (Iterator<Ticking> it = ACTIVE.iterator(); it.hasNext();) {
			if (!it.next().tick()) {
				it.remove();
			}
		}
	}

	/**
	 * A lobbed projectile: starts at {@code from} with {@code velocity} (blocks/tick), falls by {@code gravity} each
	 * tick, leaves {@code trail} particles, and lands on the first block it touches or when the fuse runs out.
	 */
	public static void lob(ServerLevel level, Vec3 from, Vec3 velocity, double gravity, int fuseTicks, ParticleOptions trail, Consumer<Vec3> onLand) {
		add(new Ticking() {
			Vec3 pos = from;
			Vec3 vel = velocity;
			int ticks;

			@Override
			public boolean tick() {
				Vec3 next = pos.add(vel);
				BlockHitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
				if (hit.getType() != HitResult.Type.MISS) {
					onLand.accept(hit.getLocation());
					return false;
				}
				pos = next;
				vel = new Vec3(vel.x * 0.99, vel.y - gravity, vel.z * 0.99);
				level.sendParticles(trail, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
				if (++ticks >= fuseTicks) {
					onLand.accept(pos);
					return false;
				}
				return true;
			}
		});
	}

	/** Calls {@code pulse(tick)} every {@code everyTicks} for {@code durationTicks}, then {@code onEnd}. */
	public static void zone(int durationTicks, int everyTicks, IntConsumer pulse, Runnable onEnd) {
		add(new Ticking() {
			int ticks;

			@Override
			public boolean tick() {
				if (ticks % Math.max(1, everyTicks) == 0) {
					pulse.accept(ticks);
				}
				if (++ticks >= durationTicks) {
					onEnd.run();
					return false;
				}
				return true;
			}
		});
	}

	public static void zone(int durationTicks, int everyTicks, IntConsumer pulse) {
		zone(durationTicks, everyTicks, pulse, () -> { });
	}

	/** Runs {@code action} once after {@code delayTicks}. */
	public static void after(int delayTicks, Runnable action) {
		zone(Math.max(1, delayTicks), Integer.MAX_VALUE, tick -> { }, action);
	}
}
