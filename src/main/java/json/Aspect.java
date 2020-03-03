package json;

import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to represent a candidate aspect.
 * Each candidate aspect has four attributes:
 * (1) ID
 * (2) Header
 * (3) Content
 * (4) List of entities
 *
 * @author Shubham Chatterjee
 * @version 03/02/2020
 */

public class Aspect {
    private final String id;
    private final String name;
    private final String header;
    private final String content;
    private final List<String> entityList = new ArrayList<>();

    public Aspect(@NotNull JSONObject o) {
        id = (String) o.get("id_aspect");
        name = id.replaceAll("%20", "_");
        header = (String) o.get("header");
        content = (String) o.get("content");
        JSONArray entities = (JSONArray) o.get("entities");
        for (Object e : entities) {
            entityList.add(e.toString());
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getHeader() {
        return header;
    }

    public String getContent() {
        return content;
    }

    public List<String> getEntityList() {
        return entityList;
    }
}
