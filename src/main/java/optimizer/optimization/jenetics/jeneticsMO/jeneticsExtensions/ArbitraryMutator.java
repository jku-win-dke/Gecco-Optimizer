package optimizer.optimization.jenetics.jeneticsMO.jeneticsExtensions;

import io.jenetics.Chromosome;
import io.jenetics.Gene;
import io.jenetics.Mutator;
import io.jenetics.MutatorResult;
import io.jenetics.internal.math.Subset;
import io.jenetics.util.MSeq;

import java.util.random.RandomGenerator;

public class ArbitraryMutator<
        G extends Gene<?, G>,
        C extends Comparable<? super C>
        >
        extends Mutator<G, C> {

    public ArbitraryMutator(final double probability) {
        super(probability);
    }

    public ArbitraryMutator() {
        this(DEFAULT_ALTER_PROBABILITY);
    }

    @Override
    protected MutatorResult<Chromosome<G>> mutate(
            final Chromosome<G> chromosome,
            final double p,
            final RandomGenerator random
    ) {
        final MutatorResult<Chromosome<G>> result;
        if(chromosome.length() > 1) {
            final int[] points = Subset.next(chromosome.length() + 1, 2, random);
            final MSeq<G> genes = MSeq.of(chromosome);

            genes.subSeq(points[0], points[1]).shuffle();

            result = new MutatorResult<>(
                    chromosome.newInstance(genes.toISeq()),
                    points[1] - points[0]
            );
        } else {
            result = new MutatorResult<>(chromosome, 0);
        }
        return result;
    }
}
