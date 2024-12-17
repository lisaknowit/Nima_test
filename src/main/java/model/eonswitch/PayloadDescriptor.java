package model.eonswitch;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayloadDescriptor{
    private String payloadType;
    private String units;
}