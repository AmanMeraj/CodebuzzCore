package com.codebuzzers.codebuzzcore.identity;

import com.codebuzzers.codebuzzcore.common.PublicEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "teams")
public class Team extends PublicEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "manager_user_id")
    private Long managerUserId;

    @Column(nullable = false, length = 32)
    private String status;
}
