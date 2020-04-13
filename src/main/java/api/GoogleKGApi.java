package api;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Class to query the Google Knowledge Graph API.
 * @author Shubham Chatterjee
 * @version 03/10/2020
 */

public class GoogleKGApi {
    private final static String URL = "https://kgsearch.googleapis.com/v1/entities:search";
    private final static String KEY = "AIzaSyDsNR_C14cCMo0mZjqWUV5Tn7Mvv9yZIXY";

    /**
     * Inner class to represent a Google Knowledge Graph entity.
     */

    public static class Entity {
        private final String name;
        private final ArrayList<String> types;
        private final String description;
        private final String detailedDesc;
        private final int resultScore;

        public Entity(String name,
                      ArrayList<String> types,
                      String description,
                      String detailedDesc,
                      int resultScore) {

            this.name = name;
            this.types = types;
            this.description = description;
            this.detailedDesc = detailedDesc;
            this.resultScore = resultScore;
        }

        public String getName() {
            return name;
        }

        public ArrayList<String> getTypes() {
            return types;
        }

        public String getDescription() {
            return description;
        }

        public String getDetailedDesc() {
            return detailedDesc;
        }

        public int getResultScore() {
            return resultScore;
        }

        @Override
        public String toString() {
            return "Entity{" +
                    "name='" + name + '\'' +
                    ", types=" + types +
                    ", description='" + description + '\'' +
                    ", detailedDesc='" + detailedDesc + '\'' +
                    ", resultScore=" + resultScore +
                    '}';
        }
    }

    @NotNull
    public static ArrayList<Entity> query(String data) {
        ArrayList<Entity> entities = new ArrayList<>();
        String name = "", description = "", detailedDesc = "";
        ArrayList<String> types;
        int resultScore;


        try {
            Document doc = getDocument(data);
            if (doc.text() != null) {
                JSONObject json = new JSONObject(doc.text());
                if (json.has("itemListElement")) {
                    JSONArray jsonArray = json.getJSONArray("itemListElement");
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        types = new ArrayList<>();
                        resultScore = jsonObject.has("resultScore")
                                ? jsonObject.getInt("resultScore")
                                : 0;
                        if (jsonObject.has("result")) {
                            JSONObject resultOb = jsonObject.getJSONObject("result");
                            name = resultOb.has("name") ? resultOb.getString("name") : "";
                            if (resultOb.has("@type")) {
                                JSONArray typeArray = resultOb.getJSONArray("@type");
                                for (int j = 0; j < typeArray.length(); j++) {
                                    types.add(typeArray.getString(j));
                                }
                            }
                            description = resultOb.has("description")
                                    ? resultOb.getString("description")
                                    : "";
                            detailedDesc = resultOb.has("detailedDescription")
                                    ? resultOb.getJSONObject("detailedDescription").getString("articleBody")
                                    : "";
                        }
                        entities.add(new Entity(name, types, description, detailedDesc, resultScore));
                    }
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return entities;
    }

    /**
     * Helper method to connect to the URL.
     * @param data String The text to annotate.
     * @return Document Jsoup document
     */

    private static Document getDocument(String data) {
        Document doc = null;
        try {
            doc = Jsoup.connect(URL)
                    .data("query", data)
                    .data("limit", "10")
                    .data("indent","true")
                    .data("key",KEY)
                    .ignoreContentType(true)
                    .get();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return doc;
    }

    public static void main(String[] args) {
        String data = "Taylor Swift";
        ArrayList<Entity> entities = GoogleKGApi.query(data);
        System.out.println("Found: " + entities.size());
        System.out.println();

        for (Entity entity : entities) {
            System.out.println("Name: " + entity.getName());
            System.out.println("Types: " + entity.getTypes());
            System.out.println("Description: " + entity.getDescription());
            System.out.println("KB Description: ");
            System.out.println(entity.getDetailedDesc());
            System.out.println("Score: " + entity.getResultScore());
            System.out.println("==================================================");
        }
    }
}