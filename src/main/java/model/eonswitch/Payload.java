package model.eonswitch;

import java.util.ArrayList;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Payload{
    private String type;
    private ArrayList<Object> values;
}