package net.fabricmc.loader.api;

import java.nio.file.Path;

/** Test-only stub of the Fabric Loader API surface our pure code touches. */
public interface FabricLoader {
	static FabricLoader getInstance() {
		throw new UnsupportedOperationException("FabricLoader is not available in unit tests");
	}

	Path getConfigDir();

	boolean isDevelopmentEnvironment();
}
