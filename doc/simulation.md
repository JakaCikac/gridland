Game creation
============

Each game is defined as a set of properties, defined in a `.sim`
file that is actually an INI-like file.

simulation options
--------------

 * `simulation.speed` - The speed of the simulation (optional, default 10)
 * `simulation.field` - The map file to load (required), see maps.txt
 * `simulation.agents` - The maximum number of agents per team (optional, default 10)
 * `simulation.respawn` - The number of timesteps between respawn phases (optional, default 30)

Protocol options
--------------

 * `message.size` - Maximum personal message size in bytes (optional, default 256)
 * message.neighborhood - The size of the neighborhood scan
    For size N, the entire neighborhood has width and height (N * 2 +1)
 * `message.speed` - The personal message transfer speed. Not that this is all game emulation stuff. An integer
    number means the number of bytes per game step. The messages are queued on the sender side for the sufficient 
    number of steps and then transmitted to the receiver.

Teams
----

For each active team it is necessary so specify an id of the team as a property.
The key of the property is `team{N}`, where `N` is an integer number starting with 1.
The value of the property is the id of the team that is used to identify the clients.

Example:

> team1=foo
> team2=bar


A more sophisticated usage involves a team database that defines id, name, passphrase and
color for all teams.

Misc options
-----------

 * `title` - the title of the simulation, visible in server window

