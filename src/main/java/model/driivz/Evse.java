package model.driivz;

import java.util.ArrayList;

import lombok.Data;

@Data
public class Evse{
    private String identityKey;
    private String id;
    private ArrayList<Connector> connectors;
}
