package org.grid.agent.sample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

/**
 * Created by nanorax on 11/08/14.
 *
 * A utility class to merge solutions of different agents in the same swarm.
 */
public class SwarmSolution {

    public static ArrayList mergeSolutionToArray(double solution, Double[] swarmSolutionArray) {
        // hello world, how do I do this?
        // probably has to be synced, otherwise weird arrays may occur..
        // am I merging arrays, or am I merging a single solution into an array?
        // I think I'm merging actual arrays, otherwise there would have to be an array
        // for each swarm, kept somewhere centrally, and yeah no, we don't want that.
        return null;
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
