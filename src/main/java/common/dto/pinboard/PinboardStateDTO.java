package common.dto.pinboard;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class PinboardStateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<PinboardItemDTO> items;
    private List<PinboardLinkDTO> links;
    private Map<String, String> templateData;
    private Map<String, List<String>> templateDroppedItems;

    public PinboardStateDTO() {}

    public List<PinboardItemDTO> getItems() { return items; }
    public void setItems(List<PinboardItemDTO> items) { this.items = items; }

    public List<PinboardLinkDTO> getLinks() { return links; }
    public void setLinks(List<PinboardLinkDTO> links) { this.links = links; }

    public Map<String, String> getTemplateData() { return templateData; }
    public void setTemplateData(Map<String, String> templateData) { this.templateData = templateData; }

    public Map<String, List<String>> getTemplateDroppedItems() { return templateDroppedItems; }
    public void setTemplateDroppedItems(Map<String, List<String>> templateDroppedItems) { this.templateDroppedItems = templateDroppedItems; }
}
