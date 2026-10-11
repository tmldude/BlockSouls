package io.github.tmldude.blocksouls.world.gen;

import java.util.Locale;
import java.util.Optional;
import java.util.function.LongFunction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.storage.loot.LootTable;

import io.github.tmldude.blocksouls.world.gen.layout.CheckpointLayout;
import io.github.tmldude.blocksouls.world.gen.layout.Layout;
import io.github.tmldude.blocksouls.world.gen.layout.WreckLayout;

/**
 * A wreck or checkpoint ruin as a registered structure (so {@code /locate} finds it). One {@link RuinPiece} at the
 * centre of the start chunk; the footprint (radius ≤ 7) stays inside that chunk. Biomes and spacing come from the
 * structure / structure set JSON.
 */
public class RuinStructure extends Structure {
	public enum Kind implements StringRepresentable {
		/** Site: corners 2 blocks out within 2 blocks of the centre's height. */
		WRECK(2, 2, StructurePlacer.WRECK_LOOT, WreckLayout::generate),
		/** Site: corners 5 blocks out within 3 blocks of the centre's height. */
		CHECKPOINT(5, 3, StructurePlacer.CHECKPOINT_LOOT, CheckpointLayout::generate);

		public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

		public final int halfSize;
		public final int maxStep;
		public final ResourceKey<LootTable> loot;
		private final LongFunction<Layout> layout;

		Kind(int halfSize, int maxStep, ResourceKey<LootTable> loot, LongFunction<Layout> layout) {
			this.halfSize = halfSize;
			this.maxStep = maxStep;
			this.loot = loot;
			this.layout = layout;
		}

		public Layout layout(long seed) {
			return layout.apply(seed);
		}

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final MapCodec<RuinStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			Kind.CODEC.fieldOf("kind").forGetter(s -> s.kind)).apply(i, RuinStructure::new));

	private final Kind kind;

	public RuinStructure(StructureSettings settings, Kind kind) {
		super(settings);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		int x = context.chunkPos().getMiddleBlockX();
		int z = context.chunkPos().getMiddleBlockZ();
		Integer y = flatDryGround(context.chunkGenerator(), context.heightAccessor(), context.randomState(), x, z, kind.halfSize, kind.maxStep);
		if (y == null) {
			return Optional.empty();
		}
		BlockPos origin = new BlockPos(x, y, z);
		long seed = context.random().nextLong();
		return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new RuinPiece(kind, origin, seed))));
	}

	/**
	 * Height of the first air block at {@code (x, z)} if that column and the four corners {@code halfSize} out are dry
	 * land within {@code maxStep} blocks of each other; null otherwise. Reads the generator's noise, so it works for
	 * chunks that don't exist yet (which is what lets {@code /locate} search unexplored land).
	 */
	static Integer flatDryGround(ChunkGenerator generator, LevelHeightAccessor heights, RandomState random, int x, int z, int halfSize, int maxStep) {
		int center = dryHeight(generator, heights, random, x, z);
		if (center == Integer.MIN_VALUE) {
			return null;
		}
		int[][] corners = {{-halfSize, -halfSize}, {halfSize, -halfSize}, {-halfSize, halfSize}, {halfSize, halfSize}};
		for (int[] c : corners) {
			int corner = dryHeight(generator, heights, random, x + c[0], z + c[1]);
			if (corner == Integer.MIN_VALUE || Math.abs(corner - center) > maxStep) {
				return null;
			}
		}
		return center;
	}

	/** Surface height, or MIN_VALUE over water (the ocean floor sits below the surface) or the void. */
	private static int dryHeight(ChunkGenerator generator, LevelHeightAccessor heights, RandomState random, int x, int z) {
		int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, random);
		int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random);
		return surface != floor || surface <= heights.getMinY() ? Integer.MIN_VALUE : surface;
	}

	@Override
	public StructureType<?> type() {
		return WorldGen.RUIN_TYPE;
	}
}
