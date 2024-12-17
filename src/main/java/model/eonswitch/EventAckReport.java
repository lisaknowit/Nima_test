package model.eonswitch;

import java.util.ArrayList;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EventAckReport {
    private String eventID;
    private String programID;
    private String clientName;
    
    private ArrayList<PayloadDescriptor> payloadDescriptors;

    private ArrayList<Resource> resources;
}