package be.kuleuven.codes.tup.heuristic_decomp;

import be.kuleuven.codes.tup.io.*;
import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;

import java.io.*;
import java.util.*;

/**
 * This class implements the Main method that executes the decomposition-based
 * heuristic algorithm for TUP. Additional functions are included to print the
 * right usage of the solver.
 *
 * @author Tulio Toffolo
 */
public class Main {

    /**
     * Stores information of the current build.
     */
    public static final String INFO = "Decomposition-based heuristic employing ( omega * sqrt(r) + unattended_locations(u) )\n" +
      "    in the objective function to penalize the number of unvisited locations. MIPGap=0.0001 and\n" +
      "    penalty automatically increased by 25 (up to 500) in case of infeasibility.";

    /**
     * Stores the version of current build;
     */
    public static final String VERSION = "jul-22";

    // mandatory arguments
    public static String instance;
    public static int q1, q2;
    public static String output;

    // optional arguments
    public static int n = 4;
    public static int step = n / 2;
    public static double penalty = 0;
    public static String input = null;
    public static int threads = Runtime.getRuntime().availableProcessors();
    public static long timeLimitMillis = 10800 * 1000; // 1 hour

    /**
     * The entry point of application.
     *
     * @param args the input arguments
     * @throws IOException if any IO error occurs.
     */
    public static void main(String args[]) throws IOException {
        Locale.setDefault(new Locale("en-US"));
        System.out.printf("Version '%s'\n", VERSION);
        System.out.printf("    %s\n\n", INFO);

        try {
            // reading input from terminal
            readOptions(args);
        }
        catch (Exception e) {
            printUsage();
            return;
        }

        File f = new File(instance);
        String problemName = instance.substring(instance.lastIndexOf("/") + 1, instance.lastIndexOf("."));
        Problem problem = ProblemReader.readProblemFromFile(f, q1, q2, problemName);

        System.out.printf("Instance: %s-%d,%d\n", problemName, q1, q2);
        System.out.printf("Parameters: n=%d, step=%d, penalty=%.0f\n", n, step, penalty);

        Solution solution = null;
        if (input != null)
            solution = SolutionReader.readSolutionFromFile(problem, new File(input));

        long startTime = System.currentTimeMillis();
        Heuristic heuristic = new Heuristic(problem, n, step, penalty);
        solution = heuristic.solve(solution, threads, timeLimitMillis);
        long endTime = System.currentTimeMillis();

        if (solution != null)
            SolutionWriter.writeSolutionToFile(solution, output);

        System.out.println();
        System.out.printf("Instance: %s-%d,%d\n", problemName, q1, q2);
        System.out.printf("Total runtime: %.2fs\n", (endTime - startTime) / 1000.0);
        if (solution != null)
            System.out.printf("Best cost: %s%s\n", solution.getDistance(), solution.isFeasible() ? "" : String.format(" (constraint 'c' violated %d times)", solution.homeVisitViolations));
        else
            System.out.printf("Solutions is infeasible!\n");
    }

    /**
     * Print the help message showing how to use the solver.
     */
    public static void printUsage() {
        System.out.printf("Usage: java -jar tup_heuristic.jar <instance> <q1> <q2> <output> [options]\n");
        System.out.printf("    <instance>   : Instance name (example: umps14.dat).\n");
        System.out.printf("    <q1>         : Value for the q1 parameter (example: 7).\n");
        System.out.printf("    <q2>         : Value for the q2 parameter  (example: 3).\n");
        System.out.printf("    <output>     : Output solution file (example: umps14_7_3.sol).\n");
        System.out.println();
        System.out.printf("Options:\n");
        System.out.printf("    -n <n>       : Value for algorithm parameter 'n' (default: %d).\n", n);
        System.out.printf("    -step <s>    : Value for algorithm parameter 'step' (default: %d).\n", step);
        System.out.printf("    -penalty <p> : Value for algorithm parameter 'penalty' (default: %.0f).\n", penalty);
        System.out.printf("    -init <file> : Load initial solution from file.\n");
        System.out.printf("    -threads <t> : Maximum number of threads to use (default: number of CPUs).\n");
        System.out.printf("    -time <time> : Runtime limit (in seconds) (default: %s).\n", timeLimitMillis / 1000);
        System.out.println();
        System.out.printf("Examples:\n");
        System.out.printf("    java -jar tup.jar umps_30.txt 5 5 umps_14_7_3.sol\n");
        System.out.printf("    java -jar tup.jar umps_30.txt 5 5 umps_14_7_3.sol -n 5 -step 2\n");
        System.out.printf("    java -jar tup.jar umps_30.txt 5 5 umps_14_7_3.sol -n 5 -step 2 -ini umps_30_5_5.sol\n");
        System.out.println();
    }

    /**
     * Reads options given as arguments.
     *
     * @param args the arguments.
     */
    public static void readOptions(String[] args) {
        // printing command line (useful for identifying the command)
        System.out.printf("Arguments:");
        for (String arg : args)
            System.out.printf(" %s", arg);
        System.out.printf("\n\n");

        int index = -1;

        // mandatory arguments
        instance = args[++index];
        q1 = new Integer(args[++index]);
        q2 = new Integer(args[++index]);
        output = args[++index];

        while (index < args.length - 1) {
            String option = args[++index];

            switch (option) {
                case "-n":
                    n = new Integer(args[++index]);
                    break;
                case "-step":
                    step = new Integer(args[++index]);
                    break;
                case "-penalty":
                    penalty = new Double(args[++index]);
                    break;
                case "-ini":
                    input = args[++index];
                    break;
                case "-threads":
                    threads = new Integer(args[++index]);
                    break;
                case "-time":
                    timeLimitMillis = new Integer(args[++index]) * 1000;
                    break;
            }
        }
    }
}
