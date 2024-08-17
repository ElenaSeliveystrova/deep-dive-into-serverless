package com.task11;

import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.environment.ValueTransformer;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@LambdaHandler(
    	lambdaName = "api_handler",
		roleName = "api_handler-role",
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
		runtime = DeploymentRuntime.JAVA11
)
@DependsOn(resourceType = ResourceType.COGNITO_USER_POOL, name = "${booking_userpool}")
@EnvironmentVariables(value = {
		@EnvironmentVariable(key = "REGION", value = "${region}"),
		@EnvironmentVariable(key = "booking_userpool", value = "${booking_userpool}"),
		@EnvironmentVariable(key = "COGNITO_ID", value = "${booking_userpool}", valueTransformer = ValueTransformer.USER_POOL_NAME_TO_USER_POOL_ID),
		@EnvironmentVariable(key = "CLIENT_ID", value = "${booking_userpool}", valueTransformer = ValueTransformer.USER_POOL_NAME_TO_CLIENT_ID)
})
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private final UserService userService = new UserService();
	private final TableService tableService = new TableService();
	private final ReservationService reservationService = new ReservationService(tableService);


	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
		context.getLogger().log("request: " + requestEvent);
		context.getLogger().log("path: " + requestEvent.getPath());
		context.getLogger().log("method: " + requestEvent.getHttpMethod());
		String userPoolName = System.getenv("booking_userpool");
		context.getLogger().log("user pool: " + userPoolName);
		context.getLogger().log("user COGNITO_ID: " + System.getenv("${COGNITO_ID}"));
		context.getLogger().log("user CLIENT_ID: " + System.getenv("${CLIENT_ID}"));
		String path = requestEvent.getPath();
		String httpMethod = requestEvent.getHttpMethod();
		APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
		String tableId = "";
		if (requestEvent.getPathParameters() != null) {
			tableId = requestEvent.getPathParameters().get("tableId");
			path = path.replace(tableId,"");
		}

		switch (path) {
			case "/signup":
				if ("POST".equals(httpMethod)) {
					responseEvent = userService.handleSignup(requestEvent);
					return responseEvent;
				}
				break;
			case "/signin":
				if ("POST".equals(httpMethod)) {
					responseEvent = userService.handleSignin(requestEvent);
					context.getLogger().log("responseEvent: " + responseEvent.getBody());
					context.getLogger().log("responseEvent: " + responseEvent);
					return responseEvent;
				}
				break;
			case "/tables":
				if ("GET".equals(httpMethod)) {
					responseEvent = tableService.handleGetTables();
					context.getLogger().log("responseEvent: " + responseEvent.getBody());
				} else if ("POST".equals(httpMethod)) {
					responseEvent = tableService.handleCreateTable(requestEvent);
					context.getLogger().log("responseEvent: " + responseEvent.getBody());
				}
				break;
			case "/tables/":
				if ("GET".equals(httpMethod)) {
					return tableService.handleGetTableById(requestEvent.getPathParameters().get("tableId"), context);
				}
				break;
			case "/reservations":
				if ("POST".equals(httpMethod)) {
					return reservationService.handleCreateReservation(requestEvent, context);
				} else if ("GET".equals(httpMethod)) {
					return reservationService.handleGetReservations();
				}
				break;
			default:
				return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid request");
		}
		return responseEvent;
	}
}