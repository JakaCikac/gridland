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
package org.grid.protocol;

import java.io.Serializable;

public abstract class Message implements Serializable {

    // Four possible directions and none.
	public static enum Direction {NONE, UP, DOWN, LEFT, RIGHT}
	
	private static final long serialVersionUID = 1L;

    /** Retrieve message type (Message, RegisterMessage, AckMessage, InitMessage,
     * TerminateMessage, ScanMessage, StateMessage, MoveMessage, SendMessage,
     * ReceiveMessage
    */
	public String toString() {
		return getClass().getSimpleName();
	}
	
	public static class RegisterMessage extends Message {

		private static final long serialVersionUID = 1L;
        private String team;

        // Register message to a team
        public RegisterMessage(String team) {
			this.team = team;
		}

        // Get and set team name
		public String getTeam() {
			return team;
		}
		public void setTeam(String team) {
			this.team = team;
		}
	}

    /**
     * AcknowledgeMessage
     */
	public static class AcknowledgeMessage extends Message {

		private static final long serialVersionUID = 1L;
		
	}

    /**
     * InitializeMessage
     * int id, int maxMessageSize, int simulationSpeed
     */
	public static class InitializeMessage extends Message {

		private static final long serialVersionUID = 1L;
		
		private int id;
		private int maxMessageSize;
		private int simulationSpeed;

        public InitializeMessage(int id, int maxMessageSize, int simulationSpeed) {
            super();
            this.id = id;
            this.maxMessageSize = maxMessageSize;
            this.simulationSpeed = simulationSpeed;
        }

        // Getters (Id, MaxMessageSize, SimulationSpeed)
		public int getId() {
			return id;
		}
        public int getMaxMessageSize() {
            return maxMessageSize;
        }
        public int getSimulationSpeed() {
            return simulationSpeed;
        }

        // Setters (Id, MaxMessageSize, SimulationSpeed)
        public void setId(int id) {
            this.id = id;
        }
        public void setMaxMessageSize(int maxMessageSize) {
			this.maxMessageSize = maxMessageSize;
		}
		public void setSimulationSpeed(int simulationSpeed) {
			this.simulationSpeed = simulationSpeed;
		}
	}

    /**
     * TerminateMessage
     */
	public static class TerminateMessage extends Message {

		private static final long serialVersionUID = 1L;
		
	}

    /**
     * ScanMessage
     * int stamp
     */
	public static class ScanMessage extends Message {

		private static final long serialVersionUID = 1L;

        private int stamp;

		public ScanMessage(int stamp) {
			super();
			this.stamp = stamp;
		}

		public int getStamp() {
			return stamp;
		}
		public void setStamp(int stamp) {
			this.stamp = stamp;
		}
	}

    /**
     * StateMessage
     * int stamp, Direction direction, Neighborhood neighborhood
     */
	public static class StateMessage extends Message {

        private static final long serialVersionUID = 1L;

		private int stamp;
        private Direction direction;
        private Neighborhood neighborhood;

        public StateMessage(Direction direction, Neighborhood neighborhood) {
            super();
            this.direction = direction;
            this.neighborhood = neighborhood;
        }

        // Getters: stamp, direction, neighborhood
		public int getStamp() {
			return stamp;
		}
        public Direction getDirection() {
            return direction;
        }
        public Neighborhood getNeighborhood() {
            return neighborhood;
        }

        // Setters: stamp, direction
		public void setStamp(int stamp) {
			this.stamp = stamp;
		}
        public void setDirection(Direction direction) {
            this.direction = direction;
        }
	}

    /**
     * MoveMessage
     * Direction direction
     */
	public static class MoveMessage extends Message {

        private static final long serialVersionUID = 1L;

		private Direction direction;

        public MoveMessage(Direction direction) {
            super();
            this.direction = direction;
        }

		public Direction getDirection() {
			return direction;
		}
		public void setDirection(Direction direction) {
			this.direction = direction;
		}
	}

    /**
     * SendMessage
     * int to, byte[] message
     */
	public static class SendMessage extends Message {

		private static final long serialVersionUID = 1L;
	
		private int to;
		private byte[] message;

		public SendMessage(int to, byte[] message) {
			super();
			this.to = to;
			this.message = message;
		}

        // Getters: to, message
		public int getTo() {
			return to;
		}
		public byte[] getMessage() {
			return message;
		}

        // Setters: to, message
        public void setTo(int to) {
            this.to = to;
        }
		public void setMessage(byte[] message) {
			this.message = message;
		}
		
	}

    /**
     * ReceiveMessage
     * int from, byte[] message
     */
	public static class ReceiveMessage extends Message {

		private static final long serialVersionUID = 1L;
		
		private int from;
		private byte[] message;

		public ReceiveMessage(int from, byte[] message) {
			super();
			this.from = from;
			this.message = message;
		}

        // Getters: from, message
		public int getFrom() {
			return from;
		}
		public byte[] getMessage() {
			return message;
		}

        // Setters: message, from
		public void setMessage(byte[] message) {
			this.message = message;
		}
        public void setFrom(int from) {
            this.from = from;
        }

	}
}
