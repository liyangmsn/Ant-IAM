package com.antiam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "application_groups")
public class ApplicationGroup extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    private boolean builtIn;

    public ApplicationGroup(String code, String name, String description, boolean builtIn) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.builtIn = builtIn;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
