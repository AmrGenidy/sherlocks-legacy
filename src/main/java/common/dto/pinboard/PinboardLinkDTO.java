package common.dto.pinboard;

import java.io.Serializable;

public class PinboardLinkDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum LinkType {
        RELATION,
        CONTRADICTION
    }

    private String startItemId;
    private String endItemId;
    private String color;
    private LinkType type; // New Field

    public PinboardLinkDTO() {}

    public PinboardLinkDTO(String startItemId, String endItemId, String color) {
        this(startItemId, endItemId, color, LinkType.RELATION);
    }

    public PinboardLinkDTO(String startItemId, String endItemId, String color, LinkType type) {
        this.startItemId = startItemId;
        this.endItemId = endItemId;
        this.color = color;
        this.type = type;
    }

    public String getStartItemId() { return startItemId; }
    public void setStartItemId(String startItemId) { this.startItemId = startItemId; }
    public String getEndItemId() { return endItemId; }
    public void setEndItemId(String endItemId) { this.endItemId = endItemId; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public LinkType getType() { return type; }
    public void setType(LinkType type) { this.type = type; }
}
