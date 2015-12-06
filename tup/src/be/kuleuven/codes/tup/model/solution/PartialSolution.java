package be.kuleuven.codes.tup.model.solution;

import be.kuleuven.codes.tup.model.*;

import java.util.*;

public class PartialSolution extends Solution {

    public final int firstRound, lastRound;

    public PartialSolution(Problem problem, int firstRound, int lastRound) {
        super(problem);
        this.firstRound = firstRound;
        this.lastRound = lastRound;

        visits = new int[problem.nUmpires][problem.nTeams];
    }

    public PartialSolution(PartialSolution sol) {
        super(sol);
        firstRound = sol.firstRound;
        lastRound = sol.lastRound;

        visits = new int[problem.nUmpires][problem.nTeams];
    }

    public long getObjectiveCost() {
        if (!scoreInitiallyCalculated) {
            calculateTravelDistance();
            calculateConsecutiveViolations();
            scoreInitiallyCalculated = true;
        }
        return travelDistance + Constants.penaltyWeight * consecutiveHomeVisitViolations
          + Constants.penaltyWeight * consecutiveTeamSeenViolations;
    }

    private void beforeValueChanges(int umpire, int round) {
        int currentgame = assignment[round][umpire];
        int venue = problem.games[currentgame][0] - 1;
        int otherTeam = problem.games[currentgame][1] - 1;

        if (round > firstRound) {
            travelDistance -= problem.dist[problem.games[assignment[round - 1][umpire]][0] - 1][venue];
            umpireDistances[umpire] -= problem.dist[problem.games[assignment[round - 1][umpire]][0] - 1][venue];
        }
        if (round < lastRound) {
            travelDistance -= problem.dist[venue][problem.games[assignment[round + 1][umpire]][0] - 1];
            umpireDistances[umpire] -= problem.dist[venue][problem.games[assignment[round + 1][umpire]][0] - 1];
        }

        for (int currentRound = round; currentRound < Math.min(lastRound + 1, round + problem.q1); currentRound++) {
            int value = --homeVisits[currentRound][umpire][venue];
            if (value == 1) {
                consecutiveHomeVisitViolations--;
            }
        }

        for (int currentRound = round; currentRound < Math.min(lastRound + 1, round + problem.q2); currentRound++) {
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

        if (round > 0) {
            travelDistance += problem.dist[problem.games[assignment[round - 1][umpire]][0] - 1][venue];
            umpireDistances[umpire] += problem.dist[problem.games[assignment[round - 1][umpire]][0] - 1][venue];
        }
        if (round < problem.nRounds - 1) {
            travelDistance += problem.dist[venue][problem.games[assignment[round + 1][umpire]][0] - 1];
            umpireDistances[umpire] += problem.dist[venue][problem.games[assignment[round + 1][umpire]][0] - 1];
        }

        for (int currentRound = round; currentRound < Math.min(lastRound + 1, round + problem.q1); currentRound++) {
            int value = ++homeVisits[currentRound][umpire][venue];
            if (value == 2) {
                consecutiveHomeVisitViolations++;
            }
        }

        for (int currentRound = round; currentRound < Math.min(lastRound + 1, round + problem.q2); currentRound++) {
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
            for (int round = firstRound + 1; round <= lastRound; round++) {
                travelDistance += problem.dist[problem.games[assignment[round - 1][ump]][0] - 1][problem.games[assignment[round][ump]][0] - 1];
                umpireDistances[ump] += problem.dist[problem.games[assignment[round - 1][ump]][0] - 1][problem.games[assignment[round][ump]][0] - 1];
            }
        }
    }

    public void calculateConsecutiveViolations() {
        consecutiveHomeVisitViolations = 0;
        consecutiveTeamSeenViolations = 0;

        homeVisits = new int[problem.nRounds][problem.nUmpires][problem.nTeams];
        teamVisits = new int[problem.nRounds][problem.nUmpires][problem.nTeams];

        for (int ump = 0; ump < problem.nUmpires; ump++) {
            for (int round = firstRound; round <= lastRound; round++) {
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
            for (int round = firstRound; round <= lastRound; round++) {
                for (int team = 0; team < problem.nTeams; team++) {
                    if (homeVisits[round][ump][team] > 1)
                        consecutiveHomeVisitViolations++;
                    if (teamVisits[round][ump][team] > 1)
                        consecutiveTeamSeenViolations++;
                }
            }
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        PartialSolution that = ( PartialSolution ) o;

        if (firstRound != that.firstRound || lastRound != that.lastRound)
            return false;

        if (!Arrays.equals(assignment, that.assignment))
            return false;

        return super.equals(o);
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

    public void calculateScore() {
        calculateTravelDistance();
        calculateConsecutiveViolations();
        scoreInitiallyCalculated = true;
    }
}
