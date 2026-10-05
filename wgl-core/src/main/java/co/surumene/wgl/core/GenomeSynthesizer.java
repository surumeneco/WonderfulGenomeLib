package co.surumene.wgl.core;

import co.surumene.wgl.api.*;

import java.util.*;

final class GenomeSynthesizer {
    private final EngineConfig config;
    private final GenomeDecoderEngine decoder;

    GenomeSynthesizer(EngineConfig config, GenomeDecoderEngine decoder) {
        this.config = Objects.requireNonNull(config, "config");
        this.decoder = Objects.requireNonNull(decoder, "decoder");
    }

    SynthesisResult synthesize(GenomeProfile<?> profile, BackboneDefinition backbone, SynthesisTarget target,
                               SynthesisContext context, GenomeRandom random) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(backbone, "backbone");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(random, "random");

        for (var e : target.continuousTargets().entrySet()) {
            if (e.getKey().isRegulation() || e.getKey().type() >= 0xF0 || e.getKey().target() == 0xFF
                    || !profile.isDefinedAddress(e.getKey())
                    || !Double.isFinite(e.getValue()) || e.getValue() < 0 || e.getValue() > 1) {
                return new SynthesisResult.Failure(SynthesisFailureReason.INVALID_TARGET,
                        "invalid continuous target " + e.getKey());
            }
        }

        boolean sawSafetyRejection = false;
        boolean sawSafeCandidate = false;

        for (int attempt = 0; attempt < context.genomeRetries(); attempt++) {
            List<MutableScaffold> a = new ArrayList<>();
            List<MutableScaffold> b = new ArrayList<>();
            for (ChromosomeTemplate template : backbone.chromosomes()) {
                a.add(new MutableScaffold(template, sampleFounderLength(template, random), random));
                b.add(new MutableScaffold(template, sampleFounderLength(template, random), random));
            }

            int profileBlockCount = placeProfileBlocks(
                    profile, backbone, target, context, a, b, random);

            boolean unsatisfiable = false;
            boolean profileOvershoot = false;
            Map<GenomeAddress, SynthesisAddressPlan> synthesisPlans = new TreeMap<>();
            for (var e : new TreeMap<>(target.continuousTargets()).entrySet()) {
                GenomeAddress address = e.getKey();
                double targetScore = e.getValue();
                SynthesisAddressPlan synthesisPlan = Objects.requireNonNull(
                        profile.synthesisPlan(address, targetScore, context, random),
                        "profile synthesisPlan returned null");
                synthesisPlans.put(address, synthesisPlan);

                DecodeResult<?> current = decoder.decode(
                        profile, currentGenome(backbone, a, b));
                SynthesisAddressPlan residual = residualPlan(
                        synthesisPlan,
                        current.decodedGenome().aggregate(address),
                        config.synthesizer().convergenceTolerance());
                if (residual == null) {
                    profileOvershoot = true;
                    break;
                }
                if (!placeAddressPlan(profile, backbone, target, address, residual, a, b, random)) {
                    unsatisfiable = true;
                    break;
                }
            }
            if (profileOvershoot) {
                continue;
            }
            if (unsatisfiable) {
                return new SynthesisResult.Failure(SynthesisFailureReason.UNSATISFIABLE_TARGET,
                        "one or more continuous targets cannot be represented by the configured gene count range");
            }

            List<ChromosomePair> pairs = new ArrayList<>();
            for (int i = 0; i < a.size(); i++) {
                pairs.add(new ChromosomePair(a.get(i).bits(), b.get(i).bits()));
            }
            DiploidGenome genome = new DiploidGenome(backbone.genomeFormatVersion(), pairs);
            if (!safeDiploid(backbone, genome)) {
                sawSafetyRejection = true;
                continue;
            }
            DecodeResult<?> decoded = decoder.decode(profile, genome);
            if (!synthesisSafe(profile, genome, decoded.decodedGenome())) {
                sawSafetyRejection = true;
                continue;
            }
            sawSafeCandidate = true;
            if (target.isSatisfied(decoded.decodedGenome(), config.synthesizer().convergenceTolerance())) {
                return successWithMicroCorrections(
                        profile, backbone, target, synthesisPlans, a, b, decoded, random);
            }

            SynthesisResult.Success adjusted = locallyAdjust(
                    profile, backbone, target, synthesisPlans, a, b, decoded, random);
            if (adjusted != null) return adjusted;

            SynthesisResult.Success regenerated = regenerateProblematicAddress(
                    profile, backbone, target, context, synthesisPlans, a, b, random);
            if (regenerated != null) return regenerated;

            if (profileBlockCount > 0) {
                SynthesisResult.Success profileBlocksRegenerated = regenerateProfileBlocks(
                        profile, backbone, target, context, synthesisPlans, a, b, random);
                if (profileBlocksRegenerated != null) return profileBlocksRegenerated;
            }
        }
        if (sawSafetyRejection && !sawSafeCandidate) {
            return new SynthesisResult.Failure(SynthesisFailureReason.SAFETY_REJECTED,
                    "all synthesized candidates were rejected by backbone safety policy");
        }
        return new SynthesisResult.Failure(SynthesisFailureReason.CONVERGENCE_LIMIT,
                "target did not converge within configured retries");
    }

    private SynthesisResult.Success locallyAdjust(GenomeProfile<?> profile,
                                                        BackboneDefinition backbone,
                                                        SynthesisTarget target,
                                                        Map<GenomeAddress, SynthesisAddressPlan> synthesisPlans,
                                                        List<MutableScaffold> a,
                                                        List<MutableScaffold> b,
                                                        DecodeResult<?> initial,
                                                        GenomeRandom random) {
        DecodeResult<?> decoded = initial;
        double tolerance = config.synthesizer().convergenceTolerance();
        double maxRatio = config.synthesizer().localAdjustmentMaxContributionRatio();

        for (int iteration = 0; iteration < config.synthesizer().localAdjustmentMaxIterations(); iteration++) {
            if (target.isSatisfied(decoded.decodedGenome(), tolerance)) {
                return successWithMicroCorrections(
                        profile, backbone, target, synthesisPlans, a, b, decoded, random);
            }

            Residual residual = largestResidual(synthesisPlans, decoded.decodedGenome(), tolerance);
            if (residual == null) break;

            GenomeAddress address = residual.address();
            AddressAggregate aggregate = decoded.decodedGenome().aggregate(address);
            boolean negative = residual.negative();
            double currentSaturation = negative
                    ? 1.0 - aggregate.negativeSurvival()
                    : aggregate.positiveSaturation();
            if (residual.targetSaturation() <= currentSaturation) break;

            double baseAmount = Math.max(residual.targetSaturation(), currentSaturation);
            double stepLimit = baseAmount * maxRatio;
            if (!(stepLimit > 0.0)) stepLimit = Math.min(residual.absoluteError(), tolerance);
            double desiredDelta = Math.min(residual.absoluteError(), stepLimit);
            double desiredSaturation = currentSaturation + desiredDelta;

            DirectContributionModel model = profile.contributionModel(address);
            int magnitude = bestAdjustmentMagnitude(model, address, currentSaturation, desiredSaturation, negative);
            if (magnitude <= 0) break;

            BitSequence extension = Objects.requireNonNull(
                    profile.synthesisExtension(address, target, random),
                    "profile synthesisExtension returned null");
            int minimumExtension = GenomeFormatV1.minimumExtensionBits(address, profile);
            if (minimumExtension < 0 || minimumExtension > 64
                    || extension.bitLength() < minimumExtension || extension.bitLength() > 64) {
                throw new IllegalStateException("invalid synthesis extension length for " + address
                        + ": " + extension.bitLength() + " bits, minimum=" + minimumExtension);
            }
            BitSequence gene = GeneCodecV1.encode(address, negative, magnitude, 15, extension);
            BitSequence spacer = nonCodingSpacer(random, 8 + random.nextInt(25));

            List<MutableScaffold> trialA = copyScaffolds(a);
            List<MutableScaffold> trialB = copyScaffolds(b);
            int chromosome = weightedChromosome(backbone, random);
            MutableScaffold scaffold = random.nextBoolean()
                    ? trialA.get(chromosome) : trialB.get(chromosome);
            scaffold.insertGeneratedAtSafeBoundary(spacer.concat(gene), random, address);

            DiploidGenome genome = currentGenome(backbone, trialA, trialB);
            if (!safeDiploid(backbone, genome)) continue;

            DecodeResult<?> next = decoder.decode(profile, genome);
            if (!synthesisSafe(profile, genome, next.decodedGenome())) continue;
            double nextError = totalError(synthesisPlans, next.decodedGenome());
            double currentError = totalError(synthesisPlans, decoded.decodedGenome());
            if (!(nextError < currentError)) continue;

            replaceScaffolds(a, trialA);
            replaceScaffolds(b, trialB);
            decoded = next;
        }

        DiploidGenome genome = currentGenome(backbone, a, b);
        if (!safeDiploid(backbone, genome)) return null;
        DecodeResult<?> finalDecoded = decoder.decode(profile, genome);
        return target.isSatisfied(finalDecoded.decodedGenome(), tolerance)
                ? successWithMicroCorrections(
                        profile, backbone, target, synthesisPlans, a, b, finalDecoded, random)
                : null;
    }

    private SynthesisResult.Success regenerateProblematicAddress(
            GenomeProfile<?> profile,
            BackboneDefinition backbone,
            SynthesisTarget target,
            SynthesisContext context,
            Map<GenomeAddress, SynthesisAddressPlan> synthesisPlans,
            List<MutableScaffold> a,
            List<MutableScaffold> b,
            GenomeRandom random) {
        double tolerance = config.synthesizer().convergenceTolerance();
        DiploidGenome currentGenome = currentGenome(backbone, a, b);
        if (!safeDiploid(backbone, currentGenome)) return null;

        DecodeResult<?> currentDecoded = decoder.decode(profile, currentGenome);
        Residual residual = largestResidual(synthesisPlans, currentDecoded.decodedGenome(), tolerance);
        if (residual == null) return null;

        GenomeAddress address = residual.address();
        Double targetScore = target.continuousTargets().get(address);
        if (targetScore == null) return null;

        List<MutableScaffold> trialA = copyScaffolds(a);
        List<MutableScaffold> trialB = copyScaffolds(b);
        for (MutableScaffold scaffold : trialA) scaffold.removeGeneratedBlocks(address);
        for (MutableScaffold scaffold : trialB) scaffold.removeGeneratedBlocks(address);

        SynthesisAddressPlan replacement = Objects.requireNonNull(
                profile.synthesisPlan(address, targetScore, context, random),
                "profile synthesisPlan returned null");
        DecodeResult<?> baseline = decoder.decode(
                profile, currentGenome(backbone, trialA, trialB));
        SynthesisAddressPlan remainingPlan = residualPlan(
                replacement,
                baseline.decodedGenome().aggregate(address),
                config.synthesizer().convergenceTolerance());
        if (remainingPlan == null
                || !placeAddressPlan(profile, backbone, target, address, remainingPlan, trialA, trialB, random)) {
            return null;
        }

        Map<GenomeAddress, SynthesisAddressPlan> trialPlans = new TreeMap<>(synthesisPlans);
        trialPlans.put(address, replacement);

        DiploidGenome regeneratedGenome = currentGenome(backbone, trialA, trialB);
        if (!safeDiploid(backbone, regeneratedGenome)) return null;
        DecodeResult<?> regeneratedDecoded = decoder.decode(profile, regeneratedGenome);
        if (!synthesisSafe(profile, regeneratedGenome, regeneratedDecoded.decodedGenome())) return null;

        if (target.isSatisfied(regeneratedDecoded.decodedGenome(), tolerance)) {
            return successWithMicroCorrections(
                    profile, backbone, target, trialPlans, trialA, trialB, regeneratedDecoded, random);
        }
        return locallyAdjust(
                profile, backbone, target, trialPlans, trialA, trialB, regeneratedDecoded, random);
    }

    private SynthesisResult.Success regenerateProfileBlocks(
            GenomeProfile<?> profile,
            BackboneDefinition backbone,
            SynthesisTarget target,
            SynthesisContext context,
            Map<GenomeAddress, SynthesisAddressPlan> synthesisPlans,
            List<MutableScaffold> a,
            List<MutableScaffold> b,
            GenomeRandom random) {
        List<MutableScaffold> trialA = copyScaffolds(a);
        List<MutableScaffold> trialB = copyScaffolds(b);
        for (MutableScaffold scaffold : trialA) scaffold.removeGeneratedBlocks(null);
        for (MutableScaffold scaffold : trialB) scaffold.removeGeneratedBlocks(null);

        placeProfileBlocks(profile, backbone, target, context, trialA, trialB, random);

        DiploidGenome regeneratedGenome = currentGenome(backbone, trialA, trialB);
        if (!safeDiploid(backbone, regeneratedGenome)) return null;

        double tolerance = config.synthesizer().convergenceTolerance();
        DecodeResult<?> regeneratedDecoded = decoder.decode(profile, regeneratedGenome);
        if (!synthesisSafe(profile, regeneratedGenome, regeneratedDecoded.decodedGenome())) return null;
        if (target.isSatisfied(regeneratedDecoded.decodedGenome(), tolerance)) {
            return successWithMicroCorrections(
                    profile, backbone, target, synthesisPlans,
                    trialA, trialB, regeneratedDecoded, random);
        }

        SynthesisResult.Success adjusted = locallyAdjust(
                profile, backbone, target, synthesisPlans,
                trialA, trialB, regeneratedDecoded, random);
        if (adjusted != null) return adjusted;

        return regenerateProblematicAddress(
                profile, backbone, target, context, synthesisPlans,
                trialA, trialB, random);
    }

    private SynthesisResult.Success successWithMicroCorrections(
            GenomeProfile<?> profile,
            BackboneDefinition backbone,
            SynthesisTarget target,
            Map<GenomeAddress, SynthesisAddressPlan> synthesisPlans,
            List<MutableScaffold> a,
            List<MutableScaffold> b,
            DecodeResult<?> initial,
            GenomeRandom random) {
        double tolerance = config.synthesizer().convergenceTolerance();
        DecodeResult<?> decoded = initial;

        for (var entry : new TreeMap<>(synthesisPlans).entrySet()) {
            AddressAggregate aggregate = decoded.decodedGenome().aggregate(entry.getKey());
            MicroResidual residual = microResidual(entry.getKey(), entry.getValue(), aggregate, tolerance);
            if (residual == null) continue;

            DirectContributionModel model = profile.contributionModel(residual.address());
            MicroGene micro = bestMicroGene(
                    model, residual.address(), residual.negative(),
                    residual.currentSaturation(), residual.targetSaturation(), tolerance);
            if (micro == null) continue;

            BitSequence extension = Objects.requireNonNull(
                    profile.synthesisExtension(residual.address(), target, random),
                    "profile synthesisExtension returned null");
            int minimumExtension = GenomeFormatV1.minimumExtensionBits(residual.address(), profile);
            if (minimumExtension < 0 || minimumExtension > 64
                    || extension.bitLength() < minimumExtension || extension.bitLength() > 64) {
                throw new IllegalStateException("invalid synthesis extension length for " + residual.address()
                        + ": " + extension.bitLength() + " bits, minimum=" + minimumExtension);
            }

            BitSequence gene = GeneCodecV1.encode(
                    residual.address(), residual.negative(),
                    micro.magnitude(), micro.expression(), extension);
            BitSequence spacer = nonCodingSpacer(random, 8 + random.nextInt(25));

            List<MutableScaffold> trialA = copyScaffolds(a);
            List<MutableScaffold> trialB = copyScaffolds(b);
            int chromosome = weightedChromosome(backbone, random);
            int haplotype = random.nextBoolean() ? 0 : 1;
            MutableScaffold scaffold = haplotype == 0
                    ? trialA.get(chromosome) : trialB.get(chromosome);
            int insertion = scaffold.insertGeneratedAtSafeBoundary(
                    spacer.concat(gene), random, residual.address());
            int geneStart = insertion + spacer.bitLength();

            DiploidGenome trialGenome = currentGenome(backbone, trialA, trialB);
            if (!safeDiploid(backbone, trialGenome)) continue;

            DecodeResult<?> next = decoder.decode(profile, trialGenome);
            if (!synthesisSafe(profile, trialGenome, next.decodedGenome())) continue;
            if (feedsRelay(next.decodedGenome(), chromosome, haplotype, geneStart)) continue;
            if (!target.isSatisfied(next.decodedGenome(), tolerance)) continue;

            AddressAggregate nextAggregate = next.decodedGenome().aggregate(residual.address());
            double nextSaturation = residual.negative()
                    ? 1.0 - nextAggregate.negativeSurvival()
                    : nextAggregate.positiveSaturation();
            double actualDelta = nextSaturation - residual.currentSaturation();
            if (!(actualDelta > 0.0)
                    || actualDelta > residual.absoluteError() + 1.0e-12
                    || actualDelta > tolerance + 1.0e-12) {
                continue;
            }

            double currentError = totalError(synthesisPlans, decoded.decodedGenome());
            double nextError = totalError(synthesisPlans, next.decodedGenome());
            if (!(nextError < currentError)) continue;

            replaceScaffolds(a, trialA);
            replaceScaffolds(b, trialB);
            decoded = next;
        }

        return new SynthesisResult.Success(currentGenome(backbone, a, b), decoded);
    }

    private static MicroResidual microResidual(
            GenomeAddress address,
            SynthesisAddressPlan plan,
            AddressAggregate aggregate,
            double tolerance) {
        double positive = plan.positiveSaturation() - aggregate.positiveSaturation();
        double negativeCurrent = 1.0 - aggregate.negativeSurvival();
        double negative = plan.negativeSaturation() - negativeCurrent;

        MicroResidual best = null;
        if (positive > 1.0e-12 && positive <= tolerance + 1.0e-12) {
            best = new MicroResidual(
                    address, false, aggregate.positiveSaturation(),
                    plan.positiveSaturation(), positive);
        }
        if (negative > 1.0e-12 && negative <= tolerance + 1.0e-12
                && (best == null || negative > best.absoluteError())) {
            best = new MicroResidual(
                    address, true, negativeCurrent,
                    plan.negativeSaturation(), negative);
        }
        return best;
    }

    private static MicroGene bestMicroGene(
            DirectContributionModel model,
            GenomeAddress address,
            boolean negative,
            double currentSaturation,
            double targetSaturation,
            double tolerance) {
        double residual = targetSaturation - currentSaturation;
        if (!(residual > 1.0e-12) || residual > tolerance + 1.0e-12) return null;

        MicroGene best = null;
        for (int expression = 1; expression <= 15; expression++) {
            for (int magnitude = 1; magnitude <= 127; magnitude++) {
                double d = model.baseEffect(address, negative, magnitude, expression);
                double u = model.saturation(address, StrictMath.abs(d));
                if (!(u > 0.0) || u > tolerance + 1.0e-12) continue;

                double predicted = 1.0 - (1.0 - currentSaturation) * (1.0 - u);
                double delta = predicted - currentSaturation;
                if (!(delta > 0.0) || delta > residual + 1.0e-12) continue;

                double error = targetSaturation - predicted;
                if (best == null || error < best.remainingError() - 1.0e-15) {
                    best = new MicroGene(magnitude, expression, error);
                }
            }
        }
        return best;
    }

    private static boolean feedsRelay(
            DecodedGenome decoded, int chromosome, int haplotype, int sourceStart) {
        List<DecodedGene> lane = decoded.physicalGenes().stream()
                .filter(gene -> gene.chromosomeIndex() == chromosome
                        && gene.haplotypeIndex() == haplotype)
                .sorted(Comparator.comparingInt(DecodedGene::startBit))
                .toList();

        for (int i = 0; i < lane.size(); i++) {
            DecodedGene gene = lane.get(i);
            if (gene.startBit() != sourceStart) continue;

            if (i + 1 < lane.size()) {
                DecodedGene next = lane.get(i + 1);
                if (next.regulation()
                        && next.address().target() == 0x06
                        && next.orientation() == GeneOrientation.FORWARD) {
                    return true;
                }
            }
            if (i > 0) {
                DecodedGene previous = lane.get(i - 1);
                if (previous.regulation()
                        && previous.address().target() == 0x06
                        && previous.orientation() == GeneOrientation.REVERSE) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    private static boolean placeAddressPlan(GenomeProfile<?> profile,
                                            BackboneDefinition backbone,
                                            SynthesisTarget target,
                                            GenomeAddress address,
                                            SynthesisAddressPlan synthesisPlan,
                                            List<MutableScaffold> a,
                                            List<MutableScaffold> b,
                                            GenomeRandom random) {
        DirectContributionModel model = profile.contributionModel(address);
        GenePlan positive = bestPlan(model, address, synthesisPlan.positiveSaturation(),
                synthesisPlan.minPositiveGenes(), synthesisPlan.maxPositiveGenes(), false);
        GenePlan negative = bestPlan(model, address, synthesisPlan.negativeSaturation(),
                synthesisPlan.minNegativeGenes(), synthesisPlan.maxNegativeGenes(), true);
        if (positive == null || negative == null) return false;

        List<GeneSpec> genes = new ArrayList<>(positive.magnitudes().size() + negative.magnitudes().size());
        for (int magnitude : positive.magnitudes()) genes.add(new GeneSpec(address, false, magnitude));
        for (int magnitude : negative.magnitudes()) genes.add(new GeneSpec(address, true, magnitude));
        shuffle(genes, random);

        for (GeneSpec spec : genes) {
            BitSequence extension = Objects.requireNonNull(
                    profile.synthesisExtension(spec.address(), target, random),
                    "profile synthesisExtension returned null");
            int minimumExtension = GenomeFormatV1.minimumExtensionBits(spec.address(), profile);
            if (minimumExtension < 0 || minimumExtension > 64
                    || extension.bitLength() < minimumExtension || extension.bitLength() > 64) {
                throw new IllegalStateException("invalid synthesis extension length for " + spec.address()
                        + ": " + extension.bitLength() + " bits, minimum=" + minimumExtension);
            }
            BitSequence gene = GeneCodecV1.encode(spec.address(), spec.negative(), spec.magnitude(), 15, extension);
            BitSequence spacer = nonCodingSpacer(random, 8 + random.nextInt(25));
            int chromosome = weightedChromosome(backbone, random);
            MutableScaffold scaffold = random.nextBoolean() ? a.get(chromosome) : b.get(chromosome);
            scaffold.insertGeneratedAtSafeBoundary(spacer.concat(gene), random, address);
        }
        return true;
    }

    private static int placeProfileBlocks(GenomeProfile<?> profile,
                                           BackboneDefinition backbone,
                                           SynthesisTarget target,
                                           SynthesisContext context,
                                           List<MutableScaffold> a,
                                           List<MutableScaffold> b,
                                           GenomeRandom random) {
        List<SynthesisBlock> blocks = List.copyOf(Objects.requireNonNull(
                profile.synthesisBlocks(target, context, random),
                "profile synthesisBlocks returned null"));
        for (SynthesisBlock block : blocks) {
            Objects.requireNonNull(block, "profile synthesisBlocks contains null");
            int chromosome = block.chromosomeIndex() == SynthesisBlock.RANDOM
                    ? weightedChromosome(backbone, random)
                    : block.chromosomeIndex();
            if (chromosome >= backbone.chromosomes().size()) {
                throw new IllegalStateException("profile synthesis block chromosome outside backbone: " + chromosome);
            }
            int haplotype = block.haplotypeIndex() == SynthesisBlock.RANDOM
                    ? (random.nextBoolean() ? 0 : 1)
                    : block.haplotypeIndex();
            MutableScaffold scaffold = haplotype == 0 ? a.get(chromosome) : b.get(chromosome);
            scaffold.insertAtSafeBoundary(block.bits(), random);
        }
        return blocks.size();
    }

    private static boolean synthesisSafe(GenomeProfile<?> profile,
                                         DiploidGenome genome,
                                         DecodedGenome decodedGenome) {
        SynthesisSafetyPolicy policy = Objects.requireNonNull(
                profile.synthesisSafetyPolicy(),
                "profile synthesisSafetyPolicy returned null");
        return policy.isSafe(synthesisMetrics(genome, decodedGenome), decodedGenome);
    }

    private static SynthesisMetrics synthesisMetrics(DiploidGenome genome, DecodedGenome decodedGenome) {
        List<SynthesisHaplotypeMetrics> metrics = new ArrayList<>();
        for (int chromosome = 0; chromosome < genome.chromosomePairs().size(); chromosome++) {
            ChromosomePair pair = genome.chromosomePairs().get(chromosome);
            for (int haplotype = 0; haplotype <= 1; haplotype++) {
                int chromosomeIndex = chromosome;
                int haplotypeIndex = haplotype;
                BitSequence bits = haplotypeIndex == 0 ? pair.haplotypeA() : pair.haplotypeB();
                List<DecodedGene> genes = decodedGenome.physicalGenes().stream()
                        .filter(gene -> gene.chromosomeIndex() == chromosomeIndex
                                && gene.haplotypeIndex() == haplotypeIndex)
                        .toList();

                List<Interval> ranges = new ArrayList<>(genes.size());
                for (DecodedGene gene : genes) {
                    int start = Math.max(0, Math.min(bits.bitLength(), gene.startBit()));
                    int end = Math.max(start, Math.min(bits.bitLength(), gene.endBitExclusive()));
                    if (end > start) ranges.add(new Interval(start, end));
                }
                int recognizableBits = 0;
                for (Interval interval : MutableScaffold.merge(ranges)) {
                    recognizableBits += interval.end() - interval.start();
                }

                metrics.add(new SynthesisHaplotypeMetrics(
                        chromosomeIndex,
                        haplotypeIndex,
                        bits.bitLength(),
                        genes.size(),
                        recognizableBits));
            }
        }
        return new SynthesisMetrics(metrics);
    }

    private static List<MutableScaffold> copyScaffolds(List<MutableScaffold> source) {
        List<MutableScaffold> copy = new ArrayList<>(source.size());
        for (MutableScaffold scaffold : source) copy.add(scaffold.copy());
        return copy;
    }

    private static void replaceScaffolds(List<MutableScaffold> target,
                                         List<MutableScaffold> replacement) {
        target.clear();
        target.addAll(replacement);
    }

    private static DiploidGenome currentGenome(BackboneDefinition backbone,
                                               List<MutableScaffold> a,
                                               List<MutableScaffold> b) {
        List<ChromosomePair> pairs = new ArrayList<>(a.size());
        for (int i = 0; i < a.size(); i++) {
            pairs.add(new ChromosomePair(a.get(i).bits(), b.get(i).bits()));
        }
        return new DiploidGenome(backbone.genomeFormatVersion(), pairs);
    }

    private static SynthesisAddressPlan residualPlan(
            SynthesisAddressPlan desired,
            AddressAggregate current,
            double tolerance) {
        double currentPositive = current.positiveSaturation();
        double currentNegative = 1.0 - current.negativeSurvival();

        if (currentPositive > desired.positiveSaturation() + tolerance
                || currentNegative > desired.negativeSaturation() + tolerance) {
            return null;
        }

        double positive = remainingSaturation(
                desired.positiveSaturation(), currentPositive, tolerance);
        double negative = remainingSaturation(
                desired.negativeSaturation(), currentNegative, tolerance);

        return new SynthesisAddressPlan(
                positive,
                negative,
                positive == 0.0 ? 0 : desired.minPositiveGenes(),
                positive == 0.0 ? 0 : desired.maxPositiveGenes(),
                negative == 0.0 ? 0 : desired.minNegativeGenes(),
                negative == 0.0 ? 0 : desired.maxNegativeGenes());
    }

    private static double remainingSaturation(
            double target,
            double current,
            double tolerance) {
        if (target <= current + tolerance) {
            return 0.0;
        }
        if (current >= 1.0) {
            return 1.0;
        }
        double residual = (target - current) / (1.0 - current);
        return Math.max(0.0, Math.min(1.0, residual));
    }

    private static Residual largestResidual(Map<GenomeAddress, SynthesisAddressPlan> plans,
                                            DecodedGenome decoded, double tolerance) {
        Residual best = null;
        for (var entry : plans.entrySet()) {
            AddressAggregate aggregate = decoded.aggregate(entry.getKey());
            SynthesisAddressPlan plan = entry.getValue();

            double positiveError = StrictMath.abs(plan.positiveSaturation() - aggregate.positiveSaturation());
            if (positiveError > tolerance && (best == null || positiveError > best.absoluteError())) {
                best = new Residual(entry.getKey(), false, plan.positiveSaturation(), positiveError);
            }

            double negative = 1.0 - aggregate.negativeSurvival();
            double negativeError = StrictMath.abs(plan.negativeSaturation() - negative);
            if (negativeError > tolerance && (best == null || negativeError > best.absoluteError())) {
                best = new Residual(entry.getKey(), true, plan.negativeSaturation(), negativeError);
            }
        }
        return best;
    }

    private static double totalError(Map<GenomeAddress, SynthesisAddressPlan> plans, DecodedGenome decoded) {
        double total = 0.0;
        for (var entry : plans.entrySet()) {
            AddressAggregate aggregate = decoded.aggregate(entry.getKey());
            total += StrictMath.abs(entry.getValue().positiveSaturation() - aggregate.positiveSaturation());
            total += StrictMath.abs(entry.getValue().negativeSaturation()
                    - (1.0 - aggregate.negativeSurvival()));
        }
        return total;
    }

    private static int bestAdjustmentMagnitude(DirectContributionModel model,
                                               GenomeAddress address,
                                               double currentSaturation,
                                               double desiredSaturation,
                                               boolean negative) {
        int bestMagnitude = 0;
        double bestError = StrictMath.abs(currentSaturation - desiredSaturation);
        for (int magnitude = 1; magnitude <= 127; magnitude++) {
            double candidateU = u(model, address, negative, magnitude);
            double predicted = 1.0 - (1.0 - currentSaturation) * (1.0 - candidateU);
            double error = StrictMath.abs(predicted - desiredSaturation);
            if (error < bestError) {
                bestError = error;
                bestMagnitude = magnitude;
            }
        }
        return bestMagnitude;
    }

    private record Residual(GenomeAddress address, boolean negative,
                            double targetSaturation, double absoluteError) {}

    private record MicroResidual(GenomeAddress address, boolean negative,
                                 double currentSaturation, double targetSaturation,
                                 double absoluteError) {}

    private record MicroGene(int magnitude, int expression, double remainingError) {}

    private static GenePlan bestPlan(DirectContributionModel model, GenomeAddress address, double target,
                                     int minCount, int maxCount, boolean negative) {
        if (target <= 0.0) return GenePlan.empty();
        GenePlan best = null;
        for (int count = minCount; count <= maxCount; count++) {
            for (int commonMagnitude = 0; commonMagnitude <= 127; commonMagnitude++) {
                double commonU = u(model, address, negative, commonMagnitude);
                double commonSurvival = StrictMath.pow(1.0 - commonU, Math.max(0, count - 1));
                for (int tailMagnitude = 0; tailMagnitude <= 127; tailMagnitude++) {
                    double tailU = u(model, address, negative, tailMagnitude);
                    double achieved = 1.0 - commonSurvival * (1.0 - tailU);
                    double error = StrictMath.abs(achieved - target);
                    if (best == null || error < best.error()) {
                        List<Integer> magnitudes = new ArrayList<>(count);
                        for (int i = 0; i < count - 1; i++) magnitudes.add(commonMagnitude);
                        magnitudes.add(tailMagnitude);
                        best = new GenePlan(List.copyOf(magnitudes), achieved, error);
                        if (error <= 1.0e-12) return best;
                    }
                }
            }
        }
        return best;
    }

    private static double u(DirectContributionModel model, GenomeAddress address,
                            boolean negative, int magnitude) {
        double d = model.baseEffect(address, negative, magnitude, 15);
        return model.saturation(address, StrictMath.abs(d));
    }

    private static int sampleFounderLength(ChromosomeTemplate template, GenomeRandom random) {
        FounderScaffoldTolerance tolerance = template.founderScaffoldTolerance();
        int baseline = template.templateBits().bitLength();
        if (baseline == 0 || tolerance.exactLength()) return baseline;

        double minimumRaw = tolerance.minLengthRatio() * baseline;
        double maximumRaw = tolerance.maxLengthRatio() * baseline;
        int minimum = Math.max(protectedBitCount(template), (int) StrictMath.ceil(minimumRaw));
        int maximum = (int) StrictMath.floor(maximumRaw);
        if (minimum > maximum) {
            throw new IllegalArgumentException("founder scaffold tolerance cannot preserve protected anchor regions");
        }

        while (true) {
            double sampled = baseline * (1.0
                    + tolerance.standardDeviationRatio() * Sampling.standardGaussian(random));
            if (!Double.isFinite(sampled) || sampled < minimumRaw || sampled > maximumRaw) continue;
            long rounded = (long) StrictMath.floor(sampled + 0.5);
            if (rounded < minimum || rounded > maximum || rounded > Integer.MAX_VALUE) continue;
            return (int) rounded;
        }
    }

    private static int protectedBitCount(ChromosomeTemplate template) {
        List<Interval> intervals = new ArrayList<>();
        for (AnchorSeed seed : template.anchors()) {
            intervals.add(new Interval(seed.position(), seed.position() + 48));
        }
        if (template.markerLocus() != null) {
            intervals.add(new Interval(template.markerLocus().first().position(),
                    template.markerLocus().first().position() + 48));
            intervals.add(new Interval(template.markerLocus().second().position(),
                    template.markerLocus().second().position() + 48));
        }
        int total = 0;
        for (Interval interval : MutableScaffold.merge(intervals)) {
            total += interval.end() - interval.start();
        }
        return total;
    }

    private static int weightedChromosome(BackboneDefinition backbone, GenomeRandom random) {
        long total = 0;
        for (ChromosomeTemplate t : backbone.chromosomes()) total += Math.max(1, t.templateBits().bitLength());
        long roll = nextLongBounded(random, total);
        long cursor = 0;
        for (int i = 0; i < backbone.chromosomes().size(); i++) {
            cursor += Math.max(1, backbone.chromosomes().get(i).templateBits().bitLength());
            if (roll < cursor) return i;
        }
        return backbone.chromosomes().size() - 1;
    }

    private static long nextLongBounded(GenomeRandom random, long bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        long r = random.nextLong() >>> 1;
        long m = bound - 1;
        if ((bound & m) == 0) return r & m;
        long u = r;
        while (u + m - (r = u % bound) < 0L) u = random.nextLong() >>> 1;
        return r;
    }

    private static BitSequence randomBits(int length, GenomeRandom random) {
        if (length < 0) throw new IllegalArgumentException("length must be >= 0");
        StringBuilder bits = new StringBuilder(length);
        for (int i = 0; i < length; i++) bits.append(random.nextBoolean() ? '1' : '0');
        return BitSequence.fromBits(bits.toString());
    }

    private static BitSequence nonCodingSpacer(GenomeRandom random, int length) {
        for (int attempt = 0; attempt < 32; attempt++) {
            StringBuilder s = new StringBuilder(length);
            for (int i = 0; i < length; i++) s.append(random.nextBoolean() ? '1' : '0');
            BitSequence candidate = BitSequence.fromBits(s.toString());
            if (!containsMotifLike(candidate, GeneCodecV1.START) && !containsMotifLike(candidate, GeneCodecV1.END)) {
                return candidate;
            }
        }
        return BitSequence.fromBits("0".repeat(length));
    }

    private static boolean containsMotifLike(BitSequence sequence, BitSequence motif) {
        if (sequence.bitLength() < motif.bitLength()) return false;
        for (int i = 0; i <= sequence.bitLength() - motif.bitLength(); i++) {
            int distance = 0;
            for (int j = 0; j < motif.bitLength(); j++) {
                if (sequence.bitAt(i + j) != motif.bitAt(j) && ++distance > 1) break;
            }
            if (distance <= 1) return true;
        }
        return false;
    }

    private static <T> void shuffle(List<T> values, GenomeRandom random) {
        for (int i = values.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = values.get(i);
            values.set(i, values.get(j));
            values.set(j, tmp);
        }
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

    private record GeneSpec(GenomeAddress address, boolean negative, int magnitude) {}
    private record GenePlan(List<Integer> magnitudes, double achieved, double error) {
        static GenePlan empty() { return new GenePlan(List.of(), 0.0, 0.0); }
    }

    private static final class MutableScaffold {
        private BitSequence bits;
        private List<Interval> backboneProtectedIntervals;
        private List<GeneratedInterval> generatedIntervals;

        MutableScaffold(ChromosomeTemplate template) {
            this.bits = template.templateBits();
            List<Interval> intervals = new ArrayList<>();
            for (AnchorSeed seed : template.anchors()) {
                intervals.add(new Interval(seed.position(), seed.position() + 48));
            }
            if (template.markerLocus() != null) {
                intervals.add(new Interval(template.markerLocus().first().position(),
                        template.markerLocus().first().position() + 48));
                intervals.add(new Interval(template.markerLocus().second().position(),
                        template.markerLocus().second().position() + 48));
            }
            this.backboneProtectedIntervals = merge(intervals);
            this.generatedIntervals = new ArrayList<>();
        }

        MutableScaffold(ChromosomeTemplate template, int targetLength, GenomeRandom random) {
            this(template);
            resizeTo(targetLength, random);
        }

        private MutableScaffold(BitSequence bits,
                                List<Interval> backboneProtectedIntervals,
                                List<GeneratedInterval> generatedIntervals) {
            this.bits = bits;
            this.backboneProtectedIntervals = new ArrayList<>(backboneProtectedIntervals);
            this.generatedIntervals = new ArrayList<>(generatedIntervals);
        }

        MutableScaffold copy() {
            return new MutableScaffold(bits, backboneProtectedIntervals, generatedIntervals);
        }

        BitSequence bits() { return bits; }

        private void resizeTo(int targetLength, GenomeRandom random) {
            if (targetLength < 0) throw new IllegalArgumentException("targetLength must be >= 0");
            if (targetLength == bits.bitLength()) return;
            if (targetLength > bits.bitLength()) {
                insertBackground(randomBits(targetLength - bits.bitLength(), random), random);
                return;
            }
            while (bits.bitLength() > targetLength) {
                int excess = bits.bitLength() - targetLength;
                List<Interval> gaps = removableIntervals();
                int total = gaps.stream().mapToInt(x -> x.end() - x.start()).sum();
                if (total < excess) {
                    throw new IllegalArgumentException("founder target length cannot preserve protected anchor regions");
                }
                int roll = random.nextInt(total);
                Interval selected = null;
                for (Interval gap : gaps) {
                    int length = gap.end() - gap.start();
                    if (roll < length) {
                        selected = gap;
                        break;
                    }
                    roll -= length;
                }
                if (selected == null) throw new IllegalStateException("failed to choose removable scaffold interval");
                int available = selected.end() - selected.start();
                int amount = Math.min(excess, available);
                int from = selected.start();
                if (available > amount) {
                    from += random.nextInt(available - amount + 1);
                }
                deleteBackground(from, from + amount);
            }
        }

        private void insertBackground(BitSequence background, GenomeRandom random) {
            if (background.bitLength() == 0) return;
            int position = randomSafeBoundary(random);
            bits = bits.insert(position, background);
            shiftAfterInsertion(position, background.bitLength());
        }

        private void deleteBackground(int from, int to) {
            if (from < 0 || to <= from || to > bits.bitLength()) {
                throw new IllegalArgumentException("invalid background deletion");
            }
            for (Interval interval : protectedRegions()) {
                if (Math.max(from, interval.start()) < Math.min(to, interval.end())) {
                    throw new IllegalArgumentException("background deletion overlaps protected region");
                }
            }
            bits = bits.delete(from, to);
            shiftAfterDeletion(from, to);
        }

        private List<Interval> removableIntervals() {
            List<Interval> gaps = new ArrayList<>();
            int cursor = 0;
            for (Interval interval : protectedRegions()) {
                if (cursor < interval.start()) gaps.add(new Interval(cursor, interval.start()));
                cursor = Math.max(cursor, interval.end());
            }
            if (cursor < bits.bitLength()) gaps.add(new Interval(cursor, bits.bitLength()));
            return gaps;
        }

        private List<Interval> protectedRegions() {
            List<Interval> all = new ArrayList<>(backboneProtectedIntervals.size() + generatedIntervals.size());
            all.addAll(backboneProtectedIntervals);
            for (GeneratedInterval generated : generatedIntervals) {
                all.add(new Interval(generated.start(), generated.end()));
            }
            return merge(all);
        }

        private int randomSafeBoundary(GenomeRandom random) {
            int count = 0;
            for (int p = 0; p <= bits.bitLength(); p++) if (safe(p)) count++;
            if (count == 0) return bits.bitLength();
            int pick = random.nextInt(count);
            for (int p = 0; p <= bits.bitLength(); p++) {
                if (!safe(p)) continue;
                if (pick-- == 0) return p;
            }
            throw new IllegalStateException("failed to choose safe scaffold boundary");
        }

        void insertAtSafeBoundary(BitSequence block, GenomeRandom random) {
            insertGeneratedAtSafeBoundary(block, random, null);
        }

        int insertGeneratedAtSafeBoundary(BitSequence block, GenomeRandom random, GenomeAddress owner) {
            Objects.requireNonNull(block, "block");
            if (block.bitLength() == 0) throw new IllegalArgumentException("generated block must not be empty");
            int position = randomSafeBoundary(random);
            bits = bits.insert(position, block);
            shiftAfterInsertion(position, block.bitLength());
            generatedIntervals.add(new GeneratedInterval(position, position + block.bitLength(), owner));
            return position;
        }

        void removeGeneratedBlocks(GenomeAddress owner) {
            List<GeneratedInterval> targets = generatedIntervals.stream()
                    .filter(interval -> Objects.equals(interval.owner(), owner))
                    .sorted(Comparator.comparingInt(GeneratedInterval::start).reversed())
                    .toList();
            for (GeneratedInterval target : targets) {
                generatedIntervals.remove(target);
                bits = bits.delete(target.start(), target.end());
                shiftAfterDeletion(target.start(), target.end());
            }
        }

        private void shiftAfterInsertion(int position, int delta) {
            List<Interval> shiftedBackbone = new ArrayList<>(backboneProtectedIntervals.size());
            for (Interval interval : backboneProtectedIntervals) {
                shiftedBackbone.add(position <= interval.start()
                        ? new Interval(interval.start() + delta, interval.end() + delta)
                        : interval);
            }
            backboneProtectedIntervals = merge(shiftedBackbone);

            List<GeneratedInterval> shiftedGenerated = new ArrayList<>(generatedIntervals.size());
            for (GeneratedInterval interval : generatedIntervals) {
                shiftedGenerated.add(position <= interval.start()
                        ? new GeneratedInterval(interval.start() + delta, interval.end() + delta, interval.owner())
                        : interval);
            }
            generatedIntervals = shiftedGenerated;
        }

        private void shiftAfterDeletion(int from, int to) {
            int delta = to - from;
            List<Interval> shiftedBackbone = new ArrayList<>(backboneProtectedIntervals.size());
            for (Interval interval : backboneProtectedIntervals) {
                if (interval.start() >= to) {
                    shiftedBackbone.add(new Interval(interval.start() - delta, interval.end() - delta));
                } else {
                    shiftedBackbone.add(interval);
                }
            }
            backboneProtectedIntervals = merge(shiftedBackbone);

            List<GeneratedInterval> shiftedGenerated = new ArrayList<>(generatedIntervals.size());
            for (GeneratedInterval interval : generatedIntervals) {
                if (interval.start() >= to) {
                    shiftedGenerated.add(new GeneratedInterval(
                            interval.start() - delta, interval.end() - delta, interval.owner()));
                } else {
                    shiftedGenerated.add(interval);
                }
            }
            generatedIntervals = shiftedGenerated;
        }

        private boolean safe(int position) {
            for (Interval interval : protectedRegions()) {
                if (position > interval.start() && position < interval.end()) return false;
            }
            return true;
        }

        private static List<Interval> merge(List<Interval> input) {
            if (input.isEmpty()) return List.of();
            input.sort(Comparator.comparingInt(Interval::start));
            List<Interval> out = new ArrayList<>();
            Interval current = input.getFirst();
            for (int i = 1; i < input.size(); i++) {
                Interval next = input.get(i);
                if (next.start() <= current.end()) {
                    current = new Interval(current.start(), Math.max(current.end(), next.end()));
                } else {
                    out.add(current);
                    current = next;
                }
            }
            out.add(current);
            return List.copyOf(out);
        }
    }

    private record GeneratedInterval(int start, int end, GenomeAddress owner) {}
    private record Interval(int start, int end) {}
}
