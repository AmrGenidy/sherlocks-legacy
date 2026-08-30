package common.dto.pinboard;

import java.io.Serializable;

public class PinboardUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum UpdateType {
        ADD_ITEM,
        MOVE_ITEM,
        RESIZE_ITEM,
        UPDATE_CONTENT,
        REMOVE_ITEM,
        ADD_LINK,
        REMOVE_LINK,
        UPDATE_TEMPLATE_NOTE,
        UPDATE_TEMPLATE_DROP,
        CLEAR_BOARD
    }

    private UpdateType type;
    private PinboardItemDTO item;
    private PinboardLinkDTO link;
    private String targetId; // For removal or specific updates
    private String key; // For template map keys
    private String value; // For template map values

    // For MOVE_ITEM optimization (send less data)
    private double newX;
    private double newY;

    public PinboardUpdateDTO() {}

    public PinboardUpdateDTO(UpdateType type) {
        this.type = type;
    }

    public UpdateType getType() { return type; }
    public void setType(UpdateType type) { this.type = type; }

    public PinboardItemDTO getItem() { return item; }
    public void setItem(PinboardItemDTO item) { this.item = item; }

    public PinboardLinkDTO getLink() { return link; }
    public void setLink(PinboardLinkDTO link) { this.link = link; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public double getNewX() { return newX; }
    public void setNewX(double newX) { this.newX = newX; }

    public double getNewY() { return newY; }
    public void setNewY(double newY) { this.newY = newY; }
}
