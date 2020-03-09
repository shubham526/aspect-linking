package help;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;

/**
 * This class uses the WAT Entity Linking System to annotate text with entities.
 * @author Shubham Chatterjee
 * @version 3/8/2020
 */

public class WATApi {

    private final static String URL = "https://wat.d4science.org/wat/tag/tag ";
    private final static String TOKEN = "8775ecea-90d0-4fca-89d3-e19c0790489f-843339462";

    /**
     * Inner class t represent an annotation.
     */

    public static class Annotation {

        String wikiTitle;
        int wikiId, start, end;
        double rho;

        /**
         * Constructor.
         * @param wikiId Integer Wikipedia ID of the page the entity links to.
         * @param wikiTitle String Wikipedia page title of the page the entity links to.
         * @param start Integer Character offset (included)
         * @param end Integer Character offset (not included)
         * @param rho Double Annotation accuracy
         */

        private Annotation(int wikiId, String wikiTitle, int start, int end, double rho) {
            this.wikiId = wikiId;
            this.wikiTitle = wikiTitle;
            this.start = start;
            this.end = end;
            this.rho = rho;
        }

        //////////////////////// GETTERS FOR THE VARIOUS FIELDS///////////////////

        public int getWikiId() {
            return wikiId;
        }

        public String getWikiTitle() {
            return wikiTitle;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public double getRho() {
            return rho;
        }

        ///////////////////////////////////////////////////////////////////////////

    }

    /**
     * Inner class to represent the entity linking system.
     */


    public static class EntityLinker {

        /**
         * Method to return the annotations in the text.
         * @param data String The text to annotate.
         * @return List List of annotations.
         */

        @NotNull
        public static ArrayList<Annotation> getAnnotations(String data) {
            ArrayList<Annotation> annotations = new ArrayList<>();

            try {

                Document doc = getDocument(data);

                if (doc.text() != null) {
                    JSONObject json = new JSONObject(doc.text());
                    if (json.has("annotations")) {
                        JSONArray jsonArray = json.getJSONArray("annotations");
                        for (int i = 0; i < jsonArray.length(); i++) {

                            JSONObject jsonObject = jsonArray.getJSONObject(i);

                            String wikiTitle = jsonObject.getString("title");
                            int wikiId = jsonObject.getInt("id");
                            int start = jsonObject.getInt("start");
                            int end = jsonObject.getInt("end");
                            double rho = jsonObject.getDouble("rho");

                            annotations.add(new Annotation(wikiId, wikiTitle, start, end, rho));

                        }
                    } else {
                        System.err.println("ERROR: WAT could not find any annotations.");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return annotations;
        }

        @NotNull
        public static ArrayList<Annotation> getAnnotations(String data, double rho) {
            ArrayList<Annotation> allAnnotations = getAnnotations(data);

            if (rho == 0.0d) {
                return allAnnotations;
            }

            ArrayList<Annotation> annotations = new ArrayList<>();
            for (Annotation annotation : allAnnotations) {
                if (annotation.getRho() >= rho) {
                    annotations.add(annotation);
                }
            }
            return annotations;
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
                        .data("lang", "en")
                        .data("gcube-token", TOKEN)
                        .data("text", data)
                        .data("tokenizer", "nlp4j")
                        .data("debug", "9")
                        .data("method", "spotter:includeUserHint=true:includeNamedEntity=true:includeNounPhrase=true,prior:k=50,filter-valid,centroid:rescore=true,topk:k=5,voting:relatedness=lm,ranker:model=0046.model,confidence:model=pruner-wiki.linear")
                        .ignoreContentType(true)
                        .get();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return doc;
        }
    }

    /**
     * Main method to test the above.
     * @param args Command line arguments.
     */

    public static void main(@NotNull String[] args) {
        String data = args[0];
        double rho = Double.parseDouble(args[1]);
        ArrayList<Annotation> annotations = WATApi.EntityLinker.getAnnotations(data, rho);

        if (rho == 0) {
            System.out.println("Got all annotations.");
            System.out.println("Total: " + annotations.size());
        } else {
            System.out.println("Got only annotations for which rho >= " + rho);
            System.out.println("Total: " + annotations.size());
        }

        for (Annotation annotation : annotations) {
            System.out.println("===========================================");
            System.out.println("Wiki-Title: " + annotation.getWikiTitle());
            System.out.println("Wiki-Id   : " + annotation.getWikiId());
            System.out.println("Start     : " + annotation.getStart());
            System.out.println("End       : " + annotation.getEnd());
            System.out.println("Rho       : " + annotation.getRho());
            System.out.println("===========================================");
        }

    }


}
