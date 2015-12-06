package be.kuleuven.codes.tup.heuristic;


import be.kuleuven.codes.tup.heuristic.move.*;
import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;

import java.util.*;

public class SteepestDescent {

    public final boolean OUTPUT = false;
    public AssignmentMove assignmentMove;
    public PartialAssignmentMove partialAssignmentMove;

    public SteepestDescent() {
        assignmentMove = new AssignmentMove(new Random());
        partialAssignmentMove = new PartialAssignmentMove(new Random());
    }

    public Solution solve(Problem problem, Solution sol0) {

        Solution current = new Solution(sol0);
        long currentObj = current.getObjectiveCost();
        boolean improved = true;
        int iter = 0;
        while (improved) {
            improved = false;

            int[] bestAssInIter = null;
            int bestRound = 0;
            long bestInIterOBj = currentObj;

            for (int round = 0; round < problem.nRounds; round++) {
                int[] originals = Arrays.copyOf(current.assignment[round], current.assignment[round].length);
                int[] newAss = assignmentMove.performMove(current, round);
                long newOBj = current.getObjectiveCost();
                if (newOBj < bestInIterOBj) {
                    if (OUTPUT)
                        System.out.println("[" + iter + "]" + "[ASSIGN] Found new best solution: " + newOBj);
                    bestAssInIter = newAss;
                    bestRound = round;
                    bestInIterOBj = newOBj;
                }

                for (int ump = 0; ump < problem.nUmpires; ump++) {
                    current.assignValue(ump, originals[ump], round);
                }
            }

            if (bestInIterOBj < currentObj) {
                //                System.out.println("Steepest Descent most improving round " + bestRound);
                //                System.out.println("New assignment");
                for (int ump = 0; ump < problem.nUmpires; ump++) {
                    //                    System.out.print(Character.toChars(bestAssInIter[ump] % problem.nUmpires + 'A'));
                    current.assignValue(ump, bestAssInIter[ump], bestRound);
                }
                //                System.out.println();
                currentObj = bestInIterOBj;
                improved = true;
            }

            iter++;
        }


        return current;


    }

    public PartialSolution solvePartial(Problem problem, PartialSolution sol0) {

        PartialSolution current = new PartialSolution(sol0);
        long currentObj = current.getObjectiveCost();
        boolean improved = true;
        int iter = 0;
        while (improved) {
            improved = false;

            int[] bestAssInIter = null;
            int bestRound = 0;
            long bestInIterOBj = currentObj;

            for (int round = sol0.firstRound; round <= sol0.lastRound; round++) {
                int[] originals = Arrays.copyOf(current.assignment[round], current.assignment[round].length);
                int[] newAss = partialAssignmentMove.performMove(current, round, sol0.firstRound, sol0.lastRound);
                long newOBj = current.getObjectiveCost();
                if (newOBj < bestInIterOBj) {
                    if (OUTPUT)
                        System.out.println("[" + iter + "]" + "[ASSIGN] Found new best solution: " + newOBj);
                    bestAssInIter = newAss;
                    bestRound = round;
                    bestInIterOBj = newOBj;
                }

                for (int ump = 0; ump < problem.nUmpires; ump++) {
                    current.assignValue(ump, originals[ump], round);
                }
            }

            if (bestInIterOBj < currentObj) {
                for (int ump = 0; ump < problem.nUmpires; ump++) {
                    current.assignValue(ump, bestAssInIter[ump], bestRound);
                }
                currentObj = bestInIterOBj;
                improved = true;
            }

            iter++;
        }

        return current;
    }
}