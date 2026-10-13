package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.storage.loot.LootTable;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.floor.FloorSlot;
import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.content.WorldBlocks;
import io.github.tmldude.blocksouls.world.gen.RuinStructure;
import io.github.tmldude.blocksouls.world.gen.StructurePlacer;

/**
 * Builds a planned floor into the floor dimension: the island (seeded hills flattened under the slots, a tapered
 * underside, a glass wall at the edge), trees, and each slot's contents. Returns the filled slots with world positions;
 * every chest it places comes back as a {@code CRATE} slot tagged with its loot table.
 */
final class FloorBuilder {
	static final ResourceKey<LootTable> CRATE_LOOT = CommonIds.lootTable(BlockSoulsWorld.MOD_ID, "chests/floor_crate");
	static final ResourceKey<LootTable> SECRET_LOOT = CommonIds.lootTable(BlockSoulsWorld.MOD_ID, "chests/floor_secret");
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private final ServerLevel level;
	private final FloorTemplate template;
	private final FloorPlanner.FloorPlan plan;
	private final FloorsConfig config;
	private final BlockPos centre;
	private final RandomSource random;
	private final BlockState top;
	private final java.util.Map<String, BlockState> tops = new java.util.HashMap<>();
	private final BlockState fill;
	private final BlockState base;
	private final double phaseA;
	private final double phaseB;
	private final double phaseC;
	private final List<FloorSlot> filled = new ArrayList<>();
	/** Box templates (floor 4, The Vault) carve a room-and-corridor box instead of an island; null for islands. */
	private final VaultBuilder vault;

	FloorBuilder(ServerLevel level, FloorTemplate template, FloorPlanner.FloorPlan plan, FloorsConfig config, BlockPos centre, long seed) {
		this.level = level;
		this.template = template;
		this.plan = plan;
		this.config = config;
		this.centre = centre;
		this.random = RandomSource.create(seed);
		this.top = block(template.terrain.top, Blocks.GRASS_BLOCK);
		this.fill = block(template.terrain.fill, Blocks.DIRT);
		this.base = block(template.terrain.base, Blocks.STONE);
		this.phaseA = random.nextDouble() * 100;
		this.phaseB = random.nextDouble() * 100;
		this.phaseC = random.nextDouble() * 100;
		this.vault = template.isBox()
				? new VaultBuilder(level, template, BoxPlanner.layout(template, plan, seed), centre, plan.groundY(), seed, top)
				: null;
	}

	private static BlockState block(String id, Block fallback) {
		Identifier parsed = Identifier.tryParse(id);
		return (parsed == null ? fallback : BuiltInRegistries.BLOCK.getOptional(parsed).orElse(fallback)).defaultBlockState();
	}

	/** Builds everything and returns the filled slots (camp first). */
	List<FloorSlot> build() {
		if (vault != null) {
			vault.terrain();
		} else {
			terrain();
			SkyShards.build(level, plan, template, centre, random, this::height, top, fill, base);
		}
		for (FloorPlanner.PlannedSlot slot : plan.slots()) {
			BlockPos pos = slot.rise() > 0 ? centre.offset(slot.x(), plan.groundY() + slot.rise() + 1 - centre.getY(), slot.z())
					: surface(slot.x(), slot.z());
			switch (slot.kind()) {
				case CAMP -> camp(pos, slot);
				case EXIT_PORTAL -> portal(pos);
				case NODE -> node(pos, slot.tag());
				case CRATE -> chest(pos, CRATE_LOOT);
				case STRUCTURE -> {
					if (vault != null && BoxPlanner.VAULT_STRUCTURE.equals(slot.tag())) {
						vault.vaultRoom(this, pos);
					} else {
						structure(pos, slot.tag());
					}
				}
				case WRECK -> wreck(pos, slot);
				case CAVE -> cave(pos);
				case SECRET -> {
					if (vault != null && BoxPlanner.HOLLOW_WALL.equals(slot.tag())) {
						vault.hollowSecret(this, pos);
					} else {
						secret(pos, slot.tag());
					}
				}
				case MINI_BOSS -> arena(pos, slot.radius());
				case EVENT -> eventSpot(pos);
				case SIEGE_RING -> {
				}
			}
			if (slot.kind() != FloorSlotKind.CRATE) {
				filled.add(new FloorSlot(slot.kind(), slot.tag(), pos, slot.radius()));
			}
		}
		if (vault == null) {
			trees();
		}
		// v2-floor5-blight: blight decor (dead trees, tendrils, dry plants); null on other templates.
		if (template.decor != null) {
			decor(template.decor);
		}
		SkyShards.paintBiome(level, template, plan, centre);
		// Camp first, then everything else in plan order (crates the contents added come after their slot).
		filled.sort((a, b) -> Integer.compare(a.kind() == FloorSlotKind.CAMP ? 0 : 1, b.kind() == FloorSlotKind.CAMP ? 0 : 1));
		return filled;
	}

	// ---------------------------------------------------------------- terrain

	/** Ground height (the top block's y) at a column relative to the centre. */
	int height(int x, int z) {
		if (vault != null) {
			return plan.groundY(); // the vault's floor is flat
		}
		double noise = (Math.sin(x * 0.11 + phaseA) + Math.sin(z * 0.13 + phaseB) + Math.sin((x + z) * 0.07 + phaseC)) / 3.0;
		double flat = 0;
		for (FloorPlanner.PlannedSlot slot : plan.slots()) {
			if (!flattens(slot.kind())) {
				continue;
			}
			double reach = slot.radius() + 3.0;
			double d = Math.sqrt((double) (x - slot.x()) * (x - slot.x()) + (double) (z - slot.z()) * (z - slot.z()));
			flat = Math.max(flat, d <= reach ? 1.0 : Math.max(0, 1 - (d - reach) / 4.0));
		}
		return plan.groundY() + (int) Math.round(noise * template.hills * (1 - flat));
	}

	private static boolean flattens(FloorSlotKind kind) {
		return kind != FloorSlotKind.NODE && kind != FloorSlotKind.CRATE && kind != FloorSlotKind.SIEGE_RING;
	}

	private BlockPos surface(int x, int z) {
		return centre.offset(x, height(x, z) + 1 - centre.getY(), z);
	}

	private void terrain() {
		int radius = plan.radius();
		int depth = Math.max(4, template.depth);
		BlockState glass = Blocks.GLASS.defaultBlockState();
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				double d = Math.sqrt((double) x * x + (double) z * z);
				if (d > radius) {
					continue;
				}
				int h = height(x, z);
				double edge = d / radius;
				int bottom = h - Math.max(2, (int) Math.round(depth * (1 - edge * edge)) + (int) ((x * 31 + z * 17 & 3) / 2));
				BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
				for (int y = bottom; y <= h; y++) {
					BlockState state = y == h ? topAt(x, z) : y >= h - 3 ? fill : base;
					level.setBlock(pos.set(centre.getX() + x, y, centre.getZ() + z), state, FLAGS);
				}
				if (template.edgeWall && d > radius - 1) {
					for (int y = h + 1; y <= h + config.wallHeight; y++) {
						level.setBlock(pos.set(centre.getX() + x, y, centre.getZ() + z), glass, FLAGS);
					}
				}
			}
		}
	}

	private BlockState topAt(int x, int z) {
		String id = template.terrain.topAt(x, z);
		return id.equals(template.terrain.top) ? top : tops.computeIfAbsent(id, key -> block(key, top.getBlock()));
	}

	private void trees() {
		if (template.decor != null && "dead".equals(template.decor.trees)) {
			deadTrees();
			return;
		}
		var feature = level.registryAccess().lookupOrThrow(Registries.FEATURE).get(TreeFeatures.OAK);
		if (feature.isEmpty()) {
			return;
		}
		double reach = plan.radius() * 0.85;
		int placed = 0;
		for (int attempt = 0; placed < template.trees && attempt < template.trees * 20; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Math.sqrt(random.nextDouble()) * reach;
			int x = (int) Math.round(Math.cos(angle) * distance);
			int z = (int) Math.round(Math.sin(angle) * distance);
			if (nearSlot(x, z, 3)) {
				continue;
			}
			BlockPos pos = surface(x, z);
			if (level.getBlockState(pos).isAir() && feature.get().value().place(level, level.getChunkSource().getGenerator(), random, pos)) {
				placed++;
			}
		}
	}

	/** Bare dead trunks (3–5 logs, a stub branch) instead of oaks. */
	private void deadTrees() {
		BlockState log = block(template.decor.deadLog, Blocks.DARK_OAK_LOG);
		double reach = plan.radius() * 0.85;
		int placed = 0;
		for (int attempt = 0; placed < template.trees && attempt < template.trees * 20; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Math.sqrt(random.nextDouble()) * reach;
			int x = (int) Math.round(Math.cos(angle) * distance);
			int z = (int) Math.round(Math.sin(angle) * distance);
			if (nearSlot(x, z, 3)) {
				continue;
			}
			BlockPos pos = surface(x, z);
			if (!level.getBlockState(pos).isAir()) {
				continue;
			}
			int tall = 3 + random.nextInt(3);
			for (int y = 0; y < tall; y++) {
				level.setBlock(pos.above(y), log, FLAGS);
			}
			Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			BlockState branch = log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, side.getAxis()) : log;
			level.setBlock(pos.above(tall - 2).relative(side), branch, FLAGS);
			placed++;
		}
	}

	/** Ground patches with cover on them, then scattered plants (counts scale with the island's area). */
	private void decor(FloorTemplate.Decor decor) {
		double area = (double) plan.radius() * plan.radius() / ((double) template.radius * template.radius);
		double reach = plan.radius() * 0.9;
		if (!decor.patchBlock.isEmpty()) {
			BlockState patch = block(decor.patchBlock, Blocks.COARSE_DIRT);
			BlockState cover = decor.patchCover.isEmpty() ? null : block(decor.patchCover, Blocks.AIR);
			int patches = (int) Math.round(decor.patches * area);
			for (int i = 0; i < patches; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double distance = Math.sqrt(random.nextDouble()) * reach;
				int cx = (int) Math.round(Math.cos(angle) * distance);
				int cz = (int) Math.round(Math.sin(angle) * distance);
				int r = Math.max(1, decor.patchRadius);
				for (int x = cx - r; x <= cx + r; x++) {
					for (int z = cz - r; z <= cz + r; z++) {
						int dx = x - cx;
						int dz = z - cz;
						if (dx * dx + dz * dz > r * r || nearSlot(x, z, 1) || (long) x * x + (long) z * z >= (long) (plan.radius() - 1) * (plan.radius() - 1)) {
							continue;
						}
						BlockPos ground = surface(x, z).below();
						if (!level.getBlockState(ground).is(topAt(x, z).getBlock())) {
							continue;
						}
						level.setBlock(ground, patch, FLAGS);
						if (cover != null && !cover.isAir() && random.nextDouble() < decor.coverChance && level.getBlockState(ground.above()).isAir()
								&& cover.canSurvive(level, ground.above())) {
							level.setBlock(ground.above(), cover, FLAGS);
						}
					}
				}
			}
		}
		for (java.util.Map.Entry<String, Integer> entry : decor.scatter.entrySet()) {
			BlockState plant = block(entry.getKey(), Blocks.AIR);
			if (plant.isAir()) {
				continue;
			}
			int count = (int) Math.round(entry.getValue() * area);
			int placed = 0;
			for (int attempt = 0; placed < count && attempt < count * 8; attempt++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double distance = Math.sqrt(random.nextDouble()) * reach;
				int x = (int) Math.round(Math.cos(angle) * distance);
				int z = (int) Math.round(Math.sin(angle) * distance);
				if (nearSlot(x, z, 1)) {
					continue;
				}
				BlockPos pos = surface(x, z);
				if (level.getBlockState(pos).isAir() && plant.canSurvive(level, pos)) {
					level.setBlock(pos, plant, FLAGS);
					placed++;
				}
			}
		}
	}

	private boolean nearSlot(int x, int z, int clearance) {
		for (FloorPlanner.PlannedSlot slot : plan.slots()) {
			if (slot.kind() == FloorSlotKind.SIEGE_RING) {
				continue;
			}
			long dx = x - slot.x();
			long dz = z - slot.z();
			long reach = slot.radius() + clearance;
			if (dx * dx + dz * dz < reach * reach) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- slot contents

	private void camp(BlockPos pos, FloorPlanner.PlannedSlot slot) {
		int r = Math.max(3, slot.radius());
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				if (x * x + z * z <= r * r && random.nextInt(3) != 0) {
					level.setBlock(pos.offset(x, -1, z), Blocks.COARSE_DIRT.defaultBlockState(), FLAGS);
				}
			}
		}
		level.setBlock(pos.offset(0, -1, 3), Blocks.COBBLESTONE.defaultBlockState(), FLAGS);
		level.setBlock(pos.offset(0, 0, 3), Blocks.CAMPFIRE.defaultBlockState(), FLAGS);
	}

	/** A 3×5 frame (polished blackstone bricks) around a 1×3 column of unlit portal blocks. */
	private void portal(BlockPos pos) {
		BlockState frame = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		for (int y = -1; y <= 3; y++) {
			level.setBlock(pos.offset(-1, y, 0), frame, FLAGS);
			level.setBlock(pos.offset(1, y, 0), frame, FLAGS);
		}
		level.setBlock(pos.offset(0, -1, 0), frame, FLAGS);
		level.setBlock(pos.offset(0, 3, 0), frame, FLAGS);
		for (int y = 0; y <= 2; y++) {
			level.setBlock(pos.above(y), FloorBlocks.FLOOR_PORTAL.defaultBlockState(), FLAGS);
		}
	}

	private void node(BlockPos pos, String tag) {
		Identifier id = Identifier.tryParse(tag);
		Block block = id == null ? FloorBlocks.SCRAP_NODE : BuiltInRegistries.BLOCK.getOptional(id).orElse(FloorBlocks.SCRAP_NODE);
		level.setBlock(pos, block.defaultBlockState(), FLAGS);
	}

	void chest(BlockPos pos, ResourceKey<LootTable> loot) {
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), FLAGS);
		RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot);
		crate(pos, loot);
	}

	private void crate(BlockPos pos, ResourceKey<LootTable> loot) {
		filled.add(new FloorSlot(FloorSlotKind.CRATE, loot.identifier().toString(), pos, 0));
	}

	/** The floor's structure: {@code outpost} is the checkpoint ruin, {@code wreck_yard} the wreck ruin. */
	private void structure(BlockPos pos, String tag) {
		RuinStructure.Kind kind = "wreck_yard".equals(tag) ? RuinStructure.Kind.WRECK : RuinStructure.Kind.CHECKPOINT;
		StructurePlacer.Placed placed = StructurePlacer.place(level, random, pos, kind.layout(random.nextLong()), kind.loot);
		placed.chests().forEach(chest -> crate(chest, kind.loot));
	}

	/** A wreck (the vehicles module's kart if loaded) with its loot chest and scrap scattered around it. */
	private void wreck(BlockPos pos, FloorPlanner.PlannedSlot slot) {
		RuinStructure.Kind kind = RuinStructure.Kind.WRECK;
		StructurePlacer.Placed placed = StructurePlacer.place(level, random, pos, kind.layout(random.nextLong()), kind.loot);
		placed.chests().forEach(chest -> crate(chest, kind.loot));
		int reach = Math.max(3, slot.radius());
		for (int i = 0; i < 6; i++) {
			int x = slot.x() + random.nextInt(2 * reach + 1) - reach;
			int z = slot.z() + random.nextInt(2 * reach + 1) - reach;
			BlockPos at = surface(x, z);
			if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).isSolid()) {
				level.setBlock(at, WorldBlocks.SCRAP_PILE.defaultBlockState(), FLAGS);
			}
		}
	}

	/**
	 * The cave dungeon: a stair tunnel from the entrance down to a lantern-lit chamber about nine blocks under the ground
	 * with a chest and two gunmetal nodes. Walls the island is too thin for are filled with stone first.
	 */
	private void cave(BlockPos pos) {
		Direction along = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		List<BlockPos[]> boxes = new ArrayList<>();
		BlockPos step = pos;
		for (int i = 0; i < 8; i++) {
			step = pos.relative(along, i).below(i);
			boxes.add(new BlockPos[] {step.relative(along.getClockWise()), step.relative(along.getCounterClockWise()).above(2)});
		}
		BlockPos hall = step.relative(along, 4).below(1);
		boxes.add(new BlockPos[] {step.relative(along).below(1), step.relative(along, 2).above(2)});
		boxes.add(new BlockPos[] {hall.offset(-3, 0, -3), hall.offset(3, 3, 3)});
		carve(boxes);
		level.setBlock(hall.offset(0, 3, 0), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), FLAGS);
		chest(hall.offset(2, 0, 2), CRATE_LOOT);
		level.setBlock(hall.offset(-2, 0, 2), FloorBlocks.GUNMETAL_NODE.defaultBlockState(), FLAGS);
		level.setBlock(hall.offset(-2, 0, -2), FloorBlocks.GUNMETAL_NODE.defaultBlockState(), FLAGS);
	}

	/**
	 * Clears the boxes (corner pairs). First every box grown by one gets stone where it is air below the ground, so a
	 * tunnel through a thin part of the island stays walled; then the boxes are cleared.
	 */
	private void carve(List<BlockPos[]> boxes) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (BlockPos[] box : boxes) {
			BlockPos min = BlockPos.min(box[0], box[1]);
			BlockPos max = BlockPos.max(box[0], box[1]);
			for (int x = min.getX() - 1; x <= max.getX() + 1; x++) {
				for (int z = min.getZ() - 1; z <= max.getZ() + 1; z++) {
					int ground = height(x - centre.getX(), z - centre.getZ());
					for (int y = min.getY() - 1; y <= max.getY() + 1 && y < ground; y++) {
						if (level.getBlockState(pos.set(x, y, z)).isAir()) {
							level.setBlock(pos, base, FLAGS);
						}
					}
				}
			}
		}
		for (BlockPos[] box : boxes) {
			for (BlockPos at : BlockPos.betweenClosed(box[0], box[1])) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
			}
		}
	}

	/**
	 * The floor's secret. {@code cracked_wall}: a sealed mossy hut, one wall cracked (break it to get in);
	 * {@code hidden_drop}: a trapdoor in the grass over a shaft to a small vault. Both hold a legendary-leaning chest.
	 */
	private void secret(BlockPos pos, String tag) {
		if ("hidden_drop".equals(tag)) {
			BlockPos vault = pos.below(6);
			carve(List.of(new BlockPos[] {pos.below(5), pos.below(1)}, new BlockPos[] {vault.offset(-1, 0, -1), vault.offset(1, 1, 1)}));
			level.setBlock(pos.below(), Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP), FLAGS);
			level.setBlock(vault.offset(1, 0, 1), Blocks.LANTERN.defaultBlockState(), FLAGS);
			chest(vault.offset(-1, 0, -1), SECRET_LOOT);
			return;
		}
		BlockState wall = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = -1; y <= 3; y++) {
					boolean shell = Math.abs(x) == 2 || Math.abs(z) == 2 || y == -1 || y == 3;
					level.setBlock(pos.offset(x, y, z), shell ? wall : Blocks.AIR.defaultBlockState(), FLAGS);
				}
			}
		}
		for (int y = 0; y <= 1; y++) {
			level.setBlock(pos.offset(0, y, 2), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), FLAGS);
		}
		level.setBlock(pos.offset(-1, 0, -1), Blocks.LANTERN.defaultBlockState(), FLAGS);
		chest(pos.offset(1, 0, -1), SECRET_LOOT);
	}

	/** The mini-boss arena: the flattened slot ringed by four lantern posts. */
	private void arena(BlockPos pos, int radius) {
		int r = Math.max(4, radius);
		for (int[] corner : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}}) {
			BlockPos post = surface(pos.getX() - centre.getX() + corner[0], pos.getZ() - centre.getZ() + corner[1]);
			level.setBlock(post, Blocks.STONE_BRICK_WALL.defaultBlockState(), FLAGS);
			level.setBlock(post.above(), Blocks.STONE_BRICK_WALL.defaultBlockState(), FLAGS);
			level.setBlock(post.above(2), Blocks.LANTERN.defaultBlockState(), FLAGS);
		}
	}

	/** The event spot: a small gravel clearing (the Director puts the event object on it). */
	private void eventSpot(BlockPos pos) {
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				if (x * x + z * z <= 5) {
					level.setBlock(pos.offset(x, -1, z), Blocks.GRAVEL.defaultBlockState(), FLAGS);
				}
			}
		}
	}
}
