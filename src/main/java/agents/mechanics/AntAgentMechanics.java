package agents.mechanics;

import agents.AntAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AntAgentMechanics {

    public static List<Double> getNextStateProbability(
            long currentCity, List<Long> availableCities,
            List<AntAgent.CityRoad> cityGrid,
            double[] subjectivePheromoneLevel,
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
        return rawValues.stream().map(rawValue -> rawValue / probSum)
                .collect(Collectors.toList());
//        Double probSum = roadIds.stream().map(roadId -> {
//            double pheromone = subjectivePheromoneLevel[roadId];
//            double roadDistance = cityGrid.get(roadId).getLength();
//            return pheromone * Math.pow(roadDistance, betaParameter);
//        }).reduce(0.0, Double::sum);
//        nextStateProbabilities = roadIds.stream()
//                .map(roadId -> {
//                    double pheromone = subjectivePheromoneLevel[roadId];
//                    double roadDistance = cityGrid.get(roadId).getLength();
//                    return pheromone * Math.pow(roadDistance, betaParameter) / probSum;
//                }).collect(Collectors.toList());
//        return nextStateProbabilities;
    }

}
