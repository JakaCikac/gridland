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

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import org.grid.protocol.Neighborhood;
import org.grid.protocol.Message.Direction;
import org.grid.server.Dispatcher.Client;
import org.grid.server.Field.BodyPosition;
import org.grid.server.Field.Cell;
import org.grid.server.Field.Wall;
import org.grid.server.Team.Headquarters;
import org.grid.server.Team.TeamBody;


public class Simulation {

	public static class MessageContainter {
		
		private int to;

		private byte[] message;

		private int delay;
		
		public MessageContainter(int to, byte[] message, int delay) {
			super();
			this.to = to;
			this.message = message;
			this.delay = delay;
		}

		public int getTo() {
			return to;
		}

		public byte[] getMessage() {
			return message;
		}

		public int decreaseDelay() {
			return delay--;
		}
		
	}

    // default simulation parameters
	private int spawnFrequency = 10;

	private Field field;

	private HashMap<String, Team> teams = new HashMap<String, Team>();

	private int maxAgentsPerTeam = 10;

	private int neighborhoodSize = 10;

	private int messageSpeed = 10;
	
	private Properties properties = null;

	private File simulationSource;

	private Vector<SimulationListener> listeners = new Vector<SimulationListener>();

	private static final Color[] colors = new Color[] { Color.red, Color.blue,
			Color.green, Color.yellow, Color.pink, Color.orange, Color.black,
			Color.white };

	/**
	 * Computes L_inf norm for distance between two agents
	 * 
	 * @param a1
	 *            agent 1
	 * @param a2
	 *            agent 2
	 * @return distance (maximum of X or Y difference)
	 */
	private int distance(Agent a1, Agent a2) {

		if (a1 == null || a2 == null)
			return -1;

		BodyPosition bp1 = field.getPosition(a1);
		BodyPosition bp2 = field.getPosition(a2);

		return Math.max(Math.abs(bp1.getX() - bp2.getX()), Math.abs(bp1.getY()
				- bp2.getY()));
	}

	private Simulation() throws IOException {

	}

	public Team getTeam(String id) {

		if (id == null)
			return null;

		return teams.get(id);

	}

	public List<Team> getTeams() {

		return new Vector<Team>(teams.values());

	}

	public static Simulation loadFromFile(File f) throws IOException {

		Simulation simulation = new Simulation();

		simulation.properties = new Properties();

		simulation.properties.load(new FileReader(f));

		simulation.simulationSource = f;

		String tdbPath = simulation.getProperty("teams", null);

		TeamDatabase database = null;
		
		if (tdbPath != null) {
		
			File tdbFile = new File(tdbPath);
	
			if (!tdbPath.startsWith(File.separator)) {
				tdbFile = new File(f.getParentFile(), tdbPath);
			}
			try {
				database = new TeamDatabase(tdbFile);
			} catch (IOException e) {
				Main.log("Unable to load team database: %s", e.toString());
			}
		}
		
		int index = 0;

		while (true) {

			index++;

			String id = simulation.properties.getProperty("team" + index);

			if (id == null)
				break;

			if (database == null) {
				simulation.teams.put(id, new Team(id, colors[index - 1]));
			} else {
				Team team = database.createTeam(id);
				if (team == null) break;
				simulation.teams.put(id, team);
			}

			Main.log("Registered team: " + id);

		}

		String fldPath = simulation.getProperty("simulation.field", f.getAbsolutePath()
				+ ".field");
        System.out.println(fldPath);

		File fldFile = new File(fldPath);

		if (!fldPath.startsWith(File.separator)) {
			fldFile = new File(f.getParentFile(), fldPath);
		}

		simulation.spawnFrequency = simulation.getProperty("simulation.respawn", 30);

		simulation.maxAgentsPerTeam = simulation.getProperty("simulation.agents", 10);

		simulation.neighborhoodSize = simulation.getProperty("message.neighborhood", 5);

		simulation.messageSpeed = simulation.getProperty("message.speed", 10);

		simulation.field = Field.loadFromFile(fldFile, simulation);

		return simulation;

	}

	public Field getField() {

		return field;

	}

	private int spawnCounter = 1;

	private int step = 0;

	public synchronized void step() {

		step++;

		fireStepEvent();
		
		// handle moves and collisions
		for (Team t : teams.values()) {
			for (Agent a : t.move(field)) {
				
				synchronized (listeners) {
					for (SimulationListener l : listeners) {
						try {
							l.position(t, a.getId(), field.getPosition(a));
						} catch (Exception e) {
							e.printStackTrace();
						}

					}
				}
				
			}
			
			t.dispatch();
		}

		// spawn new agents
		spawnCounter--;
		if (spawnCounter == 0) {
			spawnNewAgents();
			spawnCounter = spawnFrequency;
		}

		// remove dead agents
		for (Team t : teams.values()) {
			t.cleanup(field);
		}

		// check end conditions?
		// TODO

	}

	private void spawnNewAgents() {

		for (Team t : teams.values()) {

			if (t.size() < maxAgentsPerTeam) {

				BodyPosition pos = field.getPosition(t.getHeadquarters());

				if (pos == null)
					continue;

				Collection<Cell> cells = field.getNeighborhood(pos.getX(), pos
						.getY());

				for (Cell c : cells) {

					if (!c.isEmpty())
						continue;

					Agent agt = t.newAgent();

					if (agt == null)
						break;

					field.putBody(agt, new BodyPosition(c.getPosition(), 0, 0));

					break;
				}

			}

		}

	}

	public Neighborhood scanNeighborhood(int size, Agent agent) {

		Neighborhood n = new Neighborhood(size);

		BodyPosition bp = field.getPosition(agent);

		if (bp == null)
			return null;

		for (int j = -size; j <= size; j++) {
			for (int i = -size; i <= size; i++) {

				Cell c = field.getCell(bp.getX() + i, bp.getY() + j);

				if (c == null) {
					n.setCell(i, j, Neighborhood.WALL);
					continue;
				}

				if (c.isEmpty()) {
					n.setCell(i, j, Neighborhood.EMPTY);
					continue;
				}

				if (c.getBody() instanceof Wall) {
					n.setCell(i, j, Neighborhood.WALL);
					continue;
				}

				if (c.getBody() instanceof TeamBody) {

					Team t = ((TeamBody) c.getBody()).getTeam();

					if (c.getBody() instanceof Headquarters) {
						n
								.setCell(
										i,
										j,
										t == agent.getTeam() ? Neighborhood.HEADQUARTERS
												: Neighborhood.OTHER_HEADQUARTERS);
						continue;
					}

					if (c.getBody() instanceof Agent) {

						n.setCell(i, j, t == agent.getTeam() ? ((Agent) c
								.getBody()).getId() : Neighborhood.OTHER);
						continue;
					}
				}
			}
		}

		return n;

	}

	public int getProperty(String key, int def) {

		try {
			return Integer.parseInt(properties.getProperty(key));
		} catch (Exception e) {
			return def;
		}

	}

	public boolean getProperty(String key, boolean def) {

		try {
			return Boolean.parseBoolean(properties.getProperty(key));
		} catch (Exception e) {
			return def;
		}

	}
	
	public String getProperty(String key, String def) {

		if (properties.getProperty(key) == null)
			return def;

		return properties.getProperty(key);

	}

	public float getProperty(String key, float def) {

		if (properties.getProperty(key) == null)
			return def;

		return Float.parseFloat(properties.getProperty(key));

	}

	
	public String getTitle() {
		String title = properties.getProperty("title");

		if (title == null)
			title = simulationSource.getName();
		
		return title;
	}

	public int getStep() {
		return step;
	}

	public void addListener(SimulationListener listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}

	}

	public void removeListener(SimulationListener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}

	public synchronized void message(Team team, int from, int to, byte[] message) {
		Client cltto = team.findById(to);
		Client cltfrom = team.findById(from);

		if (from == to) {
			Main.log("Message from %d to %d rejected: same agent",
					from, to);
			return;
		}

		if (cltto != null && cltfrom != null) {

			int dst = distance(cltfrom.getAgent(), cltto.getAgent());
			if (dst > neighborhoodSize || dst < 0) {
				Main.log(
						"Message from %d to %d rejected: too far away", from,
						to);
				return;
			}

			cltfrom.getAgent().pushMessage(to, message, message.length / messageSpeed);

		} else
			return;

		synchronized (listeners) {
			for (SimulationListener l : listeners) {
				try {
					l.message(team, from, to, message.length);
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		}
	}

	private void fireStepEvent() {
		
		synchronized (listeners) {
			for (SimulationListener l : listeners) {
				try {
					l.step();
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		}
		
	}
	
	public synchronized void move(Team team, int agent, Direction direction) {

		Client clt = team.findById(agent);

		if (clt != null && clt.getAgent() != null) {
			clt.getAgent().setDirection(direction);
		}

	}

	public int getSpeed() {
		return getProperty("simulation.speed", 10);
	}

	public int getNeighborhoodSize() {
		return neighborhoodSize;
	}
}
