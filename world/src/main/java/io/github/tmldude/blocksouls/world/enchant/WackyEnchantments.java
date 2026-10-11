package io.github.tmldude.blocksouls.world.enchant;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.world.BlockSoulsWorld;
import io.github.tmldude.blocksouls.world.WorldConfig;

/**
 * The code half of the wacky enchantments (definitions are data in {@code data/blocksouls_world/enchantment}).
 * Moonwalk is pure data (attribute effects). Bouncy and Grapple's block hits live in {@code AbstractArrowMixin}.
 */
public final class WackyEnchantments {
	public static final ResourceKey<Enchantment> BOUNCY = key(CommonIds.BOUNCY);
	public static final ResourceKey<Enchantment> COMBUSTION = key(CommonIds.COMBUSTION);
	public static final ResourceKey<Enchantment> VAMPIRIC = key(CommonIds.VAMPIRIC);
	public static final ResourceKey<Enchantment> GRAPPLE = key(CommonIds.GRAPPLE);
	public static final ResourceKey<Enchantment> MAGNETIZED = key(CommonIds.MAGNETIZED);
	public static final ResourceKey<Enchantment> MOONWALK = key(CommonIds.MOONWALK);

	private WackyEnchantments() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register(WackyEnchantments::onKill);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> onHit(entity, source));
		ServerTickEvents.END_SERVER_TICK.register(WackyEnchantments::tickMagnets);
	}

	public static Optional<Holder<Enchantment>> holder(Level level, ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).map(h -> h);
	}

	public static int level(Level level, ResourceKey<Enchantment> key, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		return holder(level, key).map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
	}

	/** The weapon behind a damage source: the bow/gun/trident that fired it, else the attacker's main hand. */
	public static ItemStack weapon(DamageSource source, LivingEntity attacker) {
		ItemStack weapon = source.getWeaponItem();
		return weapon != null && !weapon.isEmpty() ? weapon : attacker.getMainHandItem();
	}

	static void onKill(LivingEntity victim, DamageSource source) {
		if (!(victim.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity killer) || killer == victim) {
			return;
		}
		// Explosions don't chain: a Combustion blast killing a mob doesn't set off another.
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return;
		}
		ItemStack weapon = weapon(source, killer);
		WorldConfig.Enchants cfg = BlockSoulsWorld.CONFIG.enchants;

		if (level(level, COMBUSTION, weapon) > 0) {
			combust(level, victim.position().add(0, victim.getBbHeight() / 2, 0), killer, cfg.combustionRadius);
		}
		int vampiric = level(level, VAMPIRIC, weapon);
		if (vampiric > 0 && killer.isAlive()) {
			killer.heal(WackyMath.vampiricHeal(vampiric, cfg.vampiricHealPerLevel));
		}
	}

	/** A blast that hurts mobs only: never blocks, players (owner included) or dropped items. */
	public static void combust(ServerLevel level, Vec3 at, LivingEntity owner, float radius) {
		ExplosionDamageCalculator mobsOnly = new ExplosionDamageCalculator() {
			@Override
			public boolean shouldBlockExplode(Explosion explosion, net.minecraft.world.level.BlockGetter getter, net.minecraft.core.BlockPos pos,
					net.minecraft.world.level.block.state.BlockState state, float power) {
				return false;
			}

			@Override
			public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
				return entity instanceof LivingEntity && !(entity instanceof Player) && entity != owner;
			}

			@Override
			public float getKnockbackMultiplier(Entity entity) {
				return shouldDamageEntity(null, entity) ? 1.0F : 0.0F;
			}
		};
		level.explode(null, level.damageSources().explosion(null, owner), mobsOnly, at.x, at.y, at.z, radius, false,
				Level.ExplosionInteraction.NONE);
	}

	static void onHit(LivingEntity target, DamageSource source) {
		if (!(target.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == target) {
			return;
		}
		// Grapple only fires from ranged hits (an arrow, a bullet): a melee swing never pulls you.
		if (source.getDirectEntity() == attacker && source.getWeaponItem() == null) {
			return;
		}
		if (level(level, GRAPPLE, weapon(source, attacker)) > 0) {
			grapple(attacker, target.position().add(0, target.getBbHeight() / 2, 0));
		}
	}

	/** Pulls {@code who} toward {@code to} if within grapple range. Returns whether it pulled. */
	public static boolean grapple(LivingEntity who, Vec3 to) {
		WorldConfig.Enchants cfg = BlockSoulsWorld.CONFIG.enchants;
		double[] v = WackyMath.grapplePull(new double[] {who.getX(), who.getY(), who.getZ()}, new double[] {to.x, to.y, to.z},
				cfg.grappleSpeed, cfg.grappleRange);
		if (v == null) {
			return false;
		}
		who.setDeltaMovement(v[0], v[1], v[2]);
		who.resetFallDistance();
		who.syncVelocity = true; // sync the new velocity to the client
		return true;
	}

	private static void tickMagnets(MinecraftServer server) {
		WorldConfig.Enchants cfg = BlockSoulsWorld.CONFIG.enchants;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			int lvl = level(player.level(), MAGNETIZED, player.getItemBySlot(EquipmentSlot.LEGS));
			double radius = WackyMath.magnetRadius(lvl, cfg.magnetRadiusLevel1, cfg.magnetRadiusLevel2);
			if (radius > 0) {
				pullDrops(player, radius, cfg.magnetSpeed);
			}
		}
	}

	public static void pullDrops(Player player, double radius, double speed) {
		AABB box = player.getBoundingBox().inflate(radius);
		double[] to = {player.getX(), player.getY() + 0.5, player.getZ()};
		for (Entity drop : player.level().getEntities(player, box, e -> e instanceof ItemEntity || e instanceof ExperienceOrb)) {
			if (drop instanceof ItemEntity item && item.hasPickUpDelay()) {
				continue; // just thrown: don't yank it straight back
			}
			if (drop.distanceToSqr(player) > radius * radius) {
				continue;
			}
			double[] v = WackyMath.magnetPull(new double[] {drop.getX(), drop.getY(), drop.getZ()}, to, speed);
			drop.setDeltaMovement(v[0], v[1], v[2]);
			drop.syncVelocity = true;
		}
	}

	private static ResourceKey<Enchantment> key(net.minecraft.resources.Identifier id) {
		return ResourceKey.create(Registries.ENCHANTMENT, id);
	}
}
