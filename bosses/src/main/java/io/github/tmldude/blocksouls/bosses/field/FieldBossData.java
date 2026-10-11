package io.github.tmldude.blocksouls.bosses.field;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import io.github.tmldude.blocksouls.common.CommonIds;

/** Field-boss bookkeeping that must survive restarts: killed this run, cooldowns, today's roll, live bosses. */
public final class FieldBossData extends SavedData {
	/** A field boss that is out in the world. */
	public record Active(UUID entity, Identifier dimension, BlockPos pos) {
		static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("entity").forGetter(Active::entity),
				Identifier.CODEC.fieldOf("dimension").forGetter(Active::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(Active::pos)).apply(i, Active::new));
	}

	private static final Codec<FieldBossData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.listOf().optionalFieldOf("killed", java.util.List.of()).forGetter(d -> java.util.List.copyOf(d.killed)),
			Codec.unboundedMap(Identifier.CODEC, Codec.LONG).optionalFieldOf("cooldowns", Map.of()).forGetter(d -> d.cooldownUntil),
			Codec.LONG.optionalFieldOf("lastDay", -1L).forGetter(d -> d.lastDay),
			Identifier.CODEC.listOf().optionalFieldOf("pending", java.util.List.of()).forGetter(d -> java.util.List.copyOf(d.pendingToday)),
			Codec.unboundedMap(Identifier.CODEC, Active.CODEC).optionalFieldOf("active", Map.of()).forGetter(d -> d.active)
	).apply(i, (killed, cooldowns, lastDay, pending, active) -> {
		FieldBossData data = new FieldBossData();
		data.killed.addAll(killed);
		data.cooldownUntil.putAll(cooldowns);
		data.lastDay = lastDay;
		data.pendingToday.addAll(pending);
		data.active.putAll(active);
		return data;
	}));

	public static final SavedDataType<FieldBossData> TYPE = new SavedDataType<>(CommonIds.id(CommonIds.BOSSES, "field_bosses"),
			FieldBossData::new, CODEC, null);

	final Set<Identifier> killed = new HashSet<>();
	final Map<Identifier, Long> cooldownUntil = new HashMap<>();
	long lastDay = -1;
	final Set<Identifier> pendingToday = new HashSet<>();
	final Map<Identifier, Active> active = new HashMap<>();

	public static FieldBossData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean killed(Identifier boss) {
		return killed.contains(boss);
	}

	public Set<Identifier> killedSet() {
		return Set.copyOf(killed);
	}

	public Map<Identifier, Active> activeMap() {
		return Map.copyOf(active);
	}

	/** New run: everything may appear again. */
	public void resetRun() {
		killed.clear();
		cooldownUntil.clear();
		pendingToday.clear();
		lastDay = -1;
		setDirty();
	}
}
