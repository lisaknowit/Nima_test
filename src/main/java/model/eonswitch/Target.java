package model.eonswitch;

import java.util.ArrayList;

import lombok.Data;

@Data
public class Target {
    private String type;
    private ArrayList<String> values;
}