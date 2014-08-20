package org.grid.agent.sample;

import org.grid.agent.Agent;
import org.grid.agent.sample.LocalMap.*;
import org.grid.arena.SwingView;
import org.grid.protocol.Message.Direction;
import org.grid.protocol.Neighborhood;
import org.grid.protocol.Position;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

//java -cp bin org.grid.agent.sample.Agent localhost ModReaperAgent

public class ModReaperAgent extends Agent {

    // define agent modes
    private static enum Mode {
        EXPLORE, SEEK, SURVEIL, RETURN, CLEAR
    }

    // define what the agent is looking for
    private static Filter goalFilter = new Filter() {
        @Override
        public boolean filter(Node n) {
            /* EMPTY = 0; WALL = 1; HEADQUARTERS = 2;
            OTHER_HEADQUARTERS = 4;  UNKNOWN = 6; */
            return n.getBody() == 2;
            // apparently works best in this example, if set to 2
        }
    };

    // define agent message class
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

            private Bounds bounds;

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

                return String.format("ID: %d, Flag: %b", id);

            }
    }

    /* check area for unknown nodes  */
    private static class UnknownAreaFilter implements Filter {

        private Bounds known;    // bounds of the area
        private Position center; // center position in the area
        private int radius;

        public UnknownAreaFilter(Bounds known, Position center) {
            this.known = known;
            this.center = center;
            radius = Math.min(known.getRight() - known.getLeft(), known.getBottom() - known.getTop());
        }

        @Override
        public boolean filter(Node n) {
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

    private static class DistanceFilter implements Filter {

        private Position p;

        private int mindistance, maxdistance;

        public DistanceFilter(Position p, int mindistance, int maxdistance) {
            this.p = p;
            this.mindistance = mindistance;
            this.maxdistance = maxdistance;
        }

        @Override
        public boolean filter(Node n) {
            int distance = Position.distance(n.getPosition(), p);
            return distance <= maxdistance && distance >= mindistance;
        }

    }

    private static final CostFormula returnFormula = new CostFormula() {

        @Override
        public int getCost(Node n) {
            switch (n.getBody()) {
                case Neighborhood.HEADQUARTERS:
                case Neighborhood.EMPTY:
                    return 1;
                case Neighborhood.OTHER_HEADQUARTERS:
                case Neighborhood.OTHER:
                case Neighborhood.WALL:
                default:
                    return Integer.MAX_VALUE;
            }
        }
    };

    private LocalMap map = new LocalMap();

    private Position position = new Position(0, 0);

    private Mode mode = Mode.EXPLORE;

    private int patience = 0;

    private int parent = -1;

    private int timestep = 1;

    private static class State {

        Neighborhood neighborhood;

        Direction direction;

        public State(int stamp, Neighborhood neighborhood, Direction direction) {
            super();
            this.neighborhood = neighborhood;
            this.direction = direction;
        }

    }

    private ConcurrentLinkedQueue<Message> inbox = new ConcurrentLinkedQueue<Message>();

    private ConcurrentLinkedQueue<State> buffer = new ConcurrentLinkedQueue<State>();

    private ConcurrentLinkedQueue<Direction> plan = new ConcurrentLinkedQueue<Direction>();

    private JFrame window;

    private LocalMapArena arena;

    private SwingView view;

    @Override
    public void initialize() {

        // 6 = scope of the arena
        arena = map.getArena(6);

        //if (System.getProperty("reaper") != null) {
            view = new SwingView(24);

            view.setBasePallette(new SwingView.HeatPalette(32));
            window = new JFrame("Agent " + getId());
            window.setContentPane(view);
            window.setSize(view.getPreferredSize(arena));
            window.setVisible(true);

        //}
    }

    @Override
    public void receive(int from, byte[] message) {

        // add new message to concurrent linked queue
        inbox.add(new Message(from, message));

    }

    @Override
    public void state(int stamp, Neighborhood neighborhood, Direction direction) {

        buffer.add(new State(stamp, neighborhood, direction));

    }

    @Override
    public void run() {

        int sleeptime = 1000;

        scan(0);


        while (isAlive()) {

            State state = buffer.poll();

            if (state != null) {

                if (state.direction == Direction.NONE && state.direction != null) {

                    // find movable fields
                    Set<Position> movable = analyzeNeighborhood(state.neighborhood);
                    // update local map
                    boolean replanMap = map.update(state.neighborhood, position, timestep);

                    registerMoveable(movable, state.neighborhood);

                    boolean replanAgents = blockMoveable(movable, state.neighborhood);

                    Set<Node> enemies = filterEnemies(movable, state.neighborhood);

                    while (!inbox.isEmpty()) {
                        Message m = inbox.poll();

                        replanMap &=parse(m.from, m.message,state.neighborhood);
                    }


                    if (view != null)
                        view.update(arena);

                    if (replanMap || replanAgents) {
                        if (replanAgents) {
                            System.out.println("Replanning agents...");
                        }
                        plan.clear();
                    }

                    if (plan.isEmpty()) {

                        List<Direction> directions = null;

                        Paths paths = map.findShortestPaths(position);

                        while (directions == null) {

                            switch (mode) {
                                case EXPLORE: {

                                    if (stohastic(0.9)) {
                                        List<Node> candidates = map.filter(goalFilter);

                                        directions = paths.shortestPathTo(candidates);

                                        if (directions != null) {
                                            changeMode(Mode.SEEK);
                                            break;
                                        }
                                    }

                                    List<Node> candidates = map.filter(LocalMap.BORDER_FILTER);

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
                                        List<Node> candidates = map.filter(goalFilter);

                                        directions = paths.shortestPathTo(candidates);

                                        if (directions != null) {
                                            changeMode(Mode.SEEK);
                                            break;
                                        }
                                    }

                                    List<Node> candidates = map.getOldestNodes(10);

                                    directions = paths.shortestPathTo(candidates);

                                    break;
                                }
                                case SEEK: {

                                    List<Node> candidates = map.filter(goalFilter);

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
                                    Node n = map.get(p.getX(), p.getY());
                                    directions = paths.shortestPathTo(n);
                                    break;
                                }

                                case CLEAR: {

                                    List<Node> candidates = map.filter(new DistanceFilter(position,
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
                            System.out.println("LOCK DETECTED");
                            changeMode(Mode.CLEAR);
                        }

                        scan(0);

                    }

                } else {
                    //System.out.println("Calling scan..");
                    scan(0);
                }

            }

            if (timestep % 20 == 0) {
                sleeptime = 100; //1000 / getSpeed();
            }

            try {
                Thread.sleep(sleeptime);
            } catch (InterruptedException e) {
            }

        }

    }

    @Override
    public void terminate() {

        if (view != null)
            window.setVisible(false);

    }

    protected void debug(String format, Object... objects) {
        System.out.println("[" + getId() + "]: " + String.format(format, objects));
    }

    private Set<Position> moveable = new HashSet<Position>();

    private HashMap<Integer, MemberData> registry = new HashMap<Integer, MemberData>();

    private Position origin = null;

    private Vector<Position> history = new Vector<Position>();

    private Set<Position> analyzeNeighborhood(Neighborhood n) {
        int x = position.getX();
        int y = position.getY();

        HashSet<Position> moveable = new HashSet<Position>();

        for (int i = -n.getSize(); i <= n.getSize(); i++) {
            for (int j = -n.getSize(); j <= n.getSize(); j++) {

                if ((i == 0 && j == 0))
                    continue;

                if (n.getCell(i, j) == Neighborhood.HEADQUARTERS) {
                    //System.out.println("Discovered HQ");
                    if (origin == null)
                        origin = new Position(x + i, y + j);
                    continue;
                }

                if (n.getCell(i, j) > 0 || n.getCell(i, j) == Neighborhood.OTHER) {
                    //System.out.println("Discovered something else");
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
                    //System.out.println("Member position " + p.getX() + " , " + p.getY());
                    member.notified = -30;
                    registry.put(id, member);
                    //System.out.println("Member with id " + id + " put in registry.");
                }

                MemberData data = registry.get(id);

                if (Math.abs(timestep - data.notified) > 20) {
                    sendInfo(id);
                    data.notified = timestep;
                    data.map = false;
                }

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

    private Set<Node> filterEnemies(Set<Position> moveable, Neighborhood n) {

        int x = position.getX();
        int y = position.getY();

        HashSet<Node> enemies = new HashSet<Node>();

        for (Position p : moveable) {

            int i = p.getX() - x;
            int j = p.getY() - y;

            if (n.getCell(i, j) == Neighborhood.OTHER)
                enemies.add(map.get(p.getX(), p.getY()));

        }

        return enemies;
    }

    private Set<Node> filterTeam(Set<Position> moveable, Neighborhood n) {

        int x = position.getX();
        int y = position.getY();

        HashSet<Node> enemies = new HashSet<Node>();

        for (Position p : moveable) {

            int i = p.getX() - x;
            int j = p.getY() - y;

            if (n.getCell(i, j) > 0)
                enemies.add(map.get(p.getX(), p.getY()));

        }

        return enemies;
    }

    private boolean blockMoveable(Set<Position> moveable, Neighborhood n) {

        boolean replan = false;

        int x = position.getX();
        int y = position.getY();

        map.clearModifiers();

        for (Position p : moveable) {

            int i = p.getX() - x;
            int j = p.getY() - y;

            System.out.println("BM: (i,j):  " + i + ", " + j);


            if (n.getCell(i, j) > 0 || n.getCell(i, j) == Neighborhood.OTHER) {
                System.out.println("Agent detected.");
                int size = 3;

                if (n.getCell(i, j) == Neighborhood.OTHER) {
                    size = 5;
                    System.out.println("Size increased to 5.");
                }

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

    private boolean checkParent(Neighborhood neighborhood) {

        int x = position.getX();
        int y = position.getY();

        MemberData member = registry.get(parent);

        if (member == null)
            return false;

        Position p = member.getPosition();

        return neighborhood.getCell(p.getX() - x, p.getY() - y) == member.getId();

    }

    private boolean stohastic(double probability) {
        return Math.random() < probability;
    }

    private boolean parse(int from, byte[] message, Neighborhood neighborhood) {

        try {
            ObjectInputStream in = new ObjectInputStream(
                    new ByteArrayInputStream(message));

            int type = in.readByte();

            switch (type) {
                case 1: { // info message

                    Position origin = new Position(0, 0);
                    origin.setX(in.readInt());
                    origin.setY(in.readInt());

                    Bounds bounds = new Bounds(0, 0, 0, 0);
                    Position center = new Position(0, 0);
                    bounds.setTop(in.readInt());
                    bounds.setBottom(in.readInt());
                    bounds.setLeft(in.readInt());
                    bounds.setRight(in.readInt());

                    center.setX(in.readInt());
                    center.setY(in.readInt());

                    int timediff = in.readInt() - timestep;

                    synchronized (registry) {
                        if (registry.containsKey(from)) {
                            MemberData data = registry.get(from);
                            data.bounds = bounds;
                            data.origin = origin;
                            data.center = center;
                            data.info = timestep;
                            data.timediff = timediff;
                            debug("New info: %s", data);
                        }

                    }

                    break;
                }
                case 2: { // map message

                    MapChunk chunk = new MapChunk();

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

    private void sendInfo(int to) {

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(getMaxMessageSize());
            ObjectOutputStream out = new ObjectOutputStream(buffer);

            out.writeByte(1);

            Bounds bounds = map.getBounds();

            Position center = map.getCenter();

            out.writeInt(origin.getX());
            out.writeInt(origin.getY());
            out.writeInt(bounds.getTop());
            out.writeInt(bounds.getBottom());
            out.writeInt(bounds.getLeft());
            out.writeInt(bounds.getRight());

            out.writeInt(center.getX());
            out.writeInt(center.getY());

            out.writeInt(timestep);

            out.flush();

            send(to, buffer.toByteArray());

        } catch (IOException e) {
            debug("Error sending message to %d: %s", to, e);
        }

    }

    private void sendMap(int to) {

        Collection<Node> nodes = null;
        Position position = null;
        Position offset = null;
        int timediff = 0;

        synchronized (registry) {

            MemberData data = registry.get(to);

            if (data == null || data.bounds == null)
                return;

            Bounds bounds = new Bounds(data.bounds);
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

            Vector<Node> list = new Vector<Node>(nodes);

            final Position center = position;

            Collections.sort(list, new Comparator<Node>() {

                @Override
                public int compare(Node o1, Node o2) {

                    int dist1 = Position.distance(center, o1.getPosition());
                    int dist2 = Position.distance(center, o2.getPosition());

                    if (dist1 > dist2)
                        return 1;
                    if (dist1 < dist2)
                        return -1;

                    return 0;
                }
            });

            for (Node n : list) {

                if (buffer.size() + 20 >= getMaxMessageSize())
                    break;

                MapChunk chunk = n.getChunk(offset, timediff);

                chunk.write(out);

                out.flush();
            }

            send(to, buffer.toByteArray());

        } catch (IOException e) {
            debug("Error sending message to %d: %s", to, e);
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

    private void changeMode(Mode mode) {

        if (this.mode != mode) {
            debug("Switching from %s to %s", this.mode, mode);
        }

        this.mode = mode;
    }

}
