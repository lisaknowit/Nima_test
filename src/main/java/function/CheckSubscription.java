package function;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.RandomStringUtils;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import model.OAuthTokenResponse;
import model.eonswitch.subscription.ObjectOperation;
import model.eonswitch.subscription.ProgramResponse;
import model.eonswitch.subscription.VTNSubscription;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;

public class CheckSubscription implements RequestHandler<Object, APIGatewayProxyResponseEvent> {
    private static final Gson gson = new Gson();
    private static final HttpClient client = HttpClient.newHttpClient();

    private OAuthTokenResponse tokenResponse;
    private OAuthTokenResponse QA_tokenResponse; 
    private SsmClient ssmClient;

    private int statusCode;
    
    //This is the name for the subscription created in Switch platform. And api calls with this in path, are NOT case sensitive.
    private static final String CLIENT_NAME_FOR_SUBSCRIPTION = "Nima-powerlimit"; 
    private static final String QA_CLIENT_NAME_FOR_SUBSCRIPTION = "Nima-powerlimit-QA"; 

    private final String PROD = "PROD";
    private final String QA = "QA";

    
    // private static final String API_GATEWAY_URL = "apiGatewayUrl";
    // private static final String QA_API_GATEWAY_URL= "QA_apiGatewayUrl";
    
    private String API_URL = "https://nima.knowitcloud.se/prod/prod/powerlimit"; //TODO Change this if not using custom domain;
    private String QA_API_URL = "https://nima.knowitcloud.se/prod/qa/powerlimit"; //TODO Change this if not using custom domain;

    private Map<String, String> parameters = new HashMap<>(); //prod values from parameter store
    private Map<String, String> QA_parameters = new HashMap<>(); //QA values from parameter store

    //Names of stored values in Parameter store
    private static final String CLIENT_ID = "/Switch/ClientID";
    private static final String CLIENT_SECRET = "/Switch/ClientSecret";
    private static final String GRANT_TYPE =  "/Switch/GrantType";
    private static final String OAUTH_TOKEN_URL = "/Switch/OAuthTokenURL";
    private static final String SCOPE_URL = "/Switch/ScopeURL";
    private static final String SWITCH_BASE_URL = "/Switch/SwitchBaseURL"; 
    private static final String BEARER_TOKEN = "/BearerToken";

    private static final String QA_CLIENT_ID = "/QA/Switch/ClientID";
    private static final String QA_CLIENT_SECRET = "/QA/Switch/ClientSecret";
    private static final String QA_GRANT_TYPE =  "/QA/Switch/GrantType";
    private static final String QA_OAUTH_TOKEN_URL = "/QA/Switch/OAuthTokenURL";
    private static final String QA_SCOPE_URL = "/QA/Switch/ScopeURL";
    private static final String QA_SWITCH_BASE_URL = "/QA/Switch/SwitchBaseURL"; 
    private static final String QA_BEARER_TOKEN = "/QA/BearerToken";

    /**
     * Checks that powerlimit subscription is OK
     * Triggered by AWS Eventbridge Timer
     * 
     * @param input
     * @param context
     * @return APIGatewayProxyResponseEvent
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(Object input, Context context) {
        try {
            setPrerequisites();
            updateOAuthToken_PROD();
            updateOAuthToken_QA();
            VTNSubscription vtnSubscription = getPowerLimitSubscription_PROD();
            VTNSubscription QA_vtnSubscription = getPowerLimitSubscription_QA();

            //Handles PROD environment
            if (vtnSubscription == null) {
                createVTNSubscription(CLIENT_NAME_FOR_SUBSCRIPTION, tokenResponse, parameters.get(SWITCH_BASE_URL), API_URL, PROD);
            }
            //Handles QA environment
            if (QA_vtnSubscription == null) {
                createVTNSubscription(QA_CLIENT_NAME_FOR_SUBSCRIPTION, QA_tokenResponse, QA_parameters.get(QA_SWITCH_BASE_URL), QA_API_URL, QA);
                                
            }
            //Handles PROD environment
            if (vtnSubscription != null && !vtnSubscription.getObjectOperations().isEmpty()) {
                if (shouldCallbackUrlBeUpdated(vtnSubscription, API_URL)) {
                    updateCallbackURL(vtnSubscription, tokenResponse, parameters.get(SWITCH_BASE_URL), API_URL, PROD);
                }
                if (shouldBearerTokenBeUpdated(vtnSubscription, parameters.get(BEARER_TOKEN))) {
                    updateBearerToken(vtnSubscription, parameters.get(BEARER_TOKEN), tokenResponse, parameters.get(SWITCH_BASE_URL), PROD);
                }
                if (vtnSubscription.getObjectOperations().get(0).getObjects().isEmpty() ||
                        vtnSubscription.getObjectOperations().get(0).getOperations().isEmpty()) {
                    updateObjectsAndOperations(vtnSubscription, tokenResponse, parameters.get(SWITCH_BASE_URL), PROD);
                }

            }
            //Handles QA environment
            if (QA_vtnSubscription != null && !QA_vtnSubscription.getObjectOperations().isEmpty()) {
                if (shouldCallbackUrlBeUpdated(QA_vtnSubscription, QA_API_URL)) {
                    updateCallbackURL(QA_vtnSubscription, QA_tokenResponse, QA_parameters.get(QA_SWITCH_BASE_URL), QA_API_URL, QA);
                }
                if (shouldBearerTokenBeUpdated(QA_vtnSubscription, QA_parameters.get(QA_BEARER_TOKEN))) {
                    updateBearerToken(QA_vtnSubscription, QA_parameters.get(QA_BEARER_TOKEN), QA_tokenResponse, parameters.get(SWITCH_BASE_URL), QA);
                }
                if (QA_vtnSubscription.getObjectOperations().get(0).getObjects().isEmpty() ||
                        QA_vtnSubscription.getObjectOperations().get(0).getOperations().isEmpty()) {
                    updateObjectsAndOperations(QA_vtnSubscription, QA_tokenResponse, parameters.get(SWITCH_BASE_URL), QA);
                }

            }
        } catch (Exception e) {
            System.out.println("[ALARM TRIGGER Subscription Error]");
            e.printStackTrace();
            return new APIGatewayProxyResponseEvent().withStatusCode(400)
                    .withBody("CheckSubscription function has failed");
        }
        return new APIGatewayProxyResponseEvent().withStatusCode(statusCode)
                .withBody("CheckSubscription function is OK");
    }

    /**
     * Handles prerequisites
     * gets apiURL and bearerToken from AWS System Manager - Parameter Store
     */
    private void setPrerequisites() {
        ssmClient = SsmClient.builder()
                .region(DefaultAwsRegionProviderChain.builder().build().getRegion())
                .build();

        GetParametersRequest request = GetParametersRequest.builder()
                .names(CLIENT_ID,
                        CLIENT_SECRET,
                        GRANT_TYPE,
                        OAUTH_TOKEN_URL,
                        SCOPE_URL,
                        SWITCH_BASE_URL,
                        BEARER_TOKEN)
                .withDecryption(true)
                .build();
        GetParametersRequest requestQA = GetParametersRequest.builder()
                .names(QA_CLIENT_ID,
                        QA_CLIENT_SECRET,
                        QA_GRANT_TYPE,
                        QA_OAUTH_TOKEN_URL,
                        QA_SCOPE_URL,
                        QA_SWITCH_BASE_URL,
                        QA_BEARER_TOKEN)
                .withDecryption(true)
                .build();

        GetParametersResponse response = ssmClient.getParameters(request);
        GetParametersResponse QA_response = ssmClient.getParameters(requestQA);

        for (Parameter parameter : response.parameters()) {
            parameters.put(parameter.name(), parameter.value());
        }
        for (Parameter parameter : QA_response.parameters()) {
            QA_parameters.put(parameter.name(), parameter.value());
        }

        if (parameters.get(BEARER_TOKEN).equals("initial value")) {
            parameters.put(BEARER_TOKEN, generateBearerTokenForSubscription());
            putBearerTokenInParameterStore("PROD", parameters.get(BEARER_TOKEN));
        }
        if (QA_parameters.get(QA_BEARER_TOKEN).equals("initial value")) {
            QA_parameters.put(QA_BEARER_TOKEN, generateBearerTokenForSubscription());
            putBearerTokenInParameterStore("QA", QA_parameters.get(QA_BEARER_TOKEN));
        }
    }

    /**
     * Will always update Oauth token regardless of whether it has expired or not
     * 
     * @throws Exception
     */
    private void updateOAuthToken_PROD() throws Exception {
        try {
            Map<String, String> formData = new HashMap<>();
            formData.put("grant_type", parameters.get(GRANT_TYPE));
            formData.put("client_id", parameters.get(CLIENT_ID));
            formData.put("client_secret", parameters.get(CLIENT_SECRET));
            formData.put("scope", parameters.get(SCOPE_URL));

            HttpRequest request = HttpRequest.newBuilder(new URI(parameters.get(OAUTH_TOKEN_URL)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(formData)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();

            OAuthTokenResponse oAuthTokenResponse = gson.fromJson(response.body(), OAuthTokenResponse.class);
            tokenResponse = oAuthTokenResponse;

        } catch (Exception e) {
            throw new Exception("[Switch-PROD][ALARM TRIGGER Subscription Error] Could not get OAuthToken: " + e);
        }
    }
/**
     * Will always update Oauth token regardless of whether it has expired or not
     * 
     * @throws Exception
     */
    private void updateOAuthToken_QA() throws Exception {
        try {
            Map<String, String> formData = new HashMap<>();
            formData.put("grant_type", QA_parameters.get(QA_GRANT_TYPE));
            formData.put("client_id", QA_parameters.get(QA_CLIENT_ID));
            formData.put("client_secret", QA_parameters.get(QA_CLIENT_SECRET));
            formData.put("scope",QA_parameters.get(QA_SCOPE_URL));

            HttpRequest request = HttpRequest.newBuilder(new URI(QA_parameters.get(QA_OAUTH_TOKEN_URL)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(formData)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();

            OAuthTokenResponse oAuthTokenResponse = gson.fromJson(response.body(), OAuthTokenResponse.class);
            QA_tokenResponse = oAuthTokenResponse;

        } catch (Exception e) {
            throw new Exception("[Switch-QA] Could not get OAuthToken from: " + e);
        }
    }

    /**
     * Get powerlimit subscription based on @param clientName, which are not
     * case-sensitive
     * 
     * @return VTNSubscription
     * @throws Exception
     */
    private VTNSubscription getPowerLimitSubscription_PROD() throws Exception {
        try {
            HttpRequest request = HttpRequest
                    .newBuilder(URI.create(parameters.get(SWITCH_BASE_URL) + "subscriptions?clientName="
                            + CLIENT_NAME_FOR_SUBSCRIPTION))
                    .header("Authorization", "Bearer " + tokenResponse.getAccess_token())
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
            if (statusCode == 200) {
                if (!jsonArray.isEmpty()) {
                    if (jsonArray.size() > 1) {
                        throw new Exception("[Switch-PROD][ALARM TRIGGER Subscription Error] There is more than one VTN subscription with the Client name = "
                                + CLIENT_NAME_FOR_SUBSCRIPTION);
                    }
                    VTNSubscription vtnSubscription = gson.fromJson(jsonArray.get(0), VTNSubscription.class);
                    if (vtnSubscription.getObjectOperations().isEmpty()) {
                        throw new Exception("[Switch][ALARM TRIGGER Subscription Error] There is a subscription but NO WEBHOOK connected");
                    }
                    if (vtnSubscription.getObjectOperations().size() > 1) {
                        throw new Exception(
                                "[Switch-PROD][ALARM TRIGGER Subscription Error] There are more than one Webhook within the subscription with Client name = "
                                        + CLIENT_NAME_FOR_SUBSCRIPTION);
                    }
                    System.out.println("[Switch-PROD] There is an existing subscription with Client name = "
                            + CLIENT_NAME_FOR_SUBSCRIPTION);
                    return vtnSubscription;
                } else {
                    System.out.println(
                            "[Switch-PROD][ALARM TRIGGER Subscription Error] There are no subscription with Client name = " + CLIENT_NAME_FOR_SUBSCRIPTION);
                    return null;
                }
            } else {
                throw new Exception("[Switch-PROD][ALARM TRIGGER Subscription Error] Could not get Subscription correctly: " + response.body());
            }
        } catch (Exception e) {
            throw e;
        }
    }   
    
    /**
     * Get powerlimit subscription based on @param clientName, which are not
     * case-sensitive
     * 
     * @return VTNSubscription
     * @throws Exception
     */
    private VTNSubscription getPowerLimitSubscription_QA() throws Exception {
        try {
            HttpRequest request = HttpRequest
                    .newBuilder(URI.create(QA_parameters.get(QA_SWITCH_BASE_URL) + "subscriptions?clientName="
                            + QA_CLIENT_NAME_FOR_SUBSCRIPTION))
                    .header("Authorization", "Bearer " + QA_tokenResponse.getAccess_token())
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
            if (statusCode == 200) {
                if (!jsonArray.isEmpty()) {
                    if (jsonArray.size() > 1) {
                        throw new Exception("[Switch-QA] There is more than one VTN subscription with the Client name = "
                                + QA_CLIENT_NAME_FOR_SUBSCRIPTION);
                    }
                    VTNSubscription QA_vtnSubscription = gson.fromJson(jsonArray.get(0), VTNSubscription.class);
                    if (QA_vtnSubscription.getObjectOperations().isEmpty()) {
                        throw new Exception("[Switch-QA] There is a subscription but NO WEBHOOK connected");
                    }
                    if (QA_vtnSubscription.getObjectOperations().size() > 1) {
                        throw new Exception(
                                "[Switch-QA] There are more than one Webhook within the subscription with Client name = "
                                        + QA_CLIENT_NAME_FOR_SUBSCRIPTION);
                    }
                    System.out.println("[Switch-QA] There is an existing subscription with Client name = "
                            + QA_CLIENT_NAME_FOR_SUBSCRIPTION);
                    return QA_vtnSubscription;
                } else {
                    System.out.println(
                            "[Switch-QA] There are no subscription with Client name = " + QA_CLIENT_NAME_FOR_SUBSCRIPTION);
                    return null;
                }
            } else {
                throw new Exception("[Switch-QA]  Could not get Subscription correctly: " + response.body());
            }
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Creates new VTN subscription with clientName = CLIENT_NAME_FOR_SUBSCRIPTION
     * 
     * @throws Exception
     */
    private void createVTNSubscription(String client_name, OAuthTokenResponse oAuthTokenResponse, String switchBaseURL, String apiURL, String environmentRequester) throws Exception {
        String bearerToken =  generateBearerTokenForSubscription();
        ObjectOperation objectOperation = new ObjectOperation();
        objectOperation.setObjects(new ArrayList<>(List.of("EVENT")));
        objectOperation.setOperations(new ArrayList<>(List.of("POST")));
        objectOperation.setBearerToken(bearerToken);
        objectOperation.setCallbackUrl(apiURL);

        try {
            int programID = getProgramID(oAuthTokenResponse, switchBaseURL, environmentRequester);
            if (programID > -1) {
                VTNSubscription vtnSubscription = VTNSubscription.builder().clientName(client_name)
                        .programID(String.valueOf(programID))
                        .objectOperations(new ArrayList<>(List.of(objectOperation)))
                        .build();

                HttpRequest request = HttpRequest
                        .newBuilder(new URI(switchBaseURL + "subscriptions"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + oAuthTokenResponse.getAccess_token())
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(vtnSubscription, VTNSubscription.class)))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                statusCode = response.statusCode();
                if (statusCode != 201) {
                    throw new Exception("[Switch-"+environmentRequester+"][ALARM TRIGGER Subscription Error] " +
                        vtnSubscription.getClientName() + "->  Could not create VTN subscription: " + response.body());
                }
                putBearerTokenInParameterStore(environmentRequester, bearerToken);
                System.out.println("[Switch-"+environmentRequester+"] Created new VTN subscription with Client name = " + client_name);
            }
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Updates callback URL for @param vtnSubscription
     * 
     * @throws Exception
     */
    private void updateCallbackURL(VTNSubscription vtnSubscription, OAuthTokenResponse oAuthTokenResponse, String switchBaseURL, String apiURL, String environmentRequester) throws Exception {
        try {
            vtnSubscription.getObjectOperations().get(0)
                    .setCallbackUrl(apiURL);
            HttpRequest request = HttpRequest
                    .newBuilder(URI.create(switchBaseURL + "subscriptions/" + vtnSubscription.getId()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + oAuthTokenResponse.getAccess_token())
                    .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(vtnSubscription, VTNSubscription.class)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            if (statusCode != 200) {
                throw new Exception("[Switch-"+environmentRequester+"]][ALARM TRIGGER Subscription Error] " +
                    vtnSubscription.getClientName() + 
                        "->  updateCallbackURL() has failed due to: " + response.body());
            }
            System.out.println("[Switch-"+environmentRequester+"] Updated Callback URL successfully for " + vtnSubscription.getClientName());

        } catch (Exception e) {
            throw e;
        }

    }

    /**
     * Updates bearerToken for @param vtnSubscription
     * Follow bearerToken stored in AWS - Parameter Store since WebhookTokenAuthorizer gets value from it.
     * 
     * @throws Exception
     */
    private void updateBearerToken(VTNSubscription vtnSubscription, String bearerToken, OAuthTokenResponse oAuthTokenResponse, String switchBaseURL, String environmentRequester) throws Exception {
        try {
            vtnSubscription.getObjectOperations().get(0)
                    .setBearerToken(bearerToken);
            HttpRequest request = HttpRequest
                    .newBuilder(URI.create(switchBaseURL + "subscriptions/" + vtnSubscription.getId()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + oAuthTokenResponse.getAccess_token())
                    .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(vtnSubscription, VTNSubscription.class)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            if (statusCode != 200) {
                throw new Exception("[Switch-"+environmentRequester+"][ALARM TRIGGER Subscription Error] " +
                    vtnSubscription.getClientName() + 
                        "->  updateBearerToken() has failed due to: " + response.body());
            }
            System.out.println("[Switch-"+environmentRequester+"] Updated Bearer Token successfully for " + vtnSubscription.getClientName());
        } catch (Exception e) {
            throw e;
        }

    }

    /**
     * Updates callback URL for @param vtnSubscription
     * 
     * @throws Exception
     */
    private void updateObjectsAndOperations(VTNSubscription vtnSubscription, OAuthTokenResponse oAuthTokenResponse, String switchBaseURL, String environmentRequester) throws Exception {
        try {
            vtnSubscription.getObjectOperations().get(0)
                    .setObjects(new ArrayList<>(List.of("EVENT")));
            vtnSubscription.getObjectOperations().get(0)        
                    .setOperations(new ArrayList<>(List.of("POST")));
                    System.out.println(vtnSubscription);
            HttpRequest request = HttpRequest
                    .newBuilder(URI.create(switchBaseURL + "subscriptions/" + vtnSubscription.getId()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + oAuthTokenResponse.getAccess_token())
                    .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(vtnSubscription, VTNSubscription.class)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            if (statusCode != 200) {
                throw new Exception("[Switch-"+environmentRequester+"][ALARM TRIGGER Subscription Error] " +
                    vtnSubscription.getClientName() + 
                        "->  updateObjectsAndOperations() has failed due to: " + response.body());
            }
            System.out.println("[Switch-"+environmentRequester+"] Updated Objects And Operations successfully for " + vtnSubscription.getClientName());

        } catch (Exception e) {
            throw e;
        }

    }

    /**
     * Get programID, which is needed to create VTN subscription
     * 
     * @see createVTNSubscription()
     * 
     * @return int
     */
    private int getProgramID(OAuthTokenResponse oAuthTokenResponse, String switchBaseURL, String environmentRequester) throws Exception {
        try {

            HttpRequest request = HttpRequest
                    .newBuilder(URI.create(switchBaseURL + "programs"))
                    .header("Authorization", "Bearer " + oAuthTokenResponse.getAccess_token())
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            statusCode = response.statusCode();
            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
            ProgramResponse programResponse = jsonArray.isEmpty() ? null
                    : gson.fromJson(jsonArray.get(0), ProgramResponse.class);
            return programResponse != null ? programResponse.getId() : -1;
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("[Switch-"+environmentRequester+"][ALARM TRIGGER Subscription Error] Could not get ProgramID");
        }
    }

    /**
     * @return String
     */
    private String generateBearerTokenForSubscription() {
        System.out.println("[Nima] Generate new Bearer Token");
        return RandomStringUtils.random(21, 0, 0, true, true,
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray(), new java.util.Random());
    }

    /**
     * Will put BearerToken in AWS System Manager - Parameter Store
     * 
     * @param BearerToken
     */
    private void putBearerTokenInParameterStore(String environment, String BearerToken) {
        if (environment.equals("QA")) {
            PutParameterRequest putParameterRequest = PutParameterRequest.builder()
                    .overwrite(true)
                    .name(QA_BEARER_TOKEN)
                    .value(BearerToken)
                    .type(ParameterType.SECURE_STRING)
                    .keyId("alias/NimaKey")
                    .build();
            ssmClient.putParameter(putParameterRequest);
        } else {
            PutParameterRequest putParameterRequest = PutParameterRequest.builder()
                    .overwrite(true)
                    .name(BEARER_TOKEN)
                    .value(BearerToken)
                    .type(ParameterType.SECURE_STRING)
                    .keyId("alias/NimaKey")
                    .build();
            ssmClient.putParameter(putParameterRequest);
        }
    }

    /**
     * @param vtnSubscription
     * @return boolean
     */
    private boolean shouldCallbackUrlBeUpdated(VTNSubscription vtnSubscription, String apiURL) {
        return vtnSubscription.getObjectOperations().get(0).getCallbackUrl() == null
                || !vtnSubscription.getObjectOperations().get(0).getCallbackUrl().equals(apiURL);
    }

    /**
     * @param vtnSubscription
     * @return boolean
     */
    private boolean shouldBearerTokenBeUpdated(VTNSubscription vtnSubscription, String bearerToken) {
        return vtnSubscription.getObjectOperations().get(0).getBearerToken() == null
                || !vtnSubscription.getObjectOperations().get(0).getBearerToken().equals(bearerToken);
    }

    /**
     * @param formData
     * @return String
     */
    private String getFormDataAsString(Map<String, String> formData) {
        StringBuilder formBodyBuilder = new StringBuilder();
        for (Map.Entry<String, String> singleEntry : formData.entrySet()) {
            if (formBodyBuilder.length() > 0) {
                formBodyBuilder.append("&");
            }
            formBodyBuilder.append(URLEncoder.encode(singleEntry.getKey(), StandardCharsets.UTF_8));
            formBodyBuilder.append("=");
            formBodyBuilder.append(URLEncoder.encode(singleEntry.getValue(), StandardCharsets.UTF_8));
        }
        return formBodyBuilder.toString();
    }
}
