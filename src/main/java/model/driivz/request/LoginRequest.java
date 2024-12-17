package model.driivz.request;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LoginRequest {
    private String userName;
    private String password;
}