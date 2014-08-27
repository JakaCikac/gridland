package org.grid.agent.sample;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by nanorax on 27/08/14.
 */
public class Subswarming {

    // merging the array
    // adding agent to specific array
    // remove agent from specific array

    public static ArrayList<Set<Integer>> initializeSubswarmingArray() {

        ArrayList<Set<Integer>> subswarmingArray = new ArrayList<Set<Integer>>();
        Set<Integer> subswarmSet;
        for (int i = 0; i < ConstantsRDPSO.INIT_SWARMS; i ++) {
            subswarmingArray.add(subswarmSet = new HashSet<Integer>());
        }
        return subswarmingArray;
    }

    public static ArrayList<Set<Integer>> removeAgentFromSubswarming(ArrayList<Set<Integer>> subswarmingArray, int id, int swarmID) {
        subswarmingArray.get(swarmID).remove(id);
        return subswarmingArray;
    }

    public static void toString(ArrayList<Set<Integer>> subswarmingArray) {
        int counter = 0;
        System.out.println("ARRAY: ");
        for (Set<Integer> set : subswarmingArray) {
            System.out.print("( " + counter + " ): ");
            for (Integer i : set) {
                System.out.print("[ " + i + " ] ");
            }
            System.out.println();
            counter++;
        }
    }


}
