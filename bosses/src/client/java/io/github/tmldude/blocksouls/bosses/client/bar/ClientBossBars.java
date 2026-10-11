package io.github.tmldude.blocksouls.bosses.client.bar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import io.github.tmldude.blocksouls.bosses.bar.BossBarPayload;
import io.github.tmldude.blocksouls.bosses.logic.TrailingBar;
import io.github.tmldude.blocksouls.common.client.ClientNetworking;

/** Client copy of the boss bars the server sends us. */
public final class ClientBossBars {
	/** Drop a bar the server stopped refreshing (it resends every second while you can see it). */
	private static final long EXPIRE_MS = 3000;
	private static final double TRAIL_DELAY = 0.6;
	private static final double TRAIL_DRAIN = 0.6;

	private static final Map<Integer, Bar> BARS = new LinkedHashMap<>();
	private static long lastTickMs = Util.getMillis();

	private ClientBossBars() {
	}

	public static final class Bar {
		public final int entityId;
		public Component name = Component.empty();
		public float health;
		public float maxHealth;
		public List<Float> notches = List.of();
		public int phase;
		public boolean engaged;
		public boolean inFight;
		public Optional<Identifier> music = Optional.empty();
		public final TrailingBar trail = new TrailingBar(1, TRAIL_DELAY, TRAIL_DRAIN);
		/** When the phase last changed (for shake + flash), 0 = never. */
		public long phaseChangedMs;
		long lastUpdateMs;

		Bar(int entityId) {
			this.entityId = entityId;
		}

		public float fraction() {
			return maxHealth <= 0 ? 0 : Math.clamp(health / maxHealth, 0f, 1f);
		}
	}

	public static void init() {
		ClientNetworking.receive(BossBarPayload.TYPE, (payload, context) -> accept(payload));
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BARS.clear());
	}

	static void accept(BossBarPayload payload) {
		if (payload.remove()) {
			BARS.remove(payload.entityId());
			return;
		}
		boolean fresh = !BARS.containsKey(payload.entityId());
		Bar bar = BARS.computeIfAbsent(payload.entityId(), Bar::new);
		long now = Util.getMillis();
		if (!fresh && payload.phase() > bar.phase) {
			bar.phaseChangedMs = now;
		}
		bar.name = payload.name();
		bar.health = payload.health();
		bar.maxHealth = payload.maxHealth();
		bar.notches = payload.notches();
		bar.phase = payload.phase();
		bar.engaged = payload.engaged();
		bar.inFight = payload.inFight();
		bar.music = payload.music();
		bar.lastUpdateMs = now;
		if (fresh) {
			bar.trail.set(1);
		}
		bar.trail.set(bar.fraction());
	}

	private static void tick() {
		long now = Util.getMillis();
		BARS.values().removeIf(bar -> now - bar.lastUpdateMs > EXPIRE_MS);
		BossMusic.tick(BARS.values());
	}

	/** Advances the trailing chunks; called every frame by the HUD. */
	static void frame() {
		long now = Util.getMillis();
		double seconds = Math.min(0.25, (now - lastTickMs) / 1000.0);
		lastTickMs = now;
		for (Bar bar : BARS.values()) {
			bar.trail.tick(seconds);
		}
	}

	/** Bars to draw, most important first: bosses you're fighting, then the nearest. At most {@code max}. */
	public static List<Bar> shown(int max) {
		Minecraft minecraft = Minecraft.getInstance();
		List<Bar> bars = new ArrayList<>(BARS.values());
		bars.sort(Comparator.<Bar, Boolean>comparing(bar -> !bar.inFight).thenComparingDouble(bar -> distance(minecraft, bar)));
		return bars.size() > max ? bars.subList(0, max) : bars;
	}

	public static boolean any() {
		return !BARS.isEmpty();
	}

	private static double distance(Minecraft minecraft, Bar bar) {
		if (minecraft.level == null || minecraft.player == null) {
			return 0;
		}
		Entity entity = minecraft.level.getEntity(bar.entityId);
		return entity == null ? Double.MAX_VALUE : entity.distanceToSqr(minecraft.player);
	}
}
