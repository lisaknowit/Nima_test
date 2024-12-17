package model.eonswitch;

import java.util.ArrayList;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Resource {
    private String resourceName;
    private IntervalPeriod intervalPeriod;
    private ArrayList<Interval> intervals;
}
