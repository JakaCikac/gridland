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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.swing.*;

import org.grid.arena.Arena;
import org.grid.server.History.HistoryPosition;
import org.grid.arena.SwingView;
import org.grid.server.ClientsPanel.SelectionObserver;
import org.grid.server.Dispatcher.Client;
import org.grid.server.Field.Body;
import org.grid.server.Field.BodyPosition;
import org.grid.server.Field.Cell;


public class Main {

	private static final int PORT = 5000;
	private static final String RELEASE = "0.9";
	private static Simulation simulation;
	private static long renderTime = 0;
	private static int renderCount = 0;
	private static long stepTime = 0;
	private static int stepCount = 0;
	private static Object mutex = new Object();
	private static History history = new History();
	private static boolean running = false;
	private static SimulationSwingView view = new SimulationSwingView();
	private static ClientsPanel clientsPanel = null;
	private static JLabel simulationStepDisplay = new JLabel();
	private static PrintWriter log;
    private static List emptyFieldsList;

	private static final String[] ZOOM_LEVELS_TITLES = new String[] {"nano", "micro", "mili", "tiny", "small", "normal",
			"big", "huge" };

	private static final int[] ZOOM_LEVELS = new int[] {1, 2, 3, 6, 12, 16, 20, 24 };
	
	private static final int MAX_TEAMS_VERBOSE = 4;
	
	private static Action playpause = new AbstractAction("Play") {

		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent arg0) {

			running = !running;

			setEnabled(false);
			
			//putValue(AbstractAction.NAME, running ? "Pause" : "Play");
		}
	};

	private static class SimulationSwingView extends SwingView implements
			SimulationListener, SelectionObserver, MouseListener, CounterListener {

		private static final long serialVersionUID = 1L;
		private static final int BUFFER_LIFE = 10;
		private LinkedList<Message> buffer = new LinkedList<Message>();
		private VisitMap visualization = null;

        public class Message {

			private int length, step;
			private Agent sender, receiver;

			public Message(Agent sender, Agent receiver, int length) {
				super();
				this.sender = sender;
				this.receiver = receiver;
				this.length = length;
				this.step = simulation.getStep();
			}
		}

		public SimulationSwingView() {
			super(12);
			addMouseListener(this);
		}

        @Override
        public void discoveredPoints(History history, VisitMap visualization, ClientsPanel clientsPanel) {
            // TODO: move to separate class or put into main refresh method
            Iterable<HistoryPosition> teamPoints = history.getTeamHistory(visualization.getAgent().getTeam());
            Set< HistoryPosition> unique = new HashSet<HistoryPosition>();
            for (HistoryPosition hp : teamPoints) {
                unique.add(hp);
            }
            int exploredPoints = unique.size();
            clientsPanel.getExploredPointsLabel().setText(String.valueOf(exploredPoints));
        }

		@Override
		public void paint(Graphics g) {

			long start = System.currentTimeMillis();

			Arena view = getArena();

			paintBackground(g, visualization == null ? view : visualization);

			paintObjects(g, view);

			LinkedList<Message> active = new LinkedList<Message>();
			int current = simulation.getStep();

			synchronized (buffer) {

				for (Message m : buffer) {

					if (current - m.step < BUFFER_LIFE)
						active.add(m);
				}
			}

			Field field = simulation.getField();



			g.setColor(Color.YELLOW);

			for (Message m : active) {

				BodyPosition p1 = field.getPosition(m.sender);
				BodyPosition p2 = field.getPosition(m.receiver);

				if (p1 == null || p2 == null)
					continue;

				int x1 = (int) ((p1.getX() + p1.getOffsetX()) * cellSize)
						+ cellSize / 2;
				int y1 = (int) ((p1.getY() + p1.getOffsetY()) * cellSize)
						+ cellSize / 2;
				int x2 = (int) ((p2.getX() + p2.getOffsetX()) * cellSize)
						+ cellSize / 2;
				int y2 = (int) ((p2.getY() + p2.getOffsetY()) * cellSize)
						+ cellSize / 2;

				g.drawLine(x1, y1, x2, y2);

				float progress = (float) (current - m.step) / BUFFER_LIFE;

				int size = Math.min(8, Math.max(3, m.length / cellSize));

				g.fillRect((int) ((1 - progress) * x1 + progress * x2) - size
						/ 2, (int) ((1 - progress) * y1 + progress * y2) - size
						/ 2, size, size);

			}

			 if (visualization != null) {

				BodyPosition p = field.getPosition(visualization.getAgent());
				
				if (p != null) {
					
					int translateX = (int) (p.getOffsetX() * cellSize);
					int translateY = (int) (p.getOffsetY() * cellSize);
					
					g.drawOval(p.getX() * cellSize + translateX, p.getY() * cellSize
							 + translateY, cellSize, cellSize);
				}

                 // refresh discovered points
                 discoveredPoints(history, visualization, clientsPanel);
             }

			synchronized (buffer) {
				buffer = active;
			}

			long used = System.currentTimeMillis() - start;

			synchronized (mutex) {
				renderTime += used;
				renderCount++;
            }
		}


		@Override
		public void message(Team team, int from, int to, int length) {
			synchronized (buffer) {
				try {
					Agent sender = team.findById(from).getAgent();
					Agent receiver = team.findById(to).getAgent();
					if (sender == null || receiver == null)
						return;
					buffer.add(new Message(sender, receiver, length));
				} catch (NullPointerException e) {
				}
			}
		}

		@Override
		public void clientSelected(Client client) {

			 synchronized (this) {
				if (client == null) {
					if (visualization != null)
						simulation.removeListener(visualization);
					visualization = null;
					setBasePallette(null);
					return;
				}

                Set<Client> agentSet = new HashSet<Client>();
                agentSet.add(client);
                // On selected client try to get agent
				Agent a = client.getAgent();
				if (a == null)
					return;

                // create new visited map for the selected agent, based on his history
				visualization = new VisitMap(simulation.getField(), history, agentSet, simulation
						.getNeighborhoodSize(), false); // false = single agent
				setBasePallette((Palette) visualization);
				simulation.addListener(visualization);
			}
		}

        /**
         * Takes an arrayList of clients and creates a visuzalization
         * of history for all the clients in the list (usually a team).
         * @param client
         */
        @Override
        public void clientsSelected(ArrayList<Client> client) {

            synchronized (this) {
                if (client == null) {
                    if (visualization != null)
                        simulation.removeListener(visualization);
                    visualization = null;
                    setBasePallette(null);
                    return;
                }

                if (!(client.size() > 0)) {
                    // If there are no active clients, do nothing
                    System.out.println("No active clients.");
                    return;
                }

                Set<Client> agentSet = new HashSet<Client>(client);

                // create new visited map for the selected agent, based on his history
                // the agent a's history will be refreshed, so we need to send in a list
                visualization = new VisitMap(simulation.getField(), history, agentSet, simulation
                        .getNeighborhoodSize(), true); // true = whole team
                setBasePallette((Palette) visualization);
                simulation.addListener(visualization);

                // TODO: empty field count
              // System.out.println("Empty tiles: " + simulation.getField().listEmptyFields(true).size());
            }
        }

		@Override
		public void position(Team team, Set<Client> agentSet, int id, BodyPosition p) {
		}

		@Override
		public void step() {
		}

		@Override
		public void mouseClicked(MouseEvent e) {
			
			int x = e.getX() / cellSize;
			int y = e.getY() / cellSize;
			
			Field field = simulation.getField();
			
			Cell cell = field.getCell(x, y);
			
			if (cell != null) {
				Body b = cell.getBody();
				
				if (b instanceof Agent) {
					
					Client cl = ((Agent)b).getTeam().findById(((Agent)b).getId());
					
					if (cl != null && Main.clientsPanel != null) {
						Main.clientsPanel.select(cl);
					}
				}
			}
		}

		@Override
		public void mouseEntered(MouseEvent e) {}
		@Override
		public void mouseExited(MouseEvent e) {}
		@Override
		public void mousePressed(MouseEvent e) {}
		@Override
		public void mouseReleased(MouseEvent e) {}

	}

	public static void main(String[] args) throws IOException {
		
		info("Starting simulation server (release %s)", RELEASE);

        // Warn and exit if no simulation file is provided as an argument
		if (args.length < 1) {
			info("Please provide simulation description file location as an argument.");
			System.exit(1);
		}

        // Enable opengl hardware acceleration
        System.setProperty("sun.java2d.opengl","True");
        // Log to console if opengl enabled
		info("Java2D OpenGL acceleration "
                + (("true".equalsIgnoreCase(System
                .getProperty("sun.java2d.opengl"))) ? "enabled"
                : "not enabled"));

        // Load simulation properties from a simulation file, which is given as an argument
		simulation = Simulation.loadFromFile(new File(args[0]));
        emptyFieldsList = simulation.getField().listEmptyFields(true);

        // Create a log file with timestamp as name
		try {
			log = new PrintWriter(new File(logDate.format(new Date()) + "_" + simulation.getTitle() + ".log"));
			
		} catch (Exception e) {}
		
		
		Dispatcher dispatcher = new Dispatcher(PORT, simulation);

		final int simulationSpeed = simulation.getSpeed();

		simulation.addListener(view);
		simulation.addListener(history);
		
		(new Thread(new Runnable() {

			@Override
			public void run() {
				int sleep = 1000 / simulationSpeed;
				long start, used;
				while (true) {

					start = System.currentTimeMillis();

					if (running)
						simulation.step();

					view.update(simulation.getField());

					used = System.currentTimeMillis() - start;

					stepTime += used;
					stepCount++;


					if (simulation.getStep() % 400 == 0 && running) {
						long renderFPS, stepFPS;

						synchronized (mutex) {
							renderFPS = (renderCount * 1000)
									/ Math.max(1, renderTime);
							renderCount = 0;
							renderTime = 0;
						}

						stepFPS = (stepCount * 1000) / Math.max(1, stepTime);
						stepCount = 0;
						stepTime = 0;

						info("Simulation step: %d (step: %d fps, render: %d fps)", simulation.getStep(), stepFPS, renderFPS);
					}

					if (simulation.getStep() % 10 == 0) {
						simulationStepDisplay.setText(String.format("Step: %d", simulation.getStep()));
					}
					
					try {
						if (used < sleep)
							Thread.sleep(sleep - used);
						else {
							info("Warning: low frame rate");
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}

				}

			}
		})).start();

		JFrame window = new JFrame("AgentField - " + simulation.getTitle());

		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		final JScrollPane pane = new JScrollPane(view);

		JPanel left = new JPanel(new BorderLayout());

		left.add(pane, BorderLayout.CENTER);

		JPanel status = new JPanel(new BorderLayout());

		status.add(new JButton(playpause), BorderLayout.WEST);

		final JComboBox zoom = new JComboBox(ZOOM_LEVELS_TITLES);

		zoom.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				int ind = zoom.getSelectedIndex();
				
				if (ind > -1) {
					view.setCellSize(ZOOM_LEVELS[ind]);
					pane.repaint();
				}
			}
		});

        // Set the default zoom level
		zoom.setSelectedIndex(4);
		zoom.setEditable(false);
		
		status.add(zoom, BorderLayout.EAST);
		
		simulationStepDisplay.setHorizontalAlignment(JLabel.CENTER);
		status.add(simulationStepDisplay, BorderLayout.CENTER);
		
		left.add(status, BorderLayout.NORTH);

		if (simulation.getTeams().size() > MAX_TEAMS_VERBOSE) {
			
			log("Warning: too many teams, reducing the GUI");
			
			window.getContentPane().add(left);
			
		} else {
			
			clientsPanel = new ClientsPanel(simulation, view);
			window.getContentPane().add(
					new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left,
							clientsPanel));
			
		}
		
		GraphicsEnvironment ge = GraphicsEnvironment
				.getLocalGraphicsEnvironment();

		Rectangle r = ge.getDefaultScreenDevice().getDefaultConfiguration()
				.getBounds();

		window.pack();

		Dimension ws = window.getSize();
		
		if (r.width - ws.width < 100) {
			ws.width = r.width - 100;
		}
		
		if (r.height - ws.height < 100) {
			ws.height = r.height - 100;
		}

        // TODO: change size, only for testing
        ws.height = 850;
        ws.width = 1250;
		
		window.setSize(ws);
		
		window.setVisible(true);

		(new Thread(dispatcher)).start();
		
		log("Server ready.");
		
	}
	
	private static DateFormat logDate = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
	
	private static DateFormat date = new SimpleDateFormat("[hh:mm:ss] ");
	
	public static void log(String format, Object ... objects) {
		
		try {
		
			String msg = String.format(format, objects);
			
			System.out.println(date.format(new Date()) + msg);

            // Check if log file exists and print to file
			if (log != null) {
				log.println(date.format(new Date()) + msg);
				log.flush();
			}
			
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	public static void info(String format, Object ... objects) {
		
		System.out.println(date.format(new Date()) + String.format(format, objects));
		
	}
	
}