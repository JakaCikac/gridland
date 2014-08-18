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

    // variables
    int agentPositionX = 0; // x_n(t)
    int agentPositionY = 0; // x_n(t)

    // cleanMove tells the agent to only move
    boolean cleanMove = false;
    Position goalPosition = new Position(0, 0);

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

            scan(0);

            // check for new state data
            State state = buffer.poll();
            // check if state is null, if null, you must call scan(0)
            if (state != null) {

                // todo: where do I update this?
                agentPositionX = position.getX();
                //System.out.println("position x,y: (" + position.getX() + ", " + position.getY());
                agentPositionY = position.getY();

                if (state.direction == org.grid.protocol.Message.Direction.NONE) {
                    //if (true) {

                    if (firstIteration) {
                        // initialize RDPSO variables, position and swarmID
                        // call scan, to get new state information
                        scan(0);
                        // set first iteration to false, continue the algorithm
                        firstIteration = false;
                    } // after the first iteration start the algorithm
                    else {

                        // confirm that the agent is not a member of the excluded group (swarmID = 0)
                        if (!cleanMove) {

                                int roundedX = 1;
                                int roundedY = 1;
                                // Check if the position is even possible, otherwise recalc.
                                System.out.println("GLOB current: " + position.getX() + ", " + position.getY());
                                System.out.println("GLOB new: " + (position.getX() + roundedX) + ", " + (position.getY() + roundedY));
                                System.out.println("LOCA new: " + (roundedX - position.getX()) + ", " + (roundedY - position.getY()));
                                boolean movePossible = positionPossible(state.neighborhood, (roundedX - position.getX()), (roundedY - position.getY()));
                            System.out.println(movePossible);
                                if (movePossible) {
                                    goalPosition = cleanMove(roundedX - position.getX(), roundedY - position.getY());
                                    System.out.println(goalPosition);
                                    // jump into movement execution next iteration
                                    cleanMove = true;
                                } else {
                                    // todo: recalculate?
                                }

                        } else {
                            // check if agent is in clean move mode (doesn't do anything else but move)
                            if (cleanMove) {
                                // update agent's local map
                                boolean replanMap =  map.update(state.neighborhood, position, timestep);
                                System.out.println("Replan? " + replanMap);
                                // in case new map information is received, clear the plan and calculate new position
                                if (replanMap) {
                                    plan.clear();
                                    // on replan, remember what goal you were trying to reach and go for it

                                    System.out.println("replaning for " + goalPosition.toString());
                                    goalPosition.setX(goalPosition.getX() - position.getX());
                                    goalPosition.setY(goalPosition.getY() - position.getY());
                                    replan(goalPosition);
                                    System.out.println(plan.size());
                                }

                                if (!plan.isEmpty()) {

                                    //System.out.println("Plan size: " + plan.size());

                                    Direction d = plan.poll();
                                    //debug("Next move: %s", d);

                                    timestep++;

                                    // does the plan even check if it is a wall..?
                                    //System.out.println("Current position: " + position.getX() + ", " + position.getY());
                                    boolean canMove = false;
                                    if (d != org.grid.protocol.Message.Direction.NONE) {
                                        if (d == org.grid.protocol.Message.Direction.LEFT) {
                                            canMove = positionPossible(state.neighborhood, position.getX() - 1, position.getY());
                                            if (canMove) {
                                                //System.out.println("Moving to: " + (position.getX() - 1) + ", " + position.getY());
                                            }
                                        }
                                        if (d == org.grid.protocol.Message.Direction.RIGHT) {
                                            canMove = positionPossible(state.neighborhood, position.getX() + 1, position.getY());
                                            if (canMove) {
                                                //System.out.println("Moving to: " + (position.getX() + 1) + ", " + position.getY());
                                            }
                                        }
                                        if (d == org.grid.protocol.Message.Direction.UP) {
                                            canMove = positionPossible(state.neighborhood, position.getX(), position.getY() - 1);
                                            if (canMove) {
                                                //System.out.println("Moving to: " + (position.getX()) + ", " + (position.getY() - 1));
                                            }
                                        }
                                        if (d == org.grid.protocol.Message.Direction.DOWN) {
                                            canMove = positionPossible(state.neighborhood, position.getX(), position.getY() + 1);
                                            if (canMove) {
                                                //System.out.println("Moving to: " + (position.getX()) + ", " + (position.getY() + 1));
                                            }
                                        }
                                        if (canMove) {
                                            move(d);
                                            // if can't move, clear plan, reset goal position
                                        } else {
                                            plan.clear();
                                            goalPosition = null;
                                            cleanMove = false;
                                        }
                                    }

                                    if (canMove) {

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

                                    }
                                    else {
                                        // reset goal position
                                        goalPosition = null;
                                        // mark end of multi move
                                        cleanMove = false;
                                    }

                                    scan(0);

                                } else {
                                    // reset goal position
                                    goalPosition = null;
                                    // mark end of multi move
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

    private Position cleanMove(int moveXleft, int moveYleft) {

        List<Direction> directions = null;

        // todo: check why position?
        LocalMap.Paths paths = map.findShortestPaths(position);

        Position p = new Position(position.getX() + moveXleft, position.getY() + moveYleft);
        System.out.println(p);

        LocalMap.Node n = map.get(p.getX(), p.getY());
        // todo: what do on error?
        if (n == null || n.getBody() == -1) {
            plan.clear();
            return null;
        }

        directions = paths.shortestPathTo(n);

        // cannot move anywhere ...
        if (directions == null) {
            directions = new Vector<Direction>();
            for (int i = 0; i < 2; i++)
                directions.add(Direction.NONE);
        }

        plan.addAll(directions);

        System.out.println(p);
        return p;
    }

    private void replan(Position p) {
        List<org.grid.protocol.Message.Direction> directions = null;

        LocalMap.Paths paths = map.findShortestPaths(position);

        LocalMap.Node n = map.get(p.getX(), p.getY());

        if (n == null || n.getBody() == -1) {
            plan.clear();
        } else {
            directions = paths.shortestPathTo(n);
        }

        if ( directions != null)
            plan.addAll(directions);

    }

    private boolean positionPossible(Neighborhood n, int x, int y) {

        if ( n.getCell(x, y) == Neighborhood.WALL ) {
            System.out.println("Position: " + x + ", " + y + " is an obstacle.");
            return false;
        } else return  true;

    }


}
