package io.github.tmldude.blocksouls.world.floor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import io.github.tmldude.blocksouls.common.floor.FloorSlotKind;
import io.github.tmldude.blocksouls.common.party.PartyScaling;

/**
 * Turns a template into one run's floor plan (no Minecraft classes, so it's unit-tested): picks the template, scales the
 * island for the party, rotates and mirrors the slots, fills empty slot tags, scatters nodes and crates, rings the camp
 * with siege spawn points and adds the floor's escalation tags.
 */
public final class FloorPlanner {
	private FloorPlanner() {
	}

	/**
	 * A planned slot; {@code x}/{@code z} are relative to the camp. {@code rise} &gt; 0 puts the slot on a floating shard
	 * whose top block is {@code rise} above the ground level (Skyrift's elevated siege points); 0 is the island ground.
	 */
	public record PlannedSlot(FloorSlotKind kind, String tag, int x, int z, int radius, int rise) {
		public PlannedSlot(FloorSlotKind kind, String tag, int x, int z, int radius) {
			this(kind, tag, x, z, radius, 0);
		}
	}

	/** A floating shard (Skyrift): centre relative to the camp, top block {@code rise} above the ground level. */
	public record Shard(int x, int z, int rise, int size) {
	}

	public record FloorPlan(String template, int radius, int groundY, int rotation, boolean mirrored, Set<String> tags,
			List<PlannedSlot> slots, List<Shard> shards) {
		public FloorPlan(String template, int radius, int groundY, int rotation, boolean mirrored, Set<String> tags, List<PlannedSlot> slots) {
			this(template, radius, groundY, rotation, mirrored, tags, slots, List.of());
		}

		public List<PlannedSlot> slots(FloorSlotKind kind) {
			return slots.stream().filter(slot -> slot.kind() == kind).toList();
		}
	}

	/** Picks a template from {@code pool} that allows {@code floorNumber}, by weight. Null when none does. */
	public static FloorTemplate pick(Collection<FloorTemplate> templates, String pool, int floorNumber, long seed) {
		List<FloorTemplate> fits = templates.stream().filter(t -> t.pool.equals(pool) && t.allows(floorNumber))
				.sorted((a, b) -> a.id.compareTo(b.id)).toList();
		int total = fits.stream().mapToInt(t -> Math.max(0, t.weight)).sum();
		if (total <= 0) {
			return null;
		}
		int roll = new Random(seed * 31 + floorNumber).nextInt(total);
		for (FloorTemplate template : fits) {
			roll -= Math.max(0, template.weight);
			if (roll < 0) {
				return template;
			}
		}
		return fits.getLast();
	}

	/** The escalation tags for a floor: every config entry whose floor number is at most {@code floorNumber}. */
	public static Set<String> escalationTags(FloorsConfig config, int floorNumber) {
		Set<String> tags = new LinkedHashSet<>();
		tags.add("floor_" + floorNumber);
		for (Map.Entry<String, List<String>> entry : config.escalationTags.entrySet()) {
			try {
				if (Integer.parseInt(entry.getKey().trim()) <= floorNumber) {
					tags.addAll(entry.getValue());
				}
			} catch (NumberFormatException ignored) {
				// a hand-edited key that isn't a number never applies
			}
		}
		return tags;
	}

	public static FloorPlan plan(FloorTemplate template, FloorsConfig config, int floorNumber, int players, long seed) {
		if (template.isBox()) {
			return BoxPlanner.plan(template, config, floorNumber, players, seed); // floor 4, The Vault
		}
		Random random = new Random(seed);
		double scale = PartyScaling.floorSize(Math.max(1, players));
		int radius = (int) Math.round(template.radius * scale);
		int rotation = random.nextInt(4);
		boolean mirrored = random.nextBoolean();
		LavaChannels.Plan channels = template.channels == null ? null : LavaChannels.plan(template.channels, radius, rotation, mirrored);

		List<PlannedSlot> slots = new ArrayList<>();
		List<FloorTemplate.Slot> nodeSlots = new ArrayList<>();
		for (FloorTemplate.Slot slot : template.slots) {
			FloorSlotKind kind = slot.kindEnum();
			if (kind == FloorSlotKind.NODE) {
				nodeSlots.add(slot);
				continue;
			}
			if (kind == FloorSlotKind.SIEGE_RING) {
				continue; // the ring is always generated
			}
			int[] at = transform(slot.x, slot.z, scale, rotation, mirrored);
			String tag = slot.tag;
			if (tag.isEmpty() && kind == FloorSlotKind.STRUCTURE && !config.structures.isEmpty()) {
				tag = config.structures.get(random.nextInt(config.structures.size()));
			} else if (tag.isEmpty() && kind == FloorSlotKind.SECRET && !config.secrets.isEmpty()) {
				tag = config.secrets.get(random.nextInt(config.secrets.size()));
			}
			slots.add(new PlannedSlot(kind, tag, at[0], at[1], slot.radius));
		}
		// Camp first, then the exit portal, then the rest in template order.
		slots.sort((a, b) -> Integer.compare(order(a.kind()), order(b.kind())));

		int nodeCount = Math.max(1, (int) Math.round(template.nodes * PartyScaling.floorNodes(Math.max(1, players))
				* config.nodeFactor(floorNumber)));
		List<PlannedSlot> nodes = new ArrayList<>();
		List<FloorTemplate.Slot> shuffled = new ArrayList<>(nodeSlots);
		java.util.Collections.shuffle(shuffled, random);
		for (FloorTemplate.Slot slot : shuffled) {
			if (nodes.size() >= nodeCount) {
				break;
			}
			int[] at = transform(slot.x, slot.z, scale, rotation, mirrored);
			String tag = slot.tag.isEmpty() ? weighted(template.nodeMix, random) : slot.tag;
			nodes.add(new PlannedSlot(FloorSlotKind.NODE, tag, at[0], at[1], 0));
		}
		scatter(nodes, nodeCount, FloorSlotKind.NODE, slots, radius, config, random, () -> weighted(template.nodeMix, random), channels);
		slots.addAll(nodes);
		// v2-floor5-blight: the floor's rare nodes and herbs, scattered after the common nodes (no draws when empty).
		slots.addAll(extras(template.rareNodes, PartyScaling.floorNodes(Math.max(1, players)) * config.nodeFactor(floorNumber),
				slots, radius, config, random));
		slots.addAll(extras(template.herbs, PartyScaling.floorSize(Math.max(1, players)), slots, radius, config, random));

		int crateCount = (int) Math.round(template.crates * PartyScaling.floorCrates(Math.max(1, players)));
		List<PlannedSlot> crates = new ArrayList<>();
		scatter(crates, crateCount, FloorSlotKind.CRATE, slots, radius, config, random, () -> "", channels);
		slots.addAll(crates);

		int points = Math.max(0, config.siegeRingPoints);
		double ring = radius * config.siegeRingFraction;
		double offset = random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < points; i++) {
			double angle = offset + i * Math.PI * 2 / points;
			// Infernal: a ring point that lands in (or at the lip of) a lava channel slides along the ring until clear.
			for (int nudge = 0; channels != null && nudge < 12
					&& channels.distance(Math.round(Math.cos(angle) * ring), Math.round(Math.sin(angle) * ring)) <= config.slotClearance; nudge++) {
				angle += Math.PI / 36;
			}
			slots.add(new PlannedSlot(FloorSlotKind.SIEGE_RING, Integer.toString(i), (int) Math.round(Math.cos(angle) * ring),
					(int) Math.round(Math.sin(angle) * ring), 0));
		}

		// Skyrift: shards and elevated siege points use their own random so templates without shards plan as before.
		List<Shard> shards = List.of();
		if (template.shards.count > 0) {
			Random sky = new Random(seed ^ 0x5348415244L);
			shards = shards(template, slots, radius, players, sky);
			elevate(slots, shards, radius, template.elevatedSiegePoints);
		}

		Set<String> tags = new LinkedHashSet<>(template.tags);
		tags.addAll(escalationTags(config, floorNumber));
		return new FloorPlan(template.id, radius, template.groundY, rotation, mirrored, tags, List.copyOf(slots), shards);
	}

	/** The shard count for a party: the template's count scaled like the island's radius, capped at {@code maxCount}. */
	public static int shardCount(FloorTemplate template, int players) {
		FloorTemplate.Shards spec = template.shards;
		return Math.max(0, Math.min(spec.maxCount, (int) Math.round(spec.count * PartyScaling.floorSize(Math.max(1, players)))));
	}

	/**
	 * Places floating shards around the camp: clear of the camp, portal and structure footprints and of each other, and
	 * those past the rim within {@code maxGap} of the island's edge so the movement kit can reach them.
	 */
	static List<Shard> shards(FloorTemplate template, List<PlannedSlot> slots, int radius, int players, Random random) {
		FloorTemplate.Shards spec = template.shards;
		int count = shardCount(template, players);
		List<PlannedSlot> keepClear = slots.stream().filter(slot -> slot.kind() == FloorSlotKind.CAMP
				|| slot.kind() == FloorSlotKind.EXIT_PORTAL || slot.kind() == FloorSlotKind.STRUCTURE).toList();
		List<Shard> out = new ArrayList<>();
		double offset = random.nextDouble() * Math.PI * 2;
		for (int attempt = 0; out.size() < count && attempt < count * 80; attempt++) {
			// Spread the shards around the compass: each one starts from its own sector, jittered.
			double angle = offset + (out.size() + random.nextDouble() * 0.8) * Math.PI * 2 / count;
			int size = spec.minSize + random.nextInt(spec.maxSize - spec.minSize + 1);
			double reach = (spec.minReach + random.nextDouble() * (spec.maxReach - spec.minReach)) * radius;
			int rise = spec.minRise + random.nextInt(spec.maxRise - spec.minRise + 1);
			int x = (int) Math.round(Math.cos(angle) * reach);
			int z = (int) Math.round(Math.sin(angle) * reach);
			if (reach - size - radius > spec.maxGap || !clear(x, z, keepClear, size + 3)) {
				continue;
			}
			boolean apart = true;
			for (Shard other : out) {
				long dx = x - other.x();
				long dz = z - other.z();
				long gap = size + other.size() + 3;
				apart &= dx * dx + dz * dz >= gap * gap;
			}
			if (apart) {
				out.add(new Shard(x, z, rise, size));
			}
		}
		return List.copyOf(out);
	}

	/** Whether a shard lies wholly inside the island's radius (with a two-block margin). */
	public static boolean overIsland(Shard shard, int radius) {
		double reach = Math.sqrt((double) shard.x() * shard.x() + (double) shard.z() * shard.z()) + shard.size();
		return reach <= radius - 2;
	}

	/**
	 * Moves {@code count} siege ring points (spread evenly round the ring) onto the shard nearest each, one per shard. Only
	 * shards wholly over the island qualify, so what spawns there drops onto the island, not into the void.
	 */
	static void elevate(List<PlannedSlot> slots, List<Shard> allShards, int radius, int count) {
		List<Shard> shards = allShards.stream().filter(shard -> overIsland(shard, radius)).toList();
		List<Integer> ring = new ArrayList<>();
		for (int i = 0; i < slots.size(); i++) {
			if (slots.get(i).kind() == FloorSlotKind.SIEGE_RING) {
				ring.add(i);
			}
		}
		int moves = Math.min(Math.max(0, count), Math.min(ring.size(), shards.size()));
		Set<Shard> used = new java.util.HashSet<>();
		for (int m = 0; m < moves; m++) {
			int index = ring.get(m * ring.size() / moves);
			PlannedSlot point = slots.get(index);
			Shard best = null;
			long bestDistance = Long.MAX_VALUE;
			for (Shard shard : shards) {
				long dx = shard.x() - point.x();
				long dz = shard.z() - point.z();
				if (!used.contains(shard) && dx * dx + dz * dz < bestDistance) {
					bestDistance = dx * dx + dz * dz;
					best = shard;
				}
			}
			used.add(best);
			slots.set(index, new PlannedSlot(FloorSlotKind.SIEGE_RING, point.tag(), best.x(), best.z(), 0, best.rise()));
		}
	}

	/**
	 * A floor's lava channels (null when its template has none), rebuilt from the template, the placed radius and the
	 * seed: the same rotation and mirroring {@link #plan} drew first.
	 */
	public static LavaChannels.Plan channels(FloorTemplate template, int radius, long seed) {
		if (template.channels == null) {
			return null;
		}
		Random random = new Random(seed);
		int rotation = random.nextInt(4);
		return LavaChannels.plan(template.channels, radius, rotation, random.nextBoolean());
	}

	private static int order(FloorSlotKind kind) {
		return switch (kind) {
			case CAMP -> 0;
			case EXIT_PORTAL -> 1;
			default -> 2;
		};
	}

	/** Scale, mirror (x → −x), then rotate by quarter turns. */
	static int[] transform(int x, int z, double scale, int rotation, boolean mirrored) {
		int sx = (int) Math.round(x * scale);
		int sz = (int) Math.round(z * scale);
		if (mirrored) {
			sx = -sx;
		}
		return switch (rotation & 3) {
			case 1 -> new int[] {-sz, sx};
			case 2 -> new int[] {-sx, -sz};
			case 3 -> new int[] {sz, -sx};
			default -> new int[] {sx, sz};
		};
	}

	/** Scatters {@code round(solo × factor)} (at least 1) node slots per block id in {@code counts}. */
	private static List<PlannedSlot> extras(Map<String, Integer> counts, double factor, List<PlannedSlot> placed, int radius,
			FloorsConfig config, Random random) {
		List<PlannedSlot> out = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			if (entry.getValue() == null || entry.getValue() <= 0) {
				continue;
			}
			int count = out.size() + Math.max(1, (int) Math.round(entry.getValue() * factor));
			scatter(out, count, FloorSlotKind.NODE, placed, radius, config, random, entry::getKey);
		}
		return out;
	}

	/** Adds random points inside 85% of the radius until {@code out} holds {@code count}, keeping clear of other slots. */
	private static void scatter(List<PlannedSlot> out, int count, FloorSlotKind kind, List<PlannedSlot> placed, int radius,
			FloorsConfig config, Random random, java.util.function.Supplier<String> tag, LavaChannels.Plan channels) {
		double reach = radius * 0.85;
		for (int attempt = 0; out.size() < count && attempt < count * 60; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Math.sqrt(random.nextDouble()) * reach;
			int x = (int) Math.round(Math.cos(angle) * distance);
			int z = (int) Math.round(Math.sin(angle) * distance);
			if (clear(x, z, placed, config.slotClearance) && clear(x, z, out, config.nodeSpacing)
					&& (channels == null || channels.distance(x, z) > config.slotClearance)) {
				out.add(new PlannedSlot(kind, tag.get(), x, z, 0));
			}
		}
	}

	private static boolean clear(int x, int z, List<PlannedSlot> slots, int clearance) {
		for (PlannedSlot slot : slots) {
			long dx = x - slot.x();
			long dz = z - slot.z();
			long reach = slot.radius() + clearance;
			if (dx * dx + dz * dz < reach * reach) {
				return false;
			}
		}
		return true;
	}

	static String weighted(Map<String, Integer> weights, Random random) {
		int total = weights.values().stream().mapToInt(w -> Math.max(0, w)).sum();
		if (total <= 0) {
			return weights.keySet().iterator().next();
		}
		int roll = random.nextInt(total);
		for (Map.Entry<String, Integer> entry : weights.entrySet()) {
			roll -= Math.max(0, entry.getValue());
			if (roll < 0) {
				return entry.getKey();
			}
		}
		return weights.keySet().iterator().next();
	}
}
