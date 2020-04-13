package experiments;


import help.EntityRMExpand;
import help.Utilities;
import api.WATApi;
import json.Aspect;
import json.JsonObject;
import json.ReadJsonlFile;
import lucene.Index;
import lucene.RAMIndex;
import me.tongfei.progressbar.ProgressBar;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ==============================================Experiment-6========================================
 * (1) Use the entities on the Wikipedia page of the entity mention.
 * (2) Find a distribution over these page entities using relatedness. Rank these entities using relatedness.
 * (3) Treat Query = EntityName and expand he query using top-K related entities in (2).
 * (4) Maintain an in-memory index of aspects and retrieve aspects using expanded query in (3).
 * ===========================================================================================
 *
 * @author Shubham Chatterjee
 * @version 03/28/2020
 */

public class Experiment6 {
    private final IndexSearcher pageIndexSearcher;
    // ArrayList of run strings
    private final ArrayList<String> runFileStrings = new ArrayList<>();
    private Map<String, Integer> entityIDMap = new ConcurrentHashMap<>();
    private final int takeKEntities; // Number of query expansion terms
    private final boolean omitQueryTerms; // Omit query terms or not when calculating expansion terms
    private final Analyzer analyzer; // Analyzer to use
    private String relType;

    public Experiment6(String pageIndexDir,
                       String mainDir,
                       String dataDir,
                       String outputDir,
                       String idFile,
                       String jsonFile,
                       String outFile,
                       @NotNull String relType,
                       int takeKEntities,
                       boolean omitQueryTerms,
                       Analyzer analyzer,
                       Similarity similarity) {


        String idFilePath = mainDir + "/" + dataDir + "/" + idFile;
        String jsonFilePath = mainDir + "/" + dataDir + "/"  + jsonFile;

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

        this.takeKEntities = takeKEntities;
        this.analyzer = analyzer;
        this.omitQueryTerms = omitQueryTerms;

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

        System.out.print("Setting up page index for use...");
        pageIndexSearcher = new Index.Setup(pageIndexDir, "OutlinkIds", analyzer, similarity).getSearcher();
        System.out.println("[Done].");

        score(outputFilePath, jsonObjectList);


    }

    private void score(String runFilePath, @NotNull List<JSONObject> jsonObjectList) {

        // Do in parallel
        //jsonObjectList.parallelStream().forEach(this::doTask);

        // Do in serial
        ProgressBar pb = new ProgressBar("Progress", jsonObjectList.size());
        for (JSONObject jsonObject : jsonObjectList) {
            doTask(jsonObject);
            pb.step();
        }
        pb.close();

        System.out.print("Writing to run file...");
        Utilities.writeFile(runFileStrings, runFilePath);
        System.out.println("[Done].");
        System.out.println("Runfile written to: " + runFilePath);

    }

    private void doTask(JSONObject jsonObject) {
        String entityID = JsonObject.getEntityId(jsonObject);
        List<Aspect> candidateAspects = JsonObject.getAspectCandidates(jsonObject);

        Map<String, Double> aspectScores;
        List<Map.Entry<String, Double>> pageEntityList;

        // Get the list of all entities on the Wikipedia page of this entity.
        pageEntityList = getPageEntities(entityID);

        // Score the aspects
        aspectScores = scoreAspects(jsonObject, pageEntityList, candidateAspects);

        makeRunFileStrings(jsonObject, aspectScores);
    }

    @NotNull
    private Map<String, Double> scoreAspects(JSONObject jsonObject,
                                             @NotNull List<Map.Entry<String, Double>> pageEntityList,
                                             @NotNull List<Aspect> candidateAspects) {

        Map<String, Double> aspectScores = new HashMap<>();
        List<Map.Entry<String, Double>> expansionEntities;
        String entityID = JsonObject.getEntityId(jsonObject);
        int n = candidateAspects.size();

        // Use the top K entities for expansion
        expansionEntities = pageEntityList.subList(0, Math.min(takeKEntities, pageEntityList.size()));

        if (expansionEntities.size() == 0) {
            return aspectScores;
        }


        //////////////////////////////////////Build the index of aspects/////////////////////////////////

        // First create the IndexWriter
        IndexWriter iw = RAMIndex.createWriter(new EnglishAnalyzer());

        // Now create the index
        RAMIndex.createIndex(candidateAspects, iw);

        // Create the IndexSearcher
        IndexSearcher is = null;
        try {
            is = RAMIndex.createSearcher(new BM25Similarity(), iw);
        } catch (IOException e) {
            e.printStackTrace();
        }

        /////////////////////////////////////////////////////////////////////////////////////////////////

        ///////////////////////////// Search the index for the query/////////////////////////////////////

        // First process the query
        String queryStr = entityID
                .substring(entityID.indexOf(":") + 1)          // remove enwiki: from query
                .replaceAll("%20", " ")     // replace %20 with whitespace
                .toLowerCase();                            //  convert query to lowercase

        // Convert the query to an expanded BooleanQuery
        BooleanQuery booleanQuery = null;
        try {
            booleanQuery = EntityRMExpand.toEntityRmQuery(queryStr, expansionEntities, omitQueryTerms,
                    "text", analyzer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Now search the query
        assert is != null;
        aspectScores = Utilities.sortByValueDescending(RAMIndex.searchIndex(booleanQuery, n, is));

        // Close the aspect index
        try {
            RAMIndex.close(iw);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //////////////////////////////////////////////////////////////////////////////////////////////////

        return aspectScores;
    }

    private void makeRunFileStrings(JSONObject jsonObject,
                                    @NotNull Map<String, Double> scoreMap) {
        String runFileString;
        String idContext = JsonObject.getIdContext(jsonObject);
        int rank = 1;
        String info = "6-qe-rel-page-entity-" + relType;
        Map<String, Double> sortedScoreMap = Utilities.sortByValueDescending(scoreMap);

        for (String idAspect : sortedScoreMap.keySet()) {
            runFileString = idContext + " " + "0" + " " + idAspect + " " +
                    rank++ + " " + sortedScoreMap.get(idAspect) + " "+ info ;
            runFileStrings.add(runFileString);
        }
    }

    /**
     * Helper method.
     * Returns the entities on the Wikipedia page of the given entity along with its relatedness measure.
     * @param entityID String Given entity.
     */

    @NotNull
    private List<Map.Entry<String, Double>> getPageEntities(String entityID) {
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
                pageEntityDistribution.put(eid, rel);
            }

        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return new ArrayList<>(Utilities.sortByValueDescending(pageEntityDistribution).entrySet());
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

    /**
     * Main method to run the code.
     * @param args Command line parameters.
     */

    public static void main(@NotNull String[] args) {

        Similarity similarity = null;
        Analyzer analyzer = null;
        boolean omit;
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder outFile = new StringBuilder();
        String relType = "";

        outFile.append("qe-rel-ent-page")
                .append("-");

        String pageIndexDir = args[0];
        String mainDir = args[1];
        String dataDir = args[2];
        String outputDir = args[3];
        String idFile = args[4];
        String jsonFile = args[5];
        int takeKEntities = Integer.parseInt(args[6]);
        String o = args[7];
        omit = o.equalsIgnoreCase("y") || o.equalsIgnoreCase("yes");
        String a = args[8];
        String sim = args[9];

        System.out.printf("Using %d entities for query expansion\n", takeKEntities);


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
                outFile.append("bm25");
                break;
            case "LMJM":
            case "lmjm":
                System.out.println("Similarity: LMJM");
                float lambda;
                try {
                    lambda = Float.parseFloat(args[10]);
                    System.out.println("Lambda = " + lambda);
                    similarity = new LMJelinekMercerSimilarity(lambda);
                    outFile.append("lmjm");
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("Missing lambda value for similarity LM-JM");
                    System.exit(1);
                }
                break;
            case "LMDS":
            case "lmds":
                System.out.println("Similarity: LMDS");
                similarity = new LMDirichletSimilarity();
                outFile.append("lmds");
                break;

            default:
                System.out.println("Wrong choice of similarity! Exiting.");
                System.exit(1);
        }
        outFile.append("-");

        if (omit) {
            System.out.println("Using RM1");
            outFile.append("rm1");
        } else {
            System.out.println("Using RM3");
            outFile.append("rm3");
        }
        outFile.append(".run");
        System.out.println("Output File: " + outFile.toString());

        System.out.println("Enter the entity relation type to use. Your choices are:");
        System.out.println("mw (Milne-Witten)");
        System.out.println("jaccard (Jaccard measure of pages outlinks)");
        System.out.println("lm (language model)");
        System.out.println("w2v (Word2Vect)");
        System.out.println("cp (Conditional Probability)");
        System.out.println("ba (Barabasi-Albert on the Wikipedia Graph)");
        System.out.println("pmi (Pointwise Mutual Information)");
        System.out.println("Enter you choice:");
        try {
            relType = br.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }

        new Experiment6(pageIndexDir, mainDir, dataDir, outputDir, idFile, jsonFile,
                outFile.toString(), relType, takeKEntities, omit, analyzer, similarity);

    }
}
