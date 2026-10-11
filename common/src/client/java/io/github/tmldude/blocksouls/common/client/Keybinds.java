package io.github.tmldude.blocksouls.common.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/**
 * Registers key mappings under a shared "BlockSouls" controls category.
 * Translation keys are {@code key.blocksouls.<name>}.
 */
public final class Keybinds {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("blocksouls", "controls"));

	private Keybinds() {
	}

	/** @param scancode an {@code SDLScancode.SDL_SCANCODE_*} constant */
	public static KeyMapping keyboard(String name, int scancode) {
		return register(name, InputConstants.Type.KEYBOARD, scancode);
	}

	public static KeyMapping mouse(String name, int button) {
		return register(name, InputConstants.Type.MOUSE, button);
	}

	private static KeyMapping register(String name, InputConstants.Type type, int code) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.blocksouls." + name, type, code, CATEGORY));
	}
}
