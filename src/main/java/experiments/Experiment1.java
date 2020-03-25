package experiments;

import help.SWATApi;
import help.Utilities;
import json.Aspect;
import json.Context;
import json.JsonObject;
import json.ReadJsonlFile;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ==============================Experiment-1===================================================
 * 1) (a) Use "content" from either sent_context or para_context or sect_context.
 *    (b) Pass content to SWAT. Get map of (entity,sal_score).
 * 2) Aspect candidates are the candidates we are trying to score.
 *   -- For Every aspect content, score the content by summing over the
 *      salience score of entities contained in it.
 *   -- The salience scores are obtained in Step-1 above.
 *   -- If there are no overlapping entities, then the score of the passage is 0.
 * ==============================================================================================
 *
 * @author Shubham Chatterjee
 * @version 03/03/2020
 */

public class Experiment1 {

    private Map<String, HashMap<String, HashMap<String, Integer>>> aspectEntityMap = new HashMap<>();
    private final AtomicInteger counter = new AtomicInteger();
    private final ArrayList<String> runFileStrings = new ArrayList<>();
    private final ConcurrentHashMap<String, Integer> accuracyMap = new ConcurrentHashMap<>();
    private final List<Double> accuracyList = new ArrayList<>();

    public Experiment1(String dataDir,
                       String outputDir,
                       String jsonFile,
                       String aspectEntityFile,
                       String runFile,
                       String accuracyFile) {

        String jsonFilePath = dataDir + "/" + jsonFile;
        String aspectEntityFilePath = dataDir + "/" + aspectEntityFile;
        String runFilePath = outputDir + "/" + runFile;
        String accuracyFilePath = outputDir + "/" + accuracyFile;

        System.out.print("Reading JSON-L file....");
        List<JSONObject> jsonObjectList = ReadJsonlFile.read(jsonFilePath);
        System.out.println("[Done].");

        System.out.print("Reading the aspect entity file...");
        try {
            aspectEntityMap = Utilities.readMap(aspectEntityFilePath);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("[Done].");

        score(runFilePath, jsonObjectList);

        System.out.print("Saving accuracy values...");
        try {
            Utilities.writeList(accuracyList, accuracyFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("[Done].");
    }

    /**
     * Method to score candidate aspects for every entity mention.
     * @param runFilePath String Path to the run file.
     * @param jsonObjectList List List of JSON objects read from file.
     */

    private void score(String runFilePath, @NotNull List<JSONObject> jsonObjectList) {

        double accuracy;

        // Do in parallel
        jsonObjectList.parallelStream().forEach(this::doTask);

        // Do in serial
//        for (JSONObject jsonObject : jsonObjectList) {
//            doTask(jsonObject);
//        }

        accuracy = findAccuracy();
        System.out.println("Final Accuracy = " + accuracy);

        System.out.print("Writing to run file...");
        Utilities.writeFile(runFileStrings, runFilePath);
        System.out.println("[Done].");

        System.out.println("Could not find salient entities for " + counter + " of " + jsonObjectList.size() + " mentions");
        System.out.println("Done");
    }

    private void doTask(JSONObject jsonObject) {

        Map<String, Map<String, Double>> rankings = new HashMap<>(); // Map to store the rankings
        Map<String, Double> paraScoreMap; // Inner map

        String entityID = JsonObject.getEntityId(jsonObject);
        String mention = JsonObject.getMention(jsonObject);

        // Get the paragraph context of the mention

        /*
         * MODIFY THIS LINE TO USE EITHER getSentenceContext(), getParaContext() OR getSectionContext().
         */
        Context context = JsonObject.getSectionContext(jsonObject);

        // Get the list of candidate aspects for the mention
        List<Aspect> candidateAspects = JsonObject.getAspectCandidates(jsonObject);

        // Score the candidate aspects for the mention using the context.
        // Delegates the task to a helper method.
        paraScoreMap = score(entityID, context, candidateAspects);


        // If the helper method returned a non-empty result
        if (! paraScoreMap.isEmpty()) {
            // Make a map entry where Key = id_context and Value = Map of (id_context, score)
            rankings.put(JsonObject.getIdContext(jsonObject), paraScoreMap);
        } else {
            // If you are here then it means that no salient entities were found for the context
            // Count this. Useful statistic (maybe!)
            counter.getAndIncrement();

            // If you could not score the candidate aspects, then each aspect gets a score of 0.
            // This is important because we must record the fact the we couldn't score aspects for this entity.
            for (Aspect aspect : candidateAspects) {
                paraScoreMap.put(aspect.getId(), 0.0d);
            }
            rankings.put(JsonObject.getIdContext(jsonObject), paraScoreMap);
        }
        makeRunFileStrings(rankings);
        findAccuracy(jsonObject, paraScoreMap);
        System.out.println("Mention :" + mention + "\t" + "Accuracy: "  + findAccuracy());

    }

    /**
     * Helper method.
     * Makes a run file in the format: id_context 0 id_aspect rank score run_name.
     * @param rankings Map Map of rankings for the mention.
     */

    private void makeRunFileStrings(@NotNull Map<String, Map<String, Double>> rankings) {

        String runFileString; // A candidate run file string
        int rank; // Rank of the aspect
        Map<String, Double> scoreMap; // Map of scores

        for (String idContext : rankings.keySet()) {
            scoreMap = rankings.get(idContext);
            rank = 1;
            for (String idAspect : scoreMap.keySet()) {
                runFileString = idContext + " " + "0" + " " + idAspect + " " +
                        rank++ + " " + scoreMap.get(idAspect) + " " + "salience-sec-context" ;
                runFileStrings.add(runFileString);
            }
        }
    }

    /**
     * Helper method.
     * Scores a list of candidate aspects using the context provided.
     * @param context Context May be either sentence, paragraph or section context.
     * @param candidateAspects List List of candidate aspects.
     * @return Map
     */

    @NotNull
    private Map<String, Double> score(String entityID,
                                      @NotNull Context context,
                                      List<Aspect> candidateAspects) {

        Map<String, Double> paraScoreMap = new HashMap<>(); // Map where Key = id_aspect, value = Score of aspect
        double score;
        String content = context.getContent(); // Get the content of the context

        // Use SWAT to find the salient entities in the context
        Map<String, Double> salientEntities = SWATApi.getSalientEntities(content);

        // If any salient entities were found then
        if (salientEntities != null) {

            // Get the list of salient entities. But first lowercase them all.
            List<String> salientEntityList = lowercase(new ArrayList<>(salientEntities.keySet()));

            // For every candidate aspect
            for (Aspect aspect : candidateAspects) {

                // Get the aspect id
                String aspectId = aspect.getId();

                // Get the list of entities in the aspect
                List<String> aspectEntityList = getAspectEntityList(entityID, aspect);

                // Find any common entities between the list of salient entities for the context and the aspect.
                // Basically, we are finding how many aspect entities are salient in the context.
                List<String> common = Utilities.intersection(salientEntityList, aspectEntityList);

                // Is there anything common between the two?
                // If yes, then calculate the score of the candidate aspect.
                // Score calculated by by summing over the salience score of the common entities.
                // Add the id_context and its score to the map.
                // Otherwise, add a score of zero.
                if (!common.isEmpty()) {
                    score = sum(common, salientEntities);
                    paraScoreMap.put(aspectId, score);
                } else {
                    paraScoreMap.put(aspectId, 0.0d);
                }
            }
        } else {
            // If you are here, it means no salient entities were found by SWAT.
            System.err.println("ERROR: SWAT did not find any salient entities for: " + entityID);
            return new HashMap<>();
        }
        return Utilities.sortByValueDescending(paraScoreMap); // Sort in descending order.
    }

    /**
     * Helper method.
     * Finds  the entities in the aspect content.
     * Uses both the provided entities and those returned by SWAT.
     *
     * @param entityID String
     * @param aspect Aspect
     * @return List
     */

    @NotNull
    private List<String> getAspectEntityList(String entityID, @NotNull Aspect aspect) {

        // Use all the entities provided with the aspect in the data.
        List<String> aspectEntityList = lowercase(aspect.getEntityList());

        // But also use the entities in the content.
        HashMap<String, Integer> aspectEntities = aspectEntityMap.get(entityID).get(aspect.getId());
        for (String aspectEntity : aspectEntities.keySet()) {
            aspectEntityList.add(aspectEntity.toLowerCase());
        }
        return aspectEntityList;
    }

    private double sum(@NotNull List<String> common, Map<String, Double> salientEntities) {
        double sum = 0.0d;
        for (String e : common) {
            sum += salientEntities.get(e);
        }
        return sum;
    }

    @NotNull
    private List<String> lowercase(@NotNull List<String> list) {
        List<String> newList = new ArrayList<>();
        for (String s : list) {
            s = s.toLowerCase();
            newList.add(s);
        }
        return newList;
    }

    private double findAccuracy() {
        double accuracy;

        int totalMentions = accuracyMap.size();
        int totalCorrect = Collections.frequency(accuracyMap.values(), 1);
        accuracy = (double)totalCorrect / totalMentions;
        accuracyList.add(accuracy);
        return accuracy;
    }

    private void findAccuracy(JSONObject jsonObject,
                              @NotNull Map<String, Double> finalScores) {

        String correctAspectId = JsonObject.getCorrectAspectId(jsonObject);
        String mention = JsonObject.getMention(jsonObject);
        Map.Entry<String, Double> entry = finalScores.entrySet().iterator().next();
        String predictedAspectId = entry.getKey();

        if (correctAspectId.equalsIgnoreCase(predictedAspectId)) {
            accuracyMap.put(mention, 1);
        } else {
            accuracyMap.put(mention, 0);
        }
    }

    /**
     * Main method.
     * @param args Command Line Arguments.
     */

    public static void main(@NotNull String[] args) {
        String dataDir = args[0];
        String outputDir = args[1];
        String jsonFile = args[2];
        String aspectEntityFile = args[3];
        String runFile = args[4];
        String accuracyFile = args[5];
        new Experiment1(dataDir, outputDir, jsonFile, aspectEntityFile, runFile, accuracyFile);
    }

}
