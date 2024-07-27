package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import java.util.HashMap;
import java.util.Map;

@LambdaHandler(lambdaName = "hello_world",
        roleName = "hello_world-role",
        isPublishVersion = false,
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(
        authType = AuthType.NONE
)
public class HelloWorld implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
	@Override
	public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {
		LambdaLogger log = context.getLogger();
		APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");

		String path = getPath(request);
		String method = getMethod(request);
		log.log("Path: " + path);
		log.log("Method: " + method);

		if ("/hello".equals(path) && "GET".equals(method)) {
			response.setStatusCode(200);
			response.setHeaders(headers);
			response.setBody("{\"statusCode\": 200, \"message\": \"Hello from Lambda\"}");
		} else {
			response.setStatusCode(400);
			response.setHeaders(headers);
			response.setBody("{\"statusCode\": 400, \"message\": \"Bad request syntax or unsupported method. Request path: " + path + ". HTTP method: " + method + "\"}");
		}

		return response;
	}

	private String getMethod(APIGatewayV2HTTPEvent requestEvent) {
		return requestEvent.getRequestContext().getHttp().getMethod();
	}

	private String getPath(APIGatewayV2HTTPEvent requestEvent) {
		return requestEvent.getRequestContext().getHttp().getPath();
	}

//
//	public Map<String, Object> handleRequest(Object request, Context context) {
//		LambdaLogger log = context.getLogger();
//		Map<String, Object> response = new HashMap<>();
//
//		Map<String, Object> requestMap = (Map<String, Object>) request;
//		String path = (String) requestMap.get("path");
//		String method = (String) requestMap.get("httpMethod");
//		log.log("Path: " + path);
//		log.log("Method: " + method);
//
//		if ("/hello".equals(path) && "GET".equals(method)) {
//			response.put("statusCode", 200);
//			response.put("message", "Hello from Lambda");
//		} else {
//			response.put("statusCode", 400);
//			response.put("message", "Bad request syntax or unsupported method. Request path: " + path + ". HTTP method: " + method);
//		}
//		return response;
//	}
}
