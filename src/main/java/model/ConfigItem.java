package model;

import lombok.Data;

@Data
public class ConfigItem {
    private String siteId;
    private String name;
    private String groupId;
    private String openAdrId;
    private Integer defaultMaxGridPower;
    private Integer maxGridPowerLimit;
    private Integer siteLimit;
    private String connector;
}