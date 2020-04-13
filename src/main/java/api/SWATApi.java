package api;


import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * This class uses the SWAT API to find salient entities in a text given as input.
 * You can either annotate all entities in the text (salient as well as non-salient) or just the salient entities.
 * The class creates a Map of annotated entities and writes it to disk as a serialized file.
 * @author Shubham Chatterjee
 * @version 4/11/2020
 */

public class SWATApi {
    private final static String URL = "https://swat.d4science.org/salience";
    private final static String TOKEN = "8775ecea-90d0-4fca-89d3-e19c0790489f-843339462";

    /**
     * Returns a map of entities and salience score.
     * @param text String Text to annotate
     * @param type String Type of entities required.
     *             Can be either: all (all entities) or
     *                            salient (only salient entities)
     * @return Map of (entity, salience)
     */

    @NotNull
    public static Map<String, Double> getEntities(String text, String type) {
        Map<String, Double> entityMap = new HashMap<>();
        URL u = getURL();
        assert u != null;
        URLConnection connection = setUpConnection(u);
        doTask(connection, text, entityMap, type);
        return entityMap;

    }

    private static void doTask(URLConnection connection, String text, Map<String, Double> entityMap, String type) {
        String jsonInputString = "{\"content\": \"" + text + "\"}";

        write(jsonInputString, connection);
        String res = read(connection);

        if (res.isEmpty()) {
            System.err.println("Server returned no result.");
            return;
        }

        try {
            JSONObject response = new JSONObject(res);
            String status = response.getString("status");
            if ("ok".equals(status)) {
                JSONArray jsonObjects = response.getJSONArray("annotations");
                getEntities(jsonObjects, entityMap, type);
            }
        } catch (JSONException e) {
            System.err.println("ERROR: JSONException");
            e.printStackTrace();

        }
    }

    /**
     * Get the URL for the SWAT API.
     * This method adds the parameter "gcube-token" to the URL.
     * @return URL
     */
    @Nullable
    private static URL getURL() {
        URIBuilder ub = null;
        try {
            ub = new URIBuilder(URL);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        assert ub != null;
        ub.addParameter("gcube-token", TOKEN);
        String url_new = ub.toString();
        try {
            return new URL (url_new);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Setup the connection to the SWAT API.
     * @param url URL to connect to.
     * @return URLConnection
     */
    @NotNull
    private static URLConnection setUpConnection(@NotNull URL url) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection)url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            assert con != null;
            con.setRequestMethod("POST");
        } catch (ProtocolException e) {
            e.printStackTrace();
        }
        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");
        con.setDoOutput(true);
        return con;
    }

    /**
     * Write the data in JSON format to the output stream.
     * @param jsonInputString The text in JSON format.
     * @param connection URLConnection
     */
    private static void write(@NotNull String jsonInputString,
                              @NotNull URLConnection connection) {
        try(OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Read the response from the server.
     * @param connection URLConnection
     * @return String The response read
     */
    @NotNull
    private static String read(@NotNull URLConnection connection) {

        InputStream inputStream;
        try {
            inputStream = connection.getInputStream();
        } catch (IOException e) {
            System.err.println("ERROR: IOException");
            System.err.println("Input Stream not available.");
            return "";
        }

        try {
            if (inputStream.available() == 0) {
                System.err.println("InputStream available but no data to read.");
                return "";
            }
        } catch (IOException e) {
            System.err.println("ERROR: IOException");
            System.err.println("InputStream available but no data to read.");
            return "";
        }


        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        } catch (IOException e) {
            System.err.println("ERROR: IOException while reading from InputStream");
            e.printStackTrace();
        }
        return response.toString();
    }

    /**
     * Get the salient entities in the text.
     * @param jsonArray The array of JSON objects. Entities annotated by SWAT.
     * @param entities The list of salient entities based on "salience_class"
     */
    private static void  getEntities(@NotNull JSONArray jsonArray, Map<String, Double> entities, @NotNull String type)  {
        if (type.equalsIgnoreCase("sal")) {
            try {

                for (int i = 0; i < jsonArray.length(); i++) {
                    Object ob = jsonArray.get(i);
                    JSONObject o = (JSONObject) ob;
                    if (o.getDouble("salience_class") == 1.0) {
                        String title = o.getString("wiki_title");
                        double score = o.getDouble("salience_score");
                        entities.put(title, score);
                    }
                }
            } catch (JSONException e) {
                System.err.println("ERROR: JSONException while getting entities from SWAT.");
            }
        } else {
            try {
                for (int i = 0; i < jsonArray.length(); i++) {
                    Object ob = jsonArray.get(i);
                    JSONObject o = (JSONObject) ob;
                    String title = o.getString("wiki_title");
                    double score = o.getDouble("salience_score");
                    entities.put(title, score);
                }
            } catch (JSONException e) {
                System.err.println("ERROR: JSONException while getting entities from SWAT.");
            }
        }
    }

    /**
     * Main method.
     * Just for checking purposes.
     * @param args command line arguments
     */

    public static void main(@NotNull String[] args) {

        String text = "Mohandas Karamchand Gandhi was born on 2 October 1869 into an Indian Gujarati Hindu Modh Baniya family  in Porbandar (also known as Sudamapuri), a coastal town on the Kathiawar Peninsula and then part of the small princely state of Porbandar in the Kathiawar Agency of the Indian Empire. His father, Karamchand Uttamchand Gandhi (1822–1885), served as the diwan (chief minister) of Porbandar state.[22]";
        Map<String, Double> entities = getEntities(text, "sal");
        System.out.println("Found: " + entities.size());
    }
}

