package json;

/**
 * Class to read a JSON-L file.
 * @author Shubham Chatterjee
 * @version 03/02/2020
 */

import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ReadJsonlFile {

    /**
     * Method to read the JSON-L file passed as parameter.
     * @param filePath String Path to the JSON-L file.
     * @return List List of JSON Objects.
     */

    @NotNull
    public static List<JSONObject> read(String filePath) {
        BufferedReader br;
        List<JSONObject> jsonObjectList = new ArrayList<>();
        try {
            br = new BufferedReader(new FileReader(filePath));

            String jsonlLine;
            while ((jsonlLine = br.readLine()) != null) {
                read(jsonlLine, jsonObjectList);
            }
    } catch (IOException ex) {
            ex.printStackTrace();
        }
        return jsonObjectList;
    }

    /**
     * Helper method to read a JSON-L line.
     * @param jsonLine String Line to read
     * @param jsonList List List of JSON objects
     */

    private static void read(String jsonLine, @NotNull List<JSONObject> jsonList) {
        JSONParser parser = new JSONParser();

        try {
            jsonList.add((JSONObject) parser.parse(jsonLine));
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }
}
