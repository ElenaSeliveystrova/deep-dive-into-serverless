package com.task10;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ReservationService {
    private static final String TABLE_NAME = "cmtr-e288a3c1-Reservations";
    private final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public APIGatewayProxyResponseEvent handleCreateReservation(APIGatewayProxyRequestEvent requestEvent) {
        JSONObject json = new JSONObject(requestEvent.getBody());
        int tableNumber = json.getInt("tableNumber");
        String clientName = json.getString("clientName");
        String date = json.getString("date");
        String slotTimeStart = json.getString("slotTimeStart");
        String slotTimeEnd = json.getString("slotTimeEnd");

        String reservationId = UUID.randomUUID().toString();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(reservationId).build());
        item.put("tableNumber", AttributeValue.builder().n(Integer.toString(tableNumber)).build());
        item.put("clientName", AttributeValue.builder().s(clientName).build());
        item.put("date", AttributeValue.builder().s(date).build());
        item.put("slotTimeStart", AttributeValue.builder().s(slotTimeStart).build());
        item.put("slotTimeEnd", AttributeValue.builder().s(slotTimeEnd).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
        return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody(new JSONObject().put("reservationId", reservationId).toString());
    }

    public APIGatewayProxyResponseEvent handleGetReservations() {
        try {
            ScanRequest scanRequest = ScanRequest.builder().tableName(TABLE_NAME).build();
            ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

            List<Map<String, Object>> reservations = scanResponse.items().stream()
                    .map(item -> {
                        Map<String, Object> table = new HashMap<>();
                        table.put("tableNumber", item.get("tableNumber").s());
                        table.put("clientName", item.get("clientName").s());
                        table.put("date", item.get("date").s());
                        table.put("slotTimeStart", item.get("slotTimeStart").s());
                        table.put("slotTimeEnd", item.get("slotTimeEnd").s());
                        return table;
                    })
                    .collect(Collectors.toList());

            Map<String, List<Map<String, Object>>> responseBody = new HashMap<>();
            responseBody.put("reservations", reservations);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(objectMapper.writeValueAsString(responseBody));
        } catch (DynamoDbException | IOException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("Internal server error: " + e.getMessage());
        }

    }
}
