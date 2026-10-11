# Server image: itzg/minecraft-server (Fabric) with the BlockSouls mods baked in.
# Build from the repo root: docker build -f docker/Dockerfile .

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew build --no-daemon

# Stage unversioned jar names so an image update overwrites the previous jars in /data/mods
# instead of leaving old versions next to new ones. Versions come from gradle.properties.
# Every module except common (which is nested inside each feature jar) ships a jar.
RUN mkdir /out \
	&& for libs in */build/libs; do \
		m=${libs%%/*}; { [ "$m" = common ] || [ "$m" = integration ]; } && continue; \
		cp "$libs"/blocksouls-"$m"-*[0-9].jar /out/blocksouls-"$m".jar; \
	done \
	&& printf 'BLOCKSOULS_MC_VERSION=%s\nBLOCKSOULS_LOADER_VERSION=%s\n' \
		"$(sed -n 's/^minecraft_version=//p' gradle.properties)" \
		"$(sed -n 's/^loader_version=//p' gradle.properties)" > /out/blocksouls.env

FROM itzg/minecraft-server:java25

ENV TYPE=FABRIC
# The image syncs /mods into /data/mods on every start, leaving other jars there alone.
COPY --from=build /out/*.jar /mods/
COPY --from=build /out/blocksouls.env /etc/blocksouls.env
COPY --chmod=755 docker/entrypoint.sh /blocksouls-entrypoint.sh

ENTRYPOINT ["/blocksouls-entrypoint.sh"]
