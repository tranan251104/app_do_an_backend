package vn.anpay.backend.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.anpay.backend.notification.entity.Notification;

import java.time.Instant;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserIdAndCreatedAtGreaterThanEqual(
            UUID userId,
            Instant cutoff,
            Pageable pageable
    );

    Page<Notification> findByUserIdAndCategoryAndCreatedAtGreaterThanEqual(
            UUID userId,
            String category,
            Instant cutoff,
            Pageable pageable
    );

    Page<Notification> findByUserIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(
            UUID userId,
            Instant cutoff,
            Pageable pageable
    );

    Page<Notification> findByUserIdAndCategoryAndReadAtIsNullAndCreatedAtGreaterThanEqual(
            UUID userId,
            String category,
            Instant cutoff,
            Pageable pageable
    );

    long countByUserIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(UUID userId, Instant cutoff);

    long countByUserIdAndCategoryAndReadAtIsNullAndCreatedAtGreaterThanEqual(
            UUID userId,
            String category,
            Instant cutoff
    );

    @Query("""
            select count(n)
              from Notification n
             where n.userId = :userId
               and n.type like 'PROACTIVE_%'
               and n.createdAt >= :fromInclusive
               and n.createdAt < :toExclusive
            """)
    long countProactiveForUserBetween(
            @Param("userId") UUID userId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive
    );

    @Modifying
    @Query("""
            update Notification n
               set n.readAt = :readAt
             where n.userId = :userId
               and n.readAt is null
               and n.createdAt >= :cutoff
            """)
    int markAllRead(
            @Param("userId") UUID userId,
            @Param("readAt") Instant readAt,
            @Param("cutoff") Instant cutoff
    );

    @Modifying
    @Query("""
            update Notification n
               set n.readAt = :readAt
             where n.userId = :userId
               and n.category = :category
               and n.readAt is null
               and n.createdAt >= :cutoff
            """)
    int markAllReadByCategory(
            @Param("userId") UUID userId,
            @Param("category") String category,
            @Param("readAt") Instant readAt,
            @Param("cutoff") Instant cutoff
    );

    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
