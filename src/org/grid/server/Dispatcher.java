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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Vector;

import org.grid.protocol.Message;
import org.grid.protocol.Neighborhood;
import org.grid.protocol.ProtocolSocket;
import org.grid.protocol.Message.AcknowledgeMessage;
import org.grid.protocol.Message.MoveMessage;
import org.grid.protocol.Message.RegisterMessage;
import org.grid.protocol.Message.ScanMessage;
import org.grid.protocol.Message.SendMessage;


public class Dispatcher implements Runnable {

	public static enum Status {UNKNOWN, REGISTERED, USED}

    /**
     * Client class.
     */
	public class Client extends ProtocolSocket {

		private Status status = Status.UNKNOWN;
		private Team team;
		private Agent agent = null;
		private int totalMessages = 0, scanMessages = 0, msgMessages = 0;
		
		public Client(Socket socket) throws IOException {
			super(socket);
			listeners = new Vector<ClientListener>();
		}

        /**
         * Handle incoming message to Client
         * @param message
         */
		protected void handleMessage(Message message) {
			
			synchronized (this) {
				totalMessages++;
			}
			
			if (status == null)
				status = Status.UNKNOWN;

            // If agent's status is unknown, the agents needs to be registered as a client.
			switch (status) {

                case UNKNOWN: {
                    // check if message is a registration message
                    if (message instanceof RegisterMessage) {
                        // get team for the client from the register message
                        team = simulation.getTeam(((RegisterMessage) message).getTeam());
                        // in case there is no team specified, report and close
                        if (team == null) {
                            Main.log("Unknown team: " + ((RegisterMessage) message).getTeam());
                            // close socket
                            close();
                            return;
                        }
                        // if all goes well add clien to team and set status to registered
                        Main.log("New client joined team " + team + ": " + this);
                        team.addClient(this);
                        status = Status.REGISTERED;
                        // send a message acknowledging all went well
                        sendMessage(new Message.AcknowledgeMessage());
                    }
                    break;
                }
                // the agents needs to be acknowledged after registration
                case REGISTERED: {
                    // after acknowledging the agent set it's status to used
                    if (agent != null && (message instanceof AcknowledgeMessage)) {
                        status = Status.USED;
                    }
                    break;
                }
                case USED: {

                    if (agent == null)
                        return;

                    if (message instanceof ScanMessage) {
                        scanMessages++;
                        // scan the surrounding neighborhood
                        Neighborhood n = simulation.scanNeighborhood(neighborhoodSize, getAgent());
                        // report agent's state by sending out a StateMessage
                        sendMessage(new Message.StateMessage(getAgent().getDirection(), n));
                        return;
                    }

                    if (message instanceof SendMessage) {

                        msgMessages++;
                        // who is the message going out to
                        int to = ((SendMessage) message).getTo();
                        // check if the message is valid
                        if (((SendMessage)message).getMessage() == null || ((SendMessage)message).getMessage().length > maxMessageSize) {
                            Main.log("Message from %d to %d rejected: too long", agent.getId(), to);
                            return;
                        }
                        // TODO: after getting through Simulation class
                        simulation.message(team, agent.getId(), to, ((SendMessage)message).getMessage());

                        return;
                    }

                    if (message instanceof MoveMessage) {
                        // get agents direction
                        simulation.move(team, agent.getId(), ((MoveMessage) message).getDirection());

                        return;
                    }

                }
			}

			
		}


		public Agent getAgent() {
			return agent;
		}
		public Team getTeam() {
			return team;
		}
		
		public void setAgent(Agent agent) {
		
			if (this.agent != null) {
				status = Status.REGISTERED;
				sendMessage(new Message.TerminateMessage());
			}
			
			this.agent = agent;
			
			agent(agent);
			
			if (agent == null)
				return;
			
			sendMessage(new Message.InitializeMessage(agent.getId(), maxMessageSize, simulation.getSpeed()));
			
		}

		@Override
		protected void onTerminate() {
			
			if (team != null)
				team.removeClient(this);
			
			synchronized (clients) {
				clients.remove(this);
			}
			
			
		}
		
		public String toString() {
			
			return getRemoteAddress() + ":" + getRemotePort(); 
			
		}

        /**
         * Check the number of messages for a client and reset the counter.
         * @return total number of messages for a client
         */
		public int queryMessageCounter() {
			synchronized (this) {
				int tmp = totalMessages;
				totalMessages = 0;
				msgMessages = 0;
				scanMessages = 0;
				return tmp;
			}
		}

		private Vector<ClientListener> listeners = new Vector<ClientListener>();

		public void addListener(ClientListener listener) {
			if (listeners == null)
				listeners = new Vector<ClientListener>();
			
			synchronized (listeners) {
				listeners.add(listener);
			}
			
		}
		
		public void removeListener(ClientListener listener) {
			synchronized (listeners) {
				listeners.remove(listener);
			}
		}
		
		private void agent(Agent agent) {
			
			synchronized (listeners) {
				for (ClientListener l : listeners) {
					try {
						l.agent(this, agent);
					} catch (Exception e) {e.printStackTrace();}
					
				}
			}
		}
		
		private void transfer(int messages) {
			
			synchronized (listeners) {
				for (ClientListener l : listeners) {
					try {
						l.transfer(this, messages);
					} catch (Exception e) {e.printStackTrace();}
					
				}
			}
		}
		
		public void traffic() {
			
			synchronized (this) {
				transfer(totalMessages);
				
				totalMessages = 0;				
			}

		}
		
		public void send(int from, byte[] message) {
			if (status != Status.USED) return;
			sendMessage(new Message.ReceiveMessage(from, message));
		}
		
	}
	
	private HashSet<Client> clients = new HashSet<Client>();
	
	private ServerSocket socket;
	private Simulation simulation;
	private int maxMessageSize = 1024;
	private int neighborhoodSize = 5;

    /**
     * Dispatcher constructor.
     * @param port
     * @param simulation
     * @throws IOException
     */
	public Dispatcher(int port, Simulation simulation) throws IOException {

		socket = new ServerSocket(port);
		this.simulation = simulation;
		this.maxMessageSize = simulation.getProperty("message.size", 256);
		this.neighborhoodSize = simulation.getNeighborhoodSize();
		
	}

	@Override
	public void run() {
		
		Thread traffic = new Thread(new Runnable() {
			
			@Override
			public void run() {
				
				while (true) {
					synchronized (clients) {
						for (Client cl : clients) {
							cl.traffic();
						}
					}
				
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {

					}
				
				}
			}
		});
		traffic.setDaemon(true);
		traffic.start();

		while (true) {
			try {
				Socket sck = socket.accept();
				sck.setTcpNoDelay(true);
				synchronized (clients) {
					clients.add(new Client(sck));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

}