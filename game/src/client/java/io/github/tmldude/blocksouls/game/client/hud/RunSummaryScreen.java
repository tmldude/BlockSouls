package io.github.tmldude.blocksouls.game.client.hud;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

import io.github.tmldude.blocksouls.game.player.summary.SummaryLayout;
import io.github.tmldude.blocksouls.game.player.summary.SummaryPayloads;
import io.github.tmldude.blocksouls.game.player.summary.SummaryPayloads.Card;

/**
 * Run summary v2: the run line on top, then one styled card per player (class crest, boss kills, revives given,
 * time downed, best boon, build tags, rune souls) with a badge on whoever spent the most time downed. Up to 8
 * players fit in two rows of four.
 */
public class RunSummaryScreen extends Screen {
	private static final int CARD_HEIGHT = 132;
	private static final int FILL = 0xE0101418;
	private static final int HEADER = 0xF01C2430;
	private static final int BORDER = 0xFF5A6670;
	private static final int GOLD = 0xFFF2C845;
	private static final int RED = 0xFFE0453B;
	private static final int SOUL = 0xFFE36BFF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFB0B8C0;

	private final SummaryPayloads.Cards cards;

	public RunSummaryScreen(SummaryPayloads.Cards cards) {
		super(Component.translatable("screen.blocksouls_game.summary"));
		this.cards = cards;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("screen.blocksouls_game.summary.done"), button -> onClose())
				.bounds(width / 2 - 40, Math.min(height - 24, cardsTop() + gridHeight() + 8), 80, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int top = Math.max(8, cardsTop() - 30);
		graphics.centeredText(font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, top, RED);
		SummaryPayloads.RunLine run = cards.run();
		Component line = Component.translatable("screen.blocksouls_game.summary.run", run.wave(), run.threat(), run.nights(), run.acts(), run.bosses());
		graphics.centeredText(font, line, width / 2, top + 12, TEXT_DIM);
		List<Card> list = cards.cards();
		for (int i = 0; i < list.size(); i++) {
			SummaryLayout.Slot slot = SummaryLayout.slot(i, list.size(), cards.columns(), width, cardsTop(), CARD_HEIGHT);
			extractCard(graphics, list.get(i), slot);
		}
	}

	private void extractCard(GuiGraphicsExtractor graphics, Card card, SummaryLayout.Slot slot) {
		int x = slot.x();
		int y = slot.y();
		int w = slot.width();
		int accent = card.mostDowned() ? RED : card.bestBoonTier() >= 0 ? BoonStyle.tierColor(card.bestBoonTier()) : BORDER;
		graphics.fill(x, y, x + w, y + slot.height(), FILL);
		graphics.fill(x, y, x + w, y + 24, HEADER);
		graphics.outline(x, y, w, slot.height(), accent);

		int nameX = x + 6;
		if (card.playerClass().isPresent()) {
			Identifier id = card.playerClass().get();
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath(id.getNamespace(), "class/" + id.getPath()), x + 4, y + 4, 16, 16);
			nameX = x + 24;
		}
		graphics.text(font, font.plainSubstrByWidth(card.name(), x + w - 4 - nameX), nameX, y + 4, TEXT);
		Component className = card.playerClass()
				.<Component>map(id -> Component.translatable("class." + id.getNamespace() + "." + id.getPath()))
				.orElse(Component.translatable("screen.blocksouls_game.summary.no_class"));
		graphics.text(font, className, nameX, y + 14, GOLD);

		int textY = y + 30;
		textY = row(graphics, x, w, textY, "bosses", String.valueOf(card.bossKills()), TEXT);
		textY = row(graphics, x, w, textY, "revives", String.valueOf(card.revivesGiven()), TEXT);
		textY = row(graphics, x, w, textY, "downed", formatSeconds(card.downedSeconds()), card.mostDowned() ? RED : TEXT);
		textY = row(graphics, x, w, textY, "souls", "+" + card.runeSouls(), SOUL);

		textY += 4;
		graphics.text(font, Component.translatable("screen.blocksouls_game.summary.best_boon"), x + 6, textY, TEXT_DIM);
		textY += 10;
		Component boon = card.bestBoon().<Component>map(BoonStyle::name).orElse(Component.literal("-"));
		int boonColor = card.bestBoonTier() >= 0 ? BoonStyle.tierColor(card.bestBoonTier()) : TEXT_DIM;
		graphics.text(font, font.substrByWidth(boon, w - 12).getString(), x + 6, textY, boonColor);
		textY += 12;
		graphics.text(font, buildLine(card), x + 6, textY, TEXT_DIM);

		if (card.mostDowned()) {
			Component badge = Component.translatable("screen.blocksouls_game.summary.most_downed");
			int bw = font.width(badge) + 8;
			int bx = x + (w - bw) / 2;
			int by = y + slot.height() - 14;
			graphics.fill(bx, by, bx + bw, by + 12, 0xC0401010);
			graphics.text(font, badge, bx + 4, by + 2, RED);
		}
	}

	private int row(GuiGraphicsExtractor graphics, int x, int w, int y, String key, String value, int color) {
		graphics.text(font, Component.translatable("screen.blocksouls_game.summary." + key), x + 6, y, TEXT_DIM);
		graphics.text(font, value, x + w - 6 - font.width(value), y, color);
		return y + 10;
	}

	private Component buildLine(Card card) {
		if (card.build().isEmpty()) {
			return Component.literal("-");
		}
		MutableComponent line = Component.empty();
		for (int i = 0; i < card.build().size(); i++) {
			if (i > 0) {
				line.append(" · ");
			}
			Identifier tag = card.build().get(i);
			line.append(Component.translatable("synergy." + tag.getNamespace() + "." + tag.getPath()));
		}
		return line;
	}

	static String formatSeconds(int seconds) {
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	private int gridHeight() {
		int rows = SummaryLayout.rows(cards.cards().size(), cards.columns());
		return rows * CARD_HEIGHT + (rows - 1) * SummaryLayout.GAP;
	}

	private int cardsTop() {
		return Math.max(40, (height - gridHeight()) / 2);
	}
}
