package com.task10;

import com.amazonaws.services.lambda.runtime.Context;
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
    private static final String TABLE_NAME = "cmtr-e288a3c1-Reservations-test";
    private final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TableService tableService;

    final static class Reservation {
        private int tableNumber;
        private String clientName;
        private String date;
        private String slotTimeStart;
        private String slotTimeEnd;

        public Reservation(int tableNumber, String clientName, String date, String slotTimeStart, String slotTimeEnd) {
            this.tableNumber = tableNumber;
            this.clientName = clientName;
            this.date = date;
            this.slotTimeStart = slotTimeStart;
            this.slotTimeEnd = slotTimeEnd;
        }

        public int getTableNumber() {
            return tableNumber;
        }

        public void setTableNumber(int tableNumber) {
            this.tableNumber = tableNumber;
        }

        public String getClientName() {
            return clientName;
        }

        public void setClientName(String clientName) {
            this.clientName = clientName;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getSlotTimeStart() {
            return slotTimeStart;
        }

        public void setSlotTimeStart(String slotTimeStart) {
            this.slotTimeStart = slotTimeStart;
        }

        public String getSlotTimeEnd() {
            return slotTimeEnd;
        }

        public void setSlotTimeEnd(String slotTimeEnd) {
            this.slotTimeEnd = slotTimeEnd;
        }
    }


    public ReservationService(TableService tableService) {
        this.tableService = tableService;
    }

    public APIGatewayProxyResponseEvent handleCreateReservation(APIGatewayProxyRequestEvent requestEvent, Context context) {
        JSONObject json = new JSONObject(requestEvent.getBody());
        int tableNumber = json.getInt("tableNumber");

        if (!tableService.isTableExist(tableNumber)) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("No table found");
        }

        String clientName = json.getString("clientName");
        String date = json.getString("date");
        String slotTimeStart = json.getString("slotTimeStart");
        String slotTimeEnd = json.getString("slotTimeEnd");

        if (hasOverlapping(tableNumber, date, context)) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Overlapping");
        }

        if (!date.matches("^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$")) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid date format: " + date);
        }
        String regexpTime = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$";
        if (!slotTimeStart.matches(regexpTime) ||
                !slotTimeEnd.matches(regexpTime)) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid slotTime format: " + date);
        }

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

    private boolean hasOverlapping(int tableNumber, String date, Context context) {
        context.getLogger().log("table:" + tableNumber + " date:" + date);
        return getReservations().stream()
                .peek(reservation -> context.getLogger().log("reservation:" + reservation))
                .anyMatch(reservation ->
                        reservation.getTableNumber() == tableNumber
                                && reservation.getDate().equals(date));
    }

    public APIGatewayProxyResponseEvent handleGetReservations() {
        try {
            List<Reservation> reservations = getReservations();
            Map<String, List<Reservation>> responseBody = new HashMap<>();
            responseBody.put("reservations", reservations);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(objectMapper.writeValueAsString(responseBody));
        } catch (DynamoDbException | IOException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(500).withBody("Internal server error: " + e.getMessage());
        }

    }

    private List<Reservation> getReservations() {
        ScanRequest scanRequest = ScanRequest.builder().tableName(TABLE_NAME).build();
        ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

        return scanResponse.items().stream()
                .map(item -> {
                    return new Reservation(Integer.parseInt((String) item.get("tableNumber").n()),
                            item.get("clientName").s(),
                            item.get("date").s(),
                            item.get("slotTimeStart").s(),
                            item.get("slotTimeEnd").s());
                })
                .collect(Collectors.toList());

    }
}
