package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.economy.CampObject;
import io.github.tmldude.blocksouls.common.economy.CampObjects;
import io.github.tmldude.blocksouls.common.event.DirectorEvents;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.content.InfernalContent;

/**
 * Infernal's Director events (floor 2, v2-floor2-infernal), placed through {@link DirectorEvents#PLACE_EVENT} when the
 * Director rolls one of the floor-tagged ids in its pool:
 * <ul>
 * <li>{@code soulfire_altar}: the game's blood altar (sacrifice for a boon) on a ring of soul fire.</li>
 * <li>{@code lava_chest}: the game's cursed chest on an islet in a lava moat; its plank bridge burns away after
 * {@code lavaChestBridgeSeconds}.</li>
 * <li>{@code stranded_trader}: a wandering trader on a pillar in a lava pool (bridge to him) with Infernal brews for
 * cinder; he leaves after {@code strandedTraderMinutes}.</li>
 * </ul>
 * Nothing here is saved, like the Director's own event state: a bridge still standing at a restart stays.
 */
public final class InfernalEvents {
	public static final Identifier SOULFIRE_ALTAR = BlockSoulsWorld.id("soulfire_altar");
	public static final Identifier LAVA_CHEST = BlockSoulsWorld.id("lava_chest");
	public static final Identifier STRANDED_TRADER = BlockSoulsWorld.id("stranded_trader");
	private static final int FLAGS = Block.UPDATE_CLIENTS;
	/** The moat's inner and outer square radius (the islet is the 3×3 inside it); fits the event slot's radius 5. */
	private static final int MOAT_IN = 2;
	private static final int MOAT_OUT = 4;

	/** A plank bridge waiting to burn: its deck and the server tick it goes. */
	record BurningBridge(ResourceKey<Level> dimension, List<BlockPos> deck, long burnsAt) {
	}

	static final List<BurningBridge> BRIDGES = new ArrayList<>();

	private InfernalEvents() {
	}

	static void init() {
		DirectorEvents.PLACE_EVENT.register((level, event, pos, floor) -> place(level, event, pos));
		ServerTickEvents.END_SERVER_TICK.register(InfernalEvents::tickBridges);
	}

	/** Builds one of Infernal's events at {@code pos} (the air over the event spot); false for any other id. */
	public static boolean place(ServerLevel level, Identifier event, BlockPos pos) {
		if (event.equals(SOULFIRE_ALTAR)) {
			return soulfireAltar(level, pos);
		}
		if (event.equals(LAVA_CHEST)) {
			return lavaChest(level, pos);
		}
		if (event.equals(STRANDED_TRADER)) {
			return strandedTrader(level, pos);
		}
		return false;
	}

	/** A soul-soil pad, soul fire at its corners and the blood altar in the middle (false without the game). */
	static boolean soulfireAltar(ServerLevel level, BlockPos pos) {
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				level.setBlock(pos.offset(x, -1, z), Blocks.SOUL_SOIL.defaultBlockState(), FLAGS);
				level.setBlock(pos.offset(x, 0, z), Blocks.AIR.defaultBlockState(), FLAGS);
			}
		}
		for (int[] corner : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
			level.setBlock(pos.offset(corner[0], 0, corner[1]), Blocks.SOUL_FIRE.defaultBlockState(), FLAGS);
		}
		level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.0F, 0.7F);
		return CampObjects.place(level, pos, CampObject.BLOOD_ALTAR);
	}

	/** The cursed chest on an islet, a lava moat round it, one plank bridge that burns after a while. */
	static boolean lavaChest(ServerLevel level, BlockPos pos) {
		moat(level, pos);
		List<BlockPos> deck = new ArrayList<>();
		for (int x = MOAT_IN; x <= MOAT_OUT; x++) {
			BlockPos at = pos.offset(x, -1, 0);
			level.setBlock(at, Blocks.CRIMSON_PLANKS.defaultBlockState(), FLAGS);
			deck.add(at);
		}
		if (!CampObjects.place(level, pos, CampObject.CURSED_CHEST)) {
			return false;
		}
		long ticks = Math.max(1, WorldFloors.config().infernal.lavaChestBridgeSeconds) * 20L;
		BRIDGES.add(new BurningBridge(level.dimension(), deck, level.getServer().getTickCount() + ticks));
		return true;
	}

	/** A wandering trader on a basalt pillar in a lava moat, fire-proof, selling Infernal brews for cinder. */
	static boolean strandedTrader(ServerLevel level, BlockPos pos) {
		moat(level, pos);
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				level.setBlock(pos.offset(x, 0, z), Blocks.BASALT.defaultBlockState(), FLAGS);
				level.setBlock(pos.offset(x, 1, z), Blocks.POLISHED_BASALT.defaultBlockState(), FLAGS);
			}
		}
		WanderingTrader trader = EntityTypes.WANDERING_TRADER.spawn(level, pos.above(2), EntitySpawnReason.EVENT);
		if (trader == null) {
			return false;
		}
		FloorsConfig.Infernal config = WorldFloors.config().infernal;
		trader.setDespawnDelay(Math.max(1, config.strandedTraderMinutes) * 60 * 20);
		trader.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		if (config.traderTonicCinder > 0) {
			trader.getOffers().add(new MerchantOffer(new ItemCost(InfernalContent.CINDER, config.traderTonicCinder),
					new ItemStack(InfernalContent.ASHCAP_TONIC), 3, 1, 0.05F));
		}
		if (config.traderDraughtCinder > 0) {
			trader.getOffers().add(new MerchantOffer(new ItemCost(InfernalContent.CINDER, config.traderDraughtCinder),
					new ItemStack(InfernalContent.SCORCH_DRAUGHT), 2, 1, 0.05F));
		}
		return true;
	}

	/** Lava one deep in a square ring round the spot (a blackstone bed under it, so it never drains into a cave). */
	private static void moat(ServerLevel level, BlockPos pos) {
		BlockState lava = Blocks.LAVA.defaultBlockState();
		for (int x = -MOAT_OUT; x <= MOAT_OUT; x++) {
			for (int z = -MOAT_OUT; z <= MOAT_OUT; z++) {
				int ring = Math.max(Math.abs(x), Math.abs(z));
				level.setBlock(pos.offset(x, -2, z), Blocks.BLACKSTONE.defaultBlockState(), FLAGS);
				level.setBlock(pos.offset(x, -1, z), ring >= MOAT_IN ? lava : Blocks.BLACKSTONE.defaultBlockState(), FLAGS);
				level.setBlock(pos.offset(x, 0, z), Blocks.AIR.defaultBlockState(), FLAGS);
			}
		}
		// The rim keeps the lava in: solid ground one past the moat.
		for (int x = -MOAT_OUT - 1; x <= MOAT_OUT + 1; x++) {
			for (int z = -MOAT_OUT - 1; z <= MOAT_OUT + 1; z++) {
				if (Math.max(Math.abs(x), Math.abs(z)) == MOAT_OUT + 1 && level.getBlockState(pos.offset(x, -1, z)).canBeReplaced()) {
					level.setBlock(pos.offset(x, -1, z), Blocks.BLACKSTONE.defaultBlockState(), FLAGS);
				}
			}
		}
	}

	private static void tickBridges(MinecraftServer server) {
		if (BRIDGES.isEmpty()) {
			return;
		}
		BRIDGES.removeIf(bridge -> {
			if (server.getTickCount() < bridge.burnsAt()) {
				return false;
			}
			ServerLevel level = server.getLevel(bridge.dimension());
			if (level != null) {
				burn(level, bridge);
			}
			return true;
		});
	}

	/** The deck catches and drops into the moat. */
	static void burn(ServerLevel level, BurningBridge bridge) {
		for (BlockPos at : bridge.deck()) {
			if (level.getBlockState(at).is(Blocks.CRIMSON_PLANKS)) {
				level.setBlock(at, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(ParticleTypes.LAVA, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.0);
			}
		}
		level.playSound(null, bridge.deck().getFirst(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.5F);
	}
}
