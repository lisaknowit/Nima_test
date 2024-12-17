package dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@AllArgsConstructor
@NoArgsConstructor
@DynamoDbBean
@Data
@Builder
public class LastRecurringAlarmTime {
    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String alarmName;

    private String lastAlarmDateTime;
}
