package function;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import dto.EnvironmentRequester;
import dto.EventStatus;
import dto.LastRecurringAlarmTime;
import dto.PowerLimitEvent;
import model.ConfigItem;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsRequest;
import software.amazon.awssdk.services.cloudwatch.model.DescribeAlarmsResponse;
import software.amazon.awssdk.services.cloudwatch.model.MetricAlarm;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

public class HandlePowerLimit implements RequestHandler<Object, Map<String, String>> {

    private static final int TIME_BETWEEN_RECURRING_NOTIFICATIONS_HOURS = 6;
    private static final long INITIATE_POWER_LIMIT_THRESOLD_MINUTES = 5;
    private String name = System.getenv("POWERLIMITEVENTS_TABLE_NAME");
    private String recurringAlarmTableName = System.getenv("RECURRING_ALARM_TABLE_NAME");

    private DynamoDbEnhancedClient enhancedClient;
    private DynamoDbTable<PowerLimitEvent> table;
    
    private SsmClient ssmClient;

    // Nima Sites
    private List<ConfigItem> sites;
    private List<ConfigItem> QA_sites;

    private Map<String, String> parameters = new HashMap<>(); //prod values from parameter store
    private Map<String, String> QA_parameters = new HashMap<>(); //QA values from parameter store
    
    //Names of stored values in Parameter store
    private static final String CONFIG = "/Config";
    private static final String CLIENT_ID = "/Switch/ClientID";
    private static final String CLIENT_SECRET = "/Switch/ClientSecret";
    private static final String GRANT_TYPE =  "/Switch/GrantType";
    private static final String OAUTH_TOKEN_URL = "/Switch/OAuthTokenURL";
    private static final String SCOPE_URL = "/Switch/ScopeURL";
    private static final String SWITCH_BASE_URL = "/Switch/SwitchBaseURL";
    private static final String DRIIVZ_PASSWORD = "/DriivzPassword";
    private static final String DRIIVZ_USERNAME = "/DriivzUsername";
    private static final String DRIIVS_BASE_URL = "/DriivzBaseURL";
    
    private static final String QA_CONFIG = "/QA/Config";
    private static final String QA_CLIENT_ID = "/QA/Switch/ClientID";
    private static final String QA_CLIENT_SECRET = "/QA/Switch/ClientSecret";
    private static final String QA_GRANT_TYPE =  "/QA/Switch/GrantType";
    private static final String QA_OAUTH_TOKEN_URL = "/QA/Switch/OAuthTokenURL";
    private static final String QA_SCOPE_URL = "/QA/Switch/ScopeURL";
    private static final String QA_SWITCH_BASE_URL = "/QA/Switch/SwitchBaseURL";

    private DynamoDbTable<LastRecurringAlarmTime> reccuringAlarmTable;

    private String snsTopicArn;

    private CloudWatchClient cloudWatchClient;

    private DriivzManager driivzManager;
    private String environmentRequester;
    
    @Override
    public Map<String, String> handleRequest(Object input, Context context) {
        setPrerequisites();
        PageIterable<PowerLimitEvent> powerLimitEventsPageIterable = table.scan();

        // Read and monitor configuration
        mapConfigYAML();

        powerLimitEventsPageIterable.forEach(
                powerLimitEventsPage -> powerLimitEventsPage.items().forEach(powerLimitEvent -> {
                    try {
                        handlePowerLimit(powerLimitEvent);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
            );
        checkRecurringAlarms();

        return new HashMap<String, String>();
    }

    private void handlePowerLimit(PowerLimitEvent powerLimitEvent) throws Exception {
        environmentRequester = powerLimitEvent.getEnvironmentRequester();

        ConfigItem siteConfig = null;
        if (powerLimitEvent.getEnvironmentRequester().equals(EnvironmentRequester.QA.toString())) {
            siteConfig = getConfigItemFromResource(QA_sites, powerLimitEvent.getResource());
        } else {
            siteConfig = getConfigItemFromResource(sites, powerLimitEvent.getResource());
        }
        if (siteConfig == null) {
            throw new Exception("[Nima-"+ environmentRequester +"][ALARM TRIGGER Configuration Error] Could not find site in configurations");
        }

        System.out.println("[Nima-"+ environmentRequester +"] Handle Power Limit Event: " + powerLimitEvent);
        sendAckIfNeeded(powerLimitEvent);

        if (hasPassedDeactivationLimit(powerLimitEvent)) {
            System.out.println("[Nima-"+ environmentRequester +"] Has passed deactivation time limit");
            if (shouldDeactivate(powerLimitEvent)) {
                System.out.println("[Nima-"+ environmentRequester +"] Deactivate PowerLimit");
                deactivatePowerLimit(powerLimitEvent, siteConfig);
            } else if (!isSiteUp(powerLimitEvent, siteConfig)) {
                // TODO: Add error logs here if time limit for restart has been reached
            } else {
                System.out.println("[Nima-"+ environmentRequester +"] The site is up remove. PowerLimit : " + powerLimitEvent.getResource());
                table.deleteItem(powerLimitEvent);
            }
        } else if (hasPassedActivationTimeLimit(powerLimitEvent)) {
            System.out.println("[Nima-"+ environmentRequester +"] Has passed activation time limit");
            if (shouldActivatePowerLimit(powerLimitEvent)) {
                System.out.println("[Nima-"+ environmentRequester +"] Should activate");
                activatePowerLimit(powerLimitEvent, siteConfig);
            } else {
                retryCloseIfNeeded(powerLimitEvent, siteConfig);
            }
        } else {
            System.out.println("[Nima-"+ environmentRequester +"] Pending power limit event. Resource: " + powerLimitEvent.getResource());
        }
    }

    private void deactivatePowerLimit(PowerLimitEvent powerLimitEvent, ConfigItem siteConfig) {
        String groupId = siteConfig.getGroupId();
        int resetLimit = siteConfig.getDefaultMaxGridPower();
        switch (powerLimitEvent.getPowerLimitRequester()) {
            case "SWITCH":
                System.out.println("[Switch-"+ environmentRequester +"] Deactivate power limit event: "
                    + powerLimitEvent.getResource());
                driivzManager.executePowerLimitForResource(powerLimitEvent.getResource(), groupId, resetLimit, powerLimitEvent.getEnvironmentRequester());
                powerLimitEvent.setEventStatus(EventStatus.DEACTIVATED);
                powerLimitEvent.setDeactivationDateTime(ZonedDateTime.now().toString());
                table.putItem(powerLimitEvent);
                break;
            default:
                throw new UnsupportedOperationException("Power limit activation " +
                        "is not yet implemented for net owner: " + powerLimitEvent.getPowerLimitRequester());
        }
    }

    private boolean isSiteUp(PowerLimitEvent powerLimitEvent, ConfigItem siteConfig) {
        System.out.println("[Nima-"+ environmentRequester +"] isSiteUp? resource: " + siteConfig.getName());
        String groupId = siteConfig.getGroupId();
        switch (powerLimitEvent.getPowerLimitRequester()) {
            case "SWITCH":
                return driivzManager.isSiteUp(groupId, powerLimitEvent.getEnvironmentRequester());
            default:
                throw new UnsupportedOperationException("Power limit activation " +
                        "is not yet implemented for net owner: " + powerLimitEvent.getPowerLimitRequester());
        }
    }

    private void retryCloseIfNeeded(PowerLimitEvent powerLimitEvent, ConfigItem siteConfig) {
        System.out.println("[Nima-"+ environmentRequester +"] Retry close if needed: " + siteConfig.getName());
        String groupId = siteConfig.getGroupId();
        switch (powerLimitEvent.getPowerLimitRequester()) {
            case "SWITCH":
                driivzManager.retryCloseChargersIfNotClosed(groupId, powerLimitEvent.getLimit(), powerLimitEvent.getEnvironmentRequester());
                break;
            default:
                throw new UnsupportedOperationException("Power limit activation " +
                        "is not yet implemented for net owner: " + powerLimitEvent.getPowerLimitRequester());
        }
    }

    private void activatePowerLimit(PowerLimitEvent powerLimitEvent, ConfigItem siteConfig) {
        String groupId = siteConfig.getGroupId();
        int powerLimit  = siteConfig.getMaxGridPowerLimit();

        switch (powerLimitEvent.getPowerLimitRequester()) {
            case "SWITCH":
                System.out.println("[Nima-"+ environmentRequester +"][SWITCH] Activate power limit event resource: " + powerLimitEvent.getResource());
                driivzManager.executePowerLimitForResource(powerLimitEvent.getResource(), groupId, powerLimit, powerLimitEvent.getEnvironmentRequester());
                powerLimitEvent.setEventStatus(EventStatus.ACTIVE);
                powerLimitEvent.setActivationDateTime(ZonedDateTime.now().toString());
                table.putItem(powerLimitEvent);
                break;
            default:
                throw new UnsupportedOperationException("Power limit activation " +
                        "is not yet implemented for net owner: " + powerLimitEvent.getPowerLimitRequester());
        }
    }

    private ConfigItem getConfigItemFromResource(List<ConfigItem> sites, String resource) {
        for (ConfigItem site : sites) {
            if(site.getOpenAdrId().equals(resource)){
                return site;
            }
        }
        return null;
    }

    private boolean shouldActivatePowerLimit(PowerLimitEvent powerLimitEvent) {
        System.out.println("[Nima-"+ environmentRequester +"] Should activate? " + powerLimitEvent.getEventStatus());

        boolean result = powerLimitEvent.getEventStatus().equals(EventStatus.PENDING) ||
                powerLimitEvent.getEventStatus().equals(EventStatus.FAILED);
        System.out.println("[Nima-"+ environmentRequester +"] should activate: " + result);
        return result;
    }

    private boolean shouldDeactivate(PowerLimitEvent powerLimitEvent) {
        System.out.println("[Nima-"+ environmentRequester +"] Should Deactivate? status: " + powerLimitEvent.getStartDateTime());
        return powerLimitEvent.getEventStatus().equals(EventStatus.ACTIVE);
    }

    private void sendAckIfNeeded(PowerLimitEvent powerLimitEvent) {
        System.out.println("[Nima-"+ environmentRequester +"] Send acknowledge if needed " + powerLimitEvent.getResource());
        
        switch (powerLimitEvent.getPowerLimitRequester()) {
            case "SWITCH":
                if (!powerLimitEvent.isAcknowledged()) {
                    System.out.println("[Nima-"+ environmentRequester +"][SWITCH] Acknowledge: " + powerLimitEvent.getResource());
                    acknowledgePowerLimitEvent(powerLimitEvent);
                }
                break;
                // Add net owners here
            default:
                // Do nothing here
                break;
        }
    }

    private boolean hasPassedDeactivationLimit(PowerLimitEvent powerLimitEvent) {
        return ZonedDateTime.now().isAfter(ZonedDateTime.parse(
                powerLimitEvent.getStartDateTime()).plusMinutes(powerLimitEvent.getDuration()));
    }

    private boolean hasPassedActivationTimeLimit(PowerLimitEvent powerLimitEvent) {
        return ZonedDateTime.now().isAfter(ZonedDateTime.parse(
                powerLimitEvent.getStartDateTime()).minusMinutes(INITIATE_POWER_LIMIT_THRESOLD_MINUTES));
    }

    private void acknowledgePowerLimitEvent(PowerLimitEvent powerLimitEvent) {
        SwitchManager switchManager = SwitchManager.builder().build();
        if (isEnvironmentRequesterQA(powerLimitEvent.getEnvironmentRequester())) {
            switchManager.sendAcknowledgementReport(powerLimitEvent, QA_parameters.get(QA_SWITCH_BASE_URL),
                    QA_parameters.get(QA_GRANT_TYPE),
                    QA_parameters.get(QA_CLIENT_ID), QA_parameters.get(QA_CLIENT_SECRET),
                    QA_parameters.get(QA_SCOPE_URL), QA_parameters.get(QA_OAUTH_TOKEN_URL), environmentRequester);
        } else {
            switchManager.sendAcknowledgementReport(powerLimitEvent, parameters.get(SWITCH_BASE_URL),
                    parameters.get(GRANT_TYPE),
                    parameters.get(CLIENT_ID), parameters.get(CLIENT_SECRET), parameters.get(SCOPE_URL),
                    parameters.get(OAUTH_TOKEN_URL), environmentRequester);
        }
        powerLimitEvent.setAcknowledged(true);
        table.putItem(powerLimitEvent);
    }

    private boolean isEnvironmentRequesterQA(String environmentRequester){
        return environmentRequester.equals(EnvironmentRequester.QA.toString());
    }

    /**
     * Handles prerequisites
     * Gets configuration from AWS System Manager - Parameter Store
     */
    private void setPrerequisites() {
        // The dynmamodb client
        enhancedClient = DynamoDbEnhancedClient.builder().build();
        table = enhancedClient.table(name, TableSchema.fromBean(PowerLimitEvent.class));
        reccuringAlarmTable = enhancedClient.table(recurringAlarmTableName,
            TableSchema.fromBean(LastRecurringAlarmTime.class));

        // The parameter store client
        ssmClient = SsmClient.builder()
                .region(DefaultAwsRegionProviderChain.builder().build().getRegion())
                .build();

        cloudWatchClient = CloudWatchClient.create();

        GetParametersRequest request = GetParametersRequest.builder()
                .names( DRIIVS_BASE_URL,
                        DRIIVZ_PASSWORD,
                        DRIIVZ_USERNAME,
                        CLIENT_ID,
                        CLIENT_SECRET,
                        GRANT_TYPE,
                        OAUTH_TOKEN_URL,
                        SCOPE_URL,
                        SWITCH_BASE_URL)
                .withDecryption(true)
                .build();
        GetParametersRequest requestQA = GetParametersRequest.builder()
                .names( QA_CLIENT_ID,
                        QA_CLIENT_SECRET,
                        QA_GRANT_TYPE,
                        QA_OAUTH_TOKEN_URL,
                        QA_SCOPE_URL,
                        QA_SWITCH_BASE_URL)
                .withDecryption(true)
                .build();

        GetParametersResponse response = ssmClient.getParameters(request);
        GetParametersResponse QA_response = ssmClient.getParameters(requestQA);

        for (Parameter parameter : QA_response.parameters()) {
            QA_parameters.put(parameter.name(), parameter.value());
        }
        for (Parameter parameter : response.parameters()) {
            parameters.put(parameter.name(), parameter.value());
        }
        driivzManager = new DriivzManager(parameters.get(DRIIVS_BASE_URL), parameters.get(DRIIVZ_USERNAME), parameters.get(DRIIVZ_PASSWORD));
    }

    private void mapConfigYAML() {
        GetParametersRequest request = GetParametersRequest.builder()
                .names(CONFIG,
                        QA_CONFIG)
                .withDecryption(true)
                .build();

        GetParametersResponse response = ssmClient.getParameters(request);

        for (Parameter parameter : response.parameters()) {
            if(parameter.name().equals(CONFIG)){
                parameters.put(parameter.name(), parameter.value());
            }else{
                QA_parameters.put(parameter.name(), parameter.value());
            }
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            sites = mapper.readValue(parameters.get(CONFIG), new TypeReference<List<ConfigItem>>() {});
            QA_sites = mapper.readValue(QA_parameters.get(QA_CONFIG), new TypeReference<List<ConfigItem>>() {});
        } catch (IOException e) {
            System.out.println("[Nima-"+ environmentRequester +"][ALARM TRIGGER Configuration Error]" +
                " Configuration error. Make sure yaml config in parameter store in AWS correct");
            e.printStackTrace();
        }
    }

    /*
     * Checks if alarms are active. Send notifications for active alarms if the time is right
     */
    private void checkRecurringAlarms() {
        String configAlarm = System.getenv("CONFIG_ALARM");
        String driivzAlarm = System.getenv("DRIIVZ_ALARM");
        String switchAlarm = System.getenv("SWITCH_ALARM");
        String subscriptionAlarm = System.getenv("SUBSCRIPTION_ALARM");
        snsTopicArn = System.getenv("SNS_TOPIC_ARN");

        List<String> alarmNames = new ArrayList<>();
        alarmNames.add(configAlarm);
        alarmNames.add(driivzAlarm);
        alarmNames.add(switchAlarm);
        alarmNames.add(subscriptionAlarm);

        alarmNames.forEach((alarmName) -> {
            System.out.println("Check alarm: " + alarmName);
            if (isAlarmActive(alarmName)) {
                System.out.println("Alarm active: " + alarmName);
                if (shouldSendNotification(alarmName)) {
                    System.out.println("Should send notification: " + alarmName);
                    sendNotification(alarmName);
                    updateLastSentNotification(alarmName);
                }
            }
        });
    }

    private boolean isAlarmActive(String alarmName) {
        DescribeAlarmsRequest describeRequest = DescribeAlarmsRequest.builder()
                .alarmNames(alarmName)
                .build();

        DescribeAlarmsResponse describeResponse = cloudWatchClient.describeAlarms(describeRequest);
        List<MetricAlarm> alarms = describeResponse.metricAlarms();

        if (!alarms.isEmpty()) {
            MetricAlarm alarm = alarms.get(0);
            if ("ALARM".equals(alarm.stateValueAsString())) {
                return true;
            }
        }
        return false;
    }

    /*
     * Check in the database when last time notfication was sent
     * for the alarm and return true if it has passed long enough time
     */
    private boolean shouldSendNotification(String alarmName) {
        LastRecurringAlarmTime lastRecurringAlarmTime =
        reccuringAlarmTable.getItem(r -> r.key(k -> k.partitionValue(alarmName)));

        String lastRecurringAlarmTimeString = null;
        if (lastRecurringAlarmTime != null) {
            lastRecurringAlarmTimeString = lastRecurringAlarmTime.getLastAlarmDateTime();
        }

        if (lastRecurringAlarmTimeString != null && ZonedDateTime.parse(lastRecurringAlarmTimeString)
                .isAfter(ZonedDateTime.now().minusHours(TIME_BETWEEN_RECURRING_NOTIFICATIONS_HOURS))) {
            System.out.println("Last notification was sent: " + lastRecurringAlarmTimeString);
            return false;
        }
        return true;
    }

    private void sendNotification(String alarmName) {
        SnsClient snsClient = SnsClient.create();
        String message = String.format("Alarm '%s' is still in ALARM state.", alarmName);
        PublishRequest publishRequest = PublishRequest.builder()
            .topicArn(snsTopicArn)
            .message(message)
            .subject("Recurring Alarm Notification")
            .build();

        PublishResponse response = snsClient.publish(publishRequest);
        System.out.println("Notification sent: " + response);
    }

    private void updateLastSentNotification(String alarmName) {
        reccuringAlarmTable.putItem(LastRecurringAlarmTime.builder()
            .alarmName(alarmName).lastAlarmDateTime(ZonedDateTime.now().toString()).build());
    }
}
