package uk.co.motormovers.platform.common.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UnsavedUploadRepository extends JpaRepository<UnsavedUpload, String> {

    @Query("select u.storageKey from UnsavedUpload u where u.createdAt < :cutoff order by u.createdAt")
    List<String> keysCreatedBefore(@Param("cutoff") Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UnsavedUpload u where u.storageKey = :key")
    Optional<UnsavedUpload> lock(@Param("key") String key);

    @Modifying
    @Query("delete from UnsavedUpload u where u.storageKey = :key")
    int forget(@Param("key") String key);

    /** How many items hold this key, in the four columns a key can be saved on. */
    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT 1 AS found FROM programme_session WHERE video_key = :key
                UNION ALL SELECT 1 FROM home_activity WHERE video_key = :key
                UNION ALL SELECT 1 FROM induction_lesson WHERE video_key = :key
                UNION ALL SELECT 1 FROM library_resource WHERE file_key = :key
            ) uses
            """, nativeQuery = true)
    long uses(@Param("key") String key);
}
