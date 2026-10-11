package io.github.tmldude.blocksouls.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class MovementConfigTest {
	@Test
	void jsonRoundTrip() {
		MovementConfig server = new MovementConfig();
		server.web.range = 50;
		server.roll.staminaCost = 7;
		MovementConfig parsed = MovementConfig.fromJson(server.toJson());
		assertEquals(50, parsed.web.range);
		assertEquals(7, parsed.roll.staminaCost);
	}

	@Test
	void copyKeepsSectionInstances() {
		MovementConfig local = new MovementConfig();
		MovementConfig.Web web = local.web;
		MovementConfig server = new MovementConfig();
		server.web.range = 12;
		server.jump.tripleJump = false;

		local.copyFrom(server);
		assertSame(web, local.web);
		assertEquals(12, web.range);
		assertEquals(false, local.jump.tripleJump);
	}

	@Test
	void missingKeysKeepDefaultsAndGarbageIsRejected() {
		MovementConfig parsed = MovementConfig.fromJson("{\"web\":{\"range\":5}}");
		assertEquals(5, parsed.web.range);
		assertEquals(new MovementConfig.Web().reelSpeed, parsed.web.reelSpeed);
		assertEquals(new MovementConfig.Roll().durationTicks, parsed.roll.durationTicks);
		assertNull(MovementConfig.fromJson("{not json"));
	}

	@Test
	void nullSectionIsIgnored() {
		MovementConfig local = new MovementConfig();
		MovementConfig.Web web = local.web;
		local.copyFrom(MovementConfig.fromJson("{\"web\":null}"));
		assertSame(web, local.web);
	}
}
