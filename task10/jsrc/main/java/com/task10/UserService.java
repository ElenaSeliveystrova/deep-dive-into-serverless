package com.task10;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.*;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;
import java.util.Map;

public class UserService {

    private final AWSCognitoIdentityProvider cognitoClient = AWSCognitoIdentityProviderClientBuilder.defaultClient();
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
            if (!json.getString("password").matches("^(?=.*[0-9])(?=.*[a-zA-Z])(?=.*[$%^*])[a-zA-Z0-9$%^*]{8,}$")) {
                return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Password does not meet complexity requirements");
            }

            AdminCreateUserRequest createUserRequest = new AdminCreateUserRequest()
                    .withUserPoolId(userPoolId)
                    .withUsername(email)
                    .withUserAttributes(
                            new AttributeType().withName("given_name").withValue(firstName),
                            new AttributeType().withName("family_name").withValue(lastName),
                            new AttributeType().withName("email").withValue(email)
                    )
                    .withTemporaryPassword(password);

            cognitoClient.adminCreateUser(createUserRequest);

            AdminSetUserPasswordRequest setUserPasswordRequest = new AdminSetUserPasswordRequest()
                    .withUserPoolId(userPoolId)
                    .withUsername(email)
                    .withPassword(password)
                    .withPermanent(true);

            cognitoClient.adminSetUserPassword(setUserPasswordRequest);

            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody("Sign-up successful");

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

            AdminInitiateAuthRequest authRequest = new AdminInitiateAuthRequest()
                    .withAuthFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                    .withUserPoolId(userPoolId)
                    .withClientId(clientId)//
                    .withAuthParameters(Map.of(
                            "USERNAME", email,
                            "PASSWORD", password
                    ));

            AdminInitiateAuthResult authResult = cognitoClient.adminInitiateAuth(authRequest);
            String accessToken = authResult.getAuthenticationResult().getIdToken();

            return new APIGatewayProxyResponseEvent().withStatusCode(200).withBody(new JSONObject().put("accessToken", accessToken).toString());

        } catch (NotAuthorizedException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Invalid credentials: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("User not found: " + e.getMessage());
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("Error during sign-in: " + e.getMessage());
        }
    }
}
