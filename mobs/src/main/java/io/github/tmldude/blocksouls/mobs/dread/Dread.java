package io.github.tmldude.blocksouls.mobs.dread;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.mobs.BlockSoulsMobs;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.entity.AshHound;
import io.github.tmldude.blocksouls.mobs.logic.DreadMeter;
import io.github.tmldude.blocksouls.mobs.registry.MobEntities;
import io.github.tmldude.blocksouls.mobs.registry.MobSounds;

/**
 * Hidden Nether pressure. Each player's dread is a saved attachment (never shown in a HUD). Once a second the
 * server advances every player's {@link DreadMeter}: distant howls and ash particles grow with it, and packs of
 * Ash Hounds are sent to hunt the player.
 */
public final class Dread {
	private static final AttachmentType<Double> DREAD = AttachmentRegistry.create(
			CommonIds.id(CommonIds.MOBS, "dread"), builder -> builder.persistent(Codec.DOUBLE).copyOnDeath());

	/** Live meters (pack timers aren't saved: a restart counts as a fresh visit). */
	private static final Map<UUID, DreadMeter> METERS = new HashMap<>();

	private Dread() {
	}

	private static MobsConfig.Dread config() {
		return BlockSoulsMobs.CONFIG.dread;
	}

	public static DreadMeter.Settings settings() {
		MobsConfig.Dread c = config();
		return new DreadMeter.Settings(c.risePerSecond, c.decayPerSecond, c.howlAt, c.firstPackAt, c.packEverySeconds, c.packGrowth, c.packCap);
	}

	public static double get(ServerPlayer player) {
		return meter(player).dread();
	}

	public static void set(ServerPlayer player, double value) {
		DreadMeter meter = meter(player);
		meter.setDread(value);
		((AttachmentTarget) player).setAttached(DREAD, meter.dread());
	}

	private static DreadMeter meter(ServerPlayer player) {
		return METERS.computeIfAbsent(player.getUUID(), id -> {
			Double saved = ((AttachmentTarget) player).getAttached(DREAD);
			return new DreadMeter(saved == null ? 0 : saved);
		});
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Dread::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> METERS.remove(handler.getPlayer().getUUID()));
	}

	private static void tick(MinecraftServer server) {
		if (!config().enabled || server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
				continue;
			}
			tickPlayer(player);
		}
	}

	/** One second for one player. Public so tests and commands can drive it. */
	public static void tickPlayer(ServerPlayer player) {
		MobsConfig.Dread c = config();
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		boolean inNether = level.dimension() == Level.NETHER;
		DreadMeter meter = meter(player);
		int base = c.packMin + random.nextInt(Math.max(1, c.packMax - c.packMin + 1));
		DreadMeter.Result result = meter.tick(inNether, 1, settings(), base, random.nextDouble(), 1 / Math.max(1, c.howlEverySeconds));
		((AttachmentTarget) player).setAttached(DREAD, meter.dread() > 0 ? meter.dread() : null);

		if (!inNether) {
			return;
		}
		double intensity = DreadMeter.intensity(meter.dread(), settings());
		if (intensity > 0) {
			// Only this player sees the ash thicken.
			int count = (int) Math.round(4 + 36 * intensity);
			level.sendParticles(player, ParticleTypes.WHITE_ASH, false, false, player.getX(), player.getEyeY(), player.getZ(),
					count, 4, 2, 4, 0.01);
			if (intensity > 0.6) {
				level.sendParticles(player, ParticleTypes.ASH, false, false, player.getX(), player.getEyeY(), player.getZ(),
						count / 2, 3, 2, 3, 0.01);
			}
		}
		if (result.howl()) {
			howl(player, 0.4f + 0.6f * (float) intensity);
		}
		if (result.packSize() > 0) {
			spawnPack(player, result.packSize());
		}
	}

	/** A howl from a random direction far off, heard only by this player. */
	private static void howl(ServerPlayer player, float volume) {
		RandomSource random = player.getRandom();
		double angle = random.nextDouble() * Math.PI * 2;
		Vec3 at = player.position().add(Math.cos(angle) * 12, 0, Math.sin(angle) * 12);
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(MobSounds.DREAD_HOWL), SoundSource.HOSTILE, at.x, at.y, at.z, volume,
				0.8f + random.nextFloat() * 0.3f, random.nextLong()));
	}

	/** Spawns {@code size} Ash Hounds 20–30 blocks from the player, already hunting them. Returns how many spawned. */
	public static int spawnPack(ServerPlayer player, int size) {
		ServerLevel level = player.level();
		MobsConfig.Dread c = config();
		RandomSource random = player.getRandom();
		BlockPos center = null;
		for (int attempt = 0; attempt < 20 && center == null; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double dist = c.spawnMinDistance + random.nextDouble() * (c.spawnMaxDistance - c.spawnMinDistance);
			center = findGround(level, BlockPos.containing(player.getX() + Math.cos(angle) * dist, player.getY(), player.getZ() + Math.sin(angle) * dist));
		}
		if (center == null) {
			return 0;
		}
		howl(player, 1.0f);
		int spawned = 0;
		for (int i = 0; i < size; i++) {
			BlockPos pos = findGround(level, center.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2));
			if (pos == null) {
				pos = center;
			}
			AshHound hound = MobEntities.ASH_HOUND.create(level, EntitySpawnReason.EVENT);
			if (hound == null) {
				continue;
			}
			hound.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360, 0);
			hound.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
			hound.setTarget(player);
			if (level.addFreshEntity(hound)) {
				spawned++;
			}
		}
		return spawned;
	}

	/** A standable spot (solid floor, two air blocks) within 8 blocks up or down of {@code pos}, or null. */
	private static @Nullable BlockPos findGround(ServerLevel level, BlockPos pos) {
		for (int dy = 0; dy <= 8; dy++) {
			for (int sign : new int[] {-1, 1}) {
				BlockPos p = pos.above(dy * sign);
				if (level.getBlockState(p.below()).isSolidRender() && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
						&& level.getFluidState(p.below()).isEmpty()) {
					return p;
				}
			}
		}
		return null;
	}
}
