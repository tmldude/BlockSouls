package io.github.tmldude.blocksouls.common.event;

import java.util.Collection;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Boss fight events, fired by the bosses module on the server thread. Boss ids are in {@code CommonIds}. */
public final class BossEvents {
	/** A boss fight began (a player engaged; the arena closed). */
	public static final Event<Boss> ENGAGED = EventFactory.createArrayBacked(Boss.class, callbacks -> (level, bossId, boss, players) -> {
		for (Boss callback : callbacks) {
			callback.onBoss(level, bossId, boss, players);
		}
	});

	/** {@code players} are everyone who took part in the fight. */
	public static final Event<Boss> KILLED = EventFactory.createArrayBacked(Boss.class, callbacks -> (level, bossId, boss, players) -> {
		for (Boss callback : callbacks) {
			callback.onBoss(level, bossId, boss, players);
		}
	});

	private BossEvents() {
	}

	@FunctionalInterface
	public interface Boss {
		void onBoss(ServerLevel level, Identifier bossId, LivingEntity boss, Collection<ServerPlayer> players);
	}
}
