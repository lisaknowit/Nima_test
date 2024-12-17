package function;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersResponse;

public class ConnectionMonitor implements RequestHandler<Object, Map<String, String>> {

    private static final String DRIIVZ_PASSWORD = "/DriivzPassword";
    private static final String DRIIVZ_USERNAME = "/DriivzUsername";
    private static final String DRIIVS_BASE_URL = "/DriivzBaseURL";

    private HashMap<String, String> parameters = new HashMap<>();

    /**
     * The purpose of this function is to make sure the connection to the Driivz API
     * is working.
     * For now this is done by making sure it is possible to login.
     */
    @Override
    public Map<String, String> handleRequest(Object input, Context context) {
        SsmClient ssmClient = SsmClient.builder()
                .region(DefaultAwsRegionProviderChain.builder().build().getRegion())
                .build();

        GetParametersRequest request = GetParametersRequest.builder()
                .names( DRIIVS_BASE_URL,
                        DRIIVZ_PASSWORD,
                        DRIIVZ_USERNAME)
                .withDecryption(true)
                .build();

        GetParametersResponse response = ssmClient.getParameters(request);
      
        response.parameters().forEach(parameter -> {
            parameters.put(parameter.name(), parameter.value());
        });

        DriivzManager driivzManager = new DriivzManager(parameters.get(DRIIVS_BASE_URL),
                parameters.get(DRIIVZ_USERNAME),
                parameters.get(DRIIVZ_PASSWORD));
        driivzManager.login();
        return new HashMap<String, String>();
    }
}