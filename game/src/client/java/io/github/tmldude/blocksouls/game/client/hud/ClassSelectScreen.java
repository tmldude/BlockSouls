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
import io.github.tmldude.blocksouls.game.player.classes.ClassPayloads;
import io.github.tmldude.blocksouls.game.player.classes.ClassPayloads.ClassView;

/**
 * The playerClass pick: a list of classes on the left, the selected one's movement kit, passive, drawback and build
 * tags on the right, and a Choose button. Esc closes without picking (the sigil is kept). Mechanics only; names
 * and the three text lines come from the lang file by class id.
 */
public class ClassSelectScreen extends Screen {
	private static final int ROW_HEIGHT = 18;
	private static final int LIST_WIDTH = 120;
	private static final int PANEL_WIDTH = 190;
	private static final int GAP = 12;
	private static final int FILL = 0xE0101418;
	private static final int FILL_HOVER = 0xF0222A33;
	private static final int FILL_SELECTED = 0xF02A3A4A;
	private static final int BORDER = 0xFF5A6670;
	private static final int BORDER_SELECTED = 0xFFF2C845;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFFB0B8C0;
	private static final int GOOD = 0xFF8FE39A;
	private static final int BAD = 0xFFFF8A8A;
	private static final int TAG = 0xFFE36BFF;

	private final ClassPayloads.Offer offer;
	private int selected;
	private boolean sent;
	private Button choose;

	public ClassSelectScreen(ClassPayloads.Offer offer) {
		super(Component.translatable("screen.blocksouls_game.class"));
		this.offer = offer;
		offer.current().ifPresent(current -> {
			for (int i = 0; i < offer.classes().size(); i++) {
				if (offer.classes().get(i).id().equals(current)) {
					selected = i;
				}
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		choose = addRenderableWidget(Button.builder(Component.translatable("screen.blocksouls_game.class.choose"), button -> pick())
				.bounds(panelX() + PANEL_WIDTH - 70, top() + panelHeight() - 24, 60, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, top() - 16, BORDER_SELECTED);

		List<ClassView> classes = offer.classes();
		for (int i = 0; i < classes.size(); i++) {
			extractRow(graphics, classes.get(i), i, mouseX, mouseY);
		}
		if (!classes.isEmpty()) {
			extractPanel(graphics, classes.get(Math.clamp(selected, 0, classes.size() - 1)));
		}
		graphics.centeredText(font, Component.translatable("screen.blocksouls_game.class.hint"), width / 2, top() + panelHeight() + 6, TEXT_DIM);
	}

	private void extractRow(GuiGraphicsExtractor graphics, ClassView playerClass, int index, int mouseX, int mouseY) {
		int x = listX();
		int y = rowY(index);
		boolean hover = inside(mouseX, mouseY, x, y, LIST_WIDTH, ROW_HEIGHT - 2);
		boolean isSelected = index == selected;
		graphics.fill(x, y, x + LIST_WIDTH, y + ROW_HEIGHT - 2, isSelected ? FILL_SELECTED : hover ? FILL_HOVER : FILL);
		graphics.outline(x, y, LIST_WIDTH, ROW_HEIGHT - 2, isSelected ? BORDER_SELECTED : BORDER);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, playerClass.icon(), x + 1, y + 0, 16, 16);
		graphics.text(font, name(playerClass.id()), x + 21, y + 4, TEXT);
		if (offer.current().filter(playerClass.id()::equals).isPresent()) {
			graphics.text(font, Component.translatable("screen.blocksouls_game.class.current"), x + LIST_WIDTH - 40, y + 4, TEXT_DIM);
		}
	}

	private void extractPanel(GuiGraphicsExtractor graphics, ClassView playerClass) {
		int x = panelX();
		int y = top();
		graphics.fill(x, y, x + PANEL_WIDTH, y + panelHeight(), FILL);
		graphics.outline(x, y, PANEL_WIDTH, panelHeight(), BORDER_SELECTED);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, playerClass.icon(), x + 8, y + 8, 32, 32);
		graphics.text(font, name(playerClass.id()).copy().withStyle(ChatFormatting.BOLD), x + 48, y + 10, TEXT);
		String verbs = playerClass.capabilities().isEmpty() ? "-"
				: String.join(", ", playerClass.capabilities().stream().map(Identifier::getPath).map(p -> p.replace('_', ' ')).toList());
		graphics.text(font, Component.literal(verbs), x + 48, y + 24, TEXT_DIM);

		int textY = y + 48;
		textY = section(graphics, "screen.blocksouls_game.class.movement", text(playerClass.id(), "movement"), x + 8, textY, TEXT);
		textY = section(graphics, "screen.blocksouls_game.class.passive", text(playerClass.id(), "passive"), x + 8, textY, GOOD);
		textY = section(graphics, "screen.blocksouls_game.class.drawback", text(playerClass.id(), "drawback"), x + 8, textY, BAD);
		if (!playerClass.synergyTags().isEmpty()) {
			String tags = String.join(", ", playerClass.synergyTags().stream().map(Identifier::getPath).toList());
			section(graphics, "screen.blocksouls_game.class.tags", Component.literal(tags), x + 8, textY, TAG);
		}
	}

	/** A label line then wrapped body text; returns the y below. */
	private int section(GuiGraphicsExtractor graphics, String labelKey, Component body, int x, int y, int color) {
		graphics.text(font, Component.translatable(labelKey), x, y, TEXT_DIM);
		y += 10;
		for (FormattedCharSequence line : font.split(body, PANEL_WIDTH - 16)) {
			graphics.text(font, line, x, y, color);
			y += 10;
		}
		return y + 4;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			int mouseX = (int) event.x();
			int mouseY = (int) event.y();
			for (int i = 0; i < offer.classes().size(); i++) {
				if (inside(mouseX, mouseY, listX(), rowY(i), LIST_WIDTH, ROW_HEIGHT - 2)) {
					if (selected == i && doubleClick) {
						pick();
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
			pick();
			return true;
		}
		if (event.key() == InputConstants.KEY_DOWN && selected < offer.classes().size() - 1) {
			selected++;
			return true;
		}
		if (event.key() == InputConstants.KEY_UP && selected > 0) {
			selected--;
			return true;
		}
		return super.keyPressed(event);
	}

	private void pick() {
		if (sent || offer.classes().isEmpty()) {
			return;
		}
		sent = true;
		ClientNetworking.sendToServer(new ClassPayloads.Choose(offer.classes().get(selected).id()));
		onClose();
	}

	private static Component name(Identifier id) {
		return Component.translatable("class." + id.getNamespace() + "." + id.getPath());
	}

	private static Component text(Identifier id, String part) {
		return Component.translatable("class." + id.getNamespace() + "." + id.getPath() + "." + part);
	}

	private int top() {
		return Math.max(24, (height - panelHeight()) / 2);
	}

	private int panelHeight() {
		return Math.max(150, offer.classes().size() * ROW_HEIGHT);
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
