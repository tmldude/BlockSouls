package io.github.tmldude.blocksouls.bosses.raid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.Telegraphs;
import io.github.tmldude.blocksouls.bosses.raid.logic.CrystalOrder;
import io.github.tmldude.blocksouls.bosses.raid.logic.RaidMath;

/**
 * Phase 3's kill switch, "The Crystal Order". Crystals stand on the stumps of the fallen towers; the obelisk at the
 * island center plays the order they must die in, one crystal at a time: that crystal flares in its sequence color,
 * beams at the obelisk and a chime rings, each note higher than the last. Then a pause, and the show repeats. Rings on
 * the obelisk count progress (one per crystal; lit = done). The crystals are sealed (dark, a violet shell, hits clink
 * off) unless the plates are held. The right crystal shatters with a bright note; a wrong one bursts harmlessly with a
 * low one, the dragon visibly drinks 5% health from it and its cast surges, the crystal re-forms and the obelisk plays
 * the new order. The whole order broken: the dragon crashes down stunned.
 */
final class CrystalPuzzle {
	/** Sequence colors, first to last: white-hot to deep violet ("brighter goes first"). */
	private static final int[] COLORS = {0xFFFFFF, 0xF1DDFF, 0xE0B8FF, 0xCC90FF, 0xB66BFF, 0xA04CF5, 0x8A34E0, 0x7222C8, 0x5C16AE, 0x480C94};

	private static final DustParticleOptions SEAL = new DustParticleOptions(0x6A2BC4, 1.6f);

	private final FinalDragon dragon;
	private @Nullable CrystalOrder order;
	private boolean sealed = true;
	/** Crystal entity per slot (slots index {@link RaidArena#crystals()}). */
	private final Map<Integer, UUID> crystals = new LinkedHashMap<>();
	/** Slots whose crystal is re-forming, with ticks left. */
	private final Map<Integer, Integer> reforming = new HashMap<>();
	private int total;
	private int showClock;
	private int lit = -1;

	CrystalPuzzle(FinalDragon dragon) {
		this.dragon = dragon;
	}

	private static FinalRaidConfig config() {
		return FinalRaids.CONFIG;
	}

	/** The shield is up while any crystal of the order is left (standing or re-forming). */
	boolean shielded() {
		return order != null && !order.isDone();
	}

	@Nullable CrystalOrder order() {
		return order;
	}

	/** Crystals standing right now, by slot. */
	List<RaidCrystal> standing(ServerLevel level) {
		List<RaidCrystal> list = new ArrayList<>();
		for (UUID id : crystals.values()) {
			if (level.getEntity(id) instanceof RaidCrystal crystal && crystal.isAlive()) {
				list.add(crystal);
			}
		}
		return list;
	}

	/** A fresh puzzle for {@code players}: clears the pillars and places the crystals of a new order. */
	void setup(ServerLevel level, int players) {
		clear(level);
		sealed = true;
		RaidArena arena = dragon.arena();
		removeVanillaCrystals(level, arena);
		List<Integer> open = new ArrayList<>();
		List<Integer> caged = new ArrayList<>();
		for (int i = 0; i < arena.crystals().size(); i++) {
			(arena.caged().get(i) ? caged : open).add(i);
		}
		int count = RaidMath.crystalCount(players, config().crystalsSolo, config().crystalsMax);
		order = CrystalOrder.generate(open, caged, count, random());
		total = order.remaining().size();
		for (int slot : order.remaining()) {
			spawnCrystal(level, slot);
		}
		restartShow(40);
	}

	/** Vanilla crystals on the pillars would explode for 6 when shot; the raid's own crystals replace them. */
	private static void removeVanillaCrystals(ServerLevel level, RaidArena arena) {
		List<Vec3> spots = new ArrayList<>(arena.crystals());
		for (int i = 0; i < arena.crystals().size(); i++) {
			spots.add(arena.crystalSpot(level, i));
		}
		for (Vec3 spot : spots) {
			for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, new AABB(spot, spot).inflate(3))) {
				crystal.discard();
			}
			for (RaidCrystal stray : level.getEntitiesOfClass(RaidCrystal.class, new AABB(spot, spot).inflate(3))) {
				stray.discard();
			}
		}
	}

	private void spawnCrystal(ServerLevel level, int slot) {
		Vec3 spot = dragon.arena().crystalSpot(level, slot);
		RaidCrystal crystal = FinalRaids.CRYSTAL.create(level, EntitySpawnReason.EVENT);
		if (crystal == null) {
			return;
		}
		crystal.setOwner(dragon.getUUID(), slot);
		crystal.setSealed(sealed);
		crystal.snapTo(spot.x, spot.y, spot.z, 0, 0);
		level.addFreshEntity(crystal);
		crystals.put(slot, crystal.getUUID());
	}

	/** Removes every crystal and forgets the order (reset, phase change, death). */
	void clear(ServerLevel level) {
		for (UUID id : crystals.values()) {
			if (level.getEntity(id) instanceof RaidCrystal crystal) {
				crystal.discard();
			}
		}
		crystals.clear();
		reforming.clear();
		order = null;
		total = 0;
		lit = -1;
	}

	/** Seals or opens every crystal (the plates hold them open). */
	void seal(ServerLevel level, boolean seal) {
		if (seal != sealed) {
			sealed = seal;
			for (RaidCrystal crystal : standing(level)) {
				crystal.setSealed(seal);
				Vec3 at = crystal.position().add(0, 1, 0);
				level.playSound(null, at.x, at.y, at.z, seal ? FinalRaidSounds.DEFLECT : FinalRaidSounds.CRYSTAL_REFORM, SoundSource.HOSTILE, 2f,
						seal ? 0.6f : 1.5f);
			}
		}
		if (sealed && level.getGameTime() % 6 == 0) {
			for (RaidCrystal crystal : standing(level)) {
				Telegraphs.circle(level, SEAL, crystal.position().add(0, 0.2, 0), 1.6);
			}
		}
	}

	/** Debug: completes the order (the shield drops). */
	void skip(ServerLevel level) {
		clear(level);
		order = new CrystalOrder(List.of());
	}

	private Random random() {
		return new Random(dragon.getRandom().nextLong());
	}

	private void restartShow(int delay) {
		showClock = -delay;
	}

	/** A player broke the crystal in {@code slot}. */
	void onBroken(ServerLevel level, RaidCrystal crystal, ServerPlayer player) {
		int slot = crystal.slot();
		Vec3 at = crystal.position().add(0, 1, 0);
		if (crystal.sealed()) {
			// Sealed: the hit clinks off the shell.
			level.sendParticles(SEAL, true, true, at.x, at.y, at.z, 16, 0.6, 0.6, 0.6, 0);
			level.playSound(null, at.x, at.y, at.z, FinalRaidSounds.DEFLECT, SoundSource.HOSTILE, 2f, 1.6f);
			return;
		}
		crystal.discard();
		crystals.remove(slot);
		unlight(level);
		if (order == null) {
			return;
		}
		switch (order.hit(slot, random())) {
			case RIGHT -> {
				shatter(level, at);
				restartShow(30);
			}
			case DONE -> {
				shatter(level, at);
				dragon.onOrderComplete(level);
			}
			case WRONG -> {
				// Bursts harmlessly, the dragon drinks from it, the crystal re-forms, the order is shuffled.
				level.sendParticles(ParticleTypes.EXPLOSION, true, true, at.x, at.y, at.z, 3, 0.6, 0.6, 0.6, 0);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, true, at.x, at.y, at.z, 60, 0.8, 0.8, 0.8, 0.2);
				level.playSound(null, at.x, at.y, at.z, FinalRaidSounds.CRYSTAL_WRONG, SoundSource.HOSTILE, 3f, 0.6f);
				dragon.drainFrom(level, at, config().wrongCrystalHeal);
				dragon.castPenalty();
				reforming.put(slot, config().crystalReformTicks);
				restartShow(config().crystalReformTicks / 2);
			}
			case IGNORED -> {
			}
		}
	}

	private static void shatter(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.END_ROD, true, true, at.x, at.y, at.z, 40, 0.5, 0.5, 0.5, 0.25);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFF2ECFF), true, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.playSound(null, at.x, at.y, at.z, FinalRaidSounds.CRYSTAL_SHATTER, SoundSource.HOSTILE, 3f, 1.4f);
	}

	void tick(ServerLevel level) {
		if (order == null || order.isDone()) {
			return;
		}
		// Re-forming crystals: particles stream into the spot, then it's back.
		for (var it = reforming.entrySet().iterator(); it.hasNext();) {
			var entry = it.next();
			int left = entry.getValue() - 1;
			Vec3 spot = dragon.arena().crystalSpot(level, entry.getKey()).add(0, 1, 0);
			if (left % 4 == 0) {
				level.sendParticles(ParticleTypes.PORTAL, true, true, spot.x, spot.y, spot.z, 12, 0.2, 0.2, 0.2, 1.2);
			}
			if (left <= 0) {
				it.remove();
				spawnCrystal(level, entry.getKey());
				level.playSound(null, spot.x, spot.y, spot.z, FinalRaidSounds.CRYSTAL_REFORM, SoundSource.HOSTILE, 2f, 1f);
			} else {
				entry.setValue(left);
			}
		}
		show(level);
		if (level.getGameTime() % 10 == 0) {
			obeliskRings(level);
		}
	}

	/** The repeating show: one crystal per step, then a pause. */
	private void show(ServerLevel level) {
		List<Integer> remaining = order.remaining();
		int step = Math.max(4, config().showStepTicks);
		int length = remaining.size() * step;
		showClock++;
		if (showClock < 0) {
			return;
		}
		if (showClock >= length + config().showPauseTicks) {
			showClock = 0;
		}
		if (showClock >= length) {
			if (showClock == length) {
				unlight(level);
			}
			return;
		}
		int index = showClock / step;
		int slot = remaining.get(index);
		int position = order.destroyed() + index;
		int color = COLORS[Math.min(position, COLORS.length - 1)];
		Vec3 obelisk = dragon.arena().obelisk();
		Vec3 spot = dragon.arena().crystalSpot(level, slot).add(0, 1, 0);
		if (showClock % step == 0) {
			unlight(level);
			RaidCrystal crystal = crystal(level, slot);
			if (crystal != null) {
				crystal.light(color, BlockPos.containing(obelisk));
				lit = slot;
			}
			// One chime per crystal, rising through the order; at the crystal and at the obelisk.
			float pitch = 0.6f + 0.13f * position;
			level.playSound(null, spot.x, spot.y, spot.z, FinalRaidSounds.CRYSTAL_CHIME, SoundSource.HOSTILE, 4f, pitch);
			level.playSound(null, obelisk.x, obelisk.y, obelisk.z, FinalRaidSounds.CRYSTAL_CHIME, SoundSource.HOSTILE, 3f, pitch);
			level.sendParticles(new DustParticleOptions(color, 3f), true, true, spot.x, spot.y, spot.z, 30, 0.8, 0.8, 0.8, 0);
		}
		if (showClock % 3 == 0) {
			// A colored stream along the beam, so the color reads even without the beam in view.
			DustParticleOptions dust = new DustParticleOptions(color, 1.6f);
			Vec3 delta = obelisk.subtract(spot);
			int points = (int) Math.min(80, delta.length());
			for (int i = 0; i <= points; i++) {
				Vec3 p = spot.add(delta.scale(i / (double) points));
				level.sendParticles(dust, true, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			}
			level.sendParticles(dust, true, true, obelisk.x, obelisk.y + 0.5, obelisk.z, 8, 0.4, 0.4, 0.4, 0);
		}
	}

	/** Progress on the obelisk: a ring per crystal of the order, lit white once it's done. */
	private void obeliskRings(ServerLevel level) {
		Vec3 top = dragon.arena().obelisk();
		for (int i = 0; i < total; i++) {
			boolean done = i < order.destroyed();
			double y = top.y + 0.6 + i * 0.5;
			int points = 10;
			for (int p = 0; p < points; p++) {
				double a = p * Math.PI * 2 / points + level.getGameTime() * 0.05;
				double x = top.x + Math.cos(a) * 0.9;
				double z = top.z + Math.sin(a) * 0.9;
				if (done) {
					level.sendParticles(ParticleTypes.END_ROD, true, true, x, y, z, 1, 0, 0, 0, 0);
				} else {
					level.sendParticles(new DustParticleOptions(0x3A1466, 1.2f), true, true, x, y, z, 1, 0, 0, 0, 0);
				}
			}
		}
	}

	private @Nullable RaidCrystal crystal(ServerLevel level, int slot) {
		UUID id = crystals.get(slot);
		return id != null && level.getEntity(id) instanceof RaidCrystal crystal ? crystal : null;
	}

	private void unlight(ServerLevel level) {
		if (lit >= 0) {
			RaidCrystal crystal = crystal(level, lit);
			if (crystal != null) {
				crystal.light(0, null);
			}
			lit = -1;
		}
	}
}
