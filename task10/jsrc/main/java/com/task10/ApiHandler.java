package com.task10;

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
		@EnvironmentVariable(key = "COGNITO_ID", value = "${booking_userpool}", valueTransformer = ValueTransformer.USER_POOL_NAME_TO_USER_POOL_ID),
		@EnvironmentVariable(key = "CLIENT_ID", value = "${booking_userpool}", valueTransformer = ValueTransformer.USER_POOL_NAME_TO_CLIENT_ID)
})
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private final UserService userService = new UserService();
	private final TableService tableService = new TableService();
//	private final ReservationService reservationService = new ReservationService();


	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent requestEvent, Context context) {
		context.getLogger().log("request: " + requestEvent);
		context.getLogger().log("method: " + requestEvent.getHttpMethod());
		context.getLogger().log("path: " + requestEvent.getPath());
		context.getLogger().log("body: " + requestEvent.getBody());
		context.getLogger().log("path: " + requestEvent.getPath());
		context.getLogger().log("method: " + requestEvent.getHttpMethod());
		String path = requestEvent.getPath();
		String httpMethod = requestEvent.getHttpMethod();
		APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
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
					responseEvent = tableService.handleGetTables(requestEvent);
					context.getLogger().log("responseEvent: " + responseEvent.getBody());
				} else if ("POST".equals(httpMethod)) {
					responseEvent = tableService.handleCreateTable(requestEvent);
					context.getLogger().log("responseEvent: " + responseEvent.getBody());
				}
				break;
//			case "/tables/{tableId}":
//				if ("GET".equals(method)) {
//					return tableService.handleGetTableById(request);
//				}
//				break;
//			case "/reservations":
//				if ("POST".equals(method)) {
//					return reservationService.handleCreateReservation(request);
//				} else if ("GET".equals(method)) {
//					return reservationService.handleGetReservations(request);
//				}
//				break;
			default:
				return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid request");
		}
		return responseEvent;
	}
}