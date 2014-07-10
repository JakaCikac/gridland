package org.grid.server;

import java.util.Set;

/**
 * Created by nanorax on 9/7/14.
 */
public interface CounterListener {

    public void discoveredPoints(History history, VisitMap visualization, ClientsPanel clientsPanel);

}
