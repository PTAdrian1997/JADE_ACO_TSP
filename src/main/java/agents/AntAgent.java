package agents;

import agents.mechanics.AntAgentMechanics;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import writer.Writer;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * This class represents an AntAgent agent;
 */
public class AntAgent extends Agent {

    static final String INPUT_FILE = "src\\main\\resources\\environment.txt";
    private final String TOUR_FINDING_SERVICE = "tour-finding";

    // the list of known ant agents:
    private List<AID> antAgents;

    // the conversation id for the message sent to another ant to inform it about the change
    // in current status:
    static final String UPDATE_NEIGHBOR_STATUS = "neighbor-status-update";

    // betaParameter = the parameter that determines the relative importance of
    // pheromone versus distance;
    private double betaParameter;
    // pheromoneDecayParameter = a real number from [0, 1) that determines how much of the current
    // pheromone should evaporate;
    private double pheromoneDecayParameter;

    private int numberOfCities = -1;
    private int sourceCity = -1;
    // how many iterations should this agent run:
    private int numberOfIterations;
    /**
     * status = false, if the agent hasn't found a hamiltonian cycle yet, or true, otherwise;
     */
    private boolean status = false;

    /**
     * finishedAnt[i] =
     * - true, if the ith ant has found a tsp tour
     * - false, otherwise
     */
    private boolean[] finishedAnt;

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
            return "CityRoad[" + this.sourceId.toString() + "," +
                    this.targetId.toString() + "," + this.length + "]";
        }
    }

    // pheromoneLevel[i] = the pheromone level on the ith road from cityGrid;
//    private List<Double[]> subjectivePheromoneLevel;
    private Double[] subjectivePheromoneLevel;
    // the paths chosen by the ants in the current iteration:
    private List<List<Integer>> antPaths;
    // the tour lengths of the agents:
    private List<Double> tourLengths;

    private List<CityRoad> cityGrid = null;

    /**
     * This method will be used for generating new pheromone arrays.
     *
     * @param size the size of the new array;
     * @return the new pheromone array.
     */
    private Double[] generateNewPheromoneArray(int size) {
        Double[] result = new Double[size];
        Arrays.fill(result, 0.0);
        return result;
    }

    /**
     * This behavior is used by AntAgent agents to update their perspective about
     * other ant, whenever the latter sends them an INFORM message;
     */
    private class UpdateFriendStatusServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate messageTemplate = MessageTemplate
                    .MatchConversationId(UPDATE_NEIGHBOR_STATUS);
            ACLMessage updateStatusMessage = myAgent.receive(messageTemplate);
            if (updateStatusMessage != null) {
                AID senderAID = updateStatusMessage.getSender();
                String[] contentValues = updateStatusMessage.getContent().split(" ");
                boolean newStatus = Integer.parseInt(contentValues[0]) == 1;

                int senderIndex = antAgents.indexOf(senderAID);

                // update the finishedAnt array:
                finishedAnt[senderIndex] = newStatus;

                if (newStatus) {
                    // update the tourLengths array:
                    tourLengths.set(senderIndex, Double.parseDouble(contentValues[1]));

                    // update the antPaths list:
                    antPaths.set(senderIndex,
                            Arrays.stream(Arrays.copyOfRange(contentValues, 2, contentValues.length))
                                    .map(Integer::parseInt)
                                    .collect(Collectors.toList()));
                }

//                System.out.println(myAgent.getName() + ": " + senderAID.getName() +
//                        " changed its status to " + newStatus);
            } else {
                block();
            }
        }
    }

    /**
     * get all the new agents.
     *
     * @return an array containing the other agents' identifiers.
     */
    private List<AID> updateAgentsList() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setType(TOUR_FINDING_SERVICE);
        serviceDescription.setName(TOUR_FINDING_SERVICE);
        template.addServices(serviceDescription);
        List<AID> newAgents = new ArrayList<AID>();
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            for (DFAgentDescription dfAgentDescription : result) {
                if (!dfAgentDescription.getName().equals(getAID()))
                    newAgents.add(dfAgentDescription.getName());
            }
        } catch (FIPAException fe) {
            System.out.println(getName() + ": failed to obtain all the new agents");
        }
        return newAgents;
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
    public class FindTourBehaviour extends Behaviour {

        private int state = 0;
        private int currentEpoch = 0;
        private long currentCity = -1;
        private long sourceCity = -1;
        Random random = new Random();
        boolean deadEndReached = false;
        List<Integer> lastPath = null;

        class TrackConfiguration {
            Integer edgeIndex;
            String cityIsVisitedString;
            List<Integer> hamiltonianPath;

            public TrackConfiguration(int edgeIndex, String string, List<Integer> hamiltonianPath) {
                this.edgeIndex = edgeIndex;
                this.cityIsVisitedString = string;
                this.hamiltonianPath = hamiltonianPath;
            }
        }

        // this stack will hold the indexes of the edges that have been selected for
        // the hamiltonian tour, so that the algorithm can choose an alternative path if
        // the current one is not a tour or if it leads to cities that have already been
        // visited
        private Stack<TrackConfiguration> edgeTrack;

        public void action() {
            switch (state) {
                case 0:
                    System.out.println(myAgent.getName() + ": starting...");
                    // start the agent:

                    // start from a randomly chosen city:
                    sourceCity = random.nextInt(numberOfCities) + 1;
                    currentCity = sourceCity;

                    // update the antAgents list:
                    antAgents = new ArrayList<>();
                    antAgents.add(myAgent.getAID());
                    antAgents.addAll(updateAgentsList());

                    // reset antPaths:
                    antPaths = new ArrayList<>();
                    for (int i = 0; i < antAgents.size(); i++) {
                        antPaths.add(new ArrayList<>());
                    }

                    // reset tourLengths:
                    tourLengths = new ArrayList<>();
                    //TODO: do all the collection updates in a single loop
                    for (int i = 0; i < antAgents.size(); i++) {
                        tourLengths.add(0.0);
                    }

                    // reset the finishedAnt array:
                    finishedAnt = Arrays.copyOf(finishedAnt, antAgents.size());
                    finishedAnt[0] = false;

                    // reset the subjectivePheromoneLevel:

                    // reset the edge stack:
                    edgeTrack = new Stack<>();
                    // add all the edges that start from the current city:
                    for (int edgeIndex = 0; edgeIndex < cityGrid.size(); edgeIndex++) {
                        CityRoad currentRoad = cityGrid.get(edgeIndex);
                        if (currentRoad.getSourceId() == currentCity) {
                            StringBuilder stringBuilder = new StringBuilder();
                            for (int cityIndex = 0; cityIndex < numberOfCities; cityIndex++) {
                                if (cityIndex == currentCity - 1 || cityIndex == currentRoad.getTargetId() - 1)
                                    stringBuilder.append(1);
                                else stringBuilder.append(0);
                            }
                            edgeTrack.add(new TrackConfiguration(edgeIndex, stringBuilder.toString(),
                                    new ArrayList<>(Collections.singletonList(edgeIndex))));
                        }
                    }

                    // inform the other ants that you haven't finished:
                    ACLMessage informNotFinished = new ACLMessage(ACLMessage.INFORM);
                    for (AID antAgent : antAgents) {
                        if (!antAgent.equals(myAgent.getAID()))
                            informNotFinished.addReceiver(antAgent);
                    }
                    informNotFinished.setLanguage("English");
                    informNotFinished.setConversationId(UPDATE_NEIGHBOR_STATUS);
                    informNotFinished.setContent("0");
                    myAgent.send(informNotFinished);
                    state = 1;
                    break;
                case 1:
                    // the agent is still searching for the hamiltonian cycle:

                    if (edgeTrack.empty()) {
                        // this graph doesn't contain a hamiltonian tour:
                        System.out.println(myAgent.getName() + ": cannot find a hamiltonian tour...");
                        state = 0;
                    } else {
                        // get the last possible edge from the stack:
                        TrackConfiguration currentTrack = edgeTrack.pop();
                        currentCity = cityGrid.get(currentTrack.edgeIndex).getTargetId();
                        String currentCityIsVisitedString = currentTrack.cityIsVisitedString;
                        List<Integer> currentPath = currentTrack.hamiltonianPath;
                        lastPath = new ArrayList<>(currentPath);

                        // check if the tour is complete:
                        if (AntAgentMechanics.tourCondition(currentCityIsVisitedString, sourceCity, currentCity)) {
                            // change the state to 2:
                            state = 2;
                            status = true;
                            currentEpoch += 1;
                            finishedAnt[0] = true;
                            System.out.println(myAgent.getName() + ": a hamiltonian path was found");
                            // inform the other ants that you've finished, and send them the current tour length and the
                            // current path:
                            ACLMessage informFinished = new ACLMessage(ACLMessage.INFORM);
                            for (AID antAgent : antAgents) {
                                if (!antAgent.equals(myAgent.getAID())) informFinished.addReceiver(antAgent);
                            }
                            informFinished.setLanguage("English");
                            informFinished.setConversationId(UPDATE_NEIGHBOR_STATUS);
                            antPaths.set(0, currentPath);
                            tourLengths.set(0, currentPath.stream()
                                    .map(edgeIndex -> cityGrid.get(edgeIndex).getLength())
                                    .reduce(0.0, Double::sum)
                            );
                            informFinished.setContent("1 " + tourLengths.get(0) + antPaths.get(0).stream()
                                    .map(Object::toString)
                                    .reduce("", (partialResult, currentString) -> partialResult + " " + currentString)
                            );
                            myAgent.send(informFinished);
                        } else {
                            class EdgeCityPair {
                                final Long cityIndex;
                                final Integer edgeIndex;

                                public EdgeCityPair(Long cityIndex, Integer edgeIndex) {
                                    this.cityIndex = cityIndex;
                                    this.edgeIndex = edgeIndex;
                                }
                            }
                            // get the list of neighbour cities that haven't been visited yet:
                            List<EdgeCityPair> possibleCities = new ArrayList<>();
                            for (int edgeIndex = 0; edgeIndex < cityGrid.size(); edgeIndex++) {
                                CityRoad currentEdge = cityGrid.get(edgeIndex);
                                if (currentEdge.getSourceId() == currentCity &&
                                AntAgentMechanics.possibleNextCity(currentCityIsVisitedString, sourceCity,
                                        currentEdge.getTargetId())
                                ) {
                                    possibleCities.add(new EdgeCityPair(currentEdge.getTargetId(), edgeIndex));
                                }
                            }

                            // compute the city probabilities (random-proportional rule):
                            List<Double> nextStateProbabilities = AntAgentMechanics.getNextStateProbability(currentCity,
                                    possibleCities.stream().map(pair -> pair.cityIndex).collect(Collectors.toList()),
                                    cityGrid,
                                    subjectivePheromoneLevel, betaParameter);

                            // add the edges corresponding to the next cities in edgeTrack, in ascending order of the
                            // probability:
                            List<EdgeCityPair> sortedPairs = AntAgentMechanics.sortEdges(possibleCities,
                                    nextStateProbabilities);
                            for (EdgeCityPair currentElement : sortedPairs) {
                                StringBuilder newConfiguration = new StringBuilder(currentCityIsVisitedString);
                                if(currentElement.cityIndex != sourceCity)
                                    newConfiguration.setCharAt(Math.toIntExact(currentElement.cityIndex - 1), '1');
                                else {
                                    newConfiguration.setCharAt(Math.toIntExact(sourceCity - 1), '2');
                                }
                                List<Integer> newPath = new ArrayList<>(currentPath);
                                newPath.add(currentElement.edgeIndex);
                                edgeTrack.add(new TrackConfiguration(currentElement.edgeIndex,
                                        newConfiguration.toString(), newPath));
                            }
                        }
                    }
                    break;
                case 2:
                    // a hamiltonian route has been found: wait for the other ants to finish:

                    // check if all the ants have finished:
                    boolean allAntsFinished = true;
                    for (boolean b : finishedAnt) {
                        if (!b) {
                            allAntsFinished = false;
                            break;
                        }
                    }
                    if (allAntsFinished) {
                        System.out.println(myAgent.getName() + ": all ants have found a hamiltonian tour");
                        // update the pheromone levels:
                        Double[] newPheromoneLevels = AntAgentMechanics.updatePheromoneLevel(
                                subjectivePheromoneLevel, antPaths, cityGrid, tourLengths,
                                pheromoneDecayParameter);
                        subjectivePheromoneLevel = Arrays.copyOf(newPheromoneLevels, newPheromoneLevels.length);
                    }
                    if (currentEpoch == numberOfIterations) {
                        state = 3;
                    } else {
                        state = 0;
                    }
                    break;
                case 3:
                    // the number of iterations has been exceeded:
                    break;
            }
        }

        @Override
        public boolean done() {
            boolean numberOfIterationsReached = currentEpoch == numberOfIterations;
            if (numberOfIterationsReached) {
                // the ant that has the first name in alphabetical order is designated
                // to write the pheromone levels:
                int agentIndex = 1;
                while (agentIndex < antAgents.size() &&
                        antAgents.get(agentIndex).getName().compareTo(myAgent.getAID().getName()) > 0) agentIndex++;
                if (agentIndex == antAgents.size()) {
                    System.out.println(myAgent.getName() + ": designated to write the results...");
                    // write the results:
                    Writer.write(subjectivePheromoneLevel, cityGrid);
                    Writer.write(lastPath);
                }
                System.out.println(myAgent.getName() + ": " + lastPath.stream().map(id -> cityGrid.get(id))
                        .collect(Collectors.toList()).toString() + ", " + tourLengths.get(0));
                System.out.println(myAgent.getName() + ": shutting down FindTourBehavor...");
            }
            return numberOfIterationsReached || deadEndReached;
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
            betaParameter = Double.parseDouble(firstLine[2]);
            pheromoneDecayParameter = Double.parseDouble(firstLine[3]);
            String currentLine;
            while ((currentLine = bufferedReader.readLine()) != null) {
                String[] values = currentLine.split(" ");
                cityGrid.add(new CityRoad(Long.parseLong(values[0]), Long.parseLong(values[1]),
                        Double.parseDouble(values[2])));
                // for the moment, I just assume that the roads are bidirectional:
                cityGrid.add(new CityRoad(Long.parseLong(values[1]), Long.parseLong(values[0]),
                        Double.parseDouble(values[2])));
            }
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
        try {
            Object[] args = getArguments();
            if (args == null || args.length == 0) {
                throw new Exception("invalid number of arguments passed to the AntAgent.");
            }
            // get the number of iterations:
            numberOfIterations = Integer.parseInt((String) args[0]);

            // initialize the antAgents list:
            antAgents = new ArrayList<>();
            antAgents.add(this.getAID());

            // register to the yellow-pages:
            DFAgentDescription dfAgentDescription = new DFAgentDescription();
            dfAgentDescription.setName(getAID());
            ServiceDescription serviceDescription = new ServiceDescription();
            serviceDescription.setType(TOUR_FINDING_SERVICE);
            serviceDescription.setName(TOUR_FINDING_SERVICE);
            dfAgentDescription.addServices(serviceDescription);
            try {
                DFService.register(this, dfAgentDescription);
            } catch (FIPAException fe) {
                System.out.println(getName() +
                        ": failed to register to the yellow pages: " +
                        fe.getMessage());
            }
            // read the environment graph:
            readGrid();

            // initialize the subjectivePheromoneLevel list:
            subjectivePheromoneLevel = generateNewPheromoneArray(cityGrid.size());

            // initialize the finishedAnt array:
            finishedAnt = new boolean[1];
            finishedAnt[0] = false;

            // initialize the antPaths array:
            antPaths = new ArrayList<>();
            // the first element is always associated to this agent:
            antPaths.add(new ArrayList<>());

            // initialize the currentTourLengths list:
            tourLengths = new ArrayList<>();

            // add the FindTourBehaviour behaviour:
            addBehaviour(new FindTourBehaviour());
            // add the behavior for updating the status of other ants:
            addBehaviour(new UpdateFriendStatusServer());

        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    protected void takeDown() {
        // de-register from the DF's yellow pages service:
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            System.out.println(getName() +
                    ": failed to de-register from the yellow pages: "
                    + fe.getMessage());
        }
    }

}
