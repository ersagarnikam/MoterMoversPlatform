package uk.co.motormovers.platform.family.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.co.motormovers.platform.family.model.ParentLink;

public interface ParentLinkRepository extends JpaRepository<ParentLink, Long> {

    List<ParentLink> findByParentIdOrderByLinkedAt(Long parentId);

    Optional<ParentLink> findByParentIdAndChildId(Long parentId, Long childId);

    boolean existsByParentIdAndChildId(Long parentId, Long childId);

    List<ParentLink> findByChildSchoolClassSchoolIdOrderByLinkedAtDesc(Long schoolId);
}
