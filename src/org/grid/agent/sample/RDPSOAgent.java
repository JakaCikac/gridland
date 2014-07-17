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

import java.util.HashMap;
import java.util.Random;

// Run: java -cp bin org.grid.agent.Agent localhost org.grid.agent.sample.RandomAgent

@Membership(team="packs")
public class RDPSOAgent extends Agent {

    // Possible agent states
	private static enum AgentState {
		EXPLORE, SEEK, RETURN
	}

    /* Gridland variables */
    private int x = 0;
	private int y = 0;
	private HashMap<String, Position> registry = new HashMap<String, Position>();
	private AgentState state = AgentState.EXPLORE;
	private Decision left, right, up, down, still;
	private Decision[] decisions;
	private int sn = 1;
	private long sx, sy;
    private int sleepTime = 100;

    // RDPSO variables

    /* number of swarms */
    final private int num_swarms = 3; // initial number of swarms
    final private int max_swarms = 4; // maximum number of swarms
    final private int min_swarms = 0; // minimum number of swarms (0, to allow social exclusion of all agents)

    /* number of agents in each swarm */
    final private int init_agents = 5; // initial number of agents in each swarm
    final private int max_agents = 15; // maximum number of agents in each swarm
    final private int min_agents = 0;  // minimum number of agetns in each swarm

    /* RDPSO coefficients */
    final private double w = 0.6;   // inertial coefficient
    final private double c1 = 0.8;  // cognitive weight
    final private double c2 = 0.04; // social weight
    final private double c3 = 0.2;  // obstacle suspectibility weight
    final private double c4 = 0.4;  // communication constraint weight

    final private int SCMax  = 15;     // maximum number of iterations without improving the swarm
    final private int CommRange = 200; // maximum communication range

    /* memorized parameters of each agent */
    private double  memory_local = 0;  // comm stuff
    private double  memory_global = 0; // comm stuff

    private double memory_vx = 0;
    private double memory_vx_t1 = 0;

    private int num_kill = 0;            // number of killed agents in swarm
    private double SC = 0;               // search counter
    private boolean callRobot = false;   // need of calling a agent
    private boolean createSwarm = false; // need of creating a swarm

    private int swarm_id = 1;            // which swarm does the agent belong to
    // TODO: napisi metodo, ki razporeja agente po swarmih ob inicializaciji

    /* swarm objectives */
    private double mainBestFunction = 0;  // main objective function
    private double gbestValue = 0;        // global best value, init to 0
    private double obsBestFunction = 0;

    /* initialize best cognitive, global and obstacle position as agent's own position */
    private double xcognitive = 0;
    private double ycognitive = 0;
    private double xgbest = 0;
    private double ygbest = 0;
    private double xobs = 0;
    private double yobs = 0;


    /* message has to include:
      * swarm_id
      * memory_local
      * memory_global
      * mainBestFunction
      * gbestValue
      * obsBestValue (range to obstacle)
      * xcognitive
      * ycognitive
      * xgbest
      * ygbest
      * xobs
      * yobs
      * num_kill
      * sc
      * callrobot
      * createswarm
      */


    /* possible decisions */
	@Override
	public void initialize() {
		left = new Decision(Direction.LEFT);
		right = new Decision(Direction.RIGHT);
		up = new Decision(Direction.UP);
		down = new Decision(Direction.DOWN);
		still = new Decision(Direction.NONE);

		decisions = new Decision[] {
			left, right, up, down
		};
	}

    private void initializeRDPSO(Neighborhood neighborhood) {
        //todo: nimam pojma kako mu bom dolocil lastno pozicijo in kako bom potem ponastavljal
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
                initializeRDPSO(neighborhood);

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
				Thread.sleep(sleepTime);
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

        if (n != null) {
            for (int i = -n.getSize(); i <= n.getSize(); i++) {
                for (int j = -n.getSize(); j <= n.getSize(); j++) {

                    // if cell is hq, put a mark in the registry
                    if (n.getCell(i, j) == Neighborhood.HEADQUARTERS) {
                        registry.put("hq", new Position(x + i, y + j));
                        continue;
                    }

                    // if cell is occupied as an agent send a hello message
                    if (n.getCell(i, j) > 0) {

                        // For RANDOM agent, we don't need to send messages.
                        if (! (i == 0 && j == 0) )
                            //send(n.getCell(i, j), "Hello " + n.getCell(i, j) + "!");
                        continue;
                    }
                }
            }
         }
	}

	private Decision updateDecisions(Neighborhood n, AgentState state) {

        // Check which ways the agent can move.
        if (n == null)
            return still;

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
                return decisions[i];
            } else if (decisions[i].getDirection().toString().equals("RIGHT") && moves[3]) {
                return decisions[i];
            }
        }
        // if no other move is available, stand still for the current move
        return still;
	}

    //FisherYates shuffle for random array shuffle
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