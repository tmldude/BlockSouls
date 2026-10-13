package io.github.tmldude.blocksouls.bosses.command;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.tmldude.blocksouls.bosses.api.Attack;
import io.github.tmldude.blocksouls.bosses.api.BossAffixes;
import io.github.tmldude.blocksouls.bosses.api.BossDefinition;
import io.github.tmldude.blocksouls.bosses.api.BossEntity;
import io.github.tmldude.blocksouls.bosses.api.Bosses;
import io.github.tmldude.blocksouls.bosses.field.FieldBossData;
import io.github.tmldude.blocksouls.bosses.field.FieldBosses;
import io.github.tmldude.blocksouls.common.debug.DebugCommands;
import io.github.tmldude.blocksouls.common.mob.Affix;
import io.github.tmldude.blocksouls.common.mob.Affixes;

/**
 * {@code /bs boss spawn <id>}, {@code phase <n>}, {@code health <percent>}, {@code reset}, {@code field roll [id]},
 * {@code list}, and the v2 debug hooks {@code posture [add <n>|break]}, {@code break <part>}, {@code affix <name|none>},
 * {@code attack <name>}. Commands that act on "the boss" pick the nearest one within 64 blocks.
 */
public final class BossCommands {
	private static final double NEAR = 64;

	private BossCommands() {
	}

	public static void init() {
		DebugCommands.register((root, context) -> root.then(Commands.literal("boss")
				.then(Commands.literal("spawn").then(Commands.argument("id", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Bosses.all().keySet().stream().map(Identifier::getPath), builder))
						.executes(BossCommands::spawn)))
				.then(Commands.literal("phase").then(Commands.argument("n", IntegerArgumentType.integer(1, 9)).executes(ctx -> {
					BossEntity boss = nearest(ctx);
					int n = IntegerArgumentType.getInteger(ctx, "n");
					boss.forcePhase(n - 1);
					ctx.getSource().sendSuccess(() -> Component.literal(name(boss) + " → phase " + (boss.phase() + 1)), true);
					return 1;
				})))
				.then(Commands.literal("health").then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.1, 100)).executes(ctx -> {
					BossEntity boss = nearest(ctx);
					double percent = DoubleArgumentType.getDouble(ctx, "percent");
					boss.setHealth((float) (boss.getMaxHealth() * percent / 100));
					ctx.getSource().sendSuccess(() -> Component.literal(name(boss) + " health " + Math.round(boss.getHealth()) + "/"
							+ Math.round(boss.getMaxHealth())), true);
					return 1;
				})))
				.then(Commands.literal("reset").executes(ctx -> {
					BossEntity boss = nearest(ctx);
					boss.resetFight();
					ctx.getSource().sendSuccess(() -> Component.literal(name(boss) + " reset"), true);
					return 1;
				}))
				.then(Commands.literal("field").then(Commands.literal("roll")
						.executes(ctx -> roll(ctx, null))
						.then(Commands.argument("id", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(FieldBosses.rules().keySet().stream().map(Identifier::getPath), builder))
								.executes(ctx -> roll(ctx, StringArgumentType.getString(ctx, "id"))))))
				.then(Commands.literal("list").executes(BossCommands::list))
				.then(Commands.literal("posture")
						.executes(ctx -> posture(ctx, 0, false))
						.then(Commands.literal("add").then(Commands.argument("n", DoubleArgumentType.doubleArg(0, 10000))
								.executes(ctx -> posture(ctx, DoubleArgumentType.getDouble(ctx, "n"), false))))
						.then(Commands.literal("break").executes(ctx -> posture(ctx, 0, true))))
				.then(Commands.literal("break").then(Commands.argument("part", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(breakableParts(ctx), builder))
						.executes(BossCommands::breakPart)))
				.then(Commands.literal("affix").then(Commands.argument("name", StringArgumentType.word())
						.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
								java.util.stream.Stream.concat(java.util.Arrays.stream(Affix.values()).map(Affix::getSerializedName),
										java.util.stream.Stream.of("none")), builder))
						.executes(BossCommands::affix)))
				.then(Commands.literal("attack").then(Commands.argument("name", StringArgumentType.word())
						.suggests((ctx, builder) -> {
							try {
								return SharedSuggestionProvider.suggest(nearest(ctx).definition().attacks().stream().map(Attack::name), builder);
							} catch (CommandSyntaxException e) {
								return builder.buildFuture();
							}
						})
						.executes(BossCommands::attack)))));
	}

	private static int posture(CommandContext<CommandSourceStack> ctx, double add, boolean breakNow) throws CommandSyntaxException {
		BossEntity boss = nearest(ctx);
		if (breakNow) {
			boss.breakPosture(ctx.getSource().getLevel());
		} else if (add > 0) {
			boss.addPoise(add);
		}
		ctx.getSource().sendSuccess(() -> Component.literal(String.format("%s posture %.0f%% (max poise %.0f, broken %d×%s)", name(boss),
				boss.posture() * 100, boss.maxPoise(), boss.posturesBroken(), boss.isPostureBroken() ? ", open now" : "")), true);
		return 1;
	}

	private static List<String> breakableParts(CommandContext<CommandSourceStack> ctx) {
		try {
			return nearest(ctx).definition().breaks().stream().map(BossDefinition.PartBreak::part).toList();
		} catch (CommandSyntaxException e) {
			return List.of();
		}
	}

	private static int breakPart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		BossEntity boss = nearest(ctx);
		String part = StringArgumentType.getString(ctx, "part");
		int index = boss.definition().breakIndex(part);
		if (index < 0) {
			ctx.getSource().sendFailure(Component.literal(name(boss) + " has no breakable '" + part + "'. Breakable: "
					+ boss.definition().breaks().stream().map(BossDefinition.PartBreak::part).toList()));
			return 0;
		}
		if (boss.isPartBroken(part)) {
			ctx.getSource().sendFailure(Component.literal(part + " is already broken"));
			return 0;
		}
		boss.breakPart(ctx.getSource().getLevel(), index);
		ctx.getSource().sendSuccess(() -> Component.literal(name(boss) + ": " + part + " broken"), true);
		return 1;
	}

	private static int affix(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		BossEntity boss = nearest(ctx);
		String name = StringArgumentType.getString(ctx, "name");
		if (name.equals("none")) {
			Affixes.attach(boss, null);
			ctx.getSource().sendSuccess(() -> Component.literal(name(boss) + ": affix cleared"), true);
			return 1;
		}
		Affix affix = Affix.byName(name);
		if (affix == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown affix '" + name + "'"));
			return 0;
		}
		BossAffixes.give(boss, affix);
		ctx.getSource().sendSuccess(() -> Component.literal("Gave " + name(boss) + " the " + affix.getSerializedName() + " affix"), true);
		return 1;
	}

	private static int attack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		BossEntity boss = nearest(ctx);
		String name = StringArgumentType.getString(ctx, "name");
		Optional<Attack> attack = boss.definition().attacks().stream().filter(a -> a.name().equals(name)).findFirst();
		if (attack.isEmpty()) {
			ctx.getSource().sendFailure(Component.literal(name(boss) + " has no attack '" + name + "'"));
			return 0;
		}
		boss.cancelAttack();
		boss.startAttack(ctx.getSource().getLevel(), attack.get(), ctx.getSource().getPlayer());
		ctx.getSource().sendSuccess(() -> Component.literal(name(boss) + " → " + name), true);
		return 1;
	}

	private static Optional<Identifier> resolve(String name) {
		for (Identifier id : Bosses.all().keySet()) {
			if (id.getPath().equals(name) || id.toString().equals(name)) {
				return Optional.of(id);
			}
		}
		return Optional.empty();
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		String name = StringArgumentType.getString(ctx, "id");
		Optional<Identifier> id = resolve(name);
		if (id.isEmpty()) {
			source.sendFailure(Component.literal("Unknown boss '" + name + "'. Known: " + Bosses.all().keySet()));
			return 0;
		}
		EntityType<? extends BossEntity> type = Bosses.type(id.get()).orElseThrow();
		ServerLevel level = source.getLevel();
		Vec3 look = source.getRotation() == null ? new Vec3(0, 0, 1) : Vec3.directionFromRotation(0, source.getRotation().y);
		Vec3 at = source.getPosition().add(look.scale(8));
		BossEntity boss = type.create(level, EntitySpawnReason.COMMAND);
		if (boss == null) {
			source.sendFailure(Component.literal("Could not create " + id.get()));
			return 0;
		}
		BlockPos pos = BlockPos.containing(at);
		float yaw = source.getRotation() == null ? 0 : source.getRotation().y + 180;
		boss.snapTo(at.x, at.y, at.z, yaw, 0);
		boss.setHome(pos);
		boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, null);
		level.addFreshEntity(boss);
		source.sendSuccess(() -> Component.literal("Spawned " + id.get() + " (not engaged; walk within " + boss.arenaRadius()
				+ " blocks or hit it)"), true);
		return 1;
	}

	private static int roll(CommandContext<CommandSourceStack> ctx, @Nullable String name) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		Identifier only = null;
		if (name != null) {
			only = FieldBosses.rules().keySet().stream().filter(id -> id.getPath().equals(name) || id.toString().equals(name)).findFirst()
					.orElse(null);
			if (only == null) {
				source.sendFailure(Component.literal("No field-boss rule '" + name + "'. Known: " + FieldBosses.rules().keySet()));
				return 0;
			}
		}
		Identifier spawned = FieldBosses.forceRoll(player, only);
		if (spawned == null) {
			source.sendFailure(Component.literal("No field boss found a spot near you (wrong place, killed this run, or already out)"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("Field boss " + spawned + " appeared"), true);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		FieldBossData data = FieldBossData.get(source.getServer());
		StringBuilder out = new StringBuilder("Bosses:");
		for (Identifier id : Bosses.all().keySet()) {
			out.append("\n ").append(id.getPath());
			if (FieldBosses.rules().containsKey(id)) {
				out.append(data.killed(id) ? " [killed this run]" : "").append(data.activeMap().containsKey(id) ? " [out now]" : "");
			}
		}
		for (ServerLevel level : source.getServer().getAllLevels()) {
			for (BossEntity boss : level.getEntitiesOfClass(BossEntity.class, everywhere(level, source), e -> true)) {
				out.append("\n - ").append(name(boss)).append(" in ").append(level.dimension().identifier())
						.append(" at ").append(boss.blockPosition().toShortString())
						.append(String.format(" hp %.0f/%.0f phase %d%s", boss.getHealth(), boss.getMaxHealth(), boss.phase() + 1,
								boss.isEngaged() ? " engaged" : ""));
			}
		}
		source.sendSuccess(() -> Component.literal(out.toString()), false);
		return Bosses.all().size();
	}

	/** Loaded bosses near any player in that level (entity lookups need a box). */
	private static AABB everywhere(ServerLevel level, CommandSourceStack source) {
		Vec3 center = source.getLevel() == level ? source.getPosition() : Vec3.ZERO;
		return new AABB(center, center).inflate(1024);
	}

	private static BossEntity nearest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		Vec3 pos = source.getPosition();
		List<BossEntity> bosses = source.getLevel().getEntitiesOfClass(BossEntity.class, new AABB(pos, pos).inflate(NEAR), BossEntity::isAlive);
		return bosses.stream().min(Comparator.comparingDouble(b -> b.distanceToSqr(pos)))
				.orElseThrow(() -> new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
						Component.literal("No boss within " + (int) NEAR + " blocks")).create());
	}

	private static String name(BossEntity boss) {
		return boss.getDisplayName().getString();
	}
}
