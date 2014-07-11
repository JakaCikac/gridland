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
import java.io.Serializable;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;

import org.grid.server.Field.BodyPosition;

import javax.xml.bind.SchemaOutputResolver;


public class History implements Serializable, SimulationListener {

	private static final long serialVersionUID = -3631531900582757001L;
    private Set<HistoryPosition> quickSet = new HashSet<HistoryPosition>();

	public static class HistoryPosition extends BodyPosition {

		private static final long serialVersionUID = 1L;


		private int step;
		
		public HistoryPosition(BodyPosition p, int step) {
			super(p);
			this.step = step;
		}

		public int getStep() {
			return step;
		}
	}
	
	protected class AgentHistory implements Serializable {

		private static final long serialVersionUID = 1L;
		
		private Vector<HistoryPosition> history = new Vector<HistoryPosition>();
		private transient BodyPosition preprevious, previous;
        private Set<HistoryPosition> agentQuickSet = new HashSet<HistoryPosition>();
        // In case you need to display history by teams (SLOW), you need to add
        // history position points to agentQuickSet under history.add stuff.

		public void record(BodyPosition p) {
			// if position is null then add previous position
			if (p == null) {
				history.add(new HistoryPosition(previous, step-1));
                History.this.quickSet.add(new HistoryPosition(previous, step-1));


                return;
			}
			// if previous position is null then create current position and add to history
			if (previous == null) {
				previous = new BodyPosition(p);
				history.add(new HistoryPosition(p, step));
                History.this.quickSet.add(new HistoryPosition(p,step));

				return;
			}
			// set preprevious to previous and create new position for previous
			if (preprevious == null) {
				preprevious = previous;
				previous = new BodyPosition(p);
				return;
			}
			
			float pX = (((float)p.getX() + p.getOffsetX()) +
				((float)preprevious.getX() + preprevious.getOffsetX())) / 2;

			float pY = (((float)p.getY() + p.getOffsetY()) +
					((float)preprevious.getY() + preprevious.getOffsetY())) / 2;
			
			if (Math.abs(pX - (float)previous.getX() - previous.getOffsetX()) > 0.00001f ||
				Math.abs(pY - (float)previous.getY() - previous.getOffsetY()) > 0.00001f) {
			
				history.add(new HistoryPosition(previous, step-1));
                History.this.quickSet.add(new HistoryPosition(previous, step-1));


            } else {
				
				if (!p.hasOffset() && !history.isEmpty()) {
					
					if (!history.lastElement().equals(p)) {
						history.add(new HistoryPosition(p, step));
                        History.this.quickSet.add(new HistoryPosition(p, step));
                    }
				}
			}
			
			preprevious = previous;
			previous = new BodyPosition(p);
		}

        public Set<HistoryPosition> getAgentQuickSet() {
            return agentQuickSet;
        }

	}
	
	protected class TeamHistory implements Serializable {
		
		private static final long serialVersionUID = 1L;

		private Hashtable<Integer, AgentHistory> agents = new Hashtable<Integer, AgentHistory>();

		private String teamName;
		private Color teamColor;
		
		public TeamHistory(String teamName, Color teamColor) {
			this.teamName = teamName;
			this.teamColor = teamColor;
		}
		
		public void record(int id, BodyPosition p) {
		
			AgentHistory h = agents.get(id);
			
			if (h == null) {
				h = new AgentHistory();
				agents.put(id, h);
			}
			
			h.record(p);
			
			// At the moment we do not need to know the location of dead agents.
			if (p == null) {
				agents.remove(id);
			}
			
		}
		
		public String getTeamName() {
			return teamName;
		}
		
		public Color getTeamColor() {
			return teamColor;
		}
		
	}
	
	private Hashtable<String, TeamHistory> teams = new Hashtable<String, TeamHistory>();

	private transient int step = 0;
	
	public void step() {
		step++;
	}
	
	public int calculateSize() {
		
		int size = 0;
		
		for (TeamHistory th : teams.values()) {
			
			for (AgentHistory ah : th.agents.values()) 
				size += ah.history.size();

		}

		return size;
		
	}
	
	public Iterable<HistoryPosition> getAgentHistory(Team team, int id) {
		
		TeamHistory th = teams.get(team.getName());
		
		if (th == null)
			return null;
		
		AgentHistory ah = th.agents.get(id);

		if (ah == null)
			return null;
		
		return ah.history;
	}

    public Iterable<HistoryPosition> getTeamHistory(Team team) {

        TeamHistory th = teams.get(team.getName());

        if (th == null)
            return null;

        Vector<HistoryPosition> teamPoints = new Vector<HistoryPosition>();
        for (AgentHistory pah : th.agents.values()) {
            teamPoints = mergeVectors(teamPoints, pah.history);
        }
        return teamPoints;
    }

    static Vector<HistoryPosition> mergeVectors(Vector<HistoryPosition> Va, Vector<HistoryPosition> Vb) {
        Vector<HistoryPosition> merge = new Vector<HistoryPosition>();
        merge.addAll(Va);
        merge.addAll(Vb);
        return merge;
    }

    public int getExploredCount() {
        return History.this.quickSet.size();
    }

    public int getTeamExploredCount(Team team) {
        TeamHistory th = teams.get(team.getName());

        if (th == null)
            return 0;

        Set<HistoryPosition> teamTotalSet = new HashSet<HistoryPosition>();
        for (AgentHistory pah : th.agents.values()) {
            teamTotalSet.addAll(pah.getAgentQuickSet());
        }
        return teamTotalSet.size();
    }


	@Override
	public void message(Team team, int from, int to, int length) {

	}

	@Override
	public void position(Team team, Set<Dispatcher.Client> agentSet, int id, BodyPosition p) {
		TeamHistory h = teams.get(team.getName());
		
		if (h == null) {
			h = new TeamHistory(team.getName(), team.getColor());
			teams.put(team.getName(), h);
		}
		
		h.record(id, p);
	}
}
