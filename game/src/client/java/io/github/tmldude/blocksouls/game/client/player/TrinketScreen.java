package io.github.tmldude.blocksouls.game.client.player;

import java.util.List;

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
 * The Trinket Case's pick-1-of-3: click a card to take it. With full slots the pick replaces the oldest trinket
 * (named under the title). Closing without picking keeps the case (the server remembers the cards).
 */
public class TrinketScreen extends Screen {
	private static final int CARD_WIDTH = 100;
	private static final int CARD_HEIGHT = 120;
	private static final int CARD_GAP = 12;
	private static final int CARD_FILL = 0xE0101418;
	private static final int CARD_FILL_HOVER = 0xF0222A33;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFB0B8C0;
	private static final int WARN = 0xFFE0A03B;

	private final BoonPayloads.TrinketOffer offer;
	private boolean sent;

	public TrinketScreen(BoonPayloads.TrinketOffer offer) {
		super(Component.translatable("screen.blocksouls_game.trinket"));
		this.offer = offer;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, cardTop() - 28, BoonStyle.TRINKET_COLOR);
		offer.replaces().ifPresent(replaced -> graphics.centeredText(font,
				Component.translatable("screen.blocksouls_game.trinket.replaces", BoonStyle.name(replaced.id())), width / 2, cardTop() - 16, WARN));
		List<BoonView> cards = offer.cards();
		for (int i = 0; i < cards.size(); i++) {
			extractCard(graphics, cards.get(i), i, cardX(i), cardTop(), mouseX, mouseY);
		}
		graphics.centeredText(font, Component.translatable("screen.blocksouls_game.trinket.close"), width / 2, cardTop() + CARD_HEIGHT + 10, TEXT_DIM);
	}

	private void extractCard(GuiGraphicsExtractor graphics, BoonView trinket, int index, int x, int y, int mouseX, int mouseY) {
		boolean hover = inside(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT);
		int border = BoonStyle.color(trinket);
		graphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, hover ? CARD_FILL_HOVER : CARD_FILL);
		graphics.outline(x, y, CARD_WIDTH, CARD_HEIGHT, border);
		if (hover) {
			graphics.outline(x - 1, y - 1, CARD_WIDTH + 2, CARD_HEIGHT + 2, border);
		}
		graphics.text(font, String.valueOf(index + 1), x + 4, y + 4, TEXT_DIM);

		int center = x + CARD_WIDTH / 2;
		BoonStyle.drawIcon(graphics, trinket.icon(), center - 16, y + 12, 32);
		int textY = wrapped(graphics, BoonStyle.name(trinket.id()), center, y + 52);
		graphics.centeredText(font, BoonStyle.kindName(trinket), center, textY, border);
		wrapped(graphics, BoonStyle.description(trinket.id()), center, textY + 14);
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
			for (int i = 0; i < offer.cards().size(); i++) {
				if (inside(mouseX, mouseY, cardX(i), cardTop(), CARD_WIDTH, CARD_HEIGHT)) {
					choose(i);
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int index = event.key() - com.mojang.blaze3d.platform.InputConstants.KEY_1;
		if (index >= 0 && index < offer.cards().size()) {
			choose(index);
			return true;
		}
		return super.keyPressed(event);
	}

	private void choose(int card) {
		if (!sent) {
			sent = true;
			ClientNetworking.sendToServer(new BoonPayloads.ChooseTrinket(card));
		}
		onClose();
	}

	private int cardX(int index) {
		int count = offer.cards().size();
		int total = count * CARD_WIDTH + (count - 1) * CARD_GAP;
		return (width - total) / 2 + index * (CARD_WIDTH + CARD_GAP);
	}

	private int cardTop() {
		return (height - CARD_HEIGHT) / 2;
	}

	private static boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}
}
