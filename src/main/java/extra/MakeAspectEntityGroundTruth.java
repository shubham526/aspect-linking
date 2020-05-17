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
 * Make ground truth data for aspect entities.
 * We define all entities in the aspect as relevant.
 * @author Shubham Chatterjee
 * @version 05/06/2020
 */

public class MakeAspectEntityGroundTruth {
    private final ArrayList<String> runFileStrings = new ArrayList<>();
    private Map<String, HashMap<String, HashMap<String, Integer>>> aspectEntityMap = new HashMap<>();
    public MakeAspectEntityGroundTruth(String data, String qrelFile, String aspectEntityFile) {

        System.out.print("Reading JSON-L file....");
        List<JSONObject> jsonObjectList = ReadJsonlFile.read(data);
        System.out.println("[Done].");

        System.out.print("Reading the aspect entity file...");
        try {
            aspectEntityMap = Utilities.readMap(aspectEntityFile);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("[Done].");

        makeGroundTruth(jsonObjectList);


        System.out.print("Writing to file...");
        Utilities.writeFile(runFileStrings, qrelFile);
        System.out.println("[Done].");
        System.out.println("Run file written to: " + qrelFile);
    }

    private void makeGroundTruth(@NotNull List<JSONObject> jsonObjectList) {
        for (JSONObject jsonObject : jsonObjectList) {
            String idContext = JsonObject.getIdContext(jsonObject);
            List<Aspect> candidateAspects = JsonObject.getAspectCandidates(jsonObject);
            // Get the entities in each candidate aspect
            for (Aspect aspect : candidateAspects) {
                String aspectID = aspect.getId();
                Set<String> aspectEntitySet = aspectEntityMap.get(idContext).get(aspectID).keySet();
                createRunFileStrings(aspectID, aspectEntitySet);
            }
            System.out.println("Done: " + JsonObject.getMention(jsonObject));
        }


    }

    private void createRunFileStrings(String aspectID, @NotNull Set<String> aspectEntitySet) {
        String runFileString = "";
        for (String entity : aspectEntitySet) {
            if (!entity.equals("")) {
                runFileString = aspectID + " " + "0" + " " + entity + " " + "1";
                if (! runFileStrings.contains(runFileString)) {
                    runFileStrings.add(runFileString);
                }
            }
        }
    }



    public static void main(@NotNull String[] args) {
        String data = args[0];
        String qrel = args[1];
        String aspectEntityFile = args[2];
        new MakeAspectEntityGroundTruth(data, qrel, aspectEntityFile);
    }
}
