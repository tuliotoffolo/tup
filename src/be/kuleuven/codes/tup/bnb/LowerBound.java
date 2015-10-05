package be.kuleuven.codes.tup.bnb;

import be.kuleuven.codes.tup.heuristic.assignment.*;
import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;
import be.kuleuven.codes.tup.thread.*;

import java.util.*;

/**
 * This class represents a Lower bound.
 *
 * @author Tulio Toffolo
 */
public class LowerBound {

    public final BranchAndBound solver;
    public final Problem problem;
    public final EdgePriority edgePriority;
    public final PartialMatching partialMatching;

    private final Bounds bounds;

    private ThreadExecutor threadExecutor;

    /**
     * Instantiates a new lower bound calculator for the branch-and-bound solver
     * passed as parameter.
     *
     * @param solver the branch-and-bound solver that will query the lower
     *               bounds.
     */
    public LowerBound(BranchAndBound solver) {
        this.solver = solver;

        this.problem = solver.problem;
        this.edgePriority = solver.edgePriority;
        this.partialMatching = solver.partialMatching;
        this.bounds = new Bounds(problem.nRounds);

        // initializing the lower bound with the solutions of the simple 2-round problems
        for (int i = this.problem.nRounds - 2; i >= 0; i--) {
            bounds.matching[i] = calculateMatching(i);
            bounds.set(i, i + 1, bounds.matching[i]);
        }
    }

    /**
     * Gets the current best lower bound for the {@param firstRound} until the
     * last round of the problem.
     *
     * @param firstRound the first round.
     * @return the lower bound value from {@param firstRound} till the last
     * round of the problem.
     */
    public int getLB(int firstRound) {
        return bounds.get(firstRound, problem.nRounds - 1);
    }

    /**
     * Gets the current best lower bound for the sub-problem given by rounds
     * [{@param firstRound},{@param lastRound}].
     *
     * @param firstRound the first round.
     * @param lastRound  the last round.
     * @return the lower bound value between {@param firstRound} and {@param
     * lastRound}.
     */
    public int getLB(int firstRound, int lastRound) {
        return bounds.get(firstRound, lastRound);
    }

    /**
     * Executes the lower bounds calculation.
     *
     * @param maxThreads     the maximum threads allowed.
     * @param maxLimitMillis the maximum time (in milliseconds) in which the
     *                       lower bound can still be running; notice that this
     *                       procedure can be executed while System.currentTimeMillis()
     *                       is smaller than {@param maxLimitMillis}.
     * @param useTimeWindows boolean value that is true if the calculation
     *                       should use time windows and false otherwise.
     */
    public void solve(int maxThreads, long maxLimitMillis, boolean useTimeWindows) {
        this.threadExecutor = maxThreads > 1 ? new ThreadExecutor(maxThreads) : new SequentialExecutor();

        FuturePool futurePool = new FuturePool(threadExecutor);
        List<Runnable> threads = new LinkedList<>();

        if (!useTimeWindows) {
            for (int size = 2; size < problem.nRounds; size++) {
                if (Thread.currentThread().isInterrupted()) break;

                final int firstRound = problem.nRounds - 1 - size;
                final int lastRound = problem.nRounds - 1;
                threads.add(() -> {
                    Thread.currentThread().setName(String.format("lb(%d,%d) :: recurse()", firstRound, lastRound));
                    solveSubproblem(maxLimitMillis, firstRound, lastRound);
                });
            }
        }

        else {
            Set<String> cache = new HashSet<>();

            mainLoop:
            for (int size = 2; size < problem.nRounds; size++) {
                int start = problem.nRounds - 1 - size;
                int end = start + size;

                while (start >= 0) {
                    for (int r1 = end - 2; r1 >= start; r1--) {
                        if (Thread.currentThread().isInterrupted())
                            break mainLoop;

                        if (cache.contains(r1 + "," + end))
                            continue;

                        cache.add(r1 + "," + end);
                        final int firstRound = r1;
                        final int lastRound = end;
                        threads.add(() -> {
                            Thread.currentThread().setName(String.format("lb(%d,%d) :: recurse()", firstRound, lastRound));
                            solveSubproblem(maxLimitMillis, firstRound, lastRound);
                        });
                    }
                    if (start == 0) break;
                    end = start;
                    start = Math.max(start - size, 0);
                }
            }
        }

        if (!Thread.currentThread().isInterrupted())
            futurePool.addAll(threadExecutor.submitAll(threads));
        futurePool.join();
        threadExecutor.shutdownNow();
    }

    /**
     * Solves a specific subproblem.
     *
     * @param maxTimeMillis the maximum time (in milliseconds) in which the
     *                      lower bound can still be running; notice that this
     *                      procedure can be executed while System.currentTimeMillis()
     *                      is smaller than {@param maxLimitMillis}.
     * @param firstRound    the first round of the subproblem.
     * @param lastRound     the last round of the subproblem.
     */
    public void solveSubproblem(long maxTimeMillis, int firstRound, int lastRound) {
        BranchAndBoundPartial bnb = new BranchAndBoundPartial(problem, edgePriority, partialMatching, firstRound, lastRound);
        PartialSolution solution = bnb.solve(this, Integer.MAX_VALUE, threadExecutor, maxTimeMillis);

        if (Thread.currentThread().isInterrupted()) return;

        if (solution == null)
            return;

        int newLB = solution.getDistance();
        int delta = newLB - bounds.get(firstRound, lastRound);

        if (delta > 0) {
            bounds.set(firstRound, lastRound, newLB);
            String info = String.format("# round %02d-%02d: lb improved %d", firstRound, lastRound, delta);
            solver.printStatus("lb", solver.getNNodes(), getLB(0), solver.getUB(), info);

            // updating nodes priority
            for (int r = firstRound; r < lastRound; r++) {
                for (int u = 0; u < problem.nUmpires; u++) {
                    int g1 = solution.assignment[r][u];
                    int g2 = solution.assignment[r + 1][u];
                    edgePriority.improvePriority(g1, g2);
                }
            }
        }
    }


    /**
     * Calculates and returns the cost of the solution of the assignment problem
     * involving rounds {@param round} and {@param round}+1.
     *
     * @param round the first round of the assignment problem.
     * @return the cost of the solution of the assignment problem involving
     * rounds {@param round} and {@param round}+1.
     */
    private int calculateMatching(int round) {
        HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm();

        int[][] costMatrix = new int[problem.nUmpires][problem.nUmpires];
        for (int gir = 0; gir < problem.nUmpires; gir++) {
            int g = round * problem.nUmpires + gir;
            for (int gir2 = 0; gir2 < problem.nUmpires; gir2++) {
                int g2 = (round + 1) * problem.nUmpires + gir2;
                if (problem.q2 > 1 && (problem.games[g][0] == problem.games[g2][0]
                  || problem.games[g][1] == problem.games[g2][1]
                  || problem.games[g][0] == problem.games[g2][1]
                  || problem.games[g][1] == problem.games[g2][0])) {
                    costMatrix[gir][gir2] = Integer.MAX_VALUE;
                }
                else if (problem.q1 > 1 && problem.games[g][0] == problem.games[g2][0]) {
                    costMatrix[gir][gir2] = Integer.MAX_VALUE;
                }
                else {
                    costMatrix[gir][gir2] = problem.dist[problem.games[g][0] - 1][problem.games[g2][0] - 1];
                }
            }
        }

        int[][] assignments = hungarianAlgorithm.computeAssignments(costMatrix);

        int distance = 0;
        for (int[] a : assignments) {
            int gir = a[0];
            int gir2 = a[1];
            int g = round * problem.nUmpires + gir;
            int g2 = (round + 1) * problem.nUmpires + gir2;
            distance += problem.dist[problem.games[g][0] - 1][problem.games[g2][0] - 1];
            //edgePriority[problem.games[g][0] - 1][problem.games[g2][0] - 1] = 1;
        }

        return distance;
    }


    /**
     * This private class maintains the lower bounds calculated for the all
     * sub-problems. The fields of the class are thread-safe.
     */
    private class Bounds {

        private final int n;
        private final int matching[];

        private final VolatileInteger lowerBounds[][];

        /**
         * Instantiates a new bounds container.
         *
         * @param nRounds the n rounds
         */
        public Bounds(int nRounds) {
            this.n = nRounds;
            this.matching = new int[n + 1];
            this.lowerBounds = new VolatileInteger[n][n];
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    lowerBounds[i][j] = new VolatileInteger(0);
        }

        /**
         * Returns the current best bound for rounds [{@param
         * firstRound},{@param lastRound}].
         *
         * @param firstRound the first round.
         * @param lastRound  the last round.
         * @return the current best bound for rounds [{@param
         * firstRound},{@param lastRound}].
         */
        public int get(int firstRound, int lastRound) {
            return lowerBounds[firstRound][lastRound].value;
        }

        /**
         * Updates the best lower bound for rounds [{@param firstRound},{@param
         * lastRound}] and propagates this change to all other bounds.
         *
         * @param firstRound the first round.
         * @param lastRound  the last round.
         * @param lb         the improved lower bound for [firstRound][lastRound].
         */
        public void set(int firstRound, int lastRound, int lb) {
            for (int i = firstRound; i >= 0; i--) {
                for (int j = lastRound; j < problem.nRounds; j++) {
                    lowerBounds[i][j].value = Math.max(lowerBounds[i][j].value, lowerBounds[i][firstRound].value + lb + lowerBounds[lastRound][j].value);
                }
            }
        }

        /**
         * Simple class to represent a volatile int. It is used to ensure
         * thread-safety for the lower bound values.
         */
        private class VolatileInteger {

            public volatile int value;

            public VolatileInteger(int value) {
                this.value = value;
            }
        }
    }
}
