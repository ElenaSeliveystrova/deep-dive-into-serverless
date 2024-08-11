package com.task09;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.AWSXRayRecorderBuilder;
import com.amazonaws.xray.entities.Subsegment;
import com.amazonaws.xray.strategy.sampling.LocalizedSamplingStrategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.EventSource;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.*;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@LambdaHandler(
        lambdaName = "processor",
        roleName = "processor-role",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
        runtime = DeploymentRuntime.JAVA11,
        tracingMode = TracingMode.Active,
        snapStart = LambdaSnapStart.PublishedVersions
)
@LambdaUrlConfig(
        authType = AuthType.NONE,
        invokeMode = InvokeMode.BUFFERED
)
@EventSource(eventType = EventSourceType.DYNAMODB_TRIGGER)
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "region", value = "${region}"),
        @EnvironmentVariable(key = "table", value = "${target_table}")
}
)
public class Processor implements RequestHandler<Map<String, Object>, String> {

    private static final String WEATHER_API_URL = "https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41&current=temperature_2m,wind_speed_10m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m";
    private static final String DYNAMODB_TABLE_NAME = "cmtr-e288a3c1-Weather-test";

    private final OkHttpClient httpClient = new OkHttpClient();

    public Processor() {
        AWSXRayRecorderBuilder builder = AWSXRayRecorderBuilder.standard();
        URL ruleFile = Processor.class.getResource("/sampling-rules.json");

        builder.withSamplingStrategy(new LocalizedSamplingStrategy(ruleFile));
        AWSXRay.setGlobalRecorder(builder.build());

    }

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        String responseBody = null;
        Subsegment subsegment = AWSXRay.beginSubsegment("OpenMeteoAPIRequest");
        try {
            context.getLogger().log("Run function");
            responseBody = getWeatherData();
        } catch (Exception e) {
            subsegment.addException(e);
            throw e;
        } finally {
            AWSXRay.endSubsegment();
        }


        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode weatherData = null;
        try {
            weatherData = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        DynamoDbClient ddb = DynamoDbClient.create();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
        item.put("forecast", AttributeValue.builder().m(prepareDynamoDBForcastItem(weatherData)).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(DYNAMODB_TABLE_NAME)
                .item(item)
                .build();

        ddb.putItem(request);
        return "Weather data successfully stored in DynamoDB.";
    }

    private String getWeatherData() {
        okhttp3.Request request = new Request.Builder()
                .url(WEATHER_API_URL)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, AttributeValue> prepareDynamoDBForcastItem(JsonNode weatherData) {
        Map<String, AttributeValue> forecast = new HashMap<String, AttributeValue>();

        forecast.put("latitude", AttributeValue.builder().n(String.valueOf(weatherData.get("latitude").asDouble())).build());
        forecast.put("longitude", AttributeValue.builder().n(String.valueOf(weatherData.get("longitude").asDouble())).build());
        forecast.put("generationtime_ms", AttributeValue.builder().n(String.valueOf(weatherData.get("generationtime_ms").asDouble())).build());
        forecast.put("elevation", AttributeValue.builder().n(String.valueOf(weatherData.get("elevation").asDouble())).build());
        forecast.put("timezone", AttributeValue.builder().s(weatherData.get("timezone").asText()).build());
        forecast.put("timezone_abbreviation", AttributeValue.builder().s(weatherData.get("timezone_abbreviation").asText()).build());
        forecast.put("utc_offset_seconds", AttributeValue.builder().n(String.valueOf(weatherData.get("utc_offset_seconds").asInt())).build());

        // Add hourly temperature_2m and time arrays
        JsonNode hourly = weatherData.get("hourly");

        List<AttributeValue> temperature2m = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(hourly.get("temperature_2m").elements(), Spliterator.ORDERED),
                false).map(JsonNode::asText).map(s -> AttributeValue.builder().s(s).build()).collect(Collectors.toList());

        List<AttributeValue> time = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(hourly.get("time").elements(), Spliterator.ORDERED),
                false).map(JsonNode::asText).map(s -> AttributeValue.builder().s(s).build()).collect(Collectors.toList());

        forecast.put("hourly", AttributeValue.builder().m(Map.of(
                "temperature_2m", AttributeValue.builder().l(temperature2m).build(),
                "time", AttributeValue.builder().l(time).build())
        ).build());

        // Add hourly_units
        JsonNode hourlyUnits = weatherData.get("hourly_units");
        forecast.put("hourly_units", AttributeValue.builder().m(Map.of(
                "temperature_2m", AttributeValue.builder().s(hourlyUnits.get("temperature_2m").asText()).build(),
                "time", AttributeValue.builder().s(hourlyUnits.get("time").asText()).build()
        )).build());

        return forecast;
    }
}