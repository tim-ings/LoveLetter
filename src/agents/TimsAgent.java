package agents;

import loveletter.*;

import java.util.Random;

/**
 * An interface for representing an agent in the game Love Letter All agent's
 * must have a 0 parameter constructor
 */
public class TimsAgent implements Agent {

	private final float BARRON_MIN_CHANCE = 0.0f;
	private final float GUARD_MIN_CHANCE = 0.0f;
	private final float KING_MIN_CHANCE = 0.0f;

	private final Card[] CARD_VALUES = Card.values();
	private final int UNIQUE_CARD_COUNT = 8;
	private PlayerState[] playerStates;
	private int[] cardCounts;
	private Random rand;
	private State current;
	private int myIndex;

	private class WeightedAction {
		public Action action;
		public float weight;

		public WeightedAction(float w, Action a) {
			action = a;
			weight = w;
		}
	}

	private class PlayerState {
		private int[] potentialCardCount;
		public final int playerIndex;
		// this increases as this player targets us, used as a tie breaker
		public int threat = 0;
		// priest, etc
		public Card knownCard = null;

		/**
		 * Constructs a new player state object
		 * 
		 * @param playerIndex The index of the player
		 */
		public PlayerState(int playerIndex) {
			this.playerIndex = playerIndex;
			potentialCardCount = new int[CARD_VALUES.length];
			for (int i = 0; i < CARD_VALUES.length; i++) {
				potentialCardCount[i] = CARD_VALUES[i].count();
			}
		}

		/**
		 * Updates the players state based on action taken by a player
		 * 
		 * @param act The action taken
		 */
		public void update(Action act) {
			if (act.target() == myIndex) {
				threat++;
			}
			potentialCardCount[act.card().ordinal()]--;
		}

		/**
		 * Used to retrieve the probability that the given player has a given card in
		 * their hand
		 * 
		 * @param c The card to check
		 * @return The probability Card c is in the given players hand
		 */
		public float getProb(Card c) {
			return potentialCardCount[c.ordinal()] / CARD_VALUES.length;
		}

		/**
		 * Returns the most likely card the player has
		 * 
		 * @return
		 */
		public Card getMostLikely() {
			if (knownCard != null) {
				return knownCard;
			}
			float maxProb = -Float.MAX_VALUE;
			int maxProbIndex = 0;
			for (int i = 0; i < potentialCardCount.length; i++) {
				float prob = potentialCardCount[i] / CARD_VALUES[i].count();
				// >= means the higher value card is always chosen
				if (prob >= maxProb) {
					maxProb = prob;
					maxProbIndex = i;
				}
			}
			return CARD_VALUES[maxProbIndex];
		}

		/**
		 * Returns the chance of the most likely card the player has
		 * 
		 * @return
		 */
		public float getMostLikelyChance() {
			if (knownCard != null) {
				return 1;
			}
			float maxProb = -Float.MAX_VALUE;
			for (int i = 0; i < potentialCardCount.length; i++) {
				float prob = potentialCardCount[i] / CARD_VALUES[i].count();
				// >= means the higher value card is always chosen
				if (prob >= maxProb) {
					maxProb = prob;
				}
			}
			return maxProb;
		}
	}

	// 0 place default constructor
	public TimsAgent() {
		rand = new Random();
	}

	/**
	 * Reports the agents name
	 */
	public String toString() {
		return "Tim";
	}

	/**
	 * Method called at the start of a round
	 * 
	 * @param start the starting state of the round
	 **/
	public void newRound(State start) {
		current = start;
		myIndex = current.getPlayerIndex();
		// init the card counts array with the number of cards per type, 5 guards, 2
		// priests etc.
		cardCounts = new int[UNIQUE_CARD_COUNT];
		for (int i = 0; i < CARD_VALUES.length; i++) {
			cardCounts[i] = CARD_VALUES[i].count();
		}
		playerStates = new PlayerState[start.numPlayers()];
		for (int i = 0; i < current.numPlayers(); i++) {
			if (i != myIndex) {
				playerStates[i] = new PlayerState(i);
			}
		}
	}

	/**
	 * Updates the state of every player
	 * 
	 * @param act The action taken
	 */
	private void updatePlayerStates(Action act) {
		for (int i = 0; i < current.numPlayers(); i++) {
			if (i != myIndex) {
				playerStates[i].update(act);
			}
		}
	}

	/**
	 * Method called when any agent performs an action.
	 * 
	 * @param act     the action an agent performs
	 * @param results the state of play the agent is able to observe.
	 **/
	public void see(Action act, State results) {
		current = results;
		cardCounts[act.card().ordinal()]--;
		updatePlayerStates(act);
		// when we play the priest
		if (act.card() == Card.PRIEST && act.player() == myIndex) {
			Card oppCard = results.getCard(act.target());
			playerStates[act.target()].knownCard = oppCard;
		}
		// when a player with a knownCard plays that card we need to set their knownCard
		// to null
		for (int i = 0; i < current.numPlayers(); i++) {
			if (i != myIndex && playerStates[i].knownCard != null && act.player() == i
					&& act.card() == playerStates[i].knownCard) {
				playerStates[i].knownCard = null;
			}
		}
	}

	private boolean hasCard(Card c, Card c1, Card c2) {
		return c1 == c || c2 == c;
	}

	private int maxValue(Card c1, Card c2) {
		return Math.max(c1.value(), c1.value());
	}

	private float avgDeckValue() {
		float sum = 0;
		int count = 0;
		for (int i = 0; i < cardCounts.length; i++) {
			if (cardCounts[i] > 0) {
				Card c = CARD_VALUES[i];
				sum += c.value();
				count += cardCounts[i];
			}
		}
		return sum / count;
	}

	/**
	 * Picks the best card to play against a player with a guard. There is more than
	 * 1 guard card
	 * 
	 * @param target The targeted players index
	 * @param hand   The card in our hand
	 * @param dealt  The card we were dealt
	 * @return The action to be taken against the player
	 * @throws IllegalActionException
	 */
	private Action decideGuard(int target, Card hand, Card dealt, float prob) throws IllegalActionException {
		// @formatter:off
		// Outcomes:
		// GUARD IGNORED we cannot guess guard
		// PRIEST IGNORED must look at broader game to find best target
		// BARON POSSIBLE we should only attempt this if we are confident enough we have
		// a higher value card
		// HANDMAID IGNORED self targeted only
		// PRINCE IGNORED guard is a low value card, TODO: but it is powerful maybe
		// worth discarding it here
		// KING IGNORED this is suicide
		// COUNTESS IGNORED we should avoid playing the countess
		// PRINCESS IGNORED this is suicide
		// NULL IGNORED
		// @formatter:on

		// use our baron
		if (prob > BARRON_MIN_CHANCE && hasCard(Card.BARON, hand, dealt)
				&& maxValue(hand, dealt) >= Card.GUARD.value()) {
			return Action.playBaron(myIndex, target);
		}

		return null;
	}

	/**
	 * Picks the best card to play against a player with a priest. There is more
	 * than 1 priest card
	 * 
	 * @param target The targeted players index
	 * @param hand   The card in our hand
	 * @param dealt  The card we were dealt
	 * @return The action to be taken against the player
	 * @throws IllegalActionException
	 */
	private Action decidePriest(int target, Card hand, Card dealt, float prob) throws IllegalActionException {
		// @formatter:off
		// Outcomes:
		// GUARD POSSIBLE we should guess priest
		// PRIEST IGNORED must look at broader game to find best target
		// BARON POSSIBLE we should only attempt this if we are confident enough we have
		// a higher value card
		// HANDMAID IGNORED self targeted only
		// PRINCE POSSIBLE we should make them discard if they are likely to lower their
		// value
		// KING IGNORED this is suicide
		// COUNTESS IGNORED we should avoid playing the countess
		// PRINCESS IGNORED this is suicide
		// NULL IGNORED
		// @formatter:on

		// use the low value guard to discard their baron if we are sure enough
		if (prob > GUARD_MIN_CHANCE && hasCard(Card.GUARD, hand, dealt)) {
			return Action.playGuard(myIndex, target, Card.PRIEST);
		}
		// use our baron
		if (prob > BARRON_MIN_CHANCE && hasCard(Card.BARON, hand, dealt)
				&& maxValue(hand, dealt) >= Card.PRIEST.value()) {
			return Action.playBaron(myIndex, target);
		}
		// use our prince
		if (hasCard(Card.PRINCE, hand, dealt) && avgDeckValue() <= Card.PRIEST.value()) {
			return Action.playPrince(myIndex, target);
		}

		return null;
	}

	/**
	 * Picks the best card to play against a player with a baron There is more than
	 * 1 baron card
	 * 
	 * @param target The targeted players index
	 * @param hand   The card in our hand
	 * @param dealt  The card we were dealt
	 * @return The action to be taken against the player
	 * @throws IllegalActionException
	 */
	private Action decideBaron(int target, Card hand, Card dealt, float prob) throws IllegalActionException {
		// @formatter:off
		// Outcomes:
		// GUARD POSSIBLE we should guess baron
		// PRIEST IGNORED must look at broader game to find best target
		// BARON IGNROED this is a bad idea, best we can do is draw
		// HANDMAID IGNORED self targeted only
		// PRINCE POSSIBLE we should make them discard if they are likely to lower their
		// value
		// KING IGNORED this is suicide
		// COUNTESS IGNORED we should avoid playing the countess
		// PRINCESS IGNORED this is suicide
		// NULL IGNORED
		// @formatter:on

		// use the low value guard to discard their baron if we are sure enough
		if (prob > GUARD_MIN_CHANCE && hasCard(Card.GUARD, hand, dealt)) {
			return Action.playGuard(myIndex, target, Card.BARON);
		}
		// use our prince
		if (hasCard(Card.PRINCE, hand, dealt) && avgDeckValue() <= Card.BARON.value()) {
			return Action.playPrince(myIndex, target);
		}

		return null;
	}

	/**
	 * Picks the best card to play against a player with a handmaiden There is more
	 * than 1 handmaiden card
	 * 
	 * @param target The targeted players index
	 * @param hand   The card in our hand
	 * @param dealt  The card we were dealt
	 * @return The action to be taken against the player
	 * @throws IllegalActionException
	 */
	private Action decideHandmaiden(int target, Card hand, Card dealt, float prob) throws IllegalActionException {
		// @formatter:off
		// Outcomes:
		// GUARD POSSIBLE we should guess handmaiden
		// PRIEST IGNORED must look at broader game to find best target
		// BARON POSSIBLE we should only attempt this if we are confident enough we have
		// a higher value card
		// HANDMAID IGNORED self targeted only
		// PRINCE POSSIBLE we should make them discard if they are likely to lower their
		// value
		// KING POSSIBLE we should swap for the handmaiden
		// COUNTESS IGNORED we should avoid playing the countess
		// PRINCESS IGNORED this is suicide
		// NULL IGNORED
		// @formatter:on

		// try and get the handmaiden for ourselves
		if (prob > KING_MIN_CHANCE && hasCard(Card.KING, hand, dealt)) {
			return Action.playKing(myIndex, target);
		}
		// use the low value guard to discard their handmaiden if we are sure enough
		if (prob > GUARD_MIN_CHANCE && hasCard(Card.GUARD, hand, dealt)) {
			return Action.playGuard(myIndex, target, Card.HANDMAID);
		}
		// use our baron
		if (prob > BARRON_MIN_CHANCE && hasCard(Card.BARON, hand, dealt)
				&& maxValue(hand, dealt) >= Card.HANDMAID.value()) {
			return Action.playBaron(myIndex, target);
		}
		// use our prince
		if (hasCard(Card.PRINCE, hand, dealt) && avgDeckValue() <= Card.KING.value()) {
			return Action.playPrince(myIndex, target);
		}

		return null;
	}

	/**
	 * Picks the best card to play against a player with a prince There is more than
	 * 1 prince card
	 * 
	 * @param target The targeted players index
	 * @param hand   The card in our hand
	 * @param dealt  The card we were dealt
	 * @return The action to be taken against the player
	 * @throws IllegalActionException
	 */
	private Action decidePrince(int target, Card hand, Card dealt, float prob) throws IllegalActionException {
		// @formatter:off
		// Outcomes:
		// GUARD POSSIBLE we should guess prince
		// PRIEST IGNORED must look at broader game to find best target
		// BARON POSSIBLE we should only attempt this if we are confident enough we have
		// a higher value card
		// HANDMAID IGNORED self targeted only
		// PRINCE POSSIBLE we should make them discard if they are likely to lower their
		// value
		// KING IGNORED this is suicide
		// COUNTESS IGNORED we should avoid playing the countess
		// PRINCESS IGNORED this is suicide
		// NULL IGNORED
		// @formatter:on

		// use the low value guard to discard their prince if we are sure enough
		if (prob > GUARD_MIN_CHANCE && hasCard(Card.GUARD, hand, dealt)) {
			return Action.playGuard(myIndex, target, Card.PRINCE);
		}
		// use our baron
		if (prob > BARRON_MIN_CHANCE && hasCard(Card.BARON, hand, dealt)
				&& maxValue(hand, dealt) >= Card.PRINCE.value()) {
			return Action.playBaron(myIndex, target);
		}
		// use our prince
		if (hasCard(Card.PRINCE, hand, dealt) && avgDeckValue() <= Card.KING.value()) {
			return Action.playPrince(myIndex, target);
		}

		return null;
	}

	/**
	 * Picks the best card to play against a player with a king
	 * 
	 * @param target The targeted players index
	 * @param hand   The card in our hand
	 * @param dealt  The card we were dealt
	 * @return The action to be taken against the player
	 * @throws IllegalActionException
	 */
	private Action decideKing(int target, Card hand, Card dealt, float prob) throws IllegalActionException {
		// @formatter:off
		// Outcomes:
		// GUARD POSSIBLE we should guess king
		// PRIEST IGNORED must look at broader game to find best target
		// BARON POSSIBLE we should only attempt this if we are confident enough we have
		// a higher value card
		// HANDMAID IGNORED self targeted only
		// PRINCE POSSIBLE we should make them discard if they are likely to lower their
		// value
		// KING IMPOSSIBLE there is only 1 king
		// COUNTESS IGNORED we should avoid playing the countess
		// PRINCESS IGNORED this is suicide
		// NULL IGNORED
		// @formatter:on

		// use the low value guard to discard their king if we are sure enough
		if (prob > GUARD_MIN_CHANCE && hasCard(Card.GUARD, hand, dealt)) {
			return Action.playGuard(myIndex, target, Card.KING);
		}
		// use our baron
		if (prob > BARRON_MIN_CHANCE && hasCard(Card.BARON, hand, dealt)
				&& maxValue(hand, dealt) >= Card.KING.value()) {
			return Action.playBaron(myIndex, target);
		}
		// use our prince
		if (hasCard(Card.PRINCE, hand, dealt) && avgDeckValue() <= Card.KING.value()) {
			return Action.playPrince(myIndex, target);
		}

		return null;
	}

	/**
	 * Picks the best card to play against a player with a countess
	 * 
	 * @param target The targeted players index
	 * @param hand   The card in our hand
	 * @param dealt  The card we were dealt
	 * @return The action to be taken against the player
	 * @throws IllegalActionException
	 */
	private Action decideCountess(int target, Card hand, Card dealt, float prob) throws IllegalActionException {
		// @formatter:off
		// Outcomes:
		// GUARD POSSIBLE we should guess countess
		// PRIEST IGNORED must look at broader game to find best target
		// BARON POSSIBLE we should only play baron if we have a higher value card
		// HANDMAID IGNORED self targeted only
		// PRINCE POSSIBLE we should make them discard if they are likely to lower their
		// value
		// KING POSSIBLE we should swap if our card is lower value than theirs
		// COUNTESS IMPOSSIBLE there is only 1 countess
		// PRINCESS IGNORED this is suicide
		// NULL POSSIBLE no point attacking a player who is forced to play a high value
		// card
		// @formatter:on

		// how many cards can still be dealt with a value greater than 4?
		int lowCount = 0;
		for (int i = 0; i < 4; i++) { // ending point of 4 is the index of prince (NOT VALUE), the first card to have
										// value over 4
			lowCount += cardCounts[i];
		}
		// how many cards can still be dealt with a value less than or equal to 4?
		int highCount = 0;
		for (int i = 4; i < cardCounts.length; i++) { // starting point of 4 is the index of prince (NOT VALUE), the
														// first card to
														// have value over 4
			highCount += cardCounts[i];
		}
		float forcedChance = highCount / (highCount + lowCount);
		if (forcedChance > 0.5) {
			// they will probably play the countess next turn, leave them alone
			// players only ever have 1 card in their hand, so we cannot attempt to attack
			// the high value card they are likely to be dealt yet
			return null;
		}
		// they have the countess and probably wont play it
		// countess is very high value so we should try take it given we dont have a
		// higher value card
		// this is a gamble, but we are at an advantage over the opponent that currently
		// has the countess as we know we have a king already
		if (hasCard(Card.KING, hand, dealt) && maxValue(hand, dealt) < Card.COUNTESS.value()) {
			return Action.playKing(myIndex, target);
		}
		// we want to try keep our high value prince if we can
		if (hasCard(Card.GUARD, hand, dealt)) {
			return Action.playGuard(myIndex, target, Card.COUNTESS);
		}
		// use our baron
		if (hasCard(Card.BARON, hand, dealt) && maxValue(hand, dealt) >= Card.COUNTESS.value()) {
			return Action.playBaron(myIndex, target);
		}
		// use our prince
		if (hasCard(Card.PRINCE, hand, dealt) && avgDeckValue() <= Card.COUNTESS.value()) {
			return Action.playPrince(myIndex, target);
		}

		return null;
	}

	/**
	 * Picks the best card to play against a player with a princess
	 * 
	 * @param target The targeted players index
	 * @param hand   The card in our hand
	 * @param dealt  The card we were dealt
	 * @return The action to be taken against the player
	 * @throws IllegalActionException
	 */
	private Action decidePrincess(int target, Card hand, Card dealt, float prob) throws IllegalActionException {
		// @formatter:off
		// Outcomes:
		// GUARD POSSIBLE we should guess princess
		// PRIEST IGNORED must look at broader game to find best target
		// BARON IGNORED this is suicide, only 1 princess in the game
		// HANDMAID IGNORED self targeted only
		// PRINCE POSSIBLE we should make them discard
		// KING POSSIBLE we should swap
		// COUNTESS IGNORED we should avoid playing the countess
		// PRINCESS IMPOSSIBLE there is only 1 princess
		// NULL IGNORED we need to do something, this player will win if left alone
		// @formatter:on

		// take the princess card if we can
		if (hasCard(Card.KING, hand, dealt)) {
			return Action.playKing(myIndex, target);
		}

		// use the low value guard to discard their princess if we are sure enough
		if (prob > GUARD_MIN_CHANCE && hasCard(Card.GUARD, hand, dealt)) {
			return Action.playGuard(myIndex, target, Card.PRINCESS);
		}

		// use the prince to discard their princess
		if (hasCard(Card.PRINCE, hand, dealt)) {
			return Action.playPrince(myIndex, target);
		}

		return null; // TODO: should never return null, maybe we could handle this elsewhere?
	}

	private WeightedAction decideActionVsPlayer(PlayerState opp, Card hand, Card dealt) {
		if (current.eliminated(opp.playerIndex)) {
			// eliminated players have minimum weight
			return new WeightedAction(-Float.MAX_VALUE, null);
		}
		try {
			Card mostLikely = opp.getMostLikely();
			int weight = mostLikely.value();
			switch (mostLikely) {
			case GUARD:
				return new WeightedAction(weight, decideGuard(opp.playerIndex, hand, dealt, opp.getProb(mostLikely)));
			case PRIEST:
				return new WeightedAction(weight, decidePriest(opp.playerIndex, hand, dealt, opp.getProb(mostLikely)));
			case BARON:
				return new WeightedAction(weight, decideBaron(opp.playerIndex, hand, dealt, opp.getProb(mostLikely)));
			case HANDMAID:
				return new WeightedAction(weight,
						decideHandmaiden(opp.playerIndex, hand, dealt, opp.getProb(mostLikely)));
			case PRINCE:
				return new WeightedAction(weight, decidePrince(opp.playerIndex, hand, dealt, opp.getProb(mostLikely)));
			case KING:
				return new WeightedAction(weight, decideKing(opp.playerIndex, hand, dealt, opp.getProb(mostLikely)));
			case COUNTESS:
				return new WeightedAction(weight,
						decideCountess(opp.playerIndex, hand, dealt, opp.getProb(mostLikely)));
			case PRINCESS:
				return new WeightedAction(weight,
						decidePrincess(opp.playerIndex, hand, dealt, opp.getProb(mostLikely)));
			}
		} catch (IllegalActionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private int getPirestTarget() {
		float maxChance = -Float.MAX_VALUE;
		int maxChanceIndex = 0;
		for (int i = 0; i < current.numPlayers(); i++) {
			if (i != myIndex) {
				PlayerState ps = playerStates[i];
				float c = ps.getMostLikelyChance();
				if (c > maxChance) {
					maxChance = c;
					maxChanceIndex = i;
				}
			}
		}
		return maxChanceIndex;
	}

	private Card guardGuessFromRemaining() {
		Card guess = Card.GUARD;
		while (guess == Card.GUARD) {
			int ccIndex = rand.nextInt(cardCounts.length);
			if (cardCounts[ccIndex] > 0) {
				guess = CARD_VALUES[ccIndex];
			}
		}
		return guess;
	}

	private Action decideActionSelf(Card hand, Card dealt) throws IllegalActionException {
		if (hasCard(Card.HANDMAID, hand, dealt)) {
			return Action.playHandmaid(myIndex);
		}
		if (hasCard(Card.PRIEST, hand, dealt)) {
			int target = getPirestTarget();
			while (target == myIndex) { // just pick a random player if we somehow pick ourselves
				target = rand.nextInt(current.numPlayers());
			}
			return Action.playPriest(myIndex, target); // get the player we know the least about. Should this be highest
														// threat
														// instead?
		}

		Action act = null;
		Card play = null;
		while (!current.legalAction(act, play) || act == null) {
			// default to a random action if we have to
			play = rand.nextDouble() < 0.5 ? hand : dealt;
			// dont target ourselves
			int target = rand.nextInt(current.numPlayers());
			while (target == myIndex) {
				target = rand.nextInt(current.numPlayers());
			}
			switch (play) {
			case GUARD:
				Card guess = playerStates[target].getMostLikely();
				// we cannot guess guard so pick a random guess
				while (guess == Card.GUARD) {
					guess = guardGuessFromRemaining();
				}
				act = Action.playGuard(myIndex, target, guess);
				break;
			case PRIEST:
				act = Action.playPriest(myIndex, target); // shouldnt get here
				break;
			case BARON:
				act = Action.playBaron(myIndex, target);
				break;
			case HANDMAID:
				act = Action.playHandmaid(myIndex); // shouldnt get here
				break;
			case PRINCE:
				act = Action.playPrince(myIndex, target);
				break;
			case KING:
				act = Action.playKing(myIndex, target);
				break;
			case COUNTESS:
				act = Action.playCountess(myIndex);
				break;
			default:
				act = null;// never play princess
			}
		}
		return act;
	}

	/**
	 * Perform an action after drawing a card from the deck
	 * 
	 * @param c the card drawn from the deck
	 * @return the action the agent chooses to perform
	 * @throws IllegalActionException when the Action produced is not legal.
	 */
	public Action playCard(Card dealt) {
		Card hand = current.getCard(myIndex);
		Action bestAction = null;
		float bestWeight = -Float.MAX_VALUE;
		for (int i = 0; i < current.numPlayers(); i++) {
			if (i != myIndex) {
				WeightedAction newAction = decideActionVsPlayer(playerStates[i], hand, dealt);
				if (newAction != null && newAction.action != null && bestAction == null) {
					bestAction = newAction.action;
				} else if (newAction != null && newAction.action != null && bestWeight == newAction.weight) {
					int bestActionThreat = playerStates[bestAction.target()].threat;
					int newActionThreat = playerStates[newAction.action.target()].threat;
					if (newActionThreat > bestActionThreat) {
						bestAction = newAction.action;
					}
				} else if (newAction != null && newAction.action != null && newAction.weight > bestWeight) {
					bestAction = newAction.action;
					bestWeight = newAction.weight;
				}
			}
		}

		// only attempting self actions when we fail to find an attack opportunity is
		// aggressive
		// TODO: we could consider self actions too
		if (bestAction == null) {
			try {
				bestAction = decideActionSelf(hand, dealt);
			} catch (IllegalActionException e) {
				System.out.println("We shouldnt be here");
			}
		}

		if (bestAction == null) {
			System.out.println("We shouldnt be here");
		}

		return bestAction;
	}
}
