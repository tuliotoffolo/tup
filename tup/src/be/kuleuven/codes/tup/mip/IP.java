package be.kuleuven.codes.tup.mip;


import be.kuleuven.codes.tup.io.*;
import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;
import jads.mp.*;
import jads.mp.solvers.*;

import java.io.*;
import java.util.*;

public class IP {

    public final MPModel model;
    public final Problem problem;

    private List<Edge> sources = new ArrayList<>();
    private List<Edge> sinks = new ArrayList<>();

    private List<Edge> edges = new ArrayList<>();
    private List<List<Edge>> edgesInGame = new ArrayList<>();
    private List<List<Edge>> edgesOutGame = new ArrayList<>();

    MPVar x[][];


    public IP(Problem problem) {
        this.problem = problem;
        model = new MPModel(problem.name);

        // creating edges
        createEdges();
        createVariables();
        createConstraints();
        createValidInequalities();
    }

    public Solution solve() {
        model.setSolver(new SolverGurobi(model));
//        SolverLocalSolver localSolver = (SolverLocalSolver) model.getSolver();
//        localSolver.getLocalSolver().getModel().close();
//        localSolver.getLocalSolver().getParam().setAnnealingLevel(9);
//        //model.getSolver().writeModel("ejor_bnb/test.lp");
//
//        LSPhase phase = localSolver.getLocalSolver().createPhase();
//        localSolver.getLocalSolver().solve();


//        model.getSolver().solve();

        Solution solution = new Solution(problem);
        //            solution.assignment = new int[problem.nGames];
        //            for (int i = 0; i < problem.nTeams; i++) {
        //                for (int j = 0; j < problem.nTeams; j++) {
        //                    for (int s = 0; s < problem.nRounds; s++) {
        //                        for (int u = 0; u < problem.nUmpires; u++) {
        //                            GRBVar zVar = null;
        //                            if (s==0){
        //                                zVar=z[i][j][s][u];
        //                            }else{
        //                                zVar=z[j][i][s-1][u];
        //                            }
        //
        //                            if (Math.round(zVar.get(DoubleAttr.X)) == 1) {
        //                                for (int g = s*problem.nUmpires; g < (s+1)*problem.nUmpires; g++) {
        //                                    if (problem.games[g][0]==i+1){
        //                                        solution.assignment[g]=u+1;
        //                                        break;
        //                                    }
        //                                }
        //
        //                            }
        //                        }
        //                    }
        //                }
        //            }

        return solution;
    }


    private void createEdges() {
        // creating edgesInGame and edgesOutGame lists
        for (int g = 0; g < problem.nGames; g++) {
            edgesInGame.add(new LinkedList<>());
            edgesOutGame.add(new LinkedList<>());
        }

        // adding edges from source node
        for (int nextGame = 0; nextGame < problem.nUmpires; nextGame++) {
            Edge edge = new Edge(sources.size(), -1, nextGame, 0);
            sources.add(edge);
            edgesInGame.get(nextGame).add(edge);
        }

        // adding edges between games of consecutive rounds
        for (int g1 = 0; g1 < problem.nUmpires; g1++) {
            for (int round = 0; round < problem.nRounds - 1; round++) {
                int prevGame = round * problem.nUmpires + g1;
                int nextRound = round + 1;

                int homeTeam = problem.games[prevGame][0];
                int awayTeam = problem.games[prevGame][1];

                for (int g2 = 0; g2 < problem.nUmpires; g2++) {
                    int nextGame = nextRound * problem.nUmpires + g2;

                    if (problem.q2 > 1 && (problem.games[nextGame][0] == homeTeam
                      || problem.games[nextGame][0] == awayTeam
                      || problem.games[nextGame][1] == homeTeam
                      || problem.games[nextGame][1] == awayTeam))
                        continue;

                    if (problem.q1 > 1 && problem.games[nextGame][0] == homeTeam)
                        continue;

                    Edge edge = new Edge(sources.size() + edges.size(), prevGame, nextGame, problem.distGames[prevGame][nextGame]);
                    edges.add(edge);
                    edgesOutGame.get(prevGame).add(edge);
                    edgesInGame.get(nextGame).add(edge);
                }
            }
        }

        // adding edges to sink node
        for (int prevGame = problem.nGames - problem.nUmpires; prevGame < problem.nGames; prevGame++) {
            Edge edge = new Edge(sources.size() + edges.size() + sinks.size(), prevGame, -1, 0);
            sinks.add(edge);
            edgesOutGame.get(prevGame).add(edge);
        }
    }

    private void createVariables() {
        // creating the variables
        x = new MPVar[sources.size() + edges.size() + sinks.size()][problem.nUmpires];
        for (int u = 0; u < problem.nUmpires; u++) {
            for (Edge edge : sources)
                x[edge.id][u] = model.addVar(edge.target == u ? 1 : 0, edge.target == u ? 1 : 0, 'B', String.format("x(%d,src,%d,%d)", edge.target / problem.nUmpires, edge.target, u));
            for (Edge edge : edges)
                x[edge.id][u] = model.addBinVar(edge.cost, String.format("x(%d,%d,%d,%d)", edge.target / problem.nUmpires, edge.source, edge.target, u));
            for (Edge edge : sinks)
                x[edge.id][u] = model.addBinVar(edge.cost, String.format("x(%d,%d,sink,%d)", 1 + edge.source / problem.nUmpires, edge.source, u));
        }
    }

    private void createConstraints() {
        // creating constraints (2)
        for (int g = 0; g < problem.nGames; g++) {
            MPLinExpr in = new MPLinExpr();
            for (int u = 0; u < problem.nUmpires; u++)
                for (Edge edge : edgesInGame.get(g))
                    in.addTerm(1.0, x[edge.id][u]);

            MPLinExpr out = new MPLinExpr();
            for (int u = 0; u < problem.nUmpires; u++)
                for (Edge edge : edgesOutGame.get(g))
                    out.addTerm(1.0, x[edge.id][u]);

            model.addEq(in, 1, String.format("c2_in(%d)", g));
            model.addEq(out, 1, String.format("c2_out(%d)", g));
        }

        // creating constraints (3) for source node
        for (int u = 0; u < problem.nUmpires; u++) {
            MPLinExpr lhs = new MPLinExpr();
            for (Edge edge : sources)
                lhs.addTerm(1.0, x[edge.id][u]);

            model.addEq(lhs, 1, String.format("c3(src,%d)", u));
        }

        // creating constraints (3)
        for (int g = 0; g < problem.nGames; g++) {
            for (int u = 0; u < problem.nUmpires; u++) {
                MPLinExpr lhs = new MPLinExpr();
                for (Edge edge : edgesInGame.get(g))
                    lhs.addTerm(1.0, x[edge.id][u]);

                MPLinExpr rhs = new MPLinExpr();
                for (Edge edge : edgesOutGame.get(g))
                    rhs.addTerm(1.0, x[edge.id][u]);

                model.addEq(lhs, rhs, String.format("c3(%d,%d)", g, u));
            }
        }

        // creating constraints (3) for sink node
        for (int u = 0; u < problem.nUmpires; u++) {
            MPLinExpr lhs = new MPLinExpr();
            for (Edge edge : sinks)
                lhs.addTerm(1.0, x[edge.id][u]);

            model.addEq(lhs, 1, String.format("c3(%d,sink)", u));
        }

        // creating constraints (4)
        for (int t = 0; t < problem.nTeams; t++) {
            for (int u = 0; u < problem.nUmpires; u++) {
                MPLinExpr lhs = new MPLinExpr();

                for (Edge edge : sources)
                    if (problem.games[edge.target][0] == t + 1)
                        lhs.addTerm(1.0, x[edge.id][u]);
                for (Edge edge : edges)
                    if (problem.games[edge.target][0] == t + 1)
                        lhs.addTerm(1.0, x[edge.id][u]);


                model.addGe(lhs, 1.0, String.format("c4(%d,%d)", t, u));
            }
        }

        // creating constraints (5)
        for (int t = 0; t < problem.nTeams; t++) {
            for (int r = 0; r < problem.nRounds; r++) {
                for (int u = 0; u < problem.nUmpires; u++) {
                    MPLinExpr lhs = new MPLinExpr();

                    for (int round = r; round < Math.min(problem.nRounds, r + problem.q1); round++) {
                        int game = problem.roundHomeTeamToGame[round][t];
                        if (game < 0) continue;

                        for (Edge edge : edgesInGame.get(game))
                            lhs.addTerm(1.0, x[edge.id][u]);
                    }

                    if (!lhs.isEmpty())
                        model.addLe(lhs, 1, String.format("c5(%d,%d,%d)", t, r, u));
                }
            }
        }

        // creating constraints (6)
        for (int t = 0; t < problem.nTeams; t++) {
            for (int r = 0; r < problem.nRounds; r++) {
                for (int u = 0; u < problem.nUmpires; u++) {
                    MPLinExpr lhs = new MPLinExpr();

                    for (int round = r; round < Math.min(problem.nRounds, r + problem.q2); round++) {
                        int game = problem.roundTeamToGame[round][t];

                        for (Edge edge : edgesInGame.get(game))
                            lhs.addTerm(1.0, x[edge.id][u]);
                    }

                    if (!lhs.isEmpty())
                        model.addLe(lhs, 1, String.format("c6(%d,%d,%d)", t, r, u));
                }
            }
        }
    }


    private void createValidInequalities() {
        //for (int g = 0; g < problem.nGames; g++) {
        //    for (int u = 0; u < problem.nUmpires; u++) {
        //        MPLinExpr in = new MPLinExpr();
        //        for (Edge edge : edgesInGame.get(g))
        //            in.addTerm(1.0, x[edge.id][u]);
        //
        //        MPLinExpr out = new MPLinExpr();
        //        for (Edge edge : edgesOutGame.get(g))
        //            out.addTerm(1.0, x[edge.id][u]);
        //
        //        model.addLe(in, 1, String.format("c3_in(%d,%d)", g, u));
        //        model.addLe(out, 1, String.format("c3_out(%d,%d)", g, u));
        //    }
        //}
        //
        //for (int r = 0; r < problem.nRounds; r++) {
        //    MPLinExpr in = new MPLinExpr();
        //    MPLinExpr out = new MPLinExpr();
        //
        //    for (int g = 0; g < problem.nUmpires; g++) {
        //        int game = r * problem.nUmpires + g;
        //
        //        for (int u = 0; u < problem.nUmpires; u++)
        //            for (Edge edge : edgesInGame.get(g))
        //                in.addTerm(1.0, x[edge.id][u]);
        //
        //        for (int u = 0; u < problem.nUmpires; u++)
        //            for (Edge edge : edgesOutGame.get(g))
        //                out.addTerm(1.0, x[edge.id][u]);
        //    }
        //
        //    model.addEq(in, problem.nUmpires, String.format("sum_in(%d)", r));
        //    model.addEq(out, problem.nUmpires, String.format("sum_out(%d)", r));
        //}
    }


    static class Edge {

        public final int id, source, target;
        public final double cost;

        public Edge(int id, int source, int target, double cost) {
            this.id = id;
            this.source = source;
            this.target = target;
            this.cost = cost;
        }
    }


    public static void main(String args[]) throws FileNotFoundException {
        Locale.setDefault(new Locale("en-US"));

        String argsInstance[] = args[0].split("_");
        String instance = argsInstance[0];
        int q1 = new Integer(argsInstance[1]);
        int q2 = new Integer(argsInstance[2]);

        System.out.println("Instance: " + args[0]);
        System.out.println();

        File f = new File("data/" + instance + ".dat");
        Problem problem = ProblemReader.readProblemFromFile(f, q1, q2, instance);

        long startTime = System.currentTimeMillis();
        IP ip = new IP(problem);
        Solution solution = ip.solve();
        long endTime = System.currentTimeMillis();

        ip.model.getSolver().writeModel("../lp/" + instance + "_" + q1 + "_" + q2 + ".lp.gz");

        System.out.println();
        System.out.printf("Instance.......: %s_%d_%d\n", problem.name, problem.q1, problem.q2);
        System.out.printf("Best cost......: %.0f\n", ip.model.getSolver().getObjValue());
        //System.out.printf("Number of nodes: %d\n", ip.model.getNNodes());
        System.out.printf("Total runtime..: %.2fs\n", (endTime - startTime) / 1000.0);
    }
}
