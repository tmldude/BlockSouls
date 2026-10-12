package io.github.tmldude.blocksouls.game.player.boon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Rolls the cards a relic offers. Pure logic: no Minecraft classes. */
public final class CardRoller {
	private CardRoller() {
	}

	/**
	 * One card: a boon, a Wildcard that lets the player pick any of {@code Offer.wildcardOptions}, or an Upgrade that
	 * levels up a random owned boon.
	 */
	public record Card(Boon boon, boolean wildcard, boolean upgrade) {
		public static final Card WILDCARD = new Card(null, true, false);
		public static final Card UPGRADE = new Card(null, false, true);

		public static Card of(Boon boon) {
			return new Card(boon, false, false);
		}

		public boolean isBoon() {
			return boon != null;
		}
	}

	/** {@code wildcardOptions} is empty unless one of the cards is a Wildcard. */
	public record Offer(BoonTier tier, List<Card> cards, List<Boon> wildcardOptions) {
		public Offer {
			cards = List.copyOf(cards);
			wildcardOptions = List.copyOf(wildcardOptions);
		}

		public boolean isEmpty() {
			return cards.isEmpty();
		}
	}

	/** Everything that shapes a roll besides the catalog and what's owned. */
	public record Settings(int count, double wildcardChance, boolean combosAnyTier, double upgradeCardChance, OfferRules.Weighting weighting) {
		public Settings(int count, double wildcardChance, boolean combosAnyTier) {
			this(count, wildcardChance, combosAnyTier, 0, OfferRules.Weighting.NONE);
		}
	}

	/** Whether {@code boon} can be offered to a player who owns {@code owned} ({@code id → level}); keystone rules aside. */
	public static boolean canOffer(Boon boon, Map<String, Integer> owned) {
		return boon.kind() == Boon.Kind.BOON && owned.getOrDefault(boon.id(), 0) < boon.maxLevel()
				&& owned.keySet().containsAll(boon.requires());
	}

	/** {@link #canOffer(Boon, Map)} plus the keystone rules (one per trigger tag, minimum investment). */
	public static boolean canOffer(Boon boon, Map<String, Integer> owned, Map<String, Integer> ownedTags, Collection<Boon> all,
			OfferRules.Weighting weighting) {
		return canOffer(boon, owned) && OfferRules.keystoneAllowed(boon, owned, ownedTags, all, weighting);
	}

	/** Non-combo boons of {@code tier} the player can take, sorted by id for determinism. */
	public static List<Boon> tierOptions(Collection<Boon> all, BoonTier tier, Map<String, Integer> owned) {
		return tierOptions(all, tier, owned, Map.of(), OfferRules.Weighting.NONE);
	}

	public static List<Boon> tierOptions(Collection<Boon> all, BoonTier tier, Map<String, Integer> owned, Map<String, Integer> ownedTags,
			OfferRules.Weighting weighting) {
		List<Boon> options = new ArrayList<>();
		for (Boon boon : all) {
			if (boon.tier() == tier && !boon.isCombo() && canOffer(boon, owned, ownedTags, all, weighting)) {
				options.add(boon);
			}
		}
		options.sort(Comparator.comparing(Boon::id));
		return options;
	}

	/** Rolls without build weighting, upgrade cards or keystone rules (pre-v2 behavior). */
	public static Offer roll(Collection<Boon> all, Map<String, Integer> owned, BoonTier tier, int count,
			double wildcardChance, boolean combosAnyTier, RandomGenerator random) {
		return roll(all, owned, Map.of(), tier, new Settings(count, wildcardChance, combosAnyTier), false, random);
	}

	/**
	 * Rolls up to {@code count} distinct cards. Slots come from the relic's tier; combos whose requirements are owned
	 * join the pool (from any tier when {@code combosAnyTier}, else only their own). Draws are weighted toward the
	 * tags the player owns ({@code ownedTags}: tag → stacks). If the tier runs dry, the remaining slots are filled
	 * from lower tiers, then higher ones. With {@code upgradeCardChance} and something to upgrade, the first card
	 * becomes an Upgrade. With {@code wildcardChance}, the last card is replaced by a Wildcard over every non-combo
	 * option of the relic's tier (only when there are at least 2).
	 */
	public static Offer roll(Collection<Boon> all, Map<String, Integer> owned, Map<String, Integer> ownedTags, BoonTier tier,
			Settings settings, boolean hasUpgradable, RandomGenerator random) {
		OfferRules.Weighting weighting = settings.weighting();
		List<Boon> pool = new ArrayList<>(tierOptions(all, tier, owned, ownedTags, weighting));
		List<Boon> combos = new ArrayList<>();
		for (Boon boon : all) {
			if (boon.isCombo() && (settings.combosAnyTier() || boon.tier() == tier) && canOffer(boon, owned, ownedTags, all, weighting)) {
				combos.add(boon);
			}
		}
		combos.sort(Comparator.comparing(Boon::id));
		pool.addAll(combos);

		List<Card> cards = new ArrayList<>();
		drawInto(cards, pool, settings.count(), ownedTags, weighting, random);

		for (BoonTier fallback : fallbackOrder(tier)) {
			if (cards.size() >= settings.count()) {
				break;
			}
			drawInto(cards, new ArrayList<>(tierOptions(all, fallback, owned, ownedTags, weighting)), settings.count(), ownedTags, weighting, random);
		}

		if (hasUpgradable && cards.size() >= 2 && random.nextDouble() < settings.upgradeCardChance()) {
			cards.set(0, Card.UPGRADE);
		}

		List<Boon> wildcardOptions = tierOptions(all, tier, owned, ownedTags, weighting);
		if (!cards.isEmpty() && wildcardOptions.size() >= 2 && random.nextDouble() < settings.wildcardChance()) {
			cards.set(cards.size() - 1, Card.WILDCARD);
			return new Offer(tier, cards, wildcardOptions);
		}
		return new Offer(tier, cards, List.of());
	}

	/** Draws without replacement, each boon's chance proportional to {@link OfferRules#weight}. */
	private static void drawInto(List<Card> cards, List<Boon> pool, int count, Map<String, Integer> ownedTags,
			OfferRules.Weighting weighting, RandomGenerator random) {
		while (cards.size() < count && !pool.isEmpty()) {
			double total = 0;
			for (Boon boon : pool) {
				total += OfferRules.weight(boon, ownedTags, weighting);
			}
			double pick = random.nextDouble() * total;
			int index = pool.size() - 1;
			for (int i = 0; i < pool.size(); i++) {
				pick -= OfferRules.weight(pool.get(i), ownedTags, weighting);
				if (pick < 0) {
					index = i;
					break;
				}
			}
			cards.add(Card.of(pool.remove(index)));
		}
	}

	/** Lower tiers nearest first, then higher tiers nearest first. */
	static List<BoonTier> fallbackOrder(BoonTier tier) {
		List<BoonTier> order = new ArrayList<>();
		for (int i = tier.ordinal() - 1; i >= 0; i--) {
			order.add(BoonTier.byOrdinal(i));
		}
		for (int i = tier.ordinal() + 1; i < BoonTier.values().length; i++) {
			order.add(BoonTier.byOrdinal(i));
		}
		return order;
	}
}
