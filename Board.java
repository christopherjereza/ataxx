package ataxx;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Formatter;
import java.util.Observable;
import static ataxx.PieceColor.*;
import static ataxx.GameException.error;

/** An Ataxx board.   The squares are labeled by column (a char value between
 *  'a' - 2 and 'g' + 2) and row (a char value between '1' - 2 and '7'
 *  + 2) or by linearized index, an integer described below.  Values of
 *  the column outside 'a' and 'g' and of the row outside '1' to '7' denote
 *  two layers of border squares, which are always blocked.
 *  This artificial border (which is never actually printed) is a common
 *  trick that allows one to avoid testing for edge conditions.
 *  For example, to look at all the possible moves from a square, sq,
 *  on the normal board (i.e., not in the border region), one can simply
 *  look at all squares within two rows and columns of sq without worrying
 *  about going off the board. Since squares in the border region are
 *  blocked, the normal logic that prevents moving to a blocked square
 *  will apply.
 *
 *  For some purposes, it is useful to refer to squares using a single
 *  integer, which we call its "linearized index".  This is simply the
 *  number of the square in row-major order (counting from 0).
 *
 *  Moves on this board are denoted by Moves.
 *  @author Chris Jereza
 */
class Board extends Observable {

    /** Number of squares on a side of the board. */
    static final int SIDE = 7;
    /** Length of a side + an artificial 2-deep border region. */
    static final int EXTENDED_SIDE = SIDE + 4;

    /** Number of non-extending moves before game ends. */
    static final int JUMP_LIMIT = 25;

    /** Bottom-most row. */
    static final int BOTTOM_ROW = '1';

    /** Top-most row. */
    static final int TOP_ROW = '7';

    /** Left-most column. */
    static final int LEFT_COL = 'a';

    /** Right-most column. */
    static final int RIGHT_COL = 'g';

    /** Constant used to reflect a row across board center. */
    static final int ROW_REF = 104;

    /** Constant used to reflect a column across the board center. */
    static final int COL_REF = 200;

    /** Constant distance from rightmost space in a row to the
     *  leftmost space in the row below it. */
    static final int ROW_SHIFT = 18;

    /** Linearized index to the left of the
     *  bottom-left-most index in playing space. */
    static final int BOTTOM_INDEX = 23;

    /** Linearized index to the right of the
     *  top-right-most index in playing space. */
    static final int TOP_INDEX = 97;

    /** Left-most index of top row. */
    static final int ROW_START = 90;

    /** Right-most index of top row. */
    static final int ROW_END = 97;

    /** A new, cleared board at the start of the game. */
    Board() {
        _board = new PieceColor[EXTENDED_SIDE * EXTENDED_SIDE];
        clear();
    }

    /** A copy of B. */
    Board(Board b) {
        _board = b._board.clone();
        _whoseMove = b.whoseMove();
        _numBlocks = b.numPieces(BLOCKED);
        _numRed = b.redPieces();
        _numBlue = b.bluePieces();
        _numJumps = b.numJumps();
        _moves = b.allMoves();
        _undoStack = b.undoStack();
        _undoFlipStack = b.undoFlipStack();
        _numJumpStack = b.numJumpStack();
    }

    /** Return the linearized index of square COL ROW. */
    static int index(char col, char row) {
        return (row - '1' + 2) * EXTENDED_SIDE + (col - 'a' + 2);
    }

    /** Return the linearized index of the square that is DC columns and DR
     *  rows away from the square with index SQ. */
    static int neighbor(int sq, int dc, int dr) {
        return sq + dc + dr * EXTENDED_SIDE;
    }

    /** Clear me to my starting state, with pieces in their initial
     *  positions and no blocks. */
    void clear() {
        _numRed = 0;
        _numBlue = 0;
        _numBlocks = 0;
        _whoseMove = RED;
        for (int i = 0; i < _board.length; i += 1) {
            if (isBorder(i)) {
                _board[i] = BLOCKED;
            } else {
                _board[i] = EMPTY;
            }
        }
        _moves = new ArrayList<>();
        _undoStack = new Stack<>();
        _undoFlipStack = new Stack<>();
        _numJumpStack = new Stack<>();
        set('a', '1', BLUE);
        set('g', '1', RED);
        set('a', '7', RED);
        set('g', '7', BLUE);
        setChanged();
        notifyObservers();
    }

    /** Return true iff the game is over: i.e., if neither side has
     *  any moves, if one side has no pieces, or if there have been
     *  MAX_JUMPS consecutive jumps without intervening extends. */
    boolean gameOver() {
        return _numJumps >= JUMP_LIMIT || numPieces(BLUE) == 0
                || numPieces(RED) == 0 || (!canMove(BLUE) && !canMove(RED));
    }

    /** Return number of red pieces on the board. */
    int redPieces() {
        return numPieces(RED);
    }

    /** Return number of blue pieces on the board. */
    int bluePieces() {
        return numPieces(BLUE);
    }

    /** Return number of COLOR pieces on the board. */
    int numPieces(PieceColor color) {
        if (color == RED) {
            return _numRed;
        } else if (color == BLUE) {
            return _numBlue;
        } else {
            return _numBlocks;
        }
    }

    /** Increment numPieces(COLOR) by K. */
    private void incrPieces(PieceColor color, int k) {
        if (color == RED) {
            _numRed += k;
        } else if (color == BLUE) {
            _numBlue += k;
        } else if (color == BLOCKED) {
            _numBlocks += k;
        }
    }

    /** The current contents of square CR, where 'a'-2 <= C <= 'g'+2, and
     *  '1'-2 <= R <= '7'+2.  Squares outside the range a1-g7 are all
     *  BLOCKED.  Returns the same value as get(index(C, R)). */
    PieceColor get(char c, char r) {
        return _board[index(c, r)];
    }

    /** Return the current contents of square with linearized index SQ. */
    PieceColor get(int sq) {
        return _board[sq];
    }

    /** Set get(C, R) to V, where 'a' <= C <= 'g', and
     *  '1' <= R <= '7'. */
    private void set(char c, char r, PieceColor v) {
        set(index(c, r), v, c, r);
    }

    /** Set square with linearized index SQ to V.  This operation is
     *  undoable. Stores column C and row R as the string representation
     *  of a move to be pushed onto undoFlipStack.
     */
    private void set(int sq, PieceColor v, char c, char r) {
        _board[sq] = v;
        incrPieces(v, 1);
        _numJumpStack.push(_numJumps);
        _undoStack.push("" + c + r);
        _undoFlipStack.push(new ArrayList<>());
    }

    /** Set square at C R to V (not undoable). */
    private void unrecordedSet(char c, char r, PieceColor v) {
        _board[index(c, r)] = v;
    }

    /** Set square at linearized index SQ to V (not undoable). */
    private void unrecordedSet(int sq, PieceColor v) {
        _board[sq] = v;
    }

    /** Return true iff MOVE is legal on the current board. */
    boolean legalMove(Move move) {
        if (move == null) {
            return false;
        } else if (move.isPass()) {
            return !canMove(_whoseMove);
        }
        int startIndex = index(move.col0(), move.row0());
        int endIndex = index(move.col1(), move.row1());
        if (_board[startIndex] == _whoseMove) {
            if (_board[endIndex] == EMPTY) {
                if (move.col1() <= move.col0() + 2
                        && move.col1() >= move.col0() - 2) {
                    if (move.row1() <= move.row0() + 2
                            && move.row1() >= move.row0() - 2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Return true iff player WHO can move, ignoring whether it is
     *  that player's move and whether the game is over. */
    boolean canMove(PieceColor who) {
        for (int row = BOTTOM_ROW; row <= TOP_ROW; row += 1) {
            for (int col = LEFT_COL; col <= RIGHT_COL; col += 1) {
                if (get((char) col, (char) row) == who) {
                    if (pieceHasMove((char) col, (char) row)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Return true iff the piece at col C and row R has an available move. */
    private boolean pieceHasMove(char c, char r) {
        for (int x = c - 2; x <= c + 2; x += 1) {
            for (int y = r - 2; y <= r + 2; y += 1) {
                if (get((char) x, (char) y) == EMPTY) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Return the color of the player who has the next move.  The
     *  value is arbitrary if gameOver(). */
    PieceColor whoseMove() {
        return _whoseMove;
    }

    /** Return number of non-pass moves made in the current game since the
     *  last extend move added a piece to the board (or since the
     *  start of the game). Used to detect end-of-game. */
    int numJumps() {
        return _numJumps;
    }

    /** Perform the move C0R0-C1R1, or pass if C0 is '-'.  For moves
     *  other than pass, assumes that legalMove(C0, R0, C1, R1). */
    void makeMove(char c0, char r0, char c1, char r1) {
        if (c0 == '-') {
            makeMove(Move.pass());
        } else {
            makeMove(Move.move(c0, r0, c1, r1));
        }
    }

    /** Make the MOVE on this Board, assuming it is legal. */
    void makeMove(Move move) {
        _numJumpStack.push(_numJumps);
        if (!legalMove(move)) {
            throw new GameException("Illegal Move.");
        }
        if (move.isPass()) {
            pass();
            return;
        } else if (move.isExtend()) {
            incrPieces(_whoseMove, 1);
            _numJumps = 0;
            _board[move.toIndex()] = _whoseMove;
        } else if (move.isJump()) {
            _numJumps += 1;
            _board[move.fromIndex()] = EMPTY;
            _board[move.toIndex()] = _whoseMove;
        }
        PieceColor opponent = _whoseMove.opposite();
        flipPieces(move);
        _whoseMove = opponent;
        _undoStack.push(move.toString());
        setChanged();
        notifyObservers();
    }

    /** Change color of all adjacent opponent pieces
     *  after MOVE is made. */
    private void flipPieces(Move move) {
        PieceColor opponent = _whoseMove.opposite();
        ArrayList<Integer> flips = new ArrayList<>();
        for (int c = move.col1() - 1; c <= move.col1() + 1; c += 1) {
            for (int r = move.row1() - 1; r <= move.row1() + 1; r += 1) {
                if (get((char) c, (char) r) == opponent) {
                    flips.add(index((char) c, (char) r));
                    incrPieces(opponent, -1);
                    _board[index((char) c, (char) r)] = _whoseMove;
                    incrPieces(_whoseMove, 1);
                }
            }
        }
        _undoFlipStack.push(flips);
    }

    /** Update to indicate that the current player passes, assuming it
     *  is legal to do so.  The only effect is to change whoseMove(). */
    void pass() {
        if (canMove(_whoseMove)) {
            throw new GameException("Illegal pass.");
        }
        _whoseMove = _whoseMove.opposite();
        setChanged();
        notifyObservers();
    }

    /** Undo the last move. */
    void undo() {
        _numJumps = _numJumpStack.pop();
        _whoseMove = _whoseMove.opposite();
        String lastMove;
        ArrayList<Integer> flips;
        if (_undoStack != null) {
            lastMove = _undoStack.pop();
            flips = _undoFlipStack.pop();
            if (lastMove.length() == 2) {
                char col = lastMove.charAt(0);
                char row = lastMove.charAt(1);
                incrPieces(_board[index(col, row)], -1);
                _board[index(col, row)] = EMPTY;
            } else if (lastMove.length() == 5) {
                char newCol0 = lastMove.charAt(3);
                char newCol1 = lastMove.charAt(0);
                char newRow0 = lastMove.charAt(4);
                char newRow1 = lastMove.charAt(1);
                for (Integer x : flips) {
                    incrPieces(_board[x], -1);
                    incrPieces(_board[x].opposite(), 1);
                    _board[x] = _board[x].opposite();
                }
                incrPieces(_board[index(newCol0, newRow0)], -1);
                int newIndex = index(newCol1, newRow1);
                int oldIndex = index(newCol0, newRow0);
                _board[newIndex] = _board[oldIndex];
                _board[index(newCol0, newRow0)] = EMPTY;
            }
        }
        setChanged();
        notifyObservers();
    }

    /** Return true iff it is legal to place a block at C R. */
    boolean legalBlock(char c, char r) {
        return get(c, r) == EMPTY
                || get(c, r) == BLOCKED;
    }

    /** Return true iff it is legal to place a block at CR. */
    boolean legalBlock(String cr) {
        return legalBlock(cr.charAt(0), cr.charAt(1));
    }

    /** Set a block on the square C R and its reflections across the middle
     *  row and/or column, if that square is unoccupied and not
     *  in one of the corners. Has no effect if any of the squares is
     *  already occupied by a block.  It is an error to place a block on a
     *  piece. */
    void setBlock(char c, char r) {
        if (!legalBlock(c, r)) {
            throw error("illegal block placement");
        }
        _board[index(c, r)] = BLOCKED;
        if (!legalBlock(c, reflectRow(r))) {
            throw error("illegal block placement");
        }
        _board[index(c, reflectRow(r))] = BLOCKED;
        if (!legalBlock(reflectCol(c), r)) {
            throw error("illegal block placement");
        }
        _board[index(reflectCol(c), r)] = BLOCKED;
        if (!legalBlock(reflectCol(c), reflectRow(r))) {
            throw error("illegal block placement");
        }
        _board[index(reflectCol(c), reflectRow(r))] = BLOCKED;
        setChanged();
        notifyObservers();
    }

    /** Return reflection of R across center of board. */
    private char reflectRow(char r) {
        return (char) (ROW_REF - r);
    }

    /** Return reflection of C across center of board. */
    private char reflectCol(char c) {
        return (char) (COL_REF - c);
    }

    /** Place a block at CR. */
    void setBlock(String cr) {
        setBlock(cr.charAt(0), cr.charAt(1));
    }

    /** Return a list of all moves made since the last clear (or start of
     *  game). */
    List<Move> allMoves() {
        return _moves;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    /* .equals used only for testing purposes. */
    @Override
    public boolean equals(Object obj) {
        Board other = (Board) obj;
        return Arrays.equals(_board, other._board);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(_board);
    }

    /** Return a text depiction of the board (not a dump).  If LEGEND,
     *  supply row and column numbers around the edges. */
    String toString(boolean legend) {
        Formatter out = new Formatter();
        out.format("===");
        out.format("%n");
        out.format(" ");
        for (int i = ROW_START; i < ROW_END && i > BOTTOM_INDEX; i += 1) {
            if (_board[i] == BLOCKED) {
                out.format(" X");
            } else if (_board[i] == RED) {
                out.format(" r");
            } else if (_board[i] == BLUE) {
                out.format(" b");
            } else {
                out.format(" -");
            }
            if ((i + 3) % EXTENDED_SIDE == 0) {
                i -= ROW_SHIFT;
                out.format("%n");
                if (i >= BOTTOM_INDEX) {
                    out.format(" ");
                }
            }
        }
        out.format("===");
        return out.toString();
    }

    /** Return true iff the position at linearized index X is in the border
     *  of the game board. */
    private boolean isBorder(int x) {
        return (x >= TOP_INDEX || x <= BOTTOM_INDEX
                || x % EXTENDED_SIDE == 0
                || (x - 1) % EXTENDED_SIDE == 0
                || (x + 1) % EXTENDED_SIDE == 0
                || (x + 2) % EXTENDED_SIDE == 0);
    }

    /** Return stack of moves to undo. */
    Stack<String> undoStack() {
        return _undoStack;
    }

    /** Return stack containing Lists of pieces flipped
     *  after each move made so far.
     */
    Stack<ArrayList<Integer>> undoFlipStack() {
        return _undoFlipStack;
    }

     /** Return stack containing the number of consecutive Jumps
      *  before each move made so far.
      */
    Stack<Integer> numJumpStack() {
        return _numJumpStack;
    }

    /** For reasons of efficiency in copying the board,
     *  we use a 1D array to represent it, using the usual access
     *  algorithm: row r, column c => index(r, c).
     *
     *  Next, instead of using a 7x7 board, we use an 11x11 board in
     *  which the outer two rows and columns are blocks, and
     *  row 2, column 2 actually represents row 0, column 0
     *  of the real board.  As a result of this trick, there is no
     *  need to special-case being near the edge: we don't move
     *  off the edge because it looks blocked.
     *
     *  Using characters as indices, it follows that if 'a' <= c <= 'g'
     *  and '1' <= r <= '7', then row c, column r of the board corresponds
     *  to board[(c -'a' + 2) + 11 (r - '1' + 2) ], or by a little
     *  re-grouping of terms, board[c + 11 * r + SQUARE_CORRECTION]. */
    private final PieceColor[] _board;

    /** Player that is on move. */
    private PieceColor _whoseMove;

    /** Number of BLOCKS on board. */
    private int _numBlocks;

    /** Number of RED pieces on board. */
    private int _numRed;

    /** Number of BLUE pieces on board. */
    private int _numBlue;

    /** Number of Jumps made. */
    private int _numJumps;

    /** Stack of Moves made. */
    private List<Move> _moves;

    /** Stack of operations to undo. */
    private Stack<String> _undoStack;

    /** Stack of Arrays containing pieces flipped. */
    private Stack<ArrayList<Integer>> _undoFlipStack;

    /** Stack of NumJump updates. */
    private Stack<Integer> _numJumpStack;
}
