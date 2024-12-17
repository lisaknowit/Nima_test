package model.eonswitch.subscription;

import java.util.ArrayList;

import lombok.Builder;
import lombok.Value;
import model.eonswitch.Target;

@Value
@Builder
public class VTNSubscription {
    private String id;
    private String createdDateTime;
    private String modificationDateTime;
    private String objectType;
    private String clientName;
    private String programID;
    private ArrayList<ObjectOperation> objectOperations; //Refering to Webtooken in Switch
    private ArrayList<Target> targets;
}
