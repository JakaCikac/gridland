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

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

// Run: java -cp bin org.grid.agent.Agent localhost org.grid.agent.sample.RandomAgent

@Membership(team="rdpso")
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


    protected static class ConstantsRDPSO {
        /* number of swarms */
        private static final int NUM_SWARMS = 3; // initial number of swarms
        private static final int MAX_SWARMS = 4; // maximum number of swarms
        private static final int MIN_SWARMS = 0; // minimum number of swarms (0, to allow social exclusion of all agents)

        /* number of agents in each swarm */
        private static final int INIT_AGENTS = 5; // initial number of agents in each swarm
        private static final int MAX_AGENTS = 15; // maximum number of agents in each swarm
        private static final int MIN_AGENTS = 0;  // minimum number of agetns in each swarm

        /* RDPSO coefficients */
        private static final double W = 0.6;   // inertial coefficient (fract)
        private static final double C1 = 0.8;  // cognitive weight (pc)
        private static final double C2 = 0.04; // social weight (ps)
        private static final double C3 = 0.2;  // obstacle suspectibility weight (pobs)
        private static final double C4 = 0.4;  // communication constraint weight (pcomm)

        private static final int SC_MAX  = 15;     // maximum number of iterations without improving the swarm
        private static final int COMM_RANGE = 200; // maximum communication range
    }
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
    private double  local = 0;  // comm stuff
    private double  global = 0; // comm stuff

    private double vx = 0;
    private double vx_t1 = 0;
    private double vx_t2 = 0;
    private double vx_t3 = 0;
    private double vy = 0;
    private double vy_t1 = 0;
    private double vy_t2 = 0;
    private double vy_t3 = 0;

    private int num_kill = 0;             // number of killed agents in swarm
    private double SC = 0;                // search counter
    private boolean callAgent = false;    // need of calling a agent
    private boolean createSwarm = false;  // need of creating a swarm

    /* swarm objectives */
    private double mainBestFunction = 0;  // main objective function
    private double gbestValue = 0;        // global best value, init to 0
    private double obsBestFunction = 0;

    /* initialize best cognitive, global and obstacle position as agent's own position */
    private double xcognitive = 0; // = agents x
    private double ycognitive = 0; // = agents y
    private double xgbest = 0;     // = agents x
    private double ygbest = 0;     // = agents y
    private double xobs = 0;       // = agents x
    private double yobs = 0;       // = agetns y

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

    private double evaluateObjectiveFunction(double agentPosition) {
        return 0.0;
    }

    /**
     * Send information to agent in swarm.
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
int bakd = ConstantsRDPSO.NUM_SWARMS;
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
            out.writeDouble(xcognitive);
            out.writeDouble(ycognitive);
            out.writeDouble(xgbest);
            out.writeDouble(ygbest);
            out.writeDouble(xobs);
            out.writeDouble(yobs);
            out.writeDouble(vx);
            out.writeDouble(vx_t1);
            out.writeDouble(vx_t2);
            out.writeDouble(vx_t3);
            out.writeDouble(vy);
            out.writeDouble(vy_t1);
            out.writeDouble(vy_t2);
            out.writeDouble(vy_t3);
            out.writeInt(num_kill);
            out.writeDouble(SC);
            out.writeBoolean(callAgent);
            out.writeBoolean(createSwarm);
            out.writeDouble(mainBestFunction);
            out.writeDouble(gbestValue);
            out.writeDouble(obsBestFunction);
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
                    tempXcognitive = in.readDouble();
                    tempYcognitive = in.readDouble();
                    tempxGbest = in.readDouble();
                    tempyGbest = in.readDouble();
                    tempXobs = in.readDouble();
                    tempYobs = in.readDouble();
                    vx = in.readDouble();
                    vx_t1 = in.readDouble();
                    vx_t2 = in.readDouble();
                    vx_t3 = in.readDouble();
                    vy = in.readDouble();
                    vy_t1 = in.readDouble();
                    vy_t2 = in.readDouble();
                    vy_t3 = in.readDouble();
                    num_kill = in.readInt();
                    SC = in.readDouble();
                    callAgent = in.readBoolean();
                    createSwarm = in.readBoolean();
                    mainBestFunction = in.readDouble();
                    gbestValue = in.readDouble();
                    obsBestFunction = in.readDouble();

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
        xcognitive = position.getX();
        ycognitive = position.getY();
        xobs = position.getX();
        yobs = position.getY();
        xgbest = position.getX();
        ygbest = position.getY();

        // initialize objective functions to zero
        mainBestFunction = 0;
        gbestValue = 0;
        obsBestFunction = ConstantsRDPSO.COMM_RANGE;
            swarmID = assignToSwarm();
        System.out.println("Swarm ID: " + swarmID);
        callAgent = false;
        createSwarm = false;

        SC = 0;
        num_kill = 0;

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

    }

    /*
        assign agents to different (incremental) swarm groups inside the main swarm (1 to MAX_SWARMS)
     */
    private int assignToSwarm() {
        if (getSwarmCounter() == ConstantsRDPSO.MAX_SWARMS)
            setSwarmCounter(0);
        incrementSwarmCounter();
        return getSwarmCounter();
    }

    /** TEMP VARIABLES TO FOLLOW PSEUDO **/
    double agentPosition = 0; // x_n(t)
    double agentSolution = 0; // h(x_n(t))
    double agentBestSolution = 0; // h_best
    double solutionArray[] = new double[3]; // X_1(t) = cognitive, X_2(t) = social, X_3(t) = obstacle

	@Override
	public void run() {

        int sleeptime = 100;

        scan(0);

		while (isAlive()) {
            // check for new state data
            State state = buffer.poll();
            // check if state is null, if null, you must call scan(0)
            if (state != null) {

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
                        if (swarmID != 0) {
                            // evaluate agent's current solution = h(x_n(t))
                            agentSolution = evaluateObjectiveFunction(agentPosition);
                            // check if agent improved and update agent's best solution
                            if (agentSolution > agentBestSolution) {
                                agentBestSolution = agentSolution;
                                // update best cognitive solution
                                solutionArray[0] = agentPosition;
                            }
                        }

                        // Send information to other agents
                        Set<Position> movable = analyzeNeighborhood(state.neighborhood);
                        // update agent's local map
                        boolean replanMap = map.update(state.neighborhood, position, timestep);
                        registerMoveable(movable, state.neighborhood);

                        boolean replanAgents = blockMoveable(movable, state.neighborhood);

                        while (!inbox.isEmpty()) {
                            Message m = inbox.poll();
                            // receive and parse message from other agents, filter data from agent's swarm
                            replanMap &= parse(m.from, m.message, state.neighborhood);
                        }
                        // update arena
                        if (view != null)
                            view.update(arena);

                        // if replan required
                        if (replanMap || replanAgents) {
                            plan.clear();
                        }

                        // build vector H(t) that includes solutions of all agents within the swarmID group
                        //double swarmSolutionArray[] = buildSwarmSolutionArray(swarmID);
                        // find best solution in vector H(t) = max(H(t))
                        //double maxSwarmSolution = findMax(swarmSolutionArray);
                        // check if subgroup improved
                      /*  if (maxSwarmSolution > bestSwarmSolution) {
                            bestSwarmSolution = maxSwarmSolution;
                            // update social component
                            solutionArray[1] = agentPosition;
                            // decrease stagnancy counter
                            if (SC > 0)
                                SC = SC - 1;
                            // check if group can be rewarded
                            if (SC == 0) {
                                   ///blablalba
                            }
                        } */



                        if (plan.isEmpty()) {

                            List<Direction> directions = null;

                            LocalMap.Paths paths = map.findShortestPaths(position);

                            while (directions == null) {

                                switch (mode) {
                                    case EXPLORE: {

                                        if (stohastic(0.9)) {
                                            List<LocalMap.Node> candidates = map.filter(goalFilter);

                                            directions = paths.shortestPathTo(candidates);

                                            if (directions != null) {
                                                changeMode(Mode.SEEK);
                                                break;
                                            }
                                        }

                                        List<LocalMap.Node> candidates = map.filter(LocalMap.BORDER_FILTER);

                                        Collections.shuffle(candidates);

                                        directions = paths.shortestPathTo(candidates);

                                        if (directions == null) {
                                            if (!replanAgents) {
                                                changeMode(Mode.SURVEIL);
                                                continue;
                                            }
                                        }
                                        break;
                                    }
                                    case SURVEIL: {

                                        if (stohastic(0.9)) {
                                            List<LocalMap.Node> candidates = map.filter(goalFilter);

                                            directions = paths.shortestPathTo(candidates);

                                            if (directions != null) {
                                                changeMode(Mode.SEEK);
                                                break;
                                            }
                                        }

                                        List<LocalMap.Node> candidates = map.getOldestNodes(10);

                                        directions = paths.shortestPathTo(candidates);

                                        break;
                                    }
                                    case SEEK: {

                                        List<LocalMap.Node> candidates = map.filter(goalFilter);

                                        directions = paths.shortestPathTo(candidates);

                                        if (directions == null) {
                                            changeMode(Mode.EXPLORE);
                                            continue;
                                        }

                                        break;
                                    }
                                    // return to headquarters
                                    case RETURN: {
                                        // get hq position, get node and then shortest path
                                        Position p = origin;
                                        LocalMap.Node n = map.get(p.getX(), p.getY());
                                        directions = paths.shortestPathTo(n);
                                        break;
                                    }

                                    case CLEAR: {

                                        List<LocalMap.Node> candidates = map.filter(new DistanceFilter(position,
                                                state.neighborhood.getSize() - 1,
                                                state.neighborhood.getSize() + 1));

                                        directions = paths.shortestPathTo(candidates);

                                        changeMode(Mode.EXPLORE);

                                        break;
                                    }
                                    default:
                                        break;
                                }

                                // cannot move anywhere ...
                                if (directions == null) {
                                    // otherwise just hold still for a timestep
                                    directions = new Vector<Direction>();
                                    for (int i = 0; i < 5; i++)
                                        directions.add(Direction.NONE);
                                }
                            }

                            plan.addAll(directions);

                        }

                        // if plan is not empty, pick up the next move
                        if (!plan.isEmpty()) {

                            Direction d = plan.poll();
                            //debug("Next move: %s", d);

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

                            if (detectLock() && (mode == Mode.EXPLORE || mode == Mode.SURVEIL)) {
                                changeMode(Mode.CLEAR);
                            }

                            scan(0);

                        }

                    }
                    }    else
                    scan(0);

                    }
                }
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

    private boolean stohastic(double probability) {
        return Math.random() < probability;
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
                    //sendMap(id);
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