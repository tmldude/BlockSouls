package io.github.tmldude.blocksouls.game.player.boon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** Build weighting and keystone rules of relic offers. */
class OfferRulesTest {
	private static final List<BoonEffect> EFFECT = List.of(new BoonEffect.Effect("minecraft:speed", 0));
	private static final OfferRules.Weighting WEIGHTING = new OfferRules.Weighting(0.5, 3.0, 1, true);

	private static Boon boon(String path, BoonTier tier, boolean keystone, String... tags) {
		return new Boon("t:" + path, Boon.Kind.BOON, tier, "minecraft:sugar", 1, keystone, List.of(tags), List.of(), EFFECT);
	}

	private static final Boon FIRE_A = boon("fire_a", BoonTier.COMMON, false, "blocksouls:fire");
	private static final Boon FIRE_B = boon("fire_b", BoonTier.COMMON, false, "blocksouls:fire", "blocksouls:on_kill");
	private static final Boon STORM = boon("storm", BoonTier.COMMON, false, "blocksouls:storm");
	private static final Boon PLAIN = boon("plain", BoonTier.COMMON, false);
	private static final Boon KEY_RELOAD_A = boon("key_reload_a", BoonTier.LEGENDARY, true, "blocksouls:on_reload", "blocksouls:explosion");
	private static final Boon KEY_RELOAD_B = boon("key_reload_b", BoonTier.RARE, true, "blocksouls:on_reload", "blocksouls:pull");
	private static final Boon KEY_KILL = boon("key_kill", BoonTier.LEGENDARY, true, "blocksouls:on_kill", "blocksouls:heal");
	private static final List<Boon> ALL = List.of(FIRE_A, FIRE_B, STORM, PLAIN, KEY_RELOAD_A, KEY_RELOAD_B, KEY_KILL);

	@Test
	void weightGrowsWithOwnedTagStacksAndCaps() {
		Map<String, Integer> none = Map.of();
		assertEquals(1.0, OfferRules.weight(FIRE_A, none, WEIGHTING));
		assertEquals(1.0, OfferRules.weight(PLAIN, Map.of("blocksouls:fire", 9), WEIGHTING), "no tags, no bonus");
		assertEquals(2.0, OfferRules.weight(FIRE_A, Map.of("blocksouls:fire", 2), WEIGHTING));
		assertEquals(2.5, OfferRules.weight(FIRE_B, Map.of("blocksouls:fire", 2, "blocksouls:on_kill", 1), WEIGHTING), "stacks across tags add");
		assertEquals(4.0, OfferRules.weight(FIRE_A, Map.of("blocksouls:fire", 100), WEIGHTING), "capped at 1 + max");
		assertEquals(1.0, OfferRules.weight(FIRE_A, Map.of("blocksouls:fire", 100), OfferRules.Weighting.NONE), "weighting off");
	}

	@Test
	void triggerTagsAreTheOnPrefixed() {
		assertTrue(OfferRules.isTrigger("blocksouls:on_kill"));
		assertFalse(OfferRules.isTrigger("blocksouls:fire"));
		assertTrue(OfferRules.sharesTrigger(KEY_RELOAD_A, KEY_RELOAD_B));
		assertFalse(OfferRules.sharesTrigger(KEY_RELOAD_A, KEY_KILL));
		assertTrue(OfferRules.sharesTrigger(FIRE_B, KEY_KILL), "pure tag logic; keystoneAllowed is what ignores non-keystones");
	}

	@Test
	void oneKeystonePerTriggerTag() {
		Map<String, Integer> owned = Map.of("t:key_reload_a", 1);
		Map<String, Integer> tags = Map.of("blocksouls:on_reload", 1, "blocksouls:explosion", 1, "blocksouls:on_kill", 1);
		assertFalse(OfferRules.keystoneAllowed(KEY_RELOAD_B, owned, tags, ALL, WEIGHTING), "same trigger tag as an owned keystone");
		assertTrue(OfferRules.keystoneAllowed(KEY_KILL, owned, tags, ALL, WEIGHTING), "different trigger tag");
		assertTrue(OfferRules.keystoneAllowed(KEY_RELOAD_B, owned, tags, ALL, new OfferRules.Weighting(0.5, 3, 1, false)), "rule switched off");
		assertFalse(CardRoller.canOffer(KEY_RELOAD_B, owned, tags, ALL, WEIGHTING));
		assertTrue(CardRoller.canOffer(FIRE_A, owned, tags, ALL, WEIGHTING), "ordinary boons are never blocked by keystones");
	}

	@Test
	void keystonesNeedInvestmentInOneOfTheirTags() {
		Map<String, Integer> owned = Map.of();
		assertFalse(OfferRules.keystoneAllowed(KEY_KILL, owned, Map.of(), ALL, WEIGHTING), "no on_kill or heal stacks yet");
		assertTrue(OfferRules.keystoneAllowed(KEY_KILL, owned, Map.of("blocksouls:heal", 1), ALL, WEIGHTING));
		assertTrue(OfferRules.keystoneAllowed(KEY_KILL, owned, Map.of(), ALL, new OfferRules.Weighting(0.5, 3, 0, true)), "0 = always");
	}

	@Test
	void weightedRollLeansTowardTheBuild() {
		// Three fire stacks: FIRE_A and FIRE_B weigh 2.5 and 2.5 against STORM's and PLAIN's 1.0, so the first card drawn
		// is a fire boon about 71% of the time, against 50% unweighted.
		Map<String, Integer> tags = Map.of("blocksouls:fire", 3);
		List<Boon> pool = List.of(FIRE_A, FIRE_B, STORM, PLAIN);
		CardRoller.Settings weighted = new CardRoller.Settings(1, 0, true, 0, WEIGHTING);
		CardRoller.Settings flat = new CardRoller.Settings(1, 0, true, 0, OfferRules.Weighting.NONE);
		Random random = new Random(7);
		int fireWeighted = 0;
		int fireFlat = 0;
		for (int i = 0; i < 4000; i++) {
			if (CardRoller.roll(pool, Map.of(), tags, BoonTier.COMMON, weighted, false, random).cards().getFirst().boon().tags().contains("blocksouls:fire")) {
				fireWeighted++;
			}
			if (CardRoller.roll(pool, Map.of(), tags, BoonTier.COMMON, flat, false, random).cards().getFirst().boon().tags().contains("blocksouls:fire")) {
				fireFlat++;
			}
		}
		assertTrue(fireWeighted > 2600 && fireWeighted < 3100, "weighted fire draws: " + fireWeighted);
		assertTrue(fireFlat > 1800 && fireFlat < 2200, "flat fire draws: " + fireFlat);
	}

	@Test
	void upgradeCardReplacesTheFirstCardOnlyWhenSomethingCanLevel() {
		CardRoller.Settings always = new CardRoller.Settings(3, 0, true, 1.0, OfferRules.Weighting.NONE);
		CardRoller.Offer offer = CardRoller.roll(ALL, Map.of(), Map.of(), BoonTier.COMMON, always, true, new Random(1));
		assertTrue(offer.cards().getFirst().upgrade(), "first card is the upgrade");
		assertEquals(3, offer.cards().size());
		assertTrue(offer.cards().stream().filter(CardRoller.Card::isBoon).count() == 2);

		CardRoller.Offer nothing = CardRoller.roll(ALL, Map.of(), Map.of(), BoonTier.COMMON, always, false, new Random(1));
		assertTrue(nothing.cards().stream().noneMatch(CardRoller.Card::upgrade), "nothing to upgrade, no card");
	}

	@Test
	void cursesAndTrinketsAreNeverOffered() {
		Boon curse = new Boon("t:curse", Boon.Kind.CURSE, BoonTier.COMMON, "minecraft:sugar", 1, false, List.of(), List.of(), EFFECT);
		Boon trinket = new Boon("t:trinket", Boon.Kind.TRINKET, BoonTier.COMMON, "minecraft:sugar", 1, false, List.of(), List.of(), EFFECT);
		assertFalse(CardRoller.canOffer(curse, new HashMap<>()));
		assertFalse(CardRoller.canOffer(trinket, new HashMap<>()));
		CardRoller.Offer offer = CardRoller.roll(List.of(curse, trinket, PLAIN), Map.of(), BoonTier.COMMON, 3, 0, true, new Random(3));
		assertEquals(List.of(PLAIN), offer.cards().stream().map(CardRoller.Card::boon).toList());
	}
}
