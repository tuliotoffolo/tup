package be.kuleuven.codes.tup.heuristic.move;

import be.kuleuven.codes.tup.heuristic.assignment.*;
import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;

import java.util.*;

public class PartialAssignmentMove {

    private final Random r;

    public PartialAssignmentMove(Random r) {
        this.r = r;
    }

    public int[] performMove(Solution sol, int round, int firstRound, int lastRound) {
        //        System.out.println("ROUND_" + round);

        Problem problem = sol.problem;

        int[][] costMatrix = new int[problem.nUmpires][problem.nUmpires];


        for (int ump = 0; ump < problem.nUmpires; ump++) {

            for (int game = 0; game < problem.nUmpires; game++) {

                int prevGame = -1;
                if (round > firstRound) {
                    prevGame = sol.assignment[round - 1][ump];
                }
                int currentGame = problem.nUmpires * round + game;
                int currentVenue = problem.games[currentGame][0] - 1;
                int nextGame = -1;
                if (round < lastRound) {
                    nextGame = sol.assignment[round + 1][ump];
                }

                //Calculate distance due to assignment
                int distance = 0;
                if (prevGame != -1) {
                    distance += problem.dist[problem.games[prevGame][0] - 1][currentVenue];
                }
                if (nextGame != -1) {
                    distance += problem.dist[currentVenue][problem.games[nextGame][0] - 1];
                }

                //Calculate extra home visit violations due to assignment
                //int homeVisitViolations = 0;
                //int[] visits = Arrays.copyOf(sol.visits[ump], sol.visits[ump].length);
                //visits[problem.games[sol.assignment[round][ump]][0] - 1]--;
                //visits[currentVenue]++;
                //for (int team = 0; team < problem.nTeams; team++) {
                //    if (visits[team] == 0) homeVisitViolations++;
                //}


                //Calculate consecutive violations
                int consecutiveHomeVisitViolations = 0;
                int consecutiveTeamSeenViolations = 0;

                int otherTeam = problem.games[currentGame][1] - 1;

                //if (currentGame == sol.assignment[round][ump]) {
                //    for (int currentRound = Math.max(firstRound, round - problem.q1); currentRound < round; currentRound++) {
                //        if (sol.homeVisits[currentRound][ump][currentVenue] == 2)
                //            consecutiveHomeVisitViolations++;
                //    }
                //    for (int currentRound = Math.max(firstRound, round - problem.q2); currentRound < round; currentRound++) {
                //        if (sol.teamVisits[currentRound][ump][currentVenue] == 2)
                //            consecutiveTeamSeenViolations++;
                //        if (sol.teamVisits[currentRound][ump][otherTeam] == 2)
                //            consecutiveTeamSeenViolations++;
                //    }
                //}
                //else {
                //    for (int currentRound = Math.max(firstRound, round - problem.q1); currentRound < round; currentRound++) {
                //        if (sol.homeVisits[currentRound][ump][currentVenue] == 1)
                //            consecutiveHomeVisitViolations++;
                //    }
                //    for (int currentRound = Math.max(firstRound, round - problem.q2); currentRound < round; currentRound++) {
                //        if (sol.teamVisits[currentRound][ump][currentVenue] == 1)
                //            consecutiveTeamSeenViolations++;
                //        if (sol.teamVisits[currentRound][ump][otherTeam] == 1)
                //            consecutiveTeamSeenViolations++;
                //    }
                //}

                if (currentGame == sol.assignment[round][ump]) {
                    for (int currentRound = round; currentRound < Math.min(lastRound + 1, round + problem.q1); currentRound++) {
                        if (sol.homeVisits[currentRound][ump][currentVenue] == 2)
                            consecutiveHomeVisitViolations++;
                    }
                    for (int currentRound = round; currentRound < Math.min(lastRound + 1, round + problem.q2); currentRound++) {
                        if (sol.teamVisits[currentRound][ump][currentVenue] == 2)
                            consecutiveTeamSeenViolations++;
                        if (sol.teamVisits[currentRound][ump][otherTeam] == 2)
                            consecutiveTeamSeenViolations++;
                    }
                }
                else {
                    for (int currentRound = round; currentRound < Math.min(lastRound + 1, round + problem.q1); currentRound++) {
                        if (sol.homeVisits[currentRound][ump][currentVenue] == 1)
                            consecutiveHomeVisitViolations++;
                    }
                    for (int currentRound = round; currentRound < Math.min(lastRound + 1, round + problem.q2); currentRound++) {
                        if (sol.teamVisits[currentRound][ump][currentVenue] == 1)
                            consecutiveTeamSeenViolations++;
                        if (sol.teamVisits[currentRound][ump][otherTeam] == 1)
                            consecutiveTeamSeenViolations++;
                    }
                }
                costMatrix[ump][game] = distance
                  + Constants.penaltyWeight2 * consecutiveTeamSeenViolations
                  + Constants.penaltyWeight2 * consecutiveHomeVisitViolations;

            }
        }

        HungarianAlgorithm hungarianAlgorithm = new HungarianAlgorithm();
        int[][] assignments = hungarianAlgorithm.computeAssignments(costMatrix);

        int[] newAss = new int[problem.nUmpires];

        if (assignments != null) {
            for (int[] assignment : assignments) {
                int ump = assignment[0];
                int g = assignment[1] + round * problem.nUmpires;
                newAss[ump] = g;
            }
            for (int ump = 0; ump < problem.nUmpires; ump++) {
                sol.assignValue(ump, newAss[ump], round);
            }
        }
        return newAss;
    }
}
