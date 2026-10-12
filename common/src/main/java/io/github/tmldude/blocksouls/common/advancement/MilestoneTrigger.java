package io.github.tmldude.blocksouls.common.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Criterion {@code blocksouls_common:milestone}: run milestones that have no vanilla criterion. Advancement JSON:
 * <pre>{"trigger": "blocksouls_common:milestone", "conditions": {"milestone": "boss_killed", "id": "blocksouls_bosses:ashwyrm"}}</pre>
 * {@code id} (a boss or boon id) and {@code value} (the night number, as min/max bounds) are optional filters.
 * Fired by {@link Milestones} from the common run events.
 */
public class MilestoneTrigger extends SimpleCriterionTrigger<MilestoneTrigger.TriggerInstance> {
	public enum Milestone implements StringRepresentable {
		/** At dawn, for every player still in the game; value = the night number. */
		NIGHT_SURVIVED("night_survived"),
		/** A boon was granted; id = the boon. */
		BOON("boon"),
		/** A boss died; id = the boss, for everyone credited with the fight. */
		BOSS_KILLED("boss_killed");

		public static final Codec<Milestone> CODEC = StringRepresentable.fromEnum(Milestone::values);

		private final String name;

		Milestone(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	@Override
	public Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	public void trigger(ServerPlayer player, Milestone milestone, Identifier id, int value) {
		trigger(player, instance -> instance.matches(milestone, id, value));
	}

	public record TriggerInstance(Optional<Holder<LootItemCondition>> player, Milestone milestone, Optional<Identifier> id,
			MinMaxBounds.Ints value) implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
				LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
				Milestone.CODEC.fieldOf("milestone").forGetter(TriggerInstance::milestone),
				Identifier.CODEC.optionalFieldOf("id").forGetter(TriggerInstance::id),
				MinMaxBounds.Ints.CODEC.optionalFieldOf("value", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::value))
				.apply(i, TriggerInstance::new));

		public boolean matches(Milestone milestone, Identifier id, int value) {
			return this.milestone == milestone && this.id.map(id::equals).orElse(true) && this.value.matches(value);
		}
	}
}
