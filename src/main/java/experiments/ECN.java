package experiments;

import help.PseudoDocument;
import help.Utilities;
import help.WATApi;
import json.Aspect;
import json.Context;
import json.JsonObject;
import json.ReadJsonlFile;
import lucene.Index;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
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

/**
 * =======================================Experiment-2=======================================
 * (1) Treat Query = content of sentence, paragraph or section.
 * (2) Use entities from the above context.
 * (3) Find support passages for entities in (2).
 * (4) Use aspect_candidates as candidates for support passage.
 * This experiment uses the Entity Context Neighbour method for support passage retrieval.
 * ==========================================================================================
 * @author Shubham Chatterjee
 * @version 03/12/2020
 */

public class ECN {

    private final IndexSearcher searcher;
    private HashMap<String, String> idMap = new HashMap<>();
    private final Analyzer analyzer;

    /**
     * Constructor.
     * @param indexDir String Path to the Lucene index.
     * @param dataDir String Path to the data directory.
     * @param outputDir String Path to the output directory.
     * @param jsonFile String Name of the JSON-L file.
     * @param runFile String Name of the run file.
     * @param idFile String Name of the id file.
     * @param analyzer Analyzer Type of Lucene analyzer.
     * @param similarity Similarity Type of similarity to use for search.
     */

    public ECN(String indexDir,
               String dataDir,
               String outputDir,
               String jsonFile,
               String runFile,
               String idFile,
               Analyzer analyzer,
               Similarity similarity) {

        String jsonFilePath = dataDir + "/"  + jsonFile;
        String idFilePath = dataDir + "/" + idFile;
        String runFilePath = outputDir + "/" + runFile;

        System.out.print("Setting up index for use...");
        searcher = new Index.Setup(indexDir, "text", analyzer, similarity).getSearcher();
        this.analyzer = analyzer;
        System.out.println("[Done].");

        System.out.print("Reading the ID file...");
        try {
            idMap = Utilities.readMap(idFilePath);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("[Done].");

        System.out.print("Reading the JSON-L file...");
        List<JSONObject> jsonObjectList = ReadJsonlFile.read(jsonFilePath);
        System.out.println("[Done].");


        score(runFilePath, jsonObjectList, idFilePath);
    }

    /**
     * Method to score.
     * @param runFilePath String
     * @param jsonObjectList List
     * @param idFilePath String
     */

    private void score(String runFilePath,
                       @NotNull List<JSONObject> jsonObjectList,
                       String idFilePath) {

        Map<String, Map<String, Double>> aspectScoresForEntity = new HashMap<>();



        // For every JSON object do

        for (JSONObject jsonObject : jsonObjectList) {

            System.out.println("Mention: " + JsonObject.getMention(jsonObject));

            // Get the sentence/paragraph/section context
            Context context = JsonObject.getSentenceContext(jsonObject);

            // Get the corresponding content
            String content = context.getContent();

            // Get the list of entities in the context
            List<String> entityList = getEntities(context);
            System.out.println("Found: " + entityList.size());

            // Get the candidate aspects
            List<Aspect> candidateAspects = JsonObject.getAspectCandidates(jsonObject);

            // For every entity in the context do
            for (String entity : entityList) {

                // If the entity is not already present in the score map
                if (!aspectScoresForEntity.containsKey(entity)) {

                    // Score the candidate aspects for the entity
                    Map<String, Double> aspectScores = scoreAspects(entity, candidateAspects);

                    // Store the aspect scores for the entity
                    aspectScoresForEntity.put(entity,aspectScores);
                    System.out.println("Done: " + entity);
                }
            }
            System.out.println("==============================================================================");
        }
    }

    /**
     * Helper method.
     * This method takes an entity and the candidate aspects and returns a ranked result of aspects.
     * @param entity String
     * @param candidateAspects List
     * @return Map
     */

    @NotNull
    private Map<String, Double> scoreAspects(String entity, List<Aspect> candidateAspects) {

        Map<String, Double> aspectScores = new HashMap<>();
        // Create the pseudo-document for the entity
        // We use a candidate set retrieved using the entity name as the query to create the pseudo-document
        PseudoDocument pseudoDocument = createPseudoDocument(entity);

        if (pseudoDocument != null) {

            // Get the probability distribution over the co-occurring entities
            Map<String, Double> distribution = getDistribution(pseudoDocument);

            // Now score the candidate aspects
            aspectScores = scoreAspects(distribution, candidateAspects);

        }
        return Utilities.sortByValueDescending(aspectScores);
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
    private Map<String, Double> scoreAspects(Map<String, Double> distribution, @NotNull List<Aspect> candidateAspects) {
        Map<String, Double> aspectScores = new HashMap<>();


        // For every candidate aspect
        for (Aspect aspect : candidateAspects) {

            // Get all the entities in the aspect (provided with the data + WAT annotations)
            List<String> aspectEntityList = getAspectEntities(aspect);

            // Score the aspect
            double score = scoreAspect(distribution, aspectEntityList);
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
            if (distribution.containsKey(aspectEntity)) {
                score += distribution.get(aspectEntity);
            }
        }
        return score;
    }

    /**
     * Helper method.
     * Returns the list of entities in the aspect.
     * Uses both entities provided in the data as well as those returned by WAT.
     * @param aspect Aspect
     * @return List
     */

    @NotNull
    private List<String> getAspectEntities(@NotNull Aspect aspect) {
        List<String> aspectEntityList = new ArrayList<>();

        // Get the list of entities provided with the data
        List<String> dataEntityList = lowercase(aspect.getEntityList());

        // Get the annotations from WAT API
        List<WATApi.Annotation> watEntityList = WATApi.EntityLinker.getAnnotations(aspect.getContent());

        for (WATApi.Annotation annotation : watEntityList) {
            dataEntityList.add(annotation.getWikiTitle().toLowerCase());
        }
        return dataEntityList;
    }

    /**
     * Helper method.
     * Returns a distribution of contextual entities.
     * @param pseudoDocument PseudoDocument A PseudoDocument for an entity
     * @return Map A distribution of contextual entities.
     */

    @NotNull
    private Map<String, Double> getDistribution(@NotNull PseudoDocument pseudoDocument) {
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

    /**
     * Returns all entities in the context.
     * @param context Context
     * @return List
     */

    @NotNull
    private List<String> getEntities(@NotNull Context context) {

        // Use entities provided with the data

        List<String> entityList = lowercase(context.getEntityList());
        String content = context.getContent();

        // But also use entities annotated with WAT API.
        List<WATApi.Annotation> annotationList = WATApi.EntityLinker.getAnnotations(content,0.1);
        for (WATApi.Annotation annotation : annotationList) {
            entityList.add(annotation.getWikiTitle().toLowerCase());
        }

        return entityList;
    }

    /**
     * Returns a lowercase version of the passed-in list of strings.
     * @param list List
     * @return List
     */

    @NotNull
    private List<String> lowercase(@NotNull List<String> list) {
        List<String> newList = new ArrayList<>();
        for (String s : list) {
            s = s.toLowerCase();
            newList.add(s);
        }
        return newList;
    }

    /**
     * Main method.
     * @param args Command line arguments.
     */

    public static void main(@NotNull String[] args) {
        String indexDir = args[0];
        String dataDir = args[1];
        String outputDir = args[2];
        String jsonFile = args[3];
        String runFile = args[4];
        String idFile = args[5];
        String a = args[6];
        String s = args[7];

        Analyzer analyzer = null;
        Similarity similarity = null;

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
                System.out.println("Wrong choice of analyzer! Program ends.");
                System.exit(1);
        }

        switch (s) {

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
                    lambda = Float.parseFloat(args[11]);
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
                System.out.println("Wrong choice of similarity! Program end.");
                System.exit(1);
        }
        new ECN(indexDir, dataDir, outputDir, jsonFile, runFile, idFile, analyzer, similarity);


    }
}
