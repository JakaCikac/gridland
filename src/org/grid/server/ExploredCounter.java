package org.grid.server;

/**
 * Created by nanorax on 10/7/14.
 */
public class ExploredCounter {

    private History history;
    private VisitMap visualization;
    private ClientsPanel clientsPanel;

    public ExploredCounter(History history, VisitMap visualization, ClientsPanel clientsPanel) {
        this.history = history;
        this.visualization = visualization;
        this.clientsPanel = clientsPanel;
    }

}
