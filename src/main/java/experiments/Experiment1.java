package experiments;

import api.SWATApi;
import help.Utilities;
import json.Aspect;
import json.Context;
import json.JsonObject;
import json.ReadJsonlFile;
import me.tongfei.progressbar.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ==============================Experiment-1===================================================
 * 1) (a) Use "content" from either sent_context or para_context or sect_context.
 *    (b) Pass content to SWAT. Get map of (entity,sal_score).
 * 2) Aspect candidates are the candidates we are trying to score.
 *   -- For Every aspect content, score the content by summing over the
 *      salience score of entities contained in it. Two variations:
 *      > Use only salient entities
 *      > Use all entities (salient and non-salient)
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
    private final String mode, contextType;
    private final boolean parallel;

    public Experiment1(String mainDir,
                       String dataDir,
                       String outputDir,
                       String jsonFile,
                       String aspectEntityFile,
                       String runFile,
                       String contextType,
                       @NotNull String mode,
                       boolean parallel) {

        String jsonFilePath = mainDir + "/" + dataDir + "/" + jsonFile;
        String aspectEntityFilePath = mainDir + "/" + dataDir + "/" + aspectEntityFile;
        String runFilePath = mainDir + "/" + outputDir + "/" + runFile;
        this.mode = mode;
        this.parallel = parallel;
        this.contextType = contextType;

        if (mode.equalsIgnoreCase("all")) {
            System.out.println("Using all entities.");
        } else {
            System.out.println("Using salient entities.");
        }

        if (contextType.equalsIgnoreCase("sent")) {
            System.out.println("Context: Sentence");
        } else if (contextType.equalsIgnoreCase("para")) {
            System.out.println("Context: Paragraph");
        } else {
            System.out.println("Context: Section");
        }

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

        score(runFilePath, jsonObjectList, contextType);
    }

    /**
     * Method to score candidate aspects for every entity mention.
     * @param runFilePath String Path to the run file.
     * @param jsonObjectList List List of JSON objects read from file.
     */

    private void score(String runFilePath, @NotNull List<JSONObject> jsonObjectList, String contextType) {

        if (parallel) {
            System.out.println("Using Parallel Streams.");
            int parallelism = ForkJoinPool.commonPool().getParallelism();
            int numOfCores = Runtime.getRuntime().availableProcessors();
            System.out.println("Number of available processors = " + numOfCores);
            System.out.println("Number of threads generated = " + parallelism);

            if (parallelism == numOfCores - 1) {
                System.err.println("WARNING: USING ALL AVAILABLE PROCESSORS");
                System.err.println("USE: \"-Djava.util.concurrent.ForkJoinPool.common.parallelism=N\" " +
                        "to set the number of threads used");
            }
            // Do in parallel
            jsonObjectList.parallelStream().forEach(jsonObject -> doTask(jsonObject, contextType));
        } else {
            System.out.println("Using Sequential Streams.");

            // Do in serial
            ProgressBar pb = new ProgressBar("Progress", jsonObjectList.size());
            for (JSONObject jsonObject : jsonObjectList) {
                doTask(jsonObject, contextType);
                pb.step();
            }
            pb.close();
        }

        System.out.print("Writing to run file...");
        Utilities.writeFile(runFileStrings, runFilePath);
        System.out.println("[Done].");
        System.out.println("Run file written to: " + runFilePath);

        if (mode.equalsIgnoreCase("all")) {
            System.out.println("Could not find any entities for " + counter + " of " + jsonObjectList.size() + " mentions");
        } else {
            System.out.println("Could not find salient entities for " + counter + " of " + jsonObjectList.size() + " mentions");
        }
        System.out.println("Done");
    }

    private void doTask(JSONObject jsonObject, @NotNull String contextType) {

        Map<String, Map<String, Double>> rankings = new HashMap<>(); // Map to store the rankings
        Map<String, Double> paraScoreMap; // Inner map

        String entityID = JsonObject.getEntityId(jsonObject);
        String mention = JsonObject.getMention(jsonObject);
        Context context;

        // Get the correct context based on argument
        if (contextType.equalsIgnoreCase("sent")) {
            context = JsonObject.getSentenceContext(jsonObject);
        } else if (contextType.equalsIgnoreCase("para")) {
            context = JsonObject.getParaContext(jsonObject);
        } else {
            context = JsonObject.getSectionContext(jsonObject);
        }

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
        System.out.println("Done: " + mention);

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
                        rank++ + " " + scoreMap.get(idAspect) + " " + "salience-" +  contextType + "-context-"
                        + mode + "-entities";
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
        Map<String, Double> swatAnnotations = SWATApi.getEntities(content, mode);


        // If any salient entities were found then
        if (! swatAnnotations.isEmpty()) {

            // Get the list of salient entities. But first lowercase them all.
            List<String> swatEntityList = new ArrayList<>(swatAnnotations.keySet());

            // For every candidate aspect
            for (Aspect aspect : candidateAspects) {

                // Get the aspect id
                String aspectId = aspect.getId();

                // Get the list of entities in the aspect
                List<String> aspectEntityList = getAspectEntityList(entityID, aspect);

                // Find any common entities between the list of salient entities for the context and the aspect.
                // Basically, we are finding how many aspect entities are salient in the context.
                List<String> common = Utilities.intersection(swatEntityList, aspectEntityList);

                // Is there anything common between the two?
                // If yes, then calculate the score of the candidate aspect.
                // Score calculated by by summing over the salience score of the common entities.
                // Add the id_context and its score to the map.
                // Otherwise, add a score of zero.
                if (!common.isEmpty()) {
                    score = sum(common, swatAnnotations);
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
        List<String> aspectEntityList = aspect.getEntityList();

        // But also use the entities in the content.
        HashMap<String, Integer> aspectEntities = aspectEntityMap.get(entityID).get(aspect.getId());
        aspectEntityList.addAll(aspectEntities.keySet());
        return aspectEntityList;
    }

    private double sum(@NotNull List<String> common, Map<String, Double> swatEntities) {
        double sum = 0.0d;
        for (String e : common) {
            sum += swatEntities.get(e);
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

    /**
     * Main method.
     * @param args Command Line Arguments.
     */

    public static void main(@NotNull String[] args) {
        String mainDir = args[0];
        String dataDir = args[1];
        String outputDir = args[2];
        String jsonFile = args[3];
        String aspectEntityFile = args[4];
        String contextType = args[5];
        String mode = args[6];
        String p = args[7];

        boolean parallel = false;
        if (p.equalsIgnoreCase("true")) {
            parallel = true;
        }

        String runFile = "salience-" +  contextType + "-context-" + mode + "-entities.run";

        new Experiment1(mainDir, dataDir, outputDir, jsonFile, aspectEntityFile, runFile, contextType, mode, parallel);
    }

}
