package com.iexec.core.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;

@Slf4j
@Service
public class ConfigurationService {

    private ConfigurationRepository configurationRepository;

    public ConfigurationService(ConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    private Configuration getConfiguration() {
        Configuration configuration;
        if (configurationRepository.count() > 0) {
            configuration = configurationRepository.findAll().get(0);
        } else {
            configuration = configurationRepository.save(
                    Configuration
                            .builder()
                            .lastSeenBlockWithDeal(BigInteger.ZERO)
                            .fromReplay(BigInteger.ZERO)
                            .build());
        }
        return configuration;
    }

    private void saveConfiguration(Configuration configuration) {
        configurationRepository.save(configuration);
    }

    public BigInteger getLastSeenBlockWithDeal() {
        return this.getConfiguration().getLastSeenBlockWithDeal();
    }

    public void setLastSeenBlockWithDeal(BigInteger lastBlockNumber) {
        Configuration configuration = this.getConfiguration();
        configuration.setLastSeenBlockWithDeal(lastBlockNumber);
        saveConfiguration(configuration);
    }

    public BigInteger getFromReplay() {
        return this.getConfiguration().getFromReplay();
    }

    public void setFromReplay(BigInteger fromReplay) {
        Configuration configuration = this.getConfiguration();
        configuration.setFromReplay(fromReplay);
        saveConfiguration(configuration);
    }

}