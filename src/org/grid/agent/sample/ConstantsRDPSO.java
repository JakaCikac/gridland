package org.grid.agent.sample;

/**
 * Created by nanorax on 08/08/14.
 */
public class ConstantsRDPSO {
            /* number of swarms */

    static final int MAX_SWARMS = 5; // maximum number of swarms, including social exclusion group
    static final int MIN_SWARMS = 1; // minimum number of swarms (0, to allow social exclusion of all agents)
    // for 3 swarms + socially excluded, set the number to 4
    static final int INIT_SWARMS = 4; // 0 = socially excluded, 1,2,3..,n-1 are swarms

    // set the number of agents to INIT_SWARMS * INIT_AGENTS

        /* number of agents in each swarm */

    static final int INIT_AGENTS = 3; // initial number of agents in each swarm
    static final int MAX_AGENTS = 5; // maximum number of agents in each swarm
    static final int MIN_AGENTS = 1;  // minimum number of agents in each swarm

    /* RDPSO coefficients */
    static final double W = 0.6;   // inertial coefficient (fract)
    static final double C1 = 0.8;  // cognitive weight (pc)
    static final double C2 = 0.04; // social weight (ps)
    static final double C3 = 0.2;  // obstacle suspectibility weight (pobs)

    static final int SC_MAX = 15;     // maximum number of iterations without improving the swarm

}
