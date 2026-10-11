package io.github.tmldude.blocksouls.vehicles.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.vehicles.BlockSoulsVehicles;
import io.github.tmldude.blocksouls.vehicles.VehicleRegistry;
import io.github.tmldude.blocksouls.vehicles.kart.KartEntity;
import io.github.tmldude.blocksouls.vehicles.kart.KartWreckEntity;

/** The test mod adds iron nuggets to {@code blocksouls_vehicles:repair_scrap}, since world's scrap isn't loaded here. */
public class VehicleGameTests {
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static void use(GameTestHelper helper, KartWreckEntity wreck, ServerPlayer player, Item item, int times) {
		for (int i = 0; i < times; i++) {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
			wreck.interact(player, InteractionHand.MAIN_HAND, wreck.position());
		}
	}

	private static AABB area(GameTestHelper helper) {
		return new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(10);
	}

	private static <T extends Entity> List<T> find(GameTestHelper helper, EntityType<T> type) {
		return helper.getLevel().getEntities(type, area(helper), entity -> entity.isAlive());
	}

	@GameTest(maxTicks = 40)
	public void repairingWithAllPartsMakesAKart(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		KartWreckEntity wreck = helper.spawn(VehicleRegistry.KART_WRECK, new BlockPos(4, 1, 4));
		var cfg = BlockSoulsVehicles.CONFIG.wreck;
		use(helper, wreck, player, VehicleRegistry.WHEEL, cfg.wheels);
		use(helper, wreck, player, VehicleRegistry.ENGINE, cfg.engines);
		use(helper, wreck, player, Items.IRON_NUGGET, cfg.scrap);

		helper.runAfterDelay(2, () -> {
			helper.assertTrue(wreck.isRemoved(), "the wreck is gone once repaired");
			helper.assertTrue(find(helper, VehicleRegistry.KART).size() == 1, "a kart replaced the wreck");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void partialPartsDontRepair(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		KartWreckEntity wreck = helper.spawn(VehicleRegistry.KART_WRECK, new BlockPos(4, 1, 4));
		var cfg = BlockSoulsVehicles.CONFIG.wreck;
		use(helper, wreck, player, VehicleRegistry.WHEEL, cfg.wheels - 1);
		use(helper, wreck, player, VehicleRegistry.ENGINE, cfg.engines + 2);
		use(helper, wreck, player, Items.IRON_NUGGET, cfg.scrap + 3);
		use(helper, wreck, player, Items.DIRT, 1);

		helper.runAfterDelay(2, () -> {
			helper.assertFalse(wreck.isRemoved(), "a wreck missing a wheel stays a wreck");
			helper.assertTrue(find(helper, VehicleRegistry.KART).isEmpty(), "no kart without every part");
			helper.assertTrue(wreck.getProgress().engines() == cfg.engines, "extra engines are refused");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void fourPlayersCanRide(GameTestHelper helper) {
		floor(helper);
		KartEntity kart = helper.spawn(VehicleRegistry.KART, new BlockPos(4, 1, 4));
		for (int i = 0; i < KartEntity.MAX_PASSENGERS; i++) {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			helper.assertTrue(player.startRiding(kart), "player " + (i + 1) + " can board");
		}
		ServerPlayer fifth = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(fifth.startRiding(kart), "a fifth player can't board");
		helper.assertTrue(kart.getPassengers().size() == KartEntity.MAX_PASSENGERS, "four riders");
		helper.assertTrue(kart.getControllingPassenger() == kart.getFirstPassenger(), "the first rider drives");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void kartAtZeroHealthBecomesAWreckAndEjectsRiders(GameTestHelper helper) {
		floor(helper);
		KartEntity kart = helper.spawn(VehicleRegistry.KART, new BlockPos(4, 1, 4));
		ServerPlayer driver = helper.makeMockServerPlayerInLevel();
		ServerPlayer passenger = helper.makeMockServerPlayerInLevel();
		driver.startRiding(kart);
		passenger.startRiding(kart);

		kart.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), BlockSoulsVehicles.CONFIG.kart.maxHealth / 2);
		helper.assertFalse(kart.isRemoved(), "half health still drives");
		kart.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), BlockSoulsVehicles.CONFIG.kart.maxHealth);

		helper.runAfterDelay(2, () -> {
			helper.assertTrue(kart.isRemoved(), "the kart broke down");
			helper.assertFalse(driver.isPassenger() || passenger.isPassenger(), "riders were thrown out");
			List<KartWreckEntity> wrecks = find(helper, VehicleRegistry.KART_WRECK);
			helper.assertTrue(wrecks.size() == 1, "it became a wreck");
			helper.assertFalse(wrecks.get(0).getProgress().complete(BlockSoulsVehicles.CONFIG.wreck), "the wreck needs repairs");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void wreckShrugsOffDamage(GameTestHelper helper) {
		floor(helper);
		KartWreckEntity wreck = helper.spawn(VehicleRegistry.KART_WRECK, new BlockPos(4, 1, 4));
		wreck.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1000);
		helper.getLevel().explode(null, wreck.getX(), wreck.getY(), wreck.getZ(), 4, Level.ExplosionInteraction.NONE);
		helper.runAfterDelay(2, () -> {
			helper.assertFalse(wreck.isRemoved(), "wrecks survive damage and explosions");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void rammingAZombieAtSpeedHurtsIt(GameTestHelper helper) {
		floor(helper);
		KartEntity kart = helper.spawn(VehicleRegistry.KART, new Vec3(4.5, 1, 1.5));
		kart.setYRot(0);
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(4.5, 1, 5.5));
		float startHealth = zombie.getHealth();
		Vec3 start = kart.position();
		double speed = 0.6;
		int[] ticks = {0};
		helper.onEachTick(() -> {
			if (!kart.isRemoved() && ticks[0] < 12) {
				ticks[0]++;
				kart.setPos(start.x, start.y, start.z + speed * ticks[0]);
			}
		});

		helper.runAfterDelay(20, () -> {
			float expected = (float) (speed * BlockSoulsVehicles.CONFIG.kart.ramDamagePerSpeed);
			helper.assertTrue(zombie.getHealth() <= startHealth - expected + 1 || zombie.isDeadOrDying(),
					"zombie took ~" + expected + " ram damage, health " + startHealth + " -> " + zombie.getHealth());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void slowKartDoesntRam(GameTestHelper helper) {
		floor(helper);
		KartEntity kart = helper.spawn(VehicleRegistry.KART, new Vec3(4.5, 1, 1.5));
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(4.5, 1, 4.5));
		float startHealth = zombie.getHealth();
		Vec3 start = kart.position();
		int[] ticks = {0};
		helper.onEachTick(() -> {
			if (ticks[0] < 30) {
				ticks[0]++;
				kart.setPos(start.x, start.y, start.z + 0.1 * ticks[0]);
			}
		});

		helper.runAfterDelay(35, () -> {
			helper.assertTrue(zombie.getHealth() >= startHealth - 5, "a crawling kart doesn't hurt (only sunburn), health " + zombie.getHealth());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20)
	public void scrapPatchesUpADamagedKart(GameTestHelper helper) {
		floor(helper);
		KartEntity kart = helper.spawn(VehicleRegistry.KART, new BlockPos(4, 1, 4));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var cfg = BlockSoulsVehicles.CONFIG.kart;
		kart.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 25);
		float damaged = kart.getHealth();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_NUGGET));
		kart.interact(player, InteractionHand.MAIN_HAND, kart.position());
		helper.assertTrue(kart.getHealth() == Math.min(cfg.maxHealth, damaged + cfg.scrapRepairHealth), "scrap restores health");
		helper.assertFalse(player.isPassenger(), "repairing doesn't board");
		helper.succeed();
	}
}
