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
package org.grid.server;

import java.util.LinkedList;

import org.grid.arena.Arena;
import org.grid.protocol.Message.Direction;
import org.grid.server.Field.Body;
import org.grid.server.Field.BodyPosition;
import org.grid.server.Field.Cell;
import org.grid.server.Team.TeamBody;


public class Agent extends TeamBody {

    /**
     * Agent can be Alive or Dead.
     */
	public static enum Status {
		ALIVE, DEAD
	}

	private int id;
	private Direction direction = Direction.NONE;
	private boolean alive = true;

    // Initialize agent's message queue
	private LinkedList<Simulation.MessageContainer> messageQueue = new LinkedList<Simulation.MessageContainer>();
	
	public Agent(Team team, int id) {
		super(Arena.TILE_AGENT, team);
		this.id = id;
	}

	public boolean move(Field arena) {

		synchronized (this) {
			if (!isAlive())
				return false;

			BodyPosition position = arena.getPosition(this);

			if (position == null)
				return false;

			float weight = 1;
			float speed = 0.1f / weight;
			
			switch (direction) {
			case DOWN:
				position.setOffsetY(position.getOffsetY() + speed);
				if (Math.abs(position.getOffsetY()) < speed) {
					position.setOffsetY(0);
					direction = Direction.NONE;
				}
				break;
			case UP:
				position.setOffsetY(position.getOffsetY() - speed);
				if (Math.abs(position.getOffsetY()) < speed) {
					position.setOffsetY(0);
					direction = Direction.NONE;
				}
				break;
			case LEFT:
				position.setOffsetX(position.getOffsetX() - speed);
				if (Math.abs(position.getOffsetX()) < speed) {
					position.setOffsetX(0);
					direction = Direction.NONE;
				}
				break;
			case RIGHT:
				position.setOffsetX(position.getOffsetX() + speed);
				if (Math.abs(position.getOffsetX()) < speed) {
					position.setOffsetX(0);
					direction = Direction.NONE;
				}
				break;
			default:
				return false;
			}

			// System.out.printf("%.1f %.1f %s\n", position.getOffsetX(),
			// position.getOffsetY(), direction);

			if (!arena.putBody(this, position)) {

				Cell c = arena.getCell(position.getX(), position.getY());

				if (c != null) {

					Body b = c.getBody();

                    if (b instanceof Agent) {
                        //System.out.println("Ran into agent " + ((Agent) b).getId());
                    }

					/*if (b instanceof Headquarters) {
						if (((Headquarters) b).getTeam() == getTeam()) {
						// Handle if instance is Headquarters
						}
					} */

                    // Hm
				/*	if (b instanceof Agent) {
						((Agent) b).die();
					} */
				}
				//die();
			}
		}
		return true;

	}

    /**
     * Set direction (Direction can either be UP, DOWN, LEFT, RIGHT).
     * @param direction
     */
	public void setDirection(Direction direction) {

		synchronized (this) {
			if (this.direction == Direction.NONE) {
				this.direction = direction;
			}

			if ((this.direction == Direction.DOWN && direction == Direction.UP)
					|| (this.direction == Direction.UP && direction == Direction.DOWN)
					|| (this.direction == Direction.LEFT && direction == Direction.RIGHT)
					|| (this.direction == Direction.RIGHT && direction == Direction.LEFT)) {
				this.direction = direction;
			}
		}
	}

    /**
     * Kill agent.
     */
	public void die() {
		alive = false;
	}

    /**
     * Return agent's id.
     * @return id of the agent
     */
    public int getId() {
        return id;
    }

    /**
     * Check if agent is alive (true or false)
     * @return alive - true or false
     */
	public boolean isAlive() {
		return alive;
	}

    /**
     * Get the direction in which the agent is currently moving in.
     * @return Direction
     */
	public Direction getDirection() {
		return direction;
	}

    /**
     * Return the agent's Tile
     * @return int
     */
	public int getTile() {
		return  Arena.TILE_AGENT;
	}

	public void pushMessage(int to, byte[] message, int delay) {
		
		synchronized (messageQueue) {
			messageQueue.add(new Simulation.MessageContainer(to, message, delay));
		}
		
	}
	
	public Simulation.MessageContainer pullMessage() {
		
		synchronized (messageQueue) {
		
			Simulation.MessageContainer m = messageQueue.peek();
			
			if (m == null) return null;
			
			if (m.decreaseDelay() > 0) return null;
			
			return messageQueue.poll();
			
		}
		
	}
	
}
