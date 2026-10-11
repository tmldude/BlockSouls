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

	/** One card: a boon, or a Wildcard that lets the player pick any of {@code Offer.wildcardOptions}. */
	public record Card(Boon boon, boolean wildcard) {
		public static final Card WILDCARD = new Card(null, true);

		public static Card of(Boon boon) {
			return new Card(boon, false);
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

	/** Whether {@code boon} can be offered to a player who owns {@code owned} ({@code id → level}). */
	public static boolean canOffer(Boon boon, Map<String, Integer> owned) {
		return owned.getOrDefault(boon.id(), 0) < boon.maxLevel() && owned.keySet().containsAll(boon.requires());
	}

	/** Non-combo boons of {@code tier} the player can take, sorted by id for determinism. */
	public static List<Boon> tierOptions(Collection<Boon> all, BoonTier tier, Map<String, Integer> owned) {
		List<Boon> options = new ArrayList<>();
		for (Boon boon : all) {
			if (boon.tier() == tier && !boon.isCombo() && canOffer(boon, owned)) {
				options.add(boon);
			}
		}
		options.sort(Comparator.comparing(Boon::id));
		return options;
	}

	/**
	 * Rolls up to {@code count} distinct cards. Slots come from the relic's tier; combos whose requirements are owned
	 * join the pool (from any tier when {@code combosAnyTier}, else only their own). If the tier runs dry, the
	 * remaining slots are filled from lower tiers, then higher ones. With {@code wildcardChance}, the last card is
	 * replaced by a Wildcard over every non-combo option of the relic's tier (only when there are at least 2).
	 */
	public static Offer roll(Collection<Boon> all, Map<String, Integer> owned, BoonTier tier, int count,
			double wildcardChance, boolean combosAnyTier, RandomGenerator random) {
		List<Boon> pool = new ArrayList<>(tierOptions(all, tier, owned));
		List<Boon> combos = new ArrayList<>();
		for (Boon boon : all) {
			if (boon.isCombo() && (combosAnyTier || boon.tier() == tier) && canOffer(boon, owned)) {
				combos.add(boon);
			}
		}
		combos.sort(Comparator.comparing(Boon::id));
		pool.addAll(combos);

		List<Card> cards = new ArrayList<>();
		drawInto(cards, pool, count, random);

		for (BoonTier fallback : fallbackOrder(tier)) {
			if (cards.size() >= count) {
				break;
			}
			drawInto(cards, new ArrayList<>(tierOptions(all, fallback, owned)), count, random);
		}

		List<Boon> wildcardOptions = tierOptions(all, tier, owned);
		if (!cards.isEmpty() && wildcardOptions.size() >= 2 && random.nextDouble() < wildcardChance) {
			cards.set(cards.size() - 1, Card.WILDCARD);
			return new Offer(tier, cards, wildcardOptions);
		}
		return new Offer(tier, cards, List.of());
	}

	private static void drawInto(List<Card> cards, List<Boon> pool, int count, RandomGenerator random) {
		while (cards.size() < count && !pool.isEmpty()) {
			cards.add(Card.of(pool.remove(random.nextInt(pool.size()))));
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
