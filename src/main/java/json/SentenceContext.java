package json;

import org.jetbrains.annotations.NotNull;
import org.json.simple.JSONObject;

/**
 * Class to represent a sentence context.
 * @author Shubham Chatterjee
 * @version 03/02/2020
 */

public class SentenceContext extends Context {

    public SentenceContext(@NotNull JSONObject o) {
        super((JSONObject) o.get("sent_context"));
    }
}
