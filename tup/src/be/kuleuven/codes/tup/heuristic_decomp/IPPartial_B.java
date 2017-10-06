package be.kuleuven.codes.tup.heuristic_decomp;

import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;
import be.kuleuven.codes.tup.useful.*;
import jads.mp.*;
import jads.mp.solvers.*;

import java.util.*;

/**
 * This class implements an alternative partial IP for the TUP. Note that the
 * JADS framework is utilized (https://github.com/tuliotoffolo/jads).
 *
 * @author Tulio Toffolo
 */
public class IPPartial_B {

    public final Problem problem;

    public MPModel model;

    protected int firstRound, lastRound;
    protected int firstGame, lastGame;

    protected SimpleSolution solution;

    protected List<Edge> sources = new ArrayList<>();
    protected List<Edge> sinks = new ArrayList<>();

    protected List<Edge> edges = new ArrayList<>();
    protected List<List<Edge>> edgesInGame = new ArrayList<>();
    protected List<List<Edge>> edgesOutGame = new ArrayList<>();

    protected HashMap<PairInt, Edge> edgeMap = new HashMap<>();

    MPVar x[][];
    double fixed[][];
    double initialX[] = null;


    public IPPartial_B(Problem problem) {
        this.problem = problem;

        createEdges();
    }

    public void makeModel(SimpleSolution solution, int firstRound, int lastRound) {
        this.firstRound = firstRound;
        this.lastRound = lastRound;
        this.solution = solution;

        // computing first and last games
        firstGame = firstRound * problem.nUmpires;
        lastGame = lastRound * problem.nUmpires + problem.nUmpires - 1;

        model = new MPModel(problem.name);

        createVars();
        createConstraints();
        createValidInequalities();

        // passing current solution to solver
        createInitialSimpleSolution();
    }

    public SimplePartialSolution[] solve() {
        MPSolver solver = new SolverGurobi(model, false);
        model.setSolver(solver);
        solver.solve();

        SimplePartialSolution partialSolution = new SimplePartialSolution(problem, firstRound, lastRound);
        for (int umpire = 0; umpire < problem.nUmpires; umpire++) {
            for (int game = firstGame; game <= lastGame; game++) {
                for (Edge edge : edgesInGame.get(game)) {
                    if (Math.round(solver.getValue(x[edge.id][umpire])) == 1) {
                        assert (game == edge.target);
                        partialSolution.setColor(edge.target, umpire);
                        //solution.assignment[edge.target / problem.nUmpires][u] = edge.target;
                    }
                }
            }
        }

        return new SimplePartialSolution[]{ partialSolution };
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


    private void createVars() {
        // creating the variables
        x = new MPVar[sources.size() + edges.size() + sinks.size()][problem.nUmpires];
        fixed = new double[sources.size() + edges.size() + sinks.size()][problem.nUmpires];

        // creating variables for source and sink edges
        for (int u = 0; u < problem.nUmpires; u++) {
            for (Edge edge : sources)
                x[edge.id][u] = model.addVar(edge.target == u ? 1 : 0, edge.target == u ? 1 : 0, 'B', String.format("x(%d,%d,%d)", edge.source, edge.target, u));
            for (Edge edge : sinks)
                x[edge.id][u] = model.addBinVar(edge.cost, String.format("x(%d,%d,%d)", edge.source, edge.target, u));
        }

        for (int game = 0; game < problem.nGames; game++) {

            // if round is part of the subproblem
            if (game >= firstGame && game <= lastGame) {
                for (Edge edge : edgesInGame.get(game)) {
                    for (int u = 0; u < problem.nUmpires; u++) {
                        x[edge.id][u] = model.addBinVar(edge.cost, String.format("x(%d,%d,%d)", edge.source, edge.target, u));
                    }
                }
            }

            // else if solution has allocation for the game
            else if (solution.getColor(game) != -1) {
                for (Edge edge : edgesInGame.get(game)) {
                    if (solution.getColor(edge.source) == solution.getColor(edge.target)) {
                        fixed[edge.id][solution.getColor(edge.target)] = 1;
                    }
                }
            }
        }
    }

    private void createConstraints() {
        // creating constraints (2)
        for (int g = 0; g < problem.nGames; g++) {
            MPLinExpr in = new MPLinExpr();
            for (int u = 0; u < problem.nUmpires; u++)
                for (Edge edge : edgesInGame.get(g))
                    addTerm(in, 1.0, edge.id, u);

            MPLinExpr out = new MPLinExpr();
            for (int u = 0; u < problem.nUmpires; u++)
                for (Edge edge : edgesOutGame.get(g))
                    addTerm(out, 1.0, edge.id, u);

            model.addEq(in, 1, String.format("c2_in(%d)", g));
            model.addEq(out, 1, String.format("c2_out(%d)", g));
        }

        // creating constraints (3) for source node
        for (int u = 0; u < problem.nUmpires; u++) {
            MPLinExpr lhs = new MPLinExpr();
            for (Edge edge : sources)
                addTerm(lhs, 1.0, edge.id, u);

            model.addEq(lhs, 1, String.format("c3(-1,%d)", u));
        }

        // creating constraints (3)
        for (int g = 0; g < problem.nGames; g++) {
            for (int u = 0; u < problem.nUmpires; u++) {
                MPLinExpr lhs = new MPLinExpr();
                for (Edge edge : edgesInGame.get(g))
                    addTerm(lhs, 1.0, edge.id, u);

                MPLinExpr rhs = new MPLinExpr();
                for (Edge edge : edgesOutGame.get(g))
                    addTerm(rhs, 1.0, edge.id, u);

                model.addEq(lhs, rhs, String.format("c3(%d,%d)", g, u));
            }
        }

        // creating constraints (3) for sink node
        for (int u = 0; u < problem.nUmpires; u++) {
            MPLinExpr lhs = new MPLinExpr();
            for (Edge edge : sinks)
                addTerm(lhs, 1.0, edge.id, u);

            model.addEq(lhs, 1, String.format("c3(-1,%d)", u));
        }

        // creating constraints (4)
        for (int t = 0; t < problem.nTeams; t++) {
            for (int u = 0; u < problem.nUmpires; u++) {
                MPLinExpr lhs = new MPLinExpr();

                for (Edge edge : sources)
                    if (problem.games[edge.target][0] == t + 1)
                        addTerm(lhs, 1.0, edge.id, u);
                for (Edge edge : edges)
                    if (problem.games[edge.target][0] == t + 1)
                        addTerm(lhs, 1.0, edge.id, u);


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
                            addTerm(lhs, 1.0, edge.id, u);
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
                            addTerm(lhs, 1.0, edge.id, u);
                    }

                    if (!lhs.isEmpty())
                        model.addLe(lhs, 1, String.format("c6(%d,%d,%d)", t, r, u));
                }
            }
        }
    }

    private void addTerm(MPLinExpr expr, double coeff, int edgeId, int u) {
        if (x[edgeId][u] != null)
            expr.addTerm(coeff, x[edgeId][u]);
        else
            expr.addConstant(coeff * fixed[edgeId][u]);
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

    private void createInitialSimpleSolution() {
        //initialX =
    }


    class Edge {

        final int id, source, target;
        final double cost;

        Edge(int id, int source, int target, double cost) {
            this.id = id;
            this.source = source;
            this.target = target;
            this.cost = cost;

            edgeMap.put(new PairInt(this.source, this.target), this);
        }
    }
}
