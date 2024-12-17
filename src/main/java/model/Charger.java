package model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

@Value
@Builder
@Setter
@Getter
public class Charger {
    private int id;
    private String identityKey;
}
