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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
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
    private List<Double[]> subjectivePheromoneLevel;

    private List<CityRoad> cityGrid = null;

    /**
     * This method will be used for generating new pheromone arrays.
     * @param size the size of the new array;
     * @return the new pheromone array.
     */
    private Double[] generateNewPheromoneArray(int size){
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
                boolean newStatus = Integer.parseInt(updateStatusMessage.getContent()) == 1;
                int senderIndex = antAgents.indexOf(senderAID);
                finishedAnt[senderIndex] = newStatus;
                System.out.println(myAgent.getName() + ": " + senderAID.getName() +
                        " changed its status to " + newStatus);
            }
            else {
                block();
            }
        }
    }

    /**
     * get all the new agents.
     * @return an array containing the other agents' identifiers.
     */
    private List<AID> updateAgentsList(){
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription serviceDescription = new ServiceDescription();
        serviceDescription.setType(TOUR_FINDING_SERVICE);
        serviceDescription.setName(TOUR_FINDING_SERVICE);
        template.addServices(serviceDescription);
        List<AID> newAgents = new ArrayList<AID>();
        try{
            DFAgentDescription[] result = DFService.search(this, template);
            for (DFAgentDescription dfAgentDescription : result) {
                if(!dfAgentDescription.getName().equals(getAID()))
                    newAgents.add(dfAgentDescription.getName());
            }
        }catch (FIPAException fe){
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
        private List<Integer> currentPath = new ArrayList<>();
        private long currentCity = -1;

        public void action() {
            switch (state) {
                case 0:
                    System.out.println(myAgent.getName() + ": starting...");
                    // start the agent:
                    currentCity = sourceCity;
                    // reset the cityIsVisited array:
                    currentPath.clear();
                    Arrays.fill(cityIsVisited, false);
                    cityIsVisited[Math.toIntExact(currentCity) - 1] = true;
                    antAgents = new ArrayList<>();
                    antAgents.add(myAgent.getAID());
                    antAgents.addAll(updateAgentsList());
//                    System.out.println(myAgent.getName() + ": neighbors=" + antAgents.toString());
                    finishedAnt = Arrays.copyOf(finishedAnt, antAgents.size());
                    int originalSize = subjectivePheromoneLevel.size();
                    for(int i = originalSize;i < finishedAnt.length;i++){
                        subjectivePheromoneLevel.add(generateNewPheromoneArray(cityGrid.size()));
                    }
                    // inform the other ants that you haven't finished:
                    ACLMessage informNotFinished = new ACLMessage(ACLMessage.INFORM);
                    for (AID antAgent : antAgents) {
                        if(!antAgent.equals(myAgent.getAID()))
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

                    // get the list of neighbour cities that haven't been visited yet:
                    List<Long> possibleCities = cityGrid.stream()
                            .filter(cityRoad -> cityRoad.sourceId == currentCity &&
                                    !cityIsVisited[Math.toIntExact(cityRoad.targetId) - 1])
                            .map(cityRoad -> cityRoad.targetId)
                            .collect(Collectors.toList());
                    System.out.println(myAgent.getName() + ": possibleCities=" + possibleCities.toString());
                    // update the index of the current agent:

                    // compute the city probabilities (random-proportional rule):
                    List<Double> nextStateProbabilities = AntAgentMechanics.getNextStateProbability(currentCity,
                            possibleCities,
                            cityGrid,
                            subjectivePheromoneLevel.get(0), betaParameter);
                    // using these next state probabilities, choose the next location:
                    Long nextCity = AntAgentMechanics.selectNextCity(possibleCities, nextStateProbabilities);
                    System.out.println(myAgent.getName() + ": nextCity=" + nextCity);
                    // move to this next city:
                    int nextEdgeIndex = 0;
                    while(nextEdgeIndex < cityGrid.size()) {
                        CityRoad currentRoad = cityGrid.get(nextEdgeIndex);
                        if(currentRoad.sourceId == currentCity && currentRoad.targetId.equals(nextCity))break;
                        nextEdgeIndex += 1;
                    }
                    currentPath.add(nextEdgeIndex);
                    currentCity = nextCity;
                    cityIsVisited[Math.toIntExact(nextCity) - 1] = true;
                    int lastCityVisited = 0;
                    while(lastCityVisited < cityIsVisited.length && cityIsVisited[lastCityVisited])lastCityVisited ++;
                    if(lastCityVisited == cityIsVisited.length){
                        // move to the next state:
                        state = 2;
                        status = true;
                        currentEpoch += 1;
                        finishedAnt[0] = true;
                        System.out.println(myAgent.getName() + ": found a hamiltonian route");
                        // inform the others that you've finished:
                        ACLMessage informFinished = new ACLMessage(ACLMessage.INFORM);
                        for (AID antAgent : antAgents) {
                            if(!antAgent.equals(myAgent.getAID()))
                                informFinished.addReceiver(antAgent);
                        }
                        informFinished.setLanguage("English");
                        informFinished.setConversationId(UPDATE_NEIGHBOR_STATUS);
                        informFinished.setContent("1");
                        myAgent.send(informFinished);
                    }
                    else {
                        state = 1;
                    }
                    break;
                case 2:
                    // a hamiltonian route has been found: wait for the other ants to finish:

                    // check if all the ants have finished:
//                    int lastFinished = -1;
//                    while (lastFinished < finishedAnt.length - 1 && finishedAnt[lastFinished + 1])lastFinished++;
////                    System.out.println(myAgent.getName() + ": current lastFinished=" + lastFinished + ", finishedAnt length=" + finishedAnt.length);
//                    if(lastFinished == finishedAnt.length - 1){
//                        System.out.println(myAgent.getName() + ": all the ants have found a hamiltonian tour");
//                        if(currentEpoch == numberOfIterations){
//                            state = 3;
//                        }
//                        else {
//                            state = 0;
//                        }
//                        state = 3;
//                    }
                    break;
                case 3:
                    // the number of iterations has been exceeded:
                    break;
            }
        }

        @Override
        public boolean done() {
            return currentEpoch == numberOfIterations;
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
            try{
                DFService.register(this, dfAgentDescription);
            }catch (FIPAException fe){
                System.out.println(getName() +
                        ": failed to register to the yellow pages: " +
                        fe.getMessage());
            }
            // read the environment graph:
            readGrid();
            // create the cityIsVisited array:
            cityIsVisited = new boolean[numberOfCities];

            // initialize the subjectivePheromoneLevel list:
            subjectivePheromoneLevel = new ArrayList<>();
            Double[] auxPheromoneLevels = generateNewPheromoneArray(cityGrid.size());
            subjectivePheromoneLevel.add(auxPheromoneLevels);

            // initialize the finishedAnt array:
            finishedAnt = new boolean[1];
            finishedAnt[0] = false;

            // add the FindTourBehaviour behaviour:
            addBehaviour(new FindTourBehaviour());
            // add the behavior for updating the status of other ants:
            addBehaviour(new UpdateFriendStatusServer());
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    @Override
    protected void takeDown(){
        // de-register from the DF's yellow pages service:
        try{
            DFService.deregister(this);
        }
        catch (FIPAException fe){
            System.out.println(getName() +
                    ": failed to de-register from the yellow pages: "
                    + fe.getMessage());
        }
    }

}
