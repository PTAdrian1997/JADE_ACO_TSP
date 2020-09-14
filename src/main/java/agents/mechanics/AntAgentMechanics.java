package agents.mechanics;

import agents.AntAgent;

import java.util.ArrayList;
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

}
