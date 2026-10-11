package io.github.tmldude.blocksouls.world.gen;

import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The one piece of a {@link RuinStructure}: the layout is regenerated from {@code seed} and {@link StructurePlacer}
 * builds the part inside the chunk being generated (blocks, loot chests, scrap piles, kart wreck).
 */
public class RuinPiece extends StructurePiece {
	/** Room below the origin for a sunk nose plus the propped-up foundation, and above it for roofs / scrap piles. */
	private static final int BELOW = 6;
	private static final int ABOVE = 4;

	private final RuinStructure.Kind kind;
	private final long seed;
	private BlockPos origin;

	public RuinPiece(RuinStructure.Kind kind, BlockPos origin, long seed) {
		super(WorldGen.RUIN_PIECE, 0, box(origin, kind.layout(seed).radius()));
		this.kind = kind;
		this.seed = seed;
		this.origin = origin.immutable();
	}

	public RuinPiece(CompoundTag tag) {
		super(WorldGen.RUIN_PIECE, tag);
		this.kind = RuinStructure.Kind.valueOf(tag.getStringOr("Kind", "wreck").toUpperCase(Locale.ROOT));
		this.seed = tag.getLongOr("Seed", 0L);
		this.origin = new BlockPos(tag.getIntOr("OX", 0), tag.getIntOr("OY", 0), tag.getIntOr("OZ", 0));
	}

	private static BoundingBox box(BlockPos origin, int radius) {
		return new BoundingBox(origin.getX() - radius, origin.getY() - BELOW, origin.getZ() - radius,
				origin.getX() + radius, origin.getY() + ABOVE, origin.getZ() + radius);
	}

	public RuinStructure.Kind kind() {
		return kind;
	}

	public BlockPos origin() {
		return origin;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString("Kind", kind.getSerializedName());
		tag.putLong("Seed", seed);
		tag.putInt("OX", origin.getX());
		tag.putInt("OY", origin.getY());
		tag.putInt("OZ", origin.getZ());
	}

	@Override
	public void move(int dx, int dy, int dz) {
		super.move(dx, dy, dz);
		origin = origin.offset(dx, dy, dz);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
		StructurePlacer.place(level, random, origin, kind.layout(seed), kind.loot, chunkBB);
	}
}
