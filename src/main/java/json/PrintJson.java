package json;

import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class PrintJson {
    public static void main(@NotNull String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        // Read the JSON-L file
        List<JSONObject> jsonObjects = ReadJsonlFile.read(args[0]);
        List<String> entityList;

        // For every JSON-L object, print its contents
        for (JSONObject o : jsonObjects) {
            System.out.println("Correct Aspect ID: " + JsonObject.getCorrectAspectId(o));
            System.out.println("Mention: " + JsonObject.getMention(o));

            System.out.println("-----------------------------------------------------");
            Context sentenceContext = JsonObject.getSentenceContext(o);
            System.out.println("Sentence Context Info: ");
            System.out.println("Content: ");
            System.out.println(sentenceContext.getContent());
            System.out.println("Entities:");
            entityList = sentenceContext.getEntityList();
            for (String e : entityList) {
                System.out.println(e);
            }
            System.out.println("-----------------------------------------------------");

            Context sectionContext = JsonObject.getSectionContext(o);
            System.out.println("Section Context Info: ");
            System.out.println("Content: ");
            System.out.println(sectionContext.getContent());
            System.out.println("Entities:");
            entityList = sectionContext.getEntityList();
            for (String e : entityList) {
                System.out.println(e);
            }
            System.out.println("-----------------------------------------------------");

            Context paraContext = JsonObject.getParaContext(o);
            System.out.println("Para Context Info: ");
            System.out.println("Content: ");
            System.out.println(paraContext.getContent());
            System.out.println("Entities:");
            entityList = paraContext.getEntityList();
            for (String e : entityList) {
                System.out.println(e);
            }

            System.out.println("Candidate Aspects --->");
            List<Aspect> candidates = JsonObject.getAspectCandidates(o);
            for (Aspect aspect : candidates) {
                System.out.println("Id: " + aspect.getId());
                System.out.println("Header: " + aspect.getHeader());
                System.out.println("Content: " + aspect.getContent());
                System.out.println("Entities: " + aspect.getEntityList().size());
            }

            System.out.println("Entity Name: " + JsonObject.getEntityName(o));
            System.out.println("Entity Id: " + JsonObject.getEntityId(o));
            System.out.println("Id Context: " + JsonObject.getIdContext(o));

            System.out.println("=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+");
            try {
                br.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

    }
}
