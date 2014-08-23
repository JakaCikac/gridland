package org.grid.agent.sample;

import java.util.*;

/**
 * Created by nanorax on 11/08/14.
 *
 * A utility class to merge solutions of different agents in the same swarm.
 */
public class SwarmSolution {


    // I realize this is probably the slowest way to do this, but I ran out of ideas, it gets too complex really quick.
    public static synchronized ArrayList<AgentSolution> mergeSolutionToArray(AgentSolution a, ArrayList<AgentSolution> swarmSolutionArray) {

        boolean exists = false;
        for (int i = 0; i < swarmSolutionArray.size(); i++) {
            if (swarmSolutionArray.get(i).getId() == a.getId()) {
                swarmSolutionArray.set(i, a);
                exists = true;
            }
        }
        if (!exists)
            swarmSolutionArray.add(a);

        return swarmSolutionArray;
    }

    public static synchronized void removeSolutionFromArray(AgentSolution a, ArrayList<AgentSolution> swarmSolutionArray) {

        boolean exists = false;
        for (int i = 0; i < swarmSolutionArray.size(); i++) {
            if (swarmSolutionArray.get(i).getId() == a.getId()) {
                exists = true;
            }
        }
        if (exists)
            swarmSolutionArray.remove(a);
    }

    public static double findMinSwarmSolutionList(ArrayList<AgentSolution> swarmSolutionArray) {
        return Collections.max(swarmSolutionArray, new AgentSolutionCompMin()).getSolution();
    }

    public static double findMaxSwarmSolutionList(ArrayList<AgentSolution> swarmSolutionArray) {
        return Collections.max(swarmSolutionArray, new AgentSolutionCompMax()).getSolution();
    }

    public static double findMaxInSwarmSolutionArray(int swarmID, double[] swarmSolutionArray) {
        double tempMax = 0;
        try {
            for (int i = 0; i < swarmSolutionArray.length; i++) {
                if (tempMax < swarmSolutionArray[i]) {
                    tempMax = swarmSolutionArray[i];
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tempMax;
    }

    public static double findMinInSwarmSolutionArray(int swarmID, double[] swarmSolutionArray) {
        double tempMin = swarmSolutionArray[0];
        for(int i = 1; i < swarmSolutionArray.length; i++) {
            if (tempMin > swarmSolutionArray[i]) {
                tempMin = swarmSolutionArray[i];
            }
        }
        return tempMin;
    }

    public static double[] findTopISolutionsInSwarmSolutionList(ArrayList<AgentSolution> swarmSolutionArray, int topI) {
        Collections.sort(swarmSolutionArray, new AgentSolutionCompMax());
        Collections.reverse(swarmSolutionArray);
        double[] result = null;
        // create sublist from element on position zero to topI (eg. 0,3 return elements at [0], [1], [2])

        if (swarmSolutionArray.size() >= topI) {
            List<AgentSolution> maxn = swarmSolutionArray.subList(0, topI);
            result = new double[maxn.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = maxn.get(i).getSolution();
            }
        } else {
            result = new double[1];
        }

        return result;
    }

    // find I best solutions in an array, I = number of initialized robots in a swarm
    public static double[] findTopISolutionsInSwarmSolutionArray(int swarmID, double[] tempArray, int topI) {

        // This is costly as hell, but to avoid using third-party libs..

        // Sort array ascending
        Arrays.sort(tempArray);
        // Convert array to array list, to work with objects
        ArrayList<Double> temp = new ArrayList<Double>();
        for (int i = 0; i < tempArray.length; i++) {
            temp.add(new Double(tempArray[i]));
        }
        // Sort it descending
        Collections.reverse(temp);

        // Convert objects back to the primitives array..
        double[] swarmSolutionArray = new double[temp.size()];
        Iterator<Double> iterator = temp.iterator();
        for (int i = 0; i < swarmSolutionArray.length; i++)
        {
           swarmSolutionArray[i] = iterator.next().doubleValue();
        }

        // Implementing Selection Sort
            for (int i = 0; i <= topI; i++) {
                int tempMax = i;
                for (int j = i+1; j < swarmSolutionArray.length; j++) {
                    if (swarmSolutionArray[j] > swarmSolutionArray[tempMax]) {
                        tempMax = j;
                    }
                }

                if (tempMax != i) {
                    double max = swarmSolutionArray[tempMax];
                    swarmSolutionArray[tempMax] = swarmSolutionArray[i];
                    swarmSolutionArray[i] = max;
                }
            }

            return Arrays.copyOf(swarmSolutionArray, topI);
    }

}
