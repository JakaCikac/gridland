package org.grid.agent.sample;

import java.util.Comparator;

/**
 * Created by nanorax on 20/08/14.
 */
public class AgentSolutionCompMax implements Comparator<AgentSolution> {

    public int compare(AgentSolution a, AgentSolution b) {
        // highest value first
        if (a.getSolution() > b.getSolution()) return 1;

        if (a.getSolution() == b.getSolution()) return 0;

        return -1;
    }
}
