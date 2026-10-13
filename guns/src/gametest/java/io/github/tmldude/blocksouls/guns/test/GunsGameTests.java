package io.github.tmldude.blocksouls.guns.test;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.guns.item.GunItem;
import io.github.tmldude.blocksouls.guns.item.GunRolls;
import io.github.tmldude.blocksouls.guns.item.GunsItems;
import io.github.tmldude.blocksouls.guns.logic.GunRarity;
import io.github.tmldude.blocksouls.guns.logic.Perk;
import io.github.tmldude.blocksouls.guns.loot.RarityBeam;
import io.github.tmldude.blocksouls.guns.loot.RollPerksFunction;
import io.github.tmldude.blocksouls.guns.shoot.GunServer;
import io.github.tmldude.blocksouls.guns.shoot.Hitscan;

public class GunsGameTests {
	/** A mock player standing at {@code feet} (relative), holding {@code gun}, aiming at {@code target} (absolute). */
	private static ServerPlayer shooter(GameTestHelper helper, Vec3 feet, ItemStack gun, Vec3 target) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 at = helper.absoluteVec(feet);
		player.setNoGravity(true);
		player.snapTo(at.x, at.y, at.z, 0, 0);
		player.getInventory().setSelectedSlot(0);
		player.getInventory().setItem(0, gun);
		aim(player, target);
		return player;
	}

	private static void aim(ServerPlayer player, Vec3 target) {
		Vec3 d = target.subtract(player.getEyePosition());
		float yRot = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
		float xRot = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		player.setYRot(yRot);
		player.setXRot(xRot);
		player.setYHeadRot(yRot);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z) {
		Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new Vec3(x, 1, z));
		zombie.setNoGravity(true);
		// Same size and armor every run.
		zombie.setBaby(false);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		return zombie;
	}

	private static Vec3 body(Zombie zombie) {
		return zombie.position().add(0, 0.7, 0);
	}

	private static Vec3 head(Zombie zombie) {
		return zombie.getEyePosition().add(0, 0.05, 0);
	}

	@GameTest
	public void firingDamagesTheZombieInFront(GameTestHelper helper) {
		Zombie zombie = zombie(helper, 1.5, 5.5);
		ServerPlayer player = shooter(helper, new Vec3(1.5, 1, 1.5), new ItemStack(GunsItems.STARTER_PISTOL), body(zombie));
		float before = zombie.getHealth();

		helper.assertTrue(GunServer.fire(player), "the shot went off");
		helper.assertTrue(zombie.getHealth() < before, "zombie took damage (" + before + " -> " + zombie.getHealth() + ")");
		helper.assertTrue(GunsItems.STARTER_PISTOL.mag(player.getMainHandItem()) == 7, "one round used");
		helper.succeed();
	}

	@GameTest
	public void precisionHitsDealMoreThanBodyHits(GameTestHelper helper) {
		Zombie bodyTarget = zombie(helper, 1.5, 5.5);
		Zombie headTarget = zombie(helper, 5.5, 5.5);
		ServerPlayer bodyShooter = shooter(helper, new Vec3(1.5, 1, 1.5), new ItemStack(GunsItems.STARTER_PISTOL), body(bodyTarget));
		ServerPlayer headShooter = shooter(helper, new Vec3(5.5, 1, 1.5), new ItemStack(GunsItems.STARTER_PISTOL), head(headTarget));

		float bodyBefore = bodyTarget.getHealth();
		float headBefore = headTarget.getHealth();
		helper.assertTrue(GunServer.fire(bodyShooter) && GunServer.fire(headShooter), "both shots went off");
		float bodyLoss = bodyBefore - bodyTarget.getHealth();
		float headLoss = headBefore - headTarget.getHealth();

		helper.assertTrue(bodyLoss > 0, "body shot hit");
		helper.assertTrue(headLoss > bodyLoss * 1.3F, "precision ×1.5: body " + bodyLoss + ", head " + headLoss);
		helper.succeed();
	}

	@GameTest
	public void cannotFireWhileDowned(GameTestHelper helper) {
		Zombie zombie = zombie(helper, 1.5, 5.5);
		ServerPlayer player = shooter(helper, new Vec3(1.5, 1, 1.5), new ItemStack(GunsItems.STARTER_PISTOL), body(zombie));
		PlayerStatus.setDowned(player, true);
		float before = zombie.getHealth();

		helper.assertFalse(GunServer.fire(player), "no shot while downed");
		helper.assertFalse(GunServer.startReload(player), "no reload while downed");
		helper.assertTrue(zombie.getHealth() == before, "zombie untouched");
		helper.assertTrue(GunsItems.STARTER_PISTOL.mag(player.getMainHandItem()) == 8, "no round used");
		helper.succeed();
	}

	@GameTest
	public void fireRateIsEnforced(GameTestHelper helper) {
		ServerPlayer player = shooter(helper, new Vec3(1.5, 1, 1.5), new ItemStack(GunsItems.HAND_CANNON), helper.absoluteVec(new Vec3(1.5, 2, 7)));
		helper.assertTrue(GunServer.fire(player), "first shot");
		helper.assertFalse(GunServer.fire(player), "second shot in the same tick is refused");
		helper.assertFalse(GunServer.fire(player), "third too");
		helper.assertTrue(GunsItems.HAND_CANNON.mag(player.getMainHandItem()) == 5, "only one round used");
		helper.succeed();
	}

	@GameTest
	public void explosiveRoundsDontBreakBlocks(GameTestHelper helper) {
		Zombie zombie = zombie(helper, 3.5, 5.5);
		// Blocks right beside and above the zombie's head, inside the blast radius.
		List<BlockPos> blocks = List.of(new BlockPos(2, 2, 5), new BlockPos(4, 2, 5), new BlockPos(3, 3, 6), new BlockPos(3, 3, 5).above());
		for (BlockPos pos : blocks) {
			helper.setBlock(pos, Blocks.GLASS);
		}
		ItemStack gun = new ItemStack(GunsItems.HAND_CANNON);
		GunRolls.set(gun, GunRarity.LEGENDARY, List.of(Perk.EXPLOSIVE_ROUNDS));
		ServerPlayer player = shooter(helper, new Vec3(3.5, 1, 1.5), gun, head(zombie));
		float before = zombie.getHealth();

		helper.assertTrue(GunServer.fire(player), "shot went off");
		helper.assertTrue(zombie.getHealth() < before, "zombie hit");
		for (BlockPos pos : blocks) {
			helper.assertBlockPresent(Blocks.GLASS, pos);
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void reloadConsumesAmmoItems(GameTestHelper helper) {
		ItemStack gun = new ItemStack(GunsItems.HAND_CANNON);
		GunsItems.HAND_CANNON.setMag(gun, 0);
		ServerPlayer player = shooter(helper, new Vec3(1.5, 1, 1.5), gun, helper.absoluteVec(new Vec3(1.5, 2, 7)));
		player.getAbilities().instabuild = false;
		player.getInventory().setItem(1, new ItemStack(GunsItems.PRIMARY_AMMO, 10));

		helper.assertTrue(GunServer.startReload(player), "reload started");
		helper.assertFalse(GunServer.fire(player), "can't fire mid-reload");
		helper.runAfterDelay(50, () -> {
			GunItem cannon = GunsItems.HAND_CANNON;
			helper.assertTrue(cannon.mag(player.getMainHandItem()) == 6, "mag full, got " + cannon.mag(player.getMainHandItem()));
			helper.assertTrue(GunServer.countAmmo(player, GunsItems.PRIMARY_AMMO) == 4, "6 rounds taken, left " + GunServer.countAmmo(player, GunsItems.PRIMARY_AMMO));
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void swappingCancelsTheReload(GameTestHelper helper) {
		ItemStack gun = new ItemStack(GunsItems.HAND_CANNON);
		GunsItems.HAND_CANNON.setMag(gun, 0);
		ServerPlayer player = shooter(helper, new Vec3(1.5, 1, 1.5), gun, helper.absoluteVec(new Vec3(1.5, 2, 7)));
		player.getAbilities().instabuild = false;
		player.getInventory().setItem(1, new ItemStack(GunsItems.PRIMARY_AMMO, 10));

		helper.assertTrue(GunServer.startReload(player), "reload started");
		helper.runAfterDelay(5, () -> player.getInventory().setSelectedSlot(1));
		helper.runAfterDelay(50, () -> {
			helper.assertTrue(GunsItems.HAND_CANNON.mag(gun) == 0, "reload was cancelled");
			helper.assertTrue(GunServer.countAmmo(player, GunsItems.PRIMARY_AMMO) == 10, "no ammo taken");
			helper.succeed();
		});
	}

	@GameTest
	public void bouncyBulletsRicochetOffBlocks(GameTestHelper helper) {
		// A wall along z = 4; a shot at 45° bounces off it into the zombie.
		for (int x = 1; x <= 7; x++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
			}
		}
		Zombie zombie = zombie(helper, 6.5, 1.5);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 from = helper.absoluteVec(new Vec3(1.5, 2.5, 1.5));
		Vec3 dir = new Vec3(1, 0, 1).normalize();

		Hitscan.Trace straight = Hitscan.trace(helper.getLevel(), player, from, dir, 30, 0, 0.3);
		helper.assertTrue(straight.hit() == null, "without Bouncy the wall stops the bullet");

		Hitscan.Trace bounced = Hitscan.trace(helper.getLevel(), player, from, dir, 30, 1, 0.3);
		helper.assertTrue(bounced.hit() != null && bounced.hit().getEntity() == zombie, "the ricochet hits the zombie");
		helper.assertTrue(bounced.bounces() == 1, "one bounce");
		helper.assertTrue(bounced.path().size() == 3, "path: start, bounce point, hit");
		helper.succeed();
	}

	@GameTest
	public void rollPerksGivesRarityAndPerkCount(GameTestHelper helper) {
		LootContext context = new LootContext.Builder(new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY))
				.create(Optional.empty());
		for (String tier : List.of("common", "rare", "legendary")) {
			LootItemFunction function = RollPerksFunction.roll(tier).build();
			for (int i = 0; i < 40; i++) {
				ItemStack stack = function.apply(new ItemStack(GunsItems.AUTO_RIFLE), context);
				helper.assertTrue(GunRolls.isRolled(stack), "rolled a rarity");
				GunRarity rarity = GunRolls.rarity(stack);
				helper.assertTrue(GunRolls.perks(stack).size() == rarity.perkCount(),
						rarity.id() + " gun has " + rarity.perkCount() + " perks, got " + GunRolls.perks(stack));
			}
		}
		// Legendary tier never rolls common; the Starter Pistol is never rolled.
		LootItemFunction legendary = RollPerksFunction.roll("legendary").build();
		for (int i = 0; i < 40; i++) {
			helper.assertTrue(GunRolls.rarity(legendary.apply(new ItemStack(GunsItems.SHOTGUN), context)) != GunRarity.COMMON, "legendary tier");
		}
		ItemStack pistol = legendary.apply(new ItemStack(GunsItems.STARTER_PISTOL), context);
		helper.assertTrue(GunRolls.rarity(pistol) == GunRarity.COMMON && GunRolls.perks(pistol).isEmpty(), "starter pistol stays plain");
		helper.succeed();
	}

	@GameTest
	public void bulletsCreditTheShooterAndGun(GameTestHelper helper) {
		Zombie zombie = zombie(helper, 1.5, 5.5);
		ItemStack gun = new ItemStack(GunsItems.HAND_CANNON);
		ServerPlayer player = shooter(helper, new Vec3(1.5, 1, 1.5), gun, body(zombie));

		helper.assertTrue(GunServer.fire(player), "shot went off");
		var source = zombie.getLastDamageSource();
		helper.assertTrue(source != null && source.is(GunServer.BULLET), "bullet damage type");
		helper.assertTrue(source.getEntity() == player, "shooter is the attacker");
		helper.assertTrue(source.getWeaponItem() == gun, "gun is the weapon");
		helper.assertTrue(zombie.getLastHurtByPlayer() != null, "kill credit goes to the player");
		helper.succeed();
	}

	private static ItemEntity drop(GameTestHelper helper, GunRarity rarity) {
		ItemStack stack = new ItemStack(GunsItems.HAND_CANNON);
		GunRolls.set(stack, rarity, List.of());
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.2, 1.5));
		ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
		helper.getLevel().addFreshEntity(item);
		return item;
	}

	@GameTest
	public void aRareGunDropCarriesTheBeam(GameTestHelper helper) {
		ItemEntity rare = drop(helper, GunRarity.RARE);
		helper.assertTrue(RarityBeam.hasBeam(rare), "rare gun item entity carries the beam marker");
		helper.assertTrue(rare.hasGlowingTag(), "and glows");
		helper.assertTrue(rare.isCustomNameVisible() && rare.getCustomName() != null, "and shows its name");
		helper.assertTrue(RarityBeam.hasBeam(drop(helper, GunRarity.LEGENDARY)), "legendary too");
		helper.assertFalse(RarityBeam.hasBeam(drop(helper, GunRarity.COMMON)), "common guns stay plain");
		helper.succeed();
	}
}
