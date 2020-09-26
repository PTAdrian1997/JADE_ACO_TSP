package agents.mechanics;

import agents.AntAgent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class AntAgentMechanicsTest {

    @Test
    public void getNextStateProbabilityTest(){
        System.out.println("test if the values are correctly computed:");
        List<AntAgent.CityRoad> cityGrid = Arrays.asList(
                new AntAgent.CityRoad(1L, 2L, 3.24),
                new AntAgent.CityRoad(1L, 3L, 4.65),
                new AntAgent.CityRoad(1L, 4L, 5.43),
                new AntAgent.CityRoad(2L, 4L, 4.65),
                new AntAgent.CityRoad(4L, 5L, 5.76),
                new AntAgent.CityRoad(4L, 3L, 2.48)
        );
        long currentCity = 1L;
        List<Long> availableCities = Arrays.asList(2L, 3L, 4L);
        Double [] initialSubjectivePheromoneLevel = new Double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
        Double [] randomSubjectivePheromoneLevel = new Double[]
                {1.23, 2.43, 1.14, 5.43, 2.54, 1.76};
        double betaParameter = 1.50;
        List<Double> initialPheromoneResult = AntAgentMechanics.getNextStateProbability(
                currentCity, availableCities, cityGrid, initialSubjectivePheromoneLevel,
                betaParameter
        );
        List<Double> expectedInitialPheromoneResult = Arrays.asList(0.0, 0.0, 0.0);
        System.out.print("\tfor a pheromone level array filled with zeros: ");
        assertEquals(expectedInitialPheromoneResult, initialPheromoneResult);
        System.out.println("Passed");
        List<Double> randomPheromoneResult = AntAgentMechanics.getNextStateProbability(
                currentCity, availableCities, cityGrid, randomSubjectivePheromoneLevel,
                betaParameter
        );
        List<Double> expectedRandomPheromoneResult = Arrays
                .asList(0.3881629942100124, 0.4460190182185986, 0.165817987571389);
        System.out.print("\tfor a random pheromone level array: ");
        assertEquals(expectedRandomPheromoneResult, randomPheromoneResult);
        System.out.println("Passed");
    }

    @Test
    public void updatePheromoneLevelTest(){

    }

}
