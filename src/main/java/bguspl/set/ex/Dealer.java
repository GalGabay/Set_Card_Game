package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;

import java.util.Collections;
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
            updateTimerDisplay(true);
             // probably true
            removeAllCardsFromTable();
        }
        announceWinners();
        terminate();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 900;
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            //env.ui.setCountdown(reshuffleTime-System.currentTimeMillis(), false);
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 900;
        // for(Player player : players) {
        //     synchronized(player.keyPlayer) {
        //         player.keyPlayer.notifyAll();
                
        //     }
        // }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implements
        terminate = true;
        for(int i = players.length-1; i>=0; i--) {
            players[i].terminate();
        }   
        Thread.currentThread().interrupt();
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
        synchronized(table) { 
            // TODO implement
            Player playerClaimsSet;
            synchronized(keyDealer) {
                playerClaimsSet = playerSetQueue.poll();
            }
            
            if(!(playerClaimsSet == null)) { 
                synchronized(playerClaimsSet.keyPlayer) {
                    if(playerClaimsSet.tokensSize() == 3) { 
                        env.logger.info("MYLOG:dealer is awake and checking set of player " + playerClaimsSet.id);
                        // player claims set and check if the set is valid and remove the 3 cards
                        int[] cards = new int[3];
                        if(playerClaimsSet.tokensSize() == 3) {
                            for(int i=0; i<3; i++) {
                                int slot;
                                slot = playerClaimsSet.tokens.poll();
                                env.logger.info("MYLOG: slot: " + slot + " of player " + playerClaimsSet.id);
                                cards[i] = table.slotToCard[slot];
                            }
                        }

                        if(env.util.testSet(cards)) {
                            // this is a valid set
                            playerClaimsSet.point();
                            updateFreezeDisplay();
                            synchronized(playerClaimsSet.keyPlayer) {
                                playerClaimsSet.frozen = true;
                            }
                            removeTokensFromSet(playerClaimsSet, cards);
                            // for(int i=0; i<3; i++) {
                            //     int slot = table.cardToSlot[cards[i]];
                            //     table.removeCard(slot);
                            //     table.removeToken(playerClaimsSet.id, slot);
                            // }
                            //placeCardsOnTable(); // added this line
                           // updateTimerDisplay(true);
                        } else {
                            // this is not a valid sets
                            playerClaimsSet.penalty();
                            updateFreezeDisplay();
                            synchronized(playerClaimsSet.keyPlayer) {
                                playerClaimsSet.frozen = true;
                            }
                        
                            try {
                                synchronized(playerClaimsSet.keyPlayer) {
                                    playerClaimsSet.tokens.put(table.cardToSlot[cards[0]]);
                                    playerClaimsSet.tokens.put(table.cardToSlot[cards[1]]);
                                    playerClaimsSet.tokens.put(table.cardToSlot[cards[2]]);
                                }
                            } catch (InterruptedException e) {}
            
                                // playerClaimsSet.keyPressed(table.cardToSlot[cards[0]]);
                                // playerClaimsSet.keyPressed(table.cardToSlot[cards[1]]);
                                // playerClaimsSet.keyPressed(table.cardToSlot[cards[2]]);

                        
                            
                        
                    }
               // }
                    synchronized(playerClaimsSet.keyPlayer) {
                        playerClaimsSet.keyPlayer.notifyAll();
                    }
            
        }
    }
    }
}
        
}

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        synchronized(table) {
            Collections.shuffle(deck);
            int countNulls = 0;
            int cardsToPlace = 0;
            for(int i =0; i<table.slotToCard.length; i++) {
                if(table.slotToCard[i] == null) {
                    countNulls++;
                }
            }
            if(countNulls < deck.size()) {
                cardsToPlace = countNulls;
            } else {
                cardsToPlace = deck.size();
            }

            for(int i =0; i<table.slotToCard.length; i++) {
                if(table.slotToCard[i] == null && cardsToPlace > 0) {
                    cardsToPlace--;
                    int card = deck.remove(0);
                    table.placeCard(card, i);    
                }
            }   
            
        }
        table.hints(); // DELETE

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
            //env.logger.info("get into sleepUntilWokenOrTimeout");
            try {
                if(reshuffleTime - System.currentTimeMillis() < env.config.turnTimeoutWarningMillis) {
                  //  env.logger.info("dealer wakes up each 100 milisec");
                    keyDealer.wait(100);
                } else {
                //keyDealer.wait(reshuffleTime - System.currentTimeMillis());
                  //  env.logger.info("dealer wakes up each 1000 milisec");
                    keyDealer.wait(100);
                }
            } catch (InterruptedException ignored) {}
        }
        env.logger.info("MYLOG:Dealer is awake after a 100 milis break");

    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if(reset) {
            reshuffleTime = env.config.turnTimeoutMillis + System.currentTimeMillis() + 900;
        //    env.logger.info("reshuffleTime: " + (reshuffleTime - System.currentTimeMillis()));
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);

        }
        // else {
        //     env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);
        // }
        else if(reshuffleTime - System.currentTimeMillis() < env.config.turnTimeoutWarningMillis) {
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), true);
        } 
        else {
            env.ui.setCountdown(reshuffleTime - System.currentTimeMillis(), false);
        }
        updateFreezeDisplay();
         
            // if(players[i].freezeTime - System.currentTimeMillis()+1000 <= 0) {
            //     synchronized(players[i].keyPlayer) {
            //         players[i].keyPlayer.notify();
            //     }
        //     } else {
        //         try {
        //             synchronized(players[i].keyPlayer) {
        //                 players[i].keyPlayer.wait();
        //             }
        //         }
        //         catch (InterruptedException e) {}
        //     }
            
         
    }

private void updateFreezeDisplay() {
    for(int i=0; i<players.length; i++) {
        if(!(players[i].freezeTime - System.currentTimeMillis() > 0 & players[i].freezeTime - System.currentTimeMillis() < 1000)) {
            env.ui.setFreeze(i, players[i].freezeTime - System.currentTimeMillis());
        } else {
            env.ui.setFreeze(i, 0);
        }
    }
}   


    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        synchronized(table) {
            env.logger.info("MYLOG:dealer get into removeAllCardsFromTable");
            synchronized(keyDealer) {
                playerSetQueue.clear();
            }
           
        // TODO implement
            for(Player player : players) {
                synchronized(player.keyPlayer) {
                    player.keyPlayer.notifyAll();
                    int tokenSize = player.tokens.size();
                    for(int i=0; i<tokenSize; i++) {
                        table.removeToken(player.id, player.tokens.poll());
                    }
                    player.pressedKeys.clear();
                }
            }
            for(int i =0; i<table.slotToCard.length; i++) {
                if(table.slotToCard[i] != null) {
                    deck.add(table.slotToCard[i]);
                    table.removeCard(i);
                }
            }
            Collections.shuffle(deck);   
        }

}

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        env.logger.info("get into announceWinners");
        LinkedBlockingQueue<Player> winners = new LinkedBlockingQueue<Player>();
        int winningScore = 0;
        int numOfWinners = 0;
        for(Player player : players) {
            if(winningScore < player.score()) {
                winningScore = player.score();
            }
        }
        for(Player player : players) {
            if(player.score() == winningScore) {
                numOfWinners++;
                winners.add(player);
            }
        }
        int[] winnersArray = new int[numOfWinners];
        for(int i=0; i<numOfWinners; i++) {
            Player winner = winners.poll();
            winnersArray[i] = winner.id;
        }
        env.ui.announceWinner(winnersArray);

    }

    public void removeTokensFromSet(Player playerClaimsSet, int[] cards) {
        for(int i=0 ; i<3 ; i++ ) {
            int slot = table.cardToSlot[cards[i]];
            for (Player player : players) {
                table.removeToken(player.id, slot);
                if (player.id != playerClaimsSet.id) {  
                    if (player.tokensContains(slot)) {
                        player.tokensRemove(slot);
                        synchronized(player.keyPlayer) {
                            player.keyPlayer.notifyAll();
                        }
                        // if(playerSetQueue.contains(p)) {
                        //     playerSetQueue.remove(p);
                        // }
                    }   
                } else {
                    table.removeToken(playerClaimsSet.id, slot);
                }
            }
            table.removeCard(slot);
        }
        playerClaimsSet.tokens.clear();
    }
}
