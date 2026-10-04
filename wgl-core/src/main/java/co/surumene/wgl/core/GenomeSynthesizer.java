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
            if (e.getKey().isRegulation() || !profile.isDefinedAddress(e.getKey())
                    || !Double.isFinite(e.getValue()) || e.getValue() < 0 || e.getValue() > 1) {
                return new SynthesisResult.Failure(SynthesisFailureReason.INVALID_TARGET,
                        "invalid continuous target " + e.getKey());
            }
        }

        for (int attempt = 0; attempt < context.genomeRetries(); attempt++) {
            List<MutableScaffold> a = new ArrayList<>();
            List<MutableScaffold> b = new ArrayList<>();
            for (ChromosomeTemplate template : backbone.chromosomes()) {
                a.add(new MutableScaffold(template));
                b.add(new MutableScaffold(template));
            }

            boolean unsatisfiable = false;
            for (var e : new TreeMap<>(target.continuousTargets()).entrySet()) {
                GenomeAddress address = e.getKey();
                double targetScore = e.getValue();
                if (targetScore == 0.0) continue;

                double cancellationDraw = context.cancellationMin()
                        + (context.cancellationMax() - context.cancellationMin()) * random.nextDouble();
                double cancellation = Math.min(cancellationDraw, Math.max(0.0, 0.98 - targetScore));
                double negativeSurvival = 1.0 - cancellation;
                double positiveTarget = targetScore / negativeSurvival;

                DirectContributionModel model = profile.contributionModel(address);
                GenePlan positive = bestPlan(model, address, positiveTarget,
                        context.minPositiveGenes(), context.maxPositiveGenes());
                GenePlan negative = cancellation <= 0.0 ? GenePlan.empty()
                        : bestPlan(model, address, cancellation, 1, Math.max(1, context.maxPositiveGenes() / 2));
                if (positive == null || negative == null) {
                    unsatisfiable = true;
                    break;
                }

                List<GeneSpec> genes = new ArrayList<>(positive.magnitudes().size() + negative.magnitudes().size());
                for (int magnitude : positive.magnitudes()) genes.add(new GeneSpec(address, false, magnitude));
                for (int magnitude : negative.magnitudes()) genes.add(new GeneSpec(address, true, magnitude));
                shuffle(genes, random);

                for (GeneSpec spec : genes) {
                    BitSequence gene = GeneCodecV1.encode(spec.address(), spec.negative(), spec.magnitude(), 15,
                            BitSequence.empty());
                    BitSequence spacer = nonCodingSpacer(random, 8 + random.nextInt(25));
                    BitSequence block = spacer.concat(gene);
                    int chromosome = weightedChromosome(backbone, random);
                    boolean haplotypeA = random.nextBoolean();
                    MutableScaffold scaffold = haplotypeA ? a.get(chromosome) : b.get(chromosome);
                    scaffold.insertAtSafeBoundary(block, random);
                }
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
            if (!safeDiploid(backbone, genome)) continue;

            DecodeResult<?> decoded = decoder.decode(profile, genome);
            if (target.isSatisfied(decoded.decodedGenome(), config.synthesizer().convergenceTolerance())) {
                return new SynthesisResult.Success(genome, decoded);
            }

            SynthesisResult.Success adjusted = locallyAdjust(
                    profile, backbone, target, a, b, decoded, random);
            if (adjusted != null) return adjusted;
        }
        return new SynthesisResult.Failure(SynthesisFailureReason.CONVERGENCE_LIMIT,
                "target did not converge within configured retries");
    }

    private SynthesisResult.Success locallyAdjust(GenomeProfile<?> profile,
                                                        BackboneDefinition backbone,
                                                        SynthesisTarget target,
                                                        List<MutableScaffold> a,
                                                        List<MutableScaffold> b,
                                                        DecodeResult<?> initial,
                                                        GenomeRandom random) {
        DecodeResult<?> decoded = initial;
        double tolerance = config.synthesizer().convergenceTolerance();
        double maxRatio = config.synthesizer().microCorrectionMaxRatio();

        for (int iteration = 0; iteration < config.synthesizer().localAdjustmentMaxIterations(); iteration++) {
            if (target.isSatisfied(decoded.decodedGenome(), tolerance)) {
                DiploidGenome genome = currentGenome(backbone, a, b);
                return new SynthesisResult.Success(genome, decoded);
            }

            Residual residual = largestResidual(target, decoded.decodedGenome(), tolerance);
            if (residual == null) break;

            GenomeAddress address = residual.address();
            AddressAggregate aggregate = decoded.decodedGenome().aggregate(address);
            boolean negative = residual.targetScore() < aggregate.score();
            double baseAmount = Math.max(residual.targetScore(), aggregate.score());
            double stepLimit = baseAmount * maxRatio;
            if (!(stepLimit > 0.0)) stepLimit = Math.min(residual.absoluteError(), tolerance);
            double desiredDelta = Math.min(residual.absoluteError(), stepLimit);
            double desiredScore = negative
                    ? aggregate.score() - desiredDelta
                    : aggregate.score() + desiredDelta;

            DirectContributionModel model = profile.contributionModel(address);
            int magnitude = bestAdjustmentMagnitude(model, address, aggregate, desiredScore, negative);
            if (magnitude <= 0) break;

            BitSequence gene = GeneCodecV1.encode(address, negative, magnitude, 15, BitSequence.empty());
            BitSequence spacer = nonCodingSpacer(random, 8 + random.nextInt(25));
            int chromosome = weightedChromosome(backbone, random);
            MutableScaffold scaffold = random.nextBoolean() ? a.get(chromosome) : b.get(chromosome);
            scaffold.insertAtSafeBoundary(spacer.concat(gene), random);

            DiploidGenome genome = currentGenome(backbone, a, b);
            if (!safeDiploid(backbone, genome)) break;
            DecodeResult<?> next = decoder.decode(profile, genome);
            double nextError = totalError(target, next.decodedGenome());
            double currentError = totalError(target, decoded.decodedGenome());
            if (!(nextError < currentError)) break;
            decoded = next;
        }

        DiploidGenome genome = currentGenome(backbone, a, b);
        if (!safeDiploid(backbone, genome)) return null;
        DecodeResult<?> finalDecoded = decoder.decode(profile, genome);
        return target.isSatisfied(finalDecoded.decodedGenome(), tolerance)
                ? new SynthesisResult.Success(genome, finalDecoded) : null;
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

    private static Residual largestResidual(SynthesisTarget target, DecodedGenome decoded, double tolerance) {
        Residual best = null;
        for (var entry : new TreeMap<>(target.continuousTargets()).entrySet()) {
            double current = decoded.aggregate(entry.getKey()).score();
            double error = StrictMath.abs(entry.getValue() - current);
            if (error <= tolerance) continue;
            if (best == null || error > best.absoluteError()) {
                best = new Residual(entry.getKey(), entry.getValue(), error);
            }
        }
        return best;
    }

    private static double totalError(SynthesisTarget target, DecodedGenome decoded) {
        double total = 0.0;
        for (var entry : target.continuousTargets().entrySet()) {
            total += StrictMath.abs(entry.getValue() - decoded.aggregate(entry.getKey()).score());
        }
        return total;
    }

    private static int bestAdjustmentMagnitude(DirectContributionModel model,
                                               GenomeAddress address,
                                               AddressAggregate aggregate,
                                               double desiredScore,
                                               boolean negative) {
        int bestMagnitude = 0;
        double bestError = StrictMath.abs(aggregate.score() - desiredScore);
        for (int magnitude = 1; magnitude <= 127; magnitude++) {
            double candidateU = u(model, address, magnitude);
            double predicted;
            if (negative) {
                predicted = aggregate.positiveSaturation()
                        * aggregate.negativeSurvival() * (1.0 - candidateU);
            } else {
                double positive = 1.0
                        - (1.0 - aggregate.positiveSaturation()) * (1.0 - candidateU);
                predicted = positive * aggregate.negativeSurvival();
            }
            double error = StrictMath.abs(predicted - desiredScore);
            if (error < bestError) {
                bestError = error;
                bestMagnitude = magnitude;
            }
        }
        return bestMagnitude;
    }

    private record Residual(GenomeAddress address, double targetScore, double absoluteError) {}

    private static GenePlan bestPlan(DirectContributionModel model, GenomeAddress address, double target,
                                     int minCount, int maxCount) {
        if (target <= 0.0) return GenePlan.empty();
        GenePlan best = null;
        for (int count = minCount; count <= maxCount; count++) {
            for (int commonMagnitude = 0; commonMagnitude <= 127; commonMagnitude++) {
                double commonU = u(model, address, commonMagnitude);
                double commonSurvival = StrictMath.pow(1.0 - commonU, Math.max(0, count - 1));
                for (int tailMagnitude = 0; tailMagnitude <= 127; tailMagnitude++) {
                    double tailU = u(model, address, tailMagnitude);
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

    private static double u(DirectContributionModel model, GenomeAddress address, int magnitude) {
        double d = model.baseEffect(address, false, magnitude, 15);
        return model.saturation(address, StrictMath.abs(d));
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
        private List<Interval> protectedIntervals;

        MutableScaffold(ChromosomeTemplate template) {
            this.bits = template.templateBits();
            List<Interval> intervals = new ArrayList<>();
            for (AnchorSeed seed : template.anchors()) intervals.add(new Interval(seed.position(), seed.position() + 48));
            if (template.markerLocus() != null) {
                intervals.add(new Interval(template.markerLocus().first().position(), template.markerLocus().first().position() + 48));
                intervals.add(new Interval(template.markerLocus().second().position(), template.markerLocus().second().position() + 48));
            }
            this.protectedIntervals = merge(intervals);
        }

        BitSequence bits() { return bits; }

        void insertAtSafeBoundary(BitSequence block, GenomeRandom random) {
            int position = bits.bitLength();
            for (int attempt = 0; attempt < 64; attempt++) {
                int candidate = random.nextInt(bits.bitLength() + 1);
                if (safe(candidate)) { position = candidate; break; }
            }
            bits = bits.insert(position, block);
            int delta = block.bitLength();
            List<Interval> shifted = new ArrayList<>(protectedIntervals.size());
            for (Interval interval : protectedIntervals) {
                if (position <= interval.start()) shifted.add(new Interval(interval.start() + delta, interval.end() + delta));
                else shifted.add(interval);
            }
            protectedIntervals = shifted;
        }

        private boolean safe(int position) {
            for (Interval interval : protectedIntervals) {
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
                if (next.start() <= current.end()) current = new Interval(current.start(), Math.max(current.end(), next.end()));
                else { out.add(current); current = next; }
            }
            out.add(current);
            return List.copyOf(out);
        }
    }

    private record Interval(int start, int end) {}
}
