package org.grid.server;

/**
 * Created by nanorax on 10/7/14.
 */
public class ExploredCounter implements CounterListener {

    private History history;
    private VisitMap visualization;
    private ClientsPanel clientsPanel;
    private int counterValue;
    private int maxCellValue;

    public ExploredCounter(History history, VisitMap visualization, ClientsPanel clientsPanel, int counterValue, int maxCellValue)  {
        this.history = history;
        this.visualization = visualization;
        this.clientsPanel = clientsPanel;

        // todo: how to get maxCellValue?
        maxCellValue = 0;
    }

    // This method should refresh the JLabel counter in ClientsPanel
    public void refreshClientsPanel(int counterValue, int maxCellValue) {
        clientsPanel.getExploredPointsLabel().setText(String.valueOf(counterValue) + "/" + "0");
    }

    @Override
    public void discoveredPoints() {

    }

    @Override
    public void stopSimulation() {

    }
}
