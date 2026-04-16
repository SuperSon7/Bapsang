package com.vani.week4.backend.post.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis 에 저장된 좋아요 수를 5분마다 DB와 동기화하기 위한 스케쥴러
 * 실제 동기화 작업은 synchronizer 에 위임합니다.
 * @author vani
 * @since 10/15/25
 */

@Component
@Slf4j
public class LikeSyncScheduler {
    private final LikeCountSynchronizer likeCountSynchronizer;

    public LikeSyncScheduler(LikeCountSynchronizer likeCountSynchronizer) {
        this.likeCountSynchronizer = likeCountSynchronizer;
    }

    /**
     * Redis 의 좋아요 수 동기화를 주기적으로 실행합니다.
     */
    @Scheduled(cron = "0 */5 * * * *") //5분마다 동기화
    public void syncLikeCount() {
        log.info("좋아요 수 동기화 시작");
        int syncCount = likeCountSynchronizer.syncAll();
        log.info("좋아요 수 동기화 완료 : 처리 수: {}", syncCount);
    }
}
