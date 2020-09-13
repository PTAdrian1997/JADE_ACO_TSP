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
        List<Double> result = rawValues.stream().map(rawValue -> {
            if(probSum == 0.0)return 0.0;
            return rawValue / probSum;
        })
                .collect(Collectors.toList());
        return result;
    }

    //TODO
    public static Long selectNextCity(){return 0L;}

}
