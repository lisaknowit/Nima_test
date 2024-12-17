package model.eonswitch;

import lombok.Data;

@Data
public class ReportDescriptor{
    private String payloadType;;
    private int startInterval;
    private boolean historical;
}