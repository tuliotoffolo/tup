package be.kuleuven.codes.tup.bnb;

import be.kuleuven.codes.tup.heuristic.*;
import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;
import be.kuleuven.codes.tup.thread.*;
import be.kuleuven.codes.tup.useful.*;

import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * This class implements the decomposition-based responsible for solving
 * sub-problems.
 *
 * @author Tulio Toffolo
 */
public class BranchAndBoundPartial {

    public final Problem problem;
    public final EdgePriority edgePriority;
    public final PartialMatching partialMatching;

    private LowerBound lowerbound;

    private int m;
    private int firstRound;
    private int lastRound;
    private int lastGame;
    private long startTimeMillis = System.currentTimeMillis(), maxTimeMillis;

    private ThreadExecutor threadExecutor;
    private FuturePool futurePool;
    private AtomicLong nodeCounter = new AtomicLong(0);

    private volatile int ub;
    private volatile PartialSolution bestSolution = null;


    /**
     * Instantiates a new solver for a specific TUP problem instance considering
     * the set of rounds [{@param firstRound},{@param lastRound}].
     *
     * @param problem         the TUP problem instance to be solved.
     * @param partialMatching the {@link PartialMatching} object, containing
     *                        pre-stored solution for partial assignment
     *                        problems to strenghten lower bounds.
     * @param firstRound      the first round of the sub-problem.
     * @param lastRound       the last round of the sub-problem.
     */
    public BranchAndBoundPartial(Problem problem, EdgePriority edgePriority, PartialMatching partialMatching, int firstRound, int lastRound) {
        this.problem = problem;
        this.edgePriority = edgePriority;
        this.partialMatching = partialMatching;

        this.m = problem.nUmpires;
        this.firstRound = firstRound;
        this.lastRound = lastRound;
        this.lastGame = (lastRound + 1) * m - 1;
    }

    /**
     * This method simply prints information in a standard format to stdout.
     *
     * @param pre   2 characters string to be printed in the beginning of the
     *              line.
     * @param nodes number of explored nodes.
     * @param lb    current best lower bound.
     * @param ub    current best upper bound.
     * @param extra text to be printed in the end of the line.
     */
    public void printStatus(String pre, long nodes, double lb, double ub, String extra) {
        double time = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
        String timeStr = time >= 3600.0 ? String.format("%9.2fm", time / 60) : String.format("%9.2fs", time);
        String nodesStr = nodes >= 1000000 ? String.format("%d M", nodes / 1000000) : String.format("%d", nodes);
        String lbStr = lb >= Integer.MAX_VALUE ? "-" : String.format("%.0f", lb);
        String ubStr = ub >= Integer.MAX_VALUE ? "-" : String.format("%.0f", ub);
        String gapStr = ub >= Integer.MAX_VALUE ? "-" : String.format("%.2f%%", 100 * (ub - lb) / ub);

        System.out.printf("%-4s | %9s | %10s | %10s | %10s | %10s |   %s\n", pre, timeStr, nodesStr, lbStr, ubStr, gapStr, extra);
    }

    /**
     * This method actually solves the problem using the partial
     * decomposition-based branch-and-bound.
     *
     * @param lowerbound       reference to the {@link LowerBound} object that
     *                         will provide the lower bounds.
     * @param ub               initial upper bound.
     * @param lbThreadExecutor the {@link ThreadExecutor} object that will
     *                         control the creation of new threads.
     * @param maxTimeMillis    the maximum time (in milliseconds) in which the
     *                         lower bound can still be running; notice that
     *                         this procedure can be executed while
     *                         System.currentTimeMillis() is smaller than
     *                         {@param maxLimitMillis}.
     * @return the cost of the optimal solution for the subproblem.
     */
    public PartialSolution solve(LowerBound lowerbound, int ub, ThreadExecutor lbThreadExecutor, long maxTimeMillis) {
        this.lowerbound = lowerbound;
        this.ub = ub;
        this.maxTimeMillis = maxTimeMillis;

        this.threadExecutor = lbThreadExecutor;
        this.futurePool = new FuturePool(lbThreadExecutor);

        // running branch-and-bound
        SimplePartialSolution initialX = new SimplePartialSolution(problem, firstRound, lastRound);
        long nNodes = recurse(initialX, 0, firstRound + 1);
        nodeCounter.getAndAdd(nNodes);

        // waiting the completion of all tasks
        futurePool.join();

        if (Thread.currentThread().isInterrupted() || System.currentTimeMillis() >= maxTimeMillis)
            return null;

        return this.bestSolution;
    }


    /**
     * This method executes the recursive branching to color (assign umpire) the
     * nodes of the graph, i.e. to assign umpires to games.
     *
     * @param x      the current partial solution.
     * @param umpire the current umpire under analysis.
     * @param round  the current round under analysis.
     */
    private long recurse(SimplePartialSolution x, int umpire, int round) {
        if (Thread.currentThread().isInterrupted() || System.currentTimeMillis() >= maxTimeMillis)
            return 0;

        long nodes = 1;

        PairInt[] games = createAvailableNodesArray(x, umpire, round);
        for (PairInt pair : games) {
            int node = pair.first;
            x.setColor(node, umpire);
            if (round == lastRound && umpire == m - 1) {
                checkSolution(x);
            }
            else if (!canPrune(x, umpire, round)) {

                if (canCreateNewThread(node)) {
                    SimplePartialSolution xCopy = x.clone();
                    futurePool.add(threadExecutor.submit(() -> {
                        Thread.currentThread().setName(String.format("lb(%d,%d) :: recurse(%d)\n", firstRound, lastRound, node + 1));
                        long nNodes = umpire == m - 1 ? recurseSequential(xCopy, 0, round + 1) : recurseSequential(xCopy, umpire + 1, round);
                        nodeCounter.getAndAdd(nNodes);
                    }));
                }
                else {
                    nodes += umpire == m - 1 ? recurse(x, 0, round + 1) : recurse(x, umpire + 1, round);
                }
            }
            x.unsetColor(node);
        }

        return nodes;
    }

    /**
     * This method does the same as {@link #recurse(SimplePartialSolution, int,
     * int)}, but ensures that no new Threads are created, i.e. that the method
     * is executed sequentially.
     *
     * @param x      the current partial solution.
     * @param umpire the current umpire under analysis.
     * @param round  the current round under analysis.
     */
    private long recurseSequential(SimplePartialSolution x, int umpire, int round) {
        if (Thread.currentThread().isInterrupted() || System.currentTimeMillis() >= maxTimeMillis)
            return 0;

        long nodes = 1;

        PairInt[] games = createAvailableNodesArray(x, umpire, round);
        for (PairInt pair : games) {
            int node = pair.first;
            x.setColor(node, umpire);
            if (round == lastRound && umpire == m - 1)
                checkSolution(x);
            else if (!canPrune(x, umpire, round))
                nodes += umpire == m - 1 ? recurseSequential(x, 0, round + 1) : recurseSequential(x, umpire + 1, round);
            x.unsetColor(node);
        }

        return nodes;
    }


    /**
     * This method checks if, for a determined node and in the current moment, a
     * new thread can be created. Two rules are considered:
     * <p>
     * 1) Availability of CPU: if there is no available CPUs, then a new thread
     * is not created.
     * <p>
     * 2) Depth of current node: if the current node is near a leaf node, then a
     * new thread is not created. This avoids copying memory for very short
     * runs.
     *
     * @param node the current node.
     * @return true if a new thread can be created for a determined node and in
     * the current moment and false otherwise.
     */
    private boolean canCreateNewThread(int node) {
        return node == m * (firstRound + 2) && threadExecutor.hasEmptySlot();
    }

    /**
     * This method checks if the current node can be pruned. For that, the
     * decomposition-based lower bounds is used. If necessary, the partial
     * matching is calculated to strengthen the bound.
     *
     * @param x      the current solution.
     * @param umpire the current umpire.
     * @param round  the current round.
     * @return true if current node can be pruned and false otherwise.
     */
    private boolean canPrune(SimplePartialSolution x, int umpire, int round) {
        int lb = lowerbound.getLB(round, lastRound);

        if (x.cost + lb >= ub)
            return true;

        if (umpire < m - 1) {
            int prevRound = round - 1;

            // used and usedNext keep, respectively, the unconnected games between the current and the next round
            boolean used[] = new boolean[m];
            boolean usedNext[] = new boolean[m];

            for (int i = 0; i <= umpire; i++) {
                int prevGame = x.colorsRounds[i][prevRound - firstRound] % m;
                int nextGame = x.colorsRounds[i][round - firstRound] % m;
                used[prevGame] = true;
                usedNext[nextGame] = true;
            }

            int partialMatchingCost = partialMatching.getDistance(prevRound, umpire, used, usedNext);
            if (x.cost + lb + partialMatchingCost >= ub)
                return true;
        }

        return false;
    }

    /**
     * Check the solution obtained and run a Local Search on it. If the solution
     * improves, the method {@link #setUB(PartialSolution, String)} is then
     * called.
     *
     * @param x the solution to be checked.
     */
    private void checkSolution(SimplePartialSolution x) {
        PartialSolution partialSol = x.makePartialSolution();

        //improve bound with steepest descent
        SteepestDescent sd = new SteepestDescent();
        partialSol = sd.solvePartial(problem, partialSol);
        partialSol.calculateScore();
        long obj = partialSol.getObjectiveCost();
        if (obj < ub)
            setUB(partialSol, obj < x.cost ? "* H" : "*");
    }

    /**
     * This method returns a heap with the games that can be assigned to {@param
     * umpire} in the current {@param round} considering solution {@param x}.
     * The heap is constructed such that games with smaller distance are
     * returned first. This method has complexity O(m + q1 + q2 + g log g),
     * where m is the total number of colors (umpires) and g is the number of
     * available nodes (games) in {@param round}.
     *
     * @param x      the partial solution.
     * @param umpire the current umpire under analysis.
     * @param round  the current round under analysis.
     * @return the heap (priority queue) with the available colors.
     */
    private PairInt[] createAvailableNodesArray(SimplePartialSolution x, int umpire, int round) {
        // setting initial unavailable nodes in up to O(m)
        int size = m - umpire;
        boolean nodesUsed[] = new boolean[m];
        for (int u = 0; u < umpire; u++)
            nodesUsed[x.colorsRounds[u][round - firstRound] % m] = true;

        // updating additional unavailable nodes in O(q2)
        int rq2 = Math.max(firstRound, round - problem.q2 + 1);
        for (int r = rq2; r < round; r++) {
            for (int team : problem.games[x.colorsRounds[umpire][r - firstRound]]) {
                int game = problem.opponents[round][team - 1] > 0
                  ? problem.roundHomeTeamToGame[round][team - 1] % m
                  : problem.roundHomeTeamToGame[round][-problem.opponents[round][team - 1] - 1] % m;

                if (!nodesUsed[game]) {
                    nodesUsed[game] = true;
                    if (--size == 0) return new PairInt[0];
                }
            }
        }

        // updating remaining unavailable nodes in O(q1-q2)
        int rq1 = Math.max(firstRound, round - problem.q1 + 1);
        for (int r = rq1; r < rq2; r++) {
            int team = problem.games[x.colorsRounds[umpire][r - firstRound]][0];
            int game = problem.roundHomeTeamToGame[round][team - 1] % m;

            if (game >= 0 && !nodesUsed[game]) {
                nodesUsed[game] = true;
                if (--size == 0) return new PairInt[0];
            }
        }

        // creating the heap of colors considering the distance -- try shorter first
        PairInt nodes[] = new PairInt[size];
        int p = 0;
        for (int i = 0; i < m; i++) {
            if (!nodesUsed[i]) {
                int node = round * m + i;
                nodes[p++] = new PairInt(node, edgePriority.get(x.colorsRounds[umpire][round - firstRound - 1], node));
            }
        }
        Arrays.sort(nodes, (a, b) -> a.second - b.second);

        return nodes;
    }

    /**
     * This thread-safe method updates the current best solution and the current
     * best upper bound.
     *
     * @param solution the solution to be analyzed.
     * @param extra    some extra information to be printed in the logs,
     *                 regarding the way the current solution was obtained.
     */
    private synchronized void setUB(PartialSolution solution, String extra) {
        if (solution.getObjectiveCost() < ub) {
            this.bestSolution = solution;
            ub = ( int ) solution.getObjectiveCost();
            //printStatus("sub", 0, lowerbound.getLB(0), ub, extra);
        }
    }
}
