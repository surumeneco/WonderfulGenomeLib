from pathlib import Path

paths = list(Path("Consumer/src/test/java").rglob("GenomeStatisticsProbeTest.java"))
if len(paths) != 1:
    raise SystemExit(f"Expected one statistics probe, got {paths}")
p = paths[0]
s = p.read_text(encoding="utf-8")
species = "WWC" if "WhatAWonderfulChicken" in str(p) or "whatawonderfulchicken" in str(p) else "WWW"

method = """
    private static String fullGenome(WonderfulGenomeEngine engine, DiploidGenome genome) {
        return java.util.Base64.getEncoder().encodeToString(engine.encode(genome));
    }
"""
needle = "    @Test void executeStatisticalCohort"
if needle not in s:
    raise SystemExit("Missing cohort method")
s = s.replace(needle, method + "\n" + needle, 1)

natural = "                parents.add(make(generated,FounderOrigin.NATURAL));"
if natural not in s:
    raise SystemExit("Missing natural founder")
s = s.replace(natural, natural + """
                System.out.printf("STAT_FULL stage=NATURAL shard=%d family=%d side=%d genome=%s%n",
                        GenomeStats.SHARD, f, side, fullGenome(engine,success.genome()));
""", 1)

trap = "            if(trap.result() instanceof SynthesisResult.Failure fail) {"
if trap not in s:
    raise SystemExit("Missing trap")
s = s.replace(trap, """
            if (trap.result() instanceof SynthesisResult.Success found) {
                System.out.printf("STAT_FULL stage=TRAP shard=%d family=%d genome=%s%n",
                        GenomeStats.SHARD, f, fullGenome(engine, found.genome()));
            }
""" + trap, 1)

target = f'                GenomeStats.genome("{species}",f,i,child.genome(),a.genome(),b.genome(),unrelated.genome());'
if target not in s:
    raise SystemExit("Missing breeding entry")
line = """
                System.out.printf("STAT_FULL stage=BREED shard=%d family=%d sibling=%d genome=%s phenotype=%s marker=%s%n",
                        GenomeStats.SHARD, f, i, fullGenome(engine, child.genome()),
                        java.util.Base64.getEncoder().encodeToString(
                                String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        engine.marker(profile.backbone(), child.genome()).formatted());
"""
s = s.replace(target, line + target, 1)
p.write_text(s, encoding="utf-8")
print(f"Golden probe inserted: {species}, {p}")
