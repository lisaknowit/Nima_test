package model.eonswitch.subscription;

import java.util.ArrayList;

import lombok.Data;

@Data
public class ObjectOperation {
    private ArrayList<String> objects;
    private ArrayList<String> operations;
    private String callbackUrl;
    private String bearerToken;
}
