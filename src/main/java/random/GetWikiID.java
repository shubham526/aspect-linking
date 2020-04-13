package random;

import api.WATApi;
import help.Utilities;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to find the Wikipedia ID of a given Page Title and store it in a Map.
 * Then write the map to disk.
 * This is done for the entire aspect-linking data set.
 *
 * @author Shubham Chatterjee
 * @version 03/14/2020
 */

public class GetWikiID {
    // HashMap(Key = EntityID of given entity, Value = Map(Key = Entity in sentence context, Value = Corresponding ID))
    private  Map<String, HashMap<String, Integer>> sentContextEntityToIdMap = new HashMap<>();

    // HashMap(Key = EntityID of given entity, Value = Map(Key = Entity in paragraph context, Value = Corresponding ID))
    private  Map<String, HashMap<String, Integer>> paraContextEntityToIdMap = new HashMap<>();

    // HashMap(Key = EntityID of given entity, Value = Map(Key = CandidateAspectID, Value = Map(Key = Entity in aspect, Value = Corresponding ID)))
    private  Map<String, HashMap<String, HashMap<String, Integer>>> aspectEntityToIdMap = new HashMap<>();

    private  Map<String, List<String>> sentContextEntityMap;
    private  Map<String, List<String>> paraContextEntityMap;
    private  Map<String, HashMap<String, List<String>>> aspectEntityMap;

    public GetWikiID(String dataDir,
                     String sentenceContextEntityFile,
                     String paraContextEntityFile,
                     String aspectEntityMapFile,
                     String sentenceContextEntityToIdFile,
                     String paraContextEntityToIdFile,
                     String aspectEntityToIdFile) throws IOException, ClassNotFoundException {

        String sentenceContextEntityFilePath = dataDir + "/" + sentenceContextEntityFile;
        String paraContextEntityFilePath = dataDir + "/" + paraContextEntityFile;
        String aspectEntityMapFilePath = dataDir + "/" + aspectEntityMapFile;
        String sentenceContextEntityToIdFilePath = dataDir + "/" + sentenceContextEntityToIdFile;
        String paraContextEntityToIdFilePath = dataDir + "/" + paraContextEntityToIdFile;
        String aspectEntityToIdFilePath = dataDir + "/" + aspectEntityToIdFile;

        System.out.print("Reading sentence context entity file...");
        sentContextEntityToIdMap = Utilities.readMap(sentenceContextEntityFilePath);
        System.out.println("[Done].");

        System.out.print("Reading paragraph context entity file...");
        paraContextEntityToIdMap = Utilities.readMap(paraContextEntityFilePath);
        System.out.println("[Done].");

        System.out.print("Reading aspect entity file...");
        aspectEntityToIdMap = Utilities.readMap(aspectEntityMapFilePath);
        System.out.println("[Done].");

        findID();

        System.out.print("Writing maps to file...");
        Utilities.writeMap(sentContextEntityToIdMap, sentenceContextEntityToIdFilePath);
        Utilities.writeMap(paraContextEntityToIdMap, paraContextEntityToIdFilePath);
        Utilities.writeMap(aspectEntityToIdMap, aspectEntityToIdFilePath);
        System.out.println("[Done].");
    }

    private void findID() {
        List<String> entityList = new ArrayList<>(sentContextEntityMap.keySet());
        System.out.println(entityList.size());
        HashMap<String, Integer> innerMap = new HashMap<>();

        // Do in parallel
        //entityList.parallelStream().forEach(this::doTask);

        // Do in serial

        for (String entity : entityList) {
            doTask(entity);
        }
    }

    private void doTask(String entity) {
        HashMap<String, Integer> innerMap;
        System.out.println("+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-");

        //////////////// FIND ID FOR EVERY ENTITY IN SENTENCE CONTEXT//////////////
        System.out.print("Getting ID for entities in sentence context....");
        List<String> sentenceContextEntityList = sentContextEntityMap.get(entity);
        innerMap = new HashMap<>();
        for (String sentEntity : sentenceContextEntityList) {
            int sentEntityId = WATApi.TitleResolver.getId(sentEntity);
            innerMap.put(sentEntity, sentEntityId);
        }
        sentContextEntityToIdMap.put(entity, innerMap);
        System.out.println("[Done].");
        ///////////////////////////////////////////////////////////////////////////


        //////////////// FIND ID FOR EVERY ENTITY IN PARAGRAPH CONTEXT//////////////
        System.out.print("Getting ID for entities in paragraph context....");
        List<String> paraContextEntityList = paraContextEntityMap.get(entity);
        innerMap = new HashMap<>();
        int paraEntityId;
        for (String paraEntity : paraContextEntityList) {

            //First check if the paraEntity is present in the sentenceContextToIdMap
            HashMap<String, Integer> sentenceContextEntityToIdInnerMap = sentContextEntityToIdMap.get(entity);

            if (sentenceContextEntityToIdInnerMap.containsKey(paraEntity)) {
                // If it is present
                // Then we already have the id, no need to query the server
                paraEntityId = sentenceContextEntityToIdInnerMap.get(paraEntity);
            } else {
                // Otherwise get the id from the server
                paraEntityId = WATApi.TitleResolver.getId(paraEntity);
            }

            innerMap.put(paraEntity, paraEntityId);
        }
        paraContextEntityToIdMap.put(entity, innerMap);
        System.out.println("[Done].");
        ///////////////////////////////////////////////////////////////////////////


        //////////////// FIND ID FOR EVERY ENTITY IN EACH CANDIDATE ASPECT////////////
        System.out.print("Getting ID for entities in each candidate aspect....");
        HashMap<String, List<String>> aspectToEntityMap = aspectEntityMap.get(entity);
        HashMap<String, Integer> aspectInnerInnerMap = new HashMap<>();
        HashMap<String, HashMap<String, Integer>> aspectInnerMap = new HashMap<>();
        List<String> candidateAspectList = new ArrayList<>(aspectToEntityMap.keySet());
        for (String aspectId : candidateAspectList) {
            List<String> aspectEntityList = aspectToEntityMap.get(aspectId);
            for (String aspectEntity : aspectEntityList) {
                int aspectEntityId = WATApi.TitleResolver.getId(aspectEntity);
                aspectInnerInnerMap.put(aspectEntity, aspectEntityId);
            }
            aspectInnerMap.put(aspectId, aspectInnerInnerMap);
        }
        aspectEntityToIdMap.put(entity, aspectInnerMap);
        System.out.println("[Done].");
        ///////////////////////////////////////////////////////////////////////////
        System.out.println("Done: " + entity);
        System.out.println("+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-");
    }

    @Contract(pure = true)
    public static void main(@NotNull String[] args) {
        String dataDir = args[0];
        String sentenceContextEntityFile = args[1];
        String paraContextEntityFile = args[2];
        String aspectEntityMapFile = args[3];
        String sentenceContextEntityToIdFile = args[4];
        String paraContextEntityToIdFile = args[5];
        String aspectEntityToIdFile = args[6];
        try {
            new GetWikiID(dataDir, sentenceContextEntityFile, paraContextEntityFile, aspectEntityMapFile,
                    sentenceContextEntityToIdFile, paraContextEntityToIdFile, aspectEntityToIdFile);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }


    }

}
