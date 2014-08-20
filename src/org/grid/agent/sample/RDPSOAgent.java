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
 *
 */
package org.grid.agent.sample;

import org.grid.agent.Agent;
import org.grid.agent.Membership;
import org.grid.arena.SwingView;
import org.grid.protocol.Message.Direction;
import org.grid.protocol.Neighborhood;
import org.grid.protocol.Position;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

// Run: java -cp bin org.grid.agent.Agent localhost org.grid.agent.sample.RandomAgent

@Membership(team = "rdpso")
public class RDPSOAgent extends Agent {

    private LocalMap map = new LocalMap();
    private Position position = new Position(0, 0);
    private int timestep = 1;
    private Set<Position> moveable = new HashSet<Position>();
    private HashMap<Integer, MemberData> registry = new HashMap<Integer, MemberData>();
    private Position origin = null;
    private Vector<Position> history = new Vector<Position>();


    private static class State {

        Neighborhood neighborhood;

        Direction direction;

        public State(int stamp, Neighborhood neighborhood, Direction direction) {
            super();
            this.neighborhood = neighborhood;
            this.direction = direction;
        }

    }

    /* ########################
       RDPSO variables
    ###########################  */

    private int swarmID = -1;            // which swarm does the agent belong to
    // swarmID = 0 = agent belongs to the socially excluded group
    private static int swarm_counter = 1;

    private double SC = 0;                // stagnancy counter
    private boolean callAgent = false;    // need of calling a agent
    private boolean createSwarm = false;  // need of creating a swarm

    private int numSwarms = 3; // initial number of swarms

    /* shared variables in swarm .. */
    private int numAgents = 5; // current number of agents in each swarm
    private int numKilledAgents = 0; // initial excluded robots


    int agentPositionX = 0; // x_n(t)
    int agentPositionY = 0; // x_n(t)

    double agentVelocityX = 0;
    double agentVelocityY = 0;

    double agentSolution = 0; // h(x_n(t))
    double agentBestSolution = 0; // h_best

    // X_1_1(t) = cognitive x, X_1_2(t) = cognitive y, X_2_1(t) = social X, X_2_2(t) = social Y,
    // X_3_1(t) = obstacle X, X_3_2(t) = obstacle Y
    double solutionArray[] = new double[6];

    double bestSwarmSolution = 0; // H_best , shared
    double obstacleSolution = 0; // g(x_n(t))
    double obstacleBestSolution = 0; // g_best , shared

    // every agent has the same constants but it's own randoms
    double constantArray[] = new double[3];
    double randomArray[] = new double[3];

    double swarmSolutionArray[];

    // cleanMove tells the agent to only move
    boolean cleanMove = false;
    Position goalPosition = null;
    // agentPositionX,Y = current agent position
    // agentSolution = agent's current solution
    // agentBestSolution = agent's best solution so far

    boolean positionNotInMap = false;
    boolean knownLocalMap = false;


    /**
     * Define agent message format
     */
    private static class Message {

        int from;
        byte[] message;

        protected Message(int from, byte[] message) {
            super();
            this.from = from;
            this.message = message;
        }
    }

    private static class DistanceFilter implements LocalMap.Filter {

        private Position p;

        private int mindistance, maxdistance;

        public DistanceFilter(Position p, int mindistance, int maxdistance) {
            this.p = p;
            this.mindistance = mindistance;
            this.maxdistance = maxdistance;
        }

        @Override
        public boolean filter(LocalMap.Node n) {
            int distance = Position.distance(n.getPosition(), p);
            return distance <= maxdistance && distance >= mindistance;
        }

    }

    private static class MemberData {

        // agent id
        private int id;
        private int info;
        private boolean map = false;
        private int timediff;
        private Position position;
        int notified;

        private LocalMap.Bounds bounds;
        private Position origin, center;

        public int getId() {
            return id;
        }

        public Position getPosition() {
            return position;
        }

        public void setPosition(Position position) {
            this.position = position;
        }

        protected MemberData(int id) {
            super();
            this.id = id;
        }

        @Override
        public String toString() {

            return String.format("ID: %d", id);

        }
    }

    private JFrame window;
    private LocalMap.LocalMapArena arena;
    private SwingView view;

    @Override
    public void initialize() {

        // 6 = scope of the arena
        arena = map.getArena(6);

        // enable arena, to monitor agent's behaviour, to disable, uncomment if
        //if (System.getProperty("rdpso") != null) {
        view = new SwingView(24);

        view.setBasePallette(new SwingView.HeatPalette(32));
        window = new JFrame("Agent " + getId());
        window.setContentPane(view);
        window.setSize(view.getPreferredSize(arena));
        window.setVisible(true);
        //}
    }

    /**
     * Send information to agent in swarm.
     *
     * @param to
     */
    private void sendInfo(int to) {

        /*

            Required info in a message:
            -------------------------------------
            int swarmID

            int agentPositionX
            int agentPositionY
            double agentBestSolution
            double solutionArray
            (includes local,global,obstacle best (x and y for each)
            double bestSwarmSolution
            double obstacleBestSolution
            double SC
            boolean callAgent
            boolean createSwarm
            int numAgents
            int numKilledAgents

         */


        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(getMaxMessageSize());
            ObjectOutputStream out = new ObjectOutputStream(buffer);

            out.writeByte(1); // 1 = info, 2 = map

            LocalMap.Bounds bounds = map.getBounds();

            Position center = map.getCenter();

            out.writeInt(origin.getX());
            out.writeInt(origin.getY());
            out.writeInt(bounds.getTop());
            out.writeInt(bounds.getBottom());
            out.writeInt(bounds.getLeft());
            out.writeInt(bounds.getRight());

            out.writeInt(center.getX());
            out.writeInt(center.getY());

            out.writeInt(swarmID);
            //out.writeInt(agentPositionX);
            //out.writeInt(agentPositionY);
            //out.writeDouble(agentBestSolution);
            //out.writeDouble(obstacleBestSolution);
            // todo: write solution array, how? serialize? to byte array?
            out.writeDouble(bestSwarmSolution);

            out.writeDouble(SC);
            out.writeBoolean(callAgent);
            out.writeBoolean(createSwarm);
            out.writeInt(numAgents);
            out.writeInt(numKilledAgents);

            out.writeInt(timestep);

            out.flush();

            send(to, buffer.toByteArray());

        } catch (IOException e) {
            debug("Error sending message to %d: %s", to, e);
        }

    }

    private boolean parse(int from, byte[] message, Neighborhood neighborhood) {

        try {
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(message));

            int type = in.readByte();
            int preTime;

            int recSwarmID = -1;


            switch (type) {
                case 1: { // info message

                    Position origin = new Position(0, 0);
                    origin.setX(in.readInt());
                    origin.setY(in.readInt());

                    LocalMap.Bounds bounds = new LocalMap.Bounds(0, 0, 0, 0);
                    Position center = new Position(0, 0);
                    bounds.setTop(in.readInt());
                    bounds.setBottom(in.readInt());
                    bounds.setLeft(in.readInt());
                    bounds.setRight(in.readInt());

                    center.setX(in.readInt());
                    center.setY(in.readInt());


                    // RDPSO variables
                    recSwarmID = in.readInt();
                    if (recSwarmID == swarmID) {
                        // update stuff
                        //agentPositionX = in.readInt();
                        //agentPositionY = in.readInt();
                        //agentBestSolution = in.readDouble();
                        //obstacleBestSolution = in.readDouble();
                        bestSwarmSolution = in.readDouble();

                        // todo: HOW DO I SYNC THIS STUFF? .. ?
                        SC = in.readDouble();
                        callAgent = in.readBoolean();
                        createSwarm = in.readBoolean();
                        numAgents = in.readInt();
                        numKilledAgents = in.readInt();

                        preTime = in.readInt();
                        int timediff = preTime - timestep;

                        debug("Updated info: %f %f %b %b %d %d", bestSwarmSolution, SC, callAgent, createSwarm, numAgents, numKilledAgents);

                        synchronized (registry) {
                            if (registry.containsKey(from)) {
                                MemberData data = registry.get(from);
                                data.bounds = bounds;
                                data.origin = origin;
                                data.center = center;
                                data.info = timestep;
                                data.timediff = timediff;
                                //debug("New info: %s %s %s %s", data.bounds, data.origin, data.center, data.info);
                            }
                        }
                    }
                    break;
                }
                case 2: { // map message

                    LocalMap.MapChunk chunk = new LocalMap.MapChunk();
                    boolean replan = false;
                    int chunks = 0;

                    while (true) {

                        try {

                            chunk.read(in);
                            replan |= map.update(chunk);
                            chunks++;

                        } catch (IOException e) {
                            break;
                        }
                    }

                    debug("Got %d map chunks from %d, new data %b", chunks, from, replan);

                    if (!map.verify())
                        debug("Map no longer valid!");
                    return replan;
                }
            }

        } catch (Exception e) {
            debug("Error parsing message from %d: %s", from, e);
        }

        return false;
    }

    /**
     * Print out debug information.
     *
     * @param format
     * @param objects
     */
    protected void debug(String format, Object... objects) {
        System.out.println("[" + getId() + "]: " + String.format(format, objects));
    }

    @Override
    public void receive(int from, byte[] message) {
        /* In this method, the agent should receive information from other agents
         * and update what it knows about the surrounding world.
         */
        // add new message to concurrent linked queue
        inbox.add(new Message(from, message));
    }

    private ConcurrentLinkedQueue<State> buffer = new ConcurrentLinkedQueue<State>();
    private ConcurrentLinkedQueue<Message> inbox = new ConcurrentLinkedQueue<Message>();
    private ConcurrentLinkedQueue<Direction> plan = new ConcurrentLinkedQueue<Direction>();

    private int parent = -1;

    @Override
    public void state(int stamp, Neighborhood neighborhood, Direction direction) {

        buffer.add(new State(stamp, neighborhood, direction));

    }

    @Override
    public void terminate() {
    }

    public void initializeRDPSO() {
        // initialize cognitive, social and obstacle as agent's own position
        swarmID = assignToSwarm();
        //System.out.println("Swarm ID: " + swarmID);
        callAgent = false;
        createSwarm = false;

        // The array index indicates which swarmID it belongs to.
        SC = 0;
        numAgents = ConstantsRDPSO.INIT_AGENTS;
        numKilledAgents = 0;

        swarmSolutionArray = new double[ConstantsRDPSO.MAX_AGENTS];

        // This is the same for all agents.
        constantArray[0] = ConstantsRDPSO.C1;
        constantArray[1] = ConstantsRDPSO.C2;
        constantArray[2] = ConstantsRDPSO.C3;

    }

    /*
        assign agents to different (incremental) swarm groups inside the main swarm (1 to MAX_SWARMS-1), 0 is social exclusion
     */
    private int assignToSwarm() {

        // in case there is just one swarm
        if (ConstantsRDPSO.MAX_SWARMS == 1)
            return 1;

        if (getSwarmCounter() == ConstantsRDPSO.MAX_SWARMS - 1)
            setSwarmCounter(0);

        incrementSwarmCounter();

        return getSwarmCounter();
    }

    @Override
    public void run() {

        int sleeptime = 200;
        boolean replanMap = false;
        boolean explore = false;
        int offsetX = 0;
        int offsetY = 0;
        boolean offsetCurrent = false;
        boolean clearMove = false;

        scan(0);

        initializeRDPSO();

        while (isAlive()) {

            // check for new state data
            State state = buffer.poll();
            // check if state is null, if null, you must call scan(0)
            if (state != null) {

                // todo: where do I update this?
                agentPositionX = position.getX();
                agentPositionY = position.getY();

                if (state.direction == Direction.NONE && state.direction != null) {

                    // Send information to other agents
                    Set<Position> movable = analyzeNeighborhood(state.neighborhood);

                    // update local map (otherwise plan can't be executed)
                    replanMap = map.update(state.neighborhood, position, timestep);

                    boolean replanAgents = blockMoveable(movable, state.neighborhood);

                    registerMoveable(movable, state.neighborhood);
                    // todo: sharing info with agents in subswarm, maybe timeouts

                    // update information
                    while (!inbox.isEmpty()) {
                        Message m = inbox.poll();
                        // receive and parse message from other agents, filter data from agent's swarm
                        replanMap &= parse(m.from, m.message, state.neighborhood);
                    }
                    if (replanMap || replanAgents) {
                        // not sure if I need this here.
                            plan.clear();
                            if (goalPosition != null)
                                replan(goalPosition);

                        if (replanAgents && detectLock())
                            goalPosition = null;
                    }

                    // update arena
                    if (view != null)
                        view.update(arena);

                    // check if iteration is a movement iteration
                    if (!cleanMove) { // && !clearMove && !detectLock()

                        // confirm that the agent is not a member of the excluded group (swarmID = 0)
                        if (swarmID != 0) {

                            // evaluate agent's current solution = h(x_n(t))
                            agentSolution = evaluateObjectiveFunction(agentPositionX, agentPositionY);

                            // check if agent improved and update agent's best solution
                            if (agentSolution > agentBestSolution) {
                                agentBestSolution = agentSolution;
                                // update best cognitive solution
                                solutionArray[0] = agentPositionX;
                                solutionArray[3] = agentPositionY;
                            }

                            // add agentSolution to vector H(t) that includes solutions of all agents within the swarmID group
                            // todo: synced version of this concatenating the solutions of subswarms
                            // SwarmSolution.mergeSolutionToArray(); //addToSwarmSolutionArray(ConstantsRDPSO.MAX_SWARMS, ConstantsRDPSO.MAX_AGENTS, swarmID, agentBestSolution);
                            // wait till all agents put solutions in solution array...
                            // todo: when agent dies, you have to check how many solutions are left in the array

                            // find best solution in vector H(t) = max(H(t))
                            // todo: ne pozabi vklopit funkcije nazaj
                            double maxSwarmSolution = evaluateObjectiveFunction(agentPositionX, agentPositionY);
                            //SwarmSolution.findMaxInSwarmSolutionArray(swarmID, swarmSolutionArray);
                            // check if subgroup improved
                            if (maxSwarmSolution > bestSwarmSolution) {
                                bestSwarmSolution = maxSwarmSolution;
                                // update social component
                                solutionArray[1] = agentPositionX;
                                solutionArray[4] = agentPositionY;
                                // decrease stagnancy counter
                                if (SC > 0)
                                    SC = SC - 1;
                                // check if group can be rewarded
                                if (SC == 0) {
                                    if ((numAgents < ConstantsRDPSO.MAX_AGENTS) && spawnAgentProbability()) {
                                        System.out.println("Sending new agent request.");
                                        // todo: send new agent request
                                        // sendNewAgentRequest();
                                        if (numKilledAgents > 0) {
                                            // decrease excluded agents counter
                                            numKilledAgents--;
                                            // check if group can spawn a new subgroup
                                        }
                                    }
                                    if (spawnGroupProbability()) {
                                        System.out.println("Sending new group request.");
                                        // todo: send new group request
                                        //sendNewGroupRequest();
                                        if (numKilledAgents > 0) {
                                            // decrease excluded agents counter
                                            numKilledAgents--;
                                        }
                                    }
                                }
                                // subgroup has NOT IMPROVED
                            } else {
                                SC = SC + 1;
                                if (SC == ConstantsRDPSO.SC_MAX) { // punsh subgroup
                                    if (numAgents > ConstantsRDPSO.MIN_AGENTS) { // check if agent can be excluded
                                        numKilledAgents++;
                                        // reset stagnancy counter
                                        SC = ConstantsRDPSO.SC_MAX * (1 - (1 / (numKilledAgents + 1)));
                                        // if this is the worst preforming agent in the group, exclude
                                        if (agentBestSolution == SwarmSolution.findMinInSwarmSolutionArray(swarmID, swarmSolutionArray)) {
                                            // exclude agent
                                            System.out.println("EXCLUDED!");
                                            swarmID = 0;
                                            numAgents--;
                                        }
                                    } else { // delete the entire subgroup
                                        // exclude this agent
                                        swarmID = 0;
                                        numAgents--;
                                    }
                                }
                            }
                            // evaluate obstacle function
                            // obstacleSolution is the distance from agent's position to the obstacle
                            obstacleSolution = evaluateObstacleFunction(state);
                            if (obstacleSolution >= obstacleBestSolution) {
                                // check if obstacleSolution is better than currently best solution
                                obstacleBestSolution = obstacleSolution;
                                // update obstacle component with agent's current solution
                                solutionArray[2] = agentPositionX;
                                solutionArray[5] = agentPositionY;
                            }


                            if (goalPosition == null) {
                                //System.out.print("Recalculating position.");
                                goalPosition = new Position(0, 0);
                                if (offsetCurrent) {
                                    //System.out.println(" Using offset.");
                                    offsetX = generateRandomOffset(2, 12);
                                    offsetY = generateRandomOffset(2, 12);
                                } else {
                                    offsetX = 0;
                                    offsetY = 0;
                                }

                                // UPDATE AGENT'S VELOCITY
                                double functionResultX = 0.0;
                                randomArray[0] = Math.random();
                                randomArray[1] = Math.random();
                                randomArray[2] = Math.random();
                                for (int i = 0; i < 3; i++) {
                                    functionResultX += constantArray[i] * randomArray[i] * (solutionArray[i] - agentPositionX);
                                }
                                double functionResultY = 0.0;
                                randomArray[0] = Math.random();
                                randomArray[1] = Math.random();
                                randomArray[2] = Math.random();
                                for (int i = 0; i < 3; i++) {
                                    functionResultY += constantArray[i] * randomArray[i] * (solutionArray[i + 3] - agentPositionY);
                                }
                                agentVelocityX = ConstantsRDPSO.W + functionResultX;
                                agentVelocityY = ConstantsRDPSO.W + functionResultY;

                                // UPDATE AGENT'S POSITION
                                double tempAgentPositionX = agentPositionX + agentVelocityX;
                                int roundedX = (int) Math.round(tempAgentPositionX);

                                double tempAgentPositionY = agentPositionY + agentVelocityY;
                                int roundedY = (int) Math.round(tempAgentPositionY);

                                goalPosition.setX(roundedX + offsetX);
                                goalPosition.setY(roundedY + offsetY);

                                System.out.println(" New wanted position: " + goalPosition);
                            }

                            // Is agent on goal position or is the position not even in the map?
                            if ((goalPosition.getX() - position.getX()) == 0 && (goalPosition.getY() - position.getY()) == 0 || positionNotInMap) {

                                if ((goalPosition.getX() - position.getX()) == 0 && (goalPosition.getY() - position.getY()) == 0) {
                                    System.out.println("Goal " + goalPosition + " reached!!");
                                } else if (positionNotInMap) {
                                    System.out.println(goalPosition + " is not on the map!!");
                                }

                                // reset goal position so a new one can be calculated
                                goalPosition = null;
                                // go out of cleanMove or explore
                                explore = false;
                                cleanMove = false;

                                if (positionNotInMap) {
                                    // the next position may be in the map
                                    positionNotInMap = false;
                                    // tell the agent that he has the whole map and should use planning
                                    knownLocalMap = true;
                                    System.out.println("THE WHOLE MAP HAS BEEN EXPLORED.");
                                }
                            } else {
                                // Is goal position clear?
                                boolean movePossible = positionPossible(state.neighborhood, (goalPosition.getX() - position.getX()), (goalPosition.getY() - position.getY()));

                                if ((movePossible && !knownLocalMap) || (movePossible && knownLocalMap)) {

                                    //System.out.println("Move is possible, going into clean move!");
                                    // call move with local coordinates (offset from 0,0)
                                    if (cleanMove(goalPosition.getX(), goalPosition.getY()) != null) {
                                        // jump into movement execution next iteration
                                        if (goalPosition != null)
                                            cleanMove = true; // this can happen if no local map

                                    } else {
                                        // move is possible but not accessible yet - go to explore
                                        //System.out.println(goalPosition + " is unknown, entering explore.");
                                        offsetCurrent = false;
                                        exploreMove();
                                        explore = true;
                                        cleanMove = true;

                                    }
                                    // CASE: is unknown
                                } else if (!movePossible && !knownLocalMap) {
                                    // CASE: is wall / obstacle
                                    // check if the position is an actual wall
                                    if (isWall(goalPosition)) {
                                        //System.out.println(goalPosition + " may be a wall, not entering explore, choosing different goal.");
                                        offsetCurrent = true;
                                        goalPosition = null;

                                        // CASE: is unknown
                                    } else {
                                        //System.out.println(goalPosition + " is unknown, entering explore.");
                                        offsetCurrent = false;
                                        exploreMove();
                                        explore = true;
                                        cleanMove = true;
                                    }

                                    // CASE: is an obstacle, doesn't exist
                                } else if (!movePossible && knownLocalMap) {
                                    // have to check if node exists on the global map and use plan if it does, otherwise
                                    // it doesn't exist
                                    if (cleanMove(goalPosition.getX(), goalPosition.getY()) != null) {
                                        cleanMove = true;
                                        offsetCurrent = false;
                                    } else {
                                        //System.out.println(goalPosition + " is an obstacle or doesn't exist.");
                                        goalPosition = null;
                                        offsetCurrent = true;
                                    }
                                }
                            }
                            scan(0);
                        } else { // if agent is in the EXCLUDED MEMBERS GROUP

                            // TODO: Watch out, in this section you'll also have to do scans!

                            // Send information to other agents
                            movable = analyzeNeighborhood(state.neighborhood);
                            // update agent's local map
                            map.update(state.neighborhood, position, timestep);
                            registerMoveable(movable, state.neighborhood);

                            while (!inbox.isEmpty()) {
                                Message m = inbox.poll();
                                // receive and parse message from other agents, filter data from agent's swarm
                                parse(m.from, m.message, state.neighborhood);
                            }
                            // update arena
                            if (view != null)
                                view.update(arena);

                            // todo: randomly wander round
                            // randomly wander round

                            // evaluate agent's current solution = h(x_n(t))
                            agentSolution = evaluateObjectiveFunction(agentPositionX, agentPositionY);

                            // check if agent improved and update agent's best solution
                            if (agentSolution > agentBestSolution) {
                                agentBestSolution = agentSolution;
                                // update best cognitive solution
                                solutionArray[0] = agentPositionX;
                                solutionArray[3] = agentPositionY;
                            }
                            // send group info
                            // Send information to other agents
                            movable = analyzeNeighborhood(state.neighborhood);

                            // update agent's local map
                            replanMap = map.update(state.neighborhood, position, timestep);
                            registerMoveable(movable, state.neighborhood);
                            // todo: sharing info with agents in subswarm, maybe timeouts

                            // update information
                            while (!inbox.isEmpty()) {
                                Message m = inbox.poll();
                                // receive and parse message from other agents, filter data from agent's swarm
                                parse(m.from, m.message, state.neighborhood);
                            }

                            // update arena
                            if (view != null)
                                view.update(arena);

                            // build vector H

                            // add agentSolution to vector H(t) that includes solutions of all agents within the swarmID group
                            // todo: synced version of this concatenating the solutions of subswarms
                            // SwarmSolution.mergeSolutionToArray(); //addToSwarmSolutionArray(ConstantsRDPSO.MAX_SWARMS, ConstantsRDPSO.MAX_AGENTS, swarmID, agentBestSolution);
                            // wait till all agents put solutions in solution array...

                            // check if group improved
                            // find best solution in vector H(t) = max(H(t))
                            double maxSwarmSolution = SwarmSolution.findMaxInSwarmSolutionArray(swarmID, swarmSolutionArray);
                            // check if subgroup improved
                            if (maxSwarmSolution > bestSwarmSolution) {
                                bestSwarmSolution = maxSwarmSolution;

                                // check to see, if this agent is one of the best N_I agents in the group
                                boolean best_ni = false;
                                // todo: what if num agents left in swarm < init_agents?
                                // todo: how do you track which solution is whos in the swarm solution array and how do you update them when they are let go from a swarm..?
                                double[] bestNISolutions = SwarmSolution.findTopISolutionsInSwarmSolutionArray(swarmID, swarmSolutionArray, ConstantsRDPSO.INIT_AGENTS);
                                for (int i = 0; i < bestNISolutions.length; i++) {
                                    // check if agent's best solution matches any of topI swarm solutions
                                    if (agentBestSolution == bestNISolutions[i]) {
                                        best_ni = true;
                                    }
                                }
                                // agent is one of the best in the excluded group
                                if (best_ni) {
                                    // note: N_X is the number of agents in the excluded group
                                    // check if number of excluded agents is bigger than number of initial agents
                                    // required to form a sub-swarm then check probability for forming a new group
                                    if (numAgents > ConstantsRDPSO.INIT_AGENTS && spawnGroupProbabilityExcluded()) {
                                        //todo: how to assign new swarm.. I guess you will have to keep a number of swarms
                                        //todo: broadcast a need for N_I-1 robots to form new swarm
                                    }
                                    // if the agent receives a request to join a swarm
                                    else if (true) { // todo: condition is: If received the need to join swarm
                                        swarmID = 2; // todo: change to received swarm number, you have to transmitt that info too
                                        // increase number of swarms?
                                        // increase number of agents in swarm
                                        numAgents++;
                                        // todo: send info about joining a swarm to all TEAMMATES!
                                        // right, how do I do that..

                                    } else if (true) { //todo: condition is: If received info to form a new subgroup
                                        // include agent in the new subgroup
                                        swarmID = 2; // todo: change to received swarm_id_new
                                        // change number of agents to the initial number of agents in a swarm
                                        numAgents = ConstantsRDPSO.INIT_AGENTS;
                                        // reset number of excluded robots
                                        numKilledAgents = 0;
                                        // reset the stagnancy counter
                                        SC = 0;
                                    }
                                }
                            }
                        }
                    } else {
                        // check if agent is in clean move mode (doesn't do anything else but move)
                        // update agent's local map
                        replanMap = map.update(state.neighborhood, position, timestep);
                        // update arena
                        if (view != null)
                            view.update(arena);
                        // in case new map information is received, clear the plan and calculate new position
                        if (replanMap) {
                            plan.clear();
                            if (!explore)
                                replan(goalPosition);
                        }

                        if (!plan.isEmpty()) {

                            Direction d = plan.poll();
                            //debug("Next move: %s", d);

                            timestep++;
                            if (d != org.grid.protocol.Message.Direction.NONE) {
                                move(d);
                            }

                            // update agent position
                            if (d == org.grid.protocol.Message.Direction.LEFT)
                                position.setX(position.getX() - 1);
                            if (d == org.grid.protocol.Message.Direction.RIGHT)
                                position.setX(position.getX() + 1);
                            if (d == org.grid.protocol.Message.Direction.UP)
                                position.setY(position.getY() - 1);
                            if (d == org.grid.protocol.Message.Direction.DOWN)
                                position.setY(position.getY() + 1);

                            // update arena agent position
                            arena.setOrigin(position.getX(), position.getY());

                            scan(0);

                        } else {
                            // reset goal position
                            //goalPosition = null;
                            // mark end of multi move
                            cleanMove = false;
                            explore = false;
                            scan(0);
                        }
                    }
                } else {
                    scan(0);
                }
            }

            try {
                Thread.sleep(sleeptime);
            } catch (InterruptedException e) {
            }
        }
    }

    private boolean canMove(Neighborhood n, int x, int y) {
        // return empty for available tiles
        return n.getCell(x, y) == Neighborhood.EMPTY;

    }

    private Position cleanMove(int moveXleft, int moveYleft) {

        List<Direction> directions = null;

        LocalMap.Paths paths = map.findShortestPaths(position);

        Position p = new Position(moveXleft, moveYleft);

        LocalMap.Node n = map.get(p.getX(), p.getY());

        // node doesn't exist on local map
        if (n == null || n.getBody() == -1) {
            plan.clear();
            return null;
        }

        // calculate directions to node n
        directions = paths.shortestPathTo(n);

        // cannot move anywhere ... assure, that at least one move is in the plan
        if (directions == null) {
            directions = new Vector<Direction>();
            for (int i = 0; i < 1; i++)
                directions.add(Direction.NONE);
        }

        plan.addAll(directions);

        return p;
    }

    private void exploreMove() {

        List<Direction> directions = null;
        LocalMap.Paths paths = map.findShortestPaths(position);
        List<LocalMap.Node> candidates = map.filter(LocalMap.BORDER_FILTER);

        Collections.shuffle(candidates);
        directions = paths.shortestPathTo(candidates);

        if (directions == null) {
            directions = new Vector<Direction>();
            // there is no more area to be explored, this can be taken as if the whole map has been explored
            positionNotInMap = true;
            knownLocalMap = true;
            directions.add(Direction.NONE);
        }

        plan.addAll(directions);
    }

    private void clearMove(State state) {

        List<Direction> directions = null;
        LocalMap.Paths paths = map.findShortestPaths(position);
        List<LocalMap.Node> candidates = map.filter(new DistanceFilter(position,
                state.neighborhood.getSize() - 1,
                state.neighborhood.getSize() + 1));

        directions = paths.shortestPathTo(candidates);
        if (directions != null) {
            plan.addAll(directions);
        } else {
            System.out.println("Stuck.");
        }
    }

    private void replan(Position p) {

        List<org.grid.protocol.Message.Direction> directions = null;

        LocalMap.Paths paths = map.findShortestPaths(position);

        LocalMap.Node n = map.get(p.getX(), p.getY());

        // node doesn't exist on local map
        if (n == null || n.getBody() == -1) {
            plan.clear();
        } else {
            directions = paths.shortestPathTo(n);
        }

        if (directions != null)
            plan.addAll(directions);
    }

    private boolean positionPossible(Neighborhood n, int x, int y) {

        if (n.getCell(x, y) != Neighborhood.EMPTY) {
            //System.out.println("Position: " + x + ", " + y + " is an obstacle or not visible.");
            return false;
        } else {
            // if move is possible
            return true;
        }

    }

    private boolean isWall(Position p) {

        LocalMap.Node n = map.get(p.getX(), p.getY());
        if (n == null) {
            // if the node doesn't exist on the local map, it's unknown
            return false;
        } else {
            // if node exists on the local map it may be an obstacle
            // therefore check if it is and return true/false accordingly
            if (n.getBody() == Neighborhood.WALL ||
                    n.getBody() == Neighborhood.HEADQUARTERS ||
                    n.getBody() == Neighborhood.OTHER_HEADQUARTERS ||
                    n.getBody() == Neighborhood.OTHER)
                return true;
            else return false;
        }
    }

    public static int generateRandomOffset(int max, int min) {
        int random = -min + (int) (Math.random() * ((max - (-min)) + 1));
        return random;
    }

    private double evaluateObjectiveFunction(int agentPositionX, int agentPositionY) {

        // todo: something like alpha*uncovered_cells + beta*time_needed

        double result = 0.0;
        if (Math.random() > 0.70 || Math.random() < 0.20) {
            result = -1 * Math.random() * 100000 + 1;
            //System.out.println("Evaluation: " + result);
            return result;
        } else {
            result = Math.random() * 100000 + 1;
            //System.out.println("Evaluation: " + result);
            return result;
        }

    }

    private double evaluateObstacleFunction(State state) {
        // analyze neighbourhood, find obstacles
        Set<Position> obstacles = analyzeNeighborhoodObstacles(state.neighborhood);
        double functionResult = 0.0;
        for (Position p : obstacles) {
            //if (isObstacle(state.neighborhood, p.getX(), p.getY())) {
            // calculate manhattan distance to point
            Position from = new Position(agentPositionX, agentPositionY);
            Position to = new Position(p.getX(), p.getY());
            functionResult += Position.distance(from, to);
            //}
        }

        // System.out.println("Obstacle evaluation: " + functionResult + ", Current best: " + obstacleBestSolution);
        return functionResult;
    }

    /* check probability of a group spawning a new subgroup */
    private boolean spawnGroupProbability() {
        if ((Math.random() * (numAgents / ConstantsRDPSO.MAX_AGENTS)) > Math.random()) {
            return true;
        } else return false;
    }

    /* check probability of a group spawning a new subgroup from excluded group */
    private boolean spawnGroupProbabilityExcluded() {
        // N_T = total population of agents. todo: I don't keep track of that yet, so change the formula later.
        if ((Math.random() * (numAgents / (ConstantsRDPSO.INIT_AGENTS * ConstantsRDPSO.MAX_SWARMS))) > Math.random()) {
            return true;
        } else return false;
    }

    /* check probability of a group spawning a new agent */
    private boolean spawnAgentProbability() {
        if (Math.random() * (1 / (numKilledAgents + 1)) > Math.random())
            return true;
        else return false;
    }

    private Set<Position> analyzeNeighborhood(Neighborhood n) {
        int x = position.getX();
        int y = position.getY();

        HashSet<Position> moveable = new HashSet<Position>();

        for (int i = -n.getSize(); i <= n.getSize(); i++) {
            for (int j = -n.getSize(); j <= n.getSize(); j++) {

                if ((i == 0 && j == 0))
                    continue;

                if (n.getCell(i, j) == Neighborhood.HEADQUARTERS) {
                    if (origin == null)
                        origin = new Position(x + i, y + j);
                    continue;
                }

                if (n.getCell(i, j) > 0 || n.getCell(i, j) == Neighborhood.OTHER) {
                    moveable.add(new Position(x + i, y + j));
                }

            }

        }
        return moveable;
    }

    private Set<Position> analyzeNeighborhoodObstacles(Neighborhood n) {
        int x = position.getX();
        int y = position.getY();

        HashSet<Position> moveable = new HashSet<Position>();

        for (int i = -n.getSize(); i <= n.getSize(); i++) {
            for (int j = -n.getSize(); j <= n.getSize(); j++) {

                if ((i == 0 && j == 0))
                    continue;

                if (n.getCell(i, j) == Neighborhood.HEADQUARTERS) {
                    if (origin == null)
                        origin = new Position(x + i, y + j);
                    continue;
                }

                if (n.getCell(i, j) > 0 || n.getCell(i, j) == Neighborhood.WALL) {
                    moveable.add(new Position(x + i, y + j));
                }

            }

        }
        return moveable;
    }

    private void registerMoveable(Set<Position> moveable, Neighborhood n) {

        int x = position.getX();
        int y = position.getY();

        HashSet<Position> noticed = new HashSet<Position>(moveable);
        HashSet<Position> lost = new HashSet<Position>(this.moveable);

        noticed.removeAll(this.moveable);
        lost.removeAll(moveable);

        this.moveable.removeAll(lost);

        for (Position p : moveable) {

            int i = p.getX();
            int j = p.getY();

            if (n.getCell(i - x, j - y) < 1)
                continue;

            int id = n.getCell(i - x, j - y);

            synchronized (registry) {

                if (registry.containsKey(id)) {

                    registry.get(id).setPosition(p);

                } else {

                    MemberData member = new MemberData(id);
                    member.setPosition(p);
                    member.notified = -30;
                    registry.put(id, member);
                }

                MemberData data = registry.get(id);

                // send info after some time
                if (Math.abs(timestep - data.notified) > 20) {
                    sendInfo(id);
                    data.notified = timestep;
                    data.map = false;
                }

                // send map after some time
                if (Math.abs(timestep - data.info) < 5 && !data.map) {
                    sendMap(id);
                    data.map = true;
                }
            }
        }

        for (Position p : noticed) {
            int i = p.getX();
            int j = p.getY();

            if (n.getCell(i - x, j - y) > 0) {

            }

        }

    }

    private boolean blockMoveable(Set<Position> moveable, Neighborhood n) {

        boolean replan = false;

        int x = position.getX();
        int y = position.getY();

        map.clearModifiers();

        for (Position p : moveable) {

            int i = p.getX() - x;
            int j = p.getY() - y;




            // if there is an agent
            if (n.getCell(i, j) > 0 || n.getCell(i, j) == Neighborhood.OTHER) {

                int size = 2;

                if (n.getCell(i, j) == Neighborhood.OTHER)
                    size = 3;


                int factor = (Integer.MAX_VALUE - 1) / size;

                for (int ii = -size; ii <= size; ii++) {

                    for (int jj = -size; jj <= size; jj++) {

                        int cost = Math.max(0, size - (Math.abs(ii) + Math.abs(jj)));

                        if (cost > 0)
                            map.addModifier(x + i + ii, y + j + jj, cost * factor);
                    }

                }

                replan = true;
            }

            if (n.getCell(i, j) > 0) {

                if (n.getCell(i, j) > getId() && n.getCell(i, j) != parent) {
                    map.addModifier(x + i, y + j);
                } else {
                    map.addModifier(x + i, y + j - 1);
                    map.addModifier(x + i - 1, y + j);
                    map.addModifier(x + i, y + j);
                    map.addModifier(x + i + 1, y + j);
                    map.addModifier(x + i, y + j + 1);
                }

            } else if (n.getCell(i, j) == Neighborhood.OTHER) {
                // map.addModifier(x+i-1, y+j-1); map.addModifier(x+i,
                // y+j-1); map.addModifier(x+i+1, y+j-1);
                // map.addModifier(x+i-1, y+j);
                map.addModifier(x + i, y + j);
                // map.addModifier(x+i+1, y+j);
                // map.addModifier(x+i-1, y+j+1); map.addModifier(x+i,
                // y+j+1); map.addModifier(x+i+1, y+j+1);
            }
        }

        return replan;

    }

    private void sendMap(int to) {

        Collection<LocalMap.Node> nodes = null;
        Position position = null;
        Position offset = null;
        int timediff = 0;

        synchronized (registry) {

            MemberData data = registry.get(to);

            if (data == null || data.bounds == null)
                return;

            LocalMap.Bounds bounds = new LocalMap.Bounds(data.bounds);
            Position center = new Position(data.origin, -1);
            offset = new Position(data.origin, -1);

            position = new Position(data.position);

            bounds.offset(new Position(data.origin, -1));
            bounds.offset(origin);

            offset.offset(origin);
            center.offset(offset);

            nodes = map.filter(new UnknownAreaFilter(bounds, data.center));

            timediff = data.timediff;

        }

        if (nodes == null || nodes.isEmpty())
            return;

        debug("Sending map to %d: %d chunks", to, nodes.size());

        try {

            ByteArrayOutputStream buffer = new ByteArrayOutputStream(
                    getMaxMessageSize());
            ObjectOutputStream out = new ObjectOutputStream(buffer);

            out.writeByte(2);

            Vector<LocalMap.Node> list = new Vector<LocalMap.Node>(nodes);

            final Position center = position;

            Collections.sort(list, new Comparator<LocalMap.Node>() {

                @Override
                public int compare(LocalMap.Node o1, LocalMap.Node o2) {

                    int dist1 = Position.distance(center, o1.getPosition());
                    int dist2 = Position.distance(center, o2.getPosition());

                    if (dist1 > dist2)
                        return 1;
                    if (dist1 < dist2)
                        return -1;

                    return 0;
                }
            });

            for (LocalMap.Node n : list) {

                if (buffer.size() + 20 >= getMaxMessageSize())
                    break;

                LocalMap.MapChunk chunk = n.getChunk(offset, timediff);

                chunk.write(out);

                out.flush();
            }

            send(to, buffer.toByteArray());

        } catch (IOException e) {
            debug("Error sending message to %d: %s", to, e);
        }
    }

    /* check area for unknown nodes  */
    private static class UnknownAreaFilter implements LocalMap.Filter {

        private LocalMap.Bounds known;    // bounds of the area
        private Position center; // center position in the area
        private int radius;

        public UnknownAreaFilter(LocalMap.Bounds known, Position center) {
            this.known = known;
            this.center = center;
            radius = Math.min(known.getRight() - known.getLeft(), known.getBottom() - known.getTop());
        }

        @Override
        public boolean filter(LocalMap.Node n) {
            // Check if field is a flag, if not return false
            if (n.getPosition().getX() % 2 != 0 || n.getPosition().getY() % 2 != 0)
                return false;
            //
            if (!known.inside(n.getPosition()))
                return true;
            //
            if (Position.distance(center, n.getPosition()) > radius)
                return true;

            return false;
        }

    }

    private boolean detectLock() {

        history.add(new Position(position));

        if (history.size() < 20)
            return false;

        float meanX = 0;
        float meanY = 0;

        for (Position p : history) {
            meanX += p.getX();
            meanY += p.getY();
        }

        meanX /= history.size();
        meanY /= history.size();

        float varianceX = 0;
        float varianceY = 0;

        for (Position p : history) {
            varianceX += Math.pow(p.getX() - meanX, 2);
            varianceY += Math.pow(p.getY() - meanY, 2);
        }

        varianceX /= history.size();
        varianceY /= history.size();

        history.clear();

        float variability = (float) Math.sqrt(varianceX * varianceX + varianceY * varianceY);

        return variability < 2;
    }

}