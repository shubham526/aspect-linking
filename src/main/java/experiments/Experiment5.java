package experiments;

import help.Utilities;
import api.WATApi;
import json.Aspect;
import json.JsonObject;
import json.ReadJsonlFile;
import lucene.Index;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =======================================Experiment-5========================================
 * (1) Use the entities on the Wikipedia page of the entity mention.
 * (2) Find a distribution over these page entities using relatedness.
 * (3) Score(Aspect | Entity) = Sum of entity scores from (2) of entities in the aspect.
 * ===========================================================================================
 *
 * @author Shubham Chatterjee
 * @version 03/27/2020
 */

public class Experiment5 {

    private final IndexSearcher pageIndexSearcher;
    // ArrayList of run strings
    private final ArrayList<String> runFileStrings = new ArrayList<>();
    private Map<String, Integer> entityIDMap = new ConcurrentHashMap<>();
    private Map<String, HashMap<String, HashMap<String, Integer>>> aspectEntityMap = new HashMap<>();
    private String relType;

    public Experiment5(String pageIndexDir,
                       String mainDir,
                       String dataDir,
                       String outputDir,
                       String idFile,
                       String jsonFile,
                       String aspectEntityFile,
                       String outFile,
                       @NotNull String relType,
                       Analyzer analyzer,
                       Similarity similarity) {


        String idFilePath = mainDir + "/" + dataDir + "/" + idFile;
        String jsonFilePath = mainDir + "/" + dataDir + "/"  + jsonFile;
        String aspectEntityFilePath = mainDir + "/" + dataDir + "/" + aspectEntityFile;

        if (relType.equalsIgnoreCase("mw")) {
            System.out.println("Entity Similarity Measure: Milne-Witten");
            this.relType = "mw";
        } else if (relType.equalsIgnoreCase("jaccard")) {
            System.out.println("Entity Similarity Measure: Jaccard");
            this.relType = "jaccard";
        } else if (relType.equalsIgnoreCase("lm")) {
            System.out.println("Entity Similarity Measure: Language Models");
            this.relType = "lm";
        } else if (relType.equalsIgnoreCase("w2v")) {
            System.out.println("Entity Similarity Measure: Word2Vec");
            this.relType = "w2v";
        } else if (relType.equalsIgnoreCase("cp")) {
            System.out.println("Entity Similarity Measure: Conditional Probability");
            this.relType = "conditionalprobability";
        } else if (relType.equalsIgnoreCase("ba")) {
            System.out.println("Entity Similarity Measure: Barabasi-Albert on the Wikipedia Graph");
            this.relType = "barabasialbert";
        } else if (relType.equalsIgnoreCase("pmi")) {
            System.out.println("Entity Similarity Measure: Pointwise Mutual Information");
            this.relType = "pmi";
        }
        outFile = outFile.substring(0,outFile.indexOf("."));
        String outputFilePath = mainDir + "/" + outputDir + "/" + outFile + "-" + relType + ".run";


        System.out.print("Reading id file...");
        try {
            entityIDMap = Utilities.readMap(idFilePath);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("[Done].");

        System.out.print("Reading the JSON-L file...");
        List<JSONObject> jsonObjectList = ReadJsonlFile.read(jsonFilePath);
        System.out.println("[Done].");
        System.out.println("Found: " + jsonObjectList.size() + " JSON objects.");

        System.out.print("Reading the aspect entity file...");
        try {
            aspectEntityMap = Utilities.readMap(aspectEntityFilePath);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("[Done].");

        System.out.print("Setting up page index for use...");
        pageIndexSearcher = new Index.Setup(pageIndexDir, "OutlinkIds", analyzer, similarity).getSearcher();
        System.out.println("[Done].");

        score(outputFilePath, jsonObjectList);

    }

    private void score(String runFilePath, @NotNull List<JSONObject> jsonObjectList) {

        // Do in parallel
        jsonObjectList.parallelStream().forEach(this::doTask);

        // Do in serial
        //jsonObjectList.forEach(this::doTask);

        System.out.print("Writing to run file...");
        Utilities.writeFile(runFileStrings, runFilePath);
        System.out.println("[Done].");

    }

    private void doTask(JSONObject jsonObject) {
        String entityID = JsonObject.getEntityId(jsonObject);
        String entityMention = JsonObject.getMention(jsonObject);
        List<Aspect> candidateAspects = JsonObject.getAspectCandidates(jsonObject);

        Map<String, Double> aspectScores;
        Map<String, Double> pageEntityDistribution;

        // Get the list of all entities on the Wikipedia page of this entity.
        pageEntityDistribution = getPageEntityDistribution(entityID);

        // Score the aspects
        aspectScores = scoreAspects(entityID, pageEntityDistribution, candidateAspects);

        makeRunFileStrings(jsonObject, aspectScores);
        System.out.println("Done: " + entityMention);
    }

    /**
     * Helper method.
     * This method takes a distribution of contextual entities and the candidate aspects.
     * It then scores each aspect using this distribution.
     * @param distribution Map
     * @param candidateAspects Map
     * @return Map
     */

    @NotNull
    private Map<String, Double> scoreAspects(String entityId,
                                             Map<String, Double> distribution,
                                             @NotNull List<Aspect> candidateAspects) {

        Map<String, Double> aspectScores = new HashMap<>();


        // For every candidate aspect
        for (Aspect aspect : candidateAspects) {

            // Get all the (entity, id) in the aspect (provided with the data + WAT annotations)
            Map<String, Integer> aspectEntity = aspectEntityMap.get(entityId).get(aspect.getId());

            // Score the aspect
            double score = scoreAspect(distribution, new ArrayList<>(aspectEntity.keySet()));
            aspectScores.put(aspect.getId(), score);
        }

        return aspectScores;
    }

    /**
     * Helper method.
     * This method takes a distribution of contextual entities and a list of entities in a aspect.
     * It then calculates the score for the aspect.
     * @param distribution Map
     * @param aspectEntityList List
     * @return Double Score of the aspect
     */

    private double scoreAspect(Map<String, Double> distribution, @NotNull List<String> aspectEntityList) {
        double score = 0.0d;

        for (String aspectEntity : aspectEntityList) {
            if (distribution.containsKey(aspectEntity.toLowerCase())) {
                score += distribution.get(aspectEntity.toLowerCase());
            }
        }
        return score;
    }

    /**
     * Helper method.
     * Returns the entities on the Wikipedia page of the given entity along with its relatedness measure.
     * @param entityID String Given entity.
     */

    @NotNull
    private Map<String, Double> getPageEntityDistribution(String entityID) {
        Map<String, Double> pageEntityDistribution = new HashMap<>();
        String wikiEntityID = "enwiki:" + entityID;

        try {
            // Get the document corresponding to the entityID from the page.lucene index
            Document doc = Index.Search.searchIndex("Id", wikiEntityID, pageIndexSearcher);

            // Get the list of entities in the document
            String entityString = Objects.requireNonNull(doc).getField("OutlinkIds").stringValue();

            // Make a list from this string
            String[] entityArray = entityString.split("\n");
            for (String eid : entityArray) {
                double rel = getRelatedness(wikiEntityID, eid);
                pageEntityDistribution.put(Utilities.process(eid), rel);
            }

        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return Utilities.sortByValueDescending(pageEntityDistribution);
    }

    /**
     * Helper method.
     * Returns the relatedness between between two entities.
     * @param e1 String First Entity.
     * @param e2 String Second Entity
     * @return Double Relatedness
     */

    private double getRelatedness(@NotNull String e1, @NotNull String e2) {

        int id1, id2;

        if (e1.equalsIgnoreCase(e2.substring(e2.indexOf(":") + 1))) {
            return 1.0d;
        }


        id1 = entityIDMap.containsKey(e1)
                ? entityIDMap.get(e1)
                : WATApi.TitleResolver.getId(e1.substring(e1.indexOf(":") + 1).replaceAll("%20", "_"));

        id2 = entityIDMap.containsKey(e2)
                ? entityIDMap.get(e2)
                : WATApi.TitleResolver.getId(e2.substring(e2.indexOf(":") + 1).replaceAll("%20", "_"));


        if (id1 < 0 || id2 < 0) {
            return 0.0d;
        }

        List<WATApi.EntityRelatedness.Pair> pair = WATApi.EntityRelatedness.getRelatedness(relType,id1, id2);
        if (!pair.isEmpty()) {
            return pair.get(0).getRelatedness();
        } else {
            return 0.0d;
        }
    }

    private void makeRunFileStrings(JSONObject jsonObject,
                                    @NotNull Map<String, Double> scoreMap) {
        String runFileString;
        String idContext = JsonObject.getIdContext(jsonObject);
        int rank = 1;
        String info = "5-page-entity-" + relType;
        Map<String, Double> sortedScoreMap = Utilities.sortByValueDescending(scoreMap);

        for (String idAspect : sortedScoreMap.keySet()) {
            runFileString = idContext + " " + "0" + " " + idAspect + " " +
                    rank++ + " " + sortedScoreMap.get(idAspect) + " "+ info ;
            runFileStrings.add(runFileString);
        }
    }

    public static void main(@NotNull String[] args) {
        Similarity similarity = null;
        Analyzer analyzer = null;

        String pageIndexDir = args[0];
        String mainDir = args[1];
        String dataDir = args[2];
        String outputDir = args[3];
        String idFile = args[4];
        String jsonFile = args[5];
        String aspectEntityFile = args[6];
        String outFile = args[7];
        String a = args[8];
        String sim = args[9];


        switch (a) {
            case "std" :
                System.out.println("Analyzer: Standard");
                analyzer = new StandardAnalyzer();
                break;
            case "eng":
                System.out.println("Analyzer: English");
                analyzer = new EnglishAnalyzer();

                break;
            default:
                System.out.println("Wrong choice of analyzer! Exiting.");
                System.exit(1);
        }
        switch (sim) {
            case "BM25" :
            case "bm25":
                similarity = new BM25Similarity();
                System.out.println("Similarity: BM25");
                break;
            case "LMJM":
            case "lmjm":
                System.out.println("Similarity: LMJM");
                float lambda;
                try {
                    lambda = Float.parseFloat(args[10]);
                    System.out.println("Lambda = " + lambda);
                    similarity = new LMJelinekMercerSimilarity(lambda);
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("Missing lambda value for similarity LM-JM");
                    System.exit(1);
                }
                break;
            case "LMDS":
            case "lmds":
                System.out.println("Similarity: LMDS");
                similarity = new LMDirichletSimilarity();
                break;

            default:
                System.out.println("Wrong choice of similarity! Exiting.");
                System.exit(1);
        }

        Scanner sc = new Scanner(System.in);
        System.out.println("Enter the entity relation type to use. Your choices are:");
        System.out.println("mw (Milne-Witten)");
        System.out.println("jaccard (Jaccard measure of pages outlinks)");
        System.out.println("lm (language model)");
        System.out.println("w2v (Word2Vect)");
        System.out.println("cp (Conditional Probability)");
        System.out.println("ba (Barabasi-Albert on the Wikipedia Graph)");
        System.out.println("pmi (Pointwise Mutual Information)");
        System.out.println("Enter you choice:");
        String relType = sc.nextLine();

        new Experiment5(pageIndexDir, mainDir, dataDir, outputDir, idFile, jsonFile, aspectEntityFile,
                outFile, relType, analyzer, similarity);

    }
}
