package ui.pinboard;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PinboardModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<PinboardItemModel> items = new ArrayList<>();
    private List<PinboardLinkModel> links = new ArrayList<>();
    private java.util.Map<String, String> templateData = new java.util.HashMap<>();
    private java.util.Map<String, java.util.List<String>> templateDroppedItems = new java.util.HashMap<>();

    public List<PinboardItemModel> getItems() {
        return items;
    }

    public void setItems(List<PinboardItemModel> items) {
        this.items = items;
    }

    public List<PinboardLinkModel> getLinks() {
        return links;
    }

    public void setLinks(List<PinboardLinkModel> links) {
        this.links = links;
    }

    public java.util.Map<String, String> getTemplateData() {
        return templateData;
    }

    public void setTemplateData(java.util.Map<String, String> templateData) {
        this.templateData = templateData;
    }

    public java.util.Map<String, java.util.List<String>> getTemplateDroppedItems() {
        return templateDroppedItems;
    }

    public void setTemplateDroppedItems(java.util.Map<String, java.util.List<String>> templateDroppedItems) {
        this.templateDroppedItems = templateDroppedItems;
    }
}
