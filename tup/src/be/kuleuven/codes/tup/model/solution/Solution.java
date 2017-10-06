package be.kuleuven.codes.tup.model.solution;

import be.kuleuven.codes.tup.model.*;

import java.util.*;

public class Solution {

    public Problem problem;

    public int[][] assignment; // for each game which umpire is assigned
    public int[][] visits;
    public int[] umpireDistances;
    public int[][][] homeVisits;
    public int[][][] teamVisits;

    public int travelDistance;
    public int homeVisitViolations;
    public int consecutiveHomeVisitViolations;
    public int consecutiveTeamSeenViolations;

    public boolean scoreInitiallyCalculated;

    public Solution(Problem problem) {
        this.problem = problem;
        scoreInitiallyCalculated = false;
        assignment = new int[problem.nRounds][problem.nUmpires];
    }

    public Solution(Solution sol) {
        this.problem = sol.problem;
        this.assignment = new int[problem.nRounds][problem.nUmpires];

        for (int round = 0; round < problem.nRounds; round++) {
            assignment[round] = Arrays.copyOf(sol.assignment[round],
              sol.assignment[round].length);
        }

        if (sol.scoreInitiallyCalculated) {
            this.travelDistance = sol.travelDistance;
            this.homeVisitViolations = sol.homeVisitViolations;
            this.consecutiveHomeVisitViolations = sol.consecutiveHomeVisitViolations;
            this.consecutiveTeamSeenViolations = sol.consecutiveTeamSeenViolations;
            this.visits = new int[problem.nRounds][];
            for (int ump = 0; ump < problem.nUmpires; ump++) {
                this.visits[ump] = Arrays.copyOf(sol.visits[ump],
                  sol.visits[ump].length);
            }
            this.umpireDistances = Arrays.copyOf(sol.umpireDistances, sol.umpireDistances.length);

            this.homeVisits = new int[problem.nRounds][problem.nUmpires][];
            this.teamVisits = new int[problem.nRounds][problem.nUmpires][];
            for (int round = 0; round < problem.nRounds; round++) {
                for (int umpire = 0; umpire < problem.nUmpires; umpire++) {
                    this.homeVisits[round][umpire] = Arrays.copyOf(
                      sol.homeVisits[round][umpire],
                      sol.homeVisits[round][umpire].length);
                    this.teamVisits[round][umpire] = Arrays.copyOf(
                      sol.teamVisits[round][umpire],
                      sol.teamVisits[round][umpire].length);
                }
            }
            scoreInitiallyCalculated = sol.scoreInitiallyCalculated;
        }
    }

    public long getObjectiveCost() {
        if (!scoreInitiallyCalculated) {
            calculateTravelDistance();
            calculateHomeVisitViolations();
            calculateConsecutiveViolations();
            scoreInitiallyCalculated = true;
        }
        return travelDistance + Constants.penaltyWeight * homeVisitViolations
          + Constants.penaltyWeight * consecutiveHomeVisitViolations
          + Constants.penaltyWeight * consecutiveTeamSeenViolations;
    }

    private void beforeValueChanges(int umpire, int round) {

        int currentgame = assignment[round][umpire];
        int venue = problem.games[currentgame][0] - 1;
        int otherTeam = problem.games[currentgame][1] - 1;
        if (visits[umpire][venue] == 1)
            homeVisitViolations++;
        visits[umpire][venue]--;

        if (round > 0) {
            travelDistance -= problem.dist[problem.games[assignment[round - 1][umpire]][0] - 1][venue];
            umpireDistances[umpire] -= problem.dist[problem.games[assignment[round - 1][umpire]][0] - 1][venue];
        }
        if (round < problem.nRounds - 1) {
            travelDistance -= problem.dist[venue][problem.games[assignment[round + 1][umpire]][0] - 1];
            umpireDistances[umpire] -= problem.dist[venue][problem.games[assignment[round + 1][umpire]][0] - 1];
        }

        for (int currentRound = round; currentRound < Math.min(problem.nRounds,
          round + problem.q1); currentRound++) {
            int value = --homeVisits[currentRound][umpire][venue];
            if (value == 1) {
                consecutiveHomeVisitViolations--;
            }
        }

        for (int currentRound = round; currentRound < Math.min(problem.nRounds,
          round + problem.q2); currentRound++) {
            int value = --teamVisits[currentRound][umpire][venue];
            if (value == 1) {
                consecutiveTeamSeenViolations--;
            }
            value = --teamVisits[currentRound][umpire][otherTeam];
            if (value == 1) {
                consecutiveTeamSeenViolations--;
            }
        }

    }

    private void afterValueChanges(int umpire, int round) {

        int currentgame = assignment[round][umpire];
        int venue = problem.games[currentgame][0] - 1;
        int otherTeam = problem.games[currentgame][1] - 1;

        if (visits[umpire][venue] == 0)
            homeVisitViolations--;
        visits[umpire][venue]++;

        if (round > 0) {
            travelDistance += problem.dist[problem.games[assignment[round - 1][umpire]][0] - 1][venue];
            umpireDistances[umpire] += problem.dist[problem.games[assignment[round - 1][umpire]][0] - 1][venue];
        }
        if (round < problem.nRounds - 1) {
            travelDistance += problem.dist[venue][problem.games[assignment[round + 1][umpire]][0] - 1];
            umpireDistances[umpire] += problem.dist[venue][problem.games[assignment[round + 1][umpire]][0] - 1];
        }

        for (int currentRound = round; currentRound < Math.min(problem.nRounds,
          round + problem.q1); currentRound++) {
            int value = ++homeVisits[currentRound][umpire][venue];
            if (value == 2) {
                consecutiveHomeVisitViolations++;
            }
        }

        for (int currentRound = round; currentRound < Math.min(problem.nRounds,
          round + problem.q2); currentRound++) {
            int value = ++teamVisits[currentRound][umpire][venue];
            if (value == 2) {
                consecutiveTeamSeenViolations++;
            }
            value = ++teamVisits[currentRound][umpire][otherTeam];
            if (value == 2) {
                consecutiveTeamSeenViolations++;
            }
        }
    }

    public void calculateTravelDistance() {
        umpireDistances = new int[problem.nUmpires];
        travelDistance = 0;
        for (int ump = 0; ump < problem.nUmpires; ump++) {
            for (int round = 1; round < problem.nRounds; round++) {
                travelDistance += problem.dist[problem.games[assignment[round - 1][ump]][0] - 1][problem.games[assignment[round][ump]][0] - 1];
                umpireDistances[ump] += problem.dist[problem.games[assignment[round - 1][ump]][0] - 1][problem.games[assignment[round][ump]][0] - 1];
            }
        }
    }

    public void calculateHomeVisitViolations() {
        homeVisitViolations = 0;
        visits = new int[problem.nUmpires][problem.nTeams];

        for (int ump = 0; ump < problem.nUmpires; ump++) {
            for (int round = 0; round < problem.nRounds; round++) {
                visits[ump][problem.games[assignment[round][ump]][0] - 1]++;
            }
        }
        for (int team = 0; team < problem.nTeams; team++) {
            for (int ump = 0; ump < problem.nUmpires; ump++) {
                if (visits[ump][team] == 0)
                    homeVisitViolations++;
            }
        }
    }

    public void calculateConsecutiveViolations() {

        consecutiveHomeVisitViolations = 0;
        consecutiveTeamSeenViolations = 0;

        homeVisits = new int[problem.nRounds][problem.nUmpires][problem.nTeams];
        teamVisits = new int[problem.nRounds][problem.nUmpires][problem.nTeams];

        for (int ump = 0; ump < problem.nUmpires; ump++) {
            for (int round = 0; round < problem.nRounds; round++) {
                int currentGame = assignment[round][ump];

                for (int slot = round; slot < Math.min(problem.nRounds, round
                  + problem.q1); slot++) {
                    homeVisits[slot][ump][problem.games[currentGame][0] - 1]++;
                }
                for (int slot = round; slot < Math.min(problem.nRounds, round
                  + problem.q2); slot++) {
                    teamVisits[slot][ump][problem.games[currentGame][0] - 1]++;
                    teamVisits[slot][ump][problem.games[currentGame][1] - 1]++;
                }
            }
        }
        for (int ump = 0; ump < problem.nUmpires; ump++) {
            for (int round = 0; round < problem.nRounds; round++) {
                for (int team = 0; team < problem.nTeams; team++) {
                    if (homeVisits[round][ump][team] > 1)
                        consecutiveHomeVisitViolations++;
                    if (teamVisits[round][ump][team] > 1)
                        consecutiveTeamSeenViolations++;
                }
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Solution that = ( Solution ) o;

        if (!Arrays.equals(assignment, that.assignment))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(assignment);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Solution [travelDistance="
          + travelDistance + ", homeVisitViolations="
          + homeVisitViolations + ", consecutiveHomeVisitViolations="
          + consecutiveHomeVisitViolations
          + ", consecutiveTeamSeenViolations="
          + consecutiveTeamSeenViolations + "]");
        sb.append("\n");
        for (int i = 1; i <= problem.nUmpires; i++) {
            sb.append("-" + i + "-");
        }
        sb.append("\n");

        int[] assignments = new int[problem.nGames];
        for (int ump = 0; ump < problem.nUmpires; ump++) {
            for (int round = 0; round < problem.nRounds; round++) {
                assignments[assignment[round][ump]] = ump + 1;
            }
        }

        for (int round = 0; round < problem.nRounds; round++) {
            for (int i = 0; i < problem.nUmpires; i++) {
                for (int g = round * problem.nUmpires; g < (round + 1)
                  * problem.nUmpires; g++) {
                    if (assignments[g] - 1 == i) {
                        sb.append(" " + Arrays.toString(problem.games[g]) + " ");

                    }
                }
            }
            sb.append("\n");
        }
        for (int i = 0; i < problem.nUmpires; i++) {
            sb.append("--");
        }
        sb.append("\n");
        return sb.toString();

    }

    public void printAssignmentDetail() {
        for (int i = 1; i <= problem.nUmpires; i++) {
            System.out.print("-" + i + "-");
        }

        int[] assignments = new int[problem.nGames];
        for (int ump = 0; ump < problem.nUmpires; ump++) {
            for (int round = 0; round < problem.nRounds; round++) {
                assignments[assignment[round][ump]] = ump + 1;
            }
        }

        System.out.println();
        for (int round = 0; round < problem.nRounds; round++) {
            for (int i = 0; i < problem.nUmpires; i++) {
                for (int g = round * problem.nUmpires; g < (round + 1)
                  * problem.nUmpires; g++) {
                    if (assignments[g] - 1 == i) {
                        System.out.print(" "
                          + Arrays.toString(problem.games[g]) + " ");

                    }
                }
            }
            System.out.println();
        }
        for (int i = 0; i < problem.nUmpires; i++) {
            System.out.print("--");
        }
        System.out.println();
    }

    public void assignValue(int ump, int g, int round) {
        if (assignment[round][ump] != g) {
            beforeValueChanges(ump, round);
            assignment[round][ump] = g;
            afterValueChanges(ump, round);
        }
    }

    public int getDistance() {
        return travelDistance;
    }

    public boolean isFeasible() {
        return homeVisitViolations == 0 && consecutiveHomeVisitViolations == 0 && consecutiveTeamSeenViolations == 0;
    }

    public long checkImprovement(int umpire, int round, int newGame) {
        int newDistance = umpireDistances[umpire];
        int currentgame = assignment[round][umpire];
        int currentVenue = problem.games[currentgame][0] - 1;
        int currentOtherTeam = problem.games[currentgame][1] - 1;

        int newVenue = problem.games[newGame][0] - 1;
        int newOtherTeam = problem.games[newGame][1] - 1;


        if (round > 0) {
            newDistance -= problem.dist[problem.games[assignment[round - 1][umpire]][0] - 1][currentVenue];
            newDistance += problem.dist[problem.games[assignment[round - 1][umpire]][0] - 1][newVenue];
        }
        if (round < problem.nRounds - 1) {
            newDistance -= problem.dist[currentVenue][problem.games[assignment[round + 1][umpire]][0] - 1];
            newDistance += problem.dist[newVenue][problem.games[assignment[round + 1][umpire]][0] - 1];
        }

        int extraHomeVisitViolations = 0;
        if (visits[umpire][currentVenue] == 1)
            extraHomeVisitViolations++;
        if (visits[umpire][currentVenue] == 0)
            extraHomeVisitViolations--;

        int extraConsecutiveHomeVisitViolations = 0;
        for (int currentRound = round; currentRound < Math.min(problem.nRounds,
          round + problem.q1); currentRound++) {
            int value = homeVisits[currentRound][umpire][currentVenue] - 1;
            if (value == 1) {
                extraConsecutiveHomeVisitViolations--;
            }
            value = homeVisits[currentRound][umpire][newVenue] + 1;
            if (value == 2) {
                extraConsecutiveHomeVisitViolations++;
            }
        }

        int extraConsecutiveTeamSeenViolations = 0;
        for (int currentRound = round; currentRound < Math.min(problem.nRounds,
          round + problem.q2); currentRound++) {

            int value = teamVisits[currentRound][umpire][currentVenue] - 1;
            if (value == 1) {
                extraConsecutiveTeamSeenViolations--;
            }
            value = teamVisits[currentRound][umpire][currentOtherTeam] - 1;
            if (value == 1) {
                extraConsecutiveTeamSeenViolations--;
            }

            value = teamVisits[currentRound][umpire][newVenue] + 1;
            if (value == 2) {
                extraConsecutiveTeamSeenViolations++;
            }
            value = teamVisits[currentRound][umpire][newOtherTeam] + 1;
            if (value == 2) {
                extraConsecutiveTeamSeenViolations++;
            }
        }

        return extraHomeVisitViolations * Constants.penaltyWeight
          + extraConsecutiveHomeVisitViolations * Constants.penaltyWeight
          + extraConsecutiveTeamSeenViolations * Constants.penaltyWeight
          + newDistance - umpireDistances[umpire];
    }

    public void calculateScore() {
        calculateTravelDistance();
        calculateHomeVisitViolations();
        calculateConsecutiveViolations();
        scoreInitiallyCalculated = true;
    }

    public void printAssignment() {
        System.out.println(travelDistance);
        for (int round = 0; round < problem.nRounds; round++) {
            for (int ump = 0; ump < problem.nUmpires; ump++) {
                System.out.print(Character.toChars(assignment[round][ump] % problem.nUmpires + 'A'));
            }
            System.out.println();
        }
    }
}
