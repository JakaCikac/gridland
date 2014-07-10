package org.grid.server;

import java.awt.Color;
import java.util.Arrays;
import java.util.Set;

import org.grid.arena.Arena;
import org.grid.arena.SwingView.Palette;
import org.grid.server.Field.BodyPosition;
import org.grid.server.History.HistoryPosition;


public class VisitMap implements Arena, Palette, SimulationListener {

	private static Color heatPalette[];
    private int[] cells;
    private int width, height;
    private Field field;
    private Agent agent;
    private Set<Dispatcher.Client> agentSet;
    private BodyPosition lastPosition = null;
    private int neighborhoodSize;
    private boolean forTeam; // will we display history for the whole team

    // static class constructor
	static {
		heatPalette = new Color[32];
		heatPalette[0] = Color.black;
		heatPalette[1] = Color.gray;
		
		for (int i = 2; i < heatPalette.length; i++) {
			heatPalette[i] = new Color(Math.max(100, (i * 255) / heatPalette.length),
					Math.max(0, (i * 255) / heatPalette.length), 50);	
		}
	}

    /**
     * VisitMap constructor.
     * @param field
     * @param history
     * @param agentSet
     * @param neighborhoodSize
     */
    public VisitMap(Field field, History history, Set<Dispatcher.Client> agentSet, int neighborhoodSize, boolean forTeam) {
        this.field = field;
        this.width = field.getWidth();
        this.height = field.getHeight();
        this.agentSet = agentSet;
        this.forTeam = forTeam;
        this.cells = new int[width * height];
        Arrays.fill(cells, 0);

        this.neighborhoodSize = neighborhoodSize;

        agent = agentSet.iterator().next().getAgent();
        setFromHistory(history, agentSet.iterator().next().getTeam(), agentSet, forTeam);

    }

    /**
     * Clear the array.
     */
	public void clear() {
		Arrays.fill(cells, 0);
	}

    /**
     * Set
     * @param history - agent's history
     * @param team - team which the agent belongs to
     * @param agentSet - agents for which we are displaying history
     */
	private void setFromHistory(History history, Team team, Set<Dispatcher.Client> agentSet, boolean forTeam) {

        // first clear the array
		clear();
        int agent;
        // retrieve visited points recorded in agents history
        Iterable<HistoryPosition> h = null;
        if (agentSet.size() == 1) {
              agent = agentSet.iterator().next().getAgent().getId();
              h = history.getAgentHistory(team, agent);
        } else if (agentSet.size() > 1) {
              h = history.getTeamHistory(team);
        } else System.out.println("Error: No agents in agent set.");
		// if history can not be retrieved abort
		if (h == null)
			return;
		
		lastPosition = null;

		for (HistoryPosition p : h) {
			if (lastPosition == null || !p.equals(lastPosition)) {
                // fill array with points
				cells[p.getY() * width + p.getX()]++;
                // mark points in neighborhood
                markNeighborhood(p.getX(), p.getY());
            }
			lastPosition = p;
		}
	}

	@Override
	public void message(Team team, int from, int to, int length) {
	}

	@Override
	public void position(Team team, Set<Dispatcher.Client> agentSet, int id, BodyPosition p) {
        //Check if the requested refreshing agent is contained in the set.
        // If yes, refresh history and repaint.
        boolean discoveredCell = false;
        for(Dispatcher.Client c : this.agentSet) {
            if (c.getAgent().getId() == id) {
                if (lastPosition == null || !p.equals(lastPosition)) {
                    cells[p.getY() * width + p.getX()]++;
                    markNeighborhood(p.getX(), p.getY());
                }
                lastPosition = p;
                return;
            }
        }
	}

    /**
     * Mark point neighborhood x,y as visited.
     * @param x
     * @param y
     */
	private void markNeighborhood(int x, int y) {
		
		int sX = Math.max(0, x - neighborhoodSize);
		int eX = Math.min(width-1, x + neighborhoodSize);
		int sY = Math.max(0, y - neighborhoodSize);
		int eY = Math.min(height-1, y + neighborhoodSize);

		for (int j = sY; j <= eY; j++) {
			for (int i = sX; i <= eX; i++) {
				
				if (cells[j * width + i] == 0) {
					cells[j * width + i] = 1;
                }
			}
		}
	}

    private int checkDiscoveredMap() {
        int emptyCounter = 0;
        int discoveredCounter = 0;

        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == 0) emptyCounter++;
            else if (cells[i] >= 1) discoveredCounter++;
        }
        return discoveredCounter;
    }
	
	@Override
	public void step() {
	}
	

	public Agent getAgent() {
		return agent;
	}

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public static Color getHeatColor(int value) {
        return heatPalette[Math.min(heatPalette.length, value)];
    }
    @Override
    public int getBaseTile(int x, int y) {
        return cells[y * width + x];
    }

    @Override
    public Color getBodyColor(int x, int y) {
        return field.getBodyColor(x, y);
    }

    @Override
    public float getBodyOffsetX(int x, int y) {
        return field.getBodyOffsetX(x, y);
    }

    @Override
    public float getBodyOffsetY(int x, int y) {
        return field.getBodyOffsetY(x, y);
    }

    @Override
    public int getBodyTile(int x, int y) {
        return field.getBodyTile(x, y);
    }

    @Override
    public Color getColor(int tile) {

        if (tile > -1) {
            return heatPalette[Math.min(heatPalette.length-1, tile)];
        }
        return Color.gray;
    }
}
