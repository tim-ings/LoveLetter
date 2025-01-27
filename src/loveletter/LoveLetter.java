package loveletter;

import java.util.Random;
import java.io.PrintStream;
import agents.RandomAgent;

/**
 * A class for running a single game of LoveLetter. An array of 4 agents is
 * provided, a deal is initialised and players takes turns until the game ends
 * and the score is reported.
 * 
 * @author Tim French
 */
public class LoveLetter {

	private Agent rando;
	private Random random;
	private PrintStream ps;

	/**
	 * Constructs a LoveLetter game.
	 * 
	 * @param seed a seed for the random number generator.
	 * @param ps   a PrintStream object to record the events of the game
	 **/
	public LoveLetter(long seed, PrintStream ps) {
		this.random = new Random(seed);
		this.ps = ps;
		rando = new RandomAgent();
	}

	/**
	 * Constructs a LoveLetter game. Defauklt construct with system random seed, and
	 * System.out as the PrintStream
	 **/
	public LoveLetter() {
		this(0, System.out);
		this.ps = System.out;
	}

	/**
	 * Plays a game of LoveLetter
	 * 
	 * @param agents the players in the game
	 * @return scores of each agent as an array of integers
	 **/
	public int[] playGame(Agent[] agents) {
		boolean gameOver = false;
		int winner = 0;
		int numPlayers = agents.length;
		State gameState = new State(random, agents);// the game state
		State[] playerStates = new State[numPlayers];
		try {
			while (!gameState.gameOver()) {
				for (int i = 0; i < numPlayers; i++) {
					playerStates[i] = gameState.playerState(i);
					agents[i].newRound(playerStates[i]);
				}
				while (!gameState.roundOver()) {
					Card topCard = gameState.drawCard();
					Action act = agents[gameState.nextPlayer()].playCard(topCard);
					try {
						gameState.update(act, topCard);
					} catch (IllegalActionException e) {
						rando.newRound(gameState.playerState(gameState.nextPlayer()));
						act = rando.playCard(topCard);
						gameState.update(act, topCard);
					}
					for (int p = 0; p < numPlayers; p++)
						agents[p].see(act, playerStates[p]);
				}
				gameState.newRound();
			}
			// ps.println("Player " + gameState.gameWinner() + " wins the Princess's
			// heart!");
			int[] scoreboard = new int[numPlayers];
			for (int p = 0; p < numPlayers; p++)
				scoreboard[p] = gameState.score(p);
			return scoreboard;
		} catch (IllegalActionException e) {
			// ps.println("Something has gone wrong.");
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * This main method is provided to run a simple test game with provided agents.
	 * The agent implementations should be in the default package.
	 */
	public static void main(String[] args) {
		Random rand = new Random();
		// get the number of tests to run from the args, deafult to 1
		int testCount = 1;
		if (args.length > 0) {
			testCount = Integer.parseInt(args[0]);
		}
		int timWins = 0; // keep track of how many times my AI wins
		int[] winCounts = new int[4];
		Agent[] agents = { new agents.RandomAgent(),new agents.RandomAgent(),new agents.TimsAgent(),new agents.RandomAgent() };
		int timIndex = 2;
		for (int j = 0; j < testCount; j++) {
			LoveLetter env = new LoveLetter();
			int[] results = env.playGame(agents);
			int maxScoreIndex = 0;
			int maxScore = 0;
			for (int i = 0; i < agents.length; i++) {
				if (results[i] > maxScore) {
					maxScoreIndex = i;
					maxScore = results[i];
				}
			}
			if (maxScoreIndex == timIndex) {
				timWins++;
			}
			winCounts[maxScoreIndex]++;
		}
		float winRate = (float) timWins / (float) testCount * 100;
		System.out.printf("Tim won %.1f%% of the time. Random chance is 25.0%%\n", winRate);
		
		for (int i = 0; i < 4; i++)
			System.out.printf("\t Agent " + i + ", \"" + agents[i].toString() + "\":\t " + winCounts[i] + "\n");
	}
}










