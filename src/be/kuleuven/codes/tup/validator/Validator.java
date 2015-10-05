package be.kuleuven.codes.tup.validator;

import be.kuleuven.codes.tup.io.*;
import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;

import java.io.*;
import java.util.*;

public class Validator {

    /**
     * Runs the validator.
     *
     * @param args the input arguments
     * @throws IOException if any IO error occurs.
     */
    public static void main(String args[]) throws IOException {
        Locale.setDefault(new Locale("en-US"));

        File instanceFile = null;
        int q1 = 0, q2 = 0;
        String instanceName = null;

        try {
            instanceFile = new File(args[0]);
            q1 = new Integer(args[1]);
            q2 = new Integer(args[2]);
            instanceName = instanceFile.getName();
        }
        catch (Exception e) {
            printUsageAndExit();
        }

        try {
            Problem problem = ProblemReader.readProblemFromFile(instanceFile, q1, q2, instanceName);

            String solutionPath = args[3];
            File solFile = new File(solutionPath);
            Solution solution = SolutionReader.readSolutionFromFile(problem, solFile);

            solution.calculateScore();
            if (solution.getDistance() != solution.getObjectiveCost() || !solution.isFeasible()) {
                System.out.println("Solution is infeasible!");
                System.exit(-1); //exit with code -1 for infeasible
            }

            System.out.println(solution.getDistance());
        }
        catch (Exception e) {
            System.out.println("Error while reading and/or checking the solution.");
            System.out.println("Please, ensure the solution file is consistent.");
            System.exit(-1); //exit with code -1 for infeasible
        }
    }

    /**
     * Print the help message showing how to use the solver.
     */
    public static void printUsageAndExit() {
        System.out.println("Usage: java -jar validator.jar <instance> <q1> <q2> <solution>");
        System.out.println("    <instance> : Instance name (example: umps14).");
        System.out.println("    <q1>       : Value for parameter q1 (example: 7).");
        System.out.println("    <q2>       : Value for parameter q2 (example: 3).");
        System.out.println("    <solution> : Output solution file (example: umps14_7_3.txt).");
        System.out.println();
        System.out.println("Example:");
        System.out.println("    java -jar validator.jar umps14.txt 7 3 umps14_7_3.txt");
        System.out.println();
        System.exit(-1); //exit with code -1 for infeasible
    }
}
