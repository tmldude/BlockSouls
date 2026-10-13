package io.github.tmldude.blocksouls.game.run.director;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DirectorConfigTest {
	@Test
	void classicStaysBehaviorNeutralByDefault() {
		assertFalse(new DirectorConfig().enabledInClassic);
	}

	@Test
	void migrateStampsTheVersion() {
		DirectorConfig config = new DirectorConfig();
		assertTrue(DirectorConfig.migrate(config));
		assertEquals(DirectorConfig.CURRENT_DEFAULTS, config.defaultsVersion);
		assertFalse(DirectorConfig.migrate(config));
	}

	@Test
	void logIsBounded() {
		DirectorLog log = new DirectorLog(3);
		for (int i = 0; i < 5; i++) {
			log.add(i, i == 4 ? DirectorLog.Kind.VIOLATION : DirectorLog.Kind.MOOD, "entry " + i);
		}
		assertEquals(3, log.size());
		assertEquals("entry 2", log.tail(10, null).getFirst().text());
		assertEquals(1, log.tail(10, DirectorLog.Kind.VIOLATION).size());
		assertEquals(1, log.violations());
		assertEquals("[   1:05] MOOD        x", new DirectorLog.Entry(65, DirectorLog.Kind.MOOD, "x").line());
	}

	@Test
	void compositionFollowsTheParty() {
		assertEquals(Composition.MIXED, Composition.choose(false, 0));
		assertEquals(Composition.BRUTES, Composition.choose(true, 0.05));
		assertEquals(Composition.FLANKERS, Composition.choose(true, 0.6));
	}
}
