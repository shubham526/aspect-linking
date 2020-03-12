package json;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to represent a JSON-L object.
 * Utility class which contains accessor methods for the object.
 * @author Shubham Chatterjee
 * @version 03/02/2020
 */

public class JsonObject {

    public static String getCorrectAspectId(@NotNull JSONObject ob) {
        return (String) ob.get("correct_aspect_id");
    }

    public static String getMention(@NotNull JSONObject ob) {
        return (String) ob.get("mention");
    }

    @NotNull
    @Contract("_ -> new")
    public static Context getSectionContext(JSONObject ob) {
        return new SectionContext(ob);
    }

    @NotNull
    @Contract("_ -> new")
    public static Context getSentenceContext(JSONObject ob) {
        return new SentenceContext(ob);

    }

    @NotNull
    @Contract("_ -> new")
    public static Context getParaContext(JSONObject ob) {
        return new ParaContext(ob);
    }

    @NotNull
    public static List<Aspect> getAspectCandidates(@NotNull JSONObject ob) {
        List<Aspect> candidateAspectList = new ArrayList<>();

        JSONArray candidates = (JSONArray) ob.get("aspect_candidates");
        for (Object c : candidates) {
            candidateAspectList.add(new Aspect((JSONObject) c));
        }
        return candidateAspectList;

    }

    @NotNull
    public static String getEntityName(@NotNull JSONObject ob) {
        String eid = (String) ob.get("entity");
        return eid.replaceAll("%20", "_");

    }

    public static String getEntityId(@NotNull JSONObject ob) {
        return (String) ob.get("entity");
    }

    @NotNull
    public static String getIdContext(@NotNull JSONObject ob) {
        return (String) ob.get("id_context");
    }
}
