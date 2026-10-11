package io.github.tmldude.blocksouls.world.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.world.armor.SetBonuses;
import io.github.tmldude.blocksouls.world.content.WorldBlocks;
import io.github.tmldude.blocksouls.world.content.WorldItems;
import io.github.tmldude.blocksouls.world.enchant.WackyEnchantments;
import io.github.tmldude.blocksouls.world.gen.Blight;
import io.github.tmldude.blocksouls.world.gen.StructurePlacer;
import io.github.tmldude.blocksouls.world.gen.layout.CheckpointLayout;
import io.github.tmldude.blocksouls.world.gen.layout.Layout;
import io.github.tmldude.blocksouls.world.gen.layout.WreckLayout;

public class WorldGameTests {
	private static final String FLOOR = "blocksouls_world_test:floor16";

	@GameTest(structure = FLOOR, skyAccess = true)
	public void wreckPlacesALootChestAndKart(GameTestHelper helper) {
		placeAndCheck(helper, WreckLayout.generate(7), StructurePlacer.WRECK_LOOT, true);
	}

	@GameTest(structure = FLOOR, skyAccess = true)
	public void checkpointPlacesLootChests(GameTestHelper helper) {
		placeAndCheck(helper, CheckpointLayout.generate(7), StructurePlacer.CHECKPOINT_LOOT, false);
	}

	private static void placeAndCheck(GameTestHelper helper, Layout layout, ResourceKey<LootTable> loot, boolean kart) {
		ServerLevel level = helper.getLevel();
		StructurePlacer.Placed placed = StructurePlacer.place(level, level.getRandom(), helper.absolutePos(new BlockPos(8, 1, 8)), layout, loot);
		helper.assertTrue(!placed.chests().isEmpty(), "no chest placed");
		for (BlockPos pos : placed.chests()) {
			ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos);
			helper.assertTrue(chest != null, "chest block entity missing at " + pos);
			chest.unpackLootTable(null);
			helper.assertTrue(!chest.isEmpty(), "chest at " + pos + " rolled no loot from " + loot.identifier());
		}
		helper.assertTrue(!placed.scrapPiles().isEmpty(), "no scrap piles");
		for (BlockPos pos : placed.scrapPiles()) {
			helper.assertTrue(level.getBlockState(pos).is(WorldBlocks.SCRAP_PILE), "scrap pile missing at " + pos);
		}
		if (kart) {
			boolean typeExists = BuiltInRegistries.ENTITY_TYPE.containsKey(CommonIds.KART_WRECK);
			helper.assertTrue(typeExists == (placed.kart() != null), "kart wreck placed iff its type exists (type exists: " + typeExists + ")");
		}
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
