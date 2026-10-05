# Wonderful Genome Lib

Wonderful Genome Lib (WGL) is the shared physical-genome engine for XPlayServer Minecraft plugins.

## Consumer entrypoint

Plugins that use WGL should start with [docs/consumer-integration.md](docs/consumer-integration.md). It documents the current SNAPSHOT dependency setup, Paper service acquisition, Profile lifecycle, public `GenomeEngine` operations, persistence boundary, and working consumer examples.

The current development dependency is `co.surumene:wgl-plugin:0.1.0-SNAPSHOT`. WGL remains a separate Paper plugin at runtime; consumer JARs must not bundle WGL classes.

## Modules

- `wgl-api`: immutable genome models, consumer Profile SPI, synthesis/breeding contracts. No Paper dependency.
- `wgl-core`: Format V1 codec, SECDED, gene decoding/regulation, homology, synthesis, recombination/mutation, marker generation. No Paper dependency.
- `wgl-plugin`: Paper 26.2 service host, engine configuration, and Profile registry lifecycle.

Consumer plugins own their Profile implementation and Profile-specific configuration. WGL does not own Minecraft Entity identity, PDC/DB persistence, pedigree snapshots, or game-specific phenotype application.

## Build and test

```bash
gradle build --no-daemon
```

The project targets Java 25. Core tests run without a Minecraft server. GitHub Actions executes the complete Gradle build on `main`, `develop`, and `feature/**` branches.

## Specifications

The authoritative functional specification is maintained under `forGPT/XPlayServer/Minecraft/Wonderful Genome Lib.md` and `forGPT/XPlayServer/Minecraft/WGL/` in the project Google Drive.

Repository documentation is implementation-oriented and follows the current `develop` public API. When a public API changes, update the consumer guide in the same change.
