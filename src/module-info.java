/**
 * 
 */
/**
 * 
 */
module MyDetectiveGame {
    requires com.fasterxml.jackson.databind;
    exports main.java.Core;
    opens main.java.Core;
    exports main.java.JsonDTO;
    opens main.java.JsonDTO; // Add this line for reflection
}