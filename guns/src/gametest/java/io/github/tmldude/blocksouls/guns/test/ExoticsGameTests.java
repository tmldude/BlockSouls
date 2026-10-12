package io.github.tmldude.blocksouls.guns.test;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.event.CombatEvents;
import io.github.tmldude.blocksouls.common.event.PlayerMoveEvents;
import io.github.tmldude.blocksouls.common.player.WeaponHooks;
import io.github.tmldude.blocksouls.common.synergy.SynergyTags;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.exotic.Exotics;
import io.github.tmldude.blocksouls.guns.exotic.KindlingEngine;
import io.github.tmldude.blocksouls.guns.item.GunItem;
import io.github.tmldude.blocksouls.guns.item.GunRolls;
import io.github.tmldude.blocksouls.guns.item.GunsItems;
import io.github.tmldude.blocksouls.guns.logic.GunRarity;
import io.github.tmldude.blocksouls.guns.shoot.GunServer;

/** The five exotics' rule-breaks, the feel-pass hooks (events, reload-cancel) and the exotic recipes. */
public class ExoticsGameTests {
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
	public void exoticsAreAlwaysExoticWithOnePerk(GameTestHelper helper) {
		for (var exotic : GunsItems.exotics()) {
			ItemStack stack = new ItemStack(exotic);
			GunRolls.roll(stack, GunRolls.odds("common"), helper.getLevel().getRandom());
			helper.assertTrue(GunRolls.rarity(stack) == GunRarity.EXOTIC, exotic.weaponId() + " rolls exotic whatever the odds");
			helper.assertTrue(GunRolls.perks(stack).size() == 1, exotic.weaponId() + " has one perk, got " + GunRolls.perks(stack));
			helper.assertTrue(Exotics.of(exotic.weaponId()) != null, exotic.weaponId() + " has a behavior");
		}
		helper.succeed();
	}

	@GameTest
	public void backdraftLaunchesTheShooterBackward(GameTestHelper helper) {
		ServerPlayer player = shooter(helper, new Vec3(2.5, 1, 1.5), new ItemStack(GunsItems.BACKDRAFT), helper.absoluteVec(new Vec3(2.5, 2.5, 7)));
		helper.assertTrue(GunServer.fire(player), "shot went off");
		Vec3 velocity = player.getDeltaMovement();
		helper.assertTrue(velocity.z < -0.3, "launched backward (away from +z): " + velocity);
		helper.assertTrue(velocity.y > 0, "with a little lift: " + velocity);
		helper.succeed();
	}

	@GameTest
	public void strayVerdictLastRoundRicochetsToASecondTarget(GameTestHelper helper) {
		Zombie first = zombie(helper, 2.5, 5.5);
		Zombie second = zombie(helper, 5.5, 5.5);
		ItemStack gun = new ItemStack(GunsItems.STRAY_VERDICT);
		GunsItems.STRAY_VERDICT.setMag(gun, 1);
		ServerPlayer player = shooter(helper, new Vec3(2.5, 1, 1.5), gun, body(first));
		float firstBefore = first.getHealth();
		float secondBefore = second.getHealth();

		helper.assertTrue(GunServer.fire(player), "the last round went off");
		helper.assertTrue(first.getHealth() < firstBefore, "the aimed zombie was hit");
		helper.assertTrue(second.getHealth() < secondBefore, "the ricochet hit the second zombie (" + secondBefore + " -> " + second.getHealth() + ")");
		helper.succeed();
	}

	@GameTest
	public void lanternEyePrecisionKillMarksNeighbours(GameTestHelper helper) {
		Zombie target = zombie(helper, 2.5, 5.5);
		Zombie neighbour = zombie(helper, 5.5, 5.5);
		target.setHealth(1);
		ServerPlayer player = shooter(helper, new Vec3(2.5, 1, 1.5), new ItemStack(GunsItems.LANTERN_EYE), head(target));
		long now = helper.getLevel().getGameTime();
		helper.assertTrue(!Exotics.isMarked(neighbour, now), "not marked before");

		helper.assertTrue(GunServer.fire(player), "shot went off");
		helper.assertTrue(!target.isAlive(), "precision kill");
		helper.assertTrue(Exotics.isMarked(neighbour, now), "the neighbour is marked");
		helper.assertTrue(Exotics.markedMultiplier(neighbour, now) > 1.0, "marked targets take bonus damage");
		helper.assertTrue(!Exotics.isMarked(neighbour, now + BlockSoulsGuns.CONFIG.exotics.lanternEyeMarkTicks + 1), "the mark expires");
		helper.succeed();
	}

	@GameTest
	public void kindlingEngineHeatsUpAndVents(GameTestHelper helper) {
		Zombie zombie = zombie(helper, 2.5, 5.5);
		zombie.setHealth(2000);
		ItemStack gun = new ItemStack(GunsItems.KINDLING_ENGINE);
		ServerPlayer player = shooter(helper, new Vec3(2.5, 1, 1.5), gun, body(zombie));
		long now = helper.getLevel().getGameTime();
		helper.assertTrue(KindlingEngine.heat(gun, now) == 0, "cold to start");
		helper.assertTrue(GunServer.fire(player), "first shot");
		double heat = KindlingEngine.heat(gun, now);
		helper.assertTrue(heat > 0, "a hit adds heat: " + heat);
		helper.assertTrue(!KindlingEngine.state(gun).venting(now), "not venting yet");
		// Force the heat to the top: the next hit vents the gun and it refuses to fire.
		gun.set(io.github.tmldude.blocksouls.guns.item.GunComponents.KINDLING,
				new io.github.tmldude.blocksouls.guns.exotic.KindlingState(99, now, 0));
		helper.runAfterDelay(2, () -> {
			long later = helper.getLevel().getGameTime();
			helper.assertTrue(GunServer.fire(player), "the shot that overheats");
			helper.assertTrue(KindlingEngine.state(gun).venting(later), "venting at full heat");
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(!GunServer.fire(player), "no shots while venting");
				helper.succeed();
			});
		});
	}

	@GameTest
	public void tetherLanceBlastsAndTethersTwoMobs(GameTestHelper helper) {
		Zombie a = zombie(helper, 2.5, 6.5);
		Zombie b = zombie(helper, 4.0, 6.5);
		ServerPlayer player = shooter(helper, new Vec3(2.5, 1, 1.5), new ItemStack(GunsItems.TETHER_LANCE), body(a));
		float bBefore = b.getHealth();
		int tethers = Exotics.tethers();

		helper.assertTrue(GunServer.fire(player), "rocket fired");
		helper.assertTrue(b.getHealth() < bBefore, "the blast hurt the second mob too");
		helper.assertTrue(Exotics.tethers() == tethers + 1, "the two mobs are tethered");
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void rollingFinishesALateReload(GameTestHelper helper) {
		ItemStack gun = new ItemStack(GunsItems.HAND_CANNON);
		GunsItems.HAND_CANNON.setMag(gun, 0);
		ServerPlayer player = shooter(helper, new Vec3(1.5, 1, 1.5), gun, helper.absoluteVec(new Vec3(1.5, 2, 7)));
		player.getAbilities().instabuild = false;
		player.getInventory().setItem(1, new ItemStack(GunsItems.PRIMARY_AMMO, 10));

		helper.assertTrue(GunServer.startReload(player), "reload started");
		helper.assertTrue(!GunServer.onRoll(player), "a roll at 0% doesn't finish it");
		helper.assertTrue(GunsItems.HAND_CANNON.mag(gun) == 0, "still empty");
		int reloadTicks = (int) Math.ceil(GunsItems.HAND_CANNON.stats().reloadSeconds() * 20);
		int late = (int) Math.ceil(reloadTicks * BlockSoulsGuns.CONFIG.shooting.reloadCancelThreshold) + 1;
		helper.runAfterDelay(late, () -> {
			helper.assertTrue(GunServer.reloadProgress(player) >= BlockSoulsGuns.CONFIG.shooting.reloadCancelThreshold, "past the threshold");
			PlayerMoveEvents.ROLLED.invoker().onPlayer(player);
			helper.assertTrue(GunsItems.HAND_CANNON.mag(gun) == 6, "the roll finished the reload, mag " + GunsItems.HAND_CANNON.mag(gun));
			helper.assertTrue(GunServer.countAmmo(player, GunsItems.PRIMARY_AMMO) == 4, "ammo taken");
			helper.succeed();
		});
	}

	@GameTest
	public void hitsFireTheCombatEventAndWeaponHooksReload(GameTestHelper helper) {
		Zombie zombie = zombie(helper, 2.5, 5.5);
		ItemStack gun = new ItemStack(GunsItems.HAND_CANNON);
		ServerPlayer player = shooter(helper, new Vec3(2.5, 1, 1.5), gun, head(zombie));
		player.getAbilities().instabuild = false;
		player.getInventory().setItem(1, new ItemStack(GunsItems.PRIMARY_AMMO, 10));
		boolean[] seen = new boolean[2];
		CombatEvents.HIT.register((shooter, target, damage, precision, killed) -> {
			if (shooter == player && target == zombie && precision && damage > 0) {
				seen[0] = true;
			}
		});
		CombatEvents.RELOADED.register((p, rounds) -> {
			if (p == player && rounds == 1) {
				seen[1] = true;
			}
		});

		helper.assertTrue(GunServer.fire(player), "shot went off");
		helper.assertTrue(seen[0], "CombatEvents.HIT fired with precision");
		helper.assertTrue(WeaponHooks.reload(player), "WeaponHooks.reload fills the mag");
		helper.assertTrue(GunsItems.HAND_CANNON.mag(gun) == 6, "mag full again");
		helper.assertTrue(seen[1], "CombatEvents.RELOADED fired with the rounds loaded");
		helper.assertTrue(GunServer.countAmmo(player, GunsItems.PRIMARY_AMMO) == 9, "one round of ammo taken");
		helper.succeed();
	}

	@GameTest
	public void heldGunPerksCountAsBuildTags(GameTestHelper helper) {
		ItemStack gun = new ItemStack(GunsItems.KINDLING_ENGINE);
		GunRolls.set(gun, GunRarity.EXOTIC, List.of(io.github.tmldude.blocksouls.guns.logic.Perk.RAMPAGE));
		ServerPlayer player = shooter(helper, new Vec3(2.5, 1, 1.5), gun, helper.absoluteVec(new Vec3(2.5, 2, 7)));
		var tags = SynergyTags.of(player);
		helper.assertTrue(tags.getOrDefault(SynergyTags.ON_KILL, 0) == 1, "Rampage counts as on_kill: " + tags);
		helper.assertTrue(tags.getOrDefault(SynergyTags.FIRE, 0) == 1, "the Kindling Engine counts as fire: " + tags);
		helper.succeed();
	}

	@GameTest
	public void exoticSmithingRecipesLoad(GameTestHelper helper) {
		var recipes = helper.getLevel().getServer().getRecipeManager();
		for (var exotic : GunsItems.exotics()) {
			Identifier id = CommonIds.id(CommonIds.GUNS, exotic.weaponId());
			helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, id)).isPresent(), "smithing recipe " + id);
		}
		helper.succeed();
	}
}
