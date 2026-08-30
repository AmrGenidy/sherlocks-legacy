package common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;

public class InsightTokenUpdateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final int count;

    @JsonCreator
    public InsightTokenUpdateDTO(@JsonProperty("count") int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }
}
