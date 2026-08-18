package com.codeguard.codeguard.rule;

import com.codeguard.codeguard.model.Finding;

import java.util.List;

public interface CodeReviewRule {

    List<Finding> analyze(String code);

}