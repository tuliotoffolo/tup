package be.kuleuven.codes.tup.bnb.model;

import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;

import java.util.*;

public class SimpleSolution {

    public final Problem problem;
    public final int x[];

    /**
     * This matrix keeps the game (node id) assigned to each [umpire][round]
     * cell (note that an umpire is equivalent to a color).
     */
    public final int colorsRounds[][];
    public final int colorsLocations[][];
    public final int colorsLocationsCount[];

    public final int firstRound, lastRound;
    public final int firstGame, lastGame;

    public int cost;


    public SimpleSolution(Problem problem) {
        this(problem, 0, problem.nRounds - 1);
        assert lastGame == problem.nGames - 1 : String.format("wrong index for lastGame: %d versus %d", lastGame, problem.nGames - 1);
    }

    public SimpleSolution(Problem problem, int firstRound, int lastRound) {
        this.problem = problem;
        this.firstRound = firstRound;
        this.lastRound = lastRound;
        this.firstGame = firstRound * problem.nUmpires;
        this.lastGame = (lastRound + 1) * problem.nUmpires - 1;

        this.x = new int[lastGame - firstGame + 1];
        this.cost = 0;

        this.colorsRounds = new int[problem.nUmpires][lastRound - firstRound + 1];
        this.colorsLocations = new int[problem.nUmpires][problem.nTeams];
        this.colorsLocationsCount = new int[problem.nUmpires];

        Arrays.fill(x, -1);
        for (int c = 0; c < problem.nUmpires; c++)
            Arrays.fill(colorsRounds[c], -1);

        // fixing first round to break symmetry
        for (int i = 0; i < problem.nUmpires; i++) {
            x[i] = i;
            colorsRounds[i][0] = i + firstGame;
            colorsLocations[i][problem.games[i][0] - 1]++;
            colorsLocationsCount[i]++;
        }
    }

    private SimpleSolution(SimpleSolution solution) {
        this.problem = solution.problem;
        this.firstRound = solution.firstRound;
        this.lastRound = solution.lastRound;
        this.firstGame = solution.firstGame;
        this.lastGame = solution.lastGame;

        this.x = solution.x.clone();
        this.cost = solution.cost;

        this.colorsRounds = new int[problem.nUmpires][lastRound - firstRound + 1];
        this.colorsLocations = new int[problem.nUmpires][problem.nTeams];
        this.colorsLocationsCount = solution.colorsLocationsCount.clone();

        for (int c = 0; c < problem.nUmpires; c++) {
            System.arraycopy(solution.colorsRounds[c], 0, this.colorsRounds[c], 0, lastRound - firstRound + 1);
            System.arraycopy(solution.colorsLocations[c], 0, this.colorsLocations[c], 0, problem.nTeams);
        }
    }

    public SimpleSolution clone() {
        return new SimpleSolution(this);
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

    public Solution makeSolution() {
        if (firstRound != 0 || lastRound != problem.nRounds - 1)
            return null;

        Solution sol = new Solution(problem);
        sol.assignment = new int[problem.nRounds][problem.nUmpires];

        for (int g = 0; g < x.length; g++) {
            int round = g / problem.nUmpires;
            sol.assignment[round][x[g]] = g;
        }
        sol.calculateScore();
        return sol;
    }

    public void setColor(int node, int color) {
        int idx = node - firstGame;
        int round = problem.gameToRound[node] - firstRound;

        if (x[idx] >= 0) {
            cost -= problem.distGames[colorsRounds[x[idx]][round - 1]][colorsRounds[x[idx]][round]];
            if (--colorsLocations[x[idx]][problem.games[colorsRounds[x[idx]][round]][0] - 1] == 0)
                colorsLocationsCount[x[idx]]--;

            colorsRounds[x[idx]][round] = -1;
        }

        x[idx] = color;
        colorsRounds[x[idx]][round] = node;
        if (++colorsLocations[x[idx]][problem.games[node][0] - 1] == 1)
            colorsLocationsCount[x[idx]]++;

        cost += problem.distGames[colorsRounds[x[idx]][round - 1]][colorsRounds[x[idx]][round]];
    }

    public void unsetColor(int node) {
        int idx = node - firstGame;

        if (x[idx] >= 0) {
            int round = problem.gameToRound[node] - firstRound;
            cost -= problem.distGames[colorsRounds[x[idx]][round - 1]][colorsRounds[x[idx]][round]];
            if (--colorsLocations[x[idx]][problem.games[colorsRounds[x[idx]][round]][0] - 1] == 0)
                colorsLocationsCount[x[idx]]--;

            colorsRounds[x[idx]][round] = -1;
            x[idx] = -1;
        }
    }
}
