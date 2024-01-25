package at.jku.dke.harmonic.optimizer.optimization.jenetics.jeneticsMO.jeneticsExtensions;

import io.jenetics.Gene;
import io.jenetics.Optimize;
import io.jenetics.Phenotype;
import io.jenetics.Selector;
import io.jenetics.ext.moea.ElementDistance;
import io.jenetics.ext.moea.Vec;
import io.jenetics.util.ISeq;
import io.jenetics.util.RandomRegistry;
import io.jenetics.util.Seq;

import java.util.*;
import java.util.random.RandomGenerator;

import static java.util.Objects.requireNonNull;

public class SPEA2Selector<
        G extends Gene<?, G>,
        C extends Comparable<? super C>
        >
        implements Selector<G, C> {

    private final Comparator<Phenotype<G, C>> _dominance;
    private final ElementDistance<Phenotype<G, C>> _distance;

    private Seq<Phenotype<G, C>> archive;
    private int archiveSize;

    /**
     * Creates a new {@code NSGA2Selector} with the functions needed for
     * handling the multi-objective result type {@code C}. For the {@link Vec}
     * classes, a selector is created like in the following example:
     *
     * @param dominance the pareto dominance comparator
     * @param distance  the vector element distance
     */
    public SPEA2Selector(
            final Comparator<? super C> dominance,
            final ElementDistance<? super C> distance
    ) {
        requireNonNull(dominance);
        requireNonNull(distance);

        _dominance = (a, b) -> dominance.compare(a.fitness(), b.fitness());
        _distance = distance.map(Phenotype::fitness);
        archiveSize = 50;
    }

    public SPEA2Selector(
            final Comparator<? super C> dominance,
            final ElementDistance<? super C> distance,
            final int archiveSize
    ) {
        this(dominance, distance);
        this.archiveSize = archiveSize;
    }


    @Override
    public ISeq<Phenotype<G, C>> select(
            final Seq<Phenotype<G, C>> seq,
            final int count,
            final Optimize optimize) {
        try {
            Seq<Phenotype<G, C>> combinedSequence = seq;
            if (archive != null) {
                combinedSequence = combinedSequence.append(archive);
            }
            List<SPEA2PhenotypeWrapper<G, C>> wrapperList = combinedSequence.stream()
                    .map(SPEA2PhenotypeWrapper::new).toList();
            calculateStrengthValues(wrapperList, optimize);
            calculateFitnessValue(wrapperList);
            setArchive(wrapperList);
            return applyTournamentSelector(wrapperList, count);
        } catch(Exception e) {
            System.out.println(e.getMessage());
            throw e;
        }
    }

    private void calculateStrengthValues(List<SPEA2PhenotypeWrapper<G, C>> wrapperList, Optimize optimize) {
        Comparator<? super Phenotype<G, C>> dominance = optimize == Optimize.MAXIMUM ? this._dominance : this._dominance.reversed();
        for (SPEA2PhenotypeWrapper<G, C> x : wrapperList) {
            for (SPEA2PhenotypeWrapper<G, C> y : wrapperList) {
                if (dominance.compare(x.phenotype, y.phenotype) > 0) {
                    x.dominatingPhenotypes.add(y);
                    x.strengthValue++;
                }
            }
        }
    }

    private void calculateFitnessValue(List<SPEA2PhenotypeWrapper<G, C>> wrapperList) {
        for (SPEA2PhenotypeWrapper<G, C> wrapper : wrapperList) {
            wrapper.dominatingPhenotypes
                    .forEach(w -> w.fitnessValue += wrapper.strengthValue);
        }
    }

    private void setArchive(List<SPEA2PhenotypeWrapper<G, C>> wrapperList) {
        List<Phenotype<G, C>> archiveList = new ArrayList<>(wrapperList.stream()
                .filter(w -> w.fitnessValue == 0)
                .map(w -> w.phenotype).toList());

        if (archiveList.size() < this.archiveSize) {
            archiveList.addAll(wrapperList.stream()
                    .filter(w -> w.fitnessValue > 0)
                    .sorted(Comparator.comparingInt(w -> w.fitnessValue))
                    .limit(this.archiveSize - archiveList.size())
                    .map(w -> w.phenotype).toList());
        } else if (archiveList.size() > this.archiveSize) {
            while (archiveList.size() > this.archiveSize) {
                Map<Phenotype<G, C>, Double> distances = getDistances(archiveList);
                archiveList.remove(distances.entrySet().stream()
                        .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                        .map(Map.Entry::getKey)
                        .findFirst().get());
            }
        }
        this.archive = Seq.of(archiveList);
    }

    private Map<Phenotype<G, C>, Double> getDistances(List<Phenotype<G, C>> archiveList) {
        Map<Phenotype<G, C>, Double> distances = new HashMap<>();
        for (Phenotype<G, C> p1 : archiveList) {
            double firstDist = 0;
            double secondDist = 0;
            for (Phenotype<G, C> p2 : archiveList) {
                if (p1 != p2) {
                    double distance = _distance.distance(p1, p2, 0) + _distance.distance(p1, p2, 1);
                    if (distance > firstDist) {
                        secondDist = firstDist;
                        firstDist = distance;
                    } else if (distance > secondDist) {
                        secondDist = distance;
                    }
                }
            }
            distances.put(p1, (firstDist + secondDist) / 2);
        }
        return distances;
    }

    private ISeq<Phenotype<G, C>> applyTournamentSelector(List<SPEA2PhenotypeWrapper<G, C>> wrapperList, int count) {
        RandomGenerator random = RandomRegistry.random();
        List<Phenotype<G, C>> phenotypes = new LinkedList<>();
        for (int i = 0; i < count; i++) {
            SPEA2PhenotypeWrapper<G, C> first = wrapperList.get(random.nextInt(wrapperList.size()));
            SPEA2PhenotypeWrapper<G, C> second = wrapperList.get(random.nextInt(wrapperList.size()));
            phenotypes.add(first.fitnessValue < second.fitnessValue ? first.phenotype : second.phenotype);
        }
        return ISeq.of(phenotypes);
    }

    private static class SPEA2PhenotypeWrapper<G extends Gene<?, G>, C extends Comparable<? super C>> {

        Phenotype<G, C> phenotype;
        int strengthValue;
        int fitnessValue;
        List<SPEA2PhenotypeWrapper<G, C>> dominatingPhenotypes;

        SPEA2PhenotypeWrapper(Phenotype<G, C> phenotype) {
            this.phenotype = phenotype;
            strengthValue = 0;
            fitnessValue = 0;
            dominatingPhenotypes = new LinkedList<>();
        }
    }
}
