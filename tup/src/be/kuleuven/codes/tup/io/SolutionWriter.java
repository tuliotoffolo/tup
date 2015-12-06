package be.kuleuven.codes.tup.io;

import be.kuleuven.codes.tup.model.solution.*;

import java.io.*;

public class SolutionWriter {

    public static void writeSolutionToFile(Solution solution) throws FileNotFoundException {
        long obj = solution.getObjectiveCost();
        writeSolutionToFile(solution, "newsolutions/" + solution.problem.name + "-" + solution.problem.q1 + "-" + solution.problem.q2 + "-" + obj + ".txt");
    }

    public static void writeSolutionToFile(Solution solution, String filePath) throws FileNotFoundException {
        solution.calculateScore();
        PrintWriter pWriter = new PrintWriter(new File(filePath));

        int[] assignments = new int[solution.problem.nGames];
        for (int ump = 0; ump < solution.problem.nUmpires; ump++) {
            for (int round = 0; round < solution.problem.nRounds; round++) {
                assignments[solution.assignment[round][ump]] = ump + 1;
            }
        }

        for (int ump : assignments) {
            pWriter.print(ump + ",");
        }

        pWriter.println();
        pWriter.println("---");
        pWriter.println(solution);


        pWriter.close();
    }

    public static void writeAlternativeSolutionToFile(Solution solution, String filePath) throws FileNotFoundException {
        solution.calculateScore();
        PrintWriter pWriter = new PrintWriter(new File(filePath));

        int[][] assignments = new int[solution.problem.nUmpires][solution.problem.nRounds];

        for (int u = 0; u < solution.problem.nUmpires; u++) {
            for (int r = 0; r < solution.problem.nRounds; r++) {
                if (r < solution.problem.nRounds - 1)
                    pWriter.printf("%d ", solution.problem.games[solution.assignment[r][u]][0]);
                else
                    pWriter.printf("%d", solution.problem.games[solution.assignment[r][u]][0]);
            }
            pWriter.println();
        }

        pWriter.println("---");
        pWriter.print(solution);

        pWriter.close();
    }
}
