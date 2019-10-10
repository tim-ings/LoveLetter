package agents;

import loveletter.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * An interface for representing an agent in the game Love Letter All agent's
 * must have a 0 parameter constructor
 */
public class TimsAgent implements Agent {

	private final Card[] CARD_VALUES = Card.values();
	private final int COUNTESS_FORCE_VAL = 4;
	private final int UNIQUE_CARD_COUNT = 8;
	private final int[] totalCardCounts = { 5, 2, 2, 2, 2, 1, 1, 1 };
	private PlayerState[] playerStates;
	private int[] cardCounts;
	private Random rand;
	private State current;
	private int myIndex;

	private class CardState {
		public Card card;
		public float chance;
	}

	private class PlayerState {
		private int[] potentialCardCount;
		public final int playerIndex;

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
		// init the card counts array with the number of cards per type, 5 guards, 2 priests etc.
		cardCounts = new int[UNIQUE_CARD_COUNT];
		for (int i = 0; i < CARD_VALUES.length; i++) {
			cardCounts[i] = CARD_VALUES[i].count();
		}
		playerStates = new PlayerState[start.numPlayers()];
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
	}

	private boolean hasCard(Card c, Card c1, Card c2) {
		return c1 == c || c2 == c;
	}

	private Action decideAction(PlayerState opp, Card hand, Card dealt) {
		return decideAction(opp.getMostLikely(), opp.playerIndex, hand, dealt);
	}

	private Action decideAction(Card opp, int target, Card hand, Card dealt) {
		try {
			switch (opp) {
			case GUARD:
				break;
			case PRIEST:
				break;
			case BARON:
				break;
			case HANDMAID: // we should take this card if we can or prevent them from using it
				break;
			case PRINCE:
				break;
			case KING:
				break;
			case COUNTESS: // what are the chances this player has to play this card?
				// how many cards can still be dealt with a value greater than 4?
				int lowCount = 0;
				for (int i = 0; i < 4; i++) { // ending point of 4 is the index of prince (NOT VALUE), the first card to have value over 4
					lowCount += cardCounts[i];
				}
				// how many cards can still be dealt with a value less than or equal to 4?
				int highCount = 0;
				for (int i = 4; i < cardCounts.length; i++) { // starting point of 4 is the index of prince (NOT VALUE), the first card to have value over 4
					highCount += cardCounts[i];
				}
				float forcedChance = highCount / (highCount + lowCount);
				break;
			case PRINCESS: // we should try and make them play this card
				// if we have a king we could take the princess card
				if (hasCard(Card.KING, hand, dealt)) {
					return Action.playKing(myIndex, target);
				}
				// if we have the prince, we could make them discard it
				if (hasCard(Card.PRINCE, hand, dealt)) {
					return Action.playPrince(myIndex, target);
				}
				break;
			}
		} catch (IllegalActionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Perform an action after drawing a card from the deck
	 * 
	 * @param c the card drawn from the deck
	 * @return the action the agent chooses to perform
	 * @throws IllegalActionException when the Action produced is not legal.
	 */
	public Action playCard(Card dealt) {
		// ai priority:
		// 1. Play the countess if we have to
		// 2. Play the handmaiden
		// 3. Make a player play the princess
		// 4. Swap cards with a higher value player using the king
		// 5. Use the prince to remove high value cards from players
		// 6. Play the baron to eliminate players with low value cards
		// 7. Play the priest to gather info. Bias this in the early game. Maybe we
		// should play the priest ASAP if we are not under threat?
		// 8. Play the guard and chose the most likely outcome. This also lets us reduce
		// a players potential claim space

		try {
			Card hand = current.getCard(myIndex);

			// 1
			if (hasCard(Card.COUNTESS, dealt, hand)
					&& (dealt.value() > COUNTESS_FORCE_VAL || hand.value() > COUNTESS_FORCE_VAL)) {
				return Action.playCountess(myIndex);
			}

			// 2
			if (hasCard(Card.HANDMAID, dealt, hand)) {
				return Action.playHandmaid(myIndex);
			}

			// 3
			for (int targetIndex = 0; targetIndex < current.numPlayers(); targetIndex++) {
				// this will return null if we do not know the other player card
				Card opp = current.getCard(targetIndex);
				if (opp != null) {
					decideAction(opp, hand, dealt);
				}
			}
		} catch (IllegalActionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}
}
