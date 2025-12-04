package me.junyi.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("case_suspect")
public class CaseSuspect {

    @Id
    private Long suspectId;

    private Long caseId;
    private String suspectName;
}