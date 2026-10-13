package io.github.tmldude.blocksouls.game.client.hud;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import io.github.tmldude.blocksouls.common.client.ClientNetworking;
import io.github.tmldude.blocksouls.game.player.rune.RunePayloads;
import io.github.tmldude.blocksouls.game.player.rune.RunePayloads.RuneView;

/**
 * A class's rune tree: rows of runes (unlocked, reachable, locked) with their cost, the player's rune-soul balance,
 * and an Unlock button for the selected rune. The server answers every unlock with a fresh tree.
 */
public class RuneTreeScreen extends Screen {
	private static final int ROW_HEIGHT = 20;
	private static final int LIST_WIDTH = 150;
	private static final int PANEL_WIDTH = 170;
	private static final int GAP = 10;
	private static final int FILL = 0xE0101418;
	private static final int FILL_HOVER = 0xF0222A33;
	private static final int FILL_SELECTED = 0xF02A3A4A;
	private static final int BORDER = 0xFF5A6670;
	private static final int UNLOCKED = 0xFFE36BFF;
	private static final int REACHABLE = 0xFFF2C845;
	private static final int LOCKED = 0xFF707880;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFB0B8C0;

	private RunePayloads.Tree tree;
	private int selected;
	private Button unlock;

	public RuneTreeScreen(RunePayloads.Tree tree) {
		super(Component.translatable("screen.blocksouls_game.runes"));
		this.tree = tree;
	}

	/** A fresh tree from the server (after an unlock). */
	public void update(RunePayloads.Tree fresh) {
		if (fresh.playerClass().equals(tree.playerClass())) {
			tree = fresh;
			selected = Math.clamp(selected, 0, Math.max(0, tree.runes().size() - 1));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		unlock = addRenderableWidget(Button.builder(Component.translatable("screen.blocksouls_game.runes.unlock"), button -> buy())
				.bounds(panelX() + PANEL_WIDTH - 70, top() + panelHeight() - 24, 60, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		Component className = Component.translatable("class." + tree.playerClass().getNamespace() + "." + tree.playerClass().getPath());
		graphics.centeredText(font, title.copy().append(": ").append(className), width / 2, top() - 16, REACHABLE);
		List<RuneView> runes = tree.runes();
		for (int i = 0; i < runes.size(); i++) {
			extractRow(graphics, runes.get(i), i, mouseX, mouseY);
		}
		if (!runes.isEmpty()) {
			extractPanel(graphics, runes.get(Math.clamp(selected, 0, runes.size() - 1)));
		}
		graphics.centeredText(font, Component.translatable("screen.blocksouls_game.runes.hint"), width / 2, top() + panelHeight() + 6, TEXT_DIM);
	}

	private int stateColor(RuneView rune) {
		return rune.unlocked() ? UNLOCKED : rune.reachable() ? REACHABLE : LOCKED;
	}

	private void extractRow(GuiGraphicsExtractor graphics, RuneView rune, int index, int mouseX, int mouseY) {
		int x = listX();
		int y = rowY(index);
		boolean hover = inside(mouseX, mouseY, x, y, LIST_WIDTH, ROW_HEIGHT - 2);
		boolean isSelected = index == selected;
		graphics.fill(x, y, x + LIST_WIDTH, y + ROW_HEIGHT - 2, isSelected ? FILL_SELECTED : hover ? FILL_HOVER : FILL);
		graphics.outline(x, y, LIST_WIDTH, ROW_HEIGHT - 2, isSelected ? stateColor(rune) : BORDER);
		// A diamond colored by state: unlocked, reachable, locked.
		int cx = x + 9;
		int cy = y + 8;
		int color = stateColor(rune);
		graphics.fill(cx - 4, cy - 1, cx + 4, cy + 1, color);
		graphics.fill(cx - 1, cy - 4, cx + 1, cy + 4, color);
		graphics.text(font, name(rune.id()), x + 20, y + 5, rune.unlocked() ? UNLOCKED : TEXT);
		String cost = rune.unlocked() ? "" : String.valueOf(rune.cost());
		graphics.text(font, cost, x + LIST_WIDTH - 8 - font.width(cost), y + 5, rune.reachable() && tree.souls() >= rune.cost() ? REACHABLE : TEXT_DIM);
	}

	private void extractPanel(GuiGraphicsExtractor graphics, RuneView rune) {
		int x = panelX();
		int y = top();
		graphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight(), FILL);
		graphics.outline(x, y, PANEL_WIDTH, panelHeight(), stateColor(rune));
		graphics.text(font, name(rune.id()).copy().withStyle(ChatFormatting.BOLD), x + 8, y + 8, TEXT);
		int textY = y + 22;
		for (FormattedCharSequence line : font.split(Component.translatable("rune." + rune.id().getNamespace() + "." + rune.id().getPath() + ".desc"), PANEL_WIDTH - 16)) {
			graphics.text(font, line, x + 8, textY, TEXT_DIM);
			textY += 10;
		}
		textY += 6;
		if (rune.unlocked()) {
			graphics.text(font, Component.translatable("screen.blocksouls_game.runes.unlocked"), x + 8, textY, UNLOCKED);
		} else {
			graphics.text(font, Component.translatable("screen.blocksouls_game.runes.cost", rune.cost()), x + 8, textY, tree.souls() >= rune.cost() ? REACHABLE : LOCKED);
			if (!rune.reachable()) {
				textY += 12;
				String needs = String.join(", ", rune.requires().stream().map(id -> name(id).getString()).toList());
				for (FormattedCharSequence line : font.split(Component.translatable("screen.blocksouls_game.runes.locked", needs), PANEL_WIDTH - 16)) {
					graphics.text(font, line, x + 8, textY, LOCKED);
					textY += 10;
				}
			}
		}
		if (!rune.capabilities().isEmpty()) {
			textY += 12;
			graphics.text(font, Component.literal(String.join(", ", rune.capabilities().stream().map(Identifier::getPath).toList())), x + 8, textY, REACHABLE);
		}
		graphics.text(font, Component.translatable("screen.blocksouls_game.runes.souls", tree.souls()), x + 8, y + panelHeight() - 18, UNLOCKED);
		unlock.active = !rune.unlocked() && rune.reachable() && tree.souls() >= rune.cost();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			for (int i = 0; i < tree.runes().size(); i++) {
				if (inside((int) event.x(), (int) event.y(), listX(), rowY(i), LIST_WIDTH, ROW_HEIGHT - 2)) {
					if (selected == i && doubleClick) {
						buy();
					}
					selected = i;
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN) {
			buy();
			return true;
		}
		if (event.key() == InputConstants.KEY_DOWN && selected < tree.runes().size() - 1) {
			selected++;
			return true;
		}
		if (event.key() == InputConstants.KEY_UP && selected > 0) {
			selected--;
			return true;
		}
		return super.keyPressed(event);
	}

	private void buy() {
		if (tree.runes().isEmpty()) {
			return;
		}
		RuneView rune = tree.runes().get(Math.clamp(selected, 0, tree.runes().size() - 1));
		if (!rune.unlocked() && rune.reachable() && tree.souls() >= rune.cost()) {
			ClientNetworking.sendToServer(new RunePayloads.Unlock(rune.id()));
		}
	}

	private static Component name(Identifier id) {
		return Component.translatable("rune." + id.getNamespace() + "." + id.getPath());
	}

	private int top() {
		return Math.max(24, (height - panelHeight()) / 2);
	}

	private int panelHeight() {
		return Math.max(130, tree.runes().size() * ROW_HEIGHT);
	}

	private int listX() {
		return (width - (LIST_WIDTH + GAP + PANEL_WIDTH)) / 2;
	}

	private int panelX() {
		return listX() + LIST_WIDTH + GAP;
	}

	private int rowY(int index) {
		return top() + index * ROW_HEIGHT;
	}

	private static boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}
}
