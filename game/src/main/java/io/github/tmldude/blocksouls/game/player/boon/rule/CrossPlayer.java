package io.github.tmldude.blocksouls.game.player.boon.rule;

import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import io.github.tmldude.blocksouls.common.event.PlayerRunEvents;
import io.github.tmldude.blocksouls.common.player.PlayerStatus;
import io.github.tmldude.blocksouls.common.player.PlayerTraits;
import io.github.tmldude.blocksouls.common.player.WeaponHooks;
import io.github.tmldude.blocksouls.game.BlockSoulsGame;
import io.github.tmldude.blocksouls.game.player.boon.BoonEffect;
import io.github.tmldude.blocksouls.game.player.boon.BuildConfig;
import io.github.tmldude.blocksouls.game.player.boon.PlayerBoons;

/** Co-op rules: boons whose effect lands on a teammate. */
final class CrossPlayer {
	static final Identifier ALLY_RELOAD = BoonRules.id("ally_reload");
	static final Identifier REVIVE_IFRAMES = BoonRules.id("revive_iframes");
	static final Identifier SWORN_PARTNER = BoonRules.id("sworn_partner");

	/** The sworn partner, saved with the player (both directions are set). */
	private static final AttachmentType<UUID> PARTNER = AttachmentRegistry.create(
			Identifier.fromNamespaceAndPath(BlockSoulsGame.MOD_ID, "sworn_partner"),
			builder -> builder.persistent(UUIDUtil.CODEC));

	private CrossPlayer() {
	}

	private static BuildConfig.Rules rules() {
		return BoonRules.config().rules;
	}

	static void register() {
		BoonRules.register(ALLY_RELOAD, new AllyReload());
		BoonRules.register(REVIVE_IFRAMES, new ReviveIframes());
		BoonRules.register(SWORN_PARTNER, new SwornPartner());

		// Sneak-use on a teammate while owning the boon swears them in.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer owner) || !(entity instanceof ServerPlayer target) || !owner.isShiftKeyDown()
					|| PlayerStatus.isDowned(owner) || !BoonRules.owns(owner, SWORN_PARTNER)) {
				return InteractionResult.PASS;
			}
			swear(owner, target);
			return InteractionResult.SUCCESS;
		});
		// A partner's build changed: the other's shared traits follow.
		PlayerRunEvents.BOON_GRANTED.register((player, boon) -> refreshPartner(player));
	}

	public static @Nullable UUID partnerId(ServerPlayer player) {
		return ((AttachmentTarget) player).getAttached(PARTNER);
	}

	public static @Nullable ServerPlayer partner(ServerPlayer player) {
		UUID id = partnerId(player);
		return id == null ? null : player.level().getServer().getPlayerList().getPlayer(id);
	}

	/** Pairs two players (replacing any earlier partners) and refreshes both builds. */
	public static void swear(ServerPlayer a, ServerPlayer b) {
		if (a == b) {
			return;
		}
		for (ServerPlayer player : new ServerPlayer[] {a, b}) {
			ServerPlayer old = partner(player);
			if (old != null && old != a && old != b) {
				((AttachmentTarget) old).removeAttached(PARTNER);
				PlayerBoons.refresh(old);
			}
		}
		((AttachmentTarget) a).setAttached(PARTNER, b.getUUID());
		((AttachmentTarget) b).setAttached(PARTNER, a.getUUID());
		a.sendSystemMessage(Component.translatable("message.blocksouls_game.sworn_partner", b.getDisplayName()).withStyle(ChatFormatting.AQUA));
		b.sendSystemMessage(Component.translatable("message.blocksouls_game.sworn_partner", a.getDisplayName()).withStyle(ChatFormatting.AQUA));
		PlayerBoons.refresh(a);
		PlayerBoons.refresh(b);
	}

	private static void refreshPartner(ServerPlayer player) {
		ServerPlayer partner = partner(player);
		if (partner != null) {
			PlayerBoons.refresh(partner);
		}
	}

	/** The level of a trait the player's own boons grant, before any sharing. */
	static int ownTraitLevel(ServerPlayer player, Identifier trait) {
		int total = 0;
		for (PlayerBoons.Entry entry : PlayerBoons.entries(player)) {
			for (BoonEffect effect : entry.boon().effects()) {
				if (effect instanceof BoonEffect.Trait t && Identifier.parse(t.trait()).equals(trait)) {
					total += t.level() * entry.level();
				}
			}
		}
		return total;
	}

	/** Precision kills reload the nearest standing ally (guns installs the hook). */
	static final class AllyReload implements Rule {
		@Override
		public void onHit(ServerPlayer owner, int level, LivingEntity target, float damage, boolean precision, boolean killed) {
			if (precision && killed) {
				ServerPlayer ally = RuleSupport.nearestAlly(owner, rules().allyReload.range);
				if (ally != null) {
					WeaponHooks.reload(ally);
				}
			}
		}
	}

	/** A revive grants reviver and revived full immunity for a moment. */
	static final class ReviveIframes implements Rule {
		@Override
		public void onRevived(ServerPlayer owner, int level, ServerPlayer revived, @Nullable ServerPlayer reviver) {
			if (owner != revived && owner != reviver) {
				return;
			}
			int ticks = rules().reviveIframes.ticks;
			revived.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks, 4, false, false, true));
			if (reviver != null) {
				reviver.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks, 4, false, false, true));
			}
		}
	}

	/** You and your sworn partner both get the higher of your stamina regen levels. */
	static final class SwornPartner implements Rule {
		@Override
		public void adjustTraits(ServerPlayer owner, int level, Map<Identifier, Integer> traits) {
			ServerPlayer partner = partner(owner);
			if (partner == null) {
				return;
			}
			int shared = Math.max(ownTraitLevel(owner, PlayerTraits.STAMINA_REGEN), ownTraitLevel(partner, PlayerTraits.STAMINA_REGEN));
			if (shared > 0) {
				traits.merge(PlayerTraits.STAMINA_REGEN, shared, Math::max);
			}
		}

		@Override
		public void onCleared(ServerPlayer player) {
			refreshPartner(player);
		}
	}

	/** Forces class loading so the attachment registers during init. */
	static void init() {
	}
}
