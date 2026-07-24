package com.zumm.repository;

import com.zumm.domain.Photo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux photos (US-010/028). Restreint au tenant (@TenantId + RLS). */
public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByVisiteIdOrderByIdAsc(Long visiteId);
}
