package common.dto.pinboard;

import java.io.Serializable;

public class PinboardItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String type; // EVIDENCE, NOTE
    private String title;
    private String content;
    private String relatedJournalEntryId;
    private double x;
    private double y;
    private double width;
    private double height;
    private String color;
    private String author; // For sticky notes

    public PinboardItemDTO() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getRelatedJournalEntryId() { return relatedJournalEntryId; }
    public void setRelatedJournalEntryId(String relatedJournalEntryId) { this.relatedJournalEntryId = relatedJournalEntryId; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
}
