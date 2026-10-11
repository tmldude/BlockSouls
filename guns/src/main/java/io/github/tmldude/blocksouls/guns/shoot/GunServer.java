package io.github.tmldude.blocksouls.guns.shoot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import io.github.tmldude.blocksouls.common.CommonIds;
import io.github.tmldude.blocksouls.common.CommonTags;
import io.github.tmldude.blocksouls.common.Networking;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.guns.BlockSoulsGuns;
import io.github.tmldude.blocksouls.guns.GunsConfig;
import io.github.tmldude.blocksouls.guns.item.GunItem;
import io.github.tmldude.blocksouls.guns.item.GunRolls;
import io.github.tmldude.blocksouls.guns.item.GunsItems;
import io.github.tmldude.blocksouls.guns.logic.FireGate;
import io.github.tmldude.blocksouls.guns.logic.Perk;
import io.github.tmldude.blocksouls.guns.logic.RampageStacks;
import io.github.tmldude.blocksouls.guns.logic.ShotMath;
import io.github.tmldude.blocksouls.guns.logic.Spread;
import io.github.tmldude.blocksouls.guns.net.FirePayload;
import io.github.tmldude.blocksouls.guns.net.HitMarkerPayload;
import io.github.tmldude.blocksouls.guns.net.ReloadPayload;
import io.github.tmldude.blocksouls.guns.net.ReloadStatePayload;
import io.github.tmldude.blocksouls.guns.net.ShotFxPayload;
import io.github.tmldude.blocksouls.guns.net.StatsSyncPayload;
import io.github.tmldude.blocksouls.guns.sound.GunSounds;
import io.github.tmldude.blocksouls.guns.weapon.GunStats;
import io.github.tmldude.blocksouls.guns.weapon.Weapons;

/**
 * Server-authoritative shooting: validates every trigger pull (state, ammo, fire rate), traces the bullets and
 * applies damage, perks and reloads.
 */
public final class GunServer {
	public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE, CommonIds.id(BlockSoulsGuns.MOD_ID, "bullet"));
	private static final double FX_RANGE = 96;
	private static final Gson GSON = new Gson();

	private static final Map<UUID, Shooter> SHOOTERS = new HashMap<>();

	private GunServer() {
	}

	/** Per-player shooting state. Not saved: a relog just resets fire rate, reloads and perk timers. */
	public static final class Shooter {
		final FireGate gate = new FireGate();
		long reloadEnd = -1;
		int reloadSlot;
		@Nullable ItemStack reloadStack;
		RampageStacks rampage = RampageStacks.NONE;
		long outlawUntil;

		public boolean reloading() {
			return reloadStack != null;
		}

		public int rampageStacks(long now) {
			return rampage.active(now);
		}

		public boolean outlawActive(long now) {
			return now < outlawUntil;
		}
	}

	public static void register() {
		Networking.registerServerbound(FirePayload.TYPE, FirePayload.CODEC, (payload, context) -> fire(context.player()));
		Networking.registerServerbound(ReloadPayload.TYPE, ReloadPayload.CODEC, (payload, context) -> startReload(context.player()));
		Networking.registerClientbound(HitMarkerPayload.TYPE, HitMarkerPayload.CODEC);
		Networking.registerClientbound(ShotFxPayload.TYPE, ShotFxPayload.CODEC);
		Networking.registerClientbound(ReloadStatePayload.TYPE, ReloadStatePayload.CODEC);
		Networking.registerClientbound(StatsSyncPayload.TYPE, StatsSyncPayload.CODEC);

		ServerTickEvents.END_SERVER_TICK.register(GunServer::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				Networking.sendToPlayer(handler.player, new StatsSyncPayload(GSON.toJson(Weapons.active()))));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SHOOTERS.remove(handler.player.getUUID()));
	}

	public static Shooter shooter(ServerPlayer player) {
		return SHOOTERS.computeIfAbsent(player.getUUID(), uuid -> new Shooter());
	}

	public static long now(ServerPlayer player) {
		return player.level().getServer().getTickCount();
	}

	private static GunsConfig config() {
		return BlockSoulsGuns.CONFIG;
	}

	private static boolean canAct(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !PlayerStatus.isDowned(player);
	}

	// ---- Firing ----------------------------------------------------------------------------------------------

	/** One trigger pull with the main-hand gun. Returns whether a shot went off. */
	public static boolean fire(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		GunItem gun = GunItem.of(stack);
		if (gun == null || !canAct(player)) {
			return false;
		}

		Shooter shooter = shooter(player);
		if (shooter.reloading()) {
			return false;
		}

		GunStats stats = gun.stats();
		long now = now(player);
		int mag = gun.mag(stack);
		if (mag <= 0) {
			// Auto-reload on an empty trigger pull, at most once per fire interval so a held trigger doesn't spam clicks.
			if (shooter.gate.tryFire(now, ShotMath.fireIntervalTicks(stats.rpm()), 0)) {
				playSound(player, GunSounds.EMPTY, 0.6F, 1.2F);
				startReload(player);
			}
			return false;
		}
		if (!shooter.gate.tryFire(now, ShotMath.fireIntervalTicks(stats.rpm()), config().shooting.fireRateTolerance)) {
			return false;
		}

		gun.setMag(stack, mag - 1);
		shoot(player, stack, gun, stats, shooter, now);
		return true;
	}

	private static void shoot(ServerPlayer player, ItemStack stack, GunItem gun, GunStats stats, Shooter shooter, long now) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		double spread = isAiming(player) ? stats.adsSpread() : stats.hipSpread();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		int maxBounces = bouncyLevel(level, stack);

		List<List<Vec3>> tracers = new ArrayList<>();
		int marker = -1;
		boolean ricochet = false;
		for (int pellet = 0; pellet < stats.pellets(); pellet++) {
			double[] d = Spread.deflect(look.x, look.y, look.z, spread, random.nextDouble(), random.nextDouble());
			Vec3 dir = new Vec3(d[0], d[1], d[2]);
			Hitscan.Trace trace = Hitscan.trace(level, player, eye, dir, stats.range(), maxBounces, config().shooting.hitboxMargin);
			tracers.add(trace.path());
			ricochet |= trace.path().size() > 2;
			if (trace.hit() != null) {
				marker = Math.max(marker, hit(player, stack, stats, shooter, now, trace, dir));
			}
		}

		playSound(player, GunSounds.fire(gun.weaponId()), 1.0F, 0.95F + random.nextFloat() * 0.1F);
		if (ricochet) {
			List<Vec3> first = tracers.getFirst();
			level.playSound(null, first.get(1).x, first.get(1).y, first.get(1).z, GunSounds.RICOCHET, SoundSource.PLAYERS, 0.6F, 1.0F);
		}

		ShotFxPayload fx = new ShotFxPayload(player.getId(), tracers);
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(player) < FX_RANGE * FX_RANGE) {
				Networking.sendToPlayer(viewer, fx);
			}
		}
		if (marker >= 0) {
			Networking.sendToPlayer(player, new HitMarkerPayload(marker));
		}
	}

	/** Applies one bullet's hit. Returns the hit-marker kind, or -1 if the target took no damage. */
	private static int hit(ServerPlayer player, ItemStack stack, GunStats stats, Shooter shooter, long now, Hitscan.Trace trace, Vec3 dir) {
		ServerLevel level = player.level();
		EntityHitResult result = trace.hit();
		Entity target = result.getEntity();
		Vec3 point = result.getLocation();
		GunsConfig.Perks perks = config().perks;

		boolean precision = target instanceof LivingEntity && ShotMath.isPrecision(point.y, target.getEyeY(), target.getBbHeight());
		double perkMult = 1.0;
		if (GunRolls.hasPerk(stack, Perk.RAMPAGE)) {
			perkMult *= shooter.rampage.multiplier(now, perks.rampageBonusPerStack);
		}
		if (GunRolls.hasPerk(stack, Perk.VORPAL) && isBossOrElite(target)) {
			perkMult *= perks.vorpalMult;
		}
		double damage = ShotMath.damage(stats.damage(), GunRolls.damageMult(GunRolls.rarity(stack)), precision, stats.precisionMult(),
				perkMult, ShotMath.falloff(trace.distance(), stats.falloffStart(), stats.range(), stats.falloffMinMult()),
				trace.bounces(), config().shooting.bounceDamageMult);

		boolean wasAlive = target.isAlive();
		if (!target.hurtServer(level, bullet(player), (float) damage)) {
			return -1;
		}
		if (target instanceof LivingEntity living && stats.knockback() > 0) {
			living.knockback(stats.knockback(), -dir.x, -dir.z, bullet(player), (float) damage);
		}
		if (precision) {
			level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 8, 0.1, 0.1, 0.1, 0.25);
			if (GunRolls.hasPerk(stack, Perk.EXPLOSIVE_ROUNDS)) {
				explode(player, point, damage * perks.explosiveShare);
			}
		}

		boolean killed = wasAlive && !target.isAlive();
		if (killed) {
			if (precision && GunRolls.hasPerk(stack, Perk.OUTLAW)) {
				shooter.outlawUntil = now + perks.outlawDurationTicks;
			}
			if (GunRolls.hasPerk(stack, Perk.RAMPAGE)) {
				shooter.rampage = shooter.rampage.onKill(now, perks.rampageDurationTicks, perks.rampageMaxStacks);
			}
		}
		return killed ? HitMarkerPayload.KILL : precision ? HitMarkerPayload.PRECISION : HitMarkerPayload.BODY;
	}

	/** Explosive Rounds: area damage around the hit. Never touches blocks (no vanilla explosion involved). */
	private static void explode(ServerPlayer player, Vec3 point, double damage) {
		ServerLevel level = player.level();
		double radius = config().perks.explosiveRadius;
		AABB area = new AABB(point, point).inflate(radius);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
			if (entity.getBoundingBox().distanceToSqr(point) <= radius * radius) {
				entity.hurtServer(level, bullet(player), (float) damage);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y, point.z, 1, 0, 0, 0, 0);
		level.playSound(null, point.x, point.y, point.z, GunSounds.EXPLOSIVE_ROUND, SoundSource.PLAYERS, 0.8F, 1.3F);
	}

	/** Bullet damage: the player is the attacker and (as the direct entity) supplies the gun as the weapon item. */
	public static DamageSource bullet(ServerPlayer player) {
		Holder<DamageType> type = player.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BULLET);
		return new DamageSource(type, player, player);
	}

	private static boolean isBossOrElite(Entity target) {
		return target.is(CommonTags.BOSSES) || target.is(CommonTags.ELITES);
	}

	/** Aiming down sights = using (holding use with) the gun in the main hand. */
	public static boolean isAiming(ServerPlayer player) {
		return player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND && player.getUseItem().getItem() instanceof GunItem;
	}

	/** Level of world's Bouncy enchantment on the gun; 0 when the enchantment isn't installed. */
	public static int bouncyLevel(ServerLevel level, ItemStack stack) {
		ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, CommonIds.BOUNCY);
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
				.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
				.orElse(0);
	}

	// ---- Reloading -------------------------------------------------------------------------------------------

	/** Starts reloading the main-hand gun. Returns whether a reload started. */
	public static boolean startReload(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		GunItem gun = GunItem.of(stack);
		if (gun == null || !canAct(player)) {
			return false;
		}
		Shooter shooter = shooter(player);
		GunStats stats = gun.stats();
		if (shooter.reloading() || gun.mag(stack) >= stats.magSize()) {
			return false;
		}
		if (!freeAmmo(player, stats) && countAmmo(player, GunsItems.ammoFor(stats.ammo())) <= 0) {
			return false;
		}

		long now = now(player);
		int ticks = ShotMath.reloadTicks(stats.reloadSeconds(), PlayerTraits.level(player, PlayerTraits.RELOAD_SPEED),
				config().shooting.reloadSpeedPerLevel, shooter.outlawActive(now), config().perks.outlawReloadSpeed);
		shooter.reloadEnd = now + ticks;
		shooter.reloadSlot = player.getInventory().getSelectedSlot();
		shooter.reloadStack = stack;
		playSound(player, GunSounds.RELOAD_START, 0.8F, 1.0F);
		Networking.sendToPlayer(player, new ReloadStatePayload(ticks));
		return true;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Shooter shooter = SHOOTERS.get(player.getUUID());
			if (shooter == null || !shooter.reloading()) {
				continue;
			}

			ItemStack stack = shooter.reloadStack;
			if (!canAct(player) || player.getInventory().getSelectedSlot() != shooter.reloadSlot || player.getMainHandItem() != stack
					|| !(stack.getItem() instanceof GunItem gun)) {
				// Swapping items (or going down) cancels the reload.
				endReload(player, shooter);
				continue;
			}
			if (now(player) >= shooter.reloadEnd) {
				GunStats stats = gun.stats();
				int needed = stats.magSize() - gun.mag(stack);
				int loaded = freeAmmo(player, stats) ? needed : takeAmmo(player, GunsItems.ammoFor(stats.ammo()), needed);
				gun.setMag(stack, gun.mag(stack) + loaded);
				playSound(player, GunSounds.RELOAD_END, 0.8F, 1.0F);
				endReload(player, shooter);
			}
		}
	}

	private static void endReload(ServerPlayer player, Shooter shooter) {
		shooter.reloadStack = null;
		shooter.reloadEnd = -1;
		Networking.sendToPlayer(player, new ReloadStatePayload(0));
	}

	private static boolean freeAmmo(ServerPlayer player, GunStats stats) {
		return stats.infiniteAmmo() || player.hasInfiniteMaterials();
	}

	public static int countAmmo(ServerPlayer player, @Nullable Item ammo) {
		if (ammo == null) {
			return 0;
		}
		Inventory inventory = player.getInventory();
		int count = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ammo)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static int takeAmmo(ServerPlayer player, @Nullable Item ammo, int wanted) {
		if (ammo == null) {
			return 0;
		}
		Inventory inventory = player.getInventory();
		int taken = 0;
		for (int i = 0; i < inventory.getContainerSize() && taken < wanted; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ammo)) {
				int take = Math.min(wanted - taken, stack.getCount());
				stack.shrink(take);
				taken += take;
			}
		}
		inventory.setChanged();
		return taken;
	}

	private static void playSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}
}
