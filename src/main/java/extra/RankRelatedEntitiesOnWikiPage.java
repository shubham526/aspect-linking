package extra;

import api.WATApi;
import help.Utilities;
import json.Aspect;
import json.JsonObject;
import json.ReadJsonlFile;
import lucene.Index;
import me.tongfei.progressbar.ProgressBar;
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
import java.util.concurrent.ForkJoinPool;

public class RankRelatedEntitiesOnWikiPage {
    private final IndexSearcher pageIndexSearcher;
    // ArrayList of run strings
    private final ArrayList<String> runFileStrings = new ArrayList<>();
    private Map<String, HashMap<String, Integer>> contextEntityMap = new ConcurrentHashMap<>();
    private String relType;
    boolean parallel;

    public RankRelatedEntitiesOnWikiPage(String pageIndexDir,
                       String mainDir,
                       String dataDir,
                       String outputDir,
                       String jsonFile,
                       String contextEntityFile,
                       String outFile,
                       boolean parallel,
                       @NotNull String relType,
                       Analyzer analyzer,
                       Similarity similarity) {


        String jsonFilePath = mainDir + "/" + dataDir + "/"  + jsonFile;
        String contextEntityFilePath = mainDir + "/" + dataDir + "/" + contextEntityFile;
        String outputFilePath = mainDir + "/" + outputDir + "/" + outFile;
        this.parallel = parallel;

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

        System.out.print("Reading context entity file...");
        try {
            contextEntityMap = Utilities.readMap(contextEntityFilePath);
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

        if (parallel) {
            System.out.println("Using Parallel Streams.");
            int parallelism = ForkJoinPool.commonPool().getParallelism();
            int numOfCores = Runtime.getRuntime().availableProcessors();
            System.out.println("Number of available processors = " + numOfCores);
            System.out.println("Number of threads generated = " + parallelism);

            if (parallelism == numOfCores - 1) {
                System.err.println("WARNING: USING ALL AVAILABLE PROCESSORS");
                System.err.println("USE: \"-Djava.util.concurrent.ForkJoinPool.common.parallelism=N\" " +
                        "to set the number of threads used");
            }
            // Do in parallel
            jsonObjectList.parallelStream().forEach(this::doTask);
        } else {
            System.out.println("Using Sequential Streams.");

            // Do in serial
            ProgressBar pb = new ProgressBar("Progress", jsonObjectList.size());
            for (JSONObject jsonObject : jsonObjectList) {
                doTask(jsonObject);
                pb.step();
            }
            pb.close();
        }

        System.out.print("Writing to run file...");
        Utilities.writeFile(runFileStrings, runFilePath);
        System.out.println("[Done].");

    }

    private void doTask(JSONObject jsonObject) {
        String entityID = JsonObject.getEntityId(jsonObject);
        String entityMention = JsonObject.getMention(jsonObject);
        String idContext = JsonObject.getIdContext(jsonObject);
        List<Aspect> candidateAspects = JsonObject.getAspectCandidates(jsonObject);

        Map<String, Double> pageEntityDistribution;

        // Get the list of all entities on the Wikipedia page of this entity.
        pageEntityDistribution = getPageEntityDistribution(entityID, idContext);

        makeRunFileStrings(candidateAspects, pageEntityDistribution);
        if (parallel) {
            System.out.println("Done: " + entityMention);
        }
    }

    /**
     * Helper method.
     * Returns the entities on the Wikipedia page of the given entity along with its relatedness measure.
     * @param entityID String Given entity.
     */

    @NotNull
    private Map<String, Double> getPageEntityDistribution(String entityID, String idContext) {
        Map<String, Double> pageEntityDistribution = new HashMap<>();
        String wikiEntityID = "enwiki:" + entityID;

        try {
            // Get the document corresponding to the entityID from the page.lucene index
            Document doc = Index.Search.searchIndex("Id", wikiEntityID, pageIndexSearcher);

            // Get the list of entities in the document
            String entityString = Objects.requireNonNull(doc).getField("OutlinkIds").stringValue();

            // Make a list from this string
            String[] entityArray = entityString.split("\n");

            getRelatednessDistribution(wikiEntityID, idContext, entityArray, pageEntityDistribution);



        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return Utilities.sortByValueDescending(pageEntityDistribution);
    }

    private void getRelatednessDistribution(String wikiEntityID, String idContext,
                                            @NotNull String[] entityArray, Map<String, Double> pageEntityDistribution) {

        for (String eid : entityArray) {
            double rel = getRelatedness(wikiEntityID, idContext, eid);
            pageEntityDistribution.put(process(eid), rel);
        }
    }

    /**
     * Helper method.
     * Returns the relatedness between between two entities.

     * @return Double Relatedness
     */

    private double getRelatedness(@NotNull String targetEntityId, String targetIdContext, String contextEntityId) {
        HashMap<String, Integer> targetEntityMap;

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

    private void makeRunFileStrings(@NotNull List<Aspect> candidateAspects,
                                    @NotNull Map<String, Double> scoreMap) {
        String runFileString;
        int rank = 1;
        String info = "rel";
        Map<String, Double> sortedScoreMap = Utilities.sortByValueDescending(scoreMap);

        for (Aspect aspect : candidateAspects) {
            String aspectId = aspect.getId();
            for (String idAspect : sortedScoreMap.keySet()) {
                runFileString = aspectId + " " + "0" + " " + idAspect + " " +
                        rank++ + " " + sortedScoreMap.get(idAspect) + " "+ info ;
                if (!runFileStrings.contains(runFileString)) {
                    runFileStrings.add(runFileString);
                }
            }

        }

    }
    public String process(String entityID) {
        entityID = entityID.substring(entityID.indexOf(":")+1);
        entityID = entityID.replaceAll("%20", "_");
        return entityID;
    }

    public static void main(@NotNull String[] args) {
        Similarity similarity = null;
        Analyzer analyzer = null;

        String pageIndexDir = args[0];
        String mainDir = args[1];
        String dataDir = args[2];
        String outputDir = args[3];
        String jsonFile = args[4];
        String contextEntityFile = args[5];
        String p = args[6];
        String a = args[7];
        String sim = args[8];

        String relType = "", runFile = "";
        boolean parallel = false;


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
                    lambda = Float.parseFloat(args[9]);
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
        relType = sc.nextLine();

        if (p.equalsIgnoreCase("true")) {
            parallel = true;
        }

        runFile = "wiki-page.run";
        new RankRelatedEntitiesOnWikiPage(pageIndexDir, mainDir, dataDir, outputDir, jsonFile, contextEntityFile,
                runFile, parallel, relType, analyzer, similarity);

    }
}


