package random;

import api.WATApi;
import help.Utilities;
import json.Aspect;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;


/**
 * Class to get the entities and their IDs in the mention context and candidate aspects.
 * Then write them to a file on disk.
 * @author Shubham Chatterjee
 * @version 03/13/2020
 */

public class GetEntities {
    private final Map<String, Map<String, Integer>> sentContextEntityMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> paraContextEntityMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> secContextEntityMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, Integer>>> aspectEntityMap = new ConcurrentHashMap<>();
    private final boolean parallel;

    public GetEntities(String mainDir,
                       String dataDir,
                       String outputDir,
                       String jsonFile,
                       String sentContextEntityFile,
                       String paraContextEntityFile,
                       String secContextEntityFile,
                       String aspectEntityFile,
                       boolean parallel) {

        String jsonFilePath = mainDir + "/" + dataDir + "/" + jsonFile;
        String sentContextEntityFilePath = mainDir + "/" + outputDir + "/" + sentContextEntityFile;
        String paraContextEntityFilePath = mainDir + "/" + outputDir + "/" + paraContextEntityFile;
        String secContextEntityFilePath = mainDir + "/" + outputDir + "/" + secContextEntityFile;
        String aspectEntityFilePath = mainDir + "/" + outputDir + "/" + aspectEntityFile;
        this.parallel = parallel;

        System.out.print("Reading the JSON-L file...");
        List<JSONObject> jsonObjectList = ReadJsonlFile.read(jsonFilePath);
        System.out.println("[Done].");
        System.out.println("Found: " + jsonObjectList.size() + " JSON objects.");



        System.out.println("Getting entities in the context....");
        System.out.println("+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-");
        getEntities(jsonObjectList);
        System.out.println("+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-");
        System.out.println("[Done].");

        System.out.println("Writing to file...");
        try {
            Utilities.writeMap(sentContextEntityMap, sentContextEntityFilePath);
            Utilities.writeMap(paraContextEntityMap, paraContextEntityFilePath);
            Utilities.writeMap(secContextEntityMap,  secContextEntityFilePath);
            Utilities.writeMap(aspectEntityMap, aspectEntityFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("[Done].");



    }

    private void getEntities(@NotNull List<JSONObject> jsonObjectList) {

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
    }

    private void doTask(JSONObject jsonObject) {
        Map<String, Map<String, Integer>> aspectMap = new HashMap<>();
        Map<String, Integer> cntEntMap;
        Context context;
        String idContext = JsonObject.getIdContext(jsonObject);
        List<Aspect> candidateAspects = JsonObject.getAspectCandidates(jsonObject);

         //Get the Map of (Entity, WikiId) in the sentence context
        context = JsonObject.getSentenceContext(jsonObject);
        cntEntMap = getEntities(context);
        // Store
        sentContextEntityMap.putIfAbsent(idContext, cntEntMap);

        //Get the Map of (Entity, WikiId) in the paragraph context
        context = JsonObject.getParaContext(jsonObject);
        cntEntMap = getEntities(context);
        // Store
        paraContextEntityMap.putIfAbsent(idContext, cntEntMap);

        //Get the Map of (Entity, WikiId) in the section context
        context = JsonObject.getSectionContext(jsonObject);
        cntEntMap = getEntities(context);
        // Store
        secContextEntityMap.putIfAbsent(idContext, cntEntMap);

        // Get the entities in each candidate aspect
        for (Aspect aspect : candidateAspects) {
            HashMap<String, Integer> aspectEntityToIdMap = getEntities(aspect);
            aspectMap.put(aspect.getId(), aspectEntityToIdMap);
        }
        aspectEntityMap.put(idContext, aspectMap);
        System.out.println("Done: " + idContext);
    }

    /**
     * Helper method.
     * Returns the list of entities in the aspect.
     * Uses both entities provided in the data as well as those returned by WAT.
     * @param aspect Aspect
     * @return List
     */

    @NotNull
    private HashMap<String, Integer> getEntities(@NotNull Aspect aspect) {
        HashMap<String, Integer> entityToIdMap = new HashMap<>();

        // Get the list of entities provided with the data
        List<String> dataEntityList = aspect.getEntityList();
        // Get the ids for the entities
        getEntityId(dataEntityList, entityToIdMap);

        // Get the annotations from WAT API
        List<WATApi.Annotation> watEntityList = WATApi.EntityLinker.getAnnotations(aspect.getContent(),0);
        if (!watEntityList.isEmpty()) {
            for (WATApi.Annotation annotation : watEntityList) {
                entityToIdMap.put(annotation.getWikiTitle(), annotation.getWikiId());
            }
        } else {
            System.err.println("ERROR in GetEntities.getEntities(Aspect): WAT did not return any entities.");
        }
        return entityToIdMap;
    }

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
        else {
            System.err.println("ERROR in GetEntities.getEntities(Context): WAT did not return any entities.");
        }
        return entityToIdMap;
    }

    /**
     * Helper method.
     * Queries the WAT server to get the Wikipedia ID of the entity.
     * @param entityList List List of entities
     */

    private void getEntityId(@NotNull List<String> entityList, HashMap<String, Integer> entityToIdMap) {
        for (String entity : entityList) {
            int id = WATApi.TitleResolver.getId(entity);
            entityToIdMap.put(entity, id);
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
        String sentContextEntityFile = args[4];
        String paraContextEntityFile = args[5];
        String secContextEntityFile = args[6];
        String aspectEntityFile = args[7];
        String p = args[8];

        boolean parallel = false;
        if (p.equalsIgnoreCase("true")) {
            parallel = true;
        }


        new GetEntities(mainDir, dataDir, outputDir, jsonFile, sentContextEntityFile, paraContextEntityFile,
                secContextEntityFile, aspectEntityFile, parallel);

    }
}
