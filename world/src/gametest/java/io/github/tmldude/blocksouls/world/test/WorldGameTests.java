package io.github.tmldude.blocksouls.world.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.phys.AABB;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.armor.SetBonuses;
import io.github.tmldude.blocksouls.world.content.WorldBlocks;
import io.github.tmldude.blocksouls.world.content.WorldItems;
import io.github.tmldude.blocksouls.world.enchant.WackyEnchantments;
import io.github.tmldude.blocksouls.world.gen.Blight;
import io.github.tmldude.blocksouls.world.gen.RuinPiece;
import io.github.tmldude.blocksouls.world.gen.RuinStructure;
import io.github.tmldude.blocksouls.world.gen.StructurePlacer;
import io.github.tmldude.blocksouls.world.gen.WorldGen;
import io.github.tmldude.blocksouls.world.gen.layout.CheckpointLayout;
import io.github.tmldude.blocksouls.world.gen.layout.ScrapScatter;
import io.github.tmldude.blocksouls.world.gen.layout.WreckLayout;

public class WorldGameTests {
	private static final String FLOOR = "blocksouls_world_test:floor16";

	/** World seed for the structure search; any seed works, this one is just fixed so failures reproduce. */
	private static final long SEARCH_SEED = 0L;
	/** Grid cells searched around 0,0 in each direction (wreck spacing 12 → ±96 chunks). */
	private static final int SEARCH_CELLS = 8;

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 400)
	public void wreckStructureGeneratesWithLootAndKart(GameTestHelper helper) {
		generateAndCheck(helper, WorldGen.WRECK, true);
	}

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 400)
	public void checkpointStructureGeneratesWithLoot(GameTestHelper helper) {
		generateAndCheck(helper, WorldGen.CHECKPOINT, false);
	}

	/**
	 * The test level is flat with structures off, so this builds a vanilla overworld generator and does what {@code
	 * /locate} ({@code ChunkGenerator.findNearestMapStructure}) does: ask the structure state for the structure's
	 * placement, walk its spread grid and run the structure's own generation on each candidate chunk until one is
	 * valid. That start's piece is then moved into the test area and built chunk by chunk like world generation does.
	 */
	private static void generateAndCheck(GameTestHelper helper, ResourceKey<Structure> key, boolean kart) {
		ServerLevel level = helper.getLevel();
		RegistryAccess access = level.registryAccess();
		Holder<NoiseGeneratorSettings> settings = access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
		MultiNoiseBiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(
				access.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
		NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomes, settings);
		RandomState randomState = RandomState.create(access.lookupOrThrow(Registries.NOISE), SEARCH_SEED, settings.value());
		Climate.Sampler climate = randomState.createClimateSampler(SamplerContext.builder().build());
		Holder<Structure> structure = access.lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);

		ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForNormal(randomState, SEARCH_SEED, ChunkPos.ZERO, biomes,
				access.lookupOrThrow(Registries.STRUCTURE_SET));
		List<StructurePlacement> placements = state.getPlacementsForStructure(structure);
		helper.assertTrue(placements.size() == 1 && placements.getFirst() instanceof RandomSpreadStructurePlacement,
				key.identifier() + " should have one random-spread placement, has " + placements);
		RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) placements.getFirst();

		StructureStart found = null;
		search:
		for (int ring = 0; ring <= SEARCH_CELLS; ring++) {
			for (int gx = -ring; gx <= ring; gx++) {
				for (int gz = -ring; gz <= ring; gz++) {
					if (Math.max(Math.abs(gx), Math.abs(gz)) != ring) {
						continue;
					}
					ChunkPos chunk = placement.getPotentialStructureChunk(SEARCH_SEED, gx * placement.spacing(), gz * placement.spacing());
					if (!placement.isStructureChunk(state, chunk.x(), chunk.z())) {
						continue;
					}
					StructureStart start = structure.value().generate(structure, Level.OVERWORLD, access, generator, biomes, climate, randomState,
							level.getServer().getStructureTemplateManager(), SEARCH_SEED, chunk, 0, level, structure.value().biomes()::contains);
					if (start.isValid()) {
						found = start;
						break search;
					}
				}
			}
		}
		helper.assertTrue(found != null, "no " + key.identifier() + " within " + SEARCH_CELLS + " grid cells of 0,0");
		helper.assertTrue(found.getPieces().size() == 1 && found.getPieces().getFirst() instanceof RuinPiece, "one ruin piece");

		RuinPiece piece = (RuinPiece) found.getPieces().getFirst();
		BlockPos target = helper.absolutePos(new BlockPos(8, 1, 8));
		piece.move(target.getX() - piece.origin().getX(), target.getY() - piece.origin().getY(), target.getZ() - piece.origin().getZ());
		BoundingBox box = piece.getBoundingBox();
		for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
				ChunkPos chunk = new ChunkPos(cx, cz);
				BoundingBox chunkBB = new BoundingBox(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
						chunk.getMaxBlockX(), level.getMaxY(), chunk.getMaxBlockZ());
				found.placeInChunk(level, level.structureManager(), generator, level.getRandom(), chunkBB, chunk);
			}
		}

		int chests = 0;
		int piles = 0;
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
				chest.unpackLootTable(null);
				helper.assertTrue(!chest.isEmpty(), "chest at " + pos + " rolled no loot from " + piece.kind().loot.identifier());
				chests++;
			} else if (level.getBlockState(pos).is(WorldBlocks.SCRAP_PILE)) {
				piles++;
			}
		}
		helper.assertTrue(chests > 0, "no loot chest");
		helper.assertTrue(piles > 0, "no scrap piles");
		if (kart) {
			boolean typeExists = BuiltInRegistries.ENTITY_TYPE.containsKey(CommonIds.KART_WRECK);
			int karts = level.getEntities((Entity) null, AABB.of(box).inflate(2),
					e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(CommonIds.KART_WRECK)).size();
			helper.assertTrue(karts == (typeExists ? 1 : 0), "kart wreck placed iff its type exists (type exists: " + typeExists + ", karts: " + karts + ")");
		}
		helper.succeed();
	}

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 40)
	public void wreckIsRingedWithExtraScrap(GameTestHelper helper) {
		countScrapAround(helper, RuinStructure.Kind.WRECK, BlockSoulsWorld.CONFIG.gen.wreckExtraScrapPiles);
	}

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 40)
	public void checkpointIsRingedWithExtraScrap(GameTestHelper helper) {
		countScrapAround(helper, RuinStructure.Kind.CHECKPOINT, BlockSoulsWorld.CONFIG.gen.checkpointExtraScrapPiles);
	}

	/**
	 * Places a ruin on the flat floor and counts the scrap pile blocks within {@link ScrapScatter#MAX_RADIUS} of it: the
	 * layout's own debris plus the configured extras, all of which land on flat open ground.
	 */
	private static void countScrapAround(GameTestHelper helper, RuinStructure.Kind kind, int extra) {
		ServerLevel level = helper.getLevel();
		long seed = 7L;
		int base = kind == RuinStructure.Kind.WRECK ? WreckLayout.generate(seed).scrapPiles().size() : CheckpointLayout.generate(seed).scrapPiles().size();
		helper.assertTrue(extra > 0, kind.getSerializedName() + "ExtraScrapPiles should default above 0, is " + extra);
		BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
		StructurePlacer.place(level, level.getRandom(), origin, kind.layout(seed), kind.loot);

		int r = ScrapScatter.MAX_RADIUS;
		int piles = 0;
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -3, -r), origin.offset(r, 3, r))) {
			if (level.getBlockState(pos).is(WorldBlocks.SCRAP_PILE)) {
				piles++;
			}
		}
		helper.assertTrue(piles >= base + extra, kind.getSerializedName() + ": " + piles + " scrap piles within " + r
				+ " blocks, expected at least " + base + " from the layout + " + extra + " extra");
		helper.succeed();
	}

	@GameTest(structure = FLOOR, skyAccess = true)
	public void combustionKillExplodesWithoutBreakingBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(helper, Items.DIAMOND_SWORD, WackyEnchantments.COMBUSTION, 1));
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(8, 1, 8));
		Zombie bystander = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(9, 1, 8));
		BlockPos glass = new BlockPos(8, 1, 9);
		helper.setBlock(glass, Blocks.GLASS);
		BlockPos stand = helper.absolutePos(new BlockPos(8, 1, 6));
		player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
		float playerHealth = player.getHealth();

		victim.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);

		helper.assertTrue(victim.isDeadOrDying(), "victim should die");
		helper.assertTrue(bystander.getHealth() < bystander.getMaxHealth(), "the blast should hurt the bystander");
		helper.assertBlockPresent(Blocks.GLASS, glass);
		helper.assertTrue(player.getHealth() == playerHealth, "the blast must not hurt the owner");
		helper.succeed();
	}

	@GameTest(structure = FLOOR, skyAccess = true)
	public void vampiricHealsOnKill(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemInHand(InteractionHand.MAIN_HAND, enchanted(helper, Items.DIAMOND_SWORD, WackyEnchantments.VAMPIRIC, 2));
		player.setHealth(10.0F);
		Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(8, 1, 8));

		victim.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);

		helper.assertTrue(player.getHealth() == 14.0F, "Vampiric II heals 2 hearts, health " + player.getHealth());
		helper.succeed();
	}

	@GameTest(structure = FLOOR, skyAccess = true, maxTicks = 40)
	public void moonwalkBootsLowerGravity(GameTestHelper helper) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(8, 1, 8));
		zombie.setItemSlot(EquipmentSlot.FEET, enchanted(helper, Items.IRON_BOOTS, WackyEnchantments.MOONWALK, 1));
		helper.succeedWhen(() -> {
			double gravity = zombie.getAttributeValue(Attributes.GRAVITY);
			helper.assertTrue(Math.abs(gravity - 0.08 * 0.7) < 1.0E-6, "Moonwalk I gravity should be 70% of 0.08, was " + gravity);
			helper.assertTrue(zombie.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) >= 8.0, "safe fall distance should be at least 8");
		});
	}

	@GameTest(structure = FLOOR, skyAccess = true)
	public void scrapPlateSetReducesExplosionDamage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie full = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 8));
		Zombie partial = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(12, 1, 8));
		equip(full, WorldItems.SCRAP_PLATE_HELMET, WorldItems.SCRAP_PLATE_CHESTPLATE, WorldItems.SCRAP_PLATE_LEGGINGS, WorldItems.SCRAP_PLATE_BOOTS);
		equip(partial, Items.IRON_HELMET, WorldItems.SCRAP_PLATE_CHESTPLATE, WorldItems.SCRAP_PLATE_LEGGINGS, WorldItems.SCRAP_PLATE_BOOTS);

		var blast = level.damageSources().explosion(null, null);
		helper.assertTrue(SetBonuses.modifyIncoming(full, blast, 10.0F) == 7.0F, "full set takes 70% of explosion damage");
		helper.assertTrue(SetBonuses.modifyIncoming(partial, blast, 10.0F) == 10.0F, "three pieces give no bonus");
		helper.assertTrue(SetBonuses.modifyIncoming(full, level.damageSources().generic(), 10.0F) == 10.0F, "only explosions are reduced");

		float before = full.getHealth();
		full.hurtServer(level, blast, 10.0F);
		float fullLoss = before - full.getHealth();
		before = partial.getHealth();
		partial.hurtServer(level, blast, 10.0F);
		float partialLoss = before - partial.getHealth();
		helper.assertTrue(fullLoss < partialLoss * 0.85F, "full set lost " + fullLoss + ", partial lost " + partialLoss);
		helper.succeed();
	}

	@GameTest(structure = FLOOR, skyAccess = true)
	public void gunmetalOreDropsRawGunmetal(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(8, 1, 8));
		for (var ore : List.of(WorldBlocks.GUNMETAL_ORE, WorldBlocks.DEEPSLATE_GUNMETAL_ORE)) {
			level.setBlock(pos, ore.defaultBlockState(), 3);
			List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(pos), level, pos, null, null,
					new ItemStack(Items.STONE_PICKAXE));
			helper.assertTrue(drops.stream().anyMatch(s -> s.is(WorldItems.RAW_GUNMETAL)), ore + " should drop raw gunmetal, got " + drops);
		}
		helper.assertTrue(WorldBlocks.GUNMETAL_ORE.defaultBlockState().requiresCorrectToolForDrops(), "ore needs a pickaxe");
		helper.succeed();
	}

	@GameTest(structure = FLOOR, skyAccess = true)
	public void blightConvertsNetherrack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 4; x <= 12; x++) {
			for (int z = 4; z <= 12; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.NETHERRACK);
			}
		}
		Blight.Result result = Blight.infect(level, helper.absolutePos(new BlockPos(8, 1, 8)), 3, level.getRandom(), 1.0);
		helper.assertTrue(result.converted() > 0, "nothing converted");
		helper.assertTrue(result.heart() != null && level.getBlockState(result.heart()).is(WorldBlocks.BLIGHT_HEART), "heart chance 1.0 places a heart");
		helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(8, 1, 8))).is(WorldBlocks.BLIGHTED_NETHERRACK)
				|| result.heart().equals(helper.absolutePos(new BlockPos(8, 1, 8))), "center should be blighted");
		helper.succeed();
	}

	private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> key, int level) {
		Holder<Enchantment> holder = WackyEnchantments.holder(helper.getLevel(), key)
				.orElseThrow(() -> new IllegalStateException("enchantment " + key.identifier() + " not loaded"));
		ItemStack stack = new ItemStack(item);
		stack.enchant(holder, level);
		return stack;
	}

	private static void equip(LivingEntity entity, Item head, Item chest, Item legs, Item feet) {
		entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(head));
		entity.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
		entity.setItemSlot(EquipmentSlot.LEGS, new ItemStack(legs));
		entity.setItemSlot(EquipmentSlot.FEET, new ItemStack(feet));
	}
}
