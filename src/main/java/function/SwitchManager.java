package function;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.net.HttpURLConnection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import dto.PowerLimitEvent;
import lombok.Builder;
import model.OAuthTokenResponse;
import model.eonswitch.EventAckReport;
import model.eonswitch.Interval;
import model.eonswitch.IntervalPeriod;
import model.eonswitch.Payload;
import model.eonswitch.PayloadDescriptor;
import model.eonswitch.Resource;

@Builder
public class SwitchManager {
    private final HttpClient client = HttpClient.newHttpClient();

    @Builder.Default
    private long tokenExpirationTime = 0;
    private OAuthTokenResponse cached_oauth_token_response;

    private final String ACKNOWLEDGEMENT_TYPE = "POWER_LIMIT_ACKNOWLEDGEMENT";
    // Name to be used with the switch API, for debugging purposes only
    private final String SWITCH_CLIENT_NAME = "Nima_switch_client";

    final static String POWER_LIMIT_ACKNOWLEDGEMENT = "POWER_LIMIT_ACKNOWLEDGEMENT";
    final HashMap<String, Boolean> acknowledgedPLEvents = new HashMap<String, Boolean>();

    public void sendAcknowledgementReport(PowerLimitEvent powerLimitEvent, String switchBaseURL,
            String grantType, String clientId, String clientSecret, String scopeURL, String OAuthTokenURL, String environmentRequester) {

        getOAuthToken(grantType, clientId, clientSecret, scopeURL, OAuthTokenURL);

        EventAckReport eventAckReport = getEventAckReport(powerLimitEvent);
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String eventAckReportjson = "";
        try {
            eventAckReportjson = ow.writeValueAsString(eventAckReport);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        System.out.println("[Switch-"+ environmentRequester +"] event report json : " + eventAckReportjson);
        try {
            HttpRequest reportRequest = HttpRequest.newBuilder()
                .uri(new URI(switchBaseURL + "/reports"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cached_oauth_token_response.getAccess_token())
                .POST(HttpRequest.BodyPublishers.ofString(eventAckReportjson))
                .build();
            HttpResponse<String> reporHttpResponse = client.send(reportRequest,
            HttpResponse.BodyHandlers.ofString());
            System.out.println("[Switch-"+ environmentRequester +"] Acknowledgement report sent: " + reporHttpResponse.statusCode());
            if (reporHttpResponse.statusCode() == HttpURLConnection.HTTP_CREATED) {
            } else {
                String message = "the response for the create report request was " + reporHttpResponse.statusCode() +
                "\nThe body: " + reporHttpResponse.body() + " " + reporHttpResponse.request() + " ";
                AcknowledgementFailed(message, environmentRequester);
            }
        } catch (Exception e) {
            System.out.println("[ALARM TRIGGER Switch Error] Failed to send acknowledge report!");
            e.printStackTrace();
            AcknowledgementFailed("Failed due to exception: " + e, environmentRequester);
        }
    }
    
    public void getOAuthToken(String grantType, String clientId, String clientSecret, String scopeURL, String OAuthTokenURL) {
        try {
            Map<String, String> formData = new HashMap<>();
            formData.put("grant_type", grantType);
            formData.put("client_id", clientId);
            formData.put("client_secret", clientSecret);
            formData.put("scope", scopeURL);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(OAuthTokenURL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(getFormDataAsString(formData)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper objectMapper = new ObjectMapper();
            OAuthTokenResponse tokenResponse = objectMapper.readValue(response.body(), OAuthTokenResponse.class);

            tokenExpirationTime = System.currentTimeMillis() + tokenResponse.getExpires_in() * 1000;
            cached_oauth_token_response = tokenResponse;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private EventAckReport getEventAckReport(PowerLimitEvent powerLimitEvent) {
        ArrayList<Resource> resources = new ArrayList<>();

        ArrayList<Object> values = new ArrayList<>();
        values.add(true);
        Payload payload = Payload.builder().type(ACKNOWLEDGEMENT_TYPE).values(values).build();
        ArrayList<Payload> payloads = new ArrayList<>();
        payloads.add(payload);
        Interval interval = Interval.builder().id(0).payloads(payloads).build();
        ArrayList<Interval> intervals = new ArrayList<>();
        intervals.add(interval);
        PayloadDescriptor payloadDescriptor = PayloadDescriptor.builder().payloadType(ACKNOWLEDGEMENT_TYPE).build();
        ArrayList<PayloadDescriptor> payloadDescriptors = new ArrayList<>();
        payloadDescriptors.add(payloadDescriptor);

        IntervalPeriod intervalPeriod = IntervalPeriod.builder()
            .start(powerLimitEvent.getStartDateTime())
            .duration("PT" + powerLimitEvent.getDuration() + "M").build();

        Resource resource = Resource.builder().resourceName(powerLimitEvent.getResource())
            .intervalPeriod(intervalPeriod)
            .intervals(intervals).build();
        resources.add(resource);

        EventAckReport eventAckReport = EventAckReport.builder().clientName(SWITCH_CLIENT_NAME).eventID(powerLimitEvent.getEventId())
                .resources(resources).payloadDescriptors(payloadDescriptors)
                .programID(powerLimitEvent.getProgramId()).build();
        System.out.println("[Switch-"+ powerLimitEvent.getEnvironmentRequester() +"] eventAckReport : " + eventAckReport);
        return eventAckReport;
    }

    void fetchingEventsFailed(String reason, String environmentRequester) {
        System.out.println("[Switch-"+ environmentRequester +"] Fetch event failed: " + reason);
    }

    public void AcknowledgementFailed(String reason, String environmentRequester) {
        System.out.println("[Switch-"+ environmentRequester +"] PowerLimit ack failed: " + reason);
    }

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