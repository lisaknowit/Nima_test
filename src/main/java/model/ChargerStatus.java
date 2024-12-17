package model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ChargerStatus {
    public String status;
    public String serialNumber;
    public String chargingSpeed;
}
