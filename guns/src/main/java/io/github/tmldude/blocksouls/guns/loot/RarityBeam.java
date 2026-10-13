package io.github.tmldude.blocksouls.guns.loot;

import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.event.lifecycle.v1.EntityLoadData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.GunsConfig;
import io.github.tmldude.blocksouls.guns.item.GunRolls;
import io.github.tmldude.blocksouls.guns.logic.GunRarity;
import io.github.tmldude.blocksouls.guns.sound.GunSounds;

/**
 * Rare and legendary guns lying on the ground are legible from across the field: a particle beam in the rarity's color,
 * a glow outline, the name in rarity color above the item and, when one first lands, a sound sting. Server-side only.
 */
public final class RarityBeam {
	/** Entity tag on a gun item entity that carries the beam. */
	public static final String TAG = "blocksouls_guns.rarity_beam";
	/** Rarity colors: aqua for rare, light purple for legendary (the tooltip's colors). */
	private static final int RARE_COLOR = 0x55FFFF;
	private static final int LEGENDARY_COLOR = 0xFF55FF;
	/** Exotics are gold, matching their nameplate. */
	private static final int EXOTIC_COLOR = 0xFFAA00;

	private static final Set<ItemEntity> BEAMS = Collections.newSetFromMap(new WeakHashMap<>());

	private RarityBeam() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item) {
				mark(item, level, !((EntityLoadData) entity).isLoadedFromDisk());
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> BEAMS.remove(entity));
		ServerTickEvents.END_LEVEL_TICK.register(RarityBeam::tick);
	}

	/** The beam color for a stack, or -1 if it gets no beam (not a rolled rare+ gun). */
	public static int color(ItemStack stack) {
		if (!GunRolls.canRoll(stack) || !GunRolls.isRolled(stack)) {
			return -1;
		}
		return switch (GunRolls.rarity(stack)) {
			case RARE -> RARE_COLOR;
			case LEGENDARY -> LEGENDARY_COLOR;
			case EXOTIC -> EXOTIC_COLOR;
			case COMMON -> -1;
		};
	}

	/** Gives a rare+ gun item entity its beam, glow and nameplate; {@code fresh} (just dropped) also plays the sting. */
	public static void mark(ItemEntity item, ServerLevel level, boolean fresh) {
		ItemStack stack = item.getItem();
		if (color(stack) < 0) {
			return;
		}
		GunsConfig.Drops config = BlockSoulsGuns.CONFIG.drops;
		item.addTag(TAG);
		if (config.glow) {
			item.setGlowingTag(true);
		}
		if (config.nameplate) {
			item.setCustomName(stack.getStyledHoverName());
			item.setCustomNameVisible(true);
		}
		BEAMS.add(item);
		if (fresh && config.sting) {
			SoundEvent sting = GunRolls.rarity(stack) == GunRarity.RARE ? GunSounds.DROP_RARE : GunSounds.DROP_LEGENDARY;
			level.playSound(null, item.getX(), item.getY(), item.getZ(), sting, SoundSource.NEUTRAL, 1.0F, 1.0F);
		}
	}

	private static void tick(ServerLevel level) {
		GunsConfig.Drops config = BlockSoulsGuns.CONFIG.drops;
		if (BEAMS.isEmpty() || !config.beam || level.getGameTime() % Math.max(1, config.beamIntervalTicks) != 0) {
			return;
		}
		for (Iterator<ItemEntity> it = BEAMS.iterator(); it.hasNext(); ) {
			ItemEntity item = it.next();
			if (item.isRemoved()) {
				it.remove();
				continue;
			}
			if (item.level() != level) {
				continue;
			}
			int color = color(item.getItem());
			if (color < 0) {
				continue;
			}
			DustParticleOptions dust = new DustParticleOptions(color, 1.5F);
			int steps = (int) Math.ceil(config.beamHeight * 2);
			for (int i = 0; i < steps; i++) {
				// Forced: seen from far (up to 512 blocks), not just the usual 32.
				level.sendParticles(dust, true, true, item.getX(), item.getY() + 0.3 + i * 0.5, item.getZ(), 1, 0.03, 0.1, 0.03, 0);
			}
		}
	}

	/** Whether an entity carries the beam marker (game tests). */
	public static boolean hasBeam(Entity entity) {
		return entity.entityTags().contains(TAG);
	}
}
