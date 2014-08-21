package org.grid.agent.sample;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Created by nanorax on 20/08/14.
 */
public class AgentSolution implements Serializable {
    private int id;

    public double getSolution() {
        return solution;
    }

    public int getId() {
        return id;
    }

    private double solution;

    public AgentSolution(int id, double solution) {
        this.id = id;
        this.solution = solution;
    }

}
