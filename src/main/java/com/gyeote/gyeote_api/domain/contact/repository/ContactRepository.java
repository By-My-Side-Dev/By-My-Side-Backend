package com.gyeote.gyeote_api.domain.contact.repository;

import com.gyeote.gyeote_api.domain.contact.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    boolean existsByUser_IdAndContactUser_Id(Long userId, Long contactUserId);

    Optional<Contact> findByIdAndUser_Id(Long id, Long userId);

    List<Contact> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    void deleteByUser_IdAndContactUser_Id(Long userId, Long contactUserId);
}
