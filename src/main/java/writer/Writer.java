package writer;

import agents.AntAgent;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Writer {

    /**
     *
     * @param pheromoneLevel
     * @param cityGrid
     */
    public static void write(Double[] pheromoneLevel, List<AntAgent.CityRoad> cityGrid){
        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new
                    FileWriter("pheromone_levels.txt"));
            for(int roadIndex = 0;roadIndex < cityGrid.size();roadIndex++){
                AntAgent.CityRoad currentRoad = cityGrid.get(roadIndex);
                bufferedWriter.write(currentRoad.getSourceId() + " " + currentRoad.getTargetId() + " " +
                        pheromoneLevel[roadIndex] + "\n");
            }
            bufferedWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
