package io.github.tmldude.blocksouls.game.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import io.github.tmldude.blocksouls.game.player.boon.BoonIcons;
import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads.BoonView;
import io.github.tmldude.blocksouls.game.player.boon.BoonTier;

/** How boons look everywhere on the client: tier colors, names, icons. */
public final class BoonStyle {
	private static final int[] TIER_COLORS = {0xFFB8C2CC, 0xFF4FA3FF, 0xFFFFB531};
	private static final int COMBO_COLOR = 0xFFE36BFF;

	private BoonStyle() {
	}

	public static int tierColor(int tier) {
		return TIER_COLORS[BoonTier.byOrdinal(tier).ordinal()];
	}

	/** Combos get their own color so they read as "something special" in the row. */
	public static int color(BoonView boon) {
		return boon.combo() ? COMBO_COLOR : tierColor(boon.tier());
	}

	public static Component tierName(int tier) {
		return Component.translatable("tier.blocksouls_game." + BoonTier.byOrdinal(tier).id());
	}

	public static Component name(Identifier boon) {
		return Component.translatable("boon." + boon.getNamespace() + "." + boon.getPath());
	}

	public static Component description(Identifier boon) {
		return Component.translatable("boon." + boon.getNamespace() + "." + boon.getPath() + ".desc");
	}

	public static final Identifier WILDCARD_ICON = Identifier.parse(BoonIcons.WILDCARD);

	/** Draws a boon icon (a GUI sprite or an item, see {@link BoonIcons}) as a {@code size}x{@code size} square. */
	public static void drawIcon(GuiGraphicsExtractor graphics, Identifier icon, int x, int y, int size) {
		if (BoonIcons.isSprite(icon.toString())) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x, y, size, size);
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(size / 16.0F, size / 16.0F);
		graphics.fakeItem(itemIcon(icon), 0, 0);
		graphics.pose().popMatrix();
	}

	private static ItemStack itemIcon(Identifier icon) {
		Item item = BuiltInRegistries.ITEM.getValue(icon);
		return new ItemStack(item == null || item == Items.AIR ? Items.BARRIER : item);
	}
}
