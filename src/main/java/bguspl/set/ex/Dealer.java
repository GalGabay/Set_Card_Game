package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    public Object keyDealer;
    public BlockingQueue<Player> playerSetQueue;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        keyDealer = new Object();
        playerSetQueue = new LinkedBlockingQueue<Player>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        String name;
        for(int i=0; i<players.length; i++) {
            name = "Player " + i;
            new ThreadLogger(players[i], name, env.logger).startWithLog();
        }
        // what about "join" to the threads of the players?

        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true); // probably true
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        Player playerClaimsSet = playerSetQueue.poll();
        if(playerClaimsSet == null) {
            if(reshuffleTime - System.currentTimeMillis() > 0) {

            }
        } else {
            // player claims set and check if the set is valid and remove the 3 cards
            int[] cards = new int[3];
            for(int i=0; i<playerClaimsSet.tokens.size(); i++) {
                int slot = playerClaimsSet.tokens.poll();
                cards[i] = table.slotToCard[slot];
            }
            if(env.util.testSet(cards)) {
                // this is a valid set
                playerClaimsSet.point();
                for(int i=0; i<3; i++) {
                    table.removeCard(table.cardToSlot[cards[i]]);
                }
            } else {
                // this is not a valid set
            }
        }

    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        synchronized(table) {
            for(int i =0; i<table.slotToCard.length; i++) {
                if(table.slotToCard[i] == null) {
                    if(deck.size() > 0) {
                        int card = deck.remove(0);
                        table.placeCard(card, i);
                    }
                }
            }   
        }

    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        // while(System.currentTimeMillis() < reshuffleTime) {
        //     try {
        //         // how to awake the thread because of the player's set
        //         synchronized (this) { wait(); }
        //     } catch (InterruptedException ignored) {}
        // }
        synchronized(keyDealer) {
            try {
                keyDealer.wait(reshuffleTime - System.currentTimeMillis());
            } catch (InterruptedException ignored) {}
        }

    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if(reset) {
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
        }
        else {
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
    }
}
