package dto;

import java.util.ArrayList;

import lombok.Builder;
import lombok.Value;
import model.eonswitch.Interval;
import model.eonswitch.IntervalPeriod;
import model.eonswitch.PayloadDescriptor;
import model.eonswitch.ReportDescriptor;
import model.eonswitch.Target;

@Value
@Builder
public class EventRequest {
    private String id;

    private String eventName;

    private String objectType;

    private String programID;

    private IntervalPeriod intervalPeriod;
    
    private ArrayList<ReportDescriptor> reportDescriptors;

    private ArrayList<PayloadDescriptor> payloadDescriptors;

    private ArrayList<Interval> intervals;

    private ArrayList<Target> targets;
}