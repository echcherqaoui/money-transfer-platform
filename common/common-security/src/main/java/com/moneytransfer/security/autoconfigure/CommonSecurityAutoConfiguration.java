package com.moneytransfer.security.autoconfigure;

import com.moneytransfer.security.service.ISignatureService;
import com.moneytransfer.security.service.impl.HmacSignatureService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class CommonSecurityAutoConfiguration {

    /**
     * Registers HmacSignatureService as the default ISignatureService.
     */
    @Bean
    @ConditionalOnMissingBean(ISignatureService.class)
    public ISignatureService signatureService(@Value("${security.hmac.secret}") String secret) {
        return new HmacSignatureService(secret);
    }
}