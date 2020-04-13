package experiments;

import api.WATApi;
import help.*;
import json.Aspect;
import json.JsonObject;
import json.ReadJsonlFile;
import lucene.Index;
import lucene.RAMIndex;
import me.tongfei.progressbar.ProgressBar;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ===============================================Experiment-7=========================================
 * (1) Use the entityName of the entity mention to find its pseudo-document.
 * (2) Find the distribution of co-occurring entities in (1).
 *     -- Frequency
 *     -- Relatedness
 * (3) Treat Query = EntityName and use top-K entities from (2) to expand the query.
 * (4) Maintain an in-memory index of Aspects and retrieve from this index using the expanded query.
 * ====================================================================================================
 *
 * @author Shubham Chatterjee
 * @version 03/28/2020
 */

public class Experiment7 {
    private final IndexSearcher searcher;
    private final ArrayList<String> runFileStrings = new ArrayList<>();
    private Map<String, HashMap<String, Integer>> contextEntityMap = new ConcurrentHashMap<>();
    private final int takeKEntities; // Number of query expansion terms
    private final boolean omitQueryTerms; // Omit query terms or not when calculating expansion terms
    private final Analyzer analyzer; // Analyzer to use
    private String relType;

    public Experiment7(String indexDir,
                       String mainDir,
                       String dataDir,
                       String outputDir,
                       String jsonFile,
                       String contextEntityFile,
                       String runFile,
                       boolean useRelatedness,
                       @NotNull String relType,
                       int takeKEntities,
                       boolean omitQueryTerms,
                       Analyzer analyzer,
                       Similarity similarity) {

        String jsonFilePath = mainDir + "/" + dataDir + "/"  + jsonFile;
        String contextEntityFilePath = mainDir + "/" + dataDir + "/" + contextEntityFile;
        String runFilePath;
        Map<String, HashMap<String, Integer>> contextEntityMapCopy = new ConcurrentHashMap<>();

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

        this.takeKEntities = takeKEntities;
        this.analyzer = analyzer;
        this.omitQueryTerms = omitQueryTerms;

        if (useRelatedness) {
            System.out.println("Obtaining distribution of co-occurring entities using: Relatedness");
            System.out.print("Reading context entity file...");
            try {
                contextEntityMap = Utilities.readMap(contextEntityFilePath);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
            System.out.println("[Done].");
            contextEntityMapCopy = new ConcurrentHashMap<>(contextEntityMap);
            runFile = runFile.substring(0,runFile.indexOf("."));
            runFilePath = mainDir + "/" + outputDir + "/" + runFile + "-" + relType + ".run";
        } else {
            System.out.println("Obtaining distribution of co-occurring entities using: Frequency");
            runFilePath = mainDir + "/" + outputDir + "/" + runFile;
        }

        System.out.print("Setting up index for use...");
        searcher = new Index.Setup(indexDir, "text", analyzer, similarity).getSearcher();
        System.out.println("[Done].");

        System.out.print("Reading the JSON-L file...");
        List<JSONObject> jsonObjectList = ReadJsonlFile.read(jsonFilePath);
        System.out.println("[Done].");
        System.out.println("Found: " + jsonObjectList.size() + " JSON objects.");

        score(runFilePath, jsonObjectList, useRelatedness);

        if (useRelatedness) {
            if (!contextEntityMapCopy.equals(contextEntityMap)) {
                System.out.println("Context Entity Map changed during program run.");
                System.out.print("Saving new map to file....");
                try {
                    Utilities.writeMap(contextEntityMap, contextEntityFilePath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("[Done].");
            }
        }
    }
    /**
     * Method to score.
     * @param runFilePath String
     * @param jsonObjectList List
     */

    private void score(String runFilePath,
                       @NotNull List<JSONObject> jsonObjectList,
                       boolean useRelatedness) {


        // Do in parallel
        //jsonObjectList.parallelStream().forEach(jsonObject -> doTask(jsonObject, useRelatedness));

        // Do in serial
        ProgressBar pb = new ProgressBar("Progress", jsonObjectList.size());

        // Do in serial
        for (JSONObject jsonObject : jsonObjectList) {
            doTask(jsonObject, useRelatedness);
            pb.step();
        }
        pb.close();

        System.out.print("Writing to run file...");
        Utilities.writeFile(runFileStrings, runFilePath);
        System.out.println("[Done].");

    }

    private void doTask(JSONObject jsonObject, boolean useRelatedness) {

        String entityID = JsonObject.getEntityId(jsonObject);
        String entityName  = JsonObject.getEntityName(jsonObject);
        List<Aspect> candidateAspects = JsonObject.getAspectCandidates(jsonObject);

        Map<String, Double> aspectScores = new HashMap<>();
        // Create the pseudo-document for the entity
        // We use a candidate set retrieved using the entity name as the query to create the pseudo-document
        PseudoDocument pseudoDocument = createPseudoDocument(entityName);

        if (pseudoDocument != null) {

            // Get the probability distribution over the co-occurring entities
            Map<String, Double> distribution = getDistribution(entityID, pseudoDocument, useRelatedness);

            // Now score the candidate aspects
            aspectScores = scoreAspects(
                    jsonObject,
                    new ArrayList<>(Utilities.sortByValueDescending(distribution).entrySet()),
                    candidateAspects);
        } else {
            //System.err.println("ERROR: No PseudoDocument for entity: " + entityID);
            // If no pseudo-document found, each aspect gets a score of 0
            for (Aspect aspect : candidateAspects) {
                aspectScores.put(aspect.getId(), 0.0d);
            }
        }
        makeRunFileStrings(jsonObject, aspectScores, useRelatedness);
    }

    @NotNull
    private Map<String, Double> scoreAspects(JSONObject jsonObject,
                                             @NotNull List<Map.Entry<String, Double>> entityList,
                                             @NotNull List<Aspect> candidateAspects) {

        Map<String, Double> aspectScores = new HashMap<>();
        List<Map.Entry<String, Double>> expansionEntities;
        String entityID = JsonObject.getEntityId(jsonObject);
        int n = candidateAspects.size();

        // Use the top K entities for expansion
        expansionEntities = entityList.subList(0, Math.min(takeKEntities, entityList.size()));

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

        // Search the aspect index
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
                                    @NotNull Map<String, Double> scoreMap,
                                    boolean useRelatedness) {
        String runFileString;
        String idContext = JsonObject.getIdContext(jsonObject);
        int rank = 1;
        String info = "";
        Map<String, Double> sortedScoreMap = Utilities.sortByValueDescending(scoreMap);

        if (useRelatedness) {
            info = "7b-rel-dist-" + relType;
        } else {
            info = "7a-freq-dist";
        }
        for (String idAspect : sortedScoreMap.keySet()) {
            runFileString = idContext + " " + "0" + " " + idAspect + " " +
                    rank++ + " " + sortedScoreMap.get(idAspect) + " "+ info ;
            runFileStrings.add(runFileString);
        }
    }
    /**
     * Helper method.
     * Returns a distribution of contextual entities.
     * @param pseudoDocument PseudoDocument A PseudoDocument for an entity
     * @return Map A distribution of contextual entities.
     */

    @NotNull
    private Map<String, Double> getDistribution(String entityID,
                                                @NotNull PseudoDocument pseudoDocument,
                                                boolean useRelatedness) {

        if (useRelatedness) {
            return getRelatednessDistribution(entityID, pseudoDocument);
        }

        return getFrequencyDistribution(pseudoDocument);


    }

    @NotNull
    private Map<String, Double> getFrequencyDistribution(@NotNull PseudoDocument pseudoDocument) {
        HashMap<String, Integer> freqMap = new HashMap<>();

        // Get the list of co-occurring entities
        ArrayList<String> pseudoDocEntityList = pseudoDocument.getEntityList();

        // For every co-occurring entity do
        for (String e : pseudoDocEntityList) {

            // Find the frequency of this entity in the pseudo-document and store it
            freqMap.put(e, Utilities.frequency(e, pseudoDocEntityList));
        }

        // Convert this frequency map to a distribution

        return normalize(freqMap);
    }

    @NotNull
    private Map<String, Double> getRelatednessDistribution(String entityID, @NotNull PseudoDocument pseudoDocument) {
        HashMap<String, Double> relMap = new HashMap<>();

        // Get the list of co-occurring entities
        ArrayList<String> pseudoDocEntityList = pseudoDocument.getEntityList();

        // For every co-occurring entity do
        for (String e : pseudoDocEntityList) {

            // Find the frequency of this entity in the pseudo-document and store it
            relMap.put(e, getRelatedness(entityID, unprocess(e)));
        }

        return relMap;
    }

    @NotNull
    public static String unprocess(@NotNull String e) {
        String[] arr = e.split("_");
        StringBuilder sb = new StringBuilder();

        for (String s : arr) {
            sb.append(Character.toUpperCase(s.charAt(0)))
                    .append(s.substring(1))
                    .append(" ");
        }
        String s = sb.toString().trim();
        return s.replaceAll(" ", "%20");
    }

    /**
     * Helper method.
     * Returns the relatedness between between two entities.
     * @param targetEntityId String First Entity.
     * @param contextEntityId String Second Entity
     * @return Double Relatedness
     */

    private double getRelatedness(@NotNull String targetEntityId, String contextEntityId) {
        HashMap<String, Integer> targetEntityMap;

        if (contextEntityMap.containsKey(targetEntityId)) {
            targetEntityMap = contextEntityMap.get(targetEntityId);
        } else {
            targetEntityMap = new HashMap<>();
        }

        int id1, id2;
        String s1, s2;

        if (targetEntityId.equalsIgnoreCase(contextEntityId)) {
            return 1.0d;
        }

        if (targetEntityMap.containsKey(targetEntityId)) {
            id1 = targetEntityMap.get(targetEntityId);
        } else {
            s1 = targetEntityId.substring(targetEntityId.indexOf(":") + 1).replaceAll("%20", "_");
            id1 = WATApi.TitleResolver.getId(s1);
            targetEntityMap.put(targetEntityId, id1);
        }

        if (targetEntityMap.containsKey(contextEntityId)) {
            id2 = targetEntityMap.get(contextEntityId);
        } else {
            s2 = contextEntityId.substring(contextEntityId.indexOf(":") + 1).replaceAll("%20", "_");
            id2 = WATApi.TitleResolver.getId(s2);
            targetEntityMap.put(contextEntityId, id2);
        }
        contextEntityMap.put(targetEntityId, targetEntityMap);

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
     * Normalize a map.
     * @param rankings Map
     * @return Map
     */
    @NotNull
    private LinkedHashMap<String, Double> normalize(@NotNull Map<String, Integer> rankings) {
        LinkedHashMap<String, Double> normRankings = new LinkedHashMap<>();
        double sum = 0.0d;
        for (double score : rankings.values()) {
            sum += score;
        }

        for (String s : rankings.keySet()) {
            double normScore = rankings.get(s) / sum;
            normRankings.put(s,normScore);
        }
        return normRankings;
    }

    /**
     * Create a pseudo-document for the entity using passages from Wikipedia.
     * @param entity String
     * @return PseudoDocument
     */

    private PseudoDocument createPseudoDocument(String entity) {

        // Get the top-N passages for the the entity.
        // Here we treat the Query = Entity name
        ArrayList<String> searchResults = getTopDocs(entity,100); // N = 100

        // Now create the pseudo-document for the entity
        return Utilities.createPseudoDocument(entity, searchResults, searcher);

    }

    /**
     * Searches a Lucene index for a query and returns top-K results.
     * @param entity String
     * @param topKDocs Integer
     * @return List
     */

    @NotNull
    private ArrayList<String> getTopDocs(@NotNull String entity, int topKDocs) {
        ArrayList<String> searchResults = new ArrayList<>();
        String query = entity.replaceAll("_", " ").toLowerCase();
        BooleanQuery booleanQuery;
        TopDocs topDocs;
        try {
            booleanQuery = toQuery(query, "text", analyzer);
            topDocs = Index.Search.searchIndex(booleanQuery, topKDocs, searcher);
            ScoreDoc[] retDocs = topDocs.scoreDocs;
            for (ScoreDoc retDoc : retDocs) {
                searchResults.add(searcher.doc(retDoc.doc).get("id"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return searchResults;
    }

    /**
     * Converts a string query to a BooleanQuery.
     * @param queryStr String
     * @param searchField String
     * @param analyzer Analyzer
     * @return BooleanQuery
     * @throws IOException
     */

    private BooleanQuery toQuery(String queryStr, String searchField, Analyzer analyzer) throws IOException {
        List<String> tokens = new ArrayList<>();

        tokenizeQuery(queryStr, searchField, tokens, analyzer);
        BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();

        for (String token : tokens) {
            booleanQuery.add(new TermQuery(new Term(searchField, token)), BooleanClause.Occur.SHOULD);
        }
        return booleanQuery.build();
    }

    /**
     * Tokenize a query.
     * @param queryStr String
     * @param searchField String
     * @param tokens List
     * @param analyzer Analyzer
     * @throws IOException
     */

    private void tokenizeQuery(String queryStr, String searchField, @NotNull List<String> tokens, @NotNull Analyzer analyzer) throws IOException {
        TokenStream tokenStream = analyzer.tokenStream(searchField, new StringReader(queryStr));
        tokenStream.reset();
        tokens.clear();
        while (tokenStream.incrementToken() && tokens.size() < 64)
        {
            final String token = tokenStream.getAttribute(CharTermAttribute.class).toString();
            tokens.add(token);
        }
        tokenStream.end();
        tokenStream.close();
    }

    public static void main(@NotNull String[] args) {
        Similarity similarity = null;
        Analyzer analyzer = null;
        boolean omit;
        String s1 = null, s2, s3 = "freq";
        boolean useRelatedness = false;
        String relType = "";

        String indexDir = args[0];
        String mainDir = args[1];
        String dataDir = args[2];
        String outputDir = args[3];
        String jsonFile = args[4];
        String contextEntityFile = args[5];
        String rel = args[6];
        int takeKEntities = Integer.parseInt(args[7]);
        String o = args[8];
        omit = o.equalsIgnoreCase("y") || o.equalsIgnoreCase("yes");
        String a = args[9];
        String sim = args[10];

        System.out.printf("Using %d entities for query expansion\n", takeKEntities);

        if (omit) {
            System.out.println("Using RM1");
            s2 = "rm1";
        } else {
            System.out.println("Using RM3");
            s2 = "rm3";
        }


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
                s1 = "bm25";
                break;
            case "LMJM":
            case "lmjm":
                System.out.println("Similarity: LMJM");
                float lambda;
                try {
                    lambda = Float.parseFloat(args[11]);
                    System.out.println("Lambda = " + lambda);
                    similarity = new LMJelinekMercerSimilarity(lambda);
                    s1 = "lmjm";
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("Missing lambda value for similarity LM-JM");
                    System.exit(1);
                }
                break;
            case "LMDS":
            case "lmds":
                System.out.println("Similarity: LMDS");
                similarity = new LMDirichletSimilarity();
                s1 = "lmds";
                break;

            default:
                System.out.println("Wrong choice of similarity! Exiting.");
                System.exit(1);
        }
        if (rel.equalsIgnoreCase("true")) {
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
            relType = sc.nextLine();
            s3 = "rel";
            useRelatedness = true;
        }
        String outFile = "qee" + "-" + s1 + "-" + s2 + "-" + s3 + ".run";

        new Experiment7(indexDir, mainDir, dataDir, outputDir, jsonFile, contextEntityFile,
                outFile, useRelatedness, relType, takeKEntities, omit, analyzer, similarity);

    }

}
