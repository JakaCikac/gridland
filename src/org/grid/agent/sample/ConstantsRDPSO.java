package org.grid.agent.sample;

/**
 * Created by nanorax on 08/08/14.
 */
public class ConstantsRDPSO {
            /* number of swarms */

    // For 1 swarm + exclusion group, should be 1
    static final int MIN_SWARMS = 1; // minimum number of swarms (0, to allow social exclusion of all agents)
    // for 3 swarms + socially excluded, set the number to 4
    static final int INIT_SWARMS = 4; // 0 = socially excluded, 1,2,3..,n-1 are swarms
    static final int MAX_SWARMS = INIT_SWARMS-1; // maximum number of swarms, including social exclusion group

    // set the number of agents to INIT_SWARMS * INIT_AGENTS

        /* number of agents in each swarm */

    static final int INIT_AGENTS = 3; // initial number of agents in each swarm
    static final int MAX_AGENTS = INIT_AGENTS; // maximum number of agents in each swarm
    static final int MIN_AGENTS = 1;  // minimum number of agents in each swarm

    /* RDPSO coefficients */
    static final double W = 0.6;   // inertial coefficient (fract)
    static final double C1 = 0.4;  // cognitive weight (pc)
    static final double C2 = 0.4; // social weight (ps)
    static final double C3 = 0.2;  // obstacle suspectibility weight (pobs)

    static final int SC_MAX = 2;     // maximum number of iterations without improving the swarm

}
