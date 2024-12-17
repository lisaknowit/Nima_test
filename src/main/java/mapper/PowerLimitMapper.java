package mapper;

import java.util.ArrayList;

import dto.PayloadType;
import model.eonswitch.Payload;
import model.eonswitch.Target;

public class PowerLimitMapper {

    public Integer mapDuration(String duration){
        return Integer.parseInt(duration.substring(2, duration.length() - 1));
    }

    public String mapDurationUnit(String duration) throws Exception {
        String durationPart = duration.substring(duration.length() - 1); 
        switch (durationPart) {
            case "M":
                return "Minutes";
            case "H":
                return "Hours"; //TODO can it be hours?
            default:
                throw new Exception("Duration Unit can not be mapped");
        }
    }

    public PayloadType mapPayloadType(String payloadType) throws Exception{
        switch (payloadType) {
            case "POWER_LIMIT_ACKNOWLEDGEMENT":
                return PayloadType.POWER_LIMIT_ACKNOWLEDGEMENT;
            case "CONSUMPTION_POWER_LIMIT":
                return PayloadType.CONSUMPTION_POWER_LIMIT;
            case "POWER_LIMIT_VALIDATION_STATUS":
                return PayloadType.POWER_LIMIT_VALIDATION_STATUS;
            case "POWER_LIMIT_VALIDATION_TIMESTAMP":
                return PayloadType.POWER_LIMIT_VALIDATION_TIMESTAMP;
            case "POWER_LIMIT_VALIDATION_READING":
                return PayloadType.POWER_LIMIT_VALIDATION_READING;
            case "POWER_LIMIT_VALIDATION_STATUS_TEXT":
                return PayloadType.POWER_LIMIT_VALIDATION_STATUS_TEXT;
            default:
                throw new Exception("PayloadType can not be mapped");
        }
    }

    public String getResourceWithinTargets(ArrayList<Target> targets) throws Exception{
        for (Target target : targets) {
            if(target.getType().equals("RESOURCE_NAME")){
                return target.getValues().get(0);
            } 
        }

        throw new Exception("No resources could be found");
    }

    public int getConsumptionPowerLimit(ArrayList<Payload> payloads) throws Exception{
        for (Payload payload : payloads) {
            if(mapPayloadType(payload.getType()).equals(PayloadType.CONSUMPTION_POWER_LIMIT)){
                          
                return ((Double) payload.getValues().get(0)).intValue();
            }
            
        }
        throw new Exception("No Consumption limit could be found");
    }
}
