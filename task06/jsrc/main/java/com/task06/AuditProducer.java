package com.task06;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.fasterxml.uuid.Generators;
import com.syndicate.deployment.annotations.EventSource;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@LambdaHandler(
        lambdaName = "audit_producer",
        roleName = "audit_producer-role",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
        runtime = DeploymentRuntime.JAVA11,
        snapStart = LambdaSnapStart.PublishedVersions
)
@DynamoDbTriggerEventSource(
        targetTable = "Configuration",
        batchSize = 1
)
@DependsOn(
        name = "Configuration",
        resourceType = ResourceType.DYNAMODB_TABLE
)
@EventSource(eventType = EventSourceType.DYNAMODB_TRIGGER)
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "region", value = "${region}"),
        @EnvironmentVariable(key = "table", value = "${target_table}")
	}
)

public class AuditProducer implements RequestHandler<DynamodbEvent, String> {
    private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
    private final DynamoDB dynamoDB = new DynamoDB(client);
    private final Table auditTable = dynamoDB.getTable("cmtr-e288a3c1-Audit-test");

    @Override
    public String handleRequest(DynamodbEvent event, Context context) {
        context.getLogger().log("Lambda is triggered");
        for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
            if (record.getEventName().equals("INSERT") || record.getEventName().equals("MODIFY")) {

                Map<String, AttributeValue> newImage = record.getDynamodb().getNewImage();
                context.getLogger().log("new image = " + newImage);
                Map<String, AttributeValue> oldImage = record.getDynamodb().getOldImage();
                context.getLogger().log("old image = " + newImage);
                String itemKey = newImage.get("key").getS();
                Instant modificationTime = Instant.now();
                AtomicLong idCounter = new AtomicLong();
                long numericId = idCounter.incrementAndGet();
                Item auditItem = new Item()
                        .withNumber("id", numericId)
                        .withString("itemKey", itemKey)
                        .withString("modificationTime", modificationTime.toString());

                if (record.getEventName().equals("INSERT")) {
                    auditItem.withMap("newValue", toItemMap(newImage));
                } else if (record.getEventName().equals("MODIFY")) {
                    auditItem
                            .withString("updatedAttribute", "value")
                            .withNumber("oldValue", Integer.parseInt(oldImage.get("value").getN()))
                            .withNumber("newValue", Integer.parseInt(newImage.get("value").getN()));
                }

                auditTable.putItem(auditItem);
            }
        }
        return "Done";
    }

    private Map<String, Object> toItemMap(Map<String, AttributeValue> image) {
        Map<String, Object> itemMap = new HashMap<>();
        for (Map.Entry<String, AttributeValue> entry : image.entrySet()) {
            AttributeValue value = entry.getValue();
            if (value.getS() != null) {
                itemMap.put(entry.getKey(), value.getS());
            } else if (value.getN() != null) {
                itemMap.put(entry.getKey(), Integer.parseInt(value.getN()));
            }
        }
        return itemMap;
    }
}
