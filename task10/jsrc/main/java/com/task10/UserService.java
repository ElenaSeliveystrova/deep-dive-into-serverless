package com.task10;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Map;

public class UserService {

    private final CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder().build();
    private final String userPoolId = System.getenv("COGNITO_ID");
    private final String clientId = System.getenv("CLIENT_ID");//

    public APIGatewayProxyResponseEvent handleSignup(APIGatewayProxyRequestEvent request) {
        try {
            JSONObject json = new JSONObject(request.getBody());
            String firstName = json.getString("firstName");
            String lastName = json.getString("lastName");
            String email = json.getString("email");
            String password = json.getString("password");

            if (!json.getString("email").matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
                return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid email format");
            }
            if (!json.getString("password").matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[\\$%\\^_\\-\\*])[A-Za-z\\d\\$%\\^_\\-\\*]{12,}$")) {
                return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Password does not meet complexity requirements");
            }

            AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .userAttributes(
                            AttributeType.builder().name("given_name").value(firstName).build(),
                            AttributeType.builder().name("family_name").value(lastName).build(),
                            AttributeType.builder().name("email").value(email).build()
                    )
                    .temporaryPassword(password)
                    .build();
            cognitoClient.adminCreateUser(createUserRequest);

            AdminSetUserPasswordRequest setUserPasswordRequest = AdminSetUserPasswordRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .password(password)
                    .permanent(true)
                    .build();
            cognitoClient.adminSetUserPassword(setUserPasswordRequest);
            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody(new JSONObject()
                    .put("message", "User has been successfully signed up.")
                    .toString());

//            AdminCreateUserResponse adminCreateUserResponse = cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
//                    .userPoolId(userPoolId)
//                    .username(email)
//                    .temporaryPassword(password)
//                    .userAttributes(
//                            AttributeType.builder()
//                                    .name("given_name")
//                                    .value(firstName)
//                                    .build(),
//                            AttributeType.builder()
//                                    .name("family_name")
//                                    .value(lastName)
//                                    .build(),
//                            AttributeType.builder()
//                                    .name("email")
//                                    .value(email)
//                                    .build(),
//                            AttributeType.builder()
//                                    .name("email_verified")
//                                    .value("true")
//                                    .build()
//                            )
//                    .desiredDeliveryMediums(DeliveryMediumType.EMAIL)
//                    .messageAction("SUPPRESS")
//                    .forceAliasCreation(Boolean.FALSE)
//                    .build()
//            );
//            String userId = adminCreateUserResponse.user().attributes().stream()
//                    .filter(attr -> attr.name().equals("sub"))
//                    .map(AttributeType::value)
//                    .findAny()
//                    .orElseThrow(() -> new RuntimeException("Sub not found."));
//
//            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody(new JSONObject()
//                    .put("message", "User has been successfully signed up.")
//                    .put("userId", userId)
//                    .toString());

        } catch (UsernameExistsException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("User already exists: " + e.getMessage());
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Error during sign-up: " + e.getMessage());
        }
    }

    public APIGatewayProxyResponseEvent handleSignin(APIGatewayProxyRequestEvent request) {
        try {
            JSONObject json = new JSONObject(request.getBody());
            String email = json.getString("email");
            String password = json.getString("password");

            AdminInitiateAuthRequest authRequest = AdminInitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                    .userPoolId(userPoolId)
                    .clientId(clientId)//
                    .authParameters(Map.of(
                            "USERNAME", email,
                            "PASSWORD", password
                    ))
                    .build();

            AdminInitiateAuthResponse authResult = cognitoClient.adminInitiateAuth(authRequest);
            String idToken = authResult.authenticationResult().idToken();

            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody(new JSONObject().put("idToken", idToken).toString());

        } catch (NotAuthorizedException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid credentials: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("User not found: " + e.getMessage());
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Error during sign-in: " + e.getMessage());
        }
    }
}
