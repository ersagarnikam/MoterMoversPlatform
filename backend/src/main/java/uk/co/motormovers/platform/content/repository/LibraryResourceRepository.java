package uk.co.motormovers.platform.content.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.content.model.ContentStatus;
import uk.co.motormovers.platform.content.model.LibraryResource;

public interface LibraryResourceRepository extends JpaRepository<LibraryResource, Long> {

    List<LibraryResource> findByStatusOrderByTitle(ContentStatus status);

    List<LibraryResource> findAllByOrderByIdDesc();
}
