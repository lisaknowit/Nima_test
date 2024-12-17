package model.driivz;

import java.util.ArrayList;

import lombok.Data;

@Data
public class Datum{
    private int id;
    private String identityKey;
    private String caption;
    private int chargerHostId;
    private String chargerStatus;
    private String temperatureScale;
    private String errorCode;
    private String provisionStatus;
    private String firmwareVersion;
    private int installationDate;
    private String updateStatus;
    private int provisioningDate;
    private int siteId;
    private int modelId;
    private String additionalSerialNumber;
    private String accessLevel;
    private boolean showIn3rdPartyFilter;
    private boolean hidden;
    private boolean excluded;
    private boolean disabled;
    private boolean managed;
    private String authenticationMode;
    private ArrayList<String> authenticationMethods;
    private ArrayList<String> paymentMethods;
    private boolean ignoreStatusNotifications;
    private boolean inMaintenance;
    private String status;
    private String serialNumber;
    private String chargingSpeed;
    private int usageStartDate;
    private ArrayList<Evse> evses;
    private InfrastructureCompany infrastructureCompany;
    private UtilityCompany utilityCompany;
    private ArrayList<Integer> groupIds;
    private String costCenter;
}
