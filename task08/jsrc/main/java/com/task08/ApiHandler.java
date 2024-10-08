package com.task08;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.Architecture;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import org.example.WeatherClient;


@LambdaHandler(
    	lambdaName = "api_handler",
		roleName = "api_handler-role",
		layers = {"open-meteo-sdk"},
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED

)
@LambdaLayer(
		layerName = "open-meteo-sdk",
		libraries = {"lib/meteo-sdk-1.0-SNAPSHOT.jar"},
		runtime = DeploymentRuntime.JAVA11,
		architectures = {Architecture.ARM64},
		artifactExtension = ArtifactExtension.ZIP
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
public class ApiHandler implements RequestHandler<Object, String> {

	public String handleRequest(Object input, Context context) {
		WeatherClient weatherClient = new WeatherClient();
		try {
			JsonNode forecast = weatherClient.getWeatherForecast();
			return forecast.toString();
		} catch (Exception e) {
			return "Error: " + e.getMessage();
		}
	}
}
