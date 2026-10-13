package io.github.tmldude.blocksouls.common.mob;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import io.github.tmldude.blocksouls.common.CommonIds;

/**
 * Where any module puts an {@link Affix} on a living entity and reads it back. The affix itself is stored here,
 * in a persistent attachment synced to every client tracking the entity (so it survives chunk reloads and the
 * client can tint the model). What an affix does (nameplate, palette, modifier) is the provider's: the mobs
 * module installs the real one; without it {@link #apply} and {@link #roll} do nothing, which is what a server
 * without the mobs module sees. Entity-agnostic: bosses applies the same contract to field bosses.
 *
 * <p>Threading: {@link #apply}, {@link #roll} and {@link #attach} run on the server thread; {@link #of} on either side.
 */
public final class Affixes {
	private static final AttachmentType<Affix> TYPE = AttachmentRegistry.create(
			CommonIds.id("blocksouls_common", "affix"),
			builder -> builder.persistent(Affix.CODEC)
					.syncWith(Affix.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private static final Provider NO_OP = new Provider() {
		@Override
		public boolean apply(LivingEntity entity, Affix affix) {
			return false;
		}

		@Override
		public Optional<Affix> roll(RandomSource random, int floor) {
			return Optional.empty();
		}
	};

	private static Provider provider = NO_OP;

	private Affixes() {
	}

	/**
	 * Gives the entity the affix: stores it, renames it ("Burning Brute") and turns on its modifier. Returns whether
	 * it was applied (false with no provider installed). An entity carries at most one affix; applying another
	 * replaces it.
	 */
	public static boolean apply(LivingEntity entity, Affix affix) {
		return provider.apply(entity, affix);
	}

	/** The affix the entity carries, if any. */
	public static Optional<Affix> of(LivingEntity entity) {
		return Optional.ofNullable(((AttachmentTarget) entity).getAttached(TYPE));
	}

	/**
	 * Picks an affix for an elite on the given floor (1-based act floor; 0 outside Expedition), or empty when it
	 * should carry none (always empty with no provider installed).
	 */
	public static Optional<Affix> roll(RandomSource random, int floor) {
		return provider.roll(random, floor);
	}

	/** Provider-side storage: sets (or, for {@code null}, clears) the stored affix without any behaviour. */
	public static void attach(LivingEntity entity, @Nullable Affix affix) {
		((AttachmentTarget) entity).setAttached(TYPE, affix);
	}

	/** Called once by the mobs module. */
	public static void setProvider(Provider affixProvider) {
		provider = Objects.requireNonNull(affixProvider);
	}

	/** Whether a real provider is installed (the mobs module is loaded). */
	public static boolean hasProvider() {
		return provider != NO_OP;
	}

	/** Called once by common's initializer: registers the attachment before any entity loads. */
	public static void init() {
		Objects.requireNonNull(TYPE);
	}

	public interface Provider {
		/** See {@link Affixes#apply}. Implementations call {@link Affixes#attach} to store it. */
		boolean apply(LivingEntity entity, Affix affix);

		/** See {@link Affixes#roll}. */
		Optional<Affix> roll(RandomSource random, int floor);
	}
}
