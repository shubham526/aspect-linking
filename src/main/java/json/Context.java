package json;

import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface for a context in the JSON file.
 * Concrete classes will represent a sentence, section or paragraph.
 * @author Shubham Chatterjee
 * @version 03/02/2020
 */

public class Context {

    private final String content;
    private final List<String> entityList = new ArrayList<>();

    public Context(@NotNull JSONObject o) {
        content = (String) o.get("content");
        JSONArray entities = (JSONArray) o.get("entities");
        for (Object e : entities) {
            entityList.add(e.toString());
        }
    }

    public String getContent() {
        return content;
    }
    public List<String> getEntityList() {
        return entityList;
    }

}
