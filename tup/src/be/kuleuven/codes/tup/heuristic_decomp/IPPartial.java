package be.kuleuven.codes.tup.heuristic_decomp;

import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.model.solution.*;
import jads.mp.*;
import jads.mp.solvers.*;

import java.util.*;

/**
 * This class implements a partial IP for the TUP. Note that the JADS framework
 * is utilized (https://github.com/tuliotoffolo/jads).
 *
 * @author Tulio Toffolo
 */
public class IPPartial {

    public final Problem problem;

    public MPModel model;

    private SimpleSolution solution;

    private boolean feasibleSolution;
    private int firstRound, lastRound;
    private int firstGame, lastGame;

    private int minRound, maxRound;
    private int minGame, maxGame;

    private double penalty;


    private List<Edge> sources = new ArrayList<>();
    private List<Edge> sinks = new ArrayList<>();

    private List<Edge> edges = new ArrayList<>();
    private List<List<Edge>> edgesInGame = new ArrayList<>();
    private List<List<Edge>> edgesOutGame = new ArrayList<>();

    MPVar x[][], y[][];
    boolean fixed[][];
    double initialX[] = null;

    MPVar initialActiveVars[];
    double initialVals[];


    public IPPartial(Problem problem, SimpleSolution solution, int firstRound, int lastRound, boolean feasibleSolution) {
        this(problem, solution, firstRound, lastRound, feasibleSolution, 1e5);
    }

    public IPPartial(Problem problem, SimpleSolution solution, int firstRound, int lastRound, boolean feasibleSolution, double penalty) {
        this.problem = problem;
        this.solution = solution.clone();
        this.firstRound = firstRound;
        this.lastRound = lastRound;
        this.feasibleSolution = feasibleSolution;
        this.penalty = penalty;

        // computing first and last games indexes
        firstGame = firstRound * problem.nUmpires;
        lastGame = (lastRound + 1) * problem.nUmpires - 1;

        // computing minimum and maximum rounds
        minRound = Math.max(0, firstRound - (problem.q1 - 1));
        maxRound = feasibleSolution ? Math.min(problem.nRounds - 1, lastRound + (problem.q1 - 1)) : lastRound;

        // computing minimum and maximum game indexes
        minGame = minRound * problem.nUmpires;
        maxGame = (maxRound + 1) * problem.nUmpires - 1;

        // cleaning solution
        for (int g = lastGame; g >= firstGame; g--)
            this.solution.unsetColor(g);

        model = new MPModel(problem.name);

        createEdges();
        createVars();
        createConstraints();
        createValidInequalities();

        addInitialSolution(solution);
    }

    public SimplePartialSolution[] solve(int nSolutions, int nThreads, long timeLimitMillis) {
        MPSolver solver = new SolverGurobi(model, false);
        model.setSolver(solver);

        // setting solver options
        //if (!feasibleSolution)
        solver.setParam(MPSolver.DoubleParam.MIPGap, 0.0001);
        solver.setParam(MPSolver.DoubleParam.TimeLimit, timeLimitMillis / 1000.0);
        solver.setParam(MPSolver.IntParam.Threads, nThreads);

        // setting initial solution (if applicable)
        if (initialActiveVars != null)
            solver.addSolution(initialActiveVars, initialVals);

        // solving model
        if (!solver.solve())
            return new SimplePartialSolution[]{};

        // capturing solution(s)
        ArrayList<double[]> solverSols = solver.getSolutions();
        nSolutions = Math.min(nSolutions, solverSols.size());
        SimplePartialSolution partialSolutions[] = new SimplePartialSolution[nSolutions];

        for (int i = 0; i < nSolutions; i++) {
            double[] solverSol = solverSols.get(i);
            partialSolutions[i] = new SimplePartialSolution(problem, firstRound, lastRound);

            for (int game = firstGame; game <= lastGame; game++) {
                for (Edge edge : edgesInGame.get(game)) {
                    for (int u = 0; u < problem.nUmpires; u++) {
                        if (Math.round(solverSol[x[edge.id][u].getIndex()]) == 1) {
                            assert (game == edge.target);
                            partialSolutions[i].setColor(edge.target, u);
                            //solution.assignment[edge.target / problem.nUmpires][u] = edge.target;
                        }
                    }
                }
            }
        }

        return partialSolutions;
    }


    private void createEdges() {
        // creating edgesInGame and edgesOutGame lists
        for (int g = 0; g <= maxGame; g++) {
            edgesInGame.add(new LinkedList<>());
            edgesOutGame.add(new LinkedList<>());
        }

        // adding edges from source node
        for (int nextGame = minGame; nextGame < minGame + problem.nUmpires; nextGame++) {
            Edge edge = new Edge(sources.size(), -1, nextGame, 0);
            sources.add(edge);
            edgesInGame.get(nextGame).add(edge);
        }

        // adding edges between games of consecutive rounds
        for (int round = minRound; round <= maxRound - 1; round++) {
            for (int g1 = 0; g1 < problem.nUmpires; g1++) {
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
        for (int prevGame = maxGame - problem.nUmpires + 1; prevGame <= maxGame; prevGame++) {
            Edge edge = new Edge(sources.size() + edges.size() + sinks.size(), prevGame, -1, 0);
            sinks.add(edge);
            edgesOutGame.get(prevGame).add(edge);
        }
    }

    private void createVars() {
        // creating the main variables
        x = new MPVar[sources.size() + edges.size() + sinks.size()][problem.nUmpires];
        fixed = new boolean[sources.size() + edges.size() + sinks.size()][problem.nUmpires];

        for (int u = 0; u < problem.nUmpires; u++) {

            // creating variables for edges from source node
            for (Edge edge : sources) {
                if (problem.gameToRound[edge.target] == 0) {
                    int value = edge.target == u ? 1 : 0;
                    x[edge.id][u] = model.addIntVar(value, value, edge.cost, String.format("x(%d,%d,%d)", edge.source, edge.target, u));
                }
                else if (edge.target >= firstGame && edge.target <= lastGame) {
                    x[edge.id][u] = model.addBinVar(edge.cost, String.format("x(%d,%d,%d)", edge.source, edge.target, u));
                }
                else {
                    if (solution.x[edge.target] == u)
                        fixed[edge.id][u] = true;
                }
            }

            // creating variables for intermediate edges
            for (Edge edge : edges) {
                if (edge.target >= firstGame && edge.target <= lastGame) {
                    x[edge.id][u] = model.addBinVar(edge.cost, String.format("x(%d,%d,%d)", edge.source, edge.target, u));
                }
                else if (edge.source >= firstGame && edge.source <= lastGame) {
                    x[edge.id][u] = model.addBinVar(edge.cost, String.format("x(%d,%d,%d)", edge.source, edge.target, u));
                }
                else {
                    if (solution.x[edge.source] == solution.x[edge.target] && solution.x[edge.target] == u)
                        fixed[edge.id][u] = true;
                }
            }

            // creating variables for edges to sink node
            if (maxRound == lastRound) {
                for (Edge edge : sinks)
                    x[edge.id][u] = model.addBinVar(edge.cost, String.format("x(%d,%d,%d)", edge.source, edge.target, u));
            }
            else {
                for (Edge edge : sinks) {
                    if (solution.x[edge.source] == u)
                        fixed[edge.id][u] = true;
                }
            }
        }

        // creating counter for non-visited locations
        y = new MPVar[problem.nUmpires][problem.nTeams];
        if (!feasibleSolution)
            for (int u = 0; u < problem.nUmpires; u++)
                for (int t = 0; t < problem.nTeams; t++)
                    if (solution.colorsLocations[u][t] == 0)
                        y[u][t] = model.addBinVar(penalty > 0 ? Math.sqrt(firstRound + 1) * penalty + (problem.nTeams - solution.colorsLocationsCount[u]) : 0, "y(%d,%d)", u, t);
    }

    private void createConstraints() {
        // creating constraints (2)
        for (int g = minGame; g <= maxGame; g++) {
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
        for (int g = minGame; g <= maxGame; g++) {
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

            model.addEq(lhs, 1, String.format("c3(%d,-1)", u));
        }

        // creating constraints (4)
        for (int t = 0; t < problem.nTeams; t++) {
            for (int u = 0; u < problem.nUmpires; u++) {
                if (solution.colorsLocations[u][t] > 0)
                    continue;

                MPLinExpr lhs = new MPLinExpr();
                if (y[u][t] != null) lhs.addTerm(1.0, y[u][t]);

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
            for (int r = minRound; r <= maxRound; r++) {
                for (int u = 0; u < problem.nUmpires; u++) {
                    MPLinExpr lhs = new MPLinExpr();

                    for (int round = r; round <= Math.min(maxRound, r + problem.q1 - 1); round++) {
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
            for (int r = minRound; r <= maxRound; r++) {
                for (int u = 0; u < problem.nUmpires; u++) {
                    MPLinExpr lhs = new MPLinExpr();

                    for (int round = r; round <= Math.min(maxRound, r + problem.q2 - 1); round++) {
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
        else if (fixed[edgeId][u])
            expr.addConstant(coeff);
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


    private void addInitialSolution(SimpleSolution solution) {
        List<MPVar> varList = new ArrayList<>();

        for (int game = firstGame; game <= lastGame; game++) {
            for (Edge edge : edgesInGame.get(game)) {
                if (edge.source == -1 || (solution.getColor(edge.target) != -1 && solution.getColor(edge.source) == solution.getColor(edge.target))) {
                    varList.add(x[edge.id][solution.getColor(edge.target)]);
                }
            }
        }

        if (varList.size() > 0) {
            initialActiveVars = varList.toArray(new MPVar[varList.size()]);
            initialVals = new double[initialActiveVars.length];
            Arrays.fill(initialVals, 1.0);
        }
    }


    class Edge {

        final int id, source, target;
        final double cost;

        Edge(int id, int source, int target, double cost) {
            this.id = id;
            this.source = source;
            this.target = target;
            this.cost = cost;
        }
    }
}
