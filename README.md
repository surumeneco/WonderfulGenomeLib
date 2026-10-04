# Wonderful Genome Lib

Wonderful Genome Lib (WGL) is the shared physical-genome engine for XPlayServer Minecraft plugins.

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

The authoritative functional specification is maintained under `forGPT/XPlayServer/Minecraft/Wonderful Genome Lib.md` and `forGPT/XPlayServer/Minecraft/WGL/` in the project Google Drive.
