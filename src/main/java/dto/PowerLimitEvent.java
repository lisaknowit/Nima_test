package dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
@Data
@Builder
public class PowerLimitEvent {
    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String eventId;

    private String programId;

    @Getter(onMethod_ = @DynamoDbSortKey)
    private String powerLimitRequester;
    
    //Very important since this application involves both PROD and QA
    private String environmentRequester;
    
    private String startDateTime;
    private int duration;
    private String durationUnit;

    private int limit;
    private Unit limitUnit; // e.g KW

    private PayloadType payloadType;
    private String resource;

    private String activationDateTime;
    private String deactivationDateTime;

    private EventStatus eventStatus;
    
    private boolean acknowledged;
    private String eventAckReportJson;
}