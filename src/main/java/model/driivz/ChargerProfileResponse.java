package model.driivz;
import lombok.Data;

import java.util.ArrayList;

@Data
public class ChargerProfileResponse {
    private String status;
    private String timestamp;
    private String requestId;
    private String code;
    private String reason;
    private String message;
    private int httpStatusCode;
    private int count;
    private ArrayList<Datum> data;
    private Object errors; 
}
