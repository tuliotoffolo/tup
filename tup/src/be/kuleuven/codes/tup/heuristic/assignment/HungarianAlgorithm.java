package be.kuleuven.codes.tup.heuristic.assignment;

import java.util.*;

/**
 * An implementation of the classic hungarian algorithm for the assignment problem.
 * <p>
 * Copyright 2007 Gary Baker (GPL v3)
 *
 * @author gbaker
 */
public class HungarianAlgorithm implements AssignmentAlgorithm {


    private int[][] zeroSequence;

    public int[][] computeAssignments(int[][] matrix) {


        // subtract minumum value from rows and columns to create lots of zeroes
        reduceMatrix(matrix);


        // non negative values are the index of the starred or primed zero in the row or column
        int[] starsByRow = new int[matrix.length];
        Arrays.fill(starsByRow, -1);
        int[] starsByCol = new int[matrix[0].length];
        Arrays.fill(starsByCol, -1);
        int[] primesByRow = new int[matrix.length];
        Arrays.fill(primesByRow, -1);

        // 1s mean covered, 0s mean not covered
        int[] coveredRows = new int[matrix.length];
        int[] coveredCols = new int[matrix[0].length];

        // star any zero that has no other starred zero in the same row or column
        initStars(matrix, starsByRow, starsByCol);
        coverColumnsOfStarredZeroes(starsByCol, coveredCols);

        while (!allAreCovered(coveredCols)) {

            int[] primedZero = primeSomeUncoveredZero(matrix, primesByRow, coveredRows, coveredCols);

            int whileCount = 0; //added for bug in while loop
            while (primedZero == null) {
                if (whileCount > 1000) return null; //ugly hack for bug in this while loop!!! added by Tony

                // keep making more zeroes until we find something that we can prime (i.e. a zero that is uncovered)
                makeMoreZeroes(matrix, coveredRows, coveredCols);
                primedZero = primeSomeUncoveredZero(matrix, primesByRow, coveredRows, coveredCols);
                whileCount++;
            }

            // check if there is a starred zero in the primed zero's row
            int columnIndex = starsByRow[primedZero[0]];
            if (-1 == columnIndex) {

                // if not, then we need to incrementPriority the zeroes and start over
                incrementSetOfStarredZeroes(primedZero, starsByRow, starsByCol, primesByRow);
                Arrays.fill(primesByRow, -1);
                Arrays.fill(coveredRows, 0);
                Arrays.fill(coveredCols, 0);
                coverColumnsOfStarredZeroes(starsByCol, coveredCols);
            }
            else {

                // cover the row of the primed zero and uncover the column of the starred zero in the same row
                coveredRows[primedZero[0]] = 1;
                coveredCols[columnIndex] = 0;
            }
        }

        // ok now we should have assigned everything
        // take the starred zeroes in each column as the correct assignments

        int[][] retval = new int[matrix.length][];
        for (int i = 0; i < starsByCol.length; i++) {
            retval[i] = new int[]{ starsByCol[i], i };
        }
        return retval;


    }

    private boolean allAreCovered(int[] coveredCols) {
        for (int covered : coveredCols) {
            if (0 == covered) return false;
        }
        return true;
    }


    /**
     * the first step of the hungarian algorithm
     * is to find the smallest element in each row
     * and subtract it's values from all elements
     * in that row
     *
     * @return the next step to perform
     */
    private void reduceMatrix(int[][] matrix) {

        for (int i = 0; i < matrix.length; i++) {

            // find the min value in the row
            int minValInRow = Integer.MAX_VALUE;
            for (int j = 0; j < matrix[i].length; j++) {
                if (minValInRow > matrix[i][j]) {
                    minValInRow = matrix[i][j];
                }
            }

            // subtract it from all values in the row
            for (int j = 0; j < matrix[i].length; j++) {
                matrix[i][j] -= minValInRow;
            }
        }

        for (int i = 0; i < matrix[0].length; i++) {
            int minValInCol = Integer.MAX_VALUE;
            for (int j = 0; j < matrix.length; j++) {
                if (minValInCol > matrix[j][i]) {
                    minValInCol = matrix[j][i];
                }
            }

            for (int j = 0; j < matrix.length; j++) {
                matrix[j][i] -= minValInCol;
            }

        }

    }


    private void initStars(int costMatrix[][], int[] starsByRow, int[] starsByCol) {


        int[] rowHasStarredZero = new int[costMatrix.length];
        int[] colHasStarredZero = new int[costMatrix[0].length];

        for (int i = 0; i < costMatrix.length; i++) {
            for (int j = 0; j < costMatrix[i].length; j++) {
                if (0 == costMatrix[i][j] && 0 == rowHasStarredZero[i] && 0 == colHasStarredZero[j]) {
                    starsByRow[i] = j;
                    starsByCol[j] = i;
                    rowHasStarredZero[i] = 1;
                    colHasStarredZero[j] = 1;
                    break; // move onto the next row
                }
            }
        }
    }


    /**
     * just marke the columns covered for any coluimn containing a starred zero
     *
     * @param starsByCol
     * @param coveredCols
     */
    private void coverColumnsOfStarredZeroes(int[] starsByCol, int[] coveredCols) {
        for (int i = 0; i < starsByCol.length; i++) {
            coveredCols[i] = -1 == starsByCol[i] ? 0 : 1;
        }
    }


    /**
     * finds some uncovered zero and primes it
     *
     * @param matrix
     * @param primesByRow
     * @param coveredRows
     * @param coveredCols
     * @return
     */
    private int[] primeSomeUncoveredZero(int matrix[][], int[] primesByRow,
                                         int[] coveredRows, int[] coveredCols) {


        // find an uncovered zero and prime it
        for (int i = 0; i < matrix.length; i++) {
            if (1 == coveredRows[i]) continue;
            for (int j = 0; j < matrix[i].length; j++) {
                // if it's a zero and the column is not covered
                if (0 == matrix[i][j] && 0 == coveredCols[j]) {

                    // ok this is an unstarred zero
                    // prime it
                    primesByRow[i] = j;
                    return new int[]{ i, j };
                }
            }
        }
        return null;

    }

    /**
     * @param unpairedZeroPrime
     * @param starsByRow
     * @param starsByCol
     * @param primesByRow
     */
    private void incrementSetOfStarredZeroes(int[] unpairedZeroPrime, int[] starsByRow, int[] starsByCol, int[] primesByRow) {

        // build the alternating zero sequence (prime, star, prime, star, etc)
        int i, j = unpairedZeroPrime[1];

        zeroSequence = new int[20][];
        int total = 0;
        zeroSequence[0] = unpairedZeroPrime;
        total++;
        boolean paired;
        do {
            i = starsByCol[j];
            paired = -1 != i;
            paired = paired && addZeroSequence(total, new int[]{ i, j });
            if (paired) total++;
            if (!paired) break;

            j = primesByRow[i];
            paired = -1 != j;
            paired = paired && addZeroSequence(total, new int[]{ i, j });
            if (paired) total++;

        } while (paired);


        // unstar each starred zero of the sequence
        // and star each primed zero of the sequence
        for (int k = 0; k < total; k++) {
            int[] zero = zeroSequence[k];
            if (starsByCol[zero[1]] == zero[0]) {
                starsByCol[zero[1]] = -1;
                starsByRow[zero[0]] = -1;
            }
            if (primesByRow[zero[0]] == zero[1]) {
                starsByRow[zero[0]] = zero[1];
                starsByCol[zero[1]] = zero[0];
            }
        }

    }

    private boolean addZeroSequence(int total, int[] ints) {
        for (int i = 0; i < total; i++) {
            if (Arrays.equals(zeroSequence[i], ints))
                return false;
        }
        if (total == zeroSequence.length) {
            zeroSequence = Arrays.copyOf(zeroSequence, total * 2);
        }
        zeroSequence[total] = ints;
        return true;
    }


    private void makeMoreZeroes(int[][] matrix, int[] coveredRows, int[] coveredCols) {

        // find the minimum uncovered value
        int minUncoveredValue = Integer.MAX_VALUE;
        for (int i = 0; i < matrix.length; i++) {
            if (0 == coveredRows[i]) {
                for (int j = 0; j < matrix[i].length; j++) {
                    if (0 == coveredCols[j] && matrix[i][j] < minUncoveredValue) {
                        minUncoveredValue = matrix[i][j];
                    }
                }
            }
        }

        // add the min value to all covered rows
        for (int i = 0; i < coveredRows.length; i++) {
            if (1 == coveredRows[i]) {
                for (int j = 0; j < matrix[i].length; j++) {
                    matrix[i][j] += minUncoveredValue;
                }
            }
        }

        // subtract the min value from all uncovered columns
        for (int i = 0; i < coveredCols.length; i++) {
            if (0 == coveredCols[i]) {
                for (int j = 0; j < matrix.length; j++) {
                    matrix[j][i] -= minUncoveredValue;
                }
            }
        }
    }


}