package function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayCustomAuthorizerEvent;
import com.amazonaws.services.lambda.runtime.events.IamPolicyResponse;

import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

public class WebhookTokenAuthorizer implements RequestHandler<APIGatewayCustomAuthorizerEvent, IamPolicyResponse> {

    private static final String BEARER_TOKEN = "/BearerToken"; // Must be the same as in CheckSubscription function!!
    private static final String QA_BEARER_TOKEN = "/QA/BearerToken"; // Must be the same as in CheckSubscription function!!

    private Map<String, String> parameters = new HashMap<>();

    @Override
    public IamPolicyResponse handleRequest(APIGatewayCustomAuthorizerEvent event, Context context) {
        setPrerequisites();
        String tokenReq = event.getAuthorizationToken();

        if (tokenReq == null){
            throw new RuntimeException("Unauthorized"); // Throws Unauthorized error for invalid token
        }
        if(event.getMethodArn().contains("/qa/powerlimit") && !tokenReq.equals("Bearer " + parameters.get(QA_BEARER_TOKEN))){
            throw new RuntimeException("Unauthorized"); // Throws Unauthorized error for invalid token
        }
        if(event.getMethodArn().contains("/prod/powerlimit") && !tokenReq.equals("Bearer " + parameters.get(BEARER_TOKEN))) {
            throw new RuntimeException("Unauthorized"); // Throws Unauthorized error for invalid token
        }

        String principalId = "user-id-here";

        final ArrayList<String> resources = new ArrayList<String>();
        resources.add("arn:aws:execute-api:*:*:*");
        final ArrayList<IamPolicyResponse.Statement> statements = new ArrayList<IamPolicyResponse.Statement>();

        IamPolicyResponse.Statement statement = IamPolicyResponse.Statement.builder().withAction("execute-api:Invoke")
                .withEffect("Allow").withResource(resources).build();
        statements.add(statement);
        IamPolicyResponse.PolicyDocument policyDocument = IamPolicyResponse.PolicyDocument.builder()
                .withStatement(statements)
                .withVersion("2012-10-17").build();
        IamPolicyResponse iamPolicyResponse = IamPolicyResponse.builder().withPolicyDocument(policyDocument)
                .withPrincipalId(principalId).build();

        return iamPolicyResponse;
    }

    /**
     * Handles prerequisites 
     * for now, gets subscription token from AWS System Manager - Parameter Store
     */
    private void setPrerequisites(){
        SsmClient ssmClient = SsmClient.builder()
        .region(DefaultAwsRegionProviderChain.builder().build().getRegion())
        .build();

        GetParametersRequest request = GetParametersRequest.builder()
        .names(BEARER_TOKEN,
        QA_BEARER_TOKEN)
        .withDecryption(true)
        .build();

        GetParametersResponse response = ssmClient.getParameters(request);
        
        for (Parameter parameter : response.parameters()) {
            parameters.put(parameter.name(), parameter.value());
        }
    }
}