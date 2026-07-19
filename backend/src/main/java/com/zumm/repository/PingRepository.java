package com.zumm.repository;

import com.zumm.domain.Ping;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces a l'entite factice du walking skeleton (SPRINT-00). */
public interface PingRepository extends JpaRepository<Ping, Long> {
}
