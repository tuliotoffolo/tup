package be.kuleuven.codes.tup.model.solution;

import be.kuleuven.codes.tup.model.*;

import java.util.*;

public class SimplePartialSolution {

    public final Problem problem;
    public final int x[];

    /**
     * This matrix keeps the game (node id) assigned to each [umpire][round]
     * cell (note that an umpire is equivalent to a color).
     */
    public final int colorsRounds[][];

    public final int firstRound, lastRound;
    public final int firstGame, lastGame;

    public int cost;


    public SimplePartialSolution(Problem problem, int firstRound, int lastRound) {
        this.problem = problem;
        this.firstRound = firstRound;
        this.lastRound = lastRound;
        this.firstGame = firstRound * problem.nUmpires;
        this.lastGame = (lastRound + 1) * problem.nUmpires - 1;

        this.x = new int[lastGame - firstGame + 1];
        this.cost = 0;

        this.colorsRounds = new int[problem.nUmpires][lastRound - firstRound + 1];

        Arrays.fill(x, -1);
        for (int c = 0; c < problem.nUmpires; c++)
            Arrays.fill(colorsRounds[c], -1);

        // fixing first round to break symmetry
        for (int i = 0; i < problem.nUmpires; i++) {
            x[i] = i;
            colorsRounds[i][0] = i + firstGame;
        }
    }

    private SimplePartialSolution(SimplePartialSolution solution) {
        this.problem = solution.problem;
        this.firstRound = solution.firstRound;
        this.lastRound = solution.lastRound;
        this.firstGame = solution.firstGame;
        this.lastGame = solution.lastGame;

        this.x = solution.x.clone();
        this.cost = solution.cost;

        this.colorsRounds = new int[problem.nUmpires][lastRound - firstRound + 1];

        for (int c = 0; c < problem.nUmpires; c++) {
            System.arraycopy(solution.colorsRounds[c], 0, this.colorsRounds[c], 0, lastRound - firstRound + 1);
        }
    }

    public SimplePartialSolution clone() {
        return new SimplePartialSolution(this);
    }

    public int compareTo(SimpleSolution solution) {
        return cost - solution.cost;
    }

    public int getColor(int node) {
        return x[node - firstGame];
    }

    public PartialSolution makePartialSolution() {
        PartialSolution partialSol = new PartialSolution(problem, firstRound, lastRound);
        partialSol.assignment = new int[problem.nRounds][problem.nUmpires];

        for (int g = 0; g < x.length; g++) {
            int round = firstRound + g / problem.nUmpires;
            partialSol.assignment[round][x[g]] = g + firstGame;
        }
        partialSol.calculateScore();
        return partialSol;
    }

    public void setColor(int node, int color) {
        int idx = node - firstGame;
        int round = problem.gameToRound[node] - firstRound;

        if (x[idx] >= 0 && round > 0) {
            cost -= problem.distGames[colorsRounds[x[idx]][round - 1]][colorsRounds[x[idx]][round]];

            colorsRounds[x[idx]][round] = -1;
        }

        x[idx] = color;
        colorsRounds[x[idx]][round] = node;

        if (round > 0)
            cost += problem.distGames[colorsRounds[x[idx]][round - 1]][colorsRounds[x[idx]][round]];
    }

    public void unsetColor(int node) {
        int idx = node - firstGame;

        if (x[idx] >= 0) {
            int round = problem.gameToRound[node] - firstRound;
            cost -= problem.distGames[colorsRounds[x[idx]][round - 1]][colorsRounds[x[idx]][round]];

            colorsRounds[x[idx]][round] = -1;
            x[idx] = -1;
        }
    }
}
