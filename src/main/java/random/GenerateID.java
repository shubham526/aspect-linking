package random;

import help.Utilities;
import json.Context;
import json.JsonObject;
import json.ReadJsonlFile;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Class to generate a unique ID for every context.
 * ==============================METHOD===============================
 * (1) Read the JSON-L data file.
 * (2) For every JSON object, get the sentence/paragraph/section context.
 * (3) Generate a random ID for the context in (2).
 * (4) Store the (id, content) in a file.
 * ===================================================================
 *
 * @author Shubham Chatterjee
 * @version 03/12/2020
 */

public class GenerateID {

    /**
     * Constructor.
     * @param jsonFile String Path to the JSON-L file.
     * @param outputFolder String Path to the folder where the files will be stored.
     * @param textFile String Name of the text file.
     * @param serializedFile String Name of the serialized file.
     * @param n Integer Length of the id.
     */

    public GenerateID(String jsonFile,
                      String outputFolder,
                      String textFile,
                      String serializedFile,
                      int n) {
        List<JSONObject> jsonObjectList = ReadJsonlFile.read(jsonFile);
        String textFilePath = outputFolder + "/" + textFile;
        String serializedFilePath = outputFolder + "/" + serializedFile;
        generateId(jsonObjectList, textFilePath, serializedFilePath, n);
    }

    /**
     * Method to create a unique id.
     * @param jsonObjectList List List of JSON objects.
     * @param textFilePath String Path to the text file.
     * @param serializedFilePath String Path to the serialized file.
     * @param n Integer Size of the id.
     */

    private void generateId(@NotNull List<JSONObject> jsonObjectList,
                            String textFilePath,
                            String serializedFilePath,
                            int n) {

        HashMap<String, String> idMap = new HashMap<>();

        System.out.println("Generating unique IDs of size: " + n);

        for (JSONObject jsonObject : jsonObjectList) {

            ////////// CHANGE THIS LINE TO USE OTHER CONTEXTS //////////
            Context context = JsonObject.getSectionContext(jsonObject);
            ///////////////////////////////////////////////////////////

            String content = context.getContent();
            String id = RandomString.getID(n);
            idMap.put(id, content);
        }
        writeToFile(idMap, textFilePath, serializedFilePath);
    }

    /**
     * Method to write the map to file and disk.
     * @param idMap Map Map of (id, content).
     * @param textFilePath String
     * @param serializedFilePath String
     */

    private void writeToFile(HashMap<String, String> idMap,
                             String textFilePath,
                             String serializedFilePath) {
        System.out.print("Serializing map....");
        // Serialize the Map to disk
        try {
            Utilities.writeMap(idMap, serializedFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("[Done].");

        // Write the Map to a file on disk
        System.out.print("Writing map to file as TSV file....");
        ArrayList<String> fileStrings = new ArrayList<>();
        for (String id : idMap.keySet()) {
            String content = idMap.get(id);
            String s = id + "\t" + content + "\n";
            fileStrings.add(s);
        }
        Utilities.writeFile(fileStrings, textFilePath);
        System.out.println("[Done].");
    }

    /**
     * Main method.
     * @param args Command line arguments.
     */

    public static void main(@NotNull String[] args) {
        String jsonFile = args[0];
        String outputFolder = args[1];
        String textFile = args[2];
        String serializedFile = args[3];
        int n = Integer.parseInt(args[4]);

        new GenerateID(jsonFile, outputFolder, textFile, serializedFile,n);
    }
}
