package model.eonswitch.subscription;

import java.util.ArrayList;

import lombok.Builder;
import lombok.Value;
import model.eonswitch.PayloadDescriptor;
import model.eonswitch.Target;

@Value
@Builder
public class ProgramResponse {
    private int id;
    private String createdDateTime;
    private String modificationDateTime;
    private String objectType;
    private String programName;
    private String timeZoneOffset;
    private ArrayList<String> programDescriptions;
    private boolean bindingEvents;
    private boolean localPrice;
    private ArrayList<PayloadDescriptor> payloadDescriptors;
    private ArrayList<Target> targets;
}


