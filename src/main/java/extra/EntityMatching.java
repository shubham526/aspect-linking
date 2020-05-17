package extra;

import help.Utilities;
import json.Aspect;
import json.JsonObject;
import json.ReadJsonlFile;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.*;



/**
 * Match entities from context with aspect.
 * Variations:
 * (1) All context + Salient aspect
 * (2) Salient context + All aspect
 * (3) Salient context + Salient aspect
 * Score matching entities by salience.
 * @author Shubham Chatterjee
 * @version 05/12/2020
 */

public class EntityMatching {
    private final ArrayList<String> runFileStrings = new ArrayList<>();
    private Map<String, HashMap<String, Double>> contextEntityMap = new HashMap<>();
    private Map<String, HashMap<String, HashMap<String, Double>>> aspectEntityMap = new HashMap<>();


    public EntityMatching(String mainDir,
                          String dataDir,
                          String outputDir,
                          String jsonFile,
                          String runFile,
                          String salContextEntityFile,
                          String allContextEntityFile,
                          String salAspectEntityFile,
                          String allAspectEntityFile,
                          @NotNull String contextMatch,
                          String contentMatch) {


        String jsonFilePath = mainDir + "/" + dataDir + "/"  + jsonFile;
        String runFilePath = mainDir + "/" + outputDir + "/" + runFile;
        String salContextEntityFilePath = mainDir + "/" + dataDir + "/" + salContextEntityFile;
        String allContextEntityFilePath = mainDir + "/" + dataDir + "/" + allContextEntityFile;
        String salAspectEntityFilePath = mainDir + "/" + dataDir + "/" + salAspectEntityFile;
        String allAspectEntityFilePath = mainDir + "/" + dataDir + "/" + allAspectEntityFile;


        System.out.print("Reading JSON-L file....");
        List<JSONObject> jsonObjectList = ReadJsonlFile.read(jsonFilePath);
        System.out.println("[Done].");

        if (contextMatch.equalsIgnoreCase("sal")) {
            System.out.print("Reading the salient context entity file...");
            try {
                contextEntityMap = Utilities.readMap(salContextEntityFilePath);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            System.out.print("Reading the all context entity file...");
            try {
                contextEntityMap = Utilities.readMap(allContextEntityFilePath);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        System.out.println("[Done].");

        if (contentMatch.equalsIgnoreCase("sal")) {
            System.out.print("Reading the salient aspect entity file...");
            try {
                aspectEntityMap = Utilities.readMap(salAspectEntityFilePath);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        } else {System.out.print("Reading the all aspect entity file...");
            try {
                aspectEntityMap = Utilities.readMap(allAspectEntityFilePath);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        System.out.println("[Done].");

        rankEntities(jsonObjectList);

        System.out.print("Writing to file...");
        Utilities.writeFile(runFileStrings, runFilePath);
        System.out.println("[Done].");
        System.out.println("Run file written to: " + runFilePath);

        System.out.println("Could not match ");
    }

    private void rankEntities(@NotNull List<JSONObject> jsonObjectList) {

        Map<String, Map<String, Double>> rankings = new HashMap<>();

        Map<String, Double> swatContextEntities;
        Map<String, Double> swatContentEntities;

        Set<String> contextEntitySet;
        Set<String> contentEntitySet;

        for (JSONObject jsonObject : jsonObjectList) {
            String mention = JsonObject.getMention(jsonObject);
            String idContext = JsonObject.getIdContext(jsonObject);
            //if (!mention.equalsIgnoreCase("legal status")) continue;


            // Get the candidate aspects
            List<Aspect> candidateAspects = JsonObject.getAspectCandidates(jsonObject);

            // Get salient/all entities in context
            if (contextEntityMap.containsKey(idContext)) {

                swatContextEntities = contextEntityMap.get(idContext);
                contextEntitySet = swatContextEntities.keySet();

                for (Aspect aspect : candidateAspects) {

                    String aspectID = aspect.getId();

                    if (aspectEntityMap.get(idContext).containsKey(aspectID)) {
                        swatContentEntities = aspectEntityMap.get(idContext).get(aspectID);
                        contentEntitySet = swatContentEntities.keySet();

                        // Find any common entities between the list of entities for the context and the aspect.
                        contentEntitySet.retainAll(contextEntitySet);
                        List<String> common = new ArrayList<>(contentEntitySet);
                        if (!common.isEmpty()) {
                            rankEntitiesForAspect(aspectID, common, swatContentEntities, rankings);

                        }
                    } else {
                        System.err.println("ERROR: SWAT did not return any entities for aspect content.");
                    }
                }
            } else {
                System.err.println("ERROR: SWAT did not return any entities for context.");
            }
            System.out.println("Done: " + mention);
        }
        makeRunFileStrings(rankings);
    }

    private void rankEntitiesForAspect(String aspectID,
                                       @NotNull List<String> common,
                                       Map<String, Double> swatContentEntities,
                                       Map<String, Map<String, Double>> rankings) {

        Map<String, Double> inner = new HashMap<>();
        double score = 0.0d;

        for (String entity : common) {
            try {

                score = swatContentEntities.get(entity);
            } catch (NullPointerException e) {
                System.err.println("ERROR in rankEntitiesForAspect: NullPointerException");
            }

            inner.put(entity, score);

        }
        rankings.put(aspectID, inner);
    }


    private void makeRunFileStrings(@NotNull Map<String, Map<String, Double>> rankings) {

        String runFileString; // A candidate run file string
        int rank; // Rank of the aspect
        Map<String, Double> scoreMap; // Map of scores
        String info = "entity-matching";

        for (String idAspect : rankings.keySet()) {
            scoreMap = Utilities.sortByValueDescending(rankings.get(idAspect));
            rank = 1;
            for (String entity : scoreMap.keySet()) {
                if (!entity.equals("")) {
                    runFileString = idAspect + " " + "0" + " " + entity + " " + rank++ + " " + scoreMap.get(entity)
                            + " " + info;
                    if (!runFileStrings.contains(runFileString)) {
                        runFileStrings.add(runFileString);
                    }
                }

            }
        }
    }

    public static void main(@NotNull String[] args) {
        String mainDir = args[0];
        String dataDir = args[1];
        String outputDir = args[2];
        String jsonFile = args[3];
        @NotNull String contextType = args[4];
        String salContextEntityFile = args[5];
        String allContextEntityFile = args[6];
        String salAspectEntityFile = args[7];
        String allAspectEntityFile = args[8];
        String contextMatch = args[9];
        String contentMatch = args[10];

        if (contextType.equalsIgnoreCase("sent")) {
            System.out.println("Context: Sentence");
        } else if (contextType.equalsIgnoreCase("para")) {
            System.out.println("Context: Paragraph");
        } else {
            System.out.println("Context: Section");
        }

        System.out.println("Context: " + contextMatch);
        System.out.println("Aspect: " + contentMatch);


        String runFile = "entity-matching-using-" + contextType + "-context-matching-" + contextMatch
                + "-entities-with-" + contentMatch + "-aspect-entities.run";
        new EntityMatching(mainDir, dataDir, outputDir, jsonFile, runFile, salContextEntityFile,
                allContextEntityFile, salAspectEntityFile, allAspectEntityFile, contextMatch, contentMatch);
    }
}
