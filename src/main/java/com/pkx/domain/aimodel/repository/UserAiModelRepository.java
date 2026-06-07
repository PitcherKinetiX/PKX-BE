package com.pkx.domain.aimodel.repository;

import com.pkx.domain.aimodel.entity.UserAiModel;
import com.pkx.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAiModelRepository extends JpaRepository<UserAiModel, Long> {

    Optional<UserAiModel> findByUser(User user);

    Optional<UserAiModel> findByUser_UserId(Long userId);

    List<UserAiModel> findByStatus(UserAiModel.ModelStatus status);

}
