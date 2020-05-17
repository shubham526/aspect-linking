package extra;

import api.WATApi;
import help.PseudoDocument;
import help.Utilities;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.*;

public class RelatednessAnalysis {
    private final IndexSearcher searcher;
    private final Analyzer analyzer;
    private Map<String, Map<String, Integer>> contextEntityMap = new HashMap<>();
    private String relType;


    /**
     * Constructor.
     * @param indexDir String Path to the Lucene index.
     * @param dataDir String Path to the data directory.
     * @param jsonFile String Name of the JSON-L file.
     * @param contextEntityFile String Name of the serialized file containing context entities.
     * @param analyzer Analyzer Type of Lucene analyzer.
     * @param similarity Similarity Type of similarity to use for search.
     */

    public RelatednessAnalysis(String indexDir,
                               String mainDir,
                               String dataDir,
                               String jsonFile,
                               String contextEntityFile,
                               @NotNull String relType,
                               Analyzer analyzer,
                               Similarity similarity) {

        String jsonFilePath = mainDir + "/" + dataDir + "/"  + jsonFile;
        String contextEntityFilePath = mainDir + "/" + dataDir + "/" + contextEntityFile;

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


        System.out.print("Setting up index for use...");
        searcher = new Index.Setup(indexDir, "text", analyzer, similarity).getSearcher();
        this.analyzer = analyzer;
        System.out.println("[Done].");

        System.out.print("Reading the JSON-L file...");
        List<JSONObject> jsonObjectList = ReadJsonlFile.read(jsonFilePath);
        System.out.println("[Done].");
        System.out.println("Found: " + jsonObjectList.size() + " JSON objects.");

        System.out.print("Reading the context entity file...");
        try {
            contextEntityMap = Utilities.readMap(contextEntityFilePath);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("[Done].");

        score(jsonObjectList);

    }



    /**
     * Method to score.
     * @param jsonObjectList List
     */

    private void score(@NotNull List<JSONObject> jsonObjectList) {
        for (int i = 0; i < 10; i++) {
            JSONObject jsonObject = jsonObjectList.get(i);
            doTask(jsonObject);

        }
    }

    private void doTask(JSONObject jsonObject) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));


        String mention = JsonObject.getMention(jsonObject);
        String IdContext = JsonObject.getIdContext(jsonObject);
        String entityID = JsonObject.getEntityId(jsonObject);

        // Get the Map of (entity, id) in the context
        Map<String, Integer> entityMap = contextEntityMap.get(entityID);
        List<String> contextEntityList = new ArrayList<>(entityMap.keySet());

        // For every entity in the context do
        for (int i = 0; i < 10; i++) {
            String entity = contextEntityList.get(i);
            System.out.println("Entity: " + entity);
            System.out.println("==============================================");

            PseudoDocument pseudoDocument = createPseudoDocument(entity);

            if (pseudoDocument != null) {

                // Get the probability distribution over the co-occurring entities
                Map<String, Double> distribution = Utilities.sortByValueDescending(
                        getDistribution(entityID, IdContext, pseudoDocument));
                for (String relEnt : distribution.keySet()) {
                    System.out.println("Entity: " + relEnt + " " + "Relatedness: " + distribution.get(relEnt));

                }
            }
            System.out.println("==============================================");
            System.out.println();
            try {
                br.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Done: " + mention);

    }
    @NotNull
    private Map<String, Double> getDistribution(String entityID, String idContext,
                                                @NotNull PseudoDocument pseudoDocument) {
        HashMap<String, Double> relMap = new HashMap<>();

        // Get the list of co-occurring entities
        Set<String> pseudoDocEntitySet = new HashSet<>(pseudoDocument.getEntityList());

        // For every co-occurring entity do
        for (String e : pseudoDocEntitySet) {

            // Find the frequency of this entity in the pseudo-document and store it
            relMap.put(e, getRelatedness(entityID, idContext, unprocess(e)));
        }

        return relMap;
    }
    private double getRelatedness(@NotNull String targetEntityId, String targetIdContext, String contextEntityId) {
        Map<String, Integer> targetEntityMap;

        if (contextEntityMap.containsKey(targetIdContext)) {
            targetEntityMap = contextEntityMap.get(targetIdContext);
        } else {
            targetEntityMap = new HashMap<>();
        }

        int id1, id2;
        String s1 = targetEntityId.substring(targetEntityId.indexOf(":") + 1).replaceAll("%20", "_");
        String s2 = contextEntityId.substring(contextEntityId.indexOf(":") + 1).replaceAll("%20", "_");

        if (targetEntityId.equalsIgnoreCase(contextEntityId)) {
            return 1.0d;
        }

        if (targetEntityMap.containsKey(s1)) {
            id1 = targetEntityMap.get(s1);
        } else {
            id1 = WATApi.TitleResolver.getId(s1);
        }

        if (targetEntityMap.containsKey(s2)) {
            id2 = targetEntityMap.get(s2);
        } else {
            id2 = WATApi.TitleResolver.getId(s2);
        }

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
    public static void main(@NotNull String[] args) {
        String indexDir = args[0];
        String mainDir = args[1];
        String dataDir = args[2];
        String jsonFile = args[3];
        String contextEntityFile = args[4];
        String a = args[5];
        String s = args[6];

        Analyzer analyzer = null;
        Similarity similarity = null;
        String relType = "";

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
                    lambda = Float.parseFloat(args[7]);
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

        new RelatednessAnalysis(indexDir, mainDir, dataDir, jsonFile, contextEntityFile,
                relType, analyzer, similarity);

    }
}
