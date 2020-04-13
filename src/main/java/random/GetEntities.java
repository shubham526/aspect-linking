package random;

import api.WATApi;
import help.Utilities;
import json.Context;
import json.JsonObject;
import json.ReadJsonlFile;
import me.tongfei.progressbar.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Class to get the entities and their IDs in the mention context and candidate aspects.
 * Then write them to a file on disk.
 * @author Shubham Chatterjee
 * @version 03/13/2020
 */

public class GetEntities {

    private Map<String, HashMap<String, Integer>> newSectionContextEntityMap = new HashMap<>();
    private Map<String, Integer> sectionContextEntityMap = new HashMap<>();

    public GetEntities(String mainDir,
                       String dataDir,
                       String outputDir,
                       String jsonFile,
                       String sectionContextEntityFile,
                       String newSectionContextEntityFile) {

        String jsonFilePath = mainDir + "/" + dataDir + "/" + jsonFile;
        String sectionContextEntityFilePath = mainDir + "/" + dataDir + "/" + sectionContextEntityFile;
        String newSectionContextEntityFilePath = mainDir + "/" + outputDir + "/" + newSectionContextEntityFile;

        System.out.print("Reading the JSON-L file...");
        List<JSONObject> jsonObjectList = ReadJsonlFile.read(jsonFilePath);
        System.out.println("[Done].");
        System.out.println("Found: " + jsonObjectList.size() + " JSON objects.");

        System.out.print("Reading old section context entity file....");
        try {
            sectionContextEntityMap = Utilities.readMap(sectionContextEntityFilePath);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("[Done].");

        System.out.println("Getting entities in the section context....");
        System.out.println("+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-");
        getEntities(jsonObjectList);
        System.out.println("+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-");
        System.out.println("[Done].");

        System.out.print("Writing to disk...");
        try {
            Utilities.writeMap(newSectionContextEntityMap, newSectionContextEntityFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("[Done].");
    }

    private void getEntities(@NotNull List<JSONObject> jsonObjectList) {

        // Do in parallel
        //jsonObjectList.parallelStream().forEach(this::doTask);

        // Do in serial
        ProgressBar pb = new ProgressBar("Progress", jsonObjectList.size());
        for (JSONObject jsonObject : jsonObjectList) {
            doTask(jsonObject);
            pb.step();
        }
        pb.close();
    }

    private void doTask(JSONObject jsonObject) {
//        HashMap<String, HashMap<String, Integer>> aspectMap = new HashMap<>();
//        System.out.println();
//

        Context context = JsonObject.getSectionContext(jsonObject); //CHANGE THIS LINE TO USE OTHER CONTEXTS
        String entityID = JsonObject.getEntityId(jsonObject);
        HashMap<String, Integer> entityToIdMap = getEntities(context);
        newSectionContextEntityMap.putIfAbsent(entityID, entityToIdMap);
//        String mention = JsonObject.getMention(jsonObject);
//        List<Aspect> candidateAspects = JsonObject.getAspectCandidates(jsonObject);
//        System.out.println("Mention: " + mention);
//        System.out.println("Found" + " " + candidateAspects.size() + " " + "candidate aspects.");
//
//
//        System.out.print("Getting entities in sentence context....");
//         //Get the sentence context
//        Context context = JsonObject.getSectionContext(jsonObject); //CHANGE THIS LINE TO USE OTHER CONTEXTS
//         //Get the list of entities in the context
//        getEntities(context);
//        // Store
//        System.out.println("[Done].");

//        //System.out.print("Getting entities in paragraph context....");
//
//        // Get the paragraph context
//        context = JsonObject.getParaContext(jsonObject); //CHANGE THIS LINE TO USE OTHER CONTEXTS
//        // Get the list of entities in the context
//        entityMap = getEntities(context);
//        // Store
//        paraContextEntityMap.put(entityId, entityMap);
//        //System.out.println("[Done].");
//
//        //System.out.println("Getting entities for every candidate aspect....");
//
//
//        // Get the entities in each candidate aspect
//        for (Aspect aspect : candidateAspects) {
//            HashMap<String, Integer> aspectEntityToIdMap = getEntities(aspect);
//            aspectMap.put(aspect.getId(), aspectEntityToIdMap);
//        }
//        aspectEntityMap.put(entityId, aspectMap);
//        //System.out.println("[Done].");
//        //System.out.println("============================================================");
//        //System.out.println("Done: " + mention);
    }

    private void copyToMap(@NotNull HashMap<String, Integer> entityMap) {
        for (String e : entityMap.keySet()) {
            sectionContextEntityMap.putIfAbsent(e, entityMap.get(e));
        }
    }

    /**
     * Helper method.
     * Returns the list of entities in the aspect.
     * Uses both entities provided in the data as well as those returned by WAT.
     * @param aspect Aspect
     * @return List
     */

//    @NotNull
//    private HashMap<String, Integer> getEntities(@NotNull Aspect aspect) {
//        HashMap<String, Integer> entityToIdMap = new HashMap<>();
//
//        // Get the list of entities provided with the data
//        List<String> dataEntityList = aspect.getEntityList();
//        // Get the ids for the entities
//        getEntityId(dataEntityList, entityToIdMap);
//
//        // Get the annotations from WAT API
//        List<WATApi.Annotation> watEntityList = WATApi.EntityLinker.getAnnotations(aspect.getContent(),0);
//        if (!watEntityList.isEmpty()) {
//            for (WATApi.Annotation annotation : watEntityList) {
//                entityToIdMap.put(annotation.getWikiTitle(), annotation.getWikiId());
//            }
//        } else {
//            System.err.println("ERROR in GetEntities.getEntities(Aspect): WAT did not return any entities.");
//        }
//        //System.out.println("Found" + " " + entityToIdMap.size() + " " + "entities for aspect" + " " + aspect.getId());
//        return entityToIdMap;
//    }

    /**
     * Returns all entities in the context.
     * @param context Context
     */


    @NotNull
    private HashMap<String, Integer> getEntities(@NotNull Context context) {
        HashMap<String, Integer> entityToIdMap = new HashMap<>();

        // Use entities provided with the data
        List<String> entityList = context.getEntityList();
        // Get the ids for the entities
        getEntityId(entityList, entityToIdMap);

        // But also use entities annotated with WAT API.
        String content = context.getContent();
        List<WATApi.Annotation> annotationList = WATApi.EntityLinker.getAnnotations(content,0.1);
        if (! annotationList.isEmpty()) {
            for (WATApi.Annotation annotation : annotationList) {
                entityToIdMap.put(annotation.getWikiTitle(), annotation.getWikiId());
            }
        }
//        else {
//            System.err.println("ERROR in GetEntities.getEntities(Context): WAT did not return any entities.");
//        }
        return entityToIdMap;
    }

    /**
     * Helper method.
     * Queries the WAT server to get the Wikipedia ID of the entity.
     * @param entityList List List of entities
     */

    private void getEntityId(@NotNull List<String> entityList, HashMap<String, Integer> entityToIdMap) {
        for (String entity : entityList) {
            // First check if id is already present in sectionContextEntityMap
            if (!sectionContextEntityMap.containsKey(entity)) {
                int id = WATApi.TitleResolver.getId(entity);
                entityToIdMap.put(entity, id);
            } else {
                entityToIdMap.put(entity, sectionContextEntityMap.get(entity));
            }
        }
    }

    /**
     * Main method.
     * @param args Command line arguments.
     */

    public static void main(@NotNull String[] args) {
        String mainDir = args[0];
        String dataDir = args[1];
        String outputDir = args[2];
        String jsonFile = args[3];
        String paraContextEntityFile = args[4];
        String secContextEntityFile = args[5];


        new GetEntities(mainDir, dataDir, outputDir, jsonFile, paraContextEntityFile,
                secContextEntityFile);

    }
}
