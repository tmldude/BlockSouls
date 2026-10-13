package io.github.tmldude.blocksouls.mobs.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.tmldude.blocksouls.mobs.MobsConfig;
import io.github.tmldude.blocksouls.mobs.logic.AttackPicker.Attack;
import io.github.tmldude.blocksouls.mobs.logic.AttackPicker.Situation;

class AttackPickerTest {
	private final MobsConfig.AttackPicking w = new MobsConfig().attackPicking;
	private final Attack leap = new Attack(6, 16, false, true, true, 0);
	private final Attack toss = new Attack(5, 24, true, false, false, 0);
	private final List<Attack> attacks = List.of(leap, toss);

	@Test
	void outOfRangeOrOnCooldownNeverScores() {
		assertEquals(Double.NEGATIVE_INFINITY, AttackPicker.score(leap, new Situation(3, 0, false, false), true, false, w));
		assertEquals(Double.NEGATIVE_INFINITY, AttackPicker.score(leap, new Situation(10, 0, false, false), false, false, w));
		assertEquals(-1, AttackPicker.pick(attacks, new Situation(2, 0, false, false), new boolean[] {true, true}, -1, w));
	}

	@Test
	void airborneTargetPrefersRanged() {
		Situation grounded = new Situation(11, 0, false, false);
		Situation airborne = new Situation(11, 0, false, true);
		assertEquals(0, AttackPicker.pick(attacks, grounded, new boolean[] {true, true}, -1, w));
		assertEquals(1, AttackPicker.pick(attacks, airborne, new boolean[] {true, true}, -1, w));
	}

	@Test
	void justRolledFavoursPunish() {
		// At 14 blocks the toss suits the range better, until the target rolls: then the leap punishes the recovery.
		assertEquals(1, AttackPicker.pick(attacks, new Situation(14, 0, false, false), new boolean[] {true, true}, -1, w));
		assertEquals(0, AttackPicker.pick(attacks, new Situation(14, 0, true, false), new boolean[] {true, true}, -1, w));
	}

	@Test
	void repeatPenaltyRotatesTheMoveset() {
		Situation s = new Situation(11, 0, false, false);
		assertEquals(0, AttackPicker.pick(attacks, s, new boolean[] {true, true}, -1, w));
		assertEquals(1, AttackPicker.pick(attacks, s, new boolean[] {true, true}, 0, w));
	}

	@Test
	void aSingleAttackIsNotPenalisedForRepeating() {
		// A Brute hugging its target: point-blank (range edge), facing it, slammed last time.
		Attack slam = new Attack(0, 3.5, false, true, true, 0);
		assertEquals(0, AttackPicker.pick(List.of(slam), new Situation(0.5, 0, false, false), new boolean[] {true}, 0, w));
	}

	@Test
	void targetBehindScoresLower() {
		double front = AttackPicker.score(leap, new Situation(11, 0, false, false), true, false, w);
		double back = AttackPicker.score(leap, new Situation(11, 180, false, false), true, false, w);
		assertTrue(back < front);
	}

	@Test
	void bonusCanCarryAnAttackOverTheThreshold() {
		Attack scream = new Attack(0, 24, true, false, false, 0);
		Situation edge = new Situation(23.9, 180, false, false);
		assertEquals(-1, AttackPicker.pick(List.of(scream), edge, new boolean[] {true}, 0, w));
		assertEquals(0, AttackPicker.pick(List.of(scream.withBonus(1)), edge, new boolean[] {true}, 0, w));
	}
}
