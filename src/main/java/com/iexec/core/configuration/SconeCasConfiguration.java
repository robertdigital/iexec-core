package com.iexec.core.configuration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


/**
 * CAS: Configuration and Attestation Service.
 * It handles configurations and secret provisioning: a user uploads secrets
 * and configuration infos for a specific service to the CAS.
 * When a service wants to access those secrets, it sends a quote with its MREnclave.
 * The CAS attests the quote through Intel Attestation Service and sends the secrets
 * if the MREnclave is as expected.
 * 
 * MREnclave: an enclave identifier, created by hashing all its
 * code. It guarantees that a code behaves exactly as expected.
 */
@Component
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SconeCasConfiguration {

    @Value("${scone.cas.host}")
    private String host;

    @Value("${scone.cas.port}")
    private String port;

    public String getURL() {
        return host + ":" + port;
    }
}
