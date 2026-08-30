package ui.pinboard;

import java.io.Serializable;
import java.util.UUID;

public class PinboardItemModel implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum ItemType {
        EVIDENCE,
        NOTE,
        TEMPLATE_ENTRY, // For items placed in the template slots
        SUSPECT
    }

    private String id;
    private ItemType type;
    private double x;
    private double y;
    private String title;
    private String content;
    private String color; // Hex color
    private double width;
    private double height;
    private String relatedJournalEntryId; // To de-dupe items from the journal

    public PinboardItemModel() {
        this.id = UUID.randomUUID().toString();
        this.width = 150;
        this.height = 100;
        this.color = "#fdfd96";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public String getRelatedJournalEntryId() {
        return relatedJournalEntryId;
    }

    public void setRelatedJournalEntryId(String relatedJournalEntryId) {
        this.relatedJournalEntryId = relatedJournalEntryId;
    }
}
