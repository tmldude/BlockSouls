package io.github.tmldude.blocksouls.game.client.player;

import java.util.List;
import java.util.Optional;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.game.client.hud.BoonStyle;
import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads;
import io.github.tmldude.blocksouls.game.player.boon.BoonPayloads.BoonView;

/**
 * The relic's card pick: up to three cards; click one to take it. A Wildcard opens a list of every boon of the
 * relic's tier. Closing without picking keeps the relic (the server remembers the cards).
 */
public class RelicScreen extends Screen {
	private static final int CARD_WIDTH = 100;
	private static final int CARD_HEIGHT = 130;
	private static final int CARD_GAP = 12;
	private static final int ROW_HEIGHT = 22;
	private static final int ROW_WIDTH = 150;
	private static final int CARD_FILL = 0xE0101418;
	private static final int CARD_FILL_HOVER = 0xF0222A33;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFB0B8C0;
	private static final int SKIP_WIDTH = 120;
	private static final int SKIP_HEIGHT = 16;
	private static final int SKIP_COLOR = 0xFFD8CEB2;

	private final BoonPayloads.Offer offer;
	/** Index of the Wildcard card while its list is open, else -1. */
	private int wildcardCard = -1;
	private boolean sent;

	public RelicScreen(BoonPayloads.Offer offer) {
		super(Component.translatable("screen.blocksouls_game.relic"));
		this.offer = offer;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int titleColor = BoonStyle.tierColor(offer.tier());
		if (wildcardCard >= 0) {
			graphics.centeredText(font, Component.translatable("screen.blocksouls_game.relic.wildcard"), width / 2, listTop() - 20, titleColor);
			List<BoonView> options = offer.wildcardOptions();
			for (int i = 0; i < options.size(); i++) {
				extractRow(graphics, options.get(i), rowX(i), rowY(i), mouseX, mouseY);
			}
			graphics.centeredText(font, Component.translatable("screen.blocksouls_game.relic.back"), width / 2, height - 20, TEXT_DIM);
			return;
		}

		graphics.centeredText(font, title, width / 2, cardTop() - 24, titleColor);
		List<BoonPayloads.Card> cards = offer.cards();
		for (int i = 0; i < cards.size(); i++) {
			extractCard(graphics, cards.get(i), i, cardX(i), cardTop(), mouseX, mouseY);
		}
		extractSkip(graphics, mouseX, mouseY);
	}

	/** Declining every card pays souls: never a dead choice. */
	private void extractSkip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = skipX();
		int y = skipY();
		boolean hover = inside(mouseX, mouseY, x, y, SKIP_WIDTH, SKIP_HEIGHT);
		graphics.fill(x, y, x + SKIP_WIDTH, y + SKIP_HEIGHT, hover ? CARD_FILL_HOVER : CARD_FILL);
		graphics.outline(x, y, SKIP_WIDTH, SKIP_HEIGHT, SKIP_COLOR);
		graphics.centeredText(font, Component.translatable("screen.blocksouls_game.relic.skip", offer.skipSouls()), x + SKIP_WIDTH / 2, y + 4, SKIP_COLOR);
	}

	private void extractCard(GuiGraphicsExtractor graphics, BoonPayloads.Card card, int index, int x, int y, int mouseX, int mouseY) {
		boolean hover = inside(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT);
		int border = card.boon().map(BoonStyle::color).orElse(card.upgrade() ? BoonStyle.UPGRADE_COLOR : 0xFFFFFFFF);
		graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, hover ? CARD_FILL_HOVER : CARD_FILL);
		graphics.outline(x, y, CARD_WIDTH, CARD_HEIGHT, border);
		if (hover) {
			graphics.outline(x - 1, y - 1, CARD_WIDTH + 2, CARD_HEIGHT + 2, border);
		}
		graphics.text(font, String.valueOf(index + 1), x + 4, y + 4, TEXT_DIM);

		int center = x + CARD_WIDTH / 2;
		BoonStyle.drawIcon(graphics, card.boon().map(BoonView::icon).orElse(card.upgrade() ? BoonStyle.UPGRADE_ICON : BoonStyle.WILDCARD_ICON),
				center - 16, y + 12, 32);

		int textY = y + 52;
		if (card.upgrade()) {
			graphics.centeredText(font, Component.translatable("screen.blocksouls_game.relic.upgrade_card"), center, textY, TEXT);
			textY += 14;
			wrapped(graphics, Component.translatable("screen.blocksouls_game.relic.upgrade_desc"), center, textY);
			return;
		}
		if (card.wildcard()) {
			graphics.centeredText(font, Component.translatable("screen.blocksouls_game.relic.wildcard_card"), center, textY, TEXT);
			textY += 12;
			graphics.centeredText(font, BoonStyle.tierName(offer.tier()), center, textY, BoonStyle.tierColor(offer.tier()));
			textY += 14;
			wrapped(graphics, Component.translatable("screen.blocksouls_game.relic.wildcard_desc"), center, textY);
			return;
		}

		BoonView boon = card.boon().orElseThrow();
		textY = wrapped(graphics, BoonStyle.name(boon.id()), center, textY);
		graphics.centeredText(font, BoonStyle.kindName(boon), center, textY, BoonStyle.color(boon));
		textY += 12;
		if (boon.maxLevel() > 1) {
			graphics.centeredText(font, Component.translatable("screen.blocksouls_game.relic.level", boon.level(), boon.maxLevel()), center, textY, TEXT_DIM);
			textY += 12;
		}
		wrapped(graphics, BoonStyle.description(boon.id()), center, textY + 2);
	}

	private void extractRow(GuiGraphicsExtractor graphics, BoonView boon, int x, int y, int mouseX, int mouseY) {
		boolean hover = inside(mouseX, mouseY, x, y, ROW_WIDTH, ROW_HEIGHT - 2);
		graphics.fill(x, y, x + ROW_WIDTH, y + ROW_HEIGHT - 2, hover ? CARD_FILL_HOVER : CARD_FILL);
		graphics.outline(x, y, ROW_WIDTH, ROW_HEIGHT - 2, BoonStyle.color(boon));
		BoonStyle.drawIcon(graphics, boon.icon(), x + 2, y + 2, 16);
		graphics.text(font, BoonStyle.name(boon.id()), x + 22, y + 6, TEXT);
		if (hover) {
			graphics.setTooltipForNextFrame(font, BoonStyle.description(boon.id()), mouseX, mouseY);
		}
	}

	/** Centered, word-wrapped text; returns the y below it. */
	private int wrapped(GuiGraphicsExtractor graphics, Component text, int center, int y) {
		for (FormattedCharSequence line : font.split(text, CARD_WIDTH - 10)) {
			graphics.centeredText(font, line, center, y, TEXT);
			y += 10;
		}
		return y + 2;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			int mouseX = (int) event.x();
			int mouseY = (int) event.y();
			if (wildcardCard >= 0) {
				List<BoonView> options = offer.wildcardOptions();
				for (int i = 0; i < options.size(); i++) {
					if (inside(mouseX, mouseY, rowX(i), rowY(i), ROW_WIDTH, ROW_HEIGHT - 2)) {
						choose(wildcardCard, Optional.of(options.get(i).id()));
						return true;
					}
				}
			} else {
				for (int i = 0; i < offer.cards().size(); i++) {
					if (inside(mouseX, mouseY, cardX(i), cardTop(), CARD_WIDTH, CARD_HEIGHT)) {
						pick(i);
						return true;
					}
				}
				if (inside(mouseX, mouseY, skipX(), skipY(), SKIP_WIDTH, SKIP_HEIGHT)) {
					choose(BoonPayloads.Choose.SKIP, Optional.empty());
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (wildcardCard < 0) {
			int index = event.key() - com.mojang.blaze3d.platform.InputConstants.KEY_1;
			if (index >= 0 && index < offer.cards().size()) {
				pick(index);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (wildcardCard >= 0) {
			wildcardCard = -1;
			return;
		}
		super.onClose();
	}

	private void pick(int index) {
		if (offer.cards().get(index).wildcard()) {
			wildcardCard = index;
		} else {
			choose(index, Optional.empty());
		}
	}

	private void choose(int card, Optional<net.minecraft.resources.Identifier> wildcardPick) {
		if (!sent) {
			sent = true;
			ClientNetworking.sendToServer(new BoonPayloads.Choose(card, wildcardPick));
		}
		super.onClose();
	}

	private int cardX(int index) {
		int count = offer.cards().size();
		int total = count * CARD_WIDTH + (count - 1) * CARD_GAP;
		return (width - total) / 2 + index * (CARD_WIDTH + CARD_GAP);
	}

	private int cardTop() {
		return (height - CARD_HEIGHT) / 2 - 8;
	}

	private int skipX() {
		return (width - SKIP_WIDTH) / 2;
	}

	private int skipY() {
		return cardTop() + CARD_HEIGHT + 10;
	}

	private int columns() {
		return width >= 2 * ROW_WIDTH + 30 ? 2 : 1;
	}

	private int listTop() {
		int rows = (offer.wildcardOptions().size() + columns() - 1) / columns();
		return Math.max(30, (height - rows * ROW_HEIGHT) / 2);
	}

	private int rowX(int index) {
		int columns = columns();
		int total = columns * ROW_WIDTH + (columns - 1) * 8;
		return (width - total) / 2 + (index % columns) * (ROW_WIDTH + 8);
	}

	private int rowY(int index) {
		return listTop() + (index / columns()) * ROW_HEIGHT;
	}

	private static boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}
}
