package com.task07;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.EventSource;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.events.RuleEventSource;
import com.syndicate.deployment.annotations.events.RuleEvents;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.EventSourceType;
import com.syndicate.deployment.model.LambdaSnapStart;
import com.syndicate.deployment.model.RetentionSetting;

import java.time.Instant;
import java.util.*;

@LambdaHandler(
        lambdaName = "uuid_generator",
        roleName = "uuid_generator-role",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
        runtime = DeploymentRuntime.JAVA11,
        snapStart = LambdaSnapStart.PublishedVersions
)
@EventSource(
        eventType = EventSourceType.CLOUDWATCH_RULE_TRIGGER
)
@RuleEventSource(targetRule = "uuid_trigger")
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "region", value = "${region}"),
        @EnvironmentVariable(key = "target_bucket", value = "${target_bucket}")
}
)
public class UuidGenerator implements RequestHandler<Object, String> {
	private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
	private final ObjectMapper objectMapper = new ObjectMapper();
    public String handleRequest(Object input, Context context) {
        List<UUID> uuidList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            uuidList.add(UUID.randomUUID());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("ids", uuidList);

        String timestamp = Instant.now().toString();
        String fileName = timestamp + ".json";
        String bucketName = "cmtr-e288a3c1-uuid-storage";

        try {
            String jsonData = objectMapper.writeValueAsString(data);
            s3Client.putObject(bucketName, fileName, jsonData);
            return "Success";
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
