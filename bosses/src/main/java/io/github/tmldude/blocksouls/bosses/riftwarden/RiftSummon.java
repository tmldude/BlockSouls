package io.github.tmldude.blocksouls.bosses.riftwarden;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.archetype.ArchetypeParams;
import io.github.tmldude.blocksouls.bosses.archetype.SummonShield;
import io.github.tmldude.blocksouls.bosses.archetype.Theme;
import io.github.tmldude.blocksouls.common.mob.WaveMobSpec;
import io.github.tmldude.blocksouls.common.mob.WaveMobs;

/**
 * Phase 2's summon: adds drawn from floor 5's roster (every roster's tags plus {@code more_elites}, at
 * {@code addsFloor}), as many as {@link RiftMath#adds} gives for the players in the fight.
 */
final class RiftSummon extends SummonShield {
	RiftSummon(String name, ArchetypeParams.Summon params, Theme theme, @Nullable String animation) {
		super(name, params, theme, animation);
	}

	@Override
	protected int count(BossEntity boss, int adds) {
		return RiftMath.adds(adds, Math.max(1, boss.participants().size()), Riftwarden.config().maxAdds);
	}

	@Override
	protected List<WaveMobSpec> roster(int tier) {
		Set<String> tags = new HashSet<>();
		for (WaveMobSpec spec : WaveMobs.all()) {
			tags.addAll(spec.tags());
		}
		tags.add(WaveMobs.MORE_ELITES);
		return WaveMobs.eligible(tier, Riftwarden.config().addsFloor, tags);
	}
}
