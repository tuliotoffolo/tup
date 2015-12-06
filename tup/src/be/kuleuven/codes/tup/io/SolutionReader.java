package be.kuleuven.codes.tup.io;

import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;

import java.io.*;
import java.util.*;

public class SolutionReader {

    public static Solution readSolutionFromFile(Problem problem, File file) throws FileNotFoundException {
        Scanner scanner = new Scanner(file);

        String firstLine = scanner.nextLine();
        scanner.close();

        if (!firstLine.contains(",")) {
            return readAlternativeSolutionFromFile(problem, file);
        }

        String[] parts = firstLine.split(",");
        int[][] assignments = new int[problem.nRounds][problem.nUmpires];
        int p = 0;

        for (int r = 0; r < problem.nRounds; r++) {
            for (int gir = 0; gir < problem.nUmpires; gir++) {
                int u = new Integer(parts[p].trim()) - 1;
                assignments[r][u] = p;
                p++;
            }
        }

        Solution solution = new Solution(problem);
        solution.assignment = assignments;

        return solution;
    }

    public static Solution readAlternativeSolutionFromFile(Problem problem, File file) throws FileNotFoundException {
        Scanner scanner = new Scanner(file);

        int[][] assignments = new int[problem.nRounds][problem.nUmpires];
        for (int u = 0; u < problem.nUmpires; u++) {
            String[] line = scanner.nextLine().split(" ");
            assert line.length == problem.nRounds;

            for (int r = 0; r < problem.nRounds; r++) {
                int team = new Integer(line[r]);
                assignments[r][u] = problem.roundHomeTeamToGame[r][team - 1];
            }
        }

        Solution solution = new Solution(problem);
        solution.assignment = assignments;

        return solution;
    }
}
