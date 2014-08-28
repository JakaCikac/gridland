package org.grid.agent.sample;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by nanorax on 27/08/14.
 */
public class Subswarming {

    // merging the array
    // adding agent to specific array
    // remove agent from specific array

    // initialize
    public static ArrayList<Set<Integer>> initializeSubswarmingArray() {

        ArrayList<Set<Integer>> subswarmingArray = new ArrayList<Set<Integer>>();
        Set<Integer> subswarmSet;
        for (int i = 0; i < ConstantsRDPSO.INIT_SWARMS; i ++) {
            subswarmingArray.add(subswarmSet = new HashSet<Integer>());
        }
        return subswarmingArray;
    }

    // remove agent from the appropriate set / subswarm
    public static ArrayList<Set<Integer>> removeAgentFromSubswarming(ArrayList<Set<Integer>> subswarmingArray, int id, int swarmID) {
        subswarmingArray.get(swarmID).remove(id);
        return subswarmingArray;
    }

    // add agent to the appropriate set / subswarm
    public static ArrayList<Set<Integer>> addAgentToSubswarming(ArrayList<Set<Integer>> subswarmingArray, int id, int swarmID) {
        subswarmingArray.get(swarmID).add(id);
        return subswarmingArray;
    }

    // mark swarmID as deleted
    public static ArrayList<Set<Integer>> deleteSubgroupFromSubswarmingArray(ArrayList<Set<Integer>> subswarmingArray, int swarmID) {
        // check if the subgroup is really empty
        if ( subswarmingArray.get(swarmID).size() == 0 ) {
            // put in the array a negative number
            subswarmingArray.get(swarmID).add(-1);
        }
        return subswarmingArray;
    }

    // check if subgroup index is already in the subswarming array (to determine if there is a need of creating a new one)
    public static boolean checkIfSubgroupExistsInSubswamingArray(ArrayList<Set<Integer>> subswarmingArray, int swarmID) {
        if (subswarmingArray.get(swarmID) != null) {
            return true;
        } else return false;
    }

    // restore swarmID
    public static ArrayList<Set<Integer>> restoreSubgroupInSubswarmingArray(ArrayList<Set<Integer>> subswarmingArray, int swarmID) {
        // check if the subswarm exists, check if it has one element in it (-1) and remove it
        if (subswarmingArray.get(swarmID) != null) {
            if (subswarmingArray.get(swarmID).size() == 1) {
                subswarmingArray.get(swarmID).remove(-1);
            } else return null;
        }
        return subswarmingArray;
    }

    public static int getNumberOfSubSwarms(ArrayList<Set<Integer>> subswarmingArray) {
        return subswarmingArray.size();
    }

    public static int getNumerOfAgentsInSubSwarm(ArrayList<Set<Integer>> subswarmingArray, int swarmID) {
        return subswarmingArray.get(swarmID).size();
    }

    // merge two arrays (received and currently held)
    public static ArrayList<Set<Integer>> mergeTwoSubswarmingArrays(ArrayList<Set<Integer>> held,
                                                                    ArrayList<Set<Integer>> received,
                                                                    int numMods, int numMods2int, int lastMod1, int lastMod2,
                                                                    int idRecieved, int idHeld) {

        // scenario 1: Held wasn't modified by receiving agent, it has a newer timestep than held
        // solution: just override held with received
        if (lastMod2 != idHeld) {
            // held array wasn't modified by holding agent
                return received;

        // scenario 2: last modification on received was made by the currently held agent.. so received is probably older
        } else if (lastMod1 == lastMod2) {
            return held;
        }

        // if there is no reason to update the array, keep the currently held one
        return held;
    }

    public static ArrayList<Set<Integer>> unityMergeSubswarmingArrays(ArrayList<Set<Integer>> held, ArrayList<Set<Integer>> received) {

        ArrayList<Set<Integer>> merged = null;

        if (held.size() == received.size()) {

            Iterator<Set<Integer>> itHel = held.iterator();
            Iterator<Set<Integer>> itRec = received.iterator();

            merged = new ArrayList<Set<Integer>>();

            while(itHel.hasNext()) {
                Set<Integer> union = new HashSet<Integer>();
                Set<Integer> heldSet = itHel.next();
                Set<Integer> recSet = itRec.next();
                union.addAll(heldSet);
                union.addAll(recSet);
                merged.add(union);
            }
            return merged;

        } else return null;
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
