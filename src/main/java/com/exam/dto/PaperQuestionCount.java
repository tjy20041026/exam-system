package com.exam.dto;

import lombok.Data;

@Data
public class PaperQuestionCount {

    private Long paperId;

    // SQL 里要显式写 AS questionCount，别名对不上不会报错，只会静默赋 null
    private Integer questionCount;
}
