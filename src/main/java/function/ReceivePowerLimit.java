package function;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;

import dto.EnvironmentRequester;
import dto.EventRequest;
import dto.EventStatus;
import dto.PowerLimitEvent;
import dto.PowerLimitRequester;
import dto.Unit;
import mapper.PowerLimitMapper;
import model.eonswitch.WebhookEventRequest;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.LambdaException;

public class ReceivePowerLimit implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private String name;
    private String handlePowerLimitFunctionArn;
    private DynamoDbEnhancedClient enhancedClient;
    private DynamoDbTable<PowerLimitEvent> table;

    public ReceivePowerLimit() {
        name = System.getenv("POWERLIMITEVENTS_TABLE_NAME");
        handlePowerLimitFunctionArn = System.getenv("HANDLEPOWERLIMITFUNCTION_FUNCTION_ARN");

        enhancedClient = DynamoDbEnhancedClient.builder().build();
        table = enhancedClient.table(name, TableSchema.fromBean(PowerLimitEvent.class));
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        WebhookEventRequest webhookEventRequest = new Gson().fromJson(request.getBody(),
                WebhookEventRequest.class);
        EventRequest eventRequest = webhookEventRequest.getObject();
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder().build();
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = new APIGatewayProxyResponseEvent();
        if (!isPowerLimitEventInDB(eventRequest.getId())) {
            try {
                if (request.getPath().contains("/qa")) {
                    apiGatewayProxyResponseEvent = putItem(enhancedClient, eventRequest, EnvironmentRequester.QA);
                    System.out.println("Event sent from QA");
                } else {
                    apiGatewayProxyResponseEvent = putItem(enhancedClient, eventRequest, EnvironmentRequester.PROD);
                    System.out.println("Event sent from PROD");
                }
            } catch (Exception e) {
                System.out.println("[ALARM TRIGGER Database Error] Could not add PowerLimitEvent in DB");
                e.printStackTrace();
                apiGatewayProxyResponseEvent.setStatusCode(400);
                apiGatewayProxyResponseEvent.setBody("Could not create PowerLimitEvent");
            }
            invokeFunction(handlePowerLimitFunctionArn);
        } else {
            System.out.println("PowerLimitEvent with same the id is already in the database. " +
                    "Updating PowerLimitEvents is not currently allowed.");
        }
        return apiGatewayProxyResponseEvent;
    }

    private APIGatewayProxyResponseEvent putItem(DynamoDbEnhancedClient enhancedClient, EventRequest eventRequest,
            EnvironmentRequester environmentRequester)
            throws Exception {
        PowerLimitMapper mapper = new PowerLimitMapper();

        PowerLimitEvent event = PowerLimitEvent.builder()
                .eventId(eventRequest.getId())
                .programId(eventRequest.getProgramID())
                .powerLimitRequester(PowerLimitRequester.SWITCH.toString())
                .environmentRequester(environmentRequester.toString())
                .startDateTime(eventRequest.getIntervalPeriod().getStart())
                .duration(mapper.mapDuration(
                        eventRequest.getIntervalPeriod().getDuration()))
                .durationUnit(mapper.mapDurationUnit(
                        eventRequest.getIntervalPeriod().getDuration()))
                .limit(mapper.getConsumptionPowerLimit(
                        eventRequest.getIntervals().get(0).getPayloads()))
                .limitUnit(Unit.KW) // TODO Ska vi hämta ut den ur request??
                .resource(mapper.getResourceWithinTargets(eventRequest.getTargets()))
                .payloadType(mapper.mapPayloadType(
                        eventRequest.getReportDescriptors().get(0)
                                .getPayloadType()))
                .eventStatus(EventStatus.PENDING)
                .acknowledged(false)
                .build();

        PutItemEnhancedRequest<PowerLimitEvent> putItemRequest = PutItemEnhancedRequest
                .builder(PowerLimitEvent.class)
                .item(event)
                .build();

        WriteBatch batch = WriteBatch.builder(PowerLimitEvent.class)
                .addPutItem(putItemRequest)
                .mappedTableResource(table)
                .build(); // This line throws the IllegalArgumentException
        BatchWriteItemEnhancedRequest batchWriteRequest = BatchWriteItemEnhancedRequest
                .builder()
                .addWriteBatch(batch)
                .build();

        enhancedClient.batchWriteItem(batchWriteRequest);

        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = new APIGatewayProxyResponseEvent();
        apiGatewayProxyResponseEvent.setStatusCode(200);
        apiGatewayProxyResponseEvent.setBody("Added PowerLimitEvent: " + new Gson().toJson(event));
        return apiGatewayProxyResponseEvent;
    }

    // Will invoke HandlePowerLimit Function, which handles ACK to Switch plattform
    // among other things
    private void invokeFunction(String functionName) {
        try {
            Region region = DefaultAwsRegionProviderChain.builder().build().getRegion();
            LambdaClient awsLambda = LambdaClient.builder()
                    .region(region)
                    .build();
            InvokeRequest request = InvokeRequest.builder()
                    .functionName(functionName)
                    .build();
            awsLambda.invoke(request);

            awsLambda.close();
        } catch (LambdaException e) {
            System.err.println(e.getMessage());
        }
    }

    private boolean isPowerLimitEventInDB(String eventId) {
        Key key = Key.builder()
                .partitionValue(eventId)
                .sortValue("SWITCH")
                .build();
        PowerLimitEvent powerLimitEvent = table.getItem(r -> r.key(key));
        return (powerLimitEvent != null);
    }
}