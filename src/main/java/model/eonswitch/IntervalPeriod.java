package model.eonswitch;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IntervalPeriod{
    private String start;
    private String duration;
}