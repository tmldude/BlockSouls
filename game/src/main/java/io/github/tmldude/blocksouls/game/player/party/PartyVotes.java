package io.github.tmldude.blocksouls.game.player.party;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Party votes: the hook the party relic (majority to open, PLAN2 §3g) uses. A system opens a vote for a set of
 * players with a question and a callback; each player answers with {@code /bs party vote <id> yes|no} (or a
 * screen a later session adds); the vote resolves on a strict majority ({@link PartyVote}), when it can no
 * longer pass, or when it times out (fails). One vote per id at a time.
 */
public final class PartyVotes {
	private record Vote(Set<UUID> voters, Set<UUID> yes, Set<UUID> no, int ticksLeft, Consumer<Boolean> onResolved) {
	}

	private static final Map<String, Vote> VOTES = new HashMap<>();

	private PartyVotes() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PartyVotes::tick);
	}

	/**
	 * Opens a vote. Returns false if one with this id is already open.
	 *
	 * @param voters     the players who may vote (the party; downed players vote too)
	 * @param question   shown to every voter, mechanics only ("Open the party relic?")
	 * @param timeoutTicks after this long without a decision the vote fails
	 * @param onResolved called once with the result
	 */
	public static boolean open(MinecraftServer server, String id, List<ServerPlayer> voters, Component question, int timeoutTicks,
			Consumer<Boolean> onResolved) {
		if (VOTES.containsKey(id) || voters.isEmpty()) {
			return false;
		}
		Set<UUID> ids = new HashSet<>();
		for (ServerPlayer voter : voters) {
			ids.add(voter.getUUID());
			voter.sendSystemMessage(question.copy().withStyle(ChatFormatting.GOLD));
			voter.sendSystemMessage(Component.translatable("message.blocksouls_game.party.vote_how", id, PartyVote.needed(voters.size())).withStyle(ChatFormatting.GRAY));
		}
		VOTES.put(id, new Vote(ids, new HashSet<>(), new HashSet<>(), Math.max(1, timeoutTicks), onResolved));
		return true;
	}

	/** Records a vote; returns false if there is no such open vote or the player isn't a voter. */
	public static boolean vote(ServerPlayer player, String id, boolean yes) {
		Vote vote = VOTES.get(id);
		if (vote == null || !vote.voters().contains(player.getUUID())) {
			return false;
		}
		(yes ? vote.yes() : vote.no()).add(player.getUUID());
		(yes ? vote.no() : vote.yes()).remove(player.getUUID());
		resolve(id, vote, PartyVote.outcome(vote.voters().size(), vote.yes().size(), vote.no().size()));
		return true;
	}

	public static boolean isOpen(String id) {
		return VOTES.containsKey(id);
	}

	public static Set<String> openVotes() {
		return Set.copyOf(VOTES.keySet());
	}

	private static void resolve(String id, Vote vote, PartyVote.Outcome outcome) {
		if (outcome == PartyVote.Outcome.OPEN) {
			return;
		}
		VOTES.remove(id);
		vote.onResolved().accept(outcome == PartyVote.Outcome.PASSED);
	}

	private static void tick(MinecraftServer server) {
		if (VOTES.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Vote> entry : List.copyOf(VOTES.entrySet())) {
			Vote vote = entry.getValue();
			if (vote.ticksLeft() <= 1) {
				resolve(entry.getKey(), vote, PartyVote.Outcome.FAILED);
			} else {
				VOTES.put(entry.getKey(), new Vote(vote.voters(), vote.yes(), vote.no(), vote.ticksLeft() - 1, vote.onResolved()));
			}
		}
	}

	/** Tests: drop every open vote without resolving. */
	public static void clearForTests() {
		VOTES.clear();
	}
}
