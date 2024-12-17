package model.driivz;

import lombok.Data;

@Data
public class Connector{
    private int id;
    private String identityKey;
    private int activeEvTransaction;
    private String evseConnectorIdentityKey;
    private String name;
    private String legacyId;
    private double maxPowerKw;
    private boolean ignoreStatusNotification;
    private String status;
    private boolean reservable;
    private String teslaConnectionType;
    private boolean teslaAdapterInMaintenance;
    private boolean teslaHasAdapter;
    private boolean inMaintenance;
    private boolean smartChargingEnabled;
    private int wiredPhase;
    private String errorCode;
    private String posChargingStatus;
    private String errorMessage;
    private String placement;
    private double ratedVoltage;
    private int cableLength;
    private boolean allowOverridePlan;
    private String connectorType;
    private String notes;
}