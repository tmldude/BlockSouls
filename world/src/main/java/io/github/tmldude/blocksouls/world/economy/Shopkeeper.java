package io.github.tmldude.blocksouls.world.economy;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import io.github.tmldude.blocksouls.common.economy.CampObject;
import io.github.tmldude.blocksouls.common.event.EconomyEvents;

/**
 * The camp shopkeeper: stands still at camp, can't be hurt or targeted, and never despawns. Using it fires
 * {@link EconomyEvents#CAMP_OBJECT_USED} with {@link CampObject#SHOPKEEPER}; the game opens the trade board (or takes
 * the soul wager on sneak-use). Looks like a wandering trader (it reuses that renderer) but has no vanilla trades.
 */
public class Shopkeeper extends WanderingTrader {
	public Shopkeeper(EntityType<? extends Shopkeeper> type, Level level) {
		super(type, level);
		setPermanentlyInvulnerable(true);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
		goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer
				&& !EconomyEvents.CAMP_OBJECT_USED.invoker().onUsed(serverPlayer, CampObject.SHOPKEEPER, blockPosition(), player.isShiftKeyDown())) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.blocksouls_world.economy.nothing"));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void updateTrades(ServerLevel level) {
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public @Nullable Shopkeeper getBreedOffspring(ServerLevel level, net.minecraft.world.entity.AgeableMob partner) {
		return null;
	}
}
