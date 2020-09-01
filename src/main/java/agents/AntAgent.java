package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.beans.IntrospectionException;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * This class represents an AntAgent agent;
 */
public class AntAgent extends Agent {

    static final String INPUT_FILE = "src\\main\\resources\\environment.txt";
    private final int NUMBER_OF_ANTS = 3;

    private static final List<String> agentIdentifiers = Arrays.asList("ant1", "ant2", "ant3");
    // myAgentIndex = the index of this agent's name in the list of agent identifiers;
    private int myAgentIndex;


    private int numberOfCities = -1;
    private int sourceCity = -1;
    // how many iterations should this agent run:
    private int numberOfIterations;
    private int currentIteration;
    /**
     * status = 0, if the agent hasn't found a hamiltonian cycle yet, or 1, otherwise;
     */
    private boolean status = false;

    /**
     * finishedAnt[i] =
     * - true, if the ith ant has found a tsp tour
     * - false, otherwise
     */
    private boolean[] finishedAnt;

    /**
     * cityIsVisited[i] =
     * - true, if the ith city has already been visited
     * - false otherwise
     */
    private boolean[] cityIsVisited;

    /**
     * the class representing a city connection;
     */
    public static class CityRoad {

        private final Long sourceId;
        private final Long targetId;
        private final Double length;

        public CityRoad(Long sourceId, Long targetId, Double length) {
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.length = length;
        }

        public Long getSourceId() {
            return sourceId;
        }

        public Long getTargetId() {
            return targetId;
        }

        public Double getLength() {
            return length;
        }

        public String toString() {
            return "CityRoad[" + this.targetId.toString() + "," +
                    this.sourceId.toString() + "," + this.length + "]";
        }
    }

    // pheromoneLevel[i] = the pheromone level on the ith road from cityGrid;
//    private double[] pheromoneLevel;
    private double[][] subjectivePheromoneLevel;

    private List<CityRoad> cityGrid = null;

    /**
     *
     * @param currentCity
     * @param availableCities
     * @return
     */
    private double getNextStateProbability(int currentCity, List<Integer> availableCities){
        return 0.0;
    }

    /**
     * This behavior is used by AntAgent agents to update their perspective about
     * other ant, whenever the latter sends them an INFORM message;
     */
    private class UpdateFriendStatusServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate messageTemplate = MessageTemplate
                    .MatchConversationId("send-agent-status");
            ACLMessage aclMessage = myAgent.receive(messageTemplate);
            if(aclMessage != null){
                String senderName = aclMessage.getSender().getName();
                int agentIndex = agentIdentifiers.indexOf(senderName);
                boolean newStatus = Integer.parseInt(aclMessage.getContent()) == 1;
                finishedAnt[agentIndex] = newStatus;
                if(newStatus){
                    // update the pheromone values:
                }
            }
        }
    }

    /**
     * This class represents the tour-finding cyclic behaviour of an AntAgent
     * from an AntAgent System. Each time, the ant will do two things:
     * - state transition rule (random-proportional rule):
     * - set all the values from cityIsVisited to false
     * - INFORM all the other ants that you're not finished yet;
     * - Find a hamiltonian cycle in the city grid;
     * - change your status to finished;
     * - global pheromone updating rule:
     * - wait for the other ants to complete the cycle
     * (send an INFORM message to them that you finished the tour;
     * while you're waiting, other ants might finish their tours and send
     * you an INFORM message. Every time that happens, check if all the ants have finished;
     * if that's the case, repeat the process;
     * );
     */
    public class FindTourBehaviour extends CyclicBehaviour {

        private int state = 0;

        public void action() {
            switch (state){
                case 0:
                    // start the agent:

                    // reset the cityIsVisited array:
                    Arrays.fill(cityIsVisited, false);
                    break;
                case 1:
                    // the agent is still searching for the hamiltonian cycle:
                    break;
                case 2:
                    // a hamiltonian route has been found: wait for the other ants to finish:
                    break;
            }
        }
    }

    /**
     * Read the environment (the street grid).
     * This method might need to be replaced by a method that reads from a database;
     * note: here we assume that all the cities are labeled with numbers from 1 to n,
     * where n is simply the number of cities.
     */
    private void readGrid() {
        cityGrid = new ArrayList<CityRoad>();
        FileReader fileReader = null;
        BufferedReader bufferedReader = null;
        try {
            fileReader = new FileReader(INPUT_FILE);
            bufferedReader = new BufferedReader(fileReader);
            // read the number of cities and the source city:
            String[] firstLine = bufferedReader.readLine().split(" ");
            numberOfCities = Integer.parseInt(firstLine[0]);
            sourceCity = Integer.parseInt(firstLine[1]);
            String currentLine;
            while ((currentLine = bufferedReader.readLine()) != null) {
                String[] values = currentLine.split(" ");
                cityGrid.add(new CityRoad(Long.parseLong(values[0]), Long.parseLong(values[1]),
                        Double.parseDouble(values[2])));
                // for the moment, I just assume that the roads are bidirectional:
                cityGrid.add(new CityRoad(Long.parseLong(values[1]), Long.parseLong(values[0]),
                        Double.parseDouble(values[2])));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    protected void setup() {
        myAgentIndex = agentIdentifiers.indexOf(this.getAID().getName());
        try {
            Object[] args = getArguments();
            if (args == null || args.length == 0) {
                throw new Exception("invalid number of arguments passed to the AntAgent.");
            }
            // get the number of iterations:
            numberOfIterations = Integer.parseInt((String)args[0]);
            currentIteration = 0;
            // create the finishedAnt array:
            this.finishedAnt = new boolean[NUMBER_OF_ANTS];
            // read the environment graph:
            readGrid();
            // create the cityIsVisited array:
            cityIsVisited = new boolean[numberOfCities];
            // create the pheromone matrix:
            subjectivePheromoneLevel = new double[NUMBER_OF_ANTS][cityGrid.size()];
            for(int i = 0;i < NUMBER_OF_ANTS;i++){
                subjectivePheromoneLevel[i] = new double[cityGrid.size()];
                Arrays.fill(subjectivePheromoneLevel[i], 0);
            }
            System.out.println("cityGrid length: " + cityGrid.size());
            // add the FindTourBehaviour behaviour:
            addBehaviour(new FindTourBehaviour());
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

}
