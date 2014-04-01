package org.grid.server;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.StringTokenizer;

public class TeamDatabase {

	public static class TeamData {

        // give the team an id and a name
		private String id, name;
		// select the color representing the team
		private Color color;

		protected TeamData(String id, String name, Color color) {
			super();
			this.id = id;
			this.name = name;
			this.color = color;
		}

		public String getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		public Color getColor() {
			return color;
		}

	}
	
	private Hashtable<String, TeamData> data = new Hashtable<String, TeamData>();

    // Read team database from a file
	public TeamDatabase(File file) throws IOException {
		
		BufferedReader reader = new BufferedReader(new FileReader(file));
		
		while (true) {
			
			String line = reader.readLine();
			
			if (line == null) break;
			
			StringTokenizer tokens = new StringTokenizer(line, ";");
			
			String id = tokens.nextToken();
			String name = tokens.nextToken();
			Color color = Color.decode(tokens.nextToken());

            // create new team d
			TeamData d = new TeamData(id, name, color);
            // add team to hashtable
			data.put(d.getId(), d);
			
		}
		
	}

    /** Retrieve a team from the database, based on team id
     ** and create it
     **/
	public Team createTeam(String id) {
        // get team from hashtable
		TeamData d = data.get(id);

		// if team doesn't exist return null
		if (d == null) return null;

		// else create a team and return it
		Team team = new Team(d.getId(), d.getColor());
		return team;
		
	}

    // print out every team in database info
	public void print(PrintStream out) {
		
		for (String id : data.keySet()) {
			
			TeamData t = data.get(id);
			
			out.format("----- \nID: %s\nName: %s\n-----\n", t.getId(), t.getName());
			
		}
		
	}
	
}
