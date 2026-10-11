package io.github.tmldude.blocksouls.vehicles.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Input;
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
import io.github.tmldude.blocksouls.vehicles.kart.KartFuel;
import io.github.tmldude.blocksouls.vehicles.kart.KartHorn;
import io.github.tmldude.blocksouls.vehicles.kart.KartWreckEntity;

/**
 * The test mod adds iron nuggets to {@code blocksouls_vehicles:repair_scrap}, since world's scrap isn't loaded here,
 * and zombies to {@code #blocksouls:wave_mobs}, since mobs isn't either.
 */
public class VehicleGameTests {
	private static final Input FORWARD = new Input(true, false, false, false, false, false, false);

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

	@GameTest(maxTicks = 40)
	public void throttleDrainsFuelButIdlingDoesnt(GameTestHelper helper) {
		floor(helper);
		KartEntity driving = helper.spawn(VehicleRegistry.KART, new BlockPos(2, 1, 2));
		KartEntity idling = helper.spawn(VehicleRegistry.KART, new BlockPos(6, 1, 6));
		ServerPlayer driver = helper.makeMockServerPlayerInLevel();
		ServerPlayer idler = helper.makeMockServerPlayerInLevel();
		driver.startRiding(driving);
		idler.startRiding(idling);
		// What vanilla's input packet would set for a player holding W.
		driver.setLastClientInput(FORWARD);
		int full = BlockSoulsVehicles.CONFIG.fuel.fuelCapacity;

		helper.runAfterDelay(10, () -> {
			int burned = full - driving.getFuel();
			helper.assertTrue(burned >= 8 && burned <= 12, "about 10 ticks of throttle burned, got " + burned);
			helper.assertTrue(idling.getFuel() == full, "idling burns nothing, fuel " + idling.getFuel());
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void emptyKartDoesntAccelerate(GameTestHelper helper) {
		floor(helper);
		KartEntity fueled = helper.spawn(VehicleRegistry.KART, new Vec3(1.5, 1, 1.5));
		KartEntity empty = helper.spawn(VehicleRegistry.KART, new Vec3(5.5, 1, 1.5));
		fueled.setYRot(0);
		empty.setYRot(0);
		empty.setFuel(0);

		helper.runAfterDelay(5, () -> {
			Vec3 fueledStart = fueled.position();
			Vec3 emptyStart = empty.position();
			for (int i = 0; i < 10; i++) {
				fueled.simulate(FORWARD);
				empty.simulate(FORWARD);
			}
			double fueledMoved = fueled.position().subtract(fueledStart).horizontalDistance();
			double emptyMoved = empty.position().subtract(emptyStart).horizontalDistance();
			helper.assertTrue(fueledMoved > 1, "a fueled kart pulls away, moved " + fueledMoved);
			helper.assertTrue(emptyMoved < 0.01, "an empty kart doesn't, moved " + emptyMoved);
			helper.assertTrue(empty.getDeltaMovement().horizontalDistance() < 0.01, "and has no speed");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 20)
	public void coalRefuelsByTheConfiguredAmount(GameTestHelper helper) {
		floor(helper);
		KartEntity kart = helper.spawn(VehicleRegistry.KART, new BlockPos(4, 1, 4));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		var cfg = BlockSoulsVehicles.CONFIG.fuel;
		kart.setFuel(100);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 2));
		kart.interact(player, InteractionHand.MAIN_HAND, kart.position());
		int coal = cfg.items.get("minecraft:coal");
		helper.assertTrue(kart.getFuel() == 100 + coal, "coal adds " + coal + ", fuel " + kart.getFuel());
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "one coal used");
		helper.assertFalse(player.isPassenger(), "refuelling doesn't board");

		kart.setFuel(cfg.fuelCapacity);
		kart.interact(player, InteractionHand.MAIN_HAND, kart.position());
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "a full tank takes no coal");
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void fuelSurvivesBreakingDownAndRepair(GameTestHelper helper) {
		floor(helper);
		KartEntity kart = helper.spawn(VehicleRegistry.KART, new BlockPos(4, 1, 4));
		kart.setFuel(1234);
		kart.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), BlockSoulsVehicles.CONFIG.kart.maxHealth * 2);
		KartWreckEntity fresh = helper.spawn(VehicleRegistry.KART_WRECK, new BlockPos(1, 1, 1));

		helper.runAfterDelay(2, () -> {
			List<KartWreckEntity> wrecks = find(helper, VehicleRegistry.KART_WRECK).stream().filter(w -> w != fresh).toList();
			helper.assertTrue(wrecks.size() == 1, "the kart broke down into a wreck");
			KartWreckEntity wreck = wrecks.get(0);
			helper.assertTrue(wreck.getStoredFuel() == 1234, "the wreck keeps the fuel, " + wreck.getStoredFuel());
			KartEntity repaired = wreck.repair(helper.getLevel());
			helper.assertTrue(repaired != null && repaired.getFuel() == 1234, "the repaired kart has it back");

			KartEntity fromFresh = fresh.repair(helper.getLevel());
			int quarter = KartFuel.repairedStart(BlockSoulsVehicles.CONFIG.fuel);
			helper.assertTrue(fromFresh != null && fromFresh.getFuel() == quarter, "a wreck that never ran starts at 25%");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void hornIsRateLimitedAndPushesANearbyWaveMob(GameTestHelper helper) {
		floor(helper);
		KartEntity kart = helper.spawn(VehicleRegistry.KART, new Vec3(2.5, 1, 4.5));
		ServerPlayer driver = helper.makeMockServerPlayerInLevel();
		ServerPlayer passenger = helper.makeMockServerPlayerInLevel();
		driver.startRiding(kart);
		passenger.startRiding(kart);
		Zombie near = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(5.5, 1, 4.5));
		Zombie far = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(2.5, 1, 4.5 + BlockSoulsVehicles.CONFIG.horn.flinchRadius + 3));
		var cfg = BlockSoulsVehicles.CONFIG.horn;

		helper.runAfterDelay(2, () -> {
			near.setDeltaMovement(Vec3.ZERO);
			far.setDeltaMovement(Vec3.ZERO);
			helper.assertFalse(KartHorn.honk(passenger), "only the driver honks");
			helper.assertTrue(KartHorn.honk(driver), "the driver honks");
			helper.assertFalse(KartHorn.honk(driver), "not again right away");
			Vec3 push = near.getDeltaMovement();
			helper.assertTrue(push.x > 0.1, "the nearby wave mob flinches away from the kart, " + push);
			helper.assertTrue(far.getDeltaMovement().horizontalDistance() < 1e-6, "a mob out of range doesn't");
		});
		helper.runAfterDelay(3 + cfg.cooldownTicks, () -> {
			near.setDeltaMovement(Vec3.ZERO);
			helper.assertTrue(KartHorn.honk(driver), "honks again after the cooldown");
			helper.assertTrue(near.getDeltaMovement().horizontalDistance() < 1e-6, "but the flinch has a longer cooldown");
			helper.succeed();
		});
	}
}
