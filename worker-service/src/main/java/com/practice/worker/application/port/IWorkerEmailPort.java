package com.practice.worker.application.port;

public interface IWorkerEmailPort {

    void sendVerificationEmail(String to, String rawToken);
}
