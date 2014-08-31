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
    private Decision left, right, up, down, still;
    private Decision[] decisions;
    private Set<LocalMap.Node> agentHistory = new HashSet<LocalMap.Node>();

    private static class State {

        Neighborhood neighborhood;

        Direction direction;

        int stamp;

        public State(int stamp, Neighborhood neighborhood, Direction direction) {
            super();
            this.stamp = stamp;
            this.neighborhood = neighborhood;
            this.direction = direction;
        }
    }

    protected static class Decision {

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

    /* ########################
       RDPSO variables
    ###########################  */

    private int swarmID = -1;            // which swarm does the agent belong to
    // swarmID = 0 = agent belongs to the socially excluded group

    private double SC = 0;                // stagnancy counter
    private boolean callAgent = false;    // need of calling a agent
    private boolean createSwarm = false;  // need of creating a swarm

    private int numSwarms = ConstantsRDPSO.INIT_SWARMS; // initial number of swarms

    /* shared variables in swarm .. */
    private int numAgents = ConstantsRDPSO.INIT_AGENTS; // current number of agents in each swarm

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

    ArrayList<AgentSolution> swarmSolutionArray = new ArrayList<AgentSolution>();
    ArrayList<Set<Integer>> subswarmingArray = new ArrayList<Set<Integer>>();

    // cleanMove tells the agent to only move
    boolean cleanMove = false;
    Position goalPosition = null;
    // agentPositionX,Y = current agent position
    // agentSolution = agent's current solution
    // agentBestSolution = agent's best solution so far

    boolean positionNotInMap = false;
    boolean knownLocalMap = false;

    int requestingSwarmID = 0;
    int subgroupRequestedByID = 0;
    //int newSwarmID = numSwarms;

    // objective function
    int previousT = 0;
    int counter = 0;
    int previousNodeCount = 0;
    int noNewNodesCounter = 0;
    double previousResult = 0.0;
    int numOfImprovements = 0;
    int previousNodeDiff1 = 1;
    int previousNodeDiff2 = 1;
    int previousNodeDiff3 = 1;
    int previousNodeDiff4 = 1;

    // req/res/ack flags
    boolean pendingAgentRequest = false;
    boolean pendingSubgroupRequest = false;
    boolean ackAgentRequest = false;
    boolean ackSubgroupRequest = false;
    boolean newGroupCreation = false;
    boolean requestSnapshot = false;

    // snapshot variables
    ArrayList<AgentSolution> snapSolutionArray = new ArrayList<AgentSolution>();
    double snapBestSwarmSolution = 0.0;
    double snapSC = 0;
    int snapNumAgents = 0;
    int snapKilledAgents = 0;

    boolean initRoutine = true;

    // guidance
    int b = 5;
    double diff = 0.0;

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

        left = new Decision(Direction.LEFT);
        right = new Decision(Direction.RIGHT);
        up = new Decision(Direction.UP);
        down = new Decision(Direction.DOWN);
        still = new Decision(Direction.NONE);

        decisions = new Decision[]{
                left, right, up, down
        };

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
     * @param to the receiving agent
     */
    private void sendInfo(int to, int dataType) {

        /*

            Required info in a message:
            -------------------------------------
            int swarmID

            int agentPositionX
            int agentPositionY
            double agentBestSolution
            double solutionArray
            (includes local,global,obstacle best (x and y for each)
            double cogx
            double cogy
            double socx
            double socy
            double obsx
            double obsy
            double bestSwarmSolution
            double obstacleBestSolution
            double SC
            boolean callAgent
            boolean createSwarm
            int numAgents
            int numKilledAgents
            int requestingSwarmID
            int newSwarmID
         */

       // System.out.print(getId() + ": Sending out info. ");

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(getMaxMessageSize());
            ObjectOutputStream out = new ObjectOutputStream(buffer);

            //System.out.println(getId() + " Sending out dataType: " + dataType);
            switch (dataType) {
                case 1: // info
                {
                    out.writeByte(1); // 1 = info, 2 = map
                    out.writeByte(1); // 1 = basic info

                    //System.out.println("Message type: info");

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

                    out.writeObject(subswarmingArray);
                    //System.out.println(getId() + " Sending out subswarming array: ");
                    //Subswarming.toString(subswarmingArray);

                    out.writeInt(swarmID);

                    out.writeObject(swarmSolutionArray);

                    out.writeDouble(bestSwarmSolution);
                    out.writeDouble(SC);
                    out.writeInt(numAgents);
                    out.writeInt(numKilledAgents);
                    out.writeInt(numSwarms);

                    out.writeInt(timestep);

                    break;
                }
                case 2: // agent request
                {
                    out.writeByte(1); // 1 = info, 2 = map
                    out.writeByte(2); // 2 = new agent request

                    //System.out.println(getId() + " Message type: agent req");

                    out.writeBoolean(callAgent);
                    out.writeInt(numAgents);
                    out.writeInt(requestingSwarmID);

                    out.writeObject(swarmSolutionArray);

                    out.writeDouble(bestSwarmSolution);
                    out.writeDouble(SC);
                    out.writeInt(numAgents);
                    out.writeInt(numKilledAgents);
                    out.writeInt(numSwarms);

                    out.writeInt(timestep);

                    break;
                }

                case 6: // agent response
                {
                    out.writeByte(1); // 1 = info, 2 = map
                    out.writeByte(6); // 6 = new agent response

                    //System.out.println(getId() + " Message type: agent res");

                    out.writeInt(getId());
                    out.writeInt(swarmID);
                    out.writeBoolean(callAgent);
                    out.writeInt(numAgents);
                    out.writeBoolean(ackAgentRequest);

                    out.writeObject(subswarmingArray);

                    break;
                }
                case 3: // subgroup request
                {
                    out.writeByte(1); // 1 = info, 2 = map
                    out.writeByte(3); // 3 = new subgroup request

                    //System.out.println(getId() + "Message type: sub req");

                    out.writeInt(swarmID); // will be 0 anyway
                    // so a response can be sent to them
                    out.writeInt(subgroupRequestedByID);
                    out.writeBoolean(createSwarm);
                    out.writeInt(numSwarms);
                    out.writeInt(timestep);

                    break;
                }
                case 7: // subgroup response
                {
                    out.writeByte(1); // 1 = info, 2 = map
                    out.writeByte(7); // 3 = subgroup response

                    //System.out.println(getId() + " Message type: sub res");

                    out.writeInt(swarmID); // is in fact a requestedByID
                    out.writeBoolean(ackSubgroupRequest);
                    out.writeBoolean(createSwarm);

                    break;

                }
                case 4: // the need of Ni-1 agents to form subgroup
                {
                    out.writeByte(1); // 1 = info, 2 = map
                    out.writeByte(4); // 4 = need of Ni-1 agents to form subgroup

                    //System.out.println(getId() + " Message type: need ni-1");

                    // probably need to count how many agents from swarmID=0 already responded..

                    out.writeInt(swarmID);

                    out.writeBoolean(callAgent);
                    out.writeBoolean(createSwarm);
                    out.writeInt(numAgents);
                    out.writeInt(numKilledAgents);
                    out.writeInt(requestingSwarmID);
                    //out.writeInt(newSwarmID);
                    out.writeInt(numSwarms);

                    out.writeInt(timestep);

                    break;
                }
                case 5: // broadcast group deletion
                {
                    out.writeByte(1); // 1 = info, 2 = map
                    out.writeByte(5); // 5 = group deletion

                    //System.out.println(getId() + " Message type: group deletion");

                    out.writeInt(getId());
                    out.writeInt(swarmID);
                    out.writeObject(subswarmingArray);

                    out.writeInt(timestep);

                    break;
                }
                case 8: // exclusion request
                    out.writeByte(1);
                    out.writeByte(8);

                    //System.out.println(getId() + " Sending out exclusion request.");
                    out.writeInt(getId());
                    out.writeInt(swarmID);

                    out.writeObject(subswarmingArray);

                    break;
            }

            out.flush();
            send(to, buffer.toByteArray());

        } catch (IOException e) {
            debug("Error sending message to %d: %s", to, e);
        }

    }

    private boolean parse(int from, byte[] message, Neighborhood neighborhood) {

        try {
            ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(message));

            // message classification
            int type = in.readByte();
            int dataType = in.readByte();

            // time variables
            int preTime;

            // swarm variables
            int recSwarmID;
            double tempBestSwarmSolution;
            double tempSC;
            boolean tempCallAgent;
            boolean tempCreateSwarm;
            int tempNumAgents;
            int tempNumKilledAgents;
            int tempRequestingSwarmID;
            int tempNumSwarms;

            //System.out.print(getId() + ": received message. ");

            switch (type) {
                case 1: { // info message

                    switch (dataType) {
                        case 1: {

                            //System.out.print("Message type: info");
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

                            ArrayList<Set<Integer>> tempsub = (ArrayList<Set<Integer>>)in.readObject();
                            //System.out.println(getId() + " Received subswarming array: ");
                            //Subswarming.toString(tempsub);
                            if (initRoutine)
                                subswarmingArray = Subswarming.unityMergeSubswarmingArrays(subswarmingArray, tempsub);

                            // RDPSO variables
                            recSwarmID = in.readInt();

                            ArrayList<AgentSolution> tempSolutionArray = (ArrayList<AgentSolution>)in.readObject();// new ArrayList<AgentSolution>();

                            tempBestSwarmSolution = in.readDouble();
                            tempSC = in.readDouble();
                            tempNumAgents = in.readInt();
                            tempNumKilledAgents = in.readInt();

                            if (recSwarmID == swarmID) {

                                if (swarmID == 0 && requestSnapshot) {
                                    requestSnapshot = false;
                                    //System.out.println(getId() + " received 0 snapshot!");
                                }
                                // update stuff
                                swarmSolutionArray = new ArrayList<AgentSolution>(tempSolutionArray);
                                bestSwarmSolution = tempBestSwarmSolution;
                                SC = tempSC;
                                // only update this, if changed info has been communicated
                                //if (!dirtyData) {
                                numAgents = tempNumAgents;
                                numKilledAgents = tempNumKilledAgents;
                                //}
                            }

                            tempNumSwarms = in.readInt();

                            // if (!dirtyData) {
                            numSwarms = tempNumSwarms;
                            // }

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
                                    //debug("New info: %s %s %s %s", data.bounds, data.origin, data.center, data.info);
                                }
                            }
                            break; // break dataType case 1 = basic info
                        }
                        case 2: {

                            //System.out.print("Message type: agent req");

                            tempCallAgent = in.readBoolean();
                            tempNumAgents = in.readInt();
                            tempRequestingSwarmID = in.readInt();
                            ArrayList<AgentSolution> tempSolutionArray = (ArrayList<AgentSolution>)in.readObject();

                            tempBestSwarmSolution = in.readDouble();
                            tempSC = in.readDouble();
                            tempNumAgents = in.readInt();
                            tempNumKilledAgents = in.readInt();
                            tempNumSwarms = in.readInt();
                            timestep = in.readInt();

                            // if an excluded agent receives a request to join a swarm, update variables
                            if (swarmID == 0) {
                                // do I need to join a new swarm?
                                callAgent = tempCallAgent;
                                // update num agents to requesting swarmID info
                                numAgents = tempNumAgents;
                                // which swarm do I join?
                                requestingSwarmID = tempRequestingSwarmID;

                                snapSolutionArray = new ArrayList<AgentSolution>(tempSolutionArray);
                                snapBestSwarmSolution = tempBestSwarmSolution;
                                snapSC = tempSC;
                                snapNumAgents = tempNumAgents;
                                snapKilledAgents = tempNumKilledAgents;
                                numSwarms = tempNumSwarms;

                            }

                            break; // break dataType case 2 = agent request
                        }
                        case 3: {

                            //System.out.print("Message type: sub req");

                            recSwarmID = in.readInt();
                            tempCreateSwarm = in.readBoolean();
                            numSwarms = in.readInt();
                            timestep = in.readInt();

                            // only a member of excluded group can receive subgroup request
                            if (swarmID == 0) {
                                createSwarm = tempCreateSwarm;
                                requestingSwarmID = recSwarmID;
                            }

                            break;
                        }
                        case 4: {

                            recSwarmID = in.readInt();
                            break;
                        }
                        case 5: {

                            //System.out.print("Message type: num swarms");

                            int recId = in.readInt();
                            int recSwarmId = in.readInt();
                            ArrayList<Set<Integer>> tempSub = (ArrayList<Set<Integer>>)in.readObject();
                            timestep = in.readInt();

                            // update subswarming array with agent exclusion and group deletion
                            // check assumption that agent is in 0, removed from recSwarmId and recSwarmId has [-1]
                            boolean assumption = Subswarming.confirmGroupDeletionAssumption(tempSub, recId, recSwarmId);
                            if (assumption) {
                                subswarmingArray = Subswarming.removeAgentFromSubswarming(subswarmingArray, recId, recSwarmId);
                                subswarmingArray = Subswarming.addAgentToSubswarming(subswarmingArray, recId, 0);
                                subswarmingArray = Subswarming.deleteSubgroupFromSubswarmingArray(subswarmingArray, recSwarmId);
                                //System.out.println(getId() + " Agent deleted subgroup, my updated array: ");
                                //Subswarming.toString(subswarmingArray);
                            }

                            break;
                        }
                        case 6: { // new agent response ack

                            //System.out.print(getId() + " Message type: agent res");

                            int recId = in.readInt();
                            recSwarmID = in.readInt();
                            tempCallAgent = in.readBoolean();
                            tempNumAgents = in.readInt();
                            boolean tempAckAgentRequest = in.readBoolean();

                            ArrayList<Set<Integer>> tempSub = (ArrayList<Set<Integer>>)in.readObject();
                            if (recSwarmID == swarmID) {
                                callAgent = tempCallAgent;
                                // update to the new agent number in swarm
                                numAgents = tempNumAgents;
                                ackAgentRequest = tempAckAgentRequest;
                                // allow the agent to ask for new agents
                                if (ackAgentRequest) {
                                    pendingAgentRequest = false;
                                }
                            }

                            // update subswarming array with:
                            // check assumption (agent not in 0 anymore and joined my swarm)
                            boolean assumption = Subswarming.confirmJoinAssumption(tempSub, recId, swarmID);
                            if (assumption) {
                                subswarmingArray = Subswarming.removeAgentFromSubswarming(subswarmingArray, recId, 0);
                                subswarmingArray = Subswarming.addAgentToSubswarming(subswarmingArray, recId, swarmID);
                                //System.out.println(getId() + " Agent joined group, my updated array: ");
                                //Subswarming.toString(subswarmingArray);
                            }

                            break;
                        }
                        case 7: { // new subgroup response ack

                            //System.out.print("Message type: sub res");

                            int tempSubgroupRequestedByID = in.readInt();
                            boolean tempAckSubgroupResponse = in.readBoolean();

                            if (tempSubgroupRequestedByID == swarmID && tempAckSubgroupResponse) {
                                pendingSubgroupRequest = false;
                            }
                            tempCreateSwarm = in.readBoolean();
                            if (tempSubgroupRequestedByID == swarmID) {
                                createSwarm = false;
                            }
                            break;
                        }
                        case 8:
                        {
                            int agentId = in.readInt();
                            int swarmId = in.readInt();
                            //System.out.println(getId() + " Received exclusion request.");

                            ArrayList<Set<Integer>> receivedSub = (ArrayList<Set<Integer>>)in.readObject();

                            boolean assumption = Subswarming.confirmExclusionAssumption(receivedSub, agentId, swarmId);
                            if (assumption) {
                                subswarmingArray = Subswarming.excludeOtherAgentsFromSubswarming(subswarmingArray, agentId, swarmId);
                                //System.out.println(getId() + " Updated my subswarming array: ");
                                //Subswarming.toString(subswarmingArray);
                            }

                            break;
                        }

                    } // end second switch

                    break; // break case 1 = info message
                }

                case 2: { // map message

                    //System.out.print("Message type: map.");

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

                    //debug("Got %d map chunks from %d, new data %b", chunks, from, replan);

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
     * @param format what format to print out the objects in
     * @param objects a list of objects, that get printed out
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

    // subswarming variables
    int modifyTimestamp = 0;
    int numMods = 0;

    public void initializeRDPSO() {
        // initialize cognitive, social and obstacle as agent's own position
        swarmID = assignToSwarm();
        //System.out.println(getId() + ": Swarm ID: " + swarmID);
        callAgent = false;
        createSwarm = false;

        // The array index indicates which swarmID it belongs to.
        SC = 0;
        numKilledAgents = 0;

        // This is the same for all agents.
        constantArray[0] = ConstantsRDPSO.C1;
        constantArray[1] = ConstantsRDPSO.C2;
        constantArray[2] = ConstantsRDPSO.C3;

        // put agent into the solution array
        swarmSolutionArray = SwarmSolution.mergeSolutionToArray(new AgentSolution(getId(), agentBestSolution), swarmSolutionArray);
        // initialize subswarming array, to keep track of teammates
        subswarmingArray = Subswarming.initializeSubswarmingArray();
        // add this agent to the appropriate set
        subswarmingArray.get(swarmID).add(getId());
        // update subswarming variables
        modifyTimestamp = timestep;
        numMods++;

    }

    /*
        assign agents to different (incremental) swarm groups inside the main swarm (1 to MAX_SWARMS-1), 0 is social exclusion
     */
    private int assignToSwarm() {

        // in case there is just one swarm + socially excluded
        if (ConstantsRDPSO.INIT_SWARMS == 2)
            return 1;

        if (getSwarmCounter() == ConstantsRDPSO.INIT_SWARMS - 1)
            setSwarmCounter(0);

        incrementSwarmCounter();

        return getSwarmCounter();
    }

    private void resetGroupVariables() {
        numOfImprovements = 0;
        SC = 0;
        newGroupCreation = false;
        pendingAgentRequest = false;
        pendingSubgroupRequest = false;
        bestSwarmSolution = 0.0;
        numKilledAgents = 0;
        swarmSolutionArray = new ArrayList<AgentSolution>();
    }

    @Override
    public void run() {

        int sleeptime = 100;
        boolean replanMap;
        boolean explore = false;
        boolean offsetCurrent = true;

        scan(0);

        initializeRDPSO();

        while (isAlive()) {

            // check for new state data
            State state = buffer.poll();
            // check if state is null, if null, you must call scan(0)
            if (state != null) {

                agentPositionX = position.getX();
                agentPositionY = position.getY();

                if (state.direction == Direction.NONE && state.direction != null) {

                    timestep++;

                    // Send information to other agents
                    Set<Position> movable = analyzeNeighborhood(state.neighborhood);

                    // update local map (otherwise plan can't be executed)
                    replanMap = map.update(state.neighborhood, position, timestep);
                    if (replanMap) {
                        // updated map
                        counter++;
                    }

                    // exchange info with teammates
                    registerMoveable(movable, state.neighborhood, 1); // 1 = send basic info

                    boolean replanAgents = blockMoveable(movable, state.neighborhood);

                    // update information
                    while (!inbox.isEmpty()) {
                        Message m = inbox.poll();
                        // receive and parse message from other agents, filter data from agent's swarm
                        replanMap &= parse(m.from, m.message, state.neighborhood);
                        //System.out.println("I have received a message.");
                    }
                    if (replanMap || replanAgents) {
                        plan.clear();
                        if (goalPosition != null)
                            replan(goalPosition);

                        if (replanAgents && detectLock())
                            goalPosition = null;
                    }

                    // update arena
                    if (view != null)
                        view.update(arena);

                    // INIT ROUTINE
                    // the purpose of this routine is to get all global agent's info, so the algorithm starts in sync
                    // : randomly wander, check if you have all the messages and then start the algorithm
                    if ( initRoutine ) {

                        // check if agent can start the algorithm
                        if (subswarmingArray.size() == (ConstantsRDPSO.INIT_SWARMS) ) {

                            int globalAgentNumber = 0;

                            for (Set<Integer> s : subswarmingArray) {
                                globalAgentNumber += s.size();
                            }

                            if (globalAgentNumber == ((ConstantsRDPSO.INIT_SWARMS-1) * ConstantsRDPSO.INIT_AGENTS)) {
                                // stop init routine
                                initRoutine = false;
                                //System.out.println(getId() + " Got info from every agent.");
                                numAgents = Subswarming.getNumerOfAgentsInSubSwarm(subswarmingArray, swarmID);
                                numSwarms = Subswarming.getNumberOfSubSwarms(subswarmingArray);

                                scan(0);
                            } else {
                                // keep waiting and collecting data
                                //System.out.println(getId() + " Waiting for info about other agents ... ");

                                plan.clear();
                                Decision d = updateDecisions(state.neighborhood);

                                if (d.direction != Direction.NONE) {
                                    move(d.direction);
                                }

                                // update agent position
                                if (d.direction == Direction.LEFT)
                                    position.setX(position.getX() - 1);
                                if (d.direction == Direction.RIGHT)
                                    position.setX(position.getX() + 1);
                                if (d.direction == Direction.UP)
                                    position.setY(position.getY() - 1);
                                if (d.direction == Direction.DOWN)
                                    position.setY(position.getY() + 1);

                                // update arena agent position
                                arena.setOrigin(position.getX(), position.getY());
                                // get node for history
                                LocalMap.Node n = map.get(position.getX(), position.getY());
                                agentHistory.add(n);

                                scan(0);
                            }
                        } else {
                            System.out.println(getId() + " Something is wrong with constants. Please check.");
                        }

                    } else { // no longer in init routine

                        // check if iteration is a movement iteration
                        if (!cleanMove) { // && !clearMove && !detectLock()

                            // confirm that the agent is not a member of the excluded group (swarmID = 0)
                            if (swarmID != 0) {

                                // evaluate agent's current solution = h(x_n(t))
                                agentSolution = evaluateObjectiveFunction();

                                // check if agent improved and update agent's best solution
                                if (agentSolution > agentBestSolution) {
                                    agentBestSolution = agentSolution;
                                    // update best cognitive solution
                                    solutionArray[0] = agentPositionX;
                                    solutionArray[3] = agentPositionY;
                                }

                                // add agentSolution to vector H(t) that includes solutions of all agents within the swarmID group
                                swarmSolutionArray = SwarmSolution.mergeSolutionToArray(new AgentSolution(getId(), agentBestSolution), swarmSolutionArray);
                                // wait till all agents put solutions in solution array...

                                // find best solution in vector H(t) = max(H(t))
                                double maxSwarmSolution = SwarmSolution.findMaxSwarmSolutionList(swarmSolutionArray);
                                // check if subgroup improved

                                if (maxSwarmSolution > bestSwarmSolution) {
                                    //System.out.println(getId() + " max swarm sol is better than best.");
                                    bestSwarmSolution = maxSwarmSolution;
                                    // update social component
                                    solutionArray[1] = agentPositionX;
                                    solutionArray[4] = agentPositionY;
                                    // decrease stagnancy counter
                                    if (SC > 0)
                                        SC = SC - 1;
                                    // check if group can be rewarded
                                    if (SC == 0) {

                                        //System.out.println(getId() + " SC=0, numAgents = " + Subswarming.getNumerOfAgentsInSubSwarm(subswarmingArray, swarmID));
                                        //System.out.println(getId() + " Weird Swarm ID " + swarmID);
                                        if ((Subswarming.getNumerOfAgentsInSubSwarm(subswarmingArray, swarmID)
                                                < ConstantsRDPSO.MAX_AGENTS) && spawnAgentProbability()) {// && !pendingAgentRequest) {

                                            callAgent = true;
                                            requestingSwarmID = swarmID;
                                            pendingAgentRequest = true;

                                            movable = analyzeNeighborhood(state.neighborhood);
                                            registerMoveable(movable, state.neighborhood, 2); // 2 = send request agent info
                                            // only broadcast this request to the excluded group of agents

                                            if (numKilledAgents > 0) {
                                                // decrease killed agents counter
                                                numKilledAgents--;
                                            }

                                             //System.out.println(getId() + ": S new agent req, to join swarm id: " + swarmID);
                                        }
                                        if (Subswarming.getNumberOfSubSwarms(subswarmingArray) < ConstantsRDPSO.MAX_SWARMS
                                                && spawnGroupProbability()) {//&& !pendingSubgroupRequest) {

                                            //System.out.println(getId() + ": Sending new group request.");

                                            createSwarm = true;
                                            pendingSubgroupRequest = true;
                                            subgroupRequestedByID = swarmID;

                                            movable = analyzeNeighborhood(state.neighborhood);
                                            registerMoveable(movable, state.neighborhood, 3); // 3 = send create subgroup info

                                            // yes, also decrease the numKilledAgents, it's really just a performance counter
                                            // doesn't have to do much with actual number of excluded agents
                                            if (numKilledAgents > 0) {
                                                // decrease excluded agents counter
                                                //dirtyData = true;
                                                numKilledAgents--;
                                            }
                                        }
                                    }
                                    // subgroup has NOT IMPROVED
                                } else {
                                    //System.out.println(getId() + " my subgroup " + swarmID + " has not improved SC = " + (SC + 1));
                                    SC = SC + 1;
                                    //System.out.println(", new stagnancy counter " + SC);
                                    // punish subgroup
                                    if (SC == ConstantsRDPSO.SC_MAX) {

                                        // reset stagnancy counter
                                        SC = ConstantsRDPSO.SC_MAX * (1 - (1 / (numKilledAgents + 1)));
                                        // check if agent can be excluded
                                        if (Subswarming.getNumerOfAgentsInSubSwarm(subswarmingArray, swarmID)
                                                > ConstantsRDPSO.MIN_AGENTS) { //&& !newGroupCreation) {

                                            // if this is the worst preforming agent in the group, exclude
                                            if (agentBestSolution == SwarmSolution.findMinSwarmSolutionList(swarmSolutionArray)) {

                                                // remove agent from subswarming array, with swarm id = swarmID
                                                subswarmingArray = Subswarming.removeAgentFromSubswarming(subswarmingArray, getId(), swarmID);
                                                // add agent to subswarming array, excluded group
                                                subswarmingArray = Subswarming.addAgentToSubswarming(subswarmingArray, getId(), 0);

                                                // remove agent solution from solution array..
                                                SwarmSolution.removeSolutionFromArray(new AgentSolution(getId(), agentBestSolution), swarmSolutionArray);

                                                // increase number of excluded agents in swarm
                                                numKilledAgents++;
                                                // decrease number of agents left in the swarm
                                                numAgents--;

                                                // reset acknowledged to be able to accept new swarm requests
                                                ackAgentRequest = false;

                                                //dirtyData = true;
                                                // try to send out a message, to everyone
                                                //System.out.println(getId() + " Sending exclusion request.");
                                                movable = analyzeNeighborhood(state.neighborhood);
                                                registerMoveable(movable, state.neighborhood, 8); // 8 = send exclusion request
                                                // exclude agent
                                                swarmID = 0;


                                                resetGroupVariables();

                                                // prevent agent from sending wrong data to members of excluded group
                                                // rather should receive their information first, then be able to send
                                                //System.out.println(getId() + " requesting 0 snapshot");
                                                requestSnapshot = true;

                                                //System.out.println(getId() + " EXCLUDED!" );

                                            }
                                            // delete the entire subgroup
                                        } else if (Subswarming.getNumberOfSubSwarms(subswarmingArray) > ConstantsRDPSO.MIN_SWARMS) {
                                            // exclude this agent
                                            //System.out.println(getId() + " subgroup deleted.., EXCLUDING self");
                                            // remove agent solution from solution array..
                                            SwarmSolution.removeSolutionFromArray(new AgentSolution(getId(), agentBestSolution), swarmSolutionArray);

                                            // remove agent from subswarming array
                                            subswarmingArray = Subswarming.removeAgentFromSubswarming(subswarmingArray, getId(), swarmID);

                                            subswarmingArray = Subswarming.deleteSubgroupFromSubswarmingArray(subswarmingArray, swarmID);
                                            //System.out.print(getId() + " deleted subgroup " + swarmID);
                                            //Subswarming.toString(subswarmingArray);

                                            subswarmingArray = Subswarming.addAgentToSubswarming(subswarmingArray, getId(), 0);

                                            movable = analyzeNeighborhood(state.neighborhood);

                                            registerMoveable(movable, state.neighborhood, 5); //  5 - notify about numSwarms

                                            // exclude agent
                                            swarmID = 0;

                                            resetGroupVariables();

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

                                    goalPosition = new Position(0, 0);

                                    // limit interval
                                    int maxB = b;
                                    int minB = -b;
                                    // normalization constants
                                    double minA = -1.87;
                                    double maxA = 3.87;
                                    double normalizedA = diff / maxA;

                                    double factor = Math.abs(normalizedA);
                                    if (normalizedA > 0) {
                                        factor = 1 - factor;
                                    }
                                    double minInt = minB * (minA/maxA);
                                    minInt = factor * minInt;
                                    double maxInt = factor * maxB;

                                    b += 1/normalizedA;
                                    double range = maxInt - minInt;
                                    Random r = new Random();
                                    double randomOffsetX = range * r.nextDouble();
                                    double randomOffsetY = range * r.nextDouble();
                                    if (Math.random() < 0.5) randomOffsetX = randomOffsetX * -1;
                                    if (Math.random() > 0.5) randomOffsetY = randomOffsetY * -1;

                                    //System.out.println("interval: [ " + minInt + ", " + maxInt + " ]; rrx: " + randomOffsetX + "; rry: " + randomOffsetY);
                                    //System.out.println(b + " " + (1/normalizedA));

                                    // UPDATE AGENT'S VELOCITY
                                    double functionResultX = 0.0;
                                    randomArray[0] = Math.random();
                                    randomArray[1] = Math.random();
                                    randomArray[2] = Math.random();

                                    for (int i = 0; i < 3; i++) {
                                        functionResultX += randomArray[i] * constantArray[i]  * (solutionArray[i] - agentPositionX);
                                    }

                                    double sumWeightX = 0.0;
                                    for (int i = 0; i <3; i++) {
                                        sumWeightX += randomArray[i] * constantArray[i];
                                    }
                                    functionResultX = functionResultX / sumWeightX;

                                    double functionResultY = 0.0;
                                    randomArray[0] = Math.random();
                                    randomArray[1] = Math.random();
                                    randomArray[2] = Math.random();

                                    for (int i = 0; i < 3; i++) {
                                        functionResultY += constantArray[i] * randomArray[i] * (solutionArray[i + 3] - agentPositionY);
                                    }

                                    double sumWeightY = 0.0;
                                    for (int i = 0; i <3; i++) {
                                        sumWeightY += randomArray[i] * constantArray[i];
                                    }
                                    functionResultY = functionResultY / sumWeightY;

                                    agentVelocityX = ConstantsRDPSO.W + functionResultX;
                                    agentVelocityY = ConstantsRDPSO.W + functionResultY;


                                    // UPDATE AGENT'S POSITION
                                    double tempAgentPositionX = agentPositionX + agentVelocityX + randomOffsetX;
                                    int roundedX = (int) Math.round(tempAgentPositionX);

                                    double tempAgentPositionY = agentPositionY + agentVelocityY + randomOffsetY;
                                    int roundedY = (int) Math.round(tempAgentPositionY);

                                    goalPosition.setX(roundedX);
                                    goalPosition.setY(roundedY);

                                    //System.out.println(goalPosition.getX() + " " + goalPosition.getY());
                                    //System.out.print(getId() + " New wanted position: " + goalPosition);
                                }

                                // Is agent on goal position or is the position not even in the map?
                                if ((goalPosition.getX() - position.getX()) == 0 && (goalPosition.getY() - position.getY()) == 0 || positionNotInMap) {

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
                                    }
                                } else {
                                    // Is goal position clear?
                                    boolean movePossible = positionPossible(state.neighborhood, (goalPosition.getX() - position.getX()), (goalPosition.getY() - position.getY()));

                                    if ((movePossible && !knownLocalMap) || (movePossible && knownLocalMap)) {
                                        // call move with local coordinates (offset from 0,0)
                                        if (cleanMove(goalPosition.getX(), goalPosition.getY()) != null) {
                                            // jump into movement execution next iteration
                                            if (goalPosition != null)
                                                cleanMove = true; // this can happen if no local map

                                        } else {
                                            // move is possible but not accessible yet - go to explore
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

                                // Send information to other agents
                                movable = analyzeNeighborhood(state.neighborhood);
                                // update agent's local map
                                boolean replan = map.update(state.neighborhood, position, timestep);
                                if (replan) {
                                    counter++;
                                }
                                registerMoveable(movable, state.neighborhood, 1); // 1 = send basic info

                                while (!inbox.isEmpty()) {
                                    Message m = inbox.poll();
                                    // receive and parse message from other agents, filter data from agent's swarm
                                    parse(m.from, m.message, state.neighborhood);
                                }
                                // update arena
                                if (view != null)
                                    view.update(arena);

                                plan.clear();
                                Decision d = updateDecisions(state.neighborhood);

                                if (d.direction != Direction.NONE) {
                                    move(d.direction);
                                }

                                // update agent position
                                if (d.direction == Direction.LEFT)
                                    position.setX(position.getX() - 1);
                                if (d.direction == Direction.RIGHT)
                                    position.setX(position.getX() + 1);
                                if (d.direction == Direction.UP)
                                    position.setY(position.getY() - 1);
                                if (d.direction == Direction.DOWN)
                                    position.setY(position.getY() + 1);

                                // update arena agent position
                                arena.setOrigin(position.getX(), position.getY());
                                // get node for history
                                LocalMap.Node n = map.get(position.getX(), position.getY());
                                agentHistory.add(n);

                                scan(0);

                                // randomly wander round

                                // evaluate agent's current solution = h(x_n(t))
                                agentSolution = evaluateObjectiveFunction();

                                // check if agent improved and update agent's best solution
                                if (agentSolution > agentBestSolution) {
                                    agentBestSolution = agentSolution;
                                    // update best cognitive solution
                                    solutionArray[0] = agentPositionX;
                                    solutionArray[3] = agentPositionY;
                                }

                                // add agentSolution to vector H(t) that includes solutions of all agents within the swarmID group
                                SwarmSolution.mergeSolutionToArray(new AgentSolution(getId(), agentBestSolution), swarmSolutionArray);
                                // wait till all agents put solutions in solution array...

                                // check if group improved
                                // find best solution in vector H(t) = max(H(t))
                                double maxSwarmSolution = SwarmSolution.findMaxSwarmSolutionList(swarmSolutionArray);
                                // check if subgroup improved
                                if (maxSwarmSolution > bestSwarmSolution) {
                                    bestSwarmSolution = maxSwarmSolution;

                                    // check to see, if this agent is one of the best N_I agents in the group
                                    boolean best_ni = false;
                                    double[] bestNISolutions = SwarmSolution.findTopISolutionsInSwarmSolutionList(swarmSolutionArray,
                                            Subswarming.getNumerOfAgentsInSubSwarm(subswarmingArray, 0));

                                    for (double bestNISolution : bestNISolutions) {
                                        // check if agent's best solution matches any of topI swarm solutions
                                        if (agentBestSolution == bestNISolution) {
                                            best_ni = true;
                                        }
                                    }
                                    // agent is one of the best in the excluded group
                                    if (best_ni) {
                                        // note: N_X is the number of agents in the excluded group
                                        // check if number of excluded agents is bigger than number of initial agents
                                        // required to form a sub-swarm then check probability for forming a new group
                                        if (Subswarming.getNumerOfAgentsInSubSwarm(subswarmingArray, 0)
                                                >= ConstantsRDPSO.INIT_AGENTS && spawnGroupProbabilityExcluded()) {

                                            //System.out.println("I should spawn a new group.");
                                            //todo: how to assign new swarm.. I guess you will have to keep a number of swarms
                                            //todo: broadcast a need for N_I-1 robots to form new swarm
                                            movable = analyzeNeighborhood(state.neighborhood);
                                            registerMoveable(movable, state.neighborhood, 1); // 1 = send info
                                            //swarmID = newSwarmID + 1;
                                        }
                                        // if the agent receives a request to join a swarm
                                        else if (callAgent) {

                                            // remove agent from the socially excluded group
                                            subswarmingArray = Subswarming.removeAgentFromSubswarming(subswarmingArray, getId(), 0);
                                            // i belong to a new swarm
                                            swarmID = requestingSwarmID;
                                            // add agent to the new group
                                            subswarmingArray = Subswarming.addAgentToSubswarming(subswarmingArray, getId(), swarmID);

                                            // i accepted the request
                                            callAgent = false;
                                            // request responded to
                                            ackAgentRequest = true;

                                            // update snapshot the requesting swarm ID variables
                                            swarmSolutionArray = new ArrayList<AgentSolution>(snapSolutionArray);
                                            SC = snapSC;
                                            bestSwarmSolution = snapBestSwarmSolution;
                                            numAgents = snapNumAgents + 1;
                                            numKilledAgents = snapKilledAgents;

                                            movable = analyzeNeighborhood(state.neighborhood);
                                            registerMoveable(movable, state.neighborhood, 6); // 6 - agent response

                                            // if there is a need for a new subgroup and there is enough agents in excluded
                                            // group to create one, then go for it
                                        } else if (createSwarm && Subswarming.getNumerOfAgentsInSubSwarm(subswarmingArray, swarmID) >= ConstantsRDPSO.INIT_AGENTS) {

                                            // temp change swarmID to the group that requested it, so a message can be sent
                                            swarmID = subgroupRequestedByID;
                                            // acknowledge the new group request
                                            ackSubgroupRequest = true;
                                            // stop the creation of even more swarms
                                            createSwarm = false;
                                            // send out ack for group creation to the previously requesting swarm!!
                                            movable = analyzeNeighborhood(state.neighborhood);
                                            registerMoveable(movable, state.neighborhood, 7); // 7 = subgroup ack

                                            // include agent in the new subgroup and increase numSwarms
                                            swarmID = numSwarms++;
                                            //System.out.println(getId() + ": I'm creating a new group! " + swarmID);
                                            // change number of agents to 1
                                            numAgents = 1;
                                            resetGroupVariables();
                                            // send out new group info
                                            registerMoveable(movable, state.neighborhood, 1); // 1 = send info

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
                                // get node for history
                                LocalMap.Node n = map.get(position.getX(), position.getY());
                                agentHistory.add(n);

                                scan(0);

                            } else {
                                // mark end of multi move
                                cleanMove = false;
                                explore = false;
                                scan(0);
                            }
                        }
                    } // close init routine
                } else {
                    scan(0);
                }
            }

            try {
                Thread.sleep(sleeptime);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private boolean canMove(Neighborhood n, int x, int y) {
        // return empty for available tiles
        return n.getCell(x, y) == Neighborhood.EMPTY;

    }

    private Position cleanMove(int moveXleft, int moveYleft) {

        List<Direction> directions;

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

        if (directions == null)
            plan.clear();
        else plan.addAll(directions);

        return p;
    }

    // to implement random wandering
    private Decision updateDecisions(Neighborhood n) {

        // Check which ways the agent can move.
        if (n == null)
            return still;

        boolean up = canMove(n, 0, -1);
        boolean down = canMove(n, 0, 1);
        boolean left = canMove(n, -1, 0);
        boolean right = canMove(n, 1, 0);
        boolean moves[] = {up, down, left, right};

        // Randomly shuffle possible moves.
        shuffleArray(decisions);

        // Check which moves are available against the shuffled array
        // then choose the move first possible move from the array.
        for (int i = 0; i < decisions.length; i++) {
            if (decisions[i].getDirection().toString().equals("UP") && moves[0]) {
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

    private double evaluateObjectiveFunction() {

        // How many nodes have been explored since last timestep
        int newNodeCount = agentHistory.size();
        int nodeDifference = newNodeCount - previousNodeCount;
        previousNodeCount = newNodeCount;

        double malus = (1 - 1*previousNodeDiff4) + (0.5 - 0.5*previousNodeDiff3) + (0.25 - 0.25*previousNodeDiff2) + (0.12 - 0.12*previousNodeDiff1);

        double bonus = (2 * nodeDifference) + previousNodeDiff4 + 0.5*previousNodeDiff3 + 0.25*previousNodeDiff2 + 0.12*previousNodeDiff1;

        previousNodeDiff1 = previousNodeDiff2;
        previousNodeDiff2 = previousNodeDiff3;
        previousNodeDiff3 = previousNodeDiff4;
        previousNodeDiff4 = nodeDifference;

        double result = previousResult + bonus - malus;

        diff = result - previousResult;

        previousResult = result;

        //System.out.println(String.valueOf(result));

        return result;
    }

    private double evaluateObstacleFunction(State state) {
        // analyze neighbourhood, find obstacles
        Set<Position> obstacles = analyzeNeighborhoodObstacles(state.neighborhood);
        double functionResult = 0.0;
        for (Position p : obstacles) {
            // calculate manhattan distance to point
            Position from = new Position(agentPositionX, agentPositionY);
            Position to = new Position(p.getX(), p.getY());
            functionResult += Position.distance(from, to);
        }

        //System.out.println(String.valueOf(functionResult/1000));
        return functionResult/1000;
    }

    /* check probability of a group spawning a new subgroup */
    private boolean spawnGroupProbability() {
        if ((Math.random() * (Subswarming.getNumerOfAgentsInSubSwarm(subswarmingArray, swarmID)
                / ConstantsRDPSO.MAX_AGENTS)) > Math.random()) {
            return true;
        } else return false;
    }

    /* check probability of a group spawning a new subgroup from excluded group */
    private boolean spawnGroupProbabilityExcluded() {
        // initial agents in each swarm * (initial number of swarms - 1) to account for social exclusion
        if ((Math.random() * (Subswarming.getNumerOfAgentsInSubSwarm(subswarmingArray, swarmID)
                / (ConstantsRDPSO.INIT_AGENTS * (ConstantsRDPSO.INIT_SWARMS - 1)))) > Math.random()) {
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

    private void registerMoveable(Set<Position> moveable, Neighborhood n, int dataType) {

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
                //if (Math.abs(timestep - data.notified) > 1) {
                if (Math.abs(timestep - data.notified) > 10 && dataType == 1 ) {
                    sendInfo(id, dataType);
                    data.notified = timestep;
                    data.map = false;
                } else if (dataType != 1) {
                    sendInfo(id, dataType);
                    data.notified = timestep;
                    data.map = false;
                }
                //}

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

                // change size of relative agent space
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
                map.addModifier(x + i, y + j);
            }
        }

        return replan;

    }

    private void sendMap(int to) {

        Collection<LocalMap.Node> nodes;
        Position position;
        Position offset;
        int timediff;

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

        //debug("Sending map to %d: %d chunks", to, nodes.size());

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
            // Check if field is a target, if not return false
            if (n.getPosition().getX() % 2 != 0 || n.getPosition().getY() % 2 != 0)
                return false;

            if (!known.inside(n.getPosition()))
                return true;

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

        return variability < 3;
    }

    //FisherYates shuffle for random array shuffle
    static void shuffleArray(Decision[] ar) {
        Random rnd = new Random();
        for (int i = ar.length - 1; i > 0; i--) {
            int index = rnd.nextInt(i + 1);
            // Simple swap
            Decision a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
        }
    }

}