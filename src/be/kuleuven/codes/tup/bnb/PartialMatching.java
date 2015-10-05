package be.kuleuven.codes.tup.bnb;

import be.kuleuven.codes.tup.heuristic.assignment.*;
import be.kuleuven.codes.tup.model.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * This class implements the Memoization scheme for the Partial Matchings.
 *
 * @author Tulio Toffolo
 */
public class PartialMatching {

    /**
     * This static variable stores the initial size of the hash map. Larger
     * values may improve performance, but will require more memory.
     */
    private static final int HASH_INITIAL_SIZE = ( int ) 1e6;

    /**
     * This static variable stores the maximum size of the hash limit. Be
     * careful when setting its value, as memory errors are likely to occur.
     */
    private static final int HASH_SIZE_LIMIT = ( int ) 5e8;

    public final Problem problem;

    private AtomicLong counter = new AtomicLong(0);
    private Map<Long, Integer> matchingMemoization = new ConcurrentHashMap<>(HASH_INITIAL_SIZE, 1.0f, 1);


    /**
     * Instantiates a new Partial Matching.
     *
     * @param problem the problem reference.
     */
    public PartialMatching(Problem problem) {
        this.problem = problem;
    }


    /**
     * Gets the distance of a "partial" matching problem. If the matching was
     * previously solved, it is returned in O(1). Otherwise, it is calculated
     * and stored before it is returned.
     *
     * @param round    the round of the matching subproblem.
     * @param umpireId the umpire considered.
     * @param used     boolean array indicating the assigned games in the
     *                 current round.
     * @param usedNext boolean array indicating the assigned games in the next
     *                 round.
     * @return the distance of the solution of the "partial" matching problem.
     */
    public int getDistance(int round, int umpireId, boolean used[], boolean usedNext[]) {
        long hashCode = 0;
        long multiplier = 1;
        for (int i = used.length - 1; i >= 0; i--) {
            hashCode += ((used[i] ? 1 : 0) + (usedNext[i] ? 2 : 0)) * multiplier;
            multiplier *= 10;
        }
        hashCode += (round + 1) * multiplier;

        Integer result = matchingMemoization.get(hashCode);
        if (result != null) {
            return result;
        }
        else if (counter.get() >= HASH_SIZE_LIMIT) {
            return 0;
        }

        HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm();

        int map[] = new int[problem.nRounds];
        int mapNext[] = new int[problem.nRounds];
        int idx = 0, idxNext = 0;
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) map[idx++] = i;
            if (!usedNext[i]) mapNext[idxNext++] = i;
        }

        int[][] costMatrix = new int[problem.nUmpires - (umpireId + 1)][problem.nUmpires - (umpireId + 1)];
        for (idx = 0; idx < costMatrix.length; idx++) {
            int game = round * problem.nUmpires + map[idx];
            for (idxNext = 0; idxNext < costMatrix[idx].length; idxNext++) {
                int gameNext = (round + 1) * problem.nUmpires + mapNext[idxNext];

                if (problem.q2 > 1 && (problem.games[game][0] == problem.games[gameNext][0]
                  || problem.games[game][1] == problem.games[gameNext][1]
                  || problem.games[game][0] == problem.games[gameNext][1]
                  || problem.games[game][1] == problem.games[gameNext][0])) {
                    costMatrix[idx][idxNext] = Integer.MAX_VALUE;
                }
                else if (problem.q1 > 1 && problem.games[game][0] == problem.games[gameNext][0]) {
                    costMatrix[idx][idxNext] = Integer.MAX_VALUE;
                }
                else {
                    costMatrix[idx][idxNext] = problem.dist[problem.games[game][0] - 1][problem.games[gameNext][0] - 1];
                }
            }
        }

        int distance = 0;
        int[][] assignments = hungarianAlgorithm.computeAssignments(costMatrix);
        for (int[] a : assignments) {
            int game = round * problem.nUmpires + map[a[0]];
            int gameNext = (round + 1) * problem.nUmpires + mapNext[a[1]];
            distance += problem.dist[problem.games[game][0] - 1][problem.games[gameNext][0] - 1];
        }

        counter.incrementAndGet();
        matchingMemoization.put(hashCode, distance);

        return distance;
    }
}
