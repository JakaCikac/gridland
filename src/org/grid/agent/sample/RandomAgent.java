/*
 *  AgentField - a simple capture-the-flag simulation for distributed intelligence
 *  Copyright (C) 2011 Luka Cehovin <http://vicos.fri.uni-lj.si/lukacu>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */
package org.grid.agent.sample;

import org.grid.agent.Agent;
import org.grid.agent.Membership;
import org.grid.protocol.Message.Direction;
import org.grid.protocol.Neighborhood;
import org.grid.protocol.Position;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

// Run: java -cp bin org.grid.agent.Agent localhost org.grid.agent.sample.RandomAgent

@Membership(team="packs")
public class RandomAgent extends Agent {

    // Possible agent states
	private static enum AgentState {
		EXPLORE, SEEK, RETURN
	}

    private int x = 0;
	private int y = 0;
	private HashMap<String, Position> registry = new HashMap<String, Position>();
	private AgentState state = AgentState.EXPLORE;
	private Decision left, right, up, down, still;
	private Decision[] decisions;
	private int sn = 1;
	private long sx, sy;
	
	@Override
	public void initialize() {

		left = new Decision(Direction.LEFT);
		right = new Decision(Direction.RIGHT);
		up = new Decision(Direction.UP);
		down = new Decision(Direction.DOWN);
		still = new Decision(Direction.NONE);

		decisions = new Decision[] {
			left, right, up, down, still
		};
	}

	@Override
	public void receive(int from, byte[] message) {
        /* In this method, the agent should receive information from other agents
         * and update what it knows about the surrounding world.
         */
		String msg = new String(message);
		//System.out.format("Message received from %d: %s\n", from, msg);
	}

	@Override
	public void state(int stamp, Neighborhood neighborhood, Direction direction) {

		synchronized (waitMutex) {
			this.neighborhood = neighborhood;
			this.direction = direction;

			if (state != AgentState.RETURN)
				state = AgentState.EXPLORE;
			
			waitMutex.notify();
		}
	}

	@Override
	public void terminate() {

	}

	private Direction moving = Direction.DOWN;

	protected static class Decision  {

		private Direction direction;

		public Direction getDirection() {
			return direction;
		}

		public void setDirection(Direction direction) {
			this.direction = direction;
		}

		public Decision(Direction direction) {
			super();
			this.direction = direction;
		}

		public String toString() {
			return String.format("%s", direction.toString());
		}

	}

	@Override
	public void run() {

		while (isAlive()) {

			try {

				scanAndWait();
				analyzeNeighborhood(neighborhood);

				if (direction == Direction.NONE) {

					if (moving != Direction.NONE) {
						switch (moving) {
						case DOWN:
							y += 1;
							break;
						case UP:
							y -= 1;
							break;
						case LEFT:
							x -= 1;
							break;
						case RIGHT:
							x += 1;
							break;
						}
						
						sn++;
						sx += x;
						sy += y;
					}

                    // If obstacle or agent on path, change direction of movement
					Decision d = updateDecisions(neighborhood, state);

					if (d.getDirection() != Direction.NONE) 
						move(d.getDirection());

					moving = d.getDirection();
				}

			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}

		}

	}

	private Object waitMutex = new Object();
	private Neighborhood neighborhood;
	private Direction direction;

    /* Send a scan request to the server. The server will respond with the
     * local state of the environment that will be returned to the agent using
     * the state(int, Neighborhood, Direction)} callback. */
	private void scanAndWait() throws InterruptedException {
		synchronized (waitMutex) {
			scan(0);
			waitMutex.wait();
		}
	}

	private void analyzeNeighborhood(Neighborhood n) {

		for (int i = -n.getSize(); i <= n.getSize(); i++) {
			for (int j = -n.getSize(); j <= n.getSize(); j++) {

                // if cell is hq, put a mark in the registry
				if (n.getCell(i, j) == Neighborhood.HEADQUARTERS) {
					registry.put("hq", new Position(x + i, y + j));
					continue;
				}

                // if cell is occupied as an agent send a hello message
                // TODO: send other message
				if (n.getCell(i, j) > 0) {

					if (! (i == 0 && j == 0) )
						//send(n.getCell(i, j), "Hello " + n.getCell(i, j) + "!");
					continue;
				}
			}
		}
	}

	private Decision updateDecisions(Neighborhood n, AgentState state) {

        // Check which ways the agent can move.
        boolean up = canMove(n, 0, -1);
        boolean down = canMove(n, 0, 1);
        boolean left =  canMove(n, -1, 0);
        boolean right = canMove(n, 1, 0);
        boolean moves[] = {up, down, left, right};

        // Randomly shuffle possible moves.
        shuffleArray(decisions);

        // Check which moves are available against the shuffled array
        // then choose the move first possible move from the array.
        for (int i = 0; i < decisions.length; i++) {
            if ( decisions[i].getDirection().toString().equals("UP") && moves[0]) {
                return decisions[i];
            } else if (decisions[i].getDirection().toString().equals("DOWN") && moves[1]) {
                return decisions[i];
            } else if (decisions[i].getDirection().toString().equals("LEFT") && moves[2]) {
                return  decisions[i];
            } else if (decisions[i].getDirection().toString().equals("RIGHT") && moves[3]) {
                return decisions[i];
            }
        }
        // if no other move is available, stand still for a round
        return still;
	}

    // Implementing FisherYates shuffle
    static void shuffleArray(Decision[] ar)
    {
        Random rnd = new Random();
        for (int i = ar.length - 1; i > 0; i--)
        {
            int index = rnd.nextInt(i + 1);
            // Simple swap
            Decision a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
        }
    }
	
	private boolean canMove(Neighborhood n, int x, int y) {

			return n.getCell(x, y) == Neighborhood.EMPTY;
	}

}
