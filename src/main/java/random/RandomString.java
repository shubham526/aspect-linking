package random;

import org.jetbrains.annotations.NotNull;

/**
 * Generate a random ID.
 *
 * @author Shubham Chatterjee
 * @version 03/12/2020
 */

public class RandomString {

    /**
     * Method to generate a unique random ID.
     * @param n Integer Length of ID to generate.
     * @return String A unique randomly generated ID.
     */
    @NotNull
    public static String getID(int n) {

        // Choose a Character random from this String
        String AlphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + "0123456789"
                + "abcdefghijklmnopqrstuvxyz";

        // Create StringBuilder size of AlphaNumericString
        StringBuilder sb = new StringBuilder(n);

        for (int i = 0; i < n; i++) {

            // Generate a random number between 0 to length(AlphaNumericString)
            int index = (int)(AlphaNumericString.length() * Math.random());

            // Add Character one by one at the end of sb
            sb.append(AlphaNumericString.charAt(index));
        }
        return sb.toString();
    }
}
