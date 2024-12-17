package model.driivz;

import java.util.ArrayList;

import lombok.Data;

@Data
public class LoginInfo {
    private String requestId;
    private String code;
    private String reason;
    private String message;
    private int httpStatusCode;
    private int count;
    private ArrayList<Ticket> data;
}