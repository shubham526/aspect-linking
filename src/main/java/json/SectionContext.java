package json;

import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

/**
 * Class to represent a section context.
 * @author Shubham Chatterjee
 * @version 03/02/2020
 */

public class SectionContext extends Context{

    public SectionContext(@NotNull JSONObject o) {
        super((JSONObject) o.get("sect_context"));
    }


}
