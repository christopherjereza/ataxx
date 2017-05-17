package ataxx;

import static ataxx.PieceColor.*;

/** A Player that receives its moves from its Game's getMoveCmnd method.
 *  @author Chris Jereza
 */
class Manual extends Player {

    /** A Player that will play MYCOLOR on GAME, taking its moves from
     *  GAME. */
    Manual(Game game, PieceColor myColor) {
        super(game, myColor);
    }

    @Override
    Move myMove() {
        Command moveCmnd = game().getMoveCmnd(""
                + super.game().board().whoseMove() + ": ");
        if (moveCmnd != null) {
            return Move.move(moveCmnd.operands()[0].charAt(0),
                    moveCmnd.operands()[1].charAt(0),
                    moveCmnd.operands()[2].charAt(0),
                    moveCmnd.operands()[3].charAt(0));
        } else {
            return null;
        }
    }
}

