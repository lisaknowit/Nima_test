package model.eonswitch;

import java.util.ArrayList;

import dto.EventRequest;
import lombok.Data;

@Data
public class WebhookEventRequest {
    private String objectType;
    private String operation;
    private ArrayList<Target> targets;
    private EventRequest object;
}
