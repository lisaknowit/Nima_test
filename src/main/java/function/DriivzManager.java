package function;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.AbstractMap;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;

import dto.EnvironmentRequester;
import model.Charger;
import model.ChargerStatus;
import model.KeyValue;
import model.driivz.ChargerProfileResponse;
import model.driivz.Datum;
import model.driivz.LoginInfo;
import model.driivz.request.LoginRequest;
import model.driivz.request.ResetRequest;
public class DriivzManager {

    private final HttpClient client = HttpClient.newHttpClient();
    private final String BASE_URL;
    private final String USER_NAME;
    private final String PASSWORD;
    public final StringBuffer log = new StringBuffer();

    private String ticket;

    public DriivzManager(String baseUrl, String userName, String password) {
        BASE_URL = baseUrl;
        USER_NAME = userName;
        PASSWORD = password;
    }

    public void login() {
        try {
            LoginRequest loginRequest = LoginRequest.builder().userName(USER_NAME).password(PASSWORD).build();

            ObjectMapper om = new ObjectMapper();
            HttpRequest loginHttpRequest = HttpRequest.newBuilder()
                .uri(new URI(BASE_URL + "/v1/authentication/operator/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(loginRequest)))
                .build();

            HttpResponse<String> reporHttpResponse = client.send(loginHttpRequest, 
                HttpResponse.BodyHandlers.ofString());

            LoginInfo loginInfo = om.readValue(reporHttpResponse.body(), LoginInfo.class); 
            ticket =  loginInfo.getData().get(0).getTicket();
        } catch (Exception e) {
            System.out.println("[ALARM TRIGGER Driivz Error] Could not login on Driivz");
            e.printStackTrace();
        }
        System.out.println("Login successful: " + ticket);
    }

    public ChargerProfileResponse getChargerProfiles(String groupId, String environmentRequester) {
        if (groupId.isBlank() || groupId.isEmpty()) {
            throw new RuntimeException("GroupId is empty!");
        }
        loginIfNeeded();
        AbstractMap.SimpleImmutableEntry<String,String> keyValue = new AbstractMap.SimpleImmutableEntry<>("groupId", groupId);

        ChargerProfileResponse chargerProfileResponse = null;
        try {
            ObjectMapper om = new ObjectMapper();
            String bodyString = om.writeValueAsString(keyValue);
            System.out.println(environmentRequester + " #### getChargerProfiles REQUEST: " + bodyString);

            HttpRequest eventRequest = HttpRequest.newBuilder()
                .uri(new URI(BASE_URL + "/v1/chargers/profiles/filter"))
                .header("Content-Type", "application/json")
                .header("dmsTicket", ticket)
                .POST(HttpRequest.BodyPublishers.ofString(bodyString))
                .build();

            HttpResponse<String> reporHttpResponse = client.send(eventRequest, 
                HttpResponse.BodyHandlers.ofString());
            System.out.println(environmentRequester + " #### getChargerProfiles #### response: " + reporHttpResponse);
            System.out.println(environmentRequester + " #### getChargerProfiles #### response body: " + reporHttpResponse.body());
            chargerProfileResponse = om.readValue(reporHttpResponse.body(),
                 ChargerProfileResponse.class);

            System.out.println("[Driivz-"+ environmentRequester +"]charger profile: " + chargerProfileResponse);
        } catch (Exception e) {
            System.out.println("[Driivz-"+ environmentRequester +"][ALARM TRIGGER Driivz Error] ");
            e.printStackTrace();
        }
        return chargerProfileResponse;
    }

    public ArrayList<Charger> getChargersFromGroup(String groupId, String environmentRequester) {
        loginIfNeeded();
        ArrayList<Charger> resultIdArray = new ArrayList<>();
        ChargerProfileResponse chargerGroupStatuses = getChargerProfiles(groupId, environmentRequester);
        ArrayList<Datum> data = chargerGroupStatuses.getData();

        data.forEach((chargerStatus) ->
        {
            Charger charger = Charger.builder().id(chargerStatus.getId()).identityKey(chargerStatus.getIdentityKey()).build();
            resultIdArray.add(charger);
        });

        return resultIdArray;
    }

    public ChargerStatus getChargerStatus(String chargerId, String environmentRequester) {
        loginIfNeeded();
        ChargerStatus chargerStatus = null;
        try {
            ObjectMapper om = new ObjectMapper();
            HttpRequest eventRequest = HttpRequest.newBuilder()
                .uri(new URI(BASE_URL + "/v1/chargers/" + chargerId + "/status"))
                .header("Content-Type", "application/json")
                .header("dmsTicket", ticket)
                .GET()
                .build();

            HttpResponse<String> reporHttpResponse = client.send(eventRequest, 
            HttpResponse.BodyHandlers.ofString());
            System.out.println(environmentRequester + " #### getChargerStatus #### response: " + reporHttpResponse);
            System.out.println(environmentRequester + " #### getChargerStatus #### response body: " + reporHttpResponse.body());
            ChargerProfileResponse chargerProfileResponse = om.readValue(reporHttpResponse.body(),
                 ChargerProfileResponse.class); 
            chargerStatus = ChargerStatus.builder().status(chargerProfileResponse.
                getData().get(0).getChargerStatus()).build();
        } catch (Exception e) {
            System.out.println("[Driivz-"+ environmentRequester +"][ALARM TRIGGER Driivz Error] ");
            e.printStackTrace();
        }
        return chargerStatus;
    }

    public void executePowerLimitForResource(String resourceName, String groupId, int limit, String environmentRequester) {
        loginIfNeeded();
        ArrayList<Charger> chargerIdentityKeysFromGroup = getChargersFromGroup(groupId, environmentRequester);
        chargerIdentityKeysFromGroup.forEach((charger) ->
        {
            setPowerLimitOnCharger(limit, charger, environmentRequester);
        });
    }

    private void resetCharger(String identityKey, String environmentRequester) {
        loginIfNeeded();
        ResetRequest resetRequestBody = ResetRequest.builder().resetType("SOFT").build();
        ObjectMapper om = new ObjectMapper();
        System.out.println("[Driivz-"+ environmentRequester +"] Soft reset: " + identityKey);
        // TODO: This part should be un-commented before deploying to production
        // try {
        //     String bodyString = om.writeValueAsString(resetRequestBody);
        //     HttpRequest resetRequest = HttpRequest.newBuilder()
        //         .uri(new URI(BASE_URL + "/v1/chargers/" + identityKey + "/remote-operations/reset"))
        //         .header("Content-Type", "application/json")
        //         .header("dmsTicket", ticket)
        //         .POST(HttpRequest.BodyPublishers.ofString(bodyString))
        //         .build();
        //     HttpResponse<String> resetHttpResponse = client.send(resetRequest,
        //         HttpResponse.BodyHandlers.ofString());
        //     System.out.println(environmentRequester + " #### resetCharger #### response: " + resetHttpResponse);
        //     System.out.println(environmentRequester + " #### resetCharger #### response body: " + resetHttpResponse.body());
        // } catch (Exception e) {
        //     e.printStackTrace();
        // }
    }

    private void updateChargerConfiguration(int limit, String chargerIdentityKey, String environmentRequester) {
        loginIfNeeded();
        KeyValue keyValue = KeyValue.builder().key("MaxGridPower").value(String.valueOf(limit)).build();
        ObjectMapper om = new ObjectMapper();
        try {
            String bodyString = om.writeValueAsString(keyValue);
            System.out.println("[Driivz-"+ environmentRequester +"] Update charger config: " + chargerIdentityKey + " limit: " + limit);

            HttpRequest updateRequest = HttpRequest.newBuilder()
                .uri(new URI(BASE_URL + "/v1/chargers/" + chargerIdentityKey + "/remote-operations/configuration"))
                .header("Content-Type", "application/json")
                .header("dmsTicket", ticket)
                .POST(HttpRequest.BodyPublishers.ofString(bodyString))
                .build();
            HttpResponse<String> updateHttpResponse = client.send(updateRequest,
                HttpResponse.BodyHandlers.ofString());
                System.out.println(environmentRequester + " #### updateConfig #### response: " + updateHttpResponse);
                System.out.println(environmentRequester + " #### updateConfig #### response body: " + updateHttpResponse.body());
        } catch (Exception e) {
            System.out.println("[Driivz-"+ environmentRequester +"][ALARM TRIGGER Driivz Error] ");
            e.printStackTrace();
        }
    }

    /**
     * Check if a charger is responding by trying to get its configuration
     */
    private boolean isChargerResponding(String identityKey, String environmentRequester) {
        loginIfNeeded();
        try {
            System.out.println("[Driivz-"+ environmentRequester +"] isChargerResponding, identityKey : " + identityKey);

            HttpRequest updateRequest = HttpRequest.newBuilder()
                .uri(new URI(BASE_URL + "/v1/chargers/" + identityKey + "/remote-operations/configuration/filter"))
                .header("Content-Type", "application/json")
                .header("dmsTicket", ticket)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
            HttpResponse<String> updateHttpResponse = client.send(updateRequest,
                HttpResponse.BodyHandlers.ofString());
                System.out.println(environmentRequester + " #### updateConfig #### response: " + updateHttpResponse);
                System.out.println(environmentRequester + " #### updateConfig #### response body: " + updateHttpResponse.body());
            if (updateHttpResponse.statusCode() == HttpURLConnection.HTTP_OK) {
                return true;
            }
        } catch (Exception e) {
            // Catching this exception means that the charger is not responding -> do nothing
            e.printStackTrace();
        }
        return false;
    }

    private void loginIfNeeded() {
        if (ticket == null) login();
    }

    public void retryCloseChargersIfNotClosed(String groupId, int powerLimit, String environmentRequester) {
        System.out.println("[Driivz-"+ environmentRequester +"] Retry close if needed");
        ArrayList<Charger> chargerIdsFromGroup = getChargersFromGroup(groupId, environmentRequester);

        final ArrayList<Boolean> result = new ArrayList<>();
        result.add(0, true);
        chargerIdsFromGroup.forEach((charger) -> {
            
            String chargerStatus = getChargerStatus(String.valueOf(charger.getId()), environmentRequester).status;
            if (!chargerStatus.equals(model.driivz.ChargerStatus.UNAVAILABLE.name()) &&
                    !chargerStatus.equals(model.driivz.ChargerStatus.PREPARING.name())) {
                System.out.println("[Driivz-" + environmentRequester + "] charger " + charger
                        + " is still AVAILABLE retry powerlimit!");
                if (isChargerResponding(charger.getIdentityKey(), environmentRequester)) {
                    setPowerLimitOnCharger(powerLimit, charger, environmentRequester);
                }
                result.set(0, false);
            }
        });
    }

    private void setPowerLimitOnCharger(int powerLimit, Charger charger, String environmentRequester) {
        if(environmentRequester.equals(EnvironmentRequester.PROD.toString())){
            updateChargerConfiguration(powerLimit, charger.getIdentityKey(), environmentRequester);
            resetCharger(charger.getIdentityKey(), environmentRequester);
        }else{
            System.out.println("[Driivz-QA] setPowerLimitOnCharger will not actually be changed for charger: " + charger);
        }
    }

    public boolean isSiteUp(String groupId, String environmentRequester) {
        System.out.println("[Driivz-"+ environmentRequester +"] Check if site: " + groupId + " is up");
        ArrayList<Charger> chargerIdsFromGroup = getChargersFromGroup(groupId, environmentRequester);

        final ArrayList<Boolean> result = new ArrayList<>();
        result.add(0, true);

        chargerIdsFromGroup.forEach((charger) ->
        {
            String chargerStatus = getChargerStatus(String.valueOf(charger.getId()), environmentRequester).status;

            if (!chargerStatus.equals("AVAILABLE")) {
                System.out.println("[Driivz-"+ environmentRequester +"] Charger " + charger.getId() +
                    " is still not AVAILABLE current status: " + chargerStatus);
                result.set(0, false);
            }
        });

        return (chargerIdsFromGroup != null && !chargerIdsFromGroup.isEmpty() && result.get(0));
    }
}