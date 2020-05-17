package experiments;

import api.SWATApi;
import help.Utilities;
import json.Aspect;
import json.JsonObject;
import json.ReadJsonlFile;
import me.tongfei.progressbar.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

/**
 * ==============================Experiment-1a===================================================
 * For every candidate aspect do:
 * 1) Use SWAT to find salient entities in the aspect.
 * 2) If entity mention is salient in the aspect then:
 *      Score(Aspect) = Salience score of entity mention in the aspect.
 *    Else:
 *      Score(Aspect) = 0
 * ==============================================================================================
 *
 * @author Shubham Chatterjee
 * @version 04/29/2020
 */

public class Experiment1a {
    private final ArrayList<String> runFileStrings = new ArrayList<>();
    private final String mode;
    private final boolean parallel;

    public Experiment1a(String mainDir,
                       String dataDir,
                       String outputDir,
                       String jsonFile,
                       String runFile,
                       @NotNull String mode,
                       boolean parallel) {

        String jsonFilePath = mainDir + "/" + dataDir + "/" + jsonFile;
        String runFilePath = mainDir + "/" + outputDir + "/" + runFile;
        this.mode = mode;
        this.parallel = parallel;

        if (mode.equalsIgnoreCase("all")) {
            System.out.println("Using all entities.");
        } else {
            System.out.println("Using salient entities.");
        }

        System.out.print("Reading JSON-L file....");
        List<JSONObject> jsonObjectList = ReadJsonlFile.read(jsonFilePath);
        System.out.println("[Done].");

        score(runFilePath, jsonObjectList);
    }

    /**
     * Method to score candidate aspects for every entity mention.
     * @param runFilePath String Path to the run file.
     * @param jsonObjectList List List of JSON objects read from file.
     */

    private void score(String runFilePath, @NotNull List<JSONObject> jsonObjectList) {

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
            jsonObjectList.parallelStream().forEach(this::doTask);
        } else {
            System.out.println("Using Sequential Streams.");

            // Do in serial
            ProgressBar pb = new ProgressBar("Progress", jsonObjectList.size());
            for (JSONObject jsonObject : jsonObjectList) {
                doTask(jsonObject);
                pb.step();
            }
            pb.close();
        }

        System.out.print("Writing to run file...");
        Utilities.writeFile(runFileStrings, runFilePath);
        System.out.println("[Done].");
        System.out.println("Run file written to: " + runFilePath);
    }

    private void doTask(JSONObject jsonObject) {

        Map<String, Map<String, Double>> rankings = new HashMap<>(); // Map to store the rankings
        Map<String, Double> paraScoreMap; // Inner map

        String idContext = JsonObject.getIdContext(jsonObject);
        String mention = JsonObject.getMention(jsonObject);
        String entityName = JsonObject.getEntityName(jsonObject);

        // Get the list of candidate aspects for the mention
        List<Aspect> candidateAspects = JsonObject.getAspectCandidates(jsonObject);

        // Score the candidate aspects for the mention using the context.
        // Delegates the task to a helper method.
        paraScoreMap = scoreAspects(entityName, candidateAspects);
        rankings.put(idContext, paraScoreMap);

        makeRunFileStrings(rankings);
        //System.out.println("Done: " + mention);

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
        String info = "salience-of-entity-in-aspect";

        for (String idContext : rankings.keySet()) {
            scoreMap = rankings.get(idContext);
            rank = 1;
            for (String idAspect : scoreMap.keySet()) {
                runFileString = idContext + " " + "0" + " " + idAspect + " " +
                        rank++ + " " + scoreMap.get(idAspect) + " " + info;
                runFileStrings.add(runFileString);
            }
        }
    }

    /**
     * Helper method.
     * Scores a list of candidate aspects using the context provided.
     * @param candidateAspects List List of candidate aspects.
     * @return Map
     */

    @NotNull
    private Map<String, Double> scoreAspects(String entityName,
                                      @NotNull List<Aspect> candidateAspects) {

        Map<String, Double> aspectScoreMap = new HashMap<>(); // Map where Key = id_aspect, value = Score of aspect
        // For every candidate aspect do
        for (Aspect aspect : candidateAspects) {

            // Get the salient entities in the aspect content
            String aspectContent = aspect.getContent();
            String aspectId = aspect.getId();
            Map<String, Double> swatAnnotations = SWATApi.getEntities(aspectContent, mode);

            // If the entity mention is salient in the aspect then its score is the salience score of entity, else zero
            aspectScoreMap.put(aspectId, swatAnnotations.getOrDefault(entityName, 0.0));
        }
        return Utilities.sortByValueDescending(aspectScoreMap); // Sort in descending order.
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
        String mode = args[4];
        String p = args[5];

        boolean parallel = false;
        if (p.equalsIgnoreCase("true")) {
            parallel = true;
        }

        String runFile = "salience-of-entity-in-aspect.run";

        new Experiment1a(mainDir, dataDir, outputDir, jsonFile, runFile, mode, parallel);
    }

}
