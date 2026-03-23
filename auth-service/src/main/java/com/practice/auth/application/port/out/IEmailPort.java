package com.practice.auth.application.port.out;

public interface IEmailPort {

    void sendVerificationEmail(String to, String rawToken);
}
