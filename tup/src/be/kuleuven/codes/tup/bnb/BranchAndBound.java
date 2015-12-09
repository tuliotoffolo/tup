package be.kuleuven.codes.tup.bnb;

import be.kuleuven.codes.tup.bnb.model.*;
import be.kuleuven.codes.tup.heuristic.*;
import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;
import be.kuleuven.codes.tup.thread.*;
import be.kuleuven.codes.tup.useful.*;

import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * This class implements the decomposition-based branch-and-bound presented by
 * Toffolo et al. 2015.
 *
 * @author Tulio Toffolo
 */
public class BranchAndBound {

    public final Problem problem;
    public final EdgePriority edgePriority;
    public final PartialMatching partialMatching;

    private LowerBound lowerbound;

    private int n, m, firstRound, lastRound;
    private long startTimeMillis = System.currentTimeMillis(), maxTimeMillis;

    private ThreadExecutor threadExecutor;
    private FuturePool futurePool;
    private AtomicLong nodeCounter = new AtomicLong(0);

    private volatile int ub;
    private volatile Solution bestSolution = null;


    /**
     * Instantiates a new solver considering a specific TUP problem instance.
     *
     * @param problem the TUP problem instance to be solved.
     */
    public BranchAndBound(Problem problem) {
        this.problem = problem;
        this.edgePriority = new EdgePriority(problem);
        this.partialMatching = new PartialMatching(this.problem);

        this.n = problem.nGames;
        this.m = problem.nUmpires;
        this.firstRound = 0;
        this.lastRound = problem.nRounds - 1;
    }

    /**
     * Gets the total number of visited nodes.
     *
     * @return the total number of visited nodes..
     */
    public long getNNodes() {
        return nodeCounter.get();
    }

    /**
     * Gets the current best upper bound.
     *
     * @return current best upper bound.
     */
    public int getUB() {
        return ub;
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
     * * This method actually solves the problem using the partial
     * decomposition-based branch-and-bound.
     *
     * @param ub              initial upper bound.
     * @param maxThreads      maximum number of threads to be used.
     * @param timeLimitMillis maximum runtime allowed.
     * @param useTimeWindows  indicates whether the time-window decomposition
     *                        should be used or not by the @{link LowerBound}.
     * @return the best solution found for the problem.
     * @throws InterruptedException if the method is interrupted before
     *                              finishing. This indicates that a potential
     *                              non-optimal solution was returned.
     */
    public Solution solve(int ub, int maxThreads, long timeLimitMillis, boolean useTimeWindows) throws InterruptedException {
        this.ub = ub;
        this.threadExecutor = maxThreads >= 4 ? new ThreadExecutor(maxThreads / 2) : new SequentialExecutor();
        this.futurePool = new FuturePool();
        this.maxTimeMillis = startTimeMillis + timeLimitMillis;

        System.out.printf("     /----------------------------------------------------------------\\\n");
        System.out.printf("     | %10s | %10s | %10s | %10s | %10s |\n", "Time", "Nodes", "LB", "UB", "Gap");
        System.out.printf("     |------------|------------|------------|------------|------------|\n");

        // running lower bound calculation in parallel
        this.lowerbound = new LowerBound(this);
        Thread lowerBoundThread = new Thread(() -> {
            Thread.currentThread().setName("LowerBound");
            int lbMaxThreads = maxThreads - threadExecutor.getCorePoolSize() - 1;
            lowerbound.solve(lbMaxThreads, maxTimeMillis, useTimeWindows);
            threadExecutor.setCorePoolSize(threadExecutor.getCorePoolSize() + lbMaxThreads + 1);
            threadExecutor.setMaximumPoolSize(threadExecutor.getCorePoolSize());
        });
        lowerBoundThread.start();

        // running branch-and-bound (sequential or in a new thread, if any is available)
        if (threadExecutor instanceof SequentialExecutor) {
            long nNodes = recurseSequential(new SimpleSolution(problem), 0, firstRound + 1);
            assert nodeCounter.get() == nNodes;
        }
        else {
            futurePool.add(threadExecutor.submit(() -> {
                long nNodes = recurse(new SimpleSolution(problem), 0, firstRound + 1);
                assert nodeCounter.get() == nNodes;
            }));
        }

        // waiting the completion of all tasks
        futurePool.join();
        threadExecutor.shutdownNow();

        // interrupting and finishing lower bound thread
        lowerBoundThread.interrupt();
        lowerBoundThread.join();

        if (getUB() != Integer.MAX_VALUE) {
            System.out.printf("     |------------|------------|------------|------------|------------|\n");
            printStatus(System.currentTimeMillis() < maxTimeMillis ? "opt" : "time", getNNodes(), lowerbound.getLB(0), this.ub, "");
        }
        System.out.printf("     \\----------------------------------------------------------------/\n");

        return bestSolution;
    }


    /**
     * This method executes the recursive branching to color (assign umpire) the
     * nodes of the graph, i.e. to assign umpires to games.
     *
     * @param x      the current partial solution.
     * @param umpire the current umpire under analysis.
     * @param round  the current round under analysis.
     */
    private long recurse(SimpleSolution x, int umpire, int round) {
        if (System.currentTimeMillis() >= maxTimeMillis)
            return 0;

        nodeCounter.incrementAndGet();
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
                    SimpleSolution xCopy = x.clone();
                    futurePool.add(threadExecutor.submit(() -> {
                        Thread.currentThread().setName(String.format("bnb :: recurse(%d)\n", node + 1));
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
     * This method does the same as {@link #recurse(SimpleSolution, int, int)},
     * but ensures that no new Threads are created, i.e. that the method is
     * executed sequentially.
     *
     * @param x      the current partial solution.
     * @param umpire the current umpire under analysis.
     * @param round  the current round under analysis.
     */
    private long recurseSequential(SimpleSolution x, int umpire, int round) {
        if (System.currentTimeMillis() >= maxTimeMillis)
            return 0;

        nodeCounter.incrementAndGet();
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
     * new thread can be created. Three rules are considered:
     * <p>
     * 1) <b>Availability of CPU</b>: if there is no available CPUs, then a new
     * thread is not created.
     * <p>
     * 2) <b>Depth of current node</b>: if the current node is near a leaf node,
     * then a new thread is not created. This avoids copying memory for very
     * short runs.
     * <p>
     * 3) <b>Quality of current bound</b>: if the current bound is very close to
     * the lower bound, then a new thread is also not created. If the bound is
     * too tight, then there is a smaller chance of many improving subtrees
     * under the current node. The idea is, again, to avoid copying memory for
     * very short runs.
     *
     * @param node the current node.
     * @return true if a new thread can be created for a determined node and in
     * the current moment and false otherwise.
     */
    private boolean canCreateNewThread(int node) {
        return node == m * 2 && threadExecutor.hasEmptySlot();
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
    private boolean canPrune(SimpleSolution x, int umpire, int round) {
        int lb = lowerbound.getLB(round);

        if (x.cost + lb >= ub)
            return true;

        if (umpire < m - 1 && x.cost + lb + lowerbound.getLB(round - 1, round) >= ub) {
            int prevRound = round - 1;

            // used and usedNext keep, respectively, the unconnected games between the current and the next round
            boolean used[] = new boolean[m];
            boolean usedNext[] = new boolean[m];

            for (int i = 0; i <= umpire; i++) {
                int prevGame = x.colorsRounds[i][prevRound] % m;
                int nextGame = x.colorsRounds[i][round] % m;
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
     * improves, the method {@link #setUB(Solution, String)} is then called.
     *
     * @param x the solution to be checked.
     */
    private void checkSolution(SimpleSolution x) {
        Solution sol = x.makeSolution();

        //improve bound with steepest descent
        SteepestDescent sd = new SteepestDescent();
        sol = sd.solve(problem, sol);
        sol.calculateScore();
        long obj = sol.getObjectiveCost();
        if (obj < ub)
            setUB(sol, obj < x.cost ? "* H" : "*");
    }

    /**
     * This method returns a heap with the games that can be assigned to {@param
     * umpire} in the current {@param round} considering solution {@param x}.
     * The heap is constructed such that games with smaller distance are
     * returned first. This method has complexity O(m + q1 + q2 + g log g),
     * where m is the total number of colors (umpires) and g is the number of
     * available nodes (games).
     *
     * @param x      the partial solution.
     * @param umpire the current umpire under analysis.
     * @param round  the current round under analysis.
     * @return the heap (priority queue) with the available colors.
     */
    private PairInt[] createAvailableNodesArray(SimpleSolution x, int umpire, int round) {
        // setting initial unavailable nodes in up to O(m)
        int size = m - umpire;
        boolean nodesUsed[] = new boolean[m];
        for (int u = 0; u < umpire; u++)
            nodesUsed[x.colorsRounds[u][round] % m] = true;

        // updating impossible nodes due to constraint of visiting all teams - O(m)
        for (int i = 0; i < m; i++) {
            if (!nodesUsed[i]) {
                int newLocation = x.colorsLocations[umpire][problem.games[round * m + i][0] - 1] == 0 ? 1 : 0;
                if (problem.nTeams - (x.colorsLocationsCount[umpire] + newLocation) > problem.nRounds - round) {
                    nodesUsed[i] = true;
                    if (--size == 0) return new PairInt[0];
                }
            }
        }

        // updating additional unavailable nodes in O(q2)
        int rq2 = Math.max(0, round - problem.q2 + 1);
        for (int r = rq2; r < round; r++) {
            for (int team : problem.games[x.colorsRounds[umpire][r]]) {
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
        int rq1 = Math.max(0, round - problem.q1 + 1);
        for (int r = rq1; r < rq2; r++) {
            int team = problem.games[x.colorsRounds[umpire][r]][0];
            int game = problem.roundHomeTeamToGame[round][team - 1] % m;

            if (game >= 0 && !nodesUsed[game]) {
                nodesUsed[game] = true;
                if (--size == 0) return new PairInt[0];
            }
        }

        // creating the heap of colors considering the distance -- try shorter first
        PairInt[] nodes = new PairInt[size];
        int p = 0;
        for (int i = 0; i < m; i++) {
            if (!nodesUsed[i]) {
                int node = round * m + i;
                nodes[p++] = new PairInt(node, problem.distGames[x.colorsRounds[umpire][round - 1]][node]);
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
    private synchronized void setUB(Solution solution, String extra) {
        if (solution.getObjectiveCost() < ub) {
            ub = ( int ) solution.getObjectiveCost();
            bestSolution = solution;

            printStatus("ub", getNNodes(), lowerbound.getLB(0), ub, extra);

            // updating nodes priority
            //for (int r = firstRound; r < lastRound; r++) {
            //    for (int u = 0; u < problem.nUmpires; u++) {
            //        int g1 = solution.assignment[r][u];
            //        int g2 = solution.assignment[r + 1][u];
            //        edgePriority.improvePriority(g1, g2);
            //    }
            //}
        }
    }
}
