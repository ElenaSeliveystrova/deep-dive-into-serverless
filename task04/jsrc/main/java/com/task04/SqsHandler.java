package com.task04;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.events.SqsTriggerEventSource;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.annotations.events.FunctionResponseType;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@LambdaHandler(
    lambdaName = "sqs_handler",
	roleName = "sqs_handler-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)

@SqsTriggerEventSource(
		targetQueue = "async_queue",
		batchSize = 10,
		functionResponseTypes = FunctionResponseType.REPORT_BATCH_ITEM_FAILURES
)
public class SqsHandler implements RequestHandler<SQSEvent, Map<String, Object>> {

	@Override
	public Map<String, Object> handleRequest(SQSEvent event, Context context) {
		List<SQSEvent.SQSMessage> messages = event.getRecords();
		for (SQSEvent.SQSMessage msg : messages) {
			context.getLogger().log("Received message: " + msg.getBody());
		}
		Map<String, Object> resultMap = new HashMap<String, Object>();
		resultMap.put("statusCode", 200);
		resultMap.put("message", "Hello from Lambda");
		return resultMap;
	}
}
