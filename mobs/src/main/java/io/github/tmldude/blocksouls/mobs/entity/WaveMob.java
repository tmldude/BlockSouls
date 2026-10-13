package io.github.tmldude.blocksouls.mobs.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import io.github.tmldude.blocksouls.common.mob.WaveContext;
import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.ai.BreakBlocksGoal;
import io.github.tmldude.blocksouls.mobs.ai.FlankGoal;

/**
 * Base of every wave mob: wave number and block-breaker flag (from {@link WaveContext}), and a synced "action"
 * (a telegraphed move and how far into it the mob is) that renderers animate. Sun burning comes from the {@code minecraft:burn_in_daylight} entity tag, which lists every wave mob except
 * elites.
 */
public abstract class WaveMob extends HostileMob {
	public static final byte ACTION_NONE = 0;
	private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(WaveMob.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_ACTION_TICKS = SynchedEntityData.defineId(WaveMob.class, EntityDataSerializers.INT);

	private int wave = 1;
	private boolean breaksBlocks;

	protected WaveMob(EntityType<? extends WaveMob> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes(MobsConfig.Stats stats) {
		return HostileMob.attributes(stats, 40);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(DATA_ACTION, ACTION_NONE);
		entityData.define(DATA_ACTION_TICKS, 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new BreakBlocksGoal(this));
		goalSelector.addGoal(2, new FlankGoal(this));
		registerAttackGoals();
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));

		targetSelector.addGoal(1, new HurtByTargetGoal(this, WaveMob.class));
		targetSelector.addGoal(2, targetPlayers());
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
	}

	/** Movement/attack goals at priorities 2–6. */
	protected abstract void registerAttackGoals();

	/** Called by the factory before the mob is added. */
	public void applyWaveContext(WaveContext context) {
		wave = Math.max(1, context.wave());
		breaksBlocks = context.breaksBlocks();
	}

	public int wave() {
		return wave;
	}

	public void setWave(int wave) {
		this.wave = Math.max(1, wave);
	}

	/** True when this mob may slowly break {@code #blocksouls:wave_breakable} blocks. */
	public boolean canBreakBlocks() {
		return breaksBlocks || alwaysBreaksBlocks();
	}

	public void setBreaksBlocks(boolean breaksBlocks) {
		this.breaksBlocks = breaksBlocks;
	}

	protected boolean alwaysBreaksBlocks() {
		return false;
	}

	/** Block-breaking speed multiplier (2 = twice as fast). */
	public double breakSpeed() {
		return 1;
	}

	// --- Telegraphed actions, synced for the renderer ---

	public byte action() {
		return entityData.get(DATA_ACTION);
	}

	public int actionTicks() {
		return entityData.get(DATA_ACTION_TICKS);
	}

	public void setAction(byte action, int ticks) {
		entityData.set(DATA_ACTION, action);
		entityData.set(DATA_ACTION_TICKS, ticks);
	}

	public void clearAction() {
		setAction(ACTION_NONE, 0);
	}

	/** Wave mobs never pick up items (keeps the game's drop tables the only source). */
	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	// --- Save data ---

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("BlockSoulsWave", wave);
		output.putBoolean("BlockSoulsBreaksBlocks", breaksBlocks);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		wave = Math.max(1, input.getIntOr("BlockSoulsWave", 1));
		breaksBlocks = input.getBooleanOr("BlockSoulsBreaksBlocks", false);
	}
}
