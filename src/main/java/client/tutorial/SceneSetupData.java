package client.tutorial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Represents the dummy room data for SETUP_SCENE tutorial steps.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SceneSetupData {

    private String roomName;
    private List<String> exits;
    private List<String> objects;
    private List<String> suspects;

    public SceneSetupData() {
    }

    public SceneSetupData(String roomName, List<String> exits, List<String> objects, List<String> suspects) {
        this.roomName = roomName;
        this.exits = exits;
        this.objects = objects;
        this.suspects = suspects;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public List<String> getExits() {
        return exits;
    }

    public void setExits(List<String> exits) {
        this.exits = exits;
    }

    public List<String> getObjects() {
        return objects;
    }

    public void setObjects(List<String> objects) {
        this.objects = objects;
    }

    public List<String> getSuspects() {
        return suspects;
    }

    public void setSuspects(List<String> suspects) {
        this.suspects = suspects;
    }
}