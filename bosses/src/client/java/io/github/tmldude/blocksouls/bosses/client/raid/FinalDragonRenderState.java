package io.github.tmldude.blocksouls.bosses.client.raid;

import io.github.tmldude.blocksouls.bosses.client.render.BossRenderState;
import io.github.tmldude.blocksouls.bosses.raid.FinalDragon;

/** The final dragon's render state: the framework's plus its flight mode, shield and enrage. */
public class FinalDragonRenderState extends BossRenderState {
	public FinalDragon.Mode mode = FinalDragon.Mode.FLYING;
	public boolean shielded;
	public boolean enraged;
}
