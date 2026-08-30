package common.dto;

import java.io.Serial;
import java.io.Serializable;

public class DeductionCountUpdateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private int count;

    public DeductionCountUpdateDTO() {
    }

    public DeductionCountUpdateDTO(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
