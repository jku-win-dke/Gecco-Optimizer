package at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.jeneticsExtensions;

import io.jenetics.Chromosome;
import io.jenetics.Gene;
import io.jenetics.Mutator;
import io.jenetics.MutatorResult;
import io.jenetics.internal.math.Subset;
import io.jenetics.util.MSeq;

import java.util.random.RandomGenerator;

public class ShiftMutator<
        G extends Gene<?, G>,
        C extends Comparable<? super C>
        >
        extends Mutator<G, C> {

    public ShiftMutator(final double probability) {
        super(probability);
    }

    public ShiftMutator() {
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
            final int[] points = Subset.next(chromosome.length() + 1, 3, random);
            final MSeq<G> genes = MSeq.of(chromosome);
            MSeq<G> firstSeq = genes.subSeq(points[0], points[1]).copy();
            int difOne = points[2] - points[1];
            MSeq<G> secondSeq = genes.subSeq(points[1], points[2]).copy();
            int difTwo = points[1] - points[0];
            int i = 0;
            for(G g : firstSeq) {
                genes.set(points[0]+i+difOne, g);
                i++;
            }
            i = 0;
            for(G g : secondSeq) {
                genes.set(points[1]+i-difTwo,g);
                i++;
            }

            result =  new MutatorResult<>(
                    chromosome.newInstance(genes.toISeq()),
                    points[2] - points[0] - 1
            );
        } else {
            result = new MutatorResult<>(chromosome, 0);
        }
        return result;
    }
}
