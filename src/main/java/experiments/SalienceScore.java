package experiments;

import help.EntitySalience;
import help.Utilities;
import json.Aspect;
import json.Context;
import json.JsonObject;
import json.ReadJsonlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;

import java.util.*;

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

public class SalienceScore {

    /**
     * Constructor.
     * @param jsonFilePath String  Path to the JSON-L file.
     * @param runFilePath String Path to the run file.
     */

    public SalienceScore(String jsonFilePath, String runFilePath) {
        List<JSONObject> jsonObjectList = ReadJsonlFile.read(jsonFilePath);
        score(runFilePath, jsonObjectList);
    }

    /**
     * Method to score candidate aspects for every entity mention.
     * @param runFilePath String Path to the run file.
     * @param jsonObjectList List List of JSON objects read from file.
     */

    private void score(String runFilePath, @NotNull List<JSONObject> jsonObjectList) {
        Map<String, Map<String, Double>> rankings = new HashMap<>(); // Map to store the rankings
        Map<String, Double> paraScoreMap; // Inner map
        int c = 0;

        // For every JSON object do
        for (JSONObject jsonObject : jsonObjectList) {

            // Get the paragraph context of the mention

            /*
             * MODIFY THIS LINE TO USE EITHER getSentenceContext(), getParaContext() OR getSectionContext().
             */
            Context context = JsonObject.getSectionContext(jsonObject);

            // Get the list of candidate aspects for the mention
            List<Aspect> candidateAspects = JsonObject.getAspectCandidates(jsonObject);

            // Score the candidate aspects for the mention using the context.
            // Delegates the task to a helper method.
            paraScoreMap = score(context, candidateAspects);


            // Note that the helper method can return null, so important to check for null return values
            if (paraScoreMap != null) {
                // Make a map entry where Key = id_context and Value = Map of (id_context, score)
                rankings.put(JsonObject.getIdContext(jsonObject), paraScoreMap);
            } else {
                // If you are here then it means that no salient entities were found for the context
                // Count this. Useful statistic (maybe!)
                c++;
            }
            System.out.println("Done: " + JsonObject.getMention(jsonObject));
        }
        makeRunFile(rankings, runFilePath); // Make the run file
        System.out.println("Could not find salient entities for " + c + " of " + jsonObjectList.size() + " mentions");
        System.out.println("Done");
    }

    /**
     * Helper method.
     * Makes a run file in the format: id_context 0 id_aspect rank score run_name.
     * @param rankings Map Map of rankings for the mention.
     * @param runFilePath String Path to the run file.
     */

    private void makeRunFile(@NotNull Map<String, Map<String, Double>> rankings, String runFilePath) {

        ArrayList<String> runStrings = new ArrayList<>(); // List to store run file strings
        String runFileString; // A candidate run file string
        int rank; // Rank of the aspect
        Map<String, Double> scoreMap; // Map of scores

        for (String idContext : rankings.keySet()) {
            scoreMap = rankings.get(idContext);
            rank = 1;
            for (String idAspect : scoreMap.keySet()) {
                runFileString = idContext + " " + " 0 " + idAspect + " " +
                        rank++ + " " + scoreMap.get(idAspect) + " salience-sec-context" ;
                runStrings.add(runFileString);
            }
        }
        Utilities.writeFile(runStrings, runFilePath);
    }

    /**
     * Helper method.
     * Scores a list of candidate aspects using the context provided.
     * @param context Context May be either sentence, paragraph or section context.
     * @param candidateAspects List List of candidate aspects.
     * @return Map
     */

    @Nullable
    private Map<String, Double> score(@NotNull Context context, List<Aspect> candidateAspects) {

        Map<String, Double> paraScoreMap = new HashMap<>(); // Map where Key = id_aspect, value = Score of aspect
        double score;
        String content = context.getContent(); // Get the content of the context

        // Use SWAT to find the salient entities in the context
        Map<String, Double> salientEntities = EntitySalience.getSalientEntities(content);

        // If any salient entities were found then
        if (salientEntities != null) {

            // Get the list of salient entities. But first lowercase them all.
            List<String> salientEntityList = lowercase(new ArrayList<>(salientEntities.keySet()));

            // For every candidate aspect
            for (Aspect aspect : candidateAspects) {

                // Get the aspect id
                String aspectId = aspect.getId();

                // Get the list of entities in the aspect
                List<String> aspectEntityList = getAspectEntityList(aspect);

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
            return null;
        }
        return Utilities.sortByValueDescending(paraScoreMap); // Sort in descending order.
    }

    /**
     * Helper method.
     * Finds  the entities in the aspect content.
     * Uses both the provided entities and those returned by SWAT.
     * @param aspect Aspect
     * @return List
     */

    @NotNull
    private List<String> getAspectEntityList(@NotNull Aspect aspect) {

        // Use all the entities provided with the aspect in the data.
        List<String> aspectEntityList = lowercase(aspect.getEntityList());

        // But also use the entities in the content.
        String aspectContent = aspect.getContent();
        Map<String, Double> aspectSalientEntities = EntitySalience.getSalientEntities(aspectContent);

        // Does SWAT return any salient entities?
        // If yes, then add them to the list.
        // But first lowercase them.
        if (aspectSalientEntities != null) {
            for (String e : aspectSalientEntities.keySet()) {
                aspectEntityList.add(e.toLowerCase());
            }
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

    public static void main(@NotNull String[] args) {
        String file = args[0];
        String runFile = args[1];
        new SalienceScore(file, runFile);
    }

}
