package net.datasa.project01.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import net.datasa.project01.domain.entity.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

}
