package be.kuleuven.codes.tup.heuristic_decomp;

import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;

/**
 * This class implements the decomposition-based heuristic included in the
 * thesis of Tulio Toffolo.
 *
 * @author Tulio Toffolo
 */
public class Heuristic {

    public final Problem problem;

    private int n, step;
    private double penalty;
    private SimpleSolution solution;
    private IPPartial IPPartial;

    private int nThreads = Main.threads;
    private long maxTimeLimitMillis;

    private int maxSolutions = 3, nBacktracks, maxBacktracks = 100;

    public Heuristic(Problem problem, int n, int step, double penalty) {
        this.problem = problem;
        this.n = n;
        this.step = step;
        this.penalty = penalty;

        solution = new SimpleSolution(problem);
    }

    public int getnBacktracks() {
        return nBacktracks;
    }

    public Solution solve() {
        return solve(null, nThreads, 10800 * 1000);
    }

    public Solution solve(Solution initialSolution, int nThreads, long timeLimitMillis) {
        this.nThreads = nThreads;
        this.maxTimeLimitMillis = System.currentTimeMillis() + timeLimitMillis;
        nBacktracks = 0;
        System.out.println();

        if (initialSolution == null) {
            // constructive phase
            long startTime = System.currentTimeMillis(), startIter;
            do {
                nBacktracks = 0;
                solution = new SimpleSolution(problem);
                startIter = System.currentTimeMillis();
                if (constructive(0)) {
                    initialSolution = solution.makeSolution();
                    initialSolution.calculateScore();
                    if (!initialSolution.isFeasible())
                        solution = null;
                }
                else {
                    solution = null;
                }

                // printing information
                System.out.printf("\nConstructive algorithm\n");
                System.out.printf("    Backtracks: %d\n", nBacktracks);
                System.out.printf("    Final parameters: n=%d, step=%d, penalty=%.0f\n", n, step, penalty);
                System.out.printf("    Runtime: %.2fs\n", (System.currentTimeMillis() - startIter) / 1000.0);
                System.out.printf("    Cost: %s%s\n", initialSolution.getDistance(), initialSolution.isFeasible() ? "" : String.format(" (constraint 'c' violated %d times)", initialSolution.homeVisitViolations));

                System.out.println();
                if (solution == null) penalty += 25;
            }
            while (solution == null && penalty < 500 && System.currentTimeMillis() < maxTimeLimitMillis);

            System.out.printf("    Total runtime: %.2fs\n", (System.currentTimeMillis() - startTime) / 1000.0);
            if (solution != null)
                System.out.printf("    Best cost: %s%s\n", initialSolution.getDistance(), initialSolution.isFeasible() ? "" : String.format(" (constraint 'c' violated %d times)", initialSolution.homeVisitViolations));
            else
                System.out.printf("    Infeasible solution");

            Solution finalSolution = solution.makeSolution();
            finalSolution.calculateScore();
            return finalSolution;
        }
        else {
            solution = new SimpleSolution(initialSolution);
            initialSolution = solution.makeSolution();

            // printing information
            initialSolution.calculateScore();
            System.out.printf("Loading initial solution\n");
            System.out.printf("    Cost: %s%s\n", initialSolution.getDistance(), initialSolution.isFeasible() ? "" : String.format(" (constraint 'c' violated %d times)", initialSolution.homeVisitViolations));
        }

        if (solution != null) {
            // local search phase
            long startTime = System.currentTimeMillis();
            System.out.println();
            solution = localSearch(solution);
            long endTime = System.currentTimeMillis();

            Solution finalSolution = solution.makeSolution();
            finalSolution.calculateScore();
            return finalSolution;
        }

        return null;
    }


    private boolean constructive(int round) {
        if (round >= problem.nRounds)
            return true;

        System.out.printf("Solving %d-%d...\n", round, Math.min(round + n - 1, problem.nRounds - 1));

        IPPartial ip = new IPPartial(problem, solution, round, Math.min(round + n - 1, problem.nRounds - 1), false, penalty);
        SimplePartialSolution[] partialSolutions = ip.solve(maxSolutions, nThreads, maxTimeLimitMillis - System.currentTimeMillis());
        ip = null;

        for (SimplePartialSolution partialSolution : partialSolutions) {
            if (System.currentTimeMillis() >= maxTimeLimitMillis) return false;

            addAssignments(solution, partialSolution);
            if (constructive(round + step)) return true;
            removeAssignments(solution, partialSolution);

            if (nBacktracks++ >= maxBacktracks) return false;
        }

        return false;
    }

    private SimpleSolution localSearch(SimpleSolution solution) {
        long initialTime = System.currentTimeMillis();
        int n = 4, step = 1;
        int round = 0;

        System.out.printf("Initializing local search phase...\n");

        Solution bestSolution = solution.makeSolution();
        bestSolution.calculateScore();

        while (n <= problem.nRounds) {

            int counter = 0;
            while (counter < problem.nRounds / step) {
                System.out.printf("Solving %d-%d...\n", round, Math.min(round + n - 1, problem.nRounds - 1));
                IPPartial ip = new IPPartial(problem, solution, round, Math.min(round + n - 1, problem.nRounds - 1), true);
                SimplePartialSolution[] partialSolutions = ip.solve(1, nThreads, maxTimeLimitMillis - System.currentTimeMillis());

                double initialCost = solution.cost;
                addAssignments(solution, partialSolutions[0]);
                if (solution.cost != initialCost) {
                    Solution currentSolution = solution.makeSolution();
                    currentSolution.calculateScore();
                    if (currentSolution.getDistance() < bestSolution.getDistance()) {
                        bestSolution = currentSolution;

                        counter = 0;
                        System.out.printf("-----> solution improved: %d\n", bestSolution.getDistance());
                    }
                }
                else {
                    counter++;
                }

                round = (round + step) % problem.nRounds;

                if (System.currentTimeMillis() >= maxTimeLimitMillis)
                    return solution;
            }
            n++;
            //step = ( int ) Math.ceil(n / 2.0);
        }
        return solution;
    }


    private void addAssignments(SimpleSolution solution, SimplePartialSolution partialSolution) {
        for (int node = partialSolution.lastGame; node >= partialSolution.firstGame; node--)
            solution.unsetColor(node);
        for (int node = partialSolution.firstGame; node <= partialSolution.lastGame; node++)
            solution.setColor(node, partialSolution.getColor(node));
    }

    private void removeAssignments(SimpleSolution solution, SimplePartialSolution partialSolution) {
        for (int node = partialSolution.lastGame; node >= partialSolution.firstGame; node--)
            solution.unsetColor(node);
    }
}
