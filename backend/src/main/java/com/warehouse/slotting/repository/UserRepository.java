package com.warehouse.slotting.repository;

import com.warehouse.slotting.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("select u from User u where lower(u.login) = lower(:login)")
    Optional<User> findByLoginIgnoreCase(@Param("login") String login);
}
