import experiments.*;
import random.GetEntities;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Scanner;

/**
 * Project runner.
 * @author Shubham Chatterjee
 * @version 03/28/2020
 */

public class ProjectMain {
    public static void main(String[] args) {

        if (args.length == 0) {
            help();
            System.exit(-1);
        }
        String command = args[0];
        if (command.equalsIgnoreCase("-h") || command.equalsIgnoreCase("--help")) {
            help();
            System.exit(-1);
        }

        if (command.equalsIgnoreCase("-o") || command.equalsIgnoreCase("--options")) {
            options();
            System.exit(-1);
        }

        if (command.equalsIgnoreCase("--exp1")) {
            if (args[1].equalsIgnoreCase("--desc")) {
                String desc =
                        "==============================Experiment-1===================================================\n" +
                        " 1) (a) Use \"content\" from either sent_context or para_context or sect_context.\n" +
                        "    (b) Pass content to SWAT. Get map of (entity,sal_score).\n" +
                        " 2) Aspect candidates are the candidates we are trying to score.\n" +
                        "    -- For Every aspect content, score the content by summing over the\n" +
                        "       salience score of entities contained in it.\n" +
                        "    -- The salience scores are obtained in Step-1 above.\n" +
                        "    -- If there are no overlapping entities, then the score of the passage is 0.\n" +
                        "==============================================================================================";
                System.out.println(desc);
                System.exit(-1);
            } else if (args[1].equalsIgnoreCase("--use")) {

                String use =
                        "String mainDir: Path to the top-level aspect-linking directory.\n" +
                        "String dataDir: Path to the data directory within the mainDir.\n" +
                        "String outputDir: Path to the output directory within the mainDir.\n" +
                        "String jsonFile: Name of the JSON-L data file (must be within dataDir).\n" +
                        "String aspectEntityFile: Name of the file containing aspect entities in serialized form (must be within dataDir).\n" +
                        "String runFile: Name of the run file (will be stored in the outputDir).\n" +
                        "String contextType: Type of context to use (sent|para|sec).";
                System.out.println(use);
                System.exit(-1);
            }
            System.out.println("Performing Experiment-1");
            exp1(args);
        } else if (command.equalsIgnoreCase("--exp2")) {
            if (args[1].equalsIgnoreCase("--desc")) {
                String desc =
                        "=======================================Experiment-2=======================================\n" +
                        "  (1) Use entities from sentence, paragraph or section context.\n" +
                        "  (2) Find support passages for entities in (1).\n" +
                        "  (3) Score(Aspect | Entity) = Sum of entity scores for every entity in the aspect.\n" +
                        "  Use aspect_candidates as candidates for support passage.\n" +
                        "  This experiment uses the frequency of co-occurring entities to find distribution over these entities.\n" +
                                " This experiment has two variations:\n" +
                                "  (a) Use entity relatedness score to weigh the contribution of each entity to the final aspect score.\n" +
                                "  (b) Do not use entity relatedness score but just the raw entity score for the aspect." +

                        "==========================================================================================";
                System.out.println(desc);
                System.exit(-1);
            } else if (args[1].equalsIgnoreCase("--use")) {

                String use =
                        "String indexDir: Path to the paragraph index directory.\n" +
                        "String mainDir: Path to the top-level aspect-linking directory.\n" +
                        "String dataDir: Path to the data directory within the mainDir.\n" +
                        "String outputDir: Path to the output directory within the mainDir.\n" +
                        "String jsonFile: Name of the JSON-L data file (must be within dataDir).\n" +
                        "String contextEntityFile: Name of the serialized file containing the context(sentence/paragraph/section) entities (must be within dataDir).\n" +
                        "String aspectEntityFile: Name of the file containing aspect entities in serialized form (must be within dataDir).\n" +
                        "String runFile: Name of the run file (will be stored in the outputDir).\n" +
                        "boolean useRelatedness: Whether to use relatedness (true) or not (false) to find distribution of entities.\n" +
                        "Analyzer analyzer: Type of Lucene analyzer to use (English[eng] or Standard[std]).\n" +
                        "Similarity similarity: Type of Lucene similarity to use (BM25 or Language Models with Dirichlet Smoothing(LMDS) or Language Models with Jelinek-Mercer Smoothing(LMJM).\n" +
                        "double rho: To be provided when similarity value chosen is LMJM.\n";
                System.out.println(use);
                System.exit(-1);
            }
            System.out.println("Performing Experiment-2");
            exp2(args);
        } else if (command.equalsIgnoreCase("--exp3")) {
            if (args[1].equalsIgnoreCase("--desc")) {
                String desc =
                        "===========================================Experiment-3========================================\n" +
                        "  (1) Use entities from sentence, paragraph or section context.\n" +
                        "  (2) Find support passages for entities in (1).\n" +
                        "  (3) Score(Aspect | Entity) = Sum of entity scores for every entity in the aspect.\n" +
                        "  Use aspect_candidates as candidates for support passage.\n" +
                        "  This experiment uses the relatedness of co-occurring entities.\n" +
                        "===============================================================================================";
                System.out.println(desc);
                System.exit(-1);
            } else if (args[1].equalsIgnoreCase("--use")) {

                String use =
                                "String indexDir: Path to the paragraph index directory.\n" +
                                "String mainDir: Path to the top-level aspect-linking directory.\n" +
                                "String dataDir: Path to the data directory within the mainDir.\n" +
                                "String outputDir: Path to the output directory within the mainDir.\n" +
                                "String jsonFile: Name of the JSON-L data file (must be within dataDir).\n" +
                                "String contextEntityFile: Name of the serialized file containing the context(sentence/paragraph/section) entities (must be within dataDir).\n" +
                                "String aspectEntityFile: Name of the file containing aspect entities in serialized form (must be within dataDir).\n" +
                                "String runFile: Name of the run file (will be stored in the outputDir).\n" +
                                "Analyzer analyzer: Type of Lucene analyzer to use (English[eng] or Standard[std]).\n" +
                                "Similarity similarity: Type of Lucene similarity to use (BM25 or Language Models with Dirichlet Smoothing(LMDS) or Language Models with Jelinek-Mercer Smoothing(LMJM).\n" +
                                "double rho: To be provided when similarity value chosen is LMJM.\n";
                System.out.println(use);
                System.exit(-1);
            }
            System.out.println("Performing Experiment-3");
            exp3(args);
        } else if (command.equalsIgnoreCase("--exp4")) {
            if (args[1].equalsIgnoreCase("--desc")) {
                String desc = "==========================================Experiment-4=========================================\n" +
                        "  (1) Use the entityName of the entity mention to find its pseudo-document.\n" +
                        "  (2) Find the distribution of co-occurring entities in (1).\n" +
                        "      -- Frequency\n" +
                        "      -- Relatedness\n" +
                        "  (3) Rank  the candidate aspects by summing the score of the entities from (2) in the aspect.\n" +
                        "===============================================================================================";
                System.out.println(desc);
                System.exit(-1);
            } else if (args[1].equalsIgnoreCase("--use")) {

                String use =
                        "String indexDir: Path to the paragraph index directory.\n" +
                        "String mainDir: Path to the top-level aspect-linking directory.\n" +
                        "String dataDir: Path to the data directory within the mainDir.\n" +
                        "String outputDir: Path to the output directory within the mainDir.\n" +
                        "String jsonFile: Name of the JSON-L data file (must be within dataDir).\n" +
                        "String contextEntityFile: Name of the serialized file containing the context(sentence/paragraph/section) entities (must be within dataDir).\n" +
                        "String aspectEntityFile: Name of the file containing aspect entities in serialized form (must be within dataDir).\n" +
                        "String runFile: Name of the run file (will be stored in the outputDir).\n" +
                        "boolean useRelatedness: Whether to use relatedness (true) or not (false) to find distribution of entities.\n" +
                        "Analyzer analyzer: Type of Lucene analyzer to use (English[eng] or Standard[std]).\n" +
                        "Similarity similarity: Type of Lucene similarity to use (BM25 or Language Models with Dirichlet Smoothing(LMDS) or Language Models with Jelinek-Mercer Smoothing(LMJM).\n" +
                        "double rho: To be provided when similarity value chosen is LMJM.\n";
                System.out.println(use);
                System.exit(-1);
            }
            System.out.println("Performing Experiment-4");
            exp4(args);
        } else if (command.equalsIgnoreCase("--exp5")) {
            if (args[1].equalsIgnoreCase("--desc")) {
                String desc = "=======================================Experiment-5========================================\n" +
                        "  (1) Use the entities on the Wikipedia page of the entity mention.\n" +
                        "  (2) Find a distribution over these page entities using relatedness.\n" +
                        "  (3) Score(Aspect | Entity) = Sum of entity scores from (2) of entities in the aspect.\n" +
                        "===========================================================================================";
                System.out.println(desc);
                System.exit(-1);
            } else if (args[1].equalsIgnoreCase("--use")) {

                String use =
                        "String pageIndexDir: Path to the page index directory.\n" +
                                "String mainDir: Path to the top-level aspect-linking directory.\n" +
                                "String dataDir: Path to the data directory within the mainDir.\n" +
                                "String outputDir: Path to the output directory within the mainDir.\n" +
                                "String idFile: Name of the serialized file containing the entityIDs (must be within dataDir).\n" +
                                "String jsonFile: Name of the JSON-L data file (must be within dataDir).\n" +
                                "String aspectEntityFile: Name of the file containing aspect entities in serialized form (must be within dataDir).\n" +
                                "String runFile: Name of the run file (will be stored in the outputDir).\n" +
                                "String accuracyFile: Name of the file where the accuracy values will be saved.\n" +
                                "Analyzer analyzer: Type of Lucene analyzer to use (English[eng] or Standard[std]).\n" +
                                "Similarity similarity: Type of Lucene similarity to use (BM25 or Language Models with Dirichlet Smoothing(LMDS) or Language Models with Jelinek-Mercer Smoothing(LMJM).\n" +
                                "double rho: To be provided when similarity value chosen is LMJM.\n";
                System.out.println(use);
                System.exit(-1);
            }
            System.out.println("Performing Experiment-5");
            exp5(args);
        } else if (command.equalsIgnoreCase("--exp6")) {
            if (args[1].equalsIgnoreCase("--desc")) {
                String desc = "==============================================Experiment-6========================================\n" +
                        "  (1) Use the entities on the Wikipedia page of the entity mention.\n" +
                        "  (2) Find a distribution over these page entities using relatedness. Rank these entities using relatedness.\n" +
                        "  (3) Treat Query = EntityName and expand he query using top-K related entities in (2).\n" +
                        "  (4) Maintain an in-memory index of aspects and retrieve aspects using expanded query in (3).\n" +
                        "===========================================================================================";
                System.out.println(desc);
                System.exit(-1);
            } else if (args[1].equalsIgnoreCase("--use")) {
                String use =
                        "String pageIndexDir: Path to the page index directory.\n" +
                                "String mainDir: Path to the top-level aspect-linking directory.\n" +
                                "String dataDir: Path to the data directory within the mainDir.\n" +
                                "String outputDir: Path to the output directory within the mainDir.\n" +
                                "String idFile: Name of the serialized file containing the entityIDs (must be within dataDir).\n" +
                                "String jsonFile: Name of the JSON-L data file (must be within dataDir).\n" +
                                "int takeKEntities: Number of entities to use for query expansion.\n" +
                                "boolean omitQueryTerms: Whether or not (yes/no) to omit the original query terms during expansion.\n" +
                                "Analyzer analyzer: Type of Lucene analyzer to use (English[eng] or Standard[std]).\n" +
                                "Similarity similarity: Type of Lucene similarity to use (BM25 or Language Models with Dirichlet Smoothing(LMDS) or Language Models with Jelinek-Mercer Smoothing(LMJM).\n" +
                                "double rho: To be provided when similarity value chosen is LMJM.\n";
                System.out.println(use);
                System.exit(-1);
            }
            System.out.println("Performing Experiment-6");
            exp6(args);
        } else if (command.equalsIgnoreCase("--exp7")) {
            if (args[1].equalsIgnoreCase("--desc")) {
                String desc =
                        "===============================================Experiment-7=========================================\n" +
                        "  (1) Use the entityName of the entity mention to find its pseudo-document.\n" +
                        "  (2) Find the distribution of co-occurring entities in (1).\n" +
                        "      -- Frequency\n" +
                        "      -- Relatedness\n" +
                        "  (3) Treat Query = EntityName and use top-K entities from (2) to expand the query.\n" +
                        "  (4) Maintain an in-memory index of Aspects and retrieve from this index using the expanded query.\n" +
                        "====================================================================================================";
                System.out.println(desc);
                System.exit(-1);
            } else if (args[1].equalsIgnoreCase("--use")) {
                String use =
                        "String indexDir: Path to the paragraph index directory.\n" +
                                "String mainDir: Path to the top-level aspect-linking directory.\n" +
                                "String dataDir: Path to the data directory within the mainDir.\n" +
                                "String outputDir: Path to the output directory within the mainDir.\n" +
                                "String jsonFile: Name of the JSON-L data file (must be within dataDir).\n" +
                                "String contextEntityFile: Name of the serialized file containing the entityIDs of entities in the ECD.\n" +
                                "String rel: Whether to use relatedness(true) or frequency(false) of co-occurring entities to find the distribution over co-occurring entities.\n" +
                                "int takeKEntities: Number of entities to use for query expansion.\n" +
                                "boolean omitQueryTerms: Whether or not (yes/no) to omit the original query terms during expansion.\n" +
                                "Analyzer analyzer: Type of Lucene analyzer to use (English[eng] or Standard[std]).\n" +
                                "Similarity similarity: Type of Lucene similarity to use (BM25 or Language Models with Dirichlet Smoothing(LMDS) or Language Models with Jelinek-Mercer Smoothing(LMJM).\n" +
                                "double rho: To be provided when similarity value chosen is LMJM.\n";
                System.out.println(use);
                System.exit(-1);
            }
            System.out.println("Performing Experiment-7");
            exp7(args);
        } else if (command.equalsIgnoreCase("--get-ent")) {
            getEnt(args);

        } else {
            System.err.println("Wrong command! Try again!");
            System.exit(-1);
        }

    }

    private static void exp1(@NotNull String[] args) {
        String mainDir = args[1];
        String dataDir = args[2];
        String outputDir = args[3];
        String jsonFile = args[4];
        String aspectEntityFile = args[5];
        String contextType = args[6];
        String mode = args[7];
        String p = args[8];

        boolean parallel = false;
        if (p.equalsIgnoreCase("true")) {
            parallel = true;
        }
        String runFile = "salience-" +  contextType + "-context-" + mode + "-entities.run";

        new Experiment1(mainDir, dataDir, outputDir, jsonFile, aspectEntityFile, runFile, contextType, mode, parallel);
    }

    private static void exp2(@NotNull String[] args) {

        String indexDir = args[1];
        String mainDir = args[2];
        String dataDir = args[3];
        String outputDir = args[4];
        String jsonFile = args[5];
        String contextEntityFile = args[6];
        String aspectEntityFile = args[7];
        String runFile = args[8];
        String rel = args[9];
        String a = args[10];
        String s = args[11];

        Analyzer analyzer = null;
        Similarity similarity = null;
        boolean useRelatedness = false;
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
                    lambda = Float.parseFloat(args[12]);
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
            useRelatedness = true;
        }
        new Experiment2(indexDir, mainDir, dataDir, outputDir, jsonFile, contextEntityFile, aspectEntityFile,
                runFile, useRelatedness, relType, analyzer, similarity);

    }

    private static void exp3(@NotNull String[] args) {
        String indexDir = args[1];
        String mainDir = args[2];
        String dataDir = args[3];
        String outputDir = args[4];
        String jsonFile = args[5];
        String contextEntityFile = args[6];
        String aspectEntityFile = args[7];
        String runFile = args[8];
        String a = args[9];
        String s = args[10];

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


        new Experiment3(indexDir, mainDir, dataDir, outputDir, jsonFile, contextEntityFile, aspectEntityFile,
                runFile, relType, analyzer, similarity);

    }

    private static void exp4(@NotNull String[] args) {
        String indexDir = args[1];
        String mainDir = args[2];
        String dataDir = args[3];
        String outputDir = args[4];
        String jsonFile = args[5];
        String contextEntityFile = args[6];
        String aspectEntityFile = args[7];
        String runFile = args[8];
        String rel = args[9];
        String a = args[10];
        String s = args[11];

        Analyzer analyzer = null;
        Similarity similarity = null;
        boolean useRelatedness = false;
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
                    lambda = Float.parseFloat(args[12]);
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
            useRelatedness = true;
        }
        new Experiment4(indexDir, mainDir, dataDir, outputDir, jsonFile, contextEntityFile, aspectEntityFile,
                runFile, useRelatedness, relType, analyzer, similarity);

    }

    private static void exp5(@NotNull String[] args) {
        Similarity similarity = null;
        Analyzer analyzer = null;

        String pageIndexDir = args[1];
        String mainDir = args[2];
        String outputDir = args[3];
        String dataDir = args[4];
        String idFile = args[5];
        String jsonFile = args[6];
        String aspectEntityFile = args[7];
        String outFile = args[8];
        String a = args[9];
        String sim = args[10];


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

        new Experiment5(pageIndexDir, mainDir, outputDir, dataDir, idFile, jsonFile, aspectEntityFile,
                outFile, relType, analyzer, similarity);
    }
    private static void exp6(@NotNull String[] args) {
        Similarity similarity = null;
        Analyzer analyzer = null;
        boolean omit;
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder outFile = new StringBuilder();
        String relType = "";

        outFile.append("qe-rel-ent-page")
                .append("-");

        String pageIndexDir = args[1];
        String mainDir = args[2];
        String dataDir = args[3];
        String outputDir = args[4];
        String idFile = args[5];
        String jsonFile = args[6];
        int takeKEntities = Integer.parseInt(args[7]);
        String o = args[8];
        omit = o.equalsIgnoreCase("y") || o.equalsIgnoreCase("yes");
        String a = args[9];
        String sim = args[10];

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
                    lambda = Float.parseFloat(args[11]);
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
    private static void exp7(@NotNull String[] args) {
        Similarity similarity = null;
        Analyzer analyzer = null;
        boolean omit;
        String s1 = null, s2, s3 = "freq";
        boolean useRelatedness = false;
        String relType = "";

        String indexDir = args[1];
        String mainDir = args[2];
        String dataDir = args[3];
        String outputDir = args[4];
        String jsonFile = args[5];
        String contextEntityFile = args[6];
        String rel = args[7];
        int takeKEntities = Integer.parseInt(args[8]);
        String o = args[9];
        omit = o.equalsIgnoreCase("y") || o.equalsIgnoreCase("yes");
        String a = args[10];
        String sim = args[11];

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
                    lambda = Float.parseFloat(args[12]);
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
    private static void getEnt(@NotNull String[] args) {
        String mainDir = args[1];
        String dataDir = args[2];
        String outputDir = args[3];
        String jsonFile = args[4];
        String paraContextEntityFile = args[5];
        String secContextEntityFile = args[6];


        new GetEntities(mainDir, dataDir, outputDir, jsonFile, paraContextEntityFile,
                secContextEntityFile);
    }
    private static void options() {
        System.out.println("The following options are available: exp1, exp2, exp3, exp4, exp5, exp6");
        System.out.println("Use the --desc flag with the option (such as --exp1 --desc) " +
                "to view a description of the method.");
        System.out.println("Use the --use flag with the option (such as --exp1 --use) " +
                "to view a description of the command line arguments for the option.");
    }
    private static void help() {

        System.out.println("--exp1 (mainDir|dataDir|outputDir|jsonFile|aspectEntityFile|runFile|contextType)");

        System.out.println("--exp2 (indexDir|mainDir|dataDir|outputDir|jsonFile|contextEntityFile|aspectEntityFile|runFile|" +
                "useRelatedness|analyzer|similarity)");

        System.out.println("--exp3 (indexDir|mainDir|dataDir|outputDir|jsonFile|contextEntityFile|aspectEntityFile|runFile|" +
                "analyzer|similarity)");

        System.out.println("--exp4 (indexDir|mainDir|dataDir|outputDir|jsonFile|contextEntityFile|aspectEntityFile|runFile|" +
                "useRelatedness|analyzer|similarity)");

        System.out.println("--exp5 (pageIndexDir|mainDir|dataDir|outputDir|idFile|jsonFile|aspectEntityFile|runFile|" +
                "analyzer|similarity)");

        System.out.println("--exp6 (pageIndexDir|mainDir|dataDir|outputDir|idFile|jsonFile|takeKEntities|omitQueryTerms|" +
                "analyzer|similarity)");

        System.out.println("--exp7 (indexDir|mainDir|dataDir|outputDir|jsonFile|contextEntityFile|useRelatedness|" +
                "takeKEntities|omitQueryTerms|analyzer|similarity)");
    }

}
