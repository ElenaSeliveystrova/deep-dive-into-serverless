package com.task10;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
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
import java.util.stream.Collectors;

public class TableService {
    private static final String TABLE_NAME = "cmtr-e288a3c1-Tables";
    private final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private final ObjectMapper objectMapper;

    public TableService() {
        this.objectMapper = new ObjectMapper();
    }

    public APIGatewayProxyResponseEvent handleGetTables(APIGatewayProxyRequestEvent requestEvent) {
        try {
            ScanRequest scanRequest = ScanRequest.builder().tableName("Tables").build();
            ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

            List<Map<String, Object>> tables = scanResponse.items().stream()
                    .map(item -> {
                        Map<String, Object> table = new HashMap<>();
                        table.put("id", Integer.parseInt(item.get("id").n()));
                        table.put("number", Integer.parseInt(item.get("number").n()));
                        table.put("places", Integer.parseInt(item.get("places").n()));
                        table.put("isVip", Boolean.parseBoolean(item.get("isVip").bool().toString()));
                        if (item.containsKey("minOrder")) {
                            table.put("minOrder", Integer.parseInt(item.get("minOrder").n()));
                        }
                        return table;
                    })
                    .collect(Collectors.toList());

            Map<String, List<Map<String, Object>>> responseBody = new HashMap<>();
            responseBody.put("tables", tables);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(objectMapper.writeValueAsString(responseBody));
        } catch (DynamoDbException | IOException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("Internal server error: " + e.getMessage());
        }
    }

    public APIGatewayProxyResponseEvent handleCreateTable(APIGatewayProxyRequestEvent requestEvent) {
        JSONObject json = new JSONObject(requestEvent.getBody());
        int id = json.getInt("id");
        int number = json.getInt("number");
        int places = json.getInt("places");
        boolean isVip = json.getBoolean("isVip");
        int minOrder = json.getInt("minOrder");


        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().n(Integer.toString(id)).build());
        item.put("number", AttributeValue.builder().n(Integer.toString(number)).build());
        item.put("places", AttributeValue.builder().n(Integer.toString(places)).build());
        item.put("isVip", AttributeValue.builder().bool(isVip).build());
        item.put("minOrder", AttributeValue.builder().n(Integer.toString(minOrder)).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
//        Item item = new Item()
//                .withPrimaryKey("id", id)
//                .withInt("number", number)
//                .withInt("places", places)
//                .withBoolean("isVip", isVip)
//                .withInt("minOrder", minOrder);
//
//        table.putItem(item);
        return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody(new JSONObject().put("id", id).toString());
    }
}
