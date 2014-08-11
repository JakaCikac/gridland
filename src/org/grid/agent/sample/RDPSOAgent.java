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
import org.grid.arena.SwingView;
import org.grid.protocol.Message.Direction;
import org.grid.protocol.Neighborhood;
import org.grid.protocol.Position;
import org.grid.agent.sample.ConstantsRDPSO;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

// Run: java -cp bin org.grid.agent.Agent localhost org.grid.agent.sample.RandomAgent

@Membership(team = "rdpso")
public class RDPSOAgent extends Agent {

    private LocalMap map = new LocalMap();
    private Position position = new Position(0, 0);
    private Mode mode = Mode.EXPLORE;
    private int timestep = 1;
    private Set<Position> moveable = new HashSet<Position>();
    private HashMap<Integer, MemberData> registry = new HashMap<Integer, MemberData>();
    private Position origin = null;
    private Vector<Position> history = new Vector<Position>();
    private boolean firstIteration = true;

    private int numSwarms = 3; // initial number of swarms

    /* shared variables in swarm .. */
    private int numAgents = 5; // current number of agents in each swarm
    private int numKilledAgents = 0; // initial excluded robots


    // Possible agent states
    private static enum AgentState {
        EXPLORE, SEEK, RETURN
    }


    private static class State {

        Neighborhood neighborhood;

        Direction direction;

        public State(int stamp, Neighborhood neighborhood, Direction direction) {
            super();
            this.neighborhood = neighborhood;
            this.direction = direction;
        }

    }

    // define agent modes
    private static enum Mode {
        EXPLORE, SEEK, SURVEIL, RETURN, CLEAR
    }

    // define what the agent is looking for
    private static LocalMap.Filter goalFilter = new LocalMap.Filter() {
        @Override
        public boolean filter(LocalMap.Node n) {
            /* EMPTY = 0; WALL = 1; HEADQUARTERS = 2;
            OTHER_HEADQUARTERS = 4;  UNKNOWN = 6; */
            return n.getBody() == 2;
            // apparently works best in this example, if set to 2
        }
    };

    // RDPSO variables

    private int swarmID = 1;            // which swarm does the agent belong to
    // swarmID = 0 = agent belongs to the socially excluded group
    private static int swarm_counter = 1;

    /* memorized parameters of each agent */
    private double local = 0;  // comm stuff
    private double global = 0; // comm stuff

    private double vx = 0;
    private double vx_t1 = 0;
    private double vx_t2 = 0;
    private double vx_t3 = 0;
    private double vy = 0;
    private double vy_t1 = 0;
    private double vy_t2 = 0;
    private double vy_t3 = 0;

    private int num_kill = 0;             // number of killed agents in swarm
    private double SC = 0;                // stagnancy counter
    private boolean callAgent = false;    // need of calling a agent
    private boolean createSwarm = false;  // need of creating a swarm


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


    private JFrame window;
    private LocalMap.LocalMapArena arena;
    private SwingView view;

    @Override
    public void initialize() {

        // 6 = scope of the arena
        arena = map.getArena(6);

        if (System.getProperty("rdpso") != null) {
            view = new SwingView(24);

            view.setBasePallette(new SwingView.HeatPalette(32));
            window = new JFrame("Agent " + getId());
            window.setContentPane(view);
            window.setSize(view.getPreferredSize(arena));
            window.setVisible(true);
        }
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
            int swarmID             - agent's swarmID
            double local            - agent's local best solution
            double global           - swarm's global best solution
            double xcognitive       - agent's best cognitive position x
            double ycognitive       - agent's best cognitive position y
            double xgbest           - agent's best position x
            double ygbest           - agent's best position y
            double xobs             - agent's best obstacle avoidance x
            double yobs             - agent's best obstacle avoidance y
            double vx{ ,t1,t2,t3}, vy{, t1, t2, t3} - agent's previous and current speeds
            int num_kill            - number of killed agents in the swarm
            double SC               - search counter for the swarm group
            boolean callagent       - is a new agent required
            boolean createswarm     - should the swarm create a subswarm
            double mainBestFunction - swarm's best objective solution
            double gbestValue       - globally best solution
            double obsBestFunction  - best obstacle avoidance function

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
            out.writeDouble(local);
            out.writeDouble(global);

            out.writeInt(num_kill);
            out.writeDouble(SC);
            out.writeBoolean(callAgent);
            out.writeBoolean(createSwarm);

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
            double tempLocal;
            double tempGlobal;
            double tempXcognitive;
            double tempYcognitive;
            double tempxGbest;
            double tempyGbest;
            double tempXobs;
            double tempYobs;
            int preTime;

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

                    swarmID = in.readInt();
                    tempLocal = in.readDouble();
                    tempGlobal = in.readDouble();

                    num_kill = in.readInt();
                    SC = in.readDouble();
                    callAgent = in.readBoolean();
                    createSwarm = in.readBoolean();



                    preTime = in.readInt();
                    int timediff = preTime - timestep;

                    synchronized (registry) {
                        if (registry.containsKey(from)) {
                            MemberData data = registry.get(from);
                            data.bounds = bounds;
                            data.origin = origin;
                            data.center = center;
                            data.info = timestep;
                            data.timediff = timediff;
                            debug("New info: %s %s %s %s", data.bounds, data.origin, data.center, data.info);
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

        firstIteration = true;

    }

    public void initializeRDPSO() {
        // initialize cognitive, social and obstacle as agent's own position
        swarmID = assignToSwarm();
        callAgent = false;
        createSwarm = false;

        // The array index indicates which swarmID it belongs to.
        SC = 0;
        numAgents = ConstantsRDPSO.INIT_AGENTS;
        numKilledAgents = 0;

        swarmSolutionArray = new double[ConstantsRDPSO.MAX_AGENTS];


        local = 0;
        global = 0;

        vx = 0;
        vx_t1 = 0;
        vx_t2 = 0;
        vx_t3 = 0;
        vy = 0;
        vy_t1 = 0;
        vy_t2 = 0;
        vy_t3 = 0;

        // This is the same for all agents.
        constantArray[0] = ConstantsRDPSO.C1;
        constantArray[1] = ConstantsRDPSO.C2;
        constantArray[2] = ConstantsRDPSO.C3;

    }

    /*
        assign agents to different (incremental) swarm groups inside the main swarm (1 to MAX_SWARMS-1), 0 is social exclusion
     */
    private int assignToSwarm() {
        if (getSwarmCounter() == ConstantsRDPSO.MAX_SWARMS - 1)
            setSwarmCounter(0);
        incrementSwarmCounter();
        return getSwarmCounter();
    }

    /**
     * RDPSO VARIABLES *
     */
    int agentPositionX = 0; // x_n(t)
    int agentPositionY = 0;

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

    boolean replanAgents = true;

    double swarmSolutionArray[];

    // cleanMove tells the agent to only move
    boolean cleanMove = false;
    // how many moves left on x and y
    int moveXleft = 0;
    int moveYleft = 0;

    // agentPositionX,Y = current agent position
    // agentSolution = agent's current solution
    // agentBestSolution = agent's best solution so far


    @Override
    public void run() {

        int sleeptime = 100;

        scan(0);

        while (isAlive()) {

            scan(0);

            // check for new state data
            State state = buffer.poll();
            // check if state is null, if null, you must call scan(0)
            if (state != null) {

                // todo: where do I update this?
                agentPositionX = position.getX();
                agentPositionY = position.getY();

                // todo: why null pointer exception? .. plan? .. -.-

                if (state.direction == Direction.NONE && state.direction != null) {

                    if (firstIteration) {
                        // initialize RDPSO variables, position and swarmID
                        initializeRDPSO();
                        // call scan, to get new state information
                        scan(0);
                        // set first itertion to false, continue the algorithm
                        firstIteration = false;
                    } // after the first iteration start the algorithm
                    else {

                        // confirm that the agent is not a member of the excluded group (swarmID = 0)
                        if (!cleanMove) {
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

                                // Send information to other agents
                                Set<Position> movable = analyzeNeighborhood(state.neighborhood);

                                // update agent's local map
                                boolean replanMap = map.update(state.neighborhood, position, timestep);
                                registerMoveable(movable, state.neighborhood);
                                // todo: sharing info with agents in subswarm, maybe timeouts
                                replanAgents = blockMoveable(movable, state.neighborhood);

                                // update information
                                while (!inbox.isEmpty()) {
                                    Message m = inbox.poll();
                                    // receive and parse message from other agents, filter data from agent's swarm
                                    replanMap &= parse(m.from, m.message, state.neighborhood);
                                }

                                // update arena
                                if (view != null)
                                    view.update(arena);

                                // add agentSolution to vector H(t) that includes solutions of all agents within the swarmID group
                                // todo: synced version of this concatenating the solutions of subswarms
                                addToSwarmSolutionArray(ConstantsRDPSO.MAX_SWARMS, ConstantsRDPSO.MAX_AGENTS, swarmID, agentBestSolution);
                                // wait till all agents put solutions in solution array...

                                // find best solution in vector H(t) = max(H(t))
                                double maxSwarmSolution = findSwarmSolutionMax(swarmID);
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
                                        // todo: punishing counter.. verjetno moras za vsako skupino posebi belezit kolk agentov je bilo punished / rewarded.
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
                                            if (agentBestSolution == findSwarmSolutionMin(swarmID)) {
                                                // exclude agent
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
                                obstacleSolution = evaluateObstacleFunction();
                                if (obstacleSolution >= obstacleBestSolution) {
                                    // check if obstacleSolution is better than currently best solution
                                    obstacleBestSolution = obstacleSolution;
                                    // update obstacle component with agent's current solution
                                    solutionArray[2] = agentPositionX;
                                    solutionArray[5] = agentPositionY;
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
                                //System.out.println("New agent position X: " + tempAgentPositionX);
                                int roundedX = (int) Math.round(tempAgentPositionX);
                                System.out.println("New rounded position X: " + roundedX);

                                double tempAgentPositionY = agentPositionY + agentVelocityY;
                                // System.out.println("New agent position Y: " + tempAgentPositionY);
                                int roundedY = (int) Math.round(tempAgentPositionY);
                                System.out.println("New rounded position Y: " + roundedY);

                                cleanMove(roundedX,roundedY);
                                cleanMove = true;
                                // todo: don't forget to update the agent position (int, int)!

                                // move agent to new position
                                //todo: move agent to new calculated position
                                //Decision d = updateDecisions(neighborhood, state);
                                // if (d.getDirection() != Direction.NONE)
                                //     move(d.getDirection());

                            } else { // if agent is in the EXCLUDED MEMBERS GROUP

                                // Send information to other agents
                                Set<Position> movable = analyzeNeighborhood(state.neighborhood);
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
                                // evaluate solution
                                // check if agent improved
                                // send group info
                                // build vector H
                                // check if group improved


                            }
                        } else {
                            // check if agent is in clean move mode (doesn't do anything else but move)
                            if (cleanMove) {

                                // update agent's local map
                                boolean replanMap = map.update(state.neighborhood, position, timestep);
                                if (replanMap) {
                                    plan.clear();
                                }

                                if (!plan.isEmpty()) {

                                    Direction d = plan.poll();
                                    // debug("Next move: %s", d);

                                    timestep++;

                                    if (d != Direction.NONE) {
                                        move(d);
                                    }

                                    if (d == Direction.LEFT)
                                        position.setX(position.getX() - 1);
                                    if (d == Direction.RIGHT)
                                        position.setX(position.getX() + 1);
                                    if (d == Direction.UP)
                                        position.setY(position.getY() - 1);
                                    if (d == Direction.DOWN)
                                        position.setY(position.getY() + 1);

                                    arena.setOrigin(position.getX(), position.getY());

                                    // todo: update agent position according to the move
                                    //System.out.println("new agent position X: " + position.getX());
                                    //agentPositionX = position.getX();
                                    //System.out.println("new agent position Y: " + position.getY());
                                    //agentPositionY = position.getY();
                                    scan(0);

                                } else {
                                    cleanMove = false;
                                }
                            }
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

    private void cleanMove(int moveXleft, int moveYleft) {
        List<Direction> directions = null;
        System.out.println("To move: " + moveXleft + ", " + moveYleft);

        LocalMap.Paths paths = map.findShortestPaths(position);

        Position p = new Position(position.getX() + moveXleft, position.getY() + moveYleft);

        LocalMap.Node n = map.get(p.getX(), p.getY());

        directions = paths.shortestPathTo(n);

        plan.addAll(directions);
    }

    private double evaluateObjectiveFunction(double agentPositionX, double agentPositionY) {
        // todo: something like alpha*uncovered_cells + beta*time_needed
        return 6.0;
    }

    private double evaluateObstacleFunction() {
        // todo: evklidova razdalja do ovir?
        return 3.0;
    }

    /* check probability of a group spawning a new subgroup */
    private boolean spawnGroupProbability() {
        if ((Math.random() * (numAgents / ConstantsRDPSO.MAX_AGENTS)) > Math.random()) {
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

    private void changeMode(Mode mode) {

        if (this.mode != mode) {
            debug("Switching from %s to %s", this.mode, mode);
        }

        this.mode = mode;
    }

    private boolean blockMoveable(Set<Position> moveable, Neighborhood n) {

        boolean replan = false;

        int x = position.getX();
        int y = position.getY();

        map.clearModifiers();

        for (Position p : moveable) {

            int i = p.getX() - x;
            int j = p.getY() - y;


            if (n.getCell(i, j) > 0 || n.getCell(i, j) == Neighborhood.OTHER) {

                int size = 3;

                if (n.getCell(i, j) == Neighborhood.OTHER)
                    size = 5;

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
                    System.out.println("member id: " + id);
                    member.setPosition(p);
                    System.out.println("member position: " + p.getX() + ", " + p.getY());
                    member.notified = -30;
                    registry.put(id, member);
                }

                MemberData data = registry.get(id);

                // send info after some time
                if (Math.abs(timestep - data.notified) > 20) {
                    System.out.println("sending info");
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

}