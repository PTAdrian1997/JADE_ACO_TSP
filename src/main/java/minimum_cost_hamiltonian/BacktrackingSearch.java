package minimum_cost_hamiltonian;

import agents.AntAgent;
import agents.mechanics.AntAgentMechanics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class BacktrackingSearch {

    static int numberOfCities = -1;

    public static List<AntAgent.CityRoad> readGrid() {
        List<AntAgent.CityRoad> cityGrid = new ArrayList<>();
        FileReader fileReader = null;
        BufferedReader bufferedReader = null;
        try {
            fileReader = new FileReader("src\\main\\resources\\environment.txt");
            bufferedReader = new BufferedReader(fileReader);
            String[] firstLine = bufferedReader.readLine().split(" ");
            numberOfCities = Integer.parseInt(firstLine[0]);
            String currentLine;
            while ((currentLine = bufferedReader.readLine()) != null) {
                String[] values = currentLine.split(" ");
                cityGrid.add(new AntAgent.CityRoad(Long.parseLong(values[0]), Long.parseLong(values[1]),
                        Double.parseDouble(values[2])));
                // for the moment, I just assume that the roads are bidirectional:
                cityGrid.add(new AntAgent.CityRoad(Long.parseLong(values[1]), Long.parseLong(values[0]),
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
        return cityGrid;
    }

    private static List<Integer> getPossibleEdges(String currentVisitedCitiesString, long sourceCity,
                                                  List<AntAgent.CityRoad> cityGrid, long startCity) {
        List<Integer> result = new ArrayList<>();
        for (int edgeIndex = 0; edgeIndex < cityGrid.size(); edgeIndex++) {
            AntAgent.CityRoad currentRoad = cityGrid.get(edgeIndex);
            if (currentRoad.getSourceId() == sourceCity && AntAgentMechanics
                    .possibleNextCity(currentVisitedCitiesString, startCity, currentRoad.getTargetId())) {
                result.add(edgeIndex);
            }
        }
        return result;
    }

    public static List<Integer> findOptimalTour(List<AntAgent.CityRoad> cityGrid) {
        List<Integer> optimalPath = new ArrayList<>();
        double bestLengh = -1.0;
        double worstLength = -1.0;
        int numberOfPossiblePaths = 0;
        class StackRecord {
            final long currentCity;
            final String visitedCitiesString;
            final List<Integer> currentPath;

            public StackRecord(long currentCity, String toString, List<Integer> newList) {
                this.currentCity = currentCity;
                visitedCitiesString = toString;
                this.currentPath = newList;
            }
        }
        Stack<StackRecord> stackRecords = new Stack<>();
        long sourceCity = 11;
        // add the possible edges:
        for (int edgeIndex = 0; edgeIndex < cityGrid.size(); edgeIndex++) {
            AntAgent.CityRoad currentEdge = cityGrid.get(edgeIndex);
            if (currentEdge.getSourceId() == sourceCity) {
                StringBuilder stringBuilder = new StringBuilder();
                for (int cityIndex = 0; cityIndex < numberOfCities; cityIndex++) {
                    if (cityIndex == sourceCity - 1 || cityIndex == currentEdge.getTargetId() - 1)
                        stringBuilder.append(1);
                    else stringBuilder.append(0);
                }
                stackRecords.add(new StackRecord(currentEdge.getTargetId(), stringBuilder.toString(),
                        new ArrayList<Integer>(Collections.singletonList(edgeIndex))));
            }
        }
        while (!stackRecords.empty()) {
            StackRecord currentRecord = stackRecords.pop();
            long currentCity = currentRecord.currentCity;
            List<Integer> currentRecordPath = currentRecord.currentPath;
            List<Integer> possibleEdges = getPossibleEdges(currentRecord.visitedCitiesString, currentCity,
                    cityGrid, sourceCity);
            if (AntAgentMechanics.tourCondition(currentRecord.visitedCitiesString, sourceCity, currentCity)) {
                numberOfPossiblePaths ++;
                double currentLength = currentRecordPath.stream()
                        .map(edgeIndex -> cityGrid.get(edgeIndex).getLength())
                        .reduce(0.0, Double::sum);
                if(optimalPath.isEmpty() || bestLengh > currentLength){
                    optimalPath = new ArrayList<>(currentRecordPath);
                    bestLengh = currentLength;
                }
                if(worstLength == -1.0 || worstLength < currentLength){
                    worstLength = currentLength;
                }
            } else {
                // add all possible next cities to the stack:
                for (Integer edgeIndex : possibleEdges) {
                    AntAgent.CityRoad currentRoad = cityGrid.get(edgeIndex);
                    StringBuilder newVisitedCities = new StringBuilder(currentRecord.visitedCitiesString);
                    if (currentRoad.getTargetId() != sourceCity)
                        newVisitedCities.setCharAt(Math.toIntExact(currentRoad.getTargetId() - 1), '1');
                    else newVisitedCities.setCharAt(Math.toIntExact(sourceCity - 1), '2');
                    List<Integer> newPathCopy = new ArrayList<>(currentRecordPath);
                    newPathCopy.add(edgeIndex);
                    stackRecords.add(new StackRecord(currentRoad.getTargetId(), newVisitedCities.toString(), newPathCopy));
                }
            }
        }
        System.out.println("worstLength = " + worstLength);
        System.out.println("bestLength = " + bestLengh);
        System.out.println("numberOfPossiblePaths = " + numberOfPossiblePaths);
        return optimalPath;
    }

    public static void main(String[] args) {

        List<AntAgent.CityRoad> cityGrid = readGrid();

        List<Integer> bestPath = findOptimalTour(cityGrid);

        System.out.println("bestTour: " + bestPath.stream().map(id -> cityGrid.get(id).toString())
                .collect(Collectors.toList()));

    }

}
