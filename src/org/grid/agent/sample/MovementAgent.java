package org.grid.agent.sample;

import org.grid.agent.Agent;
import org.grid.agent.Membership;
import org.grid.arena.SwingView;
import org.grid.protocol.Message.Direction;
import org.grid.protocol.Neighborhood;
import org.grid.protocol.Position;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by nanorax on 18/08/14.
 */
@Membership(team = "movement")
public class MovementAgent extends Agent {

    private LocalMap map = new LocalMap();
    private Position position = new Position(0, 0);
    private int timestep = 1;
    private boolean firstIteration = true;

    // variables
    int agentPositionX = 0; // x_n(t)
    int agentPositionY = 0; // x_n(t)

    // cleanMove tells the agent to only move
    boolean cleanMove = false;
    Position goalPosition = new Position(0, 0);

    private JFrame window;
    private LocalMap.LocalMapArena arena;
    private SwingView view;


    private static class State {

        Neighborhood neighborhood;

        org.grid.protocol.Message.Direction direction;

        public State(int stamp, Neighborhood neighborhood, org.grid.protocol.Message.Direction direction) {
            super();
            this.neighborhood = neighborhood;
            this.direction = direction;
        }

    }

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


    @Override
    public void initialize() {

        // 6 = scope of the arena
        arena = map.getArena(6);
        view = new SwingView(24);

        view.setBasePallette(new SwingView.HeatPalette(32));
        window = new JFrame("Agent " + getId());
        window.setContentPane(view);
        window.setSize(view.getPreferredSize(arena));
        window.setVisible(true);

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

    @Override
    public void state(int stamp, Neighborhood neighborhood, org.grid.protocol.Message.Direction direction) {

        buffer.add(new State(stamp, neighborhood, direction));

    }

    @Override
    public void terminate() {

        firstIteration = true;

    }

    @Override
    public void run() {

        int sleeptime = 100;

        scan(0);

        while (isAlive()) {

            // check for new state data
            State state = buffer.poll();
            // check if state is null, if null, you must call scan(0)
            if (state != null) {

                agentPositionX = position.getX();
                agentPositionY = position.getY();

                if (state.direction == org.grid.protocol.Message.Direction.NONE && state.direction != null) {


                        // update local map (otherwise plan can't be executed)
                        boolean replanMap = map.update(state.neighborhood, position, timestep);
                        if (replanMap) {
                            // not sure if I need this here.
                            plan.clear();
                            replan(goalPosition);
                        }

                        // update arena
                        if (view != null)
                            view.update(arena);

                        if (!cleanMove) {

                            // it's not global, it's an offset from the current global location
                            int roundedX = 3;
                            int roundedY = 3;
                            if ( goalPosition == null || goalPosition.getX() == 0 && goalPosition.getY() == 0 ) {
                                goalPosition = new Position(0,0);
                                roundedX = -1 + position.getX() + generateRandomOffset(2,6);
                                roundedY = 1 + position.getY() + generateRandomOffset(2,6);
                                goalPosition.setX(roundedX);
                                goalPosition.setY(roundedY);
                            }
                            System.out.println("Goal: " + goalPosition);

                            // Is goal position clear?
                            boolean movePossible = positionPossible(state.neighborhood, (goalPosition.getX() - position.getX()), (goalPosition.getY() - position.getY()));

                            if (movePossible) {
                                System.out.println("Move possible!");
                                // call move with local coordinates (offset from 0,0)
                                cleanMove(goalPosition.getX(), goalPosition.getY());
                                // jump into movement execution next iteration
                                if (goalPosition != null) cleanMove = true; // this can happen if no local map
                            } else {
                                System.out.println("Impossible move!");
                                goalPosition = null;
                            }
                            scan(0);

                        } else {
                            // check if agent is in clean move mode (doesn't do anything else but move)
                            if (cleanMove) {
                                // update agent's local map
                                replanMap = map.update(state.neighborhood, position, timestep);
                                // update arena
                                if (view != null)
                                    view.update(arena);
                                // in case new map information is received, clear the plan and calculate new position
                                if (replanMap) {
                                    plan.clear();
                                    replan(goalPosition);
                                }

                                if (!plan.isEmpty()) {

                                    Direction d = plan.poll();
                                    debug("Next move: %s", d);

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
                                     goalPosition = null;
                                    // mark end of multi move
                                    cleanMove = false;
                                    scan(0);
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

    private Position cleanMove(int moveXleft, int moveYleft) {

        List<Direction> directions = null;

        LocalMap.Paths paths = map.findShortestPaths(position);

        Position p = new Position(moveXleft, moveYleft);

        LocalMap.Node n = map.get(p.getX(), p.getY());
        if (n != null) {
            System.out.println("Node exists on local map: " + n);
        } else  if (n == null || n.getBody() == -1) {
            System.out.println("Node doesn't exist on local map!");

            // todo: how to handle missing node?
            plan.clear();
            return null;
        }

        // calculate directions to node n
        directions = paths.shortestPathTo(n);

        // cannot move anywhere ... assure, that at least one move is in the plan
        if (directions == null) {
            List<LocalMap.Node> candidates = map.filter(LocalMap.BORDER_FILTER);

            Collections.shuffle(candidates);

            directions = paths.shortestPathTo(candidates);
            if (directions == null) {
                directions = new Vector<Direction>();
                for (int i = 0; i < 1; i++)
                    directions.add(Direction.NONE);
            }
        }

        plan.addAll(directions);

        return p;
    }

    private void replan(Position p) {

        List<org.grid.protocol.Message.Direction> directions = null;

        LocalMap.Paths paths = map.findShortestPaths(position);

        LocalMap.Node n = map.get(p.getX(), p.getY());
        if (n != null) {
            System.out.println("re: Node exists on local map: " + n);
        }
        else if (n == null || n.getBody() == -1) {
            System.out.println("re: Node doesn't exist, clearing plan.");
            plan.clear();
        } else {
            directions = paths.shortestPathTo(n);
        }

        if (directions != null)
            plan.addAll(directions);
    }

    private boolean positionPossible(Neighborhood n, int x, int y) {

        if (n.getCell(x,y) != Neighborhood.EMPTY) {
            System.out.println("Position: " + x + ", " + y + " is an obstacle.");
            return false;
        } else {
            // if move is possible
            return true;
        }

    }

    public static int generateRandomOffset(int max , int min) {
        int random = - min + (int)(Math.random() * ((max - (-min)) + 1));
        return random;
    }


}
