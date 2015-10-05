package be.kuleuven.codes.tup.bnb;

import be.kuleuven.codes.tup.model.*;
import be.kuleuven.codes.tup.useful.*;

import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * This class stores the priorities among the edges of the problem. The
 * priorities are initially given by the distance (the smaller the better).
 * However, as solutions for sub-problems are produced, these priorities are
 * updated, using the {@link #improvePriority(int, int)} function.
 * <p>
 * The idea is to give higher priority to edges that form (optimal) solutions
 * for the sub-problems, as they are known to be optimal for that subset of
 * rounds.
 *
 * @author Tulio Toffolo
 */
public class EdgePriority {

    public final Problem problem;

    /**
     * Matrix of [games]x[games] that keeps the priority of each edge
     * (important: the smaller the number, the higher the priority)
     */
    private final AtomicInteger priority[][];

    /**
     * Instantiates a new set of priorities for a given {@param problem}.
     *
     * @param problem the TUP problem instance.
     */
    public EdgePriority(Problem problem) {
        this.problem = problem;
        this.priority = new AtomicInteger[problem.nGames][problem.nGames];

        populatePriorityMatrix();
    }

    /**
     * Returns the priority of the edge connecting game {@param firstGame} to
     * game {@param secondGame}.
     *
     * @param firstGame  the first game.
     * @param secondGame the second game.
     * @return the priority of the edge connecting {@param i} to {@param j}.
     */
    public int get(int firstGame, int secondGame) {
        return priority[firstGame][secondGame].get();
    }

    /**
     * Improves the priority of the edge connecting game {@param firstGame} to
     * game {@param secondGame}.
     *
     * @param firstGame  the first game.
     * @param secondGame the second game.
     */
    public void improvePriority(int firstGame, int secondGame) {
        priority[firstGame][secondGame].addAndGet(-problem.nUmpires);
    }

    /**
     * This method populates the priority matrix by sorting the candidates after
     * each game and giving it a value in [0...m-1], where m is the number of
     * games in each round.
     */
    private void populatePriorityMatrix() {
        for (int r = 0; r < problem.nRounds - 1; r++) {
            for (int i = 0; i < problem.nUmpires; i++) {
                int firstGame = r * problem.nUmpires + i;

                // building candidate list of games after *i*
                PriorityQueue<PairInt> orders = new PriorityQueue<>(problem.nUmpires, (a, b) -> a.second - b.second);
                for (int j = 0; j < problem.nUmpires; j++) {
                    int secondGame = (r + 1) * problem.nUmpires + j;
                    if (problem.games[firstGame][0] != problem.games[secondGame][0])
                        orders.add(new PairInt(secondGame, problem.distGames[firstGame][secondGame]));
                }

                int priority = 0;
                while (!orders.isEmpty()) {
                    this.priority[firstGame][orders.poll().first] = new AtomicInteger(priority++);
                }
            }
        }
    }
}
