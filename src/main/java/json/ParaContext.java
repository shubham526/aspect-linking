package json;

import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

/**
 * Class to represent a paragraph context.
 * @author Shubham Chatterjee
 * @version 03/02/2020
 */

public class ParaContext extends Context {

    public ParaContext(@NotNull JSONObject o) {
        super((JSONObject) o.get("para_context"));
    }


}
