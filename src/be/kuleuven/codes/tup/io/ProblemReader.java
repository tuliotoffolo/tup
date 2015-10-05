package be.kuleuven.codes.tup.io;

import be.kuleuven.codes.tup.model.*;

import java.io.*;
import java.util.*;

public class ProblemReader {

    public static Problem readProblemFromFile(File file, int d1, int d2, String name) throws FileNotFoundException {
        //System.out.println("Reading TUP from file " + file.getAbsolutePath()
        //		+ " with parameters " + d1 + " and " + d2);

        Scanner scanner = new Scanner(file);
        int nTeams = 0;
        int[][] dist = null;
        int[][] opponents = null;
        String line;
        while (scanner.hasNextLine()) {
            line = scanner.nextLine();
            if (line.contains("nTeams")) {
                nTeams = Integer.parseInt(line.split("=")[1].split(";")[0]
                  .trim());
                // System.out.println("nTeams = "+nTeams);
            }
            if (line.contains("dist")) {
                dist = new int[nTeams][nTeams];
                for (int i = 0; i < nTeams; i++) {
                    line = scanner.nextLine();
                    String[] parts = line.replace("[", "").replace("]", "")
                      .trim().split(" ");
                    int pos = 0;
                    for (int j = 0; j < parts.length; j++) {
                        if (parts[j].trim().length() > 0)
                            dist[i][pos++] = Integer.parseInt(parts[j].trim());
                    }
                }
                // System.out.println("dist = "+Arrays.deepToString(dist));
            }
            if (line.contains("opponents")) {
                opponents = new int[2 * nTeams - 2][nTeams];
                for (int i = 0; i < 2 * nTeams - 2; i++) {
                    line = scanner.nextLine();
                    String[] parts = line.replace("[", "").replace("]", "")
                      .trim().split(" ");
                    int pos = 0;
                    for (int j = 0; j < parts.length; j++) {
                        if (parts[j].trim().length() > 0)
                            opponents[i][pos++] = Integer.parseInt(parts[j]
                              .trim());
                    }
                }
                // System.out.println("opponents = "+Arrays.deepToString(opponents));
            }
        }

        scanner.close();

        return new Problem(nTeams, dist, opponents, d1, d2, name);
    }
}
