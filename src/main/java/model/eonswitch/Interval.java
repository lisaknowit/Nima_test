package model.eonswitch;

import java.util.ArrayList;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Interval{
    private int id;
    private ArrayList<Payload> payloads;
}
