package com.iexec.core.chain;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainUtils;
import com.iexec.common.contract.generated.IexecClerkABILegacy;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple6;

import java.math.BigInteger;

import static com.iexec.common.chain.ChainContributionStatus.*;
import static com.iexec.common.chain.ChainUtils.getWeb3j;
import static com.iexec.core.utils.DateTimeUtils.sleep;

@Slf4j
@Service
public class IexecClerkService {

    // internal variables
    private final IexecClerkABILegacy iexecClerk;
    private final IexecHubABILegacy iexecHub;
    private final Credentials credentials;
    private final Web3j web3j;

    @Autowired
    public IexecClerkService(CredentialsService credentialsService,
                             ChainConfig chainConfig) {
        this.credentials = credentialsService.getCredentials();
        this.web3j = getWeb3j(chainConfig.getPrivateChainAddress());
        this.iexecClerk = ChainUtils.loadClerkContract(credentials, web3j, chainConfig.getHubAddress());
        this.iexecHub = ChainUtils.loadHubContract(credentials, web3j, chainConfig.getHubAddress());
    }


    public ChainContribution getContribution(String chainTaskId, String workerWalletAddress) {
        Tuple6<BigInteger, byte[], byte[], String, BigInteger, BigInteger> contributionTuple = null;
        try {
            contributionTuple = iexecHub.viewContributionABILegacy(BytesUtils.stringToBytes(chainTaskId), workerWalletAddress).send();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ChainContribution.tuple2Contribution(contributionTuple);
    }

    public boolean checkContributionStatusMultipleTimes(String chainTaskId, String walletAddress, ChainContributionStatus statusToCheck) {
        return checkContributionStatusRecursivelyWithDelay(chainTaskId, walletAddress, statusToCheck);
    }

    private boolean checkContributionStatusRecursivelyWithDelay(String chainTaskId, String walletAddress, ChainContributionStatus statusToCheck) {
        return sleep(500) && checkContributionStatusRecursively(chainTaskId, walletAddress, statusToCheck, 0);
    }

    private boolean checkContributionStatusRecursively(String chainTaskId, String walletAddress, ChainContributionStatus statusToCheck, int tryIndex) {
        int MAX_RETRY = 3;

        if (tryIndex >= MAX_RETRY) {
            return false;
        }
        tryIndex++;

        ChainContribution onChainContribution = getContribution(chainTaskId, walletAddress);
        if (onChainContribution != null) {
            ChainContributionStatus onChainStatus = onChainContribution.getStatus();
            switch (statusToCheck) {
                case CONTRIBUTED:
                    if (onChainStatus.equals(UNSET)) { // should wait
                        return checkContributionStatusRecursively(chainTaskId, walletAddress, statusToCheck, tryIndex);
                    } else if (onChainStatus.equals(CONTRIBUTED) || onChainStatus.equals(REVEALED)) { // has at least contributed
                        return true;
                    } else {
                        return false;
                    }
                case REVEALED:
                    if (onChainStatus.equals(CONTRIBUTED)) { // should wait
                        return checkContributionStatusRecursively(chainTaskId, walletAddress, statusToCheck, tryIndex);
                    } else if (onChainStatus.equals(REVEALED)) { // has at least revealed
                        return true;
                    } else {
                        return false;
                    }
                default:
                    break;
            }
        }
        return false;
    }


    public String initializeTask(byte[] dealId, int numTask) throws Exception {
        TransactionReceipt res = iexecHub.initialize(dealId, BigInteger.valueOf(numTask)).send();
        if (!iexecHub.getTaskInitializeEvents(res).isEmpty()) {
            return BytesUtils.bytesToString(iexecHub.getTaskInitializeEvents(res).get(0).taskid);
        }
        return null;
    }

    public boolean consensus(String chainTaskId, String consensus) throws Exception {
        TransactionReceipt receipt = iexecHub.consensus(BytesUtils.stringToBytes(chainTaskId),
                BytesUtils.stringToBytes(consensus)).send();
        if (!iexecHub.getTaskConsensusEvents(receipt).isEmpty()) {
            log.info("Set consensus on-chain succeeded [chainTaskId:{}, consensus:{}]", chainTaskId, consensus);
            return true;
        }
        return false;
    }

    public ChainTask getChainTask(String chainTaskId) {
        try {
            return ChainTask.tuple2ChainTask(iexecHub.viewTaskABILegacy(BytesUtils.stringToBytes(chainTaskId)).send());
        } catch (Exception e) {
            log.error("Failed to view chainTask [chainTaskId:{}, error:{}]", chainTaskId, e.getMessage());
        }
        return null;
    }


}
