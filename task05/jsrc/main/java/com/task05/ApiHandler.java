package com.task05;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;

import java.util.Map;
import java.util.UUID;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
        isPublishVersion = false,
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "region", value = "${region}"),
        @EnvironmentVariable(key = "table", value = "${target_table}")
	}
)

public class ApiHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	private final DynamoDB dynamoDB;
	private final Table table;
	private final ObjectMapper objectMapper;

	public ApiHandler() {
		this.dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient());
		this.table = dynamoDB.getTable("cmtr-e288a3c1-Events");
		this.objectMapper = new ObjectMapper();
	}

	public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
		try {
			// Extract request data
			int principalId = (int) input.get("principalId");
			Map<String, String> content = (Map<String, String>) input.get("content");

			// Create event data
			String id = UUID.randomUUID().toString();
			String createdAt = java.time.Instant.now().toString();

			Item item = new Item()
					.withPrimaryKey("id", id)
					.withInt("principalId", principalId)
					.withString("createdAt", createdAt)
					.withMap("body", content);

			// Save to DynamoDB
			table.putItem(item);

			// Prepare response
			Map<String, Object> response = Map.of(
					"statusCode", 201,
					"event", item.asMap()
			);

			return response;

		} catch (Exception e) {
			context.getLogger().log("Error: " + e.getMessage());
			return Map.of("statusCode", 500, "error", e.getMessage());
		}
    }
}
