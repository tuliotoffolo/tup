package be.kuleuven.codes.tup.bnb;

import be.kuleuven.codes.tup.io.*;
import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;

import java.io.*;
import java.util.*;

/**
 * This class implements the Main method that executes the branch-and-bound.
 * Additional functions are included to print the right usage of the solver.
 *
 * @author Tulio Toffolo
 */
public class Main {

    /**
     * Stores information of the current build.
     */
    public static final String INFO = "Updated version of the branch-and-bound with sorted games per umpire.\n" +
      "    This version introduces an \"EdgePriority\" class to determine the priority of each edge.\n" +
      "    The idea is to give higher priority to edges that form optimal solutions for sub-problems.\n" +
      "    (these priorities are used only in the \"partial\" branch-and-bound).\n";

    /**
     * Stores the version of current build;
     */
    public static final String VERSION = "oct-01";

    private static int ub = Integer.MAX_VALUE;
    private static int maxThreads = 2;//Runtime.getRuntime().availableProcessors();
    private static long timeLimitMillis = 72 * 60 * 60 * 1000;
    private static boolean useTimeWindows = true;


    /**
     * The entry point of application.
     *
     * @param args the input arguments
     * @throws IOException          if any IO error occurs.
     * @throws InterruptedException if the solver is interrupted.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        Locale.setDefault(new Locale("en-US"));

        String instance, outputPath;
        int q1, q2;
        try {
            // reading input from terminal
            instance = args[0].lastIndexOf(".") != -1 ? args[0].substring(0, args[0].lastIndexOf(".")) : args[0];
            q1 = new Integer(args[1]);
            q2 = new Integer(args[2]);
            outputPath = args[3];
            readOptions(args);
        }
        catch (Exception e) {
            printUsage();
            return;
        }

        File f = new File(args[0]);
        Problem problem = ProblemReader.readProblemFromFile(f, q1, q2, instance);

        System.out.printf("Instance: %s_%d_%d\n", instance, q1, q2);
        System.out.printf("Version: %s\n", VERSION);
        System.out.printf("    %s\n", INFO);

        long startTime = System.currentTimeMillis();
        BranchAndBound solver = new BranchAndBound(problem);
        Solution solution = solver.solve(ub, maxThreads, timeLimitMillis, useTimeWindows);
        long endTime = System.currentTimeMillis();

        if (solution != null)
            SolutionWriter.writeSolutionToFile(solution, outputPath);

        System.out.println();
        System.out.printf("Best solution cost.: %s\n", solver.getUB() == Integer.MAX_VALUE ? "infeasible" : solver.getUB());
        System.out.printf("Number of nodes....: %d\n", solver.getNNodes());
        System.out.printf("Total runtime......: %.2fs\n", (endTime - startTime) / 1000.0);
    }

    /**
     * Reads options given as arguments.
     *
     * @param args the arguments.
     */
    public static void readOptions(String[] args) {
        int index = 3;

        while (index < args.length - 1) {
            String option = args[++index];

            switch (option) {
                case "-no-windows":
                    useTimeWindows = false;
                    break;
                case "-threads":
                    maxThreads = new Integer(args[++index]);
                    break;
                case "-time":
                    timeLimitMillis = ( int ) (new Double(args[++index]) * 60 * 1000);
                    break;
                case "-ub":
                    ub = new Integer(args[++index]) + 1;
                    break;
            }
        }
    }

    /**
     * Print the help message showing how to use the solver.
     */
    public static void printUsage() {
        System.out.println("Usage: java -jar tup.jar <instance> <q1> <q2> <output> [options]");
        System.out.println("    <instance>   : Instance name (example: umps14.dat).");
        System.out.println("    <q1>         : Value for the q1 parameter (example: 7).");
        System.out.println("    <q2>         : Value for the q2 parameter  (example: 3).");
        System.out.println("    <output>     : Output solution file (example: umps14_7_3.sol).");
        System.out.println();
        System.out.println("Options:");
        System.out.println("    -no-windows  : Run the lower bound without multiple time windows.");
        System.out.println("    -threads <n> : Maximum number n of threads (default: number of CPUs).");
        System.out.println("    -time <time> : Time limit, in minutes (default: 4320).");
        System.out.println("    -ub <ub>     : Initial upper bound (default: unbounded).");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("    java -jar tup.jar umps_14.txt 7 3 umps_14_7_3.sol");
        System.out.println("    java -jar tup.jar umps_14.txt 7 3 umps_14_7_3.sol -threads 8 -time 4320 -ub 164440");
        System.out.println();
        System.out.println("Version: " + VERSION);
        System.out.println("    " + INFO);
        System.exit(-1);
    }
}
