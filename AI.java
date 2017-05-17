package ataxx;

import java.util.ArrayList;
import static java.lang.Math.min;
import static java.lang.Math.max;
/**
 *  An AI player that determines its own moves.
 *  @author Chris Jereza
 */

public class AI extends Player {

    /** Maximum minimax search depth before going to static evaluation. */
    private static final int MAX_DEPTH = 4;

    /** A magnitude greater than a normal value. */
    private static final int INFTY = Integer.MAX_VALUE;

    /** A new AI for GAME that will play MYCOLOR. */
    AI(Game game, PieceColor myColor) {
        super(game, myColor);
        _myColor = super.myColor();
    }

    /** Return my move for this turn. */
    @Override
    Move myMove() {
        if (!board().canMove(myColor())) {
            System.out.println(myColor() + " passes.");
            return Move.pass();
        }
        Move move = findMax(new Board(board()), MAX_DEPTH, -INFTY, INFTY);
        System.out.println(myColor() + " moves " + move.toString() + ".");
        return move;
    }

    /** Maximize my score based on opponent's possible responses.
     *  @param b the board on which I am playing the current game.
     *  @param depth the number of moves ahead used in calculation.
     *  @param alpha limit for minimizing player.
     *  @param beta limit for maximizing player.
     *  @return maximized possible move.
     */
    private Move findMax(Board b, int depth, double alpha, double beta) {
        if (depth == 0 || b.gameOver()) {
            return simpleFindMax(b, alpha, beta);
        }
        Move bestSoFar = Move.negInfinityMove();
        ArrayList<Move> possibleMoves = allMoves(b, b.whoseMove());
        for (Move m : possibleMoves) {
            if (b.legalMove(m)) {
                b.makeMove(m);
                Move response = findMin(b, depth - 1, alpha, beta);
                double responseVal = value(response, b);
                b.undo();
                if (responseVal >= value(bestSoFar, b)) {
                    bestSoFar = m;
                    alpha = max(alpha, responseVal);
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
        }
        return bestSoFar;
    }

    /** Return opponent's minimized possible score.
     *  @param b the board on which I am playing the current game.
     *  @param depth the number of moves ahead used in calculation.
     *  @param alpha limit for minimizing player.
     *  @param beta limit for maximizing player.
     */
    private Move findMin(Board b, int depth, double alpha, double beta) {
        if (depth == 0 || b.gameOver()) {
            return simpleFindMin(b, alpha, beta);
        }
        Move bestSoFar = Move.infinityMove();
        ArrayList<Move> possibleMoves = allMoves(b, b.whoseMove());
        for (Move m : possibleMoves) {
            if (b.legalMove(m)) {
                b.makeMove(m);
                Move response = findMax(b, depth - 1, alpha, beta);
                double responseVal = value(response, b);
                b.undo();
                if (bestSoFar.isInf() || responseVal <= value(bestSoFar, b)) {
                    bestSoFar = m;
                    beta = min(beta, responseVal);
                    if (beta <= alpha) {
                        break;
                    }
                }
            }
        }
        return bestSoFar;
    }

    /** Return move with maximized heuristic, based on next possible position.
     *  @param b the board on which I am playing the current game.
     *  @param alpha limit for minimizing player.
     *  @param beta limit for maximizing player.
     *  @return maximized possible move. */
    private Move simpleFindMax(Board b, double alpha, double beta) {
        if (maxPlayerWon(b)) {
            return Move.infinityMove();
        } else if (minPlayerWon(b)) {
            return Move.negInfinityMove();
        }
        Move bestSoFar = Move.negInfinityMove();
        ArrayList<Move> possibleMoves = allMoves(b, _myColor);
        for (Move m : possibleMoves) {
            if (b.legalMove(m)) {
                if (bestSoFar.isNegInf() || value(m, b) > value(bestSoFar, b)) {
                    bestSoFar = m;
                    alpha = max(alpha, value(m, b));
                    if (beta <= alpha) {
                        return bestSoFar;
                    }
                }
            }
        }
        return bestSoFar;
    }

    /** Return move with minimized heuristic, based on opponent's
     *  next possible position.
     *  @param b the board on which I am playing the current game.
     *  @param alpha limit for minimizing player.
     *  @param beta limit for maximizing player.
     *  @return minimized possible move. */
    private Move simpleFindMin(Board b, double alpha, double beta) {
        if (maxPlayerWon(b)) {
            return Move.infinityMove();
        } else if (minPlayerWon(b)) {
            return Move.negInfinityMove();
        }
        Move bestSoFar = Move.infinityMove();
        ArrayList<Move> possibleMoves = allMoves(b, _myColor.opposite());
        for (Move m : possibleMoves) {
            if (b.legalMove(m)) {
                if (value(m, b) < value(bestSoFar, b)) {
                    bestSoFar = m;
                    alpha = min(alpha, value(m, b));
                    if (beta <= alpha) {
                        return bestSoFar;
                    }
                }
            }
        }
        return bestSoFar;
    }

    /** Return ArrayList of all possible moves of player COLOR on Board B. */
    private ArrayList<Move> allMoves(Board b, PieceColor color) {
        ArrayList<Move> allMoves = new ArrayList<>();
        for (int r = '1'; r <= '7'; r += 1) {
            for (int c = 'a'; c <= 'g'; c += 1) {
                if (b.get((char) c, (char) r) == color) {
                    ArrayList<Move> allPieceMoves = allPieceMoves(b,
                            (char) r, (char) c);
                    allMoves.addAll(allPieceMoves);
                }
            }
        }
        return allMoves;
    }

    /** Return ArrayList of all possible moves of piece at
     *  ROW0 and COL0 on Board B. */
    private ArrayList<Move> allPieceMoves(Board b, char row0, char col0) {
        ArrayList<Move> allPieceMoves = new ArrayList<>();
        for (int r = -2; r <= 2; r += 1) {
            for (int c = -2; c <= 2; c += 1) {
                if (!(c == 0 && r == 0)) {
                    char col1 = (char) (col0 + c);
                    char row1 = (char) (row0 + r);
                    Move move = Move.move(col0, row0, col1, row1);
                    if (b.legalMove(move)) {
                        allPieceMoves.add(move);
                    }
                }
            }
        }
        return allPieceMoves;
    }

    /** Return true iff I (maximizer) win.
     *  @param b board on which I am playing.
     */
    private boolean maxPlayerWon(Board b) {
        return b.gameOver()
                && b.numPieces(_myColor) > b.numPieces(_myColor.opposite());
    }

    /** Return true iff opponent (minimizer) wins.
     *  @param b board on which I am playing.
     */
    private boolean minPlayerWon(Board b) {
        return b.gameOver()
                && b.numPieces(_myColor) < b.numPieces(_myColor.opposite());
    }

    /** Return heuristic value of a move MOVE on Board B. */
    private double value(Move move, Board b) {
        if (move.isInf()) {
            return INFTY;
        } else if (move.isNegInf()) {
            return -INFTY;
        } else {
            b.makeMove(move);
            int result = b.numPieces(_myColor)
                    - b.numPieces(_myColor.opposite());
            b.undo();
            return result;
        }
    }

    /** My color. */
    private PieceColor _myColor;
}
