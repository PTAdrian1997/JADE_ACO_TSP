package agents.mechanics;

import agents.AntAgent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AntAgentMechanics {

    /**
     * Compute the list of probabilities for each next possible city.
     * @param currentCity the city where the ant is currently placed.
     * @param availableCities the list of all next possible cities.
     * @param cityGrid the list of roads.
     * @param subjectivePheromoneLevel the array containing all the pheromone
     *                                 levels for each available city.
     * @param betaParameter the parameter that determines the relative importance of
     *                      pheromone versus distance;
     * @return the list of probabilities, each probability being associated with the
     *          city at the same index from availableCity
     */
    public static List<Double> getNextStateProbability(
            long currentCity, List<Long> availableCities,
            List<AntAgent.CityRoad> cityGrid,
            Double[] subjectivePheromoneLevel,
            double betaParameter
            ){
        List<Integer> roadIds = availableCities.stream()
                .map(cityId -> {
                            int roadId = 0;
                            while(roadId < cityGrid.size()){
                                AntAgent.CityRoad cityRoad = cityGrid.get(roadId);
                                if(cityRoad.getSourceId() == currentCity &&
                                        cityRoad.getTargetId().equals(cityId))
                                    break;
                                roadId++;
                            }
                            return roadId;
                        }
                ).collect(Collectors.toList());
        List<Double> rawValues = roadIds.stream().map(roadId -> {
            double pheromone = subjectivePheromoneLevel[roadId];
            double roadDistance = cityGrid.get(roadId).getLength();
            return pheromone * Math.pow(1/roadDistance, betaParameter);
        }).collect(Collectors.toList());
        Double probSum = rawValues.stream().reduce(0.0, Double::sum);
        List<Double> result = rawValues.stream().map(rawValue -> {
            if(probSum == 0.0)return 0.0;
            return rawValue / probSum;
        })
                .collect(Collectors.toList());
        return result;
    }

    /**
     * select the best candidate from the given set of next possible cities
     * @param availableCities the set of next possible cities
     * @param nextStateProbabilities the list of probabilities associated with the
     *                               given availableCities
     * @return the best next city candidate
     */
    public static Long selectNextCity(
            List<Long> availableCities,
            List<Double> nextStateProbabilities
    ){
        int maxIndex = -1;
        for(int i = 0;i < nextStateProbabilities.size();i++){
            if(maxIndex == -1 || nextStateProbabilities.get(maxIndex) < nextStateProbabilities.get(i))
                maxIndex = i;
        }
        return availableCities.get(maxIndex);
    }

    /**
     * Get the new pheromone level array after an iteration
     * @param currentPheromoneLevel the pheromone levels that were used by the ant agents in the last iteration
     * @param antPaths The paths chosen by each ant in the last iteration
     * @param cityGrid the roads of the current city
     * @param tourLengths the lengths of the paths chosen by the agents in the last iteration
     * @param pheromoneDecayParameter a real number from the interval [0,1] that determines how much pheromone
     *                                should evaporate after an iteration
     * @return an array of Doubles containing the new pheromone levels
     */
    public static Double[] updatePheromoneLevel(
            Double[] currentPheromoneLevel, List<List<Integer>> antPaths,
            List<AntAgent.CityRoad> cityGrid,
            List<Double> tourLengths,
            double pheromoneDecayParameter
            ){
        Double[] globalPheromoneLevels = Arrays.copyOf(currentPheromoneLevel, currentPheromoneLevel.length);
        double deltaPheromone = 0.0;
        for(int edgeIndex = 0;edgeIndex < cityGrid.size();edgeIndex++){
            // update edge at the given index in the cityGrid List:
            double deltaSum = 0.0;
            for(int antIndex = 0;antIndex < antPaths.size();antIndex++){
                if(antPaths.get(antIndex).contains(edgeIndex))deltaSum += 1.0 / tourLengths.get(antIndex);
            }
            globalPheromoneLevels[edgeIndex] = (1 - pheromoneDecayParameter) * currentPheromoneLevel[edgeIndex] +
                    deltaSum;
        }
        return globalPheromoneLevels;
    }

    /**
     *
     * @param cityVisited
     * @param lastRoad
     * @param sourceCity
     * @return
     */
    public static boolean tourCondition(boolean[] cityVisited,
                                        AntAgent.CityRoad lastRoad,
                                        long sourceCity
    ){
        for (boolean b : cityVisited) if (b) return false;
        if(lastRoad.getTargetId() != sourceCity) return false;
        return true;
    }

}
