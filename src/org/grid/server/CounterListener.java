package org.grid.server;

import java.util.Set;

/**
 * Created by nanorax on 9/7/14.
 */

/* listener for counting explored points in a map and also stopping the simulation
   when the whole map is explored */
public interface CounterListener {

    public void discoveredPoints();

    public void stopSimulation();

}
