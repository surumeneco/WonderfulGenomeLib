package co.surumene.wgl.core;

import co.surumene.wgl.api.*;

import java.util.*;

final class BreedingEngine {
    private final EngineConfig config;
    private final HomologyEngine homology;
    private final GenomeDecoderEngine decoder;
    private final PhysicalGenomeDecoder parser;

    BreedingEngine(EngineConfig config, GenomeDecoderEngine decoder) {
        this.config = Objects.requireNonNull(config, "config");
        this.homology = new HomologyEngine(config);
        this.decoder = Objects.requireNonNull(decoder, "decoder");
        this.parser = new PhysicalGenomeDecoder(config);
    }

    BreedingResult breed(GenomeProfile<?> profile, DiploidGenome parentA, DiploidGenome parentB,
                         BreedingContext context, GenomeRandom random) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(parentA, "parentA");
        Objects.requireNonNull(parentB, "parentB");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(random, "random");

        if (parentA.chromosomePairCount() != parentB.chromosomePairCount()) {
            return new BreedingResult.NoViableOffspring(BreedingFailureReason.CHROMOSOME_COUNT_MISMATCH,
                    "parent chromosome pair counts differ");
        }
        BackboneDefinition backbone = context.backbone();
        if (parentA.genomeFormatVersion() != backbone.genomeFormatVersion()
                || parentB.genomeFormatVersion() != backbone.genomeFormatVersion()
                || parentA.chromosomePairCount() != backbone.chromosomes().size()) {
            return new BreedingResult.NoViableOffspring(BreedingFailureReason.INVALID_PARENT_STRUCTURE,
                    "parent structure does not match breeding backbone");
        }

        CompatibilityPolicy policy = context.compatibilityPolicy() != null
                ? context.compatibilityPolicy() : new HomologyCompatibilityPolicyV1(config);
        CompatibilityReport compatibility = policy.assess(parentA, parentB);
        if (!compatibility.compatible()) {
            return new BreedingResult.NoViableOffspring(BreedingFailureReason.INSUFFICIENT_CROSS_PARENT_HOMOLOGY,
                    compatibility.reason());
        }

        if (!validMeiosisPolicy(parentA, context.parentAPolicy())
                || !validMeiosisPolicy(parentB, context.parentBPolicy())) {
            return new BreedingResult.NoViableOffspring(BreedingFailureReason.CONSTRAINT_UNSATISFIABLE,
                    "parent meiosis policy references an invalid physical block");
        }

        List<BitSequence> gameteA = createGamete(profile, parentA, context, context.parentAPolicy(), random);
        if (gameteA == null) {
            return new BreedingResult.NoViableOffspring(BreedingFailureReason.CONSTRAINT_UNSATISFIABLE,
                    "parent A inheritance constraints could not be satisfied");
        }
        List<BitSequence> gameteB = createGamete(profile, parentB, context, context.parentBPolicy(), random);
        if (gameteB == null) {
            return new BreedingResult.NoViableOffspring(BreedingFailureReason.CONSTRAINT_UNSATISFIABLE,
                    "parent B inheritance constraints could not be satisfied");
        }
        List<ChromosomePair> pairs = new ArrayList<>(gameteA.size());
        for (int i = 0; i < gameteA.size(); i++) pairs.add(new ChromosomePair(gameteA.get(i), gameteB.get(i)));
        DiploidGenome child = new DiploidGenome(parentA.genomeFormatVersion(), pairs);

        if (!context.allowSafetyOverride() && !safeDiploid(backbone, child)) {
            return new BreedingResult.NoViableOffspring(BreedingFailureReason.SAFETY_REJECTED,
                    "offspring violates genome safety policy");
        }
        return new BreedingResult.Success(child, decoder.decode(profile, child));
    }

    private List<BitSequence> createGamete(GenomeProfile<?> profile, DiploidGenome parent,
                                           BreedingContext context, ParentMeiosisPolicy meiosisPolicy,
                                           GenomeRandom random) {
        List<PairSnapshot> pairs = new ArrayList<>(parent.chromosomePairCount());
        List<TrackedSequence> sourceChromatids = new ArrayList<>(parent.chromosomePairCount() * 2);
        for (int chromosome = 0; chromosome < parent.chromosomePairCount(); chromosome++) {
            ChromosomePair pair = parent.chromosomePairs().get(chromosome);
            LocalRateSnapshot rateA = LocalRateSnapshot.capture(pair.haplotypeA(), profile, parser, config);
            LocalRateSnapshot rateB = LocalRateSnapshot.capture(pair.haplotypeB(), profile, parser, config);
            TrackedSequence a = TrackedSequence.snapshot(pair.haplotypeA(), chromosome * 2, rateA);
            TrackedSequence b = TrackedSequence.snapshot(pair.haplotypeB(), chromosome * 2 + 1, rateB);
            sourceChromatids.add(a);
            sourceChromatids.add(b);
            pairs.add(new PairSnapshot(a, b, rateA, rateB));
        }
        ProvenanceGuard guard = ProvenanceGuard.capture(profile, parser,
                context.deNovoForbiddenAddresses(), sourceChromatids);

        List<NahrCandidate> nahrCandidates = collectNahrCandidates(pairs);
        List<TrackedSequence> gamete = null;
        if (!nahrCandidates.isEmpty()) {
            double structure = nahrStructureMultiplier(nahrCandidates);
            double probability = clamp(config.mutation().nahr().baseProbability()
                    * context.mutationRateMultiplier() * structure, 0.0, 1.0);
            if (random.nextDouble() < probability) {
                double[] weights = nahrCandidates.stream().mapToDouble(NahrCandidate::weight).toArray();
                for (int attempt = 0; attempt < config.eventRetryMax(); attempt++) {
                    int pick = Sampling.weightedIndex(weights, random);
                    if (pick < 0) break;
                    List<TrackedSequence> candidate = buildGameteWithNahr(pairs, nahrCandidates.get(pick), meiosisPolicy, random);
                    if (candidate != null && hardConstraintsSatisfied(candidate, meiosisPolicy)
                            && guard.valid(candidate)
                            && (context.allowSafetyOverride() || safeHaploid(context.backbone(), candidate))) {
                        gamete = candidate;
                        break;
                    }
                }
                // Retry exhaustion cancels this NAHR event only.
            }
        }

        if (gamete == null) {
            for (int attempt = 0; attempt < config.eventRetryMax(); attempt++) {
                List<TrackedSequence> candidate = new ArrayList<>(pairs.size());
                boolean constraintsPossible = true;
                for (int chromosome = 0; chromosome < pairs.size(); chromosome++) {
                    TrackedSequence recombinant = recombine(pairs.get(chromosome), chromosome, meiosisPolicy, random);
                    if (recombinant == null) { constraintsPossible = false; break; }
                    candidate.add(recombinant);
                }
                if (constraintsPossible && hardConstraintsSatisfied(candidate, meiosisPolicy)
                        && guard.valid(candidate)
                        && (context.allowSafetyOverride() || safeHaploid(context.backbone(), candidate))) {
                    gamete = candidate;
                    break;
                }
            }
        }
        if (gamete == null) {
            gamete = fallbackGamete(pairs, meiosisPolicy, random);
            if (gamete == null) return null;
        }

        applyStructuralMutations(gamete, context, guard, random);
        applyPointMutations(gamete, context, guard, random);
        return gamete.stream().map(TrackedSequence::bits).toList();
    }

    private List<NahrCandidate> collectNahrCandidates(List<PairSnapshot> pairs) {
        List<NahrCandidate> out = new ArrayList<>();

        // Alternative homolog mappings: normal-chain anchors are excluded; ambiguous alternatives remain.
        for (int chromosome = 0; chromosome < pairs.size(); chromosome++) {
            PairSnapshot pair = pairs.get(chromosome);
            HomologyMap map = homology.analyze(pair.a().bits(), pair.b().bits());
            Set<Long> normal = new HashSet<>();
            for (HomologyBlock block : map.blocks()) {
                for (HomologyCandidate a : block.anchors()) normal.add(anchorKey(a.positionA(), a.positionB()));
            }
            List<HomologyCandidate> alternatives = map.ambiguousCandidates().stream()
                    .filter(a -> a.orientation() == HomologyOrientation.FORWARD)
                    .filter(a -> !normal.contains(anchorKey(a.positionA(), a.positionB())))
                    .toList();
            for (HomologyCandidate alternative : alternatives) {
                double contextQuality = NahrSupport.hasSupportingAlternative(
                        alternative, alternatives, config.homology().maxAnchorGapBits()) ? 1.0 : 0.5;
                out.add(new NahrCandidate(NahrKind.HOMOLOG_UNEQUAL, chromosome, 0, alternative.positionA(),
                        chromosome, 1, alternative.positionB(), alternative.hammingDistance(), contextQuality));
            }

            // Intrachromosomal repeats on either homologue.
            for (int haplotype = 0; haplotype < 2; haplotype++) {
                TrackedSequence sequence = haplotype == 0 ? pair.a() : pair.b();
                HomologyMap repeats = homology.analyze(sequence.bits(), sequence.bits());
                for (HomologyCandidate repeat : repeats.candidates()) {
                    if (repeat.positionA() >= repeat.positionB()) continue;
                    if (repeat.positionB() - repeat.positionA() < HomologyEngine.ANCHOR_BITS) continue;
                    NahrKind kind = repeat.orientation() == HomologyOrientation.FORWARD
                            ? NahrKind.INTRACHROM_DELETION : NahrKind.INTRACHROM_INVERSION;
                    out.add(new NahrCandidate(kind, chromosome, haplotype, repeat.positionA(),
                            chromosome, haplotype, repeat.positionB(), repeat.hammingDistance(), 0.5));
                }
            }
        }

        // Repeats on different chromosomes create reciprocal translocation candidates.
        for (int first = 0; first < pairs.size(); first++) {
            for (int second = first + 1; second < pairs.size(); second++) {
                for (int h1 = 0; h1 < 2; h1++) {
                    for (int h2 = 0; h2 < 2; h2++) {
                        TrackedSequence a = h1 == 0 ? pairs.get(first).a() : pairs.get(first).b();
                        TrackedSequence b = h2 == 0 ? pairs.get(second).a() : pairs.get(second).b();
                        HomologyMap map = homology.analyze(a.bits(), b.bits());
                        Set<Long> supported = new HashSet<>();
                        for (HomologyBlock block : map.blocks()) {
                            for (HomologyCandidate anchor : block.anchors())
                                supported.add(anchorKey(anchor.positionA(), anchor.positionB()));
                        }
                        for (HomologyCandidate repeat : map.candidates()) {
                            double contextQuality = supported.contains(anchorKey(repeat.positionA(), repeat.positionB()))
                                    ? 1.0 : 0.5;
                            out.add(new NahrCandidate(NahrKind.CROSS_CHROM_TRANSLOCATION,
                                    first, h1, repeat.positionA(), second, h2, repeat.positionB(),
                                    repeat.hammingDistance(), contextQuality));
                        }
                    }
                }
            }
        }

        out.sort(Comparator.comparing((NahrCandidate c) -> c.kind().ordinal())
                .thenComparingInt(NahrCandidate::chromosomeA)
                .thenComparingInt(NahrCandidate::haplotypeA)
                .thenComparingInt(NahrCandidate::positionA)
                .thenComparingInt(NahrCandidate::chromosomeB)
                .thenComparingInt(NahrCandidate::haplotypeB)
                .thenComparingInt(NahrCandidate::positionB)
                .thenComparingInt(NahrCandidate::hamming));
        return List.copyOf(out);
    }

    private List<TrackedSequence> buildGameteWithNahr(List<PairSnapshot> pairs, NahrCandidate nahr,
                                                       ParentMeiosisPolicy meiosisPolicy, GenomeRandom random) {
        List<TrackedSequence> result = new ArrayList<>(Collections.nCopies(pairs.size(), null));
        switch (nahr.kind()) {
            case HOMOLOG_UNEQUAL -> {
                PairSnapshot pair = pairs.get(nahr.chromosomeA());
                TrackedSequence product = recombineWithNahr(pair, nahr.chromosomeA(),
                        nahr.positionA(), nahr.positionB(), meiosisPolicy, random);
                if (product == null) return null;
                result.set(nahr.chromosomeA(), product);
            }
            case INTRACHROM_DELETION -> {
                TrackedSequence source = nahr.haplotypeA() == 0
                        ? pairs.get(nahr.chromosomeA()).a() : pairs.get(nahr.chromosomeA()).b();
                result.set(nahr.chromosomeA(), source.delete(nahr.positionA(), nahr.positionB()));
            }
            case INTRACHROM_INVERSION -> {
                TrackedSequence source = nahr.haplotypeA() == 0
                        ? pairs.get(nahr.chromosomeA()).a() : pairs.get(nahr.chromosomeA()).b();
                int from = Math.min(source.bitLength(), nahr.positionA() + HomologyEngine.ANCHOR_BITS);
                int to = Math.min(source.bitLength(), nahr.positionB());
                TrackedSequence product = from < to
                        ? source.replace(from, to, source.slice(from, to).reverse()) : source;
                result.set(nahr.chromosomeA(), product);
            }
            case CROSS_CHROM_TRANSLOCATION -> {
                TrackedSequence a = nahr.haplotypeA() == 0
                        ? pairs.get(nahr.chromosomeA()).a() : pairs.get(nahr.chromosomeA()).b();
                TrackedSequence b = nahr.haplotypeB() == 0
                        ? pairs.get(nahr.chromosomeB()).a() : pairs.get(nahr.chromosomeB()).b();
                TrackedSequence newA = a.slice(0, nahr.positionA()).concat(b.slice(nahr.positionB(), b.bitLength()));
                TrackedSequence newB = b.slice(0, nahr.positionB()).concat(a.slice(nahr.positionA(), a.bitLength()));
                result.set(nahr.chromosomeA(), newA);
                result.set(nahr.chromosomeB(), newB);
            }
        }
        for (int chromosome = 0; chromosome < pairs.size(); chromosome++) {
            if (result.get(chromosome) == null) {
                TrackedSequence recombinant = recombine(pairs.get(chromosome), chromosome, meiosisPolicy, random);
                if (recombinant == null) return null;
                result.set(chromosome, recombinant);
            }
        }
        return result;
    }

    private double nahrStructureMultiplier(List<NahrCandidate> candidates) {
        double a = 0.0;
        for (NahrCandidate candidate : candidates) a += candidate.weight();
        double max = config.mutation().nahr().structureMultiplierMax();
        return 1.0 + (max - 1.0) * (1.0 - StrictMath.exp(-a / 8.0));
    }

    private static long anchorKey(int a, int b) {
        return (((long) a) << 32) ^ (b & 0xFFFFFFFFL);
    }

    private TrackedSequence recombine(PairSnapshot pair, int chromosome, ParentMeiosisPolicy meiosisPolicy, GenomeRandom random) {
        List<InheritanceConstraint> constraints = constraintsFor(meiosisPolicy, chromosome);
        HomologyMap map = homology.analyze(pair.a().bits(), pair.b().bits());
        if (map.blocks().isEmpty()) return chooseWholeChromatid(pair, chromosome, constraints, random);

        double meanLength = (pair.a().bitLength() + pair.b().bitLength()) / 2.0;
        int targetCount = RecombinationEventPlan.normalCrossoverTargetCount(
                meanLength, config.recombination(), random);
        List<CrossCandidate> candidates = buildCrossCandidates(pair, map.blocks(), constraints);
        List<CrossCandidate> selected = new ArrayList<>(selectCrosses(candidates, targetCount, random));
        if (selected.isEmpty()) return chooseWholeChromatid(pair, chromosome, constraints, random);

        selected.sort(Comparator.comparingInt(CrossCandidate::a).thenComparingInt(CrossCandidate::b));
        boolean startA = chooseStartingHaplotype(selected, constraints, random);
        TrackedSequence preferred = buildRecombinant(pair, selected, startA);
        if (hardConstraintsSatisfied(preferred, chromosome, constraints)) return preferred;
        TrackedSequence opposite = buildRecombinant(pair, selected, !startA);
        return hardConstraintsSatisfied(opposite, chromosome, constraints) ? opposite : null;
    }

    private TrackedSequence recombineWithNahr(PairSnapshot pair, int chromosome,
                                                   int nahrA, int nahrB,
                                                   ParentMeiosisPolicy meiosisPolicy,
                                                   GenomeRandom random) {
        List<InheritanceConstraint> constraints = constraintsFor(meiosisPolicy, chromosome);
        HomologyMap map = homology.analyze(pair.a().bits(), pair.b().bits());

        double meanLength = (pair.a().bitLength() + pair.b().bitLength()) / 2.0;
        int normalCount = RecombinationEventPlan.normalCrossoverTargetCount(
                meanLength, config.recombination(), random);

        List<CrossCandidate> normal = normalCount == 0 || map.blocks().isEmpty()
                ? List.of()
                : selectCrosses(buildCrossCandidates(pair, map.blocks(), constraints), normalCount, random);

        List<RecombinationEventPlan.Boundary> normalBoundaries = normal.stream()
                .map(x -> new RecombinationEventPlan.Boundary(x.a(), x.b(), false))
                .toList();
        RecombinationEventPlan plan = RecombinationEventPlan.withNahr(
                new RecombinationEventPlan.Boundary(nahrA, nahrB, true), normalBoundaries);

        List<CrossCandidate> combined = plan.boundaries().stream()
                .map(x -> new CrossCandidate(x.a(), x.b(), 1.0))
                .sorted(Comparator.comparingInt(CrossCandidate::a).thenComparingInt(CrossCandidate::b))
                .toList();

        boolean startA = chooseStartingHaplotype(combined, constraints, random);
        TrackedSequence preferred = buildRecombinant(pair, combined, startA);
        if (hardConstraintsSatisfied(preferred, chromosome, constraints)) return preferred;
        TrackedSequence opposite = buildRecombinant(pair, combined, !startA);
        return hardConstraintsSatisfied(opposite, chromosome, constraints) ? opposite : null;
    }

    private List<CrossCandidate> buildCrossCandidates(PairSnapshot pair, List<HomologyBlock> blocks,
                                                      List<InheritanceConstraint> constraints) {
        Map<Long, CrossCandidate> unique = new TreeMap<>();
        EngineConfig.RateBand band = config.localRates().recombination();
        for (HomologyBlock block : blocks) {
            List<HomologyCandidate> anchors = block.anchors();
            for (int i = 0; i < anchors.size() - 1; i++) {
                HomologyCandidate left = anchors.get(i);
                HomologyCandidate right = anchors.get(i + 1);
                int da = right.positionA() - left.positionA();
                int db = right.positionB() - left.positionB();
                if (da <= 1 || db <= 0) continue;
                double qHomology = StrictMath.sqrt(left.anchorQuality() * right.anchorQuality())
                        * StrictMath.exp(-StrictMath.abs(da - db) / 256.0);
                for (int a = left.positionA() + 1; a < right.positionA(); a++) {
                    double t = (a - left.positionA()) / (double) da;
                    int b = left.positionB() + (int) StrictMath.round(t * db);
                    if (b <= 0 || b >= pair.b().bitLength() || a >= pair.a().bitLength()) continue;
                    double qLocal = localSimilarity(pair.a().bits(), a, pair.b().bits(), b);
                    double localRate = pair.rateA().multiplier(LocalRateSnapshot.Kind.RECOMBINATION, a)
                            * pair.rateB().multiplier(LocalRateSnapshot.Kind.RECOMBINATION, b);
                    localRate = clamp(localRate, band.finalMin(), band.finalMax());
                    double inheritanceWeight = crossoverConstraintMultiplier(constraints, a, b);
                    double weight = qHomology * qLocal * localRate * inheritanceWeight;
                    if (!(weight > 0.0)) continue;
                    long key = (((long) a) << 32) ^ (b & 0xFFFFFFFFL);
                    CrossCandidate candidate = new CrossCandidate(a, b, weight);
                    unique.merge(key, candidate, (x, y) -> x.baseWeight() >= y.baseWeight() ? x : y);
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    private List<CrossCandidate> selectCrosses(List<CrossCandidate> candidates, int count, GenomeRandom random) {
        if (candidates.isEmpty() || count <= 0) return List.of();
        List<CrossCandidate> remaining = new ArrayList<>(candidates);
        List<CrossCandidate> selected = new ArrayList<>();
        for (int n = 0; n < count && !remaining.isEmpty(); n++) {
            double[] weights = new double[remaining.size()];
            for (int i = 0; i < remaining.size(); i++) {
                CrossCandidate candidate = remaining.get(i);
                double weight = candidate.baseWeight();
                for (CrossCandidate prior : selected) {
                    long da = (long) candidate.a() - prior.a();
                    long db = (long) candidate.b() - prior.b();
                    if (da == 0 || db == 0 || Long.signum(da) != Long.signum(db)) {
                        weight = 0.0;
                        break;
                    }
                    double distance = Math.min(StrictMath.abs(da), StrictMath.abs(db));
                    double scaled = distance / config.recombination().interferenceDistanceBits();
                    weight *= 1.0 - StrictMath.exp(-(scaled * scaled));
                }
                weights[i] = weight;
            }
            int pick = Sampling.weightedIndex(weights, random);
            if (pick < 0) break;
            selected.add(remaining.remove(pick));
        }
        return List.copyOf(selected);
    }

    private TrackedSequence buildRecombinant(PairSnapshot pair, List<CrossCandidate> selected, boolean startA) {
        boolean onA = startA;
        int cursorA = 0;
        int cursorB = 0;
        TrackedSequence out = TrackedSequence.fresh(BitSequence.empty());
        for (CrossCandidate cross : selected) {
            out = out.concat(onA ? pair.a().slice(cursorA, cross.a()) : pair.b().slice(cursorB, cross.b()));
            cursorA = cross.a();
            cursorB = cross.b();
            onA = !onA;
        }
        return out.concat(onA ? pair.a().slice(cursorA, pair.a().bitLength())
                : pair.b().slice(cursorB, pair.b().bitLength()));
    }

    private boolean chooseStartingHaplotype(List<CrossCandidate> selected,
                                            List<InheritanceConstraint> constraints, GenomeRandom random) {
        boolean hardA = hardSatisfiedByStart(selected, constraints, true);
        boolean hardB = hardSatisfiedByStart(selected, constraints, false);
        if (hardA != hardB) return hardA;
        if (!hardA) return random.nextBoolean();
        double weightA = softStartWeight(selected, constraints, true);
        double weightB = softStartWeight(selected, constraints, false);
        double total = weightA + weightB;
        if (!(total > 0.0) || !Double.isFinite(total)) return random.nextBoolean();
        return random.nextDouble() * total < weightA;
    }

    private static boolean hardSatisfiedByStart(List<CrossCandidate> crosses,
                                                List<InheritanceConstraint> constraints, boolean startA) {
        for (InheritanceConstraint constraint : constraints) {
            if (!constraint.hardProtection()) continue;
            if (sourceHaplotypeAt(crosses, constraint, startA) != constraint.haplotypeIndex()) return false;
        }
        return true;
    }

    private static double softStartWeight(List<CrossCandidate> crosses,
                                          List<InheritanceConstraint> constraints, boolean startA) {
        double logWeight = 0.0;
        boolean any = false;
        for (InheritanceConstraint constraint : constraints) {
            if (constraint.hardProtection()) continue;
            any = true;
            boolean retained = sourceHaplotypeAt(crosses, constraint, startA) == constraint.haplotypeIndex();
            double p = retained ? constraint.retentionProbability() : 1.0 - constraint.retentionProbability();
            if (p <= 0.0) return 0.0;
            logWeight += StrictMath.log(p);
        }
        return any ? StrictMath.exp(logWeight) : 1.0;
    }

    private static int sourceHaplotypeAt(List<CrossCandidate> crosses, InheritanceConstraint constraint, boolean startA) {
        int midpoint = constraint.startBit() + (constraint.endBitExclusive() - constraint.startBit()) / 2;
        int switches = 0;
        for (CrossCandidate cross : crosses) {
            int boundary = constraint.haplotypeIndex() == 0 ? cross.a() : cross.b();
            if (boundary >= midpoint) break;
            switches++;
        }
        boolean onA = startA ^ ((switches & 1) != 0);
        return onA ? 0 : 1;
    }

    private static double crossoverConstraintMultiplier(List<InheritanceConstraint> constraints, int aBoundary, int bBoundary) {
        double multiplier = 1.0;
        for (InheritanceConstraint constraint : constraints) {
            int boundary = constraint.haplotypeIndex() == 0 ? aBoundary : bBoundary;
            if (boundary <= constraint.startBit() || boundary >= constraint.endBitExclusive()) continue;
            if (constraint.hardProtection()) return 0.0;
            multiplier *= constraint.crossoverWeightMultiplier();
        }
        return multiplier;
    }

    private static List<InheritanceConstraint> constraintsFor(ParentMeiosisPolicy policy, int chromosome) {
        return policy.inheritanceConstraints().stream()
                .filter(c -> c.chromosomeIndex() == chromosome).toList();
    }

    private static boolean hardConstraintsSatisfied(List<TrackedSequence> gamete, ParentMeiosisPolicy policy) {
        for (int chromosome = 0; chromosome < gamete.size(); chromosome++) {
            if (!hardConstraintsSatisfied(gamete.get(chromosome), chromosome, constraintsFor(policy, chromosome))) return false;
        }
        return true;
    }

    private static boolean hardConstraintsSatisfied(TrackedSequence sequence, int chromosome,
                                                    List<InheritanceConstraint> constraints) {
        for (InheritanceConstraint constraint : constraints) {
            if (!constraint.hardProtection()) continue;
            int lane = chromosome * 2 + constraint.haplotypeIndex();
            int expected = constraint.startBit();
            boolean found = false;
            for (int i = 0; i < sequence.bitLength(); i++) {
                TrackedSequence.Origin origin = sequence.originAt(i);
                if (origin.lane() == lane && origin.sourceBit() == expected) {
                    expected++;
                    if (expected == constraint.endBitExclusive()) { found = true; break; }
                } else if (expected != constraint.startBit()) {
                    expected = (origin.lane() == lane && origin.sourceBit() == constraint.startBit())
                            ? constraint.startBit() + 1 : constraint.startBit();
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private TrackedSequence chooseWholeChromatid(PairSnapshot pair, int chromosome,
                                                 List<InheritanceConstraint> constraints, GenomeRandom random) {
        boolean aValid = hardConstraintsSatisfied(pair.a(), chromosome, constraints);
        boolean bValid = hardConstraintsSatisfied(pair.b(), chromosome, constraints);
        if (!aValid && !bValid) return null;
        if (aValid != bValid) return aValid ? pair.a() : pair.b();
        return chooseStartingHaplotype(List.of(), constraints, random) ? pair.a() : pair.b();
    }

    private List<TrackedSequence> fallbackGamete(List<PairSnapshot> pairs, ParentMeiosisPolicy policy, GenomeRandom random) {
        List<TrackedSequence> out = new ArrayList<>(pairs.size());
        for (int chromosome = 0; chromosome < pairs.size(); chromosome++) {
            TrackedSequence selected = chooseWholeChromatid(pairs.get(chromosome), chromosome,
                    constraintsFor(policy, chromosome), random);
            if (selected == null) return null;
            out.add(selected);
        }
        return hardConstraintsSatisfied(out, policy) ? out : null;
    }

    private static boolean validMeiosisPolicy(DiploidGenome parent, ParentMeiosisPolicy policy) {
        for (InheritanceConstraint constraint : policy.inheritanceConstraints()) {
            if (constraint.chromosomeIndex() >= parent.chromosomePairCount()) return false;
            ChromosomePair pair = parent.chromosomePairs().get(constraint.chromosomeIndex());
            int length = constraint.haplotypeIndex() == 0
                    ? pair.haplotypeA().bitLength() : pair.haplotypeB().bitLength();
            if (constraint.endBitExclusive() > length) return false;
        }
        return true;
    }

    private double localSimilarity(BitSequence a, int aBoundary, BitSequence b, int bBoundary) {
        int window = config.homology().qLocalWindowBits();
        int left = Math.min(window / 2, Math.min(aBoundary, bBoundary));
        int right = Math.min(window - left,
                Math.min(a.bitLength() - aBoundary, b.bitLength() - bBoundary));
        int missing = window - left - right;
        if (missing > 0) {
            int extraLeft = Math.min(missing, Math.min(aBoundary - left, bBoundary - left));
            left += extraLeft;
            missing -= extraLeft;
        }
        if (missing > 0) {
            int extraRight = Math.min(missing,
                    Math.min(a.bitLength() - aBoundary - right, b.bitLength() - bBoundary - right));
            right += extraRight;
        }
        int compared = left + right;
        if (compared <= 0) return 1.0;
        int hamming = 0;
        for (int i = -left; i < right; i++) {
            if (a.bitAt(aBoundary + i) != b.bitAt(bBoundary + i)) hamming++;
        }
        return StrictMath.exp(-hamming / config.homology().qLocalDecay());
    }

    private void applyStructuralMutations(List<TrackedSequence> gamete, BreedingContext context,
                                          ProvenanceGuard guard, GenomeRandom random) {
        StructuralMutationStage stage = new StructuralMutationStage(gamete);
        EngineConfig.Structural s = config.mutation().structural();

        stage = maybeStructural(StructuralType.INSERTION, s.insertionProbability(), stage, context, guard, random);
        stage = maybeStructural(StructuralType.DELETION, s.deletionProbability(), stage, context, guard, random);
        stage = maybeStructural(StructuralType.DUPLICATION, s.duplicationProbability(), stage, context, guard, random);
        stage = maybeStructural(StructuralType.INVERSION, s.inversionProbability(), stage, context, guard, random);
        stage = maybeStructural(StructuralType.TRANSLOCATION, s.translocationProbability(), stage, context, guard, random);

        List<TrackedSequence> materialized = stage.materialize();
        gamete.clear();
        gamete.addAll(materialized);
    }

    private StructuralMutationStage maybeStructural(StructuralType type, double baseProbability,
                                                    StructuralMutationStage accepted,
                                                    BreedingContext context, ProvenanceGuard guard,
                                                    GenomeRandom random) {
        double probability = clamp(baseProbability * context.mutationRateMultiplier(), 0.0, 1.0);
        if (random.nextDouble() >= probability) return accepted;

        for (int attempt = 0; attempt < config.eventRetryMax(); attempt++) {
            StructuralMutationStage candidate = accepted.copy();
            if (!planStructural(type, candidate, random)) continue;
            List<TrackedSequence> materialized = candidate.materialize();
            if (!guard.valid(materialized)) continue;
            if (!context.allowSafetyOverride() && !safeHaploid(context.backbone(), materialized)) continue;
            return candidate;
        }
        // Retry exhaustion cancels only this event.
        return accepted;
    }

    private boolean planStructural(StructuralType type, StructuralMutationStage stage, GenomeRandom random) {
        EngineConfig.Structural s = config.mutation().structural();
        return switch (type) {
            case INSERTION -> insertion(stage, s, random);
            case DELETION -> deletion(stage, s, random);
            case DUPLICATION -> duplication(stage, s, random);
            case INVERSION -> inversion(stage, s, random);
            case TRANSLOCATION -> translocation(stage, s, random);
        };
    }

    private boolean insertion(StructuralMutationStage stage, EngineConfig.Structural s, GenomeRandom random) {
        List<TrackedSequence> snapshot = stage.snapshot();
        Boundary target = chooseBoundary(snapshot, random, -1);
        if (target == null) return false;
        int length = Sampling.truncatedGeometric(s.insertionLengthP(), s.insertionLengthMaxBits(), random);
        TrackedSequence inserted;
        if (random.nextDouble() < s.insertionRandomSequenceRatio()) {
            inserted = TrackedSequence.fresh(randomBits(length, random));
        } else {
            SourceInterval source = chooseSourceInterval(snapshot, length, random);
            inserted = source == null
                    ? TrackedSequence.fresh(randomBits(length, random))
                    : snapshot.get(source.chromosome()).slice(source.start(), source.end());
        }
        return stage.insert(target.chromosome(), target.position(), inserted);
    }

    private boolean deletion(StructuralMutationStage stage, EngineConfig.Structural s, GenomeRandom random) {
        List<TrackedSequence> snapshot = stage.snapshot();
        int length = Sampling.truncatedGeometric(s.deletionLengthP(), s.deletionLengthMaxBits(), random);
        SourceInterval source = chooseSourceInterval(snapshot, length, random);
        return source != null && stage.delete(source.chromosome(), source.start(), source.end());
    }

    private boolean duplication(StructuralMutationStage stage, EngineConfig.Structural s, GenomeRandom random) {
        List<TrackedSequence> snapshot = stage.snapshot();
        int length = Sampling.truncatedGeometric(s.duplicationLengthP(), s.duplicationLengthMaxBits(), random);
        SourceInterval source = chooseSourceInterval(snapshot, length, random);
        if (source == null) return false;

        int targetChromosome;
        int targetPosition;
        if (random.nextDouble() < s.duplicationSameChromosomeRatio() || snapshot.size() == 1) {
            targetChromosome = source.chromosome();
            targetPosition = source.end();
        } else {
            Boundary target = chooseBoundary(snapshot, random, source.chromosome());
            if (target == null) return false;
            targetChromosome = target.chromosome();
            targetPosition = target.position();
        }
        return stage.duplicate(source.chromosome(), source.start(), source.end(),
                targetChromosome, targetPosition);
    }

    private boolean inversion(StructuralMutationStage stage, EngineConfig.Structural s, GenomeRandom random) {
        List<TrackedSequence> snapshot = stage.snapshot();
        int length = Sampling.truncatedGeometric(s.inversionLengthP(), s.inversionLengthMaxBits(), random);
        SourceInterval source = chooseSourceInterval(snapshot, length, random);
        return source != null && stage.invert(source.chromosome(), source.start(), source.end());
    }

    private boolean translocation(StructuralMutationStage stage, EngineConfig.Structural s, GenomeRandom random) {
        List<TrackedSequence> snapshot = stage.snapshot();
        if (snapshot.isEmpty()) return false;

        if (snapshot.size() > 1 && random.nextDouble() < s.translocationReciprocalRatio()) {
            Boundary first = chooseBoundary(snapshot, random, -1);
            if (first == null) return false;
            Boundary second = chooseBoundary(snapshot, random, first.chromosome());
            return second != null && stage.reciprocalTailSwap(first.chromosome(), first.position(),
                    second.chromosome(), second.position());
        }

        int length = Sampling.truncatedGeometric(s.translocationLengthP(), s.translocationLengthMaxBits(), random);
        SourceInterval source = chooseSourceInterval(snapshot, length, random);
        if (source == null) return false;

        int targetChromosome = source.chromosome();
        if (snapshot.size() > 1 && random.nextDouble() < s.translocationOtherChromosomeRatio()) {
            int pick = random.nextInt(snapshot.size() - 1);
            targetChromosome = pick >= source.chromosome() ? pick + 1 : pick;
        }
        Boundary target = chooseBoundaryForChromosome(snapshot, targetChromosome, random);
        return target != null && stage.move(source.chromosome(), source.start(), source.end(),
                target.chromosome(), target.position());
    }

    private SourceInterval chooseSourceInterval(List<TrackedSequence> snapshot, int desiredLength,
                                                GenomeRandom random) {
        List<SourceInterval> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (int c = 0; c < snapshot.size(); c++) {
            TrackedSequence sequence = snapshot.get(c);
            if (sequence.bitLength() < desiredLength) continue;
            int length = desiredLength;
            for (int start = 0; start + length <= sequence.bitLength(); start++) {
                double w = StrictMath.sqrt(sequence.structuralBoundaryWeight(start)
                        * sequence.structuralBoundaryWeight(start + length));
                candidates.add(new SourceInterval(c, start, start + length));
                weights.add(w);
            }
        }
        if (candidates.isEmpty()) return null;
        double[] raw = weights.stream().mapToDouble(Double::doubleValue).toArray();
        int index = Sampling.weightedIndex(raw, random);
        return index < 0 ? null : candidates.get(index);
    }

    private Boundary chooseBoundary(List<TrackedSequence> snapshot, GenomeRandom random, int excludedChromosome) {
        List<Boundary> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (int c = 0; c < snapshot.size(); c++) {
            if (c == excludedChromosome) continue;
            TrackedSequence sequence = snapshot.get(c);
            for (int p = 0; p <= sequence.bitLength(); p++) {
                candidates.add(new Boundary(c, p));
                weights.add(sequence.structuralBoundaryWeight(p));
            }
        }
        if (candidates.isEmpty()) return null;
        int index = Sampling.weightedIndex(weights.stream().mapToDouble(Double::doubleValue).toArray(), random);
        return index < 0 ? null : candidates.get(index);
    }

    private Boundary chooseBoundaryForChromosome(List<TrackedSequence> snapshot, int chromosome,
                                                 GenomeRandom random) {
        TrackedSequence sequence = snapshot.get(chromosome);
        double[] weights = new double[sequence.bitLength() + 1];
        for (int p = 0; p < weights.length; p++) weights[p] = sequence.structuralBoundaryWeight(p);
        int index = Sampling.weightedIndex(weights, random);
        return index < 0 ? null : new Boundary(chromosome, index);
    }

    private void applyPointMutations(List<TrackedSequence> chromosomes, BreedingContext context,
                                     ProvenanceGuard guard, GenomeRandom random) {
        double base = config.mutation().pointPerBitProbability() * context.mutationRateMultiplier();
        if (!(base > 0.0)) return;
        for (int chromosome = 0; chromosome < chromosomes.size(); chromosome++) {
            TrackedSequence sequence = chromosomes.get(chromosome);
            for (int bit = 0; bit < sequence.bitLength(); bit++) {
                double probability = clamp(base * sequence.originAt(bit).pointMultiplier(), 0.0, 1.0);
                if (random.nextDouble() >= probability) continue;
                TrackedSequence mutated = sequence.flip(bit);
                chromosomes.set(chromosome, mutated);
                if (guard.valid(chromosomes)) sequence = mutated;
                else chromosomes.set(chromosome, sequence);
            }
        }
    }

    private boolean safeHaploid(BackboneDefinition backbone, List<TrackedSequence> chromosomes) {
        List<Integer> lengths = chromosomes.stream().map(TrackedSequence::bitLength).toList();
        return backbone.safetyPolicy().isSafe(lengths, backbone.baselineChromosomeLengths());
    }

    private static boolean safeDiploid(BackboneDefinition b, DiploidGenome g) {
        List<Integer> base = b.baselineChromosomeLengths();
        List<Integer> a = new ArrayList<>(), h = new ArrayList<>();
        for (ChromosomePair p : g.chromosomePairs()) {
            a.add(p.haplotypeA().bitLength());
            h.add(p.haplotypeB().bitLength());
        }
        return b.safetyPolicy().isSafe(a, base) && b.safetyPolicy().isSafe(h, base);
    }

    private static BitSequence randomBits(int length, GenomeRandom random) {
        StringBuilder s = new StringBuilder(length);
        for (int i = 0; i < length; i++) s.append(random.nextBoolean() ? '1' : '0');
        return BitSequence.fromBits(s.toString());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record PairSnapshot(TrackedSequence a, TrackedSequence b,
                                LocalRateSnapshot rateA, LocalRateSnapshot rateB) {}
    private record CrossCandidate(int a, int b, double baseWeight) {}
    private record Boundary(int chromosome, int position) {}
    private record SourceInterval(int chromosome, int start, int end) {}
    private enum StructuralType { INSERTION, DELETION, DUPLICATION, INVERSION, TRANSLOCATION }

    private enum NahrKind { HOMOLOG_UNEQUAL, INTRACHROM_DELETION, INTRACHROM_INVERSION, CROSS_CHROM_TRANSLOCATION }
    private record NahrCandidate(NahrKind kind, int chromosomeA, int haplotypeA, int positionA,
                                 int chromosomeB, int haplotypeB, int positionB, int hamming,
                                 double contextQuality) {
        double weight() {
            double qH = switch (hamming) { case 0 -> 1.0; case 1 -> 0.75; default -> 0.50; };
            return qH * contextQuality;
        }
    }
}
