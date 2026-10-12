package io.github.tmldude.blocksouls.game.player.boon;

/**
 * A boon's {@code "icon"} is either an item id ({@code minecraft:feather}, drawn as that item) or a GUI sprite id
 * ({@code <ns>:boon/<name>}, the PNG at {@code assets/<ns>/textures/gui/sprites/boon/<name>.png}). Item ids never
 * start with {@code boon/}, so the path tells them apart.
 */
public final class BoonIcons {
	public static final String SPRITE_PREFIX = "boon/";
	/** The Wildcard card back. */
	public static final String WILDCARD = "blocksouls_game:boon/wildcard";
	/** The "upgrade a random owned boon" card back. */
	public static final String UPGRADE = "blocksouls_game:boon/upgrade";

	private BoonIcons() {
	}

	/** {@code id} is a full {@code <ns>:<path>} id. */
	public static boolean isSprite(String id) {
		return id.substring(id.indexOf(':') + 1).startsWith(SPRITE_PREFIX);
	}

	/** Where a sprite icon's PNG lives in a mod jar or resource pack. */
	public static String texturePath(String id) {
		int colon = id.indexOf(':');
		return "assets/" + id.substring(0, colon) + "/textures/gui/sprites/" + id.substring(colon + 1) + ".png";
	}
}
