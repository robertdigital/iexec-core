package com.iexec.core.replicate;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.workflow.ReplicateWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.REVEALED;
import static com.iexec.common.replicate.ReplicateStatus.getChainStatus;

@Slf4j
@Service
public class ReplicatesService {

    private ReplicatesRepository replicatesRepository;
    private IexecHubService iexecHubService;
    private ApplicationEventPublisher applicationEventPublisher;

    public ReplicatesService(ReplicatesRepository replicatesRepository,
                             IexecHubService iexecHubService,
                             ApplicationEventPublisher applicationEventPublisher) {
        this.replicatesRepository = replicatesRepository;
        this.iexecHubService = iexecHubService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void addNewReplicate(String chainTaskId, String walletAddress) {
        if (!getReplicate(chainTaskId, walletAddress).isPresent()) {
            Optional<ReplicatesList> optional = getReplicatesList(chainTaskId);
            if (optional.isPresent()) {
                ReplicatesList replicatesList = optional.get();
                replicatesList.getReplicates().add(new Replicate(walletAddress, chainTaskId));
                replicatesRepository.save(replicatesList);
                log.info("New replicate saved [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
            }
        } else {
            log.error("Replicate already saved [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
        }

    }

    public synchronized void createEmptyReplicateList(String chainTaskId) {
        replicatesRepository.save(new ReplicatesList(chainTaskId));
    }

    public Optional<ReplicatesList> getReplicatesList(String chainTaskId) {
        return replicatesRepository.findByChainTaskId(chainTaskId);
    }

    public List<Replicate> getReplicates(String chainTaskId) {
        Optional<ReplicatesList> optionalList = getReplicatesList(chainTaskId);
        if (!optionalList.isPresent()) {
            return Collections.emptyList();
        }
        return optionalList.get().getReplicates();
    }

    public Optional<Replicate> getReplicate(String chainTaskId, String walletAddress) {
        Optional<ReplicatesList> optional = getReplicatesList(chainTaskId);
        if (!optional.isPresent()) {
            return Optional.empty();
        }

        for (Replicate replicate : optional.get().getReplicates()) {
            if (replicate.getWalletAddress().equals(walletAddress)) {
                return Optional.of(replicate);
            }
        }

        return Optional.empty();
    }

    public boolean hasWorkerAlreadyContributed(String chainTaskId, String walletAddress) {
        return getReplicate(chainTaskId, walletAddress).isPresent();
    }

    public int getNbReplicatesWithStatus(String chainTaskId, ReplicateStatus... listStatus) {
        int nbReplicates = 0;
        for (Replicate replicate : getReplicates(chainTaskId)) {
            for (ReplicateStatus status : listStatus) {
                if (replicate.getCurrentStatus().equals(status)) {
                    nbReplicates++;
                }
            }
        }
        return nbReplicates;
    }

    public Optional<Replicate> getReplicateWithRevealStatus(String chainTaskId) {
        for (Replicate replicate : getReplicates(chainTaskId)) {
            if (replicate.getCurrentStatus().equals(REVEALED)) {
                return Optional.of(replicate);
            }
        }

        return Optional.empty();
    }

    public boolean moreReplicatesNeeded(String chainTaskId, int trust) {
        int nbValidReplicates = 0;
        for (Replicate replicate : getReplicates(chainTaskId)) {
            if (!(replicate.getCurrentStatus().equals(ReplicateStatus.ERROR)
                    || replicate.getCurrentStatus().equals(ReplicateStatus.WORKER_LOST))) {
                nbValidReplicates++;
            }
        }
        return nbValidReplicates < trust;
    }

    // in case the task has been modified between reading and writing it, it is retried up to 10 times
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 10)
    public void updateReplicateStatus(String chainTaskId, String walletAddress, ReplicateStatus newStatus) {

        Optional<ReplicatesList> optionalReplicates = getReplicatesList(chainTaskId);
        if (!optionalReplicates.isPresent()) {
            log.warn("No replicate found for this chainTaskId for status update [chainTaskId:{}, walletAddress:{}, status:{}]",
                    chainTaskId, walletAddress, newStatus);
            return;
        }

        Optional<Replicate> optionalReplicate = optionalReplicates.get().getReplicateOfWorker(walletAddress);
        if (!optionalReplicate.isPresent()) {
            log.warn("No replicate found for status update [chainTaskId:{}, walletAddress:{}, status:{}]", chainTaskId, walletAddress, newStatus);
            return;
        }

        Replicate replicate = optionalReplicate.get();
        ReplicateStatus currentStatus = replicate.getCurrentStatus();

        // check valid transition
        if (!ReplicateWorkflow.getInstance().isValidTransition(currentStatus, newStatus)) {
            log.error("UpdateReplicateStatus failed (bad workflow transition) [chainTaskId:{}, walletAddress:{}, " +
                            "currentStatus:{}, newStatus:{}]",
                    chainTaskId, walletAddress, currentStatus, newStatus);
            return;
        }

        // TODO: code to check here
        ChainContributionStatus wishedChainStatus = getChainStatus(newStatus);
        if (wishedChainStatus != null) {
            if (iexecHubService.checkContributionStatusMultipleTimes(chainTaskId, walletAddress, wishedChainStatus)) {
                handleReplicateWithOnChainStatus(chainTaskId, walletAddress, replicate, wishedChainStatus);
            } else {
                log.error("UpdateReplicateStatus failed (bad blockchain status) [chainTaskId:{}, walletAddress:{}, currentStatus:{}, newStatus:{}]",
                        chainTaskId, walletAddress, currentStatus, newStatus);
                return;
            }
        }

        replicate.updateStatus(newStatus);
        replicatesRepository.save(optionalReplicates.get());
        log.info("UpdateReplicateStatus succeeded [chainTaskId:{}, walletAddress:{}, currentStatus:{}, newStatus:{}]", chainTaskId,
                walletAddress, currentStatus, newStatus);
        applicationEventPublisher.publishEvent(new ReplicateUpdatedEvent(replicate));

    }

    private void handleReplicateWithOnChainStatus(String chainTaskId, String walletAddress, Replicate replicate, ChainContributionStatus wishedChainStatus) {
        Optional<ChainContribution> optional = iexecHubService.getContribution(chainTaskId, walletAddress);
        if (!optional.isPresent()){
            return;
        }
        ChainContribution chainContribution = optional.get();
        switch (wishedChainStatus) {
            case CONTRIBUTED:
                replicate.setContributionHash(chainContribution.getResultHash());
                break;
            case REVEALED:
                break;
            default:
                break;
        }
    }


}